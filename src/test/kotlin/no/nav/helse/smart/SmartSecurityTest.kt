package no.nav.helse.smart

import org.junit.Ignore
import org.junit.Test

@Ignore("ikke imeplementert")
class SmartSecurityTest {

  @Test
  fun `should authenticate request with valid token and patient scope`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token missing scope claim`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token without patient, user or system scope`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token signed with wrong issuer`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token without subject claim`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should extract patient and encounter claims into principal`() {
    TODO("not yet implemented")
  }
}
