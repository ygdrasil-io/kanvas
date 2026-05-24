package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawImageSetTest {

    @Test
    fun `DrawImageSetGM matches reference`() {
        val gm = DrawImageSetGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("DrawImageSetGM", comparison)
        if (comparison.similarity < 15.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("DrawImageSetGM", comparison.similarity))
        assertTrue(
            comparison.similarity >= 15.0,
            "DrawImageSetGM similarity ${"%.2f".format(comparison.similarity)}% < 15.0% floor",
        )
    }
}
