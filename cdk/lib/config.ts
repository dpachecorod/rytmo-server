import { AppConfig } from './types';

export const config: AppConfig = {
  appName: 'rytmo',
  accountId: '116933115593',
  hostedZoneDomain: 'rytmo.xyz',
  stages: [
    {
      stageName: 'beta',
      region: 'mx-central-1',
      isProd: false,
      serviceConfig: {
        cpu: 512,
        memoryLimitMiB: 1024,
        minCapacity: 1,
        maxCapacity: 3,
        targetCpuUtilizationPercent: 70,
        // Non-sensitive — hardcoded per stage
        bridgeBaseUrl: 'https://api.sandbox.bridge.xyz/v0',
        bridgeLiquidationReturnAddress: '',
        // Sensitive — loaded from environment at deploy time (see .env.example)
        bridgeApiKey: process.env.BRIDGE_API_KEY ?? '',
        bridgeWebhookPublicKeyPem: process.env.BRIDGE_WEBHOOK_PUBLIC_KEY_PEM ?? '',
        privyAppId: process.env.PRIVY_APP_ID ?? '',
        paginationEncryptionKey: process.env.PAGINATION_ENCRYPTION_KEY ?? '',
      },
    },
  ],
};
