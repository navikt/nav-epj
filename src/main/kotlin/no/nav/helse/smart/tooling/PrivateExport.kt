package no.nav.helse.smart.tooling

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/** An operator-facing failure whose message is safe to print: it never contains secret values. */
internal class CredentialToolException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

internal inline fun ensure(condition: Boolean, message: () -> String) {
    if (!condition) throw CredentialToolException(message())
}

private val OWNER_ONLY_DIRECTORY = PosixFilePermissions.fromString("rwx------")
private val OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------")
private val SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

/**
 * A new, owner-only directory tree for secret material. The root must not exist, must live outside
 * every forbidden root (after resolving symlinks), and is created with mode 700; files are created
 * with mode 600 and never overwrite or follow links. [rollback] removes only what this instance
 * created.
 */
internal class PrivateExport private constructor(private val root: Path) {
    private val created = mutableListOf(root)
    private val directories = mutableSetOf(root)

    fun createDirectory(relative: String) {
        val path = resolve(relative)
        require(path.parent in directories) {
            "export directory '$relative' needs an existing parent created by this export"
        }
        attempt(relative) {
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY))
            created.add(path)
            directories.add(path)
            requirePermissions(path, OWNER_ONLY_DIRECTORY)
        }
    }

    fun writeFile(relative: String, content: ByteArray) {
        val path = resolve(relative)
        require(path.parent in directories) {
            "export file '$relative' needs a parent directory created by this export"
        }
        attempt(relative) {
            val options =
                setOf(
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS,
                )
            val attribute = PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE)
            Files.newByteChannel(path, options, attribute).use { channel ->
                created.add(path)
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) channel.write(buffer)
            }
            requirePermissions(path, OWNER_ONLY_FILE)
        }
    }

    fun rollback() {
        for (path in created.asReversed()) {
            try {
                Files.deleteIfExists(path)
            } catch (_: IOException) {
                continue
            }
        }
        created.clear()
    }

    private fun resolve(relative: String): Path {
        val segments = relative.split('/')
        require(segments.all { SEGMENT.matches(it) }) { "invalid export path '$relative'" }
        return segments.fold(root) { path, segment -> path.resolve(segment) }
    }

    private fun attempt(relative: String, action: () -> Unit) {
        try {
            action()
        } catch (e: IOException) {
            rollback()
            throw CredentialToolException(
                "could not write '$relative' to the output directory (${e.javaClass.simpleName}); " +
                    "files created by this run were removed",
                e,
            )
        }
    }

    private fun requirePermissions(path: Path, expected: Set<PosixFilePermission>) {
        if (Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS) != expected) {
            rollback()
            throw CredentialToolException(
                "output path did not get owner-only permissions; files created by this run were removed"
            )
        }
    }

    companion object {
        fun validateTarget(target: Path, forbiddenRoots: List<Path>): Path {
            ensure("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
                "this file system does not support POSIX permissions; run on macOS or Linux"
            }
            val absolute = target.toAbsolutePath()
            ensure(absolute.none { it.toString() == ".." }) {
                "output path must not contain '..' segments"
            }
            val resolved = resolveNewChild(absolute.normalize())
            ensure(forbiddenRoots.none { isInside(resolved, it) }) {
                "output path resolves inside the source checkout; choose a directory outside it"
            }
            ensure(!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
                "output path already exists; refusing to overwrite, choose a new path"
            }
            return resolved
        }

        private fun resolveNewChild(normalized: Path): Path {
            val name = normalized.fileName
            val parent = normalized.parent
            if (name == null || parent == null || !Files.isDirectory(parent)) {
                throw CredentialToolException(
                    "output path must name a new directory inside an existing parent directory"
                )
            }
            return parent.toRealPath().resolve(name.toString())
        }

        fun create(target: Path, forbiddenRoots: List<Path>): PrivateExport {
            val resolved = validateTarget(target, forbiddenRoots)
            try {
                Files.createDirectory(
                    resolved,
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY),
                )
            } catch (e: IOException) {
                throw CredentialToolException(
                    "could not create the output directory (${e.javaClass.simpleName})",
                    e,
                )
            }
            val export = PrivateExport(resolved)
            export.requirePermissions(resolved, OWNER_ONLY_DIRECTORY)
            return export
        }

        private fun isInside(path: Path, forbiddenRoot: Path): Boolean {
            val real =
                try {
                    forbiddenRoot.toRealPath()
                } catch (_: IOException) {
                    return false
                }
            return path.startsWith(real)
        }
    }
}
