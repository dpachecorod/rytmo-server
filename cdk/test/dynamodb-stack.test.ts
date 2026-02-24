import * as cdk from 'aws-cdk-lib/core';
import { Template } from 'aws-cdk-lib/assertions';
import { DynamoDbStack } from '../lib/stacks/dynamodb-stack';

describe('DynamoDbStack', () => {
  test('creates customers table with correct configuration', () => {
    const app = new cdk.App();

    const stack = new DynamoDbStack(app, 'TestDynamoDbStack', {
      stage: {
        stageName: 'test',
        region: 'us-east-1',
        isProd: false,
      },
    });

    const template = Template.fromStack(stack);

    template.hasResourceProperties('AWS::DynamoDB::Table', {
      TableName: 'test-customers',
      KeySchema: [
        {
          AttributeName: 'id',
          KeyType: 'HASH',
        },
      ],
      BillingMode: 'PAY_PER_REQUEST',
      TimeToLiveSpecification: {
        AttributeName: 'ttl',
        Enabled: true,
      },
    });
  });

  test('production stage retains table on delete', () => {
    const app = new cdk.App();

    const stack = new DynamoDbStack(app, 'TestDynamoDbStack', {
      stage: {
        stageName: 'prod',
        region: 'us-east-1',
        isProd: true,
      },
    });

    const template = Template.fromStack(stack);

    template.hasResource('AWS::DynamoDB::Table', {
      DeletionPolicy: 'Retain',
      UpdateReplacePolicy: 'Retain',
    });
  });

  test('non-production stage destroys table on delete', () => {
    const app = new cdk.App();

    const stack = new DynamoDbStack(app, 'TestDynamoDbStack', {
      stage: {
        stageName: 'beta',
        region: 'us-east-1',
        isProd: false,
      },
    });

    const template = Template.fromStack(stack);

    template.hasResource('AWS::DynamoDB::Table', {
      DeletionPolicy: 'Delete',
    });
  });

  test('production stage enables point in time recovery', () => {
    const app = new cdk.App();

    const stack = new DynamoDbStack(app, 'TestDynamoDbStack', {
      stage: {
        stageName: 'prod',
        region: 'us-east-1',
        isProd: true,
      },
    });

    const template = Template.fromStack(stack);

    template.hasResourceProperties('AWS::DynamoDB::Table', {
      PointInTimeRecoverySpecification: {
        PointInTimeRecoveryEnabled: true,
      },
    });
  });
});
