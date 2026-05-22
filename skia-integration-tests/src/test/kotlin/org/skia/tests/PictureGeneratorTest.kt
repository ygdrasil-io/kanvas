package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PictureGeneratorTest {
    /**
     * `PictureGeneratorGM` requires `SkImageGenerators::MakeFromPicture`,
     * which isn't exposed in kanvas-skia. See [PictureGeneratorGM] kdoc.
     */
    @Disabled("Missing API : SkImageGenerators.MakeFromPicture not ported.")
    @Test
    fun `PictureGeneratorGM is unsupported on kanvas-skia (no SkImageGenerator MakeFromPicture)`() {
        // Intentionally empty.
    }
}
