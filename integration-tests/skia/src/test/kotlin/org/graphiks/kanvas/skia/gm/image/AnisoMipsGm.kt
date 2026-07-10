package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/anisotropic.cpp` (aniso-mips variant).
 *  Draws a gradient-backed image at various scale factors to test
 *  anisotropic mipmap filtering.
 *  @see https://github.com/google/skia/blob/main/gm/anisotropic.cpp
 */
class AnisoMipsGm : SkiaGm {
    override val name = "anisomips"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 520
    override val height = 260

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surface = Surface(kImageSize, kImageSize)
        val kScales = floatArrayOf(1f, 0.5f, 0.25f, 0.125f)
        val kColors = listOf(Color.fromRGBA(0.94f, 0.94f, 0.94f, 1f), Color.BLUE, Color.GREEN, Color.RED)

        for (shaderPass in listOf(false, true)) {
            var ci = 0
            canvas.save()
            for (sy in kScales) {
                canvas.save()
                for (sx in kScales) {
                    canvas.save()
                    canvas.scale(sx, sy)
                    val color = kColors[ci]
                    val image = updateImage(surface, color)
                    if (shaderPass) {
                        val paint = Paint(shader = image.makeShader())
                        canvas.drawRect(Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
                    } else {
                        canvas.drawImage(image, Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()))
                    }
                    canvas.restore()
                    canvas.translate(kImageSize * sx + kPad, 0f)
                    ci = (ci + 1) % kColors.size
                }
                canvas.restore()
                canvas.translate(0f, kImageSize * sy + kPad)
            }
            canvas.restore()
            for (sx in kScales) {
                canvas.translate(kImageSize * sx + kPad, 0f)
            }
        }
    }

    private fun updateImage(surf: Surface, color: Color): Image {
        surf.canvas {
            drawColor(color)
            val innerColor = Color.fromRGBA(
                0.06f, 0.06f, 0.06f, 1f,
            )
            val rect = Rect.fromLTRB(
                surf.width * 2f / 5f, surf.height * 2f / 5f,
                surf.width * 3f / 5f, surf.height * 3f / 5f,
            )
            drawRect(rect, Paint(color = innerColor))
        }
        return surf.makeImageSnapshot()
    }

    private companion object {
        const val kImageSize: Int = 128
        const val kPad: Float = 5f
    }
}
