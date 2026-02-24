plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlinDslPluginOptions {
    jvmTarget.set("17")
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("runanywhereNativeLibraryDownload") {
            id = "runanywhere.native-library-download"
            implementationClass = "NativeLibraryDownloadPlugin"
        }
    }
}
