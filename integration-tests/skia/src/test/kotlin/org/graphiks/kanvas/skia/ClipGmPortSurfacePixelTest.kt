@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.gm.clip.ComplexClip4AaGm
import org.graphiks.kanvas.skia.gm.clip.ComplexClip4BwGm
import org.graphiks.kanvas.skia.gm.clip.ManyPathAtlases128Gm
import org.graphiks.kanvas.skia.gm.clip.ManyPathAtlases2048Gm
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Public native-pixel witnesses for Skia's pinned clip GM ports. */
class ClipGmPortSurfacePixelTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun manyPathAtlases128UsesDifference() {
        assertManyPathAtlasesPixels(ManyPathAtlases128Gm())
    }

    @Test
    fun manyPathAtlases2048UsesDifference() {
        assertManyPathAtlasesPixels(ManyPathAtlases2048Gm())
    }

    @Test
    fun complexClip4AaKeepsDeviceRestriction() {
        assertComplexClip4Pixels(ComplexClip4AaGm())
    }

    @Test
    fun complexClip4BwKeepsDeviceRestriction() {
        assertComplexClip4Pixels(ComplexClip4BwGm())
    }
}

private fun assertManyPathAtlasesPixels(gm: SkiaGm) {
    // Literal source colors witness the scene semantics, not reference-image color parity.
    // (64,1) is outside the radius-64.04 leaf around (64,70), but inside the outer path;
    // (64,70) is inside the leaf well away from every boundary.
    val actual = renderClipGm(gm)
    assertPixel(actual, 64, 70, 255, 255, 0, 255)
    assertPixel(actual, 64, 1, 8, 232, 222, 255)
    assertPixel(actual, 0, 0, 255, 255, 0, 255)
    assertCleanNativeRender(actual)

    val repeated = renderClipGm(gm)
    assertCleanNativeRender(repeated)
    assertTrue(actual.pixels.contentEquals(repeated.pixels), "repeated ${gm.name} bytes differ")
}

private fun assertComplexClip4Pixels(gm: SkiaGm) {
    // Literal source colors witness the scene semantics, not reference-image color parity.
    val actual = renderClipGm(gm)
    listOf(120 to 120, 120 to 500, 270 to 500).forEach { (x, y) ->
        assertPixel(actual, x, y, 0, 255, 0, 255)
    }
    listOf(120 to 220, 750 to 250, 700 to 650, 200 to 500).forEach { (x, y) ->
        assertPixel(actual, x, y, 255, 255, 0, 255)
    }
    listOf(350 to 250, 850 to 300, 850 to 675, 350 to 500).forEach { (x, y) ->
        assertPixel(actual, x, y, 222, 223, 222, 255)
    }
    assertCleanNativeRender(actual)

    val repeated = renderClipGm(gm)
    assertCleanNativeRender(repeated)
    assertTrue(actual.pixels.contentEquals(repeated.pixels), "repeated ${gm.name} bytes differ")
}

private fun renderClipGm(gm: SkiaGm): RenderResult {
    GpuAvailability.requireWebGpu()
    val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
    val gmCanvas = GmCanvas(surface.canvas(), gm.width, gm.height)
    gm.onOnceBeforeDraw(gmCanvas)
    gm.draw(gmCanvas, gm.width, gm.height)
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
