package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetR
import org.skia.testing.TestUtils

/**
 * Integration smoke test for [ImageFilterBlurDropShadowGM].
 *
 * Phase 7d.2 doesn't have an upstream-reference visual regression
 * (the canonical `gm/imagefiltersbase.cpp` mixes filters across many
 * orientations we don't tile the same way). The bespoke GM exercises
 * Blur / DropShadow / MatrixTransform end-to-end through
 * `drawImageRect` → filter → blend.
 *
 * Asserts :
 *  - Cell 1 (raw, x=10..74) : centre pixel is red.
 *  - Cell 2 (blur, x=100..164) : centre pixel is bright (red survives
 *    blur but loses some saturation at edges).
 *  - Cell 3 (drop-shadow, x=190..) : the shadow is visible BELOW the
 *    original (positive offset), so a sample below the original's
 *    bottom edge has non-zero alpha.
 */
class ImageFilterBlurDropShadowTest {

    @Test
    fun `ImageFilterBlurDropShadowGM renders cells with expected pixels`() {
        val gm = ImageFilterBlurDropShadowGM()
        val rendered = TestUtils.runGmTest(gm)

        // Cell 1 : raw source. Red square inside (10+8..10+56)×(10+8..10+56).
        val cell1 = rendered.getPixel(25, 25)
        assertTrue(SkColorGetR(cell1) > 200) {
            "cell 1 should have red, got 0x${"%08X".format(cell1)}"
        }

        // Cell 2 : blurred. Centre still bright red.
        val cell2 = rendered.getPixel(115, 25)
        assertTrue(SkColorGetR(cell2) > 200) {
            "cell 2 (blur) centre should still be ≥200 red, got 0x${"%08X".format(cell2)}"
        }

        // Cell 3 : drop-shadow (offset 8, 8). Shadow extends below the
        // original ; sample (190 + 60, 80) which should have non-zero
        // alpha from the shadow.
        val cell3Shadow = rendered.getPixel(250, 80)
        // Shadow is alpha 0x80 ; after blur some falloff. Just check
        // alpha is > 20 (non-trivially shadowed).
        val shadowA = SkColorGetA(cell3Shadow)
        assertTrue(shadowA > 20) {
            "cell 3 shadow region should have alpha > 20, got $shadowA"
        }
    }
}
