package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseGM` (220 x 620).
 * 14 noise rect draws stacked vertically exercising fractal noise and
 * turbulence shaders with various octaves, seeds, stitching, and scale.
 * @see https://github.com/google/skia/blob/main/gm/perlinnoise.cpp
 */
class PerlinNoiseGm : SkiaGm {
    override val name = "perlinnoise"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 220
    override val height = 620

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kBox = Size(80f, 80f)
        val tile40 = Size(40f, 40f)

        test(canvas, Point(0f, 0f), Type.FRACTAL, stitch = false, fx = 0.1f, fy = 0.1f, oct = 0, seed = 0f, kSize = kBox)
        test(canvas, Point(100f, 0f), Type.TURBULENCE, stitch = false, fx = 0.1f, fy = 0.1f, oct = 0, seed = 0f, kSize = kBox)
        test(canvas, Point(0f, 100f), Type.FRACTAL, stitch = false, fx = 0.1f, fy = 0.1f, oct = 2, seed = 0f, kSize = kBox)
        test(canvas, Point(100f, 100f), Type.FRACTAL, stitch = true, fx = 0.05f, fy = 0.1f, oct = 1, seed = 0f, tile = tile40)
        test(canvas, Point(0f, 200f), Type.TURBULENCE, stitch = true, fx = 0.1f, fy = 0.1f, oct = 1, seed = 0f, tile = tile40)
        test(canvas, Point(100f, 200f), Type.TURBULENCE, stitch = false, fx = 0.2f, fy = 0.4f, oct = 5, seed = 0f, kSize = kBox)
        test(canvas, Point(0f, 300f), Type.FRACTAL, stitch = false, fx = 0.1f, fy = 0.1f, oct = 3, seed = 1f, kSize = kBox)
        test(canvas, Point(100f, 300f), Type.FRACTAL, stitch = false, fx = 0.1f, fy = 0.1f, oct = 3, seed = 4f, kSize = kBox)

        canvas.save()
        canvas.scale(0.75f, 1.0f)
        test(canvas, Point(0f, 400f), Type.FRACTAL, stitch = false, fx = 0.1f, fy = 0.1f, oct = 2, seed = 0f, kSize = kBox)
        test(canvas, Point(100f, 400f), Type.FRACTAL, stitch = true, fx = 0.1f, fy = 0.05f, oct = 1, seed = 0f, tile = tile40)
        canvas.restore()

        test(canvas, Point(0f, 500f), Type.TURBULENCE, stitch = true, fx = 0.03f, fy = 0.03f, oct = 1, seed = 0f, tile = Size(50f, 50f))
        test(canvas, Point(120f, 500f), Type.TURBULENCE, stitch = false, fx = 0.05f, fy = 0.05f, oct = 2, seed = 0f, kSize = kBox)
    }

    private enum class Type { FRACTAL, TURBULENCE }

    private fun test(
        canvas: GmCanvas, pt: Point, type: Type,
        stitch: Boolean, fx: Float, fy: Float, oct: Int, seed: Float,
        kSize: Size? = null, tile: Size? = null,
    ) {
        val tileSize = if (stitch) tile else null
        val shader: Shader = when (type) {
            Type.FRACTAL -> {
                if (tileSize != null) Shader.FractalNoise(fx, fy, oct, seed.toInt(), tileSize)
                else Shader.FractalNoise(fx, fy, oct, seed.toInt(), null)
            }
            Type.TURBULENCE -> {
                if (tileSize != null) Shader.PerlinNoise(fx, fy, oct, seed.toInt(), tileSize)
                else Shader.PerlinNoise(fx, fy, oct, seed.toInt(), null)
            }
        }
        val paint = Paint(shader = shader)
        if (stitch && tile != null) {
            drawRect(canvas, Point(pt.x, pt.y), paint, tile)
            drawRect(canvas, Point(pt.x + tile.width, pt.y), paint, tile)
            drawRect(canvas, Point(pt.x + tile.width, pt.y + tile.height), paint, tile)
            drawRect(canvas, Point(pt.x, pt.y + tile.height), paint, tile)
        } else {
            drawRect(canvas, pt, paint, kSize ?: Size(80f, 80f))
        }
    }

    private fun drawRect(canvas: GmCanvas, pt: Point, paint: Paint, size: Size) {
        canvas.save()
        canvas.translate(pt.x, pt.y)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, size.width, size.height), paint)
        canvas.restore()
    }
}
