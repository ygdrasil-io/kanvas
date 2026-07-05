package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/patheffects.cpp::CTMPathEffectGM`.
 * The custom path effect inflation is inlined: the CTM-aware normal
 * inflation is computed directly to produce the device-space quad
 * without requiring a SkPathEffect subclass.
 * @see https://github.com/google/skia/blob/main/gm/patheffects.cpp
 */
class CTMPathEffectGm : SkiaGm {
    override val name = "ctmpatheffect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val strokeWidth = 16f
        val pxInflate = 0.5f
        val p0 = Point(100f, 100f)
        val p1 = Point(200f, 200f)

        val inflatedPaint = Paint(color = Color.BLUE, antiAlias = true)
        val strokePaint = Paint(
            color = Color.GREEN,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = strokeWidth,
        )

        // Identity CTM: inflate in source space (= device space)
        canvas.drawPath(inflatedQuad(p0, p1, strokeWidth, pxInflate, 1f, 1f), inflatedPaint)
        canvas.save()
        canvas.translate(150f, 0f)
        canvas.scale(2.5f, 0.5f)
        // Scaled CTM: compute inflation in device space, return source-space quad.
        // Canvas will apply the same CTM during drawPath, so the quad appears
        // correctly in device space with the inflation already accounted for.
        canvas.drawPath(inflatedQuad(p0, p1, strokeWidth, pxInflate, 2.5f, 0.5f), inflatedPaint)
        canvas.restore()

        // Reference strokes — always drawn through the canvas CTM.
        canvas.drawPath(Path { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y) }, strokePaint)
        canvas.save()
        canvas.translate(150f, 0f)
        canvas.scale(2.5f, 0.5f)
        canvas.drawPath(Path { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y) }, strokePaint)
        canvas.restore()
    }

    /**
     * Compute a filled quad that covers the stroke area of a line from [p0] to [p1]
     * with the given [strokeWidth], inflated in device space by [pxInflate].
     *
     * [scaleX] and [scaleY] are the current CTM scale factors used to map the
     * normal from source space to device space and back. The returned path is
     * in source space so the canvas can apply its current transform naturally.
     */
    private fun inflatedQuad(
        p0: Point, p1: Point,
        strokeWidth: Float, pxInflate: Float,
        scaleX: Float, scaleY: Float,
    ): Path {
        val radius = strokeWidth / 2f
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val len = sqrt(dx * dx + dy * dy)
        if (len == 0f) return Path { }

        // Source-space normal (perpendicular to the line, length = radius)
        val nxSrc = -dy / len * radius
        val nySrc = dx / len * radius

        // Map to device space
        val nxDev = nxSrc * scaleX
        val nyDev = nySrc * scaleY

        // Inflate in device space by pxInflate pixels
        val devLen = sqrt(nxDev * nxDev + nyDev * nyDev)
        val inflatedLen = devLen + pxInflate
        val factor = inflatedLen / devLen
        val nxDevI = nxDev * factor
        val nyDevI = nyDev * factor

        // Map back to source space
        val nsx = nxDevI / scaleX
        val nsy = nyDevI / scaleY

        return Path {
            moveTo(p0.x + nsx, p0.y + nsy)
            lineTo(p1.x + nsx, p1.y + nsy)
            lineTo(p1.x - nsx, p1.y - nsy)
            lineTo(p0.x - nsx, p0.y - nsy)
            close()
        }
    }
}
