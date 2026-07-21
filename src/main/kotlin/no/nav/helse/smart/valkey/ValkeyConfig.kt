package no.nav.helse.smart.valkey

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import glide.api.models.configuration.ServerCredentials
import no.nav.helse.core.Environment

fun createGlideClientConfiguration(env: Environment): GlideClientConfiguration {
  val builder =
    GlideClientConfiguration.builder()
      .address(NodeAddress.builder().host(env.valkey.host).port(env.valkey.port).build())
      .useTLS(env.valkey.useTLS)
      .clientName("nav-epj")

  if (!env.valkey.password.isNullOrBlank()) {
    builder.credentials(
      ServerCredentials.builder()
        .username(env.valkey.username)
        .password(env.valkey.password)
        .build()
    )
  }

  return builder.build()
}

fun createGlideClient(configuration: GlideClientConfiguration): GlideClient =
  GlideClient.createClient(configuration).join()
