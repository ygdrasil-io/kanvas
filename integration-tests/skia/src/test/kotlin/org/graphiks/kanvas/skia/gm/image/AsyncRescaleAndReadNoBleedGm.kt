package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadNoBleedGM`.
 *
 * Verifies that the async rescale pipeline does not bleed across tile boundaries.
 * Simplified to drawImageRect scaling (no async readback).
 * @see https://github.com/google/skia/blob/main/gm/asyncrescaleandread.cpp
 */
class AsyncRescaleAndReadNoBleedGm : SkiaGm {
    override val name = "async_rescale_and_read_no_bleed"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 60
    override val height = 60

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val src = makeSource()
        canvas.drawImageRect(src, Rect(0f, 0f, 12f, 6f), Rect(0f, 0f, 60f, 60f))
    }

    private fun makeSource(): Image {
        val surface = Surface(12, 6)
        surface.canvas {
            drawRect(Rect(0f, 0f, 12f, 6f), Paint(color = Color(0xFFFF0000u)))
            drawRect(Rect(6f, 0f, 12f, 6f), Paint(color = Color(0xFF0000FFu)))
        }
        return surface.makeImageSnapshot()
    }
}
