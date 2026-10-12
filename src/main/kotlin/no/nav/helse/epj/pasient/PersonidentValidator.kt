package no.nav.helse.epj.pasient

import java.time.DateTimeException
import java.time.LocalDate
import no.nav.helse.core.utils.UgyldigPersonidentException

/**
 * Validates Norwegian personident (fødselsnummer/D-nummer) independently of any transport
 * framework. See https://www.skatteetaten.no/ and fnrinfo.no for the modulus-11 checksum and
 * individual-number century rules implemented here.
 */
object PersonidentValidator {

    private const val BASE_DIGIT_COUNT = 9
    private const val CHECKSUM_MODULUS = 11
    private const val INVALID_CHECKSUM = 10
    private const val DNR_DAY_OFFSET = 40
    private const val FIRST_CHECKSUM_INDEX = 9
    private const val SECOND_CHECKSUM_INDEX = 10

    private val digitsOnly = Regex("^\\d{11}$")
    private val dayOfMonthRange = 1..31
    private val k1Weights = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
    private val k2Weights = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

    private class CenturyRule(
        val century: Int,
        val individualNumbers: IntRange,
        val twoDigitYears: IntRange,
    )

    private val centuryRules =
        listOf(
            CenturyRule(century = 1900, individualNumbers = 0..499, twoDigitYears = 0..99),
            CenturyRule(century = 1900, individualNumbers = 900..999, twoDigitYears = 40..99),
            CenturyRule(century = 1800, individualNumbers = 500..749, twoDigitYears = 54..99),
            CenturyRule(century = 2000, individualNumbers = 500..999, twoDigitYears = 0..39),
        )

    /**
     * Validates that [personident] is a well-formed personident of the declared [personidentType],
     * and that it encodes exactly [birthDate]. Throws [UgyldigPersonidentException] with a
     * human-readable reason for any failure.
     */
    fun validate(personident: String, personidentType: PersonidentType, birthDate: LocalDate) {
        if (!digitsOnly.matches(personident)) {
            throw UgyldigPersonidentException("Personident må bestå av nøyaktig 11 siffer")
        }

        val digits = personident.map { it - '0' }
        validateChecksums(digits)

        val encodedBirthDate =
            parseBirthDate(
                personidentType = personidentType,
                encodedDay = personident.substring(0, 2).toInt(),
                month = personident.substring(2, 4).toInt(),
                twoDigitYear = personident.substring(4, 6).toInt(),
                individualNumber = personident.substring(6, 9).toInt(),
            )

        if (encodedBirthDate != birthDate) {
            throw UgyldigPersonidentException("Fødselsdato samsvarer ikke med personident")
        }
    }

    private fun parseBirthDate(
        personidentType: PersonidentType,
        encodedDay: Int,
        month: Int,
        twoDigitYear: Int,
        individualNumber: Int,
    ): LocalDate {
        val day = dayOfMonth(personidentType, encodedDay)
        val century =
            resolveCentury(individualNumber, twoDigitYear)
                ?: throw UgyldigPersonidentException(
                    "Fant ikke noe gyldig århundre for individnummeret i personidenten"
                )
        return try {
            LocalDate.of(century + twoDigitYear, month, day)
        } catch (cause: DateTimeException) {
            throw UgyldigPersonidentException(
                "Personidenten inneholder ikke en gyldig kalenderdato",
                cause,
            )
        }
    }

    private fun dayOfMonth(personidentType: PersonidentType, encodedDay: Int): Int {
        val (day, description) =
            when (personidentType) {
                PersonidentType.FNR -> encodedDay to "et fødselsnummer (FNR)"
                PersonidentType.DNR -> (encodedDay - DNR_DAY_OFFSET) to "et D-nummer (DNR)"
            }
        if (day !in dayOfMonthRange) {
            throw UgyldigPersonidentException(
                "Fødselsdag i personident samsvarer ikke med $description"
            )
        }
        return day
    }

    private fun validateChecksums(digits: List<Int>) {
        val baseDigits = digits.subList(0, BASE_DIGIT_COUNT)
        val k1 = checksum(baseDigits, k1Weights)
        val k2 = k1?.let { checksum(baseDigits + it, k2Weights) }
        if (k1 != digits[FIRST_CHECKSUM_INDEX] || k2 != digits[SECOND_CHECKSUM_INDEX]) {
            throw UgyldigPersonidentException("Personident har ugyldig kontrollsiffer")
        }
    }

    private fun checksum(values: List<Int>, weights: IntArray): Int? {
        val sum = values.indices.sumOf { values[it] * weights[it] }
        val control = (CHECKSUM_MODULUS - sum % CHECKSUM_MODULUS) % CHECKSUM_MODULUS
        return control.takeIf { it != INVALID_CHECKSUM }
    }

    /**
     * Resolves the century of the two-digit year based on the individual number, following the
     * standard Norwegian rule set (see fnrinfo.no):
     * - 000-499 -> 1900-1999
     * - 900-999 with year 40-99 -> 1940-1999
     * - 500-749 with year 54-99 -> 1854-1899
     * - 500-999 with year 00-39 -> 2000-2039
     */
    private fun resolveCentury(individualNumber: Int, twoDigitYear: Int): Int? =
        centuryRules
            .firstOrNull {
                individualNumber in it.individualNumbers && twoDigitYear in it.twoDigitYears
            }
            ?.century
}
