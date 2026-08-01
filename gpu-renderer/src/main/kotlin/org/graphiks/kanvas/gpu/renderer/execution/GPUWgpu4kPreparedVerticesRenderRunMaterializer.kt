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
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderProgram
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Size in bytes of the prepared-vertices draw uniform (transform + target size). */
private const val PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES = 64

/**
 * Materializes one accepted prepared-vertices run into frame-owned buffers, one target-bound
 * render scope, and transferable completion owners.
 *
 * Vertex/index buffers and bind groups are frame-owned completion operands. The pipeline and
 * its layout entries are session-owned: they are never completion operands and remain in the
 * run owner ledger. All completion owners transfer only after successful submission; on
 * failure every acquired owner closes once in reverse creation order.
 */
internal class GPUWgpu4kPreparedVerticesRenderRunMaterializer(
    private val device: GPUDevice,
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
                val indexBuffer = resourcePlan.indexBuffer?.let { index ->
                    createBuffer(
                        size = index.byteCount,
                        usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.preparedVertices.index.${resourcePlan.artifactKey}",
                    ).track(created)
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
                            byteCapacity = requireNotNull(resourcePlan.indexBuffer).byteCount,
                        )
                    },
                )
            }
            val uniformUploads = mutableListOf<GPUPreparedNativeBufferUpload>()
            bufferByArtifactKey.values.forEach { buffers ->
                uniformUploads += preparedVerticesBufferUpload(
                    role = "vertex",
                    bytes = buffers.artifact.vertexBytesForUpload(),
                    destination = buffers.vertexBuffer,
                    destinationLabel = "vertex.${buffers.artifact.key}",
                    renderScopeIndices = listOf(plan.sourceScopeIndex),
                )
                buffers.indexBuffer?.let { indexBuffer ->
                    uniformUploads += preparedVerticesBufferUpload(
                        role = "index",
                        bytes = requireNotNull(buffers.artifact.indexBytesForUpload()),
                        destination = indexBuffer,
                        destinationLabel = "index.${buffers.artifact.key}",
                        renderScopeIndices = listOf(plan.sourceScopeIndex),
                    )
                }
            }

            val drawFactsByPacketId = plan.drawFacts.associateBy { fact -> fact.packetId }
            val pipelineByKey = linkedMapOf<String, PreparedVerticesPipelineSet>()
            val commands = buildList {
                plan.packets.forEach { packet ->
                    val packetId = plan.renderStep.drawPackets.single { candidate ->
                        candidate.commandIdValue == packet.payloadRef.commandIdValue
                    }.packetId
                    val fact = requireNotNull(drawFactsByPacketId[packetId]) {
                        "A prepared-vertices packet must retain its exact draw facts"
                    }
                    val buffers = requireNotNull(bufferByArtifactKey[packet.artifact.key]) {
                        "A prepared-vertices packet must retain its exact artifact buffers"
                    }
                    val drawUniformBuffer = createBuffer(
                        size = PREPARED_VERTICES_DRAW_UNIFORM_SIZE_BYTES.toLong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.preparedVertices.draw-uniforms." +
                            "${packet.payloadRef.commandIdValue}",
                    ).track(created)
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
                    val program = requireNotNull(
                        plan.shaderProgramByPacketId[packetId],
                    ) {
                        "A prepared-vertices packet must retain its exact shader program"
                    }
                    val pipelineSet = pipelineByKey.getOrPut(program.pipelineKeyHash) {
                        createPipelineSet(
                            program = program,
                            packet = packet,
                            blendState = requireFixedFunctionBlend(plan, packet),
                            created = created,
                        )
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
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            GPUPreparedNativeRenderPipelineOperand(
                                pipelineSet.pipeline,
                                actualDeviceGeneration,
                                GPUPreparedNativeOperandOwnership.Borrowed,
                            ),
                        ),
                    )
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
                            buffer = buffers.vertexBuffer,
                            offset = 0L,
                            size = fact.vertexByteCount,
                            vertexStrideBytes = packet.artifact.layout.strideBytes.toLong(),
                        ),
                    )
                    buffers.indexBuffer?.let { indexBuffer ->
                        add(
                            GPUPreparedNativeRenderCommand.SetIndexBuffer(
                                buffer = indexBuffer,
                                format = requireNotNull(fact.indexFormat)
                                    .toPreparedVerticesNativeIndexFormat(),
                                offset = 0L,
                                size = requireNotNull(fact.indexByteCount),
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
                    if (fact.indexCount == null) {
                        add(
                            GPUPreparedNativeRenderCommand.Draw(
                                GPUPreparedNativeDrawCall.Draw(
                                    vertexCount = fact.vertexCount,
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
                                    indexCount = fact.indexCount,
                                    instanceCount = 1,
                                    firstIndex = 0,
                                    baseVertex = 0,
                                    firstInstance = 0,
                                ),
                            ),
                        )
                    }
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
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = immutableList(listOf(render)),
                uniformUploads = immutableList(uniformUploads),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            failedPreparedVerticesMaterialization(created, failure)
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
    destinationOffset = 0L,
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
