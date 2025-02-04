import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class) wasmJs {
        moduleName = "tricky"
        browser {
            commonWebpackConfig {
                outputFileName = "tricky.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":common"))
                implementation(compose.material3)
                implementation(libs.ktor.websockets.client)
                implementation(libs.ktor.content.negotiation.client)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.js.client)
            }
        }
    }
}
