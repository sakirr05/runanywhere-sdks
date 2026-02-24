# Build Logic - Secure Native Library Downloads

This module provides shared build logic for downloading native libraries with timeout and checksum verification, addressing GitHub issue #210.

## Overview

The `NativeLibraryDownloadPlugin` replaces the previous `ant.get()` based downloads with a secure implementation that includes:

- ✅ **Connection timeouts** (30s connect, 2min read)
- ✅ **SHA256 checksum verification** for downloaded binaries
- ✅ **Retry logic** with exponential backoff (up to 3 attempts)
- ✅ **Graceful error handling** instead of infinite hangs
- ✅ **Consolidated logic** eliminating code duplication across modules

## Architecture

```
build-logic/
├── build.gradle.kts                    # Build configuration with test dependencies
├── settings.gradle.kts                 # Module settings
├── src/main/kotlin/
│   ├── NativeLibraryDownloadPlugin.kt  # Gradle plugin registration
│   ├── NativeLibraryDownloadTask.kt    # Main download task implementation
│   ├── DownloadConfiguration.kt        # Configuration data class
│   ├── SecureDownloader.kt             # HTTP download with timeouts
│   └── ChecksumVerifier.kt             # SHA256 verification utilities
├── src/test/kotlin/                    # Unit tests for all components
└── README.md                           # This documentation
```

## Usage

### Apply Plugin

Add the plugin to your module's `build.gradle.kts`:

```kotlin
plugins {
    id("NativeLibraryDownloadPlugin")
}
```

### Configure Downloads

Configure the download parameters:

```kotlin
nativeLibraryDownload {
    version = nativeLibVersion
    packageType = "RACommons-android"  // or "RABackendLLAMACPP-android", etc.
    allowedLibraries = setOf(
        "librac_commons.so",
        "librunanywhere_jni.so",
        "libc++_shared.so",
        "libomp.so"
    )
    targetAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    connectTimeout = 30_000      // 30 seconds (default)
    readTimeout = 120_000        // 2 minutes (default)
    enableChecksumVerification = true  // Enable SHA256 verification (default)
    retryAttempts = 3           // Retry failed downloads (default)
    testLocal = testLocal       // Skip downloads when using local libs
}
```

### Run Downloads

The plugin creates a `downloadJniLibs` task:

```bash
# Download libraries for current module
./gradlew downloadJniLibs

# Download with detailed logging
./gradlew downloadJniLibs --info

# Skip downloads (use local libraries)
./gradlew downloadJniLibs -PtestLocal=true
```

## Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `version` | String | **Required** | Native library version (e.g., "2.1.0") |
| `packageType` | String | **Required** | Package prefix (e.g., "RACommons-android") |
| `allowedLibraries` | Set<String> | **Required** | Whitelist of allowed .so files |
| `targetAbis` | List<String> | `["arm64-v8a", "armeabi-v7a", "x86_64"]` | Android ABIs to download |
| `connectTimeout` | Int | `30_000` | Connection timeout in milliseconds |
| `readTimeout` | Int | `120_000` | Read timeout in milliseconds |
| `enableChecksumVerification` | Boolean | `true` | Enable SHA256 checksum verification |
| `retryAttempts` | Int | `3` | Number of retry attempts on failure |
| `baseUrl` | String | GitHub releases URL | Base URL for downloads |
| `testLocal` | Boolean | `false` | Skip downloads when using local libraries |

## Security Features

### Timeout Protection

- **Connect timeout**: 30 seconds to establish connection
- **Read timeout**: 2 minutes for large file downloads
- **No infinite hangs**: Builds fail gracefully on network issues

### Checksum Verification

- Downloads `.sha256` files from GitHub releases
- Verifies SHA256 hash of downloaded files
- **Fails build** on checksum mismatch
- Continues without verification if `.sha256` file is missing (with warning)

### Retry Logic

- Retries failed downloads up to 3 times
- **Exponential backoff**: 1s, 2s, 3s delays between retries
- Doesn't retry on 404 errors (file not found)

## Error Handling

The plugin handles various failure scenarios gracefully:

| Error Type | Behavior | Example |
|------------|----------|---------|
| **Connection timeout** | Retry with exponential backoff | Slow/unresponsive servers |
| **Read timeout** | Retry with exponential backoff | Large files over slow connections |
| **File not found (404)** | Fail immediately | Invalid version/package name |
| **Checksum mismatch** | Fail build with clear error | Corrupted or tampered files |
| **Network unreachable** | Retry then fail | No internet connection |

## Module Integration

The plugin is integrated into all Kotlin SDK modules:

### Main SDK (`sdk/runanywhere-kotlin/`)
```kotlin
nativeLibraryDownload {
    packageType = "RACommons-android"
    allowedLibraries = setOf(
        "librac_commons.so",
        "librunanywhere_jni.so",
        "libc++_shared.so",
        "libomp.so"
    )
}
```

