package com.rytmo.server.persistence

import com.rytmo.library.persistence.PaginationTokenEncryptor
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDao
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.customers.CustomerDao
import com.rytmo.library.persistence.customers.CustomerService
import com.rytmo.library.services.BridgeCustomerService
import com.rytmo.library.services.BridgeService
import com.rytmo.library.services.CardAccountService
import com.rytmo.library.services.DFlowService
import com.rytmo.library.services.ExternalAccountService
import com.rytmo.library.services.HeliusService
import com.rytmo.library.services.JupiterService
import com.rytmo.library.services.KycService
import com.rytmo.library.services.LiquidationAddressService
import com.rytmo.library.services.OnboardingService
import com.rytmo.library.services.PrivyService
import com.rytmo.library.services.VirtualAccountService
import io.micrometer.core.instrument.MeterRegistry
import io.privy.api.PrivyApiClient
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.Optional

@ApplicationScoped
class DynamoDbProducerScope {
    @Produces
    @ApplicationScoped
    fun dynamoDbClient(@ConfigProperty(name = "AWS_PROFILE") awsProfile: String?, @ConfigProperty(name = "AWS_REGION") awsRegion: String): DynamoDbClient {
        if (awsProfile != null) {
            val provider = ProfileCredentialsProvider.create(awsProfile)
            return DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(provider)
                .build()
        }
        return DynamoDbClient.builder().region(Region.of(awsRegion)).build()
    }

    @Produces
    @ApplicationScoped
    fun dynamoDbEnhancedClient(dynamoDbClient: DynamoDbClient): DynamoDbEnhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build()

    @Produces
    @ApplicationScoped
    fun paginationTokenEncryptor(@ConfigProperty(name = "pagination.encryption-key") encryptionKey: String): PaginationTokenEncryptor = PaginationTokenEncryptor(encryptionKey)

    @Produces
    @Singleton
    fun customerDao(
        dynamoDbEnhancedClient: DynamoDbEnhancedClient,
        paginationTokenEncryptor: PaginationTokenEncryptor,
        @ConfigProperty(name = "dynamodb.table.customers") tableName: String,
    ): CustomerDao = CustomerDao(dynamoDbEnhancedClient, paginationTokenEncryptor, tableName)

    @Produces
    @Singleton
    fun customerService(customerDao: CustomerDao): CustomerService = CustomerService(customerDao)

    @Produces
    @Singleton
    fun customerIdentityDao(
        dynamoDbEnhancedClient: DynamoDbEnhancedClient,
        paginationTokenEncryptor: PaginationTokenEncryptor,
        @ConfigProperty(name = "dynamodb.table.customer-identities") tableName: String,
    ): CustomerIdentityDao = CustomerIdentityDao(dynamoDbEnhancedClient, paginationTokenEncryptor, tableName)

    @Produces
    @Singleton
    fun customerIdentityService(customerIdentityDao: CustomerIdentityDao): CustomerIdentityService = CustomerIdentityService(customerIdentityDao)

    @Produces
    @Singleton
    fun onboardingService(customerService: CustomerService, customerIdentityService: CustomerIdentityService, customerDao: CustomerDao): OnboardingService =
        OnboardingService(customerService, customerIdentityService, customerDao)

    @Produces
    @Singleton
    fun bridgeService(
        @ConfigProperty(name = "bridge.api-key") apiKey: String,
        @ConfigProperty(name = "bridge.base-url", defaultValue = "https://api.bridge.xyz/v0")
        baseUrl: String,
        meterRegistry: MeterRegistry,
    ): BridgeService = BridgeService.create(apiKey, baseUrl, meterRegistry)

    @Produces
    @Singleton
    fun kycService(bridgeService: BridgeService, customerIdentityService: CustomerIdentityService, customerService: CustomerService): KycService =
        KycService(bridgeService, customerIdentityService, customerService)

    @Produces
    @Singleton
    fun bridgeCustomerService(bridgeService: BridgeService, customerIdentityService: CustomerIdentityService): BridgeCustomerService = BridgeCustomerService(bridgeService, customerIdentityService)

    @Produces
    @Singleton
    fun privyApiClient(@ConfigProperty(name = "privy.app-id") privyAppId: String, @ConfigProperty(name = "privy.app-secret") privyAppSecret: String): PrivyApiClient =
        PrivyApiClient.builder().privyAppId(privyAppId).privyAppSecret(privyAppSecret).build()

    @Produces
    @Singleton
    fun privyService(privyApiClient: PrivyApiClient, meterRegistry: MeterRegistry): PrivyService = PrivyService(privyApiClient, meterRegistry)

    @Produces
    @Singleton
    fun virtualAccountService(bridgeService: BridgeService, privyService: PrivyService, customerIdentityService: CustomerIdentityService): VirtualAccountService =
        VirtualAccountService(bridgeService, privyService, customerIdentityService)

    @Produces
    @Singleton
    fun cardAccountService(bridgeService: BridgeService, customerIdentityService: CustomerIdentityService): CardAccountService = CardAccountService(bridgeService, customerIdentityService)

    @Produces
    @Singleton
    fun externalAccountService(bridgeService: BridgeService, customerIdentityService: CustomerIdentityService): ExternalAccountService = ExternalAccountService(bridgeService, customerIdentityService)

    @Produces
    @Singleton
    fun liquidationAddressService(
        bridgeService: BridgeService,
        customerIdentityService: CustomerIdentityService,
        @ConfigProperty(name = "bridge.liquidation.return-address") returnAddress: Optional<String>,
    ): LiquidationAddressService = LiquidationAddressService(bridgeService, returnAddress.orElse(""), customerIdentityService)

    @Produces
    @Singleton
    fun jupiterService(meterRegistry: MeterRegistry): JupiterService = JupiterService.create(meterRegistry)

    @Produces
    @Singleton
    fun dFlowService(@ConfigProperty(name = "dflow.base-url") baseUrl: String, meterRegistry: MeterRegistry): DFlowService = DFlowService.create(baseUrl, meterRegistry)

    @Produces
    @Singleton
    fun heliusService(@ConfigProperty(name = "helius.api-key") apiKey: String, meterRegistry: MeterRegistry): HeliusService = HeliusService.create(apiKey, meterRegistry)
}
