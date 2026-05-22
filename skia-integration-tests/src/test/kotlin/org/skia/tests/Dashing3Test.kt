package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/** Visual regression for [Dashing3GM] (port of `dashing.cpp::Dashing3GM`). */
class Dashing3Test {
    @Test
    fun `Dashing3GM matches dashing3_png within tolerance`() {
        val gm = Dashing3GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Dashing3GM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Dashing3GM", comparison.similarity)
        assertTrue(accepted, "Dashing3GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "Dashing3GM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
