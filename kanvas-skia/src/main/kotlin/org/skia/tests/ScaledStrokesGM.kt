package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Port of Skia's `gm/scaledstrokes.cpp:ScaledStrokesGM`.
 *
 * 4 × 4 cells (4 scales × 4 shapes) × 2 panes (no-AA / AA) — each cell
 * draws one of: a "rounded square" path (4 cubic Béziers, kappa-based),
 * a circle, a rectangle, or a line. Under `scale(s, s)` the stroke
 * width is also scaled (`paint.strokeWidth = 4 / scale`), so each cell
 * shows a stroke of the same nominal device-space width regardless of
 * scale — the test pinpoints any deviation when the stroker doesn't
 * scale stroke width with the CTM.
 *
 * Reference image: `scaledstrokes.png`, 640 × 320, default white BG.
 *
 * Hits the [SkStroker.resScale] hot path under multiple shapes (rect,
 * circle, cubic-Bézier curves, line) at scales 1×, 2×, 3×, 4×.
 */
public class ScaledStrokesGM : GM() {

    override fun getName(): String = "scaledstrokes"
    override fun getISize(): SkISize = SkISize.Make(640, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        c.translate(5f, 5f)
        val size = 60f

        for (i in 0 until 2) {
            paint.isAntiAlias = (i == 1)
            for (j in 0 until 4) {
                val scale = (4 - j).toFloat()
                paint.strokeWidth = 4f / scale

                // Row 0: rounded-square cubic path.
                c.save()
                c.translate(size / 2f, size / 2f)
                c.scale(scale, scale)
                drawPath(size / 2f / scale, c, paint)
                c.restore()

                // Row 1: circle.
                c.save()
                c.translate(size / 2f, 80f + size / 2f)
                c.scale(scale, scale)
                c.drawCircle(0f, 0f, size / 2f / scale, paint)
                c.restore()

                // Row 2: rect.
                c.save()
                c.translate(0f, 160f)
                c.scale(scale, scale)
                c.drawRect(SkRect.MakeXYWH(0f, 0f, size / scale, size / scale), paint)
                c.restore()

                // Row 3: line.
                c.save()
                c.translate(0f, 240f)
                c.scale(scale, scale)
                c.drawLine(0f, 0f, size / scale, size / scale, paint)
                c.restore()

                c.translate(80f, 0f)
            }
        }
    }

    /**
     * Mirrors upstream's `draw_path` helper — a closed "rounded square"
     * built from 4 cubic Béziers using the standard kappa approximation
     * `0.5519...` (the upstream constant rounded to 9 digits).
     */
    private fun drawPath(size: SkScalar, canvas: SkCanvas, paint: SkPaint) {
        val cc = 0.551915024494f * size
        val path = SkPathBuilder()
            .moveTo(0f, size)
            .cubicTo(cc, size, size, cc, size, 0f)
            .cubicTo(size, -cc, cc, -size, 0f, -size)
            .cubicTo(-cc, -size, -size, -cc, -size, 0f)
            .cubicTo(-size, cc, -cc, size, 0f, size)
            .detach()
        canvas.drawPath(path, paint)
    }
}
