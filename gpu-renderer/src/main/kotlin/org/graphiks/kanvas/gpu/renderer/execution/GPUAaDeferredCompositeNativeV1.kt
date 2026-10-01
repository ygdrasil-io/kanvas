package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

internal fun GPUW6aLayerFramePlan.aaDeferredRecipe(pass: PlanPass.AaDeferredComposite): W6AaDeferredCompositeRecipeV1 =
    (physical.nativeSiteRecipeCatalogV1().recipe(NativeSiteOwnerV1(pass.id, 0, 0)) as W6AaDeferredCompositeRecipeV1)
        .also { require(it.pass === pass && it.composite === pass.contract) }

internal fun GPUW6aLayerFramePlan.aaDeferredUses(pass: PlanPass.AaDeferredComposite): List<GPUFrameResourceUse> {
    val recipe = aaDeferredRecipe(pass)
    return buildList {
        add(GPUFrameResourceUse(refs.getValue(recipe.coverageResource), GPUFrameResourceRole.FilterTarget,
            GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false))
        add(GPUFrameResourceUse(refs.getValue(recipe.uniformResource), GPUFrameResourceRole.UniformData,
            GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false))
        recipe.composite.destinationSnapshot?.let { add(GPUFrameResourceUse(refs.getValue(it),
            GPUFrameResourceRole.DestinationSnapshot, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)) }
    }
}

