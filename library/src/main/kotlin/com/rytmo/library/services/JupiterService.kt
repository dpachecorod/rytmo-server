package com.rytmo.library.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rytmo.models.swap.OutputToken
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

class JupiterService(private val assetsBaseUrl: String, private val meterRegistry: MeterRegistry) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Volatile private var cachedTokens: List<OutputToken> = emptyList()

    @Volatile private var cacheExpiry: Instant = Instant.EPOCH

    private val allowlist: Map<String, AllowlistEntry> by lazy {
        val stream =
            javaClass.getResourceAsStream("/token-allowlist.json")
                ?: error("token-allowlist.json not found on classpath")
        val entries =
            objectMapper.readValue(
                stream,
                objectMapper.typeFactory.constructCollectionType(
                    List::class.java,
                    AllowlistEntry::class.java,
                ),
            ) as List<AllowlistEntry>
        entries.associateBy { it.symbol }
    }

    private fun <T> timed(operation: String, block: () -> T): T = Timer.builder("jupiter.request")
        .tag("operation", operation)
        .register(meterRegistry)
        .recordCallable(block)!!

    fun getOutputTokens(): List<OutputToken> {
        if (Instant.now().isBefore(cacheExpiry)) return cachedTokens
        return timed("getOutputTokens") {
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create("https://lite-api.jup.ag/tokens/v2/tag?query=verified"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.error("Jupiter API returned ${response.statusCode()}: ${response.body()}")
                error("Jupiter API error: ${response.statusCode()}")
            }
            val jupiterTokens =
                objectMapper.readValue(
                    response.body(),
                    objectMapper.typeFactory.constructCollectionType(
                        List::class.java,
                        JupiterToken::class.java,
                    ),
                ) as List<JupiterToken>
            val tokens =
                jupiterTokens.mapNotNull { t ->
                    val entry = allowlist[t.symbol]?.takeIf { it.enabled } ?: return@mapNotNull null
                    OutputToken(
                        mint = t.id,
                        symbol = t.symbol,
                        name = entry.name,
                        decimals = t.decimals,
                        logoURI = entry.logo?.let { "$assetsBaseUrl/tokens/$it" },
                    )
                }
            log.info(
                "Fetched ${jupiterTokens.size} verified tokens from Jupiter, returning ${tokens.size} after allowlist filter",
            )
            cachedTokens = tokens
            cacheExpiry = Instant.now().plus(Duration.ofHours(1))
            tokens
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JupiterToken(val id: String, val symbol: String, val name: String, val decimals: Int, val icon: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AllowlistEntry(val symbol: String, val name: String, val enabled: Boolean, val logo: String? = null)

    companion object {
        fun create(assetsBaseUrl: String, meterRegistry: MeterRegistry = SimpleMeterRegistry()): JupiterService = JupiterService(assetsBaseUrl, meterRegistry)
    }
}
