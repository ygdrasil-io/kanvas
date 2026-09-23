package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedFilterNodeV1
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.intersectF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.geometry.translateF64OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.vector.Vector2F64

/**
 * W6c's only spatial-bounds authority.  It keeps public filter rectangles and offsets in F64
 * through mapping, then seals outward I32 resource rectangles exactly once.
 */
internal object W6cSpatialBoundsPlanner {
    internal data class CropPlan(val bounds: FilterBoundsPlanV1, val cropInputTargetLocalI32: RectI32,
        val clipOutputTargetLocalF64: RectF64)
    internal data class OffsetPlan(val bounds: FilterBoundsPlanV1, val offsetDeviceF64X: Double, val offsetDeviceF64Y: Double)
    internal data class TilePlan(val bounds: FilterBoundsPlanV1, val sourceInputTargetLocalI32: RectI32,
        val clipOutputTargetLocalF64: RectF64)

    internal fun crop(source: W6bFilterGraphConstruction.SourceBinding, node: CapturedFilterNodeV1.Crop): CropPlan {
        val cropDeviceF64 = map(node.copyCrop(), source.mapping) ?: refuse("Crop bounds cannot be mapped through the sealed layer transform.")
        val desired = seal(cropDeviceF64)
        val sourceDomain = source.copyDeviceBoundsI32()
        val cropLocal = rebase(desired, source.originDeviceI32)
        val produced = producedOutputBounds(node, source.copyKnownContentDeviceI32(), source.mapping)
        return CropPlan(bounds(source, desired, sourceDomain, produced), cropLocal,
            localClip(cropDeviceF64, Point2I32(desired.left, desired.top)))
    }

    internal fun offset(source: W6bFilterGraphConstruction.SourceBinding, node: CapturedFilterNodeV1.Offset): OffsetPlan {
        val offsetDevice = source.mapping.mapLocalVectorToDeviceF64OrNull(Vector2F64(node.dx.toDouble(), node.dy.toDouble()))
            ?: refuse("Offset requires a finite affine local-to-device displacement.")
        val input = source.copyDeviceBoundsI32()
        val translated = rectF64(input).translateF64OrNull(offsetDevice.x, offsetDevice.y)
            ?: refuse("Offset bounds are non-finite.")
        val desired = seal(translated)
        val produced = producedOutputBounds(node, source.copyKnownContentDeviceI32(), source.mapping)
        return OffsetPlan(bounds(source, desired, input, produced), offsetDevice.x, offsetDevice.y)
    }

    internal fun tile(source: W6bFilterGraphConstruction.SourceBinding, node: CapturedFilterNodeV1.Tile): TilePlan {
        val sourceDevice = map(node.copySource(), source.mapping) ?: refuse("Tile source bounds cannot be mapped through the sealed layer transform.")
        val destinationDevice = map(node.copyDestination(), source.mapping) ?: refuse("Tile destination bounds cannot be mapped through the sealed layer transform.")
        val desired = seal(destinationDevice)
        val sourceLocal = rebase(seal(sourceDevice), source.originDeviceI32)
        // A Tile only produces inside dst.  Its required source is the frozen src period, even
        // when a consumer asks for only one repeated cell.
        val required = seal(sourceDevice)
        val produced = producedOutputBounds(node, source.copyKnownContentDeviceI32(), source.mapping)
        return TilePlan(bounds(source, desired, required, produced),
            sourceLocal, localClip(destinationDevice, Point2I32(desired.left, desired.top)))
    }

    /** Reverse demand used while W6a chooses its source target, before physical allocation. */
    internal fun requiredInputBounds(
        operation: CapturedFilterNodeV1,
        desiredOutput: RectI32,
        mapping: LayerMappingF64,
    ): RectI32? = when (operation) {
        is CapturedFilterNodeV1.Crop -> intersect(desiredOutput, map(operation.copyCrop(), mapping)?.let(::seal) ?: return null)
        is CapturedFilterNodeV1.Offset -> mapping.mapLocalVectorToDeviceF64OrNull(
            Vector2F64(operation.dx.toDouble(), operation.dy.toDouble()),
        )?.let { offset -> seal(rectF64(desiredOutput).translateF64OrNull(-offset.x, -offset.y) ?: return null) }
        is CapturedFilterNodeV1.Tile -> {
            val destination = map(operation.copyDestination(), mapping)?.let(::seal) ?: return null
            if (intersect(desiredOutput, destination) == null) null else map(operation.copySource(), mapping)?.let(::seal)
        }
        else -> null
    }

    internal fun producedOutputBounds(
        operation: CapturedFilterNodeV1,
        knownContent: RectI32?,
        mapping: LayerMappingF64,
    ): RectI32? = knownContent?.let { known -> when (operation) {
        is CapturedFilterNodeV1.Crop -> map(operation.copyCrop(), mapping)?.let(::seal)?.let { crop ->
            if (operation.tileMode == TileMode.DECAL) intersect(known, crop) else crop
        } ?: return null
        is CapturedFilterNodeV1.Offset -> mapping.mapLocalVectorToDeviceF64OrNull(
            Vector2F64(operation.dx.toDouble(), operation.dy.toDouble()),
        )?.let { offset -> seal(rectF64(known).translateF64OrNull(offset.x, offset.y) ?: return null) }
        is CapturedFilterNodeV1.Tile -> map(operation.copySource(), mapping)?.let(::seal)?.let { source ->
            intersect(known, source)?.let { map(operation.copyDestination(), mapping)?.let(::seal) }
        }
        else -> null
    } }

    private fun bounds(source: W6bFilterGraphConstruction.SourceBinding, desired: RectI32, required: RectI32,
        produced: RectI32?): FilterBoundsPlanV1 = FilterBoundsPlanV1(produced, desired, required,
        produced, Point2I32(desired.left, desired.top))
    private fun map(rect: org.graphiks.math.geometry.RectF32, mapping: LayerMappingF64): RectF64? =
        mapping.mapLocalRectToDeviceF64OrNull(RectF64(rect.left.toDouble(), rect.top.toDouble(), rect.right.toDouble(), rect.bottom.toDouble()))
    private fun seal(rect: RectF64): RectI32 = rect.roundOutToRectI32OrNull()
        ?: refuse("W6c F64 bounds cannot be outward-rounded into I32 texels.")
    private fun rectF64(rect: RectI32): RectF64 = RectF64(rect.left.toDouble(), rect.top.toDouble(), rect.right.toDouble(), rect.bottom.toDouble())
    private fun rebase(rect: RectI32, origin: Point2I32): RectI32 = try {
        RectI32(Math.subtractExact(rect.left, origin.x), Math.subtractExact(rect.top, origin.y),
            Math.subtractExact(rect.right, origin.x), Math.subtractExact(rect.bottom, origin.y))
    } catch (_: ArithmeticException) { refuse("W6c target-local rectangle overflows I32.") }
    private fun localClip(rect: RectF64, origin: Point2I32): RectF64 = rect.translateF64OrNull(-origin.x.toDouble(), -origin.y.toDouble())
        ?: refuse("W6c target-local F64 clip is non-finite.")
    private fun intersect(first: RectI32, second: RectI32): RectI32? = RectF64(
        first.left.toDouble(), first.top.toDouble(), first.right.toDouble(), first.bottom.toDouble(),
    ).intersectF64OrNull(RectF64(second.left.toDouble(), second.top.toDouble(), second.right.toDouble(), second.bottom.toDouble()))?.let(::seal)
    private fun refuse(message: String): Nothing = throw W6bFilterGraphConstruction.ConstructionFailure(
        W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, message),
    )
}
