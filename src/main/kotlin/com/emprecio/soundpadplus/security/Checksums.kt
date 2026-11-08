package com.emprecio.soundpadplus.security

import java.io.File
import java.security.MessageDigest

object Checksums {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65_536)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Deletes [file] and throws unless its SHA-256 is [expected]. */
    fun verify(file: File, expected: String) {
        if (sha256(file) != expected.trim().lowercase()) {
            file.delete()
            throw SecurityException("SHA-256 mismatch for ${file.name}")
        }
    }

    /** Hash of [name] in a `sha256sum`-style listing (`<hash>  <name>` or `<hash> *<name>`); with a null name, the first line. */
    fun parse(listing: String, name: String? = null): String =
        listing.lines().map { it.trim().split(Regex("\\s+")) }
            .first { it[0].matches(Regex("[0-9a-fA-F]{64}")) && (name == null || it.getOrNull(1)?.removePrefix("*") == name) }[0]
}
