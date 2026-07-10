package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/tilemodes_alpha.cpp`.
 * Regression for crbug.com/957275 — image shader with translucent paint must propagate alpha.
 * @see https://github.com/google/skia/blob/main/gm/tilemodes_alpha.cpp
 */
class TilemodesAlphaGm : SkiaGm {
    override val name = "tilemodes_alpha"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val sourceImage: Image = run {
        val w = 32; val h = 32
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                pixels[i] = ((x * 255 / (w - 1)) and 0xFF).toByte()
                pixels[i + 1] = ((y * 255 / (h - 1)) and 0xFF).toByte()
                pixels[i + 2] = (((x + y) * 255 / (w + h - 2)) and 0xFF).toByte()
                pixels[i + 3] = 0xFF.toByte()
            }
        }
        Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "mandrill-standin")
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = sourceImage

        val modes = arrayOf(
            TileMode.CLAMP,
            TileMode.REPEAT,
            TileMode.MIRROR,
            TileMode.DECAL,
        )

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val rect = Rect(128f * x + 1f, 128f * y + 1f, 128f * x + 127f, 128f * y + 127f)
                val localMatrix = Matrix33.translate(rect.left, rect.top)
                val shader = org.graphiks.kanvas.paint.Shader.WithLocalMatrix(
                    image.makeShader(tileModeX = modes[x], tileModeY = modes[y]),
                    localMatrix,
                )
                val paint = Paint(
                    shader = shader,
                    color = Color.fromRGBA(0f, 0f, 0f, 0.5f),
                )
                canvas.drawRect(rect, paint)
            }
        }
    }
}
