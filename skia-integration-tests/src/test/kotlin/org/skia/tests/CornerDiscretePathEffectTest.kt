package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorGetA
import org.skia.testing.TestUtils

/**
 * Integration smoke test for [CornerDiscretePathEffectGM].
 *
 * Phase 7p2 doesn't ship an upstream-reference visual regression (the
 * canonical `gm/patheffects.cpp::PathEffectGM` mixes effect families
 * we don't all ship yet — 1D / 2D / Compose). The bespoke
 * [CornerDiscretePathEffectGM] exercises the
 * [org.skia.foundation.SkCornerPathEffect] +
 * [org.skia.tools.SkDiscretePathEffect] integration with the
 * device's `drawPath` → `pathEffect.filterPath` → stroker → fill
 * pipeline, but doesn't have a reference image.
 *
 * This test simply asserts :
 *  - The GM renders without throwing.
 *  - At least some pixels are touched (the effect didn't silently
 *    erase the entire bitmap due to a malformed transform).
 *
 * Per-effect correctness lives in [SkCornerPathEffectTest] +
 * [SkDiscretePathEffectTest] (path-level math) ; end-to-end pipeline
 * correctness lives transitively in [DashingTest] (which validates
 * the same `paint.pathEffect → stroker → fill` wiring).
 */
class CornerDiscretePathEffectTest {

    @Test
    fun `CornerDiscretePathEffectGM renders without crash and touches pixels`() {
        val gm = CornerDiscretePathEffectGM()
        val rendered = TestUtils.runGmTest(gm)
        var touchedPixels = 0
        for (y in 0 until rendered.height step 4) {
            for (x in 0 until rendered.width step 4) {
                val px = rendered.getPixel(x, y)
                // Touched = non-white (the bg). The GM strokes black
                // lines, so any pixel with darker grey-or-black colour
                // counts as touched.
                if (SkColorGetA(px) > 0 && px != 0xFFFFFFFF.toInt()) touchedPixels++
            }
        }
        assertTrue(touchedPixels > 100) {
            "expected the GM to touch at least 100 pixels, got $touchedPixels"
        }
    }
}
