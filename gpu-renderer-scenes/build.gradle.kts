plugins {
    id("buildsrc.convention.kotlin-jvm")
}

val mainSourceSet = sourceSets.main.get()
val kadreSourceSet = sourceSets.create("kadre") {
    compileClasspath += mainSourceSet.output + mainSourceSet.compileClasspath
    runtimeClasspath += mainSourceSet.runtimeClasspath
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":gpu-renderer"))
    implementation(project(":font"))
    implementation(project(":kanvas"))
    implementation(libs.wgpu4kToolkit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    "kadreImplementation"("org.graphiks.kadre:kadre:1.0.0")
    "kadreImplementation"("org.graphiks.kadre:kadre-win32:1.0.0")
    "kadreImplementation"("org.graphiks.kadre:kadre-x11:1.0.0")
    "kadreImplementation"("org.graphiks.kadre:kadre-wayland:1.0.0")

    runtimeOnly(project(":codec:png"))

    testImplementation(kotlin("test"))
}

sourceSets {
    main {
        // The scene is a conformance fixture: reuse Skia's checked-in COLRv0 font instead of
        // inventing a synthetic A/B atlas that cannot prove COLR table handling.
        resources.srcDir("../font/scaler/src/test/resources")
    }
}

tasks.register<JavaExec>("gpuRendererScenesCatalogReport") {
    group = "verification"
    description = "Writes the GPU renderer scenes catalog report without WebGPU or Kadre execution."

    val outputDir = rootProject.layout.projectDirectory.dir("reports/gpu-renderer-scenes/catalog")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.reports.GPURendererScenesCatalogReportMainKt")
    args(outputDir.asFile.absolutePath)
    outputs.dir(outputDir)
}

tasks.register<JavaExec>("renderGpuRendererSceneOffscreen") {
    group = "verification"
    description = "Renders one GPU renderer scene through the opt-in WebGPU offscreen runner."

    val sceneId = providers.gradleProperty("sceneId").orElse("solid-card-stack")
    val outputDir = providers.gradleProperty("sceneOutput")
        .map { value -> rootProject.layout.projectDirectory.file(value).asFile }
        .orElse(rootProject.layout.projectDirectory.dir("reports/gpu-renderer-scenes/offscreen").asFile)

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainKt")
    args(sceneId.get(), outputDir.get().absolutePath)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("compareKanvasSurfaceOffscreen") {
    group = "verification"
    description = "Renders a Kanvas Surface scene via GPU and compares against CPU reference."

    val sceneName = providers.gradleProperty("sceneName").orElse("solid-red-rect")
    val outputDir = providers.gradleProperty("sceneOutput")
        .map { value -> rootProject.layout.projectDirectory.file(value).asFile }
        .orElse(rootProject.layout.projectDirectory.dir("reports/kanvas-surface-offscreen/compare").asFile)

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.offscreen.CompareKanvasSurfaceOffscreenMainKt")
    args(outputDir.get().absolutePath, sceneName.get())
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("renderKanvasSurfaceOffscreen") {
    group = "verification"
    description = "Renders a Kanvas Surface scene through the opt-in WebGPU offscreen runner (bridge GPU execution)."

    val sceneName = providers.gradleProperty("sceneName").orElse("solid-red-rect")
    val outputDir = providers.gradleProperty("sceneOutput")
        .map { value -> rootProject.layout.projectDirectory.file(value).asFile }
        .orElse(rootProject.layout.projectDirectory.dir("reports/kanvas-surface-offscreen").asFile)

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderKanvasSurfaceOffscreenMainKt")
    args(outputDir.get().absolutePath, sceneName.get())
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("sampleGpuRendererSceneFrames") {
    group = "verification"
    description = "Samples one GPU renderer scene through repeated WebGPU offscreen render+readback frames."

    val sceneId = providers.gradleProperty("sceneId").orElse("frame-gate-blocker-board")
    val frames = providers.gradleProperty("frames").orElse("60")
    val outputDir = providers.gradleProperty("sceneOutput")
        .map { value -> rootProject.layout.projectDirectory.file(value).asFile }
        .orElse(rootProject.layout.projectDirectory.dir("reports/gpu-renderer-scenes/frame-samples").asFile)

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneFrameSamplesMainKt")
    args(sceneId.get(), frames.get(), outputDir.get().absolutePath)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("runPerFamilyBenchmark") {
    group = "verification"
    description = "Runs the M27 per-family GPU benchmark and writes per-family, pipeline-cache, and frame-gate reports."

    val warmupFrames = providers.gradleProperty("warmupFrames").orElse("10")
    val measuredFrames = providers.gradleProperty("measuredFrames").orElse("90")
    val outputDir = providers.gradleProperty("performanceOutput")
        .map { value -> rootProject.layout.projectDirectory.file(value).asFile }
        .orElse(layout.buildDirectory.dir("reports/performance").map { it.asFile })

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RunPerFamilyBenchmarkMainKt")
    args(outputDir.get().absolutePath, warmupFrames.get(), measuredFrames.get())
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("runGpuRendererSceneKadre") {
    group = "verification"
    description = "Opens one GPU renderer scene in the opt-in Kadre windowed runner."

    val sceneId = providers.gradleProperty("sceneId").orElse("solid-card-stack")
    val frames = providers.gradleProperty("frames").orElse("180")
    val outputFile = providers.gradleProperty("sceneSessionOutput")
        .map { value -> rootProject.layout.projectDirectory.file(value).asFile }
        .orElse(rootProject.layout.projectDirectory.file("reports/gpu-renderer-scenes/windowed/session.json").asFile)

    classpath = kadreSourceSet.runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.scenes.windowed.RunGpuRendererSceneKadreMainKt")
    args(sceneId.get(), frames.get(), outputFile.get().absolutePath)
    outputs.file(outputFile)
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register("kadreFrameLifecycleCheck") {
    group = "verification"
    description = "Opt-in: compiles Kadre and runs prepared-window lifecycle checks; requires external/poc-koreos."
    dependsOn(tasks.named("compileKadreKotlin"))
    dependsOn(project(":gpu-renderer").tasks.named("test"))
    dependsOn(tasks.named("test"))
}
