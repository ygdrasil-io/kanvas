@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.gm.composite.Sk3dSimpleGm
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.GpuAvailability
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Public native-pixel witnesses for Skia's pinned gm/3d.cpp::sk3d_simple scene. */
class Sk3dSimpleSurfacePixelTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun perspectiveFootprintMatchesIndependentCamera() {
        // Independent camera: q=cot(pi/8), c=cos(pi/6), s=sin(pi/6), then
        // device=(150*q*c*u/(q+s*u), 150*q*v/(q+s*u)). These coordinates are
        // fixed before the public Surface exists and do not use Matrix4x4F32.
        val interior = FixedBand(182, 0, 193, 255, tolerance = 1)
        val outside = listOf(20 to 150, 220 to 150, 0 to 0, 299 to 299)
        assertEquals(40.5049845, independentLeftEdge(), 0.000001)

        val actual = renderSk3dSimple()
        assertPixelNear(actual, 60, 100, interior)
        assertPixelNear(actual, 150, 100, interior)
        outside.forEach { (x, y) -> assertPixel(actual, x, y, 255, 255, 255, 255) }
        assertCleanNativeRender(actual)

        val repeated = renderSk3dSimple()
        assertCleanNativeRender(repeated)
        assertTrue(actual.pixels.contentEquals(repeated.pixels), "repeated sk3d_simple bytes differ")
    }

    @Test
    fun pictureOverlayUsesSkiaAlpha() {
        // The band is set before GPU creation. It admits source-over with blue alpha 136/255,
        // but excludes half alpha (188,0,188) and a missing Picture (255,0,0).
        val interior = FixedBand(182, 0, 193, 255, tolerance = 1)
        assertFalse(interior.contains(188, 0, 188, 255), "half-alpha color entered fixed band")
        assertFalse(interior.contains(255, 0, 0, 255), "omitted-Picture color entered fixed band")

        val actual = renderSk3dSimple()
        assertPixelNear(actual, 150, 100, interior)
        assertCleanNativeRender(actual)

        val repeated = renderSk3dSimple()
        assertCleanNativeRender(repeated)
        assertTrue(actual.pixels.contentEquals(repeated.pixels), "repeated sk3d_simple bytes differ")
    }

    @Test
    fun hardEdgeHasNoPartialCoverage() {
        // Independent perspective left edge = 40.5049845. Pixel centre 40.5 is outside by
        // 0.0049845px, while pixel centre 41.5 is inside; explicit no-AA must keep x=40 white.
        val interior = FixedBand(182, 0, 193, 255, tolerance = 1)
        assertEquals(0.0049845, independentLeftEdge() - 40.5, 0.000001)

        val actual = renderSk3dSimple()
        assertPixel(actual, 40, 100, 255, 255, 255, 255)
        assertPixelNear(actual, 41, 100, interior)
        assertCleanNativeRender(actual)

        val repeated = renderSk3dSimple()
        assertCleanNativeRender(repeated)
        assertTrue(actual.pixels.contentEquals(repeated.pixels), "repeated sk3d_simple bytes differ")
    }
}

private data class FixedBand(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
    val tolerance: Int,
) {
    fun contains(actualRed: Int, actualGreen: Int, actualBlue: Int, actualAlpha: Int): Boolean =
        actualRed in red - tolerance..red + tolerance &&
            actualGreen == green &&
            actualBlue in blue - tolerance..blue + tolerance &&
            actualAlpha == alpha
}

private fun independentLeftEdge(): Double = 150.0 * (1.0 / kotlin.math.tan(Math.PI / 8.0)) *
    kotlin.math.cos(Math.PI / 6.0) * (50.0 / 150.0) /
    ((1.0 / kotlin.math.tan(Math.PI / 8.0)) + kotlin.math.sin(Math.PI / 6.0) * (50.0 / 150.0))

private fun renderSk3dSimple(): RenderResult {
    GpuAvailability.requireWebGpu()
    val gm = Sk3dSimpleGm()
    val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
    surface.canvas().drawRect(
        RectF32.ofLTRB(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
        Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false),
    )
    val gmCanvas = GmCanvas(surface.canvas(), gm.width, gm.height)
    gm.onOnceBeforeDraw(gmCanvas)
    gm.draw(gmCanvas, gm.width, gm.height)
    return surface.render()
}

private fun assertCleanNativeRender(actual: RenderResult) {
    assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
        actual.nativeEvidenceScopeKinds.toString())
    assertTrue(actual.stats.opsDispatched > 0, "expected native dispatch")
    assertEquals(0, actual.stats.opsRefused, actual.diagnostics.toString())
    assertTrue(actual.diagnostics.isEmpty, actual.diagnostics.toString())
}

private fun assertPixelNear(actual: RenderResult, x: Int, y: Int, expected: FixedBand) {
    val (red, green, blue, alpha) = pixel(actual, x, y)
    assertTrue(expected.contains(red, green, blue, alpha),
        "pixel ($x,$y) was ($red,$green,$blue,$alpha), expected $expected")
}

private fun assertPixel(actual: RenderResult, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
    val pixel = pixel(actual, x, y)
    assertTrue(pixel.contentEquals(intArrayOf(red, green, blue, alpha)),
        "pixel ($x,$y) was ${pixel.toList()}")
}

private fun pixel(actual: RenderResult, x: Int, y: Int): IntArray {
    val offset = (y * actual.width + x) * 4
    return intArrayOf(
        actual.pixels[offset].toInt(),
        actual.pixels[offset + 1].toInt(),
        actual.pixels[offset + 2].toInt(),
        actual.pixels[offset + 3].toInt(),
    )
}
