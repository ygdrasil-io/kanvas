package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPerlinNoiseShader
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseLocalMatrixGM` (640 × 480).
 *
 * Exercises `SkShader::makeWithLocalMatrix` over the perlin-noise shader.
 * Renders four 80×80 perlin tiles in a 2 × 2 grid :
 *  - top row : two tiles at canvas-default scale 1×.
 *  - second row : same tiles at canvas-scale 2× — `drawRect` uses the
 *    same 80×80 rect, but the canvas's `scale(2, 2)` enlarges them.
 *  - third row : repeats the second row's "scale-2" output, but the
 *    scale is achieved by `paint.shader = paint.shader.makeWithLocalMatrix(2x)`
 *    while drawing a 160×160 rect with the canvas at default scale.
 *
 * The third row should match the second row pixel-for-pixel — the local
 * matrix on a shader composes the same way as a canvas-level scale (cf.
 * upstream comment "should draw the same as the previous").
 */
public class PerlinNoiseLocalMatrixGM : GM() {

    override fun getName(): String = "perlinnoise_localmatrix"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)

        val kSize = SkISize.Make(80, 80)
        val paint = SkPaint().apply {
            shader = SkPerlinNoiseShader.MakeFractalNoise(
                0.1f, 0.1f, 2, 0f, kSize,
            )
        }

        val w = kSize.width.toFloat()
        val h = kSize.height.toFloat()
        var r = SkRect.MakeWH(w, h)

        c.drawRect(r, paint)

        c.save()
        c.translate(w * 5f / 4f, 0f)
        c.drawRect(r, paint)
        c.restore()

        c.save()
        c.translate(0f, h + 10f)
        c.scale(2f, 2f)
        c.drawRect(r, paint)
        c.restore()

        c.save()
        c.translate(w + 100f, h + 10f)
        c.scale(2f, 2f)
        c.drawRect(r, paint)
        c.restore()

        // Local-matrix row — drawn at the same screen-space size but
        // using a shader local matrix instead of a canvas scale.
        c.translate(0f, h * 2f + 10f)

        val lm = SkMatrix.MakeScale(2f, 2f)
        paint.shader = paint.shader!!.makeWithLocalMatrix(lm)
        r = SkRect.MakeWH(r.width() * 2f, r.height() * 2f)

        c.save()
        c.translate(0f, h + 10f)
        c.drawRect(r, paint)
        c.restore()

        c.save()
        c.translate(w + 100f, h + 10f)
        c.drawRect(r, paint)
        c.restore()
    }
}
