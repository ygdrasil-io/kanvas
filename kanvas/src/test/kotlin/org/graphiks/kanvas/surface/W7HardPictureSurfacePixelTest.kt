@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Public native witnesses for the closed W6 Picture hard-fill prerequisite. */
class W7HardPictureSurfacePixelTest {
    companion object {
        private val bounds = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        // H(x,y)=((x+4)/(1+x/16),(y+3)/(1+x/16)); these literals are the
        // independent fixture, not a product-math oracle.
        private val h = Matrix3x3F32(tx = 4f, ty = 3f, persp0 = 1f / 16f)
        private val perspectiveClipMatrix = Matrix3x3F32(
            sx = 1.25f, kx = .2f, tx = 3f,
            ky = -.1f, sy = .8f, ty = 7f,
            persp0 = .01f, persp1 = -.02f, persp2 = 1f,
        )
        private val dyadicPerspectiveClipMatrix = Matrix3x3F32.Identity.copy(persp0 = 1f / 16f)
        private val blue = ColorARGB.of(136, 0, 0, 255)
        private val red = ColorARGB.Red
        private val green = ColorARGB.Green

        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @JvmStatic fun refusalInputs(): List<Array<Any>> = listOf(
            arrayOf<Any>("horizon", Matrix3x3F32(persp0 = -.25f), RenderConfig.DEFAULT),
            arrayOf<Any>("nan", Matrix3x3F32(sx = Float.NaN), RenderConfig.DEFAULT),
            arrayOf<Any>("rgba16", h, RenderConfig(gpuColorFormat = GPUColorFormat.RGBA16_FLOAT)),
            arrayOf<Any>("budget1", h, RenderConfig(frameLocalBudgetBytes = 1L)),
        )
    }

    @Test fun projectiveRectHasHardCoverage() {
        val result = hardPictureSurface().renderAndRepeat()
        assertBlueComposite(result.pixels, 16, 5, 5)
        assertBlueComposite(result.pixels, 16, 4, 5)
        assertPixel(result.pixels, 16, 3, 5, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 9, 5, 255, 0, 0, 255)
    }

    @Test fun projectiveTriangleKeepsItsShape() {
        val picture = recorded { concat(h); drawPath(Path().apply {
            moveTo(0f, 0f); lineTo(8f, 0f); lineTo(0f, 8f); close()
        }, Paint(blue, antiAlias = false)) }
        val result = pictureSurface(16, 16, picture).renderAndRepeat()
        assertBlueComposite(result.pixels, 16, 5, 4)
        assertPixel(result.pixels, 16, 7, 5, 255, 0, 0, 255)
    }

    @Test fun projectiveEvenOddHoleDoesNotLeakStencil() {
        val picture = recorded { concat(h); drawPath(Path().apply {
            fillType = FillType.EVEN_ODD; addRect(bounds); addRect(RectF32.ofLTRB(2f, 2f, 6f, 6f))
        }, Paint(blue, antiAlias = false)); drawRect(RectF32.ofLTRB(2f, 2f, 6f, 6f), Paint(ColorARGB.Green, antiAlias = false)) }
        val result = pictureSurface(16, 16, picture).renderAndRepeat()
        assertBlueComposite(result.pixels, 16, 5, 3)
        assertPixel(result.pixels, 16, 6, 5, 0, 255, 0, 255)
    }

