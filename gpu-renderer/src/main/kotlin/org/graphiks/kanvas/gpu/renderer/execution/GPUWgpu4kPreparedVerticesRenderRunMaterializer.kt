package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.GPUVertexStepMode
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesCanonicalizationIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderProgram
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUPreparedVerticesBatchingCounter
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUPreparedVerticesBatchingCounters

/** Size in bytes of the prepared-vertices draw uniform (transform + target size). */
private const val PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES = 64

/** Prepared-route scope constants for barrier axes that live on other materializer seams. */
private const val PREPARED_VERTICES_DESTINATION_READ_CLASS = "none"
private const val PREPARED_VERTICES_FILTER_COMPOSITE_SCOPE = "none"
private const val PREPARED_VERTICES_SAMPLED_RESOURCE_SCOPE = "none"

/** Shared alignment of every packed prepared-vertices subrange. */
private const val PREPARED_VERTICES_PACK_ALIGNMENT = 4L

/**
 * Materializes one accepted prepared-vertices run into frame-owned buffers, one target-bound
 * render scope, and transferable completion owners.
 *
 * Vertex/index buffers and bind groups are frame-owned completion operands. The pipeline and
 * its layout entries are session-owned: they are never completion operands and remain in the
 * run owner ledger. All completion owners transfer only after successful submission; on
 * failure every acquired owner closes once in reverse creation order.
 *
 * When batching is enabled, compatible adjacent draws share one packed vertex/index buffer
 * allocation and one pipeline emission; every draw keeps its exact first vertex, base index,
 * and base vertex while addressing its checked, aligned, non-overlapping packed subrange.
 * Barriers split the adjacency and never reorder draws. When batching is disabled the run's
 * command and upload emission is identical to the accepted unbatched path; native handle
 * creation order may differ only in handle-independent steps.
 */
