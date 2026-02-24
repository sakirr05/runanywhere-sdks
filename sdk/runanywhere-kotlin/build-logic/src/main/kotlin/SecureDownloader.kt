import org.gradle.api.logging.Logger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Secure downloader with timeout support and retry logic
 */
class SecureDownloader(
    private val logger: Logger,
    private val checksumVerifier: ChecksumVerifier = ChecksumVerifier()
) {

    /**
     * Downloads a file with timeout and retry support
     */
    fun downloadFile(
        url: String,
        destinationFile: File,
        connectTimeout: Int = 30_000,
        readTimeout: Int = 120_000,
        retryAttempts: Int = 3
    ): DownloadResult {

        var lastException: Exception? = null

        repeat(retryAttempts) { attempt ->
            try {
                logger.info("Downloading: $url (attempt ${attempt + 1}/$retryAttempts)")

                val connection = createConnection(url, connectTimeout, readTimeout)
                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Ensure parent directory exists
                    destinationFile.parentFile?.mkdirs()

                    connection.inputStream.use { input ->
                        destinationFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var totalBytes = 0L
                            var bytesRead: Int

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                            }

                            logger.info("Downloaded $totalBytes bytes to ${destinationFile.name}")
                            return DownloadResult.Success(destinationFile, totalBytes)
                        }
                    }
                } else {
                    throw IOException("HTTP error $responseCode for URL: $url")
                }

            } catch (e: SocketTimeoutException) {
                lastException = e
                logger.warn("Download timeout for $url (attempt ${attempt + 1}/$retryAttempts): ${e.message}")

                if (attempt < retryAttempts - 1) {
                    val delayMs = (attempt + 1) * 1000L // Exponential backoff
                    logger.info("Retrying in ${delayMs}ms...")
                    Thread.sleep(delayMs)
                }

            } catch (e: FileNotFoundException) {
                // Don't retry on 404 errors
                logger.error("File not found: $url")
                return DownloadResult.NotFound(url)

            } catch (e: IOException) {
                lastException = e
                logger.warn("Download failed for $url (attempt ${attempt + 1}/$retryAttempts): ${e.message}")

                if (attempt < retryAttempts - 1) {
                    val delayMs = (attempt + 1) * 1000L
                    logger.info("Retrying in ${delayMs}ms...")
                    Thread.sleep(delayMs)
                }
            }
        }

        return DownloadResult.Failed(url, lastException ?: IOException("Unknown download error"))
    }

    /**
     * Downloads a file with checksum verification
     */
    fun downloadFileWithChecksum(
        fileUrl: String,
        checksumUrl: String,
        destinationFile: File,
        connectTimeout: Int = 30_000,
        readTimeout: Int = 120_000,
        retryAttempts: Int = 3
    ): DownloadResult {

        val tempChecksumFile = File(destinationFile.parentFile, "${destinationFile.name}.sha256.tmp")

        try {
            // Step 1: Download checksum file
            logger.info("Downloading checksum: $checksumUrl")
            val checksumResult = downloadFile(checksumUrl, tempChecksumFile, connectTimeout, readTimeout, retryAttempts)

            if (checksumResult !is DownloadResult.Success) {
                logger.warn("Failed to download checksum file: $checksumResult")
                // Continue without checksum verification if file is not found
                if (checksumResult is DownloadResult.NotFound) {
                    logger.warn("Checksum file not available, proceeding without verification")
                    return downloadFile(fileUrl, destinationFile, connectTimeout, readTimeout, retryAttempts)
                }
                return checksumResult
            }

            val expectedChecksum = checksumVerifier.parseChecksumFile(tempChecksumFile)
            logger.info("Expected SHA256: $expectedChecksum")

            // Step 2: Download the actual file
            val downloadResult = downloadFile(fileUrl, destinationFile, connectTimeout, readTimeout, retryAttempts)

            if (downloadResult !is DownloadResult.Success) {
                return downloadResult
            }

            // Step 3: Verify checksum
            logger.info("Verifying checksum for ${destinationFile.name}...")
            val isValid = checksumVerifier.verifyChecksum(destinationFile, expectedChecksum)

            return if (isValid) {
                logger.info("✓ Checksum verification passed")
                downloadResult
            } else {
                logger.error("✗ Checksum verification failed for ${destinationFile.name}")
                // Clean up the invalid file
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                DownloadResult.ChecksumMismatch(fileUrl, expectedChecksum)
            }

        } finally {
            // Clean up temporary checksum file
            if (tempChecksumFile.exists()) {
                tempChecksumFile.delete()
            }
        }
    }

    /**
     * Creates an HttpURLConnection with the specified timeouts
     */
    private fun createConnection(url: String, connectTimeout: Int, readTimeout: Int): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "RunAnywhereAI-SDK-Build/1.0")
        return connection
    }
}

/**
 * Sealed class representing the result of a download operation
 */
sealed class DownloadResult {
    data class Success(val file: File, val bytesDownloaded: Long) : DownloadResult()
    data class Failed(val url: String, val exception: Exception) : DownloadResult()
    data class NotFound(val url: String) : DownloadResult()
    data class ChecksumMismatch(val url: String, val expectedChecksum: String) : DownloadResult()
}
