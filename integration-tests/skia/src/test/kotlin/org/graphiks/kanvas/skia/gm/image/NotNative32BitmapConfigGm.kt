package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.drawCircle
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontEdging
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(not_native32_bitmap_config, ...)` (128 × 128).
 * Tests drawing a non-native (kBGRA_8888) bitmap.
 * @see https://github.com/google/skia/blob/main/gm/all_bitmap_configs.cpp
 */
class NotNative32BitmapConfigGm : SkiaGm {
    override val name = "not_native32_bitmap_config"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 65.0
    override val requiresZeroRefusals = true
    override val width = 128
    override val height = 128

    private val typeface = checkNotNull(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf"))

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas)
        canvas.drawImage(makeNotNative32ColorWheel().toImage(), Rect.fromXYWH(0f, 0f, SCALE.toFloat(), SCALE.toFloat()))
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        for (y in 0 until SCALE step CHECK_SIZE) {
            for (x in 0 until SCALE step CHECK_SIZE) {
                val dark = ((x / CHECK_SIZE) + (y / CHECK_SIZE)) % 2 == 0
                canvas.drawRect(
                    Rect.fromXYWH(x.toFloat(), y.toFloat(), CHECK_SIZE.toFloat(), CHECK_SIZE.toFloat()),
                    Paint(color = if (dark) Color(0xFFCCCCCCu) else Color.WHITE, antiAlias = false),
                )
            }
        }
    }

    /** Mirrors Skia's native color wheel, then copies its pixels into a BGRA bitmap. */
    private fun makeNotNative32ColorWheel(): Bitmap {
        val wheel = Surface(SCALE, SCALE)
        val font = Font(
            typeface,
            size = 0.28125f * SCALE,
            isEmbolden = true,
            edging = FontEdging.ALIAS,
        )
        wheel.canvas {
            drawCircle(SCALE / 2f, SCALE / 2f, SCALE / 2f, Paint(color = Color.WHITE))
            drawCenteredLetter("K", 0f, 0f, font, Color.BLACK)
            drawCenteredLetter("R", 0f, 0.3f * SCALE, font, Color.RED)
            drawCenteredLetter("G", -0.3f * SCALE * SQRT_3_OVER_2, -0.15f * SCALE, font, Color.GREEN)
            drawCenteredLetter("B", 0.3f * SCALE * SQRT_3_OVER_2, -0.15f * SCALE, font, Color.BLUE)
            drawCenteredLetter("C", 0f, -0.3f * SCALE, font, Color(0xFF00FFFFu))
            drawCenteredLetter("M", 0.3f * SCALE * SQRT_3_OVER_2, 0.15f * SCALE, font, Color(0xFFFF00FFu))
            drawCenteredLetter("Y", -0.3f * SCALE * SQRT_3_OVER_2, 0.15f * SCALE, font, Color(0xFFFFFF00u))
        }

        val rendered = wheel.render()
        return Bitmap(SCALE, SCALE, ColorType.BGRA_8888).also { bitmap ->
            for (y in 0 until SCALE) {
                for (x in 0 until SCALE) {
                    val offset = (y * SCALE + x) * 4
                    bitmap.setPixel(
                        x,
                        y,
                        Color.fromRGBA(
                            (rendered.pixels[offset].toInt() and 0xFF) / 255f,
                            (rendered.pixels[offset + 1].toInt() and 0xFF) / 255f,
                            (rendered.pixels[offset + 2].toInt() and 0xFF) / 255f,
                            (rendered.pixels[offset + 3].toInt() and 0xFF) / 255f,
                        ),
                    )
                }
            }
        }
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawCenteredLetter(
        letter: String,
        relativeX: Float,
        relativeY: Float,
        font: Font,
        color: Color,
    ) {
        val glyphId = font.typeface.glyphIdForCodepoint(letter.codePointAt(0))
        val path = font.typeface.getGlyphPath(glyphId, font.size) ?: return
        val bounds = path.computeBounds() ?: return
        val x = SCALE / 2f + relativeX - (bounds.left + bounds.right) / 2f
        val y = SCALE / 2f + relativeY - (bounds.top + bounds.bottom) / 2f
        drawPath(
            path.transform(x, y, 1f, 1f),
            Paint(
                color = color,
                style = PaintStyle.STROKE_AND_FILL,
                strokeWidth = font.size * 0.02f,
                antiAlias = false,
            ),
        )
    }

    private companion object {
        const val SCALE = 128
        const val CHECK_SIZE = 8
        const val SQRT_3_OVER_2 = 0.8660254f
    }
}
