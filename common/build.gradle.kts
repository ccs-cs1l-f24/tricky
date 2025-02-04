import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.serialization)
            }
        }
    }
}
