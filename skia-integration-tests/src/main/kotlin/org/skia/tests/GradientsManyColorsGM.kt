package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/gradients_no_texture.cpp::GradientsManyColorsGM`
 * (`DEF_GM(return new GradientsManyColorsGM(true);)`, name `gradients_many`).
 *
 * 880×400, dither=true variant. 4 rows of horizontal-fade linear
 * gradients with many colour stops each (testing the analytic
 * many-stop linear path) :
 *   * row 0 : 24 jsfiddle-style stops with non-monotonic positions
 *   * row 1 : 9 alternating B/W stops (no positions)
 *   * row 2 : same 9 colours with explicit even positions
 *   * row 3 : 6 colours with paired hardstop positions
 *
 * Each row is drawn twice — once as-is, once mirrored under the row —
 * to compare each gradient with a flipped self.
 *
 * **Note** : the `dither` parameter is dropped — kanvas-skia raster
 * doesn't toggle dither.
 */
public class GradientsManyColorsGM : GM() {

    override fun getName(): String = "gradients_many"
    override fun getISize(): SkISize = SkISize.Make(880, 400)

    private data class ColorPos(val colors: IntArray, val pos: FloatArray?)

    private fun make0(): ColorPos {
        val colors = intArrayOf(
            0xFF22d1cd.toInt(), 0xFF22d1cd.toInt(), 0xFFdf4b37.toInt(), 0xFFdf4b37.toInt(),
            0xFF22d1cd.toInt(), 0xFF22d1cd.toInt(), 0xFFe6de36.toInt(), 0xFFe6de36.toInt(),
            0xFF3267ff.toInt(), 0xFF3267ff.toInt(), 0xFF9d47d1.toInt(), 0xFF9d47d1.toInt(),
            0xFF3267ff.toInt(), 0xFF3267ff.toInt(), 0xFF5cdd9d.toInt(), 0xFF5cdd9d.toInt(),
            0xFF3267ff.toInt(), 0xFF3267ff.toInt(), 0xFF9d47d1.toInt(), 0xFF9d47d1.toInt(),
            0xFF3267ff.toInt(), 0xFF3267ff.toInt(), 0xFFe3d082.toInt(), 0xFFe3d082.toInt(),
        )
        val percent = doubleArrayOf(
            1.0, 0.9510157507590116, 2.9510157507590113, 23.695886056604927,
            25.695886056604927, 25.39321881940624, 27.39321881940624, 31.849399922570655,
            33.849399922570655, 44.57735802921938, 46.57735802921938, 53.27185850805876,
            55.27185850805876, 61.95718972227316, 63.95718972227316, 69.89166004442,
            71.89166004442, 74.45795382765857, 76.45795382765857, 82.78364610713776,
            84.78364610713776, 94.52743647737229, 96.52743647737229, 96.03934633331295,
        )
        val pos = FloatArray(percent.size) { (percent[it] / 100.0).toFloat() }
        // Force endpoints to 0/1 as upstream's ColorPos.construct does.
        pos[0] = 0f
        pos[pos.size - 1] = 1f
        return ColorPos(colors, pos)
    }

    private fun make1(): ColorPos {
        val colors = intArrayOf(
            SK_ColorBLACK, SK_ColorWHITE, SK_ColorBLACK, SK_ColorWHITE,
            SK_ColorBLACK, SK_ColorWHITE, SK_ColorBLACK, SK_ColorWHITE,
            SK_ColorBLACK,
        )
        return ColorPos(colors, null)
    }

    private fun make2(): ColorPos {
        val colors = intArrayOf(
            SK_ColorBLACK, SK_ColorWHITE, SK_ColorBLACK, SK_ColorWHITE,
            SK_ColorBLACK, SK_ColorWHITE, SK_ColorBLACK, SK_ColorWHITE,
            SK_ColorBLACK,
        )
        val n = colors.size
        val pos = FloatArray(n) { it.toFloat() / (n - 1).toFloat() }
        // ColorPos.construct forces endpoints.
        pos[0] = 0f
        pos[n - 1] = 1f
        return ColorPos(colors, pos)
    }

    private fun make3(): ColorPos {
        val colors = intArrayOf(
            SK_ColorRED, SK_ColorBLUE, SK_ColorBLUE, SK_ColorGREEN, SK_ColorGREEN, SK_ColorBLACK,
        )
        val pos = floatArrayOf(0f, 0f, 0.5f, 0.5f, 1f, 1f)
        return ColorPos(colors, pos)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val procs = listOf(::make0, ::make1, ::make2, ::make3)
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(K_W.toFloat(), 0f))
        val r = SkRect.MakeWH(K_W.toFloat(), 30f)

        val paint = SkPaint()
        c.translate(40f, 20f)

        // 9 verticals across the row width (i = 0..8).
        for (i in 0..8) {
            val x = r.width() * i.toFloat() / 8f
            c.drawLine(x, 0f, x, 10000f, paint)
        }

        val drawR = SkRect.MakeLTRB(r.left - 20f, r.top, r.right + 20f, r.bottom)
        for (proc in procs) {
            val rec = proc()
            paint.shader = SkLinearGradient.Make(
                p0 = pts[0], p1 = pts[1],
                colors = rec.colors, positions = rec.pos,
                tileMode = SkTileMode.kClamp,
            )
            c.drawRect(drawR, paint)

            c.save()
            c.translate(r.centerX(), r.height() + 4f)
            c.scale(-1f, 1f)
            c.translate(-r.centerX(), 0f)
            c.drawRect(drawR, paint)
            c.restore()

            c.translate(0f, r.height() + 2f * r.height() + 8f)
        }
    }

    private companion object {
        const val K_W: Int = 800
    }
}
