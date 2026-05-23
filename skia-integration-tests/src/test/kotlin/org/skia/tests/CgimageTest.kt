package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-test for [CgimageGM] — the upstream `cgimage` GM that is
 * normally `#ifdef SK_BUILD_FOR_MAC`-gated and exercises Skia's
 * `CoreGraphics` round-trip helpers (`SkCreateCGImageRefWithColorspace`,
 * `SkCreateBitmapFromCGImage`, `SkMakeImageFromCGImage`).
 *
 * `:kanvas-skia` doesn't expose those JNI bridges (bucket
 * `STUB.MISSING_API`), so the port stamps the same anti-aliased blue
 * disc through the raster pipeline for each of the 7 `(colorType,
 * alphaType)` configurations and models the CG round-trip as a
 * bitmap-to-bitmap copy. Geometry matches upstream ; per-pixel parity
 * vs `cgimage.png` is **not** expected (the upstream reference carries
 * subtle 565 / BGRA quantisation differences and an Apple-CG colour
 * conversion through the device colour space). A permissive
 * similarity floor lets the ratchet track regressions in the raster
 * circle-AA path without burning a slot on a pixel-perfect comparison.
 */
class CgimageTest {

    @Test
    fun `CgimageGM matches cgimage_png within tolerance`() {
        val gm = CgimageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image cgimage.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("CgimageGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CgimageGM", comparison.similarity)
        assertTrue(accepted, "CgimageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 1.0,
            "CgimageGM similarity ${"%.2f".format(comparison.similarity)}% < 1% floor",
        )
    }
}
