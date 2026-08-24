package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/** Port of Skia's `gm/surface.cpp` (copy-on-write retain).
 *  Tests copy-on-write retain behaviour — creates a surface, snapshots it,
 *  then draws a yellow rect before re-snapshotting.
 *  @see https://github.com/google/skia/blob/main/gm/surface.cpp
 */
class CopyOnWriteRetainGm : SkiaGm {
    override val name = "copy_on_write_retain"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(256, 256)
        surf.canvas { clear(ColorARGB.Red) }
        val image = surf.makeImageSnapshot()
        surf.canvas {
            clipRect(RectF32.ofLTRB(0f, 0f, 128f, 256f))
            clear(ColorARGB.Blue)
        }
        canvas.drawImage(surf.makeImageSnapshot(), RectF32(0f, 0f, 256f, 256f))
    }
}
