package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/drawatlas.cpp::DrawAtlasGM` (DEF_GM, name
 * `draw-atlas`, 640 × 480).
 *
 * Builds a 100×100 atlas where everything is red except the target
 * rect `(50, 50, 80, 90)` — that rect carries a 1-pixel-clear inset
 * around a blue antialiased oval. Then `drawAtlas` is called twice
 * with four per-sprite [SkRSXform]s :
 *  1. `SkBlendMode.kDst` with `colors = null` — the texture sample
 *     pixels pass through untouched.
 *  2. `SkBlendMode.kSrcIn` with per-sprite colours — upstream tints
 *     each sprite with `0x80FF0000 + i*40*256` (alpha-half red,
 *     green ramp).
 *
 * **Adaptation** — `:kanvas-skia` (Phase I5.3 status) ignores the
 * `colors` parameter to [SkCanvas.drawAtlas] and treats `blendMode`
 * as a documentation hint only; the per-sprite tint in the second
 * pass therefore does not render, so the second row will diverge
 * from the reference. The first pass (kDst, no tint) should match
 * pixel-close.
 */
public class DrawAtlasGM : GM() {

    override fun getName(): String = "draw-atlas"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val target = SkRect.MakeLTRB(50f, 50f, 80f, 90f)
        val atlas = makeAtlas(target)

        // Four sprite recipes — (scale, degrees, tx, ty).
        data class Rec(val scale: Float, val degrees: Float, val tx: Float, val ty: Float)
        val rec = arrayOf(
            Rec(1f, 0f, 10f, 10f),
            Rec(2f, 0f, 110f, 10f),
            Rec(1f, 30f, 210f, 10f),
            Rec(2f, -30f, 310f, 30f),
        )
        val n = rec.size
        val xform = Array(n) {
            val r = rec[it]
            val rad = r.degrees * (Math.PI.toFloat() / 180f)
            SkRSXform(
                fSCos = r.scale * cos(rad),
                fSSin = r.scale * sin(rad),
                fTx = r.tx,
                fTy = r.ty,
            )
        }
        val tex = Array(n) { target }
        val colors = IntArray(n) { 0x80FF0000.toInt() + (it * 40 * 256) }

        val paint = SkPaint().apply { isAntiAlias = true }
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)

        c.drawAtlas(
            image = atlas,
            xform = xform,
            src = tex,
            colors = null,
            blendMode = SkBlendMode.kDst,
            sampling = sampling,
            cullRect = null,
            paint = paint,
        )
        c.translate(0f, 100f)
        c.drawAtlas(
            image = atlas,
            xform = xform,
            src = tex,
            colors = colors,
            blendMode = SkBlendMode.kSrcIn,
            sampling = sampling,
            cullRect = null,
            paint = paint,
        )
    }

    /** Builds the 100×100 atlas — red background, target rect = blue oval. */
    private fun makeAtlas(target: SkRect): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(100, 100))
        val canvas = surface.canvas
        canvas.clear(SK_ColorRED)

        val paint = SkPaint().apply { blendMode = SkBlendMode.kClear }
        val r = target.makeOutset(1f, 1f)
        canvas.drawRect(r, paint)

        paint.blendMode = SkBlendMode.kSrcOver
        paint.color = SK_ColorBLUE
        paint.isAntiAlias = true
        canvas.drawOval(target, paint)
        return surface.makeImageSnapshot()
    }
}
