package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json
import org.graphiks.kanvas.gpu.renderer.telemetry.FrameGatePolicy
import org.graphiks.kanvas.gpu.renderer.telemetry.FrameGateStatus
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

/** One benchmarked draw family and the representative scene that exercises it. */
data class BenchmarkFamily(val family: String, val sceneId: String)

/** Outcome of benchmarking one draw family. */
enum class BenchmarkFamilyStatus(val wireName: String) {
    /** Frame times were measured against the real wired pipeline. */
    Sampled("sampled"),
    /** No WebGPU adapter was available; the family was skipped. */
    GpuUnavailable("gpu-unavailable"),
    /** The scene command sequence is not renderable by the offscreen renderer. */
    Unsupported("unsupported"),
    /** Rendering failed before frame times could be measured. */
    RenderFailed("render-failed"),
}

/** Frame-time statistics in milliseconds for one family's measured frames. */
data class FrameTimeStatistics(
    val minMs: Double,
    val medianMs: Double,
    val maxMs: Double,
    val meanMs: Double,
) {
    /** Frames per second derived from the mean frame time. */
    val fps: Double get() = if (meanMs > 0.0) 1000.0 / meanMs else 0.0

    companion object {
        /** Computes statistics from per-frame durations in nanoseconds. */
        fun of(samplesNanos: List<Long>): FrameTimeStatistics {
            require(samplesNanos.isNotEmpty()) { "frame time statistics require at least one sample" }
            val sortedMs = samplesNanos.map { it / 1_000_000.0 }.sorted()
            val count = sortedMs.size
            val median = if (count % 2 == 1) {
                sortedMs[count / 2]
            } else {
                (sortedMs[count / 2 - 1] + sortedMs[count / 2]) / 2.0
            }
            return FrameTimeStatistics(
                minMs = sortedMs.first(),
                medianMs = median,
                maxMs = sortedMs.last(),
                meanMs = sortedMs.average(),
            )
        }
    }
}

/** Benchmark result for one draw family. */
data class FamilyBenchmarkResult(
    val family: String,
    val sceneId: String,
    val status: BenchmarkFamilyStatus,
    val warmupFrames: Int,
    val measuredFrames: Int,
    val statistics: FrameTimeStatistics?,
    val diagnostics: List<String>,
) {
    init {
        require(family.isNotBlank()) { "benchmark family must not be blank" }
        require(sceneId.isNotBlank()) { "benchmark sceneId must not be blank" }
        require(diagnostics.isNotEmpty()) { "benchmark diagnostics must not be empty" }
        if (status == BenchmarkFamilyStatus.Sampled) {
            require(statistics != null) { "sampled benchmark result must include statistics" }
        } else {
            require(statistics == null) { "${status.wireName} benchmark result must not include statistics" }
        }
    }

    /** JSON object for the per-family benchmark report. */
    fun toJson(): String = buildString {
        append("{")
        append("\"family\": ${family.json()}, ")
        append("\"sceneId\": ${sceneId.json()}, ")
        append("\"status\": ${status.wireName.json()}, ")
        append("\"warmupFrames\": $warmupFrames, ")
        append("\"measuredFrames\": $measuredFrames, ")
        append("\"fps\": ${statistics?.fps.fmtOrNull()}, ")
        append("\"minMs\": ${statistics?.minMs.fmtOrNull()}, ")
        append("\"medianMs\": ${statistics?.medianMs.fmtOrNull()}, ")
        append("\"maxMs\": ${statistics?.maxMs.fmtOrNull()}, ")
        append("\"meanMs\": ${statistics?.meanMs.fmtOrNull()}, ")
        append("\"diagnostics\": [${diagnostics.joinToString(",") { it.json() }}]")
        append("}")
    }
}

/**
 * Per-family benchmark report.
 *
 * `productActivation` is true because the selected prepared routes are active.
 * The report still measures only the wired pipelines and flips no renderer route.
 */
