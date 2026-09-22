package org.graphiks.kanvas.gpu.plan

import java.util.ArrayDeque
import org.graphiks.kanvas.render.ir.CapturedFilterInputV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeId
import org.graphiks.kanvas.render.ir.CapturedFilterNodeV1
import org.graphiks.kanvas.render.ir.CapturedFilterRootV1
import org.graphiks.kanvas.render.ir.CapturedFilterTableV1
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaskFilterNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.expandForBlurF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.vector.Vector2F64

/**
 * W6b's sole captured-filter authority. It seals real filter graph facts before Task 2 refuses
 * the unavailable native materialization; Task 3 consumes those facts instead of re-planning.
 */
internal object W6bFilterGraphConstruction {
    internal class SourceBinding(
        val resourceId: PlanResourceId,
        extent: SizeI32,
        val originDeviceI32: Point2I32,
        val mapping: LayerMappingF64,
    ) {
        private val extentSnapshot = extent.copy()
        fun copyExtentI32(): SizeI32 = extentSnapshot.copy()
        fun withResource(resourceId: PlanResourceId): SourceBinding = SourceBinding(
            resourceId,
            extentSnapshot,
            Point2I32(originDeviceI32.x, originDeviceI32.y),
            mapping,
        )
    }

    internal class FrozenGraph internal constructor(
        resources: List<PlanResource>,
        passes: List<PlanPass.FilterPass>,
    ) {
        private val resourceSnapshot = immutableList(resources)
        private val passSnapshot = immutableList(passes)
        fun resources(): List<PlanResource> = resourceSnapshot
        fun passes(): List<PlanPass.FilterPass> = passSnapshot
    }

    internal class ConstructionFailure(val diagnostic: RenderDiagnostic) : IllegalArgumentException(diagnostic.message)

    internal fun owns(scene: SceneSnapshot): Boolean = ownership(scene).isOwned

