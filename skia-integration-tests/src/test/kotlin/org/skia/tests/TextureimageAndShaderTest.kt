package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TextureimageAndShaderTest {

    @Test
    fun `TextureimageAndShaderGM matches reference within tolerance`() {
        val gm = TextureimageAndShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("TextureimageAndShaderGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("TextureimageAndShaderGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("TextureimageAndShaderGM", comparison.similarity)
        assertTrue(accepted, "TextureimageAndShaderGM regressed below ratchet")
    }
}
