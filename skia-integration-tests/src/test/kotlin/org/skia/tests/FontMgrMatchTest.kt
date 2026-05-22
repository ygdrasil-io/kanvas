package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Smoke-test the `fontmgr_match` GM body — renders the
 * `(weight × width)` grid + style sweep through the live
 * [org.skia.foundation.SkFontStyleSet.matchStyle] path. No PNG
 * comparison : same caveat as [FontMgrTest] — the JVM AWT font
 * catalog is platform-dependent, so a pixel diff against
 * `fontmgr_match.png` is not byte-stable.
 */
class FontMgrMatchTest {

    @Test
    fun `FontMgrMatchGM renders without crashing`() {
        val gm = FontMgrMatchGM()
        TestUtils.runGmTest(gm)
    }
}
