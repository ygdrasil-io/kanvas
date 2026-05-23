package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.skia.foundation.SkPaint
import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.max
import kotlin.math.min

/**
 * Port of Skia's `gm/hsl.cpp::DEF_SIMPLE_GM(hsl, canvas, 600, 100)`.
 *
 * Demonstrates the four non-separable HSL blend modes — Hue, Saturation,
 * Color, Luminosity — plus Src and Dst, by drawing a blended foreground
 * over a background rectangle and overlaying a reference circle computed
 * via a local CPU-side blend implementation. Correct rendering shows **no
 * visible circle** within the squares; if the blend modes are wrong the
 * reference circle stands out.
 *
 * All blend math is ported verbatim from the static helper functions in
 * `gm/hsl.cpp` (lines 48–155): `sat`, `lum`, `set_sat`, `clip_color`,
 * `set_lum`, and the four mode functions `hue`, `saturation`, `color`,
 * `luminosity`.
 *
 * C++ original (lines 157–196):
 * ```cpp
 * DEF_SIMPLE_GM(hsl, canvas, 600, 100) {
 *   SkPaint paint;
 *   SkFont  font = ToolUtils::DefaultPortableFont();
 *   const char* comment = "HSL blend modes are correct when you see no circles in the squares.";
 *   canvas->drawString(comment, 10,10, font, paint);
 *
 *   SkPaint bg, fg;
 *   bg.setColor(0xff00ff00);  // Fully-saturated bright green
 *   fg.setColor(0xff7f3f7f);  // Partly-saturated dim magenta
 *
 *   struct { SkBlendMode mode; void (*reference)(...); } tests[] = {
 *     { SkBlendMode::kSrc,        nullptr    },
 *     { SkBlendMode::kDst,        nullptr    },
 *     { SkBlendMode::kHue,        hue        },
 *     { SkBlendMode::kSaturation, saturation },
 *     { SkBlendMode::kColor,      color      },
 *     { SkBlendMode::kLuminosity, luminosity },
 *   };
 *   for (auto test : tests) {
 *     canvas->drawRect({20,20,80,80}, bg);
 *     fg.setBlendMode(test.mode);
 *     canvas->drawRect({20,20,80,80}, fg);
 *     if (test.reference) {
 *       SkPaint ref;
 *       ref.setColor(blend(bg.getColor(), fg.getColor(), test.reference));
 *       canvas->drawCircle(50,50, 20, ref);
 *     }
 *     canvas->drawString(SkBlendMode_Name(test.mode), 20, 90, font, paint);
 *     canvas->translate(100,0);
 *   }
 * }
 * ```
 */
public class HslBlendGM : GM() {

    override fun getName(): String = "hsl"

    override fun getISize(): SkISize = SkISize.Make(600, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint()
        val font = ToolUtils.DefaultPortableFont()

        val comment = "HSL blend modes are correct when you see no circles in the squares."
        c.drawString(comment, 10f, 10f, font, paint)

        val bgColor = 0xff00ff00.toInt() // fully-saturated bright green, H=120°, S=100%, L=50%
        val fgColor = 0xff7f3f7f.toInt() // partly-saturated dim magenta, H=300°, S≈33%, L≈37%

        val bg = SkPaint().apply { color = bgColor }
        val fg = SkPaint().apply { color = fgColor }

        data class TestCase(val mode: SkBlendMode, val reference: BlendFn?)

        val tests = listOf(
            TestCase(SkBlendMode.kSrc, null),
            TestCase(SkBlendMode.kDst, null),
            TestCase(SkBlendMode.kHue, ::blendHue),
            TestCase(SkBlendMode.kSaturation, ::blendSaturation),
            TestCase(SkBlendMode.kColor, ::blendColor),
            TestCase(SkBlendMode.kLuminosity, ::blendLuminosity),
        )

        for (test in tests) {
            c.drawRect(SkRect.MakeLTRB(20f, 20f, 80f, 80f), bg)

            fg.blendMode = test.mode
            c.drawRect(SkRect.MakeLTRB(20f, 20f, 80f, 80f), fg)

            val ref = test.reference
            if (ref != null) {
                val blended = blend(bgColor, fgColor, ref)
                val refPaint = SkPaint().apply { color = blended }
                c.drawCircle(50f, 50f, 20f, refPaint)
            }

            c.drawString(SkBlendMode_Name(test.mode), 20f, 90f, font, paint)
            c.translate(100f, 0f)
        }
    }

    // ── CPU-side blend helpers ────────────────────────────────────────────

