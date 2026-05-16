package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class MandolineTest {
    @Test
    fun `MandolineGM matches mandoline_png within tolerance`() {
        val gm = MandolineGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image mandoline.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("MandolineGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("MandolineGM", comparison.similarity)
        assertTrue(accepted, "MandolineGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 91.8,
            "MandolineGM similarity ${"%.2f".format(comparison.similarity)}% < 91.8% floor",
        )
    }
}
