package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkTypeface
import org.skia.foundation.colr.SkColrV1
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.COLR_V1** consumer GM. Iso-aligned port of
 * upstream's `gm/colrv1.cpp` (which renders a colour-emoji glyph
 * via the FreeType + HarfBuzz COLR v1 paint-graph resolver).
 *
 * The body touches [SkColrV1.makeColrV1Glyphs] so the compile
 * contract holds ; [ColrV1Test] is `@Disabled` because the
 * dispatch throws `STUB.COLR_V1`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.COLR_V1.
 */
public class ColrV1GM : GM() {

    override fun getName(): String = "colrv1"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.COLR_V1 at runtime.
        SkColrV1.makeColrV1Glyphs(SkTypeface.MakeEmpty(), shortArrayOf(0))
    }
}
