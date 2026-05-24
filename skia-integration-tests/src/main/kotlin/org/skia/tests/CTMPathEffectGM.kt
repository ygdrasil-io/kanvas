package org.skia.tests

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathEffect
import kotlin.math.sqrt

/**
 * Port of upstream Skia `gm/patheffects.cpp::CTMPathEffectGM`.
 *
 * The custom path effect inflates a line by a fixed device-pixel amount,
 * so it must map its normal through the current CTM and back into source
 * space before synthesising the fill geometry.
 */
public class CTMPathEffectGM : GM() {
    override fun getName(): String = "ctmpatheffect"
    override fun getISize(): SkISize = SkISize.Make(800, 600)

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return

        val strokeWidth = 16f
        val path = SkPath.Line(100f to 100f, 200f to 200f)
        val effect = StrokeLineInflated(strokeWidth, 0.5f)

        val inflatedPaint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
            pathEffect = effect
        }
        val strokePaint = SkPaint().apply {
            color = SK_ColorGREEN
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            this.strokeWidth = strokeWidth
        }

        canvas.drawPath(path, inflatedPaint)
        canvas.save()
        canvas.translate(150f, 0f)
        canvas.scale(2.5f, 0.5f)
        canvas.drawPath(path, inflatedPaint)
        canvas.restore()

        canvas.drawPath(path, strokePaint)
        canvas.save()
        canvas.translate(150f, 0f)
        canvas.scale(2.5f, 0.5f)
        canvas.drawPath(path, strokePaint)
        canvas.restore()
    }
}

private class StrokeLineInflated(
    strokeWidth: Float,
    private val pxInflate: Float,
) : SkPathEffect() {
    private val radius = strokeWidth / 2f

    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        val (p0, p1) = input.isLine() ?: return null
        val invCtm = ctm.invert() ?: return null

        val sourceNormal = vectorWithLength(
            p0.fY - p1.fY,
            p1.fX - p0.fX,
            radius,
        ) ?: return null

        val mappedNormal = ctm.mapVector(sourceNormal.first, sourceNormal.second)
        val inflatedDeviceNormal = vectorWithLength(
            mappedNormal.fX,
            mappedNormal.fY,
            vectorLength(mappedNormal.fX, mappedNormal.fY) + pxInflate,
        ) ?: return null
        val inflatedSourceNormal = invCtm.mapVector(
            inflatedDeviceNormal.first,
            inflatedDeviceNormal.second,
        )

        val nx = inflatedSourceNormal.fX
        val ny = inflatedSourceNormal.fY
        return SkPathBuilder()
            .moveTo(p0.fX + nx, p0.fY + ny)
            .lineTo(p1.fX + nx, p1.fY + ny)
            .lineTo(p1.fX - nx, p1.fY - ny)
            .lineTo(p0.fX - nx, p0.fY - ny)
            .close()
            .detach()
    }

    private fun vectorWithLength(x: Float, y: Float, targetLength: Float): Pair<Float, Float>? {
        val length = vectorLength(x, y)
        if (length == 0f || !length.isFinite()) return null
        val scale = targetLength / length
        return x * scale to y * scale
    }

    private fun vectorLength(x: Float, y: Float): Float = sqrt(x * x + y * y)
}
