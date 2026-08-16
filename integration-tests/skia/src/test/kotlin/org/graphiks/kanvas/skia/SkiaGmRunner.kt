package org.graphiks.kanvas.skia

import org.graphiks.kanvas.diagnostic.DiagnosticRunner
import org.graphiks.kanvas.diagnostic.PipelineTrace
import org.graphiks.kanvas.diagnostic.RunnerInput
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.surface.DebugLevel
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.ReferenceManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.TestAbortedException
import java.io.File
import java.util.concurrent.TimeUnit

class SkiaGmRunner {
    @TempDir
    lateinit var tempDir: File

    companion object {
        init {
            RuntimeEffectWgsl4kWiring.install()
        }

        @JvmStatic
        fun allGms() = namedSkiaGmsForRunner(
            selectSkiaGmsForRunner(
                SkiaGmRegistry.all(),
                System.getProperty("kanvas.gm.name"),
                System.getProperty("kanvas.gm.from")?.toInt(),
                System.getProperty("kanvas.gm.to")?.toInt(),
            ),
        )

        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allGms")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `render GM`(gm: SkiaGm) {
        GpuAvailability.requireWebGpu()

        val includeBlocking = System.getProperty("kanvas.gm.includeBlocking")?.toBoolean() ?: false
        if (!includeBlocking && gm.renderCost == RenderCost.BLOCKING) {
            throw TestAbortedException(
                "GM '${gm.name}' is BLOCKING — use -Dkanvas.gm.includeBlocking=true"
            )
        }

        val t0 = System.nanoTime()
        val debugLevel = DebugLevel.valueOf(
            System.getProperty("kanvas.render.debugLevel") ?: "OFF"
        )
        val config = RenderConfig.DEFAULT.copy(debugLevel = debugLevel)

        val result = SkiaGmRenderer.render(gm, config = config)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000

        val refPath = referenceResourcePath(gm)
        val refStatus = gm.referenceStatus

        if (refStatus.untrustable) {
            throw TestAbortedException(
                "Reference PNG for GM '${gm.name}' is marked untrustable" +
                    refStatus.reason?.let { ": $it" }.orEmpty(),
            )
        }

        if (!ReferenceManager.hasReference(refPath)) {
            error(missingReferenceMessage(refPath))
        }

        val reference = ReferenceManager.loadReference(refPath)

        val comparison = ComparisonUtils.compareRgba(
            actual = result.rgba,
            reference = reference,
            width = result.width,
            height = result.height,
            tolerance = gm.tolerance,
            minSimilarity = gm.minSimilarity,
        )

        SimilarityTracker.updateScore(gm.name, comparison.similarity)

        val outputDir = File(tempDir, gm.name)
        outputDir.mkdirs()
        ComparisonUtils.saveRgbaAsPng(result.rgba, result.width, result.height, File(outputDir, "kanvas.png"))
        ComparisonUtils.saveRgbaAsPng(reference, result.width, result.height, File(outputDir, "reference.png"))
        comparison.diffRgba?.let { diff ->
            ComparisonUtils.saveRgbaAsPng(diff, result.width, result.height, File(outputDir, "diff.png"))
        }

        if (debugLevel >= DebugLevel.PIXEL) {
            val diagnosticDir = File(outputDir, "diagnostics")
            diagnosticDir.mkdirs()

            var pipelineTrace: PipelineTrace? = null
            if (debugLevel >= DebugLevel.TRACE && result.pipelineTracer != null) {
                pipelineTrace = result.pipelineTracer.buildTrace()
            }

            val manifest = DiagnosticRunner.run(RunnerInput(
                gmName = gm.name,
                minSimilarity = gm.minSimilarity,
                actualRgba = result.rgba,
                referenceRgba = reference,
                width = result.width,
                height = result.height,
                tolerance = gm.tolerance,
                ops = result.ops,
                dispatchedCount = result.dispatchedCount,
                refusedCount = result.refusedCount,
                diagnostics = result.diagnostics,
                debugLevel = debugLevel,
                outputDir = diagnosticDir,
            ))

            val finalManifest = if (pipelineTrace != null) {
                manifest.copy(pipelineTrace = pipelineTrace)
            } else manifest

            val manifestFile = File(outputDir, "manifest.json")
            manifestFile.writeText(finalManifest.toJson())
        }

        println(
            "[${if (comparison.isPassing) "PASS" else "FAIL"}] ${gm.name}: " +
            "similarity=${"%.2f".format(comparison.similarity)}% " +
            "(threshold: ${comparison.minSimilarity}%) " +
            "dispatch=${result.dispatchedCount} refuse=${result.refusedCount} " +
            "(${elapsedMs}ms)",
        )
        result.diagnostics.forEach { d -> println("  ${d}") }

        if (gm.requiresZeroRefusals) {
            assertTrue(result.refusedCount == 0) {
                "${gm.name}: expected no GPU refusals, got ${result.refusedCount}: ${result.diagnostics}"
            }
        }

        assertTrue(comparison.isPassing) {
            "${gm.name}: similarity=${"%.2f".format(comparison.similarity)}% " +
            "(threshold: ${comparison.minSimilarity}%)"
        }
    }
}

internal fun referenceResourcePath(gm: SkiaGm): String =
    "/reference/${gm.referenceName}.png"

internal fun missingReferenceMessage(refPath: String): String =
    "Reference PNG not found at $refPath. Run: cp <skia-native-reference> src/test/resources$refPath"

internal fun selectSkiaGmsForRunner(
    gms: List<SkiaGm>,
    name: String?,
    from: Int? = null,
    to: Int? = null,
): List<SkiaGm> {
    validateSkiaGmRange(from, to, gms.size)
    val start = from ?: 0
    val end = to ?: gms.size
    return gms.withIndex()
        .filter { (index, gm) -> index in start until end && (name == null || gm.name == name) }
        .map { it.value }
}

internal fun namedSkiaGmsForRunner(gms: List<SkiaGm>): List<Named<SkiaGm>> =
    gms.map { Named.of(it.name, it) }

internal fun validateSkiaGmRange(from: Int?, to: Int?, size: Int) {
    val start = from ?: 0
    val end = to ?: size
    require(start >= 0) { "GM range start must be non-negative: $start" }
    require(end >= 0) { "GM range end must be non-negative: $end" }
    require(start <= end) { "GM range start must not exceed end: $start > $end" }
    require(start <= size) { "GM range start is outside registry: $start > $size" }
    require(end <= size) { "GM range end is outside registry: $end > $size" }
}
