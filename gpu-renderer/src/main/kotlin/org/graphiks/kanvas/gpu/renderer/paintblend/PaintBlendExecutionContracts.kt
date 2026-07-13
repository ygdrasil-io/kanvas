package org.graphiks.kanvas.gpu.renderer.paintblend

import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialAssemblyPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourcePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintPipelinePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintStagePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidMaterialDictionary
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.dumpLines
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendAllowlistGatePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanKind
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeys
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.ValidatingPayloadResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

/** Request for materializing the accepted paint dictionary plus blend-plan execution boundary. */
data class GPUPaintBlendExecutionRequest(
    val label: String,
    val paintPlan: GPUPaintPipelinePlan,
    val materialAssembly: GPUMaterialAssemblyPlan,
    val blendGate: GPUBlendAllowlistGatePlan,
    val payloadRequest: GPUPayloadMaterializationRequest,
    val expectedMaterialKey: String,
    val expectedDictionaryVersion: String,
    val expectedRootSetId: String,
    val expectedSnippetIds: List<String>,
    val expectedPayloadPlanHash: String,
    val pipelineCacheKey: String,
    val targetStateHash: String,
    val loadStoreLabel: String,
    val passId: String,
    val packetStreamId: String,
    val streamId: String,
    val vertexSourceLabel: String,
) {
    init {
        require(label.isNotBlank()) { "GPUPaintBlendExecutionRequest.label must not be blank" }
        require(expectedMaterialKey.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.expectedMaterialKey must not be blank"
        }
        require(expectedDictionaryVersion.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.expectedDictionaryVersion must not be blank"
        }
        require(expectedRootSetId.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.expectedRootSetId must not be blank"
        }
        require(expectedSnippetIds.none { snippetId -> snippetId.isBlank() }) {
            "GPUPaintBlendExecutionRequest.expectedSnippetIds must not contain blank labels"
        }
        require(expectedPayloadPlanHash.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.expectedPayloadPlanHash must not be blank"
        }
        require(pipelineCacheKey.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.pipelineCacheKey must not be blank"
        }
        require(targetStateHash.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.targetStateHash must not be blank"
        }
        require(loadStoreLabel.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.loadStoreLabel must not be blank"
        }
        require(passId.isNotBlank()) { "GPUPaintBlendExecutionRequest.passId must not be blank" }
        require(packetStreamId.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.packetStreamId must not be blank"
        }
        require(streamId.isNotBlank()) { "GPUPaintBlendExecutionRequest.streamId must not be blank" }
        require(vertexSourceLabel.isNotBlank()) {
            "GPUPaintBlendExecutionRequest.vertexSourceLabel must not be blank"
        }
    }
}

