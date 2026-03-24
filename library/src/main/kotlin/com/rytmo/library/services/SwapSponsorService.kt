package com.rytmo.library.services

import org.slf4j.LoggerFactory
import org.sol4k.PublicKey

class SwapSponsorService(
    private val privyServerWalletService: PrivyServerWalletService,
    private val solanaService: SolanaService,
    private val feePayerWalletId: String,
    private val feePayerWalletAddress: String,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Injects the fee payer into the DFlow transaction and has the fee payer server wallet sign it.
     * Returns the partially-signed transaction (base64) for the client to sign with the user's
     * embedded wallet.
     */
    fun prepare(dflowTxBase64: String): String {
        log.info("Swap prepare start — feePayerWalletId={}", feePayerWalletId)

        val txWithFeePayer =
            solanaService.injectFeePayer(dflowTxBase64, PublicKey(feePayerWalletAddress).bytes())
        log.info("Fee payer injected")

        val feePayerSignedTx = privyServerWalletService.signOnly(feePayerWalletId, txWithFeePayer)
        log.info("Fee payer signOnly completed — feePayerWalletId={}", feePayerWalletId)

        return feePayerSignedTx
    }
}
