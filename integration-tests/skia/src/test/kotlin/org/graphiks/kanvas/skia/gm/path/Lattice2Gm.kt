package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/lattice.cpp` — `LatticeGM2`.
 * Tests nine-patch / lattice image drawing (drawImageLattice) with fixed-color
 * and 1x1 rectangle code paths.
 * @see https://github.com/google/skia/blob/main/gm/lattice.cpp
 */
class Lattice2Gm : SkiaGm {
    override val name = "lattice2"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 50.0
    override val requiresZeroRefusals = true
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect.fromXYWH(300f, 0f, 300f, 800f),
            Paint(color = Color(0x7F123456u), blendMode = BlendMode.SRC),
        )

        val srcOver = Paint(color = Color.WHITE, blendMode = BlendMode.SRC_OVER)
        drawHelper(canvas, srcOver)
        canvas.translate(0f, 400f)
        drawHelper(canvas, srcOver.copy(blendMode = BlendMode.SRC_ATOP))
    }

    private fun drawHelper(canvas: GmCanvas, paint: Paint) {
        val image = makeImage()
        val lattice = Lattice(
            xDivs = listOf(4, 5),
            yDivs = listOf(1, 2),
            flags = listOf(
                LatticeFlags.DEFAULT, LatticeFlags.DEFAULT, LatticeFlags.DEFAULT,
                LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR,
                LatticeFlags.TRANSPARENT, LatticeFlags.TRANSPARENT, LatticeFlags.TRANSPARENT,
            ),
            colors = listOf(
                Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                Color.RED, Color(0x880000FFu), Color.GREEN,
                Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
            ),
        )

        canvas.save()
        canvas.drawImage(image, Rect.fromXYWH(10f, 10f, image.width.toFloat(), image.height.toFloat()))
        canvas.drawImageLattice(
            image,
            lattice,
            Rect.fromXYWH(100f, 100f, 200f, 200f),
            paint,
            SamplingOptions.NEAREST,
        )
        canvas.translate(400f, 0f)
        canvas.drawImageLattice(
            image,
            lattice,
            Rect.fromXYWH(100f, 100f, 200f, 200f),
            paint.copy(color = Color(0x80000FFFu)),
            SamplingOptions.NEAREST,
        )
        canvas.restore()
    }

    private fun makeImage(): org.graphiks.kanvas.image.Image {
        val surface = Surface(80, 80)
        surface.canvas {
            drawRect(Rect.fromXYWH(0f, 0f, 4f, 1f), Paint(color = Color.GREEN, antiAlias = false))
            drawRect(Rect.fromXYWH(4f, 0f, 1f, 1f), Paint(color = Color.BLUE, antiAlias = false))
            drawRect(Rect.fromXYWH(5f, 0f, 75f, 1f), Paint(color = Color.RED, antiAlias = false))

            drawRect(Rect.fromXYWH(0f, 1f, 4f, 1f), Paint(color = Color.RED, antiAlias = false))
            drawRect(Rect.fromXYWH(4f, 1f, 1f, 1f), Paint(color = Color(0x880000FFu), antiAlias = false))
            drawRect(Rect.fromXYWH(5f, 1f, 75f, 1f), Paint(color = Color.GREEN, antiAlias = false))

            drawRect(Rect.fromXYWH(0f, 2f, 4f, 78f), Paint(color = Color.GREEN, antiAlias = false))
            drawRect(Rect.fromXYWH(4f, 2f, 1f, 78f), Paint(color = Color(0x88FF0000u), antiAlias = false))
            drawRect(Rect.fromXYWH(5f, 2f, 75f, 78f), Paint(color = Color.BLUE, antiAlias = false))
        }
        return surface.makeImageSnapshot()
    }
}
