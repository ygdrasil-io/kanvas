// :gpu-raster — GPU-backed implementation of kanvas-skia's device
// abstraction. Hosts SkWebGpuDevice (sibling of SkBitmapDevice from
// :kanvas-skia) and its WebGPU plumbing (context, headless target,
// shader resources). See MIGRATION_PLAN_GPU_WEBGPU.md for the full
// phase plan; this module was extracted from :kanvas-skia in G1
// (post-mortem G0 → "code GPU touche le main classpath → split").
//
// Dependency direction: :gpu-raster depends on :kanvas-skia (for
// SkDevice / SkBitmap / SkPaint / etc.), not the reverse. Raster
// consumers of :kanvas-skia don't pay the wgpu4k-toolkit native
// binary cost (~50 MB Metal/Vulkan/DX) until they explicitly opt in
// by depending on this module.

import java.net.URL

plugins {
    id("buildsrc.convention.kotlin-jvm")
    // G7.2 — Dokka GFM doc generation for :gpu-raster (WebGPU GPU
    // pipeline). See :math/build.gradle.kts for the reference setup.
    id("org.jetbrains.dokka") version "2.2.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas-skia"))
    implementation(project(":math"))
    // G4.1 — gradient shaders need to read SkLinearGradient state
    // (endpoints, stops, positions, tile mode). The gradient classes
    // live in :cpu-raster ; this dep is the smallest change that lets
    // the GPU device detect-and-dispatch on them. Future G4.x slices
    // (radial / sweep) reuse the same surface.
    implementation(project(":cpu-raster"))
    implementation("io.ygdrasil:wgpu4k-toolkit:0.2.0-SNAPSHOT")
    // wgpu4k's mapAsync / requestDevice are `suspend` functions. The
    // toolkit ships kotlinx-coroutines transitively (runtime), but its
    // `implementation` scope hides it from consumers' compile classpath
    // (G1.2: WebGpuContext.kt + SkWebGpuDevice.kt under src/main/ use
    // `runBlocking`).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    // G2.3b — cross-test harness needs :cpu-raster's testing helpers
    // (compareBitmapsDetailed, loadReferenceBitmap, etc.) which live in
    // :cpu-raster/src/main. testImplementation only — the GPU device
    // itself never depends on the CPU rasterizer, only the cross-tests
    // need it.
    testImplementation(project(":cpu-raster"))
    // Iter 4 — Skia-mirror GM ports (BigRectGM, ThinRectsGM,
    // ClipStrokeRectGM, ...) moved out of :cpu-raster into
    // :skia-integration-tests. Cross-tests need them here.
    testImplementation(project(":skia-integration-tests"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // G7.2 — GFM (GitHub-Flavored Markdown) renderer scoped to
    // `:gpu-raster:dokkaGfm` only.
    dokkaGfmPlugin("org.jetbrains.dokka:gfm-plugin:2.2.0")
}

sourceSets {
    test {
        // G2.3b — cross-tests load `original-888/<gm>.png` reference
        // bitmaps via TestUtils.loadReferenceBitmap. Those PNGs are owned by
        // :skia-integration-tests, same wiring as :cpu-raster/build.gradle.kts.
        resources.srcDir("../skia-integration-tests/src/test/resources")
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
// reference setup.
tasks.dokkaGfm {
    moduleName.set("gpu-raster")
    dokkaSourceSets.named("main") {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URL("https://github.com/ygdrasil-io/kanvas/blob/master/gpu-raster/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}