/** Result of paint dictionary plus blend-plan execution materialization. */
data class GPUPaintBlendExecutionResult(
    val evidenceRow: String,
    val materialKey: String,
    val materialProgramId: String?,
    val dictionaryVersion: String?,
    val rootSetId: String?,
    val snippetIds: List<String>,
    val blendMode: String?,
    val blendPlanKind: GPUBlendPlanKind?,
    val targetFormatClass: String?,
    val blendStateHash: String?,
    val renderPipelineKey: String?,
    val pipelineCacheKey: String?,
    val payloadFingerprint: String?,
    val destinationReadLabel: String?,
    val resourceDecision: GPUResourceMaterializationDecision,
    val commandStream: GPUPassCommandStream?,
    val uniformValuesInKey: Boolean = false,
    val destinationResourcesInKey: Boolean = false,
    val adapterBacked: Boolean = false,
    val productActivation: Boolean = true,
) {
    init {
        require(!adapterBacked) { "GPUPaintBlendExecutionResult.adapterBacked must stay false" }
    }

    /** Emits deterministic PM/review evidence for this execution boundary. */
    fun dumpLines(): List<String> {
        val head = if (resourceDecision is GPUResourceMaterializationDecision.Materialized) {
            "paint-blend:execution row=$evidenceRow " +
                "material=$materialKey program=${materialProgramId ?: NONE_PAINT_BLEND_VALUE} " +
                "dictionary=${dictionaryVersion ?: NONE_PAINT_BLEND_VALUE} " +
                "root=${rootSetId ?: NONE_PAINT_BLEND_VALUE} snippets=${snippetIds.dumpPaintBlendList()} " +
                "blend=${blendMode ?: NONE_PAINT_BLEND_VALUE} plan=${blendPlanKind ?: NONE_PAINT_BLEND_VALUE} " +
                "target=${targetFormatClass ?: NONE_PAINT_BLEND_VALUE} " +
                "blendState=${blendStateHash ?: NONE_PAINT_BLEND_VALUE} " +
                "renderKey=${renderPipelineKey ?: NONE_PAINT_BLEND_VALUE} " +
                "pipelineCache=${pipelineCacheKey ?: NONE_PAINT_BLEND_VALUE} " +
                "payload=${payloadFingerprint ?: NONE_PAINT_BLEND_VALUE} " +
                "destinationRead=${destinationReadLabel ?: NONE_PAINT_BLEND_VALUE} " +
                "blendConstants=none uniformValuesInKey=$uniformValuesInKey " +
                "destinationResourcesInKey=$destinationResourcesInKey " +
                "adapterBacked=$adapterBacked productActivation=$productActivation"
        } else {
            "paint-blend:execution.refused row=$evidenceRow material=$materialKey " +
                "reason=${resourceDecision.paintBlendReasonCode()} " +
                "blend=${blendMode ?: NONE_PAINT_BLEND_VALUE} plan=${blendPlanKind ?: NONE_PAINT_BLEND_VALUE} " +
                "destinationRead=${destinationReadLabel ?: NONE_PAINT_BLEND_VALUE} " +
                "adapterBacked=$adapterBacked productActivation=$productActivation"
        }

        val payloadLine =
            "paint-blend:payload materialKey=$materialKey " +
                "payloadPlan=${payloadPlanHashOrNone()} " +
                "payloadFingerprint=${payloadFingerprint ?: NONE_PAINT_BLEND_VALUE} " +
                "materialKeyIncludesPayload=$uniformValuesInKey " +
                "pipelineKeyIncludesPayload=false concreteResourcesInKey=$destinationResourcesInKey"

        return listOf(head, payloadLine, destinationReadEvidenceLine(), PAINT_BLEND_EXECUTION_NONCLAIM_LINE) +
            resourceDecision.dumpLines() +
            commandStream?.dumpLines().orEmpty()
    }

    private fun payloadPlanHashOrNone(): String =
        if (resourceDecision is GPUResourceMaterializationDecision.Materialized) {
            PAINT_BLEND_SOLID_PAYLOAD_PLAN_HASH
        } else {
            NONE_PAINT_BLEND_VALUE
        }
}

/** Deterministic pipeline key derivation for accepted paint/blend execution. */
object GPUPaintBlendExecutionKeys {
    /** Builds the render pipeline preimage from material dictionary and blend gate evidence. */
    fun renderPipelinePreimage(
        paintPlan: GPUPaintPipelinePlan,
        materialAssembly: GPUMaterialAssemblyPlan,
        blendGate: GPUBlendAllowlistGatePlan,
        bindingLayoutHash: String,
    ): GPUPipelineKeyPreimage.Render =
        GPUPipelineKeyPreimage.Render(
            renderStepIdentity = blendGate.renderStepIdentity,
            renderStepVersion = "1",
            primitiveTopology = "triangle-list",
            materialKeyHash = paintPlan.materialKey.value,
            materialProgramId = materialAssembly.programId.value,
            materialDictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
            materialLayoutHash = materialAssembly.rootSet.payloadShapeHash,
            snippetIdentityHash = materialAssembly.snippetGraph
                .joinToString(",") { node -> node.snippetId.value },
            moduleHash = "material-module:${materialAssembly.moduleSalt}:${materialAssembly.programId.value}",
            vertexLayoutHash = "${blendGate.renderStepIdentity}:vertex-layout:v1",
            targetFormatClass = blendGate.targetFormatClass,
            blendStateHash = blendGate.blendStateHash,
            sampleStateHash = "sample-count-1",
            bindGroupLayoutHash = bindingLayoutHash,
            capabilityClass = "paint-blend-fixed-function",
            capabilityFacts = listOf(
                "blend=${blendGate.plan.mode}",
                "dictionary=${GPUSolidMaterialDictionary.DictionaryVersion}",
                "destinationRead=${blendGate.destinationReadRequirement}",
                "root=${materialAssembly.rootSet.rootSetId}",
                "snippets=${materialAssembly.snippetGraph.joinToString(",") { node -> node.snippetId.value }}",
                "uniformValuesInKey=false",
            ),
            rendererSalt = "kanvas-paint-blend-execution-v1",
        )

