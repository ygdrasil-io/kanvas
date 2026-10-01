@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Public native regression coverage for finite, nonsingular affine W7 Rect projection. */
class W7AffineRectSurfacePixelTest {
    companion object {
        private val shear = Matrix3x3F32(kx = .25f)
        private val bounds = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        private val sourceBounds = RectF32.ofLTRB(1f, 1f, 4f, 4f)
        private val opaqueGrey = ColorARGB.of(255, 64, 64, 64)
        private val opaqueBlack = ColorARGB.Black

        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @JvmStatic
        fun affineContexts(): List<Array<Any>> = listOf(
            BlendMode.DST_OUT, BlendMode.PLUS, BlendMode.SRC_OVER,
        ).flatMap { mode ->
            listOf("root-shear", "plain-layer-shear", "picture-root-shear", "picture-layer-shear").map { context ->
                arrayOf<Any>("$mode/$context", mode, context)
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("affineContexts")
    fun `finite affine AA Rect admission is observable in each public owner`(
        label: String,
        mode: BlendMode,
        context: String,
    ) {
        // These full-coverage expectations are fixed before a Surface exists.
        val destination = if (mode == BlendMode.DST_OUT) opaqueGrey else opaqueBlack
        val expectedInterior = if (mode == BlendMode.DST_OUT) ColorARGB.Transparent else opaqueGrey
        val expectedExterior = destination
        require(expectedInterior != expectedExterior) { "$label must distinguish draw from omission" }
        val blend = Paint(opaqueGrey, blendMode = mode, antiAlias = true)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(sourceBounds, blend)
        }.finishRecordingAsPicture()
        val surface = Surface(8, 8).also { target -> target.canvas {
            when (context) {
                "root-shear" -> {
                    drawRect(bounds, Paint(destination, blendMode = BlendMode.SRC_OVER, antiAlias = false))
                    save(); concat(shear); drawRect(sourceBounds, blend); restore()
                }
                "plain-layer-shear" -> {
                    saveLayer()
                    // D belongs to the child target; root stays transparent on restore.
                    drawRect(bounds, Paint(destination, blendMode = BlendMode.SRC_OVER, antiAlias = false))
                    save(); concat(shear); drawRect(sourceBounds, blend); restore()
                    restore()
                }
                "picture-root-shear" -> {
                    drawRect(bounds, Paint(destination, blendMode = BlendMode.SRC_OVER, antiAlias = false))
                    save(); concat(shear); drawPicture(picture); restore()
                }
                "picture-layer-shear" -> {
                    saveLayer()
                    drawRect(bounds, Paint(destination, blendMode = BlendMode.SRC_OVER, antiAlias = false))
                    save(); concat(shear); drawPicture(picture); restore()
                    restore()
                }
                else -> error("unknown affine Rect context: $context")
            }
        } }

        val first = surface.render()
        assertTrue(first.isClean, "$label ${first.diagnostics.summary()}")
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            "$label ${first.nativeEvidenceScopeKinds}")
        assertTrue(first.stats.opsDispatched > 0, "$label must dispatch")
        assertPixel(first.pixels, 8, 2, 2, expectedInterior)
        assertPixel(first.pixels, 8, 6, 6, expectedExterior)
        val second = surface.render()
        assertTrue(second.isClean, "$label repeat ${second.diagnostics.summary()}")
        assertContentEquals(first.pixels, second.pixels, "$label repeat pixels")
    }

    @Test
    fun `Picture layer affine DST_OUT observes resolved partial coverage`() {
        // x_left = 1 + .25*y.  With the established 4x pattern (.375,.125),
        // (.875,.375), (.125,.625), (.625,.875), only the second sample at (1,2)
        // lies inside. Resolve therefore stores one-fourth coverage as UNORM8 64/255.
        val source = ColorARGB.Black
        val destination = ColorARGB.of(64, 0, 0, 0)
        val edge = expectedDstOut(source, destination, 64f / 255f)
        val exterior = expectedDstOut(source, destination, 0f)
        requireDisjoint("affine Picture edge/exterior", edge, exterior)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(sourceBounds,
                Paint(source, blendMode = BlendMode.DST_OUT, antiAlias = true))
        }.finishRecordingAsPicture()
        val surface = Surface(8, 8).also { target -> target.canvas {
            saveLayer()
            drawRect(bounds, Paint(destination, blendMode = BlendMode.SRC_OVER, antiAlias = false))
            save(); concat(shear); drawPicture(picture); restore()
            restore()
        } }
        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0, "affine Picture edge must dispatch")
        assertPixel(first.pixels, 8, 2, 2, ColorARGB.Transparent)
        assertPixel(first.pixels, 8, 6, 6, destination)
        assertExpected("affine Picture partial edge", edge, first.pixels, 8, 1, 2)
        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun `root AA Rect PLUS remains admitted with a simple translation and hard SrcOver background`() {
        val surface = Surface(8, 8).also { target -> target.canvas {
            drawRect(bounds, Paint(opaqueBlack, blendMode = BlendMode.SRC_OVER, antiAlias = false))
            save(); translate(1f, 0f)
            drawRect(sourceBounds, Paint(opaqueGrey, blendMode = BlendMode.PLUS, antiAlias = true))
            restore()
        } }
        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0, "translated root Rect must dispatch")
        assertPixel(first.pixels, 8, 3, 2, opaqueGrey)
        assertPixel(first.pixels, 8, 7, 7, opaqueBlack)
        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertContentEquals(first.pixels, second.pixels)
    }

    private fun expectedDstOut(source: ColorARGB, destination: ColorARGB,
        coverage: Float): WgslFloatEnvelopeV1Oracle.DrawResult =
        WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
            WgslFloatEnvelopeV1Oracle.solidLinearPremul(source, 1f),
            requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(destination, 1f))),
            BlendMode.DST_OUT,
            coverage,
        )

    private fun requireDisjoint(label: String, first: WgslFloatEnvelopeV1Oracle.DrawResult,
        second: WgslFloatEnvelopeV1Oracle.DrawResult) {
        val left = requireBounded(label, first).channels
        val right = requireBounded(label, second).channels
        check(left.zip(right).any { (a, b) -> a.intersect(b).isEmpty() }) { "$label must differ" }
    }

    private fun requireBounded(label: String, result: WgslFloatEnvelopeV1Oracle.DrawResult):
        WgslFloatEnvelopeV1Oracle.DrawResult.Bounded = result as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
            ?: error("$label must be bounded: $result")

    private fun assertExpected(label: String, expected: WgslFloatEnvelopeV1Oracle.DrawResult,
        pixels: UByteArray, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        try {
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offset, offset + 4))
        } catch (failure: Throwable) {
            throw AssertionError("$label pixel $x,$y", failure)
        }
    }

    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int, expected: ColorARGB) {
        val offset = (y * width + x) * 4
        assertContentEquals(
            ubyteArrayOf(expected.red.toUByte(), expected.green.toUByte(), expected.blue.toUByte(), expected.alpha.toUByte()),
            pixels.copyOfRange(offset, offset + 4),
            "pixel $x,$y",
        )
    }
}
