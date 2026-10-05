package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.execution.*
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.payloads.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan

/** Source-free portion of FramePlanner's provisional-segment compatibility authority. */
internal data class GPUHostRenderMergeFacts(
    val target: GPUFrameTargetRef,
    val loadStore: GPULoadStorePlan,
    val depthStencilLoadStore: GPUDepthStencilLoadStorePlan?,
    val samplePlan: GPUSamplePlan,
    val provisionalSegmentKey: GPUProvisionalRenderSegmentKey,
    val targetStateHash: String,
)

internal fun corePrimitivePathDepthStencilLoadStore(role: GPUDrawPacketRole? = null): GPUDepthStencilLoadStorePlan = when (role) {
    GPUDrawPacketRole.PathStencilProducer -> GPUDepthStencilLoadStorePlan.WritableStencil(GPUStencilLoadOperation.Clear, GPUStorePlan.Store, 0u)
    GPUDrawPacketRole.PathStencilCover -> GPUDepthStencilLoadStorePlan.ReadOnlyKeep
    else -> GPUDepthStencilLoadStorePlan.WritableStencil(GPUStencilLoadOperation.Clear, GPUStorePlan.Discard, 0u)
}

/** The shared ordered partition operation. Keys describe geometry/operation boundaries, not sources. */
internal fun <T, K> partitionHostRuns(
    values: List<T>, key: (T) -> K, breakBefore: (T) -> Boolean = { false },
    breakAfter: (T) -> Boolean = { false },
): List<List<T>> {
    val runs = mutableListOf<MutableList<T>>()
    values.forEach { value ->
        val current = runs.lastOrNull()
        if (current == null || key(current.first()) != key(value) || breakBefore(value) || breakAfter(current.last()))
            runs += mutableListOf(value)
        else current += value
    }
    return runs.map(::immutableList)
}

internal fun corePrimitivePathPacketIdentity(base: GPUDrawPacketID, producer: Boolean): GPUDrawPacketID =
    GPUDrawPacketID("${base.value}.path-stencil-${if (producer) "producer" else "cover"}")

/** Geometry-only projection of the existing Core uniform-layout run key. */
internal fun corePrimitiveGeometryRunLayoutKey(path: Boolean, analyticClip: Boolean,
    destinationRead: Boolean, uniformBytes: Int): String = when {
    path && analyticClip -> "path-analytic-clip"
    path && destinationRead -> "path-dst-read"
    else -> "uniform$uniformBytes"
}

/**
 * Final physical Core segments, including mandatory copy boundaries and mixed-lane interruptions.
 * This token contains no executable packet or source identity. Post-bind dispatch only addresses it.
 */
internal class GPUHostRunBoundaryPlan private constructor(
    segments: List<List<GPUDrawPacketID>>,
    mandatoryCopyConsumers: Set<GPUDrawPacketID>,
) {
    val segments = immutableList(segments.map(::immutableList))
    val segmentByPacketId = immutableMap(this.segments.flatMapIndexed { index, ids -> ids.map { it to index } }.toMap())
    val mandatoryCopyConsumers = java.util.Collections.unmodifiableSet(LinkedHashSet(mandatoryCopyConsumers))
    init {
        require(this.segments.none { it.isEmpty() })
        require(segmentByPacketId.size == this.segments.sumOf { it.size })
        require(segmentByPacketId.keys.containsAll(mandatoryCopyConsumers))
    }

    fun segment(packetId: GPUDrawPacketID): Int = segmentByPacketId.getValue(packetId)
    fun requireExactSegments(actual: List<List<GPUDrawPacketID>>) {
        require(actual == segments) { "Material dispatch changed the captured physical Core run boundaries" }
    }

    companion object {
        internal data class Input(val packetId: GPUDrawPacketID, val key: Any,
            val copyBefore: Boolean, val isolateAfter: Boolean)
        fun prepare(inputs: List<Input>): GPUHostRunBoundaryPlan = GPUHostRunBoundaryPlan(
            partitionHostRuns(inputs, { it.key }, { it.copyBefore }, { it.isolateAfter }).map { run -> run.map { it.packetId } },
            inputs.filter { it.copyBefore }.mapTo(linkedSetOf()) { it.packetId })
    }
}

