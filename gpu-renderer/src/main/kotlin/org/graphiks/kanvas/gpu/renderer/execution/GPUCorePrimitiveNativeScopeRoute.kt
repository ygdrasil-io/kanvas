package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan

/** One original draw command in the exact native execution order retained by pure preflight. */
internal sealed interface GPUCorePrimitiveNativeScopeRouteUnit {
    val commandIdValue: Int
    val flattenedPacketIds: List<GPUDrawPacketID>

    class Direct(
        override val commandIdValue: Int,
        val packetId: GPUDrawPacketID,
        val route: GPUCorePrimitiveDirectNativeRoute.Accepted,
        val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    ) : GPUCorePrimitiveNativeScopeRouteUnit {
        override val flattenedPacketIds: List<GPUDrawPacketID> = listOf(packetId)

        init {
            require(commandIdValue >= 0) { "A unified direct route command identity must be non-negative" }
            require(structuralPipelineKey.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading) {
                "A unified direct route must retain a shading structural key"
            }
        }
    }

    class PathPair(
        override val commandIdValue: Int,
        val pair: GPUCorePrimitivePathStencilNativeRoute.AcceptedPair,
        val producerStructuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        val coverStructuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    ) : GPUCorePrimitiveNativeScopeRouteUnit {
        override val flattenedPacketIds: List<GPUDrawPacketID> = listOf(
            pair.producerPacketId,
            pair.coverPacketId,
        )

        init {
            require(commandIdValue >= 0) { "A unified path route command identity must be non-negative" }
            require(
                producerStructuralPipelineKey.role ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
            ) { "A unified path producer must retain a producer structural key" }
            require(
                coverStructuralPipelineKey.role ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
            ) { "A unified path cover must retain a cover structural key" }
        }
    }

    /** Continued producer half: the fan is stored in its own render pass. */
    class PathProducer(
        override val commandIdValue: Int,
        val packetId: GPUDrawPacketID,
        val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        val geometry: GPUCorePrimitivePathStencilGeometrySnapshot,
    ) : GPUCorePrimitiveNativeScopeRouteUnit {
        override val flattenedPacketIds: List<GPUDrawPacketID> = listOf(packetId)

        init {
            require(commandIdValue >= 0) { "A unified path producer command identity must be non-negative" }
            require(
                structuralPipelineKey.role ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
            ) { "A unified path producer must retain a producer structural key" }
        }
    }

    /** Continued cover half: the fan is read-only and the snapshot is bound. */
    class PathCover(
        override val commandIdValue: Int,
        val packetId: GPUDrawPacketID,
        val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        val geometry: GPUCorePrimitivePathStencilGeometrySnapshot,
    ) : GPUCorePrimitiveNativeScopeRouteUnit {
        override val flattenedPacketIds: List<GPUDrawPacketID> = listOf(packetId)

        init {
            require(commandIdValue >= 0) { "A unified path cover command identity must be non-negative" }
            require(
                structuralPipelineKey.role ==
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
            ) { "A unified path cover must retain a cover structural key" }
        }
    }
}

/** Primary render-scope seal. Direct/path seals are derived compatibility views of these units. */
internal sealed interface GPUCorePrimitiveNativeScopeUniformCoverage {
    data object ExactScope : GPUCorePrimitiveNativeScopeUniformCoverage

    data class ExactCommandRange(
        val startIndex: Int,
        val commandCount: Int,
    ) : GPUCorePrimitiveNativeScopeUniformCoverage {
        init {
            require(startIndex >= 0 && commandCount > 0) {
                "A mixed prepared-surface uniform range must be positive and bounded"
            }
        }
    }
}

/**
 * Closed, handle-free authority for the exact uniform slab shared by unified CorePrimitive runs.
 *
 * Indexed path routes own the canonical uniform32 slab directly. Direct routes retain the
 * already-validated prepared-pass authority because analytic shape/clip programs own uniform80,
 * uniform64, or uniform160 slabs rather than a [GPUCorePrimitiveUniformSlabSeal].
 */
internal sealed interface GPUCorePrimitiveNativeScopeUniformAuthority {
    val plan: GPUUniformSlabPlan
    val commandIds: List<Int>

    fun packedBytesForUpload(): ByteArray

    fun hasSameBackingAuthority(other: GPUCorePrimitiveNativeScopeUniformAuthority): Boolean

