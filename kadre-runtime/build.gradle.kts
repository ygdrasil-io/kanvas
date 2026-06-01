plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
    implementation("org.graphiks.kadre:kadre:1.0.0")
    implementation("org.graphiks.kadre:kadre-win32:1.0.0")
    implementation("org.graphiks.kadre:kadre-x11:1.0.0")
    implementation("org.graphiks.kadre:kadre-wayland:1.0.0")
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("org.skia.kadre.runtime.M69KadreNativeSmokeKt")
    applicationDefaultJvmArgs = buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    }
}

tasks.register<JavaExec>("runM69KadreNativeSmoke") {
    group = "verification"
    description = "Runs the M69 Kadre native WebGPU smoke and writes native presentation evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.kadre.runtime.M69KadreNativeSmokeKt")
    args(
        "--output",
        rootProject.layout.projectDirectory.file("reports/wgsl-pipeline/m69-kadre-native/native-smoke.json").asFile.absolutePath,
        "--frames",
        "3",
    )
    jvmArgs(buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    })
    outputs.file(rootProject.layout.projectDirectory.file("reports/wgsl-pipeline/m69-kadre-native/native-smoke.json"))
    outputs.upToDateWhen { false }
}
