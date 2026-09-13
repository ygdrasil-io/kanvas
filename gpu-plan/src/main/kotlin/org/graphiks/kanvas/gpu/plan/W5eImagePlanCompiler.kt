package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32

/** One original image source command; native decomposition never creates a logical draw. */
public class ImageDrawV1 internal constructor(
    override val commandIndex: Int,
    override val materialAuthority: PlanDrawMaterialAuthority.MaterialV3,
    public val execution: ImageSampleExecutionPlanV1,
    boundsI32: RectI32,
    override val blend: BlendPlan,
    override val coverage: CoveragePlan = CoveragePlan.FullOrScissor,
    override val sample: SamplePlan = SamplePlan.SingleSample,
    public val originalDraw: DrawNode,
) : PlanDraw {
    private val bounds = boundsI32.copy()
    public fun copyBoundsI32(): RectI32 = bounds.copy()
    override val color: ColorF32 get() = error("ImageDrawV1 has only V3 material authority")
}

public enum class ImageNoOpReasonV1 { DestinationBlend, EmptyNineDestination }

/** Separate logical source envelope over the exact compiler-issued W4 geometry graph. */
public class W5eImageConstructionPlanV1 internal constructor(
    public val constructionGraph: RenderGraph,
    public val materialTable: MaterialPlanTable,
    draws: List<ImageDrawV1>,
    ordinarySources: Map<Int, PlanDrawMaterialAuthority>,
    noOpDraws: Map<Int, DrawNode>,
    public val peakBytesI64: Long,
) {
    private val draws = immutableList(draws)
    private val ordinarySources = ordinarySources.toMap()
    private val noOpDraws = noOpDraws.toMap()
    public fun imageDraws(): List<ImageDrawV1> = draws
    public fun ordinarySources(): Map<Int, PlanDrawMaterialAuthority> = ordinarySources.toMap()
    public fun noOpDraws(): Map<Int, DrawNode> = noOpDraws.toMap()
    public fun noOpReasons(): Map<Int, ImageNoOpReasonV1> = noOpDraws.mapValues { requireNotNull(it.value.w5eNoOpReason()) }
    public val visualCommandCountI32: Int = constructionGraph.visualCommandCount + noOpDraws.size
    public val canonicalIdentity: String = "w5e-construction-v2:" + constructionGraph.id.value + ":" +
        draws.joinToString(";") { "${it.commandIndex}:${it.originalDraw.canonicalId.value}:${it.execution.canonicalIdentity}" } +
        ordinarySources.entries.joinToString(";") { "ordinary:${it.key}:${materialTable.sourceIdentity(it.value.materialPlanRef())}" } +
        noOpDraws.entries.joinToString(";") { "noop:${it.key}:${it.value.canonicalId.value}:${it.value.w5eNoOpReason()}" }
    init {
        val geometry = constructionGraph.w5eColorDraws().associateBy { it.commandIndex }
        require((draws.isNotEmpty() || noOpDraws.isNotEmpty()) && draws.map { it.commandIndex }.distinct().size == draws.size &&
            draws.all { image ->
                val source = geometry[image.commandIndex]
                source != null && source.blend == image.blend && source.coverage == image.coverage && source.sample == image.sample &&
                    materialTable.authenticatesImage(image.materialAuthority.ref, image.execution) &&
                    image.materialAuthority.imageCoordinates === image.execution.coordinates
            }) { W5eImagePlanDiagnostics.InvalidContract }
        require(peakBytesI64 <= constructionGraph.budget.maxFrameLocalBytes)
        val originalTable = constructionGraph.materialPlanTableOrNull()
        require(noOpDraws.keys.intersect(geometry.keys).isEmpty() && noOpDraws.values.all { it.w5eIsNoOp() })
        require(ordinarySources.keys == geometry.keys - draws.map { it.commandIndex }.toSet())
        for ((commandI32, projected) in ordinarySources) {
            val original = geometry.getValue(commandI32).materialAuthority
            require(requireNotNull(originalTable).sourceIdentity(original.materialPlanRef()) == materialTable.sourceIdentity(projected.materialPlanRef()) &&
                when (original) {
                    is PlanDrawMaterialAuthority.MaterialV1 -> projected is PlanDrawMaterialAuthority.MaterialV1 && original.coordinates === projected.coordinates
                    is PlanDrawMaterialAuthority.MaterialV2 -> projected is PlanDrawMaterialAuthority.MaterialV2 && original.coordinates === projected.coordinates
                    else -> false
                }) { W5eImagePlanDiagnostics.InvalidContract }
        }
    }
}

