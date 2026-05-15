package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ShaderMaskFilterGMTest {

    @Test
    fun `ShaderMaskFilterGM matches shadermaskfilter_gradient_png within tolerance`() {
        val gm = ShaderMaskFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shadermaskfilter_gradient.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ShaderMaskFilterGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShaderMaskFilterGM", comparison.similarity)
        assertTrue(accepted, "ShaderMaskFilterGM regressed below ratchet")
    }
}
