package org.graphiks.math.matrix

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.rebaseAtOriginI32OrNull

/**
 * Immutable proof that a raster already expressed in target-local texels belongs to one exact
 * device-owned target.  It never translates payload bytes: the device relation is established by
 * rebasing the authenticated device domain at the target owner origin.
 */
public class TargetLocalDeviceProvenanceI32 private constructor(
    extentI32: SizeI32,
    sourceRasterDomainLocalI32: RectI32,
    targetLocalDomainI32: RectI32,
    sourceDeviceDomainI32: RectI32,
    sourceRasterOriginLocalI32: Point2I32,
    ownerOriginDeviceI32: Point2I32,
    private val mappingSnapshot: LayerMappingF64?,
) {
    private val extentSnapshot = extentI32.copy()
    private val sourceRasterDomainSnapshot = sourceRasterDomainLocalI32.copy()
    private val targetLocalDomainSnapshot = targetLocalDomainI32.copy()
    private val sourceDeviceDomainSnapshot = sourceDeviceDomainI32.copy()
    private val sourceRasterOriginSnapshot = Point2I32(sourceRasterOriginLocalI32.x, sourceRasterOriginLocalI32.y)
    private val ownerOriginSnapshot = Point2I32(ownerOriginDeviceI32.x, ownerOriginDeviceI32.y)

    public fun copyExtentI32(): SizeI32 = extentSnapshot.copy()
    public fun copySourceRasterDomainLocalI32(): RectI32 = sourceRasterDomainSnapshot.copy()
    public fun copyTargetLocalDomainI32(): RectI32 = targetLocalDomainSnapshot.copy()
    public fun copySourceDeviceDomainI32(): RectI32 = sourceDeviceDomainSnapshot.copy()
    public fun copySourceRasterOriginLocalI32(): Point2I32 =
        Point2I32(sourceRasterOriginSnapshot.x, sourceRasterOriginSnapshot.y)
    public fun copyOwnerOriginDeviceI32(): Point2I32 = Point2I32(ownerOriginSnapshot.x, ownerOriginSnapshot.y)
    /** Immutable mapping snapshot retained with the coordinate-frame witness, when a layer owns it. */
    public fun mappingOrNull(): LayerMappingF64? = mappingSnapshot

    /** Revalidates the exact target/composite facts without projecting geometry or payload again. */
    public fun matches(
        extentI32: SizeI32,
        targetLocalDomainI32: RectI32,
        sourceRasterOriginLocalI32: Point2I32,
        ownerOriginDeviceI32: Point2I32,
        mapping: LayerMappingF64?,
    ): Boolean = sameSize(extentSnapshot, extentI32) &&
        sameRect(targetLocalDomainSnapshot, targetLocalDomainI32) &&
        samePoint(sourceRasterOriginSnapshot, sourceRasterOriginLocalI32) &&
        samePoint(ownerOriginSnapshot, ownerOriginDeviceI32) && sameMapping(mappingSnapshot, mapping) &&
        sourceDeviceDomainSnapshot.rebaseAtOriginI32OrNull(ownerOriginDeviceI32)?.let { rebased ->
            sameRect(rebased, sourceRasterDomainSnapshot) && contains(targetLocalDomainI32, rebased)
        } == true

    /** Binding-time validation when the composite mapping has not yet been published. */
    public fun matchesBinding(
        extentI32: SizeI32,
        inverseClipTargetLocalI32: RectI32,
        sourceRasterOriginLocalI32: Point2I32,
        ownerOriginDeviceI32: Point2I32,
    ): Boolean = sameSize(extentSnapshot, extentI32) &&
        samePoint(sourceRasterOriginSnapshot, sourceRasterOriginLocalI32) &&
        samePoint(ownerOriginSnapshot, ownerOriginDeviceI32) &&
        mappingSnapshot?.copyLayerOriginDeviceI32()?.let { samePoint(it, ownerOriginDeviceI32) } != false &&
        sourceDeviceDomainSnapshot.rebaseAtOriginI32OrNull(ownerOriginDeviceI32)?.let { rebased ->
            sameRect(rebased, sourceRasterDomainSnapshot) &&
                contains(sourceRasterDomainSnapshot, inverseClipTargetLocalI32) &&
                contains(targetLocalDomainSnapshot, rebased)
        } == true

    public companion object {
        /** Returns null when either coordinate frame or their checked rebase cannot be authenticated. */
        public fun ofOrNull(
            extentI32: SizeI32,
            sourceRasterDomainLocalI32: RectI32,
            targetLocalDomainI32: RectI32,
            sourceDeviceDomainI32: RectI32,
            sourceRasterOriginLocalI32: Point2I32,
            ownerOriginDeviceI32: Point2I32,
            mapping: LayerMappingF64?,
        ): TargetLocalDeviceProvenanceI32? {
            if (extentI32.isEmpty() || sourceRasterDomainLocalI32.isEmpty || targetLocalDomainI32.isEmpty || sourceDeviceDomainI32.isEmpty ||
                sourceRasterOriginLocalI32.x != 0 || sourceRasterOriginLocalI32.y != 0 ||
                sourceRasterDomainLocalI32.left < 0 || sourceRasterDomainLocalI32.top < 0 ||
                sourceRasterDomainLocalI32.right > extentI32.width || sourceRasterDomainLocalI32.bottom > extentI32.height ||
                targetLocalDomainI32.left < 0 || targetLocalDomainI32.top < 0 ||
                targetLocalDomainI32.right > extentI32.width || targetLocalDomainI32.bottom > extentI32.height ||
                mapping?.copyLayerOriginDeviceI32()?.let { !samePoint(it, ownerOriginDeviceI32) } == true
            ) return null
            val rebased = sourceDeviceDomainI32.rebaseAtOriginI32OrNull(ownerOriginDeviceI32) ?: return null
            if (!sameRect(rebased, sourceRasterDomainLocalI32) || !contains(targetLocalDomainI32, rebased)) return null
            return TargetLocalDeviceProvenanceI32(extentI32, sourceRasterDomainLocalI32, targetLocalDomainI32, sourceDeviceDomainI32,
                sourceRasterOriginLocalI32, ownerOriginDeviceI32, mapping)
        }
    }
}

