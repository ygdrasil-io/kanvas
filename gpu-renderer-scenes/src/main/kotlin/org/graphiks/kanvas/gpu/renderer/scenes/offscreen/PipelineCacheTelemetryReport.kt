package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUPipelineCacheTelemetry

/**
 * KGPU-M27-002: pipeline-cache telemetry report aggregating per-scene snapshots.
 *
 * Telemetry is derived from each scene's draw plan (the pipeline passes the
 * offscreen renderer assembles), not from a backend pipeline cache. It reports
 * hit rate, eviction count, module count per scene, and pipeline creation count
 * per family, and carries no GPU support or performance claim.
 */
data class PipelineCacheTelemetryReport(
    val backend: String,
    val frameCount: Int,
    val sceneTelemetry: List<GPUPipelineCacheTelemetry>,
    val productActivation: Boolean = true,
) {
    /** Canonical dump lines for PM evidence and tests. */
    fun dumpLines(): List<String> =
        listOf(
            "pipeline-cache-telemetry backend=$backend frameCount=$frameCount scenes=${sceneTelemetry.size}",
        ) + sceneTelemetry.map { it.dumpLine() } + listOf(
            "nonclaim:pipeline-cache-telemetry draw-plan-derived no-backend-cache-observation " +
                "no-product-activation no-performance-claim",
        )

    /** JSON report written to `pipeline-cache-telemetry.json`. */
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"backend\": ${backend.json()},")
        appendLine("  \"frameCount\": $frameCount,")
        appendLine("  \"telemetrySource\": \"draw-plan-derived\",")
        appendLine("  \"productActivation\": $productActivation,")
        appendLine("  \"scenes\": [")
        appendLine(sceneTelemetry.joinToString(",\n") { "    ${it.toJsonObject()}" })
        appendLine("  ]")
        appendLine("}")
    }

    /** Writes `pipeline-cache-telemetry.json` and a diagnostics transcript to [outputDir]. */
    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("pipeline-cache-telemetry.json").writeText(toJson())
        outputDir.resolve("pipeline-cache-telemetry-diagnostics.txt")
            .writeText(dumpLines().joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        /**
         * Builds pipeline-cache telemetry for the eight benchmark family scenes by
         * resolving each scene and deriving telemetry from its draw plan. Requires no
         * GPU adapter; the cache model is steady-state over [frameCount] frames.
         */
        fun forBenchmarkFamilies(
            frameCount: Int,
            backend: String = "webgpu-offscreen",
        ): PipelineCacheTelemetryReport =
            PipelineCacheTelemetryReport(
                backend = backend,
                frameCount = frameCount,
                sceneTelemetry = PerFamilyBenchmark.families.map { family ->
                    val scene = GPURendererSceneRegistry.registry.requireScene(family.sceneId)
                    val drawPlan = prepareRectOnlyDrawPlan(
                        sceneId = family.sceneId,
                        commands = scene.commands,
                        width = scene.dimensions.width,
                        height = scene.dimensions.height,
                    )
                    rectOnlyPipelineCacheTelemetry(drawPlan, family.sceneId, frameCount)
                },
            )
    }
}

private fun GPUPipelineCacheTelemetry.toJsonObject(): String {
    val familyJson = pipelineCreationCountsByFamily.entries
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { "${it.key.json()}: ${it.value}" }
    return "{\"sceneId\": ${sceneId.json()}, \"hitCount\": $hitCount, \"missCount\": $missCount, " +
        "\"hitRate\": ${String.format(Locale.US, "%.4f", hitRate)}, \"evictionCount\": $evictionCount, " +
        "\"moduleCount\": $moduleCount, \"pipelineCreations\": $totalPipelineCreations, " +
        "\"pipelineCreationsByFamily\": $familyJson}"
}
