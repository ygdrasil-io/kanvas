@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval as I

class W5eImageFamiliesSurfacePixelTest {
    @Test fun imageNinePreservesCornersAndStretchesEdgesAndCenter() {
        // A single stretched center patch loses eight distinct source colors.
        val axis = listOf(0, 1, 1, 1, 1, 1, 2)
        val expected = expectations(axis.flatMap { row -> axis.map { column -> row * 3 + column } })
        val surface = Surface(7, 7, config = RenderConfig(preparedImageRoute = PreparedImageRoute.BOUNDED_NEAREST_1_TO_1))
        surface.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNineHandlesSmallAndFlippedDestinations() {
        // A destination smaller than its two borders compresses both borders equally;
        // the center disappears. Signed destination extents reverse their order.
        val cases = listOf(
            RectF32.ofLTRB(0f, 0f, 1.75f, 1.75f) to listOf(0, 2, 6, 8),
            RectF32.ofLTRB(1.75f, 0f, 0f, 1.75f) to listOf(2, 0, 8, 6),
            RectF32.ofLTRB(1.75f, 1.75f, 0f, 0f) to listOf(8, 6, 2, 0),
        )
        for ((destination, indices) in cases) {
            val expected = expectations(indices)
            val surface = Surface(2, 2)
            surface.canvas { drawImageNine(image(), center(), destination, paint()) }
            val result = surface.render()
            assertPixels(expected, result.pixels)
            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun imageNinePaintAlphaAndBlendApplyOnce() {
        // Per-cell public draws, duplicate opacity, or tinting RGBA by paint RGB all
        // change these independently derived source-over / source replacement pixels.
        val sourcePaint = paint().copy(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f))
        val axis = listOf(0, 1, 1, 2)
        for (mode in listOf(BlendMode.SRC, BlendMode.SRC_OVER, BlendMode.DIFFERENCE)) {
            val expected = List(16) { index ->
                if (index == 5) expectedLinear(listOf(0f, 1f, 0f, 1f)) else {
                    val sourceIndex = axis[index / 4] * 3 + axis[index % 4]
                    val alpha = I.input(sourcePaint.color.alphaNormalized)
                    val source = List(3) { channel -> WgslFloatEnvelopeV1Oracle.gradientMultiply(
                        WgslFloatEnvelopeV1Oracle.imageUnorm8(bytes[sourceIndex * 4 + channel].toInt() and 255), alpha) }
                    val components = Array(4) { channel ->
                        val value = if (channel == 3) alpha else source[channel]
                        if (mode == BlendMode.DIFFERENCE && channel == 3) I.ONE
                        else if (mode == BlendMode.DIFFERENCE && channel == 2) WgslFloatEnvelopeV1Oracle.gradientSubtract(I.ONE, value)
                        else if (mode == BlendMode.SRC || channel !in listOf(2, 3)) value else
                            WgslFloatEnvelopeV1Oracle.gradientAdd(value, WgslFloatEnvelopeV1Oracle.gradientSubtract(I.ONE, alpha))
                    }
                    bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(components))
                }
            }
            val surface = Surface(4, 4)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint().copy(color = ColorARGB.Blue))
                drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 4f, 4f), sourcePaint.copy(blendMode = mode))
                drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), paint().copy(color = ColorARGB.Green))
            }
            val result = surface.render()
            assertPixels(expected, result.pixels)
            assertEquals(3, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun imageNineTransformAndClipMatchPublicSemantics() {
        // A quarter turn swaps source axes; a device clip removes the final row.
        val axis = listOf(0, 1, 1, 1, 1, 1, 2)
        val expected = List(81) { index ->
            val x = index % 9
            val y = index / 9
            if (x in 1..7 && y in 1..6) expectations(listOf(axis[7 - x] * 3 + axis[y - 1])).single()
            else expectedLinear(listOf(0f, 0f, 0f, 0f))
        }
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 9f, 9f))
        canvas.concat(Matrix3x3F32(sx = 0f, kx = -1f, tx = 8f, ky = 1f, sy = 0f, ty = 1f))
        canvas.drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint())
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(9, 9)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 8f, 7f), antiAlias = false)
            picture.playback(this)
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)

        // The left/top fragment centers are outside the destination yet have 1/4
        // analytic outer coverage. Internal bands must not acquire their own AA.
        val probes = listOf(
            Triple(0, 3, listOf(.25f, .25f, 0f, .25f)),
            Triple(3, 0, listOf(0f, .25f, 0f, .25f)),
            Triple(6, 3, listOf(0f, .75f, .75f, .75f)),
            Triple(1, 3, listOf(1f, 1f, 0f, 1f)),
            Triple(2, 3, listOf(1f, 0f, 1f, 1f)),
        ).map { (x, y, rgba) -> (y * 8 + x) to expectedLinear(rgba) }
        val fractional = Surface(8, 8)
        fractional.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(.75f, .75f, 6.75f, 6.75f),
            paint().copy(antiAlias = true)) }
        val fractionalResult = fractional.render()
        for ((index, value) in probes) WgslFloatEnvelopeV1Oracle.assertAdmits(value,
            fractionalResult.pixels.copyOfRange(index * 4, index * 4 + 4))
        assertEquals(1, fractionalResult.stats.opsDispatched)
    }

    @Test fun imageNineEmptyDestinationAndDstRetainOriginalOperationCounts() {
        val expected = List(4) { expectedLinear(listOf(0f, 0f, 1f, 1f)) }
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), paint().copy(color = ColorARGB.Blue))
            drawImageNine(image(), center(), RectF32.ofLTRB(1f, 0f, 1f, 2f), paint())
            drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 2f, 2f), paint().copy(blendMode = BlendMode.DST))
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNineOnlyEmptyDestinationRetainsOriginalOperation() {
        val expected = List(4) { expectedLinear(listOf(0f, 0f, 0f, 0f)) }
        val surface = Surface(2, 2)
        surface.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(1f, 0f, 1f, 2f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(0, result.stats.drawCallCount)
    }

    @Test fun imageNineClampsCenterAndOmitsEmptySourceCells() {
        val stretchedAxis = listOf(0, 0, 1, 1, 1, 2, 2)
        val clampedExpected = expectations(stretchedAxis.flatMap { row -> stretchedAxis.map { row * 3 + it } })
        val clamped = Surface(7, 7)
        clamped.canvas { drawImageNine(image(), RectF32.ofLTRB(-2f, -3f, 5f, 6f), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint()) }
        assertPixels(clampedExpected, clamped.render().pixels)
        // Empty source center columns leave destination untouched, even for SRC.
        val axis = listOf(0, 1, 1, 1, 1, 1, 2)
        val expected = List(49) { index ->
            val sourceX = when (index % 7) { 0 -> 0; 5 -> 1; 6 -> 2; else -> null }
            if (sourceX == null) expectedLinear(listOf(0f, 0f, 1f, 1f))
            else expectations(listOf(axis[index / 7] * 3 + sourceX)).single()
        }
        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), paint().copy(color = ColorARGB.Blue))
            drawImageNine(image(), RectF32.ofLTRB(1f, 1f, 1f, 2f), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint())
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(2, result.stats.opsDispatched)
    }

    @Test fun imageNineA8PaintChildAndOrdinaryGradientKeepIndependentCoordinates() {
        val maskBytes = byteArrayOf(-1, -1, -1, -1, 127, -1, -1, -1, -1)
        val mask = Image.fromPixels(3, 3, maskBytes, ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        fun gradient(start: Float, left: ColorARGB, right: ColorARGB) = Shader.LinearGradient(
            Point2F32(start, 0f), Point2F32(start + 4f, 0f), listOf(GradientStop(0f, left), GradientStop(.5f, left),
                GradientStop(.5f, right), GradientStop(1f, right)))
        val alphaPaint = ColorARGB.fromRGBA(0f, 1f, 0f, .5f)
        val axis = listOf(0, 1, 1, 2)
        val expected = List(48) { index ->
            val x = index % 12
            val y = index / 12
            if (x in 4..7) expectedLinear(if (x < 6) listOf(0f, 0f, 1f, 1f) else listOf(1f, 1f, 0f, 1f))
            else {
                val localX = x % 4
                val color = if (x >= 8) alphaPaint else if (localX < 2) ColorARGB.fromRGBA(1f, 0f, 0f, .5f)
                    else ColorARGB.fromRGBA(0f, 1f, 0f, .5f)
                bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.ALPHA_8, AlphaType.PREMUL, ColorSpace.SRGB,
                    byteArrayOf(maskBytes[axis[y] * 3 + axis[localX]]), paintColor = color))
            }
        }
        val surface = Surface(12, 4)
        surface.canvas {
            drawImageNine(mask, center(), RectF32.ofLTRB(0f, 0f, 4f, 4f), paint().copy(color = alphaPaint,
                shader = gradient(0f, ColorARGB.Red, ColorARGB.Green)))
            drawRect(RectF32.ofLTRB(4f, 0f, 8f, 4f), paint().copy(shader = gradient(4f, ColorARGB.Blue, ColorARGB.Yellow)))
            drawImageNine(mask, center(), RectF32.ofLTRB(8f, 0f, 12f, 4f), paint().copy(color = alphaPaint))
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNineInvalidCentersRefuseAndRecoverOnSameRuntime() {
        for ((invalid, diagnostic) in listOf(
            RectF32.ofLTRB(2f, 1f, 1f, 2f) to "invalid.material.image.nine-center",
            RectF32.ofLTRB(Float.NaN, 1f, 2f, 2f) to "non-finite-value",
            RectF32.ofLTRB(1f, 1f, Float.POSITIVE_INFINITY, 2f) to "non-finite-value")) for (empty in listOf(false, true)) {
            val surface = Surface(3, 3)
            surface.canvas { drawImageNine(image(), invalid, RectF32.ofLTRB(0f, 0f, if (empty) 0f else 3f, 3f), paint()) }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals(diagnostic, failure.message.orEmpty().substringBefore(':'))
            val expected = expectations((0..8).toList())
            val healthy = Surface(3, 3)
            healthy.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
            assertPixels(expected, healthy.render().pixels)
        }
    }

    @Test fun imageNineNumericAndBudgetRefusalsRecoverOnSameRuntime() {
        for (numeric in listOf(false, true)) {
            val surface = Surface(3, 3, config = if (numeric) RenderConfig() else RenderConfig(frameLocalBudgetBytes = 1L))
            surface.canvas {
                if (numeric) concat(Matrix3x3F32(sx = 1.5f, tx = 1f, persp0 = 1f, persp2 = 0f))
                drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint())
            }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals(if (numeric) "unsupported.material.image.numeric-domain-unbounded" else "resource.material.image.frame-budget",
                failure.message.orEmpty().substringBefore(':'))
            val expected = expectations((0..8).toList())
            val healthy = Surface(3, 3)
            healthy.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
            assertPixels(expected, healthy.render().pixels)
        }
    }

    @Test fun imageNineCaptureRefusalRecoversOnTheSameSurface() {
        val surface = Surface(3, 3, captureLimits = SceneCaptureLimits(maxImageBytesI64 = 36L))
        val oversized = Image.fromPixels(4, 3, ByteArray(48), alphaType = AlphaType.PREMUL)
        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawImageNine(oversized, center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
        }
        assertEquals("scene-recording-image-bytes-exceeded", failure.diagnostic.code.value)
        val expected = expectations((0..8).toList())
        surface.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    private fun expectedLinear(values: List<Float>) = bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(
        values.map(I::input).toTypedArray()))
    private fun bounded(value: WgslFloatEnvelopeV1Oracle.DrawResult): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded {
        require(value is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { value.toString() }
        return value
    }

    private fun expectations(indices: List<Int>) = indices.map { index ->
        W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888, AlphaType.PREMUL, ColorSpace.LINEAR_SRGB,
            bytes.copyOfRange(index * 4, index * 4 + 4)).also {
            require(it is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { it.toString() }
        } as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
    }

    private fun assertPixels(expected: List<WgslFloatEnvelopeV1Oracle.DrawResult.Bounded>, pixels: UByteArray) {
        assertEquals(expected.size * 4, pixels.size)
        expected.forEachIndexed { index, value ->
            try { WgslFloatEnvelopeV1Oracle.assertAdmits(value, pixels.copyOfRange(index * 4, index * 4 + 4)) }
            catch (failure: IllegalArgumentException) { throw IllegalArgumentException("pixel=$index: ${failure.message}", failure) }
        }
    }
    private fun image() = Image.fromPixels(3, 3, bytes, alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
    private fun center() = RectF32.ofLTRB(1f, 1f, 2f, 2f)
    private fun paint() = Paint(color = ColorARGB.Black, antiAlias = false, blendMode = BlendMode.SRC)
    private val bytes = byteArrayOf(
        -1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1,
        -1, -1, 0, -1, -1, 0, -1, -1, 0, -1, -1, -1,
        0, 0, 0, -1, -1, -1, -1, -1, 127, 127, 127, -1,
    )
}
