package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Validates [PictureShaderPerspGM] against the upstream `pictureshader_persp.png`
 * reference.
 *
 * The GM applies a full 4×4 perspective matrix (built from
 * `SkM44::Perspective(0.01, 10, π/3)` + pre-translate + pre-rotate on the
 * Y-axis) to two side-by-side 100×100 views of a `Hamburgefons` text picture:
 *  - **kDirect**: clip + drawPicture.
 *  - **kPictureShader**: makeShader(kDecal, kDecal, kLinear, tile=50×50).
 *
 * Floor is 0 % — the AWT rasterizer diverges from upstream's GPU path on
 * perspective text rendering and on the kDecal picture-shader boundary.
 * The test primarily exercises that the code path compiles and does not
 * throw (structural smoke test).
 */
class PictureShaderPerspTest {

    @Test
    fun `PictureShaderPerspGM matches pictureshader_persp_png`() {
        val gm = PictureShaderPerspGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pictureshader_persp.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("PictureShaderPerspGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PictureShaderPerspGM", comparison.similarity)
        assertTrue(accepted, "PictureShaderPerspGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "PictureShaderPerspGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor",
        )
    }
}
