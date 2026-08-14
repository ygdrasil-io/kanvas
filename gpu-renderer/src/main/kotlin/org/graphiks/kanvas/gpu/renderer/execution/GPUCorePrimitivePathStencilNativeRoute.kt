package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan

/** Pure path stencil classification retained between preflight and native materialization. */
internal sealed interface GPUCorePrimitivePathStencilNativeRoute {
    /**
     * One indivisible producer/cover pair.
     *
     * Both geometries are snapshotted here so later packet or input mutation cannot change the
     * native draw stream after pure preflight has accepted it.
     */
    class AcceptedPair(
        val producerPacketId: GPUDrawPacketID,
        val coverPacketId: GPUDrawPacketID,
        producerVertices: FloatArray,
        producerIndices: IntArray,
        coverBounds: GPUPixelBounds,
        targetBounds: GPUPixelBounds,
        val inverseFill: Boolean,
    ) : GPUCorePrimitivePathStencilNativeRoute {
        val producer = GPUCorePrimitivePathStencilGeometrySnapshot(
            producerVertices,
            producerIndices,
        )
        val cover: GPUCorePrimitivePathStencilGeometrySnapshot

        init {
            require(producerPacketId != coverPacketId) {
                "Path stencil producer and cover packets must have distinct identities"
            }
            require(!targetBounds.isEmpty) { "Path stencil target bounds must not be empty" }
            require(!coverBounds.isEmpty) { "Path stencil cover bounds must not be empty" }
            require(
                coverBounds.left >= targetBounds.left &&
                    coverBounds.top >= targetBounds.top &&
                    coverBounds.right <= targetBounds.right &&
                    coverBounds.bottom <= targetBounds.bottom
            ) {
                "Path stencil conservative cover bounds must stay inside the sealed target"
            }
            val resolvedCover = if (inverseFill) targetBounds else coverBounds
            cover = GPUCorePrimitivePathStencilGeometrySnapshot(
                vertices = floatArrayOf(
                    resolvedCover.left.toFloat(),
                    resolvedCover.top.toFloat(),
                    resolvedCover.right.toFloat(),
                    resolvedCover.top.toFloat(),
                    resolvedCover.right.toFloat(),
                    resolvedCover.bottom.toFloat(),
                    resolvedCover.left.toFloat(),
                    resolvedCover.bottom.toFloat(),
                ),
                indices = intArrayOf(0, 2, 1, 0, 3, 2),
            )
        }

        val pairKey: GPUCorePrimitivePathStencilRoutePairKey
            get() = GPUCorePrimitivePathStencilRoutePairKey(producerPacketId, coverPacketId)
    }

    data class Refused(val code: String, val message: String) : GPUCorePrimitivePathStencilNativeRoute {
        init {
            require(code.isNotBlank()) { "Path stencil refusal code must not be blank" }
            require(message.isNotBlank()) { "Path stencil refusal message must not be blank" }
        }
    }
}

/** Immutable primitive-array geometry owned by one accepted path pair. */
internal class GPUCorePrimitivePathStencilGeometrySnapshot(
    vertices: FloatArray,
    indices: IntArray,
) {
    private val vertexSnapshot = vertices.copyOf()
    private val indexSnapshot = indices.copyOf()

    val vertexCount: Int
    val indexCount: Int = indexSnapshot.size
    val maxLocalIndex: Int

    init {
        require(
            vertexSnapshot.isNotEmpty() &&
                vertexSnapshot.size % 2 == 0 &&
                vertexSnapshot.all(Float::isFinite)
        ) {
            "Path stencil vertices must contain finite xy pairs"
        }
        vertexCount = vertexSnapshot.size / 2
        require(indexSnapshot.isNotEmpty()) {
            "Path stencil geometry requires at least one local index"
        }
        var maximum = -1
        indexSnapshot.forEach { index ->
            require(index >= 0) { "Path stencil local indices must be non-negative" }
            if (index > maximum) maximum = index
        }
        require(maximum < vertexCount) {
            "Path stencil maximum local index must address its local vertices"
        }
        maxLocalIndex = maximum
    }

    fun copyVerticesInto(destination: FloatArray, destinationOffset: Int = 0) {
        require(destinationOffset >= 0 && destinationOffset <= destination.size - vertexSnapshot.size) {
            "Path stencil vertex snapshot does not fit its destination"
        }
        vertexSnapshot.copyInto(destination, destinationOffset)
    }

    fun copyIndicesInto(destination: IntArray, destinationOffset: Int = 0) {
        require(destinationOffset >= 0 && destinationOffset <= destination.size - indexSnapshot.size) {
            "Path stencil index snapshot does not fit its destination"
        }
        indexSnapshot.copyInto(destination, destinationOffset)
    }

    override fun equals(other: Any?): Boolean = other is GPUCorePrimitivePathStencilGeometrySnapshot &&
        vertexSnapshot.contentEquals(other.vertexSnapshot) && indexSnapshot.contentEquals(other.indexSnapshot)

    override fun hashCode(): Int = 31 * vertexSnapshot.contentHashCode() + indexSnapshot.contentHashCode()
}