    class Uniform32Slab(
        val seal: GPUCorePrimitiveUniformSlabSeal,
    ) : GPUCorePrimitiveNativeScopeUniformAuthority {
        override val plan: GPUUniformSlabPlan
            get() = seal.plan
        override val commandIds: List<Int>
            get() = seal.commandIds

        override fun packedBytesForUpload(): ByteArray = seal.packedBytesForUpload()

        override fun hasSameBackingAuthority(
            other: GPUCorePrimitiveNativeScopeUniformAuthority,
        ): Boolean = other is Uniform32Slab && other.seal === seal
    }

    class DirectPreparedPass(
        val seal: GPUCorePrimitiveDirectPreparedPassAuthority,
    ) : GPUCorePrimitiveNativeScopeUniformAuthority {
        override val plan: GPUUniformSlabPlan
            get() = seal.uniformPlan
        override val commandIds: List<Int>
            get() = seal.commandIds

        override fun packedBytesForUpload(): ByteArray = seal.packedUniformBytesForUpload()

        override fun hasSameBackingAuthority(
            other: GPUCorePrimitiveNativeScopeUniformAuthority,
        ): Boolean = other is DirectPreparedPass && other.seal === seal
    }

    class PathPreparedPass(
        val seal: GPUCorePrimitivePathStencilPreparedPassSeal,
    ) : GPUCorePrimitiveNativeScopeUniformAuthority {
        override val plan: GPUUniformSlabPlan
            get() = seal.uniformPlan
        override val commandIds: List<Int>
            get() = seal.commandIds

        override fun packedBytesForUpload(): ByteArray = seal.packedUniformBytesForUpload()

        override fun hasSameBackingAuthority(
            other: GPUCorePrimitiveNativeScopeUniformAuthority,
        ): Boolean = other is PathPreparedPass && other.seal === seal
    }
}

internal sealed interface GPUCorePrimitiveNativeScopeRouteSeal {
    data object Missing : GPUCorePrimitiveNativeScopeRouteSeal
    data object Empty : GPUCorePrimitiveNativeScopeRouteSeal

    class Routes private constructor(
        orderedUnits: List<GPUCorePrimitiveNativeScopeRouteUnit>,
        val uniformAuthority: GPUCorePrimitiveNativeScopeUniformAuthority,
        val uniformCoverage: GPUCorePrimitiveNativeScopeUniformCoverage =
            GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope,
    ) : GPUCorePrimitiveNativeScopeRouteSeal {
        val orderedUnits: List<GPUCorePrimitiveNativeScopeRouteUnit> = immutableList(orderedUnits)
        val commandIds: List<Int> = immutableList(orderedUnits.map { it.commandIdValue })
        val flattenedPacketIds: List<GPUDrawPacketID> = immutableList(
            orderedUnits.flatMap(GPUCorePrimitiveNativeScopeRouteUnit::flattenedPacketIds),
        )
        val uniformPlan: GPUUniformSlabPlan
            get() = uniformAuthority.plan
        val uniformCommandIds: List<Int>
            get() = uniformAuthority.commandIds

        init {
            require(orderedUnits.isNotEmpty()) { "A unified native route seal must not be empty" }
            require(commandIds.distinct().size == commandIds.size) {
                "A unified native route seal requires one unique command per unit"
            }
            require(flattenedPacketIds.distinct().size == flattenedPacketIds.size) {
                "A unified native route seal cannot reuse packet identities"
            }
            require(
                when (uniformCoverage) {
                    GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope ->
                        commandIds == uniformAuthority.commandIds
                    is GPUCorePrimitiveNativeScopeUniformCoverage.ExactCommandRange -> {
                        val endIndex = uniformCoverage.startIndex +
                            uniformCoverage.commandCount
                        endIndex <= uniformAuthority.commandIds.size &&
                            uniformCoverage.commandCount == commandIds.size &&
                            uniformAuthority.commandIds.subList(
                                uniformCoverage.startIndex,
                                endIndex,
                            ) == commandIds
                    }
                },
            ) {
                "Unified native route commands must match their declared shared-uniform coverage"
            }
        }

        internal constructor(
            orderedUnits: List<GPUCorePrimitiveNativeScopeRouteUnit>,
            uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal,
            uniformCoverage: GPUCorePrimitiveNativeScopeUniformCoverage =
                GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope,
        ) : this(
            orderedUnits,
            GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab(uniformSlabSeal),
            uniformCoverage,
        )

        internal constructor(
            orderedUnits: List<GPUCorePrimitiveNativeScopeRouteUnit>,
            directPreparedPassSeal: GPUCorePrimitiveDirectPreparedPassAuthority,
            uniformCoverage: GPUCorePrimitiveNativeScopeUniformCoverage =
                GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope,
        ) : this(
            orderedUnits,
            GPUCorePrimitiveNativeScopeUniformAuthority.DirectPreparedPass(
                directPreparedPassSeal,
            ),
            uniformCoverage,
        )

        internal constructor(
            orderedUnits: List<GPUCorePrimitiveNativeScopeRouteUnit>,
            pathPreparedPassSeal: GPUCorePrimitivePathStencilPreparedPassSeal,
            uniformCoverage: GPUCorePrimitiveNativeScopeUniformCoverage =
                GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope,
        ) : this(
            orderedUnits,
            GPUCorePrimitiveNativeScopeUniformAuthority.PathPreparedPass(
                pathPreparedPassSeal,
            ),
            uniformCoverage,
        )

        internal fun withOrderedUnits(
            orderedUnits: List<GPUCorePrimitiveNativeScopeRouteUnit>,
        ): Routes = Routes(
            orderedUnits,
            uniformAuthority,
            GPUCorePrimitiveNativeScopeUniformCoverage.ExactScope,
        )

        internal fun hasSameUniformAuthority(other: Routes): Boolean =
            uniformAuthority.hasSameBackingAuthority(other.uniformAuthority)

        internal fun packedUniformBytesForUpload(): ByteArray =
            uniformAuthority.packedBytesForUpload()
    }
}

