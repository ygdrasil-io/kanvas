package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Disabled test for [StrokeTextNativeGM].
 *
 * Blocked by `STUB.LIBERATION_FM` — [org.skia.tools.ToolUtils.TestFontMgr]
 * (required by the `overlap` variable-font branch) throws
 * `TODO("STUB.LIBERATION_FM: …")` until a public [org.skia.foundation.SkFontMgr]
 * backed by the Liberation TTFs with full `makeFromStream` + OpenType variation
 * support is wired into `:kanvas-skia`. The font resources `fonts/Stroking.ttf`,
 * `fonts/Stroking.otf`, and `fonts/Variable.ttf` are also absent from the
 * classpath and must be bundled first.
 *
 * Remove `@Disabled` and update the ratchet once both gaps are closed.
 */
@Disabled("STUB.LIBERATION_FM: TestFontMgr() not yet backed by a public SkFontMgr with makeFromStream+variation support; Stroking.ttf/otf and Variable.ttf resources also absent from classpath")
class StrokeTextNativeTest {

    @Test
    fun `StrokeTextNativeGM placeholder`() {
        StrokeTextNativeGM()
    }
}
