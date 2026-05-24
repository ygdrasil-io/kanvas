package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsHueMethodTest {

    @Test
    fun `GradientsHueMethodGM matches reference within tolerance`() {
        val gm = GradientsHueMethodGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("GradientsHueMethodGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }

        val accepted = SimilarityTracker.updateScore("GradientsHueMethodGM", comparison.similarity)
        assertTrue(accepted, "GradientsHueMethodGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "GradientsHueMethodGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
