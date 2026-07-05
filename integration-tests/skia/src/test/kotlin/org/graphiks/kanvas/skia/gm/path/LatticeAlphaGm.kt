package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/lattice.cpp::DEF_SIMPLE_GM_BG(lattice_alpha, ...)` (120 × 120).
 * Exercises code paths that incorporate the paint colour when drawing a
 * lattice from an alpha-only (A8) image.
 * @see https://github.com/google/skia/blob/main/gm/lattice.cpp
 */
class LatticeAlphaGm : SkiaGm {
    override val name = "lattice_alpha"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 120
    override val height = 120

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f) // white background

        val image = makeAlphaImage()
        val divs = listOf(20, 40, 60, 80)
        val lattice = Lattice(xDivs = divs, yDivs = divs)
        val paint = Paint(color = Color(0xFFFF00FFu)) // magenta

        canvas.drawImageLattice(image, lattice, Rect.fromXYWH(0f, 0f, 120f, 120f), paint)
    }

    private fun makeAlphaImage(): Image {
        val w = 100
        val h = 100
        val cx = 50f
        val cy = 50f
        val r = 50f
        val pixels = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                pixels[y * w + x] = if (dist <= r) 0xFF.toByte() else 0x00.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.ALPHA_8, "lattice_alpha")
    }
}
