package com.rytmo.library.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rytmo.models.swap.SwapStatusResponse
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.sol4k.Keypair
import org.sol4k.PublicKey
import org.sol4k.TransactionMessage
import org.sol4k.instruction.CreateAssociatedTokenAccountInstruction
import org.sol4k.instruction.SplTransferInstruction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

class SolanaService(private val heliusRpcUrl: String, private val usdcMintAddress: String, private val meterRegistry: MeterRegistry) {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun <T> timed(operation: String, block: () -> T): T = Timer.builder("solana.request")
        .tag("operation", operation)
        .register(meterRegistry)
        .recordCallable(block)!!

    private fun getLatestBlockhash(): String = timed("getLatestBlockhash") {
        val body =
            """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(heliusRpcUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Helius RPC getLatestBlockhash failed [${response.statusCode()}]: ${response.body()}",
            )
        }

        val root = objectMapper.readTree(response.body())
        root.get("result")?.get("value")?.get("blockhash")?.asText()
            ?: throw IllegalStateException("No blockhash in RPC response: ${response.body()}")
    }

    fun buildUsdcTransferTransaction(fromAddress: String, toAddress: String, lamports: Long): String = timed("buildUsdcTransferTransaction") {
        val blockhash = getLatestBlockhash()

        val fromPubkey = PublicKey(fromAddress)
        val toPubkey = PublicKey(toAddress)
        val usdcMint = PublicKey(usdcMintAddress)

        val senderAta = PublicKey.findProgramDerivedAddress(fromPubkey, usdcMint).publicKey
        val recipientAta = PublicKey.findProgramDerivedAddress(toPubkey, usdcMint).publicKey

        val createAtaInstruction =
            CreateAssociatedTokenAccountInstruction(
                payer = fromPubkey,
                associatedToken = recipientAta,
                owner = toPubkey,
                mint = usdcMint,
            )

        val transferInstruction =
            SplTransferInstruction(
                from = senderAta,
                to = recipientAta,
                mint = usdcMint,
                owner = fromPubkey,
                amount = lamports,
                decimals = USDC_DECIMALS,
            )

        val instructions = listOf(createAtaInstruction, transferInstruction)

        // Validate: only AssociatedTokenProgram and TokenProgram instructions allowed
        val allowedPrograms =
            setOf(
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", // ASSOCIATED_TOKEN_PROGRAM_ID
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", // TOKEN_PROGRAM_ID
            )
        for (instruction in instructions) {
            val programId = instruction.programId.toBase58()
            check(programId in allowedPrograms) {
                "Unexpected program in USDC transfer transaction: $programId"
            }
        }

        val message = TransactionMessage.newMessage(fromPubkey, blockhash, instructions)

        // Serialize as an unsigned versioned transaction:
        // [compact-u16 sig count = 1][64 zero bytes for empty sig][message bytes]
        val messageBytes = message.serialize()
        val txBytes = byteArrayOf(0x01.toByte()) + ByteArray(64) + messageBytes

        Base64.getEncoder().encodeToString(txBytes)
    }

    fun getSignatureStatus(signature: String): SwapStatusResponse = timed("getSignatureStatus") {
        val body =
            """{"jsonrpc":"2.0","id":1,"method":"getSignatureStatuses","params":[["$signature"],{"searchTransactionHistory":true}]}"""
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(heliusRpcUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Helius RPC getSignatureStatuses failed [${response.statusCode()}]: ${response.body()}",
            )
        }
        val root = objectMapper.readTree(response.body())
        val statusNode = root.get("result")?.get("value")?.get(0)
        when {
            statusNode == null || statusNode.isNull ->
                SwapStatusResponse(status = "open", fills = emptyList(), error = null)
            statusNode.get("err") != null && !statusNode.get("err").isNull ->
                SwapStatusResponse(
                    status = "error",
                    fills = emptyList(),
                    error = statusNode.get("err").toString(),
                )
            else -> {
                val confirmationStatus = statusNode.get("confirmationStatus")?.asText()
                val closed = confirmationStatus == "confirmed" || confirmationStatus == "finalized"
                SwapStatusResponse(
                    status = if (closed) "closed" else "open",
                    fills = emptyList(),
                    error = null,
                )
            }
        }
    }

    fun publicKeyFromKeypair(keypairBytes: ByteArray): ByteArray = Keypair.fromSecretKey(keypairBytes).publicKey.bytes()

    fun injectFeePayer(txBase64: String, feePayerPubkey: ByteArray): String {
        val bytes = Base64.getDecoder().decode(txBase64)
        var pos = 0

        val (numSigs, numSigsLen) = readCompactU16(bytes, pos)
        pos += numSigsLen
        pos += numSigs * 64
        val msgStart = pos

        // skip version prefix (0x80 for v0)
        pos += 1

        // header: 3 bytes
        val numReqSigs = bytes[pos].toInt() and 0xFF
        val numReadonlySigned = bytes[pos + 1]
        val numReadonlyUnsigned = bytes[pos + 2]
        pos += 3

        val (numAccounts, numAccountsLen) = readCompactU16(bytes, pos)
        pos += numAccountsLen
        val accountKeysPos = pos
        pos += numAccounts * 32
        val afterAccountKeysPos = pos

        // skip blockhash
        pos += 32

        val (numInstructions, numInstructionsLen) = readCompactU16(bytes, pos)
        pos += numInstructionsLen

        data class Instruction(val programIdIndex: Int, val accountIndices: List<Int>, val data: ByteArray)

        val instructions = mutableListOf<Instruction>()
        for (i in 0 until numInstructions) {
            val progIdx = bytes[pos].toInt() and 0xFF
            pos += 1
            val (numAccIdx, numAccIdxLen) = readCompactU16(bytes, pos)
            pos += numAccIdxLen
            val accIndices = (0 until numAccIdx).map { bytes[pos + it].toInt() and 0xFF }
            pos += numAccIdx
            val (dataLen, dataLenLen) = readCompactU16(bytes, pos)
            pos += dataLenLen
            val data = bytes.copyOfRange(pos, pos + dataLen)
            pos += dataLen
            instructions.add(Instruction(progIdx, accIndices, data))
        }

        val remaining = bytes.copyOfRange(pos, bytes.size)

        val out = mutableListOf<Byte>()
        writeCompactU16(out, numSigs + 1)
        repeat(64) { out.add(0) }
        for (i in numSigsLen until numSigsLen + numSigs * 64) out.add(bytes[i])
        out.add(bytes[msgStart]) // version byte
        out.add((numReqSigs + 1).toByte())
        out.add(numReadonlySigned)
        out.add(numReadonlyUnsigned)
        writeCompactU16(out, numAccounts + 1)
        feePayerPubkey.forEach { out.add(it) }
        for (i in accountKeysPos until afterAccountKeysPos) out.add(bytes[i])
        for (i in afterAccountKeysPos until afterAccountKeysPos + 32) out.add(bytes[i])
        writeCompactU16(out, numInstructions)
        for (instr in instructions) {
            out.add((instr.programIdIndex + 1).toByte())
            writeCompactU16(out, instr.accountIndices.size)
            instr.accountIndices.forEach { out.add((it + 1).toByte()) }
            writeCompactU16(out, instr.data.size)
            instr.data.forEach { out.add(it) }
        }
        remaining.forEach { out.add(it) }

        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun addFeePayerSignature(txBase64: String, keypairBytes: ByteArray): String {
        val bytes = Base64.getDecoder().decode(txBase64)
        var pos = 0
        val (numSigs, numSigsLen) = readCompactU16(bytes, pos)
        pos += numSigsLen
        val msgStart = pos + numSigs * 64
        val messageBytes = bytes.copyOfRange(msgStart, bytes.size)
        val signature = Keypair.fromSecretKey(keypairBytes).sign(messageBytes)
        val result = bytes.copyOf()
        for (i in 0 until 64) result[numSigsLen + i] = signature[i]
        return Base64.getEncoder().encodeToString(result)
    }

    fun submit(txBase64: String): String {
        val body =
            """{"jsonrpc":"2.0","id":1,"method":"sendTransaction","params":["$txBase64",{"encoding":"base64","maxRetries":0,"skipPreflight":true}]}"""
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(heliusRpcUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Helius RPC sendTransaction failed [${response.statusCode()}]: ${response.body()}",
            )
        }
        val root = objectMapper.readTree(response.body())
        return root.get("result")?.asText()
            ?: throw IllegalStateException("No result in sendTransaction response: ${response.body()}")
    }

    private fun readCompactU16(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        val b0 = bytes[offset].toInt() and 0xFF
        if (b0 and 0x80 == 0) return Pair(b0, 1)
        val b1 = bytes[offset + 1].toInt() and 0xFF
        if (b1 and 0x80 == 0) return Pair((b0 and 0x7F) or (b1 shl 7), 2)
        val b2 = bytes[offset + 2].toInt() and 0xFF
        return Pair((b0 and 0x7F) or ((b1 and 0x7F) shl 7) or (b2 shl 14), 3)
    }

    private fun writeCompactU16(out: MutableList<Byte>, value: Int) {
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.add(b.toByte())
        } while (v != 0)
    }

    companion object {
        const val USDC_DECIMALS = 6

        fun Double.toUsdcLamports(): Long = (this * 1_000_000).toLong()

        fun create(heliusRpcUrl: String, usdcMintAddress: String, meterRegistry: MeterRegistry = SimpleMeterRegistry()): SolanaService = SolanaService(heliusRpcUrl, usdcMintAddress, meterRegistry)
    }
}
