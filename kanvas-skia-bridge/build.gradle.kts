plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
    implementation(project(":kanvas"))
    implementation(project(":gpu-renderer"))
    implementation(project(":gpu-raster"))

    testImplementation(project(":cpu-raster"))
    testImplementation(project(":skia-integration-tests"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("verifyBridgeSkSurfaceRender") {
    group = "verification"
    description = "Verifies that SkiaKanvasSurface.flush() renders into the wrapped SkSurface."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.kanvas.VerifyBridgeSkSurfaceRenderKt")
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("compareBridgeVsSkiaRaster") {
    group = "verification"
    description = "Compares bridge GPU output vs Skia software raster for rect/rrect scenes."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.kanvas.CompareBridgeVsSkiaRasterKt")
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("compareBridgeVsLegacyGpuRaster") {
    group = "verification"
    description = "Compares bridge GPU output vs legacy SkWebGpuDevice for rect/rrect/path scenes."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.kanvas.CompareBridgeVsLegacyGpuRasterKt")
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
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
