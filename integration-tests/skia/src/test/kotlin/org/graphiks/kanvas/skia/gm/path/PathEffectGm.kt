package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/patheffects.cpp::PathEffectGM` (800 × 600).
 *
 * Exercises the PathEffect family:
 *  - Dash
 *  - Corner
 *  - Discrete
 *
 * @see https://github.com/google/skia/blob/main/gm/patheffects.cpp
 */
class PathEffectGm : SkiaGm {
    override val name = "patheffect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val basePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = Color.BLACK,
        )

        val polyline = Path {
            moveTo(20f, 20f)
            lineTo(70f, 120f)
            lineTo(120f, 30f)
            lineTo(170f, 80f)
            lineTo(240f, 50f)
        }

        // Column 1: Different path effects
        canvas.save()
        
        // Hair line
        canvas.drawPath(polyline, basePaint.copy(strokeWidth = 0f, pathEffect = null))
        canvas.translate(0f, 75f)

        // Hair + corner
        canvas.drawPath(polyline, basePaint.copy(strokeWidth = 0f, pathEffect = PathEffect.Corner(25f)))
        canvas.translate(0f, 75f)

        // Dash + corner
        canvas.drawPath(polyline, basePaint.copy(
            strokeWidth = 12f, 
            pathEffect = PathEffect.Dash(floatArrayOf(20f, 10f, 10f, 10f), 0f)
        ))
        canvas.translate(0f, 75f)
        
        canvas.restore()

        // Column 2: Fill and discrete effects
        val r = Rect(0f, 0f, 250f, 120f)
        val composite = Path { }.apply {
            addOval(r)
            addRect(Rect(r.left + 50f, r.top + 50f, r.right - 50f, r.bottom - 50f))
        }
        
        canvas.translate(320f, 20f)
        
        // Fill
        canvas.drawPath(composite, basePaint.copy(
            style = PaintStyle.FILL, 
            pathEffect = null
        ))
        canvas.translate(0f, 160f)
        
        // Discrete
        canvas.drawPath(composite, basePaint.copy(
            style = PaintStyle.STROKE,
            pathEffect = PathEffect.Discrete(10f, 4f)
        ))
    }
}
