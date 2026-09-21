package org.graphiks.math.matrix

import org.graphiks.math.geometry.*

/** Exact rebasing of already prepared clip geometry; it does not classify a clip again. */
public fun LayerMappingF64.mapDeviceClipToLayerF32OrNull(geometryF32: ClipGeometryF32): ClipGeometryF32? =
    when (geometryF32) {
        is ClipGeometryF32.Rect -> mapDeviceRectToLayerF32OrNull(geometryF32.copyRectF32())?.let(ClipGeometryF32::Rect)
        is ClipGeometryF32.RRect -> mapDeviceRRectToLayerF32OrNull(geometryF32.copyRRectF32())?.let(ClipGeometryF32::RRect)
        is ClipGeometryF32.Path -> geometryF32.copyPathGeometryF32().relativeToOriginI32OrNull(copyLayerOriginDeviceI32())?.let(ClipGeometryF32::Path)
        ClipGeometryF32.Empty -> ClipGeometryF32.Empty
    }

public fun LayerMappingF64.mapDeviceDomainToLayerI32OrNull(domainI32: RectI32, targetDomainI32: RectI32): RectI32? {
    val clippedI32 = RectI32(maxOf(domainI32.left, targetDomainI32.left), maxOf(domainI32.top, targetDomainI32.top),
        minOf(domainI32.right, targetDomainI32.right), minOf(domainI32.bottom, targetDomainI32.bottom))
    return if (clippedI32.isEmpty) null else mapDeviceRectToLayerI32OrNull(clippedI32)
}

public fun LayerMappingF64.mapDeviceInverseToLayerF32OrNull(geometryF32: InversePathGeometryF32,
    targetDomainI32: RectI32 = geometryF32.copyDomainI32()): InversePathGeometryF32? {
    val domainI32 = mapDeviceDomainToLayerI32OrNull(geometryF32.copyDomainI32(), targetDomainI32) ?: return null
    val interior = when (val coverage = geometryF32.interiorCoverageF32) {
        InverseInteriorCoverageF32.Zero -> InverseInteriorCoverageF32.Zero
        is InverseInteriorCoverageF32.Geometry -> InverseInteriorCoverageF32.Geometry.of(
            coverage.copyGeometryF32().relativeToOriginI32OrNull(copyLayerOriginDeviceI32()) ?: return null)
    }
    return InversePathGeometryF32.of(interior, domainI32)
}
