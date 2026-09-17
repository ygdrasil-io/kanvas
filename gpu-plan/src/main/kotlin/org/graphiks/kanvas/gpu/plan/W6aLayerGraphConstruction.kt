package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.mapRectBoundsF64OrNull

/** One occurrence binding, still before material issuance and graph publication. */
internal class W6aLayerSourceBinding(val scopeI32: Int?, val source: SourceDeferredRenderConstructionV4)

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
) {
    val readsPriorDevice: Boolean = blend.compositionFacts.readsPriorDevice
    val writesParentDevice: Boolean = blend.compositionFacts.writesParentDevice
    val restoreAffectsTransparentBlack: Boolean = blend.finalRestoreAffectsTransparentBlackV1(colorFilter)
}

/** Deliberately distinguished from malformed W6 topology so callers can recover before native work. */
internal class W6aRestoreAdmissionFailure(message: String) : IllegalArgumentException(message)

internal class W6aLayerGraphConstruction(
    private val id: PlanId,
    val extent: SizeI32,
    val caps: PlanCapabilitySnapshot,
    val budget: PlanBudget,
    occurrences: List<W6aLayerPlanCompiler.ScopeOccurrence>,
    bindings: List<W6aLayerSourceBinding>,
) {
    private val occurrences = immutableList(occurrences)
    private val bindings = immutableList(bindings)
    val lanes = immutableList(bindings.map { it.source })
    private val root = planResourceId(PlanResourceRole.LogicalTarget, 0)
    private val staging = planResourceId(PlanResourceRole.ReadbackStaging, 0)
    private val rootDomainDeviceI32 = RectI32(0, 0, extent.width, extent.height)
    private val rowBytesI64 = Math.multiplyExact(extent.width.toLong(), 4L).let {
        Math.addExact(it, (caps.copyBytesPerRowAlignment - it % caps.copyBytesPerRowAlignment) % caps.copyBytesPerRowAlignment)
    }
    private val geometries: List<W6aScopeGeometry>
    private val rawPasses: List<PlanPass>
    private val frame: LayerFramePlanV1
    private val resources: List<PlanResource>
    val nonUniformBytesI64: Long
    val passCountI32: Int get() = rawPasses.size

    init {
        require(lanes.all { it.capabilities == caps && it.budget == budget })
        require(bindings.all { it.scopeI32 == null || occurrences.any { scope -> scope.idI32 == it.scopeI32 } })
        require(lanes.all { source -> source.passes().all { it is PlanPass.RenderPass || it is PlanPass.ReadbackPass } &&
            RenderGraph.visualDraws(source.passes()).all { it is SolidRectDraw && it.blend !is BlendPlan.DestinationReadV1 } }) {
            "w6a.layer.unsupported_child"
        }

        val restoreFactsByScope = occurrences.associate { occurrence ->
            occurrence.idI32 to sealRestoreFacts(occurrence)
        }
        geometries = immutableList(occurrences.map { occurrence ->
            sealGeometry(occurrence, restoreFactsByScope.getValue(occurrence.idI32))
        })
        val geometryByScope = geometries.associateBy { it.occurrence.idI32 }
        val active = geometries.filterNot(W6aScopeGeometry::isElided)
        // A semantic restore fact is sealed for every scope, but only a non-elided scope binds
        // filter or destination resources.  This keeps required semantic refusals before
        // geometry while avoiding capability admission for work that cannot materialize.
        active.forEach { geometry ->
            admitRestoreBindings(restoreFactsByScope.getValue(geometry.occurrence.idI32))
        }
        val passes = mutableListOf<PlanPass>()
        val steps = mutableListOf<LayerExecutionStepV1>()
        val scopes = mutableListOf<LayerScopePlanV1>()
        var versionI64 = 0L
        var uniformCursorI64 = 16L

        fun render(target: PlanResourceId, draws: List<PlanDraw>, clear: Boolean): PlanPass.RenderPass {
            val version = if (target == root) DestinationVersionI64(versionI64.also { versionI64 += draws.size }) else null
            return PlanPass.RenderPass(
                passes.count { it is PlanPass.RenderPass },
                target,
                draws,
                if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load,
                AttachmentStorePlan.Store,
                destinationVersionAfter = if (target == root) DestinationVersionI64(versionI64) else version,
            ).also { passes += it }
        }

        render(root, emptyList(), true)

        fun segment(binding: W6aLayerSourceBinding) {
            val target = binding.scopeI32?.let { planResourceId(PlanResourceRole.LayerTarget, it) } ?: root
            val draws = RenderGraph.visualDraws(binding.source.passes())
            if (draws.isNotEmpty()) {
                val pass = render(target, draws, false)
                binding.scopeI32?.let { steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(it), pass.id) }
            }
        }

        var previousEndI32 = -1
        occurrences.forEach { occurrence ->
            bindings.filter { binding ->
                binding.scopeI32 == null && RenderGraph.visualDraws(binding.source.passes()).firstOrNull()?.commandIndex?.let {
                    command -> command > previousEndI32 && command < occurrence.beginCommandIndexI32
                } == true
            }.forEach(::segment)

            val geometry = geometryByScope.getValue(occurrence.idI32)
            if (!geometry.isElided) {
                val scopeId = LayerScopeIdI32(occurrence.idI32)
                val target = planResourceId(PlanResourceRole.LayerTarget, occurrence.idI32)
                steps += LayerExecutionStepV1.Initialize(scopeId, render(target, emptyList(), true).id)
                bindings.filter { it.scopeI32 == occurrence.idI32 }.forEach(::segment)
                val before = DestinationVersionI64(versionI64)
                val facts = restoreFactsByScope.getValue(occurrence.idI32)
                val filterOffset = facts.colorFilter?.let { execution ->
                    uniformCursorI64 = alignUniform(uniformCursorI64, caps.minUniformBufferOffsetAlignment)
                    uniformCursorI64.also { uniformCursorI64 = Math.addExact(it, maxOf(16L, execution.dynamicByteCountI64)) }
                }
                val blend = if (facts.readsPriorDevice) facts.blend.bindDestinationReadV1(before,
                    planResourceId(PlanResourceRole.DestinationSnapshot, occurrence.idI32)) else facts.blend
                val after = DestinationVersionI64(if (facts.writesParentDevice) Math.addExact(versionI64, 1L) else versionI64)
                val restore = LayerRestorePlanV1(facts.alphaF32, facts.colorFilter, blend, facts.readsPriorDevice,
                    facts.writesParentDevice, facts.restoreAffectsTransparentBlack, before, after, filterOffset)
                val targetDomain = requireNotNull(geometry.compositeDomainDeviceI32)
                val sourceBounds = RectI32(0, 0, targetDomain.width(), targetDomain.height())
                if (facts.readsPriorDevice) {
                    passes += PlanPass.TextureCopy(occurrence.idI32, root, requireNotNull(blend.destinationReadSnapshotResourceV1()), before,
                        rootDomainDeviceI32, Point2I32.Origin, rowBytesI64)
                }
                versionI64 = after.valueI64
                val composite = PlanPass.LayerComposite(occurrence.idI32, scopeId, target, root, sourceBounds,
                    Point2I32(targetDomain.left, targetDomain.top), restore, AttachmentLoadPlan.Load,
                    AttachmentStorePlan.Store, restore.parentVersionAfter)
                passes += composite
                steps += LayerExecutionStepV1.Restore(scopeId, composite.id)
                scopes += LayerScopePlanV1(scopeId, null, occurrence.beginCommandIndexI32, occurrence.endCommandIndexI32,
                    emptyList(), requireNotNull(geometry.mapping), LayerBoundsPlanV1(
                        geometry.requestedHintDeviceF64,
                        geometry.knownContentDeviceI32,
                        requireNotNull(geometry.desiredOutputDeviceI32),
                        requireNotNull(geometry.requiredInputDeviceI32),
                        geometry.producedOutputDeviceI32,
                        targetDomain,
                    ), LayerInitializationPlanV1.TransparentBlack, restore, target)
            }
            previousEndI32 = occurrence.endCommandIndexI32
        }
        bindings.filter { binding ->
            binding.scopeI32 == null && RenderGraph.visualDraws(binding.source.passes()).firstOrNull()?.commandIndex?.let {
                command -> command > previousEndI32
            } == true
        }.forEach(::segment)
        passes += PlanPass.ReadbackPass(0, root, staging, rowBytesI64,
            Math.addExact(Math.multiplyExact(rowBytesI64, (extent.height - 1).toLong()), Math.multiplyExact(extent.width.toLong(), 4L)))
        rawPasses = immutableList(passes)
        frame = LayerFramePlanV1(scopes, steps)

        // Every handle is acquired by one frame draft and retained until completion. An active
        // layer target therefore owns a distinct physical allocation even when another scope's
        // render pass has finished.
        resources = immutableList(buildList {
            add(PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), extent,
                checkedTextureBytesI64(4, extent.width, extent.height, 1),
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, passes.size))
            active.forEach { geometry ->
                val targetExtent = geometry.targetExtentI32()
                add(PlanResource.of(PlanResourceRole.LayerTarget, geometry.occurrence.idI32, PlanResourceKind.Texture2D,
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), targetExtent,
                    checkedTextureBytesI64(4, targetExtent.width, targetExtent.height, 1),
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), PlanResourceLifetime.FrameLocal, 0, passes.size))
            }
            scopes.filter { it.restore.readsPriorDevice }.forEach { scope ->
                add(PlanResource.of(PlanResourceRole.DestinationSnapshot, scope.id.valueI32, PlanResourceKind.Texture2D,
                    PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), extent,
                    checkedTextureBytesI64(4, extent.width, extent.height, 1),
                    setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled), PlanResourceLifetime.FrameLocal, 0, passes.size))
            }
            add(PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null,
                Math.multiplyExact(rowBytesI64, extent.height.toLong()), setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
                PlanResourceLifetime.FrameLocal, 0, passes.size))
            add(PlanResource.of(PlanResourceRole.UniformData, 0, PlanResourceKind.Buffer, null, null, uniformCursorI64,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, passes.size))
        })
        nonUniformBytesI64 = W6aLayerPlanBudget.peak(resources, passes.size, budget)
    }

    fun publish(table: MaterialPlanTable?, bound: List<List<PlanPass>>, source: SourcePhysicalConstructionV1 = SourcePhysicalConstructionV1()): RenderGraph {
        require(bound.size == lanes.size || bound.isEmpty() && table == null)
        val byCommand = bound.flatMap { RenderGraph.visualDraws(it) }.associateBy { it.commandIndex }
        val geometryByTarget = geometries.filterNot(W6aScopeGeometry::isElided).associateBy {
            planResourceId(PlanResourceRole.LayerTarget, it.occurrence.idI32)
        }
        val passes = rawPasses.map { pass ->
            if (pass is PlanPass.RenderPass) {
                val mapped = pass.draws().map { byCommand.getValue(it.commandIndex) }
                val local = if (pass.target == root) mapped else {
                    val geometry = geometryByTarget.getValue(pass.target)
                    mapped.map { draw -> localizeLayerDraw(
                        draw,
                        requireNotNull(geometry.mapping),
                        requireNotNull(geometry.compositeDomainDeviceI32),
                    ) }
                }
                PlanPass.RenderPass(pass.ordinal, pass.target, local, pass.load, pass.store,
                    destinationVersionAfter = pass.destinationVersionAfter)
            } else pass
        }
        val allResources = resources + source.resources
        val peak = W6aLayerPlanBudget.peak(allResources, passes.size, budget)
        val construction = RenderGraph.construct(id, W6aLayerPlanCompiler.CAPABILITY_ID, extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, caps, budget, byCommand.size, allResources, passes,
            passes.zipWithNext { first, second -> PlanPassDependency(first.id, second.id) }, peak, table)
        val sourceNonUniform = Math.subtractExact(construction.peakFrameLocalBytes,
            source.resources.filter { it.role == PlanResourceRole.SourceUniformData }.fold(0L) { bytes, row -> Math.addExact(bytes, row.byteSize) })
        return RenderGraph.publishW6a(construction, frame,
            packConstructedFrame(listOf(construction), table, sourceNonUniform), source)
    }

    private fun sealGeometry(
        occurrence: W6aLayerPlanCompiler.ScopeOccurrence,
        restoreFacts: W6aRestoreFacts,
    ): W6aScopeGeometry {
        // A proven-empty restore clip elides before inspecting any transform or hint.  Nothing
        // can allocate, render or sample from this scope.
        val desired = desiredOutput(occurrence.descriptor)
            ?: return W6aScopeGeometry(occurrence, null, null, null, null, null, null, null)
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
        val known = knownContent(occurrence.idI32)
        val produced = known
        // W6a's current transparent, identity restore has no filter expansion. The four values
        // remain distinct facts even when their no-filter derivations happen to coincide.
        val required = desired.copy()
        // Transparent black is real input to an alpha-creating filter or a blend such as CLEAR.
        // Its semantic restore domain is therefore the parent clip even when children are bounded.
        val effective = if (restoreFacts.restoreAffectsTransparentBlack) desired
            else if (known == null) desired else hintDomain?.let { union(known, it) } ?: known
        val composite = intersect(effective, desired)
        if (composite == null) return W6aScopeGeometry(occurrence, null, hint, known, desired, required, produced, null)
        val mapping = LayerMappingF64.ofOrNull(localToDevice, Point2I32(composite.left, composite.top))
            ?: throw IllegalArgumentException(W6aPlanDiagnostics.NonFiniteTransform)
        return W6aScopeGeometry(occurrence, mapping, hint, known, desired, required, produced, composite)
    }

    private fun desiredOutput(descriptor: org.graphiks.kanvas.render.ir.LayerDescriptor): RectI32? = when (val clip = descriptor.compositeClip) {
        null, ClipStackNode.Empty -> rootDomainDeviceI32.copy()
        is ClipStackNode.DeviceRect -> {
            val bounds = clip.copyBounds()
            if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) null
            else {
                val rounded = RectF64(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
                    .roundOutToRectI32OrNull() ?: throw IllegalArgumentException(W6aPlanDiagnostics.MappingOverflow)
                intersect(rounded, rootDomainDeviceI32)
            }
        }
        is ClipStackNode.Operations -> throw IllegalArgumentException("${W6aPlanDiagnostics.UnsupportedChild}: complex composite clip")
    }

    private fun knownContent(scopeI32: Int): RectI32? = bindings.filter { it.scopeI32 == scopeI32 }
        .flatMap { RenderGraph.visualDraws(it.source.passes()) }
        .filterIsInstance<SolidRectDraw>()
        .mapNotNull { draw -> intersect(draw.copyVisibleBounds(), draw.copyScissor()) }
        .fold<RectI32, RectI32?>(null) { union, bounds -> union?.let { union(it, bounds) } ?: bounds.copy() }

    private fun localizeLayerDraw(
        draw: PlanDraw,
        mapping: LayerMappingF64,
        targetDomainDeviceI32: RectI32,
    ): PlanDraw {
        val solid = draw as? SolidRectDraw ?: error("w6a.layer.unsupported_child")
        val visibleDevice = requireNotNull(intersect(solid.copyVisibleBounds(), targetDomainDeviceI32))
        val scissorDevice = requireNotNull(intersect(solid.copyScissor(), targetDomainDeviceI32))
        val visibleLayer = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(visibleDevice))
        val scissorLayer = requireNotNull(mapping.mapDeviceRectToLayerI32OrNull(scissorDevice))
        return when (val authority = solid.materialAuthority) {
            is PlanDrawMaterialAuthority.LegacyColorV1 -> SolidRectDraw.of(
                solid.commandIndex, authority.copyColorF32(), visibleLayer, scissorLayer,
                solid.coverage, solid.sample, solid.blend,
            )
            is PlanDrawMaterialAuthority.MaterialV3 -> error("w6a.layer.unsupported_child")
            else -> SolidRectDraw.ofMaterial(
                solid.commandIndex, authority.materialPlanRef(), visibleLayer, scissorLayer,
                solid.coverage, solid.sample, solid.blend, solid.materialCoordinates, solid.materialCoordinatesV2,
                (authority as? PlanDrawMaterialAuthority.MaterialV4)?.coordinates,
                authority is PlanDrawMaterialAuthority.MaterialV5,
            )
        }
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? = first.copy().takeIf { it.intersect(second) }

    private fun union(first: RectI32, second: RectI32): RectI32 = RectI32(
        minOf(first.left, second.left), minOf(first.top, second.top), maxOf(first.right, second.right), maxOf(first.bottom, second.bottom),
    )

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
        return W6aRestoreFacts(paint?.color?.alpha?.toInt()?.div(255f) ?: 1f, colorFilter, blend)
    }

    /**
     * The exact W5 filter proof owns the uniform footprint; W6 only admits its materialization
     * against the sealed capabilities and the composite's already-fixed binding inventory.
     */
    private fun admitRestoreBindings(facts: W6aRestoreFacts) {
        val bindingCountI32 = 1 + (if (facts.colorFilter == null) 0 else 1) + (if (facts.readsPriorDevice) 1 else 0)
        val sampledTextureCountI32 = 1 + if (facts.readsPriorDevice) 1 else 0
        try {
            facts.colorFilter?.let { filter ->
                requireColorUniformBindingV4(maxOf(16L, filter.dynamicByteCountI64), caps, bindingCountI32)
            }
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
