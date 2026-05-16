package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.math.SK_ColorYELLOW
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/drawlines_with_local_matrix.cpp`
 * ([`DEF_SIMPLE_GM(drawlines_with_local_matrix, canvas, 500, 500)`](https://github.com/google/skia/blob/main/gm/drawlines_with_local_matrix.cpp)).
 *
 * Despite the name (which refers to upstream's GPU
 * `GrAtlasTextOp::SetLocalMatrix` plumbing being exercised by the
 * `drawPoints(kLines, …)` path), this GM is a straightforward visual
 * test: a 500 × 500 canvas filled with a rainbow [SkRadialGradient]
 * centred at `(250, 250)`, then nine "double" lines — a thick white
 * stroke underneath a slightly thinner stroke that re-samples the
 * gradient (so the inner stroke takes whatever rainbow colour
 * happens to sit under it).
 *
 * Each line is drawn via [SkCanvas.drawPoints] with
 * [SkCanvas.PointMode.kLines] and [SkPaint.Cap.kSquare_Cap] caps,
 * yielding the characteristic square-tipped ends.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(drawlines_with_local_matrix, canvas, 500, 500) {
 *     canvas->clipRect({0,0,500,500});
 *     SkPaint grad;
 *     grad.setAntiAlias(true);
 *     grad.setStrokeCap(SkPaint::kSquare_Cap);
 *     float pos[6] = {0, 2/6.f, 3/6.f, 4/6.f, 5/6.f, 1};
 *     const SkColor4f indigo = SkColor4f::FromColor(SkColorSetARGB(0xFF, 0x4b, 0x00, 0x82));
 *     const SkColor4f violet = SkColor4f::FromColor(SkColorSetARGB(0xFF, 0xee, 0x82, 0xee));
 *     const SkColor4f colors[6] = {
 *         SkColors::kRed, SkColors::kYellow, SkColors::kGreen, SkColors::kBlue, indigo, violet};
 *     grad.setShader(SkShaders::RadialGradient({250,250}, 280,
 *                                              {{colors, pos, SkTileMode::kClamp}, {}}));
 *     canvas->drawPaint(grad);
 *
 *     SkPaint white;
 *     white.setAntiAlias(true);
 *     white.setStrokeCap(SkPaint::kSquare_Cap);
 *     white.setColor(SK_ColorWHITE);
 *
 *     auto drawLine = [&](float x0, float y0, float x1, float y1, float w) {
 *         SkPoint p[2] = {{x0, y0}, {x1, y1}};
 *         white.setStrokeWidth(w);
 *         canvas->drawPoints(SkCanvas::kLines_PointMode, p, white);
 *         grad.setStrokeWidth(w - 4);
 *         canvas->drawPoints(SkCanvas::kLines_PointMode, p, grad);
 *     };
 *
 *     drawLine(20, 20, 200, 120, 20);
 *     drawLine(20, 200, 20, 100, 20);
 *     drawLine(480, 20, 400, 400, 20);
 *     drawLine(50, 480, 260, 100, 20);
 *     drawLine(270, 20, 380, 210, 20);
 *     drawLine(280, 280, 400, 480, 20);
 *     drawLine(160, 375, 280, 375, 20);
 *     drawLine(220, 410, 220, 470, 20);
 *     drawLine(250, 250, 250, 250, 20);
 * }
 * ```
 */
public class DrawlinesWithLocalMatrixGM : GM() {
    override fun getName(): String = "drawlines_with_local_matrix"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.clipRect(SkRect.MakeLTRB(0f, 0f, 500f, 500f))

        // Rainbow stops — red, yellow, green, blue, indigo, violet.
        val indigo = SkColorSetARGB(0xFF, 0x4b, 0x00, 0x82)
        val violet = SkColorSetARGB(0xFF, 0xee, 0x82, 0xee)
        val colors = intArrayOf(
            SK_ColorRED,
            SK_ColorYELLOW,
            SK_ColorGREEN,
            SK_ColorBLUE,
            indigo,
            violet,
        )
        val positions = floatArrayOf(0f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f)
        val radial = SkRadialGradient.Make(
            center = SkPoint.Make(250f, 250f),
            radius = 280f,
            colors = colors,
            positions = positions,
            tileMode = SkTileMode.kClamp,
        )

        val grad = SkPaint().apply {
            isAntiAlias = true
            strokeCap = SkPaint.Cap.kSquare_Cap
            shader = radial
        }
        c.drawPaint(grad)

        val white = SkPaint().apply {
            isAntiAlias = true
            strokeCap = SkPaint.Cap.kSquare_Cap
            color = SK_ColorWHITE
        }

        // Upstream Skia's `drawPoints(kLines, …)` always strokes
        // regardless of paint.style ; `:kanvas-skia`'s drawPoints lowers
        // to drawLine → drawPath which honours paint.style. To keep
        // `grad` usable both for `drawPaint` (fill semantics) and for
        // the line strokes below we materialise a fresh stroke-styled
        // copy at each `drawLine` call. The C++ doesn't need this
        // because Skia auto-strokes the primitives.
        fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
            val pts = arrayOf(SkPoint.Make(x0, y0), SkPoint.Make(x1, y1))
            val whiteStroke = white.copy().apply {
                style = SkPaint.Style.kStroke_Style
                strokeWidth = w
            }
            c.drawPoints(SkCanvas.PointMode.kLines, pts, whiteStroke)
            val gradStroke = grad.copy().apply {
                style = SkPaint.Style.kStroke_Style
                strokeWidth = w - 4f
            }
            c.drawPoints(SkCanvas.PointMode.kLines, pts, gradStroke)
        }

        drawLine(20f, 20f, 200f, 120f, 20f)
        drawLine(20f, 200f, 20f, 100f, 20f)
        drawLine(480f, 20f, 400f, 400f, 20f)
        drawLine(50f, 480f, 260f, 100f, 20f)
        drawLine(270f, 20f, 380f, 210f, 20f)
        drawLine(280f, 280f, 400f, 480f, 20f)
        drawLine(160f, 375f, 280f, 375f, 20f)
        drawLine(220f, 410f, 220f, 470f, 20f)
        drawLine(250f, 250f, 250f, 250f, 20f)
    }
}
