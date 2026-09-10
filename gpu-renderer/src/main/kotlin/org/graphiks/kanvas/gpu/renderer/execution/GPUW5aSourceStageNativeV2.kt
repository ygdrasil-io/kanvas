package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.materials.w5aCombinedMemoryBudgetV2
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits

/** Original, authenticated geometry descriptor. W5a changes no geometry or attachment state. */
internal data class GPUW5aGeometryPipelineTemplate(
    val source: String,
    val descriptor: RenderPipelineDescriptor,
    val groupZero: GPUBindGroupLayout,
)

internal interface GPUW5aGeometryPipelineTemplateProvider {
    fun sourceTemplate(pipeline: GPURenderPipeline): GPUW5aGeometryPipelineTemplate?
}

/** Exact V2 source partition; historical geometry operands/commands keep their original ABI. */
internal class GPUW5aNativeSourceBindingV2(
    val drawOrdinalI32: Int,
    val source: W5aPacketMaterialSourceV2,
    val pipeline: GPUPreparedNativeRenderPipelineOperand,
    val bindGroup: GPUPreparedNativeBindGroupOperand,
    val buffer: GPUBuffer,
    val byteCapacityI64: Long,
) {
    init {
        require(drawOrdinalI32 >= 0 && byteCapacityI64 == source.stage.uniformByteCountI64)
        require(pipeline.deviceGeneration == bindGroup.deviceGeneration)
    }
}

/** Dependencies are closed in reverse acquisition order, retaining failed closes for retry. */
internal class GPUW5aSourceOwnedHandlesV2 : AutoCloseable {
    private val handles = mutableListOf<AutoCloseable>()
    fun <T : AutoCloseable> own(handle: T): T = handle.also { handles += it }
    fun owns(handle: AutoCloseable): Boolean = handles.any { it === handle }
    override fun close() {
        var first: Throwable? = null
        val iterator = handles.listIterator(handles.size)
        while (iterator.hasPrevious()) {
            val handle = iterator.previous()
            try { handle.close(); iterator.remove() } catch (failure: Throwable) {
                if (first == null) first = failure else first.addSuppressed(failure)
            }
        }
        first?.let { throw it }
    }
}

internal fun validatesW5aSourcePartitionV2(framePlan: GPUFramePlan, payload: GPUPreparedNativeFramePayload): Boolean {
    val owners = payload.auxiliaryOwnedHandles.mapNotNull { it.handle as? GPUW5aSourceOwnedHandlesV2 }
    return payload.scopeOperands.all { operand ->
        if (operand !is GPUPreparedNativeScopeOperand.Render) return@all true
        val render = framePlan.steps.getOrNull(operand.sourceStepIndex) as? GPUFrameStep.RenderPassStep
            ?: return@all operand.w5aSourceBindingsV2.isEmpty()
        val expected = runCatching { sourceDrawsV2(render, operand).withIndex().mapNotNull { (ordinalI32, source) ->
            source?.let { ordinalI32 to it }
        } }.getOrNull() ?: return@all false
        operand.w5aSourceBindingsV2.size == expected.size &&
            operand.w5aSourceBindingsV2.zip(expected).all { (binding, source) ->
                binding.drawOrdinalI32 == source.first && binding.source === source.second &&
                    binding.byteCapacityI64 == source.second.stage.uniformByteCountI64 &&
                    binding.pipeline.deviceGeneration == payload.identity.deviceGeneration &&
                    binding.bindGroup.deviceGeneration == payload.identity.deviceGeneration &&
                    owners.any { it.owns(binding.buffer) && it.owns(binding.pipeline.pipeline) && it.owns(binding.bindGroup.bindGroup) }
            }
    }
}

/** W4e inverse-domain packets seal an atomic stencil prefix plus one final color draw. */
private fun sourceDrawsV2(
    render: GPUFrameStep.RenderPassStep,
    operand: GPUPreparedNativeScopeOperand.Render,
): List<W5aPacketMaterialSourceV2?> {
    if (render.drawPackets.none { it.w5aSourceStageV2 != null }) return emptyList()
    val pipelines = buildList {
        var current: GPUPreparedNativeRenderPipelineOperand? = null
        operand.commands.forEach { command ->
            if (command is GPUPreparedNativeRenderCommand.SetPipeline) current = command.pipeline
            if (command is GPUPreparedNativeRenderCommand.Draw || command is GPUPreparedNativeRenderCommand.DrawIndexed) {
                add(requireNotNull(current))
            }
        }
    }
    if (pipelines.size == render.drawPackets.size) return render.drawPackets.map { it.w5aSourceStageV2 }
    val packet = render.drawPackets.single()
    require(packet.w4ePreparedFrameAuthority != null && packet.w4ePreparedPath != null &&
        packet.w4ePreparedClipConsumer is org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority.InverseDomain &&
        pipelines.size in 2..3 && pipelines.dropLast(1).all {
            it.bindingPolicy == GPUPreparedNativeRenderPipelineBindingPolicy.NoBindings
        } && pipelines.last().bindingPolicy == GPUPreparedNativeRenderPipelineBindingPolicy.BindGroupRequired) {
        "W5a source requires exact packet-to-native color draw order"
    }
    return List(pipelines.size - 1) { null } + packet.w5aSourceStageV2
}

