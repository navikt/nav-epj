package no.nav.helse.utils

import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.smart.valkey.createGlideClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

abstract class WithValkey protected constructor() {
  companion object {
    val valkey =
      GenericContainer(DockerImageName.parse("valkey/valkey:8-alpine"))
        .apply {
          withExposedPorts(6379)
          waitingFor(Wait.forListeningPort())
          start()
        }

    private val glideClientConfiguration: GlideClientConfiguration =
      GlideClientConfiguration.builder()
        .address(NodeAddress.builder().host(valkey.host).port(valkey.getMappedPort(6379)).build())
        .requestTimeout(1000)
        .clientName("nav-epj-test")
        .build()

    val glideClient = createGlideClient(glideClientConfiguration)
    val valkeyService = ValkeyService(glideClient)
  }
}
