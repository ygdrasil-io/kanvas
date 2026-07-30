package org.graphiks.kanvas.gpu.renderer.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextAuthenticatedComposite
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeAdmissionToken
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramCache
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextDeviceToLocalAffine
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.payloads.preparedImageScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.buildImageFrameResourcePlanFromBindings
import org.graphiks.kanvas.gpu.renderer.resources.buildMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.buildR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout

data class GPUPreparedSurfaceFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    val readbackRequestId: GPUReadbackRequestID?,
    val targetFormat: GPUColorFormat = GPUColorFormat.RGBA8Unorm,
)

/** Checked structural ceilings applied before one prepared task graph is published. */
data class GPUPreparedSurfaceTaskGraphLimits(
    val maxBufferAllocations: Int = Int.MAX_VALUE,
    val maxTextureAllocations: Int = Int.MAX_VALUE,
    val maxAllocations: Int = Int.MAX_VALUE,
    val maxTasks: Int = Int.MAX_VALUE,
    val maxDependencies: Int = Int.MAX_VALUE,
    val maxInstanceRanges: Int = Int.MAX_VALUE,
) {
    init {
        require(maxBufferAllocations >= 0)
        require(maxTextureAllocations >= 0)
        require(maxAllocations >= 0)
        require(maxTasks >= 0)
        require(maxDependencies >= 0)
        require(maxInstanceRanges >= 0)
    }
}

sealed interface GPUPreparedSurfaceFrameResult {
    data class Recorded(val taskList: GPUTaskList) : GPUPreparedSurfaceFrameResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameResult
}

/** Recording-owned association between task identity and a handle-free resource plan. */
internal data class GPURecordedImageUpload(
    val taskId: GPUTaskID,
    val resources: GPUImageFrameResourcePlan,
)

/** Recording-owned association between one exact immutable R8 artifact and its upload task. */
internal data class GPURecordedR8Upload(
    val taskId: GPUTaskID,
    val resources: GPUR8FrameResourcePlan,
)

/** Recording-owned association between one exact prepared-material texture and its upload. */
internal data class GPURecordedMaterialUpload(
    val taskId: GPUTaskID,
    val resources: GPUMaterialTextureFrameResourcePlan,
)

/** One immutable, frame-global prepared-text instance buffer. */
class GPUPreparedTextInstanceBufferPlan(
    val bufferRef: GPUFrameBufferRef,
    val strideBytes: Int,
    val alignmentBytes: Int,
    val instanceCount: Int,
    val byteSize: Long,
    val contentHash: String,
    uploadBytes: ByteArray,
) {
    private val uploadSnapshot = uploadBytes.copyOf()

    init {
        require(strideBytes == GPUTextA8Instance.ENCODED_BYTE_SIZE)
        require(alignmentBytes == PREPARED_TEXT_INSTANCE_ALIGNMENT_BYTES)
        require(instanceCount > 0)
        require(byteSize == uploadSnapshot.size.toLong())
        require(byteSize == Math.multiplyExact(instanceCount.toLong(), strideBytes.toLong()))
        require(contentHash == uploadSnapshot.sha256Hex())
    }

    fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()
}

/** One immutable, aligned, frame-global prepared-material uniform buffer. */
class GPUPreparedTextMaterialUniformBufferPlan(
    val bufferRef: GPUFrameBufferRef,
    val alignmentBytes: Long,
    val byteSize: Long,
    val contentHash: String,
    uploadBytes: ByteArray,
) {
    private val uploadSnapshot = uploadBytes.copyOf()

    init {
        require(alignmentBytes > 0L && alignmentBytes and (alignmentBytes - 1L) == 0L)
        require(byteSize > 0L && byteSize == uploadSnapshot.size.toLong())
        require(contentHash == uploadSnapshot.sha256Hex())
    }

    fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()
}

/** TextA8-only immutable facts duplicated into the Task 8/9 preflight seal. */
class GPUPreparedTextCompositePreflightSeal internal constructor(
    deviceToLocal: GPUPreparedTextDeviceToLocalAffine,
    val drawUniformBufferRef: GPUFrameBufferRef,
    val drawUniformAlignmentBytes: Long,
    val drawUniformLogicalSliceSizeBytes: Long,
    val drawUniformBufferByteSize: Long,
    val drawUniformBufferContentHash: String,
    drawUniformSlice: GPUPreparedTextDrawUniformSlice,
    val compositeSourceHash: String,
    val compositeAbiHash: String,
    val compositePipelineKey: String,
    val compositeVertexEntryPoint: String,
    val compositeFragmentEntryPoint: String,
    compositeVertexLayout: GPUPreparedTextVertexLayout,
    internal val compositeAdmissionToken: GPUPreparedTextCompositeAdmissionToken,
) {
    val deviceToLocal: GPUPreparedTextDeviceToLocalAffine = deviceToLocal.copy()
    val drawUniformSlice: GPUPreparedTextDrawUniformSlice = drawUniformSlice.copy()
    val compositeVertexLayout: GPUPreparedTextVertexLayout = GPUPreparedTextVertexLayout(
        arrayStrideBytes = compositeVertexLayout.arrayStrideBytes,
        stepMode = compositeVertexLayout.stepMode,
        attributes = compositeVertexLayout.attributes,
    )

    init {
        require(drawUniformAlignmentBytes > 0L)
        require(drawUniformLogicalSliceSizeBytes == PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES)
        require(drawUniformBufferByteSize > 0L)
        require(drawUniformBufferContentHash.isNotBlank())
        require(compositeSourceHash.isNotBlank())
        require(compositeAbiHash.isNotBlank())
        require(compositePipelineKey.isNotBlank())
        require(compositeVertexEntryPoint.isNotBlank())
        require(compositeFragmentEntryPoint.isNotBlank())
    }
}

/** Exact handle-free packet facts shared by TextA8 and ColorGlyph preflight. */
class GPUPreparedTextPacketAuthoritySeal internal constructor(
    val commandIdValue: Int,
    val renderStepIdentity: String,
    val renderPipelineKey: String,
    val bindingLayoutHash: String,
    val vertexSourceLabel: String,
    val targetStateHash: String,
    val scissorBoundsHash: String?,
) {
    init {
        require(commandIdValue >= 0)
        require(renderStepIdentity.isNotBlank())
        require(renderPipelineKey.isNotBlank())
        require(bindingLayoutHash.isNotBlank())
        require(vertexSourceLabel.isNotBlank())
        require(targetStateHash.isNotBlank())
        require(scissorBoundsHash == null || scissorBoundsHash.isNotBlank())
    }
}

/**
 * Passive immutable Task 8 handoff facts consumed by Task 9 preflight.
 *
 * This seal owns no validation and no native resource. It deliberately keeps
 * the gathered semantic and prepared-material facts independent from the
 * render binding so preflight can detect late substitution.
 */
class GPUPreparedTextBindingPreflightSeal(
    val semanticCanonicalHash: String,
    val atlasKey: String,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val atlasRowBytes: Int,
    val atlasGeneration: Long,
    val atlasContentHash: String,
    val pageIndex: Int,
    val instanceStrideBytes: Int,
    val firstInstance: Int,
    val instanceCount: Int,
    val instanceBufferByteSize: Long,
    val instanceBufferContentHash: String,
    val materialUniformOffsetBytes: Long,
    val materialUniformSizeBytes: Long,
    val materialKey: String,
    val materialWgslSourceHash: String,
    val materialEntryPoint: String,
    val materialAbiHash: String,
    val materialUniformContentHash: String,
    materialSampledResourceFacts: List<String>,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val clipIdentity: String,
    val blendPlanIdentity: String,
    val capabilitySnapshotHash: String,
    val textA8Composite: GPUPreparedTextCompositePreflightSeal? = null,
    val packetAuthority: GPUPreparedTextPacketAuthoritySeal? = null,
) {
    val materialSampledResourceFacts: List<String> =
        immutableList(materialSampledResourceFacts)

    init {
        require(semanticCanonicalHash.isNotBlank())
        require(atlasKey.isNotBlank())
        require(atlasWidth > 0 && atlasHeight > 0 && atlasRowBytes >= atlasWidth)
        require(atlasGeneration >= 0L && atlasContentHash.isNotBlank())
        require(pageIndex >= 0)
        require(instanceStrideBytes > 0)
        require(firstInstance >= 0 && instanceCount > 0)
        require(instanceBufferByteSize > 0L && instanceBufferContentHash.isNotBlank())
        require(materialUniformOffsetBytes >= 0L && materialUniformSizeBytes >= 0L)
        require(materialKey.isNotBlank())
        require(materialWgslSourceHash.isNotBlank())
        require(materialEntryPoint.isNotBlank())
        require(materialAbiHash.isNotBlank())
        require(materialUniformContentHash.isNotBlank())
        require(clipIdentity.isNotBlank())
        require(blendPlanIdentity.isNotBlank())
        require(capabilitySnapshotHash.isNotBlank())
    }
}

