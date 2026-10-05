@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory

private val expectedClosedArcSamples = listOf(
    // Diameter band is y=118.8..121.2; lower-arc ring is radius 94.8..97.2 about (120,120).
    // These pixel squares lie wholly inside or outside those bands, away from AA boundaries.
    RgbaSample("diameter-upper", 120, 119, listOf(0, 0, 0, 255)),
    RgbaSample("diameter-lower", 120, 120, listOf(0, 0, 0, 255)),
    RgbaSample("lower-arc-inner", 120, 215, listOf(0, 0, 0, 255)),
    RgbaSample("lower-arc-center", 120, 216, listOf(0, 0, 0, 255)),
    RgbaSample("diameter-outside", 120, 115, listOf(255, 255, 255, 255)),
    RgbaSample("arc-inside", 120, 210, listOf(255, 255, 255, 255)),
    RgbaSample("arc-outside", 120, 220, listOf(255, 255, 255, 255)),
    RgbaSample("background-origin", 0, 0, listOf(255, 255, 255, 255)),
    RgbaSample("background-corner", 255, 255, listOf(255, 255, 255, 255)),
)

private fun unitClosedArcSurface(backgroundAntiAlias: Boolean): Surface {
    val surface = Surface(256, 256)
    surface.canvas {
        drawRect(
            RectF32.ofLTRB(0f, 0f, 256f, 256f),
            Paint(color = ColorARGB.of(255, 255, 255, 255), antiAlias = backgroundAntiAlias),
        )
        scale(96f, 96f)
        translate(1.25f, 1.25f)
        drawPath(
            Path().apply {
                moveTo(-1f, 0f)
                arcTo(1f, 1f, 0f, false, false, 1f, 0f)
                close()
            },
            Paint(
                color = ColorARGB.of(255, 0, 0, 0),
                style = PaintStyle.STROKE,
                strokeWidth = 0.025f,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                strokeMiter = 4f,
                antiAlias = true,
            ),
        )
    }
    return surface
}

private fun deviceClosedArcSurface(backgroundAntiAlias: Boolean): Surface {
    val surface = Surface(256, 256)
    surface.canvas {
        drawRect(
            RectF32.ofLTRB(0f, 0f, 256f, 256f),
            Paint(color = ColorARGB.of(255, 255, 255, 255), antiAlias = backgroundAntiAlias),
        )
        drawPath(
            Path().apply {
                moveTo(24f, 120f)
                arcTo(96f, 96f, 0f, false, false, 216f, 120f)
                close()
            },
            Paint(
                color = ColorARGB.of(255, 0, 0, 0),
                style = PaintStyle.STROKE,
                strokeWidth = 2.4f,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                strokeMiter = 4f,
                antiAlias = true,
            ),
        )
    }
    return surface
}

/** Public native Render+Readback witnesses for scaled and device-space closed arcs. */
class W7ScaledClosedArcSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun scaledUnitClosedArcKeepsDiameterAndLowerArcPixels() {
        val expected = expectedClosedArcSamples
        assertClosedArcPixels("scaled unit closed arc", expected, unitClosedArcSurface(backgroundAntiAlias = true).render())
    }

    @Test
    fun deviceClosedArcKeepsDiameterAndLowerArcPixels() {
        val expected = expectedClosedArcSamples
        assertClosedArcPixels("device closed arc", expected, deviceClosedArcSurface(backgroundAntiAlias = true).render())
    }

    @Test
    fun scaledUnitClosedArcOnHardBackgroundKeepsDiameterAndLowerArcPixels() {
        val expected = expectedClosedArcSamples
        assertClosedArcPixels(
            "scaled unit closed arc on hard background",
            expected,
            unitClosedArcSurface(backgroundAntiAlias = false).render(),
        )
    }

    @Test
    fun deviceClosedArcOnHardBackgroundKeepsDiameterAndLowerArcPixels() {
        val expected = expectedClosedArcSamples
        assertClosedArcPixels(
            "device closed arc on hard background",
            expected,
            deviceClosedArcSurface(backgroundAntiAlias = false).render(),
        )
    }
}

private data class RgbaSample(val label: String, val x: Int, val y: Int, val expected: List<Int>)

private fun assertClosedArcPixels(scene: String, expected: List<RgbaSample>, actual: RenderResult) {
    assertTrue(actual.isClean, "$scene diagnostics: ${actual.diagnostics.summary()}")
    assertTrue(actual.diagnostics.isEmpty, "$scene diagnostics: ${actual.diagnostics.summary()}")
    assertEquals(0, actual.stats.opsRefused, "$scene stats=${actual.stats}")
    assertTrue(actual.stats.opsDispatched > 0, "$scene stats=${actual.stats}")
    assertTrue(
        actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
        "$scene native scopes=${actual.nativeEvidenceScopeKinds}",
    )
    assertEquals(PixelFormat.RGBA8, actual.format, "$scene pixel format")

    val sampleBytes = expected.map { sample ->
        val offset = (sample.y * actual.width + sample.x) * 4
        sample to listOf(
            actual.pixels[offset].toInt(),
            actual.pixels[offset + 1].toInt(),
            actual.pixels[offset + 2].toInt(),
            actual.pixels[offset + 3].toInt(),
        )
    }
    assertTrue(
        sampleBytes.all { (sample, bytes) -> sample.expected == bytes },
        "$scene pixel samples: " + sampleBytes.joinToString { (sample, bytes) ->
            "${sample.label}@(${sample.x},${sample.y}) expected=${sample.expected} actualRGBA=$bytes"
        },
    )
    sampleBytes.forEach { (sample, bytes) ->
        assertEquals(sample.expected, bytes, "$scene ${sample.label} at (${sample.x},${sample.y}) actualRGBA=$bytes")
    }
}