data class PerFamilyBenchmarkReport(
    val backend: String,
    val adapterInfo: String?,
    val hardwareBaseline: String,
    val warmupFrames: Int,
    val measuredFrames: Int,
    val results: List<FamilyBenchmarkResult>,
    val productActivation: Boolean = true,
) {
    /** Mean frame-time (ms) measurements for sampled families, for the frame gate. */
    fun sampledMeasurements(): List<Pair<String, Double>> =
        results
            .filter { it.status == BenchmarkFamilyStatus.Sampled && it.statistics != null }
            .map { it.family to it.statistics!!.meanMs }

    /** Canonical dump lines for PM evidence and tests. */
    fun dumpLines(): List<String> =
        listOf(
            "per-family-benchmark backend=$backend adapter=${adapterInfo ?: "none"} " +
                "baseline=$hardwareBaseline warmupFrames=$warmupFrames measuredFrames=$measuredFrames " +
                "sampled=${results.count { it.status == BenchmarkFamilyStatus.Sampled }}/${results.size}",
        ) + results.flatMap { it.diagnostics } + listOf(
            "nonclaim:per-family-benchmark no-product-activation apple-m-series-baseline-only " +
                "no-cross-platform-claim",
        )

    /** JSON report written to `per-family-benchmark.json`. */
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"backend\": ${backend.json()},")
        appendLine("  \"adapterInfo\": ${adapterInfo?.json() ?: "null"},")
        appendLine("  \"hardwareBaseline\": ${hardwareBaseline.json()},")
        appendLine("  \"warmupFrames\": $warmupFrames,")
        appendLine("  \"measuredFrames\": $measuredFrames,")
        appendLine("  \"productActivation\": $productActivation,")
        appendLine("  \"families\": [")
        appendLine(results.joinToString(",\n") { "    ${it.toJson()}" })
        appendLine("  ]")
        appendLine("}")
    }

    /** Writes `per-family-benchmark.json` and a diagnostics transcript to [outputDir]. */
    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("per-family-benchmark.json").writeText(toJson())
        outputDir.resolve("per-family-benchmark-diagnostics.txt")
            .writeText(dumpLines().joinToString(separator = "\n", postfix = "\n"))
    }
}

/**
 * KGPU-M27-001: measures FPS/frame-time for each wired draw family by rendering a
 * representative scene through the WebGPU offscreen renderer for warmup + measured
 * frames. The benchmark requires a GPU adapter; when none is available it skips
 * every family with a stable diagnostic and promotes no performance claim.
 */
