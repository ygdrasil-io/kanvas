package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/spritebitmap.cpp::SpriteBitmapGM` (640 × 480).
 *
 * Compares output of drawSprite-style and drawBitmap-style image
 * draws when paired with an image filter and an optional clipRect.
 * The 100×100 source bitmap is blue with a red AA-circle ; we draw
 * it 4 times :
 *
 *  - no filter, no clip
 *  - σ=8 Blur image filter, no clip
 *  - no filter, clipRect tight to bitmap rect (with 5-px inset)
 *  - σ=8 Blur image filter + clipRect
 *
 * Validates that the Phase 7d.2 `SkImageFilters::Blur` end-to-end
 * pipeline composes correctly with both the layer-paint blur path
 * and a tight clipRect that should bound the blur halo.
 */
public class SpriteBitmapGM : GM() {

    override fun getName(): String = "spritebitmap"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    private fun makeBm(): SkBitmap {
        val bm = SkBitmap(100, 100).also { it.eraseColor(SK_ColorBLUE) }
        val canvas = SkCanvas(bm)
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
        }
        canvas.drawCircle(50f, 50f, 50f, paint)
        return bm
    }

    private fun draw1Bitmap(c: SkCanvas, bm: SkBitmap, doClip: Boolean, dx: Int, dy: Int, blur: Boolean) {
        // Iso with upstream `SkAutoCanvasRestore acr(canvas, true);` (outer guard).
        // The inner `save()` / `restore()` pair under `if (doClip)` matches upstream's
        // bare pattern (intentional, mirrors `gm/spritebitmap.cpp:53,59`).
        c.withSave {
            val clipR = SkRect.MakeXYWH(dx.toFloat(), dy.toFloat(), bm.width.toFloat(), bm.height.toFloat())
            val insetClip = SkRect.MakeLTRB(clipR.left + 5f, clipR.top + 5f, clipR.right - 5f, clipR.bottom - 5f)

            val paint = SkPaint()
            if (blur) {
                paint.imageFilter = SkImageFilters.Blur(8f, 8f, null)
            }

            translate(bm.width + 20f, 0f)

            if (doClip) {
                save()
                clipRect(insetClip)
            }
            drawImage(bm.asImage(), dx.toFloat(), dy.toFloat(), SkSamplingOptions.Default, paint)
            if (doClip) {
                restore()
            }
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bm = makeBm()
        val dx = 10
        var dy = 10

        draw1Bitmap(c, bm, doClip = false, dx, dy, blur = false)
        dy += bm.height + 20
        draw1Bitmap(c, bm, doClip = false, dx, dy, blur = true)
        dy += bm.height + 20
        draw1Bitmap(c, bm, doClip = true, dx, dy, blur = false)
        dy += bm.height + 20
        draw1Bitmap(c, bm, doClip = true, dx, dy, blur = true)
    }
}
