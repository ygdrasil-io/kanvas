package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TilemodesAlphaTest {

    @Test
    fun `TilemodesAlphaGM matches tilemodes_alpha_png within tolerance`() {
        val gm = TilemodesAlphaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemodes_alpha.png")
        // 512 × 512 grid of 16 image-shader-filled rects under all
        // {kClamp, kRepeat, kMirror, kDecal}² combinations with a fixed
        // 0.5 alpha paint. Nearest-neighbour sampling — exact pixel
        // alignment between tile cells expected, modulo paint-alpha
        // float quantisation across 4 channels plus a residual gap
        // between kanvas-skia's premul-encoded F16 storage and the
        // upstream reference's unpremul-stored colour after Rec.2020
        // round-trip.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("TilemodesAlphaGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TilemodesAlphaGM", comparison.similarity)
        assertTrue(accepted, "TilemodesAlphaGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "TilemodesAlphaGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% (t=2 floor)",
        )
    }
}
