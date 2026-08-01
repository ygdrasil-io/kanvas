package org.graphiks.kanvas.gpu.renderer.vertices

import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeDrawCall
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeOperandOwnership
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeRenderCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeScopeOperand
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeTextureViewOperand
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedRenderRunMaterialization
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedVerticesBatchCandidate
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedVerticesBatchingPlanner
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedVerticesBatchSplit
import org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedVerticesRenderRunMaterializer
import org.graphiks.kanvas.gpu.renderer.execution.PreparedVerticesPreflightFixture
import org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesRenderRunTestPlan
import org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightArtifact
import org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightFixture
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesTopologyIdentity
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

/**
 * FP-06 Task 12 batching tests for the prepared-vertices route.
 *
 * Draws are batch-compatible only when pipeline/layout/topology/material ABI/target/blend/clip
 * scope permit it, and barriers split the adjacency for clip change, destination read, layer
 * boundary, filter/composite boundary, sampled-resource upload, incompatible blend, and
 * explicit command order. Packed subranges are checked, aligned, non-overlapping, packet
 * ordered, and never rebase a draw's exact first vertex / base index / base vertex.
 */
class PreparedVerticesBatchingTest {

    // ------------------------------------------------------------------
    // Planner-level compatibility matrix.
    // ------------------------------------------------------------------

    @Test
    fun `planner merges adjacent draws only when every compatibility axis matches`() {
        val candidates = listOf(
            candidate(packetId = "p.1"),
            candidate(packetId = "p.2"),
            candidate(packetId = "p.3"),
        )

        val plan = GPUPreparedVerticesBatchingPlanner().plan(candidates)

        assertEquals(1, plan.batches.size)
        assertEquals(listOf("p.1", "p.2", "p.3"), plan.batches.single().packetIds.map { it.value })
        assertTrue(plan.splitReasons.isEmpty())
    }

