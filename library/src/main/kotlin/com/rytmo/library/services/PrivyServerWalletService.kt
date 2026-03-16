package com.rytmo.library.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.rytmo.library.exceptions.PrivyWalletException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.privy.api.PrivyApiClient
import io.privy.api.models.components.LinkedAccountSolanaEmbeddedWallet
import io.privy.api.models.components.SolanaSignAndSendTransactionRpcInput
import io.privy.api.models.components.SolanaSignAndSendTransactionRpcInputEncoding
import io.privy.api.models.components.SolanaSignAndSendTransactionRpcInputMethod
import io.privy.api.models.components.SolanaSignAndSendTransactionRpcInputParams
import io.privy.api.models.components.SolanaSignAndSendTransactionRpcResponse
import io.privy.api.models.components.SolanaSignTransactionRpcInput
import io.privy.api.models.components.SolanaSignTransactionRpcInputEncoding
import io.privy.api.models.components.SolanaSignTransactionRpcInputMethod
import io.privy.api.models.components.SolanaSignTransactionRpcInputParams
import io.privy.api.models.components.SolanaSignTransactionRpcResponse
import io.privy.api.models.operations.WalletRpcRequest
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

data class SolanaWalletInfo(val walletId: String, val address: String)

class PrivyServerWalletService(
    private val privyApiClient: PrivyApiClient,
    private val appId: String,
    private val solanaCaip2: String,
    private val authorizationKey: String,
    private val meterRegistry: MeterRegistry,
) {
    private val jackson = ObjectMapper()

    private val privateKey: PrivateKey by lazy {
        val keyData = authorizationKey.removePrefix("wallet-auth:")
        val keyBytes = Base64.getDecoder().decode(keyData)
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
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
        val authSig = computeAuthorizationSignature(walletId, transactionBase64)
        val requestBody =
            SolanaSignAndSendTransactionRpcInput.builder()
                .method(SolanaSignAndSendTransactionRpcInputMethod.SIGN_AND_SEND_TRANSACTION)
                .caip2(solanaCaip2)
                .sponsor(true)
                .params(
                    SolanaSignAndSendTransactionRpcInputParams.builder()
                        .transaction(transactionBase64)
                        .encoding(SolanaSignAndSendTransactionRpcInputEncoding.BASE64)
                        .build(),
                )
                .build()
        val input =
            WalletRpcRequest.builder()
                .walletId(walletId)
                .requestBody(requestBody)
                .privyAuthorizationSignature(authSig)
                .build()
        val response = privyApiClient.wallets().rpc(input)
        if (response.statusCode() !in 200..299) {
            throw PrivyWalletException(
                "Privy signAndSend failed [${response.statusCode()}]",
                response.statusCode(),
            )
        }
        val responseBody =
            response.oneOf().orElseThrow {
                PrivyWalletException("Empty Privy signAndSend response", 502)
            } as SolanaSignAndSendTransactionRpcResponse
        responseBody.data().hash()
    }

    fun signOnly(walletId: String, transactionBase64: String): String = timed("signOnly") {
        val authSig = computeSignOnlyAuthorizationSignature(walletId, transactionBase64)
        val requestBody =
            SolanaSignTransactionRpcInput.builder()
                .method(SolanaSignTransactionRpcInputMethod.SIGN_TRANSACTION)
                .params(
                    SolanaSignTransactionRpcInputParams.builder()
                        .transaction(transactionBase64)
                        .encoding(SolanaSignTransactionRpcInputEncoding.BASE64)
                        .build(),
                )
                .build()
        val input =
            WalletRpcRequest.builder()
                .walletId(walletId)
                .requestBody(requestBody)
                .privyAuthorizationSignature(authSig)
                .build()
        val response = privyApiClient.wallets().rpc(input)
        if (response.statusCode() !in 200..299) {
            throw PrivyWalletException(
                "Privy signOnly failed [${response.statusCode()}]",
                response.statusCode(),
            )
        }
        val responseBody =
            response.oneOf().orElseThrow {
                PrivyWalletException("Empty Privy signOnly response", 502)
            } as SolanaSignTransactionRpcResponse
        responseBody.data().signedTransaction()
    }

    private fun computeSignOnlyAuthorizationSignature(walletId: String, transactionBase64: String): String {
        val payload =
            sortedMapOf<String, Any>(
                "body" to
                    sortedMapOf<String, Any>(
                        "method" to "signTransaction",
                        "params" to
                            sortedMapOf("encoding" to "base64", "transaction" to transactionBase64),
                    ),
                "headers" to sortedMapOf("privy-app-id" to appId),
                "method" to "POST",
                "url" to "https://api.privy.io/v1/wallets/$walletId/rpc",
                "version" to 1,
            )
        val canonicalized = jackson.writeValueAsBytes(payload)
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(canonicalized)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun computeAuthorizationSignature(walletId: String, transactionBase64: String): String {
        val payload =
            sortedMapOf<String, Any>(
                "body" to
                    sortedMapOf<String, Any>(
                        "caip2" to solanaCaip2,
                        "method" to "signAndSendTransaction",
                        "params" to
                            sortedMapOf("encoding" to "base64", "transaction" to transactionBase64),
                        "sponsor" to true,
                    ),
                "headers" to sortedMapOf("privy-app-id" to appId),
                "method" to "POST",
                "url" to "https://api.privy.io/v1/wallets/$walletId/rpc",
                "version" to 1,
            )
        val canonicalized = jackson.writeValueAsBytes(payload)
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(canonicalized)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    companion object {
        fun create(privyApiClient: PrivyApiClient, appId: String, solanaCaip2: String, authorizationKey: String, meterRegistry: MeterRegistry = SimpleMeterRegistry()): PrivyServerWalletService =
            PrivyServerWalletService(
                privyApiClient,
                appId,
                solanaCaip2,
                authorizationKey,
                meterRegistry,
            )
    }
}
