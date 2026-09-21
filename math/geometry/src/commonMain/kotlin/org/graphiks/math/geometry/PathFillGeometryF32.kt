package org.graphiks.math.geometry

/** Immutable indexed payload for the strictly-proven direct-triangle route. */
public class PathFillDirectTriangleF32 internal constructor(
    verticesF32: FloatArray,
    indicesI32: IntArray,
) {
    private val verticesSnapshotF32: FloatArray = verticesF32.copyOf()
    private val indicesSnapshotI32: IntArray = indicesI32.copyOf()

    init {
        require(verticesSnapshotF32.size == 6)
        require(indicesSnapshotI32.size == 3)
        require(verticesSnapshotF32.all(Float::isFinite))
    }

    public val vertexCountI32: Int get() = verticesSnapshotF32.size / 2

    public val indexCountI32: Int get() = indicesSnapshotI32.size

    public fun copyVerticesF32(): FloatArray = verticesSnapshotF32.copyOf()

    public fun copyIndicesI32(): IntArray = indicesSnapshotI32.copyOf()
}

/** Immutable expanded edge-fan payload for the stencil route. */
public class PathStencilEdgeFanF32 internal constructor(
    verticesF32: FloatArray,
    indicesI32: IntArray,
    contourStartsI32: IntArray,
) {
    private val verticesSnapshotF32: FloatArray = verticesF32.copyOf()
    private val indicesSnapshotI32: IntArray = indicesI32.copyOf()
    private val contourStartsSnapshotI32: IntArray = contourStartsI32.copyOf()

    init {
        require(verticesSnapshotF32.size % 6 == 0)
        require(indicesSnapshotI32.size == edgeCountI32 * 3)
        require(verticesSnapshotF32.all(Float::isFinite))
        require(contourStartsSnapshotI32.isNotEmpty())
        require(contourStartsSnapshotI32.first() == 0)
        require((1 until contourStartsSnapshotI32.size).all { indexI32 ->
            contourStartsSnapshotI32[indexI32 - 1] < contourStartsSnapshotI32[indexI32]
        })
        require(contourStartsSnapshotI32.last() in 0 until edgeCountI32)
    }

    public val edgeCountI32: Int get() = verticesSnapshotF32.size / 6

    public val vertexCountI32: Int get() = verticesSnapshotF32.size / 2

    public val indexCountI32: Int get() = indicesSnapshotI32.size

    public val contourCountI32: Int get() = contourStartsSnapshotI32.size

    public fun copyVerticesF32(): FloatArray = verticesSnapshotF32.copyOf()

    public fun copyIndicesI32(): IntArray = indicesSnapshotI32.copyOf()

    public fun copyContourStartsI32(): IntArray = contourStartsSnapshotI32.copyOf()
}

