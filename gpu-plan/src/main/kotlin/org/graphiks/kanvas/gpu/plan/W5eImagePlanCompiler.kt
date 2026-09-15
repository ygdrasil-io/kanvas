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
    override val materialAuthority: PlanDrawMaterialAuthority,
    public val execution: ImageSampleExecutionPlanV1,
    boundsI32: RectI32,
    override val blend: BlendPlan,
    override val coverage: CoveragePlan = CoveragePlan.FullOrScissor,
    override val sample: SamplePlan = SamplePlan.SingleSample,
    public val originalDraw: DrawNode,
    public val constructionEntry: ImageConstructionEntryV1,
) : PlanDraw {
    private val bounds = boundsI32.copy()
    public fun copyBoundsI32(): RectI32 = bounds.copy()
    override val color: ColorF32 get() = error("ImageDrawV1 has only issued image source authority")
    public fun authenticates(table: MaterialPlanTable): Boolean = when (val authority = materialAuthority) {
        is PlanDrawMaterialAuthority.MaterialV3 -> table.authenticatesImage(authority.ref,execution) &&
            authority.imageCoordinates === execution.coordinates
        is PlanDrawMaterialAuthority.MaterialV4 -> table.colorSourceProofV4(authority.ref).let { proof ->
            proof.authenticates(table,authority.ref,authority.coordinates) && proof.imageExecution === execution &&
                (authority.coordinates as? SourceCoordinatesV4.V3)?.plan === execution.coordinates
        }
        else -> false
    }
}

public enum class ImageNoOpReasonV1 { DestinationBlend, EmptyNineDestination, EmptyFamilyContributions }

