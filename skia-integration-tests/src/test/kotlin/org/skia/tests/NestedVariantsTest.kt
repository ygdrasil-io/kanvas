package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the remaining `gm/nested.cpp` variants not covered by
 * [D2PreBatch5Test]:
 *  - `nested_bw`           (doAA=false, flipped=false)
 *  - `nested_flipY_aa`     (doAA=true,  flipped=true)
 *  - `nested_flipY_bw`     (doAA=false, flipped=true)
 *  - `nested_hairline_square` (DEF_SIMPLE_GM 64x64)
 */
class NestedVariantsTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    /**
     * `nested_bw` — same nested rect/rrect/oval combinations as
     * `nested_aa` but with anti-aliasing disabled.
     */
    @Test
    fun `NestedBwGM matches nested_bw reference`() =
        runGm(NestedGM(doAA = false, flipped = false), "NestedBwGM", floor = 0.0)

    /**
     * `nested_flipY_aa` — anti-aliased nested shapes with the canvas
     * flipped vertically (scale 1,-1 + translate) to stress path
     * orientation handling.
     */
    @Test
    fun `NestedFlipYAaGM matches nested_flipY_aa reference`() =
        runGm(NestedGM(doAA = true, flipped = true), "NestedFlipYAaGM", floor = 0.0)

    /**
     * `nested_flipY_bw` — BW nested shapes with vertical flip.
     */
    @Test
    fun `NestedFlipYBwGM matches nested_flipY_bw reference`() =
        runGm(NestedGM(doAA = false, flipped = true), "NestedFlipYBwGM", floor = 0.0)

    /**
     * `nested_hairline_square` — regression for crbug.com/1234194.
     * Two rows of 3 nested-rect squares scaled to subpixel size;
     * the second row is offset 0.5 px down to stress hairline
     * rendering at fractional positions.
     */
    @Test
    fun `NestedHairlineSquareGM matches nested_hairline_square reference`() =
        runGm(NestedHairlineSquareGM(), "NestedHairlineSquareGM", floor = 0.0)
}
