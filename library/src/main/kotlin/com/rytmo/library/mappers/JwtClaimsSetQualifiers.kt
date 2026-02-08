package com.rytmo.library.mappers

import com.nimbusds.jwt.JWTClaimsSet
import org.mapstruct.Named
import java.util.Date

object JwtClaimsSetQualifiers {
    @JvmStatic
    @Named("sid")
    fun sid(claims: JWTClaimsSet): String = (claims.getClaim("sid") as? String).orEmpty()

    @JvmStatic
    @Named("issuedAtEpochSeconds")
    fun issuedAtEpochSeconds(claims: JWTClaimsSet): Number = claims.issueTime.toEpochSecondsOrZero()

    @JvmStatic
    @Named("expirationEpochSeconds")
    fun expirationEpochSeconds(claims: JWTClaimsSet): Number = claims.expirationTime.toEpochSecondsOrZero()

    private fun Date?.toEpochSecondsOrZero(): Long = this?.time?.div(1000L) ?: 0L
}
