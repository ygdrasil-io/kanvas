package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RSXShaderTest {

    @Test
    @Disabled(
        "Requires SkTextBlobBuilder.allocRunRSXform (not implemented). " +
            "RSXShaderGM stays as a stub.",
    )
    fun `RSXShaderGM matches rsx_blob_shader_png within tolerance`() {
        // Intentionally empty -- see @Disabled.
    }
}
