package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ReadPixelsTest {

    @Test
    @Disabled(
        "Requires make_wide_gamut/make_small_gamut SkColorSpace helpers, " +
            "SkImage.readPixels with arbitrary destination format, and " +
            "DrawResult-based onDraw signature. ReadPixelsGM stays as a stub.",
    )
    fun `ReadPixelsGM matches readpixels_png within tolerance`() {
        // Intentionally empty -- see @Disabled.
    }

    @Test
    @Disabled(
        "Same prerequisites as ReadPixelsGM ; ReadPixelsCodecGM stays as a stub.",
    )
    fun `ReadPixelsCodecGM matches readpixelscodec_png within tolerance`() {
        // Intentionally empty -- see @Disabled.
    }

    @Test
    @Disabled(
        "Same prerequisites as ReadPixelsGM ; ReadPixelsPictureGM stays as a stub.",
    )
    fun `ReadPixelsPictureGM matches readpixelspicture_png within tolerance`() {
        // Intentionally empty -- see @Disabled.
    }
}
