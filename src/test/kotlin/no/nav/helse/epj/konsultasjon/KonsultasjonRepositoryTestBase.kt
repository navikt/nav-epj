package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.utils.WithPostgresql

abstract class KonsultasjonRepositoryTestBase : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    protected val konsultasjonRepository = KonsultasjonRepository()
    protected val pasientRepository = PasientRepository()

    @OptIn(ExperimentalUuidApi::class)
    protected suspend fun opprettPasient(
        pasientId: PasientId = PasientId(Uuid.generateV4()),
        hpr: HelsepersonellHpr = HelsepersonellHpr("123"),
    ): PasientId {
        pasientRepository.insert(
            Pasient(
                id = pasientId,
                legekontorId = Legekontor.DEFAULT.id,
                fornavn = "fornavn",
                etternavn = "etternavn",
                personident = "personident-${pasientId.value}",
                hprNumbers = listOf(hpr),
            )
        )
        return pasientId
    }

    protected suspend fun opprettKonsultasjon(pasientId: PasientId): KonsultasjonId =
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientId,
                listOf(HelsepersonellHpr("123")),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
            )
        )
}
