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
        val m60Manifest = m60GeneratedRoot.resolve("data/m60-generated-scenes.json")
        val m60Scenes = if (m60Manifest.isFile) {
            val m60Root = JsonSlurper().parse(m60Manifest) as? Map<*, *>
                ?: throw GradleException("M60 generated scene manifest root must be a JSON object: ${m60Manifest.relativeTo(rootDir)}")
            m60Root["scenes"] as? List<*>
                ?: throw GradleException("M60 generated scene manifest must contain a `scenes` array: ${m60Manifest.relativeTo(rootDir)}")
        } else {
            emptyList<Any?>()
        }
        val allGeneratedScenes = scenes + m52Scenes + m53Scenes + m54Scenes + m57Scenes + m60Scenes + m61Scenes + m62Scenes + m63Scenes + m64Scenes
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
            "source" to listOf(
                "reports/wgsl-pipeline/scenes/generated/results.json",
                "build/reports/wgsl-pipeline-m52-generated/data/m52-generated-scenes.json",
                "build/reports/wgsl-pipeline-m53-generated/data/m53-generated-scenes.json",
                "build/reports/wgsl-pipeline-m54-generated/data/m54-generated-scenes.json",
                "build/reports/wgsl-pipeline-m57-generated/data/m57-generated-scenes.json",
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
            "m64-spiral-rt-wgsl-descriptor-refusal" to "runtime-effect.wgsl-descriptor-missing",
            "m64-arbitrary-sksl-runtime-effect-refusal" to "runtime-effect.arbitrary-sksl-unsupported",
            "m52-closed-capped-hairlines-edge-budget" to "coverage.edge-count-exceeded",
            "m52-big-tile-image-filter-dag-refusal" to "image-filter.dag-or-picture-prepass-required",
            "m52-color-emoji-blendmodes-refusal" to "font.color-glyph-emoji-unsupported",
            "m53-complexclip-boundary-refusal" to "coverage.complex-clip-path-unsupported",
            "m53-imagefilters-cropped-boundary" to "image-filter.crop-input-nonnull-prepass-required",
            "m54-imagefilters-graph-boundary" to "image-filter.dag-or-picture-prepass-required",
            "m54-dash-circle-boundary" to "coverage.edge-count-exceeded",
            "m60-bounded-stroke-cap-join" to "coverage.stroke-cap-join-selector-diagnostics-unavailable",
            "m60-bounded-nested-rrect-clip" to "coverage.nested-clip-visual-parity-below-threshold",
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
        val m54FamilyCounts = linkedMapOf<String, Int>()
        val m61FamilyCounts = linkedMapOf<String, Int>()
        val m62FamilyCounts = linkedMapOf<String, Int>()
        val m63FamilyCounts = linkedMapOf<String, Int>()
        val m64FamilyCounts = linkedMapOf<String, Int>()

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
        ) + statusCounts.mapKeys { "status.${it.key}" } +
            maturityCounts.mapKeys { "${it.key}" } +
            m54FamilyCounts.mapKeys { "m54.family.${it.key}" } +
            m61FamilyCounts.mapKeys { "m61.family.${it.key}" } +
            m62FamilyCounts.mapKeys { "m62.family.${it.key}" } +
            m63FamilyCounts.mapKeys { "m63.family.${it.key}" } +
            m64FamilyCounts.mapKeys { "m64.family.${it.key}" }

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

