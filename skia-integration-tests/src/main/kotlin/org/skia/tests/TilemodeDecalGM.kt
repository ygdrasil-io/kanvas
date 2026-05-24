package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/tilemodes.cpp::DEF_SIMPLE_GM(tilemode_decal, …)`
 * (720 × 1100).
 *
 * Demonstrates `SkTileMode::kDecal` combined with every other tile mode
 * on two axes, across five shader recipes:
 *
 *  1. Image, nearest-filter (`kNearest`)
 *  2. Image, linear-filter (`kLinear`)
 *  3. Image, bicubic Mitchell filter
 *  4. Linear gradient along the image diagonal
 *  5. Radial gradient centred at the image mid-point
 *
 * For each of the 4 tile-mode pairs `(Clamp/Clamp, Clamp/Decal,
 * Decal/Clamp, Decal/Decal)` a column of 5 draws is produced.  Every
 * draw starts with a yellow background, then a 4° rotated rect slightly
 * larger than the image so the tiling behaviour is visible on every
 * edge.
 *
 * Canvas layout: `translate(45, 45)`, then per pair `translate(rWidth +
 * 10, 0)`; per shader proc `translate(0, rHeight + 20)`.
 *
 * Upstream C++ (verbatim):
 * ```cpp
 * DEF_SIMPLE_GM(tilemode_decal, canvas, 720, 1100) {
 *     auto img = ToolUtils::GetResourceAsImage("images/mandrill_128.png");
 *     SkPaint bgpaint;
 *     bgpaint.setColor(SK_ColorYELLOW);
 *     SkRect r = { -20, -20, img->width() + 20.0f, img->height() + 20.0f };
 *     canvas->translate(45, 45);
 *     // … 5 shader procs × 4 tile-mode pairs …
 * }
 * ```
 */
public class TilemodeDecalGM : GM() {

    override fun getName(): String = "tilemode_decal"
    override fun getISize(): SkISize = SkISize.Make(720, 1100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return

        val bgPaint = SkPaint(SK_ColorYELLOW)

        // r is slightly bigger than the image so decal zeros are visible
        val r = SkRect.MakeLTRB(-20f, -20f, img.width + 20f, img.height + 20f)

        c.translate(45f, 45f)

        // The 5 shader recipes — parallel to upstream's `shader_procs[]`
        val shaderProcs: List<(SkPaint, SkTileMode, SkTileMode) -> Unit> = listOf(
            // 1. No filtering with decal mode (kNearest)
            { paint, tx, ty ->
                paint.shader = img.makeShader(tx, ty, SkSamplingOptions(SkFilterMode.kNearest))
            },
            // 2. Bilerp approximation (kLinear)
            { paint, tx, ty ->
                paint.shader = img.makeShader(tx, ty, SkSamplingOptions(SkFilterMode.kLinear))
            },
            // 3. Bicubic Mitchell filter
            { paint, tx, ty ->
                paint.shader = img.makeShader(tx, ty, SkSamplingOptions(SkCubicResampler.Mitchell))
            },
            // 4. Linear gradient along the image diagonal — tile mode on x only
            //    (matches upstream: `SkGradientShader::MakeLinear(pts, {colors, {}, tx}, {})`)
            { paint, tx, _ ->
                val pts = arrayOf(SkPoint(0f, 0f), SkPoint(img.width.toFloat(), img.height.toFloat()))
                val colors = intArrayOf(SK_ColorRED, SK_ColorBLUE)
                paint.shader = SkLinearGradient.Make(pts[0], pts[1], colors, null, tx)
            },
            // 5. Radial gradient centred at image mid-point — tile mode on x only
            //    (matches upstream: `SkGradientShader::MakeRadial(center, r, {colors, {}, tx}, {})`)
            { paint, tx, _ ->
                val center = SkPoint(img.width * 0.5f, img.width * 0.5f)
                val rad = img.width * 0.5f
                val colors = intArrayOf(SK_ColorRED, SK_ColorBLUE)
                paint.shader = SkRadialGradient.Make(center, rad, colors, null, tx)
            },
        )

        // 4 tile-mode pairs — parallel to upstream's `pairs[]`
        data class XY(val tx: SkTileMode, val ty: SkTileMode)
        val pairs = listOf(
            XY(SkTileMode.kClamp, SkTileMode.kClamp),
            XY(SkTileMode.kClamp, SkTileMode.kDecal),
            XY(SkTileMode.kDecal, SkTileMode.kClamp),
            XY(SkTileMode.kDecal, SkTileMode.kDecal),
        )

        for (pair in pairs) {
            c.save()
            for (proc in shaderProcs) {
                val paint = SkPaint()
                c.save()
                // Slight rotation to highlight filtered vs unfiltered decal edges
                c.rotate(4f)
                c.drawRect(r, bgPaint)
                proc(paint, pair.tx, pair.ty)
                c.drawRect(r, paint)
                c.restore()
                c.translate(0f, r.height() + 20f)
            }
            c.restore()
            c.translate(r.width() + 10f, 0f)
        }
    }
}
