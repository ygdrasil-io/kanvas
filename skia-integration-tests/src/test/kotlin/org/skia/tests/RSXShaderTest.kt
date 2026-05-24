package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet driver for [RSXShaderGM].
 *
 * The upstream `gm/rsxtext.cpp` GM exercises three pieces of plumbing
 * in concert :
 *  1. [org.skia.foundation.SkTextBlobBuilder.allocRunRSXform] — per-glyph
 *     [org.skia.foundation.SkRSXform] (rotation + scale + translate) ;
 *  2. [org.skia.core.SkCanvas.drawTextBlob] for the RSX run variant ;
 *  3. A two-stage shader (`SkImage::makeShader(lm)` →
 *     `SkShader::makeWithLocalMatrix(outer_lm)`) — kanvas-skia folds the
 *     two local matrices into a single
 *     [org.skia.foundation.SkLocalMatrixShader] wrapper.
 *
 * Tolerance + floor follow the textual GM convention
 * ([TestUtils.TEXTUAL_GM_TOLERANCE] = 8) — glyph paths come from the portable OpenType
 * scaler so AA edges drift ~1-2 ulp vs the upstream FreeType reference,
 * and the rotation/scale + shader-tile sampling amplifies that drift
 * along the diagonals. The [SimilarityTracker] ratchet locks the
 * day-to-day measurement.
 */
class RSXShaderTest {

    @Test
    fun `RSXShaderGM matches rsx_blob_shader_png within tolerance`() {
        val gm = RSXShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rsx_blob_shader.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered,
            reference!!,
            tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("RSXShaderGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RSXShaderGM", comparison.similarity)
        assertTrue(accepted, "RSXShaderGM regressed below ratchet")
    }
}
