package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionUniformSeal
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveDirectClipAuthority

internal sealed interface GPUCorePrimitiveDirectNativeRoute {
    class Accepted(
        vertices: FloatArray,
        indices: IntArray,
    ) : GPUCorePrimitiveDirectNativeRoute {
        private val vertexSnapshot = vertices.copyOf()
        private val indexSnapshot = indices.copyOf()
        val vertexCount: Int
        val indexCount: Int = indexSnapshot.size
        val maxLocalIndex: Int

        init {
            require(vertexSnapshot.isNotEmpty() && vertexSnapshot.size % 2 == 0 &&
                vertexSnapshot.all(Float::isFinite)
            ) {
                "Direct CorePrimitive vertices must contain finite xy pairs"
            }
            vertexCount = vertexSnapshot.size / 2
            require(indexSnapshot.isNotEmpty()) {
                "Direct CorePrimitive geometry requires at least one local index"
            }
            var maximum = -1
            indexSnapshot.forEach { index ->
                require(index >= 0) { "Direct CorePrimitive local indices must be non-negative" }
                if (index > maximum) maximum = index
            }
            require(maximum < vertexCount) {
                "Direct CorePrimitive maximum local index must address its local vertices"
            }
            maxLocalIndex = maximum
        }

        fun copyVerticesInto(destination: FloatArray, destinationOffset: Int = 0) {
            require(destinationOffset >= 0 && destinationOffset <= destination.size - vertexSnapshot.size) {
                "Direct CorePrimitive vertex snapshot does not fit its destination"
            }
            vertexSnapshot.copyInto(destination, destinationOffset)
        }

        fun copyIndicesInto(destination: IntArray, destinationOffset: Int = 0) {
            require(destinationOffset >= 0 && destinationOffset <= destination.size - indexSnapshot.size) {
                "Direct CorePrimitive index snapshot does not fit its destination"
            }
            indexSnapshot.copyInto(destination, destinationOffset)
        }

        override fun equals(other: Any?): Boolean = other is Accepted &&
            vertexSnapshot.contentEquals(other.vertexSnapshot) && indexSnapshot.contentEquals(other.indexSnapshot)

        override fun hashCode(): Int = 31 * vertexSnapshot.contentHashCode() + indexSnapshot.contentHashCode()

        override fun toString(): String =
            "Accepted(vertexCount=$vertexCount, indexCount=$indexCount, maxLocalIndex=$maxLocalIndex)"
    }

    data class Refused(val code: String, val message: String) : GPUCorePrimitiveDirectNativeRoute
}

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
internal class GPUCorePrimitiveDirectPreparedPassSeal(
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
    analyticClipUniformSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal> = emptyList(),
    analyticClipPackedBytes: ByteArray? = null,
    analyticIntersectionUniformSeals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal> = emptyList(),
    analyticIntersectionPackedBytes: ByteArray? = null,
) {
    private val analyticClipUniformSealsSnapshot = immutableList(analyticClipUniformSeals)
    private val analyticClipPackedBytesSnapshot = analyticClipPackedBytes?.copyOf()
    private val analyticIntersectionUniformSealsSnapshot = immutableList(analyticIntersectionUniformSeals)
    private val analyticIntersectionPackedBytesSnapshot = analyticIntersectionPackedBytes?.copyOf()

    val analyticClipUniformSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal>
        get() = analyticClipUniformSealsSnapshot
    val analyticIntersectionUniformSeals: List<GPUCorePrimitiveAnalyticIntersectionUniformSeal>
        get() = analyticIntersectionUniformSealsSnapshot

    init {
        require(listOf(
            uniformSlabSeal != null,
            analyticClipUniformSealsSnapshot.isNotEmpty(),
            analyticIntersectionUniformSealsSnapshot.isNotEmpty(),
        ).count { it } == 1) {
            "A direct CorePrimitive pass must retain exactly one uniform32, uniform64, or uniform160 authority"
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
            ?: analyticClipPackedBytesSnapshot
            ?: requireNotNull(analyticIntersectionPackedBytesSnapshot)
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

internal fun validateCorePrimitiveDirectNativeRoute(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    clipAuthority: GPUCorePrimitiveDirectClipAuthority,
    blendPlan: GPUBlendPlan?,
    samplePlan: GPUSamplePlan,
    targetFormat: String,
): GPUCorePrimitiveDirectNativeRoute {
    fun refused(code: String, message: String) = GPUCorePrimitiveDirectNativeRoute.Refused(code, message)
    if (targetFormat != "rgba8unorm") {
        return refused(
            "unsupported.native-core-primitive.target-format",
            "Direct CorePrimitive native geometry requires rgba8unorm.",
        )
    }
    if (samplePlan != GPUSamplePlan.SingleSampleFrame) {
        return refused(
            "unsupported.native-core-primitive.sample-plan",
            "Direct CorePrimitive native geometry requires one sample.",
        )
    }
    if (!blendPlan.isCanonicalSolidRectSrcOver()) {
        return refused(
            "unsupported.native-core-primitive.blend",
            "Direct CorePrimitive native geometry requires canonical premultiplied SrcOver.",
        )
    }
    if (semantic.coverageMode != GPUCorePrimitiveCoverageMode.FullOrScissor) {
        return refused(
            "unsupported.native-core-primitive.coverage",
            "Scalar AA and stencil coverage are not promoted by the direct native slice.",
        )
    }
    if (clipAuthority !is GPUCorePrimitiveDirectClipAuthority.Accepted) {
        return refused(
            "unsupported.native-core-primitive.clip",
            "Direct CorePrimitive native geometry accepts only no clip or scissor clip.",
        )
    }
    val exactScissor = clipAuthority.scissor
    if (semantic.scissorBounds != exactScissor) {
        return refused(
            "invalid.native-core-primitive.scissor-authority",
            "The semantic scissor must exactly match the classified direct clip plan.",
        )
    }
    fun accepted(vertices: FloatArray, indices: IntArray): GPUCorePrimitiveDirectNativeRoute = try {
        GPUCorePrimitiveDirectNativeRoute.Accepted(vertices, indices)
    } catch (_: IllegalArgumentException) {
        refused(
            "invalid.native-core-primitive.geometry-indices",
            "Direct CorePrimitive geometry requires finite xy vertices and in-range local indices.",
        )
    }
    return when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect -> accepted(
            vertices = floatArrayOf(
                geometry.left,
                geometry.top,
                geometry.right,
                geometry.top,
                geometry.right,
                geometry.bottom,
                geometry.left,
                geometry.bottom,
            ),
            indices = intArrayOf(0, 2, 1, 0, 3, 2),
        )
        is GPUCorePrimitiveGeometry.RRect -> refused(
            "unsupported.native-core-primitive.geometry",
            "Analytic RRect geometry is not promoted by the direct native slice.",
        )
        is GPUCorePrimitiveGeometry.TriangulatedPath -> when {
            geometry.inverseFill -> refused(
                "unsupported.native-core-primitive.inverse-fill",
                "Inverse fill requires a later cover or mask route.",
            )
            geometry.geometryMode != GPUCorePrimitiveGeometryMode.DirectTriangles -> refused(
                "unsupported.native-core-primitive.geometry",
                "Stencil edge fans require the later stencil-cover route.",
            )
            geometry.strokeStyle != null -> refused(
                "unsupported.native-core-primitive.geometry",
                "Direct triangles may not retain stroke lowering state.",
            )
            else -> accepted(
                FloatArray(geometry.vertices.size) { index -> geometry.vertices[index] },
                IntArray(geometry.indices.size) { index -> geometry.indices[index] },
            )
        }
    }
}
