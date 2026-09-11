package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.materials.w5aCombinedMemoryBudgetV2
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary

/** Native realization of the separately sealed, reusable W5b snapshot resource. */
internal class GPUW5bDestinationSnapshotNativeV3(val texture: GPUTexture, val view: GPUTextureView) : AutoCloseable {
    override fun close() { try { view.close() } finally { texture.close() } }
}

/** Borrowed canonical W4e mask view: its composite attachment lease owns the lifetime. */
internal class GPUW5bCoverageNativeV4(val witness: org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3,
    val view: GPUTextureView) : AutoCloseable {
    init { require(witness.clipPrefixV4 != null) }
    override fun close() = Unit
}

internal enum class GPUW5bInlineCoverageV3 { NativeMask, NativeFull }

/** Original, authenticated geometry descriptor. W5a changes no geometry or attachment state. */
internal data class GPUW5aGeometryPipelineTemplate(
    val source: String,
    val descriptor: RenderPipelineDescriptor,
    val groupZero: GPUBindGroupLayout,
    val w5bInlineCoverageV3: GPUW5bInlineCoverageV3? = null,
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
    val destinationGroupV3: GPUPreparedNativeBindGroupOperand? = null,
    val coverageGroupV4: GPUPreparedNativeBindGroupOperand? = null,
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
        if (operand.w5bInitialClearV3 !== render.w5bInitialClearV3) return@all false
        val expected = runCatching { sourceDrawsV2(render, operand).withIndex().mapNotNull { (ordinalI32, source) ->
            source?.let { ordinalI32 to it }
        } }.getOrNull() ?: return@all false
        operand.w5aSourceBindingsV2.size == expected.size &&
            operand.w5aSourceBindingsV2.zip(expected).all { (binding, source) ->
                val destination = nativeSourcePacketV3(render.drawPackets, source.first, source.second)?.blendPlan
                    as? GPUBlendPlan.ShaderBlendWithDstRead
                (destination == null || destination.sealedW5b != null) &&
                    binding.drawOrdinalI32 == source.first && binding.source === source.second &&
                    (binding.destinationGroupV3 != null) == (destination != null) &&
                    (binding.coverageGroupV4 != null) == (destination?.sealedW5b?.compositionAbiI32 == 4) &&
                    binding.byteCapacityI64 == source.second.stage.uniformByteCountI64 &&
                    binding.pipeline.deviceGeneration == payload.identity.deviceGeneration &&
                    binding.bindGroup.deviceGeneration == payload.identity.deviceGeneration &&
                    owners.any { it.owns(binding.buffer) && it.owns(binding.pipeline.pipeline) && it.owns(binding.bindGroup.bindGroup) &&
                        (binding.destinationGroupV3 == null || it.owns(binding.destinationGroupV3.bindGroup)) &&
                        (binding.coverageGroupV4 == null || it.owns(binding.coverageGroupV4.bindGroup)) } &&
                    (binding.coverageGroupV4 == null || binding.coverageGroupV4.deviceGeneration == payload.identity.deviceGeneration) &&
                    (binding.destinationGroupV3 == null || binding.destinationGroupV3.deviceGeneration == payload.identity.deviceGeneration)
            }
    }
}