    /** Derives the render key used by `setRenderPipeline`. */
    fun renderPipelineKey(
        paintPlan: GPUPaintPipelinePlan,
        materialAssembly: GPUMaterialAssemblyPlan,
        blendGate: GPUBlendAllowlistGatePlan,
        bindingLayoutHash: String,
    ) = GPUPipelineKeys.renderPipelineKey(
        renderPipelinePreimage(paintPlan, materialAssembly, blendGate, bindingLayoutHash),
    )

    /** Derives the cache key used for render pipeline cache evidence. */
    fun pipelineCacheKey(
        paintPlan: GPUPaintPipelinePlan,
        materialAssembly: GPUMaterialAssemblyPlan,
        blendGate: GPUBlendAllowlistGatePlan,
        bindingLayoutHash: String,
    ) = GPUPipelineKeys.pipelineCacheKey(
        renderPipelinePreimage(paintPlan, materialAssembly, blendGate, bindingLayoutHash),
    )

    /** Derives pass target-state identity from fixed-function blend target facts. */
    fun targetStateHash(blendGate: GPUBlendAllowlistGatePlan): String =
        "target-state:${blendGate.targetFormatClass}:${blendGate.blendStateHash}:sample-count-1"
}

/** Materializes an accepted fixed-function paint/blend boundary or refuses with stable diagnostics. */
class ValidatingPaintBlendExecutionBoundary(
    private val payloadProvider: GPUResourceProvider = ValidatingPayloadResourceProvider(),
) {
    /** Validates material dictionary, blend, payload, and pipeline facts before command encoding. */
    fun materialize(
        request: GPUPaintBlendExecutionRequest,
        context: GPUTargetPreparationContext,
    ): GPUPaintBlendExecutionResult {
        val diagnostics = request.executionDiagnostics(context)
        if (diagnostics.isNotEmpty()) {
            return request.refused(diagnostics.first(), diagnostics, context)
        }

        val payloadDecision = payloadProvider.materializePayloadBindings(request.payloadRequest, context)
        val payloadMaterialized = payloadDecision as? GPUResourceMaterializationDecision.Materialized
            ?: return request.resultFromDecision(payloadDecision, commandStream = null)

        val pipelineBinding = request.pipelineOperandBinding(context)
        val materializedDecision = payloadMaterialized.copy(
            resourcePlanLabels = (payloadMaterialized.dumpResourcePlanLabelsSnapshot + pipelineBinding.operand.label)
                .distinct(),
            operandBridge = payloadMaterialized.dumpOperandBridgeSnapshot + pipelineBinding,
        )
        val commandStream = request.commandStream(materializedDecision)

        return request.resultFromDecision(materializedDecision, commandStream)
    }
}

