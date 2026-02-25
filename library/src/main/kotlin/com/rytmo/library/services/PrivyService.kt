package com.rytmo.library.services

import com.rytmo.library.mappers.PrivyMapper
import com.rytmo.models.wallets.CryptoWallet
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.privy.api.PrivyApiClient
import org.slf4j.LoggerFactory

class PrivyService(private val privyApiClient: PrivyApiClient, private val meterRegistry: MeterRegistry = SimpleMeterRegistry()) {
    companion object {
        val mapper = PrivyMapper.INSTANCE
        val log = LoggerFactory.getLogger(this::class.java.name)
    }

    fun getWallet(walletId: String): CryptoWallet {
        log.info("Fetching wallet with ID: $walletId")
        return Timer.builder("privy.request")
            .tag("operation", "getWallet")
            .register(meterRegistry)
            .recordCallable {
                val wallet = privyApiClient.wallets().retrieve(walletId)
                mapper.map(wallet)
            }!!
    }
}
