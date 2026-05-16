package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersUnpremulTest {

    @Test
    fun `ImageFiltersUnpremulGM matches imagefiltersunpremul_png within tolerance`() {
        val gm = ImageFiltersUnpremulGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefiltersunpremul.png")
        // 64×64 unpremul red-50/255 bitmap composited onto a black
        // background through `paint.setImageFilter(Image(...))` on a
        // plain drawPaint. Phase G7 wired drawPaint to route through
        // saveLayer/restore when paint.imageFilter is set, so this GM
        // now actually rasterises the filter input. Tolerance is
        // permissive because upstream Mitchell sampling doesn't match
        // our nearest-neighbour fallback exactly on the unpremul-blend
        // boundary, but every pixel falls in the same brown-red ballpark.
        // Tolerance 32 absorbs the wide-gamut Rec.2020 encoding drift on
        // the dark-red unpremul output (max per-channel diff ~26 in
        // current runs; before Phase G7 this rendered fully-black
        // because drawPaint silently dropped paint.imageFilter).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 32)
        TestReport.recordDetailed("ImageFiltersUnpremulGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersUnpremulGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersUnpremulGM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "ImageFiltersUnpremulGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=32 floor)")
    }
}
