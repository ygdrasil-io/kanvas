package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ColorFiltersTest {

    @Test
    fun `ColorFiltersGM matches lightingcolorfilter_png within tolerance`() {
        val gm = ColorFiltersGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lightingcolorfilter.png")

        // 7 rainbow-gradient rows under SkColorFilters::Lighting (synth'd
        // here as Matrix). Linear gradients and matrix-multiply are
        // bit-equivalent paths upstream / kanvas-skia ; tolerance kept
        // tight (=2).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("ColorFiltersGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorFiltersGM", comparison.similarity)
        assertTrue(accepted, "ColorFiltersGM regressed below ratchet")
    }
}
