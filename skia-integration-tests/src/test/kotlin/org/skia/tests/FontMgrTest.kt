package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Smoke-test the `fontmgr_iter` GM body — just renders into a
 * bitmap to exercise the [FontMgrGM] code path against the live
 * [org.skia.foundation.SkFontMgr] / [org.skia.foundation.SkFontStyleSet]
 * surface. No PNG comparison : the GM now uses the deterministic
 * Liberation OpenType manager, but the upstream reference still
 * bakes in C++ rasterisation details outside this smoke test's scope.
 */
class FontMgrTest {

    @Test
    fun `FontMgrGM renders without crashing`() {
        val gm = FontMgrGM()
        TestUtils.runGmTest(gm)
    }
}
