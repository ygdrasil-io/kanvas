package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkTileMode
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClippedBitmapShadersTest {

    @Test
    fun `ClippedBitmapShadersGM kRepeat matches clipped-bitmap-shaders-tile_png`() {
        runVariant(SkTileMode.kRepeat, hq = false, "ClippedBitmapShadersTileGM", expected = 99.0)
    }

    @Test
    fun `ClippedBitmapShadersGM kMirror matches clipped-bitmap-shaders-mirror_png`() {
        runVariant(SkTileMode.kMirror, hq = false, "ClippedBitmapShadersMirrorGM", expected = 99.0)
    }

    @Test
    fun `ClippedBitmapShadersGM kClamp matches clipped-bitmap-shaders-clamp_png`() {
        runVariant(SkTileMode.kClamp, hq = false, "ClippedBitmapShadersClampGM", expected = 99.0)
    }

    @Test
    fun `ClippedBitmapShadersGM kRepeat hq matches clipped-bitmap-shaders-tile-hq_png`() {
        runVariant(SkTileMode.kRepeat, hq = true, "ClippedBitmapShadersTileHqGM", expected = 60.0)
    }

    @Test
    fun `ClippedBitmapShadersGM kMirror hq matches clipped-bitmap-shaders-mirror-hq_png`() {
        runVariant(SkTileMode.kMirror, hq = true, "ClippedBitmapShadersMirrorHqGM", expected = 60.0)
    }

    @Test
    fun `ClippedBitmapShadersGM kClamp hq matches clipped-bitmap-shaders-clamp-hq_png`() {
        runVariant(SkTileMode.kClamp, hq = true, "ClippedBitmapShadersClampHqGM", expected = 90.0)
    }

    private fun runVariant(mode: SkTileMode, hq: Boolean, trackerKey: String, expected: Double) {
        val gm = ClippedBitmapShadersGM(mode, hq)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerKey, comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore(trackerKey, comparison.similarity)
        assertTrue(accepted, "$trackerKey regressed below ratchet")
        assertTrue(comparison.similarity >= expected,
            "$trackerKey similarity ${"%.2f".format(comparison.similarity)}% < $expected%")
    }
}
