@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package no.nav.helse.epj.sessionorg

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.LegekontorTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.legekontor.LegekontorRepository
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.Before
import org.junit.Test

private class InMemoryChoiceStore : SessionOrgChoiceStore {
    val choices = mutableMapOf<String, String>()

    override suspend fun get(hpr: String) = choices[hpr]

    override suspend fun set(hpr: String, orgnummer: String) {
        choices[hpr] = orgnummer
    }

    val extended = mutableListOf<String>()

    override suspend fun extend(hpr: String) {
        extended += hpr
    }
}

class SessionOrgResolverTest : WithPostgresql() {
    init {
        connect()
    }

    private val store = InMemoryChoiceStore()
    private val resolver =
        SessionOrgResolver(FastlegelisteRepository(), LegekontorRepository(), store)

    private val fjordvik = Uuid.generateV4()
    private val lyngdal = Uuid.generateV4()

    @Before
    fun seed() {
        runMigrations(true)
        runTest {
            insertOrg(fjordvik, "Fjordvik Legekontor", "311111116")
            insertOrg(lyngdal, "Lyngdal Legesenter", "311111124")
        }
    }

    private suspend fun insertOrg(id: Uuid, navn: String, orgnummer: String) = dbQuery {
        LegekontorTable.insert {
            it[LegekontorTable.id] = id
            it[LegekontorTable.navn] = navn
            it[tlf] = "22000000"
            it[LegekontorTable.orgnummer] = orgnummer
        }
    }

    private suspend fun insertList(hpr: String, orgId: Uuid) = dbQuery {
        FastlegelisteTable.insert {
            it[id] = Uuid.generateV4()
            it[FastlegelisteTable.hpr] = hpr
            it[legekontorId] = orgId
        }
    }

    private fun SessionOrgResolution.org() = (this as SessionOrgResolution.Resolved).org

    @Test
    fun `an hpr listed at one org resolves to it automatically`() = runTest {
        insertList("111", fjordvik)

        val resolution = resolver.resolve("111")

        assertEquals(fjordvik, resolution.org().id.value)
        assertTrue((resolution as SessionOrgResolution.Resolved).derived)
    }

    @Test
    fun `a stored choice cannot override a single listed org`() = runTest {
        insertList("111", fjordvik)
        store.set("111", "311111124")

        assertEquals(fjordvik, resolver.resolve("111").org().id.value)
    }

    @Test
    fun `an unknown hpr needs a choice until one is stored`() = runTest {
        assertEquals(SessionOrgResolution.NeedsChoice, resolver.resolve("999"))

        store.set("999", "311111124")
        val resolution = resolver.resolve("999")

        assertEquals(lyngdal, resolution.org().id.value)
        assertFalse((resolution as SessionOrgResolution.Resolved).derived)
    }

    @Test
    fun `resolving a stored choice extends it and an unresolved hpr does not`() = runTest {
        store.set("999", "311111124")

        resolver.resolve("999")
        resolver.resolve("998")

        assertEquals(listOf("999"), store.extended)
    }

    @Test
    fun `an hpr listed at several orgs needs a choice`() = runTest {
        insertList("222", fjordvik)
        insertList("222", lyngdal)

        assertEquals(SessionOrgResolution.NeedsChoice, resolver.resolve("222"))

        store.set("222", "311111116")
        assertEquals(fjordvik, resolver.resolve("222").org().id.value)
    }

    @Test
    fun `a stored choice for an org that is not choosable is ignored`() = runTest {
        store.set("999", "864425208")
        store.set("998", "000000000")

        assertEquals(SessionOrgResolution.NeedsChoice, resolver.resolve("999"))
        assertEquals(SessionOrgResolution.NeedsChoice, resolver.resolve("998"))
    }

    @Test
    fun `choosable orgs exclude the legacy default org and flag own lists`() = runTest {
        insertList("222", fjordvik)
        insertList("222", lyngdal)
        insertList("333", lyngdal)

        val unknown = resolver.choosableOrgs("999")
        val multi = resolver.choosableOrgs("222")
        val single = resolver.choosableOrgs("333")

        assertEquals(listOf("311111116", "311111124"), unknown.map { it.org.orgnummer })
        assertTrue(unknown.none { it.hasOwnList })
        assertTrue(multi.all { it.hasOwnList })
        assertEquals(listOf(false, true), single.map { it.hasOwnList })
    }
}
