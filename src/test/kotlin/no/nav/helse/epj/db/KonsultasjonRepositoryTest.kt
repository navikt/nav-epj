package no.nav.helse.epj.db

import no.nav.helse.utils.WithPostgresql
import org.junit.Test

class KonsultasjonRepositoryTest : WithPostgresql() {

  companion object {
    init {
      runMigrations(true)
      connect()
    }
  }

  @Test
  fun `should create konsultasjon`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return null when konsultasjon does not exist`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return konsultasjon by id`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return null when getKonsultasjon is called with an invalid id`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return null active konsultasjon when none exists for pasient`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return active konsultasjon for pasient`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should not return an avsluttet konsultasjon as active`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return most recently started konsultasjon when multiple are active`() {
    TODO("not yet implemented")
  }
}
