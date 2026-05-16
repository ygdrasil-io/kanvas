package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AnisoMipsTest {

    @Test
    fun `AnisoMipsGM matches anisomips_png within tolerance`() {
        val gm = AnisoMipsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image anisomips.png")
        // 520 × 260 canvas. Two 4×4 grids of progressively-scaled
        // images, one via drawImage, one via image.makeShader. Floor
        // sits at 30% — kanvas-skia's box-filter mip pyramid + N-tap
        // aniso shortcut diverges from upstream's trilinear + full
        // elliptical weighted average, but the cell-to-cell mean colour
        // (which dominates the area-weighted similarity score) should
        // still match.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnisoMipsGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnisoMipsGM", comparison.similarity)
        assertTrue(accepted, "AnisoMipsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "AnisoMipsGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% (t=8 floor)",
        )
    }
}