/** Immutable geometry authority emitted by W4c device-space preparation. */
public class PathFillGeometryF32 internal constructor(
    public val fillRule: FillRule,
    public val attemptedEdgeCountI32: Int,
    public val emittedNonZeroClosedEdgeCountI32: Int,
    conservativeScissorI32: RectI32,
    directTriangleF32: PathFillDirectTriangleF32?,
    stencilEdgeFanF32: PathStencilEdgeFanF32?,
) {
    private val conservativeScissorSnapshotI32 = copyRectI32(conservativeScissorI32)
    private val directTriangleSnapshotF32 = directTriangleF32?.copySnapshotF32()
    private val stencilEdgeFanSnapshotF32 = stencilEdgeFanF32?.copySnapshotF32()

    init {
        require(attemptedEdgeCountI32 >= 0)
        require(emittedNonZeroClosedEdgeCountI32 > 0)
        require((directTriangleSnapshotF32 == null) != (stencilEdgeFanSnapshotF32 == null))
    }

    public val vertexCostI64: Long
        get() = directTriangleSnapshotF32?.vertexCountI32?.toLong()
            ?: stencilEdgeFanSnapshotF32!!.edgeCountI32.toLong() * 3L + 4L

    public val indexCostI64: Long
        get() = directTriangleSnapshotF32?.indexCountI32?.toLong()
            ?: stencilEdgeFanSnapshotF32!!.edgeCountI32.toLong() * 3L + 6L

    /** Exact byte cost of the mutable numeric payload retained by this snapshot. */
    public val snapshotByteCostI64: Long
        get() {
            val arrayBytesI64 = directTriangleSnapshotF32?.let { triangleF32 ->
                checkedPathFillSnapshotAddI64(
                    checkedPathFillSnapshotMultiplyI64(triangleF32.vertexCountI32.toLong(), 8L),
                    checkedPathFillSnapshotMultiplyI64(triangleF32.indexCountI32.toLong(), 4L),
                )
            } ?: stencilEdgeFanSnapshotF32!!.let { fanF32 ->
                checkedPathFillSnapshotAddI64(
                    checkedPathFillSnapshotAddI64(
                        checkedPathFillSnapshotMultiplyI64(fanF32.edgeCountI32.toLong(), 24L),
                        checkedPathFillSnapshotMultiplyI64(fanF32.indexCountI32.toLong(), 4L),
                    ),
                    checkedPathFillSnapshotMultiplyI64(fanF32.contourCountI32.toLong(), 4L),
                )
            }
            return checkedPathFillSnapshotAddI64(arrayBytesI64, 16L)
        }

    public fun copyConservativeScissorI32(): RectI32 = copyRectI32(conservativeScissorSnapshotI32)

    public fun copyDirectTriangleF32OrNull(): PathFillDirectTriangleF32? = directTriangleSnapshotF32?.copySnapshotF32()

    public fun copyStencilEdgeFanF32OrNull(): PathStencilEdgeFanF32? = stencilEdgeFanSnapshotF32?.copySnapshotF32()

    /** Exact origin change of an admitted mesh; topology and work accounting are unchanged. */
    public fun relativeToOriginI32OrNull(originI32: Point2I32): PathFillGeometryF32? {
        fun vertices(inputF32: FloatArray): FloatArray? = FloatArray(inputF32.size) { index ->
            val valueF64 = inputF32[index].toDouble() -
                (if (index % 2 == 0) originI32.x else originI32.y).toDouble()
            val narrowedF32 = valueF64.toFloat()
            if (!narrowedF32.isFinite() || narrowedF32.toDouble() != valueF64) return null
            narrowedF32
        }
        fun edge(value: Int, origin: Int): Int? = (value.toLong() - origin.toLong())
            .takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        val old = conservativeScissorSnapshotI32
        val scissor = RectI32(edge(old.left, originI32.x) ?: return null,
            edge(old.top, originI32.y) ?: return null, edge(old.right, originI32.x) ?: return null,
            edge(old.bottom, originI32.y) ?: return null)
        val direct = directTriangleSnapshotF32?.let { PathFillDirectTriangleF32(
            vertices(it.copyVerticesF32()) ?: return null, it.copyIndicesI32()) }
        val fan = stencilEdgeFanSnapshotF32?.let { PathStencilEdgeFanF32(
            vertices(it.copyVerticesF32()) ?: return null, it.copyIndicesI32(), it.copyContourStartsI32()) }
        return PathFillGeometryF32(fillRule, attemptedEdgeCountI32, emittedNonZeroClosedEdgeCountI32,
            scissor, direct, fan)
    }

    /**
     * Rebases an already prepared device-space mesh with the F32 subtraction used by its target
     * coordinate system. Unlike [relativeToOriginI32OrNull], this retains a valid F32 mesh when
     * subtracting an integer layer origin changes the value's F64 representation.
     */
    public fun relativeToOriginI32F32OrNull(originI32: Point2I32): PathFillGeometryF32? {
        fun vertices(inputF32: FloatArray): FloatArray? = FloatArray(inputF32.size) { index ->
            val valueF32 = inputF32[index] - if (index % 2 == 0) originI32.x.toFloat() else originI32.y.toFloat()
            if (!valueF32.isFinite()) return null
            valueF32
        }
        fun edge(value: Int, origin: Int): Int? = (value.toLong() - origin.toLong())
            .takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        val old = conservativeScissorSnapshotI32
        val scissor = RectI32(edge(old.left, originI32.x) ?: return null,
            edge(old.top, originI32.y) ?: return null, edge(old.right, originI32.x) ?: return null,
            edge(old.bottom, originI32.y) ?: return null)
        val direct = directTriangleSnapshotF32?.let { PathFillDirectTriangleF32(
            vertices(it.copyVerticesF32()) ?: return null, it.copyIndicesI32()) }
        val fan = stencilEdgeFanSnapshotF32?.let { PathStencilEdgeFanF32(
            vertices(it.copyVerticesF32()) ?: return null, it.copyIndicesI32(), it.copyContourStartsI32()) }
        return PathFillGeometryF32(fillRule, attemptedEdgeCountI32, emittedNonZeroClosedEdgeCountI32,
            scissor, direct, fan)
    }
}

private fun PathFillDirectTriangleF32.copySnapshotF32(): PathFillDirectTriangleF32 = PathFillDirectTriangleF32(
    copyVerticesF32(),
    copyIndicesI32(),
)

private fun PathStencilEdgeFanF32.copySnapshotF32(): PathStencilEdgeFanF32 = PathStencilEdgeFanF32(
    copyVerticesF32(),
    copyIndicesI32(),
    copyContourStartsI32(),
)

private fun copyRectI32(source: RectI32): RectI32 = RectI32(source.left, source.top, source.right, source.bottom)

private fun checkedPathFillSnapshotAddI64(firstI64: Long, secondI64: Long): Long {
    check(firstI64 >= 0L && secondI64 >= 0L && firstI64 <= Long.MAX_VALUE - secondI64)
    return firstI64 + secondI64
}

private fun checkedPathFillSnapshotMultiplyI64(firstI64: Long, secondI64: Long): Long {
    check(firstI64 >= 0L && secondI64 >= 0L && (firstI64 == 0L || secondI64 <= Long.MAX_VALUE / firstI64))
    return firstI64 * secondI64
}
