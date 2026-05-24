package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Color4blendcfTest {

    @Test
    fun `Color4blendcfGM matches color4blendcf_png within tolerance`() {
        val gm = Color4blendcfGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image color4blendcf.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Color4blendcfGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Color4blendcfGM", comparison.similarity)
        assertTrue(accepted, "Color4blendcfGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "Color4blendcfGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
