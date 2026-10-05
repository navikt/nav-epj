package no.nav.helse.smart.tooling

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PrivateExportTest {
    private lateinit var base: Path
    private lateinit var checkout: Path
    private lateinit var outside: Path

    @BeforeTest
    fun setUp() {
        base = Files.createTempDirectory("private-export-test").toRealPath()
        checkout = Files.createDirectory(base.resolve("checkout"))
        outside = Files.createDirectory(base.resolve("outside"))
    }

    @AfterTest
    fun tearDown() {
        Files.walk(base).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    private fun perms(path: Path) =
        PosixFilePermissions.toString(
            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
        )

    @Test
    fun `directories are 700 and files are 600`() {
        val export = PrivateExport.create(outside.resolve("out"), listOf(checkout))
        export.createDirectory("teams")
        export.writeFile("teams/a.json", "secret".toByteArray())
        export.writeFile("registry.json", "[]".toByteArray())

        assertEquals("rwx------", perms(outside.resolve("out")))
        assertEquals("rwx------", perms(outside.resolve("out/teams")))
        assertEquals("rw-------", perms(outside.resolve("out/teams/a.json")))
        assertEquals("rw-------", perms(outside.resolve("out/registry.json")))
        assertEquals("secret", outside.resolve("out/teams/a.json").readText())
    }

    @Test
    fun `existing directory file and symlinks are refused and untouched`() {
        val dir = Files.createDirectory(outside.resolve("dir"))
        val file = outside.resolve("file").also { it.writeText("keep") }
        val link = Files.createSymbolicLink(outside.resolve("link"), dir)
        val dangling =
            Files.createSymbolicLink(outside.resolve("dangling"), outside.resolve("none"))

        listOf(dir, file, link, dangling).forEach {
            assertFailsWith<CredentialToolException> { PrivateExport.create(it, listOf(checkout)) }
        }
        assertEquals("keep", file.readText())
        assertFalse(outside.resolve("none").exists())
        assertTrue(Files.list(dir).use { it.count() } == 0L)
    }

    @Test
    fun `output inside the checkout is refused directly and through aliases`() {
        val alias = Files.createSymbolicLink(outside.resolve("alias"), checkout)
        val sub = Files.createDirectory(checkout.resolve("sub"))
        val targets =
            listOf(
                checkout.resolve("out"),
                sub.resolve("out"),
                alias.resolve("out"),
                outside.resolve("alias/sub/out"),
            )

        targets.forEach {
            val error =
                assertFailsWith<CredentialToolException> {
                    PrivateExport.create(it, listOf(checkout))
                }
            assertTrue("inside the source checkout" in error.message.orEmpty())
        }
        assertEquals(1L, Files.list(checkout).use { it.count() })
    }

    @Test
    fun `missing parent dotdot segments and filesystem root are refused`() {
        listOf(
                outside.resolve("missing/out"),
                Paths.get(outside.toString(), "..", "outside", "out"),
                Paths.get("/"),
            )
            .forEach {
                assertFailsWith<CredentialToolException> {
                    PrivateExport.create(it, listOf(checkout))
                }
            }
        assertFalse(outside.resolve("missing").exists())
    }

    @Test
    fun `failed write rolls back only paths created by this run`() {
        val sibling = outside.resolve("sibling").also { it.writeText("keep") }
        val export = PrivateExport.create(outside.resolve("out"), listOf(checkout))
        export.createDirectory("teams")
        export.writeFile("teams/a.json", "one".toByteArray())

        val error =
            assertFailsWith<CredentialToolException> {
                export.writeFile("teams/a.json", "two".toByteArray())
            }

        assertFalse("one" in error.message.orEmpty() || "two" in error.message.orEmpty())
        assertFalse(outside.resolve("out").exists())
        assertEquals("keep", sibling.readText())
    }

    @Test
    fun `rollback keeps a directory that holds foreign files`() {
        val export = PrivateExport.create(outside.resolve("out"), listOf(checkout))
        export.writeFile("a.json", "x".toByteArray())
        outside.resolve("out/foreign").writeText("keep")

        export.rollback()

        assertFalse(outside.resolve("out/a.json").exists())
        assertEquals("keep", outside.resolve("out/foreign").readText())
    }

    @Test
    fun `existing symlinks inside the export are not followed`() {
        val victim = outside.resolve("victim").also { it.writeText("keep") }
        val export = PrivateExport.create(outside.resolve("out"), listOf(checkout))
        Files.createSymbolicLink(outside.resolve("out/link.json"), victim)

        assertFailsWith<CredentialToolException> {
            export.writeFile("link.json", "overwritten".toByteArray())
        }

        assertEquals("keep", victim.readText())
    }

    @Test
    fun `invalid relative paths are rejected`() {
        val export = PrivateExport.create(outside.resolve("out"), listOf(checkout))
        listOf("../x", ".hidden", "a//b", "/abs", "").forEach {
            assertFailsWith<IllegalArgumentException> { export.writeFile(it, ByteArray(0)) }
        }
        assertFailsWith<IllegalArgumentException> { export.writeFile("nodir/x", ByteArray(0)) }
        export.rollback()
    }
}
