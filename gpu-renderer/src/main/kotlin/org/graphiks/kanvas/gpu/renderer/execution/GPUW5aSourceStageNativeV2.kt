package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.materials.w5aCombinedMemoryBudgetV2
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUW5aGeometryHostTemplateV1
import org.graphiks.kanvas.gpu.renderer.recording.GPUW5aHostBindingLayoutV1
import org.graphiks.kanvas.gpu.renderer.recording.nativeDescriptorV1
import org.graphiks.kanvas.gpu.renderer.recording.hostTargetV1
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary

/** Native realization of the separately sealed, reusable W5b snapshot resource. */
internal class GPUW5bDestinationSnapshotNativeV3(val texture: GPUTexture, val view: GPUTextureView) : AutoCloseable {
    override fun close() { try { view.close() } finally { texture.close() } }
}

/** Borrow of the prepared destination owner; identity is the exact preflight-sealed copy. */
internal class GPUW5bPreparedDestinationNativeV6(val copy: GPUFrameStep.CopyDestinationStep,
    val view: GPUTextureView) : AutoCloseable {
    override fun close() = Unit
}

/** Borrowed canonical W4e mask view: its composite attachment lease owns the lifetime. */
internal class GPUW5bCoverageNativeV4(val witness: org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3,
    val view: GPUTextureView) : AutoCloseable {
    init { require(witness.clipPrefixV4 != null) }
    override fun close() = Unit
}

internal enum class GPUW5bInlineCoverageV3 { NativeMask, NativeFull, PreparedTextA8 }
internal enum class MaterialCoordinateSlotV1(val devicePointWgsl: String) {
    FragmentPosition("fragment_position.xy"),
    Position("position.xy"),
    InputPosition("input.position.xy"),
}

/** Original, authenticated geometry descriptor. W5a changes no geometry or attachment state. */
internal data class GPUW5aGeometryPipelineTemplate(
    val pipelineRecipeId: String,
    val descriptor: RenderPipelineDescriptor,
    val groupZero: GPUBindGroupLayout,
    val w5bInlineCoverageV3: GPUW5bInlineCoverageV3? = null,
    val materialCoordinateSlot: MaterialCoordinateSlotV1? = null,
)

internal interface GPUW5aGeometryPipelineTemplateProvider {
    fun sourceTemplate(pipeline: GPURenderPipeline): GPUW5aGeometryPipelineTemplate?
}

/** Exact V2 source partition; historical geometry operands/commands keep their original ABI. */
internal class GPUW5gImageLeaseV5(val image: org.graphiks.kanvas.gpu.plan.ComposedImageResourceV5,
    val lease: GPUW5eDecodedImageSessionCache.Lease)

internal class GPUW5hResourceLeaseV1(val reference: org.graphiks.kanvas.gpu.plan.RuntimeEffectResourceReferenceV1,
    val texture: GPUW5eDecodedImageSessionCache.Lease? = null,val runtime: GPUW5hRuntimeResourceSessionCache.Lease? = null) {
    init { require((texture == null) != (runtime == null)) }
    val owner: AutoCloseable get() = texture ?: requireNotNull(runtime)
    fun binding(): BindGroupEntry = texture?.let { GPUW5eImageNativeV1.binding(it,reference.bindingI32.toUInt()) }
        ?: requireNotNull(runtime).binding(reference.bindingI32)
    fun matches(generationI64: Long): Boolean = texture?.let {
        val request=reference.cacheRequest as? org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest.Texture ?: return false
        it.matches(request,generationI64)
    } ?: requireNotNull(runtime).matches(reference.cacheRequest,generationI64)
}

