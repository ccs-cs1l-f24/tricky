plugins {
    kotlin("jvm") version libs.versions.kotlin apply false
}

tasks.register("copyClientAssetsDev") {
    group = "custom"
    dependsOn(":client:wasmJsBrowserDevelopmentExecutableDistribution")
    val distDir = project("client").layout.buildDirectory.dir("dist/wasmJs/developmentExecutable")
    val assetsDir = layout.projectDirectory.dir("clientAssets")
    inputs.dir(distDir)
    doLast {
        delete { delete(assetsDir) }
        copy {
            from(distDir)
            into(assetsDir)
        }
    }
}

tasks.register("prod") {
    group = "custom"
    dependsOn(":client:wasmJsBrowserDistribution")
    dependsOn(":server:installDist")
    val clientDist = project("client").layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    val serverDist = project("server").layout.buildDirectory.dir("install/server")
    val dest = layout.buildDirectory.dir("dist")
    inputs.dir(clientDist)
    inputs.dir(serverDist)
    doLast {
        delete { delete(dest) }
        copy {
            from(clientDist)
            into(dest.get().dir("clientAssets"))
        }
        copy {
            from(serverDist)
            into(dest)
        }
    }
}
