package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/** Visual regression for [Dashing4GM] (port of `dashing.cpp::Dashing4GM`). */
class Dashing4Test {
    @Test
    fun `Dashing4GM matches dashing4_png within tolerance`() {
        val gm = Dashing4GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Dashing4GM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Dashing4GM", comparison.similarity)
        assertTrue(accepted, "Dashing4GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "Dashing4GM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
