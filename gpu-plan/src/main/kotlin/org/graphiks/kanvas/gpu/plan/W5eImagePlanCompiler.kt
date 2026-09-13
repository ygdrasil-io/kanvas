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
                entry.program is ImageMaterialProgramV3 && entry.bindings is ImageSampleV3 &&
                entry.bindings.execution === image.execution && image.materialAuthority.imageCoordinates === image.execution.coordinates &&
                materialTable.authenticatesImage(image.materialAuthority.ref, image.execution)
        }) { W5eImagePlanDiagnostics.InvalidContract }
        require(peakBytesI64 <= constructionGraph.budget.maxFrameLocalBytes)
    }
}

/** Decoded color/mask direct patches, Nearest, with the established integral Rect coverage slice. */
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
                    pixels.pixelFormat !in setOf(ImagePixelFormat.RGBA_8888, ImagePixelFormat.BGRA_8888,
                        ImagePixelFormat.SRGBA_8888, ImagePixelFormat.ALPHA_8) ||
                    pixels.alphaType !in setOf(ImageAlphaType.OPAQUE, ImageAlphaType.PREMUL, ImageAlphaType.UNPREMUL) ||
                    node.effects !is EffectStack.Empty ||
                    node.operationBlendMode != null || paint?.blender != null ||
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
            val entries = mutableListOf<MaterialPlanEntry>()
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
                val channel = when (pixels.pixelFormat) {
                    ImagePixelFormat.RGBA_8888, ImagePixelFormat.SRGBA_8888 -> ImageChannelOrderV1.RGBA
                    ImagePixelFormat.BGRA_8888 -> ImageChannelOrderV1.BGRA
                    ImagePixelFormat.ALPHA_8 -> ImageChannelOrderV1.ALPHA
                    else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.Format)
                }
                require(pixels.alphaType in setOf(ImageAlphaType.OPAQUE, ImageAlphaType.PREMUL, ImageAlphaType.UNPREMUL)) {
                    W5eImagePlanDiagnostics.Alpha
                }
                val color = when (pixels.pixelFormat) {
                    ImagePixelFormat.ALPHA_8 -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.NONE, ImageGamutPlanV1.NONE)
                    ImagePixelFormat.SRGBA_8888 -> {
                        require(pixels.colorSpace == ColorSpace.SRGB) { W5eImagePlanDiagnostics.ColorSpace }
                        ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.SRGB, ImageGamutPlanV1.SRGB)
                    }
                    else -> when (pixels.colorSpace) {
                        ColorSpace.SRGB -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.SRGB, ImageGamutPlanV1.SRGB)
                        ColorSpace.DISPLAY_P3 -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.SRGB, ImageGamutPlanV1.DISPLAY_P3)
                        ColorSpace.LINEAR_SRGB -> ImageColorAlphaPlanV1(channel, pixels.alphaType, ImageTransferPlanV1.LINEAR, ImageGamutPlanV1.SRGB)
                        else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.ColorSpace)
                    }
                }
                val child = if (channel == ImageChannelOrderV1.ALPHA) when (val result =
                    EffectiveMaterialPlanner.planImageMaskSource(node, RectI32(0, 0, extent.width, extent.height))) {
                    is EffectiveMaterialPlanner.Result.Ready -> result
                    is EffectiveMaterialPlanner.Result.Refused -> throw IllegalArgumentException(result.diagnosticCode)
                } else null
                val program: ImageMaterialProgramV3 = if (child == null)
                    ImageMaterialProgramV3.ColorV3(channel, color.alphaType, color.transfer, color.gamut)
                    else ImageMaterialProgramV3.MaskV3(child.table.entry(child.root).program, color.alphaType)
                if (child != null) entries += child.table.entries()
                val upload = ImageUploadPlanV1.seal(pixels).let { uploads.getOrPut(it.contentIdentity) { it } }
                val coordinates = ImageCoordinatePlanV1.seal(node.transform, patch.copySource(), patch.copyDestination())
                val paintAlphaF32 = if (child == null) node.paint?.color?.alphaNormalized ?: 1f else 1f
                // Projection is proved before any raster conversion, including the cross-zero refusal.
                val numeric = ImageNumericAuthorityV1.seal(program, upload, coordinates, targetF32, paintAlphaF32)
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
                        setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination))) { W5eImagePlanDiagnostics.TextureLimit }
                require(capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= 2 } == true) {
                    W5eImagePlanDiagnostics.BindingLimit
                }
                val execution = ImageSampleExecutionPlanV1(upload, coordinates,
                    color, numeric, paintAlphaF32,
                    child?.table?.sourceIdentity(child.root), Math.addExact(upload.byteCountI64, 96L))
                val ref = MaterialPlanRef(entries.size)
                entries += MaterialPlanEntry(program, ImageSampleV3.of(execution))
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
            val materialTable = MaterialPlanTable.of(entries)
            val rawSources = imageDraws.map { RawMaterialRequirementsV2.of(materialTable, it.materialAuthority.ref) }
            for (requirements in rawSources.distinctBy { it.canonicalIdentity }) {
                require(requirements.fitsUniformBinding(capabilities)) { W5eImagePlanDiagnostics.BindingLimit }
                imageBytesI64 = Math.addExact(imageBytesI64, requirements.uniformByteCountI64)
            }
            materialTable.gradientStopSlab?.let {
                try { it.requireStorageCapabilities(capabilities) }
                catch (_: IllegalArgumentException) { throw IllegalArgumentException(W5eImagePlanDiagnostics.BindingLimit) }
                imageBytesI64 = Math.addExact(imageBytesI64, it.byteSizeI64)
            }
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
                    it.colorAlpha, it.numericAuthority, it.paintAlphaF32, it.childSourceIdentity, peakI64) }
                ImageDrawV1(image.commandIndex, image.materialAuthority, execution, image.copyBoundsI32(), geometry.blend)
            }
            RenderPlanResult.Ready(RenderGraph.issueW5e(W5eImageConstructionPlanV1(construction,
                MaterialPlanTable.of(materialTable.entries().mapIndexed { indexI32, entry ->
                    images.firstOrNull { it.materialAuthority.ref.indexI32 == indexI32 }?.let {
                        entry.copy(bindings = ImageSampleV3.of(it.execution))
                    } ?: entry
                }), images, peakI64)))
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
