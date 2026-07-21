package no.nav.helse.smart

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals
import no.nav.helse.utils.configureTestSmartDependencies
import org.junit.Test

@OptIn(ExperimentalKtorApi::class)
class SmartRoutingTest() {

  @Test
  fun `GET fhir launch uten url gir 400`() = testApplication {
    application { configureTestSmartDependencies() }
    val response = client.get("/fhir/launch")
    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

  // TODO: fullføre tester under
  @Test
  fun `GET fhir launch med ukjent app-url `() = testApplication {
    application { configureTestSmartDependencies() }
  }

  @Test
  fun `GET fhir launch med ukjent pasient gir 404`() = testApplication {
    application { configureTestSmartDependencies() }
  }

  @Test
  fun `GET fhir launch med gyldig context redirecter til app med iss og launch`() =
    testApplication {
      application { configureTestSmartDependencies() }
    }

  /*
      andre ting å teste:
      GET /oidc/authorize mangler redirect_uri gir 400
      GET /oidc/authorize ukjent client_id gir 400
      GET /oidc/authorize redirect_uri ikke tillatt gir 400
      GET /oidc/authorize mangler scope redirecter med error=invalid_request
      GET /oidc/authorize response_type != code redirecter med unsupported_response_type
      GET /oidc/authorize aud feil redirecter med invalid_request
      GET /oidc/authorize code_challenge_method != S256 redirecter med invalid_request
      GET /oidc/authorize gyldig request redirecter med code og state

      POST /oidc/token mangler code gir 400 invalid_request
      POST /oidc/token grant_type != authorization_code gir 400 unsupported_grant_type
      POST /oidc/token ukjent code gir 400 invalid_grant
      POST /oidc/token feil code_verifier gir 400 invalid_grant
      POST /oidc/token feil redirect_uri gir 400 invalid_grant
      POST /oidc/token gyldig request returnerer tokenResponse
      POST /oidc/token samme code kan ikke brukes to ganger
  */

}
