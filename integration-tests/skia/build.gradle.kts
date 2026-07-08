plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas"))
    implementation(project(":codec"))
    implementation(project(":integration-tests:test-utils"))
    implementation(project(":integration-tests:diagnostic"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("generateSkiaRenders") {
    group = "verification"
    description = "Generates Kanvas render PNGs for all Skia GMs. Use -Pgm.includeBlocking=true to include RenderCost.BLOCKING rows."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.SkiaRenderGeneratorKt")
    val outputDir = layout.projectDirectory.dir("src/test/resources/generated-renders")
    val gmIncludeBlocking = project.findProperty("gm.includeBlocking")?.toString()?.toBoolean() ?: false
    args(outputDir.asFile.absolutePath)
    if (gmIncludeBlocking) {
        args("--include-blocking")
    }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        val maxPathVertices = project.findProperty("kanvas.render.maxPathVertices")?.toString()
        if (maxPathVertices != null) {
            add("-Dkanvas.render.maxPathVertices=$maxPathVertices")
        }
    })
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("generateSkiaRendersFor") {
    group = "verification"
    description = "Generates Kanvas render PNGs for a subset of GMs. Use -Pgm.includeBlocking=true to include RenderCost.BLOCKING rows."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.SkiaRenderGeneratorKt")
    val outputDir = layout.projectDirectory.dir("src/test/resources/generated-renders")
    val gmFamily = project.findProperty("gm.family")?.toString()
    val gmName = project.findProperty("gm.name")?.toString()
    val gmIncludeBlocking = project.findProperty("gm.includeBlocking")?.toString()?.toBoolean() ?: false
    val renderArgs = mutableListOf(outputDir.asFile.absolutePath)
    if (gmFamily != null) { renderArgs.add("--family"); renderArgs.add(gmFamily) }
    if (gmName != null) { renderArgs.add("--name"); renderArgs.add(gmName) }
    if (gmIncludeBlocking) { renderArgs.add("--include-blocking") }
    args(renderArgs)
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        val maxPathVertices = project.findProperty("kanvas.render.maxPathVertices")?.toString()
        if (maxPathVertices != null) {
            add("-Dkanvas.render.maxPathVertices=$maxPathVertices")
        }
    })
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("generateSkiaScan") {
    group = "verification"
    description = "Scans Skia GMs with per-GM timeout, saves results to a file."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.SkiaGmScannerKt")
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
    project.findProperty("kanvas.scan.from")?.let { args("--from", it.toString()) }
    project.findProperty("kanvas.scan.to")?.let { args("--to", it.toString()) }
    project.findProperty("kanvas.scan.timeout")?.let { args("--timeout", it.toString()) }
    project.findProperty("kanvas.scan.output")?.let { args("--output", it.toString()) }
}

tasks.register<JavaExec>("generateSkiaDashboard") {
    group = "verification"
    description = "Generates Skia GM visual comparison dashboard."
    dependsOn(tasks.named("generateSkiaRenders"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.SkiaDashboardGeneratorKt")
    val refDir = layout.projectDirectory.dir("src/test/resources/reference")
    val genDir = layout.projectDirectory.dir("src/test/resources/generated-renders")
    val scoresFile = layout.projectDirectory.file("test-similarity-scores.properties")
    val outputDir = layout.buildDirectory.dir("reports/skia-gm-dashboard")
    args(
        "--ref-dir", refDir.asFile.absolutePath,
        "--gen-dir", genDir.asFile.absolutePath,
        "--scores", scoresFile.asFile.absolutePath,
        "--output-dir", outputDir.get().asFile.absolutePath,
    )
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
}
