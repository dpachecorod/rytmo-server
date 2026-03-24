package com.rytmo.library.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.rytmo.library.exceptions.PrivyWalletException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.privy.api.PrivyApiClient
import io.privy.api.models.components.LinkedAccountSolanaEmbeddedWallet
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

data class SolanaWalletInfo(val walletId: String, val address: String)

class PrivyServerWalletService(
    private val privyApiClient: PrivyApiClient,
    private val appId: String,
    private val appSecret: String,
    private val solanaCaip2: String,
    private val authorizationKey: String,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jackson = ObjectMapper()
    private val httpClient = HttpClient.newHttpClient()
    private val basicAuth = Base64.getEncoder().encodeToString("$appId:$appSecret".toByteArray())

    private val privateKey: PrivateKey by lazy {
        val keyData = authorizationKey.removePrefix("wallet-auth:").trim()
        val keyBytes = Base64.getDecoder().decode(keyData)
        val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        log.info("Authorization key loaded — fingerprint={}", keyData.take(8))
        priv
    }

    private fun <T> timed(operation: String, block: () -> T): T = Timer.builder("privy.server_wallet.request")
        .tag("operation", operation)
        .register(meterRegistry)
        .recordCallable(block)!!

    fun getSolanaWallet(privyUserId: String): SolanaWalletInfo = timed("getSolanaWallet") {
        val response = privyApiClient.users().retrieve(privyUserId)
        if (response.statusCode() !in 200..299) {
            throw PrivyWalletException(
                "Privy get user failed [${response.statusCode()}]",
                response.statusCode(),
            )
        }
        val user =
            response.user().orElseThrow {
                PrivyWalletException("No user in Privy response for $privyUserId", 502)
            }
        val wallet =
            user.getFirstLinkedAccountByType(LinkedAccountSolanaEmbeddedWallet::class.java)
                ?: throw PrivyWalletException(
                    "No Solana embedded wallet found for user $privyUserId",
                    404,
                )
        val walletId =
            wallet.id().takeIf { it.isPresent }?.get()
                ?: throw PrivyWalletException("Solana wallet has no id for user $privyUserId", 502)
        SolanaWalletInfo(walletId, wallet.address())
    }

    fun signAndSend(walletId: String, transactionBase64: String): String = timed("signAndSend") {
        val bodyMap =
            mapOf(
                "method" to "signAndSendTransaction",
                "caip2" to solanaCaip2,
                "params" to mapOf("transaction" to transactionBase64, "encoding" to "base64"),
            )
        val bodyJson = jackson.writeValueAsString(bodyMap)
        val authSig = computeAuthorizationSignature(walletId, bodyMap)
        val url = "https://api.privy.io/v1/wallets/$walletId/rpc"
        val response = post(url, bodyJson, authSig)
        val hash = jackson.readTree(response)["data"]["hash"].asText()
        hash
    }

    fun signOnly(walletId: String, transactionBase64: String): String = timed("signOnly") {
        val bodyMap =
            mapOf(
                "method" to "signTransaction",
                "params" to mapOf("transaction" to transactionBase64, "encoding" to "base64"),
            )
        val bodyJson = jackson.writeValueAsString(bodyMap)
        val authSig = computeAuthorizationSignature(walletId, bodyMap)
        val url = "https://api.privy.io/v1/wallets/$walletId/rpc"
        val response = post(url, bodyJson, authSig)
        val signedTx = jackson.readTree(response)["data"]["signed_transaction"].asText()
        signedTx
    }

    private fun post(url: String, bodyJson: String, authSig: String): String {
        val request =
            HttpRequest.newBuilder()
                .uri(URI(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic $basicAuth")
                .header("privy-app-id", appId)
                .header("privy-authorization-signature", authSig)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()
        log.info("Privy direct POST — url={} body={}", url, bodyJson)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        log.info("Privy direct response — status={} body={}", response.statusCode(), response.body())
        if (response.statusCode() !in 200..299) {
            throw PrivyWalletException(
                "Privy RPC failed [${response.statusCode()}]: ${response.body()}",
                response.statusCode(),
            )
        }
        return response.body()
    }

    /**
     * RFC 8785 JSON Canonicalization: recursively sort object keys and produce compact JSON. Matches
     * the behavior of the `canonicalize` npm package and Python's json.dumps(sort_keys=True).
     */
    private fun canonicalize(value: Any?): String = when (value) {
        is Map<*, *> -> {
            val fields =
                value.entries
                    .sortedBy { it.key.toString() }
                    .joinToString(",") { (k, v) ->
                        "${jackson.writeValueAsString(k.toString())}:${canonicalize(v)}"
                    }
            "{$fields}"
        }
        is List<*> -> "[${value.joinToString(",") { canonicalize(it) }}]"
        is String -> jackson.writeValueAsString(value)
        is Int -> value.toString()
        is Long -> value.toString()
        is Boolean -> value.toString()
        null -> "null"
        else -> jackson.writeValueAsString(value)
    }

    private fun signPayload(canonical: String): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun computeAuthorizationSignature(walletId: String, bodyMap: Map<String, Any>): String {
        val payload =
            mapOf(
                "version" to 1,
                "method" to "POST",
                "url" to "https://api.privy.io/v1/wallets/$walletId/rpc",
                "body" to bodyMap,
                "headers" to mapOf("privy-app-id" to appId),
            )
        val canonical = canonicalize(payload)
        log.info("Authorization signature payload: {}", canonical)
        return signPayload(canonical)
    }

    companion object {
        fun create(
            privyApiClient: PrivyApiClient,
            appId: String,
            appSecret: String,
            solanaCaip2: String,
            authorizationKey: String,
            meterRegistry: MeterRegistry = SimpleMeterRegistry(),
        ): PrivyServerWalletService = PrivyServerWalletService(
            privyApiClient,
            appId,
            appSecret,
            solanaCaip2,
            authorizationKey,
            meterRegistry,
        )
    }
}
