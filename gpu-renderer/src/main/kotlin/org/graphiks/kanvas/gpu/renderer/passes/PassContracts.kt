package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

/** Stable render-step identifier. */
@JvmInline
value class GPURenderStepID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURenderStepID.value must not be blank" }
    }
}

/**
 * Draw invocation expanded from analysis and layer planning.
 *
 * The invocation owns only immutable planning evidence: command identity,
 * render-step selection, ordering, pipeline key, optional binding slots, and
 * bounds hashes. It must not claim concrete resources or backend submission.
 * `scissorBoundsHash` is present only when analysis accepted a simple device
 * rectangle clip; otherwise unsupported clips must have refused before pass
 * construction. This adapts Graphite's late clip/pass evidence idea into a
 * Kanvas-owned hash contract without importing Graphite task ownership.
 */
data class GPUDrawInvocation(
    val commandIdValue: Int,
    val analysisRecordId: String,
    val renderStepIndex: Int,
    val renderStepId: GPURenderStepID,
    val role: String,
    val layerScopeId: String,
    val sortKey: Long,
    val pipelineKeyHash: String,
    val uniformSlot: String? = null,
    val resourceSlot: String? = null,
    val boundsHash: String,
    val scissorBoundsHash: String? = null,
    val originalPaintOrder: Int,
)

/** Insertion decision for reordered or original-order draw invocations. */
data class GPUDrawInsertion(
    val drawLayerId: String,
    val bindingListId: String,
    val position: Int,
    val layerOrderBand: String,
    val dependencyClass: String,
    val commandIdValue: Int,
    val renderStepIndex: Int,
    val reasonCode: String,
)

/** Stable identifier for one pass-local draw packet. */
@JvmInline
value class GPUDrawPacketID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUDrawPacketID.value must not be blank" }
    }
}

/** Packet role used when lowering pass-local work into facade command order. */
enum class GPUDrawPacketRole {
    Shading,
    DepthOnly,
    StencilProducer,
    StencilConsumer,
    ClipProducer,
    Clear,
    Discard,
    Copy,
    Upload,
    Compute,
    Composite,
    Readback,
}

/**
 * Pass-local packet of executable planning evidence before backend command encoding.
 *
 * A packet snapshots the draw identity, render-step version, pipeline-key identity, payload slots,
 * sort evidence, and target state needed by a Dawn/WGPU-style encoder. It intentionally carries no
 * backend handles, concrete bind groups, command buffers, textures, or queue state.
 */
