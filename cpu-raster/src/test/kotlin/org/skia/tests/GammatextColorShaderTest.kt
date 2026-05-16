package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Wave 11A textual GM port — see [GammatextColorShaderGM] for source
 * mapping. Validates the
 * paint-colour vs `SkShaders.Color` vs `SkShaders.Color(SkColor4f, sRGB)`
 * three-way alignment on grey ABCDEFG runs.
 */
class GammatextColorShaderTest {

    @Test
    fun `GammatextColorShaderGM matches gammatext_color_shader_png within tolerance`() {
        val gm = GammatextColorShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image gammatext_color_shader.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("GammatextColorShaderGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("GammatextColorShaderGM", comparison.similarity)
        assertTrue(accepted, "GammatextColorShaderGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= FLOOR,
            "GammatextColorShaderGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR% floor",
        )
    }

    private companion object {
        private const val FLOOR = 80.0
    }
}
