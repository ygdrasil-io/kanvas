package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/** Tests clipping to a rounded-rect path with overlapping semi-transparent draws. */
class ClipSuperRRectGm : SkiaGm {
    override val name = "clipsuperrrect"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val clipPath = Path { }.apply { addRRect(RRectF32.of(RectF32(20f, 20f, 200f, 200f), 40f)) }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(RectF32.ofOriginSize(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = ColorARGB.fromRGBA(1f, 0f, 0f, 0.5f)))
        canvas.drawCircle(128f, 128f, 80f, Paint(color = ColorARGB.fromRGBA(0f, 0f, 1f, 0.5f)))
        canvas.drawRect(RectF32.ofOriginSize(50f, 50f, 100f, 100f),
            Paint(color = ColorARGB.fromRGBA(0f, 1f, 0f, 0.5f)))
        canvas.restore()
    }
}
