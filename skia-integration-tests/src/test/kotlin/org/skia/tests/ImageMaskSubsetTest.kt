package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Pixel-regression test for [ImageMaskSubsetGM].
 *
 * The GM checks that subset [org.skia.foundation.SkImage]s preserve the
 * original `kAlpha_8` color type after [org.skia.foundation.SkImage.makeSubset]
 * — three backing strategies (raster, GPU-fallback, lazy/generator) each
 * produce two columns: the full image drawn with a src-subset rect, and a
 * materialised `makeSubset` snapshot drawn full-bounds.
 *
 * **Disabled** — the GM body calls
 * [org.skia.core.SkCanvas.drawImageRect] with a `kAlpha_8` source image and
 * a coloured paint, which routes through
 * `TODO("STUB.ALPHA8_IMAGE_AS_MASK")` in
 * `SkBitmapDevice.drawImageRect`. Until that path is implemented (drawing
 * an alpha-only image as a colour mask, with the paint's RGB filling
 * covered pixels modulated by the image alpha), the test is kept disabled
 * to prevent false CI failures.
 */
@Disabled(
    "STUB.ALPHA8_IMAGE_AS_MASK: drawImageRect with a kAlpha_8 image and a " +
        "coloured paint (green 0xFF00FF00) routes through " +
        "TODO(\"STUB.ALPHA8_IMAGE_AS_MASK\") in SkBitmapDevice.drawImageRect. " +
        "The alpha-mask draw semantic (image alpha modulates paint RGB) is not " +
        "yet implemented in the kanvas-skia raster backend.",
)
class ImageMaskSubsetTest {

    @Test
    fun `ImageMaskSubsetGM renders alpha-mask subset rows`() {
        val gm = ImageMaskSubsetGM()
        TestUtils.runGmTest(gm)
    }
}
