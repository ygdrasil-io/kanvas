import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:gpu-api"))
    implementation(project(":font"))

    implementation(kotlin("stdlib"))
    implementation(libs.wgpu4kToolkit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly(libs.wgslCoreJvm)
    compileOnly(libs.wgslParserJvm)
    testImplementation(kotlin("test"))
    testImplementation(libs.wgslCoreJvm)
    testImplementation(libs.wgslParserJvm)
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

tasks.register<JavaExec>("generateGpuPhase6ImageResourceEvidence") {
    group = "verification"
    description = "Writes Phase 6 IMAGE texture/sampler resource evidence."
    dependsOn("testClasses")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceKt")
    val outputFile =
        rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/resource-evidence.json")
    args(outputFile.asFile.absolutePath)
    outputs.file(outputFile)
}
