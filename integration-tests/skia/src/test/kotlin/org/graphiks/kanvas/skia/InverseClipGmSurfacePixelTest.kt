@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.gm.clip.InverseClipGm
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Public native-pixel witness for Skia's pinned inverse clip GM port. */
class InverseClipGmSurfacePixelTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun inverseClipPreservesWhiteInteriorAndBlueExterior() {
        // A full-frame clip or inverted draw would exchange these two observable regions.
        val gm = InverseClipGm()
        val actual = renderInverseClipGm(gm)

        listOf(195 to 197, 195 to 100, 195 to 300, 100 to 197, 300 to 197).forEach { (x, y) ->
            assertPixel(actual, x, y, 255, 255, 255, 255)
        }
        listOf(0 to 0, 399 to 399, 0 to 197, 399 to 197, 195 to 0, 195 to 399).forEach { (x, y) ->
            assertPixel(actual, x, y, 0, 0, 255, 255)
        }
        assertTrue(
            actual.pixels.indices.step(4).any { offset ->
                actual.pixels[offset].toInt() in 1..254 &&
                    actual.pixels[offset + 1].toInt() == actual.pixels[offset].toInt() &&
                    actual.pixels[offset + 2].toInt() == 255 &&
                    actual.pixels[offset + 3].toInt() == 255
            },
            "expected a partially covered white/blue clip edge",
        )
        assertCleanNativeRender(actual)

        val repeated = renderInverseClipGm(gm)
        assertCleanNativeRender(repeated)
        assertTrue(actual.pixels.contentEquals(repeated.pixels), "repeated ${gm.name} bytes differ")
    }
}

private fun renderInverseClipGm(gm: SkiaGm): RenderResult {
    val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
    val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)
    canvas.drawRect(
        RectF32(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
        Paint(color = ColorARGB.White, antiAlias = false),
    )
    gm.onOnceBeforeDraw(canvas)
    gm.draw(canvas, gm.width, gm.height)
    return surface.render()
}

private fun assertCleanNativeRender(actual: RenderResult) {
    assertTrue(
        actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
        actual.nativeEvidenceScopeKinds.toString(),
    )
    assertTrue(actual.stats.opsDispatched > 0, "expected native dispatch")
    assertEquals(0, actual.stats.opsRefused, actual.diagnostics.toString())
    assertTrue(actual.diagnostics.isEmpty, actual.diagnostics.toString())
}

private fun assertPixel(actual: RenderResult, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
    val offset = (y * actual.width + x) * 4
    val observed = intArrayOf(
        actual.pixels[offset].toInt(),
        actual.pixels[offset + 1].toInt(),
        actual.pixels[offset + 2].toInt(),
        actual.pixels[offset + 3].toInt(),
    )
    assertTrue(
        observed.contentEquals(intArrayOf(red, green, blue, alpha)),
        "pixel ($x,$y) was ${observed.toList()}",
    )
}
