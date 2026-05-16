package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkTypeface_Fontations
import org.skia.foundation.stream.SkMemoryStream
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.FONTATIONS** consumer GM. Iso-aligned port of
 * upstream's `gm/fontations_ft_compare.cpp` (which renders the same
 * glyph through the FreeType scaler *and* the Rust
 * [`fontations`](https://github.com/googlefonts/fontations) scaler
 * side-by-side, to spot bit-level differences).
 *
 * The body touches [SkTypeface_Fontations.MakeFromStream] so the
 * compile contract holds. [FontationsFtCompareTest] is `@Disabled`
 * because the fontations dispatch throws `STUB.FONTATIONS`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.FONTATIONS.
 */
public class FontationsFtCompareGM : GM() {

    override fun getName(): String = "fontations-ft-compare"
    override fun getISize(): SkISize = SkISize.Make(640, 320)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.FONTATIONS at runtime.
        SkTypeface_Fontations.MakeFromStream(SkMemoryStream(ByteArray(0)))
    }
}
