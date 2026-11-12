package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Resource
import kotlinx.serialization.SerializationException

private val clientLanguage = Regex("\"language\"\\s*:\\s*\"NO-nb\"")
private val libraryLanguage = Regex("\"language\"\\s*:\\s*\"no-NO\"")

class FhirCodec(private val fhirJson: FhirR4Json = FhirR4Json()) {

    fun decode(body: String): Resource =
        try {
            fhirJson.decodeFromString(clientLanguage.replace(body, "\"language\": \"no-NO\""))
        } catch (exception: SerializationException) {
            unreadable(exception.message)
        } catch (exception: IllegalArgumentException) {
            unreadable(exception.message)
        } catch (exception: IllegalStateException) {
            unreadable(exception.message)
        } catch (_: StackOverflowError) {
            unreadable("ressursen er for dypt nøstet")
        }

    fun encode(resource: Resource): String =
        libraryLanguage.replace(fhirJson.encodeToString(resource), "\"language\": \"NO-nb\"")

    private fun unreadable(reason: String?): Nothing =
        invalidResource(
            OperationOutcome.IssueType.Structure,
            "Klarte ikke å tolke forespørselen som en FHIR-ressurs: $reason",
        )
}
