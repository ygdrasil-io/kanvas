package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef

/** Immutable execution projection independent from mutable packet payloads. */
internal class GPUCorePrimitiveClipStencilGeometrySnapshot(
    vertices: FloatArray,
    indices: IntArray,
) {
    private val vertices = vertices.copyOf()
    private val indices = indices.copyOf()
    val vertexCount: Int = this.vertices.size / 2
    val indexCount: Int = this.indices.size
    val maxLocalIndex: Int = this.indices.maxOrNull() ?: -1

    init {
        require(this.vertices.isNotEmpty() && this.vertices.size % 2 == 0 &&
            this.vertices.all(Float::isFinite) && this.indices.isNotEmpty() &&
            this.indices.all { it in 0 until vertexCount }
        ) { "Clip-stencil geometry snapshot requires exact finite indexed xy geometry" }
    }

    fun copyVerticesInto(destination: FloatArray, offset: Int) =
        vertices.copyInto(destination, offset)

    fun copyIndicesInto(destination: IntArray, offset: Int) =
        indices.copyInto(destination, offset)
}

private fun GPUCorePrimitiveClipStencilNativeRoute.ConsumerSeal.geometrySnapshot():
    GPUCorePrimitiveClipStencilGeometrySnapshot = when (val sealedGeometry = geometry) {
    is GPUCorePrimitiveGeometry.Rect -> GPUCorePrimitiveClipStencilGeometrySnapshot(
        floatArrayOf(
            sealedGeometry.left,
            sealedGeometry.top,
            sealedGeometry.right,
            sealedGeometry.top,
            sealedGeometry.right,
            sealedGeometry.bottom,
            sealedGeometry.left,
            sealedGeometry.bottom,
        ),
        intArrayOf(0, 2, 1, 0, 3, 2),
    )
    is GPUCorePrimitiveGeometry.TriangulatedPath ->
        GPUCorePrimitiveClipStencilGeometrySnapshot(
            sealedGeometry.vertices.toFloatArray(),
            sealedGeometry.indices.toIntArray(),
        )
    is GPUCorePrimitiveGeometry.RRect -> error("The pure route rejects RRect consumers")
}

internal data class GPUCorePrimitiveClipStencilPreparedFrameRouteKey(
    val producerSourceStepIndex: Int,
    val producerPacketId: GPUDrawPacketID,
    val contentKey: String,
)

internal enum class GPUCorePrimitiveClipStencilPreparedGeometryRole {
    Producer,
    Consumer,
}

internal data class GPUCorePrimitiveClipStencilPreparedGeometrySlice(
    val sourceStepIndex: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val role: GPUCorePrimitiveClipStencilPreparedGeometryRole,
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int,
    val vertexCount: Int,
    val maxLocalIndex: Int,
)

internal class GPUCorePrimitiveClipStencilPreparedGeometryArena private constructor(
    vertices: FloatArray,
    indices: IntArray,
    slices: List<GPUCorePrimitiveClipStencilPreparedGeometrySlice>,
) {
    private val vertices = vertices.copyOf()
    private val indices = indices.copyOf()
    val slices: List<GPUCorePrimitiveClipStencilPreparedGeometrySlice> = immutableList(slices)
    val vertexFloatCount: Int get() = vertices.size
    val indexCount: Int get() = indices.size

    fun copyVertices(): FloatArray = vertices.copyOf()
    fun copyIndices(): IntArray = indices.copyOf()

    companion object {
        internal fun pack(
            entries: List<GPUCorePrimitiveClipStencilPreparedGeometryEntry>,
        ): GPUCorePrimitiveClipStencilPreparedGeometryArena {
            require(entries.isNotEmpty()) { "Clip-stencil arena requires producer and consumer entries" }
            val vertexCount = entries.fold(0) { total, entry ->
                Math.addExact(total, entry.geometry.vertexCount)
            }
            val indexCount = entries.fold(0) { total, entry ->
                Math.addExact(total, entry.geometry.indexCount)
            }
            val vertices = FloatArray(Math.multiplyExact(vertexCount, 2))
            val indices = IntArray(indexCount)
            val slices = ArrayList<GPUCorePrimitiveClipStencilPreparedGeometrySlice>(entries.size)
            var baseVertex = 0
            var firstIndex = 0
            entries.forEach { entry ->
                entry.geometry.copyVerticesInto(vertices, Math.multiplyExact(baseVertex, 2))
                entry.geometry.copyIndicesInto(indices, firstIndex)
                slices += GPUCorePrimitiveClipStencilPreparedGeometrySlice(
                    sourceStepIndex = entry.sourceStepIndex,
                    packetId = entry.packetId,
                    commandId = entry.commandId,
                    role = entry.role,
                    firstIndex = firstIndex,
                    indexCount = entry.geometry.indexCount,
                    baseVertex = baseVertex,
                    vertexCount = entry.geometry.vertexCount,
                    maxLocalIndex = entry.geometry.maxLocalIndex,
                )
                baseVertex = Math.addExact(baseVertex, entry.geometry.vertexCount)
                firstIndex = Math.addExact(firstIndex, entry.geometry.indexCount)
            }
            return GPUCorePrimitiveClipStencilPreparedGeometryArena(vertices, indices, slices)
        }
    }
}