internal data class GPUCorePrimitivePathStencilRoutePairKey(
    val producerPacketId: GPUDrawPacketID,
    val coverPacketId: GPUDrawPacketID,
) {
    init {
        require(producerPacketId != coverPacketId) {
            "Path stencil route pair requires distinct producer and cover packet identities"
        }
    }
}

internal data class GPUCorePrimitivePathStencilNativeFrameRouteKey(
    val sourceStepIndex: Int,
    val producerPacketId: GPUDrawPacketID,
    val coverPacketId: GPUDrawPacketID,
) {
    init {
        require(sourceStepIndex >= 0) { "Path stencil frame-route step index must be non-negative" }
        require(producerPacketId != coverPacketId) {
            "Path stencil frame-route pair requires distinct packet identities"
        }
    }

    val pairKey: GPUCorePrimitivePathStencilRoutePairKey
        get() = GPUCorePrimitivePathStencilRoutePairKey(producerPacketId, coverPacketId)
}

/** Scope-local seal. Missing is corruption; Empty means that the scope owns no path pairs. */
internal sealed interface GPUCorePrimitivePathStencilNativeRouteSeal {
    data object Missing : GPUCorePrimitivePathStencilNativeRouteSeal
    data object Empty : GPUCorePrimitivePathStencilNativeRouteSeal

    class Pairs internal constructor(
        orderedPairs: List<GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>,
        val preparedPassSeal: GPUCorePrimitivePathStencilPreparedPassSeal? = null,
    ) :
        GPUCorePrimitivePathStencilNativeRouteSeal {
        val orderedPairs: List<GPUCorePrimitivePathStencilNativeRoute.AcceptedPair> =
            immutableList(orderedPairs)
        val flattenedPacketIds: List<GPUDrawPacketID> = immutableList(
            orderedPairs.flatMap { pair -> listOf(pair.producerPacketId, pair.coverPacketId) },
        )

        init {
            require(orderedPairs.isNotEmpty()) { "A path stencil pair seal must not be empty" }
            require(orderedPairs.map { it.pairKey }.distinct().size == orderedPairs.size) {
                "A path stencil pair seal cannot retain duplicate packet pairs"
            }
            requireUniquePathStencilPacketIds(
                orderedPairs,
                "A path stencil pair seal cannot reuse one packet identity across pairs or roles",
            )
        }
    }

    /**
     * FP-13 Task 8: one continued half of a producer/cover pair that spans two render scopes.
     * The scope owns exactly the producer fan or the cover fan, not the merged pair.
     */
    class Continued internal constructor(
        val pair: GPUCorePrimitivePathStencilNativeRoute.AcceptedPair,
        val producerHalf: Boolean,
        val preparedPassSeal: GPUCorePrimitivePathStencilPreparedPassSeal? = null,
    ) : GPUCorePrimitivePathStencilNativeRouteSeal {
        val packetId: GPUDrawPacketID = if (producerHalf) pair.producerPacketId else pair.coverPacketId
    }
}

/** Exact producer/cover builder authority retained without recomputing either structural key. */
internal data class GPUCorePrimitivePathStencilPreparedPairSeal(
    val commandIdValue: Int,
    val uniformSlotIndex: Int,
    val producerPacketId: GPUDrawPacketID,
    val coverPacketId: GPUDrawPacketID,
    val producerStructuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val coverStructuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
) {
    init {
        require(commandIdValue >= 0) { "A prepared path pair command identity must be non-negative" }
        require(uniformSlotIndex >= 0) { "A prepared path pair uniform slot index must be non-negative" }
        require(producerPacketId != coverPacketId) {
            "A prepared path pair requires distinct producer and cover packet identities"
        }
        require(
            producerStructuralPipelineKey.role ==
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
        ) { "A prepared path producer must retain a producer structural key" }
        require(
            coverStructuralPipelineKey.role ==
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
        ) { "A prepared path cover must retain a cover structural key" }
    }

    val pairKey: GPUCorePrimitivePathStencilRoutePairKey
        get() = GPUCorePrimitivePathStencilRoutePairKey(producerPacketId, coverPacketId)
}