private fun GPUPaintBlendExecutionRequest.resultFromDecision(
    decision: GPUResourceMaterializationDecision,
    commandStream: GPUPassCommandStream?,
): GPUPaintBlendExecutionResult =
    GPUPaintBlendExecutionResult(
        evidenceRow = PAINT_BLEND_EXECUTION_ROW,
        materialKey = paintPlan.materialKey.value,
        materialProgramId = materialAssembly.programId.value,
        dictionaryVersion = expectedDictionaryVersion,
        rootSetId = materialAssembly.rootSet.rootSetId,
        snippetIds = materialAssembly.snippetGraph.map { node -> node.snippetId.value },
        blendMode = blendGate.plan.mode.name,
        blendPlanKind = blendGate.planKind,
        targetFormatClass = blendGate.targetFormatClass,
        blendStateHash = blendGate.blendStateHash,
        renderPipelineKey = GPUPaintBlendExecutionKeys.renderPipelineKey(
            paintPlan,
            materialAssembly,
            blendGate,
            payloadRequest.reflectedBindingLayoutHash,
        ).value,
        pipelineCacheKey = pipelineCacheKey,
        payloadFingerprint = payloadRequest.uniformBlock.fingerprint.value,
        destinationReadLabel = blendGate.destinationReadLinkLabel(),
        resourceDecision = decision,
        commandStream = commandStream,
    )

private fun GPUPaintBlendExecutionRequest.refused(
    diagnostic: GPUResourceDiagnostic,
    diagnostics: List<GPUResourceDiagnostic>,
    context: GPUTargetPreparationContext,
): GPUPaintBlendExecutionResult =
    GPUPaintBlendExecutionResult(
        evidenceRow = PAINT_BLEND_EXECUTION_ROW,
        materialKey = paintPlan.materialKey.value,
        materialProgramId = null,
        dictionaryVersion = null,
        rootSetId = null,
        snippetIds = emptyList(),
        blendMode = blendGate.plan.mode.name,
        blendPlanKind = blendGate.planKind,
        targetFormatClass = blendGate.targetFormatClass,
        blendStateHash = null,
        renderPipelineKey = null,
        pipelineCacheKey = pipelineCacheKey,
        payloadFingerprint = null,
        destinationReadLabel = blendGate.destinationReadLinkLabel(),
        resourceDecision = GPUResourceMaterializationDecision.Refused(
            diagnostic = diagnostic,
            targetId = context.targetId,
            taskIds = payloadRequest.dumpTaskIdsSnapshot,
            resourcePlanLabels = listOf(resourcePlanLabel()),
            diagnostics = diagnostics,
        ),
        commandStream = null,
    )

