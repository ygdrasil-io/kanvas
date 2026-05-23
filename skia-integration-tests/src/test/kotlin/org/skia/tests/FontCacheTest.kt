package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet driver for [FontCacheGM].
 *
 * Upstream `gm/fontcache.cpp` is **GPU-only** — the header comment
 * says so explicitly (`"It's not necessary to run this with CPU
 * configs"`) and the reference `original-888/fontcache.png` was
 * Ganesh-rendered through a glyph-atlas configured to immediately
 * evict every entry (`fGlyphCacheTextureMaximumBytes = 0`). The
 * raster body still renders the same 9-size × 6-typeface × 4-string
 * loop with sub-pixel offsets, but :
 *  - the AWT scaler differs from FreeType on glyph edges (~1-2 ulp),
 *  - sub-pixel positioning under `font.isSubpixel = true` is
 *    quarter-phase quantised on raster vs continuous on the GPU
 *    SDF path,
 *  - 6 typeface families collapse to Liberation Serif / Sans
 *    × Italic / Regular / Bold (matches upstream's portable resolver)
 *    but the rendered bitmap pixels will not be identical to the
 *    Ganesh reference, especially at the smaller sizes (16-26 px)
 *    where hinting dominates.
 *
 * Tolerance / floor follow the textual-GM convention from
 * [BigTextTest] — `tolerance = 8` to absorb per-pixel ulp drift and a
 * `60%` floor instead of the usual `95%` because the glyph-density
 * (≈2500 draws across the 1280² canvas) amplifies any positioning
 * mismatch. The [SimilarityTracker] ratchet locks the actual
 * measurement day-to-day, so regressions are caught even when the
 * floor is loose.
 */
class FontCacheTest {

    @Test
    fun `FontCacheGM matches fontcache_png within tolerance`() {
        val gm = FontCacheGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fontcache.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("FontCacheGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FontCacheGM", comparison.similarity)
        assertTrue(accepted, "FontCacheGM regressed below ratchet")
    }
}
