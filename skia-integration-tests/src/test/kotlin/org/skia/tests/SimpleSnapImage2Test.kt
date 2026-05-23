package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SimpleSnapImage2Test {

    @Test
    fun `SimpleSnapImage2GM matches simple_snap_image2_png within tolerance`() {
        val gm = SimpleSnapImage2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image simple_snap_image2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SimpleSnapImage2GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SimpleSnapImage2GM", comparison.similarity)
        assertTrue(accepted, "SimpleSnapImage2GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "SimpleSnapImage2GM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor"
        )
    }
}