/** Exact atlas and frame-global instance range consumed by one ordered prepared-text packet. */
class GPUPreparedTextRenderBinding(
    val packetId: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID,
    val atlasResourcePlan: GPUR8FrameResourcePlan,
    val instanceBufferPlan: GPUPreparedTextInstanceBufferPlan,
    val firstInstance: Int,
    val instanceCount: Int,
    val materialUniformBufferPlan: GPUPreparedTextMaterialUniformBufferPlan?,
    val materialUniformOffsetBytes: Long,
    val materialUniformSizeBytes: Long,
    materialSampledResourcePlans: List<GPUMaterialTextureFrameResourcePlan>,
    val preflightSeal: GPUPreparedTextBindingPreflightSeal,
    private val drawUniformBufferPlanOrNull: GPUPreparedTextDrawUniformBufferPlan? = null,
    private val drawUniformSliceOrNull: GPUPreparedTextDrawUniformSlice? = null,
    private val compositeProgramOrNull: GPUPreparedTextCompositeProgram? = null,
) {
    val materialSampledResourcePlans: List<GPUMaterialTextureFrameResourcePlan> =
        immutableList(materialSampledResourcePlans)
    internal val hasTextA8Composite: Boolean
        get() = compositeProgramOrNull != null
    val drawUniformBufferPlan: GPUPreparedTextDrawUniformBufferPlan
        get() = checkNotNull(drawUniformBufferPlanOrNull) {
            "ColorGlyph binding has no TextA8 draw-uniform plan before Task 11"
        }
    val drawUniformSlice: GPUPreparedTextDrawUniformSlice
        get() = checkNotNull(drawUniformSliceOrNull) {
            "ColorGlyph binding has no TextA8 draw-uniform slice before Task 11"
        }
    val compositeProgram: GPUPreparedTextCompositeProgram
        get() = checkNotNull(compositeProgramOrNull) {
            "ColorGlyph binding has no TextA8 composite program before Task 11"
        }
    internal val nativeProgram: GPUPreparedTextNativeProgramHandoff
        get() {
            val compositeSeal = checkNotNull(preflightSeal.textA8Composite) {
                "Prepared TextA8 native handoff requires a composite preflight seal"
            }
            val authenticatedComposite = checkNotNull(
                compositeProgram.authenticatedSnapshot(
                    compositeSeal.compositeAdmissionToken,
                ),
            ) {
                "Prepared TextA8 native handoff requires an authenticated composite program"
            }
            return GPUPreparedTextNativeProgramHandoff.fromAuthenticated(
                authenticatedComposite,
            )
        }

    init {
        require(firstInstance >= 0 && instanceCount > 0)
        require(
            Math.addExact(firstInstance, instanceCount) <= instanceBufferPlan.instanceCount,
        )
        require(materialUniformOffsetBytes >= 0L && materialUniformSizeBytes >= 0L)
        if (materialUniformBufferPlan == null) {
            require(materialUniformOffsetBytes == 0L && materialUniformSizeBytes == 0L)
        } else {
            require(materialUniformSizeBytes > 0L)
            require(
                materialUniformOffsetBytes % materialUniformBufferPlan.alignmentBytes == 0L,
            )
            require(
                Math.addExact(materialUniformOffsetBytes, materialUniformSizeBytes) <=
                    materialUniformBufferPlan.byteSize,
            )
        }
        require(
            listOf(
                drawUniformBufferPlanOrNull,
                drawUniformSliceOrNull,
                compositeProgramOrNull,
                preflightSeal.textA8Composite,
            ).all { it == null } ||
                listOf(
                    drawUniformBufferPlanOrNull,
                    drawUniformSliceOrNull,
                    compositeProgramOrNull,
                    preflightSeal.textA8Composite,
                ).all { it != null },
        ) {
            "Prepared TextA8 composite binding facts must be published atomically"
        }
        if (drawUniformBufferPlanOrNull != null) {
            val slice = requireNotNull(drawUniformSliceOrNull)
            require(slice.packetId == packetId)
            require(drawUniformBufferPlanOrNull.slices.single { it.packetId == packetId } == slice)
            require(preflightSeal.textA8Composite?.drawUniformSlice == slice)
        }
    }
}

/** Passive Task 5 handoff; native execution consumes no materials-package semantic type. */
internal class GPUPreparedTextNativeProgramHandoff private constructor(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val drawUniformBinding: Int,
    val materialUniformBinding: GPUPreparedTextNativeUniformBinding?,
    materialSampledBindings: List<GPUPreparedTextNativeSampledBinding>,
    val atlasTextureBinding: Int,
    val atlasSamplerBinding: Int,
    val sourceHash: String,
    val abiHash: String,
    val targetFormatClass: String,
    val blendPlanIdentity: String,
    val fixedFunctionBlendState:
        org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState,
    val vertexLayout: GPUPreparedTextVertexLayout,
    val pipelineKey: String,
) {
    val materialSampledBindings: List<GPUPreparedTextNativeSampledBinding> =
        immutableList(materialSampledBindings)

    companion object {
        internal fun fromAuthenticated(
            program: GPUPreparedTextAuthenticatedComposite,
        ): GPUPreparedTextNativeProgramHandoff {
            val fragment = program.bindingPlan.materialFragment
            return GPUPreparedTextNativeProgramHandoff(
                wgslSource = program.wgslSource,
                vertexEntryPoint = program.vertexEntryPoint,
                fragmentEntryPoint = program.fragmentEntryPoint,
                drawUniformBinding = program.bindingPlan.drawUniformBinding,
                materialUniformBinding = fragment.uniformBinding?.let { binding ->
                    GPUPreparedTextNativeUniformBinding(
                        binding = binding.binding,
                        minBindingSizeBytes = binding.minBindingSizeBytes,
                    )
                },
                materialSampledBindings = fragment.sampledBindings.map { binding ->
                    GPUPreparedTextNativeSampledBinding(
                        textureBinding = binding.textureBinding,
                        samplerBinding = binding.samplerBinding,
                    )
                },
                atlasTextureBinding = program.bindingPlan.atlasTextureBinding,
                atlasSamplerBinding = program.bindingPlan.atlasSamplerBinding,
                sourceHash = program.sourceHash,
                abiHash = program.abiHash,
                targetFormatClass = program.targetFormatClass,
                blendPlanIdentity = program.blendPlanIdentity,
                fixedFunctionBlendState = checkNotNull(program.fixedFunctionBlendState) {
                    "Prepared TextA8 native handoff requires preflight-authenticated " +
                        "fixed-function blend state"
                },
                vertexLayout = GPUPreparedTextVertexLayout(
                    arrayStrideBytes = program.vertexLayout.arrayStrideBytes,
                    stepMode = program.vertexLayout.stepMode,
                    attributes = program.vertexLayout.attributes,
                ),
                pipelineKey = program.pipelineKey,
            )
        }
    }
}

internal data class GPUPreparedTextNativeUniformBinding(
    val binding: Int,
    val minBindingSizeBytes: Int,
)

internal data class GPUPreparedTextNativeSampledBinding(
    val textureBinding: Int,
    val samplerBinding: Int,
)

/**
 * Builds a handle-free prepared frame while keeping semantic/resource authorities immutable.
 *
 * Validation and all resource planning finish before any output task collection is constructed.
 */
