package com.rytmo.library.services

import com.rytmo.library.mappers.PrivyMapper
import com.rytmo.models.wallets.CryptoWallet
import io.privy.api.PrivyApiClient
import org.slf4j.LoggerFactory

class PrivyService(private val privyApiClient: PrivyApiClient) {
    companion object {
        val mapper = PrivyMapper.INSTANCE
        val log = LoggerFactory.getLogger(this::class.java.name)
    }

    fun getWallet(walletId: String): CryptoWallet {
        log.info("Fetching wallet with ID: $walletId")
        val wallet = privyApiClient.wallets().retrieve(walletId)
        return mapper.map(wallet)
    }
}
