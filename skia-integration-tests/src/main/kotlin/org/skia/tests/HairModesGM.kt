package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/hairmodes.cpp::HairModesGM` (640 × 480).
 *
 * 4 alpha permutations × 12 blend modes (Porter-Duff core), each
 * cell drawing :
 *   - a blue oval inset by `(W/10, H/10)`,
 *   - 24 red hairlines radiating from the centre under the cell's
 *     blend mode, with stroke width growing `(angle * 2 / 24)` so the
 *     lines start sub-pixel and end at ~2 px wide.
 *
 * Each cell renders inside a `saveLayer(bounds, null)` over a 2×2
 * checkerboard bg-shader (pixels `0xFFFFFFFF` / `0xFFCECFCE`). The
 * layer composite-back tests `compositeFrom` correctness across all
 * 12 paint blend modes.
 */
public class HairModesGM : GM() {

    private val gWidth = 64
    private val gHeight = 64
    private val W = gWidth.toFloat()
    private val H = gHeight.toFloat()

    private var fBGPaint: SkPaint = SkPaint()

    private val gModes = arrayOf(
        SkBlendMode.kClear,
        SkBlendMode.kSrc,
        SkBlendMode.kDst,
        SkBlendMode.kSrcOver,
        SkBlendMode.kDstOver,
        SkBlendMode.kSrcIn,
        SkBlendMode.kDstIn,
        SkBlendMode.kSrcOut,
        SkBlendMode.kDstOut,
        SkBlendMode.kSrcATop,
        SkBlendMode.kDstATop,
        SkBlendMode.kXor,
    )

    override fun getName(): String = "hairmodes"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onOnceBeforeDraw() {
        fBGPaint.shader = makeBgShader()
    }

    private fun makeBgShader(): SkShader {
        val bm = SkBitmap(2, 2)
        val white = 0xFFFFFFFF.toInt()
        val cc = SkColorSetARGB(0xFF, 0xCE, 0xCF, 0xCE)
        bm.setPixel(0, 0, white)
        bm.setPixel(1, 1, white)
        bm.setPixel(1, 0, cc)
        bm.setPixel(0, 1, cc)
        val m = SkMatrix.MakeScale(6f, 6f)
        return bm.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions.Default, m)
    }

    private fun drawCell(canvas: SkCanvas, mode: SkBlendMode, a0: Int, a1: Int): Float {
        val paint = SkPaint().apply { isAntiAlias = true }

        val r = SkRect.MakeLTRB(W / 10f, H / 10f, W - W / 10f, H - H / 10f)
        paint.color = SK_ColorBLUE
        paint.alpha = a0
        canvas.drawOval(r, paint)

        paint.color = SK_ColorRED
        paint.alpha = a1
        paint.blendMode = mode
        for (angle in 0 until 24) {
            val theta = angle * (2.0 * PI) / 24.0
            val x = (cos(theta) * gWidth).toFloat()
            val y = (sin(theta) * gHeight).toFloat()
            paint.strokeWidth = (angle * 2f) / 24f
            canvas.drawLine(W / 2, H / 2, W / 2 + x, H / 2 + y, paint)
        }
        return H
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bounds = SkRect.MakeWH(W, H)
        val gAlphaValue = intArrayOf(0xFF, 0x88, 0x88)

        c.translate(4f, 4f)

        for (alpha in 0 until 4) {
            c.save()
            c.save()
            for (i in gModes.indices) {
                if (i == 6) {
                    c.restore()
                    c.translate(W * 5, 0f)
                    c.save()
                }
                c.drawRect(bounds, fBGPaint)
                c.saveLayer(bounds, null)
                val dy = drawCell(c, gModes[i], gAlphaValue[alpha and 1], gAlphaValue[alpha and 2])
                c.restore()
                c.translate(0f, dy * 5 / 4)
            }
            c.restore()
            c.restore()
            c.translate(W * 5 / 4, 0f)
        }
    }
}
