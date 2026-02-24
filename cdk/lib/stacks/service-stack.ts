import * as cdk from 'aws-cdk-lib/core';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';
import { Stage } from '../types';

export interface ServiceStackProps extends cdk.StackProps {
  stage: Stage;
  hostedZoneDomain: string;
  vpc: ec2.Vpc;
  customersTableName: string;
  customersTableArn: string;
  customerIdentitiesTableName: string;
  customerIdentitiesTableArn: string;
}

export class ServiceStack extends cdk.Stack {
  public readonly repository: ecr.Repository;

  constructor(scope: Construct, id: string, props: ServiceStackProps) {
    super(scope, id, props);

    const { stage, hostedZoneDomain, vpc } = props;
    const serviceConfig = stage.serviceConfig!;
    const apiDomain = `${stage.stageName}.api.${hostedZoneDomain}`;

    // ── ECR ─────────────────────────────────────────────────────────────────

    this.repository = new ecr.Repository(this, 'Repository', {
      repositoryName: `${stage.stageName}-rytmo-server`,
      removalPolicy: stage.isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      emptyOnDelete: !stage.isProd,
      lifecycleRules: [{ maxImageCount: 10, description: 'Keep last 10 images' }],
    });

    // ── ECS Cluster ──────────────────────────────────────────────────────────

    const cluster = new ecs.Cluster(this, 'Cluster', {
      clusterName: `${stage.stageName}-rytmo`,
      vpc,
      containerInsights: true,
    });

    // ── IAM Roles ────────────────────────────────────────────────────────────

    // Execution role: used by the ECS agent to pull images and write logs
    const executionRole = new iam.Role(this, 'TaskExecutionRole', {
      roleName: `${stage.stageName}-rytmo-execution-role`,
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy'),
      ],
    });

    // Task role: used by the Quarkus application at runtime
    const taskRole = new iam.Role(this, 'TaskRole', {
      roleName: `${stage.stageName}-rytmo-task-role`,
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    taskRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'dynamodb:GetItem',
          'dynamodb:PutItem',
          'dynamodb:UpdateItem',
          'dynamodb:DeleteItem',
          'dynamodb:Query',
          'dynamodb:Scan',
        ],
        resources: [
          props.customersTableArn,
          `${props.customersTableArn}/index/*`,
          props.customerIdentitiesTableArn,
          `${props.customerIdentitiesTableArn}/index/*`,
        ],
      }),
    );

    // ── Logging ──────────────────────────────────────────────────────────────

    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: `/ecs/${stage.stageName}-rytmo`,
      retention: stage.isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: stage.isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // ── Task Definition ──────────────────────────────────────────────────────

    const taskDef = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      family: `${stage.stageName}-rytmo`,
      cpu: serviceConfig.cpu,
      memoryLimitMiB: serviceConfig.memoryLimitMiB,
      executionRole,
      taskRole,
    });

    taskDef.addContainer('server', {
      containerName: 'server',
      image: ecs.ContainerImage.fromEcrRepository(this.repository, 'latest'),
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'server', logGroup }),
      environment: {
        DYNAMODB_TABLE_CUSTOMERS: props.customersTableName,
        DYNAMODB_TABLE_CUSTOMER_IDENTITIES: props.customerIdentitiesTableName,
        BRIDGE_API_KEY: serviceConfig.bridgeApiKey,
        BRIDGE_BASE_URL: serviceConfig.bridgeBaseUrl,
        BRIDGE_LIQUIDATION_RETURN_ADDRESS: serviceConfig.bridgeLiquidationReturnAddress,
        BRIDGE_WEBHOOK_PUBLIC_KEY_PEM: serviceConfig.bridgeWebhookPublicKeyPem,
        PRIVY_APP_ID: serviceConfig.privyAppId,
        PRIVY_JWKS_URL: `https://auth.privy.io/api/v1/apps/${serviceConfig.privyAppId}/jwks.json`,
        PAGINATION_ENCRYPTION_KEY: serviceConfig.paginationEncryptionKey,
      },
      portMappings: [{ containerPort: 8080 }],
      // Liveness probe — ECS restarts the task if this fails
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost:8080/q/health/live || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60), // Allow JVM warm-up time
      },
    });

    // ── Security Groups ──────────────────────────────────────────────────────

    const albSg = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc,
      securityGroupName: `${stage.stageName}-alb-sg`,
      description: 'ALB — allow HTTP and HTTPS from the internet',
    });
    albSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'HTTP');
    albSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'HTTPS');

    const ecsSg = new ec2.SecurityGroup(this, 'EcsSg', {
      vpc,
      securityGroupName: `${stage.stageName}-ecs-sg`,
      description: 'ECS tasks — allow port 8080 from ALB only',
    });
    ecsSg.addIngressRule(albSg, ec2.Port.tcp(8080), 'From ALB');

    // ── Load Balancer ────────────────────────────────────────────────────────

    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      loadBalancerName: `${stage.stageName}-rytmo`,
      vpc,
      internetFacing: true,
      securityGroup: albSg,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // ACM certificate — DNS validated against the existing hosted zone
    const hostedZone = route53.HostedZone.fromLookup(this, 'HostedZone', {
      domainName: hostedZoneDomain,
    });

    const certificate = new acm.Certificate(this, 'Certificate', {
      domainName: apiDomain,
      validation: acm.CertificateValidation.fromDns(hostedZone),
    });

    const targetGroup = new elbv2.ApplicationTargetGroup(this, 'TargetGroup', {
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

    // HTTP → HTTPS redirect
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
      defaultAction: elbv2.ListenerAction.forward([targetGroup]),
    });

    // ── ECS Service ──────────────────────────────────────────────────────────

    // Tasks run in public subnets with a public IP — no NAT Gateway needed.
    // Inbound is locked down to port 8080 from the ALB security group only.
    const service = new ecs.FargateService(this, 'Service', {
      serviceName: `${stage.stageName}-rytmo`,
      cluster,
      taskDefinition: taskDef,
      desiredCount: serviceConfig.minCapacity,
      securityGroups: [ecsSg],
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      assignPublicIp: true,
    });

    service.attachToApplicationTargetGroup(targetGroup);

    // ── Auto Scaling ─────────────────────────────────────────────────────────

    const scaling = service.autoScaleTaskCount({
      minCapacity: serviceConfig.minCapacity,
      maxCapacity: serviceConfig.maxCapacity,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: serviceConfig.targetCpuUtilizationPercent,
      scaleInCooldown: cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(60),
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

    new cdk.CfnOutput(this, 'EcrRepositoryUri', {
      value: this.repository.repositoryUri,
      exportName: `${stage.stageName}-ecr-repository-uri`,
    });
  }
}
