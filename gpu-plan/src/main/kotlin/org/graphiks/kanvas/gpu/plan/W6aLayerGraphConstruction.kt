package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaskFilterNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.intersectF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.geometry.translateCheckedOrNull
import org.graphiks.math.vector.Vector2I32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.composeInOrderF64
import org.graphiks.math.matrix.isFinite
import org.graphiks.math.matrix.mapRectBoundsF64OrNull
import org.graphiks.math.matrix.relativeToOriginI32OrNull
import org.graphiks.math.matrix.timesCheckedOrNull
import org.graphiks.math.matrix.toMatrix3x3F64

/** One unpublished W3/W5 lane, attached to its exact command occurrence and immediate target. */
internal class W6aLayerSourceBinding(
    val scopeI32: Int?,
    val firstCommandIndexI32: Int,
    val source: SourceDeferredRenderConstructionV4,
    val occurrenceInput: OccurrenceSourceInputV1? = null,
)

/** All device-space regions are sealed before the physical target has an extent. */
private class W6aScopeGeometry(
    val occurrence: W6aLayerPlanCompiler.ScopeOccurrence,
    val mapping: LayerMappingF64?,
    val requestedHintDeviceF64: RectF64?,
    val knownContentDeviceI32: RectI32?,
    val desiredOutputDeviceI32: RectI32?,
    val requiredInputDeviceI32: RectI32?,
    val producedOutputDeviceI32: RectI32?,
    val compositeDomainDeviceI32: RectI32?,
) {
    val isElided: Boolean get() = compositeDomainDeviceI32 == null
    fun targetExtentI32(): SizeI32 = requireNotNull(compositeDomainDeviceI32).let { SizeI32(it.width(), it.height()) }
}

/** Restore semantics are sealed before geometry decides how large a transparent layer must be. */
private class W6aRestoreFacts(
    val alphaF32: Float,
    val colorFilter: ColorFilterExecutionPlanV1?,
    val blend: BlendPlan,
    initWithPrevious: Boolean,
    hasBackdrop: Boolean,
    hasLayerFilter: Boolean,
) {
    /** Only the selected W5 destination-read blend needs a restore-time snapshot resource. */
    val restoreReadsPriorDevice: Boolean = blend.compositionFacts.readsPriorDevice
    /**
     * W5 seals an identity previous-content restore as fixed-function SRC_OVER.  It may retain
     * the hint-sized domain because transparent source leaves the unchanged parent untouched;
     * every other selected restore needs the complete parent domain at BeginLayer.
     */
    private val isIdentityPreviousPassthrough: Boolean = alphaF32 == 1f && colorFilter == null &&
        (blend as? BlendPlan.FixedFunctionV1)?.let { fixed ->
            fixed.mode == BlendMode.SRC_OVER && fixed.compositionFacts.let { facts ->
                !facts.readsPriorDevice && !facts.affectsTransparentBlack && facts.writesParentDevice
            }
        } == true
    val previousContentRequiresFullParentDomain: Boolean = initWithPrevious &&
        (!isIdentityPreviousPassthrough || hasLayerFilter)
    /** A backdrop samples its immediate parent before the child target exists, so it needs its complete desired domain. */
    val backdropRequiresFullParentDomain: Boolean = hasBackdrop
    val readsPriorDevice: Boolean = restoreReadsPriorDevice || previousContentRequiresFullParentDomain || backdropRequiresFullParentDomain
    val writesParentDevice: Boolean = blend.compositionFacts.writesParentDevice
    val restoreAffectsTransparentBlack: Boolean = blend.finalRestoreAffectsTransparentBlackV1(colorFilter)
}

/** Keeps the only Device→target conversion for a deferred Picture clip in gpu-plan. */
private fun RectF64.toExactI32OrNull(): RectI32? {
    fun coordinate(value: Double): Int? = if (value.isFinite() &&
        value >= Int.MIN_VALUE.toDouble() && value <= Int.MAX_VALUE.toDouble() &&
        value == value.toLong().toDouble()) Math.toIntExact(value.toLong()) else null
    return RectI32(coordinate(left) ?: return null, coordinate(top) ?: return null,
        coordinate(right) ?: return null, coordinate(bottom) ?: return null)
}

/** Deliberately distinguished from malformed W6 topology so callers can recover before native work. */
internal class W6aRestoreAdmissionFailure(message: String) : IllegalArgumentException(message)

/**
 * Freezes a complete layer event stack into one physical graph. In particular, this class never
 * groups by depth: each Begin, direct draw segment, and End is emitted in captured command order.
 */
