package no.nav.helse.smart.security

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SmartScopeTest {

  @Test
  fun `v2 suffix in cruds order is accepted`() {
    val scope = parseScope("patient/Patient.rs")
    assertEquals(
      SmartScope.Fhir(ScopeContext.PATIENT, "Patient", setOf(Interaction.READ, Interaction.SEARCH)),
      scope,
    )
  }

  @Test
  fun `v2 suffix out of order is rejected`() {
    assertNull(parseScope("patient/Patient.sr")) // must be "rs" (c_r_ud_s_)
  }

  @Test
  fun `v2 suffix with duplicated code is rejected`() {
    assertNull(parseScope("patient/Patient.rr"))
  }

  @Test
  fun `v2 suffix with unknown code is rejected`() {
    assertNull(parseScope("patient/Patient.x"))
  }

  @Test
  fun `empty suffix is rejected`() {
    assertNull(parseScope("patient/Patient."))
  }

  @Test
  fun `v1 read maps to read and search`() {
    val scope = parseScope("patient/Patient.read")
    assertEquals(
      SmartScope.Fhir(ScopeContext.PATIENT, "Patient", setOf(Interaction.READ, Interaction.SEARCH)),
      scope,
    )
  }

  @Test
  fun `v1 write maps to create update delete`() {
    val scope = parseScope("patient/Patient.write")
    assertEquals(
      SmartScope.Fhir(
        ScopeContext.PATIENT,
        "Patient",
        setOf(Interaction.CREATE, Interaction.UPDATE, Interaction.DELETE),
      ),
      scope,
    )
  }

  @Test
  fun `v1 wildcard star maps to all interactions`() {
    val scope = parseScope("patient/Patient.*")
    assertEquals(
      SmartScope.Fhir(ScopeContext.PATIENT, "Patient", Interaction.entries.toSet()),
      scope,
    )
  }

  @Test
  fun `openid launch and fhirUser are opaque scopes`() {
    assertEquals(SmartScope.Other("openid"), parseScope("openid"))
    assertEquals(SmartScope.Other("launch"), parseScope("launch"))
    assertEquals(SmartScope.Other("launch/patient"), parseScope("launch/patient"))
  }

  @Test
  fun `bare launch and launch slash patient are distinct scopes`() {
    // A substring check ("launch" in scopeString) would wrongly treat these as the same grant.
    assertTrue(SmartScope.Other("launch") != SmartScope.Other("launch/patient"))
  }

  @Test
  fun `unparseable scopes are dropped, not rejected, at request time`() {
    val scopes = parseScopes("openid patient/Patient.rs patient/Patient.sr")
    assertEquals(setOf(SmartScope.Other("openid"), parseScope("patient/Patient.rs")), scopes)
  }

  @Test
  fun `search parameter scopes fail closed`() {
    assertNull(parseScope("patient/Observation.rs?category=laboratory"))
  }

  @Test
  fun `registering an unparseable scope throws`() {
    assertFailsWith<IllegalArgumentException> {
      parseRegisteredScopes(listOf("patient/Patient.sr"))
    }
  }

  @Test
  fun `registering an unknown opaque scope throws`() {
    assertFailsWith<IllegalArgumentException> { parseRegisteredScopes(listOf("patinet")) }
  }

  @Test
  fun `registering known scopes succeeds`() {
    val scopes = parseRegisteredScopes(listOf("openid", "fhirUser", "launch", "patient/Patient.rs"))
    assertEquals(4, scopes.size)
  }

  @Test
  fun `exact match is granted`() {
    val allowed = setOf(parseScope("patient/Patient.rs")!!)
    val requested = setOf(parseScope("patient/Patient.rs")!!)
    assertEquals(requested, grantScopes(requested, allowed))
  }

  @Test
  fun `request narrower than allowed is granted as requested`() {
    val allowed = setOf(parseScope("patient/Patient.cruds")!!)
    val requested = setOf(parseScope("patient/Patient.r")!!)
    assertEquals(requested, grantScopes(requested, allowed))
  }

  @Test
  fun `request wider than allowed is narrowed to allowed interactions`() {
    val allowed = setOf(parseScope("patient/Patient.r")!!)
    val requested = setOf(parseScope("patient/Patient.cruds")!!)
    assertEquals(setOf(parseScope("patient/Patient.r")!!), grantScopes(requested, allowed))
  }

  @Test
  fun `wildcard resource request is narrowed to what is registered`() {
    val allowed = setOf(parseScope("patient/Observation.rs")!!)
    val requested = setOf(parseScope("patient/*.rs")!!)
    assertEquals(setOf(parseScope("patient/Observation.rs")!!), grantScopes(requested, allowed))
  }

  @Test
  fun `wildcard registration covers a specific request`() {
    val allowed = setOf(parseScope("patient/*.cruds")!!)
    val requested = setOf(parseScope("patient/Observation.rs")!!)
    assertEquals(setOf(parseScope("patient/Observation.rs")!!), grantScopes(requested, allowed))
  }

  @Test
  fun `mismatched context is not granted`() {
    val allowed = setOf(parseScope("user/Patient.rs")!!)
    val requested = setOf(parseScope("patient/Patient.rs")!!)
    assertEquals(emptySet(), grantScopes(requested, allowed))
  }

  @Test
  fun `mismatched resource type is not granted`() {
    val allowed = setOf(parseScope("patient/Encounter.rs")!!)
    val requested = setOf(parseScope("patient/Patient.rs")!!)
    assertEquals(emptySet(), grantScopes(requested, allowed))
  }

  @Test
  fun `disjoint interactions grant nothing`() {
    val allowed = setOf(parseScope("patient/Patient.rs")!!)
    val requested = setOf(parseScope("patient/Patient.cud")!!)
    assertEquals(emptySet(), grantScopes(requested, allowed))
  }

  @Test
  fun `opaque scopes are granted only if registered`() {
    val allowed = setOf(SmartScope.Other("openid"), SmartScope.Other("launch"))
    val requested = setOf(SmartScope.Other("openid"), SmartScope.Other("offline_access"))
    assertEquals(setOf(SmartScope.Other("openid")), grantScopes(requested, allowed))
  }

  @Test
  fun `serialize re-emits fhir scopes in canonical cruds order regardless of parse order`() {
    val scope = parseScope("patient/Patient.sr")
    val fhir =
      SmartScope.Fhir(ScopeContext.PATIENT, "Patient", setOf(Interaction.SEARCH, Interaction.READ))
    assertEquals("patient/Patient.rs", fhir.toString())
    assertNull(scope)
  }

  @Test
  fun `serialize joins multiple scopes with a space`() {
    val scopes = setOf(SmartScope.Other("openid"), parseScope("patient/Patient.rs")!!)
    val serialized = scopes.serialize()
    assertTrue(serialized.contains("openid"))
    assertTrue(serialized.contains("patient/Patient.rs"))
    assertTrue(serialized.contains(" "))
  }
}
