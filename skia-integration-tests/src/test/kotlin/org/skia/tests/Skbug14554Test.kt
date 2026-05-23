package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.EDGE_AA_IMAGE_SET: SkCanvas::experimental_DrawEdgeAAImageSet is not implemented; the draw_image_set procedure in this GM will throw NotImplementedError at runtime")
class Skbug14554Test {

    @Test
    fun `Skbug14554GM placeholder`() {
        TestUtils.runGmTest(Skbug14554GM())
    }
}
