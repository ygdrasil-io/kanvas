package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/readpixels.cpp::ReadPixelsGM`
 * (`readpixels`, 384 x 405).
 *
 * Upstream iterates over `(SkColorSpace, SkColorType, SkAlphaType)`
 * cartesian products, building a raster `SkImage` from each
 * (`make_raster_image(srcColorType)`), uploading to a texture if a
 * GPU context is available, then reading the pixels back into
 * various destination `(colorType, alphaType, colorSpace)` formats
 * via `image->readPixels` and re-drawing the converted bitmap.
 *
 * The GM relies on :
 *  - `make_wide_gamut()` / `make_small_gamut()` colour-space helpers
 *    (not present in `:kanvas-skia` yet),
 *  - `SkImage.readPixels` with arbitrary destination formats
 *    (the current implementation only round-trips to the source
 *    bitmap's storage), and
 *  - the `DrawResult` return convention used to early-skip when
 *    the canvas has no colour space.
 *
 * Kept as a stub for class-shape parity ; the matching test is
 * `@Ignore`d.
 */
public class ReadPixelsGM : GM() {

    override fun getName(): String = "readpixels"

    override fun getISize(): SkISize = SkISize.Make(384, 405)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : requires make_wide_gamut / make_small_gamut, SkImage.readPixels
        // with arbitrary dst format, and DrawResult conventions.
    }
}
