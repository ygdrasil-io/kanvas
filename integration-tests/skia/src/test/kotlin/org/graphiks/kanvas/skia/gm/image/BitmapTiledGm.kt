package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect

private const val K_TILE_SIZE: Int = 1 shl 10
private const val K_BITMAP_LONG_EDGE: Int = 7 * K_TILE_SIZE
private const val K_BITMAP_SHORT_EDGE: Int = 1 * K_TILE_SIZE

/**
 * Port of Skia's `gm/bitmaptiled.cpp` (manual horizontal variant).
 * Exercises tiled bitmap drawing with fractional offsets.
 * @see https://github.com/google/skia/blob/main/gm/bitmaptiled.cpp
 */
class BitmapTiledFractionalHorizontalManualGm : SkiaGm {
    override val name = "bitmaptiled_fractional_horizontal_manual"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 1124
    override val height = 365

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawTiles(canvas, vertical = false)
    }
}

/**
 * Port of Skia's `gm/bitmaptiled.cpp` (manual vertical variant).
 * Exercises tiled bitmap drawing with fractional offsets.
 * @see https://github.com/google/skia/blob/main/gm/bitmaptiled.cpp
 */
class BitmapTiledFractionalVerticalManualGm : SkiaGm {
    override val name = "bitmaptiled_fractional_vertical_manual"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 365
    override val height = 1124

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawTiles(canvas, vertical = true)
    }
}

private fun drawTiles(canvas: GmCanvas, vertical: Boolean) {
    val w = if (vertical) K_BITMAP_SHORT_EDGE else K_BITMAP_LONG_EDGE
    val h = if (vertical) K_BITMAP_LONG_EDGE else K_BITMAP_SHORT_EDGE
    val surf = Surface(w, h)
    surf.canvas { }
    val image = surf.makeImageSnapshot()

    for (i in 0 until 10) {
        val offset = i * 0.1f
        val src = if (vertical) {
            Rect.fromXYWH(0f, (K_TILE_SIZE - 50) + offset, 32f, 1124f)
        } else {
            Rect.fromXYWH((K_TILE_SIZE - 50) + offset, 0f, 1124f, 32f)
        }
        val dst = if (vertical) {
            Rect.fromXYWH(37f * i, 0f, 32f, 1124f)
        } else {
            Rect.fromXYWH(0f, 37f * i, 1124f, 32f)
        }
        canvas.drawImageRect(image, src, dst)
    }
}
