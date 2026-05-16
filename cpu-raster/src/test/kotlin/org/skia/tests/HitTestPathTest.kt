package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HitTestPathTest {

    @Test
    fun `HitTestPathGM matches hittestpath_png within tolerance`() {
        val gm = HitTestPathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image hittestpath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("HitTestPathGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("HitTestPathGM", comparison.similarity)
        assertTrue(accepted, "HitTestPathGM regressed below ratchet")
        // Floor stays loose initially (curve flattening differs slightly between
        // upstream's polynomial root solver and our 16-step subdivision).
        assertTrue(
            comparison.similarity >= 70.0,
            "HitTestPathGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0%",
        )
    }
}
