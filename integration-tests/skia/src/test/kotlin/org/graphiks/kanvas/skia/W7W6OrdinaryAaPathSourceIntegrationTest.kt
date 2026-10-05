package org.graphiks.kanvas.skia

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.SceneRecordingScope
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.skia.gm.blur.BlurCircles2Gm
import org.graphiks.kanvas.skia.gm.blur.RRectBlurGm
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.ReferenceManager
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Full-frame native qualification witnesses for two W7 ordinary AA source GMs. */
class W7W6OrdinaryAaPathSourceIntegrationTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun rrectBlursRendersCompleteOrdinarySeparators() {
        val gm = RRectBlurGm()
        qualifyFullGm(
            gm = gm,
            replayGm = ::RRectBlurGm,
            commandIndex = 10,
            expectedReferencePngSha256 = "3327fa6254d219f5a23c5bdcdab30f0f363da1834353ff246f7e0b5ce66beaaa",
            expectedPixels = listOf(
                expected("left_white_center_y50", 50, 50, 255, 255, 255),
                expected("right_white_center_y50", 250, 50, 255, 255, 255),
                expected("left_yellow_center_y150", 50, 150, 255, 255, 0),
                expected("right_yellow_center_y150", 250, 150, 255, 255, 0),
                expected("left_orange_center_y250", 50, 250, 200, 100, 30),
                expected("right_orange_center_y250", 250, 250, 200, 100, 30),
                expected("left_blue_center_y350", 50, 350, 35, 120, 220),
                expected("right_blue_center_y350", 250, 350, 35, 120, 220),
                expected("empty_center_column", 150, 50, 68, 68, 68),
                expected("vertical_separator_left_half", 99, 50, 192, 192, 192, tolerance = 1),
                expected("vertical_separator_right_half", 100, 50, 192, 192, 192, tolerance = 1),
                expected("horizontal_separator_y100_top_half", 150, 99, 192, 192, 192, tolerance = 1),
                expected("horizontal_separator_y100_bottom_half", 150, 100, 192, 192, 192, tolerance = 1),
                expected("horizontal_separator_y200_top_half", 150, 199, 192, 192, 192, tolerance = 1),
                expected("horizontal_separator_y200_bottom_half", 150, 200, 192, 192, 192, tolerance = 1),
                expected("horizontal_separator_y300_top_half", 150, 299, 192, 192, 192, tolerance = 1),
                expected("horizontal_separator_y300_bottom_half", 150, 300, 192, 192, 192, tolerance = 1),
                expected("vertical_outer_left", 98, 50, 68, 68, 68),
                expected("vertical_outer_right", 101, 50, 68, 68, 68),
                expected("horizontal_outer_y100_top", 150, 98, 68, 68, 68),
                expected("horizontal_outer_y100_bottom", 150, 101, 68, 68, 68),
                expected("horizontal_outer_y200_top", 150, 198, 68, 68, 68),
                expected("horizontal_outer_y200_bottom", 150, 201, 68, 68, 68),
                expected("horizontal_outer_y300_top", 150, 298, 68, 68, 68),
                expected("horizontal_outer_y300_bottom", 150, 301, 68, 68, 68),
            ),
            crops = listOf(
                EvidenceCrop("row-000", 0, 0, 300, 100),
                EvidenceCrop("row-100", 0, 100, 300, 100),
                EvidenceCrop("row-200", 0, 200, 300, 100),
                EvidenceCrop("row-300", 0, 300, 300, 100),
            ),
        )
    }

    @Test
    fun blurCircles2RendersCompleteOrdinarySeparators() {
        val gm = BlurCircles2Gm()
        qualifyFullGm(
            gm = gm,
            replayGm = ::BlurCircles2Gm,
            commandIndex = 22,
            expectedReferencePngSha256 = "57680c49964fa6989acebf8526498cf799f9eaf07ad3d5c3cd8dfd7f87146964",
            expectedPixels = listOf(
                darkCenter("first_circle_center_presence_bound", 65, 65),
                darkCenter("first_almost_circle_center_presence_bound", 65, 170),
                expected("empty_white_background", 10, 10, 255, 255, 255),
                expected("separator_y222", 200, 222, 0, 0, 0),
                expected("separator_y456", 200, 456, 0, 0, 0),
                expected("separator_y722", 200, 722, 0, 0, 0),
                expected("separator_y1020", 200, 1020, 0, 0, 0),
            ),
            crops = listOf(
                EvidenceCrop("first-circle", 40, 40, 50, 50),
                EvidenceCrop("first-almost-circle", 40, 145, 50, 50),
                EvidenceCrop("separator-y222", 65, 212, 573, 21),
                EvidenceCrop("separator-y456", 65, 446, 573, 21),
                EvidenceCrop("separator-y722", 65, 712, 573, 21),
                EvidenceCrop("separator-y1020", 65, 1010, 573, 21),
            ),
        )
    }

    private fun qualifyFullGm(
        gm: SkiaGm,
        replayGm: () -> SkiaGm,
        commandIndex: Int,
        expectedReferencePngSha256: String,
        expectedPixels: List<PixelExpectation>,
        crops: List<EvidenceCrop>,
    ) {
        val firstSurface = recordGm(gm)
        printOriginalDraw(gm.name, firstSurface, commandIndex)
        val first = firstSurface.render()
        val actual = first.pixels.map { it.toByte() }.toByteArray()

        val referencePath = "/reference/${gm.referenceName}.png"
        val referencePng = requireNotNull(javaClass.getResourceAsStream(referencePath)) {
            "missing $referencePath"
        }.use { it.readBytes() }
        val referenceSha256 = sha256(referencePng)
        val reference = ReferenceManager.loadReference(referencePath)
        val referenceImage = ComparisonUtils.readPngAsSrgbBufferedImage(ByteArrayInputStream(referencePng))
        val evidenceDirectory = System.getProperty("w7.ordinaryAaEvidenceDir")
            ?.let { File(it, gm.name) }

        // Save actual and reference first so a later metric/content failure keeps both full frames.
        if (evidenceDirectory != null) {
            require(evidenceDirectory.mkdirs() || evidenceDirectory.isDirectory) {
                "cannot create W7 evidence directory: $evidenceDirectory"
            }
            ComparisonUtils.saveRgbaAsPng(actual, first.width, first.height, File(evidenceDirectory, "actual.png"))
            ComparisonUtils.saveRgbaAsPng(reference, referenceImage.width, referenceImage.height,
                File(evidenceDirectory, "reference.png"))
        }

        val comparison0 = ComparisonUtils.compareRgba(
            actual, reference, gm.width, gm.height, tolerance = 0, minSimilarity = 0.0,
        )
        val comparison2 = ComparisonUtils.compareRgba(
            actual, reference, gm.width, gm.height, tolerance = 2, minSimilarity = 0.0,
        )
        val diff0 = comparison0.diffRgba ?: ByteArray(actual.size)
        val diff2 = comparison2.diffRgba ?: ByteArray(actual.size)
        if (evidenceDirectory != null) {
            ComparisonUtils.saveRgbaAsPng(diff0, gm.width, gm.height, File(evidenceDirectory, "diff-tolerance-0.png"))
            ComparisonUtils.saveRgbaAsPng(diff2, gm.width, gm.height, File(evidenceDirectory, "diff-tolerance-2.png"))
            comparisonCrops(evidenceDirectory, actual, reference, diff0, diff2, gm.width, gm.height, crops)
        }

        val replay = recordGm(replayGm()).render()
        val replayBytes = replay.pixels.map { it.toByte() }.toByteArray()
        if (evidenceDirectory != null) {
            ComparisonUtils.saveRgbaAsPng(replayBytes, replay.width, replay.height,
                File(evidenceDirectory, "replay.png"))
        }
        val anchorSamples = sampleEvidenceJson(actual, first.width, expectedPixels)
        println(
            "W7_W6_ORDINARY_AA_GM {" +
                "\"gm\":${jsonString(gm.name)}," +
                "\"domain\":${jsonString(gm.compositionDomain.name)}," +
                "\"dimensions\":{\"actualWidth\":${first.width},\"actualHeight\":${first.height}," +
                    "\"expectedWidth\":${gm.width},\"expectedHeight\":${gm.height}}," +
                "\"referencePngSha256\":${jsonString(referenceSha256)}," +
                "\"actualRgbaSha256\":${jsonString(sha256(actual))}," +
                "\"replayRgbaSha256\":${jsonString(sha256(replayBytes))}," +
                "\"native\":${renderReceiptJson(first)}," +
                "\"replayNative\":${renderReceiptJson(replay)}," +
                "\"sourceResolveConsumption\":{" +
                    "\"observedNativeCounters\":${longMapJson(first.nativeEvidenceCounters)}," +
                    "\"observedStructuralSteps\":${stringListJson(first.structuralSteps)}," +
                    "\"reviewedTask1Contract\":${jsonString("ordinary AA4 source resolves once to 1x and has one W6 consumer; Task1 Sol review at c14864408d3d63eb162bbbfb04489d64d3fd0625")}," +
                    "\"perSourceRuntimeCountersExported\":false}," +
                "\"comparisonTolerance0\":${comparisonJson(comparison0)}," +
                "\"comparisonTolerance2\":${comparisonJson(comparison2)}," +
                "\"contentSamples\":$anchorSamples," +
                "\"minSimilarityIsFidelityOracle\":false," +
                "\"evidenceDirectory\":${evidenceDirectory?.let { jsonString(it.absolutePath) } ?: "null"}" +
                "}"
        )

        // Every image/metric receipt is emitted before these hard content and delivery assertions.
        assertEquals(expectedReferencePngSha256, referenceSha256, "$referencePath fixed PNG hash")
        assertEquals(gm.width, referenceImage.width, "$referencePath width")
        assertEquals(gm.height, referenceImage.height, "$referencePath height")
        assertEquals(gm.width, first.width, "${gm.name} actual width")
        assertEquals(gm.height, first.height, "${gm.name} actual height")
        assertNative(first, "${gm.name} full frame")
        assertNative(replay, "${gm.name} replay")
        assertArrayEquals(actual, replayBytes, "${gm.name} identical full-frame replay bytes")
        assertOpaque(actual, first.width, first.height, "${gm.name} full frame")
        expectedPixels.forEach { expectation -> assertPixel(actual, first.width, expectation) }
    }

    private fun assertNative(result: RenderResult, label: String) {
        assertTrue(result.isClean, "$label ${result.diagnostics.summary()}")
        assertTrue(result.stats.opsDispatched > 0, "$label expected positive native dispatch")
        assertEquals(0, result.stats.opsRefused, "$label refused operations")
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")),
            "$label scopes=${result.nativeEvidenceScopeKinds}")
    }

    private fun assertOpaque(rgba: ByteArray, width: Int, height: Int, label: String) {
        assertEquals(width * height * 4, rgba.size, "$label RGBA byte count")
        for (pixel in 0 until width * height) {
            assertEquals(255, rgba[pixel * 4 + 3].toInt() and 0xff, "$label alpha at (${pixel % width},${pixel / width})")
        }
    }

    private fun assertPixel(rgba: ByteArray, width: Int, expected: PixelExpectation) {
        val offset = (expected.y * width + expected.x) * 4
        val actual = (0..3).map { rgba[offset + it].toInt() and 0xff }
        assertTrue(actual[0] in expected.red && actual[1] in expected.green && actual[2] in expected.blue && actual[3] == 255,
            "${expected.label} at (${expected.x},${expected.y}) actual=$actual expected=" +
                "R${expected.red} G${expected.green} B${expected.blue} A255")
    }

    private fun expected(
        label: String,
        x: Int,
        y: Int,
        red: Int,
        green: Int,
        blue: Int,
        tolerance: Int = 0,
    ) = PixelExpectation(
        label, x, y,
        (red - tolerance).coerceAtLeast(0)..(red + tolerance).coerceAtMost(255),
        (green - tolerance).coerceAtLeast(0)..(green + tolerance).coerceAtMost(255),
        (blue - tolerance).coerceAtLeast(0)..(blue + tolerance).coerceAtMost(255),
    )

    private fun darkCenter(label: String, x: Int, y: Int) =
        PixelExpectation(label, x, y, 0..96, 0..96, 0..96)

    private fun sampleEvidenceJson(
        rgba: ByteArray,
        width: Int,
        expectedPixels: List<PixelExpectation>,
    ): String = expectedPixels.joinToString(prefix = "[", postfix = "]") { expected ->
        val offset = (expected.y * width + expected.x) * 4
        val channels = (0..3).joinToString(prefix = "[", postfix = "]") {
            (rgba[offset + it].toInt() and 0xff).toString()
        }
        "{\"label\":${jsonString(expected.label)},\"x\":${expected.x},\"y\":${expected.y}," +
            "\"actualRgba\":$channels,\"expected\":{\"r\":${jsonString(expected.red.toString())}," +
            "\"g\":${jsonString(expected.green.toString())},\"b\":${jsonString(expected.blue.toString())},\"a\":\"255\"}}"
    }

    private fun comparisonJson(result: ComparisonUtils.ComparisonResult): String =
        "{\"similarity\":${result.similarity},\"ssim\":${result.ssim}," +
            "\"meanChannelError\":${result.meanChannelError},\"totalPixels\":${result.totalPixels}," +
            "\"matchingPixels\":${result.matchingPixels},\"maxDiff\":${intArrayJson(result.maxDiff)}," +
            "\"meanDiff\":${result.meanDiff.joinToString(prefix = "[", postfix = "]")}," +
            "\"minSimilarity\":${result.minSimilarity},\"isPassingOnlyByConfiguredMinimum\":${result.isPassing}}"

    private fun renderReceiptJson(result: RenderResult): String =
        "{\"isClean\":${result.isClean},\"opsDispatched\":${result.stats.opsDispatched}," +
            "\"opsRefused\":${result.stats.opsRefused},\"pipelineCount\":${result.stats.pipelineCount}," +
            "\"drawCallCount\":${result.stats.drawCallCount},\"nativeEvidenceCounters\":" +
            "${longMapJson(result.nativeEvidenceCounters)},\"nativeEvidenceScopeKinds\":" +
            "${stringListJson(result.nativeEvidenceScopeKinds)},\"structuralSteps\":" +
            "${stringListJson(result.structuralSteps)},\"format\":${jsonString(result.format.name)}," +
            "\"colorSpace\":${jsonString(result.colorSpace.toString())}," +
            "\"premultiplication\":${jsonString(result.premultiplication.name)}," +
            "\"diagnostics\":${jsonString(result.diagnostics.summary())}}"

    private fun comparisonCrops(
        evidenceDirectory: File,
        actual: ByteArray,
        reference: ByteArray,
        diff0: ByteArray,
        diff2: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        crops: List<EvidenceCrop>,
    ) {
        val cropDirectory = File(evidenceDirectory, "crops")
        require(cropDirectory.mkdirs() || cropDirectory.isDirectory) {
            "cannot create W7 crop directory: $cropDirectory"
        }
        crops.forEach { crop ->
            require(crop.x >= 0 && crop.y >= 0 && crop.width > 0 && crop.height > 0 &&
                crop.x + crop.width <= imageWidth && crop.y + crop.height <= imageHeight) {
                "invalid fixed W7 crop $crop for ${imageWidth}x$imageHeight"
            }
            listOf(
                "actual" to actual,
                "reference" to reference,
                "diff-tolerance-0" to diff0,
                "diff-tolerance-2" to diff2,
            ).forEach { (kind, bytes) ->
                ComparisonUtils.saveRgbaAsPng(
                    cropRgba(bytes, imageWidth, crop), crop.width, crop.height,
                    File(cropDirectory, "$kind-${crop.name}.png"),
                )
            }
        }
    }

    private fun cropRgba(rgba: ByteArray, imageWidth: Int, crop: EvidenceCrop): ByteArray {
        val result = ByteArray(crop.width * crop.height * 4)
        for (row in 0 until crop.height) {
            val sourceOffset = ((crop.y + row) * imageWidth + crop.x) * 4
            val targetOffset = row * crop.width * 4
            System.arraycopy(rgba, sourceOffset, result, targetOffset, crop.width * 4)
        }
        return result
    }

    private fun intArrayJson(values: IntArray) = values.joinToString(prefix = "[", postfix = "]")

    private fun longMapJson(values: Map<String, Long>) = values.entries.sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { "${jsonString(it.key)}:${it.value}" }

    private fun stringListJson(values: List<String>) = values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }

    private fun jsonString(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class PixelExpectation(
        val label: String,
        val x: Int,
        val y: Int,
        val red: IntRange,
        val green: IntRange,
        val blue: IntRange,
    )

    private data class EvidenceCrop(val name: String, val x: Int, val y: Int, val width: Int, val height: Int)

    private fun recordGm(gm: SkiaGm): Surface {
        val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)
        SceneRecordingScope.recordingOnly {
            canvas.drawRect(RectF32.ofLTRB(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
                Paint(color = ColorARGB.White, antiAlias = false))
            gm.onOnceBeforeDraw(canvas)
            gm.draw(canvas, gm.width, gm.height)
        }
        return surface
    }

    private fun printOriginalDraw(gmName: String, surface: Surface, commandIndex: Int) {
        val scene = when (val capture = surface.snapshotScene()) {
            is SceneCaptureResult.Captured -> capture.scene
            is SceneCaptureResult.Invalid -> error(capture.diagnostics.joinToString { "${it.code.value}: ${it.message}" })
        }
        val command = scene.commandAt(commandIndex) as SceneCommand.Draw
        val draw = command.node
        println(formatOriginalDraw(gmName, commandIndex, draw))
    }

    private fun formatOriginalDraw(gmName: String, commandIndex: Int, draw: DrawNode): String {
        val paint = draw.paint
        val pathFillRule = (draw.geometry as? GeometryNode.Path)?.path?.fillRule
        return buildString {
            append("W7_W6_ORDINARY_AA_ORIGINAL")
            append(" gm=").append(gmName)
            append(" commandIndexI32=").append(commandIndex)
            append(" origin=").append(draw.origin)
            append(" geometry=").append(draw.geometry)
            append(" pathFillRule=").append(pathFillRule)
            append(" coverage=").append(draw.coverage)
            append(" style=").append(paint?.style)
            append(" strokeWidth=").append(paint?.strokeWidth)
            append(" strokeCap=").append(paint?.strokeCap)
            append(" strokeJoin=").append(paint?.strokeJoin)
            append(" material=").append(draw.material)
            append(" blend=").append(draw.blend)
            append(" clip=").append(draw.clip)
            append(" ctm=").append(draw.transform)
            append(" imageFilter=").append(paint?.imageFilter)
            append(" maskFilter=").append(paint?.maskFilter)
            append(" colorFilter=").append(paint?.colorFilter)
            append(" pathEffect=").append(paint?.pathEffect)
            append(" effects=").append(draw.effects)
            append(" paint=").append(paint)
        }
    }
}
