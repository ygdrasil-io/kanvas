package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Fourth batch of GM ports — 3 more GMs that don't need any of
 * the unshipped chantiers. Floors at 0 % per the "as many as
 * possible, accept low %" directive.
 */
class D2PreBatch4Test {

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
     * `lighting` — C1.7 lighting filters on a synthetic 100×100
     * input bitmap (substitutes upstream's 'e' glyph render).
     */
    @Test
    fun `LightingGM matches reference`() =
        runGm(LightingGM(), "LightingGM", floor = 0.0)
}
