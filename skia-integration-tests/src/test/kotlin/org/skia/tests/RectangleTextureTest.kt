package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.GL_TEXTURE_RECTANGLE: rectangle_texture GM requires " +
        "GrDirectContext::createBackendTexture, GrBackendFormats::MakeGL(" +
        "GR_GL_RGBA8, GR_GL_TEXTURE_RECTANGLE), and " +
        "SkImages::AdoptTextureFrom (Ganesh flavour) to create " +
        "GL_TEXTURE_RECTANGLE-backed images. The entire GM is guarded by " +
        "#ifdef SK_GL and skips on any non-OpenGL / non-rectangle-texture " +
        "capable context. kanvas-skia is a raster-only facade with no " +
        "Ganesh/GL backend — INTRACTABLE without a GPU port.",
)
class RectangleTextureTest {

    @Test
    fun `RectangleTextureGM matches reference`() {
        val gm = RectangleTextureGM()
        TestUtils.runGmTest(gm)
    }
}
