package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/readpixels.cpp::ReadPixelsCodecGM`
 * (`readpixelscodec`, 27 x 108).
 *
 * Variant of [ReadPixelsGM] that drives `make_codec_image()` (a PNG-
 * decoded `SkImage`) instead of a raster bitmap. Adds a `CachingHint`
 * dimension on top of the colour-space / colour-type / alpha-type
 * matrix and also early-skips when `canvas->imageInfo().colorSpace()`
 * is null. Same set of missing prerequisites as [ReadPixelsGM] -- kept
 * as a stub ; matching test is `@Ignore`d.
 */
public class ReadPixelsCodecGM : GM() {

    override fun getName(): String = "readpixelscodec"

    override fun getISize(): SkISize = SkISize.Make(27, 108)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : see ReadPixelsGM stub KDoc for the prerequisite list.
    }
}
