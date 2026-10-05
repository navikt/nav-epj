package no.nav.helse.hackathon.credentials

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.server.config.MapApplicationConfig
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import no.nav.helse.smart.security.GrantType
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.loadSmartClients
import org.junit.Test
import tools.jackson.databind.JsonNode

private const val SCOPES_OK = """["openid","launch","patient/Patient.rs"]"""

private fun endpoints(name: String) =
    """{"launchUri":"https://$name.example.com/launch","callbackUri":"https://$name.example.com/cb"}"""

private fun teamJson(slot: String) =
    """{"teamSlot":"$slot","publicClient":${endpoints("$slot-p")},""" +
        """"clientSecretClient":${endpoints("$slot-s")},""" +
        """"privateKeyJwtClient":${endpoints("$slot-k")}}"""

internal fun manifestJson(vararg slots: String, scopes: String = SCOPES_OK) =
    """{"schemaVersion":1,"interactiveScopes":$scopes,"systemScopes":["system/Patient.rs"],""" +
        """"teams":[${slots.joinToString(",") { teamJson(it) }}]}"""

internal fun clinicianJson(vararg slots: String) =
    """{"schemaVersion":1,"teams":[${slots.joinToString(",") {
        """{"teamSlot":"$it","clinician":{"login":"login-$it","password":"CANARY-clinician-$it"}}"""
    }}]}"""

internal const val ROSTER_JSON =
    """{"schemaVersion":1,"patients":[{"ref":"CANARY-patient-1"},{"ref":"CANARY-patient-2"}]}"""

class StarterCredentialCliTest {
    private lateinit var base: Path
    private lateinit var checkout: Path
    private lateinit var work: Path
    private val outBytes = ByteArrayOutputStream()
    private val errBytes = ByteArrayOutputStream()

    @BeforeTest
    fun setUp() {
        base = Files.createTempDirectory("credential-cli-test").toRealPath()
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

    private val stdout
        get() = outBytes.toString()

    private val stderr
        get() = errBytes.toString()

    private fun perms(path: Path) =
        PosixFilePermissions.toString(
            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
        )

    private fun tree(path: Path): JsonNode = toolMapper.readTree(path.readText())

    private fun generateComplete(out: Path): Int {
        val manifest = file("manifest.json", manifestJson("alpha", "beta"))
        val clinicians = file("clinicians.json", clinicianJson("alpha", "beta"))
        val roster = file("roster.json", ROSTER_JSON)
        return run(
            "--manifest=$manifest",
            "--output=$out",
            "--clinicians=$clinicians",
            "--roster=$roster",
        )
    }

    @Test
    fun `complete mode writes registry and isolated owner-only team packets`() {
        val out = base.resolve("out")
        assertEquals(0, generateComplete(out), stderr)

        assertEquals("rwx------", perms(out))
        assertEquals("rwx------", perms(out.resolve("teams/alpha")))
        listOf("registry.json", "teams/alpha/packet.json", "teams/beta/launch-key.private.jwk.json")
            .forEach { assertEquals("rw-------", perms(out.resolve(it))) }

        val alpha = tree(out.resolve("teams/alpha/packet.json"))
        assertEquals("COMPLETE", alpha["status"].stringValue())
        assertEquals(0, alpha["missingInputs"].size())
        assertEquals("CANARY-clinician-alpha", alpha["clinician"]["password"].stringValue())
        assertEquals(2, alpha["roster"]["patients"].size())

        val alphaText =
            Files.walk(out.resolve("teams/alpha")).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .map { it.readText() }
                    .toList()
                    .joinToString()
            }
        assertFalse("CANARY-clinician-beta" in alphaText)
        val betaPacket = tree(out.resolve("teams/beta/packet.json"))
        assertFalse(
            betaPacket["clients"]["clientSecret"]["clientSecret"].stringValue() in alphaText
        )
    }

