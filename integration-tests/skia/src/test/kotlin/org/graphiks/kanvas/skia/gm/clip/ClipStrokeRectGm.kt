package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clip_strokerect.cpp` (`ClipStrokeRectGM`).
 *
 * Two AA-stroked red rects, each clipped to a tight horizontal band that
 * cuts deep into the stroke, plus a non-AA blue 2-pixel stroke marking the
 * clip rect itself. Exercises:
 *
 *   - AA-stroke coverage on rect edges (`strokeWidth = 22`, fully AA).
 *   - Clip interaction with AA fragments.
 *   - Non-AA hairline-ish stroke (width 2) on a thin rect.
 * @see https://github.com/google/skia/blob/main/gm/clip_strokerect.cpp
 */
class ClipStrokeRectGm : SkiaGm {
    override val name = "clip_strokerect"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 71.8
    override val width = 200
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint(color = Color.RED, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 22f)

        val r1 = Rect.fromXYWH(20f, 20f, 100f, 100f)
        val rect1 = Rect.fromXYWH(20f, 0f, 100f, 20f)

        canvas.save()
        canvas.clipRect(rect1)
        canvas.drawRect(r1, paint)
        canvas.restore()

        paint = paint.copy(color = Color.BLUE, strokeWidth = 2f)
        canvas.drawRect(rect1, paint)

        paint = paint.copy(color = Color.RED, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 22f)

        val r2 = Rect.fromXYWH(20f, 140f, 100f, 100f)
        val rect2 = Rect.fromXYWH(20f, 120f, 100f, 19f)

        canvas.save()
        canvas.clipRect(rect2)
        canvas.drawRect(r2, paint)
        canvas.restore()

        paint = paint.copy(color = Color.BLUE, strokeWidth = 2f)
        canvas.drawRect(rect2, paint)
    }
}
