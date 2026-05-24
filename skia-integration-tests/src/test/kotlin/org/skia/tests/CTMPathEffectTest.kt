package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CTMPathEffectTest {

    @Test
    fun `CTMPathEffectGM matches reference`() {
        val gm = CTMPathEffectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ctmpatheffect.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CTMPathEffectGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CTMPathEffectGM", comparison.similarity)
        assertTrue(accepted, "CTMPathEffectGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "CTMPathEffectGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
