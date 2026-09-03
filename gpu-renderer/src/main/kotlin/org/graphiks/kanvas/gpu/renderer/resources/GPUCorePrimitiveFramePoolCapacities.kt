package org.graphiks.kanvas.gpu.renderer.resources

/**
 * Handle-free allocation capacities for the reusable CorePrimitive frame pool.
 *
 * The native pool rounds each scratch buffer independently, so capability checks that
 * authorize pooled scratch must use these values rather than only the useful payload bytes.
 */
internal data class GPUCorePrimitiveFramePoolCapacities(
    val vertexBytes: Long,
    val indexBytes: Long,
    val uniformBytes: Long,
) {
    init {
        require(vertexBytes > 0L && indexBytes > 0L && uniformBytes > 0L)
    }
}

internal const val CORE_PRIMITIVE_FRAME_POOL_VERTEX_FLOOR_BYTES: Long = 16L * 1024L
internal const val CORE_PRIMITIVE_FRAME_POOL_INDEX_FLOOR_BYTES: Long = 4L * 1024L
internal const val CORE_PRIMITIVE_FRAME_POOL_UNIFORM_FLOOR_BYTES: Long = 4L * 1024L

/** Returns the exact minimum capacities that the frame pool would allocate, or null on overflow. */
internal fun corePrimitiveFramePoolCapacitiesOrNull(
    vertexBytes: Long,
    indexBytes: Long,
    uniformBytes: Long,
): GPUCorePrimitiveFramePoolCapacities? {
    val vertex = roundedCorePrimitiveFramePoolCapacityOrNull(
        vertexBytes,
        CORE_PRIMITIVE_FRAME_POOL_VERTEX_FLOOR_BYTES,
    ) ?: return null
    val index = roundedCorePrimitiveFramePoolCapacityOrNull(
        indexBytes,
        CORE_PRIMITIVE_FRAME_POOL_INDEX_FLOOR_BYTES,
    ) ?: return null
    val uniform = roundedCorePrimitiveFramePoolCapacityOrNull(
        uniformBytes,
        CORE_PRIMITIVE_FRAME_POOL_UNIFORM_FLOOR_BYTES,
    ) ?: return null
    return GPUCorePrimitiveFramePoolCapacities(vertex, index, uniform)
}

/** Rounds a positive requested capacity up from its supplied power-of-two floor. */
internal fun roundedCorePrimitiveFramePoolCapacityOrNull(
    requestedBytes: Long,
    floorBytes: Long,
): Long? {
    if (requestedBytes <= 0L || floorBytes <= 0L) return null
    val required = maxOf(requestedBytes, floorBytes)
    var capacity = floorBytes
    while (capacity < required) {
        if (capacity > Long.MAX_VALUE / 2L) return null
        capacity *= 2L
    }
    return capacity
}
