package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class FastSlowBlurImageFilterTest {

    @Test
    fun `FastSlowBlurImageFilterGM matches reference`() {
        val gm = FastSlowBlurImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("FastSlowBlurImageFilterGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FastSlowBlurImageFilterGM", comparison.similarity)
        assertTrue(accepted, "FastSlowBlurImageFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= FLOOR,
            "FastSlowBlurImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR%",
        )
    }

    private companion object {
        private const val FLOOR: Double = 40.0
    }
}
