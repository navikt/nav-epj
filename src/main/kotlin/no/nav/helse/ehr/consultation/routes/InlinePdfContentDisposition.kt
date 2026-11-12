package no.nav.helse.ehr.consultation.routes

import io.ktor.http.ContentDisposition

private const val DEFAULT_FILE_NAME = "dokument.pdf"
private const val EXTENSION = ".pdf"

fun inlinePdfContentDisposition(title: String?): String {
    val cleaned =
        title
            ?.map { if (it.isISOControl() || it == '/' || it == '\\') '_' else it }
            ?.joinToString("")
            ?.trim()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_FILE_NAME
    val fileName =
        if (cleaned.endsWith(EXTENSION, ignoreCase = true)) cleaned else cleaned + EXTENSION
    val fallback =
        fileName.map { if (it.isAsciiLetterOrDigit() || it in " ._-") it else '_' }.joinToString("")
    val disposition =
        ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, fallback)
    return if (fallback == fileName) {
        disposition.toString()
    } else {
        disposition
            .withParameter(ContentDisposition.Parameters.FileNameAsterisk, fileName)
            .toString()
    }
}

private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
