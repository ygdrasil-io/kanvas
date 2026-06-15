package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json

data class OffscreenRunReport(
    val sceneId: String,
    val status: String,
    val productRefusal: Boolean,
    val backend: String,
    val imagePath: String?,
    val width: Int?,
    val height: Int?,
    val byteCount: Long?,
    val nonTransparentPixels: Int?,
    val diagnostics: List<String>,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"sceneId\": ${sceneId.json()},")
        appendLine("  \"status\": ${status.json()},")
        appendLine("  \"productRefusal\": $productRefusal,")
        appendLine("  \"backend\": ${backend.json()},")
        appendLine("  \"imagePath\": ${imagePath?.json() ?: "null"},")
        appendLine("  \"width\": ${width ?: "null"},")
        appendLine("  \"height\": ${height ?: "null"},")
        appendLine("  \"byteCount\": ${byteCount ?: "null"},")
        appendLine("  \"nonTransparentPixels\": ${nonTransparentPixels ?: "null"},")
        appendLine("  \"diagnostics\": [${diagnostics.joinToString(",") { it.json() }}]")
        appendLine("}")
    }

    fun diagnosticsText(): String =
        diagnostics.joinToString(separator = "\n", postfix = "\n")

    fun writeTo(outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("run.json").writeText(toJson())
        outputDir.resolve("diagnostics.txt").writeText(diagnosticsText())
    }

    companion object {
        fun notYetRendered(sceneId: String, reason: String): OffscreenRunReport =
            OffscreenRunReport(
                sceneId = sceneId,
                status = "not-yet-rendered",
                productRefusal = false,
                backend = "webgpu-offscreen",
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf(reason),
            )

        fun failed(sceneId: String, reason: String, backend: String = "webgpu-offscreen"): OffscreenRunReport =
            OffscreenRunReport(
                sceneId = sceneId,
                status = "render-failed",
                productRefusal = false,
                backend = backend,
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf(reason),
            )
    }
}
