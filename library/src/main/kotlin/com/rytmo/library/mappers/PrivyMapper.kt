package com.rytmo.library.mappers

import com.rytmo.models.wallets.CryptoWallet
import io.privy.api.models.operations.WalletRetrieveResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper
abstract class PrivyMapper {

    companion object {
        val INSTANCE: PrivyMapper
            get() = Mappers.getMapper(PrivyMapper::class.java)
    }

    @Mapping(target = "address", expression = "java(extractAddress(wallet))")
    abstract fun map(wallet: WalletRetrieveResponse): CryptoWallet

    protected fun extractAddress(response: WalletRetrieveResponse): String? = response.wallet().map { w -> w.address() }.orElse(null)
}
