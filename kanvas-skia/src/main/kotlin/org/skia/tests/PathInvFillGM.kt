package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/pathfill.cpp::PathInverseFillGM` (450 × 220).
 *
 * Tests inverse-fill with a clip that completely excludes the geometry.
 * 4 cells = 2 (`doclip`) × 2 (`AA`). Each cell renders an inverse-
 * winding circle (`r=40` at `(50, 50)`) twice — the first half of the
 * outer clip then the second half. With inverse fill, the visible
 * area is everywhere outside the circle, modulated by the optional
 * inner clip.
 */
public class PathInvFillGM : GM() {

    override fun getName(): String = "pathinvfill"
    override fun getISize(): SkISize = SkISize.Make(450, 220)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val path: SkPath = SkPathBuilder()
            .addCircle(50f, 50f, 40f)
            .toggleInverseFillType()
            .detach()

        val clipR = SkRect.MakeLTRB(0f, 0f, 100f, 200f)
        c.translate(10f, 10f)

        for (doclip in 0..1) {
            for (aa in 0..1) {
                val paint = SkPaint().apply { isAntiAlias = aa != 0 }

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
        top: Float,
        bottom: Float,
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
