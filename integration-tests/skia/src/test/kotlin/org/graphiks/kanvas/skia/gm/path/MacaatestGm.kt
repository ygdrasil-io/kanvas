package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/mac_aa_explorer.cpp` — `MacAAFontsGM`.
 * macOS-only CoreText + CoreGraphics font rendering test.
 * @see https://github.com/google/skia/blob/main/gm/mac_aa_explorer.cpp
 */
class MacaatestGm : SkiaGm {
    override val name = "macaatest"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 768

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.MACAATEST — requires macOS CoreText APIs (platform-specific)")
    }
}
