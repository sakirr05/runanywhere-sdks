/**
 * Configuration for native library downloads
 */
data class DownloadConfiguration(
    val version: String,
    val packageType: String,
    val allowedLibraries: Set<String>,
    val targetAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64"),
    val connectTimeout: Int = 30_000, // 30 seconds
    val readTimeout: Int = 120_000,   // 2 minutes
    val enableChecksumVerification: Boolean = true,
    val retryAttempts: Int = 3,
    val baseUrl: String = "https://github.com/RunanywhereAI/runanywhere-sdks/releases/download"
) {
    val releaseBaseUrl: String
        get() = "$baseUrl/v$version"

    fun packageName(abi: String): String = "$packageType-$abi-v$version.zip"

    fun packageUrl(abi: String): String = "$releaseBaseUrl/${packageName(abi)}"

    fun checksumUrl(abi: String): String = "$releaseBaseUrl/${packageName(abi)}.sha256"
}
