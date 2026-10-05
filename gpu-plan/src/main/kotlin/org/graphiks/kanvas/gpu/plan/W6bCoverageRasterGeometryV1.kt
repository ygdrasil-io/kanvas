package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.TriangleMeshF32
import org.graphiks.math.geometry.TriangleTopologyI32

/**
 * Immutable V/I/U windows for one already-triangulated W6b raw-coverage bundle.
 *
 * This is deliberately only a physical-source witness.  It contains no renderer selector,
 * uniform payload, or native-site catalog key; those remain the 2P7b boundary.
 */
public class W6bCoverageRasterBufferWindowV1 internal constructor(
    public val resourceId: PlanResourceId,
    public val offsetBytesI64: Long,
    public val usefulBytesI64: Long,
    public val capacityBytesI64: Long,
) {
    init {
        require(offsetBytesI64 >= 0L && usefulBytesI64 > 0L && capacityBytesI64 > 0L)
        require(Math.addExact(offsetBytesI64, usefulBytesI64) <= capacityBytesI64)
    }
}

/** Bundle role preserves the existing direct or producer/cover packet order without choosing a pipeline. */
public enum class W6bCoverageRasterBundleRoleV1 { Direct, StencilProducer, StencilCover }

/**
 * One final W6b bundle with a [TriangleTopologyI32.List] mesh.  The mesh copies the final
 * PlanDraw arrays exactly; in particular, a stencil edge fan is retained as its already-expanded
 * list rather than being reinterpreted as a triangle fan.
 */
public class W6bCoverageRasterBundleGeometryV1 internal constructor(
    public val bundleOrdinalI32: Int,
    public val role: W6bCoverageRasterBundleRoleV1,
    public val mesh: TriangleMeshF32,
    public val vertexWindow: W6bCoverageRasterBufferWindowV1,
    public val indexWindow: W6bCoverageRasterBufferWindowV1,
    public val uniformWindow: W6bCoverageRasterBufferWindowV1,
    public val vertexStrideBytesI32: Int,
    public val indexElementBytesI32: Int,
) {
    public val vertexCountI32: Int get() = mesh.vertexCountI32
    public val indexCountI32: Int get() = requireNotNull(mesh.indexCountI32)
    public val maxIndexI32: Int get() = requireNotNull(mesh.maxIndexI32)

    init {
        require(bundleOrdinalI32 >= 0)
        require(mesh.topologyI32 == TriangleTopologyI32.List && !mesh.fanExpanded)
        require(vertexStrideBytesI32 == 8 && indexElementBytesI32 == 4)
        require(vertexWindow.usefulBytesI64 == Math.multiplyExact(vertexCountI32.toLong(), vertexStrideBytesI32.toLong()))
        require(indexWindow.usefulBytesI64 == Math.multiplyExact(indexCountI32.toLong(), indexElementBytesI32.toLong()))
        require(maxIndexI32 in 0 until vertexCountI32)
        require(uniformWindow.usefulBytesI64 == 32L || uniformWindow.usefulBytesI64 == 80L)
        when (role) {
            W6bCoverageRasterBundleRoleV1.Direct -> require(bundleOrdinalI32 == 0)
            W6bCoverageRasterBundleRoleV1.StencilProducer -> require(bundleOrdinalI32 == 0)
            W6bCoverageRasterBundleRoleV1.StencilCover -> require(bundleOrdinalI32 == 1)
        }
    }

    internal fun matches(other: W6bCoverageRasterBundleGeometryV1): Boolean =
        bundleOrdinalI32 == other.bundleOrdinalI32 && role == other.role &&
            vertexWindow.matches(other.vertexWindow) && indexWindow.matches(other.indexWindow) &&
            uniformWindow.matches(other.uniformWindow) &&
            vertexStrideBytesI32 == other.vertexStrideBytesI32 && indexElementBytesI32 == other.indexElementBytesI32 &&
            mesh.topologyI32 == other.mesh.topologyI32 && mesh.fanExpanded == other.mesh.fanExpanded &&
            mesh.copyPositionsF32().contentEquals(other.mesh.copyPositionsF32()) &&
            mesh.copyIndicesI32().contentEquals(other.mesh.copyIndicesI32()) &&
            mesh.copyBoundsF32() == other.mesh.copyBoundsF32()
}

private fun W6bCoverageRasterBufferWindowV1.matches(other: W6bCoverageRasterBufferWindowV1): Boolean =
    resourceId == other.resourceId && offsetBytesI64 == other.offsetBytesI64 && usefulBytesI64 == other.usefulBytesI64 &&
        capacityBytesI64 == other.capacityBytesI64

