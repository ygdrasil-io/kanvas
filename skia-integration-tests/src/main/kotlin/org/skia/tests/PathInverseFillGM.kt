package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * Port of Skia's `gm/pathfill.cpp::PathInverseFillGM` (450 × 220).
 *
 * Exercises the **inverse-fill** path with a clip that completely
 * excludes the path's geometry. The GM lays out a 2 × 2 × 2 cube of
 * `(doClip, antiAlias)` × `(top-half, bottom-half)` cells :
 *  - the path is a single inverse-filled circle (centre 50,50 r=40).
 *  - each cell clips to a `0..100 × 0..200` rect, optionally further
 *    clipped to top-half or bottom-half via the `show()` helper's
 *    `top`/`bottom` slice.
 *
 * Expected output : every cell is fully painted black except for a
 * circular hole in the upper half of each `200 px`-tall column. The
 * inverse-fill is what makes the *exterior* of the circle visible,
 * including across the slice clip boundaries.
 */
public class PathInverseFillGM : GM() {

    override fun getName(): String = "pathinvfill"
    override fun getISize(): SkISize = SkISize.Make(450, 220)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .addCircle(50f, 50f, 40f)
            .toggleInverseFillType()
            .detach()

        val clipR = SkRect.MakeLTRB(0f, 0f, 100f, 200f)

        c.translate(10f, 10f)

        for (doclip in 0..1) {
            for (aa in 0..1) {
                val paint = SkPaint().apply { isAntiAlias = (aa != 0) }

                c.save()
                c.clipRect(clipR)

                val clipPtr: SkRect? = if (doclip != 0) clipR else null

                show(c, path, paint, clipPtr, clipR.top, clipR.centerY())
                show(c, path, paint, clipPtr, clipR.centerY(), clipR.bottom)

                c.restore()
                c.translate(110f, 0f)
            }
        }
    }

    private fun show(
        canvas: SkCanvas,
        path: SkPath,
        paint: SkPaint,
        clip: SkRect?,
        top: SkScalar,
        bottom: SkScalar,
    ) {
        canvas.save()
        if (clip != null) {
            val r = SkRect.MakeLTRB(clip.left, top, clip.right, bottom)
            canvas.clipRect(r)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
