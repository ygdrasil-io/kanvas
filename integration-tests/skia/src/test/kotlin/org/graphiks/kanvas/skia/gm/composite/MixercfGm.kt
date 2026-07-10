package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/mixercolorfilter.cpp`.
 * Tests lerp between two color filters over a sweep gradient with varying paint colors.
 * @see https://github.com/google/skia/blob/main/gm/mixercolorfilter.cpp
 */
class MixercfGm : SkiaGm {
    override val name = "mixerCF"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 900

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val tileSize = 200f
        val tileHeight = 250f
        val tileCount = 5

        val sweep = Shader.SweepGradient(
            Point(tileSize / 2f, tileHeight / 2f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.33f, Color.GREEN),
                GradientStop(0.66f, Color.BLUE),
                GradientStop(1f, Color.RED),
            ),
        )

        val cf0 = makeTintColorFilter(0xff300000.toInt(), 0xffa00000.toInt())
        val cf1 = makeTintColorFilter(0xff003000.toInt(), 0xff00a000.toInt())
        val noop = ColorFilter.Matrix(
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        )

        mixRow(canvas, sweep, tileSize, tileHeight, tileCount, noop, cf1)
        mixRow(canvas, sweep, tileSize, tileHeight, tileCount, cf0, noop)
        mixRow(canvas, sweep, tileSize, tileHeight, tileCount, cf0, cf1)
    }

    private fun makeTintColorFilter(lo: Int, hi: Int): ColorFilter {
        val rLo = (lo shr 16) and 0xFF; val gLo = (lo shr 8) and 0xFF
        val bLo = lo and 0xFF; val aLo = (lo shr 24) and 0xFF
        val rHi = (hi shr 16) and 0xFF; val gHi = (hi shr 8) and 0xFF
        val bHi = hi and 0xFF; val aHi = (hi shr 24) and 0xFF
        val tintMatrix = floatArrayOf(
            0f, 0f, 0f, (rHi - rLo) / 255f, rLo / 255f,
            0f, 0f, 0f, (gHi - gLo) / 255f, gLo / 255f,
            0f, 0f, 0f, (bHi - bLo) / 255f, bLo / 255f,
            0f, 0f, 0f, (aHi - aLo) / 255f, aLo / 255f,
        )
        val inner = ColorFilter.Luma
        val outer = ColorFilter.Matrix(tintMatrix)
        return ColorFilter.Compose(outer, inner)
    }

    private fun mixRow(
        canvas: GmCanvas, shader: Shader,
        tileSize: Float, tileHeight: Float, tileCount: Int,
        cf0: ColorFilter, cf1: ColorFilter,
    ) {
        val paintColors = listOf(
            Color.fromRGBA(1f, 1f, 1f, 1f),
            Color.fromRGBA(1f, 1f, 1f, 0.5f),
            Color.fromRGBA(0.5f, 0.5f, 1f, 1f),
            Color.fromRGBA(0.5f, 0.5f, 1f, 0.5f),
        )

        canvas.translate(0f, tileHeight * 0.1f)
        canvas.save()
        for (i in 0 until tileCount) {
            val t = i.toFloat() / (tileCount - 1)
            val lerpCF = ColorFilter.Lerp(t, cf0, cf1)
            val paint = Paint(
                shader = shader,
                colorFilter = lerpCF,
                color = paintColors[i % paintColors.size],
            )
            canvas.translate(tileSize * 0.1f, 0f)
            canvas.drawRect(Rect(0f, 0f, tileSize, tileHeight), paint)
            canvas.translate(tileSize * 1.1f, 0f)
        }
        canvas.restore()
        canvas.translate(0f, tileHeight * 1.1f)
    }
}
