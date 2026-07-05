import java.net.URL

plugins {
    id("buildsrc.convention.kotlin-jvm")
    // G7.2 — Dokka GFM doc generation for :kanvas-skia (foundation
    // types). Mirrors the :math setup (Dokka 2.2.0 V1 + gfm-plugin).
    // The docs pipeline (Dokka GFM → post-process → MkDocs Material)
    // lives in `.github/workflows/docs.yml` and `docs/scripts/`.
    id("org.jetbrains.dokka") version "2.2.0"
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":render-pipeline"))
    implementation(project(":font:core"))
    implementation(project(":font:sfnt"))
    implementation(project(":font:text"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")

    // :kanvas-skia/src/main has NO dependency on :cpu-raster (architecture
    // goal: core/abstractions module). But :kanvas-skia/src/test references
    // classes that live in :cpu-raster (e.g. ToolUtils, GMs used by
    // diagnostic tests). testImplementation only affects the test classpath ;
    // the main JAR stays raster-free.
    testImplementation(project(":cpu-raster"))
    testImplementation(project(":codec:core"))
    testImplementation(project(":codec:png"))
    testImplementation(project(":codec:image-generator"))
    testImplementation(project(":codec:webp"))
    testImplementation(project(":integration-tests:skia"))
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

    // G7.2 — GFM (GitHub-Flavored Markdown) renderer scoped to
    // `:kanvas-skia:dokkaGfm` only. Same approach as `:math`.
    dokkaGfmPlugin("org.jetbrains.dokka:gfm-plugin:2.2.0")
}

sourceSets {
    test {
        // Skia GM and image fixtures are owned by :integration-tests:skia.
        resources.srcDir("../integration-tests/skia/src/test/resources")
        // Encoder tests reuse the small redistributable real-image corpus
        // from the codec validation module to prove encode paths against
        // decoded real fixtures rather than synthetic-only bitmaps.
        resources.srcDir("../codec/real-image-tests/src/test/resources")
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

// G7.2 — Dokka GFM config. See :math/build.gradle.kts for the
// reference setup ; the post-processor in
// `docs/scripts/postprocess_dokka_gfm.py` is module-agnostic and works
// on whatever directory is passed to it (`docs/api/kanvas-skia` for
// this module).
tasks.dokkaGfm {
    moduleName.set("kanvas-skia")
    dokkaSourceSets.named("main") {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URL("https://github.com/ygdrasil-io/kanvas/blob/master/kanvas-skia/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}