/** All final physical geometry facts for one `FilterCoverageSourcePass.rasterBinding`, site ordinal zero. */
public class W6bCoverageRasterGeometryV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val siteOrdinalI32: Int,
    bundles: List<W6bCoverageRasterBundleGeometryV1>,
) {
    private val bundleSnapshots = java.util.Collections.unmodifiableList(bundles.toList())

    public fun bundles(): List<W6bCoverageRasterBundleGeometryV1> = bundleSnapshots
    public fun bundle(bundleOrdinalI32: Int): W6bCoverageRasterBundleGeometryV1 =
        bundleSnapshots.single { it.bundleOrdinalI32 == bundleOrdinalI32 }

    init {
        require(siteOrdinalI32 == 0)
        require(bundleSnapshots.map { it.bundleOrdinalI32 } == bundleSnapshots.indices.toList())
        require(bundleSnapshots.size in 1..2)
        require(if (bundleSnapshots.size == 1) bundleSnapshots.single().role == W6bCoverageRasterBundleRoleV1.Direct
        else bundleSnapshots.map { it.role } == listOf(W6bCoverageRasterBundleRoleV1.StencilProducer,
            W6bCoverageRasterBundleRoleV1.StencilCover))
    }

    internal fun matches(other: W6bCoverageRasterGeometryV1): Boolean =
        ownerPassId == other.ownerPassId && siteOrdinalI32 == other.siteOrdinalI32 &&
            bundleSnapshots.size == other.bundleSnapshots.size &&
            bundleSnapshots.zip(other.bundleSnapshots).all { (left, right) -> left.matches(right) }
}

/**
 * Derives the exact V/I/U windows from the final localized coverage draws and the final graph
 * rows.  This is the sole planner policy used both before publication and by physical-layout
 * sealing; it intentionally does not inspect renderer pipeline keys or templates.
 */
public fun freezeW6bCoverageRasterGeometryV1(
    passes: List<PlanPass>,
    resources: List<PlanResource>,
    capabilities: PlanCapabilitySnapshot,
): Map<PlanPassId, W6bCoverageRasterGeometryV1> {
    val rows = resources.associateBy(PlanResource::id)
    require(rows.size == resources.size) { "W6b coverage raster geometry requires unique final resource IDs." }
    val result = linkedMapOf<PlanPassId, W6bCoverageRasterGeometryV1>()
    passes.forEach { pass ->
        val coverage = pass as? PlanPass.FilterCoverageSourcePass ?: return@forEach
        val binding = coverage.rasterBinding ?: return@forEach
        // SolidRect uses its existing fullscreen coverage path and has no V/I/U bundle to freeze.
        if (binding.draw is SolidRectDraw) return@forEach
        val data = requireNotNull(binding.drawDataResources) {
            "Final W6b coverage raster geometry requires its planner-owned V/I/U resources."
        }
        require(rows.getValue(data.vertex).role == PlanResourceRole.VertexData &&
            PlanResourceUsage.Vertex in rows.getValue(data.vertex).usages())
        require(rows.getValue(data.index).role == PlanResourceRole.IndexData &&
            PlanResourceUsage.Index in rows.getValue(data.index).usages())
        require(rows.getValue(data.uniform).role == PlanResourceRole.UniformData &&
            PlanResourceUsage.Uniform in rows.getValue(data.uniform).usages())
        val uniformBytesI64 = when (binding.draw) {
            is AnalyticRectDraw, is AnalyticRRectDraw -> 80L
            is W5bPointDraw, is PathDraw -> 32L
            else -> error("Unadmitted W6b coverage raster draw after final binding.")
        }
        val direct = binding.depthStencil == null
        val bundles = if (direct) listOf(bundle(
            bundleOrdinalI32 = 0,
            role = W6bCoverageRasterBundleRoleV1.Direct,
            mesh = directMesh(binding.draw),
            data = data,
            vertexOffsetI64 = 0L,
            indexOffsetI64 = 0L,
            uniformOffsetI64 = 0L,
            uniformBytesI64 = uniformBytesI64,
            rows = rows,
        )) else {
            val draw = binding.draw as? PathDraw ?: error("Only a path may own W6b stencil coverage.")
            require(draw.strategy == PathFillStrategy.StencilCover)
            val producerMesh = stencilProducerMesh(draw)
            val coverUniformOffsetI64 = maxOf(32L, capabilities.minUniformBufferOffsetAlignment.toLong())
            require(coverUniformOffsetI64 % capabilities.minUniformBufferOffsetAlignment.toLong() == 0L)
            listOf(
                bundle(0, W6bCoverageRasterBundleRoleV1.StencilProducer, producerMesh, data, 0L, 0L, 0L,
                    uniformBytesI64, rows),
                bundle(1, W6bCoverageRasterBundleRoleV1.StencilCover, rectangleMesh(draw.copyScissorI32()), data,
                    Math.multiplyExact(producerMesh.vertexCountI32.toLong(), 8L),
                    Math.multiplyExact(requireNotNull(producerMesh.indexCountI32).toLong(), 4L),
                    coverUniformOffsetI64, uniformBytesI64, rows),
            )
        }
        val frozen = W6bCoverageRasterGeometryV1(coverage.id, 0, bundles)
        require(result.put(coverage.id, frozen) == null)
    }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(result))
}

