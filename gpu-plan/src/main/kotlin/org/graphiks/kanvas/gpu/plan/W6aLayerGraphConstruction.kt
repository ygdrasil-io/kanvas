package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.mapRectBoundsF64OrNull
import org.graphiks.math.matrix.relativeToOriginI32OrNull

/** One unpublished W3/W5 lane, attached to its exact command occurrence and immediate target. */
internal class W6aLayerSourceBinding(
    val scopeI32: Int?,
    val firstCommandIndexI32: Int,
    val source: SourceDeferredRenderConstructionV4,
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
    val previousContentRequiresFullParentDomain: Boolean = initWithPrevious && !isIdentityPreviousPassthrough
    val readsPriorDevice: Boolean = restoreReadsPriorDevice || previousContentRequiresFullParentDomain
    val writesParentDevice: Boolean = blend.compositionFacts.writesParentDevice
    val restoreAffectsTransparentBlack: Boolean = blend.finalRestoreAffectsTransparentBlackV1(colorFilter)
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
    bindings: List<W6aLayerSourceBinding>,
    private val filterScene: org.graphiks.kanvas.render.ir.SceneSnapshot? = null,
) {
    private val occurrences = immutableList(occurrences)
    private val bindings = immutableList(bindings)
    val lanes = immutableList(bindings.map { it.source })
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
            RenderGraph.visualDraws(source.passes()).all { (it is SolidRectDraw || it is AnalyticRectDraw ||
                it is AnalyticRRectDraw || it is PathFillDraw || it is PathStrokeDraw || it is GeneralPathDraw ||
                    it is W5bPointDraw || it is W5bVerticesDraw || it is W5bW4ePathDraw) } }) {
            "w6a.layer.unsupported_child"
        }

        val restoreFactsByScope = occurrences.associate { occurrence ->
            occurrence.idI32 to sealRestoreFacts(occurrence)
        }
        val directKnownByScope = arrayOfNulls<RectI32>(occurrences.size)
        bindings.forEach { binding ->
            val scopeIdI32 = binding.scopeI32 ?: return@forEach
            RenderGraph.visualDraws(binding.source.passes()).forEach { draw ->
                intersect(w6aRasterBoundsI32(draw), w6aScissorI32(draw))?.let { bounds ->
                    directKnownByScope[scopeIdI32] = unionOrNull(directKnownByScope[scopeIdI32], bounds)
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

        // Restore output is post-order.  This is an ID-indexed table rather than recursive
        // descendant walks, so a legal GraphLimits depth has linear work and stack use.
        val knownContentByScope = arrayOfNulls<RectI32>(occurrences.size)
        val producedOutputByScope = arrayOfNulls<RectI32>(occurrences.size)
        for (indexI32 in occurrences.indices.reversed()) {
            val occurrence = occurrences[indexI32]
            val desired = desiredOutputByScope[occurrence.idI32]
            var known = if (occurrence.descriptor.initWithPrevious) desired?.copy()
                else directKnownByScope[occurrence.idI32]
            occurrence.childIdsI32.forEach { childIdI32 ->
                known = unionOrNull(known, producedOutputByScope[childIdI32])
            }
            known = desired?.let { desiredDomain -> known?.let { intersect(it, desiredDomain) } }
            knownContentByScope[occurrence.idI32] = known
            producedOutputByScope[occurrence.idI32] = when {
                desired == null -> null
                restoreFactsByScope.getValue(occurrence.idI32).restoreAffectsTransparentBlack -> desired.copy()
                else -> known?.copy()
            }
        }

        // Only after output is known do we freeze target domains.  A parent retains every child
        // target domain it must composite, including a child whose restore expands transparent
        // black beyond the parent's direct content.
        val geometryByScope = arrayOfNulls<W6aScopeGeometry>(occurrences.size)
        for (indexI32 in occurrences.indices.reversed()) {
            val occurrence = occurrences[indexI32]
            var physicalInput = directKnownByScope[occurrence.idI32]
            occurrence.childIdsI32.forEach { childIdI32 ->
                physicalInput = unionOrNull(physicalInput, geometryByScope[childIdI32]?.compositeDomainDeviceI32)
            }
            geometryByScope[occurrence.idI32] = sealGeometry(
                occurrence,
                restoreFactsByScope.getValue(occurrence.idI32),
                desiredOutputByScope[occurrence.idI32],
                knownContentByScope[occurrence.idI32],
                producedOutputByScope[occurrence.idI32],
                physicalInput,
            )
        }
        geometries = immutableList(occurrences.map { requireNotNull(geometryByScope[it.idI32]) })
        val activeByScope = geometries.filterNot(W6aScopeGeometry::isElided).associateBy { it.occurrence.idI32 }
        activeByScope.values.forEach { geometry -> admitRestoreBindings(restoreFactsByScope.getValue(geometry.occurrence.idI32)) }

        val passes = mutableListOf<PlanPass>()
        val filterResourceSpecs = mutableListOf<W6bFilterGraphConstruction.ResourceSpec>()
        val steps = mutableListOf<LayerExecutionStepV1>()
        val scopePlans = linkedMapOf<Int, LayerScopePlanV1>()
        val initializationByScope = linkedMapOf<Int, LayerInitializationPlanV1>()
        val versions = mutableMapOf<PlanResourceId, Long>()
        var uniformCursorI64 = 16L

        val sourceBindingsById = linkedMapOf<PlanResourceId, W6bFilterGraphConstruction.SourceBinding>()
        val sourceSpecs = mutableListOf<W6bFilterGraphConstruction.ResourceSpec>()
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
            val binding = W6bFilterGraphConstruction.SourceBinding(target, targetExtent(target), origin, mapping,
                RectI32(origin.x, origin.y, Math.addExact(origin.x, targetExtent(target).width), Math.addExact(origin.y, targetExtent(target).height)))
            return binding
        }
        fun targetDeviceBounds(target: PlanResourceId): RectI32 = filterSource(target).copyDeviceBoundsI32()
        fun allocateOccurrenceSource(
            domain: RectI32,
            parentTarget: PlanResourceId,
            copyDestination: Boolean = false,
        ): W6bFilterGraphConstruction.SourceBinding {
            val parent = filterSource(parentTarget)
            val mapping = requireNotNull(LayerMappingF64.ofOrNull(parent.mapping.copyLocalToDeviceF64(), Point2I32(domain.left, domain.top)))
            val id = planResourceId(PlanResourceRole.FilterSource, nextFilterSourceOrdinalI32)
            nextFilterSourceOrdinalI32 = Math.addExact(nextFilterSourceOrdinalI32, 1)
            val binding = W6bFilterGraphConstruction.SourceBinding(id, SizeI32(domain.width(), domain.height()),
                Point2I32(domain.left, domain.top), mapping, domain)
            sourceSpecs += W6bFilterGraphConstruction.ResourceSpec(
                id,
                PlanResourceRole.FilterSource,
                binding.copyExtentI32(),
                if (copyDestination) setOf(PlanResourceUsage.CopyDestination) else emptySet(),
            )
            sourceBindingsById[id] = binding
            return binding
        }
        val dataByCommand = linkedMapOf<Int, PlanDrawDataResources>()
        val laneResourceIds = lanes.mapIndexed { laneI32, lane -> lane.resources().associate { row -> row.id to when (row.role) {
            PlanResourceRole.LogicalTarget -> targetFor(bindings[laneI32].scopeI32)
            PlanResourceRole.ReadbackStaging -> staging
            PlanResourceRole.DestinationSnapshot -> planResourceId(row.role, occurrences.size + laneI32)
            PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil ->
                planResourceId(row.role, laneI32 + 1)
            else -> row.id // Replaced below by distinct per-role graph ordinals.
        } }.toMutableMap() }
        val nextOrdinal = mutableMapOf<PlanResourceRole, Int>()
        lanes.forEachIndexed { laneI32, lane -> lane.resources().filter { it.role !in setOf(PlanResourceRole.LogicalTarget,
            PlanResourceRole.ReadbackStaging, PlanResourceRole.DestinationSnapshot, PlanResourceRole.VertexData,
            PlanResourceRole.IndexData, PlanResourceRole.UniformData, PlanResourceRole.DepthStencil) }.forEach { row ->
            val ordinal = nextOrdinal[row.role] ?: 0
            laneResourceIds[laneI32][row.id] = planResourceId(row.role, ordinal)
            nextOrdinal[row.role] = Math.addExact(ordinal, 1)
        } }
        val nativeByLane = linkedMapOf<Int, LinkedHashMap<PlanPassId, PlanPass>>()
        lanes.forEachIndexed { laneI32, lane ->
            val data = lane.resources().filter { it.role in setOf(PlanResourceRole.VertexData,
                PlanResourceRole.IndexData, PlanResourceRole.UniformData) }
            if (data.isNotEmpty()) {
                require(data.map { it.role }.toSet().size == 3 && data.size == 3)
                val binding = PlanDrawDataResources(planResourceId(PlanResourceRole.VertexData, laneI32 + 1),
                    planResourceId(PlanResourceRole.IndexData, laneI32 + 1),
                    planResourceId(PlanResourceRole.UniformData, laneI32 + 1))
                RenderGraph.visualDraws(lane.passes()).forEach { dataByCommand[it.commandIndex] = binding }
            }
        }
        fun appendRender(target: PlanResourceId, draws: List<PlanDraw>, clear: Boolean): PlanPass.RenderPass {
            val before = versions[target] ?: 0L
            val after = Math.addExact(before, draws.size.toLong())
            versions[target] = after
            return PlanPass.RenderPass(passes.size, target, draws,
                if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load,
                AttachmentStorePlan.Store, drawDataResources = draws.firstOrNull()?.let { dataByCommand[it.commandIndex] },
                destinationVersionAfter = DestinationVersionI64(after)).also(passes::add)
        }

        val bindingsByCommand = bindings.associateBy { it.firstCommandIndexI32 }
        val filterOccurrences = filterScene?.takeIf(W6bFilterGraphConstruction::owns)
            ?.let(W6bFilterGraphConstruction::positiveOccurrences) ?: emptyList()
        val filterOccurrencesByInsertion = filterOccurrences.groupBy { it.insertionCommandIndexI32 }
        val filterLayersByBegin = filterOccurrences.filter { it.isLayerOccurrence }.associateBy { it.insertionCommandIndexI32 }
        val directFilterSourceByCommand = linkedMapOf<Int, W6bFilterGraphConstruction.SourceBinding>()
        filterOccurrences.filterNot { it.isLayerOccurrence || it.isPictureOccurrence }.forEach { occurrence ->
            val binding = requireNotNull(bindingsByCommand[occurrence.insertionCommandIndexI32]) {
                "W6b direct occurrence has no W5 source generation."
            }
            val draw = RenderGraph.visualDraws(binding.source.passes()).single()
            val deviceBounds = requireNotNull(intersect(w6aRasterBoundsI32(draw), w6aScissorI32(draw)))
            val clipped = requireNotNull(intersect(deviceBounds, targetDeviceBounds(targetFor(binding.scopeI32))))
            directFilterSourceByCommand[occurrence.insertionCommandIndexI32] =
                allocateOccurrenceSource(clipped, targetFor(binding.scopeI32))
        }
        val filterCursor = W6bFilterGraphConstruction.FreezeCursor(0, 0, passes.size)
        fun appendFrozenOccurrence(
            occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
            source: W6bFilterGraphConstruction.SourceBinding,
            destination: PlanResourceId,
            operation: FilterCompositeOperationV1,
            replacedLayerSource: PlanResourceId? = null,
        ): PlanPass.FilterComposite {
            filterCursor.passOrdinalI32 = passes.size
            val frozen = W6bFilterGraphConstruction.freezeOccurrence(occurrence, source, filterCursor)
            filterResourceSpecs += frozen.resourceSpecs()
            passes += frozen.passes()
            val outputBounds = frozen.output.copyDeviceBoundsI32()
            val compositeDeviceBounds = requireNotNull(intersect(outputBounds, targetDeviceBounds(destination)))
            val sourceBounds = requireNotNull(frozen.output.mapping.mapDeviceRectToTargetI32OrNull(
                compositeDeviceBounds, frozen.output.originDeviceI32,
            ))
            val destinationOrigin = targetOriginDevice(destination)
            val destinationLocal = Point2I32(
                Math.toIntExact(Math.subtractExact(compositeDeviceBounds.left.toLong(), destinationOrigin.x.toLong())),
                Math.toIntExact(Math.subtractExact(compositeDeviceBounds.top.toLong(), destinationOrigin.y.toLong())),
            )
            val before = DestinationVersionI64(versions[destination] ?: 0L)
            val after = when (operation) {
                is FilterCompositeOperationV1.Draw -> {
                    require(operation.blend !is BlendPlan.DestinationReadV1) { "W6b filtered destination-read blend is not planned." }
                    DestinationVersionI64(if (operation.blend.compositionFacts.writesParentDevice)
                        Math.addExact(before.valueI64, 1L) else before.valueI64)
                }
                is FilterCompositeOperationV1.Layer -> operation.restore.parentVersionAfter
                is FilterCompositeOperationV1.Picture -> DestinationVersionI64(Math.addExact(before.valueI64, 1L))
            }
            require(operation !is FilterCompositeOperationV1.Layer || operation.restore.parentVersionBefore == before)
            versions[destination] = after.valueI64
            return PlanPass.FilterComposite(passes.size, frozen.output.resourceId, destination, frozen.terminalKey,
                sourceBounds, destinationLocal, operation, replacedLayerSource, after).also(passes::add)
        }
        fun copyLayerSource(
            layerTarget: PlanResourceId,
            source: W6bFilterGraphConstruction.SourceBinding,
        ) {
            val sourceExtent = targetExtent(layerTarget)
            val capturedVersion = DestinationVersionI64(requireNotNull(versions[layerTarget]))
            passes += PlanPass.TextureCopy(
                passes.size,
                layerTarget,
                source.resourceId,
                capturedVersion,
                RectI32(0, 0, sourceExtent.width, sourceExtent.height),
                Point2I32.Origin,
                Math.multiplyExact(sourceExtent.width.toLong(), 4L),
            )
            versions[source.resourceId] = 0L
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
        fun pictureParentTarget(commandIndexI32: Int): PlanResourceId {
            val scope = occurrences.filter { it.beginCommandIndexI32 < commandIndexI32 && commandIndexI32 < it.endCommandIndexI32 }
                .maxByOrNull { it.beginCommandIndexI32 }?.idI32
            return targetFor(scope)
        }
        for (commandIndexI32 in 0..lastCommandIndexI32) {
            begins[commandIndexI32]?.let { occurrence ->
                val geometry = activeByScope[occurrence.idI32] ?: return@let
                val scopeId = LayerScopeIdI32(occurrence.idI32)
                require(geometry.mapping != null)
                val layerTarget = targetFor(occurrence.idI32)
                if (!occurrence.descriptor.initWithPrevious) {
                    val initialize = appendRender(layerTarget, emptyList(), true)
                    initializationByScope[occurrence.idI32] = LayerInitializationPlanV1.TransparentBlack
                    steps += LayerExecutionStepV1.Initialize(scopeId, initialize.id)
                } else {
                    val parentTarget = targetFor(occurrence.parentIdI32)
                    val parentOrigin = targetOriginDevice(parentTarget)
                    val copyDomain = requireNotNull(geometry.compositeDomainDeviceI32)
                    val sourceBounds = RectI32(
                        Math.toIntExact(Math.subtractExact(copyDomain.left.toLong(), parentOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.top.toLong(), parentOrigin.y.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.right.toLong(), parentOrigin.x.toLong())),
                        Math.toIntExact(Math.subtractExact(copyDomain.bottom.toLong(), parentOrigin.y.toLong())),
                    )
                    val capturedParentVersion = DestinationVersionI64(versions[parentTarget] ?: 0L)
                    val copy = PlanPass.TextureCopy(
                        passes.size,
                        parentTarget,
                        layerTarget,
                        capturedParentVersion,
                        sourceBounds,
                        Point2I32.Origin,
                        Math.multiplyExact(sourceBounds.width().toLong(), 4L),
                    )
                    passes += copy
                    initializationByScope[occurrence.idI32] = LayerInitializationPlanV1.PreviousCopy(
                        parentTarget,
                        layerTarget,
                        capturedParentVersion,
                        sourceBounds,
                        Point2I32.Origin,
                    )
                    steps += LayerExecutionStepV1.Initialize(scopeId, copy.id)
                }
            }
            bindingsByCommand[commandIndexI32]?.let { binding ->
                if (binding.scopeI32 == null || binding.scopeI32 in activeByScope) {
                    val draws = RenderGraph.visualDraws(binding.source.passes())
                    if (draws.isNotEmpty()) {
                        val parentTarget = targetFor(binding.scopeI32)
                        val directFilterSource = directFilterSourceByCommand[commandIndexI32]
                        val target = directFilterSource?.resourceId ?: parentTarget
                        val directFilter = directFilterSource != null
                        if (target != parentTarget) appendRender(target, emptyList(), true)
                        val selectedDraw = draws.single()
                        val laneI32 = bindings.indexOf(binding)
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
                        fun localNative(pass: PlanPass, ordinal: Int): PlanPass = pass.rebindW4eV6(ordinal,
                            laneResourceIds[laneI32]::getValue, geometry?.mapping, geometry?.compositeDomainDeviceI32)
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
                            val depth = planResourceId(PlanResourceRole.DepthStencil, bindings.indexOf(binding) + 1)
                            val group = canonicalPathAtomicGroup(draw)
                            val producer = PlanPass.StencilGeometryProducerV3(passes.size, target, depth, draw.commandIndex,
                                draw.copyPathGeometry(), draw.copyScissorI32(), data, group,
                                AttachmentLoadPlan.Load, AttachmentStorePlan.Store)
                            passes += producer
                            val after = DestinationVersionI64(Math.addExact(versions.getValue(target), 1L))
                            versions[target] = after.valueI64
                            val cover = PlanPass.StencilCover(passes.size, target, depth, draw, data, group,
                                AttachmentLoadPlan.Load, AttachmentStorePlan.Store, PlanDepthStencilAccess.ReadWrite,
                                PlanDepthStencilLoadStore.LoadStoreTestReset, after)
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
                            val pass = appendRender(target, listOf(draw), false)
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
                            val occurrence = filterOccurrencesByInsertion.getValue(commandIndexI32).single {
                                !it.isLayerOccurrence && !it.isPictureOccurrence
                            }
                            val composite = appendFrozenOccurrence(occurrence, source, parentTarget,
                                FilterCompositeOperationV1.Draw(selectedDraw.blend))
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
                    val pictureOccurrences = filterOccurrencesByInsertion[commandIndexI32].orEmpty()
                        .filter { it.isPictureOccurrence }
                    if (pictureOccurrences.isEmpty()) {
                        val picture = PlanPass.PictureSourcePass(passes.size, parentTarget,
                            filterScene!!.canonicalId.value, commandIndexI32)
                        passes += picture
                        versions[parentTarget] = Math.addExact(versions[parentTarget] ?: 0L, 1L)
                    } else pictureOccurrences.forEach { occurrence ->
                        val source = allocateOccurrenceSource(targetDeviceBounds(parentTarget), parentTarget)
                        passes += PlanPass.PictureSourcePass(passes.size, source.resourceId,
                            occurrence.sourceSceneCanonicalId, occurrence.sourceCommandIndexI32)
                        versions[source.resourceId] = 0L
                        appendFrozenOccurrence(occurrence, source, parentTarget,
                            FilterCompositeOperationV1.Picture(occurrence.sourceSceneCanonicalId, occurrence.sourceCommandIndexI32))
                    }
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
                val parentOrigin = targetOriginDevice(parentTarget)
                val destinationOrigin = Point2I32(
                    Math.toIntExact(Math.subtractExact(childDomain.left.toLong(), parentOrigin.x.toLong())),
                    Math.toIntExact(Math.subtractExact(childDomain.top.toLong(), parentOrigin.y.toLong())),
                )
                val composite = filterLayersByBegin[occurrence.beginCommandIndexI32]?.let { filtered ->
                    val source = allocateOccurrenceSource(targetDeviceBounds(target), target, copyDestination = true)
                    copyLayerSource(target, source)
                    appendFrozenOccurrence(filtered, source, parentTarget,
                        FilterCompositeOperationV1.Layer(restore), target)
                } ?: PlanPass.LayerComposite(passes.size, scopeId, target, parentTarget,
                    RectI32(0, 0, childDomain.width(), childDomain.height()), destinationOrigin, restore,
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
        // IDs and semantic scope enumeration are assigned at BeginLayer in source order. Restore
        // construction is post-order, but must not leak that implementation detail into the
        // immutable plan metadata.
        frame = LayerFramePlanV1(occurrences.mapNotNull { scopePlans[it.idI32] }, steps)

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
            add(PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null,
                Math.multiplyExact(readbackRowBytesI64, extent.height.toLong()),
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 0, passes.size))
            add(PlanResource.of(PlanResourceRole.UniformData, 0, PlanResourceKind.Buffer, null, null, uniformCursorI64,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, passes.size))
            lanes.forEachIndexed { laneI32, lane ->
                if (bindings[laneI32].scopeI32 != null && bindings[laneI32].scopeI32 !in activeByScope) return@forEachIndexed
                lane.resources().filter { it.role !in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging) }.forEach { row ->
                    val targetSized = row.kind == PlanResourceKind.Texture2D && row.role != PlanResourceRole.DestinationSnapshot
                    val boundExtent = if (targetSized)
                        targetExtents.getValue(targetFor(bindings[laneI32].scopeI32)) else row.copyExtent()
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
            val target = targetFor(bindings[laneI32].scopeI32)
            val targetExtent = targetExtents.getValue(target)
            val payload = requireNotNull(W4eNativePayloadPlan.fromDeferred(native.values.toList(), resources, targetExtent,
                caps, lanes[laneI32].sourceTable())) { "w6a.layer.w4e_payload" }
            w4eBindings += PlanW4eGeometryBindingV1(target, targetExtent, native, payload)
        }
        nonUniformBytesI64 = W6aLayerPlanBudget.peak(resources, passes.size, budget)
    }

    fun publish(
        table: MaterialPlanTable?,
        bound: List<List<PlanPass>>,
        source: SourcePhysicalConstructionV1 = SourcePhysicalConstructionV1(),
    ): RenderGraph {
        require(bound.size == lanes.size || bound.isEmpty() && table == null)
        val byCommand = bound.flatMap { RenderGraph.visualDraws(it) }.associateBy { it.commandIndex }
        val geometryByTarget = geometries.filterNot(W6aScopeGeometry::isElided).associateBy {
            planResourceId(PlanResourceRole.LayerTarget, it.occurrence.idI32)
        }
        val localized = linkedMapOf<Int, PlanDraw>()
        val finalBlends = RenderGraph.visualDraws(rawPasses).associate { it.commandIndex to it.blend }
        fun boundDraw(command: Int, target: PlanResourceId): PlanDraw = localized.getOrPut(command) {
            val bound = byCommand.getValue(command)
            val draw = if (finalBlends.getValue(command) is BlendPlan.DestinationReadV1)
                bound.withFinalBlendV1(finalBlends.getValue(command)) else bound
            if (draw is W5bW4ePathDraw) {
                val native = w4eBindings.flatMap { it.nativePasses() }.filterIsInstance<PlanPass.PathRenderPass>()
                    .single { it.draw.commandIndex == command && it.phase != PathRenderPhase.SingleSampleStencilProducer }
                W5bW4ePathDraw(native.rebindW4eV6(native.ordinal, { it }, null, null, draw.materialAuthority) as PlanPass.PathRenderPass, draw.blend)
            } else if (target == root) draw else filterSourceBindings[target]?.let { sourceBinding ->
                localizeLayerDraw(draw, sourceBinding.mapping, sourceBinding.copyDeviceBoundsI32())
            } ?: geometryByTarget.getValue(target).let { geometry ->
                localizeLayerDraw(draw, requireNotNull(geometry.mapping), requireNotNull(geometry.compositeDomainDeviceI32))
            }
        }
        val passes = rawPasses.map { pass ->
            if (pass is PlanPass.RenderPass) {
                val local = pass.draws().map { boundDraw(it.commandIndex, pass.target) }
                PlanPass.RenderPass(pass.ordinal, pass.target, local, pass.load, pass.store,
                    drawDataResources = pass.drawDataResources, destinationVersionAfter = pass.destinationVersionAfter)
            } else when (pass) {
                is PlanPass.StencilGeometryProducerV3 -> {
                    val draw = boundDraw(pass.commandIndexI32, pass.target) as PathDraw
                    PlanPass.StencilGeometryProducerV3(pass.ordinal, pass.target, pass.depthStencil, pass.commandIndexI32,
                        draw.copyPathGeometry(), draw.copyScissorI32(), pass.drawDataResources, pass.atomicGroup, pass.load, pass.store)
                }
                is PlanPass.StencilCover -> PlanPass.StencilCover(pass.ordinal, pass.target, pass.depthStencil,
                    boundDraw(pass.draw.commandIndex, pass.target) as PathDraw, pass.drawDataResources, pass.atomicGroup,
                    pass.load, pass.store, pass.depthStencilAccess, pass.depthStencilLoadStore, pass.destinationVersionAfter)
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
        val peak = W6aLayerPlanBudget.peak(allResources, passes.size, budget)
        val construction = RenderGraph.construct(id, W6aLayerPlanCompiler.CAPABILITY_ID, extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, caps, budget, byCommand.size, allResources, passes,
            passes.zipWithNext { first, second -> PlanPassDependency(first.id, second.id) }, peak, table)
        val sourceNonUniform = Math.subtractExact(construction.peakFrameLocalBytes,
            source.resources.filter { it.role == PlanResourceRole.SourceUniformData }.fold(0L) { bytes, row -> Math.addExact(bytes, row.byteSize) })
        return RenderGraph.publishW6a(construction, frame,
            packConstructedFrame(listOf(construction), table, sourceNonUniform), SourcePhysicalConstructionV1(
                source.resources, source.uniforms, source.caches, w4eBindings.map { it.bindSources(localized) }))
    }

    private fun sealGeometry(
        occurrence: W6aLayerPlanCompiler.ScopeOccurrence,
        restoreFacts: W6aRestoreFacts,
        desired: RectI32?,
        known: RectI32?,
        produced: RectI32?,
        physicalInput: RectI32?,
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
        val required = desired.copy()
        val effective = if (restoreFacts.previousContentRequiresFullParentDomain || restoreFacts.restoreAffectsTransparentBlack) desired
            else if (physicalInput == null) desired else hintDomain?.let { union(physicalInput, it) } ?: physicalInput
        val composite = intersect(effective, desired)
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
        return W6aRestoreFacts(paint?.color?.alpha?.div(255f) ?: 1f, colorFilter, blend,
            occurrence.descriptor.initWithPrevious)
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