private fun sameRect(first: RectI32, second: RectI32): Boolean =
    first.left == second.left && first.top == second.top && first.right == second.right && first.bottom == second.bottom

private fun sameSize(first: SizeI32, second: SizeI32): Boolean = first.width == second.width && first.height == second.height

private fun contains(outer: RectI32, inner: RectI32): Boolean =
    outer.left <= inner.left && outer.top <= inner.top && outer.right >= inner.right && outer.bottom >= inner.bottom

private fun samePoint(first: Point2I32, second: Point2I32): Boolean = first.x == second.x && first.y == second.y

private fun sameMapping(first: LayerMappingF64?, second: LayerMappingF64?): Boolean = when {
    first == null || second == null -> first == null && second == null
    else -> samePoint(first.copyLayerOriginDeviceI32(), second.copyLayerOriginDeviceI32()) &&
        sameMatrix(first.copyLocalToDeviceF64(), second.copyLocalToDeviceF64()) &&
        sameMatrix(first.copyDeviceToLayerF64(), second.copyDeviceToLayerF64()) &&
        sameMatrix(first.copyLocalToLayerF64(), second.copyLocalToLayerF64())
}

private fun sameMatrix(first: Matrix3x3F64, second: Matrix3x3F64): Boolean =
    first.sxF64 == second.sxF64 && first.kxF64 == second.kxF64 && first.txF64 == second.txF64 &&
        first.kyF64 == second.kyF64 && first.syF64 == second.syF64 && first.tyF64 == second.tyF64 &&
        first.persp0F64 == second.persp0F64 && first.persp1F64 == second.persp1F64 && first.persp2F64 == second.persp2F64