internal data class GPUCorePrimitiveNativeScopeFrameRouteKey(
    val sourceStepIndex: Int,
    val firstPacketId: GPUDrawPacketID,
) {
    init {
        require(sourceStepIndex >= 0) { "Unified native frame-route step index must be non-negative" }
    }
}

/** Frame-local owner that only releases a unified seal for an exact render packet sequence. */
internal class GPUCorePrimitiveNativeScopeFrameRouteSeal(
    routesByFrameKey: Map<GPUCorePrimitiveNativeScopeFrameRouteKey, GPUCorePrimitiveNativeScopeRouteSeal.Routes>,
) {
    private val routesByFrameKey = immutableMap(routesByFrameKey)


    /** Exact relocation of sealed lane ranges into the composite frame's step coordinates. */
    fun reindexed(indices: Map<Int, Int>): GPUCorePrimitiveNativeScopeFrameRouteSeal = GPUCorePrimitiveNativeScopeFrameRouteSeal(
        routesByFrameKey.mapKeys { (key, _) -> key.copy(sourceStepIndex = indices.getValue(key.sourceStepIndex)) },
    )

    fun appended(other: GPUCorePrimitiveNativeScopeFrameRouteSeal): GPUCorePrimitiveNativeScopeFrameRouteSeal {
        require(routesByFrameKey.keys.intersect(other.routesByFrameKey.keys).isEmpty())
        return GPUCorePrimitiveNativeScopeFrameRouteSeal(routesByFrameKey + other.routesByFrameKey)
    }
    init {
        routesByFrameKey.forEach { (key, route) ->
            require(key.firstPacketId == route.flattenedPacketIds.first()) {
                "Unified native frame-route key must name the route's first packet"
            }
        }
        require(routesByFrameKey.keys.map { it.sourceStepIndex }.distinct().size == routesByFrameKey.size) {
            "A unified native frame-route seal may retain only one route per render step"
        }
    }

    fun hasRouteForStep(sourceStepIndex: Int): Boolean =
        routesByFrameKey.keys.any { key -> key.sourceStepIndex == sourceStepIndex }

    fun retainedFor(
        sourceStepIndex: Int,
        packetIds: List<GPUDrawPacketID>,
    ): GPUCorePrimitiveNativeScopeRouteSeal {
        val route = routesByFrameKey.entries.singleOrNull { (key, _) ->
            key.sourceStepIndex == sourceStepIndex
        }?.value
        return when {
            route == null && packetIds.isEmpty() -> GPUCorePrimitiveNativeScopeRouteSeal.Empty
            route == null -> GPUCorePrimitiveNativeScopeRouteSeal.Missing
            route.flattenedPacketIds == packetIds -> route
            else -> GPUCorePrimitiveNativeScopeRouteSeal.Missing
        }
    }

    companion object {
        val Empty = GPUCorePrimitiveNativeScopeFrameRouteSeal(emptyMap())
    }
}

internal enum class GPUCorePrimitiveNativeScopeArenaRole {
    Direct,
    PathProducer,
    PathCover,
}

internal data class GPUCorePrimitiveNativeScopeGeometrySlice(
    val packetId: GPUDrawPacketID,
    val role: GPUCorePrimitiveNativeScopeArenaRole,
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int,
    val vertexCount: Int,
    val maxLocalIndex: Int,
)