/** Separate logical source envelope over the exact compiler-issued W4 geometry graph. */
public class W5eImageConstructionPlanV1 internal constructor(
    public val constructionGraph: RenderGraph,
    public val materialTable: MaterialPlanTable,
    draws: List<ImageDrawV1>,
    ordinarySources: Map<Int, PlanDrawMaterialAuthority>,
    noOpDraws: Map<Int, DrawNode>,
    public val peakBytesI64: Long,
    families: List<ImageFamilyCommandV1>,
    originalIndices: Map<Int, Int>,
    public val visualCommandCountI32: Int,
    public val originalSceneCanonicalId: CanonicalId,
) {
    private val draws = immutableList(draws)
    private val ordinarySources = ordinarySources.toMap()
    private val noOpDraws = noOpDraws.toMap()
    public val families: List<ImageFamilyCommandV1> = immutableList(families)
    private val originalIndices = originalIndices.toMap()
    public fun imageDraws(): List<ImageDrawV1> = draws
    public fun ordinarySources(): Map<Int, PlanDrawMaterialAuthority> = ordinarySources.toMap()
    public fun noOpDraws(): Map<Int, DrawNode> = noOpDraws.toMap()
    public fun omittedConstructionIndicesI32(): Set<Int> = noOpDraws.keys + families.flatMap { it.entries }
        .filter { it.cell is ImageCellPlanV1.OmittedV1 }.map { it.constructionIndexI32 }
    public fun colorConstructionOrderI32(): List<Int> = (draws.map { it.commandIndex } + ordinarySources.keys).sorted()
    public fun noOpReasons(): Map<Int, ImageNoOpReasonV1> = noOpDraws.mapValues {
        it.value.w5eNoOpReason() ?: ImageNoOpReasonV1.EmptyFamilyContributions }
    public val canonicalIdentity: String = "w5e-construction-v3:${originalSceneCanonicalId.value}:$visualCommandCountI32:" + constructionGraph.id.value + ":" +
        families.joinToString(";") { it.canonicalIdentity } + ":" + originalIndices.entries.joinToString(";") { "${it.key}:${it.value}" } + ":" +
        draws.joinToString(";") { "${it.commandIndex}:${it.originalDraw.canonicalId.value}:${it.execution.canonicalIdentity}" +
            if (it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4)
                ":color-v4:${materialTable.sourceIdentity(it.materialAuthority.materialPlanRef())}" else "" } +
        ordinarySources.entries.joinToString(";") { "ordinary:${it.key}:${materialTable.sourceIdentity(it.value.materialPlanRef())}" } +
        noOpDraws.entries.joinToString(";") { "noop:${it.key}:${it.value.canonicalId.value}:${it.value.w5eNoOpReason()}" }
    init {
        val geometry = constructionGraph.w5eColorDraws().associateBy { it.commandIndex }
        require((draws.isNotEmpty() || noOpDraws.isNotEmpty()) && draws.map { it.commandIndex }.distinct().size == draws.size &&
            draws.all { image ->
                val source = geometry[image.commandIndex]
                source != null && source.blend == image.blend && source.coverage == image.coverage && source.sample == image.sample &&
                    image.commandIndex == image.constructionEntry.constructionIndexI32 &&
                    image.originalDraw === image.constructionEntry.originalDraw &&
                    image.execution.atlasBlend?.color == image.constructionEntry.atlasEntryColor &&
                    image.execution.atlasBlend?.mode == image.constructionEntry.atlasEntryColor?.let { image.originalDraw.operationBlendMode } &&
                    families.single { it.commandIndex == image.constructionEntry.originalCommandIndexI32 }.entries.any { it === image.constructionEntry } &&
                    image.authenticates(materialTable)
            }) { W5eImagePlanDiagnostics.InvalidContract }
        require(peakBytesI64 <= constructionGraph.budget.maxFrameLocalBytes)
        val originalTable = constructionGraph.materialPlanTableOrNull()
        require(families.zipWithNext().all { (a, b) -> a.commandIndex < b.commandIndex } &&
            originalIndices.entries.zipWithNext().all { (a, b) -> a.key < b.key && a.value <= b.value } &&
            originalIndices.keys.containsAll(geometry.keys) && visualCommandCountI32 == originalIndices.values.distinct().size)
        require(noOpDraws.keys.intersect(geometry.keys).isEmpty() && noOpDraws.values.all { node -> node.w5eIsNoOp() ||
            families.singleOrNull { it.originalDraw === node }?.entries?.all { it.cell is ImageCellPlanV1.OmittedV1 } == true })
        require(families.flatMap { it.entries }.filterNot { it.cell is ImageCellPlanV1.OmittedV1 }.map { it.constructionIndexI32 }.toSet() ==
            draws.map { it.commandIndex }.toSet()) { W5eImagePlanDiagnostics.InvalidContract }
        for (image in draws) image.constructionEntry.cell?.let { cell ->
            require(image.execution.coordinates.copyDestinationF32() == cell.copyDestinationF32() &&
                (cell !is ImageCellPlanV1.Sampled || image.execution.coordinates.copySourceF32() == cell.copySourceF32()) &&
                image.execution.coordinates.canonicalIdentity == ImageCoordinatePlanV1.seal(image.constructionEntry.copyTransformF32(),
                    image.execution.coordinates.copySourceF32(), cell.copyDestinationF32()).canonicalIdentity) {
                W5eImagePlanDiagnostics.InvalidContract
            }
        }
        require(ordinarySources.keys == geometry.keys - draws.map { it.commandIndex }.toSet())
        for ((commandI32, projected) in ordinarySources) {
            val original = geometry.getValue(commandI32).materialAuthority
            require(requireNotNull(originalTable).sourceIdentity(original.materialPlanRef()) == materialTable.sourceIdentity(projected.materialPlanRef()) &&
                when (original) {
                    is PlanDrawMaterialAuthority.MaterialV1 -> projected is PlanDrawMaterialAuthority.MaterialV1 && original.coordinates === projected.coordinates
                    is PlanDrawMaterialAuthority.MaterialV2 -> projected is PlanDrawMaterialAuthority.MaterialV2 && original.coordinates === projected.coordinates
                    is PlanDrawMaterialAuthority.MaterialV4 -> projected is PlanDrawMaterialAuthority.MaterialV4 && original.coordinates === projected.coordinates
                    is PlanDrawMaterialAuthority.MaterialV5 -> projected is PlanDrawMaterialAuthority.MaterialV5 &&
                        projected === original && originalTable === materialTable &&
                        projected.ref == original.ref
                    else -> false
                }) { W5eImagePlanDiagnostics.InvalidContract }
        }
    }
}

