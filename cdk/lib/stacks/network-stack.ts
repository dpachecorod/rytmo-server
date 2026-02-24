import * as cdk from 'aws-cdk-lib/core';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';
import { Stage } from '../types';

export interface NetworkStackProps extends cdk.StackProps {
  stage: Stage;
}

export class NetworkStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;

  constructor(scope: Construct, id: string, props: NetworkStackProps) {
    super(scope, id, props);

    const { stage } = props;

    this.vpc = new ec2.Vpc(this, 'Vpc', {
      vpcName: `${stage.stageName}-vpc`,
      maxAzs: 1,
      natGateways: 0,
      subnetConfiguration: [
        {
          name: 'public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
      ],
    });

    // Free gateway endpoint — DynamoDB traffic stays within the AWS network
    // and doesn't consume the task's public IP bandwidth
    this.vpc.addGatewayEndpoint('DynamoDbEndpoint', {
      service: ec2.GatewayVpcEndpointAwsService.DYNAMODB,
    });
  }
}
