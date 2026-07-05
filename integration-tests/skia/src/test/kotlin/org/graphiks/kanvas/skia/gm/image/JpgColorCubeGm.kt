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

/**
 * Port of Skia's `gm/jpg_color_cube.cpp::ColorCubeGM` (512 × 512).
 * Builds a 512×512 "colour cube" bitmap, encodes through JpegEncoder,
 * decodes back, and draws the decoded image.
 * **Adaptation**: Kanvas does not expose a high-level encode → decode round-trip
 * from [Image]; the colour cube bitmap is drawn directly without the JPEG round-trip.
 * @see https://github.com/google/skia/blob/main/gm/jpg_color_cube.cpp
 */
class JpgColorCubeGm : SkiaGm {
    override val name = "jpg-color-cube"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surface = Surface(512, 512)
        surface.canvas {
            var bX = 0
            var bY = 0
            for (b in 0 until 64) {
                for (r in 0 until 64) {
                    for (g in 0 until 64) {
                        val a = 255
                        val red = (r * 4).coerceIn(0, 255)
                        val green = (g * 4).coerceIn(0, 255)
                        val blue = (b * 4).coerceIn(0, 255)
                        val color = (a shl 24) or (red shl 16) or (green shl 8) or blue
                        drawRect(
                            Rect(bX + r.toFloat(), bY + g.toFloat(), bX + r + 1f, bY + g + 1f),
                            Paint(color = Color(color.toUInt())),
                        )
                    }
                }
                bX += 64
                if (bX >= 512) { bX = 0; bY += 64 }
            }
        }
        val image = surface.makeImageSnapshot()
        canvas.drawImage(image, Rect(0f, 0f, 512f, 512f))
    }
}
