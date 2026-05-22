package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RRectBlurTest {

    @Test
    @Disabled(
        "Requires SkCanvas.readPixels / writePixels (not implemented). " +
            "RRectBlurGM stays as a stub.",
    )
    fun `RRectBlurGM matches rrect_blurs_png within tolerance`() {
        // Intentionally empty -- see @Disabled.
    }
}
