package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.core.SkSurface
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/image.cpp::ImageGM`
 * (`DEF_GM(return new ImageGM;)`, name `image-surface`).
 *
 * 960×1200 GM that snapshots a 64×64 raster surface in successive
 * "draw + snapshot" stages, then draws the snapshots back to the
 * outer canvas through 7 row variants : raw / modified / surface
 * direct / full-crop / over-crop / upper-left / no-crop. Upstream
 * runs 3 surface columns (WrapPixels / Raster / GPU) — kanvas-skia
 * has no GPU surface, so we render the 2 raster columns and leave
 * the GPU column slot empty (still labelled in the legend).
 */
public class ImageGM : GM() {

    override fun getName(): String = "image-surface"
    override fun getISize(): SkISize = SkISize.Make(960, 1200)

    private fun drawContents(surface: SkSurface, fill: Int) {
        val w = surface.width.toFloat()
        val h = surface.height.toFloat()
        val stroke = w / 10f
        val radius = (w - stroke) / 2f
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = fill
        }
        surface.canvas.drawCircle(w / 2f, h / 2f, radius, paint)

        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = stroke
        paint.color = SK_ColorBLACK
        surface.canvas.drawCircle(w / 2f, h / 2f, radius, paint)
    }

    private fun testSurface(canvas: SkCanvas, surf: SkSurface) {
        drawContents(surf, SK_ColorRED)
        val imgR: SkImage = surf.makeImageSnapshot()
        drawContents(surf, SK_ColorGREEN)
        val imgG: SkImage = surf.makeImageSnapshot()
        drawContents(surf, SK_ColorBLUE)

        val sampling = SkSamplingOptions.Default
        val paint = SkPaint()

        canvas.drawImage(imgR, 0f, 0f, sampling, paint)
        canvas.drawImage(imgG, 0f, 80f, sampling, paint)
        surf.draw(canvas, 0f, 160f, paint)

        val src1 = SkRect.MakeWH(surf.width.toFloat(), surf.height.toFloat())
        val src2 = SkRect.MakeLTRB(
            -surf.width.toFloat() / 2f,
            -surf.height.toFloat() / 2f,
            surf.width.toFloat(),
            surf.height.toFloat(),
        )
        val src3 = SkRect.MakeWH(surf.width.toFloat() / 2f, surf.height.toFloat() / 2f)
        val dst1 = SkRect.MakeLTRB(0f, 240f, 65f, 305f)
        val dst2 = SkRect.MakeLTRB(0f, 320f, 65f, 385f)
        val dst3 = SkRect.MakeLTRB(0f, 400f, 65f, 465f)
        val dst4 = SkRect.MakeLTRB(0f, 480f, 65f, 545f)

        canvas.drawImageRect(imgR, src1, dst1, sampling, paint)
        canvas.drawImageRect(imgG, src2, dst2, sampling, paint)
        canvas.drawImageRect(imgR, src3, dst3, sampling, paint)
        // Upstream uses the 3-arg drawImageRect overload (src defaults to
        // full image bounds). We pass the full image rect explicitly.
        val fullSrc = SkRect.MakeWH(imgG.width.toFloat(), imgG.height.toFloat())
        canvas.drawImageRect(imgG, fullSrc, dst4, sampling, paint)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(2f, 2f)

        val font = ToolUtils.DefaultPortableFont().apply { size = 8f }
        val pen = SkPaint()
        c.drawString("Original Img", 10f, 60f, font, pen)
        c.drawString("Modified Img", 10f, 140f, font, pen)
        c.drawString("Cur Surface", 10f, 220f, font, pen)
        c.drawString("Full Crop", 10f, 300f, font, pen)
        c.drawString("Over-crop", 10f, 380f, font, pen)
        c.drawString("Upper-left", 10f, 460f, font, pen)
        c.drawString("No Crop", 10f, 540f, font, pen)
        c.drawString("Pre-Alloc Img", 80f, 10f, font, pen)
        c.drawString("New Alloc Img", 160f, 10f, font, pen)
        c.drawString("GPU", 265f, 10f, font, pen)

        c.translate(80f, 20f)

        val info = SkImageInfo.MakeN32(K_W, K_H, SkAlphaType.kPremul)
        val surf0 = SkSurface.MakeRaster(info)
        val surf1 = SkSurface.MakeRaster(info)

        testSurface(c, surf0)
        c.translate(80f, 0f)
        testSurface(c, surf1)
        // GPU column intentionally left blank — see KDoc.
    }

    private companion object {
        const val K_W: Int = 64
        const val K_H: Int = 64
    }
}
