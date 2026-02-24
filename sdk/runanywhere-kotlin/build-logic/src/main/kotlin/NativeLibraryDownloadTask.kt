import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task for downloading native libraries with secure download and checksum verification
 */
abstract class NativeLibraryDownloadTask : DefaultTask() {

    @get:Input
    abstract val configuration: Property<DownloadConfiguration>

    @get:OutputDirectory
    val outputDir: File = project.file("src/androidMain/jniLibs")

    private val tempDir: File = project.file("${project.layout.buildDirectory.get()}/jni-temp")
    private val nativeLibVersionMarker: File = File(outputDir, ".native_lib_version")

    @TaskAction
    fun downloadLibraries() {
        val config = configuration.get()

        // Skip if using local libraries (testLocal=true)
        val testLocal = project.findProperty("testLocal")?.toString()?.toBoolean() ?: false
        if (testLocal) {
            logger.lifecycle("Skipping JNI download: testLocal=true (using local libs)")
            return
        }

        // Check if libs already exist with correct version
        val existingLibs = if (outputDir.exists()) {
            outputDir.walkTopDown().filter { it.extension == "so" }.count()
        } else 0

        val existingVersion = nativeLibVersionMarker.takeIf { it.exists() }?.readText()?.trim()

        if (existingLibs > 0 && existingVersion == config.version) {
            logger.lifecycle(
                "Skipping JNI download: $existingLibs .so files already in $outputDir " +
                    "(native version v${config.version})"
            )
            return
        }

        if (existingLibs > 0 && existingVersion != config.version) {
            logger.lifecycle(
                "Refreshing JNI libs: found $existingLibs existing .so files " +
                    "with version '${existingVersion ?: "unknown"}', expected '${config.version}'"
            )
        }

        // Clean directories for fresh download
        outputDir.deleteRecursively()
        tempDir.deleteRecursively()
        outputDir.mkdirs()
        tempDir.mkdirs()

        logger.lifecycle("")
        logger.lifecycle("═══════════════════════════════════════════════════════════════")
        logger.lifecycle(" Downloading native libraries with secure downloader")
        logger.lifecycle("═══════════════════════════════════════════════════════════════")
        logger.lifecycle("")
        logger.lifecycle("Package type: ${config.packageType}")
        logger.lifecycle("Native lib version: v${config.version}")
        logger.lifecycle("Target ABIs: ${config.targetAbis.joinToString(", ")}")
        logger.lifecycle("Checksum verification: ${if (config.enableChecksumVerification) "enabled" else "disabled"}")
        logger.lifecycle("Timeout: ${config.connectTimeout / 1000}s connect, ${config.readTimeout / 1000}s read")
        logger.lifecycle("")

        val downloader = SecureDownloader(logger)
        var totalDownloaded = 0
        val errors = mutableListOf<String>()

        // Download for each ABI
        config.targetAbis.forEach { abi ->
            val abiOutputDir = File(outputDir, abi)
            abiOutputDir.mkdirs()

            val packageName = config.packageName(abi)
            val zipUrl = config.packageUrl(abi)
            val checksumUrl = config.checksumUrl(abi)
            val tempZip = File(tempDir, packageName)

            logger.lifecycle("▶ Downloading: $packageName")

            try {
                // Download with or without checksum verification
                val result = if (config.enableChecksumVerification) {
                    downloader.downloadFileWithChecksum(
                        fileUrl = zipUrl,
                        checksumUrl = checksumUrl,
                        destinationFile = tempZip,
                        connectTimeout = config.connectTimeout,
                        readTimeout = config.readTimeout,
                        retryAttempts = config.retryAttempts
                    )
                } else {
                    downloader.downloadFile(
                        url = zipUrl,
                        destinationFile = tempZip,
                        connectTimeout = config.connectTimeout,
                        readTimeout = config.readTimeout,
                        retryAttempts = config.retryAttempts
                    )
                }

                when (result) {
                    is DownloadResult.Success -> {
                        // Extract and filter libraries
                        val libsExtracted = extractAndFilterLibraries(tempZip, abiOutputDir, config.allowedLibraries)
                        totalDownloaded += libsExtracted

                        tempZip.delete()
                        logger.lifecycle("  ✓ Downloaded and extracted $libsExtracted libraries")
                    }

                    is DownloadResult.Failed -> {
                        val errorMsg = "Failed to download $packageName: ${result.exception.message}"
                        logger.warn("  ⚠ $errorMsg")
                        errors.add(errorMsg)
                    }

                    is DownloadResult.NotFound -> {
                        val errorMsg = "Package not found: $packageName"
                        logger.warn("  ⚠ $errorMsg")
                        errors.add(errorMsg)
                    }

                    is DownloadResult.ChecksumMismatch -> {
                        val errorMsg = "Checksum verification failed for $packageName"
                        logger.error("  ✗ $errorMsg")
                        errors.add(errorMsg)
                        throw IllegalStateException("Build failed due to checksum verification failure")
                    }
                }

            } catch (e: Exception) {
                val errorMsg = "Unexpected error downloading $packageName: ${e.message}"
                logger.warn("  ⚠ $errorMsg")
                errors.add(errorMsg)
            }

            logger.lifecycle("")
        }

        // Clean up temp directory
        tempDir.deleteRecursively()

        // Write version marker
        nativeLibVersionMarker.writeText(config.version)

        // Summary
        val totalLibs = outputDir.walkTopDown().filter { it.extension == "so" }.count()
        val abiDirs = outputDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

        logger.lifecycle("═══════════════════════════════════════════════════════════════")
        if (totalLibs > 0) {
            logger.lifecycle("✓ Native libraries ready: $totalLibs .so files")
            logger.lifecycle("  ABIs: ${abiDirs.joinToString(", ")}")
            logger.lifecycle("  Location: $outputDir")
        } else {
            logger.error("✗ No libraries were downloaded successfully")
        }

        if (errors.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Errors encountered:")
            errors.forEach { error ->
                logger.lifecycle("  • $error")
            }
        }

        logger.lifecycle("═══════════════════════════════════════════════════════════════")
    }

    /**
     * Extracts libraries from ZIP and filters by allowed library names
     */
    private fun extractAndFilterLibraries(zipFile: File, outputDir: File, allowedLibraries: Set<String>): Int {
        val extractDir = File(tempDir, "extracted-${zipFile.nameWithoutExtension}")
        extractDir.mkdirs()

        // Extract ZIP using Gradle's built-in support
        project.copy {
            from(project.zipTree(zipFile))
            into(extractDir)
        }

        var libCount = 0

        // Copy filtered .so files
        extractDir.walkTopDown()
            .filter { it.extension == "so" && it.name in allowedLibraries }
            .forEach { soFile ->
                val targetFile = File(outputDir, soFile.name)
                soFile.copyTo(targetFile, overwrite = true)
                logger.lifecycle("    ✓ ${soFile.name}")
                libCount++
            }

        return libCount
    }
}