/** Closed authority for the one uniform ABI shared by an indexed path pass. */
internal sealed interface GPUCorePrimitivePathStencilUniformAuthority {
    val plan: GPUUniformSlabPlan
    val commandIds: List<Int>

    fun packedBytesForUpload(): ByteArray

    class Uniform32(
        val seal: GPUCorePrimitiveUniformSlabSeal,
    ) : GPUCorePrimitivePathStencilUniformAuthority {
        override val plan: GPUUniformSlabPlan
            get() = seal.plan
        override val commandIds: List<Int>
            get() = seal.commandIds

        override fun packedBytesForUpload(): ByteArray = seal.packedBytesForUpload()
    }

    class AnalyticClip64(
        seals: List<GPUCorePrimitiveAnalyticClipUniformSeal>,
    ) : GPUCorePrimitivePathStencilUniformAuthority {
        val seals: List<GPUCorePrimitiveAnalyticClipUniformSeal> = immutableList(seals)
        override val plan: GPUUniformSlabPlan
        override val commandIds: List<Int>
        private val packedBytes: ByteArray

        init {
            require(this.seals.isNotEmpty()) {
                "An analytic path pass requires at least one existing analytic clip seal"
            }
            plan = this.seals.first().plan
            commandIds = immutableList(this.seals.map { seal -> seal.commandId })
            require(plan.totalBytes <= Int.MAX_VALUE.toLong() &&
                this.seals.size == plan.slots.size &&
                this.seals.map { seal -> seal.slotIndex } == plan.slots.indices.toList() &&
                this.seals.all { seal -> seal.plan === plan }
            ) {
                "Analytic path seals must retain one exact host-addressable ordered uniform64 plan"
            }
            packedBytes = ByteArray(plan.totalBytes.toInt())
            this.seals.forEach { seal ->
                seal.copyPayloadInto(packedBytes, seal.alignedOffset.toInt())
            }
        }

        override fun packedBytesForUpload(): ByteArray = packedBytes
    }
}

/** Builder-owned path authority shared by all producer/cover pairs in one prepared pass. */
internal class GPUCorePrimitivePathStencilPreparedPassSeal private constructor(
    orderedPairs: List<GPUCorePrimitivePathStencilPreparedPairSeal>,
    val uniformAuthority: GPUCorePrimitivePathStencilUniformAuthority,
) {
    val orderedPairs: List<GPUCorePrimitivePathStencilPreparedPairSeal> = immutableList(orderedPairs)
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?
        get() = (uniformAuthority as? GPUCorePrimitivePathStencilUniformAuthority.Uniform32)?.seal
    val uniformPlan: GPUUniformSlabPlan
        get() = uniformAuthority.plan
    val commandIds: List<Int>
        get() = uniformAuthority.commandIds

    init {
        require(orderedPairs.isNotEmpty()) { "A prepared path pass must retain at least one pair" }
        require(orderedPairs.map { it.pairKey }.distinct().size == orderedPairs.size) {
            "A prepared path pass cannot retain duplicate packet pairs"
        }
        require(orderedPairs.zipWithNext().all { (left, right) ->
            left.uniformSlotIndex < right.uniformSlotIndex
        }) {
            "Prepared path pair uniform slot indices must be unique and strictly increasing"
        }
        require(orderedPairs.all { pair ->
            uniformAuthority.commandIds.getOrNull(pair.uniformSlotIndex) == pair.commandIdValue
        }) {
            "Every prepared path pair must address its exact command in the shared uniform slab"
        }
    }

    constructor(
        orderedPairs: List<GPUCorePrimitivePathStencilPreparedPairSeal>,
        uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal,
    ) : this(
        orderedPairs,
        GPUCorePrimitivePathStencilUniformAuthority.Uniform32(uniformSlabSeal),
    )

    constructor(
        orderedPairs: List<GPUCorePrimitivePathStencilPreparedPairSeal>,
        analyticClipUniformSeals: List<GPUCorePrimitiveAnalyticClipUniformSeal>,
    ) : this(
        orderedPairs,
        GPUCorePrimitivePathStencilUniformAuthority.AnalyticClip64(analyticClipUniformSeals),
    )

    fun packedUniformBytesForUpload(): ByteArray = uniformAuthority.packedBytesForUpload()
}

