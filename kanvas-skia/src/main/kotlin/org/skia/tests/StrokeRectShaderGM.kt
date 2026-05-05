package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/stroke_rect_shader.cpp::stroke_rect_shader`
 * (DEF_SIMPLE_GM, 690 × 300).
 *
 * Stroked rects with all join flavours — bevel, miter, miter-limited-
 * to-bevel (`strokeMiter = 0.01`), round, plus a hairline (`strokeWidth
 * = 0`) — under both AA on and AA off, painted with a linear gradient
 * shader so the source colour drifts across each rect. The initial
 * `translate(50, 50)` ensures local-shader coords disagree with device
 * coords for the first rect (stresses the inverse-CTM path in the
 * shader sampler).
 */
public class StrokeRectShaderGM : GM() {

    override fun getName(): String = "stroke_rect_shader"
    override fun getISize(): SkISize = SkISize.Make(690, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val pts = arrayOf(SkPoint.Make(rect.left, rect.top), SkPoint.Make(rect.right, rect.bottom))
        val colors = intArrayOf(SK_ColorRED, SK_ColorBLUE)
        val shader = SkLinearGradient.Make(pts[0], pts[1], colors, null, SkTileMode.kClamp)

        // Large initial translate so local coords differ from device coords for the first rect.
        c.translate(rect.centerX(), rect.centerY())

        val pad = 20f
        for (aa in arrayOf(false, true)) {
            val paint = SkPaint().apply {
                this.shader = shader
                style = SkPaint.Style.kStroke_Style
                isAntiAlias = aa
            }
            c.save()

            val strokeWidth = 10f
            paint.strokeWidth = strokeWidth

            paint.strokeJoin = SkPaint.Join.kBevel_Join
            c.drawRect(rect, paint)
            c.translate(rect.width() + pad, 0f)

            paint.strokeJoin = SkPaint.Join.kMiter_Join
            c.drawRect(rect, paint)
            c.translate(rect.width() + pad, 0f)

            // Miter limit < ~1.4 forces every miter to fall back to bevel.
            paint.strokeMiter = 0.01f
            c.drawRect(rect, paint)
            c.translate(rect.width() + pad, 0f)

            paint.strokeJoin = SkPaint.Join.kRound_Join
            c.drawRect(rect, paint)
            c.translate(rect.width() + pad, 0f)

            paint.strokeWidth = 0f      // hairline
            c.drawRect(rect, paint)

            c.restore()
            c.translate(0f, rect.height() + pad)
        }
    }
}
