package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.LayerDescriptor
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.CapturedFilterRootV1
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ColorFilterNode
import org.graphiks.kanvas.render.ir.MaskFilterNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.mapRectBoundsF64OrNull

/**
 * First layer authority. It recognizes every layer boundary before any child capability or
 * target-format decision, then freezes occurrence metadata before asking the existing W5 source
 * authority to lower the transparent/SRC_OVER subset.
 */
public class W6aLayerPlanCompiler public constructor(
    private val runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
) : GpuPlanCompiler {
    public constructor() : this(RuntimeEffectSemanticCatalogSnapshot.Unbound)

    internal data class ScopeOccurrence(
        val idI32: Int,
        val parentIdI32: Int?,
        val beginCommandIndexI32: Int,
        val endCommandIndexI32: Int,
        val descriptor: LayerDescriptor,
        val childIdsI32: List<Int> = emptyList(),
    )

    private class Candidate(
        val owner: W6aLayerPlanCompiler,
        val scene: SceneSnapshot,
        override val sceneCanonicalId: org.graphiks.kanvas.render.ir.CanonicalId,
        override val target: RenderTargetDescriptor,
        val occurrences: List<ScopeOccurrence>,
        val segments: List<Segment>,
    ) : GpuPlanCandidate {
        override val capabilityId: String = CAPABILITY_ID
    }

    /** One pre-publication W3/W5 lane, attached to the scope active at its recorded draw. */
    private class Segment(
        val scopeI32: Int?,
        val firstCommandIndexI32: Int,
        val compiler: CapabilityCompilerChain,
        val candidate: GpuPlanCandidate,
    )

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        val commands = scene.toList()
        val ownsW6b = W6bFilterGraphConstruction.owns(scene)
        if (commands.none { it is SceneCommand.BeginLayer || it is SceneCommand.EndLayer } && !ownsW6b) {
            return GpuPlanSelection.NotCandidate(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild, "Scene has no layer boundary.")))
        }
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalid(W6aPlanDiagnostics.UnsupportedChild, "Scene and target descriptors disagree.")
        }
        // W6b ownership is terminal before child-lane planning or any physical construction.
        // Positive arms remain frozen-but-unmaterialized until Task 3 provides native execution.
        W6bFilterGraphConstruction.admissionRefusalOrNull(scene)?.let { refusal ->
            return GpuPlanSelection.InvalidScene(listOf(refusal))
        }

        // Layer occurrence limits come from the same immutable GraphLimits vocabulary used at
        // capture.  W6 owns the resulting terminal refusal before it can issue a resource or
        // ask the native renderer for a draft.
        val graphLimits = scene.graphLimits
        if (commands.size > graphLimits.maxNodes) return invalid(
            W6aPlanDiagnostics.CommandLimit,
            "Layer frame has ${commands.size} commands; limit is ${graphLimits.maxNodes}.",
        )
        data class MutableOccurrence(
            val idI32: Int,
            val parentIdI32: Int?,
            val beginCommandIndexI32: Int,
            val descriptor: LayerDescriptor,
            val childIdsI32: MutableList<Int> = mutableListOf(),
            var endCommandIndexI32: Int = -1,
        )
        val scopes = mutableListOf<MutableOccurrence>()
        val stack = ArrayDeque<MutableOccurrence>()
        val scopeByDrawIndex = mutableMapOf<Int, Int?>()
        commands.forEachIndexed { indexI32, command ->
            when (command) {
                is SceneCommand.BeginLayer -> {
                    if (stack.size >= graphLimits.maxDepth) return invalid(
                        W6aPlanDiagnostics.DepthLimit,
                        "Layer nesting exceeds depth ${graphLimits.maxDepth}.",
                    )
                    semanticRefusalFor(command.descriptor, ownsW6b)?.let { (code, message) -> return invalid(code, message) }
                    if (!hasEmptyExplicitCompositeClip(command.descriptor)) {
                        geometryRefusalFor(command.descriptor, target)?.let { (code, message) -> return invalid(code, message) }
                    }
                    val occurrence = MutableOccurrence(scopes.size, stack.lastOrNull()?.idI32, indexI32, command.descriptor)
                    stack.lastOrNull()?.childIdsI32?.add(occurrence.idI32)
                    scopes += occurrence
                    stack.addLast(occurrence)
                }
                SceneCommand.EndLayer -> {
                    val begun = stack.removeLastOrNull() ?: return invalid(
                        W6aPlanDiagnostics.MalformedStack,
                        "EndLayer has no matching BeginLayer.",
                    )
                    begun.endCommandIndexI32 = indexI32
                }
                is SceneCommand.Draw -> {
                    scopeByDrawIndex[indexI32] = stack.lastOrNull()?.idI32
                    val paint = command.node.paint
                    if ((!ownsW6b && (paint?.imageFilter != null || paint?.maskFilter != null)) ||
                        (command.node.effects !is EffectStack.Empty && !isW6bFilterStack(command.node.effects))
                    ) {
                        return invalid(
                            W6aPlanDiagnostics.UnsupportedSpatialFilter,
                            "W6a does not admit image or mask filters in a layer frame.",
                        )
                    }
                    if (!finite(command.node.transform)) return invalid(
                        W6aPlanDiagnostics.NonFiniteTransform,
                        "A layer child transform is non-finite.",
                    )
                }
                is SceneCommand.SetTransform -> if (!finite(command.matrix)) return invalid(
                    W6aPlanDiagnostics.NonFiniteTransform,
                    "A layer transform is non-finite.",
                )
                is SceneCommand.SetClip, is SceneCommand.Annotation -> Unit
                else -> return invalid(W6aPlanDiagnostics.UnsupportedChild, "This layer-frame command has no admitted source lane.")
            }
        }
        if (stack.isNotEmpty()) return invalid(W6aPlanDiagnostics.MalformedStack, "BeginLayer has no matching EndLayer.")
        if (scopes.isEmpty() && !ownsW6b) return invalid(W6aPlanDiagnostics.MalformedStack, "Layer markers did not form a scope.")

        val segments = mutableListOf<Segment>()
        val immutableScopes = scopes.map { occurrence -> ScopeOccurrence(
            occurrence.idI32,
            occurrence.parentIdI32,
            occurrence.beginCommandIndexI32,
            occurrence.endCommandIndexI32,
            occurrence.descriptor,
            occurrence.childIdsI32.toList(),
        ) }
        fun isElidedByExplicitAncestor(scopeI32: Int?): Boolean {
            var current = scopeI32
            while (current != null) {
                val occurrence = immutableScopes[current]
                if (hasEmptyExplicitCompositeClip(occurrence)) return true
                current = occurrence.parentIdI32
            }
            return false
        }
        // Each recorded draw is its own unpublished lane.  This deliberately keeps Begin/Draw/
        // End chronology as a first-class input instead of reconstructing parent segments from
        // a flat collection after source planning.
        scopeByDrawIndex.forEach { (drawIndexI32, scopeI32) ->
            // Restore clips apply only at the typed composite.  A proven-empty one has no child
            // render work, while semantic refusal has already run at BeginLayer.
            if (isElidedByExplicitAncestor(scopeI32)) return@forEach
            // Picture is a first-class W6a typed source lane, emitted in the graph with its
            // captured scene/outer draw.  It is deliberately not erased merely because another
            // occurrence in the frame owns W6b.
            if ((commands[drawIndexI32] as SceneCommand.Draw).node.geometry is GeometryNode.Picture) {
                return@forEach
            }
            val draws = setOf(drawIndexI32)
            val segment = SceneSnapshot.of(scene.extent, scene.colorSpace, commands.mapIndexed { index, command ->
                if (index in draws) {
                    stripW6bPayload(command as SceneCommand.Draw)
                } else SceneCommand.Annotation.of(org.graphiks.math.geometry.RectF32(0f, 0f, 0f, 0f), "w6a.segment", index.toString())
            }, graphLimits)
            val child = CapabilityCompilerChain.of(listOf(W5bVerticesPlanCompiler(runtimeCatalog), W5bPointPlanCompiler(runtimeCatalog), W5eImagePlanCompiler(), W3SolidRectPlanCompiler(),
                W4aAnalyticRectPlanCompiler(), W4bAnalyticRRectPlanCompiler(),
                W4cPathFillPlanCompiler(), W4dPathStrokePlanCompiler()), runtimeCatalog)
            when (val selection = child.select(segment, target)) {
                is GpuPlanSelection.Candidate -> segments += Segment(scopeI32, drawIndexI32, child, selection.candidate)
                // A source lane that is admissible except for its W5 material must retain that
                // material authority.  The outer router will terminalize it because W6 owns the
                // layer boundary; collapsing it to UnsupportedChild would both lose the public
                // diagnostic and make recovery observably unstable.
                is GpuPlanSelection.MaterialOnlyRefusal -> return selection
                is GpuPlanSelection.InvalidScene -> return selection
                is GpuPlanSelection.ResourceLimitExceeded -> return selection
                is GpuPlanSelection.NotCandidate -> return invalid(W6aPlanDiagnostics.UnsupportedChild,
                    "Layer segment is outside the admitted child geometry/source lanes.")
            }
        }
        return GpuPlanSelection.Candidate(Candidate(this, scene, scene.canonicalId, target, immutableScopes, segments.toList()))
    }

    override fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph> {
        val selected = candidate as? Candidate
        if (selected == null || selected.owner !== this) {
            return RenderPlanResult.InvalidScene(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild, "Foreign W6a candidate.")))
        }
        return try {
            val bindings = mutableListOf<W6aLayerSourceBinding>()
            for (segment in selected.segments) when (val result = segment.compiler.constructSourceLanes(segment.candidate, capabilities, budget)) {
                is RenderPlanResult.Ready -> result.plan.forEach { bindings += W6aLayerSourceBinding(
                    segment.scopeI32, segment.firstCommandIndexI32, it,
                ) }
                // The child has already named its owner/capture limit. W6 only owns the
                // aggregate peak after every child source is sealed.
                is RenderPlanResult.ResourceLimitExceeded -> return result
                is RenderPlanResult.GapNotMigrated -> return result
                is RenderPlanResult.GapOnPromotedScope -> return result
                is RenderPlanResult.InvalidScene -> return result
            }
            val frame = W6aLayerGraphConstruction(PlanId("w6a.${selected.sceneCanonicalId.value}"), org.graphiks.math.geometry.SizeI32(selected.target.extent.width, selected.target.extent.height),
                capabilities, budget, selected.occurrences, bindings, selected.scene, runtimeCatalog)
            when (val layout = FrameSourceLayoutV4.layeredFrame(frame)) {
                // Task 2 has now published (and therefore validated) the one graph authority.
                // Native admission reads only those frozen operation kinds, schedule, terminals
                // and terminal operands; it never revisits SceneSnapshot or captured filters.
                is SourceConstructionResultV4.Built -> when (val published = layout.value.prepareAndPublish()) {
                    is RenderPlanResult.Ready -> frozenW6bNativeAdmission(published.plan) ?: published
                    is RenderPlanResult.ResourceLimitExceeded -> published
                    is RenderPlanResult.GapNotMigrated -> published
                    is RenderPlanResult.GapOnPromotedScope -> published
                    is RenderPlanResult.InvalidScene -> published
                }
                is SourceConstructionResultV4.Refused -> layout.failure
            }
        } catch (failure: W6aResourceLimitFailure) {
            val message = failure.message ?: "Layer frame budget exceeded."
            if (W6bFilterGraphConstruction.owns(selected.scene)) {
                RenderPlanResult.ResourceLimitExceeded(listOf(W6bFilterDiagnostics.budgetRefusal(message)))
            } else {
                W6aLayerPlanBudget.refusal(message)
            }
        } catch (failure: W6aRestoreAdmissionFailure) {
            RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(W6aPlanDiagnostics.RestoreCapability,
                failure.message ?: "Restore bindings are unavailable on this device.")))
        } catch (failure: W6bFilterGraphConstruction.ConstructionFailure) {
            RenderPlanResult.InvalidScene(listOf(failure.diagnostic))
        } catch (failure: RawMaterialRequirementsV2.Refusal) {
            sourceConstructionRefusalV4(failure.code).failure
        } catch (failure: IllegalArgumentException) {
            RenderPlanResult.GapOnPromotedScope(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild,
                failure.message ?: "Invalid layer construction.")))
        } catch (_: ArithmeticException) {
            RenderPlanResult.ResourceLimitExceeded(listOf(diagnostic(W6aPlanDiagnostics.FrameConstructionOverflow,
                "Layer frame construction overflows I64.")))
        }
    }

    /** The W6b native arm admits only the frozen Task 3–6 image, mask, and shadow operations. */
    private fun frozenW6bNativeAdmission(graph: RenderGraph): RenderPlanResult<RenderGraph>? {
        val filters = graph.passes().filterIsInstance<PlanPass.FilterPass>()
        if (filters.isEmpty()) return null
        val schedule = graph.layerFramePlanOrNull()?.frozenPassSchedule()
        val nativeBlurKinds = setOf(
            FilterImplementationKindV1.IMAGE_BLUR_X,
            FilterImplementationKindV1.IMAGE_BLUR_Y,
            FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
            FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y,
        )
        val materialized = filters.all { pass -> when (val operation = pass.operation) {
            is FilterPassOperationV1.Crop -> true
            is FilterPassOperationV1.SeparableBlur -> operation.kind in nativeBlurKinds
            is FilterPassOperationV1.MaskBlurStyle,
            is FilterPassOperationV1.MaskShader,
            is FilterPassOperationV1.MaskTable,
            is FilterPassOperationV1.MaterializedSource,
            is FilterPassOperationV1.DropShadowColorize,
            is FilterPassOperationV1.DropShadowComposite,
            -> true
        } }
        val terminals = graph.passes().filterIsInstance<PlanPass.FilterComposite>()
        // W6b consumes a typed frozen W4 producer for direct mask coverage.  Do not admit a
        // lane merely because its filter operations are supported: an arbitrary vertices/W4e
        // producer has no executable raw-coverage packet in this frozen contract yet.
        val executableCoverage = graph.passes().filterIsInstance<PlanPass.FilterCoverageSourcePass>()
            .mapNotNull { it.rasterBinding?.draw }
            .all { draw -> draw is SolidRectDraw || draw is AnalyticRectDraw ||
                draw is AnalyticRRectDraw || draw is PathDraw || draw is W5bPointDraw }
        val emptyNoOp = terminals.isEmpty() && filters.all { pass ->
            (pass.operation as? FilterPassOperationV1.SeparableBlur)
                ?.bounds?.copyProducedOutputDeviceI32() == null
        }
        val clips = buildList {
            graph.passes().filterIsInstance<PlanPass.PictureComposite>().forEach { add(requireNotNull(it.operands)) }
            terminals.mapNotNull { (it.operation as? FilterCompositeOperationV1.Picture)?.terminal }.forEach(::add)
        }
        val admitted = materialized && executableCoverage &&
            schedule != null && schedule == graph.passes().map(PlanPass::id) && (terminals.isNotEmpty() || emptyNoOp) &&
            clips.all(::supportsFrozenDeferredPictureClip)
        return if (admitted) null else RenderPlanResult.InvalidScene(listOf(W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.NativeExecutionUnimplemented,
            "W6b filter graph is frozen, but this native operation is not implemented.",
        )))
    }

    /** The graph has already converted the admitted hard-edge clip to a local sealed scissor. */
    private fun supportsFrozenDeferredPictureClip(operands: PictureCompositeOperandsV1): Boolean =
        operands.compositeScissorAdmitted && operands.copyCompositeScissorTargetLocalI32()?.isEmpty != true

    /** A hard-edge DeviceRect is exact only when its frozen mapped edges are device texels. */
    private fun Matrix3x3F64.integralAxisAlignedDeviceScissorFor(bounds: org.graphiks.math.geometry.RectF32): Boolean {
        if (kxF64 != 0.0 || kyF64 != 0.0 || persp0F64 != 0.0 || persp1F64 != 0.0 || persp2F64 != 1.0)
            return false
        val mapped = mapRectBoundsF64OrNull(RectF64(
            bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble(),
        )) ?: return false
        return listOf(mapped.left, mapped.top, mapped.right, mapped.bottom).all(::isExactI32DeviceEdge)
    }

    private fun isExactI32DeviceEdge(value: Double): Boolean = value.isFinite() &&
        value >= Int.MIN_VALUE.toDouble() && value <= Int.MAX_VALUE.toDouble() &&
        value == value.toLong().toDouble()

    /**
     * Refusals determined entirely by the descriptor's requested semantics.  These must stay
     * observable even when an explicit empty composite clip later elides geometry and targets.
     */
    private fun semanticRefusalFor(descriptor: LayerDescriptor, w6bOwned: Boolean): Pair<String, String>? {
        if (descriptor.backdrop !is EffectStack.Empty) return W6aPlanDiagnostics.UnsupportedBackdrop to
            "W6a does not admit layer backdrop filters."
        if (descriptor.paint == null && descriptor.material != null) return W6aPlanDiagnostics.UnsupportedRestore to "A restore source without its captured paint is unsupported."
        val paint = descriptor.paint ?: return null
        if (!w6bOwned && (paint.imageFilter != null || paint.maskFilter != null)) return W6aPlanDiagnostics.UnsupportedSpatialFilter to
            "W6a does not admit filters on layer restore paint."
        val colorFilter = paint.colorFilter
        if (colorFilter != null && ColorFilterPlanCompilerV1.compile(colorFilter) is ColorFilterCompileResultV1.Refused)
            return W6aPlanDiagnostics.UnsupportedRestore to "W6a cannot compile this restore color filter."
        if (FinalBlendPlanner.plan(descriptor.blend, CoveragePlan.FullOrScissor, SamplePlan.SingleSample,
                PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1()) == null)
            return W6aPlanDiagnostics.UnsupportedRestore to "W6a does not admit this restore blender."
        return null
    }

    /**
     * W6b binds only the captured IR payload to its frozen graph.  The W5 lane may still prove
     * the unfiltered source draw, but never sees a public spatial-filter object or chooses a
     * second filter route.
     */
    private fun stripW6bPayload(command: SceneCommand.Draw): SceneCommand.Draw {
        val paint = command.node.paint
        val effects = (command.node.effects as? EffectStack.Entries)?.let { entries ->
            EffectStack.of(entries.filterNot { it is CapturedFilterRootV1 || it is MaskFilterNode })
        } ?: command.node.effects
        if ((paint == null || paint.imageFilter == null && paint.maskFilter == null) && effects === command.node.effects) return command
        return command.copy(node = command.node.copy(
            paint = paint?.copy(imageFilter = null, maskFilter = null),
            effects = effects,
        ))
    }

    private fun isW6bFilterStack(effects: EffectStack): Boolean = effects is EffectStack.Entries &&
        effects.all { it is CapturedFilterRootV1 || it is MaskFilterNode || it is ColorFilterNode }

    /**
     * Refusals that require a device mapping or a usable layer extent.  An explicit empty
     * composite clip elides this work before any mapping, horizon probe, or target allocation.
     */
    private fun geometryRefusalFor(descriptor: LayerDescriptor, target: RenderTargetDescriptor): Pair<String, String>? {
        if (!finite(descriptor.transform) || descriptor.copyBounds()?.isFinite() == false) {
            return W6aPlanDiagnostics.NonFiniteTransform to "A layer descriptor contains non-finite geometry."
        }
        val localToDevice = descriptor.matrixF64()
        if (LayerMappingF64.ofOrNull(localToDevice, Point2I32.Origin) == null) {
            return W6aPlanDiagnostics.NonFiniteTransform to "A layer mapping is non-invertible."
        }
        val requestedHint = descriptor.copyBounds()?.takeUnless { it.isEmpty }
        val horizonProbe = requestedHint?.let { bounds ->
            RectF64(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
        } ?: RectF64(0.0, 0.0, target.extent.width.toDouble(), target.extent.height.toDouble())
        val mappedProbe = localToDevice.mapRectBoundsF64OrNull(horizonProbe)
            ?: return W6aPlanDiagnostics.MappingHorizon to "A layer mapping crosses a W=0 horizon."
        if (requestedHint != null && mappedProbe.roundOutToRectI32OrNull() == null) {
            return W6aPlanDiagnostics.MappingOverflow to "A layer hint cannot be represented in I32 device texels."
        }
        return null
    }

    private fun LayerDescriptor.matrixF64(): Matrix3x3F64 = transform.let { matrix -> Matrix3x3F64(
        matrix.sx.toDouble(), matrix.kx.toDouble(), matrix.tx.toDouble(),
        matrix.ky.toDouble(), matrix.sy.toDouble(), matrix.ty.toDouble(),
        matrix.persp0.toDouble(), matrix.persp1.toDouble(), matrix.persp2.toDouble(),
    ) }

    private fun hasEmptyExplicitCompositeClip(descriptor: LayerDescriptor): Boolean =
        (descriptor.compositeClip as? org.graphiks.kanvas.render.ir.ClipStackNode.DeviceRect)
            ?.copyBounds()?.isEmpty == true

    private fun hasEmptyExplicitCompositeClip(occurrence: ScopeOccurrence): Boolean =
        hasEmptyExplicitCompositeClip(occurrence.descriptor)

    private fun finite(matrix: org.graphiks.math.matrix.Matrix3x3F32): Boolean = listOf(
        matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty,
        matrix.persp0, matrix.persp1, matrix.persp2,
    ).all(Float::isFinite)

    private fun invalid(code: String, message: String): GpuPlanSelection.InvalidScene =
        GpuPlanSelection.InvalidScene(listOf(diagnostic(code, message)))

    private fun diagnostic(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message,
    )

    public companion object {
        public const val CAPABILITY_ID: String = "w6a.layer.v1"
    }
}
