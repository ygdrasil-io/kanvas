package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1
import org.graphiks.kanvas.gpu.plan.ComposedMaterialProgramV6
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.W5hRuntimeEffectManifest
import org.graphiks.kanvas.render.ir.*

internal sealed interface W5hFrameSourcePreflightResultV1 {
    data class Validated(val witness: W5hFrameSourceValidationWitnessV1) : W5hFrameSourcePreflightResultV1
    data class Refused(val diagnostics: List<RenderDiagnostic>) : W5hFrameSourcePreflightResultV1
}

/** No device, native handles, cache reads, or leases can enter this witness. */
internal class W5hFrameSourceValidationWitnessV1 private constructor(
    private val root: GPUW5hSourceAuthorityRootV1,
    packets: Map<String, Packet>,
) {
    internal class Packet internal constructor(
        val source: W5aPacketMaterialSourceV2,
        val template: GPUW5aGeometryHostTemplateV1,
        val assembledModule: String,
        val materialLayout: GPUW5aHostBindGroupLayoutV1,
        val structuralId: String,
        expectations: List<ComposedMaterialProgramV6.RuntimeNodeExpectation>,
        val geometryAuthority: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveGeometryAuthority?,
        val materialBindingAuthority: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveMaterialBindingAuthority?,
    ) {
        val expectations = java.util.Collections.unmodifiableList(ArrayList(expectations))
    }
    private val packets = java.util.Collections.unmodifiableMap(LinkedHashMap(packets))
    val rootFrameIdentity: String = root.frameIdentity
    fun authenticates(frame: GPUFramePlan): Boolean = root.owns(frame)
    fun packet(frame: GPUFramePlan, packet: GPUDrawPacket): Packet {
        require(authenticates(frame)) { "W5h source witness belongs to another frame root" }
        return requireNotNull(packets[packet.packetId.value]).also {
            require(root.template(packet) === it.template && packet.materialSourcePartitionV3() === it.source)
            val binding = root.coreBinding(packet)
            require(binding?.geometryAuthority === it.geometryAuthority &&
                binding?.materialBindingAuthority === it.materialBindingAuthority)
        }
    }
    companion object {
        fun preflight(frame: GPUFramePlan): W5hFrameSourcePreflightResultV1 {
            var currentPacket: GPUDrawPacket? = null
            return try {
            val packets = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { it.drawPackets }.filter { it.materialSourcePartitionV3() != null }
            require(packets.map { it.packetId.value }.distinct().size == packets.size)
            require(frame.w5aGeometryHostTemplatesV1.map { it.packetId }.toSet() == packets.map { it.packetId.value }.toSet()) {
                "Every material packet must have its sealed geometry host template"
            }
            val stages = packets.map { requireNotNull(it.materialSourcePartitionV3()).stage }
            val stops = stages.mapNotNull { it.gradientStopSlab }.distinct()
            val noise = stages.mapNotNull { it.noiseTableSlab }.distinct()
            require(stops.size <= 1 && noise.size <= 1)
            GPUW5eImageNativeV1.validate(frame)
            val validated = packets.associate { packet ->
                currentPacket = packet
                val source = requireNotNull(packet.materialSourcePartitionV3())
                val stage = source.stage
                val template = requireNotNull(frame.w5hSourceAuthorityRootV1.template(packet))
                val expectations = stage.composedProof?.composedProgramV6?.runtimeExpectations.orEmpty()
                expectations.forEach { W5hRuntimeEffectManifest.verify(it.expectation) }
                stage.composedLayout?.let { layout -> require(layout.composedBindingLayoutHash == layout.recomputeBindingLayoutHash() &&
                    stage.composedProof?.composedProgramV6?.layout === layout && stage.uniformByteCountI64 == layout.uniformBytesI64) }
                stage.composedLayout?.resources?.forEach { resource ->
                    when (resource.buffer?.storageKind) {
                        ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS -> require(stage.gradientStopSlab === stops.single() &&
                            requireNotNull(stage.composedProof).authenticatesComposedStorage(resource, stops.single()))
                        ComposedBindingLayoutV1.StorageKind.NOISE_U32 -> require(stage.noiseTableSlab === noise.single() &&
                            requireNotNull(stage.composedProof).authenticatesComposedNoise(resource, noise.single()))
                        ComposedBindingLayoutV1.StorageKind.RUNTIME_READ -> require(requireNotNull(stage.composedProof).runtimeResources
                            .single { it.resource === resource }.let(stage.composedProof::authenticatesRuntimeResource))
                        null -> if (resource.texture != null) {
                            val proof=requireNotNull(stage.composedProof)
                            val image = proof.composedImageResources.singleOrNull { it.resource === resource }
                            require(if(image != null) proof.authenticatesComposedImage(resource, image.upload)
                                else proof.runtimeResources.single { it.resource === resource }.let(proof::authenticatesRuntimeResource))
                        } else if(resource.sampler != null) {
                            val proof=requireNotNull(stage.composedProof)
                            require(proof.runtimeResources.single { it.resource === resource }.let(proof::authenticatesRuntimeResource))
                        }
                    }
                }
                val destination = packet.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead
                val bounds = destination?.let { frame.w6aLayerFrameV1?.destinationCopy(packet)?.copySourceBoundsI32()?.let {
                    org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(it.left, it.top, it.right, it.bottom)
                } ?: frame.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
                    .single { copy -> copy.consumers.any { it.packetId == packet.packetId } }.logicalBounds }
                val module = composeW5aHostSourceV1(template, source, destination, bounds)
                val layout = GPUW5aHostBindGroupLayoutV1.of(stage.bindingManifest.map { binding ->
                    val entry = when (binding.resourceKind) {
                        "uniformBuffer" -> GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.Uniform, false, stage.uniformByteCountI64)
                        "storageBuffer" -> GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.ReadOnlyStorage, false,
                            binding.composedResource?.buffer?.minBindingSizeBytesI64 ?: 32L)
                        "sampledTexture" -> GPUW5aHostBindingLayoutV1.Texture(GPUTextureSampleType.Float, GPUTextureViewDimension.TwoD, false)
                        "sampler" -> GPUW5aHostBindingLayoutV1.Sampler(if(requireNotNull(binding.composedResource?.sampler).samplerTypeTagU32 == 1u)
                            GPUSamplerBindingType.Filtering else GPUSamplerBindingType.NonFiltering)
                        else -> error("Unsupported source binding manifest")
                    }
                    GPUW5aHostBindGroupEntryV1(binding.bindingI32, 2u, entry)
                })
                val binding = frame.w5hSourceAuthorityRootV1.coreBinding(packet)
                packet.packetId.value to Packet(source, template, module, layout, stage.structuralId, expectations,
                    binding?.geometryAuthority, binding?.materialBindingAuthority)
            }
            W5hFrameSourcePreflightResultV1.Validated(W5hFrameSourceValidationWitnessV1(frame.w5hSourceAuthorityRootV1, validated))
        } catch (failure: IllegalArgumentException) {
            refused(failure, currentPacket)
        } catch (failure: IllegalStateException) {
            refused(failure, currentPacket)
        }
        }
        private fun refused(failure: Exception, packet: GPUDrawPacket?) = W5hFrameSourcePreflightResultV1.Refused(listOf(RenderDiagnostic(
            RenderDiagnosticCode("unsupported.material.runtime_effect.renderer_preflight"), RenderDiagnosticDomain.EXECUTION,
            RenderDiagnosticSeverity.ERROR, buildString {
                packet?.let {
                    append("drawIndex=${it.originalPaintOrder}, commandId=${it.commandIdValue}, packetId=${it.packetId.value}, ")
                    append("material=${it.materialSourcePartitionV3()?.stage?.structuralId}: ")
                }
                append(failure.message ?: "Invalid frame source authority")
            })))
    }
}

internal fun preflightW5hFrameSourcesV1(framePlan: GPUFramePlan): W5hFrameSourcePreflightResultV1 =
    W5hFrameSourceValidationWitnessV1.preflight(framePlan)
