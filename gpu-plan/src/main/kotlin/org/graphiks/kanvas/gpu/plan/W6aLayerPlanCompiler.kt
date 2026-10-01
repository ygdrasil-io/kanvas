package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.LayerDescriptor
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.CapturedFilterRootV1
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ColorFilterNode
import org.graphiks.kanvas.render.ir.CoverageRequest
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
import org.graphiks.math.matrix.PathTransformClass
import org.graphiks.math.matrix.classifyPathTransform
import org.graphiks.math.matrix.isFinite
import org.graphiks.math.matrix.mapRectBoundsF64OrNull
import org.graphiks.math.matrix.toMatrix3x3F64

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
        val deferredAa: W7AaDeferredOccurrenceFactsV1? = null,
    )

    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        val commands = scene.toList()
        val ownsW6b = W6bFilterGraphConstruction.owns(scene)
        val hasLayerBoundary = commands.any { it is SceneCommand.BeginLayer || it is SceneCommand.EndLayer }
        val ownsPictureStream = scene.filterIsInstance<SceneCommand.Draw>().any { draw ->
            draw.node.geometry is GeometryNode.Picture
        }
        val ownsAaDeferred = ownsAaDeferred(scene, target, runtimeCatalog)
        val ownsRootAaDeferredRect = !hasLayerBoundary && !ownsW6b && ownsRootAaDeferredRect(scene, target)
        val ownsMixedRootAaRect = !hasLayerBoundary && !ownsW6b && ownsMixedRootAaRectFrame(commands)
        val ownsEncodedHairlineFrame = !ownsW6b && target.compositionDomain ==
            org.graphiks.kanvas.render.ir.CompositionDomain.SRGB_ENCODED &&
            CompositionAdmissionV1.validate(scene, target).isEmpty() &&
            commands.filterIsInstance<SceneCommand.Draw>().any(CompositionAdmissionV1::isAdmittedEncodedRectHairline)
        val ownsEncodedRootSegments = !hasLayerBoundary && !ownsW6b && !ownsMixedRootAaRect &&
            (ownsEncodedRootSegmentFrame(scene, target, commands) || ownsEncodedHairlineFrame)
        if (!hasLayerBoundary && !ownsW6b && !ownsPictureStream && !ownsAaDeferred && !ownsRootAaDeferredRect && !ownsMixedRootAaRect && !ownsEncodedRootSegments) {
            return GpuPlanSelection.NotCandidate(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild, "Scene has no layer boundary.")))
        }
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return invalid(W6aPlanDiagnostics.UnsupportedChild, "Scene and target descriptors disagree.")
        }
        // Every W6 owner must validate the complete public scene before it can split root
        // occurrences. Otherwise a valid W7 source family can exceed the graph cap only after
        // fragmentation, reaching native submission without the configured pre-GPU refusal.
        if (org.graphiks.kanvas.render.ir.SceneSemanticValidator.validate(scene) is
            org.graphiks.kanvas.render.ir.SceneSemanticValidationResult.Invalid
        ) return GpuPlanSelection.InvalidScene(listOf(W4dGeneralPlanDiagnostics.diagnostic(
            W4dGeneralPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, "Scene validation failed",
        )))
        // W4e additionally validates finite public facts before it identifies and elides its
        // complex-clip or inverse-path semantic NoOps; preserve that stricter boundary.
        if (scene.filterIsInstance<SceneCommand.Draw>().any { isW4eSemanticNoOp(it.node, target) }) {
            W4dGeneralPathPlanCompiler(
                strokePolicyF64 = org.graphiks.math.geometry.PathStrokePolicyF64(),
                acceptsNarrowTransforms = true,
                retainGeometryConstructionGraph = true,
                runtimeCatalog = runtimeCatalog,
            ).finiteSceneError(scene)?.let { message ->
                return GpuPlanSelection.InvalidScene(listOf(W4dGeneralPlanDiagnostics.diagnostic(
                    W4dGeneralPlanDiagnostics.SceneInvalid, RenderDiagnosticDomain.SCENE, message,
                )))
            }
        }
        // W6b ownership is terminal before child-lane planning or any physical construction.
        // Positive arms remain frozen-but-unmaterialized until Task 3 provides native execution.
        W6bFilterGraphConstruction.admissionRefusalOrNull(scene)?.let { refusal ->
            return GpuPlanSelection.InvalidScene(listOf(refusal))
        }
        // A direct filter with reverse input demand must reach its frozen sampler before its
        // terminal clip. Keep that clip in the immutable W6b occurrence for FilterComposite,
        // while the existing W5 lane receives the un-clipped geometry it must rasterize.
        val directInputDemandCommands = if (ownsW6b) W6bFilterGraphConstruction.positiveOccurrences(scene)
            .asSequence()
            .filter { occurrence -> !occurrence.isLayerOccurrence && !occurrence.isPictureOccurrence &&
                W6bFilterGraphConstruction.hasReverseInputDemandTerminal(occurrence) }
            .map { occurrence -> occurrence.insertionCommandIndexI32 }
            .toSet()
        else emptySet()

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
                    semanticRefusalFor(command.descriptor, ownsW6b, target)?.let { (code, message) -> return invalid(code, message) }
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
                    // W6b may retain a captured filter and strip it from the child lane.  That
                    // is not an admission to the W4d resolved-colour source: this AA path has
                    // no filter or ResolvedCoverage contract yet, so name the W6 boundary
                    // before a later child-chain NotCandidate could become UnsupportedChild.
                    if (stack.isNotEmpty() && command.node.geometry is GeometryNode.Path &&
                        command.node.coverage == CoverageRequest.ANTIALIASED &&
                        (paint?.imageFilter != null || paint?.maskFilter != null)
                    ) {
                        return invalid(
                            W6aPlanDiagnostics.UnsupportedSpatialFilter,
                            "W6a AA resolved-colour sources do not admit image or mask filters.",
                        )
                    }
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
                is SceneCommand.DrawColor -> {
                    // W3 owns the authenticated color recognition; preserving this command as
                    // its own ordered segment lets a plain layer retain that same lane.
                    scopeByDrawIndex[indexI32] = stack.lastOrNull()?.idI32
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
        if (scopes.isEmpty() && !ownsW6b && !ownsPictureStream && !ownsAaDeferred && !ownsRootAaDeferredRect && !ownsMixedRootAaRect && !ownsEncodedRootSegments)
            return invalid(W6aPlanDiagnostics.MalformedStack, "Layer markers did not form a scope.")

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
            val recordedCommand = commands[drawIndexI32]
            if ((recordedCommand as? SceneCommand.Draw)?.node?.geometry is GeometryNode.Picture) {
                return@forEach
            }
            val draws = setOf(drawIndexI32)
            val segment = SceneSnapshot.of(scene.extent, scene.colorSpace, commands.mapIndexed { index, command ->
                if (index in draws) {
                    (command as? SceneCommand.Draw)?.let {
                        stripW6bPayload(it, drawIndexI32 in directInputDemandCommands)
                    } ?: command
                } else SceneCommand.Annotation.of(org.graphiks.math.geometry.RectF32(0f, 0f, 0f, 0f), "w6a.segment", index.toString())
            }, graphLimits)
            val aaSource = W4dGeneralPathPlanCompiler.w6AaColorSource(runtimeCatalog)
            val rootAaRectSource = W4dGeneralPathPlanCompiler.w6RootAaRectStrokeSource(runtimeCatalog)
            val aaCoverageSource = W4dGeneralPathPlanCompiler.w6AaCoverageSource(runtimeCatalog)
            val deferredAaSource = W4dGeneralPathPlanCompiler.w7AaDeferredSource(runtimeCatalog)
            val encodedHairlineSource = W4dGeneralPathPlanCompiler.w6EncodedRectHairlineSource(runtimeCatalog)
            val originalCommand = recordedCommand as? SceneCommand.Draw
            val originalDraw = originalCommand?.node
            val unfilteredDraw = (recordedCommand as? SceneCommand.Draw)?.let {
                stripW6bPayload(it, drawIndexI32 in directInputDemandCommands).node
            }
            // Keep the historical ordinary general-path compiler for every other segment.
            // The AA variant proves DirectTriangle during select, before capability planning.
            val historicalAaSource = originalDraw?.let(aaSource::acceptsW6AaColorSourceScope) == true
            // DST is a semantic no-op independently of geometry, source material, or an
            // inverse/AA clip.  W4e authenticates the same selected fact before preparing its
            // clip mask; preserve that ordering when a later W7 sibling makes W6 the owner.
            val semanticNoOp = originalDraw?.let { draw -> isW4eSemanticNoOp(draw, target) } == true
            if (semanticNoOp) return@forEach
            val rootAaSource = scopeI32 == null && !ownsW6b &&
                originalDraw?.coverage == CoverageRequest.ANTIALIASED && historicalAaSource
            // Preserve W4a for its identity/axis-aligned Rect domain.  A finite general-affine
            // Rect has no analytic source lane, so it may select the shared W7 occurrence path.
            val deferredAa = originalCommand?.takeIf { draw ->
                // A direct layer Rect keeps W4a unless the existing affine fact proves its
                // analytic lane is unavailable. Root keeps its historical destination-read
                // route; Picture occurrence selection applies its corresponding guard later.
                (scopeI32 == null || draw.node.geometry !is GeometryNode.Rect ||
                    requiresW7AffineRectProjection(draw.node)) &&
                    retainsW7AaDeferredSourceAuthority(segment, draw, target, runtimeCatalog)
            }?.node?.let { draw ->
                selectedW7AaDeferredBlend(draw, target, runtimeCatalog, historicalAaSource)
                    ?.let { W7AaDeferredOccurrenceFactsV1(drawIndexI32, it) }
            }
            // DST is a selected W7 semantic NoOp.  Keep W6 ownership so sibling rendering
            // remains native, but publish no source lane, resource, pass, or destination write.
            if (deferredAa?.blend == BlendPlan.NoOpV1) return@forEach
            val rootAaRectStroke = ownsMixedRootAaRect && scopeI32 == null &&
                originalDraw?.let(rootAaRectSource::acceptsW6RootAaRectStrokeScope) == true
            val encodedHairline = ownsEncodedHairlineFrame &&
                (recordedCommand as? SceneCommand.Draw)?.let(CompositionAdmissionV1::isAdmittedEncodedRectHairline) == true
            val rootAaCoverage = scopeI32 == null && ownsW6b &&
                originalDraw?.coverage == CoverageRequest.ANTIALIASED &&
                originalDraw.paint?.let { paint ->
                    (paint.maskFilter as? org.graphiks.kanvas.render.ir.MaskFilterNode.Blur)?.style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL &&
                        paint.imageFilter == null && paint.colorFilter == null && paint.shader == null
                } == true &&
                unfilteredDraw?.let(aaCoverageSource::acceptsW6AaColorSourceScope) == true
            val generalPath = when {
                deferredAa != null -> deferredAaSource
                rootAaCoverage -> aaCoverageSource
                rootAaRectStroke -> rootAaRectSource
                encodedHairline -> encodedHairlineSource
                (scopeI32 != null && originalDraw?.let(aaSource::acceptsW6AaColorSourceScope) == true) || rootAaSource -> aaSource
                else -> W4dGeneralPathPlanCompiler()
            }
            val child = CapabilityCompilerChain.ofProjected(
                if (deferredAa != null) listOf(
                    W5bVerticesPlanCompiler(runtimeCatalog), W5bPointPlanCompiler(runtimeCatalog), W5eImagePlanCompiler(),
                    W4dPathStrokePlanCompiler(), generalPath,
                ) else listOf(
                    W5bVerticesPlanCompiler(runtimeCatalog), W5bPointPlanCompiler(runtimeCatalog), W5eImagePlanCompiler(),
                    W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler(), W4bAnalyticRRectPlanCompiler(),
                    W4cPathFillPlanCompiler(), W4dPathStrokePlanCompiler(), generalPath,
                ),
                runtimeCatalog,
            )
            when (val selection = child.select(segment, target)) {
                is GpuPlanSelection.Candidate -> segments += Segment(scopeI32, drawIndexI32, child, selection.candidate, deferredAa)
                // A source lane that is admissible except for its W5 material must retain that
                // material authority.  The outer router will terminalize it because W6 owns the
                // layer boundary; collapsing it to UnsupportedChild would both lose the public
                // diagnostic and make recovery observably unstable.
                is GpuPlanSelection.MaterialOnlyRefusal -> return selection
                is GpuPlanSelection.InvalidScene -> return selection
                is GpuPlanSelection.ResourceLimitExceeded -> return selection
                is GpuPlanSelection.NotCandidate -> return if (ownsMixedRootAaRect || ownsEncodedRootSegments)
                    GpuPlanSelection.NotCandidate(listOf(diagnostic(W6aPlanDiagnostics.UnsupportedChild,
                        "Mixed root frame is outside its complete child source lanes.")))
                else invalid(W6aPlanDiagnostics.UnsupportedChild, "Layer segment is outside the admitted child geometry/source lanes.")
            }
        }
        return GpuPlanSelection.Candidate(Candidate(this, scene, scene.canonicalId, target, immutableScopes, segments.toList()))
    }

    /** W7 owns only a complete direct Rect frame: at least one root AA stroke and one linear gradient sibling. */
    private fun ownsMixedRootAaRectFrame(commands: List<SceneCommand>): Boolean {
        val rootSource = W4dGeneralPathPlanCompiler.w6RootAaRectStrokeSource(runtimeCatalog)
        var stroke = false
        var gradient = false
        for (command in commands) {
            if (command is SceneCommand.SetTransform || command is SceneCommand.SetClip || command is SceneCommand.Annotation) continue
            val draw = (command as? SceneCommand.Draw)?.node ?: return false
            if (rootSource.acceptsW6RootAaRectStrokeScope(draw)) {
                stroke = true
                continue
            }
            if (!acceptsMixedRootRectFill(draw)) return false
            gradient = gradient || draw.material is MaterialNode.LinearGradient
        }
        return stroke && gradient
    }

    /** The W7 root extension is closed to an already-admitted encoded DrawColor mixture. */
    private fun ownsEncodedRootSegmentFrame(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        commands: List<SceneCommand>,
    ): Boolean = target.compositionDomain == org.graphiks.kanvas.render.ir.CompositionDomain.SRGB_ENCODED &&
        CompositionAdmissionV1.validate(scene, target).isEmpty() &&
        commands.any { it is SceneCommand.DrawColor } && commands.any { it is SceneCommand.Draw } &&
        commands.all { it is SceneCommand.DrawColor || it is SceneCommand.Draw ||
            it is SceneCommand.SetTransform || it is SceneCommand.SetClip || it is SceneCommand.Annotation }

    /** Root ownership is whole-frame: every non-stroke sibling is a direct, bounded Rect fill. */
    private fun acceptsMixedRootRectFill(draw: DrawNode): Boolean {
        val paint = draw.paint ?: return false
        val bounds = (draw.geometry as? GeometryNode.Rect)?.copyBounds() ?: return false
        val matrix = draw.transform.toMatrix3x3F64()
        val srcOver = when (val blend = draw.blend) {
            BlendNode.SrcOver -> true
            is BlendNode.Mode -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER
            is BlendNode.Paint -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER && blend.blender == null
            is BlendNode.Custom -> false
        }
        val hardClip = when (val clip = draw.clip) {
            ClipStackNode.Empty -> true
            is ClipStackNode.DeviceRect -> !clip.antiAlias && clip.copyBounds().let { rect ->
                listOf(rect.left, rect.top, rect.right, rect.bottom).all { it.isFinite() && it == it.toInt().toFloat() }
            }
            else -> false
        }
        return draw.origin == DrawOrigin.RECT && !bounds.isEmpty &&
            listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite) &&
            (draw.material is MaterialNode.Solid || draw.material is MaterialNode.LinearGradient) &&
            paint.style == PaintStyleNode.FILL && draw.coverage in setOf(CoverageRequest.HARD_EDGE, CoverageRequest.ANTIALIASED) &&
            draw.resource == null && draw.operationBlendMode == null && paint.blender == null && paint.colorFilter == null &&
            paint.maskFilter == null && paint.imageFilter == null && paint.pathEffect == null && draw.effects == EffectStack.Empty &&
            srcOver && matrix.isFinite() && matrix.classifyPathTransform() in setOf(PathTransformClass.Identity, PathTransformClass.AxisAlignedAffine) &&
            matrix.sxF64 != 0.0 && matrix.syF64 != 0.0 && hardClip
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
        W6bFilterGraphConstruction.nativeCapabilityRefusalOrNull(selected.scene, capabilities)?.let { refusal ->
            return RenderPlanResult.InvalidScene(listOf(refusal))
        }
        return try {
            val bindings = mutableListOf<W6aLayerSourceBinding>()
            for (segment in selected.segments) when (val result = segment.compiler.constructSourceLanes(segment.candidate, capabilities, budget)) {
                is RenderPlanResult.Ready -> result.plan.forEach { bindings += W6aLayerSourceBinding(
                    segment.scopeI32, segment.firstCommandIndexI32, it, deferredAa = segment.deferredAa,
                ) }
                // The child has already named its owner/capture limit. W6 only owns the
                // aggregate peak after every child source is sealed.
                is RenderPlanResult.ResourceLimitExceeded -> return result
                is RenderPlanResult.GapNotMigrated -> return result
                is RenderPlanResult.GapOnPromotedScope -> return result
                is RenderPlanResult.InvalidScene -> return result
            }
            val frame = W6aLayerGraphConstruction(PlanId("w6a.${selected.sceneCanonicalId.value}" +
                if (selected.target.compositionDomain == org.graphiks.kanvas.render.ir.CompositionDomain.SRGB_ENCODED) ".SRGB_ENCODED" else ""), org.graphiks.math.geometry.SizeI32(selected.target.extent.width, selected.target.extent.height),
                capabilities, budget, selected.occurrences, bindings, selected.scene, runtimeCatalog,
                logicalColorFormat(selected.target))
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
        } catch (failure: W6aScanSpanDrawLimitFailure) {
            RenderPlanResult.ResourceLimitExceeded(listOf(W4ePlanDiagnostics.diagnostic(
                W4ePlanDiagnostics.ScanSpanDrawLimit,
                org.graphiks.kanvas.render.ir.RenderDiagnosticDomain.RESOURCE,
                failure.message ?: "W4e inverse scan-span producers exceed the frame-wide draw limit.",
            )))
        } catch (failure: W6aResourceLimitFailure) {
            val message = failure.message ?: "Layer frame budget exceeded."
            if (W6bFilterGraphConstruction.ownsW6dAdvanced(selected.scene)) {
                RenderPlanResult.ResourceLimitExceeded(listOf(W6dPlanDiagnostics.budgetRefusal(message)))
            } else if (W6bFilterGraphConstruction.owns(selected.scene)) {
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

    /** The W6 native arm admits only frozen W6b operations and the Task 1 W6c Crop witness. */
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
            is FilterPassOperationV1.Offset -> true
            is FilterPassOperationV1.Tile -> true
            is FilterPassOperationV1.ColorFilter -> operation.uniformResource != null && operation.uniformOffsetBytesI64 != null &&
                operation.uniformCapacityBytesI64 != null
            is FilterPassOperationV1.Merge,
            is FilterPassOperationV1.Blend,
            is FilterPassOperationV1.Morphology,
            -> true
            is FilterPassOperationV1.SeparableBlur -> operation.kind in nativeBlurKinds
            is FilterPassOperationV1.MaskBlurStyle,
            is FilterPassOperationV1.MaskShader,
            is FilterPassOperationV1.MaskTable,
            is FilterPassOperationV1.MaterializedSource,
            is FilterPassOperationV1.DropShadowColorize,
            is FilterPassOperationV1.DropShadowComposite,
            -> true
            is FilterPassOperationV1.MatrixConvolution,
            is FilterPassOperationV1.DisplacementMap,
            is FilterPassOperationV1.Magnifier,
            -> true
            is FilterPassOperationV1.Lighting -> pass.frozenSamplingProgram?.program?.let { program ->
                when (operation.family) {
                    LightingFamilyV1.DISTANT_DIFFUSE -> program.programId == W6dSamplingProgramIdV1.DISTANT_DIFFUSE_RGBA8_V1
                    LightingFamilyV1.POINT_DIFFUSE -> program.programId == W6dSamplingProgramIdV1.POINT_DIFFUSE_RGBA8_V1
                    LightingFamilyV1.SPOT_DIFFUSE -> program.programId == W6dSamplingProgramIdV1.SPOT_DIFFUSE_RGBA8_V1
                    LightingFamilyV1.DISTANT_SPECULAR -> program.programId == W6dSamplingProgramIdV1.DISTANT_SPECULAR_RGBA8_V1
                    LightingFamilyV1.POINT_SPECULAR -> program.programId == W6dSamplingProgramIdV1.POINT_SPECULAR_RGBA8_V1
                    LightingFamilyV1.SPOT_SPECULAR -> program.programId == W6dSamplingProgramIdV1.SPOT_SPECULAR_RGBA8_V1
                }
            } == true
            is FilterPassOperationV1.Picture -> pass.frozenSamplingProgram?.program?.let { program ->
                program is W6dSamplingProgramV1.Picture &&
                    program.copySampling().matches(operation.copyPictureSampling()) &&
                    pass.frozenSamplingProgram.inputs() == pass.inputs() &&
                    pass.frozenSamplingProgram.output == pass.output
            } == true
            is FilterPassOperationV1.RuntimeImageOpacity -> pass.frozenSamplingProgram?.program?.let { program ->
                program is W6dSamplingProgramV1.RuntimeImageOpacity &&
                    program.alphaF32 == operation.alphaF32 &&
                    program.alphaUniformOffsetBytesI32 == operation.alphaUniformOffsetBytesI32 &&
                    pass.frozenSamplingProgram.inputs() == pass.inputs() &&
                    pass.frozenSamplingProgram.output == pass.output
            } == true
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
    private fun semanticRefusalFor(descriptor: LayerDescriptor, w6bOwned: Boolean, target: RenderTargetDescriptor): Pair<String, String>? {
        if (descriptor.paint == null && descriptor.material != null) return W6aPlanDiagnostics.UnsupportedRestore to "A restore source without its captured paint is unsupported."
        val paint = descriptor.paint ?: return null
        if (!w6bOwned && (paint.imageFilter != null || paint.maskFilter != null)) return W6aPlanDiagnostics.UnsupportedSpatialFilter to
            "W6a does not admit filters on layer restore paint."
        val colorFilter = paint.colorFilter
        if (colorFilter != null && ColorFilterPlanCompilerV1.compile(colorFilter) is ColorFilterCompileResultV1.Refused)
            return W6aPlanDiagnostics.UnsupportedRestore to "W6a cannot compile this restore color filter."
        if (FinalBlendPlanner.plan(descriptor.blend, CoveragePlan.FullOrScissor, SamplePlan.SingleSample,
                logicalColorFormat(target).blendTargetClampV1()) == null)
            return W6aPlanDiagnostics.UnsupportedRestore to "W6a does not admit this restore blender."
        return null
    }

    /**
     * W6b binds only the captured IR payload to its frozen graph.  The W5 lane may still prove
     * the unfiltered source draw, but never sees a public spatial-filter object or chooses a
     * second filter route.
     */
    private fun stripW6bPayload(command: SceneCommand.Draw, deferTerminalClip: Boolean = false): SceneCommand.Draw {
        val paint = command.node.paint
        val effects = (command.node.effects as? EffectStack.Entries)?.let { entries ->
            EffectStack.of(entries.filterNot { it is CapturedFilterRootV1 || it is MaskFilterNode })
        } ?: command.node.effects
        if ((paint == null || paint.imageFilter == null && paint.maskFilter == null) && effects === command.node.effects &&
            !deferTerminalClip) return command
        return command.copy(node = command.node.copy(
            paint = paint?.copy(imageFilter = null, maskFilter = null),
            effects = effects,
            clip = if (deferTerminalClip) ClipStackNode.Empty else command.node.clip,
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

        private fun logicalColorFormat(target: RenderTargetDescriptor): PlanLogicalColorFormat = when (target.compositionDomain) {
            org.graphiks.kanvas.render.ir.CompositionDomain.LINEAR -> PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL
            org.graphiks.kanvas.render.ir.CompositionDomain.SRGB_ENCODED -> PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL
        }

        /** Captured counterpart of the DisplayOp hard Picture seed; traversal stays bounded. */
        internal fun ownsHardPictureStream(
            scene: SceneSnapshot,
        ): Boolean {
            val pending = java.util.ArrayDeque<SceneSnapshot>()
            scene.filterIsInstance<SceneCommand.Draw>().forEach { draw ->
                (draw.node.geometry as? GeometryNode.Picture)?.let { pending.addLast(it.scene) }
            }
            var inspectedI32 = 0
            while (pending.isNotEmpty()) {
                val pictureScene = pending.removeLast()
                pictureScene.forEach { command ->
                    inspectedI32 = try { Math.addExact(inspectedI32, 1) } catch (_: ArithmeticException) { return true }
                    if (inspectedI32 > org.graphiks.kanvas.render.ir.GraphLimits().maxNodes) return true
                    val draw = command as? SceneCommand.Draw ?: return@forEach
                    when (val geometry = draw.node.geometry) {
                        is GeometryNode.Picture -> pending.addLast(geometry.scene)
                        is GeometryNode.Rect -> if (isHardPictureRectFill(draw.node)) return true
                        is GeometryNode.Path -> if (isHardPicturePathFill(draw.node)) return true
                        else -> Unit
                    }
                }
            }
            return false
        }

        /** Closed raw-source family shared with Picture clip-refusal admission. */
        internal fun isPlainHardPictureSource(node: DrawNode): Boolean =
            isHardPictureRectFill(node) || isHardPicturePathFill(node)

        private fun isHardPicturePathFill(node: DrawNode): Boolean {
            val paint = node.paint ?: return false
            val srcOver = when (val blend = node.blend) {
                BlendNode.SrcOver -> true
                is BlendNode.Mode -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER
                is BlendNode.Paint -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER && blend.blender == null
                is BlendNode.Custom -> false
            }
            return node.origin == DrawOrigin.PATH && node.geometry is GeometryNode.Path &&
                node.coverage == CoverageRequest.HARD_EDGE && node.material is MaterialNode.Solid &&
                paint.style == PaintStyleNode.FILL && paint.shader == null && paint.blender == null && paint.colorFilter == null &&
                paint.maskFilter == null && paint.imageFilter == null && paint.pathEffect == null &&
                node.effects == EffectStack.Empty && srcOver
        }

        /** Ownership is structural; W4d later owns finite/empty/transform source admission. */
        private fun isHardPictureRectFill(node: DrawNode): Boolean {
            val paint = node.paint ?: return false
            val srcOver = when (val blend = node.blend) {
                BlendNode.SrcOver -> true
                is BlendNode.Mode -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER
                is BlendNode.Paint -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER && blend.blender == null
                is BlendNode.Custom -> false
            }
            return node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect &&
                node.coverage == CoverageRequest.HARD_EDGE && node.material is MaterialNode.Solid &&
                paint.style == PaintStyleNode.FILL && paint.shader == null && paint.blender == null &&
                paint.colorFilter == null && paint.maskFilter == null && paint.imageFilter == null &&
                paint.pathEffect == null && node.effects == EffectStack.Empty && srcOver
        }

        internal fun ownsAaDeferred(
            scene: SceneSnapshot,
            target: RenderTargetDescriptor,
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): Boolean = target.compositionDomain == org.graphiks.kanvas.render.ir.CompositionDomain.LINEAR &&
            scene.filterIsInstance<SceneCommand.Draw>().any { draw ->
                val historical = W4dGeneralPathPlanCompiler.w6AaColorSource(catalog).acceptsW6AaColorSourceScope(draw.node)
                selectedW7AaDeferredBlend(draw.node, target, catalog, historical) != null &&
                    retainsW7AaDeferredSourceAuthority(scene, draw, target, catalog)
            }

        /**
         * Scope admission deliberately includes an off-target RECT.  The sole selection result
         * that relinquishes W7 ownership is W4d's exact empty-geometry limit; all invalid,
         * material, capability, and real resource refusals remain terminal on their native lane.
         */
        private fun retainsW7AaDeferredSourceAuthority(
            scene: SceneSnapshot,
            draw: SceneCommand.Draw,
            target: RenderTargetDescriptor,
            catalog: RuntimeEffectSemanticCatalogSnapshot,
        ): Boolean {
            val selected = W4dGeneralPathPlanCompiler.w7AaDeferredSource(catalog).select(
                SceneSnapshot.of(scene.extent, scene.colorSpace, listOf(draw), scene.graphLimits), target,
            )
            return selected !is GpuPlanSelection.ResourceLimitExceeded || selected.diagnostics().none { diagnostic ->
                diagnostic.code == W4dGeneralPlanDiagnostics.PathResourceLimit &&
                    diagnostic.message == "W4d.2 retained no visible prepared geometry"
            }
        }

        /** Mirrors W4e's blend elision domain without extending it to ordinary W7 DST draws. */
        private fun isW4eSemanticNoOp(draw: DrawNode, target: RenderTargetDescriptor): Boolean {
            val complexClipOrInversePath = draw.clip is ClipStackNode.Operations ||
                ((draw.geometry as? GeometryNode.Path)?.path?.fillRule in setOf(
                    org.graphiks.math.geometry.FillRule.INVERSE_WINDING,
                    org.graphiks.math.geometry.FillRule.INVERSE_EVEN_ODD,
                ))
            return complexClipOrInversePath && FinalBlendPlanner.plan(
                draw.blend,
                CoveragePlan.FullOrScissor,
                SamplePlan.SingleSample,
                logicalColorFormat(target).blendTargetClampV1(),
                BlendCoverageApplicationV1.SourceMultiplication,
            ) == BlendPlan.NoOpV1
        }

        /** Closed W7 selection fact shared by root ownership and occurrence construction. */
        internal fun selectedW7AaDeferredBlend(
            draw: DrawNode,
            target: RenderTargetDescriptor,
            catalog: RuntimeEffectSemanticCatalogSnapshot,
            historicalAaSource: Boolean,
        ): BlendPlan? {
            if (!W4dGeneralPathPlanCompiler.w7AaDeferredSource(catalog).acceptsW7AaDeferredSourceScope(draw)) return null
            val initial = requireNotNull(FinalBlendPlanner.plan(draw.blend, CoveragePlan.StencilAA4,
                SamplePlan.SingleSample, logicalColorFormat(target).blendTargetClampV1(),
                BlendCoverageApplicationV1.SourceMultiplication, BlendCoverageEncodingV1.ScalarCoverageInShader))
            val blend = if (initial is BlendPlan.FixedFunctionV1 && initial.mode != org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER) {
                requireNotNull(FinalBlendPlanner.plan(draw.blend, CoveragePlan.StencilAA4,
                    SamplePlan.SingleSample, logicalColorFormat(target).blendTargetClampV1(),
                    BlendCoverageApplicationV1.DestinationInterpolation, BlendCoverageEncodingV1.ScalarCoverageInShader))
            } else initial
            return blend.takeIf { (it is BlendPlan.DestinationReadV1) ||
                ((!historicalAaSource || requiresW7AffineRectProjection(draw)) && it.isW7AaDeferredBlendV1()) }
        }

        /** W4a cannot lower a sheared/rotated Rect; W7 may project only that missing lane. */
        internal fun requiresW7AffineRectProjection(draw: DrawNode): Boolean =
            draw.origin == DrawOrigin.RECT && draw.geometry is GeometryNode.Rect &&
                draw.transform.toMatrix3x3F64().classifyPathTransform() == PathTransformClass.GeneralAffine

        /** Closed root-only W7 ownership for the existing W4a analytic Rect lane. */
        internal fun ownsRootAaDeferredRect(
            scene: SceneSnapshot,
            target: RenderTargetDescriptor,
        ): Boolean {
            if (target.compositionDomain != org.graphiks.kanvas.render.ir.CompositionDomain.LINEAR) return false
            val draws = scene.filterIsInstance<SceneCommand.Draw>()
            return draws.isNotEmpty() && scene.all { it is SceneCommand.Draw } &&
                draws.all { acceptsRootAaDeferredRect(it.node) } &&
                draws.any { it.node.coverage == CoverageRequest.ANTIALIASED &&
                    rootAaDeferredRectBlendMode(it.node.blend) == org.graphiks.kanvas.render.ir.BlendMode.PLUS } &&
                draws.any { it.node.coverage == CoverageRequest.HARD_EDGE &&
                    rootAaDeferredRectBlendMode(it.node.blend) == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER }
        }

        private fun acceptsRootAaDeferredRect(draw: DrawNode): Boolean {
            val paint = draw.paint ?: return false
            val bounds = (draw.geometry as? GeometryNode.Rect)?.copyBounds() ?: return false
            var material = draw.material
            while (material is MaterialNode.Opacity) material = material.material
            val matrix = draw.transform.toMatrix3x3F64()
            return draw.origin == DrawOrigin.RECT && !bounds.isEmpty &&
                listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite) &&
                material is MaterialNode.Solid && paint.style == PaintStyleNode.FILL &&
                draw.coverage in setOf(CoverageRequest.HARD_EDGE, CoverageRequest.ANTIALIASED) &&
                draw.resource == null && draw.operationBlendMode == null && paint.blender == null &&
                paint.colorFilter == null && paint.maskFilter == null && paint.imageFilter == null &&
                paint.pathEffect == null && draw.effects == EffectStack.Empty &&
                rootAaDeferredRectBlendMode(draw.blend) != null && matrix.isFinite() &&
                matrix.classifyPathTransform() in setOf(PathTransformClass.Identity, PathTransformClass.AxisAlignedAffine) &&
                matrix.sxF64 != 0.0 && matrix.syF64 != 0.0
        }

        private fun rootAaDeferredRectBlendMode(blend: BlendNode): org.graphiks.kanvas.render.ir.BlendMode? = when (blend) {
            BlendNode.SrcOver -> org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER
            is BlendNode.Mode -> blend.mode.takeIf {
                it in setOf(org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER, org.graphiks.kanvas.render.ir.BlendMode.PLUS)
            }
            is BlendNode.Paint -> blend.mode.takeIf {
                blend.blender == null && it in setOf(
                    org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER,
                    org.graphiks.kanvas.render.ir.BlendMode.PLUS,
                )
            }
            is BlendNode.Custom -> null
        }
    }
}
