package org.graphiks.kanvas.gpu.plan

/** One frozen physical cache request.  A hit and a miss reserve this exact amount. */
public class SpatialFilterCachePlanV1 internal constructor(
    public val outputResourceId: PlanResourceId,
    public val key: SpatialFilterCacheKeyV1,
    public val reservedBytesI64: Long,
) {
    init { require(reservedBytesI64 > 0L) }
}
