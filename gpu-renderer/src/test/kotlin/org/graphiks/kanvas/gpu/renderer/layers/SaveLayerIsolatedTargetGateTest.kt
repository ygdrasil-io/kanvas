package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SaveLayerIsolatedTargetGateTest {
    @Test
    fun boundedTransparentLayerProducesIsolatedTargetEvidenceDump() {
        val plan = GPUSaveLayerIsolatedTargetPlanner().plan(saveLayerRequest())

        assertEquals("gpu-renderer.savelayer.isolated-target", plan.evidenceRow)
        assertEquals("GPUNative", plan.routeKind)
        assertEquals("TargetNative", plan.classification)
        assertFalse(plan.promoted)
        assertFalse(plan.productActivation)
        assertFalse(plan.materialized)
        assertEquals(emptyList(), plan.diagnostics)

        val execution = assertIs<GPULayerExecutionPlan.IsolatedTarget>(plan.layerPlan.execution)
        assertEquals("render_attachment,texture_binding", execution.target.usageLabel)
        assertEquals("clear(transparent-black)", execution.initialization.clearPolicy)
        assertEquals("clear", execution.target.loadOp)
        assertEquals("store", execution.target.storeOp)
        assertEquals("layer-local", execution.target.lifetimeClass)
        assertEquals("target-generation:17", execution.target.generationLabel)
        assertEquals("fixed-function-srcOver", execution.composite.compositeRoute)
        assertEquals("none", execution.composite.destinationReadStrategy)

        val dump = plan.dumpLines()
        assertEquals(
            listOf(
                "savelayer:isolated-target row=gpu-renderer.savelayer.isolated-target routeKind=GPUNative classification=TargetNative promoted=false productActivation=false materialized=false scope=layer:card parent=root-target",
                "savelayer:save scope=layer:card parentScope=root bounds=requested=card-local device=0,0,64,48 finite=true conservative=true children=draw-rect,draw-image",
                "savelayer:target label=layer-target:card owner=GPURecorderScope generation=17 descriptor=${plan.targetDescriptorHash} size=64x48 format=rgba8unorm sampleCount=1 usage=render_attachment,texture_binding load=clear store=store lifetime=layer-local origin=device:0,0 bytes=12288",
                "savelayer:init clear=clear(transparent-black) load=clear backdropCopy=false previousContent=false",
                "savelayer:tasks order=layer.task.allocate_target,layer.task.clear_transparent,layer.task.render_children,layer.task.composite_parent,layer.task.release_target",
                "savelayer:pass offscreen=layer-target:card parent=root-target load=clear store=store separateAttachments=true activeAttachmentSampled=false",
                "savelayer:composite source=layer-target:card parent=root-target blend=srcOver route=fixed-function-srcOver destinationRead=none ordering=layer-order:layer:card:restore",
                "savelayer:resource plan=create-texture owner=GPURecorderScope release=layer-scope-end budget=layer-small requiredUsage=render_attachment,texture_binding availableUsage=render_attachment,texture_binding",
                "savelayer:nonclaim nativeSaveLayer=false adapterBacked=false cpuLayerTextureFallback=false arbitraryLayerStacks=false filters=false destinationRead=false",
            ),
            dump,
        )
    }

    @Test
    fun targetDescriptorHashIsStableAcrossChildAndGenerationChanges() {
        val base = GPUSaveLayerIsolatedTargetPlanner().plan(saveLayerRequest())
        val changed = GPUSaveLayerIsolatedTargetPlanner().plan(
            saveLayerRequest(
                deviceGeneration = 99,
                saveRecord = saveRecord(
                    scopeId = GPULayerScopeID("layer:dialog"),
                    childCommandIds = listOf("draw-different"),
                ),
            ),
        )

        assertEquals(base.targetDescriptorHash, changed.targetDescriptorHash)
    }

    @Test
    fun unsupportedLayerVariantsRefuseWithStableDiagnostics() {
        val cases = listOf(
            refusalCase(
                "unbounded",
                bounds = bounds(finite = false),
                reason = "unsupported.layer.bounds_unbounded",
            ),
            refusalCase(
                "missing-usage",
                availableUsageLabels = setOf("render_attachment"),
                reason = "unsupported.layer.target_usage_missing",
            ),
            refusalCase(
                "active-attachment",
                activeAttachmentSampled = true,
                reason = "unsupported.layer.active_attachment_sampled",
            ),
            refusalCase(
                "init-previous",
                saveRecord = saveRecord(initWithPrevious = true),
                reason = "unsupported.layer.init_previous_unaccepted",
            ),
            refusalCase(
                "backdrop",
                saveRecord = saveRecord(backdropRequired = true),
                reason = "unsupported.layer.backdrop_filter",
            ),
            refusalCase(
                "filter-chain",
                saveRecord = saveRecord(sourceFilterCount = 1),
                reason = "unsupported.layer.filter_chain",
            ),
            refusalCase(
                "unsupported-blend",
                saveRecord = saveRecord(restoreBlendMode = "multiply"),
                reason = "unsupported.layer.restore_blend",
            ),
            refusalCase(
                "cpu-fallback",
                saveRecord = saveRecord(cpuFallbackRequested = true),
                reason = "unsupported.layer.cpu_fallback_forbidden",
            ),
            refusalCase(
                "target-too-large",
                bounds = bounds(width = Int.MAX_VALUE, height = Int.MAX_VALUE),
                reason = "unsupported.layer.target_too_large",
            ),
        )

        cases.forEach { case ->
            val plan = GPUSaveLayerIsolatedTargetPlanner().plan(case.request)

            assertIs<GPULayerExecutionPlan.Refused>(plan.layerPlan.execution)
            assertEquals(case.reason, plan.diagnostics.single().code)
            assertEquals(
                listOf(
                    "savelayer:isolated-target.refused row=gpu-renderer.savelayer.isolated-target routeKind=RefuseDiagnostic classification=TargetNative promoted=false productActivation=false materialized=false scope=layer:card reason=${case.reason} label=${case.label}",
                    "savelayer:nonclaim nativeSaveLayer=false adapterBacked=false cpuLayerTextureFallback=false arbitraryLayerStacks=false filters=false destinationRead=false",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class RefusalCase(
    val label: String,
    val request: GPUSaveLayerIsolatedTargetRequest,
    val reason: String,
)

private fun refusalCase(
    label: String,
    reason: String,
    saveRecord: GPULayerSaveRecord = saveRecord(),
    bounds: GPULayerBoundsPlan = bounds(),
    availableUsageLabels: Set<String> = setOf("render_attachment", "texture_binding"),
    activeAttachmentSampled: Boolean = false,
): RefusalCase = RefusalCase(
    label = label,
    reason = reason,
    request = saveLayerRequest(
        label = label,
        saveRecord = saveRecord,
        bounds = bounds,
        availableUsageLabels = availableUsageLabels,
        activeAttachmentSampled = activeAttachmentSampled,
    ),
)

private fun saveLayerRequest(
    label: String = "accepted",
    saveRecord: GPULayerSaveRecord = saveRecord(),
    bounds: GPULayerBoundsPlan = bounds(),
    deviceGeneration: Long = 17,
    availableUsageLabels: Set<String> = setOf("render_attachment", "texture_binding"),
    activeAttachmentSampled: Boolean = false,
): GPUSaveLayerIsolatedTargetRequest = GPUSaveLayerIsolatedTargetRequest(
    label = label,
    saveRecord = saveRecord,
    bounds = bounds,
    parentTargetLabel = "root-target",
    deviceGeneration = deviceGeneration,
    availableUsageLabels = availableUsageLabels,
    activeAttachmentSampled = activeAttachmentSampled,
)

private fun saveRecord(
    scopeId: GPULayerScopeID = GPULayerScopeID("layer:card"),
    initWithPrevious: Boolean = false,
    backdropRequired: Boolean = false,
    sourceFilterCount: Int = 0,
    restoreBlendMode: String = "srcOver",
    cpuFallbackRequested: Boolean = false,
    childCommandIds: List<String> = listOf("draw-rect", "draw-image"),
): GPULayerSaveRecord = GPULayerSaveRecord(
    scopeId = scopeId,
    parentScopeId = GPULayerScopeID("root"),
    boundsLabel = "card-local",
    childCommandIds = childCommandIds,
    initWithPrevious = initWithPrevious,
    backdropRequired = backdropRequired,
    sourceFilterCount = sourceFilterCount,
    restoreBlendMode = restoreBlendMode,
    cpuFallbackRequested = cpuFallbackRequested,
)

private fun bounds(
    finite: Boolean = true,
    width: Int = 64,
    height: Int = 48,
): GPULayerBoundsPlan = GPULayerBoundsPlan(
    requestedBoundsLabel = "card-local",
    deviceBoundsLabel = "0,0,64,48",
    conservative = true,
    finite = finite,
    originX = 0,
    originY = 0,
    width = width,
    height = height,
)