internal class GPUW5aNativeSourceBindingV2(
    val drawOrdinalI32: Int,
    val source: W5aPacketMaterialSourceV2,
    val pipeline: GPUPreparedNativeRenderPipelineOperand,
    val bindGroup: GPUPreparedNativeBindGroupOperand,
    val buffer: GPUBuffer,
    val byteCapacityI64: Long,
    val destinationGroupV3: GPUPreparedNativeBindGroupOperand? = null,
    val coverageGroupV4: GPUPreparedNativeBindGroupOperand? = null,
    val imageLeaseV3: GPUW5eDecodedImageSessionCache.Lease? = null,
    composedImagesV5: List<GPUW5gImageLeaseV5> = emptyList(),
    val noiseBufferV1: GPUBuffer? = null,
    runtimeResourcesV1: List<GPUW5hResourceLeaseV1> = emptyList(),
) {
    val runtimeResourcesV1: List<GPUW5hResourceLeaseV1> = java.util.Collections.unmodifiableList(ArrayList(runtimeResourcesV1))
    val composedImagesV5: List<GPUW5gImageLeaseV5> = java.util.Collections.unmodifiableList(ArrayList(composedImagesV5))
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
                    (binding.noiseBufferV1 != null) == (source.second.stage.noiseTableSlab != null) &&
                    (binding.noiseBufferV1 == null || owners.any { it.owns(binding.noiseBufferV1) }) &&
                    (binding.imageLeaseV3 != null) == (source.second.stage.imageV3 != null) &&
                    (binding.imageLeaseV3 == null || source.second.stage.imageV3?.let {
                        binding.imageLeaseV3.matches(it.cacheRequest, payload.identity.deviceGeneration.value)
                    } == true) &&
                    binding.composedImagesV5.map { it.image } == source.second.stage.composedProof?.composedImageResources.orEmpty().distinctBy { it.resource } &&
                    binding.composedImagesV5.all { image ->
                        image.lease.matches(image.image.upload.cacheRequest,payload.identity.deviceGeneration.value) &&
                            owners.any { it.owns(image.lease) }
                    } &&
                    binding.runtimeResourcesV1.map { it.reference } == source.second.stage.composedProof?.runtimeResources.orEmpty() &&
                    binding.runtimeResourcesV1.all { it.matches(payload.identity.deviceGeneration.value) && owners.any { owner -> owner.owns(it.owner) } } &&
                    binding.pipeline.deviceGeneration == payload.identity.deviceGeneration &&
                    binding.bindGroup.deviceGeneration == payload.identity.deviceGeneration &&
                    owners.any { it.owns(binding.buffer) && it.owns(binding.pipeline.pipeline) && it.owns(binding.bindGroup.bindGroup) &&
                        (binding.destinationGroupV3 == null || it.owns(binding.destinationGroupV3.bindGroup)) &&
                        (binding.coverageGroupV4 == null || it.owns(binding.coverageGroupV4.bindGroup)) } &&
                    (binding.imageLeaseV3 == null || owners.any { it.owns(binding.imageLeaseV3) }) &&
                    (binding.coverageGroupV4 == null || binding.coverageGroupV4.deviceGeneration == payload.identity.deviceGeneration) &&
                    (binding.destinationGroupV3 == null || binding.destinationGroupV3.deviceGeneration == payload.identity.deviceGeneration)
            }
    }
}

private fun nativeSourcePacketV3(packets: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket>,
    ordinalI32: Int, source: W5aPacketMaterialSourceV2) =
    packets.singleOrNull()?.takeIf { packet ->
        packet.w5bFinalFrameWitnessV3?.w4eLane?.owns(packet) == true &&
            packet.materialSourcePartitionV3() === source && packet.w4ePreparedFrameAuthority != null
    } ?: packets.getOrNull(ordinalI32)

/** W4e inverse-domain packets seal an atomic stencil prefix plus one final color draw. */
private fun sourceDrawsV2(
    render: GPUFrameStep.RenderPassStep,
    operand: GPUPreparedNativeScopeOperand.Render,
): List<W5aPacketMaterialSourceV2?> {
    if (render.drawPackets.none { it.materialSourcePartitionV3() != null }) return emptyList()
    val pipelines = buildList {
        var current: GPUPreparedNativeRenderPipelineOperand? = null
        operand.commands.forEach { command ->
            if (command is GPUPreparedNativeRenderCommand.SetPipeline) current = command.pipeline
            if (command is GPUPreparedNativeRenderCommand.Draw || command is GPUPreparedNativeRenderCommand.DrawIndexed) {
                add(requireNotNull(current))
            }
        }
    }
    if (pipelines.size == render.drawPackets.size) return render.drawPackets.map { it.materialSourcePartitionV3() }
    val packet = render.drawPackets.single()
    require(packet.w4ePreparedFrameAuthority != null && packet.w4ePreparedPath != null &&
        packet.w4ePreparedClipConsumer is org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority.InverseDomain &&
        pipelines.size in 2..3 && pipelines.dropLast(1).all {
            it.bindingPolicy == GPUPreparedNativeRenderPipelineBindingPolicy.NoBindings
        } && pipelines.last().bindingPolicy == GPUPreparedNativeRenderPipelineBindingPolicy.BindGroupRequired) {
        "W5a source requires exact packet-to-native color draw order"
    }
    return List(pipelines.size - 1) { null } + packet.materialSourcePartitionV3()
}

