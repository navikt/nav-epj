package no.nav.helse.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helse.fhir.security.InsufficientScopeException
import no.nav.helse.fhir.security.PatientMismatchException
import no.nav.helse.smart.security.Interaction
import org.junit.Test

class StatusPagesAuthorizationTest {
    private fun Route.failingGet(path: String, failure: () -> Exception) {
        get(path) { throw failure() }
    }

    private fun insufficientScope() =
        InsufficientScopeException("DocumentReference", Interaction.CREATE)

    private fun ApplicationTestBuilder.setup() {
        application {
            configureStatusPages()
            routing {
                failingGet("/fhir", ::insufficientScope)
                for (prefix in listOf("/fhir", "/fhirish", "/api")) {
                    failingGet("$prefix/scope", ::insufficientScope)
                    failingGet("$prefix/mismatch") { PatientMismatchException("DocumentReference") }
                }
            }
        }
    }

    private suspend fun assertOutcome(
        response: HttpResponse,
        status: HttpStatusCode,
        code: String,
        diagnostics: String,
    ) {
        assertEquals(status, response.status)
        assertEquals(
            ContentType("application", "fhir+json"),
            response.contentType()?.withoutParameters(),
        )
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("OperationOutcome", body["resourceType"]?.jsonPrimitive?.content)
        val issue = body["issue"]!!.jsonArray.single().jsonObject
        assertEquals("error", issue["severity"]?.jsonPrimitive?.content)
        assertEquals(code, issue["code"]?.jsonPrimitive?.content)
        assertEquals(diagnostics, issue["diagnostics"]?.jsonPrimitive?.content)
    }

    private suspend fun assertPlainText(
        response: HttpResponse,
        status: HttpStatusCode,
        text: String,
    ) {
        assertEquals(status, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        assertEquals(text, response.bodyAsText())
    }

    private val insufficientText = "No granted scope covers DocumentReference.c"
    private val wwwAuthenticate = HttpHeaders.WWWAuthenticate

    @Test
    fun `insufficient scope under fhir is an OperationOutcome`() = testApplication {
        setup()
        val response = client.get("/fhir/scope")
        assertOutcome(response, HttpStatusCode.Forbidden, "forbidden", insufficientText)
        val header = response.headers[wwwAuthenticate]!!
        assertTrue(header.contains("insufficient_scope"))
        assertTrue(header.contains("scope=\"DocumentReference.c\""))
        assertOutcome(client.get("/fhir"), HttpStatusCode.Forbidden, "forbidden", insufficientText)
    }

    @Test
    fun `patient mismatch under fhir is a privacy safe not-found OperationOutcome`() =
        testApplication {
            setup()
            assertOutcome(
                client.get("/fhir/mismatch"),
                HttpStatusCode.NotFound,
                "not-found",
                "Not found",
            )
        }

    @Test
    fun `non-fhir paths keep plain text`() = testApplication {
        setup()
        for (prefix in listOf("/fhirish", "/api")) {
            val scope = client.get("$prefix/scope")
            assertPlainText(scope, HttpStatusCode.Forbidden, insufficientText)
            assertTrue(scope.headers[wwwAuthenticate]!!.contains("insufficient_scope"))
            assertPlainText(client.get("$prefix/mismatch"), HttpStatusCode.NotFound, "Not found")
        }
    }
}
