package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withLayer
import org.skia.core.withSave
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * Port of Skia's `gm/composeshader.cpp::composeshader_grid` (`DEF_SIMPLE_GM`).
 *
 * A 4×4 grid (16 cells) of blend-mode pairs. Each cell shows two views
 * of the same blend between a vertical blue-fade `src` shader and a
 * horizontal red-fade `dst` shader :
 *
 *  - **Left column** : `draw_cell` — drawn via two explicit draw calls
 *    (dst with `kSrc`, then src with [mode]) inside a `saveLayer` that
 *    carries the outer paint alpha.
 *  - **Right column** : `draw_composed` — drawn as a single
 *    `SkShaders::Blend(mode, dst, src)` shader call at the same alpha.
 *
 * The two renderings must be visually identical, proving that the composed
 * shader is equivalent to the layered approach. The 16 modes iterated are
 * `SkBlendMode` values 0–15 (`kClear` … `kOverlay`).
 *
 * Each mode is drawn twice (top row alpha=0xFF, bottom row alpha=0x80) to
 * verify that the outer paint alpha is correctly forwarded through both
 * paths.
 *
 * C++ original :
 * ```cpp
 * static const SkScalar gCellSize = 100;
 *
 * static void draw_cell(SkCanvas* canvas, sk_sp<SkShader> src, sk_sp<SkShader> dst,
 *                        SkBlendMode mode, SkAlpha alpha) {
 *     const SkRect r = SkRect::MakeWH(gCellSize, gCellSize);
 *     SkPaint p;
 *     p.setAlpha(alpha);
 *     SkAutoCanvasRestore acr(canvas, false);
 *     canvas->saveLayer(&r, &p);
 *     p.setAlpha(0xFF);
 *     p.setShader(dst); p.setBlendMode(SkBlendMode::kSrc);
 *     canvas->drawRect(r, p);
 *     p.setShader(src); p.setBlendMode(mode);
 *     canvas->drawRect(r, p);
 * }
 *
 * static void draw_composed(SkCanvas* canvas, sk_sp<SkShader> src, sk_sp<SkShader> dst,
 *                            SkBlendMode mode, SkAlpha alpha) {
 *     SkPaint p;
 *     p.setAlpha(alpha);
 *     p.setShader(SkShaders::Blend(mode, dst, src));
 *     canvas->drawRect(SkRect::MakeWH(gCellSize, gCellSize), p);
 * }
 *
 * DEF_SIMPLE_GM(composeshader_grid, canvas, 882, 882) {
 *     auto src = make_src_shader(gCellSize);
 *     auto dst = make_dst_shader(gCellSize);
 *     const SkScalar margin = 15, dx = 2*gCellSize + margin, dy = 2*gCellSize + margin;
 *     canvas->translate(margin, margin);
 *     canvas->save();
 *     for (int m = 0; m < 16; ++m) {
 *         SkBlendMode mode = static_cast<SkBlendMode>(m);
 *         draw_pair(canvas, src, dst, mode);
 *         if ((m % 4) == 3) { canvas->restore(); canvas->translate(0, dy); canvas->save(); }
 *         else               { canvas->translate(dx, 0); }
 *     }
 *     canvas->restore();
 * }
 * ```
 */
public class ComposeShaderGridGM : GM() {

    override fun getName(): String = "composeshader_grid"
    override fun getISize(): SkISize = SkISize.Make(882, 882)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val src = makeSrcShader(CELL_SIZE)
        val dst = makeDstShader(CELL_SIZE)

        val margin: SkScalar = 15f
        val dx: SkScalar = 2f * CELL_SIZE + margin
        val dy: SkScalar = 2f * CELL_SIZE + margin

        c.translate(margin, margin)
        c.save()
        for (m in 0 until 16) {
            val mode = SkBlendMode.entries[m]
            drawPair(c, src, dst, mode)
            if ((m % 4) == 3) {
                c.restore()
                c.translate(0f, dy)
                c.save()
            } else {
                c.translate(dx, 0f)
            }
        }
        c.restore()
    }

    /**
     * Mirrors upstream `make_src_shader` — vertical blue gradient (opaque → transparent).
     * Blue `{0,0,1,1}` at y=0, transparent blue `{0,0,1,0}` at y=[size].
     */
    private fun makeSrcShader(size: SkScalar): SkShader =
        SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(0f, size),
            colors = intArrayOf(SK_ColorBLUE, SkColorSetARGB(0, 0, 0, 0xFF)),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )

    /**
     * Mirrors upstream `make_dst_shader` — horizontal red gradient (opaque → transparent).
     * Red `{1,0,0,1}` at x=0, transparent red `{1,0,0,0}` at x=[size].
     */
    private fun makeDstShader(size: SkScalar): SkShader =
        SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(size, 0f),
            colors = intArrayOf(SK_ColorRED, SkColorSetARGB(0, 0xFF, 0, 0)),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )

    /**
     * Mirrors upstream `draw_cell` — draws dst (kSrc) then src ([mode]) inside
     * a saveLayer that carries [alpha] so the whole cell is composited at that
     * opacity against whatever is below.
     */
    private fun drawCell(
        canvas: SkCanvas,
        src: SkShader,
        dst: SkShader,
        mode: SkBlendMode,
        alpha: Int,
    ) {
        val r = SkRect.MakeWH(CELL_SIZE, CELL_SIZE)
        val p = SkPaint().apply { this.alpha = alpha }
        // SkAutoCanvasRestore(canvas, false) + saveLayer == withLayer
        canvas.withLayer(bounds = r, paint = p) {
            p.alpha = 0xFF
            p.shader = dst
            p.blendMode = SkBlendMode.kSrc
            drawRect(r, p)
            p.shader = src
            p.blendMode = mode
            drawRect(r, p)
        }
    }

    /**
     * Mirrors upstream `draw_composed` — draws the composed `Blend(mode, dst, src)` shader
     * at [alpha] directly.
     */
    private fun drawComposed(
        canvas: SkCanvas,
        src: SkShader,
        dst: SkShader,
        mode: SkBlendMode,
        alpha: Int,
    ) {
        val p = SkPaint().apply {
            this.alpha = alpha
            shader = SkShaders.Blend(mode, dst, src)
        }
        canvas.drawRect(SkRect.MakeWH(CELL_SIZE, CELL_SIZE), p)
    }

    /**
     * Mirrors upstream `draw_pair` — draws the border and both alpha rows
     * (0xFF and 0x80) for a given [mode], with cell and composed side by side.
     */
    private fun drawPair(canvas: SkCanvas, src: SkShader, dst: SkShader, mode: SkBlendMode) {
        canvas.withSave {
            val gap = 4f
            // r = MakeWH(2*CELL_SIZE + gap, 2*CELL_SIZE + gap), then outset(gap + 1.5, gap + 1.5)
            val outset = gap + 1.5f
            val borderR = SkRect.MakeLTRB(
                -outset,
                -outset,
                2f * CELL_SIZE + gap + outset,
                2f * CELL_SIZE + gap + outset,
            )
            val borderPaint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
            drawRect(borderR, borderPaint)

            var alpha = 0xFF
            for (y in 0 until 2) {
                drawCell(this, src, dst, mode, alpha)
                withSave {
                    translate(CELL_SIZE + gap, 0f)
                    drawComposed(this, src, dst, mode, alpha)
                }
                translate(0f, CELL_SIZE + gap)
                alpha = 0x80
            }
        }
    }

    private companion object {
        const val CELL_SIZE: SkScalar = 100f
    }
}
