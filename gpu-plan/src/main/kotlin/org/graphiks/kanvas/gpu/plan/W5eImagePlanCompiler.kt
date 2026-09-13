package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** One public image command; decomposition never creates another logical destination write. */
public class ImageDrawV1 internal constructor(
    override val commandIndex: Int,
    override val materialAuthority: PlanDrawMaterialAuthority.MaterialV3,
    public val execution: ImageSampleExecutionPlanV1,
    boundsI32: RectI32,
    override val blend: BlendPlan,
) : PlanDraw {
    private val bounds = boundsI32.copy()
    public fun copyBoundsI32(): RectI32 = bounds.copy()
    override val color: ColorF32 get() = error("ImageDrawV1 has only V3 material authority")
    override val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override val sample: SamplePlan = SamplePlan.SingleSample
}

/** Planner-issued image-to-Rect construction proof; the original image IR is never rewritten. */
public class W5eImageConstructionPlanV1 internal constructor(
    public val constructionGraph: RenderGraph,
    public val materialTable: MaterialPlanTable,
    draws: List<ImageDrawV1>,
    public val peakBytesI64: Long,
) {
    private val draws = immutableList(draws)
    public fun imageDraws(): List<ImageDrawV1> = draws
    public val canonicalIdentity: String = "w5e-construction-v1:${constructionGraph.id.value}:" +
        draws.joinToString(";") { "${it.commandIndex}:${it.execution.canonicalIdentity}" }
    init {
        require(constructionGraph.capabilityId == W5eImagePlanCompiler.CONSTRUCTION_CAPABILITY_ID)
        val constructed = constructionGraph.passes().filterIsInstance<PlanPass.RenderPass>().flatMap { it.draws() }
        require(draws.isNotEmpty() && constructed.size == draws.size && constructionGraph.visualCommandCount == draws.size)
        require(constructed.zip(draws).all { (geometry, image) ->
            val entry = materialTable.entry(image.materialAuthority.ref)
            geometry is SolidRectDraw && geometry.commandIndex == image.commandIndex &&
                geometry.copyVisibleBounds() == image.copyBoundsI32() && geometry.copyScissor() == image.copyBoundsI32() &&
                geometry.blend == image.blend && geometry.coverage == image.coverage && geometry.sample == image.sample &&
                entry.program is ImageMaterialProgramV3.ColorV3 && entry.bindings is ImageSampleV3 &&
                entry.bindings.execution === image.execution && image.materialAuthority.imageCoordinates === image.execution.coordinates &&
                image.execution.numericAuthority.authenticates(entry.program, image.execution)
        }) { W5eImagePlanDiagnostics.InvalidContract }
        require(peakBytesI64 <= constructionGraph.budget.maxFrameLocalBytes)
    }
}