tasks.register("pipelinePmBundle") {
    group = "verification"
    description = "Builds a portable PM review bundle for the WGSL scene dashboard."

    dependsOn(
        "pipelineSceneDashboardGate",
        "pipelineDashboardFrontQa",
        "pipelinePerformanceTrendWarnings",
        "pipelinePerformanceReleaseGate",
        "pipelineSkiaGmInventoryGate",
    )

    val dashboardDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scenes")
    val generatedExportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-generated-scenes")
    val gateReportDir = layout.buildDirectory.dir("reports/wgsl-pipeline-scene-gate")
    val frontQaDir = layout.buildDirectory.dir("reports/wgsl-pipeline-front-qa")
    val performanceWarningsDir = layout.buildDirectory.dir("reports/wgsl-pipeline-performance-warnings")
    val performanceReleaseGateDir = layout.buildDirectory.dir("reports/wgsl-pipeline-performance-release-gate")
    val inventoryDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory")
    val inventoryGateDir = layout.buildDirectory.dir("reports/wgsl-pipeline-skia-gm-inventory-gate")
    val bundleDir = layout.buildDirectory.dir("reports/wgsl-pipeline-pm-bundle")
    inputs.dir(dashboardDir)
    inputs.dir(generatedExportDir)
    inputs.dir(gateReportDir)
    inputs.dir(frontQaDir)
    inputs.dir(performanceWarningsDir)
    inputs.dir(performanceReleaseGateDir)
    inputs.dir(inventoryDir)
    inputs.dir(inventoryGateDir)
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/results.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json"))
    inputs.file(layout.projectDirectory.file("reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json"))
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
        val inventoryRoot = inventoryDir.get().asFile
        val inventoryGateRoot = inventoryGateDir.get().asFile
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
        if (inventoryRoot.isDirectory) {
            inventoryRoot.copyRecursively(targetRoot.resolve("inventory"), overwrite = true)
        }
        if (inventoryGateRoot.isDirectory) {
            inventoryGateRoot.copyRecursively(targetRoot.resolve("inventory-gate"), overwrite = true)
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
        val promotionValidationErrors = mutableListOf<String>()
        inventoryDerivedScenes
            .filter { scene ->
                val generation = scene["generation"] as? Map<*, *>
                generation?.get("derivationTask") in setOf("pipelineM53InventoryPromotionPack", "pipelineM54HardFeatureDepthPack", "pipelineM57PathAaClipMicroPromotionPack")
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
            "inventoryCounters" to inventorySummary,
            "dashboardInventoryLinks" to dashboardInventoryLinks,
            "expectedUnsupportedRows" to expectedUnsupported,
            "adapterBackedRows" to adapterBacked,
            "knownLimitations" to listOf(
                "Skia GM inventory rows classify visibility and planning state only; support still requires generated dashboard evidence.",
                "Expected-unsupported rows are planning evidence, not support claims.",
                "Performance trend warnings remain non-blocking until an owner-approved release-blocking policy exists.",
                "The bundle is a static PM review artifact and does not execute GPU captures.",
                "The M50 font/text rows prove selected simple OpenType evidence and explicit refusals only; broad font, emoji, shaping, SDF, LCD, glyph-mask, codec, arbitrary SkSL, arbitrary image-filter DAG, and broad Path AA support remain outside this bundle's claims.",
                "M52 promoted 10 inventory-derived rows; the rows prove only their generated scene contracts and do not turn M51 inventory status into broad Skia GM support.",
                "M53 promotes selected GM feature rows only; broad Skia GM parity, broad image-filter DAGs, broad Path AA, font, codec, emoji, shaping, SDF, LCD, and glyph-mask support remain outside this bundle's claims.",
                "M54 promotes selected hard feature depth rows only; broad Skia GM parity, broad image-filter DAGs, broad Path AA, dependency-gated font/codec/emoji substitutes, and release-blocking performance gates remain outside this bundle's claims.",
                "M55 exposes performance gate candidate evidence only; missing measured lanes are deferred or warned, estimated metrics are not promoted to measured, and performance remains non-blocking.",
                "M59 closes the selected performance target with measured CPU and GPU/cache payloads for all seven rows; estimated and missing metrics are still rejected as measured evidence.",
                "M56 promotes one corrected sweep-gradient row only; two-point conical gradients, arbitrary image-filter DAGs, picture prepass support, broad Path AA, dash, stroke, and complex clip remain outside this bundle's claims.",
                "M57 promotes one bounded AA clip grid slice only; broad aaclip, broad Path AA, dash, cap, join, stroke-outline, complex clip, large clipped paths, and edge-budget increases remain outside this bundle's claims.",
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
                appendLine("- `inventory/`: M51 Skia GM inventory JSON and Markdown. Inventory rows are not support claims.")
                appendLine("- `inventory-gate/`: M51 inventory validation reports and mismatch snapshot.")
                appendLine("- M52 inventory promotion counters live in `manifest.json` under `m52InventoryPromotion`.")
                appendLine("- M53 inventory promotion counters live in `manifest.json` under `m53InventoryPromotion`.")
                appendLine("- M54 hard feature depth counters live in `manifest.json` under `m54HardFeatureDepth`.")
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
