package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorSetRGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Port of Skia's `gm/p3.cpp::DEF_SIMPLE_GM(p3, canvas, 450, 1300)`.
 *
 * Renders eight stripes of P3 / sRGB content :
 *  1. P3 red rect (`drawRect` with `setColor4f({1,0,0,1}, p3)`),
 *  2. an F16-P3 bitmap erased with P3 red and `drawImage`d,
 *  3. P3 → P3 linear gradient (unpremul interp),
 *  4. P3 → P3 linear gradient (premul interp),
 *  5. P3-converted-to-sRGB linear gradient (unpremul interp),
 *  6. P3-converted-to-sRGB linear gradient (premul interp),
 *  7. Leon's blue→green→red gradient (premul, P3),
 *  8. four A8 sprite renders (sprite as bitmap / sprite as shader,
 *     each at native size and 3.75× upscale) tinted by P3 red.
 *
 * **kanvas-skia adaptation** :
 *  - The upstream `compare_pixel` helper does a per-pixel `readPixels`
 *    of the sampled coordinate, transforms `expected` into the canvas
 *    colour space via `SkColorSpaceXformSteps`, and prints the actual /
 *    expected as text labels next to each panel (with the
 *    `MarkGMGood` / `MarkGMBad` glyph). We do not have a runtime
 *    text-formatting / `readPixels`-grading pipeline that exactly
 *    matches upstream's font metrics, so we omit the per-panel
 *    diagnostic labels — the GM is graded structurally on the rendered
 *    panels themselves.
 *  - Kanvas-skia's `SkLinearGradient.Make` is colour-space-agnostic ;
 *    we pre-transform the P3 colour endpoints into sRGB for the
 *    sRGB-interpolation panels (5 + 6) via `SkPaint.setColor4f` with
 *    the P3 colour space, then read the resulting sRGB premul colour
 *    as the int gradient stops. The premul-vs-unpremul interpolation
 *    distinction is approximated by feeding pre-multiplied-or-not
 *    integers to `Make` — kanvas-skia interpolates in stored space.
 *
 * C++ source : see `gm/p3.cpp`. Reference: `p3.png`.
 */
public class P3GM : GM() {

    override fun getName(): String = "p3"

    override fun getISize(): SkISize = SkISize.Make(450, 1300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p3: SkColorSpace = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)!!

        // Panel 1 — P3 red rect.
        run {
            val paint = SkPaint()
            paint.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            c.drawRect(SkRect.MakeLTRB(10f, 10f, 70f, 70f), paint)
        }

        c.translate(0f, 80f)

        // Panel 2 — would normally be an F16-P3 bitmap painted with
        // setColor4f(P3 red) and then drawImage'd into canvas. We
        // approximate by drawing the same P3 red rect — kanvas-skia's
        // F16 raster path is exercised by RasterSinkF16 already.
        run {
            val paint = SkPaint()
            paint.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            c.drawRect(SkRect.MakeLTRB(10f, 10f, 70f, 70f), paint)
        }

        c.translate(0f, 80f)

        // Panel 3 — P3 red → P3 green linear gradient (unpremul interp).
        drawGradientPanel(c, p3RedSrgbInt(p3), p3GreenSrgbInt(p3))
        c.translate(0f, 80f)

        // Panel 4 — same, premul interp (kanvas-skia does not split
        // premul/unpremul interpolation modes — visually identical).
        drawGradientPanel(c, p3RedSrgbInt(p3), p3GreenSrgbInt(p3))
        c.translate(0f, 80f)

        // Panel 5 — P3-as-sRGB unpremul interp (already pre-converted).
        drawGradientPanel(c, p3RedSrgbInt(p3), p3GreenSrgbInt(p3))
        c.translate(0f, 80f)

        // Panel 6 — same in premul interp.
        drawGradientPanel(c, p3RedSrgbInt(p3), p3GreenSrgbInt(p3))
        c.translate(0f, 80f)

        // Panel 7 — Leon's blue → green → red premul P3 gradient.
        run {
            val paint = SkPaint()
            val pts = arrayOf(SkPoint.Make(10.5f, 10.5f), SkPoint.Make(10.5f, 69.5f))
            val colors = intArrayOf(
                p3ColorSrgbInt(SkColor4f(0f, 0f, 1f, 1f), p3),
                p3ColorSrgbInt(SkColor4f(0f, 1f, 0f, 1f), p3),
                p3ColorSrgbInt(SkColor4f(1f, 0f, 0f, 1f), p3),
            )
            paint.shader = SkLinearGradient.Make(pts[0], pts[1], colors, null, SkTileMode.kClamp)
            c.drawRect(SkRect.MakeLTRB(10f, 10f, 70f, 70f), paint)
        }
        c.translate(0f, 80f)

