package org.skia.foundation

/**
 * R-final.S **STUB.LIBERATION_FM** — surface stub for upstream's
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
 * **What ships today** : the equivalent functionality already lives
 * in [org.skia.foundation.awt.LiberationFontMgr] (an `internal`
 * object backed by AWT, see kdoc there for the design rationale).
 * Production callers that need a portable typeface should reach for
 * [org.skia.tools.ToolUtils.DefaultPortableTypeface] instead — that
 * routes through the AWT-backed Liberation manager and returns a
 * fully functional [SkTypeface].
 *
 * The factory below stays as a stub because the *exact* upstream
 * shape — `LiberationFontMgr::Make()` returning a public [SkFontMgr]
 * sub-class with full `SkFontMgr` semantics (style sets, family
 * iteration, fallback resolution) — would need exposing the
 * internal AWT helper as a public-API SkFontMgr, which carries
 * back-compat risk we're not absorbing in R-final.S.
 */
@Suppress("UNUSED_PARAMETER")
public object LiberationFontMgr {

    /**
     * Mirrors `sk_sp<SkFontMgr> LiberationFontMgr::Make()`. Always
     * throws ; consumers should use
     * [org.skia.tools.ToolUtils.DefaultPortableTypeface] (or
     * `JvmAwtFontMgr.Make()` once it ships) instead.
     */
    @Suppress("FunctionName")
    public fun Make(): SkFontMgr = throw NotImplementedError(
        "STUB.LIBERATION_FM: requires Skia internal portable family resolution — " +
            "use ToolUtils.DefaultPortableTypeface() (AWT-backed Liberation) instead. " +
            "See API_FINALIZATION_PLAN.md.",
    )
}
