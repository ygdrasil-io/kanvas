package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/** Visual regression for [DashCircle2GM] (port of `dashcircle.cpp::DashCircle2GM`). */
class DashCircle2Test {
    @Test
    fun `DashCircle2GM matches dashcircle2_png within tolerance`() {
        val gm = DashCircle2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DashCircle2GM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DashCircle2GM", comparison.similarity)
        assertTrue(accepted, "DashCircle2GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "DashCircle2GM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
