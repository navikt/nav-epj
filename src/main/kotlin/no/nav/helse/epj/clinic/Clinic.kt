package no.nav.helse.epj.clinic

import kotlin.uuid.Uuid

@JvmInline value class ClinicId(val value: Uuid)

data class Clinic(val id: ClinicId, val name: String, val orgNumber: String?, val phone: String?)
