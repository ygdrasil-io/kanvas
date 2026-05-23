package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HairlinesButtcapTest {

    @Test
    fun `HairlinesButtcapGM matches reference within tolerance`() {
        val gm = HairlinesButtcapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("HairlinesButtcapGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("HairlinesButtcapGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("HairlinesButtcapGM", comparison.similarity)
        assertTrue(accepted, "HairlinesButtcapGM regressed below ratchet")
    }
}