class GPUDrawPacket(
    val packetId: GPUDrawPacketID,
    val commandIdValue: Int,
    val analysisRecordId: String,
    val passId: String,
    val layerId: String,
    val bindingListId: String,
    val insertionReasonCode: String,
    val sortKey: Long,
    val sortKeyPreimage: String,
    val renderStepId: GPURenderStepID,
    val renderStepVersion: Int,
    val role: GPUDrawPacketRole,
    val renderPipelineKey: GPURenderPipelineKey? = null,
    val computePipelineKey: GPUComputePipelineKey? = null,
    val bindingLayoutHash: String,
    val uniformSlot: GPUUniformPayloadSlot? = null,
    val resourceSlot: GPUResourceBindingSlot? = null,
    val vertexSourceLabel: String,
    val scissorBoundsHash: String? = null,
    val targetStateHash: String,
    val originalPaintOrder: Int,
    val resourceGeneration: Long,
    diagnostics: List<GPUPassDiagnostic> = emptyList(),
) {
    /** Diagnostics copied from packet production so caller mutation cannot rewrite evidence. */
    val diagnostics: List<GPUPassDiagnostic> = diagnostics.toList()

    /** Diagnostic codes in packet-local order for compact dump and assertion surfaces. */
    val diagnosticCodes: List<String>
        get() = diagnostics.map { diagnostic -> diagnostic.code }

    /** Stable human-readable source label used by command-stream and PM evidence dumps. */
    val provenanceLabel: String
        get() = "draw-command:$commandIdValue:${renderStepId.value}"

    init {
        require(commandIdValue >= 0) { "GPUDrawPacket.commandIdValue must be non-negative" }
        require(analysisRecordId.isNotBlank()) { "GPUDrawPacket.analysisRecordId must not be blank" }
        require(passId.isNotBlank()) { "GPUDrawPacket.passId must not be blank" }
        require(layerId.isNotBlank()) { "GPUDrawPacket.layerId must not be blank" }
        require(bindingListId.isNotBlank()) { "GPUDrawPacket.bindingListId must not be blank" }
        require(insertionReasonCode.isNotBlank()) { "GPUDrawPacket.insertionReasonCode must not be blank" }
        require(sortKeyPreimage.isNotBlank()) { "GPUDrawPacket.sortKeyPreimage must not be blank" }
        require(renderStepVersion >= 0) { "GPUDrawPacket.renderStepVersion must be non-negative" }
        require(bindingLayoutHash.isNotBlank()) { "GPUDrawPacket.bindingLayoutHash must not be blank" }
        require(vertexSourceLabel.isNotBlank()) { "GPUDrawPacket.vertexSourceLabel must not be blank" }
        require(targetStateHash.isNotBlank()) { "GPUDrawPacket.targetStateHash must not be blank" }
        require(originalPaintOrder >= 0) { "GPUDrawPacket.originalPaintOrder must be non-negative" }
        require(resourceGeneration >= 0L) { "GPUDrawPacket.resourceGeneration must be non-negative" }
        requireRoleHasPipelineKey()
    }

    private fun requireRoleHasPipelineKey() {
        when (role) {
            GPUDrawPacketRole.Compute -> require(computePipelineKey != null) {
                "Compute GPUDrawPacket requires computePipelineKey"
            }

            GPUDrawPacketRole.Copy,
            GPUDrawPacketRole.Upload,
            GPUDrawPacketRole.Readback,
            GPUDrawPacketRole.Discard,
            -> Unit

            else -> require(renderPipelineKey != null) {
                "$role GPUDrawPacket requires renderPipelineKey"
            }
        }
    }
}

/**
 * Ordered packet stream for one draw pass before facade command lowering.
 *
 * The stream is pass-local and snapshots packets/diagnostics at construction so sort windows,
 * insertion decisions, and payload references can be audited independently of backend execution.
 */
class GPUDrawPacketStream(
    val streamId: String,
    val passId: String,
    packets: List<GPUDrawPacket>,
    diagnostics: List<GPUPassDiagnostic> = emptyList(),
) {
    /** Packets copied in stream order. */
    val packets: List<GPUDrawPacket> = packets.toList()

    /** Stream diagnostics copied before command lowering. */
    val diagnostics: List<GPUPassDiagnostic> = diagnostics.toList()

    /** Packet identifiers in stream order. */
    val packetIds: List<GPUDrawPacketID>
        get() = packets.map { packet -> packet.packetId }

    /** Original draw-command identifiers in stream order. */
    val commandIds: List<Int>
        get() = packets.map { packet -> packet.commandIdValue }

    /** Sort keys in stream order. */
    val sortKeys: List<Long>
        get() = packets.map { packet -> packet.sortKey }

    /** Distinct render pipeline keys referenced by packets in first-use order. */
    val renderPipelineKeys: List<GPURenderPipelineKey>
        get() = packets.mapNotNull { packet -> packet.renderPipelineKey }.distinct()

    /** Number of packets captured in this stream. */
    val packetCount: Int
        get() = packets.size

    init {
        require(streamId.isNotBlank()) { "GPUDrawPacketStream.streamId must not be blank" }
        require(passId.isNotBlank()) { "GPUDrawPacketStream.passId must not be blank" }
        require(packets.all { packet -> packet.passId == passId }) {
            "GPUDrawPacketStream packets must belong to passId $passId"
        }
    }
}

