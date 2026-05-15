package org.skia.foundation.emoji

import org.skia.foundation.SkData
import org.skia.foundation.SkTypeface

/**
 * R-final.S **STUB.EMOJI_TABLES** — surface stub for the per-format
 * colour-emoji typeface factories upstream Skia builds via
 * FreeType (or librsvg, for the `SVG` table).
 *
 * Real-world colour-emoji fonts ship one of four glyph tables :
 *  - **CBDT/CBLC** (PNG bitmap glyphs, used by Noto Color Emoji
 *    until 2018) — needs FreeType's `FT_LOAD_COLOR` flag ;
 *  - **sbix** (Apple's PNG-in-OT format, used by Apple Color Emoji) —
 *    needs FreeType + bitmap-emit ;
 *  - **COLR v0** (RGBA layers, used by some legacy Microsoft / Twitter
 *    sets) — could be ported to pure Kotlin (each layer is a flat
 *    glyph + a palette index), but the current AWT scaler doesn't
 *    expose layer iteration ;
 *  - **SVG-in-OT** (the `SVG` table, used by Mozilla Twemoji /
 *    EmojiOne) — needs librsvg or a pure-Kotlin SVG renderer (which
 *    we *do* have, via `org.skia.svg.SkSVGDOM`, but the
 *    table-extraction + glyph-id-to-SVG mapping is non-trivial).
 *
 * The stub exists so direct ports of `gm/scaledemoji.cpp`,
 * `gm/scaledemoji_rendering.cpp` and `gm/coloremoji_blendmodes.cpp`
 * (see
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md))
 * compile and reference the documented surface.
 */
@Suppress("UNUSED_PARAMETER")
public object EmojiTypeface {

    /**
     * The four colour-emoji glyph-table dialects we surface, mirroring
     * upstream's FreeType dispatch.
     */
    public enum class Format {
        /** Apple's PNG-in-sbix table, used by Apple Color Emoji. */
        Sbix,

        /** Google's CBDT/CBLC PNG bitmap table, used by Noto Color Emoji
         *  (legacy, pre-COLRv1 cut-over). */
        CBDT,

        /** OpenType COLR v0 — flat layered glyphs with palette colours. */
        COLRv0,

        /** OpenType SVG table — vector glyphs as in-table SVG documents. */
        SVG,
    }

    /**
     * Build a colour-emoji typeface from the given [data] in the
     * declared [format]. Always throws — see class doc for which
     * external dependencies each format would need.
     */
    public fun create(format: Format, data: SkData): SkTypeface =
        throw NotImplementedError(
            "STUB.EMOJI_TABLES: requires FreeType (and librsvg for SVG) for " +
                "$format via JNI — see API_FINALIZATION_PLAN.md.",
        )
}