### LlamaCPP Module (`modules/runanywhere-core-llamacpp/`)
```kotlin
nativeLibraryDownload {
    packageType = "RABackendLLAMACPP-android"
    allowedLibraries = setOf(
        "librac_backend_llamacpp.so",
        "librac_backend_llamacpp_jni.so"
    )
}
```

### ONNX Module (`modules/runanywhere-core-onnx/`)
```kotlin
nativeLibraryDownload {
    packageType = "RABackendONNX-android"
    allowedLibraries = setOf(
        "librac_backend_onnx.so",
        "librac_backend_onnx_jni.so",
        "libonnxruntime.so",
        "libsherpa-onnx-c-api.so",
        "libsherpa-onnx-cxx-api.so",
        "libsherpa-onnx-jni.so"
    )
}
```

## Testing

### Unit Tests

Run the test suite:

```bash
cd build-logic/
./gradlew test
```

Test coverage includes:
- SHA256 checksum calculation and verification
- Download configuration validation
- Plugin application and task creation
- Error handling scenarios

### Manual Testing

See `test-download-security.md` for comprehensive manual testing procedures.

## Migration from Old Implementation

### Before (Problematic)

```kotlin
// Old implementation using ant.get() - NO timeouts, NO checksums
ant.withGroovyBuilder {
    "get"("src" to zipUrl, "dest" to tempZip, "verbose" to false)
}
```

**Issues**:
- No connection/read timeouts → builds hang indefinitely
- No checksum verification → security risk
- Code duplication across 3 modules → maintenance burden

### After (Secure)

```kotlin
// New implementation with secure downloader
nativeLibraryDownload {
    version = nativeLibVersion
    packageType = "RACommons-android"
    allowedLibraries = setOf("librac_commons.so", "librunanywhere_jni.so")
    connectTimeout = 30_000
    readTimeout = 120_000
    enableChecksumVerification = true
    retryAttempts = 3
}
```

**Benefits**:
- ✅ Configurable timeouts prevent hanging builds
- ✅ SHA256 verification ensures binary integrity
- ✅ Retry logic handles transient network issues
- ✅ Shared plugin eliminates code duplication
- ✅ Clear error reporting aids debugging

## Implementation Details

### HttpURLConnection Configuration

```kotlin
val connection = URL(url).openConnection() as HttpURLConnection
connection.connectTimeout = connectTimeout  // 30 seconds
connection.readTimeout = readTimeout        // 2 minutes
connection.instanceFollowRedirects = true
connection.setRequestProperty("User-Agent", "RunAnywhereAI-SDK-Build/1.0")
```

### SHA256 Verification Process

1. **Download checksum file**: `package.zip.sha256`
2. **Parse expected hash**: Handle different checksum file formats
3. **Calculate actual hash**: SHA256 of downloaded file
4. **Compare hashes**: Fail build if mismatch detected
5. **Clean up**: Remove invalid files on verification failure

### URL Structure

Downloads follow GitHub releases convention:
```
Base: https://github.com/RunanywhereAI/runanywhere-sdks/releases/download/v{version}/
File: {packageType}-{abi}-v{version}.zip
Checksum: {packageType}-{abi}-v{version}.zip.sha256
```

Example:
```
https://github.com/RunanywhereAI/runanywhere-sdks/releases/download/v2.1.0/RACommons-android-arm64-v8a-v2.1.0.zip
https://github.com/RunanywhereAI/runanywhere-sdks/releases/download/v2.1.0/RACommons-android-arm64-v8a-v2.1.0.zip.sha256
```

## Troubleshooting

### Common Issues

**Build hangs during download**:
- Check network connectivity
- Verify GitHub releases are accessible
- Increase timeout values if needed

**Checksum verification fails**:
- Ensure `.sha256` files exist in GitHub releases
- Verify file integrity wasn't compromised during download
- Check if the expected version exists

**Downloads fail with 404**:
- Verify `nativeLibVersion` matches available GitHub releases
- Check `packageType` naming convention
- Ensure all target ABIs have corresponding packages

### Debug Logging

Enable detailed logging:
```bash
./gradlew downloadJniLibs --info --debug
```

Look for:
- Connection establishment timing
- Download progress and byte counts
- Checksum calculation and verification
- Retry attempts and backoff delays

## Future Enhancements

Potential improvements for future versions:

- **Parallel downloads**: Download multiple ABIs concurrently
- **Progress bars**: Visual download progress indicators
- **Bandwidth limiting**: Respect CI/CD resource constraints
- **Mirror support**: Fallback download sources
- **Caching**: Local caching of downloaded packages
- **Compression**: On-the-fly decompression streaming

## Related Issues

- **GitHub Issue #210**: Add timeout and SHA256 checksum verification for native library downloads
- **PR #206**: Initial code review that identified the security concerns

## Contributing

When modifying the build logic:

1. **Run tests**: Ensure all unit tests pass
2. **Test manually**: Use the manual testing guide
3. **Update documentation**: Keep README and comments current
4. **Consider backwards compatibility**: Maintain existing API surface
5. **Security first**: Don't compromise timeout or verification features
