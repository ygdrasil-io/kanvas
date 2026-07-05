package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/drawregionmodes.cpp` (375 × 500).
 * Stresses region drawing under rotated CTM, paint image filter (blur),
 * paint mask filter (blur), paint stroke + dash path effect, paint shader.
 * **Adaptation**: [GmCanvas] does not expose drawRegion; region rects are
 * converted to a [Path] with two sub-rects.
 * @see https://github.com/google/skia/blob/main/gm/drawregionmodes.cpp
 */
class DrawRegionModesGm : SkiaGm {
    override val name = "drawregionmodes"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 375
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 1f, 0f, 1f)

        val regionPath = Path { }.apply {
            addRect(Rect(50f, 50f, 100f, 100f))
            addRect(Rect(50f, 100f, 150f, 150f))
        }

        var paint = Paint(
            style = PaintStyle.FILL,
            color = Color.RED,
            antiAlias = true,
        )

        canvas.save()
        canvas.translate(-50f, 75f)
        canvas.rotate(-45f)
        canvas.drawPath(regionPath, paint)

        canvas.translate(125f, 125f)
        paint = paint.copy(imageFilter = ImageFilter.Blur(5f, 5f, input = null))
        canvas.drawPath(regionPath, paint)

        canvas.translate(-125f, 125f)
        paint = paint.copy(imageFilter = null, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, blurSigma(5f)))
        canvas.drawPath(regionPath, paint)

        canvas.translate(-125f, -125f)
        paint = paint.copy(
            maskFilter = null,
            style = PaintStyle.STROKE,
            pathEffect = PathEffect.Dash(floatArrayOf(5f, 5f), 2.5f),
        )
        canvas.drawPath(regionPath, paint)

        canvas.restore()

        canvas.translate(100f, 325f)
        paint = Paint(
            style = PaintStyle.FILL,
            shader = Shader.LinearGradient(
                Point(50f, 50f), Point(150f, 150f),
                listOf(GradientStop(0f, Color.BLUE), GradientStop(1f, Color(0xFFFFFF00u))),
            ),
        )
        canvas.drawPath(regionPath, paint)
    }

    private companion object {
        fun blurSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
