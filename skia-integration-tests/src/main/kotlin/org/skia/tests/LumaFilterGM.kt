package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkLumaColorFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/lumafilter.cpp::LumaFilterGM`
 * (`DEF_GM(return new LumaFilterGM;)`).
 *
 * **Note** : the upstream `lumafilter.cpp` ships a second
 * `DEF_SIMPLE_GM(AlternateLuma)` that wraps `SkLumaColorFilter`
 * inside an `SkColorFilterPriv::WithWorkingFormat` runtime-effect
 * shim — that GM depends on `SkRuntimeEffect` and is therefore
 * deferred to D2.4.a. This port only covers the first `DEF_GM`,
 * which uses **only** primitives already shipped (`SkLumaColorFilter`,
 * `SkLinearGradient`, standard blend modes, `saveLayer`).
 *
 * **What's drawn** : a 6-column × 4-row grid where each column
 * exercises a different `SkBlendMode` (`kSrcOver`, `kDstOver`,
 * `kSrcATop`, `kDstATop`, `kSrcIn`, `kDstIn`) and each row pairs
 * two shader configurations (none/none, none/g2, g1/none, g1/g2).
 * Inside each cell, two ovals are drawn through a `saveLayer` /
 * blend / `saveLayer` chain modulated by the luma color filter on
 * the second oval.
 *
 * **Iso-fidelity caveat** : labels (column / row text) are skipped
 * to avoid font-rendering drift dominating the similarity. The
 * pixel content of the cells themselves should match upstream.
 */
public class LumaFilterGM : GM() {

    override fun getName(): String = "lumafilter"
    override fun getISize(): SkISize = SkISize.Make(600, 420)

    private lateinit var fFilter: SkColorFilter
    private lateinit var fGr1: SkShader
    private lateinit var fGr2: SkShader

    override fun onOnceBeforeDraw() {
        // kColor1 = (1, 1, 0, 1) — pure yellow ; kColor2 = (0x82/255, 1, 0, 1) —
        // yellow-green. Each shader's stop array is `(kColor, kColor.withAlpha(0x20))`
        // mapped to positions (0.2, 1.0) — Skia's stop-with-implicit-pos rule places
        // the first stop at index 0 too if `pos` doesn't start at 0, but the upstream
        // gradient builder pads it implicitly. We set explicit positions.
        val color1Full = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0)        // yellow
        val color1Fade = SkColorSetARGB(0x20, 0xFF, 0xFF, 0)        // yellow alpha 0x20
        val color2Full = SkColorSetARGB(0xFF, 0x82, 0xFF, 0)        // yellow-green
        val color2Fade = SkColorSetARGB(0x20, 0x82, 0xFF, 0)        // yellow-green alpha 0x20

        fFilter = SkLumaColorFilter.Make()
        fGr1 = SkLinearGradient.Make(
            SkPoint(0f, 0f), SkPoint(0f, 100f),
            intArrayOf(color1Full, color1Fade), floatArrayOf(0.2f, 1.0f),
            SkTileMode.kClamp,
        )
        fGr2 = SkLinearGradient.Make(
            SkPoint(0f, 0f), SkPoint(K_SIZE, 0f),
            intArrayOf(color2Full, color2Fade), floatArrayOf(0.2f, 1.0f),
            SkTileMode.kClamp,
        )
    }

    /**
     * Mirrors upstream's `draw_scene` helper. Two ovals composed
     * through a `saveLayer` chain ; the second oval is modulated by
     * [filter] (`SkLumaColorFilter`) and applied via [mode].
     */
    private fun drawScene(
        canvas: SkCanvas,
        filter: SkColorFilter,
        mode: SkBlendMode,
        s1: SkShader?,
        s2: SkShader?,
    ) {
        val color1Full = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0)
        val color1Fade = SkColorSetARGB(0x80, 0xFF, 0xFF, 0)
        val color2Full = SkColorSetARGB(0xFF, 0x82, 0xFF, 0)
        val color2Fade = SkColorSetARGB(0x80, 0x82, 0xFF, 0)

        val paint = SkPaint()
        paint.isAntiAlias = true
        val bounds = SkRect.MakeWH(K_SIZE, K_SIZE)

        // Background : 1/8-alpha blue rectangle covering the whole cell.
        val c = SkRect.MakeLTRB(bounds.left, bounds.top, bounds.centerX(), bounds.bottom)
        paint.color = SkColorSetARGB(0x20, 0, 0, 0xFF)
        canvas.drawRect(bounds, paint)

        canvas.saveLayer(bounds, null)

        // First oval — no color filter ; if no shader, fall back to a yellow-ish color.
        val r1 = SkRect.MakeLTRB(bounds.left + K_INSET, bounds.top, bounds.right - K_INSET, bounds.bottom)
        paint.shader = s1
        paint.color = if (s1 != null) SkColorSetARGB(0xFF, 0, 0, 0) else color1Fade
        canvas.drawOval(r1, paint)
        if (s1 == null) {
            canvas.save()
            canvas.clipRect(c)
            paint.color = color1Full
            canvas.drawOval(r1, paint)
            canvas.restore()
        }

        // Inner saveLayer with the blend mode for the second oval.
        val xferPaint = SkPaint()
        xferPaint.blendMode = mode
        canvas.saveLayer(bounds, xferPaint)

        val r2 = SkRect.MakeLTRB(bounds.left, bounds.top + K_INSET, bounds.right, bounds.bottom - K_INSET)
        paint.shader = s2
        paint.color = if (s2 != null) SkColorSetARGB(0xFF, 0, 0, 0) else color2Fade
        paint.colorFilter = filter
        canvas.drawOval(r2, paint)
        if (s2 == null) {
            canvas.save()
            canvas.clipRect(c)
            paint.color = color2Full
            canvas.drawOval(r2, paint)
            canvas.restore()
        }

        canvas.restore()
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val modes = arrayOf(
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
        )
        // 4 rows : (none/none, none/g2, g1/none, g1/g2)
        val shaderPairs: Array<Pair<SkShader?, SkShader?>> = arrayOf(
            null to null,
            null to fGr2,
            fGr1 to null,
            fGr1 to fGr2,
        )

        val gridStep = K_SIZE + 2 * K_INSET

        for (rowIdx in shaderPairs.indices) {
            c.save()
            c.translate(K_INSET, gridStep * rowIdx + 30f)
            for (m in modes.indices) {
                drawScene(c, fFilter, modes[m], shaderPairs[rowIdx].first, shaderPairs[rowIdx].second)
                c.translate(gridStep, 0f)
            }
            c.restore()
        }
        // Note : column-header labels and row labels are intentionally
        // skipped — text rendering drift would dominate the similarity
        // metric. The cell pixels above are what we're validating.
    }

    private companion object {
        private const val K_SIZE: Float = 80f
        private const val K_INSET: Float = 10f
    }
}
