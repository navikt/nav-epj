package no.nav.helse.epj.maaling

import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.ZoneOffset
import no.nav.helse.epj.patientId

fun Route.maalingRoutes(maalingService: MaalingService) {
    route("/api/patient/{patientId}/maalinger") {
        get {
            call.respond(
                maalingService.getMaalingerForPasient(call.patientId()).map { it.toResponse() }
            )
        }
    }
}

private data class MaalingResponse(
    val id: String,
    val pasientId: String,
    val konsultasjonId: String,
    val hpr: String?,
    val loincKode: String,
    val loincVisningsnavn: String,
    val verdi: java.math.BigDecimal,
    val enhetKode: String,
    val enhetVisningsnavn: String,
    val effektivTidspunkt: String,
    val status: MaalingStatus,
)

private fun Maaling.toResponse() =
    MaalingResponse(
        id = id.value.toString(),
        pasientId = pasientId.value.toString(),
        konsultasjonId = konsultasjonId.value.toString(),
        hpr = hpr?.value,
        loincKode = loincKode,
        loincVisningsnavn = loincVisningsnavn,
        verdi = verdi,
        enhetKode = enhetKode,
        enhetVisningsnavn = enhetVisningsnavn,
        effektivTidspunkt = effektivTidspunkt.toInstant(ZoneOffset.UTC).toString(),
        status = status,
    )
