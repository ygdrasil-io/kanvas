package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.dumpCommandOperandFields

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

/**
 * One render-step vertex or instance attribute before backend buffer allocation.
 *
 * Attribute facts are layout evidence only. They name WGSL-visible value classes and byte ranges,
 * but do not own concrete buffers, mapped memory, or backend vertex-state objects.
 */
data class GPURenderStepAttribute(
    val name: String,
    val valueClass: String,
    val sourceClass: String,
    val byteOffset: Long,
    val byteSize: Long,
) {
    init {
        require(name.isNotBlank()) { "GPURenderStepAttribute.name must not be blank" }
        require(valueClass.isNotBlank()) { "GPURenderStepAttribute.valueClass must not be blank" }
        require(sourceClass.isNotBlank()) { "GPURenderStepAttribute.sourceClass must not be blank" }
        require(byteOffset >= 0L) { "GPURenderStepAttribute.byteOffset must be non-negative" }
        require(byteSize > 0L) { "GPURenderStepAttribute.byteSize must be positive" }
    }
}

/**
 * Vertex and instance layout metadata for a render step.
 *
 * Static and append attributes are copied at construction so caller mutation cannot rewrite
 * pipeline-key preimage evidence after the render-step descriptor is built.
 */
class GPURenderStepVertexLayout(
    val layoutHash: String,
    val primitiveTopology: String,
    staticAttributes: List<GPURenderStepAttribute>,
    appendAttributes: List<GPURenderStepAttribute>,
    val instanceStepRate: String,
) {
    /** Static vertex attributes copied in declaration order. */
    val staticAttributes: List<GPURenderStepAttribute> = staticAttributes.toList()

    /** Append or instance attributes copied in declaration order. */
    val appendAttributes: List<GPURenderStepAttribute> = appendAttributes.toList()

    /** Static attribute names in declaration order. */
    val staticAttributeNames: List<String>
        get() = staticAttributes.map { attribute -> attribute.name }

    /** Append attribute names in declaration order. */
    val appendAttributeNames: List<String>
        get() = appendAttributes.map { attribute -> attribute.name }

    init {
        require(layoutHash.isNotBlank()) { "GPURenderStepVertexLayout.layoutHash must not be blank" }
        require(primitiveTopology.isNotBlank()) {
            "GPURenderStepVertexLayout.primitiveTopology must not be blank"
        }
        require(instanceStepRate.isNotBlank()) { "GPURenderStepVertexLayout.instanceStepRate must not be blank" }
    }
}

/**
 * Payload and bind-group layout metadata required by a render step.
 *
 * This contract names layout hashes and dynamic-offset policy only. Concrete bind groups, buffers,
 * and offsets remain owned by resource materialization and command encoding.
 */
data class GPURenderStepPayloadLayout(
    val bindingLayoutHash: String,
    val uniformLayoutHash: String,
    val resourceLayoutHash: String? = null,
    val dynamicOffsetPolicy: String,
) {
    init {
        require(bindingLayoutHash.isNotBlank()) {
            "GPURenderStepPayloadLayout.bindingLayoutHash must not be blank"
        }
        require(uniformLayoutHash.isNotBlank()) {
            "GPURenderStepPayloadLayout.uniformLayoutHash must not be blank"
        }
        require(resourceLayoutHash == null || resourceLayoutHash.isNotBlank()) {
            "GPURenderStepPayloadLayout.resourceLayoutHash must not be blank"
        }
        require(dynamicOffsetPolicy.isNotBlank()) {
            "GPURenderStepPayloadLayout.dynamicOffsetPolicy must not be blank"
        }
    }
}

/**
 * Fixed render-state metadata contributed by a render step.
 *
 * The state hash is suitable for pipeline-key preimages. The split hashes keep blend, sample,
 * depth/stencil, target, and coverage-function evidence reviewable without backend objects.
 */
data class GPURenderStepFixedState(
    val stateHash: String,
    val blendStateHash: String,
    val sampleStateHash: String,
    val depthStencilStateHash: String,
    val targetStateClass: String,
    val coverageFunctionIdentity: String,
) {
    init {
        require(stateHash.isNotBlank()) { "GPURenderStepFixedState.stateHash must not be blank" }
        require(blendStateHash.isNotBlank()) { "GPURenderStepFixedState.blendStateHash must not be blank" }
        require(sampleStateHash.isNotBlank()) { "GPURenderStepFixedState.sampleStateHash must not be blank" }
        require(depthStencilStateHash.isNotBlank()) {
            "GPURenderStepFixedState.depthStencilStateHash must not be blank"
        }
        require(targetStateClass.isNotBlank()) { "GPURenderStepFixedState.targetStateClass must not be blank" }
        require(coverageFunctionIdentity.isNotBlank()) {
            "GPURenderStepFixedState.coverageFunctionIdentity must not be blank"
        }
    }
}

