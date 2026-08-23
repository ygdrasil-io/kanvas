plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

val sourceCommit = providers.gradleProperty("sourceCommit")
val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":gpu-renderer"))
    implementation(project(":integration-tests:test-utils"))
    implementation(libs.kotlinxSerialization)
    runtimeOnly(project(":codec:png"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<Test> {
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
}

val generateGpuEvidence = tasks.register<JavaExec>("generateGpuEvidence") {
    group = "verification"
    description = "Generates curated GPU evidence through the canonical prepared-session route."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.runner.GpuEvidenceCliKt")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
    doFirst {
        require(sourceCommit.isPresent && sourceCommit.get().matches(Regex("[0-9a-f]{40}"))) { "-PsourceCommit=<40 lowercase hex> is required" }
    }
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", sourceCommit.get())
    })
    outputs.upToDateWhen { false }
}

val warmupFrames = providers.gradleProperty("warmupFrames").orElse("10")
val measuredFrames = providers.gradleProperty("measuredFrames").orElse("90")
tasks.register<JavaExec>("gpuEvidencePerformance") {
    group = "verification"
    description = "Captures independent hardware-eligible GPU performance evidence."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.performance.GpuEvidencePerformanceCliKt")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
    doFirst {
        require(sourceCommit.isPresent && sourceCommit.get().matches(Regex("[0-9a-f]{40}"))) { "-PsourceCommit=<40 lowercase hex> is required" }
        require(warmupFrames.get().toInt() == 10 && measuredFrames.get().toInt() == 90) { "warmupFrames and measuredFrames must be exactly 10 and 90" }
    }
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider { listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", sourceCommit.get(), "--warmup-frames", warmupFrames.get(), "--measured-frames", measuredFrames.get()) })
    outputs.upToDateWhen { false }
}

tasks.register("generateBootstrapGpuEvidence") {
    group = "verification"
    description = "Temporary alias for generateGpuEvidence; removed by Task 8."
    dependsOn(generateGpuEvidence)
}

tasks.register<JavaExec>("verifyGeneratedGpuEvidence") {
    group = "verification"
    description = "Verifies generated GPU evidence without creating a GPU runtime."
    dependsOn(generateGpuEvidence, tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.VerifyEvidenceCliKt")
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        val root = rootProject.layout.projectDirectory
            .dir("reports/gpu-renderer/evidence/correctness/generated/${sourceCommit.get()}")
            .asFile.absolutePath
        listOf("--root", root, "--source-commit", sourceCommit.get())
    })
}

tasks.register<JavaExec>("verifyPromotedGpuEvidence") {
    group = "verification"
    description = "Verifies checked-in promoted GPU evidence headlessly."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.VerifyEvidenceCliKt")
    args(
        "--root",
        rootProject.layout.projectDirectory
            .dir("reports/gpu-renderer/evidence/correctness/promoted")
            .asFile.absolutePath,
        "--allow-historical-commit",
    )
}

tasks.register<JavaExec>("promoteGpuEvidence") {
    group = "verification"
    description = "Promotes independently verified generated GPU evidence with explicit review metadata."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.PromoteEvidenceCliKt")
    val reviewer = providers.gradleProperty("promotionReviewer")
    val reason = providers.gradleProperty("promotionReason")
    doFirst {
        require(sourceCommit.isPresent && sourceCommit.get().matches(Regex("[0-9a-f]{40}"))) { "sourceCommit with 40 hexadecimal characters is required" }
        require(reviewer.isPresent && reviewer.get().isNotBlank()) { "promotionReviewer is required" }
        require(reason.isPresent && reason.get().isNotBlank()) { "promotionReason is required" }
    }
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf(
            "--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath,
            "--source-commit", sourceCommit.get(), "--reviewer", reviewer.get(), "--reason", reason.get(), "--all",
        )
    })
}
