package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/luminosity.cpp::luminosity_overflow` (DEF_SIMPLE_GM,
 * 256 × 256).
 *
 * Reproduces b/359049360 : the `kLuminosity` blend formula divides by
 * destination luminance, producing black "overflow" boxes when low-alpha
 * white is drawn over bright backgrounds on certain GPUs. Our raster
 * pipeline operates in premul float and matches the W3C reference
 * implementation, so we expect smooth near-white results everywhere
 * (no black boxes).
 *
 * 64 vertical 4-px-wide strips ({243, 247, 251, 255} ³ permutations)
 * fill the canvas, then 16 alpha-stepped (1..16) full-width white rects
 * draw with `kLuminosity`, descending from y=0.
 */
public class LuminosityOverflowGM : GM() {

    override fun getName(): String = "luminosity_overflow"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rgbs = intArrayOf(243, 247, 251, 255)

        c.save()
        for (r in rgbs) {
            for (g in rgbs) {
                for (b in rgbs) {
                    val p = SkPaint()
                    p.color = SkColorSetARGB(255, r, g, b)
                    c.drawRect(SkRect.MakeWH(4f, 256f), p)
                    c.translate(4f, 0f)
                }
            }
        }
        c.restore()

        for (a in 1..16) {
            val p = SkPaint()
            p.color = SkColorSetARGB(a, 255, 255, 255)
            p.blendMode = SkBlendMode.kLuminosity
            c.drawRect(SkRect.MakeWH(256f, 16f), p)
            c.translate(0f, 16f)
        }
    }
}
