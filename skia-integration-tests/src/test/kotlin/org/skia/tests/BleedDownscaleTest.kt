package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BleedDownscaleTest {

    @Test
    fun `BleedDownscaleGM matches reference`() {
        val gm = BleedDownscaleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("BleedDownscaleGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BleedDownscaleGM", comparison.similarity)
        assertTrue(accepted, "BleedDownscaleGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 5.0,
            "BleedDownscaleGM similarity ${"%.2f".format(comparison.similarity)}% < 5% floor",
        )
    }
}
