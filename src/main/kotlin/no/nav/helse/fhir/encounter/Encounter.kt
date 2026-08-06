package no.nav.helse.fhir.encounter

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@JvmInline @Serializable value class EncounterId(val value: Uuid? = null) {}
