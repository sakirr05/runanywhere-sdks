import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ChecksumVerifierTest {

    private val verifier = ChecksumVerifier()

    @Test
    fun `should calculate correct SHA256 checksum`(@TempDir tempDir: Path) {
        val testFile = File(tempDir.toFile(), "test.txt")
        testFile.writeText("Hello, World!")

        val expectedSha256 = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f"

        val actualChecksum = verifier.calculateSHA256(testFile)
        assertEquals(expectedSha256, actualChecksum.lowercase())
    }

    @Test
    fun `should verify checksum successfully with matching hash`(@TempDir tempDir: Path) {
        val testFile = File(tempDir.toFile(), "test.txt")
        testFile.writeText("Hello, World!")

        val expectedSha256 = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f"

        assertTrue(verifier.verifyChecksum(testFile, expectedSha256))
    }

    @Test
    fun `should fail verification with mismatched hash`(@TempDir tempDir: Path) {
        val testFile = File(tempDir.toFile(), "test.txt")
        testFile.writeText("Hello, World!")

        val wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        assertFalse(verifier.verifyChecksum(testFile, wrongSha256))
    }

    @Test
    fun `should handle case insensitive checksums`(@TempDir tempDir: Path) {
        val testFile = File(tempDir.toFile(), "test.txt")
        testFile.writeText("Hello, World!")

        val uppercaseSha256 = "DFFD6021BB2BD5B0AF676290809EC3A53191DD81C7F70A4B28688A362182986F"

        assertTrue(verifier.verifyChecksum(testFile, uppercaseSha256))
    }

    @Test
    fun `should parse checksum from standard format file`(@TempDir tempDir: Path) {
        val checksumFile = File(tempDir.toFile(), "test.txt.sha256")
        checksumFile.writeText("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f  test.txt\n")

        val checksum = verifier.parseChecksumFile(checksumFile)
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", checksum)
    }

    @Test
    fun `should parse checksum from checksum-only format file`(@TempDir tempDir: Path) {
        val checksumFile = File(tempDir.toFile(), "test.txt.sha256")
        checksumFile.writeText("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f\n")

        val checksum = verifier.parseChecksumFile(checksumFile)
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", checksum)
    }

    @Test
    fun `should throw exception for non-existent file`(@TempDir tempDir: Path) {
        val nonExistentFile = File(tempDir.toFile(), "nonexistent.txt")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            verifier.verifyChecksum(nonExistentFile, "dummy")
        }

        assertTrue(exception.message!!.contains("File does not exist"))
    }
}
