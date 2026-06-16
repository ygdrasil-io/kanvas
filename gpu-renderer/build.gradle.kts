plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":font:gpu-api"))

    implementation(kotlin("stdlib"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
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
