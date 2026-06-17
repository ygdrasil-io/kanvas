package org.graphiks.kanvas.gpu.renderer.layers

import java.security.MessageDigest

/** Layer scope identity. */
@JvmInline
value class GPULayerScopeID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPULayerScopeID.value must not be blank" }
    }
}

/** Layer ordering token. */
@JvmInline
value class GPULayerOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPULayerOrderingToken.value must not be blank" }
    }
}

/** Save-layer record captured from input state. */
data class GPULayerSaveRecord(
    val scopeId: GPULayerScopeID,
    val boundsLabel: String,
    val paintLabel: String? = null,
    val backdropRequired: Boolean,
    val parentScopeId: GPULayerScopeID? = null,
    val childCommandIds: List<String> = emptyList(),
    val initWithPrevious: Boolean = false,
    val sourceFilterCount: Int = 0,
    val restoreBlendMode: String = "srcOver",
    val cpuFallbackRequested: Boolean = false,
    val preserveLCDText: Boolean = false,
    val f16Requested: Boolean = false,
)

/** Restore plan for a layer scope. */
data class GPULayerRestorePlan(
    val scopeId: GPULayerScopeID,
    val compositePlanHash: String,
    val orderingToken: GPULayerOrderingToken,
)

/** Layer bounds plan. */
data class GPULayerBoundsPlan(
    val requestedBoundsLabel: String,
    val deviceBoundsLabel: String,
    val conservative: Boolean,
    val finite: Boolean = true,
    val originX: Int = 0,
    val originY: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

/** Layer target plan. */
data class GPULayerTargetPlan(
    val targetLabel: String,
    val formatClass: String,
    val sampleCount: Int,
    val lifetimeClass: String,
    val targetDescriptorHash: String = "",
    val usageLabels: List<String> = emptyList(),
    val loadOp: String = "load",
    val storeOp: String = "store",
    val originLabel: String = "device:0,0",
    val byteEstimate: Long = 0L,
    val ownerLabel: String = "GPURecorderScope",
    val generationLabel: String = "target-generation:0",
) {
    val usageLabel: String
        get() = usageLabels.joinToString(",")
}

/** Layer initialization plan. */
data class GPULayerInitializationPlan(
    val clearPolicy: String,
    val loadPolicy: String,
    val requiresBackdropCopy: Boolean,
)

/** Layer backdrop plan. */
data class GPULayerBackdropPlan(
    val sourceLabel: String,
    val readBoundsLabel: String,
    val copyPolicy: String,
)

/** Layer source plan. */
data class GPULayerSourcePlan(
    val sourceLabel: String,
    val colorTreatment: String,
    val samplingPolicy: String,
)

/** Layer filter chain plan. */
data class GPULayerFilterChainPlan(
    val filterGraphHash: String,
    val intermediateCount: Int,
    val cropPolicy: String,
)

/** Layer composite plan. */
data class GPULayerCompositePlan(
    val sourcePlan: GPULayerSourcePlan,
    val blendModeLabel: String,
    val destinationReadLabel: String? = null,
    val compositeRoute: String = "fixed-function-srcOver",
    val destinationReadStrategy: String = destinationReadLabel ?: "none",
    val parentTargetLabel: String = "",
    val orderingToken: GPULayerOrderingToken = GPULayerOrderingToken("layer-order:default:restore"),
)

/** Layer elision proof. */
data class GPULayerElisionPlan(
    val canElide: Boolean,
    val proofFacts: List<String>,
    val reasonCode: String,
)

/** Layer task plan. */
data class GPULayerTaskPlan(
    val taskLabels: List<String>,
    val dependencies: List<String>,
)

/** Layer resource plan. */
data class GPULayerResourcePlan(
    val targetPlan: GPULayerTargetPlan,
    val scratchLabels: List<String>,
    val budgetPolicy: GPULayerBudgetPolicy,
    val allocationPlanLabel: String = "create-texture",
    val ownerLabel: String = "GPURecorderScope",
    val releasePolicy: String = "layer-scope-end",
    val requiredUsageLabels: List<String> = emptyList(),
    val availableUsageLabels: List<String> = emptyList(),
)

/** Layer cache plan. */
data class GPULayerCachePlan(
    val cacheKey: String,
    val invalidationFacts: List<String>,
    val reusable: Boolean,
)

/** Layer budget policy. */
data class GPULayerBudgetPolicy(
    val maxBytes: Long,
    val priorityClass: String,
    val refusalCode: String? = null,
)

/** Executable layer plan. */
sealed interface GPULayerExecutionPlan {
    /** Layer can be elided. */
    data class Elided(val elision: GPULayerElisionPlan) : GPULayerExecutionPlan

    /** Layer uses isolated target work. */
    data class IsolatedTarget(
        val target: GPULayerTargetPlan,
        val tasks: GPULayerTaskPlan,
        val initialization: GPULayerInitializationPlan = GPULayerInitializationPlan(
            clearPolicy = "clear(transparent-black)",
            loadPolicy = "clear",
            requiresBackdropCopy = false,
        ),
        val composite: GPULayerCompositePlan = GPULayerCompositePlan(
            sourcePlan = GPULayerSourcePlan(
                sourceLabel = target.targetLabel,
                colorTreatment = "premul",
                samplingPolicy = "texture-binding",
            ),
            blendModeLabel = "srcOver",
        ),
    ) : GPULayerExecutionPlan

    /** Layer initializes from a backdrop. */
    data class Backdrop(val backdrop: GPULayerBackdropPlan, val initialization: GPULayerInitializationPlan) : GPULayerExecutionPlan

    /** Layer composites back into its parent. */
    data class Composite(val composite: GPULayerCompositePlan) : GPULayerExecutionPlan

    /** Layer planning was refused. */
    data class Refused(val diagnostic: GPULayerDiagnostic) : GPULayerExecutionPlan
}

/** Semantic layer plan. */
data class GPULayerPlan(
    val saveRecord: GPULayerSaveRecord,
    val bounds: GPULayerBoundsPlan,
    val execution: GPULayerExecutionPlan,
    val resources: GPULayerResourcePlan? = null,
    val cache: GPULayerCachePlan? = null,
    val diagnostics: List<GPULayerDiagnostic> = emptyList(),
)

/** Request for the saveLayer isolated target evidence gate. */
data class GPUSaveLayerIsolatedTargetRequest(
    val label: String = "accepted",
    val saveRecord: GPULayerSaveRecord,
    val bounds: GPULayerBoundsPlan,
    val parentTargetLabel: String,
    val targetFormatClass: String = "rgba8unorm",
    val sampleCount: Int = 1,
    val requiredUsageLabels: Set<String> = setOf("render_attachment", "texture_binding"),
    val availableUsageLabels: Set<String> = requiredUsageLabels,
    val deviceGeneration: Long = 0L,
    val activeAttachmentSampled: Boolean = false,
    val maxTargetBytes: Long = DEFAULT_SAVE_LAYER_TARGET_MAX_BYTES,
)

/** Evidence result for the saveLayer isolated target gate. */
data class GPUSaveLayerIsolatedTargetGatePlan(
    val label: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val targetDescriptorHash: String,
    val layerPlan: GPULayerPlan,
    val diagnostics: List<GPULayerDiagnostic>,
) {
    /** Returns the canonical dump lines required before native promotion. */
    fun dumpLines(): List<String> {
        val diagnostic = diagnostics.singleOrNull()
        if (diagnostic != null) {
            return listOf(
                "savelayer:isolated-target.refused row=$evidenceRow routeKind=$routeKind " +
                    "classification=$classification promoted=$promoted productActivation=$productActivation " +
                    "materialized=$materialized scope=${layerPlan.saveRecord.scopeId.value} " +
                    "reason=${diagnostic.code} label=$label",
                SAVE_LAYER_NONCLAIM_LINE,
            )
        }

        val saveRecord = layerPlan.saveRecord
        val bounds = layerPlan.bounds
        val execution = layerPlan.execution as GPULayerExecutionPlan.IsolatedTarget
        val target = execution.target
        val resource = requireNotNull(layerPlan.resources) {
            "accepted saveLayer isolated target plans must expose a resource plan"
        }
        val parentScopeLabel = saveRecord.parentScopeId?.value ?: "none"
        val childLabel = saveRecord.childCommandIds.joinToString(",").ifEmpty { "none" }

        return listOf(
            "savelayer:isolated-target row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "scope=${saveRecord.scopeId.value} parent=${execution.composite.parentTargetLabel}",
            "savelayer:save scope=${saveRecord.scopeId.value} parentScope=$parentScopeLabel " +
                "bounds=requested=${bounds.requestedBoundsLabel} device=${bounds.deviceBoundsLabel} " +
                "finite=${bounds.finite} conservative=${bounds.conservative} children=$childLabel",
            "savelayer:target label=${target.targetLabel} owner=${target.ownerLabel} " +
                "generation=${target.generationLabel.removePrefix(TARGET_GENERATION_PREFIX)} " +
                "descriptor=${target.targetDescriptorHash} size=${bounds.width}x${bounds.height} " +
                "format=${target.formatClass} sampleCount=${target.sampleCount} usage=${target.usageLabel} " +
                "load=${target.loadOp} store=${target.storeOp} lifetime=${target.lifetimeClass} " +
                "origin=${target.originLabel} bytes=${target.byteEstimate}",
            "savelayer:init clear=${execution.initialization.clearPolicy} load=${execution.initialization.loadPolicy} " +
                "backdropCopy=${execution.initialization.requiresBackdropCopy} " +
                "previousContent=${saveRecord.initWithPrevious}",
            "savelayer:tasks order=${execution.tasks.taskLabels.joinToString(",")}",
            "savelayer:pass offscreen=${target.targetLabel} parent=${execution.composite.parentTargetLabel} " +
                "load=${target.loadOp} store=${target.storeOp} separateAttachments=true " +
                "activeAttachmentSampled=false",
            "savelayer:composite source=${execution.composite.sourcePlan.sourceLabel} " +
                "parent=${execution.composite.parentTargetLabel} blend=${execution.composite.blendModeLabel} " +
                "route=${execution.composite.compositeRoute} " +
                "destinationRead=${execution.composite.destinationReadStrategy} " +
                "ordering=${execution.composite.orderingToken.value}",
            "savelayer:resource plan=${resource.allocationPlanLabel} owner=${resource.ownerLabel} " +
                "release=${resource.releasePolicy} budget=${resource.budgetPolicy.priorityClass} " +
                "requiredUsage=${resource.requiredUsageLabels.joinToString(",")} " +
                "availableUsage=${resource.availableUsageLabels.joinToString(",")}",
            SAVE_LAYER_NONCLAIM_LINE,
        )
    }
}

/** Planner for the contract-only saveLayer isolated target route gate. */
class GPUSaveLayerIsolatedTargetPlanner {
    /** Plans a contract-only saveLayer isolated target route or a stable refusal. */
    fun plan(request: GPUSaveLayerIsolatedTargetRequest): GPUSaveLayerIsolatedTargetGatePlan {
        val targetBytes = request.bounds.targetByteEstimate()
        val refusalCode = request.refusalCode(targetBytes)
        if (refusalCode != null) {
            return refusedPlan(request, refusalCode)
        }

        val usageLabels = request.requiredSaveLayerUsageLabels().canonicalUsageLabels()
        val descriptorHash = targetDescriptorHash(request, usageLabels)
        val targetLabel = "layer-target:${request.saveRecord.scopeId.value.removePrefix("layer:")}"
        val targetPlan = GPULayerTargetPlan(
            targetLabel = targetLabel,
            formatClass = request.targetFormatClass,
            sampleCount = request.sampleCount,
            lifetimeClass = "layer-local",
            targetDescriptorHash = descriptorHash,
            usageLabels = usageLabels,
            loadOp = "clear",
            storeOp = "store",
            originLabel = "device:${request.bounds.originX},${request.bounds.originY}",
            byteEstimate = targetBytes,
            ownerLabel = "GPURecorderScope",
            generationLabel = "$TARGET_GENERATION_PREFIX${request.deviceGeneration}",
        )
        val initialization = GPULayerInitializationPlan(
            clearPolicy = "clear(transparent-black)",
            loadPolicy = "clear",
            requiresBackdropCopy = false,
        )
        val composite = GPULayerCompositePlan(
            sourcePlan = GPULayerSourcePlan(
                sourceLabel = targetLabel,
                colorTreatment = "premul",
                samplingPolicy = "texture-binding",
            ),
            blendModeLabel = request.saveRecord.restoreBlendMode,
            destinationReadLabel = null,
            compositeRoute = "fixed-function-srcOver",
            destinationReadStrategy = "none",
            parentTargetLabel = request.parentTargetLabel,
            orderingToken = GPULayerOrderingToken("layer-order:${request.saveRecord.scopeId.value}:restore"),
        )
        val tasks = GPULayerTaskPlan(
            taskLabels = listOf(
                "layer.task.allocate_target",
                "layer.task.clear_transparent",
                "layer.task.render_children",
                "layer.task.composite_parent",
                "layer.task.release_target",
            ),
            dependencies = emptyList(),
        )
        val resourcePlan = GPULayerResourcePlan(
            targetPlan = targetPlan,
            scratchLabels = emptyList(),
            budgetPolicy = GPULayerBudgetPolicy(
                maxBytes = request.maxTargetBytes,
                priorityClass = "layer-small",
                refusalCode = null,
            ),
            allocationPlanLabel = "create-texture",
            ownerLabel = "GPURecorderScope",
            releasePolicy = "layer-scope-end",
            requiredUsageLabels = usageLabels,
            availableUsageLabels = request.availableUsageLabels.canonicalUsageLabels(),
        )
        val layerPlan = GPULayerPlan(
            saveRecord = request.saveRecord,
            bounds = request.bounds,
            execution = GPULayerExecutionPlan.IsolatedTarget(
                target = targetPlan,
                tasks = tasks,
                initialization = initialization,
                composite = composite,
            ),
            resources = resourcePlan,
        )

        return GPUSaveLayerIsolatedTargetGatePlan(
            label = request.label,
            evidenceRow = SAVE_LAYER_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = false,
            materialized = false,
            targetDescriptorHash = descriptorHash,
            layerPlan = layerPlan,
            diagnostics = emptyList(),
        )
    }

    private fun refusedPlan(
        request: GPUSaveLayerIsolatedTargetRequest,
        refusalCode: String,
    ): GPUSaveLayerIsolatedTargetGatePlan {
        val diagnostic = GPULayerDiagnostic(
            code = refusalCode,
            scopeId = request.saveRecord.scopeId,
            message = "saveLayer isolated target gate refused: $refusalCode",
            terminal = true,
        )
        val layerPlan = GPULayerPlan(
            saveRecord = request.saveRecord,
            bounds = request.bounds,
            execution = GPULayerExecutionPlan.Refused(diagnostic),
            diagnostics = listOf(diagnostic),
        )

        return GPUSaveLayerIsolatedTargetGatePlan(
            label = request.label,
            evidenceRow = SAVE_LAYER_EVIDENCE_ROW,
            routeKind = "RefuseDiagnostic",
            classification = "TargetNative",
            promoted = false,
            productActivation = false,
            materialized = false,
            targetDescriptorHash = "",
            layerPlan = layerPlan,
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Low-level draw-layer plan. */
data class GPUDrawLayer(
    val layerId: String,
    val scopeId: GPULayerScopeID,
    val orderBand: String,
    val insertionLabels: List<String>,
)

/** Draw-layer planner contract. */
interface GPUDrawLayerPlanner {
    /** Plans low-level draw layers from semantic layer labels. */
    fun plan(layerLabels: List<String>): List<GPUDrawLayer> = TODO("Wire GPUDrawLayerPlanner to layer ordering and pass construction")
}

/** Layer diagnostic. */
data class GPULayerDiagnostic(
    val code: String,
    val scopeId: GPULayerScopeID? = null,
    val message: String,
    val terminal: Boolean,
)

private const val DEFAULT_SAVE_LAYER_TARGET_MAX_BYTES = 16L * 1024L * 1024L
private const val SAVE_LAYER_EVIDENCE_ROW = "gpu-renderer.savelayer.isolated-target"
private const val TARGET_GENERATION_PREFIX = "target-generation:"
private const val SAVE_LAYER_NONCLAIM_LINE =
    "savelayer:nonclaim nativeSaveLayer=false adapterBacked=false cpuLayerTextureFallback=false " +
        "arbitraryLayerStacks=false filters=false destinationRead=false"

private fun GPUSaveLayerIsolatedTargetRequest.refusalCode(targetBytes: Long): String? =
    when {
        !bounds.finite -> "unsupported.layer.bounds_unbounded"
        bounds.width <= 0 || bounds.height <= 0 -> "unsupported.layer.bounds_invalid"
        (requiredSaveLayerUsageLabels() - availableUsageLabels).isNotEmpty() -> "unsupported.layer.target_usage_missing"
        activeAttachmentSampled -> "unsupported.layer.active_attachment_sampled"
        saveRecord.backdropRequired -> "unsupported.layer.backdrop_filter"
        saveRecord.initWithPrevious -> "unsupported.layer.init_previous_unaccepted"
        saveRecord.sourceFilterCount > 0 -> "unsupported.layer.filter_chain"
        !saveRecord.restoreBlendMode.equals("srcOver", ignoreCase = true) -> "unsupported.layer.restore_blend"
        saveRecord.cpuFallbackRequested -> "unsupported.layer.cpu_fallback_forbidden"
        saveRecord.preserveLCDText -> "unsupported.layer.preserve_lcd_text"
        saveRecord.f16Requested -> "unsupported.layer.f16_unavailable"
        targetBytes > maxTargetBytes -> "unsupported.layer.target_too_large"
        else -> null
    }

private fun GPULayerBoundsPlan.targetByteEstimate(): Long =
    if (width <= 0 || height <= 0) {
        0L
    } else {
        val pixelCount = runCatching { Math.multiplyExact(width.toLong(), height.toLong()) }
            .getOrDefault(Long.MAX_VALUE / SAVE_LAYER_BYTES_PER_PIXEL + 1L)
        runCatching { Math.multiplyExact(pixelCount, SAVE_LAYER_BYTES_PER_PIXEL) }
            .getOrDefault(Long.MAX_VALUE)
    }

private fun Set<String>.canonicalUsageLabels(): List<String> =
    sortedWith(
        compareBy(
            { SAVE_LAYER_USAGE_ORDER.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } },
            { it },
        ),
    )

private fun targetDescriptorHash(
    request: GPUSaveLayerIsolatedTargetRequest,
    usageLabels: List<String>,
): String = "sha256:" + stableHash(
    listOf(
        "savelayer-target-v1",
        request.bounds.width.toString(),
        request.bounds.height.toString(),
        request.targetFormatClass,
        request.sampleCount.toString(),
        usageLabels.joinToString(","),
        "load=clear",
        "store=store",
        "lifetime=layer-local",
        "origin=${request.bounds.originX},${request.bounds.originY}",
    ),
)

private fun stableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray()
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}

private val SAVE_LAYER_USAGE_ORDER = listOf(
    "render_attachment",
    "texture_binding",
    "copy_src",
    "copy_dst",
    "storage_binding",
)
private val SAVE_LAYER_REQUIRED_USAGE_LABELS = setOf("render_attachment", "texture_binding")
private const val SAVE_LAYER_BYTES_PER_PIXEL = 4L

private fun GPUSaveLayerIsolatedTargetRequest.requiredSaveLayerUsageLabels(): Set<String> =
    requiredUsageLabels + SAVE_LAYER_REQUIRED_USAGE_LABELS
