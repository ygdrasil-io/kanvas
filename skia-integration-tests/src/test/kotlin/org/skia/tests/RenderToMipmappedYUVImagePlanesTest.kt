package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.YUVA_PIXMAPS: render_to_mipmapped_yuv_image_planes is Graphite GPU-only — " +
        "requires SkColorMatrix.RGBtoYUV, SkYUVAInfo.toYUVALocations, " +
        "SkImages.TextureFromYUVAImages, and SkSurfaces.RenderTarget(recorder, mipmapped)"
)
class RenderToMipmappedYUVImagePlanesTest {

    @Test
    fun `RenderToMipmappedYUVImagePlanesGM matches reference`() {
        TestUtils.runGmTest(RenderToMipmappedYUVImagePlanesGM())
    }
}
