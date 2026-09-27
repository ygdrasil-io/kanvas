@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.materials.W5fColorOperationEmitterV1
import org.graphiks.kanvas.gpu.renderer.materials.W5aMaterialSourceStage
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6cMorphologyPass
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6cMultiInputPass
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6cSpatialSamplingPass
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6dAdvancedSamplingPass
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6dDistantDiffusePass
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6dLightingPass
import org.graphiks.kanvas.gpu.renderer.filters.GPUW6dPictureSamplingPass
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.wgsl.W6bMaskCoverageSnippet
import org.graphiks.kanvas.gpu.renderer.wgsl.W6bSeparableBlurSnippet
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.mapRectBoundsF64OrNull

/** Native handles for one binding in an already-published W5 source manifest. */
private sealed interface GPUW6bMaskShaderResourceV1 {
    class Buffer(val value: GPUBuffer, val byteSizeI64: Long) : GPUW6bMaskShaderResourceV1
    class Image(val lease: GPUW5eDecodedImageSessionCache.Lease) : GPUW6bMaskShaderResourceV1
    class Runtime(val lease: GPUW5hRuntimeResourceSessionCache.Lease) : GPUW6bMaskShaderResourceV1
}

private data class W4eClipMaskInitializeNativePreflight(
    val entries: List<GPUW4eNativePassEntry>,
    val recipesByPassId: Map<String, W4eClipMaskInitializeRecipeV1>,
)

/** Authenticates the final W4e binding and its recorded initialize packets before any device.create*. */
private fun preflightW4eClipMaskInitializes(
    frame: GPUW6aLayerFramePlan,
    framePlan: GPUFramePlan,
): Map<PlanW4eGeometryBindingV1, W4eClipMaskInitializeNativePreflight> =
    frame.w4eAuthorities.map { (binding, authority) ->
        val entries = framePlan.steps.mapIndexedNotNull { index, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@mapIndexedNotNull null
            if (render.w6aPassV1?.id !in binding.graphPassIds()) return@mapIndexedNotNull null
            val bound = binding.nativePass(requireNotNull(render.w6aPassV1).id)
            require(bound != null)
            if (bound is PlanPass.ClipMaskInitialize) require(bound === render.w6aPassV1)
            GPUW4eNativePassEntry(index, render, render.drawPackets.single())
        }
        require(entries.isNotEmpty() && entries.first().packet.w4ePreparedFrameAuthority?.validatesRenderSteps(
            framePlan.frameId.value, framePlan.capabilitySeal.sealHash, entries.map { it.render },
        ) == true)
        val recipes = entries.mapNotNull { entry ->
            frame.w4eClipMaskInitializeRecipeOrNull(entry.packet)?.let { recipe ->
                require(recipe.passId.value == entry.packet.passId)
                recipe.passId.value to recipe
            }
        }.toMap()
        val initializes = binding.nativePasses().filterIsInstance<PlanPass.ClipMaskInitialize>()
        require(recipes.keys == initializes.map { it.id.value }.toSet()) {
            "W4e ClipMaskInitialize recording recipes must cover exactly one final binding."
        }
        initializes.forEach { initialize ->
            val recipe = recipes.getValue(initialize.id.value)
            require(recipe.passId == initialize.id && recipe.output == initialize.output &&
                recipe.copyDomainI32() == initialize.copyDomainI32() &&
                recipe.clearCoverageF32 == initialize.clearCoverageF32)
        }
        requireW4eClipMaskInitializeRecipes(entries, recipes)
        binding to W4eClipMaskInitializeNativePreflight(entries, recipes)
    }.toMap()

/** Exhaustively authenticates W6b recipes, packet order, meshes and V/I/U windows before any device.create*. */
private fun preflightW6bCoverageRasters(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = frame.graph.passes().filterIsInstance<PlanPass.FilterCoverageSourcePass>().mapNotNull { pass ->
        pass.rasterBinding?.takeUnless { it.draw is SolidRectDraw }?.let { pass }
    }
    require(frame.physical.w6bCoverageRasterHostRecipes().keys == expected.map { it.id }.toSet())
    expected.forEach { pass ->
        val binding = requireNotNull(pass.rasterBinding)
        val data = requireNotNull(binding.drawDataResources)
        require(frame.physical.resource(pass.output).format ==
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)) {
            "W6b coverage preflight requires its frozen sRGB color attachment."
        }
        val geometry = frame.physical.w6bCoverageRasterGeometry(pass.id)
        val host = frame.physical.w6bCoverageRasterHostRecipe(pass.id)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1 === pass }
        val depthUses = render.resourceUses.filter {
            it.role == org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.PathDepthStencil
        }
        recipeDepthAttachmentPreflight(host, depthUses, render, frame)
        require(render.drawPackets.size == geometry.bundles().size && render.drawPackets.size == host.bundles().size)
        render.drawPackets.forEachIndexed { ordinal, packet ->
            val bundle = geometry.bundle(ordinal); val recipe = host.bundle(ordinal)
            val catalogRecipe = frame.physical.nativeSiteRecipeCatalogV1().recipe(
                NativeSiteOwnerV1(pass.id, 0, ordinal),
            ) as? W6bCoverageRasterNativeSiteRecipeV1
                ?: error("W6b raster bundle is absent from the frozen native-site catalog.")
            val vertexRow = frame.physical.resource(bundle.vertexWindow.resourceId)
            val indexRow = frame.physical.resource(bundle.indexWindow.resourceId)
            val uniformRow = frame.physical.resource(bundle.uniformWindow.resourceId)
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: error("W6b coverage packet must retain CorePrimitive semantics.")
            val expectedRole = when (recipe.role) {
                W6bCoverageRasterRoleV1.Shading -> GPUDrawPacketRole.Shading
                W6bCoverageRasterRoleV1.PathStencilProducer -> GPUDrawPacketRole.PathStencilProducer
                W6bCoverageRasterRoleV1.PathStencilCover -> GPUDrawPacketRole.PathStencilCover
            }
            val expectedScissor = recipe.copyScissorI32().let {
                GPUPixelBounds(it.left, it.top, it.right, it.bottom)
            }
            val expectedClip = if (recipe.clip == W6bCoverageRasterClipV1.None)
                GPUClipExecutionPlan.NoClip else GPUClipExecutionPlan.ScissorOnly(expectedScissor)
            val semanticMesh = semantic.geometry as? org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.TriangulatedPath
            val triangulatedCoverage = recipe.family in setOf(
                W6bCoverageRasterFamilyV1.Point,
                W6bCoverageRasterFamilyV1.PathFill,
                W6bCoverageRasterFamilyV1.PathStroke,
            ) && recipe.role != W6bCoverageRasterRoleV1.PathStencilCover
            if (triangulatedCoverage) {
                requireNotNull(semanticMesh) { "W6b frozen direct or stencil-producer coverage requires triangulated packet geometry." }
                require(semanticMesh.vertices == bundle.mesh.copyPositionsF32().toList() &&
                    requireNotNull(semanticMesh.indices) == requireNotNull(bundle.mesh.copyIndicesI32()).toList()) {
                    "W6b frozen coverage mesh differs from its recorded CorePrimitive packet."
                }
            } else {
                require(recipe.role == W6bCoverageRasterRoleV1.PathStencilCover ||
                    (recipe.family in setOf(W6bCoverageRasterFamilyV1.AnalyticRect, W6bCoverageRasterFamilyV1.AnalyticRRect) &&
                        recipe.uniformAbi == W6bCoverageRasterUniformAbiV1.AnalyticShape80)) {
                    "W6b non-triangulated coverage must retain its frozen analytic or stencil-cover authority."
                }
                require(recipe.role != W6bCoverageRasterRoleV1.PathStencilCover ||
                    (recipe.topology == W6bCoverageRasterTopologyV1.DirectTriangleList &&
                    bundle.vertexCountI32 == 4 && bundle.indexCountI32 == 6)) {
                    "W6b stencil cover must retain its planner-sealed scissor quad."
                }
            }
            require(catalogRecipe.host === recipe && recipe.ownerPassId == pass.id &&
                recipe.siteOrdinalI32 == 0 && recipe.bundleOrdinalI32 == ordinal) { "W6b catalog owner mismatch" }
            require(recipe.output == pass.output && recipe.depthStencil == binding.depthStencil &&
                render.target == frame.refs.getValue(recipe.output)) { "W6b frozen attachment reference mismatch." }
            require(
                packet.role == expectedRole && semantic.scissorBounds == expectedScissor &&
                packet.clipExecutionPlan == expectedClip &&
                frame.coverageRasterPipeline(packet) != null &&
                recipe.geometry === bundle && bundle.vertexWindow.resourceId == data.vertex &&
                bundle.indexWindow.resourceId == data.index && bundle.uniformWindow.resourceId == data.uniform &&
                vertexRow.byteSize == bundle.vertexWindow.capacityBytesI64 &&
                indexRow.byteSize == bundle.indexWindow.capacityBytesI64 &&
                uniformRow.byteSize == bundle.uniformWindow.capacityBytesI64 &&
                w6bCoverageUniformBytes(recipe).contentEquals(frame.analyticUniform(packet)) &&
                recipe.copyScissorI32().width() > 0 && recipe.copyScissorI32().height() > 0 &&
                bundle.vertexWindow.usefulBytesI64 == bundle.vertexCountI32.toLong() * 8L &&
                bundle.indexWindow.usefulBytesI64 == bundle.indexCountI32.toLong() * 4L &&
                Math.addExact(bundle.vertexWindow.offsetBytesI64, bundle.vertexWindow.usefulBytesI64) <= vertexRow.byteSize &&
                Math.addExact(bundle.indexWindow.offsetBytesI64, bundle.indexWindow.usefulBytesI64) <= indexRow.byteSize &&
                Math.addExact(bundle.uniformWindow.offsetBytesI64, bundle.uniformWindow.usefulBytesI64) <= uniformRow.byteSize)
        }
    }
}

/** Checks all and only the planned Empty programs before the first native allocation. */
private fun preflightW6FullscreenEmpties(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FullscreenEmptyRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FullscreenEmptyRecipes().keys == expected.keys)
    expected.forEach { (passId, frozen) ->
        val actual = frame.physical.w6FullscreenEmptyRecipe(passId)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1() && actual.inputs().isEmpty()) {
            "W6 Empty native preflight differs from its frozen planner recipe."
        }
        val row = frame.physical.resource(actual.target)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single {
            it.w6aPassV1?.id == passId
        }
        val pass = requireNotNull(render.w6aPassV1)
        val expectedRecordedSource = when (actual.phase) {
            W6FullscreenEmptyPhaseV1.PictureCompositeNoScissor -> (pass as? PlanPass.PictureComposite)?.source
            W6FullscreenEmptyPhaseV1.FilterCompositeNoOp,
            W6FullscreenEmptyPhaseV1.FilterCompositeNoScissor,
            -> (pass as? PlanPass.FilterComposite)?.source
            else -> null
        }
        val expectedUses = expectedRecordedSource?.let { source -> listOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                frame.refs.getValue(source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false,
            ))
        }.orEmpty()
        require(render.target == frame.refs.getValue(actual.target) && render.resourceUses == expectedUses && render.drawPackets.isEmpty() &&
            row.copyExtent() == actual.copyExtent() && row.format == PlanTextureFormat.Color(actual.targetFormat) &&
            row.sampleCountI32 == actual.sampleCountI32 && render.samplePlan is GPUSamplePlan.SingleSampleFrame &&
            render.loadStore.loadOp == (if (actual.load == AttachmentLoadPlan.ClearTransparent) "clear" else "load") &&
            render.loadStore.storePlan == GPUStorePlan.Store) { "W6 Empty physical render step differs from its frozen recipe." }
    }
}

/** Checks the Ib1 recorded step separately from its later texture binding before any allocation. */
private fun preflightW6FullscreenCoverageAlphas(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FullscreenCoverageAlphaRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FullscreenCoverageAlphaRecipes().keys == expected.keys)
    expected.forEach { (passId, frozen) ->
        val actual = frame.physical.w6FullscreenCoverageAlphaRecipe(passId)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1()) {
            "W6 CoverageAlpha native preflight differs from its frozen planner recipe."
        }
        val pass = frame.graph.passes().single { it.id == passId } as? PlanPass.FilterCoverageSourcePass
            ?: error("W6 CoverageAlpha owner is not a coverage source pass.")
        val alpha = requireNotNull(pass.sealedAlphaSource)
        val sampling = requireNotNull(pass.sealedAlphaSampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == passId }
        val expectedUses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
            frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false,
        ))
        val target = frame.physical.resource(actual.target)
        val source = frame.physical.resource(actual.source)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == alpha.sealedSourceId &&
            actual.sourceGenerationI64 == alpha.sealedSourceGenerationI64 &&
            actual.copySourceSampleBoundsTargetI32() == alpha.copySampleBoundsTargetI32() &&
            actual.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) &&
            target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.target) && render.resourceUses == expectedUses && render.drawPackets.isEmpty() &&
            render.loadStore.loadOp == "load" && render.loadStore.storePlan == GPUStorePlan.Store &&
            render.samplePlan is GPUSamplePlan.SingleSampleFrame) {
            "W6 CoverageAlpha recorded resource use or attachment differs from its frozen recipe."
        }
    }
}

/** Authenticates all Ib2 Clear/empty-group SolidRect sites before the first device.create*. */
private fun preflightW6FullscreenCoverageSolidRects(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FullscreenCoverageSolidRectRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FullscreenCoverageSolidRectRecipes().keys == expected.keys)
    expected.forEach { (passId, frozen) ->
        val actual = frame.physical.w6FullscreenCoverageSolidRectRecipe(passId)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == passId } as? PlanPass.FilterCoverageSourcePass
            ?: error("W6 CoverageSolidRect owner is not a coverage source pass.")
        val binding = requireNotNull(pass.rasterBinding)
        val target = frame.physical.resource(actual.target)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == passId }
        require(binding.draw is SolidRectDraw && pass.sealedAlphaSource == null && binding.depthStencil == null &&
            actual.ownerPassId == pass.id && actual.target == pass.output && actual.copyScissorTargetLocalI32() ==
                org.graphiks.math.geometry.RectI32(0, 0, actual.copyExtent().width, actual.copyExtent().height) &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) &&
            target.sampleCountI32 == actual.sampleCountI32 && render.target == frame.refs.getValue(actual.target) &&
            render.resourceUses.isEmpty() && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" &&
            render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame) {
            "W6 CoverageSolidRect recorded render step differs from its frozen recipe."
        }
    }
}

private fun preflightW6FullscreenCoverageRetains(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FullscreenCoverageRetainRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FullscreenCoverageRetainRecipes().keys == expected.keys)
    expected.forEach { (passId, frozen) ->
        val actual = frame.physical.w6FullscreenCoverageRetainRecipe(passId)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == passId } as? PlanPass.FilterCoverageRetainPass
            ?: error("W6 CoverageRetain owner is not a retain pass.")
        val sampling = requireNotNull(pass.sampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == passId }
        val expectedUses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
            frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.source &&
            actual.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() &&
            actual.copySourceKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) &&
            target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.target) && render.resourceUses == expectedUses && render.drawPackets.isEmpty() &&
            render.loadStore.loadOp == "load" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame)
    }
}
private fun preflightW6FullscreenPictureSourceLayers(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FullscreenPictureSourceLayerRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FullscreenPictureSourceLayerRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FullscreenPictureSourceLayerRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.PictureSourcePass
            ?: error("W6 PictureSourceLayer owner is not a picture source pass.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val expectedUses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
            frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false,
        ))
        val catalogRecipe = frame.physical.nativeSiteRecipeCatalogV1().recipe(actual.nativeSiteOwnerV1())
            as? W6FullscreenPictureSourceLayerNativeSiteRecipeV1
            ?: error("W6 PictureSourceLayer is absent from the frozen native-site catalog.")
        val target = frame.physical.resource(actual.target)
        val source = frame.physical.resource(actual.source)
        require(catalogRecipe.host === actual && frame.physical.slot(actual.target).resourceId == actual.target &&
            frame.physical.slot(actual.source).resourceId == actual.source && pass.graphTextureOperand == null &&
            pass.layerInput == actual.source && requireNotNull(pass.sourceSampling).copyOutputToInputOffsetTargetLocalI32() ==
                actual.copyOutputToInputOffsetTargetLocalI32() && actual.ownerPassId == pass.id && actual.target == pass.output &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) &&
            target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() &&
            source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) &&
            source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.target) && render.resourceUses == expectedUses &&
            render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" &&
            render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame &&
            render.depthStencilLoadStore == null) {
            "W6 PictureSourceLayer recorded resource use or attachment differs from its frozen recipe."
        }
    }
}

/** Authenticates Ic2 graph-texture PictureSource sites before the first device.create*. */
private fun preflightW6FullscreenPictureSourceGraphs(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FullscreenPictureSourceGraphRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FullscreenPictureSourceGraphRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FullscreenPictureSourceGraphRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.PictureSourcePass
            ?: error("W6 PictureSourceGraph owner is not a picture source pass.")
        val operand = requireNotNull(pass.graphTextureOperand)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val expectedUses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
            frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false,
        ))
        val catalogRecipe = frame.physical.nativeSiteRecipeCatalogV1().recipe(actual.nativeSiteOwnerV1())
            as? W6FullscreenPictureSourceGraphNativeSiteRecipeV1
            ?: error("W6 PictureSourceGraph is absent from the frozen native-site catalog.")
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source)
        require(catalogRecipe.host === actual && frame.physical.slot(actual.target).resourceId == actual.target &&
            frame.physical.slot(actual.source).resourceId == actual.source && actual.ownerPassId == pass.id &&
            actual.target == pass.output && actual.source == operand.sealedSourceId &&
            actual.copyOutputToInputOffsetTargetLocalI32() == requireNotNull(pass.sourceSampling).copyOutputToInputOffsetTargetLocalI32() &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) &&
            target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() &&
            source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) &&
            source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.target) && render.resourceUses == expectedUses && render.drawPackets.isEmpty() &&
            render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store &&
            render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) {
            "W6 PictureSourceGraph recorded resource use or attachment differs from its frozen recipe."
        }
    }
}

