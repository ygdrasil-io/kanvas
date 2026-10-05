@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.skia.gm.path.Crbug691386Gm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private data class ClosedArcPixelSample(val label: String, val x: Int, val y: Int, val rgba: List<Int>)

private val expectedClosedArcPixels = listOf(
    ClosedArcPixelSample("diameter-upper", 120, 119, listOf(0, 0, 0, 255)),
    ClosedArcPixelSample("diameter-lower", 120, 120, listOf(0, 0, 0, 255)),
    ClosedArcPixelSample("lower-arc-inner", 120, 215, listOf(0, 0, 0, 255)),
    ClosedArcPixelSample("lower-arc-center", 120, 216, listOf(0, 0, 0, 255)),
    ClosedArcPixelSample("diameter-outside", 120, 115, listOf(255, 255, 255, 255)),
    ClosedArcPixelSample("arc-inside", 120, 210, listOf(255, 255, 255, 255)),
    ClosedArcPixelSample("arc-outside", 120, 220, listOf(255, 255, 255, 255)),
    ClosedArcPixelSample("background-origin", 0, 0, listOf(255, 255, 255, 255)),
    ClosedArcPixelSample("background-corner", 255, 255, listOf(255, 255, 255, 255)),
)

private fun assertClosedArcNativePixels(label: String, actual: RenderResult) {
    assertTrue(actual.isClean, "$label diagnostics=${actual.diagnostics.summary()}")
    assertTrue(actual.diagnostics.isEmpty, "$label diagnostics=${actual.diagnostics.summary()}")
    assertEquals(0, actual.stats.opsRefused, "$label stats=${actual.stats}")
    assertTrue(actual.stats.opsDispatched > 0, "$label stats=${actual.stats}")
    assertTrue(
        actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
        "$label native scopes=${actual.nativeEvidenceScopeKinds}",
    )
    assertEquals(PixelFormat.RGBA8, actual.format, "$label pixel format")

    val observed = expectedClosedArcPixels.map { sample ->
        val offset = (sample.y * actual.width + sample.x) * 4
        sample to listOf(
            actual.pixels[offset].toInt(),
            actual.pixels[offset + 1].toInt(),
            actual.pixels[offset + 2].toInt(),
            actual.pixels[offset + 3].toInt(),
        )
    }
    assertTrue(
        observed.all { (sample, rgba) -> sample.rgba == rgba },
        "$label samples (label, point, expectedRGBA, actualRGBA): " + observed.joinToString {
            (sample, rgba) -> "${sample.label}@(${sample.x},${sample.y}) expected=${sample.rgba} actual=$rgba"
        },
    )
}

/** Native Surface pixels comparing the registered GM path with its literal device contour. */
class ScaledClosedArcGmSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun scaledGmKeepsDiameterAndLowerArcPixels() {
        val gm = Crbug691386Gm()
        val surface = Surface(256, 256, config = gm.compositionConfig())
        surface.canvas().drawRect(
            RectF32.ofLTRB(0f, 0f, 256f, 256f),
            Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false),
        )
        val gmCanvas = GmCanvas(surface.canvas(), 256, 256)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, 256, 256)
        assertClosedArcNativePixels("scaled GM", surface.render())
    }

    @Test
    fun deviceGmCanvasKeepsDiameterAndLowerArcPixels() {
        val gm = Crbug691386Gm()
        val surface = Surface(256, 256, config = gm.compositionConfig())
        surface.canvas().drawRect(
            RectF32.ofLTRB(0f, 0f, 256f, 256f),
            Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false),
        )
        val gmCanvas = GmCanvas(surface.canvas(), 256, 256)
        gmCanvas.drawPath(
            Path {
                moveTo(24f, 120f)
                arcTo(96f, 96f, 0f, false, false, 216f, 120f)
                close()
            },
            Paint(
                color = ColorARGB.Black,
                style = PaintStyle.STROKE,
                strokeWidth = 2.4f,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                strokeMiter = 4f,
                antiAlias = true,
            ),
        )
        assertClosedArcNativePixels("device GmCanvas", surface.render())
    }
}