internal data class GPUCorePrimitiveClipStencilPreparedGeometryEntry(
    val sourceStepIndex: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val role: GPUCorePrimitiveClipStencilPreparedGeometryRole,
    val geometry: GPUCorePrimitiveClipStencilGeometrySnapshot,
)

internal class GPUCorePrimitiveClipStencilPreparedSlabAuthority(
    val vertexResource: GPUFrameBufferRef,
    val vertexGeneration: Long,
    val vertexByteSize: Long,
    val indexResource: GPUFrameBufferRef,
    val indexGeneration: Long,
    val indexByteSize: Long,
    val uniformResource: GPUFrameBufferRef,
    val uniformGeneration: Long,
    val uniformByteSize: Long,
    val uniformAlignmentBytes: Long,
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal,
) {
    init {
        require(setOf(vertexResource, indexResource, uniformResource).size == 3 &&
            vertexGeneration >= 0L && indexGeneration >= 0L && uniformGeneration >= 0L &&
            vertexByteSize > 0L && vertexByteSize % 4L == 0L &&
            indexByteSize > 0L && indexByteSize % 4L == 0L &&
            uniformByteSize == uniformSlabSeal.plan.totalBytes &&
            uniformAlignmentBytes == uniformSlabSeal.plan.alignmentBytes
        ) { "Prepared clip-stencil slabs require exact distinct handle-free authorities" }
    }
}

internal class GPUCorePrimitiveClipStencilPreparedAttachmentAuthority(
    val resource: GPUFrameTextureRef,
    val resourceGeneration: Long,
) {
    init {
        require(resourceGeneration >= 0L) {
            "Prepared clip-stencil attachment requires a typed generated resource"
        }
    }
}

internal data class GPUCorePrimitiveClipStencilPreparedUniformSlice(
    val resource: GPUFrameBufferRef,
    val resourceGeneration: Long,
    val commandId: Int,
    val alignedOffset: Long,
    val payloadBytes: Long,
    val allocatedBytes: Long,
) {
    init {
        require(resourceGeneration >= 0L && commandId >= 0 && alignedOffset >= 0L &&
            alignedOffset <= UInt.MAX_VALUE.toLong() && payloadBytes == 32L &&
            allocatedBytes >= payloadBytes
        ) { "Prepared clip-stencil uniform slice requires one exact dynamic-uniform32 slot" }
    }
}

internal sealed interface GPUCorePrimitiveClipStencilPreparedScopeRouteSeal {
    data object Missing : GPUCorePrimitiveClipStencilPreparedScopeRouteSeal
    data object Empty : GPUCorePrimitiveClipStencilPreparedScopeRouteSeal

