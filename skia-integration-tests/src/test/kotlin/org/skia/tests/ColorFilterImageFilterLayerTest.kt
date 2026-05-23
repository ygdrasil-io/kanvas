package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ColorFilterImageFilterLayerTest {

    @Test
    fun `ColorFilterImageFilterLayerGM matches colorfilterimagefilter_layer_png within tolerance`() {
        val gm = ColorFilterImageFilterLayerGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorfilterimagefilter_layer.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ColorFilterImageFilterLayerGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorFilterImageFilterLayerGM", comparison.similarity)
        assertTrue(accepted, "ColorFilterImageFilterLayerGM regressed below ratchet")
    }
}
