package no.nav.helse.plugins

import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlin.uuid.Uuid
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinModule

val uuidModule: SimpleModule =
  SimpleModule().apply {
    addSerializer(
      Uuid::class.java,
      object : ValueSerializer<Uuid>() {
        override fun serialize(value: Uuid, gen: JsonGenerator, ctxt: SerializationContext) {
          gen.writeString(value.toString())
        }
      },
    )
    addDeserializer(
      Uuid::class.java,
      object : ValueDeserializer<Uuid>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Uuid =
          Uuid.parse(p.string)
      },
    )
  }

fun Application.configureSerialization() {
  install(ContentNegotiation) {
    jackson {
      enable(SerializationFeature.INDENT_OUTPUT)
      addModule(KotlinModule.Builder().build())
      addModule(uuidModule)
    }
  }
}
