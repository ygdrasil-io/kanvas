package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Partial port of Skia's `gm/tilemodes_scaled.cpp::ScaledTiling2GM`
 * (`scaled_tilemode_gradient`, 650 × 610).
 *
 * 3 × 3 grid of `(2*gWidth) × (2*gHeight)` rects under `scale(1.5,
 * 1.5)`. Each cell uses a different `(tmx, tmy)` permutation of
 * `(kClamp, kRepeat, kMirror)` ; the shader inside is one of three
 * gradient flavours selected by the *y*-tile mode's ordinal :
 *  - `0` (Clamp) → `LinearGradient`
 *  - `1` (Repeat) → `RadialGradient`
 *  - `2` (Mirror) → `SweepGradient(135°..225°)`
 *
 * The `tx` parameter is the gradient's [SkTileMode]. We deliberately
 * skip upstream's row/column text labels (no [SkTextUtils.DrawString]
 * port yet) ; the reference PNG includes those labels so the residual
 * diff is concentrated on the text-band rows / left column.
 */
public open class ScaledTiling2GM(
    private val gmName: String,
    private val proc: (tx: SkTileMode, ty: SkTileMode) -> SkShader,
) : GM() {

    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(650, 610)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(3f / 2f, 3f / 2f)

        val w = G_WIDTH.toFloat()
        val h = G_HEIGHT.toFloat()
        val r = SkRect.MakeLTRB(-w, -h, w * 2, h * 2)

        val modes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)

        // Skipped : top-row "Clamp/Repeat/Mirror" labels and per-row
        // labels at left — upstream renders these via SkTextUtils.

        var y = 24f + 16f + h
        for (ky in modes.indices) {
            var x = 16f + w + 50f
            for (kx in modes.indices) {
                val paint = SkPaint().apply { shader = proc(modes[kx], modes[ky]) }
                c.save()
                c.translate(x, y)
                c.drawRect(r, paint)
                c.restore()
                x += r.width() * 4f / 3f
            }
            y += r.height() * 4f / 3f
        }
    }

    public companion object {
        public const val G_WIDTH: Int = 32
        public const val G_HEIGHT: Int = 32

        /** Gradient shader factory matching upstream's `make_grad`. */
        public fun makeGrad(tx: SkTileMode, ty: SkTileMode): SkShader {
            val pts = arrayOf(SkPoint(0f, 0f), SkPoint(G_WIDTH.toFloat(), G_HEIGHT.toFloat()))
            val center = SkPoint(G_WIDTH / 2f, G_HEIGHT / 2f)
            val rad = G_WIDTH / 2f
            val colors = intArrayOf(0xFFFF0000.toInt(), ToolUtils.colorTo565(0xFF0044FF.toInt()))
            return when (ty.ordinal % 3) {
                0 -> SkLinearGradient.Make(pts[0], pts[1], colors, null, tx)
                1 -> SkRadialGradient.Make(center, rad, colors, null, tx)
                else -> SkSweepGradient.Make(center, 135f, 225f, colors, null, tx)
            }
        }
    }
}

/** `scaled_tilemode_gradient` GM (the bitmap variant isn't ported). */
public class ScaledTilingGradientGM : ScaledTiling2GM(
    gmName = "scaled_tilemode_gradient",
    proc = ::makeGradFn,
)

private fun makeGradFn(tx: SkTileMode, ty: SkTileMode): SkShader =
    ScaledTiling2GM.makeGrad(tx, ty)