/** Packed host arenas and their final physical run ranges, issued strictly before V6 capture. */
internal class GPUCorePrimitiveGeometryRunPlan private constructor(
    val boundaries: GPUHostRunBoundaryPlan,
    val arena: GPUCorePrimitiveNativeScopeGeometryArena,
    val layoutBatch: Boolean,
    coreLayoutRuns: List<List<Int>>,
    layoutKeys: Map<Int, String>,
    pathPairs: Map<Int, GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>,
    directRoutes: Map<Int, GPUCorePrimitiveDirectNativeRoute.Accepted>,
    val sizing: GPUCorePrimitiveRenderRunSizingV1?,
    private val layouts: List<GPUUniformSlabLayout>,
    private val slabCommandIds: List<List<Int>>,
    private val uniformGeometry: Map<Int, GPUCorePrimitiveGeometryUniformBytes>,
    private val uniformGeometryArena: ByteArray,
    private val packetCommandIds: Map<GPUDrawPacketID, Int>,
) {
    internal class HostSizeRefusal : IllegalArgumentException("Core host arenas exceed the admitted buffer envelope")
    internal class UniformLayoutRefusal(val code: String, message: String) : IllegalArgumentException(message)
    val coreLayoutRuns = immutableList(coreLayoutRuns.map(::immutableList))
    val layoutKeys = immutableMap(layoutKeys)
    val pathPairs = immutableMap(pathPairs)
    val directRoutes = immutableMap(directRoutes)
    private val uniformRangesByCommandId = immutableMap(buildMap<Int, Pair<Int, Int>> {
        layouts.forEachIndexed { index, layout -> layout.slots.zip(slabCommandIds[index]).forEach { (slot, id) ->
            putIfAbsent(id, Math.toIntExact(Math.addExact(requireNotNull(sizing).uniformBasesI64[index], slot.alignedOffset)) to Math.toIntExact(slot.payloadBytes))
        } }
    })
    internal fun uniformRange(commandId: Int): Pair<Int, Int> = uniformRangesByCommandId.getValue(commandId)
    val slicesByRun = immutableList(boundaries.segments.map { ids ->
        immutableList(ids.map { id -> arena.slices.single { it.packetId == id } }) })

    /** The only post-publication write fills the source-color holes; geometry bytes never move. */
    fun bindMaterials(semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>): GPUCorePrimitiveMaterialDispatchPlan {
        require(semantics.keys == uniformGeometry.keys)
        val bytes = uniformGeometryArena.copyOf()
        layouts.forEachIndexed { runIndex, layout ->
            val base = requireNotNull(sizing).uniformBasesI64[runIndex]
            layout.slots.zip(slabCommandIds[runIndex]).forEach { (slot, commandId) ->
                uniformGeometry.getValue(commandId).bindSourceColorInto(bytes,
                    Math.toIntExact(Math.addExact(base, slot.alignedOffset)), semantics.getValue(commandId).premultipliedRgba)
            }
        }
        val slabs = layouts.mapIndexed { index, layout ->
            val base = Math.toIntExact(requireNotNull(sizing).uniformBasesI64[index])
            val packed = bytes.copyOfRange(base, Math.addExact(base, Math.toIntExact(layout.totalBytes)))
            val plan = layout.bind(layout.slots.map { slot ->
                val start = Math.toIntExact(slot.alignedOffset)
                GPUUniformSlabPayload(slot.slotLabel,
                    packed.copyOfRange(start, Math.addExact(start, Math.toIntExact(slot.payloadBytes))))
            })
            GPUCorePrimitiveUniformSlabSeal(plan, slabCommandIds[index], packed)
        }
        return GPUCorePrimitiveMaterialDispatchPlan(this, bytes, immutableMap(semantics), immutableList(slabs))
    }

    fun validateRoutes(routes: List<GPUCorePrimitiveNativeScopeRouteSeal.Routes>) {
        boundaries.requireExactSegments(routes.map { it.flattenedPacketIds })
        require(routes.size == layouts.size)
        routes.forEachIndexed { index, route ->
            val expected = layouts[index]
            require(route.uniformPlan.totalBytes == expected.totalBytes &&
                route.uniformPlan.alignmentBytes == expected.alignmentBytes &&
                route.uniformPlan.deviceGeneration == expected.deviceGeneration &&
                route.commandIds == slabCommandIds[index] &&
                route.uniformCommandIds == slabCommandIds[index] &&
                route.uniformPlan.slots.map { Triple(it.slotLabel, it.alignedOffset, it.allocatedBytes) } ==
                expected.slots.map { Triple(it.slotLabel, it.alignedOffset, it.allocatedBytes) }) {
                "Material dispatch substituted a prepublication uniform layout"
            }
            val pieces = route.geometryPieces()
            require(pieces.size == slicesByRun[index].size)
            pieces.zip(slicesByRun[index]).forEach { (piece, slice) ->
                val commandId = packetCommandIds.getValue(piece.packetId)
                require(when (piece) {
                    is GPUCorePrimitiveGeometryPiece.Direct -> piece.route === directRoutes[commandId]
                    is GPUCorePrimitiveGeometryPiece.Path -> piece.geometry === pathPairs[commandId]?.let {
                        if (piece.role == GPUCorePrimitiveNativeScopeArenaRole.PathProducer) it.producer else it.cover
                    }
                }) { "Material dispatch substituted a captured geometry snapshot" }
                require(piece.packetId == slice.packetId && piece.role == slice.role &&
                    piece.vertexCount == slice.vertexCount && piece.indexCount == slice.indexCount && piece.maxLocalIndex == slice.maxLocalIndex)
            }
        }
        if (routes.isNotEmpty()) require(sizing == corePrimitiveRenderRunSizingV1(routes, layouts.first().alignmentBytes))
    }

    fun uniformOffsets(routes: List<GPUCorePrimitiveNativeScopeRouteSeal.Routes>): List<List<Long>> =
        routes.mapIndexed { index, route -> route.commandIds.map { commandId ->
            val slotIndex = slabCommandIds[index].indexOf(commandId)
            require(slotIndex >= 0)
            Math.addExact(requireNotNull(sizing).uniformBasesI64[index], layouts[index].slots[slotIndex].alignedOffset)
        } }

    companion object {
        fun prepare(
            plans: List<GPURecordedPlan.Routed>, allConsumerCommandIds: List<Int>,
            geometries: Map<Int, GPUCorePrimitiveGeometryAuthority>,
            directRoutes: Map<Int, GPUCorePrimitiveDirectNativeRoute.Accepted>,
            uniforms: Map<Int, GPUCorePrimitiveGeometryUniformBytes>,
            prepareUniformLayout: (List<Int>) -> GPUUniformSlabLayout,
            destinationCommandIds: Set<Int>, alignment: Long, maxArenaBytesI64: Long,
        ): GPUCorePrimitiveGeometryRunPlan {
            val packets = plans.flatMap { it.plan.pass.drawPackets }
            require(allConsumerCommandIds == allConsumerCommandIds.sorted() && allConsumerCommandIds.distinct().size == allConsumerCommandIds.size)
            require(allConsumerCommandIds.containsAll(geometries.keys))
            val paths = geometries.keys - directRoutes.keys
            val layoutBatch = paths.none { it in destinationCommandIds }
            val layoutKey = packets.associate { packet -> packet.commandIdValue to corePrimitiveGeometryRunLayoutKey(
                packet.commandIdValue in paths, false, packet.commandIdValue in destinationCommandIds,
                uniforms.getValue(packet.commandIdValue).byteCountI32) }
            val coreLayoutRuns = partitionHostRuns(packets, { layoutKey.getValue(it.commandIdValue) })
            val layoutRunByCommand = coreLayoutRuns.flatMapIndexed { index, run -> run.map { it.commandIdValue to index } }.toMap()
            val pieces = mutableListOf<GPUCorePrimitiveGeometryPiece>()
            val pairs = linkedMapOf<Int, GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>()
            val packetCommands = linkedMapOf<GPUDrawPacketID, Int>()
            val inputs = mutableListOf<GPUHostRunBoundaryPlan.Companion.Input>()
            packets.forEach { packet ->
                val id = packet.commandIdValue
                val path = id in paths
                val dst = id in destinationCommandIds
                val geometry = geometries.getValue(id)
                val piecesForCommand = if (!path) listOf(GPUCorePrimitiveGeometryPiece.Direct(packet.packetId, directRoutes.getValue(id))) else {
                    val fan = geometry.geometry as GPUCorePrimitiveGeometry.TriangulatedPath
                    val pair = GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(
                        corePrimitivePathPacketIdentity(packet.packetId, true), corePrimitivePathPacketIdentity(packet.packetId, false),
                        FloatArray(fan.vertices.size) { fan.vertices[it] }, IntArray(fan.indices.size) { fan.indices[it] },
                        fan.coverBounds, geometry.targetBounds, fan.inverseFill)
                    pairs[id] = pair
                    listOf(GPUCorePrimitiveGeometryPiece.Path(pair.producerPacketId, GPUCorePrimitiveNativeScopeArenaRole.PathProducer, pair.producer),
                        GPUCorePrimitiveGeometryPiece.Path(pair.coverPacketId, GPUCorePrimitiveNativeScopeArenaRole.PathCover, pair.cover))
                }
                val globalCoreRun = allConsumerCommandIds.takeWhile { it != id }.count { it !in geometries }
                piecesForCommand.forEach { piece ->
                    val cover = piece.role == GPUCorePrimitiveNativeScopeArenaRole.PathCover
                    val copy = dst && (!path || cover)
                    val continuedRole = if (path && dst) piece.role else null
                    // Every common V6 frame uses the same prepared-surface assembler,
                    // including frames whose survivors happen to be exclusively Core.
                    val key = if (layoutBatch) listOf(layoutRunByCommand.getValue(id), path, globalCoreRun)
                        else listOf(id, continuedRole)
                    inputs += GPUHostRunBoundaryPlan.Companion.Input(piece.packetId, key, copy, copy)
                    packetCommands[piece.packetId] = id
                }
                pieces += piecesForCommand
            }
            val boundary = GPUHostRunBoundaryPlan.prepare(inputs)
            val slabCommands = boundary.segments.map { ids -> ids.map(packetCommands::getValue).distinct() }
            val runLayouts = slabCommands.map { commandIds ->
                val sizes = commandIds.map { uniforms.getValue(it).byteCountI32 }.distinct()
                require(sizes.size == 1) { "A physical Core run cannot mix uniform layouts" }
                prepareUniformLayout(commandIds)
            }
            val sizing = if (pieces.isEmpty()) null else corePrimitiveRenderRunSizingV1(
                listOf(GPUCorePrimitiveNativeScopeGeometryArena.countsI64(pieces)),
                runLayouts.map { it.totalBytes }, alignment)
            // Use the native owner's checked physical capacities before any arena-sized
            // host allocation. The complete frame is still budgeted once by V6 afterward.
            sizing?.capacities?.let { capacities ->
                if (maxOf(capacities.vertexBytes, capacities.indexBytes, capacities.uniformBytes) > maxArenaBytesI64)
                    throw HostSizeRefusal()
            }
            val arena = GPUCorePrimitiveNativeScopeGeometryArena.pack(pieces)
            val uniformArena = ByteArray(sizing?.uniformBytesI64?.let(Math::toIntExact) ?: 0)
            runLayouts.forEachIndexed { index, layout ->
                layout.slots.zip(slabCommands[index]).forEach { (slot, id) ->
                    uniforms.getValue(id).copyGeometryInto(uniformArena,
                        Math.toIntExact(Math.addExact(requireNotNull(sizing).uniformBasesI64[index], slot.alignedOffset)))
                }
            }
            return GPUCorePrimitiveGeometryRunPlan(boundary, arena, layoutBatch,
                boundary.segments.map { ids -> ids.map(packetCommands::getValue).distinct() },
                layoutKey, pairs, directRoutes, sizing, immutableList(runLayouts),
                immutableList(slabCommands.map(::immutableList)), immutableMap(uniforms), uniformArena, immutableMap(packetCommands))
        }
    }
}