/** Dawn/WGPU-shaped facade command emitted from a packet stream before backend encoding. */
sealed interface GPUPassCommand {
    /** Stable operation label matching the facade call shape. */
    val commandLabel: String

    /** Packet that caused this command, or null for pass delimiters. */
    val sourcePacketId: GPUDrawPacketID?

    /** Starts a render pass for the packet stream target. */
    data class BeginRenderPass(
        val targetStateHash: String,
        val loadStoreLabel: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "beginRenderPass"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(targetStateHash.isNotBlank()) { "BeginRenderPass.targetStateHash must not be blank" }
            require(loadStoreLabel.isNotBlank()) { "BeginRenderPass.loadStoreLabel must not be blank" }
        }
    }

    /** Selects the render pipeline for one draw packet. */
    data class SetRenderPipeline(
        val pipelineKey: GPURenderPipelineKey,
        val packetId: GPUDrawPacketID,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "setRenderPipeline"
        override val sourcePacketId: GPUDrawPacketID get() = packetId
    }

    /** Binds pass-local uniform and resource slots for one draw packet. */
    data class SetBindGroup(
        val bindingLayoutHash: String,
        val uniformSlot: GPUUniformPayloadSlot?,
        val resourceSlot: GPUResourceBindingSlot?,
        val packetId: GPUDrawPacketID,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "setBindGroup"
        override val sourcePacketId: GPUDrawPacketID get() = packetId

        init {
            require(bindingLayoutHash.isNotBlank()) { "SetBindGroup.bindingLayoutHash must not be blank" }
        }
    }

    /** Applies the scissor evidence captured during analysis and pass construction. */
    data class SetScissor(
        val scissorBoundsHash: String,
        val packetId: GPUDrawPacketID,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "setScissor"
        override val sourcePacketId: GPUDrawPacketID get() = packetId

        init {
            require(scissorBoundsHash.isNotBlank()) { "SetScissor.scissorBoundsHash must not be blank" }
        }
    }

    /** Issues the draw call shape for one packet without owning a backend encoder. */
    data class Draw(
        val vertexSourceLabel: String,
        val packetId: GPUDrawPacketID,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "draw"
        override val sourcePacketId: GPUDrawPacketID get() = packetId

        init {
            require(vertexSourceLabel.isNotBlank()) { "Draw.vertexSourceLabel must not be blank" }
        }
    }

    /** Ends a render pass after all packet commands have been emitted. */
    data class EndRenderPass(val passId: String) : GPUPassCommand {
        override val commandLabel: String get() = "endRenderPass"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(passId.isNotBlank()) { "EndRenderPass.passId must not be blank" }
        }
    }
}

/**
 * Linearized pass command stream before a concrete command encoder records WGPU calls.
 *
 * The stream keeps Dawn-style operation order as Kanvas-owned evidence. It is still backend-neutral:
 * commands name pipeline keys, payload slots, and target-state hashes rather than concrete objects.
 */