    class Producer internal constructor(
        val sourceStepIndex: Int,
        val packetId: GPUDrawPacketID,
        val commandId: Int,
        val route: GPUCorePrimitiveClipStencilNativeRoute.Accepted,
        val geometrySlice: GPUCorePrimitiveClipStencilPreparedGeometrySlice,
        val slabAuthority: GPUCorePrimitiveClipStencilPreparedSlabAuthority,
        val attachmentAuthority: GPUCorePrimitiveClipStencilPreparedAttachmentAuthority,
    ) : GPUCorePrimitiveClipStencilPreparedScopeRouteSeal

    class Consumer internal constructor(
        val sourceStepIndex: Int,
        val packetId: GPUDrawPacketID,
        val commandId: Int,
        val sourceOrder: Int,
        val dependencyFromPreviousConsumerToken: String?,
        val isLastConsumer: Boolean,
        val route: GPUCorePrimitiveClipStencilNativeRoute.Accepted,
        val geometrySlice: GPUCorePrimitiveClipStencilPreparedGeometrySlice,
        val slabAuthority: GPUCorePrimitiveClipStencilPreparedSlabAuthority,
        val attachmentAuthority: GPUCorePrimitiveClipStencilPreparedAttachmentAuthority,
        val uniformSlice: GPUCorePrimitiveClipStencilPreparedUniformSlice,
    ) : GPUCorePrimitiveClipStencilPreparedScopeRouteSeal
}

internal data class GPUCorePrimitiveClipStencilPreparedConsumerLocation(
    val sourceStepIndex: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val sourceOrder: Int,
    val dependencyFromPreviousConsumerToken: String?,
)

internal sealed interface GPUCorePrimitiveClipStencilPreparedFrameRouteSeal {
    data object Empty : GPUCorePrimitiveClipStencilPreparedFrameRouteSeal

    class Route internal constructor(
        val key: GPUCorePrimitiveClipStencilPreparedFrameRouteKey,
        val route: GPUCorePrimitiveClipStencilNativeRoute.Accepted,
        val geometryArena: GPUCorePrimitiveClipStencilPreparedGeometryArena,
        val slabAuthority: GPUCorePrimitiveClipStencilPreparedSlabAuthority,
        val attachmentAuthority: GPUCorePrimitiveClipStencilPreparedAttachmentAuthority,
        viewsBySourceStepIndex: Map<Int, GPUCorePrimitiveClipStencilPreparedScopeRouteSeal>,
    ) : GPUCorePrimitiveClipStencilPreparedFrameRouteSeal {
        private val viewsBySourceStepIndex = immutableMap(viewsBySourceStepIndex)

        fun retainedFor(
            sourceStepIndex: Int,
            packetIds: List<GPUDrawPacketID>,
        ): GPUCorePrimitiveClipStencilPreparedScopeRouteSeal {
            val view = viewsBySourceStepIndex[sourceStepIndex]
                ?: return GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty
            val expectedPacketId = when (view) {
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer -> view.packetId
                is GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer -> view.packetId
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Empty,
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing,
                -> return GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing
            }
            return if (packetIds == listOf(expectedPacketId)) {
                view
            } else {
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Missing
            }
        }
    }
}

