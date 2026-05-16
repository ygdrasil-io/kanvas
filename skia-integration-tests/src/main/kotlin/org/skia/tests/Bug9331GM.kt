package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bug9331.cpp::bug9331` (256 × 256).
 *
 * Reproduces skbug.com/40040651 — `clipRect` followed by
 * `drawRect(stroke + dash)` rendered differently in debug vs release
 * builds. The fix forced the same path through both pipelines.
 *
 *  - Translucent red `(0x44FF0000)` `200×150` rect at the top.
 *  - Black `dash 13/17 phase 9` stroke-10 rect inside `(50,50)→(150,150)`,
 *    clipped to `(0,0)→(200,150)`.
 *  - Same dashed rect repeated below, clipped to `(0,150)→(200,300)`,
 *    coloured blue.
 */
public class Bug9331GM : GM() {

    override fun getName(): String = "bug9331"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val clip = SkRect.MakeLTRB(0f, 0f, 200f, 150f)

        run {
            val p = SkPaint().apply { color = 0x44FF0000.toInt() }
            c.drawRect(clip, p)
        }

        fun draw(color: Int, clipRect: SkRect) {
            val intervals = floatArrayOf(13f, 17f)
            val phase = 9f
            val p = SkPaint().apply {
                this.color = color
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 10f
                pathEffect = SkDashPathEffect.Make(intervals, phase)
            }
            c.save()
            c.clipRect(clipRect)
            // Substitute drawRect → drawPath: the canvas drawRect fast
            // path bypasses paint.pathEffect when the CTM is axis-
            // aligned and shader is null. Forcing through drawPath
            // honours the dasher.
            c.drawPath(SkPath.Rect(SkRect.MakeLTRB(50f, 50f, 150f, 150f)), p)
            c.restore()
        }

        draw(0xFF000000.toInt(), clip)
        draw(0xFF0000FF.toInt(), clip.makeOffset(0f, 150f))
    }
}
