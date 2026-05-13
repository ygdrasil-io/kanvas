package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class OffsetImageFilterTest {
    @Test
    fun `OffsetImageFilterGM matches offsetimagefilter_png within tolerance`() {
        val gm = OffsetImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image offsetimagefilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("OffsetImageFilterGM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("OffsetImageFilterGM", comparison.similarity)
        assertTrue(accepted, "OffsetImageFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "OffsetImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        private const val THRESHOLD: Double = 84.5
    }
}
