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
 * walking that paint graph; the portable text path implements the
 * supported subset in pure Kotlin.
 *
 * `:kanvas-skia` now has a pure Kotlin OpenType text path for the
 * supported COLR v1 subset. This object is a separate `cpu-raster`
 * surface kept so direct ports of `gm/palette.cpp` compile and so the
 * remaining GM migration can be tracked without reintroducing a
 * mandatory native dependency.
 *
 * Follow-up #1020 tracks either replacing this GM dependency with the
 * pure Kotlin text path or isolating it as an optional backend surface.
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
            "STUB.COLR_V1: cpu-raster surface is not wired to the pure Kotlin " +
                "OpenType COLR v1 text path yet — see #1020.",
        )
}
