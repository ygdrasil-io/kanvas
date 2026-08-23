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
    val commit = sourceCommit.orNull
    doFirst {
        require(commit != null && commit.matches(Regex("[0-9a-f]{40}"))) { "-PsourceCommit=<40 lowercase hex> is required" }
    }
    setArgs(listOf("--repository-root", rootDir.absolutePath, "--source-commit", commit ?: ""))
}

tasks.register("generateBootstrapGpuEvidence") {
    group = "verification"
    description = "Temporary alias for generateGpuEvidence; removed by Task 8."
    dependsOn(generateGpuEvidence)
}