/** Source-free input to the single arena packer, retained before material publication. */
internal sealed interface GPUCorePrimitiveGeometryPiece {
    val packetId: GPUDrawPacketID
    val role: GPUCorePrimitiveNativeScopeArenaRole
    val vertexCount: Int
    val indexCount: Int
    val maxLocalIndex: Int
    fun copyVerticesInto(destination: FloatArray, offset: Int)
    fun copyIndicesInto(destination: IntArray, offset: Int)

    class Direct(override val packetId: GPUDrawPacketID, val route: GPUCorePrimitiveDirectNativeRoute.Accepted) : GPUCorePrimitiveGeometryPiece {
        override val role = GPUCorePrimitiveNativeScopeArenaRole.Direct
        override val vertexCount get() = route.vertexCount
        override val indexCount get() = route.indexCount
        override val maxLocalIndex get() = route.maxLocalIndex
        override fun copyVerticesInto(destination: FloatArray, offset: Int) = route.copyVerticesInto(destination, offset)
        override fun copyIndicesInto(destination: IntArray, offset: Int) = route.copyIndicesInto(destination, offset)
    }
    class Path(override val packetId: GPUDrawPacketID, override val role: GPUCorePrimitiveNativeScopeArenaRole,
        val geometry: GPUCorePrimitivePathStencilGeometrySnapshot) : GPUCorePrimitiveGeometryPiece {
        init { require(role != GPUCorePrimitiveNativeScopeArenaRole.Direct) }
        override val vertexCount get() = geometry.vertexCount
        override val indexCount get() = geometry.indexCount
        override val maxLocalIndex get() = geometry.maxLocalIndex
        override fun copyVerticesInto(destination: FloatArray, offset: Int) = geometry.copyVerticesInto(destination, offset)
        override fun copyIndicesInto(destination: IntArray, offset: Int) = geometry.copyIndicesInto(destination, offset)
    }
}

internal fun GPUCorePrimitiveNativeScopeRouteSeal.Routes.geometryPieces(): List<GPUCorePrimitiveGeometryPiece> =
    orderedUnits.flatMap { unit -> when (unit) {
        is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> listOf(GPUCorePrimitiveGeometryPiece.Direct(unit.packetId, unit.route))
        is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> listOf(
            GPUCorePrimitiveGeometryPiece.Path(unit.pair.producerPacketId, GPUCorePrimitiveNativeScopeArenaRole.PathProducer, unit.pair.producer),
            GPUCorePrimitiveGeometryPiece.Path(unit.pair.coverPacketId, GPUCorePrimitiveNativeScopeArenaRole.PathCover, unit.pair.cover))
        is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer -> listOf(GPUCorePrimitiveGeometryPiece.Path(unit.packetId,
            GPUCorePrimitiveNativeScopeArenaRole.PathProducer, unit.geometry))
        is GPUCorePrimitiveNativeScopeRouteUnit.PathCover -> listOf(GPUCorePrimitiveGeometryPiece.Path(unit.packetId,
            GPUCorePrimitiveNativeScopeArenaRole.PathCover, unit.geometry))
    } }

