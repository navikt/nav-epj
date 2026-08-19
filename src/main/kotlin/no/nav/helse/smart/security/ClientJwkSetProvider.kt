package no.nav.helse.smart.security

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

fun interface ClientJwksSetProvider {
  fun sourceFor(jwksUri: String): JWKSource<SecurityContext>
}

class RemoteClientJwksSetProvider : ClientJwksSetProvider {
  private val sources = ConcurrentHashMap<String, JWKSource<SecurityContext>>()

  override fun sourceFor(jwksUri: String): JWKSource<SecurityContext> =
    sources.computeIfAbsent(jwksUri) { uri ->
      JWKSourceBuilder.create<SecurityContext>(URI(uri).toURL())
        .cache(true)
        .retrying(true)
        .rateLimited(true)
        .build()
    }
}