internal class GPUWgpu4kPreparedVerticesRenderRunMaterializer(
    private val device: GPUDevice,
    private val batchingEnabled: Boolean = true,
    private val countersObserver: ((GPUPreparedVerticesBatchingCounters) -> Unit)? = null,
) {
    fun materializeAcceptedRun(
        plan: GPUPreparedVerticesRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
        targetViewOperand: GPUPreparedNativeTextureViewOperand,
    ): GPUPreparedRenderRunMaterialization {
        plan.packets.firstOrNull { packet -> packet.material.sampledResources.isNotEmpty() }
            ?.let { packet ->
                return GPUPreparedRenderRunMaterialization.Refused(
                    code = "unsupported.prepared-vertices.sampled-material",
                    message = "Prepared-vertices material ${packet.materialIdentity} samples " +
                        "frame resources that require the sampled-material materializer.",
                    facts = mapOf("boundary" to "native"),
                )
            }
        val created = mutableListOf<AutoCloseable>()
        return try {
            val artifactByKey = plan.packets
                .map(GPUDrawSemanticPayload.Vertices::artifact)
                .distinctBy { artifact -> artifact.key }
                .associateBy { artifact -> artifact.key }
            val drawFactsByPacketId = plan.drawFacts.associateBy { fact -> fact.packetId }
            val entries = plan.renderStep.drawPackets.map { drawPacket ->
                PreparedVerticesPacketEntry(
                    drawPacket = drawPacket,
                    packet = drawPacket.semanticPayload as GPUDrawSemanticPayload.Vertices,
                    fact = requireNotNull(drawFactsByPacketId[drawPacket.packetId]) {
                        "A prepared-vertices packet must retain its exact draw facts"
                    },
                    program = requireNotNull(plan.shaderProgramByPacketId[drawPacket.packetId]) {
                        "A prepared-vertices packet must retain its exact shader program"
                    },
                )
            }
            val uniformUploads = mutableListOf<GPUPreparedNativeBufferUpload>()
            val pipelineByKey = linkedMapOf<String, PreparedVerticesPipelineSet>()
            var bufferCreationCount = 0L
            var setPipelineEmissions = 0L
            var batches: List<GPUPreparedVerticesBatch> = emptyList()
            val commands = buildList {
                if (batchingEnabled) {
                    val batchingPlan = GPUPreparedVerticesBatchingPlanner().plan(
                        entries.map(PreparedVerticesPacketEntry::toBatchCandidate),
                    )
                    batches = batchingPlan.batches
                    batchingPlan.batches.forEach { batch ->
                        emitPreparedVerticesBatch(
                            batch = batch,
                            entriesByPacketId = entries.associateBy { entry -> entry.drawPacket.packetId },
                            plan = plan,
                            actualDeviceGeneration = actualDeviceGeneration,
                            artifactByKey = artifactByKey,
                            pipelineByKey = pipelineByKey,
                            uniformUploads = uniformUploads,
                            created = created,
                            bufferCreationCount = { bufferCreationCount += 1L },
                            setPipelineEmissions = { setPipelineEmissions += 1L },
                        )
                    }
                } else {
                    emitUnbatchedPreparedVertices(
                        entries = entries,
                        plan = plan,
                        actualDeviceGeneration = actualDeviceGeneration,
                        artifactByKey = artifactByKey,
                        pipelineByKey = pipelineByKey,
                        uniformUploads = uniformUploads,
                        created = created,
                        bufferCreationCount = { bufferCreationCount += 1L },
                        setPipelineEmissions = { setPipelineEmissions += 1L },
                    )
                }
            }
            val render = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = plan.sourceScopeIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = targetViewOperand,
                    loadOperation = when (plan.renderStep.loadStore.loadOp) {
                        "clear" -> GPUPreparedNativeLoadOperation.Clear
                        "load" -> GPUPreparedNativeLoadOperation.Load
                        else -> error("Unsupported prepared-vertices load operation")
                    },
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                        .takeIf { plan.renderStep.loadStore.loadOp == "clear" },
                ),
                commands = commands,
                semanticPayloads = plan.packets.map<
                    GPUDrawSemanticPayload.Vertices,
                    GPUDrawSemanticPayload,
                    > { it },
            )
            val scopeOperands = immutableList(listOf(render))
            runCatching {
                countersObserver?.invoke(
                    preparedVerticesBatchingCounters(
                        entries = entries,
                        batches = batches,
                        uniformUploads = uniformUploads,
                        commands = commands,
                        bufferCreations = bufferCreationCount,
                        pipelineCreations = pipelineByKey.size.toLong(),
                        pipelineReuses = (setPipelineEmissions - pipelineByKey.size).coerceAtLeast(0L),
                        encoderScopes = scopeOperands.size.toLong(),
                    ),
                )
            }
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = scopeOperands,
                uniformUploads = immutableList(uniformUploads),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            failedPreparedVerticesMaterialization(created, failure)
        }
    }

    private fun MutableList<GPUPreparedNativeRenderCommand>.emitUnbatchedPreparedVertices(
        entries: List<PreparedVerticesPacketEntry>,
        plan: GPUPreparedVerticesRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
        artifactByKey: Map<String, GPUPreparedVerticesUploadArtifact>,
        pipelineByKey: MutableMap<String, PreparedVerticesPipelineSet>,
        uniformUploads: MutableList<GPUPreparedNativeBufferUpload>,
        created: MutableList<AutoCloseable>,
        bufferCreationCount: () -> Unit,
        setPipelineEmissions: () -> Unit,
    ) {
        val bufferByArtifactKey = linkedMapOf<String, PreparedVerticesBufferSet>()
        plan.resourcePlans.forEach { resourcePlan ->
            val artifact = requireNotNull(artifactByKey[resourcePlan.artifactKey]) {
                "A prepared-vertices resource plan must retain its exact immutable artifact"
            }
            val vertexBuffer = createBuffer(
                size = resourcePlan.vertexBuffer.byteCount,
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = "Kanvas.frame.preparedVertices.vertex.${resourcePlan.artifactKey}",
            ).track(created)
            bufferCreationCount()
            val indexBuffer = resourcePlan.indexBuffer?.let { index ->
                createBuffer(
                    size = alignedFourBytes(index.byteCount),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.preparedVertices.index.${resourcePlan.artifactKey}",
                ).track(created)
                    .also { bufferCreationCount() }
            }
            bufferByArtifactKey[resourcePlan.artifactKey] = PreparedVerticesBufferSet(
                artifact = artifact,
                vertexBuffer = GPUPreparedNativeBufferOperand(
                    vertexBuffer,
                    actualDeviceGeneration,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    byteCapacity = resourcePlan.vertexBuffer.byteCount,
                ),
                indexBuffer = indexBuffer?.let { buffer ->
                    GPUPreparedNativeBufferOperand(
                        buffer,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        byteCapacity = alignedFourBytes(
                            requireNotNull(resourcePlan.indexBuffer).byteCount,
                        ),
                    )
                },
            )
        }
        bufferByArtifactKey.values.forEach { buffers ->
            uniformUploads += preparedVerticesBufferUpload(
                role = "vertex",
                bytes = buffers.artifact.vertexBytesForUpload(),
                destination = buffers.vertexBuffer,
                destinationLabel = "vertex.${buffers.artifact.key}",
                renderScopeIndices = listOf(plan.sourceScopeIndex),
            )
            buffers.indexBuffer?.let { indexBuffer ->
                val indexBytes = requireNotNull(buffers.artifact.indexBytesForUpload())
                uniformUploads += preparedVerticesBufferUpload(
                    role = "index",
                    bytes = indexBytes.paddedToFourBytes(),
                    destination = indexBuffer,
                    destinationLabel = "index.${buffers.artifact.key}",
                    renderScopeIndices = listOf(plan.sourceScopeIndex),
                )
            }
        }
        entries.forEach { entry ->
            val pipelineSet = pipelineByKey.getOrPut(entry.program.pipelineKeyHash) {
                createPipelineSet(
                    program = entry.program,
                    packet = entry.packet,
                    blendState = requireFixedFunctionBlend(plan, entry.packet),
                    created = created,
                )
            }
            add(
                GPUPreparedNativeRenderCommand.SetPipeline(
                    GPUPreparedNativeRenderPipelineOperand(
                        pipelineSet.pipeline,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                ),
            )
            setPipelineEmissions()
            val buffers = requireNotNull(bufferByArtifactKey[entry.packet.artifact.key]) {
                "A prepared-vertices packet must retain its exact artifact buffers"
            }
            emitPreparedVerticesPacket(
                entry = entry,
                plan = plan,
                actualDeviceGeneration = actualDeviceGeneration,
                pipelineSet = pipelineSet,
                vertexBuffer = buffers.vertexBuffer,
                vertexOffset = 0L,
                vertexSize = entry.fact.vertexByteCount,
                indexBuffer = buffers.indexBuffer,
                indexOffset = 0L,
                indexSize = requireNotNull(entry.fact.indexByteCount),
                created = created,
                uniformUploads = uniformUploads,
                bufferCreationCount = bufferCreationCount,
            )
        }
    }

    private fun MutableList<GPUPreparedNativeRenderCommand>.emitPreparedVerticesBatch(
        batch: GPUPreparedVerticesBatch,
        entriesByPacketId: Map<GPUDrawPacketID, PreparedVerticesPacketEntry>,
        plan: GPUPreparedVerticesRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
        artifactByKey: Map<String, GPUPreparedVerticesUploadArtifact>,
        pipelineByKey: MutableMap<String, PreparedVerticesPipelineSet>,
        uniformUploads: MutableList<GPUPreparedNativeBufferUpload>,
        created: MutableList<AutoCloseable>,
        bufferCreationCount: () -> Unit,
        setPipelineEmissions: () -> Unit,
    ) {
        val vertexPackBuffer = createBuffer(
            size = batch.vertexPack.totalBytes,
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            label = "Kanvas.frame.preparedVertices.packed.vertex.${batch.batchIndex}",
        ).track(created)
        bufferCreationCount()
        val vertexPackOperand = GPUPreparedNativeBufferOperand(
            vertexPackBuffer,
            actualDeviceGeneration,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            byteCapacity = batch.vertexPack.totalBytes,
        )
        val indexPackBuffer = batch.indexPack?.let { pack ->
            createBuffer(
                size = alignedFourBytes(pack.totalBytes),
                usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                label = "Kanvas.frame.preparedVertices.packed.index.${batch.batchIndex}",
            ).track(created)
                .also { bufferCreationCount() }
        }
        val indexPackOperand = indexPackBuffer?.let { buffer ->
            GPUPreparedNativeBufferOperand(
                buffer,
                actualDeviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                byteCapacity = alignedFourBytes(requireNotNull(batch.indexPack).totalBytes),
            )
        }
        batch.vertexPack.subranges.forEach { subrange ->
            val artifact = requireNotNull(artifactByKey[subrange.artifactKey]) {
                "A packed prepared-vertices subrange must retain its exact immutable artifact"
            }
            uniformUploads += preparedVerticesBufferUpload(
                role = "vertex",
                bytes = artifact.vertexBytesForUpload(),
                destination = vertexPackOperand,
                destinationLabel = "packed.vertex.${batch.batchIndex}.${subrange.artifactKey}",
                renderScopeIndices = listOf(plan.sourceScopeIndex),
                destinationOffset = subrange.offsetBytes,
            )
        }
        batch.indexPack?.subranges?.forEach { subrange ->
            val artifact = requireNotNull(artifactByKey[subrange.artifactKey]) {
                "A packed prepared-vertices subrange must retain its exact immutable artifact"
            }
            uniformUploads += preparedVerticesBufferUpload(
                role = "index",
                bytes = requireNotNull(artifact.indexBytesForUpload()).paddedToFourBytes(),
                destination = requireNotNull(indexPackOperand),
                destinationLabel = "packed.index.${batch.batchIndex}.${subrange.artifactKey}",
                renderScopeIndices = listOf(plan.sourceScopeIndex),
                destinationOffset = subrange.offsetBytes,
            )
        }
        val firstEntry = requireNotNull(entriesByPacketId[batch.packetIds.first()]) {
            "A prepared-vertices batch must retain its exact first packet"
        }
        val pipelineSet = pipelineByKey.getOrPut(batch.pipelineKeyHash) {
            createPipelineSet(
                program = firstEntry.program,
                packet = firstEntry.packet,
                blendState = requireFixedFunctionBlend(plan, firstEntry.packet),
                created = created,
            )
        }
        batch.packetIds.forEach { packetId ->
            val entry = requireNotNull(entriesByPacketId[packetId]) {
                "A prepared-vertices batch must retain its exact packet"
            }
            // One SetPipeline per packet: the facade contract
            // (expectedFacadeOperations) and the pass command stream both emit
            // one SetRenderPipeline per packet, so a batched packet emits its
            // own SetPipeline command even when the batch shares one pipeline.
            add(
                GPUPreparedNativeRenderCommand.SetPipeline(
                    GPUPreparedNativeRenderPipelineOperand(
                        pipelineSet.pipeline,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                ),
            )
            setPipelineEmissions()
            val vertexSubrange = batch.vertexPack.subranges.single { subrange ->
                subrange.artifactKey == entry.packet.artifact.key
            }
            val indexSubrange = batch.indexPack?.subranges?.singleOrNull { subrange ->
                subrange.artifactKey == entry.packet.artifact.key
            }
            emitPreparedVerticesPacket(
                entry = entry,
                plan = plan,
                actualDeviceGeneration = actualDeviceGeneration,
                pipelineSet = pipelineSet,
                vertexBuffer = vertexPackOperand,
                vertexOffset = vertexSubrange.offsetBytes,
                vertexSize = vertexSubrange.byteCount,
                indexBuffer = indexPackOperand,
                indexOffset = indexSubrange?.offsetBytes ?: 0L,
                indexSize = indexSubrange?.byteCount ?: 0L,
                created = created,
                uniformUploads = uniformUploads,
                bufferCreationCount = bufferCreationCount,
            )
        }
    }

    private fun MutableList<GPUPreparedNativeRenderCommand>.emitPreparedVerticesPacket(
        entry: PreparedVerticesPacketEntry,
        plan: GPUPreparedVerticesRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
        pipelineSet: PreparedVerticesPipelineSet,
        vertexBuffer: GPUPreparedNativeBufferOperand,
        vertexOffset: Long,
        vertexSize: Long,
        indexBuffer: GPUPreparedNativeBufferOperand?,
        indexOffset: Long,
        indexSize: Long,
        created: MutableList<AutoCloseable>,
        uniformUploads: MutableList<GPUPreparedNativeBufferUpload>,
        bufferCreationCount: () -> Unit,
    ) {
        val packet = entry.packet
        val drawUniformBuffer = createBuffer(
            size = PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES.toLong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            label = "Kanvas.frame.preparedVertices.draw-uniforms." +
                "${packet.payloadRef.commandIdValue}",
        ).track(created)
        bufferCreationCount()
        uniformUploads += preparedVerticesBufferUpload(
            role = "draw-uniforms",
            bytes = preparedVerticesDrawUniformBytes(packet),
            destination = GPUPreparedNativeBufferOperand(
                drawUniformBuffer,
                actualDeviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                byteCapacity = PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES.toLong(),
            ),
            destinationLabel = "draw-uniforms.${packet.payloadRef.commandIdValue}",
            renderScopeIndices = listOf(plan.sourceScopeIndex),
        )
        val materialUniformBuffer = packet.material.uniformBytes
            .takeIf(List<Int>::isNotEmpty)
            ?.let { uniformBytes ->
                val buffer = createBuffer(
                    size = uniformBytes.size.toLong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.preparedVertices.material-uniforms." +
                        "${packet.payloadRef.commandIdValue}",
                ).track(created)
                bufferCreationCount()
                uniformUploads += preparedVerticesBufferUpload(
                    role = "material-uniforms",
                    bytes = uniformBytes.map(Int::toByte).toByteArray(),
                    destination = GPUPreparedNativeBufferOperand(
                        buffer,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        byteCapacity = uniformBytes.size.toLong(),
                    ),
                    destinationLabel =
                        "material-uniforms.${packet.payloadRef.commandIdValue}",
                    renderScopeIndices = listOf(plan.sourceScopeIndex),
                )
                buffer
            }
        val drawGroup = device.createBindGroup(
            BindGroupDescriptor(
                label = "Kanvas.frame.preparedVertices.draw-group." +
                    "${packet.payloadRef.commandIdValue}",
                layout = pipelineSet.drawBindGroupLayout,
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = BufferBinding(
                            buffer = drawUniformBuffer,
                            offset = 0uL,
                            size = PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES.toULong(),
                        ),
                    ),
                ),
            ),
        ).track(created)
        val materialGroup = device.createBindGroup(
            BindGroupDescriptor(
                label = "Kanvas.frame.preparedVertices.material-group." +
                    "${packet.payloadRef.commandIdValue}",
                layout = pipelineSet.materialBindGroupLayout,
                entries = buildList {
                    packet.material.composableFragment.uniformBinding?.let { uniform ->
                        add(
                            BindGroupEntry(
                                binding = uniform.binding.toUInt(),
                                resource = BufferBinding(
                                    buffer = requireNotNull(materialUniformBuffer),
                                    offset = 0uL,
                                    size = uniform.minBindingSizeBytes.toULong(),
                                ),
                            ),
                        )
                    }
                },
            ),
        ).track(created)
        add(
            GPUPreparedNativeRenderCommand.SetBindGroup(
                0,
                GPUPreparedNativeBindGroupOperand(
                    drawGroup,
                    actualDeviceGeneration,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )
        add(
            GPUPreparedNativeRenderCommand.SetBindGroup(
                1,
                GPUPreparedNativeBindGroupOperand(
                    materialGroup,
                    actualDeviceGeneration,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )
        add(
            GPUPreparedNativeRenderCommand.SetVertexBuffer(
                slot = 0,
                buffer = vertexBuffer,
                offset = vertexOffset,
                size = vertexSize,
                vertexStrideBytes = packet.artifact.layout.strideBytes.toLong(),
            ),
        )
        if (entry.fact.indexCount != null && indexBuffer != null) {
            add(
                GPUPreparedNativeRenderCommand.SetIndexBuffer(
                    buffer = indexBuffer,
                    format = requireNotNull(entry.fact.indexFormat)
                        .toPreparedVerticesNativeIndexFormat(),
                    offset = indexOffset,
                    size = indexSize,
                ),
            )
        }
        add(
            GPUPreparedNativeRenderCommand.SetScissor(
                packet.scissorBounds.left,
                packet.scissorBounds.top,
                packet.scissorBounds.width,
                packet.scissorBounds.height,
            ),
        )
        if (entry.fact.indexCount == null) {
            add(
                GPUPreparedNativeRenderCommand.Draw(
                    GPUPreparedNativeDrawCall.Draw(
                        vertexCount = entry.fact.vertexCount,
                        instanceCount = 1,
                        firstVertex = 0,
                        firstInstance = 0,
                    ),
                ),
            )
        } else {
            add(
                GPUPreparedNativeRenderCommand.DrawIndexed(
                    GPUPreparedNativeDrawCall.DrawIndexed(
                        indexCount = entry.fact.indexCount,
                        instanceCount = 1,
                        firstIndex = 0,
                        baseVertex = 0,
                        firstInstance = 0,
                    ),
                ),
            )
        }
    }

    private fun createPipelineSet(
        program: GPUPreparedVerticesShaderProgram,
        packet: GPUDrawSemanticPayload.Vertices,
        blendState: GPUFixedFunctionBlendState,
        created: MutableList<AutoCloseable>,
    ): PreparedVerticesPipelineSet {
        val layout = packet.artifact.layout
        val drawLayout = device.createBindGroupLayout(
            BindGroupLayoutDescriptor(
                label = "Kanvas.frame.preparedVertices.drawLayout",
                entries = listOf(
                    BindGroupLayoutEntry(
                        binding = 0u,
                        visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                        buffer = BufferBindingLayout(
                            type = GPUBufferBindingType.Uniform,
                            hasDynamicOffset = false,
                            minBindingSize = PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES.toULong(),
                        ),
                    ),
                ),
            ),
        ).track(created)
        val materialEntries = buildList {
            packet.material.composableFragment.uniformBinding?.let { uniform ->
                add(
                    BindGroupLayoutEntry(
                        binding = uniform.binding.toUInt(),
                        visibility = GPUShaderStage.Fragment,
                        buffer = BufferBindingLayout(
                            type = GPUBufferBindingType.Uniform,
                            hasDynamicOffset = false,
                            minBindingSize = uniform.minBindingSizeBytes.toULong(),
                        ),
                    ),
                )
            }
        }
        val materialLayout = device.createBindGroupLayout(
            BindGroupLayoutDescriptor(
                label = "Kanvas.frame.preparedVertices.materialLayout",
                entries = materialEntries,
            ),
        ).track(created)
        val shader = device.createShaderModule(
            ShaderModuleDescriptor(
                label = "Kanvas.frame.preparedVertices.shader.${program.pipelineKeyHash}",
                code = program.wgslSource,
            ),
        ).track(created)
        val pipelineLayout = device.createPipelineLayout(
            PipelineLayoutDescriptor(
                label = "Kanvas.frame.preparedVertices.pipelineLayout",
                bindGroupLayouts = listOf(drawLayout, materialLayout),
            ),
        ).track(created)
        val pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                label = "Kanvas.frame.preparedVertices.pipeline.${program.pipelineKeyHash}",
                layout = pipelineLayout,
                vertex = VertexState(
                    module = shader,
                    entryPoint = program.vertexEntryPoint,
                    buffers = listOf(
                        VertexBufferLayout(
                            arrayStride = layout.strideBytes.toULong(),
                            stepMode = GPUVertexStepMode.Vertex,
                            attributes = layout.attributes.map { attribute ->
                                VertexAttribute(
                                    format = attribute.toPreparedVerticesVertexFormat(),
                                    offset = layout.offsets.getValue(attribute).toULong(),
                                    shaderLocation =
                                        layout.shaderLocations.getValue(attribute).toUInt(),
                                )
                            },
                        ),
                    ),
                ),
                primitive = PrimitiveState(
                    topology = when (packet.topologyIdentity.sourceLabel) {
                        "Triangles" -> GPUPrimitiveTopology.TriangleList
                        "TriangleStrip" -> GPUPrimitiveTopology.TriangleStrip
                        else -> error("Unsupported prepared-vertices topology")
                    },
                ),
                fragment = FragmentState(
                    module = shader,
                    entryPoint = program.fragmentEntryPoint,
                    targets = listOf(
                        ColorTargetState(
                            format = packet.targetFormat.toPreparedVerticesTargetFormat(),
                            blend = blendState.toPreparedVerticesBlendState(),
                            writeMask = blendState.toPreparedVerticesWriteMask(),
                        ),
                    ),
                ),
            ),
        ).track(created)
        return PreparedVerticesPipelineSet(
            drawBindGroupLayout = drawLayout,
            materialBindGroupLayout = materialLayout,
            pipelineLayout = pipelineLayout,
            pipeline = pipeline,
            shader = shader,
        )
    }

    private fun createBuffer(
        size: Long,
        usage: GPUBufferUsage,
        label: String,
    ): GPUBuffer = device.createBuffer(
        BufferDescriptor(
            size = size.toULong(),
            usage = usage,
            mappedAtCreation = false,
            label = label,
        ),
    )

    private data class PreparedVerticesBufferSet(
        val artifact: GPUPreparedVerticesUploadArtifact,
        val vertexBuffer: GPUPreparedNativeBufferOperand,
        val indexBuffer: GPUPreparedNativeBufferOperand?,
    )

    private data class PreparedVerticesPipelineSet(
        val drawBindGroupLayout: GPUBindGroupLayout,
        val materialBindGroupLayout: GPUBindGroupLayout,
        val pipelineLayout: GPUPipelineLayout,
        val pipeline: GPURenderPipeline,
        val shader: GPUShaderModule,
    )
}

/** One packet with its exact draw facts and shader program, in plan order. */
internal data class PreparedVerticesPacketEntry(
    val drawPacket: GPUDrawPacket,
    val packet: GPUDrawSemanticPayload.Vertices,
    val fact: GPUPreparedVerticesDrawFacts,
    val program: GPUPreparedVerticesShaderProgram,
) {
    /** Derives the deterministic batching candidate for this packet. */
    fun toBatchCandidate(): GPUPreparedVerticesBatchCandidate = GPUPreparedVerticesBatchCandidate(
        packetId = drawPacket.packetId,
        artifactKey = packet.artifact.key,
        pipelineKeyHash = program.pipelineKeyHash,
        vertexLayoutHash = program.vertexLayoutHash,
        topology = packet.artifact.topology.sourceLabel,
        materialAbiHash = packet.materialIdentity,
        targetFormat = packet.targetFormat,
        indexFormat = fact.indexFormat,
        vertexByteCount = fact.vertexByteCount,
        indexByteCount = fact.indexByteCount,
        primitiveBlendIdentity = packet.primitiveBlendIdentity,
        finalBlendIdentity = packet.finalBlendIdentity,
        clipIdentity = packet.clipIdentity,
        layerId = drawPacket.layerId,
        destinationReadClass = PREPARED_VERTICES_DESTINATION_READ_CLASS,
        filterCompositeScope = PREPARED_VERTICES_FILTER_COMPOSITE_SCOPE,
        sampledResourceScope = PREPARED_VERTICES_SAMPLED_RESOURCE_SCOPE,
        commandOrderBand = drawPacket.insertionReasonCode,
    )
}

/** Deterministic identity of one prepared-vertices draw for the FP-06 batching planner. */
internal data class GPUPreparedVerticesBatchCandidate(
    val packetId: GPUDrawPacketID,
    val artifactKey: String,
    val pipelineKeyHash: String,
    val vertexLayoutHash: String,
    val topology: String,
    val materialAbiHash: String,
    val targetFormat: String,
    val indexFormat: String?,
    val vertexByteCount: Long,
    val indexByteCount: Long?,
    val primitiveBlendIdentity: String?,
    val finalBlendIdentity: String,
    val clipIdentity: String,
    val layerId: String,
    val destinationReadClass: String,
    val filterCompositeScope: String,
    val sampledResourceScope: String,
    val commandOrderBand: String,
) {
    init {
        require(packetId.value.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.packetId must not be blank"
        }
        require(artifactKey.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.artifactKey must not be blank"
        }
        require(pipelineKeyHash.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.pipelineKeyHash must not be blank"
        }
        require(vertexLayoutHash.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.vertexLayoutHash must not be blank"
        }
        require(topology.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.topology must not be blank"
        }
        require(materialAbiHash.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.materialAbiHash must not be blank"
        }
        require(targetFormat.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.targetFormat must not be blank"
        }
        require(vertexByteCount > 0L) {
            "GPUPreparedVerticesBatchCandidate.vertexByteCount must be positive"
        }
        require((indexFormat == null) == (indexByteCount == null)) {
            "GPUPreparedVerticesBatchCandidate index facts must be carried together"
        }
        require(indexByteCount == null || indexByteCount > 0L) {
            "GPUPreparedVerticesBatchCandidate.indexByteCount must be positive when indexed"
        }
        require(finalBlendIdentity.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.finalBlendIdentity must not be blank"
        }
        require(clipIdentity.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.clipIdentity must not be blank"
        }
        require(layerId.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.layerId must not be blank"
        }
        require(destinationReadClass.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.destinationReadClass must not be blank"
        }
        require(filterCompositeScope.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.filterCompositeScope must not be blank"
        }
        require(sampledResourceScope.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.sampledResourceScope must not be blank"
        }
        require(commandOrderBand.isNotBlank()) {
            "GPUPreparedVerticesBatchCandidate.commandOrderBand must not be blank"
        }
    }
}

/** Closed split reasons emitted by the prepared-route batching planner. */
internal enum class GPUPreparedVerticesBatchSplit {
    PipelineMismatch,
    LayoutMismatch,
    TopologyMismatch,
    MaterialAbiMismatch,
    TargetFormatMismatch,
    IndexFormatMismatch,
    ClipChange,
    DestinationRead,
    LayerBoundary,
    FilterCompositeBoundary,
    SampledResourceUpload,
    IncompatibleBlend,
    ExplicitCommandOrder,
    ;

    /** Stable split label conforming to the vertices batching contract taxonomy. */
    fun label(): String = when (this) {
        PipelineMismatch -> "pipeline"
        LayoutMismatch -> "layout"
        TopologyMismatch -> "topology"
        MaterialAbiMismatch -> "material-abi"
        TargetFormatMismatch -> "target-format"
        IndexFormatMismatch -> "index-format"
        IncompatibleBlend -> "incompatible-blend"
        ClipChange -> "clip-change"
        LayerBoundary -> "layer-boundary"
        DestinationRead -> "destination-read"
        FilterCompositeBoundary -> "filter-composite-boundary"
        SampledResourceUpload -> "sampled-resource-upload"
        ExplicitCommandOrder -> "explicit-command-order"
    }
}

/** One checked, 4-byte-aligned byte subrange inside a packed prepared-vertices buffer. */
internal data class GPUPreparedVerticesPackedSubrange(
    val artifactKey: String,
    val offsetBytes: Long,
    val byteCount: Long,
) {
    init {
        require(artifactKey.isNotBlank()) {
            "GPUPreparedVerticesPackedSubrange.artifactKey must not be blank"
        }
        require(offsetBytes >= 0L && offsetBytes % PREPARED_VERTICES_PACK_ALIGNMENT == 0L) {
            "GPUPreparedVerticesPackedSubrange.offsetBytes must be 4-byte aligned"
        }
        require(byteCount > 0L) {
            "GPUPreparedVerticesPackedSubrange.byteCount must be positive"
        }
    }
}

/** Checked packed layout of one buffer kind inside one prepared-vertices batch. */
internal data class GPUPreparedVerticesPackedLayout(
    val bufferKind: String,
    val totalBytes: Long,
    val subranges: List<GPUPreparedVerticesPackedSubrange>,
) {
    init {
        require(bufferKind == "vertex" || bufferKind == "index") {
            "GPUPreparedVerticesPackedLayout.bufferKind must be vertex or index"
        }
        require(totalBytes > 0L) {
            "GPUPreparedVerticesPackedLayout.totalBytes must be positive"
        }
        require(subranges.isNotEmpty()) {
            "GPUPreparedVerticesPackedLayout.subranges must not be empty"
        }
        subranges.zipWithNext().forEach { (left, right) ->
            require(checkedPackAdd(left.offsetBytes, left.byteCount) <= right.offsetBytes) {
                "GPUPreparedVerticesPackedLayout subranges must not overlap"
            }
        }
        require(checkedPackAdd(subranges.last().offsetBytes, subranges.last().byteCount) <=
            totalBytes
        ) {
            "GPUPreparedVerticesPackedLayout subranges must fit the packed byte count"
        }
    }
}

/** One compatible adjacency run with its exact packed subranges. */
internal data class GPUPreparedVerticesBatch(
    val batchIndex: Int,
    val pipelineKeyHash: String,
    val packetIds: List<GPUDrawPacketID>,
    val packetArtifactKeys: List<String>,
    val vertexPack: GPUPreparedVerticesPackedLayout,
    val indexPack: GPUPreparedVerticesPackedLayout?,
) {
    init {
        require(batchIndex >= 0) {
            "GPUPreparedVerticesBatch.batchIndex must not be negative"
        }
        require(pipelineKeyHash.isNotBlank()) {
            "GPUPreparedVerticesBatch.pipelineKeyHash must not be blank"
        }
        require(packetIds.isNotEmpty() && packetIds.size == packetArtifactKeys.size) {
            "GPUPreparedVerticesBatch must retain one exact artifact key per packet"
        }
    }

    /** Returns the exact artifact key of one packet in this batch. */
    fun artifactKeyFor(packetId: GPUDrawPacketID): String {
        val index = packetIds.indexOf(packetId)
        require(index >= 0) { "GPUPreparedVerticesBatch does not contain packet $packetId" }
        return packetArtifactKeys[index]
    }
}

/** One adjacency split decision between two consecutive prepared-vertices draws. */
internal data class GPUPreparedVerticesBatchSplitDecision(
    val beforePacketId: GPUDrawPacketID,
    val afterPacketId: GPUDrawPacketID,
    val reason: GPUPreparedVerticesBatchSplit,
)

/** Deterministic batching result: ordered batches and their exact split reasons. */
internal data class GPUPreparedVerticesBatchingPlan(
    val batches: List<GPUPreparedVerticesBatch>,
    val splitReasons: List<GPUPreparedVerticesBatchSplitDecision>,
) {
    init {
        require(batches.isNotEmpty()) {
            "GPUPreparedVerticesBatchingPlan requires at least one batch"
        }
        require(
            batches.flatMap { batch -> batch.packetIds } ==
                batches.flatMap { batch -> batch.packetIds }.distinct(),
        ) {
            "GPUPreparedVerticesBatchingPlan must retain every packet exactly once"
        }
    }
}

/**
 * Plans compatible prepared-vertices batching over an ordered adjacency.
 *
 * Two adjacent draws merge only when every compatibility axis matches and no barrier fires.
 * The planner never reorders draws and never merges across a barrier; each batch owns its
 * checked, aligned, non-overlapping packed subranges laid out in packet order.
 */
internal class GPUPreparedVerticesBatchingPlanner {
    fun plan(candidates: List<GPUPreparedVerticesBatchCandidate>): GPUPreparedVerticesBatchingPlan {
        require(candidates.isNotEmpty()) {
            "GPUPreparedVerticesBatchingPlanner requires at least one draw"
        }
        val splitReasons = mutableListOf<GPUPreparedVerticesBatchSplitDecision>()
        val batches = mutableListOf<GPUPreparedVerticesBatch>()
        var current = mutableListOf(candidates.first())
        candidates.zipWithNext().forEach { (previous, next) ->
            val reason = previous.splitReason(next)
            if (reason == null) {
                current += next
            } else {
                batches += current.toBatch(batches.size)
                splitReasons += GPUPreparedVerticesBatchSplitDecision(
                    beforePacketId = previous.packetId,
                    afterPacketId = next.packetId,
                    reason = reason,
                )
                current = mutableListOf(next)
            }
        }
        batches += current.toBatch(batches.size)
        return GPUPreparedVerticesBatchingPlan(batches = batches, splitReasons = splitReasons)
    }
}

private fun GPUPreparedVerticesBatchCandidate.splitReason(
    next: GPUPreparedVerticesBatchCandidate,
): GPUPreparedVerticesBatchSplit? = when {
    pipelineKeyHash != next.pipelineKeyHash -> GPUPreparedVerticesBatchSplit.PipelineMismatch
    vertexLayoutHash != next.vertexLayoutHash -> GPUPreparedVerticesBatchSplit.LayoutMismatch
    topology != next.topology -> GPUPreparedVerticesBatchSplit.TopologyMismatch
    materialAbiHash != next.materialAbiHash -> GPUPreparedVerticesBatchSplit.MaterialAbiMismatch
    targetFormat != next.targetFormat -> GPUPreparedVerticesBatchSplit.TargetFormatMismatch
    indexFormat != next.indexFormat -> GPUPreparedVerticesBatchSplit.IndexFormatMismatch
    primitiveBlendIdentity != next.primitiveBlendIdentity ||
        finalBlendIdentity != next.finalBlendIdentity -> GPUPreparedVerticesBatchSplit.IncompatibleBlend
    clipIdentity != next.clipIdentity -> GPUPreparedVerticesBatchSplit.ClipChange
    layerId != next.layerId -> GPUPreparedVerticesBatchSplit.LayerBoundary
    destinationReadClass != next.destinationReadClass -> GPUPreparedVerticesBatchSplit.DestinationRead
    filterCompositeScope != next.filterCompositeScope -> GPUPreparedVerticesBatchSplit.FilterCompositeBoundary
    sampledResourceScope != next.sampledResourceScope -> GPUPreparedVerticesBatchSplit.SampledResourceUpload
    commandOrderBand != next.commandOrderBand -> GPUPreparedVerticesBatchSplit.ExplicitCommandOrder
    else -> null
}

private fun List<GPUPreparedVerticesBatchCandidate>.toBatch(batchIndex: Int): GPUPreparedVerticesBatch {
    val vertexPack = packPreparedVerticesSubranges(candidates = this, bufferKind = "vertex") {
        candidate -> candidate.vertexByteCount
    }
    val indexPack = takeIf { candidates -> candidates.any { candidate -> candidate.indexFormat != null } }
        ?.let { candidates ->
            packPreparedVerticesSubranges(candidates = candidates, bufferKind = "index") {
                candidate -> requireNotNull(candidate.indexByteCount)
            }
        }
    return GPUPreparedVerticesBatch(
        batchIndex = batchIndex,
        pipelineKeyHash = first().pipelineKeyHash,
        packetIds = map { candidate -> candidate.packetId },
        packetArtifactKeys = map { candidate -> candidate.artifactKey },
        vertexPack = vertexPack,
        indexPack = indexPack,
    )
}

private fun packPreparedVerticesSubranges(
    candidates: List<GPUPreparedVerticesBatchCandidate>,
    bufferKind: String,
    byteCount: (GPUPreparedVerticesBatchCandidate) -> Long,
): GPUPreparedVerticesPackedLayout {
    var cursor = 0L
    val subranges = mutableListOf<GPUPreparedVerticesPackedSubrange>()
    candidates.forEach { candidate ->
        if (subranges.any { subrange -> subrange.artifactKey == candidate.artifactKey }) {
            return@forEach
        }
        val offset = alignPreparedVerticesPackUp(cursor)
        val size = byteCount(candidate)
        subranges += GPUPreparedVerticesPackedSubrange(
            artifactKey = candidate.artifactKey,
            offsetBytes = offset,
            byteCount = size,
        )
        cursor = checkedPackAdd(offset, size)
    }
    return GPUPreparedVerticesPackedLayout(
        bufferKind = bufferKind,
        totalBytes = cursor,
        subranges = subranges,
    )
}

private fun alignPreparedVerticesPackUp(value: Long): Long {
    require(value >= 0L) { "Prepared-vertices pack cursor must not be negative" }
    val remainder = value % PREPARED_VERTICES_PACK_ALIGNMENT
    return if (remainder == 0L) value else checkedPackAdd(value, PREPARED_VERTICES_PACK_ALIGNMENT - remainder)
}

private fun checkedPackAdd(left: Long, right: Long): Long = try {
    Math.addExact(left, right)
} catch (failure: ArithmeticException) {
    throw IllegalArgumentException("Prepared-vertices pack byte accounting overflowed", failure)
}

private fun preparedVerticesBatchingCounters(
    entries: List<PreparedVerticesPacketEntry>,
    batches: List<GPUPreparedVerticesBatch>,
    uniformUploads: List<GPUPreparedNativeBufferUpload>,
    commands: List<GPUPreparedNativeRenderCommand>,
    bufferCreations: Long,
    pipelineCreations: Long,
    pipelineReuses: Long,
    encoderScopes: Long,
): GPUPreparedVerticesBatchingCounters {
    val packedSubranges = batches.sumOf { batch ->
        batch.vertexPack.subranges.size + (batch.indexPack?.subranges?.size ?: 0)
    }
    return GPUPreparedVerticesBatchingCounters.of(
        mapOf(
            GPUPreparedVerticesBatchingCounter.DrawCount to entries.size.toLong(),
            GPUPreparedVerticesBatchingCounter.UniqueArtifacts to
                entries.map { entry -> entry.packet.artifact.key }.distinct().size.toLong(),
            GPUPreparedVerticesBatchingCounter.VertexBytes to
                uniformUploads.filter { upload -> upload.uploadRole == "vertex" }
                    .sumOf { upload -> upload.data.bytes().size.toLong() },
            GPUPreparedVerticesBatchingCounter.IndexBytes to
                uniformUploads.filter { upload -> upload.uploadRole == "index" }
                    .sumOf { upload -> upload.data.bytes().size.toLong() },
            GPUPreparedVerticesBatchingCounter.FanExpansion to
                entries.count { entry ->
                    entry.packet.artifact.canonicalizationIdentity ==
                        GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1
                }.toLong(),
            GPUPreparedVerticesBatchingCounter.BufferCreations to bufferCreations,
            GPUPreparedVerticesBatchingCounter.UploadCount to uniformUploads.size.toLong(),
            GPUPreparedVerticesBatchingCounter.UploadBytes to
                uniformUploads.sumOf { upload -> upload.data.bytes().size.toLong() },
            GPUPreparedVerticesBatchingCounter.PackedSubranges to packedSubranges.toLong(),
            GPUPreparedVerticesBatchingCounter.PipelineCreations to pipelineCreations,
            GPUPreparedVerticesBatchingCounter.PipelineReuses to pipelineReuses,
            GPUPreparedVerticesBatchingCounter.LayoutCreations to pipelineCreations * 3L,
            GPUPreparedVerticesBatchingCounter.LayoutReuses to pipelineReuses,
            GPUPreparedVerticesBatchingCounter.CompatibleBatches to
                batches.count { batch -> batch.packetIds.size >= 2 }.toLong(),
            GPUPreparedVerticesBatchingCounter.DrawCalls to
                commands.count { command -> command is GPUPreparedNativeRenderCommand.Draw }.toLong(),
            GPUPreparedVerticesBatchingCounter.DrawIndexedCalls to
                commands.count { command -> command is GPUPreparedNativeRenderCommand.DrawIndexed }.toLong(),
            GPUPreparedVerticesBatchingCounter.EncoderScopes to encoderScopes,
            GPUPreparedVerticesBatchingCounter.QueueSubmits to 0L,
            GPUPreparedVerticesBatchingCounter.Readbacks to 0L,
        ),
    )
}

private fun requireFixedFunctionBlend(
    plan: GPUPreparedVerticesRenderRunPlan,
    packet: GPUDrawSemanticPayload.Vertices,
): GPUFixedFunctionBlendState {
    val blendPlan = plan.renderStep.drawPackets
        .single { candidate -> candidate.commandIdValue == packet.payloadRef.commandIdValue }
        .blendPlan
    return when (blendPlan) {
        is GPUBlendPlan.FixedFunctionBlend -> blendPlan.state
        else -> throw IllegalArgumentException(
            "Prepared-vertices packet ${packet.payloadRef.commandIdValue} requires " +
                "an exact fixed-function blend plan",
        )
    }
}

private fun <T : AutoCloseable> T.track(handles: MutableList<AutoCloseable>): T =
    also(handles::add)

private fun preparedVerticesDrawUniformBytes(
    packet: GPUDrawSemanticPayload.Vertices,
): ByteArray {
    val values = packet.transformBytes.map(Float::fromBits)
    val buffer = ByteBuffer.allocate(PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    for (column in 0..2) {
        for (row in 0..2) {
            buffer.putFloat(values[row * 3 + column])
        }
        buffer.putInt(0)
    }
    buffer.putFloat(packet.targetBounds.width.toFloat())
    buffer.putFloat(packet.targetBounds.height.toFloat())
    buffer.putFloat(0f)
    buffer.putFloat(0f)
    return buffer.array()
}

private fun preparedVerticesBufferUpload(
    role: String,
    bytes: ByteArray,
    destination: GPUPreparedNativeBufferOperand,
    destinationLabel: String,
    renderScopeIndices: List<Int>,
    destinationOffset: Long = 0L,
): GPUPreparedNativeBufferUpload = GPUPreparedNativeBufferUpload(
    data = GPUPreparedNativeUploadData(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.UploadSource,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("prepared-vertices-upload-data:$destinationLabel"),
        ),
        bytes,
    ),
    destination = destination,
    destinationKey = GPUPreparedNativeOperandKey(
        GPUPreparedNativeOperandRole.UploadDestination,
        GPUPreparedNativeOperandKind.Buffer,
        gpuPreparedNativeBindingKey("prepared-vertices-buffer:$destinationLabel"),
    ),
    destinationOffset = destinationOffset,
    consumerSourceStepIndices = renderScopeIndices,
    uploadRole = role,
)

private fun String.toPreparedVerticesVertexFormat(): GPUVertexFormat = when (this) {
    "position", "texcoord" -> GPUVertexFormat.Float32x2
    "color" -> GPUVertexFormat.Unorm8x4
    else -> error("Unsupported prepared-vertices vertex attribute $this")
}

private fun String.toPreparedVerticesNativeIndexFormat(): GPUPreparedNativeIndexFormat =
    when (this) {
        "uint16" -> GPUPreparedNativeIndexFormat.Uint16
        "uint32" -> GPUPreparedNativeIndexFormat.Uint32
        else -> error("Unsupported prepared-vertices index format $this")
    }

private fun String.toPreparedVerticesTargetFormat(): GPUTextureFormat = when (this) {
    "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
    "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
    else -> error("Unsupported prepared-vertices target format: $this")
}

private fun GPUFixedFunctionBlendState.toPreparedVerticesBlendState():
    io.ygdrasil.webgpu.BlendState = io.ygdrasil.webgpu.BlendState(
    color = io.ygdrasil.webgpu.BlendComponent(
        operation = color.operation.toPreparedVerticesBlendOperation(),
        srcFactor = color.sourceFactor.toPreparedVerticesBlendFactor(),
        dstFactor = color.destinationFactor.toPreparedVerticesBlendFactor(),
    ),
    alpha = io.ygdrasil.webgpu.BlendComponent(
        operation = alpha.operation.toPreparedVerticesBlendOperation(),
        srcFactor = alpha.sourceFactor.toPreparedVerticesBlendFactor(),
        dstFactor = alpha.destinationFactor.toPreparedVerticesBlendFactor(),
    ),
)

private fun String.toPreparedVerticesBlendFactor(): GPUBlendFactor = when (this) {
    "zero" -> GPUBlendFactor.Zero
    "one" -> GPUBlendFactor.One
    "src" -> GPUBlendFactor.Src
    "one-minus-src" -> GPUBlendFactor.OneMinusSrc
    "dst" -> GPUBlendFactor.Dst
    "one-minus-dst" -> GPUBlendFactor.OneMinusDst
    "src-alpha" -> GPUBlendFactor.SrcAlpha
    "one-minus-src-alpha" -> GPUBlendFactor.OneMinusSrcAlpha
    "dst-alpha" -> GPUBlendFactor.DstAlpha
    "one-minus-dst-alpha" -> GPUBlendFactor.OneMinusDstAlpha
    "src-alpha-saturated" -> GPUBlendFactor.SrcAlphaSaturated
    "constant" -> GPUBlendFactor.Constant
    "one-minus-constant" -> GPUBlendFactor.OneMinusConstant
    else -> error("Unsupported prepared-vertices fixed-function blend factor: $this")
}

private fun String.toPreparedVerticesBlendOperation(): GPUBlendOperation = when (this) {
    "add" -> GPUBlendOperation.Add
    "reverse-subtract" -> GPUBlendOperation.ReverseSubtract
    else -> error("Unsupported prepared-vertices fixed-function blend operation: $this")
}

private fun GPUFixedFunctionBlendState.toPreparedVerticesWriteMask(): GPUColorWrite =
    when (writeMask) {
        "rgba" -> GPUColorWrite.All
        "none" -> GPUColorWrite.None
        else -> error("Unsupported prepared-vertices write mask: $writeMask")
    }

private fun failedPreparedVerticesMaterialization(
    handles: MutableList<AutoCloseable>,
    failure: Throwable,
): GPUPreparedRenderRunMaterialization.Refused {
    val owner = GPUPreparedRenderRunOwnedResources(handles)
    handles.clear()
    val rollbackFailure = runCatching(owner::close).exceptionOrNull()
    return GPUPreparedRenderRunMaterialization.Refused(
        code = "failed.prepared_vertices.materialization",
        message = "Prepared-vertices native materialization failed: " +
            "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}." +
            rollbackFailure?.let {
                " Rollback retained native ownership after close failure: " +
                    "${it::class.simpleName.orEmpty()}: ${it.message.orEmpty()}."
            }.orEmpty(),
        facts = mapOf("boundary" to "native"),
        retainedCloseOwner = owner.takeIf { rollbackFailure != null },
    )
}


/** Rounds a byte count up to the WebGPU 4-byte copy alignment. */
private fun alignedFourBytes(byteCount: Long): Long {
    require(byteCount >= 0L) { "Byte count must be non-negative" }
    val remainder = byteCount % 4L
    return if (remainder == 0L) byteCount else byteCount + (4L - remainder)
}

/** Pads raw bytes to a WebGPU 4-byte aligned copy size (zero fill). */
private fun ByteArray.paddedToFourBytes(): ByteArray {
    val remainder = size % 4
    if (remainder == 0) return this
    val padded = ByteArray(size + (4 - remainder))
    copyInto(padded)
    return padded
}