class GPUPreparedSurfaceFrameTaskListBuilder(
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
    private val preparedTextCompositeProgramCache:
        GPUPreparedTextCompositeProgramCache = GPUPreparedTextCompositeProgramCache(),
) {
    fun build(
        request: GPUPreparedSurfaceFrameRequest,
        configuredAggregateBudgetBytes: Long = 1L shl 30,
        taskGraphLimits: GPUPreparedSurfaceTaskGraphLimits =
            GPUPreparedSurfaceTaskGraphLimits(),
    ): GPUPreparedSurfaceFrameResult {
        request.baseTaskList.tasks.filterIsInstance<GPUTask.Refused>().firstOrNull()?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it.diagnostic.atRecordingBoundary())
        }
        request.baseTaskList.diagnostics.firstOrNull(GPUDiagnostic::isTerminal)?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it.atRecordingBoundary())
        }
        val baseRenders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        if (baseRenders.isEmpty() || request.baseTaskList.tasks.any { it !is GPUTask.Render }) {
            return refused(
                "invalid.recording.prepared_surface_base_tasks",
                "Prepared surfaces require one accepted render-only base task list.",
            )
        }
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
            request.targetBounds.width <= 0 || request.targetBounds.height <= 0
        ) {
            return refused(
                "invalid.recording.prepared_surface_target",
                "Prepared surfaces require one non-empty zero-origin target.",
            )
        }
        if (configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.prepared_surface_budget",
                "Prepared-surface aggregate budget must be positive.",
            )
        }
        val packets = baseRenders.flatMap(GPUTask.Render::drawPackets)
            .sortedBy(GPUDrawPacket::originalPaintOrder)
        val commandIds = packets.map(GPUDrawPacket::commandIdValue)
        val semanticRefs = request.semanticsByCommandId.values
            .map { semantic -> semantic.payloadRef.commandIdValue }
        if (commandIds.distinct().size != commandIds.size ||
            packets.map(GPUDrawPacket::originalPaintOrder).distinct().size != packets.size ||
            commandIds.toSet() != request.semanticsByCommandId.keys ||
            semanticRefs.distinct().size != semanticRefs.size ||
            semanticRefs.toSet() != request.semanticsByCommandId.keys ||
            request.semanticsByCommandId.any { (commandId, semantic) ->
                semantic.payloadRef.commandIdValue != commandId
            }
        ) {
            return refused(
                "invalid.recording.prepared_surface_semantics",
                "Every accepted packet requires one unique semantic with the identical command identity.",
            )
        }
        val unsupported = request.semanticsByCommandId.values.firstOrNull {
            it !is GPUDrawSemanticPayload.CorePrimitive &&
                it !is GPUDrawSemanticPayload.SampledImage &&
                it !is GPUDrawSemanticPayload.TextA8 &&
                it !is GPUDrawSemanticPayload.ColorGlyph
        }
        if (unsupported != null) {
            return refused(
                "unsupported.recording.prepared_surface_semantic_type",
                "Prepared surfaces accept only CorePrimitive, SampledImage, TextA8, and ColorGlyph semantics.",
            )
        }
        val hasPreparedText = request.semanticsByCommandId.values.any {
            it is GPUDrawSemanticPayload.TextA8 || it is GPUDrawSemanticPayload.ColorGlyph
        }
        if (hasPreparedText && request.capabilities.limits == null) {
            return refused(
                "unsupported.recording.prepared_surface_limits_unavailable",
                "Prepared text requires observed device limits.",
            )
        }
        val invalidPreparedText = request.semanticsByCommandId.values.firstOrNull { semantic ->
            when (semantic) {
                is GPUDrawSemanticPayload.TextA8 ->
                    !semantic.hasCanonicalHashIntegrity() ||
                        semantic.targetBounds != request.targetBounds ||
                        semantic.atlasGeneration.value.toLong() != semantic.atlas.generation ||
                        semantic.instances.isEmpty()
                is GPUDrawSemanticPayload.ColorGlyph ->
                    !semantic.hasCanonicalHashIntegrity() ||
                        semantic.targetBounds != request.targetBounds ||
                        semantic.instances.isEmpty() ||
                        semantic.material == null
                else -> false
            }
        }
        if (invalidPreparedText != null) {
            return refused(
                "invalid.recording.prepared_text_semantic",
                "Prepared text requires one canonical immutable payload with exact target and instances.",
            )
        }
        val invalidImage = request.semanticsByCommandId.values
            .filterIsInstance<GPUDrawSemanticPayload.SampledImage>()
            .firstOrNull { semantic ->
                semantic.artifact.colorInterpretation !=
                    GPUColorInterpretation.EncodedPremulSrgb.value ||
                    semantic.targetBounds != request.targetBounds ||
                    !semantic.hasCanonicalHashIntegrity()
            }
        if (invalidImage != null) {
            return refused(
                "invalid.recording.prepared_image_semantic",
                "Prepared images require canonical EncodedPremulSrgb artifact and target authority.",
            )
        }
        val invalidImageScissorAuthority = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.SampledImage ?: return@firstOrNull false
            val hasScissor = semantic.scissorBounds != semantic.targetBounds
            val expectedHash = if (hasScissor) {
                preparedImageScissorAuthority(semantic.scissorBounds)
            } else {
                null
            }
            val expectedCoverage = if (hasScissor) {
                GPUClipCoveragePlan.Scissor(
                    org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(
                        semantic.scissorBounds.left.toFloat(),
                        semantic.scissorBounds.top.toFloat(),
                        semantic.scissorBounds.right.toFloat(),
                        semantic.scissorBounds.bottom.toFloat(),
                    ),
                )
            } else {
                GPUClipCoveragePlan.NoClip
            }
            val expectedExecution = if (hasScissor) {
                GPUClipExecutionPlan.ScissorOnly(semantic.scissorBounds)
            } else {
                GPUClipExecutionPlan.NoClip
            }
            packet.scissorBoundsHash != expectedHash ||
                packet.clipCoveragePlan != expectedCoverage ||
                packet.clipExecutionPlan != expectedExecution
        }
        if (invalidImageScissorAuthority != null) {
            return refused(
                "invalid.recording.prepared_image_scissor_authority",
                "Prepared-image packet clip authorities must exactly match the immutable semantic.",
            )
        }

        val allCore = request.semanticsByCommandId.values
            .all { it is GPUDrawSemanticPayload.CorePrimitive }
        if (allCore) {
            @Suppress("UNCHECKED_CAST")
            val coreSemantics = request.semanticsByCommandId as
                Map<Int, GPUDrawSemanticPayload.CorePrimitive>
            return when (
                val core = GPUCorePrimitivePreparedFrameTaskListAssembler(readbackLayoutPlanner).build(
                    GPUCorePrimitivePreparedFrameRequest(
                        baseTaskList = request.baseTaskList,
                        capabilities = request.capabilities,
                        target = request.target,
                        targetBounds = request.targetBounds,
                        semanticsByCommandId = coreSemantics,
                        readbackRequestId = request.readbackRequestId,
                        configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                        targetFormat = request.targetFormat,
                    ),
                )
            ) {
                is GPUCorePrimitivePreparedFrameResult.Recorded ->
                    GPUPreparedSurfaceFrameResult.Recorded(core.taskList)
                is GPUCorePrimitivePreparedFrameResult.Refused ->
                    GPUPreparedSurfaceFrameResult.Refused(core.diagnostic)
            }
        }
        val invalidRoutePacket = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            when (semantic) {
                is GPUDrawSemanticPayload.SampledImage ->
                    packet.renderStepId.value != semantic.payloadRef.renderStepIdentity ||
                        semantic.payloadRef.renderStepIdentity != "image.draw.texture_upload"
                is GPUDrawSemanticPayload.TextA8,
                is GPUDrawSemanticPayload.ColorGlyph,
                -> packet.renderStepId.value != semantic.payloadRef.renderStepIdentity
                else -> false
            }
        }
        if (invalidRoutePacket != null) {
            return refused(
                "invalid.recording.prepared_surface_route_identity",
                "Prepared-surface packets and semantics must retain one identical closed render route.",
            )
        }
        val invalidCoreAuthority = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.CorePrimitive ?: return@firstOrNull false
            val coverage = packet.clipCoveragePlan
            val execution = packet.clipExecutionPlan
            coverage == null || execution == null ||
                coverage != semantic.clipCoveragePlan ||
                semantic.clipExecutionPlanIdentity?.let { identity ->
                    execution.canonicalIdentity() != identity
                } == true
        }
        if (invalidCoreAuthority != null) {
            return refused(
                "invalid.recording.prepared_surface_core_authority",
                "Mixed prepared surfaces require exact packet clip coverage and execution authorities.",
            )
        }

        val imagePackets = packets.filter { packet ->
            request.semanticsByCommandId.getValue(packet.commandIdValue) is
                GPUDrawSemanticPayload.SampledImage
        }
        val imageSemantics = imagePackets.associate { packet ->
            packet.commandIdValue to
                request.semanticsByCommandId.getValue(packet.commandIdValue)
                    as GPUDrawSemanticPayload.SampledImage
        }
        val recordedImageUploads = imageSemantics.values
            .groupBy { semantic -> semantic.artifact.key }
            .toSortedMap(compareBy { key -> key.value })
            .values
            .mapIndexed { index, semantics ->
                val artifact = semantics.first().artifact
                if (semantics.any { it.artifact.contentHash != artifact.contentHash }) {
                    return refused(
                        "invalid.recording.prepared_image_artifact_identity",
                        "One prepared-image artifact key must identify one exact immutable byte artifact.",
                    )
                }
                GPURecordedImageUpload(
                    taskId = GPUTaskID(
                        "task.prepared-surface.image-upload.${request.baseTaskList.frameId.value}.$index",
                    ),
                    resources = buildImageFrameResourcePlanFromBindings(
                        artifact = artifact,
                        bindingInputs = semantics.map { semantic ->
                            GPUImageBindingInput(
                                packetId = packetForSemantic(packets, semantic).packetId.value,
                                sampling = semantic.sampling,
                            )
                        },
                        bindingLayoutHash = GPUPreparedImageBindingLayoutTopology.IDENTITY,
                        capabilities = request.capabilities,
                        frameIdentity = request.baseTaskList.frameId.value.toString(),
                    ),
                )
            }
        val imagePlans = recordedImageUploads.map(GPURecordedImageUpload::resources)
        val imagePlanByArtifactKey = imagePlans.associateBy { plan ->
            plan.bindingRequests.first().artifactKey
        }
        val imageUploadByArtifactKey = recordedImageUploads.associateBy { upload ->
            upload.resources.bindingRequests.first().artifactKey
        }
        val r8Semantics = packets.mapNotNull { packet ->
            when (val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)) {
                is GPUDrawSemanticPayload.TextA8 -> semantic
                is GPUDrawSemanticPayload.ColorGlyph -> semantic
                else -> null
            }
        }
        val textA8Inputs = packets.mapNotNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.TextA8 ?: return@mapNotNull null
            GPUPreparedTextDrawUniformInput(packet.packetId, semantic)
        }
        preparedTextNativeBlendDomainRefusal(
            textA8Inputs.map { input ->
                packets.single { packet -> packet.packetId == input.packetId }.blendPlan
            },
        )?.let { refusal ->
            return refused(refusal.code, refusal.message)
        }
        val compositeProgramsByPacketId =
            linkedMapOf<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID, GPUPreparedTextCompositeProgram>()
        textA8Inputs.forEach { input ->
            when (
                val composition = preparedTextCompositeProgramCache.getOrCompose(
                    material = input.semantic.material,
                    targetFormatClass = request.targetFormat.value,
                    blendPlanIdentity = input.semantic.blendPlanIdentity,
                    fixedFunctionBlendState = (
                        packets.single { packet -> packet.packetId == input.packetId }.blendPlan as?
                            org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan.FixedFunctionBlend
                        )?.state,
                )
            ) {
                is GPUPreparedTextCompositeProgramResult.Ready ->
                    compositeProgramsByPacketId[input.packetId] = composition.program
                is GPUPreparedTextCompositeProgramResult.Refused ->
                    return refused(composition.code, composition.message)
            }
        }
        val textDrawUniformAssembly = if (textA8Inputs.isEmpty()) {
            null
        } else {
            when (
                val result = buildPreparedTextDrawUniformBufferPlan(
                    inputs = textA8Inputs,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    alignmentBytes =
                        requireNotNull(request.capabilities.limits)
                            .minUniformBufferOffsetAlignment,
                    maxBufferSize = requireNotNull(request.capabilities.limits).maxBufferSize,
                )
            ) {
                is GPUPreparedTextDrawUniformPlanResult.Prepared -> result
                is GPUPreparedTextDrawUniformPlanResult.Refused ->
                    return refused(result.code, result.message)
            }
        }
        val inconsistentR8Identity = r8Semantics
            .groupBy(GPUDrawSemanticPayload::exactR8ArtifactIdentity)
            .values
            .firstOrNull { group ->
                val expectedBytes = group.first().r8Artifact().tightBytesForUpload()
                group.drop(1).any { semantic ->
                    !semantic.r8Artifact().tightBytesForUpload().contentEquals(expectedBytes)
                }
            }
        if (inconsistentR8Identity != null) {
            return refused(
                "invalid.recording.prepared_text_r8_artifact_identity",
                "One exact prepared-text R8 identity must retain identical immutable bytes.",
            )
        }
        val recordedR8Uploads = try {
            r8Semantics
                .distinctBy(GPUDrawSemanticPayload::exactR8ArtifactIdentity)
                .mapIndexed { index, semantic ->
                    GPURecordedR8Upload(
                        taskId = GPUTaskID(
                            "task.prepared-surface.r8-upload.${request.baseTaskList.frameId.value}.$index",
                        ),
                        resources = buildR8FrameResourcePlan(
                            artifact = semantic.r8Artifact(),
                            capabilities = request.capabilities,
                            frameIdentity = request.baseTaskList.frameId.value.toString(),
                        ),
                    )
                }
        } catch (failure: IllegalArgumentException) {
            return refused(
                "unsupported.recording.prepared_text_r8_resource",
                failure.message ?: "Prepared text R8 resource planning failed.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.prepared_text_r8_resource",
                "Prepared text R8 resource planning overflowed.",
            )
        }
        val r8UploadByIdentity = recordedR8Uploads.associateBy { upload ->
            upload.resources.exactR8ArtifactIdentity()
        }
        val materialResources = r8Semantics.flatMap { semantic ->
            semantic.preparedTextMaterial().sampledResources
        }
        val inconsistentMaterialResource = materialResources
            .groupBy { resource -> resource.resourceKey }
            .values
            .firstOrNull { group ->
                val first = group.first()
                group.drop(1).any { resource ->
                    resource.contentHash != first.contentHash ||
                        resource.width != first.width ||
                        resource.height != first.height ||
                        resource.samplingFilterMode != first.samplingFilterMode ||
                        resource.alphaOnly != first.alphaOnly ||
                        !resource.rgba8Bytes().contentEquals(first.rgba8Bytes())
                }
            }
        if (inconsistentMaterialResource != null) {
            return refused(
                "invalid.recording.prepared_text_material_resource_identity",
                "One prepared-material resource key must retain one exact immutable resource.",
            )
        }
        val recordedMaterialUploads = try {
            materialResources
                .distinctBy { resource -> resource.resourceKey }
                .mapIndexed { index, resource ->
                    GPURecordedMaterialUpload(
                        taskId = GPUTaskID(
                            "task.prepared-surface.material-upload." +
                                "${request.baseTaskList.frameId.value}.$index",
                        ),
                        resources = buildMaterialTextureFrameResourcePlan(
                            resourceKey = resource.resourceKey,
                            width = resource.width,
                            height = resource.height,
                            samplingFilterMode = resource.samplingFilterMode,
                            alphaOnly = resource.alphaOnly,
                            contentHash = resource.contentHash,
                            rgba8Bytes = resource.rgba8Bytes(),
                            capabilities = request.capabilities,
                            frameIdentity = request.baseTaskList.frameId.value.toString(),
                        ),
                    )
                }
        } catch (failure: IllegalArgumentException) {
            return refused(
                "unsupported.recording.prepared_text_material_resource",
                failure.message ?: "Prepared text material resource planning failed.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.prepared_text_material_resource",
                "Prepared text material resource planning overflowed.",
            )
        }
        val materialUploadByResourceKey = recordedMaterialUploads.associateBy { upload ->
            upload.resources.resourceKey
        }
        val textInstanceAssembly = if (r8Semantics.isEmpty()) {
            null
        } else {
            when (
                val assembly = buildPreparedTextInstanceAssembly(
                    semantics = r8Semantics,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    capabilities = request.capabilities,
                )
            ) {
                is PreparedTextInstanceAssemblyResult.Prepared -> assembly
                is PreparedTextInstanceAssemblyResult.Refused ->
                    return refused(assembly.code, assembly.message)
            }
        }
        val textMaterialUniformAssembly = if (r8Semantics.isEmpty()) {
            null
        } else {
            when (
                val assembly = buildPreparedTextMaterialUniformAssembly(
                    semantics = r8Semantics,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    capabilities = request.capabilities,
                )
            ) {
                is PreparedTextMaterialUniformAssemblyResult.Prepared -> assembly.assembly
                is PreparedTextMaterialUniformAssemblyResult.Refused ->
                    return refused(assembly.code, assembly.message)
            }
        }

        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId = requestId,
                sourceBounds = request.targetBounds,
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val plan = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> plan
                is GPUReadbackLayoutPlan.Refused ->
                    return GPUPreparedSurfaceFrameResult.Refused(plan.diagnostic)
            }
        }
        val enclosingAllocations = buildList {
            imagePlans.forEach { plan -> addAll(plan.memoryAllocations) }
            recordedR8Uploads.forEach { upload -> addAll(upload.resources.memoryAllocations) }
            recordedMaterialUploads.forEach { upload ->
                addAll(upload.resources.memoryAllocations)
            }
            textInstanceAssembly?.let { assembly ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-text.instances.${assembly.plan.contentHash}",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = assembly.plan.byteSize,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
            textMaterialUniformAssembly?.plan?.let { plan ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-text.material-uniforms.${plan.contentHash}",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = plan.byteSize,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
            textDrawUniformAssembly?.plan?.let { plan ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-text.draw-uniforms.${plan.contentHash}",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = plan.byteSize,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
            readbackPlan?.let { plan ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-surface.readback",
                        category = GPUFrameMemoryCategory.ReadbackStaging,
                        bytes = plan.stagingDescriptor.minimumBufferBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
        }
        val conflictingEnclosingAllocation = enclosingAllocations
            .groupBy(GPUFrameMemoryAllocation::label)
            .values.firstOrNull { sameLabel -> sameLabel.distinct().size > 1 }
        if (conflictingEnclosingAllocation != null) {
            return refused(
                "invalid.recording.prepared_surface_resource_identity",
                "Prepared-surface memory allocation identities must be exact and unique.",
            )
        }
        val coreAssembly = prepareMixedCoreAuthority(
            request = request,
            packets = packets,
            configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
            additionalMemoryAllocations = enclosingAllocations.distinct(),
        )
        if (coreAssembly is MixedCoreAssembly.Refused) {
            return GPUPreparedSurfaceFrameResult.Refused(coreAssembly.diagnostic)
        }
        coreAssembly as MixedCoreAssembly.Prepared
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.recording.prepared_surface_budget",
                "Prepared-surface target byte size overflowed.",
            )
        }
        val memoryBudget = coreAssembly.memoryBudget ?: GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "prepared-surface.scene-target",
                        category = GPUFrameMemoryCategory.CanonicalTarget,
                        bytes = targetBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = request.targetBounds,
                    ),
                ) + enclosingAllocations,
                configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                deviceLimits = requireNotNull(request.capabilities.limits),
            ),
        )
        memoryBudget.diagnostic?.let { diagnostic ->
            return GPUPreparedSurfaceFrameResult.Refused(diagnostic)
        }

        val preparations = mutableListOf<GPUResourcePreparationRequest>()
        preparations += coreAssembly.preparations
            .filterNot { preparation -> preparation.resource == request.target }
        preparations += corePrimitiveTargetPreparation(
            request.target,
            request.targetBounds,
            request.targetFormat,
        )
        imagePlans.forEach { plan ->
            preparations += plan.preparationRequests
        }
        recordedR8Uploads.forEach { upload ->
            preparations += upload.resources.preparationRequests
        }
        recordedMaterialUploads.forEach { upload ->
            preparations += upload.resources.preparationRequests
        }
        textInstanceAssembly?.let { assembly ->
            preparations += GPUResourcePreparationRequest(
                resource = assembly.plan.bufferRef,
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = assembly.plan.byteSize,
                    alignmentBytes = assembly.plan.alignmentBytes.toLong(),
                ),
                role = GPUFrameResourceRole.VertexData,
                usages = setOf(
                    GPUFrameResourceUsage.Vertex,
                    GPUFrameResourceUsage.CopyDestination,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = assembly.plan.byteSize,
                diagnosticLabel = "prepared-text.instances.${assembly.plan.contentHash}",
            )
        }
        textMaterialUniformAssembly?.plan?.let { plan ->
            preparations += GPUResourcePreparationRequest(
                resource = plan.bufferRef,
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = plan.byteSize,
                    alignmentBytes = plan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(
                    GPUFrameResourceUsage.Uniform,
                    GPUFrameResourceUsage.CopyDestination,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = plan.byteSize,
                diagnosticLabel = "prepared-text.material-uniforms.${plan.contentHash}",
            )
        }
        textDrawUniformAssembly?.plan?.let { plan ->
            preparations += GPUResourcePreparationRequest(
                resource = plan.bufferRef,
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = plan.byteSize,
                    alignmentBytes = plan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(
                    GPUFrameResourceUsage.Uniform,
                    GPUFrameResourceUsage.CopyDestination,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = plan.byteSize,
                diagnosticLabel = "prepared-text.draw-uniforms.${plan.contentHash}",
            )
        }
        val readbackStaging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.prepared-surface.readback.${request.baseTaskList.frameId.value}")
        }
        if (readbackPlan != null && readbackStaging != null) {
            preparations += GPUResourcePreparationRequest(
                resource = readbackStaging,
                descriptor = GPUFrameBufferDescriptor(
                    readbackPlan.stagingDescriptor.minimumBufferBytes,
                    4L,
                ),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
                diagnosticLabel = "prepared-surface.readback",
            )
        }
        val duplicatePreparation = preparations.groupBy { it.resource }.values
            .firstOrNull { group -> group.size > 1 }
        if (duplicatePreparation != null) {
            return refused(
                "invalid.recording.prepared_surface_resource_identity",
                "Prepared-surface resource identities must be unique before task emission.",
            )
        }

        val recordingId = baseRenders.first().recordingId
        val prepareTask = GPUTask.PrepareResources(
            taskId = GPUTaskID("task.prepared-surface.prepare.${request.baseTaskList.frameId.value}"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Prepare,
            requests = preparations,
        )
        val uploads = recordedImageUploads.map { recordedUpload ->
            val plan = recordedUpload.resources
            GPUTask.Upload(
                taskId = recordedUpload.taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                textureResourcePlan = plan,
            )
        }
        val r8Uploads = recordedR8Uploads.map { recordedUpload ->
            val plan = recordedUpload.resources
            GPUTask.Upload(
                taskId = recordedUpload.taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                textureResourcePlan = plan,
            )
        }
        val materialUploads = recordedMaterialUploads.map { recordedUpload ->
            val plan = recordedUpload.resources
            GPUTask.Upload(
                taskId = recordedUpload.taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                textureResourcePlan = plan,
            )
        }
        val baseRenderByPacketId = baseRenders.flatMap { render ->
            render.drawPackets.map { packet -> packet.packetId to render }
        }.toMap()
        val preparedRenderByPacketId = baseRenderByPacketId +
            coreAssembly.renderByPacketId
        val orderedPreparedPackets = packets.flatMap { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            if (semantic is GPUDrawSemanticPayload.CorePrimitive) {
                coreAssembly.packetByCommandId.getValue(packet.commandIdValue)
            } else {
                listOf(packet.withSemantic(semantic))
            }
        }
        val routeRuns = orderedPreparedPackets.contiguousRouteRuns(preparedRenderByPacketId)
        val predictedTaskCount = 1L +
            recordedImageUploads.size +
            recordedR8Uploads.size +
            recordedMaterialUploads.size +
            routeRuns.size +
            if (readbackRequest != null) 1L else 0L
        val predictedDependencyCount =
            recordedImageUploads.size.toLong() +
                recordedR8Uploads.size.toLong() +
                recordedMaterialUploads.size.toLong() +
                routeRuns.size.toLong() +
                routeRuns.sumOf { run ->
                    run.mapNotNull { packet ->
                        (packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage)
                            ?.artifact
                            ?.key
                    }.distinct().size.toLong()
                } +
                routeRuns.sumOf { run ->
                    run.mapNotNull { packet ->
                        packet.semanticPayload
                            ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                            ?.exactR8ArtifactIdentity()
                    }.distinct().size.toLong()
                } +
                routeRuns.sumOf { run ->
                    run.flatMap { packet ->
                        packet.semanticPayload
                            ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                            ?.preparedTextMaterial()
                            ?.sampledResources
                            .orEmpty()
                    }.map { resource -> resource.resourceKey }.distinct().size.toLong()
                } +
                (routeRuns.size - 1).coerceAtLeast(0).toLong() +
                if (readbackRequest != null) 1L else 0L
        val taskGraphRefusal = taskGraphLimitRefusal(
            limits = taskGraphLimits,
            bufferAllocations = memoryBudget.allocations.count {
                it.resourceKind == GPUFrameMemoryResourceKind.Buffer
            },
            textureAllocations = memoryBudget.allocations.count {
                it.resourceKind == GPUFrameMemoryResourceKind.Texture2D
            },
            allocations = memoryBudget.allocations.size,
            tasks = predictedTaskCount,
            dependencies = predictedDependencyCount,
            instanceRanges = r8Semantics.size,
        )
        if (taskGraphRefusal != null) {
            return refused(taskGraphRefusal.code, taskGraphRefusal.message)
        }
        val renders = routeRuns.mapIndexed { index, run ->
            val original = preparedRenderByPacketId.getValue(run.first().packetId)
            val uses = if (run.first().semanticPayload is GPUDrawSemanticPayload.SampledImage) {
                run.flatMap { packet ->
                    val semantic = packet.semanticPayload as GPUDrawSemanticPayload.SampledImage
                    val plan = imagePlanByArtifactKey.getValue(semantic.artifact.key)
                    listOf(
                        GPUFrameResourceUse(
                            plan.frameTextureRef,
                            GPUFrameResourceRole.StorageData,
                            GPUFrameResourceUsage.TextureBinding,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                        GPUFrameResourceUse(
                            plan.uniformRef,
                            GPUFrameResourceRole.UniformData,
                            GPUFrameResourceUsage.Uniform,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                    )
                }.distinct()
            } else if (requireNotNull(run.first().semanticPayload).isPreparedTextSemantic()) {
                val atlasUses = run.map { packet ->
                    val plan = r8UploadByIdentity.getValue(
                        requireNotNull(packet.semanticPayload).exactR8ArtifactIdentity(),
                    ).resources
                    GPUFrameResourceUse(
                        plan.frameTextureRef,
                        GPUFrameResourceRole.GlyphAtlas,
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    )
                }.distinct()
                buildList {
                    addAll(atlasUses)
                    add(
                        GPUFrameResourceUse(
                            requireNotNull(textInstanceAssembly).plan.bufferRef,
                            GPUFrameResourceRole.VertexData,
                            GPUFrameResourceUsage.Vertex,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                    )
                    textDrawUniformAssembly?.plan
                        ?.takeIf {
                            run.any { packet ->
                                packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                            }
                        }
                        ?.let { plan ->
                            add(
                                GPUFrameResourceUse(
                                    plan.bufferRef,
                                    GPUFrameResourceRole.UniformData,
                                    GPUFrameResourceUsage.Uniform,
                                    GPUFrameResourceLifetime.FrameLocal,
                                    write = false,
                                ),
                            )
                        }
                    textMaterialUniformAssembly?.plan?.let { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.bufferRef,
                                GPUFrameResourceRole.UniformData,
                                GPUFrameResourceUsage.Uniform,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                    run.flatMap { packet ->
                        requireNotNull(packet.semanticPayload)
                            .preparedTextMaterial()
                            .sampledResources
                    }.map { resource ->
                        materialUploadByResourceKey.getValue(resource.resourceKey).resources
                    }.distinctBy { plan -> plan.resourceKey }.forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.frameTextureRef,
                                GPUFrameResourceRole.StorageData,
                                GPUFrameResourceUsage.TextureBinding,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                }
            } else {
                run.flatMap { packet ->
                    coreAssembly.resourceUsesByCommandId[packet.commandIdValue].orEmpty()
                }.distinct()
            }
            GPUTask.Render(
                taskId = GPUTaskID(
                    "task.prepared-surface.render.${request.baseTaskList.frameId.value}.$index",
                ),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan(
                    loadOp = if (index == 0) "clear" else "load",
                    storePlan = GPUStorePlan.Store,
                ),
                samplePlan = original.samplePlan,
                resourceUses = uses,
                provisionalSegmentKey = original.provisionalSegmentKey,
                drawPackets = run,
                batchEligibilityByPacketId = run.associate { packet ->
                    packet.packetId to
                        preparedRenderByPacketId.getValue(packet.packetId)
                            .batchEligibilityByPacketId.getValue(packet.packetId)
                },
                sampleContinuationKey = original.sampleContinuationKey,
                depthStencilLoadStore = original.depthStencilLoadStore?.takeIf {
                    run.any { packet ->
                        packet.role == org.graphiks.kanvas.gpu.renderer.passes
                            .GPUDrawPacketRole.PathStencilProducer ||
                            packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                .GPUDrawPacketRole.PathStencilCover
                    }
                },
                preparedImageBindingsByPacketId = run.mapNotNull { packet ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage
                        ?: return@mapNotNull null
                    val binding = imagePlanByArtifactKey.getValue(semantic.artifact.key)
                        .bindingRequests.single { request ->
                            request.packetId == packet.packetId.value
                        }
                    packet.packetId to binding
                }.toMap(),
                preparedTextBindingsByPacketId = run.mapNotNull { packet ->
                    val semantic = packet.semanticPayload
                        ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                        ?: return@mapNotNull null
                    val range = requireNotNull(textInstanceAssembly)
                        .rangesByCommandId
                        .getValue(packet.commandIdValue)
                    val materialUniformRange = requireNotNull(textMaterialUniformAssembly)
                        .rangesByCommandId
                        .getValue(packet.commandIdValue)
                    val material = semantic.preparedTextMaterial()
                    val drawUniformPlan = if (semantic is GPUDrawSemanticPayload.TextA8) {
                        requireNotNull(textDrawUniformAssembly).plan
                    } else {
                        null
                    }
                    val drawUniformSlice = if (semantic is GPUDrawSemanticPayload.TextA8) {
                        requireNotNull(textDrawUniformAssembly)
                            .slicesByPacketId
                            .getValue(packet.packetId)
                    } else {
                        null
                    }
                    val compositeProgram = if (semantic is GPUDrawSemanticPayload.TextA8) {
                        compositeProgramsByPacketId.getValue(packet.packetId)
                    } else {
                        null
                    }
                    packet.packetId to GPUPreparedTextRenderBinding(
                        packetId = packet.packetId,
                        atlasResourcePlan = r8UploadByIdentity
                            .getValue(semantic.exactR8ArtifactIdentity())
                            .resources,
                        instanceBufferPlan = textInstanceAssembly.plan,
                        firstInstance = range.firstInstance,
                        instanceCount = range.instanceCount,
                        materialUniformBufferPlan = textMaterialUniformAssembly.plan,
                        materialUniformOffsetBytes = materialUniformRange.offsetBytes,
                        materialUniformSizeBytes = materialUniformRange.sizeBytes,
                        materialSampledResourcePlans = material.sampledResources.map { resource ->
                            materialUploadByResourceKey.getValue(resource.resourceKey).resources
                        },
                        preflightSeal = semantic.preparedTextPreflightSeal(
                            packet = packet,
                            material = material,
                            atlasResourcePlan = r8UploadByIdentity
                                .getValue(semantic.exactR8ArtifactIdentity())
                                .resources,
                            instanceBufferPlan = textInstanceAssembly.plan,
                            firstInstance = range.firstInstance,
                            instanceCount = range.instanceCount,
                            materialUniformOffsetBytes = materialUniformRange.offsetBytes,
                            materialUniformSizeBytes = materialUniformRange.sizeBytes,
                            drawUniformBufferPlan = drawUniformPlan,
                            drawUniformSlice = drawUniformSlice,
                            compositeProgram = compositeProgram,
                        ),
                        drawUniformBufferPlanOrNull = drawUniformPlan,
                        drawUniformSliceOrNull = drawUniformSlice,
                        compositeProgramOrNull = compositeProgram,
                    )
                }.toMap(),
            )
        }

        val dependencies = mutableListOf<GPUTaskDependency>()
        uploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-image-resource-order",
                "prepared.image.prepare-before-upload",
                "prepared-image.prepare.$index",
            )
        }
        r8Uploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-text-resource-order",
                "prepared.text.prepare-before-upload",
                "prepared-text.prepare.$index",
            )
        }
        materialUploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-text-material-resource-order",
                "prepared.text.material-prepare-before-upload",
                "prepared-text.material-prepare.$index",
            )
        }
        renders.forEachIndexed { index, render ->
            dependencies += dependency(
                prepareTask.taskId,
                render.taskId,
                "prepared-surface-resource-order",
                "prepared.surface.prepare-before-consumer",
                "prepared-surface.prepare.$index",
            )
            render.drawPackets
                .mapNotNull { packet -> packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage }
                .map { semantic -> imageUploadByArtifactKey.getValue(semantic.artifact.key).taskId }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-image-resource-order",
                        "prepared.image.upload-before-consumer",
                        "prepared-image.consumer.${dependencies.size}",
                    )
                }
            render.drawPackets
                .mapNotNull { packet ->
                    packet.semanticPayload?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                }
                .map { semantic ->
                    r8UploadByIdentity.getValue(semantic.exactR8ArtifactIdentity()).taskId
                }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-text-resource-order",
                        "prepared.text.upload-before-consumer",
                        "prepared-text.consumer.${dependencies.size}",
                    )
                }
            render.drawPackets
                .flatMap { packet ->
                    packet.semanticPayload
                        ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                        ?.preparedTextMaterial()
                        ?.sampledResources
                        .orEmpty()
                }
                .map { resource ->
                    materialUploadByResourceKey.getValue(resource.resourceKey).taskId
                }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-text-material-resource-order",
                        "prepared.text.material-upload-before-consumer",
                        "prepared-text.material-consumer.${dependencies.size}",
                    )
                }
        }
        renders.zipWithNext().forEachIndexed { index, (from, to) ->
            dependencies += dependency(
                from.taskId,
                to.taskId,
                "prepared-scene-order",
                "preserve.prepared-scene.order",
                "prepared-surface.paint.$index",
            )
        }
        val tasks = mutableListOf<GPUTask>(prepareTask)
        tasks += uploads
        tasks += r8Uploads
        tasks += materialUploads
        tasks += renders
        if (readbackRequest != null && readbackStaging != null) {
            val readbackTask = GPUTask.Readback(
                taskId = GPUTaskID("task.prepared-surface.readback.${request.baseTaskList.frameId.value}"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Readback,
                source = request.target,
                staging = readbackStaging,
                request = readbackRequest,
            )
            tasks += readbackTask
            dependencies += dependency(
                renders.last().taskId,
                readbackTask.taskId,
                "prepared-surface-readback-order",
                "prepared.surface.render-before-readback",
                "prepared-surface.readback",
            )
        }
        val colorDiagnostic = GPUDiagnostic(
            code = GPUDiagnosticCode("info.recording.prepared_image_color_contract"),
            domain = GPUDiagnosticDomain.Color,
            severity = GPUDiagnosticSeverity.Info,
            message =
                "Prepared color images upload straight encoded sRGB bytes through an sRGB source " +
                    "texture and shade as linear-premultiplied values into the declared target.",
            facts = mapOf(
                "image.upload.format" to "RGBA8UnormSrgb",
                "image.upload.encoding" to "StraightEncodedSrgb",
                "image.upload.interpretation" to "StraightEncodedSrgb",
                "image.target.format" to request.targetFormat.value,
                "image.shader.interpretation" to "LinearPremul",
                "image.attachment.srgbConversion" to
                    (request.targetFormat == GPUColorFormat.RGBA8UnormSrgb).toString(),
            ),
        )
        val diagnostics = if (imageSemantics.isEmpty()) {
            request.baseTaskList.diagnostics
        } else {
            request.baseTaskList.diagnostics + colorDiagnostic
        }
        return GPUPreparedSurfaceFrameResult.Recorded(
            GPUTaskList(
                frameId = request.baseTaskList.frameId,
                capabilitySeal = request.baseTaskList.capabilitySeal,
                recordingSeals = request.baseTaskList.recordingSeals,
                expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
                tasks = tasks,
                dependencies = dependencies.distinct(),
                phaseOrder = request.baseTaskList.phaseOrder,
                memoryBudget = memoryBudget,
                diagnostics = diagnostics,
            ),
        )
    }

    private fun prepareMixedCoreAuthority(
        request: GPUPreparedSurfaceFrameRequest,
        packets: List<GPUDrawPacket>,
        configuredAggregateBudgetBytes: Long,
        additionalMemoryAllocations: List<GPUFrameMemoryAllocation>,
    ): MixedCoreAssembly {
        val corePackets = packets.mapNotNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.CorePrimitive ?: return@mapNotNull null
            packet.withSemantic(semantic)
        }
        if (corePackets.isEmpty()) {
            return MixedCoreAssembly.Prepared(
                packetByCommandId = emptyMap(),
                resourceUsesByCommandId = emptyMap(),
                preparations = emptyList(),
                memoryBudget = null,
            )
        }
        val baseRenderByPacketId = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap { render -> render.drawPackets.map { packet -> packet.packetId to render } }
            .toMap()
        val coreRenders = corePackets.mapIndexed { index, packet ->
            val base = baseRenderByPacketId.getValue(packet.packetId)
            GPUTask.Render(
                taskId = GPUTaskID("task.prepared-surface.core-base.$index"),
                recordingId = base.recordingId,
                phase = GPUTaskPhase.Render,
                target = base.target,
                loadStore = base.loadStore,
                samplePlan = base.samplePlan,
                resourceUses = base.resourceUses,
                provisionalSegmentKey = base.provisionalSegmentKey,
                drawPackets = listOf(packet),
                batchEligibilityByPacketId = mapOf(
                    packet.packetId to base.batchEligibilityByPacketId.getValue(packet.packetId),
                ),
                sampleContinuationKey = base.sampleContinuationKey,
                depthStencilLoadStore = base.depthStencilLoadStore,
            )
        }
        val coreBase = GPUTaskList(
            frameId = request.baseTaskList.frameId,
            capabilitySeal = request.baseTaskList.capabilitySeal,
            recordingSeals = request.baseTaskList.recordingSeals,
            expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
            tasks = coreRenders,
            dependencies = emptyList(),
            phaseOrder = request.baseTaskList.phaseOrder,
            memoryBudget = request.baseTaskList.memoryBudget,
            diagnostics = request.baseTaskList.diagnostics,
        )
        val coreSemantics = request.semanticsByCommandId.mapNotNull { (commandId, semantic) ->
            (semantic as? GPUDrawSemanticPayload.CorePrimitive)?.let { commandId to it }
        }.toMap()
        return when (
            val result = GPUCorePrimitivePreparedFrameTaskListAssembler(readbackLayoutPlanner).build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = coreBase,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = request.targetBounds,
                    semanticsByCommandId = coreSemantics,
                    readbackRequestId = null,
                    configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                    targetFormat = request.targetFormat,
                ),
                additionalMemoryAllocations = additionalMemoryAllocations,
            )
        ) {
            is GPUCorePrimitivePreparedFrameResult.Refused ->
                MixedCoreAssembly.Refused(result.diagnostic)
            is GPUCorePrimitivePreparedFrameResult.Recorded -> {
                val renders = result.taskList.tasks.filterIsInstance<GPUTask.Render>()
                val consumerByCommandId = coreSemantics.keys.associateWith { commandId ->
                    renders.singleOrNull { render ->
                        render.drawPackets.any { packet -> packet.commandIdValue == commandId }
                    }
                }
                if (consumerByCommandId.values.any { it == null } ||
                    renders.any { render ->
                        render.drawPackets.none { packet -> packet.commandIdValue in coreSemantics }
                    }
                ) {
                    MixedCoreAssembly.Refused(
                        diagnostic(
                            "unsupported.recording.prepared_surface_core_producer_topology",
                            "Mixed prepared surfaces do not yet interleave core producer passes with image runs.",
                        ),
                    )
                } else {
                    MixedCoreAssembly.Prepared(
                        packetByCommandId = consumerByCommandId.mapValues { (commandId, render) ->
                            val packetsForCommand = requireNotNull(render).drawPackets.filter { packet ->
                                packet.commandIdValue == commandId
                            }
                            if (packetsForCommand.any { packet ->
                                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                        .GPUDrawPacketRole.PathStencilProducer
                                }
                            ) {
                                packetsForCommand
                            } else {
                                packetsForCommand.map(
                                    GPUDrawPacket::withoutPreparedPathDepthStencil,
                                )
                            }
                        },
                        renderByPacketId = renders.flatMap { render ->
                            render.drawPackets.map { packet -> packet.packetId to render }
                        }.toMap(),
                        resourceUsesByCommandId = consumerByCommandId.mapValues { (commandId, render) ->
                            val exactRender = requireNotNull(render)
                            val hasPath = exactRender.drawPackets.any { packet ->
                                packet.commandIdValue == commandId &&
                                    packet.role in setOf(
                                        org.graphiks.kanvas.gpu.renderer.passes
                                            .GPUDrawPacketRole.PathStencilProducer,
                                        org.graphiks.kanvas.gpu.renderer.passes
                                            .GPUDrawPacketRole.PathStencilCover,
                                    )
                            }
                            exactRender.resourceUses.filter { use ->
                                hasPath ||
                                    use.role != GPUFrameResourceRole.PathDepthStencil
                            }
                        },
                        preparations = result.taskList.tasks
                            .filterIsInstance<GPUTask.PrepareResources>()
                            .flatMap(GPUTask.PrepareResources::requests),
                        memoryBudget = result.taskList.memoryBudget,
                    )
                }
            }
        }
    }

    private fun refused(code: String, message: String) =
        GPUPreparedSurfaceFrameResult.Refused(diagnostic(code, message))
}

