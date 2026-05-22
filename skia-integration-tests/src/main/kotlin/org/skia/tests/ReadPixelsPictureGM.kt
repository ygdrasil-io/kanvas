package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/readpixels.cpp::ReadPixelsPictureGM`
 * (`readpixelspicture`, ~192 x 768).
 *
 * Variant of [ReadPixelsGM] that drives `make_picture_image()` (an
 * `SkImage` snapshot of an `SkPicture`) as the source for the
 * `(colorType, alphaType, colorSpace, cachingHint)` matrix. Same
 * missing prerequisites as [ReadPixelsGM] -- kept as a stub ;
 * matching test is `@Ignore`d.
 */
public class ReadPixelsPictureGM : GM() {

    override fun getName(): String = "readpixelspicture"

    override fun getISize(): SkISize = SkISize.Make(192, 768)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : see ReadPixelsGM stub KDoc for the prerequisite list.
    }
}
