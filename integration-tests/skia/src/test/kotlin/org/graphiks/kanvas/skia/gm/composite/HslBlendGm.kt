package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Port of Skia's `gm/hsl.cpp::DEF_SIMPLE_GM(hsl, canvas, 600, 100)`.
 * Demonstrates the four HSL blend modes plus Src and Dst.
 * Correct rendering shows no visible circle within the squares.
 * @see https://github.com/google/skia/blob/main/gm/hsl.cpp
 */
class HslBlendGm : SkiaGm {
    override val name = "hsl"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 100

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 12f)

        val comment = "HSL blend modes are correct when you see no circles in the squares."
        canvas.drawString(comment, 10f, 10f, font, Paint())

        val bgColor = argb(255, 0, 255, 0)
        val fgColor = argb(255, 127, 63, 127)

        data class TestCase(val mode: BlendMode, val reference: BlendFn?)

        val tests = listOf(
            TestCase(BlendMode.SRC, null),
            TestCase(BlendMode.DST, null),
            TestCase(BlendMode.HUE, ::blendHue),
            TestCase(BlendMode.SATURATION, ::blendSaturation),
            TestCase(BlendMode.COLOR, ::blendColor),
            TestCase(BlendMode.LUMINOSITY, ::blendLuminosity),
        )

        for (test in tests) {
            val r = Rect.fromLTRB(20f, 20f, 80f, 80f)

            canvas.save()
            canvas.drawRect(r, Paint(color = bgColor))
            canvas.drawRect(r, Paint(color = fgColor, blendMode = test.mode))
            val ref = test.reference
            if (ref != null) {
                val blended = blend(bgColor, fgColor, ref)
                canvas.drawCircle(50f, 50f, 20f, Paint(color = blended))
            }
            canvas.restore()

            canvas.drawString(test.mode.name.lowercase(), 20f, 90f, font, Paint())
            canvas.translate(100f, 0f)
        }
    }

    private typealias BlendFn = (dr: Float, dg: Float, db: Float,
                                  sr: FloatArray, sg: FloatArray, sb: FloatArray) -> Unit

    private fun blend(dst: Color, src: Color, mode: BlendFn): Color {
        val d = toFloats(dst)
        val s = toFloats(src)
        val sr = floatArrayOf(s[0])
        val sg = floatArrayOf(s[1])
        val sb = floatArrayOf(s[2])
        mode(d[0], d[1], d[2], sr, sg, sb)
        return fromFloats(sr[0], sg[0], sb[0], s[3])
    }

    private fun fmin(r: Float, g: Float, b: Float): Float = min(r, min(g, b))
    private fun fmax(r: Float, g: Float, b: Float): Float = max(r, max(g, b))
    private fun sat(r: Float, g: Float, b: Float): Float = fmax(r, g, b) - fmin(r, g, b)
    private fun lum(r: Float, g: Float, b: Float): Float = r * 0.30f + g * 0.59f + b * 0.11f

    private fun setSat(r: FloatArray, g: FloatArray, b: FloatArray, s: Float) {
        val mn = fmin(r[0], g[0], b[0])
        val mx = fmax(r[0], g[0], b[0])
        fun channel(c: Float): Float =
            if (mx == mn) 0f else (c - mn) * s / (mx - mn)
        r[0] = channel(r[0])
        g[0] = channel(g[0])
        b[0] = channel(b[0])
    }

    private fun clipColor(r: FloatArray, g: FloatArray, b: FloatArray) {
        val l = lum(r[0], g[0], b[0])
        val mn = fmin(r[0], g[0], b[0])
        val mx = fmax(r[0], g[0], b[0])
        fun clip(c: Float): Float {
            var cc = c
            if (mn < 0) { cc = l + (cc - l) * l / (l - mn) }
            if (mx > 1) { cc = l + (cc - l) * (1 - l) / (mx - l) }
            return cc.coerceIn(-0.0001f, 1f)
        }
        r[0] = clip(r[0])
        g[0] = clip(g[0])
        b[0] = clip(b[0])
    }

    private fun setLum(r: FloatArray, g: FloatArray, b: FloatArray, l: Float) {
        val diff = l - lum(r[0], g[0], b[0])
        r[0] += diff; g[0] += diff; b[0] += diff
        clipColor(r, g, b)
    }

    private fun blendHue(dr: Float, dg: Float, db: Float,
                          sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        setSat(sr, sg, sb, sat(dr, dg, db))
        setLum(sr, sg, sb, lum(dr, dg, db))
    }

    private fun blendSaturation(dr: Float, dg: Float, db: Float,
                                sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        val R = floatArrayOf(dr)
        val G = floatArrayOf(dg)
        val B = floatArrayOf(db)
        setSat(R, G, B, sat(sr[0], sg[0], sb[0]))
        setLum(R, G, B, lum(dr, dg, db))
        sr[0] = R[0]; sg[0] = G[0]; sb[0] = B[0]
    }

    private fun blendColor(dr: Float, dg: Float, db: Float,
                            sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        setLum(sr, sg, sb, lum(dr, dg, db))
    }

    private fun blendLuminosity(dr: Float, dg: Float, db: Float,
                                sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        val R = floatArrayOf(dr)
        val G = floatArrayOf(dg)
        val B = floatArrayOf(db)
        setLum(R, G, B, lum(sr[0], sg[0], sb[0]))
        sr[0] = R[0]; sg[0] = G[0]; sb[0] = B[0]
    }

    private fun toFloats(c: Color): FloatArray {
        val packed = c.packed.toInt()
        val r = (packed shr 16) and 0xFF
        val g = (packed shr 8) and 0xFF
        val b = packed and 0xFF
        val a = (packed shr 24) and 0xFF
        return floatArrayOf(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    private fun fromFloats(r: Float, g: Float, b: Float, a: Float): Color {
        val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
        val ai = (a.coerceIn(0f, 1f) * 255f).toInt()
        return Color.fromRGBA(ri / 255f, gi / 255f, bi / 255f, ai / 255f)
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Color =
        Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
}