private sealed interface MixedCoreAssembly {
    data class Prepared(
        val packetByCommandId: Map<Int, List<GPUDrawPacket>>,
        val renderByPacketId: Map<
            org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID,
            GPUTask.Render,
            > = emptyMap(),
        val resourceUsesByCommandId: Map<Int, List<GPUFrameResourceUse>>,
        val preparations: List<GPUResourcePreparationRequest>,
        val memoryBudget: GPUFrameMemoryBudgetPlan?,
    ) : MixedCoreAssembly

    data class Refused(val diagnostic: GPUDiagnostic) : MixedCoreAssembly
}

/** Public module boundary for the validated four-corner prepared-image geometry value. */
fun buildPreparedImageGeometry(
    geometryClass: GPUPreparedImageGeometryClass,
    vertices: List<GPUPreparedImageVertex>,
): GPUPreparedImageGeometry = GPUPreparedImageGeometry(
    geometryClass = geometryClass,
    vertices = vertices,
    indices = listOf(0, 1, 2, 0, 2, 3),
)

private fun packetForSemantic(
    packets: List<GPUDrawPacket>,
    semantic: GPUDrawSemanticPayload.SampledImage,
): GPUDrawPacket = packets.single {
    it.commandIdValue == semantic.payloadRef.commandIdValue
}

