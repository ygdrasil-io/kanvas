plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
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