private fun GPUPaintBlendExecutionRequest.executionDiagnostics(
    context: GPUTargetPreparationContext,
): List<GPUResourceDiagnostic> {
    val materialStage = paintPlan.materialStageAccepted()
    return buildList {
        blendGate.diagnostics.firstOrNull { diagnostic -> diagnostic.terminal }?.let { diagnostic ->
            add(resourceDiagnostic(diagnostic.code))
            return@buildList
        }
        if (payloadRequest.targetId != context.targetId) {
            add(
                GPUResourceDiagnostic.commandOperandTargetMismatch(
                    resourceLabel = resourcePlanLabel(),
                    requestTargetId = payloadRequest.targetId,
                    contextTargetId = context.targetId,
                ),
            )
        }
        if (paintPlan.materialKey.value != expectedMaterialKey ||
            blendGate.materialKeyHash != paintPlan.materialKey.value
        ) {
            add(resourceDiagnostic("unsupported.paint_blend.material_key_mismatch"))
        }
        if (materialStage == null) {
            add(resourceDiagnostic("unsupported.paint_blend.material_source_refused"))
        } else if (materialStage.payloadPlanHash != expectedPayloadPlanHash) {
            add(resourceDiagnostic("unsupported.paint_blend.payload_plan_mismatch"))
        }
        if (materialAssembly.programId.value != "program:${paintPlan.materialKey.value}") {
            add(resourceDiagnostic("unsupported.paint_blend.material_program_mismatch"))
        }
        if (expectedDictionaryVersion != GPUSolidMaterialDictionary.DictionaryVersion) {
            add(resourceDiagnostic("unsupported.paint_blend.dictionary_version_mismatch"))
        }
        if (materialAssembly.rootSet.rootSetId != expectedRootSetId) {
            add(resourceDiagnostic("unsupported.paint_blend.root_set_mismatch"))
        }
        if (materialAssembly.snippetGraph.map { node -> node.snippetId.value } != expectedSnippetIds) {
            add(resourceDiagnostic("unsupported.paint_blend.snippet_mismatch"))
        }
        if (blendGate.planKind != GPUBlendPlanKind.FixedFunctionBlend) {
            add(resourceDiagnostic("unsupported.paint_blend.shader_blend_unvalidated"))
        }
        if (blendGate.destinationReadRequirement != GPUBlendDestinationReadRequirement.None) {
            add(resourceDiagnostic("unsupported.paint_blend.destination_read_required"))
        }
        if (targetStateHash != GPUPaintBlendExecutionKeys.targetStateHash(blendGate)) {
            add(resourceDiagnostic("unsupported.paint_blend.target_state_mismatch"))
        }
        if (blendGate.materialized) {
            add(resourceDiagnostic("unsupported.paint_blend.gate_already_materialized"))
        }
        if (payloadRequest.reflectedBindingLayoutHash != GPUSolidMaterialDictionary.SolidMaterialLayoutHash ||
            payloadRequest.resourceBlock.bindingPlanHash != GPUSolidMaterialDictionary.SolidMaterialLayoutHash
        ) {
            add(resourceDiagnostic("unsupported.paint_blend.payload_binding_mismatch"))
        }
        val payloadFields = payloadRequest.uniformBlock.fields.map { field ->
            "${field.fieldPath}:${field.valueClass}@${field.byteOffset}:${field.byteSize}"
        }
        if (payloadRequest.uniformBlock.byteSize != 16L ||
            payloadFields != listOf("color:vec4<f32>@0:16")
        ) {
            add(resourceDiagnostic("unsupported.paint_blend.uniform_payload_schema_mismatch"))
        }
        if (pipelineCacheKey != GPUPaintBlendExecutionKeys.pipelineCacheKey(
                paintPlan,
                materialAssembly,
                blendGate,
                payloadRequest.reflectedBindingLayoutHash,
            ).value
        ) {
            add(resourceDiagnostic("unsupported.paint_blend.pipeline_cache_key_mismatch"))
        }
    }
}

private fun GPUPaintBlendExecutionRequest.pipelineOperandBinding(
    context: GPUTargetPreparationContext,
): GPUMaterializedCommandOperandBinding =
    GPUMaterializedCommandOperandBinding(
        packetId = payloadRequest.packetId,
        commandLabel = "setRenderPipeline",
        operand = GPUMaterializedCommandOperandReference(
            label = pipelineOperandLabel(),
            kind = GPUMaterializedCommandOperandKind.RenderPipeline,
            descriptorHash = pipelineCacheKey,
            deviceGeneration = context.deviceGeneration,
            ownerScope = "paint-blend-pipeline-cache",
            usageLabels = listOf("render"),
            invalidationPolicy = "material-dictionary",
            evidenceFacts = mapOf(
                "blend" to blendGate.plan.mode.name,
                "blendState" to blendGate.blendStateHash,
                "dictionary" to expectedDictionaryVersion,
                "material" to paintPlan.materialKey.value,
                "program" to materialAssembly.programId.value,
                "root" to materialAssembly.rootSet.rootSetId,
                "snippets" to materialAssembly.snippetGraph.joinToString(",") { node -> node.snippetId.value },
                "uniformValuesInKey" to "false",
            ),
        ),
    )

