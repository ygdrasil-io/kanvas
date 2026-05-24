package org.skia.foundation

import org.skia.foundation.opentype.LiberationOpenTypeFontMgr

/**
 * Portable surface for upstream's
 * `LiberationFontMgr` / `MakePortableFontMgr` factory
 * (`tools/fonts/TestFontMgr.cpp`). Mirrors the public symbol
 * downstream code expects when porting `gm/fontmgr.cpp` (see
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)).
 *
 * **Why a stub** : upstream Skia ships a *portable* font manager that
 * resolves "monospace" / "sans-serif" / "serif" + style to one of the
 * 12 Liberation TTF subfaces, hard-baked into Skia's binary via
 * pre-extracted `test_font_*.inc`. The lookup is internal-Skia and
 * not exposed as a public C++ class.
 *
 * The factory returns the pure-Kotlin OpenType-backed manager, so callers
 * get deterministic portable fonts without depending on AWT or native
 * font stacks.
 */
public object LiberationFontMgr {

    /**
     * Mirrors `sk_sp<SkFontMgr> LiberationFontMgr::Make()`.
     */
    @Suppress("FunctionName")
    public fun Make(): SkFontMgr = LiberationOpenTypeFontMgr.Create()
}
