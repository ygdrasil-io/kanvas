package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [Format4444GM] (`format4444`, 64×64).
 *
 * Verifies ARGB_4444 pixel storage: two solid-color blocks drawn via
 * [org.skia.core.SkCanvas.clear] and two written via
 * [org.skia.foundation.SkBitmap.writePixels] with raw-packed 16-bit values.
 * Our 4444 quantisation (round-to-nearest) may produce minor deltas vs the
 * upstream reference — tolerance set to 8 to absorb 4-bit rounding.
 */
class Format4444Test {

    @Test
    fun `Format4444GM matches format4444_png within tolerance`() {
        val gm = Format4444GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image format4444.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Format4444GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Format4444GM", comparison.similarity)
        assertTrue(accepted, "Format4444GM regressed below ratchet")
    }
}
