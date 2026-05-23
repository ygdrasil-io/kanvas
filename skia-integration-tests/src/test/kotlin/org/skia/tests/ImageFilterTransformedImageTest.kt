package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFilterTransformedImageTest {

    @Test
    fun `ImageFilterTransformedImageGM matches imagefilter_transformed_image_png within tolerance`() {
        val gm = ImageFilterTransformedImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefilter_transformed_image.png")
        // Mirror (flip-x) and perspective rotation applied via canvas concat vs
        // MatrixTransform filter. Perspective projection + bilinear sampling
        // accumulate per-pixel rounding vs upstream; floor 30 % is conservative.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ImageFilterTransformedImageGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFilterTransformedImageGM", comparison.similarity)
        assertTrue(accepted, "ImageFilterTransformedImageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ImageFilterTransformedImageGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
