package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.recording.*

/** Verify native W4 stencil operands against the same W6 physical IDs before encoding. */
internal fun GPUW6aLayerFramePlan.validatesNativePathPayload(
    prepared: PreparedGPUFrame, payload: GPUPreparedNativeFramePayload?,
): Boolean {
    if (!validatesW4eFragments(prepared.semanticPlan) || payload == null) return false
    val byStep = payload.scopeOperands.associateBy { it.sourceStepIndex }
    val depthViews = mutableMapOf<PlanResourceId, Any>()
    val expectedPathSteps = mutableSetOf<Int>()
    graph.passes().forEachIndexed { ordinalI32, pass ->
        val w4e = physical.w4eGeometryBinding(pass.id)?.nativePass(pass.id)
        if (w4e != null) {
            val stepI32 = ordinalI32 + 1
            val native = byStep[stepI32] as? GPUPreparedNativeScopeOperand.Render ?: return false
            if (native.w6aPassV1 !== pass) return false
            val depthId = when (w4e) {
                is PlanPass.PathRenderPass -> w4e.depthStencil
                is PlanPass.ClipMaskProducer -> w4e.depthStencil
                else -> null
            }
            if (depthId == null) {
                if (native.pass.depthStencilTarget != null || stepI32 in payload.pathDepthStencilViewAuthority) return false
                return@forEachIndexed
            }
            expectedPathSteps += stepI32
            val view = native.pass.depthStencilTarget ?: return false
            if (payload.pathDepthStencilViewAuthority[stepI32] !== view.view ||
                view.deviceGeneration != prepared.generationSeal.deviceGeneration ||
                view.ownership != GPUPreparedNativeOperandOwnership.Borrowed) return false
            val prior = depthViews[depthId]
            if (prior != null && prior !== view.view || depthViews.any { (id, other) -> id != depthId && other === view.view }) return false
            val producer = w4e is PlanPass.ClipMaskProducer || w4e is PlanPass.PathRenderPass &&
                w4e.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore
            if (producer) {
                if (native.pass.stencilReadOnly || native.pass.stencilLoadOperation != GPUPreparedNativeLoadOperation.Clear ||
                    native.pass.stencilClearValue != 0u || native.pass.stencilStoreOperation != GPUPreparedNativeStoreOperation.Store) return false
            } else {
                // W4e retains its read-only cover, or its explicitly writable inverse-interior prefix.
                if (prior == null || !native.pass.depthReadOnly || native.pass.stencilClearValue != null) return false
                if (native.pass.stencilReadOnly) {
                    if (native.pass.stencilLoadOperation != null || native.pass.stencilStoreOperation != null) return false
                } else if (native.pass.stencilLoadOperation != GPUPreparedNativeLoadOperation.Load ||
                    native.pass.stencilStoreOperation != GPUPreparedNativeStoreOperation.Store) return false
            }
            depthViews[depthId] = view.view
            return@forEachIndexed
        }
        val depthId = when (pass) {
            is PlanPass.StencilGeometryProducerV3 -> pass.depthStencil
            is PlanPass.StencilCover -> pass.depthStencil
            else -> return@forEachIndexed
        }
        val stepI32 = ordinalI32 + 1
        expectedPathSteps += stepI32
        val native = byStep[stepI32] as? GPUPreparedNativeScopeOperand.Render ?: return false
        val view = native.pass.depthStencilTarget ?: return false
        val producer = pass is PlanPass.StencilGeometryProducerV3
        if (native.w6aPassV1 !== pass || payload.pathDepthStencilViewAuthority[stepI32] !== view.view ||
            view.deviceGeneration != prepared.generationSeal.deviceGeneration ||
            view.ownership != GPUPreparedNativeOperandOwnership.Borrowed ||
            !native.pass.depthReadOnly || native.pass.stencilReadOnly ||
            native.pass.stencilLoadOperation != (if (producer) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load) ||
            native.pass.stencilStoreOperation != GPUPreparedNativeStoreOperation.Store ||
            native.pass.stencilClearValue != (if (producer) 0u else null)) return false
        val priorView = depthViews[depthId]
        if (priorView != null && priorView !== view.view || priorView == null && !producer) return false
        if (depthViews.any { (id, other) -> id != depthId && other === view.view }) return false
        depthViews[depthId] = view.view
    }
    return payload.pathDepthStencilViewAuthority.keys == expectedPathSteps && payload.clipDepthStencilViewAuthority.isEmpty()
}
