package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/tilemodes_scaled.cpp` ScaledTiling2GM with make_bm (650 × 610).
 * Renders a 32×32 gradient bitmap shader at 1.5× scale with all 9 tile mode
 * combinations (Clamp, Repeat, Mirror × Clamp, Repeat, Mirror).
 * @see https://github.com/google/skia/blob/main/gm/tilemodes_scaled.cpp
 */
class ScaledTilemodeBitmapGm : SkiaGm {
    override val name = "scaled_tilemode_bitmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 610

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(1.5f, 1.5f)
        canvas.drawColor(1f, 1f, 1f, 1f)

        val bmpImage = makeBitmapImage()

        val modes = listOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)
        val modeNames = listOf("Clamp", "Repeat", "Mirror")
        val gW = 32f
        val gH = 32f
        val r = Rect(-gW, -gH, gW * 2f, gH * 2f)
        val spacingX = 96f
        val spacingY = 80f

        var y = 24f
        var x = 66f
        val font = Font(typeface, 12f)
        for (kx in 0 until 3) {
            canvas.drawString(modeNames[kx], x + r.width / 2f, y, font, Paint())
            x += spacingX
        }
        y += 16f + gH

        for (ky in 0 until 3) {
            x = 16f + gW
            canvas.drawString(modeNames[ky], x, y + gH / 2f, font, Paint())
            x += 50f
            for (kx in 0 until 3) {
                val s = Shader.Image(bmpImage, modes[kx], modes[ky])
                canvas.save()
                canvas.translate(x, y)
                canvas.drawRect(r, Paint(shader = s))
                canvas.restore()
                x += spacingX
            }
            y += spacingY
        }
    }

    private fun makeBitmapImage(): Image {
        val gradShader = Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(32f, 32f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.5f, Color.GREEN),
                GradientStop(1f, Color.BLUE),
            ),
            tileMode = TileMode.CLAMP,
        )
        val surf = Surface(32, 32)
        surf.canvas { drawRect(Rect(0f, 0f, 32f, 32f), Paint(shader = gradShader)) }
        return surf.makeImageSnapshot()
    }
}
