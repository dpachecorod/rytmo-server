package com.rytmo.library.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rytmo.library.exceptions.DFlowException
import com.rytmo.models.swap.SwapQuoteResponse
import com.rytmo.models.swap.SwapStatusResponse
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DFlowService(private val baseUrl: String, private val meterRegistry: MeterRegistry) {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun <T> timed(operation: String, block: () -> T): T = Timer.builder("dflow.request")
        .tag("operation", operation)
        .register(meterRegistry)
        .recordCallable(block)!!

    fun getOrderQuote(userPublicKey: String, inputMint: String, outputMint: String, amount: String, slippageBps: String = "auto", priorityLevel: String = "high"): SwapQuoteResponse =
        timed("getOrderQuote") {
            val params = buildString {
                append("userPublicKey=").append(userPublicKey)
                append("&inputMint=").append(inputMint)
                append("&outputMint=").append(outputMint)
                append("&amount=").append(amount)
                append("&slippageBps=").append(slippageBps)
                append("&priorityLevel=").append(priorityLevel)
            }
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/order?$params"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw DFlowException(
                    "DFlow order quote failed: ${response.body()}",
                    response.statusCode(),
                )
            }
            objectMapper.readValue(response.body(), SwapQuoteResponse::class.java)
        }

    fun getOrderStatus(signature: String, lastValidBlockHeight: Long? = null): SwapStatusResponse = timed("getOrderStatus") {
        val params = buildString {
            append("signature=").append(signature)
            if (lastValidBlockHeight != null) {
                append("&lastValidBlockHeight=").append(lastValidBlockHeight)
            }
        }
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/order-status?$params"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw DFlowException(
                "DFlow order status failed: ${response.body()}",
                response.statusCode(),
            )
        }
        objectMapper.readValue(response.body(), SwapStatusResponse::class.java)
    }

    companion object {
        fun create(baseUrl: String, meterRegistry: MeterRegistry = SimpleMeterRegistry()): DFlowService = DFlowService(baseUrl, meterRegistry)
    }
}