/** Pure preflight: authenticate the final site/table/row and lower the existing W5 numeric DAG. */
internal class GPUAaDeferredCompositeNativeV1 private constructor(
    val recipe: W6AaDeferredCompositeRecipeV1,
    private val stage: W5aMaterialSourceStage,
    private val shader: String,
) {
    companion object {
        fun preflight(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan): Map<PlanPassId, GPUAaDeferredCompositeNativeV1> {
            val passes = frame.graph.passes().filterIsInstance<PlanPass.AaDeferredComposite>()
            val recipes = passes.map(frame::aaDeferredRecipe)
            val expected = freezeW6AaDeferredCompositeRecipesV1(frame.graph.passes(), frame.graph.materialPlanTableOrNull(),
                frame.graph.resources(), recipes.associate { it.raw.canonicalIdentity to it.uniformResource })
            require(recipes.map { it.canonicalLogicalEncodingV1 } == expected.map { it.canonicalLogicalEncodingV1 })
            return recipes.associate { recipe ->
                val composite = recipe.composite
                val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1 === recipe.pass }
                require(render.target == frame.refs.getValue(composite.target) && render.resourceUses == frame.aaDeferredUses(recipe.pass) &&
                    render.drawPackets.isEmpty() && render.samplePlan.sampleCount == 1 && render.depthStencilLoadStore == null &&
                    render.loadStore.loadOp == "load" && render.loadStore.storePlan == GPUStorePlan.Store)
                val authority = composite.sourceDraw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1
                val material = requireNotNull(W5aMaterialSourceStage.lower(requireNotNull(frame.graph.materialPlanTableOrNull()), recipe.material, authority.coordinates))
                require(material.structuralId == recipe.raw.structuralId && material.canonicalIdentity == recipe.raw.canonicalIdentity &&
                    material.uniformBytes.contentEquals(recipe.raw.copyUniformBytes()) && material.uniformByteCountI64 == recipe.raw.uniformByteCountI64 &&
                    material.bindingManifest == listOf(W5aMaterialSourceStage.Binding(0, "uniformBuffer")) &&
                    !material.consumesDevicePositionF32 && material.compositionDomain == org.graphiks.kanvas.render.ir.CompositionDomain.LINEAR)
                val destination = composite.blend as? BlendPlan.DestinationReadV1
                val formula = destination?.let { requireNotNull(GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
                    it.mode.name.lowercase(), it.formulaIdentity, "w7_full_blend")) }.orEmpty()
                val result = if (destination == null) "return coverage * source;" else when (destination.coverageLaw) {
                    BlendCoverageLawV1.SourcePreScale -> "return w7_full_blend(coverage * source, destination);"
                    BlendCoverageLawV1.DestinationInterpolation -> "return destination + coverage * (w7_full_blend(source, destination) - destination);"
                }
                val shader = W6A_VERTEX_SHADER + material.declarationsWgsl.replace("@group(1) @binding(0)", "@group(0) @binding(1)") + """
                    @group(0) @binding(0) var w7_coverage: texture_2d<f32>;
                    ${if (destination != null) "@group(0) @binding(2) var w7_destination: texture_2d<f32>;" else ""}
                    $formula
                    @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                        let coverage_sample: vec4<f32> = textureLoad(w7_coverage, vec2<i32>(position.xy), 0);
                        let coverage: f32 = coverage_sample.a;
                        let source: vec4<f32> = kanvas_material_source(vec2<f32>(0.0));
                        ${if (destination != null) "let destination: vec4<f32> = textureLoad(w7_destination, vec2<i32>(position.xy), 0);" else ""}
                        $result
                    }
                """
                require(org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl("w7-aa-deferred", shader) is
                    org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation.Validated)
                recipe.pass.id to GPUAaDeferredCompositeNativeV1(recipe, material, shader)
            }
        }
    }

    fun materialize(device: GPUDevice, queue: GPUQueue, views: Map<PlanResourceId, GPUTextureView>,
        uniform: GPUBuffer, generation: GPUDeviceGenerationID, stepIndex: Int, owned: W6aOwnedHandles): GPUPreparedNativeScopeOperand.Render {
        val composite = recipe.composite
        queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(stage.uniformBytes))
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, buffer = BufferBindingLayout(
                type = GPUBufferBindingType.Uniform, minBindingSize = stage.uniformByteCountI64.toULong())))
            if (composite.destinationSnapshot != null) add(BindGroupLayoutEntry(2u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
        })))
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code = shader)))
        val pipelineLayout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(layout))))
        val pipeline = owned.own(device.createRenderPipeline(RenderPipelineDescriptor(layout = pipelineLayout,
            vertex = VertexState(module, entryPoint = "vs_main"),
            fragment = FragmentState(module = module, targets = listOf(w6aColorTarget(composite.blend)), entryPoint = "fs_main"),
            primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList))))
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, views.getValue(recipe.coverageResource)))
            add(BindGroupEntry(1u, BufferBinding(uniform, 0uL, stage.uniformByteCountI64.toULong())))
            composite.destinationSnapshot?.let { add(BindGroupEntry(2u, views.getValue(it))) }
        })))
        val scissor = composite.copySourceBoundsTargetI32()
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(composite.target), generation)),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0))),
            operationKindOverride = GPUEncoderOperationKind.LayerComposite, w6aPassV1 = recipe.pass)
    }
}

internal fun validatesAaDeferredNativeV1(frame: GPUW6aLayerFramePlan, pass: PlanPass.AaDeferredComposite,
    native: GPUPreparedNativeScopeOperand.Render): Boolean {
    val recipe = frame.aaDeferredRecipe(pass)
    val scissor = recipe.composite.copySourceBoundsTargetI32()
    return native.w6aPassV1 === pass && native.semanticPayloads.isEmpty() && native.pass.resolveTarget == null &&
        native.pass.depthStencilTarget == null && native.pass.loadOperation == GPUPreparedNativeLoadOperation.Load &&
        native.pass.storeOperation == GPUPreparedNativeStoreOperation.Store && native.commands.size == 4 &&
        native.commands[0] is GPUPreparedNativeRenderCommand.SetPipeline &&
        (native.commands[1] as? GPUPreparedNativeRenderCommand.SetBindGroup)?.index == 0 &&
        native.commands[2] == GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()) &&
        native.commands[3] == GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0))
}
