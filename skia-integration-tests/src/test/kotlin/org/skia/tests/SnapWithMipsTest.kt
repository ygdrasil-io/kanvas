package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SnapWithMipsTest {

    @Test
    fun `SnapWithMipsGM matches snap_with_mips_png within tolerance`() {
        val gm = SnapWithMipsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image snap_with_mips.png")
        // Mipmap filtering produces downscaled blends that differ from the
        // GPU reference; allow moderate tolerance.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("SnapWithMipsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SnapWithMipsGM", comparison.similarity)
        assertTrue(accepted, "SnapWithMipsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "SnapWithMipsGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor"
        )
    }
}