    /** Unsupported W6c/W6d/backdrop/filtered-previous cases stop before any source planning. */
    internal fun admissionRefusalOrNull(scene: SceneSnapshot): RenderDiagnostic? {
        val ownership = ownership(scene)
        if (!ownership.isOwned) return null
        if (ownership.traversalBounded) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.InvalidBounds,
            "W6b picture traversal exceeded its sealed capture bound.",
        )
        if (ownership.hasBackdrop) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.UnsupportedBackdrop,
            "W6b does not admit backdrop filters.",
        )
        if (ownership.hasFilteredPrevious) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.FilteredPrevious,
            "W6b does not admit initWithPrevious combined with a spatial filter.",
        )
        if (ownership.hasUnsupportedImageFamily()) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.UnsupportedFamily,
            "The captured image-filter family belongs to W6c or W6d.",
        )
        return null
    }

    /**
     * Appends frozen W6b resources and passes to the existing W6a graph. It deliberately
     * performs no native allocation or renderer execution.
     */
    internal fun freezePositiveGraph(
        scene: SceneSnapshot,
        sourceForTopLevelCommand: (Int) -> SourceBinding?,
        nestedPictureSource: SourceBinding,
        firstTargetOrdinalI32: Int,
        firstPassOrdinalI32: Int,
    ): FrozenGraph {
        admissionRefusalOrNull(scene)?.let { throw ConstructionFailure(it) }
        val occurrences = positiveOccurrences(scene)
        if (occurrences.isEmpty()) return FrozenGraph(emptyList(), emptyList())

        val targets = mutableListOf<TargetSpec>()
        val passes = mutableListOf<PlanPass.FilterPass>()
        var targetOrdinalI32 = firstTargetOrdinalI32
        var passOrdinalI32 = firstPassOrdinalI32

        fun allocateTarget(source: SourceBinding): SourceBinding {
            val extent = source.copyExtentI32()
            val id = planResourceId(PlanResourceRole.FilterTarget, targetOrdinalI32)
            targetOrdinalI32 = Math.addExact(targetOrdinalI32, 1)
            targets += TargetSpec(id, extent)
            return source.withResource(id)
        }

        fun bounds(source: SourceBinding, sigmaXF32: Float = 0f, sigmaYF32: Float = 0f): FilterBoundsPlanV1 {
            val desired = source.copyDeviceBoundsI32()
            val required = RectF64(
                desired.left.toDouble(), desired.top.toDouble(), desired.right.toDouble(), desired.bottom.toDouble(),
            ).expandForBlurF64OrNull(sigmaXF32, sigmaYF32)?.roundOutToRectI32OrNull()
                ?.intersectWith(desired)
                ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b filter bounds cannot be represented in checked I32 texels.",
                ))
            return FilterBoundsPlanV1(desired, desired, required, desired, source.originDeviceI32)
        }

        fun appendBlur(
            nodeId: CapturedFilterNodeId,
            source: SourceBinding,
            sigmaXF32: Float,
            sigmaYF32: Float,
            tileMode: org.graphiks.kanvas.render.ir.TileMode,
            horizontalKind: FilterImplementationKindV1 = FilterImplementationKindV1.IMAGE_BLUR_X,
            verticalKind: FilterImplementationKindV1 = FilterImplementationKindV1.IMAGE_BLUR_Y,
            key: FilterEvaluationKeyV1 = FilterEvaluationKeyV1.of(
                nodeId, source.resourceId, source.mapping, source.copyDeviceBoundsI32(),
            ),
        ): SourceBinding {
            val horizontal = allocateTarget(source)
            passes += PlanPass.FilterPass(
                passOrdinalI32++, listOf(source.resourceId), horizontal.resourceId, key,
                FilterPassOperationV1.SeparableBlur(horizontalKind, sigmaXF32, FilterAxisV1.X, tileMode, bounds(source, sigmaXF32, 0f)),
            )
            val vertical = allocateTarget(horizontal)
            passes += PlanPass.FilterPass(
                passOrdinalI32++, listOf(horizontal.resourceId), vertical.resourceId, key,
                FilterPassOperationV1.SeparableBlur(verticalKind, sigmaYF32, FilterAxisV1.Y, tileMode, bounds(horizontal, 0f, sigmaYF32)),
            )
            // The occurrence owns the immutable source. Individual passes own their immediate
            // inputs, so Y may read X while retaining this exact contextual key.
            require(passes[passes.lastIndex - 1].inputs().single() == key.boundSourceId)
            return vertical
        }

        fun materializeNode(table: CapturedFilterTableV1, id: CapturedFilterNodeId, source: SourceBinding): SourceBinding =
            when (val node = table.nodeAt(id)) {
                is CapturedFilterNodeV1.Blur -> {
                    val input = node.input.materializeInput(table, source, ::materializeNode)
                    appendBlur(id, input, node.sigmaX, node.sigmaY, node.tileMode)
                }
                is CapturedFilterNodeV1.DropShadow -> {
                    val input = node.input.materializeInput(table, source, ::materializeNode)
                    val key = FilterEvaluationKeyV1.of(id, input.resourceId, input.mapping, input.copyDeviceBoundsI32())
                    val blurred = appendBlur(
                        id,
                        input,
                        node.sigmaX,
                        node.sigmaY,
                        org.graphiks.kanvas.render.ir.TileMode.CLAMP,
                        key = key,
                    )
                    val colorized = allocateTarget(blurred)
                    passes += PlanPass.FilterPass(
                        passOrdinalI32++, listOf(blurred.resourceId), colorized.resourceId, key,
                        FilterPassOperationV1.DropShadowColorize(node.color, Vector2F64(node.dx.toDouble(), node.dy.toDouble()), bounds(blurred)),
                    )
                    val composite = allocateTarget(colorized)
                    passes += PlanPass.FilterPass(
                        passOrdinalI32++, listOf(colorized.resourceId, input.resourceId), composite.resourceId, key,
                        FilterPassOperationV1.DropShadowComposite(node.mode, bounds(colorized)),
                    )
                    require(passes[passes.lastIndex - 3].inputs().single() == key.boundSourceId)
                    composite
                }
                else -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.UnsupportedFamily,
                    "The captured image-filter family belongs to W6c or W6d.",
                ))
            }

        occurrences.forEach { occurrence ->
            val source = occurrence.topLevelCommandIndexI32?.let(sourceForTopLevelCommand) ?: nestedPictureSource
            occurrence.root?.let { root -> materializeNode(occurrence.table, root.id, source) }
            occurrence.mask?.let { mask ->
                val key = FilterEvaluationKeyV1.forMaskOccurrence(
                    occurrence.maskOccurrenceI32, source.resourceId, source.mapping, source.copyDeviceBoundsI32(),
                )
                when (mask) {
                    is MaskFilterNode.Blur -> appendBlur(
                        CapturedFilterNodeId(0), source, mask.sigma, mask.sigma,
                        org.graphiks.kanvas.render.ir.TileMode.CLAMP,
                        FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
                        FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y,
                        key,
                    )
                    is MaskFilterNode.Shader -> {
                        val target = allocateTarget(source)
                        passes += PlanPass.FilterPass(
                            passOrdinalI32++, listOf(source.resourceId), target.resourceId, key,
                            FilterPassOperationV1.MaskShader(MaterialPlanRef(0), 0L, bounds(source)),
                        )
                        require(passes.last().inputs().single() == key.boundSourceId)
                    }
                    is MaskFilterNode.Table -> {
                        val target = allocateTarget(source)
                        passes += PlanPass.FilterPass(
                            passOrdinalI32++, listOf(source.resourceId), target.resourceId, key,
                            FilterPassOperationV1.MaskTable(mask.table, bounds(source)),
                        )
                        require(passes.last().inputs().single() == key.boundSourceId)
                    }
                }
            }
        }
        return FrozenGraph(targets.map { target -> PlanResource.of(
            PlanResourceRole.FilterTarget,
            target.id.value.substringAfter(':').toInt(),
            PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            target.extent,
            checkedTextureBytesI64(4, target.extent.width, target.extent.height, 1),
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal,
            // Physical layout is deliberately pessimistic: every frame-local reservation remains
            // live through frame completion, so cache warmth or logical pass locality cannot
            // reduce admission bytes.
            0,
            // The caller appends exactly one terminal readback after every frozen filter pass.
            // Keep every target live through that pass, independent of any later physical-slot
            // coalescing policy.
            Math.addExact(passOrdinalI32, 1),
        ) }, passes)
    }

    private fun ownership(scene: SceneSnapshot): Ownership {
        val roots = mutableListOf<RootOccurrence>()
        var mask = false
        var backdrop = false
        var filteredPrevious = false
        val bounded = visitScenes(scene) { nestedScene, command, _ ->
            when (command) {
                is SceneCommand.Draw -> {
                    val payload = filterPayload(command.node.paint, command.node.effects)
                    payload.root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                    mask = mask || payload.mask != null
                }
                is SceneCommand.BeginLayer -> {
                    val payload = filterPayload(command.descriptor.paint, command.descriptor.effects)
                    payload.root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                    mask = mask || payload.mask != null
                    if (command.descriptor.backdrop !is EffectStack.Empty) {
                        backdrop = true
                        filterPayload(null, command.descriptor.backdrop).root?.let { root ->
                            roots += RootOccurrence(nestedScene.filterTable, root)
                        }
                    }
                    filteredPrevious = filteredPrevious || command.descriptor.initWithPrevious &&
                        (payload.root != null || payload.mask != null)
                }
                else -> Unit
            }
        }
        return Ownership(roots, mask, backdrop, filteredPrevious, bounded)
    }

    private fun positiveOccurrences(scene: SceneSnapshot): List<PositiveOccurrence> {
        val result = mutableListOf<PositiveOccurrence>()
        var nextMaskOccurrenceI32 = 0
        visitScenes(scene) { nestedScene, command, topLevelCommandIndexI32 ->
            fun append(root: CapturedFilterRootV1?, mask: MaskFilterNode?) {
                if (root != null || mask != null) result += PositiveOccurrence(
                    nestedScene.filterTable, root, mask, topLevelCommandIndexI32, nextMaskOccurrenceI32,
                )
                if (mask != null) nextMaskOccurrenceI32 = Math.addExact(nextMaskOccurrenceI32, 1)
            }
            when (command) {
                is SceneCommand.Draw -> filterPayload(command.node.paint, command.node.effects).let { payload ->
                    append(payload.root, payload.mask)
                }
                is SceneCommand.BeginLayer -> filterPayload(command.descriptor.paint, command.descriptor.effects).let { payload ->
                    append(payload.root, payload.mask)
                }
                else -> Unit
            }
        }
        return result
    }

    /** Traverses captured Picture scenes once per occurrence, bounded by the root capture graph. */
    private fun visitScenes(root: SceneSnapshot, visit: (SceneSnapshot, SceneCommand, Int?) -> Unit): Boolean {
        data class Visit(
            val scene: SceneSnapshot,
            val depthI32: Int,
            val inheritedTopLevelCommandIndexI32: Int?,
            val ancestors: Set<String>,
        )
        val pending = ArrayDeque<Visit>()
        pending.addLast(Visit(root, 1, null, emptySet()))
        var visitedCommandsI32 = 0
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current.depthI32 > root.graphLimits.maxDepth) return true
            val nextAncestors = current.ancestors + current.scene.canonicalId.value
            current.scene.toList().forEachIndexed { indexI32, command ->
                visitedCommandsI32 = try { Math.addExact(visitedCommandsI32, 1) } catch (_: ArithmeticException) { return true }
                if (visitedCommandsI32 > root.graphLimits.maxNodes) return true
                val sourceIndex = current.inheritedTopLevelCommandIndexI32 ?: indexI32
                visit(current.scene, command, sourceIndex)
                val picture = (command as? SceneCommand.Draw)?.node?.geometry as? GeometryNode.Picture ?: return@forEachIndexed
                val child = picture.scene
                if (child.canonicalId.value in nextAncestors) return true
                pending.addLast(Visit(child, Math.addExact(current.depthI32, 1), sourceIndex, nextAncestors))
            }
        }
        return false
    }

    private class Ownership(
        private val roots: List<RootOccurrence>,
        private val hasMask: Boolean,
        val hasBackdrop: Boolean,
        val hasFilteredPrevious: Boolean,
        val traversalBounded: Boolean,
    ) {
        val isOwned: Boolean get() = roots.isNotEmpty() || hasMask || hasBackdrop || traversalBounded

        fun hasUnsupportedImageFamily(): Boolean = roots.any { root ->
            val pending = ArrayDeque<CapturedFilterNodeId>()
            pending.addLast(root.root.id)
            val seen = BooleanArray(root.table.nodeCount)
            while (pending.isNotEmpty()) {
                val id = pending.removeLast()
                if (id.value !in seen.indices || seen[id.value]) continue
                seen[id.value] = true
                when (val node = root.table.nodeAt(id)) {
                    is CapturedFilterNodeV1.Blur -> node.input.enqueueNodeOrUnsupported(pending)?.let { return true }
                    is CapturedFilterNodeV1.DropShadow -> node.input.enqueueNodeOrUnsupported(pending)?.let { return true }
                    else -> return true
                }
            }
            false
        }
    }

    private data class RootOccurrence(val table: CapturedFilterTableV1, val root: CapturedFilterRootV1)
    private data class FilterPayload(val root: CapturedFilterRootV1?, val mask: MaskFilterNode?)
    private data class PositiveOccurrence(
        val table: CapturedFilterTableV1,
        val root: CapturedFilterRootV1?,
        val mask: MaskFilterNode?,
        val topLevelCommandIndexI32: Int?,
        val maskOccurrenceI32: Int,
    )
    private data class TargetSpec(val id: PlanResourceId, val extent: SizeI32)

    /** Paint duplicates its captured filter in EffectStack; select it once per recorded occurrence. */
    private fun filterPayload(
        paint: org.graphiks.kanvas.render.ir.PaintNode?,
        effects: EffectStack,
    ): FilterPayload {
        val entries: Iterable<org.graphiks.kanvas.render.ir.EffectNode> = when (effects) {
            EffectStack.Empty -> emptyList()
            is EffectStack.Entries -> effects
        }
        return FilterPayload(
            paint?.imageFilter ?: entries.filterIsInstance<CapturedFilterRootV1>().singleOrNull(),
            paint?.maskFilter ?: entries.filterIsInstance<MaskFilterNode>().singleOrNull(),
        )
    }

    private fun CapturedFilterInputV1.materializeInput(
        table: CapturedFilterTableV1,
        source: SourceBinding,
        materializeNode: (CapturedFilterTableV1, CapturedFilterNodeId, SourceBinding) -> SourceBinding,
    ): SourceBinding = when (this) {
        CapturedFilterInputV1.ImplicitSource, CapturedFilterInputV1.TransparentBlack -> source
        is CapturedFilterInputV1.Node -> materializeNode(table, id, source)
        is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> throw ConstructionFailure(
            W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.UnsupportedFamily, "The captured filter input belongs to W6d."),
        )
    }

    /** Returns true for a reserved W6d input; W6b executes only captured-node or source inputs. */
    private fun CapturedFilterInputV1.enqueueNodeOrUnsupported(pending: ArrayDeque<CapturedFilterNodeId>): Boolean? = when (this) {
        CapturedFilterInputV1.ImplicitSource, CapturedFilterInputV1.TransparentBlack -> null
        is CapturedFilterInputV1.Node -> { pending.addLast(id); null }
        is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> true
    }

    private fun SourceBinding.copyDeviceBoundsI32(): RectI32 {
        val extent = copyExtentI32()
        return try {
            RectI32(
                originDeviceI32.x,
                originDeviceI32.y,
                Math.toIntExact(Math.addExact(originDeviceI32.x.toLong(), extent.width.toLong())),
                Math.toIntExact(Math.addExact(originDeviceI32.y.toLong(), extent.height.toLong())),
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds,
                "W6b target origin and extent overflow I32 device texels.",
            ))
        }
    }

    private fun RectI32.intersectWith(other: RectI32): RectI32? = copy().takeIf { it.intersect(other) }
}