/** Frame-local ordered pair seal; packet identities are scoped by their render step. */
internal class GPUCorePrimitivePathStencilNativeFrameRouteSeal(
    routesByFrameKey: Map<
        GPUCorePrimitivePathStencilNativeFrameRouteKey,
        GPUCorePrimitivePathStencilNativeRoute.AcceptedPair,
        >,
    preparedPassByStep: Map<Int, GPUCorePrimitivePathStencilPreparedPassSeal> = emptyMap(),
) {
    private val routesByFrameKey = immutableMap(routesByFrameKey)
    private val preparedPassByStep = immutableMap(preparedPassByStep)

    init {
        routesByFrameKey.forEach { (key, route) ->
            require(key.pairKey == route.pairKey) {
                "Path stencil frame-route key must match its accepted pair"
            }
        }
        routesByFrameKey.entries.groupBy { (key, _) -> key.sourceStepIndex }.forEach { (_, entries) ->
            requireUniquePathStencilPacketIds(
                entries.map { (_, route) -> route },
                "A path stencil frame-route step cannot reuse one packet identity across pairs or roles",
            )
        }
    }

    fun retainedFor(
        sourceStepIndex: Int,
        packetIds: List<GPUDrawPacketID>,
    ): GPUCorePrimitivePathStencilNativeRouteSeal {
        if (packetIds.isEmpty()) {
            return GPUCorePrimitivePathStencilNativeRouteSeal.Empty
        }
        val scopedPairs = routesByFrameKey.entries
            .filter { (key, _) -> key.sourceStepIndex == sourceStepIndex }
            .map { (_, route) -> route }
        val expectedPacketIds = scopedPairs.flatMap { pair ->
            listOf(pair.producerPacketId, pair.coverPacketId)
        }
        if (packetIds == expectedPacketIds) {
            return GPUCorePrimitivePathStencilNativeRouteSeal.Pairs(
                scopedPairs,
                preparedPassByStep[sourceStepIndex],
            )
        }
        // FP-13 Task 8: a continued producer/cover half lives in its own scope, so the half is
        // resolved by its packet identity across the whole frame seal.
        val continuedPair = routesByFrameKey.values.singleOrNull { candidate ->
            packetIds == listOf(candidate.producerPacketId) ||
                packetIds == listOf(candidate.coverPacketId)
        }
        if (continuedPair != null) {
            return if (packetIds == listOf(continuedPair.producerPacketId)) {
                GPUCorePrimitivePathStencilNativeRouteSeal.Continued(
                    continuedPair,
                    producerHalf = true,
                    preparedPassByStep[sourceStepIndex],
                )
            } else {
                GPUCorePrimitivePathStencilNativeRouteSeal.Continued(
                    continuedPair,
                    producerHalf = false,
                    preparedPassByStep[sourceStepIndex],
                )
            }
        }
        return GPUCorePrimitivePathStencilNativeRouteSeal.Missing
    }

    companion object {
        val Empty = GPUCorePrimitivePathStencilNativeFrameRouteSeal(emptyMap())
    }
}

internal enum class GPUCorePrimitivePathStencilArenaRole {
    Producer,
    Cover,
}

internal data class GPUCorePrimitivePathStencilFrameGeometrySlice(
    val pairKey: GPUCorePrimitivePathStencilRoutePairKey,
    val role: GPUCorePrimitivePathStencilArenaRole,
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int,
    val vertexCount: Int,
    val maxLocalIndex: Int,
)