private fun nativeSourcePacketV3(packets: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket>,
    ordinalI32: Int, source: W5aPacketMaterialSourceV2) =
    packets.singleOrNull()?.takeIf { packet ->
        packet.w5bFinalFrameWitnessV3?.w4eLane?.owns(packet) == true &&
            packet.w5aSourceStageV2 === source && packet.w4ePreparedFrameAuthority != null
    } ?: packets.getOrNull(ordinalI32)

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
private fun composeSource(template: GPUW5aGeometryPipelineTemplate, source: W5aPacketMaterialSourceV2,
    destination: GPUBlendPlan.ShaderBlendWithDstRead? = null,
    destinationBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds? = null): String {
    val target = requireNotNull(template.descriptor.fragment).targets.single()
    val blend = target.blend
    require(target.format == GPUTextureFormat.RGBA8UnormSrgb &&
        (if (destination == null) blend != null && listOf(blend.color, blend.alpha).all { it.operation == GPUBlendOperation.Add }
        else blend != null && listOf(blend.color, blend.alpha).all {
            it.operation == GPUBlendOperation.Add && it.srcFactor == GPUBlendFactor.One && it.dstFactor == GPUBlendFactor.Zero
        } && destination.sealedW5b?.compositionAbiI32 in 3..4)) {
        "W5b source DAG requires an authenticated premultiplied fixed-function sRGB attachment tail"
    }
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
    val sourceExpression = "kanvas_material_source(vec2<f32>(0.0))"
    val analyticCoverage = destination?.sealedW5b?.compositionAbiI32 == 3 &&
        destination.sourceCoverageEncoding == org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding.ScalarCoverageInShader
    val tail = if (destination == null) "" else {
        val scalar = destination.sealedW5b?.compositionAbiI32 == 4
        require(destination.sourceCoverageEncoding == if (scalar || analyticCoverage)
            org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding.ScalarCoverageInShader
            else org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding.None) {
            "W5b W3 destination tail requires sealed full/scissor coverage"
        }
        require(!geometry.contains("@group(2)"))
        if (analyticCoverage) {
            when (template.w5bInlineCoverageV3) {
                GPUW5bInlineCoverageV3.NativeMask -> {
                    require(geometry.contains("fn fs_main(@builtin(position) position: vec4<f32>)") &&
                        geometry.contains("return consumer.color * coverage;"))
                    geometry = geometry.replace("return consumer.color * coverage;",
                        "return kanvas_w5b_target($sourceExpression, position.xy, coverage);")
                }
                GPUW5bInlineCoverageV3.NativeFull -> {
                    require(geometry.contains("fn fs_main()") && geometry.contains("return consumer.color;"))
                    geometry = geometry.replace("fn fs_main()", "fn fs_main(@builtin(position) fragment_position: vec4<f32>)")
                        .replace("return consumer.color;", "return kanvas_w5b_target($sourceExpression, fragment_position.xy, 1.0);")
                }
                null -> {
                    require(geometry.contains("fn fs_main(@builtin(position) fragment_position: vec4<f32>)") &&
                        geometry.contains("return analytic.premul_rgba * coverage;"))
                    geometry = geometry.replace("return analytic.premul_rgba * coverage;",
                        "return kanvas_w5b_target($sourceExpression, fragment_position.xy, coverage);")
                }
            }
        } else {
            require(geometry.contains("fn fs_main()"))
            geometry = geometry.replace("fn fs_main()", "fn fs_main(@builtin(position) fragment_position: vec4<f32>)")
        }
        val formula = requireNotNull(GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
            destination.mode.gpuLabel, destination.formulaId, "kanvas_w5b_blend"))
        """
            @group(2) @binding(0) var kanvas_w5b_destination: texture_2d<f32>;
            @group(2) @binding(1) var kanvas_w5b_sampler: sampler;
            ${if (scalar) "@group(3) @binding(0) var kanvas_w5b_coverage: texture_2d<f32>;" else ""}
            $formula
            fn kanvas_w5b_target(src: vec4<f32>, pixel: vec2<f32>${if (analyticCoverage) ", coverage: f32" else ""}) -> vec4<f32> {
                let dst = textureSampleLevel(kanvas_w5b_destination, kanvas_w5b_sampler,
                    (pixel - vec2<f32>(${requireNotNull(destinationBounds).left}.0, ${destinationBounds.top}.0)) /
                        vec2<f32>(textureDimensions(kanvas_w5b_destination)), 0.0);
                let blended = kanvas_w5b_blend(src, dst);
                ${if (scalar) "let mask_sample: vec4<f32> = textureLoad(kanvas_w5b_coverage, vec2<i32>(pixel), 0); let coverage = clamp(mask_sample.r, 0.0, 1.0); return dst + coverage * (blended - dst);" else if (analyticCoverage) "return dst + coverage * (blended - dst);" else "return blended;"}
            }
        """.trimIndent().replace(Regex("\\bvec([234])([fiu])\\b")) { "vec${it.groupValues[1]}<${it.groupValues[2]}32>" }
    }
    slots.forEach { slot -> geometry = geometry.replace(slot, if (destination == null || analyticCoverage) sourceExpression
        else "kanvas_w5b_target($sourceExpression, fragment_position.xy)") }
    val result = geometry + "\n" + source.stage.declarationsWgsl + "\n" + tail
    val composed = (validateColorWgsl("w5a-source-v2:${source.stage.structuralId}", result) as? GPUColorWgslValidation.Validated)
        ?.reflection?.report ?: error("Composed W5a fragment module failed parser validation")
    val material = composed.bindings.singleOrNull { it.group == 1 }
    require(original.validation.success && composed.validation.success &&
        original.bindings.all { it.group == 0 } &&
        composed.bindings.filter { it.group == 0 } == original.bindings &&
        composed.bindings.size == original.bindings.size + (if (destination == null) 1 else if (destination.sealedW5b?.compositionAbiI32 == 4) 4 else 3) && material != null &&
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
        data class SourcePipelineKey(
            val geometryPipeline: GPURenderPipeline,
            val sourceStructuralId: String,
            val compositionAbiI32: Int,
            val destinationKey: org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey?,
            val destinationBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds?,
        )
        val pipelines = mutableMapOf<SourcePipelineKey, Pair<GPUPreparedNativeRenderPipelineOperand, GPUBindGroupLayout>>()
        val buffers = mutableMapOf<String, GPUBuffer>()
        val groups = mutableMapOf<Pair<String, GPUBindGroupLayout>, GPUPreparedNativeBindGroupOperand>()
        val destinationSnapshot = old.auxiliaryOwnedHandles.mapNotNull { it.handle as? GPUW5bDestinationSnapshotNativeV3 }.singleOrNull()
        val coverage = old.auxiliaryOwnedHandles.mapNotNull { it.handle as? GPUW5bCoverageNativeV4 }.singleOrNull()
        require(coverage == null || coverage.witness.validates(framePlan))
        val coverageLayout = coverage?.let { owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(
            label = "Kanvas.w5b.w4e-coverage-group3-abi-v4", entries = listOf(BindGroupLayoutEntry(
                binding = 0u, visibility = GPUShaderStage.Fragment,
                texture = TextureBindingLayout(sampleType = GPUTextureSampleType.Float)))))) }
        val coverageGroup = coverage?.let { GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(
            BindGroupDescriptor(label = "Kanvas.w5b.w4e-coverage-v4", layout = requireNotNull(coverageLayout),
                entries = listOf(BindGroupEntry(binding = 0u, resource = it.view))))), generation) }
        val destinationLayout = destinationSnapshot?.let { owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(
            label = "Kanvas.w5b.destination-group2-abi-v3", entries = listOf(
                BindGroupLayoutEntry(binding = 0u, visibility = GPUShaderStage.Fragment,
                    texture = TextureBindingLayout(sampleType = GPUTextureSampleType.Float)),
                BindGroupLayoutEntry(binding = 1u, visibility = GPUShaderStage.Fragment,
                    sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering)),
            )))) }
        val destinationGroup = destinationSnapshot?.let { snapshot ->
            val sampler = owned.own(device.createSampler(SamplerDescriptor(label = "Kanvas.w5b.nearest",
                magFilter = GPUFilterMode.Nearest, minFilter = GPUFilterMode.Nearest)))
            GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(BindGroupDescriptor(
                label = "Kanvas.w5b.destination-group2-v3", layout = requireNotNull(destinationLayout), entries = listOf(
                    BindGroupEntry(binding = 0u, resource = snapshot.view), BindGroupEntry(binding = 1u, resource = sampler),
                )))), generation)
        }
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
                val sourcePacket = nativeSourcePacketV3(packets, ordinalI32, source)
                val destination = (sourcePacket?.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead)
                    ?.also { requireNotNull(it.sealedW5b) { "W5 destination-read source lost its sealed final blend" } }
                require(destination == null || destinationGroup != null)
                val destinationCopy = destination?.let { framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
                    .single { copy -> copy.consumers.any { it.packetId == sourcePacket?.packetId } } }
                val scalar = destination?.sealedW5b?.compositionAbiI32 == 4
                require(!scalar || coverageGroup != null && packets[ordinalI32].corePrimitivePreparedAuthority?.w5bFrameWitnessV3 === coverage?.witness)
                val base = requireNotNull(currentPipeline)
                val key = SourcePipelineKey(base.pipeline, source.stage.structuralId, destination?.sealedW5b?.compositionAbiI32 ?: 2,
                    destinationCopy?.sourceKey, destinationCopy?.logicalBounds)
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
                        label = "Kanvas.w5a.source-v2.${source.stage.structuralId}", code = composeSource(template, source, destination, destinationCopy?.logicalBounds))))
                    val pipelineLayout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(
                        label = "Kanvas.composed-abi-v${destination?.sealedW5b?.compositionAbiI32 ?: 2}",
                        bindGroupLayouts = listOf(template.groupZero, layout) +
                            (if (destination == null) emptyList() else listOf(requireNotNull(destinationLayout))) +
                            (if (scalar) listOf(requireNotNull(coverageLayout)) else emptyList()))))
                    val descriptor = template.descriptor
                    val native = owned.own(device.createRenderPipeline(descriptor.copy(
                        label = "Kanvas.w5a.fragment-source-v2.${source.stage.structuralId}",
                        layout = pipelineLayout,
                        vertex = VertexState(module = shader, buffers = descriptor.vertex.buffers,
                            entryPoint = descriptor.vertex.entryPoint, constants = descriptor.vertex.constants),
                        fragment = requireNotNull(descriptor.fragment).let { fragment -> FragmentState(
                            module = shader, targets = if (destination == null) fragment.targets else fragment.targets.map {
                                ColorTargetState(format = it.format, blend = null, writeMask = it.writeMask)
                            }, entryPoint = fragment.entryPoint,
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
                bindings += GPUW5aNativeSourceBindingV2(ordinalI32, source, pipeline, group, buffer, bytes.size.toLong(),
                    destinationGroup.takeIf { destination != null }, coverageGroup.takeIf { scalar })
            }
              GPUPreparedNativeScopeOperand.Render(operand.sourceStepIndex, operand.pass, operand.commands,
                  operand.semanticPayloads, operand.operandLayout, operand.operationKind, operand.passSegment, bindings,
                  operand.w5bInitialClearV3)
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
