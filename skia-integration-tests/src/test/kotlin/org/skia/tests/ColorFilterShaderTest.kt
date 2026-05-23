package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * `@Disabled` — [ColorFilterShaderGM] calls [org.skia.foundation.SkShader.makeWithColorFilter]
 * which is not yet implemented in `:kanvas-skia`. The GM body runs up to the first
 * `shader.makeWithColorFilter(filter)` invocation and then throws
 * [NotImplementedError] with the `STUB.MAKE_WITH_COLOR_FILTER` tag.
 *
 * Drop this `@Disabled` and ratchet the similarity score once
 * `SkColorFilterShader` (Skia's private `makeWithColorFilter` result class)
 * is ported and `SkShader.makeWithColorFilter` returns a working shader
 * instead of throwing.
 */
class ColorFilterShaderTest {

    @Test
    @Disabled(
        "STUB.MAKE_WITH_COLOR_FILTER: ColorFilterShaderGM calls " +
            "SkShader.makeWithColorFilter which throws NotImplementedError — " +
            "port SkColorFilterShader to enable this test.",
    )
    fun `ColorFilterShaderGM matches colorfiltershader_png within tolerance`() {
        // Body intentionally left blank — the GM body throws at the
        // shader.makeWithColorFilter(filter) call site.
    }
}
