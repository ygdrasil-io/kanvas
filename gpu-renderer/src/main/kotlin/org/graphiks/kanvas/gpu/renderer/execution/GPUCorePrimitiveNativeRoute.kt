package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionUniformSeal

/**
 * Execution-only result of the sole direct-route classification pass.
 *
 * [Missing] means the render scope was not sealed and is therefore corrupt. [Empty] is a valid
 * pure-preflight result for a render scope that owns no direct CorePrimitive packets. [Routes]
 * retains immutable geometry snapshots keyed by the exact packet identities of a direct scope.
 */
internal sealed interface GPUCorePrimitiveDirectNativeRouteSeal {
    data object Missing : GPUCorePrimitiveDirectNativeRouteSeal
    data object Empty : GPUCorePrimitiveDirectNativeRouteSeal

    class Routes private constructor(
        routesByPacketId: Map<GPUDrawPacketID, GPUCorePrimitiveDirectNativeRoute.Accepted>,
        val preparedPassSeal: GPUCorePrimitiveDirectPreparedPassSeal?,
    ) : GPUCorePrimitiveDirectNativeRouteSeal {
        val routesByPacketId: Map<GPUDrawPacketID, GPUCorePrimitiveDirectNativeRoute.Accepted> =
            immutableMap(routesByPacketId)

        fun retainedFor(packetIds: List<GPUDrawPacketID>): GPUCorePrimitiveDirectNativeRouteSeal {
            val retainedCount = packetIds.count(routesByPacketId::containsKey)
            return when {
                retainedCount == 0 -> Empty
                packetIds == routesByPacketId.keys.toList() -> this
                else -> Missing
            }
        }

        companion object {
            fun snapshot(
                routesByPacketId: Map<GPUDrawPacketID, GPUCorePrimitiveDirectNativeRoute.Accepted>,
            ): GPUCorePrimitiveDirectNativeRouteSeal = if (routesByPacketId.isEmpty()) {
                Empty
            } else {
                Routes(routesByPacketId, null)
            }

            fun snapshot(
                routesByPacketId: Map<GPUDrawPacketID, GPUCorePrimitiveDirectNativeRoute.Accepted>,
                preparedPassSeal: GPUCorePrimitiveDirectPreparedPassSeal,
            ): GPUCorePrimitiveDirectNativeRouteSeal = if (routesByPacketId.isEmpty()) {
                Empty
            } else {
                Routes(routesByPacketId, preparedPassSeal)
            }
        }
    }
}

