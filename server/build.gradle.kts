plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization")
}

group = "dev.edwinchang"
version = "0.0.1"

application {
    mainClass = "MainKt"
}

dependencies {
    implementation(project(":common"))
    implementation(libs.ktor.server)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.websockets.server)
    implementation(libs.ktor.content.negotiation.server)
    implementation(libs.ktor.serialization)
    implementation(libs.logback)
}