internal class W6aLayerGraphConstruction(
    private val id: PlanId,
    val extent: SizeI32,
    val caps: PlanCapabilitySnapshot,
    val budget: PlanBudget,
    occurrences: List<W6aLayerPlanCompiler.ScopeOccurrence>,
    sourceBindings: List<W6aLayerSourceBinding>,
    private val filterScene: org.graphiks.kanvas.render.ir.SceneSnapshot? = null,
    private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot = RuntimeEffectSemanticCatalogSnapshot.Unbound,
) {
    private val occurrences = immutableList(occurrences)
    private val bindings = sourceBindings.toMutableList()
    val lanes: List<SourceDeferredRenderConstructionV4> get() = immutableList(bindings.map { it.source })
    private val root = planResourceId(PlanResourceRole.LogicalTarget, 0)
    private val staging = planResourceId(PlanResourceRole.ReadbackStaging, 0)
    private val rootDomainDeviceI32 = RectI32(0, 0, extent.width, extent.height)
    private val readbackRowBytesI64 = Math.multiplyExact(extent.width.toLong(), 4L).let {
        Math.addExact(it, (caps.copyBytesPerRowAlignment - it % caps.copyBytesPerRowAlignment) % caps.copyBytesPerRowAlignment)
    }
    private val geometries: List<W6aScopeGeometry>
    private val rawPasses: List<PlanPass>
    private val frame: LayerFramePlanV1
    private val resources: List<PlanResource>
    private val frozenFilterResourceSpecs: List<W6bFilterGraphConstruction.ResourceSpec>
    private val filterSourceBindings: Map<PlanResourceId, W6bFilterGraphConstruction.SourceBinding>
    /** Direct lighting sources defer their terminal hard clip until FilterComposite. */
    private val directConsumerDemandCommands: Set<Int>
    /** Additional real W5 source rows used by captured MaskShader coverage evaluation. */
    private val maskMaterialSourcesByOccurrence: Map<Int, MaterialSourceConstructionV4>
    /** One W5 row per isolated Picture paint; it samples a sealed graph texture, never a SceneSnapshot. */
    private val graphTextureMaterialSourcesByAggregate: Map<PictureStreamAggregateIdI32, MaterialSourceConstructionV4>
    private val w4eBindings = mutableListOf<PlanW4eGeometryBindingV1>()
    private val childSnapshots = mutableSetOf<PlanResourceId>()
    val nonUniformBytesI64: Long
    val passCountI32: Int get() = rawPasses.size

    init {
        require(lanes.all { it.capabilities == caps && it.budget == budget })
        val occurrenceById = occurrences.associateBy { it.idI32 }
        require(occurrenceById.size == occurrences.size)
        require(occurrences.all { occurrence ->
            occurrence.parentIdI32?.let { parent -> parent in occurrenceById && parent < occurrence.idI32 } ?: true
        })
        require(occurrences.all { occurrence -> occurrence.childIdsI32.all { child ->
            occurrenceById[child]?.parentIdI32 == occurrence.idI32
        } })
        require(occurrences.indices.all { indexI32 -> occurrences[indexI32].idI32 == indexI32 })
        require(bindings.all { binding -> binding.scopeI32 == null || binding.scopeI32 in occurrenceById })
        require(bindings.map { it.firstCommandIndexI32 }.distinct().size == bindings.size)
        require(lanes.all { source -> source.passes().all { pass -> pass is PlanPass.RenderPass || pass is PlanPass.ReadbackPass ||
            pass is PlanPass.StencilProducer || pass is PlanPass.StencilGeometryProducerV3 || pass is PlanPass.StencilCover || pass is PlanPass.TextureCopy ||
            pass is PlanPass.ClipMaskInitialize || pass is PlanPass.ClipMaskProducer || pass is PlanPass.ClipMaskFold ||
            pass is PlanPass.PathRenderPass && pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.SingleSample &&
                pass.phase in setOf(PathRenderPhase.SingleSampleDirectColor, PathRenderPhase.SingleSampleStencilProducer,
                    PathRenderPhase.SingleSampleStencilColorCover) } &&
            RenderGraph.visualDraws(source.passes()).all { draw ->
                var sourceDraw = draw
                while (sourceDraw is ClippedPlanDraw) sourceDraw = sourceDraw.source
                sourceDraw is SolidRectDraw || sourceDraw is AnalyticRectDraw || sourceDraw is AnalyticRRectDraw ||
                    sourceDraw is PathFillDraw || sourceDraw is PathStrokeDraw || sourceDraw is GeneralPathDraw ||
                    sourceDraw is W5bPointDraw || sourceDraw is W5bVerticesDraw || sourceDraw is W5bW4ePathDraw
            } }) {
            "w6a.layer.unsupported_child"
        }

        val restoreFactsByScope = occurrences.associate { occurrence ->
            occurrence.idI32 to sealRestoreFacts(occurrence)
        }
        // Direct mask auto-layers need their frozen halo while deriving an explicit W6a
        // target.  Preserve Task 3's error priority, though: a traversal refusal is deferred
        // to the former discovery point, after ordinary W6a command-limit admission.
        var deferredFilterFailure: W6bFilterGraphConstruction.ConstructionFailure? = null
        val filterOccurrences = try {
            filterScene?.takeIf(W6bFilterGraphConstruction::owns)
                ?.let(W6bFilterGraphConstruction::positiveOccurrences) ?: emptyList()
        } catch (failure: W6bFilterGraphConstruction.ConstructionFailure) {
            deferredFilterFailure = failure
            emptyList()
        }
        val pictureDiscovery = PictureStreamAggregateDiscoveryV1(
            filterOccurrences,
            filterScene?.toList()?.size ?: bindings.maxOfOrNull { it.firstCommandIndexI32 + 1 } ?: 0,
        )
        fun preparePictureDrawLane(
            entry: PictureStreamEntryDraftV1.Draw,
            sourceGeometry: W6bSourceGeometryV1,
            ownerContext: W6bSourceGeometryV1?,
            target: PlanResourceId,
            sourceOnly: Boolean,
        ): Pair<OccurrenceSourceInputV1, SourceDeferredRenderConstructionV4> {
                val captured = requireNotNull(entry.source.sourceDraw)
            val domain = sourceGeometry.copyDeviceBoundsI32()
            // A filter-owned Picture aggregate is already rooted in its exact source
            // context.  Its child scene transform must compose from that mapping, rather
            // than restart at the captured Picture-local origin while its cull is mapped
            // through the filter source below in pictureAggregateDomain.
            val enclosing = ownerContext?.let { source ->
                requireNotNull(source.mapping.copyLocalToDeviceF64().timesCheckedOrNull(
                    composeInOrderF64(entry.source.outerPictures().map { it.transform }),
                )) { "Picture source transform cannot be composed in finite F64." }
            } ?: composeInOrderF64(entry.source.outerPictures().map { it.transform })
            val evaluation = requireNotNull(enclosing.timesCheckedOrNull(captured.transform.toMatrix3x3F64()))
            val input = OccurrenceSourceInputV1(entry.plannedCommandId, entry.locator, entry.source,
                requireNotNull(LayerMappingF64.ofOrNull(evaluation, Point2I32(domain.left, domain.top))),
                enclosing, domain, domain, entry.source.recordedInnerClipWithoutCull(), ClipStackNode.Empty, target,
                entry.plannedCommandId.valueI32, sourceOnly)
            val compiler = CapabilityCompilerChain.of(listOf(W5bVerticesPlanCompiler(runtimeCatalog),
                W5bPointPlanCompiler(runtimeCatalog), W5eImagePlanCompiler(), W3SolidRectPlanCompiler(),
                W4aAnalyticRectPlanCompiler(), W4bAnalyticRRectPlanCompiler(), W4cPathFillPlanCompiler(),
                W4dPathStrokePlanCompiler()), runtimeCatalog)
            val lane = when (val result = compiler.constructSourceLanes(input, caps, budget)) {
                is org.graphiks.kanvas.render.ir.RenderPlanResult.Ready -> result.plan.single()
                else -> {
                    val diagnostics = when (result) {
                        is org.graphiks.kanvas.render.ir.RenderPlanResult.GapOnPromotedScope -> result.diagnostics
                        is org.graphiks.kanvas.render.ir.RenderPlanResult.GapNotMigrated -> result.diagnostics
                        is org.graphiks.kanvas.render.ir.RenderPlanResult.InvalidScene -> result.diagnostics
                        is org.graphiks.kanvas.render.ir.RenderPlanResult.ResourceLimitExceeded -> result.diagnostics
                    }
                    throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6aPlanDiagnostics.UnsupportedChild,
                        "Occurrence ${entry.plannedCommandId} has no W4/W5 lane: ${diagnostics.joinToString { it.code.value + ": " + it.message }}"))
                }
            }
            return input to lane
        }

        data class PictureAggregateDomain(
            val localToDeviceF64: Matrix3x3F64,
            val mapping: LayerMappingF64,
            val cullContentDeviceI32: RectI32,
            val knownContentDeviceI32: RectI32?,
            val demandDeviceI32: RectI32,
            val sourceDeviceI32: RectI32,
        )
        fun pictureAggregateDomain(
            aggregate: PictureStreamAggregateDraftV1,
            parent: W6bSourceGeometryV1,
            filterOwnerSource: W6bSourceGeometryV1?,
        ): PictureAggregateDomain {
            (aggregate.owner as? PictureStreamAggregateDraftOwnerV1.FilterPicture)?.let { owner ->
                val localToDevice = owner.source.sourceDraw?.let { carrier ->
                    val carrierToSource = composeInOrderF64(owner.source.outerPictures().map { it.transform } + carrier.transform)
                    requireNotNull(parent.mapping.copyLocalToDeviceF64().timesCheckedOrNull(carrierToSource))
                } ?: parent.mapping.copyLocalToDeviceF64()
                // The emitter owns the one F64 crop projection for this filter context.
                // Do not remap it here: the aggregate only chooses a target origin and
                // carries that sealed content through its descendants.
                val known = owner.copyKnownContentDeviceI32()
                val sourceDomain = owner.copySourceDomainDeviceI32()
                val demand = parent.copyDesiredOutputDeviceI32() ?: parent.copyDeviceBoundsI32()
                // A consumer clip is a terminal demand, not a source crop: retaining the
                // known Picture content lets an outer blur produce its halo beyond cull.
                val source = sourceDomain.copy()
                val mapping = LayerMappingF64.ofOrNull(localToDevice, Point2I32(source.left, source.top))
                    ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.InvalidBounds,
                        "W6d filter Picture source mapping is non-invertible.",
                    ))
                return PictureAggregateDomain(localToDevice, mapping, sourceDomain, known, demand, source)
            }
            val outerPictures = aggregate.source.outerPictures()
            // Captured transforms map directly to root device coordinates. Target rebasing
            // happens only in LayerMappingF64, never by composing the parent twice.
            val outerTransforms = outerPictures.map { it.transform } + aggregate.draw.transform
            val outer = try {
                composeInOrderF64(outerTransforms)
            } catch (_: IllegalArgumentException) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "Picture outer transform cannot be composed in finite F64.",
                ))
            }
            val localToDevice = filterOwnerSource?.let { source ->
                requireNotNull(source.mapping.copyLocalToDeviceF64().timesCheckedOrNull(outer)) {
                    "Picture outer transform cannot be composed in finite F64."
                }
            } ?: outer
            val picture = aggregate.draw.geometry as? GeometryNode.Picture
                ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6aPlanDiagnostics.UnsupportedChild, "Picture aggregate lost its captured Picture geometry.",
                ))
            val cull = localToDevice.mapRectBoundsF64OrNull(RectF64(
                picture.copyCullRect().left.toDouble(), picture.copyCullRect().top.toDouble(),
                picture.copyCullRect().right.toDouble(), picture.copyCullRect().bottom.toDouble(),
            ))?.roundOutToRectI32OrNull() ?: throw W6bFilterGraphConstruction.ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                    "Picture cull cannot be projected to checked I32 device texels."),
            )
            val parentDomain = parent.copyDeviceBoundsI32()
            val enclosing = composeInOrderF64(outerPictures.map { it.transform })
            val deferredDemand = when (val clip = aggregate.source.recordedInnerClipWithoutCull()) {
                ClipStackNode.Empty, is ClipStackNode.Operations -> parentDomain.copy()
                is ClipStackNode.DeviceRect -> clip.copyBounds().let { bounds ->
                    enclosing.mapRectBoundsF64OrNull(RectF64(bounds.left.toDouble(), bounds.top.toDouble(),
                        bounds.right.toDouble(), bounds.bottom.toDouble()))?.roundOutToRectI32OrNull()
                        ?.let { rounded -> intersect(rounded, parentDomain) }
                }
            }
            // An empty clip is a later terminal no-op.  Keeping its source demand conservative
            // is intentional until Task 3 materializes the typed deferred clip.
            val demand = deferredDemand ?: parentDomain.copy()
            val reverseMapping = LayerMappingF64.ofOrNull(localToDevice, Point2I32(demand.left, demand.top))
                ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "Picture reverse-demand mapping is non-invertible.",
                ))
            // A spatial no-op requests no source texels.  The aggregate still needs a non-empty
            // transaction target, so retain its cull rectangle without turning null into demand.
            val source = W6bFilterGraphConstruction.reverseInputDemand(aggregate.filterOccurrence, demand, reverseMapping)
                ?: cull
            val known = intersect(cull, source)
            val mapping = LayerMappingF64.ofOrNull(localToDevice, Point2I32(source.left, source.top))
                ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "Picture evaluation mapping is non-invertible.",
                ))
            return PictureAggregateDomain(localToDevice, mapping, cull, known, demand, source)
        }

        fun pictureColorBounds(
            sourceGeometry: W6bSourceGeometryV1,
            clip: ClipStackNode,
            transform: org.graphiks.math.matrix.Matrix3x3F32?,
        ): RectI32? {
            val extent = sourceGeometry.copyExtentI32()
            val targetBounds = RectI32(0, 0, extent.width, extent.height)
            fun unsupportedScissor(): Nothing = throw W6bFilterGraphConstruction.ConstructionFailure(
                W6bFilterDiagnostics.refusal(
                    W6aPlanDiagnostics.UnsupportedChild,
                    "Picture DrawColor clip has no exact pixel-aligned scissor representation.",
                ),
            )
            fun isAxisAlignedScissor(matrix: Matrix3x3F64): Boolean = matrix.isFinite() &&
                matrix.persp0F64 == 0.0 && matrix.persp1F64 == 0.0 && matrix.persp2F64 == 1.0 &&
                ((matrix.kxF64 == 0.0 && matrix.kyF64 == 0.0) ||
                    (matrix.sxF64 == 0.0 && matrix.syF64 == 0.0))
            fun exactTargetScissor(clipToDevice: Matrix3x3F64, bounds: RectF64): RectI32 {
                if (!isAxisAlignedScissor(clipToDevice)) unsupportedScissor()
                val device = clipToDevice.mapRectBoundsF64OrNull(bounds)?.toExactI32OrNull() ?: unsupportedScissor()
                return sourceGeometry.mapping.mapDeviceRectToTargetI32OrNull(
                    device, sourceGeometry.originDeviceI32,
                ) ?: unsupportedScissor()
            }
            transform?.let { matrix ->
                if (!listOf(matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty,
                        matrix.persp0, matrix.persp1, matrix.persp2).all(Float::isFinite)) {
                    throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.InvalidBounds, "Picture DrawColor transform is non-finite.",
                    ))
                }
            }
            val clippedBounds = when (clip) {
                ClipStackNode.Empty -> targetBounds
                is ClipStackNode.DeviceRect -> {
                    val bounds = clip.copyBounds()
                    if (bounds.isEmpty) null else intersect(targetBounds, exactTargetScissor(
                        sourceGeometry.mapping.copyLocalToDeviceF64(),
                        RectF64(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
                    ))
                }
                is ClipStackNode.Operations -> {
                    clip.fold(targetBounds.copy()) { accumulated, entry ->
                        val geometry = entry.geometry as? GeometryNode.Rect
                            ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                                W6aPlanDiagnostics.UnsupportedChild,
                                "Picture DrawColor requires rectangular intersect clips.",
                            ))
                        if (entry.operation != ClipOperation.INTERSECT) {
                            throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                                W6aPlanDiagnostics.UnsupportedChild,
                                "Picture DrawColor clip has no exact frozen scissor representation.",
                            ))
                        }
                        val transform = (entry.transform as? ClipTransformSnapshot.Known)?.copyMatrixF32()
                            ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                                W6aPlanDiagnostics.UnsupportedChild,
                                "Picture DrawColor clip lacks a captured transform.",
                            ))
                        val bounds = geometry.copyBounds()
                        val clipToDevice = sourceGeometry.mapping.copyLocalToDeviceF64()
                            .timesCheckedOrNull(transform.toMatrix3x3F64()) ?: unsupportedScissor()
                        accumulated.takeIf { it.intersect(exactTargetScissor(clipToDevice, RectF64(
                            bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
                        ))) }
                            ?: RectI32(0, 0, 0, 0)
                    }.takeUnless(RectI32::isEmpty)
                }
            }
            return clippedBounds
        }

        fun prepareFilterPicture(
            occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
            bound: W6bBoundFilterOperationV1,
            sourceContext: W6bSourceGeometryV1,
        ): W6bPreparedPictureSourceV1? {
            val nodeId = bound.id
            val node = bound.node as org.graphiks.kanvas.render.ir.CapturedFilterNodeV1.Picture
            val commands = node.scene.toList()
            if (commands.none { command -> command is SceneCommand.Draw || command is SceneCommand.Clear ||
                    command is SceneCommand.DrawColor || command is SceneCommand.BeginLayer }) {
                return null
            } else {
            val cullLocal = node.copyCullRect().let { bounds -> RectF64(
                bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
            ) }
            val sourceLocal = node.copySource()?.let { bounds -> RectF64(
                bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
            ) }
            if (!cullLocal.isFinite() || sourceLocal?.isFinite() == false) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d Picture source has invalid F64 geometry.",
                ))
            }
            val effectiveLocal = cullLocal.intersectF64OrNull(sourceLocal ?: cullLocal)
                ?: return null
            // Draw-owned filters compose their captured carrier transform once. A BeginLayer
            // filter has no DrawNode by design: its already-frozen SourceBinding mapping is the
            // layer context and remains the sole geometry authority for its Picture source.
            val contentMapping = occurrence.source.sourceDraw?.let { carrier ->
                val carrierToSource = composeInOrderF64(occurrence.source.outerPictures().map { it.transform } + carrier.transform)
                val localToDevice = requireNotNull(sourceContext.mapping.copyLocalToDeviceF64().timesCheckedOrNull(carrierToSource)) {
                    "W6d filter Picture carrier transform cannot be composed in finite F64."
                }
                LayerMappingF64.ofOrNull(localToDevice, Point2I32.Origin)
                    ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.InvalidBounds, "W6d Picture source mapping is non-invertible.",
                    ))
            } ?: sourceContext.mapping
            val contentDeviceF64 = contentMapping.mapLocalRectToDeviceF64OrNull(effectiveLocal)
                ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d Picture crop cannot be mapped to finite device coordinates.",
                ))
            val knownContentDeviceI32 = contentDeviceF64.roundOutToRectI32OrNull()
                ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d Picture crop cannot fit checked I32 texels.",
                ))
            val sourceDomainDeviceI32 = if (sourceLocal == null) knownContentDeviceI32 else
                contentMapping.mapLocalRectToDeviceI32OrNull(cullLocal)
                    ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.InvalidBounds, "W6d Picture cull cannot fit checked I32 texels.",
                    ))
            val draft = pictureDiscovery.prepareFilterRoot(nodeId, node, occurrence, knownContentDeviceI32,
                sourceDomainDeviceI32, contentDeviceF64)
            val mapping = requireNotNull(LayerMappingF64.ofOrNull(contentMapping.copyLocalToDeviceF64(),
                Point2I32(sourceDomainDeviceI32.left, sourceDomainDeviceI32.top)))
            val geometry = W6bRecipeSourceV1(W6bRecipeSymbolV1(0),
                SizeI32(sourceDomainDeviceI32.width(), sourceDomainDeviceI32.height()),
                Point2I32(sourceDomainDeviceI32.left, sourceDomainDeviceI32.top), mapping,
                knownContentDeviceI32, sourceContext.copyDesiredOutputDeviceI32() ?: sourceContext.copyDeviceBoundsI32(),
                sourceDomainDeviceI32, knownContentDeviceI32)
            return W6bPreparedPictureSourceV1(draft, geometry, contentDeviceF64)
            }
        }
        val filterLayersByBegin = filterOccurrences.filter { it.isLayerOccurrence && !it.isBackdropInitialization }
            .associateBy { it.insertionCommandIndexI32 }
        val backdropFiltersByBegin = filterOccurrences.filter(W6bFilterGraphConstruction.PositiveOccurrence::isBackdropInitialization)
            .associateBy { it.insertionCommandIndexI32 }
        // A direct mask blur inside an explicit W6a layer composites its frozen auto-layer
        // back into that layer before the ordinary restore.  Reserve its already-frozen halo
        // in the parent target now; otherwise the W6a content-sized target clips the terminal.
        val directBlurByCommand = filterOccurrences.filter { occurrence ->
            !occurrence.isLayerOccurrence && !occurrence.isPictureOccurrence && occurrence.mask is MaskFilterNode.Blur
        }.associateBy { occurrence -> occurrence.insertionCommandIndexI32 }
        val directKnownByScope = arrayOfNulls<RectI32>(occurrences.size)
        bindings.forEach { binding ->
            val scopeIdI32 = binding.scopeI32 ?: return@forEach
            RenderGraph.visualDraws(binding.source.passes()).forEach { draw ->
                intersect(w6aRasterBoundsI32(draw), w6aScissorI32(draw))?.let { bounds ->
                    val frozenAutoLayerBounds = directBlurByCommand[draw.commandIndex]?.let { occurrence ->
                        W6bFilterGraphConstruction.reverseInputDemand(occurrence, bounds, binding.occurrenceInput?.mapping) ?: bounds
                    } ?: bounds
                    directKnownByScope[scopeIdI32] = unionOrNull(directKnownByScope[scopeIdI32], frozenAutoLayerBounds)
                }
            }
        }

        // Desired restore output is semantic and flows from the root clip downward.  It must
        // never be constrained by a parent's later, content-sized physical allocation.
        val desiredOutputByScope = arrayOfNulls<RectI32>(occurrences.size)
        occurrences.forEach { occurrence ->
            val parentDesired = if (occurrence.parentIdI32 == null) rootDomainDeviceI32
                else desiredOutputByScope[occurrence.parentIdI32]
            desiredOutputByScope[occurrence.idI32] = parentDesired?.let { desiredOutput(occurrence.descriptor, it) }
        }

        // A backdrop or filtered initWithPrevious layer reads its immediate parent before the
        // layer target exists.  Its copy must therefore cover the frozen filter's reverse input
        // demand, while desiredOutput remains the independent restore clip.  The parent desired
        // domain is the only available source outside an inner restrictive clip.
        val snapshotInputByScope = arrayOfNulls<RectI32>(occurrences.size)
        occurrences.forEach { occurrence ->
            val desired = desiredOutputByScope[occurrence.idI32] ?: return@forEach
            val filter = backdropFiltersByBegin[occurrence.beginCommandIndexI32] ?: if (occurrence.descriptor.initWithPrevious)
                filterLayersByBegin[occurrence.beginCommandIndexI32] else null
            if (filter != null) {
                val transform = occurrence.descriptor.transform
                val localToDevice = Matrix3x3F64(
                    transform.sx.toDouble(), transform.kx.toDouble(), transform.tx.toDouble(),
                    transform.ky.toDouble(), transform.sy.toDouble(), transform.ty.toDouble(),
                    transform.persp0.toDouble(), transform.persp1.toDouble(), transform.persp2.toDouble(),
                )
                val mapping = LayerMappingF64.ofOrNull(localToDevice, Point2I32(desired.left, desired.top))
                    ?: throw IllegalArgumentException(W6aPlanDiagnostics.NonFiniteTransform)
                val parentDesired = occurrence.parentIdI32?.let(desiredOutputByScope::get) ?: rootDomainDeviceI32
                snapshotInputByScope[occurrence.idI32] = W6bFilterGraphConstruction.reverseInputDemand(filter, desired, mapping)
                    ?.let { input -> intersect(input, parentDesired) }
            }
        }

        // Restore output is post-order.  This is an ID-indexed table rather than recursive
        // descendant walks, so a legal GraphLimits depth has linear work and stack use.
        val knownContentByScope = arrayOfNulls<RectI32>(occurrences.size)
        val producedOutputByScope = arrayOfNulls<RectI32>(occurrences.size)
        val geometryByScope = arrayOfNulls<W6aScopeGeometry>(occurrences.size)
        val evaluatedImagesByOccurrence = java.util.IdentityHashMap<W6bFilterGraphConstruction.PositiveOccurrence, W6bEvaluatedFilterRecipeV1>()
        val evaluatedMasksByOccurrence = java.util.IdentityHashMap<W6bFilterGraphConstruction.PositiveOccurrence, W6bEvaluatedFilterRecipeV1>()
        for (indexI32 in occurrences.indices.reversed()) {
            val occurrence = occurrences[indexI32]
            val desired = desiredOutputByScope[occurrence.idI32]
            val snapshotInput = snapshotInputByScope[occurrence.idI32]
            val filter = filterLayersByBegin[occurrence.beginCommandIndexI32]
            val transform = occurrence.descriptor.transform
            val localToDevice = Matrix3x3F64(
                transform.sx.toDouble(), transform.kx.toDouble(), transform.tx.toDouble(),
                transform.ky.toDouble(), transform.sy.toDouble(), transform.ty.toDouble(),
                transform.persp0.toDouble(), transform.persp1.toDouble(), transform.persp2.toDouble())
            val demandMapping = desired?.let {
                LayerMappingF64.ofOrNull(localToDevice, Point2I32(it.left, it.top))
                    ?: throw IllegalArgumentException(W6aPlanDiagnostics.NonFiniteTransform)
            }
            val recipe = if (filter != null && desired != null) W6bFilterGraphConstruction.bindOccurrenceRecipe(
                filter, desired, requireNotNull(demandMapping)) else null
            val sourceDemand = if (recipe == null) desired else recipe.copyRequiredInputDeviceI32()
            var known = snapshotInput?.copy() ?: if (occurrence.descriptor.initWithPrevious ||
                occurrence.descriptor.backdrop !is EffectStack.Empty) desired?.copy() else directKnownByScope[occurrence.idI32]
            var physicalInput = unionOrNull(directKnownByScope[occurrence.idI32], snapshotInput)
            occurrence.childIdsI32.forEach { child ->
                known = unionOrNull(known, producedOutputByScope[child])
                physicalInput = unionOrNull(physicalInput, producedOutputByScope[child])
            }
            if (snapshotInput == null) known = sourceDemand?.let { demand -> known?.let { intersect(it, demand) } }
            knownContentByScope[occurrence.idI32] = known
            val sourceGeometry = sealGeometry(occurrence, restoreFactsByScope.getValue(occurrence.idI32),
                desired, known, null, physicalInput, snapshotInput, sourceDemand)
            val domain = sourceGeometry.compositeDomainDeviceI32
            val evaluation = if (recipe != null && domain != null) {
                var facts = W6bFilterSourceFactsV1(domain, known, requireNotNull(desired), requireNotNull(sourceGeometry.mapping),
                    { bound, context -> prepareFilterPicture(requireNotNull(filter), bound, context) })
                if (filter?.mask != null) {
                    val mask = recipe.evaluateMask(facts)
                    evaluatedMasksByOccurrence[filter] = mask
                    val output = mask.output
                    facts = W6bFilterSourceFactsV1(output.copyDeviceBoundsI32(), output.copyKnownContentDeviceI32(),
                        output.copyDesiredOutputDeviceI32() ?: requireNotNull(desired), output.mapping,
                        { bound, context -> prepareFilterPicture(filter, bound, context) })
                }
                if (filter?.root != null) recipe.evaluate(facts, runtimeCatalog).also {
                    evaluatedImagesByOccurrence[filter] = it
                } else evaluatedMasksByOccurrence[filter]
            } else null
            val produced = when {
                desired == null -> null
                restoreFactsByScope.getValue(occurrence.idI32).restoreAffectsTransparentBlack -> desired.copy()
                evaluation != null -> evaluation.copyProducedOutputDeviceI32()?.let { intersect(it, desired) }
                filter != null -> null
                else -> known?.let { intersect(it, desired) }
            }
            producedOutputByScope[occurrence.idI32] = produced
            geometryByScope[occurrence.idI32] = W6aScopeGeometry(occurrence, sourceGeometry.mapping,
                sourceGeometry.requestedHintDeviceF64, sourceGeometry.knownContentDeviceI32,
                sourceGeometry.desiredOutputDeviceI32, sourceGeometry.requiredInputDeviceI32, produced,
                sourceGeometry.compositeDomainDeviceI32)
        }
        geometries = immutableList(occurrences.map { requireNotNull(geometryByScope[it.idI32]) })
        val activeByScope = geometries.filterNot(W6aScopeGeometry::isElided).associateBy { it.occurrence.idI32 }
        activeByScope.values.forEach { geometry -> admitRestoreBindings(restoreFactsByScope.getValue(geometry.occurrence.idI32)) }

        val passes = mutableListOf<PlanPass>()
        val framePassSink = W6bFilterGraphConstruction.W6FramePassSinkV1(passes)
        val filterResourceSpecs = mutableListOf<W6bFilterGraphConstruction.ResourceSpec>()
        val coverageDepthExtents = linkedMapOf<PlanResourceId, SizeI32>()
        var nextCoverageDepthOrdinalI32 = lanes.size + 1
        val steps = mutableListOf<LayerExecutionStepV1>()
        val scopePlans = linkedMapOf<Int, LayerScopePlanV1>()
        /** Picture-local layers share the existing W6a target/restore authority, never a replay VM. */
        data class PictureLayerExecutionScope(val id: LayerScopeIdI32, val target: PlanResourceId)
        var nextPictureLayerScopeI32 = occurrences.size
        var nextPictureLayerTargetOrdinalI32 = occurrences.size
        val initializationByScope = linkedMapOf<Int, LayerInitializationPlanV1>()
        val versions = mutableMapOf<PlanResourceId, Long>()
        var uniformCursorI64 = 16L

        val sourceBindingsById = linkedMapOf<PlanResourceId, W6bFilterGraphConstruction.SourceBinding>()
        val sourceSpecs = mutableListOf<W6bFilterGraphConstruction.ResourceSpec>()
        /** Number of enclosing Picture transforms already represented by a target mapping. */
        var nextFilterSourceOrdinalI32 = 0
        fun targetFor(scopeI32: Int?): PlanResourceId = scopeI32?.let { planResourceId(PlanResourceRole.LayerTarget, it) } ?: root
        fun targetExtent(target: PlanResourceId): SizeI32 = sourceBindingsById[target]?.copyExtentI32() ?: if (target == root) extent.copy() else
            activeByScope.getValue(target.value.substringAfter(':').toInt()).targetExtentI32()
        fun targetOriginDevice(target: PlanResourceId): Point2I32 = sourceBindingsById[target]?.originDeviceI32 ?: if (target == root) Point2I32.Origin else
            activeByScope.getValue(target.value.substringAfter(':').toInt()).mapping!!.copyLayerOriginDeviceI32()
        fun filterSource(target: PlanResourceId): W6bFilterGraphConstruction.SourceBinding {
            sourceBindingsById[target]?.let { return it }
            val origin = targetOriginDevice(target)
            val mapping = if (target == root) requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), origin)) else
                activeByScope.getValue(target.value.substringAfter(':').toInt()).mapping!!
            val domain = RectI32(origin.x, origin.y,
                Math.addExact(origin.x, targetExtent(target).width), Math.addExact(origin.y, targetExtent(target).height))
            val geometry = target.takeIf { it != root }?.let { activeByScope.getValue(it.value.substringAfter(':').toInt()) }
            val binding = W6bFilterGraphConstruction.SourceBinding(target, targetExtent(target), origin, mapping,
                geometry?.knownContentDeviceI32 ?: domain, geometry?.desiredOutputDeviceI32 ?: domain,
                geometry?.requiredInputDeviceI32 ?: domain, geometry?.producedOutputDeviceI32 ?: geometry?.knownContentDeviceI32 ?: domain)
            return binding
        }
        fun targetDeviceBounds(target: PlanResourceId): RectI32 = filterSource(target).copyDeviceBoundsI32()
        fun allocateOccurrenceSource(
            domain: RectI32,
            parentTarget: PlanResourceId,
            role: PlanResourceRole = PlanResourceRole.FilterSource,
            copyDestination: Boolean = false,
            mappingLocalToDeviceF64: Matrix3x3F64? = null,
            knownContentDeviceI32: RectI32? = domain,
            desiredOutputDeviceI32: RectI32? = domain,
            requiredInputDeviceI32: RectI32? = domain,
            producedOutputDeviceI32: RectI32? = knownContentDeviceI32,
        ): W6bFilterGraphConstruction.SourceBinding {
            val parent = filterSource(parentTarget)
            val mapping = requireNotNull(LayerMappingF64.ofOrNull(
                mappingLocalToDeviceF64?.copy() ?: parent.mapping.copyLocalToDeviceF64(),
                Point2I32(domain.left, domain.top),
            ))
            val id = planResourceId(role, nextFilterSourceOrdinalI32)
            nextFilterSourceOrdinalI32 = Math.addExact(nextFilterSourceOrdinalI32, 1)
            val binding = W6bFilterGraphConstruction.SourceBinding(id, SizeI32(domain.width(), domain.height()),
                Point2I32(domain.left, domain.top), mapping, knownContentDeviceI32,
                desiredOutputDeviceI32, requiredInputDeviceI32, producedOutputDeviceI32)
            sourceSpecs += W6bFilterGraphConstruction.ResourceSpec(
                id,
                role,
                binding.copyExtentI32(),
                buildSet {
                    if (copyDestination) add(PlanResourceUsage.CopyDestination)
                    // An aggregate may be the immediate parent of a captured initWithPrevious
                    // layer.  Retain the ordinary W6a copy-source usage conservatively.
                    if (role == PlanResourceRole.PictureAggregateSource) add(PlanResourceUsage.CopySource)
                },
            )
            sourceBindingsById[id] = binding
            return binding
        }
        /** Allocates a true W6a child target for a BeginLayer recorded inside a Picture stream. */
        fun allocatePictureLayerTarget(
            domain: RectI32,
            parentTarget: PlanResourceId,
            mappingLocalToDeviceF64: Matrix3x3F64? = null,
        ): W6bFilterGraphConstruction.SourceBinding {
            val parent = filterSource(parentTarget)
            val id = planResourceId(PlanResourceRole.LayerTarget,
                nextPictureLayerTargetOrdinalI32.also { nextPictureLayerTargetOrdinalI32 = Math.addExact(it, 1) })
            val binding = W6bFilterGraphConstruction.SourceBinding(
                id,
                SizeI32(domain.width(), domain.height()),
                Point2I32(domain.left, domain.top),
                requireNotNull(LayerMappingF64.ofOrNull(
                    mappingLocalToDeviceF64?.copy() ?: parent.mapping.copyLocalToDeviceF64(),
                    Point2I32(domain.left, domain.top),
                )),
                parent.copyKnownContentDeviceI32(),
                parent.copyDesiredOutputDeviceI32(),
                parent.copyRequiredInputDeviceI32(),
                parent.copyProducedOutputDeviceI32(),
            )
            sourceSpecs += W6bFilterGraphConstruction.ResourceSpec(
                id,
                PlanResourceRole.LayerTarget,
                binding.copyExtentI32(),
                // This target can be the immediate parent of another recorded layer whose
                // initWithPrevious reads the parent's current generation.
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.CopySource),
            )
            sourceBindingsById[id] = binding
            return binding
        }
        /** W5 shading starts from the post-mask coverage domain, including any expanded halo. */
        fun allocateShadedOccurrenceSource(
            coverage: W6bFilterGraphConstruction.SourceBinding,
            parentTarget: PlanResourceId,
        ): W6bFilterGraphConstruction.SourceBinding {
            val domain = coverage.copyDeviceBoundsI32()
            return allocateOccurrenceSource(
                domain,
                parentTarget,
                PlanResourceRole.FilterSource,
                mappingLocalToDeviceF64 = coverage.mapping.copyLocalToDeviceF64(),
                knownContentDeviceI32 = coverage.copyKnownContentDeviceI32(),
                desiredOutputDeviceI32 = coverage.copyDesiredOutputDeviceI32() ?: domain,
                // The W5 source stage is evaluated over the styled coverage's whole
                // published output domain.  Retaining the preceding blur pass's input
                // demand here would rebase this material texture at the unexpanded
                // raw-coverage origin and drop the leading halo on materialization.
                requiredInputDeviceI32 = domain,
                producedOutputDeviceI32 = coverage.copyProducedOutputDeviceI32(),
            )
        }
        val dataByCommand = linkedMapOf<Int, PlanDrawDataResources>()
        // A FilterCoverage source and its subsequent W5 source draw execute in one submitted
        // frame.  They therefore cannot share an upload allocation: queue writes are performed
        // before that submission, not between the two render passes.  These are still the same
        // frozen W4 producer bytes, merely separate plan-owned buffer allocations.
        val dataRowsById = linkedMapOf<PlanResourceId, PlanResource>()
        val coverageRasterData = mutableListOf<Pair<PlanDrawDataResources, PlanDrawDataResources>>()
        var nextDataOrdinalI32 = Math.addExact(lanes.size, 1)
        val laneResourceIds = lanes.mapIndexed { laneI32, lane -> lane.resources().associate { row -> row.id to when (row.role) {
            PlanResourceRole.LogicalTarget -> targetFor(bindings[laneI32].scopeI32)
            PlanResourceRole.ReadbackStaging -> staging
            PlanResourceRole.DestinationSnapshot -> planResourceId(row.role, occurrences.size + laneI32)
            PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil ->
                planResourceId(row.role, laneI32 + 1)
            else -> row.id // Replaced below by distinct per-role graph ordinals.
        } }.toMutableMap() }.toMutableList()
        val nextOrdinal = mutableMapOf<PlanResourceRole, Int>()
        lanes.forEachIndexed { laneI32, lane -> lane.resources().filter { it.role !in setOf(PlanResourceRole.LogicalTarget,
            PlanResourceRole.ReadbackStaging, PlanResourceRole.DestinationSnapshot, PlanResourceRole.VertexData,
            PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil) }.forEach { row ->
            val ordinal = nextOrdinal[row.role] ?: 0
            laneResourceIds[laneI32][row.id] = planResourceId(row.role, ordinal)
            nextOrdinal[row.role] = Math.addExact(ordinal, 1)
        } }
        val nativeByLane = linkedMapOf<Int, LinkedHashMap<PlanPassId, PlanPass>>()
        /** A filtered path lane owns the occurrence target, never its mutable parent target. */
        val physicalTargetByLane = mutableMapOf<Int, PlanResourceId>()
        lanes.forEachIndexed { laneI32, lane ->
            val data = lane.resources().filter { it.role in setOf(PlanResourceRole.VertexData,
                PlanResourceRole.IndexData, PlanResourceRole.UniformData) }
            if (data.isNotEmpty()) {
                require(data.map { it.role }.toSet().size == 3 && data.size == 3)
                val binding = PlanDrawDataResources(planResourceId(PlanResourceRole.VertexData, laneI32 + 1),
                    planResourceId(PlanResourceRole.IndexData, laneI32 + 1),
                    planResourceId(PlanResourceRole.UniformData, laneI32 + 1))
                dataRowsById[binding.vertex] = data.single { it.role == PlanResourceRole.VertexData }
                dataRowsById[binding.index] = data.single { it.role == PlanResourceRole.IndexData }
                dataRowsById[binding.uniform] = data.single { it.role == PlanResourceRole.UniformData }
                RenderGraph.visualDraws(lane.passes()).forEach { dataByCommand[it.commandIndex] = binding }
            }
        }
        fun allocateCoverageRasterData(source: PlanDrawDataResources?): PlanDrawDataResources? = source?.let { original ->
            val ordinal = nextDataOrdinalI32.also { nextDataOrdinalI32 = Math.addExact(it, 1) }
            require(listOf(original.vertex, original.index, original.uniform).all(dataRowsById::containsKey))
            PlanDrawDataResources(
                planResourceId(PlanResourceRole.VertexData, ordinal),
                planResourceId(PlanResourceRole.IndexData, ordinal),
                planResourceId(PlanResourceRole.UniformData, ordinal),
            ).also { coverageRasterData += it to original }
        }
        fun appendRender(target: PlanResourceId, draws: List<PlanDraw>, clear: Boolean,
            coverageSource: PlanResourceId? = null, w6bMaskSourceBinding: PlanPass.W6bRasterCoverageBindingV1? = null,
            plannedCommandId: FramePlannedCommandIdI32? = null): PlanPass.RenderPass {
            val before = versions[target] ?: 0L
            val after = Math.addExact(before, draws.size.toLong())
            versions[target] = after
            return PlanPass.RenderPass(passes.size, target, draws,
                if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load,
                AttachmentStorePlan.Store, drawDataResources = draws.firstOrNull()?.let { dataByCommand[it.commandIndex] },
                destinationVersionAfter = DestinationVersionI64(after), coverageSource = coverageSource,
                w6bMaskSourceBinding = w6bMaskSourceBinding, plannedCommandId = plannedCommandId,
                materialDeviceOriginI32 = filterSource(target).originDeviceI32).also(passes::add)
        }

        val bindingsByCommand = bindings.associateBy { it.firstCommandIndexI32 }
        deferredFilterFailure?.let { throw it }
        val filterOccurrencesByInsertion = filterOccurrences.groupBy { it.insertionCommandIndexI32 }
        // One ordered, occurrence-local aggregate discovery owns nested Picture structure.  It
        // intentionally replaces the earlier leaf flattening: a filtered parent retains every
        // child (including unfiltered siblings) and an inline child remains in the current target.
        val pictureStreamAggregates = mutableListOf<PictureStreamAggregateV1>()
        data class DirectFilterSources(
            val coverage: W6bFilterGraphConstruction.SourceBinding,
        )
        fun directTerminalClip(occurrence: W6bFilterGraphConstruction.PositiveOccurrence): RectI32? = when (
            val clip = occurrence.source.recordedInnerClipWithoutCull().terminalDeferredClip()
        ) {
            ClipStackNode.Empty -> null
            is ClipStackNode.Operations -> throw W6bFilterGraphConstruction.ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.DirectTerminalClip,
                    "W6c direct filtered terminal requires an exact DeviceRect clip."),
            )
            is ClipStackNode.DeviceRect -> clip.copyBounds().let { bounds ->
                RectF64(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
                    .roundOutToRectI32OrNull() ?: throw W6bFilterGraphConstruction.ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6b direct terminal clip cannot be rounded into I32 texels."),
                )
            }
        }
        val directFilterSourceByCommand = linkedMapOf<Int, DirectFilterSources>()
        val directConsumerDemandCommands = linkedSetOf<Int>()
        filterOccurrences.filterNot { it.isLayerOccurrence || it.isPictureOccurrence }.forEach { occurrence ->
            // Reject an opaque terminal clip before allocating any direct filter source; W4e
            // retains ownership of complex clips on non-filtered routes.
            val terminalClip = directTerminalClip(occurrence)
            val binding = requireNotNull(bindingsByCommand[occurrence.insertionCommandIndexI32]) {
                "W6b direct occurrence has no W5 source generation."
            }
            val draw = RenderGraph.visualDraws(binding.source.passes()).single()
            val parentTarget = targetFor(binding.scopeI32)
            val targetBounds = targetDeviceBounds(parentTarget)
            // A direct occurrence's terminal clip is its downstream consumer.  Seal that
            // intersection before allocating coverage or freezing an unbounded lighting pass.
            val clippedConsumer = terminalClip?.let { intersect(targetBounds, it) }
            val terminalNoOp = terminalClip != null && clippedConsumer == null
            val consumerDomain = clippedConsumer ?: targetBounds
            val noOpDomain = RectI32(targetBounds.left, targetBounds.top,
                Math.addExact(targetBounds.left, 1), Math.addExact(targetBounds.top, 1))
            val inputDemandFilter = W6bFilterGraphConstruction.hasReverseInputDemandTerminal(occurrence)
            val consumerDemandFilter = W6bFilterGraphConstruction.hasConsumerDemandTerminal(occurrence)
            if (consumerDemandFilter) directConsumerDemandCommands += occurrence.insertionCommandIndexI32
            // The sampler recipe maps its public local lens with this captured draw transform.
            // Derive it before reverse demand so direct clipped Magnifier input matches the
            // immutable coordinates later used to freeze the filter pass.
            val sourceLocalToDeviceF64 = if (W6bFilterGraphConstruction.hasContentOutputSamplingTerminal(occurrence)) {
                occurrence.source.sourceDraw?.let { sourceDraw ->
                    val sourceToParent = composeInOrderF64(
                        occurrence.source.outerPictures().map { it.transform } + sourceDraw.transform,
                    )
                    requireNotNull(filterSource(parentTarget).mapping.copyLocalToDeviceF64()
                        .timesCheckedOrNull(sourceToParent)) {
                        "W6d direct source local-to-device mapping cannot be composed in finite F64."
                    }
                } ?: throw W6bFilterGraphConstruction.ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6d direct sampling filter requires its captured source draw mapping."),
                )
            } else null
            // The W5 wrapper represents the terminal consumer clip. Any filter with reverse
            // input demand must rasterize its pre-clip source before freezing its halo.
            val sourceGeometryDraw = if (inputDemandFilter) draw.withoutW6aTerminalClip() else draw
            val rasterInTarget = intersect(w6aRasterBoundsI32(sourceGeometryDraw), targetBounds)
            // Keep only the texels demanded by the frozen input halo, not the terminal
            // clip/scissor itself. A null terminal domain is a legal sealed no-op.
            val sourceDomain = when {
                terminalNoOp -> noOpDomain
                inputDemandFilter -> W6bFilterGraphConstruction.reverseInputDemand(
                    occurrence, consumerDomain,
                    sourceLocalToDeviceF64?.let { mapping ->
                        requireNotNull(LayerMappingF64.ofOrNull(mapping, filterSource(parentTarget).originDeviceI32))
                    } ?: filterSource(parentTarget).mapping,
                )?.let { demand -> rasterInTarget?.let { raster -> intersect(raster, demand) } } ?: noOpDomain
                else -> rasterInTarget?.let { raster -> intersect(raster, w6aScissorI32(draw)) } ?: noOpDomain
            }
            // Lighting affects transparent black. Its physical child remains tightly rasterized,
            // while its semantic output is the frozen terminal consumer. Sampling filters only
            // widen their input domain and retain their content-sized output contract.
            val desired = if (consumerDemandFilter && !terminalNoOp) consumerDomain else sourceDomain
            // Only the samplers that freeze target-local coordinates need the draw mapping.
            // Rebinding Picture/Matrix to it would compose a carrier transform twice: those
            // established paths retain the parent source mapping below.
            directFilterSourceByCommand[occurrence.insertionCommandIndexI32] = DirectFilterSources(
                allocateOccurrenceSource(sourceDomain, parentTarget, PlanResourceRole.CoverageSource,
                    mappingLocalToDeviceF64 = sourceLocalToDeviceF64,
                    desiredOutputDeviceI32 = desired, requiredInputDeviceI32 = sourceDomain),
            )
        }
        this.directConsumerDemandCommands = directConsumerDemandCommands
        /*
         * MaskShader is not a placeholder operation: the captured MaterialNode is normalized by
         * the same W5 source authority as the rest of the frame.  Its row is appended to this
         * frame's one FrameSourceLayoutV4 and is bound back to the exact occurrence at publish.
         * The synthetic Rect form is only a typed coordinate carrier for Layer/Picture coverage;
         * it retains the exact captured layer/Picture transform, clip, cull/bounds and Material.
        */
        val shaderMaterialSources = linkedMapOf<Int, MaterialSourceConstructionV4>()
        val graphTextureMaterialSources = linkedMapOf<PictureStreamAggregateIdI32, MaterialSourceConstructionV4>()
        fun captureMaskShaderMaterial(
            occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
            target: PlanResourceId,
        ) {
            if (occurrence.idI32 in shaderMaterialSources) return
            val mask = occurrence.mask as MaskFilterNode.Shader
            val domain = targetDeviceBounds(target)
            val original = occurrence.source.materialCoordinateDrawOrNull(mask.material)
            // A saveLayer has no original draw to carry its material coordinates.  This is the
            // same neutral W5 capture paint used by the direct fallback: it carries no second
            // material authority, and leaves the already-frozen mask material as the only source.
            val capturePaint = original?.paint?.copy(
                color = org.graphiks.math.color.ColorARGB.White,
                shader = mask.material,
                blendMode = BlendMode.SRC_OVER,
                blender = null,
                colorFilter = null,
                maskFilter = null,
                pathEffect = null,
                imageFilter = null,
            ) ?: PaintNode(
                org.graphiks.math.color.ColorARGB.White,
                mask.material,
                BlendMode.SRC_OVER,
                null, null, null, null, null,
                PaintStyleNode.FILL,
                0f,
                StrokeCapNode.BUTT,
                StrokeJoinNode.MITER,
                4f,
                false,
            )
            val materialDraw: DrawNode = if (original != null) {
                val picture = original.geometry as? GeometryNode.Picture
                original.copy(
                    geometry = picture?.let { GeometryNode.Rect.of(it.copyCullRect()) } ?: original.geometry,
                    material = mask.material,
                    paint = null,
                    effects = org.graphiks.kanvas.render.ir.EffectStack.Empty,
                    blend = BlendNode.SrcOver,
                    origin = if (picture == null) original.origin else DrawOrigin.RECT,
                    resource = null,
                    operationBlendMode = null,
                )
            } else if (occurrence.source.sourceDraw != null) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.UnsupportedFamily,
                    "W6b cannot compose an outer Picture clip without its captured transform.",
                ))
            } else {
                val descriptor = requireNotNull(occurrence.source.layerDescriptor)
                val local = descriptor.copyBounds() ?: org.graphiks.math.geometry.RectF32.ofLTRB(
                    domain.left.toFloat(), domain.top.toFloat(), domain.right.toFloat(), domain.bottom.toFloat(),
                )
                DrawNode(
                    GeometryNode.Rect.of(local),
                    mask.material,
                    CoverageRequest.DEFAULT,
                    descriptor.clip,
                    BlendNode.SrcOver,
                    org.graphiks.kanvas.render.ir.EffectStack.Empty,
                    descriptor.transform,
                    DrawOrigin.RECT,
                    paint = capturePaint,
                )
            }
            val normalized = EffectiveMaterialPlanner.normalizeSourcesV4(
                materialDraw,
                PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                domain,
                coverage = CoveragePlan.FullOrScissor,
                sample = SamplePlan.SingleSample,
                legacyGradientBoundsI32 = domain,
                runtimeCatalog = runtimeCatalog,
            )
            shaderMaterialSources[occurrence.idI32] = when (normalized) {
                is EffectiveMaterialPlanner.SourceNormalizationV4.Source -> normalized.captured
                EffectiveMaterialPlanner.SourceNormalizationV4.NoOp ->
                    throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.UnsupportedFamily,
                        "W6b MaskShader material is outside the existing W5 MaterialSourceConstructionV4 authority.",
                    ))
                is EffectiveMaterialPlanner.SourceNormalizationV4.Refused -> when (val captured =
                    MaterialSourceConstructionV4.capture(
                        materialDraw.copy(paint = capturePaint),
                        SourceCoordinatesV4.None,
                        org.graphiks.math.geometry.RectF32.ofLTRB(
                            domain.left.toFloat(), domain.top.toFloat(), domain.right.toFloat(), domain.bottom.toFloat(),
                        ),
                        BlendPlan.LegacySrcOverV1,
                        runtimeCatalog = runtimeCatalog,
                        composedV6 = true,
                    )) {
                    is SourceConstructionResultV4.Built -> captured.value
                    is SourceConstructionResultV4.Refused ->
                        throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                            W6bFilterDiagnostics.UnsupportedFamily,
                            "W6b MaskShader material is outside the existing W5 MaterialSourceConstructionV4 authority: ${captured.diagnosticCode}",
                        ))
                }
            }
        }
        filterOccurrences.filter { it.mask is MaskFilterNode.Shader }.forEach { occurrence ->
            // A layer recorded inside a Picture has no top-level W6a occurrence.  Its actual
            // target is allocated while the ordered Picture stream opens that layer below.
            if (occurrence.isLayerOccurrence && occurrence.outerPicturePathI32().isNotEmpty()) return@forEach
            val target = when {
                !occurrence.isLayerOccurrence && !occurrence.isPictureOccurrence ->
                    requireNotNull(directFilterSourceByCommand[occurrence.insertionCommandIndexI32]).coverage.resourceId
                occurrence.isLayerOccurrence -> targetFor(requireNotNull(occurrences.singleOrNull {
                    it.beginCommandIndexI32 == occurrence.insertionCommandIndexI32
                }).idI32)
                else -> targetFor(occurrences.filter {
                    it.beginCommandIndexI32 < occurrence.insertionCommandIndexI32 &&
                        occurrence.insertionCommandIndexI32 < it.endCommandIndexI32
                }.maxByOrNull { it.beginCommandIndexI32 }?.idI32)
            }
            captureMaskShaderMaterial(occurrence, target)
        }
        val filterCursor = W6bFilterGraphConstruction.FreezeCursor(0, 0, 0, 0, passes.size)
        lateinit var emitFilterPictureSource: W6bFilterGraphConstruction.FilterPictureSourceEmitterV1
        // Construction owns these snapshots until their aggregate receives the final immutable
        // admission fact, before the corresponding pass is published.
        val pictureTerminalScissorAuthorityByPassId = linkedMapOf<PlanPassId, PictureTerminalScissorAuthorityV1>()
        var nextPictureSnapshotI32 = Math.addExact(occurrences.size, filterScene?.graphLimits?.maxNodes ?: bindings.size)
        fun admitPictureTerminalScissor(
            sourceBounds: RectI32,
            destinationOrigin: Point2I32,
            destination: PlanResourceId,
            clip: ClipStackNode,
            clipMapping: Matrix3x3F64,
        ): PictureTerminalScissorAuthorityV1 {
            val sourceInDestination = try {
                RectI32(
                    destinationOrigin.x,
                    destinationOrigin.y,
                    Math.addExact(destinationOrigin.x, sourceBounds.width()),
                    Math.addExact(destinationOrigin.y, sourceBounds.height()),
                )
            } catch (_: ArithmeticException) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b Picture terminal source bounds overflow target-local I32 texels.",
                ))
            }
            val destinationDeviceOrigin = targetOriginDevice(destination)
            val (scissor, scissorAdmitted) = when (clip) {
                ClipStackNode.Empty -> sourceInDestination to true
                is ClipStackNode.DeviceRect -> {
                    val bounds = clip.copyBounds()
                    if (bounds.isEmpty) null to true else {
                        val device = clipMapping.mapRectBoundsF64OrNull(RectF64(
                            bounds.left.toDouble(), bounds.top.toDouble(),
                            bounds.right.toDouble(), bounds.bottom.toDouble(),
                        ))?.toExactI32OrNull()
                        val targetLocal = device?.let { exact -> try {
                            RectI32(
                                Math.subtractExact(exact.left, destinationDeviceOrigin.x),
                                Math.subtractExact(exact.top, destinationDeviceOrigin.y),
                                Math.subtractExact(exact.right, destinationDeviceOrigin.x),
                                Math.subtractExact(exact.bottom, destinationDeviceOrigin.y),
                            )
                        } catch (_: ArithmeticException) {
                            null
                        } }
                        targetLocal?.let { local ->
                            sourceInDestination.copy().let { clipped -> clipped.takeIf { it.intersect(local) } }
                        } to (targetLocal != null)
                    }
                }
                is ClipStackNode.Operations -> sourceInDestination to false
            }
            return PictureTerminalScissorAuthorityV1(scissor, scissorAdmitted, scissor == null)
        }
        fun freezePictureTerminal(
            planned: FramePlannedCommandIdI32,
            source: W6bFilterGraphConstruction.SourceBinding,
            destination: PlanResourceId,
            sourceBounds: RectI32,
            destinationOrigin: Point2I32,
            clip: ClipStackNode,
            clipMapping: Matrix3x3F64,
            selectedBlend: BlendPlan,
        ): PictureTerminalAdmissionV1 {
            // This is intentionally first: a destination snapshot may append a pass below, but
            // the aggregate's terminal admission has already frozen its causal fact.
            val authority = admitPictureTerminalScissor(sourceBounds, destinationOrigin, destination, clip, clipMapping)
            val before = DestinationVersionI64(versions[destination] ?: 0L)
            val blend = if (selectedBlend is BlendPlan.DestinationReadV1) {
                val extent = filterSource(destination).copyExtentI32()
                val snapshot = planResourceId(PlanResourceRole.DestinationSnapshot, nextPictureSnapshotI32)
                nextPictureSnapshotI32 = Math.addExact(nextPictureSnapshotI32, 1)
                sourceSpecs += W6bFilterGraphConstruction.ResourceSpec(snapshot, PlanResourceRole.DestinationSnapshot,
                    extent, setOf(PlanResourceUsage.CopyDestination))
                passes += PlanPass.TextureCopy(passes.size, destination, snapshot, before,
                    RectI32(0, 0, extent.width, extent.height), Point2I32.Origin, Math.multiplyExact(extent.width.toLong(), 4L))
                selectedBlend.bindDestinationReadV1(before, snapshot)
            } else selectedBlend
            val sourceSampleOffset = try {
                Point2I32(
                    Math.subtractExact(sourceBounds.left, destinationOrigin.x),
                    Math.subtractExact(sourceBounds.top, destinationOrigin.y),
                )
            } catch (_: ArithmeticException) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b Picture terminal sample offset overflows target-local I32 texels.",
                ))
            }
            val operands = PictureCompositeOperandsV1(planned, source.resourceId, 0L, sourceBounds,
                destinationOrigin, authority.copyCompositeScissorTargetLocalI32(), sourceSampleOffset, blend,
                before, authority.compositeScissorAdmitted)
            return PictureTerminalAdmissionV1(authority, operands)
        }
        fun appendFrozenOccurrence(
            occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
            source: W6bFilterGraphConstruction.SourceBinding,
            destination: PlanResourceId,
            operation: FilterCompositeOperationV1,
            materialCoverage: W6bFilterGraphConstruction.SourceBinding? = null,
            replacedLayerSource: PlanResourceId? = null,
            terminalClipDeviceI32: RectI32? = null,
            pictureTerminal: ((W6bFilterGraphConstruction.SourceBinding, RectI32, Point2I32) -> PictureTerminalAdmissionV1)? = null,
        ): PlanPass.FilterComposite {
            filterCursor.passOrdinalI32 = passes.size
            val materialized = occurrence.mask?.let {
                W6bFilterGraphConstruction.freezeMaterializedSource(occurrence, source,
                    requireNotNull(materialCoverage) { "W6b materialized source requires its frozen styled coverage." }, filterCursor)
            }
            // A combined image+mask occurrence has both frozen stages: coverage is first
            // materialized through the W5 source once, then the existing image graph samples
            // that output.  Neither stage rediscovers scene state or invents an operation.
            val frozen = if (occurrence.root != null) {
                materialized?.let { material ->
                    filterResourceSpecs += material.resourceSpecs()
                    framePassSink.appendAll(material.passes())
                    sourceBindingsById[material.output.resourceId] = material.output
                }
                W6bFilterGraphConstruction.freezeImageOccurrence(
                    occurrence, materialized?.output ?: source, filterCursor, framePassSink, emitFilterPictureSource,
                    runtimeCatalog, evaluated = evaluatedImagesByOccurrence[occurrence],
                    preparePicture = { bound, context -> prepareFilterPicture(occurrence, bound, context) },
                )
            } else requireNotNull(materialized) { "W6b mask occurrence needs its materialized W5 source." }
            filterResourceSpecs += frozen.resourceSpecs()
            if (occurrence.root == null) framePassSink.appendAll(frozen.passes())
            sourceBindingsById[frozen.output.resourceId] = frozen.output
            val outputBounds = frozen.output.copyDeviceBoundsI32()
            val compositeDeviceBounds = intersect(outputBounds, terminalClipDeviceI32 ?: outputBounds)?.let { clipped ->
                intersect(clipped, targetDeviceBounds(destination))
            }
            val sealedNoOp = compositeDeviceBounds == null &&
                (operation is FilterCompositeOperationV1.Layer || operation is FilterCompositeOperationV1.Draw)
            if (compositeDeviceBounds == null && !sealedNoOp) throw W6bFilterGraphConstruction.ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6b filter composite has no visible terminal domain."),
            )
            // A no-op retains a valid, unused one-texel source relation. The null scissor is the
            // authoritative terminal fact shared by construction, validation and materialization.
            val sourceBounds = compositeDeviceBounds?.let { bounds -> requireNotNull(frozen.output.mapping.mapDeviceRectToTargetI32OrNull(
                bounds, frozen.output.originDeviceI32,
            )) } ?: RectI32(0, 0, 1, 1)
            val destinationOrigin = targetOriginDevice(destination)
            val destinationLocal = compositeDeviceBounds?.let { bounds -> Point2I32(
                Math.toIntExact(Math.subtractExact(bounds.left.toLong(), destinationOrigin.x.toLong())),
                Math.toIntExact(Math.subtractExact(bounds.top.toLong(), destinationOrigin.y.toLong())),
            ) } ?: Point2I32.Origin
            val sourceSampleOffset = try {
                Point2I32(
                    Math.subtractExact(sourceBounds.left, destinationLocal.x),
                    Math.subtractExact(sourceBounds.top, destinationLocal.y),
                )
            } catch (_: ArithmeticException) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b filter composite sample offset overflows target-local I32 texels.",
                ))
            }
            val compositeScissor = try {
                RectI32(
                    destinationLocal.x,
                    destinationLocal.y,
                    Math.addExact(destinationLocal.x, sourceBounds.width()),
                    Math.addExact(destinationLocal.y, sourceBounds.height()),
                )
            } catch (_: ArithmeticException) {
                throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b filter composite scissor overflows target-local I32 texels.",
                ))
            }
            val before = DestinationVersionI64(versions[destination] ?: 0L)
            val terminalAdmission = if (operation is FilterCompositeOperationV1.Picture && pictureTerminal != null)
                pictureTerminal(frozen.output, sourceBounds, destinationLocal) else null
            val finalOperation = if (sealedNoOp) when (operation) {
                is FilterCompositeOperationV1.Draw -> operation.copy(noOp = true)
                is FilterCompositeOperationV1.Layer -> operation.copy(noOp = true)
                is FilterCompositeOperationV1.Picture -> error("Picture terminal no-op is admitted by its own sealed authority.")
            }
            else if (operation is FilterCompositeOperationV1.Picture)
                operation.copy(terminal = terminalAdmission?.operands ?: operation.terminal) else operation
            val terminal = (finalOperation as? FilterCompositeOperationV1.Picture)?.terminal
            val terminalScissorAuthority = terminalAdmission?.authority
            val finalSampleOffset = terminal?.copySourceSampleOffsetTargetLocalI32() ?: sourceSampleOffset
            // A Picture terminal's null scissor is a sealed no-op/non-admission fact, not a
            // missing value that may be replaced by the generic composite rectangle.
            val finalScissor = if (sealedNoOp) null else if (terminalScissorAuthority != null)
                terminalScissorAuthority.copyCompositeScissorTargetLocalI32() else compositeScissor
            val after = when (finalOperation) {
                is FilterCompositeOperationV1.Draw -> {
                    require(finalOperation.blend !is BlendPlan.DestinationReadV1) { "W6b filtered destination-read blend is not planned." }
                    DestinationVersionI64(if (!finalOperation.noOp && finalOperation.blend.compositionFacts.writesParentDevice)
                        Math.addExact(before.valueI64, 1L) else before.valueI64)
                }
                is FilterCompositeOperationV1.Layer -> if (finalOperation.noOp) before else finalOperation.restore.parentVersionAfter
                is FilterCompositeOperationV1.Picture -> DestinationVersionI64(if (finalOperation.terminal?.blend?.compositionFacts?.writesParentDevice != false)
                    Math.addExact(before.valueI64, 1L) else before.valueI64)
            }
            require(operation !is FilterCompositeOperationV1.Layer || operation.restore.parentVersionBefore == before)
            versions[destination] = after.valueI64
            return PlanPass.FilterComposite(passes.size, frozen.output.resourceId, destination, frozen.terminalKey,
                sourceBounds, destinationLocal, finalSampleOffset, finalScissor, finalOperation,
                replacedLayerSource, after).also { composite ->
                terminalScissorAuthority?.let { authority -> pictureTerminalScissorAuthorityByPassId[composite.id] = authority }
                passes += composite
            }
        }
        /** Emits one frozen aggregate without turning a nested SceneSnapshot into renderer work. */
        fun appendPictureAggregate(
            draft: PictureStreamAggregateDraftV1,
            parentTarget: PlanResourceId,
            owningLayerScope: PictureLayerExecutionScope?,
            filterOwnerSource: W6bFilterGraphConstruction.SourceBinding? = null,
        ): Pair<PictureStreamAggregateV1, PlanPassId?> {
            if (draft.owner is PictureStreamAggregateDraftOwnerV1.FilterPicture) {
                requireNotNull(filterOwnerSource) {
                    "A filter-owned Picture aggregate requires its exact source context."
                }
            }
            val aggregateStartPassI32 = passes.size
            // A finite transformed empty intersect is an exact typed terminal no-op.  Its
            // children cannot affect any aggregate generation, so retain the frozen schedule
            // without asking W4/W5 to lower geometry that no terminal can observe.
            val terminallyEmptyAggregate = (draft.source.recordedInnerClipWithoutCull().terminalDeferredClip()
                as? ClipStackNode.DeviceRect)?.copyBounds()?.isEmpty == true
            fun filterOwnerLocalToDevice(): Matrix3x3F64 {
                val owner = draft.owner as? PictureStreamAggregateDraftOwnerV1.FilterPicture
                    ?: error("A draw-owned Picture aggregate has no filter-owner mapping.")
                return owner.source.sourceDraw?.let { carrier ->
                    val carrierToSource = composeInOrderF64(
                        owner.source.outerPictures().map { it.transform } + carrier.transform,
                    )
                    requireNotNull(requireNotNull(filterOwnerSource).mapping.copyLocalToDeviceF64()
                        .timesCheckedOrNull(carrierToSource)) {
                        "A filter-owned Picture carrier transform cannot be composed in finite F64."
                    }
                } ?: requireNotNull(filterOwnerSource).mapping.copyLocalToDeviceF64()
            }
            val plannedDrawSources = linkedMapOf<FramePlannedCommandIdI32, Pair<PlanPassId, Int>>()
            val plannedDrawCoordinates = linkedMapOf<FramePlannedCommandIdI32, PictureDrawCoordinatesV1>()
            fun recordPictureWork(pass: PlanPass, scope: PictureLayerExecutionScope? = owningLayerScope) {
                val scope = scope ?: return
                val target = scope.target
                val belongs = when (pass) {
                    is PlanPass.RenderPass -> pass.target == target && pass.draws().isNotEmpty()
                    is PlanPass.StencilGeometryProducerV3 -> pass.target == target
                    is PlanPass.StencilCover -> pass.target == target
                    is PlanPass.PictureSourcePass -> pass.parentTarget == target
                    is PlanPass.PictureComposite -> pass.destination == target
                    is PlanPass.FilterComposite -> pass.destination == target && pass.operation is FilterCompositeOperationV1.Picture
                    is PlanPass.LayerComposite -> pass.destination == target
                    else -> false
                }
                if (belongs) steps += LayerExecutionStepV1.RenderChildren(scope.id, pass.id)
            }
            fun appendPlannedDraw(
                entry: PictureStreamEntryDraftV1.Draw,
                target: PlanResourceId,
                coverage: PlanResourceId? = null,
                coverageProducer: PlanResourceId? = coverage,
                workScope: PictureLayerExecutionScope? = owningLayerScope,
            ): PlanPassId? {
                val (input, lane) = preparePictureDrawLane(entry, filterSource(target),
                    filterOwnerSource, target, coverage != null)
                val enclosing = input.enclosingMappingF64
                val selected = RenderGraph.visualDraws(lane.passes()).singleOrNull() ?: return null
                val laneI32 = this.bindings.size
                this.bindings += W6aLayerSourceBinding(null, input.commandIndexI32, lane, input)
                physicalTargetByLane[laneI32] = target
                val ids = lane.resources().associate { row -> row.id to when (row.role) {
                    PlanResourceRole.LogicalTarget -> target
                    PlanResourceRole.ReadbackStaging -> staging
                    PlanResourceRole.DestinationSnapshot -> planResourceId(row.role, occurrences.size + laneI32)
                    PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil ->
                        planResourceId(row.role, laneI32 + 1)
                    else -> (nextOrdinal[row.role] ?: 0).let { ordinal ->
                        nextOrdinal[row.role] = Math.addExact(ordinal, 1)
                        planResourceId(row.role, ordinal)
                    }
                } }.toMutableMap()
                laneResourceIds += ids
                // Dynamic Picture lanes are appended after the initial data-ordinal reservation.
                // Reserve their V/I/uniform triplet before a W6b coverage copy claims an ordinal.
                nextDataOrdinalI32 = maxOf(nextDataOrdinalI32, Math.addExact(laneI32, 2))
                val laneData = lane.resources().filter { it.role in setOf(PlanResourceRole.VertexData,
                    PlanResourceRole.IndexData, PlanResourceRole.UniformData) }
                if (laneData.isNotEmpty()) {
                    require(laneData.map { it.role }.toSet().size == 3 && laneData.size == 3)
                    val data = PlanDrawDataResources(
                        planResourceId(PlanResourceRole.VertexData, laneI32 + 1),
                        planResourceId(PlanResourceRole.IndexData, laneI32 + 1),
                        planResourceId(PlanResourceRole.UniformData, laneI32 + 1))
                    dataRowsById[data.vertex] = laneData.single { it.role == PlanResourceRole.VertexData }
                    dataRowsById[data.index] = laneData.single { it.role == PlanResourceRole.IndexData }
                    dataRowsById[data.uniform] = laneData.single { it.role == PlanResourceRole.UniformData }
                    dataByCommand[selected.commandIndex] = data
                }
                if (target !in versions) appendRender(target, emptyList(), true)
                val geometry = lane.geometrySource ?: lane.takeIf {
                    W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(it.capabilityId)
                }
                val native = geometry?.let { nativeByLane.getOrPut(laneI32, ::linkedMapOf) }
                fun nativePass(pass: PlanPass, ordinal: Int): PlanPass = pass.rebindW4eV6(ordinal,
                    { ids.getValue(it) }, null, null)
                geometry?.passes()?.filter { it is PlanPass.ClipMaskInitialize || it is PlanPass.ClipMaskProducer ||
                    it is PlanPass.ClipMaskFold }?.forEach { original ->
                    val pass = nativePass(original, passes.size)
                    passes += pass
                    requireNotNull(native)[pass.id] = pass
                }
                val draw = if (selected.blend is BlendPlan.DestinationReadV1) {
                    val copy = lane.passes().filterIsInstance<PlanPass.TextureCopy>().single()
                    val snapshot = ids.getValue(copy.destination)
                    val version = DestinationVersionI64(versions.getValue(target))
                    passes += PlanPass.TextureCopy(passes.size, target, snapshot, version,
                        copy.copySourceBoundsI32(), copy.copyDestinationOriginI32(), copy.bytesPerRowI64)
                    selected.withFinalBlendV1(selected.blend.bindDestinationReadV1(version, snapshot))
                } else selected
                if (coverageProducer != null) {
                    val coveragePassIndexI32 = passes.indexOfLast { pass ->
                        pass is PlanPass.FilterCoverageSourcePass && pass.output == coverageProducer
                    }
                    val coveragePass = passes.getOrNull(coveragePassIndexI32) as? PlanPass.FilterCoverageSourcePass
                        ?: error("Picture W6b coverage source is missing its frozen W4 producer.")
                    val coverageDraw = selected.withFinalBlendV1(BlendPlan.LegacySrcOverV1)
                    val coverageDepth = (coverageDraw as? PathDraw)?.takeIf {
                        it.strategy == PathFillStrategy.StencilCover
                    }?.let {
                        planResourceId(PlanResourceRole.DepthStencil, nextCoverageDepthOrdinalI32.also { ordinal ->
                            nextCoverageDepthOrdinalI32 = Math.addExact(ordinal, 1)
                        }).also { depth ->
                            coverageDepthExtents[depth] = sourceBindingsById.getValue(coverageProducer).copyExtentI32()
                        }
                    }
                    passes[coveragePassIndexI32] = coveragePass.withRasterBinding(
                        PlanPass.W6bRasterCoverageBindingV1(
                            coverageDraw,
                            laneI32,
                            allocateCoverageRasterData(dataByCommand[coverageDraw.commandIndex]),
                            coverageDepth,
                        ),
                    )
                }
                val terminal = if (draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover) {
                    val data = dataByCommand.getValue(draw.commandIndex)
                    val depth = planResourceId(PlanResourceRole.DepthStencil, laneI32 + 1)
                    val group = canonicalPathAtomicGroup(draw)
                    val producer = PlanPass.StencilGeometryProducerV3(passes.size, target, depth, draw.commandIndex,
                        draw.copyPathGeometry(), draw.copyScissorI32(), data, group,
                        AttachmentLoadPlan.Load, AttachmentStorePlan.Store)
                    passes += producer
                    val after = DestinationVersionI64(Math.addExact(versions.getValue(target), 1L))
                    versions[target] = after.valueI64
                    PlanPass.StencilCover(passes.size, target, depth, draw, data, group, AttachmentLoadPlan.Load,
                        AttachmentStorePlan.Store, PlanDepthStencilAccess.ReadWrite,
                        PlanDepthStencilLoadStore.LoadStoreTestReset, after, coverage, entry.plannedCommandId).also { pass ->
                        passes += pass
                        geometry?.passes()?.filterIsInstance<PlanPass.PathRenderPass>()?.let { originals ->
                            requireNotNull(native)[producer.id] = nativePass(originals.single {
                                it.phase == PathRenderPhase.SingleSampleStencilProducer }, producer.ordinal)
                            native[pass.id] = nativePass(originals.single {
                                it.phase == PathRenderPhase.SingleSampleStencilColorCover }, pass.ordinal)
                        }
                    }
                } else appendRender(target, listOf(draw), false, coverage,
                    plannedCommandId = entry.plannedCommandId).also { pass ->
                    geometry?.passes()?.filterIsInstance<PlanPass.PathRenderPass>()?.singleOrNull {
                        it.phase == PathRenderPhase.SingleSampleDirectColor
                    }?.let { requireNotNull(native)[pass.id] = nativePass(it, pass.ordinal) }
                }
                if (coverage == null) recordPictureWork(terminal, workScope)
                plannedDrawSources[entry.plannedCommandId] = terminal.id to selected.commandIndex
                plannedDrawCoordinates[entry.plannedCommandId] = PictureDrawCoordinatesV1(target, input.mapping,
                    input.recordedInnerClip, input.deferredCompositeClip, enclosing, input.copyDemandDeviceI32())
                return terminal.id
            }
            fun appendStreamSource(
                target: PlanResourceId,
                source: FilterOccurrenceSourceV1?,
                locator: PictureSourceLocatorV1,
                planned: FramePlannedCommandIdI32,
                coverage: PlanResourceId? = null,
                layerInput: PlanResourceId? = null,
                graphTextureRequest: GraphTextureSourceRequestV1? = null,
                parentCoordinateTarget: PlanResourceId = target,
                deferSourceDrawClip: Boolean = false,
                workScope: PictureLayerExecutionScope? = owningLayerScope,
            ): PlanPass.PictureSourcePass {
                val pass = PlanPass.PictureSourcePass(
                    passes.size,
                    target,
                    source?.scene?.canonicalId?.value ?: draft.sourceScene.canonicalId.value,
                    locator.sourceCommandIndexI32,
                    source,
                    coverage,
                    layerInput,
                    parentTarget = parentCoordinateTarget,
                    pictureCoordinates = source?.pictureW5CoordinatesOrNull(
                        includeSourceDrawClip = !deferSourceDrawClip,
                    ),
                    pictureSourceLocator = locator,
                    plannedCommandId = planned,
                    aggregateId = draft.id,
                    graphTextureRequest = graphTextureRequest,
                    sourceSampling = (layerInput ?: graphTextureRequest?.sealedSourceId)?.let { inputId ->
                        sourceBindingsById.getValue(inputId).samplingFor(sourceBindingsById.getValue(target))
                    },
                )
                passes += pass
                if (target in sourceBindingsById) {
                    // A FilterSource hand-off has its own immutable generation; an aggregate or
                    // parent target receives a real source-order visual write instead.
                    if (sourceBindingsById.getValue(target).resourceId == target &&
                        sourceSpecs.any { it.id == target && it.role == PlanResourceRole.FilterSource }) {
                        versions[target] = 0L
                    } else {
                        versions[target] = Math.addExact(versions[target] ?: 0L, 1L)
                    }
                } else {
                    versions[target] = Math.addExact(versions[target] ?: 0L, 1L)
                }
                recordPictureWork(pass, workScope)
                return pass
            }
            /**
             * Clear and DrawColor have no W5 material input to hand off.  Freeze their complete
             * color and blend facts as an existing W6 RenderPass instead of leaving a source pass
             * whose renderer path would have to rediscover the captured command.
             */
            fun appendFrozenColor(
                target: PlanResourceId,
                colorLinearPremultiplied: ColorF32,
                mode: BlendMode,
                locator: PictureSourceLocatorV1,
                planned: FramePlannedCommandIdI32,
                clip: ClipStackNode = ClipStackNode.Empty,
                transform: org.graphiks.math.matrix.Matrix3x3F32? = null,
            ): PlanPass.RenderPass {
                val extent = filterSource(target).copyExtentI32()
                val targetBounds = RectI32(0, 0, extent.width, extent.height)
                val clippedBounds = pictureColorBounds(filterSource(target), clip, transform)
                // DrawColor is matrix-invariant, but it is clip-bound.  A fully clipped command
                // remains a sealed visual entry with a transparent SrcOver no-op rather than
                // being widened to the target or silently removed from source order.
                val bounds = clippedBounds ?: targetBounds
                val selectedColor = if (clippedBounds == null) ColorF32.Transparent else colorLinearPremultiplied
                val selectedMode = if (clippedBounds == null) BlendMode.SRC_OVER else mode
                val blend = requireNotNull(FinalBlendPlanner.plan(
                    BlendNode.Mode(selectedMode),
                    CoveragePlan.FullOrScissor,
                    SamplePlan.SingleSample,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                )) { "${W6aPlanDiagnostics.UnsupportedChild}: Picture DrawColor blend" }
                if (blend is BlendPlan.DestinationReadV1) {
                    throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6aPlanDiagnostics.UnsupportedChild,
                        "Picture DrawColor destination-read blend has no frozen snapshot lane.",
                    ))
                }
                return appendRender(
                    target,
                    listOf(SolidRectDraw.of(planned.valueI32, selectedColor, bounds, bounds, blend = blend)),
                    clear = false,
                    plannedCommandId = planned,
                )
            }
            fun encodedColorLinearPremultiplied(color: ColorF32): ColorF32 = ColorF32.of(
                ColorTransferFunction.sRgb.toLinear(color.red) * color.alpha,
                ColorTransferFunction.sRgb.toLinear(color.green) * color.alpha,
                ColorTransferFunction.sRgb.toLinear(color.blue) * color.alpha,
                color.alpha,
            )
            fun encodedColorLinearPremultiplied(color: org.graphiks.math.color.ColorARGB): ColorF32 {
                val alphaF32 = color.alphaNormalized
                return ColorF32.of(
                    ColorTransferFunction.sRgb.toLinear(color.redNormalized) * alphaF32,
                    ColorTransferFunction.sRgb.toLinear(color.greenNormalized) * alphaF32,
                    ColorTransferFunction.sRgb.toLinear(color.blueNormalized) * alphaF32,
                    alphaF32,
                )
            }
            fun pictureBlend(draw: DrawNode): BlendPlan = requireNotNull(FinalBlendPlanner.plan(
                draw.blend,
                CoveragePlan.FullOrScissor,
                SamplePlan.SingleSample,
                PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
            )) { "${W6aPlanDiagnostics.UnsupportedChild}: Picture blend" }
            fun pictureColorFilter(draw: DrawNode): ColorFilterExecutionPlanV1? = draw.paint?.colorFilter?.let { filter ->
                (ColorFilterPlanCompilerV1.compile(filter) as? ColorFilterCompileResultV1.Ready)?.execution
                    ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6aPlanDiagnostics.UnsupportedChild, "Picture color filter is outside the W5 authority.",
                    ))
            }
            /** Reuses the existing W6a restore facts for a layer recorded inside a Picture stream. */
            fun pictureLayerRestore(
                descriptor: org.graphiks.kanvas.render.ir.LayerDescriptor,
                parentVersionBefore: DestinationVersionI64,
            ): LayerRestorePlanV1 {
                val colorFilter = descriptor.paint?.colorFilter?.let { filter ->
                    (ColorFilterPlanCompilerV1.compile(filter) as? ColorFilterCompileResultV1.Ready)?.execution
                        ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                            W6aPlanDiagnostics.UnsupportedRestore,
                            "Picture-stream layer restore color filter is outside W6a authority.",
                        ))
                }
                val blend = requireNotNull(FinalBlendPlanner.plan(
                    descriptor.blend,
                    CoveragePlan.FullOrScissor,
                    SamplePlan.SingleSample,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                )) { "${W6aPlanDiagnostics.UnsupportedRestore}: Picture-stream layer blend" }
                if (blend.compositionFacts.readsPriorDevice) {
                    throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6aPlanDiagnostics.UnsupportedRestore,
                        "Picture-stream layer destination-read restore has no frozen snapshot lane.",
                    ))
                }
                val offset = colorFilter?.let { execution ->
                    uniformCursorI64 = alignUniform(uniformCursorI64, caps.minUniformBufferOffsetAlignment)
                    uniformCursorI64.also {
                        uniformCursorI64 = Math.addExact(it, maxOf(16L, execution.dynamicByteCountI64))
                    }
                }
                val after = DestinationVersionI64(if (blend.compositionFacts.writesParentDevice)
                    Math.addExact(parentVersionBefore.valueI64, 1L) else parentVersionBefore.valueI64)
                return LayerRestorePlanV1(
                    descriptor.paint?.color?.alphaNormalized ?: 1f,
                    colorFilter,
                    blend,
                    readsPriorDevice = descriptor.initWithPrevious,
                    writesParentDevice = blend.compositionFacts.writesParentDevice,
                    restoreAffectsTransparentBlack = blend.finalRestoreAffectsTransparentBlackV1(colorFilter),
                    parentVersionBefore = parentVersionBefore,
                    parentVersionAfter = after,
                    colorFilterUniformOffsetI64 = offset,
                )
            }
            /**
             * The outer Picture paint is still a W5 material source.  This deliberately captures
             * coordinates from the occurrence-local source, rather than replaying the child
             * SceneSnapshot or borrowing a parent material row.  The resulting row is resolved
             * once by FrameSourceLayoutV4 and becomes the material half of GraphTextureSourceOperandV1.
             */
            fun captureGraphTextureMaterial(
                aggregate: PictureStreamAggregateDraftV1,
                domain: RectI32,
            ): MaterialSourceConstructionV4 {
                val original = aggregate.source.materialCoordinateDrawOrNull(aggregate.draw.material)
                    ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6aPlanDiagnostics.UnsupportedChild,
                        "Picture graph-texture paint cannot retain its occurrence-local outer coordinates.",
                    ))
                // A graph texture is already the children’s premultiplied color.  Feed its W5
                // paint through a neutral rect coordinate carrier, not the public Picture draw:
                // this keeps W5 from rediscovering/replaying children.  Its actual parent color
                // filter remains in this W5 row solely as the authenticated byte binding that
                // the frozen graph-texture operand names; Task 3 applies it to the graph texture
                // exactly once rather than evaluating this carrier program.
                val picture = original.geometry as? GeometryNode.Picture
                val parentPaint = original.paint?.copy(
                    color = org.graphiks.math.color.ColorARGB.White,
                    shader = null,
                    blendMode = BlendMode.SRC_OVER,
                    blender = null,
                    maskFilter = null,
                    pathEffect = null,
                    imageFilter = null,
                )
                val materialDraw = original.copy(
                    geometry = picture?.let { GeometryNode.Rect.of(it.copyCullRect()) } ?: original.geometry,
                    material = org.graphiks.kanvas.render.ir.MaterialNode.Solid(org.graphiks.math.color.ColorARGB.White),
                    paint = parentPaint,
                    effects = parentPaint?.colorFilter?.let { filter ->
                        org.graphiks.kanvas.render.ir.EffectStack.of(listOf(filter))
                    } ?: org.graphiks.kanvas.render.ir.EffectStack.Empty,
                    blend = BlendNode.SrcOver,
                    origin = if (picture == null) original.origin else DrawOrigin.RECT,
                    resource = null,
                    operationBlendMode = null,
                )
                return when (val normalized = EffectiveMaterialPlanner.normalizeSourcesV4(
                    materialDraw,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                    domain,
                    coverage = CoveragePlan.FullOrScissor,
                    sample = SamplePlan.SingleSample,
                    legacyGradientBoundsI32 = domain,
                    runtimeCatalog = runtimeCatalog,
                )) {
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Source -> normalized.captured
                    EffectiveMaterialPlanner.SourceNormalizationV4.NoOp,
                    is EffectiveMaterialPlanner.SourceNormalizationV4.Refused,
                    -> throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                        W6bFilterDiagnostics.UnsupportedFamily,
                        "Picture graph-texture paint is outside the existing W5 material authority.",
                    ))
                }
            }
            /**
             * Distinguishes raw Picture content from its final composite demand.  The cull only
             * sizes the immutable child source; the deferred clip is retained for the terminal
             * composite and never cuts samples before a parent blur/image filter can consume them.
             */
            if (terminallyEmptyAggregate) {
                /*
                 * A typed empty deferred clip annihilates the complete Picture stream before
                 * any child can become observable.  Publish the isolated source lifecycle so
                 * its begin/seal/generation ownership remains explicit, but deliberately do
                 * not construct an occurrence mapping, W4/W5 lane, filter, material, nested
                 * Picture, layer, clear, or drawColor pass.  In particular, use the already
                 * valid parent mapping for the empty allocation: a singular child transform
                 * has no content to invert.
                 */
                val terminalScissorAuthority = PictureTerminalScissorAuthorityV1(null, true, true)
                val parent = filterOwnerSource ?: filterSource(parentTarget)
                val domain = parent.copyDeviceBoundsI32()
                val parentMapping = parent.mapping
                val aggregateTargetBinding = if (draft.executionMode == PictureStreamExecutionModeV1.ISOLATED_SOURCE) {
                    allocateOccurrenceSource(
                        domain,
                        parentTarget,
                        PlanResourceRole.PictureAggregateSource,
                        mappingLocalToDeviceF64 = parentMapping.copyLocalToDeviceF64(),
                        knownContentDeviceI32 = null,
                        desiredOutputDeviceI32 = domain,
                        requiredInputDeviceI32 = domain,
                        producedOutputDeviceI32 = null,
                    ).also { binding ->
                        passes += PlanPass.PictureAggregateBeginPass(passes.size, draft.id, binding.resourceId, parentTarget)
                        versions[binding.resourceId] = 0L
                    }
                } else null
                val seal = aggregateTargetBinding?.let { binding ->
                    PlanPass.PictureAggregateSealPass(
                        passes.size,
                        draft.id,
                        binding.resourceId,
                        binding.resourceId,
                        versions.getValue(binding.resourceId),
                    ).also(passes::add)
                }
                val aggregate = PictureStreamAggregateV1(
                    draft.id,
                    draft.freezeOwner(),
                    draft.executionMode,
                    draft.sourceScene.canonicalId.value,
                    draft.sourceScene.toList().size,
                    draft.sourcePictureOccurrenceIdI32,
                    draft.outerPicturePathI32(),
                    parentMapping,
                    draft.source.recordedInnerClipWithoutCull(),
                    draft.source.recordedInnerClipWithoutCull().terminalDeferredClip(),
                    null,
                    domain,
                    parentTarget,
                    aggregateTargetBinding?.resourceId,
                    aggregateTargetBinding?.resourceId,
                    seal?.sourceGenerationI64,
                    PictureStreamRegionsV1(null, domain, domain, null),
                    emptyList(),
                    aggregateTargetBinding?.let { binding ->
                        (passes.first { pass -> pass is PlanPass.PictureAggregateBeginPass && pass.aggregateId == draft.id }
                            as PlanPass.PictureAggregateBeginPass).id
                    },
                    seal?.id,
                    seal?.id,
                    Matrix3x3F64(),
                    if (draft.owner is PictureStreamAggregateDraftOwnerV1.DrawPicture && draft.outerPicturePathI32().isEmpty())
                        draft.source.sourceCommandIndexI32 else null,
                    executionPassIds = passes.subList(aggregateStartPassI32, passes.size).map(PlanPass::id),
                    terminalCompositeScissorAuthority = terminalScissorAuthority,
                )
                pictureStreamAggregates += aggregate
                return aggregate to seal?.id
            }

            var aggregateDomain = pictureAggregateDomain(draft, filterOwnerSource ?: filterSource(parentTarget), filterOwnerSource)
            val aggregateTargetDraft = if (draft.executionMode == PictureStreamExecutionModeV1.ISOLATED_SOURCE) {
                val domain = aggregateDomain.sourceDeviceI32
                allocateOccurrenceSource(
                    domain,
                    parentTarget,
                    PlanResourceRole.PictureAggregateSource,
                    mappingLocalToDeviceF64 = aggregateDomain.localToDeviceF64,
                    knownContentDeviceI32 = aggregateDomain.knownContentDeviceI32,
                    // Children must materialize the parent's reverse-input domain. The final
                    // parent's desired output stays distinct on PictureStreamRegionsV1.
                    desiredOutputDeviceI32 = domain,
                    requiredInputDeviceI32 = domain,
                    producedOutputDeviceI32 = aggregateDomain.knownContentDeviceI32,
                ).also { binding ->
                    val begin = PlanPass.PictureAggregateBeginPass(passes.size, draft.id, binding.resourceId, parentTarget)
                    passes += begin
                    versions[binding.resourceId] = 0L
                }
            } else null
            val entryTarget = aggregateTargetDraft?.resourceId ?: parentTarget
            // LayerDescriptor transforms are captured Canvas transforms in this Picture, not
            // deltas from an enclosing layer.  Keep the aggregate's root Picture context fixed:
            // it includes an inline drawPicture host transform and uses the exact filter-owner
            // context when this is an isolated Picture source, so nested layers apply a shared
            // descriptor transform once rather than omitting or repeating it.
            val aggregateRootLocalToDeviceF64 = aggregateDomain.localToDeviceF64
            val childStartPassI32 = passes.size
            data class NestedLayerEmission(val entry: PictureStreamEntryV1.Layer, val terminal: PlanPassId)
            val nestedLayersByEntry = linkedMapOf<PictureStreamEntryIdI32, NestedLayerEmission>()
            lateinit var emitNestedLayerEntries: (List<PictureStreamEntryDraftV1>, PlanResourceId, PictureLayerExecutionScope) -> List<PictureStreamEntryV1>
            fun appendNestedPictureLayer(
                entry: PictureStreamEntryDraftV1.Layer,
                layerParentTarget: PlanResourceId,
            ): PlanPassId {
                val parentBinding = filterSource(layerParentTarget)
                val descriptor = requireNotNull(entry.descriptorSource.layerDescriptor)
                val layerLocalToDevice = aggregateRootLocalToDeviceF64.timesCheckedOrNull(
                    descriptor.transform.toMatrix3x3F64(),
                ) ?: throw W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "Picture layer transform cannot be composed into finite F64 device coordinates.",
                ))
                val layerBinding = allocatePictureLayerTarget(
                    parentBinding.copyDeviceBoundsI32(),
                    layerParentTarget,
                    layerLocalToDevice,
                )
                val layerTarget = layerBinding.resourceId
                val layerScope = PictureLayerExecutionScope(
                    LayerScopeIdI32(nextPictureLayerScopeI32.also { nextPictureLayerScopeI32 = Math.addExact(it, 1) }),
                    layerTarget,
                )
                fun layerOccurrence(backdropInitialization: Boolean): W6bFilterGraphConstruction.PositiveOccurrence? =
                    filterOccurrences.singleOrNull { occurrence ->
                        occurrence.isLayerOccurrence && occurrence.isBackdropInitialization == backdropInitialization &&
                            occurrence.sourceSceneCanonicalId == entry.descriptorSource.scene.canonicalId.value &&
                            occurrence.sourceCommandIndexI32 == entry.descriptorSource.sourceCommandIndexI32 &&
                            occurrence.outerPicturePathI32() == entry.descriptorSource.picturePathI32()
                    }
                val backdropOccurrence = layerOccurrence(backdropInitialization = true)
                val postChildOccurrence = layerOccurrence(backdropInitialization = false)
                val initialization: LayerInitializationPlanV1 = if (backdropOccurrence != null) {
                    val parentExtent = parentBinding.copyExtentI32()
                    val parentDomain = parentBinding.copyDeviceBoundsI32()
                    val sourceBounds = RectI32(0, 0, parentExtent.width, parentExtent.height)
                    val snapshot = allocateOccurrenceSource(
                        parentDomain,
                        layerParentTarget,
                        PlanResourceRole.FilterSource,
                        copyDestination = true,
                        mappingLocalToDeviceF64 = parentBinding.mapping.copyLocalToDeviceF64(),
                        knownContentDeviceI32 = parentDomain,
                        desiredOutputDeviceI32 = parentDomain,
                        requiredInputDeviceI32 = parentDomain,
                        producedOutputDeviceI32 = parentDomain,
                    )
                    val capturedParentVersion = DestinationVersionI64(versions[layerParentTarget] ?: 0L)
                    val clear = appendRender(layerTarget, emptyList(), true)
                    val copy = PlanPass.TextureCopy(
                        passes.size,
                        layerParentTarget,
                        snapshot.resourceId,
                        capturedParentVersion,
                        sourceBounds,
                        Point2I32.Origin,
                        Math.multiplyExact(parentExtent.width.toLong(), 4L),
                    )
                    passes += copy
                    val initializationComposite = appendFrozenOccurrence(
                        backdropOccurrence,
                        snapshot,
                        layerTarget,
                        FilterCompositeOperationV1.Draw(BlendPlan.LegacySrcOverV1),
                        terminalClipDeviceI32 = parentDomain,
                    )
                    require(clear.ordinal < copy.ordinal && copy.ordinal < initializationComposite.ordinal)
                    LayerInitializationPlanV1.Backdrop(
                        BackdropInitializationPlanV1(
                            layerParentTarget,
                            snapshot.resourceId,
                            initializationComposite.source,
                            capturedParentVersion,
                            requireNotNull(backdropOccurrence.root).id,
                        ),
                    ).also {
                        steps += LayerExecutionStepV1.Initialize(layerScope.id, initializationComposite.id)
                    }
                } else if (descriptor.initWithPrevious) {
                    val parentExtent = parentBinding.copyExtentI32()
                    val sourceBounds = RectI32(0, 0, parentExtent.width, parentExtent.height)
                    val copy = PlanPass.TextureCopy(
                        passes.size,
                        layerParentTarget,
                        layerTarget,
                        DestinationVersionI64(versions[layerParentTarget] ?: 0L),
                        sourceBounds,
                        Point2I32.Origin,
                        Math.multiplyExact(parentExtent.width.toLong(), 4L),
                    )
                    passes += copy
                    versions[layerTarget] = 0L
                    LayerInitializationPlanV1.PreviousCopy(layerParentTarget, layerTarget,
                        requireNotNull(copy.destinationVersion), sourceBounds, Point2I32.Origin).also {
                        steps += LayerExecutionStepV1.Initialize(layerScope.id, copy.id)
                    }
                } else {
                    val clear = appendRender(layerTarget, emptyList(), true)
                    LayerInitializationPlanV1.TransparentBlack.also {
                        steps += LayerExecutionStepV1.Initialize(layerScope.id, clear.id)
                    }
                }
                val children = emitNestedLayerEntries(entry.children(), layerTarget, layerScope)
                val before = DestinationVersionI64(versions[layerParentTarget] ?: 0L)
                val restore = pictureLayerRestore(descriptor, before)
                val terminal = postChildOccurrence?.let { occurrence ->
                    if (occurrence.mask is MaskFilterNode.Shader) captureMaskShaderMaterial(occurrence, layerTarget)
                    val coverage = allocateOccurrenceSource(targetDeviceBounds(layerTarget), layerTarget,
                        PlanResourceRole.CoverageSource,
                        mappingLocalToDeviceF64 = layerBinding.mapping.copyLocalToDeviceF64(),
                        knownContentDeviceI32 = layerBinding.copyKnownContentDeviceI32(),
                        desiredOutputDeviceI32 = layerBinding.copyDesiredOutputDeviceI32(),
                        requiredInputDeviceI32 = layerBinding.copyRequiredInputDeviceI32(),
                        producedOutputDeviceI32 = layerBinding.copyProducedOutputDeviceI32())
                    filterCursor.passOrdinalI32 = passes.size
                    passes += PlanPass.FilterCoverageSourcePass(passes.size, coverage.resourceId, occurrence.source)
                    val masked = occurrence.mask?.let {
                        W6bFilterGraphConstruction.freezeMaskOccurrence(occurrence, coverage, filterCursor).also { frozen ->
                            filterResourceSpecs += frozen.resourceSpecs(); passes += frozen.passes()
                        }.output
                    } ?: coverage
                    val source = allocateShadedOccurrenceSource(masked, layerTarget)
                    appendStreamSource(source.resourceId, entry.descriptorSource, entry.locator, entry.plannedCommandId,
                        coverage = masked.resourceId, layerInput = layerTarget, parentCoordinateTarget = layerTarget,
                        workScope = layerScope)
                    val composite = appendFrozenOccurrence(occurrence, source, layerParentTarget,
                        FilterCompositeOperationV1.Layer(restore), masked, layerTarget)
                    // Restore is this pass's sole execution step, including when it writes
                    // another layer. A second parent RenderChildren step would execute it twice.
                    steps += LayerExecutionStepV1.Restore(layerScope.id, composite.id)
                    composite.id
                } ?: run {
                    val layerExtent = layerBinding.copyExtentI32()
                    val composite = PlanPass.LayerComposite(passes.size, layerScope.id, layerTarget, layerParentTarget,
                        RectI32(0, 0, layerExtent.width, layerExtent.height), Point2I32.Origin, restore,
                        AttachmentLoadPlan.Load, AttachmentStorePlan.Store, restore.parentVersionAfter)
                    passes += composite
                    versions[layerParentTarget] = restore.parentVersionAfter.valueI64
                    steps += LayerExecutionStepV1.Restore(layerScope.id, composite.id)
                    composite.id
                }
                val domain = layerBinding.copyDeviceBoundsI32()
                scopePlans[layerScope.id.valueI32] = LayerScopePlanV1(
                    layerScope.id,
                    null,
                    entry.locator.sourceCommandIndexI32,
                    entry.endCommandIndexI32,
                    emptyList(),
                    layerBinding.mapping,
                    LayerBoundsPlanV1(null, layerBinding.copyKnownContentDeviceI32(),
                        layerBinding.copyDesiredOutputDeviceI32() ?: domain,
                        layerBinding.copyRequiredInputDeviceI32() ?: domain,
                        layerBinding.copyProducedOutputDeviceI32(), domain),
                    initialization,
                    restore,
                    layerTarget,
                    parentTargetResource = layerParentTarget,
                )
                val public = PictureStreamEntryV1.Layer(entry.id, entry.locator, entry.plannedCommandId,
                    entry.endCommandIndexI32, layerScope.id, layerTarget, children, terminal)
                nestedLayersByEntry[entry.id] = NestedLayerEmission(public, terminal)
                return terminal
            }
            emitNestedLayerEntries = { entries, target, workScope ->
                entries.map { entry -> when (entry) {
                    is PictureStreamEntryDraftV1.Draw -> {
                        val occurrence = entry.filterOccurrence
                        val terminal = if (occurrence == null) {
                            appendPlannedDraw(entry, target, workScope = workScope)
                        } else {
                            if (occurrence.mask is MaskFilterNode.Shader) captureMaskShaderMaterial(occurrence, target)
                            val coverage = allocateOccurrenceSource(targetDeviceBounds(target), target, PlanResourceRole.CoverageSource)
                            filterCursor.passOrdinalI32 = passes.size
                            passes += PlanPass.FilterCoverageSourcePass(passes.size, coverage.resourceId, occurrence.source)
                            val masked = occurrence.mask?.let {
                                W6bFilterGraphConstruction.freezeMaskOccurrence(occurrence, coverage, filterCursor).also { frozen ->
                                    filterResourceSpecs += frozen.resourceSpecs(); passes += frozen.passes()
                                }.output
                            } ?: coverage
                            val source = allocateShadedOccurrenceSource(masked, target)
                            appendPlannedDraw(entry, source.resourceId, masked.resourceId,
                                coverageProducer = coverage.resourceId, workScope = workScope)
                            appendFrozenOccurrence(occurrence, source, target,
                                FilterCompositeOperationV1.Picture(entry.source.scene.canonicalId.value,
                                    entry.source.sourceCommandIndexI32), masked, pictureTerminal = { output, rect, origin ->
                                    freezePictureTerminal(entry.plannedCommandId, output, target, rect, origin,
                                        ClipStackNode.Empty, Matrix3x3F64(), pictureBlend(requireNotNull(entry.source.sourceDraw)))
                                }).also { composite ->
                                recordPictureWork(composite, workScope)
                            }.id
                        }
                        PictureStreamEntryV1.Draw(entry.id, entry.locator, entry.plannedCommandId, terminal,
                            plannedDrawSources[entry.plannedCommandId]?.first,
                            plannedDrawSources[entry.plannedCommandId]?.second, terminal == null,
                            plannedDrawCoordinates[entry.plannedCommandId])
                    }
                    is PictureStreamEntryDraftV1.Picture -> {
                        val child = appendPictureAggregate(entry.child, target, workScope, filterOwnerSource)
                        PictureStreamEntryV1.Picture(entry.id, entry.locator, entry.plannedCommandId, entry.child.id, child.second)
                    }
                    is PictureStreamEntryDraftV1.Layer -> {
                        appendNestedPictureLayer(entry, target)
                        nestedLayersByEntry.getValue(entry.id).entry
                    }
                    is PictureStreamEntryDraftV1.Clear -> PictureStreamEntryV1.Clear(entry.id, entry.locator,
                        entry.plannedCommandId, entry.color,
                        appendFrozenColor(target, encodedColorLinearPremultiplied(entry.color), BlendMode.SRC,
                            entry.locator, entry.plannedCommandId).id)
                    is PictureStreamEntryDraftV1.DrawColor -> PictureStreamEntryV1.DrawColor(entry.id, entry.locator,
                        entry.plannedCommandId, entry.color, entry.mode, entry.copyTransformF32(), entry.clip,
                        appendFrozenColor(target, encodedColorLinearPremultiplied(entry.color), entry.mode,
                            entry.locator, entry.plannedCommandId, entry.clip, entry.copyTransformF32()).id)
                    is PictureStreamEntryDraftV1.ConsumedState ->
                        PictureStreamEntryV1.ConsumedState(entry.id, entry.locator, entry.state)
                    is PictureStreamEntryDraftV1.AnnotationNoOp ->
                        PictureStreamEntryV1.AnnotationNoOp(entry.id, entry.locator, entry.annotationCanonicalId)
                } }
            }
            val emittedEntries = mutableListOf<PictureStreamEntryV1>()
            fun emitEntry(entry: PictureStreamEntryDraftV1): PlanPassId? = when (entry) {
                is PictureStreamEntryDraftV1.Draw -> {
                    val occurrence = entry.filterOccurrence
                    if (occurrence == null) {
                        appendPlannedDraw(entry, entryTarget)
                    } else {
                        val coverage = allocateOccurrenceSource(targetDeviceBounds(entryTarget), entryTarget,
                            PlanResourceRole.CoverageSource)
                        filterCursor.passOrdinalI32 = passes.size
                        passes += PlanPass.FilterCoverageSourcePass(passes.size, coverage.resourceId, occurrence.source)
                        val masked = occurrence.mask?.let {
                            W6bFilterGraphConstruction.freezeMaskOccurrence(occurrence, coverage, filterCursor).also { frozen ->
                                filterResourceSpecs += frozen.resourceSpecs(); passes += frozen.passes()
                            }.output
                        } ?: coverage
                        val source = allocateShadedOccurrenceSource(masked, entryTarget)
                        appendPlannedDraw(entry, source.resourceId, masked.resourceId,
                            coverageProducer = coverage.resourceId)
                        val composite = appendFrozenOccurrence(occurrence, source, entryTarget,
                            FilterCompositeOperationV1.Picture(entry.source.scene.canonicalId.value, entry.source.sourceCommandIndexI32),
                            masked,
                            pictureTerminal = { output, rect, origin -> freezePictureTerminal(entry.plannedCommandId,
                                output, entryTarget, rect, origin, ClipStackNode.Empty, Matrix3x3F64(),
                                pictureBlend(requireNotNull(entry.source.sourceDraw))) })
                        recordPictureWork(composite)
                        composite.id
                    }
                }
                is PictureStreamEntryDraftV1.Picture -> {
                    val child = appendPictureAggregate(entry.child, entryTarget, owningLayerScope, filterOwnerSource)
                    child.second
                }
                is PictureStreamEntryDraftV1.Layer ->
                    appendNestedPictureLayer(entry, entryTarget)
                is PictureStreamEntryDraftV1.Clear ->
                    appendFrozenColor(entryTarget, encodedColorLinearPremultiplied(entry.color), BlendMode.SRC,
                        entry.locator, entry.plannedCommandId).id
                is PictureStreamEntryDraftV1.DrawColor ->
                    appendFrozenColor(entryTarget, encodedColorLinearPremultiplied(entry.color), entry.mode,
                        entry.locator, entry.plannedCommandId, entry.clip, entry.copyTransformF32()).id
                is PictureStreamEntryDraftV1.ConsumedState,
                is PictureStreamEntryDraftV1.AnnotationNoOp,
                -> null
            }
            draft.entries().forEach { entry ->
                val terminal = emitEntry(entry)
                emittedEntries += when (entry) {
                    is PictureStreamEntryDraftV1.Draw -> PictureStreamEntryV1.Draw(entry.id, entry.locator,
                        entry.plannedCommandId, terminal, plannedDrawSources[entry.plannedCommandId]?.first,
                        plannedDrawSources[entry.plannedCommandId]?.second, terminal == null,
                        plannedDrawCoordinates[entry.plannedCommandId])
                    is PictureStreamEntryDraftV1.Picture -> PictureStreamEntryV1.Picture(entry.id, entry.locator,
                        entry.plannedCommandId, entry.child.id, terminal)
                    is PictureStreamEntryDraftV1.Layer -> nestedLayersByEntry.getValue(entry.id).entry
                    is PictureStreamEntryDraftV1.Clear -> PictureStreamEntryV1.Clear(entry.id, entry.locator,
                        entry.plannedCommandId, entry.color, terminal)
                    is PictureStreamEntryDraftV1.DrawColor -> PictureStreamEntryV1.DrawColor(entry.id, entry.locator,
                        entry.plannedCommandId, entry.color, entry.mode, entry.copyTransformF32(), entry.clip, terminal)
                    is PictureStreamEntryDraftV1.ConsumedState -> PictureStreamEntryV1.ConsumedState(entry.id, entry.locator, entry.state)
                    is PictureStreamEntryDraftV1.AnnotationNoOp -> PictureStreamEntryV1.AnnotationNoOp(
                        entry.id, entry.locator, entry.annotationCanonicalId,
                    )
                }
            }
            val entryOrigin = filterSource(entryTarget).originDeviceI32
            /**
             * A terminal can initialize only the source output it actually published.  Its
             * physical source rectangle is a demand/allocation domain and must never be
             * promoted to Picture known content: that would let a transparent halo become a
             * CLAMP edge for a later parent filter.
             */
            fun initializedCompositeOutput(source: PlanResourceId): RectI32? {
                val produced = sourceBindingsById[source]?.copyProducedOutputDeviceI32() ?: return null
                // The child can produce a larger offscreen halo, but this terminal initializes
                // only the overlap it composites into its immediate aggregate target.  Preserve
                // that published source generation and terminal target; never substitute a
                // demand, cull, or physical source allocation for known content.
                val initialized = intersect(produced, targetDeviceBounds(entryTarget)) ?: return null
                return requireNotNull(filterSource(entryTarget).mapping.mapDeviceRectToTargetI32OrNull(
                    initialized,
                    entryOrigin,
                ))
            }
            var initializedContent: RectI32? = null
            passes.drop(childStartPassI32).forEach { pass ->
                val localBounds: RectI32? = when (pass) {
                    is PlanPass.RenderPass -> if (pass.target == entryTarget) pass.draws().map(::w6aRasterBoundsI32)
                        .fold(null as RectI32?) { bounds, draw -> unionOrNull(bounds, draw) } else null
                    is PlanPass.StencilCover -> if (pass.target == entryTarget) w6aRasterBoundsI32(pass.draw) else null
                    is PlanPass.FilterComposite -> if (pass.destination == entryTarget) initializedCompositeOutput(pass.source) else null
                    is PlanPass.PictureComposite -> if (pass.destination == entryTarget) initializedCompositeOutput(pass.source) else null
                    is PlanPass.LayerComposite -> if (pass.destination == entryTarget) initializedCompositeOutput(pass.source) else null
                    is PlanPass.PictureSourcePass -> if (pass.output == entryTarget) filterSource(entryTarget).copyExtentI32().let {
                        RectI32(0, 0, it.width, it.height)
                    } else null
                    else -> null
                }
                localBounds?.let { bounds -> initializedContent = unionOrNull(initializedContent,
                    requireNotNull(bounds.translateCheckedOrNull(Vector2I32(entryOrigin.x, entryOrigin.y)))) }
            }
            aggregateDomain = aggregateDomain.copy(knownContentDeviceI32 = initializedContent)
            val aggregateTargetBinding = aggregateTargetDraft?.withResource(aggregateTargetDraft.resourceId,
                knownContentDeviceI32 = initializedContent, producedOutputDeviceI32 = initializedContent)?.also {
                sourceBindingsById[it.resourceId] = it
            }
            val terminal: PlanPassId?
            val beginPassId: PlanPassId?
            val sealPassId: PlanPassId?
            val sealedSourceId: PlanResourceId?
            val generation: Long?
            if (aggregateTargetBinding == null) {
                terminal = emittedEntries.lastOrNull { it.terminalPassId != null }?.terminalPassId
                beginPassId = null
                sealPassId = null
                sealedSourceId = null
                generation = null
            } else {
                val generationI64 = versions.getValue(aggregateTargetBinding.resourceId)
                val seal = PlanPass.PictureAggregateSealPass(passes.size, draft.id, aggregateTargetBinding.resourceId,
                    aggregateTargetBinding.resourceId, generationI64)
                passes += seal
                if (draft.owner is PictureStreamAggregateDraftOwnerV1.FilterPicture) {
                    // A filter-owned aggregate ends at Seal.  Its sole reader is published by
                    // FilterPass.Picture outside this aggregate; no W5 graph texture, carrier
                    // material, synthetic composite, or parent write is admitted here.
                    terminal = seal.id
                    beginPassId = (passes.first { pass -> pass is PlanPass.PictureAggregateBeginPass && pass.aggregateId == draft.id }
                        as PlanPass.PictureAggregateBeginPass).id
                    sealPassId = seal.id
                    sealedSourceId = aggregateTargetBinding.resourceId
                    generation = generationI64
                } else {
                val occurrence = draft.filterOccurrence
                val maskedCoverage = occurrence?.mask?.let {
                    val coverage = allocateOccurrenceSource(targetDeviceBounds(aggregateTargetBinding.resourceId),
                        aggregateTargetBinding.resourceId, PlanResourceRole.CoverageSource,
                        knownContentDeviceI32 = aggregateTargetBinding.copyKnownContentDeviceI32())
                    filterCursor.passOrdinalI32 = passes.size
                    passes += PlanPass.FilterCoverageSourcePass(
                        passes.size, coverage.resourceId, occurrence.source, deferSourceDrawClip = true,
                        sealedAlphaSource = PictureAlphaSourceV1(draft.id, aggregateTargetBinding.resourceId,
                            generationI64, aggregateTargetBinding.mapping, aggregateTargetBinding.copyExtentI32().let {
                                RectI32(0, 0, it.width, it.height)
                            }),
                        sealedAlphaSampling = aggregateTargetBinding.samplingFor(coverage),
                    )
                    W6bFilterGraphConstruction.freezeMaskOccurrence(occurrence, coverage, filterCursor).also { frozen ->
                        filterResourceSpecs += frozen.resourceSpecs(); passes += frozen.passes()
                    }.output
                }
                val source = maskedCoverage?.let { allocateShadedOccurrenceSource(it, parentTarget) } ?: allocateOccurrenceSource(
                    aggregateTargetBinding.copyDeviceBoundsI32(), parentTarget, PlanResourceRole.FilterSource,
                    mappingLocalToDeviceF64 = aggregateTargetBinding.mapping.copyLocalToDeviceF64(),
                    knownContentDeviceI32 = aggregateTargetBinding.copyKnownContentDeviceI32(),
                    desiredOutputDeviceI32 = aggregateTargetBinding.copyDesiredOutputDeviceI32(),
                    requiredInputDeviceI32 = aggregateTargetBinding.copyRequiredInputDeviceI32(),
                    producedOutputDeviceI32 = aggregateTargetBinding.copyProducedOutputDeviceI32(),
                )
                val aggregateBounds = aggregateTargetBinding.copyExtentI32().let { RectI32(0, 0, it.width, it.height) }
                graphTextureMaterialSources.getOrPut(draft.id) {
                    captureGraphTextureMaterial(draft, aggregateTargetBinding.copyDeviceBoundsI32())
                }
                val request = GraphTextureSourceRequestV1(
                    draft.id,
                    aggregateTargetBinding.resourceId,
                    generationI64,
                    aggregateTargetBinding.originDeviceI32,
                    aggregateBounds,
                    aggregateTargetBinding.mapping,
                    draft.source.recordedInnerClipWithoutCull().terminalDeferredClip(),
                    draft.draw.paint?.color?.alphaNormalized ?: 1f,
                    pictureColorFilter(draft.draw),
                    pictureBlend(draft.draw),
                    if (maskedCoverage == null) GraphTextureCoverageOperationV1.PRESERVE_PREMULTIPLIED_SOURCE
                    else GraphTextureCoverageOperationV1.REPLACE_ALPHA_FROM_MASK,
                    composeInOrderF64(draft.source.outerPictures().map { it.transform }),
                )
                appendStreamSource(source.resourceId, draft.source,
                    PictureSourceLocatorV1(draft.sourcePictureOccurrenceIdI32, draft.source.sourceCommandIndexI32),
                    requireNotNull(draft.sourcePlannedCommandId), coverage = maskedCoverage?.resourceId,
                    graphTextureRequest = request, parentCoordinateTarget = parentTarget, deferSourceDrawClip = true)
                terminal = if (occurrence != null) {
                    appendFrozenOccurrence(occurrence, source, parentTarget,
                        FilterCompositeOperationV1.Picture(draft.source.scene.canonicalId.value, draft.source.sourceCommandIndexI32),
                        maskedCoverage,
                        pictureTerminal = { output, rect, origin -> freezePictureTerminal(requireNotNull(draft.sourcePlannedCommandId),
                            output, parentTarget, rect, origin, draft.source.recordedInnerClipWithoutCull().terminalDeferredClip(),
                            composeInOrderF64(draft.source.outerPictures().map { it.transform }), pictureBlend(draft.draw)) }
                    ).also(::recordPictureWork).id
                } else {
                    val domain = requireNotNull(intersect(source.copyDeviceBoundsI32(), targetDeviceBounds(parentTarget)))
                    val rect = requireNotNull(source.mapping.mapDeviceRectToLayerI32OrNull(domain))
                    val origin = requireNotNull(filterSource(parentTarget).mapping.mapDeviceRectToLayerI32OrNull(domain))
                    val terminalAdmission = freezePictureTerminal(requireNotNull(draft.sourcePlannedCommandId), source, parentTarget, rect,
                        Point2I32(origin.left, origin.top), draft.source.recordedInnerClipWithoutCull().terminalDeferredClip(),
                        composeInOrderF64(draft.source.outerPictures().map { it.transform }), pictureBlend(draft.draw))
                    val operands = terminalAdmission.operands
                    val after = DestinationVersionI64(if (operands.blend.compositionFacts.writesParentDevice)
                        Math.addExact(versions[parentTarget] ?: 0L, 1L) else versions[parentTarget] ?: 0L)
                    val composite = PlanPass.PictureComposite(passes.size, source.resourceId, parentTarget, draft.source, after, operands)
                    passes += composite
                    pictureTerminalScissorAuthorityByPassId[composite.id] = terminalAdmission.authority
                    versions[parentTarget] = after.valueI64
                    recordPictureWork(composite)
                    composite.id
                }
                beginPassId = (passes.firstOrNull { pass -> pass is PlanPass.PictureAggregateBeginPass && pass.aggregateId == draft.id }
                    as PlanPass.PictureAggregateBeginPass).id
                sealPassId = seal.id
                sealedSourceId = aggregateTargetBinding.resourceId
                generation = generationI64
                }
            }
            val producedOutput = when (val pass = passes.lastOrNull { it.id == terminal }) {
                is PlanPass.FilterComposite -> sourceBindingsById[pass.source]?.copyProducedOutputDeviceI32()
                else -> aggregateDomain.knownContentDeviceI32
            }
            val terminalScissorAuthority = terminal?.let(pictureTerminalScissorAuthorityByPassId::get)
            val filterOwned = draft.owner is PictureStreamAggregateDraftOwnerV1.FilterPicture
            val aggregate = PictureStreamAggregateV1(
                draft.id,
                draft.freezeOwner(),
                draft.executionMode,
                draft.sourceScene.canonicalId.value,
                draft.sourceScene.toList().size,
                draft.sourcePictureOccurrenceIdI32,
                draft.outerPicturePathI32(),
                aggregateDomain.mapping,
                if (filterOwned) ClipStackNode.Empty else draft.source.recordedInnerClipWithoutCull(),
                if (filterOwned) ClipStackNode.Empty else draft.source.recordedInnerClipWithoutCull().terminalDeferredClip(),
                aggregateDomain.cullContentDeviceI32,
                aggregateDomain.demandDeviceI32,
                parentTarget,
                aggregateTargetBinding?.resourceId,
                sealedSourceId,
                generation,
                PictureStreamRegionsV1(
                    aggregateDomain.knownContentDeviceI32,
                    aggregateDomain.demandDeviceI32,
                    aggregateDomain.sourceDeviceI32,
                    producedOutput,
                ),
                emittedEntries,
                beginPassId,
                sealPassId,
                terminal,
                if (filterOwned) aggregateDomain.localToDeviceF64 else
                    composeInOrderF64(draft.source.outerPictures().map { it.transform }),
                if (!filterOwned && draft.outerPicturePathI32().isEmpty()) draft.source.sourceCommandIndexI32 else null,
                executionPassIds = passes.subList(aggregateStartPassI32, passes.size).map(PlanPass::id),
                terminalCompositeScissorAuthority = terminalScissorAuthority,
            )
            pictureStreamAggregates += aggregate
            return aggregate to terminal
        }
        emitFilterPictureSource = W6bFilterGraphConstruction.FilterPictureSourceEmitterV1 { prepared, sourceContext ->
            val draft = pictureDiscovery.publishFilterRoot(prepared.draft)
            // A re-entrant W6d leaf may be evaluated from a W6b-produced FilterTarget rather
            // than an ordinary W6a layer target.  It is already allocated by W6b; retain only
            // its immutable mapping facts locally so the nested aggregate can discover its
            // parent domain without attempting to reinterpret the FilterTarget ordinal as a
            // layer scope.
            sourceBindingsById.putIfAbsent(sourceContext.resourceId, sourceContext)
            val aggregate = appendPictureAggregate(draft, sourceContext.resourceId, null, sourceContext).first
            val resourceId = requireNotNull(aggregate.sealedSourceId) {
                "W6d filter Picture owner did not publish a sealed aggregate source."
            }
            W6bFilterGraphConstruction.FilterPictureSourceEmissionV1(
                aggregate.id,
                resourceId,
                requireNotNull(aggregate.sealedSourceGenerationI64),
                sourceBindingsById.getValue(resourceId),
                aggregate.owner as? PictureAggregateOwnerV1.FilterPicture
                    ?: error("W6d Picture aggregate did not freeze a filter-owned source identity."),
                prepared.copyContentDeviceF64(),
            )
        }
        // The root is the one scene attachment. It starts clear; every later root segment loads.
        appendRender(root, emptyList(), true)
        val begins = occurrences.associateBy { it.beginCommandIndexI32 }
        val ends = occurrences.associateBy { it.endCommandIndexI32 }
        val lastCommandIndexI32 = maxOf(
            occurrences.maxOfOrNull { it.endCommandIndexI32 } ?: -1,
            bindings.maxOfOrNull { it.firstCommandIndexI32 } ?: -1,
            filterScene?.toList()?.lastIndex ?: -1,
        )
        val filterCommands = filterScene?.toList() ?: emptyList()
        fun pictureParentScope(commandIndexI32: Int): Int? = occurrences
            .filter { it.beginCommandIndexI32 < commandIndexI32 && commandIndexI32 < it.endCommandIndexI32 }
            .maxByOrNull { it.beginCommandIndexI32 }?.idI32
        fun pictureParentTarget(commandIndexI32: Int): PlanResourceId = targetFor(pictureParentScope(commandIndexI32))
        for (commandIndexI32 in 0..lastCommandIndexI32) {
            begins[commandIndexI32]?.let { occurrence ->
                val geometry = activeByScope[occurrence.idI32] ?: return@let
                val scopeId = LayerScopeIdI32(occurrence.idI32)
                require(geometry.mapping != null)
                val layerTarget = targetFor(occurrence.idI32)
                val backdrop = backdropFiltersByBegin[occurrence.beginCommandIndexI32]
                if (backdrop != null) {
                    val parentTarget = targetFor(occurrence.parentIdI32)
                    val parentOrigin = targetOriginDevice(parentTarget)
                    val copyDomain = requireNotNull(snapshotInputByScope[occurrence.idI32] ?: geometry.compositeDomainDeviceI32)
                    val sourceBounds = RectI32(
                        Math.toIntExact(Math.subtractExact(copyDomain.left.toLong(), parentOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.top.toLong(), parentOrigin.y.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.right.toLong(), parentOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.bottom.toLong(), parentOrigin.y.toLong())),
                    )
                    val snapshot = allocateOccurrenceSource(
                        copyDomain,
                        parentTarget,
                        PlanResourceRole.FilterSource,
                        copyDestination = true,
                        knownContentDeviceI32 = copyDomain,
                        desiredOutputDeviceI32 = geometry.desiredOutputDeviceI32,
                        requiredInputDeviceI32 = copyDomain,
                        producedOutputDeviceI32 = geometry.producedOutputDeviceI32,
                    )
                    val capturedParentVersion = DestinationVersionI64(versions[parentTarget] ?: 0L)
                    val clear = appendRender(layerTarget, emptyList(), true)
                    val copy = PlanPass.TextureCopy(
                        passes.size,
                        parentTarget,
                        snapshot.resourceId,
                        capturedParentVersion,
                        sourceBounds,
                        Point2I32.Origin,
                        Math.multiplyExact(sourceBounds.width().toLong(), 4L),
                    )
                    passes += copy
                    val initializationComposite = appendFrozenOccurrence(
                        backdrop,
                        snapshot,
                        layerTarget,
                        FilterCompositeOperationV1.Draw(BlendPlan.LegacySrcOverV1),
                        terminalClipDeviceI32 = geometry.desiredOutputDeviceI32,
                    )
                    initializationByScope[occurrence.idI32] = LayerInitializationPlanV1.Backdrop(
                        BackdropInitializationPlanV1(
                            parentTarget,
                            snapshot.resourceId,
                            initializationComposite.source,
                            capturedParentVersion,
                            requireNotNull(backdrop.root).id,
                        ),
                    )
                    // The terminal composite is the semantic initializer; the clear is a physical attachment precondition.
                    require(clear.ordinal < copy.ordinal && copy.ordinal < initializationComposite.ordinal)
                    steps += LayerExecutionStepV1.Initialize(scopeId, initializationComposite.id)
                } else if (!occurrence.descriptor.initWithPrevious) {
                    val initialize = appendRender(layerTarget, emptyList(), true)
                    initializationByScope[occurrence.idI32] = LayerInitializationPlanV1.TransparentBlack
                    steps += LayerExecutionStepV1.Initialize(scopeId, initialize.id)
                } else {
                    val parentTarget = targetFor(occurrence.parentIdI32)
                    val parentOrigin = targetOriginDevice(parentTarget)
                    val copyDomain = requireNotNull(snapshotInputByScope[occurrence.idI32] ?: geometry.compositeDomainDeviceI32)
                    val sourceBounds = RectI32(
                        Math.toIntExact(Math.subtractExact(copyDomain.left.toLong(), parentOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.top.toLong(), parentOrigin.y.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.right.toLong(), parentOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.bottom.toLong(), parentOrigin.y.toLong())),
                    )
                    val capturedParentVersion = DestinationVersionI64(versions[parentTarget] ?: 0L)
                    val layerOrigin = targetOriginDevice(layerTarget)
                    val destinationOrigin = Point2I32(
                        Math.toIntExact(Math.subtractExact(copyDomain.left.toLong(), layerOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.top.toLong(), layerOrigin.y.toLong())),
                    )
                    val copy = PlanPass.TextureCopy(
                        passes.size,
                        parentTarget,
                        layerTarget,
                        capturedParentVersion,
                        sourceBounds,
                        destinationOrigin,
                        Math.multiplyExact(sourceBounds.width().toLong(), 4L),
                    )
                    passes += copy
                    // TextureCopy establishes the new layer generation even when it has no child
                    // draw.  The post-child DAG consumes this exact source generation below.
                    versions[layerTarget] = 0L
                    initializationByScope[occurrence.idI32] = LayerInitializationPlanV1.PreviousCopy(
                        parentTarget,
                        layerTarget,
                        capturedParentVersion,
                        sourceBounds,
                        destinationOrigin,
                    )
                    steps += LayerExecutionStepV1.Initialize(scopeId, copy.id)
                }
            }
            bindingsByCommand[commandIndexI32]?.let { binding ->
                if (binding.scopeI32 == null || binding.scopeI32 in activeByScope) {
                    val draws = RenderGraph.visualDraws(binding.source.passes())
                    if (draws.isNotEmpty()) {
                        val parentTarget = targetFor(binding.scopeI32)
                        val directFilterSources = directFilterSourceByCommand[commandIndexI32]
                        val directFilter = directFilterSources != null
                        val directOccurrence = directFilterSources?.let {
                            filterOccurrencesByInsertion.getValue(commandIndexI32).single { occurrence ->
                                !occurrence.isLayerOccurrence && !occurrence.isPictureOccurrence
                            }
                        }
                        val selectedDraw = draws.single()
                        val sourceDepth = selectedDraw.takeIf { draw ->
                            draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover
                        }?.let { planResourceId(PlanResourceRole.DepthStencil, bindings.indexOf(binding) + 1) }
                        var coverageDepth: PlanResourceId? = null
                        var rasterBinding: PlanPass.W6bRasterCoverageBindingV1? = null
                        val materialCoverage = directFilterSources?.let { sources ->
                            val occurrence = requireNotNull(directOccurrence)
                            filterCursor.passOrdinalI32 = passes.size
                            rasterBinding = if (occurrence.mask is MaskFilterNode.Blur ||
                                occurrence.mask is MaskFilterNode.Shader || occurrence.mask is MaskFilterNode.Table) {
                                coverageDepth = selectedDraw.takeIf { draw ->
                                    draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover
                                }?.let {
                                    planResourceId(PlanResourceRole.DepthStencil,
                                        nextCoverageDepthOrdinalI32.also { ordinal ->
                                            nextCoverageDepthOrdinalI32 = Math.addExact(ordinal, 1)
                                        }).also { depth -> coverageDepthExtents[depth] = sources.coverage.copyExtentI32() }
                                }
                                PlanPass.W6bRasterCoverageBindingV1(
                                    selectedDraw.withFinalBlendV1(BlendPlan.LegacySrcOverV1),
                                    bindings.indexOf(binding),
                                    allocateCoverageRasterData(dataByCommand[selectedDraw.commandIndex]),
                                    coverageDepth,
                                )
                            } else null
                            passes += PlanPass.FilterCoverageSourcePass(passes.size, sources.coverage.resourceId,
                                occurrence.source, rasterBinding = rasterBinding)
                            val frozen = occurrence.mask?.let {
                                W6bFilterGraphConstruction.freezeMaskOccurrence(occurrence, sources.coverage, filterCursor)
                            }
                            frozen?.let { filterResourceSpecs += it.resourceSpecs(); passes += it.passes(); it.output }
                                ?: sources.coverage
                        }
                        val directFilterSource = materialCoverage?.let {
                            allocateShadedOccurrenceSource(it, parentTarget)
                        }
                        val target = directFilterSource?.resourceId ?: parentTarget
                        val maskCoverage = materialCoverage?.resourceId
                        if (target != parentTarget) appendRender(target, emptyList(), true)
                        val laneI32 = bindings.indexOf(binding)
                        if (directFilterSource != null) physicalTargetByLane[laneI32] = target
                        val w4e = binding.source.geometrySource?.takeIf { it.w4ePayload != null }
                        // General W4d paths retain the same already-issued native PathRenderPass
                        // authority as clipped W4e paths.  The W6 graph owns the remapped pass IDs,
                        // target, and V/I/U slots; it never publishes the deferred source graph.
                        val general = binding.source.takeIf {
                            it.topology == DeferredLaneTopologyV4.GeneralGeometryAndColor
                        }?.geometrySource ?: binding.source.takeIf {
                            W4dGeneralPathPlanCompiler.isW5aMaterialCapabilityId(it.capabilityId)
                        }
                        val native = if (w4e == null && general == null) null else nativeByLane.getOrPut(laneI32, ::linkedMapOf)
                        val geometry = binding.scopeI32?.let(activeByScope::getValue)
                        fun localNative(pass: PlanPass, ordinal: Int): PlanPass {
                            val physicalTarget = physicalTargetByLane[laneI32] ?: targetFor(binding.scopeI32)
                            val sourceBinding = sourceBindingsById[physicalTarget]
                            val logicalTarget = targetFor(binding.scopeI32)
                            return pass.rebindW4eV6(ordinal, { original ->
                                laneResourceIds[laneI32].getValue(original).let { rebound ->
                                    if (rebound == logicalTarget) physicalTarget else rebound
                                }
                            }, sourceBinding?.mapping ?: geometry?.mapping,
                                sourceBinding?.copyDeviceBoundsI32() ?: geometry?.compositeDomainDeviceI32)
                        }
                        if (w4e != null) w4e.passes().filter { it is PlanPass.ClipMaskInitialize ||
                            it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold }.forEach { original ->
                            val prefix = localNative(original, passes.size)
                            passes += prefix
                            requireNotNull(native)[prefix.id] = prefix
                            if (!directFilter) binding.scopeI32?.let {
                                steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(it), prefix.id)
                            }
                        }
                        val selectedCopy = binding.source.passes().filterIsInstance<PlanPass.TextureCopy>().singleOrNull()
                        val draw = if (selectedDraw.blend is BlendPlan.DestinationReadV1) {
                            val copy = requireNotNull(selectedCopy)
                            val snapshot = planResourceId(PlanResourceRole.DestinationSnapshot, occurrences.size + bindings.indexOf(binding))
                            childSnapshots += snapshot
                            val version = DestinationVersionI64(versions.getValue(target))
                            passes += PlanPass.TextureCopy(passes.size, target, snapshot, version,
                                copy.copySourceBoundsI32(), copy.copyDestinationOriginI32(), copy.bytesPerRowI64)
                            selectedDraw.withFinalBlendV1(selectedDraw.blend.bindDestinationReadV1(version, snapshot))
                        } else selectedDraw.also { require(selectedCopy == null) }
                        if (draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover) {
                            val data = dataByCommand.getValue(draw.commandIndex)
                            val depth = requireNotNull(sourceDepth)
                            val group = canonicalPathAtomicGroup(draw)
                            val producer = PlanPass.StencilGeometryProducerV3(passes.size, target, depth, draw.commandIndex,
                                draw.copyPathGeometry(), draw.copyScissorI32(), data, group,
                                AttachmentLoadPlan.Load, AttachmentStorePlan.Store)
                            passes += producer
                            val after = DestinationVersionI64(Math.addExact(versions.getValue(target), 1L))
                            versions[target] = after.valueI64
                            // The isolated source is transparent: the selected parent blend is
                            // applied only by FilterComposite after mask materialization.
                            val autoLayerSourceDraw = if (directOccurrence?.mask is MaskFilterNode.Blur ||
                                directOccurrence?.mask is MaskFilterNode.Shader ||
                                directOccurrence?.mask is MaskFilterNode.Table)
                                draw.withFinalBlendV1(BlendPlan.LegacySrcOverV1) as PathDraw else draw
                            val cover = PlanPass.StencilCover(passes.size, target, depth, autoLayerSourceDraw, data, group,
                                AttachmentLoadPlan.Load, AttachmentStorePlan.Store, PlanDepthStencilAccess.ReadWrite,
                                PlanDepthStencilLoadStore.LoadStoreTestReset, after, maskCoverage)
                            passes += cover
                            if (w4e != null) {
                                requireNotNull(native)[producer.id] = localNative(w4e.passes().filterIsInstance<PlanPass.PathRenderPass>()
                                    .single { it.phase == PathRenderPhase.SingleSampleStencilProducer }, producer.ordinal)
                                native[cover.id] = localNative((selectedDraw as W5bW4ePathDraw).nativeColorPass, cover.ordinal)
                            } else if (general != null) {
                                requireNotNull(native)[producer.id] = localNative(general.passes().filterIsInstance<PlanPass.PathRenderPass>()
                                    .single { it.draw.commandIndex == draw.commandIndex &&
                                        it.phase == PathRenderPhase.SingleSampleStencilProducer }, producer.ordinal)
                                native[cover.id] = localNative(general.passes().filterIsInstance<PlanPass.PathRenderPass>()
                                    .single { it.draw.commandIndex == draw.commandIndex &&
                                        it.phase == PathRenderPhase.SingleSampleStencilColorCover }, cover.ordinal)
                            }
                            if (!directFilter) binding.scopeI32?.let { scope ->
                                steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(scope), producer.id)
                                steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(scope), cover.id)
                            }
                        } else {
                            // The auto-layer source is transparent, so its W5 source-stage write
                            // stays SRC_OVER. The captured parent blend remains on FilterComposite.
                            val autoLayerSourceDraw = if (directOccurrence?.mask is MaskFilterNode.Blur ||
                                directOccurrence?.mask is MaskFilterNode.Shader ||
                                directOccurrence?.mask is MaskFilterNode.Table)
                                draw.withFinalBlendV1(BlendPlan.LegacySrcOverV1) else draw
                            val pass = appendRender(target, listOf(autoLayerSourceDraw), false, maskCoverage,
                                rasterBinding)
                            if (w4e != null) requireNotNull(native)[pass.id] = localNative((selectedDraw as W5bW4ePathDraw).nativeColorPass, pass.ordinal)
                            else if (general != null) requireNotNull(native)[pass.id] = localNative(
                                general.passes().filterIsInstance<PlanPass.PathRenderPass>().single {
                                    it.draw.commandIndex == draw.commandIndex &&
                                        it.phase == PathRenderPhase.SingleSampleDirectColor
                                },
                                pass.ordinal,
                            )
                            if (!directFilter) binding.scopeI32?.let {
                                steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(it), pass.id)
                            }
                        }
                        directFilterSource?.let { source ->
                            val occurrence = requireNotNull(directOccurrence)
                            val composite = appendFrozenOccurrence(occurrence, source, parentTarget,
                                FilterCompositeOperationV1.Draw(selectedDraw.blend), materialCoverage,
                                terminalClipDeviceI32 = directTerminalClip(occurrence))
                            binding.scopeI32?.let {
                                steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(it), composite.id)
                            }
                        }
                    }
                }
            }
            (filterCommands.getOrNull(commandIndexI32) as? org.graphiks.kanvas.render.ir.SceneCommand.Draw)
                ?.node?.geometry?.let { geometry -> if (geometry is GeometryNode.Picture) {
                    val parentTarget = pictureParentTarget(commandIndexI32)
                    val rootDraw = (filterCommands[commandIndexI32] as org.graphiks.kanvas.render.ir.SceneCommand.Draw).node
                    appendPictureAggregate(pictureDiscovery.root(requireNotNull(filterScene), commandIndexI32, rootDraw),
                        parentTarget, pictureParentScope(commandIndexI32)?.let { scopeI32 ->
                            PictureLayerExecutionScope(LayerScopeIdI32(scopeI32), targetFor(scopeI32))
                        })
                } }
            ends[commandIndexI32]?.let { occurrence ->
                val geometry = activeByScope[occurrence.idI32] ?: return@let
                val scopeId = LayerScopeIdI32(occurrence.idI32)
                val target = targetFor(occurrence.idI32)
                val parentTarget = targetFor(occurrence.parentIdI32)
                val facts = restoreFactsByScope.getValue(occurrence.idI32)
                val before = DestinationVersionI64(versions[parentTarget] ?: 0L)
                val filterOffset = facts.colorFilter?.let { execution ->
                    uniformCursorI64 = alignUniform(uniformCursorI64, caps.minUniformBufferOffsetAlignment)
                    uniformCursorI64.also { uniformCursorI64 = Math.addExact(it, maxOf(16L, execution.dynamicByteCountI64)) }
                }
                val snapshot = planResourceId(PlanResourceRole.DestinationSnapshot, occurrence.idI32)
                val blend = if (facts.restoreReadsPriorDevice) facts.blend.bindDestinationReadV1(before, snapshot) else facts.blend
                val after = DestinationVersionI64(if (facts.writesParentDevice) Math.addExact(before.valueI64, 1L) else before.valueI64)
                val restore = LayerRestorePlanV1(facts.alphaF32, facts.colorFilter, blend, facts.readsPriorDevice,
                    facts.writesParentDevice, facts.restoreAffectsTransparentBlack, before, after, filterOffset)
                if (facts.restoreReadsPriorDevice) {
                    val parentExtent = targetExtent(parentTarget)
                    passes += PlanPass.TextureCopy(passes.size, parentTarget, snapshot, before,
                        RectI32(0, 0, parentExtent.width, parentExtent.height), Point2I32.Origin,
                        Math.multiplyExact(parentExtent.width.toLong(), 4L))
                }
                val childDomain = requireNotNull(geometry.compositeDomainDeviceI32)
                // A backdrop initializer may retain input halo in its physical target, but its
                // subsequent ordinary layer restore is still constrained to desired output.
                // Restoring the halo with SRC would otherwise erase the immediate parent outside
                // the save-time composite clip.
                val restoreDomain = if (backdropFiltersByBegin.containsKey(occurrence.beginCommandIndexI32))
                    requireNotNull(geometry.desiredOutputDeviceI32) else childDomain
                val parentOrigin = targetOriginDevice(parentTarget)
                val destinationOrigin = Point2I32(
                    Math.toIntExact(Math.subtractExact(restoreDomain.left.toLong(), parentOrigin.x.toLong())),
                    Math.toIntExact(Math.subtractExact(restoreDomain.top.toLong(), parentOrigin.y.toLong())),
                )
                val composite = filterLayersByBegin[occurrence.beginCommandIndexI32]?.let { filtered ->
                    val coverage = allocateOccurrenceSource(targetDeviceBounds(target), target,
                        PlanResourceRole.CoverageSource,
                        knownContentDeviceI32 = geometry.knownContentDeviceI32,
                        desiredOutputDeviceI32 = geometry.desiredOutputDeviceI32,
                        requiredInputDeviceI32 = geometry.requiredInputDeviceI32,
                        producedOutputDeviceI32 = geometry.producedOutputDeviceI32)
                    filterCursor.passOrdinalI32 = passes.size
                    passes += PlanPass.FilterCoverageSourcePass(passes.size, coverage.resourceId, filtered.source,
                        sealedAlphaSource = PictureAlphaSourceV1(null, target, versions.getValue(target),
                            requireNotNull(geometry.mapping), targetExtent(target).let { extent ->
                                RectI32(0, 0, extent.width, extent.height)
                            }),
                        sealedAlphaSampling = filterSource(target).samplingFor(coverage),
                    )
                    val frozenMask = filtered.mask?.let {
                        W6bFilterGraphConstruction.freezeMaskOccurrence(filtered, coverage, filterCursor, evaluatedMasksByOccurrence[filtered])
                    }
                    frozenMask?.let { filterResourceSpecs += it.resourceSpecs(); passes += it.passes() }
                    val source = allocateShadedOccurrenceSource(frozenMask?.output ?: coverage, target)
                    val layerSource = PlanPass.PictureSourcePass(
                        passes.size,
                        source.resourceId,
                        filtered.sourceSceneCanonicalId,
                        filtered.sourceCommandIndexI32,
                        filtered.source,
                        frozenMask?.output?.resourceId ?: coverage.resourceId,
                        target,
                        target,
                        filtered.source.pictureW5CoordinatesOrNull(),
                        sourceSampling = filterSource(target).samplingFor(source),
                    )
                    passes += layerSource
                    versions[source.resourceId] = 0L
                    steps += LayerExecutionStepV1.RenderChildren(scopeId, layerSource.id)
                    // Displacement and magnifier preserve a content-sized output coordinate
                    // domain after freezing their input demand. Matrix keeps its established
                    // wide output contract; lighting may emit onto transparent black. All other
                    // families retain their existing desired-output terminal.
                    val filterTerminalDomain = if (
                        W6bFilterGraphConstruction.hasContentOutputSamplingTerminal(filtered) &&
                        !W6bFilterGraphConstruction.hasConsumerDemandTerminal(filtered)
                    ) childDomain else geometry.desiredOutputDeviceI32
                    appendFrozenOccurrence(filtered, source, parentTarget,
                        FilterCompositeOperationV1.Layer(restore), frozenMask?.output ?: coverage, target,
                        terminalClipDeviceI32 = filterTerminalDomain)
                } ?: PlanPass.LayerComposite(passes.size, scopeId, target, parentTarget,
                    RectI32(
                        Math.toIntExact(Math.subtractExact(restoreDomain.left.toLong(), childDomain.left.toLong())),
                        Math.toIntExact(Math.subtractExact(restoreDomain.top.toLong(), childDomain.top.toLong())),
                        Math.toIntExact(Math.subtractExact(restoreDomain.right.toLong(), childDomain.left.toLong())),
                        Math.toIntExact(Math.subtractExact(restoreDomain.bottom.toLong(), childDomain.top.toLong())),
                    ), destinationOrigin, restore,
                    AttachmentLoadPlan.Load, AttachmentStorePlan.Store, after).also {
                    versions[parentTarget] = after.valueI64
                    passes += it
                }
                steps += LayerExecutionStepV1.Restore(scopeId, composite.id)
                scopePlans[occurrence.idI32] = LayerScopePlanV1(
                    scopeId,
                    occurrence.parentIdI32?.let(::LayerScopeIdI32),
                    occurrence.beginCommandIndexI32,
                    occurrence.endCommandIndexI32,
                    occurrence.childIdsI32.filter { it in activeByScope }.map(::LayerScopeIdI32),
                    requireNotNull(geometry.mapping),
                    LayerBoundsPlanV1(
                        geometry.requestedHintDeviceF64,
                        geometry.knownContentDeviceI32,
                        requireNotNull(geometry.desiredOutputDeviceI32),
                        requireNotNull(geometry.requiredInputDeviceI32),
                        geometry.producedOutputDeviceI32,
                        childDomain,
                    ),
                    initializationByScope.getValue(occurrence.idI32),
                    restore,
                    target,
                )
            }
        }
        frozenFilterResourceSpecs = immutableList(filterResourceSpecs)
        filterSourceBindings = java.util.Collections.unmodifiableMap(LinkedHashMap(sourceBindingsById))
        passes += PlanPass.ReadbackPass(passes.size, root, staging, readbackRowBytesI64,
            Math.addExact(Math.multiplyExact(readbackRowBytesI64, (extent.height - 1).toLong()), Math.multiplyExact(extent.width.toLong(), 4L)))
        rawPasses = immutableList(passes)
        val consumedMaskShaderOccurrences = rawPasses.filterIsInstance<PlanPass.FilterPass>().mapNotNull { pass ->
            ((pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding as?
                FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence)?.occurrenceIdI32
        }.toSet()
        require(consumedMaskShaderOccurrences.all(shaderMaterialSources::containsKey)) {
            "Every W6b MaskShader pass must have one existing W5 material source."
        }
        maskMaterialSourcesByOccurrence = java.util.Collections.unmodifiableMap(LinkedHashMap(
            shaderMaterialSources.filterKeys(consumedMaskShaderOccurrences::contains),
        ))
        graphTextureMaterialSourcesByAggregate = java.util.Collections.unmodifiableMap(LinkedHashMap(
            graphTextureMaterialSources,
        ))
        // IDs and semantic scope enumeration are assigned at BeginLayer in source order. Restore
        // construction is post-order, but must not leak that implementation detail into the
        // immutable plan metadata.
        frame = LayerFramePlanV1(scopePlans.values.toList(), steps, pictureStreamAggregates,
            frozenPassSchedule = rawPasses.map(PlanPass::id))

        val copySources = passes.filterIsInstance<PlanPass.TextureCopy>().map { it.source }.toSet()
        val copyDestinations = passes.filterIsInstance<PlanPass.TextureCopy>().map { it.destination }.toSet()
        val targetExtents = buildMap<PlanResourceId, SizeI32> {
            put(root, extent.copy())
            activeByScope.values.forEach { geometry -> put(targetFor(geometry.occurrence.idI32), geometry.targetExtentI32()) }
            sourceBindingsById.forEach { (id, binding) -> put(id, binding.copyExtentI32()) }
        }
        resources = immutableList(buildList {
            add(PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), extent,
                checkedTextureBytesI64(4, extent.width, extent.height, 1),
                buildSet {
                    add(PlanResourceUsage.RenderAttachment)
                    add(PlanResourceUsage.CopySource)
                    if (filterResourceSpecs.isNotEmpty()) add(PlanResourceUsage.Sampled)
                }, PlanResourceLifetime.FrameLocal, 0, passes.size))
            activeByScope.values.forEach { geometry ->
                val target = targetFor(geometry.occurrence.idI32)
                val usages = buildSet {
                    add(PlanResourceUsage.RenderAttachment)
                    add(PlanResourceUsage.Sampled)
                    if (target in copySources) add(PlanResourceUsage.CopySource)
                    if (target in copyDestinations) add(PlanResourceUsage.CopyDestination)
                }
                val targetExtent = geometry.targetExtentI32()
                add(PlanResource.of(PlanResourceRole.LayerTarget, geometry.occurrence.idI32, PlanResourceKind.Texture2D,
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), targetExtent,
                    checkedTextureBytesI64(4, targetExtent.width, targetExtent.height, 1), usages,
                    PlanResourceLifetime.FrameLocal, 0, passes.size))
            }
            scopePlans.values.filter { it.restore.blend.compositionFacts.readsPriorDevice }.forEach { scope ->
                val parentTarget = scope.parentId?.let { targetFor(it.valueI32) } ?: root
                val parentExtent = targetExtents.getValue(parentTarget)
                add(PlanResource.of(PlanResourceRole.DestinationSnapshot, scope.id.valueI32, PlanResourceKind.Texture2D,
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), parentExtent,
                    checkedTextureBytesI64(4, parentExtent.width, parentExtent.height, 1),
                    setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled), PlanResourceLifetime.FrameLocal, 0, passes.size))
            }
            sourceSpecs.forEach { spec -> add(spec.seal(passes.size)) }
            frozenFilterResourceSpecs.forEach { spec -> add(spec.seal(passes.size)) }
            coverageDepthExtents.forEach { (id, depthExtent) ->
                add(PlanResource.of(PlanResourceRole.DepthStencil, id.value.substringAfter(':').toInt(),
                    PlanResourceKind.Texture2D, PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), depthExtent,
                    checkedTextureBytesI64(4, depthExtent.width, depthExtent.height, 1),
                    setOf(PlanResourceUsage.DepthStencilAttachment), PlanResourceLifetime.FrameLocal, 0, passes.size))
            }
            add(PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null,
                Math.multiplyExact(readbackRowBytesI64, extent.height.toLong()),
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 0, passes.size))
            add(PlanResource.of(PlanResourceRole.UniformData, 0, PlanResourceKind.Buffer, null, null, uniformCursorI64,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, passes.size))
            coverageRasterData.forEach { (coverage, original) ->
                listOf(
                    coverage.vertex to original.vertex,
                    coverage.index to original.index,
                    coverage.uniform to original.uniform,
                ).forEach { (coverageId, originalId) ->
                    val sourceRow = dataRowsById.getValue(originalId)
                    add(PlanResource.of(sourceRow.role, coverageId.value.substringAfter(':').toInt(), sourceRow.kind,
                        sourceRow.format, sourceRow.copyExtent(), sourceRow.byteSize, sourceRow.usages(), sourceRow.lifetime,
                        0, passes.size, sourceRow.sampleCountI32))
                }
            }
            lanes.forEachIndexed { laneI32, lane ->
                if (bindings[laneI32].scopeI32 != null && bindings[laneI32].scopeI32 !in activeByScope) return@forEachIndexed
                lane.resources().filter { it.role !in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging) }.forEach { row ->
                    val targetSized = row.kind == PlanResourceKind.Texture2D && row.role != PlanResourceRole.DestinationSnapshot
                    val boundExtent = if (targetSized)
                        targetExtents.getValue(physicalTargetByLane[laneI32] ?: targetFor(bindings[laneI32].scopeI32)) else row.copyExtent()
                    val bytes = if (targetSized)
                        checkedTextureBytesI64(4, requireNotNull(boundExtent).width, boundExtent.height, row.sampleCountI32) else row.byteSize
                    val boundId = laneResourceIds[laneI32].getValue(row.id)
                    add(PlanResource.of(row.role, boundId.value.substringAfter(':').toInt(),
                        row.kind, row.format, boundExtent, bytes,
                        row.usages(), row.lifetime, 0, passes.size, row.sampleCountI32))
                }
            }
        })
        nativeByLane.forEach { (laneI32, native) ->
            val target = physicalTargetByLane[laneI32] ?: targetFor(bindings[laneI32].scopeI32)
            val targetExtent = targetExtents.getValue(target)
            val payload = requireNotNull(W4eNativePayloadPlan.fromDeferred(native.values.toList(), resources, targetExtent,
                caps, lanes[laneI32].sourceTable())) { "w6a.layer.w4e_payload" }
            w4eBindings += PlanW4eGeometryBindingV1(target, targetExtent, native, payload, targetOriginDevice(target))
        }
        nonUniformBytesI64 = W6aLayerPlanBudget.peak(resources, passes, caps, budget)
    }

    fun publish(
        table: MaterialPlanTable?,
        bound: List<List<PlanPass>>,
        source: SourcePhysicalConstructionV1 = SourcePhysicalConstructionV1(),
        maskMaterialRoots: Map<Int, MaterialPlanRef> = emptyMap(),
        graphTextureMaterialRoots: Map<PictureStreamAggregateIdI32, MaterialPlanRef> = emptyMap(),
    ): RenderGraph {
        require(bound.size == lanes.size || bound.isEmpty() && table == null)
        val byCommand = bound.flatMap { RenderGraph.visualDraws(it) }.associateBy { it.commandIndex }
        val byLaneAndCommand = bound.flatMapIndexed { laneI32, passes ->
            RenderGraph.visualDraws(passes).map { draw -> (laneI32 to draw.commandIndex) to draw }
        }.toMap()
        val geometryByTarget = geometries.filterNot(W6aScopeGeometry::isElided).associateBy {
            planResourceId(PlanResourceRole.LayerTarget, it.occurrence.idI32)
        }
        fun targetOriginDevice(target: PlanResourceId): Point2I32 = filterSourceBindings[target]?.originDeviceI32
            ?: if (target == root) Point2I32.Origin else
                requireNotNull(geometryByTarget[target]?.mapping).copyLayerOriginDeviceI32()
        // A captured command may appear in its retained raw W5 lane and again in a nested
        // Picture aggregate.  Its target-local raster coordinates are therefore a function
        // of both identities; caching by command alone leaks the first target's origin into
        // the later sealed aggregate.
        val localized = linkedMapOf<Pair<Int, PlanResourceId>, PlanDraw>()
        val finalBlends = RenderGraph.visualDraws(rawPasses).associate { it.commandIndex to it.blend }
        fun boundDraw(unbound: PlanDraw, target: PlanResourceId): PlanDraw {
            // Clear/DrawColor stream entries are already complete legacy-color RenderPass
            // operands.  They intentionally have no W4/W5 lane to rebind: retaining them as-is
            // is the frozen plan contract, while every material draw follows the source table.
            if (unbound.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1) return unbound
            val command = unbound.commandIndex
            return localized.getOrPut(command to target) {
            val bound = byCommand.getValue(command)
            // rawPasses owns the source-stage blend selected by the frozen schedule.  In
            // particular, a transparent mask auto-layer shades with SRC_OVER while its
            // captured DST_OUT (or other parent blend) is applied exactly once later by
            // FilterComposite.
            val draw = bound.withFinalBlendV1(finalBlends.getValue(command))
            // Lighting's Sobel neighborhood is evaluated before its terminal clip.  The
            // matching terminal FilterComposite remains the sole owner of that hard clip.
            val sourceDraw = if (command in directConsumerDemandCommands && target in filterSourceBindings)
                draw.withoutW6aTerminalClip() else draw
            if (sourceDraw is W5bW4ePathDraw) {
                val native = w4eBindings.flatMap { it.nativePasses() }.filterIsInstance<PlanPass.PathRenderPass>()
                    .single { it.draw.commandIndex == command && it.phase != PathRenderPhase.SingleSampleStencilProducer }
                W5bW4ePathDraw(native.rebindW4eV6(native.ordinal, { it }, null, null, sourceDraw.materialAuthority) as PlanPass.PathRenderPass, sourceDraw.blend)
            } else if (target == root || bindings.any { it.occurrenceInput?.commandIndexI32 == command }) sourceDraw else filterSourceBindings[target]?.let { sourceBinding ->
                localizeLayerDraw(sourceDraw, sourceBinding.mapping, sourceBinding.copyDeviceBoundsI32())
            } ?: geometryByTarget.getValue(target).let { geometry ->
                localizeLayerDraw(sourceDraw, requireNotNull(geometry.mapping), requireNotNull(geometry.compositeDomainDeviceI32))
            }
            }
        }
        fun boundDraw(command: Int, target: PlanResourceId): PlanDraw = boundDraw(byCommand.getValue(command), target)
        // Direct W6b coverage is already selected by W4, but its independent source texture
        // has its own frozen origin.  Bind that producer to the published coverage target here;
        // renderer materialization receives only the target-local W4 operand.
        fun localizedCoverageDraw(binding: PlanPass.W6bRasterCoverageBindingV1, target: PlanResourceId): PlanDraw {
            val selected = byLaneAndCommand.getValue(binding.sourceLaneI32 to binding.draw.commandIndex)
                .withFinalBlendV1(binding.draw.blend)
            if (bindings.getOrNull(binding.sourceLaneI32)?.occurrenceInput?.commandIndexI32 == binding.draw.commandIndex) {
                return selected
            }
            val source = requireNotNull(filterSourceBindings[target]) {
                "A raster W6b coverage binding requires its published source mapping."
            }
            return localizeLayerDraw(selected, source.mapping, source.copyDeviceBoundsI32())
        }
        require(maskMaterialRoots.keys == maskMaterialSourcesByOccurrence.keys)
        require(graphTextureMaterialRoots.keys == graphTextureMaterialSourcesByAggregate.keys)
        /**
         * Projects the one W5-issued source coordinate authority into the frozen MASK_SHADER
         * operand.  This is intentionally published here, after FrameSourceLayoutV4 selected
         * the sole row, rather than rebuilt by the renderer from a captured Shader or mapping.
         */
        fun maskShaderAuthority(
            root: MaterialPlanRef,
            captured: MaterialSourceConstructionV4,
        ): PlanDrawMaterialAuthority {
            val materialTable = requireNotNull(table)
            var leaf = root
            while (materialTable.entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) {
                leaf = MaterialPlanRef(leaf.indexI32 - 1)
            }
            return when (materialTable.entry(leaf).bindings) {
                is MaterialBindingPlan.GradientV1 -> PlanDrawMaterialAuthority.MaterialV1(
                    root,
                    (captured.coordinates as? SourceCoordinatesV4.V1)?.plan
                        ?: error("W6b MaskShader lost its sealed W5 V1 coordinates."),
                )
                is MaterialBindingPlan.GradientV2 -> PlanDrawMaterialAuthority.MaterialV2(
                    root,
                    (captured.coordinates as? SourceCoordinatesV4.V2)?.plan
                        ?: error("W6b MaskShader lost its sealed W5 V2 coordinates."),
                )
                is GradientInterpolationBindingV4,
                is ColorFilterBindingV4,
                -> PlanDrawMaterialAuthority.MaterialV4(root, captured.coordinates)
                is ComposedMaterialBindingV5 -> PlanDrawMaterialAuthority.MaterialV5(root)
                else -> PlanDrawMaterialAuthority.MaterialV1(root)
            }
        }
        val pictureTerminalBlends = rawPasses.mapNotNull { pass -> when (pass) {
            is PlanPass.PictureComposite -> pass.operands
            is PlanPass.FilterComposite -> (pass.operation as? FilterCompositeOperationV1.Picture)?.terminal
            else -> null
        } }.associate { it.plannedCommandId to it.blend }
        val passes = rawPasses.map { pass ->
            if (pass is PlanPass.RenderPass) {
                val local = pass.draws().map { boundDraw(it, pass.target) }
                PlanPass.RenderPass(pass.ordinal, pass.target, local, pass.load, pass.store,
                    drawDataResources = pass.drawDataResources, destinationVersionAfter = pass.destinationVersionAfter,
                    coverageSource = pass.coverageSource, w6bMaskSourceBinding = pass.w6bMaskSourceBinding,
                    plannedCommandId = pass.plannedCommandId,
                    materialDeviceOriginI32 = pass.copyMaterialDeviceOriginI32() ?: targetOriginDevice(pass.target))
            } else when (pass) {
                is PlanPass.FilterPass -> {
                    val operation: FilterPassOperationV1 = when (val original = pass.operation) {
                        is FilterPassOperationV1.MaskShader -> when (val binding = original.materialBinding) {
                            is FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence -> {
                                val material = maskMaterialRoots.getValue(binding.occurrenceIdI32)
                                val captured = maskMaterialSourcesByOccurrence.getValue(binding.occurrenceIdI32)
                                val uniformIdentity = if (captured.pending ||
                                    captured.resolvedSource?.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) {
                                    RawMaterialRequirementsV2.measureV4(requireNotNull(table), material).canonicalIdentity
                                } else RawMaterialRequirementsV2.measureLegacy(requireNotNull(table), material).canonicalIdentity
                                val uniformResource = source.uniforms.getValue(uniformIdentity)
                                val uniformCapacity = source.resources.single { it.id == uniformResource }.byteSize
                                FilterPassOperationV1.MaskShader(
                                    FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned(
                                        binding.occurrenceIdI32,
                                        material,
                                        uniformResource,
                                        0L,
                                        uniformCapacity,
                                        maskShaderAuthority(material, captured),
                                        pass.evaluationKey.mapping,
                                        pass.evaluationKey.mapping.copyLayerOriginDeviceI32(),
                                    ),
                                    original.bounds,
                                    original.sampling,
                                )
                            }
                            is FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned -> original
                        }
                        else -> original
                    }
                    val resolved = when (operation) {
                        is FilterPassOperationV1.ColorFilter -> {
                            operation.withUniformBinding(source.w6cColorUniformBindings.getValue(
                                W6cComposePlanner.colorUniformIdentity(operation.execution),
                            ))
                        }
                        else -> operation
                    }
                    PlanPass.FilterPass(pass.ordinal, pass.inputs(), pass.output, pass.evaluationKey, resolved)
                }
                is PlanPass.StencilGeometryProducerV3 -> {
                    val draw = boundDraw(pass.commandIndexI32, pass.target) as PathDraw
                    PlanPass.StencilGeometryProducerV3(pass.ordinal, pass.target, pass.depthStencil, pass.commandIndexI32,
                        draw.copyPathGeometry(), draw.copyScissorI32(), pass.drawDataResources, pass.atomicGroup, pass.load, pass.store)
                }
                is PlanPass.StencilCover -> PlanPass.StencilCover(pass.ordinal, pass.target, pass.depthStencil,
                    boundDraw(pass.draw.commandIndex, pass.target) as PathDraw, pass.drawDataResources, pass.atomicGroup,
                    pass.load, pass.store, pass.depthStencilAccess, pass.depthStencilLoadStore, pass.destinationVersionAfter,
                    pass.coverageSource, pass.plannedCommandId)
                is PlanPass.FilterCoverageSourcePass -> pass.rasterBinding?.let { binding ->
                    PlanPass.FilterCoverageSourcePass(pass.ordinal, pass.output, pass.occurrence,
                        pass.deferSourceDrawClip, pass.pictureCoordinates, pass.sealedAlphaSource,
                        pass.sealedAlphaSampling, binding.withDraw(localizedCoverageDraw(binding, pass.output)))
                } ?: pass
                is PlanPass.PictureSourcePass -> {
                    val request = pass.graphTextureRequest ?: return@map pass
                    val material = graphTextureMaterialRoots.getValue(request.aggregateId)
                    val identity = graphTextureMaterialSourcesByAggregate.getValue(request.aggregateId)
                    val uniformIdentity = if (identity.pending ||
                        identity.resolvedSource?.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) {
                        RawMaterialRequirementsV2.measureV4(requireNotNull(table), material).canonicalIdentity
                    } else RawMaterialRequirementsV2.measureLegacy(requireNotNull(table), material).canonicalIdentity
                    val parentFilterBinding = request.colorFilter?.let { filter ->
                        val footprint = RawMaterialRequirementsV2.measureV4(requireNotNull(table), material)
                        val bindingByteCount = maxOf(16L, filter.dynamicByteCountI64)
                        val offset = if (filter.dynamicByteCountI64 == 0L) 0L else footprint.sourceUniformByteCountI64
                        require(Math.addExact(offset, bindingByteCount) <= footprint.uniformByteCountI64) {
                            "W6b graph-texture parent filter must retain its W5 source-uniform binding."
                        }
                        offset to footprint.uniformByteCountI64
                    }
                    PlanPass.PictureSourcePass(
                        pass.ordinal,
                        pass.output,
                        pass.sourceSceneCanonicalId,
                        pass.sourceCommandIndexI32,
                        pass.occurrence,
                        pass.coverageSource,
                        pass.layerInput,
                        pass.parentTarget,
                        pass.pictureCoordinates,
                        pass.pictureSourceLocator,
                        pass.plannedCommandId,
                        pass.aggregateId,
                        graphTextureRequest = null,
                        graphTextureOperand = GraphTextureSourceOperandV1(
                            aggregateId = request.aggregateId,
                            sealedSourceId = request.sealedSourceId,
                            sealedSourceGenerationI64 = request.sealedSourceGenerationI64,
                            targetOriginDeviceI32 = request.copyTargetOriginDeviceI32(),
                            sampleBoundsTargetI32 = request.copySampleBoundsTargetI32(),
                            mapping = request.mapping,
                            deferredCompositeClip = request.deferredCompositeClip,
                            material = material,
                            uniformResource = source.uniforms.getValue(uniformIdentity),
                            colorFilterUniformOffsetI64 = parentFilterBinding?.first,
                            colorFilterUniformByteCountI64 = parentFilterBinding?.second,
                            alphaF32 = request.alphaF32,
                            colorFilter = request.colorFilter,
                            finalBlend = pictureTerminalBlends.getValue(requireNotNull(pass.plannedCommandId)),
                            coverageOperation = request.coverageOperation,
                            clipToDeviceF64 = request.copyClipToDeviceF64(),
                        ),
                        sourceSampling = pass.sourceSampling,
                    )
                }
                is PlanPass.TextureCopy -> if (pass.destination in childSnapshots && pass.source != root) {
                    val mapping = filterSourceBindings[pass.source]?.mapping ?: requireNotNull(geometryByTarget.getValue(pass.source).mapping)
                    PlanPass.TextureCopy(pass.ordinal, pass.source, pass.destination, pass.destinationVersion,
                        requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(requireNotNull(pass.copySourceBoundsI32()))),
                        pass.copyDestinationOriginI32(), pass.bytesPerRowI64)
                } else pass
                else -> pass
            }
        }
        val allResources = resources + source.resources
        val peak = W6aLayerPlanBudget.peak(allResources, passes, caps, budget)
        val construction = RenderGraph.construct(id, W6aLayerPlanCompiler.CAPABILITY_ID, extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, caps, budget, RenderGraph.visualDraws(passes).size, allResources, passes,
            passes.zipWithNext { first, second -> PlanPassDependency(first.id, second.id) }, peak, table)
        val sourceNonUniform = Math.subtractExact(construction.peakFrameLocalBytes,
            source.resources.filter { it.role == PlanResourceRole.SourceUniformData }.fold(0L) { bytes, row -> Math.addExact(bytes, row.byteSize) })
        val frozenMaterialRows = buildList {
            maskMaterialRoots.forEach { (occurrenceIdI32, material) ->
                val source = maskMaterialSourcesByOccurrence.getValue(occurrenceIdI32)
                if (source.pending || source.resolvedSource?.materialAuthority is PlanDrawMaterialAuthority.MaterialV4)
                    add(material to source.coordinates)
            }
            graphTextureMaterialRoots.forEach { (aggregateId, material) ->
                val source = graphTextureMaterialSourcesByAggregate.getValue(aggregateId)
                if (source.pending || source.resolvedSource?.materialAuthority is PlanDrawMaterialAuthority.MaterialV4)
                    add(material to source.coordinates)
            }
        }
        return RenderGraph.publishW6a(construction, frame,
            packConstructedFrame(listOf(construction), table, sourceNonUniform, frozenMaterialRows), SourcePhysicalConstructionV1(
                resources = source.resources,
                uniforms = source.uniforms,
                caches = source.caches,
                w6cColorUniformBindings = source.w6cColorUniformBindings,
                w4eGeometry = w4eBindings.map { binding ->
                    binding.bindSources(localized.entries.associate { (key, draw) -> key.first to draw })
                },
            ))
    }

    /** Appended after all native W5 lanes, preserving one source-table/publish authority. */
    fun maskMaterialSources(): List<Pair<Int, MaterialSourceConstructionV4>> =
        maskMaterialSourcesByOccurrence.entries.map { it.key to it.value }

    /** One W5 source row per isolated Picture paint, resolved beside MaskShader rows. */
    fun graphTextureMaterialSources(): List<Pair<PictureStreamAggregateIdI32, MaterialSourceConstructionV4>> =
        graphTextureMaterialSourcesByAggregate.entries.map { it.key to it.value }

    /** Every W6c image ColorFilter is compiled by W5f before the sole source layout issues rows. */
    fun imageColorFilterExecutions(): List<ColorFilterExecutionPlanV1> = rawPasses
        .filterIsInstance<PlanPass.FilterPass>()
        .mapNotNull { (it.operation as? FilterPassOperationV1.ColorFilter)?.execution }
        .distinctBy { it.canonicalIdentity }

    private fun sealGeometry(
        occurrence: W6aLayerPlanCompiler.ScopeOccurrence,
        restoreFacts: W6aRestoreFacts,
        desired: RectI32?,
        known: RectI32?,
        produced: RectI32?,
        physicalInput: RectI32?,
        snapshotInput: RectI32?,
        sourceDemand: RectI32? = desired,
    ): W6aScopeGeometry {
        if (desired == null) return W6aScopeGeometry(occurrence, null, null, known, null, null, produced, null)
        val transform = occurrence.descriptor.transform
        val localToDevice = Matrix3x3F64(
            transform.sx.toDouble(), transform.kx.toDouble(), transform.tx.toDouble(),
            transform.ky.toDouble(), transform.sy.toDouble(), transform.ty.toDouble(),
            transform.persp0.toDouble(), transform.persp1.toDouble(), transform.persp2.toDouble(),
        )
        val hint = occurrence.descriptor.copyBounds()?.takeUnless { it.isEmpty }?.let { bounds ->
            localToDevice.mapRectBoundsF64OrNull(RectF64(bounds.left.toDouble(), bounds.top.toDouble(),
                bounds.right.toDouble(), bounds.bottom.toDouble()))
                ?: throw IllegalArgumentException(W6aPlanDiagnostics.MappingHorizon)
        }
        val hintDomain = hint?.roundOutToRectI32OrNull()
            ?: if (hint == null) null else throw IllegalArgumentException(W6aPlanDiagnostics.MappingOverflow)
        val required = snapshotInput?.copy() ?: sourceDemand?.copy() ?: desired.copy()
        val effective = if (snapshotInput != null) {
            val sourceAndOutput = union(snapshotInput, desired)
            physicalInput?.let { input -> intersect(input, desired)?.let { union(sourceAndOutput, it) } ?: sourceAndOutput }
                ?: sourceAndOutput
        } else if (restoreFacts.previousContentRequiresFullParentDomain || restoreFacts.backdropRequiresFullParentDomain ||
            restoreFacts.restoreAffectsTransparentBlack) desired
            else if (physicalInput == null) desired else hintDomain?.let { union(physicalInput, it) } ?: physicalInput
        val composite = if (snapshotInput == null) intersect(effective, sourceDemand ?: desired) else effective
        if (composite == null) return W6aScopeGeometry(occurrence, null, hint, known, desired, required, produced, null)
        val mapping = LayerMappingF64.ofOrNull(localToDevice, Point2I32(composite.left, composite.top))
            ?: throw IllegalArgumentException(W6aPlanDiagnostics.NonFiniteTransform)
        return W6aScopeGeometry(occurrence, mapping, hint, known, desired, required, produced, composite)
    }

    private fun desiredOutput(descriptor: org.graphiks.kanvas.render.ir.LayerDescriptor, parentDomain: RectI32): RectI32? = when (val clip = descriptor.compositeClip) {
        null, ClipStackNode.Empty -> parentDomain.copy()
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) null
            else intersect(RectF64(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
                .roundOutToRectI32OrNull() ?: throw IllegalArgumentException(W6aPlanDiagnostics.MappingOverflow), parentDomain)
        }
        is ClipStackNode.Operations -> exactHardRectCompositeDomain(clip, parentDomain).let { resolution ->
            if (resolution.supported) resolution.domain
            else throw IllegalArgumentException("${W6aPlanDiagnostics.UnsupportedChild}: complex composite clip")
        }
    }

    /**
     * Picture replay may retain a sequence of rectangular intersections instead of its compact
     * device-rect form.  This is reducible only when every AA rectangle covers the complete
     * parent target or contains the resulting hard domain strictly, hence contributes full
     * coverage everywhere the restore can write.
     * Difference, non-rectangular geometry, and non-axis-aligned transforms remain unreduced.
     */
    private fun exactHardRectCompositeDomain(
        operations: ClipStackNode.Operations,
        parentDomain: RectI32,
    ): ExactCompositeClipResolution {
        if (operations.entryCount == 0) return ExactCompositeClipResolution.Unsupported
        var result: RectI32? = parentDomain.copy()
        val fullCoverageBounds = mutableListOf<RectF64>()
        operations.forEach { entry ->
            if (entry.operation != ClipOperation.INTERSECT) {
                return ExactCompositeClipResolution.Unsupported
            }
            val geometry = entry.geometry as? GeometryNode.Rect ?: return ExactCompositeClipResolution.Unsupported
            val transform = (entry.transform as? ClipTransformSnapshot.Known)?.copyMatrixF32()
                ?: return ExactCompositeClipResolution.Unsupported
            if (!transform.isScaleTranslate() || !listOf(
                    transform.sx, transform.kx, transform.tx,
                    transform.ky, transform.sy, transform.ty,
                    transform.persp0, transform.persp1, transform.persp2,
                ).all(Float::isFinite)) {
                return ExactCompositeClipResolution.Unsupported
            }
            val bounds = geometry.copyBounds()
            val mappedBounds = Matrix3x3F64(
                transform.sx.toDouble(), transform.kx.toDouble(), transform.tx.toDouble(),
                transform.ky.toDouble(), transform.sy.toDouble(), transform.ty.toDouble(),
                transform.persp0.toDouble(), transform.persp1.toDouble(), transform.persp2.toDouble(),
            ).mapRectBoundsF64OrNull(RectF64(
                bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
            )) ?: return ExactCompositeClipResolution.Unsupported
            if (entry.antiAlias) {
                fullCoverageBounds += mappedBounds
            } else {
                val mapped = mappedBounds.roundOutToRectI32OrNull()
                    ?: return ExactCompositeClipResolution.Unsupported
                result = result?.let { current -> intersect(current, mapped) }
            }
        }
        val final = result ?: return ExactCompositeClipResolution.Supported(null)
        if (fullCoverageBounds.any { bounds ->
                val coversParent = bounds.left <= parentDomain.left.toDouble() &&
                    bounds.top <= parentDomain.top.toDouble() &&
                    bounds.right >= parentDomain.right.toDouble() &&
                    bounds.bottom >= parentDomain.bottom.toDouble()
                !coversParent && (final.left.toDouble() <= bounds.left || final.top.toDouble() <= bounds.top ||
                    final.right.toDouble() >= bounds.right || final.bottom.toDouble() >= bounds.bottom)
            }) return ExactCompositeClipResolution.Unsupported
        return ExactCompositeClipResolution.Supported(final)
    }

    private sealed interface ExactCompositeClipResolution {
        val supported: Boolean
        val domain: RectI32?

        data object Unsupported : ExactCompositeClipResolution {
            override val supported: Boolean = false
            override val domain: RectI32? = null
        }

        data class Supported(override val domain: RectI32?) : ExactCompositeClipResolution {
            override val supported: Boolean = true
        }
    }

    private fun localizeLayerDraw(draw: PlanDraw, mapping: LayerMappingF64, targetDomainDeviceI32: RectI32): PlanDraw {
        if (draw is ClippedPlanDraw) {
            fun localScissor(strategy: ClipPlanStrategy): RectI32 = when (strategy) {
                is ClipPlanStrategy.Scissor -> {
                    val current = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(strategy.copyDomainI32()))
                    strategy.child?.let { child -> requireNotNull(intersect(current, localScissor(child))) } ?: current
                }
                // W6b's direct mask source can retain only the already-admitted exact
                // hard scissor here.  Resource-backed clips continue through their W4e lane.
                is ClipPlanStrategy.Stencil,
                is ClipPlanStrategy.Mask,
                is ClipPlanStrategy.InverseMask,
                is ClipPlanStrategy.InverseDomain,
                -> throw IllegalArgumentException("${W6aPlanDiagnostics.UnsupportedChild}: non-scissor clipped source")
            }
            val local = localizeLayerDraw(draw.source, mapping, targetDomainDeviceI32)
            return local.withLocalizedScissorV6(requireNotNull(intersect(local.copyScissorV6(), localScissor(draw.strategy))))
        }
        if (draw is W5bVerticesDraw) return W5bVerticesDraw(draw.commandIndex, draw.materialAuthority, draw.geometryF32,
            draw.copyColorsRgba8(), requireNotNull(draw.transformF32.relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32())),
            requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(requireNotNull(intersect(draw.copyBoundsI32(), targetDomainDeviceI32)))),
            requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(requireNotNull(intersect(draw.copyScissorI32(), targetDomainDeviceI32)))),
            draw.blend, draw.primitiveBlend, draw.sealedUploadPayloadOrNull())
        if (draw is W5bPointDraw) return requireNotNull(draw.relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32(),
            requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(requireNotNull(intersect(draw.copyScissorI32(), targetDomainDeviceI32))))))
        if (draw is PathFillDraw || draw is PathStrokeDraw) {
            val authority = draw.materialAuthority
            val material = authority.materialPlanRef()
            val scissor = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(
                requireNotNull(intersect(w6aScissorI32(draw), targetDomainDeviceI32))))
            val origin = mapping.copyLayerOriginDeviceI32()
            return when (draw) {
                is PathFillDraw -> PathFillDraw.ofMaterial(draw.commandIndex, material,
                    requireNotNull(draw.copyGeometryF32().relativeToOriginI32OrNull(origin)), draw.strategy, scissor,
                    draw.blend, draw.materialCoordinates, draw.materialCoordinatesV2,
                    (authority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
                    authority is PlanDrawMaterialAuthority.MaterialV5)
                is PathStrokeDraw -> {
                    val shape = requireNotNull(draw.copyGeometryF32().relativeToOriginI32OrNull(origin))
                    if (authority is PlanDrawMaterialAuthority.MaterialV4) PathStrokeDraw.ofMaterialV4(draw.commandIndex,
                        material, shape, scissor, draw.mode, draw.styleF64, draw.blend, authority.coordinates)
                    else PathStrokeDraw.ofMaterial(draw.commandIndex, material, shape, scissor, draw.mode, draw.styleF64,
                        draw.blend, draw.materialCoordinates, draw.materialCoordinatesV2, authority is PlanDrawMaterialAuthority.MaterialV5)
                }
            }
        }
        if (draw is GeneralPathDraw) {
            val geometry = when (val source = draw.copyPathGeometry()) {
                is PathDrawGeometry.Fill -> PathDrawGeometry.Fill(requireNotNull(
                    source.valueF32.relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32())))
                is PathDrawGeometry.Stroke -> PathDrawGeometry.Stroke(requireNotNull(
                    source.valueF32.relativeToOriginI32OrNull(mapping.copyLayerOriginDeviceI32())))
                is PathDrawGeometry.InverseDomainSource -> PathDrawGeometry.InverseDomainSource.of(
                    source.copySourcePath(), requireNotNull(source.copySourceTransform().relativeToOriginI32OrNull(
                        mapping.copyLayerOriginDeviceI32())))
                PathDrawGeometry.Empty -> source
            }
            val scissor = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(
                requireNotNull(intersect(draw.copyScissorI32(), targetDomainDeviceI32))))
            return draw.rebindGeometryV6(geometry, scissor)
        }
        if (draw is AnalyticRectDraw || draw is AnalyticRRectDraw) {
            val raster = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(w6aRasterBoundsI32(draw)))
            val scissor = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(
                requireNotNull(intersect(w6aScissorI32(draw), targetDomainDeviceI32))))
            val authority = draw.materialAuthority
            val material = authority.materialPlanRef()
            return when (draw) {
                is AnalyticRectDraw -> AnalyticRectDraw.ofMaterial(draw.commandIndex, material,
                    requireNotNull(mapping.mapDeviceRectToLayerF32OrNull(draw.copyDeviceBounds())), raster, scissor,
                    draw.blend, draw.materialCoordinates, draw.materialCoordinatesV2,
                    (authority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
                    authority is PlanDrawMaterialAuthority.MaterialV5)
                is AnalyticRRectDraw -> {
                    val shape = requireNotNull(mapping.mapDeviceRRectToLayerF32OrNull(draw.copyDeviceShape()))
                    if (authority is PlanDrawMaterialAuthority.MaterialV4)
                        AnalyticRRectDraw.ofMaterialV4(draw.commandIndex, material, draw.origin, shape, raster,
                            scissor, draw.blend, authority.coordinates)
                    else AnalyticRRectDraw.ofMaterial(draw.commandIndex, material, draw.origin, shape, raster,
                        scissor, draw.blend, draw.materialCoordinates, draw.materialCoordinatesV2,
                        authority is PlanDrawMaterialAuthority.MaterialV5)
                }
            }
        }
        val solid = draw as? SolidRectDraw ?: error("w6a.layer.unsupported_child")
        val visibleDevice = requireNotNull(intersect(solid.copyVisibleBounds(), targetDomainDeviceI32))
        val scissorDevice = requireNotNull(intersect(solid.copyScissor(), targetDomainDeviceI32))
        val visibleLayer = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(visibleDevice))
        val scissorLayer = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(scissorDevice))
        return when (val authority = solid.materialAuthority) {
            is PlanDrawMaterialAuthority.LegacyColorV1 -> SolidRectDraw.of(
                solid.commandIndex, authority.copyColorF32(), visibleLayer, scissorLayer, solid.coverage, solid.sample, solid.blend,
            )
            is PlanDrawMaterialAuthority.MaterialV3 -> error("w6a.layer.unsupported_child")
            else -> SolidRectDraw.ofMaterial(
                solid.commandIndex, authority.materialPlanRef(), visibleLayer, scissorLayer, solid.coverage, solid.sample,
                solid.blend, solid.materialCoordinates, solid.materialCoordinatesV2,
                (authority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates, authority is PlanDrawMaterialAuthority.MaterialV5,
            )
        }
    }

    /** Removes only the deferred direct-filter clip wrappers; geometry remains W5-owned. */
    private fun PlanDraw.withoutW6aTerminalClip(): PlanDraw {
        var source = this
        while (source is ClippedPlanDraw) source = source.source
        return source
    }

    /** Bakes an already-admitted hard clip into the existing W4 draw; no renderer clip planning occurs. */
    private fun PlanDraw.copyScissorV6(): RectI32 = when (this) {
        is SolidRectDraw -> copyScissor()
        is AnalyticRectDraw -> copyScissor()
        is AnalyticRRectDraw -> copyScissor()
        is PathDraw -> copyScissorI32()
        is W5bPointDraw -> copyScissorI32()
        is W5bVerticesDraw -> copyScissorI32()
        else -> error("${W6aPlanDiagnostics.UnsupportedChild}: clipped source geometry")
    }

    private fun PlanDraw.withLocalizedScissorV6(scissor: RectI32): PlanDraw {
        val ref = materialAuthority.materialPlanRef()
        val v4 = (materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates
        val composed = materialAuthority is PlanDrawMaterialAuthority.MaterialV5
        return when (this) {
            is SolidRectDraw -> SolidRectDraw.ofMaterial(commandIndex, ref, copyVisibleBounds(), scissor,
                coverage, sample, blend, materialCoordinates, materialCoordinatesV2, v4, composed)
            is AnalyticRectDraw -> AnalyticRectDraw.ofMaterial(commandIndex, ref, copyDeviceBounds(), copyRasterBounds(),
                scissor, blend, materialCoordinates, materialCoordinatesV2, v4, composed)
            is AnalyticRRectDraw -> if (v4 != null) AnalyticRRectDraw.ofMaterialV4(commandIndex, ref, origin,
                copyDeviceShape(), copyRasterBounds(), scissor, blend, v4)
                else AnalyticRRectDraw.ofMaterial(commandIndex, ref, origin, copyDeviceShape(), copyRasterBounds(),
                    scissor, blend, materialCoordinates, materialCoordinatesV2, composed)
            is PathFillDraw -> PathFillDraw.ofMaterial(commandIndex, ref, copyGeometryF32(), strategy, scissor,
                blend, materialCoordinates, materialCoordinatesV2, v4, composed)
            is PathStrokeDraw -> if (v4 != null) PathStrokeDraw.ofMaterialV4(commandIndex, ref, copyGeometryF32(), scissor,
                mode, styleF64, blend, v4) else PathStrokeDraw.ofMaterial(commandIndex, ref, copyGeometryF32(), scissor,
                mode, styleF64, blend, materialCoordinates, materialCoordinatesV2, composed)
            is GeneralPathDraw -> rebindGeometryV6(copyPathGeometry(), scissor)
            else -> error("${W6aPlanDiagnostics.UnsupportedChild}: clipped source geometry")
        }
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }
    private fun union(first: RectI32, second: RectI32): RectI32 = RectI32(
        minOf(first.left, second.left), minOf(first.top, second.top), maxOf(first.right, second.right), maxOf(first.bottom, second.bottom),
    )

    private fun unionOrNull(first: RectI32?, second: RectI32?): RectI32? = when {
        first == null -> second?.copy()
        second == null -> first.copy()
        else -> union(first, second)
    }

    private fun sealRestoreFacts(occurrence: W6aLayerPlanCompiler.ScopeOccurrence): W6aRestoreFacts {
        val paint = occurrence.descriptor.paint
        val colorFilter = paint?.colorFilter?.let { filter ->
            (ColorFilterPlanCompilerV1.compile(filter) as? ColorFilterCompileResultV1.Ready)?.execution
                ?: throw W6aRestoreAdmissionFailure("${W6aPlanDiagnostics.UnsupportedRestore}: restore color filter")
        }
        val blend = requireNotNull(FinalBlendPlanner.plan(occurrence.descriptor.blend,
            CoveragePlan.FullOrScissor, SamplePlan.SingleSample,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1())) {
            "${W6aPlanDiagnostics.UnsupportedRestore}: restore blend"
        }
        return W6aRestoreFacts(
            paint?.color?.alpha?.div(255f) ?: 1f,
            colorFilter,
            blend,
            occurrence.descriptor.initWithPrevious,
            occurrence.descriptor.backdrop !is EffectStack.Empty,
            paint?.imageFilter != null || paint?.maskFilter != null || occurrence.descriptor.effects !is EffectStack.Empty,
        )
    }

    /** The W5 filter proof owns bytes; W6 admits only the already-sealed binding requirements. */
    private fun admitRestoreBindings(facts: W6aRestoreFacts) {
        val bindingCountI32 = 1 + (if (facts.colorFilter == null) 0 else 1) + (if (facts.restoreReadsPriorDevice) 1 else 0)
        val sampledTextureCountI32 = 1 + if (facts.restoreReadsPriorDevice) 1 else 0
        try {
            facts.colorFilter?.let { filter -> requireColorUniformBindingV4(maxOf(16L, filter.dynamicByteCountI64), caps, bindingCountI32) }
            require(caps.maxBindingsPerBindGroupI32?.let { it >= bindingCountI32 } == true &&
                caps.maxSampledTexturesPerShaderStageI32?.let { it >= sampledTextureCountI32 } == true) {
                "restore sampled/bind-group capability"
            }
        } catch (failure: RawMaterialRequirementsV2.Refusal) {
            throw W6aRestoreAdmissionFailure("${W6aPlanDiagnostics.UnsupportedRestore}: ${failure.code}")
        } catch (failure: IllegalArgumentException) {
            throw W6aRestoreAdmissionFailure("${W6aPlanDiagnostics.UnsupportedRestore}: ${failure.message}")
        }
    }

    private fun alignUniform(bytesI64: Long, alignmentI32: Int): Long {
        val alignmentI64 = alignmentI32.toLong()
        return Math.multiplyExact(Math.addExact(bytesI64, alignmentI64 - 1L) / alignmentI64, alignmentI64)
    }
}
