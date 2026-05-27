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

fun gpuAdapterEvidenceForReport(suites: List<PipelineConformanceSuiteSummary>): GpuAdapterEvidence {
    val adapterSuites = suites.filter {
        it.className == "org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest" ||
            it.className == "org.skia.gpu.webgpu.PipelineKeyTelemetryTest"
    }
    val localStatus = conformanceStatus(adapterSuites)
    val ciLaneAvailable = false
    val status = when {
        adapterSuites.any { it.failed } -> "failed"
        !ciLaneAvailable -> "blocked-no-adapter-lane"
        adapterSuites.any { it.skipped > 0 } -> "skipped-known-risk"
        adapterSuites.all { it.passed } -> "passed"
        else -> "skipped-known-risk"
    }
    return GpuAdapterEvidence(
        status = status,
        localJUnitStatus = localStatus,
        ciLaneAvailable = ciLaneAvailable,
        blockerText = "Release blocker: GitHub Actions `GPU tests (macos)` is disabled with `if: false`, so PR CI cannot prove rect/rrect/path adapter fixtures or warm pipeline cache telemetry.",
        unblockCondition = "Unblock by enabling a non-skippable GPU adapter CI/scheduled lane that uploads `gpu-raster` reports/artifacts and fails when adapter-dependent tests skip, or by attaching an equivalent scheduled adapter artifact to the release.",
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
    val releaseReadinessStatus = if (gpuAdapterEvidence.status == "passed") "passed" else "blocked"
    val vectorStatus = if (vectorDecisionReportPresent) "rejected benchmark" else "not run"
    val vectorDecision = if (vectorDecisionReportPresent) {
        "`rejected benchmark` — see `reports/wgsl-pipeline/2026-05-27-m22-vector-promotion-decision.md`"
    } else {
        "`not run` — no vector decision report found"
    }

    return """
        |# M24 Pipeline Conformance PM Report
        |
        |Linear: GRA-53, GRA-56, GRA-57, GRA-58, GRA-59, GRA-60, GRA-61, GRA-62, GRA-63, GRA-64, GRA-69
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
            |- GPU adapter residual risk: adapter-dependent WebGPU tests may report JUnit SKIPPED when no adapter is available; a skip is recorded risk, not a green adapter pass.
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
