package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's gm/gradients_no_texture.cpp::GradientsManyColorsGM.
 * 4 rows of horizontal-fade linear gradients with many colour stops each.
 * @see https://github.com/google/skia/blob/main/gm/gradients_no_texture.cpp
 */
class GradientManyColorsGm : SkiaGm {
    override val name = "gradients_many"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 880
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val procs = listOf(::make0, ::make1, ::make2, ::make3)
        val pts = arrayOf(Point(0f, 0f), Point(K_W.toFloat(), 0f))
        val r = Rect(0f, 0f, K_W.toFloat(), 30f)

        canvas.translate(40f, 20f)

        for (i in 0..8) {
            val x = r.width * i.toFloat() / 8f
            canvas.drawLine(x, 0f, x, 10000f, Paint())
        }

        val drawR = Rect.fromLTRB(r.left - 20f, r.top, r.right + 20f, r.bottom)
        for (proc in procs) {
            val rec = proc()
            val stops = rec.colors.mapIndexed { index, color ->
                GradientStop(rec.pos?.getOrNull(index) ?: (index.toFloat() / (rec.colors.size - 1)), color)
            }
            val paint = Paint(shader = Shader.LinearGradient(
                start = pts[0], end = pts[1],
                stops = stops, tileMode = TileMode.CLAMP,
            ))
            canvas.drawRect(drawR, paint)

            canvas.save()
            canvas.translate(r.width / 2, r.height + 4f)
            canvas.scale(-1f, 1f)
            canvas.translate(-r.width / 2, 0f)
            canvas.drawRect(drawR, paint)
            canvas.restore()

            canvas.translate(0f, r.height + 2 * r.height + 8f)
        }
    }

    private data class ColorPos(val colors: List<Color>, val pos: FloatArray?)

    private fun make0(): ColorPos {
        val colors = listOf(
            Color.fromRGBA(0x22 / 255f, 0xD1 / 255f, 0xCD / 255f, 1f),
            Color.fromRGBA(0x22 / 255f, 0xD1 / 255f, 0xCD / 255f, 1f),
            Color.fromRGBA(0xDF / 255f, 0x4B / 255f, 0x37 / 255f, 1f),
            Color.fromRGBA(0xDF / 255f, 0x4B / 255f, 0x37 / 255f, 1f),
            Color.fromRGBA(0x22 / 255f, 0xD1 / 255f, 0xCD / 255f, 1f),
            Color.fromRGBA(0x22 / 255f, 0xD1 / 255f, 0xCD / 255f, 1f),
            Color.fromRGBA(0xE6 / 255f, 0xDE / 255f, 0x36 / 255f, 1f),
            Color.fromRGBA(0xE6 / 255f, 0xDE / 255f, 0x36 / 255f, 1f),
            Color.fromRGBA(0x32 / 255f, 0x67 / 255f, 0xFF / 255f, 1f),
            Color.fromRGBA(0x32 / 255f, 0x67 / 255f, 0xFF / 255f, 1f),
            Color.fromRGBA(0x9D / 255f, 0x47 / 255f, 0xD1 / 255f, 1f),
            Color.fromRGBA(0x9D / 255f, 0x47 / 255f, 0xD1 / 255f, 1f),
            Color.fromRGBA(0x32 / 255f, 0x67 / 255f, 0xFF / 255f, 1f),
            Color.fromRGBA(0x32 / 255f, 0x67 / 255f, 0xFF / 255f, 1f),
            Color.fromRGBA(0x9D / 255f, 0x47 / 255f, 0xD1 / 255f, 1f),
            Color.fromRGBA(0x9D / 255f, 0x47 / 255f, 0xD1 / 255f, 1f),
            Color.fromRGBA(0x32 / 255f, 0x67 / 255f, 0xFF / 255f, 1f),
            Color.fromRGBA(0x32 / 255f, 0x67 / 255f, 0xFF / 255f, 1f),
            Color.fromRGBA(0xE3 / 255f, 0xD0 / 255f, 0x82 / 255f, 1f),
        )
        val percent = doubleArrayOf(
            1.0, 0.9510157507590116, 2.9510157507590113, 23.695886056604927,
            25.695886056604927, 25.39321881940624, 27.39321881940624, 31.849399922570655,
            33.849399922570655, 44.57735802921938, 46.57735802921938, 53.27185850805876,
            55.27185850805876, 61.95718972227316, 63.95718972227316, 69.89166004442,
            71.89166004442, 74.45795382765857, 76.45795382765857, 82.78364610713776,
            84.78364610713776, 94.52743647737229, 96.52743647737229, 96.03934633331295,
        )
        val pos = FloatArray(percent.size) { (percent[it] / 100.0).toFloat() }
        pos[0] = 0f
        pos[pos.size - 1] = 1f
        return ColorPos(colors, pos)
    }

    private fun make1(): ColorPos {
        val colors = listOf(
            Color.BLACK, Color.WHITE, Color.BLACK, Color.WHITE,
            Color.BLACK, Color.WHITE, Color.BLACK, Color.WHITE,
            Color.BLACK,
        )
        return ColorPos(colors, null)
    }

    private fun make2(): ColorPos {
        val colors = listOf(
            Color.BLACK, Color.WHITE, Color.BLACK, Color.WHITE,
            Color.BLACK, Color.WHITE, Color.BLACK, Color.WHITE,
            Color.BLACK,
        )
        val n = colors.size
        val pos = FloatArray(n) { it.toFloat() / (n - 1).toFloat() }
        pos[0] = 0f
        pos[n - 1] = 1f
        return ColorPos(colors, pos)
    }

    private fun make3(): ColorPos {
        val colors = listOf(
            Color.RED, Color.BLUE, Color.BLUE, Color.GREEN, Color.GREEN, Color.BLACK,
        )
        val pos = floatArrayOf(0f, 0f, 0.5f, 0.5f, 1f, 1f)
        return ColorPos(colors, pos)
    }

    private companion object {
        const val K_W: Int = 800
    }
}
