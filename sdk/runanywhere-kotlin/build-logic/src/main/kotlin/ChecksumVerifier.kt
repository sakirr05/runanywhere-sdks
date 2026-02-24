import java.io.File
import java.security.MessageDigest

/**
 * Utility for SHA256 checksum verification of downloaded files
 */
class ChecksumVerifier {

    /**
     * Verifies the SHA256 checksum of a file against an expected checksum string
     */
    fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
        }

        val actualChecksum = calculateSHA256(file)
        val normalizedExpected = expectedChecksum.trim().lowercase()
        val normalizedActual = actualChecksum.lowercase()

        return normalizedExpected == normalizedActual
    }

    /**
     * Calculates the SHA256 checksum of a file
     */
    fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses a checksum from a .sha256 file (format: "checksum filename")
     */
    fun parseChecksumFile(checksumFile: File): String {
        if (!checksumFile.exists()) {
            throw IllegalArgumentException("Checksum file does not exist: ${checksumFile.absolutePath}")
        }

        val content = checksumFile.readText().trim()

        // Handle different checksum file formats:
        // Format 1: "checksum filename"
        // Format 2: "checksum"
        val parts = content.split(Regex("\\s+"))

        return if (parts.isNotEmpty()) {
            parts[0].trim().lowercase()
        } else {
            throw IllegalArgumentException("Invalid checksum file format: ${checksumFile.absolutePath}")
        }
    }
}