internal class GPUCorePrimitiveNativeScopeGeometryArena private constructor(
    vertices: FloatArray,
    indices: IntArray,
    slices: List<GPUCorePrimitiveNativeScopeGeometrySlice>,
) {
    private val vertexSlab = vertices
    private val indexSlab = indices
    val slices: List<GPUCorePrimitiveNativeScopeGeometrySlice> = immutableList(slices)
    val vertexFloatCount: Int = vertexSlab.size
    val indexCount: Int = indexSlab.size
    val packedGeometryHash: String = java.security.MessageDigest.getInstance("SHA-256").run {
        val word = java.nio.ByteBuffer.allocate(Int.SIZE_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fun add(value: Int) { word.clear(); word.putInt(value); update(word.array()) }
        add(vertexSlab.size); vertexSlab.forEach { add(it.toRawBits()) }
        add(indexSlab.size); indexSlab.forEach(::add)
        slices.forEach { slice ->
            update(slice.packetId.value.toByteArray(Charsets.UTF_8)); add(slice.role.ordinal)
            add(slice.firstIndex); add(slice.indexCount); add(slice.baseVertex); add(slice.vertexCount); add(slice.maxLocalIndex)
        }
        digest().joinToString("") { "%02x".format(it) }
    }

    /** A compatibility view over these exact ranges; never reclassifies or repacks geometry. */
    fun directCompatibilityView(): GPUCorePrimitiveFrameGeometryArena {
        require(slices.all { it.role == GPUCorePrimitiveNativeScopeArenaRole.Direct })
        return GPUCorePrimitiveFrameGeometryArena(vertexSlab.copyOf(), indexSlab.copyOf(), slices.map {
            GPUCorePrimitiveFrameGeometrySlice(it.firstIndex, it.indexCount, it.baseVertex, it.vertexCount, it.maxLocalIndex) })
    }

    fun copyVerticesInto(destination: FloatArray, destinationOffset: Int = 0) {
        require(destinationOffset >= 0 && destinationOffset <= destination.size - vertexSlab.size) {
            "Unified native vertex slab does not fit its destination"
        }
        vertexSlab.copyInto(destination, destinationOffset)
    }

    fun copyIndicesInto(destination: IntArray, destinationOffset: Int = 0) {
        require(destinationOffset >= 0 && destinationOffset <= destination.size - indexSlab.size) {
            "Unified native index slab does not fit its destination"
        }
        indexSlab.copyInto(destination, destinationOffset)
    }

    companion object {
        /** Shared sizing walk for preflight and packing; never reads or allocates native handles. */
        fun countsI64(routes: GPUCorePrimitiveNativeScopeRouteSeal.Routes): Pair<Long, Long> = countsI64(routes.geometryPieces())

        fun countsI64(pieces: List<GPUCorePrimitiveGeometryPiece>): Pair<Long, Long> {
            var verticesI64 = 0L
            var indicesI64 = 0L
            fun add(vertices: Int, indices: Int) {
                verticesI64 = Math.addExact(verticesI64, vertices.toLong())
                indicesI64 = Math.addExact(indicesI64, indices.toLong())
            }
            pieces.forEach { add(it.vertexCount, it.indexCount) }
            return verticesI64 to indicesI64
        }
        fun pack(routes: GPUCorePrimitiveNativeScopeRouteSeal.Routes): GPUCorePrimitiveNativeScopeGeometryArena = pack(routes.geometryPieces())

        fun pack(pieces: List<GPUCorePrimitiveGeometryPiece>): GPUCorePrimitiveNativeScopeGeometryArena {
            require(pieces.map { it.packetId }.distinct().size == pieces.size)
            val geometryCount = pieces.size
            val counts = countsI64(pieces)
            val totalVertexCount = Math.toIntExact(counts.first)
            val totalIndexCount = Math.toIntExact(counts.second)
            val vertices = FloatArray(Math.multiplyExact(totalVertexCount, 2))
            val indices = IntArray(totalIndexCount)
            val slices = ArrayList<GPUCorePrimitiveNativeScopeGeometrySlice>(geometryCount)
            var baseVertex = 0
            var firstIndex = 0

            fun append(
                packetId: GPUDrawPacketID,
                role: GPUCorePrimitiveNativeScopeArenaRole,
                vertexCount: Int,
                indexCount: Int,
                maxLocalIndex: Int,
                copyVertices: (FloatArray, Int) -> Unit,
                copyIndices: (IntArray, Int) -> Unit,
            ) {
                require(Math.addExact(baseVertex, maxLocalIndex) < totalVertexCount) {
                    "Unified native geometry addresses outside the frame vertex slab"
                }
                copyVertices(vertices, Math.multiplyExact(baseVertex, 2))
                copyIndices(indices, firstIndex)
                slices += GPUCorePrimitiveNativeScopeGeometrySlice(
                    packetId,
                    role,
                    firstIndex,
                    indexCount,
                    baseVertex,
                    vertexCount,
                    maxLocalIndex,
                )
                baseVertex = Math.addExact(baseVertex, vertexCount)
                firstIndex = Math.addExact(firstIndex, indexCount)
            }

            pieces.forEach { piece ->
                append(piece.packetId, piece.role, piece.vertexCount, piece.indexCount,
                    piece.maxLocalIndex, piece::copyVerticesInto, piece::copyIndicesInto)
            }
            check(baseVertex == totalVertexCount && firstIndex == totalIndexCount) {
                "Unified native geometry sizing and copy passes diverged"
            }
            return GPUCorePrimitiveNativeScopeGeometryArena(vertices, indices, slices)
        }
    }
}

internal fun packCorePrimitiveNativeScopeGeometry(
    routes: GPUCorePrimitiveNativeScopeRouteSeal.Routes,
): GPUCorePrimitiveNativeScopeGeometryArena = GPUCorePrimitiveNativeScopeGeometryArena.pack(routes)
