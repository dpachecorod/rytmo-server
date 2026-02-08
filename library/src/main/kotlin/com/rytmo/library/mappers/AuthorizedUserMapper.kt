package com.rytmo.library.mappers

import com.nimbusds.jwt.JWTClaimsSet
import com.rytmo.models.auth.AuthorizedUser
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(uses = [JwtClaimsSetQualifiers::class])
interface AuthorizedUserMapper {
    @Mapping(target = "issuer", expression = "java(claims.getIssuer())")
    @Mapping(target = "userId", expression = "java(claims.getSubject())")
    @Mapping(target = "sessionId", source = ".", qualifiedByName = ["sid"])
    @Mapping(target = "issuedAt", source = ".", qualifiedByName = ["issuedAtEpochSeconds"])
    @Mapping(target = "expiration", source = ".", qualifiedByName = ["expirationEpochSeconds"])
    fun fromClaims(claims: JWTClaimsSet): AuthorizedUser
}