/** Post-V6 companion: source bytes and exact semantic objects, never a second geometry owner. */
internal class GPUCorePrimitiveMaterialDispatchPlan internal constructor(
    val geometry: GPUCorePrimitiveGeometryRunPlan,
    private val uniforms: ByteArray,
    private val semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    val uniformSlabs: List<GPUCorePrimitiveUniformSlabSeal>,
) {
    fun uniformSlab(packetId: GPUDrawPacketID): GPUCorePrimitiveUniformSlabSeal =
        uniformSlabs[geometry.boundaries.segment(packetId)]
    fun validatesUniformPacket(packet: GPUDrawPacket): Boolean {
        val authority = packet.corePrimitivePreparedAuthority ?: return false
        val slab = uniformSlab(packet.packetId)
        return authority.materialDispatchPlan === this && when (val shape = authority.analyticShapeUniformSeal) {
            null -> authority.uniformSlabSeal === slab
            else -> shape.plan === slab.plan && shape.commandId == packet.commandIdValue &&
                slab.commandIds.getOrNull(shape.slotIndex) == packet.commandIdValue &&
                shape.hasExactPayload(uniformPayload(packet.commandIdValue))
        }
    }
    fun validateRoutes(routes: List<GPUCorePrimitiveNativeScopeRouteSeal.Routes>) {
        geometry.validateRoutes(routes)
        require(routes.indices.all { routes[it].uniformPlan === uniformSlabs[it].plan }) {
            "Material dispatch substituted a bound physical uniform slab"
        }
    }
    fun packedUniformBytesForUpload(): ByteArray = uniforms.copyOf()
    fun uniformPayload(commandId: Int): ByteArray {
        val (offset, count) = geometry.uniformRange(commandId)
        return uniforms.copyOfRange(offset, Math.addExact(offset, count))
    }
    fun validatesMaterial(commandId: Int, actual: GPUDrawSemanticPayload.CorePrimitive): Boolean =
        semantics[commandId]?.let { it.material === actual.material && it.premultipliedRgba == actual.premultipliedRgba } == true
}
