package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CanonicalId

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
import org.graphiks.math.geometry.PathBuilder
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
import org.graphiks.math.geometry.toExactRectI32OrNull
import org.graphiks.math.geometry.rectHairlineCoverageBandsI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.PathProjectiveInvalidSceneReason
import org.graphiks.math.matrix.PathTransformClass
import org.graphiks.math.matrix.PathTransformedFillInvalidSceneReason
import org.graphiks.math.matrix.PathTransformedFillPreparationResult
import org.graphiks.math.matrix.classifyPathTransform
import org.graphiks.math.matrix.invertFiniteOrNull
import org.graphiks.math.matrix.preparePathFillGeometryF32
import org.graphiks.math.matrix.preparePathStrokeGeometryF32
import org.graphiks.math.matrix.toMatrix3x3F64

/** Exact W4d.2 resource lifetimes, represented without graph or GPU resource objects. */
internal class W4dGeneralFramePreview(
    extent: SizeI32,
    val passCount: Int,
    resources: List<FrameResourceSpan>,
    colorConsumerPassByCommand: Map<Int, Int>,
) {
    private val extentSnapshot: SizeI32 = extent.copy()
    val extent: SizeI32 get() = extentSnapshot.copy()
    val resources: List<FrameResourceSpan> = Collections.unmodifiableList(resources.toList())
    val colorConsumerPassByCommand: Map<Int, Int> = Collections.unmodifiableMap(colorConsumerPassByCommand.toMap())
}

/**
 * W4d.2 planning authority for bounded transformed and four-sample path frames.
 *
 * This compiler intentionally declines the historical hard identity and
 * axis-aligned cases, leaving their stable W4c/W4d routes untouched.
 */
