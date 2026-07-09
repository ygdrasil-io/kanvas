plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinxSerialization)

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.register<JavaExec>("generateGpuPhase6ImageFamilyEvidence") {
    group = "verification"
    description = "Generates the GPU Phase 6 IMAGE family classification and evidence report."
    dependsOn(
        ":integration-tests:skia:generateSkiaDashboard",
        ":gpu-renderer:generateGpuPhase6ImageResourceEvidence",
        tasks.named("classes"),
    )
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceCliKt")
    args(rootDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    inputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/resource-evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md"))
}

tasks.register<JavaExec>("generateGpuPhase6CoverageFamiliesEvidence") {
    group = "verification"
    description = "Generates the GPU Phase 6 PATH + CLIP coverage family classification and evidence report."
    dependsOn(
        ":integration-tests:skia:generateSkiaDashboard",
        tasks.named("classes"),
    )
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6CoverageFamiliesEvidenceCliKt")
    args(rootDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-coverage-families/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-coverage-families/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-09-gpu-phase-6-coverage-families.md"))
}

tasks.register<JavaExec>("generateGpuPhase6MaterialFamiliesEvidence") {
    group = "verification"
    description = "Generates the GPU Phase 6 GRADIENT + RUNTIME_EFFECT + COLOR material family classification and evidence report."
    dependsOn(
        ":integration-tests:skia:generateSkiaDashboard",
        tasks.named("classes"),
    )
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6MaterialFamiliesEvidenceCliKt")
    args(rootDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-material-families/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-material-families/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md"))
}