internal fun sealGPUCorePrimitiveClipStencilPreparedFrameRoute(
    route: GPUCorePrimitiveClipStencilNativeRoute.Accepted,
    producerFanVertices: List<Float>,
    producerFanIndices: List<Int>,
    slabAuthority: GPUCorePrimitiveClipStencilPreparedSlabAuthority,
    attachmentAuthority: GPUCorePrimitiveClipStencilPreparedAttachmentAuthority,
    producerSourceStepIndex: Int,
    producerPacketId: GPUDrawPacketID,
    producerCommandId: Int,
    consumers: List<GPUCorePrimitiveClipStencilPreparedConsumerLocation>,
): GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route {
    require(producerSourceStepIndex >= 0 && producerCommandId >= 0 && consumers.isNotEmpty()) {
        "Prepared clip-stencil frame route requires one producer before consumers"
    }
    require(consumers.size == route.consumers.size &&
        consumers.zip(route.consumers).all { (location, consumer) ->
            location.commandId == consumer.commandId && location.sourceOrder == consumer.sourceOrder &&
                location.sourceStepIndex > producerSourceStepIndex
        }
    ) { "Prepared clip-stencil consumer locations must exactly match the accepted route" }
    require(consumers.zipWithNext().all { (left, right) ->
        left.sourceStepIndex < right.sourceStepIndex
    } &&
        consumers.map { it.packetId }.distinct().size == consumers.size &&
        consumers.first().dependencyFromPreviousConsumerToken == null &&
        consumers.drop(1).all {
            !it.dependencyFromPreviousConsumerToken.isNullOrBlank()
        } &&
        consumers.mapNotNull { it.dependencyFromPreviousConsumerToken }.distinct().size ==
        consumers.size - 1
    ) { "Prepared clip-stencil consumers require strict frame order and one exact packet per render scope" }

    val entries = buildList {
        add(
            GPUCorePrimitiveClipStencilPreparedGeometryEntry(
                producerSourceStepIndex,
                producerPacketId,
                producerCommandId,
                GPUCorePrimitiveClipStencilPreparedGeometryRole.Producer,
                GPUCorePrimitiveClipStencilGeometrySnapshot(
                    producerFanVertices.toFloatArray(),
                    producerFanIndices.toIntArray(),
                ),
            ),
        )
        consumers.zip(route.consumers).forEach { (location, consumer) ->
            add(
                GPUCorePrimitiveClipStencilPreparedGeometryEntry(
                    location.sourceStepIndex,
                    location.packetId,
                    location.commandId,
                    GPUCorePrimitiveClipStencilPreparedGeometryRole.Consumer,
                    consumer.geometrySnapshot(),
                ),
            )
        }
    }
    val arena = GPUCorePrimitiveClipStencilPreparedGeometryArena.pack(entries)
    require(slabAuthority.vertexByteSize ==
        Math.multiplyExact(arena.vertexFloatCount.toLong(), 4L) &&
        slabAuthority.indexByteSize == Math.multiplyExact(arena.indexCount.toLong(), 4L) &&
        slabAuthority.uniformSlabSeal.commandIds == consumers.map { it.commandId } &&
        attachmentAuthority.resource.value == route.attachment.logicalReference &&
        attachmentAuthority.resourceGeneration == route.attachment.resourceGeneration
    ) { "Prepared clip-stencil slab sizes and uniform commands must match the exact arena" }
    val views = linkedMapOf<Int, GPUCorePrimitiveClipStencilPreparedScopeRouteSeal>()
    val producerSlice = arena.slices.first()
    views[producerSourceStepIndex] = GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer(
        producerSourceStepIndex,
        producerPacketId,
        producerCommandId,
        route,
        producerSlice,
        slabAuthority,
        attachmentAuthority,
    )
    consumers.forEachIndexed { index, consumer ->
        views[consumer.sourceStepIndex] = GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer(
            consumer.sourceStepIndex,
            consumer.packetId,
            consumer.commandId,
            consumer.sourceOrder,
            consumer.dependencyFromPreviousConsumerToken,
            index == consumers.lastIndex,
            route,
            arena.slices[index + 1],
            slabAuthority,
            attachmentAuthority,
            slabAuthority.uniformSlabSeal.plan.slots[index].let { slot ->
                GPUCorePrimitiveClipStencilPreparedUniformSlice(
                    slabAuthority.uniformResource,
                    slabAuthority.uniformGeneration,
                    consumer.commandId,
                    slot.alignedOffset,
                    slot.payloadBytes,
                    slot.allocatedBytes,
                )
            },
        )
    }
    return GPUCorePrimitiveClipStencilPreparedFrameRouteSeal.Route(
        key = GPUCorePrimitiveClipStencilPreparedFrameRouteKey(
            producerSourceStepIndex,
            producerPacketId,
            route.producer.contentKey,
        ),
        route = route,
        geometryArena = arena,
        slabAuthority = slabAuthority,
        attachmentAuthority = attachmentAuthority,
        viewsBySourceStepIndex = views,
    )
}
