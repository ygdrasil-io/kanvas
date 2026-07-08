package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/composecolorfilter.cpp` — ComposeCFIF variant.
 * Tests composing a color filter with an inner Luma filter.
 * @see https://github.com/google/skia/blob/main/gm/composecolorfilter.cpp
 */
class ComposecfifGm : SkiaGm {
    override val name = "composeCFIF"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 604
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rLo = 0x30; val gLo = 0x00; val bLo = 0x00; val aLo = 0xFF
        val rHi = 0xA0; val gHi = 0x00; val bHi = 0x00; val aHi = 0xFF
        val tintMatrix = floatArrayOf(
            0f, 0f, 0f, (rHi - rLo) / 255f, rLo / 255f,
            0f, 0f, 0f, (gHi - gLo) / 255f, gLo / 255f,
            0f, 0f, 0f, (bHi - bLo) / 255f, bLo / 255f,
            0f, 0f, 0f, (aHi - aLo) / 255f, aLo / 255f,
        )
        val outer = ColorFilter.Matrix(tintMatrix)
        val inner = ColorFilter.Luma
        val cf = ColorFilter.Compose(outer, inner)
        val shader = Shader.PerlinNoise(0.01f, 0.01f, 2, 0, null)

        canvas.save()
        canvas.clipRect(Rect(0f, 0f, 200f, 200f))
        canvas.drawRect(Rect(0f, 0f, 200f, 200f), Paint(shader = shader, colorFilter = cf))
        canvas.restore()

        canvas.translate(202f, 0f)
        canvas.save()
        canvas.clipRect(Rect(0f, 0f, 200f, 200f))
        canvas.drawRect(Rect(0f, 0f, 200f, 200f), Paint(shader = shader, colorFilter = cf))
        canvas.restore()

        canvas.translate(202f, 0f)
        canvas.save()
        canvas.clipRect(Rect(0f, 0f, 200f, 200f))
        canvas.drawRect(Rect(0f, 0f, 200f, 200f), Paint(shader = shader, colorFilter = cf))
        canvas.restore()
    }
}