private data class PreparedRouteRunKey(
    val semanticKind: String,
    val passId: String,
    val renderStepId: String,
    val renderStepVersion: Int,
    val renderPipelineKey: String?,
    val bindingLayoutHash: String,
    val samplePlanKey: String,
    val target: String,
    val loadStore: GPULoadStorePlan,
    val provisionalSegmentKey:
        org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey,
    val depthStencilLoadStore: GPUDepthStencilLoadStorePlan?,
    val targetStateHash: String,
    val continuationKey: String?,
)

private fun List<GPUDrawPacket>.contiguousRouteRuns(
    baseRenderByPacketId: Map<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID, GPUTask.Render>,
): List<List<GPUDrawPacket>> {
    val runs = mutableListOf<MutableList<GPUDrawPacket>>()
    forEach { packet ->
        val render = baseRenderByPacketId.getValue(packet.packetId)
        val coreRouteIdentity = if (
            packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
        ) {
            render.taskId.value
        } else {
            null
        }
        val key = PreparedRouteRunKey(
            semanticKind = when (packet.semanticPayload) {
                is GPUDrawSemanticPayload.SampledImage -> "sampled-image"
                is GPUDrawSemanticPayload.CorePrimitive -> "core-primitive"
                is GPUDrawSemanticPayload.TextA8 -> "text-a8"
                is GPUDrawSemanticPayload.ColorGlyph -> "color-glyph"
                else -> "unsupported"
            },
            passId = packet.passId,
            renderStepId = coreRouteIdentity ?: packet.renderStepId.value,
            renderStepVersion = if (coreRouteIdentity == null) packet.renderStepVersion else 0,
            renderPipelineKey = if (coreRouteIdentity == null) {
                packet.renderPipelineKey?.value
            } else {
                null
            },
            bindingLayoutHash = coreRouteIdentity ?: packet.bindingLayoutHash,
            samplePlanKey = render.samplePlan.specializationKey,
            target = render.target.value,
            loadStore = render.loadStore,
            provisionalSegmentKey = render.provisionalSegmentKey,
            depthStencilLoadStore = render.depthStencilLoadStore,
            targetStateHash = coreRouteIdentity ?: packet.targetStateHash,
            continuationKey = render.sampleContinuationKey?.toString(),
        )
        val current = runs.lastOrNull()
        val currentKey = current?.firstOrNull()?.let { first ->
            val firstRender = baseRenderByPacketId.getValue(first.packetId)
            val firstCoreRouteIdentity = if (
                first.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            ) {
                firstRender.taskId.value
            } else {
                null
            }
            PreparedRouteRunKey(
                semanticKind = when (first.semanticPayload) {
                    is GPUDrawSemanticPayload.SampledImage -> "sampled-image"
                    is GPUDrawSemanticPayload.CorePrimitive -> "core-primitive"
                    is GPUDrawSemanticPayload.TextA8 -> "text-a8"
                    is GPUDrawSemanticPayload.ColorGlyph -> "color-glyph"
                    else -> "unsupported"
                },
                passId = first.passId,
                renderStepId = firstCoreRouteIdentity ?: first.renderStepId.value,
                renderStepVersion =
                    if (firstCoreRouteIdentity == null) first.renderStepVersion else 0,
                renderPipelineKey = if (firstCoreRouteIdentity == null) {
                    first.renderPipelineKey?.value
                } else {
                    null
                },
                bindingLayoutHash = firstCoreRouteIdentity ?: first.bindingLayoutHash,
                samplePlanKey = firstRender.samplePlan.specializationKey,
                target = firstRender.target.value,
                loadStore = firstRender.loadStore,
                provisionalSegmentKey = firstRender.provisionalSegmentKey,
                depthStencilLoadStore = firstRender.depthStencilLoadStore,
                targetStateHash = firstCoreRouteIdentity ?: first.targetStateHash,
                continuationKey = firstRender.sampleContinuationKey?.toString(),
            )
        }
        if (current == null || currentKey != key) {
            runs += mutableListOf(packet)
        } else {
            current += packet
        }
    }
    return runs
}