class GPUPassCommandStream(
    val streamId: String,
    val packetStreamId: String,
    val passId: String,
    commands: List<GPUPassCommand>,
    diagnostics: List<GPUPassDiagnostic> = emptyList(),
) {
    /** Commands copied in facade call order. */
    val commands: List<GPUPassCommand> = commands.toList()

    /** Command-stream diagnostics copied before encoder planning. */
    val diagnostics: List<GPUPassDiagnostic> = diagnostics.toList()

    /** Facade operation labels in encoded order. */
    val commandLabels: List<String>
        get() = commands.map { command -> command.commandLabel }

    /** Packet identifiers that caused emitted commands, excluding pass delimiters. */
    val sourcePacketIds: List<GPUDrawPacketID>
        get() = commands.mapNotNull { command -> command.sourcePacketId }

    /** Number of commands in this stream. */
    val commandCount: Int
        get() = commands.size

    init {
        require(streamId.isNotBlank()) { "GPUPassCommandStream.streamId must not be blank" }
        require(packetStreamId.isNotBlank()) { "GPUPassCommandStream.packetStreamId must not be blank" }
        require(passId.isNotBlank()) { "GPUPassCommandStream.passId must not be blank" }
        require(commands.isNotEmpty()) { "GPUPassCommandStream.commands must not be empty" }
    }

    companion object {
        /** Lowers a draw packet stream to a first-route render pass command sequence. */
        fun fromDrawPacketStream(
            streamId: String,
            packetStream: GPUDrawPacketStream,
            targetStateHash: String,
            loadStoreLabel: String,
        ): GPUPassCommandStream {
            val commands = buildList {
                add(
                    GPUPassCommand.BeginRenderPass(
                        targetStateHash = targetStateHash,
                        loadStoreLabel = loadStoreLabel,
                    ),
                )
                for (packet in packetStream.packets) {
                    val renderPipelineKey = requireNotNull(packet.renderPipelineKey) {
                        "Packet ${packet.packetId.value} cannot be lowered to render commands without renderPipelineKey"
                    }
                    add(
                        GPUPassCommand.SetRenderPipeline(
                            pipelineKey = renderPipelineKey,
                            packetId = packet.packetId,
                        ),
                    )
                    add(
                        GPUPassCommand.SetBindGroup(
                            bindingLayoutHash = packet.bindingLayoutHash,
                            uniformSlot = packet.uniformSlot,
                            resourceSlot = packet.resourceSlot,
                            packetId = packet.packetId,
                        ),
                    )
                    packet.scissorBoundsHash?.let { scissorBoundsHash ->
                        add(
                            GPUPassCommand.SetScissor(
                                scissorBoundsHash = scissorBoundsHash,
                                packetId = packet.packetId,
                            ),
                        )
                    }
                    add(
                        GPUPassCommand.Draw(
                            vertexSourceLabel = packet.vertexSourceLabel,
                            packetId = packet.packetId,
                        ),
                    )
                }
                add(GPUPassCommand.EndRenderPass(passId = packetStream.passId))
            }

            return GPUPassCommandStream(
                streamId = streamId,
                packetStreamId = packetStream.streamId,
                passId = packetStream.passId,
                commands = commands,
                diagnostics = packetStream.diagnostics,
            )
        }
    }
}

/** Draw pass descriptor close to GPU submission. */
data class GPUDrawPass(
    val passId: String,
    val targetStateHash: String,
    val layerScopeId: String,
    val loadStoreLabel: String,
    val invocations: List<GPUDrawInvocation>,
    val pipelineKeys: List<String>,
    val barriers: List<String>,
    val diagnostics: List<GPUPassDiagnostic> = emptyList(),
)

/** Render-step contract for geometry and coverage execution. */
interface GPURenderStep {
    /** Stable step identifier. */
    val stepId: GPURenderStepID

    /** Step version included in pipeline-key preimages. */
    val version: Int

    /** Creates a non-executing render-step plan for an invocation. */
    fun planFor(invocation: GPUDrawInvocation): GPURenderStepPlan = TODO("Wire GPURenderStep planning to concrete geometry and coverage implementations")
}

/** Render-step plan selected before pipeline creation. */
data class GPURenderStepPlan(
    val stepId: GPURenderStepID,
    val geometryClass: String,
    val coverageClass: String,
    val vertexLayoutHash: String,
    val fixedStateHash: String,
    val wgslFragmentHash: String,
    val pipelineAxes: Map<String, String>,
)

/** Compute task descriptor for GPU-native preparation or filters. */
data class GPUComputeTask(
    val taskId: String,
    val programKeyHash: String,
    val dispatchShape: String,
    val bindingPlanHash: String,
    val dependencies: List<String>,
)

/** Copy task descriptor for destination reads or transfers. */
data class GPUCopyTask(
    val taskId: String,
    val sourceDescriptorHash: String,
    val destinationDescriptorHash: String,
    val boundsHash: String,
    val useTokenLabel: String,
)

