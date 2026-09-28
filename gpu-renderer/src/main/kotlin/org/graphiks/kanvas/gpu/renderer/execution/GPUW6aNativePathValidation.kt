package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
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
            if (w4e is PlanPass.PathRenderPass && w4e.scanSpansDeviceI32 != null) {
                val spans = requireNotNull(w4e.scanSpansDeviceI32)
                val proxy = pass as? PlanPass.StencilGeometryProducerV3 ?: return false
                val expected = proxy.scanScissorsLocalI32?.copyScissorsI32() ?: return false
                val recipe = runCatching { physical.w6InverseMaskPathRecipe(proxy.id) }.getOrNull() ?: return false
                if (proxy.scanSpansDeviceI32 !== spans ||
                    expected.size != spans.spanCountI32 ||
                    recipe.producer.ownerPassId != proxy.id || recipe.producer.target.id != proxy.target ||
                    recipe.producer.depthStencil.id != proxy.depthStencil ||
                    recipe.producer.copyDomainDeviceI32() != spans.copyDomainI32() ||
                    recipe.producer.copyScissorsLocalI32() != expected ||
                    recipe.producer.drawCountI32 != spans.spanCountI32 || recipe.producer.hasVertexIndexSlices ||
                    (recipe.producer is W6InverseMaskPathRecipeV1.GeometryProducer.NonEmpty) != (spans.spanCountI32 > 0) ||
                    native.semanticPayloads.singleOrNull() !is GPUDrawSemanticPayload.PathStencilProducer
                ) return false
                val evidence = runCatching { preparedNativeScanSpanEvidence(native) }.getOrNull() ?: return false
                if (!validatesW6InverseMaskScanSpanCommandStream(native, recipe, expected) ||
                    evidence.size != recipe.producer.drawCountI32
                ) return false
            }
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

/**
 * The one semantic scan-span packet expands only into this canonical native stream:
 *
 *   NonEmpty: SetStencilReference(1), SetPipeline(NoBindings), (SetScissor(row), Draw(3))*
 *   Empty:    no commands
 *
 * Comparing separately filtered scissor and draw lists loses their pairing, so consume the
 * ordered stream instead.  Exhausting it also excludes bind groups and vertex/index commands.
 */
private fun validatesW6InverseMaskScanSpanCommandStream(
    native: GPUPreparedNativeScopeOperand.Render,
    recipe: W6InverseMaskPathRecipeV1,
    expectedScissors: List<org.graphiks.math.geometry.RectI32>,
): Boolean {
    val producer = recipe.producer
    if (producer.pipeline != W6InverseMaskPathProducerPipelineV1.FullscreenNoBindings ||
        producer.stencil != W6InverseMaskPathProducerStencilV1.ClearZeroReplaceOne ||
        producer.fullscreenVertexCountI32 != 3 || producer.hasVertexIndexSlices
    ) return false
    if (producer is W6InverseMaskPathRecipeV1.GeometryProducer.Empty) {
        return expectedScissors.isEmpty() && native.commands.isEmpty() &&
            native.w6InverseMaskScanSpanPipelineWitnessV1 === GPUW6InverseMaskScanSpanPipelineWitnessV1.Empty
    }
    if (producer !is W6InverseMaskPathRecipeV1.GeometryProducer.NonEmpty || expectedScissors.isEmpty()) return false

    val expectedPipelineWitness = native.w6InverseMaskScanSpanPipelineWitnessV1
        as? GPUW6InverseMaskScanSpanPipelineWitnessV1.NonEmpty ?: return false
    if (expectedPipelineWitness.vertexProgram != GPUW6InverseMaskScanSpanPipelineWitnessV1.VertexProgram.FullscreenTriangle ||
        expectedPipelineWitness.stencil != GPUW6InverseMaskScanSpanPipelineWitnessV1.Stencil.ReplaceOne ||
        expectedPipelineWitness.colorWrite != GPUW6InverseMaskScanSpanPipelineWitnessV1.ColorWrite.None ||
        expectedPipelineWitness.bindingPolicy != GPUPreparedNativeRenderPipelineBindingPolicy.NoBindings
    ) return false

    val commands = native.commands
    var commandIndexI32 = 0
    val stencilReference = commands.getOrNull(commandIndexI32++) as? GPUPreparedNativeRenderCommand.SetStencilReference
        ?: return false
    if (stencilReference.reference != 1u) return false
    val pipeline = (commands.getOrNull(commandIndexI32++) as? GPUPreparedNativeRenderCommand.SetPipeline)
        ?.pipeline ?: return false
    if (pipeline.bindingPolicy != GPUPreparedNativeRenderPipelineBindingPolicy.NoBindings ||
        pipeline.w6InverseMaskScanSpanPipelineWitnessV1 !== expectedPipelineWitness ||
        pipeline.pipeline !== expectedPipelineWitness.pipeline ||
        pipeline.deviceGeneration != expectedPipelineWitness.deviceGeneration
    ) return false

    expectedScissors.forEach { expected ->
        val scissor = commands.getOrNull(commandIndexI32++) as? GPUPreparedNativeRenderCommand.SetScissor
            ?: return false
        if (scissor.x != expected.left || scissor.y != expected.top ||
            scissor.width != expected.width() || scissor.height != expected.height()
        ) return false
        val draw = commands.getOrNull(commandIndexI32++) as? GPUPreparedNativeRenderCommand.Draw
            ?: return false
        if (draw.drawCall != GPUPreparedNativeDrawCall.Draw(producer.fullscreenVertexCountI32)) return false
    }
    return commandIndexI32 == commands.size
}
