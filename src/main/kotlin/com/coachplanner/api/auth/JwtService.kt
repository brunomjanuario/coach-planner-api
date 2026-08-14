package com.coachplanner.api.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

const val ACCESS_TOKEN_TTL_MINUTES = 15L

/**
 * Issues signed access tokens (HS256, symmetric — the same app.jwt.secret
 * SecurityConfig's JwtDecoder verifies against). Verification itself is
 * deliberately not here: it's Spring Security's resource-server filter
 * chain's job (T15/T18), using the standard JwtDecoder bean — issuer and
 * verifier are different concerns even though one app plays both roles.
 */
@Service
class JwtService(@Value("\${app.jwt.secret}") secret: String) {

    private val signer = MACSigner(secret.toByteArray(Charsets.UTF_8))

    fun issueAccessToken(userId: UUID): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)))
            .build()
        val signedJwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        signedJwt.sign(signer)
        return signedJwt.serialize()
    }
}
