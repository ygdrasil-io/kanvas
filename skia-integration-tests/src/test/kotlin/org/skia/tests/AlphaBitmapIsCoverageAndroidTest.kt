package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Runner for [AlphaBitmapIsCoverageAndroidGM] (`alpha_bitmap_is_coverage_ANDROID`).
 *
 * Disabled — see the GM Javadoc. Two compounding reasons :
 *  1. **No fixture** : the upstream GM is gated by
 *     `SK_SUPPORT_LEGACY_ALPHA_BITMAP_AS_COVERAGE` and is not built into
 *     Skia's default DM run, so `original-888/` has no
 *     `alpha_bitmap_is_coverage_ANDROID.png` to compare against.
 *  2. **No backend toggle** : the body documents an Android-only legacy
 *     where the CPU backend treats A8 bitmaps as coverage rather than
 *     alpha. `:kanvas-skia` only implements the modern (post-fix)
 *     alpha-as-alpha path, so even with a perfect port the rendered
 *     mandrill ends up fully erased instead of carrying the round-rect
 *     border the GM advertises.
 */
@Disabled("STUB.INTRACTABLE: no original-888 fixture + requires SK_SUPPORT_LEGACY_ALPHA_BITMAP_AS_COVERAGE backend toggle")
class AlphaBitmapIsCoverageAndroidTest {

    @Test
    fun `AlphaBitmapIsCoverageAndroidGM matches reference`() {
        val gm = AlphaBitmapIsCoverageAndroidGM()
        TestUtils.runGmTest(gm)
    }
}
