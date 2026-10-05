package no.nav.helse.smart.tooling

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess
import no.nav.helse.smart.security.generateStarterCredentials

private const val USAGE =
    "usage: generateTeamCredentials --manifest=FILE --output=NEW_DIRECTORY " +
        "(--clinicians=FILE --roster=FILE | --credentials-only) [--checkout-root=DIRECTORY]"

internal class CliOptions(
    val manifest: Path,
    val output: Path,
    val clinicians: Path?,
    val roster: Path?,
    val credentialsOnly: Boolean,
    val checkoutRoot: Path,
)

fun main(args: Array<String>) {
    exitProcess(runCredentialCli(args, System.out, System.err))
}

internal fun runCredentialCli(args: Array<String>, out: PrintStream, err: PrintStream): Int =
    try {
        generateCredentials(parseOptions(args), out)
        0
    } catch (e: CredentialToolException) {
        err.println("error: ${e.message}")
        1
    } catch (e: Exception) {
        err.println("error: unexpected failure (${e.javaClass.simpleName}); details withheld")
        2
    }

internal fun parseOptions(args: Array<String>): CliOptions {
    val values = mutableMapOf<String, String>()
    var credentialsOnly = false
    args.forEachIndexed { index, arg ->
        val name = arg.substringBefore('=')
        ensure(name in VALUE_OPTIONS || arg == CREDENTIALS_ONLY) {
            "unknown argument at position ${index + 1}; $USAGE"
        }
        ensure(name == CREDENTIALS_ONLY || ('=' in arg && name !in values)) {
            "argument at position ${index + 1} must be given once as --name=value; $USAGE"
        }
        if (name == CREDENTIALS_ONLY) credentialsOnly = true
        else values[name] = arg.substringAfter('=')
    }
    val manifest = values[MANIFEST]
    val output = values[OUTPUT]
    ensure(manifest != null && output != null) { "--manifest and --output are required; $USAGE" }
    val clinicians = values[CLINICIANS]
    val roster = values[ROSTER]
    ensure((clinicians == null) == (roster == null)) {
        "--clinicians and --roster must be provided together; $USAGE"
    }
    ensure((clinicians == null) == credentialsOnly) {
        "provide --clinicians and --roster, or --credentials-only to explicitly produce " +
            "incomplete packets; $USAGE"
    }
    return CliOptions(
        manifest = Paths.get(manifest!!),
        output = Paths.get(output!!),
        clinicians = clinicians?.let(Paths::get),
        roster = roster?.let(Paths::get),
        credentialsOnly = credentialsOnly,
        checkoutRoot = Paths.get(values[CHECKOUT_ROOT] ?: "").toAbsolutePath(),
    )
}

private fun generateCredentials(options: CliOptions, out: PrintStream) {
    val manifest = readManifest(options.manifest)
    val inputs = readExternalInputs(options, manifest)
    val forbiddenRoots = checkoutRoots(options.checkoutRoot)
    PrivateExport.validateTarget(options.output, forbiddenRoots)

    val generated =
        try {
            generateStarterCredentials(
                manifest.teams,
                manifest.interactiveScopes,
                manifest.systemScopes,
            )
        } catch (e: IllegalArgumentException) {
            throw CredentialToolException(
                "manifest rejected by registry validation: ${e.message}",
                e,
            )
        }

    val export = PrivateExport.create(options.output, forbiddenRoots)
    try {
        export.writeFile("registry.json", registryJson(generated.registrations))
        writeTeamFiles(export, manifest, generated, inputs)
    } catch (e: Exception) {
        export.rollback()
        throw e
    }
    out.println("Wrote credentials for ${generated.teams.size} team(s) to ${options.output}")
    out.println(
        if (inputs == null) {
            "Packets are CREDENTIALS_ONLY: clinician login and roster are still missing."
        } else {
            "Packets are COMPLETE."
        }
    )
}

private fun readExternalInputs(options: CliOptions, manifest: StarterManifest): ExternalInputs? {
    if (options.clinicians == null || options.roster == null) return null
    val clinicians = readClinicians(options.clinicians)
    val roster = readRoster(options.roster)
    val expected = manifest.teams.map { it.teamSlot }.toSet()
    val missing = expected - clinicians.byTeam.keys
    val unexpected = clinicians.byTeam.keys - expected
    ensure(missing.isEmpty() && unexpected.isEmpty()) {
        "clinician input must cover exactly the manifest teams; " +
            "missing: ${missing.sorted()}, not in manifest: ${unexpected.sorted()}"
    }
    return ExternalInputs(clinicians, roster)
}

internal fun checkoutRoots(checkoutRoot: Path): List<Path> {
    val real = checkoutRoot.toRealPath()
    val gitRoot =
        generateSequence(real) { it.parent }.firstOrNull { Files.exists(it.resolve(".git")) }
    return listOfNotNull(real, gitRoot).distinct()
}

private const val MANIFEST = "--manifest"
private const val OUTPUT = "--output"
private const val CLINICIANS = "--clinicians"
private const val ROSTER = "--roster"
private const val CHECKOUT_ROOT = "--checkout-root"
private const val CREDENTIALS_ONLY = "--credentials-only"
private val VALUE_OPTIONS = setOf(MANIFEST, OUTPUT, CLINICIANS, ROSTER, CHECKOUT_ROOT)