/** Upload task descriptor for buffers, textures, or artifacts. */
data class GPUUploadTask(
    val taskId: String,
    val uploadPlanHash: String,
    val byteSize: Long,
    val stagingScope: String,
    val beforeUseToken: String,
    val budgetClass: String,
)

/** Legal sorting window for draw invocations. */
data class GPUSortWindow(
    val windowId: String,
    val firstPaintOrder: Int,
    val lastPaintOrder: Int,
    val allowedAxes: Set<String>,
    val barrierGeneration: Long,
    val closedReason: String? = null,
)

/** Diagnostic emitted by pass construction. */
data class GPUPassDiagnostic(
    val code: String,
    val passId: String? = null,
    val invocationId: String? = null,
    val terminal: Boolean,
)

/** Builds first-route pass descriptors whose contents remain pre-materialization planning records. */
object GPUFirstRoutePassBuilder {
    /**
     * Builds an accepted FillRect pass with invocation identity but no concrete resource or binding slots.
     *
     * Callers must pass only analysis-proven command and scissor bounds. A
     * non-null `scissorBoundsHash` means the invocation preserves a simple
     * device-rectangle clip for later backend encoding; unsupported clips must
     * use [refusedFillRect] instead.
     */
    fun acceptedFillRect(
        commandIdValue: Int,
        analysisRecordId: String,
        renderStepIdentity: String,
        sortKey: Long,
        pipelineKeyHash: String,
        boundsHash: String,
        scissorBoundsHash: String?,
        originalPaintOrder: Int,
        targetStateHash: String,
    ): GPUDrawPass {
        val invocation = GPUDrawInvocation(
            commandIdValue = commandIdValue,
            analysisRecordId = analysisRecordId,
            renderStepIndex = 0,
            renderStepId = GPURenderStepID(renderStepIdentity),
            role = "fill",
            layerScopeId = "root",
            sortKey = sortKey,
            pipelineKeyHash = pipelineKeyHash,
            uniformSlot = null,
            resourceSlot = null,
            boundsHash = boundsHash,
            scissorBoundsHash = scissorBoundsHash,
            originalPaintOrder = originalPaintOrder,
        )
        return GPUDrawPass(
            passId = "pass.root.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "load.store",
            invocations = listOf(invocation),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )
    }

    /** Builds an empty refused pass so unsupported inputs cannot produce executable draw work. */
    fun refusedFillRect(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass {
        val passId = "pass.refused.$commandIdValue"
        return GPUDrawPass(
            passId = passId,
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "refused",
            invocations = emptyList(),
            pipelineKeys = emptyList(),
            barriers = emptyList(),
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = code,
                    passId = passId,
                    terminal = true,
                ),
            ),
        )
    }

    /** Builds an accepted FillRRect pass with rrect render-step identity but no concrete resources. */
    fun acceptedFillRRect(
        commandIdValue: Int,
        analysisRecordId: String,
        renderStepIdentity: String,
        sortKey: Long,
        pipelineKeyHash: String,
        boundsHash: String,
        scissorBoundsHash: String?,
        originalPaintOrder: Int,
        targetStateHash: String,
    ): GPUDrawPass =
        acceptedFillRect(
            commandIdValue = commandIdValue,
            analysisRecordId = analysisRecordId,
            renderStepIdentity = renderStepIdentity,
            sortKey = sortKey,
            pipelineKeyHash = pipelineKeyHash,
            boundsHash = boundsHash,
            scissorBoundsHash = scissorBoundsHash,
            originalPaintOrder = originalPaintOrder,
            targetStateHash = targetStateHash,
        )

    /** Builds an empty refused FillRRect pass so unsupported rrects cannot produce draw work. */
    fun refusedFillRRect(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass =
        refusedFillRect(
            commandIdValue = commandIdValue,
            targetStateHash = targetStateHash,
            code = code,
        )
}
