package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class DrawAtlasColorGm : SkiaGm {
    override val name = "draw-atlas-colors"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = kNumXferModes * (kAtlasSize + kPad) + kPad
    override val height = 2 * kNumColors * (kAtlasSize + kPad) + kTextPad + kPad

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val target = Rect.fromXYWH(0f, 0f, kAtlasSize.toFloat(), kAtlasSize.toFloat())
        val atlas = makeAtlas(kAtlasSize)

        val numModes = gModes.size
        val numColors = gColors.size
        val transforms = List(numColors) { i ->
            Matrix33.translate(kPad.toFloat(), i * (target.width + kPad))
        }
        val texRects = List(numColors) { target }
        val quadColors = gColors.toList()

        val paint = Paint(color = Color.BLACK, antiAlias = true)
        val font = Font(typeface, kTextPad.toFloat())

        for (i in 0 until numModes) {
            val label = gModes[i].name
            canvas.drawString(label, i * (target.width + kPad) + kPad, kTextPad.toFloat(), font, paint)
        }

        for (i in 0 until numModes) {
            canvas.save()
            canvas.translate(i * (target.height + kPad), (kTextPad + kPad).toFloat())
            canvas.drawAtlas(atlas, transforms, texRects, quadColors, gModes[i], null)
            canvas.translate(0f, numColors * (target.height + kPad))
            canvas.drawAtlas(atlas, transforms, texRects, quadColors, gModes[i], paint)
            canvas.restore()
        }
    }

    private fun makeAtlas(atlasSize: Int): Image {
        val kBlockSize = atlasSize / 2
        val bitmap = Bitmap(atlasSize, atlasSize)
        bitmap.eraseColor(Color.TRANSPARENT)
        for (x in 0 until kBlockSize) {
            for (y in 0 until kBlockSize) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }
        for (x in kBlockSize until atlasSize) {
            for (y in 0 until kBlockSize) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }
        for (x in 0 until kBlockSize) {
            for (y in kBlockSize until atlasSize) {
                bitmap.setPixel(x, y, Color.GREEN)
            }
        }
        for (x in kBlockSize until atlasSize) {
            for (y in kBlockSize until atlasSize) {
                bitmap.setPixel(x, y, Color.TRANSPARENT)
            }
        }
        return bitmap.toImage()
    }

    private companion object {
        val gModes = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER,
            BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT,
            BlendMode.DST_OUT, BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR,
            BlendMode.PLUS, BlendMode.MODULATE, BlendMode.SCREEN, BlendMode.OVERLAY,
            BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT,
            BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.MULTIPLY,
            BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY,
        )
        val gColors = listOf(Color.WHITE, Color.RED, Color.fromRGBA(0.5f, 0.5f, 0.5f, 0.5f), Color.fromRGBA(0f, 0f, 0.5f, 0.5f))
        const val kNumXferModes = 29
        const val kNumColors = 4
        const val kAtlasSize = 30
        const val kPad = 2
        const val kTextPad = 8
    }
}
