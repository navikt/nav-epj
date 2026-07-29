package no.nav.helse.fhir.Practitioner

import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@OptIn(ExperimentalUuidApi::class)
value class PracitionerId(val value: String)
