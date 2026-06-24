import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:gpu-api"))
    implementation(project(":font"))

    implementation(kotlin("stdlib"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation(project(":codec-api"))
    implementation(project(":kanvas-skia"))
    compileOnly("org.graphiks:core-jvm:1.0.0-SNAPSHOT")
    compileOnly("org.graphiks:parser-jvm:1.0.0-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("org.graphiks:core-jvm:1.0.0-SNAPSHOT")
    testImplementation("org.graphiks:parser-jvm:1.0.0-SNAPSHOT")
}

tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gpuRendererR6FirstRoutePmEvidenceBundle") {
    group = "verification"
    description = "Writes the validation-owned R6 first-route PM evidence bundle without product route activation."

    val outputDir = layout.buildDirectory.dir("reports/gpu-renderer-r6-first-route-pm-evidence")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.validation.FirstRoutePMEvidenceExportKt")
    outputs.dir(outputDir)
    args(outputDir.get().asFile.absolutePath)

    doFirst {
        outputDir.get().asFile.deleteRecursively()
    }
}

tasks.register<JavaExec>("gpuRendererM9ReadinessPmEvidenceBundle") {
    group = "verification"
    description = "Writes the KGPU-M9-003 GPU renderer readiness PM evidence bundle without moving readiness."

    val outputDir = layout.buildDirectory.dir("reports/gpu-renderer-m9-readiness-pm-evidence")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.telemetry.ReadinessDashboardPMEvidenceExportKt")
    outputs.dir(outputDir)
    args(outputDir.get().asFile.absolutePath)

    doFirst {
        outputDir.get().asFile.deleteRecursively()
    }
}

tasks.register<JavaExec>("gpuRendererWgsl4kEvolutionReportFixtures") {
    group = "verification"
    description = "Writes WGSL4K-EVO-004 reflection and validation report fixtures without route promotion."

    val outputDir = rootProject.layout.projectDirectory.dir("reports/wgsl4k-evolution/generated")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kEvolutionReportFixturesKt")
    outputs.dir(outputDir)
    args(
        outputDir.asFile.absolutePath,
        "72a35b58758f241756d984a84768ae77308730da",
    )

    doFirst {
        outputDir.asFile.deleteRecursively()
    }
}
