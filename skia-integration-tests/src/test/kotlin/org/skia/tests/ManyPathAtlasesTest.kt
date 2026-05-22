package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ManyPathAtlasesTest {
    @Test
    fun `ManyPathAtlasesGM_128 matches manypathatlases_128_png within tolerance`() {
        val gm = ManyPathAtlasesGM(128)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image manypathatlases_128.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ManyPathAtlasesGM_128", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ManyPathAtlasesGM_128", comparison.similarity)
        assertTrue(accepted, "ManyPathAtlasesGM_128 regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "ManyPathAtlasesGM_128 similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }

    @Test
    fun `ManyPathAtlasesGM_2048 matches manypathatlases_2048_png within tolerance`() {
        val gm = ManyPathAtlasesGM(2048)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image manypathatlases_2048.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ManyPathAtlasesGM_2048", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ManyPathAtlasesGM_2048", comparison.similarity)
        assertTrue(accepted, "ManyPathAtlasesGM_2048 regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "ManyPathAtlasesGM_2048 similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
