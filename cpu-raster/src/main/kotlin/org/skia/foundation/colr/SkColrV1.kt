package org.skia.foundation.colr

import org.skia.foundation.SkPath
import org.skia.foundation.SkTypeface

/**
 * R-final.S **STUB.COLR_V1** — surface stub for upstream's COLR v1
 * colour-glyph rendering path
 * (`src/ports/SkScalerContext_FreeType_common.cpp`,
 * `modules/skparagraph/.../SkColrV1.cpp`).
 *
 * COLR v1 (the OpenType colour-glyph table version 1) extends COLR
 * v0's flat per-layer palette with arbitrary **paint graphs** (linear
 * / radial / sweep gradients, transforms, blend modes, sub-glyph
 * composition). Resolving a glyph to a list of paths + paints requires
 * walking that paint graph, which upstream Skia does through FreeType
 * + HarfBuzz once a font advertises a `COLR` table version 1.
 *
 * `:kanvas-skia` does not bind FreeType / HarfBuzz, so the COLR v1
 * resolver throws. The API is exposed so direct ports of
 * `gm/colrv1.cpp` and `gm/palette.cpp` (see
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md))
 * compile and reference the documented surface.
 *
 * A future JNI binding can drop in behind this object without
 * touching call sites — make the impl an `interface` and inject a
 * native one when present.
 */
@Suppress("UNUSED_PARAMETER")
public object SkColrV1 {

    /**
     * Mirrors `void SkColrV1::DrawGlyphs(SkCanvas*, SkTypeface*,
     * const uint16_t* glyphIds, size_t count, const SkFont&)`.
     * Returns the per-glyph path graph as a flat list ; layered
     * paint application is the caller's responsibility (or a future
     * helper).
     */
    public fun makeColrV1Glyphs(typeface: SkTypeface, glyphs: ShortArray): List<SkPath> =
        throw NotImplementedError(
            "STUB.COLR_V1: requires FreeType + HarfBuzz COLR v1 path graph " +
                "resolution via JNI — see API_FINALIZATION_PLAN.md.",
        )
}
