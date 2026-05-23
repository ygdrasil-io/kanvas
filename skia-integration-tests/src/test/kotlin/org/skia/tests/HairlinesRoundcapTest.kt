package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HairlinesRoundcapTest {

    @Test
    fun `HairlinesRoundcapGM matches reference within tolerance`() {
        val gm = HairlinesRoundcapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("HairlinesRoundcapGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("HairlinesRoundcapGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("HairlinesRoundcapGM", comparison.similarity)
        assertTrue(accepted, "HairlinesRoundcapGM regressed below ratchet")
    }
}
