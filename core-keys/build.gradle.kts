import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM module. No Android dependency: which key sits where, and which
// presses and releases are sent for a sequence of touches, are decided here so
// a plain JVM test can walk every row and every modifier sequence.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Match the Android module's bytecode level so :app can consume this module.
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
