package no.nav.helse.fhir.patient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@OptIn(ExperimentalUuidApi::class)
value class PatientInputId(val value: Uuid)
