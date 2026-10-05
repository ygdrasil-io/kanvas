@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class W7InverseSceneInventorySurfacePixelTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    // Catches wrong inverse polarity for a hard inverse draw.
    @Test
    fun inverseOnlyHard() {
        val result = inverseOnlySurface(antiAlias = false).renderAndRepeat()

        assertInverseOnlyPixels(result.pixels)
    }

    // Catches wrong inverse polarity for an antialiased inverse draw.
    @Test
    fun inverseOnlyAA() {
        val result = inverseOnlySurface(antiAlias = true).renderAndRepeat()

        assertInverseOnlyPixels(result.pixels)
    }

    // Catches a paired inverse cover that applies a translucent source more than once.
    @Test
    fun inverseOnlyAlphaHard() {
        val result = inverseOnlyAlphaSurface(antiAlias = false).renderAndRepeat()

        assertInverseOnlyAlphaPixels(result.pixels)
    }

    // Catches a paired inverse cover that loses alpha on the antialiased route.
    @Test
    fun inverseOnlyAlphaAA() {
        val result = inverseOnlyAlphaSurface(antiAlias = true).renderAndRepeat()

        assertInverseOnlyAlphaPixels(result.pixels)
    }

    // Catches an earlier AA inverse cover losing its complement when a later color pass resolves.
    @Test
    fun successiveInverseAAColorsPreserveEarlierComplement() {
        val result = successiveInverseAASurface().renderAndRepeat()

        assertSuccessiveInverseAAPixels(result.pixels)
    }

    // Catches a lost ordinary sibling before a hard inverse draw.
    @Test
    fun ordinaryThenInverseHard() {
        val result = ordinaryThenInverseSurface(antiAlias = false).renderAndRepeat()

        assertOrdinaryThenInversePixels(result.pixels)
    }

    // Catches a lost ordinary sibling before an antialiased inverse draw.
    @Test
    fun ordinaryThenInverseAA() {
        val result = ordinaryThenInverseSurface(antiAlias = true).renderAndRepeat()

        assertOrdinaryThenInversePixels(result.pixels)
    }

    // Catches wrong inverse polarity in a hard intersect clip.
    @Test
    fun inverseClipHard() {
        val result = inverseClipSurface(antiAlias = false).renderAndRepeat()

        assertInverseClipPixels(result.pixels)
    }

    // Catches missing native inventory for an antialiased inverse intersect clip.
    @Test
    fun inverseClipAA() {
        val result = inverseClipSurface(antiAlias = true).renderAndRepeat()

        assertInverseClipPixels(result.pixels)
    }

    // Catches a suffix draw that loses the inverse producer/cover scene ownership.
    @Test
    fun inverseThenOrdinaryHard() {
        val result = inverseThenOrdinarySurface(antiAlias = false).renderAndRepeat()

        assertInverseThenOrdinaryPixels(result.pixels)
    }

    // Catches the same suffix ownership loss when the inverse route is four-sample.
    @Test
    fun inverseThenOrdinaryAA() {
        val result = inverseThenOrdinarySurface(antiAlias = true).renderAndRepeat()

        assertInverseThenOrdinaryPixels(result.pixels)
    }

    // Catches recording bounds being treated as a clip for a real in-memory hard Picture.
    @Test
    fun inMemoryHardPictureKeepsExplicitClipAfterTranslation() {
        val result = inversePictureSurface(antiAlias = false, serialized = false).renderAndRepeat()

        assertTranslatedInversePicturePixels(result.pixels)
    }

    // Catches serialization changing a hard Picture's explicit clip or inverse ownership.
    @Test
    fun serializedHardPictureKeepsExplicitClipAfterTranslation() {
        val result = inversePictureSurface(antiAlias = false, serialized = true).renderAndRepeat()

        assertTranslatedInversePicturePixels(result.pixels)
    }

    // Catches recording bounds being treated as a clip for a real in-memory AA Picture.
    @Test
    fun inMemoryAAPictureKeepsExplicitClipAfterTranslation() {
        val result = inversePictureSurface(antiAlias = true, serialized = false).renderAndRepeat(
            expectedKinds = setOf("Render", "LayerComposite", "Readback"),
        )

        assertTranslatedInversePicturePixels(result.pixels)
    }

    // Catches serialization changing an AA Picture's explicit clip or inverse ownership.
    @Test
    fun serializedAAPictureKeepsExplicitClipAfterTranslation() {
        val result = inversePictureSurface(antiAlias = true, serialized = true).renderAndRepeat(
            expectedKinds = setOf("Render", "LayerComposite", "Readback"),
        )

        assertTranslatedInversePicturePixels(result.pixels)
    }

    // Catches a typed composite refusal poisoning subsequent admitted inverse work on this Surface.
    @Test
    fun unsupportedCompositePaintRefusalPreservesSentinelAndRecoversMixedInverseScene() {
        val rejected = Surface(16, 16).also { surface -> surface.canvas {
            drawPicture(unsupportedCompositePaintPicture())
        } }
        val sentinel = UByteArray(16 * 16 * 4) { 0x5au }
        val before = sentinel.copyOf()

        val failure = assertFailsWith<IllegalStateException> {
            rejected.readPixels(RectF32.ofLTRB(0f, 0f, 16f, 16f), sentinel)
        }
        assertTrue(failure.message.orEmpty().contains("unsupported.composite.paint"), failure.message)
        assertContentEquals(before, sentinel, "refusal must preserve the readPixels sentinel")

        rejected.discardRecordedOperations()
        rejected.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false))
            drawPath(inverseRect(), Paint(ColorARGB.White, antiAlias = false))
        }
        val recovered = rejected.renderAndRepeat()
        assertOrdinaryThenInversePixels(recovered.pixels, width = 16, height = 16)
    }

    private fun inverseOnlySurface(antiAlias: Boolean): Surface = Surface(8, 8).also { surface ->
        surface.canvas {
            drawPath(inverseRect(), Paint(ColorARGB.White, antiAlias = antiAlias))
        }
    }

    private fun inverseOnlyAlphaSurface(antiAlias: Boolean): Surface = Surface(8, 8).also { surface ->
        surface.canvas {
            drawPath(inverseRect(), Paint(ColorARGB.of(128, 255, 255, 255), antiAlias = antiAlias))
        }
    }

    private fun successiveInverseAASurface(): Surface = Surface(8, 8).also { surface ->
        surface.canvas {
            drawPath(inverseRect(), Paint(ColorARGB.White, antiAlias = true))
            drawPath(inverseLeftRect(), Paint(ColorARGB.Blue, antiAlias = true))
        }
    }

    private fun ordinaryThenInverseSurface(antiAlias: Boolean): Surface = Surface(8, 8).also { surface ->
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
            drawPath(inverseRect(), Paint(ColorARGB.White, antiAlias = antiAlias))
        }
    }

    private fun inverseClipSurface(antiAlias: Boolean): Surface = Surface(8, 8).also { surface ->
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.White, antiAlias = false))
            clipPath(inverseRect(), ClipOp.INTERSECT, antiAlias = antiAlias)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
        }
    }

    private fun inverseThenOrdinarySurface(antiAlias: Boolean): Surface = Surface(8, 8).also { surface ->
        surface.canvas {
            drawPath(inverseRect(), Paint(ColorARGB.White, antiAlias = antiAlias))
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 8f), Paint(ColorARGB.Red, antiAlias = false))
        }
    }

    private fun inversePictureSurface(antiAlias: Boolean, serialized: Boolean): Surface = Surface(12, 10).also { surface ->
        surface.canvas {
            save()
            translate(2f, 1f)
            drawPicture(inversePicture(antiAlias, serialized))
            restore()
        }
    }

    private fun inversePicture(antiAlias: Boolean, serialized: Boolean): Picture {
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).apply {
            clipRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), ClipOp.INTERSECT, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
            drawPath(inverseRect(), Paint(ColorARGB.White, antiAlias = antiAlias))
        }
        val picture = recorder.finishRecordingAsPicture()
        return if (serialized) requireNotNull(Picture.fromByteArray(picture.toByteArray())) else picture
    }

    private fun unsupportedCompositePaintPicture(): Picture {
        // Keep this public refusal fixture identical to
        // W7HardPictureSurfacePixelTest.opacitySolidShaderPictureStaysOutsideHardSeed.
        val bounds = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        val h = Matrix3x3F32(tx = 4f, ty = 3f, persp0 = 1f / 16f)
        val blue = ColorARGB.of(136, 0, 0, 255)
        return PictureRecorder().also { recorder -> recorder.beginRecording(bounds).apply {
            concat(h)
            drawRect(bounds, Paint(shader = Shader.Opacity(Shader.SolidColor(blue), .5f), antiAlias = false))
        } }.finishRecordingAsPicture()
    }

    private fun inverseRect(): Path = Path().apply {
        addRect(RectF32.ofLTRB(2f, 2f, 6f, 6f))
        fillType = FillType.INVERSE_WINDING
    }

    private fun inverseLeftRect(): Path = Path().apply {
        addRect(RectF32.ofLTRB(0f, 0f, 4f, 8f))
        fillType = FillType.INVERSE_WINDING
    }

    private fun Surface.renderAndRepeat(expectedKinds: Set<String> = setOf("Render", "Readback")): RenderResult {
        val first = render()
        assertNativeRenderAndReadback(first, expectedKinds)

        val second = render()
        assertNativeRenderAndReadback(second, expectedKinds)
        assertContentEquals(first.pixels, second.pixels, "second retained Surface frame must be byte-identical")
        return first
    }

    private fun assertNativeRenderAndReadback(
        result: RenderResult,
        expectedKinds: Set<String> = setOf("Render", "Readback"),
    ) {
        val trace = "diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.diagnostics.isEmpty, trace)
        assertEquals(expectedKinds, result.nativeEvidenceScopeKinds.toSet(), trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertEquals(0, result.stats.opsRefused, trace)
    }

    private fun assertInverseOnlyPixels(pixels: UByteArray) {
        for (y in 0 until 8) for (x in 0 until 8) {
            if (x in 2..5 && y in 2..5) assertPixel(pixels, x, y, 0, 0, 0, 0)
            else assertPixel(pixels, x, y, 255, 255, 255, 255)
        }
    }

    private fun assertInverseOnlyAlphaPixels(pixels: UByteArray) {
        for (y in 0 until 8) for (x in 0 until 8) {
            if (x in 2..5 && y in 2..5) assertPixel(pixels, x, y, 0, 0, 0, 0)
            // A linear-premultiplied half-white is sRGB-encoded to 188; alpha remains 128.
            else assertPixel(pixels, x, y, 188, 188, 188, 128)
        }
    }

    private fun assertSuccessiveInverseAAPixels(pixels: UByteArray) {
        for (y in 0 until 8) for (x in 0 until 8) {
            when {
                x >= 4 -> assertPixel(pixels, x, y, 0, 0, 255, 255)
                x >= 2 && y in 2..5 -> assertPixel(pixels, x, y, 0, 0, 0, 0)
                else -> assertPixel(pixels, x, y, 255, 255, 255, 255)
            }
        }
    }

    private fun assertOrdinaryThenInversePixels(pixels: UByteArray) {
        for (y in 0 until 8) for (x in 0 until 8) {
            if (x in 2..5 && y in 2..5) assertPixel(pixels, x, y, 0, 0, 255, 255)
            else assertPixel(pixels, x, y, 255, 255, 255, 255)
        }
    }

    private fun assertOrdinaryThenInversePixels(pixels: UByteArray, width: Int, height: Int) {
        for (y in 0 until height) for (x in 0 until width) {
            if (x in 2..5 && y in 2..5) assertPixel(pixels, width, x, y, 0, 0, 255, 255)
            else assertPixel(pixels, width, x, y, 255, 255, 255, 255)
        }
    }

    private fun assertInverseClipPixels(pixels: UByteArray) {
        for (y in 0 until 8) for (x in 0 until 8) {
            if (x in 2..5 && y in 2..5) assertPixel(pixels, x, y, 255, 255, 255, 255)
            else assertPixel(pixels, x, y, 0, 0, 255, 255)
        }
    }

    private fun assertInverseThenOrdinaryPixels(pixels: UByteArray) {
        for (y in 0 until 8) for (x in 0 until 8) {
            when {
                x < 4 -> assertPixel(pixels, x, y, 255, 0, 0, 255)
                x in 4..5 && y in 2..5 -> assertPixel(pixels, x, y, 0, 0, 0, 0)
                else -> assertPixel(pixels, x, y, 255, 255, 255, 255)
            }
        }
    }

    private fun assertTranslatedInversePicturePixels(pixels: UByteArray) {
        for (y in 0 until 10) for (x in 0 until 12) {
            val pictureX = x - 2
            val pictureY = y - 1
            when {
                pictureX !in 0..7 || pictureY !in 0..7 -> assertPixel(pixels, 12, x, y, 0, 0, 0, 0)
                pictureX in 2..5 && pictureY in 2..5 -> assertPixel(pixels, 12, x, y, 0, 0, 255, 255)
                else -> assertPixel(pixels, 12, x, y, 255, 255, 255, 255)
            }
        }
    }

    private fun assertPixel(pixels: UByteArray, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * 8 + x) * 4
        assertContentEquals(
            ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4),
            "pixel $x,$y",
        )
    }

    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * width + x) * 4
        assertContentEquals(
            ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4),
            "pixel $x,$y",
        )
    }
}
