package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawImageSetAlphaOnlyTest {

    @Test
    fun `DrawImageSetAlphaOnlyGM matches reference`() {
        val gm = DrawImageSetAlphaOnlyGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("DrawImageSetAlphaOnlyGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("DrawImageSetAlphaOnlyGM", comparison.similarity))
        assertTrue(
            comparison.similarity >= 60.0,
            "DrawImageSetAlphaOnlyGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
