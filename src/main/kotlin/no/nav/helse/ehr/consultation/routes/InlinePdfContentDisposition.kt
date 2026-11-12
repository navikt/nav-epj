package no.nav.helse.ehr.consultation.routes

private const val DEFAULT_FILE_NAME = "dokument.pdf"
private const val EXTENSION = ".pdf"
private const val BYTE_MASK = 0xFF
private const val RFC_5987_SAFE = "!#$&+-.^_`|~"

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
    return "inline; filename=\"$fallback\"; filename*=UTF-8''${fileName.percentEncoded()}"
}

private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun String.percentEncoded(): String = buildString {
    for (byte in toByteArray(Charsets.UTF_8)) {
        val char = byte.toInt().toChar()
        if (byte >= 0 && (char.isAsciiLetterOrDigit() || char in RFC_5987_SAFE)) {
            append(char)
        } else {
            append('%').append("%02X".format(byte.toInt() and BYTE_MASK))
        }
    }
}
