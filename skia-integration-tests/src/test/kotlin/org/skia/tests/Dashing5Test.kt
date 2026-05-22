package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [Dashing5GM] (port of `dashing.cpp::Dashing5GM`).
 *
 * Two variants : `dashing5_aa` and `dashing5_bw`, each compared to
 * its own reference PNG.
 */
class Dashing5Test {

    @Test
    fun `Dashing5GM AA matches dashing5_aa_png within tolerance`() {
        runVariant(Dashing5GM(doAA = true), "Dashing5GMAA")
    }

    @Test
    fun `Dashing5GM BW matches dashing5_bw_png within tolerance`() {
        runVariant(Dashing5GM(doAA = false), "Dashing5GMBW")
    }

    private fun runVariant(gm: Dashing5GM, trackerKey: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerKey, comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerKey, comparison.similarity)
        assertTrue(accepted, "$trackerKey regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "$trackerKey similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
