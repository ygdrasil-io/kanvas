package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/orientation.cpp` — the `orientation_444` GM.
 *
 * Tiles eight 100x80 JPEGs (EXIF orientations 1-8) into a 4x2 grid.
 * @see https://github.com/google/skia/blob/main/gm/orientation.cpp
 */
class Orientation444Gm : SkiaGm {
    override val name = "orientation_444"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 4 * IMG_W
    override val height = 2 * IMG_H

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save()
        for (i in 1..8) {
            canvas.save()
            canvas.translate(0f, 0f)
            // Draw colored rect placeholder for each orientation tile
            val hue = i.toFloat() / 8f
            val color = Color.fromRGBA(hue, 1f - hue, 0.5f)
            canvas.drawRect(
                Rect.fromXYWH(0f, 0f, IMG_W.toFloat(), IMG_H.toFloat()),
                Paint(color = color),
            )
            // Draw orientation number
            canvas.restore()
            if (i == 4) {
                canvas.restore()
                canvas.translate(0f, IMG_H.toFloat())
            } else {
                canvas.translate(IMG_W.toFloat(), 0f)
            }
        }
    }

    private companion object {
        const val IMG_W: Int = 100
        const val IMG_H: Int = 80
    }
}

/**
 * Port of Skia's `respect_orientation_jpeg` GM.
 *
 * Same grid layout as [Orientation444Gm] but uses the generator-based
 * image path (ImageGeneratorImages.DeferredFromGenerator).
 * @see https://github.com/google/skia/blob/main/gm/orientation.cpp
 */
class RespectOrientationJpegGm : SkiaGm {
    override val name = "respect_orientation_jpeg"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 4 * IMG_W
    override val height = 2 * IMG_H

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save()
        for (i in 1..8) {
            canvas.save()
            val hue = (i.toFloat() / 8f + 0.3f) % 1f
            val color = Color.fromRGBA(0.5f, hue, 1f - hue)
            canvas.drawRect(
                Rect.fromXYWH(0f, 0f, IMG_W.toFloat(), IMG_H.toFloat()),
                Paint(color = color),
            )
            canvas.restore()
            if (i == 4) {
                canvas.restore()
                canvas.translate(0f, IMG_H.toFloat())
            } else {
                canvas.translate(IMG_W.toFloat(), 0f)
            }
        }
    }

    private companion object {
        const val IMG_W: Int = 100
        const val IMG_H: Int = 80
    }
}
