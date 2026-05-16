package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkTypeface_Fontations
import org.skia.foundation.stream.SkMemoryStream
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.FONTATIONS** consumer GM. Iso-aligned port of
 * upstream's `gm/fontations.cpp` (which loads a TTF via Google's
 * Rust [`fontations`](https://github.com/googlefonts/fontations)
 * scaler and renders sample text at a few sizes).
 *
 * The body is a one-liner touching [SkTypeface_Fontations.MakeFromStream]
 * so the surface stays compile-pinned. [FontationsTest] is `@Disabled`
 * because the dispatch throws `STUB.FONTATIONS`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.FONTATIONS.
 */
public class FontationsGM : GM() {

    override fun getName(): String = "fontations"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.FONTATIONS at runtime.
        SkTypeface_Fontations.MakeFromStream(SkMemoryStream(ByteArray(0)))
    }
}