private fun bundle(
    bundleOrdinalI32: Int,
    role: W6bCoverageRasterBundleRoleV1,
    mesh: TriangleMeshF32,
    data: PlanDrawDataResources,
    vertexOffsetI64: Long,
    indexOffsetI64: Long,
    uniformOffsetI64: Long,
    uniformBytesI64: Long,
    rows: Map<PlanResourceId, PlanResource>,
): W6bCoverageRasterBundleGeometryV1 = W6bCoverageRasterBundleGeometryV1(
    bundleOrdinalI32,
    role,
    mesh,
    window(data.vertex, vertexOffsetI64, Math.multiplyExact(mesh.vertexCountI32.toLong(), 8L), rows),
    window(data.index, indexOffsetI64, Math.multiplyExact(requireNotNull(mesh.indexCountI32).toLong(), 4L), rows),
    window(data.uniform, uniformOffsetI64, uniformBytesI64, rows),
    vertexStrideBytesI32 = 8,
    indexElementBytesI32 = 4,
)

private fun window(
    resourceId: PlanResourceId,
    offsetBytesI64: Long,
    usefulBytesI64: Long,
    rows: Map<PlanResourceId, PlanResource>,
): W6bCoverageRasterBufferWindowV1 {
    val row = requireNotNull(rows[resourceId]) { "W6b coverage raster window references an unpublished resource." }
    require(row.kind == PlanResourceKind.Buffer)
    return W6bCoverageRasterBufferWindowV1(resourceId, offsetBytesI64, usefulBytesI64, row.byteSize)
}

private fun directMesh(draw: PlanDraw): TriangleMeshF32 = when (draw) {
    is AnalyticRectDraw -> rectangleMesh(draw.copyRasterBounds())
    is AnalyticRRectDraw -> rectangleMesh(draw.copyRasterBounds())
    is W5bPointDraw -> listMesh(draw.copyVerticesF32(), draw.copyIndicesI32())
    is PathDraw -> {
        require(draw.strategy == PathFillStrategy.DirectTriangle)
        val fill = draw.copyPathGeometry().coverageFillGeometry()
        val triangles = requireNotNull(fill.copyDirectTriangleF32OrNull()) {
            "Final W6b direct path lost its already-triangulated List geometry."
        }
        listMesh(triangles.copyVerticesF32(), triangles.copyIndicesI32())
    }
    else -> error("Unadmitted direct W6b coverage geometry.")
}

private fun stencilProducerMesh(draw: PathDraw): TriangleMeshF32 {
    val edges = requireNotNull(draw.copyPathGeometry().coverageFillGeometry().copyStencilEdgeFanF32OrNull()) {
        "Final W6b stencil producer lost its already-triangulated List geometry."
    }
    return listMesh(edges.copyVerticesF32(), edges.copyIndicesI32())
}

private fun PathDrawGeometry.coverageFillGeometry() = when (this) {
    is PathDrawGeometry.Fill -> valueF32
    is PathDrawGeometry.Stroke -> valueF32.copyFillGeometryF32()
    is PathDrawGeometry.InverseDomainSource, PathDrawGeometry.Empty ->
        error("Unadmitted W6b coverage path geometry.")
}

private fun rectangleMesh(bounds: org.graphiks.math.geometry.RectI32): TriangleMeshF32 = listMesh(
    floatArrayOf(
        bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.top.toFloat(),
        bounds.right.toFloat(), bounds.bottom.toFloat(), bounds.left.toFloat(), bounds.bottom.toFloat(),
    ),
    intArrayOf(0, 2, 1, 0, 3, 2),
)

private fun listMesh(verticesF32: FloatArray, indicesI32: IntArray): TriangleMeshF32 = requireNotNull(
    TriangleMeshF32.ofOrNull(TriangleTopologyI32.List, verticesF32, null, indicesI32,
        maxVerticesI32 = verticesF32.size / 2, maxIndicesI32 = indicesI32.size),
) { "Final W6b coverage draw did not retain valid already-triangulated List geometry." }
