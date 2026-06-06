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

import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.net.URL

plugins {
    id("buildsrc.convention.kotlin-jvm")
    // G7.2 — Dokka GFM doc generation for :gpu-raster (WebGPU GPU
    // pipeline). See :math/build.gradle.kts for the reference setup.
    id("org.jetbrains.dokka") version "2.2.0"
}

data class GpuSmokePatternSpec(
    val pattern: String,
    val evidencePath: String,
    val unresolvedSimilarityRegression: String? = null,
)

data class ImageRectSimilarityGuard(
    val testClass: String,
    val issue: String,
    val resolutionEvidencePath: String,
)

fun testPatternMayIncludeClass(pattern: String, testClass: String): Boolean =
    pattern == testClass ||
        pattern == "$testClass.*" ||
        (pattern.endsWith("*") && testClass.startsWith(pattern.removeSuffix("*"))) ||
        pattern.contains(testClass) ||
        testClass.contains(pattern)

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":kanvas-skia"))
    implementation(project(":math"))
    implementation(project(":render-pipeline"))
    implementation("org.graphiks:core-jvm:1.0.0-SNAPSHOT")
    implementation("org.graphiks:parser-jvm:1.0.0-SNAPSHOT")
    implementation("org.graphiks:generator-jvm:1.0.0-SNAPSHOT")
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

tasks.register<JavaExec>("wgslParserSmoke") {
    group = "verification"
    description = "Parses one WGSL shader and prints deterministic parser smoke diagnostics."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.gpu.webgpu.tools.WgslParserSmokeMainKt")
    args(file("src/main/resources/shaders/solid_color.wgsl").absolutePath)
}

tasks.register<JavaExec>("wgslParserSmokeInvalid") {
    group = "verification"
    description = "Parses an intentionally invalid WGSL fixture and requires diagnostics."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.gpu.webgpu.tools.WgslParserSmokeMainKt")
    args(
        file("src/main/resources/wgsl-fixtures/invalid_missing_semicolon.wgsl").absolutePath,
        "--expect-failure",
    )
}

tasks.register<JavaExec>("wgslValidateAll") {
    group = "verification"
    description = "Parses all WGSL shaders and emits reflection coverage summary."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.gpu.webgpu.tools.WgslValidationReportKt")
    args(file("src/main/resources/shaders").absolutePath)
}

tasks.register<JavaExec>("wgslValidateStrict") {
    group = "verification"
    description = "Fails on parser or reflection diagnostics in generated and registered WGSL modules."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.gpu.webgpu.tools.WgslStrictValidationReportKt")
    args(file("src/main/resources/shaders").absolutePath)
}

tasks.register<JavaExec>("gpuInventoryFailureReport") {
    group = "verification"
    description = "Classifies full GPU inventory failures from JUnit XML and emits markdown/json artifacts."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.skia.gpu.webgpu.tools.GpuInventoryFailureReportKt")
    args(
        file("build/test-results/test").absolutePath,
        file("build/reports/gpu-inventory").absolutePath,
    )
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
    System.getProperty("kanvas.sceneEvidence.write")?.let {
        systemProperty("kanvas.sceneEvidence.write", it)
    }
    System.getProperty("kanvas.for333.runtimeTrace.write")?.let {
        systemProperty("kanvas.for333.runtimeTrace.write", it)
    }
    System.getProperty("kanvas.for339.runtimeTrace.write")?.let {
        systemProperty("kanvas.for339.runtimeTrace.write", it)
    }
    System.getProperty("kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16CoverageStencilContributionMap.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16CoverageStencilContributionMap.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16FinalResidualOriginMap.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16FinalResidualOriginMap.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16PassWriteProbe.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16PassWriteProbe.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16DirectPassWriteHook.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16DirectPassWriteHook.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled", it)
    }
    System.getProperty("kanvas.cpu.m60F16ScanFillSubsampleMaskFor428.enabled")?.let {
        systemProperty("kanvas.cpu.m60F16ScanFillSubsampleMaskFor428.enabled", it)
    }
    System.getProperty("kanvas.cpu.m60F16CpuSpanQuantizationFor429.enabled")?.let {
        systemProperty("kanvas.cpu.m60F16CpuSpanQuantizationFor429.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16CpuWidthQuantizationAlignmentFor430.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16CpuWidthQuantizationAlignmentFor430.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16StencilSubdrawSourceColorFor433.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16StencilSubdrawSourceColorFor433.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16StencilSourcePayloadTraceFor434.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16StencilSourcePayloadTraceFor434.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16HostDrawPaintBindingFor436.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16HostDrawPaintBindingFor436.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16CpuReferenceSourceExpectationFor437.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16CpuReferenceSourceExpectationFor437.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16CpuVsWebGpuGreenDrawCoverageFor438.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16CpuVsWebGpuGreenDrawCoverageFor438.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16StencilCoverGeometryVsCpuGreenMaskFor439.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16StencilCoverGeometryVsCpuGreenMaskFor439.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16EdgePredicateVsCpuGreenCoverageFor440.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16EdgePredicateVsCpuGreenCoverageFor440.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16ExactSubsampleMaskVsCpuGreenFor441.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16ExactSubsampleMaskVsCpuGreenFor441.enabled", it)
    }
    System.getProperty("kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled")?.let {
        systemProperty("kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled", it)
    }
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("gpuInventoryFailureReport"))
}

