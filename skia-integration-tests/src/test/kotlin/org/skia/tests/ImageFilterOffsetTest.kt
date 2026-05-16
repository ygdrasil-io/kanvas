package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetR
import org.skia.math.SkColorGetB
import org.skia.testing.TestUtils

/**
 * Integration smoke test for [ImageFilterOffsetGM].
 *
 * Phase 7d.1 doesn't ship an upstream-reference visual regression
 * (the canonical `gm/imagefiltersbase.cpp` mixes filters we don't
 * all ship yet — Blur / DropShadow / MatrixTransform). The bespoke
 * [ImageFilterOffsetGM] exercises the
 * [org.skia.foundation.SkImageFilters.Offset] +
 * [org.skia.foundation.SkImageFilters.ColorFilter] +
 * [org.skia.foundation.SkImageFilters.Compose] integration with the
 * device's `drawImageRect` → filter → blend pipeline, but doesn't
 * have a reference image.
 *
 * This test asserts :
 *  - The GM renders without throwing.
 *  - Cell 1 (raw source) shows red pixels in the expected area.
 *  - Cell 3 (swap-RB filter) shows BLUE where cell 1 had RED — the
 *    colour filter through the image-filter pipeline did its job.
 */
class ImageFilterOffsetTest {

    @Test
    fun `ImageFilterOffsetGM renders cells with expected colour transformations`() {
        val gm = ImageFilterOffsetGM()
        val rendered = TestUtils.runGmTest(gm)

        // Cell 1 (raw, x=10..74) — red square at source-relative (8..56),
        // so device-relative (18..66). Sample (32, 32) which is inside
        // the red square.
        val cell1Px = rendered.getPixel(32, 32)
        assertTrue(SkColorGetR(cell1Px) > 200 && SkColorGetA(cell1Px) > 200) {
            "cell 1 should have red pixel, got 0x${"%08X".format(cell1Px)}"
        }

        // Cell 3 (Compose(Offset, ColorFilter swap-RB), x=190..254 + offset 15) —
        // sample what should now be in the BLUE channel (was RED in source).
        // Device offset 15 right + 10 down ; source-relative red at (8..56)
        // → device-relative (190+15+8 .. 190+15+56) = (213..261).
        // Pick (220, 35) which should be blue (post swap) inside the square.
        val cell3Px = rendered.getPixel(220, 35)
        assertTrue(SkColorGetB(cell3Px) > 200 && SkColorGetA(cell3Px) > 200) {
            "cell 3 should have blue pixel after swap-RB filter, " +
                "got 0x${"%08X".format(cell3Px)}"
        }
    }
}
