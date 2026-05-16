package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ShallowAnglePathArcToTest {
    @Test
    fun `ShallowAnglePathArcToGM matches shallow_angle_path_arcto_png within tolerance`() {
        val gm = ShallowAnglePathArcToGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shallow_angle_path_arcto.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ShallowAnglePathArcToGM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShallowAnglePathArcToGM", comparison.similarity)
        assertTrue(accepted, "ShallowAnglePathArcToGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "ShallowAnglePathArcToGM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        private const val THRESHOLD: Double = 99.8
    }
}
