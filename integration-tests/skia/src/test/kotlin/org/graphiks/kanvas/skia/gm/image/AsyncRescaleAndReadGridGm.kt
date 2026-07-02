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
 * Port of Skia's `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadGridGM`.
 *
 * Representative RGBA port of the `async_rescale_and_read_rose` variant.
 * Simplified to drawImageRect grid (no async readback).
 * @see https://github.com/google/skia/blob/main/gm/asyncrescaleandread.cpp
 */
class AsyncRescaleAndReadGridGm : SkiaGm {
    override val name = "async_rescale_and_read_rose"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 3 * 410
    override val height = 2 * 410

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val src = makeSource()

        for (row in 0..1) {
            for (col in 0..2) {
                val dst = Rect(
                    (col * 410).toFloat(), (row * 410).toFloat(),
                    ((col + 1) * 410).toFloat(), ((row + 1) * 410).toFloat(),
                )
                canvas.drawImageRect(src, Rect(0f, 0f, 410f, 410f), dst)
            }
        }
    }

    private fun makeSource(): Image {
        val surface = Surface(410, 410)
        surface.canvas {
            drawRect(Rect(0f, 0f, 410f, 410f), Paint(color = Color.WHITE))
            drawRect(Rect(0f, 0f, 205f, 205f), Paint(color = Color(0xFFFF5555u)))
            drawRect(Rect(205f, 0f, 410f, 205f), Paint(color = Color(0xFF55FF55u)))
            drawRect(Rect(0f, 205f, 205f, 410f), Paint(color = Color(0xFF5555FFu)))
            drawRect(Rect(205f, 205f, 410f, 410f), Paint(color = Color(0xFFFFFF55u)))
        }
        return surface.makeImageSnapshot()
    }
}
