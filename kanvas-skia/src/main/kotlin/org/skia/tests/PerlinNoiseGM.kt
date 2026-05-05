package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPerlinNoiseShader
import org.skia.foundation.SkShader
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseGM` (220 × 620).
 *
 * 14 noise rect draws stacked vertically:
 *  - row 0 — `kFractalNoise` / `kTurbulence`, `numOctaves = 0` (degenerate)
 *  - row 1 — `kFractalNoise` `numOctaves = 2`, then a 4-tile stitched draw
 *            (`{0.05, 0.1}` freq, octaves 1, drawn into a 2×2 grid)
 *  - row 2 — `kTurbulence` stitched single tile, then `numOctaves = 5`
 *  - row 3 — two `kFractalNoise` rects with `seed = 1` and `seed = 4`
 *  - row 4 — same as row 1's first cell but under a non-uniform `scale(.75, 1)`
 *  - row 5 — Chromium-flavour test cases for SVG `feTurbulence`
 *
 * Box size: `80 × 80` for non-stitching cells, `40 × 40` for stitching.
 */
public class PerlinNoiseGM : GM() {

    override fun getName(): String = "perlinnoise"
    override fun getISize(): SkISize = SkISize.Make(220, 620)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val kSize = SkISize.Make(80, 80)
        val tile40 = SkISize.Make(40, 40)

        test(c, SkPoint(  0f,   0f), Type.FRACTAL,    stitch = false, fx = 0.1f,  fy = 0.1f,  oct = 0, seed = 0f, kSize = kSize)
        test(c, SkPoint(100f,   0f), Type.TURBULENCE, stitch = false, fx = 0.1f,  fy = 0.1f,  oct = 0, seed = 0f, kSize = kSize)
        test(c, SkPoint(  0f, 100f), Type.FRACTAL,    stitch = false, fx = 0.1f,  fy = 0.1f,  oct = 2, seed = 0f, kSize = kSize)
        test(c, SkPoint(100f, 100f), Type.FRACTAL,    stitch = true,  fx = 0.05f, fy = 0.1f,  oct = 1, seed = 0f, tile = tile40)
        test(c, SkPoint(  0f, 200f), Type.TURBULENCE, stitch = true,  fx = 0.1f,  fy = 0.1f,  oct = 1, seed = 0f, tile = tile40)
        test(c, SkPoint(100f, 200f), Type.TURBULENCE, stitch = false, fx = 0.2f,  fy = 0.4f,  oct = 5, seed = 0f, kSize = kSize)
        test(c, SkPoint(  0f, 300f), Type.FRACTAL,    stitch = false, fx = 0.1f,  fy = 0.1f,  oct = 3, seed = 1f, kSize = kSize)
        test(c, SkPoint(100f, 300f), Type.FRACTAL,    stitch = false, fx = 0.1f,  fy = 0.1f,  oct = 3, seed = 4f, kSize = kSize)

        c.save()
        c.scale(0.75f, 1.0f)
        test(c, SkPoint(  0f, 400f), Type.FRACTAL,    stitch = false, fx = 0.1f,  fy = 0.1f,  oct = 2, seed = 0f, kSize = kSize)
        test(c, SkPoint(100f, 400f), Type.FRACTAL,    stitch = true,  fx = 0.1f,  fy = 0.05f, oct = 1, seed = 0f, tile = tile40)
        c.restore()

        // Chromium-flavour SVG `feTurbulence` reproductions.
        test(c, SkPoint(  0f, 500f), Type.TURBULENCE, stitch = true,  fx = 0.03f, fy = 0.03f, oct = 1, seed = 0f, tile = SkISize.Make(50, 50))
        test(c, SkPoint(120f, 500f), Type.TURBULENCE, stitch = false, fx = 0.05f, fy = 0.05f, oct = 2, seed = 0f, kSize = kSize)
    }

    private enum class Type { FRACTAL, TURBULENCE }

    private fun test(
        canvas: SkCanvas, pt: SkPoint, type: Type,
        stitch: Boolean, fx: Float, fy: Float, oct: Int, seed: Float,
        kSize: SkISize? = null, tile: SkISize? = null,
    ) {
        val tileSize = if (stitch) tile else null
        val shader: SkShader = when (type) {
            Type.FRACTAL    -> SkPerlinNoiseShader.MakeFractalNoise(fx, fy, oct, seed, tileSize)
            Type.TURBULENCE -> SkPerlinNoiseShader.MakeTurbulence(fx, fy, oct, seed, tileSize)
        }
        val paint = SkPaint().apply { this.shader = shader }
        if (stitch && tile != null) {
            // 2×2 block — exercises tile-stitch continuity.
            drawRect(canvas, pt, paint, tile)
            drawRect(canvas, SkPoint(pt.fX + tile.width, pt.fY), paint, tile)
            drawRect(canvas, SkPoint(pt.fX + tile.width, pt.fY + tile.height), paint, tile)
            drawRect(canvas, SkPoint(pt.fX, pt.fY + tile.height), paint, tile)
        } else {
            drawRect(canvas, pt, paint, kSize ?: SkISize.Make(80, 80))
        }
    }

    private fun drawRect(canvas: SkCanvas, pt: SkPoint, paint: SkPaint, size: SkISize) {
        canvas.save()
        canvas.translate(pt.fX, pt.fY)
        canvas.drawRect(SkRect.MakeWH(size.width.toFloat(), size.height.toFloat()), paint)
        canvas.restore()
    }
}
