package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class WideButtCapsTest {

    @Test
    fun `WideButtCapsGM matches widebuttcaps_png within tolerance`() {
        val gm = WideButtCapsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image widebuttcaps.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("WideButtCapsGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("WideButtCapsGM", comparison.similarity)
        assertTrue(accepted, "WideButtCapsGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 80.0,
            "WideButtCapsGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% floor",
        )
    }
}
