package no.nav.helse.smart.valkey

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import no.nav.helse.core.Environment

fun createGlideClientConfiguration(env: Environment): GlideClientConfiguration {
  return GlideClientConfiguration.builder()
    .address(NodeAddress.builder().host(env.valkey.host).port(env.valkey.port).build())
    .useTLS(true)
    .clientName("nav-epj")
    .build()
}

fun createGlideClient(configuration: GlideClientConfiguration): GlideClient =
  GlideClient.createClient(configuration).join()
