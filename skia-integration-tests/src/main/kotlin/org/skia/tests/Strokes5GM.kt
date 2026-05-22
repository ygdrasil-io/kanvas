package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp::Strokes5GM` (`zero_control_stroke`,
 * 400 × 800).
 *
 * Regression GM for skbug.com/40035337 — stroking curves that produce
 * degenerate tangents when `t == 0` or `t == 1`. Six paths : three
 * "stop-at-start" curves (cubic / quad / conic with a coincident first
 * control point) and three "stop-at-end" curves (the same shapes
 * mirrored). All drawn with a 40-px AA butt-capped red stroke.
 *
 * Stresses the stroker's degenerate-tangent path : without the
 * fallback, the stroker emits no geometry where the tangent vanishes.
 */
public class Strokes5GM : GM() {

    override fun getName(): String = "zero_control_stroke"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 40f
            strokeCap = SkPaint.Cap.kButt_Cap
        }

        // First group : tangent degenerates at end (last control coincident).
        var path = SkPathBuilder()
            .moveTo(157.474f, 111.753f)
            .cubicTo(128.5f, 111.5f, 35.5f, 29.5f, 35.5f, 29.5f)
            .detach()
        c.drawPath(path, p)

        path = SkPathBuilder()
            .moveTo(250f, 50f)
            .quadTo(280f, 80f, 280f, 80f)
            .detach()
        c.drawPath(path, p)

        path = SkPathBuilder()
            .moveTo(150f, 50f)
            .conicTo(180f, 80f, 180f, 80f, 0.707f)
            .detach()
        c.drawPath(path, p)

        // Second group : tangent degenerates at start (first control coincident).
        path = SkPathBuilder()
            .moveTo(157.474f, 311.753f)
            .cubicTo(157.474f, 311.753f, 85.5f, 229.5f, 35.5f, 229.5f)
            .detach()
        c.drawPath(path, p)

        path = SkPathBuilder()
            .moveTo(280f, 250f)
            .quadTo(280f, 250f, 310f, 280f)
            .detach()
        c.drawPath(path, p)

        path = SkPathBuilder()
            .moveTo(180f, 250f)
            .conicTo(180f, 250f, 210f, 280f, 0.707f)
            .detach()
        c.drawPath(path, p)
    }

    private companion object {
        const val W: Int = 400
        const val H: Int = 400
    }
}
