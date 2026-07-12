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
        fun allGms() = selectSkiaGmsForRunner(
            SkiaGmRegistry.all(),
            System.getProperty("kanvas.gm.name"),
        )

        @AfterAll
        @JvmStatic
        fun cleanup() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @ParameterizedTest
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
            error("Reference PNG not found at $refPath. Run: cp <skia-native-reference> src/test/resources/reference/${gm.name}.png")
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

        assertTrue(comparison.isPassing) {
            "${gm.name}: similarity=${"%.2f".format(comparison.similarity)}% " +
            "(threshold: ${comparison.minSimilarity}%)"
        }
    }
}

internal fun referenceResourcePath(gm: SkiaGm): String =
    "/reference/${gm.referenceName}.png"

internal fun selectSkiaGmsForRunner(gms: List<SkiaGm>, name: String?): List<SkiaGm> =
    if (name == null) gms else gms.filter { it.name == name }