/** Builder authority proven structurally by pure preflight and retained for native materialization. */
internal class GPUCorePrimitiveDirectPreparedPassSeal private constructor(
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
    analyticShapeUniformSeals: List<GPUCorePrimitiveAnalyticShapeUniformSeal>,
    ownedAnalyticShapePackedBytes: ByteArray?,
    analyticClipUniformSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal>,
    ownedAnalyticClipPackedBytes: ByteArray?,
    analyticIntersectionUniformSeals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal>,
    ownedAnalyticIntersectionPackedBytes: ByteArray?,
) {
    private val analyticShapeUniformSealsSnapshot = immutableList(analyticShapeUniformSeals)
    private val analyticShapePackedBytesSnapshot = ownedAnalyticShapePackedBytes
    private val analyticClipUniformSealsSnapshot = immutableList(analyticClipUniformSeals)
    private val analyticClipPackedBytesSnapshot = ownedAnalyticClipPackedBytes
    private val analyticIntersectionUniformSealsSnapshot = immutableList(analyticIntersectionUniformSeals)
    private val analyticIntersectionPackedBytesSnapshot = ownedAnalyticIntersectionPackedBytes

    /** Caller-owned uniform64/uniform160 bytes keep their defensive-copy API boundary. */
    constructor(
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
        analyticClipUniformSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal> = emptyList(),
        analyticClipPackedBytes: ByteArray? = null,
        analyticIntersectionUniformSeals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal> = emptyList(),
        analyticIntersectionPackedBytes: ByteArray? = null,
    ) : this(
        structuralPipelineKey = structuralPipelineKey,
        uniformSlabSeal = uniformSlabSeal,
        analyticShapeUniformSeals = emptyList(),
        ownedAnalyticShapePackedBytes = null,
        analyticClipUniformSeals = analyticClipUniformSeals,
        ownedAnalyticClipPackedBytes = analyticClipPackedBytes?.copyOf(),
        analyticIntersectionUniformSeals = analyticIntersectionUniformSeals,
        ownedAnalyticIntersectionPackedBytes = analyticIntersectionPackedBytes?.copyOf(),
    )

    val analyticShapeUniformSeals: List<GPUCorePrimitiveAnalyticShapeUniformSeal>
        get() = analyticShapeUniformSealsSnapshot
    val analyticClipUniformSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal>
        get() = analyticClipUniformSealsSnapshot
    val analyticIntersectionUniformSeals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal>
        get() = analyticIntersectionUniformSealsSnapshot

    init {
        require(listOf(
            uniformSlabSeal != null,
            analyticShapeUniformSealsSnapshot.isNotEmpty(),
            analyticClipUniformSealsSnapshot.isNotEmpty(),
            analyticIntersectionUniformSealsSnapshot.isNotEmpty(),
        ).count { it } == 1) {
            "A direct CorePrimitive pass must retain exactly one uniform32, uniform64, uniform80, or uniform160 authority"
        }
        require((analyticShapePackedBytesSnapshot != null) == analyticShapeUniformSealsSnapshot.isNotEmpty()) {
            "An analytic-shape direct CorePrimitive pass must retain its exact packed uniform80 slab"
        }
        analyticShapePackedBytesSnapshot?.let { packed ->
            val plan = analyticShapeUniformSealsSnapshot.first().plan
            require(packed.size.toLong() == plan.totalBytes) {
                "The analytic-shape uniform80 packed slab must match its exact plan size"
            }
            require(analyticShapeUniformSealsSnapshot.size == plan.slots.size &&
                analyticShapeUniformSealsSnapshot.map { it.slotIndex } == plan.slots.indices.toList() &&
                analyticShapeUniformSealsSnapshot.all {
                    it.plan === plan && it.structuralPipelineKey == structuralPipelineKey
                }
            ) { "The analytic-shape uniform80 seals must retain one exact ordered pass plan" }
            analyticShapeUniformSealsSnapshot.forEach { seal ->
                val offset = seal.alignedOffset.toInt()
                require(seal.hasExactPayloadAt(packed, offset)) {
                    "The analytic-shape packed uniform80 slab must retain every sealed payload"
                }
            }
        }
        require((analyticClipPackedBytesSnapshot != null) == analyticClipUniformSealsSnapshot.isNotEmpty()) {
            "An analytic direct CorePrimitive pass must retain its exact packed uniform64 slab"
        }
        analyticClipPackedBytesSnapshot?.let { packed ->
            require(packed.size.toLong() == analyticClipUniformSealsSnapshot.first().plan.totalBytes) {
                "The analytic uniform64 packed slab must match its exact plan size"
            }
        }
        require((analyticIntersectionPackedBytesSnapshot != null) ==
            analyticIntersectionUniformSealsSnapshot.isNotEmpty()
        ) { "An analytic-intersection direct pass must retain its exact packed uniform160 slab" }
        analyticIntersectionPackedBytesSnapshot?.let { packed ->
            require(packed.size.toLong() == analyticIntersectionUniformSealsSnapshot.first().plan.totalBytes) {
                "The analytic-intersection uniform160 packed slab must match its exact plan size"
            }
        }
    }

    /** Internal zero-copy borrow valid only for the immediate queue upload. */
    fun packedUniformBytesForUpload(): ByteArray =
        uniformSlabSeal?.packedBytesForUpload()
            ?: analyticShapePackedBytesSnapshot
            ?: analyticClipPackedBytesSnapshot
            ?: requireNotNull(analyticIntersectionPackedBytesSnapshot)

    companion object {
        /**
         * Creates the sole packed uniform80 snapshot directly from immutable packet seals.
         *
         * No caller-owned full slab crosses this boundary, so the returned pass owns the only
         * packed upload buffer and materialization may borrow it only for immediate validation and
         * queue upload.
         */
        fun analyticShape(
            structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
            analyticShapeUniformSeals: List<GPUCorePrimitiveAnalyticShapeUniformSeal>,
        ): GPUCorePrimitiveDirectPreparedPassSeal {
            val seals = immutableList(analyticShapeUniformSeals)
            require(seals.isNotEmpty()) {
                "An analytic-shape direct CorePrimitive pass requires at least one uniform80 seal"
            }
            val plan = seals.first().plan
            require(plan.totalBytes <= Int.MAX_VALUE.toLong() &&
                seals.size == plan.slots.size &&
                seals.map { seal -> seal.slotIndex } == plan.slots.indices.toList() &&
                seals.all { seal ->
                    seal.plan === plan && seal.structuralPipelineKey == structuralPipelineKey
                }
            ) { "The analytic-shape uniform80 seals must retain one host-addressable ordered pass plan" }
            val packed = ByteArray(plan.totalBytes.toInt())
            seals.forEach { seal ->
                seal.copyPayloadInto(packed, seal.alignedOffset.toInt())
            }
            return GPUCorePrimitiveDirectPreparedPassSeal(
                structuralPipelineKey = structuralPipelineKey,
                uniformSlabSeal = null,
                analyticShapeUniformSeals = seals,
                ownedAnalyticShapePackedBytes = packed,
                analyticClipUniformSeals = emptyList(),
                ownedAnalyticClipPackedBytes = null,
                analyticIntersectionUniformSeals = emptyList(),
                ownedAnalyticIntersectionPackedBytes = null,
            )
        }
    }
}