private fun GPUDrawPacket.withSemantic(
    semantic: GPUDrawSemanticPayload,
    clipCoverageOverride: GPUClipCoveragePlan? = clipCoveragePlan,
    clipExecutionOverride: GPUClipExecutionPlan? = clipExecutionPlan,
) = GPUDrawPacket(
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
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = bindingLayoutHash,
    uniformSlot = uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semantic,
    vertexSourceLabel = vertexSourceLabel,
    scissorBoundsHash = scissorBoundsHash,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoverageOverride,
    clipExecutionPlan = clipExecutionOverride,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun GPUDrawPacket.withoutPreparedPathDepthStencil(): GPUDrawPacket {
    val authority = requireNotNull(corePrimitivePreparedAuthority)
    val structural = authority.structuralPipelineKey.copy(
        depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
    )
    val pipeline = structural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
    val rebuilt = GPUDrawPacket(
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
        renderPipelineKey = pipeline,
        computePipelineKey = computePipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = uniformSlot,
        resourceSlot = resourceSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        frameProvenance = frameProvenance,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
        diagnostics = diagnostics,
        clipProducerAuthority = clipProducerAuthority,
    )
    return rebuilt.attachCorePrimitivePreparedAuthority(
        authority.copy(
            structuralPipelineKey = structural,
            renderPipelineKey = pipeline,
        ),
    )
}

private fun dependency(
    from: GPUTaskID,
    to: GPUTaskID,
    kind: String,
    reason: String,
    token: String,
) = GPUTaskDependency(
    fromTaskId = from,
    toTaskId = to,
    dependencyKind = kind,
    useToken = GPUTaskUseToken(token),
    reasonCode = reason,
)

private fun diagnostic(code: String, message: String) = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
)