/** Compose only the source expression. Existing coverage and fixed-function tail stay exact. */
private fun composeSource(template: GPUW5aGeometryPipelineTemplate, source: W5aPacketMaterialSourceV2): String {
    val target = requireNotNull(template.descriptor.fragment).targets.single()
    val blend = requireNotNull(target.blend)
    require(target.format == GPUTextureFormat.RGBA8UnormSrgb &&
        listOf(blend.color, blend.alpha).all {
            it.operation == GPUBlendOperation.Add && it.srcFactor == GPUBlendFactor.One &&
                it.dstFactor == GPUBlendFactor.OneMinusSrcAlpha
        }) { "W5a source DAG requires the authenticated premultiplied SRC_OVER sRGB attachment tail" }
    // The renderer reflection parser uses explicit scalar type parameters; W4e's native
    // spelling uses WGSL's equivalent vector aliases. Normalize those before composition.
    var geometry = template.source.replace(Regex("\\bvec([234])([fiu])\\b")) {
        "vec${it.groupValues[1]}<${it.groupValues[2]}32>"
    }
    val original = (validateColorWgsl("w5a-geometry-v2", geometry) as? GPUColorWgslValidation.Validated)
        ?.reflection?.report ?: error("W5a geometry requires parser-backed reflection")
    val slots = listOf("core.premul_rgba", "analytic.premul_rgba", "drrect.premul_rgba",
        "consumer.premul_rgba", "consumer.color")
    require(slots.any(geometry::contains)) { "W5a source requires an authenticated color-writing geometry shader" }
    require(!geometry.contains("@group(1)")) { "W5a source group is already occupied" }
    slots.forEach { slot -> geometry = geometry.replace(slot, "kanvas_material_source(vec2<f32>(0.0))") }
    val result = geometry + "\n" + source.stage.declarationsWgsl
    val composed = (validateColorWgsl("w5a-source-v2:${source.stage.structuralId}", result) as? GPUColorWgslValidation.Validated)
        ?.reflection?.report ?: error("Composed W5a fragment module failed parser validation")
    val material = composed.bindings.singleOrNull { it.group == 1 }
    require(original.validation.success && composed.validation.success &&
        original.bindings.all { it.group == 0 } &&
        composed.bindings.filter { it.group == 0 } == original.bindings &&
        composed.bindings.size == original.bindings.size + 1 && material != null &&
        material.binding == 0 && material.resourceKind == "uniformBuffer" &&
        material.minBindingSize?.toLong() == source.stage.uniformByteCountI64) {
        "W5a composed ABI must preserve geometry bindings and add exactly its raw group-1 block"
    }
    return result
}

