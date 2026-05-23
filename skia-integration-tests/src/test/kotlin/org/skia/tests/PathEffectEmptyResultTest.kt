package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathEffectEmptyResultTest {
    @Test
    fun `PathEffectEmptyResultGM matches path_effect_empty_result_png within tolerance`() {
        val gm = PathEffectEmptyResultGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathEffectEmptyResultGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathEffectEmptyResultGM", comparison.similarity)
        assertTrue(accepted, "PathEffectEmptyResultGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "PathEffectEmptyResultGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
