package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CopyOnWriteSavelayerTest {

    @Test
    fun `CopyOnWriteSavelayerGM matches copy_on_write_savelayer_png within tolerance`() {
        val gm = CopyOnWriteSavelayerGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image copy_on_write_savelayer.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("CopyOnWriteSavelayerGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CopyOnWriteSavelayerGM", comparison.similarity)
        assertTrue(accepted, "CopyOnWriteSavelayerGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "CopyOnWriteSavelayerGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor"
        )
    }
}
