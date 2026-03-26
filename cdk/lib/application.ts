import * as cdk from 'aws-cdk-lib/core';
import { AppConfig, Stage } from './types';
import { DynamoDbStack } from './stacks/dynamodb-stack';
import { NetworkStack } from './stacks/network-stack';
import { RoutingStack } from './stacks/routing-stack';
import { ServiceStack } from './stacks/service-stack';

export class Application {
  private readonly app: cdk.App;
  private readonly config: AppConfig;

  constructor(app: cdk.App, config: AppConfig) {
    this.app = app;
    this.config = config;
  }

  public deploy(): void {
    for (const stage of this.config.stages) {
      this.deployStage(stage);
    }
  }

  private deployStage(stage: Stage): void {
    const env: cdk.Environment = {
      account: this.config.accountId,
      region: stage.region,
    };

    const stackNamePrefix = `${this.config.appName}-${stage.stageName}`;
    const tags = {
      Application: this.config.appName,
      Stage: stage.stageName,
      Environment: stage.isProd ? 'production' : 'development',
    };

    const dynamoDbStack = new DynamoDbStack(this.app, `${stackNamePrefix}-dynamodb`, {
      env,
      stage,
      stackName: `${stackNamePrefix}-dynamodb`,
      tags,
    });

    const networkStack = new NetworkStack(this.app, `${stackNamePrefix}-network`, {
      env,
      stage,
      stackName: `${stackNamePrefix}-network`,
      tags,
    });

    const routingStack = new RoutingStack(this.app, `${stackNamePrefix}-routing`, {
      env,
      stage,
      hostedZoneDomain: this.config.hostedZoneDomain,
      vpc: networkStack.vpc,
      stackName: `${stackNamePrefix}-routing`,
      tags,
    });

    new ServiceStack(this.app, `${stackNamePrefix}-service`, {
      env,
      stage,
      vpc: networkStack.vpc,
      ecsSg: routingStack.ecsSg,
      targetGroup: routingStack.targetGroup,
      stackName: `${stackNamePrefix}-service`,
      customersTableName: dynamoDbStack.customersTable.tableName,
      customersTableArn: dynamoDbStack.customersTable.tableArn,
      customerIdentitiesTableName: dynamoDbStack.customerIdentitiesTable.tableName,
      customerIdentitiesTableArn: dynamoDbStack.customerIdentitiesTable.tableArn,
      deviceTokensTableName: dynamoDbStack.deviceTokensTable.tableName,
      deviceTokensTableArn: dynamoDbStack.deviceTokensTable.tableArn,
      tags,
    });
  }
}