class PerFamilyBenchmark(
    private val sessionFactory: () -> GPUBackendSession? = GPUBackendRuntimeFactory::createOrNull,
) {
    /** Runs the benchmark, writes the report to [outputDir], and returns it. */
    fun run(
        outputDir: Path,
        warmupFrames: Int = DEFAULT_WARMUP_FRAMES,
        measuredFrames: Int = DEFAULT_MEASURED_FRAMES,
    ): PerFamilyBenchmarkReport {
        require(warmupFrames >= 0) { "warmupFrames must not be negative" }
        require(measuredFrames > 0) { "measuredFrames must be positive" }

        val session = sessionFactory()
        val report = if (session == null) {
            PerFamilyBenchmarkReport(
                backend = BACKEND,
                adapterInfo = null,
                hardwareBaseline = HARDWARE_BASELINE,
                warmupFrames = warmupFrames,
                measuredFrames = measuredFrames,
                results = families.map { family ->
                    skippedResult(
                        family = family,
                        status = BenchmarkFamilyStatus.GpuUnavailable,
                        warmupFrames = warmupFrames,
                        measuredFrames = measuredFrames,
                        reason = "webgpu-context-unavailable: no GPU adapter; " +
                            "${family.family} benchmark skipped (adapter-gated, no performance claim)",
                    )
                },
            )
        } else {
            session.use { activeSession ->
                val gate = FrameGatePolicy()
                PerFamilyBenchmarkReport(
                    backend = BACKEND,
                    adapterInfo = activeSession.adapterInfo?.summary,
                    hardwareBaseline = HARDWARE_BASELINE,
                    warmupFrames = warmupFrames,
                    measuredFrames = measuredFrames,
                    results = families.map { family ->
                        benchmarkFamily(activeSession, gate, family, warmupFrames, measuredFrames)
                    },
                )
            }
        }
        report.writeTo(outputDir)
        return report
    }

    private fun benchmarkFamily(
        session: GPUBackendSession,
        gate: FrameGatePolicy,
        family: BenchmarkFamily,
        warmupFrames: Int,
        measuredFrames: Int,
    ): FamilyBenchmarkResult =
        runCatching {
            val scene = GPURendererSceneRegistry.registry.requireScene(family.sceneId)
            if (scene.usesPreparedSolidRectPilot()) {
                return@runCatching benchmarkPreparedSolidRect(
                    session,
                    gate,
                    family,
                    scene,
                    warmupFrames,
                    measuredFrames,
                )
            }
            if (scene.usesPreparedStrokeRectPilot()) {
                return@runCatching benchmarkPreparedStrokeRect(
                    session,
                    gate,
                    family,
                    scene,
                    warmupFrames,
                    measuredFrames,
                )
            }
            if (scene.usesPreparedRegisteredUniformRectPilot()) {
                return@runCatching benchmarkPreparedRegisteredUniform(
                    session,
                    gate,
                    family,
                    scene,
                    warmupFrames,
                    measuredFrames,
                )
            }
            if (scene.usesPreparedSeparableBlurRectPilot()) {
                return@runCatching benchmarkPreparedSeparableBlur(
                    session,
                    gate,
                    family,
                    scene,
                    warmupFrames,
                    measuredFrames,
                )
            }
            val unsupported = rectOnlyCommandSequenceUnsupportedReason(scene.commands)
            if (unsupported != null) {
                return@runCatching skippedResult(
                    family = family,
                    status = BenchmarkFamilyStatus.Unsupported,
                    warmupFrames = warmupFrames,
                    measuredFrames = measuredFrames,
                    reason = "unsupported: ${family.sceneId} $unsupported",
                )
            }
            val width = scene.dimensions.width
            val height = scene.dimensions.height
            val drawPlan = prepareRectOnlyDrawPlan(
                sceneId = family.sceneId,
                commands = scene.commands,
                width = width,
                height = height,
            )
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = width, height = height, colorFormat = COLOR_FORMAT),
            ).use { target ->
                val renderer = RectOnlyOffscreenRenderer()
                repeat(warmupFrames) { renderer.renderToPixels(target, drawPlan) }
                val samples = ArrayList<Long>(measuredFrames)
                repeat(measuredFrames) {
                    val frameStart = System.nanoTime()
                    renderer.renderToPixels(target, drawPlan)
                    samples += (System.nanoTime() - frameStart).coerceAtLeast(1L)
                }
                val statistics = FrameTimeStatistics.of(samples)
                val gateResult = gate.evaluate(family.family, statistics.meanMs)
                val diagnostics = buildList {
                    add("sampled ${family.family} scene=${family.sceneId} via WebGPU offscreen render+readback")
                    add(
                        "fps=${statistics.fps.fmt()} meanMs=${statistics.meanMs.fmt()} " +
                            "minMs=${statistics.minMs.fmt()} medianMs=${statistics.medianMs.fmt()} " +
                            "maxMs=${statistics.maxMs.fmt()}",
                    )
                    add("frameGateStatus=${gateResult.status.wireName}")
                    if (gateResult.status != FrameGateStatus.Pass) {
                        add(
                            "BUDGET MISS: ${family.family} ${gateResult.status.wireName} " +
                                "fps=${statistics.fps.fmt()} exceeds ${gate.targetFps}fps target",
                        )
                    }
                }
                FamilyBenchmarkResult(
                    family = family.family,
                    sceneId = family.sceneId,
                    status = BenchmarkFamilyStatus.Sampled,
                    warmupFrames = warmupFrames,
                    measuredFrames = measuredFrames,
                    statistics = statistics,
                    diagnostics = diagnostics,
                )
            }
        }.getOrElse { error ->
            skippedResult(
                family = family,
                status = BenchmarkFamilyStatus.RenderFailed,
                warmupFrames = warmupFrames,
                measuredFrames = measuredFrames,
                reason = "render-failed: ${family.sceneId} ${error.message ?: error::class.simpleName}",
            )
        }

    private fun benchmarkPreparedSolidRect(
        session: GPUBackendSession,
        gate: FrameGatePolicy,
        family: BenchmarkFamily,
        scene: org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene<
            org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand,
        >,
        warmupFrames: Int,
        measuredFrames: Int,
    ): FamilyBenchmarkResult {
        val capabilities = session.capabilities ?: return skippedResult(
            family,
            BenchmarkFamilyStatus.Unsupported,
            warmupFrames,
            measuredFrames,
            "unsupported: prepared SolidRect benchmark requires observed capabilities",
        )
        val generation = capabilities.snapshotId.substringAfterLast('-').toLongOrNull()
            ?.let(::GPUDeviceGenerationID)
            ?: return skippedResult(
                family,
                BenchmarkFamilyStatus.Unsupported,
                warmupFrames,
                measuredFrames,
                "unsupported: prepared SolidRect benchmark requires device generation",
            )
        return session.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(scene.dimensions.width, scene.dimensions.height, COLOR_FORMAT),
        ).use { preparedSession ->
            fun render(frameOrdinal: Long, withReadback: Boolean): Long {
                val recorded = when (
                    val result = PreparedSolidRectSceneFrameRecorder().record(
                        scene,
                        capabilities,
                        generation,
                        frameOrdinal,
                        withReadback,
                    )
                ) {
                    is PreparedSolidRectSceneFrameResult.Recorded -> result
                    is PreparedSolidRectSceneFrameResult.Refused -> error(result.reason)
                }
                val output = recorded.readbackRequestId?.let(GPUSceneFrameOutputRequest::ReadbackRgba)
                    ?: GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly
                val start = System.nanoTime()
                val terminal = preparedSession.renderFrame(recorded.taskList, output)
                    .completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                val duration = (System.nanoTime() - start).coerceAtLeast(1L)
                check(terminal.outcome == GPUFrameStructuralOutcome.Succeeded) {
                    terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                        ?: "prepared SolidRect benchmark frame failed"
                }
                return duration
            }

            repeat(warmupFrames) { index -> render(index + 1L, withReadback = false) }
            val samples = List(measuredFrames) { index ->
                render(warmupFrames + index + 1L, withReadback = false)
            }
            // Validate the last state once, outside every warmup/measured interval.
            render(warmupFrames + measuredFrames + 1L, withReadback = true)
            val statistics = FrameTimeStatistics.of(samples)
            val gateResult = gate.evaluate(family.family, statistics.meanMs)
            val counters = preparedSession.nativeCounters()
            FamilyBenchmarkResult(
                family = family.family,
                sceneId = family.sceneId,
                status = BenchmarkFamilyStatus.Sampled,
                warmupFrames = warmupFrames,
                measuredFrames = measuredFrames,
                statistics = statistics,
                diagnostics = listOf(
                    "sampled ${family.family} scene=${family.sceneId} via prepared submit+completion",
                    "metricSource=wall-clock-prepared-submit-completion measuredReadbacks=0 " +
                        "finalValidationReadbacks=${counters.readbackCopies}",
                    "nativeFrames=${warmupFrames + measuredFrames + 1} encoders=${counters.encoders} " +
                        "commandBuffers=${counters.commandBuffers} submits=${counters.submits}",
                    "solidRectCache creations=${counters.solidRectInvariantCreations} " +
                        "reuses=${counters.solidRectInvariantReuses}",
                    "fps=${statistics.fps.fmt()} meanMs=${statistics.meanMs.fmt()} " +
                        "minMs=${statistics.minMs.fmt()} medianMs=${statistics.medianMs.fmt()} " +
                        "maxMs=${statistics.maxMs.fmt()}",
                    "frameGateStatus=${gateResult.status.wireName}",
                ),
            )
        }
    }

    private fun benchmarkPreparedRegisteredUniform(
        session: GPUBackendSession,
        gate: FrameGatePolicy,
        family: BenchmarkFamily,
        scene: org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene<
            org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand,
        >,
        warmupFrames: Int,
        measuredFrames: Int,
    ): FamilyBenchmarkResult {
        val capabilities = session.capabilities ?: return skippedResult(
            family,
            BenchmarkFamilyStatus.Unsupported,
            warmupFrames,
            measuredFrames,
            "unsupported: prepared registered uniform benchmark requires observed capabilities",
        )
        val generation = capabilities.snapshotId.substringAfterLast('-').toLongOrNull()
            ?.let(::GPUDeviceGenerationID)
            ?: return skippedResult(
                family,
                BenchmarkFamilyStatus.Unsupported,
                warmupFrames,
                measuredFrames,
                "unsupported: prepared registered uniform benchmark requires device generation",
            )
        return session.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(scene.dimensions.width, scene.dimensions.height, COLOR_FORMAT),
        ).use { preparedSession ->
            fun render(frameOrdinal: Long, withReadback: Boolean): Long {
                val recorded = when (
                    val result = PreparedRegisteredUniformRectSceneFrameRecorder().record(
                        scene,
                        capabilities,
                        generation,
                        frameOrdinal,
                        withReadback,
                    )
                ) {
                    is PreparedRegisteredUniformRectSceneFrameResult.Recorded -> result
                    is PreparedRegisteredUniformRectSceneFrameResult.Refused -> error(result.reason)
                }
                val output = recorded.readbackRequestId?.let(GPUSceneFrameOutputRequest::ReadbackRgba)
                    ?: GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly
                val start = System.nanoTime()
                val terminal = preparedSession.renderFrame(recorded.taskList, output)
                    .completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                val duration = (System.nanoTime() - start).coerceAtLeast(1L)
                check(terminal.outcome == GPUFrameStructuralOutcome.Succeeded) {
                    terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                        ?: "prepared registered uniform benchmark frame failed"
                }
                return duration
            }

            repeat(warmupFrames) { index -> render(index + 1L, withReadback = false) }
            val samples = List(measuredFrames) { index ->
                render(warmupFrames + index + 1L, withReadback = false)
            }
            render(warmupFrames + measuredFrames + 1L, withReadback = true)
            val statistics = FrameTimeStatistics.of(samples)
            val gateResult = gate.evaluate(family.family, statistics.meanMs)
            val counters = preparedSession.nativeCounters()
            FamilyBenchmarkResult(
                family = family.family,
                sceneId = family.sceneId,
                status = BenchmarkFamilyStatus.Sampled,
                warmupFrames = warmupFrames,
                measuredFrames = measuredFrames,
                statistics = statistics,
                diagnostics = listOf(
                    "sampled ${family.family} scene=${family.sceneId} via prepared submit+completion",
                    "metricSource=wall-clock-prepared-submit-completion measuredReadbacks=0 " +
                        "finalValidationReadbacks=${counters.readbackCopies}",
                    "nativeFrames=${warmupFrames + measuredFrames + 1} encoders=${counters.encoders} " +
                        "commandBuffers=${counters.commandBuffers} submits=${counters.submits}",
                    "registeredUniformCache creations=${counters.registeredUniformInvariantCreations} " +
                        "reuses=${counters.registeredUniformInvariantReuses}",
                    "fps=${statistics.fps.fmt()} meanMs=${statistics.meanMs.fmt()} " +
                        "minMs=${statistics.minMs.fmt()} medianMs=${statistics.medianMs.fmt()} " +
                        "maxMs=${statistics.maxMs.fmt()}",
                    "frameGateStatus=${gateResult.status.wireName}",
                ),
            )
        }
    }

    private fun benchmarkPreparedStrokeRect(
        session: GPUBackendSession,
        gate: FrameGatePolicy,
        family: BenchmarkFamily,
        scene: org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene<
            org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand,
        >,
        warmupFrames: Int,
        measuredFrames: Int,
    ): FamilyBenchmarkResult {
        val capabilities = session.capabilities ?: return skippedResult(
            family,
            BenchmarkFamilyStatus.Unsupported,
            warmupFrames,
            measuredFrames,
            "unsupported: prepared stroke-rect benchmark requires observed capabilities",
        )
        val generation = capabilities.snapshotId.substringAfterLast('-').toLongOrNull()
            ?.let(::GPUDeviceGenerationID)
            ?: return skippedResult(
                family,
                BenchmarkFamilyStatus.Unsupported,
                warmupFrames,
                measuredFrames,
                "unsupported: prepared stroke-rect benchmark requires device generation",
            )
        return session.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(scene.dimensions.width, scene.dimensions.height, COLOR_FORMAT),
        ).use { preparedSession ->
            fun render(frameOrdinal: Long, withReadback: Boolean): Long {
                val recorded = when (
                    val result = PreparedStrokeRectSceneFrameRecorder().record(
                        scene,
                        capabilities,
                        generation,
                        frameOrdinal,
                        withReadback,
                    )
                ) {
                    is PreparedStrokeRectSceneFrameResult.Recorded -> result
                    is PreparedStrokeRectSceneFrameResult.Refused -> error(result.reason)
                }
                val output = recorded.readbackRequestId?.let(GPUSceneFrameOutputRequest::ReadbackRgba)
                    ?: GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly
                val start = System.nanoTime()
                val terminal = preparedSession.renderFrame(recorded.taskList, output)
                    .completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                val duration = (System.nanoTime() - start).coerceAtLeast(1L)
                check(terminal.outcome == GPUFrameStructuralOutcome.Succeeded) {
                    terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                        ?: "prepared stroke-rect benchmark frame failed"
                }
                return duration
            }

            repeat(warmupFrames) { index -> render(index + 1L, withReadback = false) }
            val samples = List(measuredFrames) { index ->
                render(warmupFrames + index + 1L, withReadback = false)
            }
            render(warmupFrames + measuredFrames + 1L, withReadback = true)
            val statistics = FrameTimeStatistics.of(samples)
            val gateResult = gate.evaluate(family.family, statistics.meanMs)
            val counters = preparedSession.nativeCounters()
            FamilyBenchmarkResult(
                family = family.family,
                sceneId = family.sceneId,
                status = BenchmarkFamilyStatus.Sampled,
                warmupFrames = warmupFrames,
                measuredFrames = measuredFrames,
                statistics = statistics,
                diagnostics = listOf(
                    "sampled ${family.family} scene=${family.sceneId} via prepared submit+completion",
                    "metricSource=wall-clock-prepared-submit-completion measuredReadbacks=0 " +
                        "finalValidationReadbacks=${counters.readbackCopies}",
                    "nativeFrames=${warmupFrames + measuredFrames + 1} encoders=${counters.encoders} " +
                        "commandBuffers=${counters.commandBuffers} submits=${counters.submits}",
                    "strokeGeometry=analytic-annular-rect.coverage bands=4 legacyStrokeWgsl=false",
                    "solidRectCache creations=${counters.solidRectInvariantCreations} " +
                        "reuses=${counters.solidRectInvariantReuses}",
                    "fps=${statistics.fps.fmt()} meanMs=${statistics.meanMs.fmt()} " +
                        "minMs=${statistics.minMs.fmt()} medianMs=${statistics.medianMs.fmt()} " +
                        "maxMs=${statistics.maxMs.fmt()}",
                    "frameGateStatus=${gateResult.status.wireName}",
                ),
            )
        }
    }

    private fun benchmarkPreparedSeparableBlur(
        session: GPUBackendSession,
        gate: FrameGatePolicy,
        family: BenchmarkFamily,
        scene: org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene<
            org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand,
        >,
        warmupFrames: Int,
        measuredFrames: Int,
    ): FamilyBenchmarkResult {
        val capabilities = session.capabilities ?: return skippedResult(
            family,
            BenchmarkFamilyStatus.Unsupported,
            warmupFrames,
            measuredFrames,
            "unsupported: prepared separable blur benchmark requires observed capabilities",
        )
        val generation = capabilities.snapshotId.substringAfterLast('-').toLongOrNull()
            ?.let(::GPUDeviceGenerationID)
            ?: return skippedResult(
                family,
                BenchmarkFamilyStatus.Unsupported,
                warmupFrames,
                measuredFrames,
                "unsupported: prepared separable blur benchmark requires device generation",
            )
        return session.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(scene.dimensions.width, scene.dimensions.height, COLOR_FORMAT),
        ).use { preparedSession ->
            fun render(frameOrdinal: Long, withReadback: Boolean): Long {
                val recorded = when (
                    val result = PreparedSeparableBlurRectSceneFrameRecorder().record(
                        scene,
                        capabilities,
                        generation,
                        frameOrdinal,
                        withReadback,
                    )
                ) {
                    is PreparedSeparableBlurRectSceneFrameResult.Recorded -> result
                    is PreparedSeparableBlurRectSceneFrameResult.Refused -> error(result.reason)
                }
                val output = recorded.readbackRequestId?.let(GPUSceneFrameOutputRequest::ReadbackRgba)
                    ?: GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly
                val start = System.nanoTime()
                val terminal = preparedSession.renderFrame(recorded.taskList, output)
                    .completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                val duration = (System.nanoTime() - start).coerceAtLeast(1L)
                check(terminal.outcome == GPUFrameStructuralOutcome.Succeeded) {
                    terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                        ?: "prepared separable blur benchmark frame failed"
                }
                return duration
            }

            repeat(warmupFrames) { index -> render(index + 1L, withReadback = false) }
            val samples = List(measuredFrames) { index ->
                render(warmupFrames + index + 1L, withReadback = false)
            }
            render(warmupFrames + measuredFrames + 1L, withReadback = true)
            val statistics = FrameTimeStatistics.of(samples)
            val gateResult = gate.evaluate(family.family, statistics.meanMs)
            val counters = preparedSession.nativeCounters()
            FamilyBenchmarkResult(
                family = family.family,
                sceneId = family.sceneId,
                status = BenchmarkFamilyStatus.Sampled,
                warmupFrames = warmupFrames,
                measuredFrames = measuredFrames,
                statistics = statistics,
                diagnostics = listOf(
                    "sampled ${family.family} scene=${family.sceneId} via prepared submit+completion",
                    "metricSource=wall-clock-prepared-submit-completion measuredReadbacks=0 " +
                        "finalValidationReadbacks=${counters.readbackCopies}",
                    "nativeFrames=${warmupFrames + measuredFrames + 1} encoders=${counters.encoders} " +
                        "commandBuffers=${counters.commandBuffers} submits=${counters.submits}",
                    "separableBlurCache invariants=${counters.separableBlurInvariantCreations}/" +
                        "${counters.separableBlurInvariantReuses} intermediates=" +
                        "${counters.separableBlurIntermediateCreations}/" +
                        "${counters.separableBlurIntermediateReuses}",
                    "fps=${statistics.fps.fmt()} meanMs=${statistics.meanMs.fmt()} " +
                        "minMs=${statistics.minMs.fmt()} medianMs=${statistics.medianMs.fmt()} " +
                        "maxMs=${statistics.maxMs.fmt()}",
                    "frameGateStatus=${gateResult.status.wireName}",
                ),
            )
        }
    }

    private fun skippedResult(
        family: BenchmarkFamily,
        status: BenchmarkFamilyStatus,
        warmupFrames: Int,
        measuredFrames: Int,
        reason: String,
    ): FamilyBenchmarkResult =
        FamilyBenchmarkResult(
            family = family.family,
            sceneId = family.sceneId,
            status = status,
            warmupFrames = warmupFrames,
            measuredFrames = measuredFrames,
            statistics = null,
            diagnostics = listOf(reason),
        )

    companion object {
        const val DEFAULT_WARMUP_FRAMES: Int = 10
        const val DEFAULT_MEASURED_FRAMES: Int = 90
        private const val BACKEND: String = "webgpu-offscreen"
        private const val COLOR_FORMAT: String = "rgba8unorm"
        private const val HARDWARE_BASELINE: String = "Apple M-series"

        /** The ten wired draw families and their representative benchmark scenes. */
        val families: List<BenchmarkFamily> = listOf(
            BenchmarkFamily("FillRect", "solid-card-stack"),
            BenchmarkFamily("LinearGradient", "linear-gradient-lanes"),
            BenchmarkFamily("RadialGradient", "radial-swatch"),
            BenchmarkFamily("SweepGradient", "sweep-disk"),
            BenchmarkFamily("PathFill", "path-fill-stencil"),
            BenchmarkFamily("BitmapRect", "bitmap-sampler-matrix"),
            BenchmarkFamily("Text", "glyph-atlas-strip"),
            BenchmarkFamily("Blur", "gaussian-blur-photo"),
            BenchmarkFamily("ColorMatrix", "color-matrix-filter"),
            BenchmarkFamily("Stroke", "stroke-rect-outline"),
        )
    }
}

private fun Double.fmt(): String = String.format(Locale.US, "%.4f", this)

private fun Double?.fmtOrNull(): String = this?.let { String.format(Locale.US, "%.4f", it) } ?: "null"
