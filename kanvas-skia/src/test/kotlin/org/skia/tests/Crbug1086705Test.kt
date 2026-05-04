package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug1086705Test {

    @Test
    fun `Crbug1086705GM matches crbug_1086705_png within tolerance`() {
        val gm = Crbug1086705GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1086705.png")
        // 700-vertex tiny circle stroked with width > radius; the
        // stroke self-intersects.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug1086705GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1086705GM", comparison.similarity)
        assertTrue(accepted, "Crbug1086705GM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "Crbug1086705GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=1 floor)")
    }
}