/** Geometry authorities remain W4-owned; only source/material authority is overlaid by W5e. */
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
                if (isImageSource(node)) {
                    if (!admittedSource(node)) return gap(W5eImagePlanDiagnostics.UnsupportedSlice)
                    imagesI32++
                } else if (node.geometry !is GeometryNode.Rect && node.geometry !is GeometryNode.Path &&
                    node.geometry !is GeometryNode.RRect) return gap(W5eImagePlanDiagnostics.UnsupportedSlice)
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
            val noOpDraws = selected.scene.withIndex().mapNotNull { (indexI32, command) ->
                (command as? SceneCommand.Draw)?.node?.takeIf { it.w5eIsNoOp() }?.let { indexI32 to it }
            }.toMap()
            val sourceNodes = selected.scene.withIndex().mapNotNull { (indexI32, command) ->
                (command as? SceneCommand.Draw)?.node?.takeIf { isImageSource(it) && indexI32 !in noOpDraws }?.let { indexI32 to it }
            }.toMap()
            // This private projection is a construction input, never a public operation or
            // replacement Scene authority. Original IMAGE metadata is retained in the bridge.
            val projected = SceneSnapshot.of(selected.scene.extent, selected.scene.colorSpace, selected.scene.mapIndexed { indexI32, command ->
                if (noOpDraws[indexI32]?.w5eNoOpReason() == ImageNoOpReasonV1.EmptyNineDestination)
                    SceneCommand.Annotation.of(RectF32.ofLTRB(0f, 0f, 0f, 0f), "w5e.empty-nine", indexI32.toString())
                else if (command is SceneCommand.Draw && isImageSource(command.node)) SceneCommand.Draw(projectGeometry(command.node)) else command
            })
            val metadataOnly = projected.none { it is SceneCommand.Draw }
            val emptyCompiler = W3SolidRectPlanCompiler()
            val compiler: GpuPlanCompiler = if (metadataOnly) emptyCompiler else CapabilityCompilerChain.of(listOf(W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler(),
                W4cPathFillPlanCompiler(), W4dPathStrokePlanCompiler(), W5aCompositePlanCompiler()))
            val selection = if (metadataOnly) {
                require(noOpDraws.isNotEmpty() && selected.scene.withIndex().all { (indexI32, command) ->
                    command !is SceneCommand.Draw || noOpDraws[indexI32] === command.node
                }) { W5eImagePlanDiagnostics.InvalidContract }
                emptyCompiler.selectEmptyW5eConstruction(projected, selected.target)
            } else compiler.select(projected, selected.target)
            if (selection !is GpuPlanSelection.Candidate) {
                // Invalid image inverses retain image-specific public diagnostics even when
                // the geometry capability cannot construct the corresponding footprint.
                for (node in sourceNodes.values) when (val source = EffectiveMaterialPlanner.planW5eImageSource(node,
                    RectI32(0, 0, selected.target.extent.width, selected.target.extent.height))) {
                    is EffectiveMaterialPlanner.Result.Refused -> throw IllegalArgumentException(source.diagnosticCode)
                    else -> Unit
                }
                return when (selection) {
                    is GpuPlanSelection.NotCandidate -> RenderPlanResult.GapOnPromotedScope(selection.diagnostics())
                    is GpuPlanSelection.InvalidScene -> RenderPlanResult.InvalidScene(selection.diagnostics())
                    is GpuPlanSelection.ResourceLimitExceeded -> RenderPlanResult.ResourceLimitExceeded(selection.diagnostics())
                    else -> RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(W5eImagePlanDiagnostics.UnsupportedSlice)))
                }
            }
            val construction = when (val result = compiler.plan(selection.candidate, capabilities, budget)) {
                is RenderPlanResult.Ready -> result.plan
                is RenderPlanResult.ResourceLimitExceeded -> return RenderPlanResult.ResourceLimitExceeded(
                    listOf(diagnostic(W5eImagePlanDiagnostics.FrameBudget)))
                else -> return result
            }
            val geometry = construction.w5eColorDraws().associateBy { it.commandIndex }
            val entries = mutableListOf<MaterialPlanEntry>()
            val images = mutableListOf<ImageDrawV1>()
            for ((commandI32, node) in sourceNodes) {
                val draw = requireNotNull(geometry[commandI32]) { W5eImagePlanDiagnostics.UnsupportedSlice }
                val bounds = draw.w5eDeviceBoundsI32()
                val source = when (val result = EffectiveMaterialPlanner.planW5eImageSource(node, bounds)) {
                    is EffectiveMaterialPlanner.Result.Ready -> result
                    is EffectiveMaterialPlanner.Result.Refused -> throw IllegalArgumentException(result.diagnosticCode)
                }
                val ref = MaterialPlanRef(entries.size + source.root.indexI32)
                entries += source.table.entries()
                val execution = (source.table.entry(source.root).bindings as ImageSampleV3).execution
                val upload = execution.upload
                require(upload.widthI32 <= capabilities.maxTextureDimension2D && upload.heightI32 <= capabilities.maxTextureDimension2D &&
                    capabilities.supportsTexture(PlanTextureFormat.ImageV1(upload.physicalFormat), 1,
                        setOf(PlanResourceUsage.Sampled, PlanResourceUsage.CopyDestination))) { W5eImagePlanDiagnostics.TextureLimit }
                val textureCountI32 = 1 + (if (draw.blend is BlendPlan.DestinationReadV1) 1 else 0) +
                    draw.w5eGeometryTextureCountI32()
                require(capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= textureCountI32 } == true) {
                    W5eImagePlanDiagnostics.BindingLimit
                }
                images += ImageDrawV1(commandI32, PlanDrawMaterialAuthority.MaterialV3(ref, execution.coordinates),
                    execution, bounds, draw.blend, draw.coverage, draw.sample, node)
            }
            // An all-DST frame has no material source. The inert table satisfies the envelope
            // schema only; it issues no packet, raw source requirement, upload or native binding.
            val emptyTable = MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1)))
            val geometryTable = construction.materialPlanTableOrNull() ?: emptyTable
            val interned = MaterialPlanTable.intern(listOf(if (entries.isEmpty()) emptyTable else MaterialPlanTable.of(entries), geometryTable))
            val table = interned.table
            val remappedImages = images.map { image ->
                val ref = interned.remap(0, image.materialAuthority.ref)
                val execution = (table.entry(ref).bindings as ImageSampleV3).execution
                ImageDrawV1(image.commandIndex, PlanDrawMaterialAuthority.MaterialV3(ref, execution.coordinates), execution,
                    image.copyBoundsI32(), image.blend, image.coverage, image.sample, image.originalDraw)
            }
            val ordinarySources = geometry.values.filter { it.commandIndex !in sourceNodes }.associate { draw ->
                draw.commandIndex to when (val authority = draw.materialAuthority) {
                    is PlanDrawMaterialAuthority.MaterialV1 -> authority.copy(ref = interned.remap(1, authority.ref))
                    is PlanDrawMaterialAuthority.MaterialV2 -> authority.copy(ref = interned.remap(1, authority.ref))
                    else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.InvalidContract)
                }
            }
            var extraBytesI64 = table.gradientStopSlab?.byteSizeI64 ?: 0L
            table.gradientStopSlab?.requireStorageCapabilities(capabilities)
            for (upload in images.map { it.execution.upload }.distinctBy { it.contentIdentity }) {
                val alignmentI64 = lcmI64(256L, capabilities.copyBytesPerRowAlignment.toLong())
                val rowI64 = Math.addExact(upload.logicalRowBytesI64, (alignmentI64 - upload.logicalRowBytesI64 % alignmentI64) % alignmentI64)
                val stagingI64 = Math.multiplyExact(rowI64, upload.heightI32.toLong())
                require(stagingI64 <= minOf(capabilities.maxBufferSizeBytes, Int.MAX_VALUE.toLong())) { W5eImagePlanDiagnostics.Capability }
                extraBytesI64 = Math.addExact(extraBytesI64, Math.addExact(upload.byteCountI64, stagingI64))
            }
            val imageRequirements = remappedImages.map { RawMaterialRequirementsV2.of(table, it.materialAuthority.ref) }
            val ordinaryRequirements = ordinarySources.values.map { RawMaterialRequirementsV2.of(table, it.materialPlanRef()) }
            for (source in (imageRequirements + ordinaryRequirements).distinctBy { it.canonicalIdentity }) {
                require(source.fitsUniformBinding(capabilities)) { W5eImagePlanDiagnostics.BindingLimit }
                extraBytesI64 = Math.addExact(extraBytesI64, source.uniformByteCountI64)
            }
            if (construction.capabilityId == W3SolidRectPlanCompiler.W5A_CAPABILITY_ID) {
                val alignmentI64 = capabilities.minUniformBufferOffsetAlignment.toLong()
                val strideI64 = Math.addExact(32L, (alignmentI64 - 32L % alignmentI64) % alignmentI64)
                for ((kind, perDrawI64) in listOf(PlanScratchBufferKind.Vertex to 32L,
                    PlanScratchBufferKind.Index to 24L, PlanScratchBufferKind.Uniform to strideI64)) {
                    extraBytesI64 = Math.addExact(extraBytesI64, requireNotNull(capabilities.bufferAllocationPolicy.reserve(kind,
                        Math.multiplyExact(construction.visualCommandCount.toLong(), perDrawI64))) { W5eImagePlanDiagnostics.FrameBudget })
                }
            }
            val peakI64 = Math.addExact(construction.peakFrameLocalBytes, extraBytesI64)
            require(peakI64 <= budget.maxFrameLocalBytes) { W5eImagePlanDiagnostics.FrameBudget }
            val finalEntries = table.entries().toMutableList()
            val finalExecutions = mutableMapOf<MaterialPlanRef, ImageSampleExecutionPlanV1>()
            val finalImages = remappedImages.map { image ->
                val source = image.execution
                val ref = image.materialAuthority.ref
                val execution = finalExecutions.getOrPut(ref) { ImageSampleExecutionPlanV1(source.upload,
                    source.coordinates, source.colorAlpha, source.numericAuthority, source.paintAlphaF32,
                    source.childSourceIdentity, peakI64, source.sampling, source.tileModes) }
                finalEntries[ref.indexI32] = table.entry(ref).copy(bindings = ImageSampleV3.of(execution))
                ImageDrawV1(image.commandIndex, image.materialAuthority, execution, image.copyBoundsI32(),
                    image.blend, image.coverage, image.sample, image.originalDraw)
            }
            RenderPlanResult.Ready(RenderGraph.issueW5e(W5eImageConstructionPlanV1(construction,
                MaterialPlanTable.of(finalEntries), finalImages, ordinarySources, noOpDraws, peakI64)))
        } catch (_: ArithmeticException) {
            RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(W5eImagePlanDiagnostics.FrameBudget)))
        } catch (failure: IllegalArgumentException) {
            val code = failure.message.orEmpty().takeIf { it.startsWith("unsupported.") || it.startsWith("resource.") || it.startsWith("invalid.") }
                ?: W5eImagePlanDiagnostics.InvalidContract
            if (code.startsWith("resource.")) RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(code)))
            else RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(code)))
        }
    }

    private fun projectGeometry(node: DrawNode): DrawNode {
        val neutral = MaterialNode.Transparent
        val paint = node.paint?.copy(color = ColorARGB.Transparent, shader = neutral) ?: PaintNode(
            ColorARGB.Transparent, neutral, BlendMode.SRC_OVER, null, null, null, null, null,
            PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, node.coverage == CoverageRequest.ANTIALIASED)
        if (node.origin != DrawOrigin.IMAGE && node.origin != DrawOrigin.IMAGE_NINE) return node.copy(material = neutral, paint = paint)
        val destination = when (val source = node.geometry) {
            is GeometryNode.ImagePatch -> source.copyDestination()
            is GeometryNode.ImageNine -> source.copyDestination()
            else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.InvalidContract)
        }
        val bounds = RectF32.ofLTRB(minOf(destination.left, destination.right), minOf(destination.top, destination.bottom),
            maxOf(destination.left, destination.right), maxOf(destination.top, destination.bottom))
        val deviceEdgesF64 = listOf(
            bounds.left.toDouble() * node.transform.sx.toDouble() + node.transform.tx.toDouble(),
            bounds.right.toDouble() * node.transform.sx.toDouble() + node.transform.tx.toDouble(),
            bounds.top.toDouble() * node.transform.sy.toDouble() + node.transform.ty.toDouble(),
            bounds.bottom.toDouble() * node.transform.sy.toDouble() + node.transform.ty.toDouble())
        val aligned = deviceEdgesF64.all { it.isFinite() && it == kotlin.math.floor(it) }
        val rectLane = (node.transform.isIdentity || node.transform.isScaleTranslate()) &&
            (node.coverage == CoverageRequest.ANTIALIASED || aligned) &&
            node.clip !is ClipStackNode.Operations && (node.clip as? ClipStackNode.DeviceRect)?.antiAlias != true
        val geometry = if (rectLane) GeometryNode.Rect.of(bounds) else GeometryNode.Path(PathBuilder().addRect(bounds).build())
        return node.copy(geometry = geometry, origin = if (rectLane) DrawOrigin.RECT else DrawOrigin.PATH,
            resource = null, material = neutral, paint = paint)
    }
    private fun isImageSource(node: DrawNode): Boolean {
        var source = node.material
        repeat(65) {
            source = when (val current = source) {
                is MaterialNode.WithLocalMatrix -> current.material
                is MaterialNode.Opacity -> current.material
                else -> return current is MaterialNode.ImageSample
            }
        }
        return false
    }
    private fun admittedSource(node: DrawNode): Boolean {
        val nine = node.geometry as? GeometryNode.ImageNine
        if (nine != null && nine.sampling != ImageSampling.Nearest) return false
        val direct = node.origin == DrawOrigin.IMAGE && node.geometry is GeometryNode.ImagePatch ||
            node.origin == DrawOrigin.IMAGE_NINE && node.geometry is GeometryNode.ImageNine
        if (!direct && !(node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect ||
                node.origin == DrawOrigin.PATH && node.geometry is GeometryNode.Path)) return false
        val paint = node.paint
        if (paint != null && (paint.style != PaintStyleNode.FILL || paint.blender != null || paint.colorFilter != null ||
                paint.maskFilter != null || paint.imageFilter != null || paint.pathEffect != null) ||
            node.effects !is EffectStack.Empty || node.operationBlendMode != null) return false
        var source = node.material
        repeat(65) {
            source = when (val current = source) {
                is MaterialNode.WithLocalMatrix -> current.material
                is MaterialNode.Opacity -> current.material
                is MaterialNode.ImageSample -> return current.image is ImageResourceSnapshot.Pixels
                else -> return false
            }
        }
        return false
    }
    private fun lcmI64(leftI64: Long, rightI64: Long): Long {
        var aI64 = leftI64; var bI64 = rightI64
        while (bI64 != 0L) { val restI64 = aI64 % bI64; aI64 = bI64; bI64 = restI64 }
        return Math.multiplyExact(leftI64 / aI64, rightI64)
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

internal fun RenderGraph.w5eColorDraws(): List<PlanDraw> =
    w5aCompositePlanOrNull()?.lanes()?.flatMap { it.w5eColorDraws() } ?: passes().flatMap { pass ->
        when (pass) {
            is PlanPass.RenderPass -> pass.draws()
            is PlanPass.StencilCover -> listOf(pass.draw)
            is PlanPass.PathRenderPass -> if (pass.phase in setOf(PathRenderPhase.SingleSampleDirectColor,
                PathRenderPhase.SingleSampleStencilColorCover, PathRenderPhase.MultisampleDirectColor,
                PathRenderPhase.MultisampleStencilColorCover, PathRenderPhase.HardEdgeBinaryColorCover)) listOf(pass.draw) else emptyList()
            else -> emptyList()
        }
    }

private fun PlanDraw.w5eDeviceBoundsI32(): RectI32 = when (this) {
    is SolidRectDraw -> copyScissor()
    is AnalyticRectDraw -> copyScissor()
    is PathDraw -> copyScissorI32()
    else -> throw IllegalArgumentException(W5eImagePlanDiagnostics.UnsupportedSlice)
}

/** Exact established final-blend authority; a missing active source is never inferred to be NoOp. */
private fun DrawNode.w5eIsNoOp(): Boolean = w5eNoOpReason() != null

private fun DrawNode.w5eNoOpReason(): ImageNoOpReasonV1? {
    if (origin == DrawOrigin.IMAGE_NINE) {
        val nine = geometry as? GeometryNode.ImageNine ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.InvalidContract)
        val center = nine.copyCenter()
        val destination = nine.copyDestination()
        require(listOf(center.left, center.top, center.right, center.bottom).all(Float::isFinite) && center.isSorted()) {
            "invalid.material.image.nine-center"
        }
        require(listOf(destination.left, destination.top, destination.right, destination.bottom).all(Float::isFinite)) {
            W5eImagePlanDiagnostics.NumericDomainUnbounded
        }
        if (destination.left == destination.right || destination.top == destination.bottom) return ImageNoOpReasonV1.EmptyNineDestination
    }
    return if (FinalBlendPlanner.plan(blend, CoveragePlan.FullOrScissor,
        SamplePlan.SingleSample, BlendTargetClampV1.Unavailable) == BlendPlan.NoOpV1) ImageNoOpReasonV1.DestinationBlend else null
}

/** Geometry group 0 is independent of image group 1 and destination group 2. */
private fun PlanDraw.w5eGeometryTextureCountI32(): Int = when (this) {
    is W5bW4ePathDraw -> nativeColorPass.draw.w5eGeometryTextureCountI32()
    is ClippedPlanDraw -> source.w5eGeometryTextureCountI32() + strategy.w5eTextureCountI32()
    is ClippedGeneralPathDraw -> source.w5eGeometryTextureCountI32() + clip.w5eTextureCountI32()
    is ClippedBinaryMaskedPathDraw -> source.w5eGeometryTextureCountI32() + clip.w5eTextureCountI32()
    is BinaryMaskedPathDraw -> 1
    else -> 0
}

private fun ClipPlanStrategy.w5eTextureCountI32(): Int = when (this) {
    is ClipPlanStrategy.Mask, is ClipPlanStrategy.InverseMask -> 1
    is ClipPlanStrategy.Scissor -> child?.w5eTextureCountI32() ?: 0
    is ClipPlanStrategy.Stencil -> child?.w5eTextureCountI32() ?: 0
    is ClipPlanStrategy.InverseDomain -> 0
}