    @Test
    fun `aggregate registry round trips and holds no private keys or clinician data`() {
        val out = base.resolve("out")
        assertEquals(0, generateComplete(out), stderr)
        val registry = out.resolve("registry.json").readText()

        val clients = loadSmartClients(MapApplicationConfig("smart.clientRegistryJson" to registry))
        assertEquals(8, clients.size)
        assertEquals(setOf("alpha", "beta"), clients.mapNotNull { it.teamSlot }.toSet())
        assertEquals(2, clients.count { GrantType.CLIENT_CREDENTIALS in it.grantTypes })
        assertEquals(
            2,
            clients.count {
                it.tokenEndpointAuthMethod == TokenEndpointAuthMethod.CLIENT_SECRET_BASIC
            },
        )
        assertFalse("CANARY" in registry)

        val packet = tree(out.resolve("teams/alpha/packet.json"))
        val backend = packet["clients"]["backendServices"]
        val privateKey =
            RSAKey.parse(
                out.resolve("teams/alpha/${backend["privateKeyFile"].stringValue()}").readText()
            )
        assertTrue(privateKey.isPrivate)
        assertEquals(3072, privateKey.modulus.decode().size * 8)
        assertEquals(backend["keyId"].stringValue(), privateKey.keyID)
        val registered = clients.single { it.clientId == backend["clientId"].stringValue() }
        val registeredJwks =
            tree(out.resolve("registry.json"))
                .first { it["clientId"].stringValue() == registered.clientId }["jwkSet"]
                .stringValue()
        val publicKey = JWKSet.parse(registeredJwks).keys.single()
        assertTrue(!publicKey.isPrivate && publicKey.keyID == privateKey.keyID)
        assertFalse("\"d\"" in registry || "\"p\"" in registry || "\"q\"" in registry)
    }

    @Test
    fun `credentials-only mode marks packets incomplete without clinician or roster`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        val out = base.resolve("out")

        assertEquals(0, run("--manifest=$manifest", "--output=$out", "--credentials-only"), stderr)

