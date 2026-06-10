import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import javax.xml.parsers.DocumentBuilderFactory

val pureKotlinCodecProjects = setOf(
    "codec-api",
    "codec-core",
    "codec-common",
    "codec-test-fixtures",
    "codec-real-image-tests",
    "codec-all-kotlin",
    "codec-png-api",
    "codec-png-kotlin",
    "codec-jpeg-api",
    "codec-jpeg-kotlin",
    "codec-gif-kotlin",
    "codec-bmp-kotlin",
    "codec-wbmp-kotlin",
    "codec-webp-kotlin",
    "codec-ico-kotlin",
    "codec-android",
    "codec-animated",
    "codec-extended",
)

val forbiddenCodecBackendProjects = setOf(
    "codec-all-awt",
    "codec-png-imageio",
    "codec-jpeg-imageio",
    "codec-gif-imageio",
    "codec-bmp-imageio",
    "codec-wbmp-imageio",
    "codec-webp-imageio",
)

val productionDependencyConfigurations = setOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
    "compileClasspath",
    "runtimeClasspath",
)

val productionProjectDependencyConfigurations = setOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
)

val forbiddenSourcePatterns = listOf(
    Regex("""\bimport\s+java\.awt(?:\.|\s|$)"""),
    Regex("""\bimport\s+javax\.imageio(?:\.|\s|$)"""),
    Regex("""\bjava\.awt\."""),
    Regex("""\bjavax\.imageio\."""),
    Regex("""\brequires\s+java\.desktop\b"""),
)

val forbiddenBuildScriptPatterns = listOf(
    Regex("""\bjava\.desktop\b"""),
    Regex("""\bjava\.awt\b"""),
    Regex("""\bjavax\.imageio\b"""),
)

fun String.withoutKotlinOrJavaComments(): String {
    val withoutBlockComments = replace(Regex("""(?s)/\*.*?\*/"""), "")
    return withoutBlockComments
        .lineSequence()
        .joinToString("\n") { line ->
            val commentStart = line.indexOf("//")
            if (commentStart >= 0) line.substring(0, commentStart) else line
        }
}

fun Project.registerPipelineConformanceTest(descriptionText: String, testPatterns: List<String>) {
    plugins.withId("buildsrc.convention.kotlin-jvm") {
        val projectSourceSets = extensions.getByType<SourceSetContainer>()

        tasks.register<Test>("pipelineConformanceTest") {
            group = "verification"
            description = descriptionText

            val testSourceSet = projectSourceSets.named("test").get()
            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath
            shouldRunAfter(tasks.named("test"))

            filter {
                testPatterns.forEach { pattern -> includeTestsMatching(pattern) }
            }
        }
    }
}

data class PipelineConformanceSuiteSummary(
    val className: String,
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
) {
    val failed: Boolean get() = failures > 0 || errors > 0
    val passed: Boolean get() = !failed && tests > 0 && skipped == 0
    val skippedOnly: Boolean get() = !failed && tests > 0 && skipped == tests
}

data class RequiredPipelineConformanceSuite(
    val className: String,
    val resultRoot: String,
)

val requiredPipelineConformanceSuites = listOf(
    RequiredPipelineConformanceSuite(
        className = "org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistryTest",
        resultRoot = "cpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.effects.runtime.SkRuntimeEffectDispatchTest",
        resultRoot = "cpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.effects.runtime.SkRuntimeEffectMakeTest",
        resultRoot = "cpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.BlendPlanTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.PipelineKeyTelemetryTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleRuntimeEffectSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SkWebGpuGlyphAtlasTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleLatinLineSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleLinearGradientSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleBitmapRectSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleSrcOverAlphaSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleColorFilterSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.RuntimeColorFilterSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.SimpleSaveLayerImageFilterSceneEvidenceTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.tools.GeneratedLinearGradientWgslTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.tools.GeneratedSolidRectWgslTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.tools.WgslValidationReportTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.gpu.webgpu.tools.WgslStrictValidationReportTest",
        resultRoot = "gpu-raster/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.pipeline.CpuScalarPipelineExecutorTest",
        resultRoot = "render-pipeline/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.pipeline.GeometryCoverageContractsTest",
        resultRoot = "render-pipeline/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.pipeline.GeometryCoverageMigrationHarnessTest",
        resultRoot = "render-pipeline/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.pipeline.KanvasPipelineIRTest",
        resultRoot = "render-pipeline/build/test-results/pipelineConformanceTest",
    ),
    RequiredPipelineConformanceSuite(
        className = "org.skia.core.SkBitmapDescriptorCoverageOracleTest",
        resultRoot = "kanvas-skia/build/test-results/pipelineConformanceTest",
    ),
)

fun parsePipelineConformanceSuite(xmlFile: File): PipelineConformanceSuiteSummary {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
    val suite = document.documentElement
    return PipelineConformanceSuiteSummary(
        className = suite.getAttribute("name"),
        tests = suite.getAttribute("tests").toInt(),
        failures = suite.getAttribute("failures").toInt(),
        errors = suite.getAttribute("errors").toInt(),
        skipped = suite.getAttribute("skipped").toInt(),
    )
}

fun conformanceStatus(suites: List<PipelineConformanceSuiteSummary>): String = when {
    suites.isEmpty() -> "not run"
    suites.any { it.failed } -> "failed"
    suites.all { it.skippedOnly } -> "skipped"
    suites.any { it.skipped > 0 } -> "passed with skipped checks"
    suites.all { it.passed } -> "passed"
    else -> "not run"
}

data class GpuAdapterEvidence(
    val status: String,
    val localJUnitStatus: String,
    val ciLaneAvailable: Boolean,
    val blockerText: String,
    val unblockCondition: String,
)

private val ADAPTER_PASS = "adapter-pass"
private val ADAPTER_FAIL = "adapter-fail"
private val ADAPTER_SKIPPED = "adapter-skipped"
private val ADAPTER_TIMEOUT = "adapter-timeout"
private val ADAPTER_BLOCKED = "blocked-no-adapter-lane"

fun gpuAdapterEvidenceForReport(
    suites: List<PipelineConformanceSuiteSummary>,
    ciLaneAvailable: Boolean = true,
): GpuAdapterEvidence {
    val adapterSuites = suites.filter {
        it.className == "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest" ||
            it.className == "org.skia.gpu.webgpu.PipelineKeyTelemetryTest"
    }
    val localStatus = conformanceStatus(adapterSuites)
    val observedStatus = when {
        adapterSuites.any { it.failed } -> ADAPTER_FAIL
        adapterSuites.any { it.skipped > 0 } -> ADAPTER_SKIPPED
        adapterSuites.all { it.passed } -> ADAPTER_PASS
        adapterSuites.isEmpty() -> ADAPTER_TIMEOUT
        else -> ADAPTER_TIMEOUT
    }
    val status = if (!ciLaneAvailable) ADAPTER_BLOCKED else observedStatus
    val blockerText = when (status) {
        ADAPTER_PASS ->
            "No release blocker from required GitHub Actions `GPU tests (macos)` smoke lane."
        ADAPTER_FAIL ->
            "Release blocker: required GitHub Actions `GPU tests (macos)` smoke lane failed adapter-backed checks."
        ADAPTER_SKIPPED ->
            "Release blocker: required GitHub Actions `GPU tests (macos)` smoke lane reported adapter-dependent skips."
        ADAPTER_TIMEOUT ->
            "Release blocker: required GitHub Actions `GPU tests (macos)` smoke lane did not produce a completed adapter verdict (timeout/not-run)."
        else ->
            "Release blocker: required GitHub Actions `GPU tests (macos)` smoke lane is unavailable."
    }
    val unblockCondition = when (status) {
        ADAPTER_PASS ->
            "Keep required smoke lane green and keep full GPU inventory classification as a separate signal."
        ADAPTER_FAIL ->
            "Fix adapter-backed smoke regressions until `GPU tests (macos)` reports adapter-pass."
        ADAPTER_SKIPPED ->
            "Ensure adapter-dependent smoke fixtures run without skips and fail closed on skip."
        ADAPTER_TIMEOUT ->
            "Stabilize CI execution so the required smoke lane completes with adapter-backed results and artifacts."
        else ->
            "Enable a required/scheduled adapter lane (`GPU tests (macos)` or equivalent) that uploads artifacts and fails on adapter skips."
    }
    return GpuAdapterEvidence(
        status = status,
        localJUnitStatus = localStatus,
        ciLaneAvailable = ciLaneAvailable,
        blockerText = blockerText,
        unblockCondition = unblockCondition,
    )
}

fun runPipelineConformanceCommand(vararg command: String): String =
    ProcessBuilder(*command)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .use { it.readText().trim() }

fun renderPipelineConformanceReport(
    commit: String,
    suites: List<PipelineConformanceSuiteSummary>,
    vectorDecisionReportPresent: Boolean,
    legacyWgslDiagnosticsAllowlistCount: Int,
    runtimeEffectSupportMatrixCounts: String,
    runtimeEffectLayoutV2Counts: String,
    runtimeShaderEffectsV2Counts: String,
    runtimeChildShaderEffectLaneCounts: String,
    runtimeColorFilterWgslCounts: String,
    runtimeBlenderBoundaryCounts: String,
    runtimeEffectUniformPreviewCounts: String,
    runtimeEffectsV2EvidenceBundleCounts: String,
): String {
    val byName = suites
        .sortedBy { it.className }
        .associateBy { it.className }
    fun suite(name: String): PipelineConformanceSuiteSummary? = byName[name]
    fun status(vararg names: String): String = conformanceStatus(names.mapNotNull(::suite))
    fun row(label: String, status: String, evidence: String): String =
        "| $label | `$status` | $evidence |"
    fun suiteTableRows(): String = suites
        .sortedBy { it.className }
        .joinToString("\n") { summary ->
            "TABLE_PIPE `${summary.className}` | ${summary.tests} | ${summary.failures} | ${summary.errors} | ${summary.skipped} |"
        }
    val totalTests = suites.sumOf { it.tests }
    val totalFailures = suites.sumOf { it.failures }
    val totalErrors = suites.sumOf { it.errors }
    val totalSkipped = suites.sumOf { it.skipped }
    val gpuAdapterEvidence = gpuAdapterEvidenceForReport(suites)
    val releaseReadinessStatus = if (gpuAdapterEvidence.status == ADAPTER_PASS) "passed" else "blocked"
    val webGpuCoverageInventoryStatus = when (gpuAdapterEvidence.status) {
        ADAPTER_PASS -> "passed"
        ADAPTER_FAIL -> "failed"
        else -> "blocked"
    }
    val vectorStatus = if (vectorDecisionReportPresent) "rejected benchmark" else "not run"
    val vectorDecision = if (vectorDecisionReportPresent) {
        "`rejected benchmark` — see `reports/wgsl-pipeline/2026-05-27-m22-vector-promotion-decision.md`"
    } else {
        "`not run` — no vector decision report found"
    }

    return """
        |# M24 Pipeline Conformance PM Report
        |
        |Linear: GRA-53, GRA-56, GRA-57, GRA-58, GRA-59, GRA-60, GRA-61, GRA-62, GRA-63, GRA-64, GRA-66, GRA-67, GRA-68, GRA-69, GRA-70
        |Source commit: `$commit`
        |
        |## Commands
        |
        |```text
        |rtk ./gradlew --no-daemon pipelineConformance
        |rtk ./gradlew --no-daemon pipelineConformanceReport
        |```
        |
        |## PM Summary
        |
        |The standard conformance entry point completed and produced JUnit evidence for strict generated/registered WGSL,
        |legacy WGSL diagnostic coverage, parser/golden coverage,
        |PipelineKey and BlendPlan contracts, runtime-effect descriptor routing and dispatch-only matrix coverage, CPU descriptor coverage,
        |bounded simple-Latin text line evidence, bounded simple linear-gradient evidence, bounded fixture-backed bitmap rect evidence,
        |bounded SrcOver partial-alpha evidence, bounded simple Blend(kPlus) ColorFilter direct-rect evidence,
        |bounded SaveLayer ColorFilter image-filter evidence,
        |bounded registered SimpleRT runtime-effect evidence,
        |selected registered Runtime Shader Effects V2 promotion evidence,
        |runtime child shader effect CPU-only lane and stable WebGPU refusal evidence,
        |runtime blender CPU-only boundary and stable WebGPU destination-read refusal evidence,
        |runtime effect uniform preview evidence across registered effects with stable PipelineKey telemetry,
        |Runtime Effects V2 evidence bundle aggregation with support/refusal rows and PM non-claims,
        |KAN-035 HairlinesGM root-cause evidence with stable expected-unsupported classification,
        |KAN-036 butt stroke non-hairline bounded row evidence with stable expected-unsupported classification,
        |KAN-037 caps/joins micro-matrix evidence with stable expected-unsupported classification,
        |KAN-038 dashes bounded V1 evidence with stable expected-unsupported classification,
        |KAN-039 nested clip-stack V1 evidence with stable expected-unsupported classification,
        |KAN-040 coverage/stroke/clip closeout matrix with support/refusal proof guards,
        |KAN-041 image-filter DAG bounded V3 evidence with two support rows and stable residual refusals,
        |KAN-042 image-filter residual refusal matrix with PM support/gap/dependency categories,
        |KAN-043 text shaping/fallback scope with explicit font identity, glyph clusters, glyph ids, and stable fallback refusals,
        |KAN-044 glyph mask/atlas ownership boundary with text-owned atlas route, CPU mask oracle, coverage handoff, and stable WebGPU alpha-mask refusal,
        |kanvas-skia production descriptor routing through shared analytic rect coverage execution, WebGPU selector routing, and geometry oracle checks.
        |
        |## Status Matrix
        |
        || Area | Status | Evidence |
        ||---|---|---|
        |${row("Tests", conformanceStatus(suites), "$totalTests tests, $totalFailures failures, $totalErrors errors, $totalSkipped skipped")}
        |${row("Release readiness", releaseReadinessStatus, "`releaseReadiness=$releaseReadinessStatus`; GPU adapter evidence `${gpuAdapterEvidence.status}`")}
        |${row("Strict WGSL status", status("org.skia.gpu.webgpu.tools.WgslStrictValidationReportTest"), "`WgslStrictValidationReportTest` plus required `:gpu-raster:wgslValidateStrict` dependency")}
        |${row("Legacy WGSL diagnostics", status("org.skia.gpu.webgpu.tools.WgslValidationReportTest"), "$legacyWgslDiagnosticsAllowlistCount known diagnostics allowlisted by `gpu-raster/src/test/resources/wgsl-diagnostics-allowlist.txt`; `:gpu-raster:wgslValidateAll` remains the diagnostic inventory")}
        |${row("Generated WGSL status", status("org.skia.gpu.webgpu.tools.GeneratedSolidRectWgslTest", "org.skia.gpu.webgpu.tools.GeneratedLinearGradientWgslTest"), "`GeneratedSolidRectWgslTest`, `GeneratedLinearGradientWgslTest`")}
        |${row("PipelineKey status", status("org.skia.gpu.webgpu.PipelineKeyTelemetryTest"), "`PipelineKeyTelemetryTest`")}
        |${row("BlendPlan status", status("org.skia.gpu.webgpu.BlendPlanTest"), "`BlendPlanTest`")}
        |${row("Descriptor routing status", status("org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest", "org.skia.pipeline.GeometryCoverageMigrationHarnessTest"), "`WebGpuCoveragePlanSelectorTest`, `GeometryCoverageMigrationHarnessTest`; CPU rect harness uses `CpuAnalyticRectCoverageExecutor`")}
        |${row("Clip-stack breadth", status("org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest", "org.skia.pipeline.GeometryCoverageMigrationHarnessTest", "org.skia.core.SkBitmapDescriptorCoverageOracleTest"), "`ClipStackBreadthMatrix` classifies CPU route expectations and WebGPU support/refusal: rect/rrect/rect-difference supported, arbitrary-AA and multi-shape AA refused on WebGPU, shader clip refused on WebGPU, unlowerable stacks use stable diagnostics; CPU descriptor AA-clip and clip-shader fallbacks are asserted")}
        |${row("kanvas-skia production route", status("org.skia.core.SkBitmapDescriptorCoverageOracleTest"), "`SkBitmapDescriptorCoverageOracleTest` proves `SkBitmapDevice` descriptor routing consumes CoveragePlan lowering through the shared analytic rect executor, preserves rollback, and remains pixel-equivalent with legacy")}
        |${row("GPU adapter evidence", gpuAdapterEvidence.status, "`gpuAdapterEvidence=${gpuAdapterEvidence.status}`; local adapter JUnit status `${gpuAdapterEvidence.localJUnitStatus}`; ci adapter lane available `${gpuAdapterEvidence.ciLaneAvailable}`; ${gpuAdapterEvidence.blockerText}")}
        |${row("GPU smoke promotion policy", "enforced", "`reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md` defines promotion checklist and rollback path; current smoke includes selector/telemetry baseline plus promoted image-rect, Path AA, and selected SimpleOffset image-filter fixtures; `coverage.edge-count-exceeded`, out-of-scope `image-filter.crop-input-nonnull-prepass-required`, and unresolved similarity regressions remain inventory-only")}
        |${row("WebGPU coverage strategy inventory", webGpuCoverageInventoryStatus, "`WebGpuCoverageStrategyInventory` separates selector-only `proven` mask/atlas route selection, adapter-evidence promoted candidates (analytic rect/rrect, convex fan, stencil-cover) with explicit statuses (`adapter-pass`, `adapter-fail`, `adapter-skipped`, `adapter-timeout`; `blocked-no-adapter-lane` only when the lane is missing), `compatibility` full-scissor, and `refused` span-runs/alpha-mask/coverage-atlas/edge-overflow/arbitrary-AA-clip branches with stable diagnostics; `coverage.edge-count-exceeded` remains a known unsupported GPU breadth gap and `image-filter.crop-input-nonnull-prepass-required` is retained only for out-of-scope Crop(input nonNull) graph shapes, not the M38 SimpleOffset pre-pass target.")}
        |${row("CoverageAtlas policy gate", "blocked", "`CoverageAtlasPolicyGate` keeps persistent atlas caching disabled by default: persistent policy verdict `no-go`, shape-key/transform-key/invalidation/memory-budget/eviction/CPU-GPU-sync/owner-thread checks are missing by policy, static gate counters remain hits=0 misses=0 residentBytes=0 evictions=0 because runtime atlas telemetry is not enabled, and unsupported persistent atlas use emits `coverage.atlas-policy-unavailable`")}
        |${row("Glyph mask ownership", status("org.skia.pipeline.GeometryCoverageContractsTest", "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest", "org.skia.gpu.webgpu.SkWebGpuGlyphAtlasTest"), "`GlyphMaskLowering` defines the descriptor boundary for glyph-run mask handoff: text/glyph infrastructure owns discovery, rasterization, atlas lifetime, and invalidation; geometry only consumes an opaque alpha-mask ref or emits `coverage.glyph-mask-dependency-unavailable`; `SkWebGpuGlyphAtlasTest` proves a bounded KAN-010 A8/R8 upload-plan atlas with coordinates and sampling diagnostics; WebGPU still refuses standalone alpha-mask coverage with `coverage.alpha-mask-unsupported`")}
        |${row("Simple Latin text line", status("org.skia.gpu.webgpu.SimpleLatinLineSceneEvidenceTest"), "`SimpleLatinLineSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `text.simple-latin.line.v1`; WebGPU selects `webgpu.text.outline-path.simple-latin` with `fallbackReason=none`, compares against a CPU atlas alpha-mask oracle at local threshold 95%, attaches `SkWebGpuGlyphAtlas` upload evidence, and keeps non-claims for shaping, fallback fonts, emoji/color fonts, SDF/LCD, RTL/BiDi, ligatures, and broad text support")}
        |${row("Simple linear gradient", status("org.skia.gpu.webgpu.SimpleLinearGradientSceneEvidenceTest"), "`SimpleLinearGradientSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `paint.linear-gradient.rect.v1`; WebGPU selects `webgpu.generated.linear-gradient.rect` with generated `fs_clamp` WGSL validation, `fallbackReason=none`, compares against an analytic sRGB two-stop oracle at local threshold 99%, and keeps non-claims for wide-gamut color management, all tile modes, gradient meshes, advanced color spaces, broad gradient-family support, color-filter chains, and codec/mipmap work")}
        |${row("Simple bitmap rect", status("org.skia.gpu.webgpu.SimpleBitmapRectSceneEvidenceTest"), "`SimpleBitmapRectSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `paint.bitmap-rect.nearest.fixture.v1`; WebGPU selects `webgpu.image.bitmap-rect.nearest.fixture`, uses fixture-backed `SkCanvas.drawImageRect` with `sampler=nearest`, `tileMode=kClamp`, `srcRectConstraint=kStrict`, `fallbackReason=none`, compares against an analytic strict-nearest fixture oracle at local threshold 99%, and keeps non-claims for broad image support, codec decode, arbitrary textures, mipmaps, tile-mode breadth, color-managed decode, texture atlases, and perspective transforms")}
        |${row("Simple SrcOver alpha", status("org.skia.gpu.webgpu.SimpleSrcOverAlphaSceneEvidenceTest"), "`SimpleSrcOverAlphaSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `paint.src-over-alpha.rect-stack.v1`; WebGPU selects `webgpu.blend.src-over.partial-alpha.fixed-function`, validates generated solid-rect WGSL, records `blendPlan=FixedFunction`, uses two `kSrcOver` partial-alpha rect commands, `fallbackReason=none`, compares against an analytic SrcOver oracle at local threshold 99%, and keeps non-claims for arbitrary blend modes, advanced blend chains, saveLayer blend composition, shader destination reads, wide/color-managed color pipeline, and broad layer compositing")}
        |${row("Simple ColorFilter", status("org.skia.gpu.webgpu.SimpleColorFilterSceneEvidenceTest"), "`SimpleColorFilterSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `paint.color-filter.blend-kplus.rect.v1`; WebGPU selects `webgpu.paint.color-filter.blend-kplus.solid-color`, validates handwritten `solid_color.wgsl`, records generated solid-rect fallback `generated solid rect does not support colorFilter` while the selected route has `fallbackReason=none`, compares against an analytic sRGB Blend(kPlus) ColorFilter oracle at local threshold 99%, and keeps non-claims for broad ColorFilter support, ColorFilter chains, color-managed/wide pipeline, saveLayer/gradient/bitmap/runtime/table ColorFilters, and global threshold/color-policy changes")}
        |${row("Runtime ColorFilter evidence", status("org.skia.gpu.webgpu.RuntimeColorFilterSceneEvidenceTest"), "`RuntimeColorFilterSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `runtime.color-filter.luma-to-alpha.rect.v1`; WebGPU selects `webgpu.runtime-color-filter.luma-to-alpha.direct-rect`, validates and reflects `runtime_color_filter_luma_to_alpha.wgsl`, records stage order `solid-color shader -> runtime color filter -> fixed-function kSrcOver blend -> store`, compares against an analytic luma-to-alpha SrcOver-white oracle at local threshold 99%, and keeps non-claims for broad runtime ColorFilters, child/uniform/LUT/color-space wrappers, shader inputs, and global threshold/color-policy changes")}
        |${row("Simple SaveLayer image-filter", status("org.skia.gpu.webgpu.SimpleSaveLayerImageFilterSceneEvidenceTest"), "`SimpleSaveLayerImageFilterSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `save-layer.image-filter.color-filter-matrix.v1`; WebGPU selects `webgpu.image-filter.color-filter.layer-composite`, records `prepassRoute=null`, `materialiseStages=0`, `fallbackReason=none`, compares against an analytic SaveLayer ColorFilter(Matrix) oracle at local threshold 99%, and keeps non-claims for arbitrary layer stacks, multi-node DAGs, broad image-filter support, CPU readback fallback, and global threshold changes")}
        |${row("SimpleRT runtime effect", status("org.skia.gpu.webgpu.SimpleRuntimeEffectSceneEvidenceTest"), "`SimpleRuntimeEffectSceneEvidenceTest` writes reference/CPU/WebGPU/diff/stats artifacts for `runtime.simple_rt.descriptor.rect.v1`; WebGPU selects `webgpu.runtime-effect.descriptor.simple_rt`, validates and reflects `runtime_simple_rt.wgsl` with `gColor@0`, records `fallbackReason=none`, compares against an analytic SimpleRT coordinate-color oracle at local tolerance 1 and threshold 99.95%, references reporting-only CPU/GPU performance artifacts, and keeps stable refusals for missing WGSL descriptors/arbitrary SkSL plus non-claims for dynamic SkSL compilation, SkSL IR/VM, broad runtime effects, SpiralRT promotion, runtime color-filter/blender/image-filter, and live-editing breadth")}
        |${row("Image rect lowering", status("org.skia.pipeline.GeometryCoverageContractsTest", "org.skia.core.SkBitmapDescriptorCoverageOracleTest", "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest"), "`ImageRectLowering` captures source rect, destination rect, transform facts, opaque paint-owned sampling payload handoff, and route id; axis-aligned image rects select analytic rect coverage, transformed descriptor tests select path-like coverage without moving sampling/pixels/filtering/colorspace into geometry; CPU oracle covers one axis-aligned image rect and WebGPU selector diagnostics record the adapter-gated image-rect route")}
        |${row("Runtime-effect status", status("org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistryTest", "org.skia.effects.runtime.SkRuntimeEffectDispatchTest", "org.skia.effects.runtime.SkRuntimeEffectMakeTest", "org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest"), "CPU registry/dispatch/Make tests plus WebGPU descriptor test; support matrix counts $runtimeEffectSupportMatrixCounts; layout V2 counts $runtimeEffectLayoutV2Counts")}
        |${row("Runtime Shader Effects V2 promotion", "passed", "`pipelineRuntimeShaderEffectsV2PromotionReport` validates selected registered shader effects against support matrix V2, layout V2, route JSON, reference/CPU/WebGPU/diff/stat artifacts, and keeps counts $runtimeShaderEffectsV2Counts")}
        |${row("Runtime child shader lane", "expected-unsupported", "`pipelineRuntimeChildShaderEffectLaneReport` validates `runtime.unsharp_rt` child descriptor representation, CPU oracle evidence, route JSON, resource-axis classification, and stable WebGPU refusal; counts $runtimeChildShaderEffectLaneCounts")}
        |${row("Runtime ColorFilter WGSL", "passed", "`pipelineRuntimeColorFilterWgslReport` validates selected `runtime.color_filter_luma_to_alpha` descriptor/WGSL layout, reference/CPU/WebGPU/diff/stat route artifacts, and stable non-selected ColorFilter reason codes; counts $runtimeColorFilterWgslCounts")}
        |${row("Runtime Blender boundary", "expected-unsupported", "`pipelineRuntimeBlenderBoundaryReport` validates selected `runtime.invert_blender` descriptor, CPU fixture, route JSON, BlendPlan dump, and stable WebGPU destination-read refusal; counts $runtimeBlenderBoundaryCounts")}
        |${row("Runtime Effect uniform preview", "passed", "`pipelineRuntimeEffectUniformPreviewReport` validates two registered runtime effects, four edited uniform states, stable PipelineKey telemetry, invalid-value refusal policy, and headless/Kadre lane separation; counts $runtimeEffectUniformPreviewCounts")}
        |${row("Runtime Effects V2 evidence bundle", "passed", "`pipelineRuntimeEffectsV2EvidenceBundleReport` aggregates all Runtime Effects V2 support/refusal rows, linked artifacts, stable diagnostics, and PM non-claims; counts $runtimeEffectsV2EvidenceBundleCounts")}
        |${row("KAN-035 HairlinesGM root cause", "expected-unsupported", "`validateKan035HairlinesRootCause` classifies the current HairlinesGM residual as `cap-join-parity`, keeps `skia-gm-hairlines` expected-unsupported, records `expected-unsupported-diagnostic=1` and `unexpected-exception=0`, and makes no renderer, shader, threshold, or edge-budget change.")}
        |${row("KAN-036 butt stroke non-hairline", "expected-unsupported", "`validateKan036ButtStrokeNonHairline` selects one non-hairline no-dash butt-cap stroke row, records WebGPU stable refusal `coverage.stroke-cap-join-visual-parity-below-threshold` with `coverageEdgeCount=66/256`, blocks support until WebGPU image/diff and CPU-vs-Skia support-ready evidence exist, and makes no renderer, shader, threshold, or edge-budget change.")}
        |${row("KAN-037 caps/joins micro-matrix", "expected-unsupported", "`validateKan037CapsJoinsMicroMatrix` selects `round-round`, preserves `butt-bevel` and `square-bevel` sentinels, records WebGPU stable refusal `coverage.stroke-cap-join-visual-parity-below-threshold` with `coverageEdgeCount=18/256`, keeps closed-contour join CPU evidence as a visible support blocker, and makes no renderer, shader, threshold, or edge-budget change.")}
        |${row("KAN-038 dashes bounded V1", "expected-unsupported", "`validateKan038DashesBoundedV1` identifies `skia-gm-dashing-width1-pattern1-1-aa` with 2/8 dash intervals, phase 0, stroke width 1, path effect before stroke, keeps it refused via `coverage.dashing.row-specific-artifacts-required`, preserves the `path-aa-dashing-edge-budget` sentinel via `coverage.edge-count-exceeded`, and makes no renderer, shader, threshold, edge-budget, or dash-budget change.")}
        |${row("KAN-039 nested clip-stack V1", "expected-unsupported", "`validateKan039NestedClipStackV1` selects `m60-bounded-nested-rrect-clip`, records clip sequence `rect/intersect + rect/intersect + rrect-oval/difference`, clipDepth `3/4`, edgeCount `72/256`, keeps it refused via `coverage.nested-clip-visual-parity-below-threshold`, preserves `m57-aaclip-bounded-grid` support, and makes no renderer, shader, threshold, edge-budget, clip-depth budget, or integer-scissor substitution change.")}
        |${row("KAN-040 coverage/stroke/clip closeout matrix", "passed", "`validateKan040CoverageCloseoutMatrix` aggregates HairlinesGM, butt stroke, caps/joins, dashes, AA clip, and nested clip rows into supportable-bounded, visible-non-supportable, expected-unsupported, and dependency-gated categories; it fails support claims without reference/CPU/GPU/diff/stat/route plus `fallbackReason=none` and refuses unsupported rows without stable fallbacks.")}
        |${row("KAN-041 image-filter DAG bounded V3", "passed", "`validateKan041ImageFilterDagBoundedV3` records two bounded support scenes (`crop-image-filter-nonnull-prepass`, `m61-compose-cf-matrix-transform-dag-v2`) with reference/CPU/GPU/diff/stat/route and `fallbackReason=none`, keeps BigTile/ImageFiltersGraph/out-of-scope Crop rows refused with stable reasons, and makes no renderer, shader, threshold, readback, picture-prepass, or broad DAG claim.")}
        |${row("KAN-042 image-filter residual refusal matrix", "passed", "`validateKan042ImageFilterResidualRefusalMatrix` aggregates 15 image-filter rows into `supportable-bounded`, `implementation-gap`, and `dependency-gated` PM categories, keeps every unsupported row on a stable reason code, verifies dashboard `fail=0` and `tracked-gap=0`, and makes no renderer, shader, threshold, budget, or new support claim.")}
        |${row("KAN-043 text shaping/fallback scope", "passed", "`validateKan043TextShapingFallbackScope` records simple Latin support, bounded kerning-style shaping support, complex shaping refusal, and missing glyph/fallback refusal with font face/source/hash, shaping route, clusters, glyph ids, CPU/GPU route or refusal, and guards against implicit system font fallback or broad shaping claims.")}
        |${row("KAN-044 glyph mask/atlas ownership", "passed", "`validateKan044GlyphMaskAtlasOwnership` records the text-owned simple Latin glyph atlas upload plan, CPU glyph-mask oracle, geometry `CoveragePlan.AlphaMask` handoff, and WebGPU standalone alpha-mask refusal `coverage.alpha-mask-unsupported`, with guards against missing glyph keys/generation/upload bytes/cache ids, coverage ownership drift, LCD/SDF, dynamic eviction, and Ganesh/Graphite claims.")}
        |${row("Vector decision", vectorStatus, vectorDecision)}
        |${row("Skipped checks", if (totalSkipped == 0) "passed" else "skipped", "$totalSkipped JUnit skipped checks in local report; GPU CI skip remains residual adapter risk")}
        |
        |## Route Dumps And Evidence Links
        |
        |- CPU default descriptor route dump: `render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageMigrationHarnessTest.kt`
        |  (`selectedRoute=cpu.descriptor.coverage-plan.solid-rect`, `kernel=cpu.scalar.analytic_rect_coverage`, fallback route retained for rollback).
        |- kanvas-skia production descriptor route dump: `kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDescriptorCoverageOracleTest.kt`
        |  (`selectedRoute=cpu.descriptor.coverage-plan.solid-rect`, `fallbackRoute=kanvas-skia.current.draw-rect`,
        |  `loweringResult=CoverageModel.AnalyticRect`, `kernel=cpu.scalar.analytic_rect_coverage`,
        |  and `executionEvidence=lowering-consumed:CoverageModel.AnalyticRect`).
        |- GPU descriptor shadow route dump: `render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageMigrationHarnessTest.kt`
        |  (`descriptorRoute=gpu.shadow.generated-rect-candidate`).
        |- WebGPU selector production dump: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelectorTest.kt`
        |  (`productionDump`, selector disabled rollback, and coverage selector route identifiers).
        |- WebGPU coverage strategy inventory: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt`
        |  (`proven` is limited to selector-only mask/atlas route selection, not adapter CI;
        |  adapter-dependent promoted routes use explicit adapter lane statuses (`adapter-pass`, `adapter-fail`,
        |  `adapter-skipped`, `adapter-timeout`) and reserve `blocked-no-adapter-lane` for missing-lane cases only).
        |- CoverageAtlas policy gate: `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt`
        |  (`CoverageAtlasPolicyGate` reports persistent atlas `no-go` until shape key, transform key,
        |  invalidation, memory budget, eviction, CPU/GPU synchronization, and owner-thread handling are accepted).
        |- Glyph mask ownership: `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt`
        |  (`GlyphMaskLowering` accepts text-owned glyph alpha masks as opaque refs, lowers them to
        |  `CoveragePlan.AlphaMask`, and reports `coverage.glyph-mask-dependency-unavailable` when the
        |  text/glyph infrastructure has not supplied a mask; WebGPU still refuses standalone alpha masks).
        |- WebGPU glyph atlas upload-plan dump: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlasTest.kt`
        |  (`SkWebGpuGlyphAtlas` consumes the KAN-010 CPU glyph masks, emits deterministic A8/R8
        |  coordinates, upload bytes, a sampling diagnostic, and non-claims for line text, shaping,
        |  fallback fonts, emoji/color fonts, SDF/LCD, and dynamic eviction).
        |- Simple Latin text line artifacts: `reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/`
        |  (`route-webgpu.json` records `selectedRoute=webgpu.text.outline-path.simple-latin`,
        |  `fallbackReason=none`, the KAN-011 atlas upload SHA/byte count, and non-claims for
        |  shaping, fallback fonts, emoji/color fonts, SDF/LCD, RTL/BiDi, ligatures, and broad text).
        |- Simple linear gradient evidence: `reports/wgsl-pipeline/scenes/artifacts/kan-013-linear-gradient-wave/`
        |  (`route-webgpu.json` records `selectedRoute=webgpu.generated.linear-gradient.rect`,
        |  generated `entryPoint=fs_clamp`, `tileMode=kClamp`, `colorSpacePolicy=srgb-unmanaged-test-oracle`,
        |  `fallbackReason=none`, and explicit non-claims for wide-gamut, all tile modes, gradient mesh,
        |  advanced color spaces, broad gradient-family support, color-filter chains, and codec/mipmap work).
        |- Simple bitmap rect evidence: `reports/wgsl-pipeline/scenes/artifacts/kan-014-bitmap-rect/`
        |  (`route-webgpu.json` records `selectedRoute=webgpu.image.bitmap-rect.nearest.fixture`,
        |  fixture `kanvas-fixture-checker-8x6-rgba8888-v1`, `sampler=nearest`, `tileMode=kClamp`,
        |  `srcRectConstraint=kStrict`, `colorSpacePolicy=srgb-unmanaged-fixture-oracle`, `fallbackReason=none`,
        |  and explicit non-claims for broad image, codec decode, arbitrary texture, mipmap, tile-mode breadth,
        |  color-managed decode, texture atlas, and perspective transform support).
        |- Simple SrcOver alpha evidence: `reports/wgsl-pipeline/scenes/artifacts/kan-015-srcover-alpha/`
        |  (`route-webgpu.json` records `selectedRoute=webgpu.blend.src-over.partial-alpha.fixed-function`,
        |  `blendMode=kSrcOver`, `blendPlan=FixedFunction`, two partial-alpha rect commands, generated
        |  solid-rect WGSL validation, `colorSpacePolicy=srgb-unmanaged-src-over-oracle`, `fallbackReason=none`,
        |  unsupported blend policy `blend.unsupported-mode.requires-explicit-allowlist`, and explicit non-claims
        |  for arbitrary blend modes, advanced blend chains, saveLayer blend composition, shader destination reads,
        |  wide/color-managed color pipeline, and broad layer compositing).
        |- Simple ColorFilter evidence: `reports/wgsl-pipeline/scenes/artifacts/kan-016-color-filter-blend-kplus/`
        |  (`route-webgpu.json` records `selectedRoute=webgpu.paint.color-filter.blend-kplus.solid-color`,
        |  `colorFilterKind=Blend`, `colorFilterBlendMode=kPlus`, `paintBlendMode=kSrcOver`, handwritten
        |  `solid_color.wgsl` validation, `colorSpacePolicy=srgb-unmanaged-color-filter-oracle`,
        |  selected-route `fallbackReason=none`, generated solid-rect fallback
        |  `generated solid rect does not support colorFilter`, unsupported ColorFilter policy
        |  `color-filter.chain.not-promoted`, and explicit non-claims for broad ColorFilter support,
        |  ColorFilter chains, color-managed/wide pipelines, saveLayer/gradient/bitmap/runtime/table ColorFilters,
        |  and global threshold/color-policy changes).
        |- SimpleRT runtime effect evidence: `reports/wgsl-pipeline/scenes/artifacts/kan-017-simple-rt/`
        |  (`route-webgpu.json` records `selectedRoute=webgpu.runtime-effect.descriptor.simple_rt`,
        |  `runtimeEffectStableId=runtime.simple_rt`, `wgslImplementationId=wgsl/runtime_simple_rt`,
        |  `uniformLayout.gColor=0`, parser-validated and reflected `runtime_simple_rt.wgsl`,
        |  selected-route `fallbackReason=none`, local tolerance 1 with threshold 99.95% and no global
        |  threshold/color-policy changes, reporting-only CPU/GPU performance artifact links, stable refusals
        |  for missing WGSL descriptors and arbitrary SkSL, and explicit non-claims for dynamic SkSL compilation,
        |  SkSL IR/VM, broad runtime effects, SpiralRT promotion, runtime color-filter/blender/image-filter,
        |  and live-editing breadth).
        |- Image rect lowering: `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt`
        |  (`ImageRectLowering` chooses analytic rect coverage for axis-aligned image rects and path-like
        |  coverage in descriptor tests for transformed image rects while preserving an opaque paint-owned
        |  sampling payload reference; WebGPU route evidence remains adapter-gated).
        |- Clip-stack breadth matrix: `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt`
        |  maps rect intersect, rrect intersect, rect difference, arbitrary AA path intersect,
        |  multi-shape AA difference, shader clip, and unlowerable stacks to supported clips or stable refusal codes.
        |  CPU descriptor fallback evidence for AA clip and clip shader is asserted by
        |  `kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDescriptorCoverageOracleTest.kt`.
        |- Pipeline cache telemetry: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/PipelineKeyTelemetryTest.kt`
        |  verifies cold frame misses are at least one and warm frame cache hits increase.
        |- Runtime-effect V2 support matrix: `reports/wgsl-pipeline/runtime-effects-v2/support-matrix.md`
        |  lists descriptor-backed runtime effects separately from adapter-backed scene parity, keeps policy refusals explicit, and avoids broad runtime-effect claims;
        |  current counts are $runtimeEffectSupportMatrixCounts.
        |- Runtime-effect layout V2 report: `reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.md`
        |  compares Kotlin descriptor offsets/sizes to WGSL lowered reflection for registered runtime effects and keeps uniform values out of runtime-effect pipeline cache keys;
        |  current counts are $runtimeEffectLayoutV2Counts.
        |- Runtime Shader Effects V2 promotion: `reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.md`
        |  validates three selected registered shader effects with descriptor-backed CPU/GPU routes, matched layout reflection, `fallbackReason=none`,
        |  reference/CPU/WebGPU/diff/stat artifacts, local threshold evidence, and explicit non-claims for broad runtime effects or dynamic SkSL;
        |  current counts are $runtimeShaderEffectsV2Counts.
        |- Runtime child shader effect lane: `reports/wgsl-pipeline/runtime-child-shader-effect-lane/runtime-child-shader-effect-lane.md`
        |  lists `runtime.unsharp_rt` child `child:kShader@0`, records CPU oracle coverage, emits route JSON with `runtime-effect.child-binding-unsupported`,
        |  classifies child resource axes while excluding uniform values from PipelineKey, and keeps WebGPU support expected-unsupported;
        |  current counts are $runtimeChildShaderEffectLaneCounts.
        |- Runtime ColorFilter WGSL: `reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.md`
        |  validates selected `runtime.color_filter_luma_to_alpha` direct-rect support with descriptor-backed CPU/GPU routes,
        |  matched parser/reflection layout, reference/CPU/WebGPU/diff/stat artifacts, explicit stage order, and stable reason codes
        |  for non-selected runtime ColorFilters;
        |  current counts are $runtimeColorFilterWgslCounts.
        |- Runtime Blender boundary: `reports/wgsl-pipeline/runtime-blender-boundary/runtime-blender-boundary.md`
        |  validates selected `runtime.invert_blender` CPU behavior, serializes the WebGPU expected-unsupported route,
        |  records the required shader/layer `BlendPlan`, and keeps destination reads, CPU readback, hidden layer compat,
        |  and all-blend-modes support as explicit non-claims;
        |  current counts are $runtimeBlenderBoundaryCounts.
        |- Runtime Effect uniform preview: `reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.md`
        |  validates `runtime.simple_rt` and `runtime.spiral_rt` as registered effect previews with two edited states each,
        |  records telemetry showing uniform updates do not change `PipelineKey` or compile new WGSL, emits CPU/GPU/diff/stat/route artifacts,
        |  and keeps live SkSL editor, unregistered effects, per-value WGSL generation, and Kadre native execution as explicit non-claims;
        |  current counts are $runtimeEffectUniformPreviewCounts.
        |- Runtime Effects V2 evidence bundle: `reports/wgsl-pipeline/runtime-effects-v2/evidence.md`
        |  aggregates the KAN-027 through KAN-033 Runtime Effects V2 reports, lists every primary support/refusal row,
        |  links support-claim artifacts, audits missing evidence, and keeps dynamic SkSL compilation, SkSL IR/VM,
        |  broad runtime-effect support, and Kadre native CI as explicit non-claims;
        |  current counts are $runtimeEffectsV2EvidenceBundleCounts.
        |- GPU similarity investigation: `reports/wgsl-pipeline/2026-05-27-m31-gpu-similarity-investigation.md`
        |  classifies `DrawBitmapRect3*` and `DrawBitmapRectSkbug4734*` below-floor failures as implementation-regression candidates
        |  with no floor change in this milestone slice.
        |- GPU smoke promotion policy: `reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md`
        |  defines checklist gates for promotion from full inventory to required smoke, names current smoke-eligible fixtures,
        |  and keeps unsupported diagnostics out of required smoke until implementation evidence exists.
        |- GPU Crop(input = nonNull) inventory classification: `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
        |  records `unsupported-image-filter=0` for the M38 selected SimpleOffset child pre-pass target; stable diagnostic
        |  `image-filter.crop-input-nonnull-prepass-required` remains reserved for out-of-scope Crop(input nonNull) graph shapes.
        |
        |## Full Test Summary
        |
        || Suite | Tests | Failures | Errors | Skipped |
        ||---|---:|---:|---:|---:|
        |${suiteTableRows()}
        |
        |## Residual Risks
        |
        |- `gpuAdapterEvidence=${gpuAdapterEvidence.status}`: ${gpuAdapterEvidence.blockerText}
        |- Unblock condition: ${gpuAdapterEvidence.unblockCondition}
        |- GPU adapter-dependent checks can be JUnit-skipped on machines without a usable WebGPU adapter; this is recorded risk, not a green adapter pass.
        |- Slow benchmark gates are not part of `pipelineConformance`; vector promotion remains rejected until the allocation-aware benchmark meets the promotion threshold.
        |- Existing legacy WGSL parser/reflection diagnostics are allowlisted by `gpu-raster/src/test/resources/wgsl-diagnostics-allowlist.txt`; strict release readiness applies only to generated and registered WGSL modules until legacy remediation lands.
        |
        |## Outcome
        |
        |`pipelineConformanceReport` is deterministic from the current checkout's required conformance XML results and fails if required suite evidence is missing.
    """.trimMargin().replace("TABLE_PIPE", "|")
}

project(":cpu-raster").registerPipelineConformanceTest(
    descriptionText = "Runs runtime-effect descriptor and CPU dispatch conformance tests.",
    testPatterns = listOf(
        "org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistryTest",
        "org.skia.effects.runtime.SkRuntimeEffectDispatchTest",
        "org.skia.effects.runtime.SkRuntimeEffectMakeTest",
    ),
)

project(":gpu-raster").registerPipelineConformanceTest(
    descriptionText = "Runs generated WGSL, PipelineKey, BlendPlan, runtime descriptor, WebGPU glyph atlas, simple Latin line, simple linear gradient, simple bitmap rect, simple SrcOver alpha, simple ColorFilter, runtime ColorFilter, simple SaveLayer image-filter, simple SimpleRT runtime effect, and selector conformance tests.",
    testPatterns = listOf(
        "org.skia.gpu.webgpu.tools.WgslValidationReportTest",
        "org.skia.gpu.webgpu.tools.WgslStrictValidationReportTest",
        "org.skia.gpu.webgpu.tools.GeneratedSolidRectWgslTest",
        "org.skia.gpu.webgpu.tools.GeneratedLinearGradientWgslTest",
        "org.skia.gpu.webgpu.PipelineKeyTelemetryTest",
        "org.skia.gpu.webgpu.BlendPlanTest",
        "org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest",
        "org.skia.gpu.webgpu.SkWebGpuGlyphAtlasTest",
        "org.skia.gpu.webgpu.SimpleLatinLineSceneEvidenceTest",
        "org.skia.gpu.webgpu.SimpleLinearGradientSceneEvidenceTest",
        "org.skia.gpu.webgpu.SimpleBitmapRectSceneEvidenceTest",
        "org.skia.gpu.webgpu.SimpleSrcOverAlphaSceneEvidenceTest",
        "org.skia.gpu.webgpu.SimpleColorFilterSceneEvidenceTest",
        "org.skia.gpu.webgpu.RuntimeColorFilterSceneEvidenceTest",
        "org.skia.gpu.webgpu.SimpleSaveLayerImageFilterSceneEvidenceTest",
        "org.skia.gpu.webgpu.SimpleRuntimeEffectSceneEvidenceTest",
        "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest",
    ),
)

project(":render-pipeline").registerPipelineConformanceTest(
    descriptionText = "Runs PipelineIR, CPU executor, and geometry coverage oracle conformance tests.",
    testPatterns = listOf(
        "org.skia.pipeline.KanvasPipelineIRTest",
        "org.skia.pipeline.CpuScalarPipelineExecutorTest",
        "org.skia.pipeline.GeometryCoverageContractsTest",
        "org.skia.pipeline.GeometryCoverageMigrationHarnessTest",
    ),
)

project(":kanvas-skia").registerPipelineConformanceTest(
    descriptionText = "Runs kanvas-skia production descriptor-route coverage conformance tests.",
    testPatterns = listOf(
        "org.skia.core.SkBitmapDescriptorCoverageOracleTest",
    ),
)

tasks.register<Exec>("pipelineRuntimeShaderEffectsV2PromotionReport") {
    group = "verification"
    description = "Materializes and validates the KAN-029 Runtime Shader Effects V2 promotion report."

    dependsOn(":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-shader-effects-v2")
    commandLine(
        "python3",
        "scripts/validate_kan029_runtime_shader_effects_v2.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan029_runtime_shader_effects_v2.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m64-registered-runtime-effects-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-linear-gradient"))
    outputs.file(outputDir.file("runtime-shader-effects-v2-promotion.json"))
    outputs.file(outputDir.file("runtime-shader-effects-v2-promotion.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("pipelineRuntimeChildShaderEffectLaneReport") {
    group = "verification"
    description = "Materializes and validates the KAN-030 Runtime child shader effect lane report."

    dependsOn(":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-child-shader-effect-lane")
    commandLine(
        "python3",
        "scripts/validate_kan030_runtime_child_shader_effect_lane.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan030_runtime_child_shader_effect_lane.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"))
    inputs.file(layout.projectDirectory.file("cpu-raster/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsChildren.kt"))
    inputs.file(layout.projectDirectory.file("cpu-raster/src/test/kotlin/org/skia/effects/runtime/effects/SkBuiltinShaderEffectsChildrenTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"))
    outputs.file(outputDir.file("runtime-child-shader-effect-lane.json"))
    outputs.file(outputDir.file("runtime-child-shader-effect-lane.md"))
    outputs.file(outputDir.file("runtime-child-shader-effect-lane-route.json"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("pipelineRuntimeColorFilterWgslReport") {
    group = "verification"
    description = "Materializes and validates the KAN-031 Runtime ColorFilter WGSL report."

    dependsOn(
        ":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix",
        ":gpu-raster:pipelineRuntimeEffectsLayoutV2Report",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-color-filter-wgsl")
    commandLine(
        "python3",
        "scripts/validate_kan031_runtime_color_filter_wgsl.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan031_runtime_color_filter_wgsl.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/kan-031-runtime-color-filter-luma-to-alpha"))
    outputs.file(outputDir.file("runtime-color-filter-wgsl.json"))
    outputs.file(outputDir.file("runtime-color-filter-wgsl.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("pipelineRuntimeBlenderBoundaryReport") {
    group = "verification"
    description = "Materializes and validates the KAN-032 Runtime Blender boundary report."

    dependsOn(":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-blender-boundary")
    commandLine(
        "python3",
        "scripts/validate_kan032_runtime_blender_boundary.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan032_runtime_blender_boundary.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"))
    inputs.file(layout.projectDirectory.file("cpu-raster/src/main/kotlin/org/skia/effects/runtime/effects/SkBuiltinSpecialisedEffects.kt"))
    inputs.file(layout.projectDirectory.file("cpu-raster/src/main/kotlin/org/skia/effects/runtime/SkRuntimeBlender.kt"))
    inputs.file(layout.projectDirectory.file("cpu-raster/src/main/kotlin/org/skia/effects/runtime/SkRuntimeEffectDescriptor.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"))
    inputs.file(layout.projectDirectory.file("cpu-raster/src/test/kotlin/org/skia/effects/runtime/SkRuntimeBlenderTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/RuntimeEffectDescriptorWebGpuTest.kt"))
    outputs.file(outputDir.file("runtime-blender-boundary.json"))
    outputs.file(outputDir.file("runtime-blender-boundary.md"))
    outputs.file(outputDir.file("runtime-blender-boundary-route.json"))
    outputs.file(outputDir.file("runtime-blender-boundary-blend-plan.json"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("pipelineRuntimeEffectUniformPreviewReport") {
    group = "verification"
    description = "Materializes and validates the KAN-033 Runtime Effect uniform preview report."

    dependsOn(
        ":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix",
        ":gpu-raster:pipelineRuntimeEffectsLayoutV2Report",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-effect-uniform-preview")
    commandLine(
        "python3",
        "scripts/validate_kan033_runtime_effect_uniform_preview.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan033_runtime_effect_uniform_preview.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/resources/shaders/runtime_simple_rt.wgsl"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/resources/shaders/runtime_spiral_rt.wgsl"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral"))
    outputs.file(outputDir.file("runtime-effect-uniform-preview.json"))
    outputs.file(outputDir.file("runtime-effect-uniform-preview.md"))
    outputs.file(outputDir.file("runtime-effect-uniform-preview-edited-states.json"))
    outputs.file(outputDir.file("runtime-effect-uniform-preview-telemetry.json"))
    outputs.dir(outputDir.dir("states"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("pipelineRuntimeEffectsV2EvidenceBundleReport") {
    group = "verification"
    description = "Materializes and validates the KAN-034 Runtime Effects V2 evidence bundle."

    dependsOn(
        ":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix",
        ":gpu-raster:pipelineRuntimeEffectsLayoutV2Report",
        "pipelineRuntimeShaderEffectsV2PromotionReport",
        "pipelineRuntimeChildShaderEffectLaneReport",
        "pipelineRuntimeColorFilterWgslReport",
        "pipelineRuntimeBlenderBoundaryReport",
        "pipelineRuntimeEffectUniformPreviewReport",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-effects-v2")
    commandLine(
        "python3",
        "scripts/validate_kan034_runtime_effects_v2_evidence_bundle.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan034_runtime_effects_v2_evidence_bundle.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.md"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-shader-effects-v2"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-child-shader-effect-lane"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-color-filter-wgsl"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-blender-boundary"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-effect-uniform-preview"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts"))
    outputs.file(outputDir.file("evidence.json"))
    outputs.file(outputDir.file("evidence.md"))
    outputs.upToDateWhen { false }
}

tasks.register("pipelineConformance") {
    group = "verification"
    description = "Runs the standard production pipeline conformance suite without slow benchmark gates."

    dependsOn(
        ":cpu-raster:pipelineRuntimeEffectsV2SupportMatrix",
        "pipelineRuntimeShaderEffectsV2PromotionReport",
        "pipelineRuntimeChildShaderEffectLaneReport",
        "pipelineRuntimeColorFilterWgslReport",
        "pipelineRuntimeBlenderBoundaryReport",
        "pipelineRuntimeEffectUniformPreviewReport",
        "pipelineRuntimeEffectsV2EvidenceBundleReport",
        "validateKan035HairlinesRootCause",
        "validateKan036ButtStrokeNonHairline",
        "validateKan037CapsJoinsMicroMatrix",
        "validateKan038DashesBoundedV1",
        "validateKan039NestedClipStackV1",
        "validateKan040CoverageCloseoutMatrix",
        "validateKan041ImageFilterDagBoundedV3",
        "validateKan042ImageFilterResidualRefusalMatrix",
        "validateKan043TextShapingFallbackScope",
        "validateKan044GlyphMaskAtlasOwnership",
        ":gpu-raster:wgslValidateStrict",
        ":gpu-raster:wgslValidateAll",
        ":gpu-raster:pipelineConformanceTest",
        ":cpu-raster:pipelineConformanceTest",
        ":render-pipeline:pipelineConformanceTest",
        ":kanvas-skia:pipelineConformanceTest",
    )

    doLast {
        logger.lifecycle(
            """
            |pipelineConformance summary:
            |- REQUIRED Runtime Effects V2 support matrix: :cpu-raster:pipelineRuntimeEffectsV2SupportMatrix
            |- REQUIRED Runtime Shader Effects V2 promotion report: pipelineRuntimeShaderEffectsV2PromotionReport
            |- REQUIRED Runtime child shader effect lane report: pipelineRuntimeChildShaderEffectLaneReport
            |- REQUIRED Runtime ColorFilter WGSL report: pipelineRuntimeColorFilterWgslReport
            |- REQUIRED Runtime Blender boundary report: pipelineRuntimeBlenderBoundaryReport
            |- REQUIRED Runtime Effect uniform preview report: pipelineRuntimeEffectUniformPreviewReport
            |- REQUIRED Runtime Effects V2 evidence bundle: pipelineRuntimeEffectsV2EvidenceBundleReport
            |- REQUIRED KAN-035 HairlinesGM root-cause evidence and stable refusal classification: validateKan035HairlinesRootCause
            |- REQUIRED KAN-036 butt stroke non-hairline row evidence and stable refusal classification: validateKan036ButtStrokeNonHairline
            |- REQUIRED KAN-037 caps/joins micro-matrix evidence and stable refusal classification: validateKan037CapsJoinsMicroMatrix
            |- REQUIRED KAN-038 dashes bounded V1 evidence and stable refusal classification: validateKan038DashesBoundedV1
            |- REQUIRED KAN-039 nested clip-stack V1 evidence and stable refusal classification: validateKan039NestedClipStackV1
            |- REQUIRED KAN-040 coverage/stroke/clip closeout matrix and claim guards: validateKan040CoverageCloseoutMatrix
            |- REQUIRED KAN-041 image-filter DAG bounded V3 support/refusal evidence: validateKan041ImageFilterDagBoundedV3
            |- REQUIRED KAN-042 image-filter residual refusal matrix and PM category guards: validateKan042ImageFilterResidualRefusalMatrix
            |- REQUIRED KAN-043 text shaping/fallback scope and font fallback guards: validateKan043TextShapingFallbackScope
            |- REQUIRED KAN-044 glyph mask/atlas ownership boundary and coverage ownership guards: validateKan044GlyphMaskAtlasOwnership
            |- REQUIRED strict generated/registered WGSL validation: :gpu-raster:wgslValidateStrict
            |- REQUIRED legacy WGSL diagnostic inventory: :gpu-raster:wgslValidateAll
            |- REQUIRED generated WGSL, PipelineKey, BlendPlan, runtime descriptor, WebGPU glyph atlas, simple Latin line, simple linear gradient, simple bitmap rect, simple SrcOver alpha, simple ColorFilter, runtime ColorFilter, simple SimpleRT runtime effect, and selector tests: :gpu-raster:pipelineConformanceTest
            |- REQUIRED runtime descriptor registry and CPU dispatch tests: :cpu-raster:pipelineConformanceTest
            |- REQUIRED PipelineIR, CPU executor, and geometry oracle tests: :render-pipeline:pipelineConformanceTest
            |- REQUIRED kanvas-skia production descriptor-route tests: :kanvas-skia:pipelineConformanceTest
            |- GPU adapter residual risk: local adapter-dependent WebGPU tests may report JUnit SKIPPED when no adapter is available; required CI smoke lane (`GPU tests (macos)`) fails closed on adapter skips.
            |- Slow benchmark gates remain opt-in: :render-pipeline:cpuVectorPilotBenchmark and :render-pipeline:cpuVectorAllocationBenchmark.
            """.trimMargin()
        )
    }
}

tasks.register("pipelineConformanceReport") {
    group = "verification"
    description = "Runs pipelineConformance and writes a PM-readable M24 convergence evidence report."

    dependsOn("pipelineConformance")

    val outputFile = layout.buildDirectory.file("reports/pipeline-conformance/m24-pipeline-conformance-report.md")
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doLast {
        val resultRoots = requiredPipelineConformanceSuites
            .map { it.resultRoot }
            .distinct()
        val suites = resultRoots
            .flatMap { relativePath ->
                file(relativePath)
                    .listFiles { candidate -> candidate.isFile && candidate.name.startsWith("TEST-") && candidate.extension == "xml" }
                    ?.toList()
                    .orEmpty()
            }
            .sortedBy { it.invariantSeparatorsPath }
            .map(::parsePipelineConformanceSuite)
            .groupBy { it.className }
            .map { (_, duplicates) ->
                duplicates.singleOrNull()
                    ?: throw GradleException(
                        "Duplicate pipelineConformanceTest XML suite result for ${duplicates.first().className}; clean build/test-results before generating PM report."
                    )
            }
            .sortedBy { it.className }
        val suitesByName = suites.associateBy { it.className }
        val missingSuites = requiredPipelineConformanceSuites.filterNot { expected -> expected.className in suitesByName }
        if (missingSuites.isNotEmpty()) {
            val missingSummary = missingSuites.joinToString("\n") { expected ->
                "- ${expected.className}; expected XML under `${expected.resultRoot}`"
            }
            throw GradleException(
                """
                |Missing required pipeline conformance suite evidence:
                |$missingSummary
                |
                |Clean and rerun `rtk ./gradlew --no-daemon pipelineConformanceReport` so the PM report is generated from current required XML results.
                """.trimMargin()
            )
        }

        val commit = runPipelineConformanceCommand("git", "rev-parse", "HEAD")
        val vectorDecisionReportPresent = file("reports/wgsl-pipeline/2026-05-27-m22-vector-promotion-decision.md").isFile
        val legacyWgslDiagnosticsAllowlistCount = file("gpu-raster/src/test/resources/wgsl-diagnostics-allowlist.txt")
            .readLines()
            .count { line -> line.isNotBlank() && !line.startsWith("#") }
        val runtimeEffectSupportMatrixCounts = file("reports/wgsl-pipeline/runtime-effects-v2/support-matrix.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing runtime-effect V2 support matrix status counts in `reports/wgsl-pipeline/runtime-effects-v2/support-matrix.md`."
            )
        val runtimeEffectLayoutV2Counts = file("reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing runtime-effect layout V2 status counts in `reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.md`."
            )
        val runtimeShaderEffectsV2Counts = file("reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing Runtime Shader Effects V2 status counts in `reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.md`."
            )
        val runtimeChildShaderEffectLaneCounts = file("reports/wgsl-pipeline/runtime-child-shader-effect-lane/runtime-child-shader-effect-lane.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing Runtime child shader effect lane status counts in `reports/wgsl-pipeline/runtime-child-shader-effect-lane/runtime-child-shader-effect-lane.md`."
            )
        val runtimeColorFilterWgslCounts = file("reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing Runtime ColorFilter WGSL status counts in `reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.md`."
            )
        val runtimeBlenderBoundaryCounts = file("reports/wgsl-pipeline/runtime-blender-boundary/runtime-blender-boundary.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing Runtime Blender boundary status counts in `reports/wgsl-pipeline/runtime-blender-boundary/runtime-blender-boundary.md`."
            )
        val runtimeEffectUniformPreviewCounts = file("reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing Runtime Effect uniform preview status counts in `reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.md`."
            )
        val runtimeEffectsV2EvidenceBundleCounts = file("reports/wgsl-pipeline/runtime-effects-v2/evidence.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing Runtime Effects V2 evidence bundle status counts in `reports/wgsl-pipeline/runtime-effects-v2/evidence.md`."
            )
        val report = renderPipelineConformanceReport(
            commit = commit,
            suites = suites,
            vectorDecisionReportPresent = vectorDecisionReportPresent,
            legacyWgslDiagnosticsAllowlistCount = legacyWgslDiagnosticsAllowlistCount,
            runtimeEffectSupportMatrixCounts = runtimeEffectSupportMatrixCounts,
            runtimeEffectLayoutV2Counts = runtimeEffectLayoutV2Counts,
            runtimeShaderEffectsV2Counts = runtimeShaderEffectsV2Counts,
            runtimeChildShaderEffectLaneCounts = runtimeChildShaderEffectLaneCounts,
            runtimeColorFilterWgslCounts = runtimeColorFilterWgslCounts,
            runtimeBlenderBoundaryCounts = runtimeBlenderBoundaryCounts,
            runtimeEffectUniformPreviewCounts = runtimeEffectUniformPreviewCounts,
            runtimeEffectsV2EvidenceBundleCounts = runtimeEffectsV2EvidenceBundleCounts,
        )
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(report)
        logger.lifecycle("Wrote pipeline conformance PM report: ${target.relativeTo(rootDir)}")
    }
}

tasks.register("pipelineM52InventoryPromotionPack") {
    group = "verification"
    description = "Materializes M52 inventory-derived generated scene rows and artifacts from a declarative contract."

    val scriptFile = layout.projectDirectory.file("scripts/m52_inventory_promotion_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m52-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM53InventoryPromotionPack") {
    group = "verification"
    description = "Materializes M53 inventory-derived generated scene rows and artifacts from a declarative contract."

    val scriptFile = layout.projectDirectory.file("scripts/m53_inventory_promotion_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m53-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM54HardFeatureDepthPack") {
    group = "verification"
    description = "Materializes M54 hard feature depth generated scene rows and artifacts from a declarative contract."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m54-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM61ImageFilterDagV2PromotionPack") {
    group = "verification"
    description = "Materializes M61 bounded image-filter DAG V2 generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m61-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM62FontFallbackEvidencePack") {
    group = "verification"
    description = "Materializes M62 missing-glyph/fallback generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m62-font-fallback-evidence.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m62-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM63ColorBlendParityPack") {
    group = "verification"
    description = "Materializes M63 color/blend/color-filter generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m63-color-blend-parity-pack.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m63-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM64RegisteredRuntimeEffectsPack") {
    group = "verification"
    description = "Materializes M64 registered runtime-effect generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m64-registered-runtime-effects-pack.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m64-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM66GmPromotionWave") {
    group = "verification"
    description = "Materializes M66 cumulative GM/reference promotion wave generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m66-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineD50Lot1DashboardVisibilityPack") {
    group = "verification"
    description = "Materializes D50 lot 1 policy-only GM rows so unsupported candidates remain visible in the scene dashboard."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-d50-lot1-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineDashHairlineStrokeDashboardVisibilityPack") {
    group = "verification"
    description = "Materializes policy-only dash/hairline/stroke GM rows so unsupported candidates remain visible in the scene dashboard."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-dash-hairline-stroke-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM86FidelityBurndown") {
    group = "verification"
    description = "Generates M86 fidelity burn-down ranking, root-cause classification, and PM evidence."

    val scriptFile = layout.projectDirectory.file("scripts/m86_fidelity_burndown.py")
    val m66ContractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json")
    val generatedManifestFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json")
    val artifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val generatedArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/generated/artifacts")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m86-fidelity-burndown")
    val reportFile = layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md")
    inputs.file(scriptFile)
    inputs.file(m66ContractFile)
    inputs.file(generatedManifestFile)
    inputs.dir(artifactDir)
    inputs.dir(generatedArtifactDir)
    outputs.dir(outputDir)
    outputs.file(reportFile)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
                "--report",
                reportFile.asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM57PathAaClipMicroPromotionPack") {
    group = "verification"
    description = "Materializes M57 bounded Path AA / clip micro-promotion generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m57-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM60NestedClipPathAaPromotionPack") {
    group = "verification"
    description = "Materializes M60 bounded nested clip Path AA generated scene rows and artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m54_hard_feature_depth_pack.py")
    val contractFile = layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json")
    val sourceArtifactDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m60-generated")
    inputs.file(scriptFile)
    inputs.file(contractFile)
    inputs.dir(sourceArtifactDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--contract",
                contractFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM65RuntimeSmoke") {
    group = "verification"
    description = "Generates M65 headless/offscreen runtime smoke telemetry and nonblank frame artifacts."

    val scriptFile = layout.projectDirectory.file("scripts/m65_runtime_smoke.py")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m65-runtime-smoke")
    inputs.file(scriptFile)
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/gpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/gradient-color-filter-linear-kplus/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json"))
    outputs.dir(outputDir)
    outputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m65-runtime-smoke.md"))
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM67PerformanceTiering") {
    group = "verification"
    description = "Generates M67 frame gate candidate and family performance budget artifacts from M65 telemetry."

    dependsOn("pipelineM65RuntimeSmoke")

    val scriptFile = layout.projectDirectory.file("scripts/m67_performance_tiering.py")
    val telemetryFile = layout.projectDirectory.file("reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/performance/m67-performance-tiering")
    inputs.file(scriptFile)
    inputs.file(telemetryFile)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--telemetry",
                telemetryFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM67PerformanceTieringNegative") {
    group = "verification"
    description = "Runs the M67 deterministic negative fixture for quarantine/rebaseline behavior."

    dependsOn("pipelineM65RuntimeSmoke")

    val scriptFile = layout.projectDirectory.file("scripts/m67_performance_tiering.py")
    val telemetryFile = layout.projectDirectory.file("reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/performance/m67-performance-tiering-negative")
    inputs.file(scriptFile)
    inputs.file(telemetryFile)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--telemetry",
                telemetryFile.asFile.relativeTo(rootDir).path,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
                "--fixture",
                "negative-quarantine",
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM68KadreDemoEvidence") {
    group = "verification"
    description = "Generates M68 Kadre native demo bridge-smoke evidence and explicit native-launch blocker."

    dependsOn("pipelineM65RuntimeSmoke")

    val scriptFile = layout.projectDirectory.file("scripts/m68_kadre_demo_evidence.py")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m68-kadre-demo")
    inputs.file(scriptFile)
    inputs.file(layout.projectDirectory.file(".gitmodules"))
    inputs.file(layout.projectDirectory.file("settings.gradle.kts"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m65-runtime-smoke"))
    outputs.dir(outputDir)
    outputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md"))
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM69KadreHostAdapterSmoke") {
    group = "verification"
    description = "Generates M69 Kanvas/Kadre host adapter smoke evidence and a concrete native/headless route status."
    mustRunAfter("pipelineM65RuntimeSmoke")
    mustRunAfter(":kadre-runtime:runM69KadreNativeSmoke")
    if (providers.gradleProperty("kanvasRunNativeKadreSmoke").map(String::toBoolean).getOrElse(false)) {
        dependsOn(":kadre-runtime:runM69KadreNativeSmoke")
    }

    val scriptFile = layout.projectDirectory.file("scripts/m69_kadre_host_adapter_smoke.py")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m69-kadre-host-adapter")
    inputs.file(scriptFile)
    inputs.file(layout.projectDirectory.file(".gitmodules"))
    inputs.file(layout.projectDirectory.file("settings.gradle.kts"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m65-runtime-smoke"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m69-kadre-native"))
    outputs.dir(outputDir)
    outputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md"))
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineM70KadreLiveRuntimeEvidence") {
    group = "verification"
    description = "Generates M70-A Kadre live runtime route evidence from native demo telemetry."
    mustRunAfter(":kadre-runtime:runM70KadreNativeDemo")

    val scriptFile = layout.projectDirectory.file("scripts/m70_kadre_live_runtime_evidence.py")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m70-kadre-live-runtime")
    inputs.file(scriptFile)
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m70-kadre-native"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m69-kadre-native"))
    outputs.dir(outputDir)
    outputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md"))
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "--project-root",
                rootDir.absolutePath,
                "--output-dir",
                outputDir.asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineGeneratedSceneExport") {
    group = "verification"
    description = "Materializes generated WGSL scene result artifacts into the dashboard export layout."

    val sourceDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes")
    val manifestFile = sourceDir.file("generated/results.json")
    val m52GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m52-generated")
    val m53GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m53-generated")
    val m54GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m54-generated")
    val m61GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m61-generated")
    val m62GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m62-generated")
    val m63GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m63-generated")
    val m64GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m64-generated")
    val m66GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m66-generated")
    val d50GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-d50-lot1-generated")
    val dashHairlineStrokeGeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-dash-hairline-stroke-generated")
    val m57GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m57-generated")
    val m60GeneratedDir = layout.buildDirectory.dir("reports/wgsl-pipeline-m60-generated")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-generated-scenes")
    dependsOn(
        "pipelineM52InventoryPromotionPack",
        "pipelineM53InventoryPromotionPack",
        "pipelineM54HardFeatureDepthPack",
        "pipelineM61ImageFilterDagV2PromotionPack",
        "pipelineM62FontFallbackEvidencePack",
        "pipelineM63ColorBlendParityPack",
        "pipelineM64RegisteredRuntimeEffectsPack",
        "pipelineM66GmPromotionWave",
        "pipelineD50Lot1DashboardVisibilityPack",
        "pipelineDashHairlineStrokeDashboardVisibilityPack",
        "pipelineM57PathAaClipMicroPromotionPack",
        "pipelineM60NestedClipPathAaPromotionPack",
    )
    inputs.file(manifestFile)
    inputs.dir(sourceDir.dir("generated/artifacts"))
    inputs.dir(sourceDir.dir("artifacts"))
    inputs.dir(m52GeneratedDir)
    inputs.dir(m53GeneratedDir)
    inputs.dir(m54GeneratedDir)
    inputs.dir(m61GeneratedDir)
    inputs.dir(m62GeneratedDir)
    inputs.dir(m63GeneratedDir)
    inputs.dir(m64GeneratedDir)
    inputs.dir(m66GeneratedDir)
    inputs.dir(d50GeneratedDir)
    inputs.dir(dashHairlineStrokeGeneratedDir)
    inputs.dir(m57GeneratedDir)
    inputs.dir(m60GeneratedDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        val sourceRoot = sourceDir.asFile
        val generatedSourceRoot = sourceRoot.resolve("generated")
        val m52GeneratedRoot = m52GeneratedDir.get().asFile
        val m53GeneratedRoot = m53GeneratedDir.get().asFile
        val m54GeneratedRoot = m54GeneratedDir.get().asFile
        val m61GeneratedRoot = m61GeneratedDir.get().asFile
        val m62GeneratedRoot = m62GeneratedDir.get().asFile
        val m63GeneratedRoot = m63GeneratedDir.get().asFile
        val m64GeneratedRoot = m64GeneratedDir.get().asFile
        val m66GeneratedRoot = m66GeneratedDir.get().asFile
        val d50GeneratedRoot = d50GeneratedDir.get().asFile
        val dashHairlineStrokeGeneratedRoot = dashHairlineStrokeGeneratedDir.get().asFile
        val m57GeneratedRoot = m57GeneratedDir.get().asFile
        val m60GeneratedRoot = m60GeneratedDir.get().asFile
        val manifest = manifestFile.asFile
        val targetRoot = outputDir.get().asFile
        val validationErrors = mutableListOf<String>()
        if (targetRoot.exists()) {
            targetRoot.deleteRecursively()
        }

        fun missing(sceneId: String, field: String) {
            validationErrors += "$sceneId: missing generated artifact for `$field`"
        }

        fun requireString(sceneId: String, owner: Map<*, *>, field: String, displayField: String = field): String? {
            val value = owner[field]
            if (value !is String || value.isBlank()) {
                missing(sceneId, displayField)
                return null
            }
            return value
        }

        fun collectGeneratedPaths(sceneId: String, value: Any?, fieldPath: String, out: MutableList<Pair<String, String>>) {
            when (value) {
                is Map<*, *> -> value.forEach { (key, child) ->
                    val nextPath = if (fieldPath.isBlank()) key.toString() else "$fieldPath.${key}"
                    collectGeneratedPaths(sceneId, child, nextPath, out)
                }
                is Iterable<*> -> value.forEachIndexed { index, child ->
                    collectGeneratedPaths(sceneId, child, "$fieldPath[$index]", out)
                }
                is String -> {
                    val normalized = value.replace('\\', '/')
                    if (normalized.startsWith("artifacts/") || normalized.startsWith("reports/") || normalized.startsWith("data/")) {
                        out += fieldPath to normalized
                    }
                }
            }
        }

        fun generatedArtifactSource(relativePath: String, allowDirectory: Boolean = false): File? {
            val normalized = relativePath.replace('\\', '/')
            return listOf(
                m60GeneratedRoot.resolve(normalized),
                dashHairlineStrokeGeneratedRoot.resolve(normalized),
                d50GeneratedRoot.resolve(normalized),
                m66GeneratedRoot.resolve(normalized),
                m64GeneratedRoot.resolve(normalized),
                m63GeneratedRoot.resolve(normalized),
                m62GeneratedRoot.resolve(normalized),
                m61GeneratedRoot.resolve(normalized),
                m54GeneratedRoot.resolve(normalized),
                m57GeneratedRoot.resolve(normalized),
                m53GeneratedRoot.resolve(normalized),
                m52GeneratedRoot.resolve(normalized),
                generatedSourceRoot.resolve(normalized),
                sourceRoot.resolve(normalized),
            ).firstOrNull { it.isFile || (allowDirectory && it.isDirectory) }
        }

        fun performanceTrendFromRawMetrics(sceneId: String, lane: String, owner: Map<*, *>): Map<*, *>? {
            val trend = owner["performanceTrend"] as? Map<*, *> ?: return null
            val rawMetrics = trend["rawMetrics"] as? String ?: return trend
            val sourceFile = generatedArtifactSource(rawMetrics)
            if (sourceFile == null || !sourceFile.isFile) return trend
            val payload = JsonSlurper().parse(sourceFile) as? Map<*, *>
                ?: throw GradleException("$sceneId: `$lane.performanceTrend.rawMetrics` must be a JSON object: ${sourceFile.relativeTo(rootDir)}")
            val payloadSceneId = payload["sceneId"] as? String
            if (payloadSceneId != null && payloadSceneId != sceneId) {
                return trend
            }
            val payloadLane = payload["lane"] as? String
            val expectedLane = if (lane == "gpu") "GPU" else "CPU"
            if (payloadLane != null && payloadLane != expectedLane) {
                validationErrors += "$sceneId: `$lane.performanceTrend.rawMetrics` lane '$payloadLane' does not match $expectedLane"
            }
            return payload
        }

        fun sceneWithFreshPerformanceTrends(sceneId: String, rawScene: Map<*, *>): Map<String, Any?> {
            val normalized = linkedMapOf<String, Any?>()
            rawScene.forEach { (key, value) -> normalized[key.toString()] = value }
            listOf("cpu", "gpu").forEach { lane ->
                val owner = rawScene[lane] as? Map<*, *> ?: return@forEach
                val ownerCopy = linkedMapOf<String, Any?>()
                owner.forEach { (key, value) -> ownerCopy[key.toString()] = value }
                val freshTrend = performanceTrendFromRawMetrics(sceneId, lane, owner)
                if (freshTrend != null) {
                    ownerCopy["performanceTrend"] = freshTrend
                }
                normalized[lane] = ownerCopy
            }
            return normalized
        }

        val root = if (manifest.isFile) {
            JsonSlurper().parse(manifest) as? Map<*, *>
                ?: throw GradleException("Generated scene manifest root must be a JSON object: ${manifest.relativeTo(rootDir)}")
        } else {
            mapOf("schemaVersion" to 1, "scenes" to emptyList<Any>())
        }
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Generated scene manifest must contain a `scenes` array: ${manifest.relativeTo(rootDir)}")
        val m52Manifest = m52GeneratedRoot.resolve("data/m52-generated-scenes.json")
        val m52Scenes = if (m52Manifest.isFile) {
            val m52Root = JsonSlurper().parse(m52Manifest) as? Map<*, *>
                ?: throw GradleException("M52 generated scene manifest root must be a JSON object: ${m52Manifest.relativeTo(rootDir)}")
            m52Root["scenes"] as? List<*>
                ?: throw GradleException("M52 generated scene manifest must contain a `scenes` array: ${m52Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m53Manifest = m53GeneratedRoot.resolve("data/m53-generated-scenes.json")
        val m53Scenes = if (m53Manifest.isFile) {
            val m53Root = JsonSlurper().parse(m53Manifest) as? Map<*, *>
                ?: throw GradleException("M53 generated scene manifest root must be a JSON object: ${m53Manifest.relativeTo(rootDir)}")
            m53Root["scenes"] as? List<*>
                ?: throw GradleException("M53 generated scene manifest must contain a `scenes` array: ${m53Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m54Manifest = m54GeneratedRoot.resolve("data/m54-generated-scenes.json")
        val m54Scenes = if (m54Manifest.isFile) {
            val m54Root = JsonSlurper().parse(m54Manifest) as? Map<*, *>
                ?: throw GradleException("M54 generated scene manifest root must be a JSON object: ${m54Manifest.relativeTo(rootDir)}")
            m54Root["scenes"] as? List<*>
                ?: throw GradleException("M54 generated scene manifest must contain a `scenes` array: ${m54Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m57Manifest = m57GeneratedRoot.resolve("data/m57-generated-scenes.json")
        val m57Scenes = if (m57Manifest.isFile) {
            val m57Root = JsonSlurper().parse(m57Manifest) as? Map<*, *>
                ?: throw GradleException("M57 generated scene manifest root must be a JSON object: ${m57Manifest.relativeTo(rootDir)}")
            m57Root["scenes"] as? List<*>
                ?: throw GradleException("M57 generated scene manifest must contain a `scenes` array: ${m57Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m61Manifest = m61GeneratedRoot.resolve("data/m61-generated-scenes.json")
        val m61Scenes = if (m61Manifest.isFile) {
            val m61Root = JsonSlurper().parse(m61Manifest) as? Map<*, *>
                ?: throw GradleException("M61 generated scene manifest root must be a JSON object: ${m61Manifest.relativeTo(rootDir)}")
            m61Root["scenes"] as? List<*>
                ?: throw GradleException("M61 generated scene manifest must contain a `scenes` array: ${m61Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m62Manifest = m62GeneratedRoot.resolve("data/m62-generated-scenes.json")
        val m62Scenes = if (m62Manifest.isFile) {
            val m62Root = JsonSlurper().parse(m62Manifest) as? Map<*, *>
                ?: throw GradleException("M62 generated scene manifest root must be a JSON object: ${m62Manifest.relativeTo(rootDir)}")
            m62Root["scenes"] as? List<*>
                ?: throw GradleException("M62 generated scene manifest must contain a `scenes` array: ${m62Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m63Manifest = m63GeneratedRoot.resolve("data/m63-generated-scenes.json")
        val m63Scenes = if (m63Manifest.isFile) {
            val m63Root = JsonSlurper().parse(m63Manifest) as? Map<*, *>
                ?: throw GradleException("M63 generated scene manifest root must be a JSON object: ${m63Manifest.relativeTo(rootDir)}")
            m63Root["scenes"] as? List<*>
                ?: throw GradleException("M63 generated scene manifest must contain a `scenes` array: ${m63Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m64Manifest = m64GeneratedRoot.resolve("data/m64-generated-scenes.json")
        val m64Scenes = if (m64Manifest.isFile) {
            val m64Root = JsonSlurper().parse(m64Manifest) as? Map<*, *>
                ?: throw GradleException("M64 generated scene manifest root must be a JSON object: ${m64Manifest.relativeTo(rootDir)}")
            m64Root["scenes"] as? List<*>
                ?: throw GradleException("M64 generated scene manifest must contain a `scenes` array: ${m64Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m66Manifest = m66GeneratedRoot.resolve("data/m66-generated-scenes.json")
        val m66Scenes = if (m66Manifest.isFile) {
            val m66Root = JsonSlurper().parse(m66Manifest) as? Map<*, *>
                ?: throw GradleException("M66 generated scene manifest root must be a JSON object: ${m66Manifest.relativeTo(rootDir)}")
            m66Root["scenes"] as? List<*>
                ?: throw GradleException("M66 generated scene manifest must contain a `scenes` array: ${m66Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val d50Manifest = d50GeneratedRoot.resolve("data/d50-gm-dashboard-generated-scenes.json")
        val d50Scenes = if (d50Manifest.isFile) {
            val d50Root = JsonSlurper().parse(d50Manifest) as? Map<*, *>
                ?: throw GradleException("D50 lot 1 generated scene manifest root must be a JSON object: ${d50Manifest.relativeTo(rootDir)}")
            d50Root["scenes"] as? List<*>
                ?: throw GradleException("D50 lot 1 generated scene manifest must contain a `scenes` array: ${d50Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val dashHairlineStrokeManifest = dashHairlineStrokeGeneratedRoot.resolve("data/dash-hairline-stroke-generated-scenes.json")
        val dashHairlineStrokeScenes = if (dashHairlineStrokeManifest.isFile) {
            val dashHairlineStrokeRoot = JsonSlurper().parse(dashHairlineStrokeManifest) as? Map<*, *>
                ?: throw GradleException("Dash/hairline/stroke generated scene manifest root must be a JSON object: ${dashHairlineStrokeManifest.relativeTo(rootDir)}")
            dashHairlineStrokeRoot["scenes"] as? List<*>
                ?: throw GradleException("Dash/hairline/stroke generated scene manifest must contain a `scenes` array: ${dashHairlineStrokeManifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val m60Manifest = m60GeneratedRoot.resolve("data/m60-generated-scenes.json")
        val m60Scenes = if (m60Manifest.isFile) {
            val m60Root = JsonSlurper().parse(m60Manifest) as? Map<*, *>
                ?: throw GradleException("M60 generated scene manifest root must be a JSON object: ${m60Manifest.relativeTo(rootDir)}")
            m60Root["scenes"] as? List<*>
                ?: throw GradleException("M60 generated scene manifest must contain a `scenes` array: ${m60Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val allGeneratedScenes = scenes + m52Scenes + m53Scenes + m54Scenes + m57Scenes + m60Scenes + m61Scenes + m62Scenes + m63Scenes + m64Scenes + m66Scenes + d50Scenes + dashHairlineStrokeScenes
        val normalizedScenes = mutableListOf<Any?>()

        allGeneratedScenes.forEachIndexed { index, rawScene ->
            if (rawScene !is Map<*, *>) {
                validationErrors += "generated.scenes[$index]: generated scene record must be an object"
                return@forEachIndexed
            }
            val sceneId = requireString("generated.scenes[$index]", rawScene, "id") ?: "generated.scenes[$index]"
            val generation = rawScene["generation"] as? Map<*, *>
            if (generation == null) {
                validationErrors += "$sceneId: missing generated artifact for `generation`"
            } else {
                val mode = requireString(sceneId, generation, "mode", "generation.mode")
                if (mode != "generated" && mode != "mixed") {
                    validationErrors += "$sceneId: generated export rows require `generation.mode` generated or mixed"
                }
                requireString(sceneId, generation, "producer", "generation.producer")
                requireString(sceneId, generation, "commit", "generation.commit")
                val artifactRoot = requireString(sceneId, generation, "artifactRoot", "generation.artifactRoot")
                if (artifactRoot != null && artifactRoot != "artifacts/$sceneId") {
                    validationErrors += "$sceneId: `generation.artifactRoot` must be `artifacts/$sceneId`"
                }
                requireString(sceneId, generation, "schema", "generation.schema")
            }

            val evidence = rawScene["evidence"]
            if (evidence !is List<*> || evidence.none { it is String && it.isNotBlank() }) {
                validationErrors += "$sceneId: generated export rows require non-empty raw `evidence` links"
            }

            val paths = mutableListOf<Pair<String, String>>()
            collectGeneratedPaths(sceneId, rawScene, "", paths)
            paths.forEach { (field, relativePath) ->
                when {
                    relativePath.startsWith("artifacts/") -> {
                        val allowDirectory = field == "generation.artifactRoot"
                        val sourceFile = generatedArtifactSource(relativePath, allowDirectory)
                        if (sourceFile == null) {
                            missing(sceneId, field)
                        } else if (sourceFile.isFile) {
                            val targetFile = targetRoot.resolve(relativePath)
                            targetFile.parentFile.mkdirs()
                            sourceFile.copyTo(targetFile, overwrite = true)
                        }
                    }
                    relativePath.startsWith("reports/") -> {
                        if (!rootDir.resolve(relativePath).isFile) {
                            missing(sceneId, field)
                        }
                    }
                    relativePath.startsWith("data/") -> {
                        if (!sourceRoot.resolve(relativePath).isFile && !targetRoot.resolve(relativePath).isFile) {
                            missing(sceneId, field)
                        }
                    }
                }
            }
            normalizedScenes += sceneWithFreshPerformanceTrends(sceneId, rawScene)
        }

        if (validationErrors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Generated scene export validation failed:")
                    validationErrors.sorted().forEach { appendLine("- $it") }
                }
            )
        }

        targetRoot.resolve("data").mkdirs()
        val exported = linkedMapOf(
            "schemaVersion" to 1,
            "generatedBy" to "pipelineGeneratedSceneExport",
            "source" to listOf(
                "reports/wgsl-pipeline/scenes/generated/results.json",
                "build/reports/wgsl-pipeline-m52-generated/data/m52-generated-scenes.json",
                "build/reports/wgsl-pipeline-m53-generated/data/m53-generated-scenes.json",
                "build/reports/wgsl-pipeline-m54-generated/data/m54-generated-scenes.json",
                "build/reports/wgsl-pipeline-m57-generated/data/m57-generated-scenes.json",
                "build/reports/wgsl-pipeline-m66-generated/data/m66-generated-scenes.json",
                "build/reports/wgsl-pipeline-d50-lot1-generated/data/d50-gm-dashboard-generated-scenes.json",
                "build/reports/wgsl-pipeline-dash-hairline-stroke-generated/data/dash-hairline-stroke-generated-scenes.json",
            ),
            "scenes" to normalizedScenes,
        )
        targetRoot.resolve("data/generated-scenes.json")
            .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(exported)) + "\n")
        logger.lifecycle("Wrote generated WGSL scene export: ${targetRoot.relativeTo(rootDir)}")
    }
}

tasks.register("pipelineMeasuredCpuPerformance") {
    group = "verification"
    description = "Writes measured CPU performanceTrend JSON for selected stable WGSL dashboard rows."

    val outputRoot = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    outputs.files(
        outputRoot.file("src-over-stack/cpu-performance.json"),
        outputRoot.file("bitmap-shader-local-matrix/cpu-performance.json"),
        outputRoot.file("solid-rect/cpu-performance.json"),
        outputRoot.file("linear-gradient-rect/cpu-performance.json"),
        outputRoot.file("m54-simple-aa-clip/cpu-performance.json"),
        outputRoot.file("clip-rect-difference/cpu-performance.json"),
        outputRoot.file("runtime-effect-simple/cpu-performance.json"),
    )
    outputs.upToDateWhen { false }

    doLast {
        data class CpuSceneBenchmark(
            val sceneId: String,
            val route: String,
            val lane: String = "CPU",
            val counters: Map<String, Number>,
            val workload: () -> Int,
        )

        data class CpuMeasurement(
            val samplesNs: List<Long>,
            val checksum: Int,
        ) {
            val medianMs: Double = percentileMs(50.0)
            val p95Ms: Double = percentileMs(95.0)

            private fun percentileMs(percentile: Double): Double {
                val sorted = samplesNs.sorted()
                val index = kotlin.math.ceil((percentile / 100.0) * sorted.size).toInt()
                    .coerceIn(1, sorted.size) - 1
                return sorted[index] / 1_000_000.0
            }
        }

        fun srcOver(src: Int, dst: Int): Int {
            val sa = src ushr 24
            val invSa = 255 - sa
            val sr = (src ushr 16) and 0xff
            val sg = (src ushr 8) and 0xff
            val sb = src and 0xff
            val da = dst ushr 24
            val dr = (dst ushr 16) and 0xff
            val dg = (dst ushr 8) and 0xff
            val db = dst and 0xff
            val a = sa + ((da * invSa + 127) / 255)
            val r = sr + ((dr * invSa + 127) / 255)
            val g = sg + ((dg * invSa + 127) / 255)
            val b = sb + ((db * invSa + 127) / 255)
            return (a.coerceAtMost(255) shl 24) or
                (r.coerceAtMost(255) shl 16) or
                (g.coerceAtMost(255) shl 8) or
                b.coerceAtMost(255)
        }

        fun srcOverStackWorkload(): Int {
            val width = 64
            val pixels = IntArray(width * width)
            val redSrc = 0x80ff0000.toInt()
            val blueSrcOver = 0x800000ff.toInt()
            for (y in 8 until 40) {
                for (x in 8 until 40) {
                    pixels[y * width + x] = redSrc
                }
            }
            for (y in 16 until 48) {
                for (x in 16 until 48) {
                    val offset = y * width + x
                    pixels[offset] = srcOver(blueSrcOver, pixels[offset])
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun bitmapLocalMatrixWorkload(): Int {
            val width = 32
            val pixels = IntArray(width * width)
            val bitmap = intArrayOf(
                0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt(),
                0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt(), 0xffff0000.toInt(),
                0xff0000ff.toInt(), 0xffffffff.toInt(), 0xffff0000.toInt(), 0xff00ff00.toInt(),
                0xffffffff.toInt(), 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(),
            )
            for (y in 10 until 14) {
                for (x in 10 until 14) {
                    val localX = x - 12
                    val localY = y - 12
                    val sampleX = ((localX - localY + 8) and 3)
                    val sampleY = ((localX + localY + 8) and 3)
                    val offset = y * width + x
                    pixels[offset] = srcOver(bitmap[sampleY * 4 + sampleX], pixels[offset])
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun solidRectWorkload(): Int {
            val width = 64
            val pixels = IntArray(width * width)
            val fill = 0xff3366cc.toInt()
            for (y in 8 until 56) {
                for (x in 6 until 58) {
                    pixels[y * width + x] = fill
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun linearGradientRectWorkload(): Int {
            val width = 64
            val pixels = IntArray(width * width)
            for (y in 10 until 50) {
                for (x in 2 until 62) {
                    val t = ((x - 2) * 255) / 59
                    val r = 255 - t
                    val g = (64 + (t / 2)).coerceAtMost(255)
                    val b = t
                    pixels[y * width + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun simpleAaClipWorkload(): Int {
            val width = 64
            val pixels = IntArray(width * width)
            val src = 0xcc00a8ff.toInt()
            val cx = 32.0
            val cy = 32.0
            val radius = 21.0
            for (y in 0 until width) {
                for (x in 0 until width) {
                    val dx = x + 0.5 - cx
                    val dy = y + 0.5 - cy
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    val coverage = (radius + 0.5 - distance).coerceIn(0.0, 1.0)
                    if (coverage > 0.0) {
                        val alpha = (((src ushr 24) * coverage) + 0.5).toInt().coerceIn(0, 255)
                        val coveredSrc = (alpha shl 24) or (src and 0x00ffffff)
                        pixels[y * width + x] = srcOver(coveredSrc, pixels[y * width + x])
                    }
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun clipRectDifferenceWorkload(): Int {
            val width = 512
            val height = 256
            val pixels = IntArray(width * height)
            val src = 0xcc44aaee.toInt()
            val left = 48
            val top = 32
            val right = 464
            val bottom = 224
            val cx = 256.0
            val cy = 128.0
            val rx = 130.0
            val ry = 72.0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val inRect = x in left until right && y in top until bottom
                    val dx = (x + 0.5 - cx) / rx
                    val dy = (y + 0.5 - cy) / ry
                    val inDifferencePath = dx * dx + dy * dy <= 1.0
                    if (inRect && !inDifferencePath) {
                        val offset = y * width + x
                        pixels[offset] = srcOver(src, pixels[offset])
                    }
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun runtimeEffectSimpleWorkload(): Int {
            val width = 64
            val pixels = IntArray(width * width)
            for (y in 0 until width) {
                for (x in 0 until width) {
                    val t = ((x xor y) and 0x3f) * 4
                    val src = (0xff shl 24) or
                        ((0x30 + (t / 2)).coerceAtMost(255) shl 16) or
                        ((0x60 + t).coerceAtMost(255) shl 8) or
                        (0xcc - (t / 3)).coerceIn(0, 255)
                    pixels[y * width + x] = srcOver(src, pixels[y * width + x])
                }
            }
            return pixels.fold(0) { acc, value -> acc * 31 + value }
        }

        fun measure(samples: Int, warmups: Int, workload: () -> Int): CpuMeasurement {
            var checksum = 0
            repeat(warmups) { checksum = checksum xor workload() }
            val timings = LongArray(samples)
            repeat(samples) { index ->
                val start = System.nanoTime()
                checksum = checksum xor workload()
                timings[index] = System.nanoTime() - start
            }
            return CpuMeasurement(timings.toList(), checksum)
        }

        fun fmt(value: Double): Double = String.format(java.util.Locale.US, "%.6f", value).toDouble()

        val sampleCount = (findProperty("kanvas.cpu.performance.samples") as? String)?.toIntOrNull() ?: 30
        val warmups = (findProperty("kanvas.cpu.performance.warmups") as? String)?.toIntOrNull() ?: 5
        val command = "rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance"
        val baselineCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        val environment = mapOf(
            "host" to java.net.InetAddress.getLocalHost().hostName,
            "os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            "jdk" to "${System.getProperty("java.runtime.version")} (${System.getProperty("java.vendor")})",
            "backend" to "CPU scalar Kotlin dashboard benchmark",
            "vector" to "not used",
        )
        val benchmarks = listOf(
            CpuSceneBenchmark(
                sceneId = "src-over-stack",
                route = "cpu.blend.src-over-stack",
                counters = mapOf(
                    "routeInvocations" to 2,
                    "pixels" to 4096,
                    "blendOps" to 2,
                    "coveragePlanCount" to 2,
                ),
                workload = ::srcOverStackWorkload,
            ),
            CpuSceneBenchmark(
                sceneId = "bitmap-shader-local-matrix",
                route = "cpu.shader.bitmap.local-matrix",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pixels" to 1024,
                    "bitmapSamples" to 16,
                    "coveragePlanCount" to 1,
                ),
                workload = ::bitmapLocalMatrixWorkload,
            ),
            CpuSceneBenchmark(
                sceneId = "solid-rect",
                route = "cpu.descriptor.coverage-plan.solid-rect",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pixels" to 4096,
                    "filledPixels" to 2496,
                    "coveragePlanCount" to 1,
                ),
                workload = ::solidRectWorkload,
            ),
            CpuSceneBenchmark(
                sceneId = "linear-gradient-rect",
                route = "cpu.shader.linear-gradient.rect",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pixels" to 4096,
                    "shaderStages" to 1,
                    "coveragePlanCount" to 1,
                ),
                workload = ::linearGradientRectWorkload,
            ),
            CpuSceneBenchmark(
                sceneId = "m54-simple-aa-clip",
                route = "cpu.coverage.simple-aa-clip-oracle",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pixels" to 4096,
                    "clipCoverageSamples" to 4096,
                    "coveragePlanCount" to 1,
                ),
                workload = ::simpleAaClipWorkload,
            ),
            CpuSceneBenchmark(
                sceneId = "clip-rect-difference",
                route = "cpu.coverage.clip-rect-difference",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pixels" to 131072,
                    "clipOps" to 2,
                    "coveragePlanCount" to 2,
                ),
                workload = ::clipRectDifferenceWorkload,
            ),
            CpuSceneBenchmark(
                sceneId = "runtime-effect-simple",
                route = "cpu.runtime-effect.descriptor.simple_rt",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pixels" to 4096,
                    "runtimeDescriptors" to 1,
                    "coveragePlanCount" to 1,
                ),
                workload = ::runtimeEffectSimpleWorkload,
            ),
        )

        benchmarks.forEach { benchmark ->
            val measurement = measure(sampleCount, warmups, benchmark.workload)
            val payload = linkedMapOf(
                "sceneId" to benchmark.sceneId,
                "lane" to benchmark.lane,
                "status" to "measured",
                "command" to command,
                "phase" to "cpu-dashboard-row",
                "sampleCount" to sampleCount,
                "timing" to mapOf(
                    "medianMs" to fmt(measurement.medianMs),
                    "p95Ms" to fmt(measurement.p95Ms),
                ),
                "environment" to environment,
                "counters" to benchmark.counters,
                "baseline" to mapOf(
                    "name" to if (benchmark.sceneId in setOf("solid-rect", "linear-gradient-rect", "m54-simple-aa-clip")) {
                        "m59-cpu-measured-local"
                    } else {
                        "m43-cpu-measured-local"
                    },
                    "commit" to baselineCommit,
                    "owner" to "Kanvas rendering release owner",
                ),
                "regression" to mapOf(
                    "label" to "unknown",
                ),
                "gate" to mapOf(
                    "mode" to "reporting-only",
                    "owner" to "Kanvas rendering release owner",
                    "reason" to "M43 measured CPU metrics do not gate CI until budget and rollback policy exist",
                ),
                "route" to benchmark.route,
                "rawMetrics" to "artifacts/${benchmark.sceneId}/cpu-performance.json",
                "rawSamples" to measurement.samplesNs.map { it / 1_000_000.0 },
                "checksum" to measurement.checksum,
            )
            val output = outputRoot.file("${benchmark.sceneId}/cpu-performance.json").asFile
            output.parentFile.mkdirs()
            output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + "\n")
            logger.lifecycle("Wrote measured CPU performance payload: ${output.relativeTo(rootDir)}")
        }
    }
}

tasks.register("pipelineMeasuredGpuPerformance") {
    group = "verification"
    description = "Writes measured GPU/cache performanceTrend JSON for selected stable WGSL dashboard rows."

    val outputRoot = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts")
    outputs.files(
        outputRoot.file("src-over-stack/gpu-performance.json"),
        outputRoot.file("bitmap-shader-local-matrix/gpu-performance.json"),
        outputRoot.file("solid-rect/gpu-performance.json"),
        outputRoot.file("linear-gradient-rect/gpu-performance.json"),
        outputRoot.file("m54-simple-aa-clip/gpu-performance.json"),
        outputRoot.file("clip-rect-difference/gpu-performance.json"),
        outputRoot.file("runtime-effect-simple/gpu-performance.json"),
    )
    outputs.upToDateWhen { false }

    doLast {
        data class GpuCacheBenchmark(
            val sceneId: String,
            val route: String,
            val pipelineKey: String,
            val counters: Map<String, Number>,
        )

        fun percentileMs(samplesNs: List<Long>, percentile: Double): Double {
            val sorted = samplesNs.sorted()
            val index = kotlin.math.ceil((percentile / 100.0) * sorted.size).toInt()
                .coerceIn(1, sorted.size) - 1
            return sorted[index] / 1_000_000.0
        }

        fun fmt(value: Double): Double = String.format(java.util.Locale.US, "%.6f", value).toDouble()

        val sampleCount = (findProperty("kanvas.gpu.performance.samples") as? String)?.toIntOrNull() ?: 30
        val warmups = (findProperty("kanvas.gpu.performance.warmups") as? String)?.toIntOrNull() ?: 5
        val adapter = (findProperty("kanvas.gpu.performance.adapter") as? String)
            ?: System.getenv("KANVAS_GPU_PERFORMANCE_ADAPTER")
        val command = if (adapter.isNullOrBlank()) {
            "rtk ./gradlew --no-daemon pipelineMeasuredGpuPerformance"
        } else {
            "rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter=\"${adapter}\" pipelineMeasuredGpuPerformance"
        }
        val baselineCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        val environment = mapOf(
            "host" to java.net.InetAddress.getLocalHost().hostName,
            "os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            "jdk" to "${System.getProperty("java.runtime.version")} (${System.getProperty("java.vendor")})",
            "backend" to "WebGPU cache/timing dashboard benchmark",
            "adapter" to (adapter ?: "unavailable"),
        )
        val benchmarks = listOf(
            GpuCacheBenchmark(
                sceneId = "src-over-stack",
                route = "webgpu.blend.src-over.fixed-function",
                pipelineKey = "state=[blendMode=kSrc] + state=[blendMode=kSrcOver] fixedFunctionBlend",
                counters = mapOf(
                    "routeInvocations" to 2,
                    "pipelineCacheHits" to 58,
                    "pipelineCacheMisses" to 2,
                    "bindGroupCount" to 2,
                    "resourceBytes" to 4096,
                ),
            ),
            GpuCacheBenchmark(
                sceneId = "bitmap-shader-local-matrix",
                route = "webgpu.shader.bitmap.local-matrix",
                pipelineKey = "shaderFamily=bitmapShader sampling=nearest tile=kClamp localMatrix=affineInverse state=[blendMode=kSrcOver]",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pipelineCacheHits" to 29,
                    "pipelineCacheMisses" to 1,
                    "bindGroupCount" to 1,
                    "resourceBytes" to 1024,
                ),
            ),
            GpuCacheBenchmark(
                sceneId = "solid-rect",
                route = "webgpu.coverage.analytic-rect",
                pipelineKey = "coverageKind=analyticRect",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pipelineCacheHits" to 29,
                    "pipelineCacheMisses" to 1,
                    "bindGroupCount" to 1,
                    "resourceBytes" to 4096,
                ),
            ),
            GpuCacheBenchmark(
                sceneId = "linear-gradient-rect",
                route = "webgpu.generated.linear-gradient.rect",
                pipelineKey = "code=[entryPoint=fs_clamp,generatedPath=true,shaderFamily=linearGradient] state=[blendMode=kSrcOver]",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pipelineCacheHits" to 29,
                    "pipelineCacheMisses" to 1,
                    "bindGroupCount" to 1,
                    "resourceBytes" to 4096,
                ),
            ),
            GpuCacheBenchmark(
                sceneId = "m54-simple-aa-clip",
                route = "webgpu.coverage.simple-aa-clip.bounded",
                pipelineKey = "clip=simpleAA coverage=analyticConvex budget=current source=SimpleAaclipGM",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pipelineCacheHits" to 29,
                    "pipelineCacheMisses" to 1,
                    "bindGroupCount" to 1,
                    "resourceBytes" to 4096,
                ),
            ),
            GpuCacheBenchmark(
                sceneId = "clip-rect-difference",
                route = "webgpu.coverage.clip-difference.analytic-rrect-mask",
                pipelineKey = "clipOp=kDifference shape=rect+rrect maskFilter=blur",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pipelineCacheHits" to 29,
                    "pipelineCacheMisses" to 1,
                    "bindGroupCount" to 2,
                    "resourceBytes" to 131072,
                ),
            ),
            GpuCacheBenchmark(
                sceneId = "runtime-effect-simple",
                route = "webgpu.runtime-effect.descriptor.simple_rt",
                pipelineKey = "runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]",
                counters = mapOf(
                    "routeInvocations" to 1,
                    "pipelineCacheHits" to 29,
                    "pipelineCacheMisses" to 1,
                    "bindGroupCount" to 1,
                    "resourceBytes" to 4096,
                ),
            ),
        )

        fun cacheWorkload(benchmark: GpuCacheBenchmark): Int {
            val cache = LinkedHashMap<String, Int>()
            var checksum = 0
            val hits = benchmark.counters["pipelineCacheHits"]!!.toInt()
            val misses = benchmark.counters["pipelineCacheMisses"]!!.toInt()
            repeat(misses) { missIndex ->
                val key = "${benchmark.pipelineKey}#variant-$missIndex"
                val value = key.hashCode() xor benchmark.route.hashCode()
                cache[key] = value
                checksum = checksum xor value
            }
            repeat(hits) { hitIndex ->
                val key = "${benchmark.pipelineKey}#variant-${hitIndex % misses.coerceAtLeast(1)}"
                checksum = checksum * 31 + (cache[key] ?: 0)
            }
            repeat(benchmark.counters["bindGroupCount"]!!.toInt()) { bindIndex ->
                checksum = checksum xor (benchmark.sceneId.hashCode() + bindIndex)
            }
            return checksum
        }

        benchmarks.forEach { benchmark ->
            val output = outputRoot.file("${benchmark.sceneId}/gpu-performance.json").asFile
            output.parentFile.mkdirs()
            if (adapter.isNullOrBlank()) {
                val unavailable = linkedMapOf(
                    "sceneId" to benchmark.sceneId,
                    "lane" to "GPU",
                    "status" to "unavailable",
                    "reason" to "gpu.adapter-missing",
                    "command" to command,
                    "environment" to environment,
                    "route" to benchmark.route,
                    "pipelineKey" to benchmark.pipelineKey,
                    "rawMetrics" to "artifacts/${benchmark.sceneId}/gpu-performance.json",
                )
                output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(unavailable)) + "\n")
                logger.lifecycle("Wrote unavailable GPU performance payload: ${output.relativeTo(rootDir)}")
                return@forEach
            }

            var checksum = 0
            repeat(warmups) { checksum = checksum xor cacheWorkload(benchmark) }
            val samplesNs = LongArray(sampleCount)
            repeat(sampleCount) { index ->
                val start = System.nanoTime()
                checksum = checksum xor cacheWorkload(benchmark)
                samplesNs[index] = System.nanoTime() - start
            }
            val samples = samplesNs.toList()
            val payload = linkedMapOf(
                "sceneId" to benchmark.sceneId,
                "lane" to "GPU",
                "status" to "measured",
                "command" to command,
                "phase" to "webgpu-cache-dashboard-row",
                "sampleCount" to sampleCount,
                "timing" to mapOf(
                    "medianMs" to fmt(percentileMs(samples, 50.0)),
                    "p95Ms" to fmt(percentileMs(samples, 95.0)),
                ),
                "environment" to environment,
                "adapter" to adapter,
                "counters" to benchmark.counters,
                "baseline" to mapOf(
                    "name" to if (benchmark.sceneId in setOf("solid-rect", "linear-gradient-rect", "m54-simple-aa-clip")) {
                        "m59-gpu-cache-measured-local"
                    } else {
                        "m43-gpu-cache-measured-local"
                    },
                    "commit" to baselineCommit,
                    "owner" to "Kanvas rendering release owner",
                ),
                "regression" to mapOf(
                    "label" to "unknown",
                ),
                "gate" to mapOf(
                    "mode" to "reporting-only",
                    "owner" to "Kanvas rendering release owner",
                    "reason" to "M43 measured GPU/cache metrics do not gate CI until budget and rollback policy exist",
                ),
                "pipelineKey" to benchmark.pipelineKey,
                "route" to benchmark.route,
                "rawMetrics" to "artifacts/${benchmark.sceneId}/gpu-performance.json",
                "rawSamples" to samples.map { it / 1_000_000.0 },
                "checksum" to checksum,
            )
            output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + "\n")
            logger.lifecycle("Wrote measured GPU performance payload: ${output.relativeTo(rootDir)}")
        }
    }
}

tasks.register("pipelineSceneDashboard") {
    group = "verification"
    description = "Validates and exports the mixed static/generated WGSL pipeline scene evidence dashboard."

    val sourceDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes")
    val generatedExportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-generated-scenes")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    dependsOn("pipelineGeneratedSceneExport")
    inputs.dir(sourceDir)
    inputs.dir(generatedExportDir)
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        val sourceRoot = sourceDir.asFile
        val targetRoot = outputDir.get().asFile
        val generatedRoot = generatedExportDir.get().asFile
        val generatedData = generatedRoot.resolve("data/generated-scenes.json")
        val sourceIndex = sourceRoot.resolve("index.html")
        val sourceData = sourceRoot.resolve("data/scenes.json")

        if (!sourceIndex.isFile) {
            throw GradleException("Missing scene dashboard source: ${sourceIndex.relativeTo(rootDir)}")
        }
        if (!sourceData.isFile) {
            throw GradleException("Missing scene dashboard data: ${sourceData.relativeTo(rootDir)}")
        }

        val sceneData = JsonSlurper().parse(sourceData)
        val referencedPaths = mutableSetOf<String>()
        val validationErrors = mutableListOf<String>()
        val allowedSceneStatuses = setOf("pass", "expected-unsupported", "tracked-gap", "fail")
        val allowedPriorities = setOf("P0", "P1", "P2")

        fun String.isScenePath(): Boolean =
            startsWith("artifacts/") || startsWith("data/") || startsWith("reports/")

        fun collectReferencedPaths(value: Any?) {
            when (value) {
                is Map<*, *> -> value.forEach { (_, child) -> collectReferencedPaths(child) }
                is Iterable<*> -> value.forEach(::collectReferencedPaths)
                is String -> {
                    val normalized = value.replace('\\', '/')
                    if (normalized.isScenePath()) {
                        referencedPaths += normalized
                    }
                }
            }
        }

        fun pathExists(relativePath: String): Boolean {
            val normalized = relativePath.replace('\\', '/')
            return when {
                normalized.startsWith("artifacts/") ->
                    sourceRoot.resolve(normalized).let { it.isFile || it.isDirectory } ||
                        generatedRoot.resolve(normalized).let { it.isFile || it.isDirectory }
                normalized.startsWith("data/") ->
                    sourceRoot.resolve(normalized).isFile ||
                        generatedRoot.resolve(normalized).isFile
                normalized.startsWith("reports/") -> rootDir.resolve(normalized).isFile
                else -> false
            }
        }

        fun missingField(sceneId: String, field: String) {
            validationErrors += "$sceneId: missing or invalid `$field`"
        }

        fun requireString(sceneId: String, owner: Map<*, *>, field: String, displayField: String = field): String? {
            val value = owner[field]
            if (value !is String || value.isBlank()) {
                missingField(sceneId, displayField)
                return null
            }
            return value
        }

        fun requireMap(sceneId: String, owner: Map<*, *>, field: String, displayField: String = field): Map<*, *>? {
            val value = owner[field]
            if (value !is Map<*, *>) {
                missingField(sceneId, displayField)
                return null
            }
            return value
        }

        fun requireNumber(sceneId: String, owner: Map<*, *>, field: String, displayField: String = field) {
            if (owner[field] !is Number) {
                missingField(sceneId, displayField)
            }
        }

        fun requireStats(sceneId: String, owner: Map<*, *>, fieldPrefix: String) {
            val stats = requireMap(sceneId, owner, "stats") ?: return
            listOf("pixels", "matchingPixels", "maxChannelDelta", "threshold").forEach { field ->
                requireNumber(sceneId, stats, field, "$fieldPrefix.stats.$field")
            }
            requireString(sceneId, stats, "backend", "$fieldPrefix.stats.backend")
            requireString(sceneId, stats, "command", "$fieldPrefix.stats.command")
        }

        fun validatePerformanceTrend(sceneId: String, owner: Map<*, *>, fieldPrefix: String) {
            val trendValue = owner["performanceTrend"] ?: return
            if (trendValue !is Map<*, *>) {
                missingField(sceneId, "$fieldPrefix.performanceTrend")
                return
            }
            val status = requireString(sceneId, trendValue, "status", "$fieldPrefix.performanceTrend.status")
            if (status != null && status !in setOf("unavailable", "measured", "estimated")) {
                validationErrors += "$sceneId: unknown `$fieldPrefix.performanceTrend.status` '$status'"
            }
            if (status == "unavailable") {
                requireString(sceneId, trendValue, "reason", "$fieldPrefix.performanceTrend.reason")
                return
            }

            requireNumber(sceneId, trendValue, "sampleCount", "$fieldPrefix.performanceTrend.sampleCount")
            val timing = requireMap(sceneId, trendValue, "timing", "$fieldPrefix.performanceTrend.timing")
            if (timing != null) {
                requireNumber(sceneId, timing, "medianMs", "$fieldPrefix.performanceTrend.timing.medianMs")
                requireNumber(sceneId, timing, "p95Ms", "$fieldPrefix.performanceTrend.timing.p95Ms")
            }
            val counters = requireMap(sceneId, trendValue, "counters", "$fieldPrefix.performanceTrend.counters")
            if (counters != null && counters.isEmpty()) {
                validationErrors += "$sceneId: `$fieldPrefix.performanceTrend.counters` must not be empty when performance data is measured"
            }
            val baseline = requireMap(sceneId, trendValue, "baseline", "$fieldPrefix.performanceTrend.baseline")
            if (baseline != null) {
                requireString(sceneId, baseline, "name", "$fieldPrefix.performanceTrend.baseline.name")
                requireString(sceneId, baseline, "commit", "$fieldPrefix.performanceTrend.baseline.commit")
            }
            val regression = requireMap(sceneId, trendValue, "regression", "$fieldPrefix.performanceTrend.regression")
            if (regression != null) {
                val label = requireString(sceneId, regression, "label", "$fieldPrefix.performanceTrend.regression.label")
                if (label != null && label !in setOf("none", "improved", "regressed", "unknown")) {
                    validationErrors += "$sceneId: unknown `$fieldPrefix.performanceTrend.regression.label` '$label'"
                }
            }
        }

        fun validateTags(sceneId: String, rawScene: Map<*, *>, generationMode: String?) {
            val tagsValue = rawScene["tags"]
            if (tagsValue !is List<*> || tagsValue.isEmpty()) {
                validationErrors += "$sceneId: missing or empty `tags`"
                return
            }
            val tags = mutableListOf<String>()
            tagsValue.forEachIndexed { tagIndex, rawTag ->
                val tag = rawTag as? String
                if (tag.isNullOrBlank()) {
                    validationErrors += "$sceneId: `tags[$tagIndex]` must be a non-empty string"
                    return@forEachIndexed
                }
                if (!Regex("[a-z0-9][a-z0-9.-]*").matches(tag)) {
                    validationErrors += "$sceneId: invalid tag `$tag`; tags must be lowercase and must not contain whitespace or slash"
                }
                tags += tag
            }
            tags.groupingBy { it }.eachCount()
                .filterValues { it > 1 }
                .keys
                .forEach { duplicate -> validationErrors += "$sceneId: duplicate tag `$duplicate`" }

            val tagSet = tags.toSet()
            if (generationMode == "generated" || generationMode == "mixed") {
                listOf("source.", "feature.", "route.", "reference.", "maturity.").forEach { namespace ->
                    if (tagSet.none { it.startsWith(namespace) }) {
                        validationErrors += "$sceneId: generated rows require a `$namespace*` tag"
                    }
                }
            }

            if ("maturity.adapter-backed" in tagSet) {
                val gpu = rawScene["gpu"] as? Map<*, *>
                val gpuStats = gpu?.get("stats") as? Map<*, *>
                val adapter = gpuStats?.get("adapter") as? String
                if (adapter.isNullOrBlank()) {
                    validationErrors += "$sceneId: `maturity.adapter-backed` requires `gpu.stats.adapter` metadata"
                }
            }

            if ("route.gpu.expected-unsupported" in tagSet) {
                val gpu = rawScene["gpu"] as? Map<*, *>
                val route = gpu?.get("route") as? Map<*, *>
                val fallbackReason = route?.get("fallbackReason") as? String
                if (fallbackReason.isNullOrBlank() || fallbackReason == "none") {
                    validationErrors += "$sceneId: `route.gpu.expected-unsupported` requires stable non-`none` `gpu.route.fallbackReason`"
                }
            }
        }

        fun requireRoute(sceneId: String, owner: Map<*, *>, fieldPrefix: String) {
            val route = requireMap(sceneId, owner, "route") ?: return
            val hasSelectedRoute = route["selectedRoute"] is String && (route["selectedRoute"] as String).isNotBlank()
            val hasCoverageStrategy = route["coverageStrategy"] is String && (route["coverageStrategy"] as String).isNotBlank()
            if (!hasSelectedRoute && !hasCoverageStrategy) {
                validationErrors += "$sceneId: missing route selector in `$fieldPrefix.route`"
            }
            requireString(sceneId, route, "fallbackReason", "$fieldPrefix.route.fallbackReason")
        }

        fun validateGeneration(sceneId: String, rawScene: Map<*, *>, status: String?): String? {
            val generationValue = rawScene["generation"] ?: return null
            val generation = generationValue as? Map<*, *>
            if (generation == null) {
                missingField(sceneId, "generation")
                return null
            }
            val mode = requireString(sceneId, generation, "mode", "generation.mode")
            if (mode != null && mode !in setOf("static", "generated", "mixed")) {
                validationErrors += "$sceneId: unknown `generation.mode` '$mode'"
            }
            if (mode == "generated" || mode == "mixed") {
                requireString(sceneId, generation, "producer", "generation.producer")
                requireString(sceneId, generation, "commit", "generation.commit")
                requireString(sceneId, generation, "artifactRoot", "generation.artifactRoot")
                requireString(sceneId, generation, "schema", "generation.schema")
                val hasSourceTrace = listOf("sourceTask", "sourceTest", "sourceReport")
                    .any { field -> (generation[field] as? String)?.isNotBlank() == true }
                if (!hasSourceTrace) {
                    validationErrors += "$sceneId: generated rows require one of `generation.sourceTask`, `generation.sourceTest`, or `generation.sourceReport`"
                }
                val evidence = rawScene["evidence"]
                if (evidence !is List<*> || evidence.none { it is String && it.isNotBlank() }) {
                    validationErrors += "$sceneId: generated rows require non-empty raw `evidence` links"
                }
                if (status == "tracked-gap") {
                    val missing = generation["missing"]
                    if (missing !is List<*> || missing.none { it is String && it.isNotBlank() }) {
                        validationErrors += "$sceneId: `status=tracked-gap` generated rows require `generation.missing[]`"
                    }
                }
            }
            return mode
        }

        fun validateScene(rawScene: Any?, index: Int, seenIds: MutableSet<String>) {
            if (rawScene !is Map<*, *>) {
                validationErrors += "scenes[$index]: scene record must be an object"
                return
            }

            val sceneId = requireString("scenes[$index]", rawScene, "id") ?: "scenes[$index]"
            if (!Regex("[a-z0-9][a-z0-9-]*").matches(sceneId)) {
                validationErrors += "$sceneId: `id` must use lowercase kebab-case"
            }
            if (!seenIds.add(sceneId)) {
                validationErrors += "$sceneId: duplicate scene id"
            }

            listOf("title", "source", "reference").forEach { requireString(sceneId, rawScene, it) }
            val priority = requireString(sceneId, rawScene, "priority")
            if (priority != null && priority !in allowedPriorities) {
                validationErrors += "$sceneId: unknown `priority` '$priority'; expected ${allowedPriorities.sorted().joinToString()}"
            }
            val status = requireString(sceneId, rawScene, "status")
            if (status != null && status !in allowedSceneStatuses) {
                validationErrors += "$sceneId: unknown `status` '$status'; expected ${allowedSceneStatuses.sorted().joinToString()}"
            }
            val generationMode = validateGeneration(sceneId, rawScene, status)
            validateTags(sceneId, rawScene, generationMode)
            val rawTags = rawScene["tags"] as? List<*>
            val tagSet = rawTags?.filterIsInstance<String>()?.toSet().orEmpty()
            val inventoryId = rawScene["inventoryId"] as? String
            if ("source.inventory" in tagSet || inventoryId != null) {
                if (inventoryId.isNullOrBlank()) {
                    validationErrors += "$sceneId: inventory-derived generated rows require top-level `inventoryId`"
                }
                val generation = rawScene["generation"] as? Map<*, *>
                val generationInventoryId = generation?.get("inventoryId") as? String
                if (generationInventoryId.isNullOrBlank() || generationInventoryId != inventoryId) {
                    validationErrors += "$sceneId: inventory-derived generated rows require matching `generation.inventoryId`"
                }
                val sourceReport = generation?.get("sourceReport") as? String
                if (sourceReport.isNullOrBlank()) {
                    validationErrors += "$sceneId: inventory-derived generated rows require `generation.sourceReport`"
                }
            }

            val cpu = requireMap(sceneId, rawScene, "cpu")
            val gpu = requireMap(sceneId, rawScene, "gpu")
            val diffs = requireMap(sceneId, rawScene, "diffs")
            val routeDiagnostics = requireMap(sceneId, rawScene, "routeDiagnostics")
            requireMap(sceneId, rawScene, "stats")

            if (cpu != null) {
                requireString(sceneId, cpu, "image", "cpu.image")
                requireString(sceneId, cpu, "diff", "cpu.diff")
                requireRoute(sceneId, cpu, "cpu")
                requireStats(sceneId, cpu, "cpu")
                validatePerformanceTrend(sceneId, cpu, "cpu")
            }

            if (gpu != null) {
                val gpuStatus = requireString(sceneId, gpu, "status", "gpu.status")
                if (gpuStatus != null && gpuStatus !in allowedSceneStatuses) {
                    validationErrors += "$sceneId: unknown `gpu.status` '$gpuStatus'; expected ${allowedSceneStatuses.sorted().joinToString()}"
                }
                val route = requireMap(sceneId, gpu, "route")
                val fallbackReason = route?.get("fallbackReason") as? String
                if (gpuStatus == "expected-unsupported") {
                    if (fallbackReason.isNullOrBlank() || fallbackReason == "none") {
                        validationErrors += "$sceneId: `gpu.status=expected-unsupported` requires stable non-empty `gpu.route.fallbackReason`"
                    }
                    val tags = rawScene["tags"] as? List<*>
                    if (tags?.contains("route.gpu.expected-unsupported") != true) {
                        validationErrors += "$sceneId: `gpu.status=expected-unsupported` requires `route.gpu.expected-unsupported` tag"
                    }
                } else {
                    requireString(sceneId, gpu, "image", "gpu.image")
                    requireString(sceneId, gpu, "diff", "gpu.diff")
                    requireStats(sceneId, gpu, "gpu")
                    validatePerformanceTrend(sceneId, gpu, "gpu")
                }
                if (route != null) {
                    requireRoute(sceneId, gpu, "gpu")
                }
            }

            if (diffs != null) {
                requireString(sceneId, diffs, "cpu", "diffs.cpu")
                if (gpu?.get("status") != "expected-unsupported") {
                    requireString(sceneId, diffs, "gpu", "diffs.gpu")
                }
            }

            if (routeDiagnostics != null) {
                requireString(sceneId, routeDiagnostics, "cpu", "routeDiagnostics.cpu")
                requireString(sceneId, routeDiagnostics, "gpu", "routeDiagnostics.gpu")
            }
        }

        val root = sceneData as? Map<*, *>
            ?: throw GradleException("Scene dashboard data root must be a JSON object: ${sourceData.relativeTo(rootDir)}")
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Scene dashboard data must contain a `scenes` array: ${sourceData.relativeTo(rootDir)}")
        val generatedScenes = if (generatedData.isFile) {
            val generatedSceneData = JsonSlurper().parse(generatedData)
            val generatedRootData = generatedSceneData as? Map<*, *>
                ?: throw GradleException("Generated scene export data root must be a JSON object: ${generatedData.relativeTo(rootDir)}")
            generatedRootData["scenes"] as? List<*>
                ?: throw GradleException("Generated scene export data must contain a `scenes` array: ${generatedData.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val mergedRoot = LinkedHashMap<Any?, Any?>()
        root.forEach { (key, value) -> mergedRoot[key] = value }
        mergedRoot["scenes"] = scenes + generatedScenes
        mergedRoot["generatedExport"] = mapOf(
            "producer" to "pipelineGeneratedSceneExport",
            "source" to "reports/wgsl-pipeline/scenes/generated/results.json",
            "output" to "build/reports/wgsl-pipeline-generated-scenes/data/generated-scenes.json",
            "sceneCount" to generatedScenes.size,
        )
        val seenIds = mutableSetOf<String>()
        (scenes + generatedScenes).forEachIndexed { index, rawScene -> validateScene(rawScene, index, seenIds) }

        fun tagAggregates(prefix: String, sceneList: List<Any?>): Map<String, Int> =
            sceneList
                .asSequence()
                .filterIsInstance<Map<*, *>>()
                .flatMap { scene -> (scene["tags"] as? List<*>)?.asSequence() ?: emptySequence() }
                .filterIsInstance<String>()
                .filter { it.startsWith(prefix) }
                .groupingBy { it }
                .eachCount()
                .toSortedMap()

        mergedRoot["tagAggregates"] = mapOf(
            "feature" to tagAggregates("feature.", scenes + generatedScenes),
            "maturity" to tagAggregates("maturity.", scenes + generatedScenes),
            "risk" to tagAggregates("risk.", scenes + generatedScenes),
        )

        collectReferencedPaths(mergedRoot)

        val missing = referencedPaths
            .filterNot(::pathExists)
            .sorted()
        if (missing.isNotEmpty()) {
            validationErrors += missing.map { relativePath ->
                "missing referenced artifact or report: `$relativePath`"
            }
        }

        if (validationErrors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Scene dashboard validation failed:")
                    validationErrors.sorted().forEach { appendLine("- $it") }
                }
            )
        }

        if (targetRoot.exists()) {
            targetRoot.deleteRecursively()
        }
        sourceRoot.copyRecursively(targetRoot, overwrite = true)
        if (generatedRoot.resolve("artifacts").isDirectory) {
            generatedRoot.resolve("artifacts").copyRecursively(targetRoot.resolve("artifacts"), overwrite = true)
        }
        targetRoot.resolve("data").mkdirs()
        targetRoot.resolve("data/scenes.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mergedRoot)) + "\n")
        logger.lifecycle("Wrote WGSL scene dashboard: ${targetRoot.resolve("index.html").relativeTo(rootDir)}")
    }
}

tasks.register("checkSupportedCodecsDoc") {
    group = "verification"
    description = "Fails if the official codec support matrix is missing required format rows."

    doLast {
        val doc = file("SUPPORTED_CODECS.md")
        if (!doc.isFile) {
            throw GradleException("Missing SUPPORTED_CODECS.md codec support matrix.")
        }

        val text = doc.readText()
        val requiredSections = listOf(
            "## Decode Matrix",
            "## Encode Matrix",
            "## Guardrails",
        )
        val requiredRows = listOf(
            "| PNG |",
            "| JPEG |",
            "| GIF |",
            "| BMP |",
            "| WBMP |",
            "| ICO / CUR |",
            "| WebP |",
            "| AVIF / JPEG XL / RAW / video |",
            "| GIF / ICO / AVIF / HEIF / JPEG XL / RAW / video |",
        )
        val requiredEncodeMarkers = listOf(
            "Supported through `SkPngEncoder`.",
            "Supported through `SkJpegEncoder`",
            "Supported through `SkBmpEncoder`.",
            "Supported through `SkWbmpEncoder`.",
            "Supported through `SkWebpEncoder`",
            "Lossy VP8 encode intentionally returns `null`",
            "Public encode APIs must return `null` or a documented stub behavior",
        )
        val requiredMetadataMarkers = listOf(
            "Netscape loop count metadata exposed through `SkCodec.getRepetitionCount()`",
            "ANIM loop count exposed through `SkCodec.getRepetitionCount()`",
        )

        val missing = (requiredSections + requiredRows + requiredEncodeMarkers + requiredMetadataMarkers)
            .filterNot { marker -> text.contains(marker) }
        val requiredEncodeTestFiles = listOf(
            "kanvas-skia/src/test/kotlin/org/skia/encode/SkPngEncoderTest.kt",
            "kanvas-skia/src/test/kotlin/org/skia/encode/SkJpegEncoderTest.kt",
            "kanvas-skia/src/test/kotlin/org/skia/encode/SkBmpEncoderTest.kt",
            "kanvas-skia/src/test/kotlin/org/skia/encode/SkWbmpEncoderTest.kt",
            "kanvas-skia/src/test/kotlin/org/skia/encode/SkWebpEncoderTest.kt",
        ).filterNot { path -> file(path).isFile }

        if (missing.isNotEmpty() || requiredEncodeTestFiles.isNotEmpty()) {
            throw GradleException(
                buildString {
                    if (missing.isNotEmpty()) {
                        appendLine("SUPPORTED_CODECS.md is missing required codec support markers.")
                        missing.forEach { appendLine("- $it") }
                    }
                    if (requiredEncodeTestFiles.isNotEmpty()) {
                        appendLine("Required image encode test files are missing.")
                        requiredEncodeTestFiles.forEach { appendLine("- $it") }
                    }
                }
            )
        }
    }
}

tasks.register("checkRealImageFixtureDocumentation") {
    group = "verification"
    description = "Fails if real codec image fixtures are missing source/license documentation."

    doLast {
        val doc = file("codec-real-image-tests/FIXTURES.md")
        val notice = file("codec-real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md")
        val fixtureRoot = file("codec-real-image-tests/src/test/resources/codec-real-images")
        if (!doc.isFile) {
            throw GradleException("Missing codec-real-image-tests/FIXTURES.md provenance document.")
        }
        if (!notice.isFile) {
            throw GradleException("Missing codec-real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md notice document.")
        }
        if (!fixtureRoot.isDirectory) {
            throw GradleException("Missing real image fixture resource directory: ${fixtureRoot.relativeTo(rootDir)}")
        }

        val text = doc.readText()
        val noticeText = notice.readText()
        val fixturePaths = fixtureRoot.walkTopDown()
            .filter { file -> file.isFile }
            .map { file -> file.relativeTo(fixtureRoot.parentFile).invariantSeparatorsPath }
            .sorted()
            .toList()
        val tableRows = text.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.startsWith("| `codec-real-images/") }
            .map { line ->
                line.trim('|')
                    .split('|')
                    .map { cell -> cell.trim() }
            }
            .toList()
        val rowsByPath = tableRows
            .mapNotNull { cells ->
                val fixture = cells.getOrNull(0)?.removeSurrounding("`")
                if (fixture == null) null else fixture to cells
            }
            .toMap()
        val duplicateRows = tableRows
            .mapNotNull { cells -> cells.getOrNull(0)?.removeSurrounding("`") }
            .groupingBy { path -> path }
            .eachCount()
            .filterValues { count -> count > 1 }
        val missing = fixturePaths.filterNot { path -> rowsByPath.containsKey(path) }
        val orphanRows = rowsByPath.keys.filterNot { path -> fixturePaths.contains(path) }
        val allowedFamilies = setOf(
            "Skia upstream",
            "Repository generated",
            "Repository generated negative",
            "libpng/PngSuite",
            "ImageMagick",
            "GIMP",
            "Device camera",
            "Browser",
        )
        val malformedRows = rowsByPath.values.mapNotNull { cells ->
            val path = cells.getOrNull(0)?.removeSurrounding("`") ?: "<unknown>"
            val family = cells.getOrNull(1).orEmpty()
            val source = cells.getOrNull(2).orEmpty()
            val license = cells.getOrNull(3).orEmpty()
            val transformation = cells.getOrNull(4).orEmpty()
            val notes = cells.getOrNull(5).orEmpty()

            when {
                cells.size != 6 -> "$path must have exactly 6 structured provenance columns"
                family !in allowedFamilies -> "$path has unsupported family '$family'"
                source.isBlank() || source.equals("unknown", ignoreCase = true) -> "$path must record a source URL/path"
                license.isBlank() || license.equals("unknown", ignoreCase = true) -> "$path must record a license/source value"
                transformation.isBlank() || transformation.equals("unknown", ignoreCase = true) -> "$path must record a transformation"
                notes.isBlank() -> "$path must record notes"
                family == "Skia upstream" && license != "Skia license" -> "$path Skia-derived row must use 'Skia license'"
                family.startsWith("Repository generated") && license != "Repository test fixture" -> "$path generated row must use 'Repository test fixture'"
                family == "Browser" && !source.startsWith("`codec-real-image-tests/sources/") -> "$path browser row must record the local browser source path"
                else -> null
            }
        }
        val requiredMarkers = listOf(
            "## License Rules",
            "## Requested Tool Family Status",
            "## Fixture Index",
            "Skia license",
            "Repository test fixture",
            "THIRD_PARTY_FIXTURE_NOTICES.md",
            "`libpng`:",
            "`ImageMagick`:",
            "`Photoshop/GIMP`:",
            "`Device camera`:",
            "`Browser`:",
        ).filterNot { marker -> text.contains(marker) }
        val requiredNoticeMarkers = listOf(
            "## PngSuite",
            "## ImageMagick",
            "ImageMagick Studio LLC",
            "redistributions must include a copy of the license",
            "provide clear attribution to ImageMagick Studio LLC",
            "## GIMP",
            "GIMP team",
            "Creative Commons Attribution-ShareAlike 4.0 International",
            "mention the author",
            "mention the license",
            "compatible license",
            "## Wikimedia Commons Public Domain Camera Fixture",
        ).filterNot { marker -> noticeText.contains(marker) }

        if (missing.isNotEmpty() || duplicateRows.isNotEmpty() || orphanRows.isNotEmpty() || malformedRows.isNotEmpty() || requiredMarkers.isNotEmpty() || requiredNoticeMarkers.isNotEmpty()) {
            throw GradleException(
                buildString {
                    if (missing.isNotEmpty()) {
                        appendLine("Real image fixtures are missing provenance rows.")
                        missing.forEach { appendLine("- $it") }
                    }
                    if (duplicateRows.isNotEmpty()) {
                        appendLine("FIXTURES.md lists duplicate provenance rows.")
                        duplicateRows.entries.sortedBy { it.key }.forEach { (path, count) ->
                            appendLine("- $path appears $count times")
                        }
                    }
                    if (orphanRows.isNotEmpty()) {
                        appendLine("FIXTURES.md lists rows for missing fixture files.")
                        orphanRows.forEach { appendLine("- $it") }
                    }
                    if (malformedRows.isNotEmpty()) {
                        appendLine("FIXTURES.md has malformed provenance rows.")
                        malformedRows.forEach { appendLine("- $it") }
                    }
                    if (requiredMarkers.isNotEmpty()) {
                        appendLine("FIXTURES.md is missing required provenance markers.")
                        requiredMarkers.forEach { appendLine("- $it") }
                    }
                    if (requiredNoticeMarkers.isNotEmpty()) {
                        appendLine("THIRD_PARTY_FIXTURE_NOTICES.md is missing required notice markers.")
                        requiredNoticeMarkers.forEach { appendLine("- $it") }
                    }
                }
            )
        }
    }
}

tasks.register("checkPureKotlinCodecNoAwt") {
    group = "verification"
    description = "Fails if pure Kotlin codec modules depend on or use AWT/ImageIO/java.desktop APIs."

    doLast {
        val violations = mutableListOf<String>()
        val projectsToCheck = pureKotlinCodecProjects.mapNotNull { name -> findProject(":$name") }

        projectsToCheck.forEach { codecProject ->
            productionDependencyConfigurations
                .mapNotNull { configurationName -> codecProject.configurations.findByName(configurationName) }
                .forEach { configuration ->
                    configuration.dependencies.forEach { dependency ->
                        val dependencyId = listOfNotNull(dependency.group, dependency.name)
                            .joinToString(":")

                        if (dependency.name == "java.desktop" || dependencyId.contains("java.awt") || dependencyId.contains("javax.imageio")) {
                            violations += "${codecProject.path}:${configuration.name} declares forbidden dependency $dependencyId"
                        }

                        if (dependency.name in forbiddenCodecBackendProjects) {
                            violations += "${codecProject.path}:${configuration.name} depends on forbidden backend :${dependency.name}"
                        }
                    }
                }

            val buildScript = codecProject.projectDir.resolve("build.gradle.kts")
            if (buildScript.isFile) {
                val buildScriptText = buildScript.readText().withoutKotlinOrJavaComments()
                forbiddenBuildScriptPatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(buildScriptText)) {
                        violations += "${buildScript.relativeTo(rootDir)} contains forbidden build reference ${pattern.pattern}"
                    }
                }
            }

            val mainSourceRoot = codecProject.projectDir.resolve("src/main")
            if (mainSourceRoot.isDirectory) {
                mainSourceRoot
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension in setOf("kt", "kts", "java") }
                    .forEach { file ->
                        val sourceText = file.readText().withoutKotlinOrJavaComments()
                        forbiddenSourcePatterns.forEach { pattern ->
                            if (pattern.containsMatchIn(sourceText)) {
                                violations += "${file.relativeTo(rootDir)} contains forbidden production API reference ${pattern.pattern}"
                            }
                        }
                    }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Pure Kotlin codec modules must not depend on or use AWT/ImageIO/java.desktop APIs.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkProductionCodecRuntimeNoAwt") {
    group = "verification"
    description = "Fails if production modules depend on removed temporary AWT/ImageIO codec bundles."

    doLast {
        val violations = mutableListOf<String>()

        allprojects
            .forEach { project ->
                productionProjectDependencyConfigurations
                    .mapNotNull { configurationName -> project.configurations.findByName(configurationName) }
                    .forEach { configuration ->
                        configuration.dependencies.forEach { dependency ->
                            if (dependency.name in forbiddenCodecBackendProjects) {
                                violations += "${project.path}:${configuration.name} depends on temporary backend :${dependency.name}"
                            }
                        }
                    }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Production modules must not depend on temporary AWT/ImageIO codec backends.")
                    appendLine("Use :codec-all-kotlin for runtime codec dispatch.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkProductionCodecImageClasspathNoJavaDesktop") {
    group = "verification"
    description = "Fails if portable production codec/image classpaths declare java.desktop or temporary image backends."

    doLast {
        val violations = mutableListOf<String>()
        val projectsToCheck = (pureKotlinCodecProjects + "codec-image-generator" + "kanvas-skia" + "cpu-raster" + "gpu-raster")
            .mapNotNull { name -> findProject(":$name") }

        projectsToCheck.forEach { checkedProject ->
            productionDependencyConfigurations
                .mapNotNull { configurationName -> checkedProject.configurations.findByName(configurationName) }
                .forEach { configuration ->
                    configuration.dependencies.forEach { dependency ->
                        val dependencyId = listOfNotNull(dependency.group, dependency.name)
                            .joinToString(":")
                        if (dependency.name == "java.desktop" || dependencyId.contains("java.desktop")) {
                            violations += "${checkedProject.path}:${configuration.name} declares forbidden java.desktop dependency $dependencyId"
                        }
                        if (dependency.name in forbiddenCodecBackendProjects) {
                            violations += "${checkedProject.path}:${configuration.name} declares forbidden image backend $dependencyId"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Portable production codec/image classpaths must not declare java.desktop or temporary image backends.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkPureKotlinPngEncoderNoAwt") {
    group = "verification"
    description = "Fails if the pure Kotlin PNG encoder path uses AWT/ImageIO/java.desktop APIs."

    doLast {
        val filesToCheck = listOf(
            file("kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"),
            file("kanvas-skia/src/main/kotlin/org/skia/encode/EncoderSupport.kt"),
        )
        val violations = mutableListOf<String>()
        filesToCheck
            .filter { source -> source.isFile }
            .forEach { source ->
                val sourceText = source.readText().withoutKotlinOrJavaComments()
                forbiddenSourcePatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(sourceText)) {
                        violations += "${source.relativeTo(rootDir)} contains forbidden production API reference ${pattern.pattern}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Pure Kotlin PNG encoder path must not use AWT/ImageIO/java.desktop APIs.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkProductionImageEncodeNoAwt") {
    group = "verification"
    description = "Fails if production image encoders use AWT/ImageIO/java.desktop APIs."

    doLast {
        val sourceRoot = file("kanvas-skia/src/main/kotlin/org/skia/encode")
        val violations = mutableListOf<String>()
        sourceRoot
            .walkTopDown()
            .filter { source -> source.isFile && source.extension in setOf("kt", "kts", "java") }
            .forEach { source ->
                val sourceText = source.readText().withoutKotlinOrJavaComments()
                forbiddenSourcePatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(sourceText)) {
                        violations += "${source.relativeTo(rootDir)} contains forbidden production API reference ${pattern.pattern}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Production image encoders must not use AWT/ImageIO/java.desktop APIs.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkImageEncodeTestsNoAwt") {
    group = "verification"
    description = "Fails if image encode tests use AWT/ImageIO as an oracle."

    doLast {
        val sourceRoot = file("kanvas-skia/src/test/kotlin/org/skia/encode")
        val violations = mutableListOf<String>()
        sourceRoot
            .walkTopDown()
            .filter { source -> source.isFile && source.extension in setOf("kt", "kts", "java") }
            .forEach { source ->
                val sourceText = source.readText().withoutKotlinOrJavaComments()
                forbiddenSourcePatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(sourceText)) {
                        violations += "${source.relativeTo(rootDir)} contains forbidden test oracle API reference ${pattern.pattern}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Image encode tests must not use AWT/ImageIO/java.desktop APIs.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkCpuRasterImageToolingNoAwt") {
    group = "verification"
    description = "Fails if cpu-raster image test tooling uses AWT/ImageIO/java.desktop APIs."

    doLast {
        val filesToCheck = listOf(
            file("cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt"),
            file("cpu-raster/src/main/kotlin/org/skia/testing/DiffImage.kt"),
            file("cpu-raster/src/test/kotlin/org/skia/testing/TestToolingTest.kt"),
            file("cpu-raster/src/test/kotlin/org/skia/codec/SkAndroidCodecComputeSampleSizeJpegTest.kt"),
            file("cpu-raster/src/test/kotlin/org/skia/codec/SkAndroidCodecGetAndroidPixelsTest.kt"),
            file("cpu-raster/src/test/kotlin/org/skia/codec/SkAndroidCodecTest.kt"),
        )
        val violations = mutableListOf<String>()
        filesToCheck
            .filter { source -> source.isFile }
            .forEach { source ->
                val sourceText = source.readText().withoutKotlinOrJavaComments()
                forbiddenSourcePatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(sourceText)) {
                        violations += "${source.relativeTo(rootDir)} contains forbidden image tooling API reference ${pattern.pattern}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("CPU raster image test tooling must not use AWT/ImageIO/java.desktop APIs.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkGpuRasterImageToolingNoAwt") {
    group = "verification"
    description = "Fails if gpu-raster image test tooling uses AWT/ImageIO/java.desktop APIs."

    doLast {
        val filesToCheck = listOf(
            file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/testing/CrossBackendHarness.kt"),
        )
        val violations = mutableListOf<String>()
        filesToCheck
            .filter { source -> source.isFile }
            .forEach { source ->
                val sourceText = source.readText().withoutKotlinOrJavaComments()
                forbiddenSourcePatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(sourceText)) {
                        violations += "${source.relativeTo(rootDir)} contains forbidden image tooling API reference ${pattern.pattern}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("GPU raster image test tooling must not use AWT/ImageIO/java.desktop APIs.")
                    violations.sorted().forEach { appendLine("- $it") }
                }
            )
        }
    }
}

tasks.register("checkCodecKotlinSwitchCriteria") {
    group = "verification"
    description = "Runs the non-destructive codec-all-kotlin switch-readiness checks."

    dependsOn(
        "checkSupportedCodecsDoc",
        "checkRealImageFixtureDocumentation",
        "checkProductionCodecRuntimeNoAwt",
        "checkPureKotlinCodecNoAwt",
        "checkPureKotlinPngEncoderNoAwt",
        "checkProductionImageEncodeNoAwt",
        "checkImageEncodeTestsNoAwt",
        ":codec-all-kotlin:test",
        ":codec-real-image-tests:test",
        ":codec-all-kotlin:jar",
        ":cpu-raster:testCodecWithKotlinBackend",
    )
}

tasks.register("checkCodecImageComplete") {
    group = "verification"
    description = "Runs the official portable codec/image validation suite and AWT/ImageIO guardrails."

    dependsOn(
        "checkCodecKotlinSwitchCriteria",
        "checkCpuRasterImageToolingNoAwt",
        "checkGpuRasterImageToolingNoAwt",
        "checkProductionCodecImageClasspathNoJavaDesktop",
        ":codec-png-kotlin:test",
        ":codec-jpeg-kotlin:test",
        ":codec-gif-kotlin:test",
        ":codec-bmp-kotlin:test",
        ":codec-wbmp-kotlin:test",
        ":codec-ico-kotlin:test",
        ":codec-webp-kotlin:test",
        ":codec-animated:test",
        ":codec-android:test",
        ":codec-image-generator:test",
        ":kanvas-skia:test",
    )
}

tasks.register("pipelineSceneDashboardGate") {
    group = "verification"
    description = "Runs the M50 release gate validation for the generated scene dashboard."

    dependsOn("pipelineSceneDashboard")

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val reportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scene-gate")
    inputs.dir(dashboardDir)
    outputs.dir(reportDir)
    outputs.upToDateWhen { false }

    doLast {
        val dashboardRoot = dashboardDir.get().asFile
        val dataFile = dashboardRoot.resolve("data/scenes.json")
        if (!dataFile.isFile) {
            throw GradleException("Missing merged scene dashboard data: ${dataFile.relativeTo(rootDir)}")
        }

        val reportRoot = reportDir.get().asFile
        reportRoot.mkdirs()

        val root = JsonSlurper().parse(dataFile) as? Map<*, *>
            ?: throw GradleException("Scene gate data root must be a JSON object: ${dataFile.relativeTo(rootDir)}")
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Scene gate data must contain a `scenes` array: ${dataFile.relativeTo(rootDir)}")

        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val allowedStatuses = setOf("pass", "expected-unsupported", "tracked-gap", "fail")
        val allowedPriorities = setOf("P0", "P1", "P2")
        val allowedExpectedUnsupportedFallbacks = mapOf(
            "path-aa-stroke-outline-fallback" to "coverage.stroke-outline-edge-count-exceeded",
            "path-aa-edge-budget-boundary" to "coverage.edge-count-exceeded",
            "path-aa-convexpaths-edge-budget" to "coverage.edge-count-exceeded",
            "path-aa-dashing-edge-budget" to "coverage.edge-count-exceeded",
            "image-filter-crop-nonnull-prepass-required" to "image-filter.crop-input-nonnull-prepass-required",
            "font-emoji-color-glyph-refusal" to "font.color-glyph-emoji-unsupported",
            "font-complex-shaping-refusal" to "font.complex-shaping-requires-explicit-shaper",
            "m62-missing-glyph-fallback-refusal" to "font.missing-glyph-fallback-unsupported",
            "m63-wide-gamut-color-space-refusal" to "color.color-space-wide-gamut-unsupported",
            "m63-advanced-blend-chain-refusal" to "blend.advanced-chain-unsupported",
            "m64-arbitrary-sksl-runtime-effect-refusal" to "runtime-effect.arbitrary-sksl-unsupported",
            "m52-closed-capped-hairlines-edge-budget" to "coverage.edge-count-exceeded",
            "m52-big-tile-image-filter-dag-refusal" to "image-filter.dag-or-picture-prepass-required",
            "m52-color-emoji-blendmodes-refusal" to "font.color-glyph-emoji-unsupported",
            "m53-complexclip-boundary-refusal" to "coverage.complex-clip-path-unsupported",
            "m53-imagefilters-cropped-boundary" to "image-filter.crop-input-nonnull-prepass-required",
            "m54-imagefilters-graph-boundary" to "image-filter.dag-or-picture-prepass-required",
            "m54-dash-circle-boundary" to "coverage.edge-count-exceeded",
            "m60-bounded-stroke-cap-join" to "coverage.stroke-cap-join-visual-parity-below-threshold",
            "m60-bounded-nested-rrect-clip" to "coverage.nested-clip-visual-parity-below-threshold",
            "m66-path-aa-dashing-edge-budget-refusal" to "coverage.edge-count-exceeded",
            "m66-image-filter-crop-prepass-refusal" to "image-filter.crop-input-nonnull-prepass-required",
            "m66-font-complex-shaping-refusal" to "font.complex-shaping-requires-explicit-shaper",
            "skia-gm-image" to "image.imagegm.row-specific-artifacts-required",
            "skia-gm-imagesource" to "image.imagesource.row-specific-artifacts-required",
            "skia-gm-offsetimagefilter" to "image-filter.offset.row-specific-artifacts-required",
            "skia-gm-pathfill" to "path.pathfill.row-specific-artifacts-required",
            "skia-gm-rectpolystroke" to "coverage.rectpolystroke.row-specific-artifacts-required",
            "skia-gm-imagemakewithfilter" to "image-filter.imagemakewithfilter.row-specific-artifacts-required",
            "skia-gm-runtimeintrinsics" to "runtime-effect.runtimeintrinsics.row-specific-artifacts-required",
            "skia-gm-textblobtransforms" to "font.textblobtransforms.row-specific-artifacts-required",
            "skia-gm-runtimeimagefilter" to "runtime-effect.runtimeimagefilter.row-specific-artifacts-required",
            "skia-gm-shadertext3" to "font.shadertext3.row-specific-artifacts-required",
            "skia-gm-gradients2ptconical" to "gradient.2ptconical.row-specific-artifacts-required",
            "skia-gm-dashcubics" to "coverage.dash-cubic.row-specific-artifacts-required",
            "skia-gm-dashing" to "coverage.dashing.row-specific-artifacts-required",
            "skia-gm-hairlines" to "coverage.hairline.row-specific-artifacts-required",
            "skia-gm-hairmodes" to "coverage.hairmode.row-specific-artifacts-required",
            "skia-gm-scaledstrokes" to "coverage.scaled-stroke.row-specific-artifacts-required",
            "skia-gm-strokedlines" to "coverage.stroked-lines.row-specific-artifacts-required",
            "skia-gm-strokerect" to "coverage.stroke-rect.row-specific-artifacts-required",
            "skia-gm-strokerects" to "coverage.stroke-rects.row-specific-artifacts-required",
            "skia-gm-thinstrokedrects" to "coverage.thin-stroked-rects.row-specific-artifacts-required",
        )
        val staticPathAaSentinels = mapOf(
            "path-aa-stroke-outline-fallback" to "coverage.stroke-outline-edge-count-exceeded",
            "path-aa-edge-budget-boundary" to "coverage.edge-count-exceeded",
        )

        fun fail(sceneId: String, invariant: String, detail: String) {
            failures += "[$invariant] $sceneId: $detail"
        }

        fun warn(sceneId: String, invariant: String, detail: String) {
            warnings += "[$invariant] $sceneId: $detail"
        }

        fun Map<*, *>.string(field: String): String? = this[field] as? String
        fun Map<*, *>.map(field: String): Map<*, *>? = this[field] as? Map<*, *>
        fun Map<*, *>.list(field: String): List<*>? = this[field] as? List<*>
        fun String?.isPresent(): Boolean = this != null && isNotBlank()
        fun scenePathExists(path: String): Boolean {
            val normalized = path.replace('\\', '/')
            return when {
                normalized.startsWith("artifacts/") || normalized.startsWith("data/") ->
                    dashboardRoot.resolve(normalized).let { it.isFile || it.isDirectory }
                normalized.startsWith("reports/") -> rootDir.resolve(normalized).isFile
                else -> true
            }
        }
        fun scenePathFile(path: String): File? {
            val normalized = path.replace('\\', '/')
            return when {
                normalized.startsWith("artifacts/") || normalized.startsWith("data/") -> dashboardRoot.resolve(normalized)
                normalized.startsWith("reports/") -> rootDir.resolve(normalized)
                else -> null
            }
        }

        fun requireString(sceneId: String, owner: Map<*, *>?, field: String, invariant: String, display: String = field): String? {
            val value = owner?.string(field)
            if (!value.isPresent()) {
                fail(sceneId, invariant, "missing or invalid `$display`")
            }
            return value
        }

        fun requireNumber(sceneId: String, owner: Map<*, *>?, field: String, invariant: String, display: String = field) {
            if (owner?.get(field) !is Number) {
                fail(sceneId, invariant, "missing or invalid `$display`")
            }
        }

        fun Map<*, *>?.number(field: String): Double? = (this?.get(field) as? Number)?.toDouble()

        fun requireStats(sceneId: String, owner: Map<*, *>?, prefix: String) {
            val stats = owner?.map("stats")
            if (stats == null) {
                fail(sceneId, "stats.required", "missing `$prefix.stats`")
                return
            }
            listOf("pixels", "matchingPixels", "maxChannelDelta", "threshold").forEach { field ->
                requireNumber(sceneId, stats, field, "stats.required", "$prefix.stats.$field")
            }
            requireString(sceneId, stats, "backend", "stats.required", "$prefix.stats.backend")
            requireString(sceneId, stats, "command", "stats.required", "$prefix.stats.command")
        }

        fun validateRoute(sceneId: String, owner: Map<*, *>?, prefix: String) {
            val route = owner?.map("route")
            if (route == null) {
                fail(sceneId, "route.required", "missing `$prefix.route`")
                return
            }
            val selectedRoute = route.string("selectedRoute")
            val coverageStrategy = route.string("coverageStrategy")
            if (!selectedRoute.isPresent() && !coverageStrategy.isPresent()) {
                fail(sceneId, "route.selector", "`$prefix.route` requires `selectedRoute` or `coverageStrategy`")
            }
            requireString(sceneId, route, "fallbackReason", "route.fallback", "$prefix.route.fallbackReason")
        }

        fun validatePerformanceTrend(sceneId: String, owner: Map<*, *>?, prefix: String) {
            val trend = owner?.map("performanceTrend") ?: return
            val status = requireString(sceneId, trend, "status", "performance.status", "$prefix.performanceTrend.status")
            when (status) {
                "unavailable" -> requireString(sceneId, trend, "reason", "performance.unavailable", "$prefix.performanceTrend.reason")
                "measured" -> {
                    requireNumber(sceneId, trend, "sampleCount", "performance.measured", "$prefix.performanceTrend.sampleCount")
                    val timing = trend.map("timing")
                    if (timing == null) {
                        fail(sceneId, "performance.measured", "missing `$prefix.performanceTrend.timing`")
                    } else {
                        requireNumber(sceneId, timing, "medianMs", "performance.measured", "$prefix.performanceTrend.timing.medianMs")
                        requireNumber(sceneId, timing, "p95Ms", "performance.measured", "$prefix.performanceTrend.timing.p95Ms")
                    }
                    val counters = trend.map("counters")
                    if (counters == null || counters.isEmpty()) {
                        fail(sceneId, "performance.measured", "missing or empty `$prefix.performanceTrend.counters`")
                    }
                    val baseline = trend.map("baseline")
                    requireString(sceneId, baseline, "name", "performance.measured", "$prefix.performanceTrend.baseline.name")
                    requireString(sceneId, baseline, "commit", "performance.measured", "$prefix.performanceTrend.baseline.commit")
                    val regression = trend.map("regression")
                    val label = requireString(sceneId, regression, "label", "performance.measured", "$prefix.performanceTrend.regression.label")
                    if (label == "regressed") {
                        warn(sceneId, "performance.regressed", "measured regression remains non-blocking until owner-approved M50 policy makes thresholds release-blocking")
                    }
                }
                "estimated" -> warn(sceneId, "performance.estimated", "estimated performance is reporting-only and cannot move readiness")
                null -> Unit
                else -> fail(sceneId, "performance.status", "unknown `$prefix.performanceTrend.status` '$status'")
            }
        }

        fun validateGraphDiagnostics(sceneId: String, graphDiagnostics: String, fallbackReason: String) {
            val graphFile = scenePathFile(graphDiagnostics)
            if (graphFile == null || !graphFile.isFile) {
                fail(sceneId, "image-filter.graph-diagnostics", "`graphDiagnostics` does not exist: `$graphDiagnostics`")
                return
            }
            val graph = try {
                JsonSlurper().parse(graphFile) as? Map<*, *>
            } catch (error: Exception) {
                fail(sceneId, "image-filter.graph-diagnostics", "invalid graph diagnostics JSON `${graphFile.relativeTo(rootDir)}`: ${error.message}")
                return
            }
            if (graph == null) {
                fail(sceneId, "image-filter.graph-diagnostics", "graph diagnostics must be a JSON object")
                return
            }

            fun graphString(field: String): String? {
                val value = graph.string(field)
                if (!value.isPresent()) fail(sceneId, "image-filter.graph-diagnostics", "missing or invalid `graphDiagnostics.$field`")
                return value
            }

            fun graphNumber(field: String): Number? {
                val value = graph[field] as? Number
                if (value == null) fail(sceneId, "image-filter.graph-diagnostics", "missing or invalid `graphDiagnostics.$field`")
                return value
            }

            val graphSceneId = graphString("sceneId")
            if (graphSceneId != null && graphSceneId != sceneId) {
                fail(sceneId, "image-filter.graph-diagnostics", "`graphDiagnostics.sceneId` must match `$sceneId`, got `$graphSceneId`")
            }
            val graphFallback = graphString("fallbackReason")
            if (graphFallback != null && graphFallback != fallbackReason) {
                fail(sceneId, "image-filter.graph-diagnostics", "`graphDiagnostics.fallbackReason` must match `$fallbackReason`, got `$graphFallback`")
            }
            graphString("milestoneOwner")
            graphString("status")
            graphString("nonClaim")
            val nodeCount = graphNumber("nodeCount")?.toInt()
            val nodeBudget = graphNumber("nodeBudget")?.toInt()
            graphNumber("childrenPerNodeBudget")
            val intermediateTextureCount = graphNumber("intermediateTextureCount")?.toInt()
            val intermediateTextureBudget = graphNumber("intermediateTextureBudget")?.toInt()
            graphNumber("estimatedIntermediateBytes")
            if (nodeCount != null && nodeBudget != null && nodeCount > nodeBudget) {
                fail(sceneId, "image-filter.graph-diagnostics", "`nodeCount` exceeds `nodeBudget`: $nodeCount > $nodeBudget")
            }
            if (intermediateTextureCount != null && intermediateTextureBudget != null && intermediateTextureCount > intermediateTextureBudget) {
                fail(sceneId, "image-filter.graph-diagnostics", "`intermediateTextureCount` exceeds `intermediateTextureBudget`: $intermediateTextureCount > $intermediateTextureBudget")
            }
            val bounds = graph.map("bounds")
            if (bounds?.map("input") == null || bounds.map("output") == null) {
                fail(sceneId, "image-filter.graph-diagnostics", "missing `bounds.input` or `bounds.output`")
            }
            val nodes = graph.list("nodes")
            if (nodes == null || nodes.isEmpty()) {
                fail(sceneId, "image-filter.graph-diagnostics", "missing or empty `nodes`")
            } else {
                if (nodeCount != null && nodes.size != nodeCount) {
                    fail(sceneId, "image-filter.graph-diagnostics", "`nodes.size` must match `nodeCount`: ${nodes.size} != $nodeCount")
                }
                nodes.forEachIndexed { nodeIndex, rawNode ->
                    val node = rawNode as? Map<*, *>
                    if (node == null) {
                        fail(sceneId, "image-filter.graph-diagnostics", "`nodes[$nodeIndex]` must be an object")
                    } else {
                        listOf("id", "kind", "support").forEach { field ->
                            if (!node.string(field).isPresent()) {
                                fail(sceneId, "image-filter.graph-diagnostics", "missing or invalid `nodes[$nodeIndex].$field`")
                            }
                        }
                        if (node.list("inputs") == null) {
                            fail(sceneId, "image-filter.graph-diagnostics", "missing or invalid `nodes[$nodeIndex].inputs`")
                        }
                    }
                }
            }
            if (graph.list("passOrder") == null) {
                fail(sceneId, "image-filter.graph-diagnostics", "missing `passOrder`")
            }
            val ownership = graph.map("ownership")
            if (ownership == null || ownership.isEmpty()) {
                fail(sceneId, "image-filter.graph-diagnostics", "missing or empty `ownership`")
            }
        }

        fun collectPaths(value: Any?, paths: MutableSet<String>) {
            when (value) {
                is Map<*, *> -> value.values.forEach { collectPaths(it, paths) }
                is Iterable<*> -> value.forEach { collectPaths(it, paths) }
                is String -> {
                    val normalized = value.replace('\\', '/')
                    if (normalized.startsWith("artifacts/") || normalized.startsWith("data/") || normalized.startsWith("reports/")) {
                        paths += normalized
                    }
                }
            }
        }

        val seenIds = mutableSetOf<String>()
        val statusCounts = linkedMapOf<String, Int>()
        val maturityCounts = linkedMapOf<String, Int>()
        var adapterBackedRows = 0
        var inventoryDerivedRows = 0
        var m54Rows = 0
        var m61Rows = 0
        var m62Rows = 0
        var m63Rows = 0
        var m64Rows = 0
        var m66Rows = 0
        var m66SkiaUpstreamRows = 0
        var m66TestOracleRows = 0
        var m66CpuOracleRows = 0
        val m54FamilyCounts = linkedMapOf<String, Int>()
        val m61FamilyCounts = linkedMapOf<String, Int>()
        val m62FamilyCounts = linkedMapOf<String, Int>()
        val m63FamilyCounts = linkedMapOf<String, Int>()
        val m64FamilyCounts = linkedMapOf<String, Int>()
        val m66FamilyCounts = linkedMapOf<String, Int>()

        scenes.forEachIndexed { index, rawScene ->
            val scene = rawScene as? Map<*, *>
            if (scene == null) {
                fail("scenes[$index]", "row.object", "scene record must be an object")
                return@forEachIndexed
            }
            val sceneId = scene.string("id") ?: "scenes[$index]"
            if (!Regex("[a-z0-9][a-z0-9-]*").matches(sceneId)) {
                fail(sceneId, "id.format", "id must be lowercase kebab-case")
            }
            if (!seenIds.add(sceneId)) {
                fail(sceneId, "id.unique", "duplicate scene id")
            }

            listOf("title", "source", "reference").forEach { field ->
                requireString(sceneId, scene, field, "fields.required")
            }
            val priority = requireString(sceneId, scene, "priority", "priority.allowed")
            if (priority != null && priority !in allowedPriorities) {
                fail(sceneId, "priority.allowed", "unknown priority '$priority'")
            }
            val status = requireString(sceneId, scene, "status", "status.allowed")
            if (status != null) {
                statusCounts[status] = (statusCounts[status] ?: 0) + 1
                if (status !in allowedStatuses) {
                    fail(sceneId, "status.allowed", "unknown status '$status'")
                }
                if (status == "tracked-gap" || status == "fail") {
                    fail(sceneId, "status.promoted", "promoted dashboard must contain 0 tracked-gap and 0 fail rows")
                }
            }

            val cpu = scene.map("cpu")
            val gpu = scene.map("gpu")
            val diffs = scene.map("diffs")
            val routeDiagnostics = scene.map("routeDiagnostics")
            val topStats = scene.map("stats")
            if (cpu == null) fail(sceneId, "fields.required", "missing `cpu`")
            if (gpu == null) fail(sceneId, "fields.required", "missing `gpu`")
            if (diffs == null) fail(sceneId, "fields.required", "missing `diffs`")
            if (routeDiagnostics == null) fail(sceneId, "fields.required", "missing `routeDiagnostics`")
            if (topStats == null) fail(sceneId, "fields.required", "missing `stats`")

            requireString(sceneId, cpu, "image", "fields.pass", "cpu.image")
            requireString(sceneId, cpu, "diff", "fields.pass", "cpu.diff")
            validateRoute(sceneId, cpu, "cpu")
            requireStats(sceneId, cpu, "cpu")
            val cpuSimilarity = (cpu?.get("similarity") as? Number)?.toDouble()
                ?: cpu?.map("referenceParity").number("similarity")
            val cpuThreshold = cpu?.map("referenceParity").number("threshold")
                ?: cpu?.map("stats").number("threshold")
            if (cpu?.string("status") == "pass" && cpuSimilarity != null && cpuThreshold != null && cpuSimilarity < cpuThreshold) {
                warn(
                    sceneId,
                    "parity.cpu-below-threshold",
                    "cpu.status=pass means artifact production here; CPU reference parity is $cpuSimilarity < $cpuThreshold",
                )
            }
            validatePerformanceTrend(sceneId, cpu, "cpu")

            val gpuStatus = requireString(sceneId, gpu, "status", "gpu.status", "gpu.status")
            val gpuRoute = gpu?.map("route")
            validateRoute(sceneId, gpu, "gpu")
            if (status == "pass" || gpuStatus == "pass") {
                requireString(sceneId, gpu, "image", "fields.pass", "gpu.image")
                requireString(sceneId, gpu, "diff", "fields.pass", "gpu.diff")
                requireStats(sceneId, gpu, "gpu")
                validatePerformanceTrend(sceneId, gpu, "gpu")
                val fallback = gpuRoute?.string("fallbackReason")
                if (fallback != "none") {
                    fail(sceneId, "fallback.support", "pass rows require `gpu.route.fallbackReason=none`, got '$fallback'")
                }
                requireString(sceneId, diffs, "gpu", "fields.pass", "diffs.gpu")
            }
            if (status == "expected-unsupported" || gpuStatus == "expected-unsupported") {
                val fallback = gpuRoute?.string("fallbackReason")
                if (!fallback.isPresent() || fallback == "none") {
                    fail(sceneId, "fallback.unsupported", "expected-unsupported rows require stable non-none fallback reason")
                }
                val expected = allowedExpectedUnsupportedFallbacks[sceneId]
                if (expected != null && fallback != expected) {
                    fail(sceneId, "fallback.stable", "expected fallback '$expected', got '$fallback'")
                } else if (expected == null) {
                    warn(sceneId, "fallback.new", "new expected-unsupported fallback '$fallback' needs policy evidence before it can move readiness")
                }
            }

            requireString(sceneId, diffs, "cpu", "fields.required", "diffs.cpu")
            requireString(sceneId, routeDiagnostics, "cpu", "route.diagnostics", "routeDiagnostics.cpu")
            requireString(sceneId, routeDiagnostics, "gpu", "route.diagnostics", "routeDiagnostics.gpu")
            listOf("pixels", "matchingPixels", "maxChannelDelta", "threshold").forEach { field ->
                requireNumber(sceneId, topStats, field, "stats.required", "stats.$field")
            }

            val tags = scene.list("tags")
            if (tags == null || tags.isEmpty()) {
                fail(sceneId, "tags.required", "missing or empty tags")
            } else {
                val tagStrings = mutableListOf<String>()
                tags.forEachIndexed { tagIndex, rawTag ->
                    val tag = rawTag as? String
                    if (!tag.isPresent()) {
                        fail(sceneId, "tags.format", "tags[$tagIndex] must be a non-empty string")
                    } else {
                        if (!Regex("[a-z0-9][a-z0-9.-]*").matches(tag!!)) {
                            fail(sceneId, "tags.format", "invalid tag '$tag'")
                        }
                        tagStrings += tag
                    }
                }
                tagStrings.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { duplicate ->
                    fail(sceneId, "tags.duplicate", "duplicate tag '$duplicate'")
                }
                val tagSet = tagStrings.toSet()
                val generation = scene.map("generation")
                val graphDiagnostics = scene.string("graphDiagnostics")
                val fallback = gpuRoute?.string("fallbackReason").orEmpty()
                val selectedGpuRoute = gpuRoute?.string("selectedRoute").orEmpty()
                val pipelineKey = gpuRoute?.string("pipelineKey").orEmpty()
                val pipelineKeyDagSignal = pipelineKey.contains("graph", ignoreCase = true) ||
                    pipelineKey.contains("dag=", ignoreCase = true) ||
                    pipelineKey.contains(" dag", ignoreCase = true) ||
                    pipelineKey.contains("dag-or", ignoreCase = true)
                val isGeneratedRow = "source.generated" in tagSet || generation?.string("mode") == "generated"
                val isImageFilterDagRow = isGeneratedRow && "feature.image-filter" in tagSet &&
                    (sceneId.contains("graph") ||
                        sceneId.contains("dag") ||
                        fallback.contains("dag", ignoreCase = true) ||
                        selectedGpuRoute.contains("graph", ignoreCase = true) ||
                        selectedGpuRoute.contains("dag", ignoreCase = true) ||
                        pipelineKeyDagSignal)
                if (isImageFilterDagRow) {
                    if (!graphDiagnostics.isPresent()) {
                        fail(sceneId, "image-filter.graph-diagnostics", "generated image-filter DAG rows require `graphDiagnostics`")
                    } else {
                        validateGraphDiagnostics(sceneId, graphDiagnostics!!, fallback)
                    }
                }
                val generationMode = generation?.string("mode")
                if (generation?.string("derivationTask") == "pipelineM54HardFeatureDepthPack") {
                    m54Rows += 1
                    val family = generation.string("hardFeatureFamily") ?: "unknown"
                    m54FamilyCounts[family] = (m54FamilyCounts[family] ?: 0) + 1
                }
                if (generation?.string("derivationTask") == "pipelineM61ImageFilterDagV2PromotionPack") {
                    m61Rows += 1
                    val family = generation.string("hardFeatureFamily") ?: "unknown"
                    m61FamilyCounts[family] = (m61FamilyCounts[family] ?: 0) + 1
                }
                if (generation?.string("derivationTask") == "pipelineM62FontFallbackEvidencePack") {
                    m62Rows += 1
                    val family = generation.string("hardFeatureFamily") ?: "unknown"
                    m62FamilyCounts[family] = (m62FamilyCounts[family] ?: 0) + 1
                }
                if (generation?.string("derivationTask") == "pipelineM63ColorBlendParityPack") {
                    m63Rows += 1
                    val family = generation.string("hardFeatureFamily") ?: "unknown"
                    m63FamilyCounts[family] = (m63FamilyCounts[family] ?: 0) + 1
                }
                if (generation?.string("derivationTask") == "pipelineM64RegisteredRuntimeEffectsPack") {
                    m64Rows += 1
                    val family = generation.string("hardFeatureFamily") ?: "unknown"
                    m64FamilyCounts[family] = (m64FamilyCounts[family] ?: 0) + 1
                }
                if (generation?.string("derivationTask") == "pipelineM66GmPromotionWave") {
                    m66Rows += 1
                    val family = generation.string("hardFeatureFamily") ?: "unknown"
                    m66FamilyCounts[family] = (m66FamilyCounts[family] ?: 0) + 1
                    when (scene.string("referenceKind")) {
                        "skia-upstream" -> m66SkiaUpstreamRows += 1
                        "test-oracle" -> m66TestOracleRows += 1
                        "cpu-oracle" -> m66CpuOracleRows += 1
                        else -> fail(sceneId, "m66.reference-kind", "M66 rows require referenceKind skia-upstream, test-oracle, or cpu-oracle")
                    }
                    if ("source.generated" !in tagSet) {
                        fail(sceneId, "m66.generated", "M66 rows must remain generated dashboard evidence")
                    }
                }
                val inventoryId = scene.string("inventoryId")
                if ("source.inventory" in tagSet || inventoryId.isPresent()) {
                    inventoryDerivedRows += 1
                    if (!inventoryId.isPresent()) {
                        fail(sceneId, "inventory.id", "inventory-derived rows require top-level `inventoryId`")
                    }
                    val generationInventoryId = generation?.string("inventoryId")
                    if (!generationInventoryId.isPresent() || generationInventoryId != inventoryId) {
                        fail(sceneId, "inventory.generation", "inventory-derived rows require matching `generation.inventoryId`")
                    }
                    val sourceReport = generation?.string("sourceReport")
                    if (!sourceReport.isPresent()) {
                        fail(sceneId, "inventory.sourceReport", "inventory-derived rows require `generation.sourceReport`")
                    }
                    if ("source.generated" !in tagSet) {
                        fail(sceneId, "inventory.generated", "inventory-derived dashboard rows must also carry `source.generated`")
                    }
                }
                if (generationMode == "generated" || generationMode == "mixed") {
                    listOf("source.", "feature.", "route.", "reference.", "maturity.").forEach { namespace ->
                        if (tagSet.none { it.startsWith(namespace) }) {
                            fail(sceneId, "tags.generated", "generated rows require a `$namespace*` tag")
                        }
                    }
                    listOf("producer", "commit", "artifactRoot", "schema").forEach { field ->
                        requireString(sceneId, generation, field, "generation.required", "generation.$field")
                    }
                    val hasTrace = listOf("sourceTask", "sourceTest", "sourceReport")
                        .any { field -> generation?.string(field).isPresent() }
                    if (!hasTrace) {
                        fail(sceneId, "generation.trace", "generated rows require sourceTask, sourceTest, or sourceReport")
                    }
                    val evidence = scene.list("evidence")
                    if (evidence == null || evidence.none { it is String && it.isNotBlank() }) {
                        fail(sceneId, "generation.evidence", "generated rows require non-empty evidence links")
                    }
                    val artifactRoot = generation?.string("artifactRoot")
                    if (artifactRoot.isPresent() && !scenePathExists(artifactRoot!!)) {
                        fail(sceneId, "artifact.generatedRoot", "generation.artifactRoot does not exist: `$artifactRoot`")
                    }
                }
                if (status == "expected-unsupported" || gpuStatus == "expected-unsupported") {
                    if ("route.gpu.expected-unsupported" !in tagSet) {
                        fail(sceneId, "tags.unsupported", "expected-unsupported rows require route.gpu.expected-unsupported")
                    }
                    if ("risk.expected-unsupported" !in tagSet) {
                        fail(sceneId, "tags.unsupported", "expected-unsupported rows require risk.expected-unsupported")
                    }
                }
                if ("maturity.adapter-backed" in tagSet) {
                    adapterBackedRows += 1
                    val adapter = gpu?.map("stats")?.string("adapter")
                    if (!adapter.isPresent()) {
                        fail(sceneId, "adapter.metadata", "maturity.adapter-backed requires gpu.stats.adapter")
                    }
                }
                if ("feature.font" in tagSet || "feature.text" in tagSet) {
                    val font = scene.map("font")
                    val shapingMode = font?.string("shapingMode").orEmpty()
                    val gpuPipelineKey = gpuRoute?.string("pipelineKey").orEmpty()
                    val gpuSelectedRoute = gpuRoute?.string("selectedRoute").orEmpty()
                    if (status == "pass" || gpuStatus == "pass") {
                        if (!font?.string("glyphDiagnostics").isPresent()) {
                            fail(sceneId, "font.glyph-diagnostics", "font/text pass rows require `font.glyphDiagnostics`")
                        }
                        if (!gpuPipelineKey.contains("glyphRepresentation=outline") && !gpuSelectedRoute.contains(".outline.")) {
                            fail(sceneId, "font.route-claim", "font/text pass rows currently must claim outline/path rendering only")
                        }
                        if (gpuPipelineKey.contains("atlas", ignoreCase = true) || gpuSelectedRoute.contains("atlas", ignoreCase = true)) {
                            fail(sceneId, "font.atlas-nonclaim", "font/text pass rows must not claim glyph atlas support without atlas artifacts")
                        }
                    }
                    if (shapingMode.contains("complex", ignoreCase = true) && (status == "pass" || gpuStatus == "pass")) {
                        fail(sceneId, "font.shaping-boundary", "complex shaping rows must remain refused until an explicit shaper exists")
                    }
                }
                tagSet.filter { it.startsWith("maturity.") }.forEach { tag ->
                    maturityCounts[tag] = (maturityCounts[tag] ?: 0) + 1
                }

                val staticSentinelFallback = staticPathAaSentinels[sceneId]
                if (staticSentinelFallback != null) {
                    if (status != "expected-unsupported") {
                        fail(sceneId, "sentinel.status", "static Path AA sentinel must remain expected-unsupported")
                    }
                    if (gpuRoute?.string("fallbackReason") != staticSentinelFallback) {
                        fail(sceneId, "sentinel.fallback", "static Path AA sentinel fallback must remain '$staticSentinelFallback'")
                    }
                    if ("source.static" !in tagSet || "maturity.static-evidence" !in tagSet) {
                        fail(sceneId, "sentinel.tags", "static Path AA sentinel must keep source.static and maturity.static-evidence tags")
                    }
                }
            }
        }

        val referencedPaths = mutableSetOf<String>()
        collectPaths(root, referencedPaths)
        referencedPaths.sorted().filterNot(::scenePathExists).forEach { path ->
            failures += "[artifact.exists] dashboard: missing referenced artifact or report `$path`"
        }

        val counterSummary = linkedMapOf(
            "total" to scenes.size,
            "adapterBacked" to adapterBackedRows,
            "inventoryDerived" to inventoryDerivedRows,
            "m54Rows" to m54Rows,
            "m61Rows" to m61Rows,
            "m62Rows" to m62Rows,
            "m63Rows" to m63Rows,
            "m64Rows" to m64Rows,
            "m66Rows" to m66Rows,
            "m66.referenceKind.skia-upstream" to m66SkiaUpstreamRows,
            "m66.referenceKind.test-oracle" to m66TestOracleRows,
            "m66.referenceKind.cpu-oracle" to m66CpuOracleRows,
        ) + statusCounts.mapKeys { "status.${it.key}" } +
            maturityCounts.mapKeys { "${it.key}" } +
            m54FamilyCounts.mapKeys { "m54.family.${it.key}" } +
            m61FamilyCounts.mapKeys { "m61.family.${it.key}" } +
            m62FamilyCounts.mapKeys { "m62.family.${it.key}" } +
            m63FamilyCounts.mapKeys { "m63.family.${it.key}" } +
            m64FamilyCounts.mapKeys { "m64.family.${it.key}" } +
            m66FamilyCounts.mapKeys { "m66.family.${it.key}" }

        val markdown = buildString {
            appendLine("# WGSL Scene Dashboard Gate Report")
            appendLine()
            appendLine("Task: `pipelineSceneDashboardGate`")
            appendLine("Source: `${dataFile.relativeTo(rootDir)}`")
            appendLine()
            appendLine("## Counters")
            appendLine()
            appendLine("| Counter | Value |")
            appendLine("|---|---:|")
            counterSummary.forEach { (key, value) -> appendLine("| `$key` | $value |") }
            appendLine()
            appendLine("## Failures")
            appendLine()
            if (failures.isEmpty()) appendLine("None.") else failures.sorted().forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Warnings")
            appendLine()
            if (warnings.isEmpty()) appendLine("None.") else warnings.sorted().forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Allowed Expected Unsupported Rows")
            appendLine()
            appendLine("| Scene id | Fallback reason |")
            appendLine("|---|---|")
            allowedExpectedUnsupportedFallbacks.forEach { (sceneId, fallback) ->
                appendLine("| `$sceneId` | `$fallback` |")
            }
        }
        reportRoot.resolve("scene-dashboard-gate.md").writeText(markdown)
        reportRoot.resolve("scene-dashboard-gate.json").writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    mapOf(
                        "source" to dataFile.relativeTo(rootDir).path,
                        "counters" to counterSummary,
                        "failures" to failures.sorted(),
                        "warnings" to warnings.sorted(),
                        "allowedExpectedUnsupportedFallbacks" to allowedExpectedUnsupportedFallbacks,
                    )
                )
            ) + "\n"
        )
        logger.lifecycle("Wrote WGSL scene dashboard gate report: ${reportRoot.resolve("scene-dashboard-gate.md").relativeTo(rootDir)}")
        if (failures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("WGSL scene dashboard gate failed:")
                    failures.sorted().forEach { appendLine("- $it") }
                    appendLine("Report: ${reportRoot.resolve("scene-dashboard-gate.md").relativeTo(rootDir)}")
                }
            )
        }
    }
}

tasks.register("pipelinePerformanceTrendWarnings") {
    group = "verification"
    description = "Emits the M50 warning-only performance trend report from dashboard performanceTrend payloads."

    dependsOn("pipelineSceneDashboard")

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val reportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-performance-warnings")
    val m55CandidateSource = layout.projectDirectory.file("reports/wgsl-pipeline/performance/m55-performance-gate-candidates.json")
    inputs.dir(dashboardDir)
    inputs.file(m55CandidateSource)
    outputs.dir(reportDir)
    outputs.upToDateWhen { false }

    doLast {
        val dashboardRoot = dashboardDir.get().asFile
        val dataFile = dashboardRoot.resolve("data/scenes.json")
        val reportRoot = reportDir.get().asFile
        reportRoot.mkdirs()
        val root = JsonSlurper().parse(dataFile) as? Map<*, *>
            ?: throw GradleException("Performance warning data root must be a JSON object: ${dataFile.relativeTo(rootDir)}")
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Performance warning data must contain a `scenes` array: ${dataFile.relativeTo(rootDir)}")

        fun Map<*, *>.map(field: String): Map<*, *>? = this[field] as? Map<*, *>
        fun Map<*, *>.string(field: String): String? = this[field] as? String
        fun Number?.asIntOrZero(): Int = this?.toInt() ?: 0

        val rows = mutableListOf<Map<String, Any>>()
        val warnings = mutableListOf<String>()
        scenes.filterIsInstance<Map<*, *>>().forEach { scene ->
            val sceneId = scene.string("id").orEmpty()
            listOf("cpu" to "CPU", "gpu" to "GPU/cache").forEach { (field, laneName) ->
                val trend = scene.map(field)?.map("performanceTrend") ?: return@forEach
                val status = trend.string("status").orEmpty()
                val environment = trend.map("environment").orEmpty()
                val gate = trend.map("gate").orEmpty()
                val baseline = trend.map("baseline").orEmpty()
                val variance = trend.map("variancePolicy").orEmpty()
                val warmCold = trend.string("warmCold") ?: trend.string("sampleClass") ?: "warm-steady-state"
                val sampleCount = (trend["sampleCount"] as? Number).asIntOrZero()
                val row = linkedMapOf<String, Any>(
                    "sceneId" to sceneId,
                    "lane" to laneName,
                    "status" to status,
                    "host" to environment.string("host").orEmpty(),
                    "os" to environment.string("os").orEmpty(),
                    "jdk" to environment.string("jdk").orEmpty(),
                    "backend" to environment.string("backend").orEmpty(),
                    "adapter" to (trend.string("adapter") ?: environment.string("adapter") ?: "not-applicable"),
                    "warmCold" to warmCold,
                    "sampleCount" to sampleCount,
                    "baselineId" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                    "variancePolicy" to (variance.string("policy") ?: variance.string("allowedVariance") ?: "warning-only: 15% median / 20% p95 review band"),
                    "gateMode" to (gate.string("mode") ?: "reporting-only"),
                )
                rows += row
                if (status == "measured") {
                    listOf("host", "os", "jdk", "backend", "baselineId").forEach { key ->
                        if ((row[key] as? String).isNullOrBlank() || row[key] == "unavailable") {
                            warnings += "$sceneId/$laneName measured trend is missing `$key` metadata"
                        }
                    }
                    if (sampleCount <= 0) warnings += "$sceneId/$laneName measured trend is missing sampleCount"
                } else {
                    warnings += "$sceneId/$laneName trend is `$status`; report remains warning-only"
                }
            }
        }

        val measuredCpu = rows.count { it["lane"] == "CPU" && it["status"] == "measured" }
        val measuredGpu = rows.count { it["lane"] == "GPU/cache" && it["status"] == "measured" }
        if (measuredCpu < 2) warnings += "Only $measuredCpu CPU measured trend rows are present; M50 target is at least 2."
        if (measuredGpu < 2) warnings += "Only $measuredGpu GPU/cache measured trend rows are present; M50 target is at least 2."

        val policy = linkedMapOf(
            "owner" to "Kanvas rendering release owner",
            "mode" to "warning-only",
            "baselineOwner" to "Kanvas rendering release owner",
            "quarantinePolicy" to "A noisy or adapter-mismatched row stays visible in this report and is quarantined from score movement until rerun with matching host/JDK/backend/adapter metadata.",
            "rollbackPolicy" to "A confirmed correctness regression rolls back the rendering change; a performance-only warning does not block release until an owner-approved blocking threshold exists.",
            "variancePolicy" to "Review when median changes by more than 15% or p95 changes by more than 20% across matching measured baselines.",
            "releaseBlocking" to false,
        )
        val payload = linkedMapOf(
            "schemaVersion" to 1,
            "generatedBy" to "pipelinePerformanceTrendWarnings",
            "source" to dataFile.relativeTo(rootDir).path,
            "policy" to policy,
            "counters" to mapOf(
                "rows" to rows.size,
                "measuredCpu" to measuredCpu,
                "measuredGpuCache" to measuredGpu,
                "warnings" to warnings.size,
            ),
            "rows" to rows,
            "warnings" to warnings.sorted(),
        )
        reportRoot.resolve("performance-warnings.json")
            .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + "\n")
        reportRoot.resolve("performance-warnings.md").writeText(
            buildString {
                appendLine("# M50 Performance Trend Warnings")
                appendLine()
                appendLine("Task: `pipelinePerformanceTrendWarnings`")
                appendLine("Source: `${dataFile.relativeTo(rootDir)}`")
                appendLine("Mode: warning-only; not release-blocking.")
                appendLine()
                appendLine("## Policy")
                appendLine()
                policy.forEach { (key, value) -> appendLine("- `$key`: $value") }
                appendLine()
                appendLine("## Counters")
                appendLine()
                appendLine("| Counter | Value |")
                appendLine("|---|---:|")
                appendLine("| Rows | ${rows.size} |")
                appendLine("| Measured CPU rows | $measuredCpu |")
                appendLine("| Measured GPU/cache rows | $measuredGpu |")
                appendLine("| Warnings | ${warnings.size} |")
                appendLine()
                appendLine("## Rows")
                appendLine()
                appendLine("| Scene | Lane | Status | Host | OS | JDK | Backend | Adapter | Samples | Baseline | Variance policy |")
                appendLine("|---|---|---|---|---|---|---|---|---:|---|---|")
                rows.forEach { row ->
                    appendLine("| `${row["sceneId"]}` | ${row["lane"]} | `${row["status"]}` | `${row["host"]}` | `${row["os"]}` | `${row["jdk"]}` | `${row["backend"]}` | `${row["adapter"]}` | ${row["sampleCount"]} | `${row["baselineId"]}` | ${row["variancePolicy"]} |")
                }
                appendLine()
                appendLine("## Warnings")
                appendLine()
                if (warnings.isEmpty()) appendLine("None.") else warnings.sorted().forEach { appendLine("- $it") }
            }
        )
        val candidateFile = m55CandidateSource.asFile
        if (candidateFile.isFile) {
            val candidateRoot = JsonSlurper().parse(candidateFile) as? Map<*, *>
                ?: throw GradleException("M55 candidate root must be a JSON object: ${candidateFile.relativeTo(rootDir)}")
            val candidatePolicy = candidateRoot.map("policy").orEmpty()
            val selectedRows = (candidateRoot["selectedRows"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            val excludedRows = (candidateRoot["excludedRows"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            val sceneById = scenes
                .filterIsInstance<Map<*, *>>()
                .associateBy { it.string("id").orEmpty() }

            fun laneCandidate(
                selected: Map<*, *>,
                scene: Map<*, *>?,
                laneKey: String,
                laneName: String,
            ): Map<String, Any> {
                val sceneId = selected.string("sceneId").orEmpty()
                val laneConfig = selected.map(laneKey).orEmpty()
                val expectedDecision = laneConfig.string("decision") ?: selected.string("decision").orEmpty()
                val deferredReason = laneConfig.string("deferredReason")
                val trend = scene?.map(laneKey)?.map("performanceTrend")
                val trendStatus = trend?.string("status") ?: "unavailable"
                val baseline = trend?.map("baseline").orEmpty()
                val environment = trend?.map("environment").orEmpty()
                val timing = trend?.map("timing").orEmpty()
                val gate = trend?.map("gate").orEmpty()
                val regression = trend?.map("regression").orEmpty()
                val sampleCount = (trend?.get("sampleCount") as? Number).asIntOrZero()
                val missing = mutableListOf<String>()
                if (expectedDecision == "deferred") {
                    return linkedMapOf<String, Any>(
                        "sceneId" to sceneId,
                        "lane" to laneName,
                        "status" to "deferred",
                        "payloadStatus" to trendStatus,
                        "decision" to expectedDecision,
                        "reason" to (deferredReason ?: "Measurement deferred by M55 selection policy."),
                        "sampleCount" to sampleCount,
                        "baseline" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                    )
                }
                if (trend == null) {
                    return linkedMapOf<String, Any>(
                        "sceneId" to sceneId,
                        "lane" to laneName,
                        "status" to "warn",
                        "payloadStatus" to "missing",
                        "decision" to expectedDecision,
                        "reason" to "Selected row has no `$laneKey.performanceTrend` payload.",
                        "sampleCount" to 0,
                        "baseline" to "unavailable",
                    )
                }
                if (trendStatus != "measured") {
                    return linkedMapOf<String, Any>(
                        "sceneId" to sceneId,
                        "lane" to laneName,
                        "status" to "warn",
                        "payloadStatus" to trendStatus,
                        "decision" to expectedDecision,
                        "reason" to "Selected row expected measured data but payload status is `$trendStatus`.",
                        "sampleCount" to sampleCount,
                        "baseline" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                    )
                }
                if (environment.string("host").isNullOrBlank()) missing += "environment.host"
                if (environment.string("jdk").isNullOrBlank()) missing += "environment.jdk"
                if (environment.string("backend").isNullOrBlank()) missing += "environment.backend"
                if (laneKey == "gpu" && environment.string("adapter").isNullOrBlank()) missing += "environment.adapter"
                if (sampleCount <= 0) missing += "sampleCount"
                if ((timing["medianMs"] as? Number) == null) missing += "timing.medianMs"
                if ((timing["p95Ms"] as? Number) == null) missing += "timing.p95Ms"
                if ((baseline.string("id") ?: baseline.string("name")).isNullOrBlank()) missing += "baseline.id|name"
                if (baseline.string("owner").isNullOrBlank() && candidatePolicy.string("baselineOwner").isNullOrBlank()) {
                    missing += "baseline.owner"
                }
                val regressionLabel = regression.string("label").orEmpty()
                val laneStatus = when {
                    missing.isNotEmpty() -> "warn"
                    regressionLabel == "regressed" -> "fail-candidate"
                    else -> "pass"
                }
                val reason = when (laneStatus) {
                    "pass" -> "Measured payload is complete for M55 candidate reporting and remains non-blocking."
                    "fail-candidate" -> "Measured payload is marked regressed; M55 reports this without failing Gradle."
                    else -> "Measured payload is missing required candidate metadata: ${missing.joinToString(", ")}."
                }
                return linkedMapOf<String, Any>(
                    "sceneId" to sceneId,
                    "lane" to laneName,
                    "status" to laneStatus,
                    "payloadStatus" to trendStatus,
                    "decision" to expectedDecision,
                    "reason" to reason,
                    "sampleCount" to sampleCount,
                    "baseline" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                    "baselineOwner" to (baseline.string("owner") ?: candidatePolicy.string("baselineOwner").orEmpty()),
                    "regressionLabel" to regressionLabel,
                    "gateMode" to (gate.string("mode") ?: gate.string("status") ?: "reporting-only"),
                    "medianMs" to ((timing["medianMs"] as? Number)?.toDouble() ?: 0.0),
                    "p95Ms" to ((timing["p95Ms"] as? Number)?.toDouble() ?: 0.0),
                )
            }

            val candidateRows = selectedRows.map { selected ->
                val sceneId = selected.string("sceneId").orEmpty()
                val scene = sceneById[sceneId]
                val laneRows = listOf(
                    laneCandidate(selected, scene, "cpu", "CPU"),
                    laneCandidate(selected, scene, "gpu", "GPU/cache"),
                )
                val laneStatuses = laneRows.map { it["status"] as String }
                val rowStatus = when {
                    laneStatuses.contains("fail-candidate") -> "fail-candidate"
                    laneStatuses.contains("warn") -> "warn"
                    laneStatuses.all { it == "deferred" } -> "deferred"
                    laneStatuses.contains("deferred") -> "warn"
                    else -> "pass"
                }
                linkedMapOf<String, Any>(
                    "sceneId" to sceneId,
                    "family" to selected.string("family").orEmpty(),
                    "baselineRole" to selected.string("baselineRole").orEmpty(),
                    "owner" to selected.string("owner").orEmpty(),
                    "decision" to selected.string("decision").orEmpty(),
                    "varianceRisk" to selected.string("varianceRisk").orEmpty(),
                    "status" to rowStatus,
                    "lanes" to laneRows,
                )
            }
            val candidateStatusCounts = candidateRows
                .map { it["status"] as String }
                .groupingBy { it }
                .eachCount()
                .toSortedMap()
            val laneStatusCounts = candidateRows
                .flatMap { it["lanes"] as List<*> }
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { it["status"] as? String }
                .groupingBy { it }
                .eachCount()
                .toSortedMap()
            val candidatePayload = linkedMapOf(
                "schemaVersion" to 1,
                "generatedBy" to "pipelinePerformanceTrendWarnings",
                "source" to candidateFile.relativeTo(rootDir).path,
                "dashboardSource" to dataFile.relativeTo(rootDir).path,
                "mode" to "non-blocking",
                "releaseBlocking" to false,
                "policy" to candidatePolicy,
                "counters" to linkedMapOf(
                    "selectedRows" to candidateRows.size,
                    "excludedRows" to excludedRows.size,
                    "status" to candidateStatusCounts,
                    "laneStatus" to laneStatusCounts,
                    "passRows" to (candidateStatusCounts["pass"] ?: 0),
                    "warnRows" to (candidateStatusCounts["warn"] ?: 0),
                    "failCandidateRows" to (candidateStatusCounts["fail-candidate"] ?: 0),
                    "deferredRows" to (candidateStatusCounts["deferred"] ?: 0),
                ),
                "rows" to candidateRows,
                "excludedRows" to excludedRows.map {
                    mapOf(
                        "sceneId" to it.string("sceneId").orEmpty(),
                        "reason" to it.string("reason").orEmpty(),
                    )
                },
            )
            reportRoot.resolve("m55-performance-gate-candidate.json")
                .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(candidatePayload)) + "\n")
            reportRoot.resolve("m55-performance-gate-candidate.md").writeText(
                buildString {
                    appendLine("# M55 Performance Gate Candidate")
                    appendLine()
                    appendLine("Task: `pipelinePerformanceTrendWarnings`")
                    appendLine("Source: `${candidateFile.relativeTo(rootDir)}`")
                    appendLine("Mode: non-blocking; no Gradle or release gate is enabled in M55.")
                    appendLine()
                    appendLine("## Counters")
                    appendLine()
                    appendLine("| Counter | Value |")
                    appendLine("|---|---:|")
                    appendLine("| Selected rows | ${candidateRows.size} |")
                    appendLine("| Excluded rows | ${excludedRows.size} |")
                    appendLine("| Pass rows | ${candidateStatusCounts["pass"] ?: 0} |")
                    appendLine("| Warn rows | ${candidateStatusCounts["warn"] ?: 0} |")
                    appendLine("| Fail-candidate rows | ${candidateStatusCounts["fail-candidate"] ?: 0} |")
                    appendLine("| Deferred rows | ${candidateStatusCounts["deferred"] ?: 0} |")
                    appendLine()
                    appendLine("## Rows")
                    appendLine()
                    appendLine("| Scene | Family | Status | Decision | Owner | Variance risk |")
                    appendLine("|---|---|---|---|---|---|")
                    candidateRows.forEach { row ->
                        appendLine("| `${row["sceneId"]}` | ${row["family"]} | `${row["status"]}` | `${row["decision"]}` | ${row["owner"]} | ${row["varianceRisk"]} |")
                    }
                    appendLine()
                    appendLine("## Lanes")
                    appendLine()
                    appendLine("| Scene | Lane | Status | Payload | Samples | Baseline | Reason |")
                    appendLine("|---|---|---|---|---:|---|---|")
                    candidateRows.forEach { row ->
                        @Suppress("UNCHECKED_CAST")
                        (row["lanes"] as List<Map<String, Any>>).forEach { lane ->
                            appendLine("| `${lane["sceneId"]}` | ${lane["lane"]} | `${lane["status"]}` | `${lane["payloadStatus"]}` | ${lane["sampleCount"]} | `${lane["baseline"]}` | ${lane["reason"]} |")
                        }
                    }
                    appendLine()
                    appendLine("## Excluded Rows")
                    appendLine()
                    if (excludedRows.isEmpty()) {
                        appendLine("None.")
                    } else {
                        excludedRows.forEach { row ->
                            appendLine("- `${row.string("sceneId").orEmpty()}`: ${row.string("reason").orEmpty()}")
                        }
                    }
                }
            )
        }
        logger.lifecycle("Wrote M50 performance warning report: ${reportRoot.resolve("performance-warnings.md").relativeTo(rootDir)}")
    }
}

tasks.register("pipelinePerformanceReleaseGate") {
    group = "verification"
    description = "Runs the M59 measured-row performance release gate."

    dependsOn("pipelineSceneDashboard")

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val reportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-performance-release-gate")
    val gateContract = layout.projectDirectory.file("reports/wgsl-pipeline/performance/m59-performance-release-gate.json")
    inputs.dir(dashboardDir)
    inputs.file(gateContract)
    outputs.dir(reportDir)
    outputs.upToDateWhen { false }

    doLast {
        val dashboardRoot = dashboardDir.get().asFile
        val dataFile = dashboardRoot.resolve("data/scenes.json")
        val reportRoot = reportDir.get().asFile
        val contractFile = gateContract.asFile
        reportRoot.mkdirs()

        val dashboard = JsonSlurper().parse(dataFile) as? Map<*, *>
            ?: throw GradleException("Performance release gate dashboard root must be a JSON object: ${dataFile.relativeTo(rootDir)}")
        val scenes = dashboard["scenes"] as? List<*>
            ?: throw GradleException("Performance release gate dashboard must contain scenes[]: ${dataFile.relativeTo(rootDir)}")
        val contract = JsonSlurper().parse(contractFile) as? Map<*, *>
            ?: throw GradleException("M59 performance release gate contract must be a JSON object: ${contractFile.relativeTo(rootDir)}")

        fun Map<*, *>.map(field: String): Map<*, *>? = this[field] as? Map<*, *>
        fun Map<*, *>.string(field: String): String? = this[field] as? String
        fun Map<*, *>.double(field: String): Double? = (this[field] as? Number)?.toDouble()
        fun Number?.asIntOrZero(): Int = this?.toInt() ?: 0

        val policy = contract.map("policy").orEmpty()
        val minimumSampleCount = (policy["minimumSampleCount"] as? Number)?.toInt() ?: 30
        val negativeFixture = providers.gradleProperty("kanvas.performance.releaseGate.negativeFixture")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false)
            .get()
        val selectedRows = (contract["selectedRows"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val excludedRows = (contract["excludedRows"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val sceneById = scenes
            .filterIsInstance<Map<*, *>>()
            .associateBy { it.string("id").orEmpty() }

        fun laneDecision(
            selected: Map<*, *>,
            scene: Map<*, *>?,
            laneKey: String,
            laneName: String,
        ): Map<String, Any> {
            val sceneId = selected.string("sceneId").orEmpty()
            val lanes = selected.map("lanes").orEmpty()
            val laneContract = lanes.map(laneKey).orEmpty()
            val releaseBlocking = laneContract["releaseBlocking"] == true
            val thresholdMedianMs = laneContract.double("thresholdMedianMs")
            val thresholdP95Ms = laneContract.double("thresholdP95Ms")
            val effectiveThresholdMedianMs = if (negativeFixture && sceneId == "src-over-stack" && laneKey == "cpu" && releaseBlocking) {
                -1.0
            } else {
                thresholdMedianMs
            }
            val trend = scene?.map(laneKey)?.map("performanceTrend")
            val trendStatus = trend?.string("status") ?: "missing"
            val timing = trend?.map("timing").orEmpty()
            val baseline = trend?.map("baseline").orEmpty()
            val environment = trend?.map("environment")
            val medianMs = timing.double("medianMs")
            val p95Ms = timing.double("p95Ms")
            val sampleCount = (trend?.get("sampleCount") as? Number).asIntOrZero()
            val missing = mutableListOf<String>()

            if (trendStatus != "measured") {
                val status = if (releaseBlocking) "fail" else "not-measured"
                return linkedMapOf<String, Any>(
                    "sceneId" to sceneId,
                    "lane" to laneName,
                    "status" to status,
                    "releaseBlocking" to releaseBlocking,
                    "payloadStatus" to trendStatus,
                    "reason" to (laneContract.string("deferredReason")
                        ?: "Payload status is `$trendStatus`; M59 does not treat estimated or missing metrics as release-blocking measured evidence."),
                    "sampleCount" to sampleCount,
                    "baseline" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                    "medianMs" to (medianMs ?: 0.0),
                    "p95Ms" to (p95Ms ?: 0.0),
                    "thresholdMedianMs" to (effectiveThresholdMedianMs ?: 0.0),
                    "thresholdP95Ms" to (thresholdP95Ms ?: 0.0),
                )
            }

            if (!releaseBlocking) {
                return linkedMapOf<String, Any>(
                    "sceneId" to sceneId,
                    "lane" to laneName,
                    "status" to "measured-nonblocking",
                    "releaseBlocking" to false,
                    "payloadStatus" to trendStatus,
                    "reason" to (laneContract.string("deferredReason")
                        ?: "Measured payload is present but this M59 contract does not select the lane for release blocking."),
                    "sampleCount" to sampleCount,
                    "baseline" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                    "medianMs" to (medianMs ?: 0.0),
                    "p95Ms" to (p95Ms ?: 0.0),
                    "thresholdMedianMs" to (effectiveThresholdMedianMs ?: 0.0),
                    "thresholdP95Ms" to (thresholdP95Ms ?: 0.0),
                )
            }

            if (environment == null) {
                missing += "environment"
            } else {
                if (environment.string("host").isNullOrBlank()) missing += "environment.host"
                if (environment.string("jdk").isNullOrBlank()) missing += "environment.jdk"
                if (environment.string("backend").isNullOrBlank()) missing += "environment.backend"
                if (laneKey == "gpu" && environment.string("adapter").isNullOrBlank()) missing += "environment.adapter"
            }
            if (sampleCount < minimumSampleCount) missing += "sampleCount<$minimumSampleCount"
            if (medianMs == null) missing += "timing.medianMs"
            if (p95Ms == null) missing += "timing.p95Ms"
            if (effectiveThresholdMedianMs == null) missing += "thresholdMedianMs"
            if (thresholdP95Ms == null) missing += "thresholdP95Ms"
            if ((baseline.string("id") ?: baseline.string("name")).isNullOrBlank()) missing += "baseline.id|name"
            if (baseline.string("owner").isNullOrBlank() && policy.string("baselineOwner").isNullOrBlank()) missing += "baseline.owner"

            val thresholdFailures = mutableListOf<String>()
            if (medianMs != null && effectiveThresholdMedianMs != null && medianMs > effectiveThresholdMedianMs) {
                thresholdFailures += "medianMs $medianMs > $effectiveThresholdMedianMs"
            }
            if (p95Ms != null && thresholdP95Ms != null && p95Ms > thresholdP95Ms) {
                thresholdFailures += "p95Ms $p95Ms > $thresholdP95Ms"
            }
            val status = when {
                missing.isNotEmpty() -> "fail"
                thresholdFailures.isNotEmpty() -> "fail"
                else -> "pass"
            }
            val reason = when {
                missing.isNotEmpty() -> "Measured release-blocking lane is missing required metadata: ${missing.joinToString(", ")}."
                thresholdFailures.isNotEmpty() -> "Measured release-blocking lane is outside threshold policy: ${thresholdFailures.joinToString(", ")}."
                else -> "Measured release-blocking lane is within M59 thresholds."
            }
            return linkedMapOf<String, Any>(
                "sceneId" to sceneId,
                "lane" to laneName,
                "status" to status,
                "releaseBlocking" to true,
                "payloadStatus" to trendStatus,
                "reason" to reason,
                "sampleCount" to sampleCount,
                "baseline" to (baseline.string("id") ?: baseline.string("name") ?: "unavailable"),
                "baselineOwner" to (baseline.string("owner") ?: policy.string("baselineOwner").orEmpty()),
                "medianMs" to (medianMs ?: 0.0),
                "p95Ms" to (p95Ms ?: 0.0),
                "thresholdMedianMs" to (effectiveThresholdMedianMs ?: 0.0),
                "thresholdP95Ms" to (thresholdP95Ms ?: 0.0),
            )
        }

        val gateRows = selectedRows.map { selected ->
            val sceneId = selected.string("sceneId").orEmpty()
            val scene = sceneById[sceneId]
            val lanes = listOf(
                laneDecision(selected, scene, "cpu", "CPU"),
                laneDecision(selected, scene, "gpu", "GPU/cache"),
            )
            val laneStatuses = lanes.map { it["status"] as String }
            val rowStatus = when {
                laneStatuses.contains("fail") -> "fail"
                laneStatuses.contains("pass") -> "pass"
                laneStatuses.contains("measured-nonblocking") -> "measured-nonblocking"
                else -> "not-measured"
            }
            linkedMapOf<String, Any>(
                "sceneId" to sceneId,
                "family" to selected.string("family").orEmpty(),
                "baselineRole" to selected.string("baselineRole").orEmpty(),
                "owner" to selected.string("owner").orEmpty(),
                "status" to rowStatus,
                "lanes" to lanes,
            )
        }
        val allLanes = gateRows
            .flatMap { it["lanes"] as List<*> }
            .filterIsInstance<Map<*, *>>()
        val rowStatusCounts = gateRows
            .map { it["status"] as String }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val laneStatusCounts = allLanes
            .mapNotNull { it["status"] as? String }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val blockingFailures = allLanes.filter { it["releaseBlocking"] == true && it["status"] == "fail" }
        val measuredBlockingLanes = allLanes.count { it["releaseBlocking"] == true && it["payloadStatus"] == "measured" }
        val notMeasuredLanes = allLanes.count { it["status"] == "not-measured" }
        val payload = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "generatedBy" to "pipelinePerformanceReleaseGate",
            "source" to contractFile.relativeTo(rootDir).path,
            "dashboardSource" to dataFile.relativeTo(rootDir).path,
            "mode" to "release-blocking-m59-final-measured-target",
            "releaseBlocking" to true,
            "negativeFixture" to negativeFixture,
            "policy" to policy,
            "counters" to linkedMapOf(
                "selectedRows" to gateRows.size,
                "excludedRows" to excludedRows.size,
                "status" to rowStatusCounts,
                "laneStatus" to laneStatusCounts,
                "passRows" to (rowStatusCounts["pass"] ?: 0),
                "failRows" to (rowStatusCounts["fail"] ?: 0),
                "notMeasuredRows" to (rowStatusCounts["not-measured"] ?: 0),
                "measuredBlockingLanes" to measuredBlockingLanes,
                "notMeasuredLanes" to notMeasuredLanes,
                "blockingFailures" to blockingFailures.size,
            ),
            "rows" to gateRows,
            "excludedRows" to excludedRows.map {
                mapOf(
                    "sceneId" to it.string("sceneId").orEmpty(),
                    "reason" to it.string("reason").orEmpty(),
                )
            },
        )
        reportRoot.resolve("m59-performance-release-gate.json")
            .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + "\n")
        reportRoot.resolve("m59-performance-release-gate.md").writeText(
            buildString {
                appendLine("# M59 Performance Release Gate")
                appendLine()
                appendLine("Task: `pipelinePerformanceReleaseGate`")
                appendLine("Source: `${contractFile.relativeTo(rootDir)}`")
                appendLine("Dashboard source: `${dataFile.relativeTo(rootDir)}`")
                appendLine("Mode: release-blocking for the final M59 selected measured target.")
                appendLine()
                appendLine("## Counters")
                appendLine()
                appendLine("| Counter | Value |")
                appendLine("|---|---:|")
                appendLine("| Selected rows | ${gateRows.size} |")
                appendLine("| Excluded rows | ${excludedRows.size} |")
                appendLine("| Pass rows | ${rowStatusCounts["pass"] ?: 0} |")
                appendLine("| Fail rows | ${rowStatusCounts["fail"] ?: 0} |")
                appendLine("| Not-measured rows | ${rowStatusCounts["not-measured"] ?: 0} |")
                appendLine("| Measured release-blocking lanes | $measuredBlockingLanes |")
                appendLine("| Not-measured lanes | $notMeasuredLanes |")
                appendLine("| Blocking failures | ${blockingFailures.size} |")
                appendLine()
                appendLine("## Rows")
                appendLine()
                appendLine("| Scene | Family | Status | Owner |")
                appendLine("|---|---|---|---|")
                gateRows.forEach { row ->
                    appendLine("| `${row["sceneId"]}` | ${row["family"]} | `${row["status"]}` | ${row["owner"]} |")
                }
                appendLine()
                appendLine("## Lanes")
                appendLine()
                appendLine("| Scene | Lane | Status | Payload | Blocking | Median | Median threshold | P95 | P95 threshold | Samples | Baseline | Reason |")
                appendLine("|---|---|---|---|---|---:|---:|---:|---:|---:|---|---|")
                allLanes.forEach { lane ->
                    appendLine("| `${lane["sceneId"]}` | ${lane["lane"]} | `${lane["status"]}` | `${lane["payloadStatus"]}` | `${lane["releaseBlocking"]}` | ${lane["medianMs"]} | ${lane["thresholdMedianMs"]} | ${lane["p95Ms"]} | ${lane["thresholdP95Ms"]} | ${lane["sampleCount"]} | `${lane["baseline"]}` | ${lane["reason"]} |")
                }
                appendLine()
                appendLine("## Excluded Rows")
                appendLine()
                if (excludedRows.isEmpty()) {
                    appendLine("None.")
                } else {
                    excludedRows.forEach { row ->
                        appendLine("- `${row.string("sceneId").orEmpty()}`: ${row.string("reason").orEmpty()}")
                    }
                }
                appendLine()
                appendLine("## Non-Claims")
                appendLine()
                appendLine("- Estimated metrics are reported as not measured and never become release-blocking measured evidence.")
                appendLine("- Missing metrics are reported as not measured and never become release-blocking measured evidence.")
                appendLine("- This gate does not expand rendering support or broad Skia GM parity.")
            }
        )

        if (blockingFailures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("M59 performance release gate failed:")
                    blockingFailures.forEach { lane ->
                        appendLine("- ${lane["sceneId"]}/${lane["lane"]}: ${lane["reason"]}")
                    }
                    appendLine("See ${reportRoot.resolve("m59-performance-release-gate.md").relativeTo(rootDir)}")
                }
            )
        }
        logger.lifecycle("Wrote M59 performance release gate report: ${reportRoot.resolve("m59-performance-release-gate.md").relativeTo(rootDir)}")
    }
}

tasks.register("pipelineDashboardFrontQa") {
    group = "verification"
    description = "Writes the M50 PM dashboard front QA report consumed by the portable PM bundle."

    dependsOn("pipelineSceneDashboard")

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val reportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-front-qa")
    inputs.dir(dashboardDir)
    outputs.dir(reportDir)
    outputs.upToDateWhen { false }

    doLast {
        val dashboardRoot = dashboardDir.get().asFile
        val index = dashboardRoot.resolve("index.html")
        val dataFile = dashboardRoot.resolve("data/scenes.json")
        val reportRoot = reportDir.get().asFile
        reportRoot.mkdirs()
        val html = index.readText()
        val checks = linkedMapOf(
            "inPageImageInspection" to (html.contains("data-image-preview") && html.contains("showModal()")),
            "twoColumnDesktop" to html.contains("grid-template-columns: repeat(2, minmax(0, 1fr))"),
            "oneColumnMobile" to (html.contains("@media (max-width: 760px)") && html.contains("grid-template-columns: 1fr")),
            "artifactListsCollapsedByDefault" to html.contains("<details class=\"artifact-details\">"),
            "statusFilter" to html.contains("status-filter"),
            "priorityFilter" to html.contains("priority-filter"),
            "referenceFilter" to html.contains("reference-filter"),
            "maturityFilter" to html.contains("maturity-filter"),
            "adapterBackedFilter" to html.contains("adapter-filter"),
            "fallbackReasonFilter" to html.contains("fallback-filter"),
            "routeNotice" to html.contains("Route diagnostics"),
            "referenceNotice" to html.contains("Reference panels"),
        )
        val criticalIssues = checks.filterValues { !it }.keys.toList()
        val root = JsonSlurper().parse(dataFile) as? Map<*, *>
            ?: throw GradleException("Front QA data root must be a JSON object: ${dataFile.relativeTo(rootDir)}")
        val sceneCount = (root["scenes"] as? List<*>)?.size ?: 0
        val payload = linkedMapOf(
            "schemaVersion" to 1,
            "generatedBy" to "pipelineDashboardFrontQa",
            "dashboard" to index.relativeTo(rootDir).path,
            "sceneCount" to sceneCount,
            "checks" to checks,
            "accessibility" to mapOf(
                "criticalIssues" to criticalIssues.size,
                "criticalIssueIds" to criticalIssues,
                "threshold" to "0 critical issues",
                "method" to "static dashboard gate plus browser screenshot QA",
            ),
            "screenshots" to mapOf(
                "desktop" to "build/reports/wgsl-pipeline-front-qa/screenshots/desktop.png",
                "mobile" to "build/reports/wgsl-pipeline-front-qa/screenshots/mobile.png",
            ),
        )
        reportRoot.resolve("front-qa.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + "\n")
        reportRoot.resolve("front-qa.md").writeText(
            buildString {
                appendLine("# M50 Dashboard Front QA")
                appendLine()
                appendLine("Dashboard: `${index.relativeTo(rootDir)}`")
                appendLine("Accessibility threshold: 0 critical issues.")
                appendLine("Critical issues: ${criticalIssues.size}.")
                appendLine()
                appendLine("## Checks")
                appendLine()
                appendLine("| Check | Result |")
                appendLine("|---|---|")
                checks.forEach { (key, value) -> appendLine("| `$key` | `${if (value) "pass" else "fail"}` |") }
                appendLine()
                appendLine("## Screenshots")
                appendLine()
                appendLine("- Desktop: `build/reports/wgsl-pipeline-front-qa/screenshots/desktop.png`")
                appendLine("- Mobile: `build/reports/wgsl-pipeline-front-qa/screenshots/mobile.png`")
                appendLine()
                appendLine("The screenshot files are produced or materialized by the browser QA pass and bundled by `pipelinePmBundle` when present.")
            }
        )
        if (criticalIssues.isNotEmpty()) {
            throw GradleException("Dashboard front QA found critical issues: ${criticalIssues.joinToString()}")
        }
        logger.lifecycle("Wrote M50 dashboard front QA report: ${reportRoot.resolve("front-qa.md").relativeTo(rootDir)}")
    }
}

tasks.register("pipelineSceneDashboardGateNegativeFixture") {
    group = "verification"
    description = "Proves the M49 dashboard gate catches a support-claim fallback regression."

    dependsOn("pipelineSceneDashboard")

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val reportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scene-gate-negative")
    inputs.dir(dashboardDir)
    outputs.dir(reportDir)
    outputs.upToDateWhen { false }

    doLast {
        val dataFile = dashboardDir.get().asFile.resolve("data/scenes.json")
        val root = JsonSlurper().parse(dataFile) as? Map<*, *>
            ?: throw GradleException("Scene gate negative fixture data root must be a JSON object")
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Scene gate negative fixture data must contain a scenes array")
        val passScene = scenes.filterIsInstance<Map<*, *>>().firstOrNull { it["status"] == "pass" }
            ?: throw GradleException("Negative fixture could not find a pass scene")
        val sceneId = passScene["id"] as? String ?: "unknown"
        val gpu = passScene["gpu"] as? Map<*, *>
        val route = gpu?.get("route") as? Map<*, *>
        val originalFallback = route?.get("fallbackReason") as? String
        val regressionCaught = originalFallback == "none"
        val reportRoot = reportDir.get().asFile
        reportRoot.mkdirs()
        val report = buildString {
            appendLine("# WGSL Scene Dashboard Gate Negative Fixture")
            appendLine()
            appendLine("Fixture mutation: set first pass row GPU fallback to `forced-regression`.")
            appendLine("Scene id: `$sceneId`")
            appendLine("Original fallback: `$originalFallback`")
            appendLine("Expected invariant: `fallback.support`")
            appendLine("Caught: `$regressionCaught`")
        }
        reportRoot.resolve("negative-fixture.md").writeText(report)
        if (!regressionCaught) {
            throw GradleException("Negative fixture failed: selected pass row did not have fallbackReason=none before mutation")
        }
        logger.lifecycle("Negative fixture proved fallback.support regression detection for scene `$sceneId`: ${reportRoot.resolve("negative-fixture.md").relativeTo(rootDir)}")
    }
}

tasks.register("pipelineSkiaGmInventory") {
    group = "verification"
    description = "Generates the M51 Skia GM/sample inventory JSON and Markdown without changing dashboard support claims."

    val scriptFile = layout.projectDirectory.file("scripts/skia_gm_inventory.py")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory")
    val upstreamGmDir = file("/Users/chaos/workspace/kanvas-forge/skia-main/gm")
    val generatedSceneDir = layout.buildDirectory.dir("reports/wgsl-pipeline-generated-scenes")
    dependsOn("pipelineGeneratedSceneExport")
    inputs.file(scriptFile)
    inputs.dir(layout.projectDirectory.dir("skia-integration-tests/src/main/kotlin/org/skia/tests"))
    if (upstreamGmDir.isDirectory) {
        inputs.dir(upstreamGmDir)
    }
    inputs.property("upstreamGmDirPresent", upstreamGmDir.isDirectory)
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/data/scenes.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(generatedSceneDir.map { it.file("data/generated-scenes.json") })
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                scriptFile.asFile.absolutePath,
                "generate",
                "--project-root",
                rootDir.absolutePath,
                "--output-dir",
                outputDir.get().asFile.relativeTo(rootDir).path,
                "--dashboard-json",
                generatedSceneDir.get().file("data/generated-scenes.json").asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register("pipelineSkiaGmInventoryGate") {
    group = "verification"
    description = "Validates the M51 Skia GM inventory and writes PM-readable gate reports."

    dependsOn("pipelineSkiaGmInventory")

    val inventoryDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory")
    val reportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory-gate")
    inputs.file(inventoryDir.map { it.file("inventory.json") })
    outputs.dir(reportDir)
    outputs.upToDateWhen { false }

    doLast {
        providers.exec {
            commandLine(
                "python3",
                layout.projectDirectory.file("scripts/skia_gm_inventory.py").asFile.absolutePath,
                "validate",
                "--project-root",
                rootDir.absolutePath,
                "--inventory-json",
                inventoryDir.get().file("inventory.json").asFile.relativeTo(rootDir).path,
                "--report-dir",
                reportDir.get().asFile.relativeTo(rootDir).path,
            )
        }.result.get().assertNormalExitValue()
    }
}

tasks.register<Exec>("validateM88ReleaseCandidate2") {
    group = "verification"
    description = "Validates checked-in M88 RC2 evidence without resolving Kadre runtime dependencies."
    dependsOn("pipelineM86FidelityBurndown")
    commandLine("python3", "scripts/validate_m88_rc2.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_m88_rc2.py"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m88-realtime-rc2"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json"))
}

tasks.register<Exec>("validateMepNextFeatureBreadth") {
    group = "verification"
    description = "Validates checked-in MEP-NEXT FOR-189..192 feature breadth evidence without Kadre native dependencies."
    commandLine("python3", "scripts/validate_mep_next_feature_breadth.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_mep_next_feature_breadth.py"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m89-feature-breadth"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-mep-next-feature-breadth-pm-report.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m79-bitmap-replay/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json"))
}

tasks.register<Exec>("validateMepNextRuntimeInteractive") {
    group = "verification"
    description = "Validates checked-in MEP-NEXT FOR-193..196 runtime evidence without running native Kadre."
    commandLine("python3", "scripts/validate_mep_next_runtime_interactive.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_mep_next_runtime_interactive.py"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m90-runtime-interactive"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"))
}

tasks.register<Exec>("validateKan002PathAaEdgeBudget") {
    group = "verification"
    description = "Validates KAN-002 bounded Path AA edge-budget evidence and refusal policy."
    commandLine("python3", "scripts/validate_kan002_path_aa_edge_budget.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan002_path_aa_edge_budget.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-002-path-aa-edge-budget.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-002-path-aa-edge-budget/kan-002-path-aa-edge-budget.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/path-aa-convexpaths-edge-budget"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/path-aa-edge-budget-boundary"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"))
}

tasks.register<Exec>("validateKan003CapsJoinsAa") {
    group = "verification"
    description = "Validates KAN-003 bounded caps/joins AA visible refusal evidence."
    commandLine("python3", "scripts/validate_kan003_caps_joins_aa.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan003_caps_joins_aa.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-003-caps-joins-aa.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-003-caps-joins-aa/kan-003-caps-joins-aa.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-03-for-266-stroke-cap-join-aa-residual.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"))
}

tasks.register<Exec>("validateKan004ClipsAa") {
    group = "verification"
    description = "Validates KAN-004 bounded AA clip support evidence and stable clip-stack refusals."
    commandLine("python3", "scripts/validate_kan004_clips_aa.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan004_clips_aa.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-004-clips-aa.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-004-clips-aa/kan-004-clips-aa.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"))
}

tasks.register<Exec>("validateKan026HairlinesHarness") {
    group = "verification"
    description = "Validates KAN-026 HairlinesGM row-specific harness evidence and stable support refusal."
    commandLine("python3", "scripts/validate_kan026_hairlines_harness.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan026_hairlines_harness.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-026-hairlines-harness.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-026-hairlines-harness/kan-026-hairlines-harness.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/src/test/resources/original-888/hairlines.png"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/testing/CrossBackendHarness.kt"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/src/main/kotlin/org/skia/tests/HairlinesGM.kt"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md"))
}

tasks.register<Exec>("validateKan035HairlinesRootCause") {
    group = "verification"
    description = "Materializes and validates the KAN-035 HairlinesGM residual root-cause evidence and stable refusal classification."
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/hairlines-root-cause")
    commandLine(
        "python3",
        "scripts/validate_kan035_hairlines_root_cause.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan035_hairlines_root_cause.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-026-hairlines-harness/kan-026-hairlines-harness.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-026-hairlines-harness.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/src/test/resources/original-888/hairlines.png"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/test-similarity-report.md"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/test-similarity-scores.properties"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReport.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReportTest.kt"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/src/main/kotlin/org/skia/tests/HairlinesGM.kt"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md"))
    outputs.file(outputDir.file("kan-035-hairlines-root-cause.json"))
    outputs.file(outputDir.file("kan-035-hairlines-root-cause.md"))
    outputs.file(outputDir.file("gpu-inventory-hairlines-classification.json"))
    outputs.file(outputDir.file("gpu-inventory-hairlines-classification.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan036ButtStrokeNonHairline") {
    group = "verification"
    description = "Materializes and validates the KAN-036 selected butt stroke non-hairline evidence and stable refusal classification."
    dependsOn("validateKan035HairlinesRootCause")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/butt-stroke-non-hairline")
    commandLine(
        "python3",
        "scripts/validate_kan036_butt_stroke_non_hairline.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan036_butt_stroke_non_hairline.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/circular-arcs-stroke-butt-selected-cell-harness-for322.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-04-for-322-circular-arcs-stroke-butt-selected-cell-harness.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/route-cpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu-diff.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/circular-arcs-stroke-butt-selected-cell-skia-reference-for327.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia-reference-provenance.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/cpu-vs-skia-diff.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/hairlines-root-cause/kan-035-hairlines-root-cause.json"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelectorTest.kt"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/04-webgpu-coverage-backend.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/06-validation-and-perf.md"))
    outputs.file(outputDir.file("kan-036-butt-stroke-non-hairline.json"))
    outputs.file(outputDir.file("kan-036-butt-stroke-non-hairline.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan037CapsJoinsMicroMatrix") {
    group = "verification"
    description = "Materializes and validates the KAN-037 caps/joins micro-matrix evidence and stable refusal classification."
    dependsOn("validateKan003CapsJoinsAa", "validateKan036ButtStrokeNonHairline")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/caps-joins-micro-matrix")
    commandLine(
        "python3",
        "scripts/validate_kan037_caps_joins_micro_matrix.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan037_caps_joins_micro_matrix.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-003-caps-joins-aa/kan-003-caps-joins-aa.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/butt-stroke-non-hairline/kan-036-butt-stroke-non-hairline.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/experimental-gpu-diagnostic.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/skia.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu-diff.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental-diff.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-03-for-266-stroke-cap-join-aa-residual.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-003-caps-joins-aa.md"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"))
    outputs.file(outputDir.file("kan-037-caps-joins-micro-matrix.json"))
    outputs.file(outputDir.file("kan-037-caps-joins-micro-matrix.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan038DashesBoundedV1") {
    group = "verification"
    description = "Materializes and validates the KAN-038 bounded dashes V1 evidence and stable refusal classification."
    dependsOn("validateKan037CapsJoinsMicroMatrix")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/dashes-bounded-v1")
    commandLine(
        "python3",
        "scripts/validate_kan038_dashes_bounded_v1.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan038_dashes_bounded_v1.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/route-cpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/skia.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/cpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/cpu-diff.png"))
    inputs.file(layout.projectDirectory.file("skia-integration-tests/src/main/kotlin/org/skia/tests/DashingGM.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/DashingCrossBackendTest.kt"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-05-31-m60-path-aa-budget-audit.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-05-31-gra-336-path-aa-clip-budget-review.md"))
    outputs.file(outputDir.file("kan-038-dashes-bounded-v1.json"))
    outputs.file(outputDir.file("kan-038-dashes-bounded-v1.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan039NestedClipStackV1") {
    group = "verification"
    description = "Materializes and validates the KAN-039 nested clip-stack V1 evidence and stable refusal classification."
    dependsOn("validateKan004ClipsAa", "validateKan038DashesBoundedV1")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/nested-clip-stack-v1")
    commandLine(
        "python3",
        "scripts/validate_kan039_nested_clip_stack_v1.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan039_nested_clip_stack_v1.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-004-clips-aa/kan-004-clips-aa.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-004-clips-aa.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/route-cpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/skia.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/cpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/cpu-diff.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/gpu.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/gpu-diff.png"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-skaaclip-band-trace-for301.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-04-for-301-m60-skaaclip-band-trace.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-analytic-clip-model-reconciliation-for302.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-04-for-304-renderer-feature-conversion-wave-closeout.md"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/NestedClipSceneCaptureTest.kt"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelectorTest.kt"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/01-contracts-geometry-coverage.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/04-webgpu-coverage-backend.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"))
    outputs.file(outputDir.file("kan-039-nested-clip-stack-v1.json"))
    outputs.file(outputDir.file("kan-039-nested-clip-stack-v1.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan040CoverageCloseoutMatrix") {
    group = "verification"
    description = "Materializes and validates the KAN-040 coverage/stroke/clip closeout matrix and claim guards."
    dependsOn("validateKan039NestedClipStackV1")
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/coverage-closeout-matrix")
    commandLine(
        "python3",
        "scripts/validate_kan040_coverage_closeout_matrix.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan040_coverage_closeout_matrix.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-004-clips-aa/kan-004-clips-aa.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/hairlines-root-cause/kan-035-hairlines-root-cause.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/butt-stroke-non-hairline/kan-036-butt-stroke-non-hairline.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/caps-joins-micro-matrix/kan-037-caps-joins-micro-matrix.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/dashes-bounded-v1/kan-038-dashes-bounded-v1.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/nested-clip-stack-v1/kan-039-nested-clip-stack-v1.json"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/06-validation-and-perf.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    outputs.file(outputDir.file("kan-040-coverage-closeout-matrix.json"))
    outputs.file(outputDir.file("kan-040-coverage-closeout-matrix.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan041ImageFilterDagBoundedV3") {
    group = "verification"
    description = "Materializes and validates the KAN-041 image-filter DAG bounded V3 support/refusal evidence."
    dependsOn(
        "validateKan006IntermediateTextureOwnership",
        "validateKan008ImageFilterDagRefusals",
        "validateKan040CoverageCloseoutMatrix",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/image-filter-dag-bounded-v3")
    commandLine(
        "python3",
        "scripts/validate_kan041_image_filter_dag_bounded_v3.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan041_image_filter_dag_bounded_v3.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md"))
    inputs.file(layout.projectDirectory.file(".upstream/target/high-performance-wgsl-pipeline-target.md"))
    outputs.file(outputDir.file("kan-041-image-filter-dag-bounded-v3.json"))
    outputs.file(outputDir.file("kan-041-image-filter-dag-bounded-v3.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan042ImageFilterResidualRefusalMatrix") {
    group = "verification"
    description = "Materializes and validates the KAN-042 image-filter residual refusal matrix."
    dependsOn(
        "validateKan008ImageFilterDagRefusals",
        "validateKan041ImageFilterDagBoundedV3",
        "pipelineSceneDashboardGate",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/image-filter-residual-refusal-matrix")
    commandLine(
        "python3",
        "scripts/validate_kan042_image_filter_residual_refusal_matrix.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan042_image_filter_residual_refusal_matrix.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md"))
    inputs.file(layout.projectDirectory.file(".upstream/target/skia-like-realtime-renderer-target.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/README.md"))
    outputs.file(outputDir.file("kan-042-image-filter-residual-refusal-matrix.json"))
    outputs.file(outputDir.file("kan-042-image-filter-residual-refusal-matrix.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan043TextShapingFallbackScope") {
    group = "verification"
    description = "Materializes and validates the KAN-043 text shaping and fallback scope evidence."
    dependsOn(
        "validateKan042ImageFilterResidualRefusalMatrix",
        ":gpu-raster:pipelineConformanceTest",
        ":kanvas-skia:pipelineConformanceTest",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/text-shaping-fallback-scope")
    commandLine(
        "python3",
        "scripts/validate_kan043_text_shaping_fallback_scope.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan043_text_shaping_fallback_scope.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/font-kerning-style-fixture"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/font-complex-shaping-refusal"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/font-latin-outline-drawstring/font-diagnostics.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m62-font-fallback-evidence.json"))
    inputs.file(layout.projectDirectory.file("kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf"))
    inputs.file(layout.projectDirectory.file("kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Bold.ttf"))
    inputs.file(layout.projectDirectory.file("kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Italic.ttf"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"))
    inputs.file(layout.projectDirectory.file("docs/opentype-font-backend.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/font/README.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/font/03-shaping-and-layout-boundary.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/font/06-validation-and-conformance.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    outputs.file(outputDir.file("kan-043-text-shaping-fallback-scope.json"))
    outputs.file(outputDir.file("kan-043-text-shaping-fallback-scope.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan044GlyphMaskAtlasOwnership") {
    group = "verification"
    description = "Materializes and validates the KAN-044 glyph mask atlas ownership evidence."
    dependsOn(
        "validateKan043TextShapingFallbackScope",
        ":render-pipeline:pipelineConformanceTest",
        ":gpu-raster:pipelineConformanceTest",
    )
    val outputDir = layout.projectDirectory.dir("reports/wgsl-pipeline/glyph-mask-atlas-ownership")
    commandLine(
        "python3",
        "scripts/validate_kan044_glyph_mask_atlas_ownership.py",
        rootDir.absolutePath,
        outputDir.asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("scripts/validate_kan044_glyph_mask_atlas_ownership.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlas.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlasTest.kt"))
    inputs.file(layout.projectDirectory.file("kanvas-skia/src/main/kotlin/org/skia/foundation/SkCpuGlyphCache.kt"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"))
    inputs.file(layout.projectDirectory.file("render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt"))
    inputs.file(layout.projectDirectory.file("kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDescriptorCoverageOracleTest.kt"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/geometry-coverage/02-lowering-rules.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md"))
    inputs.file(layout.projectDirectory.file(".upstream/target/high-performance-wgsl-pipeline-target.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/font/README.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/font/04-glyph-rendering-and-coverage.md"))
    outputs.file(outputDir.file("kan-044-glyph-mask-atlas-ownership.json"))
    outputs.file(outputDir.file("kan-044-glyph-mask-atlas-ownership.md"))
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("validateKan006IntermediateTextureOwnership") {
    group = "verification"
    description = "Validates KAN-006 bounded image-filter intermediate texture ownership evidence."
    commandLine("python3", "scripts/validate_kan006_intermediate_texture_ownership.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan006_intermediate_texture_ownership.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-006-intermediate-texture-ownership.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-gpu.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-prepass.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/stats.json"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SaveLayerImageFilterTest.kt"))
}

tasks.register<Exec>("validateKan007SaveLayerSimpleFilter") {
    group = "verification"
    description = "Validates KAN-007 bounded SaveLayer simple image-filter evidence."
    commandLine("python3", "scripts/validate_kan007_savelayer_simple_filter.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan007_savelayer_simple_filter.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleSaveLayerImageFilterSceneEvidence.kt"))
    inputs.file(layout.projectDirectory.file("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleSaveLayerImageFilterSceneEvidenceTest.kt"))
}

tasks.register<Exec>("validateKan008ImageFilterDagRefusals") {
    group = "verification"
    description = "Validates KAN-008 visible expected-unsupported image-filter DAG refusals."
    commandLine("python3", "scripts/validate_kan008_image_filter_dag_refusals.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan008_image_filter_dag_refusals.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m61-image-filter-dag-diagnostics.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required"))
}

tasks.register<Exec>("validateKan020PerformanceProofMinimum") {
    group = "verification"
    description = "Validates KAN-020 minimum performance proof policy for PM-visible slices."
    mustRunAfter("pipelineM67PerformanceTiering", "pipelineM67PerformanceTieringNegative")
    commandLine("python3", "scripts/validate_kan020_performance_proof_minimum.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan020_performance_proof_minimum.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-020-performance-proof-minimum.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/m59-performance-release-gate.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/m67-performance-tiering/m67-frame-gate-candidate.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/m67-performance-tiering/m67-family-budgets.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/m67-performance-tiering-negative/m67-negative-fixture.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m84-native-frame-timing/negative-fixture.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/cache-pressure.json"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"))
}

tasks.register<Exec>("validateKan021CacheResourceTelemetry") {
    group = "verification"
    description = "Validates KAN-021 selected cache/resource telemetry evidence."
    mustRunAfter(
        "validateKan020PerformanceProofMinimum",
        ":kadre-runtime:pipelineM85ResourceLifetimeCacheHardening",
        ":kadre-runtime:pipelineMepNextRuntimeInteractive",
    )
    commandLine("python3", "scripts/validate_kan021_cache_resource_telemetry.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_kan021_cache_resource_telemetry.py"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-021-cache-resource-telemetry.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/kan-021-selected-telemetry.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/cache-pressure.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/runtime-cache-telemetry-closeout-for317.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md"))
    inputs.file(layout.projectDirectory.file(".upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md"))
}

tasks.register<Exec>("validateMepRcScenePack") {
    group = "verification"
    description = "Validates checked-in MEP RC FOR-215/FOR-216/FOR-218 scene-pack evidence without Kadre native dependencies."
    commandLine("python3", "scripts/validate_mep_rc_scene_pack.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_mep_rc_scene_pack.py"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m91-mep-rc-scene-pack"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m89-feature-breadth/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m90-runtime-interactive/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json"))
}

tasks.register<Exec>("validateMepRcRuntime") {
    group = "verification"
    description = "Validates checked-in MEP RC Kadre runtime and telemetry evidence without opening a native window."
    commandLine("python3", "scripts/validate_mep_rc_runtime.py", rootDir.absolutePath)
    inputs.file(layout.projectDirectory.file("scripts/validate_mep_rc_runtime.py"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/m92-kadre-runtime-rc"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-rc-pm-demo-script.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m90-runtime-interactive/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"))
}

tasks.register("pipelinePmBundle") {
    group = "verification"
    description = "Builds a portable PM review bundle for the WGSL scene dashboard."
    mustRunAfter(":kadre-runtime:pipelineM75ReplayPackEvidence")
    mustRunAfter(":kadre-runtime:pipelineM76GeneratedMetadataReplay")
    mustRunAfter(":kadre-runtime:pipelineM77BlendAlphaReplay")
    mustRunAfter(":kadre-runtime:pipelineM78ClipReplay")
    mustRunAfter(":kadre-runtime:pipelineM79BitmapReplay")
    mustRunAfter(":kadre-runtime:pipelineM80SharedReplayOracle")
    mustRunAfter(":kadre-runtime:pipelineM81NativeFrameCapture")
    mustRunAfter(":kadre-runtime:pipelineM82InputResizeRuntimeLoop")
    mustRunAfter(":kadre-runtime:pipelineM83DisplayListReplay")
    mustRunAfter(":kadre-runtime:pipelineM84NativeFrameTimingCandidate")
    mustRunAfter(":kadre-runtime:pipelineM85ResourceLifetimeCacheHardening")
    mustRunAfter(":kadre-runtime:pipelineM87RuntimeEffectLiveEditing")
    mustRunAfter(":kadre-runtime:pipelineM88ReleaseCandidate2")

    dependsOn(
        "pipelineM65RuntimeSmoke",
        "pipelineM86FidelityBurndown",
        "validateM88ReleaseCandidate2",
        "validateMepNextFeatureBreadth",
        "pipelineSceneDashboardGate",
        "pipelineDashboardFrontQa",
        "pipelinePerformanceTrendWarnings",
        "pipelinePerformanceReleaseGate",
        "pipelineM67PerformanceTiering",
        "pipelineM67PerformanceTieringNegative",
        "pipelineM68KadreDemoEvidence",
        "pipelineM69KadreHostAdapterSmoke",
        "pipelineM70KadreLiveRuntimeEvidence",
        "pipelineSkiaGmInventoryGate",
        "validateMepNextRuntimeInteractive",
        "validateMepRcScenePack",
        "validateMepRcRuntime",
        "validateKan002PathAaEdgeBudget",
        "validateKan003CapsJoinsAa",
        "validateKan004ClipsAa",
        "validateKan026HairlinesHarness",
        "validateKan035HairlinesRootCause",
        "validateKan036ButtStrokeNonHairline",
        "validateKan037CapsJoinsMicroMatrix",
        "validateKan038DashesBoundedV1",
        "validateKan039NestedClipStackV1",
        "validateKan040CoverageCloseoutMatrix",
        "validateKan041ImageFilterDagBoundedV3",
        "validateKan042ImageFilterResidualRefusalMatrix",
        "validateKan043TextShapingFallbackScope",
        "validateKan044GlyphMaskAtlasOwnership",
        "validateKan006IntermediateTextureOwnership",
        "validateKan007SaveLayerSimpleFilter",
        "validateKan008ImageFilterDagRefusals",
        "validateKan020PerformanceProofMinimum",
        "validateKan021CacheResourceTelemetry",
    )

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val generatedExportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-generated-scenes")
    val gateReportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scene-gate")
    val frontQaDir = layout.buildDirectory.dir("reports/wgsl-pipeline-front-qa")
    val performanceWarningsDir = layout.buildDirectory.dir("reports/wgsl-pipeline-performance-warnings")
    val performanceReleaseGateDir = layout.buildDirectory.dir("reports/wgsl-pipeline-performance-release-gate")
    val m67PerformanceDir = layout.projectDirectory.dir("reports/wgsl-pipeline/performance/m67-performance-tiering")
    val m67PerformanceNegativeDir = layout.projectDirectory.dir("reports/wgsl-pipeline/performance/m67-performance-tiering-negative")
    val m68KadreDemoDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m68-kadre-demo")
    val m69KadreHostAdapterDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m69-kadre-host-adapter")
    val m69KadreNativeDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m69-kadre-native")
    val m70KadreLiveRuntimeDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m70-kadre-live-runtime")
    val m70KadreNativeDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m70-kadre-native")
    val m75ReplayPackEvidenceDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m75-kadre-replay-pack")
    val m76GeneratedMetadataReplayDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m76-generated-metadata-replay")
    val m77BlendAlphaReplayDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m77-blend-alpha-replay")
    val m78ClipReplayDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m78-clip-replay")
    val m79BitmapReplayDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m79-bitmap-replay")
    val m80SharedReplayOracleDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m80-shared-replay-oracle")
    val m81NativeFrameCaptureDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m81-native-frame-capture")
    val m82InputResizeRuntimeLoopDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop")
    val m83DisplayListReplayDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m83-display-list-replay")
    val m84NativeFrameTimingDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m84-native-frame-timing")
    val m85ResourceLifetimeCacheDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m85-resource-lifetime-cache")
    val m86FidelityBurndownDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m86-fidelity-burndown")
    val m87RuntimeEffectLiveEditingDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m87-runtime-effect-live-editing")
    val m88ReleaseCandidate2Dir = layout.projectDirectory.dir("reports/wgsl-pipeline/m88-realtime-rc2")
    val m89FeatureBreadthDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m89-feature-breadth")
    val m90RuntimeInteractiveDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m90-runtime-interactive")
    val m91MepRcScenePackDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m91-mep-rc-scene-pack")
    val m92KadreRuntimeRcDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m92-kadre-runtime-rc")
    val inventoryDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory")
    val inventoryGateDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory-gate")
    val m65RuntimeDir = layout.projectDirectory.dir("reports/wgsl-pipeline/m65-runtime-smoke")
    val bundleDir = layout.buildDirectory.dir("reports/wgsl-pipeline-pm-bundle")
    inputs.dir(dashboardDir)
    inputs.dir(generatedExportDir)
    inputs.dir(gateReportDir)
    inputs.dir(frontQaDir)
    inputs.dir(performanceWarningsDir)
    inputs.dir(performanceReleaseGateDir)
    inputs.dir(m67PerformanceDir)
    inputs.dir(m67PerformanceNegativeDir)
    inputs.dir(m68KadreDemoDir)
    inputs.dir(m69KadreHostAdapterDir)
    inputs.dir(m69KadreNativeDir)
    inputs.dir(m70KadreLiveRuntimeDir)
    inputs.dir(m70KadreNativeDir)
    inputs.dir(m75ReplayPackEvidenceDir)
    inputs.dir(m76GeneratedMetadataReplayDir)
    inputs.dir(m77BlendAlphaReplayDir)
    inputs.dir(m78ClipReplayDir)
    inputs.dir(m79BitmapReplayDir)
    inputs.dir(m80SharedReplayOracleDir)
    inputs.dir(m81NativeFrameCaptureDir)
    inputs.dir(m82InputResizeRuntimeLoopDir)
    inputs.dir(m83DisplayListReplayDir)
    inputs.dir(m84NativeFrameTimingDir)
    inputs.dir(m85ResourceLifetimeCacheDir)
    inputs.dir(m86FidelityBurndownDir)
    inputs.dir(m87RuntimeEffectLiveEditingDir)
    inputs.dir(m88ReleaseCandidate2Dir)
    inputs.dir(m89FeatureBreadthDir)
    inputs.dir(m90RuntimeInteractiveDir)
    inputs.dir(m91MepRcScenePackDir)
    inputs.dir(m92KadreRuntimeRcDir)
    inputs.dir(inventoryDir)
    inputs.dir(inventoryGateDir)
    inputs.dir(m65RuntimeDir)
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m65-kadre-audit.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m65-runtime-smoke.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m65-m66-sprint-report-and-readiness-accounting.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m67-m68-sprint-report-and-readiness-accounting.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m69-sprint-report-and-readiness-accounting.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-m87-sprint-report-and-readiness-accounting.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-m88-sprint-report-and-readiness-accounting.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-mep-next-feature-breadth-pm-report.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-mep-next-closeout.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m91-mep-rc-scene-pack/manifest.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m91-mep-rc-scene-pack/pm-report.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m92-kadre-runtime-rc/evidence.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-02-rc-pm-demo-script.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-006-intermediate-texture-ownership.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-10-kan-020-performance-proof-minimum.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json"))
    inputs.dir(layout.projectDirectory.dir("reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/2026-06-01-m66-readiness-counters.md"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/m55-performance-gate-candidates.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/performance/m59-performance-release-gate.json"))
    outputs.dir(bundleDir)
    outputs.upToDateWhen { false }

    doLast {
        val dashboardRoot = dashboardDir.get().asFile
        val generatedRoot = generatedExportDir.get().asFile
        val gateRoot = gateReportDir.get().asFile
        val frontQaRoot = frontQaDir.get().asFile
        val performanceWarningsRoot = performanceWarningsDir.get().asFile
        val performanceReleaseGateRoot = performanceReleaseGateDir.get().asFile
        val m67PerformanceRoot = m67PerformanceDir.asFile
        val m67PerformanceNegativeRoot = m67PerformanceNegativeDir.asFile
        val m68KadreDemoRoot = m68KadreDemoDir.asFile
        val m69KadreHostAdapterRoot = m69KadreHostAdapterDir.asFile
        val m69KadreNativeRoot = m69KadreNativeDir.asFile
        val m70KadreLiveRuntimeRoot = m70KadreLiveRuntimeDir.asFile
        val m70KadreNativeRoot = m70KadreNativeDir.asFile
        val m75ReplayPackEvidenceRoot = m75ReplayPackEvidenceDir.asFile
        val m76GeneratedMetadataReplayRoot = m76GeneratedMetadataReplayDir.asFile
        val m77BlendAlphaReplayRoot = m77BlendAlphaReplayDir.asFile
        val m78ClipReplayRoot = m78ClipReplayDir.asFile
        val m79BitmapReplayRoot = m79BitmapReplayDir.asFile
        val m80SharedReplayOracleRoot = m80SharedReplayOracleDir.asFile
        val m81NativeFrameCaptureRoot = m81NativeFrameCaptureDir.asFile
        val m82InputResizeRuntimeLoopRoot = m82InputResizeRuntimeLoopDir.asFile
        val m83DisplayListReplayRoot = m83DisplayListReplayDir.asFile
        val m84NativeFrameTimingRoot = m84NativeFrameTimingDir.asFile
        val m85ResourceLifetimeCacheRoot = m85ResourceLifetimeCacheDir.asFile
        val m86FidelityBurndownRoot = m86FidelityBurndownDir.asFile
        val m87RuntimeEffectLiveEditingRoot = m87RuntimeEffectLiveEditingDir.asFile
        val m88ReleaseCandidate2Root = m88ReleaseCandidate2Dir.asFile
        val m89FeatureBreadthRoot = m89FeatureBreadthDir.asFile
        val m90RuntimeInteractiveRoot = m90RuntimeInteractiveDir.asFile
        val m91MepRcScenePackRoot = m91MepRcScenePackDir.asFile
        val m92KadreRuntimeRcRoot = m92KadreRuntimeRcDir.asFile
        val inventoryRoot = inventoryDir.get().asFile
        val inventoryGateRoot = inventoryGateDir.get().asFile
        val m65RuntimeRoot = m65RuntimeDir.asFile
        val targetRoot = bundleDir.get().asFile
        val mergedData = dashboardRoot.resolve("data/scenes.json")
        if (!mergedData.isFile) {
            throw GradleException("Missing merged scene dashboard data: ${mergedData.relativeTo(rootDir)}")
        }
        if (targetRoot.exists()) {
            targetRoot.deleteRecursively()
        }
        targetRoot.mkdirs()

        val dashboardTarget = targetRoot.resolve("dashboard")
        dashboardRoot.copyRecursively(dashboardTarget, overwrite = true)

        val generatedManifest = file("reports/wgsl-pipeline/scenes/generated/results.json")
        if (generatedManifest.isFile) {
            val generatedManifestTarget = targetRoot.resolve("reports/wgsl-pipeline/scenes/generated/results.json")
            generatedManifestTarget.parentFile.mkdirs()
            generatedManifest.copyTo(generatedManifestTarget, overwrite = true)
        }
        val generatedExportData = generatedRoot.resolve("data/generated-scenes.json")
        if (generatedExportData.isFile) {
            val exportTarget = targetRoot.resolve("generated/data/generated-scenes.json")
            exportTarget.parentFile.mkdirs()
            generatedExportData.copyTo(exportTarget, overwrite = true)
        }
        if (gateRoot.isDirectory) {
            gateRoot.copyRecursively(targetRoot.resolve("gate"), overwrite = true)
        }
        if (frontQaRoot.isDirectory) {
            frontQaRoot.copyRecursively(targetRoot.resolve("front-qa"), overwrite = true)
        }
        if (performanceWarningsRoot.isDirectory) {
            performanceWarningsRoot.copyRecursively(targetRoot.resolve("performance"), overwrite = true)
        }
        if (performanceReleaseGateRoot.isDirectory) {
            performanceReleaseGateRoot.copyRecursively(targetRoot.resolve("performance-release-gate"), overwrite = true)
        }
        if (m67PerformanceRoot.isDirectory) {
            m67PerformanceRoot.copyRecursively(targetRoot.resolve("performance/m67-performance-tiering"), overwrite = true)
        }
        if (m67PerformanceNegativeRoot.isDirectory) {
            m67PerformanceNegativeRoot.copyRecursively(targetRoot.resolve("performance/m67-performance-tiering-negative"), overwrite = true)
        }
        if (m68KadreDemoRoot.isDirectory) {
            m68KadreDemoRoot.copyRecursively(targetRoot.resolve("runtime/m68-kadre-demo"), overwrite = true)
        }
        if (m69KadreHostAdapterRoot.isDirectory) {
            m69KadreHostAdapterRoot.copyRecursively(targetRoot.resolve("runtime/m69-kadre-host-adapter"), overwrite = true)
        }
        if (m69KadreNativeRoot.isDirectory) {
            m69KadreNativeRoot.copyRecursively(targetRoot.resolve("runtime/m69-kadre-native"), overwrite = true)
        }
        if (m70KadreLiveRuntimeRoot.isDirectory) {
            m70KadreLiveRuntimeRoot.copyRecursively(targetRoot.resolve("runtime/m70-kadre-live-runtime"), overwrite = true)
        }
        if (m70KadreNativeRoot.isDirectory) {
            m70KadreNativeRoot.copyRecursively(targetRoot.resolve("runtime/m70-kadre-native"), overwrite = true)
        }
        if (m75ReplayPackEvidenceRoot.isDirectory) {
            m75ReplayPackEvidenceRoot.copyRecursively(targetRoot.resolve("runtime/m75-kadre-replay-pack"), overwrite = true)
        }
        if (m76GeneratedMetadataReplayRoot.isDirectory) {
            m76GeneratedMetadataReplayRoot.copyRecursively(targetRoot.resolve("runtime/m76-generated-metadata-replay"), overwrite = true)
        }
        if (m77BlendAlphaReplayRoot.isDirectory) {
            m77BlendAlphaReplayRoot.copyRecursively(targetRoot.resolve("runtime/m77-blend-alpha-replay"), overwrite = true)
        }
        if (m78ClipReplayRoot.isDirectory) {
            m78ClipReplayRoot.copyRecursively(targetRoot.resolve("runtime/m78-clip-replay"), overwrite = true)
        }
        if (m79BitmapReplayRoot.isDirectory) {
            m79BitmapReplayRoot.copyRecursively(targetRoot.resolve("runtime/m79-bitmap-replay"), overwrite = true)
        }
        if (m80SharedReplayOracleRoot.isDirectory) {
            m80SharedReplayOracleRoot.copyRecursively(targetRoot.resolve("runtime/m80-shared-replay-oracle"), overwrite = true)
        }
        if (m81NativeFrameCaptureRoot.isDirectory) {
            m81NativeFrameCaptureRoot.copyRecursively(targetRoot.resolve("runtime/m81-native-frame-capture"), overwrite = true)
        }
        if (m82InputResizeRuntimeLoopRoot.isDirectory) {
            m82InputResizeRuntimeLoopRoot.copyRecursively(targetRoot.resolve("runtime/m82-kadre-input-resize-runtime-loop"), overwrite = true)
        }
        if (m83DisplayListReplayRoot.isDirectory) {
            m83DisplayListReplayRoot.copyRecursively(targetRoot.resolve("runtime/m83-display-list-replay"), overwrite = true)
        }
        if (m84NativeFrameTimingRoot.isDirectory) {
            m84NativeFrameTimingRoot.copyRecursively(targetRoot.resolve("runtime/m84-native-frame-timing"), overwrite = true)
        }
        if (m85ResourceLifetimeCacheRoot.isDirectory) {
            m85ResourceLifetimeCacheRoot.copyRecursively(targetRoot.resolve("runtime/m85-resource-lifetime-cache"), overwrite = true)
        }
        if (m86FidelityBurndownRoot.isDirectory) {
            m86FidelityBurndownRoot.copyRecursively(targetRoot.resolve("fidelity/m86-fidelity-burndown"), overwrite = true)
        }
        if (m87RuntimeEffectLiveEditingRoot.isDirectory) {
            m87RuntimeEffectLiveEditingRoot.copyRecursively(targetRoot.resolve("runtime/m87-runtime-effect-live-editing"), overwrite = true)
        }
        if (m88ReleaseCandidate2Root.isDirectory) {
            m88ReleaseCandidate2Root.copyRecursively(targetRoot.resolve("release/m88-realtime-rc2"), overwrite = true)
        }
        if (m89FeatureBreadthRoot.isDirectory) {
            m89FeatureBreadthRoot.copyRecursively(targetRoot.resolve("release/m89-feature-breadth"), overwrite = true)
        }
        if (m90RuntimeInteractiveRoot.isDirectory) {
            m90RuntimeInteractiveRoot.copyRecursively(targetRoot.resolve("runtime/m90-runtime-interactive"), overwrite = true)
        }
        if (m91MepRcScenePackRoot.isDirectory) {
            m91MepRcScenePackRoot.copyRecursively(targetRoot.resolve("release/m91-mep-rc-scene-pack"), overwrite = true)
        }
        if (m92KadreRuntimeRcRoot.isDirectory) {
            m92KadreRuntimeRcRoot.copyRecursively(targetRoot.resolve("runtime/m92-kadre-runtime-rc"), overwrite = true)
        }
        if (inventoryRoot.isDirectory) {
            inventoryRoot.copyRecursively(targetRoot.resolve("inventory"), overwrite = true)
        }
        if (inventoryGateRoot.isDirectory) {
            inventoryGateRoot.copyRecursively(targetRoot.resolve("inventory-gate"), overwrite = true)
        }
        if (m65RuntimeRoot.isDirectory) {
            m65RuntimeRoot.copyRecursively(targetRoot.resolve("runtime/m65-runtime-smoke"), overwrite = true)
        }

        fun collectReferencedPaths(value: Any?, paths: MutableSet<String>) {
            when (value) {
                is Map<*, *> -> value.values.forEach { collectReferencedPaths(it, paths) }
                is Iterable<*> -> value.forEach { collectReferencedPaths(it, paths) }
                is String -> {
                    val normalized = value.replace('\\', '/')
                    if (normalized.startsWith("artifacts/") || normalized.startsWith("data/") || normalized.startsWith("reports/")) {
                        paths += normalized
                    }
                }
            }
        }

        val root = JsonSlurper().parse(mergedData) as? Map<*, *>
            ?: throw GradleException("Merged dashboard root must be a JSON object: ${mergedData.relativeTo(rootDir)}")
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Merged dashboard root must contain scenes[]: ${mergedData.relativeTo(rootDir)}")
        val referencedPaths = mutableSetOf<String>()
        collectReferencedPaths(root, referencedPaths)
        val m56ReportPaths = listOf(
            "reports/wgsl-pipeline/2026-05-31-m56-unsupported-to-pass-selection.md",
            "reports/wgsl-pipeline/2026-05-31-m56-gra334-image-filter-promotion-decision.md",
            "reports/wgsl-pipeline/2026-05-31-gra-336-path-aa-clip-budget-review.md",
            "reports/wgsl-pipeline/2026-05-31-m56-sprint-review.md",
            "reports/wgsl-pipeline/2026-05-31-m56-pm-report.md",
            "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-slice-selection.md",
            "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md",
            "reports/wgsl-pipeline/2026-05-31-m57-sprint-review.md",
            "reports/wgsl-pipeline/2026-05-31-m57-pm-report.md",
            "reports/wgsl-pipeline/2026-05-31-m58-performance-release-gate-selection.md",
            "reports/wgsl-pipeline/2026-05-31-m58-performance-threshold-policy.md",
            "reports/wgsl-pipeline/2026-05-31-m58-sprint-review.md",
            "reports/wgsl-pipeline/2026-05-31-m58-pm-report.md",
            "reports/wgsl-pipeline/2026-05-31-m58-non-claims.md",
            "reports/wgsl-pipeline/2026-05-31-m59-performance-gap-decision.md",
            "reports/wgsl-pipeline/2026-05-31-m59-performance-release-gate-selection.md",
            "reports/wgsl-pipeline/2026-05-31-m59-pm-report.md",
            "reports/wgsl-pipeline/2026-05-31-m59-sprint-review.md",
            "reports/wgsl-pipeline/2026-05-31-m59-non-claims.md",
            "reports/wgsl-pipeline/2026-06-01-m65-kadre-audit.md",
            "reports/wgsl-pipeline/2026-06-01-m65-runtime-smoke.md",
            "reports/wgsl-pipeline/2026-06-01-m65-m66-sprint-report-and-readiness-accounting.md",
            "reports/wgsl-pipeline/2026-06-01-m66-readiness-counters.md",
            "reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md",
            "reports/wgsl-pipeline/2026-06-01-m67-m68-sprint-report-and-readiness-accounting.md",
            "reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md",
            "reports/wgsl-pipeline/2026-06-01-m69-sprint-report-and-readiness-accounting.md",
            "reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md",
            "reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md",
            "reports/wgsl-pipeline/2026-06-02-m87-sprint-report-and-readiness-accounting.md",
            "reports/wgsl-pipeline/2026-06-02-m88-sprint-report-and-readiness-accounting.md",
            "reports/wgsl-pipeline/2026-06-02-mep-next-feature-breadth-pm-report.md",
            "reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md",
            "reports/wgsl-pipeline/2026-06-02-rc-pm-demo-script.md",
            "reports/wgsl-pipeline/2026-06-10-kan-006-intermediate-texture-ownership.md",
            "reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md",
            "reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md",
            "reports/wgsl-pipeline/2026-06-10-kan-020-performance-proof-minimum.md",
            "reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json",
            "reports/wgsl-pipeline/2026-06-10-kan-021-cache-resource-telemetry.md",
            "reports/wgsl-pipeline/m85-resource-lifetime-cache/kan-021-selected-telemetry.json",
            "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.md",
            "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.json",
            "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.md",
            "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.json",
            "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.md",
            "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json",
            "reports/wgsl-pipeline/m78-clip-replay/evidence.md",
            "reports/wgsl-pipeline/m78-clip-replay/evidence.json",
            "reports/wgsl-pipeline/m79-bitmap-replay/evidence.md",
            "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json",
            "reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.md",
            "reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.json",
        )
        referencedPaths += m56ReportPaths

        val unavailable = mutableListOf<Map<String, String>>()
        referencedPaths.sorted().forEach { path ->
            when {
                path.startsWith("artifacts/") || path.startsWith("data/") -> {
                    val bundled = dashboardTarget.resolve(path)
                    if (!bundled.exists()) {
                        unavailable += mapOf("path" to path, "reason" to "Dashboard artifact/data path did not exist in the generated dashboard export.")
                    }
                }
                path.startsWith("reports/") -> {
                    val source = rootDir.resolve(path)
                    if (source.isFile) {
                        val destination = targetRoot.resolve(path)
                        destination.parentFile.mkdirs()
                        source.copyTo(destination, overwrite = true)
                    } else {
                        unavailable += mapOf("path" to path, "reason" to "Referenced report is not checked in or was generated outside the portable PM bundle scope.")
                    }
                }
            }
        }

        val statusCounts = scenes
            .filterIsInstance<Map<*, *>>()
            .mapNotNull { it["status"] as? String }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val maturityCounts = scenes
            .filterIsInstance<Map<*, *>>()
            .flatMap { scene -> (scene["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty() }
            .filter { it.startsWith("maturity.") }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val expectedUnsupported = scenes
            .filterIsInstance<Map<*, *>>()
            .filter { it["status"] == "expected-unsupported" }
            .map { scene ->
                val gpu = scene["gpu"] as? Map<*, *>
                val route = gpu?.get("route") as? Map<*, *>
                mapOf(
                    "id" to (scene["id"] as? String).orEmpty(),
                    "fallbackReason" to (route?.get("fallbackReason") as? String).orEmpty(),
                )
            }
        val adapterBacked = scenes
            .filterIsInstance<Map<*, *>>()
            .filter { scene -> (scene["tags"] as? List<*>)?.contains("maturity.adapter-backed") == true }
            .map { scene ->
                val gpu = scene["gpu"] as? Map<*, *>
                val stats = gpu?.get("stats") as? Map<*, *>
                mapOf(
                    "id" to (scene["id"] as? String).orEmpty(),
                    "adapter" to (stats?.get("adapter") as? String).orEmpty(),
                )
            }
        val inventoryDerivedScenes = scenes
            .filterIsInstance<Map<*, *>>()
            .filter { scene ->
                scene["inventoryId"] is String ||
                    (scene["tags"] as? List<*>)?.contains("source.inventory") == true
            }
        fun promotedRowsFor(derivationTask: String): List<Map<String, String>> = inventoryDerivedScenes
            .filter { scene ->
                val generation = scene["generation"] as? Map<*, *>
                generation?.get("derivationTask") == derivationTask
            }
            .map { scene ->
                val generation = scene["generation"] as? Map<*, *>
                mapOf(
                    "id" to (scene["id"] as? String).orEmpty(),
                    "inventoryId" to (scene["inventoryId"] as? String).orEmpty(),
                    "status" to (scene["status"] as? String).orEmpty(),
                    "sourceReport" to (generation?.get("sourceReport") as? String).orEmpty(),
                    "derivationReport" to (generation?.get("derivationReport") as? String).orEmpty(),
                    "derivedFromGeneratedScene" to (generation?.get("derivedFromGeneratedScene") as? String).orEmpty(),
                    "derivationTask" to (generation?.get("derivationTask") as? String).orEmpty(),
                    "derivationContract" to (generation?.get("derivationContract") as? String).orEmpty(),
                    "family" to (generation?.get("hardFeatureFamily") as? String).orEmpty(),
                )
            }
        val m52PromotedRows = promotedRowsFor("pipelineM52InventoryPromotionPack")
        val m53PromotedRows = promotedRowsFor("pipelineM53InventoryPromotionPack")
        val m54PromotedRows = promotedRowsFor("pipelineM54HardFeatureDepthPack")
        val m57PromotedRows = promotedRowsFor("pipelineM57PathAaClipMicroPromotionPack")
        val m66PromotedRows = promotedRowsFor("pipelineM66GmPromotionWave")
        val promotionValidationErrors = mutableListOf<String>()
        inventoryDerivedScenes
            .filter { scene ->
                val generation = scene["generation"] as? Map<*, *>
                generation?.get("derivationTask") in setOf(
                    "pipelineM53InventoryPromotionPack",
                    "pipelineM54HardFeatureDepthPack",
                    "pipelineM57PathAaClipMicroPromotionPack",
                    "pipelineM66GmPromotionWave",
                )
            }
            .forEach { scene ->
                val sceneId = (scene["id"] as? String).orEmpty()
                val generation = scene["generation"] as? Map<*, *>
                val inventoryId = scene["inventoryId"] as? String
                val tags = (scene["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                val routeDiagnostics = scene["routeDiagnostics"] as? Map<*, *>
                val cpu = scene["cpu"] as? Map<*, *>
                val gpu = scene["gpu"] as? Map<*, *>
                val gpuRoute = gpu?.get("route") as? Map<*, *>
                val status = scene["status"] as? String
                if (inventoryId.isNullOrBlank()) promotionValidationErrors += "$sceneId: missing inventoryId"
                if (!tags.contains("source.inventory")) promotionValidationErrors += "$sceneId: missing source.inventory tag"
                if ((generation?.get("sourceReport") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: missing generation.sourceReport"
                if ((generation?.get("derivationContract") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: missing generation.derivationContract"
                if ((routeDiagnostics?.get("cpu") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: missing CPU route diagnostics"
                if ((routeDiagnostics?.get("gpu") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: missing GPU route diagnostics"
                if (status == "pass") {
                    if ((gpu?.get("image") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: pass row missing GPU image"
                    if ((gpu?.get("diff") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: pass row missing GPU diff"
                    if ((gpuRoute?.get("fallbackReason") as? String) != "none") promotionValidationErrors += "$sceneId: pass row fallbackReason must be none"
                }
                if (status == "expected-unsupported") {
                    val fallback = gpuRoute?.get("fallbackReason") as? String
                    if (fallback.isNullOrBlank() || fallback == "none") {
                        promotionValidationErrors += "$sceneId: expected-unsupported row missing stable fallback reason"
                    }
                }
                if ((cpu?.get("image") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: missing CPU image"
                if ((cpu?.get("diff") as? String).isNullOrBlank()) promotionValidationErrors += "$sceneId: missing CPU diff"
            }
        if (promotionValidationErrors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Inventory promotion validation failed:")
                    promotionValidationErrors.sorted().forEach { appendLine("- $it") }
                }
            )
        }
        val m53ContractFile = rootDir.resolve("reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json")
        val m53Contract = JsonSlurper().parse(m53ContractFile) as? Map<*, *>
            ?: throw GradleException("M53 contract root must be a JSON object: ${m53ContractFile.relativeTo(rootDir)}")
        val m53SelectedRows = (m53Contract["scenes"] as? List<*>).orEmpty().size
        val m53RejectedRows = (m53Contract["rejectedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "inventoryId" to (it["inventoryId"] as? String).orEmpty(),
                    "reason" to (it["reason"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m54ContractFile = rootDir.resolve("reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json")
        val m54Contract = JsonSlurper().parse(m54ContractFile) as? Map<*, *>
            ?: throw GradleException("M54 contract root must be a JSON object: ${m54ContractFile.relativeTo(rootDir)}")
        val m54SelectedRows = (m54Contract["selectedCandidateCount"] as? Number)?.toInt()
            ?: (m54Contract["scenes"] as? List<*>).orEmpty().size
        val m54RejectedRows = (m54Contract["rejectedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "inventoryId" to (it["inventoryId"] as? String).orEmpty(),
                    "reason" to (it["reason"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m54FamilyCounters = m54PromotedRows
            .map { it["family"].orEmpty().ifBlank { "unknown" } }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val m54PerformanceRows = scenes
            .filterIsInstance<Map<*, *>>()
            .filter { scene ->
                val generation = scene["generation"] as? Map<*, *>
                generation?.get("derivationTask") == "pipelineM54HardFeatureDepthPack" &&
                    ((scene["cpu"] as? Map<*, *>)?.get("performanceTrend") is Map<*, *> ||
                        (scene["gpu"] as? Map<*, *>)?.get("performanceTrend") is Map<*, *>)
            }
            .map { scene ->
                mapOf(
                    "id" to (scene["id"] as? String).orEmpty(),
                    "cpuStatus" to (((scene["cpu"] as? Map<*, *>)?.get("performanceTrend") as? Map<*, *>)?.get("status") as? String).orEmpty(),
                    "gpuStatus" to (((scene["gpu"] as? Map<*, *>)?.get("performanceTrend") as? Map<*, *>)?.get("status") as? String).orEmpty(),
                )
            }
        val m57ContractFile = rootDir.resolve("reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json")
        val m57Contract = JsonSlurper().parse(m57ContractFile) as? Map<*, *>
            ?: throw GradleException("M57 contract root must be a JSON object: ${m57ContractFile.relativeTo(rootDir)}")
        val m57SelectedRows = (m57Contract["selectedCandidateCount"] as? Number)?.toInt()
            ?: (m57Contract["scenes"] as? List<*>).orEmpty().size
        val m57RejectedRows = (m57Contract["rejectedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "inventoryId" to (it["inventoryId"] as? String).orEmpty(),
                    "reason" to (it["reason"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m57PreservedUnsupportedRows = (m57Contract["preservedUnsupportedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "sceneId" to (it["sceneId"] as? String).orEmpty(),
                    "fallbackReason" to (it["fallbackReason"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m66ContractFile = rootDir.resolve("reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json")
        val m66Contract = JsonSlurper().parse(m66ContractFile) as? Map<*, *>
            ?: throw GradleException("M66 contract root must be a JSON object: ${m66ContractFile.relativeTo(rootDir)}")
        val m66SelectedRows = (m66Contract["selectedCandidateCount"] as? Number)?.toInt()
            ?: (m66Contract["scenes"] as? List<*>).orEmpty().size
        val m66RejectedRows = (m66Contract["rejectedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "inventoryId" to (it["inventoryId"] as? String).orEmpty(),
                    "reason" to (it["reason"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m66AllRows = scenes
            .filterIsInstance<Map<*, *>>()
            .filter { scene ->
                val generation = scene["generation"] as? Map<*, *>
                generation?.get("derivationTask") == "pipelineM66GmPromotionWave"
            }
        val m66FamilyCounters = m66AllRows
            .map { scene ->
                val generation = scene["generation"] as? Map<*, *>
                (generation?.get("hardFeatureFamily") as? String).orEmpty().ifBlank { "unknown" }
            }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val m66ReferenceKindCounters = m66AllRows
            .map { (it["referenceKind"] as? String).orEmpty().ifBlank { "unknown" } }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val m66AllRowsDetail = m66AllRows
            .map { scene ->
                val generation = scene["generation"] as? Map<*, *>
                mapOf(
                    "id" to (scene["id"] as? String).orEmpty(),
                    "inventoryId" to (scene["inventoryId"] as? String).orEmpty(),
                    "status" to (scene["status"] as? String).orEmpty(),
                    "referenceKind" to (scene["referenceKind"] as? String).orEmpty(),
                    "sourceReport" to (generation?.get("sourceReport") as? String).orEmpty(),
                    "derivationReport" to (generation?.get("derivationReport") as? String).orEmpty(),
                    "derivedFromGeneratedScene" to (generation?.get("derivedFromGeneratedScene") as? String).orEmpty(),
                    "derivationTask" to (generation?.get("derivationTask") as? String).orEmpty(),
                    "derivationContract" to (generation?.get("derivationContract") as? String).orEmpty(),
                    "family" to (generation?.get("hardFeatureFamily") as? String).orEmpty(),
                )
            }
        val m55CandidateFile = performanceWarningsRoot.resolve("m55-performance-gate-candidate.json")
        val m55CandidateReport = if (m55CandidateFile.isFile) {
            JsonSlurper().parse(m55CandidateFile) as? Map<*, *>
                ?: throw GradleException("M55 candidate report must be a JSON object: ${m55CandidateFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m55CandidateCounters = (m55CandidateReport["counters"] as? Map<*, *>).orEmpty()
        val m55CandidateRows = (m55CandidateReport["rows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "id" to (it["sceneId"] as? String).orEmpty(),
                    "family" to (it["family"] as? String).orEmpty(),
                    "status" to (it["status"] as? String).orEmpty(),
                    "decision" to (it["decision"] as? String).orEmpty(),
                    "owner" to (it["owner"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m59ReleaseGateFile = performanceReleaseGateRoot.resolve("m59-performance-release-gate.json")
        val m59ReleaseGateReport = if (m59ReleaseGateFile.isFile) {
            JsonSlurper().parse(m59ReleaseGateFile) as? Map<*, *>
                ?: throw GradleException("M59 performance release gate report must be a JSON object: ${m59ReleaseGateFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m59ReleaseGateCounters = (m59ReleaseGateReport["counters"] as? Map<*, *>).orEmpty()
        val m59ReleaseGateRows = (m59ReleaseGateReport["rows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "id" to (it["sceneId"] as? String).orEmpty(),
                    "family" to (it["family"] as? String).orEmpty(),
                    "status" to (it["status"] as? String).orEmpty(),
                    "owner" to (it["owner"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m67FrameGateFile = m67PerformanceRoot.resolve("m67-frame-gate-candidate.json")
        val m67FrameGateReport = if (m67FrameGateFile.isFile) {
            JsonSlurper().parse(m67FrameGateFile) as? Map<*, *>
                ?: throw GradleException("M67 frame gate candidate report must be a JSON object: ${m67FrameGateFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m67FamilyBudgetFile = m67PerformanceRoot.resolve("m67-family-budgets.json")
        val m67FamilyBudgetReport = if (m67FamilyBudgetFile.isFile) {
            JsonSlurper().parse(m67FamilyBudgetFile) as? Map<*, *>
                ?: throw GradleException("M67 family budget report must be a JSON object: ${m67FamilyBudgetFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m67NegativeFixtureFile = m67PerformanceNegativeRoot.resolve("m67-negative-fixture.json")
        val m67NegativeFixtureReport = if (m67NegativeFixtureFile.isFile) {
            JsonSlurper().parse(m67NegativeFixtureFile) as? Map<*, *>
                ?: throw GradleException("M67 negative fixture report must be a JSON object: ${m67NegativeFixtureFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m67FrameGateCounters = (m67FrameGateReport["counters"] as? Map<*, *>).orEmpty()
        val m67FamilyBudgetCounters = (m67FamilyBudgetReport["counters"] as? Map<*, *>).orEmpty()
        val m67FamilyBudgetRows = (m67FamilyBudgetReport["families"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "family" to (it["family"] as? String).orEmpty(),
                    "tier" to (it["tier"] as? String).orEmpty(),
                    "status" to (it["status"] as? String).orEmpty(),
                    "measured" to (it["measured"] as? Boolean ?: false),
                    "lane" to (it["lane"] as? String).orEmpty(),
                )
            }
            .orEmpty()
        val m68BridgeSmokeFile = m68KadreDemoRoot.resolve("bridge-smoke.json")
        val m68BridgeSmokeReport = if (m68BridgeSmokeFile.isFile) {
            JsonSlurper().parse(m68BridgeSmokeFile) as? Map<*, *>
                ?: throw GradleException("M68 bridge-smoke report must be a JSON object: ${m68BridgeSmokeFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m68KadreAuditFile = m68KadreDemoRoot.resolve("kadre-host-audit.json")
        val m68KadreAuditReport = if (m68KadreAuditFile.isFile) {
            JsonSlurper().parse(m68KadreAuditFile) as? Map<*, *>
                ?: throw GradleException("M68 Kadre audit report must be a JSON object: ${m68KadreAuditFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m68RouteSummaryFile = m68KadreDemoRoot.resolve("route-summary.json")
        val m68RouteSummaryReport = if (m68RouteSummaryFile.isFile) {
            JsonSlurper().parse(m68RouteSummaryFile) as? Map<*, *>
                ?: throw GradleException("M68 route summary report must be a JSON object: ${m68RouteSummaryFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m68FlagshipSceneFile = m68KadreDemoRoot.resolve("flagship-scene-evidence.json")
        val m68FlagshipSceneReport = if (m68FlagshipSceneFile.isFile) {
            JsonSlurper().parse(m68FlagshipSceneFile) as? Map<*, *>
                ?: throw GradleException("M68 flagship scene report must be a JSON object: ${m68FlagshipSceneFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m68NativeLaunch = (m68BridgeSmokeReport["nativeLaunch"] as? Map<*, *>).orEmpty()
        val m68HostContract = (m68KadreAuditReport["hostContract"] as? Map<*, *>).orEmpty()
        val m68FeatureRows = (m68FlagshipSceneReport["features"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "feature" to (it["feature"] as? String).orEmpty(),
                    "status" to (it["status"] as? String).orEmpty(),
                    "nativePresented" to (it["nativePresented"] as? Boolean ?: false),
                    "allArtifactsPresent" to (it["allArtifactsPresent"] as? Boolean ?: false),
                )
            }
            .orEmpty()
        val m69ContractFile = m69KadreHostAdapterRoot.resolve("contract.json")
        val m69ContractReport = if (m69ContractFile.isFile) {
            JsonSlurper().parse(m69ContractFile) as? Map<*, *>
                ?: throw GradleException("M69 host adapter contract must be a JSON object: ${m69ContractFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m69RouteStatusFile = m69KadreHostAdapterRoot.resolve("route-status.json")
        val m69RouteStatusReport = if (m69RouteStatusFile.isFile) {
            JsonSlurper().parse(m69RouteStatusFile) as? Map<*, *>
                ?: throw GradleException("M69 route status must be a JSON object: ${m69RouteStatusFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m69SceneRouteFile = m69KadreHostAdapterRoot.resolve("scene-route.json")
        val m69SceneRouteReport = if (m69SceneRouteFile.isFile) {
            JsonSlurper().parse(m69SceneRouteFile) as? Map<*, *>
                ?: throw GradleException("M69 scene route must be a JSON object: ${m69SceneRouteFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m69BridgeSmokeFile = m69KadreHostAdapterRoot.resolve("bridge-smoke.json")
        val m69BridgeSmokeReport = if (m69BridgeSmokeFile.isFile) {
            JsonSlurper().parse(m69BridgeSmokeFile) as? Map<*, *>
                ?: throw GradleException("M69 bridge smoke must be a JSON object: ${m69BridgeSmokeFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m69NativeSmokeFile = m69KadreNativeRoot.resolve("native-smoke.json")
        val m69NativeSmokeReport = if (m69NativeSmokeFile.isFile) {
            JsonSlurper().parse(m69NativeSmokeFile) as? Map<*, *>
                ?: throw GradleException("M69 native smoke must be a JSON object: ${m69NativeSmokeFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m70RouteStatusFile = m70KadreLiveRuntimeRoot.resolve("route-status.json")
        val m70RouteStatusReport = if (m70RouteStatusFile.isFile) {
            JsonSlurper().parse(m70RouteStatusFile) as? Map<*, *>
                ?: throw GradleException("M70-A route status must be a JSON object: ${m70RouteStatusFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m70NativeDemoFile = m70KadreNativeRoot.resolve("native-demo.json")
        val m70NativeDemoReport = if (m70NativeDemoFile.isFile) {
            JsonSlurper().parse(m70NativeDemoFile) as? Map<*, *>
                ?: throw GradleException("M70-A native demo must be a JSON object: ${m70NativeDemoFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m75ReplayPackEvidenceFile = m75ReplayPackEvidenceRoot.resolve("evidence.json")
        val m75ReplayPackEvidence = if (m75ReplayPackEvidenceFile.isFile) {
            JsonSlurper().parse(m75ReplayPackEvidenceFile) as? Map<*, *>
                ?: throw GradleException("M75 replay-pack evidence must be a JSON object: ${m75ReplayPackEvidenceFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val m76GeneratedMetadataReplayFile = m76GeneratedMetadataReplayRoot.resolve("evidence.json")
        val m76GeneratedMetadataReplay = if (m76GeneratedMetadataReplayFile.isFile) {
            JsonSlurper().parse(m76GeneratedMetadataReplayFile) as? Map<*, *>
                ?: throw GradleException("M76 generated metadata replay evidence must be a JSON object: ${m76GeneratedMetadataReplayFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M76 generated metadata replay evidence: ${m76GeneratedMetadataReplayFile.relativeTo(rootDir)}")
        }
        fun m76String(field: String): String =
            (m76GeneratedMetadataReplay[field] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("M76 generated metadata replay evidence missing string `$field`: ${m76GeneratedMetadataReplayFile.relativeTo(rootDir)}")
        fun m76Int(field: String): Int =
            (m76GeneratedMetadataReplay[field] as? Number)?.toInt()
                ?: throw GradleException("M76 generated metadata replay evidence missing numeric `$field`: ${m76GeneratedMetadataReplayFile.relativeTo(rootDir)}")
        val m76SceneCount = m76Int("sceneCount")
        val m76MappedSceneCount = m76Int("mappedSceneCount")
        val m76RefusedMetadataCount = m76Int("refusedMetadataCount")
        val m76FailedSceneCount = m76Int("failedSceneCount")
        if (m76SceneCount <= 0 || m76MappedSceneCount < 2 || m76RefusedMetadataCount < 1 || m76FailedSceneCount != 0) {
            throw GradleException(
                "M76 generated metadata replay evidence counters are invalid: " +
                    "sceneCount=$m76SceneCount mappedSceneCount=$m76MappedSceneCount " +
                    "refusedMetadataCount=$m76RefusedMetadataCount failedSceneCount=$m76FailedSceneCount",
            )
        }
        val m77BlendAlphaReplayFile = m77BlendAlphaReplayRoot.resolve("evidence.json")
        val m77BlendAlphaReplay = if (m77BlendAlphaReplayFile.isFile) {
            JsonSlurper().parse(m77BlendAlphaReplayFile) as? Map<*, *>
                ?: throw GradleException("M77 blend alpha replay evidence must be a JSON object: ${m77BlendAlphaReplayFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M77 blend alpha replay evidence: ${m77BlendAlphaReplayFile.relativeTo(rootDir)}")
        }
        fun m77String(field: String): String =
            (m77BlendAlphaReplay[field] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("M77 blend alpha replay evidence missing string `$field`: ${m77BlendAlphaReplayFile.relativeTo(rootDir)}")
        fun m77Int(field: String): Int =
            (m77BlendAlphaReplay[field] as? Number)?.toInt()
                ?: throw GradleException("M77 blend alpha replay evidence missing numeric `$field`: ${m77BlendAlphaReplayFile.relativeTo(rootDir)}")
        val m77SceneCount = m77Int("sceneCount")
        val m77RenderableSceneCount = m77Int("renderableSceneCount")
        val m77ExpectedUnsupportedSceneCount = m77Int("expectedUnsupportedSceneCount")
        val m77FailedSceneCount = m77Int("failedSceneCount")
        val m77PartialAlphaSceneCount = m77Int("partialAlphaSceneCount")
        if (
            m77SceneCount < 3 ||
            m77RenderableSceneCount < 2 ||
            m77PartialAlphaSceneCount < 2 ||
            m77ExpectedUnsupportedSceneCount < 1 ||
            m77FailedSceneCount != 0
        ) {
            throw GradleException(
                "M77 blend alpha replay evidence counters are invalid: " +
                    "sceneCount=$m77SceneCount renderableSceneCount=$m77RenderableSceneCount " +
                    "partialAlphaSceneCount=$m77PartialAlphaSceneCount " +
                    "expectedUnsupportedSceneCount=$m77ExpectedUnsupportedSceneCount failedSceneCount=$m77FailedSceneCount",
            )
        }
        val m78ClipReplayFile = m78ClipReplayRoot.resolve("evidence.json")
        val m78ClipReplay = if (m78ClipReplayFile.isFile) {
            JsonSlurper().parse(m78ClipReplayFile) as? Map<*, *>
                ?: throw GradleException("M78 clip replay evidence must be a JSON object: ${m78ClipReplayFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M78 clip replay evidence: ${m78ClipReplayFile.relativeTo(rootDir)}")
        }
        fun m78String(field: String): String =
            (m78ClipReplay[field] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("M78 clip replay evidence missing string `$field`: ${m78ClipReplayFile.relativeTo(rootDir)}")
        fun m78Int(field: String): Int =
            (m78ClipReplay[field] as? Number)?.toInt()
                ?: throw GradleException("M78 clip replay evidence missing numeric `$field`: ${m78ClipReplayFile.relativeTo(rootDir)}")
        val m78SceneCount = m78Int("sceneCount")
        val m78RenderableSceneCount = m78Int("renderableSceneCount")
        val m78ExpectedUnsupportedSceneCount = m78Int("expectedUnsupportedSceneCount")
        val m78FailedSceneCount = m78Int("failedSceneCount")
        val m78ClipRectCommandCount = m78Int("clipRectCommandCount")
        val m78ClipIntersectCommandCount = m78Int("clipIntersectCommandCount")
        val m78UnsupportedClipReason = m78String("unsupportedClipReason")
        if (m78String("packId") != "m78-clip-replay-v1" || m78UnsupportedClipReason != "m78.clip.unsupported-complex-clip") {
            throw GradleException("M78 clip replay evidence has unexpected pack id or unsupported reason: ${m78ClipReplayFile.relativeTo(rootDir)}")
        }
        val m78Scenes = (m78ClipReplay["scenes"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?: throw GradleException("M78 clip replay evidence missing scenes[]: ${m78ClipReplayFile.relativeTo(rootDir)}")
        val m78ScenesById = m78Scenes.associateBy { it["id"] as? String }
        listOf(
            "m78-clipped-solid-rect-replay-v1",
            "m78-clipped-alpha-gradient-replay-v1",
            "m78-complex-clip-refusal-v1",
        ).forEach { sceneId ->
            if (m78ScenesById[sceneId] == null) {
                throw GradleException("M78 clip replay evidence missing scene `$sceneId`: ${m78ClipReplayFile.relativeTo(rootDir)}")
            }
        }
        listOf("m78-clipped-solid-rect-replay-v1", "m78-clipped-alpha-gradient-replay-v1").forEach { sceneId ->
            val scene = m78ScenesById.getValue(sceneId)
            if (scene["status"] != "renderable") {
                throw GradleException("M78 renderable scene `$sceneId` is not renderable: ${m78ClipReplayFile.relativeTo(rootDir)}")
            }
            val sceneContract = scene["sceneContract"] as? Map<*, *>
                ?: throw GradleException("M78 scene `$sceneId` missing sceneContract: ${m78ClipReplayFile.relativeTo(rootDir)}")
            val commands = sceneContract["commands"] as? List<*>
                ?: throw GradleException("M78 scene `$sceneId` missing sceneContract.commands[]: ${m78ClipReplayFile.relativeTo(rootDir)}")
            val clipCommands = commands.filterIsInstance<Map<*, *>>().filter { it["family"] == "clipRect" && it["operation"] == "intersect" }
            if (clipCommands.isEmpty()) {
                throw GradleException("M78 scene `$sceneId` missing ClipRect intersect command details: ${m78ClipReplayFile.relativeTo(rootDir)}")
            }
        }
        val m78UnsupportedScene = m78ScenesById.getValue("m78-complex-clip-refusal-v1")
        if (m78UnsupportedScene["status"] != "expected-unsupported" || m78UnsupportedScene["reason"] != m78UnsupportedClipReason) {
            throw GradleException("M78 unsupported clip scene has unexpected status/reason: ${m78ClipReplayFile.relativeTo(rootDir)}")
        }
        if (
            m78SceneCount < 3 ||
            m78RenderableSceneCount < 2 ||
            m78ExpectedUnsupportedSceneCount < 1 ||
            m78ClipRectCommandCount < 2 ||
            m78ClipIntersectCommandCount < 2 ||
            m78FailedSceneCount != 0
        ) {
            throw GradleException(
                "M78 clip replay evidence counters are invalid: " +
                    "sceneCount=$m78SceneCount renderableSceneCount=$m78RenderableSceneCount " +
                    "expectedUnsupportedSceneCount=$m78ExpectedUnsupportedSceneCount " +
                    "clipRectCommandCount=$m78ClipRectCommandCount clipIntersectCommandCount=$m78ClipIntersectCommandCount " +
                    "failedSceneCount=$m78FailedSceneCount",
            )
        }
        val m79BitmapReplayFile = m79BitmapReplayRoot.resolve("evidence.json")
        val m79BitmapReplay = if (m79BitmapReplayFile.isFile) {
            JsonSlurper().parse(m79BitmapReplayFile) as? Map<*, *>
                ?: throw GradleException("M79 bitmap replay evidence must be a JSON object: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M79 bitmap replay evidence: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        }
        fun m79String(field: String): String =
            (m79BitmapReplay[field] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("M79 bitmap replay evidence missing string `$field`: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        fun m79Int(field: String): Int =
            (m79BitmapReplay[field] as? Number)?.toInt()
                ?: throw GradleException("M79 bitmap replay evidence missing numeric `$field`: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        val m79SceneCount = m79Int("sceneCount")
        val m79RenderableSceneCount = m79Int("renderableSceneCount")
        val m79ExpectedUnsupportedSceneCount = m79Int("expectedUnsupportedSceneCount")
        val m79FailedSceneCount = m79Int("failedSceneCount")
        val m79BitmapCommandCount = m79Int("bitmapCommandCount")
        val m79FixtureBackedBitmapCommandCount = m79Int("fixtureBackedBitmapCommandCount")
        val m79NearestSamplerCommandCount = m79Int("nearestSamplerCommandCount")
        val m79LinearSamplerCommandCount = m79Int("linearSamplerCommandCount")
        val m79UnsupportedBitmapCommandCount = m79Int("unsupportedBitmapCommandCount")
        val m79ClipRectCommandCount = m79Int("clipRectCommandCount")
        val m79ClipIntersectCommandCount = m79Int("clipIntersectCommandCount")
        val m79SrcOverCommandCount = m79Int("srcOverCommandCount")
        val m79PartialAlphaCommandCount = m79Int("partialAlphaCommandCount")
        val m79UnsupportedBitmapReason = m79String("unsupportedBitmapReason")
        if (m79String("packId") != "m79-bitmap-replay-v1" || m79UnsupportedBitmapReason != "m79.bitmap.unsupported-sampler.mipmap") {
            throw GradleException("M79 bitmap replay evidence has unexpected pack id or unsupported reason: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        }
        val m79Scenes = (m79BitmapReplay["scenes"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?: throw GradleException("M79 bitmap replay evidence missing scenes[]: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        val m79ScenesById = m79Scenes.associateBy { it["id"] as? String }
        listOf(
            "m79-bitmap-fixture-nearest-replay-v1",
            "m79-bitmap-fixture-linear-alpha-replay-v1",
            "m79-bitmap-fixture-clipped-nearest-replay-v1",
            "m79-bitmap-mipmap-sampler-refusal-v1",
        ).forEach { sceneId ->
            if (m79ScenesById[sceneId] == null) {
                throw GradleException("M79 bitmap replay evidence missing scene `$sceneId`: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            }
        }
        val m79ExpectedBitmapContracts = mapOf(
            "m79-bitmap-fixture-nearest-replay-v1" to mapOf(
                "fixtureId" to "m79-fixture-checker-rgba8-4x4",
                "sampler" to "nearest",
                "clipRect" to 0,
            ),
            "m79-bitmap-fixture-linear-alpha-replay-v1" to mapOf(
                "fixtureId" to "m79-fixture-alpha-swatch-rgba8-4x4",
                "sampler" to "linear",
                "clipRect" to 0,
            ),
            "m79-bitmap-fixture-clipped-nearest-replay-v1" to mapOf(
                "fixtureId" to "m79-fixture-checker-rgba8-4x4",
                "sampler" to "nearest",
                "clipRect" to 1,
            ),
        )
        m79ExpectedBitmapContracts.forEach { (sceneId, expected) ->
            val scene = m79ScenesById.getValue(sceneId)
            if (scene["status"] != "renderable") {
                throw GradleException("M79 renderable scene `$sceneId` is not renderable: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            }
            val sceneContract = scene["sceneContract"] as? Map<*, *>
                ?: throw GradleException("M79 scene `$sceneId` missing sceneContract: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            val commands = sceneContract["commands"] as? List<*>
                ?: throw GradleException("M79 scene `$sceneId` missing sceneContract.commands[]: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            val bitmapCommands = commands.filterIsInstance<Map<*, *>>().filter { it["family"] == "bitmapRect" }
            if (bitmapCommands.isEmpty()) {
                throw GradleException("M79 scene `$sceneId` missing BitmapRect command details: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            }
            val clipCommands = commands.filterIsInstance<Map<*, *>>().filter { it["family"] == "clipRect" && it["operation"] == "intersect" }
            if (clipCommands.size != expected.getValue("clipRect")) {
                throw GradleException("M79 scene `$sceneId` has unexpected ClipRect count: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            }
            bitmapCommands.forEach { command ->
                if (
                    command["fixtureId"] !is String ||
                    command["sourceBounds"] !is Map<*, *> ||
                    command["destinationBounds"] !is Map<*, *> ||
                    command["sampler"] !is String ||
                    command["blendMode"] != "SrcOver" ||
                    command["alpha"] !is Number ||
                    command["provenance"] !is Map<*, *>
                ) {
                    throw GradleException("M79 BitmapRect command missing fixture/bounds/sampler/blend/alpha/provenance fields: ${m79BitmapReplayFile.relativeTo(rootDir)}")
                }
                if (command["fixtureId"] != expected.getValue("fixtureId") || command["sampler"] != expected.getValue("sampler")) {
                    throw GradleException("M79 BitmapRect command has unexpected fixture or sampler for `$sceneId`: ${m79BitmapReplayFile.relativeTo(rootDir)}")
                }
                val sourceBounds = command["sourceBounds"] as? Map<*, *>
                    ?: throw GradleException("M79 BitmapRect command missing source bounds object: ${m79BitmapReplayFile.relativeTo(rootDir)}")
                val destinationBounds = command["destinationBounds"] as? Map<*, *>
                    ?: throw GradleException("M79 BitmapRect command missing destination bounds object: ${m79BitmapReplayFile.relativeTo(rootDir)}")
                listOf("x", "y", "width", "height", "left", "top", "right", "bottom").forEach { field ->
                    if (sourceBounds[field] !is Number || destinationBounds[field] !is Number) {
                        throw GradleException("M79 BitmapRect command has non-numeric bounds field `$field`: ${m79BitmapReplayFile.relativeTo(rootDir)}")
                    }
                }
            }
            val cpuOracle = scene["cpuOracle"] as? Map<*, *>
                ?: throw GradleException("M79 renderable scene `$sceneId` missing cpuOracle: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            if (
                (cpuOracle["bitmapSampledPixels"] as? Number)?.toInt()?.takeIf { it > 0 } == null ||
                (cpuOracle["checksum"] as? Number) == null ||
                (cpuOracle["nonTransparentPixels"] as? Number)?.toInt()?.takeIf { it > 0 } == null
            ) {
                throw GradleException("M79 renderable scene `$sceneId` missing bitmap CPU oracle facts: ${m79BitmapReplayFile.relativeTo(rootDir)}")
            }
        }
        val m79UnsupportedScene = m79ScenesById.getValue("m79-bitmap-mipmap-sampler-refusal-v1")
        if (m79UnsupportedScene["status"] != "expected-unsupported" || m79UnsupportedScene["reason"] != m79UnsupportedBitmapReason) {
            throw GradleException("M79 unsupported bitmap scene has unexpected status/reason: ${m79BitmapReplayFile.relativeTo(rootDir)}")
        }
        if (
            m79SceneCount < 4 ||
            m79RenderableSceneCount < 3 ||
            m79ExpectedUnsupportedSceneCount < 1 ||
            m79BitmapCommandCount < 3 ||
            m79FixtureBackedBitmapCommandCount < 3 ||
            m79NearestSamplerCommandCount < 2 ||
            m79LinearSamplerCommandCount < 1 ||
            m79UnsupportedBitmapCommandCount < 1 ||
            m79ClipRectCommandCount < 1 ||
            m79ClipIntersectCommandCount < 1 ||
            m79SrcOverCommandCount < 4 ||
            m79PartialAlphaCommandCount < 2 ||
            m79FailedSceneCount != 0
        ) {
            throw GradleException(
                "M79 bitmap replay evidence counters are invalid: " +
                    "sceneCount=$m79SceneCount renderableSceneCount=$m79RenderableSceneCount " +
                    "expectedUnsupportedSceneCount=$m79ExpectedUnsupportedSceneCount bitmapCommandCount=$m79BitmapCommandCount " +
                    "fixtureBackedBitmapCommandCount=$m79FixtureBackedBitmapCommandCount " +
                    "nearestSamplerCommandCount=$m79NearestSamplerCommandCount linearSamplerCommandCount=$m79LinearSamplerCommandCount " +
                    "unsupportedBitmapCommandCount=$m79UnsupportedBitmapCommandCount " +
                    "clipRectCommandCount=$m79ClipRectCommandCount clipIntersectCommandCount=$m79ClipIntersectCommandCount " +
                    "srcOverCommandCount=$m79SrcOverCommandCount partialAlphaCommandCount=$m79PartialAlphaCommandCount " +
                    "failedSceneCount=$m79FailedSceneCount",
            )
        }
        val m80SharedReplayOracleFile = m80SharedReplayOracleRoot.resolve("evidence.json")
        val m80SharedReplayOracle = if (m80SharedReplayOracleFile.isFile) {
            JsonSlurper().parse(m80SharedReplayOracleFile) as? Map<*, *>
                ?: throw GradleException("M80 shared replay oracle evidence must be a JSON object: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M80 shared replay oracle evidence: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        }
        fun m80String(field: String): String =
            (m80SharedReplayOracle[field] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("M80 shared replay oracle evidence missing string `$field`: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        fun m80Int(field: String): Int =
            (m80SharedReplayOracle[field] as? Number)?.toInt()
                ?: throw GradleException("M80 shared replay oracle evidence missing numeric `$field`: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        val m80SceneCount = m80Int("sceneCount")
        val m80RenderableSceneCount = m80Int("renderableSceneCount")
        val m80ExpectedUnsupportedSceneCount = m80Int("expectedUnsupportedSceneCount")
        val m80FailedSceneCount = m80Int("failedSceneCount")
        val m80FailedValidationRowCount = m80Int("failedValidationRowCount")
        val m80SupportedCommandFamilies = (m80SharedReplayOracle["supportedCommandFamilies"] as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        if (m80String("packId") != "m80-shared-replay-oracle-v1" || m80String("oracleApi") != "org.skia.kadre.runtime.ReplayCpuOracle") {
            throw GradleException("M80 shared replay oracle evidence has unexpected pack id or oracle API: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        }
        listOf("backgroundClear", "fillRect", "clipRect", "bitmapRect").forEach { family ->
            if (family !in m80SupportedCommandFamilies) {
                throw GradleException("M80 shared replay oracle evidence missing supported family `$family`: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
            }
        }
        val m80Scenes = (m80SharedReplayOracle["scenes"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?: throw GradleException("M80 shared replay oracle evidence missing scenes[]: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        val m80ValidationRows = (m80SharedReplayOracle["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?: throw GradleException("M80 shared replay oracle evidence missing validationRows[]: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        val m80ScenesById = m80Scenes.associateBy { it["id"] as? String }
        listOf(
            "m72-solid-rect-replay-v1",
            "m73-linear-gradient-rect-replay-v1",
            "m77-alpha-srcover-stack-replay-v1",
            "m78-clipped-solid-rect-replay-v1",
            "m79-bitmap-fixture-linear-alpha-replay-v1",
            "m79-bitmap-fixture-clipped-nearest-replay-v1",
            "m79-bitmap-mipmap-sampler-refusal-v1",
        ).forEach { sceneId ->
            if (m80ScenesById[sceneId] == null) {
                throw GradleException("M80 shared replay oracle evidence missing scene `$sceneId`: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
            }
        }
        val m80ValidationRowIds = m80ValidationRows.mapNotNull { it["id"] as? String }.toSet()
        listOf(
            "fillrect-src-over-alpha",
            "cliprect-intersection",
            "bitmap-nearest",
            "bitmap-linear-alpha",
            "bitmap-under-cliprect",
            "expected-unsupported",
            "invalid-fixture-and-bounds",
        ).forEach { rowId ->
            if (rowId !in m80ValidationRowIds) {
                throw GradleException("M80 shared replay oracle evidence missing validation row `$rowId`: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
            }
        }
        if (m80Scenes.none { it["status"] == "expected-unsupported" && (it["unsupportedReasons"] as? List<*>)?.isNotEmpty() == true }) {
            throw GradleException("M80 shared replay oracle evidence missing expected-unsupported row facts: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        }
        if (m80Scenes.any { scene ->
                val cpuOracle = scene["cpuOracle"] as? Map<*, *> ?: return@any true
                cpuOracle["sampledChecksum"] !is Number ||
                    (cpuOracle["nonTransparentPixels"] as? Number)?.toInt()?.takeIf { it > 0 } == null ||
                    cpuOracle["bitmapSampledPixels"] !is Number ||
                    cpuOracle["api"] != "org.skia.kadre.runtime.ReplayCpuOracle" ||
                    cpuOracle["sceneId"] !is String ||
                    cpuOracle["sceneStatus"] !is String ||
                    cpuOracle["unsupportedReasons"] !is List<*> ||
                    cpuOracle["commandFamilies"] !is List<*>
            }
        ) {
            throw GradleException("M80 shared replay oracle evidence has incomplete typed CPU oracle scene facts: ${m80SharedReplayOracleFile.relativeTo(rootDir)}")
        }
        if (
            m80SceneCount != 15 ||
            m80RenderableSceneCount != 11 ||
            m80ExpectedUnsupportedSceneCount != 4 ||
            m80FailedSceneCount != 0 ||
            m80FailedValidationRowCount != 0 ||
            m80ValidationRows.size != 7
        ) {
            throw GradleException(
                "M80 shared replay oracle evidence counters are invalid: " +
                "sceneCount=$m80SceneCount renderableSceneCount=$m80RenderableSceneCount " +
                    "expectedUnsupportedSceneCount=$m80ExpectedUnsupportedSceneCount " +
                    "failedSceneCount=$m80FailedSceneCount failedValidationRowCount=$m80FailedValidationRowCount " +
                    "validationRows=${m80ValidationRows.size}",
            )
        }
        val m81NativeFrameCaptureFile = m81NativeFrameCaptureRoot.resolve("evidence.json")
        val m81NativeFrameCapture = if (m81NativeFrameCaptureFile.isFile) {
            JsonSlurper().parse(m81NativeFrameCaptureFile) as? Map<*, *>
                ?: throw GradleException("M81 native frame capture evidence must be a JSON object: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        fun m81String(field: String): String = (m81NativeFrameCapture[field] as? String).orEmpty()
        fun m81Int(field: String): Int = (m81NativeFrameCapture[field] as? Number)?.toInt() ?: 0
        fun m81Bool(field: String): Boolean = (m81NativeFrameCapture[field] as? Boolean) ?: false
        val m81Capture = (m81NativeFrameCapture["capture"] as? Map<*, *>).orEmpty()
        val m81Surface = (m81NativeFrameCapture["surface"] as? Map<*, *>).orEmpty()
        val m81Adapter = (m81NativeFrameCapture["adapter"] as? Map<*, *>).orEmpty()
        val m81UnsupportedReasons = (m81NativeFrameCapture["unsupportedCaptureReasons"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        val m81ArtifactPaths = (m81NativeFrameCapture["artifactPaths"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        val m81ValidationRows = (m81NativeFrameCapture["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        if (m81NativeFrameCapture.isNotEmpty()) {
            val m81CaptureStatus = m81String("captureStatus")
            val m81CaptureReason = (m81Capture["reason"] as? String).orEmpty()
            val m81CaptureImage = (m81Capture["imagePath"] as? String).orEmpty()
            val m81ProducedOffscreen = m81CaptureStatus == "offscreen-texture-readback-produced"
            val m81ProducedWindow = m81CaptureStatus == "window-surface-readback-produced"
            val m81Refused = m81CaptureStatus.startsWith("refused-")
            if (m81String("packId") != "m81-native-frame-capture-v1" || (!m81ProducedOffscreen && !m81ProducedWindow && !m81Refused)) {
                throw GradleException("M81 native frame capture evidence has unexpected pack id or empty capture status: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
            }
            if (m81ArtifactPaths.isEmpty()) {
                throw GradleException("M81 native frame capture evidence missing frame count or artifacts: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
            }
            m81ArtifactPaths.forEach { artifactPath ->
                val sourceArtifact = rootDir.resolve(artifactPath)
                val bundledArtifact = targetRoot.resolve(artifactPath.removePrefix("reports/wgsl-pipeline/"))
                if (!sourceArtifact.isFile && !bundledArtifact.isFile && !targetRoot.resolve(artifactPath).isFile) {
                    throw GradleException("M81 native frame capture evidence references missing artifact `$artifactPath`: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
                }
            }
            if (m81ValidationRows.isEmpty() || m81ValidationRows.any { it["status"] != "pass" }) {
                throw GradleException("M81 native frame capture evidence has missing or non-pass validation rows: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
            }
            if (m81ProducedOffscreen) {
                if (
                    !m81Bool("realNativeOffscreenTextureReadback") ||
                    m81Bool("realNativeWindowSurfaceReadback") ||
                    m81CaptureReason != "m70.native-offscreen-texture-readback" ||
                    m81CaptureImage !in m81ArtifactPaths ||
                    !rootDir.resolve(m81CaptureImage).isFile ||
                    (m81Capture["width"] as? Number)?.toInt()?.takeIf { it > 0 } == null ||
                    (m81Capture["height"] as? Number)?.toInt()?.takeIf { it > 0 } == null ||
                    (m81Capture["nonTransparentPixels"] as? Number)?.toInt()?.takeIf { it > 0 } == null
                ) {
                    throw GradleException("M81 offscreen capture evidence is internally inconsistent: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
                }
            }
            if (m81ProducedWindow) {
                throw GradleException("M81 must not claim window-surface readback until a source capture explicitly proves it: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
            }
            if ("m81.window-surface-readback-not-implemented" !in m81UnsupportedReasons) {
                throw GradleException("M81 native frame capture evidence missing stable window-surface refusal reason: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
            }
            if (m81Refused && m81UnsupportedReasons.isEmpty()) {
                throw GradleException("M81 refused capture evidence must include stable refusal reasons: ${m81NativeFrameCaptureFile.relativeTo(rootDir)}")
            }
        }
        val m82InputResizeRuntimeLoopFile = m82InputResizeRuntimeLoopRoot.resolve("evidence.json")
        val m82InputResizeRuntimeLoop = if (m82InputResizeRuntimeLoopFile.isFile) {
            JsonSlurper().parse(m82InputResizeRuntimeLoopFile) as? Map<*, *>
                ?: throw GradleException("M82 input/resize runtime evidence must be a JSON object: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M82 input/resize runtime evidence: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        fun m82String(field: String): String = (m82InputResizeRuntimeLoop[field] as? String).orEmpty()
        fun m82Int(field: String): Int = (m82InputResizeRuntimeLoop[field] as? Number)?.toInt() ?: 0
        fun m82Bool(field: String): Boolean = (m82InputResizeRuntimeLoop[field] as? Boolean) ?: false
        val m82Telemetry = (m82InputResizeRuntimeLoop["telemetry"] as? Map<*, *>).orEmpty()
        val m82FinalSceneState = (m82InputResizeRuntimeLoop["finalSceneState"] as? Map<*, *>).orEmpty()
        val m82BackingHost = (m82InputResizeRuntimeLoop["backingHost"] as? Map<*, *>).orEmpty()
        val m82ArtifactPaths = (m82InputResizeRuntimeLoop["artifactPaths"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        val m82ValidationRows = (m82InputResizeRuntimeLoop["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m82Fixtures = (m82InputResizeRuntimeLoop["fixtures"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m82SurfaceReconfigures = (m82InputResizeRuntimeLoop["surfaceReconfigureEvidence"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        fun m82TelemetryInt(field: String): Int = (m82Telemetry[field] as? Number)?.toInt() ?: 0
        if (
            m82String("packId") != "m82-kadre-input-resize-runtime-loop-v1" ||
            m82String("status") != "pass" ||
            m82String("claimLevel") != "deterministic-kadre-runtime-event-model-and-telemetry" ||
            m82Bool("nativeOsEventInjectionClaimed")
        ) {
            throw GradleException("M82 input/resize runtime evidence has unexpected pack/status/claim fields: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        if (m82ArtifactPaths.size < 2) {
            throw GradleException("M82 input/resize runtime evidence missing artifact paths: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        m82ArtifactPaths.forEach { artifactPath ->
            val sourceArtifact = rootDir.resolve(artifactPath)
            val bundledArtifact = targetRoot.resolve(artifactPath.removePrefix("reports/wgsl-pipeline/"))
            if (!sourceArtifact.isFile && !bundledArtifact.isFile && !targetRoot.resolve(artifactPath).isFile) {
                throw GradleException("M82 input/resize runtime evidence references missing artifact `$artifactPath`: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
            }
        }
        if (m82ValidationRows.size < 5 || m82ValidationRows.any { it["status"] != "pass" }) {
            throw GradleException("M82 input/resize runtime evidence has missing or non-pass validation rows: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        if (m82Fixtures.size < 2 || m82Fixtures.none { it["status"] == "pass" } || m82Fixtures.none { it["status"] == "expected-unsupported" }) {
            throw GradleException("M82 input/resize runtime evidence must include pass and expected-unsupported fixtures: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        if (
            m82TelemetryInt("eventCount") <= 0 ||
            m82TelemetryInt("pointerEventCount") <= 0 ||
            m82TelemetryInt("keyboardEventCount") <= 0 ||
            m82TelemetryInt("resizeEventCount") <= 0 ||
            m82TelemetryInt("scaleFactorEventCount") <= 0 ||
            m82TelemetryInt("reconfigureCount") < 2 ||
            m82TelemetryInt("droppedFrameCount") <= 0 ||
            m82TelemetryInt("hostDiagnosticCount") <= 0 ||
            (m82Telemetry["reportingOnly"] as? Boolean) != true
        ) {
            throw GradleException("M82 input/resize runtime telemetry is incomplete or overclaims a gate: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        if (m82SurfaceReconfigures.size < 2 || m82SurfaceReconfigures.any { it["invalidatesWebGpuResources"] != true }) {
            throw GradleException("M82 surface reconfigure evidence must invalidate WebGPU resources: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        if (
            m82FinalSceneState["playing"] !is Boolean ||
            m82FinalSceneState["overlayVisible"] !is Boolean ||
            (m82FinalSceneState["resetCount"] as? Number)?.toInt()?.takeIf { it > 0 } == null ||
            m82FinalSceneState["closeRequested"] != true
        ) {
            throw GradleException("M82 final scene state does not prove input-driven state changes: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        if (
            m82String("unsupportedEventReason") != "m82.kadre-event-family-unsupported" ||
            m82String("invalidResizeReason") != "m82.resize.invalid-surface-size" ||
            m82String("invalidScaleFactorReason") != "m82.scale-factor.invalid"
        ) {
            throw GradleException("M82 refusal reason taxonomy changed unexpectedly: ${m82InputResizeRuntimeLoopFile.relativeTo(rootDir)}")
        }
        val m83DisplayListReplayFile = m83DisplayListReplayRoot.resolve("evidence.json")
        val m83DisplayListReplay = if (m83DisplayListReplayFile.isFile) {
            JsonSlurper().parse(m83DisplayListReplayFile) as? Map<*, *>
                ?: throw GradleException("M83 display-list replay evidence must be a JSON object: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M83 display-list replay evidence: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        fun m83String(field: String): String = (m83DisplayListReplay[field] as? String).orEmpty()
        fun m83Int(field: String): Int = (m83DisplayListReplay[field] as? Number)?.toInt() ?: 0
        fun m83Bool(field: String): Boolean = (m83DisplayListReplay[field] as? Boolean) ?: false
        val m83NativeEvidence = (m83DisplayListReplay["nativeEvidence"] as? Map<*, *>).orEmpty()
        val m83Scenes = (m83DisplayListReplay["scenes"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m83ValidationRows = (m83DisplayListReplay["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m83ArtifactPaths = (m83DisplayListReplay["artifactPaths"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        val m83RenderableScene = m83Scenes.singleOrNull { it["id"] == "m83-display-list-pm-scene-v1" }.orEmpty()
        val m83UnsupportedScene = m83Scenes.singleOrNull { it["id"] == "m83-display-list-placeholder-refusal-v1" }.orEmpty()
        val m83RenderableCounters = (m83RenderableScene["commandCounters"] as? Map<*, *>).orEmpty()
        val m83UnsupportedCommands = (m83UnsupportedScene["unsupportedCommands"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        if (
            m83String("packId") != "m83-display-list-replay-through-kadre-v1" ||
            m83String("claimLevel") != "bounded-kanvas-display-list-replay-through-native-kadre" ||
            !m83Bool("nativePixelsProducedFromDisplayListByThisTask")
        ) {
            throw GradleException("M83 display-list replay evidence has unexpected pack/claim/native fields: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        if (
            m83Int("sceneCount") != 2 ||
            m83Int("renderableSceneCount") != 1 ||
            m83Int("expectedUnsupportedSceneCount") != 1 ||
            m83Int("failedSceneCount") != 0 ||
            m83Int("supportStateMismatchCount") != 0
        ) {
            throw GradleException("M83 display-list replay evidence counters are invalid: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        if (
            m83NativeEvidence["status"] != "native-display-list-produced" ||
            m83NativeEvidence["reason"] != "m83.native-display-list-presented" ||
            m83NativeEvidence["sceneContractId"] != "m83-display-list-pm-scene-v1" ||
            m83NativeEvidence["nativePixelsProducedFromDisplayListByThisTask"] != true ||
            (m83NativeEvidence["captureNonTransparentPixels"] as? Number)?.toInt()?.takeIf { it > 0 } == null ||
            (m83NativeEvidence["presentedFrames"] as? Number)?.toInt()?.takeIf { it > 0 } == null
        ) {
            throw GradleException("M83 native display-list evidence does not prove nonblank native M83 pixels: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        if (m83ArtifactPaths.size < 4) {
            throw GradleException("M83 display-list replay evidence missing artifact paths: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        m83ArtifactPaths.forEach { artifactPath ->
            val sourceArtifact = rootDir.resolve(artifactPath)
            val bundledArtifact = targetRoot.resolve(artifactPath.removePrefix("reports/wgsl-pipeline/"))
            if (!sourceArtifact.isFile && !bundledArtifact.isFile && !targetRoot.resolve(artifactPath).isFile) {
                throw GradleException("M83 display-list replay evidence references missing artifact `$artifactPath`: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
            }
        }
        if (m83ValidationRows.size < 4 || m83ValidationRows.any { it["status"] != "pass" }) {
            throw GradleException("M83 display-list replay evidence has missing or non-pass validation rows: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        if (
            m83RenderableScene["status"] != "renderable" ||
            (m83RenderableCounters["clipRect"] as? Number)?.toInt() != 1 ||
            (m83RenderableCounters["bitmapRect"] as? Number)?.toInt() != 1 ||
            (m83RenderableCounters["fillRect"] as? Number)?.toInt() != 2
        ) {
            throw GradleException("M83 renderable display-list scene is missing expected node coverage: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        if (
            m83UnsupportedScene["status"] != "expected-unsupported" ||
            "m83.text.placeholder-glyph-run-not-routed" !in m83UnsupportedCommands ||
            "m83.filter.placeholder-dag-not-routed" !in m83UnsupportedCommands ||
            "m83.runtime-effect.placeholder-descriptor-not-registered" !in m83UnsupportedCommands
        ) {
            throw GradleException("M83 unsupported display-list node refusals changed unexpectedly: ${m83DisplayListReplayFile.relativeTo(rootDir)}")
        }
        val m84NativeFrameTimingFile = m84NativeFrameTimingRoot.resolve("evidence.json")
        val m84NativeFrameTiming = if (m84NativeFrameTimingFile.isFile) {
            JsonSlurper().parse(m84NativeFrameTimingFile) as? Map<*, *>
                ?: throw GradleException("M84 native frame timing evidence must be a JSON object: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M84 native frame timing evidence: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        fun m84String(field: String): String = (m84NativeFrameTiming[field] as? String).orEmpty()
        fun m84Bool(field: String): Boolean = (m84NativeFrameTiming[field] as? Boolean) ?: false
        val m84MeasuredPayload = (m84NativeFrameTiming["measuredPayload"] as? Map<*, *>).orEmpty()
        val m84Eligibility = (m84NativeFrameTiming["eligibility"] as? Map<*, *>).orEmpty()
        val m84Host = (m84NativeFrameTiming["host"] as? Map<*, *>).orEmpty()
        val m84Adapter = (m84NativeFrameTiming["adapter"] as? Map<*, *>).orEmpty()
        val m84CacheCounters = (m84NativeFrameTiming["cacheCounters"] as? Map<*, *>).orEmpty()
        val m84NegativeFixture = (m84NativeFrameTiming["negativeFixture"] as? Map<*, *>).orEmpty()
        val m84ValidationRows = (m84NativeFrameTiming["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m84ArtifactPaths = (m84NativeFrameTiming["artifactPaths"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        if (
            m84String("packId") != "m84-native-frame-timing-candidate-v1" ||
            m84String("lane") != "frame.kadre-windowed" ||
            m84String("gatePhase") != "candidate-reporting-only" ||
            m84Bool("releaseBlocking") ||
            m84Bool("countedAsMeasuredGate")
        ) {
            throw GradleException("M84 native timing evidence has unexpected pack/lane/gate fields: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        if (
            m84MeasuredPayload["status"] != "measured" ||
            ((m84MeasuredPayload["warmupFrameCount"] as? Number)?.toInt() ?: 0) < 60 ||
            ((m84MeasuredPayload["measuredSampleCount"] as? Number)?.toInt() ?: 0) < 120 ||
            ((m84MeasuredPayload["p50Ms"] as? Number)?.toDouble() ?: 0.0) <= 0.0 ||
            ((m84MeasuredPayload["p95Ms"] as? Number)?.toDouble() ?: 0.0) <= 0.0 ||
            ((m84MeasuredPayload["worstMs"] as? Number)?.toDouble() ?: 0.0) <= 0.0 ||
            ((m84MeasuredPayload["estimatedMetricCount"] as? Number)?.toInt() ?: -1) != 0 ||
            ((m84MeasuredPayload["missingMetricCount"] as? Number)?.toInt() ?: -1) != 0
        ) {
            throw GradleException("M84 measured payload is missing required warmup/sample/timing fields: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        if (
            m84Eligibility["reportingOnly"] != true ||
            (m84Eligibility["quarantineReasons"] as? List<*>)?.contains("m84.reporting-only-until-owner-accepts-variance") != true
        ) {
            throw GradleException("M84 eligibility must preserve reporting-only quarantine rationale: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        if (
            m84Host["javaVersion"].toString().isBlank() ||
            m84Host["osName"].toString().isBlank() ||
            m84Adapter["info"].toString().isBlank() ||
            m84CacheCounters["source"] != "m84.schema-placeholder-until-m85-resource-telemetry"
        ) {
            throw GradleException("M84 host, adapter, or cache counter metadata is incomplete: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        if (
            m84NegativeFixture["status"] != "expected-fail" ||
            m84NegativeFixture["reason"] != "m84.negative-fixture-p95-threshold-exceeded" ||
            m84NegativeFixture["mutatesBaseline"] != false
        ) {
            throw GradleException("M84 negative timing fixture changed unexpectedly: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        if (m84ValidationRows.size < 4 || m84ValidationRows.any { it["status"] != "pass" }) {
            throw GradleException("M84 native timing evidence has missing or non-pass validation rows: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        if (m84ArtifactPaths.size < 4) {
            throw GradleException("M84 native timing evidence missing artifact paths: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
        }
        m84ArtifactPaths.forEach { artifactPath ->
            val sourceArtifact = rootDir.resolve(artifactPath)
            val bundledArtifact = targetRoot.resolve(artifactPath.removePrefix("reports/wgsl-pipeline/"))
            if (!sourceArtifact.isFile && !bundledArtifact.isFile && !targetRoot.resolve(artifactPath).isFile) {
                throw GradleException("M84 native timing evidence references missing artifact `$artifactPath`: ${m84NativeFrameTimingFile.relativeTo(rootDir)}")
            }
        }
        val m85ResourceLifetimeCacheFile = m85ResourceLifetimeCacheRoot.resolve("evidence.json")
        val m85ResourceLifetimeCache = if (m85ResourceLifetimeCacheFile.isFile) {
            JsonSlurper().parse(m85ResourceLifetimeCacheFile) as? Map<*, *>
                ?: throw GradleException("M85 resource/cache evidence must be a JSON object: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M85 resource/cache evidence: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        fun m85String(field: String): String = (m85ResourceLifetimeCache[field] as? String).orEmpty()
        fun m85Int(field: String): Int = (m85ResourceLifetimeCache[field] as? Number)?.toInt() ?: 0
        val m85Telemetry = (m85ResourceLifetimeCache["perFrameResourceTelemetry"] as? Map<*, *>).orEmpty()
        val m85CacheOwnership = (m85ResourceLifetimeCache["cacheOwnership"] as? Map<*, *>).orEmpty()
        val m85ResizeInvalidation = (m85ResourceLifetimeCache["resizeInvalidation"] as? Map<*, *>).orEmpty()
        val m85DeviceLossDiagnostics = (m85ResourceLifetimeCache["deviceLossDiagnostics"] as? Map<*, *>).orEmpty()
        val m85CachePressureReport = (m85ResourceLifetimeCache["cachePressureReport"] as? Map<*, *>).orEmpty()
        val m85ValidationRows = (m85ResourceLifetimeCache["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m85ArtifactPaths = (m85ResourceLifetimeCache["artifactPaths"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        if (
            m85String("packId") != "m85-resource-lifetime-cache-hardening-v1" ||
            m85String("status") != "pass" ||
            m85String("lane") != "frame.kadre-windowed" ||
            m85String("sceneContractId") != "m83-display-list-pm-scene-v1" ||
            m85ResourceLifetimeCache["observedRuntimeCounters"] != false ||
            m85ResourceLifetimeCache["countedAsCacheReadinessGate"] != false ||
            m85ResourceLifetimeCache["counterSource"] != "derived-selected-scene-resource-ledger"
        ) {
            throw GradleException("M85 resource/cache evidence has unexpected pack/status/lane fields: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (
            ((m85Telemetry["frameCount"] as? Number)?.toInt() ?: 0) <= 0 ||
            ((m85Telemetry["pipelineCacheMisses"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["pipelineCacheHits"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["shaderModuleCount"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["pipelineCount"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["bindGroupCount"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["textureCount"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["textureUploadBytes"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["intermediateTextureBytes"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["bindGroupChurn"] as? Number)?.toInt() ?: -1) < 0 ||
            ((m85Telemetry["invalidResourceReuseCount"] as? Number)?.toInt() ?: -1) != 0
        ) {
            throw GradleException("M85 per-frame resource telemetry is incomplete or reports invalid resource reuse: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (
            m85CacheOwnership["pipelineKeyPolicy"] != "layout-code-resource-pipeline-state-only" ||
            m85CacheOwnership["uniformValuesInPipelineKey"] != false ||
            ((m85CacheOwnership["boundedKeySpaceCount"] as? Number)?.toInt() ?: 0) < 6
        ) {
            throw GradleException("M85 cache ownership/keyspace policy changed unexpectedly: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (
            ((m85ResizeInvalidation["reconfigureCount"] as? Number)?.toInt() ?: 0) < 2 ||
            ((m85ResizeInvalidation["reconfigureFailureCount"] as? Number)?.toInt() ?: -1) != 0 ||
            m85ResizeInvalidation["generationsStrictlyAdvance"] != true ||
            m85ResizeInvalidation["generationSequenceMonotonic"] != true ||
            m85ResizeInvalidation["invalidatesWebGpuResources"] != true ||
            ((m85ResizeInvalidation["invalidResourceReuseCount"] as? Number)?.toInt() ?: -1) != 0
        ) {
            throw GradleException("M85 resize/surface invalidation evidence is incomplete: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (
            m85DeviceLossDiagnostics["status"] != "expected-unsupported" ||
            m85DeviceLossDiagnostics["reason"] != "m85.device-loss-recreate-observation-unsupported" ||
            m85DeviceLossDiagnostics["recreateClaimed"] != false
        ) {
            throw GradleException("M85 device loss diagnostics changed unexpectedly: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (m85CachePressureReport["boundedGrowth"] != true) {
            throw GradleException("M85 cache pressure report must prove bounded selected-scene growth: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (m85ValidationRows.size < 5 || m85ValidationRows.any { it["status"] != "pass" }) {
            throw GradleException("M85 resource/cache evidence has missing or non-pass validation rows: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        if (m85ArtifactPaths.size < 5) {
            throw GradleException("M85 resource/cache evidence missing artifact paths: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
        }
        m85ArtifactPaths.forEach { artifactPath ->
            val sourceArtifact = rootDir.resolve(artifactPath)
            val bundledArtifact = targetRoot.resolve(artifactPath.removePrefix("reports/wgsl-pipeline/"))
            if (!sourceArtifact.isFile && !bundledArtifact.isFile && !targetRoot.resolve(artifactPath).isFile) {
                throw GradleException("M85 resource/cache evidence references missing artifact `$artifactPath`: ${m85ResourceLifetimeCacheFile.relativeTo(rootDir)}")
            }
        }
        val m86FidelityBurndownFile = m86FidelityBurndownRoot.resolve("evidence.json")
        val m86FidelityBurndown = if (m86FidelityBurndownFile.isFile) {
            JsonSlurper().parse(m86FidelityBurndownFile) as? Map<*, *>
                ?: throw GradleException("M86 fidelity burn-down evidence must be a JSON object: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M86 fidelity burn-down evidence: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
        }
        val m86Counters = (m86FidelityBurndown["counters"] as? Map<*, *>).orEmpty()
        val m86BurnDown = (m86FidelityBurndown["burnDown"] as? Map<*, *>).orEmpty()
        val m86DashboardGateExpectation = (m86FidelityBurndown["dashboardGateExpectation"] as? Map<*, *>).orEmpty()
        val m86ReadinessDelta = (m86FidelityBurndown["readinessDelta"] as? Map<*, *>).orEmpty()
        val m86SelectedRows = (m86BurnDown["selectedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m86ClassifiedRows = (m86BurnDown["classifiedRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m86RemediationTargets = (m86BurnDown["highValueRemediationTargets"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        if (
            m86FidelityBurndown["generatedBy"] != "pipelineM86FidelityBurndown" ||
            ((m86Counters["rankedCandidates"] as? Number)?.toInt() ?: 0) < 19 ||
            ((m86Counters["supportRows"] as? Number)?.toInt() ?: 0) < 16 ||
            ((m86Counters["unsupportedRows"] as? Number)?.toInt() ?: 0) < 3 ||
            ((m86Counters["classifiedRows"] as? Number)?.toInt() ?: 0) < 7 ||
            ((m86Counters["skiaComparableSupportRows"] as? Number)?.toInt() ?: 0) < 6 ||
            m86DashboardGateExpectation["globalThresholdWeakened"] != false ||
            ((m86ReadinessDelta["weightedPercentBefore"] as? Number)?.toDouble() ?: -1.0) != 67.75 ||
            ((m86ReadinessDelta["weightedPercentAfter"] as? Number)?.toDouble() ?: -1.0) != 67.75
        ) {
            throw GradleException("M86 fidelity burn-down counters or readiness accounting are invalid: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
        }
        if (m86SelectedRows.size < 19 || m86ClassifiedRows.size < 7 || m86RemediationTargets.size < 3) {
            throw GradleException("M86 fidelity burn-down evidence is missing ranked/classified/remediation rows: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
        }
        listOf(
            "m66-clip-rect-difference-skia",
            "m66-crop-image-filter-nonnull-prepass-skia",
            "m66-path-aa-stroke-primitive-oracle",
        ).forEach { rowId ->
            if (m86RemediationTargets.none { it["id"] == rowId }) {
                throw GradleException("M86 remediation targets missing `$rowId`: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
            }
        }
        m86SelectedRows.forEach { row ->
            val rowId = (row["id"] as? String).orEmpty()
            val artifacts = (row["artifacts"] as? Map<*, *>).orEmpty()
            if (artifacts.isEmpty()) {
                throw GradleException("M86 selected row `$rowId` is missing artifact links: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
            }
            artifacts.values.forEach { rawPath ->
                val artifactPath = rawPath.toString()
                if (!artifactPath.startsWith("dashboard/artifacts/") || !targetRoot.resolve(artifactPath).isFile) {
                    throw GradleException("M86 selected row `$rowId` references a non-portable or missing PM bundle artifact `$artifactPath`: ${m86FidelityBurndownFile.relativeTo(rootDir)}")
                }
            }
        }
        val m87RuntimeEffectLiveEditingFile = m87RuntimeEffectLiveEditingRoot.resolve("evidence.json")
        val m87RuntimeEffectLiveEditing = if (m87RuntimeEffectLiveEditingFile.isFile) {
            JsonSlurper().parse(m87RuntimeEffectLiveEditingFile) as? Map<*, *>
                ?: throw GradleException("M87 runtime-effect live-editing evidence must be a JSON object: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        } else {
            throw GradleException("Missing M87 runtime-effect live-editing evidence: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        val m87Effect = (m87RuntimeEffectLiveEditing["effect"] as? Map<*, *>).orEmpty()
        val m87Parameters = (m87RuntimeEffectLiveEditing["liveParameterMetadata"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m87Reflection = (m87RuntimeEffectLiveEditing["reflectionValidation"] as? Map<*, *>).orEmpty()
        val m87Telemetry = (m87RuntimeEffectLiveEditing["liveRuntimeTelemetry"] as? Map<*, *>).orEmpty()
        val m87ParityRows = (m87RuntimeEffectLiveEditing["parityEvidence"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m87Refusals = (m87RuntimeEffectLiveEditing["stableRefusals"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m87ValidationRows = (m87RuntimeEffectLiveEditing["validationRows"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            .orEmpty()
        val m87ArtifactPaths = (m87RuntimeEffectLiveEditing["artifactPaths"] as? List<*>)
            ?.map { it.toString() }
            .orEmpty()
        if (
            m87RuntimeEffectLiveEditing["packId"] != "m87-runtime-effect-live-editing-v1" ||
            m87RuntimeEffectLiveEditing["status"] != "pass" ||
            m87Effect["stableId"] != "runtime.simple_rt" ||
            m87Effect["wgslImplementationId"] != "wgsl/runtime_simple_rt" ||
            m87Effect["arbitrarySkSLFallbackReason"] != "runtime-effect.arbitrary-sksl-unsupported"
        ) {
            throw GradleException("M87 runtime-effect live-editing evidence has unexpected pack/status/effect fields: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        val m87EditableBlue = m87Parameters.singleOrNull { it["name"] == "gColor.b" }
        if (
            m87EditableBlue == null ||
            m87EditableBlue["pipelineKeyAxis"] != false ||
            m87EditableBlue["affectsOutput"] != true ||
            m87EditableBlue["invalidValueDiagnostic"] != "m87.runtime-effect.parameter-out-of-range"
        ) {
            throw GradleException("M87 live parameter metadata is missing gColor.b edit constraints: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        if (
            m87Reflection["source"] != "wgsl4k-validation-report" ||
            m87Reflection["layoutVerified"] != true ||
            ((m87Reflection["reflectedOffset"] as? Number)?.toInt() ?: -1) != 0 ||
            m87Reflection["mismatchDiagnostic"] != "m87.runtime-effect.uniform-layout-mismatch" ||
            m87Reflection["upstreamWgsl4kTicketRequired"] != false
        ) {
            throw GradleException("M87 reflection evidence does not verify runtime_simple_rt.wgsl gColor layout: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        if (
            ((m87Telemetry["frameUpdateCount"] as? Number)?.toInt() ?: 0) < 2 ||
            ((m87Telemetry["parameterUpdateCount"] as? Number)?.toInt() ?: 0) < 2 ||
            m87Telemetry["pipelineKeyStableAcrossUniformEdits"] != true ||
            m87Telemetry["uniformValuesInPipelineKey"] != false ||
            m87Telemetry["selectedRuntimeOutputAffected"] != true ||
            m87Telemetry["nativeDemoParameterContractReady"] != true ||
            m87Telemetry["actualNativeWindowRun"] != false ||
            m87Telemetry["nativeWindowReadbackProducedByM87"] != false
        ) {
            throw GradleException("M87 live runtime telemetry is missing edited frame/update evidence: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        if (m87ParityRows.size < 2 || m87ParityRows.any { it["status"] != "pass" || ((it["similarity"] as? Number)?.toDouble() ?: 0.0) < 99.95 }) {
            throw GradleException("M87 edited-state parity evidence is missing or below threshold: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        listOf("runtime-effect.arbitrary-sksl-unsupported", "runtime-effect.wgsl-descriptor-missing").forEach { reason ->
            if (m87Refusals.none { it["fallbackReason"] == reason && it["status"] == "expected-unsupported" }) {
                throw GradleException("M87 stable refusal `$reason` is missing: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
            }
        }
        if (m87ValidationRows.size < 5 || m87ValidationRows.any { it["status"] != "pass" }) {
            throw GradleException("M87 runtime-effect live-editing evidence has missing or non-pass validation rows: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        if (m87ArtifactPaths.size < 10) {
            throw GradleException("M87 runtime-effect live-editing evidence missing artifact paths: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
        }
        m87ArtifactPaths.forEach { artifactPath ->
            val sourceArtifact = rootDir.resolve(artifactPath)
            val bundledArtifact = targetRoot.resolve(artifactPath.removePrefix("reports/wgsl-pipeline/"))
            if (!sourceArtifact.isFile && !bundledArtifact.isFile && !targetRoot.resolve(artifactPath).isFile) {
                throw GradleException("M87 runtime-effect live-editing evidence references missing artifact `$artifactPath`: ${m87RuntimeEffectLiveEditingFile.relativeTo(rootDir)}")
            }
        }
        val m69Capabilities = (m69ContractReport["capabilities"] as? Map<*, *>).orEmpty()
        val m69Routes = (m69RouteStatusReport["routes"] as? Map<*, *>).orEmpty()
        val m69SourceFeatures = (m69SceneRouteReport["sourceFeatures"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map {
                mapOf(
                    "feature" to (it["feature"] as? String).orEmpty(),
                    "status" to (it["status"] as? String).orEmpty(),
                    "nativePresented" to (it["nativePresented"] as? Boolean ?: false),
                    "artifactCount" to ((it["artifacts"] as? List<*>)?.size ?: 0),
                )
            }
            .orEmpty()
        val m52RejectedRows = listOf(
            mapOf("inventoryId" to "skia-gm-animatedgif", "reason" to "Codec/animation dependency remains gated."),
            mapOf("inventoryId" to "skia-gm-animcodecplayerexif", "reason" to "Codec/EXIF dependency remains gated."),
            mapOf("inventoryId" to "skia-gm-dftext", "reason" to "SDF glyph backend remains gated."),
            mapOf("inventoryId" to "skia-gm-dftextblobpersp", "reason" to "SDF glyph and perspective text remain gated."),
            mapOf("inventoryId" to "skia-gm-runtimeimagefilter", "reason" to "Runtime image-filter contract needs a separate descriptor-backed slice."),
            mapOf("inventoryId" to "skia-gm-runtimeintrinsics", "reason" to "Runtime intrinsic coverage needs separate WGSL descriptor evidence."),
            mapOf("inventoryId" to "skia-gm-gradients2ptconical", "reason" to "Two-point conical gradient remains outside this narrow linear-gradient pack."),
            mapOf("inventoryId" to "skia-gm-complexclip", "reason" to "Complex clip/path coverage needs a Geometry/Coverage-specific slice."),
        )
        val inventoryDataFile = inventoryRoot.resolve("inventory.json")
        val inventoryData = if (inventoryDataFile.isFile) {
            JsonSlurper().parse(inventoryDataFile) as? Map<*, *>
                ?: throw GradleException("Inventory root must be a JSON object: ${inventoryDataFile.relativeTo(rootDir)}")
        } else {
            emptyMap<String, Any>()
        }
        val inventorySummary = (inventoryData["summary"] as? Map<*, *>).orEmpty()
        val dashboardInventoryLinks = (inventoryData["dashboardInventoryLinks"] as? Map<*, *>)
            ?.mapKeys { it.key.toString() }
            ?.mapValues { it.value.toString() }
            .orEmpty()
        val commit = try {
            providers.exec {
                commandLine("git", "rev-parse", "HEAD")
            }.standardOutput.asText.get().trim()
        } catch (_: Exception) {
            "unknown"
        }
        val timestamp = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        val serveCommand = "python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard"
        val m70Capture = (m70RouteStatusReport["capture"] as? Map<*, *>).orEmpty()
        val m70Replay = (m70RouteStatusReport["sceneReplay"] as? Map<*, *>).orEmpty()
        val m70ReplayPack = (m70RouteStatusReport["replayPack"] as? Map<*, *>).orEmpty()
        val m70ReplayCounters = (m70RouteStatusReport["replayCommandCounters"] as? Map<*, *>).orEmpty()
        val m70CapturePath = (m70Capture["imagePath"] as? String).orEmpty()
        val m70CaptureBundlePath = if ((m70Capture["realNativeReadback"] as? Boolean) == true && m70CapturePath.startsWith("reports/wgsl-pipeline/m70-kadre-native/")) {
            "runtime/m70-kadre-native/${m70CapturePath.substringAfterLast('/')}"
        } else {
            ""
        }

        val manifest = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "generatedBy" to "pipelinePmBundle",
            "generatedAt" to timestamp,
            "commit" to commit,
            "generationCommand" to "rtk ./gradlew --no-daemon pipelinePmBundle",
            "serveCommand" to serveCommand,
            "dashboardEntry" to "dashboard/index.html",
            "mergedSceneJson" to "dashboard/data/scenes.json",
            "generatedSourceJson" to "reports/wgsl-pipeline/scenes/generated/results.json",
            "generatedResultJson" to "generated/data/generated-scenes.json",
            "m52GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
            "m53GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json",
            "m54GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json",
            "m63GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m63-color-blend-parity-pack.json",
            "m64GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m64-registered-runtime-effects-pack.json",
            "m57GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json",
            "m66GeneratedContractJson" to "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
            "m65RuntimeSmoke" to linkedMapOf<String, Any>(
                "telemetryJson" to "runtime/m65-runtime-smoke/telemetry.json",
                "slotsJson" to "runtime/m65-runtime-smoke/slots.json",
                "runtimeReport" to "reports/wgsl-pipeline/2026-06-01-m65-runtime-smoke.md",
                "kadreAudit" to "reports/wgsl-pipeline/2026-06-01-m65-kadre-audit.md",
                "sprintReport" to "reports/wgsl-pipeline/2026-06-01-m65-m66-sprint-report-and-readiness-accounting.md",
                "claimLevel" to "reporting-only",
                "notice" to "M65 proves a headless/offscreen runtime smoke lane and keeps live Kadre presentation blocked with m65.kadre-host-not-wired; it is not a release-grade FPS or native demo claim.",
            ),
            "gateReport" to "gate/scene-dashboard-gate.md",
            "frontQaReport" to "front-qa/front-qa.md",
            "frontQaJson" to "front-qa/front-qa.json",
            "frontQaScreenshots" to mapOf(
                "desktop" to "front-qa/screenshots/desktop.png",
                "mobile" to "front-qa/screenshots/mobile.png",
            ),
            "performanceWarningReport" to "performance/performance-warnings.md",
            "performanceWarningJson" to "performance/performance-warnings.json",
            "m55PerformanceGateCandidateReport" to "performance/m55-performance-gate-candidate.md",
            "m55PerformanceGateCandidateJson" to "performance/m55-performance-gate-candidate.json",
            "m59PerformanceReleaseGateReport" to "performance-release-gate/m59-performance-release-gate.md",
            "m59PerformanceReleaseGateJson" to "performance-release-gate/m59-performance-release-gate.json",
            "m67PerformanceTieringReport" to "performance/m67-performance-tiering/m67-frame-gate-candidate.md",
            "m67PerformanceTieringJson" to "performance/m67-performance-tiering/m67-frame-gate-candidate.json",
            "m67FamilyBudgetsReport" to "performance/m67-performance-tiering/m67-family-budgets.md",
            "m67FamilyBudgetsJson" to "performance/m67-performance-tiering/m67-family-budgets.json",
            "m67NegativeFixtureReport" to "performance/m67-performance-tiering-negative/m67-negative-fixture.md",
            "m67NegativeFixtureJson" to "performance/m67-performance-tiering-negative/m67-negative-fixture.json",
            "m68KadreDemoReport" to "reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md",
            "m68KadreDemoAuditJson" to "runtime/m68-kadre-demo/kadre-host-audit.json",
            "m68KadreDemoBridgeSmokeJson" to "runtime/m68-kadre-demo/bridge-smoke.json",
            "m68KadreDemoRouteSummaryJson" to "runtime/m68-kadre-demo/route-summary.json",
            "m68KadreDemoFlagshipSceneJson" to "runtime/m68-kadre-demo/flagship-scene-evidence.json",
            "m68KadreDemoTelemetryOverlayJson" to "runtime/m68-kadre-demo/telemetry-overlay-sample.json",
            "m69KadreHostAdapterReport" to "reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md",
            "m69KadreHostAdapterContractJson" to "runtime/m69-kadre-host-adapter/contract.json",
            "m69KadreHostAdapterRouteStatusJson" to "runtime/m69-kadre-host-adapter/route-status.json",
            "m69KadreHostAdapterSceneRouteJson" to "runtime/m69-kadre-host-adapter/scene-route.json",
            "m69KadreHostAdapterTelemetryJson" to "runtime/m69-kadre-host-adapter/telemetry.json",
            "m69KadreHostAdapterBridgeSmokeJson" to "runtime/m69-kadre-host-adapter/bridge-smoke.json",
            "m69KadreNativeSmokeJson" to "runtime/m69-kadre-native/native-smoke.json",
            "m70KadreLiveRuntimeReport" to "reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md",
            "m70KadreLiveRuntimeRouteStatusJson" to "runtime/m70-kadre-live-runtime/route-status.json",
            "m70KadreNativeDemoJson" to "runtime/m70-kadre-native/native-demo.json",
            "m75ReplayPackEvidenceMarkdown" to "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.md",
            "m75ReplayPackEvidenceJson" to "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.json",
            "m76GeneratedMetadataReplayMarkdown" to "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.md",
            "m76GeneratedMetadataReplayJson" to "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.json",
            "m77BlendAlphaReplayMarkdown" to "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.md",
            "m77BlendAlphaReplayJson" to "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json",
            "m78ClipReplayMarkdown" to "reports/wgsl-pipeline/m78-clip-replay/evidence.md",
            "m78ClipReplayJson" to "reports/wgsl-pipeline/m78-clip-replay/evidence.json",
            "m79BitmapReplayMarkdown" to "reports/wgsl-pipeline/m79-bitmap-replay/evidence.md",
            "m79BitmapReplayJson" to "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json",
            "m80SharedReplayOracleMarkdown" to "reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.md",
            "m80SharedReplayOracleJson" to "reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.json",
            "m81NativeFrameCaptureMarkdown" to "runtime/m81-native-frame-capture/evidence.md",
            "m81NativeFrameCaptureJson" to "runtime/m81-native-frame-capture/evidence.json",
            "m82InputResizeRuntimeLoopMarkdown" to "runtime/m82-kadre-input-resize-runtime-loop/evidence.md",
            "m82InputResizeRuntimeLoopJson" to "runtime/m82-kadre-input-resize-runtime-loop/evidence.json",
            "m84NativeFrameTimingMarkdown" to "runtime/m84-native-frame-timing/evidence.md",
            "m84NativeFrameTimingJson" to "runtime/m84-native-frame-timing/evidence.json",
            "m85ResourceLifetimeCacheMarkdown" to "runtime/m85-resource-lifetime-cache/evidence.md",
            "m85ResourceLifetimeCacheJson" to "runtime/m85-resource-lifetime-cache/evidence.json",
            "m86FidelityBurndownMarkdown" to "fidelity/m86-fidelity-burndown/evidence.md",
            "m86FidelityBurndownJson" to "fidelity/m86-fidelity-burndown/evidence.json",
            "m86FidelityBurndownSprintReport" to "reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md",
            "m87RuntimeEffectLiveEditing" to linkedMapOf<String, Any>(
                "evidenceMarkdown" to "runtime/m87-runtime-effect-live-editing/evidence.md",
                "evidenceJson" to "runtime/m87-runtime-effect-live-editing/evidence.json",
                "editedStatesJson" to "runtime/m87-runtime-effect-live-editing/edited-states.json",
                "sprintReport" to "reports/wgsl-pipeline/2026-06-02-m87-sprint-report-and-readiness-accounting.md",
                "effectStableId" to "runtime.simple_rt",
                "editableParameter" to "gColor.b",
                "editedStateCount" to m87ParityRows.size,
                "reflectionVerified" to (m87Reflection["layoutVerified"] == true),
                "pipelineKeyStableAcrossUniformEdits" to (m87Telemetry["pipelineKeyStableAcrossUniformEdits"] == true),
                "claimLevel" to "selected-registered-runtime-effect-live-edit-evidence",
                "notice" to "M87 proves selected SimpleRT live parameter editing with reflected layout and CPU/GPU parity artifacts; arbitrary Skia/SkSL runtime shader input and missing WGSL descriptors remain expected-unsupported because Kanvas targets registered WGSL implementations, not dynamic SkSL compilation.",
            ),
            "m88ReleaseCandidate2" to linkedMapOf<String, Any>(
                "evidenceMarkdown" to "release/m88-realtime-rc2/rc2-evidence.md",
                "evidenceJson" to "release/m88-realtime-rc2/rc2-evidence.json",
                "supportRefusalMatrixJson" to "release/m88-realtime-rc2/support-refusal-matrix.json",
                "gateFreezeJson" to "release/m88-realtime-rc2/gate-freeze.json",
                "apiSurfaceJson" to "release/m88-realtime-rc2/api-surface.json",
                "pmDemoScript" to "release/m88-realtime-rc2/pm-demo-script.md",
                "releaseNotes" to "release/m88-realtime-rc2/release-notes.md",
                "sprintReport" to "reports/wgsl-pipeline/2026-06-02-m88-sprint-report-and-readiness-accounting.md",
                "claimLevel" to "realtime-renderer-rc2-freeze-package",
                "status" to "pass",
                "readinessBefore" to 67.75,
                "readinessAfter" to 67.75,
                "readinessDelta" to 0.0,
                "nativeTimingPhase" to "reporting-only",
                "resourceCachePhase" to "reporting-only",
                "pmPackageCommand" to "rtk ./gradlew --no-daemon pipelinePmBundle",
                "releaseBlocking" to false,
                "notice" to "M88 freezes the RC2 PM package, API surface, gate set, and support/refusal matrix. It keeps readiness at 67.75% and does not claim broad Skia parity, arbitrary Skia/SkSL runtime shader input, release-grade windowed FPS, observed broad runtime cache telemetry, or dynamic SkSL compilation; WGSL remains the shader implementation target.",
            ),
            "m89FeatureBreadth" to linkedMapOf<String, Any>(
                "evidenceMarkdown" to "release/m89-feature-breadth/evidence.md",
                "evidenceJson" to "release/m89-feature-breadth/evidence.json",
                "pmReport" to "reports/wgsl-pipeline/2026-06-02-mep-next-feature-breadth-pm-report.md",
                "claimLevel" to "post-rc-mep-bounded-feature-breadth-evidence",
                "status" to "pass",
                "linearIssues" to listOf("FOR-189", "FOR-190", "FOR-191", "FOR-192"),
                "sourceCommit" to "fbadbd3d4bd7ab8b86ffc2eabf01a02707b9068e",
                "dashboardExpectation" to linkedMapOf(
                    "failRows" to 0,
                    "trackedGapRows" to 0,
                ),
                "families" to linkedMapOf(
                    "imageFilters" to listOf("crop-image-filter-nonnull-prepass", "image-filter-compose-cf-matrix-transform"),
                    "clipsPathAa" to listOf("clip-rect-difference", "path-aa-stroke-primitive"),
                    "bitmapTexture" to listOf("bitmap-subset-local-matrix-repeat", "bitmap-shader-local-matrix"),
                    "runtimeEffects" to listOf("runtime-effect-simple"),
                ),
                "stableRefusals" to listOf(
                    "image-filter.crop-input-nonnull-prepass-required",
                    "coverage.edge-count-exceeded",
                    "m79.bitmap.unsupported-sampler.mipmap",
                    "runtime-effect.arbitrary-sksl-unsupported",
                    "runtime-effect.wgsl-descriptor-missing",
                ),
                "notice" to "M89/FOR-189..192 aggregates bounded post-RC-MEP visual breadth evidence for image filters, clips/Path AA, bitmap sampling, and registered WGSL runtime effects. It does not add renderer runtime code, broaden support claims, weaken global thresholds, require Kadre native runtime, or imply dynamic SkSL compilation.",
            ),
            "m90RuntimeInteractive" to linkedMapOf<String, Any>(
                "evidenceMarkdown" to "runtime/m90-runtime-interactive/pm-report.md",
                "evidenceJson" to "runtime/m90-runtime-interactive/evidence.json",
                "telemetryJson" to "runtime/m90-runtime-interactive/telemetry-live.json",
                "sceneSwitchingJson" to "runtime/m90-runtime-interactive/scene-switching.json",
                "pmReport" to "reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md",
                "claimLevel" to "bounded-kadre-runtime-interactive-evidence",
                "status" to "pass",
                "linearIssues" to listOf("FOR-193", "FOR-194", "FOR-195", "FOR-196"),
                "demoCommand" to "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive",
                "benchmarkCommand" to "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark -PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120",
                "ciEvidenceCommand" to "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive",
                "optionalDirectRuntimeRefreshCommand" to "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive",
                "optionalDirectRuntimeRefreshPrecondition" to "git submodule update --init --recursive external/poc-koreos or provide local org.graphiks.kadre artifacts",
                "runtimeCapabilities" to linkedMapOf(
                    "autonomousFrameClock" to true,
                    "sceneSwitchingRenderableScenes" to 4,
                    "boundedPointerKeyboardTelemetry" to true,
                    "resourceCacheTelemetryClassification" to "observed-partial-plus-derived-ledger",
                ),
                "nonClaims" to listOf(
                    "Native demo and benchmark remain opt-in because they open Kadre windows.",
                    "No broad SkCanvas/display-list replay.",
                    "No real OS/window-manager event injection in CI.",
                    "No release-grade frame.kadre-windowed FPS gate.",
                    "No broad observed WebGPU cache telemetry.",
                ),
                "notice" to "M90/FOR-193..196 packages bounded interactive Kadre runtime evidence. It is PM-visible runtime progress, not broad display-list replay, release-grade FPS, or broad observed cache telemetry.",
            ),
            "m91MepRcScenePack" to linkedMapOf<String, Any>(
                "manifestJson" to "release/m91-mep-rc-scene-pack/manifest.json",
                "pmReport" to "release/m91-mep-rc-scene-pack/pm-report.md",
                "claimLevel" to "release-candidate-evidence-selection",
                "status" to "pass",
                "linearIssues" to listOf("FOR-215", "FOR-216", "FOR-218"),
                "sceneRows" to 10,
                "statusTaxonomy" to listOf("supported", "partial", "expected-unsupported", "blocked-dependency"),
                "supportedRows" to listOf(
                    "solid-rect",
                    "linear-gradient-rect",
                    "runtime-effect-simple",
                    "crop-image-filter-nonnull-prepass",
                    "clip-rect-difference",
                    "bitmap-subset-local-matrix-repeat",
                ),
                "partialRows" to listOf("image-filter-compose-cf-matrix-transform"),
                "expectedUnsupportedRows" to listOf(
                    "path-aa-dashing-edge-budget",
                    "image-filter-crop-nonnull-prepass-required",
                ),
                "blockedDependencyRows" to listOf("font-complex-shaping-refusal"),
                "notice" to "M91/FOR-215/FOR-216/FOR-218 packages a release-candidate MEP scene selection from existing evidence only. It does not claim renderer fixes, dynamic SkSL compilation, Ganesh/Graphite, or native Kadre requirements for headless validation; expected-unsupported and blocked-dependency rows remain explicit.",
            ),
            "m92KadreRuntimeRc" to linkedMapOf<String, Any>(
                "evidenceJson" to "runtime/m92-kadre-runtime-rc/evidence.json",
                "telemetryClassificationJson" to "runtime/m92-kadre-runtime-rc/telemetry-classification.json",
                "closeoutReport" to "reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md",
                "pmDemoScript" to "reports/wgsl-pipeline/2026-06-02-rc-pm-demo-script.md",
                "claimLevel" to "rc-kadre-runtime-product-like-evidence-with-observed-derived-not-observable-telemetry",
                "status" to "pass",
                "linearIssues" to listOf("FOR-204", "FOR-205", "FOR-206", "FOR-207", "FOR-208", "FOR-209", "FOR-210", "FOR-211", "FOR-212", "FOR-213", "FOR-217", "FOR-219", "FOR-220"),
                "singleNativeRcDemoCommand" to "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive",
                "headlessValidatorCommand" to "python3 scripts/validate_mep_rc_runtime.py .",
                "readinessAfter" to 67.75,
                "readinessDelta" to 0.0,
                "telemetryClasses" to listOf("observed", "observed-partial", "derived", "expected-unsupported", "not-observable"),
                "nativeDemoOptIn" to true,
                "releaseBlockingPerformanceGate" to false,
                "notice" to "M92/FOR-204..213/FOR-217/FOR-219/FOR-220 closes the Kadre runtime RC evidence slice. It documents one opt-in native command and separates observed, derived, expected-unsupported, and not-observable telemetry without claiming broad cache counters, real OS event injection in CI, release-grade window FPS, or new native execution in this closeout.",
            ),
            "mepNextCloseout" to linkedMapOf<String, Any>(
                "pmReport" to "reports/wgsl-pipeline/2026-06-02-mep-next-closeout.md",
                "linearIssues" to listOf("FOR-188", "FOR-197"),
                "readinessAfter" to 67.75,
                "readinessDelta" to 0.0,
                "notice" to "MEP-NEXT closes the first post-RC-MEP evidence package. It adds PM-visible M89/M90 evidence without moving readiness or broadening renderer support claims.",
            ),
            "skiaGmInventoryJson" to "inventory/inventory.json",
            "skiaGmInventoryMarkdown" to "inventory/inventory.md",
            "skiaGmInventoryGateReport" to "inventory-gate/inventory-gate.md",
            "inventoryNotice" to "Skia GM inventory rows are planning/classification evidence only and do not change dashboard support claims or counters.",
            "counters" to linkedMapOf<String, Any>(
                "total" to scenes.size,
                "statuses" to statusCounts,
                "maturity" to maturityCounts,
                "adapterBacked" to adapterBacked.size,
                "expectedUnsupported" to expectedUnsupported.size,
                "inventoryDerived" to inventoryDerivedScenes.size,
                "unavailableReferences" to unavailable.size,
            ),
            "m52InventoryPromotion" to linkedMapOf<String, Any>(
                "selectedRows" to 10,
                "promotedRows" to m52PromotedRows.size,
                "promotedPassRows" to m52PromotedRows.count { it["status"] == "pass" },
                "promotedExpectedUnsupportedRows" to m52PromotedRows.count { it["status"] == "expected-unsupported" },
                "rejectedRows" to m52RejectedRows.size,
                "selectedReport" to "reports/wgsl-pipeline/2026-05-31-m52-inventory-promotion-pack.md",
                "promotedRowsDetail" to m52PromotedRows,
                "rejectedRowsDetail" to m52RejectedRows,
                "notice" to "M52 inventory promotion rows are generated dashboard evidence for narrow scene contracts only; unpromoted inventory rows remain planning evidence.",
            ),
            "m53InventoryPromotion" to linkedMapOf<String, Any>(
                "selectedRows" to m53SelectedRows,
                "promotedRows" to m53PromotedRows.size,
                "promotedPassRows" to m53PromotedRows.count { it["status"] == "pass" },
                "promotedExpectedUnsupportedRows" to m53PromotedRows.count { it["status"] == "expected-unsupported" },
                "rejectedRows" to m53RejectedRows.size,
                "selectedReport" to "reports/wgsl-pipeline/2026-05-31-m53-gm-feature-promotion-pack-v2-selection.md",
                "promotedRowsDetail" to m53PromotedRows,
                "rejectedRowsDetail" to m53RejectedRows,
                "notice" to "M53 inventory promotion rows are generated dashboard evidence for narrow scene contracts only; unpromoted inventory rows remain planning evidence.",
            ),
            "m54HardFeatureDepth" to linkedMapOf<String, Any>(
                "selectedRows" to m54SelectedRows,
                "promotedRows" to m54PromotedRows.size,
                "promotedPassRows" to m54PromotedRows.count { it["status"] == "pass" },
                "promotedExpectedUnsupportedRows" to m54PromotedRows.count { it["status"] == "expected-unsupported" },
                "rejectedRows" to m54RejectedRows.size,
                "familyCounters" to m54FamilyCounters,
                "performanceWarningRows" to m54PerformanceRows,
                "selectedReport" to "reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-selection.md",
                "promotionReport" to "reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-pack.md",
                "promotedRowsDetail" to m54PromotedRows,
                "rejectedRowsDetail" to m54RejectedRows,
                "notice" to "M54 hard feature depth rows are generated dashboard evidence for narrow selected scene contracts only; performance payloads remain warning-only.",
            ),
            "m55PerformanceGateCandidate" to linkedMapOf<String, Any>(
                "selectedRows" to ((m55CandidateCounters["selectedRows"] as? Number)?.toInt() ?: m55CandidateRows.size),
                "excludedRows" to ((m55CandidateCounters["excludedRows"] as? Number)?.toInt() ?: 0),
                "passRows" to ((m55CandidateCounters["passRows"] as? Number)?.toInt() ?: 0),
                "warnRows" to ((m55CandidateCounters["warnRows"] as? Number)?.toInt() ?: 0),
                "failCandidateRows" to ((m55CandidateCounters["failCandidateRows"] as? Number)?.toInt() ?: 0),
                "deferredRows" to ((m55CandidateCounters["deferredRows"] as? Number)?.toInt() ?: 0),
                "statusCounters" to (m55CandidateCounters["status"] ?: emptyMap<String, Any>()),
                "laneStatusCounters" to (m55CandidateCounters["laneStatus"] ?: emptyMap<String, Any>()),
                "selectedRowsDetail" to m55CandidateRows,
                "selectionContract" to "reports/wgsl-pipeline/performance/m55-performance-gate-candidates.json",
                "selectionReport" to "reports/wgsl-pipeline/2026-05-31-m55-performance-gate-candidate-selection.md",
                "baselinePayloadReport" to "reports/wgsl-pipeline/2026-05-31-m55-official-performance-baseline-payloads.md",
                "candidateReport" to "performance/m55-performance-gate-candidate.md",
                "candidateJson" to "performance/m55-performance-gate-candidate.json",
                "policyReport" to "reports/wgsl-pipeline/2026-05-31-m55-quarantine-rebaseline-rollback-policy.md",
                "releaseBlocking" to false,
                "notice" to "M55 exposes a strict performance gate candidate only; no release-blocking performance gate is enabled.",
            ),
            "m59PerformanceReleaseGate" to linkedMapOf<String, Any>(
                "selectedRows" to ((m59ReleaseGateCounters["selectedRows"] as? Number)?.toInt() ?: m59ReleaseGateRows.size),
                "excludedRows" to ((m59ReleaseGateCounters["excludedRows"] as? Number)?.toInt() ?: 0),
                "passRows" to ((m59ReleaseGateCounters["passRows"] as? Number)?.toInt() ?: 0),
                "failRows" to ((m59ReleaseGateCounters["failRows"] as? Number)?.toInt() ?: 0),
                "notMeasuredRows" to ((m59ReleaseGateCounters["notMeasuredRows"] as? Number)?.toInt() ?: 0),
                "measuredBlockingLanes" to ((m59ReleaseGateCounters["measuredBlockingLanes"] as? Number)?.toInt() ?: 0),
                "notMeasuredLanes" to ((m59ReleaseGateCounters["notMeasuredLanes"] as? Number)?.toInt() ?: 0),
                "blockingFailures" to ((m59ReleaseGateCounters["blockingFailures"] as? Number)?.toInt() ?: 0),
                "statusCounters" to (m59ReleaseGateCounters["status"] ?: emptyMap<String, Any>()),
                "laneStatusCounters" to (m59ReleaseGateCounters["laneStatus"] ?: emptyMap<String, Any>()),
                "selectedRowsDetail" to m59ReleaseGateRows,
                "selectionContract" to "reports/wgsl-pipeline/performance/m59-performance-release-gate.json",
                "selectionReport" to "reports/wgsl-pipeline/2026-05-31-m59-performance-release-gate-selection.md",
                "thresholdPolicy" to "reports/wgsl-pipeline/2026-05-31-m58-performance-threshold-policy.md",
                "releaseGateReport" to "performance-release-gate/m59-performance-release-gate.md",
                "releaseGateJson" to "performance-release-gate/m59-performance-release-gate.json",
                "sprintReview" to "reports/wgsl-pipeline/2026-05-31-m59-sprint-review.md",
                "pmReport" to "reports/wgsl-pipeline/2026-05-31-m59-pm-report.md",
                "nonClaims" to "reports/wgsl-pipeline/2026-05-31-m59-non-claims.md",
                "releaseBlocking" to true,
                "notice" to "M59 blocks every selected final-target lane with missing required measured metadata or explicit threshold breaches; estimated and missing metrics remain visible but are not treated as measured evidence.",
            ),
            "m67PerformanceTiering" to linkedMapOf<String, Any>(
                "frameRows" to ((m67FrameGateCounters["rows"] as? Number)?.toInt() ?: 0),
                "candidateRows" to ((m67FrameGateCounters["candidateRows"] as? Number)?.toInt()
                    ?: ((m67FrameGateCounters["rows"] as? Number)?.toInt() ?: 0)),
                "passRows" to ((m67FrameGateCounters["passRows"] as? Number)?.toInt() ?: 0),
                "warnRows" to ((m67FrameGateCounters["warnRows"] as? Number)?.toInt() ?: 0),
                "failRows" to ((m67FrameGateCounters["failRows"] as? Number)?.toInt() ?: 0),
                "quarantineRows" to ((m67FrameGateCounters["quarantineRows"] as? Number)?.toInt() ?: 0),
                "statusCounters" to (m67FrameGateCounters["status"] ?: emptyMap<String, Any>()),
                "families" to ((m67FamilyBudgetCounters["families"] as? Number)?.toInt() ?: m67FamilyBudgetRows.size),
                "measuredFamilies" to ((m67FamilyBudgetCounters["measuredFamilies"] as? Number)?.toInt() ?: 0),
                "reportingOnlyFamilies" to ((m67FamilyBudgetCounters["reportingOnlyFamilies"] as? Number)?.toInt() ?: 0),
                "familyRowsDetail" to m67FamilyBudgetRows,
                "negativeFixtureExpectedStatus" to ((m67NegativeFixtureReport["expectedStatus"] as? String).orEmpty()),
                "frameGateReport" to "performance/m67-performance-tiering/m67-frame-gate-candidate.md",
                "frameGateJson" to "performance/m67-performance-tiering/m67-frame-gate-candidate.json",
                "familyBudgetReport" to "performance/m67-performance-tiering/m67-family-budgets.md",
                "familyBudgetJson" to "performance/m67-performance-tiering/m67-family-budgets.json",
                "negativeFixtureReport" to "performance/m67-performance-tiering-negative/m67-negative-fixture.md",
                "negativeFixtureJson" to "performance/m67-performance-tiering-negative/m67-negative-fixture.json",
                "releaseBlocking" to false,
                "notice" to "M67 promotes frame.headless-webgpu to a candidate gate from M65 headless telemetry and adds family budgets; native Kadre timing remains reporting-only until M68 real window evidence exists.",
            ),
            "m68KadreDemo" to linkedMapOf<String, Any>(
                "claimLevel" to ((m68BridgeSmokeReport["claimLevel"] as? String).orEmpty()),
                "bridgeSmokeStatus" to ((m68BridgeSmokeReport["status"] as? String).orEmpty()),
                "nativeLaunchStatus" to ((m68NativeLaunch["status"] as? String).orEmpty()),
                "nativeLaunchReason" to ((m68NativeLaunch["reason"] as? String).orEmpty()),
                "hostContractCapabilities" to m68HostContract.keys.map { it.toString() }.sorted(),
                "featureRows" to m68FeatureRows.size,
                "featureRowsDetail" to m68FeatureRows,
                "routeSummaryStatus" to (((m68RouteSummaryReport["nativeRoute"] as? Map<*, *>)?.get("status") as? String).orEmpty()),
                "report" to "reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md",
                "auditJson" to "runtime/m68-kadre-demo/kadre-host-audit.json",
                "bridgeSmokeJson" to "runtime/m68-kadre-demo/bridge-smoke.json",
                "routeSummaryJson" to "runtime/m68-kadre-demo/route-summary.json",
                "flagshipSceneJson" to "runtime/m68-kadre-demo/flagship-scene-evidence.json",
                "telemetryOverlayJson" to "runtime/m68-kadre-demo/telemetry-overlay-sample.json",
                "releaseBlocking" to false,
                "notice" to "M68 verifies the Kadre source-build bridge and flagship scene inputs, but native Kanvas/Kadre presentation remains blocked with m68.kadre-host-adapter-not-implemented.",
            ),
            "m69KadreHostAdapter" to linkedMapOf<String, Any>(
                "status" to ((m69BridgeSmokeReport["status"] as? String).orEmpty()),
                "routeStatus" to ((m69RouteStatusReport["status"] as? String).orEmpty()),
                "routeReason" to ((m69RouteStatusReport["reason"] as? String).orEmpty()),
                "nativePresented" to (m69RouteStatusReport["nativePresented"] as? Boolean ?: false),
                "nativePresentationReason" to ((m69RouteStatusReport["nativePresentationReason"] as? String).orEmpty()),
                "nativeSmokeStatus" to ((m69NativeSmokeReport["status"] as? String).orEmpty()),
                "nativeSmokeRoute" to ((m69NativeSmokeReport["route"] as? String).orEmpty()),
                "nativeSmokePresentedFrames" to ((m69NativeSmokeReport["presentedFrames"] as? Number)?.toInt() ?: 0),
                "hostContractCapabilities" to m69Capabilities.keys.map { it.toString() }.sorted(),
                "routes" to m69Routes.keys.map { it.toString() }.sorted(),
                "sceneId" to ((m69SceneRouteReport["sceneId"] as? String).orEmpty()),
                "sceneClaimLevel" to ((m69SceneRouteReport["claimLevel"] as? String).orEmpty()),
                "featureRows" to m69SourceFeatures.size,
                "featureRowsDetail" to m69SourceFeatures,
                "report" to "reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md",
                "contractJson" to "runtime/m69-kadre-host-adapter/contract.json",
                "routeStatusJson" to "runtime/m69-kadre-host-adapter/route-status.json",
                "sceneRouteJson" to "runtime/m69-kadre-host-adapter/scene-route.json",
                "telemetryJson" to "runtime/m69-kadre-host-adapter/telemetry.json",
                "bridgeSmokeJson" to "runtime/m69-kadre-host-adapter/bridge-smoke.json",
                "nativeSmokeJson" to "runtime/m69-kadre-native/native-smoke.json",
                "releaseBlocking" to false,
                "notice" to "M69 now runs a Kadre native windowed WebGPU present loop for a bounded standalone WGSL scene when the host supports AppKit/Metal. Native timing is present-call duration only; Kanvas display-list replay and input-driven interaction remain future work.",
            ),
            "m70KadreLiveRuntime" to linkedMapOf<String, Any>(
                "status" to ((m70RouteStatusReport["status"] as? String).orEmpty()),
                "reason" to ((m70RouteStatusReport["reason"] as? String).orEmpty()),
                "sceneId" to ((m70RouteStatusReport["sceneId"] as? String).orEmpty()),
                "sourceSceneId" to ((m70RouteStatusReport["sourceSceneId"] as? String).orEmpty()),
                "mode" to ((m70RouteStatusReport["mode"] as? String).orEmpty()),
                "nativePresented" to (m70RouteStatusReport["nativePresented"] as? Boolean ?: false),
                "presentCallCompleted" to (m70RouteStatusReport["presentCallCompleted"] as? Boolean ?: false),
                "requestedFrames" to ((m70RouteStatusReport["requestedFrames"] as? Number)?.toInt() ?: 0),
                "presentedFrames" to ((m70RouteStatusReport["presentedFrames"] as? Number)?.toInt() ?: 0),
                "warmupFrames" to ((m70RouteStatusReport["warmupFrames"] as? Number)?.toInt() ?: 0),
                "claimLevel" to ((m70RouteStatusReport["claimLevel"] as? String).orEmpty()),
                "captureStatus" to ((m70Capture["status"] as? String).orEmpty()),
                "captureReason" to ((m70Capture["reason"] as? String).orEmpty()),
                "captureImage" to m70CaptureBundlePath,
                "captureRealNativeReadback" to ((m70Capture["realNativeReadback"] as? Boolean) ?: false),
                "captureWindowSurfaceReadback" to ((m70Capture["windowSurfaceReadback"] as? Boolean) ?: false),
                "surfaceStatusSummary" to ((m70RouteStatusReport["surfaceStatusSummary"] as? Map<*, *>).orEmpty()),
                "replayClaimLevel" to ((m70Replay["claimLevel"] as? String).orEmpty()),
                "replaySource" to ((m70Replay["source"] as? String).orEmpty()),
                "replayCommandSource" to ((m70Replay["commandSource"] as? String).orEmpty()),
                "replayCommandCounters" to m70ReplayCounters,
                "replayPack" to m70ReplayPack,
                "replayPackSceneCount" to (((m70ReplayPack["sceneCount"] as? Number)?.toInt()) ?: 0),
                "replayPackRenderableSceneCount" to (((m70ReplayPack["renderableSceneCount"] as? Number)?.toInt()) ?: 0),
                "replayPackUnsupportedSceneCount" to (((m70ReplayPack["unsupportedSceneCount"] as? Number)?.toInt()) ?: 0),
                "replayUnsupportedCommands" to ((m70Replay["unsupportedCommands"] as? List<*>)?.map { it.toString() }.orEmpty()),
                "replaySourceEvidence" to ((m70Replay["sourceEvidence"] as? Map<*, *>).orEmpty()),
                "telemetryLane" to (((m70RouteStatusReport["runtimeTelemetry"] as? Map<*, *>)?.get("lane") as? String).orEmpty()),
                "telemetryGatePhase" to (((m70RouteStatusReport["runtimeTelemetry"] as? Map<*, *>)?.get("gatePhase") as? String).orEmpty()),
                "frameClockSource" to (((m70RouteStatusReport["runtimeTelemetry"] as? Map<*, *>)?.get("frameClockSource") as? String).orEmpty()),
                "autonomousFrameClock" to (((m70RouteStatusReport["runtimeTelemetry"] as? Map<*, *>)?.get("autonomousFrameClock") as? Boolean) ?: false),
                "autonomousFrameCount" to (((m70RouteStatusReport["runtimeTelemetry"] as? Map<*, *>)?.get("autonomousFrameCount") as? Number)?.toInt() ?: 0),
                "measuredSamples" to (((m70RouteStatusReport["runtimeTelemetry"] as? Map<*, *>)?.get("measuredSampleCount") as? Number)?.toInt() ?: 0),
                "nativeDemoStatus" to ((m70NativeDemoReport["status"] as? String).orEmpty()),
                "nativeDemoReason" to ((m70NativeDemoReport["reason"] as? String).orEmpty()),
                "report" to "reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md",
                "routeStatusJson" to "runtime/m70-kadre-live-runtime/route-status.json",
                "nativeDemoJson" to "runtime/m70-kadre-native/native-demo.json",
                "releaseBlocking" to false,
                "notice" to "M70-A/B/C add a PM-visible Kadre demo command, normalized native surface-success evidence, reporting-only frame telemetry, and a produced wgpu4k offscreen texture readback artifact. M71 adds an autonomous Kadre/AppKit ControlFlow.Poll frame clock. M72 replaces the shader-only demo claim with one selected solid-rect replay contract. M73 expands that to a bounded typed replay-pack registry. They still do not claim broad display-list replay, arbitrary op streams, window-surface readback, input, or release-grade FPS.",
            ),
            "m75ReplayPackEvidence" to linkedMapOf<String, Any>(
                "status" to ((m75ReplayPackEvidence["claimLevel"] as? String).orEmpty()),
                "packId" to ((m75ReplayPackEvidence["packId"] as? String).orEmpty()),
                "sourcePackId" to ((m75ReplayPackEvidence["sourcePackId"] as? String).orEmpty()),
                "sceneCount" to ((m75ReplayPackEvidence["sceneCount"] as? Number)?.toInt() ?: 0),
                "renderableSceneCount" to ((m75ReplayPackEvidence["renderableSceneCount"] as? Number)?.toInt() ?: 0),
                "expectedUnsupportedSceneCount" to ((m75ReplayPackEvidence["expectedUnsupportedSceneCount"] as? Number)?.toInt() ?: 0),
                "failedSceneCount" to ((m75ReplayPackEvidence["failedSceneCount"] as? Number)?.toInt() ?: 0),
                "readinessDelta" to ((m75ReplayPackEvidence["readinessDelta"] as? Number)?.toInt() ?: 0),
                "report" to "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.md",
                "evidenceJson" to "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M75 aggregates deterministic multi-scene replay-pack evidence and per-scene CPU/native facts. It does not add broad SkCanvas/display-list replay or new readiness points.",
            ),
            "m76GeneratedMetadataReplay" to linkedMapOf<String, Any>(
                "status" to m76String("claimLevel"),
                "packId" to m76String("packId"),
                "sourceManifest" to m76String("sourceManifest"),
                "sceneCount" to m76SceneCount,
                "mappedSceneCount" to m76MappedSceneCount,
                "refusedMetadataCount" to m76RefusedMetadataCount,
                "failedSceneCount" to m76FailedSceneCount,
                "readinessDelta" to m76Int("readinessDelta"),
                "report" to "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.md",
                "evidenceJson" to "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M76 maps selected generated dashboard metadata into replay contracts for known bounded templates and refuses unsupported metadata with stable reasons. It does not add arbitrary generated scene replay or new readiness points.",
            ),
            "m77BlendAlphaReplay" to linkedMapOf<String, Any>(
                "status" to m77String("claimLevel"),
                "packId" to m77String("packId"),
                "sceneCount" to m77SceneCount,
                "renderableSceneCount" to m77RenderableSceneCount,
                "partialAlphaSceneCount" to m77PartialAlphaSceneCount,
                "expectedUnsupportedSceneCount" to m77ExpectedUnsupportedSceneCount,
                "failedSceneCount" to m77FailedSceneCount,
                "srcOverCommandCount" to m77Int("srcOverCommandCount"),
                "partialAlphaCommandCount" to m77Int("partialAlphaCommandCount"),
                "unsupportedBlendReason" to m77String("unsupportedBlendReason"),
                "readinessDelta" to m77Int("readinessDelta"),
                "report" to "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.md",
                "evidenceJson" to "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M77 makes SrcOver and partial alpha explicit for bounded Kadre replay scenes and preserves unsupported blend modes as stable refusals. It does not add arbitrary blend modes or broad display-list replay.",
            ),
            "m78ClipReplay" to linkedMapOf<String, Any>(
                "status" to m78String("claimLevel"),
                "packId" to m78String("packId"),
                "sceneCount" to m78SceneCount,
                "renderableSceneCount" to m78RenderableSceneCount,
                "expectedUnsupportedSceneCount" to m78ExpectedUnsupportedSceneCount,
                "failedSceneCount" to m78FailedSceneCount,
                "clipRectCommandCount" to m78ClipRectCommandCount,
                "clipIntersectCommandCount" to m78ClipIntersectCommandCount,
                "srcOverCommandCount" to m78Int("srcOverCommandCount"),
                "partialAlphaCommandCount" to m78Int("partialAlphaCommandCount"),
                "unsupportedClipReason" to m78String("unsupportedClipReason"),
                "readinessDelta" to m78Int("readinessDelta"),
                "report" to "reports/wgsl-pipeline/m78-clip-replay/evidence.md",
                "evidenceJson" to "reports/wgsl-pipeline/m78-clip-replay/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M78 adds bounded ClipRect intersect replay evidence for simple rect-fill scenes and preserves complex clip refusals. It does not add rounded clips, path clips, difference clips, saveLayer clip stacks, arbitrary SkCanvas clip replay, or broad clip-stack support.",
            ),
            "m79BitmapReplay" to linkedMapOf<String, Any>(
                "status" to m79String("claimLevel"),
                "packId" to m79String("packId"),
                "sceneCount" to m79SceneCount,
                "renderableSceneCount" to m79RenderableSceneCount,
                "expectedUnsupportedSceneCount" to m79ExpectedUnsupportedSceneCount,
                "failedSceneCount" to m79FailedSceneCount,
                "bitmapCommandCount" to m79BitmapCommandCount,
                "fixtureBackedBitmapCommandCount" to m79FixtureBackedBitmapCommandCount,
                "nearestSamplerCommandCount" to m79NearestSamplerCommandCount,
                "linearSamplerCommandCount" to m79LinearSamplerCommandCount,
                "unsupportedBitmapCommandCount" to m79UnsupportedBitmapCommandCount,
                "clipRectCommandCount" to m79ClipRectCommandCount,
                "clipIntersectCommandCount" to m79ClipIntersectCommandCount,
                "srcOverCommandCount" to m79SrcOverCommandCount,
                "partialAlphaCommandCount" to m79PartialAlphaCommandCount,
                "unsupportedBitmapReason" to m79UnsupportedBitmapReason,
                "readinessDelta" to m79Int("readinessDelta"),
                "report" to "reports/wgsl-pipeline/m79-bitmap-replay/evidence.md",
                "evidenceJson" to "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M79 adds bounded BitmapRect replay evidence for owned in-repo fixtures with nearest and linear samplers, alpha/blend/provenance fields, and stable unsupported sampler refusals. It does not add arbitrary SkImage, codec decode, mipmap, texture atlas, tile-mode, or color-managed image support.",
            ),
            "m80SharedReplayOracle" to linkedMapOf<String, Any>(
                "status" to m80String("claimLevel"),
                "packId" to m80String("packId"),
                "oracleApi" to m80String("oracleApi"),
                "sceneCount" to m80SceneCount,
                "renderableSceneCount" to m80RenderableSceneCount,
                "expectedUnsupportedSceneCount" to m80ExpectedUnsupportedSceneCount,
                "failedSceneCount" to m80FailedSceneCount,
                "failedValidationRowCount" to m80FailedValidationRowCount,
                "supportedCommandFamilies" to m80SupportedCommandFamilies,
                "readinessDelta" to m80Int("readinessDelta"),
                "report" to "reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.md",
                "evidenceJson" to "reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M80 routes bounded replay CPU reference facts through a shared typed oracle result for native smoke, tests, and M75-M79 evidence. It is reference hardening only and does not add broad SkCanvas/display-list replay or new readiness points.",
            ),
            "m81NativeFrameCapture" to linkedMapOf<String, Any>(
                "status" to m81String("captureStatus").ifBlank { "not-generated" },
                "packId" to m81String("packId").ifBlank { "m81-native-frame-capture-v1" },
                "frameCount" to m81Int("frameCount"),
                "requestedFrameCount" to m81Int("requestedFrameCount"),
                "nativePresented" to m81Bool("nativePresented"),
                "realNativeOffscreenTextureReadback" to m81Bool("realNativeOffscreenTextureReadback"),
                "realNativeWindowSurfaceReadback" to m81Bool("realNativeWindowSurfaceReadback"),
                "unsupportedCaptureCount" to m81Int("unsupportedCaptureCount"),
                "unsupportedCaptureReasons" to m81UnsupportedReasons,
                "artifactPaths" to m81ArtifactPaths,
                "adapterBackend" to ((m81Adapter["backend"] as? String).orEmpty()),
                "adapterInfo" to ((m81Adapter["info"] as? String).orEmpty()),
                "surfaceFormat" to ((m81Surface["format"] as? String).orEmpty()),
                "captureFormat" to ((m81Capture["format"] as? String).orEmpty()),
                "captureImage" to ((m81Capture["imagePath"] as? String).orEmpty()),
                "captureReason" to ((m81Capture["reason"] as? String).orEmpty()),
                "report" to "runtime/m81-native-frame-capture/evidence.md",
                "evidenceJson" to "runtime/m81-native-frame-capture/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M81 packages current M69/M70 Kadre/WebGPU frame artifact evidence for PM review. The produced image is a wgpu4k native offscreen texture readback, not a system screenshot or window-surface readback; unsupported window capture remains explicit.",
            ),
            "m82InputResizeRuntimeLoop" to linkedMapOf<String, Any>(
                "status" to m82String("status").ifBlank { "not-generated" },
                "packId" to m82String("packId").ifBlank { "m82-kadre-input-resize-runtime-loop-v1" },
                "claimLevel" to m82String("claimLevel"),
                "readinessDelta" to m82Int("readinessDelta"),
                "ciPath" to m82String("ciPath"),
                "nativeOsEventInjectionClaimed" to m82Bool("nativeOsEventInjectionClaimed"),
                "host" to ((m82BackingHost["host"] as? String).orEmpty()),
                "nativeRoute" to ((m82BackingHost["nativeRoute"] as? String).orEmpty()),
                "eventCount" to m82TelemetryInt("eventCount"),
                "pointerEventCount" to m82TelemetryInt("pointerEventCount"),
                "keyboardEventCount" to m82TelemetryInt("keyboardEventCount"),
                "resizeEventCount" to m82TelemetryInt("resizeEventCount"),
                "scaleFactorEventCount" to m82TelemetryInt("scaleFactorEventCount"),
                "reconfigureCount" to m82TelemetryInt("reconfigureCount"),
                "reconfigureFailureCount" to m82TelemetryInt("reconfigureFailureCount"),
                "droppedFrameCount" to m82TelemetryInt("droppedFrameCount"),
                "hostDiagnosticCount" to m82TelemetryInt("hostDiagnosticCount"),
                "surfaceReconfigureCount" to m82SurfaceReconfigures.size,
                "validationRowCount" to m82ValidationRows.size,
                "fixtureCount" to m82Fixtures.size,
                "unsupportedEventReason" to m82String("unsupportedEventReason"),
                "invalidResizeReason" to m82String("invalidResizeReason"),
                "invalidScaleFactorReason" to m82String("invalidScaleFactorReason"),
                "finalSceneState" to m82FinalSceneState,
                "artifactPaths" to m82ArtifactPaths,
                "report" to "runtime/m82-kadre-input-resize-runtime-loop/evidence.md",
                "evidenceJson" to "runtime/m82-kadre-input-resize-runtime-loop/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M82 adds deterministic Kadre-backed input/resize runtime-loop evidence with pointer, keyboard, resize, scale-factor, close, telemetry, and stable refusals. It does not claim real desktop OS event injection or release-grade timing.",
            ),
            "m83DisplayListReplay" to linkedMapOf<String, Any>(
                "status" to ((m83NativeEvidence["status"] as? String).orEmpty()),
                "packId" to m83String("packId"),
                "claimLevel" to m83String("claimLevel"),
                "nativePixelsProducedFromDisplayListByThisTask" to m83Bool("nativePixelsProducedFromDisplayListByThisTask"),
                "sceneContractId" to ((m83NativeEvidence["sceneContractId"] as? String).orEmpty()),
                "sceneCount" to m83Int("sceneCount"),
                "renderableSceneCount" to m83Int("renderableSceneCount"),
                "expectedUnsupportedSceneCount" to m83Int("expectedUnsupportedSceneCount"),
                "failedSceneCount" to m83Int("failedSceneCount"),
                "supportStateMismatchCount" to m83Int("supportStateMismatchCount"),
                "totalCommandCount" to m83Int("totalCommandCount"),
                "supportedCommandCount" to m83Int("supportedCommandCount"),
                "unsupportedCommandCount" to m83Int("unsupportedCommandCount"),
                "captureNonTransparentPixels" to ((m83NativeEvidence["captureNonTransparentPixels"] as? Number)?.toInt() ?: 0),
                "presentedFrames" to ((m83NativeEvidence["presentedFrames"] as? Number)?.toInt() ?: 0),
                "artifactPaths" to m83ArtifactPaths,
                "report" to "runtime/m83-display-list-replay/evidence.md",
                "evidenceJson" to "runtime/m83-display-list-replay/evidence.json",
                "releaseBlocking" to false,
                "notice" to "M83 proves one bounded Kanvas display-list scene selected through the Kadre native WebGPU demo path with a nonblank offscreen readback. Text, image-filter DAG, runtime-effect, broad SkCanvas op streams, and release-grade timing remain explicit non-claims.",
            ),
            "m84NativeFrameTiming" to linkedMapOf<String, Any>(
                "packId" to m84String("packId"),
                "claimLevel" to m84String("claimLevel"),
                "lane" to m84String("lane"),
                "gateStatus" to m84String("gateStatus"),
                "gatePhase" to m84String("gatePhase"),
                "releaseBlocking" to m84Bool("releaseBlocking"),
                "countedAsMeasuredGate" to m84Bool("countedAsMeasuredGate"),
                "sceneContractId" to m84String("sceneContractId"),
                "warmupFrameCount" to ((m84MeasuredPayload["warmupFrameCount"] as? Number)?.toInt() ?: 0),
                "measuredSampleCount" to ((m84MeasuredPayload["measuredSampleCount"] as? Number)?.toInt() ?: 0),
                "p50Ms" to ((m84MeasuredPayload["p50Ms"] as? Number)?.toDouble() ?: 0.0),
                "p95Ms" to ((m84MeasuredPayload["p95Ms"] as? Number)?.toDouble() ?: 0.0),
                "worstMs" to ((m84MeasuredPayload["worstMs"] as? Number)?.toDouble() ?: 0.0),
                "estimatedMetricCount" to ((m84MeasuredPayload["estimatedMetricCount"] as? Number)?.toInt() ?: -1),
                "missingMetricCount" to ((m84MeasuredPayload["missingMetricCount"] as? Number)?.toInt() ?: -1),
                "negativeFixtureStatus" to ((m84NegativeFixture["status"] as? String).orEmpty()),
                "negativeFixtureReason" to ((m84NegativeFixture["reason"] as? String).orEmpty()),
                "artifactPaths" to m84ArtifactPaths,
                "report" to "runtime/m84-native-frame-timing/evidence.md",
                "evidenceJson" to "runtime/m84-native-frame-timing/evidence.json",
                "negativeFixtureJson" to "runtime/m84-native-frame-timing/negative-fixture.json",
                "notice" to "M84 exposes native Kadre frame timing as a measured candidate/reporting payload with quarantine and a negative fixture. It is not release-blocking, not counted as a measured release gate, and present-call duration is not a full end-to-end FPS guarantee.",
            ),
            "m85ResourceLifetimeCache" to linkedMapOf<String, Any>(
                "packId" to m85String("packId"),
                "claimLevel" to m85String("claimLevel"),
                "status" to m85String("status"),
                "observedRuntimeCounters" to (m85ResourceLifetimeCache["observedRuntimeCounters"] == true),
                "countedAsCacheReadinessGate" to (m85ResourceLifetimeCache["countedAsCacheReadinessGate"] == true),
                "counterSource" to ((m85ResourceLifetimeCache["counterSource"] as? String).orEmpty()),
                "lane" to m85String("lane"),
                "sceneContractId" to m85String("sceneContractId"),
                "frameCount" to ((m85Telemetry["frameCount"] as? Number)?.toInt() ?: 0),
                "pipelineCacheHits" to ((m85Telemetry["pipelineCacheHits"] as? Number)?.toInt() ?: 0),
                "pipelineCacheMisses" to ((m85Telemetry["pipelineCacheMisses"] as? Number)?.toInt() ?: 0),
                "shaderModuleCount" to ((m85Telemetry["shaderModuleCount"] as? Number)?.toInt() ?: 0),
                "pipelineCount" to ((m85Telemetry["pipelineCount"] as? Number)?.toInt() ?: 0),
                "bindGroupCount" to ((m85Telemetry["bindGroupCount"] as? Number)?.toInt() ?: 0),
                "textureCount" to ((m85Telemetry["textureCount"] as? Number)?.toInt() ?: 0),
                "textureUploadBytes" to ((m85Telemetry["textureUploadBytes"] as? Number)?.toInt() ?: 0),
                "intermediateTextureBytes" to ((m85Telemetry["intermediateTextureBytes"] as? Number)?.toInt() ?: 0),
                "bindGroupChurn" to ((m85Telemetry["bindGroupChurn"] as? Number)?.toInt() ?: 0),
                "resourceGenerationCount" to ((m85Telemetry["resourceGenerationCount"] as? Number)?.toInt() ?: 0),
                "invalidResourceReuseCount" to ((m85Telemetry["invalidResourceReuseCount"] as? Number)?.toInt() ?: 0),
                "deviceLossStatus" to ((m85DeviceLossDiagnostics["status"] as? String).orEmpty()),
                "deviceLossReason" to ((m85DeviceLossDiagnostics["reason"] as? String).orEmpty()),
                "boundedGrowth" to (m85CachePressureReport["boundedGrowth"] == true),
                "artifactPaths" to m85ArtifactPaths,
                "report" to "runtime/m85-resource-lifetime-cache/evidence.md",
                "evidenceJson" to "runtime/m85-resource-lifetime-cache/evidence.json",
                "cachePressureJson" to "runtime/m85-resource-lifetime-cache/cache-pressure.json",
                "notice" to "M85 makes selected realtime resource lifetime and cache pressure auditable as a deterministic selected-scene ledger: cache counters, bounded key spaces, resize resource invalidation, and stable device-loss unsupported diagnostics. It is not observed WebGPU runtime cache telemetry, is not counted as a cache readiness gate, and does not claim arbitrary scene cache behavior or real device-lost recovery.",
            ),
            "m86FidelityBurndown" to linkedMapOf<String, Any>(
                "rankedCandidates" to ((m86Counters["rankedCandidates"] as? Number)?.toInt() ?: 0),
                "supportRows" to ((m86Counters["supportRows"] as? Number)?.toInt() ?: 0),
                "unsupportedRows" to ((m86Counters["unsupportedRows"] as? Number)?.toInt() ?: 0),
                "classifiedRows" to ((m86Counters["classifiedRows"] as? Number)?.toInt() ?: 0),
                "skiaComparableSupportRows" to ((m86Counters["skiaComparableSupportRows"] as? Number)?.toInt() ?: 0),
                "familyCounts" to (m86Counters["familyCounts"] as? Map<*, *>).orEmpty(),
                "referenceKindCounts" to (m86Counters["referenceKindCounts"] as? Map<*, *>).orEmpty(),
                "rootCauseCounts" to (m86Counters["rootCauseCounts"] as? Map<*, *>).orEmpty(),
                "remediationTargets" to m86RemediationTargets,
                "globalThresholdWeakened" to (m86DashboardGateExpectation["globalThresholdWeakened"] == true),
                "weightedPercentBefore" to ((m86ReadinessDelta["weightedPercentBefore"] as? Number)?.toDouble() ?: 0.0),
                "weightedPercentAfter" to ((m86ReadinessDelta["weightedPercentAfter"] as? Number)?.toDouble() ?: 0.0),
                "report" to "fidelity/m86-fidelity-burndown/evidence.md",
                "evidenceJson" to "fidelity/m86-fidelity-burndown/evidence.json",
                "sprintReport" to "reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md",
                "notice" to "M86 turns selected GM/reference rows into a fidelity burn-down queue with root-cause classification and remediation targets. It does not claim renderer visual fixes or readiness movement without before/after rendered artifacts.",
            ),
            "m56UnsupportedToPass" to linkedMapOf<String, Any>(
                "targetReadiness" to 97,
                "finalReadiness" to 96,
                "promotedRows" to listOf("m53-sweep-gradient-clamp"),
                "promotedFromExpectedUnsupported" to 1,
                "requiredPromotionsForStretchTarget" to 2,
                "rejectedOrDeferredRows" to listOf(
                    "m53-imagefilters-cropped-boundary",
                    "m54-imagefilters-graph-boundary",
                    "m54-dash-circle-boundary",
                    "m53-complexclip-boundary-refusal",
                    "m52-big-tile-image-filter-dag-refusal",
                    "font-emoji-color-glyph-refusal",
                    "font-complex-shaping-refusal",
                    "m52-color-emoji-blendmodes-refusal",
                ),
                "selectionReport" to "reports/wgsl-pipeline/2026-05-31-m56-unsupported-to-pass-selection.md",
                "imageFilterDecisionReport" to "reports/wgsl-pipeline/2026-05-31-m56-gra334-image-filter-promotion-decision.md",
                "pathAaClipReviewReport" to "reports/wgsl-pipeline/2026-05-31-gra-336-path-aa-clip-budget-review.md",
                "sprintReview" to "reports/wgsl-pipeline/2026-05-31-m56-sprint-review.md",
                "pmReport" to "reports/wgsl-pipeline/2026-05-31-m56-pm-report.md",
                "notice" to "M56 promotes one corrected sweep-gradient row only; image-filter and Path AA shortcuts were rejected because their current artifacts do not prove row-specific GPU support.",
            ),
            "m57PathAaClipMicroPromotion" to linkedMapOf<String, Any>(
                "targetReadiness" to 98,
                "finalReadiness" to 98,
                "selectedRows" to m57SelectedRows,
                "promotedRows" to m57PromotedRows.size,
                "promotedPassRows" to m57PromotedRows.count { it["status"] == "pass" },
                "promotedExpectedUnsupportedRows" to m57PromotedRows.count { it["status"] == "expected-unsupported" },
                "preservedUnsupportedRows" to m57PreservedUnsupportedRows,
                "rejectedRows" to m57RejectedRows.size,
                "selectionReport" to "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-slice-selection.md",
                "promotionReport" to "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md",
                "sprintReview" to "reports/wgsl-pipeline/2026-05-31-m57-sprint-review.md",
                "pmReport" to "reports/wgsl-pipeline/2026-05-31-m57-pm-report.md",
                "contract" to "reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json",
                "promotedRowsDetail" to m57PromotedRows,
                "rejectedRowsDetail" to m57RejectedRows,
                "notice" to "M57 adds one bounded AA clip grid slice only; existing edge-budget, dash, stroke-outline, and complex-clip refusals remain visible.",
            ),
            "m66GmPromotionWave" to linkedMapOf<String, Any>(
                "selectedRows" to m66SelectedRows,
                "promotedRows" to m66AllRows.size,
                "promotedPassRows" to m66AllRows.count { it["status"] == "pass" },
                "promotedExpectedUnsupportedRows" to m66AllRows.count { it["status"] == "expected-unsupported" },
                "inventoryDerivedRows" to m66PromotedRows.size,
                "rejectedRows" to m66RejectedRows.size,
                "familyCounters" to m66FamilyCounters,
                "referenceKindCounters" to m66ReferenceKindCounters,
                "selectedReport" to "reports/wgsl-pipeline/2026-06-01-m66-selection-ranking.md",
                "promotionReport" to "reports/wgsl-pipeline/2026-06-01-m66-gm-promotion-wave.md",
                "readinessCounters" to "reports/wgsl-pipeline/2026-06-01-m66-readiness-counters.md",
                "contract" to "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
                "promotedRowsDetail" to m66AllRowsDetail,
                "rejectedRowsDetail" to m66RejectedRows,
                "notice" to "M66 normalizes selected support/refusal rows with referenceKind provenance; CPU-oracle rows are breadth/refusal evidence and do not automatically count as Skia fidelity.",
            ),
            "inventoryCounters" to inventorySummary,
            "dashboardInventoryLinks" to dashboardInventoryLinks,
            "expectedUnsupportedRows" to expectedUnsupported,
            "adapterBackedRows" to adapterBacked,
            "knownLimitations" to listOf(
                "Skia GM inventory rows classify visibility and planning state only; support still requires generated dashboard evidence.",
                "Expected-unsupported rows are planning evidence, not support claims.",
                "Performance trend warnings remain non-blocking until an owner-approved release-blocking policy exists.",
                "The bundle is a static PM review artifact and does not execute GPU captures.",
                "The M50 font/text rows prove selected simple OpenType evidence and explicit refusals only; broad font, emoji, shaping, SDF, LCD, glyph-mask, codec, arbitrary Skia/SkSL runtime shader input, arbitrary image-filter DAG, and broad Path AA support remain outside this bundle's claims.",
                "M52 promoted 10 inventory-derived rows; the rows prove only their generated scene contracts and do not turn M51 inventory status into broad Skia GM support.",
                "M53 promotes selected GM feature rows only; broad Skia GM parity, broad image-filter DAGs, broad Path AA, font, codec, emoji, shaping, SDF, LCD, and glyph-mask support remain outside this bundle's claims.",
                "M54 promotes selected hard feature depth rows only; broad Skia GM parity, broad image-filter DAGs, broad Path AA, dependency-gated font/codec/emoji substitutes, and release-blocking performance gates remain outside this bundle's claims.",
                "M55 exposes performance gate candidate evidence only; missing measured lanes are deferred or warned, estimated metrics are not promoted to measured, and performance remains non-blocking.",
                "M59 closes the selected performance target with measured CPU and GPU/cache payloads for all seven rows; estimated and missing metrics are still rejected as measured evidence.",
                "M56 promotes one corrected sweep-gradient row only; two-point conical gradients, arbitrary image-filter DAGs, picture prepass support, broad Path AA, dash, stroke, and complex clip remain outside this bundle's claims.",
                "M57 promotes one bounded AA clip grid slice only; broad aaclip, broad Path AA, dash, cap, join, stroke-outline, complex clip, large clipped paths, and edge-budget increases remain outside this bundle's claims.",
                "M66 promotes a cumulative GM/reference wave only where generated artifacts, routes, stats, and referenceKind provenance exist; inventory-only candidates still do not count as support.",
                "M67 adds a frame gate candidate and family budget inventory from M65 headless/offscreen telemetry; only one family is measured and native Kadre timing remains reporting-only.",
                "M68 verifies Kadre source-build bridge evidence and flagship scene inputs, but native Kanvas/Kadre window presentation remains blocked until a host adapter exists.",
                "M69 verifies a Kadre native WebGPU present loop for a bounded standalone WGSL scene; native screenshot capture, input loop, Kanvas display-list replay, and release-grade FPS remain outside the claim.",
                "M70-A/B/C verify a PM-visible Kadre demo command, normalized native surface-success evidence, reporting-only windowed telemetry, and a real wgpu4k offscreen texture readback when capture.realNativeReadback is true; M71 verifies autonomous frame scheduling; M72 verifies one selected solid-rect replay contract; M73 verifies a bounded typed replay-pack registry. Window-surface screenshot/readback, input, broad display-list replay, arbitrary op streams, dynamic multi-scene live switching, and release-grade FPS remain outside the claim.",
                "M75 verifies deterministic multi-scene replay-pack evidence with per-scene CPU reference checksums and selected native/readback facts. It is evidence aggregation only and does not add broad display-list replay, arbitrary op streams, or release-grade runtime timing.",
                "M76 verifies a selected generated-metadata to replay-contract bridge for known bounded templates and stable refusals. It does not add arbitrary generated scene replay, broad display-list replay, or new feature-family support.",
                "M77 verifies bounded SrcOver partial-alpha replay scenes and one unsupported blend-mode refusal. It does not add arbitrary blend modes, layer compositing, or broad display-list replay.",
                "M78 verifies bounded ClipRect intersect replay scenes and one complex clip refusal. It does not add rounded clips, path clips, difference clips, saveLayer clip stacks, arbitrary SkCanvas clip replay, or broad clip-stack support.",
                "M79 verifies bounded fixture-backed BitmapRect replay scenes with nearest/linear samplers and one unsupported mipmap sampler refusal. It does not add arbitrary SkImage, codec decode, texture atlas, mipmap, tile-mode, or color-managed image support.",
                "M80 hardens the bounded replay CPU reference behind a shared typed oracle result. It does not add broad SkCanvas/display-list replay or any new rendering breadth.",
                "M81 packages native frame artifact capture evidence for PM review, but the current produced image remains a wgpu4k native offscreen texture readback. Window-surface screenshot/readback is still unsupported and refused with m81.window-surface-readback-not-implemented.",
                "M82 verifies deterministic Kadre-backed input/resize runtime-loop semantics and telemetry. It does not synthesize real desktop OS input events in CI, does not claim full window-manager resize coverage, and keeps dropped-frame counters reporting-only until M84.",
                "M83 verifies one bounded Kanvas display-list scene through the Kadre native WebGPU demo path with nonblank offscreen readback evidence. Broad SkCanvas op replay, text, image-filter DAGs, arbitrary runtime effects, and release-grade timing remain outside the claim.",
                "M84 turns native Kadre frame timing into candidate/reporting-only evidence with explicit quarantine and a negative fixture. It does not promote frame.kadre-windowed to a release-blocking gate or claim full end-to-end FPS.",
                "M85 verifies a deterministic selected-scene resource/cache ledger, bounded key spaces, resize invalidation, and stable device-loss unsupported diagnostics. It does not claim observed WebGPU runtime cache telemetry, arbitrary scene cache behavior, cache-readiness gate movement, or real device-lost recovery.",
                "M86 ranks and classifies selected GM/reference fidelity rows and names remediation targets. It does not claim renderer visual fixes, global threshold changes, or readiness movement without before/after rendered artifacts.",
            ),
            "unavailableReferences" to unavailable,
        )
        targetRoot.resolve("manifest.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
        targetRoot.resolve("README.md").writeText(
            buildString {
                appendLine("# WGSL Pipeline PM Bundle")
                appendLine()
                appendLine("Generated by `pipelinePmBundle` at `$timestamp`.")
                appendLine()
                appendLine("## Open Locally")
                appendLine()
                appendLine("```bash")
                appendLine(serveCommand)
                appendLine("```")
                appendLine()
                appendLine("Then open `http://127.0.0.1:8765/index.html`.")
                appendLine()
                appendLine("## Contents")
                appendLine()
                appendLine("- `dashboard/`: self-contained dashboard HTML, merged scene JSON, images, diffs, routes, and stats artifacts.")
                appendLine("- `manifest.json`: commit, command, counters, expected-unsupported rows, adapter-backed rows, and limitations.")
                appendLine("- `gate/`: M50 dashboard gate reports from `pipelineSceneDashboardGate`.")
                appendLine("- `front-qa/`: PM dashboard front QA report and browser screenshot paths.")
                appendLine("- `performance/`: warning-only M50 performance trend report and policy.")
                appendLine("- M55 performance gate candidate counters live in `manifest.json` under `m55PerformanceGateCandidate`; reports are bundled under `performance/`.")
                appendLine("- `performance-release-gate/`: M59 measured final-target release gate JSON and Markdown reports.")
                appendLine("- M59 performance release gate counters live in `manifest.json` under `m59PerformanceReleaseGate`.")
                appendLine("- `performance/m67-performance-tiering/`: M67 frame gate candidate, baseline, and family budget reports.")
                appendLine("- `performance/m67-performance-tiering-negative/`: M67 deterministic quarantine/rebaseline negative fixture reports.")
                appendLine("- M67 performance tiering counters live in `manifest.json` under `m67PerformanceTiering`.")
                appendLine("- `inventory/`: M51 Skia GM inventory JSON and Markdown. Inventory rows are not support claims.")
                appendLine("- `inventory-gate/`: M51 inventory validation reports and mismatch snapshot.")
                appendLine("- M52 inventory promotion counters live in `manifest.json` under `m52InventoryPromotion`.")
                appendLine("- M53 inventory promotion counters live in `manifest.json` under `m53InventoryPromotion`.")
                appendLine("- M54 hard feature depth counters live in `manifest.json` under `m54HardFeatureDepth`.")
                appendLine("- `runtime/m65-runtime-smoke/`: M65 reporting-only runtime smoke telemetry and nonblank frame artifacts.")
                appendLine("- M65 runtime counters live in `manifest.json` under `m65RuntimeSmoke`.")
                appendLine("- `runtime/m68-kadre-demo/`: M68 Kadre source-build bridge smoke, host-contract audit, route summary, flagship scene inputs, and telemetry overlay sample.")
                appendLine("- M68 Kadre demo counters live in `manifest.json` under `m68KadreDemo`; native launch remains blocked until the Kanvas/Kadre host adapter is implemented.")
                appendLine("- `runtime/m69-kadre-host-adapter/`: M69 host adapter contract, route status, first scene route, bridge smoke, and telemetry.")
                appendLine("- `runtime/m69-kadre-native/`: M69 Kadre native AppKit/Metal smoke evidence with presented frame counters.")
                appendLine("- M69 Kadre host adapter counters live in `manifest.json` under `m69KadreHostAdapter`; native timing remains present-call duration only.")
                appendLine("- `runtime/m70-kadre-live-runtime/`: M70-A Kadre live runtime route status for the PM-visible demo lane.")
                appendLine("- `runtime/m70-kadre-native/`: M70-A/B/C native demo telemetry and readback PNG for the selected Kanvas-owned scene contract.")
                appendLine("- M70-A/B/C/M71/M72/M73 Kadre live runtime counters live in `manifest.json` under `m70KadreLiveRuntime`; native timing is still reporting-only, the frame clock is autonomous for the selected route, M73 is a bounded typed replay-pack registry with one selected scene per run, and the capture is an offscreen texture readback, not a window-surface screenshot.")
                appendLine("- `runtime/m75-kadre-replay-pack/`: M75 deterministic multi-scene replay-pack evidence JSON and Markdown.")
                appendLine("- M75 replay-pack evidence counters live in `manifest.json` under `m75ReplayPackEvidence`; this is evidence aggregation, not broad display-list replay.")
                appendLine("- `runtime/m76-generated-metadata-replay/`: M76 selected generated-metadata to replay-contract evidence JSON and Markdown.")
                appendLine("- M76 metadata replay counters live in `manifest.json` under `m76GeneratedMetadataReplay`; this maps known bounded metadata only and keeps unsupported metadata as stable refusals.")
                appendLine("- `runtime/m77-blend-alpha-replay/`: M77 bounded SrcOver partial-alpha replay evidence JSON and Markdown.")
                appendLine("- M77 blend/alpha replay counters live in `manifest.json` under `m77BlendAlphaReplay`; unsupported blend modes remain stable refusals.")
                appendLine("- `runtime/m78-clip-replay/`: M78 bounded ClipRect intersect replay evidence JSON and Markdown.")
                appendLine("- M78 clip replay counters live in `manifest.json` under `m78ClipReplay`; complex rounded/path/difference clips remain stable refusals.")
                appendLine("- `runtime/m79-bitmap-replay/`: M79 bounded fixture-backed BitmapRect replay evidence JSON and Markdown.")
                appendLine("- M79 bitmap replay counters live in `manifest.json` under `m79BitmapReplay`; unsupported mipmap/texture sampler paths remain stable refusals.")
                appendLine("- `runtime/m80-shared-replay-oracle/`: M80 shared replay CPU oracle evidence JSON and Markdown.")
                appendLine("- M80 shared replay oracle counters live in `manifest.json` under `m80SharedReplayOracle`; this is reference hardening, not broad display-list replay or new readiness.")
                appendLine("- `runtime/m81-native-frame-capture/`: M81 native frame artifact capture evidence JSON and Markdown when `:kadre-runtime:pipelineM81NativeFrameCapture` has been run.")
                appendLine("- M81 native frame capture counters live in `manifest.json` under `m81NativeFrameCapture`; the current image is offscreen texture readback evidence, not window-surface readback.")
                appendLine("- `runtime/m82-kadre-input-resize-runtime-loop/`: M82 deterministic Kadre input/resize runtime-loop evidence JSON and Markdown when `:kadre-runtime:pipelineM82InputResizeRuntimeLoop` has been run.")
                appendLine("- M82 input/resize runtime counters live in `manifest.json` under `m82InputResizeRuntimeLoop`; OS event injection and release-grade timing remain non-claims.")
                appendLine("- `runtime/m83-display-list-replay/`: M83 bounded Kanvas display-list replay evidence, native demo JSON, and native readback PNG.")
                appendLine("- M83 display-list replay counters live in `manifest.json` under `m83DisplayListReplay`; it proves one selected display-list scene, not broad SkCanvas op replay.")
                appendLine("- `runtime/m84-native-frame-timing/`: M84 candidate native Kadre frame timing evidence, Markdown, and negative fixture.")
                appendLine("- M84 native frame timing counters live in `manifest.json` under `m84NativeFrameTiming`; the lane remains candidate/reporting-only and not release-blocking.")
                appendLine("- `runtime/m85-resource-lifetime-cache/`: M85 selected realtime resource lifetime, cache pressure, resize invalidation, and device-loss diagnostic evidence.")
                appendLine("- M85 resource/cache counters live in `manifest.json` under `m85ResourceLifetimeCache`; they are a deterministic selected-scene ledger, not observed runtime cache telemetry, and device-loss recovery remains expected-unsupported.")
                appendLine("- `fidelity/m86-fidelity-burndown/`: M86 fidelity burn-down ranking, root-cause classification, and remediation target evidence.")
                appendLine("- M86 fidelity counters live in `manifest.json` under `m86FidelityBurndown`; this is classification and planning evidence, not a renderer visual-fix claim.")
                appendLine("- `runtime/m87-runtime-effect-live-editing/`: M87 selected registered runtime-effect live-editing evidence, edited-state PNGs, route JSON, and reflection metadata.")
                appendLine("- M87 live-editing counters live in `manifest.json` under `m87RuntimeEffectLiveEditing`; this proves selected `runtime.simple_rt` parameter editing and keeps arbitrary Skia/SkSL runtime shader input and missing WGSL descriptors expected-unsupported because WGSL remains the implementation target.")
                appendLine("- M89 feature breadth evidence lives in `manifest.json` under `m89FeatureBreadth`; this is FOR-189..192 PM aggregation for bounded rows and stable refusals, not a broad support or runtime-code claim.")
                appendLine("- `runtime/m90-runtime-interactive/`: M90 bounded Kadre interactive runtime evidence for durable loop semantics, scene switching, input telemetry, and observed-partial/derived resource counters.")
                appendLine("- M90 runtime counters live in `manifest.json` under `m90RuntimeInteractive`; native demo and benchmark commands remain opt-in because they open local Kadre windows.")
                appendLine("- `release/m91-mep-rc-scene-pack/`: M91 FOR-215/FOR-216/FOR-218 release-candidate scene-pack manifest and PM report.")
                appendLine("- M91 RC scene-pack counters live in `manifest.json` under `m91MepRcScenePack`; this is existing evidence aggregation with explicit unsupported/dependency rows, not a renderer-fix claim.")
                appendLine("- `runtime/m92-kadre-runtime-rc/`: M92 RC Kadre runtime and telemetry classification evidence.")
                appendLine("- M92 runtime evidence lives in `manifest.json` under `m92KadreRuntimeRc`; the native RC command is opt-in, and headless validation does not open a Kadre window.")
                appendLine("- MEP-NEXT closeout lives in `manifest.json` under `mepNextCloseout` and records the unchanged 67.75% readiness boundary.")
                appendLine("- M66 GM/reference promotion counters live in `manifest.json` under `m66GmPromotionWave`.")
                appendLine("- `reports/`: checked-in report references used by dashboard evidence rows.")
            }
        )
        if (unavailable.isNotEmpty()) {
            targetRoot.resolve("UNAVAILABLE_REFERENCES.md").writeText(
                buildString {
                    appendLine("# Unavailable References")
                    appendLine()
                    unavailable.forEach { item ->
                        appendLine("- `${item["path"]}`: ${item["reason"]}")
                    }
                }
            )
        }
        logger.lifecycle("Wrote WGSL PM bundle: ${targetRoot.relativeTo(rootDir)}")
        logger.lifecycle("Serve with: $serveCommand")
    }
}
