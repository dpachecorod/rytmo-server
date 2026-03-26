import * as cdk from 'aws-cdk-lib/core';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';
import { Stage } from '../types';

export interface DynamoDbStackProps extends cdk.StackProps {
  stage: Stage;
}

export class DynamoDbStack extends cdk.Stack {
  public readonly customersTable: dynamodb.Table;
  public readonly customerIdentitiesTable: dynamodb.Table;
  public readonly deviceTokensTable: dynamodb.Table;

  constructor(scope: Construct, id: string, props: DynamoDbStackProps) {
    super(scope, id, props);

    const { stage } = props;

    // Customers table
    this.customersTable = new dynamodb.Table(this, 'CustomersTable', {
      tableName: `${stage.stageName}-customers`,
      partitionKey: {
        name: 'id',
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: stage.isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      pointInTimeRecoverySpecification: {
        pointInTimeRecoveryEnabled: stage.isProd,
      },
      timeToLiveAttribute: 'ttl',
    });

    // GSI for email lookup (used for uniqueness check during onboarding)
    this.customersTable.addGlobalSecondaryIndex({
      indexName: 'email-index',
      partitionKey: {
        name: 'email',
        type: dynamodb.AttributeType.STRING,
      },
      projectionType: dynamodb.ProjectionType.KEYS_ONLY,
    });

    // Customer identities table (maps external IDs to internal customer IDs)
    this.customerIdentitiesTable = new dynamodb.Table(this, 'CustomerIdentitiesTable', {
      tableName: `${stage.stageName}-customer-identities`,
      partitionKey: {
        name: 'internalCustomerId',
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: 'provider',
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: stage.isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      pointInTimeRecoverySpecification: {
        pointInTimeRecoveryEnabled: stage.isProd,
      },
      timeToLiveAttribute: 'ttl',
    });

    // GSI for reverse lookup (external ID -> internal customer ID)
    this.customerIdentitiesTable.addGlobalSecondaryIndex({
      indexName: 'externalId-index',
      partitionKey: {
        name: 'externalId',
        type: dynamodb.AttributeType.STRING,
      },
      projectionType: dynamodb.ProjectionType.ALL,
    });

    // Device tokens table (Expo push notification tokens, client-managed)
    this.deviceTokensTable = new dynamodb.Table(this, 'DeviceTokensTable', {
      tableName: `${stage.stageName}-device-tokens`,
      partitionKey: { name: 'customerId', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'tokenHash', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: stage.isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      pointInTimeRecoverySpecification: {
        pointInTimeRecoveryEnabled: stage.isProd,
      },
    });

    // Outputs
    new cdk.CfnOutput(this, 'CustomersTableName', {
      value: this.customersTable.tableName,
      exportName: `${stage.stageName}-customers-table-name`,
    });

    new cdk.CfnOutput(this, 'CustomersTableArn', {
      value: this.customersTable.tableArn,
      exportName: `${stage.stageName}-customers-table-arn`,
    });

    new cdk.CfnOutput(this, 'CustomerIdentitiesTableName', {
      value: this.customerIdentitiesTable.tableName,
      exportName: `${stage.stageName}-customer-identities-table-name`,
    });

    new cdk.CfnOutput(this, 'CustomerIdentitiesTableArn', {
      value: this.customerIdentitiesTable.tableArn,
      exportName: `${stage.stageName}-customer-identities-table-arn`,
    });

    new cdk.CfnOutput(this, 'DeviceTokensTableName', {
      value: this.deviceTokensTable.tableName,
      exportName: `${stage.stageName}-device-tokens-table-name`,
    });

    new cdk.CfnOutput(this, 'DeviceTokensTableArn', {
      value: this.deviceTokensTable.tableArn,
      exportName: `${stage.stageName}-device-tokens-table-arn`,
    });
  }
}
