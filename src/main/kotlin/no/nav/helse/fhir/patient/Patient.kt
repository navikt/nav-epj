package no.nav.helse.fhir.patient

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@JvmInline @Serializable value class PatientInputId(val value: Uuid? = null) {}