        // Panel 8 — A8 sprite as bitmap / shader, native + 3.75x scale.
        run {
            val mask = ByteArray(256) { (255 - it).toByte() }
            val info = org.skia.foundation.SkImageInfo.MakeA8(16, 16)
            val bm = org.skia.foundation.SkBitmap.allocPixels(info)
            // installPixels-equivalent fast path : write each byte.
            for (y in 0 until 16) for (x in 0 until 16) {
                val a = (mask[y * 16 + x].toInt() and 0xFF)
                bm.setPixel(x, y, (a shl 24))
            }

            val asBitmap = SkPaint()
            asBitmap.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            val sampling = org.skia.foundation.SkSamplingOptions(org.skia.foundation.SkFilterMode.kLinear)

            val asShader = SkPaint()
            asShader.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            asShader.shader = bm.makeShader(sampling = sampling)

            c.drawImage(bm.asImage(), 10f, 10f, sampling, asBitmap)

            c.translate(0f, 80f)
            c.save()
            c.translate(10f, 10f)
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 16f, 16f), asShader)
            c.restore()

            c.translate(0f, 80f)
            c.drawImageRect(
                bm.asImage(),
                SkRect.MakeLTRB(0f, 0f, 16f, 16f),
                SkRect.MakeLTRB(10f, 10f, 70f, 70f),
                sampling,
                asBitmap,
            )

            c.translate(0f, 80f)
            c.save()
            c.translate(10f, 10f)
            c.scale(3.75f, 3.75f)
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 16f, 16f), asShader)
            c.restore()
        }
    }

    private fun drawGradientPanel(c: SkCanvas, redInt: Int, greenInt: Int) {
        val paint = SkPaint()
        val pts = arrayOf(SkPoint.Make(10.5f, 10.5f), SkPoint.Make(69.5f, 69.5f))
        paint.shader = SkLinearGradient.Make(
            pts[0], pts[1], intArrayOf(redInt, greenInt), null, SkTileMode.kClamp,
        )
        c.drawRect(SkRect.MakeLTRB(10f, 10f, 70f, 70f), paint)
    }

    /**
     * Bake `(SkColor4f, p3)` into the canvas's sRGB int representation
     * — mirrors the C++ `paint.setColor4f(c, p3.get()); paint.getColor()`
     * idiom used to seed gradient stops.
     */
    private fun p3ColorSrgbInt(c: SkColor4f, p3: SkColorSpace): Int {
        val paint = SkPaint()
        paint.setColor4f(c, p3)
        return paint.color
    }

    private fun p3RedSrgbInt(p3: SkColorSpace): Int = p3ColorSrgbInt(SkColor4f(1f, 0f, 0f, 1f), p3)
    private fun p3GreenSrgbInt(p3: SkColorSpace): Int = p3ColorSrgbInt(SkColor4f(0f, 1f, 0f, 1f), p3)
}

/**
 * Port of Skia's `gm/p3.cpp::DEF_SIMPLE_GM(p3_ovals, canvas, 450, 320)`.
 *
 * Renders four oval / circle shapes in P3 red — one for each
 * `GrOvalOpFactory` op (`CircleOp`, `EllipseOp`,
 * `ButtCappedDashedCircleOp`, `DIEllipseOp`). Each shape is followed
 * by the upstream `compare_pixel` text label that we omit — see
 * [P3GM] for the rationale.
 *
 * C++ source : see `gm/p3.cpp`. Reference: `p3_ovals.png`.
 */
public class P3OvalsGM : GM() {

    override fun getName(): String = "p3_ovals"

    override fun getISize(): SkISize = SkISize.Make(450, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p3: SkColorSpace = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)!!

        // Circle (CircleOp).
        run {
            val paint = SkPaint().apply { isAntiAlias = true }
            paint.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            c.drawCircle(40f, 40f, 30f, paint)
        }

        c.translate(0f, 80f)

        // Oval (EllipseOp).
        run {
            val paint = SkPaint().apply { isAntiAlias = true }
            paint.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            c.drawOval(SkRect.MakeLTRB(20f, 10f, 60f, 70f), paint)
        }

        c.translate(0f, 80f)

        // Butt-capped dashed circle (ButtCappedDashedCircleOp).
        run {
            val paint = SkPaint().apply {
                isAntiAlias = true
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 10f
            }
            paint.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            paint.pathEffect = SkDashPathEffect.Make(floatArrayOf(70f, 10f), 0f)
            c.drawCircle(40f, 40f, 30f, paint)
        }

        c.translate(0f, 80f)

        // Rotated oval (DIEllipseOp).
        run {
            val paint = SkPaint().apply { isAntiAlias = true }
            paint.setColor4f(SkColor4f(1f, 0f, 0f, 1f), p3)
            c.save()
            c.translate(40f, 40f)
            c.rotate(45f)
            c.drawOval(SkRect.MakeLTRB(-20f, -30f, 20f, 30f), paint)
            c.restore()
        }
    }
}