/** Exact first slice: decoded RGBA/PREMUL/sRGB, direct patch, Nearest, simple integral Rect coverage. */
public class W5eImagePlanCompiler : GpuPlanCompiler {
    private class Candidate(val owner: W5eImagePlanCompiler, val scene: SceneSnapshot,
        override val target: RenderTargetDescriptor) : GpuPlanCandidate {
        override val capabilityId: String = CAPABILITY_ID
        override val sceneCanonicalId: CanonicalId = scene.canonicalId
    }
    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace)
            return GpuPlanSelection.InvalidScene(listOf(diagnostic(W5eImagePlanDiagnostics.InvalidContract)))
        if (scene.colorSpace != ColorSpace.SRGB) return gap(W5eImagePlanDiagnostics.UnsupportedSlice)
        var imagesI32 = 0
        for (command in scene) when (command) {
            is SceneCommand.Draw -> {
                val node = command.node
                val patch = node.geometry as? GeometryNode.ImagePatch ?: return gap(W5eImagePlanDiagnostics.UnsupportedSlice)
                val pixels = node.resource as? ImageResourceSnapshot.Pixels ?: return gap(W5eImagePlanDiagnostics.ExternalResource)
                val paint = node.paint
                if (node.origin != DrawOrigin.IMAGE || patch.sampling != ImageSampling.Nearest ||
                    pixels.pixelFormat != ImagePixelFormat.RGBA_8888 || pixels.alphaType != ImageAlphaType.PREMUL ||
                    pixels.colorSpace != ColorSpace.SRGB || node.effects !is EffectStack.Empty ||
                    node.operationBlendMode != null || paint?.shader != null || paint?.blender != null ||
                    paint?.colorFilter != null || paint?.maskFilter != null || paint?.imageFilter != null ||
                    paint?.pathEffect != null || paint?.style?.let { it != PaintStyleNode.FILL } == true ||
                    node.clip is ClipStackNode.Operations || (node.clip as? ClipStackNode.DeviceRect)?.antiAlias == true)
                    return gap(W5eImagePlanDiagnostics.UnsupportedSlice)
                imagesI32++
            }
            is SceneCommand.SetTransform, is SceneCommand.SetClip, is SceneCommand.Annotation -> Unit
            else -> return gap(W5eImagePlanDiagnostics.UnsupportedSlice)
        }
        return if (imagesI32 == 0) gap(W5eImagePlanDiagnostics.UnsupportedSlice)
            else GpuPlanSelection.Candidate(Candidate(this, scene, target))
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate
        if (selected == null || selected.owner !== this) return RenderPlanResult.InvalidScene(listOf(diagnostic(W5eImagePlanDiagnostics.InvalidContract)))
        return try {
            val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
            val targetF32 = RectF32.ofLTRB(0f, 0f, extent.width.toFloat(), extent.height.toFloat())
            val imageDraws = mutableListOf<ImageDrawV1>()
            val geometryDraws = mutableListOf<SolidRectDraw>()
            val program = ImageMaterialProgramV3.ColorV3(ImageChannelOrderV1.RGBA, ImageAlphaType.PREMUL,
                ImageTransferPlanV1.SRGB, ImageGamutPlanV1.SRGB)
            var imageBytesI64 = 0L
            val uploads = linkedMapOf<String, ImageUploadPlanV1>()
            fun alignedRowI64(bytesI64: Long): Long {
                var aI64 = 256L
                var bI64 = capabilities.copyBytesPerRowAlignment.toLong()
                while (bI64 != 0L) { val remainderI64 = aI64 % bI64; aI64 = bI64; bI64 = remainderI64 }
                val alignmentI64 = Math.multiplyExact(256L / aI64, capabilities.copyBytesPerRowAlignment.toLong())
                return Math.addExact(bytesI64, (alignmentI64 - bytesI64 % alignmentI64) % alignmentI64)
            }
            for ((commandIndexI32, command) in selected.scene.withIndex()) {
                if (command !is SceneCommand.Draw) continue
                val node = command.node
                val patch = node.geometry as GeometryNode.ImagePatch
                val pixels = node.resource as ImageResourceSnapshot.Pixels
                require(patch.image.id.value == pixels.sourceId) { W5eImagePlanDiagnostics.InvalidContract }
                val upload = ImageUploadPlanV1.seal(pixels).let { uploads.getOrPut(it.contentIdentity) { it } }
                val coordinates = ImageCoordinatePlanV1.seal(node.transform, patch.copySource(), patch.copyDestination())
                // Projection is proved before any raster conversion, including the cross-zero refusal.
                val numeric = ImageNumericAuthorityV1.seal(program, upload, coordinates, targetF32)
                    ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.NumericDomainUnbounded)
                require(node.transform.isScaleTranslate() || node.transform.isIdentity) { W5eImagePlanDiagnostics.UnsupportedSlice }
                val destination = patch.copyDestination()
                val first = node.transform.transform(Point2F32(destination.left, destination.top))
                val second = node.transform.transform(Point2F32(destination.right, destination.bottom))
                val edges = listOf(minOf(first.x, second.x), minOf(first.y, second.y), maxOf(first.x, second.x), maxOf(first.y, second.y))
                require(edges.all { it.isFinite() && it.toDouble() >= Int.MIN_VALUE && it.toDouble() <= Int.MAX_VALUE && it.toInt().toFloat() == it }) {
                    W5eImagePlanDiagnostics.UnsupportedSlice
                }
                val clip = (node.clip as? ClipStackNode.DeviceRect)?.copyBounds()
                require(clip == null || listOf(clip.left, clip.top, clip.right, clip.bottom).all { it.isFinite() && it.toInt().toFloat() == it }) {
                    W5eImagePlanDiagnostics.UnsupportedSlice
                }
                val visible = RectI32.ofLTRB(maxOf(0, edges[0].toInt(), clip?.left?.toInt() ?: 0),
                    maxOf(0, edges[1].toInt(), clip?.top?.toInt() ?: 0),
                    minOf(extent.width, edges[2].toInt(), clip?.right?.toInt() ?: extent.width),
                    minOf(extent.height, edges[3].toInt(), clip?.bottom?.toInt() ?: extent.height))
                require(!visible.isEmpty) { W5eImagePlanDiagnostics.UnsupportedSlice }
                require(upload.widthI32 <= capabilities.maxTextureDimension2D && upload.heightI32 <= capabilities.maxTextureDimension2D &&
                    capabilities.supportsTexture(PlanTextureFormat.ImageV1(upload.physicalFormat), 1,
                        setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination)) &&
                    capabilities.maxBindingsPerBindGroupI32?.let { it >= 2 } == true &&
                    capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= 2 } == true &&
                    capabilities.maxUniformBufferBindingSizeBytesI64?.let { it >= 96L } == true) { W5eImagePlanDiagnostics.Capability }
                val execution = ImageSampleExecutionPlanV1(upload, coordinates,
                    ImageColorAlphaPlanV1(program.channelOrder, program.alphaType, program.transfer, program.gamut),
                    numeric, node.paint?.color?.alphaNormalized ?: 1f, Math.addExact(upload.byteCountI64, 96L))
                val ref = MaterialPlanRef(imageDraws.size)
                val blend = FinalBlendPlanner.plan(node.blend, CoveragePlan.FullOrScissor, SamplePlan.SingleSample,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1())
                    ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.UnsupportedSlice)
                require(blend != BlendPlan.NoOpV1) { W5eImagePlanDiagnostics.UnsupportedSlice }
                imageDraws += ImageDrawV1(commandIndexI32, PlanDrawMaterialAuthority.MaterialV3(ref, coordinates), execution, visible, blend)
                // Transparent is a conservative construction source: it can never trigger an opaque optimization.
                geometryDraws += SolidRectDraw.ofMaterial(commandIndexI32, MaterialPlanRef(0), visible, visible, blend = blend)
            }
            for (upload in uploads.values) {
                val rowI64 = alignedRowI64(upload.logicalRowBytesI64)
                require(Math.multiplyExact(rowI64, upload.heightI32.toLong()) <= minOf(capabilities.maxBufferSizeBytes, Int.MAX_VALUE.toLong())) {
                    W5eImagePlanDiagnostics.Capability
                }
                imageBytesI64 = Math.addExact(imageBytesI64, Math.addExact(upload.byteCountI64, Math.multiplyExact(rowI64, upload.heightI32.toLong())))
            }
            imageBytesI64 = Math.addExact(imageBytesI64, Math.multiplyExact(imageDraws.size.toLong(), 96L))
            require(imageBytesI64 < budget.maxFrameLocalBytes) { W5eImagePlanDiagnostics.FrameBudget }
            val targetBytesI64 = checkedTextureBytesI64(4, extent.width, extent.height, 1)
            val rowI64 = Math.addExact(extent.width.toLong() * 4L,
                (capabilities.copyBytesPerRowAlignment - extent.width.toLong() * 4L % capabilities.copyBytesPerRowAlignment) % capabilities.copyBytesPerRowAlignment)
            val neutral = MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1)))
            val construction = W5bDestinationGraphSealer.seal(PlanId("w5e-construction:${selected.sceneCanonicalId.value}:${capabilities.deviceGeneration}:${budget.maxFrameLocalBytes}"),
                CONSTRUCTION_CAPABILITY_ID, extent, capabilities, budget, geometryDraws, neutral, targetBytesI64,
                Math.multiplyExact(rowI64, extent.height.toLong()), rowI64)
            val uniformAlignmentI64 = capabilities.minUniformBufferOffsetAlignment.toLong()
            val uniformStrideI64 = Math.addExact(32L, (uniformAlignmentI64 - 32L % uniformAlignmentI64) % uniformAlignmentI64)
            val geometryBytesI64 = listOf(PlanScratchBufferKind.Vertex to 32L, PlanScratchBufferKind.Index to 24L,
                PlanScratchBufferKind.Uniform to uniformStrideI64).fold(0L) { totalI64, (kind, perDrawI64) ->
                val bytesI64 = requireNotNull(capabilities.bufferAllocationPolicy.reserve(kind,
                    Math.multiplyExact(imageDraws.size.toLong(), perDrawI64))) { W5eImagePlanDiagnostics.FrameBudget }
                require(bytesI64 <= capabilities.maxBufferSizeBytes) { W5eImagePlanDiagnostics.Capability }
                Math.addExact(totalI64, bytesI64)
            }
            val peakI64 = Math.addExact(Math.addExact(construction.peakFrameLocalBytes, geometryBytesI64), imageBytesI64)
            require(peakI64 <= budget.maxFrameLocalBytes) { W5eImagePlanDiagnostics.FrameBudget }
            val finalized = construction.passes().filterIsInstance<PlanPass.RenderPass>().flatMap { it.draws() }
            val images = imageDraws.zip(finalized).map { (image, geometry) ->
                val execution = image.execution.let { ImageSampleExecutionPlanV1(it.upload, it.coordinates,
                    it.colorAlpha, it.numericAuthority, it.paintAlphaF32, peakI64) }
                ImageDrawV1(image.commandIndex, image.materialAuthority, execution, image.copyBoundsI32(), geometry.blend)
            }
            RenderPlanResult.Ready(RenderGraph.issueW5e(W5eImageConstructionPlanV1(construction,
                MaterialPlanTable.of(images.map { MaterialPlanEntry(program, ImageSampleV3.of(it.execution)) }), images, peakI64)))
        } catch (failure: ArithmeticException) {
            RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(W5eImagePlanDiagnostics.FrameBudget)))
        } catch (failure: IllegalArgumentException) {
            val code = failure.message.orEmpty().takeIf { it.startsWith("unsupported.") || it.startsWith("resource.") || it.startsWith("invalid.") }
                ?: W5eImagePlanDiagnostics.InvalidContract
            if (code.startsWith("resource.")) RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(code)))
            else RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(code)))
        }
    }
    private fun gap(code: String): GpuPlanSelection.NotCandidate = GpuPlanSelection.NotCandidate(listOf(diagnostic(code)))
    private fun diagnostic(code: String): RenderDiagnostic = RenderDiagnostic(RenderDiagnosticCode(code),
        if (code.startsWith("resource.")) RenderDiagnosticDomain.RESOURCE else RenderDiagnosticDomain.CAPABILITY,
        RenderDiagnosticSeverity.ERROR, "W5e image planning refused the sealed $code contract.")
    public companion object {
        public const val CAPABILITY_ID: String = "w5e-decoded-nearest-image-v1"
        public const val CONSTRUCTION_CAPABILITY_ID: String = "w5e-rect-construction-v1"
    }
}
