package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/shaderpath.cpp::ShaderPathGM` (820 × 930).
 *
 * Cubic curves filled with a 75×75 bitmap shader (two crossing linear
 * gradients) exercised under the 4 combinations of `kRepeat` / `kMirror`
 * tile modes on each axis, with a non-trivial local matrix
 * (`translate(5,5) ; rotate(20°) ; scale(1.15, 0.85)`). Each cell pairs
 * a filled path (current shader) with a hairline outline so the path
 * geometry is visible against the tiled bg.
 */
public class ShaderPathGM : GM() {

    private var fBmp: SkBitmap = SkBitmap(75, 75, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)

    init { setBGColor(0xFFDDDDDDu.toInt()) }

    override fun getName(): String = "shaderpath"
    override fun getISize(): SkISize = SkISize.Make(820, 930)

    override fun onOnceBeforeDraw() {
        // Replicate the 75×75 cross-gradient bg bitmap from upstream's
        // `makebm()` (using direct ARGB ints since we don't have
        // SkColorConverter).
        fBmp.eraseColor(0)
        val bmCanvas = SkCanvas(fBmp)
        val w = 75f
        val pos = floatArrayOf(0f, 0.5f, 1f)
        val colors0 = intArrayOf(0x80F00080.toInt(), 0xF0F08000.toInt(), 0x800080F0.toInt())
        val colors1 = intArrayOf(0xF08000F0.toInt(), 0x8080F000.toInt(), 0xF000F080.toInt())

        val paint = SkPaint().apply {
            shader = SkLinearGradient.Make(
                SkPoint(0f, 0f), SkPoint(w, w),
                colors0, pos, SkTileMode.kClamp,
            )
        }
        bmCanvas.drawPaint(paint)
        paint.shader = SkLinearGradient.Make(
            SkPoint(w / 2, 0f), SkPoint(w / 2, w),
            colors1, pos, SkTileMode.kClamp,
        )
        bmCanvas.drawPaint(paint)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sampling = SkSamplingOptions.Default

        val bmpPaint = SkPaint().apply {
            isAntiAlias = true
            alphaf = 0.5f
        }
        c.drawImage(fBmp.asImage(), 5f, 5f, sampling, bmpPaint)

        val outlinePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }

        c.translate(15f, 15f)
        c.scale(2f, 2f)
        c.translate(0f, 2.25f)

        val path = SkPathBuilder()
            .moveTo(0f, 40f)
            .cubicTo(10f, 70f, 20f, 10f, 30f, 40f)
            .detach()

        val tileModes = arrayOf(SkTileMode.kRepeat, SkTileMode.kMirror)

        c.save()
        var i = 0
        for (tm0 in tileModes.indices) {
            for (tm1 in tileModes.indices) {
                val localM = SkMatrix.MakeTrans(5f, 5f).postRotate(20f).postScale(1.15f, 0.85f)

                val fillPaint = SkPaint().apply {
                    isAntiAlias = true
                    shader = fBmp.makeShader(tileModes[tm0], tileModes[tm1], sampling, localM)
                }
                c.drawPath(path, fillPaint)
                c.drawPath(path, outlinePaint)
                c.translate(50f, 0f)
                i++
                if ((i and 1) == 0) {
                    c.restore()
                    c.translate(0f, 22.5f)
                    c.save()
                }
            }
        }
        c.restore()
    }
}
