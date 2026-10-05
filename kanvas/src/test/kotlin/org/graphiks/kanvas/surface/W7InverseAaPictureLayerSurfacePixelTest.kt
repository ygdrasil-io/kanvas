@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Public W7 witness for an inverse-AA Picture replayed into a nonzero-origin plain layer. */
class W7InverseAaPictureLayerSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @JvmStatic
        fun pictureForms(): List<Array<Any>> = listOf(false, true).map { wire ->
            arrayOf<Any>(if (wire) "wire" else "memory", wire)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pictureForms")
    fun `inverse AA Picture preserves target local coordinates in a nonzero layer`(
        label: String,
        wire: Boolean,
    ) {
        val picture = inverseLayerPicture(wire)
        val surface = Surface(18, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 18f, 16f), Paint(ColorARGB.Blue, antiAlias = false))
            saveLayer(RectF32.ofLTRB(5f, 4f, 13f, 12f))
            translate(5f, 4f)
            drawPicture(picture)
            restore()
        } }

        val first = surface.render()
        assertNativeLayerReplay(label, first)
        for (yI32 in 0 until 16) for (xI32 in 0 until 18) {
            val localXI32 = xI32 - 5
            val localYI32 = yI32 - 4
            val insideCell = localXI32 in 0..7 && localYI32 in 0..7
            val inHole = localXI32 in 2..5 && localYI32 in 2..5
            val expected = if (insideCell && !inHole) ColorARGB.White else ColorARGB.Blue
            assertPixel(first.pixels, 18, xI32, yI32, expected, "$label/$xI32,$yI32")
        }

        val second = surface.render()
        assertNativeLayerReplay("$label/repeat", second)
        assertContentEquals(first.pixels, second.pixels, "$label retained Surface frame must be byte-identical")
    }

    private fun inverseLayerPicture(wire: Boolean): Picture {
        val recorded = PictureRecorder().also { recorder -> recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).apply {
            clipRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), antiAlias = false)
            drawPath(Path().apply {
                addRect(RectF32.ofLTRB(2f, 2f, 6f, 6f))
                fillType = FillType.INVERSE_WINDING
            }, Paint(ColorARGB.White, blendMode = BlendMode.SRC_OVER, antiAlias = true))
        } }.finishRecordingAsPicture()
        return if (wire) requireNotNull(Picture.fromByteArray(recorded.toByteArray())) else recorded
    }

    private fun assertNativeLayerReplay(label: String, result: RenderResult) {
        val trace = "$label diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.isClean, trace)
        assertTrue(result.diagnostics.isEmpty, trace)
        assertEquals(setOf("Render", "LayerComposite", "Readback"), result.nativeEvidenceScopeKinds.toSet(), trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertEquals(0, result.stats.opsRefused, trace)
    }

    private fun assertPixel(
        pixels: UByteArray,
        widthI32: Int,
        xI32: Int,
        yI32: Int,
        color: ColorARGB,
        label: String,
    ) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertContentEquals(
            ubyteArrayOf(
                color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte(),
            ),
            pixels.copyOfRange(offsetI32, offsetI32 + 4),
            "$label pixel",
        )
    }
}
