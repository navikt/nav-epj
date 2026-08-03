package no.nav.helse.fhir.encounter

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@JvmInline @Serializable @OptIn(ExperimentalUuidApi::class) value class EncounterId(val value: Uuid)
