package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Test driver for [TypefaceRenderingPfbGM] (`typefacerendering_pfb.png`).
 *
 * **Disabled — STUB.FIXTURE** : `fonts/Roboto2-Regular.pfb` is not present
 * in the kanvas-skia classpath resources. The GM's
 * [TypefaceRenderingPfbGM.onDraw] calls `TODO("STUB.FIXTURE: …")` when
 * [org.skia.tools.ToolUtils.CreateTypefaceFromResource] returns `null`,
 * so executing this test would always throw [NotImplementedError].
 *
 * Additionally, AWT's `Font.createFont` does not support Type 1 PFB
 * format on all JVMs, so even if the file were present, loading it
 * would require a JNI-backed FreeType pipeline.
 *
 * Re-enable when the fixture font is added and a Type 1 typeface
 * backend is implemented.
 */
@Disabled("STUB.FIXTURE: fonts/Roboto2-Regular.pfb not available — see TypefaceRenderingPfbGM kdoc")
class TypefaceRenderingPfbTest {

    @Test
    fun `TypefaceRenderingPfbGM matches typefacerendering_pfb_png within tolerance`() {
        val gm = TypefaceRenderingPfbGM()
        gm.draw(null)
    }
}
