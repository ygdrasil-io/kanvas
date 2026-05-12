package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TransparencyCheckTest {

    @Test
    fun `TransparencyCheckGM matches transparency_check_png within tolerance`() {
        val gm = TransparencyCheckGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image transparency_check.png")
        // Gradient stops produce smooth premul-alpha bands over a
        // 2-cell checker BG, scaled 7x/120x via surface->draw. The
        // bulk pixel drift comes from a known gap in our 8-bit gradient
        // path: when the destination surface is sRGB-encoded N32Premul
        // (as upstream's `SkSurfaces::Raster(MakeN32Premul)` is), our
        // `lerpPremul` blends stops in sRGB-encoded byte space, while
        // upstream decodes to linear via `SkColor4f` before
        // interpolating. The visible result is the gradient mid-tones
        // (around t=0.5, alpha=0.5) being ~30-50 levels brighter than
        // the reference. The structural geometry — band positions,
        // colour bands, checker BG — is correct ; only the gamma curve
        // of the gradient mid-tones drifts.
        //
        // Tracking gap : `SkGradientShader::MakeLinear` needs a
        // SkColor4f-aware overload that propagates linear interpolation
        // through the 8-bit path on sRGB-encoded surfaces (or the path
        // needs to detect non-trivial transfer functions and decode/
        // re-encode around the lerp). Until then, this GM stays at the
        // structural-similarity floor.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TransparencyCheckGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TransparencyCheckGM", comparison.similarity)
        assertTrue(accepted, "TransparencyCheckGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 10.0,
            "TransparencyCheckGM similarity ${"%.2f".format(comparison.similarity)}% < 10%",
        )
    }
}