    /** Functional type alias for the four non-separable blend mode functions. */
    private typealias BlendFn = (dr: Float, dg: Float, db: Float,
                                  sr: FloatArray, sg: FloatArray, sb: FloatArray) -> Unit

    /** Applies [mode] to [dst] and [src] (both fully opaque) and returns blended [SkColor]. */
    private fun blend(dst: SkColor, src: SkColor, mode: BlendFn): SkColor {
        // Both dst and src must be fully opaque (matches upstream's SkASSERT).
        val d = SkColor4f.FromColor(dst)
        val s = SkColor4f.FromColor(src)

        // Use single-element arrays to emulate C++ pointer output parameters.
        val sr = floatArrayOf(s.fR)
        val sg = floatArrayOf(s.fG)
        val sb = floatArrayOf(s.fB)

        mode(d.fR, d.fG, d.fB, sr, sg, sb)

        return SkColor4f(sr[0], sg[0], sb[0], s.fA).toSkColor()
    }

    // ── Static helpers (verbatim from gm/hsl.cpp lines 48-89) ────────────

    private fun fmin(r: Float, g: Float, b: Float): Float = min(r, min(g, b))
    private fun fmax(r: Float, g: Float, b: Float): Float = max(r, max(g, b))

    private fun sat(r: Float, g: Float, b: Float): Float = fmax(r, g, b) - fmin(r, g, b)
    private fun lum(r: Float, g: Float, b: Float): Float = r * 0.30f + g * 0.59f + b * 0.11f

    /**
     * Mirrors `set_sat()` (gm/hsl.cpp:57). Maps minimum channel to 0,
     * maximum to [s], middle proportionately.
     */
    private fun setSat(r: FloatArray, g: FloatArray, b: FloatArray, s: Float) {
        val mn = fmin(r[0], g[0], b[0])
        val mx = fmax(r[0], g[0], b[0])
        fun channel(c: Float): Float =
            if (mx == mn) 0f else (c - mn) * s / (mx - mn)
        r[0] = channel(r[0])
        g[0] = channel(g[0])
        b[0] = channel(b[0])
    }

    /** Mirrors `clip_color()` (gm/hsl.cpp:68). Web-spec version (not KHR). */
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

    /** Mirrors `set_lum()` (gm/hsl.cpp:83). */
    private fun setLum(r: FloatArray, g: FloatArray, b: FloatArray, l: Float) {
        val diff = l - lum(r[0], g[0], b[0])
        r[0] += diff; g[0] += diff; b[0] += diff
        clipColor(r, g, b)
    }

    // ── Four non-separable blend mode functions ───────────────────────────

    /**
     * Hue of Src, Saturation and Luminosity of Dst.
     * Mirrors `hue()` (gm/hsl.cpp:92).
     */
    private fun blendHue(dr: Float, dg: Float, db: Float,
                         sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        setSat(sr, sg, sb, sat(dr, dg, db))
        setLum(sr, sg, sb, lum(dr, dg, db))
    }

    /**
     * Saturation of Src, Hue and Luminosity of Dst.
     * Mirrors `saturation()` (gm/hsl.cpp:105).
     */
    private fun blendSaturation(dr: Float, dg: Float, db: Float,
                                sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        val R = floatArrayOf(dr)
        val G = floatArrayOf(dg)
        val B = floatArrayOf(db)
        setSat(R, G, B, sat(sr[0], sg[0], sb[0]))
        setLum(R, G, B, lum(dr, dg, db))
        sr[0] = R[0]; sg[0] = G[0]; sb[0] = B[0]
    }

    /**
     * Hue and Saturation of Src, Luminosity of Dst.
     * Mirrors `color()` (gm/hsl.cpp:118).
     */
    private fun blendColor(dr: Float, dg: Float, db: Float,
                           sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        setLum(sr, sg, sb, lum(dr, dg, db))
    }

    /**
     * Luminosity of Src, Hue and Saturation of Dst.
     * Mirrors `luminosity()` (gm/hsl.cpp:130).
     */
    private fun blendLuminosity(dr: Float, dg: Float, db: Float,
                                sr: FloatArray, sg: FloatArray, sb: FloatArray) {
        val R = floatArrayOf(dr)
        val G = floatArrayOf(dg)
        val B = floatArrayOf(db)
        setLum(R, G, B, lum(sr[0], sg[0], sb[0]))
        sr[0] = R[0]; sg[0] = G[0]; sb[0] = B[0]
    }
}
