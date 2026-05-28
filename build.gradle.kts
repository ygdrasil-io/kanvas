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
        |${row("Glyph mask ownership", status("org.skia.pipeline.GeometryCoverageContractsTest", "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest"), "`GlyphMaskLowering` defines the descriptor boundary for glyph-run mask handoff: text/glyph infrastructure owns discovery, rasterization, atlas lifetime, and invalidation; geometry only consumes an opaque alpha-mask ref or emits `coverage.glyph-mask-dependency-unavailable`; WebGPU currently refuses alpha-mask coverage with `coverage.alpha-mask-unsupported`")}
        |${row("Image rect lowering", status("org.skia.pipeline.GeometryCoverageContractsTest", "org.skia.core.SkBitmapDescriptorCoverageOracleTest", "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest"), "`ImageRectLowering` captures source rect, destination rect, transform facts, opaque paint-owned sampling payload handoff, and route id; axis-aligned image rects select analytic rect coverage, transformed descriptor tests select path-like coverage without moving sampling/pixels/filtering/colorspace into geometry; CPU oracle covers one axis-aligned image rect and WebGPU selector diagnostics record the adapter-gated image-rect route")}
        |${row("Runtime-effect status", status("org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistryTest", "org.skia.effects.runtime.SkRuntimeEffectDispatchTest", "org.skia.effects.runtime.SkRuntimeEffectMakeTest", "org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest"), "CPU registry/dispatch/Make tests plus WebGPU descriptor test; matrix counts $runtimeEffectSupportMatrixCounts")}
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
        |- Runtime-effect support matrix: `reports/wgsl-pipeline/2026-05-27-m23-runtime-effect-support-matrix.md`
        |  lists `runtime.simple_rt` as descriptor-backed and dispatch-only builtins as missing descriptor/WGSL evidence;
        |  current counts are $runtimeEffectSupportMatrixCounts.
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
    descriptionText = "Runs generated WGSL, PipelineKey, BlendPlan, runtime descriptor, and WebGPU selector conformance tests.",
    testPatterns = listOf(
        "org.skia.gpu.webgpu.tools.WgslValidationReportTest",
        "org.skia.gpu.webgpu.tools.WgslStrictValidationReportTest",
        "org.skia.gpu.webgpu.tools.GeneratedSolidRectWgslTest",
        "org.skia.gpu.webgpu.tools.GeneratedLinearGradientWgslTest",
        "org.skia.gpu.webgpu.PipelineKeyTelemetryTest",
        "org.skia.gpu.webgpu.BlendPlanTest",
        "org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest",
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

tasks.register("pipelineConformance") {
    group = "verification"
    description = "Runs the standard production pipeline conformance suite without slow benchmark gates."

    dependsOn(
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
            |- REQUIRED strict generated/registered WGSL validation: :gpu-raster:wgslValidateStrict
            |- REQUIRED legacy WGSL diagnostic inventory: :gpu-raster:wgslValidateAll
            |- REQUIRED generated WGSL, PipelineKey, BlendPlan, runtime descriptor, and selector tests: :gpu-raster:pipelineConformanceTest
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
        val runtimeEffectSupportMatrixCounts = file("reports/wgsl-pipeline/2026-05-27-m23-runtime-effect-support-matrix.md")
            .readLines()
            .firstOrNull { it.startsWith("Status counts: ") }
            ?.removePrefix("Status counts: ")
            ?.removeSuffix(".")
            ?: throw GradleException(
                "Missing runtime-effect support matrix status counts in `reports/wgsl-pipeline/2026-05-27-m23-runtime-effect-support-matrix.md`."
            )
        val report = renderPipelineConformanceReport(
            commit = commit,
            suites = suites,
            vectorDecisionReportPresent = vectorDecisionReportPresent,
            legacyWgslDiagnosticsAllowlistCount = legacyWgslDiagnosticsAllowlistCount,
            runtimeEffectSupportMatrixCounts = runtimeEffectSupportMatrixCounts,
        )
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(report)
        logger.lifecycle("Wrote pipeline conformance PM report: ${target.relativeTo(rootDir)}")
    }
}

tasks.register("pipelineGeneratedSceneExport") {
    group = "verification"
    description = "Materializes generated WGSL scene result artifacts into the dashboard export layout."

    val sourceDir = layout.projectDirectory.dir("reports/wgsl-pipeline/scenes")
    val manifestFile = sourceDir.file("generated/results.json")
    val outputDir = layout.buildDirectory.dir("reports/wgsl-pipeline-generated-scenes")
    inputs.file(manifestFile)
    inputs.dir(sourceDir.dir("generated/artifacts"))
    inputs.dir(sourceDir.dir("artifacts"))
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        val sourceRoot = sourceDir.asFile
        val generatedSourceRoot = sourceRoot.resolve("generated")
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
                generatedSourceRoot.resolve(normalized),
                sourceRoot.resolve(normalized),
            ).firstOrNull { it.isFile || (allowDirectory && it.isDirectory) }
        }

        val root = if (manifest.isFile) {
            JsonSlurper().parse(manifest) as? Map<*, *>
                ?: throw GradleException("Generated scene manifest root must be a JSON object: ${manifest.relativeTo(rootDir)}")
        } else {
            mapOf("schemaVersion" to 1, "scenes" to emptyList<Any>())
        }
        val scenes = root["scenes"] as? List<*>
            ?: throw GradleException("Generated scene manifest must contain a `scenes` array: ${manifest.relativeTo(rootDir)}")
        val normalizedScenes = mutableListOf<Any?>()

        scenes.forEachIndexed { index, rawScene ->
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
            normalizedScenes += rawScene
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
            "source" to "reports/wgsl-pipeline/scenes/generated/results.json",
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
                    "name" to "m43-cpu-measured-local",
                    "commit" to baselineCommit,
                ),
                "regression" to mapOf(
                    "label" to "unknown",
                ),
                "gate" to mapOf(
                    "mode" to "reporting-only",
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

        fun requireRoute(sceneId: String, owner: Map<*, *>, fieldPrefix: String) {
            val route = requireMap(sceneId, owner, "route") ?: return
            val hasSelectedRoute = route["selectedRoute"] is String && (route["selectedRoute"] as String).isNotBlank()
            val hasCoverageStrategy = route["coverageStrategy"] is String && (route["coverageStrategy"] as String).isNotBlank()
            if (!hasSelectedRoute && !hasCoverageStrategy) {
                validationErrors += "$sceneId: missing route selector in `$fieldPrefix.route`"
            }
            requireString(sceneId, route, "fallbackReason", "$fieldPrefix.route.fallbackReason")
        }

        fun validateGeneration(sceneId: String, rawScene: Map<*, *>, status: String?) {
            val generationValue = rawScene["generation"] ?: return
            val generation = generationValue as? Map<*, *>
            if (generation == null) {
                missingField(sceneId, "generation")
                return
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
            validateGeneration(sceneId, rawScene, status)

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
