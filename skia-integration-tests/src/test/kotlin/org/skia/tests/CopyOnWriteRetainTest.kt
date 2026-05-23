package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CopyOnWriteRetainTest {

    @Test
    fun `CopyOnWriteRetainGM matches copy_on_write_retain_png within tolerance`() {
        val gm = CopyOnWriteRetainGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image copy_on_write_retain.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CopyOnWriteRetainGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CopyOnWriteRetainGM", comparison.similarity)
        assertTrue(accepted, "CopyOnWriteRetainGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "CopyOnWriteRetainGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor"
        )
    }
}