    @Test
    fun `planner splits on every compatibility axis with its exact reason`() {
        val mutations = listOf(
            "pipeline" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(pipelineKeyHash = "pipeline:other")
            },
            "layout" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(vertexLayoutHash = "layout:other")
            },
            "topology" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(topology = "triangle-strip")
            },
            "material-abi" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(materialAbiHash = "material:other")
            },
            "target-format" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(targetFormat = "rgba8unorm")
            },
            "index-format" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(indexFormat = "uint32")
            },
            "incompatible-blend" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(finalBlendIdentity = "multiply")
            },
            "clip-change" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(clipIdentity = "clip:scissored")
            },
        )

        mutations.forEach { (axis, mutate) ->
            val plan = GPUPreparedVerticesBatchingPlanner().plan(
                listOf(candidate(packetId = "p.1"), mutate(candidate(packetId = "p.2"))),
            )
            assertEquals(2, plan.batches.size, "axis $axis must split the adjacency")
            val split = plan.splitReasons.single()
            assertEquals("p.1", split.beforePacketId.value)
            assertEquals("p.2", split.afterPacketId.value)
            assertEquals(axis, split.reason.label(), "axis $axis must report its exact reason")
        }
    }

    // ------------------------------------------------------------------
    // Planner-level barrier matrix.
    // ------------------------------------------------------------------

    @Test
    fun `planner splits on every barrier kind and never reorders draws`() {
        val barrierMutations = listOf(
            "clip-change" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(clipIdentity = "clip:scissored")
            },
            "destination-read" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(destinationReadClass = "destination-read")
            },
            "layer-boundary" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(layerId = "layer:child")
            },
            "filter-composite-boundary" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(filterCompositeScope = "filter:blur")
            },
            "sampled-resource-upload" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(sampledResourceScope = "sampled:upload")
            },
            "incompatible-blend" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(primitiveBlendIdentity = "blend:primitive")
            },
            "explicit-command-order" to { it: GPUPreparedVerticesBatchCandidate ->
                it.copy(commandOrderBand = "explicit-order")
            },
        )

        barrierMutations.forEach { (barrier, mutate) ->
            val plan = GPUPreparedVerticesBatchingPlanner().plan(
                listOf(candidate(packetId = "p.1"), mutate(candidate(packetId = "p.2"))),
            )
            assertEquals(2, plan.batches.size, "barrier $barrier must split the adjacency")
            assertEquals(barrier, plan.splitReasons.single().reason.label(), barrier)
        }

        val three = GPUPreparedVerticesBatchingPlanner().plan(
            listOf(
                candidate(packetId = "p.1"),
                candidate(packetId = "p.2").let { it ->
                    it.copy(clipIdentity = "clip:scissored")
                },
                candidate(packetId = "p.3"),
            ),
        )
        assertEquals(
            listOf("p.1", "p.2", "p.3"),
            three.batches.flatMap { batch -> batch.packetIds }.map { id -> id.value },
            "batching must never reorder draws across a barrier",
        )
    }

    // ------------------------------------------------------------------
    // Packed subranges.
    // ------------------------------------------------------------------

    @Test
    fun `packed subranges are aligned non overlapping and packet ordered`() {
        val plan = GPUPreparedVerticesBatchingPlanner().plan(
            listOf(
                candidate(packetId = "p.1", artifactKey = "artifact:a", vertexByteCount = 48L,
                    indexByteCount = 12L),
                candidate(packetId = "p.2", artifactKey = "artifact:b", vertexByteCount = 96L,
                    indexByteCount = 14L),
                candidate(packetId = "p.3", artifactKey = "artifact:c", vertexByteCount = 16L,
                    indexByteCount = 18L),
            ),
        )

        val batch = plan.batches.single()
        val vertexSubranges = batch.vertexPack.subranges
        assertEquals(
            listOf("artifact:a" to 0L, "artifact:b" to 48L, "artifact:c" to 144L),
            vertexSubranges.map { subrange -> subrange.artifactKey to subrange.offsetBytes },
        )
        assertEquals(160L, batch.vertexPack.totalBytes)
        val indexSubranges = assertNotNull(batch.indexPack).subranges
        assertEquals(
            listOf("artifact:a" to 0L, "artifact:b" to 12L, "artifact:c" to 28L),
            indexSubranges.map { subrange -> subrange.artifactKey to subrange.offsetBytes },
        )
        assertEquals(46L, batch.indexPack!!.totalBytes)
        (vertexSubranges + indexSubranges).forEach { subrange ->
            assertEquals(0L, subrange.offsetBytes % 4L, "packed offsets must be 4-byte aligned")
        }
        plan.batches.forEach { candidateBatch ->
            candidateBatch.vertexPack.subranges.zipWithNext().forEach { (left, right) ->
                assertTrue(
                    left.offsetBytes + left.byteCount <= right.offsetBytes,
                    "vertex subranges must not overlap",
                )
            }
        }
    }

    @Test
    fun `packed subranges retain each draw exact first vertex base index and base vertex`() {
        val plan = GPUPreparedVerticesBatchingPlanner().plan(
            listOf(
                candidate(packetId = "p.1", artifactKey = "artifact:a", vertexByteCount = 48L,
                    indexByteCount = 12L),
                candidate(packetId = "p.2", artifactKey = "artifact:b", vertexByteCount = 96L,
                    indexByteCount = 24L),
            ),
        )

        val batch = plan.batches.single()
        assertEquals(listOf("p.1", "p.2"), batch.packetIds.map { id -> id.value })
        val vertexByArtifact = batch.vertexPack.subranges.associateBy { it.artifactKey }
        assertEquals(0L, vertexByArtifact.getValue("artifact:a").offsetBytes)
        assertEquals(48L, vertexByArtifact.getValue("artifact:a").byteCount)
        assertEquals(48L, vertexByArtifact.getValue("artifact:b").offsetBytes)
        assertEquals(96L, vertexByArtifact.getValue("artifact:b").byteCount)
        val indexByArtifact = assertNotNull(batch.indexPack).subranges.associateBy { it.artifactKey }
        assertEquals(0L, indexByArtifact.getValue("artifact:a").offsetBytes)
        assertEquals(12L, indexByArtifact.getValue("artifact:a").byteCount)
        assertEquals(12L, indexByArtifact.getValue("artifact:b").offsetBytes)
        assertEquals(24L, indexByArtifact.getValue("artifact:b").byteCount)
        assertEquals(
            listOf(0L, 48L),
            batch.packetIds.map { id -> vertexByArtifact.getValue(batch.artifactKeyFor(id)).offsetBytes },
        )
        assertEquals(
            listOf(0L, 12L),
            batch.packetIds.map { id -> indexByArtifact.getValue(batch.artifactKeyFor(id)).offsetBytes },
        )
    }

    // ------------------------------------------------------------------
    // Contract conformance.
    // ------------------------------------------------------------------

    @Test
    fun `execution split reasons conform to the vertices batching contract`() {
        val barrierLabels = GPU_PREPARED_VERTICES_BATCH_BARRIER_KINDS
        val compatibilityAxes = GPU_PREPARED_VERTICES_BATCH_COMPATIBILITY_AXES

        assertEquals(
            barrierLabels,
            GPUPreparedVerticesBatchSplit.entries
                .filter { split -> split in barrierSplits }
                .map(GPUPreparedVerticesBatchSplit::label),
        )
        GPUPreparedVerticesBatchSplit.entries
            .filterNot { split -> split in barrierSplits }
            .forEach { split ->
                assertTrue(
                    split.label() in compatibilityAxes,
                    "split ${split.label()} must be one compatibility axis",
                )
            }
        assertEquals(
            PREPARED_VERTICES_BATCH_NONCLAIM_LINE,
            org.graphiks.kanvas.gpu.renderer.telemetry.GPUPreparedVerticesBatchingCounters
                .of(emptyMap())
                .dumpLines()
                .last(),
            "the telemetry dump must carry exactly the vertices non-claim line",
        )
    }

    // ------------------------------------------------------------------
    // Materializer integration.
    // ------------------------------------------------------------------

    @Test
    fun `compatible two draw run shares one packed buffer and one pipeline emission`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(commandId = 0),
                verticesPreflightSemanticFor(commandId = 1),
            ),
        )
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val ready = materialize(plan, rebuilt, native)

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        val commands = render.commands
        assertEquals(
            1,
            commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size,
            "one compatible batch must emit one SetPipeline",
        )
        assertEquals(
            2,
            commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().size,
        )
        val vertexBindings =
            commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
        assertEquals(2, vertexBindings.size)
        assertEquals(listOf(0L, 0L), vertexBindings.map { binding -> binding.offset })
        assertEquals(listOf(48L, 48L), vertexBindings.map { binding -> binding.size })
        val indexBindings = commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
        assertEquals(2, indexBindings.size)
        assertEquals(listOf(0L, 0L), indexBindings.map { binding -> binding.offset })
        commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().forEach { command ->
            val draw = command.drawCall
            assertEquals(6, draw.indexCount)
            assertEquals(0, draw.firstIndex)
            assertEquals(0, draw.baseVertex)
        }
        assertEquals(1, native.bufferRecords.count { it.label == "vertex" })
        assertEquals(1, native.bufferRecords.count { it.label == "index" })
        val vertexUploads = ready.uniformUploads.filter { upload -> upload.uploadRole == "vertex" }
        assertEquals(1, vertexUploads.size)
        assertEquals(0L, vertexUploads.single().destinationOffset)
        assertEquals(2, ready.uniformUploads.filter { it.uploadRole == "draw-uniforms" }.size)
        ready.ownedResources.single().close()
    }

    @Test
    fun `distinct artifacts pack at checked offsets with exact draw emission`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(
                    commandId = 0,
                    artifact = verticesPreflightArtifact(vertexCount = 6),
                ),
                verticesPreflightSemanticFor(
                    commandId = 1,
                    artifact = verticesPreflightArtifact(vertexCount = 12),
                ),
            ),
        )
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val ready = materialize(plan, rebuilt, native)

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        val vertexBindings =
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
        assertEquals(listOf(0L, 48L), vertexBindings.map { binding -> binding.offset })
        assertEquals(listOf(48L, 96L), vertexBindings.map { binding -> binding.size })
        val indexBindings =
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
        assertEquals(listOf(0L, 12L), indexBindings.map { binding -> binding.offset })
        assertEquals(listOf(12L, 24L), indexBindings.map { binding -> binding.size })
        val draws = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
        assertEquals(listOf(6, 12), draws.map { command -> command.drawCall.indexCount })
        draws.forEach { command ->
            assertEquals(0, command.drawCall.firstIndex)
            assertEquals(0, command.drawCall.baseVertex)
        }
        val packedVertex = native.bufferRecords.single { it.label == "vertex" }
        assertEquals(144uL, packedVertex.descriptor.size)
        val packedIndex = native.bufferRecords.single { it.label == "index" }
        assertEquals(36uL, packedIndex.descriptor.size)
        ready.ownedResources.single().close()
    }

    @Test
    fun `clip change splits the run into two packed buffers`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(commandId = 0),
                verticesPreflightSemanticFor(commandId = 1, clipIdentity = "clip:scissored"),
            ),
        )
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val ready = materialize(plan, rebuilt, native)

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        assertEquals(
            2,
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size,
            "a clip barrier must split the run into two singleton batches",
        )
        assertEquals(2, native.bufferRecords.count { it.label == "vertex" })
        val draws = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
        assertEquals(2, draws.size)
        assertEquals(listOf(0, 0), draws.map { command -> command.drawCall.firstIndex })
        assertEquals(listOf(0, 0), draws.map { command -> command.drawCall.baseVertex })
        ready.ownedResources.single().close()
    }

    @Test
    fun `incompatible pipeline splits and preserves draw order`() {
        val otherMaterial = compiledPreparedVerticesMaterial(
            org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor(
                r = 0.25f,
                g = 1f,
                b = 1f,
                a = 1f,
            ),
        )
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(commandId = 0),
                verticesPreflightSemanticFor(commandId = 1, material = otherMaterial),
            ),
        )
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val ready = materialize(plan, rebuilt, native)

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        assertEquals(2, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size)
        assertEquals(
            listOf("DrawIndexed", "DrawIndexed"),
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
                .map { command -> command::class.simpleName },
        )
        assertEquals(2, native.bufferRecords.count { it.label == "vertex" })
        ready.ownedResources.single().close()
    }

    @Test
    fun `batching disabled emits the exact unbatched draw emission sequence`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(commandId = 0),
                verticesPreflightSemanticFor(commandId = 1),
            ),
        )
        val disabled = RecordingPreparedVerticesBatchNative()
        val enabled = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val off = materialize(plan, rebuilt, disabled, batchingEnabled = false)
        val on = materialize(plan, rebuilt, enabled, batchingEnabled = true)

        assertEquals(offDrawSequence(off), onDrawSequence(on))
        assertEquals(2, offDrawSequence(off).size)
        val onRender = assertIs<GPUPreparedNativeScopeOperand.Render>(on.scopeOperands.single())
        val offRender = assertIs<GPUPreparedNativeScopeOperand.Render>(off.scopeOperands.single())
        assertEquals(
            1,
            onRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size,
            "one compatible batch must emit one pipeline change",
        )
        assertEquals(
            2,
            offRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size,
            "the unbatched path must emit one pipeline change per draw",
        )
    }

    @Test
    fun `batching an incompatible frame emits the exact unbatched command sequence`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(commandId = 0),
                verticesPreflightSemanticFor(commandId = 1, clipIdentity = "clip:scissored"),
            ),
        )
        val disabled = RecordingPreparedVerticesBatchNative()
        val enabled = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val off = materialize(plan, rebuilt, disabled, batchingEnabled = false)
        val on = materialize(plan, rebuilt, enabled, batchingEnabled = true)

        assertEquals(offCommandSequence(off), offCommandSequence(on))
        val onRender = assertIs<GPUPreparedNativeScopeOperand.Render>(on.scopeOperands.single())
        assertEquals(
            2,
            onRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size,
            "a clip barrier must keep two singleton batches with two pipeline changes",
        )
        assertEquals(
            2,
            onRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().size,
        )
    }

    @Test
    fun `compatible two draw frame keeps every draw exactly its own vertex bytes`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesPackets(
            listOf(
                verticesPreflightSemanticFor(commandId = 0),
                verticesPreflightSemanticFor(commandId = 1),
            ),
        )
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)

        val ready = materialize(plan, rebuilt, native)

        val vertexUploads = ready.uniformUploads.filter { upload -> upload.uploadRole == "vertex" }
        val artifactBytes = plan.packets.first().artifact.vertexBytesForUpload()
        assertEquals(1, vertexUploads.size)
        assertContentEquals(artifactBytes, vertexUploads.single().data.bytes())
        val indexUploads = ready.uniformUploads.filter { upload -> upload.uploadRole == "index" }
        assertContentEquals(
            plan.packets.first().artifact.indexBytesForUpload(),
            indexUploads.single().data.bytes(),
        )
        ready.ownedResources.single().close()
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private fun materialize(
        plan: org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedVerticesRenderRunPlan,
        fixture: PreparedVerticesPreflightFixture,
        native: RecordingPreparedVerticesBatchNative,
        batchingEnabled: Boolean = true,
    ): GPUPreparedRenderRunMaterialization.Ready =
        assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(
                native.device,
                batchingEnabled = batchingEnabled,
            ).materializeAcceptedRun(
                plan,
                fixture.context.deviceGeneration,
                targetViewOperand(fixture.context.deviceGeneration, native),
            ),
        )

    private fun offDrawSequence(ready: GPUPreparedRenderRunMaterialization.Ready): List<String> {
        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        return render.commands
            .filter { command ->
                command is GPUPreparedNativeRenderCommand.Draw ||
                    command is GPUPreparedNativeRenderCommand.DrawIndexed
            }
            .map { command ->
                when (command) {
                    is GPUPreparedNativeRenderCommand.Draw -> {
                        val draw = command.drawCall
                        "draw:${draw.vertexCount}:${draw.instanceCount}:${draw.firstVertex}:" +
                            "${draw.firstInstance}"
                    }
                    is GPUPreparedNativeRenderCommand.DrawIndexed -> {
                        val draw = command.drawCall
                        "drawIndexed:${draw.indexCount}:${draw.instanceCount}:${draw.firstIndex}:" +
                            "${draw.baseVertex}:${draw.firstInstance}"
                    }
                    else -> error("unreachable")
                }
            }
    }

    private fun onDrawSequence(ready: GPUPreparedRenderRunMaterialization.Ready): List<String> =
        offDrawSequence(ready)

    private fun offCommandSequence(ready: GPUPreparedRenderRunMaterialization.Ready): List<String> {
        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        return render.commands.map { command ->
            when (command) {
                is GPUPreparedNativeRenderCommand.SetPipeline ->
                    "SetPipeline:${command.pipeline.ownership.name}"
                is GPUPreparedNativeRenderCommand.SetBindGroup ->
                    "SetBindGroup:${command.index}"
                is GPUPreparedNativeRenderCommand.SetVertexBuffer ->
                    "SetVertexBuffer:${command.offset}:${command.size}"
                is GPUPreparedNativeRenderCommand.SetIndexBuffer ->
                    "SetIndexBuffer:${command.offset}:${command.size}:${command.format.name}"
                is GPUPreparedNativeRenderCommand.SetScissor ->
                    "SetScissor:${command.x}:${command.y}:${command.width}:${command.height}"
                is GPUPreparedNativeRenderCommand.Draw -> {
                    val draw = command.drawCall
                    "Draw:${draw.vertexCount}:${draw.firstVertex}"
                }
                is GPUPreparedNativeRenderCommand.DrawIndexed -> {
                    val draw = command.drawCall
                    "DrawIndexed:${draw.indexCount}:${draw.firstIndex}:${draw.baseVertex}"
                }
                else -> error("unexpected command ${command::class.simpleName}")
            }
        }
    }

    private fun candidate(
        packetId: String,
        artifactKey: String = "artifact:a",
        pipelineKeyHash: String = "pipeline:a",
        vertexLayoutHash: String = "layout:a",
        topology: String = "triangle-list",
        materialAbiHash: String = "material:a",
        targetFormat: String = "rgba8unorm-srgb",
        indexFormat: String? = "uint16",
        vertexByteCount: Long = 48L,
        indexByteCount: Long? = 12L,
        primitiveBlendIdentity: String? = null,
        finalBlendIdentity: String = "src-over",
        clipIdentity: String = "clip:none",
        layerId: String = "root",
        destinationReadClass: String = "none",
        filterCompositeScope: String = "none",
        sampledResourceScope: String = "none",
        commandOrderBand: String = "prepared-vertices",
    ): GPUPreparedVerticesBatchCandidate = GPUPreparedVerticesBatchCandidate(
        packetId = GPUDrawPacketID(packetId),
        artifactKey = artifactKey,
        pipelineKeyHash = pipelineKeyHash,
        vertexLayoutHash = vertexLayoutHash,
        topology = topology,
        materialAbiHash = materialAbiHash,
        targetFormat = targetFormat,
        indexFormat = indexFormat,
        vertexByteCount = vertexByteCount,
        indexByteCount = indexByteCount,
        primitiveBlendIdentity = primitiveBlendIdentity,
        finalBlendIdentity = finalBlendIdentity,
        clipIdentity = clipIdentity,
        layerId = layerId,
        destinationReadClass = destinationReadClass,
        filterCompositeScope = filterCompositeScope,
        sampledResourceScope = sampledResourceScope,
        commandOrderBand = commandOrderBand,
    )

    private fun PreparedVerticesPreflightFixture.withVerticesPackets(
        semantics: List<GPUDrawSemanticPayload.Vertices>,
    ): PreparedVerticesPreflightFixture {
        val renderIndex = framePlan.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep }
        val render = framePlan.steps[renderIndex] as GPUFrameStep.RenderPassStep
        val packets = render.drawPackets.toMutableList()
        semantics.forEachIndexed { index, semantic ->
            if (index < packets.size) {
                packets[index] = packets[index].rebuilt(semanticPayload = semantic)
            } else {
                val template = packets[index - 1]
                packets += template.rebuilt(
                    packetId = GPUDrawPacketID("packet.vertices.extra.$index"),
                    commandIdValue = index,
                    semanticPayload = semantic,
                )
            }
        }
        val rebuiltRender = GPUFrameStep.RenderPassStep(
            target = render.target,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = packets,
            sourceTaskIds = render.sourceTaskIds,
            batches = listOf(
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch(
                    batchId = "batch.vertices.test",
                    kind = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind.Isolated,
                    packets = packets,
                    sourceTaskIds = render.sourceTaskIds,
                ),
            ),
            sampleContinuation = render.sampleContinuation,
            depthStencilLoadStore = render.depthStencilLoadStore,
            preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
            preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
        )
        return copy(
            framePlan = GPUFramePlan(
                frameId = framePlan.frameId,
                capabilitySeal = framePlan.capabilitySeal,
                recordingSeals = framePlan.recordingSeals,
                steps = framePlan.steps.map { step -> if (step === render) rebuiltRender else step },
                memoryBudget = framePlan.memoryBudget,
                diagnostics = framePlan.diagnostics,
                dependencies = framePlan.dependencies,
                phaseOrder = framePlan.phaseOrder,
                elidedNoOpDraws = framePlan.elidedNoOpDraws,
                atomicallyRefused = framePlan.atomicallyRefused,
            ),
        )
    }

    private fun verticesPreflightSemanticFor(
        commandId: Int,
        artifact: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact =
            verticesPreflightArtifact(),
        material: org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
            org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialProgram(),
        clipIdentity: String = "clip:none",
    ): GPUDrawSemanticPayload.Vertices {
        val base = verticesPreflightSemanticForBase(commandId, artifact, material)
        return assertIs<GPUPreparedVerticesPayloadResult.Ready>(
            GPUPreparedVerticesPayloadGatherer.gather(
                GPUPreparedVerticesPayloadInput(
                    payloadRef = base.payloadRef,
                    artifact = artifact,
                    material = material,
                    topologyIdentity = when (artifact.topology) {
                        GPUVertexMode.Triangles -> GPUPreparedVerticesTopologyIdentity.Triangles
                        GPUVertexMode.TriangleStrip -> GPUPreparedVerticesTopologyIdentity.TriangleStrip
                        else -> error("fixture topology must be canonical")
                    },
                    transformBytes = base.transformBytes,
                    targetBounds = base.targetBounds,
                    scissorBounds = base.scissorBounds,
                    targetFormat = base.targetFormat,
                    clipIdentity = clipIdentity,
                    clipCoverageIdentity = base.clipCoverageIdentity,
                    primitiveColorPresent = base.primitiveColorPresent,
                    primitiveBlendIdentity = base.primitiveBlendIdentity,
                    finalBlendIdentity = base.finalBlendIdentity,
                    capabilitySnapshotHash = base.capabilitySnapshotHash,
                    drawProvenance = base.drawProvenance,
                    frameProvenance = base.frameProvenance,
                ),
            ),
        ).payload
    }

    private fun verticesPreflightSemanticForBase(
        commandId: Int,
        artifact: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact,
        material: org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram,
    ): GPUDrawSemanticPayload.Vertices =
        org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightSemantic(
            commandId = commandId,
            artifact = artifact,
            material = material,
        )

    private fun GPUDrawPacket.rebuilt(
        packetId: GPUDrawPacketID = this.packetId,
        commandIdValue: Int = this.commandIdValue,
        semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = packetId,
        commandIdValue = commandIdValue,
        analysisRecordId = analysisRecordId,
        passId = passId,
        layerId = layerId,
        bindingListId = bindingListId,
        insertionReasonCode = insertionReasonCode,
        sortKey = sortKey,
        sortKeyPreimage = sortKeyPreimage,
        renderStepId = renderStepId,
        renderStepVersion = renderStepVersion,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = renderPipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        frameProvenance = frameProvenance,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
    )

    private fun targetViewOperand(
        generation: GPUDeviceGenerationID,
        native: RecordingPreparedVerticesBatchNative,
    ): GPUPreparedNativeTextureViewOperand = GPUPreparedNativeTextureViewOperand(
        native.targetView,
        generation,
        GPUPreparedNativeOperandOwnership.Borrowed,
    )

    private fun compiledPreparedVerticesMaterial(
        descriptor: org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor,
    ): org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
        assertIs<org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult.Ready>(
            org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler.compile(
                descriptor = descriptor,
                paintAlpha = 1f,
                context = org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext(
                    capabilityClass = "webgpu-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:prepared-material:v1",
                    runtimeEffectResolver =
                        org.graphiks.kanvas.gpu.renderer.runtimeeffects
                            .KanvasPreparedRuntimeEffectResolver(),
                ),
            ),
        ).program

    private companion object {
        val barrierSplits: Set<GPUPreparedVerticesBatchSplit> = setOf(
            GPUPreparedVerticesBatchSplit.ClipChange,
            GPUPreparedVerticesBatchSplit.DestinationRead,
            GPUPreparedVerticesBatchSplit.LayerBoundary,
            GPUPreparedVerticesBatchSplit.FilterCompositeBoundary,
            GPUPreparedVerticesBatchSplit.SampledResourceUpload,
            GPUPreparedVerticesBatchSplit.IncompatibleBlend,
            GPUPreparedVerticesBatchSplit.ExplicitCommandOrder,
        )
    }
}

