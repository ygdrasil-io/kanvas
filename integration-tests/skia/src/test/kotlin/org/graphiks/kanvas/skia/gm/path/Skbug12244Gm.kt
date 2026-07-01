package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/strokes.cpp::skbug12244`.
 * Hand-baked stroked-triangle outline drawn as plain green fill.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class Skbug12244Gm : SkiaGm {
    override val name = "skbug12244"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 150

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(2.7854299545288085938f, -6.9635753631591796875f)
            lineTo(120.194366455078125f, 40f)
            lineTo(-7.5000004768371582031f, 91.07775115966796875f)
            lineTo(-7.5000004768371582031f, -11.077748298645019531f)
            lineTo(2.7854299545288085938f, -6.9635753631591796875f)
            moveTo(-2.7854299545288085938f, 6.9635753631591796875f)
            lineTo(0f, 0f)
            lineTo(7.5f, 0f)
            lineTo(7.5000004768371582031f, 68.92224884033203125f)
            lineTo(79.805633544921875f, 40f)
            lineTo(-2.7854299545288085938f, 6.9635753631591796875f)
        }

        val paint = Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f))
        canvas.translate(20f, 20f)
        canvas.drawPath(path, paint)
    }
}
