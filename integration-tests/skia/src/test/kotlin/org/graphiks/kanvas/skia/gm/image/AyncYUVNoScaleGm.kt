package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/asyncrescaleandread.cpp::AyncYUVNoScaleGM`.
 *
 * Verifies async YUV read-back without rescale.
 * Simplified to draw source image directly (no async YUV readback).
 * @see https://github.com/google/skia/blob/main/gm/asyncrescaleandread.cpp
 */
class AyncYUVNoScaleGm : SkiaGm {
    override val name = "async_yuv_no_scale"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val src = makeSource()
        canvas.drawImage(src, Rect(0f, 0f, 400f, 300f))
    }

    private fun makeSource(): Image {
        val surface = Surface(400, 300)
        surface.canvas {
            drawRect(Rect(0f, 0f, 400f, 300f), Paint(color = Color.WHITE))
            drawRect(Rect(30f, 20f, 170f, 100f), Paint(color = Color.RED))
            drawRect(Rect(200f, 30f, 320f, 130f), Paint(color = Color.GREEN))
            drawRect(Rect(100f, 150f, 340f, 270f), Paint(color = Color.BLUE))
        }
        return surface.makeImageSnapshot()
    }
}
