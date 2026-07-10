package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/conicpaths.cpp::ConicPathsGM`.
 * Adapted: conic curves replaced with quad approximations.
 * @see https://github.com/google/skia/blob/main/gm/conicpaths.cpp
 */
class ConicPathsGm : SkiaGm {
    override val name = "conicpaths"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 0.0
    override val width = 920
    override val height = 960

    private val paths: List<Path> = buildPaths()

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val margin = 15f
        canvas.translate(margin, margin)

        for (p in paths.indices) {
            canvas.save()
            for (alpha in intArrayOf(0xFF, 0x40)) {
                for (aa in 0 until 2) {
                    for (fh in 0 until 2) {
                        val style = if (fh != 0) PaintStyle.STROKE else PaintStyle.FILL
                        val paint = Paint(
                            color = Color.fromRGBA(0f, 0f, 0f, alpha / 255f),
                            antiAlias = aa != 0,
                            style = style,
                        )
                        canvas.drawPath(paths[p], paint)
                        canvas.translate(110f, 0f)
                    }
                }
            }
            canvas.restore()
            canvas.translate(0f, 110f)
        }
    }

    private fun buildPaths(): List<Path> {
        return listOf(
            Path {
                moveTo(100f, 0f)
                quadTo(100f, 55.23f, 55.23f, 100f)
                quadTo(0f, 100f, 0f, 55.23f)
                quadTo(0f, 0f, 55.23f, 0f)
                close()
            },
            Path { moveTo(0f, 0f); quadTo(0f, 100f, 100f, 100f) },
            Path { moveTo(0f, 0f); quadTo(100f, 100f, 5f, 0f) },
            Path { moveTo(0f, 0f); quadTo(100f, 100f, 1f, 0f) },
            Path { moveTo(0f, 0f); quadTo(100f, 100f, 0f, 0f) },
            Path { moveTo(0f, 0f); quadTo(0f, 100f, 100f, 100f) },
            Path { moveTo(0f, 0f); quadTo(100f, 100f, 5f, 0f) },
            Path { moveTo(0f, 0f); quadTo(100f, 100f, 1f, 0f) },
            Path { moveTo(0f, 0f); quadTo(100f, 100f, 0f, 0f) },
        )
    }
}
