package com.rytmo.library.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rytmo.library.exceptions.DeframeException
import com.rytmo.models.yield.YieldBytecodeResponse
import com.rytmo.models.yield.YieldQuoteResponse
import com.rytmo.models.yield.YieldStrategiesResponse
import com.rytmo.models.yield.YieldStrategy
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DeframeService(
    private val apiKey: String,
    private val baseUrl: String,
    private val meterRegistry: MeterRegistry,
    private val client: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun <T> timed(operation: String, block: () -> T): T = Timer.builder("deframe.request")
        .tag("operation", operation)
        .register(meterRegistry)
        .recordCallable(block)!!

    private fun get(path: String): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey)
                .GET()
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DeframeStrategy(
        @com.fasterxml.jackson.annotation.JsonProperty("id") val id: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("slug") val slug: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("protocol") val protocol: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("asset") val asset: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("assetName") val assetName: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("network") val network: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("networkId") val networkId: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("apy") val apy: Double? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("paused") val paused: Boolean = false,
        @com.fasterxml.jackson.annotation.JsonProperty("availableActions")
        val availableActions: List<String> = emptyList(),
        @com.fasterxml.jackson.annotation.JsonProperty("logourl") val logoUrl: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("fee") val fee: String? = null,
    ) {
        fun toYieldStrategy() = YieldStrategy(
            id = id,
            slug = slug,
            protocol = protocol,
            asset = asset,
            assetName = assetName,
            network = network,
            networkId = networkId,
            apy = apy,
            paused = paused,
            availableActions = availableActions,
            logoUrl = logoUrl,
            fee = fee,
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DeframePagination(val totalRecords: Int = 0, val limit: Int = 10, val totalPages: Int = 0, val page: Int = 1, val hasPrevPage: Boolean = false, val hasNextPage: Boolean = false)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DeframeStrategiesPage(val data: List<DeframeStrategy> = emptyList(), val pagination: DeframePagination = DeframePagination())

    fun listStrategies(page: Int = 1, limit: Int = 10): YieldStrategiesResponse = timed("listStrategies") {
        val response = get("/strategies?page=$page&limit=$limit")
        log.debug("DFrame listStrategies [{}]: {}", response.statusCode(), response.body())
        if (response.statusCode() !in 200..299) {
            throw DeframeException(
                "DFrame listStrategies failed: ${response.body()}",
                response.statusCode(),
            )
        }
        val raw = objectMapper.readValue(response.body(), DeframeStrategiesPage::class.java)
        log.debug(
            "DFrame listStrategies parsed: totalRecords={} data={}",
            raw.pagination.totalRecords,
            raw.data.size,
        )
        YieldStrategiesResponse(
            strategies = raw.data.map { it.toYieldStrategy() },
            totalDocs = raw.pagination.totalRecords,
            limit = raw.pagination.limit,
            page = raw.pagination.page,
            totalPages = raw.pagination.totalPages,
            hasNextPage = raw.pagination.hasNextPage,
            hasPrevPage = raw.pagination.hasPrevPage,
        )
    }

    fun getStrategy(id: String): YieldStrategy = timed("getStrategy") {
        val response = get("/strategies/$id")
        if (response.statusCode() !in 200..299) {
            throw DeframeException(
                "DFrame getStrategy failed: ${response.body()}",
                response.statusCode(),
            )
        }
        objectMapper.readValue(response.body(), DeframeStrategy::class.java).toYieldStrategy()
    }

    fun getQuote(strategyId: String, amount: String, walletAddress: String): YieldQuoteResponse = timed("getQuote") {
        val response = get("/strategies/$strategyId/quote?amount=$amount&wallet=$walletAddress")
        if (response.statusCode() !in 200..299) {
            throw DeframeException(
                "DFrame getQuote failed: ${response.body()}",
                response.statusCode(),
            )
        }
        objectMapper.readValue(response.body(), YieldQuoteResponse::class.java)
    }

    fun getBytecode(strategyId: String, action: String, amount: String, walletAddress: String, fromToken: String? = null): YieldBytecodeResponse = timed("getBytecode") {
        val params = buildString {
            append("action=").append(action)
            append("&amount=").append(amount)
            append("&wallet=").append(walletAddress)
            if (fromToken != null) append("&fromToken=").append(fromToken)
        }
        val response = get("/strategies/$strategyId/bytecode?$params")
        if (response.statusCode() !in 200..299) {
            throw DeframeException(
                "DFrame getBytecode failed: ${response.body()}",
                response.statusCode(),
            )
        }
        objectMapper.readValue(response.body(), YieldBytecodeResponse::class.java)
    }

    companion object {
        fun create(apiKey: String, baseUrl: String = "https://api.deframe.io", meterRegistry: MeterRegistry = SimpleMeterRegistry()): DeframeService = DeframeService(apiKey, baseUrl, meterRegistry)
    }
}
