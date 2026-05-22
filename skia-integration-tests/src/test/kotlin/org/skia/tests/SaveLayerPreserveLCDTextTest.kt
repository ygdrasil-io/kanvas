package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SaveLayerPreserveLCDTextTest {
    @Test
    fun `SaveLayerPreserveLCDTextGM matches savelayerpreservelcdtext_png within tolerance`() {
        val gm = SaveLayerPreserveLCDTextGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image savelayerpreservelcdtext.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SaveLayerPreserveLCDTextGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SaveLayerPreserveLCDTextGM", comparison.similarity)
        assertTrue(accepted, "SaveLayerPreserveLCDTextGM regressed below ratchet")
    }
}
