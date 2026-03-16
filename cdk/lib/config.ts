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
        // Non-sensitive
        bridgeBaseUrl: 'https://api.sandbox.bridge.xyz/v0',
        bridgeLiquidationReturnAddress: '',
        solanaUsdcMint: 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v',
        solanaCaip2: 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp',
        // Sensitive
        bridgeApiKey: 'sk-test-87445e39af1d96ba64f7ebc4dd68db0f',
        bridgeWebhookPublicKeyPem: '-----BEGIN PUBLIC KEY-----\\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxrV+s8CvC0+s1W6vZG52\\n5eozo6W6HzkTcLQMWDoEzQX+ulEoYH2fPuXeupi11MdVLpEqNqYas8LD3BIf/c9H\\nkK54V8vnXNwoHa5ROp/Gjp3B17q3wGfjLa8bQJoJZFWd9W+e3TjUohCDNpeD/qv+\\nbkY2y3b1QixmXKK3REw35sfiEe5NkGMU4aEfXhZieIZ1mKXLsIgsgrIpv9BFwQr5\\n+h3R7Vv3hGKVgSZHnRMa9F1/go8v5Au8gj+9w0LxxRJikoJCubI6igaTCivibxuo\\nQXWfFylw6m7eQTvZDQz70pnUEakofRlvKasetbyKmvLzMhuRHeqsxgi8C4ZCx7MP\\ndwIDAQAB\\n-----END PUBLIC KEY-----\\n',
        privyAppId: 'cmjuay7a303lyl20d48gc3c2i',
        privyAppSecret: 'privy_app_secret_ggZBwSp87ZGoU9ffJusmmWT7EsA5JBJBCfpErekA7KDYtaDTod328a8CS9ec5P1DMYSy6F9AA3mc3Js16vb1JXR',
          privyAuthorizationKey: 'wallet-auth:MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgmhBRPlMPO6aX/YaKb+EbnGOJG+ThETKFeMI3QZMffY6hRANCAASrim7n/9/R9d89PrncfmDpupp1OlOx8nQ6TqCCC0JAwR6XYwoMPw0PDF1a+JnsT8qqz3L1+GcZOb99Po/RfgRn',
        paginationEncryptionKey: 'rytmo-dev-key-32-chars-long!!',
        heliusApiKey: 'cda778e1-91fa-4d78-9fda-ec8ceed2f69c',
        swapSponsorshipMode: 'backend-wallet',
        swapFeePayerPrivateKey: process.env.SWAP_FEE_PAYER_PRIVATE_KEY ?? '',
      },
    },
  ],
};
