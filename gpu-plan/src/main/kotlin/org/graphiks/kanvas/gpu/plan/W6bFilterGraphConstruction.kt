package org.graphiks.kanvas.gpu.plan

import java.util.ArrayDeque
import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
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
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.expandForBlurF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.geometry.translateF64OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.vector.Vector2F64

/** W6b's single captured-filter authority; it publishes planning facts but never native work. */
internal object W6bFilterGraphConstruction {
    internal class SourceBinding(
        val resourceId: PlanResourceId,
        extent: SizeI32,
        val originDeviceI32: Point2I32,
        val mapping: LayerMappingF64,
        knownContentDeviceI32: RectI32? = null,
    ) {
        private val extentSnapshotI32 = extent.copy()
        private val knownContentSnapshotI32 = knownContentDeviceI32?.copy()

        fun copyExtentI32(): SizeI32 = extentSnapshotI32.copy()
        fun copyKnownContentDeviceI32(): RectI32? = knownContentSnapshotI32?.copy()
        fun copyDeviceBoundsI32(): RectI32 = try {
            RectI32(
                originDeviceI32.x,
                originDeviceI32.y,
                Math.toIntExact(Math.addExact(originDeviceI32.x.toLong(), extentSnapshotI32.width.toLong())),
                Math.toIntExact(Math.addExact(originDeviceI32.y.toLong(), extentSnapshotI32.height.toLong())),
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds,
                "W6b target origin and extent overflow I32 device texels.",
            ))
        }

