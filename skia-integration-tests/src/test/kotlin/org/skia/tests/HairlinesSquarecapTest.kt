package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HairlinesSquarecapTest {

    @Test
    fun `HairlinesSquarecapGM matches reference within tolerance`() {
        val gm = HairlinesSquarecapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("HairlinesSquarecapGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("HairlinesSquarecapGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("HairlinesSquarecapGM", comparison.similarity)
        assertTrue(accepted, "HairlinesSquarecapGM regressed below ratchet")
    }
}
