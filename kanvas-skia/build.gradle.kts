plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")

    // :kanvas-skia/src/main has NO dependency on :cpu-raster (architecture
    // goal: core/abstractions module). But :kanvas-skia/src/test references
    // classes that live in :cpu-raster (e.g. ToolUtils, GMs used by
    // diagnostic tests). testImplementation only affects the test classpath ;
    // the main JAR stays raster-free.
    testImplementation(project(":cpu-raster"))
    testImplementation(project(":skia-integration-tests"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    // G0 (MIGRATION_PLAN_GPU_WEBGPU.md) — wgpu4k's mapAsync /
    // requestDevice are `suspend` functions. The toolkit ships
    // kotlinx-coroutines transitively (runtime), but its
    // `implementation` scope hides it from consumers' compile
    // classpath, so the GPU tests must declare the API dependency
    // themselves to call `runBlocking`.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // D1.4 — PathOps regression harness loads upstream Skia
    // fixtures from a JSON resource. jackson-databind is the
    // standard mature JSON parser ; only the harness imports it.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

sourceSets {
    test {
        // kanvas-legacy is excluded from the build (see settings.gradle.kts)
        // but its src/test/resources/{images,original-888} are still required
        // at runtime for kanvas-skia's GMs and DM harness.
        resources.srcDir("../kanvas-legacy/src/test/resources")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}


// MIGRATION_PLAN_GPU_WEBGPU.md Phase G0 — GLFW (used by wgpu4k for
// surface creation, see ClearRedTest / WebGpuContext) requires the
// AppKit main thread on macOS. `-XstartOnFirstThread` lets the JVM
// main thread BE thread 0; JUnit's default executor then runs each
// test method on that thread, satisfying the constraint. Linux/Windows
// have no equivalent — this flag is macOS-only and a no-op elsewhere.
//
// `--add-opens java.base/java.lang=ALL-UNNAMED` is required because
// Rococoa (transitive dep of wgpu4k-toolkit) uses CGLib to generate
// dynamic Objective-C proxy classes, which calls
// `ClassLoader.defineClass` reflectively. Strong encapsulation in
// JVM 17+ blocks this without the explicit `--add-opens`.
//
// `--enable-native-access=ALL-UNNAMED` silences the warning from
// `LibraryLoader.load()` calling `System.loadLibrary`; in a future
// JVM release these calls will be blocked outright without it.
tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}