/** Geometry authorities remain W4-owned; only source/material authority is overlaid by W5e. */
public class W5eImagePlanCompiler(private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot) : GpuPlanCompiler {
    public constructor() : this(RuntimeEffectSemanticCatalogSnapshot.Unbound)
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
            val maxLatticeCellsI64 = minOf(budget.maxFrameLocalBytes / 128L,
                maxOf(0L, (capabilities.maxUniformBufferBindingSizeBytesI64 ?: 0L) - 128L) / 80L)
            // Bound the expanded host inventory and all index arithmetic before allocating it.
            val constructionCountI64 = selected.scene.fold(0L) { countI64, command ->
                val geometry = (command as? SceneCommand.Draw)?.node?.geometry
                val contributionsI64 = when {
                    geometry is GeometryNode.Atlas -> maxOf(1L, geometry.entryCount.toLong())
                    geometry is GeometryNode.ImageLattice && geometry.cellRectCountI32 != null ->
                        Math.multiplyExact(Math.addExact(geometry.xDivCountI32.toLong(), 1L), Math.addExact(geometry.yDivCountI32.toLong(), 1L))
                    else -> 1L
                }
                Math.addExact(countI64, contributionsI64)
            }
            require(constructionCountI64 <= Int.MAX_VALUE.toLong() && Math.multiplyExact(constructionCountI64, 256L) <= budget.maxFrameLocalBytes) {
                W5eImagePlanDiagnostics.FrameBudget
            }
            // Metadata-only pre-copy admission. This is deliberately per contribution,
            // without content deduplication: it bounds large host upload copies as well
            // as the eventual source/upload/staging inventory. The small semantic lists
            // above are NOT a geometry upper bound; General/clip/stencil remain owned
            // and budgeted by their real compilers before native allocation.
            var preCopyBytesI64 = 0L
            for (command in selected.scene) {
                val node = (command as? SceneCommand.Draw)?.node ?: continue
                val lattice = node.geometry as? GeometryNode.ImageLattice
                val atlas = node.geometry as? GeometryNode.Atlas
                if (lattice == null && atlas == null || node.w5eIsNoOp()) continue
                val pixels = node.resource as? ImageResourceSnapshot.Pixels
                    ?: throw IllegalArgumentException(W5eImagePlanDiagnostics.ExternalResource)
                require(pixels.width <= capabilities.maxTextureDimension2D && pixels.height <= capabilities.maxTextureDimension2D) {
                    W5eImagePlanDiagnostics.TextureLimit
                }
                val bytesPerPixelI64 = if (pixels.pixelFormat == ImagePixelFormat.ALPHA_8) 1L else 4L
                val rowI64 = Math.multiplyExact(pixels.width.toLong(), bytesPerPixelI64)
                val uploadI64 = Math.multiplyExact(rowI64, pixels.height.toLong())
                val alignmentI64 = lcmI64(256L, capabilities.copyBytesPerRowAlignment.toLong())
                val alignedRowI64 = Math.addExact(rowI64, (alignmentI64 - rowI64 % alignmentI64) % alignmentI64)
                val stagingI64 = Math.multiplyExact(alignedRowI64, pixels.height.toLong())
                require(uploadI64 <= Int.MAX_VALUE.toLong() && stagingI64 <= minOf(Int.MAX_VALUE.toLong(), capabilities.maxBufferSizeBytes)) {
                    W5eImagePlanDiagnostics.Capability
                }
                val countI64 = lattice?.let { Math.multiplyExact(Math.addExact(it.xDivCountI32.toLong(), 1L),
                    Math.addExact(it.yDivCountI32.toLong(), 1L)) } ?: requireNotNull(atlas).entryCount.toLong()
                val regular = lattice != null && lattice.cellRectCountI32 == null
                val contributionsI64 = if (regular) 1L else countI64
                val sourceI64 = if (regular) Math.addExact(128L, Math.multiplyExact(countI64, 80L))
                    else if (lattice != null) 208L else 128L
                val uniformLimitI64 = capabilities.maxUniformBufferBindingSizeBytesI64 ?: 0L
                val childI64 = if (bytesPerPixelI64 != 1L) 0L else if (node.paint?.shader == null) 16L
                    else maxOf(0L, uniformLimitI64 - sourceI64)
                val uniformI64 = Math.addExact(sourceI64, childI64)
                require(uniformI64 <= minOf(uniformLimitI64, capabilities.maxBufferSizeBytes)) { W5eImagePlanDiagnostics.BindingLimit }
                preCopyBytesI64 = Math.addExact(preCopyBytesI64, Math.multiplyExact(contributionsI64,
                    Math.addExact(Math.addExact(uploadI64, stagingI64), uniformI64)))
                require(preCopyBytesI64 <= budget.maxFrameLocalBytes) { W5eImagePlanDiagnostics.FrameBudget }
            }
            val noOpDraws = linkedMapOf<Int, DrawNode>()
            val sourceNodes = linkedMapOf<Int, DrawNode>()
            val constructionEntries = linkedMapOf<Int, ImageConstructionEntryV1>()
            val originalIndices = linkedMapOf<Int, Int>()
            val families = mutableListOf<ImageFamilyCommandV1>()
            val projectedCommands = mutableListOf<SceneCommand>()
            fun annotation(indexI32: Int): SceneCommand = SceneCommand.Annotation.of(RectF32.ofLTRB(0f, 0f, 0f, 0f), "w5e.omitted-contribution", indexI32.toString())
            for ((originalIndexI32, command) in selected.scene.withIndex()) {
                if (command !is SceneCommand.Draw) { projectedCommands += command; continue }
                val node = command.node
                val pixels = node.resource as? ImageResourceSnapshot.Pixels
                val lattice = node.geometry as? GeometryNode.ImageLattice
                val latticeCells = lattice?.let { ImageCellDecomposerV1.lattice(requireNotNull(pixels).width, pixels.height, it,
                    if (it.cellRectCountI32 == null) maxLatticeCellsI64 else budget.maxFrameLocalBytes / 256L) }
                val firstConstructionI32 = projectedCommands.size
                if (node.w5eIsNoOp()) {
                    originalIndices[firstConstructionI32] = originalIndexI32
                    noOpDraws[firstConstructionI32] = node
                    projectedCommands += annotation(firstConstructionI32)
                    if (isImageSource(node)) families += ImageFamilyCommandV1(originalIndexI32, node, emptyList())
                    continue
                }
                if (!isImageSource(node)) {
                    originalIndices[firstConstructionI32] = originalIndexI32
                    projectedCommands += command
                    continue
                }
                val entries = mutableListOf<ImageConstructionEntryV1>()
                fun append(cell: ImageCellPlanV1?, transform: org.graphiks.math.matrix.Matrix3x3F32,
                    color: ColorARGB? = null) {
                    val constructionIndexI32 = projectedCommands.size
                    originalIndices[constructionIndexI32] = originalIndexI32
                    val destination = cell?.copyDestinationF32()
                    val empty = destination?.let { it.left == it.right || it.top == it.bottom } == true
                    val retainedCell = if (empty && cell !is ImageCellPlanV1.OmittedV1)
                        ImageCellPlanV1.OmittedV1(requireNotNull(destination), requireNotNull(cell).outerEdges) else cell
                    val entry = ImageConstructionEntryV1(originalIndexI32, entries.size, constructionIndexI32, node, retainedCell, transform, color)
                    entries += entry
                    constructionEntries[constructionIndexI32] = entry
                    if (retainedCell is ImageCellPlanV1.OmittedV1) projectedCommands += annotation(constructionIndexI32)
                    else {
                        sourceNodes[constructionIndexI32] = node
                        projectedCommands += SceneCommand.Draw(projectGeometry(node, destination, transform))
                    }
                }
                when (val geometry = node.geometry) {
                    is GeometryNode.Atlas -> for (atlasEntry in geometry) {
                        val source = atlasEntry.copySource()
                        require(listOf(source.left, source.top, source.right, source.bottom).all(Float::isFinite) && source.isSorted()) {
                            "invalid.material.image.atlas-source"
                        }
                        val destination = RectF32.ofLTRB(0f, 0f, source.right - source.left, source.bottom - source.top)
                        append(ImageCellPlanV1.Sampled(source, destination, List(4) { true }), node.transform * atlasEntry.transform, atlasEntry.color)
                    }
                    is GeometryNode.ImageLattice -> if (geometry.cellRectCountI32 != null) requireNotNull(latticeCells).forEach {
                        append(it, node.transform)
                    } else append(null, node.transform)
                    else -> append(null, node.transform)
                }
                families += ImageFamilyCommandV1(originalIndexI32, node, entries)
                if (entries.isEmpty()) {
                    originalIndices[firstConstructionI32] = originalIndexI32
                    projectedCommands += annotation(firstConstructionI32)
                }
                if (entries.all { it.cell is ImageCellPlanV1.OmittedV1 }) noOpDraws[firstConstructionI32] = node
            }
            val projected = SceneSnapshot.of(selected.scene.extent, selected.scene.colorSpace, projectedCommands)
            val metadataOnly = projected.none { it is SceneCommand.Draw }
            val deferredColor = !metadataOnly && selected.scene.any { command ->
                (command as? SceneCommand.Draw)?.node?.let { node -> node.paint?.colorFilter != null ||
                    hasDeferredColor(node.material) || node.paint?.shader?.let(::hasDeferredColor) == true } == true
            }
            val emptyCompiler = W3SolidRectPlanCompiler(runtimeCatalog)
            val compiler: GpuPlanCompiler = if (metadataOnly) emptyCompiler else CapabilityCompilerChain.of(listOf(
                W5aCompositePlanCompiler(constructionEntries,runtimeCatalog), W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler(),
                W4cPathFillPlanCompiler(), W4dPathStrokePlanCompiler(), W4dGeneralPathPlanCompiler()),runtimeCatalog)
            val selection = if (metadataOnly) {
                require(noOpDraws.isNotEmpty() && selected.scene.all { command ->
                    command !is SceneCommand.Draw || noOpDraws.values.any { it === command.node }
                }) { W5eImagePlanDiagnostics.InvalidContract }
                emptyCompiler.selectEmptyW5eConstruction(projected, selected.target)
            } else compiler.select(projected, selected.target)
            if (selection !is GpuPlanSelection.Candidate) {
                // Invalid image inverses retain image-specific public diagnostics even when
                // the geometry capability cannot construct the corresponding footprint.
                for ((constructionIndexI32, node) in sourceNodes) when (val source = EffectiveMaterialPlanner.planW5eImageSource(node,
                    RectI32(0, 0, selected.target.extent.width, selected.target.extent.height), maxLatticeCellsI64,
                    constructionEntries.getValue(constructionIndexI32),runtimeCatalog)) {
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
            if (deferredColor) {
                val chain = requireNotNull(compiler as? CapabilityCompilerChain) { W5fPlanDiagnostics.Schema }
                val layout = when (val result = chain.constructSourceLayout(selection.candidate,capabilities,budget) { lane ->
                    lane.overlayImageSources { geometryDraw ->
                        sourceNodes[geometryDraw.commandIndex]?.let { original ->
                            val bounds = geometryDraw.w5eDeviceBoundsI32()
                            val metadata = when (val captured = EffectiveMaterialPlanner.describeW5eImageSource(original,bounds,
                                maxLatticeCellsI64,constructionEntries.getValue(geometryDraw.commandIndex),true,runtimeCatalog)) {
                                is SourceConstructionResultV4.Built -> captured.value
                                is SourceConstructionResultV4.Refused -> throw IllegalArgumentException(captured.diagnosticCode)
                            }
                            val textureCount = 1 + (if (geometryDraw.blend is BlendPlan.DestinationReadV1) 1 else 0) +
                                geometryDraw.w5eGeometryTextureCountI32()
                            require(capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= textureCount } == true) {
                                W5eImagePlanDiagnostics.BindingLimit
                            }
                            MaterialSourceConstructionV4.captureImage(metadata,RectF32.ofLTRB(bounds.left.toFloat(),bounds.top.toFloat(),
                                bounds.right.toFloat(),bounds.bottom.toFloat()),geometryDraw.blend,runtimeCatalog)
                        }
                    }
                }) {
                    is RenderPlanResult.Ready -> result.plan
                    is RenderPlanResult.GapNotMigrated -> return result
                    is RenderPlanResult.GapOnPromotedScope -> return result
                    is RenderPlanResult.InvalidScene -> return result
                    is RenderPlanResult.ResourceLimitExceeded -> return result
                }
                return when (val bound = layout.prepareImageFrame { construction,table,peak ->
                    val geometry = construction.w5eColorDraws().associateBy { it.commandIndex }
                    val images = sourceNodes.map { (command,original) ->
                        val draw = requireNotNull(geometry[command]) { W5eImagePlanDiagnostics.InvalidContract }
                        val authority = draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV4
                            ?: error(W5fPlanDiagnostics.Schema)
                        val execution = requireNotNull(table.colorSourceProofV4(authority.ref).imageExecution) { W5fPlanDiagnostics.Schema }
                        ImageDrawV1(command,authority,execution,draw.w5eDeviceBoundsI32(),draw.blend,draw.coverage,
                            draw.sample,original,constructionEntries.getValue(command))
                    }
                    val ordinary = geometry.filterKeys { it !in sourceNodes }.mapValues { it.value.materialAuthority }
                    RenderGraph.issueW5e(W5eImageConstructionPlanV1(construction,table,images,ordinary,noOpDraws,peak,
                        families,originalIndices,selected.scene.count { it is SceneCommand.Draw },selected.scene.canonicalId))
                }) {
                    is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(bound.value)
                    is SourceConstructionResultV4.Refused -> bound.failure
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
                val source = when (val result = EffectiveMaterialPlanner.planW5eImageSource(node, bounds, maxLatticeCellsI64,
                    constructionEntries.getValue(commandI32),runtimeCatalog)) {
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
                    execution, bounds, draw.blend, draw.coverage, draw.sample, node, constructionEntries.getValue(commandI32))
            }
            // An all-DST frame has no material source. The inert table satisfies the envelope
            // schema only; it issues no packet, raw source requirement, upload or native binding.
            val emptyTable = MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1)))
            val geometryTable = construction.materialPlanTableOrNull() ?: emptyTable
            val interned = MaterialPlanTable.intern(listOf(if (entries.isEmpty()) emptyTable else MaterialPlanTable.of(entries), geometryTable))
            val table = interned.table
            val remappedImages = images.map { image ->
                val ref = interned.remap(0, image.materialAuthority.materialPlanRef())
                val execution = (table.entry(ref).bindings as ImageSampleV3).execution
                ImageDrawV1(image.commandIndex, PlanDrawMaterialAuthority.MaterialV3(ref, execution.coordinates), execution,
                    image.copyBoundsI32(), image.blend, image.coverage, image.sample, image.originalDraw, image.constructionEntry)
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
            val imageRequirements = remappedImages.map { RawMaterialRequirementsV2.of(table, it.materialAuthority.materialPlanRef()) }
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
                val ref = image.materialAuthority.materialPlanRef()
                val execution = finalExecutions.getOrPut(ref) { ImageSampleExecutionPlanV1(source.upload,
                    source.coordinates, source.colorAlpha, source.numericAuthority, source.paintAlphaF32,
                    source.childSourceIdentity, peakI64, source.sampling, source.tileModes, source.atlasBlend) }
                finalEntries[ref.indexI32] = table.entry(ref).copy(bindings = ImageSampleV3.of(execution))
                ImageDrawV1(image.commandIndex, image.materialAuthority, execution, image.copyBoundsI32(),
                    image.blend, image.coverage, image.sample, image.originalDraw, image.constructionEntry)
            }
            RenderPlanResult.Ready(RenderGraph.issueW5e(W5eImageConstructionPlanV1(construction,
                MaterialPlanTable.of(finalEntries), finalImages, ordinarySources, noOpDraws, peakI64, families, originalIndices,
                selected.scene.count { it is SceneCommand.Draw }, selected.scene.canonicalId)))
        } catch (_: ArithmeticException) {
            RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(W5eImagePlanDiagnostics.FrameBudget)))
        } catch (failure: IllegalArgumentException) {
            val code = failure.message.orEmpty().takeIf { it.startsWith("unsupported.") || it.startsWith("resource.") || it.startsWith("invalid.") }
                ?: W5eImagePlanDiagnostics.InvalidContract
            if (code.startsWith("resource.")) RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(code)))
            else RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(code)))
        }
    }

    private fun projectGeometry(node: DrawNode, physicalDestination: RectF32? = null,
        physicalTransform: org.graphiks.math.matrix.Matrix3x3F32 = node.transform): DrawNode {
        val node = node.copy(transform = physicalTransform)
        val neutral = MaterialNode.Transparent
        val paint = node.paint?.copy(color = ColorARGB.Transparent, shader = neutral,colorFilter=null) ?: PaintNode(
            ColorARGB.Transparent, neutral, BlendMode.SRC_OVER, null, null, null, null, null,
            PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, node.coverage == CoverageRequest.ANTIALIASED)
        if (node.origin != DrawOrigin.IMAGE && node.origin != DrawOrigin.IMAGE_NINE && node.origin != DrawOrigin.IMAGE_LATTICE && node.origin != DrawOrigin.ATLAS)
            return node.copy(material = neutral, paint = paint,effects=EffectStack.Empty)
        val destination = physicalDestination ?: when (val source = node.geometry) {
            is GeometryNode.ImagePatch -> source.copyDestination()
            is GeometryNode.ImageNine -> source.copyDestination()
            is GeometryNode.ImageLattice -> source.copyDestination()
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
        // An omitted image Paint has the same hard-edge construction as the
        // neutral paint synthesized above. Keep DEFAULT on the original image
        // draw; only its physical construction must name the existing coverage.
        val coverage = if (node.paint == null && node.coverage == CoverageRequest.DEFAULT)
            CoverageRequest.HARD_EDGE else node.coverage
        return node.copy(geometry = geometry, origin = if (rectLane) DrawOrigin.RECT else DrawOrigin.PATH,
            coverage = coverage,
            resource = null, material = neutral, paint = paint, operationBlendMode = null,effects=EffectStack.Empty)
    }
    private fun isImageSource(node: DrawNode): Boolean {
        var source = node.material
        repeat(65) {
            source = when (val current = source) {
                is MaterialNode.WithLocalMatrix -> current.material
                is MaterialNode.Opacity -> current.material
                is MaterialNode.WithColorFilter -> current.material
                is MaterialNode.WithWorkingColorSpace -> current.material
                else -> return current is MaterialNode.ImageSample
            }
        }
        return false
    }
    private fun admittedSource(node: DrawNode): Boolean {
        val nine = node.geometry as? GeometryNode.ImageNine
        if (nine != null && nine.sampling != ImageSampling.Nearest) return false
        val direct = node.origin == DrawOrigin.IMAGE && node.geometry is GeometryNode.ImagePatch ||
            node.origin == DrawOrigin.IMAGE_NINE && node.geometry is GeometryNode.ImageNine ||
            node.origin == DrawOrigin.IMAGE_LATTICE && node.geometry is GeometryNode.ImageLattice
            || node.origin == DrawOrigin.ATLAS && node.geometry is GeometryNode.Atlas
        if (!direct && !(node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect ||
                node.origin == DrawOrigin.PATH && node.geometry is GeometryNode.Path)) return false
        val paint = node.paint
        if (paint != null && (paint.style != PaintStyleNode.FILL || paint.blender != null ||
                paint.maskFilter != null || paint.imageFilter != null || paint.pathEffect != null) ||
            !colorFilterEffectsMatchPaint(node) || node.operationBlendMode != null && node.origin != DrawOrigin.ATLAS) return false
        var source = node.material
        repeat(65) {
            source = when (val current = source) {
                is MaterialNode.WithLocalMatrix -> current.material
                is MaterialNode.Opacity -> current.material
                is MaterialNode.WithColorFilter -> current.material
                is MaterialNode.WithWorkingColorSpace -> current.material
                is MaterialNode.ImageSample -> return current.image is ImageResourceSnapshot.Pixels
                else -> return false
            }
        }
        return false
    }
    private fun hasDeferredColor(material: MaterialNode): Boolean = when (material) {
        is MaterialNode.Blend,is MaterialNode.PerlinNoise,is MaterialNode.FractalNoise -> true
        is MaterialNode.WithColorFilter,is MaterialNode.WithWorkingColorSpace -> true
        is MaterialNode.Opacity -> hasDeferredColor(material.material)
        is MaterialNode.WithLocalMatrix -> hasDeferredColor(material.material)
        is MaterialNode.CoordClamp -> hasDeferredColor(material.material)
        is MaterialNode.LinearGradient -> material.interpolation != ColorInterpolation.SRGB
        is MaterialNode.RadialGradient -> material.interpolation != ColorInterpolation.SRGB
        is MaterialNode.SweepGradient -> material.interpolation != ColorInterpolation.SRGB
        is MaterialNode.ConicalGradient -> material.interpolation != ColorInterpolation.SRGB
        else -> false
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

/** Original image upload row-alignment arithmetic, shared with the final metadata inventory. */
internal fun lcmI64(leftI64: Long, rightI64: Long): Long {
    var aI64 = leftI64; var bI64 = rightI64
    while (bI64 != 0L) { val restI64 = aI64 % bI64; aI64 = bI64; bI64 = restI64 }
    return Math.multiplyExact(leftI64 / aI64, rightI64)
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
