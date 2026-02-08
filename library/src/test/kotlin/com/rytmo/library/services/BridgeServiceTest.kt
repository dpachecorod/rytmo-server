package com.rytmo.library.services

import com.rytmo.library.bridge.api.KycLinksApi
import com.rytmo.library.bridge.invoker.ApiException
import com.rytmo.library.bridge.model.KycLinksGet200ResponseDataInner
import com.rytmo.library.bridge.model.KycLinksPostRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BridgeServiceTest {
    private lateinit var kycLinksApi: KycLinksApi
    private lateinit var bridgeService: BridgeService

    @BeforeEach
    fun setUp() {
        kycLinksApi = mock()
        bridgeService = BridgeService(kycLinksApi)
    }

    @Test
    fun `createKycLink should call API with correct parameters`() {
        val expectedResponse =
            KycLinksGet200ResponseDataInner()
                .id("kyc_link_123")
                .fullName("John Doe")
                .email("johndoe@example.com")
                .kycLink("https://kyc.bridge.xyz/abc123")
                .kycStatus("not_started")
                .tosLink("https://tos.bridge.xyz/abc123")
                .tosStatus("pending")
                .createdAt("2024-01-15T10:00:00Z")

        val requestCaptor = argumentCaptor<KycLinksPostRequest>()
        whenever(kycLinksApi.kycLinksPost(any(), requestCaptor.capture())).thenReturn(expectedResponse)

        val result =
            bridgeService.createKycLink(
                fullName = "John Doe",
                email = "johndoe@example.com",
                type = "individual",
            )

        assertEquals(expectedResponse.id, result.id)
        assertEquals(expectedResponse.fullName, result.fullName)
        assertEquals(expectedResponse.email, result.email)
        assertEquals(expectedResponse.kycLink, result.kycLink)
        assertEquals(expectedResponse.kycStatus, result.kycStatus)

        val capturedRequest = requestCaptor.firstValue
        assertEquals("John Doe", capturedRequest.fullName)
        assertEquals("johndoe@example.com", capturedRequest.email)
        assertEquals("individual", capturedRequest.type)
    }

    @Test
    fun `createKycLink should generate idempotency key`() {
        val idempotencyKeyCaptor = argumentCaptor<String>()
        whenever(kycLinksApi.kycLinksPost(idempotencyKeyCaptor.capture(), any()))
            .thenReturn(KycLinksGet200ResponseDataInner())

        bridgeService.createKycLink(
            fullName = "John Doe",
            email = "johndoe@example.com",
        )

        val idempotencyKey = idempotencyKeyCaptor.firstValue
        assertNotNull(idempotencyKey)
        assertEquals(36, idempotencyKey.length)
    }

    @Test
    fun `createKycLink should use default type individual`() {
        val requestCaptor = argumentCaptor<KycLinksPostRequest>()
        whenever(kycLinksApi.kycLinksPost(any(), requestCaptor.capture()))
            .thenReturn(KycLinksGet200ResponseDataInner())

        bridgeService.createKycLink(
            fullName = "John Doe",
            email = "johndoe@example.com",
        )

        assertEquals("individual", requestCaptor.firstValue.type)
    }

    @Test
    fun `createKycLink should wrap API exception`() {
        whenever(kycLinksApi.kycLinksPost(any(), any())).thenThrow(ApiException(400, "Bad Request"))

        val exception =
            assertThrows(BridgeApiException::class.java) {
                bridgeService.createKycLink(
                    fullName = "John Doe",
                    email = "johndoe@example.com",
                )
            }

        assertEquals(400, exception.statusCode)
    }
}
