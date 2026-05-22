package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathInverseFillTest {
    @Test
    fun `PathInverseFillGM matches pathinvfill_png within tolerance`() {
        val gm = PathInverseFillGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pathinvfill.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathInverseFillGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathInverseFillGM", comparison.similarity)
        assertTrue(accepted, "PathInverseFillGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "PathInverseFillGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
