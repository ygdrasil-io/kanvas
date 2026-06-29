plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas"))
    implementation(project(":kanvas:svg"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    test {
        resources.srcDir("src/main/resources")
        resources.srcDir("src/test/resources")
    }
}

tasks.named<ProcessResources>("processTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "-Djdk.xml.maxParameterEntitySizeLimit=0",
    )
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("generateSvgRenders") {
    group = "verification"
    description = "Generates Kanvas render PNGs for all SVG test inputs."

    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.graphiks.kanvas.svg.SvgRenderGeneratorKt")

    val svgInputDir = layout.projectDirectory.dir("src/main/resources/by-render-family")
    val renderOutputDir = layout.projectDirectory.dir("src/test/resources/generated-renders")

    args(svgInputDir.asFile.absolutePath, renderOutputDir.asFile.absolutePath)

    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })

    outputs.dir(renderOutputDir)
    outputs.upToDateWhen { false }
}
