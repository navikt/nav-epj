package no.nav.helse.ehr.consultation.routes

import kotlin.test.assertEquals
import org.junit.Test

class InlinePdfContentDispositionTest {

    @Test
    fun `ASCII title is used as is`() {
        assertEquals(
            "inline; filename=sykmelding.pdf",
            inlinePdfContentDisposition("sykmelding.pdf"),
        )
    }

    @Test
    fun `extension is appended when missing`() {
        assertEquals("inline; filename=sykmelding.pdf", inlinePdfContentDisposition("sykmelding"))
    }

    @Test
    fun `non ASCII characters get an ASCII fallback and a UTF-8 filename`() {
        assertEquals(
            "inline; filename=\"Sykmelding bl__.pdf\"; " +
                "filename*=utf-8''Sykmelding%20bl%C3%A5%C3%A5.pdf",
            inlinePdfContentDisposition("Sykmelding blåå"),
        )
    }

    @Test
    fun `quotes, separators and control characters cannot leave the filename`() {
        val header = inlinePdfContentDisposition("a\"b/c\\d\r\nX-Injected: 1.pdf")

        assertEquals(
            "inline; filename=\"a_b_c_d__X-Injected_ 1.pdf\"; " +
                "filename*=utf-8''a%22b_c_d__X-Injected%3A%201.pdf",
            header,
        )
    }

    @Test
    fun `missing or blank titles fall back to a default name`() {
        val expected = "inline; filename=dokument.pdf"

        assertEquals(expected, inlinePdfContentDisposition(null))
        assertEquals(expected, inlinePdfContentDisposition("  "))
    }
}
