package no.nav.helse.fhir.Patient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@OptIn(ExperimentalUuidApi::class)
value class PatientInputId(val value: Uuid)
