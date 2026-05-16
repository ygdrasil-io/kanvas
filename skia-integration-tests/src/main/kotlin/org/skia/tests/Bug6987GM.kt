package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/strokefill.cpp::bug6987` (DEF_SIMPLE_GM, 200 × 200).
 *
 * A tiny triangle (1-px scale) stroked with `strokeWidth = 0.0001` then
 * scaled by `50000×` so the stroker resolution matches the reference
 * rasterization. Stresses `SkStroker.resScale` on extreme CTM scale
 * (Phase 3i fix) — without it the triangle's stroke flattens to a
 * polygon at low resolution.
 */
public class Bug6987GM : GM() {

    override fun getName(): String = "bug6987"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0.0001f
            isAntiAlias = true
        }
        val path = SkPathBuilder()
            .moveTo(0.0005f, 0.0004f)
            .lineTo(0.0008f, 0.0010f)
            .lineTo(0.0002f, 0.0010f)
            .close()
            .detach()
        c.save()
        c.scale(50000f, 50000f)
        c.drawPath(path, paint)
        c.restore()
    }
}
