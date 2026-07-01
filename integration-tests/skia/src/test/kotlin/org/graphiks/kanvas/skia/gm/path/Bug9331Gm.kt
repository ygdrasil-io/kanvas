package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bug9331.cpp::bug9331`.
 * clipRect followed by drawRect(stroke + dash) rendered differently in
 * debug vs release builds.
 * @see https://github.com/google/skia/blob/main/gm/bug9331.cpp
 */
class Bug9331Gm : SkiaGm {
    override val name = "bug9331"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 48.8
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val clip = Rect(0f, 0f, 200f, 150f)

        run {
            val p = Paint(color = Color.fromRGBA(0x44 / 255f, 0f, 0f, 1f))
            canvas.drawRect(clip, p)
        }

        fun draw(color: Color, clipRect: Rect) {
            val intervals = floatArrayOf(13f, 17f)
            val p = Paint(
                color = color,
                style = PaintStyle.STROKE,
                strokeWidth = 10f,
                pathEffect = PathEffect.Dash(intervals, 9f),
            )
            canvas.save()
            canvas.clipRect(clipRect)
            canvas.drawPath(Path { }.also { it.addRect(Rect(50f, 50f, 150f, 150f)) }, p)
            canvas.restore()
        }

        draw(Color.BLACK, clip)
        draw(Color.BLUE, Rect(0f, 150f, 200f, 300f))
    }
}
