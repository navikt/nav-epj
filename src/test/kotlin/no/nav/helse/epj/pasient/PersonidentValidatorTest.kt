package no.nav.helse.epj.pasient

import java.time.LocalDate
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import no.nav.helse.core.utils.UgyldigPersonidentException
import org.junit.Test

/**
 * All personidents below are synthetic and constructed purely from the public modulus-11 algorithm
 * for test purposes. None are issued or real identifiers.
 */
class PersonidentValidatorTest {

    @Test
    fun `accepts a valid FNR matching its birth date`() {
        PersonidentValidator.validate(
            personident = "15068500017",
            personidentType = PersonidentType.FNR,
            birthDate = LocalDate.of(1985, 6, 15),
        )
    }

    @Test
    fun `accepts a valid DNR with the day offset by 40`() {
        PersonidentValidator.validate(
            personident = "63117800026",
            personidentType = PersonidentType.DNR,
            birthDate = LocalDate.of(1978, 11, 23),
        )
    }

    @Test
    fun `resolves individual number 500-999 with year 00-39 to 2000-2039`() {
        PersonidentValidator.validate(
            personident = "10030550140",
            personidentType = PersonidentType.FNR,
            birthDate = LocalDate.of(2005, 3, 10),
        )
    }

    @Test
    fun `resolves individual number 500-749 with year 54-99 to 1854-1899`() {
        PersonidentValidator.validate(
            personident = "14078750023",
            personidentType = PersonidentType.FNR,
            birthDate = LocalDate.of(1887, 7, 14),
        )
    }

    @Test
    fun `resolves individual number 900-999 with year 40-99 to 1940-1999`() {
        PersonidentValidator.validate(
            personident = "29027290068",
            personidentType = PersonidentType.FNR,
            birthDate = LocalDate.of(1972, 2, 29),
        )
    }

    @Test
    fun `rejects a bad checksum`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15068500018",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }

    @Test
    fun `rejects a valid FNR declared as DNR`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15068500017",
                personidentType = PersonidentType.DNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }

    @Test
    fun `rejects a valid DNR declared as FNR`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "63117800026",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1978, 11, 23),
            )
        }
    }

    @Test
    fun `rejects a birth date with the wrong day`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15068500017",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 16),
            )
        }
    }

    @Test
    fun `rejects a birth date with the wrong month`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15068500017",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 7, 15),
            )
        }
    }

    @Test
    fun `rejects a birth date with the correct day and month but wrong century`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "14078750023",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1987, 7, 14),
            )
        }
    }

    @Test
    fun `rejects an individual number with no valid century mapping without leaking it`() {
        // Individual number 750 falls in the gap between the 500-749 (year>=54) and
        // 900-999 (year>=40) rules, and year 40 excludes the 500-999&&year<=39 rule, so no
        // century resolves for any of these combinations.
        val personident = "01014075069"

        val exception =
            assertFailsWith<UgyldigPersonidentException> {
                PersonidentValidator.validate(
                    personident = personident,
                    personidentType = PersonidentType.FNR,
                    birthDate = LocalDate.of(1940, 1, 1),
                )
            }

        assertFalse(exception.message.orEmpty().contains("750"))
    }

    @Test
    fun `rejects non-digit characters`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "1506850001A",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }

    @Test
    fun `rejects the wrong length`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "150685000",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }

    @Test
    fun `accepts a synthetic FNR with the month offset by 80`() {
        PersonidentValidator.validate(
            personident = "15868500082",
            personidentType = PersonidentType.FNR,
            birthDate = LocalDate.of(1985, 6, 15),
        )
    }

    @Test
    fun `accepts a synthetic DNR with the day offset by 40 and month by 80`() {
        PersonidentValidator.validate(
            personident = "63917800091",
            personidentType = PersonidentType.DNR,
            birthDate = LocalDate.of(1978, 11, 23),
        )
    }

    @Test
    fun `identifies synthetic and conventional personidents`() {
        assertTrue(PersonidentValidator.isSynthetic("15868500082"))
        assertTrue(PersonidentValidator.isSynthetic("63917800091"))
        assertFalse(PersonidentValidator.isSynthetic("15068500017"))
        assertFalse(PersonidentValidator.isSynthetic("63117800026"))
        assertFalse(PersonidentValidator.isSynthetic("not-a-number"))
    }

    @Test
    fun `rejects a synthetic FNR with a bad checksum`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15868500083",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }

    @Test
    fun `rejects a synthetic FNR declared as DNR`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15868500082",
                personidentType = PersonidentType.DNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }

    @Test
    fun `rejects a synthetic DNR declared as FNR`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "63917800091",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1978, 11, 23),
            )
        }
    }

    @Test
    fun `rejects a synthetic personident whose birth date does not match the decoded date`() {
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15868500082",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 16),
            )
        }
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = "15868500082",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 12, 15),
            )
        }
    }

    @Test
    fun `rejects the NAV month offset of 40 with a valid checksum`() {
        val personident = "15468500180"

        assertFalse(PersonidentValidator.isSynthetic(personident))
        assertFailsWith<UgyldigPersonidentException> {
            PersonidentValidator.validate(
                personident = personident,
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1985, 6, 15),
            )
        }
    }
}
