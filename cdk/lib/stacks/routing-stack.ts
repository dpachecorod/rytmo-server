import * as cdk from 'aws-cdk-lib/core';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';
import { Stage } from '../types';

export interface RoutingStackProps extends cdk.StackProps {
  stage: Stage;
  hostedZoneDomain: string;
  vpc: ec2.Vpc;
}

export class RoutingStack extends cdk.Stack {
  public readonly targetGroup: elbv2.ApplicationTargetGroup;
  public readonly ecsSg: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: RoutingStackProps) {
    super(scope, id, props);

    const { stage, hostedZoneDomain, vpc } = props;
    const apiDomain = `${stage.stageName}.api.${hostedZoneDomain}`;

    // ── Security Groups ──────────────────────────────────────────────────────

    const albSg = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc,
      securityGroupName: `${stage.stageName}-alb-sg`,
      description: 'ALB - allow HTTP and HTTPS from the internet',
    });
    albSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'HTTP');
    albSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'HTTPS');

    this.ecsSg = new ec2.SecurityGroup(this, 'EcsSg', {
      vpc,
      securityGroupName: `${stage.stageName}-ecs-sg`,
      description: 'ECS tasks - allow port 8080 from ALB only',
    });
    this.ecsSg.addIngressRule(albSg, ec2.Port.tcp(8080), 'From ALB');

    // ── Load Balancer ────────────────────────────────────────────────────────

    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      loadBalancerName: `${stage.stageName}-rytmo`,
      vpc,
      internetFacing: true,
      securityGroup: albSg,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // ── ACM Certificate ──────────────────────────────────────────────────────

    // ACM certificate - DNS validated against the existing hosted zone
    const hostedZone = route53.HostedZone.fromLookup(this, 'HostedZone', {
      domainName: hostedZoneDomain,
    });

    const certificate = new acm.Certificate(this, 'Certificate', {
      domainName: apiDomain,
      validation: acm.CertificateValidation.fromDns(hostedZone),
    });

    // ── Target Group & Listeners ─────────────────────────────────────────────

    this.targetGroup = new elbv2.ApplicationTargetGroup(this, 'TargetGroup', {
      targetGroupName: `${stage.stageName}-rytmo`,
      vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
      healthCheck: {
        path: '/q/health/live',
        interval: cdk.Duration.seconds(30),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
        timeout: cdk.Duration.seconds(5),
      },
      deregistrationDelay: cdk.Duration.seconds(30),
    });

    // HTTP -> HTTPS redirect
    alb.addListener('HttpListener', {
      port: 80,
      defaultAction: elbv2.ListenerAction.redirect({
        protocol: 'HTTPS',
        port: '443',
        permanent: true,
      }),
    });

    alb.addListener('HttpsListener', {
      port: 443,
      certificates: [certificate],
      defaultAction: elbv2.ListenerAction.forward([this.targetGroup]),
    });

    // ── DNS ──────────────────────────────────────────────────────────────────

    new route53.ARecord(this, 'ApiDnsRecord', {
      zone: hostedZone,
      recordName: apiDomain,
      target: route53.RecordTarget.fromAlias(new route53targets.LoadBalancerTarget(alb)),
    });

    // ── Outputs ──────────────────────────────────────────────────────────────

    new cdk.CfnOutput(this, 'ApiUrl', {
      value: `https://${apiDomain}`,
      exportName: `${stage.stageName}-api-url`,
    });
  }
}
