package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM port validating Phase G3's [org.skia.core.SkPicture.makeShader]
 * does **not** cache its bitmap snapshot across colour spaces.
 *
 * See [PictureShaderCacheGM] for source-spec mapping. The sibling
 * green-to-yellow surface draw is fired only for the cache-invalidation
 * side effect; the assertion compares only the final draw to the GM
 * canvas against the upstream `pictureshadercache.png`.
 *
 * Floor 90% — the canvas is small (100 × 100), the picture-shader
 * blits a green circle + red cross structure that round-trips through
 * a transient bitmap before tiling. Most of the canvas matches
 * pixel-for-pixel; the AA cap of the lines and the circle edge
 * accounts for any residual drift.
 */
class PictureShaderCacheTest {

    @Test
    fun `PictureShaderCacheGM matches pictureshadercache_png within tolerance`() {
        val gm = PictureShaderCacheGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pictureshadercache.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("PictureShaderCacheGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PictureShaderCacheGM", comparison.similarity)
        assertTrue(accepted, "PictureShaderCacheGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "PictureShaderCacheGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
