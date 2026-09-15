package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSemanticValidationResult
import org.graphiks.kanvas.render.ir.SceneSemanticValidator
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.mapPathFillInputF64

/** Closed W4c capability for bounded solid hard-edge path fills. */
public class W4cPathFillPlanCompiler internal constructor(private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot) : GpuPlanCompiler {
    public constructor() : this(RuntimeEffectSemanticCatalogSnapshot.Unbound)
    override fun select(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalidSelection("Scene and target descriptors disagree")
        }
        when (val validation = SceneSemanticValidator.validate(scene)) {
            is SceneSemanticValidationResult.Invalid -> return invalidSelection(validation.message)
            SceneSemanticValidationResult.Valid -> Unit
        }
        if (target.colorSpace != ColorSpace.SRGB) {
            return notCandidate("W4c supports only sRGB targets")
        }
        return when (val recognition = recognize(scene)) {
            is Recognition.MaterialRefused -> GpuPlanSelection.MaterialOnlyRefusal(CAPABILITY_ID, scene.canonicalId, target, recognition.refusals)
            is Recognition.Accepted -> GpuPlanSelection.Candidate(
                W4cCandidate(this, scene.canonicalId, target, recognition.draws, recognition.materialPlanTable, recognition.capabilityId,recognition.sourceTable),
            )
            is Recognition.Gap -> notCandidate(recognition.message)
            is Recognition.Invalid -> invalidSelection(recognition.message)
            is Recognition.ResourceLimit -> resourceSelection(recognition.message)
        }
    }

    private fun recognize(scene: SceneSnapshot): Recognition {
        if (scene.colorSpace != ColorSpace.SRGB) return Recognition.Gap("W4c supports only sRGB scenes")
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val draws = mutableListOf<SealedDraw>()
        val materialEntries = mutableListOf<MaterialPlanEntry>()
        val sources = mutableListOf<MaterialSourceConstructionV4>()
        val materialRefusals = mutableListOf<EffectiveMaterialPlanner.Result.Refused>()
        var frameAttemptedEdgesBeforeI32 = 0
        var elidedNoOpsI32 = 0
        for ((commandIndex, command) in scene.withIndex()) {
            when (command) {
                is SceneCommand.Draw -> {
                    if (draws.size + materialRefusals.size + elidedNoOpsI32 == MAX_DRAWS) {
                        return Recognition.Gap("W4c accepts at most 512 visual path draws")
                    }
                    when (
                        val draw = recognizeDraw(
                            node = command.node,
                            commandIndex = commandIndex,
                            targetBounds = targetBounds,
                            frameAttemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
                            materialEntries = materialEntries,
                            sources = sources,
                        )
                    ) {
                        is DrawRecognition.NoOp -> {
                            elidedNoOpsI32++
                            frameAttemptedEdgesBeforeI32 = draw.frameAttemptedEdgesAfterI32
                        }
                        is DrawRecognition.MaterialRefused -> {
                            materialRefusals += draw.refusal
                            frameAttemptedEdgesBeforeI32 = draw.frameAttemptedEdgesAfterI32
                        }
                        is DrawRecognition.Accepted -> {
                            draws += draw.draw
                            frameAttemptedEdgesBeforeI32 = draw.frameAttemptedEdgesAfterI32
                        }
                        is DrawRecognition.Gap -> return Recognition.Gap(draw.message)
                        is DrawRecognition.Invalid -> return Recognition.Invalid(draw.message)
                        is DrawRecognition.ResourceLimit -> return Recognition.ResourceLimit(draw.message)
                    }
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) {
                    return Recognition.Invalid("Transform metadata is non-finite")
                }
                is SceneCommand.SetClip -> if (!finiteMetadataClip(command.clip)) {
                    return Recognition.Invalid("Clip metadata is non-finite")
                }
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) {
                    return Recognition.Invalid("Annotation bounds are non-finite")
                }
                else -> return Recognition.Gap("Scene command is outside W4c")
            }
        }
        if (materialRefusals.isNotEmpty()) return Recognition.MaterialRefused(materialRefusals)
        return if (draws.isEmpty() && elidedNoOpsI32 == 0) {
            Recognition.Gap("W4c requires at least one visual path draw")
        } else {
            val pending = sources.any { it.pending }
            Recognition.Accepted(if (pending) draws.mapIndexed { ordinal,draw -> draw.copy(material=MaterialPlanRef(ordinal)) } else draws,
                materialEntries.takeIf { it.isNotEmpty() && !pending }?.let(MaterialPlanTable::of),
                if (elidedNoOpsI32 > 0 || pending || materialEntries.any { it.stopSlab != null } || draws.any { it.blend != BlendPlan.LegacySrcOverV1 }) W5B_CAPABILITY_ID else CAPABILITY_ID,
                (MaterialSourceConstructionTableV4.of(sources) as SourceConstructionResultV4.Built).value)
        }
    }

    private fun recognizeDraw(
        node: DrawNode,
        commandIndex: Int,
        targetBounds: RectI32,
        frameAttemptedEdgesBeforeI32: Int,
        materialEntries: MutableList<MaterialPlanEntry>,
        sources: MutableList<MaterialSourceConstructionV4>,
    ): DrawRecognition {
        val geometry = node.geometry as? GeometryNode.Path
            ?: return DrawRecognition.Gap("Draw geometry is outside W4c")
        if (!finite(geometry.path)) return DrawRecognition.Invalid("Path geometry is non-finite")
        if (!finite(node.transform)) return DrawRecognition.Invalid("Draw transform is non-finite")
        val clip = when (val recognized = recognizeClip(node.clip)) {
            is ClipRecognition.Accepted -> recognized.bounds
            is ClipRecognition.Gap -> return DrawRecognition.Gap(recognized.message)
            is ClipRecognition.Invalid -> return DrawRecognition.Invalid(recognized.message)
        }
        if (node.origin != DrawOrigin.PATH) {
            return DrawRecognition.Gap("Path provenance is outside W4c")
        }
        if (geometry.path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD)) {
            return DrawRecognition.Gap("Inverse path fills are outside W4c")
        }
        if (node.coverage != CoverageRequest.HARD_EDGE) {
            return DrawRecognition.Gap("Antialiased path coverage is outside W4c")
        }
        if (!(node.transform.isIdentity || node.transform.isScaleTranslate())) {
            return DrawRecognition.Gap("Transform is outside W4c")
        }
        if (!supportsSolidFill(node)) {
            return DrawRecognition.Gap("Draw material, paint, blend, or effect is outside W4c")
        }

        val pathSnapshot = snapshotPath(geometry.path)
        val input = try {
            node.transform.mapPathFillInputF64(pathSnapshot)
        } catch (_: IllegalArgumentException) {
            return DrawRecognition.Invalid("Path transform could not produce finite device geometry")
        }
        return when (
            val prepared = preparePathFillGeometryF32(
                inputF64 = input,
                frameAttemptedEdgesBeforeI32 = frameAttemptedEdgesBeforeI32,
            )
        ) {
            is PathFillPreparationResult.InvalidScene ->
                DrawRecognition.Invalid("Path preparation found non-finite device geometry")
            is PathFillPreparationResult.ResourceLimitExceeded ->
                DrawRecognition.ResourceLimit("Path preparation exceeded a W4c resource limit")
            is PathFillPreparationResult.Empty ->
                DrawRecognition.Gap("Path preparation retained no fill contour")
            is PathFillPreparationResult.Ready -> {
                val geometryF32 = prepared.geometryF32
                val strategy = when {
                    geometryF32.copyDirectTriangleF32OrNull() != null -> PathFillStrategy.DirectTriangle
                    geometryF32.copyStencilEdgeFanF32OrNull() != null -> {
                        if (
                            geometryF32.fillRule == FillRule.WINDING &&
                            geometryF32.emittedNonZeroClosedEdgeCountI32 > MAX_WINDING_STENCIL_EDGES
                        ) {
                            return DrawRecognition.ResourceLimit(
                                "Winding path exceeds the W4c stencil edge limit",
                            )
                        }
                        PathFillStrategy.StencilCover
                    }
                    else -> return DrawRecognition.ResourceLimit("Path preparation returned no renderable payload")
                }
                val targetScissor = intersect(geometryF32.copyConservativeScissorI32(), targetBounds)
                    ?: return DrawRecognition.Gap("Path is outside the target")
                val scissor = clip?.let { intersect(targetScissor, it) } ?: targetScissor
                if (scissor.isEmpty) return DrawRecognition.Gap("Path is fully clipped out")
                val attemptedAfter = try {
                    Math.addExact(frameAttemptedEdgesBeforeI32, prepared.attemptedEdgeCountI32)
                } catch (_: ArithmeticException) {
                    return DrawRecognition.ResourceLimit("Frame attempted-edge count overflowed")
                }
                val source = when (val planned = EffectiveMaterialPlanner.normalizeSourcesV4(node, FORMAT.blendTargetClampV1(), scissor,runtimeCatalog=runtimeCatalog)) {
                    EffectiveMaterialPlanner.SourceNormalizationV4.NoOp -> return DrawRecognition.NoOp(attemptedAfter)
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Refused -> return DrawRecognition.MaterialRefused(
                        EffectiveMaterialPlanner.Result.Refused(planned.diagnosticCode), attemptedAfter)
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Source -> planned.captured
                }
                sources += source
                val resolved = source.resolvedSource
                DrawRecognition.Accepted(
                    SealedDraw(
                        commandIndex = commandIndex,
                        pathF32 = pathSnapshot,
                        transform = node.transform.copy(),
                        material = if (resolved == null) MaterialPlanRef(sources.lastIndex)
                            else appendMaterialPlan(materialEntries,resolved.table,resolved.root),
                        coordinates = MaterialCoordinatePlanV1.fromCtm(node.transform),
                        coordinatesV2 = resolved?.table?.coordinatesV2(resolved.root),
                        coordinatesV4 = if (source.pending) source.coordinates else resolved?.table?.coordinatesV4(resolved.root),
                        geometryF32 = geometryF32,
                        strategy = strategy,
                        scissorI32 = scissor.copy(),
                        blend = if (source.blend is BlendPlan.FixedFunctionV1 && source.blend.mode == BlendMode.SRC_OVER)
                            BlendPlan.LegacySrcOverV1 else source.blend,
                    ),
                    attemptedAfter,
                )
            }
        }
    }

    private fun supportsSolidFill(node: DrawNode): Boolean {
        if (
            !colorFilterEffectsMatchPaint(node) ||
            node.resource != null ||
            node.operationBlendMode != null ||
            !w4cBlend(node.blend)
        ) {
            return false
        }
        val paint = node.paint ?: return false
        return finite(paint) &&
            paint.blender == null &&
            paint.maskFilter == null &&
            paint.pathEffect == null &&
            paint.imageFilter == null &&
            paint.style == PaintStyleNode.FILL &&
            materialMatchesPaintAuthority(node)
    }

    private fun recognizeClip(clip: ClipStackNode): ClipRecognition = when (clip) {
        ClipStackNode.Empty -> ClipRecognition.Accepted(null)
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            when {
                !finite(bounds) -> ClipRecognition.Invalid("Clip bounds are non-finite")
                clip.antiAlias -> ClipRecognition.Gap("Antialiased clip is outside W4c")
                else -> integralRect(bounds)?.let(ClipRecognition::Accepted)
                    ?: ClipRecognition.Gap("Clip is not a non-empty I32 device rectangle")
            }
        }
        is ClipStackNode.Operations -> {
            if (!finiteMetadataClip(clip)) {
                ClipRecognition.Invalid("Complex clip metadata is non-finite")
            } else {
                ClipRecognition.Gap("Complex clips are outside W4c")
            }
        }
    }

    private fun snapshotPath(path: PathF32): PathF32 =
        PathBuilder(path.fillRule).addPath(path).build()

    private fun integralRect(bounds: RectF32): RectI32? {
        if (!finite(bounds)) return null
        val values = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val converted = values.map { value ->
            val long = value.toLong()
            if (long.toFloat() != value || long !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                return null
            }
            long.toInt()
        }
        return RectI32(converted[0], converted[1], converted[2], converted[3]).takeUnless { it.isEmpty64() }
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? =
        first.copy().takeIf { it.intersect(second) }

    private fun finiteMetadataClip(clip: ClipStackNode): Boolean = when (clip) {
        ClipStackNode.Empty -> true
        is ClipStackNode.DeviceRect -> finite(clip.copyBounds())
        is ClipStackNode.Operations -> clip.all { finiteMetadataClipGeometry(it.geometry) }
    }

    private fun finiteMetadataClipGeometry(geometry: GeometryNode): Boolean = when (geometry) {
        is GeometryNode.Rect -> finite(geometry.copyBounds())
        is GeometryNode.RRect -> finite(geometry.copyShape())
        is GeometryNode.Path -> finite(geometry.path)
        else -> false
    }

    private fun finite(shape: RRectF32): Boolean = finite(shape.rect) && listOf(
        shape.topLeft.x,
        shape.topLeft.y,
        shape.topRight.x,
        shape.topRight.y,
        shape.bottomRight.x,
        shape.bottomRight.y,
        shape.bottomLeft.x,
        shape.bottomLeft.y,
    ).all(Float::isFinite)

    private fun finite(path: PathF32): Boolean = path.all { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> finite(segment.point)
            is PathSegmentF32.LineTo -> finite(segment.point)
            is PathSegmentF32.QuadTo -> finite(segment.control) && finite(segment.point)
            is PathSegmentF32.CubicTo ->
                finite(segment.control1) && finite(segment.control2) && finite(segment.point)
            is PathSegmentF32.ArcTo ->
                finite(segment.point) &&
                    segment.radius.x.isFinite() &&
                    segment.radius.y.isFinite() &&
                    segment.xAxisRotation.isFinite()
            PathSegmentF32.Close -> true
        }
    }

    private fun finite(paint: PaintNode): Boolean =
        paint.strokeWidth.isFinite() && paint.strokeMiter.isFinite()

    private fun finite(point: Point2F32): Boolean = point.x.isFinite() && point.y.isFinite()

    private fun finite(bounds: RectF32): Boolean =
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)

    private fun finite(matrix: Matrix3x3F32): Boolean = listOf(
        matrix.sx,
        matrix.kx,
        matrix.tx,
        matrix.ky,
        matrix.sy,
        matrix.ty,
        matrix.persp0,
        matrix.persp1,
        matrix.persp2,
    ).all(Float::isFinite)

    private fun w4cBlend(blend: BlendNode): Boolean = when (blend) {
        BlendNode.SrcOver -> true
        is BlendNode.Mode -> true
        is BlendNode.Paint -> blend.blender == null
        is BlendNode.Custom -> false
    }

    private fun appendMaterialPlan(entries: MutableList<MaterialPlanEntry>, incoming: MaterialPlanTable, root: MaterialPlanRef): MaterialPlanRef {
        val offset = entries.size
        incoming.entries().forEach { entry -> entries += entry }
        return MaterialPlanRef(offset + root.indexI32)
    }

    private fun materialMatchesPaintAuthority(node: DrawNode): Boolean {
        val paint = node.paint ?: return false
        val paintMaterial = paint.shader ?: MaterialNode.Solid(paint.color)
        return node.material.canonicalId == paintMaterial.canonicalId
    }

    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(),
        capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes,
        capabilities.bufferAllocationPolicy.indexFloorBytes,
        capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { it > 0L && it and (it - 1L) == 0L }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> =
        if (hasPendingSources(candidate)) constructSources(candidate,capabilities,budget).prepareAndPublishSourcesV4()
        else construct(candidate,capabilities,budget).publishConstructionResult()

    internal fun hasPendingSources(candidate: GpuPlanCandidate): Boolean =
        (candidate as? W4cCandidate)?.sourceTable?.sources()?.any { it.pending } == true

    internal fun construct(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraphConstruction> = constructChecked(candidate,capabilities,budget,
        clear = { selected,extent -> RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.constructClearOnly(
            PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),W5B_CAPABILITY_ID,
            extent,capabilities,budget,selected.materialPlanTable))) },
        destination = { selected,extent,draws,resources,data,depth,memory ->
            require(!hasPendingSources(selected)) { W5fPlanDiagnostics.Schema }
            RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(W5bDestinationGraphSealer.construct(
                PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),W5B_CAPABILITY_ID,
                extent,capabilities,budget,draws,selected.materialPlanTable,memory.targetBytes,memory.readbackBytes,
                memory.readbackBytesPerRow,resources,drawDataResources=data,depthStencilByCommandI32=depth)))
        }) { selected,extent,topology ->
            require(!hasPendingSources(selected)) { W5fPlanDiagnostics.Schema }
            RenderPlanResult.Ready(RenderGraph.construct(PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),
                CAPABILITY_ID,extent,FORMAT,capabilities,budget,selected.draws.size,topology.resources,topology.passes,
                topology.dependencies,topology.peakI64,selected.materialPlanTable))
        }

    internal fun constructSources(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> = constructChecked(candidate,capabilities,budget,
        clear = { selected,extent -> SourceDeferredRenderConstructionV4.clearOnly(
            PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),W5B_CAPABILITY_ID,
            extent,capabilities,budget) },
        destination = { selected,extent,draws,resources,data,depth,memory ->
            val symbolic = draws.mapIndexed { ordinal,draw -> draw.withMaterialRef(MaterialPlanRef(ordinal)) }
            deferred(selected,extent,capabilities,budget,W5bDestinationGraphSealer.describeSources(W5B_CAPABILITY_ID,
                extent,capabilities,budget,symbolic,memory.targetBytes,memory.readbackBytes,memory.readbackBytesPerRow,
                resources,drawDataResources=data,depthStencilByCommandI32=depth))
        }) { selected,extent,topology ->
            val roots = selected.draws.map { it.material }
            deferred(selected,extent,capabilities,budget,W5bDestinationGraphSealer.DestinationTopologyV4(topology.format,
                topology.resources,remapSourcePassesV4(topology.passes) { ref ->
                    MaterialPlanRef(roots.indexOf(ref).also { require(it >= 0) }) },topology.dependencies,topology.peakI64))
        }

    private fun deferred(selected: W4cCandidate,extent: SizeI32,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        topology: W5bDestinationGraphSealer.DestinationTopologyV4): RenderPlanResult<SourceDeferredRenderConstructionV4> =
        when (val result = SourceDeferredRenderConstructionV4.of(
            PlanId(planIdentity(selected.sceneCanonicalId,selected.target,capabilities,budget)),selected.capabilityId,
            extent,topology.format,capabilities,budget,selected.draws.size,topology.resources,topology.passes,topology.dependencies,
            selected.sourceTable,if (selected.capabilityId == W5B_CAPABILITY_ID) DeferredLaneTopologyV4.GeometryBridge
                else DeferredLaneTopologyV4.Ordinary,null,emptyList(),emptyMap(),emptyMap())) {
            is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(result.value)
            is SourceConstructionResultV4.Refused -> result.failure
        }

    private fun <T: Any> constructChecked(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        clear: (W4cCandidate,SizeI32)->RenderPlanResult<T>,
        destination: (W4cCandidate,SizeI32,List<PathFillDraw>,List<PlanResource>,PlanDrawDataResources,
            Map<Int,PlanResourceId>,PathFillMemoryFootprint)->RenderPlanResult<T>,
        ordinary: (W4cCandidate,SizeI32,W5bDestinationGraphSealer.DestinationTopologyV4)->RenderPlanResult<T>): RenderPlanResult<T> {
        val selected = candidate as? W4cCandidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.hasMatchingFingerprints()) return invalidCandidate()

        val target = selected.target
        val extent = SizeI32(target.extent.width, target.extent.height)
        if (extent.width > capabilities.maxTextureDimension2D || extent.height > capabilities.maxTextureDimension2D) {
            return promoted(
                W4cPlanDiagnostics.CapabilityTextureDimension,
                "Target extent exceeds device texture limits",
            )
        }
        if (FORMAT !in capabilities.supportedFormats()) {
            return promoted(W4cPlanDiagnostics.CapabilityFormat, "W4c target format is unavailable")
        }
        if (!REQUIRED_OPERATIONS.all { it in capabilities.supportedOperations() }) {
            return promoted(W4cPlanDiagnostics.CapabilityOperation, "W4c required operation is unavailable")
        }
        if (PlanDepthStencilFormat.Depth24PlusStencil8 !in capabilities.supportedDepthStencilFormats()) {
            return promoted(
                W4cPlanDiagnostics.CapabilityDepthStencilFormat,
                "W4c depth-stencil format is unavailable",
            )
        }
        val usesStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover }
        if (capabilities.maxDynamicUniformBuffersPerPipelineLayout < 1) {
            return promoted(
                W4cPlanDiagnostics.CapabilityDynamicUniform,
                "W4c requires one dynamic uniform buffer",
            )
        }
        if (!validAllocationFacts(capabilities)) {
            return promoted(
                W4cPlanDiagnostics.CapabilityAllocationPolicy,
                "W4c allocation facts are not positive powers of two",
            )
        }
        if (selected.draws.isEmpty()) return try {
            clear(selected,extent)
        } catch (failure: IllegalArgumentException) {
            resourceLimit(W4cPlanDiagnostics.PlanIdentityInvalid, failure.message ?: "Invalid W5b clear-only path frame")
        }
        val footprint = when (
            val memory = PathFillPlanBudget.calculate(
                targetExtent = extent,
                geometriesF32 = selected.draws.map(SealedDraw::geometryF32),
                capabilities = capabilities,
                budget = budget,
            )
        ) {
            is PathFillPlanBudgetResult.WithinBudget -> memory.footprint
            is PathFillPlanBudgetResult.Exceeded -> {
                return resourceLimit(
                    W4cPlanDiagnostics.BudgetFrameLocalExceeded,
                    "Frame-local memory budget is exceeded",
                )
            }
            is PathFillPlanBudgetResult.Invalid -> {
                return resourceLimit(
                    W4cPlanDiagnostics.SizeOverflow,
                    "W4c physical footprint cannot be represented: ${memory.code}",
                )
            }
        }
        if (
            listOf(
                footprint.readbackBytes,
                footprint.vertexCapacityBytes,
                footprint.indexCapacityBytes,
                footprint.uniformCapacityBytes,
            ).any { it > capabilities.maxBufferSizeBytes }
        ) {
            return promoted(
                W4cPlanDiagnostics.CapabilityBufferSize,
                "W4c buffer capacity exceeds device limits",
            )
        }

        return try {
            val colorPassCount = checkedColorPassCount(selected.draws)
            val readbackIndex = colorPassCount
            val passCount = Math.addExact(readbackIndex, 1)
            val firstStencilPassIndex = selected.draws.indexOfFirst {
                it.strategy == PathFillStrategy.StencilCover
            }.let { drawIndex ->
                if (drawIndex < 0) -1 else checkedColorPassCount(selected.draws.take(drawIndex))
            }

            val logicalTarget = PlanResource.of(
                PlanResourceRole.LogicalTarget,
                0,
                PlanResourceKind.Texture2D,
                PlanTextureFormat.Color(FORMAT),
                extent,
                footprint.targetBytes,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val staging = PlanResource.of(
                PlanResourceRole.ReadbackStaging,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.readbackBytes,
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
                PlanResourceLifetime.FrameLocal,
                readbackIndex,
                passCount,
            )
            val vertex = PlanResource.of(
                PlanResourceRole.VertexData,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.vertexCapacityBytes,
                setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val index = PlanResource.of(
                PlanResourceRole.IndexData,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.indexCapacityBytes,
                setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val uniform = PlanResource.of(
                PlanResourceRole.UniformData,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.uniformCapacityBytes,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                passCount,
            )
            val depthStencil = if (usesStencil) {
                PlanResource.of(
                    PlanResourceRole.DepthStencil,
                    0,
                    PlanResourceKind.Texture2D,
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                    extent,
                    footprint.depthStencilBytes,
                    setOf(PlanResourceUsage.DepthStencilAttachment),
                    PlanResourceLifetime.FrameLocal,
                    firstStencilPassIndex,
                    passCount,
                )
            } else {
                null
            }
            val drawData = PlanDrawDataResources(vertex.id, index.id, uniform.id)
            if (selected.capabilityId == W5B_CAPABILITY_ID) {
                val draws = selected.draws.map { draw -> PathFillDraw.ofMaterial(draw.commandIndex, draw.material,
                    draw.geometryF32, draw.strategy, draw.scissorI32, draw.blend, draw.coordinates,
                    coordinatesV2 = draw.coordinatesV2,
                    coordinatesV4 = draw.coordinatesV4) }
                return destination(selected,extent,draws,listOfNotNull(vertex,index,uniform,depthStencil),drawData,
                    draws.mapNotNull { draw -> depthStencil?.id?.let { draw.commandIndex to it } }.toMap(),footprint)
            }
            val passes = mutableListOf<PlanPass>()
            var renderOrdinal = 0
            var producerOrdinal = 0
            var coverOrdinal = 0
            var firstColorAttachment = true
            selected.draws.forEach { sealed ->
                val draw = PathFillDraw.ofMaterial(
                    commandIndexI32 = sealed.commandIndex,
                    material = sealed.material,
                    geometryF32 = sealed.geometryF32,
                    strategy = sealed.strategy,
                    scissorI32 = sealed.scissorI32,
                    coordinates = sealed.coordinates,
                    coordinatesV2 = sealed.coordinatesV2,
                    coordinatesV4 = sealed.coordinatesV4,
                )
                val load = if (firstColorAttachment) {
                    AttachmentLoadPlan.ClearTransparent
                } else {
                    AttachmentLoadPlan.Load
                }
                when (sealed.strategy) {
                    PathFillStrategy.DirectTriangle -> {
                        passes += PlanPass.RenderPass(
                            renderOrdinal++,
                            logicalTarget.id,
                            listOf(draw),
                            load,
                            AttachmentStorePlan.Store,
                            drawData,
                        )
                        firstColorAttachment = false
                    }
                    PathFillStrategy.StencilCover -> {
                        val depth = requireNotNull(depthStencil)
                        val atomicGroup = PlanAtomicGroupId("w4c:${sealed.commandIndex}")
                        passes += PlanPass.StencilProducer(
                            producerOrdinal++,
                            logicalTarget.id,
                            depth.id,
                            draw,
                            drawData,
                            atomicGroup,
                            load,
                            AttachmentStorePlan.Store,
                            PlanDepthStencilAccess.Write,
                            PlanDepthStencilLoadStore.ClearZeroStore,
                        )
                        passes += PlanPass.StencilCover(
                            coverOrdinal++,
                            logicalTarget.id,
                            depth.id,
                            draw,
                            drawData,
                            atomicGroup,
                            AttachmentLoadPlan.Load,
                            AttachmentStorePlan.Store,
                            PlanDepthStencilAccess.ReadWrite,
                            PlanDepthStencilLoadStore.LoadStoreTestReset,
                        )
                        firstColorAttachment = false
                    }
                }
            }
            val readback = PlanPass.ReadbackPass(
                0,
                logicalTarget.id,
                staging.id,
                footprint.readbackBytesPerRow,
            )
            passes += readback
            val dependencies = passes.zipWithNext().map { (before, after) ->
                PlanPassDependency(before.id, after.id)
            }
            ordinary(selected,extent,W5bDestinationGraphSealer.DestinationTopologyV4(FORMAT,
                listOfNotNull(logicalTarget,staging,vertex,index,uniform,depthStencil),passes,dependencies,footprint.peakBytes))
        } catch (_: IllegalArgumentException) {
            resourceLimit(
                W4cPlanDiagnostics.PlanIdentityInvalid,
                "W4c graph invariants were not satisfied",
            )
        } catch (_: ArithmeticException) {
            resourceLimit(
                W4cPlanDiagnostics.SizeOverflow,
                "W4c graph arithmetic overflowed",
            )
        }
    }

    private fun checkedColorPassCount(draws: List<SealedDraw>): Int = draws.fold(0) { count, draw ->
        Math.addExact(count, if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2)
    }

    private fun notCandidate(message: String): GpuPlanSelection.NotCandidate =
        GpuPlanSelection.NotCandidate(
            listOf(diag(W4cPlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)),
        )

    private fun invalidSelection(message: String): GpuPlanSelection.InvalidScene =
        GpuPlanSelection.InvalidScene(
            listOf(diag(W4cPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message)),
        )

    private fun resourceSelection(message: String): GpuPlanSelection.ResourceLimitExceeded =
        GpuPlanSelection.ResourceLimitExceeded(
            listOf(diag(W4cPlanDiagnostics.PathResourceLimit, RenderDiagnosticDomain.RESOURCE, message)),
        )

    private fun promoted(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> =
        RenderPlanResult.GapOnPromotedScope(
            listOf(diag(code, RenderDiagnosticDomain.CAPABILITY, message)),
        )

    private fun resourceLimit(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> =
        RenderPlanResult.ResourceLimitExceeded(
            listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)),
        )

    private fun invalidCandidate(): RenderPlanResult<Nothing> = RenderPlanResult.InvalidScene(
        listOf(
            RenderDiagnostic(
                RenderDiagnosticCode("gpu-plan.selection.invalid-candidate"),
                RenderDiagnosticDomain.SCENE,
                RenderDiagnosticSeverity.ERROR,
                "W4c candidate does not belong to this compiler.",
            ),
        ),
    )

    private fun diag(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = W4cPlanDiagnostics.diagnostic(code, domain, message)

    private fun planIdentity(
        scene: CanonicalId,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): String {
        val fields = listOf(
            "w4c-plan-w5a-material-v2",
            scene.value,
            target.canonicalId.value,
            target.extent.width.toString(),
            target.extent.height.toString(),
            target.colorSpace.name,
            target.colorSpace.transferFunction.name,
            target.colorSpace.gamut.name,
            capabilities.deviceGeneration.toString(),
            capabilities.maxTextureDimension2D.toString(),
            capabilities.maxBufferSizeBytes.toString(),
            capabilities.copyBytesPerRowAlignment.toString(),
            capabilities.supportedFormats().map { it.name }.sorted().joinToString(","),
            capabilities.minUniformBufferOffsetAlignment.toString(),
            capabilities.maxDynamicUniformBuffersPerPipelineLayout.toString(),
            capabilities.supportedOperations().map { it.name }.sorted().joinToString(","),
            capabilities.bufferAllocationPolicy.vertexFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.indexFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.uniformFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.growth.name,
            capabilities.supportedDepthStencilFormats().map { it.name }.sorted().joinToString(","),
            budget.maxFrameLocalBytes.toString(),
        ) + planCapabilityIdentityFacts(capabilities)
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { value ->
            val bytes = value.encodeToByteArray()
            digest.update(bytes.size.toString().encodeToByteArray())
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private sealed interface Recognition {
        data class MaterialRefused(val refusals: List<EffectiveMaterialPlanner.Result.Refused>) : Recognition
        data class Accepted(val draws: List<SealedDraw>, val materialPlanTable: MaterialPlanTable?, val capabilityId: String,
            val sourceTable: MaterialSourceConstructionTableV4) : Recognition
        data class Gap(val message: String) : Recognition
        data class Invalid(val message: String) : Recognition
        data class ResourceLimit(val message: String) : Recognition
    }

    private sealed interface DrawRecognition {
        data class NoOp(val frameAttemptedEdgesAfterI32: Int) : DrawRecognition
        data class MaterialRefused(val refusal: EffectiveMaterialPlanner.Result.Refused, val frameAttemptedEdgesAfterI32: Int) : DrawRecognition
        data class Accepted(
            val draw: SealedDraw,
            val frameAttemptedEdgesAfterI32: Int,
        ) : DrawRecognition

        data class Gap(val message: String) : DrawRecognition
        data class Invalid(val message: String) : DrawRecognition
        data class ResourceLimit(val message: String) : DrawRecognition
    }

    private sealed interface ClipRecognition {
        data class Accepted(val bounds: RectI32?) : ClipRecognition
        data class Gap(val message: String) : ClipRecognition
        data class Invalid(val message: String) : ClipRecognition
    }

    private data class SealedDraw(
        val commandIndex: Int,
        val pathF32: PathF32,
        val transform: Matrix3x3F32,
        val material: MaterialPlanRef,
        val coordinates: MaterialCoordinatePlanV1?,
        val coordinatesV2: MaterialCoordinatePlanV2?,
        val coordinatesV4: SourceCoordinatesV4?,
        val geometryF32: PathFillGeometryF32,
        val strategy: PathFillStrategy,
        val scissorI32: RectI32,
        val blend: BlendPlan,
    )

    private class W4cCandidate(
        val owner: W4cPathFillPlanCompiler,
        override val sceneCanonicalId: CanonicalId,
        override val target: RenderTargetDescriptor,
        draws: List<SealedDraw>,
        val materialPlanTable: MaterialPlanTable?,
        override val capabilityId: String,
        val sourceTable: MaterialSourceConstructionTableV4,
    ) : GpuPlanCandidate {

        val draws: List<SealedDraw> = Collections.unmodifiableList(
            draws.map { draw ->
                draw.copy(
                    pathF32 = owner.snapshotPath(draw.pathF32),
                    transform = draw.transform.copy(),
                    scissorI32 = draw.scissorI32.copy(),
                )
            },
        )

        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId

        fun hasMatchingFingerprints(): Boolean =
            capabilityId in setOf(CAPABILITY_ID, W5B_CAPABILITY_ID) &&
                sceneCanonicalId == sceneFingerprint &&
                target.canonicalId == targetFingerprint
    }

    public companion object {
        public const val W5B_CAPABILITY_ID: String = "w5b-path-fill-final-blend-v3"
        /** Historical public graph contract; it carries only legacy per-draw colors. */
        public const val HISTORICAL_CAPABILITY_ID: String =
            "solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1"
        public const val CAPABILITY_ID: String =
            "w5a-solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v2"

        public fun isHistoricalCapabilityId(capabilityId: String): Boolean =
            capabilityId == HISTORICAL_CAPABILITY_ID

        public fun isW5aMaterialCapabilityId(capabilityId: String): Boolean =
            capabilityId == CAPABILITY_ID

        private val FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        private val REQUIRED_OPERATIONS = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        )
        private const val MAX_DRAWS: Int = 512
        private const val MAX_WINDING_STENCIL_EDGES: Int = 255
    }
}
