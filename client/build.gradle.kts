import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
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
        val commonMain by getting {
            dependencies {
                implementation(compose.material3)
            }
        }
    }
}
