package no.nav.helse.fhir.DocumentReference

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline @OptIn(ExperimentalUuidApi::class) value class DocumentReferenceId(val value: Uuid)
