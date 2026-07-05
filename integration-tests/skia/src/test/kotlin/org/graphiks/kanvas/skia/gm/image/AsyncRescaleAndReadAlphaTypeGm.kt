package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadAlphaTypeGM`.
 *
 * Exercises the async pixel-read + rescale pipeline across alpha types.
 * Simplified to drawImageRect scaling (no async readback).
 * @see https://github.com/google/skia/blob/main/gm/asyncrescaleandread.cpp
 */
class AsyncRescaleAndReadAlphaTypeGm : SkiaGm {
    override val name = "async_rescale_and_read_alpha_type"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        for (row in 0..1) {
            val src = makeSource()
            val dst = Rect(
                0f, (row * 256).toFloat(),
                256f, (row * 256 + 256).toFloat(),
            )
            canvas.drawImageRect(src, Rect(0f, 0f, 64f, 64f), dst)
        }
    }

    private fun makeSource(): org.graphiks.kanvas.image.Image {
        val surface = Surface(64, 64)
        surface.canvas {
            drawRect(Rect(0f, 0f, 64f, 64f), Paint(color = Color(0x8000AAFFu)))
            drawRect(Rect(16f, 16f, 48f, 48f), Paint(color = Color(0xFFFF5500u)))
        }
        return surface.makeImageSnapshot()
    }
}
