plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas"))
    implementation(project(":integration-tests:test-utils"))
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
    description = "Generates Kanvas render PNGs for all Skia GMs."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.SkiaRenderGeneratorKt")
    val outputDir = layout.projectDirectory.dir("src/test/resources/generated-renders")
    args(outputDir.asFile.absolutePath)
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
