@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Public causal witness for the W7 deferred-AA Picture occurrence seam. */
class W7AaDeferredPictureSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @JvmStatic
        fun pictureCells(): List<Array<Any>> = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER,
            BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT,
            BlendMode.DST_OUT, BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR,
            BlendMode.PLUS,
        ).flatMap { mode ->
            listOf("path", "rect").flatMap { geometry ->
                listOf(false, true).map { layer ->
                    arrayOf<Any>("$mode/$geometry/${if (layer) "Picture-layer" else "Picture-root"}", mode, geometry, layer)
                }
            }
        }
    }

    @Test
    fun `Picture root replays an AA PLUS child through native Render and Readback`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawPath(
                Path().apply { moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close() },
                Paint(ColorARGB.Red, blendMode = BlendMode.PLUS, antiAlias = true),
            )
        }.finishRecordingAsPicture()
        val surface = Surface(7, 7).also { target ->
            target.canvas {
                drawRect(bounds, Paint(ColorARGB.Black, antiAlias = false))
                drawPicture(picture)
            }
        }

        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0, "Picture replay must dispatch")
        assertPixel(first.pixels, 7, 2, 2, 255, 0, 0, 255)
        assertPixel(first.pixels, 7, 6, 6, 0, 0, 0, 255)

        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun `one serialized Picture replays at two translations over distinct destinations`() {
        val source = W5bBlendCpuOracle.Draw(ColorARGB.of(192, 0, 0, 0), 1f, BlendMode.DST_OUT)
        val left = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val right = W5bBlendCpuOracle.Draw(ColorARGB.Black, 1f, BlendMode.SRC_OVER)
        val recorded = PictureRecorder().also { recorder -> recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
            .drawPath(Path().apply { moveTo(0f, 0f); lineTo(4f, 0f); lineTo(0f, 4f); close() },
                Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32), blendMode = BlendMode.DST_OUT, antiAlias = true))
        }.finishRecordingAsPicture()
        val picture = requireNotNull(Picture.fromByteArray(recorded.toByteArray()))
        val leftFull = expectedPixel(source, left, BlendMode.DST_OUT, 1f)
        val leftOutside = expectedPixel(source, left, BlendMode.DST_OUT, 0f)
        val rightFull = expectedPixel(source, right, BlendMode.DST_OUT, 1f)
        val rightOutside = expectedPixel(source, right, BlendMode.DST_OUT, 0f)
        // These literals are derived before building the Surface.  A doubled translation
        // misses its full pixels, while a stale left destination cannot satisfy the right
        // full/outside pair.
        requireDisjoint("translated left full/outside", leftFull, leftOutside)
        requireDisjoint("translated right full/outside", rightFull, rightOutside)
        requireDisjoint("translated distinct destinations", leftFull, rightFull)
        val surface = Surface(11, 5).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 11f, 5f), Paint(left.color, antiAlias = false))
            drawRect(RectF32.ofLTRB(6f, 0f, 11f, 5f), Paint(right.color, antiAlias = false))
            save(); translate(1f, 1f); drawPicture(picture); restore()
            save(); translate(7f, 1f); drawPicture(picture); restore()
        } }
        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0)
        assertExpected("translated left full", leftFull, first.pixels, 11, 2, 2)
        assertExpected("translated right full", rightFull, first.pixels, 11, 8, 2)
        assertExpected("translated left unaffected", leftOutside, first.pixels, 11, 4, 4)
        assertExpected("translated right unaffected", rightOutside, first.pixels, 11, 10, 4)
        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun `Picture layer uses its nonzero origin and hard scissor`() {
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val destination = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().destination
        val full = expectedPixel(source, destination, BlendMode.PLUS, 1f)
        val edge = expectedPixel(source, destination, BlendMode.PLUS, 128f / 255f)
        val outside = expectedPixel(source, destination, BlendMode.PLUS, 0f)
        requireDisjoint("layer origin full/outside", full, outside)
        requireDisjoint("layer origin edge/outside", edge, outside)
        val picture = PictureRecorder().also { recorder -> recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
            .drawPath(Path().apply { moveTo(0f, 0f); lineTo(4f, 0f); lineTo(0f, 4f); close() },
                Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32), blendMode = BlendMode.PLUS, antiAlias = true))
        }.finishRecordingAsPicture()
        val surface = Surface(10, 9).also { target -> target.canvas {
            saveLayer(RectF32.ofLTRB(5f, 4f, 9f, 8f))
            drawRect(RectF32.ofLTRB(5f, 4f, 9f, 8f), Paint(destination.color, antiAlias = false))
            save(); clipRect(RectF32.ofLTRB(6f, 5f, 8f, 7f), antiAlias = false); translate(5f, 4f); drawPicture(picture); restore()
            restore()
        } }
        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0)
        assertExpected("layer origin full", full, first.pixels, 10, 6, 5)
        assertExpected("layer origin edge", edge, first.pixels, 10, 7, 5)
        assertExpected("layer scissor outside", outside, first.pixels, 10, 8, 5)
        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun `Picture even odd fill and perspective refusal remain distinct`() {
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val destination = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().destination
        val full = expectedPixel(source, destination, BlendMode.PLUS, 1f)
        val outside = expectedPixel(source, destination, BlendMode.PLUS, 0f)
        val picture = PictureRecorder().also { recorder -> recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 7f, 7f))
            .drawPath(Path().apply { fillType = FillType.EVEN_ODD; addRect(RectF32.ofLTRB(0f, 0f, 6f, 6f)); addRect(RectF32.ofLTRB(2f, 2f, 4f, 4f)) },
                Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32), blendMode = BlendMode.PLUS, antiAlias = true))
        }.finishRecordingAsPicture()
        val admitted = Surface(7, 7).also { target -> target.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(destination.color, antiAlias = false)); drawPicture(picture) } }
        val result = admitted.render()
        assertTrue(result.isClean, result.diagnostics.summary())
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
        assertTrue(result.stats.opsDispatched > 0)
        assertExpected("even-odd filled", full, result.pixels, 7, 1, 1)
        assertExpected("even-odd hole", outside, result.pixels, 7, 3, 3)
        val repeated = admitted.render()
        assertTrue(repeated.isClean, repeated.diagnostics.summary())
        assertContentEquals(result.pixels, repeated.pixels)
        val refused = Surface(7, 7).also { target -> target.canvas { concat(Matrix3x3F32(persp0 = .125f)); drawPicture(picture) } }
        val sentinel = UByteArray(7 * 7 * 4) { 0x5au }
        val before = sentinel.copyOf()
        assertFailsWith<IllegalStateException> { refused.readPixels(RectF32.ofLTRB(0f, 0f, 7f, 7f), sentinel) }
        assertContentEquals(before, sentinel)
        refused.discardRecordedOperations(); refused.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(ColorARGB.Blue, antiAlias = false)) }
        val recovered = refused.render()
        assertTrue(recovered.isClean, recovered.diagnostics.summary())
        assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            recovered.nativeEvidenceScopeKinds.toString())
        assertPixel(recovered.pixels, 7, 3, 3, 0, 0, 255, 255)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pictureCells")
    fun `Picture AA Porter Duff matrix has an independently fixed interior and exterior`(
        label: String,
        mode: BlendMode,
        geometry: String,
        layer: Boolean,
    ) {
        // Freeze an independent fractional S/D oracle before constructing the public Picture.
        // In particular, D-only Porter-Duff modes must not collapse to an opaque-background
        // omission witness.
        val source = when (mode) {
            BlendMode.CLEAR, BlendMode.SRC -> W5bBlendCpuOracle.Draw(ColorARGB.Transparent, 1f, mode)
            BlendMode.PLUS -> W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
            BlendMode.SRC_ATOP -> W5bBlendCpuOracle.Draw(ColorARGB.of(192, 255, 0, 0), 1f, mode)
            else -> W5bBlendCpuOracle.Draw(ColorARGB.of(192, 0, 0, 0), 1f, mode)
        }
        val destination = if (mode == BlendMode.PLUS) W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().destination
        else W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val covered = expectedPixel(source, destination, mode, 1f)
        val destinationOnly = expectedPixel(source, destination, mode, 0f)
        val edge = if (mode == BlendMode.PLUS) expectedPixel(source, destination,
            mode, if (geometry == "path") 128f / 255f else .5f) else null
        requireBounded("$label covered", covered)
        requireBounded("$label destination", destinationOnly)
        if (mode != BlendMode.DST) requireDisjoint("$label covered/destination", covered, destinationOnly)
        edge?.let { requireDisjoint("$label edge/destination", it, destinationOnly) }
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                val shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32)
                val paint = if (mode == BlendMode.SRC_OVER) Paint(shader = shader, antiAlias = true)
                else Paint(shader = shader, blendMode = mode, antiAlias = true)
                if (geometry == "path") {
                    drawPath(Path().apply { moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close() }, paint)
                } else {
                    drawRect(RectF32.ofLTRB(1.5f, 1f, 4.5f, 5f), paint)
                }
            }
        }.finishRecordingAsPicture()
        val surface = Surface(7, 7).also { target ->
            target.canvas {
                if (layer) {
                    // The child layer, rather than the eventual root restore, owns D.
                    // This keeps CLEAR/DST_IN/etc. observable after a SrcOver restore onto
                    // a transparent root and makes the Picture replay witness independent
                    // of the former opaque-root fixture.
                    saveLayer()
                    drawRect(bounds, Paint(destination.color, antiAlias = false))
                    drawPicture(picture)
                    restore()
                } else {
                    drawRect(bounds, Paint(destination.color, antiAlias = false))
                    drawPicture(picture)
                }
            }
        }
        val first = surface.render()
        assertTrue(first.isClean, "$label ${first.diagnostics.summary()}")
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), "$label ${first.nativeEvidenceScopeKinds}")
        assertTrue(first.stats.opsDispatched > 0, "$label must dispatch")
        assertExpected(label, covered, first.pixels, 7, 2, 2)
        assertExpected(label, destinationOnly, first.pixels, 7, 6, 6)
        edge?.let { assertExpected(label, it, first.pixels, 7,
            if (geometry == "path") 4 else 1, 1) }
        val second = surface.render()
        assertTrue(second.isClean, "$label repeat ${second.diagnostics.summary()}")
        assertContentEquals(first.pixels, second.pixels)
    }

    private fun expectedPixel(source: W5bBlendCpuOracle.Draw, destination: W5bBlendCpuOracle.Draw,
        mode: BlendMode, coverage: Float): WgslFloatEnvelopeV1Oracle.DrawResult {
        if (mode == BlendMode.PLUS) return W5bBlendCpuOracle.point(source, destination, coverage)
        val destinationAttachment = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(destination.color, destination.opacityF32),
        ))
        return WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
            WgslFloatEnvelopeV1Oracle.solidLinearPremul(source.color, source.opacityF32),
            destinationAttachment, mode, coverage,
        )
    }

    private fun requireBounded(label: String, result: WgslFloatEnvelopeV1Oracle.DrawResult):
        WgslFloatEnvelopeV1Oracle.DrawResult.Bounded = result as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
            ?: error("$label must be bounded: $result")

    private fun requireDisjoint(label: String, first: WgslFloatEnvelopeV1Oracle.DrawResult,
        second: WgslFloatEnvelopeV1Oracle.DrawResult) {
        val left = requireBounded(label, first).channels
        val right = requireBounded(label, second).channels
        check(left.zip(right).any { (a, b) -> a.intersect(b).isEmpty() }) { "$label must differ" }
    }

    private fun assertExpected(label: String, expected: WgslFloatEnvelopeV1Oracle.DrawResult,
        pixels: UByteArray, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        try {
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offset, offset + 4))
        } catch (failure: Throwable) {
            throw AssertionError("$label pixel $x,$y", failure)
        }
    }

    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int,
        r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * width + x) * 4
        assertContentEquals(ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4))
    }
}
