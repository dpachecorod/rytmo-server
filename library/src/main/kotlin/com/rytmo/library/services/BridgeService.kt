package com.rytmo.library.services

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.rytmo.library.bridge.api.CustomersApi
import com.rytmo.library.bridge.api.ExternalAccountsApi
import com.rytmo.library.bridge.api.KycLinksApi
import com.rytmo.library.bridge.api.LiquidationAddressesApi
import com.rytmo.library.bridge.api.VirtualAccountsApi
import com.rytmo.library.bridge.invoker.ApiClient
import com.rytmo.library.bridge.invoker.ApiException
import com.rytmo.library.bridge.model.CreateIndividualCustomerPayload
import com.rytmo.library.bridge.model.CreateKycLinks
import com.rytmo.library.bridge.model.CreateLiquidationAddress
import com.rytmo.library.bridge.model.CreateLiquidationAddressResponse
import com.rytmo.library.bridge.model.CreateVirtualAccount
import com.rytmo.library.bridge.model.CryptoCurrency
import com.rytmo.library.bridge.model.Customer
import com.rytmo.library.bridge.model.CustomersPostRequest
import com.rytmo.library.bridge.model.EndorsementType
import com.rytmo.library.bridge.model.EuroInclusiveCurrency
import com.rytmo.library.bridge.model.EuroInclusiveFiatCurrency
import com.rytmo.library.bridge.model.ExternalAccount1
import com.rytmo.library.bridge.model.ExternalAccountResponse
import com.rytmo.library.bridge.model.IndividualKycLinkResponse
import com.rytmo.library.bridge.model.LiquidationAddressSourceChain
import com.rytmo.library.bridge.model.LiquidationAddressSourceCurrency
import com.rytmo.library.bridge.model.LiquidationAddresses
import com.rytmo.library.bridge.model.OfframpChain
import com.rytmo.library.bridge.model.SepaSwiftInclusivePaymentRail
import com.rytmo.library.bridge.model.VirtualAccountDestination
import com.rytmo.library.bridge.model.VirtualAccountHistory
import com.rytmo.library.bridge.model.VirtualAccountResponse
import com.rytmo.library.bridge.model.VirtualAccountSourceInput
import com.rytmo.library.bridge.model.VirtualAccounts
import com.rytmo.models.externalaccounts.ExternalAccountAddress
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class BridgeService(
    private val apiClient: ApiClient,
    private val kycLinksApi: KycLinksApi,
    private val virtualAccountsApi: VirtualAccountsApi,
    private val externalAccountsApi: ExternalAccountsApi,
    private val liquidationAddressesApi: LiquidationAddressesApi,
    private val customersApi: CustomersApi,
) {
    val log = LoggerFactory.getLogger(this::class.java.name)

    private val objectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Caches the HttpClient so the same instance (and its connection pool) is reused
    // across all requests. The generated ApiClient.getHttpClient() calls builder.build()
    // on every invocation, which would create a new isolated client with no connection reuse.
    private class CachingApiClient : ApiClient() {
        private val cachedClient: HttpClient by lazy { super.getHttpClient() }

        override fun getHttpClient(): HttpClient = cachedClient
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.bridge.xyz/v0"
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

        fun create(apiKey: String, baseUrl: String = DEFAULT_BASE_URL): BridgeService {
            val apiClient = CachingApiClient()
            apiClient.updateBaseUri(baseUrl)
            apiClient.setRequestInterceptor { builder -> builder.header("Api-Key", apiKey) }
            apiClient.setConnectTimeout(CONNECT_TIMEOUT)
            apiClient.setReadTimeout(READ_TIMEOUT)
            return BridgeService(
                apiClient = apiClient,
                KycLinksApi(apiClient),
                VirtualAccountsApi(apiClient),
                ExternalAccountsApi(apiClient),
                LiquidationAddressesApi(apiClient),
                CustomersApi(apiClient),
            )
        }
    }

    fun createKycLink(fullName: String, email: String, type: String = "individual"): IndividualKycLinkResponse {
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        val request =
            CreateKycLinks()
                .fullName(fullName)
                .email(email)
                .type(CreateKycLinks.TypeEnum.fromValue(type))
                .endorsements(listOf(EndorsementType.SPEI, EndorsementType.BASE))

        try {
            return kycLinksApi.kycLinksPost(idempotencyKey, request)
        } catch (e: ApiException) {
            if (e.code == 400) {
                val existing = tryExtractExistingKycLink(e.responseBody)
                if (existing != null) {
                    log.info("KYC link already exists, returning existing one")
                    return existing
                }
            }
            log.error("Failed to create KYC link: ${e.message}", e)
            throw BridgeApiException("Failed to create KYC link: ${e.message}", e.code, e)
        }
    }

    private fun tryExtractExistingKycLink(responseBody: String?): IndividualKycLinkResponse? {
        if (responseBody == null) return null
        return try {
            val node = objectMapper.readTree(responseBody)
            if (node.get("code")?.asText() == "duplicate_record") {
                val existingNode = node.get("existing_kyc_link") ?: return null
                objectMapper.treeToValue(existingNode, IndividualKycLinkResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to parse duplicate KYC link response: ${e.message}")
            null
        }
    }

    fun createCustomer(firstName: String, lastName: String, email: String): Customer {
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        val request =
            CustomersPostRequest(
                CreateIndividualCustomerPayload().firstName(firstName).lastName(lastName).email(email),
            )

        try {
            return customersApi.customersPost(idempotencyKey, request)
        } catch (e: ApiException) {
            log.error("Failed to create Bridge customer: ${e.message}", e)
            throw BridgeApiException("Failed to create Bridge customer: ${e.message}", e.code, e)
        }
    }

    fun getKycLinks(customerId: String): IndividualKycLinkResponse {
        try {
            val response = kycLinksApi.kycLinksGet(customerId, null, null, null, null)
            return response.data.firstOrNull()
                ?: throw BridgeApiException("No KYC link found for customer: $customerId", 404)
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to get KYC links: ${e.message}", e.code, e)
        }
    }

    fun createVirtualAccount(customerId: String, sourceCurrency: String, destinationCurrency: String, destinationPaymentRail: String, destinationAddress: String): VirtualAccountResponse {
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        val source =
            VirtualAccountSourceInput().currency(EuroInclusiveFiatCurrency.fromValue(sourceCurrency))
        val destination =
            VirtualAccountDestination()
                .currency(CryptoCurrency.fromValue(destinationCurrency))
                .paymentRail(OfframpChain.fromValue(destinationPaymentRail))
                .address(destinationAddress)
        log.info("Creating virtual account with source: $source, destination: $destination")
        val request = CreateVirtualAccount().source(source).destination(destination)

        try {
            return virtualAccountsApi.customersCustomerIDVirtualAccountsPost(
                idempotencyKey,
                customerId,
                request,
            )
        } catch (e: ApiException) {
            log.error("Failed to create virtual account: ${e.message}", e)
            throw BridgeApiException("Failed to create virtual account: ${e.message}", e.code, e)
        }
    }

    fun listVirtualAccounts(customerId: String): VirtualAccounts {
        try {
            return virtualAccountsApi.customersCustomerIDVirtualAccountsGet(
                customerId,
                null,
                null,
                null,
                null,
            )
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to list virtual accounts: ${e.message}", e.code, e)
        }
    }

    fun getVirtualAccountActivity(customerId: String, virtualAccountId: String): VirtualAccountHistory {
        try {
            return virtualAccountsApi.customersCustomerIDVirtualAccountsVirtualAccountIDHistoryGet(
                customerId,
                virtualAccountId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            )
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to get virtual account activity: ${e.message}", e.code, e)
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private data class CreateUsExternalAccountBody(
        @field:JsonProperty("account_type") val accountType: String = "us",
        @field:JsonProperty("account_owner_name") val accountOwnerName: String,
        @field:JsonProperty("routing_number") val routingNumber: String?,
        @field:JsonProperty("account_number") val accountNumber: String,
        @field:JsonProperty("address") val address: Map<String, String?>?,
    )

    fun createExternalAccount(
        customerId: String,
        accountType: String,
        accountOwnerName: String,
        routingNumber: String?,
        accountNumber: String,
        address: ExternalAccountAddress,
    ): ExternalAccountResponse {
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        val addressMap =
            mapOf(
                "street_line_1" to address.streetLine1,
                "street_line_2" to address.streetLine2,
                "city" to address.city,
                "state" to address.state,
                "postal_code" to address.postalCode,
                "country" to address.country,
            )
        val requestBody =
            CreateUsExternalAccountBody(
                accountType = accountType,
                accountOwnerName = accountOwnerName,
                routingNumber = routingNumber,
                accountNumber = accountNumber,
                address = addressMap,
            )
        log.info("Creating external account with body: $requestBody")

        val httpClient = apiClient.httpClient
        val bodyBytes = apiClient.objectMapper.writeValueAsBytes(requestBody)
        val uri = URI.create(apiClient.baseUri + "/customers/$customerId/external_accounts")
        val requestBuilder =
            HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
        apiClient.requestInterceptor?.accept(requestBuilder)

        try {
            val response =
                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() / 100 != 2) {
                val body = response.body()?.readBytes()?.toString(Charsets.UTF_8)
                log.error("Failed to create external account: status=${response.statusCode()}, body=$body")
                throw BridgeApiException(
                    "Failed to create external account: HTTP ${response.statusCode()}",
                    response.statusCode(),
                )
            }
            return apiClient.objectMapper.readValue(response.body(), ExternalAccountResponse::class.java)
        } catch (e: BridgeApiException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to create external account: ${e.message}", e)
            throw BridgeApiException("Failed to create external account: ${e.message}", 500, e)
        }
    }

    fun listExternalAccounts(customerId: String): ExternalAccount1 {
        try {
            return externalAccountsApi.customersCustomerIDExternalAccountsGet(
                customerId,
                null,
                null,
                null,
            )
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to list external accounts: ${e.message}", e.code, e)
        }
    }

    fun listLiquidationAddresses(customerId: String): LiquidationAddresses {
        try {
            return liquidationAddressesApi.customersCustomerIDLiquidationAddressesGet(
                customerId,
                null,
                null,
                null,
            )
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to list liquidation addresses: ${e.message}", e.code, e)
        }
    }

    fun createLiquidationAddress(
        customerId: String,
        currency: String,
        chain: String,
        externalAccountId: String,
        destinationPaymentRail: String,
        destinationCurrency: String,
        returnAddress: String,
    ): CreateLiquidationAddressResponse {
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        val request =
            CreateLiquidationAddress()
                .currency(LiquidationAddressSourceCurrency.fromValue(currency))
                .chain(LiquidationAddressSourceChain.fromValue(chain))
                .externalAccountId(externalAccountId)
                .destinationPaymentRail(SepaSwiftInclusivePaymentRail.fromValue(destinationPaymentRail))
                .destinationCurrency(EuroInclusiveCurrency.fromValue(destinationCurrency))
                .returnAddress(returnAddress)

        try {
            return liquidationAddressesApi.customersCustomerIDLiquidationAddressesPost(
                customerId,
                idempotencyKey,
                request,
            )
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to create liquidation address: ${e.message}", e.code, e)
        }
    }
}

class BridgeApiException(message: String, val statusCode: Int, cause: Throwable? = null) : RuntimeException(message, cause)