/** IIa1 authenticates Crop's exact physical and recorded texture use before device.create*. */
private fun preflightW6FilterSpatialCrops(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterSpatialCropRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterSpatialCropRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterSpatialCropRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 Crop owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.Crop ?: error("W6 Crop operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
            frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.inputs().single() &&
            actual.tileMode == operation.tileMode && actual.copySourceDomainTargetLocalI32() == operation.sampling.copySourceInputTargetLocalI32() &&
            actual.copyClipTargetLocalF64() == operation.sampling.copyClipOutputTargetLocalF64() &&
            actual.copyOutputToInputOffsetTargetLocalF64() == operation.sampling.copyOutputToInputOffsetTargetLocalF64() &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() &&
            source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() &&
            render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IIa2a authenticates the frozen DECAL Offset recipe before any native allocation. */
private fun preflightW6FilterSpatialOffsets(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterSpatialOffsetRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterSpatialOffsetRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterSpatialOffsetRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 Offset owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.Offset ?: error("W6 Offset operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.inputs().single() && actual.copySourceDomainTargetLocalI32() == operation.sampling.copySourceInputTargetLocalI32() && actual.copyClipTargetLocalF64() == operation.sampling.copyClipOutputTargetLocalF64() && actual.copyOutputToInputOffsetTargetLocalF64() == operation.sampling.copyOutputToInputOffsetTargetLocalF64() && target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IIa2b authenticates the frozen periodic Tile recipe and recorded pass before device.create*. */
private fun preflightW6FilterSpatialTiles(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterSpatialTileRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterSpatialTileRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterSpatialTileRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 Tile owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.Tile ?: error("W6 Tile operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.inputs().single() && actual.copySourceDomainTargetLocalI32() == operation.copySourceInputTargetLocalI32() && actual.copyClipTargetLocalF64() == operation.sampling.copyClipOutputTargetLocalF64() && actual.copyOutputToInputOffsetTargetLocalF64() == operation.sampling.copyOutputToInputOffsetTargetLocalF64() && target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IIb authenticates Morphology's complete separable selection before any device.create*. */
private fun preflightW6FilterMorphologies(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterMorphologyRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterMorphologyRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterMorphologyRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 Morphology owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.Morphology ?: error("W6 Morphology operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.inputs().single() &&
            actual.morphologyKind == operation.morphologyKind && actual.axis == operation.axis && actual.radiusXF64 == operation.radiusXF64 && actual.radiusYF64 == operation.radiusYF64 && actual.radiusXTexelsI32 == operation.radiusXTexelsI32 && actual.radiusYTexelsI32 == operation.radiusYTexelsI32 &&
            actual.copySourceKnownContentTargetLocalI32() == operation.sampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToInputOffsetTargetLocalI32() == operation.sampling.copyOutputToInputOffsetTargetLocalI32() &&
            target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() &&
            source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IIc authenticates ColorFilter's W5f uniform ABI and recorded texture pass before create*. */
private fun preflightW6FilterColorFilters(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterColorFilterRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterColorFilterRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterColorFilterRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 ColorFilter owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.ColorFilter ?: error("W6 ColorFilter operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val uses = listOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.uniformResource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.UniformData, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.Uniform, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
        )
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source); val uniform = frame.physical.resource(actual.uniformResource)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.inputs().single() && actual.execution === operation.execution && actual.uniformResource == operation.uniformResource && actual.uniformOffsetBytesI64 == operation.uniformOffsetBytesI64 && actual.uniformCapacityBytesI64 == operation.uniformCapacityBytesI64 && actual.copyOutputToInputOffsetTargetLocalI32() == operation.sampling.copyOutputToInputOffsetTargetLocalI32() && target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && source.copyExtent() == actual.copySourceExtent() && source.format == PlanTextureFormat.Color(actual.sourceFormat) && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.Sampled in source.usages() && uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer && uniform.byteSize == actual.uniformCapacityBytesI64 && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination) && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IId1 authenticates every ordered Merge source/sampling row before device.create*. */
private fun preflightW6FilterMerges(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterMergeRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterMergeRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterMergeRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 Merge owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.Merge ?: error("W6 Merge operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val uses = actual.inputs().map { input -> org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(input.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false) }
        val target = frame.physical.resource(actual.target)
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.inputs().map { it.source } == pass.inputs() && actual.inputs().size == operation.inputSamplings().size && actual.inputs().zip(operation.inputSamplings()).all { (input, sampling) -> input.copyKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() && input.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() } && target.copyExtent() == actual.copyExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && target.sampleCountI32 == actual.sampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && actual.inputs().all { input -> val source = frame.physical.resource(input.source); source.copyExtent() == input.copyExtent() && source.format == PlanTextureFormat.Color(input.format) && source.sampleCountI32 == input.sampleCountI32 && PlanResourceUsage.Sampled in source.usages() } && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IId2 authenticates Blend's ordered two inputs and its recorded fullscreen pass before device.create*. */
private fun preflightW6FilterBlends(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterBlendRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterBlendRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterBlendRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1() &&
            actual.blendFormulaWgsl == frozen.blendFormulaWgsl &&
            actual.blendFormulaWgsl == frozenW6FilterBlendFormulaWgslV1(actual.blend, actual.formula))
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass
            ?: error("W6 Blend owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.Blend
            ?: error("W6 Blend operation changed after seal.")
        val inputs = listOf(actual.background(), actual.foreground())
        val samplings = listOf(operation.backgroundSampling(), operation.foregroundSampling())
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single {
            it.w6aPassV1?.id == id
        }
        val uses = inputs.map { input ->
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                frame.refs.getValue(input.source),
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal,
                false,
            )
        }
        val target = frame.physical.resource(actual.target)
        require(
            actual.ownerPassId == pass.id && actual.target == pass.output &&
                inputs.map { it.source } == pass.inputs() && inputs.size == 2 &&
                actual.blend == operation.blend &&
                inputs.zip(samplings).all { (input, sampling) ->
                    input.copyKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() &&
                        input.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32()
                } &&
                actual.groupZeroAbi == W6FilterBlendGroupZeroAbiV1.BackgroundAndForegroundTextures &&
                actual.shaderFamily == W6FilterBlendShaderFamilyV1.FrozenW5BlendFormulaTextureLoad &&
                actual.load == AttachmentLoadPlan.ClearTransparent && actual.store == AttachmentStorePlan.Store &&
                target.copyExtent() == actual.copyExtent() &&
                target.format == PlanTextureFormat.Color(actual.targetFormat) &&
                target.sampleCountI32 == actual.sampleCountI32 &&
                PlanResourceUsage.RenderAttachment in target.usages() &&
                inputs.all { input ->
                    val source = frame.physical.resource(input.source)
                    source.copyExtent() == input.copyExtent() &&
                        source.format == PlanTextureFormat.Color(input.format) &&
                        source.sampleCountI32 == input.sampleCountI32 &&
                        PlanResourceUsage.Sampled in source.usages()
                } &&
                render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses &&
                render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" &&
                render.loadStore.storePlan == GPUStorePlan.Store &&
                render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null,
        ) { "W6 Blend physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIe1 authenticates each frozen horizontal/vertical blur pass before device.create*. */
private fun preflightW6FilterSeparableBlurs(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterSeparableBlurRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterSeparableBlurRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterSeparableBlurRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 SeparableBlur owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.SeparableBlur ?: error("W6 SeparableBlur operation changed after seal.")
        val sampling = requireNotNull(operation.sampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val source = frame.physical.resource(actual.source); val target = frame.physical.resource(actual.target)
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.source == pass.inputs().single() && actual.kind == operation.kind && actual.axis == operation.axis && actual.sigmaF32.toRawBits() == operation.sigmaF32.toRawBits() && actual.tileMode == operation.tileMode && actual.groupZeroAbi == W6FilterSeparableBlurGroupZeroAbiV1.SourceTexture && actual.copyKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() && target.copyExtent() == actual.copyExtent() && source.copyExtent() == actual.copySourceExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && source.format == PlanTextureFormat.Color(actual.sourceFormat) && target.sampleCountI32 == actual.sampleCountI32 && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null)
    }
}

/** IIe2a authenticates the NORMAL one-texture style before any native allocation. */
private fun preflightW6FilterMaskBlurNormals(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterMaskBlurNormalRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterMaskBlurNormalRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterMaskBlurNormalRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 MaskBlur NORMAL owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.MaskBlurStyle ?: error("W6 MaskBlur NORMAL operation changed after seal.")
        val sampling = requireNotNull(operation.blurredSampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val source = frame.physical.resource(actual.blurredSource); val target = frame.physical.resource(actual.target)
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.blurredSource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.blurredSource == operation.blurredCoverageSource && actual.style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL && operation.style == actual.style && operation.originalCoverageSource == null && operation.originalSampling == null && pass.inputs() == listOf(actual.blurredSource) && actual.groupZeroAbi == W6FilterMaskBlurNormalGroupZeroAbiV1.BlurredCoverageTexture && actual.shaderFamily == W6FilterMaskBlurNormalShaderFamilyV1.BlurredCoverageTextureLoad && actual.copyKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToBlurredOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() && target.copyExtent() == actual.copyExtent() && source.copyExtent() == actual.copyBlurredExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && source.format == PlanTextureFormat.Color(actual.blurredFormat) && target.sampleCountI32 == actual.sampleCountI32 && source.sampleCountI32 == actual.blurredSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 MaskBlur NORMAL physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIe2b authenticates the ordered blurred/original pair before any native allocation. */
private fun preflightW6FilterMaskBlurDualSources(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterMaskBlurDualSourceRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterMaskBlurDualSourceRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterMaskBlurDualSourceRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 dual MaskBlur owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.MaskBlurStyle ?: error("W6 dual MaskBlur operation changed after seal.")
        val blurredSampling = requireNotNull(operation.blurredSampling); val originalSampling = requireNotNull(operation.originalSampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val blurred = frame.physical.resource(actual.blurredSource); val original = frame.physical.resource(actual.originalSource); val target = frame.physical.resource(actual.target)
        val uses = listOf(actual.blurredSource, actual.originalSource).map { org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(it), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false) }
        require(actual.ownerPassId == pass.id && actual.target == pass.output && operation.style == actual.style && actual.style in setOf(org.graphiks.kanvas.render.ir.MaskBlurStyle.SOLID, org.graphiks.kanvas.render.ir.MaskBlurStyle.OUTER, org.graphiks.kanvas.render.ir.MaskBlurStyle.INNER) && operation.blurredCoverageSource == actual.blurredSource && operation.originalCoverageSource == actual.originalSource && pass.inputs() == listOf(actual.blurredSource, actual.originalSource) && actual.groupZeroAbi == W6FilterMaskBlurDualSourceGroupZeroAbiV1.BlurredThenOriginalCoverageTextures && actual.shaderFamily == W6FilterMaskBlurDualSourceShaderFamilyV1.BlurredThenOriginalCoverageTextureLoad && actual.copyBlurredKnownContentTargetLocalI32() == blurredSampling.copyKnownContentInputTargetLocalI32() && actual.copyOriginalKnownContentTargetLocalI32() == originalSampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToBlurredOffsetTargetLocalI32() == blurredSampling.copyOutputToInputOffsetTargetLocalI32() && actual.copyOutputToOriginalOffsetTargetLocalI32() == originalSampling.copyOutputToInputOffsetTargetLocalI32() && target.copyExtent() == actual.copyExtent() && blurred.copyExtent() == actual.copyBlurredExtent() && original.copyExtent() == actual.copyOriginalExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && blurred.format == PlanTextureFormat.Color(actual.blurredFormat) && original.format == PlanTextureFormat.Color(actual.originalFormat) && target.sampleCountI32 == actual.sampleCountI32 && blurred.sampleCountI32 == actual.blurredSampleCountI32 && original.sampleCountI32 == actual.originalSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in blurred.usages() && PlanResourceUsage.Sampled in original.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 dual MaskBlur physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIf1 authenticates the frozen coverage/W5-material group ABI before any device.create*. */
private fun preflightW6FilterMaskShaders(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expectedIds = frame.graph.passes().filterIsInstance<PlanPass.FilterPass>().filter {
        it.operation is FilterPassOperationV1.MaskShader
    }.map { it.id }.toSet()
    require(frame.physical.w6FilterMaskShaderRecipes().keys == expectedIds)
    expectedIds.forEach { id ->
        val actual = frame.physical.w6FilterMaskShaderRecipe(id)
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 MaskShader owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.MaskShader ?: error("W6 MaskShader operation changed after seal.")
        val binding = operation.materialBinding as? FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned
            ?: error("W6 MaskShader lost its sealed W5 material binding.")
        val sampling = requireNotNull(operation.sampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val coverage = frame.physical.resource(actual.coverageSource); val target = frame.physical.resource(actual.target)
        val uniform = frame.physical.resource(actual.uniformResource)
        val material = frame.maskShaderMaterial(binding)
        val stageManifest = material.stage.bindingManifest.map { stageBinding ->
            W6FilterMaskShaderBindingAbiV1(stageBinding.bindingI32, when (stageBinding.resourceKind) {
                "uniformBuffer" -> W6FilterMaskShaderBindingKindV1.UniformBuffer
                "storageBuffer" -> W6FilterMaskShaderBindingKindV1.StorageBuffer
                "sampledTexture" -> W6FilterMaskShaderBindingKindV1.SampledTexture
                "sampler" -> W6FilterMaskShaderBindingKindV1.Sampler
                else -> error("W6 MaskShader stage has an unsupported binding kind.")
            })
        }
        require(actual.materialStructuralId == material.stage.structuralId) { "W6 MaskShader structural id differs from its frozen recipe." }
        require(actual.materialCanonicalIdentity == material.stage.canonicalIdentity) { "W6 MaskShader canonical identity differs from its frozen recipe." }
        require(actual.materialUniformByteCountI64 == material.stage.uniformByteCountI64) { "W6 MaskShader uniform byte count differs from its frozen recipe." }
        require(actual.bindingManifest() == stageManifest) { "W6 MaskShader binding manifest differs from its frozen recipe: recipe=${actual.bindingManifest()} stage=$stageManifest" }
        val uses = listOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.coverageSource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.uniformResource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.UniformData, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.Uniform, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
        )
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.coverageSource == pass.inputs().single() && actual.occurrenceIdI32 == binding.occurrenceIdI32 && actual.material == binding.material && actual.uniformResource == binding.uniformResource && actual.uniformOffsetBytesI64 == binding.uniformOffsetBytesI64 && actual.uniformCapacityBytesI64 == binding.uniformCapacityBytesI64 && actual.materialStructuralId == material.stage.structuralId && actual.materialCanonicalIdentity == material.stage.canonicalIdentity && actual.materialUniformByteCountI64 == material.stage.uniformByteCountI64 && actual.bindingManifest() == stageManifest && actual.copyMaterialDeviceOriginI32() == binding.materialDeviceOriginI32 && binding.materialAuthority.materialPlanRef() == actual.material && pass.inputs() == listOf(actual.coverageSource) && actual.groupZeroAbi == W6FilterMaskShaderGroupZeroAbiV1.CoverageThenFrozenW5Material && actual.shaderFamily == W6FilterMaskShaderFamilyV1.FrozenW5MaterialCoverageAlpha && actual.load == AttachmentLoadPlan.ClearTransparent && actual.store == AttachmentStorePlan.Store && actual.blend == BlendPlan.LegacySrcOverV1 && actual.draw == W6FullscreenEmptyDrawV1() && actual.copyKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToCoverageOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() && target.copyExtent() == actual.copyExtent() && coverage.copyExtent() == actual.copyCoverageExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && coverage.format == PlanTextureFormat.Color(actual.coverageFormat) && target.sampleCountI32 == actual.sampleCountI32 && coverage.sampleCountI32 == actual.coverageSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in coverage.usages() && uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer && uniform.byteSize == actual.uniformCapacityBytesI64 && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination) && material.binding == binding && material.stage.uniformBytes.size.toLong() == material.stage.uniformByteCountI64 && Math.addExact(actual.uniformOffsetBytesI64, material.stage.uniformByteCountI64) <= actual.uniformCapacityBytesI64 && Math.addExact(actual.uniformOffsetBytesI64, material.stage.uniformByteCountI64) <= uniform.byteSize && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 MaskShader physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIf2 authenticates both recorded bindings, including the exact immutable LUT storage window, before device.create*. */
private fun preflightW6FilterMaskTables(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterMaskTableRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterMaskTableRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterMaskTableRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("W6 MaskTable owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.MaskTable ?: error("W6 MaskTable operation changed after seal.")
        val sampling = requireNotNull(operation.sampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val target = frame.physical.resource(actual.target); val coverage = frame.physical.resource(actual.coverageSource); val table = frame.physical.resource(actual.tableResource)
        val uses = listOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.coverageSource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.tableResource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.StorageData, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.Storage, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
        )
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.coverageSource == pass.inputs().single() && actual.tableResource == operation.tableResourceId && actual.copyTable().copyToUByteArray().contentEquals(operation.copyTable().copyToUByteArray()) && actual.tableGenerationI64 == operation.generationI64 && actual.tableOwnerMaskOccurrenceI32 == operation.ownerMaskOccurrenceI32 && actual.tableOffsetBytesI64 == 0L && actual.tableRangeBytesI64 == 256L && actual.groupZeroAbi == W6FilterMaskTableGroupZeroAbiV1.CoverageTextureThenTableStorage && actual.shaderFamily == W6FilterMaskTableShaderFamilyV1.CoverageLookupStorageU32 && actual.load == AttachmentLoadPlan.ClearTransparent && actual.store == AttachmentStorePlan.Store && actual.blend == BlendPlan.LegacySrcOverV1 && actual.draw == W6FullscreenEmptyDrawV1() && actual.copyKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToCoverageOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32() && target.copyExtent() == actual.copyExtent() && coverage.copyExtent() == actual.copyCoverageExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && coverage.format == PlanTextureFormat.Color(actual.coverageFormat) && target.sampleCountI32 == actual.sampleCountI32 && coverage.sampleCountI32 == actual.coverageSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in coverage.usages() && table.role == PlanResourceRole.MaskTableData && table.kind == PlanResourceKind.Buffer && table.byteSize == actual.tableRangeBytesI64 && table.usages() == setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination) && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 MaskTable physical or recorded preflight differs from its frozen recipe." }
    }
}

private fun preflightW6FilterMaterializedSources(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterMaterializedSourceRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterMaterializedSourceRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterMaterializedSourceRecipe(id); require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("MaterializedSource owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.MaterializedSource ?: error("MaterializedSource operation changed after seal.")
        val sourceSampling = requireNotNull(operation.sourceSampling); val coverageSampling = requireNotNull(operation.coverageSampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val target = frame.physical.resource(actual.target); val source = frame.physical.resource(actual.source); val coverage = frame.physical.resource(actual.coverage)
        val uses = listOf(actual.source, actual.coverage).map { org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(it), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false) }
        require(actual.ownerPassId == pass.id && actual.target == pass.output && pass.inputs() == listOf(actual.source, actual.coverage) && actual.groupZeroAbi == W6FilterMaterializedSourceGroupZeroAbiV1.SourceThenCoverageTextures && actual.shaderFamily == W6FilterMaterializedSourceShaderFamilyV1.SourceAndCoverageTextureLoad && actual.load == AttachmentLoadPlan.ClearTransparent && actual.store == AttachmentStorePlan.Store && actual.blend == BlendPlan.LegacySrcOverV1 && actual.draw == W6FullscreenEmptyDrawV1() && actual.copySourceKnownContentTargetLocalI32() == sourceSampling.copyKnownContentInputTargetLocalI32() && actual.copyCoverageKnownContentTargetLocalI32() == coverageSampling.copyKnownContentInputTargetLocalI32() && actual.copyOutputToSourceOffsetTargetLocalI32() == sourceSampling.copyOutputToInputOffsetTargetLocalI32() && actual.copyOutputToCoverageOffsetTargetLocalI32() == coverageSampling.copyOutputToInputOffsetTargetLocalI32() && actual.copyTargetOriginDeviceI32() == operation.bounds.copyTargetOriginDeviceI32() && target.copyExtent() == actual.copyExtent() && source.copyExtent() == actual.copySourceExtent() && coverage.copyExtent() == actual.copyCoverageExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && source.format == PlanTextureFormat.Color(actual.sourceFormat) && coverage.format == PlanTextureFormat.Color(actual.coverageFormat) && target.sampleCountI32 == actual.sampleCountI32 && source.sampleCountI32 == actual.sourceSampleCountI32 && coverage.sampleCountI32 == actual.coverageSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.Sampled in coverage.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 MaterializedSource physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIg1 checks the sealed color, F64 offset and linear DECAL geometry before native allocation. */
private fun preflightW6FilterDropShadowColorizes(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterDropShadowColorizeRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterDropShadowColorizeRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterDropShadowColorizeRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass
            ?: error("DropShadowColorize owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.DropShadowColorize
            ?: error("DropShadowColorize operation changed after seal.")
        val sampling = requireNotNull(operation.linearSampling)
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val target = frame.physical.resource(actual.target); val blurred = frame.physical.resource(actual.blurredSource)
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.blurredSource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.blurredSource == pass.inputs().single() && actual.colorArgbU32 == operation.color.value && actual.copyOffsetF64() == operation.copyOffsetF64() && actual.copySourceCoordinateOffsetTargetLocalF64() == sampling.copySourceCoordinateOffsetTargetLocalF64() && actual.copySourceFootprintTargetLocalI32() == sampling.copySourceFootprintTargetLocalI32() && actual.copyOutputFootprintTargetLocalI32() == sampling.copyOutputFootprintTargetLocalI32() && actual.copyScissorTargetLocalI32() == sampling.copyOutputFootprintTargetLocalI32() && actual.groupZeroAbi == W6FilterDropShadowColorizeGroupZeroAbiV1.BlurredAlphaTexture && actual.shaderFamily == W6FilterDropShadowColorizeShaderFamilyV1.LinearDecalBlurredAlphaColorize && actual.load == AttachmentLoadPlan.ClearTransparent && actual.store == AttachmentStorePlan.Store && actual.blend == BlendPlan.LegacySrcOverV1 && actual.draw == W6FullscreenEmptyDrawV1() && target.copyExtent() == actual.copyExtent() && blurred.copyExtent() == actual.copyBlurredExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && blurred.format == PlanTextureFormat.Color(actual.blurredFormat) && target.sampleCountI32 == actual.sampleCountI32 && blurred.sampleCountI32 == actual.blurredSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in blurred.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 DropShadowColorize physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIg2 authenticates the ordered shadow/original pair before any native allocation. */
private fun preflightW6FilterDropShadowComposites(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilterDropShadowCompositeRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilterDropShadowCompositeRecipes().keys == expected.keys)
    expected.forEach { (id, frozen) ->
        val actual = frame.physical.w6FilterDropShadowCompositeRecipe(id)
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1())
        val pass = frame.graph.passes().single { it.id == id } as? PlanPass.FilterPass ?: error("DropShadowComposite owner is not FilterPass.")
        val operation = pass.operation as? FilterPassOperationV1.DropShadowComposite ?: error("DropShadowComposite operation changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == id }
        val target = frame.physical.resource(actual.target); val shadow = frame.physical.resource(actual.colorizedShadow); val original = frame.physical.resource(actual.originalSource)
        val uses = listOf(actual.colorizedShadow, actual.originalSource).map { org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(it), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false) }
        require(actual.ownerPassId == pass.id && actual.target == pass.output && actual.mode == CapturedDropShadowModeV1.COMPOSITE && operation.mode == actual.mode && operation.originalInput == actual.originalSource && pass.inputs() == listOf(actual.colorizedShadow, actual.originalSource) && actual.copyShadowOffsetTargetLocalI32() == operation.copyShadowSampleOffsetTargetLocalI32() && actual.copyOriginalOffsetTargetLocalI32() == operation.copyOriginalSampleOffsetTargetLocalI32() && actual.copyShadowFootprintTargetLocalI32().width() == actual.copyShadowExtent().width && actual.copyShadowFootprintTargetLocalI32().height() == actual.copyShadowExtent().height && actual.copyOriginalFootprintTargetLocalI32().width() == actual.copyOriginalExtent().width && actual.copyOriginalFootprintTargetLocalI32().height() == actual.copyOriginalExtent().height && actual.copyScissorTargetLocalI32().width() == actual.copyExtent().width && actual.copyScissorTargetLocalI32().height() == actual.copyExtent().height && actual.groupZeroAbi == W6FilterDropShadowCompositeGroupZeroAbiV1.ColorizedShadowThenOriginalTextures && actual.shaderFamily == W6FilterDropShadowCompositeShaderFamilyV1.ShadowThenOriginalSrcOverTextureLoad && actual.load == AttachmentLoadPlan.ClearTransparent && actual.store == AttachmentStorePlan.Store && actual.blend == BlendPlan.LegacySrcOverV1 && actual.draw == W6FullscreenEmptyDrawV1() && target.copyExtent() == actual.copyExtent() && shadow.copyExtent() == actual.copyShadowExtent() && original.copyExtent() == actual.copyOriginalExtent() && target.format == PlanTextureFormat.Color(actual.targetFormat) && shadow.format == PlanTextureFormat.Color(actual.shadowFormat) && original.format == PlanTextureFormat.Color(actual.originalFormat) && target.sampleCountI32 == actual.sampleCountI32 && shadow.sampleCountI32 == actual.shadowSampleCountI32 && original.sampleCountI32 == actual.originalSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in shadow.usages() && PlanResourceUsage.Sampled in original.usages() && render.target == frame.refs.getValue(actual.target) && render.resourceUses == uses && render.drawPackets.isEmpty() && render.loadStore.loadOp == "clear" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.depthStencilLoadStore == null) { "W6 DropShadowComposite physical or recorded preflight differs from its frozen recipe." }
    }
}

/** IIIa1 seals the only filtered, non-destination-read LayerComposite before any device allocation. */
private fun preflightW6FilteredLayerComposites(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6FilteredLayerCompositeRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6FilteredLayerCompositeRecipes().keys == expected.keys)
    expected.forEach { (site, frozen) ->
        val actual = requireNotNull(frame.physical.w6FilteredLayerCompositeRecipeOrNull(site))
        val pass = frame.graph.passes().single { it.id == site.ownerPassId } as? PlanPass.LayerComposite
            ?: error("Filtered layer-composite owner is not LayerComposite.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == pass.id }
        val target = frame.physical.resource(actual.destination); val source = frame.physical.resource(actual.source); val uniform = frame.physical.resource(actual.uniformResource)
        val uses = listOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.LayerTarget, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.uniformResource), org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.UniformData, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.Uniform, org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false),
        )
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1() && actual.source == pass.source && actual.destination == pass.destination && actual.blend !is BlendPlan.DestinationReadV1 && actual.copyScissorParentI32().width() == actual.copySourceBoundsLayerI32().width() && actual.copyScissorParentI32().height() == actual.copySourceBoundsLayerI32().height() && target.copyExtent() == actual.copyTargetExtentI32() && source.copyExtent() == actual.copySourceExtentI32() && target.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) && source.format == PlanTextureFormat.Color(actual.sourceFormat) && target.sampleCountI32 == actual.target.sampleCountI32 && source.sampleCountI32 == actual.sourceSampleCountI32 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && uniform.kind == PlanResourceKind.Buffer && uniform.role == PlanResourceRole.UniformData && uniform.byteSize == actual.uniformCapacityBytesI64 && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination) && Math.addExact(actual.uniformOffsetBytesI64, maxOf(16L, actual.execution.dynamicByteCountI64)) <= uniform.byteSize && render.target == frame.refs.getValue(actual.destination) && render.resourceUses == uses && render.loadStore.loadOp == "load" && render.loadStore.storePlan == GPUStorePlan.Store && render.samplePlan is GPUSamplePlan.SingleSampleFrame && render.drawPackets.isEmpty() && render.depthStencilLoadStore == null) {
            "W6 filtered layer-composite physical or recorded preflight differs from its frozen recipe."
        }
    }
}

/** IIIb1 validates every direct active PictureComposite before the first device allocation. */
private fun preflightW6PictureComposites(frame: GPUW6aLayerFramePlan, framePlan: GPUFramePlan) {
    val expected = freezeW6PictureCompositeRecipesV1(frame.graph.passes(), frame.graph.resources())
    require(frame.physical.w6PictureCompositeRecipes().keys == expected.keys)
    expected.forEach { (passId, frozen) ->
        val actual = frame.physical.w6PictureCompositeRecipe(passId)
        val pass = frame.graph.passes().single { it.id == passId } as? PlanPass.PictureComposite
            ?: error("PictureComposite recipe owner changed after seal.")
        val render = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { it.w6aPassV1?.id == passId }
        val target = frame.physical.resource(actual.destination); val source = frame.physical.resource(actual.source)
        val uses = listOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(frame.refs.getValue(actual.source),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.FilterTarget,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        require(actual.canonicalLogicalEncodingV1() == frozen.canonicalLogicalEncodingV1() &&
            actual.source == pass.source && actual.destination == pass.destination &&
            target.copyExtent() == actual.copyTargetExtentI32() && source.copyExtent() == actual.copySourceExtentI32() &&
            target.format == PlanTextureFormat.Color(actual.targetFormat) && source.format == PlanTextureFormat.Color(actual.sourceFormat) &&
            target.sampleCountI32 == actual.targetSampleCountI32 && source.sampleCountI32 == actual.sourceSampleCountI32 &&
            PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() &&
            render.target == frame.refs.getValue(actual.destination) && render.resourceUses == uses &&
            render.loadStore.loadOp == "load" && render.loadStore.storePlan == GPUStorePlan.Store &&
            render.drawPackets.isEmpty() && render.depthStencilLoadStore == null) {
            "W6 PictureComposite physical or recorded preflight differs from its frozen recipe."
        }
    }
}

private fun recipeDepthAttachmentPreflight(
    host: W6bCoverageRasterHostRecipeV1,
    depthUses: List<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse>,
    render: GPUFrameStep.RenderPassStep,
    frame: GPUW6aLayerFramePlan,
) {
    val depth = host.bundles().map { it.depthStencil }.distinct()
    require(depth.size == 1) { "W6b bundles must retain one shared explicit depth attachment presence." }
    val depthId = depth.single()
    if (depthId == null) {
        require(depthUses.isEmpty() && render.depthStencilLoadStore == null) {
            "Direct W6b coverage must not record a depth attachment."
        }
    } else {
        val expected = frame.refs.getValue(depthId)
        require(depthUses.size == 1 && depthUses.single().resource == expected &&
            depthUses.single().usage == org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.RenderAttachment &&
            depthUses.single().write && render.depthStencilLoadStore != null) {
            "W6b stencil coverage depth attachment differs from its frozen recipe."
        }
    }
}

/** Native translation of exact W6 resources and passes behind one ordinary frame draft. */
internal class GPUWgpu4kW6aLayerFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val rootTarget: GPUWgpu4kPreparedSceneTarget,
    private val decodedImageCache: GPUW5eDecodedImageSessionCache? = null,
    private val runtimeResourceCache: GPUW5hRuntimeResourceSessionCache? = null,
    private val spatialFilterCache: GPUW6cSpatialFilterSessionCache? = null,
) : GPUPreparedNativeFramePayloadMaterializer {
    private var consumed = false

    override fun bindLateSurface(draft: GPUPreparedNativeFrameDraft, acquiredSurface: GPUAcquiredSurfaceOutput?): GPUPreparedNativeFrameLateSurfaceBinding =
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired

    override fun materializeReusable(framePlan: GPUFramePlan, sourceWitness: W5hFrameSourceValidationWitnessV1,
        encoderPlan: GPUCommandEncoderPlan, resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal): GPUPreparedNativeFramePayloadMaterialization {
        val frame = framePlan.w6aLayerFrameV1
        if (consumed || frame == null || !frame.validates(framePlan) || !sourceWitness.authenticates(framePlan))
            return GPUPreparedNativeFramePayloadMaterialization.Refused("w6a.layer.invalid_plan", "Missing or consumed W6 frame authority")
        consumed = true
        val owned = W6aOwnedHandles()
        var readbackBuffer: GPUBuffer? = null
        var spatialBinding: GPUW6cSpatialFilterSessionCache.Binding? = null
        try {
            val graph = frame.graph
            preflightW6FullscreenEmpties(frame, framePlan)
            preflightW6FullscreenCoverageAlphas(frame, framePlan)
            preflightW6FullscreenCoverageSolidRects(frame, framePlan)
            preflightW6FullscreenCoverageRetains(frame, framePlan)
            preflightW6FullscreenPictureSourceLayers(frame, framePlan)
            preflightW6FullscreenPictureSourceGraphs(frame, framePlan)
            preflightW6FilterSpatialCrops(frame, framePlan)
            preflightW6FilterSpatialOffsets(frame, framePlan)
            preflightW6FilterSpatialTiles(frame, framePlan)
            preflightW6FilterMorphologies(frame, framePlan)
            preflightW6FilterColorFilters(frame, framePlan)
            preflightW6FilterMerges(frame, framePlan)
            preflightW6FilterBlends(frame, framePlan)
            preflightW6FilterSeparableBlurs(frame, framePlan)
            preflightW6FilterMaskBlurNormals(frame, framePlan)
            preflightW6FilterMaskBlurDualSources(frame, framePlan)
            preflightW6FilterMaskShaders(frame, framePlan)
            preflightW6FilterMaskTables(frame, framePlan)
            preflightW6FilterMaterializedSources(frame, framePlan)
            preflightW6FilterDropShadowColorizes(frame, framePlan)
            preflightW6FilterDropShadowComposites(frame, framePlan)
            preflightW6FilteredLayerComposites(frame, framePlan)
            preflightW6PictureComposites(frame, framePlan)
            preflightW6bCoverageRasters(frame, framePlan)
            val w4eClipMaskInitializePreflights = preflightW4eClipMaskInitializes(frame, framePlan)
            // Consume only the exact program leases that were frozen and budgeted before this
            // native boundary.  A warm driver cache may avoid creation work, never this lease.
            val frozenPrograms = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                pass.frozenSamplingProgram?.let { pass.id to it }
            }
            require(frame.physical.programSlots().map { it.lease.ownerPassId }.toSet() ==
                frozenPrograms.map { it.first }.toSet()) { "W6d native program lease set differs from the frozen graph." }
            frozenPrograms.forEach { (ownerPassId, binding) ->
                require(frame.physical.programSlot(ownerPassId).lease.matches(
                    binding, generationSeal.deviceGeneration.value, graph.passes().size,
                )) { "W6d native program materialization lacks its pre-publication logical lease." }
            }
            spatialBinding = if (frame.physical.spatialCachePlans().isEmpty()) null else
                requireNotNull(spatialFilterCache?.consume(framePlan)) { "W6c cache binding was not selected by preflight." }
            val generation = generationSeal.deviceGeneration
            val root = frame.physical.resource(graph.resources().single { it.role == PlanResourceRole.LogicalTarget }.id)
            require(rootTarget.width == root.copyExtent()?.width && rootTarget.height == root.copyExtent()?.height &&
                rootTarget.deviceGeneration == generation && rootTarget.targetGeneration == generationSeal.targetGeneration)
            val (rootTexture, rootView) = rootTarget.borrow()
            val views = linkedMapOf(root.id to rootView)
            val textures = linkedMapOf(root.id to rootTexture)
            graph.resources().filter { it.kind == PlanResourceKind.Texture2D && it.lifetime == PlanResourceLifetime.FrameLocal && it.id != root.id }.forEach { resource ->
                if (spatialBinding?.usesCachedTarget(resource.id) == true) {
                    views[resource.id] = spatialBinding.view(resource.id)
                    textures[resource.id] = spatialBinding.texture(resource.id)
                    return@forEach
                }
                val slot = frame.physical.slot(resource.id)
                val extent = requireNotNull(resource.copyExtent())
                val format = when (resource.format) {
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) -> GPUTextureFormat.RGBA8UnormSrgb
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) -> GPUTextureFormat.Depth24PlusStencil8
                    PlanTextureFormat.CoverageMask -> GPUTextureFormat.RGBA8Unorm
                    else -> error("Unadmitted W6 texture format")
                }
                val usage = resource.usages().fold(GPUTextureUsage.None) { result, value -> result or when (value) {
                    PlanResourceUsage.RenderAttachment, PlanResourceUsage.DepthStencilAttachment -> GPUTextureUsage.RenderAttachment
                    PlanResourceUsage.Sampled -> GPUTextureUsage.TextureBinding
                    PlanResourceUsage.CopySource -> GPUTextureUsage.CopySrc
                    PlanResourceUsage.CopyDestination -> GPUTextureUsage.CopyDst
                    else -> error("Unadmitted layer usage")
                } }
                val texture = owned.own(device.createTexture(TextureDescriptor(size = Extent3D(extent.width.toUInt(), extent.height.toUInt()),
                    format = format, usage = usage, sampleCount = resource.sampleCountI32.toUInt(), label = "w6a.slot.${slot.slotI32}")))
                views[resource.id] = owned.own(texture.createView())
                textures[resource.id] = texture
            }
            val drawData = graph.passes().mapNotNull { frame.physical.geometryBinding(it.id)?.data } +
                frame.physical.w4eGeometryBindings().map { PlanDrawDataResources(it.payload.vertexResourceId, it.payload.indexResourceId, it.payload.uniformResourceId) }
            val drawUniformIds = drawData.map { it.uniform }.toSet()
            val geometryUniform = frame.physical.resource(graph.resources().single {
                it.role == PlanResourceRole.UniformData && it.id !in drawUniformIds }.id)
            val geometryBuffers = drawData.flatMap { listOf(it.vertex, it.index, it.uniform) }.distinct().associateWith { id ->
                val row = frame.physical.resource(id)
                val usage = when (row.role) {
                    PlanResourceRole.VertexData -> GPUBufferUsage.Vertex
                    PlanResourceRole.IndexData -> GPUBufferUsage.Index
                    PlanResourceRole.UniformData -> GPUBufferUsage.Uniform
                    else -> error("Invalid W4 data resource")
                }
                owned.own(device.createBuffer(BufferDescriptor(size = row.byteSize.toULong(),
                    usage = usage or GPUBufferUsage.CopyDst, label = "w6a.slot.${frame.physical.slot(id).slotI32}")))
            }
            // All consumers group by the compiler-issued physical ID before native allocation.
            // In particular, an identical W5 row shared by MaskShader and a graph-texture
            // parent gets one buffer/cache lease, never an allocate-then-map overwrite.
            val maskShaderMaterials = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                (pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding
                    as? FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned
            }.distinctBy { it.occurrenceIdI32 }.associateWith(frame::maskShaderMaterial)
            val graphTextureUniformIds = graph.passes().filterIsInstance<PlanPass.PictureSourcePass>()
                .mapNotNull { it.graphTextureOperand?.uniformResource }.distinct()
            val maskMaterialsByUniform = maskShaderMaterials.values.groupBy { it.binding.uniformResource }
            val colorFiltersByUniform = graph.passes().filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
                (pass.operation as? FilterPassOperationV1.ColorFilter)?.let {
                    val recipe = frame.physical.nativeSiteRecipeCatalogV1().recipe(NativeSiteOwnerV1(pass.id, 0, 0))
                        as? W6FilterColorFilterNativeSiteRecipeV1
                        ?: error("ColorFilter source-uniform staging lacks its frozen native-site recipe.")
                    require(recipe.host === frame.physical.w6FilterColorFilterRecipe(pass.id))
                    recipe.host.uniformResource to recipe.host
                }
            }.groupBy({ it.first }, { it.second })
            val sourceUniformBuffers = (graphTextureUniformIds + maskMaterialsByUniform.keys + colorFiltersByUniform.keys).distinct().associateWith { id ->
                val resource = frame.physical.resource(id)
                require(resource.role == PlanResourceRole.SourceUniformData && resource.kind == PlanResourceKind.Buffer &&
                    resource.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination))
                val materials = maskMaterialsByUniform[id].orEmpty()
                val canonicalBytes = materials.firstOrNull()?.stage?.uniformBytes ?: colorFiltersByUniform[id]?.firstOrNull()?.let { operation ->
                    val offset = requireNotNull(operation.uniformOffsetBytesI64)
                    ByteArray(Math.toIntExact(resource.byteSize)).also { bytes ->
                        operation.execution.copyDynamicBytes().copyInto(bytes, Math.toIntExact(offset))
                    }
                }
                materials.forEach { material ->
                    require(material.binding.uniformOffsetBytesI64 == 0L &&
                        material.binding.uniformCapacityBytesI64 == resource.byteSize &&
                        Math.addExact(material.binding.uniformOffsetBytesI64, material.stage.uniformByteCountI64) <= resource.byteSize &&
                        canonicalBytes!!.contentEquals(material.stage.uniformBytes))
                }
                colorFiltersByUniform[id].orEmpty().forEach { operation ->
                    val offset = requireNotNull(operation.uniformOffsetBytesI64)
                    val capacity = requireNotNull(operation.uniformCapacityBytesI64)
                    require(capacity == resource.byteSize &&
                        Math.addExact(offset, maxOf(16L, operation.execution.dynamicByteCountI64)) <= capacity &&
                        canonicalBytes!!.copyOfRange(Math.toIntExact(offset), Math.toIntExact(Math.addExact(offset,
                            operation.execution.dynamicByteCountI64))).contentEquals(operation.execution.copyDynamicBytes()))
                }
                owned.own(device.createBuffer(BufferDescriptor(size = resource.byteSize.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "w6b.source.uniform.${frame.physical.slot(id).slotI32}"))).also { buffer ->
                    canonicalBytes?.let { bytes -> queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(bytes), 0uL, bytes.size.toULong()) }
                }
            }
            val graphTextureUniformBuffers = graphTextureUniformIds.associateWith(sourceUniformBuffers::getValue)
            val colorFilterUniformBuffers = colorFiltersByUniform.keys.associateWith(sourceUniformBuffers::getValue)
            val storageBuffers = linkedMapOf<PlanResourceId, GPUW6bMaskShaderResourceV1.Buffer>()
            val imageLeases = linkedMapOf<PlanResourceId, GPUW5eDecodedImageSessionCache.Lease>()
            val runtimeLeases = linkedMapOf<PlanResourceId, GPUW5hRuntimeResourceSessionCache.Lease>()
            fun gradientBuffer(slab: GradientStopSlabPlanV1): GPUW6bMaskShaderResourceV1.Buffer {
                val resource = frame.physical.resource(graph.resources().single { it.role == PlanResourceRole.GradientStopData }.id)
                require(resource.byteSize == slab.byteSizeI64 &&
                    resource.usages() == setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination))
                return storageBuffers.getOrPut(resource.id) {
                    val bytes = ByteBuffer.allocate(Math.toIntExact(slab.byteSizeI64)).order(ByteOrder.LITTLE_ENDIAN).also { sink ->
                        slab.copyStops().forEach { stop ->
                            sink.putFloat(stop.positionF32)
                            repeat(3) { sink.putFloat(0f) }
                            val color = stop.preparedTupleF32
                            listOf(color.red, color.green, color.blue, color.alpha).forEach(sink::putFloat)
                        }
                    }.array()
                    GPUW6bMaskShaderResourceV1.Buffer(owned.own(device.createBuffer(BufferDescriptor(
                        size = resource.byteSize.toULong(), usage = GPUBufferUsage.Storage or GPUBufferUsage.CopyDst,
                        label = "w6b.source.storage.${frame.physical.slot(resource.id).slotI32}"))).also { buffer ->
                        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(bytes), 0uL, bytes.size.toULong())
                    }, resource.byteSize)
                }
            }
            fun noiseBuffer(slab: NoiseTableSlabV1): GPUW6bMaskShaderResourceV1.Buffer {
                val resource = frame.physical.resource(graph.resources().single { it.role == PlanResourceRole.NoiseTableData }.id)
                require(resource.byteSize == slab.byteCountI64 &&
                    resource.usages() == setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination))
                return storageBuffers.getOrPut(resource.id) {
                    val bytes = ByteArray(slab.bytes.sizeI32) { slab.bytes[it].toByte() }
                    GPUW6bMaskShaderResourceV1.Buffer(owned.own(device.createBuffer(BufferDescriptor(
                        size = resource.byteSize.toULong(), usage = GPUBufferUsage.Storage or GPUBufferUsage.CopyDst,
                        label = "w6b.source.storage.${frame.physical.slot(resource.id).slotI32}"))).also { buffer ->
                        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(bytes), 0uL, bytes.size.toULong())
                    }, resource.byteSize)
                }
            }
            fun imageLease(request: PlanCacheResourceRequest.Texture): GPUW6bMaskShaderResourceV1.Image {
                val planned = frame.physical.cacheBinding(request)
                val issued = planned.request as? PlanCacheResourceRequest.Texture
                    ?: error("W6b MaskShader image binding is not a frozen W5 texture request.")
                return GPUW6bMaskShaderResourceV1.Image(imageLeases.getOrPut(planned.resourceId) {
                    owned.own(GPUW5eImageNativeV1.acquire(requireNotNull(decodedImageCache), issued, generation.value, frame.physical))
                })
            }
            fun runtimeLease(request: PlanCacheResourceRequest): GPUW6bMaskShaderResourceV1.Runtime {
                val planned = frame.physical.cacheBinding(request)
                require(planned.request is PlanCacheResourceRequest.Storage || planned.request is PlanCacheResourceRequest.Sampler)
                return GPUW6bMaskShaderResourceV1.Runtime(runtimeLeases.getOrPut(planned.resourceId) {
                    owned.own(requireNotNull(runtimeResourceCache).acquire(planned.request, generation.value))
                })
            }
            fun resourceFor(material: GPUW6bMaskShaderMaterialV1, binding: W5aMaterialSourceStage.Binding):
                GPUW6bMaskShaderResourceV1 {
                val stage = material.stage
                val proof = stage.composedProof
                val composed = binding.composedResource
                return when (binding.resourceKind) {
                    "storageBuffer" -> when (composed?.buffer?.storageKind) {
                        ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS -> gradientBuffer(requireNotNull(stage.gradientStopSlab))
                        ComposedBindingLayoutV1.StorageKind.NOISE_U32 -> noiseBuffer(requireNotNull(stage.noiseTableSlab))
                        ComposedBindingLayoutV1.StorageKind.RUNTIME_READ -> runtimeLease(requireNotNull(proof).runtimeResources
                            .single { it.resource === composed }.cacheRequest)
                        null -> gradientBuffer(requireNotNull(stage.gradientStopSlab))
                    }
                    "sampledTexture" -> proof?.composedImageResources?.singleOrNull { it.resource === composed }?.let {
                        imageLease(it.upload.cacheRequest)
                    } ?: proof?.runtimeResources?.singleOrNull { it.resource === composed }?.let {
                        imageLease(it.cacheRequest as? PlanCacheResourceRequest.Texture
                            ?: error("W6b MaskShader texture binding lost its frozen W5 texture request."))
                    } ?: imageLease(requireNotNull(stage.imageV3).cacheRequest)
                    "sampler" -> runtimeLease(requireNotNull(proof).runtimeResources.single { it.resource === composed }.cacheRequest)
                    else -> error("W6b MaskShader has an unadmitted frozen W5 resource kind ${binding.resourceKind}.")
                }
            }
            val maskShaderResources = maskShaderMaterials.values.associateWith { material ->
                material.stage.bindingManifest.filter { it.resourceKind != "uniformBuffer" }.associate { binding ->
                    binding.bindingI32 to resourceFor(material, binding)
                }
            }
            // MASK_TABLE owns both its immutable captured bytes and its physical StorageRead
            // row in the published graph.  Upload that exact snapshot to that exact row; no
            // renderer-local LUT, padding, or generated fallback is permitted.
            val maskTableBuffers = frame.physical.w6FilterMaskTableRecipes().values.associate { recipe ->
                val resource = frame.physical.resource(recipe.tableResource)
                val bytes = recipe.copyTable().copyToUByteArray()
                require(recipe.tableGenerationI64 >= 0L && recipe.tableOffsetBytesI64 >= 0L &&
                    bytes.size.toLong() == recipe.tableRangeBytesI64 &&
                    resource.role == PlanResourceRole.MaskTableData && resource.kind == PlanResourceKind.Buffer &&
                    resource.byteSize == recipe.tableRangeBytesI64 && resource.usages() ==
                    setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination))
                recipe.tableResource to owned.own(device.createBuffer(BufferDescriptor(size = resource.byteSize.toULong(),
                    usage = GPUBufferUsage.Storage or GPUBufferUsage.CopyDst,
                    label = "w6b.mask.table.${frame.physical.slot(recipe.tableResource).slotI32}"))).also { buffer ->
                    queue.writeBuffer(buffer, recipe.tableOffsetBytesI64.toULong(),
                        ArrayBuffer.of(ByteArray(bytes.size) { index -> bytes[index].toByte() }))
                }
            }
            val graphTextureOperandsBySource = graph.passes().filterIsInstance<PlanPass.PictureSourcePass>()
                .mapNotNull { pass -> pass.graphTextureOperand?.let { operand -> pass.output to operand } }
                .toMap()
            val uniform = owned.own(device.createBuffer(BufferDescriptor(size = geometryUniform.byteSize.toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst, label = "w6a.geometry.uniform")))
            val uniformBytes = ByteArray(Math.toIntExact(geometryUniform.byteSize))
            graph.passes().forEach { pass ->
                if (pass is PlanPass.LayerComposite) {
                    val recipe = frame.physical.w6FilteredLayerCompositeRecipeOrNull(W6LayerCompositeSiteKeyV1(pass.id, 0))
                    if (recipe != null) {
                        require(recipe.uniformResource == geometryUniform.id)
                        recipe.execution.copyDynamicBytes().copyInto(uniformBytes, Math.toIntExact(recipe.uniformOffsetBytesI64))
                        return@forEach
                    }
                }
                val restore = when (pass) {
                    is PlanPass.LayerComposite -> pass.restore
                    is PlanPass.FilterComposite -> (pass.operation as? FilterCompositeOperationV1.Layer)?.restore
                    else -> null
                } ?: return@forEach
                val filter = restore.colorFilter ?: return@forEach
                val offset = requireNotNull(restore.colorFilterUniformOffsetI64)
                val data = filter.copyDynamicBytes()
                data.copyInto(uniformBytes, Math.toIntExact(offset))
            }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(uniformBytes))
            val renderOperands = mutableListOf<GPUPreparedNativeScopeOperand>()
            val pathViews = mutableMapOf<Int, GPUTextureView>()
            val w4eOperands = frame.w4eAuthorities.flatMap { (binding, authority) ->
                val payload = binding.payload
                require(payload.matchesDeclaredResources(graph.resources()))
                queue.writeBuffer(geometryBuffers.getValue(payload.vertexResourceId), 0uL, ArrayBuffer.of(payload.copyVertexData()))
                queue.writeBuffer(geometryBuffers.getValue(payload.indexResourceId), 0uL, ArrayBuffer.of(payload.copyIndexData()))
                queue.writeBuffer(geometryBuffers.getValue(payload.uniformResourceId), 0uL, ArrayBuffer.of(payload.copyUniformData()))
                fun buffer(id: PlanResourceId) = GPUPreparedNativeBufferOperand(geometryBuffers.getValue(id), generation,
                    byteCapacity = frame.physical.resource(id).byteSize)
                val preflight = w4eClipMaskInitializePreflights.getValue(binding)
                val entries = preflight.entries
                val clipMaskInitializeRecipes = preflight.recipesByPassId
                val extent = binding.copyExtentI32()
                val childOwned = owned.own(GPUW4eNativeOwnedHandles())
                encodeW4eNativePasses(device, generation, entries, payload, buffer(payload.vertexResourceId),
                    buffer(payload.indexResourceId), buffer(payload.uniformResourceId), childOwned,
                    { id -> GPUPreparedNativeTextureViewOperand(views.getValue(graph.resources().single { it.id.value == id }.id), generation) },
                    { id -> graph.resources().single { it.id.value == id }.format == PlanTextureFormat.CoverageMask },
                    GPUPreparedNativeTextureViewOperand(views.getValue(binding.target), generation), null,
                    org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(0, 0, extent.width, extent.height),
                    commonSource = true, authority::consumerFor, { code, message -> IllegalArgumentException("$code: $message") },
                    clipMaskInitializeRecipesByPassId = clipMaskInitializeRecipes)
                    .map { native ->
                        val pass = graph.passes()[native.sourceStepIndex - 1]
                        native.pass.depthStencilTarget?.let { pathViews[native.sourceStepIndex] = it.view }
                        native.sourceStepIndex to GPUPreparedNativeScopeOperand.Render(native.sourceStepIndex, native.pass, native.commands,
                            native.semanticPayloads, native.operandLayout, passSegment = native.passSegment, w6aPassV1 = pass)
                    }
            }.toMap()
            graph.passes().forEachIndexed { ordinal, pass ->
                val stepIndex = ordinal + 1
                val step = framePlan.steps[stepIndex]
                if (pass is PlanPass.FilterPass && spatialBinding?.skipsFilterPass(pass.output) == true) {
                    renderOperands += GPUPreparedNativeScopeOperand.NoOp(stepIndex, GPUEncoderOperationKind.Render,
                        encoderPlan.scopes.single { it.sourceStepIndex == stepIndex }.nativeOperandKeys)
                    return@forEachIndexed
                }
                w4eOperands[stepIndex]?.let { renderOperands += it; return@forEachIndexed }
                when (pass) {
                    is PlanPass.RenderPass, is PlanPass.StencilGeometryProducerV3, is PlanPass.StencilCover -> {
                        val render = step as GPUFrameStep.RenderPassStep
                        val targetId = when (pass) {
                            is PlanPass.RenderPass -> pass.target
                            is PlanPass.StencilGeometryProducerV3 -> pass.target
                            is PlanPass.StencilCover -> pass.target
                        }
                        val depthId = when (pass) {
                            is PlanPass.StencilGeometryProducerV3 -> pass.depthStencil
                            is PlanPass.StencilCover -> pass.depthStencil
                            else -> null
                        }
                        val draws = when (pass) {
                            is PlanPass.RenderPass -> pass.draws()
                            is PlanPass.StencilGeometryProducerV3 -> listOf(graph.passes().filterIsInstance<PlanPass.StencilCover>()
                                .single { it.draw.commandIndex == pass.commandIndexI32 }.draw)
                            is PlanPass.StencilCover -> listOf(pass.draw)
                        }
                        val commands = buildList {
                            draws.zip(render.drawPackets).forEachIndexed { drawOrdinalI32, (draw, packet) ->
                                val template = frame.template(packet)
                                val binding = frame.physical.geometryBinding(pass.id)
                                val mapped = binding?.let { frame.geometryPipeline(packet) }
                                val solidRectRecipe = (draw as? SolidRectDraw)?.let {
                                    val site = W6GeometrySiteKeyV1(pass.id, drawOrdinalI32)
                                    require(frame.solidRectSite(packet) == site) {
                                        "W6 SolidRect packet lost its frozen owner/ordinal."
                                    }
                                    frame.physical.w6SolidRectHostRecipe(site)
                                }
                                val corePrimitiveRecipe = draw.takeIf {
                                    it is AnalyticRectDraw || it is AnalyticRRectDraw
                                }
                                    ?.takeIf { pass is PlanPass.RenderPass }
                                    ?.let {
                                        val site = W6GeometrySiteKeyV1(pass.id, drawOrdinalI32)
                                        require(frame.corePrimitiveSite(packet) == site) {
                                            "W6 analytic CorePrimitive packet lost its frozen owner/ordinal."
                                        }
                                        frame.physical.w6CorePrimitiveHostRecipe(site).also { recipe ->
                                            require((draw is AnalyticRectDraw && recipe is W6AnalyticRectHostRecipeV1) ||
                                                (draw is AnalyticRRectDraw && recipe is W6AnalyticRRectHostRecipeV1)) {
                                                "W6 analytic CorePrimitive recipe shape differs from its packet."
                                            }
                                            require(w6aCorePrimitivePacketMatchesRecipe(packet, draw, recipe)) {
                                                "W6 analytic CorePrimitive packet geometry differs from its frozen host recipe."
                                            }
                                        }
                                    }
                                val pointRecipe = (draw as? W5bPointDraw)
                                    ?.takeIf { it.clipOnly == null && pass is PlanPass.RenderPass }
                                    ?.let { point ->
                                        val site = W6GeometrySiteKeyV1(pass.id, drawOrdinalI32)
                                        require(frame.corePrimitiveSite(packet) == site) {
                                            "W6 Point packet lost its frozen owner/ordinal."
                                        }
                                        (frame.physical.w6CorePrimitiveHostRecipe(site) as? W6PointHostRecipeV1)?.also { recipe ->
                                            require(w6aPointPacketMatchesRecipe(packet, point, recipe)) {
                                                "W6 Point packet geometry differs from its frozen host recipe."
                                            }
                                        } ?: error("W6 Point recipe shape differs from its packet.")
                                    }
                                val preparedVerticesRecipe = (draw as? W5bVerticesDraw)
                                    ?.takeIf { pass is PlanPass.RenderPass }
                                    ?.let {
                                        val site = W6GeometrySiteKeyV1(pass.id, drawOrdinalI32)
                                        require(frame.preparedVerticesSite(packet) == site) {
                                            "W6 prepared-vertices packet lost its frozen owner/ordinal."
                                        }
                                        frame.physical.w6PreparedVerticesHostRecipe(site).also { recipe ->
                                            require(w6aPreparedVerticesPacketMatchesRecipe(packet, recipe)) {
                                                "W6 prepared-vertices packet layout, uniform, or source differs from its frozen host recipe."
                                            }
                                            require(frame.template(packet)?.pipelineRecipeId == recipe.hostProgramIdentity.canonicalIdentity) {
                                                "W6 prepared-vertices renderer template lost its frozen host program identity."
                                            }
                                        }
                                    }
                                require(corePrimitiveRecipe == null || mapped != null) {
                                    "W6 analytic CorePrimitive host recipe requires its frozen pipeline before allocation."
                                }
                                require(pointRecipe == null || mapped != null) {
                                    "W6 Point host recipe requires its frozen pipeline before allocation."
                                }
                                require(preparedVerticesRecipe == null || mapped == null) {
                                    "W6 prepared-vertices host recipe must not select a CorePrimitive pipeline."
                                }
                                val pointUniformPayload = pointRecipe?.let { recipe ->
                                    val pointBinding = requireNotNull(binding) {
                                        "W6 Point host recipe requires its physical geometry binding before allocation."
                                    }
                                    val semantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
                                        ?: error("W6 Point host recipe requires a CorePrimitive payload before allocation.")
                                    val block = requireNotNull(semantic.payloadRef.uniformBlock) {
                                        "W6 Point host recipe requires its Point32 uniform block before allocation."
                                    }
                                    val payload = frame.analyticUniform(packet)
                                    require(recipe.selector.uniformAbi == W6CorePrimitiveHostUniformAbiV1.Point32 &&
                                        block.byteSize == 32L && block.bytes.size == 32 && payload.size == 32 &&
                                        pointBinding.uniformBytesI64 == 32L && payload.size.toLong() == pointBinding.uniformBytesI64) {
                                        "W6 Point host recipe Point32 uniform differs from its sealed physical binding."
                                    }
                                    payload
                                }
                                val preparedVerticesUniformPayload = preparedVerticesRecipe?.let { recipe ->
                                    val verticesBinding = requireNotNull(binding) {
                                        "W6 prepared-vertices host recipe requires its physical geometry binding before allocation."
                                    }
                                    val semantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices
                                        ?: error("W6 prepared-vertices host recipe requires a Vertices payload before allocation.")
                                    val payload = frame.analyticUniform(packet)
                                    val sealedUpload = requireNotNull(verticesBinding.verticesUploadPayload) {
                                        "W6 prepared-vertices host recipe requires its sealed render-ir upload payload before allocation."
                                    }
                                    val sealedVertexBytes = sealedUpload.copyVertexBytes()
                                    val sealedIndexBytes = sealedUpload.copyIndexBytes()
                                    val artifactVertexBytes = semantic.artifact.vertexBytesForUpload()
                                    val artifactIndexBytes = semantic.artifact.indexBytesForUpload()
                                    require(recipe.uniformAbi == W6PreparedVerticesHostUniformAbiV1.DrawUniform64 &&
                                        recipe.groupZeroAbi == W6PreparedVerticesHostGroupZeroAbiV1.DrawUniform64 &&
                                        payload.size == 64 && verticesBinding.uniformBytesI64 == 64L &&
                                        payload.size.toLong() == verticesBinding.uniformBytesI64 &&
                                        verticesBinding.verticesUploadPayload?.canonicalIdentity == recipe.payloadCanonicalIdentity &&
                                        artifactVertexBytes.contentEquals(sealedVertexBytes) &&
                                        when {
                                            sealedIndexBytes == null -> artifactIndexBytes == null
                                            artifactIndexBytes == null -> false
                                            else -> artifactIndexBytes.contentEquals(sealedIndexBytes)
                                        } && artifactVertexBytes.size.toLong() == verticesBinding.vertexBytesI64 &&
                                        (artifactIndexBytes?.size?.toLong() ?: 0L) == verticesBinding.indexBytesI64) {
                                        "W6 prepared-vertices host recipe layout or DrawUniform64 differs from its sealed physical binding."
                                    }
                                    payload
                                }
                                val frozenLegacyColor = when (val recipe = solidRectRecipe) {
                                    null -> draw.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1
                                    else -> recipe.colorMode is W6SolidRectColorModeV1.FrozenColor
                                }
                                // W6b has already selected this source pass and its target.  Its
                                // source stage is transparent and must never consume the final
                                // draw blend; that one belongs exclusively to FilterComposite.
                                val maskMaterialSource = pass.materializesW6bMaskSourceV1()
                                val layout = owned.own(device.createBindGroupLayout(if (mapped != null) corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity)
                                else requireNotNull(template).groupZeroLayout.nativeDescriptorV1("w6a.rect.group0")))
                                val data = binding?.data
                                val verticesSemantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices
                                val pipeline = if (mapped == null) pipeline(requireNotNull(template).sourceWgsl, layout,
                                    w6aColorTarget(if (maskMaterialSource) BlendPlan.LegacySrcOverV1 else solidRectRecipe?.blend
                                        ?: preparedVerticesRecipe?.blend ?: draw.blend), owned, template,
                                    verticesSemantic?.artifact)
                                    else geometryPipeline(mapped, layout, owned, template,
                                        if (maskMaterialSource) BlendPlan.LegacySrcOverV1 else null,
                                        maskMaterialSource && pass is PlanPass.StencilCover)
                                val uniformPayload = binding?.let { pointUniformPayload ?: preparedVerticesUniformPayload ?: frame.analyticUniform(packet) }
                                val nativeUniform = data?.let { geometryBuffers.getValue(it.uniform) } ?: uniform
                                if (data != null) {
                                    require(!frozenLegacyColor)
                                    require(draws.size == 1)
                                    if (verticesSemantic != null) {
                                        val vertices = verticesSemantic.artifact.vertexBytesForUpload()
                                        val indices = verticesSemantic.artifact.indexBytesForUpload()
                                        require(vertices.size.toLong() == binding.vertexBytesI64 &&
                                            (indices?.size?.toLong() ?: 0L) == binding.indexBytesI64 &&
                                            requireNotNull(uniformPayload).size.toLong() == binding.uniformBytesI64)
                                        queue.writeBuffer(geometryBuffers.getValue(data.vertex), binding.vertexOffsetI64.toULong(), ArrayBuffer.of(vertices))
                                        if (indices != null) queue.writeBuffer(geometryBuffers.getValue(data.index), binding.indexOffsetI64.toULong(),
                                            ArrayBuffer.of(indices.copyOf(Math.toIntExact(binding.indexUploadBytesI64))))
                                    } else {
                                    val sourceBounds = if (maskMaterialSource) {
                                        requireNotNull(graph.resources().single { it.id == targetId }.copyExtent()).let { extent ->
                                            RectI32(0, 0, extent.width, extent.height)
                                        }
                                    } else null
                                    val (vertices, indices) = when (draw) {
                                        // The source target is published by the frozen W6b pass.
                                        // Expand only this existing source draw to that target; W5
                                        // then shades the material once while FilterCoverage owns
                                        // the original shape coverage independently.
                                        is AnalyticRectDraw, is AnalyticRRectDraw -> packW4RasterGeometry(listOf(
                                            sourceBounds ?: requireNotNull(corePrimitiveRecipe).rasterBounds,
                                        )).let { it.vertices to it.indices }
                                        is W5bPointDraw -> sourceBounds?.let { bounds ->
                                            fullMaskMaterialPointGeometry(draw, bounds)
                                        } ?: pointRecipe?.let { it.copyVerticesF32() to it.copyIndicesI32() }
                                            ?: (draw.copyVerticesF32() to draw.copyIndicesI32())
                                        is PathDraw -> {
                                            sourceBounds?.let { bounds ->
                                                if (pass is PlanPass.StencilCover) {
                                                    packW4RasterGeometry(listOf(bounds)).let { it.vertices to it.indices }
                                                } else fullMaskMaterialTriangleGeometry(bounds,
                                                    binding.vertexCountI32, binding.indexCountI32)
                                            } ?: run {
                                                val fill = when (val geometry = draw.copyPathGeometry()) {
                                                    is PathDrawGeometry.Fill -> geometry.valueF32
                                                    is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
                                                    else -> error("Unadmitted W6 path geometry")
                                                }
                                                when (pass) {
                                                    is PlanPass.StencilGeometryProducerV3 -> requireNotNull(fill.copyStencilEdgeFanF32OrNull()).let {
                                                        it.copyVerticesF32() to it.copyIndicesI32() }
                                                    is PlanPass.StencilCover -> packW4RasterGeometry(listOf(draw.copyScissorI32())).let { it.vertices to it.indices }
                                                    else -> requireNotNull(fill.copyDirectTriangleF32OrNull()).let { it.copyVerticesF32() to it.copyIndicesI32() }
                                                }
                                            }
                                        }
                                        else -> error("Unadmitted W6 geometry data")
                                    }
                                    require(vertices.size * 4L == binding.vertexBytesI64 && indices.size * 4L == binding.indexBytesI64 &&
                                        requireNotNull(uniformPayload).size.toLong() == binding.uniformBytesI64)
                                    queue.writeBuffer(geometryBuffers.getValue(data.vertex), binding.vertexOffsetI64.toULong(), ArrayBuffer.of(vertices))
                                    queue.writeBuffer(geometryBuffers.getValue(data.index), binding.indexOffsetI64.toULong(), ArrayBuffer.of(indices))
                                    }
                                    queue.writeBuffer(nativeUniform, binding.uniformOffsetI64.toULong(), ArrayBuffer.of(requireNotNull(uniformPayload)))
                                }
                                val bind = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout,
                                    entries = if (frozenLegacyColor) emptyList() else listOf(BindGroupEntry(0u,
                                        BufferBinding(nativeUniform, 0uL, uniformPayload?.size?.toULong() ?: geometryUniform.byteSize.toULong()))))))
                                add(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)))
                                add(GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(bind, generation),
                                    if (mapped == null) emptyList() else binding.let { listOf(it.uniformOffsetI64) }))
                                val scissor = if (maskMaterialSource) {
                                    val extent = requireNotNull(graph.resources().single { it.id == targetId }.copyExtent())
                                    RectI32(0, 0, extent.width, extent.height)
                                } else when (draw) {
                                    // The W6a rect vertex shader is fullscreen; its raster domain is
                                    // therefore the immutable visible rect intersected with the clip,
                                    // not the clip alone.
                                    is SolidRectDraw -> draw.copyVisibleBounds().also {
                                        require(it.intersect(draw.copyScissor()))
                                    }
                                    is AnalyticRectDraw, is AnalyticRRectDraw ->
                                        requireNotNull(corePrimitiveRecipe).scissor
                                    is PathDraw -> draw.copyScissorI32()
                                    is W5bPointDraw -> pointRecipe?.scissor ?: draw.copyScissorI32()
                                    is W5bVerticesDraw -> draw.copyScissorI32()
                                    else -> error("Unadmitted W6 geometry")
                                }
                                // W6a construction already rebases non-root PlanDraws to their
                                // frozen target; WebGPU therefore receives this texture-local scissor.
                                add(GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()))
                                if (data == null) add(GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)))
                                else {
                                    add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0,
                                        GPUPreparedNativeBufferOperand(geometryBuffers.getValue(data.vertex), generation), binding.vertexOffsetI64, binding.vertexBytesI64, binding.vertexStrideBytesI32.toLong()))
                                    if (binding.indexCountI32 == 0) add(GPUPreparedNativeRenderCommand.Draw(
                                        GPUPreparedNativeDrawCall.Draw(binding.vertexCountI32, 1, 0, 0))) else {
                                    add(GPUPreparedNativeRenderCommand.SetIndexBuffer(
                                        GPUPreparedNativeBufferOperand(geometryBuffers.getValue(data.index), generation),
                                        if (binding.indexElementBytesI32 == 2) GPUPreparedNativeIndexFormat.Uint16 else GPUPreparedNativeIndexFormat.Uint32,
                                        binding.indexOffsetI64, binding.indexBytesI64))
                                    add(GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(
                                        indexCount = binding.indexCountI32, firstIndex = 0, baseVertex = 0,
                                        vertexCount = binding.vertexCountI32, maxLocalIndex = binding.maxLocalIndexI32)))
                                    }
                                }
                            }
                        }
                        val clear = render.loadStore.loadOp == "clear"
                        depthId?.let { pathViews[stepIndex] = views.getValue(it) }
                        renderOperands += GPUPreparedNativeScopeOperand.Render(stepIndex,
                            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(targetId), generation),
                                depthStencilTarget = depthId?.let { GPUPreparedNativeTextureViewOperand(views.getValue(it), generation) },
                                loadOperation = if (clear) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                                clearColor = if (clear) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null,
                                depthReadOnly = true, stencilReadOnly = depthId == null,
                                stencilClearValue = if (pass is PlanPass.StencilGeometryProducerV3) 0u else null,
                                stencilLoadOperation = depthId?.let { if (pass is PlanPass.StencilGeometryProducerV3)
                                    GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load },
                                stencilStoreOperation = depthId?.let { GPUPreparedNativeStoreOperation.Store }),
                            commands, render.drawPackets.map { requireNotNull(it.semanticPayload) }, w6aPassV1 = pass)
                    }
                    is PlanPass.LayerComposite -> {
                        val plainSite = W6LayerCompositeSiteKeyV1(pass.id, 0)
                        val plainRecipe = frame.physical.w6PlainLayerCompositeRecipeOrNull(plainSite)
                        if (plainRecipe != null) {
                            require(plainRecipe.site == plainSite && plainRecipe.source == pass.source &&
                                plainRecipe.destination == pass.destination &&
                                plainRecipe.family == W6PlainLayerCompositeFamilyV1.FullscreenRestore &&
                                plainRecipe.target == W6PlainLayerCompositeTargetV1.Rgba8UnormSrgbSingleSample &&
                                plainRecipe.groupZeroAbi == W6PlainLayerCompositeGroupZeroAbiV1.OneTexture &&
                                plainRecipe.blend !is BlendPlan.DestinationReadV1 && plainRecipe.alphaF32.isFinite()) {
                                "W6 plain layer-composite recipe projection changed before native allocation."
                            }
                            val source = plainRecipe.copySourceBoundsLayerI32()
                            val destination = plainRecipe.copyDestinationOriginParentI32()
                            val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
                                BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
                            ))))
                            val shader = W6A_VERTEX_SHADER + """
                                @group(0) @binding(0) var layer_source: texture_2d<f32>;
                                @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                                    return textureLoad(layer_source, vec2<i32>(position.xy) - vec2<i32>(${destination.x}, ${destination.y}) + vec2<i32>(${source.left}, ${source.top}), 0) * ${plainRecipe.alphaF32};
                                }
                            """
                            val pipeline = pipeline(shader, layout, w6aColorTarget(plainRecipe.blend), owned)
                            val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout,
                                entries = listOf(BindGroupEntry(0u, views.getValue(plainRecipe.source))))))
                            renderOperands += GPUPreparedNativeScopeOperand.Render(stepIndex,
                                GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(plainRecipe.destination), generation)),
                                listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                                    GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                                    GPUPreparedNativeRenderCommand.SetScissor(destination.x, destination.y, source.width(), source.height()),
                                    GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0))),
                                operationKindOverride = GPUEncoderOperationKind.LayerComposite, w6aPassV1 = pass)
                            return@forEachIndexed
                        }
                        val filteredRecipe = frame.physical.w6FilteredLayerCompositeRecipeOrNull(plainSite)
                        if (filteredRecipe != null) {
                            require(filteredRecipe.source == pass.source && filteredRecipe.destination == pass.destination &&
                                filteredRecipe.blend !is BlendPlan.DestinationReadV1 && filteredRecipe.alphaF32.isFinite()) {
                                "W6 filtered layer-composite recipe projection changed before native allocation."
                            }
                            val source = filteredRecipe.copySourceBoundsLayerI32()
                            val destination = filteredRecipe.copyDestinationOriginParentI32()
                            val scissor = filteredRecipe.copyScissorParentI32()
                            val execution = filteredRecipe.execution
                            val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
                                BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
                                BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,
                                    minBindingSize = maxOf(16L, execution.dynamicByteCountI64).toULong())),
                            ))))
                            val declaration = "struct W5fMaterialBlock { words: array<vec4<u32>, ${maxOf(1L, (execution.dynamicByteCountI64 + 15L) / 16L)}>, }\n" +
                                "@group(0) @binding(1) var<uniform> w5fMaterial: W5fMaterialBlock;\n" +
                                "fn w6a_restore_filter(input: vec4<f32>) -> vec4<f32> {\n" +
                                W5fColorOperationEmitterV1.emit(execution.copyOperationGraph(), "input", 0L) + "}\n"
                            val shader = W6A_VERTEX_SHADER + """
                                @group(0) @binding(0) var layer_source: texture_2d<f32>;
                                $declaration
                                @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                                    let alpha_applied = textureLoad(layer_source, vec2<i32>(position.xy) - vec2<i32>(${destination.x}, ${destination.y}) + vec2<i32>(${source.left}, ${source.top}), 0) * ${filteredRecipe.alphaF32};
                                    return w6a_restore_filter(alpha_applied);
                                }
                            """
                            val pipeline = pipeline(shader, layout, w6aColorTarget(filteredRecipe.blend), owned)
                            val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
                                BindGroupEntry(0u, views.getValue(filteredRecipe.source)),
                                BindGroupEntry(1u, BufferBinding(uniform, filteredRecipe.uniformOffsetBytesI64.toULong(), maxOf(16L, execution.dynamicByteCountI64).toULong())),
                            ))))
                            renderOperands += GPUPreparedNativeScopeOperand.Render(stepIndex,
                                GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(filteredRecipe.destination), generation)),
                                listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                                    GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                                    GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                                    GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(filteredRecipe.draw.vertexCountI32, filteredRecipe.draw.instanceCountI32, filteredRecipe.draw.firstVertexI32, filteredRecipe.draw.firstInstanceI32))),
                                operationKindOverride = GPUEncoderOperationKind.LayerComposite, w6aPassV1 = pass)
                            return@forEachIndexed
                        }
                        val filter = pass.restore.colorFilter
                        val destinationRead = pass.restore.blend as? BlendPlan.DestinationReadV1
                        val entries = buildList {
                            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
                            if (filter != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment,
                                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,
                                    minBindingSize = maxOf(16L, filter.dynamicByteCountI64).toULong())))
                            if (destinationRead != null) add(BindGroupLayoutEntry(2u, GPUShaderStage.Fragment,
                                texture = TextureBindingLayout()))
                        }
                        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = entries)))
                        val source = pass.copySourceBoundsLayerI32()
                        val destination = pass.copyDestinationOriginParentI32()
                        val colorDeclaration = filter?.let { execution ->
                            "struct W5fMaterialBlock { words: array<vec4<u32>, ${maxOf(1L, (execution.dynamicByteCountI64 + 15L) / 16L)}>, }\n" +
                                "@group(0) @binding(1) var<uniform> w5fMaterial: W5fMaterialBlock;\n" +
                                "fn w6a_restore_filter(input: vec4<f32>) -> vec4<f32> {\n" +
                                W5fColorOperationEmitterV1.emit(execution.copyOperationGraph(), "input", 0L) + "}\n"
                        }.orEmpty()
                        val formula = destinationRead?.let { blend ->
                            requireNotNull(BlendFormulaProgramV1.selectedBlendFunctionWgsl(blend.mode.name.lowercase(), "w6a_restore_blend"))
                        }.orEmpty()
                        val filterExpression = if (filter == null) "alpha_applied" else "w6a_restore_filter(alpha_applied)"
                        val blendExpression = if (destinationRead == null) filterExpression else
                            "w6a_restore_blend($filterExpression, textureLoad(destination_snapshot, vec2<i32>(position.xy), 0))"
                        val snapshotDeclaration = if (destinationRead == null) "" else
                            "@group(0) @binding(2) var destination_snapshot: texture_2d<f32>;"
                        val shader = W6A_VERTEX_SHADER + """
                            @group(0) @binding(0) var layer_source: texture_2d<f32>;
                            $colorDeclaration
                            $snapshotDeclaration
                            $formula
                            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                                let alpha_applied = textureLoad(layer_source, vec2<i32>(position.xy) - vec2<i32>(${destination.x}, ${destination.y}) + vec2<i32>(${source.left}, ${source.top}), 0) * ${pass.restore.alphaF32};
                                return $blendExpression;
                            }
                        """
                        val pipeline = pipeline(shader, layout, w6aColorTarget(pass.restore.blend), owned)
                        val bindings = buildList {
                            add(BindGroupEntry(0u, views.getValue(pass.source)))
                            if (filter != null) add(BindGroupEntry(1u, BufferBinding(uniform,
                                requireNotNull(pass.restore.colorFilterUniformOffsetI64).toULong(),
                                maxOf(16L, filter.dynamicByteCountI64).toULong())))
                            if (destinationRead != null) add(BindGroupEntry(2u, views.getValue(requireNotNull(destinationRead.snapshotResource))))
                        }
                        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = bindings)))
                        renderOperands += GPUPreparedNativeScopeOperand.Render(stepIndex,
                            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(views.getValue(pass.destination), generation)),
                            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                                GPUPreparedNativeRenderCommand.SetScissor(destination.x, destination.y, source.width(), source.height()),
                                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0))),
                            operationKindOverride = GPUEncoderOperationKind.LayerComposite, w6aPassV1 = pass)
                    }
                    is PlanPass.PictureAggregateBeginPass, is PlanPass.FilterSourceClear -> {
                        val target = when (pass) {
                            is PlanPass.PictureAggregateBeginPass -> pass.target
                            is PlanPass.FilterSourceClear -> pass.output
                        }
                        renderOperands += emptyRender(stepIndex, views.getValue(target), generation, pass, frame.physical.w6FullscreenEmptyRecipe(pass.id), owned)
                    }
                    is PlanPass.FilterCoverageSourcePass -> {
                        // The frozen catalog, not nullable pass fields, chooses the native site.
                        // Pass fields below only authenticate the selected frozen recipe.
                        when (val recipe = frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(
                            NativeSiteOwnerV1(pass.id, 0, 0),
                        )) {
                            is W6FullscreenCoverageAlphaNativeSiteRecipeV1 -> {
                                val host = frame.physical.w6FullscreenCoverageAlphaRecipe(pass.id)
                                require(recipe.host === host)
                                renderOperands += coverageAlphaRender(stepIndex, views.getValue(host.target), views.getValue(host.source), generation,
                                    host, pass, owned)
                            }
                            is W6FullscreenEmptyNativeSiteRecipeV1 -> {
                                // Plan-published coverage absence remains an executed Empty site.
                                renderOperands += emptyRender(stepIndex, views.getValue(recipe.host.target), generation, pass, recipe.host, owned)
                            }
                            is W6bCoverageRasterNativeSiteRecipeV1 -> {
                                val binding = requireNotNull(pass.rasterBinding) {
                                    "Frozen W6b coverage raster has no authenticated raster binding."
                                }
                                require(binding.draw !is SolidRectDraw)
                                renderOperands += coverageRasterRender(stepIndex, views.getValue(recipe.host.output),
                                    binding.depthStencil?.let(views::get), generation, frame, step as? GPUFrameStep.RenderPassStep
                                        ?: error("W6b coverage requires its frozen render step"), pass, binding,
                                    geometryBuffers, uniform, owned)
                            }
                            is W6FullscreenCoverageSolidRectNativeSiteRecipeV1 -> {
                                val host = frame.physical.w6FullscreenCoverageSolidRectRecipe(pass.id)
                                require(recipe.host === host)
                                renderOperands += coverageSolidRectRender(stepIndex, views.getValue(host.target), generation,
                                    host, pass, owned)
                            }
                            null -> error("CoverageSource pass is missing its frozen native-site recipe.")
                            else -> error("CoverageSource pass selected an inadmissible frozen native-site recipe ${recipe.family}.")
                        }
                    }
                    is PlanPass.FilterCoverageRetainPass -> {
                        val recipe = frame.physical.nativeSiteRecipeCatalogV1().recipe(NativeSiteOwnerV1(pass.id, 0, 0))
                            as? W6FullscreenCoverageRetainNativeSiteRecipeV1
                            ?: error("CoverageRetain pass is missing its frozen native-site recipe.")
                        renderOperands += coverageRetainRender(stepIndex, views.getValue(recipe.host.target), views.getValue(recipe.host.source), generation,
                            recipe.host, pass, owned)
                    }
                    is PlanPass.PictureAggregateSealPass -> {
                        renderOperands += emptyRender(stepIndex, views.getValue(pass.aggregateTarget), generation, pass, frame.physical.w6FullscreenEmptyRecipe(pass.id), owned)
                    }
                    is PlanPass.PictureSourcePass -> {
                        when (val recipe = frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0))) {
                            is W6FullscreenPictureSourceLayerNativeSiteRecipeV1 -> {
                                val host = frame.physical.w6FullscreenPictureSourceLayerRecipe(pass.id)
                                require(recipe.host === host)
                                renderOperands += pictureSourceLayerRender(stepIndex, views.getValue(host.target), views.getValue(host.source),
                                    generation, host, pass, owned)
                            }
                            is W6FullscreenPictureSourceGraphNativeSiteRecipeV1 -> {
                                val host = frame.physical.w6FullscreenPictureSourceGraphRecipe(pass.id)
                                require(recipe.host === host)
                                renderOperands += pictureSourceGraphRender(stepIndex, views.getValue(host.target), views.getValue(host.source),
                                    generation, host, pass, owned)
                            }
                            null -> error("PictureSource pass is missing its frozen native-site recipe.")
                            else -> error("PictureSource pass has an incompatible frozen native-site recipe.")
                        }
                    }
                    is PlanPass.FilterPass -> {
                        val outputExtent = requireNotNull(graph.resources().single { it.id == pass.output }.copyExtent())
                        val cropRecipe = frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0))
                            as? W6FilterSpatialCropNativeSiteRecipeV1
                        if (cropRecipe != null) {
                            require(cropRecipe.host === frame.physical.w6FilterSpatialCropRecipe(pass.id))
                            renderOperands += spatialCropRender(stepIndex, views.getValue(cropRecipe.host.target),
                                views.getValue(cropRecipe.host.source), generation, cropRecipe.host, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterSpatialOffsetNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterSpatialOffsetRecipe(pass.id)
                            renderOperands += spatialOffsetRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.source), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterSpatialTileNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterSpatialTileRecipe(pass.id)
                            renderOperands += spatialTileRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.source), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMorphologyNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMorphologyRecipe(pass.id)
                            renderOperands += morphologyRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.source), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterColorFilterNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterColorFilterRecipe(pass.id)
                            renderOperands += colorFilterRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.source), colorFilterUniformBuffers.getValue(recipe.uniformResource), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMergeNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMergeRecipe(pass.id)
                            renderOperands += mergeRender(stepIndex, views.getValue(recipe.target), recipe.inputs().map { views.getValue(it.source) }, generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterBlendNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterBlendRecipe(pass.id)
                            renderOperands += blendRender(stepIndex, views.getValue(recipe.target),
                                listOf(views.getValue(recipe.background().source), views.getValue(recipe.foreground().source)),
                                generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterSeparableBlurNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterSeparableBlurRecipe(pass.id)
                            val offset = recipe.copyOutputToInputOffsetTargetLocalI32(); val known = recipe.copyKnownContentTargetLocalI32()
                            val shader = W6A_VERTEX_SHADER + W6bSeparableBlurSnippet.fragment(recipe.axis, recipe.sigmaF32, recipe.tileMode, offset.x, offset.y, known.left, known.top, known.right, known.bottom, recipe.kind in setOf(FilterImplementationKindV1.MASK_COVERAGE_BLUR_X, FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y))
                            val extent = recipe.copyExtent()
                            renderOperands += separableBlurRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.source), generation, shader, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMaskBlurNormalNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMaskBlurNormalRecipe(pass.id)
                            renderOperands += maskBlurNormalRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.blurredSource), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMaskBlurDualSourceNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMaskBlurDualSourceRecipe(pass.id)
                            renderOperands += maskBlurDualSourceRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.blurredSource), views.getValue(recipe.originalSource), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMaskShaderNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMaskShaderRecipe(pass.id)
                            val binding = (pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding as?
                                FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned
                                ?: error("W6 MaskShader lost its frozen W5 material binding.")
                            val material = frame.maskShaderMaterial(binding)
                            renderOperands += maskShaderRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.coverageSource), generation,
                                recipe, material, sourceUniformBuffers.getValue(recipe.uniformResource),
                                maskShaderResources.getValue(material), pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMaskTableNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMaskTableRecipe(pass.id)
                            renderOperands += maskTableCoverageRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.coverageSource),
                                maskTableBuffers.getValue(recipe.tableResource), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterMaterializedSourceNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterMaterializedSourceRecipe(pass.id)
                            renderOperands += maskedMaterialSourceRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.source), views.getValue(recipe.coverage), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterDropShadowColorizeNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterDropShadowColorizeRecipe(pass.id)
                            renderOperands += dropShadowColorizeRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.blurredSource), generation, recipe, pass, owned)
                        } else if (frame.physical.nativeSiteRecipeCatalogV1().recipeOrNull(NativeSiteOwnerV1(pass.id, 0, 0)) is W6FilterDropShadowCompositeNativeSiteRecipeV1) {
                            val recipe = frame.physical.w6FilterDropShadowCompositeRecipe(pass.id)
                            renderOperands += dropShadowCompositeRender(stepIndex, views.getValue(recipe.target), views.getValue(recipe.colorizedShadow), views.getValue(recipe.originalSource), generation, recipe, pass, owned)
                        } else when (val operation = pass.operation) {
                            is FilterPassOperationV1.Crop -> error("Crop pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.Offset -> error("Offset pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.Tile -> error("Tile pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.ColorFilter -> error("ColorFilter pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.Merge -> error("Merge pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.Blend -> error("Blend pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.Morphology -> error("Morphology pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.SeparableBlur -> error("SeparableBlur pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.MaskBlurStyle -> {
                                error("MaskBlur ${operation.style} pass is missing its frozen native-site recipe.")
                                val blurredOffset = requireNotNull(operation.blurredSampling) {
                                    "W6b mask style has no sealed blurred sampling."
                                }.copyOutputToInputOffsetTargetLocalI32()
                                val original = operation.originalCoverageSource
                                val originalOffset = operation.originalSampling?.copyOutputToInputOffsetTargetLocalI32()
                                renderOperands += maskStyleRender(stepIndex, views.getValue(pass.output),
                                    views.getValue(operation.blurredCoverageSource), original?.let(views::get), generation,
                                    operation.style, blurredOffset.x, blurredOffset.y, originalOffset?.x, originalOffset?.y,
                                    outputExtent.width, outputExtent.height, pass, owned)
                            }
                            is FilterPassOperationV1.MaskShader -> error("MaskShader pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.MaskTable -> error("MaskTable pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.MaterializedSource -> error("MaterializedSource pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.DropShadowColorize -> error("DropShadowColorize pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.DropShadowComposite -> error("DropShadowComposite pass is missing its frozen native-site recipe.")
                            is FilterPassOperationV1.MatrixConvolution,
                            is FilterPassOperationV1.DisplacementMap,
                            is FilterPassOperationV1.Magnifier -> {
                                val binding = requireNotNull(pass.frozenSamplingProgram) {
                                    "W6d sampling pass lost its frozen program/binding contract."
                                }
                                require(binding.inputs() == pass.inputs() && binding.output == pass.output)
                                renderOperands += multiInputRender(stepIndex, views.getValue(binding.output), binding.inputs().map(views::getValue), generation,
                                    W6A_VERTEX_SHADER + GPUW6dAdvancedSamplingPass.fragment(binding.program),
                                    outputExtent.width, outputExtent.height, pass, owned)
                            }
                            is FilterPassOperationV1.Lighting -> {
                                val binding = requireNotNull(pass.frozenSamplingProgram) {
                                    "W6d lighting lost its frozen program/binding contract."
                                }
                                require(binding.inputs() == pass.inputs() && binding.output == pass.output)
                                val fragment = when (val program = binding.program) {
                                    is W6dSamplingProgramV1.DistantDiffuse -> {
                                        require(operation.family == LightingFamilyV1.DISTANT_DIFFUSE)
                                        GPUW6dDistantDiffusePass.fragment(program)
                                    }
                                    is W6dSamplingProgramV1.Lighting -> {
                                        require(operation.family == program.family)
                                        GPUW6dLightingPass.fragment(program)
                                    }
                                    else -> error("W6d lighting received a non-lighting frozen recipe.")
                                }
                                renderOperands += multiInputRender(stepIndex, views.getValue(binding.output), binding.inputs().map(views::getValue), generation,
                                    W6A_VERTEX_SHADER + fragment,
                                    outputExtent.width, outputExtent.height, pass, owned)
                            }
                            is FilterPassOperationV1.Picture -> {
                                val sealed = operation.copySealedSource()
                                val binding = requireNotNull(pass.frozenSamplingProgram) {
                                    "W6d Picture pass lost its frozen program/binding contract."
                                }
                                val program = binding.program as? W6dSamplingProgramV1.Picture
                                    ?: error("W6d Picture pass received a non-Picture frozen program.")
                                require(binding.inputs() == listOf(sealed.resourceId) && binding.output == pass.output &&
                                    program.copySampling().matches(operation.copyPictureSampling())) {
                                    "W6d Picture pass does not bind its exact frozen source and sampling recipe."
                                }
                                renderOperands += textureRender(stepIndex, views.getValue(binding.output),
                                    views.getValue(binding.inputs().single()), generation,
                                    W6A_VERTEX_SHADER + GPUW6dPictureSamplingPass.fragment(program),
                                    BlendPlan.LegacySrcOverV1,
                                    0, 0, outputExtent.width, outputExtent.height, pass, owned)
                            }
                            is FilterPassOperationV1.RuntimeImageOpacity -> {
                                val binding = requireNotNull(pass.frozenSamplingProgram) {
                                    "W6d runtime image opacity lost its frozen program/binding contract."
                                }
                                val program = binding.program as? W6dSamplingProgramV1.RuntimeImageOpacity
                                    ?: error("W6d runtime image opacity received a non-runtime frozen program.")
                                require(binding.inputs() == pass.inputs() && binding.output == pass.output &&
                                    program.alphaF32 == operation.alphaF32 &&
                                    program.alphaUniformOffsetBytesI32 == operation.alphaUniformOffsetBytesI32 &&
                                    operation.sampling.copyOutputToInputOffsetTargetLocalI32().x == 0 &&
                                    operation.sampling.copyOutputToInputOffsetTargetLocalI32().y == 0) {
                                    "W6d runtime image opacity does not retain its sealed same-pixel binding."
                                }
                                renderOperands += multiInputRender(
                                    stepIndex,
                                    views.getValue(binding.output),
                                    binding.inputs().map(views::getValue),
                                    generation,
                                    W6A_VERTEX_SHADER + GPUW6dAdvancedSamplingPass.fragment(program),
                                    outputExtent.width,
                                    outputExtent.height,
                                    pass,
                                    owned,
                                )
                            }
                        }
                    }
                    is PlanPass.PictureComposite -> {
                        val operands = requireNotNull(pass.operands) { "W6b Picture composite needs frozen operands." }
                        val directRecipe = frame.physical.w6PictureCompositeRecipeOrNull(pass.id)
                        val scissor = directRecipe?.copyCompositeScissorTargetLocalI32() ?: operands.copyCompositeScissorTargetLocalI32()
                        val sampleOffset = directRecipe?.copySourceSampleOffsetTargetLocalI32() ?: operands.copySourceSampleOffsetTargetLocalI32()
                        val operand = graphTextureOperandsBySource[pass.source]
                        renderOperands += if (scissor == null) emptyRender(stepIndex, views.getValue(pass.destination), generation,
                            pass, frame.physical.w6FullscreenEmptyRecipe(pass.id), owned) else if (operand == null) {
                            textureRender(stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                sampledCompositeShader(sampleOffset.x, sampleOffset.y, 1f), directRecipe?.blend ?: operands.blend,
                                scissor.left, scissor.top, scissor.width(), scissor.height(), pass, owned)
                        } else {
                            require(operand.finalBlend.canonicalLabel == operands.blend.canonicalLabel) {
                                "W6b Picture composite blend differs from its frozen graph-texture operand."
                            }
                            val filter = operand.colorFilter
                            val filterOffset = filter?.let { requireNotNull(operand.colorFilterUniformOffsetI64) }
                            val filterCapacity = filter?.let { requireNotNull(operand.colorFilterUniformByteCountI64) }
                            val filterBuffer = filter?.let { execution ->
                                val offset = requireNotNull(filterOffset)
                                val capacity = requireNotNull(filterCapacity)
                                val bindingBytes = maxOf(16L, execution.dynamicByteCountI64)
                                require(Math.addExact(offset, bindingBytes) <= capacity)
                                graphTextureUniformBuffers.getValue(operand.uniformResource).also { buffer ->
                                    if (execution.dynamicByteCountI64 > 0L)
                                        queue.writeBuffer(buffer, offset.toULong(), ArrayBuffer.of(execution.copyDynamicBytes()))
                                }
                            }
                            filteredCompositeRender(
                                stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                sampleOffset, requireNotNull(scissor), operand.alphaF32, filter, filterBuffer,
                                if (filter == null) null else 0L, filterCapacity, filterOffset?.div(4L) ?: 0L,
                                (operands.blend as? BlendPlan.DestinationReadV1)?.snapshotResource?.let(views::get),
                                operands.blend, pass, owned,
                            )
                        }
                    }
                    is PlanPass.FilterComposite -> {
                        val sampleOffset = pass.copySourceSampleOffsetTargetLocalI32()
                        val scissor = pass.copyCompositeScissorTargetLocalI32()
                        when (val operation = pass.operation) {
                            is FilterCompositeOperationV1.Draw -> {
                                renderOperands += if (operation.noOp) emptyRender(stepIndex, views.getValue(pass.destination), generation,
                                    pass, frame.physical.w6FullscreenEmptyRecipe(pass.id), owned) else {
                                    val finalScissor = requireNotNull(scissor) { "W6b Draw composite has no sealed scissor." }
                                    textureRender(
                                        stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                        sampledCompositeShader(sampleOffset.x, sampleOffset.y, 1f), operation.blend,
                                        finalScissor.left, finalScissor.top, finalScissor.width(), finalScissor.height(), pass, owned,
                                    )
                                }
                            }
                            is FilterCompositeOperationV1.Layer -> {
                                val restore = operation.restore
                                val destinationRead = restore.blend as? BlendPlan.DestinationReadV1
                                renderOperands += if (operation.noOp) emptyRender(stepIndex, views.getValue(pass.destination), generation,
                                    pass, frame.physical.w6FullscreenEmptyRecipe(pass.id), owned) else filteredCompositeRender(
                                    stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                    sampleOffset, requireNotNull(scissor) { "W6b Layer composite has no sealed scissor." },
                                    restore.alphaF32, restore.colorFilter, uniform, restore.colorFilterUniformOffsetI64,
                                    restore.colorFilter?.let { maxOf(16L, it.dynamicByteCountI64) }, 0L,
                                    destinationRead?.snapshotResource?.let(views::get), restore.blend, pass, owned,
                                )
                            }
                            is FilterCompositeOperationV1.Picture -> {
                                val terminal = requireNotNull(operation.terminal)
                                val operand = graphTextureOperandsBySource[pass.evaluationKey.boundSourceId]
                                renderOperands += if (scissor == null) {
                                    emptyRender(stepIndex, views.getValue(pass.destination), generation, pass, frame.physical.w6FullscreenEmptyRecipe(pass.id), owned)
                                } else if (operand == null) {
                                    // Inner Picture draws already carry their W5 material in the
                                    // filter source.  They have no parent graph-texture operand,
                                    // but their frozen terminal still owns a destination snapshot
                                    // and exact blend.
                                    filteredCompositeRender(
                                        stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                        sampleOffset, scissor, 1f, null, null, null, null, 0L,
                                        (terminal.blend as? BlendPlan.DestinationReadV1)?.snapshotResource?.let(views::get),
                                        terminal.blend, pass, owned,
                                    )
                                } else {
                                    require(operand.finalBlend.canonicalLabel == terminal.blend.canonicalLabel) {
                                        "W6b Picture terminal blend differs from its frozen graph-texture operand."
                                    }
                                    val filter = operand.colorFilter
                                    val filterOffset = filter?.let { requireNotNull(operand.colorFilterUniformOffsetI64) }
                                    val filterCapacity = filter?.let { requireNotNull(operand.colorFilterUniformByteCountI64) }
                                    val filterBuffer = filter?.let { execution ->
                                        val offset = requireNotNull(filterOffset)
                                        val capacity = requireNotNull(filterCapacity)
                                        val bindingBytes = maxOf(16L, execution.dynamicByteCountI64)
                                        require(Math.addExact(offset, bindingBytes) <= capacity)
                                        graphTextureUniformBuffers.getValue(operand.uniformResource).also { buffer ->
                                            if (execution.dynamicByteCountI64 > 0L)
                                                queue.writeBuffer(buffer, offset.toULong(), ArrayBuffer.of(execution.copyDynamicBytes()))
                                        }
                                    }
                                    filteredCompositeRender(
                                        stepIndex, views.getValue(pass.destination), views.getValue(pass.source), generation,
                                        sampleOffset, scissor,
                                        operand.alphaF32, filter, filterBuffer, if (filter == null) null else 0L, filterCapacity,
                                        filterOffset?.div(4L) ?: 0L,
                                        (terminal.blend as? BlendPlan.DestinationReadV1)?.snapshotResource?.let(views::get), terminal.blend,
                                        pass, owned,
                                    )
                                }
                            }
                        }
                    }
                    is PlanPass.ReadbackPass -> {
                        val output = resources.outputOwnedReadbacks.single()
                        val staging = frame.physical.resource(pass.staging)
                        require(output.stagingLease.backingBufferBytes == staging.byteSize && output.layout.paddedBytesPerRow == pass.bytesPerRow &&
                            output.layout.totalBufferBytes == pass.mappedBytesI64)
                        val buffer = device.createBuffer(BufferDescriptor(size = staging.byteSize.toULong(),
                            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst, label = "w6a.readback"))
                        readbackBuffer = buffer
                        renderOperands += GPUPreparedNativeScopeOperand.Readback(stepIndex,
                            GPUPreparedNativeTextureOperand(rootTexture, generation),
                            GPUPreparedNativeBufferOperand(buffer, generation, GPUPreparedNativeOperandOwnership.OutputOwnedReadback),
                            GPUPreparedNativeReadbackLayout(0, 0, graph.targetExtent.width, graph.targetExtent.height, pass.bytesPerRow,
                                graph.targetExtent.height, 0L, requireNotNull(pass.mappedBytesI64), GPUTextureFormat.RGBA8UnormSrgb))
                    }
                    is PlanPass.TextureCopy -> {
                        val region = requireNotNull(pass.copySourceBoundsI32())
                        renderOperands += GPUPreparedNativeScopeOperand.Copy(stepIndex, GPUEncoderOperationKind.Copy,
                            GPUPreparedNativeTextureOperand(textures.getValue(pass.source), generation),
                            GPUPreparedNativeTextureOperand(textures.getValue(pass.destination), generation),
                            GPUPreparedNativeTextureCopyLayout(region.left, region.top, pass.copyDestinationOriginI32().x,
                                pass.copyDestinationOriginI32().y, region.width(), region.height()))
                    }
                    else -> error("Unadmitted W6 native pass")
                }
            }
            val keys = encoderPlan.scopes.map { GPUPreparedNativeScopeKey(it.sourceStepIndex, it.operationKind, it.resourceGenerationLabels, it.nativeOperandKeys) }
            val payload = GPUPreparedNativeFramePayload(GPUPreparedNativeFrameIdentity(framePlan.frameId, encoderPlan.contextIdentity,
                encoderPlan.planId, generation, generationSeal.targetGeneration, keys), renderOperands,
                encoderPlan.scopes.map { it.nativeOperandKeys },
                listOf(GPUPreparedNativeAuxiliaryHandle(owned, GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion)) +
                    framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
                        .mapNotNull { frame.destinationCopy(it) }.distinct().map { copy ->
                            GPUPreparedNativeAuxiliaryHandle(GPUW6aDestinationNativeV1(frame, copy, views.getValue(copy.destination)),
                                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion) },
                leaseLifecycle = spatialBinding,
                pathDepthStencilViewAuthority = pathViews)
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(GPUPreparedNativeFrameDraft(payload))
        } catch (failure: Throwable) {
            // If no payload was returned, the preflight binding has not reached the registry;
            // it must release its consumer lease and destroy unsubmitted misses now.
            runCatching { spatialBinding?.releaseBeforeSubmit() }
            runCatching { spatialFilterCache?.discardPrepared(framePlan) }
            val cleanup = AutoCloseable {
                owned.close()
                readbackBuffer?.close()
                readbackBuffer = null
            }
            val retained = if (runCatching { cleanup.close() }.isSuccess) null else cleanup
            return GPUPreparedNativeFramePayloadMaterialization.Refused("w6a.layer.native_materialization",
                "Layer native materialization failed: ${failure.message.orEmpty()}", retainedCloseOwner = retained)
        }
    }

    /**
     * Consumes a sealed terminal source.  The caller provides only plan-published alpha,
     * color-filter binding, blend and destination snapshot facts; this lowers no scene state.
     */
    private fun filteredCompositeRender(
        stepIndex: Int,
        target: GPUTextureView,
        sourceTexture: GPUTextureView,
        generation: GPUDeviceGenerationID,
        sourceSampleOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
        compositeScissorTargetLocalI32: RectI32,
        alpha: Float,
        filter: ColorFilterExecutionPlanV1?,
        filterBuffer: GPUBuffer?,
        filterBufferOffsetI64: Long?,
        filterBindingByteCountI64: Long?,
        filterWordOffsetI64: Long,
        destinationSnapshot: GPUTextureView?,
        blend: BlendPlan,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val destinationRead = blend as? BlendPlan.DestinationReadV1
        require((destinationRead != null) == (destinationSnapshot != null))
        val entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            if (filter != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment,
                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,
                    minBindingSize = requireNotNull(filterBindingByteCountI64).toULong())))
            if (destinationRead != null) add(BindGroupLayoutEntry(2u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
        }
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = entries)))
        val colorDeclaration = filter?.let { execution ->
            "struct W5fMaterialBlock { words: array<vec4<u32>, ${maxOf(1L, (requireNotNull(filterBindingByteCountI64) + 15L) / 16L)}>, }\n" +
                "@group(0) @binding(1) var<uniform> w5fMaterial: W5fMaterialBlock;\n" +
                "fn w6b_terminal_filter(input: vec4<f32>) -> vec4<f32> {\n" +
                W5fColorOperationEmitterV1.emit(execution.copyOperationGraph(), "input", filterWordOffsetI64) + "}\n"
        }.orEmpty()
        val formula = destinationRead?.let { selected ->
            requireNotNull(BlendFormulaProgramV1.selectedBlendFunctionWgsl(selected.mode.name.lowercase(), "w6b_terminal_blend"))
        }.orEmpty()
        val filtered = if (filter == null) "alpha_applied" else "w6b_terminal_filter(alpha_applied)"
        val output = if (destinationRead == null) filtered else
            "w6b_terminal_blend($filtered, textureLoad(destination_snapshot, vec2<i32>(position.xy), 0))"
        val snapshotDeclaration = if (destinationRead == null) "" else
            "@group(0) @binding(2) var destination_snapshot: texture_2d<f32>;"
        val shader = W6A_VERTEX_SHADER + """
            @group(0) @binding(0) var terminal_source: texture_2d<f32>;
            $colorDeclaration
            $snapshotDeclaration
            $formula
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let alpha_applied = textureLoad(terminal_source,
                    vec2<i32>(position.xy) + vec2<i32>(${sourceSampleOffsetTargetLocalI32.x}, ${sourceSampleOffsetTargetLocalI32.y}), 0) * $alpha;
                return $output;
            }
        """
        val pipeline = pipeline(shader, layout, w6aColorTarget(blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, sourceTexture))
            if (filter != null) add(BindGroupEntry(1u, BufferBinding(requireNotNull(filterBuffer),
                requireNotNull(filterBufferOffsetI64).toULong(), requireNotNull(filterBindingByteCountI64).toULong())))
            if (destinationRead != null) add(BindGroupEntry(2u, requireNotNull(destinationSnapshot)))
        })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(compositeScissorTargetLocalI32.left, compositeScissorTargetLocalI32.top,
                    compositeScissorTargetLocalI32.width(), compositeScissorTargetLocalI32.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            operationKindOverride = if (pass is PlanPass.LayerComposite) GPUEncoderOperationKind.LayerComposite else null,
            w6aPassV1 = pass,
        )
    }

    /** Materializes one published graph-texture source and, when present, its frozen W5 filter row. */
    private fun pictureSourceRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        filterBuffer: GPUBuffer?,
        filterBindingByteCountI64: Long?,
        generation: GPUDeviceGenerationID,
        shader: String,
        scissorX: Int,
        scissorY: Int,
        scissorWidth: Int,
        scissorHeight: Int,
        pass: PlanPass.PictureSourcePass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(scissorWidth > 0 && scissorHeight > 0)
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            if (filterBindingByteCountI64 != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment,
                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform, minBindingSize = filterBindingByteCountI64.toULong())))
        })))
        val pipeline = pipeline(shader, layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, source))
            if (filterBindingByteCountI64 != null) add(BindGroupEntry(1u, BufferBinding(requireNotNull(filterBuffer),
                0uL, filterBindingByteCountI64.toULong())))
        })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissorX, scissorY, scissorWidth, scissorHeight),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Rasterizes an already-issued W4 solid-rect lane as raw, unshaded coverage. */
    private fun coverageSolidRectRender(
        stepIndex: Int,
        target: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FullscreenCoverageSolidRectRecipeV1,
        pass: PlanPass.FilterCoverageSourcePass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val binding = requireNotNull(pass.rasterBinding)
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && binding.draw is SolidRectDraw &&
            pass.sealedAlphaSource == null && binding.depthStencil == null && recipe.load == AttachmentLoadPlan.ClearTransparent &&
            recipe.groupZeroAbi == W6FullscreenCoverageSolidRectGroupZeroAbiV1.Empty &&
            recipe.shaderFamily == W6FullscreenCoverageSolidRectShaderFamilyV1.SolidRectCoverageOpaque)
        val scissor = recipe.copyScissorTargetLocalI32()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = emptyList())))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.solidRectCoverageFragment(), layout,
            w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = emptyList())))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(recipe.clearColor.redF32.toDouble(), recipe.clearColor.greenF32.toDouble(),
                    recipe.clearColor.blueF32.toDouble(), recipe.clearColor.alphaF32.toDouble())),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Emits the exact frozen W4 geometry as raw coverage, never as a reconstructed source draw. */
    private fun coverageRasterRender(
        stepIndex: Int,
        target: GPUTextureView,
        depthStencil: GPUTextureView?,
        generation: GPUDeviceGenerationID,
        frame: GPUW6aLayerFramePlan,
        render: GPUFrameStep.RenderPassStep,
        pass: PlanPass.FilterCoverageSourcePass,
        coverageBinding: PlanPass.W6bRasterCoverageBindingV1,
        geometryBuffers: Map<PlanResourceId, GPUBuffer>,
        fallbackUniform: GPUBuffer,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val draw = coverageBinding.draw
        require(draw !is SolidRectDraw && draw !is W5bVerticesDraw) {
            "W6b coverage raster requires one admitted W4 analytic or path producer."
        }
        val data = requireNotNull(coverageBinding.drawDataResources)
        val frozenGeometry = frame.physical.w6bCoverageRasterGeometry(pass.id)
        val frozenHost = frame.physical.w6bCoverageRasterHostRecipe(pass.id)
        val packets = render.drawPackets
        val stencil = coverageBinding.depthStencil != null
        require(packets.size == if (stencil) 2 else 1)
        val commands = buildList {
            packets.forEachIndexed { packetIndexI32, packet ->
                val producer = stencil && packetIndexI32 == 0
                val frozenBundle = frozenGeometry.bundle(packetIndexI32)
                val recipe = frozenHost.bundle(packetIndexI32)
                require(recipe.geometry === frozenBundle)
                val mapped = requireNotNull(frame.coverageRasterPipeline(packet)) {
                    "W6b coverage raster requires a mapped frozen W4 pipeline."
                }
                val template = frame.template(packet)
                val layout = owned.own(device.createBindGroupLayout(
                    corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity),
                ))
                val pipeline = if (producer) geometryPipeline(mapped, layout, owned, template)
                    else coverageGeometryPipeline(mapped, layout, requireNotNull(template), owned)
                val uniformPayload = frame.analyticUniform(packet)
                require(uniformPayload.contentEquals(w6bCoverageUniformBytes(recipe)))
                val nativeUniform = geometryBuffers[data.uniform] ?: fallbackUniform
                val vertex = frozenBundle.vertexWindow
                val index = frozenBundle.indexWindow
                val uniform = frozenBundle.uniformWindow
                require(vertex.resourceId == data.vertex && index.resourceId == data.index && uniform.resourceId == data.uniform &&
                    uniform.usefulBytesI64 == uniformPayload.size.toLong())
                queue.writeBuffer(geometryBuffers.getValue(vertex.resourceId), vertex.offsetBytesI64.toULong(), ArrayBuffer.of(frozenBundle.mesh.copyPositionsF32()))
                queue.writeBuffer(geometryBuffers.getValue(index.resourceId), index.offsetBytesI64.toULong(), ArrayBuffer.of(requireNotNull(frozenBundle.mesh.copyIndicesI32())))
                queue.writeBuffer(nativeUniform, uniform.offsetBytesI64.toULong(), ArrayBuffer.of(uniformPayload))
                val bind = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
                    BindGroupEntry(0u, BufferBinding(nativeUniform, 0uL, uniformPayload.size.toULong())),
                ))))
                val scissor = recipe.copyScissorI32()
                add(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)))
                add(GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(bind, generation),
                    listOf(uniform.offsetBytesI64)))
                add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0,
                    GPUPreparedNativeBufferOperand(geometryBuffers.getValue(vertex.resourceId), generation), vertex.offsetBytesI64, vertex.usefulBytesI64, 8L))
                add(GPUPreparedNativeRenderCommand.SetIndexBuffer(
                    GPUPreparedNativeBufferOperand(geometryBuffers.getValue(index.resourceId), generation),
                    GPUPreparedNativeIndexFormat.Uint32, index.offsetBytesI64, index.usefulBytesI64))
                add(GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()))
                add(GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(
                    indexCount = frozenBundle.indexCountI32, firstIndex = 0, baseVertex = 0,
                    vertexCount = frozenBundle.vertexCountI32, maxLocalIndex = frozenBundle.maxIndexI32,
                )))
            }
        }
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                depthStencilTarget = depthStencil?.let { GPUPreparedNativeTextureViewOperand(it, generation) },
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                depthReadOnly = true,
                stencilReadOnly = depthStencil == null,
                stencilClearValue = if (depthStencil == null) null else 0u,
                stencilLoadOperation = depthStencil?.let { GPUPreparedNativeLoadOperation.Clear },
                stencilStoreOperation = depthStencil?.let { GPUPreparedNativeStoreOperation.Store },
            ),
            commands,
            // These are the published W4 packets that the coverage operand consumes.
            // Retain the exact instances so prepared-surface validation observes the
            // same packet order as the frozen plan rather than a renderer-side proxy.
            render.drawPackets.map { requireNotNull(it.semanticPayload) },
            w6aPassV1 = pass,
        )
    }

    /** Applies the plan-owned 256-byte LUT before material/source blending. */
    private fun maskTableCoverageRender(
        stepIndex: Int,
        target: GPUTextureView,
        coverage: GPUTextureView,
        table: GPUBuffer,
        generation: GPUDeviceGenerationID,
        recipe: W6FilterMaskTableRecipeV1,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
            BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, buffer = BufferBindingLayout(
                type = GPUBufferBindingType.ReadOnlyStorage, minBindingSize = recipe.tableRangeBytesI64.toULong())),
        ))))
        require(recipe.groupZeroAbi == W6FilterMaskTableGroupZeroAbiV1.CoverageTextureThenTableStorage &&
            recipe.shaderFamily == W6FilterMaskTableShaderFamilyV1.CoverageLookupStorageU32 &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val offset = recipe.copyOutputToCoverageOffsetTargetLocalI32()
        val extent = recipe.copyExtent()
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskTableCoverageFragment(
            offset.x, offset.y,
        ), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout,
            entries = listOf(BindGroupEntry(0u, coverage), BindGroupEntry(1u, BufferBinding(table,
                recipe.tableOffsetBytesI64.toULong(), recipe.tableRangeBytesI64.toULong()))))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Mechanical IIf1 translator: every native operand comes from the catalogued recipe. */
    private fun maskShaderRender(
        stepIndex: Int,
        target: GPUTextureView,
        coverage: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FilterMaskShaderRecipeV1,
        material: GPUW6bMaskShaderMaterialV1,
        uniform: GPUBuffer,
        resources: Map<Int, GPUW6bMaskShaderResourceV1>,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val stage = material.stage
        require(recipe.ownerPassId == pass.id && recipe.occurrenceIdI32 == material.binding.occurrenceIdI32 &&
            recipe.material == material.binding.material && recipe.uniformResource == material.binding.uniformResource &&
            recipe.uniformOffsetBytesI64 == material.binding.uniformOffsetBytesI64 &&
            recipe.uniformCapacityBytesI64 == material.binding.uniformCapacityBytesI64 &&
            recipe.materialStructuralId == stage.structuralId && recipe.materialCanonicalIdentity == stage.canonicalIdentity &&
            recipe.materialUniformByteCountI64 == stage.uniformByteCountI64 &&
            recipe.bindingManifest().map { it.bindingI32 to it.kind.name } == stage.bindingManifest.map { it.bindingI32 to when (it.resourceKind) {
                "uniformBuffer" -> W6FilterMaskShaderBindingKindV1.UniformBuffer.name
                "storageBuffer" -> W6FilterMaskShaderBindingKindV1.StorageBuffer.name
                "sampledTexture" -> W6FilterMaskShaderBindingKindV1.SampledTexture.name
                "sampler" -> W6FilterMaskShaderBindingKindV1.Sampler.name
                else -> error("W6 MaskShader stage has an unsupported binding kind.")
            } } &&
            recipe.copyMaterialDeviceOriginI32() == material.binding.materialDeviceOriginI32 &&
            recipe.groupZeroAbi == W6FilterMaskShaderGroupZeroAbiV1.CoverageThenFrozenW5Material &&
            recipe.shaderFamily == W6FilterMaskShaderFamilyV1.FrozenW5MaterialCoverageAlpha &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store) {
            "W6 MaskShader recipe and W5 material ABI differ."
        }
        val offset = recipe.copyOutputToCoverageOffsetTargetLocalI32()
        val origin = recipe.copyMaterialDeviceOriginI32()
        val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries =
            listOf(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout())) +
            stage.bindingManifest.map { binding -> when (binding.resourceKind) {
                "uniformBuffer" -> BindGroupLayoutEntry((binding.bindingI32 + 1).toUInt(), GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,
                        minBindingSize = stage.uniformByteCountI64.toULong()))
                "storageBuffer" -> BindGroupLayoutEntry((binding.bindingI32 + 1).toUInt(), GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(type = GPUBufferBindingType.ReadOnlyStorage,
                        minBindingSize = when (val resource = resources.getValue(binding.bindingI32)) {
                            is GPUW6bMaskShaderResourceV1.Buffer -> resource.byteSizeI64.toULong()
                            is GPUW6bMaskShaderResourceV1.Runtime -> requireNotNull(binding.composedResource?.buffer)
                                .minBindingSizeBytesI64.toULong()
                            else -> error("W6b MaskShader storage binding is not backed by its frozen W5 resource.")
                        }))
                "sampledTexture" -> BindGroupLayoutEntry((binding.bindingI32 + 1).toUInt(), GPUShaderStage.Fragment,
                    texture = TextureBindingLayout(sampleType = GPUTextureSampleType.Float,
                        viewDimension = GPUTextureViewDimension.TwoD, multisampled = false))
                "sampler" -> BindGroupLayoutEntry((binding.bindingI32 + 1).toUInt(), GPUShaderStage.Fragment,
                    sampler = SamplerBindingLayout(type = if (requireNotNull(binding.composedResource?.sampler).samplerTypeTagU32 == 1u)
                        GPUSamplerBindingType.Filtering else GPUSamplerBindingType.NonFiltering))
                else -> error("W6b MaskShader has an unadmitted frozen W5 resource kind ${binding.resourceKind}.")
            } })))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskShaderCoverageFragment(
            stage.bindingManifest.fold(stage.declarationsWgsl) { declarations, binding ->
                declarations.replace("@group(1) @binding(${binding.bindingI32})",
                    "@group(0) @binding(${binding.bindingI32 + 1})")
            }, material.sourceInputWgsl, offset.x, offset.y, origin.x, origin.y,
        ), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout,
            entries = listOf(BindGroupEntry(0u, coverage)) + stage.bindingManifest.map { binding -> when (binding.resourceKind) {
                "uniformBuffer" -> BindGroupEntry((binding.bindingI32 + 1).toUInt(), BufferBinding(uniform,
                    material.binding.uniformOffsetBytesI64.toULong(),
                    stage.uniformByteCountI64.toULong()))
                "storageBuffer" -> when (val resource = resources.getValue(binding.bindingI32)) {
                    is GPUW6bMaskShaderResourceV1.Buffer -> BindGroupEntry((binding.bindingI32 + 1).toUInt(),
                        BufferBinding(resource.value, 0uL, resource.byteSizeI64.toULong()))
                    is GPUW6bMaskShaderResourceV1.Runtime -> resource.lease.binding(binding.bindingI32 + 1)
                    else -> error("W6b MaskShader storage binding is not backed by its frozen W5 resource.")
                }
                "sampledTexture" -> GPUW5eImageNativeV1.binding((resources.getValue(binding.bindingI32)
                    as? GPUW6bMaskShaderResourceV1.Image)?.lease
                    ?: error("W6b MaskShader texture binding is not backed by its frozen W5 image."),
                    (binding.bindingI32 + 1).toUInt())
                "sampler" -> (resources.getValue(binding.bindingI32) as? GPUW6bMaskShaderResourceV1.Runtime)
                    ?.lease?.binding(binding.bindingI32 + 1)
                    ?: error("W6b MaskShader sampler binding is not backed by its frozen W5 sampler.")
                else -> error("W6b MaskShader has an unadmitted frozen W5 resource kind ${binding.resourceKind}.")
            } })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Applies one frozen mask style to its blurred input and optional original coverage. */
    private fun maskStyleRender(
        stepIndex: Int,
        target: GPUTextureView,
        blurred: GPUTextureView,
        original: GPUTextureView?,
        generation: GPUDeviceGenerationID,
        style: org.graphiks.kanvas.render.ir.MaskBlurStyle,
        outputToBlurredOffsetTargetLocalXI32: Int,
        outputToBlurredOffsetTargetLocalYI32: Int,
        outputToOriginalOffsetTargetLocalXI32: Int?,
        outputToOriginalOffsetTargetLocalYI32: Int?,
        widthI32: Int,
        heightI32: Int,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require((style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL) == (original == null))
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            if (original != null) add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
        })))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskStyleFragment(
            style, outputToBlurredOffsetTargetLocalXI32, outputToBlurredOffsetTargetLocalYI32,
            outputToOriginalOffsetTargetLocalXI32, outputToOriginalOffsetTargetLocalYI32,
        ), layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, blurred))
            original?.let { add(BindGroupEntry(1u, it)) }
        })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, widthI32, heightI32),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Mechanical IIe2a translation: NORMAL has exactly one blurred-coverage binding. */
    private fun maskBlurNormalRender(
        stepIndex: Int, target: GPUTextureView, blurred: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FilterMaskBlurNormalRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && recipe.blurredSource == pass.inputs().single() &&
            recipe.groupZeroAbi == W6FilterMaskBlurNormalGroupZeroAbiV1.BlurredCoverageTexture &&
            recipe.style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL && recipe.shaderFamily == W6FilterMaskBlurNormalShaderFamilyV1.BlurredCoverageTextureLoad &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val offset = recipe.copyOutputToBlurredOffsetTargetLocalI32(); val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskStyleFragment(
            org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL, offset.x, offset.y, null, null,
        ), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, blurred)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))),
            w6aPassV1 = pass)
    }

    /** Mechanical IIe2b translation preserves the frozen blurred-first/original-second ABI. */
    private fun maskBlurDualSourceRender(
        stepIndex: Int, target: GPUTextureView, blurred: GPUTextureView, original: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FilterMaskBlurDualSourceRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && pass.inputs() == listOf(recipe.blurredSource, recipe.originalSource) &&
            recipe.style in setOf(org.graphiks.kanvas.render.ir.MaskBlurStyle.SOLID, org.graphiks.kanvas.render.ir.MaskBlurStyle.OUTER, org.graphiks.kanvas.render.ir.MaskBlurStyle.INNER) &&
            recipe.groupZeroAbi == W6FilterMaskBlurDualSourceGroupZeroAbiV1.BlurredThenOriginalCoverageTextures && recipe.shaderFamily == W6FilterMaskBlurDualSourceShaderFamilyV1.BlurredThenOriginalCoverageTextureLoad && recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val blurredOffset = recipe.copyOutputToBlurredOffsetTargetLocalI32(); val originalOffset = recipe.copyOutputToOriginalOffsetTargetLocalI32(); val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()), BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskStyleFragment(recipe.style, blurredOffset.x, blurredOffset.y, originalOffset.x, originalOffset.y), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, blurred), BindGroupEntry(1u, original)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex, GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation), loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)), listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)), GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)), GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height), GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))), w6aPassV1 = pass)
    }

    /** Applies exactly one frozen mask to an already materialized source texture. */
    private fun maskedMaterialSourceRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        coverage: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FilterMaterializedSourceRecipeV1,
        pass: PlanPass.FilterPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
            BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && pass.inputs() == listOf(recipe.source, recipe.coverage) && recipe.groupZeroAbi == W6FilterMaterializedSourceGroupZeroAbiV1.SourceThenCoverageTextures && recipe.shaderFamily == W6FilterMaterializedSourceShaderFamilyV1.SourceAndCoverageTextureLoad && recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val sourceOffset = recipe.copyOutputToSourceOffsetTargetLocalI32(); val coverageOffset = recipe.copyOutputToCoverageOffsetTargetLocalI32(); val extent = recipe.copyExtent()
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.maskedMaterialSourceFragment(sourceOffset.x, sourceOffset.y, coverageOffset.x, coverageOffset.y, recipe.alphaMode == W6FilterMaterializedSourceAlphaModeV1.ReplaceSourceAlpha), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, source), BindGroupEntry(1u, coverage),
        ))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Mechanical IIc translation: the sealed W5f ColorFilter recipe is the native authority. */
    private fun colorFilterRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        uniform: GPUBuffer,
        generation: GPUDeviceGenerationID,
        recipe: W6FilterColorFilterRecipeV1,
        pass: PlanPass.FilterPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.groupZeroAbi == W6FilterColorFilterGroupZeroAbiV1.TextureAndW5fUniform && recipe.shaderFamily == W6FilterColorFilterShaderFamilyV1.W5fColorOperationTextureLoad && recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val offset = recipe.copyOutputToInputOffsetTargetLocalI32()
        val wordsI32 = Math.toIntExact(recipe.uniformCapacityBytesI64 / 16L)
        val extent = recipe.copyExtent()
        val shader = W6A_VERTEX_SHADER + """
            @group(0) @binding(0) var w6c_color_source: texture_2d<f32>;
            struct W5fMaterial { words: array<vec4<u32>, $wordsI32>, }
            @group(0) @binding(1) var<uniform> w5fMaterial: W5fMaterial;
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let source_position = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});
                let source_extent = vec2<i32>(textureDimensions(w6c_color_source));
                if (source_position.x < 0 || source_position.y < 0 || source_position.x >= source_extent.x || source_position.y >= source_extent.y) {
                    return vec4<f32>(0.0);
                }
                let input = textureLoad(w6c_color_source, source_position, 0);
                ${W5fColorOperationEmitterV1.emit(recipe.execution.copyOperationGraph(), "input", recipe.uniformOffsetBytesI64 / 4L)}
            }
        """
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
            BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, buffer = BufferBindingLayout(
                type = GPUBufferBindingType.Uniform, minBindingSize = recipe.uniformCapacityBytesI64.toULong())),
        ))))
        val pipeline = pipeline(shader, layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, source), BindGroupEntry(1u, BufferBinding(uniform, 0uL,
                recipe.uniformCapacityBytesI64.toULong())),
        ))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ), w6aPassV1 = pass)
    }

    /** Mechanical Ic1 translation of the frozen layer-input PictureSource recipe. */
    private fun pictureSourceLayerRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FullscreenPictureSourceLayerRecipeV1,
        pass: PlanPass.PictureSourcePass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val sampling = requireNotNull(pass.sourceSampling)
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && recipe.source == pass.layerInput &&
            pass.graphTextureOperand == null && recipe.load == AttachmentLoadPlan.ClearTransparent &&
            recipe.store == AttachmentStorePlan.Store && recipe.targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL &&
            recipe.sampleCountI32 == 1 && recipe.topology == W6FullscreenEmptyTopologyV1.FullscreenTriangle &&
            recipe.groupZeroAbi == W6FullscreenPictureSourceLayerGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FullscreenPictureSourceLayerShaderFamilyV1.SampledLayerTextureLoad &&
            recipe.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32())
        val offset = recipe.copyOutputToInputOffsetTargetLocalI32()
        val scissor = recipe.copyScissorTargetLocalI32()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(sampledCompositeShader(offset.x, offset.y, 1f), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, source),
        ))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(recipe.clearColor.redF32.toDouble(), recipe.clearColor.greenF32.toDouble(),
                    recipe.clearColor.blueF32.toDouble(), recipe.clearColor.alphaF32.toDouble()),
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Mechanical Ic2 translation of the frozen graph-texture PictureSource recipe. */
    private fun pictureSourceGraphRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FullscreenPictureSourceGraphRecipeV1,
        pass: PlanPass.PictureSourcePass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val operand = requireNotNull(pass.graphTextureOperand)
        val sampling = requireNotNull(pass.sourceSampling)
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && recipe.source == operand.sealedSourceId &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store &&
            recipe.targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && recipe.sampleCountI32 == 1 &&
            recipe.sourceFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && recipe.sourceSampleCountI32 == 1 &&
            recipe.topology == W6FullscreenEmptyTopologyV1.FullscreenTriangle &&
            recipe.groupZeroAbi == W6FullscreenPictureSourceGraphGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FullscreenPictureSourceGraphShaderFamilyV1.SampledGraphTextureLoad &&
            recipe.copyOutputToInputOffsetTargetLocalI32() == sampling.copyOutputToInputOffsetTargetLocalI32())
        val offset = recipe.copyOutputToInputOffsetTargetLocalI32(); val scissor = recipe.copyScissorTargetLocalI32()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(sampledCompositeShader(offset.x, offset.y, 1f), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(recipe.clearColor.redF32.toDouble(), recipe.clearColor.greenF32.toDouble(),
                    recipe.clearColor.blueF32.toDouble(), recipe.clearColor.alphaF32.toDouble())),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))), w6aPassV1 = pass)
    }

    /** Mechanical IIa1 translation: every native selection is carried by the frozen Crop recipe. */
    private fun spatialCropRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FilterSpatialCropRecipeV1,
        pass: PlanPass.FilterPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.groupZeroAbi == W6FilterSpatialCropGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FilterSpatialCropShaderFamilyV1.TargetLocalCropTextureLoad &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + GPUW6cSpatialSamplingPass.fragment(recipe), layout,
            w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ), w6aPassV1 = pass)
    }

    /** Mechanical IIa2a translation: the sealed Offset recipe is the sole native authority. */
    private fun spatialOffsetRender(
        stepIndex: Int, target: GPUTextureView, source: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FilterSpatialOffsetRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.groupZeroAbi == W6FilterSpatialOffsetGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FilterSpatialOffsetShaderFamilyV1.TargetLocalOffsetDecalTextureLoad &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + GPUW6cSpatialSamplingPass.fragment(recipe), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation), loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))), w6aPassV1 = pass)
    }

    /** Mechanical IIa2b translation: periodic REPEAT is selected only by the frozen Tile recipe. */
    private fun spatialTileRender(
        stepIndex: Int, target: GPUTextureView, source: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FilterSpatialTileRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.groupZeroAbi == W6FilterSpatialTileGroupZeroAbiV1.Texture && recipe.shaderFamily == W6FilterSpatialTileShaderFamilyV1.TargetLocalTileRepeatTextureLoad && recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout())))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + GPUW6cSpatialSamplingPass.fragment(recipe), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation), loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)), GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)), GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height), GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))), w6aPassV1 = pass)
    }

    /** Mechanical IIb translation: native WGSL consumes only the sealed Morphology recipe. */
    private fun morphologyRender(
        stepIndex: Int, target: GPUTextureView, source: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FilterMorphologyRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.groupZeroAbi == W6FilterMorphologyGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FilterMorphologyShaderFamilyV1.TargetLocalSeparableTextureLoad &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout())))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + GPUW6cMorphologyPass.fragment(recipe), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation), loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)), GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)), GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height), GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))), w6aPassV1 = pass)
    }

    /** IIe1 translation reads every native selection from the sealed blur recipe. */
    private fun separableBlurRender(stepIndex: Int, target: GPUTextureView, source: GPUTextureView,
        generation: GPUDeviceGenerationID, shader: String, recipe: W6FilterSeparableBlurRecipeV1,
        pass: PlanPass.FilterPass, owned: W6aOwnedHandles): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && recipe.source == pass.inputs().single() &&
            recipe.groupZeroAbi == W6FilterSeparableBlurGroupZeroAbiV1.SourceTexture &&
            recipe.shaderFamily == W6FilterSeparableBlurShaderFamilyV1.GaussianTextureLoad &&
            recipe.store == AttachmentStorePlan.Store)
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout())))))
        val pipeline = pipeline(shader, layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        val extent = recipe.copyExtent()
        return GPUPreparedNativeScopeOperand.Render(stepIndex, GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
            loadOperation = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
            clearColor = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null), listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
            GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
        ), w6aPassV1 = pass)
    }

    private fun textureRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        generation: GPUDeviceGenerationID,
        shader: String,
        blend: BlendPlan,
        scissorX: Int,
        scissorY: Int,
        scissorWidth: Int,
        scissorHeight: Int,
        pass: PlanPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(scissorWidth > 0 && scissorHeight > 0)
        // A freshly allocated W6b source or filter target must begin transparent.
        // These fullscreen shaders intentionally emit transparent pixels outside the
        // frozen source domain; with source-over blend, loading uninitialized target
        // memory would otherwise preserve those undefined pixels.
        val clearTarget = pass is PlanPass.PictureSourcePass || pass is PlanPass.FilterPass
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(shader, layout, w6aColorTarget(blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, source),
        ))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = if (clearTarget) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                clearColor = if (clearTarget) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissorX, scissorY, scissorWidth, scissorHeight),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ),
            operationKindOverride = if (pass is PlanPass.LayerComposite) GPUEncoderOperationKind.LayerComposite else null,
            w6aPassV1 = pass,
        )
    }

    /** Mechanical Ib1 translation: texture-only group zero, Load, and planner-sealed target-local offset. */
    private fun coverageAlphaRender(
        stepIndex: Int,
        target: GPUTextureView,
        source: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FullscreenCoverageAlphaRecipeV1,
        pass: PlanPass.FilterCoverageSourcePass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && recipe.load == AttachmentLoadPlan.Load &&
            recipe.groupZeroAbi == W6FullscreenCoverageAlphaGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FullscreenCoverageAlphaShaderFamilyV1.AlphaCoverageTextureLoad)
        val alpha = requireNotNull(pass.sealedAlphaSource)
        val sampling = requireNotNull(pass.sealedAlphaSampling)
        val offset = recipe.copyOutputToInputOffsetTargetLocalI32()
        require(recipe.source == alpha.sealedSourceId && recipe.sourceGenerationI64 == alpha.sealedSourceGenerationI64 &&
            recipe.copySourceSampleBoundsTargetI32() == alpha.copySampleBoundsTargetI32() &&
            offset == sampling.copyOutputToInputOffsetTargetLocalI32())
        val extent = recipe.copyExtent()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(W6A_VERTEX_SHADER + W6bMaskCoverageSnippet.alphaCoverageFragment(offset.x, offset.y),
            layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Load),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ), w6aPassV1 = pass)
    }

    private fun coverageRetainRender(
        stepIndex: Int, target: GPUTextureView, source: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FullscreenCoverageRetainRecipeV1, pass: PlanPass.FilterCoverageRetainPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        val sampling = requireNotNull(pass.sampling)
        val offset = recipe.copyOutputToInputOffsetTargetLocalI32(); val extent = recipe.copyExtent(); val scissor = recipe.copyScissorTargetLocalI32()
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && recipe.source == pass.source &&
            recipe.load == AttachmentLoadPlan.Load && recipe.groupZeroAbi == W6FullscreenCoverageRetainGroupZeroAbiV1.Texture &&
            recipe.shaderFamily == W6FullscreenCoverageRetainShaderFamilyV1.SampledCoverageTextureLoad &&
            offset == sampling.copyOutputToInputOffsetTargetLocalI32() && recipe.copySourceKnownContentTargetLocalI32() == sampling.copyKnownContentInputTargetLocalI32())
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout())))))
        val pipeline = pipeline(sampledCompositeShader(offset.x, offset.y, 1f), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(BindGroupEntry(0u, source)))))
        return GPUPreparedNativeScopeOperand.Render(stepIndex, GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation), loadOperation = GPUPreparedNativeLoadOperation.Load),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32))), w6aPassV1 = pass)
    }

    /** Mechanical IId1 translation: all native choices come from the sealed Merge recipe. */
    private fun mergeRender(stepIndex: Int, target: GPUTextureView, sources: List<GPUTextureView>, generation: GPUDeviceGenerationID,
        recipe: W6FilterMergeRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles): GPUPreparedNativeScopeOperand.Render {
        require(sources.size == recipe.inputs().size && recipe.ownerPassId == pass.id &&
            recipe.groupZeroAbi == W6FilterMergeGroupZeroAbiV1.OrderedTextures &&
            recipe.shaderFamily == W6FilterMergeShaderFamilyV1.OrderedSourceOverTextureLoad &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = sources.indices.map { BindGroupLayoutEntry(it.toUInt(), GPUShaderStage.Fragment, texture = TextureBindingLayout()) })))
        val pipeline = pipeline(W6A_VERTEX_SHADER + GPUW6cMultiInputPass.mergeFragment(recipe), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = sources.mapIndexed { index, source -> BindGroupEntry(index.toUInt(), source) })))
        return GPUPreparedNativeScopeOperand.Render(stepIndex, GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation), loadOperation = GPUPreparedNativeLoadOperation.Clear, clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)), listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, recipe.copyExtent().width, recipe.copyExtent().height),
            GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
        ), w6aPassV1 = pass)
    }

    /** Mechanical IId2 translation: the Blend recipe owns target, ABI, shader, load/store and draw. */
    private fun blendRender(stepIndex: Int, target: GPUTextureView, sources: List<GPUTextureView>, generation: GPUDeviceGenerationID,
        recipe: W6FilterBlendRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles): GPUPreparedNativeScopeOperand.Render {
        require(sources.size == 2 && recipe.ownerPassId == pass.id && recipe.target == pass.output &&
            recipe.groupZeroAbi == W6FilterBlendGroupZeroAbiV1.BackgroundAndForegroundTextures &&
            recipe.shaderFamily == W6FilterBlendShaderFamilyV1.FrozenW5BlendFormulaTextureLoad &&
            recipe.blendFormulaWgsl == frozenW6FilterBlendFormulaWgslV1(recipe.blend, recipe.formula) &&
            recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = sources.indices.map {
            BindGroupLayoutEntry(it.toUInt(), GPUShaderStage.Fragment, texture = TextureBindingLayout())
        })))
        // The formula has already applied recipe.blend in the fragment output.  The clear target
        // therefore retains the historical source-over color target without blending it twice.
        val pipeline = pipeline(W6A_VERTEX_SHADER + GPUW6cMultiInputPass.blendFragment(recipe), layout,
            w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout,
            entries = sources.mapIndexed { index, source -> BindGroupEntry(index.toUInt(), source) })))
        val extent = recipe.copyExtent()
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, extent.width, extent.height),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ), w6aPassV1 = pass)
    }

    /** Binds the non-Blend FilterPass input list positionally. */
    private fun multiInputRender(
        stepIndex: Int,
        target: GPUTextureView,
        sources: List<GPUTextureView>,
        generation: GPUDeviceGenerationID,
        shader: String,
        widthI32: Int,
        heightI32: Int,
        pass: PlanPass.FilterPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(sources.isNotEmpty())
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = sources.indices.map { indexI32 ->
            BindGroupLayoutEntry(indexI32.toUInt(), GPUShaderStage.Fragment, texture = TextureBindingLayout())
        })))
        val pipeline = pipeline(shader, layout, w6aColorTarget(BlendPlan.LegacySrcOverV1), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = sources.mapIndexed { indexI32, source ->
            BindGroupEntry(indexI32.toUInt(), source)
        })))
        return GPUPreparedNativeScopeOperand.Render(stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, widthI32, heightI32),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3, 1, 0, 0)),
            ), w6aPassV1 = pass)
    }

    private fun emptyRender(
        stepIndex: Int,
        target: GPUTextureView,
        generation: GPUDeviceGenerationID,
        pass: PlanPass,
        recipe: W6FullscreenEmptyRecipeV1,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.inputs().isEmpty() &&
            recipe.topology == W6FullscreenEmptyTopologyV1.FullscreenTriangle &&
            recipe.groupZeroAbi == W6FullscreenEmptyGroupZeroAbiV1.Empty)
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = emptyList())))
        val pipeline = pipeline(W6A_VERTEX_SHADER + """
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                return vec4<f32>(0.0);
            }
        """, layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = emptyList())))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                clearColor = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeClearColor(
                    recipe.clearColor.redF32.toDouble(), recipe.clearColor.greenF32.toDouble(),
                    recipe.clearColor.blueF32.toDouble(), recipe.clearColor.alphaF32.toDouble()) else null,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32,
                    recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    private fun sampledCompositeShader(sourceOffsetTargetLocalXI32: Int, sourceOffsetTargetLocalYI32: Int, alpha: Float): String = W6A_VERTEX_SHADER + """
        @group(0) @binding(0) var w6b_source: texture_2d<f32>;
        @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
            let source_position = vec2<i32>(position.xy) + vec2<i32>($sourceOffsetTargetLocalXI32, $sourceOffsetTargetLocalYI32);
            let source_extent = vec2<i32>(textureDimensions(w6b_source));
            if (source_position.x < 0 || source_position.y < 0 || source_position.x >= source_extent.x || source_position.y >= source_extent.y) {
                return vec4<f32>(0.0);
            }
            return textureLoad(w6b_source, source_position, 0) * $alpha;
        }
    """

    /** Mechanical IIg1 translation: the planner-owned recipe is the complete native authority. */
    private fun dropShadowColorizeRender(
        stepIndex: Int, target: GPUTextureView, blurredSource: GPUTextureView, generation: GPUDeviceGenerationID,
        recipe: W6FilterDropShadowColorizeRecipeV1, pass: PlanPass.FilterPass, owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output && pass.inputs() == listOf(recipe.blurredSource) && recipe.groupZeroAbi == W6FilterDropShadowColorizeGroupZeroAbiV1.BlurredAlphaTexture && recipe.shaderFamily == W6FilterDropShadowColorizeShaderFamilyV1.LinearDecalBlurredAlphaColorize && recipe.load == AttachmentLoadPlan.ClearTransparent && recipe.store == AttachmentStorePlan.Store)
        val scissor = recipe.copyScissorTargetLocalI32()
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = listOf(
            BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()),
        ))))
        val pipeline = pipeline(dropShadowColorizeShader(recipe), layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = listOf(
            BindGroupEntry(0u, blurredSource),
        ))))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                clearColor = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    /** Colors the already-blurred alpha using only the frozen recipe payload. */
    private fun dropShadowColorizeShader(recipe: W6FilterDropShadowColorizeRecipeV1): String {
        val sourceOffset = recipe.copySourceCoordinateOffsetTargetLocalF64()
        val sourceFootprint = recipe.copySourceFootprintTargetLocalI32()
        val outputFootprint = recipe.copyOutputFootprintTargetLocalI32()
        val color = recipe.colorArgbU32
        val alpha = ((color shr 24) and 0xffu).toInt()
        val red = ((color shr 16) and 0xffu).toInt()
        val green = ((color shr 8) and 0xffu).toInt()
        val blue = (color and 0xffu).toInt()
        return W6A_VERTEX_SHADER + """
            @group(0) @binding(0) var w6b_shadow_blur: texture_2d<f32>;
            fn w6b_shadow_decal(coordinate: vec2<i32>) -> vec4<f32> {
                let extent = vec2<i32>(${sourceFootprint.width()}, ${sourceFootprint.height()});
                if (coordinate.x < 0 || coordinate.y < 0 || coordinate.x >= extent.x || coordinate.y >= extent.y) {
                    return vec4<f32>(0.0);
                }
                return textureLoad(w6b_shadow_blur, coordinate, 0);
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let output_extent = vec2<i32>(${outputFootprint.width()}, ${outputFootprint.height()});
                if (i32(position.x) < 0 || i32(position.y) < 0 || i32(position.x) >= output_extent.x || i32(position.y) >= output_extent.y) {
                    return vec4<f32>(0.0);
                }
                let coordinate = position.xy + vec2<f32>(${sourceOffset.x}f, ${sourceOffset.y}f);
                let lower = vec2<i32>(floor(coordinate));
                let fraction = coordinate - vec2<f32>(lower);
                let top = mix(w6b_shadow_decal(lower), w6b_shadow_decal(lower + vec2<i32>(1, 0)), fraction.x);
                let bottom = mix(w6b_shadow_decal(lower + vec2<i32>(0, 1)),
                    w6b_shadow_decal(lower + vec2<i32>(1, 1)), fraction.x);
                let alpha = mix(top, bottom, fraction.y).a * ${alpha / 255f}f;
                let color_encoded = vec3<f32>(${red / 255f}f, ${green / 255f}f, ${blue / 255f}f);
                let color_linear = select(
                    pow((color_encoded + vec3<f32>(0.055)) / vec3<f32>(1.055), vec3<f32>(2.4)),
                    color_encoded / vec3<f32>(12.92),
                    color_encoded <= vec3<f32>(0.04045));
                return vec4<f32>(color_linear * alpha, alpha);
            }
        """
    }

    /** Mechanical IIg2 translation of the planner-owned, ordered shadow/original recipe. */
    private fun dropShadowCompositeRender(
        stepIndex: Int,
        target: GPUTextureView,
        shadow: GPUTextureView,
        original: GPUTextureView,
        generation: GPUDeviceGenerationID,
        recipe: W6FilterDropShadowCompositeRecipeV1,
        pass: PlanPass.FilterPass,
        owned: W6aOwnedHandles,
    ): GPUPreparedNativeScopeOperand.Render {
        require(recipe.ownerPassId == pass.id && recipe.target == pass.output &&
            pass.inputs() == listOf(recipe.colorizedShadow, recipe.originalSource) &&
            recipe.mode == CapturedDropShadowModeV1.COMPOSITE &&
            recipe.groupZeroAbi == W6FilterDropShadowCompositeGroupZeroAbiV1.ColorizedShadowThenOriginalTextures &&
            recipe.shaderFamily == W6FilterDropShadowCompositeShaderFamilyV1.ShadowThenOriginalSrcOverTextureLoad &&
            recipe.store == AttachmentStorePlan.Store)
        val shadowOffset = recipe.copyShadowOffsetTargetLocalI32()
        val originalOffset = recipe.copyOriginalOffsetTargetLocalI32()
        val shader = W6A_VERTEX_SHADER + """
            @group(0) @binding(0) var w6b_shadow_color: texture_2d<f32>;
            @group(0) @binding(1) var w6b_shadow_original: texture_2d<f32>;
            fn w6b_shadow_sample(source: texture_2d<f32>, coordinate: vec2<i32>) -> vec4<f32> {
                let extent = vec2<i32>(textureDimensions(source));
                if (coordinate.x < 0 || coordinate.y < 0 || coordinate.x >= extent.x || coordinate.y >= extent.y) {
                    return vec4<f32>(0.0);
                }
                return textureLoad(source, coordinate, 0);
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let colored = w6b_shadow_sample(w6b_shadow_color, vec2<i32>(position.xy) +
                    vec2<i32>(${shadowOffset.x}, ${shadowOffset.y}));
                let source = w6b_shadow_sample(w6b_shadow_original, vec2<i32>(position.xy) +
                    vec2<i32>(${originalOffset.x}, ${originalOffset.y}));
                return source + colored * (1.0 - source.a);
            }
        """
        val layout = owned.own(device.createBindGroupLayout(BindGroupLayoutDescriptor(entries = buildList {
            add(BindGroupLayoutEntry(0u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
            add(BindGroupLayoutEntry(1u, GPUShaderStage.Fragment, texture = TextureBindingLayout()))
        })))
        val pipeline = pipeline(shader, layout, w6aColorTarget(recipe.blend), owned)
        val group = owned.own(device.createBindGroup(BindGroupDescriptor(layout = layout, entries = buildList {
            add(BindGroupEntry(0u, shadow))
            add(BindGroupEntry(1u, original))
        })))
        return GPUPreparedNativeScopeOperand.Render(
            stepIndex,
            GPUPreparedNativeRenderPassConfig(GPUPreparedNativeTextureViewOperand(target, generation),
                loadOperation = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeLoadOperation.Clear else GPUPreparedNativeLoadOperation.Load,
                clearColor = if (recipe.load == AttachmentLoadPlan.ClearTransparent) GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0) else null),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(GPUPreparedNativeRenderPipelineOperand(pipeline, generation)),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, GPUPreparedNativeBindGroupOperand(group, generation)),
                recipe.copyScissorTargetLocalI32().let { scissor -> GPUPreparedNativeRenderCommand.SetScissor(scissor.left, scissor.top, scissor.width(), scissor.height()) },
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(recipe.draw.vertexCountI32, recipe.draw.instanceCountI32, recipe.draw.firstVertexI32, recipe.draw.firstInstanceI32)),
            ),
            w6aPassV1 = pass,
        )
    }

    private fun PlanPass.materializesW6bMaskSourceV1(): Boolean = when (this) {
        is PlanPass.RenderPass -> w6bMaskSourceBinding != null
        is PlanPass.StencilCover -> coverageSource != null
        else -> false
    }

    private fun fullMaskMaterialPointGeometry(draw: W5bPointDraw, bounds: RectI32): Pair<FloatArray, IntArray> {
        val vertices = draw.copyVerticesF32()
        require(vertices.size % 8 == 0)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        for (offsetI32 in vertices.indices step 8) {
            vertices[offsetI32] = left
            vertices[offsetI32 + 1] = top
            vertices[offsetI32 + 2] = right
            vertices[offsetI32 + 3] = top
            vertices[offsetI32 + 4] = right
            vertices[offsetI32 + 5] = bottom
            vertices[offsetI32 + 6] = left
            vertices[offsetI32 + 7] = bottom
        }
        return vertices to draw.copyIndicesI32()
    }

    /** Reuses the frozen direct-path binding capacity while covering its published source extent. */
    private fun fullMaskMaterialTriangleGeometry(bounds: RectI32, vertexCountI32: Int,
        indexCountI32: Int): Pair<FloatArray, IntArray> {
        require(vertexCountI32 >= 3 && indexCountI32 >= 3 && indexCountI32 % 3 == 0)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val vertices = FloatArray(Math.multiplyExact(vertexCountI32, 2))
        vertices[0] = left
        vertices[1] = top
        vertices[2] = right * 2f - left
        vertices[3] = top
        vertices[4] = left
        vertices[5] = bottom * 2f - top
        for (offsetI32 in 6 until vertices.size step 2) {
            vertices[offsetI32] = left
            vertices[offsetI32 + 1] = top
        }
        return vertices to IntArray(indexCountI32) { indexI32 -> indexI32 % 3 }
    }

    private fun geometryPipeline(mapped: GPUWgpu4kCorePrimitivePipelineMapping.Mapped, groupZero: GPUBindGroupLayout,
        owned: W6aOwnedHandles, template: GPUW5aGeometryHostTemplateV1?, sourceBlend: BlendPlan? = null,
        sourceIgnoresDepthStencil: Boolean = false): GPURenderPipeline {
        require(!sourceIgnoresDepthStencil || sourceBlend != null)
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code =
            requireNotNull(corePrimitiveMaterialGeometryWgslV1(mapped.componentIdentity)))))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        val sourceIdentity = if (sourceIgnoresDepthStencil) mapped.identity.copy(
            program = GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
            blendProgram = GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver,
        ) else mapped.identity
        val descriptor = corePrimitiveWgpu4kRenderPipelineDescriptor(sourceIdentity, module, layout).let { descriptor ->
            sourceBlend?.let { blend ->
                val fragment = requireNotNull(descriptor.fragment)
                RenderPipelineDescriptor(
                    label = descriptor.label,
                    layout = descriptor.layout,
                    vertex = descriptor.vertex,
                    primitive = descriptor.primitive,
                    depthStencil = descriptor.depthStencil,
                    multisample = descriptor.multisample,
                    fragment = FragmentState(module = fragment.module, entryPoint = fragment.entryPoint,
                        targets = listOf(w6aColorTarget(blend)), constants = fragment.constants),
                )
            } ?: descriptor
        }
        return owned.own(device.createRenderPipeline(descriptor)).also { pipeline -> template?.let {
            owned.templates[pipeline] = GPUW5aGeometryPipelineTemplate(it.pipelineRecipeId, descriptor, groupZero,
                materialCoordinateSlot = it.materialCoordinateSlot)
        } }
    }

    private fun coverageGeometryPipeline(mapped: GPUWgpu4kCorePrimitivePipelineMapping.Mapped,
        groupZero: GPUBindGroupLayout, template: GPUW5aGeometryHostTemplateV1, owned: W6aOwnedHandles): GPURenderPipeline {
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code = composeW5aHostCoverageV1(template))))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        return owned.own(device.createRenderPipeline(corePrimitiveWgpu4kRenderPipelineDescriptor(mapped.identity, module, layout)))
    }

    private fun pipeline(shader: String, groupZero: GPUBindGroupLayout, target: ColorTargetState,
        owned: W6aOwnedHandles, template: GPUW5aGeometryHostTemplateV1? = null,
        vertices: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact? = null): GPURenderPipeline {
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code = shader)))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = listOf(groupZero))))
        val descriptor = RenderPipelineDescriptor(layout = layout, vertex = VertexState(module, entryPoint = template?.vertexEntryPoint ?: "vs_main",
            buffers = vertices?.let { listOf(preparedVerticesVertexLayoutV6(it.layout)) }.orEmpty()),
            fragment = FragmentState(module = module, targets = listOf(target), entryPoint = template?.fragmentEntryPoint ?: "fs_main"),
            primitive = PrimitiveState(topology = if (vertices?.topology == org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode.TriangleStrip)
                GPUPrimitiveTopology.TriangleStrip else GPUPrimitiveTopology.TriangleList,
                stripIndexFormat = if (vertices?.topology == org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode.TriangleStrip)
                    vertices.indexFormat?.let { if (it == "uint16") GPUIndexFormat.Uint16 else GPUIndexFormat.Uint32 } else null))
        return owned.own(device.createRenderPipeline(descriptor)).also { pipeline -> template?.let {
            owned.templates[pipeline] = GPUW5aGeometryPipelineTemplate(it.pipelineRecipeId, descriptor, groupZero,
                materialCoordinateSlot = it.materialCoordinateSlot)
        } }
    }

    /** Fullscreen W6b coverage filters may consume a frozen W5 row in group 1. */
    private fun pipeline(shader: String, bindGroups: List<GPUBindGroupLayout>, target: ColorTargetState,
        owned: W6aOwnedHandles): GPURenderPipeline {
        val module = owned.own(device.createShaderModule(ShaderModuleDescriptor(code = shader)))
        val layout = owned.own(device.createPipelineLayout(PipelineLayoutDescriptor(bindGroupLayouts = bindGroups)))
        return owned.own(device.createRenderPipeline(RenderPipelineDescriptor(layout = layout,
            vertex = VertexState(module, entryPoint = "vs_main"),
            fragment = FragmentState(module = module, targets = listOf(target), entryPoint = "fs_main"),
            primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList))))
    }
}


private class W6aOwnedHandles : AutoCloseable, GPUW5aGeometryPipelineTemplateProvider {
    private val handles = mutableListOf<AutoCloseable>()
    val templates = java.util.IdentityHashMap<GPURenderPipeline, GPUW5aGeometryPipelineTemplate>()
    fun <T : AutoCloseable> own(value: T): T = value.also { handles += it }
    override fun sourceTemplate(pipeline: GPURenderPipeline): GPUW5aGeometryPipelineTemplate? = templates[pipeline]
        ?: handles.filterIsInstance<GPUW5aGeometryPipelineTemplateProvider>().firstNotNullOfOrNull { it.sourceTemplate(pipeline) }
    override fun close() {
        var failure: Throwable? = null
        val iterator = handles.listIterator(handles.size)
        while (iterator.hasPrevious()) try { iterator.previous().close(); iterator.remove() }
        catch (error: Throwable) { if (failure == null) failure = error else failure.addSuppressed(error) }
        failure?.let { throw it }
    }
}
