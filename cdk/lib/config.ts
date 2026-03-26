import { AppConfig } from './types';

// Secrets are read from environment variables — set them in ~/.zprofile, never hardcode.
const env = (key: string): string => {
  const value = process.env[key];
  if (!value) throw new Error(`Missing required environment variable: ${key}`);
  return value;
};

const optionalEnv = (key: string): string => process.env[key] ?? '';

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
        // Container sizing
        cpu: 512,
        memoryLimitMiB: 1024,
        // Autoscaling
        minCapacity: 1,
        maxCapacity: 3,
        targetCpuUtilizationPercent: 70,
        // Non-sensitive — safe to hardcode per stage
        bridgeBaseUrl: 'https://api.bridge.xyz/v0',
        bridgeLiquidationReturnAddress: '9DbrgujQjkWTuVojJKNfDsgcEQXrSRWwGrnsTwN1mvbe',
        solanaUsdcMint: 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v',
        solanaCaip2: 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',
        feePayerWalletId: optionalEnv('SWAP_FEE_PAYER_WALLET_ID'),
        feePayerWalletAddress: optionalEnv('SWAP_FEE_PAYER_WALLET_ADDRESS'),
        // Sensitive — loaded from cdk/.env at deploy time
        bridgeApiKey: env('BRIDGE_API_KEY'),
        bridgeWebhookPublicKeyPem: env('BRIDGE_WEBHOOK_PUBLIC_KEY_PEM'),
        privyAppId: env('PRIVY_APP_ID'),
        privyAppSecret: env('PRIVY_APP_SECRET'),
        privyAuthorizationKey: env('PRIVY_AUTHORIZATION_KEY'),
        paginationEncryptionKey: env('PAGINATION_ENCRYPTION_KEY'),
        heliusApiKey: env('HELIUS_API_KEY'),
        deframeApiKey: optionalEnv('DEFRAME_API_KEY'),
        privyWebhookSecret: optionalEnv('PRIVY_WEBHOOK_SECRET'),
      },
    },
  ],
};