internal data class GPUCorePrimitiveDirectNativeFrameRouteKey(
    val sourceStepIndex: Int,
    val packetId: GPUDrawPacketID,
) {
    init {
        require(sourceStepIndex >= 0) { "CorePrimitive direct frame-route step index must be non-negative" }
    }
}

/** Frame-local composite seal; packet identities are only unique inside one render step. */
internal class GPUCorePrimitiveDirectNativeFrameRouteSeal(
    routesByFrameKey: Map<GPUCorePrimitiveDirectNativeFrameRouteKey, GPUCorePrimitiveDirectNativeRoute.Accepted>,
    preparedPassByStep: Map<Int, GPUCorePrimitiveDirectPreparedPassSeal> = emptyMap(),
) {
    private val routesByFrameKey = immutableMap(routesByFrameKey)
    private val preparedPassByStep = immutableMap(preparedPassByStep)

    fun routeOrNull(
        sourceStepIndex: Int,
        packetId: GPUDrawPacketID,
    ): GPUCorePrimitiveDirectNativeRoute.Accepted? =
        routesByFrameKey[GPUCorePrimitiveDirectNativeFrameRouteKey(sourceStepIndex, packetId)]

    fun retainedFor(
        sourceStepIndex: Int,
        packetIds: List<GPUDrawPacketID>,
    ): GPUCorePrimitiveDirectNativeRouteSeal {
        val routes = packetIds.mapNotNull { packetId ->
            routeOrNull(sourceStepIndex, packetId)?.let { packetId to it }
        }
        return when {
            routes.isEmpty() -> GPUCorePrimitiveDirectNativeRouteSeal.Empty
            routes.size == packetIds.size -> preparedPassByStep[sourceStepIndex]?.let { preparedPassSeal ->
                GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(routes.toMap(), preparedPassSeal)
            } ?: GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(routes.toMap())
            else -> GPUCorePrimitiveDirectNativeRouteSeal.Missing
        }
    }

    companion object {
        val Empty = GPUCorePrimitiveDirectNativeFrameRouteSeal(emptyMap())
    }
}

internal data class GPUCorePrimitiveFrameGeometrySlice(
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int,
    val vertexCount: Int,
    val maxLocalIndex: Int,
)

internal class GPUCorePrimitiveFrameGeometryArena(
    val vertices: FloatArray,
    val indices: IntArray,
    slices: List<GPUCorePrimitiveFrameGeometrySlice>,
) {
    val slices: List<GPUCorePrimitiveFrameGeometrySlice> = immutableList(slices)
}

/** Packs all direct draws into two frame-local slabs while keeping indices local to each draw. */
internal fun packCorePrimitiveFrameGeometry(
    geometries: List<GPUCorePrimitiveDirectNativeRoute.Accepted>,
): GPUCorePrimitiveFrameGeometryArena {
    require(geometries.isNotEmpty()) { "A CorePrimitive geometry arena requires at least one draw" }
    var totalVertexCount = 0
    var totalIndexCount = 0
    geometries.forEach { geometry ->
        totalVertexCount = Math.addExact(totalVertexCount, geometry.vertexCount)
        totalIndexCount = Math.addExact(totalIndexCount, geometry.indexCount)
    }
    val totalVertexFloatCount = Math.multiplyExact(totalVertexCount, 2)
    val vertices = FloatArray(totalVertexFloatCount)
    val indices = IntArray(totalIndexCount)
    var baseVertex = 0
    var firstIndex = 0
    val slices = buildList(geometries.size) {
        geometries.forEach { geometry ->
            val nextBaseVertex = Math.addExact(baseVertex, geometry.vertexCount)
            val nextFirstIndex = Math.addExact(firstIndex, geometry.indexCount)
            val maximumAddressedVertex = Math.addExact(baseVertex, geometry.maxLocalIndex)
            require(maximumAddressedVertex < totalVertexCount) {
                "Direct CorePrimitive baseVertex plus maxLocalIndex exceeds the frame vertex slab"
            }
            geometry.copyVerticesInto(vertices, Math.multiplyExact(baseVertex, 2))
            geometry.copyIndicesInto(indices, firstIndex)
            add(
                GPUCorePrimitiveFrameGeometrySlice(
                    firstIndex = firstIndex,
                    indexCount = geometry.indexCount,
                    baseVertex = baseVertex,
                    vertexCount = geometry.vertexCount,
                    maxLocalIndex = geometry.maxLocalIndex,
                ),
            )
            baseVertex = nextBaseVertex
            firstIndex = nextFirstIndex
        }
    }
    check(baseVertex == totalVertexCount && firstIndex == totalIndexCount) {
        "Direct CorePrimitive geometry sizing and copy passes diverged"
    }
    return GPUCorePrimitiveFrameGeometryArena(vertices, indices, slices)
}