val gpuSmokePatternSpecs = listOf(
    // M31 promotion policy baseline:
    // `reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md`
    // Keep smoke minimal, adapter-backed, and free of expected unsupported
    // diagnostics. Broader coverage stays in :gpu-raster:test inventory.
    GpuSmokePatternSpec(
        pattern = "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest",
        evidencePath = "../reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md",
    ),
    GpuSmokePatternSpec(
        pattern = "org.skia.gpu.webgpu.PipelineKeyTelemetryTest",
        evidencePath = "../reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md",
    ),
    // M32 image-rect promotion: the 64x64 Skbug4734 fixture is the
    // smallest fixed bitmap/image-rect regression and exercises strict
    // nearest fractional-src sampling without adding cross-backend cost.
    GpuSmokePatternSpec(
        pattern = "org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest",
        evidencePath = "../reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion.md",
    ),
    // M33 Path AA promotion: this fixture renders a stable analytic-AA
    // convex path and is separate from edge-budget refusal inventory.
    GpuSmokePatternSpec(
        pattern = "org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest",
        evidencePath = "../reports/wgsl-pipeline/2026-05-27-m33-path-aa-smoke-promotion.md",
    ),
    // M38 image-filter promotion: selected Crop(kDecal, input = Offset(null))
    // child pre-pass fixture now has adapter-backed WebGPU evidence. Keep the
    // cross-backend fixture in full inventory to avoid expanding required smoke
    // cost while still preserving raster/GPU parity evidence.
    GpuSmokePatternSpec(
        pattern = "org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest",
        evidencePath = "../reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md",
    ),
)

val gpuSmokePatterns = gpuSmokePatternSpecs.map { it.pattern }

val imageRectSimilarityGuards = listOf(
    ImageRectSimilarityGuard(
        testClass = "org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest",
        issue = "GRA-95",
        resolutionEvidencePath = "../reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md",
    ),
    ImageRectSimilarityGuard(
        testClass = "org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest",
        issue = "GRA-95",
        resolutionEvidencePath = "../reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md",
    ),
    ImageRectSimilarityGuard(
        testClass = "org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest",
        issue = "GRA-96",
        resolutionEvidencePath = "../reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md",
    ),
    ImageRectSimilarityGuard(
        testClass = "org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest",
        issue = "GRA-96",
        resolutionEvidencePath = "../reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md",
    ),
)

tasks.register("validateGpuSmokePromotionPolicy") {
    group = "verification"
    description = "Fails when required GPU smoke includes unresolved inventory-only diagnostics."

    inputs.property("gpuSmokePatterns", gpuSmokePatterns)
    inputs.files(gpuSmokePatternSpecs.map { file(it.evidencePath) })
    inputs.files(imageRectSimilarityGuards.map { file(it.resolutionEvidencePath) })

    doLast {
        val explicitlyBlocked = gpuSmokePatternSpecs.filter { it.unresolvedSimilarityRegression != null }
        if (explicitlyBlocked.isNotEmpty()) {
            val details = explicitlyBlocked.joinToString(separator = "\n") { spec ->
                "- ${spec.pattern}: ${spec.unresolvedSimilarityRegression}"
            }
            throw GradleException(
                "gpuSmokeTest promotion blocked: unresolved similarity-regression entries are not smoke-eligible.\n" +
                    details + "\n" +
                    "Fix/rebaseline with artifact evidence first, or leave the fixture in gpuInventoryTest.",
            )
        }

        val missingEvidence = gpuSmokePatternSpecs.filter { !file(it.evidencePath).isFile }
        if (missingEvidence.isNotEmpty()) {
            val details = missingEvidence.joinToString(separator = "\n") { spec ->
                "- ${spec.pattern}: missing ${spec.evidencePath}"
            }
            throw GradleException("gpuSmokeTest promotion evidence is missing.\n$details")
        }

        val unresolvedImageRectPromotions = imageRectSimilarityGuards.filter { guard ->
            gpuSmokePatterns.any { pattern -> testPatternMayIncludeClass(pattern, guard.testClass) } &&
                !file(guard.resolutionEvidencePath).isFile
        }
        if (unresolvedImageRectPromotions.isNotEmpty()) {
            val details = unresolvedImageRectPromotions.joinToString(separator = "\n") { guard ->
                "- ${guard.testClass}: ${guard.issue} resolution evidence missing at ${guard.resolutionEvidencePath}"
            }
            throw GradleException(
                "gpuSmokeTest promotion blocked: known bitmap/image-rect similarity regressions " +
                    "are not smoke-eligible until resolution evidence exists.\n" +
                    details,
            )
        }

    }
}

tasks.register<Test>("gpuSmokeTest") {
    group = "verification"
    description =
        "Runs required adapter-backed GPU smoke fixtures (selector route + telemetry) and fails when adapter tests skip."

    dependsOn(tasks.named("validateGpuSmokePromotionPolicy"))

    val inventoryTask = tasks.named<Test>("test").get()
    testClassesDirs = inventoryTask.testClassesDirs
    classpath = inventoryTask.classpath
    shouldRunAfter(tasks.named("test"))

    filter {
        gpuSmokePatterns.forEach { pattern -> includeTestsMatching(pattern) }
    }

    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null && result.skippedTestCount > 0) {
                    throw GradleException(
                        "gpuSmokeTest rejected adapter skip evidence: skipped=${result.skippedTestCount}. " +
                            "Smoke lane requires adapter-backed execution with zero skipped tests.",
                    )
                }
            }

            override fun beforeTest(testDescriptor: TestDescriptor) = Unit

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
        },
    )
}

tasks.register("gpuInventoryTest") {
    group = "verification"
    description =
        "Runs the full GPU inventory suite (alias for :gpu-raster:test) for failure classification and artifacts."
    dependsOn(tasks.named("test"))
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