    @Test fun affineRectPictureUsesItsRecordedTransform() {
        val affine = Matrix3x3F32(tx = 4f, ty = 3f, kx = .5f)
        val picture = recorded { concat(affine); drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(blue, antiAlias = false)) }
        val result = pictureSurface(16, 16, picture).renderAndRepeat()
        assertBlueComposite(result.pixels, 16, 5, 4)
        assertPixel(result.pixels, 16, 4, 6, 255, 0, 0, 255)
    }

    @Test fun twoPictureOccurrencesSeeTheirOwnDestination() {
        val picture = projectiveRectPicture()
        val surface = Surface(24, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 24f, 16f), Paint(red, antiAlias = false))
            drawRect(RectF32.ofLTRB(8f, 0f, 24f, 16f), Paint(green, antiAlias = false))
            drawPicture(picture); save(); translate(8f, 0f); drawPicture(picture); restore()
        } }
        val result = surface.renderAndRepeat()
        assertBlueComposite(result.pixels, 24, 5, 5)
        assertBlueOverGreen(result.pixels, 24, 13, 5)
    }

    @Test fun nestedPictureComposesOuterTranslationOnce() {
        val inner = projectiveRectPicture()
        val outer = recorded { translate(8f, 4f); drawPicture(inner) }
        val result = pictureSurface(24, 16, outer).renderAndRepeat()
        assertBlueComposite(result.pixels, 24, 13, 9)
        assertPixel(result.pixels, 24, 11, 9, 255, 0, 0, 255)
    }

    @Test fun pictureLayerRebasesHardScissorAndSiblings() {
        val picture = projectiveRectPicture()
        val surface = Surface(24, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 24f, 16f), Paint(red, antiAlias = false))
            saveLayer(RectF32.ofLTRB(8f, 4f, 20f, 15f))
            drawRect(RectF32.ofLTRB(8f, 4f, 20f, 15f), Paint(green, antiAlias = false))
            save(); clipRect(RectF32.ofLTRB(12f, 8f, 15f, 12f), antiAlias = false); translate(8f, 4f); drawPicture(picture); restore()
            restore()
            drawRect(RectF32.ofLTRB(20f, 1f, 21f, 2f), Paint(ColorARGB.Green, antiAlias = false))
        } }
        val result = surface.renderAndRepeat()
        assertBlueOverGreen(result.pixels, 24, 13, 9)
        assertPixel(result.pixels, 24, 16, 9, 0, 255, 0, 255)
        assertPixel(result.pixels, 24, 7, 9, 255, 0, 0, 255)
        assertPixel(result.pixels, 24, 20, 1, 0, 255, 0, 255)
    }

    @Test fun identityAndScaleTranslatePicturesKeepAnalyticBudget() {
        // Fixed before GPU from the inline W6 topology: one 16x16 RGBA8 root target (1024),
        // 16 aligned 256-byte readback rows (4096), the W6 cursor (16), and one 16-byte
        // Solid material row for each distinct red/blue paint.  Picture inline owns no target;
        // a W4d path diversion would introduce its V/I/U pools and cannot fit B=5168.
        val budget = analyticPictureBudgetI64()
        listOf(false, true).forEach { scaleTranslate ->
            val picture = recorded { drawRect(RectF32.ofLTRB(1f, 1f, 4f, 4f), Paint(blue, antiAlias = false)) }
            val surface = Surface(16, 16, config = RenderConfig(frameLocalBudgetBytes = budget)).also { target -> target.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(red, antiAlias = false))
                save(); if (scaleTranslate) { translate(1f, 0f); scale(2f, 1f) }; drawPicture(picture); restore()
            } }
            val result = surface.renderAndRepeat()
            assertTrue(result.stats.opsDispatched > 0)
            assertBlueComposite(result.pixels, 16, if (scaleTranslate) 4 else 2, 2)
            assertPixel(result.pixels, 16, 0, 2, 255, 0, 0, 255)
        }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("refusalInputs")
    fun hardPictureRefusalsPreserveSentinelAndRecover(label: String, matrix: Matrix3x3F32, config: RenderConfig) {
        val refused = Surface(16, 16, config = config).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(red, antiAlias = false))
            save(); concat(matrix); drawPicture(recorded { drawRect(bounds, Paint(blue, antiAlias = false)) }); restore()
        } }
        val sentinel = UByteArray(16 * 16 * 4) { 0x5au }; val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(RectF32.ofLTRB(0f, 0f, 16f, 16f), sentinel) }
        assertTrue(failure.message.orEmpty().contains(refusalDiagnostic(label)), "$label ${failure.message}")
        assertContentEquals(before, sentinel, "$label must preserve the readPixels sentinel")
        val recovered = hardPictureSurface().renderAndRepeat()
        assertBlueComposite(recovered.pixels, 16, 5, 5)
    }

    @Test fun opacitySolidShaderPictureStaysOutsideHardSeed() {
        // This Picture is intentionally outside the closed no-shader hard family. Before a
        // Surface exists, its existing public path is the prepared compositor's typed paint
        // refusal, not a W6 child-source refusal; the sentinel makes that boundary observable.
        val shaderPicture = recorded {
            concat(h)
            drawRect(bounds, Paint(shader = Shader.Opacity(Shader.SolidColor(blue), .5f), antiAlias = false))
        }
        val rejected = Surface(16, 16).also { target -> target.canvas { drawPicture(shaderPicture) } }
        val sentinel = UByteArray(16 * 16 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            rejected.readPixels(RectF32.ofLTRB(0f, 0f, 16f, 16f), sentinel)
        }
        assertTrue(failure.message.orEmpty().contains("unsupported.composite.paint"), failure.message)
        assertContentEquals(before, sentinel)
        rejected.discardRecordedOperations()
        rejected.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Green, antiAlias = false)) }
        val recovered = rejected.renderAndRepeat()
        assertPixel(recovered.pixels, 16, 8, 8, 0, 255, 0, 255)
    }

    @Test fun serializedHardRectPictureKeepsPerspectiveClipRefusal() {
        assertPerspectiveClipRefusalAndRecover { target ->
            target.canvas { translate(1f, 0f); drawPicture(perspectiveClipPicture(path = false, serialized = true)) }
        }
    }

    @Test fun serializedHardPathPictureKeepsPerspectiveClipRefusal() {
        assertPerspectiveClipRefusalAndRecover { target ->
            target.canvas { translate(1f, 0f); drawPicture(perspectiveClipPicture(path = true, serialized = true)) }
        }
    }

    @Test fun forbiddenPerspectiveClipWinsOverHardProjectiveSiblingInEitherOrder() {
        listOf(false, true).forEach { forbiddenFirst ->
            assertPerspectiveClipRefusalAndRecover { target -> target.canvas {
                val forbidden = perspectiveClipPicture(path = false, serialized = true)
                val admitted = projectiveRectPicture()
                if (forbiddenFirst) {
                    drawPicture(forbidden); drawPicture(admitted)
                } else {
                    drawPicture(admitted); drawPicture(forbidden)
                }
            } }
        }
    }

    @Test fun enclosingPictureKeepsPerspectiveClipRefusal() {
        assertPerspectiveClipRefusalAndRecover { target -> target.canvas {
            drawPicture(recorded { drawPicture(perspectiveClipPicture(path = false, serialized = true)) })
        } }
    }

    @Test fun enclosingLayerKeepsPerspectiveClipRefusal() {
        assertPerspectiveClipRefusalAndRecover { target -> target.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 8f, 8f))
            drawPicture(perspectiveClipPicture(path = false, serialized = true))
            restore()
        } }
    }

    @Test fun capturedPerspectiveClipDoesNotBecomeAffineThroughOuterInverse() {
        val inverse = requireNotNull(dyadicPerspectiveClipMatrix.invert())
        assertTrue(Matrix3x3F32.concat(inverse, dyadicPerspectiveClipMatrix).isIdentity)
        assertPerspectiveClipRefusalAndRecover { target -> target.canvas {
            concat(inverse)
            drawPicture(perspectiveClipPicture(path = false, serialized = true, clipMatrix = dyadicPerspectiveClipMatrix))
        } }
    }

    @Test fun hardSiblingDoesNotAdmitAaPerspective() {
        val hard = projectiveRectPicture()
        val aa = recorded { drawRect(bounds, Paint(blue, antiAlias = true)) }
        val surface = Surface(16, 16).also { target -> target.canvas {
            drawPicture(hard); save(); concat(h); drawPicture(aa); restore()
        } }
        val sentinel = UByteArray(16 * 16 * 4) { 0x5au }; val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(RectF32.ofLTRB(0f, 0f, 16f, 16f), sentinel) }
        assertTrue(failure.message.orEmpty().contains("w6a.layer.unsupported_child"), failure.message)
        assertContentEquals(before, sentinel)
        val recovered = hardPictureSurface().renderAndRepeat()
        assertBlueComposite(recovered.pixels, 16, 5, 5)
    }

    @Test fun directFrameWithoutPictureStaysClean() {
        val surface = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(red, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 6f, 6f), Paint(blue, antiAlias = false))
        } }
        val result = surface.renderAndRepeat()
        assertBlueComposite(result.pixels, 16, 3, 3)
    }

    private fun projectiveRectPicture(): Picture = recorded { concat(h); drawRect(bounds, Paint(blue, antiAlias = false)) }
    private fun perspectiveClipPicture(
        path: Boolean,
        serialized: Boolean,
        clipMatrix: Matrix3x3F32 = perspectiveClipMatrix,
    ): Picture {
        val recorder = PictureRecorder()
        recorder.beginRecording(bounds).apply {
            setMatrix(clipMatrix)
            clipPath(Path().addRect(RectF32.ofLTRB(1f, 1f, 7f, 7f)), antiAlias = false)
            resetMatrix()
            if (path) drawPath(Path().addRect(bounds), Paint(red, antiAlias = false))
            else drawRect(bounds, Paint(red, antiAlias = false))
        }
        val picture = recorder.finishRecordingAsPicture()
        return if (serialized) requireNotNull(Picture.fromByteArray(picture.toByteArray())) else picture
    }
    private fun assertPerspectiveClipRefusalAndRecover(record: (Surface) -> Unit) {
        val refused = Surface(16, 16).also(record)
        val sentinel = UByteArray(16 * 16 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            refused.readPixels(RectF32.ofLTRB(0f, 0f, 16f, 16f), sentinel)
        }
        assertTrue(failure.message.orEmpty().startsWith("unsupported_transform:Perspective"), failure.message)
        assertContentEquals(before, sentinel)
        refused.discardRecordedOperations()
        refused.canvas {
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(red, antiAlias = false))
            drawPicture(projectiveRectPicture())
        }
        val recovered = refused.renderAndRepeat()
        assertBlueComposite(recovered.pixels, 16, 5, 5)
    }
    private fun hardPictureSurface(): Surface = pictureSurface(16, 16, projectiveRectPicture())
    private fun pictureSurface(width: Int, height: Int, picture: Picture): Surface = Surface(width, height).also { target -> target.canvas {
        drawRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat()), Paint(red, antiAlias = false)); drawPicture(picture)
    } }
    private fun recorded(draw: org.graphiks.kanvas.canvas.Canvas.() -> Unit): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(bounds).draw()
    }.finishRecordingAsPicture()
    private fun Surface.renderAndRepeat(): RenderResult {
        val first = render(); assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0, "native Picture frame must dispatch")
        val second = render(); assertTrue(second.isClean, second.diagnostics.summary()); assertContentEquals(first.pixels, second.pixels)
        return first
    }
    private fun analyticPictureBudgetI64(): Long = listOf(1_024L, 4_096L, 16L, 16L, 16L).fold(0L, Math::addExact)
    private fun refusalDiagnostic(label: String): String = when (label) {
        "horizon" -> "w6b.filter.invalid_bounds"
        "nan" -> "non-finite-value"
        "rgba16" -> "unsupported.surface.composition.target-format"
        "budget1" -> "w3.budget.frame_local_exceeded"
        else -> error("unknown refusal $label")
    }
    private fun assertBlueComposite(pixels: UByteArray, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        val actual = pixels.copyOfRange(offset, offset + 4)
        assertTrue(actual[0].toInt() in 181..183 && actual[1] == 0.toUByte() &&
            actual[2].toInt() in 192..194 && actual[3] == 255.toUByte(), "pixel $x,$y=${actual.joinToString()}")
    }
    private fun assertBlueOverGreen(pixels: UByteArray, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        val actual = pixels.copyOfRange(offset, offset + 4)
        assertTrue(actual[0] == 0.toUByte() && actual[1].toInt() in 181..183 &&
            actual[2].toInt() in 192..194 && actual[3] == 255.toUByte(), "pixel $x,$y=${actual.joinToString()}")
    }
    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * width + x) * 4
        assertContentEquals(ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()), pixels.copyOfRange(offset, offset + 4), "pixel $x,$y")
    }
}
