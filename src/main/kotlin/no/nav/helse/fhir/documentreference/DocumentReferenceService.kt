package no.nav.helse.fhir.documentreference

import io.ktor.client.*

class DocumentReferenceService(private val epjClient: HttpClient) {

  /*

    suspend fun getDocumentReference(encounterId: EncounterId): Encounter {
      val konsultasjon = epjClient.get("/api/konsultasjon/${encounterId.value}").body<Konsultasjon>()

      return konsultasjon.toEncounter()
    }

  */

}
