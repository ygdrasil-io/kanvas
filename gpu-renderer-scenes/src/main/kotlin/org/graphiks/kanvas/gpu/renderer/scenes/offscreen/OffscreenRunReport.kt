package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json

enum class OffscreenRunStatus(val wireName: String) {
    NotYetRendered("not-yet-rendered"),
    RenderFailed("render-failed"),
}

data class OffscreenRunReport(
    val sceneId: String,
    val runStatus: OffscreenRunStatus,
    val backend: String,
    val imagePath: String?,
    val width: Int?,
    val height: Int?,
    val byteCount: Long?,
    val nonTransparentPixels: Int?,
    val diagnostics: List<String>,
) {
    val status: String get() = runStatus.wireName
    val productRefusal: Boolean get() = false

    init {
        require(diagnostics.isNotEmpty()) { "diagnostics must not be empty" }
        require(diagnostics.all { it.isNotBlank() }) { "diagnostics must not contain blank entries" }
    }

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
                runStatus = OffscreenRunStatus.NotYetRendered,
                backend = "webgpu-offscreen",
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = singleDiagnostic(reason),
            )

        fun failed(sceneId: String, reason: String, backend: String = "webgpu-offscreen"): OffscreenRunReport =
            OffscreenRunReport(
                sceneId = sceneId,
                runStatus = OffscreenRunStatus.RenderFailed,
                backend = backend,
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = singleDiagnostic(reason),
            )

        private fun singleDiagnostic(reason: String): List<String> {
            require(reason.isNotBlank()) { "reason must not be blank" }
            return listOf(reason)
        }
    }
}
