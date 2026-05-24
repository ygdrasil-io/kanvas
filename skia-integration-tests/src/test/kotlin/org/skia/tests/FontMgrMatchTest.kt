package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Smoke-test the `fontmgr_match` GM body — renders the
 * `(weight × width)` grid + style sweep through the live
 * [org.skia.foundation.SkFontStyleSet.matchStyle] path. No PNG
 * comparison : same caveat as [FontMgrTest] — this smoke test
 * verifies the OpenType family/style matching path without pinning
 * pixels to upstream C++ rasterisation.
 */
class FontMgrMatchTest {

    @Test
    fun `FontMgrMatchGM renders without crashing`() {
        val gm = FontMgrMatchGM()
        TestUtils.runGmTest(gm)
    }
}
