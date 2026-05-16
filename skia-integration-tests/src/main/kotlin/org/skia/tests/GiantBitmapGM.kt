package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix

/**
 * Port of Skia's [`gm/giantbitmap.cpp`](https://github.com/google/skia/blob/main/gm/giantbitmap.cpp)
 * `GiantBitmapGM` (640 × 480).
 *
 * Stresses the bitmap shader's sampling precision when scaling a long
 * thin striped bitmap with an extreme matrix (skew or 11/12 scale).
 *
 * 12 variants — `{Clamp,Repeat,Mirror} × {point,bilerp} × {scale,rotate}`.
 */
public open class GiantBitmapGM(
    private val mode: SkTileMode,
    private val doFilter: Boolean,
    private val doRotate: Boolean,
) : GM() {

    private var bm: SkBitmap? = null

    private fun getBitmap(): SkBitmap {
        bm?.let { return it }
        val b = SkBitmap(W, H)
        b.eraseColor(SK_ColorWHITE)
        val canvas = SkCanvas(b)
        val paint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = 20f
        }
        val colors = intArrayOf(SK_ColorBLUE, SK_ColorRED, SK_ColorBLACK, SK_ColorGREEN)
        var x = -W
        while (x < W) {
            paint.color = colors[(x / 60) and 0x3]
            val xx = x.toFloat()
            canvas.drawLine(xx, 0f, xx, H.toFloat(), paint)
            x += 60
        }
        bm = b
        return b
    }

    override fun getName(): String {
        val sb = StringBuilder("giantbitmap_")
        sb.append(when (mode) {
            SkTileMode.kClamp -> "clamp"
            SkTileMode.kRepeat -> "repeat"
            SkTileMode.kMirror -> "mirror"
            SkTileMode.kDecal -> "decal"
        })
        sb.append(if (doFilter) "_bilerp" else "_point")
        sb.append(if (doRotate) "_rotate" else "_scale")
        return sb.toString()
    }

    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint()

        val m = if (doRotate) {
            SkMatrix.MakeSkew(1f, 0f)
        } else {
            val scale = 11f / 12f
            SkMatrix.MakeScale(scale, scale)
        }
        paint.shader = getBitmap().makeShader(
            mode,
            mode,
            SkSamplingOptions(if (doFilter) SkFilterMode.kLinear else SkFilterMode.kNearest),
            m,
        )

        c.translate(50f, 50f)
        c.drawPaint(paint)
    }

    private companion object {
        const val W: Int = 257
        const val H: Int = 161
    }
}

// 12 variants for the upstream `DEF_GM` registrations.
public class GiantBitmapClampPointScale : GiantBitmapGM(SkTileMode.kClamp, false, false)
public class GiantBitmapRepeatPointScale : GiantBitmapGM(SkTileMode.kRepeat, false, false)
public class GiantBitmapMirrorPointScale : GiantBitmapGM(SkTileMode.kMirror, false, false)
public class GiantBitmapClampBilerpScale : GiantBitmapGM(SkTileMode.kClamp, true, false)
public class GiantBitmapRepeatBilerpScale : GiantBitmapGM(SkTileMode.kRepeat, true, false)
public class GiantBitmapMirrorBilerpScale : GiantBitmapGM(SkTileMode.kMirror, true, false)
public class GiantBitmapClampPointRotate : GiantBitmapGM(SkTileMode.kClamp, false, true)
public class GiantBitmapRepeatPointRotate : GiantBitmapGM(SkTileMode.kRepeat, false, true)
public class GiantBitmapMirrorPointRotate : GiantBitmapGM(SkTileMode.kMirror, false, true)
public class GiantBitmapClampBilerpRotate : GiantBitmapGM(SkTileMode.kClamp, true, true)
public class GiantBitmapRepeatBilerpRotate : GiantBitmapGM(SkTileMode.kRepeat, true, true)
public class GiantBitmapMirrorBilerpRotate : GiantBitmapGM(SkTileMode.kMirror, true, true)
