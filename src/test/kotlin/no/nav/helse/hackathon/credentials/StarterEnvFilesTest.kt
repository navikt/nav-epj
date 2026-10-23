package no.nav.helse.hackathon.credentials

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import tools.jackson.databind.JsonNode

class StarterEnvFilesTest {
    private lateinit var base: Path
    private lateinit var checkout: Path
    private lateinit var work: Path
    private val outBytes = ByteArrayOutputStream()
    private val errBytes = ByteArrayOutputStream()

    @BeforeTest
    fun setUp() {
        base = Files.createTempDirectory("env-files-test").toRealPath()
        checkout = Files.createDirectory(base.resolve("checkout"))
        work = Files.createDirectory(base.resolve("work"))
    }

    @AfterTest
    fun tearDown() {
        Files.walk(base).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    private fun file(name: String, content: String): Path =
        work.resolve(name).also { it.writeText(content) }

    private fun run(vararg args: String): Int =
        runCredentialCli(
            arrayOf("--checkout-root=$checkout", *args),
            PrintStream(outBytes),
            PrintStream(errBytes),
        )

    private val stderr
        get() = errBytes.toString()

    private fun perms(path: Path) =
        PosixFilePermissions.toString(
            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
        )

    private fun tree(path: Path): JsonNode = toolMapper.readTree(path.readText())

    private fun envLines(out: Path, name: String) =
        out.resolve("teams/alpha/$name").readText().trim().lines().associate {
            it.substringBefore("=") to it.substringAfter("=")
        }

    @Test
    fun `each team dir holds owner-only env files per variant`() {
        val out = base.resolve("out")
        val manifest =
            file(
                "manifest.json",
                manifestJson("alpha")
                    .replace("https://alpha-p.example.com/cb", "http://localhost:3000/callback"),
            )
        assertEquals(0, run("--manifest=$manifest", "--output=$out", "--credentials-only"), stderr)
        val packet = tree(out.resolve("teams/alpha/packet.json"))["clients"]
        val dirFiles =
            Files.list(out.resolve("teams/alpha")).use {
                it.map { p -> p.fileName.toString() }.toList()
            }
        assertEquals(
            setOf(
                "packet.json",
                "launch-key.private.jwk.json",
                "backend-key.private.jwk.json",
                "public.env",
                "client-secret.env",
                "private-key-jwt.env",
                "backend-services.env",
            ),
            dirFiles.toSet(),
        )
        dirFiles.forEach { assertEquals("rw-------", perms(out.resolve("teams/alpha/$it"))) }

        val common =
            mapOf(
                "FHIR_BASE_URL" to "https://epj.ekstern.dev.nav.no/fhir",
                "AUTH_ISSUER" to "https://epj.ekstern.dev.nav.no/oidc",
                "SCOPES" to "openid launch patient/Patient.rs",
            )
        assertEquals(
            common +
                mapOf(
                    "CLIENT_ID" to packet["public"]["clientId"].stringValue(),
                    "PORT" to "3000",
                    "REDIRECT_URI" to "http://localhost:3000/callback",
                ),
            envLines(out, "public.env"),
        )
        assertEquals(
            common +
                mapOf(
                    "CLIENT_ID" to packet["clientSecret"]["clientId"].stringValue(),
                    "REDIRECT_URI" to "https://alpha-s.example.com/cb",
                    "CLIENT_SECRET" to packet["clientSecret"]["clientSecret"].stringValue(),
                ),
            envLines(out, "client-secret.env"),
        )
        assertEquals(
            common +
                mapOf(
                    "CLIENT_ID" to packet["privateKeyJwt"]["clientId"].stringValue(),
                    "REDIRECT_URI" to "https://alpha-k.example.com/cb",
                    "PRIVATE_JWK_PATH" to "keys/launch-key.private.jwk.json",
                ),
            envLines(out, "private-key-jwt.env"),
        )
        assertEquals(
            common +
                mapOf(
                    "CLIENT_ID" to packet["backendServices"]["clientId"].stringValue(),
                    "SCOPES" to "system/Patient.rs",
                    "PRIVATE_JWK_PATH" to "keys/backend-key.private.jwk.json",
                    "PATIENT_IDENTIFIER" to "urn:oid:2.16.578.1.12.4.1.4.1|20856210112",
                ),
            envLines(out, "backend-services.env"),
        )
    }

    @Test
    fun `manifest overrides fhirBaseUrl and authIssuer`() {
        val out = base.resolve("out")
        val manifest =
            file(
                "manifest.json",
                manifestJson("alpha")
                    .replace(
                        "\"schemaVersion\":1",
                        "\"schemaVersion\":1,\"fhirBaseUrl\":\"http://localhost:8080/fhir\"," +
                            "\"authIssuer\":\"https://auth.example.com/oidc\"",
                    ),
            )
        assertEquals(0, run("--manifest=$manifest", "--output=$out", "--credentials-only"), stderr)
        val env = envLines(out, "public.env")
        assertEquals("http://localhost:8080/fhir", env["FHIR_BASE_URL"])
        assertEquals("https://auth.example.com/oidc", env["AUTH_ISSUER"])
    }

    @Test
    fun `invalid fhirBaseUrl or authIssuer is rejected`() {
        listOf(
                "https://x.example.com/fhir/",
                "http://x.example.com/fhir",
                "ftp://x.example.com",
                "https://x.example.com/fhir?a=b",
                "not a url",
            )
            .forEachIndexed { index, url ->
                listOf("fhirBaseUrl", "authIssuer").forEach { field ->
                    errBytes.reset()
                    val out = base.resolve("out-$index-$field")
                    val manifest =
                        file(
                            "manifest-$index-$field.json",
                            manifestJson("alpha")
                                .replace(
                                    "\"schemaVersion\":1",
                                    "\"schemaVersion\":1,\"$field\":\"$url\"",
                                ),
                        )
                    assertEquals(
                        1,
                        run("--manifest=$manifest", "--output=$out", "--credentials-only"),
                        "$field $url",
                    )
                    assertTrue(field in stderr, "$field $url")
                    assertFalse(out.exists())
                }
            }
    }

    @Test
    fun `registrations get readable navn per interactive client`() {
        val out = base.resolve("out")
        val manifest = file("manifest.json", manifestJson("alpha"))
        assertEquals(0, run("--manifest=$manifest", "--output=$out", "--credentials-only"), stderr)
        val navn =
            tree(out.resolve("registry.json")).mapNotNull { it["navn"]?.stringValue() }.toSet()
        assertEquals(setOf("alpha public", "alpha client secret", "alpha private key JWT"), navn)
    }
}
