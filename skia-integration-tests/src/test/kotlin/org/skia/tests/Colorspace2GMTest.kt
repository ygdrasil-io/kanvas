package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Integration test for [Colorspace2GM] — the `colorspace2` GM from
 * `gm/colorspace.cpp`.
 *
 * **Classification: partial-complete.** The GM renders correctly (7×5 grid
 * using the `SkCanvas_makeSurface` strategy: an offscreen surface is
 * created in each intermediate colour space via
 * [org.skia.core.SkCanvas.imageInfo] + [org.skia.core.SkCanvas.makeSurface],
 * the source image is drawn into it, and the snapshot is composited back).
 * Per-pixel divergence from the upstream reference is expected for the same
 * 8-bit quantization reason as [ColorspaceGMTest]; tolerance set to 8.
 */
class Colorspace2GMTest {

    @Test
    fun `Colorspace2GM matches colorspace2_png within tolerance`() {
        val gm = Colorspace2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorspace2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Colorspace2GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Colorspace2GM", comparison.similarity)
        assertTrue(accepted, "Colorspace2GM regressed below ratchet")
    }
}
