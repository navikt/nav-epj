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

    private val digitsOnly = Regex("^\\d{11}$")
    private val k1Weights = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
    private val k2Weights = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

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

        val encodedDay = digits[0] * 10 + digits[1]
        val month = digits[2] * 10 + digits[3]
        val twoDigitYear = digits[4] * 10 + digits[5]
        val individualNumber = digits[6] * 100 + digits[7] * 10 + digits[8]

        val day =
            when (personidentType) {
                PersonidentType.FNR ->
                    encodedDay.takeIf { it in 1..31 }
                        ?: throw UgyldigPersonidentException(
                            "Fødselsdag i personident samsvarer ikke med et fødselsnummer (FNR)"
                        )
                PersonidentType.DNR ->
                    (encodedDay - 40).takeIf { it in 1..31 }
                        ?: throw UgyldigPersonidentException(
                            "Fødselsdag i personident samsvarer ikke med et D-nummer (DNR)"
                        )
            }

        val century =
            resolveCentury(individualNumber, twoDigitYear)
                ?: throw UgyldigPersonidentException(
                    "Fant ikke noe gyldig århundre for individnummer $individualNumber"
                )

        val encodedBirthDate =
            try {
                LocalDate.of(century + twoDigitYear, month, day)
            } catch (cause: DateTimeException) {
                throw UgyldigPersonidentException("Personident koder ikke en gyldig kalenderdato")
            }

        if (encodedBirthDate != birthDate) {
            throw UgyldigPersonidentException("Fødselsdato samsvarer ikke med personident")
        }
    }

    private fun validateChecksums(digits: List<Int>) {
        val k1 =
            checksum(digits.subList(0, 9), k1Weights)
                ?: throw UgyldigPersonidentException("Personident har ugyldig kontrollsiffer")
        if (k1 != digits[9]) {
            throw UgyldigPersonidentException("Personident har ugyldig kontrollsiffer")
        }

        val k2 =
            checksum(digits.subList(0, 9) + k1, k2Weights)
                ?: throw UgyldigPersonidentException("Personident har ugyldig kontrollsiffer")
        if (k2 != digits[10]) {
            throw UgyldigPersonidentException("Personident har ugyldig kontrollsiffer")
        }
    }

    private fun checksum(values: List<Int>, weights: IntArray): Int? {
        val sum = values.indices.sumOf { values[it] * weights[it] }
        return when (val remainder = 11 - (sum % 11)) {
            11 -> 0
            10 -> null
            else -> remainder
        }
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
        when {
            individualNumber in 0..499 -> 1900
            individualNumber in 900..999 && twoDigitYear >= 40 -> 1900
            individualNumber in 500..749 && twoDigitYear >= 54 -> 1800
            individualNumber in 500..999 && twoDigitYear <= 39 -> 2000
            else -> null
        }
}
