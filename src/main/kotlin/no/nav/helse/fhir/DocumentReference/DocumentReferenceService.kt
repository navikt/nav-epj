package no.nav.helse.fhir.DocumentReference

import com.google.fhir.model.r4.Encounter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.fhir.Encounter.EncounterId

class DocumentReferenceService(private val epjClient: HttpClient) {

/*

  suspend fun getDocumentReference(encounterId: EncounterId): Encounter {
    val konsultasjon = epjClient.get("/api/konsultasjon/${encounterId.value}").body<Konsultasjon>()

    return konsultasjon.toEncounter()
  }

*/


}
