package no.nav.helse.fhir.documentreference

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline @OptIn(ExperimentalUuidApi::class) value class DocumentReferenceId(val value: Uuid)
