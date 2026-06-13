package org.graphiks.kanvas.gpu.renderer.passes

/** Stable render-step identifier. */
@JvmInline
value class GPURenderStepID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURenderStepID.value must not be blank" }
    }
}

/** Draw invocation expanded from analysis and layer planning. */
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
