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
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
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
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDashF64
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeInvalidSceneReason
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.PathProjectiveInvalidSceneReason
import org.graphiks.math.matrix.PathTransformClass
import org.graphiks.math.matrix.PathTransformedFillInvalidSceneReason
import org.graphiks.math.matrix.PathTransformedFillPreparationResult
import org.graphiks.math.matrix.classifyPathTransform
import org.graphiks.math.matrix.preparePathFillGeometryF32
import org.graphiks.math.matrix.preparePathStrokeGeometryF32
import org.graphiks.math.matrix.toMatrix3x3F64

/**
 * W4d.2 planning authority for bounded transformed and four-sample path frames.
 *
 * This compiler intentionally declines the historical hard identity and
 * axis-aligned cases, leaving their stable W4c/W4d routes untouched.
 */
public class W4dGeneralPathPlanCompiler internal constructor(
    private val strokePolicyF64: PathStrokePolicyF64,
    private val acceptsNarrowTransforms: Boolean = false,
) : GpuPlanCompiler {
    public constructor() : this(PathStrokePolicyF64())

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) return invalid("Scene and target differ")
        if (SceneSemanticValidator.validate(scene) is SceneSemanticValidationResult.Invalid) return invalid("Scene validation failed")
        if (scene.colorSpace != ColorSpace.SRGB) return gap("W4d.2 supports only sRGB")
        when (val preflight = preflight(scene)) {
            Preflight.Member -> Unit
            Preflight.Outside -> return gap("Scene is outside W4d.2")
            is Preflight.Invalid -> return invalid(preflight.message)
            is Preflight.Limit -> return limit(preflight.message)
        }
        return when (val recognized = recognize(scene)) {
            is Recognition.Ready -> GpuPlanSelection.Candidate(Candidate(this, scene.canonicalId, target, recognized.draws))
            is Recognition.Gap -> gap(recognized.message)
            is Recognition.Invalid -> invalid(recognized.message)
            is Recognition.Horizon -> horizon(recognized.message)
            is Recognition.Limit -> limit(recognized.message)
        }
    }

    /** Establishes ownership before spending any shared :math work ledger. */
    private fun preflight(scene: SceneSnapshot): Preflight {
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    val path = (command.node.geometry as? GeometryNode.Path)?.path
                    val paint = command.node.paint
                    if (!finite(command.node.transform) || !finite(command.node.effects) || !finiteClip(command.node.clip) ||
                        (path != null && !finite(path)) || (paint != null && !finite(paint))
                    ) return Preflight.Invalid("Draw facts are non-finite")
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return Preflight.Invalid("Transform metadata is non-finite")
                is SceneCommand.SetClip -> if (!finiteClip(command.clip)) return Preflight.Invalid("Clip metadata is non-finite")
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return Preflight.Invalid("Annotation bounds are non-finite")
                else -> Unit
            }
        }

        var visualDrawCountI32 = 0
        var requiresGeneral = false
        var outside = false
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    visualDrawCountI32 = Math.addExact(visualDrawCountI32, 1)
                    when (val scope = classifyDrawScope(command.node)) {
                        is DrawScope.Ready -> requiresGeneral = requiresGeneral ||
                            scope.transformClass == PathTransformClass.GeneralAffine ||
                            scope.transformClass == PathTransformClass.Perspective
                        is DrawScope.Gap -> outside = true
                        is DrawScope.Invalid -> return Preflight.Invalid(scope.message)
                    }
                }
                is SceneCommand.SetTransform,
                is SceneCommand.SetClip,
                is SceneCommand.Annotation,
                -> Unit
                else -> outside = true
            }
        }
        if (outside || (!requiresGeneral && !acceptsNarrowTransforms)) return Preflight.Outside
        return if (visualDrawCountI32 > MAX_DRAWS) Preflight.Limit("W4d.2 accepts at most 512 visual path draws") else Preflight.Member
    }

    private fun recognize(scene: SceneSnapshot): Recognition {
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        val draws = mutableListOf<SealedDraw>()
        var visualDrawCountI32 = 0
        var frameWorkUsageI64 = PathStrokeWorkUsageI64()
        scene.withIndex().forEach { (commandIndex, command) ->
            when (command) {
                is SceneCommand.Draw -> {
                    if (visualDrawCountI32 >= MAX_DRAWS) return Recognition.Limit("W4d.2 accepts at most 512 visual path draws")
                    visualDrawCountI32 = Math.addExact(visualDrawCountI32, 1)
                    when (val result = recognizeDraw(command.node, commandIndex, targetBounds, frameWorkUsageI64)) {
                        is DrawResult.Ready -> {
                            draws += result.draw
                            frameWorkUsageI64 = result.frameWorkUsageI64
                        }
                        is DrawResult.Empty -> frameWorkUsageI64 = result.frameWorkUsageI64
                        is DrawResult.Gap -> return Recognition.Gap(result.message)
                        is DrawResult.Invalid -> return Recognition.Invalid(result.message)
                        is DrawResult.Horizon -> return Recognition.Horizon(result.message)
                        is DrawResult.Limit -> return Recognition.Limit(result.message)
                    }
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return Recognition.Invalid("Transform metadata is non-finite")
                is SceneCommand.SetClip -> if (!finiteClip(command.clip)) return Recognition.Invalid("Clip metadata is non-finite")
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return Recognition.Invalid("Annotation bounds are non-finite")
                else -> return Recognition.Gap("Scene command is outside W4d.2")
            }
        }
        return if (draws.isEmpty()) Recognition.Limit("W4d.2 retained no visible prepared geometry") else Recognition.Ready(draws)
    }

    private fun recognizeDraw(
        node: DrawNode,
        commandIndex: Int,
        targetBounds: RectI32,
        frameWorkUsageI64: PathStrokeWorkUsageI64,
    ): DrawResult {
        val scope = when (val classified = classifyDrawScope(node)) {
            is DrawScope.Ready -> classified
            is DrawScope.Gap -> return DrawResult.Gap(classified.message)
            is DrawScope.Invalid -> return DrawResult.Invalid(classified.message)
        }
        return when (val prepared = prepare(scope, frameWorkUsageI64)) {
            is Prepared.Ready -> {
                val targetScissor = intersect(prepared.geometry.copyConservativeScissorI32(), targetBounds)
                    ?: return DrawResult.Empty(prepared.frameWorkUsageI64)
                val scissor = scope.clip?.let { intersect(targetScissor, it) } ?: targetScissor
                if (scissor.isEmpty) return DrawResult.Empty(prepared.frameWorkUsageI64)
                if (prepared.geometry.fillRule == FillRule.WINDING &&
                    prepared.geometry.copyStencilEdgeFanF32OrNull() != null &&
                    prepared.geometry.emittedNonZeroClosedEdgeCountI32 > UByte.MAX_VALUE.toInt()
                ) return DrawResult.Limit("W4d.2 winding path exceeds the stencil edge limit")
                DrawResult.Ready(
                    SealedDraw(
                        commandIndex = commandIndex,
                        color = linear((node.material as MaterialNode.Solid).color),
                        geometry = prepared.pathGeometry,
                        strategy = strategy(prepared.geometry),
                        scissorI32 = scissor.copy(),
                        requestsAntiAlias = scope.requestsAntiAlias,
                    ),
                    prepared.frameWorkUsageI64,
                )
            }
            is Prepared.Empty -> DrawResult.Empty(prepared.frameWorkUsageI64)
            is Prepared.Invalid -> DrawResult.Invalid(prepared.message)
            is Prepared.Horizon -> DrawResult.Horizon(prepared.message)
            is Prepared.Limit -> DrawResult.Limit(prepared.message)
        }
    }

    private fun prepare(scope: DrawScope.Ready, frameWorkUsageI64: PathStrokeWorkUsageI64): Prepared =
        if (scope.fill) {
            when (val result = scope.matrixF64.preparePathFillGeometryF32(
                path = scope.path,
                strokePolicyF64 = strokePolicyF64,
                frameWorkUsageBeforeI64 = frameWorkUsageI64,
            )) {
                is PathTransformedFillPreparationResult.Ready -> Prepared.Ready(
                    result.geometryF32,
                    PathDrawGeometry.Fill(result.geometryF32),
                    result.frameWorkUsageAfterI64,
                )
                is PathTransformedFillPreparationResult.Empty -> Prepared.Empty(result.frameWorkUsageAfterI64)
                is PathTransformedFillPreparationResult.InvalidScene -> if (result.reason.isHorizon()) {
                    Prepared.Horizon("Math rejected perspective horizon crossing")
                } else {
                    Prepared.Invalid("Math rejected fill scene: ${result.reason}")
                }
                is PathTransformedFillPreparationResult.ResourceLimitExceeded ->
                    Prepared.Limit("Math fill limit: ${result.reason}")
            }
        } else {
            when (val result = scope.matrixF64.preparePathStrokeGeometryF32(
                path = scope.path,
                styleF64 = requireNotNull(scope.styleF64),
                mode = requireNotNull(scope.mode),
                policyF64 = strokePolicyF64,
                frameWorkUsageBeforeI64 = frameWorkUsageI64,
            )) {
                is PathStrokePreparationResult.Ready -> Prepared.Ready(
                    result.geometryF32.copyFillGeometryF32(),
                    PathDrawGeometry.Stroke(result.geometryF32),
                    result.frameWorkUsageAfterI64,
                )
                is PathStrokePreparationResult.Empty -> Prepared.Empty(result.frameWorkUsageAfterI64)
                is PathStrokePreparationResult.InvalidScene -> if (
                    result.reason == PathStrokeInvalidSceneReason.ProjectionHorizonCrossing
                ) {
                    Prepared.Horizon("Math rejected perspective horizon crossing")
                } else {
                    Prepared.Invalid("Math rejected stroke scene: ${result.reason}")
                }
                is PathStrokePreparationResult.ResourceLimitExceeded ->
                    Prepared.Limit("Math stroke limit: ${result.reason}")
            }
        }

    private fun classifyDrawScope(node: DrawNode): DrawScope {
        val path = (node.geometry as? GeometryNode.Path)?.path ?: return DrawScope.Gap("Draw geometry is outside W4d.2")
        val paint = node.paint ?: return DrawScope.Gap("W4d.2 requires paint")
        if (!finite(path) || !finite(node.transform) || !finite(paint) || !finite(node.effects) || !finiteClip(node.clip)) {
            return DrawScope.Invalid("Draw facts are non-finite")
        }
        if (node.origin != DrawOrigin.PATH || path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD)) {
            return DrawScope.Gap("Path provenance or fill rule is outside W4d.2")
        }
        if (node.coverage !in setOf(CoverageRequest.HARD_EDGE, CoverageRequest.ANTIALIASED)) {
            return DrawScope.Gap("Coverage is outside W4d.2")
        }
        val clip = when (val value = node.clip) {
            ClipStackNode.Empty -> null
            is ClipStackNode.DeviceRect -> {
                if (value.antiAlias) return DrawScope.Gap("Clip is outside W4d.2")
                integral(value.copyBounds()) ?: return DrawScope.Gap("Clip is outside W4d.2")
            }
            else -> return DrawScope.Gap("Clip is outside W4d.2")
        }
        if (!solid(node, paint)) return DrawScope.Gap("Material, blend, or effect is outside W4d.2")
        val fill = paint.style == PaintStyleNode.FILL
        val stroke = paint.style == PaintStyleNode.STROKE || paint.style == PaintStyleNode.STROKE_AND_FILL
        if (fill && paint.pathEffect != null) return DrawScope.Gap("Path effects require a stroke in W4d.2")
        val mode = if (stroke) if (paint.style == PaintStyleNode.STROKE_AND_FILL) {
            PathStrokeDrawMode.StrokeAndFill
        } else {
            PathStrokeDrawMode.Stroke
        } else null
        val style = if (mode != null) try {
            style(paint, mode)
        } catch (_: IllegalArgumentException) {
            return DrawScope.Invalid("Stroke style is invalid")
        } else null
        val matrixF64 = node.transform.toMatrix3x3F64()
        return DrawScope.Ready(
            path = path,
            matrixF64 = matrixF64,
            transformClass = matrixF64.classifyPathTransform(),
            clip = clip,
            fill = fill,
            mode = mode,
            styleF64 = style,
            requestsAntiAlias = node.coverage == CoverageRequest.ANTIALIASED,
        )
    }

    override fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.hasMatchingFingerprints()) return invalidCandidate()
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        if (!coreCapabilities(capabilities, extent)) return promoted("Required W4d.2 device capability is unavailable")
        val anyAa = selected.draws.any { it.requestsAntiAlias }
        val anyHard = selected.draws.any { !it.requestsAntiAlias }
        val aaStencil = selected.draws.any { it.requestsAntiAlias && it.strategy == PathFillStrategy.StencilCover }
        val hardStencil = selected.draws.any { !it.requestsAntiAlias && it.strategy == PathFillStrategy.StencilCover }
        textureRefusal(capabilities, anyAa, anyHard, hardStencil)?.let { return it }
        if ((anyAa || hardStencil) && PlanOperationCapability.DepthStencilAttachment !in capabilities.supportedOperations()) {
            return promoted("W4d.2 depth-stencil capability is unavailable")
        }
        if ((aaStencil || hardStencil) && PlanOperationCapability.StencilCover !in capabilities.supportedOperations()) {
            return promoted("W4d.2 stencil cover capability is unavailable")
        }
        val geometry = selected.draws.map { it.fillGeometry() }
        return try {
            if (anyAa) planAa(selected, capabilities, budget, geometry, anyHard, hardStencil)
            else planHard(selected, capabilities, budget, geometry, hardStencil)
        } catch (_: ArithmeticException) {
            resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 arithmetic overflowed")
        } catch (_: IllegalArgumentException) {
            resource(W4dGeneralPlanDiagnostics.PlanIdentityInvalid, "W4d.2 graph invariants failed")
        }
    }

    private fun planHard(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        usesStencil: Boolean,
    ): RenderPlanResult<RenderGraph> {
        val provisionalMemory = when (val value = PathStrokePlanBudget.calculate(
            SizeI32(selected.target.extent.width, selected.target.extent.height), geometries, capabilities, budget,
        )) {
            is PathStrokePlanBudgetResult.WithinBudget -> value.footprint
            is PathStrokePlanBudgetResult.Exceeded -> return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
            is PathStrokePlanBudgetResult.Invalid -> return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 size is unrepresentable: ${value.code}")
        }
        val memory = w4dGeneralExactFrameMemory(
            provisionalMemory,
            w4dGeneralNativePayloadBudget(selected.draws, materializesHardMasks = false),
            capabilities,
        ) ?: return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 native frame resources overflow")
        if (memory.peakBytes > budget.maxFrameLocalBytes) {
            return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
        }
        if (!buffersFit(memory, capabilities)) return promoted("W4d.2 buffer capability is unavailable")
        return RenderPlanResult.Ready(
            RenderGraph.issueW4dGeneralCompilerWitness(
                hardGraph(selected, capabilities, budget, memory, usesStencil),
            ),
        )
    }

    private fun planAa(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        anyHard: Boolean,
        hardStencil: Boolean,
    ): RenderPlanResult<RenderGraph> {
        val provisionalMemory = when (val value = PathAaPlanBudget.calculate(
            targetExtent = SizeI32(selected.target.extent.width, selected.target.extent.height),
            geometriesF32 = geometries,
            requiresAa4DepthStencil = true,
            requiresHardMask = anyHard,
            requiresHardEdgeDepthStencil = hardStencil,
            capabilities = capabilities,
            budget = budget,
        )) {
            is PathAaPlanBudgetResult.WithinBudget -> value.footprint
            is PathAaPlanBudgetResult.Exceeded -> return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
            is PathAaPlanBudgetResult.Invalid -> return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 size is unrepresentable: ${value.code}")
        }
        val base = w4dGeneralExactFrameMemory(
            provisionalMemory.base,
            w4dGeneralNativePayloadBudget(selected.draws, materializesHardMasks = true),
            capabilities,
        ) ?: return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 native frame resources overflow")
        val terminalPeakBytes = try {
            Math.subtractExact(base.peakBytes, base.depthStencilBytes)
        } catch (_: ArithmeticException) {
            return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 native frame resources overflow")
        }
        val colorPeakBytes = try {
            listOf(
                terminalPeakBytes,
                -base.readbackBytes,
                provisionalMemory.multisampleColorBytes,
                provisionalMemory.multisampleDepthStencilBytes,
                provisionalMemory.hardEdgeMaskCapacityBytes,
                provisionalMemory.hardEdgeDepthStencilCapacityBytes,
            ).fold(0L, Math::addExact)
        } catch (_: ArithmeticException) {
            return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 native frame resources overflow")
        }
        val memory = provisionalMemory.copy(
            base = base,
            peakBytes = maxOf(terminalPeakBytes, colorPeakBytes),
        )
        if (memory.peakBytes > budget.maxFrameLocalBytes) {
            return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
        }
        if (!buffersFit(memory.base, capabilities)) return promoted("W4d.2 buffer capability is unavailable")
        return RenderPlanResult.Ready(
            RenderGraph.issueW4dGeneralCompilerWitness(
                aaGraph(selected, capabilities, budget, memory, hardStencil),
            ),
        )
    }

    private fun hardGraph(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        memory: PathFillMemoryFootprint,
        usesStencil: Boolean,
    ): RenderGraph {
        val pathPassCount = selected.draws.sumOf { if (it.strategy == PathFillStrategy.DirectTriangle) 1 else 2 }
        val passCount = Math.addExact(pathPassCount, 1)
        val readbackIndex = pathPassCount
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        fun resource(
            role: PlanResourceRole,
            kind: PlanResourceKind,
            format: PlanTextureFormat?,
            bytes: Long,
            usages: Set<PlanResourceUsage>,
            first: Int = 0,
            last: Int = passCount,
            samples: Int = 1,
        ) = PlanResource.of(role, 0, kind, format, if (kind == PlanResourceKind.Texture2D) extent else null,
            bytes, usages, PlanResourceLifetime.FrameLocal, first, last, samples)
        val target = resource(PlanResourceRole.LogicalTarget, PlanResourceKind.Texture2D, PlanTextureFormat.Color(FORMAT),
            memory.targetBytes, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource))
        val staging = resource(PlanResourceRole.ReadbackStaging, PlanResourceKind.Buffer, null, memory.readbackBytes,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), readbackIndex, passCount)
        val vertex = resource(PlanResourceRole.VertexData, PlanResourceKind.Buffer, null, memory.vertexCapacityBytes,
            setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination))
        val index = resource(PlanResourceRole.IndexData, PlanResourceKind.Buffer, null, memory.indexCapacityBytes,
            setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination))
        val uniform = resource(PlanResourceRole.UniformData, PlanResourceKind.Buffer, null, memory.uniformCapacityBytes,
            setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination))
        val depth = if (usesStencil) resource(PlanResourceRole.DepthStencil, PlanResourceKind.Texture2D,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), memory.depthStencilBytes,
            setOf(PlanResourceUsage.DepthStencilAttachment)) else null
        val data = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        val passes = mutableListOf<PlanPass>()
        var ordinal = 0
        var clear = true
        selected.draws.forEach { sealed ->
            val draw = generalDraw(sealed, CoveragePlan.FullOrScissor, SamplePlan.SingleSample)
            val load = if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load
            if (sealed.strategy == PathFillStrategy.DirectTriangle) {
                passes += PlanPass.PathRenderPass(ordinal++, target.id, draw, PathRenderPhase.SingleSampleDirectColor,
                    data, null, null, load, AttachmentStorePlan.Store, null, null, null)
            } else {
                val group = canonicalGeneralPathAtomicGroup(draw)
                passes += PlanPass.PathRenderPass(ordinal++, target.id, draw, PathRenderPhase.SingleSampleStencilProducer,
                    data, group, requireNotNull(depth).id, load, AttachmentStorePlan.Store,
                    PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore, null)
                passes += PlanPass.PathRenderPass(ordinal++, target.id, draw, PathRenderPhase.SingleSampleStencilColorCover,
                    data, group, depth.id, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                    PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset, null)
            }
            clear = false
        }
        passes += PlanPass.ReadbackPass(0, target.id, staging.id, memory.readbackBytesPerRow)
        return RenderGraph.of(
            id = PlanId(identity(selected, capabilities, budget, HARD_CAPABILITY_ID)),
            capabilityId = HARD_CAPABILITY_ID,
            targetExtent = extent,
            colorFormat = FORMAT,
            capabilities = capabilities,
            budget = budget,
            visualCommandCount = selected.draws.size,
            resources = buildList { add(target); add(staging); add(vertex); add(index); add(uniform); depth?.let(::add) },
            passes = passes,
            dependencies = dependencies(passes),
            peakFrameLocalBytes = memory.peakBytes,
        )
    }

    private fun aaGraph(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        memory: PathAaMemoryFootprint,
        hardStencil: Boolean,
    ): RenderGraph {
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        val logicalId = planResourceId(PlanResourceRole.LogicalTarget, 0)
        val multisampleId = planResourceId(PlanResourceRole.MultisampleColorTarget, 0)
        val aaDepthId = planResourceId(PlanResourceRole.DepthStencil, 0)
        val vertexId = planResourceId(PlanResourceRole.VertexData, 0)
        val indexId = planResourceId(PlanResourceRole.IndexData, 0)
        val uniformId = planResourceId(PlanResourceRole.UniformData, 0)
        val data = PlanDrawDataResources(vertexId, indexId, uniformId)
        val passes = mutableListOf<PlanPass>()
        val maskLife = mutableListOf<ResourceLife>()
        val hardDepthLife = mutableListOf<ResourceLife>()
        val colorPassIndexes = mutableListOf<Int>()
        var pathOrdinal = 0
        var clearOrdinal = 0
        var maskOrdinal = 0
        var hardDepthOrdinal = 0
        var clearColor = true
        selected.draws.forEach { sealed ->
            if (sealed.requestsAntiAlias) {
                val draw = generalDraw(sealed, CoveragePlan.StencilAA4, SamplePlan.Multisample4)
                val load = if (clearColor) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load
                if (sealed.strategy == PathFillStrategy.DirectTriangle) {
                    passes += PlanPass.PathRenderPass(pathOrdinal++, multisampleId, draw, PathRenderPhase.MultisampleDirectColor,
                        data, null, aaDepthId, load, AttachmentStorePlan.Store, null, null, null)
                    colorPassIndexes += passes.lastIndex
                } else {
                    val group = canonicalGeneralPathAtomicGroup(draw)
                    passes += PlanPass.PathRenderPass(pathOrdinal++, multisampleId, draw, PathRenderPhase.MultisampleStencilProducer,
                        data, group, aaDepthId, load, AttachmentStorePlan.Store,
                        PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore, null)
                    passes += PlanPass.PathRenderPass(pathOrdinal++, multisampleId, draw, PathRenderPhase.MultisampleStencilColorCover,
                        data, group, aaDepthId, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                        PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset, null)
                    colorPassIndexes += passes.lastIndex
                }
                clearColor = false
            } else {
                val producer = generalDraw(sealed, CoveragePlan.FullOrScissor, SamplePlan.SingleSample)
                val group = canonicalGeneralPathAtomicGroup(producer)
                val maskId = planResourceId(PlanResourceRole.PathHardEdgeMask, maskOrdinal++)
                val clearIndex = passes.size
                passes += PlanPass.PathMaskClearPass(clearOrdinal++, maskId, group)
                if (sealed.strategy == PathFillStrategy.DirectTriangle) {
                    passes += PlanPass.PathRenderPass(pathOrdinal++, maskId, producer, PathRenderPhase.HardEdgeMaskProducer,
                        data, group, null, AttachmentLoadPlan.Load, AttachmentStorePlan.Store, null, null, null)
                } else {
                    val depthId = planResourceId(PlanResourceRole.PathHardEdgeDepthStencil, hardDepthOrdinal++)
                    val producerIndex = passes.size
                    passes += PlanPass.PathRenderPass(pathOrdinal++, maskId, producer, PathRenderPhase.HardEdgeMaskStencilProducer,
                        data, group, depthId, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                        PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore, null)
                    passes += PlanPass.PathRenderPass(pathOrdinal++, maskId, producer, PathRenderPhase.HardEdgeMaskStencilCover,
                        data, group, depthId, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                        PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset, null)
                    hardDepthLife += ResourceLife(hardDepthOrdinal - 1, producerIndex, passes.size)
                }
                val load = if (clearColor) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load
                passes += PlanPass.PathRenderPass(pathOrdinal++, multisampleId, BinaryMaskedPathDraw.of(producer, maskId),
                    PathRenderPhase.HardEdgeBinaryColorCover, data, group, null, load, AttachmentStorePlan.Store,
                    null, null, null)
                colorPassIndexes += passes.lastIndex
                maskLife += ResourceLife(maskOrdinal - 1, clearIndex, passes.size)
                clearColor = false
            }
        }
        val finalColorIndex = colorPassIndexes.last()
        val finalColor = assertPathRenderPass(passes[finalColorIndex])
        passes[finalColorIndex] = PlanPass.PathRenderPass(
            finalColor.ordinal, finalColor.target, finalColor.draw, finalColor.phase, finalColor.drawDataResources,
            finalColor.atomicGroup, finalColor.depthStencil, finalColor.load, finalColor.store,
            finalColor.depthStencilAccess, finalColor.depthStencilLoadStore, logicalId,
        )
        val readbackIndex = passes.size
        val passCount = Math.addExact(readbackIndex, 1)
        val resources = mutableListOf<PlanResource>()
        fun texture(
            role: PlanResourceRole,
            ordinal: Int,
            format: PlanTextureFormat,
            bytes: Long,
            usages: Set<PlanResourceUsage>,
            first: Int,
            last: Int,
            samples: Int,
        ) = PlanResource.of(role, ordinal, PlanResourceKind.Texture2D, format, extent, bytes, usages,
            PlanResourceLifetime.FrameLocal, first, last, samples)
        fun buffer(role: PlanResourceRole, bytes: Long, usages: Set<PlanResourceUsage>, first: Int, last: Int) =
            PlanResource.of(role, 0, PlanResourceKind.Buffer, null, null, bytes, usages,
                PlanResourceLifetime.FrameLocal, first, last)
        resources += texture(PlanResourceRole.MultisampleColorTarget, 0, PlanTextureFormat.Color(FORMAT),
            memory.multisampleColorBytes, setOf(PlanResourceUsage.RenderAttachment), 0, readbackIndex, 4)
        resources += texture(PlanResourceRole.LogicalTarget, 0, PlanTextureFormat.Color(FORMAT), memory.base.targetBytes,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), 0, passCount, 1)
        resources += texture(PlanResourceRole.DepthStencil, 0, PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            memory.multisampleDepthStencilBytes, setOf(PlanResourceUsage.DepthStencilAttachment), 0, readbackIndex, 4)
        maskLife.forEach { life -> resources += texture(PlanResourceRole.PathHardEdgeMask, life.ordinal,
            PlanTextureFormat.CoverageMask, memory.hardEdgeMaskCapacityBytes,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), life.first, life.last, 1) }
        hardDepthLife.forEach { life -> resources += texture(PlanResourceRole.PathHardEdgeDepthStencil, life.ordinal,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), memory.hardEdgeDepthStencilCapacityBytes,
            setOf(PlanResourceUsage.DepthStencilAttachment), life.first, life.last, 1) }
        resources += buffer(PlanResourceRole.ReadbackStaging, memory.base.readbackBytes,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), readbackIndex, passCount)
        resources += buffer(PlanResourceRole.VertexData, memory.base.vertexCapacityBytes,
            setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination), 0, passCount)
        resources += buffer(PlanResourceRole.IndexData, memory.base.indexCapacityBytes,
            setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination), 0, passCount)
        resources += buffer(PlanResourceRole.UniformData, memory.base.uniformCapacityBytes,
            setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), 0, passCount)
        val staging = resources.single { it.role == PlanResourceRole.ReadbackStaging }
        passes += PlanPass.ReadbackPass(0, logicalId, staging.id, memory.base.readbackBytesPerRow)
        return RenderGraph.of(
            id = PlanId(identity(selected, capabilities, budget, AA_CAPABILITY_ID)),
            capabilityId = AA_CAPABILITY_ID,
            targetExtent = extent,
            colorFormat = FORMAT,
            capabilities = capabilities,
            budget = budget,
            visualCommandCount = selected.draws.size,
            resources = resources,
            passes = passes,
            dependencies = dependencies(passes),
            peakFrameLocalBytes = memory.peakBytes,
        )
    }

    private fun generalDraw(sealed: SealedDraw, coverage: CoveragePlan, sample: SamplePlan): GeneralPathDraw =
        GeneralPathDraw.of(sealed.commandIndex, sealed.color, sealed.geometry, sealed.strategy, sealed.scissorI32, coverage, sample)

    private fun coreCapabilities(capabilities: PlanCapabilitySnapshot, extent: SizeI32): Boolean =
        extent.width <= capabilities.maxTextureDimension2D && extent.height <= capabilities.maxTextureDimension2D &&
            FORMAT in capabilities.supportedFormats() && capabilities.maxDynamicUniformBuffersPerPipelineLayout >= 1 &&
            REQUIRED.all { it in capabilities.supportedOperations() } && validAllocationFacts(capabilities)

    private fun textureRefusal(
        caps: PlanCapabilitySnapshot,
        anyAa: Boolean,
        anyHard: Boolean,
        hardStencil: Boolean,
    ): RenderPlanResult.GapOnPromotedScope? {
        val logical = PlanTextureFormat.Color(FORMAT)
        if (!caps.supportsTexture(logical, 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource))) {
            return promoted(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, "W4d.2 logical target support is unavailable")
        }
        if (!anyAa) return if (!hardStencil) null else if (caps.supportsTexture(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            )) null else promoted(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, "W4d.2 hard depth-stencil support is unavailable")
        if (!caps.supportsTexture(logical, 4, setOf(PlanResourceUsage.RenderAttachment))) {
            return promoted(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, "W4d.2 four-sample color support is unavailable")
        }
        if (!caps.supportsResolve(logical, 4, 1)) {
            return promoted(W4dGeneralPlanDiagnostics.ResolveUnsupported, "W4d.2 four-sample color resolve is unavailable")
        }
        if (!caps.supportsTexture(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4,
                setOf(PlanResourceUsage.DepthStencilAttachment))) {
            return promoted(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, "W4d.2 four-sample depth-stencil support is unavailable")
        }
        if (anyHard && !caps.supportsTexture(PlanTextureFormat.CoverageMask, 1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled))) {
            return promoted(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, "W4d.2 hard mask support is unavailable")
        }
        return if (!hardStencil || caps.supportsTexture(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1,
                setOf(PlanResourceUsage.DepthStencilAttachment))) null
        else promoted(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, "W4d.2 hard depth-stencil support is unavailable")
    }

    /**
     * Exact native payload totals for the W4d.2 pass sequence.  The ordinary geometry budget is
     * deliberately insufficient here: hard AA bridges add binary cover quads and Uniform64
     * consumer slots which do not exist in the original draw geometry.
     */
    private data class W4dGeneralNativePayloadBudget(
        val vertexFloatCount: Long,
        val indexCount: Long,
        val uniformPayloadBytes: List<Long>,
    )

    private fun w4dGeneralNativePayloadBudget(
        draws: List<SealedDraw>,
        materializesHardMasks: Boolean,
    ): W4dGeneralNativePayloadBudget {
        var vertexFloatCount = 0L
        var indexCount = 0L
        val uniforms = mutableListOf<Long>()
        fun addQuad() {
            vertexFloatCount = Math.addExact(vertexFloatCount, 8L)
            indexCount = Math.addExact(indexCount, 6L)
        }
        draws.forEach { draw ->
            val geometry = draw.fillGeometry()
            when (draw.strategy) {
                PathFillStrategy.DirectTriangle -> {
                    val direct = requireNotNull(geometry.copyDirectTriangleF32OrNull())
                    vertexFloatCount = Math.addExact(vertexFloatCount, direct.copyVerticesF32().size.toLong())
                    indexCount = Math.addExact(indexCount, direct.copyIndicesI32().size.toLong())
                    uniforms += 32L
                    if (materializesHardMasks && !draw.requestsAntiAlias) {
                        addQuad()
                        uniforms += 64L
                    }
                }
                PathFillStrategy.StencilCover -> {
                    val fan = requireNotNull(geometry.copyStencilEdgeFanF32OrNull())
                    vertexFloatCount = Math.addExact(vertexFloatCount, fan.copyVerticesF32().size.toLong())
                    indexCount = Math.addExact(indexCount, fan.copyIndicesI32().size.toLong())
                    addQuad()
                    uniforms += 32L
                    uniforms += 32L
                    if (materializesHardMasks && !draw.requestsAntiAlias) {
                        addQuad()
                        uniforms += 64L
                    }
                }
            }
        }
        return W4dGeneralNativePayloadBudget(vertexFloatCount, indexCount, uniforms.toList())
    }

    private fun w4dGeneralExactFrameMemory(
        provisional: PathFillMemoryFootprint,
        payload: W4dGeneralNativePayloadBudget,
        capabilities: PlanCapabilitySnapshot,
    ): PathFillMemoryFootprint? = try {
        val vertexUsefulBytes = Math.multiplyExact(payload.vertexFloatCount, Float.SIZE_BYTES.toLong())
        val indexUsefulBytes = Math.multiplyExact(payload.indexCount, Int.SIZE_BYTES.toLong())
        val alignment = capabilities.minUniformBufferOffsetAlignment.toLong()
        val uniformUsefulBytes = payload.uniformPayloadBytes.fold(0L, Math::addExact)
        val uniformReservedBytes = payload.uniformPayloadBytes.fold(0L) { total, bytes ->
            Math.addExact(total, w4dGeneralAlignUp(bytes, alignment))
        }
        val policy = capabilities.bufferAllocationPolicy
        val vertexCapacityBytes = policy.reserve(PlanScratchBufferKind.Vertex, vertexUsefulBytes) ?: return null
        val indexCapacityBytes = policy.reserve(PlanScratchBufferKind.Index, indexUsefulBytes) ?: return null
        val uniformCapacityBytes = policy.reserve(PlanScratchBufferKind.Uniform, uniformReservedBytes) ?: return null
        if (listOf(vertexCapacityBytes, indexCapacityBytes, uniformCapacityBytes).any { value ->
                value > Int.MAX_VALUE.toLong()
            }
        ) return null
        val peakBytes = listOf(
            provisional.targetBytes,
            provisional.readbackBytes,
            vertexCapacityBytes,
            indexCapacityBytes,
            uniformCapacityBytes,
            provisional.depthStencilBytes,
        ).fold(0L, Math::addExact)
        provisional.copy(
            vertexUsefulBytes = vertexUsefulBytes,
            indexUsefulBytes = indexUsefulBytes,
            uniformStrideBytes = alignment,
            uniformUsefulBytes = uniformUsefulBytes,
            vertexCapacityBytes = vertexCapacityBytes,
            indexCapacityBytes = indexCapacityBytes,
            uniformCapacityBytes = uniformCapacityBytes,
            peakBytes = peakBytes,
        )
    } catch (_: ArithmeticException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun w4dGeneralAlignUp(value: Long, alignment: Long): Long {
        require(value > 0L && alignment > 0L)
        val remainder = value % alignment
        return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
    }

    private fun buffersFit(memory: PathFillMemoryFootprint, capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        memory.readbackBytes, memory.vertexCapacityBytes, memory.indexCapacityBytes, memory.uniformCapacityBytes,
    ).all { it <= capabilities.maxBufferSizeBytes }

    private fun strategy(geometry: org.graphiks.math.geometry.PathFillGeometryF32): PathFillStrategy = when {
        geometry.copyDirectTriangleF32OrNull() != null && geometry.copyStencilEdgeFanF32OrNull() == null ->
            PathFillStrategy.DirectTriangle
        geometry.copyDirectTriangleF32OrNull() == null && geometry.copyStencilEdgeFanF32OrNull() != null ->
            PathFillStrategy.StencilCover
        else -> throw IllegalArgumentException("Prepared path geometry did not select one strategy")
    }

    private fun SealedDraw.fillGeometry(): org.graphiks.math.geometry.PathFillGeometryF32 = when (geometry) {
        is PathDrawGeometry.Fill -> geometry.valueF32
        is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
    }

    private fun PathTransformedFillInvalidSceneReason.isHorizon(): Boolean =
        this is PathTransformedFillInvalidSceneReason.Projective &&
            value == PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing

    private fun solid(node: DrawNode, paint: PaintNode): Boolean = node.material is MaterialNode.Solid &&
        effectsMatchPaintPathEffect(node.effects, paint.pathEffect) && node.resource == null &&
        node.operationBlendMode == null && w4Blend(node.blend) && paint.shader == null && paint.blender == null &&
        paint.colorFilter == null && paint.maskFilter == null && paint.imageFilter == null &&
        paint.blendMode == BlendMode.SRC_OVER && (paint.pathEffect == null || paint.pathEffect is PathEffectNode.Dash)

    private fun effectsMatchPaintPathEffect(effects: EffectStack, pathEffect: PathEffectNode?): Boolean = when (effects) {
        EffectStack.Empty -> true
        is EffectStack.Entries -> if (effects.effectCount != 1) false else {
            val paintDash = pathEffect as? PathEffectNode.Dash
            val entryDash = effects.effectAt(0) as? PathEffectNode.Dash
            paintDash != null && entryDash != null && sameDashBits(paintDash, entryDash)
        }
    }

    private fun sameDashBits(first: PathEffectNode.Dash, second: PathEffectNode.Dash): Boolean {
        if (first.phase.toRawBits() != second.phase.toRawBits()) return false
        val a = first.intervals.copyToFloatArray()
        val b = second.intervals.copyToFloatArray()
        return a.size == b.size && a.indices.all { a[it].toRawBits() == b[it].toRawBits() }
    }

    private fun style(paint: PaintNode, mode: PathStrokeDrawMode): PathStrokeStyleF64 = PathStrokeStyleF64(
        if (paint.strokeWidth == 0f && mode == PathStrokeDrawMode.Stroke) PathStrokeWidthF64.Hairline
        else PathStrokeWidthF64.Finite(paint.strokeWidth.toDouble()),
        when (paint.strokeCap) { StrokeCapNode.BUTT -> PathStrokeCap.Butt; StrokeCapNode.ROUND -> PathStrokeCap.Round; StrokeCapNode.SQUARE -> PathStrokeCap.Square },
        when (paint.strokeJoin) { StrokeJoinNode.MITER -> PathStrokeJoin.Miter; StrokeJoinNode.ROUND -> PathStrokeJoin.Round; StrokeJoinNode.BEVEL -> PathStrokeJoin.Bevel },
        paint.strokeMiter.toDouble(),
        (paint.pathEffect as? PathEffectNode.Dash)?.let { dash ->
            PathStrokeDashF64.of(dash.intervals.copyToFloatArray().map(Float::toDouble).toDoubleArray(), dash.phase.toDouble())
        },
    )

    private fun integral(bounds: RectF32): RectI32? = if (!finite(bounds)) null else listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        .map { value -> value.toLong().takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && it.toFloat() == value } }
        .takeIf { it.none { value -> value == null } }
        ?.let { RectI32(it[0]!!.toInt(), it[1]!!.toInt(), it[2]!!.toInt(), it[3]!!.toInt()).takeUnless(RectI32::isEmpty64) }

    private fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }

    private fun finiteClip(clip: ClipStackNode): Boolean = when (clip) {
        ClipStackNode.Empty -> true
        is ClipStackNode.DeviceRect -> finite(clip.copyBounds())
        is ClipStackNode.Operations -> clip.all { entry -> when (val geometry = entry.geometry) {
            is GeometryNode.Rect -> finite(geometry.copyBounds())
            is GeometryNode.RRect -> finite(geometry.copyShape())
            is GeometryNode.Path -> finite(geometry.path)
            else -> false
        } }
    }

    private fun finite(path: PathF32): Boolean = path.all { segment -> when (segment) {
        is org.graphiks.math.geometry.PathSegmentF32.MoveTo -> finite(segment.point)
        is org.graphiks.math.geometry.PathSegmentF32.LineTo -> finite(segment.point)
        is org.graphiks.math.geometry.PathSegmentF32.QuadTo -> finite(segment.control) && finite(segment.point)
        is org.graphiks.math.geometry.PathSegmentF32.CubicTo -> finite(segment.control1) && finite(segment.control2) && finite(segment.point)
        is org.graphiks.math.geometry.PathSegmentF32.ArcTo -> finite(segment.point) && segment.radius.x.isFinite() && segment.radius.y.isFinite() && segment.xAxisRotation.isFinite()
        org.graphiks.math.geometry.PathSegmentF32.Close -> true
    } }
    private fun finite(point: org.graphiks.math.geometry.Point2F32): Boolean = point.x.isFinite() && point.y.isFinite()
    private fun finite(bounds: RectF32): Boolean = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite)
    private fun finite(matrix: Matrix3x3F32): Boolean = listOf(matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty, matrix.persp0, matrix.persp1, matrix.persp2).all(Float::isFinite)
    private fun finite(paint: PaintNode): Boolean = paint.strokeWidth.isFinite() && paint.strokeMiter.isFinite() && finite(paint.pathEffect)
    private fun finite(effects: EffectStack): Boolean = when (effects) { EffectStack.Empty -> true; is EffectStack.Entries -> effects.all { effect -> (effect as? PathEffectNode)?.let(::finite) ?: true } }
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
    private fun w4Blend(blend: BlendNode): Boolean = when (blend) { BlendNode.SrcOver -> true; is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER; is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null; is BlendNode.Custom -> false }
    private fun linear(color: ColorARGB): ColorF32 { val alpha = color.alphaNormalized; return ColorF32.of(ColorTransferFunction.sRgb.toLinear(color.redNormalized) * alpha, ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * alpha, ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * alpha, alpha) }
    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(), capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes, capabilities.bufferAllocationPolicy.indexFloorBytes,
        capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { it > 0L && it and (it - 1L) == 0L }

    private fun identity(selected: Candidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget, capability: String): String {
        val fields = listOf(
            "w4d-general-plan-v1", capability, selected.sceneCanonicalId.value, selected.target.canonicalId.value,
            capabilities.deviceGeneration.toString(), capabilities.maxTextureDimension2D.toString(), capabilities.maxBufferSizeBytes.toString(),
            capabilities.copyBytesPerRowAlignment.toString(), capabilities.supportedFormats().map { it.name }.sorted().joinToString(","),
            capabilities.minUniformBufferOffsetAlignment.toString(), capabilities.maxDynamicUniformBuffersPerPipelineLayout.toString(),
            capabilities.supportedOperations().map { it.name }.sorted().joinToString(","),
            capabilities.bufferAllocationPolicy.vertexFloorBytes.toString(), capabilities.bufferAllocationPolicy.indexFloorBytes.toString(),
            capabilities.bufferAllocationPolicy.uniformFloorBytes.toString(), capabilities.bufferAllocationPolicy.growth.name,
            capabilities.supportedDepthStencilFormats().map { it.name }.sorted().joinToString(","), budget.maxFrameLocalBytes.toString(),
            strokePolicyF64.maximumSagittaErrorF64.toRawBits().toString(), strokePolicyF64.maximumDashArcLengthErrorF64.toRawBits().toString(),
            strokePolicyF64.limitsI32.maxSubdivisionDepthI32.toString(), strokePolicyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32.toString(),
            strokePolicyF64.limitsI32.maxAttemptedGeometryUnitsPerFrameI32.toString(), strokePolicyF64.limitsI32.maxEmittedVertexCountPerPathI32.toString(),
            strokePolicyF64.limitsI32.maxEmittedVertexCountPerFrameI32.toString(), strokePolicyF64.limitsI32.maxEmittedIndexCountPerPathI32.toString(),
            strokePolicyF64.limitsI32.maxEmittedIndexCountPerFrameI32.toString(), strokePolicyF64.limitsI64.maxSnapshotByteCountPerPathI64.toString(),
            strokePolicyF64.limitsI64.maxSnapshotByteCountPerFrameI64.toString(),
        ) + planCapabilityIdentityFacts(capabilities)
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.encodeToByteArray()
            digest.update(bytes.size.toString().encodeToByteArray())
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun dependencies(passes: List<PlanPass>): List<PlanPassDependency> =
        passes.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) }
    private fun assertPathRenderPass(pass: PlanPass): PlanPass.PathRenderPass = pass as? PlanPass.PathRenderPass
        ?: throw IllegalStateException("Expected a path render pass")
    private fun gap(message: String): GpuPlanSelection.NotCandidate = GpuPlanSelection.NotCandidate(listOf(diag(W4dGeneralPlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)))
    private fun invalid(message: String): GpuPlanSelection.InvalidScene = GpuPlanSelection.InvalidScene(listOf(diag(W4dGeneralPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message)))
    private fun horizon(message: String): GpuPlanSelection.InvalidScene = GpuPlanSelection.InvalidScene(listOf(diag(W4dGeneralPlanDiagnostics.ProjectionHorizonCrossing, RenderDiagnosticDomain.SCENE, message)))
    private fun limit(message: String): GpuPlanSelection.ResourceLimitExceeded = GpuPlanSelection.ResourceLimitExceeded(listOf(diag(W4dGeneralPlanDiagnostics.PathResourceLimit, RenderDiagnosticDomain.SCENE, message)))
    private fun resource(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, message: String): RenderPlanResult.ResourceLimitExceeded = RenderPlanResult.ResourceLimitExceeded(listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)))
    private fun promoted(message: String): RenderPlanResult.GapOnPromotedScope = promoted(W4dGeneralPlanDiagnostics.CapabilityUnavailable, message)
    private fun promoted(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, message: String): RenderPlanResult.GapOnPromotedScope = RenderPlanResult.GapOnPromotedScope(listOf(diag(code, RenderDiagnosticDomain.CAPABILITY, message)))
    private fun invalidCandidate(): RenderPlanResult.InvalidScene = RenderPlanResult.InvalidScene(listOf(diag(W4dGeneralPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Candidate does not belong to W4d.2")))
    private fun diag(code: org.graphiks.kanvas.render.ir.RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic = W4dGeneralPlanDiagnostics.diagnostic(code, domain, message)

    private sealed interface Preflight { data object Member : Preflight; data object Outside : Preflight; data class Invalid(val message: String) : Preflight; data class Limit(val message: String) : Preflight }
    private sealed interface Recognition { data class Ready(val draws: List<SealedDraw>) : Recognition; data class Gap(val message: String) : Recognition; data class Invalid(val message: String) : Recognition; data class Horizon(val message: String) : Recognition; data class Limit(val message: String) : Recognition }
    private sealed interface DrawScope {
        data class Ready(val path: PathF32, val matrixF64: Matrix3x3F64, val transformClass: PathTransformClass, val clip: RectI32?, val fill: Boolean, val mode: PathStrokeDrawMode?, val styleF64: PathStrokeStyleF64?, val requestsAntiAlias: Boolean) : DrawScope
        data class Gap(val message: String) : DrawScope
        data class Invalid(val message: String) : DrawScope
    }
    private sealed interface DrawResult { data class Ready(val draw: SealedDraw, val frameWorkUsageI64: PathStrokeWorkUsageI64) : DrawResult; data class Empty(val frameWorkUsageI64: PathStrokeWorkUsageI64) : DrawResult; data class Gap(val message: String) : DrawResult; data class Invalid(val message: String) : DrawResult; data class Horizon(val message: String) : DrawResult; data class Limit(val message: String) : DrawResult }
    private sealed interface Prepared { data class Ready(val geometry: org.graphiks.math.geometry.PathFillGeometryF32, val pathGeometry: PathDrawGeometry, val frameWorkUsageI64: PathStrokeWorkUsageI64) : Prepared; data class Empty(val frameWorkUsageI64: PathStrokeWorkUsageI64) : Prepared; data class Invalid(val message: String) : Prepared; data class Horizon(val message: String) : Prepared; data class Limit(val message: String) : Prepared }
    private data class SealedDraw(val commandIndex: Int, val color: ColorF32, val geometry: PathDrawGeometry, val strategy: PathFillStrategy, val scissorI32: RectI32, val requestsAntiAlias: Boolean)
    private data class ResourceLife(val ordinal: Int, val first: Int, val last: Int)
    private class Candidate(val owner: W4dGeneralPathPlanCompiler, override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId, override val target: RenderTargetDescriptor, draws: List<SealedDraw>) : GpuPlanCandidate {
        override val capabilityId: String = if (draws.any { it.requestsAntiAlias }) AA_CAPABILITY_ID else HARD_CAPABILITY_ID
        val draws: List<SealedDraw> = Collections.unmodifiableList(draws.map { it.copy(scissorI32 = it.scissorI32.copy()) })
        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId
        fun hasMatchingFingerprints(): Boolean = (capabilityId == AA_CAPABILITY_ID || capabilityId == HARD_CAPABILITY_ID) && sceneCanonicalId == sceneFingerprint && target.canonicalId == targetFingerprint
    }

    public companion object {
        public const val HARD_CAPABILITY_ID: String = "solid-path-geometry-hard-1x-general-transform-simple-scissor-src-over-srgb-v1"
        public const val AA_CAPABILITY_ID: String = "solid-path-geometry-mixed-aa4-general-transform-simple-scissor-src-over-srgb-v1"
        private val FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        private val REQUIRED = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload, PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback)
        private const val MAX_DRAWS = 512
    }
}
