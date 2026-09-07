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
            is Recognition.Ready -> GpuPlanSelection.Candidate(Candidate(this, scene.canonicalId, target, recognized.draws))
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
        var visualDrawCount = 0
        var frameWork = PathStrokeWorkUsageI64()
        var sawStroke = false
        scene.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Draw -> {
                    if (visualDrawCount >= MAX_DRAWS) return Recognition.Gap("W4d accepts at most 512 visual path draws")
                    visualDrawCount = Math.addExact(visualDrawCount, 1)
                    when (val draw = recognizeDraw(command.node, index, targetBounds, frameWork)) {
                        is DrawResult.Ready -> { draws += draw.draw; frameWork = draw.frameWork; sawStroke = sawStroke || draw.stroke }
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
        if (draws.isEmpty()) return Recognition.Limit("W4d retained no visible prepared geometry")
        return Recognition.Ready(draws)
    }

    private fun recognizeDraw(node: DrawNode, commandIndex: Int, targetBounds: RectI32, frameWork: PathStrokeWorkUsageI64): DrawResult {
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
                ) DrawResult.Limit("Winding path exceeds the W4d stencil edge limit") else DrawResult.Ready(
                    SealedDraw(commandIndex, linear(node.material.let { (it as MaterialNode.Solid).color }), result.geometry, result.strokeGeometry, result.mode, result.styleF64, scissor),
                    result.work,
                    scope.stroke,
                )
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
        if (!finite(path) || !finite(node.transform) || !finite(paint) || !finite(node.effects)) {
            return DrawScope.Invalid("Draw facts are non-finite")
        }
        if (node.origin != DrawOrigin.PATH || path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD)) {
            return DrawScope.Gap("Path provenance or fill rule is outside W4d")
        }
        if (node.coverage != CoverageRequest.HARD_EDGE || !(node.transform.isIdentity || node.transform.isScaleTranslate())) {
            return DrawScope.Gap("Coverage or transform is outside W4d")
        }
        if (!finiteClip(node.clip)) return DrawScope.Invalid("Clip metadata is non-finite")
        val clip = when (val value = node.clip) {
            ClipStackNode.Empty -> null
            is ClipStackNode.DeviceRect -> {
                if (value.antiAlias) return DrawScope.Gap("Clip is outside W4d")
                integral(value.copyBounds()) ?: return DrawScope.Gap("Clip is outside W4d")
            }
            else -> return DrawScope.Gap("Clip is outside W4d")
        }
        if (!solid(node, paint)) return DrawScope.Gap("Material, blend, or effect is outside W4d")
        val fill = paint.style == PaintStyleNode.FILL
        if (fill && paint.pathEffect != null) return DrawScope.Gap("Path effects require a stroke in W4d")
        val stroke = paint.style == PaintStyleNode.STROKE || paint.style == PaintStyleNode.STROKE_AND_FILL
        if (!stroke) return DrawScope.Ready(path, clip, fill = true, stroke = false, mode = null, styleF64 = null)
        val mode = if (paint.style == PaintStyleNode.STROKE_AND_FILL) {
            PathStrokeDrawMode.StrokeAndFill
        } else {
            PathStrokeDrawMode.Stroke
        }
        val styleF64 = try {
            style(paint, mode)
        } catch (_: IllegalArgumentException) {
            return DrawScope.Invalid("Stroke style is invalid")
        }
        return DrawScope.Ready(path, clip, fill = false, stroke = true, mode, styleF64)
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

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this) return invalidCandidate()
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        if (extent.width > capabilities.maxTextureDimension2D || extent.height > capabilities.maxTextureDimension2D || FORMAT !in capabilities.supportedFormats() || !REQUIRED.all { it in capabilities.supportedOperations() } || capabilities.maxDynamicUniformBuffersPerPipelineLayout < 1 || !validAllocationFacts(capabilities)) return promoted("Required W4d device capability is unavailable")
        val usesStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover }
        if (usesStencil && (PlanDepthStencilFormat.Depth24PlusStencil8 !in capabilities.supportedDepthStencilFormats() || PlanOperationCapability.DepthStencilAttachment !in capabilities.supportedOperations() || PlanOperationCapability.StencilCover !in capabilities.supportedOperations())) return promoted("W4d stencil capability is unavailable")
        val memory = when (val value = PathStrokePlanBudget.calculate(extent, selected.draws.map { it.geometry }, capabilities, budget)) {
            is PathStrokePlanBudgetResult.WithinBudget -> value.footprint
            is PathStrokePlanBudgetResult.Exceeded -> return resource(W4dPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
            is PathStrokePlanBudgetResult.Invalid -> return resource("W4d size is unrepresentable: ${value.code}")
        }
        if (listOf(memory.readbackBytes, memory.vertexCapacityBytes, memory.indexCapacityBytes, memory.uniformCapacityBytes).any { it > capabilities.maxBufferSizeBytes }) return promoted("W4d buffer capability is unavailable")
        return try { graph(selected, capabilities, budget, memory, usesStencil) } catch (_: ArithmeticException) { resource("W4d arithmetic overflowed") } catch (_: IllegalArgumentException) { resource(W4dPlanDiagnostics.PlanIdentityInvalid, "W4d graph invariants failed") }
    }

    private fun graph(selected: Candidate, caps: PlanCapabilitySnapshot, budget: PlanBudget, memory: PathFillMemoryFootprint, usesStencil: Boolean): RenderPlanResult<RenderGraph> {
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
        val passes = mutableListOf<PlanPass>(); var render = 0; var producer = 0; var cover = 0; var clear = true
        selected.draws.forEach { sealed ->
            val draw: PathDraw = sealed.stroke?.let { PathStrokeDraw.of(sealed.commandIndex, sealed.color, it, sealed.scissor, requireNotNull(sealed.mode), requireNotNull(sealed.styleF64)) } ?: PathFillDraw.of(sealed.commandIndex, sealed.color, sealed.geometry, sealed.strategy, sealed.scissor)
            val load = if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load
            if (draw.strategy == PathFillStrategy.DirectTriangle) passes += PlanPass.RenderPass(render++, target.id, listOf(draw), load, AttachmentStorePlan.Store, data)
            else { val group = canonicalPathAtomicGroup(draw); val stencil = requireNotNull(depth); passes += PlanPass.StencilProducer(producer++, target.id, stencil.id, draw, data, group, load, AttachmentStorePlan.Store, PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore); passes += PlanPass.StencilCover(cover++, target.id, stencil.id, draw, data, group, AttachmentLoadPlan.Load, AttachmentStorePlan.Store, PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset) }
            clear = false
        }
        passes += PlanPass.ReadbackPass(0, target.id, staging.id, memory.readbackBytesPerRow)
        val graph = RenderGraph.of(PlanId(identity(selected, caps, budget)), CAPABILITY_ID, extent, FORMAT, caps, budget, selected.draws.size, buildList { add(target); add(staging); add(vertex); add(index); add(uniform); depth?.let(::add) }, passes, passes.zipWithNext().map { PlanPassDependency(it.first.id, it.second.id) }, memory.peakBytes)
        return RenderPlanResult.Ready(RenderGraph.issueW4dCompilerWitness(graph))
    }

    private fun solid(node: DrawNode, paint: PaintNode): Boolean = node.material is MaterialNode.Solid && effectsMatchPaintPathEffect(node.effects, paint.pathEffect) && node.resource == null && node.operationBlendMode == null && w4Blend(node.blend) && paint.shader == null && paint.blender == null && paint.colorFilter == null && paint.maskFilter == null && paint.imageFilter == null && paint.blendMode == BlendMode.SRC_OVER && (paint.pathEffect == null || paint.pathEffect is PathEffectNode.Dash)
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
    private fun w4Blend(blend: BlendNode): Boolean = when (blend) { BlendNode.SrcOver -> true; is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER; is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null; is BlendNode.Custom -> false }
    private fun linear(color: ColorARGB): ColorF32 { val a = color.alphaNormalized; return ColorF32.of(ColorTransferFunction.sRgb.toLinear(color.redNormalized) * a, ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * a, ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * a, a) }
    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(), capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes, capabilities.bufferAllocationPolicy.indexFloorBytes,
        capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { value -> value > 0L && value and (value - 1L) == 0L }
    private fun identity(selected: Candidate, caps: PlanCapabilitySnapshot, budget: PlanBudget): String { val d = MessageDigest.getInstance("SHA-256"); listOf("w4d-plan-v1", selected.sceneCanonicalId.value, selected.target.canonicalId.value, caps.deviceGeneration.toString(), caps.maxTextureDimension2D.toString(), caps.maxBufferSizeBytes.toString(), caps.copyBytesPerRowAlignment.toString(), caps.supportedFormats().map { it.name }.sorted().joinToString(","), caps.minUniformBufferOffsetAlignment.toString(), caps.maxDynamicUniformBuffersPerPipelineLayout.toString(), caps.supportedOperations().map { it.name }.sorted().joinToString(","), caps.bufferAllocationPolicy.vertexFloorBytes.toString(), caps.bufferAllocationPolicy.indexFloorBytes.toString(), caps.bufferAllocationPolicy.uniformFloorBytes.toString(), caps.bufferAllocationPolicy.growth.name, caps.supportedDepthStencilFormats().map { it.name }.sorted().joinToString(","), budget.maxFrameLocalBytes.toString(), strokePolicyF64.maximumSagittaErrorF64.toRawBits().toString(), strokePolicyF64.maximumDashArcLengthErrorF64.toRawBits().toString(), strokePolicyF64.limitsI32.maxSubdivisionDepthI32.toString(), strokePolicyF64.limitsI32.maxAttemptedGeometryUnitsPerPathI32.toString(), strokePolicyF64.limitsI32.maxAttemptedGeometryUnitsPerFrameI32.toString(), strokePolicyF64.limitsI32.maxEmittedVertexCountPerPathI32.toString(), strokePolicyF64.limitsI32.maxEmittedVertexCountPerFrameI32.toString(), strokePolicyF64.limitsI32.maxEmittedIndexCountPerPathI32.toString(), strokePolicyF64.limitsI32.maxEmittedIndexCountPerFrameI32.toString(), strokePolicyF64.limitsI64.maxSnapshotByteCountPerPathI64.toString(), strokePolicyF64.limitsI64.maxSnapshotByteCountPerFrameI64.toString()).forEach { value -> d.update(value.encodeToByteArray().size.toString().encodeToByteArray()); d.update(0); d.update(value.encodeToByteArray()); d.update(0) }; return d.digest().joinToString("") { "%02x".format(it) } }
    private fun gap(message: String) = GpuPlanSelection.NotCandidate(listOf(diag(W4dPlanDiagnostics.CommandNotMigrated, RenderDiagnosticDomain.SCENE, message)))
    private fun invalid(message: String) = GpuPlanSelection.InvalidScene(listOf(diag(W4dPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message)))
    private fun limitSelection(message: String) = GpuPlanSelection.ResourceLimitExceeded(listOf(diag(W4dPlanDiagnostics.PathResourceLimit, RenderDiagnosticDomain.RESOURCE, message)))
    private fun promoted(message: String): RenderPlanResult<Nothing> = RenderPlanResult.GapOnPromotedScope(listOf(diag(W4dPlanDiagnostics.CapabilityUnavailable, RenderDiagnosticDomain.CAPABILITY, message)))
    private fun resource(message: String): RenderPlanResult<Nothing> = resource(W4dPlanDiagnostics.SizeOverflow, message)
    private fun resource(code: RenderDiagnosticCode, message: String): RenderPlanResult<Nothing> = RenderPlanResult.ResourceLimitExceeded(listOf(diag(code, RenderDiagnosticDomain.RESOURCE, message)))
    private fun invalidCandidate(): RenderPlanResult<Nothing> = RenderPlanResult.InvalidScene(listOf(RenderDiagnostic(RenderDiagnosticCode("gpu-plan.selection.invalid-candidate"), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, "W4d candidate does not belong to this compiler.")))
    private fun diag(code: RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic = W4dPlanDiagnostics.diagnostic(code, domain, message)

    private sealed interface Recognition { data class Ready(val draws: List<SealedDraw>) : Recognition; data class Gap(val message: String) : Recognition; data class Invalid(val message: String) : Recognition; data class Limit(val message: String) : Recognition }
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
    private sealed interface DrawResult { data class Ready(val draw: SealedDraw, val frameWork: PathStrokeWorkUsageI64, val stroke: Boolean) : DrawResult; data class Empty(val frameWork: PathStrokeWorkUsageI64, val stroke: Boolean) : DrawResult; data class Gap(val message: String) : DrawResult; data class Invalid(val message: String) : DrawResult; data class Limit(val message: String) : DrawResult }
    private sealed interface Prepared { data class Ready(val geometry: org.graphiks.math.geometry.PathFillGeometryF32, val strokeGeometry: org.graphiks.math.geometry.PathStrokeGeometryF32?, val mode: PathStrokeDrawMode?, val styleF64: PathStrokeStyleF64?, val work: PathStrokeWorkUsageI64) : Prepared; data class Empty(val work: PathStrokeWorkUsageI64) : Prepared; data class Invalid(val message: String) : Prepared; data class Limit(val message: String) : Prepared }
    private data class SealedDraw(val commandIndex: Int, val color: ColorF32, val geometry: org.graphiks.math.geometry.PathFillGeometryF32, val stroke: org.graphiks.math.geometry.PathStrokeGeometryF32?, val mode: PathStrokeDrawMode?, val styleF64: PathStrokeStyleF64?, val scissor: RectI32) { val strategy: PathFillStrategy = if (geometry.copyDirectTriangleF32OrNull() != null) PathFillStrategy.DirectTriangle else PathFillStrategy.StencilCover }
    private class Candidate(val owner: W4dPathStrokePlanCompiler, override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId, override val target: RenderTargetDescriptor, draws: List<SealedDraw>) : GpuPlanCandidate { override val capabilityId: String = CAPABILITY_ID; val draws = Collections.unmodifiableList(draws.toList()) }

    public companion object { public const val CAPABILITY_ID: String = "solid-path-stroke-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1"; private val FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL; private val REQUIRED = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload, PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback); private const val MAX_DRAWS = 512 }
}
