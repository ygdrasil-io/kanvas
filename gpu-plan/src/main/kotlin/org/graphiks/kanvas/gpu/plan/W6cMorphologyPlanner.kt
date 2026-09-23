package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.expandForMorphologyF64OrNull
import org.graphiks.math.geometry.insetForMorphologyF64OrNull
import org.graphiks.math.geometry.morphologyRadiusTexelsI32OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.vector.Vector2F64

/** W6c's frozen F64 bounds authority for separable Dilate and Erode passes. */
internal object W6cMorphologyPlanner {
    internal data class DeviceRadii(val radiusXF64: Double, val radiusYF64: Double, val radiusXTexelsI32: Int, val radiusYTexelsI32: Int)

    internal fun deviceRadii(radiusXF64: Double, radiusYF64: Double, mapping: LayerMappingF64): DeviceRadii {
        val device = mapping.mapLocalMorphologyRadiiToDeviceF64OrNull(Vector2F64(radiusXF64, radiusYF64))
            ?: refuse("Morphology radii require a finite affine local-to-device mapping.")
        val radiusXTexelsI32 = morphologyRadiusTexelsI32OrNull(device.x)
            ?: refuse("Morphology X radius cannot be quantized to Skia texels.")
        val radiusYTexelsI32 = morphologyRadiusTexelsI32OrNull(device.y)
            ?: refuse("Morphology Y radius cannot be quantized to Skia texels.")
        return DeviceRadii(radiusXTexelsI32.toDouble(), radiusYTexelsI32.toDouble(),
            radiusXTexelsI32, radiusYTexelsI32)
    }

    internal fun bounds(
        source: W6bFilterGraphConstruction.SourceBinding,
        morphologyKind: FilterPassOperationV1.Morphology.Kind,
        axis: FilterAxisV1,
        radiusF64: Double,
    ): FilterBoundsPlanV1 {
        val input = source.copyDeviceBoundsI32()
        val desired = if (morphologyKind == FilterPassOperationV1.Morphology.Kind.DILATE) {
            expand(input, axis, radiusF64)
        } else {
            input
        }
        val known = source.copyKnownContentDeviceI32()?.let { content ->
            when (morphologyKind) {
                FilterPassOperationV1.Morphology.Kind.DILATE -> expand(content, axis, radiusF64)
                FilterPassOperationV1.Morphology.Kind.ERODE -> inset(content, axis, radiusF64)
            }
        }
        return FilterBoundsPlanV1(known, desired, input, known, Point2I32(desired.left, desired.top))
    }

    /** Reverse demand remains outward for both extrema: every output texel samples its full support. */
    internal fun requiredInputBounds(
        radiusXF64: Double,
        radiusYF64: Double,
        desiredOutput: RectI32,
        mapping: LayerMappingF64,
    ): RectI32 {
        val radii = deviceRadii(radiusXF64, radiusYF64, mapping)
        return RectF64(
            desiredOutput.left.toDouble(), desiredOutput.top.toDouble(),
            desiredOutput.right.toDouble(), desiredOutput.bottom.toDouble(),
        ).expandForMorphologyF64OrNull(radii.radiusXF64, radii.radiusYF64)?.roundOutToRectI32OrNull()
            ?: refuse("Morphology reverse input demand cannot be outward-rounded into I32 texels.")
    }

    private fun expand(bounds: RectI32, axis: FilterAxisV1, radiusF64: Double): RectI32 = rectF64(bounds)
        .expandForMorphologyF64OrNull(if (axis == FilterAxisV1.X) radiusF64 else 0.0,
            if (axis == FilterAxisV1.Y) radiusF64 else 0.0)?.roundOutToRectI32OrNull()
        ?: refuse("Morphology output expansion cannot be outward-rounded into I32 texels.")

    private fun inset(bounds: RectI32, axis: FilterAxisV1, radiusF64: Double): RectI32? = rectF64(bounds)
        .insetForMorphologyF64OrNull(if (axis == FilterAxisV1.X) radiusF64 else 0.0,
            if (axis == FilterAxisV1.Y) radiusF64 else 0.0)?.roundOutToRectI32OrNull()

    private fun rectF64(bounds: RectI32): RectF64 = RectF64(
        bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
    )

    private fun refuse(message: String): Nothing = throw W6bFilterGraphConstruction.ConstructionFailure(
        W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, message),
    )
}
