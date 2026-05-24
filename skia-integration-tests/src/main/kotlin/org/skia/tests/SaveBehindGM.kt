package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(save_behind, canvas, 830, 670)`.
 *
 * Tests `SkCanvasPriv::SaveBehind` / `SkCanvasPriv::DrawBehind` — private
 * Skia API that saves a snapshot of a partial canvas region before drawing
 * foreground content, then restores and composites it. The GM draws two
 * columns of eight coloured rows; each row demonstrates a "fade-out" effect
 * at the right edge by:
 *  1. Calling `SkCanvasPriv::SaveBehind(&r)` to snapshot the right margin.
 *  2. Drawing foreground text over the full row.
 *  3. Blending a linear gradient (`DstIn`) over the snapshotted region to
 *     make the text fade into transparency.
 *  4. Restoring the "behind" snapshot, compositing the gradient-blended
 *     result on top.
 *
 * The left column uses `drawRect` to apply the gradient treatment;
 * the right column uses `SkCanvasPriv::DrawBehind` (draws directly into
 * the saved-behind layer without clipping the current draw).
 *
 * The upstream calls are private; the Kotlin port exposes raster-only
 * [SkCanvas.saveBehind] / [SkCanvas.drawBehind] shims for this GM.
 */
public class SaveBehindGM : GM() {

    override fun getName(): String = "save_behind"
    override fun getISize(): SkISize = SkISize.Make(830, 670)

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        val font = ToolUtils.DefaultPortableFont(30f)
        val blob = SkTextBlob.MakeFromString("This is a very long line of text", font) ?: return

        for (useDrawBehind in listOf(false, true)) {
            canvas.save()

            drawList(canvas, blob, useDrawBehind)
            canvas.translate(0f, 350f)
            canvas.saveLayer(SkRect.MakeLTRB(0f, 0f, 400f, 320f), null)
            drawList(canvas, blob, useDrawBehind)
            canvas.restore()

            canvas.restore()
            canvas.translate(430f, 0f)
        }
    }

    private fun drawList(canvas: SkCanvas, blob: SkTextBlob, useDrawBehind: Boolean) {
        canvas.save()
        val rand = SkRandom()
        repeat(8) {
            val color = (rand.nextU() and 0x00FFFFFF) or 0x80000000.toInt()
            drawCell(canvas, blob, color, 400f, 40f, useDrawBehind)
            canvas.translate(0f, 40f)
        }
        canvas.restore()
    }

    private fun drawCell(canvas: SkCanvas, blob: SkTextBlob, color: Int, w: Float, h: Float, useDrawBehind: Boolean) {
        val r = SkRect.MakeWH(w, h)
        val paint = SkPaint().apply {
            this.color = color
            blendMode = SkBlendMode.kSrc
        }
        canvas.drawRect(r, paint)

        paint.blendMode = SkBlendMode.kSrcOver
        val margin = 80f
        r.left = w - margin

        canvas.saveBehind(r)
        paint.color = 0xFF000000.toInt()
        canvas.drawTextBlob(blob, 10f, 30f, paint)

        paint.shader = SkLinearGradient.Make(
            SkPoint(r.left, 0f),
            SkPoint(r.right, 0f),
            intArrayOf(0x88000000.toInt(), 0x00000000),
            null,
            SkTileMode.kClamp,
        )
        paint.blendMode = SkBlendMode.kDstIn

        if (useDrawBehind) {
            canvas.drawBehind(paint)
        } else {
            canvas.drawRect(r, paint)
        }
        canvas.restore()
    }
}
