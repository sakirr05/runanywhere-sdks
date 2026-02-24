import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.gradle.api.Project

class NativeLibraryDownloadTaskTest {

    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply("NativeLibraryDownloadPlugin")
    }

    @Test
    fun `should apply plugin successfully`() {
        assertTrue(project.plugins.hasPlugin("NativeLibraryDownloadPlugin"))
    }

    @Test
    fun `should create downloadJniLibs task`() {
        val task = project.tasks.findByName("downloadJniLibs")
        assertNotNull(task)
        assertEquals("runanywhere", task?.group)
        assertTrue(task?.description?.contains("timeout and checksum verification") == true)
    }

    @Test
    fun `should create extension with correct defaults`() {
        val extension = project.extensions.findByType(NativeLibraryDownloadExtension::class.java)
        assertNotNull(extension)

        // Set some test values
        extension?.apply {
            version = "1.0.0"
            packageType = "RACommons-android"
            allowedLibraries = setOf("librac_commons.so")
        }

        val config = extension?.toConfiguration()
        assertNotNull(config)
        assertEquals("1.0.0", config?.version)
        assertEquals("RACommons-android", config?.packageType)
        assertEquals(30_000, config?.connectTimeout) // Default value
        assertEquals(120_000, config?.readTimeout)   // Default value
        assertTrue(config?.enableChecksumVerification == true) // Default value
        assertEquals(3, config?.retryAttempts) // Default value
    }

    @Test
    fun `should configure extension with custom values`() {
        val extension = project.extensions.getByType(NativeLibraryDownloadExtension::class.java)

        extension.apply {
            version = "2.1.0"
            packageType = "RABackendONNX-android"
            allowedLibraries = setOf(
                "librac_backend_onnx.so",
                "libonnxruntime.so"
            )
            targetAbis = listOf("arm64-v8a", "x86_64")
            connectTimeout = 15_000
            readTimeout = 60_000
            enableChecksumVerification = false
            retryAttempts = 5
            testLocal = true
        }

        val config = extension.toConfiguration()

        assertEquals("2.1.0", config.version)
        assertEquals("RABackendONNX-android", config.packageType)
        assertEquals(2, config.allowedLibraries.size)
        assertTrue(config.allowedLibraries.contains("librac_backend_onnx.so"))
        assertTrue(config.allowedLibraries.contains("libonnxruntime.so"))
        assertEquals(2, config.targetAbis.size)
        assertEquals(15_000, config.connectTimeout)
        assertEquals(60_000, config.readTimeout)
        assertFalse(config.enableChecksumVerification)
        assertEquals(5, config.retryAttempts)
    }

    @Test
    fun `should handle task conditional execution`() {
        val task = project.tasks.getByName("downloadJniLibs") as NativeLibraryDownloadTask

        // Test with testLocal=false (should run)
        project.extensions.getByType(NativeLibraryDownloadExtension::class.java).apply {
            version = "1.0.0"
            packageType = "RACommons-android"
            allowedLibraries = setOf("librac_commons.so")
            testLocal = false
        }

        // The task should be created successfully
        assertNotNull(task)
        assertEquals("runanywhere", task.group)
    }

    @Test
    fun `should validate required configuration properties`() {
        val extension = project.extensions.getByType(NativeLibraryDownloadExtension::class.java)

        // Test with minimal required configuration
        extension.apply {
            version = "1.0.0"
            packageType = "RACommons-android"
            allowedLibraries = setOf("librac_commons.so")
        }

        val config = extension.toConfiguration()

        // Verify required properties are set
        assertNotNull(config.version)
        assertNotNull(config.packageType)
        assertNotNull(config.allowedLibraries)
        assertTrue(config.allowedLibraries.isNotEmpty())

        // Verify defaults are applied
        assertTrue(config.targetAbis.isNotEmpty())
        assertTrue(config.connectTimeout > 0)
        assertTrue(config.readTimeout > 0)
        assertTrue(config.retryAttempts > 0)
    }
}
