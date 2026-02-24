import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Gradle plugin for downloading native libraries with timeout and checksum verification
 */
class NativeLibraryDownloadPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Create extension for configuration
        val extension = project.extensions.create("nativeLibraryDownload", NativeLibraryDownloadExtension::class.java)

        // Register the download task
        project.tasks.register("downloadJniLibs", NativeLibraryDownloadTask::class.java) {
            group = "runanywhere"
            description = "Download native libraries with timeout and checksum verification"
            configuration.set(extension.toConfiguration())
        }
    }
}

/**
 * Extension for configuring native library downloads
 */
open class NativeLibraryDownloadExtension {
    var version: String = ""
    var packageType: String = ""
    var allowedLibraries: Set<String> = emptySet()
    var targetAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    var connectTimeout: Int = 30_000 // 30 seconds
    var readTimeout: Int = 120_000   // 2 minutes
    var enableChecksumVerification: Boolean = true
    var retryAttempts: Int = 3
    var baseUrl: String = "https://github.com/RunanywhereAI/runanywhere-sdks/releases/download"
    var testLocal: Boolean = false

    fun toConfiguration(): DownloadConfiguration {
        return DownloadConfiguration(
            version = version,
            packageType = packageType,
            allowedLibraries = allowedLibraries,
            targetAbis = targetAbis,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            enableChecksumVerification = enableChecksumVerification,
            retryAttempts = retryAttempts,
            baseUrl = baseUrl
        )
    }
}
