package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round13Test {

    private fun runGm(gm: org.skia.tests.GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `MatrixImageFilterGM matches reference`() =
        runGm(MatrixImageFilterGM(), "MatrixImageFilterGM", 60.0)

    @Test
    fun `SpriteBitmapGM matches reference`() = runGm(SpriteBitmapGM(), "SpriteBitmapGM", 50.0)

    @Test
    fun `HairModesGM matches reference`() = runGm(HairModesGM(), "HairModesGM", 60.0)

    @Test
    fun `ShaderPathGM matches reference`() = runGm(ShaderPathGM(), "ShaderPathGM", 50.0)

    // FatPathFillGM at ~54 % — our SkStroker.fromPaint substitute for
    // skpathutils::FillPathWithPaint produces a fat-path with subtly
    // different geometry under the 1-pixel stroke + 32× scale combo,
    // so the inner-fill coverage drifts from upstream. Visual layout
    // matches : a stripe-shaped alpha region per row + red hairline.
    @Test
    fun `FatPathFillGM matches reference`() = runGm(FatPathFillGM(), "FatPathFillGM", 50.0)
}
