package org.graphiks.kanvas.gpu.renderer.passes

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
