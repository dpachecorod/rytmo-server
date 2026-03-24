import * as cdk from 'aws-cdk-lib/core';
import * as cw from 'aws-cdk-lib/aws-cloudwatch';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecrAssets from 'aws-cdk-lib/aws-ecr-assets';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import * as fs from 'fs';
import * as path from 'path';
import { Construct } from 'constructs';
import { Stage } from '../types';

export interface ServiceStackProps extends cdk.StackProps {
  stage: Stage;
  vpc: ec2.Vpc;
  ecsSg: ec2.SecurityGroup;
  targetGroup: elbv2.ApplicationTargetGroup;
  customersTableName: string;
  customersTableArn: string;
  customerIdentitiesTableName: string;
  customerIdentitiesTableArn: string;
}

export class ServiceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: ServiceStackProps) {
    super(scope, id, props);

    const { stage, vpc, ecsSg, targetGroup } = props;
    const serviceConfig = stage.serviceConfig!;

    // ── Docker Image ─────────────────────────────────────────────────────────

    const image = ecs.ContainerImage.fromAsset(path.join(__dirname, '../../../server'), {
      file: 'src/main/docker/Dockerfile.jvm',
      platform: ecrAssets.Platform.LINUX_AMD64,
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

    taskRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
      }),
    );

    // ── Logging ──────────────────────────────────────────────────────────────

    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: `/ecs/${stage.stageName}-rytmo`,
      retention: stage.isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: stage.isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // ── SSM Parameters ───────────────────────────────────────────────────────

    const ssmPrefix = `/${stage.stageName}/rytmo`;

    const requiredSecrets: Record<string, string> = {
      BRIDGE_API_KEY: serviceConfig.bridgeApiKey,
      BRIDGE_WEBHOOK_PUBLIC_KEY_PEM: serviceConfig.bridgeWebhookPublicKeyPem,
      PRIVY_APP_ID: serviceConfig.privyAppId,
      PRIVY_APP_SECRET: serviceConfig.privyAppSecret,
      PRIVY_AUTHORIZATION_KEY: serviceConfig.privyAuthorizationKey,
      PAGINATION_ENCRYPTION_KEY: serviceConfig.paginationEncryptionKey,
      HELIUS_API_KEY: serviceConfig.heliusApiKey,
    };
    const missing = Object.entries(requiredSecrets)
      .filter(([, v]) => !v)
      .map(([k]) => k);
    if (missing.length > 0) {
      throw new Error(`Missing required secrets in cdk/.env: ${missing.join(', ')}`);
    }

    const mkParam = (id: string, name: string, value: string) =>
      new ssm.StringParameter(this, id, {
        parameterName: `${ssmPrefix}/${name}`,
        stringValue: value,
      });

    const bridgeApiKeyParam = mkParam('BridgeApiKey', 'bridge-api-key', serviceConfig.bridgeApiKey);
    const bridgeWebhookKeyParam = mkParam('BridgeWebhookKey', 'bridge-webhook-public-key-pem', serviceConfig.bridgeWebhookPublicKeyPem);
    const privyAppIdParam = mkParam('PrivyAppId', 'privy-app-id', serviceConfig.privyAppId);
    const privyAppSecretParam = mkParam('PrivyAppSecret', 'privy-app-secret', serviceConfig.privyAppSecret);
    const privyAuthorizationKeyParam = mkParam('PrivyAuthorizationKey', 'privy-authorization-key', serviceConfig.privyAuthorizationKey);
    const paginationKeyParam = mkParam('PaginationKey', 'pagination-encryption-key', serviceConfig.paginationEncryptionKey);
    const heliusApiKeyParam = mkParam('HeliusApiKey', 'helius-api-key', serviceConfig.heliusApiKey);

    const swapFeePayerKeyParam = mkParam('SwapFeePayerKey', 'swap-fee-payer-private-key', serviceConfig.swapFeePayerPrivateKey || ' ');

    const deframeApiKeyParam = mkParam('DeframeApiKey', 'deframe-api-key', serviceConfig.deframeApiKey || ' ');

    // ── Assets Bucket ────────────────────────────────────────────────────────

    const assetsBucket = new s3.Bucket(this, 'AssetsBucket', {
      bucketName: `${stage.stageName}-rytmo-assets`,
      blockPublicAccess: new s3.BlockPublicAccess({
        blockPublicAcls: true,
        ignorePublicAcls: true,
        blockPublicPolicy: false,
        restrictPublicBuckets: false,
      }),
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    assetsBucket.addToResourcePolicy(
      new iam.PolicyStatement({
        actions: ['s3:GetObject'],
        resources: [assetsBucket.arnForObjects('tokens/*')],
        principals: [new iam.StarPrincipal()],
      }),
    );

    const assetsBaseUrl = `https://${assetsBucket.bucketRegionalDomainName}`;

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
      image,
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'server', logGroup }),
      environment: {
        DYNAMODB_TABLE_CUSTOMERS: props.customersTableName,
        DYNAMODB_TABLE_CUSTOMER_IDENTITIES: props.customerIdentitiesTableName,
        BRIDGE_BASE_URL: serviceConfig.bridgeBaseUrl,
        BRIDGE_LIQUIDATION_RETURN_ADDRESS: serviceConfig.bridgeLiquidationReturnAddress,
        PRIVY_JWKS_URL: `https://auth.privy.io/api/v1/apps/${serviceConfig.privyAppId}/jwks.json`,
        SOLANA_USDC_MINT: serviceConfig.solanaUsdcMint,
        PRIVY_SOLANA_CAIP2: serviceConfig.solanaCaip2,
        SWAP_SPONSORSHIP_MODE: serviceConfig.swapSponsorshipMode,
        ASSETS_BASE_URL: assetsBaseUrl,
        DEFRAME_BASE_URL: 'https://api.deframe.io',
        AWS_REGION: stage.region,
        METRICS_CLOUDWATCH_ENABLED: 'true',
        METRICS_CLOUDWATCH_NAMESPACE: `${stage.stageName}/rytmo-api`,
      },
      secrets: {
        BRIDGE_API_KEY: ecs.Secret.fromSsmParameter(bridgeApiKeyParam),
        BRIDGE_WEBHOOK_PUBLIC_KEY_PEM: ecs.Secret.fromSsmParameter(bridgeWebhookKeyParam),
        PRIVY_APP_ID: ecs.Secret.fromSsmParameter(privyAppIdParam),
        PRIVY_APP_SECRET: ecs.Secret.fromSsmParameter(privyAppSecretParam),
        PRIVY_AUTHORIZATION_KEY: ecs.Secret.fromSsmParameter(privyAuthorizationKeyParam),
        PAGINATION_ENCRYPTION_KEY: ecs.Secret.fromSsmParameter(paginationKeyParam),
        HELIUS_API_KEY: ecs.Secret.fromSsmParameter(heliusApiKeyParam),
        SWAP_FEE_PAYER_PRIVATE_KEY: ecs.Secret.fromSsmParameter(swapFeePayerKeyParam),
        DEFRAME_API_KEY: ecs.Secret.fromSsmParameter(deframeApiKeyParam),
      },
      portMappings: [{ containerPort: 8080 }],
      // Liveness probe - ECS restarts the task if this fails
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost:8080/q/health/live || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60), // Allow JVM warm-up time
      },
    });

    // ── ECS Service ──────────────────────────────────────────────────────────

    // Tasks run in public subnets with a public IP - no NAT Gateway needed.
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

    // ── CloudWatch Dashboard ─────────────────────────────────────────────────

    const cwNamespace = `${stage.stageName}/rytmo-api`;

    // Load endpoints from the OpenAPI spec generated by `./gradlew build`.
    // Re-run `./gradlew build` before `cdk deploy` to pick up new endpoints.
    const openApiSpecPath = path.join(__dirname, '../../../server/build/openapi/openapi.json');
    const apiEndpoints: Array<{ method: string; uri: string }> = [];
    if (fs.existsSync(openApiSpecPath)) {
      const spec = JSON.parse(fs.readFileSync(openApiSpecPath, 'utf-8')) as {
        paths?: Record<string, Record<string, unknown>>;
      };
      for (const [uri, pathItem] of Object.entries(spec.paths ?? {})) {
        for (const method of ['get', 'post', 'put', 'delete', 'patch']) {
          if (pathItem[method]) {
            apiEndpoints.push({ method: method.toUpperCase(), uri });
          }
        }
      }
    }

    // One metric per endpoint, driven by the OpenAPI spec.
    const endpointMetrics = (metricName: string, stat: string): cw.IMetric[] =>
      apiEndpoints.map(
        ({ method, uri }) =>
          new cw.Metric({
            namespace: cwNamespace,
            metricName,
            dimensionsMap: { method, uri },
            statistic: stat,
            period: cdk.Duration.minutes(1),
            label: `${method} ${uri}`,
          }),
      );

    // SEARCH expression - discovers all matching metrics at runtime without hardcoding.
    const search = (schema: string, metricName: string, stat: string, extraFilter = ''): cw.MathExpression => {
      const filter = extraFilter ? ` ${extraFilter}` : '';
      return new cw.MathExpression({
        expression: `SEARCH('{${cwNamespace},${schema}}${filter} MetricName="${metricName}"', '${stat}', 60)`,
        period: cdk.Duration.minutes(1),
      });
    };

    new cw.Dashboard(this, 'Dashboard', {
      dashboardName: `${stage.stageName}-rytmo-api`,
      widgets: [
        [
          new cw.GraphWidget({
            title: 'TPS per endpoint (req/min)',
            left: endpointMetrics('http.server.requests.count', 'Sum'),
            width: 12,
            height: 6,
          }),
          new cw.GraphWidget({
            title: 'Max Latency per endpoint (ms)',
            left: endpointMetrics('http.server.requests.max', 'Maximum'),
            width: 12,
            height: 6,
          }),
        ],
        [
          new cw.GraphWidget({
            title: '4xx Errors by endpoint',
            left: [search('method,uri,outcome', 'http.server.requests.count', 'Sum', 'outcome="CLIENT_ERROR"')],
            width: 12,
            height: 6,
          }),
          new cw.GraphWidget({
            title: '5xx Errors by endpoint',
            left: [search('method,uri,outcome', 'http.server.requests.count', 'Sum', 'outcome="SERVER_ERROR"')],
            width: 12,
            height: 6,
          }),
        ],
        [
          new cw.GraphWidget({
            title: 'Bridge Dependency Max Latency (ms) - all operations',
            left: [search('operation', 'bridge.request.max', 'Maximum')],
            width: 12,
            height: 6,
          }),
          new cw.GraphWidget({
            title: 'Privy Dependency Max Latency (ms) - all operations',
            left: [search('operation', 'privy.request.max', 'Maximum')],
            width: 12,
            height: 6,
          }),
        ],
      ],
    });
  }
}
