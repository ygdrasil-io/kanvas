package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/surface.cpp` (copy-on-write retain variant 2).
 *  Tests copy-on-write with a yellow-filled red surface snapshot.
 *  @see https://github.com/google/skia/blob/main/gm/surface.cpp
 */
class CopyOnWriteRetain2Gm : SkiaGm {
    override val name = "copy_on_write_retain2"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(256, 256)
        surf.canvas { clear(Color.BLUE) }
        val image = surf.makeImageSnapshot()
        surf.canvas { clear(Color.RED) }
        surf.canvas {
            clipRect(Rect.fromLTRB(0f, 0f, 128f, 256f))
            drawImage(image, Rect(0f, 0f, 256f, 256f))
        }
        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 256f, 256f))
    }
}