private fun GPUDiagnostic.atRecordingBoundary(): GPUDiagnostic =
    if (code.value in GPUPreparedImageRefusalCodes.ALL) {
        copy(facts = facts + ("boundary" to "recording"))
    } else {
        this
    }

private data class PreparedR8ArtifactIdentity(
    val key: String,
    val generation: Long,
    val contentHash: String,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
)

private data class PreparedSurfaceTaskGraphRefusal(
    val code: String,
    val message: String,
)

private fun taskGraphLimitRefusal(
    limits: GPUPreparedSurfaceTaskGraphLimits,
    bufferAllocations: Int,
    textureAllocations: Int,
    allocations: Int,
    tasks: Long,
    dependencies: Long,
    instanceRanges: Int,
): PreparedSurfaceTaskGraphRefusal? {
    if (bufferAllocations > limits.maxBufferAllocations) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_buffer_allocation_budget",
            "Prepared-surface buffer allocation count exceeds its configured limit.",
        )
    }
    if (textureAllocations > limits.maxTextureAllocations) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_texture_allocation_budget",
            "Prepared-surface texture allocation count exceeds its configured limit.",
        )
    }
    if (allocations > limits.maxAllocations) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_allocation_budget",
            "Prepared-surface allocation count exceeds its configured limit.",
        )
    }
    if (tasks > limits.maxTasks.toLong()) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_task_budget",
            "Prepared-surface task count exceeds its configured limit.",
        )
    }
    if (dependencies > limits.maxDependencies.toLong()) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_dependency_budget",
            "Prepared-surface dependency count exceeds its configured limit.",
        )
    }
    if (instanceRanges > limits.maxInstanceRanges) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_text_instance_range_budget",
            "Prepared-text instance-range count exceeds its configured limit.",
        )
    }
    return null
}

private const val PREPARED_TEXT_INSTANCE_ALIGNMENT_BYTES = 16

private data class PreparedTextInstanceRange(
    val firstInstance: Int,
    val instanceCount: Int,
)

private sealed interface PreparedTextInstanceAssemblyResult {
    class Prepared(
        val plan: GPUPreparedTextInstanceBufferPlan,
        val rangesByCommandId: Map<Int, PreparedTextInstanceRange>,
    ) : PreparedTextInstanceAssemblyResult

    data class Refused(
        val code: String,
        val message: String,
    ) : PreparedTextInstanceAssemblyResult
}

private fun buildPreparedTextInstanceAssembly(
    semantics: List<GPUDrawSemanticPayload>,
    frameIdentity: String,
    capabilities: GPUCapabilities,
): PreparedTextInstanceAssemblyResult {
    val totalInstances = try {
        semantics.fold(0) { count, semantic ->
            Math.addExact(count, semantic.preparedTextInstances().size)
        }
    } catch (_: ArithmeticException) {
        return PreparedTextInstanceAssemblyResult.Refused(
            "unsupported.recording.prepared_text_instance_range",
            "Prepared text instance count overflowed.",
        )
    }
    val byteSize = try {
        Math.multiplyExact(
            totalInstances.toLong(),
            GPUTextA8Instance.ENCODED_BYTE_SIZE.toLong(),
        )
    } catch (_: ArithmeticException) {
        return PreparedTextInstanceAssemblyResult.Refused(
            "unsupported.recording.prepared_text_instance_buffer",
            "Prepared text instance byte size overflowed.",
        )
    }
    if (byteSize > Int.MAX_VALUE.toLong() ||
        capabilities.limits?.maxBufferSize?.let { byteSize > it } == true
    ) {
        return PreparedTextInstanceAssemblyResult.Refused(
            "unsupported.recording.prepared_text_instance_buffer",
            "Prepared text instance buffer exceeds the observed allocation limit.",
        )
    }
    val bytes = ByteBuffer.allocate(byteSize.toInt())
        .order(ByteOrder.LITTLE_ENDIAN)
        .also { target ->
            semantics.forEach { semantic ->
                semantic.preparedTextInstances().forEach { instance ->
                    instance.deviceQuad.forEach(target::putFloat)
                    target.putFloat(instance.uvRect.left)
                    target.putFloat(instance.uvRect.top)
                    target.putFloat(instance.uvRect.right)
                    target.putFloat(instance.uvRect.bottom)
                    target.putInt(instance.glyphId)
                    target.putInt(instance.sourceGlyphIndex.value)
                    target.putInt(instance.pageIndex)
                    target.putInt(instance.colorLayerIndex ?: -1)
                }
            }
        }
        .array()
    val contentHash = bytes.sha256Hex()
    val plan = GPUPreparedTextInstanceBufferPlan(
        bufferRef = GPUFrameBufferRef(
            "buffer.prepared-text.instances:$frameIdentity:$contentHash",
        ),
        strideBytes = GPUTextA8Instance.ENCODED_BYTE_SIZE,
        alignmentBytes = PREPARED_TEXT_INSTANCE_ALIGNMENT_BYTES,
        instanceCount = totalInstances,
        byteSize = byteSize,
        contentHash = contentHash,
        uploadBytes = bytes,
    )
    var firstInstance = 0
    val ranges = linkedMapOf<Int, PreparedTextInstanceRange>()
    semantics.forEach { semantic ->
        val count = semantic.preparedTextInstances().size
        ranges[semantic.payloadRef.commandIdValue] = PreparedTextInstanceRange(
            firstInstance = firstInstance,
            instanceCount = count,
        )
        firstInstance = Math.addExact(firstInstance, count)
    }
    return PreparedTextInstanceAssemblyResult.Prepared(plan, ranges)
}

private data class PreparedTextMaterialUniformRange(
    val offsetBytes: Long,
    val sizeBytes: Long,
)

private data class PreparedTextMaterialUniformIdentity(
    val materialKey: String,
    val abiHash: String,
    val sourceKind: String,
    val paintAlphaBits: Int,
    val bytes: List<Int>,
)

private data class PreparedTextMaterialUniformAssembly(
    val plan: GPUPreparedTextMaterialUniformBufferPlan?,
    val rangesByCommandId: Map<Int, PreparedTextMaterialUniformRange>,
)

private sealed interface PreparedTextMaterialUniformAssemblyResult {
    data class Prepared(val assembly: PreparedTextMaterialUniformAssembly) :
        PreparedTextMaterialUniformAssemblyResult

    data class Refused(val code: String, val message: String) :
        PreparedTextMaterialUniformAssemblyResult
}

