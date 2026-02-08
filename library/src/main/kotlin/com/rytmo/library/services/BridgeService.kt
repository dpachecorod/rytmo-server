package com.rytmo.library.services

import com.rytmo.library.bridge.api.KycLinksApi
import com.rytmo.library.bridge.invoker.ApiClient
import com.rytmo.library.bridge.invoker.ApiException
import com.rytmo.library.bridge.model.KycLinksGet200Response
import com.rytmo.library.bridge.model.KycLinksGet200ResponseDataInner
import com.rytmo.library.bridge.model.KycLinksPostRequest
import java.util.UUID

class BridgeService(private val kycLinksApi: KycLinksApi) {
    companion object {
        private const val DEFAULT_BASE_URL = "https://api.bridge.xyz/v0"

        fun create(apiKey: String, baseUrl: String = DEFAULT_BASE_URL): BridgeService {
            val apiClient = ApiClient()
            apiClient.updateBaseUri(baseUrl)
            apiClient.setRequestInterceptor { builder -> builder.header("Api-Key", apiKey) }
            return BridgeService(KycLinksApi(apiClient))
        }
    }

    fun createKycLink(fullName: String, email: String, type: String = "individual"): KycLinksGet200ResponseDataInner {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = KycLinksPostRequest().fullName(fullName).email(email).type(type)

        try {
            return kycLinksApi.kycLinksPost(idempotencyKey, request)
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to create KYC link: ${e.message}", e.code, e)
        }
    }

    fun getKycLinks(customerId: String): KycLinksGet200Response {
        try {
            return kycLinksApi.kycLinksGet(customerId)
        } catch (e: ApiException) {
            throw BridgeApiException("Failed to get KYC links: ${e.message}", e.code, e)
        }
    }
}

class BridgeApiException(message: String, val statusCode: Int, cause: Throwable? = null) : RuntimeException(message, cause)
