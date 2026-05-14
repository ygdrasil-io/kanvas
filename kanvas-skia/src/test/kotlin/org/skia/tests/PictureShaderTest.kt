package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PictureShaderTest {

    @Test
    fun `PictureShaderGM matches pictureshader_png within tolerance`() {
        val gm = PictureShaderGM(tileSize = 50f, sceneSize = 100f)
        runOne(gm)
    }

    @Test
    fun `PictureShaderLocalWrapperGM matches pictureshader_localwrapper_png within tolerance`() {
        val gm = PictureShaderGM(tileSize = 50f, sceneSize = 100f, useLocalMatrixWrapper = true)
        runOne(gm)
    }

    @Test
    fun `PictureShaderAlphaGM matches pictureshader_alpha_png within tolerance`() {
        val gm = PictureShaderGM(tileSize = 50f, sceneSize = 100f, alpha = 0.25f)
        runOne(gm)
    }

    private fun runOne(gm: PictureShaderGM) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        // Picture shader is built by rasterising the picture into a
        // bitmap then routing through `image.makeShader`. Six columns
        // of CTM × 5 rows of localMatrix variants land most pixels on
        // raster-level pixel-perfect tile composites; tolerance 8
        // absorbs the AA drift around the green circles + red strokes.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed(gm.name(), comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(gm.name(), comparison.similarity)
        assertTrue(accepted, "${gm.name()} regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "${gm.name()} similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