internal class GPUCorePrimitivePathStencilFrameGeometryArena private constructor(
    vertices: FloatArray,
    indices: IntArray,
    slices: List<GPUCorePrimitivePathStencilFrameGeometrySlice>,
) {
    private val vertexSlab = vertices
    private val indexSlab = indices
    val slices: List<GPUCorePrimitivePathStencilFrameGeometrySlice> = immutableList(slices)
    val vertexFloatCount: Int = vertexSlab.size
    val indexCount: Int = indexSlab.size

    fun copyVerticesInto(destination: FloatArray, destinationOffset: Int = 0) {
        require(destinationOffset >= 0 && destinationOffset <= destination.size - vertexSlab.size) {
            "Path stencil frame vertex slab does not fit its destination"
        }
        vertexSlab.copyInto(destination, destinationOffset)
    }

    fun copyIndicesInto(destination: IntArray, destinationOffset: Int = 0) {
        require(destinationOffset >= 0 && destinationOffset <= destination.size - indexSlab.size) {
            "Path stencil frame index slab does not fit its destination"
        }
        indexSlab.copyInto(destination, destinationOffset)
    }

    companion object {
        internal fun pack(
            pairs: List<GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>,
        ): GPUCorePrimitivePathStencilFrameGeometryArena {
            require(pairs.isNotEmpty()) { "A path stencil geometry arena requires at least one pair" }
            require(pairs.map { it.pairKey }.distinct().size == pairs.size) {
                "A path stencil geometry arena cannot pack duplicate packet pairs"
            }
            requireUniquePathStencilPacketIds(
                pairs,
                "A path stencil geometry arena cannot reuse one packet identity across pairs or roles",
            )

            var totalVertexCount = 0
            var totalIndexCount = 0
            pairs.forEach { pair ->
                totalVertexCount = Math.addExact(totalVertexCount, pair.producer.vertexCount)
                totalVertexCount = Math.addExact(totalVertexCount, pair.cover.vertexCount)
                totalIndexCount = Math.addExact(totalIndexCount, pair.producer.indexCount)
                totalIndexCount = Math.addExact(totalIndexCount, pair.cover.indexCount)
            }
            val vertices = FloatArray(Math.multiplyExact(totalVertexCount, 2))
            val indices = IntArray(totalIndexCount)
            var baseVertex = 0
            var firstIndex = 0
            val slices = ArrayList<GPUCorePrimitivePathStencilFrameGeometrySlice>(
                Math.multiplyExact(pairs.size, 2),
            )

            fun append(
                pair: GPUCorePrimitivePathStencilNativeRoute.AcceptedPair,
                role: GPUCorePrimitivePathStencilArenaRole,
                geometry: GPUCorePrimitivePathStencilGeometrySnapshot,
            ) {
                val nextBaseVertex = Math.addExact(baseVertex, geometry.vertexCount)
                val nextFirstIndex = Math.addExact(firstIndex, geometry.indexCount)
                val maximumAddressedVertex = Math.addExact(baseVertex, geometry.maxLocalIndex)
                require(maximumAddressedVertex < totalVertexCount) {
                    "Path stencil baseVertex plus maxLocalIndex exceeds the frame vertex slab"
                }
                geometry.copyVerticesInto(vertices, Math.multiplyExact(baseVertex, 2))
                geometry.copyIndicesInto(indices, firstIndex)
                slices += GPUCorePrimitivePathStencilFrameGeometrySlice(
                    pairKey = pair.pairKey,
                    role = role,
                    firstIndex = firstIndex,
                    indexCount = geometry.indexCount,
                    baseVertex = baseVertex,
                    vertexCount = geometry.vertexCount,
                    maxLocalIndex = geometry.maxLocalIndex,
                )
                baseVertex = nextBaseVertex
                firstIndex = nextFirstIndex
            }

            pairs.forEach { pair ->
                append(pair, GPUCorePrimitivePathStencilArenaRole.Producer, pair.producer)
                append(pair, GPUCorePrimitivePathStencilArenaRole.Cover, pair.cover)
            }
            check(baseVertex == totalVertexCount && firstIndex == totalIndexCount) {
                "Path stencil geometry sizing and copy passes diverged"
            }
            return GPUCorePrimitivePathStencilFrameGeometryArena(vertices, indices, slices)
        }
    }
}

/** Packs pairwise producer/cover geometry into exactly one vertex and one index slab. */
internal fun packCorePrimitivePathStencilFrameGeometry(
    pairs: List<GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>,
): GPUCorePrimitivePathStencilFrameGeometryArena =
    GPUCorePrimitivePathStencilFrameGeometryArena.pack(pairs)

private fun requireUniquePathStencilPacketIds(
    pairs: Collection<GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>,
    message: String,
) {
    val packetIds = HashSet<GPUDrawPacketID>(Math.multiplyExact(pairs.size, 2))
    pairs.forEach { pair ->
        require(packetIds.add(pair.producerPacketId) && packetIds.add(pair.coverPacketId)) { message }
    }
}