        fun withResource(
            resourceId: PlanResourceId,
            extent: SizeI32 = extentSnapshotI32,
            originDeviceI32: Point2I32 = this.originDeviceI32,
            knownContentDeviceI32: RectI32? = knownContentSnapshotI32,
        ): SourceBinding = SourceBinding(
            resourceId,
            extent,
            Point2I32(originDeviceI32.x, originDeviceI32.y),
            mapping,
            knownContentDeviceI32,
        )
    }

    internal class ResourceSpec(
        val id: PlanResourceId,
        val role: PlanResourceRole,
        extent: SizeI32,
        private val additionalUsages: Set<PlanResourceUsage> = emptySet(),
    ) {
        private val extentSnapshotI32 = extent.copy()
        fun copyExtentI32(): SizeI32 = extentSnapshotI32.copy()
        fun seal(lastPassIndexExclusiveI32: Int): PlanResource = PlanResource.of(
            role,
            id.value.substringAfter(':').toInt(),
            PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            extentSnapshotI32,
            checkedTextureBytesI64(4, extentSnapshotI32.width, extentSnapshotI32.height, 1),
            buildSet {
                add(PlanResourceUsage.RenderAttachment)
                add(PlanResourceUsage.Sampled)
                addAll(additionalUsages)
            },
            PlanResourceLifetime.FrameLocal,
            0,
            lastPassIndexExclusiveI32,
        )
    }

    internal class PositiveOccurrence internal constructor(
        val idI32: Int,
        val table: CapturedFilterTableV1,
        val root: CapturedFilterRootV1?,
        val mask: MaskFilterNode?,
        /** Root command position where this occurrence must finish before later siblings. */
        val insertionCommandIndexI32: Int,
        /** Exact source command, including nested Picture commands. */
        val sourceSceneCanonicalId: String,
        val sourceCommandIndexI32: Int,
        val isLayerOccurrence: Boolean,
        val isPictureOccurrence: Boolean,
        val maskOccurrenceI32: Int,
    )

    internal class FreezeCursor(
        var filterTargetOrdinalI32: Int,
        var transparentBlackOrdinalI32: Int,
        var passOrdinalI32: Int,
    )

    internal class FrozenOccurrence internal constructor(
        resourceSpecs: List<ResourceSpec>,
        passes: List<PlanPass>,
        val output: SourceBinding,
        val terminalKey: FilterEvaluationKeyV1,
    ) {
        private val resourceSnapshot = immutableList(resourceSpecs)
        private val passSnapshot = immutableList(passes)
        fun resourceSpecs(): List<ResourceSpec> = resourceSnapshot
        fun passes(): List<PlanPass> = passSnapshot
    }

    internal class ConstructionFailure(val diagnostic: RenderDiagnostic) : IllegalArgumentException(diagnostic.message)

    internal fun owns(scene: SceneSnapshot): Boolean = ownership(scene).isOwned

    /** Unsupported W6c/W6d/backdrop/filtered-previous cases stop before source allocation. */
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

    /** Sealed occurrence discovery is shared by W6a event ordering and W6b graph freezing. */
    internal fun positiveOccurrences(scene: SceneSnapshot): List<PositiveOccurrence> {
        admissionRefusalOrNull(scene)?.let { throw ConstructionFailure(it) }
        val result = mutableListOf<PositiveOccurrence>()
        var nextOccurrenceI32 = 0
        var nextMaskOccurrenceI32 = 0
        visitScenes(scene) { nestedScene, command, insertionIndexI32, commandIndexI32, nested ->
            fun append(root: CapturedFilterRootV1?, mask: MaskFilterNode?, layer: Boolean, picture: Boolean) {
                if (root == null && mask == null) return
                result += PositiveOccurrence(
                    nextOccurrenceI32++, nestedScene.filterTable, root, mask, insertionIndexI32,
                    nestedScene.canonicalId.value, commandIndexI32, layer, picture || nested, nextMaskOccurrenceI32,
                )
                if (mask != null) nextMaskOccurrenceI32 = Math.addExact(nextMaskOccurrenceI32, 1)
            }
            when (command) {
                is SceneCommand.Draw -> filterPayload(command.node.paint, command.node.effects).let { payload ->
                    append(payload.root, payload.mask, false, command.node.geometry is GeometryNode.Picture)
                }
                is SceneCommand.BeginLayer -> filterPayload(command.descriptor.paint, command.descriptor.effects).let { payload ->
                    append(payload.root, payload.mask, true, false)
                }
                else -> Unit
            }
        }
        return immutableList(result)
    }

    /** Freezes one occurrence from its exact immutable source generation. */
    internal fun freezeOccurrence(
        occurrence: PositiveOccurrence,
        occurrenceSource: SourceBinding,
        cursor: FreezeCursor,
    ): FrozenOccurrence {
        val resources = mutableListOf<ResourceSpec>()
        val passes = mutableListOf<PlanPass>()
        fun append(pass: PlanPass) {
            require(pass.ordinal == cursor.passOrdinalI32)
            cursor.passOrdinalI32 = Math.addExact(cursor.passOrdinalI32, 1)
            passes += pass
        }
        fun resource(role: PlanResourceRole, extent: SizeI32): PlanResourceId {
            val ordinal = when (role) {
                PlanResourceRole.FilterTarget -> cursor.filterTargetOrdinalI32.also {
                    cursor.filterTargetOrdinalI32 = Math.addExact(it, 1)
                }
                PlanResourceRole.FilterTransparentBlack -> cursor.transparentBlackOrdinalI32.also {
                    cursor.transparentBlackOrdinalI32 = Math.addExact(it, 1)
                }
                else -> error("W6b only allocates typed filter resources.")
            }
            val id = planResourceId(role, ordinal)
            resources += ResourceSpec(id, role, extent)
            return id
        }
        fun bindOutput(id: PlanResourceId, bounds: FilterBoundsPlanV1): SourceBinding {
            val desired = bounds.copyDesiredOutputDeviceI32()
            return occurrenceSource.withResource(
                id, SizeI32(desired.width(), desired.height()), bounds.copyTargetOriginDeviceI32(),
                bounds.copyProducedOutputDeviceI32(),
            )
        }
        fun allocateTarget(bounds: FilterBoundsPlanV1): SourceBinding = bindOutput(
            resource(PlanResourceRole.FilterTarget, bounds.copyDesiredOutputDeviceI32().let { SizeI32(it.width(), it.height()) }), bounds,
        )
        fun transparentBlack(source: SourceBinding): SourceBinding {
            val id = resource(PlanResourceRole.FilterTransparentBlack, source.copyExtentI32())
            append(PlanPass.FilterSourceClear(cursor.passOrdinalI32, id, source.resourceId))
            return source.withResource(id, knownContentDeviceI32 = null)
        }
        fun keyFor(nodeId: CapturedFilterNodeId?, maskOccurrenceI32: Int?, desired: RectI32): FilterEvaluationKeyV1 = when {
            nodeId != null -> FilterEvaluationKeyV1.of(nodeId, occurrenceSource.resourceId, occurrenceSource.mapping, desired)
            maskOccurrenceI32 != null -> FilterEvaluationKeyV1.forMaskOccurrence(
                maskOccurrenceI32, occurrenceSource.resourceId, occurrenceSource.mapping, desired,
            )
            else -> error("W6b occurrence key is missing its captured identity.")
        }
        fun appendBlur(
            source: SourceBinding,
            sigmaXF32: Float,
            sigmaYF32: Float,
            tileMode: TileMode,
            horizontalKind: FilterImplementationKindV1,
            verticalKind: FilterImplementationKindV1,
            key: FilterEvaluationKeyV1,
        ): SourceBinding {
            val horizontalBounds = blurBounds(source, sigmaXF32, 0f)
            val horizontal = allocateTarget(horizontalBounds)
            append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(source.resourceId), horizontal.resourceId, key,
                FilterPassOperationV1.SeparableBlur(horizontalKind, sigmaXF32, FilterAxisV1.X, tileMode, horizontalBounds)))
            val verticalBounds = blurBounds(horizontal, 0f, sigmaYF32)
            val vertical = allocateTarget(verticalBounds)
            append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(horizontal.resourceId), vertical.resourceId, key,
                FilterPassOperationV1.SeparableBlur(verticalKind, sigmaYF32, FilterAxisV1.Y, tileMode, verticalBounds)))
            return vertical
        }
        lateinit var materializeNode: (CapturedFilterNodeId) -> Pair<SourceBinding, FilterEvaluationKeyV1>
        fun materializeInput(input: CapturedFilterInputV1): SourceBinding = when (input) {
            CapturedFilterInputV1.ImplicitSource -> occurrenceSource
            CapturedFilterInputV1.TransparentBlack -> transparentBlack(occurrenceSource)
            is CapturedFilterInputV1.Node -> materializeNode(input.id).first
            is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> throw ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.UnsupportedFamily, "The captured filter input belongs to W6d."),
            )
        }
        materializeNode = { id -> when (val node = occurrence.table.nodeAt(id)) {
            is CapturedFilterNodeV1.Blur -> {
                val input = materializeInput(node.input)
                val full = blurBounds(input, node.sigmaX, node.sigmaY)
                val key = keyFor(id, null, full.copyDesiredOutputDeviceI32())
                appendBlur(input, node.sigmaX, node.sigmaY, node.tileMode,
                    FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, key) to key
            }
            is CapturedFilterNodeV1.DropShadow -> {
                val input = materializeInput(node.input)
                val blurredBounds = blurBounds(input, node.sigmaX, node.sigmaY)
                val predictedBlur = SourceBinding(occurrenceSource.resourceId,
                    SizeI32(blurredBounds.copyDesiredOutputDeviceI32().width(), blurredBounds.copyDesiredOutputDeviceI32().height()),
                    blurredBounds.copyTargetOriginDeviceI32(), occurrenceSource.mapping, blurredBounds.copyProducedOutputDeviceI32())
                val predictedShadowBounds = translatedBounds(predictedBlur, node.dx.toDouble(), node.dy.toDouble())
                val predictedShadow = predictedBlur.withResource(predictedBlur.resourceId,
                    SizeI32(predictedShadowBounds.copyDesiredOutputDeviceI32().width(), predictedShadowBounds.copyDesiredOutputDeviceI32().height()),
                    predictedShadowBounds.copyTargetOriginDeviceI32(), predictedShadowBounds.copyProducedOutputDeviceI32())
                val key = keyFor(id, null, dropShadowCompositeBounds(input, predictedShadow, node.mode).copyDesiredOutputDeviceI32())
                val blurred = appendBlur(input, node.sigmaX, node.sigmaY, TileMode.CLAMP,
                    FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, key)
                val colorBounds = translatedBounds(blurred, node.dx.toDouble(), node.dy.toDouble())
                val colorized = allocateTarget(colorBounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(blurred.resourceId), colorized.resourceId, key,
                    FilterPassOperationV1.DropShadowColorize(node.color, Vector2F64(node.dx.toDouble(), node.dy.toDouble()), colorBounds)))
                val compositeBounds = dropShadowCompositeBounds(input, colorized, node.mode)
                val composite = allocateTarget(compositeBounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(colorized.resourceId, input.resourceId), composite.resourceId, key,
                    FilterPassOperationV1.DropShadowComposite(node.mode, compositeBounds)))
                composite to key
            }
            else -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.UnsupportedFamily, "The captured image-filter family belongs to W6c or W6d.",
            ))
        } }
        fun materializeMask(mask: MaskFilterNode, input: SourceBinding): Pair<SourceBinding, FilterEvaluationKeyV1> = when (mask) {
            is MaskFilterNode.Blur -> {
                val full = blurBounds(input, mask.sigma, mask.sigma)
                val key = keyFor(null, occurrence.maskOccurrenceI32, full.copyDesiredOutputDeviceI32())
                val blurred = appendBlur(input, mask.sigma, mask.sigma, TileMode.CLAMP,
                    FilterImplementationKindV1.MASK_COVERAGE_BLUR_X, FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y, key)
                val styleBounds = identityBounds(blurred)
                val styled = allocateTarget(styleBounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(blurred.resourceId), styled.resourceId, key,
                    FilterPassOperationV1.MaskBlurStyle(mask.style, styleBounds)))
                styled to key
            }
            is MaskFilterNode.Shader -> {
                val bounds = identityBounds(input)
                val key = keyFor(null, occurrence.maskOccurrenceI32, bounds.copyDesiredOutputDeviceI32())
                val target = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), target.resourceId, key,
                    FilterPassOperationV1.MaskShader(
                        FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedDeferred(mask.material.canonicalId.value), bounds)))
                target to key
            }
            is MaskFilterNode.Table -> {
                val bounds = identityBounds(input)
                val key = keyFor(null, occurrence.maskOccurrenceI32, bounds.copyDesiredOutputDeviceI32())
                val target = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), target.resourceId, key,
                    FilterPassOperationV1.MaskTable(mask.table, bounds)))
                target to key
            }
        }

        val image = occurrence.root?.let { materializeNode(it.id) }
        val terminal = occurrence.mask?.let { materializeMask(it, image?.first ?: occurrenceSource) } ?: image
            ?: error("W6b positive occurrence has no captured filter payload.")
        return FrozenOccurrence(resources, passes, terminal.first, terminal.second)
    }

    private fun identityBounds(source: SourceBinding): FilterBoundsPlanV1 {
        val domain = source.copyDeviceBoundsI32()
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), domain, domain, source.copyKnownContentDeviceI32(), source.originDeviceI32)
    }

    /** The output domain grows by blur support; it is never intersected back to the source. */
    private fun blurBounds(source: SourceBinding, sigmaXF32: Float, sigmaYF32: Float): FilterBoundsPlanV1 {
        val input = source.copyDeviceBoundsI32()
        val desired = RectF64(input.left.toDouble(), input.top.toDouble(), input.right.toDouble(), input.bottom.toDouble())
            .expandForBlurF64OrNull(sigmaXF32, sigmaYF32)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6b blur expansion cannot be represented in checked I32 texels."))
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), desired, input,
            source.copyKnownContentDeviceI32()?.let { desired.copy() }, Point2I32(desired.left, desired.top))
    }

    private fun translatedBounds(source: SourceBinding, dxF64: Double, dyF64: Double): FilterBoundsPlanV1 {
        val input = source.copyDeviceBoundsI32()
        fun translate(bounds: RectI32): RectI32 = RectF64(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
            .translateF64OrNull(dxF64, dyF64)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6b drop-shadow offset cannot be represented in checked I32 texels."))
        val desired = translate(input)
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32()?.let(::translate), desired, input,
            source.copyKnownContentDeviceI32()?.let(::translate), Point2I32(desired.left, desired.top))
    }

    private fun dropShadowCompositeBounds(input: SourceBinding, shadow: SourceBinding, mode: CapturedDropShadowModeV1): FilterBoundsPlanV1 {
        val inputDomain = input.copyDeviceBoundsI32()
        val shadowDomain = shadow.copyDeviceBoundsI32()
        val desired = if (mode == CapturedDropShadowModeV1.COMPOSITE) union(inputDomain, shadowDomain) else shadowDomain
        val known = if (mode == CapturedDropShadowModeV1.COMPOSITE)
            unionOrNull(input.copyKnownContentDeviceI32(), shadow.copyKnownContentDeviceI32()) else shadow.copyKnownContentDeviceI32()
        return FilterBoundsPlanV1(known, desired, union(inputDomain, shadowDomain), known, Point2I32(desired.left, desired.top))
    }

    private fun ownership(scene: SceneSnapshot): Ownership {
        val roots = mutableListOf<RootOccurrence>()
        var mask = false
        var backdrop = false
        var filteredPrevious = false
        val bounded = visitScenes(scene) { nestedScene, command, _, _, _ -> when (command) {
            is SceneCommand.Draw -> filterPayload(command.node.paint, command.node.effects).let { payload ->
                payload.root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                mask = mask || payload.mask != null
            }
            is SceneCommand.BeginLayer -> filterPayload(command.descriptor.paint, command.descriptor.effects).let { payload ->
                payload.root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                mask = mask || payload.mask != null
                if (command.descriptor.backdrop !is EffectStack.Empty) {
                    backdrop = true
                    filterPayload(null, command.descriptor.backdrop).root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                }
                filteredPrevious = filteredPrevious || command.descriptor.initWithPrevious && (payload.root != null || payload.mask != null)
            }
            else -> Unit
        } }
        return Ownership(roots, mask, backdrop, filteredPrevious, bounded)
    }

    /** Bounded iterative traversal refuses repeated ancestral Picture scenes. */
    private fun visitScenes(root: SceneSnapshot, visit: (SceneSnapshot, SceneCommand, Int, Int, Boolean) -> Unit): Boolean {
        data class Visit(val scene: SceneSnapshot, val depthI32: Int, val insertionCommandIndexI32: Int?, val ancestors: Set<String>)
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
                val insertion = current.insertionCommandIndexI32 ?: indexI32
                visit(current.scene, command, insertion, indexI32, current.insertionCommandIndexI32 != null)
                val picture = (command as? SceneCommand.Draw)?.node?.geometry as? GeometryNode.Picture ?: return@forEachIndexed
                if (picture.scene.canonicalId.value in nextAncestors) return true
                pending.addLast(Visit(picture.scene, Math.addExact(current.depthI32, 1), insertion, nextAncestors))
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

    private fun filterPayload(paint: org.graphiks.kanvas.render.ir.PaintNode?, effects: EffectStack): FilterPayload {
        val entries: Iterable<org.graphiks.kanvas.render.ir.EffectNode> = when (effects) {
            EffectStack.Empty -> emptyList()
            is EffectStack.Entries -> effects
        }
        return FilterPayload(
            paint?.imageFilter ?: entries.filterIsInstance<CapturedFilterRootV1>().singleOrNull(),
            paint?.maskFilter ?: entries.filterIsInstance<MaskFilterNode>().singleOrNull(),
        )
    }

    private fun CapturedFilterInputV1.enqueueNodeOrUnsupported(pending: ArrayDeque<CapturedFilterNodeId>): Boolean? = when (this) {
        CapturedFilterInputV1.ImplicitSource, CapturedFilterInputV1.TransparentBlack -> null
        is CapturedFilterInputV1.Node -> { pending.addLast(id); null }
        is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> true
    }

    private fun union(first: RectI32, second: RectI32): RectI32 = RectI32(
        minOf(first.left, second.left), minOf(first.top, second.top), maxOf(first.right, second.right), maxOf(first.bottom, second.bottom),
    )
    private fun unionOrNull(first: RectI32?, second: RectI32?): RectI32? = when {
        first == null -> second?.copy()
        second == null -> first.copy()
        else -> union(first, second)
    }
}