/** Called once by the native dispatcher, after authentic geometry materialization, before Ready. */
internal fun materializeW5aSourcePartitionV2(
    device: GPUDevice,
    queue: GPUQueue,
    limits: GPULimits,
    framePlan: GPUFramePlan,
    geometry: GPUPreparedNativeFramePayloadMaterialization,
    templates: GPUW5aGeometryPipelineTemplateProvider,
): GPUPreparedNativeFramePayloadMaterialization {
    if (geometry !is GPUPreparedNativeFramePayloadMaterialization.Materialized) return geometry
    val renders = framePlan.steps.withIndex().filter { it.value is GPUFrameStep.RenderPassStep }
        .associate { it.index to (it.value as GPUFrameStep.RenderPassStep) }
    if (renders.values.none { render -> render.drawPackets.any { it.w5aSourceStageV2 != null } }) return geometry
    val old = geometry.draft.payload
    val owned = GPUW5aSourceOwnedHandlesV2()
    val generation = old.identity.deviceGeneration
    var replacement: GPUPreparedNativeFrameDraft? = null
    try {
        require(framePlan.w5aCombinedMemoryBudgetV2(limits).diagnostic == null)
        val pipelines = mutableMapOf<Pair<GPURenderPipeline, String>, Pair<GPUPreparedNativeRenderPipelineOperand, GPUBindGroupLayout>>()
        val buffers = mutableMapOf<String, GPUBuffer>()
        val groups = mutableMapOf<Pair<String, GPUBindGroupLayout>, GPUPreparedNativeBindGroupOperand>()
        val operands = old.scopeOperands.map { operand ->
            if (operand !is GPUPreparedNativeScopeOperand.Render) return@map operand
            val packets = renders.getValue(operand.sourceStepIndex).drawPackets
            if (packets.none { it.w5aSourceStageV2 != null }) return@map operand
            val sources = sourceDrawsV2(renders.getValue(operand.sourceStepIndex), operand)
            var currentPipeline: GPUPreparedNativeRenderPipelineOperand? = null
            var drawOrdinalI32 = 0
            val bindings = mutableListOf<GPUW5aNativeSourceBindingV2>()
            operand.commands.forEach { command ->
                if (command is GPUPreparedNativeRenderCommand.SetPipeline) currentPipeline = command.pipeline
                if (command !is GPUPreparedNativeRenderCommand.Draw && command !is GPUPreparedNativeRenderCommand.DrawIndexed) return@forEach
                val ordinalI32 = drawOrdinalI32++
                val source = sources[ordinalI32] ?: return@forEach
                val base = requireNotNull(currentPipeline)
                val key = base.pipeline to source.stage.structuralId
                val (pipeline, materialLayout) = pipelines.getOrPut(key) {
                    val template = templates.sourceTemplate(base.pipeline)
                        ?: old.auxiliaryOwnedHandles.asSequence().mapNotNull { it.handle as? GPUW5aGeometryPipelineTemplateProvider }
                            .mapNotNull { it.sourceTemplate(base.pipeline) }.firstOrNull()
                        ?: error("W5a source lost its authentic geometry pipeline template")
                    val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(
                        label = "Kanvas.w5a.source-v2.${source.stage.structuralId}",
                        entries = listOf(BindGroupLayoutEntry(binding = 0u, visibility = GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform, hasDynamicOffset = false,
                                minBindingSize = source.stage.uniformByteCountI64.toULong()))),
                    )))
                    val shader = owned.own(device.createShaderModule(ShaderModuleDescriptor(
                        label = "Kanvas.w5a.source-v2.${source.stage.structuralId}", code = composeSource(template, source))))
                    val pipelineLayout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(
                        label = "Kanvas.w5a.composed-abi-v2", bindGroupLayouts = listOf(template.groupZero, layout))))
                    val descriptor = template.descriptor
                    val native = owned.own(device.createRenderPipeline(descriptor.copy(
                        label = "Kanvas.w5a.fragment-source-v2.${source.stage.structuralId}",
                        layout = pipelineLayout,
                        vertex = VertexState(module = shader, buffers = descriptor.vertex.buffers,
                            entryPoint = descriptor.vertex.entryPoint, constants = descriptor.vertex.constants),
                        fragment = requireNotNull(descriptor.fragment).let { fragment -> FragmentState(
                            module = shader, targets = fragment.targets, entryPoint = fragment.entryPoint,
                            constants = fragment.constants) },
                    )))
                    GPUPreparedNativeRenderPipelineOperand(native, generation) to layout
                }
                val bytes = source.stage.uniformBytes
                val buffer = buffers.getOrPut(source.stage.canonicalIdentity) {
                    owned.own(device.createBuffer(BufferDescriptor(size = bytes.size.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst, label = "Kanvas.w5a.raw-source-v2"))).also {
                        queue.writeBuffer(it, 0uL, ArrayBuffer.of(bytes), 0uL, bytes.size.toULong())
                    }
                }
                val group = groups.getOrPut(source.stage.canonicalIdentity to materialLayout) {
                    GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(BindGroupDescriptor(label = "Kanvas.w5a.source-group1-v2",
                        layout = materialLayout, entries = listOf(BindGroupEntry(binding = 0u,
                            resource = BufferBinding(buffer = buffer, offset = 0uL, size = bytes.size.toULong())))))), generation)
                }
                bindings += GPUW5aNativeSourceBindingV2(ordinalI32, source, pipeline, group, buffer, bytes.size.toLong())
            }
            GPUPreparedNativeScopeOperand.Render(operand.sourceStepIndex, operand.pass, operand.commands,
                operand.semanticPayloads, operand.operandLayout, operand.operationKind, operand.passSegment, bindings)
        }
        val payload = GPUPreparedNativeFramePayload(old.identity, operands, old.scopeOperandKeys,
            listOf(GPUPreparedNativeAuxiliaryHandle(owned, GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion)) + old.auxiliaryOwnedHandles,
            old.leaseLifecycle, old.pathDepthStencilViewAuthority, old.clipDepthStencilViewAuthority)
        replacement = GPUPreparedNativeFrameDraft(payload)
        check(geometry.draft.transferOwnershipToDraft(requireNotNull(replacement)))
        return GPUPreparedNativeFramePayloadMaterialization.Materialized(requireNotNull(replacement))
    } catch (failure: Throwable) {
        // Give the ordinary rollback journal the added owners even when source construction fails.
        if (replacement == null) {
            replacement = GPUPreparedNativeFrameDraft(GPUPreparedNativeFramePayload(old.identity, old.scopeOperands,
                old.scopeOperandKeys, listOf(GPUPreparedNativeAuxiliaryHandle(owned,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion)) + old.auxiliaryOwnedHandles, old.leaseLifecycle,
                old.pathDepthStencilViewAuthority, old.clipDepthStencilViewAuthority))
            check(geometry.draft.transferOwnershipToDraft(requireNotNull(replacement)))
        }
        return GPUPreparedNativeFramePayloadMaterialization.Refused("failed.native-w5a.source-stage-v2",
            "W5a fragment source materialization failed: ${failure.message.orEmpty()}", requireNotNull(replacement))
    }
}