/** Compose only the source expression. Existing coverage and fixed-function tail stay exact. */
internal fun composeW5aHostSourceV1(template: GPUW5aGeometryHostTemplateV1, source: W5aPacketMaterialSourceV2,
    destination: GPUBlendPlan.ShaderBlendWithDstRead? = null,
    destinationBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds? = null): String {
    val target = template.target
    val blend = listOf(target.colorBlend, target.alphaBlend)
    require(target.format == GPUTextureFormat.RGBA8UnormSrgb &&
        (if (destination == null) blend.all { it.operation == GPUBlendOperation.Add }
        else blend.all {
            it.operation == GPUBlendOperation.Add && it.srcFactor == GPUBlendFactor.One && it.dstFactor == GPUBlendFactor.Zero
        } && destination.sealedW5b?.compositionAbiI32 in 3..4)) {
        "W5b source DAG requires an authenticated premultiplied fixed-function sRGB attachment tail"
    }
    // The renderer reflection parser uses explicit scalar type parameters; W4e's native
    // spelling uses WGSL's equivalent vector aliases. Normalize those before composition.
    var geometry = template.sourceWgsl.replace(Regex("\\bvec([234])([fiu])\\b")) {
        "vec${it.groupValues[1]}<${it.groupValues[2]}32>"
    }
    val original = (validateColorWgsl("w5a-geometry-v2", geometry) as? GPUColorWgslValidation.Validated)
        ?.reflection?.report ?: error("W5a geometry requires parser-backed reflection")
    require(original.entryPoints.any { it.name == template.vertexEntryPoint && it.stage == "vertex" } &&
        original.entryPoints.any { it.name == template.fragmentEntryPoint && it.stage == "fragment" })
    require(original.bindings.size == template.groupZeroLayout.entries.size && template.groupZeroLayout.entries.all { entry ->
        val binding = original.bindings.singleOrNull { it.group == 0 && it.binding == entry.bindingI32 } ?: return@all false
        when (val layout = entry.layout) {
            is GPUW5aHostBindingLayoutV1.Buffer -> binding.resourceKind == "uniformBuffer" &&
                layout.type == GPUBufferBindingType.Uniform &&
                binding.minBindingSize?.toLong() == layout.minBindingSizeBytesI64
            is GPUW5aHostBindingLayoutV1.Texture -> binding.resourceKind == "sampledTexture" &&
                binding.sampleType == "float" && binding.viewDimension == "2d" && !layout.multisampled &&
                layout.sampleType == GPUTextureSampleType.Float && layout.viewDimension == GPUTextureViewDimension.TwoD
            is GPUW5aHostBindingLayoutV1.Sampler -> binding.resourceKind == "sampler"
        }
    }) { "W5a geometry host layout disagrees with reflection" }
    val slots = listOf("core.premul_rgba", "analytic.premul_rgba", "drrect.premul_rgba",
        "consumer.premul_rgba", "consumer.color", "vec4<f32>(input.localPosition, 0.0, 0.0)")
    require(slots.any(geometry::contains)) { "W5a source requires an authenticated color-writing geometry shader" }
    require(!geometry.contains("@group(1)")) { "W5a source group is already occupied" }
    val requiresCoordinates = source.stage.consumesDevicePositionF32
    require(!requiresCoordinates || template.materialCoordinateSlot != null)
    require(template.primitiveEncodedInput == (source.stage.composedProof?.consumesPrimitiveEncodedInput == true))
    val point = template.materialCoordinateSlot?.devicePointWgsl ?: "fragment_position.xy"
    val coordinates = if (requiresCoordinates) "${source.stage.coordinateFunctionName}($point)" else "vec2<f32>(0.0)"
    val sourceExpression = "kanvas_material_source($coordinates${if (template.primitiveEncodedInput) ", input.primitiveColor" else ""})"
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
                GPUW5bInlineCoverageV3.PreparedTextA8 -> {
                    require(geometry.contains("fn fs_main(input: PreparedTextVertexOutput)") &&
                        geometry.contains("return coverageFactor * vec4<f32>(input.localPosition, 0.0, 0.0);"))
                    geometry = geometry.replace("return coverageFactor * vec4<f32>(input.localPosition, 0.0, 0.0);",
                        "return kanvas_w5b_target($sourceExpression, input.position.xy, coverageFactor);")
                }
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
            if (template.materialCoordinateSlot != MaterialCoordinateSlotV1.InputPosition) {
                require(geometry.contains("fn fs_main()"))
                geometry = geometry.replace("fn fs_main()", "fn fs_main(@builtin(position) fragment_position: vec4<f32>)")
            }
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
    if (requiresCoordinates && geometry.contains("fn fs_main()")) geometry = geometry.replace("fn fs_main()",
        "fn fs_main(@builtin(position) fragment_position: vec4<f32>)")
    slots.forEach { slot -> geometry = geometry.replace(slot, if (destination == null || analyticCoverage) sourceExpression
        else "kanvas_w5b_target($sourceExpression, $point)") }
    val result = geometry + "\n" + source.stage.declarationsWgsl + "\n" + tail
    val composed = (validateColorWgsl("w5a-source-v2:${source.stage.structuralId}", result) as? GPUColorWgslValidation.Validated)
        ?.reflection?.report ?: error("Composed W5a fragment module failed parser validation")
    val material = composed.bindings.singleOrNull { it.group == 1 && it.binding == 0 }
    val manifest = source.stage.bindingManifest
    require(original.validation.success && composed.validation.success &&
        original.bindings.all { it.group == 0 } &&
        composed.bindings.filter { it.group == 0 } == original.bindings &&
        composed.bindings.filter { it.group == 1 }.size == manifest.size &&
        manifest.all { expected -> composed.bindings.any { it.group == 1 && it.binding == expected.bindingI32 &&
            it.resourceKind == expected.resourceKind && (expected.composedResource?.buffer?.let { buffer ->
                // Runtime-array binding size is unavailable in this parser's
                // report; its reflected element layout supplies the declared
                // minimum. The complete dynamic slab is authenticated separately.
                it.access == "read" && (if(buffer.storageKind == org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.RUNTIME_READ)
                    buffer.minBindingSizeBytesI64 >= 4L && composed.layouts.any { layout ->
                        layout.structName == "W5hStorageBinding${expected.bindingI32}" && layout.addressSpace == "storage" &&
                            layout.size.toLong() == buffer.minBindingSizeBytesI64 &&
                            layout.alignment == 4 && layout.members.singleOrNull()?.let { member ->
                                member.offset == 0 && member.size.toLong() == buffer.minBindingSizeBytesI64 && member.alignment == 4 &&
                                    member.stride == 4
                            } == true
                    } else composed.layouts.any { layout ->
                    layout.structName == when(buffer.storageKind) {
                        org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS -> "GradientStopV1"
                        org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.NOISE_U32 -> "NoiseWordV1"
                        org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.RUNTIME_READ -> error("handled above")
                    } && layout.addressSpace == "storage" &&
                        layout.size.toLong() == buffer.minBindingSizeBytesI64 && layout.alignment == 16 &&
                        layout.members.map { member -> member.offset } == when(buffer.storageKind) {
                            org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS -> listOf(0,16)
                            org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.NOISE_U32 -> listOf(0)
                            org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.RUNTIME_READ -> error("handled above")
                        } &&
                        layout.members.all { member -> member.size == 16 && member.alignment == 16 }
                })
            } ?: true) && (expected.composedResource?.texture?.let { texture ->
                texture.textureViewDimensionTagU32 == 1u && texture.textureSampleTypeTagU32 == 1u && !texture.multisampled &&
                    it.resourceKind == "sampledTexture" && it.sampleType == "float" && it.viewDimension == "2d" &&
                    it.access == "read" && it.storageFormat == null
            } ?: true) && (expected.composedResource?.sampler?.let { sampler ->
                sampler.samplerTypeTagU32 in setOf(1u,2u) && it.resourceKind == "sampler"
            } ?: true) } } &&
        composed.bindings.size == original.bindings.size + manifest.size + (if (destination == null) 0 else if (destination.sealedW5b?.compositionAbiI32 == 4) 3 else 2) && material != null &&
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
    sourceWitness: W5hFrameSourceValidationWitnessV1,
    geometry: GPUPreparedNativeFramePayloadMaterialization,
    templates: GPUW5aGeometryPipelineTemplateProvider,
    imageCache: GPUW5eDecodedImageSessionCache? = null,
    runtimeResourceCache: GPUW5hRuntimeResourceSessionCache? = null,
): GPUPreparedNativeFramePayloadMaterialization {
    require(sourceWitness.authenticates(framePlan))
    if (geometry !is GPUPreparedNativeFramePayloadMaterialization.Materialized) return geometry
    val renders = framePlan.steps.withIndex().filter { it.value is GPUFrameStep.RenderPassStep }
        .associate { it.index to (it.value as GPUFrameStep.RenderPassStep) }
    if (renders.values.none { render -> render.drawPackets.any { it.materialSourcePartitionV3() != null } }) return geometry
    val old = geometry.draft.payload
    val owned = GPUW5aSourceOwnedHandlesV2()
    val generation = old.identity.deviceGeneration
    var replacement: GPUPreparedNativeFrameDraft? = null
    try {
        val stopSlabs = renders.values.flatMap { it.drawPackets }.mapNotNull { it.materialSourcePartitionV3()?.stage?.gradientStopSlab }
            .distinctBy { it.canonicalIdentity }
        val stopBuffer = stopSlabs.singleOrNull()?.let { materializeGradientStopsV1(device, queue, it, owned) }
        val noiseStages=renders.values.flatMap { it.drawPackets }.mapNotNull { it.materialSourcePartitionV3()?.stage }
            .filter { it.noiseTableSlab != null }
        val noiseSlabs=noiseStages.map { requireNotNull(it.noiseTableSlab) }.distinct()
        val noiseBuffer=noiseSlabs.singleOrNull()?.let { slab ->
            val bytes=ByteArray(slab.bytes.sizeI32) { slab.bytes[it].toByte() }
            owned.own(device.createBuffer(BufferDescriptor(size=bytes.size.toULong(),
                usage=GPUBufferUsage.Storage or GPUBufferUsage.CopyDst,label="Kanvas.noise-v1.tables"))).also {
                queue.writeBuffer(it,0uL,ArrayBuffer.of(bytes),0uL,bytes.size.toULong())
            }
        }
        data class SourcePipelineKey(
            val deviceGenerationI64: Long,
            val programIdentity: String,
            val geometryPipeline: GPURenderPipeline,
            val compositionAbiI32: Int,
            val destinationKey: org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey?,
            val destinationBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds?,
        )
        val pipelines = mutableMapOf<SourcePipelineKey, Pair<GPUPreparedNativeRenderPipelineOperand, GPUBindGroupLayout>>()
        val buffers = mutableMapOf<String, GPUBuffer>()
        val groups = mutableMapOf<Pair<String, GPUBindGroupLayout>, GPUPreparedNativeBindGroupOperand>()
        val destinationSnapshot = old.auxiliaryOwnedHandles.mapNotNull { it.handle as? GPUW5bDestinationSnapshotNativeV3 }.singleOrNull()
        val preparedDestinations = old.auxiliaryOwnedHandles.mapNotNull { it.handle as? GPUW5bPreparedDestinationNativeV6 }
        val coverage = old.auxiliaryOwnedHandles.mapNotNull { it.handle as? GPUW5bCoverageNativeV4 }.singleOrNull()
        require(coverage == null || coverage.witness.validates(framePlan))
        val coverageLayout = coverage?.let { owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(
            label = "Kanvas.w5b.w4e-coverage-group3-abi-v4", entries = listOf(BindGroupLayoutEntry(
                binding = 0u, visibility = GPUShaderStage.Fragment,
                texture = TextureBindingLayout(sampleType = GPUTextureSampleType.Float)))))) }
        val coverageGroup = coverage?.let { GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(
            BindGroupDescriptor(label = "Kanvas.w5b.w4e-coverage-v4", layout = requireNotNull(coverageLayout),
                entries = listOf(BindGroupEntry(binding = 0u, resource = it.view))))), generation) }
        val destinationLayout = if (destinationSnapshot == null && preparedDestinations.isEmpty()) null else owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(
            label = "Kanvas.w5b.destination-group2-abi-v3", entries = listOf(
                BindGroupLayoutEntry(binding = 0u, visibility = GPUShaderStage.Fragment,
                    texture = TextureBindingLayout(sampleType = GPUTextureSampleType.Float)),
                BindGroupLayoutEntry(binding = 1u, visibility = GPUShaderStage.Fragment,
                    sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering)),
            ))))
        val destinationGroup = destinationSnapshot?.let { snapshot ->
            val sampler = owned.own(device.createSampler(SamplerDescriptor(label = "Kanvas.w5b.nearest",
                magFilter = GPUFilterMode.Nearest, minFilter = GPUFilterMode.Nearest)))
            GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(BindGroupDescriptor(
                label = "Kanvas.w5b.destination-group2-v3", layout = requireNotNull(destinationLayout), entries = listOf(
                    BindGroupEntry(binding = 0u, resource = snapshot.view), BindGroupEntry(binding = 1u, resource = sampler),
                )))), generation)
        }
        val preparedDestinationGroups = preparedDestinations.associate { borrowed ->
            require(framePlan.steps.any { it === borrowed.copy })
            val sampler = owned.own(device.createSampler(SamplerDescriptor(label = "Kanvas.w5b.prepared.nearest",
                magFilter = GPUFilterMode.Nearest, minFilter = GPUFilterMode.Nearest)))
            borrowed.copy to GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(BindGroupDescriptor(
                label = "Kanvas.w5b.prepared.destination-group2-v6", layout = requireNotNull(destinationLayout), entries = listOf(
                    BindGroupEntry(binding = 0u, resource = borrowed.view), BindGroupEntry(binding = 1u, resource = sampler),
                )))), generation)
        }
        val operands = old.scopeOperands.map { operand ->
            if (operand !is GPUPreparedNativeScopeOperand.Render) return@map operand
            val packets = renders.getValue(operand.sourceStepIndex).drawPackets
            if (packets.none { it.materialSourcePartitionV3() != null }) return@map operand
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
                val validated = sourceWitness.packet(framePlan, requireNotNull(sourcePacket))
                val destination = (sourcePacket?.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead)
                    ?.also { requireNotNull(it.sealedW5b) { "W5 destination-read source lost its sealed final blend" } }
                val destinationCopy = destination?.let { framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
                    .single { copy -> copy.consumers.any { it.packetId == sourcePacket?.packetId } } }
                val exactDestinationGroup = destinationCopy?.let { preparedDestinationGroups[it] ?: destinationGroup }
                require(destination == null || exactDestinationGroup != null)
                val scalar = destination?.sealedW5b?.compositionAbiI32 == 4
                require(!scalar || coverageGroup != null && packets[ordinalI32].corePrimitivePreparedAuthority?.w5bFrameWitnessV3 === coverage?.witness)
                val base = requireNotNull(currentPipeline)
                val key = SourcePipelineKey(generation.value, validated.structuralId, base.pipeline, destination?.sealedW5b?.compositionAbiI32 ?: 2,
                    destinationCopy?.sourceKey, destinationCopy?.logicalBounds)
                val (pipeline, materialLayout) = pipelines.getOrPut(key) {
                    val template = templates.sourceTemplate(base.pipeline)
                        ?: old.auxiliaryOwnedHandles.asSequence().mapNotNull { it.handle as? GPUW5aGeometryPipelineTemplateProvider }
                            .mapNotNull { it.sourceTemplate(base.pipeline) }.firstOrNull()
                        ?: error("W5a source lost its authentic geometry pipeline template")
                    require(template.descriptor.vertex.entryPoint == validated.template.vertexEntryPoint &&
                        template.pipelineRecipeId == validated.template.pipelineRecipeId &&
                        template.descriptor.fragment?.entryPoint == validated.template.fragmentEntryPoint &&
                        template.descriptor.fragment?.targets?.single()?.hostTargetV1() == validated.template.target)
                    val layout = owned.own(device.createBindGroupLayout(validated.materialLayout.nativeDescriptorV1(
                        "Kanvas.w5a.source-v2.${validated.structuralId}")))
                    val shader = owned.own(device.createShaderModule(ShaderModuleDescriptor(
                        label = "Kanvas.w5a.source-v2.${source.stage.structuralId}", code = validated.assembledModule)))
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
                // The authenticated source layout already includes a reachable
                // degenerate average, if any. Native upload never integrates colors.
                require(bytes.size.toLong() == source.stage.uniformByteCountI64)
                val buffer = buffers.getOrPut(source.stage.canonicalIdentity) {
                    owned.own(device.createBuffer(BufferDescriptor(size = bytes.size.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst, label = "Kanvas.w5a.raw-source-v2"))).also {
                        queue.writeBuffer(it, 0uL, ArrayBuffer.of(bytes), 0uL, bytes.size.toULong())
                    }
                }
                val imageLease = source.stage.imageV3?.let { execution ->
                    owned.own(GPUW5eImageNativeV1.acquire(requireNotNull(imageCache), execution.cacheRequest, generation.value))
                }
                val composedImages=source.stage.composedProof?.composedImageResources.orEmpty().distinctBy { it.resource }.map { image ->
                    val request=image.upload.cacheRequest
                    val lease=owned.own(GPUW5eImageNativeV1.acquire(requireNotNull(imageCache),request,generation.value))
                    GPUW5gImageLeaseV5(image,lease)
                }
                val runtimeLeases=source.stage.composedProof?.runtimeResources.orEmpty().map { reference ->
                    when(val request=reference.cacheRequest) {
                        is org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest.Texture -> GPUW5hResourceLeaseV1(reference,
                            texture=owned.own(GPUW5eImageNativeV1.acquire(requireNotNull(imageCache),request,generation.value)))
                        is org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest.Storage,
                        is org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest.Sampler ->
                            GPUW5hResourceLeaseV1(reference,runtime=owned.own(requireNotNull(runtimeResourceCache).acquire(request,generation.value)))
                    }
                }
                val group = groups.getOrPut(source.stage.canonicalIdentity to materialLayout) {
                    val entries = mutableListOf(BindGroupEntry(binding = 0u,
                        resource = BufferBinding(buffer = buffer, offset = 0uL, size = bytes.size.toULong())))
                    source.stage.gradientStopSlab?.let { slab ->
                        val bindings=if(source.stage.composedLayout != null) source.stage.bindingManifest.filter {
                            it.composedResource?.buffer?.storageKind == org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS }
                            else listOf(source.stage.bindingManifest.single { it.resourceKind == "storageBuffer" })
                        bindings.forEach { binding ->
                            entries += BindGroupEntry(binding = binding.bindingI32.toUInt(),
                                resource = BufferBinding(buffer = requireNotNull(stopBuffer), offset = 0uL, size = slab.byteSizeI64.toULong()))
                        }
                    }
                    source.stage.noiseTableSlab?.let { slab ->
                        val resource=requireNotNull(source.stage.composedLayout).resources.single {
                            it.buffer?.storageKind == org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.NOISE_U32 }
                        entries += BindGroupEntry(binding=resource.bindingI32.toUInt(),resource=BufferBinding(
                            buffer=requireNotNull(noiseBuffer),offset=0uL,size=slab.byteCountI64.toULong()))
                    }
                    imageLease?.let { entries += GPUW5eImageNativeV1.binding(it,
                        source.stage.bindingManifest.single { it.resourceKind == "sampledTexture" }.bindingI32.toUInt()) }
                    composedImages.forEach { image ->
                        entries += GPUW5eImageNativeV1.binding(image.lease,image.image.resource.bindingI32.toUInt())
                    }
                    entries += runtimeLeases.map { it.binding() }
                    if(source.stage.composedLayout != null) {
                        entries.sortBy { it.binding }
                        require(entries.map { it.binding.toInt() } == source.stage.bindingManifest.map { it.bindingI32 })
                    }
                    GPUPreparedNativeBindGroupOperand(owned.own(device.createBindGroup(BindGroupDescriptor(
                        label = "Kanvas.w5a.source-group1-v2", layout = materialLayout, entries = entries))), generation)
                }
                bindings += GPUW5aNativeSourceBindingV2(ordinalI32, source, pipeline, group, buffer, bytes.size.toLong(),
                    exactDestinationGroup, coverageGroup.takeIf { scalar }, imageLease,composedImages,
                    noiseBuffer.takeIf { source.stage.noiseTableSlab != null },runtimeLeases)
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
