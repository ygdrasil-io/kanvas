package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Select the sealed W4d contract, including malformed partial frames which must fail closed. */
internal fun PreparedGPUFrame.hasW4dGeneralMsaaPackets(): Boolean =
    semanticPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().any { render ->
        render.drawPackets.any { packet -> packet.corePrimitivePreparedAuthority?.let {
            it.w4dGeneralPreparedAuthority != null || it.w4dGeneralFrameMaterializationAuthority != null
        } == true }
    }

/**
 * W4d retains a physical MSAA target and resolves only its final color pass. Recheck the
 * existing opaque frame facts both before payload consumption and against native views.
 * D24S8 phase/pair validation remains in the executor's planned-path payload validation.
 */
internal fun PreparedGPUFrame.validatesW4dGeneralMsaa(
    sceneTarget: GPUSceneTarget,
    payload: GPUPreparedNativeFramePayload? = null,
    canonicalResolve: (GPUPreparedNativeTextureViewOperand) -> Boolean = { false },
): Boolean {
    val renders = semanticPlan.steps.mapIndexedNotNull { index, step ->
        (step as? GPUFrameStep.RenderPassStep)?.let { index to it }
    }
    val first = renders.firstOrNull()?.second?.drawPackets?.singleOrNull()?.corePrimitivePreparedAuthority
        ?: return false
    val prepared = first.w4dGeneralPreparedAuthority ?: return false
    val authority = first.w4dGeneralFrameMaterializationAuthority ?: return false
    if (semanticPlan.w6aLayerFrameV1 != null || authority.pathPassFacts.size != renders.size ||
        authority.deviceGeneration != generationSeal.deviceGeneration ||
        authority.capabilitySealHash != semanticPlan.capabilitySeal.sealHash ||
        authority.targetBounds.width != sceneTarget.width || authority.targetBounds.height != sceneTarget.height ||
        semanticPlan.recordingSeals.singleOrNull()?.let {
            it.compatibilityKeyHash == "w4d-general:${authority.planId}" &&
                it.replayKeyHash == "w4d-general:${authority.planId}"
        } != true
    ) return false
    renders.zip(authority.pathPassFacts).forEach { (row, fact) ->
        val render = row.second
        val packet = render.drawPackets.singleOrNull() ?: return false
        val packetAuthority = packet.corePrimitivePreparedAuthority ?: return false
        if (packetAuthority.w4dGeneralPreparedAuthority !== prepared ||
            packetAuthority.w4dGeneralFrameMaterializationAuthority !== authority ||
            packet.passId != fact.pathPassId || packet.commandIdValue != fact.commandIdValue ||
            packetAuthority.structuralPipelineKey != authority.structuralPipelineKey(fact.pathPassId) ||
            render.target != authority.resource(fact.targetResourceId) ||
            render.samplePlan.sampleCount != fact.sampleCountI32 ||
            (fact.sampleCountI32 != 4 && render.sampleContinuation != null)
        ) return false
    }
    val msaa = renders.zip(authority.pathPassFacts).filter { it.second.sampleCountI32 == 4 }
    val transitions = prepared.sampleContinuation?.transitions ?: return false
    if (msaa.isEmpty() || transitions.size != msaa.size) return false
    val targetId = msaa.first().second.targetResourceId
    val target = authority.resource(targetId) ?: return false
    val targetFact = authority.resourceFact(targetId) ?: return false
    val resolveId = authority.readbackSourceResourceId
    val resolve = authority.resource(resolveId) ?: return false
    val resolveFact = authority.resourceFact(resolveId) ?: return false
    val (expectedColorFormat, expectedColorInterpretation) = when (
        val format = (targetFact.format as? org.graphiks.kanvas.gpu.plan.PlanTextureFormat.Color)?.value
    ) {
        org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL ->
            org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat.RGBA8UnormSrgb to GPUColorInterpretation.LinearPremul
        org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL ->
            org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat.RGBA8Unorm to GPUColorInterpretation.EncodedPremulSrgb
        null -> return false
    }
    if (target == resolve || targetFact.role != PlanResourceRole.MultisampleColorTarget ||
        resolveFact.role != PlanResourceRole.LogicalTarget || targetFact.sampleCountI32 != 4 ||
        resolveFact.sampleCountI32 != 1 || targetFact.format != resolveFact.format ||
        listOf(targetFact, resolveFact).any {
            it.kind != PlanResourceKind.Texture2D || it.width != sceneTarget.width || it.height != sceneTarget.height ||
                PlanResourceUsage.RenderAttachment !in it.usages
        } || semanticPlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull()?.source != resolve
    ) return false
    val commonKey = msaa.first().first.second.sampleContinuation?.key ?: return false
    if (commonKey.target.value != target.value || commonKey.deviceGeneration != generationSeal.deviceGeneration ||
        commonKey.targetGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
        commonKey.colorFormat != expectedColorFormat || sceneTarget.format != expectedColorFormat ||
        commonKey.colorInterpretation != expectedColorInterpretation ||
        commonKey.samplePlan != GPUSamplePlan.MultisampleFrame(4) ||
        commonKey.attachmentAuthority != GPUSampleAttachmentAuthority.PreparedFramePayload ||
        commonKey.colorAttachment.value != "msaa-color:${target.value}:$PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION" ||
        commonKey.depthStencilAttachment != null
    ) return false
    var retainedColorView: Any? = null
    val retainedDepthViews = mutableMapOf<String, Any>()
    msaa.zip(transitions).forEachIndexed { ordinal, (entry, transition) ->
        val (row, fact) = entry
        val (stepIndex, render) = row
        val request = render.sampleContinuation ?: return false
        val final = ordinal == msaa.lastIndex
        val clear = ordinal == 0
        if (fact.targetResourceId != targetId || request.key != commonKey ||
            transition.pathPassId != fact.pathPassId || transition.commandIdValue != fact.commandIdValue ||
            request.loadTransition != transition.loadTransition || request.storeAction != transition.storeAction ||
            request.resolveAction != transition.resolveAction ||
            request.loadTransition != (if (clear) GPUSampleLoadTransition.FreshClear else GPUSampleLoadTransition.RetainedLoad) ||
            request.storeAction != GPUSampleStoreAction.Store ||
            request.resolveAction != (if (final) GPUSampleResolveAction.ResolveCanonical else GPUSampleResolveAction.Skip) ||
            fact.resolveTargetResourceId != (if (final) resolveId else null) ||
            fact.load != (if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load) ||
            fact.store != AttachmentStorePlan.Store || render.loadStore.loadOp != (if (clear) "clear" else "load") ||
            render.loadStore.storePlan != GPUStorePlan.Store
        ) return false
        val scope = encoderPlan.scopes.singleOrNull { it.sourceStepIndex == stepIndex } ?: return false
        fun key(role: GPUPreparedNativeOperandRole, label: String) = GPUPreparedNativeOperandKey(
            role, GPUPreparedNativeOperandKind.TextureView, gpuPreparedNativeBindingKey(label),
        )
        val colorKey = key(GPUPreparedNativeOperandRole.RenderMsaaColorTarget,
            "w4d-general:${authority.planId}:msaa:${target.value}")
        val resolveKey = key(GPUPreparedNativeOperandRole.RenderResolveTarget,
            "w4d-general:${authority.planId}:resolve:${resolve.value}")
        val expectedAttachments = buildList {
            add(colorKey)
            if (final) add(resolveKey)
            fact.depthStencilResourceId?.let { id ->
                val depth = authority.resource(id) ?: return false
                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    "w4d-general:${authority.planId}:depth:${depth.value}"))
            }
        }
        val attachmentRoles = setOf(GPUPreparedNativeOperandRole.RenderColorTarget,
            GPUPreparedNativeOperandRole.RenderMsaaColorTarget, GPUPreparedNativeOperandRole.RenderResolveTarget,
            GPUPreparedNativeOperandRole.RenderDepthStencilTarget)
        if (scope.nativeOperandKeys.filter { it.role in attachmentRoles } != expectedAttachments) return false
        if (payload != null) {
            val scopeIndex = encoderPlan.scopes.indexOf(scope)
            val native = payload.scopeOperands.getOrNull(scopeIndex) as? GPUPreparedNativeScopeOperand.Render ?: return false
            val keys = payload.scopeOperandKeys.getOrNull(scopeIndex) ?: return false
            if (native.sourceStepIndex != stepIndex || keys != scope.nativeOperandKeys) return false
            val pass = native.pass
            val color = pass.colorTarget
            val resolved = pass.resolveTarget
            val depth = pass.depthStencilTarget
            if ((resolved != null) != final || (depth != null) != (fact.depthStencilResourceId != null) ||
                listOfNotNull(color, resolved, depth).any {
                    it.deviceGeneration != generationSeal.deviceGeneration || it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
                } || retainedColorView != null && retainedColorView !== color.view ||
                resolved != null && (resolved.view === color.view || !canonicalResolve(resolved)) ||
                depth != null && (depth.view === color.view || depth.view === resolved?.view) ||
                pass.loadOperation != (if (clear) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load) ||
                pass.storeOperation != GPUPreparedNativeStoreOperation.Store ||
                pass.clearColor != (if (clear) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null)
            ) return false
            val keyed = native.operands.zip(keys)
            if (keyed.singleOrNull { it.second == colorKey }?.first !== color ||
                final && keyed.singleOrNull { it.second == resolveKey }?.first !== resolved ||
                depth != null && keyed.singleOrNull { it.second.role == GPUPreparedNativeOperandRole.RenderDepthStencilTarget }?.first !== depth
            ) return false
            if (depth != null) {
                val depthId = fact.depthStencilResourceId ?: return false
                val previous = retainedDepthViews[depthId]
                if (previous != null && previous !== depth.view ||
                    retainedDepthViews.any { (id, view) -> id != depthId && view === depth.view }
                ) return false
                retainedDepthViews[depthId] = depth.view
            }
            retainedColorView = color.view
        }
    }
    return true
}
