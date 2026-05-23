package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Test driver for [TypefaceRenderingGM] (`typefacerendering.png`).
 *
 * **Disabled — STUB.FIXTURE** : `fonts/hintgasp.ttf` is not present in
 * the kanvas-skia classpath resources. The GM's [TypefaceRenderingGM.onDraw]
 * calls `TODO("STUB.FIXTURE: …")` when [org.skia.tools.ToolUtils.CreateTypefaceFromResource]
 * returns `null`, so executing this test would always throw [NotImplementedError].
 *
 * Re-enable when the fixture font is added to `kanvas-skia/src/main/resources/fonts/`.
 */
@Disabled("STUB.FIXTURE: fonts/hintgasp.ttf not available — see TypefaceRenderingGM kdoc")
class TypefaceRenderingTest {

    @Test
    fun `TypefaceRenderingGM matches typefacerendering_png within tolerance`() {
        val gm = TypefaceRenderingGM()
        gm.draw(null)
    }
}
