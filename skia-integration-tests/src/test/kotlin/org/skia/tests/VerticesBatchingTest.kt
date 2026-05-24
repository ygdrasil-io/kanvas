package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class VerticesBatchingTest {

    @Test
    fun `VerticesBatchingGM matches vertices_batching_png within tolerance`() {
        val gm = VerticesBatchingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("VerticesBatchingGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("VerticesBatchingGM", comparison.similarity)
        assertTrue(accepted, "VerticesBatchingGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 75.0,
            "VerticesBatchingGM similarity ${"%.2f".format(comparison.similarity)}% < 75.0% floor",
        )
    }
}
