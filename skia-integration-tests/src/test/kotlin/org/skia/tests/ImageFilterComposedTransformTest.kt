package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFilterComposedTransformTest {

    @Test
    fun `ImageFilterComposedTransformGM matches imagefilter_composed_transform_png within tolerance`() {
        val gm = ImageFilterComposedTransformGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefilter_composed_transform.png")
        // Four quadrants of composed MatrixTransform + Offset filters (should match).
        // Bilinear sampling of a rotated mandrill accumulates per-pixel rounding.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ImageFilterComposedTransformGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFilterComposedTransformGM", comparison.similarity)
        assertTrue(accepted, "ImageFilterComposedTransformGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ImageFilterComposedTransformGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
