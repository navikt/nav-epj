package no.nav.helse.smart

import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

internal object SmartKeys {
  private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
  val keyId: String = UUID.randomUUID().toString()
  val rsaPublic: RSAPublicKey = keyPair.public as RSAPublicKey
  val algorithm: Algorithm = Algorithm.RSA256(rsaPublic, keyPair.private as RSAPrivateKey)
  val jwk: RSAKey =
    RSAKey.Builder(rsaPublic)
      .privateKey(keyPair.private as RSAPrivateKey)
      .keyUse(KeyUse.SIGNATURE)
      .keyID(keyId)
      .algorithm(JWSAlgorithm.RS256)
      .build()
}
