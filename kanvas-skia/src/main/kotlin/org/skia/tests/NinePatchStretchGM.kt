package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkSize

/**
 * Port of Skia's `gm/ninepatchstretch.cpp` (`NinePatchStretchGM`,
 * GM name `ninepatch-stretch`).
 *
 * Builds a 64×64 source image — red rounded rectangle (corners stay
 * intact) with a green vertical and blue horizontal middle strip
 * (8 px wide each) — then renders 8 stretched destinations through
 * [SkCanvas.drawImageNine] : 4 size variants × 2 filter modes
 * (linear / nearest), arranged on a 760 × 800 grid.
 *
 * Reference image: `ninepatch-stretch.png`, 760 × 800.
 */
public class NinePatchStretchGM : GM() {

    override fun getName(): String = "ninepatch-stretch"
    override fun getISize(): SkISize = SkISize.Make(760, 800)

    private var fImage: SkImage? = null
    private val fCenter: SkIRect = SkIRect(0, 0, 0, 0)

    private fun makeImage(canvas: SkCanvas): SkImage {
        val kFixed = 28
        val kStretchy = 8
        val kSize = 2 * kFixed + kStretchy

        val info = SkImageInfo.MakeN32Premul(kSize, kSize)
        val surface: SkSurface = canvas.makeSurface(info) ?: SkSurface.MakeRaster(info)
        val c = surface.canvas

        val sizeF = kSize.toFloat()
        var r = SkRect.MakeWH(sizeF, sizeF)
        val strokeWidth = 6f
        val radius = kFixed.toFloat() - strokeWidth / 2f

        fCenter.left = kFixed
        fCenter.top = kFixed
        fCenter.right = kFixed + kStretchy
        fCenter.bottom = kFixed + kStretchy

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = 0xFFFF0000.toInt()
        }
        c.drawRoundRect(r, radius, radius, paint)
        r = SkRect.MakeXYWH(kFixed.toFloat(), 0f, kStretchy.toFloat(), sizeF)
        paint.color = 0x8800FF00.toInt()
        c.drawRect(r, paint)
        r = SkRect.MakeXYWH(0f, kFixed.toFloat(), sizeF, kStretchy.toFloat())
        paint.color = 0x880000FF.toInt()
        c.drawRect(r, paint)

        return surface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (fImage == null) {
            fImage = makeImage(c)
        }
        val img = fImage!!

        val fixed = (img.width - fCenter.width()).toFloat()

        val sizes = arrayOf(
            SkSize.Make(fixed * 4f / 5f, fixed * 4f / 5f),  // shrink in both axes
            SkSize.Make(fixed * 4f / 5f, fixed * 4f),       // shrink in X
            SkSize.Make(fixed * 4f, fixed * 4f / 5f),       // shrink in Y
            SkSize.Make(fixed * 4f, fixed * 4f),
        )

        c.drawImage(img, 10f, 10f)

        val x = 100f
        val y = 100f

        val filters = arrayOf(
            org.skia.foundation.SkFilterMode.kLinear,
            org.skia.foundation.SkFilterMode.kNearest,
        )
        for (fm in filters) {
            for (iy in 0..1) {
                for (ix in 0..1) {
                    val i = ix * 2 + iy
                    val r = SkRect.MakeXYWH(
                        x + ix * fixed,
                        y + iy * fixed,
                        sizes[i].width,
                        sizes[i].height,
                    )
                    c.drawImageNine(img, fCenter, r, fm)
                }
            }
            c.translate(0f, 400f)
        }
    }
}
