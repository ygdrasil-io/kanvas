package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class TrimTest {

    @Test
    @Disabled(
        "SkTrimPathEffect not implemented in :kanvas-skia yet. " +
            "TrimGM stays as a stub; revive this test once the effect lands.",
    )
    fun `TrimGM matches trimpatheffect_png within tolerance`() {
        // Intentionally empty -- see @Disabled.
    }
}
