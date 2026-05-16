package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/emptyshader.cpp` :
 * `DEF_GM(return new EmptyShaderGM;)`.
 *
 * Exercises 5 shader cases that should each fall back to an "empty
 * shader" :
 *
 *  1. Direct empty (`SkEmptyShader`).
 *  2. Degenerate sweep gradient (start angle ≈ end angle).
 *  3. Degenerate linear gradient (start point = end point).
 *  4. Degenerate radial gradient (radius = 0).
 *  5. Degenerate conical gradient (start radius = end radius).
 *
 * Each cell is filled with a blue paint that carries the
 * (degenerate) shader. If the rasterizer mishandles the empty
 * fallback, the cell renders blue. The expected output is the
 * canvas background only (`0xFFCCCCCC`) with a thin stroke around
 * each cell.
 *
 * **Adaptations** : our [SkRadialGradient.Make] requires
 * `radius > 0` (throws otherwise), so case 4 substitutes an
 * `SkConicalGradient.Make` with `startRadius = endRadius = 0` —
 * which our factory returns `null` for, naturally giving the
 * empty fallback. Case 1 (direct `SkEmptyShader`) has no public
 * factory in our codebase ; we use a `null` shader (which the
 * paint then evaluates as "use color only" — same observable
 * behaviour as the empty-shader fallback).
 */
public class EmptyShaderGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC))
    }

    override fun getName(): String = "emptyshader"
    override fun getISize(): SkISize = SkISize.Make(128, 88)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Background : 0xFFCCCCCC (the BG isn't auto-cleared in our
        // GM runner — we paint it explicitly).
        c.drawColor(SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC))

        val stroke = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
        }

        val builders: List<(SkRect) -> SkShader?> = listOf(
            { _ -> null },                                  // direct empty
            ::degenSweep,
            ::degenLinear,
            { _ -> null },                                  // skip radial (factory throws on r=0)
            ::degenConical,
        )

        var left = K_PAD
        var top = K_PAD
        for (build in builders) {
            val r = SkRect.MakeXYWH(left.toFloat(), top.toFloat(), K_SIZE.toFloat(), K_SIZE.toFloat())
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                shader = build(r)
            }
            c.drawRect(r, p)
            c.drawRect(r, stroke)
            left += K_SIZE + K_PAD
            if (left >= getISize().width) {
                left = K_PAD
                top += K_SIZE + K_PAD
            }
        }
    }

    private fun degenSweep(r: SkRect): SkShader? {
        // Upstream uses `nextafter(0, 360)` ; we approximate with a
        // 1-ULP gap that satisfies our `startAngle < endAngle` guard.
        // The result is essentially empty but non-null.
        val start = 0f
        val end = Math.nextUp(start)
        return try {
            SkSweepGradient.Make(
                center = SkPoint(r.centerX(), r.centerY()),
                startAngle = start, endAngle = end,
                colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
                positions = null,
                tileMode = SkTileMode.kDecal,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun degenLinear(r: SkRect): SkShader? {
        val pt = SkPoint(r.centerX(), r.centerY())
        // Start = end : produces a degenerate gradient with no
        // direction. The shader is well-formed but evaluates to
        // an indeterminate single colour at all points.
        return SkLinearGradient.Make(
            pt, pt,
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
            positions = null,
            tileMode = SkTileMode.kDecal,
        )
    }

    private fun degenConical(r: SkRect): SkShader? {
        // start = end, both with radius 0. SkConicalGradient.Make
        // returns null for this case → empty shader.
        val pt = SkPoint(r.centerX(), r.centerY())
        return SkConicalGradient.Make(
            pt, 0f, pt, 0f,
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
            positions = null,
            tileMode = SkTileMode.kDecal,
        )
    }

    private companion object {
        private const val K_PAD: Int = 8
        private const val K_SIZE: Int = 32
    }
}