internal class BufferRecord(
    val buffer: GPUBuffer,
    val descriptor: BufferDescriptor,
    val label: String,
)

/** Recording wgpu4k fake shared by the FP-06 prepared-vertices batching and telemetry tests. */
internal class RecordingPreparedVerticesBatchNative {
    val pipelineDescriptors = mutableListOf<RenderPipelineDescriptor>()
    val bufferRecords = mutableListOf<BufferRecord>()
    val closeCounts = linkedMapOf<Any, Int>()
    private val handlesByLabel = linkedMapOf<String, MutableList<Any>>()
    private var ordinal = 0

    private inline fun <reified T : Any> handle(
        label: String,
        crossinline other: (String, Array<out Any?>?) -> Any? = { _, _ -> null },
        tracked: Boolean = true,
    ): T {
        val exactLabel = "$label.${ordinal++}"
        val proxy = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                proxy, method, args ->
            when (method.name) {
                "close" -> closeCounts[proxy] = closeCounts.getOrDefault(proxy, 0) + 1
                "setLabel" -> Unit
                "getLabel", "toString" -> exactLabel
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.singleOrNull()
                else -> other(method.name, args)
            }
        } as T
        if (tracked) {
            handlesByLabel.getOrPut(label) { mutableListOf() } += proxy
        }
        return proxy
    }

    val targetView: GPUTextureView = handle("target-view", tracked = false)

    val device: GPUDevice = handle(
        "device",
        tracked = false,
        other = { methodName, args ->
            when (methodName) {
                "createShaderModule" ->
                    handle<io.ygdrasil.webgpu.GPUShaderModule>("shader-module")
                "createBindGroupLayout" ->
                    handle<io.ygdrasil.webgpu.GPUBindGroupLayout>("bind-group-layout")
                "createPipelineLayout" ->
                    handle<io.ygdrasil.webgpu.GPUPipelineLayout>("pipeline-layout")
                "createRenderPipeline" -> {
                    pipelineDescriptors += args?.first() as RenderPipelineDescriptor
                    handle<io.ygdrasil.webgpu.GPURenderPipeline>("pipeline")
                }
                "createBuffer" -> {
                    val descriptor = args?.first() as BufferDescriptor
                    val label = when {
                        "index" in descriptor.label -> "index"
                        "draw-uniforms" in descriptor.label -> "draw-uniforms"
                        "material-uniforms" in descriptor.label -> "material-uniforms"
                        else -> "vertex"
                    }
                    val buffer = handle<GPUBuffer>(label)
                    bufferRecords += BufferRecord(buffer, descriptor, label)
                    buffer
                }
                "createBindGroup" -> handle<GPUBindGroup>("bind-group")
                else -> null
            }
        },
    )

    fun createdHandles(): List<Any> = handlesByLabel.values.flatten()
}
