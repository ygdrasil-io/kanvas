package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/rrect.cpp::RRectBlurGM`
 * (`rrect_blurs`, 300 x 400).
 *
 * Upstream paints 4 rows of `(drawRRect, diff, drawPath)` cells with
 * `SkMaskFilter::MakeBlur(kNormal, sigma, respectCTM=false)`, then
 * reads the rrect / path pixels back via `canvas->readPixels`,
 * synthesises a diff bitmap, and writes it back via
 * `canvas->writePixels`. The diff visualisation is the *point* of the
 * GM -- without `SkCanvas::readPixels` / `writePixels` the result
 * would not match the reference at all.
 *
 * `:kanvas-skia` does not currently expose `SkCanvas.readPixels` /
 * `SkCanvas.writePixels` (only [SkBitmap] supports the bulk pixel
 * accessors). The GM is kept as a stub so downstream tooling can
 * still reference the class ; the matching test is `@Ignore`d.
 */
public class RRectBlurGM : GM() {

    override fun getName(): String = "rrect_blurs"

    override fun getISize(): SkISize = SkISize.Make(300, 400)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : SkCanvas.readPixels / writePixels not implemented.
    }
}
