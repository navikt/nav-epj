package no.nav.helse.hackathon.credentials

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess
import no.nav.helse.smart.security.RawClientRegistration

private const val USAGE =
    "usage: generateHackathonCredentials --manifest=FILE --output=NEW_DIRECTORY " +
        "(--clinicians=FILE --roster=FILE | --credentials-only) " +
        "[--existing-registry=FILE [--rotate]] [--checkout-root=DIRECTORY]"

internal class CliOptions(
    val manifest: Path,
    val output: Path,
    val clinicians: Path?,
    val roster: Path?,
    val credentialsOnly: Boolean,
    val existingRegistry: Path?,
    val rotate: Boolean,
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
    val flags = mutableSetOf<String>()
    args.forEachIndexed { index, arg ->
        val name = arg.substringBefore('=')
        ensure(name in VALUE_OPTIONS || arg in FLAGS) {
            "unknown argument at position ${index + 1}; $USAGE"
        }
        ensure(arg in FLAGS || ('=' in arg && name !in values)) {
            "argument at position ${index + 1} must be given once as --name=value; $USAGE"
        }
        if (arg in FLAGS) flags.add(arg) else values[name] = arg.substringAfter('=')
    }
    val credentialsOnly = CREDENTIALS_ONLY in flags
    val rotate = ROTATE in flags
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
    ensure(!rotate || EXISTING_REGISTRY in values) { "--rotate needs --existing-registry; $USAGE" }
    return CliOptions(
        manifest = Paths.get(manifest!!),
        output = Paths.get(output!!),
        clinicians = clinicians?.let(Paths::get),
        roster = roster?.let(Paths::get),
        credentialsOnly = credentialsOnly,
        existingRegistry = values[EXISTING_REGISTRY]?.let(Paths::get),
        rotate = rotate,
        checkoutRoot = Paths.get(values[CHECKOUT_ROOT] ?: "").toAbsolutePath(),
    )
}

private fun generateCredentials(options: CliOptions, out: PrintStream) {
    val manifest = readManifest(options.manifest)
    val inputs = readExternalInputs(options, manifest)
    val forbiddenRoots = checkoutRoots(options.checkoutRoot)
    PrivateExport.validateTarget(options.output, forbiddenRoots)
    val kept = keptRegistrations(options, manifest)

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
    val registrations = kept + generated.registrations
    validateRegistrations(registrations, "resulting registry")

    val export = PrivateExport.create(options.output, forbiddenRoots)
    try {
        export.writeFile("registry.json", registryJson(registrations))
        writeTeamFiles(export, manifest, generated, inputs)
    } catch (e: Exception) {
        export.rollback()
        throw e
    }
    val verb = if (options.rotate) "Rotated" else "Wrote"
    out.println("$verb credentials for ${generated.teams.size} team(s) to ${options.output}")
    if (options.existingRegistry != null) {
        out.println(
            "registry.json keeps ${kept.size} existing registration(s) unchanged " +
                "and adds ${generated.registrations.size}."
        )
    }
    out.println(
        if (inputs == null) {
            "Packets are CREDENTIALS_ONLY: clinician login and roster are still missing."
        } else {
            "Packets are COMPLETE."
        }
    )
}

private fun keptRegistrations(
    options: CliOptions,
    manifest: StarterManifest,
): List<RawClientRegistration> {
    val path = options.existingRegistry ?: return emptyList()
    return registrationsToKeep(
        readExistingRegistry(path),
        manifest.teams.map { it.teamSlot }.toSet(),
        options.rotate,
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
private const val EXISTING_REGISTRY = "--existing-registry"
private const val CREDENTIALS_ONLY = "--credentials-only"
private const val ROTATE = "--rotate"
private val VALUE_OPTIONS =
    setOf(MANIFEST, OUTPUT, CLINICIANS, ROSTER, CHECKOUT_ROOT, EXISTING_REGISTRY)
private val FLAGS = setOf(CREDENTIALS_ONLY, ROTATE)
