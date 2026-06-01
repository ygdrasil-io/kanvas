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
    testImplementation(kotlin("test"))
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
        "--mode",
        "smoke",
        "--warmup-frames",
        "0",
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

tasks.register<JavaExec>("runM70KadreNativeDemo") {
    group = "verification"
    description = "Runs the PM-visible M70-M73 Kadre native WebGPU demo and writes reporting-only runtime telemetry."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.kadre.runtime.M69KadreNativeSmokeKt")
    args(
        "--output",
        rootProject.layout.projectDirectory.file("reports/wgsl-pipeline/m70-kadre-native/native-demo.json").asFile.absolutePath,
        "--frames",
        providers.gradleProperty("kadreDemoFrames").orElse("420").get(),
        "--mode",
        "demo",
        "--warmup-frames",
        providers.gradleProperty("kadreDemoWarmupFrames").orElse("120").get(),
        "--scene-contract-id",
        providers.gradleProperty("kadreReplaySceneId").orElse("m73-linear-gradient-rect-replay-v1").get(),
        "--capture-output",
        rootProject.layout.projectDirectory.file("reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png").asFile.absolutePath,
    )
    jvmArgs(buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    })
    outputs.file(rootProject.layout.projectDirectory.file("reports/wgsl-pipeline/m70-kadre-native/native-demo.json"))
    outputs.upToDateWhen { false }
}
