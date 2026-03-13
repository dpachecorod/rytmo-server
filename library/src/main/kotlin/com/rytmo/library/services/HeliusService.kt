package com.rytmo.library.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rytmo.models.swap.TokenBalance
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HeliusService(private val rpcUrl: String, private val meterRegistry: MeterRegistry) {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun <T> timed(operation: String, block: () -> T): T = Timer.builder("helius.request")
        .tag("operation", operation)
        .register(meterRegistry)
        .recordCallable(block)!!

    fun getTokenBalances(walletAddress: String): List<TokenBalance> = timed("getTokenBalances") {
        val body =
            objectMapper.writeValueAsString(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "helius-get-assets",
                    "method" to "getAssetsByOwner",
                    "params" to
                        mapOf(
                            "ownerAddress" to walletAddress,
                            "displayOptions" to
                                mapOf(
                                    "showFungible" to true,
                                    "showNativeBalance" to true,
                                ),
                        ),
                ),
            )
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(rpcUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val rpcResponse = objectMapper.readValue(response.body(), DasRpcResponse::class.java)
        val result = rpcResponse.result ?: return@timed emptyList()

        val tokens = mutableListOf<TokenBalance>()

        // Include native SOL balance
        val nativeBalance = result.nativeBalance
        if (nativeBalance != null) {
            tokens.add(
                TokenBalance(
                    mint = "So11111111111111111111111111111111111111112",
                    symbol = "SOL",
                    name = "Solana",
                    balance = nativeBalance.lamports.toString(),
                    decimals = 9,
                    priceUsd = nativeBalance.pricePerSol,
                    imageUrl =
                    "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/So11111111111111111111111111111111111111112/logo.png",
                ),
            )
        }

        // Include fungible tokens
        result.items
            .filter { it.interface_ in listOf("FungibleToken", "FungibleAsset") }
            .forEach { asset ->
                val token = asset.token_info ?: return@forEach
                tokens.add(
                    TokenBalance(
                        mint = asset.id,
                        symbol = token.symbol,
                        name = asset.content?.metadata?.name,
                        balance = token.balance?.toString() ?: "0",
                        decimals = token.decimals ?: 0,
                        priceUsd = token.price_info?.pricePerToken,
                        imageUrl = asset.content?.links?.image,
                    ),
                )
            }

        tokens
    }

    // Internal DAS response models
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DasRpcResponse(val result: DasResult?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DasResult(val items: List<DasAsset> = emptyList(), val nativeBalance: NativeBalance?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class NativeBalance(val lamports: Long = 0, @JsonProperty("price_per_sol") val pricePerSol: Double?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DasAsset(val id: String, @JsonProperty("interface") val interface_: String = "", val content: DasContent?, val token_info: TokenInfo?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DasContent(val metadata: DasMetadata?, val links: DasLinks?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DasMetadata(val name: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DasLinks(val image: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TokenInfo(val symbol: String?, val balance: Long?, val decimals: Int?, val price_info: PriceInfo?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PriceInfo(@JsonProperty("price_per_token") val pricePerToken: Double?)

    companion object {
        fun create(apiKey: String, meterRegistry: MeterRegistry = SimpleMeterRegistry()): HeliusService {
            val rpcUrl = "https://mainnet.helius-rpc.com/?api-key=$apiKey"
            return HeliusService(rpcUrl, meterRegistry)
        }
    }
}
