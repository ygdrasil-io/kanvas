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

    /** FP-13 Task 8 continued producer half: the fan is stored in its own render pass. */
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

    /** FP-13 Task 8 continued cover half: the fan is read-only and the snapshot is bound. */
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
        fun pack(routes: GPUCorePrimitiveNativeScopeRouteSeal.Routes): GPUCorePrimitiveNativeScopeGeometryArena {
            val geometryCount = routes.orderedUnits.sumOf { unit ->
                when (unit) {
                    is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> 1
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> 2
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer -> 1
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathCover -> 1
                }
            }
            var totalVertexCount = 0
            var totalIndexCount = 0
            routes.orderedUnits.forEach { unit ->
                when (unit) {
                    is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> {
                        totalVertexCount = Math.addExact(totalVertexCount, unit.route.vertexCount)
                        totalIndexCount = Math.addExact(totalIndexCount, unit.route.indexCount)
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> {
                        totalVertexCount = Math.addExact(totalVertexCount, unit.pair.producer.vertexCount)
                        totalVertexCount = Math.addExact(totalVertexCount, unit.pair.cover.vertexCount)
                        totalIndexCount = Math.addExact(totalIndexCount, unit.pair.producer.indexCount)
                        totalIndexCount = Math.addExact(totalIndexCount, unit.pair.cover.indexCount)
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer -> {
                        totalVertexCount = Math.addExact(totalVertexCount, unit.geometry.vertexCount)
                        totalIndexCount = Math.addExact(totalIndexCount, unit.geometry.indexCount)
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathCover -> {
                        totalVertexCount = Math.addExact(totalVertexCount, unit.geometry.vertexCount)
                        totalIndexCount = Math.addExact(totalIndexCount, unit.geometry.indexCount)
                    }
                }
            }
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

            routes.orderedUnits.forEach { unit ->
                when (unit) {
                    is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> append(
                        unit.packetId,
                        GPUCorePrimitiveNativeScopeArenaRole.Direct,
                        unit.route.vertexCount,
                        unit.route.indexCount,
                        unit.route.maxLocalIndex,
                        unit.route::copyVerticesInto,
                        unit.route::copyIndicesInto,
                    )
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> {
                        append(
                            unit.pair.producerPacketId,
                            GPUCorePrimitiveNativeScopeArenaRole.PathProducer,
                            unit.pair.producer.vertexCount,
                            unit.pair.producer.indexCount,
                            unit.pair.producer.maxLocalIndex,
                            unit.pair.producer::copyVerticesInto,
                            unit.pair.producer::copyIndicesInto,
                        )
                        append(
                            unit.pair.coverPacketId,
                            GPUCorePrimitiveNativeScopeArenaRole.PathCover,
                            unit.pair.cover.vertexCount,
                            unit.pair.cover.indexCount,
                            unit.pair.cover.maxLocalIndex,
                            unit.pair.cover::copyVerticesInto,
                            unit.pair.cover::copyIndicesInto,
                        )
                    }
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer -> append(
                        unit.packetId,
                        GPUCorePrimitiveNativeScopeArenaRole.PathProducer,
                        unit.geometry.vertexCount,
                        unit.geometry.indexCount,
                        unit.geometry.maxLocalIndex,
                        unit.geometry::copyVerticesInto,
                        unit.geometry::copyIndicesInto,
                    )
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathCover -> append(
                        unit.packetId,
                        GPUCorePrimitiveNativeScopeArenaRole.PathCover,
                        unit.geometry.vertexCount,
                        unit.geometry.indexCount,
                        unit.geometry.maxLocalIndex,
                        unit.geometry::copyVerticesInto,
                        unit.geometry::copyIndicesInto,
                    )
                }
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
