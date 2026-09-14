package org.graphiks.kanvas.gpu.plan

import java.security.MessageDigest
import java.util.Collections
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.PathEffectNode
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
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillWithStrokeWorkPreparationResult
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDashF64
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.prepareMappedPathFillGeometryWithStrokeWorkF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.pathStrokeDeviceFillSegmentMapperF64
import org.graphiks.math.matrix.preparePathStrokeGeometryF32

/**
 * Closed W4d.1 capability for bounded hard-edge sRGB path stroke frames.
 *
 * Policy injection is internal to the planner module so the public compiler
 * stays a closed capability with its default W4d bounds.
 */
public class W4dPathStrokePlanCompiler internal constructor(
    private val strokePolicyF64: PathStrokePolicyF64,
) : GpuPlanCompiler {
    public constructor() : this(PathStrokePolicyF64())
    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) return invalid("Scene and target differ")
        if (SceneSemanticValidator.validate(scene) is SceneSemanticValidationResult.Invalid) return invalid("Scene validation failed")
        if (scene.colorSpace != ColorSpace.SRGB) return gap("W4d supports only sRGB")
        when (val preflight = preflightStrokeFrame(scene)) {
            FramePreflight.Member -> Unit
            FramePreflight.Outside -> return gap("Scene is outside W4d")
            is FramePreflight.Invalid -> return invalid(preflight.message)
            is FramePreflight.Limit -> return limitSelection(preflight.message)
        }
        return when (val recognized = recognize(scene)) {
            is Recognition.MaterialRefused -> GpuPlanSelection.MaterialOnlyRefusal(CAPABILITY_ID, scene.canonicalId, target, recognized.refusals)
            is Recognition.Ready -> GpuPlanSelection.Candidate(Candidate(this, scene.canonicalId, target, recognized.draws, recognized.materialPlanTable, recognized.capabilityId,recognized.sources))
            is Recognition.Gap -> gap(recognized.message)
            is Recognition.Invalid -> invalid(recognized.message)
            is Recognition.Limit -> limitSelection(recognized.message)
        }
    }

    /**
     * Establishes lane ownership before geometry preparation so a frame-size
     * overflow is terminal only for structurally W4d path-stroke traffic.
     */
    private fun preflightStrokeFrame(scene: SceneSnapshot): FramePreflight {
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    val node = command.node
                    val path = (node.geometry as? GeometryNode.Path)?.path
                    val paint = node.paint
                    if (!finite(node.transform) || !finite(node.effects) || !finiteClip(node.clip) ||
                        (path != null && !finite(path)) || (paint != null && !finite(paint))
                    ) {
                        return FramePreflight.Invalid("Draw facts are non-finite")
                    }
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) {
                    return FramePreflight.Invalid("Transform metadata is non-finite")
                }
                is SceneCommand.SetClip -> if (!finiteClip(command.clip)) {
                    return FramePreflight.Invalid("Clip metadata is non-finite")
                }
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) {
                    return FramePreflight.Invalid("Annotation bounds are non-finite")
                }
                else -> Unit
            }
        }

        var visualDrawCountI32 = 0
        var sawStroke = false
        var outside = false
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    visualDrawCountI32 = Math.addExact(visualDrawCountI32, 1)
                    when (val scope = classifyDrawScope(command.node)) {
                        is DrawScope.Ready -> sawStroke = sawStroke || scope.stroke
                        is DrawScope.Gap -> outside = true
                        is DrawScope.Invalid -> return FramePreflight.Invalid(scope.message)
                    }
                }
                is SceneCommand.SetTransform,
                is SceneCommand.SetClip,
                is SceneCommand.Annotation,
                -> Unit
                else -> outside = true
            }
        }
        if (outside || !sawStroke) return FramePreflight.Outside
        return if (visualDrawCountI32 > MAX_DRAWS) {
            FramePreflight.Limit("W4d accepts at most 512 visual path draws")
        } else {
            FramePreflight.Member
        }
    }

    private fun recognize(scene: SceneSnapshot): Recognition {
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val draws = mutableListOf<SealedDraw>()
        val materialEntries = mutableListOf<MaterialPlanEntry>()
        val sources = mutableListOf<MaterialSourceConstructionV4>()
        val materialRefusals = mutableListOf<EffectiveMaterialPlanner.Result.Refused>()
        var visualDrawCount = 0
        var frameWork = PathStrokeWorkUsageI64()
        var sawStroke = false
        var elidedNoOpsI32 = 0
        scene.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Draw -> {
                    if (visualDrawCount >= MAX_DRAWS) return Recognition.Gap("W4d accepts at most 512 visual path draws")
                    visualDrawCount = Math.addExact(visualDrawCount, 1)
                    when (val draw = recognizeDraw(command.node, index, targetBounds, frameWork, materialEntries,sources)) {
                        is DrawResult.MaterialRefused -> { materialRefusals += draw.refusal; frameWork = draw.frameWork; sawStroke = sawStroke || draw.stroke }
                        is DrawResult.Ready -> { draws += draw.draw; frameWork = draw.frameWork; sawStroke = sawStroke || draw.stroke }
                        is DrawResult.NoOp -> { elidedNoOpsI32++; frameWork = draw.frameWork; sawStroke = sawStroke || draw.stroke }
                        is DrawResult.Empty -> { frameWork = draw.frameWork; sawStroke = sawStroke || draw.stroke }
                        is DrawResult.Gap -> return Recognition.Gap(draw.message)
                        is DrawResult.Invalid -> return Recognition.Invalid(draw.message)
                        is DrawResult.Limit -> return Recognition.Limit(draw.message)
                    }
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return Recognition.Invalid("Transform metadata is non-finite")
                is SceneCommand.SetClip -> if (!finiteClip(command.clip)) return Recognition.Invalid("Clip metadata is non-finite")
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return Recognition.Invalid("Annotation bounds are non-finite")
                else -> return Recognition.Gap("Scene command is outside W4d")
            }
        }
        if (!sawStroke) return Recognition.Gap("W4d requires at least one stroke draw")
        if (materialRefusals.isNotEmpty()) return Recognition.MaterialRefused(materialRefusals)
        if (draws.isEmpty() && elidedNoOpsI32 == 0) return Recognition.Limit("W4d retained no visible prepared geometry")
        val pending = sources.any { it.pending }
        val successor = pending || draws.any { it.blend != BlendPlan.SrcOver } || elidedNoOpsI32 > 0 || materialEntries.any { it.stopSlab != null }
        return Recognition.Ready(if (pending) draws.mapIndexed { ordinal,draw -> draw.copy(material=MaterialPlanRef(ordinal)) } else draws,
            materialEntries.takeIf { it.isNotEmpty() && !pending }?.let(MaterialPlanTable::of),
            if (successor) W5B_CAPABILITY_ID else CAPABILITY_ID,
            (MaterialSourceConstructionTableV4.of(sources) as SourceConstructionResultV4.Built).value)
    }

    private fun recognizeDraw(node: DrawNode, commandIndex: Int, targetBounds: RectI32, frameWork: PathStrokeWorkUsageI64,
        materialEntries: MutableList<MaterialPlanEntry>,sources: MutableList<MaterialSourceConstructionV4>): DrawResult {
        val scope = when (val classified = classifyDrawScope(node)) {
            is DrawScope.Ready -> classified
            is DrawScope.Gap -> return DrawResult.Gap(classified.message)
            is DrawScope.Invalid -> return DrawResult.Invalid(classified.message)
        }
        val result = if (scope.fill) {
            prepareFill(scope.path, node.transform, frameWork)
        } else {
            prepareStroke(
                scope.path,
                node.transform,
                requireNotNull(scope.mode),
                requireNotNull(scope.styleF64),
                frameWork,
            )
        }
        return when (result) {
            is Prepared.Ready -> {
                val targetScissor = intersect(result.geometry.copyConservativeScissorI32(), targetBounds)
                    ?: return DrawResult.Empty(result.work, scope.stroke)
                val scissor = if (scope.clip == null) targetScissor else intersect(targetScissor, scope.clip)
                    ?: return DrawResult.Empty(result.work, scope.stroke)
                if (scissor.isEmpty) DrawResult.Empty(result.work, scope.stroke) else if (
                    result.geometry.fillRule == FillRule.WINDING &&
                    result.geometry.copyStencilEdgeFanF32OrNull() != null &&
                    result.geometry.emittedNonZeroClosedEdgeCountI32 > UByte.MAX_VALUE.toInt()
                ) DrawResult.Limit("Winding path exceeds the W4d stencil edge limit") else {
                    // Path effects are geometry-only facts for this lane; the material plan
                    // remains the captured paint/material authority.
                    val normalized = when (val planned = EffectiveMaterialPlanner.normalizeSourcesV4(
                        node.copy(effects = EffectStack.Empty), FORMAT.blendTargetClampV1(),scissor)) {
                        is EffectiveMaterialPlanner.SourceNormalizationV4.Refused -> return DrawResult.MaterialRefused(EffectiveMaterialPlanner.Result.Refused(planned.diagnosticCode), result.work, scope.stroke)
                        EffectiveMaterialPlanner.SourceNormalizationV4.NoOp -> return DrawResult.NoOp(result.work, scope.stroke)
                        is EffectiveMaterialPlanner.SourceNormalizationV4.Source -> planned.captured
                    }
                    sources += normalized
                    val resolved = normalized.resolvedSource
                    val material = if (resolved == null) MaterialPlanRef(sources.lastIndex)
                        else appendMaterialPlan(materialEntries,resolved.table,resolved.root)
                    val blend = normalized.blend.takeUnless {
                        it is BlendPlan.FixedFunctionV1 && it.mode == BlendMode.SRC_OVER
                    } ?: BlendPlan.SrcOver
                    DrawResult.Ready(
                    SealedDraw(commandIndex, material, result.geometry, result.strokeGeometry, result.mode, result.styleF64, scissor,
                        blend, MaterialCoordinatePlanV1.fromCtm(node.transform),resolved?.table?.coordinatesV2(resolved.root),
                        if (normalized.pending) normalized.coordinates else resolved?.table?.coordinatesV4(resolved.root)),
                    result.work,
                    scope.stroke,
                ) }
            }
            is Prepared.Empty -> DrawResult.Empty(result.work, scope.stroke)
            is Prepared.Invalid -> DrawResult.Invalid(result.message)
            is Prepared.Limit -> DrawResult.Limit(result.message)
        }
    }

    private fun classifyDrawScope(node: DrawNode): DrawScope {
        val path = (node.geometry as? GeometryNode.Path)?.path
            ?: return DrawScope.Gap("Draw geometry is outside W4d")
        val paint = node.paint ?: return DrawScope.Gap("W4d requires paint")
        if (!finite(path) || !finite(node.transform) || !finite(paint) || !finite(node.effects) || !finiteClip(node.clip)) {
            return DrawScope.Invalid("Draw facts are non-finite")
        }
        val fill = paint.style == PaintStyleNode.FILL
        val stroke = paint.style == PaintStyleNode.STROKE || paint.style == PaintStyleNode.STROKE_AND_FILL
        val mode = if (stroke) {
            if (paint.style == PaintStyleNode.STROKE_AND_FILL) {
                PathStrokeDrawMode.StrokeAndFill
            } else {
                PathStrokeDrawMode.Stroke
            }
        } else {
            null
        }
        val styleF64 = if (mode != null) {
            try {
                style(paint, mode)
            } catch (_: IllegalArgumentException) {
                return DrawScope.Invalid("Stroke style is invalid")
            }
        } else {
            null
        }
        if (node.origin != DrawOrigin.PATH || path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD)) {
            return DrawScope.Gap("Path provenance or fill rule is outside W4d")
        }
        if (node.coverage != CoverageRequest.HARD_EDGE || !(node.transform.isIdentity || node.transform.isScaleTranslate())) {
            return DrawScope.Gap("Coverage or transform is outside W4d")
        }
        val clip = when (val value = node.clip) {
            ClipStackNode.Empty -> null
            is ClipStackNode.DeviceRect -> {
                if (value.antiAlias) return DrawScope.Gap("Clip is outside W4d")
                integral(value.copyBounds()) ?: return DrawScope.Gap("Clip is outside W4d")
            }
            else -> return DrawScope.Gap("Clip is outside W4d")
        }
        if (!solid(node, paint)) return DrawScope.Gap("Material, blend, or effect is outside W4d")
        if (fill && paint.pathEffect != null) return DrawScope.Gap("Path effects require a stroke in W4d")
        if (!stroke) return DrawScope.Ready(path, clip, fill = true, stroke = false, mode = null, styleF64 = null)
        return DrawScope.Ready(path, clip, fill = false, stroke = true, requireNotNull(mode), requireNotNull(styleF64))
    }

    private fun prepareFill(path: org.graphiks.math.geometry.PathF32, matrix: Matrix3x3F32, frame: PathStrokeWorkUsageI64): Prepared {
        val mapper = try { matrix.pathStrokeDeviceFillSegmentMapperF64() } catch (_: IllegalArgumentException) { return Prepared.Invalid("Path transform is non-finite") }
        return when (val prepared = prepareMappedPathFillGeometryWithStrokeWorkF32(PathFillInputF64.fromPathF32(path), mapper, strokePolicyF64 = strokePolicyF64, frameWorkUsageBeforeI64 = frame)) {
            is PathFillWithStrokeWorkPreparationResult.Ready -> Prepared.Ready(prepared.geometryF32, null, null, null, prepared.frameWorkUsageAfterI64)
            is PathFillWithStrokeWorkPreparationResult.Empty -> Prepared.Empty(prepared.frameWorkUsageAfterI64)
            is PathFillWithStrokeWorkPreparationResult.InvalidScene -> Prepared.Invalid("Math rejected fill scene: ${prepared.reason}")
            is PathFillWithStrokeWorkPreparationResult.ResourceLimitExceeded -> Prepared.Limit("Math fill limit: ${prepared.reason}")
        }
    }

    private fun prepareStroke(path: org.graphiks.math.geometry.PathF32, matrix: Matrix3x3F32, mode: PathStrokeDrawMode, styleF64: PathStrokeStyleF64, frame: PathStrokeWorkUsageI64): Prepared {
        return when (val prepared = matrix.preparePathStrokeGeometryF32(path, styleF64, mode, policyF64 = strokePolicyF64, frameWorkUsageBeforeI64 = frame)) {
            is PathStrokePreparationResult.Ready -> Prepared.Ready(prepared.geometryF32.copyFillGeometryF32(), prepared.geometryF32, mode, styleF64, prepared.frameWorkUsageAfterI64)
            is PathStrokePreparationResult.Empty -> Prepared.Empty(prepared.frameWorkUsageAfterI64)
            is PathStrokePreparationResult.InvalidScene -> Prepared.Invalid("Math rejected stroke scene: ${prepared.reason}")
            is PathStrokePreparationResult.ResourceLimitExceeded -> Prepared.Limit("Math stroke limit: ${prepared.reason}")
        }
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> =
        if (hasPendingSources(candidate)) constructSources(candidate,capabilities,budget).prepareAndPublishSourcesV4()
        else construct(candidate,capabilities,budget).publishConstructionResult()

    internal fun hasPendingSources(candidate: GpuPlanCandidate): Boolean =
        (candidate as? Candidate)?.sources?.sources()?.any { it.pending } == true

    internal fun construct(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraphConstruction> =
        constructChecked(candidate,capabilities,budget,
            clear = { selected,extent -> RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(W5bGeometryLanePlanV3.constructClearOnly(
                PlanId(identity(selected,capabilities,budget)),W5B_CAPABILITY_ID,extent,capabilities,budget,null))) }) { selected,memory,stencil ->
            require(!hasPendingSources(selected)) { W5fPlanDiagnostics.Schema }
            val extent = SizeI32(selected.target.extent.width,selected.target.extent.height)
            graph(selected,capabilities,budget,memory,stencil,
                destination = { draws,resources,data,depth -> RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(
                    W5bDestinationGraphSealer.construct(PlanId(identity(selected,capabilities,budget)),W5B_CAPABILITY_ID,
                        extent,capabilities,budget,draws,selected.materialPlanTable,memory.targetBytes,memory.readbackBytes,
                        memory.readbackBytesPerRow,resources,data,depthStencilByCommandI32=depth))) }) { topology ->
                RenderPlanResult.Ready(RenderGraph.issueW4dCompilerWitness(RenderGraph.construct(
                    PlanId(identity(selected,capabilities,budget)),CAPABILITY_ID,extent,FORMAT,capabilities,budget,
                    selected.draws.size,topology.resources,topology.passes,topology.dependencies,topology.peakI64,selected.materialPlanTable)))
            }
        }

    internal fun constructSources(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> = constructChecked(candidate,capabilities,budget,
        clear = { selected,extent -> SourceDeferredRenderConstructionV4.clearOnly(
            PlanId(identity(selected,capabilities,budget)),W5B_CAPABILITY_ID,extent,capabilities,budget) }) { selected,memory,stencil ->
            val extent = SizeI32(selected.target.extent.width,selected.target.extent.height)
            graph(selected,capabilities,budget,memory,stencil,
                destination = { draws,resources,data,depth ->
                    val symbolic = draws.mapIndexed { ordinal,draw -> when (draw) {
                        is PathFillDraw -> draw.withMaterialRef(MaterialPlanRef(ordinal))
                        is PathStrokeDraw -> draw.withMaterialRef(MaterialPlanRef(ordinal))
                        else -> error(W5fPlanDiagnostics.Schema)
                    } }
                    deferred(selected,capabilities,budget,W5bDestinationGraphSealer.describeSources(W5B_CAPABILITY_ID,
                        extent,capabilities,budget,symbolic,memory.targetBytes,memory.readbackBytes,memory.readbackBytesPerRow,
                        resources,data,depthStencilByCommandI32=depth))
                }) { topology ->
                val refs = selected.draws.map { it.material }
                deferred(selected,capabilities,budget,W5bDestinationGraphSealer.DestinationTopologyV4(topology.format,
                    topology.resources,remapSourcePassesV4(topology.passes) { ref ->
                        MaterialPlanRef(refs.indexOf(ref).also { require(it >= 0) }) },topology.dependencies,topology.peakI64))
            }
        }

    private fun deferred(selected: Candidate,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        topology: W5bDestinationGraphSealer.DestinationTopologyV4): RenderPlanResult<SourceDeferredRenderConstructionV4> =
        when (val result = SourceDeferredRenderConstructionV4.of(PlanId(identity(selected,capabilities,budget)),selected.capabilityId,
            SizeI32(selected.target.extent.width,selected.target.extent.height),topology.format,capabilities,budget,selected.draws.size,
            topology.resources,topology.passes,topology.dependencies,selected.sources,
            if (selected.capabilityId == W5B_CAPABILITY_ID) DeferredLaneTopologyV4.GeometryBridge else DeferredLaneTopologyV4.Ordinary,
            null,emptyList(),emptyMap(),emptyMap())) {
            is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(result.value)
            is SourceConstructionResultV4.Refused -> result.failure
        }

    private fun <T: Any> constructChecked(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget,
        clear: (Candidate,SizeI32)->RenderPlanResult<T>,
        finish: (Candidate,PathFillMemoryFootprint,Boolean)->RenderPlanResult<T>): RenderPlanResult<T> {
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this) return invalidCandidate()
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        if (extent.width > capabilities.maxTextureDimension2D || extent.height > capabilities.maxTextureDimension2D || FORMAT !in capabilities.supportedFormats() || !REQUIRED.all { it in capabilities.supportedOperations() } || capabilities.maxDynamicUniformBuffersPerPipelineLayout < 1 || !validAllocationFacts(capabilities)) return promoted("Required W4d device capability is unavailable")
        if (selected.draws.isEmpty()) return try {
            clear(selected,extent)
        } catch (_: IllegalArgumentException) { resource("W4d empty frame exceeds its resource contract") }
        val usesStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover }
        if (usesStencil && (PlanDepthStencilFormat.Depth24PlusStencil8 !in capabilities.supportedDepthStencilFormats() || PlanOperationCapability.DepthStencilAttachment !in capabilities.supportedOperations() || PlanOperationCapability.StencilCover !in capabilities.supportedOperations())) return promoted("W4d stencil capability is unavailable")
        val memory = when (val value = PathStrokePlanBudget.calculate(extent, selected.draws.map { it.geometry }, capabilities, budget)) {
            is PathStrokePlanBudgetResult.WithinBudget -> value.footprint
            is PathStrokePlanBudgetResult.Exceeded -> return resource(W4dPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
            is PathStrokePlanBudgetResult.Invalid -> return resource("W4d size is unrepresentable: ${value.code}")
        }
        if (listOf(memory.readbackBytes, memory.vertexCapacityBytes, memory.indexCapacityBytes, memory.uniformCapacityBytes).any { it > capabilities.maxBufferSizeBytes }) return promoted("W4d buffer capability is unavailable")
        return try { finish(selected,memory,usesStencil) } catch (_: ArithmeticException) { resource("W4d arithmetic overflowed") } catch (_: IllegalArgumentException) { resource(W4dPlanDiagnostics.PlanIdentityInvalid, "W4d graph invariants failed") }
    }

    private fun <T: Any> graph(selected: Candidate, caps: PlanCapabilitySnapshot, budget: PlanBudget, memory: PathFillMemoryFootprint,
        usesStencil: Boolean,destination: (List<PathDraw>,List<PlanResource>,PlanDrawDataResources,Map<Int,PlanResourceId>)->RenderPlanResult<T>,
        ordinary: (W5bDestinationGraphSealer.DestinationTopologyV4)->RenderPlanResult<T>): RenderPlanResult<T> {
        val colorPasses = selected.draws.sumOf { if (it.strategy == PathFillStrategy.DirectTriangle) 1 else 2 }
        val passCount = Math.addExact(colorPasses, 1)
        val readbackIndex = colorPasses
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        fun resource(role: PlanResourceRole, kind: PlanResourceKind, format: PlanTextureFormat?, size: SizeI32?, bytes: Long, usages: Set<PlanResourceUsage>, first: Int) = PlanResource.of(role, 0, kind, format, size, bytes, usages, PlanResourceLifetime.FrameLocal, first, passCount)
        val target = resource(PlanResourceRole.LogicalTarget, PlanResourceKind.Texture2D, PlanTextureFormat.Color(FORMAT), extent, memory.targetBytes, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), 0)
        val staging = resource(PlanResourceRole.ReadbackStaging, PlanResourceKind.Buffer, null, null, memory.readbackBytes, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), readbackIndex)
        val vertex = resource(PlanResourceRole.VertexData, PlanResourceKind.Buffer, null, null, memory.vertexCapacityBytes, setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination), 0)
        val index = resource(PlanResourceRole.IndexData, PlanResourceKind.Buffer, null, null, memory.indexCapacityBytes, setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination), 0)
        val uniform = resource(PlanResourceRole.UniformData, PlanResourceKind.Buffer, null, null, memory.uniformCapacityBytes, setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), 0)
        val firstStencil = selected.draws.indexOfFirst { it.strategy == PathFillStrategy.StencilCover }.let { if (it < 0) 0 else selected.draws.take(it).sumOf { draw -> if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2 } }
        val depth = if (usesStencil) resource(PlanResourceRole.DepthStencil, PlanResourceKind.Texture2D, PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), extent, memory.depthStencilBytes, setOf(PlanResourceUsage.DepthStencilAttachment), firstStencil) else null
        val data = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        if (selected.capabilityId == W5B_CAPABILITY_ID) {
            val colors = selected.draws.map { it.draw() }
            return destination(colors,listOfNotNull(vertex,index,uniform,depth),data,
                colors.filter { it.strategy == PathFillStrategy.StencilCover }.associate { it.commandIndex to requireNotNull(depth).id })
        }
        val passes = mutableListOf<PlanPass>(); var render = 0; var producer = 0; var cover = 0; var clear = true
        selected.draws.forEach { sealed ->
            val draw = sealed.draw(BlendPlan.SrcOver)
            val load = if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load
            if (draw.strategy == PathFillStrategy.DirectTriangle) passes += PlanPass.RenderPass(render++, target.id, listOf(draw), load, AttachmentStorePlan.Store, data)
            else { val group = canonicalPathAtomicGroup(draw); val stencil = requireNotNull(depth); passes += PlanPass.StencilProducer(producer++, target.id, stencil.id, draw, data, group, load, AttachmentStorePlan.Store, PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore); passes += PlanPass.StencilCover(cover++, target.id, stencil.id, draw, data, group, AttachmentLoadPlan.Load, AttachmentStorePlan.Store, PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset) }
            clear = false
        }
        passes += PlanPass.ReadbackPass(0, target.id, staging.id, memory.readbackBytesPerRow)
        return ordinary(W5bDestinationGraphSealer.DestinationTopologyV4(FORMAT,listOfNotNull(target,staging,vertex,index,uniform,depth),
            passes,passes.zipWithNext().map { PlanPassDependency(it.first.id,it.second.id) },memory.peakBytes))
    }

    private fun solid(node: DrawNode, paint: PaintNode): Boolean = effectsMatchPaintPathEffect(node.effects, paint.pathEffect) && node.resource == null && node.operationBlendMode == null && w4Blend(node.blend) && paint.blender == null && paint.colorFilter == null && paint.maskFilter == null && paint.imageFilter == null && (paint.pathEffect == null || paint.pathEffect is PathEffectNode.Dash) && materialMatchesPaintAuthority(node)
    private fun effectsMatchPaintPathEffect(effects: EffectStack, pathEffect: PathEffectNode?): Boolean = when (effects) {
        EffectStack.Empty -> true
        is EffectStack.Entries -> {
            if (effects.effectCount != 1) {
                false
            } else {
                val sealedDash = pathEffect as? PathEffectNode.Dash
                val duplicatedDash = effects.effectAt(0) as? PathEffectNode.Dash
                sealedDash != null && duplicatedDash != null && sameDashBits(sealedDash, duplicatedDash)
            }
        }
    }
    private fun sameDashBits(first: PathEffectNode.Dash, second: PathEffectNode.Dash): Boolean {
        if (first.phase.toRawBits() != second.phase.toRawBits()) return false
        val firstIntervals = first.intervals.copyToFloatArray()
        val secondIntervals = second.intervals.copyToFloatArray()
        return firstIntervals.size == secondIntervals.size && firstIntervals.indices.all { index ->
            firstIntervals[index].toRawBits() == secondIntervals[index].toRawBits()
        }
    }
    private fun style(paint: PaintNode, mode: PathStrokeDrawMode): PathStrokeStyleF64 = PathStrokeStyleF64(if (paint.strokeWidth == 0f && mode == PathStrokeDrawMode.Stroke) PathStrokeWidthF64.Hairline else PathStrokeWidthF64.Finite(paint.strokeWidth.toDouble()), when (paint.strokeCap) { StrokeCapNode.BUTT -> PathStrokeCap.Butt; StrokeCapNode.ROUND -> PathStrokeCap.Round; StrokeCapNode.SQUARE -> PathStrokeCap.Square }, when (paint.strokeJoin) { StrokeJoinNode.MITER -> PathStrokeJoin.Miter; StrokeJoinNode.ROUND -> PathStrokeJoin.Round; StrokeJoinNode.BEVEL -> PathStrokeJoin.Bevel }, paint.strokeMiter.toDouble(), (paint.pathEffect as? PathEffectNode.Dash)?.let { PathStrokeDashF64.of(it.intervals.copyToFloatArray().map(Float::toDouble).toDoubleArray(), it.phase.toDouble()) })
    private fun integral(bounds: RectF32): RectI32? = if (!finite(bounds)) null else listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).map { value -> value.toLong().takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && it.toFloat() == value } }.takeIf { it.none { value -> value == null } }?.let { RectI32(it[0]!!.toInt(), it[1]!!.toInt(), it[2]!!.toInt(), it[3]!!.toInt()).takeUnless(RectI32::isEmpty64) }
    private fun intersect(a: RectI32, b: RectI32): RectI32? = a.copy().takeIf { it.intersect(b) }
    private fun finiteClip(clip: ClipStackNode): Boolean = when (clip) { ClipStackNode.Empty -> true; is ClipStackNode.DeviceRect -> finite(clip.copyBounds()); is ClipStackNode.Operations -> clip.all { entry -> when (val geometry = entry.geometry) { is GeometryNode.Rect -> finite(geometry.copyBounds()); is GeometryNode.RRect -> finite(geometry.copyShape()); is GeometryNode.Path -> finite(geometry.path); else -> false } } }
    private fun finite(path: org.graphiks.math.geometry.PathF32): Boolean = path.all { segment -> when (segment) { is org.graphiks.math.geometry.PathSegmentF32.MoveTo -> finite(segment.point); is org.graphiks.math.geometry.PathSegmentF32.LineTo -> finite(segment.point); is org.graphiks.math.geometry.PathSegmentF32.QuadTo -> finite(segment.control) && finite(segment.point); is org.graphiks.math.geometry.PathSegmentF32.CubicTo -> finite(segment.control1) && finite(segment.control2) && finite(segment.point); is org.graphiks.math.geometry.PathSegmentF32.ArcTo -> finite(segment.point) && segment.radius.x.isFinite() && segment.radius.y.isFinite() && segment.xAxisRotation.isFinite(); org.graphiks.math.geometry.PathSegmentF32.Close -> true } }
    private fun finite(point: org.graphiks.math.geometry.Point2F32): Boolean = point.x.isFinite() && point.y.isFinite()
    private fun finite(bounds: RectF32): Boolean = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)
    private fun finite(matrix: Matrix3x3F32): Boolean = listOf(matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty, matrix.persp0, matrix.persp1, matrix.persp2).all(Float::isFinite)
    private fun finite(paint: PaintNode): Boolean = paint.strokeWidth.isFinite() && paint.strokeMiter.isFinite() && finite(paint.pathEffect)
    private fun finite(effects: EffectStack): Boolean = when (effects) {
        EffectStack.Empty -> true
        is EffectStack.Entries -> effects.all { effect -> (effect as? PathEffectNode)?.let(::finite) ?: true }
    }
    private fun finite(effect: PathEffectNode?): Boolean = when (effect) {
        null -> true
        is PathEffectNode.Dash -> effect.phase.isFinite() && effect.intervals.copyToFloatArray().all(Float::isFinite)
        is PathEffectNode.Corner -> effect.radius.isFinite()
        is PathEffectNode.Discrete -> effect.segmentLength.isFinite() && effect.deviation.isFinite()
        is PathEffectNode.Path1D -> finite(effect.path) && effect.advance.isFinite() && effect.phase.isFinite()
        is PathEffectNode.Path2D -> finite(effect.path) && finite(effect.matrix)
        is PathEffectNode.Trim -> effect.start.isFinite() && effect.stop.isFinite()
    }
    private fun finite(shape: org.graphiks.math.geometry.RRectF32): Boolean = finite(shape.rect) && listOf(shape.topLeft.x, shape.topLeft.y, shape.topRight.x, shape.topRight.y, shape.bottomRight.x, shape.bottomRight.y, shape.bottomLeft.x, shape.bottomLeft.y).all(Float::isFinite)
    private fun w4Blend(blend: BlendNode): Boolean = when (blend) { BlendNode.SrcOver -> true; is BlendNode.Mode -> true; is BlendNode.Paint -> blend.blender == null; is BlendNode.Custom -> false }
    private fun materialMatchesPaintAuthority(node: DrawNode): Boolean { val paint = node.paint ?: return false; val paintMaterial = paint.shader ?: MaterialNode.Solid(paint.color); return node.material.canonicalId == paintMaterial.canonicalId }
    private fun appendMaterialPlan(entries: MutableList<MaterialPlanEntry>, incoming: MaterialPlanTable, root: MaterialPlanRef): MaterialPlanRef { val offset = entries.size; incoming.entries().forEach { entry -> entries += entry }; return MaterialPlanRef(offset + root.indexI32) }
    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(), capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes, capabilities.bufferAllocationPolicy.indexFloorBytes,
        capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { value -> value > 0L && value and (value - 1L) == 0L }
    private fun identity(selected: Candidate, caps: PlanCapabilitySnapshot, budget: PlanBudget): String {
        val fields = listOf(
            "w4d-plan-w5a-material-v2", selected.sceneCanonicalId.value, selected.target.canonicalId.value,
            caps.deviceGeneration.toString(), caps.maxTextureDimension2D.toString(), caps.maxBufferSizeBytes.toString(),
            caps.copyBytesPerRowAlignment.toString(), caps.supportedFormats().map { it.name }.sorted().joinToString(","),
            caps.minUniformBufferOffsetAlignment.toString(), caps.maxDynamicUniformBuffersPerPipelineLayout.toString(),
            caps.supportedOperations().map { it.name }.sorted().joinToString(","),
            caps.bufferAllocationPolicy.vertexFloorBytes.toString(), caps.bufferAllocationPolicy.indexFloorBytes.toString(),
            caps.bufferAllocationPolicy.uniformFloorBytes.toString(), caps.bufferAllocationPolicy.growth.name,
            caps.supportedDepthStencilFormats().map { it.name }.sorted().joinToString(","), budget.maxFrameLocalBytes.toString(),
            strokePolicyF64.maximumSagittaErrorF64.toRawBits().toString(),
            strokePolicyF64.maximumDashArcLengthErrorF64.toRawBits().toString(),
            strokePolicyF64.limitsI32.maxSubdivisionDepthI32.toString(),
            strokePolicyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32.toString(),
            strokePolicyF64.limitsI32.maxAttemptedGeometryUnitsPerFrameI32.toString(),
            strokePolicyF64.limitsI32.maxEmittedVertexCountPerPathI32.toString(),
            strokePolicyF64.limitsI32.maxEmittedVertexCountPerFrameI32.toString(),
            strokePolicyF64.limitsI32.maxEmittedIndexCountPerPathI32.toString(),
            strokePolicyF64.limitsI32.maxEmittedIndexCountPerFrameI32.toString(),
            strokePolicyF64.limitsI64.maxSnapshotByteCountPerPathI64.toString(),
            strokePolicyF64.limitsI64.maxSnapshotByteCountPerFrameI64.toString(),
        ) + planCapabilityIdentityFacts(caps)
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
    private fun gap(message: String) = GpuPlanSelection.NotCandidate(listOf(diag(W4dPlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)))
    private fun invalid(message: String) = GpuPlanSelection.InvalidScene(listOf(diag(W4dPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message)))
    private fun limitSelection(message: String) = GpuPlanSelection.ResourceLimitExceeded(listOf(diag(W4dPlanDiagnostics.PathResourceLimit, RenderDiagnosticDomain.RESOURCE, message)))
    private fun promoted(message: String): RenderPlanResult<Nothing> = RenderPlanResult.GapOnPromotedScope(listOf(diag(W4dPlanDiagnostics.CapabilityUnavailable, RenderDiagnosticDomain.CAPABILITY, message)))
    private fun resource(message: String): RenderPlanResult<Nothing> = resource(W4dPlanDiagnostics.SizeOverflow, message)
    private fun resource(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> = RenderPlanResult.ResourceLimitExceeded(listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)))
    private fun invalidCandidate(): RenderPlanResult<Nothing> = RenderPlanResult.InvalidScene(listOf(RenderDiagnostic(RenderDiagnosticCode("gpu-plan.selection.invalid-candidate"), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, "W4d candidate does not belong to this compiler.")))
    private fun diag(code: RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic = W4dPlanDiagnostics.diagnostic(code, domain, message)

    private sealed interface Recognition { data class MaterialRefused(val refusals: List<EffectiveMaterialPlanner.Result.Refused>) : Recognition; data class Ready(val draws: List<SealedDraw>, val materialPlanTable: MaterialPlanTable?, val capabilityId: String,val sources: MaterialSourceConstructionTableV4) : Recognition; data class Gap(val message: String) : Recognition; data class Invalid(val message: String) : Recognition; data class Limit(val message: String) : Recognition }
    private sealed interface FramePreflight { data object Member : FramePreflight; data object Outside : FramePreflight; data class Invalid(val message: String) : FramePreflight; data class Limit(val message: String) : FramePreflight }
    private sealed interface DrawScope {
        data class Ready(
            val path: org.graphiks.math.geometry.PathF32,
            val clip: RectI32?,
            val fill: Boolean,
            val stroke: Boolean,
            val mode: PathStrokeDrawMode?,
            val styleF64: PathStrokeStyleF64?,
        ) : DrawScope
        data class Gap(val message: String) : DrawScope
        data class Invalid(val message: String) : DrawScope
    }
    private sealed interface DrawResult { data class NoOp(val frameWork: PathStrokeWorkUsageI64, val stroke: Boolean) : DrawResult; data class MaterialRefused(val refusal: EffectiveMaterialPlanner.Result.Refused, val frameWork: PathStrokeWorkUsageI64, val stroke: Boolean) : DrawResult; data class Ready(val draw: SealedDraw, val frameWork: PathStrokeWorkUsageI64, val stroke: Boolean) : DrawResult; data class Empty(val frameWork: PathStrokeWorkUsageI64, val stroke: Boolean) : DrawResult; data class Gap(val message: String) : DrawResult; data class Invalid(val message: String) : DrawResult; data class Limit(val message: String) : DrawResult }
    private sealed interface Prepared { data class Ready(val geometry: org.graphiks.math.geometry.PathFillGeometryF32, val strokeGeometry: org.graphiks.math.geometry.PathStrokeGeometryF32?, val mode: PathStrokeDrawMode?, val styleF64: PathStrokeStyleF64?, val work: PathStrokeWorkUsageI64) : Prepared; data class Empty(val work: PathStrokeWorkUsageI64) : Prepared; data class Invalid(val message: String) : Prepared; data class Limit(val message: String) : Prepared }
    private data class SealedDraw(val commandIndex: Int, val material: MaterialPlanRef, val geometry: org.graphiks.math.geometry.PathFillGeometryF32,
        val stroke: org.graphiks.math.geometry.PathStrokeGeometryF32?, val mode: PathStrokeDrawMode?, val styleF64: PathStrokeStyleF64?,
        val scissor: RectI32, val blend: BlendPlan, val coordinates: MaterialCoordinatePlanV1?,
        val coordinatesV2: MaterialCoordinatePlanV2?,val coordinatesV4: SourceCoordinatesV4?) {
        val strategy: PathFillStrategy = if (geometry.copyDirectTriangleF32OrNull() != null) PathFillStrategy.DirectTriangle else PathFillStrategy.StencilCover
        fun draw(blend: BlendPlan = this.blend): PathDraw = stroke?.let { geometry ->
            coordinatesV4?.let { PathStrokeDraw.ofMaterialV4(commandIndex,material,geometry,scissor,
                requireNotNull(mode),requireNotNull(styleF64),blend,it) }
                ?: PathStrokeDraw.ofMaterial(commandIndex,material,geometry,scissor,requireNotNull(mode),
                    requireNotNull(styleF64),blend,coordinates,coordinatesV2)
        } ?: PathFillDraw.ofMaterial(commandIndex,material,geometry,strategy,scissor,blend,coordinates,coordinatesV2,coordinatesV4)
    }
    private class Candidate(val owner: W4dPathStrokePlanCompiler, override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId,
        override val target: RenderTargetDescriptor, draws: List<SealedDraw>, val materialPlanTable: MaterialPlanTable?,
        override val capabilityId: String,val sources: MaterialSourceConstructionTableV4) : GpuPlanCandidate {
        val draws = Collections.unmodifiableList(draws.toList())
    }

    public companion object {
        /** Historical public graph contract; it carries only legacy per-draw colors. */
        public const val HISTORICAL_CAPABILITY_ID: String = "solid-path-stroke-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1"
        public const val W5B_CAPABILITY_ID: String = "w5b-path-stroke-final-blend-v3"
        public const val CAPABILITY_ID: String = "w5a-solid-path-stroke-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v2"
        public fun isHistoricalCapabilityId(capabilityId: String): Boolean = capabilityId == HISTORICAL_CAPABILITY_ID
        public fun isW5aMaterialCapabilityId(capabilityId: String): Boolean = capabilityId == CAPABILITY_ID
        private val FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        private val REQUIRED = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload, PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback)
        private const val MAX_DRAWS = 512
    }
}