public class W4dGeneralPathPlanCompiler internal constructor(
    private val strokePolicyF64: PathStrokePolicyF64,
    private val acceptsNarrowTransforms: Boolean = false,
    /** Root-only W7 extension: project Rect geometry locally without changing DrawNode provenance. */
    private val admitsStandaloneRectPathFrames: Boolean = false,
    /** W6-only source contract; the standalone W4d graph keeps its terminal readback. */
    private val allowAaColorSource: Boolean = false,
    /** Closed W7 root mixture source: only an AA Rect STROKE may use the Rect projection. */
    private val w6RootAaRectStrokeSource: Boolean = false,
    /** W4e may promote a mixed clip frame to its AA4 construction branch without rewriting draws. */
    private val forceAaFrame: Boolean = false,
    /** W4e inserts its clip consumers before promoting the shared material/resource graph. */
    private val retainGeometryConstructionGraph: Boolean = false,
    private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot = RuntimeEffectSemanticCatalogSnapshot.Unbound,
    private val imageProjection: ImageOriginGeometryProjectionV6? = null,
    /** Closed W6-only sibling of the colour source. It never changes colour-source admission. */
    private val w6AaCoverageSource: Boolean = false,
    /** W7 retains its selected material/blend and produces only opaque-white geometric coverage. */
    private val w7AaDeferredSource: Boolean = false,
    /** The public Rect projection must not become an implicit encoded-composition entry. */
    private val requiresPublicEncodedAdmission: Boolean = false,
    /** Closed W6 Picture authority, distinct from standalone, AA and hairline switches. */
    private val rectProjectionMode: RectProjectionMode = RectProjectionMode.None,
    /** W4e-only construction permission: retain already-normalized plain solid AA material. */
    private val resolvePlainAaSolids: Boolean = false,
) : GpuPlanCompiler {
    internal enum class RectProjectionMode { None, PictureHardFill }
    internal fun withRuntimeCatalog(catalog: RuntimeEffectSemanticCatalogSnapshot): W4dGeneralPathPlanCompiler =
        W4dGeneralPathPlanCompiler(strokePolicyF64, acceptsNarrowTransforms, admitsStandaloneRectPathFrames, allowAaColorSource, w6RootAaRectStrokeSource, forceAaFrame, retainGeometryConstructionGraph, catalog,imageProjection,w6AaCoverageSource,w7AaDeferredSource,requiresPublicEncodedAdmission,rectProjectionMode,resolvePlainAaSolids)
    internal fun withImageOriginProjection(projection: ImageOriginGeometryProjectionV6?): W4dGeneralPathPlanCompiler =
        W4dGeneralPathPlanCompiler(strokePolicyF64,acceptsNarrowTransforms,admitsStandaloneRectPathFrames,allowAaColorSource,w6RootAaRectStrokeSource,forceAaFrame,retainGeometryConstructionGraph,runtimeCatalog,projection,w6AaCoverageSource,w7AaDeferredSource,requiresPublicEncodedAdmission,rectProjectionMode,resolvePlainAaSolids)
    public constructor() : this(PathStrokePolicyF64(), requiresPublicEncodedAdmission = true)

    /**
     * Computes the complete W4d.2 physical lifetime inventory without issuing a RenderGraph or
     * PlanResource.  W4e composes this with its clip prefix before asking the seam to emit.
     */
    internal fun preflightFrame(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        scanSpanProducerCommandIndexes: Set<Int> = emptySet(),
    ): RenderPlanResult<W4dGeneralFramePreview> {
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.hasMatchingFingerprints()) return invalidCandidate()
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        val colorFormat = logicalColorFormat(selected.target)
        if (!coreCapabilities(capabilities, extent, colorFormat)) return promoted("Required W4d.2 device capability is unavailable")
        val anyAa = forceAaFrame || selected.requestedAa
        val anyHard = selected.draws.any { !it.requestsAntiAlias }
        val aaStencil = selected.draws.any { it.requestsAntiAlias && it.strategy == PathFillStrategy.StencilCover }
        val hardStencil = selected.draws.any { !it.requestsAntiAlias && it.strategy == PathFillStrategy.StencilCover }
        val aaDepthStencil = requiresAaDepthStencil(anyAa, aaStencil)
        textureRefusal(capabilities, colorFormat, anyAa, anyHard, aaDepthStencil, hardStencil)?.let { return it }
        if ((aaDepthStencil || hardStencil) && PlanOperationCapability.DepthStencilAttachment !in capabilities.supportedOperations()) {
            return promoted("W4d.2 depth-stencil capability is unavailable")
        }
        if ((aaStencil || hardStencil) && PlanOperationCapability.StencilCover !in capabilities.supportedOperations()) {
            return promoted("W4d.2 stencil cover capability is unavailable")
        }
        val geometry = selected.draws.map { it.fillGeometry() }
        return try {
            if (anyAa) preflightAa(selected, capabilities, budget, geometry, anyHard, hardStencil, scanSpanProducerCommandIndexes)
            else preflightHard(selected, capabilities, budget, geometry, hardStencil, scanSpanProducerCommandIndexes)
        } catch (_: ArithmeticException) {
            resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 arithmetic overflowed")
        } catch (_: IllegalArgumentException) {
            resource(W4dGeneralPlanDiagnostics.PlanIdentityInvalid, "W4d.2 frame preflight failed")
        }
    }

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) return invalid("Scene and target differ")
        if (SceneSemanticValidator.validate(scene) is SceneSemanticValidationResult.Invalid) return invalid("Scene validation failed")
        if (scene.colorSpace != ColorSpace.SRGB) return gap("W4d.2 supports only sRGB")
        val publicEncodedRootAaPathFrame = isPublicEncodedRootAaPathFrame(scene, target)
        if (requiresPublicEncodedAdmission) CompositionAdmissionV1.validate(scene, target).firstOrNull()?.let { diagnostic ->
            return GpuPlanSelection.InvalidScene(listOf(diagnostic))
        }
        when (val preflight = preflight(scene, target)) {
            Preflight.Member -> Unit
            Preflight.Outside -> return gap("Scene is outside W4d.2")
            is Preflight.Invalid -> return invalid(preflight.message)
            is Preflight.Limit -> return limit(preflight.message)
        }
        if ((forceAaFrame || scene.any { it is SceneCommand.Draw && it.node.coverage == CoverageRequest.ANTIALIASED }) &&
            target.compositionDomain == org.graphiks.kanvas.render.ir.CompositionDomain.SRGB_ENCODED &&
            !publicEncodedRootAaPathFrame
        ) return gap("W4d.2 AA supports only LINEAR composition")
        return when (val recognized = recognize(scene, target)) {
            is Recognition.MaterialRefused -> GpuPlanSelection.MaterialOnlyRefusal(
                if (forceAaFrame || scene.any { it is SceneCommand.Draw && it.node.coverage == CoverageRequest.ANTIALIASED }) W5A_AA_CAPABILITY_ID else W5A_HARD_CAPABILITY_ID,
                scene.canonicalId, target, recognized.refusals,
            )
            is Recognition.Ready -> if ((allowAaColorSource || w7AaDeferredSource) && (recognized.elidedNoOpsI32 != 0 ||
                recognized.draws.size != 1 || recognized.draws.any {
                    !it.requestsAntiAlias || (!w7AaDeferredSource && it.blend != BlendPlan.SrcOver)
                })) gap("W6 AA colour source admits one solid SrcOver child")
            else GpuPlanSelection.Candidate(Candidate(this, scene.canonicalId, target, recognized.draws, recognized.materialPlanTable, recognized.elidedNoOpsI32, recognized.requestedAa,recognized.sources))
            is Recognition.Gap -> gap(recognized.message)
            is Recognition.Invalid -> invalid(recognized.message)
            is Recognition.Horizon -> horizon(recognized.message)
            is Recognition.Limit -> limit(recognized.message)
        }
    }

    /** Numeric validation shared with W4e before a semantically valid NoOp is elided. */
    internal fun finiteSceneError(scene: SceneSnapshot): String? {
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    val path = (command.node.geometry as? GeometryNode.Path)?.path
                    val rect = (command.node.geometry as? GeometryNode.Rect)?.copyBounds()
                    val paint = command.node.paint
                    if (!finite(command.node.transform) || !finite(command.node.effects) || !finiteClip(command.node.clip) ||
                        (path != null && !finite(path)) ||
                        ((admitsStandaloneRectPathFrames || rectProjectionMode == RectProjectionMode.PictureHardFill || w7DeferredRectProjection(command.node)) && rect != null && !finite(rect)) ||
                        (paint != null && !finite(paint))
                    ) return "Draw facts are non-finite"
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return "Transform metadata is non-finite"
                is SceneCommand.SetClip -> if (!finiteClip(command.clip)) return "Clip metadata is non-finite"
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return "Annotation bounds are non-finite"
                else -> Unit
            }
        }

        return null
    }

    /** Establishes ownership before spending any shared :math work ledger. */
    private fun preflight(scene: SceneSnapshot, target: RenderTargetDescriptor): Preflight {
        finiteSceneError(scene)?.let { return Preflight.Invalid(it) }
        var visualDrawCountI32 = 0
        var requiresGeneral = false
        var requiresStandaloneRectRouting = false
        var containsRectProjection = false
        var standaloneFrameFacts = true
        var outside = false
        scene.forEach { command ->
            when (command) {
                is SceneCommand.Draw -> {
                    visualDrawCountI32 = Math.addExact(visualDrawCountI32, 1)
                    when (val scope = classifyDrawScope(command.node)) {
                        is DrawScope.Ready -> {
                            requiresGeneral = requiresGeneral ||
                                scope.transformClass == PathTransformClass.GeneralAffine ||
                                scope.transformClass == PathTransformClass.Perspective
                            requiresStandaloneRectRouting = requiresStandaloneRectRouting ||
                                (scope.rectProjection && scope.mode != null) || scope.requestsAntiAlias ||
                                scope.styleF64?.widthF64 == PathStrokeWidthF64.Hairline
                            containsRectProjection = containsRectProjection || scope.rectProjection
                            standaloneFrameFacts = standaloneFrameFacts && standaloneFrameDraw(node = command.node)
                        }
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
        val historicalMember = !containsRectProjection && (requiresGeneral || acceptsNarrowTransforms)
        val w7Frame = w7AaDeferredSource && scene.all { command ->
            command !is SceneCommand.Draw || command.node.geometry !is GeometryNode.Rect || w7DeferredRectProjection(command.node)
        }
        val pictureHardFrame = rectProjectionMode == RectProjectionMode.PictureHardFill &&
            scene.filterIsInstance<SceneCommand.Draw>().all { acceptsPictureHardRect(it.node) }
        val publicEncodedRootAaPathFrame = isPublicEncodedRootAaPathFrame(scene, target)
        val standaloneMember = (admitsStandaloneRectPathFrames || w6RootAaRectStrokeSource || w7Frame || pictureHardFrame ||
            publicEncodedRootAaPathFrame) &&
            (w7Frame || pictureHardFrame || standaloneFrameFacts) &&
            (requiresGeneral || requiresStandaloneRectRouting)
        if (outside || !(historicalMember || standaloneMember)) {
            return Preflight.Outside
        }
        return if (visualDrawCountI32 > MAX_DRAWS) Preflight.Limit("W4d.2 accepts at most 512 visual path draws") else Preflight.Member
    }

    private fun isPublicEncodedRootAaPathFrame(scene: SceneSnapshot, target: RenderTargetDescriptor): Boolean =
        requiresPublicEncodedAdmission &&
            (admitsStandaloneRectPathFrames || scene.none { command ->
                command is SceneCommand.Draw && command.node.origin == DrawOrigin.RECT &&
                    command.node.geometry is GeometryNode.Rect
            }) &&
            CompositionAdmissionV1.isAdmittedEncodedRootAaPathFrame(scene, target)

    private fun recognize(scene: SceneSnapshot, target: RenderTargetDescriptor): Recognition {
        val targetBounds = RectI32(0, 0, scene.extent.width, scene.extent.height)
        // The standalone AA extension and W4e's private construction seam both prove a
        // whole frame of plain solid SrcOver draws. Normalize those solids directly while
        // retaining their original draw/source authority; a deferred composed stroke source
        // would select W5b's single-sample source topology instead of this compiler's closed
        // AA frame.
        val normalizedDraws = scene.filterIsInstance<SceneCommand.Draw>()
        val resolvesPlainAaFrame = (forceAaFrame || normalizedDraws.any {
            it.node.coverage == CoverageRequest.ANTIALIASED
        }) && normalizedDraws.all { standaloneFrameDraw(it.node) }
        val standaloneAaSolids = (admitsStandaloneRectPathFrames || w6RootAaRectStrokeSource ||
            resolvePlainAaSolids || isPublicEncodedRootAaPathFrame(scene, target)) && resolvesPlainAaFrame
        val draws = mutableListOf<SealedDraw>()
        val materialEntries = mutableListOf<MaterialPlanEntry>()
        val sources = mutableListOf<MaterialSourceConstructionV4>()
        val materialRefusals = mutableListOf<EffectiveMaterialPlanner.Result.Refused>()
        var visualDrawCountI32 = 0
        var elidedNoOpsI32 = 0
        var emptyImageCandidatesI32 = 0
        var requestedAa = false
        var frameWorkUsageI64 = PathStrokeWorkUsageI64()
        scene.withIndex().forEach { (commandIndex, command) ->
            when (command) {
                is SceneCommand.Draw -> {
                    if (visualDrawCountI32 >= MAX_DRAWS) return Recognition.Limit("W4d.2 accepts at most 512 visual path draws")
                    visualDrawCountI32 = Math.addExact(visualDrawCountI32, 1)
                    requestedAa = requestedAa || command.node.coverage == CoverageRequest.ANTIALIASED
                    when (val result = recognizeDraw(command.node, commandIndex, targetBounds, frameWorkUsageI64, materialEntries,sources,standaloneAaSolids, target)) {
                        is DrawResult.MaterialRefused -> { materialRefusals += result.refusal; frameWorkUsageI64 = result.frameWorkUsageI64 }
                        is DrawResult.Ready -> {
                            draws += result.draw
                            frameWorkUsageI64 = result.frameWorkUsageI64
                        }
                        is DrawResult.NoOp -> { elidedNoOpsI32++; frameWorkUsageI64 = result.frameWorkUsageI64 }
                        is DrawResult.Empty -> {
                            if (imageProjection?.owns(commandIndex,command.node) == true) emptyImageCandidatesI32++
                            if (result.elidedAdmittedRectHairline) elidedNoOpsI32++
                            frameWorkUsageI64 = result.frameWorkUsageI64
                        }
                        is DrawResult.Gap -> return Recognition.Gap(result.message)
                        is DrawResult.Invalid -> return Recognition.Invalid(result.message)
                        is DrawResult.Horizon -> return Recognition.Horizon(result.message)
                        is DrawResult.Limit -> return Recognition.Limit(
                            "commandIndexI32=$commandIndex; ${result.message}",
                        )
                    }
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return Recognition.Invalid("Transform metadata is non-finite")
                is SceneCommand.SetClip -> if (!finiteClip(command.clip)) return Recognition.Invalid("Clip metadata is non-finite")
                is SceneCommand.Annotation -> if (!finite(command.copyBounds())) return Recognition.Invalid("Annotation bounds are non-finite")
                else -> return Recognition.Gap("Scene command is outside W4d.2")
            }
        }
        if (materialRefusals.isNotEmpty()) return Recognition.MaterialRefused(materialRefusals)
        return if (draws.isEmpty() && elidedNoOpsI32 == 0 && emptyImageCandidatesI32 == 0)
            Recognition.Limit("W4d.2 retained no visible prepared geometry")
        else {
            val pending = sources.any { it.pending }
            Recognition.Ready(if (pending) draws.mapIndexed { ordinal,draw -> draw.copy(material=MaterialPlanRef(ordinal)) } else draws,
                materialEntries.takeIf { it.isNotEmpty() && !pending }?.let(MaterialPlanTable::of),elidedNoOpsI32,requestedAa,
                (MaterialSourceConstructionTableV4.of(sources) as SourceConstructionResultV4.Built).value)
        }
    }

    private fun recognizeDraw(
        node: DrawNode,
        commandIndex: Int,
        targetBounds: RectI32,
        frameWorkUsageI64: PathStrokeWorkUsageI64,
        materialEntries: MutableList<MaterialPlanEntry>,
        sources: MutableList<MaterialSourceConstructionV4>,
        standaloneAaSolids: Boolean,
        target: RenderTargetDescriptor,
    ): DrawResult {
        val scope = when (val classified = classifyDrawScope(node)) {
            is DrawScope.Ready -> classified
            is DrawScope.Gap -> return DrawResult.Gap(classified.message)
            is DrawScope.Invalid -> return DrawResult.Invalid(classified.message)
        }
        return when (val prepared = prepare(scope, frameWorkUsageI64, targetBounds)) {
            is Prepared.Ready -> {
                // A W6 coverage source is localized only after W6b has frozen its raw demand.
                // In particular, an offscreen edge may blur into a terminal clip; pruning it here
                // would erase that input before the filter has a chance to produce its halo.
                val targetScissor = if (w6AaCoverageSource) prepared.geometry.copyConservativeScissorI32() else
                    intersect(prepared.geometry.copyConservativeScissorI32(), targetBounds)
                        ?: return DrawResult.Empty(prepared.frameWorkUsageI64, scope.rectHairlineDeviceRectI32 != null)
                val scissor = if (w6AaCoverageSource) targetScissor else
                    scope.clip?.let { intersect(targetScissor, it) } ?: targetScissor
                if (scissor.isEmpty) return DrawResult.Empty(prepared.frameWorkUsageI64, scope.rectHairlineDeviceRectI32 != null)
                if (prepared.geometry.fillRule == FillRule.WINDING &&
                    prepared.geometry.copyStencilEdgeFanF32OrNull() != null &&
                    prepared.geometry.emittedNonZeroClosedEdgeCountI32 > UByte.MAX_VALUE.toInt()
                ) return DrawResult.Limit("W4d.2 winding path exceeds the stencil edge limit")
                val colorFormat = logicalColorFormat(target)
                val normalizedSource = if (standaloneAaSolids) {
                    when (val normalized = EffectiveMaterialPlanner.normalize(
                        node,
                        colorFormat.blendTargetClampV1(),
                        compositionDomain = target.compositionDomain,
                    )) {
                        EffectiveMaterialPlanner.Normalization.NoOp -> EffectiveMaterialPlanner.SourceNormalizationV4.NoOp
                        is EffectiveMaterialPlanner.Normalization.Refused ->
                            EffectiveMaterialPlanner.SourceNormalizationV4.Refused(normalized.diagnosticCode)
                        is EffectiveMaterialPlanner.Normalization.Source -> EffectiveMaterialPlanner.SourceNormalizationV4.Source(
                            MaterialSourceConstructionV4.retain(node,
                                EffectiveMaterialPlanner.Result.Ready(normalized.table, normalized.root, normalized.blend),
                                RectF32.ofLTRB(scissor.left.toFloat(), scissor.top.toFloat(), scissor.right.toFloat(), scissor.bottom.toFloat())),
                        )
                    }
                } else EffectiveMaterialPlanner.normalizeSourcesV4(
                    if (node.paint?.colorFilter == null) node.copy(effects = EffectStack.Empty) else node,
                    colorFormat.blendTargetClampV1(),scissor,runtimeCatalog=runtimeCatalog,
                    compositionDomain=target.compositionDomain)
                val source = when (val planned = normalizedSource) {
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Refused -> return DrawResult.MaterialRefused(
                        EffectiveMaterialPlanner.Result.Refused(planned.diagnosticCode), prepared.frameWorkUsageI64)
                    EffectiveMaterialPlanner.SourceNormalizationV4.NoOp -> return DrawResult.NoOp(prepared.frameWorkUsageI64)
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Source -> planned.captured
                }
                sources += source
                val resolved = source.resolvedSource
                val material = if (resolved == null) MaterialPlanRef(sources.lastIndex)
                    else appendMaterialPlan(materialEntries,resolved.table,resolved.root)
                DrawResult.Ready(
                    SealedDraw(
                        commandIndex = commandIndex,
                        material = material,
                        coordinates = MaterialCoordinatePlanV1.fromCtm(node.transform),
                        coordinatesV2 = resolved?.table?.coordinatesV2(resolved.root),
                        coordinatesV4 = if (source.pending) source.coordinates else resolved?.table?.coordinatesV4(resolved.root),
                        geometry = prepared.pathGeometry,
                        strategy = strategy(prepared.geometry),
                        scissorI32 = scissor.copy(),
                        requestsAntiAlias = scope.requestsAntiAlias,
                        blend = source.blend.takeUnless { it is BlendPlan.FixedFunctionV1 && it.mode == BlendMode.SRC_OVER } ?: BlendPlan.SrcOver,
                    ),
                    prepared.frameWorkUsageI64,
                )
            }
            is Prepared.Empty -> DrawResult.Empty(prepared.frameWorkUsageI64, scope.rectHairlineDeviceRectI32 != null)
            is Prepared.Invalid -> DrawResult.Invalid(prepared.message)
            is Prepared.Horizon -> DrawResult.Horizon(prepared.message)
            is Prepared.Limit -> DrawResult.Limit(prepared.message)
        }
    }

    private fun prepare(
        scope: DrawScope.Ready,
        frameWorkUsageI64: PathStrokeWorkUsageI64,
        targetBounds: RectI32,
    ): Prepared {
        val rectHairlineDeviceRectI32 = scope.rectHairlineDeviceRectI32
        if (rectHairlineDeviceRectI32 != null) {
            val effectiveClipI32 = scope.clip?.let { intersect(targetBounds, it) }
                ?: if (scope.clip == null) targetBounds.copy() else return Prepared.Empty(frameWorkUsageI64)
            val bandsI32 = rectHairlineCoverageBandsI32(rectHairlineDeviceRectI32, effectiveClipI32)
            if (bandsI32.isEmpty()) return Prepared.Empty(frameWorkUsageI64)
            val coveragePath = PathBuilder(FillRule.WINDING).apply {
                bandsI32.forEach { bandI32 ->
                    addRect(RectF32.ofLTRB(
                        bandI32.left.toFloat(), bandI32.top.toFloat(),
                        bandI32.right.toFloat(), bandI32.bottom.toFloat(),
                    ))
                }
            }.build()
            return when (val result = Matrix3x3F64().preparePathFillGeometryF32(
                path = coveragePath,
                strokePolicyF64 = strokePolicyF64,
                frameWorkUsageBeforeI64 = frameWorkUsageI64,
            )) {
                is PathTransformedFillPreparationResult.Ready -> Prepared.Ready(
                    result.geometryF32,
                    PathDrawGeometry.Fill(result.geometryF32),
                    result.frameWorkUsageAfterI64,
                )
                is PathTransformedFillPreparationResult.Empty -> Prepared.Empty(result.frameWorkUsageAfterI64)
                is PathTransformedFillPreparationResult.InvalidScene -> Prepared.Invalid(
                    "Math rejected Rect hairline scene: ${result.reason}",
                )
                is PathTransformedFillPreparationResult.ResourceLimitExceeded ->
                    Prepared.Limit("Math Rect hairline limit: ${result.reason}")
            }
        }
        return if (scope.fill) {
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
    }

    /** Semantic source scope only; selection additionally proves the prepared direct strategy. */
    internal fun acceptsW6AaColorSourceScope(node: DrawNode): Boolean =
        allowAaColorSource && classifyDrawScope(node) is DrawScope.Ready

    /** Closed W7 predicate shared by root and layer ownership; it does not select a backend. */
    internal fun acceptsW7AaDeferredSourceScope(node: DrawNode): Boolean =
        w7AaDeferredSource && classifyDrawScope(node) is DrawScope.Ready

    /** Closed W7 admission: keep the original RECT/STROKE provenance and style intact. */
    internal fun acceptsW6RootAaRectStrokeScope(node: DrawNode): Boolean =
        w6RootAaRectStrokeSource && node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect &&
            node.paint?.style == PaintStyleNode.STROKE &&
            classifyDrawScope(node) is DrawScope.Ready

    private fun classifyDrawScope(node: DrawNode): DrawScope {
        val paint = node.paint ?: return DrawScope.Gap("W4d.2 requires paint")
        val rect = (node.geometry as? GeometryNode.Rect)?.copyBounds()
        // Empty/inverted rectangles stay outside this opt-in projection so their existing
        // route retains ownership of their observable semantics.
        val rectProjection = (admitsStandaloneRectPathFrames || w6RootAaRectStrokeSource ||
            rectProjectionMode == RectProjectionMode.PictureHardFill || w7DeferredRectProjection(node)) && rect?.isEmpty == false
        val path = (node.geometry as? GeometryNode.Path)?.path ?: if (rectProjection) {
            if (!finite(requireNotNull(rect))) return DrawScope.Invalid("Draw facts are non-finite")
            PathBuilder(FillRule.WINDING).addRect(requireNotNull(rect)).build()
        } else return DrawScope.Gap("Draw geometry is outside W4d.2")
        val transform = node.transform
        if (!finite(path) || !finite(transform) || !finite(paint) || !finite(node.effects) || !finiteClip(node.clip)) {
            return DrawScope.Invalid("Draw facts are non-finite")
        }
        if ((rectProjection && node.origin != DrawOrigin.RECT) || (!rectProjection && node.origin != DrawOrigin.PATH) ||
            path.fillRule !in setOf(FillRule.WINDING, FillRule.EVEN_ODD)
        ) {
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
        if (!(if (w7AaDeferredSource) deferredAaMaterial(node, paint) else solid(node, paint)))
            return DrawScope.Gap("Material, blend, or effect is outside W4d.2")
        val rootRectStroke = w6RootAaRectStrokeSource && rectProjection
        if (allowAaColorSource && !rootRectStroke &&
            (node.coverage != CoverageRequest.ANTIALIASED || node.material !is MaterialNode.Solid || paint.colorFilter != null ||
                paint.style != PaintStyleNode.FILL || paint.pathEffect != null ||
                node.effects != EffectStack.Empty || when (val blend = node.blend) {
                    BlendNode.SrcOver -> false
                    is BlendNode.Mode -> blend.mode != BlendMode.SRC_OVER
                    is BlendNode.Paint -> blend.mode != BlendMode.SRC_OVER || blend.blender != null
                    is BlendNode.Custom -> true
                })) {
            return DrawScope.Gap("W6 AA colour source requires an unfiltered solid SrcOver fill")
        }
        if (w7AaDeferredSource &&
            (node.coverage != CoverageRequest.ANTIALIASED || paint.style != PaintStyleNode.FILL || !w7PorterDuff(node.blend))) {
            return DrawScope.Gap("W7 deferred AA source requires an admitted Porter-Duff fill")
        }
        if (rectProjectionMode == RectProjectionMode.PictureHardFill && !acceptsPictureHardRect(node))
            return DrawScope.Gap("W6 hard Picture source requires a general hard solid Rect fill")
        val fill = paint.style == PaintStyleNode.FILL
        val stroke = paint.style == PaintStyleNode.STROKE || paint.style == PaintStyleNode.STROKE_AND_FILL
        if (fill && paint.pathEffect != null) return DrawScope.Gap("Path effects require a stroke in W4d.2")
        val mode = if (stroke) if (paint.style == PaintStyleNode.STROKE_AND_FILL) {
            PathStrokeDrawMode.StrokeAndFill
        } else {
            PathStrokeDrawMode.Stroke
        } else null
        val style = if (mode != null) try {
            w4PathStrokeStyleF64(paint, mode)
        } catch (_: IllegalArgumentException) {
            return DrawScope.Invalid("Stroke style is invalid")
        } else null
        val matrixF64 = transform.toMatrix3x3F64()
        if (w7AaDeferredSource &&
            (matrixF64.classifyPathTransform() == PathTransformClass.Perspective || matrixF64.invertFiniteOrNull() == null)) {
            return DrawScope.Gap("W7 deferred AA source requires a finite non-singular affine transform")
        }
        if (rootRectStroke && !(node.coverage == CoverageRequest.ANTIALIASED && node.material is MaterialNode.Solid &&
                paint.shader == null && paint.colorFilter == null && paint.style == PaintStyleNode.STROKE && paint.pathEffect == null &&
                node.effects == EffectStack.Empty && paint.strokeJoin == StrokeJoinNode.MITER && paint.strokeMiter.isFinite() &&
                paint.strokeMiter >= 2f && matrixF64.classifyPathTransform() in setOf(PathTransformClass.Identity, PathTransformClass.AxisAlignedAffine) &&
                matrixF64.sxF64 != 0.0 && matrixF64.syF64 != 0.0 && when (val blend = node.blend) {
                    BlendNode.SrcOver -> true
                    is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER
                    is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null
                    is BlendNode.Custom -> false
                }
            )) return DrawScope.Gap("W7 root AA Rect source requires a solid SrcOver MITER stroke")
        val rectHairlineDeviceRectI32 = if (admitsStandaloneRectPathFrames && rectProjection &&
            node.coverage == CoverageRequest.HARD_EDGE && paint.style == PaintStyleNode.STROKE &&
            paint.strokeWidth == 0f && node.material is MaterialNode.Solid && paint.shader == null &&
            paint.colorFilter == null && paint.pathEffect == null && node.effects == EffectStack.Empty &&
            paint.strokeCap == StrokeCapNode.BUTT && paint.strokeJoin == StrokeJoinNode.MITER &&
            paint.strokeMiter.isFinite() && paint.strokeMiter >= 2f &&
            matrixF64.classifyPathTransform() in setOf(PathTransformClass.Identity, PathTransformClass.AxisAlignedAffine) &&
            matrixF64.sxF64 != 0.0 && matrixF64.syF64 != 0.0 && srcOver(node.blend)
        ) projectedIntegralRectI32(requireNotNull(rect), matrixF64) else null
        return DrawScope.Ready(
            path = path,
            matrixF64 = matrixF64,
            transformClass = matrixF64.classifyPathTransform(),
            clip = clip,
            fill = fill,
            mode = mode,
            styleF64 = style,
            requestsAntiAlias = node.coverage == CoverageRequest.ANTIALIASED,
            rectProjection = rectProjection,
            rectHairlineDeviceRectI32 = rectHairlineDeviceRectI32,
        )
    }

    override fun plan(candidate: GpuPlanCandidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget): RenderPlanResult<RenderGraph> =
        if (hasPendingSources(candidate)) constructSources(candidate,capabilities,budget).prepareAndPublishSourcesV4()
        else construct(candidate,capabilities,budget).publishConstructionResult()

    internal fun hasPendingSources(candidate: GpuPlanCandidate): Boolean =
        (candidate as? Candidate)?.sources?.sources()?.any { it.pending } == true

    internal fun construct(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraphConstruction> = constructChecked(candidate,capabilities,budget) { selected,geometry,anyAa,anyHard,hardStencil ->
        require(!hasPendingSources(selected)) { W5fPlanDiagnostics.Schema }
        val successor = selected.elidedNoOpsI32 > 0 || selected.draws.any { it.blend != BlendPlan.SrcOver }
        if (anyAa && successor) promoted("W5b final blending requires the admitted single-sample W4d.2 topology")
        else if (!anyAa && selected.draws.isEmpty()) RenderPlanResult.Ready(RenderGraph.issueW5bGeometry(
            W5bGeometryLanePlanV3.constructClearOnly(PlanId(identity(selected,capabilities,budget,W5B_HARD_CAPABILITY_ID)),
                W5B_HARD_CAPABILITY_ID,SizeI32(selected.target.extent.width,selected.target.extent.height),capabilities,budget,null,
                logicalColorFormat(selected.target))))
        else if (anyAa) planAa(selected,capabilities,budget,geometry,anyHard,hardStencil)
        else planHard(selected,capabilities,budget,geometry,hardStencil)
    }

    internal fun constructSources(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget): RenderPlanResult<SourceDeferredRenderConstructionV4> =
        constructChecked(candidate,capabilities,budget) { selected,geometry,anyAa,_,hardStencil ->
            if ((allowAaColorSource || w7AaDeferredSource) && !anyAa) promoted("W6 AA colour source cannot construct a hard path source")
            else if (anyAa && !allowAaColorSource && !w7AaDeferredSource) promoted("W5b final blending requires the admitted single-sample W4d.2 topology")
            else if (anyAa && (selected.draws.any { !it.requestsAntiAlias } || selected.draws.any {
                    !w7AaDeferredSource && it.blend != BlendPlan.SrcOver
                })) promoted("W6 AA colour source admits only solid SrcOver paths")
            else if (anyAa) sourceAa(selected, capabilities, budget, geometry)
            else if (selected.draws.isEmpty()) SourceDeferredRenderConstructionV4.clearOnly(
                PlanId(identity(selected,capabilities,budget,W5B_HARD_CAPABILITY_ID)),W5B_HARD_CAPABILITY_ID,
                SizeI32(selected.target.extent.width,selected.target.extent.height),capabilities,budget,
                preparedIdentity = { scene, _, _ -> PlanId(identity(selected, capabilities, budget, W5B_HARD_CAPABILITY_ID, scene)) },
                colorFormat = logicalColorFormat(selected.target),
            )
            else withHardMemory(selected,capabilities,budget,geometry) { memory ->
                val topology = hardSourceTopology(selected,memory,hardStencil)
                val refs = selected.draws.map { it.material }
                val symbolic = remapSourcePassesV4(topology.passes) { ref ->
                    MaterialPlanRef(refs.indexOf(ref).also { require(it >= 0) }) }
                val source = SourceDeferredRenderConstructionV4.of(
                    PlanId(identity(selected,capabilities,budget,W5A_HARD_CAPABILITY_ID)),W5A_HARD_CAPABILITY_ID,
                    SizeI32(selected.target.extent.width,selected.target.extent.height),logicalColorFormat(selected.target),capabilities,budget,selected.draws.size,
                    topology.resources,symbolic,topology.dependencies,selected.sources,DeferredLaneTopologyV4.Ordinary,
                    null,emptyList(),emptyMap(),emptyMap(),
                preparedIdentity = { scene, _, _ -> PlanId(identity(selected, capabilities, budget, W5A_HARD_CAPABILITY_ID, scene)) })
                when (source) {
                    is SourceConstructionResultV4.Refused -> source.failure
                    is SourceConstructionResultV4.Built -> {
                        // Original capture decides the colour envelope. No table/slab inspection or geometry witness exists yet.
                        val successor = selected.elidedNoOpsI32 > 0 || !retainGeometryConstructionGraph &&
                            selected.sources.sources().any { it.hasGradientStorage } || selected.draws.any { it.blend != BlendPlan.SrcOver }
                        if (!successor) RenderPlanResult.Ready(source.value)
                        else when (val envelope = describeW5bGeneralPathSourcesV4(source.value,
                            selected.draws.associate { it.commandIndex to it.blend })) {
                            is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(envelope.value)
                            is SourceConstructionResultV4.Refused -> envelope.failure
                        }
                    }
                }
            }
        }

    /**
     * W6 owns the final destination and readback.  This source publishes the same W4d AA
     * geometry/pass authority as [aaGraph], but resolves only to its isolated sampled colour.
     */
    private fun sourceAa(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
    ): RenderPlanResult<SourceDeferredRenderConstructionV4> {
        val provisional = when (val result = PathAaPlanBudget.calculate(
            targetExtent = SizeI32(selected.target.extent.width, selected.target.extent.height),
            geometriesF32 = geometries,
            requiresAa4DepthStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover },
            requiresHardMask = false,
            requiresHardEdgeDepthStencil = false,
            capabilities = capabilities,
            budget = budget,
        )) {
            is PathAaPlanBudgetResult.WithinBudget -> result.footprint
            is PathAaPlanBudgetResult.Exceeded -> return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
            is PathAaPlanBudgetResult.Invalid -> return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 size is unrepresentable: ${result.code}")
        }
        val base = w4dGeneralExactFrameMemory(
            provisional.base,
            w4dGeneralNativePayloadBudget(selected.draws, materializesHardMasks = false),
            capabilities,
        ) ?: return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 native frame resources overflow")
        if (!buffersFit(base, capabilities)) return promoted("W4d.2 buffer capability is unavailable")
        val topology = aaResolvedColorTopology(selected, base, provisional, w6AaCoverageSource || w7AaDeferredSource)
        val symbolic = remapSourcePassesV4(topology.passes) { reference ->
            MaterialPlanRef(selected.draws.map(SealedDraw::material).indexOf(reference).also { require(it >= 0) })
        }
        return when (val source = SourceDeferredRenderConstructionV4.of(
            PlanId(identity(selected, capabilities, budget, w6AaSourceCapabilityId())),
            w6AaSourceCapabilityId(),
            SizeI32(selected.target.extent.width, selected.target.extent.height), AA_FORMAT, capabilities, budget,
            selected.draws.size, topology.resources, symbolic, topology.dependencies, selected.sources,
            if (w6AaCoverageSource || w7AaDeferredSource) DeferredLaneTopologyV4.AaResolvedCoverage else DeferredLaneTopologyV4.AaResolvedColor,
            null, emptyList(), emptyMap(), emptyMap(),
            preparedIdentity = { scene, _, _ -> PlanId(identity(selected, capabilities, budget, w6AaSourceCapabilityId(), scene)) },
        )) {
            is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(source.value)
            is SourceConstructionResultV4.Refused -> source.failure
        }
    }

    private data class AaResolvedColorTopology(
        val resources: List<PlanResource>,
        val passes: List<PlanPass>,
        val dependencies: List<PlanPassDependency>,
    )

    private fun aaResolvedColorTopology(
        selected: Candidate,
        base: PathFillMemoryFootprint,
        aa: PathAaMemoryFootprint,
        coverage: Boolean,
    ): AaResolvedColorTopology {
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        val multisample = planResourceId(PlanResourceRole.MultisampleColorTarget, 0)
        val resolved = planResourceId(if (coverage) PlanResourceRole.CoverageSource else PlanResourceRole.PathAaResolvedColor, 0)
        val vertex = planResourceId(PlanResourceRole.VertexData, 0)
        val index = planResourceId(PlanResourceRole.IndexData, 0)
        val uniform = planResourceId(PlanResourceRole.UniformData, 0)
        val data = PlanDrawDataResources(vertex, index, uniform)
        val usesStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover }
        val depth = if (usesStencil) planResourceId(PlanResourceRole.DepthStencil, 0) else null
        val passes = buildList {
            var ordinal = 0
            selected.draws.forEach { sealed ->
            val selectedDraw = generalDraw(sealed, CoveragePlan.StencilAA4, SamplePlan.Multisample4)
            // The deferred source retains the selected material in `selected.sources`; its
            // hidden geometry producer is deliberately opaque-white SrcOver only.
            val draw = if (w7AaDeferredSource) selectedDraw.rebindGeometryV6(
                selectedDraw.copyPathGeometry(), selectedDraw.copyScissorI32(), blend = BlendPlan.SrcOver,
            ) else selectedDraw
                if (sealed.strategy == PathFillStrategy.DirectTriangle) {
                    add(aaDirectColorPass(ordinal++, multisample, draw, data, null,
                        if (isEmpty()) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load, resolved))
                } else {
                    val group = canonicalGeneralPathAtomicGroup(draw)
                    add(PlanPass.PathRenderPass(ordinal++, multisample, draw, PathRenderPhase.MultisampleStencilProducer,
                        data, group, depth, AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
                        PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore, null))
                    add(PlanPass.PathRenderPass(ordinal++, multisample, draw, PathRenderPhase.MultisampleStencilColorCover,
                        data, group, depth, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                        PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset, resolved))
                }
            }
        }
        fun texture(role: PlanResourceRole, format: PlanTextureFormat, bytes: Long,
            usages: Set<PlanResourceUsage>, samples: Int) = PlanResource.of(role, 0, PlanResourceKind.Texture2D,
            format, extent, bytes, usages, PlanResourceLifetime.FrameLocal, 0, passes.size, samples)
        fun buffer(role: PlanResourceRole, bytes: Long, usages: Set<PlanResourceUsage>) =
            PlanResource.of(role, 0, PlanResourceKind.Buffer, null, null, bytes, usages,
                PlanResourceLifetime.FrameLocal, 0, passes.size)
        val colorFormat = AA_FORMAT
        val resources = buildList {
            add(texture(PlanResourceRole.MultisampleColorTarget, PlanTextureFormat.Color(colorFormat), aa.multisampleColorBytes,
                setOf(PlanResourceUsage.RenderAttachment), 4))
            add(texture(if (coverage) PlanResourceRole.CoverageSource else PlanResourceRole.PathAaResolvedColor, PlanTextureFormat.Color(colorFormat), base.targetBytes,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 1))
            if (depth != null) add(texture(PlanResourceRole.DepthStencil,
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), aa.multisampleDepthStencilBytes,
                setOf(PlanResourceUsage.DepthStencilAttachment), 4))
            add(buffer(PlanResourceRole.VertexData, base.vertexCapacityBytes,
                setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination)))
            add(buffer(PlanResourceRole.IndexData, base.indexCapacityBytes,
                setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination)))
            add(buffer(PlanResourceRole.UniformData, base.uniformCapacityBytes,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination)))
        }
        return AaResolvedColorTopology(resources, passes, dependencies(passes))
    }

    private fun w6AaSourceCapabilityId(): String = when {
        w7AaDeferredSource -> W7_AA_DEFERRED_SOURCE_CAPABILITY_ID
        w6AaCoverageSource -> W6_AA_COVERAGE_SOURCE_CAPABILITY_ID
        else -> W6_AA_COLOR_SOURCE_CAPABILITY_ID
    }

    private fun <T: Any> constructChecked(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        finish: (Candidate,List<org.graphiks.math.geometry.PathFillGeometryF32>,Boolean,Boolean,Boolean)->RenderPlanResult<T>): RenderPlanResult<T> {
        val selected = candidate as? Candidate ?: return invalidCandidate()
        if (selected.owner !== this || !selected.hasMatchingFingerprints()) return invalidCandidate()
        val extent = SizeI32(selected.target.extent.width, selected.target.extent.height)
        val anyAa = forceAaFrame || selected.requestedAa
        val colorFormat = if (anyAa) aaLogicalColorFormat(selected.target) else logicalColorFormat(selected.target)
        if (!coreCapabilities(capabilities, extent, colorFormat)) return promoted("Required W4d.2 device capability is unavailable")
        val anyHard = selected.draws.any { !it.requestsAntiAlias }
        val aaStencil = selected.draws.any { it.requestsAntiAlias && it.strategy == PathFillStrategy.StencilCover }
        val hardStencil = selected.draws.any { !it.requestsAntiAlias && it.strategy == PathFillStrategy.StencilCover }
        val aaDepthStencil = requiresAaDepthStencil(anyAa, aaStencil)
        textureRefusal(capabilities, colorFormat, anyAa, anyHard, aaDepthStencil, hardStencil)?.let { return it }
        if ((aaDepthStencil || hardStencil) && PlanOperationCapability.DepthStencilAttachment !in capabilities.supportedOperations()) {
            return promoted("W4d.2 depth-stencil capability is unavailable")
        }
        if ((aaStencil || hardStencil) && PlanOperationCapability.StencilCover !in capabilities.supportedOperations()) {
            return promoted("W4d.2 stencil cover capability is unavailable")
        }
        val geometry = selected.draws.map { it.fillGeometry() }
        return try {
            finish(selected,geometry,anyAa,anyHard,hardStencil)
        } catch (failure: RawMaterialRequirementsV2.Refusal) {
            resource(org.graphiks.kanvas.render.ir.RenderDiagnosticCode(failure.code),
                "W4d.2 material frame exceeds its aggregate memory budget")
        } catch (_: ArithmeticException) {
            resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 arithmetic overflowed")
        } catch (_: IllegalArgumentException) {
            resource(W4dGeneralPlanDiagnostics.PlanIdentityInvalid, "W4d.2 graph invariants failed")
        }
    }

    private fun preflightHard(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        usesStencil: Boolean,
        scanSpanProducerCommandIndexes: Set<Int>,
    ): RenderPlanResult<W4dGeneralFramePreview> = withHardMemory(selected,capabilities,budget,geometries,
        scanSpanProducerCommandIndexes) { memory ->
        RenderPlanResult.Ready(hardFramePreview(selected,memory,usesStencil))
    }

    private fun <T: Any> withHardMemory(selected: Candidate,capabilities: PlanCapabilitySnapshot,budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        scanSpanProducerCommandIndexes: Set<Int> = emptySet(),
        finish: (PathFillMemoryFootprint)->RenderPlanResult<T>): RenderPlanResult<T> {
        val provisionalMemory = when (val value = PathStrokePlanBudget.calculate(
            SizeI32(selected.target.extent.width, selected.target.extent.height), geometries, capabilities, budget,
        )) {
            is PathStrokePlanBudgetResult.WithinBudget -> value.footprint
            is PathStrokePlanBudgetResult.Exceeded -> return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
            is PathStrokePlanBudgetResult.Invalid -> return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 size is unrepresentable: ${value.code}")
        }
        val memory = w4dGeneralExactFrameMemory(
            provisionalMemory,
            w4dGeneralNativePayloadBudget(selected.draws, materializesHardMasks = false, scanSpanProducerCommandIndexes),
            capabilities,
        ) ?: return resource(W4dGeneralPlanDiagnostics.SizeOverflow, "W4d.2 native frame resources overflow")
        if (memory.peakBytes > budget.maxFrameLocalBytes) {
            return resource(W4dGeneralPlanDiagnostics.BudgetFrameLocalExceeded, "Frame-local budget is exceeded")
        }
        if (!buffersFit(memory, capabilities)) return promoted("W4d.2 buffer capability is unavailable")
        return finish(memory)
    }

    private fun preflightAa(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        anyHard: Boolean,
        hardStencil: Boolean,
        scanSpanProducerCommandIndexes: Set<Int>,
    ): RenderPlanResult<W4dGeneralFramePreview> {
        val provisionalMemory = when (val value = PathAaPlanBudget.calculate(
            targetExtent = SizeI32(selected.target.extent.width, selected.target.extent.height),
            geometriesF32 = geometries,
            requiresAa4DepthStencil = !w7AaDeferredSource ||
                selected.draws.any { it.strategy == PathFillStrategy.StencilCover },
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
            w4dGeneralNativePayloadBudget(selected.draws, materializesHardMasks = true, scanSpanProducerCommandIndexes),
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
            if (w7AaDeferredSource) aaDeferredSourceFramePreview(selected, memory)
            else aaFramePreview(selected, memory),
        )
    }

    private fun planHard(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        usesStencil: Boolean,
    ): RenderPlanResult<RenderGraphConstruction> = withHardMemory(selected,capabilities,budget,geometries) { memory ->
        val source = RenderGraph.issueW4dGeneralCompilerWitness(
            hardGraph(selected, capabilities, budget, memory, usesStencil))
        RenderPlanResult.Ready(if (selected.elidedNoOpsI32 > 0 ||
            !retainGeometryConstructionGraph && selected.materialPlanTable?.gradientStopSlab != null ||
            selected.draws.any { it.blend != BlendPlan.SrcOver })
            issueW5bGeneralPathGraph(source, selected.draws.associate { it.commandIndex to it.blend }) else source)
    }

    private fun planAa(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        geometries: List<org.graphiks.math.geometry.PathFillGeometryF32>,
        anyHard: Boolean,
        hardStencil: Boolean,
    ): RenderPlanResult<RenderGraphConstruction> {
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

    private fun hardFramePreview(
        selected: Candidate,
        memory: PathFillMemoryFootprint,
        usesStencil: Boolean,
    ): W4dGeneralFramePreview {
        val pathPassCount = selected.draws.sumOf { if (it.strategy == PathFillStrategy.DirectTriangle) 1 else 2 }
        val readbackIndex = pathPassCount
        val passCount = Math.addExact(pathPassCount, 1)
        val consumers = linkedMapOf<Int, Int>()
        var passIndex = 0
        selected.draws.forEach { draw ->
            consumers[draw.commandIndex] = if (draw.strategy == PathFillStrategy.DirectTriangle) passIndex else passIndex + 1
            passIndex = Math.addExact(passIndex, if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2)
        }
        return W4dGeneralFramePreview(
            extent = SizeI32(selected.target.extent.width, selected.target.extent.height),
            passCount = passCount,
            resources = buildList {
                add(FrameResourceSpan(memory.targetBytes, 0, passCount))
                add(FrameResourceSpan(memory.readbackBytes, readbackIndex, passCount))
                add(FrameResourceSpan(memory.vertexCapacityBytes, 0, passCount, PlanResourceRole.VertexData))
                add(FrameResourceSpan(memory.indexCapacityBytes, 0, passCount, PlanResourceRole.IndexData))
                add(FrameResourceSpan(memory.uniformCapacityBytes, 0, passCount))
                if (usesStencil) add(FrameResourceSpan(memory.depthStencilBytes, 0, passCount))
            },
            colorConsumerPassByCommand = consumers,
        )
    }

    private fun aaFramePreview(
        selected: Candidate,
        memory: PathAaMemoryFootprint,
    ): W4dGeneralFramePreview {
        val topology = aaTopology(selected)
        return W4dGeneralFramePreview(
            extent = SizeI32(selected.target.extent.width, selected.target.extent.height),
            passCount = topology.passCount,
            resources = buildList {
                add(FrameResourceSpan(memory.multisampleColorBytes, 0, topology.readbackIndex))
                add(FrameResourceSpan(memory.base.targetBytes, 0, topology.passCount))
                add(FrameResourceSpan(memory.multisampleDepthStencilBytes, 0, topology.readbackIndex))
                topology.hardMaskLives.forEach { life ->
                    add(FrameResourceSpan(memory.hardEdgeMaskCapacityBytes, life.first, life.last))
                }
                topology.hardDepthLives.forEach { life ->
                    add(FrameResourceSpan(memory.hardEdgeDepthStencilCapacityBytes, life.first, life.last))
                }
                add(FrameResourceSpan(memory.base.readbackBytes, topology.readbackIndex, topology.passCount))
                add(FrameResourceSpan(memory.base.vertexCapacityBytes, 0, topology.passCount, PlanResourceRole.VertexData))
                add(FrameResourceSpan(memory.base.indexCapacityBytes, 0, topology.passCount, PlanResourceRole.IndexData))
                add(FrameResourceSpan(memory.base.uniformCapacityBytes, 0, topology.passCount))
            },
            colorConsumerPassByCommand = topology.colorConsumerPassByCommand,
        )
    }

    /**
     * The closed W7 source has no W4d readback pass: it terminates at the coverage resolve
     * consumed by W4e/W6.  Keep its preview to that same source topology so W4e can account
     * for its own direct-triangle expansion before publishing physical resources.
     */
    private fun aaDeferredSourceFramePreview(
        selected: Candidate,
        memory: PathAaMemoryFootprint,
    ): W4dGeneralFramePreview {
        require(selected.draws.all { it.requestsAntiAlias })
        val topology = aaTopology(selected)
        val sourcePassCount = topology.readbackIndex
        val usesStencil = selected.draws.any { it.strategy == PathFillStrategy.StencilCover }
        return W4dGeneralFramePreview(
            extent = SizeI32(selected.target.extent.width, selected.target.extent.height),
            passCount = sourcePassCount,
            resources = buildList {
                add(FrameResourceSpan(memory.multisampleColorBytes, 0, sourcePassCount))
                add(FrameResourceSpan(memory.base.targetBytes, 0, sourcePassCount))
                if (usesStencil) add(FrameResourceSpan(memory.multisampleDepthStencilBytes, 0, sourcePassCount))
                add(FrameResourceSpan(memory.base.vertexCapacityBytes, 0, sourcePassCount, PlanResourceRole.VertexData))
                add(FrameResourceSpan(memory.base.indexCapacityBytes, 0, sourcePassCount, PlanResourceRole.IndexData))
                add(FrameResourceSpan(memory.base.uniformCapacityBytes, 0, sourcePassCount))
            },
            colorConsumerPassByCommand = topology.colorConsumerPassByCommand,
        )
    }

    private fun aaTopology(selected: Candidate): W4dGeneralAaTopology {
        val colorConsumers = linkedMapOf<Int, Int>()
        val hardMasks = mutableListOf<ResourceLife>()
        val hardDepths = mutableListOf<ResourceLife>()
        var passIndex = 0
        var maskOrdinal = 0
        var hardDepthOrdinal = 0
        selected.draws.forEach { draw ->
            if (draw.requestsAntiAlias) {
                colorConsumers[draw.commandIndex] = if (draw.strategy == PathFillStrategy.DirectTriangle) {
                    passIndex
                } else {
                    passIndex + 1
                }
                passIndex = Math.addExact(passIndex, if (draw.strategy == PathFillStrategy.DirectTriangle) 1 else 2)
            } else {
                val clearIndex = passIndex++
                if (draw.strategy == PathFillStrategy.DirectTriangle) {
                    passIndex++
                } else {
                    val producerIndex = passIndex++
                    passIndex++
                    hardDepths += ResourceLife(hardDepthOrdinal++, producerIndex, passIndex)
                }
                colorConsumers[draw.commandIndex] = passIndex++
                hardMasks += ResourceLife(maskOrdinal++, clearIndex, passIndex)
            }
        }
        val readbackIndex = passIndex
        return W4dGeneralAaTopology(
            passCount = Math.addExact(readbackIndex, 1),
            readbackIndex = readbackIndex,
            hardMaskLives = hardMasks,
            hardDepthLives = hardDepths,
            colorConsumerPassByCommand = colorConsumers,
        )
    }

    private fun hardGraph(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        memory: PathFillMemoryFootprint,
        usesStencil: Boolean,
    ): RenderGraphConstruction {
        val colorFormat = logicalColorFormat(selected.target)
        val topology = hardSourceTopology(selected,memory,usesStencil, colorFormat)
        return RenderGraph.construct(PlanId(identity(selected,capabilities,budget,W5A_HARD_CAPABILITY_ID)),W5A_HARD_CAPABILITY_ID,
            SizeI32(selected.target.extent.width,selected.target.extent.height),colorFormat,capabilities,budget,selected.draws.size,
            topology.resources,topology.passes,topology.dependencies,topology.peakI64,selected.materialPlanTable)
    }

    private fun hardSourceTopology(selected: Candidate,memory: PathFillMemoryFootprint,
        usesStencil: Boolean, colorFormat: PlanLogicalColorFormat = logicalColorFormat(selected.target)): W5bDestinationGraphSealer.DestinationTopologyV4 {
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
        val target = resource(PlanResourceRole.LogicalTarget, PlanResourceKind.Texture2D, PlanTextureFormat.Color(colorFormat),
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
        return W5bDestinationGraphSealer.DestinationTopologyV4(colorFormat,listOfNotNull(target,staging,vertex,index,uniform,depth),
            passes,dependencies(passes),memory.peakBytes)
    }

    private fun aaGraph(
        selected: Candidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        memory: PathAaMemoryFootprint,
        hardStencil: Boolean,
    ): RenderGraphConstruction {
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
                    passes += aaDirectColorPass(pathOrdinal++, multisampleId, draw, data, aaDepthId, load, null)
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
        val colorFormat = aaLogicalColorFormat(selected.target)
        resources += texture(PlanResourceRole.MultisampleColorTarget, 0, PlanTextureFormat.Color(colorFormat),
            memory.multisampleColorBytes, setOf(PlanResourceUsage.RenderAttachment), 0, readbackIndex, 4)
        resources += texture(PlanResourceRole.LogicalTarget, 0, PlanTextureFormat.Color(colorFormat), memory.base.targetBytes,
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
        return RenderGraph.construct(
            id = PlanId(identity(selected, capabilities, budget, W5A_AA_CAPABILITY_ID)),
            capabilityId = W5A_AA_CAPABILITY_ID,
            targetExtent = extent,
            colorFormat = colorFormat,
            capabilities = capabilities,
            budget = budget,
            visualCommandCount = selected.draws.size,
            resources = resources,
            passes = passes,
            dependencies = dependencies(passes),
            peakFrameLocalBytes = memory.peakBytes,
            materialPlanTable = selected.materialPlanTable,
        )
    }

    /** The colour phase is identical for standalone AA and an isolated W6 source. */
    private fun aaDirectColorPass(ordinal: Int, target: PlanResourceId, draw: GeneralPathDraw,
        data: PlanDrawDataResources, depth: PlanResourceId?, load: AttachmentLoadPlan,
        resolve: PlanResourceId?): PlanPass.PathRenderPass =
        PlanPass.PathRenderPass(ordinal, target, draw, PathRenderPhase.MultisampleDirectColor,
            data, null, depth, load, AttachmentStorePlan.Store, null, null, resolve)

    private fun generalDraw(sealed: SealedDraw, coverage: CoveragePlan, sample: SamplePlan): GeneralPathDraw =
        GeneralPathDraw.ofMaterial(sealed.commandIndex, sealed.material, sealed.geometry, sealed.strategy, sealed.scissorI32, coverage, sample,
            coordinates = sealed.coordinates, coordinatesV2 = sealed.coordinatesV2, coordinatesV4 = sealed.coordinatesV4)

    private fun logicalColorFormat(target: RenderTargetDescriptor): PlanLogicalColorFormat = when (target.compositionDomain) {
        org.graphiks.kanvas.render.ir.CompositionDomain.LINEAR -> PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        org.graphiks.kanvas.render.ir.CompositionDomain.SRGB_ENCODED -> PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL
    }

    private fun aaLogicalColorFormat(target: RenderTargetDescriptor): PlanLogicalColorFormat =
        if (requiresPublicEncodedAdmission) logicalColorFormat(target) else AA_FORMAT

    private fun coreCapabilities(
        capabilities: PlanCapabilitySnapshot,
        extent: SizeI32,
        colorFormat: PlanLogicalColorFormat,
    ): Boolean =
        extent.width <= capabilities.maxTextureDimension2D && extent.height <= capabilities.maxTextureDimension2D &&
            colorFormat in capabilities.supportedFormats() && capabilities.maxDynamicUniformBuffersPerPipelineLayout >= 1 &&
            REQUIRED.all { it in capabilities.supportedOperations() } && validAllocationFacts(capabilities)

    /**
     * The autonomous W4d AA graph still carries its conservative depth contract.  The W6-only
     * resolved-colour source is narrower: DirectTriangle has no depth attachment, while
     * StencilCover remains dependent on D24S8 and the stencil operation.
     */
    private fun requiresAaDepthStencil(anyAa: Boolean, aaStencil: Boolean): Boolean =
        anyAa && (!allowAaColorSource || aaStencil)

    private fun textureRefusal(
        caps: PlanCapabilitySnapshot,
        colorFormat: PlanLogicalColorFormat,
        anyAa: Boolean,
        anyHard: Boolean,
        aaDepthStencil: Boolean,
        hardStencil: Boolean,
    ): RenderPlanResult.GapOnPromotedScope? {
        val logical = PlanTextureFormat.Color(colorFormat)
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
        if (aaDepthStencil && !caps.supportsTexture(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4,
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
        scanSpanProducerCommandIndexes: Set<Int> = emptySet(),
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
                    if (draw.commandIndex !in scanSpanProducerCommandIndexes) {
                        vertexFloatCount = Math.addExact(vertexFloatCount, direct.copyVerticesF32().size.toLong())
                        indexCount = Math.addExact(indexCount, direct.copyIndicesI32().size.toLong())
                    }
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
        // The W4e scan-span producer has no indexed upload, but it still owns the established
        // physical scratch pools.  Reserve their policy floor once; reserve(0) means "no pool"
        // in the shared policy and would incorrectly turn this valid route into an overflow.
        fun reserve(kind: PlanScratchBufferKind, usefulBytes: Long): Long? =
            policy.reserve(kind, maxOf(1L, usefulBytes))
        val vertexCapacityBytes = reserve(PlanScratchBufferKind.Vertex, vertexUsefulBytes) ?: return null
        val indexCapacityBytes = reserve(PlanScratchBufferKind.Index, indexUsefulBytes) ?: return null
        val uniformCapacityBytes = reserve(PlanScratchBufferKind.Uniform, uniformReservedBytes) ?: return null
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
        is PathDrawGeometry.InverseDomainSource -> error("W4d.2 cannot retain W4e inverse-domain source geometry")
        PathDrawGeometry.Empty -> error("W4d.2 cannot retain W4e inverse-domain empty geometry")
    }

    private fun PathTransformedFillInvalidSceneReason.isHorizon(): Boolean =
        this is PathTransformedFillInvalidSceneReason.Projective &&
            value == PathProjectiveInvalidSceneReason.PerspectiveHorizonCrossing

    private fun solid(node: DrawNode, paint: PaintNode): Boolean =
        (if (paint.colorFilter == null) effectsMatchPaintPathEffect(node.effects, paint.pathEffect)
            else paint.style == PaintStyleNode.FILL && paint.pathEffect == null && colorFilterEffectsMatchPaint(node)) && node.resource == null &&
        node.operationBlendMode == null && w4Blend(node.blend) && paint.blender == null &&
        paint.maskFilter == null && paint.imageFilter == null &&
         (paint.pathEffect == null || paint.pathEffect is PathEffectNode.Dash) &&
        materialMatchesPaintAuthority(node)

    /** W7 accepts the existing Solid/Opacity material subset, but no source-effect wrapper. */
    private fun deferredAaMaterial(node: DrawNode, paint: PaintNode): Boolean {
        var material = node.material
        while (material is MaterialNode.Opacity) material = material.material
        return material is MaterialNode.Solid && node.resource == null && node.operationBlendMode == null &&
            paint.blender == null && paint.colorFilter == null && paint.maskFilter == null &&
            paint.imageFilter == null && paint.pathEffect == null && node.effects == EffectStack.Empty
    }

    private fun w7PorterDuff(blend: BlendNode): Boolean = when (blend) {
        BlendNode.SrcOver -> true
        is BlendNode.Mode -> blend.mode in W7_PORTER_DUFF_BLEND_MODES
        is BlendNode.Paint -> blend.blender == null && blend.mode in W7_PORTER_DUFF_BLEND_MODES
        is BlendNode.Custom -> false
    }

    /**
     * Rect keeps its original provenance when W7 projects it to a path.  The old W7 projection
     * already owns the non-SRC_OVER/PLUS blends; retain that axis-aligned route.  SRC_OVER and
     * PLUS join it only for GeneralAffine, where W4a has no analytic source lane.
     */
    private fun w7DeferredRectProjection(node: DrawNode): Boolean {
        if (!w7AaDeferredSource || !w7PorterDuff(node.blend)) return false
        val historicallyAnalyticBlend = when (val blend = node.blend) {
            BlendNode.SrcOver -> true
            is BlendNode.Mode -> blend.mode in setOf(BlendMode.SRC_OVER, BlendMode.PLUS)
            is BlendNode.Paint -> blend.blender == null && blend.mode in setOf(BlendMode.SRC_OVER, BlendMode.PLUS)
            is BlendNode.Custom -> false
        }
        return !historicallyAnalyticBlend ||
            node.transform.toMatrix3x3F64().classifyPathTransform() == PathTransformClass.GeneralAffine
    }

    private fun acceptsPictureHardRect(node: DrawNode): Boolean {
        val paint = node.paint ?: return false
        val transformClass = node.transform.toMatrix3x3F64().classifyPathTransform()
        return node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect &&
            node.coverage == CoverageRequest.HARD_EDGE && node.material is MaterialNode.Solid &&
            paint.style == PaintStyleNode.FILL && paint.shader == null && paint.colorFilter == null &&
            paint.blender == null && paint.maskFilter == null && paint.imageFilter == null && paint.pathEffect == null &&
            node.effects == EffectStack.Empty && srcOver(node.blend) &&
            transformClass in setOf(PathTransformClass.GeneralAffine, PathTransformClass.Perspective)
    }

    internal fun acceptsW6HardPictureRectScope(node: DrawNode): Boolean =
        rectProjectionMode == RectProjectionMode.PictureHardFill && acceptsPictureHardRect(node)

    /** Strict W7 root extension; broader historical W4d path admission remains unchanged. */
    private fun standaloneFrameDraw(node: DrawNode): Boolean {
        val paint = node.paint ?: return false
        val srcOver = when (val blend = node.blend) {
            BlendNode.SrcOver -> true
            is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER
            is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null
            is BlendNode.Custom -> false
        }
        return node.material is MaterialNode.Solid && node.resource == null && node.operationBlendMode == null &&
            srcOver && paint.blender == null && paint.colorFilter == null && paint.maskFilter == null &&
            paint.imageFilter == null && paint.pathEffect == null && paint.shader == null && node.effects == EffectStack.Empty &&
            paint.style in setOf(PaintStyleNode.FILL, PaintStyleNode.STROKE)
    }

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

    private fun integral(bounds: RectF32): RectI32? = bounds.toExactRectI32OrNull()

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
    private fun w4Blend(blend: BlendNode): Boolean = when (blend) { BlendNode.SrcOver -> true; is BlendNode.Mode -> true; is BlendNode.Paint -> blend.blender == null; is BlendNode.Custom -> false }
    private fun srcOver(blend: BlendNode): Boolean = when (blend) {
        BlendNode.SrcOver -> true
        is BlendNode.Mode -> blend.mode == BlendMode.SRC_OVER
        is BlendNode.Paint -> blend.mode == BlendMode.SRC_OVER && blend.blender == null
        is BlendNode.Custom -> false
    }
    private fun projectedIntegralRectI32(rect: RectF32, matrix: Matrix3x3F64): RectI32? {
        fun coordinate(value: Double): Int? = value.takeIf { it.isFinite() &&
            it >= Int.MIN_VALUE.toDouble() && it <= Int.MAX_VALUE.toDouble() &&
            it == it.toLong().toDouble() }?.toLong()?.toInt()
        val firstX = rect.left.toDouble() * matrix.sxF64 + matrix.txF64
        val secondX = rect.right.toDouble() * matrix.sxF64 + matrix.txF64
        val firstY = rect.top.toDouble() * matrix.syF64 + matrix.tyF64
        val secondY = rect.bottom.toDouble() * matrix.syF64 + matrix.tyF64
        val left = coordinate(minOf(firstX, secondX)) ?: return null
        val right = coordinate(maxOf(firstX, secondX)) ?: return null
        val top = coordinate(minOf(firstY, secondY)) ?: return null
        val bottom = coordinate(maxOf(firstY, secondY)) ?: return null
        return RectI32(left, top, right, bottom).takeUnless(RectI32::isEmpty64)
    }
    private fun materialMatchesPaintAuthority(node: DrawNode): Boolean { val paint = node.paint ?: return false; val paintMaterial = paint.shader ?: MaterialNode.Solid(paint.color); return node.material.canonicalId == paintMaterial.canonicalId }
    private fun appendMaterialPlan(entries: MutableList<MaterialPlanEntry>, incoming: MaterialPlanTable, root: MaterialPlanRef): MaterialPlanRef { val offset = entries.size; incoming.entries().forEach { entry -> entries += entry }; return MaterialPlanRef(offset + root.indexI32) }
    private fun validAllocationFacts(capabilities: PlanCapabilitySnapshot): Boolean = listOf(
        capabilities.copyBytesPerRowAlignment.toLong(), capabilities.minUniformBufferOffsetAlignment.toLong(),
        capabilities.bufferAllocationPolicy.vertexFloorBytes, capabilities.bufferAllocationPolicy.indexFloorBytes,
        capabilities.bufferAllocationPolicy.uniformFloorBytes,
    ).all { it > 0L && it and (it - 1L) == 0L }

    private fun identity(selected: Candidate, capabilities: PlanCapabilitySnapshot, budget: PlanBudget, capability: String,
        sceneIdentity: CanonicalId = selected.sceneCanonicalId): String {
        val fields = listOf(
            "w4d-general-plan-v2-material-v1", capability, sceneIdentity.value, selected.target.canonicalId.value,
            capabilities.deviceGeneration.toString(), capabilities.maxTextureDimension2D.toString(), capabilities.maxBufferSizeBytes.toString(),
            capabilities.copyBytesPerRowAlignment.toString(), capabilities.identitySupportedFormats(selected.target).map { it.name }.sorted().joinToString(","),
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
        ) + planCapabilityIdentityFacts(capabilities, selected.target)
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
    private sealed interface Recognition { data class MaterialRefused(val refusals: List<EffectiveMaterialPlanner.Result.Refused>) : Recognition; data class Ready(val draws: List<SealedDraw>, val materialPlanTable: MaterialPlanTable?, val elidedNoOpsI32: Int, val requestedAa: Boolean,val sources: MaterialSourceConstructionTableV4) : Recognition; data class Gap(val message: String) : Recognition; data class Invalid(val message: String) : Recognition; data class Horizon(val message: String) : Recognition; data class Limit(val message: String) : Recognition }
    private sealed interface DrawScope {
        data class Ready(val path: PathF32, val matrixF64: Matrix3x3F64, val transformClass: PathTransformClass, val clip: RectI32?, val fill: Boolean, val mode: PathStrokeDrawMode?, val styleF64: PathStrokeStyleF64?, val requestsAntiAlias: Boolean, val rectProjection: Boolean = false, val rectHairlineDeviceRectI32: RectI32? = null) : DrawScope
        data class Gap(val message: String) : DrawScope
        data class Invalid(val message: String) : DrawScope
    }
    private sealed interface DrawResult { data class NoOp(val frameWorkUsageI64: PathStrokeWorkUsageI64) : DrawResult; data class MaterialRefused(val refusal: EffectiveMaterialPlanner.Result.Refused, val frameWorkUsageI64: PathStrokeWorkUsageI64) : DrawResult; data class Ready(val draw: SealedDraw, val frameWorkUsageI64: PathStrokeWorkUsageI64) : DrawResult; data class Empty(val frameWorkUsageI64: PathStrokeWorkUsageI64, val elidedAdmittedRectHairline: Boolean = false) : DrawResult; data class Gap(val message: String) : DrawResult; data class Invalid(val message: String) : DrawResult; data class Horizon(val message: String) : DrawResult; data class Limit(val message: String) : DrawResult }
    private sealed interface Prepared { data class Ready(val geometry: org.graphiks.math.geometry.PathFillGeometryF32, val pathGeometry: PathDrawGeometry, val frameWorkUsageI64: PathStrokeWorkUsageI64) : Prepared; data class Empty(val frameWorkUsageI64: PathStrokeWorkUsageI64) : Prepared; data class Invalid(val message: String) : Prepared; data class Horizon(val message: String) : Prepared; data class Limit(val message: String) : Prepared }
    private data class SealedDraw(val commandIndex: Int, val material: MaterialPlanRef, val geometry: PathDrawGeometry, val strategy: PathFillStrategy, val scissorI32: RectI32, val requestsAntiAlias: Boolean, val blend: BlendPlan, val coordinates: MaterialCoordinatePlanV1?, val coordinatesV2: MaterialCoordinatePlanV2?, val coordinatesV4: SourceCoordinatesV4?)
    private data class ResourceLife(val ordinal: Int, val first: Int, val last: Int)
    private data class W4dGeneralAaTopology(
        val passCount: Int,
        val readbackIndex: Int,
        val hardMaskLives: List<ResourceLife>,
        val hardDepthLives: List<ResourceLife>,
        val colorConsumerPassByCommand: Map<Int, Int>,
    )
    private class Candidate(val owner: W4dGeneralPathPlanCompiler, override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId, override val target: RenderTargetDescriptor, draws: List<SealedDraw>, val materialPlanTable: MaterialPlanTable?, val elidedNoOpsI32: Int, val requestedAa: Boolean,val sources: MaterialSourceConstructionTableV4) : GpuPlanCandidate {
        override val capabilityId: String = if (owner.forceAaFrame || requestedAa) W5A_AA_CAPABILITY_ID else W5A_HARD_CAPABILITY_ID
        val draws: List<SealedDraw> = Collections.unmodifiableList(draws.map { it.copy(scissorI32 = it.scissorI32.copy()) })
        private val sceneFingerprint = sceneCanonicalId
        private val targetFingerprint = target.canonicalId
        fun hasMatchingFingerprints(): Boolean = isW5aMaterialCapabilityId(capabilityId) && sceneCanonicalId == sceneFingerprint && target.canonicalId == targetFingerprint
    }

    public companion object {
        public const val W5B_HARD_CAPABILITY_ID: String = "w5b-general-path-hard-final-blend-v3"
        public const val HARD_CAPABILITY_ID: String = "solid-path-geometry-hard-1x-general-transform-simple-scissor-src-over-srgb-v1"
        public const val AA_CAPABILITY_ID: String = "solid-path-geometry-mixed-aa4-general-transform-simple-scissor-src-over-srgb-v1"
        /** W5a material-bearing successor to the historical [HARD_CAPABILITY_ID] contract. */
        public const val W5A_HARD_CAPABILITY_ID: String = "solid-path-geometry-hard-1x-general-transform-simple-scissor-src-over-srgb-w5a-material-v2"
        /** W5a material-bearing successor to the historical [AA_CAPABILITY_ID] contract. */
        public const val W5A_AA_CAPABILITY_ID: String = "solid-path-geometry-mixed-aa4-general-transform-simple-scissor-src-over-srgb-w5a-material-v2"
        /** W6-only sealed child source; intentionally distinct from W4d's standalone AA graph. */
        public const val W6_AA_COLOR_SOURCE_CAPABILITY_ID: String = "w6-aa-resolved-color-source-v1"
        /** W6-only opaque-white AA coverage handoff; colour authority remains in W5. */
        public const val W6_AA_COVERAGE_SOURCE_CAPABILITY_ID: String = "w6-aa-resolved-coverage-source-v1"
        /** W7 source has the same geometric resolve, with an explicit deferred final consumer. */
        public const val W7_AA_DEFERRED_SOURCE_CAPABILITY_ID: String = "w7-aa-deferred-coverage-source-v1"

        /** Root-only opt-in; W5/W6 source compilers keep the default path-only contract. */
        public fun standaloneRectPathFrames(): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(),
            admitsStandaloneRectPathFrames = true,
            requiresPublicEncodedAdmission = true,
        )

        internal fun w6AaColorSource(
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(),
            acceptsNarrowTransforms = true,
            allowAaColorSource = true,
            runtimeCatalog = catalog,
        )

        /** Closed W6 source for a recorded general-affine/perspective hard Rect Picture fill. */
        internal fun w6HardRectFillSource(
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(), runtimeCatalog = catalog,
            rectProjectionMode = RectProjectionMode.PictureHardFill,
        )

        /** W6-only hard source retaining the Task 1 Rect hairline projection. */
        internal fun w6EncodedRectHairlineSource(
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(),
            admitsStandaloneRectPathFrames = true,
            runtimeCatalog = catalog,
        )

        /** Root-only W7 source; this is deliberately not the broad W6 path source. */
        internal fun w6RootAaRectStrokeSource(
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(),
            acceptsNarrowTransforms = true,
            allowAaColorSource = true,
            w6RootAaRectStrokeSource = true,
            runtimeCatalog = catalog,
        )

        internal fun w6AaCoverageSource(
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(),
            acceptsNarrowTransforms = true,
            allowAaColorSource = true,
            runtimeCatalog = catalog,
            w6AaCoverageSource = true,
        )

        internal fun w7AaDeferredSource(
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): W4dGeneralPathPlanCompiler = W4dGeneralPathPlanCompiler(
            PathStrokePolicyF64(),
            acceptsNarrowTransforms = true,
            runtimeCatalog = catalog,
            w7AaDeferredSource = true,
        )

        public fun isLegacyCapabilityId(capabilityId: String): Boolean =
            capabilityId == HARD_CAPABILITY_ID || capabilityId == AA_CAPABILITY_ID

        public fun isW5aMaterialCapabilityId(capabilityId: String): Boolean =
            capabilityId == W5A_HARD_CAPABILITY_ID || capabilityId == W5A_AA_CAPABILITY_ID

        public fun isHardCapabilityId(capabilityId: String): Boolean =
            capabilityId == HARD_CAPABILITY_ID || capabilityId == W5A_HARD_CAPABILITY_ID

        public fun isAaCapabilityId(capabilityId: String): Boolean =
            capabilityId == AA_CAPABILITY_ID || capabilityId == W5A_AA_CAPABILITY_ID
        private val REQUIRED = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload, PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback)
        private val AA_FORMAT = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
        private const val MAX_DRAWS = 512
    }
}

/** Shared W4 stroke-style translation used by both finite W4d draws and W4e inverse interiors. */
internal fun w4PathStrokeStyleF64(
    paint: PaintNode,
    mode: PathStrokeDrawMode,
): PathStrokeStyleF64 = PathStrokeStyleF64(
    if (paint.strokeWidth == 0f && mode == PathStrokeDrawMode.Stroke) PathStrokeWidthF64.Hairline
    else PathStrokeWidthF64.Finite(paint.strokeWidth.toDouble()),
    when (paint.strokeCap) {
        StrokeCapNode.BUTT -> PathStrokeCap.Butt
        StrokeCapNode.ROUND -> PathStrokeCap.Round
        StrokeCapNode.SQUARE -> PathStrokeCap.Square
    },
    when (paint.strokeJoin) {
        StrokeJoinNode.MITER -> PathStrokeJoin.Miter
        StrokeJoinNode.ROUND -> PathStrokeJoin.Round
        StrokeJoinNode.BEVEL -> PathStrokeJoin.Bevel
    },
    paint.strokeMiter.toDouble(),
    (paint.pathEffect as? PathEffectNode.Dash)?.let { dash ->
        PathStrokeDashF64.of(
            dash.intervals.copyToFloatArray().map(Float::toDouble).toDoubleArray(),
            dash.phase.toDouble(),
        )
    },
)
