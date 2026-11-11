// Pure Kotlin/JVM core module.
//
// Rules, models, content interfaces and the game engine live here so they can
// be unit-tested without Android, Compose, DataStore or any platform types.
// core MUST NOT depend on Android, Context, Compose, Looper, or DataStore.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    // Pin toolchain to JDK 17 for predictability; system JDK 25 is the host.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Lets us use the multiplatform-style `kotlin.test.*` API inside this JVM
    // module; Mappers onto JUnit 4 via the kotlin-test-junit artifact.
    testImplementation(kotlin("test-junit"))
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
