package com.rytmo.library.services

import org.sol4k.Base58

class SwapSponsorService(private val privyServerWalletService: PrivyServerWalletService, private val solanaService: SolanaService, private val feePayerKeypairBytes: ByteArray) {
    fun execute(walletId: String, dflowTxBase64: String): String {
        val feePayerPubkey = solanaService.publicKeyFromKeypair(feePayerKeypairBytes)
        val modifiedTx = solanaService.injectFeePayer(dflowTxBase64, feePayerPubkey)
        val privySignedTx = privyServerWalletService.signOnly(walletId, modifiedTx)
        val fullySigned = solanaService.addFeePayerSignature(privySignedTx, feePayerKeypairBytes)
        return solanaService.submit(fullySigned)
    }

    companion object {
        fun create(privyServerWalletService: PrivyServerWalletService, solanaService: SolanaService, feePayerPrivateKey: String): SwapSponsorService = SwapSponsorService(
            privyServerWalletService,
            solanaService,
            if (feePayerPrivateKey.isBlank()) ByteArray(0) else Base58.decode(feePayerPrivateKey),
        )
    }
}
