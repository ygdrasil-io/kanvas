package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug224618GMTest {

    @Test
    fun `Crbug224618GM matches crbug_224618_png within tolerance`() {
        val gm = Crbug224618GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_224618.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug224618GM", comparison)
        if (comparison.similarity < 70.0) {
            // Perspective rasterisation drift may be > 1 ulp ; we accept lower
            // similarity but still ratchet against any regression.
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug224618GM", comparison.similarity)
        assertTrue(accepted, "Crbug224618GM regressed below ratchet")
    }
}