        val packet = tree(out.resolve("teams/alpha/packet.json"))
        assertEquals("CREDENTIALS_ONLY", packet["status"].stringValue())
        assertEquals(
            "clinician,roster",
            packet["missingInputs"].joinToString(",") { it.stringValue() },
        )
        assertFalse(packet.has("clinician") || packet.has("roster"))
        assertTrue("CREDENTIALS_ONLY" in stdout)
    }

    @Test
    fun `completeness must be explicit`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        val clinicians = file("clinicians.json", clinicianJson("alpha"))
        val roster = file("roster.json", ROSTER_JSON)
        val out = base.resolve("out")
        val m = "--manifest=$manifest"
        val o = "--output=$out"

        assertEquals(1, run(m, o))
        assertEquals(1, run(m, o, "--clinicians=$clinicians"))
        assertEquals(1, run(m, o, "--roster=$roster"))
        assertEquals(
            1,
            run(m, o, "--clinicians=$clinicians", "--roster=$roster", "--credentials-only"),
        )
        assertFalse(out.exists())
    }

    @Test
    fun `clinician input must match manifest teams exactly`() {
        val manifest = file("manifest.json", manifestJson("alpha", "beta"))
        val roster = file("roster.json", ROSTER_JSON)
        val out = base.resolve("out")

        listOf(clinicianJson("alpha"), clinicianJson("alpha", "beta", "gamma")).forEach {
            val clinicians = file("clinicians.json", it)
            assertEquals(
                1,
                run(
                    "--manifest=$manifest",
                    "--output=$out",
                    "--clinicians=$clinicians",
                    "--roster=$roster",
                ),
            )
            assertFalse("CANARY" in stderr)
        }
        assertFalse(out.exists())
    }

    @Test
    fun `invalid manifests fail before anything is written`() {
        val out = base.resolve("out")
        val bad =
            listOf(
                "not json CANARY-secret",
                manifestJson("alpha", "alpha"),
                manifestJson("alpha", "ALPHA"),
                manifestJson("bad slot"),
                manifestJson("alpha", scopes = "[]"),
                manifestJson("alpha", scopes = """["openid","system/Patient.rs"]"""),
                manifestJson("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "a11"),
                manifestJson("alpha")
                    .replace("https://alpha-p.example.com/launch", "http://insecure.example.com/x"),
                manifestJson("alpha")
                    .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"extra\":\"CANARY\""),
                """{"schemaVersion":1,"schemaVersion":1}""",
            )

        bad.forEachIndexed { index, content ->
            errBytes.reset()
            val manifest = file("manifest-$index.json", content)
            assertEquals(
                1,
                run("--manifest=$manifest", "--output=$out", "--credentials-only"),
                "case $index",
            )
            assertTrue(stderr.startsWith("error: "), "case $index")
            assertFalse("CANARY" in stderr, "case $index")
            assertFalse(out.exists(), "case $index")
        }
    }

    @Test
    fun `malformed json reports only position`() {
        val manifest = file("manifest.json", "{\n  \"teams\": CANARY-secret-value\n}")
        val out = base.resolve("out")

        assertEquals(1, run("--manifest=$manifest", "--output=$out", "--credentials-only"))

        assertTrue("not valid JSON at line 2" in stderr, stderr)
        assertFalse("CANARY" in stderr)
    }

    @Test
    fun `invalid clinician and roster inputs never echo their contents`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        val out = base.resolve("out")
        val badClinicians =
            listOf(
                """{"schemaVersion":1,"teams":[{"teamSlot":"alpha","clinician":{"pw":5}}]}""",
                """{"schemaVersion":1,"teams":[{"teamSlot":"alpha","clinician":{"CANARY bad name":"x"}}]}""",
                """{"schemaVersion":1,"teams":[{"teamSlot":"alpha","clinician":{"a":"b"},"CANARY":1}]}""",
            )
        val roster = file("roster.json", ROSTER_JSON)

        badClinicians.forEachIndexed { index, content ->
            errBytes.reset()
            val clinicians = file("c-$index.json", content)
            assertEquals(
                1,
                run(
                    "--manifest=$manifest",
                    "--output=$out",
                    "--clinicians=$clinicians",
                    "--roster=$roster",
                ),
            )
            assertFalse("CANARY" in stderr)
        }
        val badRoster = file("bad-roster.json", """{"schemaVersion":1,"patients":[]}""")
        val clinicians = file("good.json", clinicianJson("alpha"))
        assertEquals(
            1,
            run(
                "--manifest=$manifest",
                "--output=$out",
                "--clinicians=$clinicians",
                "--roster=$badRoster",
            ),
        )
        assertFalse(out.exists())
    }

    @Test
    fun `output inside the checkout or an alias of it is refused`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        val alias = Files.createSymbolicLink(base.resolve("alias"), checkout)

        listOf(checkout.resolve("out"), alias.resolve("out")).forEach {
            errBytes.reset()
            assertEquals(1, run("--manifest=$manifest", "--output=$it", "--credentials-only"))
            assertTrue("inside the source checkout" in stderr)
        }
        assertEquals(0L, Files.list(checkout).use { it.count() })
    }

    @Test
    fun `default checkout guard rejects output under the real project directory`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        val target = Path.of("build", "credential-cli-guard-test")

        val code =
            runCredentialCli(
                arrayOf("--manifest=$manifest", "--output=$target", "--credentials-only"),
                PrintStream(outBytes),
                PrintStream(errBytes),
            )

        assertEquals(1, code)
        assertTrue("inside the source checkout" in stderr)
        assertFalse(target.exists())
    }

    @Test
    fun `existing output path is never overwritten`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        val existing = Files.createDirectory(base.resolve("existing"))
        val marker = existing.resolve("keep").also { it.writeText("keep") }
        val existingFile = base.resolve("file").also { it.writeText("keep") }

        listOf(existing, existingFile).forEach {
            assertEquals(1, run("--manifest=$manifest", "--output=$it", "--credentials-only"))
        }

        assertEquals("keep", marker.readText())
        assertEquals("keep", existingFile.readText())
        assertNotNull(Files.list(existing).use { it.toList() }.singleOrNull())
    }

    @Test
    fun `no secret reaches stdout or stderr`() {
        val out = base.resolve("out")
        assertEquals(0, generateComplete(out), stderr)
        val secrets =
            listOf("alpha", "beta").flatMap {
                val packet = tree(out.resolve("teams/$it/packet.json"))
                listOf(
                    packet["clients"]["clientSecret"]["clientSecret"].stringValue(),
                    "CANARY",
                    out.resolve("teams/$it/launch-key.private.jwk.json").readText().trim(),
                )
            }

        secrets.forEach { assertFalse(it in stdout + stderr) }
    }

    @Test
    fun `unknown arguments are rejected without echoing them`() {
        assertEquals(1, run("--password=CANARY-arg"))
        assertEquals(1, run("--manifest"))
        assertFalse("CANARY" in stderr)
    }

    private val legacyClient =
        """{"clientId":"legacy-app","redirectUris":["https://legacy.example.com/cb"],""" +
            """"launchUris":["https://legacy.example.com/launch"],""" +
            """"scopes":["openid","launch","patient/Patient.rs"],"navn":"Legacy","beskrivelse":"keep me"}"""

    private fun existingRegistry(): Path {
        assertEquals(0, generateComplete(base.resolve("initial")), stderr)
        val text = base.resolve("initial/registry.json").readText().trimEnd().removeSuffix("]")
        return file("existing.json", "$text,$legacyClient]")
    }

    private fun clientIds(registry: Path, slot: String? = null) =
        tree(registry)
            .filter { slot == null || it["teamSlot"]?.stringValue() == slot }
            .map { it["clientId"].stringValue() }
            .toSet()

    private fun runWithExisting(
        existing: Path,
        out: Path,
        slots: List<String>,
        vararg extra: String,
    ) =
        run(
            "--manifest=${file("m-${out.fileName}.json", manifestJson(*slots.toTypedArray()))}",
            "--output=$out",
            "--clinicians=${file("c-${out.fileName}.json", clinicianJson(*slots.toTypedArray()))}",
            "--roster=${file("r-${out.fileName}.json", ROSTER_JSON)}",
            "--existing-registry=$existing",
            *extra,
        )

    @Test
    fun `adding a team preserves every existing registration and packets cover only new teams`() {
        val existing = existingRegistry()
        val out = base.resolve("added")

        assertEquals(0, runWithExisting(existing, out, listOf("gamma")), stderr)

        val before = tree(existing).toList()
        val after = tree(out.resolve("registry.json")).toList()
        assertEquals(before.size + 4, after.size)
        before.forEach { assertTrue(it in after) }
        assertEquals(4, clientIds(out.resolve("registry.json"), "gamma").size)
        assertEquals(
            listOf("gamma"),
            Files.list(out.resolve("teams")).use { s -> s.map { it.fileName.toString() }.toList() },
        )
        assertEquals(
            before.size + 4,
            loadSmartClients(
                    MapApplicationConfig(
                        "smart.clientRegistryJson" to out.resolve("registry.json").readText()
                    )
                )
                .size,
        )
        assertTrue("keeps ${before.size} existing" in stdout)
    }

    @Test
    fun `adding an already registered team is refused and the input is untouched`() {
        val existing = existingRegistry()
        val original = existing.readText()

        listOf("alpha", "ALPHA").forEach {
            val out = base.resolve("dup-$it")
            assertEquals(1, runWithExisting(existing, out, listOf(it)))
            assertTrue("use --rotate" in stderr, stderr)
            assertFalse(out.exists())
        }
        assertEquals(original, existing.readText())
    }

    @Test
    fun `rotation replaces only the selected team`() {
        val existing = existingRegistry()
        val out = base.resolve("rotated")
        val oldAlpha = clientIds(existing, "alpha")
        val oldKeyId =
            tree(base.resolve("initial/teams/alpha/packet.json"))["clients"]["backendServices"][
                    "keyId"]
                .stringValue()

        assertEquals(0, runWithExisting(existing, out, listOf("alpha"), "--rotate"), stderr)

        val registry = out.resolve("registry.json")
        val newAlpha = clientIds(registry, "alpha")
        assertEquals(4, newAlpha.size)
        assertTrue(newAlpha.intersect(oldAlpha).isEmpty())
        assertEquals(clientIds(existing, "beta"), clientIds(registry, "beta"))
        assertTrue("legacy-app" in clientIds(registry))
        val betaBefore = tree(existing).filter { it["teamSlot"]?.stringValue() == "beta" }
        betaBefore.forEach { assertTrue(it in tree(registry).toList()) }
        assertEquals(9, tree(registry).size())
        assertEquals(
            9,
            loadSmartClients(
                    MapApplicationConfig("smart.clientRegistryJson" to registry.readText())
                )
                .size,
        )
        val newKeyId =
            tree(out.resolve("teams/alpha/packet.json"))["clients"]["backendServices"]["keyId"]
                .stringValue()
        assertFalse(oldKeyId == newKeyId)
        assertFalse(Files.exists(out.resolve("teams/beta")))
        assertTrue("Rotated credentials for 1" in stdout)
    }

    @Test
    fun `rotation refuses unknown or partial teams`() {
        val existing = existingRegistry()
        val partial =
            tree(existing).filterNot { it["clientId"].stringValue().startsWith("beta-public-") }
        val partialFile = file("partial.json", toolMapper.writeValueAsString(partial))

        assertEquals(
            1,
            runWithExisting(existing, base.resolve("unknown"), listOf("gamma"), "--rotate"),
        )
        assertTrue("unknown team" in stderr, stderr)
        errBytes.reset()
        assertEquals(
            1,
            runWithExisting(partialFile, base.resolve("partial"), listOf("beta"), "--rotate"),
        )
        assertTrue("partial rotation" in stderr, stderr)
        assertFalse(base.resolve("unknown").exists() || base.resolve("partial").exists())
    }

    @Test
    fun `rotate requires an existing registry`() {
        val manifest = file("manifest.json", manifestJson("alpha"))
        assertEquals(
            1,
            run(
                "--manifest=$manifest",
                "--output=${base.resolve("o")}",
                "--credentials-only",
                "--rotate",
            ),
        )
        assertFalse(base.resolve("o").exists())
    }

    @Test
    fun `invalid existing registries fail safely`() {
        val bad =
            listOf(
                "{}",
                "[]",
                """[{"clientId":"x","clientSecret":"CANARY-existing","bogus":1}]""",
                """[{"clientId":"x","clientSecret":"CANARY-existing"}""",
                """[{"clientId":"x","clientSecret":"CANARY-existing","scopes":[]}]""",
                """[{"clientId":"x","clientSecret":"CANARY-existing","scopes":["openid"]},""" +
                    """{"clientId":"x","clientSecret":"CANARY-existing","scopes":["openid"]}]""",
            )
        bad.forEachIndexed { index, content ->
            errBytes.reset()
            val out = base.resolve("bad-$index")
            assertEquals(
                1,
                runWithExisting(file("bad-$index.json", content), out, listOf("gamma")),
                "case $index",
            )
            assertFalse("CANARY" in stderr, "case $index")
            assertFalse(out.exists(), "case $index")
        }
    }

    @Test
    fun `team cap counts existing teams`() {
        val tenTeams =
            (1..10).joinToString(",", "[", "]") {
                """{"clientId":"t$it","teamSlot":"t$it","scopes":["openid"]}"""
            }
        val out = base.resolve("eleventh")

        assertEquals(1, runWithExisting(file("ten.json", tenTeams), out, listOf("gamma")))

        assertTrue("more than 10 teams" in stderr, stderr)
        assertFalse(out.exists())
    }
}
