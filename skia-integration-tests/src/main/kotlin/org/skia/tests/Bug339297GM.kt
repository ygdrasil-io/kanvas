package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/strokefill.cpp::bug339297` (DEF_SIMPLE_GM, 640 × 480).
 *
 * A giant cubic-shaped near-horizontal sliver at y = -10 365 663 with a
 * `translate(258, 10 365 663)` that brings it back into device range.
 * Drawn twice : black filled then red 1-px stroked. Stresses path-bounds
 * arithmetic and CTM-aware float precision when source coords sit
 * 10⁷ orders of magnitude away from the visible window.
 */
public class Bug339297GM : GM() {

    override fun getName(): String = "bug339297"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(-469515f, -10354890f)
            .cubicTo(771919.62f, -10411179f, 2013360.1f, -10243774f, 3195542.8f, -9860664f)
            .lineTo(3195550f, -9860655f)
            .lineTo(3195539f, -9860652f)
            .lineTo(3195539f, -9860652f)
            .lineTo(3195539f, -9860652f)
            .cubicTo(2013358.1f, -10243761f, 771919.25f, -10411166f, -469513.84f, -10354877f)
            .lineTo(-469515f, -10354890f)
            .close()
            .detach()

        c.translate(258f, 10365663f)

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLACK
            style = SkPaint.Style.kFill_Style
        }
        c.drawPath(path, paint)

        paint.color = SK_ColorRED
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 1f
        c.drawPath(path, paint)
    }
}
