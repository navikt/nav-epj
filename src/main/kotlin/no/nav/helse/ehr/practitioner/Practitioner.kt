package no.nav.helse.ehr.practitioner

@JvmInline value class PractitionerHpr(val value: String)

data class Practitioner(val hpr: PractitionerHpr, val name: String, val qualification: String)

data class CreatePractitioner(val hpr: PractitionerHpr, val name: String, val qualification: String)