private fun GPUPaintBlendExecutionRequest.commandStream(
    materializedDecision: GPUResourceMaterializationDecision.Materialized,
): GPUPassCommandStream {
    val packetId = GPUDrawPacketID(payloadRequest.packetId)
    return GPUPassCommandStream(
        streamId = streamId,
        packetStreamId = packetStreamId,
        passId = passId,
        commands = listOf(
            GPUPassCommand.BeginRenderPass(targetStateHash = targetStateHash, loadStoreLabel = loadStoreLabel),
            GPUPassCommand.SetRenderPipeline(
                pipelineKey = GPUPaintBlendExecutionKeys.renderPipelineKey(
                    paintPlan,
                    materialAssembly,
                    blendGate,
                    payloadRequest.reflectedBindingLayoutHash,
                ),
                packetId = packetId,
            ),
            GPUPassCommand.SetBindGroup(
                bindingLayoutHash = payloadRequest.reflectedBindingLayoutHash,
                uniformSlot = payloadRequest.uniformSlot,
                resourceSlot = payloadRequest.resourceSlot,
                packetId = packetId,
            ),
            GPUPassCommand.Draw(vertexSourceLabel = vertexSourceLabel, packetId = packetId),
            GPUPassCommand.EndRenderPass(passId = passId),
        ),
        operandBridge = materializedDecision.dumpOperandBridgeSnapshot.map(GPUPassCommandOperandBridge::fromMaterializedBinding),
    )
}

private fun GPUPaintPipelinePlan.materialStageAccepted(): GPUMaterialSourcePlan.Accepted? =
    stages.filterIsInstance<GPUPaintStagePlan.Material>()
        .singleOrNull()
        ?.sourcePlan as? GPUMaterialSourcePlan.Accepted

private fun GPUPaintBlendExecutionRequest.resourceDiagnostic(code: String): GPUResourceDiagnostic =
    GPUResourceDiagnostic(
        code = code,
        resourceLabel = resourcePlanLabel(),
        message = "paint/blend execution boundary refused $label: $code",
        terminal = true,
        facts = mapOf(
            "blend" to blendGate.plan.mode.name,
            "material" to paintPlan.materialKey.value,
        ),
    )

private fun GPUPaintBlendExecutionRequest.resourcePlanLabel(): String =
    "paint-blend:${label}"

private fun GPUPaintBlendExecutionRequest.pipelineOperandLabel(): String =
    "paint-blend-pipeline:${paintPlan.materialKey.value}"

private fun GPUResourceMaterializationDecision.paintBlendReasonCode(): String =
    when (this) {
        is GPUResourceMaterializationDecision.Refused -> diagnostic.code
        is GPUResourceMaterializationDecision.Deferred -> reasonCode
        is GPUResourceMaterializationDecision.Materialized -> "accepted.paint_blend.execution"
    }

private fun List<String>.dumpPaintBlendList(): String =
    if (isEmpty()) NONE_PAINT_BLEND_VALUE else joinToString(",")

private fun GPUPaintBlendExecutionResult.destinationReadEvidenceLine(): String =
    "paint-blend:destination-read strategy=${destinationReadLabel ?: NONE_PAINT_BLEND_VALUE}"

private fun GPUBlendAllowlistGatePlan.destinationReadLinkLabel(): String =
    "${destinationReadRequirement};" +
        "plan=semantic;" +
        "planStrategy=${destinationReadRequirement};" +
        "activeAttachmentSampled=$activeAttachmentSampled"

private const val PAINT_BLEND_EXECUTION_ROW = "gpu-renderer.paint-blend.execution-boundary"
private const val PAINT_BLEND_SOLID_PAYLOAD_PLAN_HASH =
    "payload:SolidMaterialBlock.color.vec4f32@group1.binding0"
private const val NONE_PAINT_BLEND_VALUE = "none"
private const val PAINT_BLEND_EXECUTION_NONCLAIM_LINE =
    "paint-blend:nonclaim shaderBlend=false framebufferFetch=false inputAttachment=false " +
        "destinationReadTexture=false cpuRenderedFallback=false productActivation=true"
