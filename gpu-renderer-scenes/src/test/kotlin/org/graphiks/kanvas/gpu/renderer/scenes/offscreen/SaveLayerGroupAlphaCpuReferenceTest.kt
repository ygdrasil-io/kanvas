package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KGPU-M28-006 (proof hardening): proves the [OffscreenSceneCpuReference] performs
 * GENUINE layered compositing for a group-alpha saveLayer, NOT a naive direct draw.
 *
 * The `savelayer-group-alpha` scene fades a layer (group alpha 0.5) containing two
 * overlapping OPAQUE children over an opaque background. With true layer isolation
 * the layer's coverage alpha is 1 across the whole union, so every covered pixel
 * blends at exactly 50% over the background:
 *
 *   background      = (0.20, 0.25, 0.35) -> (51, 64, 89)
 *   child A only    = 0.5*A + 0.5*bg     -> (140, 57, 70)
 *   child B only    = 0.5*B + 0.5*bg     -> (51, 134, 83)
 *   overlap (B/A)   = 0.5*B + 0.5*bg     -> (51, 134, 83)   (== child B only)
 *
 * A naive direct path would make the overlap ~75% opaque -> red ~96 (child A still
 * showing through) instead of 51, and child A only would stay fully opaque
 * (red ~230) instead of 140. These assertions therefore fail for a circular /
 * direct CPU reference and only pass for real layered compositing.
 */
class SaveLayerGroupAlphaCpuReferenceTest {

    private val image = OffscreenSceneCpuReference.renderSceneRgba("savelayer-group-alpha")

    private fun rgb(x: Int, y: Int): Triple<Int, Int, Int> {
        val base = (y * image.width + x) * 4
        return Triple(
            image.rgba[base].toInt() and 0xFF,
            image.rgba[base + 1].toInt() and 0xFF,
            image.rgba[base + 2].toInt() and 0xFF,
        )
    }

    private fun assertNear(
        sample: Triple<Int, Int, Int>,
        expected: Triple<Int, Int, Int>,
        tolerance: Int,
        label: String,
    ) {
        val (r, g, b) = sample
        val (er, eg, eb) = expected
        assertTrue(
            kotlin.math.abs(r - er) <= tolerance &&
                kotlin.math.abs(g - eg) <= tolerance &&
                kotlin.math.abs(b - eb) <= tolerance,
            "$label expected ~($er,$eg,$eb) got ($r,$g,$b)",
        )
    }

    @Test
    fun `background region is the opaque background color`() {
        assertNear(rgb(40, 50), Triple(51, 64, 89), tolerance = 2, label = "background")
    }

    @Test
    fun `child A only region blends at 50 percent over the background`() {
        // Layered 0.5 -> (140,57,70). A naive opaque direct draw would be (230,51,51).
        assertNear(rgb(100, 110), Triple(140, 57, 70), tolerance = 3, label = "childA-only")
    }

    @Test
    fun `child B only region blends at 50 percent over the background`() {
        assertNear(rgb(240, 150), Triple(51, 134, 83), tolerance = 3, label = "childB-only")
    }

    @Test
    fun `overlap region equals the child B only blend proving uniform layer isolation`() {
        val overlap = rgb(170, 120)
        val childBOnly = rgb(240, 150)
        // True layered: overlap == childB-only (uniform 50% blend across the union).
        assertNear(overlap, childBOnly, tolerance = 2, label = "overlap-vs-childB")
        // Pin the layered value; naive direct overlap would be ~(96,131,73) and a
        // no-group-alpha full-opacity overlay would be ~(51,204,76).
        assertNear(overlap, Triple(51, 134, 83), tolerance = 3, label = "overlap")
    }
}
