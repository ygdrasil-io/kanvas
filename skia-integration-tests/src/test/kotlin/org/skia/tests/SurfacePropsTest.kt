package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SurfacePropsTest {

    @Test
    fun `SurfacePropsGM matches surfaceprops within tolerance`() {
        val gm = SurfacePropsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image surfaceprops.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("SurfacePropsGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SurfacePropsGM", comparison.similarity)
        assertTrue(accepted, "SurfacePropsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "SurfacePropsGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0%",
        )
    }
}