/**
 * Backend-neutral descriptor for one concrete render step.
 *
 * A descriptor is the bridge between route analysis and pipeline-key construction. It records
 * topology, vertex/append layout, payload ABI, fixed state, WGSL entry points, and diagnostics
 * without creating shader modules, buffers, pipelines, or command encoders.
 */
class GPURenderStepDescriptor(
    val stepId: GPURenderStepID,
    val version: Int,
    val geometryClass: String,
    val coverageClass: String,
    val vertexLayout: GPURenderStepVertexLayout,
    val payloadLayout: GPURenderStepPayloadLayout,
    val fixedState: GPURenderStepFixedState,
    val wgslVertexEntryPoint: String,
    val wgslFragmentEntryPoint: String,
    val wgslModuleHash: String,
    diagnostics: List<GPUPassDiagnostic> = emptyList(),
) {
    /** Diagnostics copied from descriptor production. */
    val diagnostics: List<GPUPassDiagnostic> = diagnostics.toList()

    init {
        require(version >= 0) { "GPURenderStepDescriptor.version must be non-negative" }
        require(geometryClass.isNotBlank()) { "GPURenderStepDescriptor.geometryClass must not be blank" }
        require(coverageClass.isNotBlank()) { "GPURenderStepDescriptor.coverageClass must not be blank" }
        require(wgslVertexEntryPoint.isNotBlank()) {
            "GPURenderStepDescriptor.wgslVertexEntryPoint must not be blank"
        }
        require(wgslFragmentEntryPoint.isNotBlank()) {
            "GPURenderStepDescriptor.wgslFragmentEntryPoint must not be blank"
        }
        require(wgslModuleHash.isNotBlank()) { "GPURenderStepDescriptor.wgslModuleHash must not be blank" }
    }

    /** Projects this descriptor into the existing render-step plan contract. */
    fun toPlan(): GPURenderStepPlan =
        GPURenderStepPlan(
            stepId = stepId,
            geometryClass = geometryClass,
            coverageClass = coverageClass,
            vertexLayoutHash = vertexLayout.layoutHash,
            fixedStateHash = fixedState.stateHash,
            wgslFragmentHash = "$wgslModuleHash#$wgslFragmentEntryPoint",
            pipelineAxes = mapOf(
                "bindingLayoutHash" to payloadLayout.bindingLayoutHash,
                "coverageFunctionIdentity" to fixedState.coverageFunctionIdentity,
                "fragmentEntryPoint" to wgslFragmentEntryPoint,
                "primitiveTopology" to vertexLayout.primitiveTopology,
                "targetStateClass" to fixedState.targetStateClass,
                "vertexEntryPoint" to wgslVertexEntryPoint,
            ),
        )
}

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

    /** Copies a texture region before later commands sample it. */
    data class CopyTexture(
        val sourceLabel: String,
        val destinationLabel: String,
        val boundsLabel: String,
        val tokenLabel: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "copyTexture"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(sourceLabel.isNotBlank()) { "CopyTexture.sourceLabel must not be blank" }
            require(destinationLabel.isNotBlank()) { "CopyTexture.destinationLabel must not be blank" }
            require(boundsLabel.isNotBlank()) { "CopyTexture.boundsLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "CopyTexture.tokenLabel must not be blank" }
        }
    }

    /** Binds an intermediate texture for a later shader blend or composite draw. */
    data class BindIntermediate(
        val textureLabel: String,
        val bindingLabel: String,
        val layoutHash: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "bindIntermediate"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(textureLabel.isNotBlank()) { "BindIntermediate.textureLabel must not be blank" }
            require(bindingLabel.isNotBlank()) { "BindIntermediate.bindingLabel must not be blank" }
            require(layoutHash.isNotBlank()) { "BindIntermediate.layoutHash must not be blank" }
        }
    }

    /** Materializes or reuses an offscreen layer target before rendering children. */
    data class PrepareLayerTarget(
        val targetLabel: String,
        val descriptorHash: String,
        val usageLabel: String,
        val byteEstimate: Long,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "prepareLayerTarget"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(targetLabel.isNotBlank()) { "PrepareLayerTarget.targetLabel must not be blank" }
            require(descriptorHash.isNotBlank()) { "PrepareLayerTarget.descriptorHash must not be blank" }
            require(usageLabel.isNotBlank()) { "PrepareLayerTarget.usageLabel must not be blank" }
            require(byteEstimate >= 0L) { "PrepareLayerTarget.byteEstimate must be non-negative" }
        }
    }

    /** Materializes or reuses a generic intermediate texture before copy, layer, filter, or MSAA work. */
    data class PrepareIntermediateTexture(
        val textureLabel: String,
        val purposeLabel: String,
        val descriptorHash: String,
        val usageLabel: String,
        val sampleCount: Int,
        val byteEstimate: Long,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "prepareIntermediateTexture"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(textureLabel.isNotBlank()) { "PrepareIntermediateTexture.textureLabel must not be blank" }
            require(purposeLabel.isNotBlank()) { "PrepareIntermediateTexture.purposeLabel must not be blank" }
            require(descriptorHash.isNotBlank()) { "PrepareIntermediateTexture.descriptorHash must not be blank" }
            require(usageLabel.isNotBlank()) { "PrepareIntermediateTexture.usageLabel must not be blank" }
            require(sampleCount > 0) { "PrepareIntermediateTexture.sampleCount must be positive" }
            require(byteEstimate >= 0L) { "PrepareIntermediateTexture.byteEstimate must be non-negative" }
        }
    }

    /** Clears an isolated layer target before child rendering. */
    data class ClearLayerTarget(
        val targetLabel: String,
        val clearPolicy: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "clearLayerTarget"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(targetLabel.isNotBlank()) { "ClearLayerTarget.targetLabel must not be blank" }
            require(clearPolicy.isNotBlank()) { "ClearLayerTarget.clearPolicy must not be blank" }
        }
    }

    /** Materializes or reuses a pass-local depth/stencil attachment before stencil-cover work. */
    data class PrepareStencilAttachment(
        val attachmentLabel: String,
        val descriptorHash: String,
        val formatLabel: String,
        val usageLabel: String,
        val sampleCount: Int,
        val byteEstimate: Long,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "prepareStencilAttachment"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(attachmentLabel.isNotBlank()) { "PrepareStencilAttachment.attachmentLabel must not be blank" }
            require(descriptorHash.isNotBlank()) { "PrepareStencilAttachment.descriptorHash must not be blank" }
            require(formatLabel.isNotBlank()) { "PrepareStencilAttachment.formatLabel must not be blank" }
            require(usageLabel.isNotBlank()) { "PrepareStencilAttachment.usageLabel must not be blank" }
            require(sampleCount > 0) { "PrepareStencilAttachment.sampleCount must be positive" }
            require(byteEstimate >= 0L) { "PrepareStencilAttachment.byteEstimate must be non-negative" }
        }
    }

    /** Clears pass-local stencil state before the producer writes coverage. */
    data class ClearStencilAttachment(
        val attachmentLabel: String,
        val clearValue: Int,
        val loadStorePolicy: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "clearStencilAttachment"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(attachmentLabel.isNotBlank()) { "ClearStencilAttachment.attachmentLabel must not be blank" }
            require(loadStorePolicy.isNotBlank()) { "ClearStencilAttachment.loadStorePolicy must not be blank" }
        }
    }

    /** Emits the bounded path stencil producer command before the cover consumer. */
    data class StencilCoverProducer(
        val attachmentLabel: String,
        val pipelineLabel: String,
        val boundsLabel: String,
        val stencilStateLabel: String,
        val tokenLabel: String,
        val packetId: GPUDrawPacketID,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "stencilCoverProducer"
        override val sourcePacketId: GPUDrawPacketID get() = packetId

        init {
            require(attachmentLabel.isNotBlank()) { "StencilCoverProducer.attachmentLabel must not be blank" }
            require(pipelineLabel.isNotBlank()) { "StencilCoverProducer.pipelineLabel must not be blank" }
            require(boundsLabel.isNotBlank()) { "StencilCoverProducer.boundsLabel must not be blank" }
            require(stencilStateLabel.isNotBlank()) { "StencilCoverProducer.stencilStateLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "StencilCoverProducer.tokenLabel must not be blank" }
        }
    }

    /** Emits the cover draw command that consumes producer-written stencil state. */
    data class StencilCoverDraw(
        val attachmentLabel: String,
        val pipelineLabel: String,
        val boundsLabel: String,
        val compareLabel: String,
        val writeMaskLabel: String,
        val tokenLabel: String,
        val packetId: GPUDrawPacketID,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "stencilCoverDraw"
        override val sourcePacketId: GPUDrawPacketID get() = packetId

        init {
            require(attachmentLabel.isNotBlank()) { "StencilCoverDraw.attachmentLabel must not be blank" }
            require(pipelineLabel.isNotBlank()) { "StencilCoverDraw.pipelineLabel must not be blank" }
            require(boundsLabel.isNotBlank()) { "StencilCoverDraw.boundsLabel must not be blank" }
            require(compareLabel.isNotBlank()) { "StencilCoverDraw.compareLabel must not be blank" }
            require(writeMaskLabel.isNotBlank()) { "StencilCoverDraw.writeMaskLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "StencilCoverDraw.tokenLabel must not be blank" }
        }
    }

    /** Renders layer children into the isolated target scope. */
    data class RenderLayerChildren(
        val scopeLabel: String,
        val targetLabel: String,
        val childrenLabel: String,
        val tokenLabel: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "renderLayerChildren"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(scopeLabel.isNotBlank()) { "RenderLayerChildren.scopeLabel must not be blank" }
            require(targetLabel.isNotBlank()) { "RenderLayerChildren.targetLabel must not be blank" }
            require(childrenLabel.isNotBlank()) { "RenderLayerChildren.childrenLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "RenderLayerChildren.tokenLabel must not be blank" }
        }
    }

    /** Composites an isolated layer source back into its parent target. */
    data class CompositeLayer(
        val sourceLabel: String,
        val parentTargetLabel: String,
        val blendModeLabel: String,
        val routeLabel: String,
        val tokenLabel: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "compositeLayer"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(sourceLabel.isNotBlank()) { "CompositeLayer.sourceLabel must not be blank" }
            require(parentTargetLabel.isNotBlank()) { "CompositeLayer.parentTargetLabel must not be blank" }
            require(blendModeLabel.isNotBlank()) { "CompositeLayer.blendModeLabel must not be blank" }
            require(routeLabel.isNotBlank()) { "CompositeLayer.routeLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "CompositeLayer.tokenLabel must not be blank" }
        }
    }

    /** Resolves a multisample intermediate into a single-sample texture before sampling or presentation. */
    data class ResolveMSAA(
        val sourceLabel: String,
        val destinationLabel: String,
        val strategyLabel: String,
        val tokenLabel: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "resolveMSAA"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(sourceLabel.isNotBlank()) { "ResolveMSAA.sourceLabel must not be blank" }
            require(destinationLabel.isNotBlank()) { "ResolveMSAA.destinationLabel must not be blank" }
            require(strategyLabel.isNotBlank()) { "ResolveMSAA.strategyLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "ResolveMSAA.tokenLabel must not be blank" }
        }
    }

    /** Records a stable intermediate planning or materialization refusal. */
    data class RefuseIntermediate(
        val scopeLabel: String,
        val reasonCode: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "refuseIntermediate"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(scopeLabel.isNotBlank()) { "RefuseIntermediate.scopeLabel must not be blank" }
            require(reasonCode.isNotBlank()) { "RefuseIntermediate.reasonCode must not be blank" }
        }
    }

    /** Records a stable layer materialization refusal in command-stream evidence. */
    data class RefuseLayer(
        val scopeLabel: String,
        val reasonCode: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "refuseLayer"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(scopeLabel.isNotBlank()) { "RefuseLayer.scopeLabel must not be blank" }
            require(reasonCode.isNotBlank()) { "RefuseLayer.reasonCode must not be blank" }
        }
    }

    /** Records a stable stencil-cover materialization refusal in command-stream evidence. */
    data class RefuseStencilCover(
        val pathLabel: String,
        val reasonCode: String,
    ) : GPUPassCommand {
        override val commandLabel: String get() = "refuseStencilCover"
        override val sourcePacketId: GPUDrawPacketID? get() = null

        init {
            require(pathLabel.isNotBlank()) { "RefuseStencilCover.pathLabel must not be blank" }
            require(reasonCode.isNotBlank()) { "RefuseStencilCover.reasonCode must not be blank" }
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

/** Packet-to-command bridge for provider-materialized command operands. */
data class GPUPassCommandOperandBridge(
    val packetId: GPUDrawPacketID?,
    val commandLabel: String,
    val operand: GPUMaterializedCommandOperandReference,
) {
    init {
        require(commandLabel.isNotBlank()) { "GPUPassCommandOperandBridge.commandLabel must not be blank" }
    }

    companion object {
        /** Converts provider-owned string evidence into pass-local typed packet evidence. */
        fun fromMaterializedBinding(binding: GPUMaterializedCommandOperandBinding): GPUPassCommandOperandBridge =
            GPUPassCommandOperandBridge(
                packetId = binding.packetId?.let(::GPUDrawPacketID),
                commandLabel = binding.commandLabel,
                operand = binding.operand,
            )
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
    operandBridge: List<GPUPassCommandOperandBridge> = emptyList(),
) {
    /** Commands copied in facade call order. */
    val commands: List<GPUPassCommand> = commands.toList()

    /** Command-stream diagnostics copied before encoder planning. */
    val diagnostics: List<GPUPassDiagnostic> = diagnostics.toList()

    /** Provider-materialized packet-to-command operands copied before encoder planning. */
    val operandBridge: List<GPUPassCommandOperandBridge> = operandBridge.toList()

    /** Facade operation labels in encoded order. */
    val commandLabels: List<String>
        get() = commands.map { command -> command.commandLabel }

    /** Packet identifiers that caused emitted commands, excluding pass delimiters. */
    val sourcePacketIds: List<GPUDrawPacketID>
        get() = commands.mapNotNull { command -> command.sourcePacketId }

    /** Number of commands in this stream. */
    val commandCount: Int
        get() = commands.size

    /** Materialized operand labels in bridge order. */
    val materializedOperandLabels: List<String>
        get() = operandBridge.map { bridge -> bridge.operand.label }

    init {
        require(streamId.isNotBlank()) { "GPUPassCommandStream.streamId must not be blank" }
        require(packetStreamId.isNotBlank()) { "GPUPassCommandStream.packetStreamId must not be blank" }
        require(passId.isNotBlank()) { "GPUPassCommandStream.passId must not be blank" }
        require(commands.isNotEmpty()) { "GPUPassCommandStream.commands must not be empty" }
        val packetIds = sourcePacketIds.toSet()
        val commandKeys = commands.map { command -> command.sourcePacketId to command.commandLabel }.toSet()
        require(operandBridge.all { bridge -> bridge.packetId == null || bridge.packetId in packetIds }) {
            "GPUPassCommandStream operandBridge packets must belong to source packet ids"
        }
        require(operandBridge.all { bridge -> bridge.packetId to bridge.commandLabel in commandKeys }) {
            "GPUPassCommandStream operandBridge command labels must match the bridged packet"
        }
        require(operandBridge.all { bridge -> bridge.matchesCommandOperandKind() }) {
            "GPUPassCommandStream operandBridge operand kinds must match the bridged command"
        }
    }

    companion object {
        /** Lowers a draw packet stream to a first-route render pass command sequence. */
        fun fromDrawPacketStream(
            streamId: String,
            packetStream: GPUDrawPacketStream,
            targetStateHash: String,
            loadStoreLabel: String,
            materialization: GPUResourceMaterializationDecision.Materialized? = null,
            operandBridge: List<GPUPassCommandOperandBridge> = emptyList(),
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
            val materializedOperandBridge =
                materialization?.dumpOperandBridgeSnapshot
                    ?.map(GPUPassCommandOperandBridge::fromMaterializedBinding)
                    .orEmpty()
            require(materializedOperandBridge.isEmpty() || operandBridge.isEmpty()) {
                "GPUPassCommandStream accepts either provider materialization or explicit operandBridge, not both"
            }

            return GPUPassCommandStream(
                streamId = streamId,
                packetStreamId = packetStream.streamId,
                passId = packetStream.passId,
                commands = commands,
                diagnostics = packetStream.diagnostics,
                operandBridge = materializedOperandBridge.ifEmpty { operandBridge },
            )
        }
    }
}

/** Emits deterministic render-step evidence lines without backend objects. */
fun GPURenderStepDescriptor.dumpLines(): List<String> =
    listOf(
        "passes.render-step id=${stepId.value} " +
            "version=$version " +
            "geometry=$geometryClass " +
            "coverage=$coverageClass " +
            "topology=${vertexLayout.primitiveTopology} " +
            "vertexLayout=${vertexLayout.layoutHash} " +
            "payloadLayout=${payloadLayout.bindingLayoutHash} " +
            "fixedState=${fixedState.stateHash} " +
            "wgsl=$wgslModuleHash " +
            "vertexEntry=$wgslVertexEntryPoint " +
            "fragmentEntry=$wgslFragmentEntryPoint " +
            "diagnostics=${diagnostics.dumpCodes()}",
        "passes.render-step.attributes " +
            "static=${vertexLayout.staticAttributes.dumpAttributes()} " +
            "append=${vertexLayout.appendAttributes.dumpAttributes()}",
        "passes.render-step.payload " +
            "binding=${payloadLayout.bindingLayoutHash} " +
            "uniform=${payloadLayout.uniformLayoutHash} " +
            "resource=${payloadLayout.resourceLayoutHash ?: NONE_DUMP_VALUE} " +
            "dynamicOffsets=${payloadLayout.dynamicOffsetPolicy}",
        "passes.render-step.state " +
            "blend=${fixedState.blendStateHash} " +
            "sample=${fixedState.sampleStateHash} " +
            "depthStencil=${fixedState.depthStencilStateHash} " +
            "target=${fixedState.targetStateClass} " +
            "coverageFn=${fixedState.coverageFunctionIdentity}",
    ) + diagnostics.dumpLines()

/** Emits deterministic packet evidence lines for one pass-local packet. */
fun GPUDrawPacket.dumpLines(): List<String> =
    listOf(
        "passes.packet id=${packetId.value} " +
            "command=$commandIdValue " +
            "analysis=$analysisRecordId " +
            "pass=$passId " +
            "layer=$layerId " +
            "bindingList=$bindingListId " +
            "role=$role " +
            "step=${renderStepId.value}@$renderStepVersion " +
            "renderPipeline=${renderPipelineKey?.value ?: NONE_DUMP_VALUE} " +
            "computePipeline=${computePipelineKey?.value ?: NONE_DUMP_VALUE} " +
            "bindingLayout=$bindingLayoutHash " +
            "uniformSlot=${uniformSlot?.slotId?.value ?: NONE_DUMP_VALUE} " +
            "resourceSlot=${resourceSlot?.slotId?.value ?: NONE_DUMP_VALUE} " +
            "vertex=$vertexSourceLabel " +
            "scissor=${scissorBoundsHash ?: NONE_DUMP_VALUE} " +
            "target=$targetStateHash " +
            "order=$originalPaintOrder " +
            "resourceGeneration=$resourceGeneration " +
            "diagnostics=${diagnostics.dumpCodes()}",
        "passes.packet.sort id=${packetId.value} " +
            "sortKey=$sortKey " +
            "preimage=$sortKeyPreimage " +
            "insertion=$insertionReasonCode " +
            "provenance=$provenanceLabel",
    ) + diagnostics.dumpLines()

/** Emits deterministic packet-stream evidence in stream order. */
fun GPUDrawPacketStream.dumpLines(): List<String> =
    listOf(
        "passes.packet-stream id=$streamId " +
            "pass=$passId " +
            "packets=${packetIds.map { packetId -> packetId.value }.dumpSequence()} " +
            "commands=${commandIds.map { commandId -> commandId.toString() }.dumpSequence()} " +
            "sortKeys=${sortKeys.map { sortKey -> sortKey.toString() }.dumpSequence()} " +
            "pipelines=${renderPipelineKeys.map { pipelineKey -> pipelineKey.value }.dumpSequence()} " +
            "diagnostics=${diagnostics.dumpCodes()}",
    ) + packets.flatMap { packet -> packet.dumpLines() } + diagnostics.dumpLines()

/** Emits deterministic pass-command stream evidence before backend encoding. */
fun GPUPassCommandStream.dumpLines(): List<String> =
    listOf(
        "passes.command-stream id=$streamId " +
            "packetStream=$packetStreamId " +
            "pass=$passId " +
            "commands=${commandLabels.dumpSequence()} " +
            "packets=${sourcePacketIds.map { packetId -> packetId.value }.dumpSequence()} " +
            "diagnostics=${diagnostics.dumpCodes()}",
    ) +
        commands.map { command -> command.dumpLine() } +
        operandBridge.map { bridge -> bridge.dumpLine() } +
        diagnostics.dumpLines()

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

private const val NONE_DUMP_VALUE = "none"

private fun GPUPassCommand.dumpLine(): String =
    when (this) {
        is GPUPassCommand.BeginRenderPass ->
            "passes.command beginRenderPass target=$targetStateHash loadStore=$loadStoreLabel"
        is GPUPassCommand.SetRenderPipeline ->
            "passes.command setRenderPipeline packet=${packetId.value} pipeline=${pipelineKey.value}"
        is GPUPassCommand.SetBindGroup ->
            "passes.command setBindGroup " +
                "packet=${packetId.value} " +
                "bindingLayout=$bindingLayoutHash " +
                "uniformSlot=${uniformSlot?.slotId?.value ?: NONE_DUMP_VALUE} " +
                "resourceSlot=${resourceSlot?.slotId?.value ?: NONE_DUMP_VALUE}"
        is GPUPassCommand.SetScissor ->
            "passes.command setScissor packet=${packetId.value} scissor=$scissorBoundsHash"
        is GPUPassCommand.Draw ->
            "passes.command draw packet=${packetId.value} vertex=$vertexSourceLabel"
        is GPUPassCommand.CopyTexture ->
            "passes.command copyTexture source=$sourceLabel destination=$destinationLabel " +
                "bounds=$boundsLabel token=$tokenLabel"
        is GPUPassCommand.BindIntermediate ->
            "passes.command bindIntermediate texture=$textureLabel binding=$bindingLabel layout=$layoutHash"
        is GPUPassCommand.PrepareLayerTarget ->
            "passes.command prepareLayerTarget target=$targetLabel descriptor=$descriptorHash " +
                "usage=$usageLabel bytes=$byteEstimate"
        is GPUPassCommand.PrepareIntermediateTexture ->
            "passes.command prepareIntermediateTexture texture=$textureLabel purpose=$purposeLabel " +
                "descriptor=$descriptorHash usage=$usageLabel samples=$sampleCount bytes=$byteEstimate"
        is GPUPassCommand.ClearLayerTarget ->
            "passes.command clearLayerTarget target=$targetLabel clear=$clearPolicy"
        is GPUPassCommand.PrepareStencilAttachment ->
            "passes.command prepareStencilAttachment attachment=$attachmentLabel " +
                "descriptor=$descriptorHash format=$formatLabel usage=$usageLabel " +
                "samples=$sampleCount bytes=$byteEstimate"
        is GPUPassCommand.ClearStencilAttachment ->
            "passes.command clearStencilAttachment attachment=$attachmentLabel " +
                "clear=$clearValue loadStore=$loadStorePolicy"
        is GPUPassCommand.StencilCoverProducer ->
            "passes.command stencilCoverProducer attachment=$attachmentLabel " +
                "pipeline=$pipelineLabel bounds=$boundsLabel state=$stencilStateLabel " +
                "token=$tokenLabel packet=${packetId.value}"
        is GPUPassCommand.StencilCoverDraw ->
            "passes.command stencilCoverDraw attachment=$attachmentLabel " +
                "pipeline=$pipelineLabel bounds=$boundsLabel compare=$compareLabel " +
                "writeMask=$writeMaskLabel token=$tokenLabel packet=${packetId.value}"
        is GPUPassCommand.RenderLayerChildren ->
            "passes.command renderLayerChildren scope=$scopeLabel target=$targetLabel " +
                "children=$childrenLabel token=$tokenLabel"
        is GPUPassCommand.CompositeLayer ->
            "passes.command compositeLayer source=$sourceLabel parent=$parentTargetLabel " +
                "blend=$blendModeLabel route=$routeLabel token=$tokenLabel"
        is GPUPassCommand.ResolveMSAA ->
            "passes.command resolveMSAA source=$sourceLabel destination=$destinationLabel " +
                "strategy=$strategyLabel token=$tokenLabel"
        is GPUPassCommand.RefuseIntermediate ->
            "passes.command refuseIntermediate scope=$scopeLabel reason=$reasonCode"
        is GPUPassCommand.RefuseLayer ->
            "passes.command refuseLayer scope=$scopeLabel reason=$reasonCode"
        is GPUPassCommand.RefuseStencilCover ->
            "passes.command refuseStencilCover path=$pathLabel reason=$reasonCode"
        is GPUPassCommand.EndRenderPass ->
            "passes.command endRenderPass pass=$passId"
    }

private fun GPUPassCommandOperandBridge.dumpLine(): String =
    "passes.command-bridge packet=${packetId?.value ?: NONE_DUMP_VALUE} command=$commandLabel " +
        operand.dumpCommandOperandFields()

private fun GPUPassCommandOperandBridge.matchesCommandOperandKind(): Boolean =
    when (commandLabel) {
        "beginRenderPass" ->
            operand.kind in setOf(
                GPUMaterializedCommandOperandKind.RenderTarget,
                GPUMaterializedCommandOperandKind.TextureView,
            )
        "copyTexture" -> operand.kind == GPUMaterializedCommandOperandKind.DestinationCopyTexture
        "prepareIntermediateTexture" -> operand.kind == GPUMaterializedCommandOperandKind.Texture
        "prepareLayerTarget" -> operand.kind == GPUMaterializedCommandOperandKind.Texture
        "clearLayerTarget", "renderLayerChildren" ->
            operand.kind == GPUMaterializedCommandOperandKind.RenderTarget
        "prepareStencilAttachment" -> operand.kind == GPUMaterializedCommandOperandKind.Texture
        "clearStencilAttachment" ->
            operand.kind == GPUMaterializedCommandOperandKind.DepthStencilAttachment
        "stencilCoverProducer", "stencilCoverDraw" ->
            operand.kind in setOf(
                GPUMaterializedCommandOperandKind.RenderPipeline,
                GPUMaterializedCommandOperandKind.DepthStencilAttachment,
            )
        "compositeLayer" ->
            operand.kind in setOf(
                GPUMaterializedCommandOperandKind.TextureView,
                GPUMaterializedCommandOperandKind.Sampler,
            )
        "resolveMSAA" ->
            operand.kind in setOf(
                GPUMaterializedCommandOperandKind.Texture,
                GPUMaterializedCommandOperandKind.TextureView,
            )
        "setRenderPipeline" -> operand.kind == GPUMaterializedCommandOperandKind.RenderPipeline
        "setBindGroup" ->
            operand.kind in setOf(
                GPUMaterializedCommandOperandKind.BindGroup,
                GPUMaterializedCommandOperandKind.UniformBuffer,
                GPUMaterializedCommandOperandKind.StorageBuffer,
                GPUMaterializedCommandOperandKind.Texture,
                GPUMaterializedCommandOperandKind.TextureView,
                GPUMaterializedCommandOperandKind.Sampler,
            )
        "draw" ->
            operand.kind in setOf(
                GPUMaterializedCommandOperandKind.VertexBuffer,
                GPUMaterializedCommandOperandKind.IndexBuffer,
            )
        else -> false
    }

private fun List<GPURenderStepAttribute>.dumpAttributes(): String =
    if (isEmpty()) {
        NONE_DUMP_VALUE
    } else {
        joinToString(",") { attribute ->
            "${attribute.name}:${attribute.valueClass}@${attribute.byteOffset}+${attribute.byteSize}"
        }
    }

private fun List<String>.dumpSequence(): String =
    if (isEmpty()) NONE_DUMP_VALUE else joinToString(",")

private fun List<GPUPassDiagnostic>.dumpCodes(): String =
    if (isEmpty()) NONE_DUMP_VALUE else map { diagnostic -> diagnostic.code }.sorted().joinToString(",")

private fun List<GPUPassDiagnostic>.dumpLines(): List<String> =
    toList().sortedWith(
        compareBy<GPUPassDiagnostic> { it.code }
            .thenBy { it.passId ?: "" }
            .thenBy { it.invocationId ?: "" }
            .thenBy { it.terminal.toString() },
    )
        .map { diagnostic ->
            "passes.diagnostic " +
                "code=${diagnostic.code} " +
                "pass=${diagnostic.passId ?: NONE_DUMP_VALUE} " +
                "invocation=${diagnostic.invocationId ?: NONE_DUMP_VALUE} " +
                "terminal=${diagnostic.terminal}"
        }

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

    /**
     * Builds an accepted DrawTextRun/A8 pass with invocation identity but no
     * concrete resource or binding slots.
     */
    fun acceptedDrawTextRun(
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
            role = "text",
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
            passId = "pass.text.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "load.store",
            invocations = listOf(invocation),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )
    }

    /** Builds an accepted FillPath pass with path-fill render-step identity. */
    fun acceptedFillPath(
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
            role = "path_fill",
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
            passId = "pass.path_fill.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "load.store",
            invocations = listOf(invocation),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )
    }

    /** Builds an empty refused FillPath pass. */
    fun refusedFillPath(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass.refused.path_fill.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "refused",
            invocations = emptyList(),
            pipelineKeys = emptyList(),
            barriers = emptyList(),
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = code,
                    passId = "pass.refused.path_fill.$commandIdValue",
                    terminal = true,
                ),
            ),
        )

    /** Builds an empty refused DrawTextRun pass. */
    fun refusedDrawTextRun(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass.refused.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "refused",
            invocations = emptyList(),
            pipelineKeys = emptyList(),
            barriers = emptyList(),
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = code,
                    passId = "pass.refused.$commandIdValue",
                    terminal = true,
                ),
            ),
        )

    /**
     * Builds an accepted DrawImageRect pass with image-upload render-step
     * identity but no concrete texture or sampler resources.
     */
    fun acceptedDrawImageRect(
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
            role = "image_draw",
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
            passId = "pass.image_draw.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "load.store",
            invocations = listOf(invocation),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )
    }

    /**
     * Builds an accepted ApplyFilter pass with filter render-step identity but
     * no concrete resource or binding slots.
     */
    fun acceptedApplyFilter(
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
            role = "filter",
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
            passId = "pass.filter.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "load.store",
            invocations = listOf(invocation),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )
    }

    /** Builds an empty refused ApplyFilter pass. */
    fun refusedApplyFilter(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass.refused.filter.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "refused",
            invocations = emptyList(),
            pipelineKeys = emptyList(),
            barriers = emptyList(),
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = code,
                    passId = "pass.refused.filter.$commandIdValue",
                    terminal = true,
                ),
            ),
        )

    /** Builds an empty refused DrawImageRect pass. */
    fun refusedDrawImageRect(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass.refused.image_draw.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "refused",
            invocations = emptyList(),
            pipelineKeys = emptyList(),
            barriers = emptyList(),
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = code,
                    passId = "pass.refused.image_draw.$commandIdValue",
                    terminal = true,
                ),
            ),
        )

    /**
     * Builds an accepted DrawLayer pass with saveLayer render-step
     * identity but no concrete offscreen target or composite resources.
     */
    fun acceptedDrawLayer(
        commandIdValue: Int,
        analysisRecordId: String,
        renderStepIdentity: String,
        sortKey: Long,
        pipelineKeyHash: String,
        boundsHash: String,
        scissorBoundsHash: String?,
        originalPaintOrder: Int,
        targetStateHash: String,
        layerScopeId: String,
    ): GPUDrawPass {
        val invocation = GPUDrawInvocation(
            commandIdValue = commandIdValue,
            analysisRecordId = analysisRecordId,
            renderStepIndex = 0,
            renderStepId = GPURenderStepID(renderStepIdentity),
            role = "draw_layer",
            layerScopeId = layerScopeId,
            sortKey = sortKey,
            pipelineKeyHash = pipelineKeyHash,
            uniformSlot = null,
            resourceSlot = null,
            boundsHash = boundsHash,
            scissorBoundsHash = scissorBoundsHash,
            originalPaintOrder = originalPaintOrder,
        )
        return GPUDrawPass(
            passId = "pass.draw_layer.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = layerScopeId,
            loadStoreLabel = "load.store",
            invocations = listOf(invocation),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )
    }

    /** Builds an empty refused DrawLayer pass. */
    fun refusedDrawLayer(
        commandIdValue: Int,
        targetStateHash: String,
        code: String,
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass.refused.draw_layer.$commandIdValue",
            targetStateHash = targetStateHash,
            layerScopeId = "root",
            loadStoreLabel = "refused",
            invocations = emptyList(),
            pipelineKeys = emptyList(),
            barriers = emptyList(),
            diagnostics = listOf(
                GPUPassDiagnostic(
                    code = code,
                    passId = "pass.refused.draw_layer.$commandIdValue",
                    terminal = true,
                ),
            ),
        )
}
