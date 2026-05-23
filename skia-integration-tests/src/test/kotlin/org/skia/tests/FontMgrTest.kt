package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Smoke-test the `fontmgr_iter` GM body — just renders into a
 * bitmap to exercise the [FontMgrGM] code path against the live
 * [org.skia.foundation.SkFontMgr] / [org.skia.foundation.SkFontStyleSet]
 * surface. No PNG comparison : the JVM AWT-backed
 * `SkFontMgr.RefDefault()` enumerates the host's fonts, so a pixel
 * diff against upstream `fontmgr_iter.png` (captured with
 * Liberation portable fonts) would fail at the family-enumeration
 * step regardless of glyph-level fidelity. See [FontMgrGM]'s KDoc
 * for the deferred portable-`SkFontMgr` path.
 */
class FontMgrTest {

    @Test
    fun `FontMgrGM renders without crashing`() {
        val gm = FontMgrGM()
        TestUtils.runGmTest(gm)
    }
}
