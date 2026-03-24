package com.rytmo.library.services

import com.rytmo.library.exceptions.DeframeException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DeframeServiceTest {
    private lateinit var mockClient: HttpClient
    private lateinit var service: DeframeService

    @BeforeEach
    fun setUp() {
        mockClient = mock()
        service =
            DeframeService(
                "test-api-key",
                "https://test.example.com",
                SimpleMeterRegistry(),
                mockClient,
            )
    }

    private fun mockResponse(status: Int, body: String): HttpResponse<String> {
        val response = mock<HttpResponse<String>>()
        whenever(response.statusCode()).thenReturn(status)
        whenever(response.body()).thenReturn(body)
        return response
    }

    private fun stubGet(response: HttpResponse<String>) {
        doReturn(response)
            .whenever(mockClient)
            .send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())
    }

    @Test
    fun `listStrategies returns response`() {
        val json =
            """{"data":[{"id":"strat-1","slug":"aave-usdc","protocol":"aave","asset":"0xusdc","assetName":"USDC","network":"base","networkId":"8453","apy":4.5,"paused":false,"availableActions":["lend","withdraw"],"logourl":null,"fee":null}],"pagination":{"totalRecords":1,"limit":10,"totalPages":1,"page":1,"hasPrevPage":false,"hasNextPage":false}}"""
        stubGet(mockResponse(200, json))

        val result = service.listStrategies()

        assertEquals(1, result.strategies.size)
        assertEquals("strat-1", result.strategies[0].id)
        assertEquals("aave", result.strategies[0].protocol)
        assertEquals("USDC", result.strategies[0].assetName)
        assertEquals(1, result.totalDocs)
        assertEquals(false, result.hasNextPage)
    }

    @Test
    fun `listStrategies wraps non-2xx as DeframeException`() {
        stubGet(mockResponse(500, "Internal Server Error"))

        val ex = assertThrows(DeframeException::class.java) { service.listStrategies() }
        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `getStrategy returns response`() {
        val json =
            """{"id":"strat-1","slug":"aave-usdc","protocol":"aave","asset":"0xusdc","assetName":"USDC","network":"base","networkId":"8453","apy":4.5,"paused":false,"availableActions":["lend","withdraw"],"logourl":null,"fee":null}"""
        stubGet(mockResponse(200, json))

        val result = service.getStrategy("strat-1")

        assertEquals("strat-1", result.id)
        assertEquals("aave", result.protocol)
        assertEquals("USDC", result.assetName)
    }

    @Test
    fun `getStrategy wraps 404`() {
        stubGet(mockResponse(404, "Not Found"))

        val ex = assertThrows(DeframeException::class.java) { service.getStrategy("unknown") }
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun `getQuote returns response`() {
        val json = """{"expectedReturn":"105.00","fee":"0.50","apy":4.5}"""
        stubGet(mockResponse(200, json))

        val result = service.getQuote("strat-1", "100000000", "0xwallet")

        assertEquals("105.00", result.expectedReturn)
        assertEquals("0.50", result.fee)
        assertEquals(4.5, result.apy)
    }

    @Test
    fun `getBytecode returns response for lend`() {
        val json =
            """{"feeCharged":"0","metadata":{"isCrossChain":false,"isSameChainSwap":false,"crossChainQuoteId":null},"bytecode":[{"chainId":8453,"to":"0xcontract","data":"0xdata","value":"0","from":"0xwallet"}]}"""
        val requestCaptor = argumentCaptor<HttpRequest>()
        doReturn(mockResponse(200, json))
            .whenever(mockClient)
            .send(requestCaptor.capture(), any<HttpResponse.BodyHandler<String>>())

        val result = service.getBytecode("strat-1", "lend", "100000000", "0xwallet")

        assertEquals("0", result.feeCharged)
        assertEquals(1, result.bytecode.size)
        assertEquals(8453, result.bytecode[0].chainId)
        assert(requestCaptor.firstValue.uri().toString().contains("action=lend"))
    }

    @Test
    fun `getBytecode returns response for withdraw`() {
        val json =
            """{"feeCharged":"0","metadata":{"isCrossChain":false,"isSameChainSwap":false,"crossChainQuoteId":null},"bytecode":[{"chainId":8453,"to":"0xcontract","data":"0xdata","value":null,"from":null}]}"""
        val requestCaptor = argumentCaptor<HttpRequest>()
        doReturn(mockResponse(200, json))
            .whenever(mockClient)
            .send(requestCaptor.capture(), any<HttpResponse.BodyHandler<String>>())

        val result = service.getBytecode("strat-1", "withdraw", "100000000", "0xwallet")

        assertEquals(1, result.bytecode.size)
        assert(requestCaptor.firstValue.uri().toString().contains("action=withdraw"))
    }

    @Test
    fun `getBytecode includes fromToken when provided`() {
        val json =
            """{"feeCharged":"0","metadata":{"isCrossChain":true,"isSameChainSwap":false,"crossChainQuoteId":"qid-123"},"bytecode":[{"chainId":8453,"to":"0xcontract","data":"0xdata","value":"0","from":"0xwallet"}]}"""
        val requestCaptor = argumentCaptor<HttpRequest>()
        doReturn(mockResponse(200, json))
            .whenever(mockClient)
            .send(requestCaptor.capture(), any<HttpResponse.BodyHandler<String>>())

        val result = service.getBytecode("strat-1", "lend", "100000000", "0xwallet", "0xusdc")

        assertEquals(true, result.metadata.isCrossChain)
        assertEquals("qid-123", result.metadata.crossChainQuoteId)
        assert(requestCaptor.firstValue.uri().toString().contains("fromToken=0xusdc"))
    }
}
