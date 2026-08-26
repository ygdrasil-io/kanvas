import org.graphiks.kanvas.build.promotionRebaselineArguments

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

val sourceCommitPattern = Regex("[0-9a-f]{40}")
val sourceCommit = providers.gradleProperty("sourceCommit")
val currentGitHeadSourceCommit = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { value ->
    val normalized = value.trim()
    require(sourceCommitPattern.matches(normalized)) { "current Git HEAD must resolve to 40 lowercase hex characters" }
    normalized
}
val correctnessSourceCommit = sourceCommit.map { value ->
    val normalized = value.trim()
    require(sourceCommitPattern.matches(normalized)) { "-PsourceCommit must be 40 lowercase hexadecimal characters when provided" }
    normalized
}.orElse(currentGitHeadSourceCommit)
val scene = providers.gradleProperty("scene")
val scenesFile = providers.gradleProperty("scenesFile")
val all = providers.gradleProperty("all")
val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()

fun optionalSceneArgument(): List<String> = scene.orNull?.let { listOf("--scene", it) }.orEmpty()

fun selectionArguments(): List<String> {
    val selectedScene = scene.orNull?.trim()?.also { require(it.isNotEmpty()) { "-Pscene must not be blank" } }
    val selectedScenesFile = scenesFile.orNull?.trim()?.also { require(it.isNotEmpty()) { "-PscenesFile must not be blank" } }
    val selectAll = if (all.isPresent) {
        val value = all.orNull.orEmpty()
        if (value.isBlank()) true else value.toBooleanStrictOrNull()
            ?: error("-Pall must be true, false, or blank when provided")
    } else {
        false
    }
    val selectors = listOfNotNull(
        selectedScene?.let { "scene" },
        selectedScenesFile?.let { "scenesFile" },
        selectAll.takeIf { it }?.let { "all" },
    )
    require(selectors.size <= 1) { "at most one of -Pscene, -PscenesFile, or -Pall may be supplied" }
    return when {
        selectedScene != null -> listOf("--scene", selectedScene)
        selectedScenesFile != null -> listOf("--scenes-file", selectedScenesFile)
        selectAll -> listOf("--all")
        else -> listOf("--all")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas"))
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
    description = "Generates GPU correctness evidence for the selected scenes or the full catalogue when no selector is provided."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.runner.GpuEvidenceCliKt")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
    doFirst {
        correctnessSourceCommit.get()
    }
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", correctnessSourceCommit.get()) + selectionArguments()
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
        require(sourceCommit.isPresent && sourceCommit.get().matches(sourceCommitPattern)) { "-PsourceCommit=<40 lowercase hex> is required" }
        require(warmupFrames.get().toInt() == 10 && measuredFrames.get().toInt() == 90) { "warmupFrames and measuredFrames must be exactly 10 and 90" }
    }
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider { listOf("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath, "--source-commit", sourceCommit.get(), "--warmup-frames", warmupFrames.get(), "--measured-frames", measuredFrames.get()) + optionalSceneArgument() })
    outputs.upToDateWhen { false }
}

tasks.register("generateBootstrapGpuEvidence") {
    group = "verification"
    description = "Alias for generateGpuEvidence."
    dependsOn(generateGpuEvidence)
}

tasks.register<JavaExec>("verifyGeneratedGpuEvidence") {
    group = "verification"
    description = "Verifies generated GPU correctness evidence for the selected scenes or the full catalogue when no selector is provided."
    dependsOn(generateGpuEvidence, tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.VerifyEvidenceCliKt")
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        val root = rootProject.layout.projectDirectory
            .dir("reports/gpu-renderer/evidence/correctness/generated/${correctnessSourceCommit.get()}")
            .asFile.absolutePath
        listOf("--root", root, "--source-commit", correctnessSourceCommit.get()) + selectionArguments()
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
        "--all",
    )
}

tasks.register<JavaExec>("migratePromotedGpuEvidenceV1ToV2") {
    group = "verification"
    description = "Mechanically migrates checked-in promoted GPU evidence from v1 scene metadata to the v2 root catalogue layout."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.MigratePromotedEvidenceCliKt")
    val reviewer = providers.gradleProperty("promotionReviewer")
    val reason = providers.gradleProperty("promotionReason")
    doFirst {
        require(reviewer.isPresent && reviewer.get().isNotBlank()) { "promotionReviewer is required" }
        require(reason.isPresent && reason.get().isNotBlank()) { "promotionReason is required" }
    }
    inputs.dir(
        rootProject.layout.projectDirectory
            .dir("reports/gpu-renderer/evidence/correctness/promoted"),
    )
    outputs.dir(
        rootProject.layout.projectDirectory
            .dir("reports/gpu-renderer/evidence/correctness"),
    )
    argumentProviders.add(
        org.gradle.process.CommandLineArgumentProvider {
            listOf(
                "--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath,
                "--reviewer", reviewer.get(),
                "--reason", reason.get(),
            )
        },
    )
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("promoteGpuEvidence") {
    group = "verification"
    description = "Promotes independently verified GPU correctness evidence for the selected scenes or the full catalogue when no selector is provided."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.PromoteEvidenceCliKt")
    val reviewer = providers.gradleProperty("promotionReviewer")
    val reason = providers.gradleProperty("promotionReason")
    val rebaseline = providers.gradleProperty("promotionRebaseline")
    val priorComparison = providers.gradleProperty("promotionPriorComparison")
    val newComparison = providers.gradleProperty("promotionNewComparison")
    doFirst {
        require(sourceCommit.isPresent && sourceCommit.get().matches(sourceCommitPattern)) { "sourceCommit with 40 hexadecimal characters is required" }
        require(reviewer.isPresent && reviewer.get().isNotBlank()) { "promotionReviewer is required" }
        require(reason.isPresent && reason.get().isNotBlank()) { "promotionReason is required" }
    }
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf(
            "--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath,
            "--source-commit", sourceCommit.get(), "--reviewer", reviewer.get(), "--reason", reason.get(),
        ) + promotionRebaselineArguments(rebaseline.orNull, priorComparison.orNull, newComparison.orNull) + selectionArguments()
    })
}