private fun buildPreparedTextMaterialUniformAssembly(
    semantics: List<GPUDrawSemanticPayload>,
    frameIdentity: String,
    capabilities: GPUCapabilities,
): PreparedTextMaterialUniformAssemblyResult {
    val limits = requireNotNull(capabilities.limits)
    val alignment = limits.minUniformBufferOffsetAlignment
    val offsetByIdentity = linkedMapOf<PreparedTextMaterialUniformIdentity, Long>()
    val bytesByIdentity = linkedMapOf<PreparedTextMaterialUniformIdentity, ByteArray>()
    val ranges = linkedMapOf<Int, PreparedTextMaterialUniformRange>()
    var byteSize = 0L
    for (semantic in semantics) {
        val material = semantic.preparedTextMaterial()
        if (!material.paintAlpha.isFinite() || material.paintAlpha !in 0f..1f ||
            material.uniformBytes.any { it !in 0..255 }
        ) {
            return PreparedTextMaterialUniformAssemblyResult.Refused(
                "invalid.recording.prepared_text_material_uniform",
                "Prepared text material uniforms and paint alpha must retain validated values.",
            )
        }
        if (material.uniformBytes.isEmpty()) {
            ranges[semantic.payloadRef.commandIdValue] =
                PreparedTextMaterialUniformRange(0L, 0L)
            continue
        }
        val identity = PreparedTextMaterialUniformIdentity(
            materialKey = material.materialKey,
            abiHash = material.abiHash,
            sourceKind = material.sourceKind.name,
            paintAlphaBits = material.paintAlpha.toRawBits(),
            bytes = material.uniformBytes,
        )
        val offset = offsetByIdentity[identity] ?: try {
            alignUpPreparedText(byteSize, alignment).also { alignedOffset ->
                byteSize = Math.addExact(alignedOffset, material.uniformBytes.size.toLong())
                offsetByIdentity[identity] = alignedOffset
                bytesByIdentity[identity] =
                    ByteArray(material.uniformBytes.size) { index ->
                        material.uniformBytes[index].toByte()
                    }
            }
        } catch (_: ArithmeticException) {
            return PreparedTextMaterialUniformAssemblyResult.Refused(
                "unsupported.recording.prepared_text_material_uniform_buffer",
                "Prepared text material uniform offsets overflowed.",
            )
        }
        ranges[semantic.payloadRef.commandIdValue] = PreparedTextMaterialUniformRange(
            offsetBytes = offset,
            sizeBytes = material.uniformBytes.size.toLong(),
        )
    }
    if (byteSize == 0L) {
        return PreparedTextMaterialUniformAssemblyResult.Prepared(
            PreparedTextMaterialUniformAssembly(null, ranges),
        )
    }
    if (byteSize > Int.MAX_VALUE.toLong() ||
        limits.maxBufferSize?.let { byteSize > it } == true
    ) {
        return PreparedTextMaterialUniformAssemblyResult.Refused(
            "unsupported.recording.prepared_text_material_uniform_buffer",
            "Prepared text material uniform buffer exceeds the observed allocation limit.",
        )
    }
    val bytes = ByteArray(byteSize.toInt())
    bytesByIdentity.forEach { (identity, materialBytes) ->
        materialBytes.copyInto(bytes, offsetByIdentity.getValue(identity).toInt())
    }
    val contentHash = bytes.sha256Hex()
    return PreparedTextMaterialUniformAssemblyResult.Prepared(
        PreparedTextMaterialUniformAssembly(
            plan = GPUPreparedTextMaterialUniformBufferPlan(
                bufferRef = GPUFrameBufferRef(
                    "buffer.prepared-text.material-uniforms:$frameIdentity:$contentHash",
                ),
                alignmentBytes = alignment,
                byteSize = byteSize,
                contentHash = contentHash,
                uploadBytes = bytes,
            ),
            rangesByCommandId = ranges,
        ),
    )
}

private fun alignUpPreparedText(value: Long, alignment: Long): Long {
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

private fun GPUDrawSemanticPayload.preparedTextInstances(): List<GPUTextA8Instance> = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> instances
    is GPUDrawSemanticPayload.ColorGlyph -> instances
    else -> error("Only prepared text semantics own instance records")
}

private fun GPUDrawSemanticPayload.preparedTextMaterial() = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> material
    is GPUDrawSemanticPayload.ColorGlyph -> requireNotNull(material)
    else -> error("Only prepared text semantics own prepared material programs")
}

private fun GPUDrawSemanticPayload.preparedTextPreflightSeal(
    packet: GPUDrawPacket,
    material: org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram,
    atlasResourcePlan: GPUR8FrameResourcePlan,
    instanceBufferPlan: GPUPreparedTextInstanceBufferPlan,
    firstInstance: Int,
    instanceCount: Int,
    materialUniformOffsetBytes: Long,
    materialUniformSizeBytes: Long,
    drawUniformBufferPlan: GPUPreparedTextDrawUniformBufferPlan?,
    drawUniformSlice: GPUPreparedTextDrawUniformSlice?,
    compositeProgram: GPUPreparedTextCompositeProgram?,
): GPUPreparedTextBindingPreflightSeal {
    val targetBounds: GPUPixelBounds
    val scissorBounds: GPUPixelBounds
    val clipIdentity: String
    val blendPlanIdentity: String
    val capabilitySnapshotHash: String
    val canonicalHash: String
    val pageIndex: Int
    val textA8Composite: GPUPreparedTextCompositePreflightSeal?
    when (this) {
        is GPUDrawSemanticPayload.TextA8 -> {
            targetBounds = this.targetBounds
            scissorBounds = this.scissorBounds
            clipIdentity = this.clipIdentity
            blendPlanIdentity = this.blendPlanIdentity
            capabilitySnapshotHash = this.capabilitySnapshotHash
            canonicalHash = this.canonicalHash
            pageIndex = this.pageIndex
            val exactPlan = requireNotNull(drawUniformBufferPlan)
            val exactSlice = requireNotNull(drawUniformSlice)
            val exactProgram = requireNotNull(compositeProgram)
            textA8Composite = GPUPreparedTextCompositePreflightSeal(
                deviceToLocal = deviceToLocal,
                drawUniformBufferRef = exactPlan.bufferRef,
                drawUniformAlignmentBytes = exactPlan.alignmentBytes,
                drawUniformLogicalSliceSizeBytes = exactPlan.logicalSliceSizeBytes,
                drawUniformBufferByteSize = exactPlan.byteSize,
                drawUniformBufferContentHash = exactPlan.contentHash,
                drawUniformSlice = exactSlice,
                compositeSourceHash = exactProgram.sourceHash,
                compositeAbiHash = exactProgram.abiHash,
                compositePipelineKey = exactProgram.pipelineKey,
                compositeVertexEntryPoint = exactProgram.vertexEntryPoint,
                compositeFragmentEntryPoint = exactProgram.fragmentEntryPoint,
                compositeVertexLayout = exactProgram.vertexLayout,
                compositeAdmissionToken = exactProgram.admissionToken,
            )
        }
        is GPUDrawSemanticPayload.ColorGlyph -> {
            targetBounds = this.targetBounds
            scissorBounds = this.scissorBounds
            clipIdentity = requireNotNull(this.clipIdentity)
            blendPlanIdentity = requireNotNull(this.blendPlanIdentity)
            capabilitySnapshotHash = requireNotNull(this.capabilitySnapshotHash)
            canonicalHash = this.canonicalHash
            pageIndex = this.instances.first().pageIndex
            require(
                drawUniformBufferPlan == null &&
                    drawUniformSlice == null &&
                    compositeProgram == null,
            ) {
                "ColorGlyph composition remains deferred to Task 11"
            }
            textA8Composite = null
        }
        else -> error("Only prepared text semantics own preflight seals")
    }
    return GPUPreparedTextBindingPreflightSeal(
        semanticCanonicalHash = canonicalHash,
        atlasKey = atlasResourcePlan.artifactKey,
        atlasWidth = atlasResourcePlan.artifactWidth,
        atlasHeight = atlasResourcePlan.artifactHeight,
        atlasRowBytes = atlasResourcePlan.artifactRowBytes,
        atlasGeneration = atlasResourcePlan.artifactGeneration,
        atlasContentHash = atlasResourcePlan.artifactContentHash,
        pageIndex = pageIndex,
        instanceStrideBytes = instanceBufferPlan.strideBytes,
        firstInstance = firstInstance,
        instanceCount = instanceCount,
        instanceBufferByteSize = instanceBufferPlan.byteSize,
        instanceBufferContentHash = instanceBufferPlan.contentHash,
        materialUniformOffsetBytes = materialUniformOffsetBytes,
        materialUniformSizeBytes = materialUniformSizeBytes,
        materialKey = material.materialKey,
        materialWgslSourceHash = material.wgslSource.toByteArray().sha256Hex(),
        materialEntryPoint = material.entryPoint,
        materialAbiHash = material.abiHash,
        materialUniformContentHash = material.uniformBytes
            .map(Int::toByte)
            .toByteArray()
            .sha256Hex(),
        materialSampledResourceFacts = material.sampledResources.flatMap { resource ->
            resource.identityFacts()
        },
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipIdentity = clipIdentity,
        blendPlanIdentity = blendPlanIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        textA8Composite = textA8Composite,
        packetAuthority = GPUPreparedTextPacketAuthoritySeal(
            commandIdValue = packet.commandIdValue,
            renderStepIdentity = packet.renderStepId.value,
            renderPipelineKey = requireNotNull(packet.renderPipelineKey).value,
            bindingLayoutHash = packet.bindingLayoutHash,
            vertexSourceLabel = packet.vertexSourceLabel,
            targetStateHash = packet.targetStateHash,
            scissorBoundsHash = packet.scissorBoundsHash,
        ),
    )
}

private fun GPUDrawSemanticPayload.isPreparedTextSemantic(): Boolean =
    this is GPUDrawSemanticPayload.TextA8 || this is GPUDrawSemanticPayload.ColorGlyph

private fun GPUDrawSemanticPayload.r8Artifact() = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> atlas
    is GPUDrawSemanticPayload.ColorGlyph -> atlas
    else -> error("Only prepared text semantics own an R8 artifact")
}

private fun GPUDrawSemanticPayload.exactR8ArtifactIdentity(): PreparedR8ArtifactIdentity =
    r8Artifact().let { artifact ->
        PreparedR8ArtifactIdentity(
            key = artifact.key,
            generation = artifact.generation,
            contentHash = artifact.contentHash,
            width = artifact.width,
            height = artifact.height,
            rowBytes = artifact.rowBytes,
        )
    }

private fun GPUR8FrameResourcePlan.exactR8ArtifactIdentity(): PreparedR8ArtifactIdentity =
    PreparedR8ArtifactIdentity(
        key = artifactKey,
        generation = artifactGeneration,
        contentHash = artifactContentHash,
        width = artifactWidth,
        height = artifactHeight,
        rowBytes = artifactRowBytes,
    )

private fun ByteArray.sha256Hex(): String =
    buildString(64) {
        MessageDigest.getInstance("SHA-256")
            .digest(this@sha256Hex)
            .forEach { byte ->
                val value = byte.toInt() and 0xff
                append(LOWER_HEX_DIGITS[value ushr 4])
                append(LOWER_HEX_DIGITS[value and 0x0f])
            }
    }

private const val LOWER_HEX_DIGITS = "0123456789abcdef"
