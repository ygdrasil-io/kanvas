package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json

enum class OffscreenRunStatus(val wireName: String) {
    NotYetRendered("not-yet-rendered"),
    RenderFailed("render-failed"),
    Rendered("rendered"),
}

class OffscreenRunReport(
    val sceneId: String,
    val runStatus: OffscreenRunStatus,
    val backend: String,
    val imagePath: String?,
    val width: Int?,
    val height: Int?,
    val byteCount: Long?,
    val nonTransparentPixels: Int?,
    diagnostics: List<String>,
) {
    private val diagnosticsSnapshot: List<String> = Collections.unmodifiableList(diagnostics.toList())

    val diagnostics: List<String> get() = diagnosticsSnapshot
    val status: String get() = runStatus.wireName
    val productRefusal: Boolean get() = false

    init {
        require(sceneId.isNotBlank()) { "sceneId must not be blank" }
        require(backend.isNotBlank()) { "backend must not be blank" }
        require(diagnosticsSnapshot.isNotEmpty()) { "diagnostics must not be empty" }
        require(diagnosticsSnapshot.all { it.isNotBlank() }) { "diagnostics must not contain blank entries" }
        requireStatusInvariants()
    }

    private fun requireStatusInvariants(): Unit =
        when (runStatus) {
            OffscreenRunStatus.NotYetRendered,
            OffscreenRunStatus.RenderFailed -> requireNoRenderedOutput()
            OffscreenRunStatus.Rendered -> requireRenderedOutput()
        }

    private fun requireNoRenderedOutput() {
        require(imagePath == null) { "${runStatus.wireName} reports must not include imagePath" }
        require(width == null) { "${runStatus.wireName} reports must not include width" }
        require(height == null) { "${runStatus.wireName} reports must not include height" }
        require(byteCount == null) { "${runStatus.wireName} reports must not include byteCount" }
        require(nonTransparentPixels == null) {
            "${runStatus.wireName} reports must not include nonTransparentPixels"
        }
    }

    private fun requireRenderedOutput() {
        require(!imagePath.isNullOrBlank()) { "rendered reports must include nonblank imagePath" }
        require(width != null && width > 0) { "rendered reports must include positive width" }
        require(height != null && height > 0) { "rendered reports must include positive height" }
        require(byteCount != null && byteCount > 0L) { "rendered reports must include positive byteCount" }
        require(nonTransparentPixels != null && nonTransparentPixels > 0) {
            "rendered reports must include nonTransparentPixels > 0"
        }
        val pixelCount = width.toLong() * height.toLong()
        val expectedByteCount = pixelCount * RAW_RGBA_BYTES_PER_PIXEL
        require(byteCount == expectedByteCount) {
            "rendered reports raw RGBA byteCount must equal width * height * 4: expected $expectedByteCount, got $byteCount"
        }
        require(nonTransparentPixels.toLong() <= pixelCount) {
            "rendered reports nonTransparentPixels must be in 1..$pixelCount"
        }
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
        private const val RAW_RGBA_BYTES_PER_PIXEL: Long = 4L

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

        fun rendered(
            sceneId: String,
            imagePath: String,
            width: Int,
            height: Int,
            byteCount: Long,
            nonTransparentPixels: Int,
            diagnostics: List<String>,
            backend: String = "webgpu-offscreen",
        ): OffscreenRunReport =
            OffscreenRunReport(
                sceneId = sceneId,
                runStatus = OffscreenRunStatus.Rendered,
                backend = backend,
                imagePath = imagePath,
                width = width,
                height = height,
                byteCount = byteCount,
                nonTransparentPixels = nonTransparentPixels,
                diagnostics = diagnostics,
            )

        private fun singleDiagnostic(reason: String): List<String> {
            require(reason.isNotBlank()) { "reason must not be blank" }
            return listOf(reason)
        }
    }
}
