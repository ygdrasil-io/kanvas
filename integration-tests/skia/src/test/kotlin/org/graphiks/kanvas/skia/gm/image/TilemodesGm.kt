package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
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
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/tilemodes.cpp`.
 *  Renders a grid of Shader.Image tiles with various TileMode/filter
 *  combinations (POT and NPOT sizes).
 *  @see https://github.com/google/skia/blob/main/gm/tilemodes.cpp
 */
class TilemodesGm(
    private val powerOfTwoSize: Boolean = true,
) : SkiaGm {
    override val name: String = if (powerOfTwoSize) "tilemodes" else "tilemodes_npot"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 880
    override val height = 560

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 12f)
        val size = if (powerOfTwoSize) kPOTSize else kNPOTSize
        val r = Rect(0f, 0f, (size * 2).toFloat(), (size * 2).toFloat())

        val configNames = listOf("8888", "565")
        val filters = listOf(SamplingOptions.NEAREST, SamplingOptions.LINEAR)
        val filterNames = listOf("point", "bilinear")
        val modes = listOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)
        val modeNames = listOf("C", "R", "M")

        var y = 24f
        var x = 10f

        val textures = gColorTypes.map { ct -> makebm(ct, size, size) }

        val labelPaint = Paint()
        for (kx in modes.indices) {
            for (ky in modes.indices) {
                val str = "[${modeNames[kx]},${modeNames[ky]}]"
                canvas.drawString(str, x + r.width / 2f, y, font, labelPaint)
                x += r.width * 4f / 3f
            }
        }
        y += 16f

        for (i in gColorTypes.indices) {
            for (j in filters.indices) {
                x = 10f
                for (kx in modes.indices) {
                    for (ky in modes.indices) {
                        val image = if (powerOfTwoSize) textures[i] else makebm(gColorTypes[i], size, size)
                        val paint = Paint(
                            shader = Shader.Image(image, modes[kx], modes[ky], filters[j]),
                        )
                        canvas.save()
                        canvas.translate(x, y)
                        canvas.drawRect(r, paint)
                        canvas.restore()
                        x += r.width * 4f / 3f
                    }
                }
                val tag = "${configNames[i]}, ${filterNames[j]}"
                canvas.drawString(tag, x, y + r.height * 2f / 3f, font, Paint())
                y += r.height * 4f / 3f
            }
        }
    }

    private companion object {
        const val kPOTSize: Int = 32
        const val kNPOTSize: Int = 21

        val gColorTypes: List<String> = listOf<String>("8888", "565")

        fun makebm(ct: String, w: Int, h: Int): Image {
            val surface = Surface(w, h)
            surface.canvas {
                val pts = listOf(Point(0f, 0f), Point(w.toFloat(), h.toFloat()))
        val stops = listOf<GradientStop>(
            GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
            GradientStop(0.5f, Color.fromRGBA(0f, 1f, 0f, 1f)),
            GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
        )
                val paint = Paint(
                    shader = Shader.LinearGradient(pts[0], pts[1], stops, TileMode.CLAMP),
                )
                drawRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), paint)
            }
            return surface.makeImageSnapshot()
        }
    }
}
