export interface Stage {
  stageName: string;
  region: string;
  isProd: boolean;
  serviceConfig?: ServiceConfig;
}

export interface ServiceConfig {
  // Container sizing
  cpu: number;
  memoryLimitMiB: number;
  // Autoscaling
  minCapacity: number;
  maxCapacity: number;
  targetCpuUtilizationPercent: number;
  // Non-sensitive config — hardcoded per stage
  bridgeBaseUrl: string;
  bridgeLiquidationReturnAddress: string;
  // Sensitive values — injected via env vars at deploy time (see .env.example)
  bridgeApiKey: string;
  bridgeWebhookPublicKeyPem: string;
  privyAppId: string;
  paginationEncryptionKey: string;
}

export interface AppConfig {
  appName: string;
  accountId: string;
  hostedZoneDomain: string;
  stages: Stage[];
}
