package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(all_bitmap_configs, ...)`.
 * @see https://github.com/google/skia/blob/main/gm/all_bitmap_configs.cpp
 */
class AllBitmapConfigsGm : SkiaGm {
    override val name = "all_bitmap_configs"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 768

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 12f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0.753f, 0.753f, 0.753f, 1f)

        val paint = Paint(color = Color.BLACK)
        canvas.drawString("Native 32 (deferred)", 0f, 12f, font, paint)
        canvas.translate(0f, 128f)
        canvas.drawString("RGB 565 (deferred)", 0f, 12f, font, paint)
        canvas.translate(0f, 128f)
        canvas.drawString("ARGB 4444 (deferred)", 0f, 12f, font, paint)
        canvas.translate(0f, 128f)
        canvas.drawString("RGBA F16 (deferred)", 0f, 12f, font, paint)
        canvas.translate(0f, 128f)
        canvas.drawString("Alpha 8 (deferred)", 0f, 12f, font, paint)
        canvas.translate(0f, 128f)
        paint.copy(color = Color.RED)
        canvas.drawString("Gray 8 (deferred)", 0f, 12f, font, Paint(color = Color.RED))
    }
}
