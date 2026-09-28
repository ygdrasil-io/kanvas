package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1
import org.graphiks.kanvas.gpu.plan.ComposedMaterialProgramV6
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.SolidRectDraw
import org.graphiks.kanvas.gpu.plan.W5aSourceNativeSiteRecipeV1
import org.graphiks.kanvas.gpu.plan.W5aSourceNativeBindingKindV1
import org.graphiks.kanvas.gpu.plan.W5aSourceNativeVariantV1
import org.graphiks.kanvas.gpu.plan.W6SolidRectNativeSiteRecipeV1
import org.graphiks.kanvas.gpu.plan.w5aOrdinarySourceNativeVariantV1OrNull
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.recording.hostTargetV1
import org.graphiks.kanvas.gpu.renderer.recording.w6aColorTarget
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
        val nativeRecipe: W5aSourceNativeSiteRecipeV1?,
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
            require(packets.map { it.packetId.value }.distinct().size == packets.size) {
                "W5h source packet IDs must be unique"
            }
            require(frame.w5aGeometryHostTemplatesV1.map { it.packetId }.toSet() == packets.map { it.packetId.value }.toSet()) {
                "Every material packet must have its sealed geometry host template"
            }
            val stages = packets.map { requireNotNull(it.materialSourcePartitionV3()).stage }
            val stops = stages.mapNotNull { it.gradientStopSlab }.distinct()
            val noise = stages.mapNotNull { it.noiseTableSlab }.distinct()
            require(stops.size <= 1 && noise.size <= 1)
            GPUW5eImageNativeV1.validate(frame)
            fun ordinaryRecipe(packet: GPUDrawPacket, source: W5aPacketMaterialSourceV2): W5aSourceNativeSiteRecipeV1? {
                val render = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { packet in it.drawPackets }
                val pass = render.w6aPassV1 as? PlanPass.RenderPass ?: return null
                val ordinal = render.drawPackets.indexOf(packet)
                val draw = pass.draws().getOrNull(ordinal) as? SolidRectDraw ?: return null
                val authority = draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1 ?: return null
                val w6 = requireNotNull(frame.w6aLayerFrameV1)
                val variant = w5aOrdinarySourceNativeVariantV1OrNull(w6.graph.materialPlanTableOrNull(), pass, draw) ?: return null
                val physical = w6.physical
                val table = requireNotNull(w6.graph.materialPlanTableOrNull())
                val recipe = physical.w5aOrdinarySolidSourceRecipes().entries.singleOrNull { (owner, _) ->
                    owner.ownerPassId == pass.id && owner.drawOrPacketOrdinalI32 == ordinal
                }?.value ?: error("Missing frozen W5a ordinary source recipe for ${pass.id.value}/$ordinal.")
                val geometry = physical.nativeSiteRecipeCatalogV1().recipe(recipe.geometryOwner)
                    as? W6SolidRectNativeSiteRecipeV1
                    ?: error("W5a ordinary source geometry owner is not a sealed SolidRect site: ${recipe.geometryOwner}.")
                require(geometry.canonicalLogicalEncodingV1 == recipe.geometryCanonicalEncodingV1 &&
                    geometry.host.target.format == recipe.targetFormat &&
                    geometry.host.target.sampleCountI32 == recipe.targetSampleCountI32) {
                    "W5a ordinary source recipe does not authenticate its geometry target/site: ${pass.id.value}/$ordinal"
                }
                require(recipe.commandIndexI32 == packet.commandIdValue && recipe.material == authority.ref &&
                    recipe.materialProgramStructuralId == table.entry(authority.ref).program.structuralId.value &&
                    recipe.variant == variant && recipe.materialStructuralId == source.stage.structuralId &&
                    recipe.materialCanonicalIdentity == source.stage.canonicalIdentity &&
                    recipe.uniformByteCountI64 == source.stage.uniformByteCountI64 &&
                    recipe.copyUniformBytes().contentEquals(source.stage.uniformBytes) &&
                    recipe.materialCoordinateCanonicalIdentity == (if (variant == W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1)
                        authority.coordinates?.canonicalIdentity.orEmpty() else "") &&
                    recipe.bindingManifest().map { it.bindingI32 to it.kind } == source.stage.bindingManifest.map { binding ->
                        binding.bindingI32 to when (binding.resourceKind) {
                            "uniformBuffer" -> W5aSourceNativeBindingKindV1.UniformBuffer
                            "storageBuffer" -> W5aSourceNativeBindingKindV1.StorageBuffer
                            else -> error("W5a ordinary source has a non-admitted binding ${binding.resourceKind}")
                        }
                    }) {
                    "W5a ordinary source packet does not match its sealed logical recipe: pass=${pass.id.value}, ordinal=$ordinal, command=${packet.commandIdValue}"
                }
                if (variant == W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1) {
                    val slab = requireNotNull(source.stage.gradientStopSlab)
                    val resource = physical.resource(requireNotNull(recipe.gradientStopResource))
                    require(recipe.gradientStopByteCountI64 == slab.byteSizeI64 &&
                        recipe.gradientStopCanonicalIdentity == slab.canonicalIdentity &&
                        requireNotNull(table.gradientStopSlab).canonicalIdentity == slab.canonicalIdentity &&
                        resource.byteSize == slab.byteSizeI64 &&
                        resource.role == org.graphiks.kanvas.gpu.plan.PlanResourceRole.GradientStopData &&
                        resource.usages() == setOf(org.graphiks.kanvas.gpu.plan.PlanResourceUsage.StorageRead,
                            org.graphiks.kanvas.gpu.plan.PlanResourceUsage.CopyDestination)) {
                        "W5a ordinary linear-gradient packet lost its sealed stop storage resource"
                    }
                }
                return recipe
            }
            val validated = packets.associate { packet ->
                currentPacket = packet
                val source = requireNotNull(packet.materialSourcePartitionV3())
                val stage = source.stage
                val template = requireNotNull(frame.w5hSourceAuthorityRootV1.template(packet))
                val nativeRecipe = ordinaryRecipe(packet, source)
                nativeRecipe?.let { recipe ->
                    require(template.target.format == GPUTextureFormat.RGBA8UnormSrgb &&
                        template.target == w6aColorTarget(recipe.blend).hostTargetV1()) {
                        "W5a ordinary source recipe does not select the sealed native target/blend pipeline: ${recipe.ownerPassId.value}/${recipe.drawOrdinalI32}"
                    }
                }
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
                val module = when (nativeRecipe?.variant) {
                    org.graphiks.kanvas.gpu.plan.W5aSourceNativeVariantV1.OrdinarySolidMaterialV1 -> {
                        require(destination == null) { "W5a ordinary source recipe must not compose a destination read" }
                        composeW5aHostSourceV1(template, source, destination, bounds)
                    }
                    org.graphiks.kanvas.gpu.plan.W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1 -> {
                        require(destination == null) { "W5a ordinary source recipe must not compose a destination read" }
                        composeW5aHostSourceV1(template, source, destination, bounds)
                    }
                    null -> composeW5aHostSourceV1(template, source, destination, bounds)
                }
                val layout = nativeRecipe?.let { recipe ->
                    GPUW5aHostBindGroupLayoutV1.of(recipe.bindingManifest().map { binding ->
                        GPUW5aHostBindGroupEntryV1(binding.bindingI32, 2u, when (binding.kind) {
                            W5aSourceNativeBindingKindV1.UniformBuffer ->
                                GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.Uniform, false, recipe.uniformByteCountI64)
                            W5aSourceNativeBindingKindV1.StorageBuffer ->
                                GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.ReadOnlyStorage, false, recipe.gradientStopByteCountI64)
                        })
                    })
                } ?: GPUW5aHostBindGroupLayoutV1.of(stage.bindingManifest.map { binding ->
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
                packet.packetId.value to Packet(source, template, module, layout, stage.structuralId, nativeRecipe, expectations,
                    binding?.geometryAuthority, binding?.materialBindingAuthority)
            }
            frame.w6aLayerFrameV1?.physical?.w5aOrdinarySolidSourceRecipes()?.let { recipes ->
                val witnessedRecipes = validated.values.mapNotNull { it.nativeRecipe }
                val witnessed = witnessedRecipes
                    .associateBy { it.owner }
                require(witnessed.size == witnessedRecipes.size && witnessed.keys == recipes.keys && witnessed.all { (owner, recipe) ->
                    recipe.canonicalLogicalEncodingV1() == recipes.getValue(owner).canonicalLogicalEncodingV1()
                }) {
                    "Every frozen W5a ordinary source recipe must have exactly one matching W5h packet before native allocation"
                }
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
