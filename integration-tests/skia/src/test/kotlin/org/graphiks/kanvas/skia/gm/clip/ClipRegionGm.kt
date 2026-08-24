package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/clipdrawdraw.cpp::clip_region` (256 × 256).
 *
 * Two-quadrant clip-region smoke test:
 *  - Top-left: clipRect(10, 10, 100, 100) then drawColor(RED)
 *  - Center: saveLayer(30, 30, 80, 80) then clipRect + drawColor(BLUE)
 * @see https://github.com/google/skia/blob/main/gm/clipdrawdraw.cpp
 */
class ClipRegionGm : SkiaGm {
    override val name = "clip_region"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val region = RectF32.ofLTRB(10f, 10f, 100f, 100f)

        canvas.save()
        canvas.clipPath(Path { }.apply { addRect(region) })
        canvas.drawColor(1f, 0f, 0f)
        canvas.restore()

        val bounds = RectF32.ofLTRB(30f, 30f, 80f, 80f)
        canvas.saveLayer(bounds, null)
        canvas.clipPath(Path { }.apply { addRect(region) })
        canvas.drawColor(0f, 0f, 1f)
        canvas.restore()
    }
}
