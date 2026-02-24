# Manual Testing Guide for Secure Download Implementation

This guide provides manual testing steps to verify the new secure download implementation addresses issue #210.

## Test Prerequisites

1. Ensure you have the Kotlin SDK environment set up
2. Have access to internet connection for downloads
3. Optional: Set up a local HTTP server for timeout testing

## Test Cases

### 1. Test Timeout Configuration

**Purpose**: Verify that downloads respect the configured timeouts (30s connect, 2min read)

**Steps**:
```bash
# Navigate to Kotlin SDK
cd sdk/runanywhere-kotlin/

# Test with normal download (should succeed)
./gradlew downloadJniLibs

# Test timeout behavior with slow/unresponsive server
# (This would require modifying the URL to a slow test server)
```

**Expected Results**:
- Normal downloads complete successfully
- Slow/hanging connections timeout after configured intervals
- Build fails gracefully instead of hanging indefinitely

### 2. Test Checksum Verification

**Purpose**: Verify SHA256 checksum validation works correctly

**Test A - Valid Checksum**:
```bash
# Download should succeed with valid checksums
./gradlew downloadJniLibs -PnativeLibVersion=<known_good_version>
```

**Test B - Invalid Checksum** (requires test setup):
```bash
# This test would require creating a test release with intentionally wrong checksums
# Expected: Build should fail with checksum mismatch error
```

**Expected Results**:
- Valid checksums: Download succeeds, libraries extracted
- Invalid checksums: Build fails with clear error message
- Missing checksum files: Warning logged, download continues

### 3. Test Retry Logic

**Purpose**: Verify downloads retry on failure with exponential backoff

**Steps**:
```bash
# Test with intermittent network issues
# (Monitor logs for retry attempts)
./gradlew downloadJniLibs --info
```

**Expected Results**:
- Failed downloads retry up to 3 times
- Exponential backoff delay between retries (1s, 2s, 3s)
- Clear logging of retry attempts

### 4. Test Module-Specific Configuration

**Purpose**: Verify each module downloads correct libraries with proper configuration

**Test Main SDK**:
```bash
cd sdk/runanywhere-kotlin/
./gradlew downloadJniLibs --info
```

**Expected**: Downloads RACommons-android packages with libraries:
- librac_commons.so
- librunanywhere_jni.so
- libc++_shared.so
- libomp.so

**Test LlamaCPP Module**:
```bash
cd sdk/runanywhere-kotlin/modules/runanywhere-core-llamacpp/
./gradlew downloadJniLibs --info
```

**Expected**: Downloads RABackendLLAMACPP-android packages with libraries:
- librac_backend_llamacpp.so
- librac_backend_llamacpp_jni.so

**Test ONNX Module**:
```bash
cd sdk/runanywhere-kotlin/modules/runanywhere-core-onnx/
./gradlew downloadJniLibs --info
```

**Expected**: Downloads RABackendONNX-android packages with libraries:
- librac_backend_onnx.so
- librac_backend_onnx_jni.so
- libonnxruntime.so
- libsherpa-onnx-c-api.so
- libsherpa-onnx-cxx-api.so
- libsherpa-onnx-jni.so

### 5. Test Error Handling

**Purpose**: Verify graceful error handling for various failure scenarios

**Test A - Network Unreachable**:
```bash
# Disconnect internet, run download
./gradlew downloadJniLibs
```

**Test B - File Not Found (404)**:
```bash
# Test with non-existent version
./gradlew downloadJniLibs -PnativeLibVersion=99.99.99
```

**Test C - Insufficient Disk Space** (optional):
```bash
# Test on system with limited disk space
./gradlew downloadJniLibs
```

**Expected Results**:
- Clear error messages for each failure type
- Build fails gracefully without hanging
- No partial/corrupted downloads left behind

### 6. Test Legacy Compatibility

**Purpose**: Verify existing build processes continue to work

**Steps**:
```bash
# Test full build process
./gradlew build

# Test with testLocal=true (should skip downloads)
./gradlew downloadJniLibs -PtestLocal=true

# Test existing task dependencies
./gradlew preBuild
```

**Expected Results**:
- All existing build tasks work unchanged
- testLocal=true properly skips downloads
- Task dependencies preserved

## Performance Testing

### 7. Test Download Performance

**Purpose**: Verify downloads are reasonably fast and efficient

**Steps**:
```bash
# Clean download (no cached files)
rm -rf src/androidMain/jniLibs/
./gradlew downloadJniLibs --info | tee download-timing.log

# Check timing logs
grep -E "(Downloaded|Downloading)" download-timing.log
```

**Expected Results**:
- Downloads complete in reasonable time (< 5 minutes for all modules)
- Progress logging shows active download status
- No excessive memory usage during large file downloads

## Verification Checklist

After running the tests, verify:

- [ ] **Timeouts work**: Downloads don't hang indefinitely
- [ ] **Checksums verified**: Invalid checksums cause build failure
- [ ] **Retries function**: Failed downloads retry with backoff
- [ ] **Errors handled**: Clear error messages for all failure types
- [ ] **Libraries filtered**: Only allowed libraries are extracted
- [ ] **Versions respected**: Version markers prevent unnecessary re-downloads
- [ ] **Modules configured**: Each module downloads its specific libraries
- [ ] **Legacy compatibility**: Existing build processes unchanged

## Troubleshooting

If tests fail, check:

1. **Network connectivity**: Can you reach github.com?
2. **Permissions**: Write access to `src/androidMain/jniLibs/`?
3. **Disk space**: Sufficient space for downloaded files?
4. **Version availability**: Does the specified nativeLibVersion exist in GitHub releases?
5. **Build-logic compilation**: Did the build-logic module compile successfully?

## Integration with CI/CD

The secure downloader should improve CI/CD reliability by:

- **Preventing hanging builds** due to network timeouts
- **Failing fast** on checksum mismatches instead of subtle corruption
- **Providing clear logs** for debugging download issues
- **Retrying transient failures** automatically

Monitor CI logs for:
- Download timing improvements
- Reduced build failures due to network issues
- Clear error reporting when downloads fail
