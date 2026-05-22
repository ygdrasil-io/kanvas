package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Smoke-test the `fontmgr_bounds[_*]_*` GMs — renders all three
 * registrations through the live
 * [org.skia.foundation.SkFont.getBounds] /
 * [org.skia.foundation.SkFontPriv.GetFontBounds] /
 * [org.skia.foundation.SkDashPathEffect.Make] surface. No PNG
 * comparison : same caveat as [FontMgrTest] — the per-glyph bbox
 * sweep depends on the JVM AWT font catalog, not upstream's
 * Liberation portable set.
 *
 * The three registrations correspond to the three upstream
 * `DEF_GM` calls :
 *  - `fontmgr_bounds`           = `(1, 0)`           — identity
 *  - `fontmgr_bounds_0.75_0`    = `(0.75, 0)`        — x-scale
 *  - `fontmgr_bounds_1_-0.25`   = `(1, -0.25)`       — x-skew
 */
class FontMgrBoundsTest {

    @Test
    fun `FontMgrBoundsGM identity renders without crashing`() {
        TestUtils.runGmTest(FontMgrBoundsGM(1f, 0f))
    }

    @Test
    fun `FontMgrBoundsGM 0_75 scaleX renders without crashing`() {
        TestUtils.runGmTest(FontMgrBoundsGM(0.75f, 0f))
    }

    @Test
    fun `FontMgrBoundsGM negative skewX renders without crashing`() {
        TestUtils.runGmTest(FontMgrBoundsGM(1f, -0.25f))
    }
}
