package org.graphiks.kanvas.gpu.plan

import java.util.ArrayDeque
import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.kanvas.render.ir.CapturedFilterInputV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32
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
import org.graphiks.math.geometry.expandSamplingHaloF64OrNull
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
        desiredOutputDeviceI32: RectI32? = null,
        requiredInputDeviceI32: RectI32? = null,
        producedOutputDeviceI32: RectI32? = null,
        /** Exact immutable SceneSnapshot revision for cache admission, if known. */
        val sourceRevisionIdentity: String? = null,
    ) {
        private val extentSnapshotI32 = extent.copy()
        private val knownContentSnapshotI32 = knownContentDeviceI32?.copy()
        private val desiredOutputSnapshotI32 = desiredOutputDeviceI32?.copy()
        private val requiredInputSnapshotI32 = requiredInputDeviceI32?.copy()
        private val producedOutputSnapshotI32 = producedOutputDeviceI32?.copy()

        fun copyExtentI32(): SizeI32 = extentSnapshotI32.copy()
        fun copyKnownContentDeviceI32(): RectI32? = knownContentSnapshotI32?.copy()
        fun copyDesiredOutputDeviceI32(): RectI32? = desiredOutputSnapshotI32?.copy()
        fun copyRequiredInputDeviceI32(): RectI32? = requiredInputSnapshotI32?.copy()
        fun copyProducedOutputDeviceI32(): RectI32? = producedOutputSnapshotI32?.copy()
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
            desiredOutputDeviceI32: RectI32? = desiredOutputSnapshotI32,
            requiredInputDeviceI32: RectI32? = requiredInputSnapshotI32,
            producedOutputDeviceI32: RectI32? = producedOutputSnapshotI32,
        ): SourceBinding = SourceBinding(
            resourceId,
            extent,
            Point2I32(originDeviceI32.x, originDeviceI32.y),
            mapping,
            knownContentDeviceI32,
            desiredOutputDeviceI32,
            requiredInputDeviceI32,
            producedOutputDeviceI32,
            sourceRevisionIdentity,
        )

        fun withSourceRevision(identity: String): SourceBinding {
            require(identity.isNotBlank())
            return SourceBinding(resourceId, extentSnapshotI32, Point2I32(originDeviceI32.x, originDeviceI32.y), mapping,
                knownContentSnapshotI32, desiredOutputSnapshotI32, requiredInputSnapshotI32, producedOutputSnapshotI32, identity)
        }

        /** Seals a source-to-output local sampling transform before native lowering. */
        fun samplingFor(output: SourceBinding): FilterInputSamplingV1 {
            val known = copyKnownContentDeviceI32() ?: copyDeviceBoundsI32()
            val knownLocal = try {
                RectI32(
                    Math.subtractExact(known.left, originDeviceI32.x),
                    Math.subtractExact(known.top, originDeviceI32.y),
                    Math.subtractExact(known.right, originDeviceI32.x),
                    Math.subtractExact(known.bottom, originDeviceI32.y),
                )
            } catch (_: ArithmeticException) {
                throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                    "W6b source known-content cannot be represented in target-local I32 texels."))
            }
            val offset = try {
                Point2I32(
                    Math.subtractExact(output.originDeviceI32.x, originDeviceI32.x),
                    Math.subtractExact(output.originDeviceI32.y, originDeviceI32.y),
                )
            } catch (_: ArithmeticException) {
                throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                    "W6b source sampling offset cannot be represented in target-local I32 texels."))
            }
            return FilterInputSamplingV1(offset, knownLocal)
        }
    }

    internal class ResourceSpec(
        val id: PlanResourceId,
        val role: PlanResourceRole,
        extent: SizeI32? = null,
        private val additionalUsages: Set<PlanResourceUsage> = emptySet(),
        private val bufferByteSizeI64: Long? = null,
    ) {
        private val extentSnapshotI32 = extent?.copy()

        init {
            require((extentSnapshotI32 != null) != (bufferByteSizeI64 != null))
            if (bufferByteSizeI64 != null) {
                require(role == PlanResourceRole.MaskTableData && bufferByteSizeI64 == 256L &&
                    additionalUsages.isEmpty())
            }
        }

        fun copyExtentI32(): SizeI32 = requireNotNull(extentSnapshotI32).copy()
        fun seal(lastPassIndexExclusiveI32: Int): PlanResource = if (bufferByteSizeI64 != null) {
            PlanResource.of(
                role,
                id.value.substringAfter(':').toInt(),
                PlanResourceKind.Buffer,
                null,
                null,
                bufferByteSizeI64,
                setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination),
                PlanResourceLifetime.FrameLocal,
                0,
                lastPassIndexExclusiveI32,
            )
        } else {
            val textureExtent = requireNotNull(extentSnapshotI32)
            PlanResource.of(
                role,
                id.value.substringAfter(':').toInt(),
                PlanResourceKind.Texture2D,
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                textureExtent,
                checkedTextureBytesI64(4, textureExtent.width, textureExtent.height, 1),
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

        companion object {
            fun maskTable(id: PlanResourceId): ResourceSpec = ResourceSpec(
                id = id,
                role = PlanResourceRole.MaskTableData,
                bufferByteSizeI64 = 256L,
            )
        }
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
        picturePathI32: List<Int>,
        val source: FilterOccurrenceSourceV1,
    ) {
        private val picturePathSnapshot = immutableList(picturePathI32)
        fun outerPicturePathI32(): List<Int> = picturePathSnapshot
    }

    internal class FreezeCursor(
        var filterTargetOrdinalI32: Int,
        var transparentBlackOrdinalI32: Int,
        var coverageOriginalOrdinalI32: Int,
        var maskTableOrdinalI32: Int,
        var passOrdinalI32: Int,
    )

    internal class FrozenMask internal constructor(
        resourceSpecs: List<ResourceSpec>,
        passes: List<PlanPass>,
        val output: SourceBinding,
    ) {
        private val resourceSnapshot = immutableList(resourceSpecs)
        private val passSnapshot = immutableList(passes)
        fun resourceSpecs(): List<ResourceSpec> = resourceSnapshot
        fun passes(): List<PlanPass> = passSnapshot
    }

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

    /**
     * Contextual evaluation never returns a resource without the bounds that produced it.  The
     * binding remains the sole carrier of target-local origin and known-content facts.
     */
    private class ContextualFilterResult(
        val source: SourceBinding,
        val bounds: FilterBoundsPlanV1,
        val evaluationKey: FilterEvaluationKeyV1?,
    ) {
        val resourceId: PlanResourceId get() = source.resourceId
    }

    internal fun owns(scene: SceneSnapshot): Boolean = ownership(scene).isOwned

    /** True when the terminal side of a Compose chain is this slice's unbounded distant light. */
    internal fun hasDistantDiffuseTerminal(occurrence: PositiveOccurrence): Boolean {
        lateinit var nodeHasDistant: (CapturedFilterNodeIdI32) -> Boolean
        fun inputHasDistant(input: CapturedFilterInputV1): Boolean = when (input) {
            is CapturedFilterInputV1.Node -> nodeHasDistant(input.id)
            else -> false
        }
        nodeHasDistant = { id -> when (val node = occurrence.table.nodeAt(id)) {
            is CapturedFilterNodeV1.DistantLitDiffuse -> true
            is CapturedFilterNodeV1.Compose -> inputHasDistant(node.outer)
            else -> false
        } }
        return occurrence.root?.let { nodeHasDistant(it.id) } == true
    }

    /** Unsupported W6c/W6d/backdrop/filtered-previous cases stop before source allocation. */
    internal fun admissionRefusalOrNull(scene: SceneSnapshot): RenderDiagnostic? {
        val ownership = ownership(scene)
        if (!ownership.isOwned) return null
        if (ownership.traversalBounded) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.InvalidBounds,
            "W6b picture traversal exceeded its sealed capture bound.",
        )
        ownership.invalidMaskTableLengthI32?.let { lengthI32 -> return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.InvalidMaskTableLength,
            "MaskFilter.Table requires exactly 256 entries; captured $lengthI32.",
        ) }
        if (ownership.hasBackdrop) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.UnsupportedBackdrop,
            "W6b does not admit backdrop filters.",
        )
        if (ownership.hasFilteredPrevious) return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.FilteredPrevious,
            "W6b does not admit initWithPrevious combined with a spatial filter.",
        )
        ownership.unsupportedImageFamilyOwnerOrNull()?.let { owner -> return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.UnsupportedFamily,
            "The captured image-filter family belongs to $owner.",
        ) }
        return null
    }

    /**
     * Checks the positional Merge ABI against the immutable device facts before child lanes,
     * targets, or a RenderGraph can be published.  Native lowering has one sampled-texture and
     * one bind-group binding per retained input position.
     */
    internal fun nativeCapabilityRefusalOrNull(
        scene: SceneSnapshot,
        capabilities: PlanCapabilitySnapshot,
    ): RenderDiagnostic? {
        val ownership = ownership(scene)
        if (!ownership.isOwned) return null
        val inputCount = ownership.firstMergeInputCountExceeding(
            capabilities.maxSampledTexturesPerShaderStageI32,
            capabilities.maxBindingsPerBindGroupI32,
        ) ?: return null
        val sampledLimit = capabilities.maxSampledTexturesPerShaderStageI32?.toString() ?: "unknown"
        val bindingLimit = capabilities.maxBindingsPerBindGroupI32?.toString() ?: "unknown"
        return W6bFilterDiagnostics.refusal(
            W6bFilterDiagnostics.NativeCapability,
            "W6c Merge requires $inputCount sampled textures and bindings; " +
                "device limits are sampledTextures=$sampledLimit, bindings=$bindingLimit.",
        )
    }

    /** Sealed occurrence discovery is shared by W6a event ordering and W6b graph freezing. */
    internal fun positiveOccurrences(scene: SceneSnapshot): List<PositiveOccurrence> {
        admissionRefusalOrNull(scene)?.let { throw ConstructionFailure(it) }
        val result = mutableListOf<PositiveOccurrence>()
        var nextOccurrenceI32 = 0
        var nextMaskOccurrenceI32 = 0
        visitScenes(scene) { nestedScene, command, insertionIndexI32, commandIndexI32, nested, outerPictures, picturePathI32 ->
            fun append(root: CapturedFilterRootV1?, mask: MaskFilterNode?, layer: Boolean, picture: Boolean) {
                if (root == null && mask == null) return
                result += PositiveOccurrence(
                    nextOccurrenceI32++, nestedScene.filterTable, root, mask, insertionIndexI32,
                    nestedScene.canonicalId.value, commandIndexI32, layer, picture || nested, nextMaskOccurrenceI32,
                    picturePathI32,
                    FilterOccurrenceSourceV1(nestedScene, commandIndexI32,
                        (command as? SceneCommand.Draw)?.node, outerPictures,
                        (command as? SceneCommand.BeginLayer)?.descriptor, picturePathI32),
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
    /** Freezes only the post-material captured image-filter chain. */
    internal fun freezeImageOccurrence(
        occurrence: PositiveOccurrence,
        sourceBinding: SourceBinding,
        cursor: FreezeCursor,
    ): FrozenOccurrence {
        // The source scene's canonical id is an immutable content revision.  It is deliberately
        // captured here, where the occurrence still owns the SceneSnapshot, and carried through
        // every derived binding; later plan/native stages cannot rediscover it.
        val occurrenceSource = sourceBinding.withSourceRevision(
            "${occurrence.source.scene.canonicalId.value}:${occurrence.source.sourceCommandIndexI32}:" +
                occurrence.source.picturePathI32().joinToString(","),
        )
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
                PlanResourceRole.CoverageOriginal -> cursor.coverageOriginalOrdinalI32.also {
                    cursor.coverageOriginalOrdinalI32 = Math.addExact(it, 1)
                }
                else -> error("W6b only allocates typed filter resources.")
            }
            val id = planResourceId(role, ordinal)
            resources += ResourceSpec(id, role, extent)
            return id
        }
        fun maskTableResource(): PlanResourceId {
            val ordinal = cursor.maskTableOrdinalI32.also {
                cursor.maskTableOrdinalI32 = Math.addExact(it, 1)
            }
            return planResourceId(PlanResourceRole.MaskTableData, ordinal).also { id ->
                resources += ResourceSpec.maskTable(id)
            }
        }
        fun bindOutput(id: PlanResourceId, bounds: FilterBoundsPlanV1): SourceBinding {
            val desired = bounds.copyDesiredOutputDeviceI32()
            return occurrenceSource.withResource(
                id, SizeI32(desired.width(), desired.height()), bounds.copyTargetOriginDeviceI32(),
                bounds.copyProducedOutputDeviceI32(), bounds.copyDesiredOutputDeviceI32(),
                bounds.copyRequiredInputDeviceI32(), bounds.copyProducedOutputDeviceI32(),
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
        fun keyFor(nodeId: CapturedFilterNodeIdI32?, maskOccurrenceI32: Int?, boundSource: SourceBinding, desired: RectI32): FilterEvaluationKeyV1 = when {
            nodeId != null -> FilterEvaluationKeyV1.of(nodeId, boundSource.resourceId, boundSource.mapping, desired,
                sourceRevisionIdentity = boundSource.sourceRevisionIdentity)
            maskOccurrenceI32 != null -> FilterEvaluationKeyV1.forMaskOccurrence(
                maskOccurrenceI32, boundSource.resourceId, boundSource.mapping, desired,
                sourceRevisionIdentity = boundSource.sourceRevisionIdentity,
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
        ): ContextualFilterResult {
            val horizontalBounds = blurBounds(source, sigmaXF32, 0f)
            val horizontal = allocateTarget(horizontalBounds)
            append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(source.resourceId), horizontal.resourceId, key,
                FilterPassOperationV1.SeparableBlur(horizontalKind, sigmaXF32, FilterAxisV1.X, tileMode,
                    horizontalBounds, filterInputSampling(source, horizontalBounds))))
            val verticalBounds = blurBounds(horizontal, 0f, sigmaYF32)
            val vertical = allocateTarget(verticalBounds)
            append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(horizontal.resourceId), vertical.resourceId, key,
                FilterPassOperationV1.SeparableBlur(verticalKind, sigmaYF32, FilterAxisV1.Y, tileMode,
                    verticalBounds, filterInputSampling(horizontal, verticalBounds))))
            return ContextualFilterResult(vertical, verticalBounds, key)
        }
        fun appendMorphology(
            source: SourceBinding,
            morphologyKind: FilterPassOperationV1.Morphology.Kind,
            radiusXF64: Double,
            radiusYF64: Double,
            key: FilterEvaluationKeyV1,
        ): ContextualFilterResult {
            val radii = W6cMorphologyPlanner.deviceRadii(radiusXF64, radiusYF64, source.mapping)
            fun appendAxis(input: SourceBinding, axis: FilterAxisV1): SourceBinding {
                val radius = if (axis == FilterAxisV1.X) radii.radiusXF64 else radii.radiusYF64
                val bounds = W6cMorphologyPlanner.bounds(input, morphologyKind, axis, radius)
                val output = allocateTarget(bounds)
                val kind = if (axis == FilterAxisV1.X) FilterImplementationKindV1.MORPHOLOGY_X else FilterImplementationKindV1.MORPHOLOGY_Y
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Morphology(morphologyKind, radii.radiusXF64, radii.radiusYF64,
                        radii.radiusXTexelsI32, radii.radiusYTexelsI32, axis, bounds,
                        filterInputSampling(input, bounds), kind)))
                return output
            }
            val horizontal = appendAxis(source, FilterAxisV1.X)
            val vertical = appendAxis(horizontal, FilterAxisV1.Y)
            return ContextualFilterResult(vertical, W6cMorphologyPlanner.bounds(horizontal, morphologyKind,
                FilterAxisV1.Y, radii.radiusYF64), key)
        }
        lateinit var materializeNode: (CapturedFilterNodeIdI32, SourceBinding) -> ContextualFilterResult
        fun bindInput(input: CapturedFilterInputV1, currentSource: SourceBinding): ContextualFilterResult = when (input) {
            CapturedFilterInputV1.ImplicitSource -> ContextualFilterResult(currentSource, identityBounds(currentSource), null)
            CapturedFilterInputV1.TransparentBlack -> transparentBlack(currentSource).let { transparent ->
                ContextualFilterResult(transparent, identityBounds(transparent), null)
            }
            is CapturedFilterInputV1.Node -> materializeNode(input.id, currentSource)
            is CapturedFilterInputV1.Picture, is CapturedFilterInputV1.Backdrop -> throw ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.UnsupportedFamily, "The captured filter input belongs to W6d."),
            )
        }
        fun materializeInput(input: CapturedFilterInputV1, currentSource: SourceBinding): SourceBinding =
            bindInput(input, currentSource).source
        lateinit var nodeHasDistantTerminal: (CapturedFilterNodeIdI32) -> Boolean
        fun inputHasDistantTerminal(input: CapturedFilterInputV1): Boolean = when (input) {
            is CapturedFilterInputV1.Node -> nodeHasDistantTerminal(input.id)
            else -> false
        }
        nodeHasDistantTerminal = { id -> when (val node = occurrence.table.nodeAt(id)) {
            is CapturedFilterNodeV1.DistantLitDiffuse -> true
            is CapturedFilterNodeV1.Compose -> inputHasDistantTerminal(node.outer)
            else -> false
        } }
        materializeNode = { id, currentSource -> when (val node = occurrence.table.nodeAt(id)) {
            is CapturedFilterNodeV1.Crop -> {
                val input = materializeInput(node.input, currentSource)
                val planned = W6cSpatialBoundsPlanner.crop(input, node, id == occurrence.root?.id)
                val bounds = planned.bounds
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Crop(planned.cropInputTargetLocalI32, node.tileMode, bounds,
                        spatialSampling(input, bounds, planned.clipOutputTargetLocalF64, 0.0, 0.0))))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Offset -> {
                val input = materializeInput(node.input, currentSource)
                val planned = W6cSpatialBoundsPlanner.offset(input, node)
                val bounds = planned.bounds
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Offset(Vector2F64(planned.offsetDeviceF64X, planned.offsetDeviceF64Y), bounds,
                        spatialSampling(input, bounds, fullClip(bounds), -planned.offsetDeviceF64X, -planned.offsetDeviceF64Y))))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Tile -> {
                val input = materializeInput(node.input, currentSource)
                val planned = W6cSpatialBoundsPlanner.tile(input, node, id == occurrence.root?.id)
                val bounds = planned.bounds
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Tile(planned.sourceInputTargetLocalI32, bounds,
                        spatialSampling(input, bounds, planned.clipOutputTargetLocalF64, 0.0, 0.0))))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Blur -> {
                val input = materializeInput(node.input, currentSource)
                val full = blurBounds(input, node.sigmaX, node.sigmaY)
                val key = keyFor(id, null, currentSource, full.copyDesiredOutputDeviceI32())
                appendBlur(input, node.sigmaX, node.sigmaY, node.tileMode,
                    FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, key)
            }
            is CapturedFilterNodeV1.DropShadow -> {
                val input = materializeInput(node.input, currentSource)
                val blurredBounds = blurBounds(input, node.sigmaX, node.sigmaY)
                val predictedBlur = SourceBinding(occurrenceSource.resourceId,
                    SizeI32(blurredBounds.copyDesiredOutputDeviceI32().width(), blurredBounds.copyDesiredOutputDeviceI32().height()),
                    blurredBounds.copyTargetOriginDeviceI32(), occurrenceSource.mapping, blurredBounds.copyProducedOutputDeviceI32())
                val predictedShadowBounds = translatedBounds(predictedBlur, node.dx.toDouble(), node.dy.toDouble())
                val predictedShadow = predictedBlur.withResource(predictedBlur.resourceId,
                    SizeI32(predictedShadowBounds.copyDesiredOutputDeviceI32().width(), predictedShadowBounds.copyDesiredOutputDeviceI32().height()),
                    predictedShadowBounds.copyTargetOriginDeviceI32(), predictedShadowBounds.copyProducedOutputDeviceI32())
                val key = keyFor(id, null, currentSource, dropShadowCompositeBounds(input, predictedShadow, node.mode).copyDesiredOutputDeviceI32())
                // DropShadow does not expose a public tile mode.  Skia defines its internal
                // Blur through the overload whose default is transparent DECAL sampling.
                val blurred = appendBlur(input, node.sigmaX, node.sigmaY, TileMode.DECAL,
                    FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, key)
                val colorBounds = translatedBounds(blurred.source, node.dx.toDouble(), node.dy.toDouble())
                val colorized = allocateTarget(colorBounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(blurred.resourceId), colorized.resourceId, key,
                    FilterPassOperationV1.DropShadowColorize(node.color, Vector2F64(node.dx.toDouble(), node.dy.toDouble()), colorBounds,
                        dropShadowLinearSampling(blurred.source, colorBounds, node.dx.toDouble(), node.dy.toDouble()))))
                if (node.mode == CapturedDropShadowModeV1.SHADOW_ONLY) {
                    // The colored target is the terminal: there is no identity composite, target,
                    // slot, or lifetime to charge in SHADOW_ONLY.
                    ContextualFilterResult(colorized, colorBounds, key)
                } else {
                    val compositeBounds = dropShadowCompositeBounds(input, colorized, node.mode)
                    val composite = allocateTarget(compositeBounds)
                    append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(colorized.resourceId, input.resourceId), composite.resourceId, key,
                        FilterPassOperationV1.DropShadowComposite(node.mode, input.resourceId, compositeBounds,
                            targetLocalSampleOffset(colorized, compositeBounds), targetLocalSampleOffset(input, compositeBounds))))
                    ContextualFilterResult(composite, compositeBounds, key)
                }
            }
            is CapturedFilterNodeV1.ColorFilter -> {
                val input = materializeInput(node.input, currentSource)
                val execution = when (val compiled = ColorFilterPlanCompilerV1.compile(node.filter)) {
                    is ColorFilterCompileResultV1.Ready -> compiled.execution
                    is ColorFilterCompileResultV1.Refused -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                        compiled.diagnosticCode, "W6c ColorFilter could not reuse the W5f numeric graph."))
                }
                val bounds = identityBounds(input)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.ColorFilter(execution, null, null, null, bounds, filterInputSampling(input, bounds))))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Compose -> {
                // Skia Compose binds inner to the current source, then binds outer to inner's
                // concrete result. This pair keeps result and bounds inseparable through recursion.
                val inner = bindInput(node.inner, currentSource)
                // An outer distant light consumes this Compose's terminal demand, not the
                // bounded concrete target just produced by its inner child (for example Crop).
                val outerSource = if (inputHasDistantTerminal(node.outer)) inner.source.withResource(
                    inner.source.resourceId,
                    desiredOutputDeviceI32 = currentSource.copyDesiredOutputDeviceI32(),
                ) else inner.source
                val outer = bindInput(node.outer, outerSource)
                ContextualFilterResult(outer.source, outer.bounds, requireNotNull(outer.evaluationKey) {
                    "W6c Compose requires a materialized outer filter result."
                })
            }
            is CapturedFilterNodeV1.Merge -> {
                // List traversal is deliberately positional: repeated and value-equal nodes are
                // evaluated as separate occurrences unless their complete evaluation facts match
                // at a future explicit cache boundary.
                val inputs = node.map { input -> bindInput(input, currentSource) }.toList()
                if (inputs.isEmpty()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.UnsupportedFamily, "W6c Merge requires at least one captured input."))
                val sources = inputs.map(ContextualFilterResult::source)
                val bounds = W6cMultiInputPlanner.bounds(sources)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, inputs.map(ContextualFilterResult::resourceId), output.resourceId, key,
                    FilterPassOperationV1.Merge(sources.map { input -> filterInputSampling(input, bounds) }, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Blend -> {
                // The public order is background then foreground.  FinalBlendPlanner freezes the
                // exact W5 numeric/blend authority before any native materialization occurs.
                val background = bindInput(node.background, currentSource)
                val foreground = bindInput(node.foreground, currentSource)
                val bounds = W6cMultiInputPlanner.bounds(listOf(background.source, foreground.source))
                val blend = requireNotNull(FinalBlendPlanner.plan(
                    org.graphiks.kanvas.render.ir.BlendNode.Mode(node.mode),
                    CoveragePlan.FullOrScissor,
                    SamplePlan.SingleSample,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                )) { "W6c Blend cannot freeze the selected W5 BlendPlan." }
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32,
                    listOf(background.resourceId, foreground.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Blend(blend, filterInputSampling(background.source, bounds),
                        filterInputSampling(foreground.source, bounds), bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Dilate -> {
                val input = materializeInput(node.input, currentSource)
                val key = keyFor(id, null, currentSource, input.copyDeviceBoundsI32())
                appendMorphology(input, FilterPassOperationV1.Morphology.Kind.DILATE,
                    node.radiusX.toDouble(), node.radiusY.toDouble(), key)
            }
            is CapturedFilterNodeV1.Erode -> {
                val input = materializeInput(node.input, currentSource)
                val key = keyFor(id, null, currentSource, input.copyDeviceBoundsI32())
                appendMorphology(input, FilterPassOperationV1.Morphology.Kind.ERODE,
                    node.radiusX.toDouble(), node.radiusY.toDouble(), key)
            }
            is CapturedFilterNodeV1.DistantLitDiffuse -> {
                val input = materializeInput(node.input, currentSource)
                if (!node.direction.x.isFinite() || !node.direction.y.isFinite() || !node.direction.z.isFinite() ||
                    !node.surfaceScale.isFinite() || !node.kd.isFinite() || node.kd < 0f) {
                    throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6d distant diffuse requires finite direction and surface scale plus finite kd >= 0."))
                }
                val mappedDirection = input.mapping.mapLightingVectorToLayerF32OrNull(node.direction) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping,
                        "W6d distant diffuse requires a finite affine layer mapping for its direction."),
                )
                val mappedSurfaceDepth = input.mapping.mapLightingZToLayerF32OrNull(node.surfaceScale) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping,
                        "W6d distant diffuse surface depth cannot be represented by the sealed layer mapping."),
                )
                // Lighting's unbounded output belongs to its consumer, not to a bounded child
                // such as Crop. The child remains the frozen Sobel sampling domain below.
                val bounds = distantDiffuseBounds(input, currentSource.copyDesiredOutputDeviceI32())
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Lighting(LightingFamilyV1.DISTANT_DIFFUSE,
                        LightingParametersV1.Distant(mappedDirection, node.lightColor, mappedSurfaceDepth, node.kd), bounds,
                        FilterImplementationKindV1.DISTANT_DIFFUSE, distantDiffuseSobelSampling(input, bounds))))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.MatrixConvolution -> {
                val input = materializeInput(node.input, currentSource)
                val width = exactPositiveI32(node.kernelSize.width, "matrix kernel width")
                val height = exactPositiveI32(node.kernelSize.height, "matrix kernel height")
                if (node.kernel.sizeI32 != Math.multiplyExact(width, height)) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d matrix kernel count is inconsistent."))
                val offsetX = node.kernelOffset.x.toDouble()
                val offsetY = node.kernelOffset.y.toDouble()
                if (!offsetX.isFinite() || !offsetY.isFinite()) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d matrix kernel offset is non-finite."))
                val bounds = matrixBounds(input, width, height, offsetX, offsetY)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.MatrixConvolution(SizeI32(width, height), node.kernel, node.gain, node.bias,
                        Vector2F64(offsetX, offsetY), node.tileMode, node.convolveAlpha, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.DisplacementMap -> {
                val displacement = materializeInput(node.displacement, currentSource)
                val input = materializeInput(node.input, currentSource)
                if (!node.scale.isFinite()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d displacement scale is non-finite."))
                val bounds = identityBounds(input)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(displacement.resourceId, input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.DisplacementMap(node.xChannelSelector, node.yChannelSelector, node.scale, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Magnifier -> {
                val input = materializeInput(node.input, currentSource)
                val source = node.copySource()
                if (!node.zoom.isFinite() || node.zoom <= 0f || !node.inset.isFinite() || node.inset < 0f ||
                    !source.left.isFinite() || !source.top.isFinite() || !source.right.isFinite() || !source.bottom.isFinite() || source.isEmpty) {
                    throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6d magnifier has invalid lens geometry."))
                }
                val bounds = identityBounds(input)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), output.resourceId, key,
                    FilterPassOperationV1.Magnifier(RectF64(source.left.toDouble(), source.top.toDouble(), source.right.toDouble(), source.bottom.toDouble()),
                        node.zoom, node.inset, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            else -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.UnsupportedFamily, "The captured image-filter family belongs to W6c or W6d.",
            ))
        } }
        fun materializeMask(mask: MaskFilterNode, input: SourceBinding): Pair<SourceBinding, FilterEvaluationKeyV1> = when (mask) {
            is MaskFilterNode.Blur -> {
                val full = blurBounds(input, mask.sigma, mask.sigma)
                val key = keyFor(null, occurrence.maskOccurrenceI32, input, full.copyDesiredOutputDeviceI32())
                val blurred = appendBlur(input, mask.sigma, mask.sigma, TileMode.CLAMP,
                    FilterImplementationKindV1.MASK_COVERAGE_BLUR_X, FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y, key).source
                val styleBounds = identityBounds(blurred)
                val styled = allocateTarget(styleBounds)
                val original = input.resourceId.takeIf { mask.style != org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL }
                val styleInputs = buildList {
                    add(blurred.resourceId)
                    original?.let(::add)
                }
                append(PlanPass.FilterPass(cursor.passOrdinalI32, styleInputs, styled.resourceId, key,
                    FilterPassOperationV1.MaskBlurStyle(mask.style, original, blurred.resourceId, styleBounds,
                        filterInputSampling(blurred, styleBounds), original?.let { filterInputSampling(input, styleBounds) })))
                styled to key
            }
            is MaskFilterNode.Shader -> {
                val bounds = identityBounds(input)
                val key = keyFor(null, occurrence.maskOccurrenceI32, input, bounds.copyDesiredOutputDeviceI32())
                val target = allocateTarget(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), target.resourceId, key,
                    FilterPassOperationV1.MaskShader(
                        FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence(occurrence.idI32, mask.material),
                        bounds, filterInputSampling(input, bounds))))
                target to key
            }
            is MaskFilterNode.Table -> {
                val bounds = identityBounds(input)
                val key = keyFor(null, occurrence.maskOccurrenceI32, input, bounds.copyDesiredOutputDeviceI32())
                val target = allocateTarget(bounds)
                val tableResource = maskTableResource()
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(input.resourceId), target.resourceId, key,
                    FilterPassOperationV1.MaskTable(mask.table, tableResource, 256, 0L,
                        occurrence.maskOccurrenceI32, bounds, filterInputSampling(input, bounds))))
                target to key
            }
        }

        val terminal = occurrence.root?.let { materializeNode(it.id, occurrenceSource) }
            ?: error("W6b image freeze requires one captured image-filter root.")
        return FrozenOccurrence(resources, passes, terminal.source, requireNotNull(terminal.evaluationKey))
    }

    /** Freezes raw-coverage mask work before W5 material/color evaluation. */
    internal fun freezeMaskOccurrence(
        occurrence: PositiveOccurrence,
        coverageSource: SourceBinding,
        cursor: FreezeCursor,
    ): FrozenMask {
        val mask = requireNotNull(occurrence.mask) { "W6b mask freeze requires a captured mask." }
        val resources = mutableListOf<ResourceSpec>()
        val passes = mutableListOf<PlanPass>()
        fun append(pass: PlanPass) {
            require(pass.ordinal == cursor.passOrdinalI32)
            cursor.passOrdinalI32 = Math.addExact(cursor.passOrdinalI32, 1)
            passes += pass
        }
        fun target(bounds: FilterBoundsPlanV1): SourceBinding {
            val ordinal = cursor.filterTargetOrdinalI32.also { cursor.filterTargetOrdinalI32 = Math.addExact(it, 1) }
            val id = planResourceId(PlanResourceRole.FilterTarget, ordinal)
            resources += ResourceSpec(id, PlanResourceRole.FilterTarget,
                SizeI32(bounds.copyDesiredOutputDeviceI32().width(), bounds.copyDesiredOutputDeviceI32().height()))
            return coverageSource.withResource(id,
                SizeI32(bounds.copyDesiredOutputDeviceI32().width(), bounds.copyDesiredOutputDeviceI32().height()),
                bounds.copyTargetOriginDeviceI32(), bounds.copyProducedOutputDeviceI32(),
                bounds.copyDesiredOutputDeviceI32(), bounds.copyRequiredInputDeviceI32(), bounds.copyProducedOutputDeviceI32())
        }
        fun maskTableResource(): PlanResourceId {
            val ordinal = cursor.maskTableOrdinalI32.also {
                cursor.maskTableOrdinalI32 = Math.addExact(it, 1)
            }
            return planResourceId(PlanResourceRole.MaskTableData, ordinal).also { id ->
                resources += ResourceSpec.maskTable(id)
            }
        }
        fun key(desired: RectI32): FilterEvaluationKeyV1 = FilterEvaluationKeyV1.forMaskOccurrence(
            occurrence.maskOccurrenceI32, coverageSource.resourceId, coverageSource.mapping, desired,
        )
        fun blur(source: SourceBinding, sigma: Float, evaluationKey: FilterEvaluationKeyV1): SourceBinding {
            val horizontalBounds = blurBounds(source, sigma, 0f)
            val horizontal = target(horizontalBounds)
            append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(source.resourceId), horizontal.resourceId, evaluationKey,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.MASK_COVERAGE_BLUR_X, sigma,
                    FilterAxisV1.X, TileMode.CLAMP, horizontalBounds, filterInputSampling(source, horizontalBounds))))
            val verticalBounds = blurBounds(horizontal, 0f, sigma)
            val vertical = target(verticalBounds)
            append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(horizontal.resourceId), vertical.resourceId, evaluationKey,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y, sigma,
                    FilterAxisV1.Y, TileMode.CLAMP, verticalBounds, filterInputSampling(horizontal, verticalBounds))))
            return vertical
        }
        val output = when (mask) {
            is MaskFilterNode.Blur -> {
                val original = if (mask.style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL) null else {
                    val ordinal = cursor.coverageOriginalOrdinalI32.also { cursor.coverageOriginalOrdinalI32 = Math.addExact(it, 1) }
                    val id = planResourceId(PlanResourceRole.CoverageOriginal, ordinal)
                    resources += ResourceSpec(id, PlanResourceRole.CoverageOriginal, coverageSource.copyExtentI32())
                    append(PlanPass.FilterCoverageRetainPass(cursor.passOrdinalI32, coverageSource.resourceId, id,
                        filterInputSampling(coverageSource, identityBounds(coverageSource))))
                    coverageSource.withResource(id, knownContentDeviceI32 = coverageSource.copyKnownContentDeviceI32())
                }
                val full = blurBounds(coverageSource, mask.sigma, mask.sigma)
                val evaluationKey = key(full.copyDesiredOutputDeviceI32())
                val blurred = blur(coverageSource, mask.sigma, evaluationKey)
                val bounds = identityBounds(blurred)
                val styled = target(bounds)
                val inputs = buildList {
                    add(blurred.resourceId)
                    original?.let { add(it.resourceId) }
                }
                append(PlanPass.FilterPass(cursor.passOrdinalI32, inputs, styled.resourceId, evaluationKey,
                    FilterPassOperationV1.MaskBlurStyle(mask.style, original?.resourceId, blurred.resourceId, bounds,
                        filterInputSampling(blurred, bounds), original?.let { filterInputSampling(it, bounds) })))
                styled
            }
            is MaskFilterNode.Shader -> {
                val bounds = identityBounds(coverageSource)
                val evaluationKey = key(bounds.copyDesiredOutputDeviceI32())
                val output = target(bounds)
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(coverageSource.resourceId), output.resourceId, evaluationKey,
                    FilterPassOperationV1.MaskShader(
                        FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence(occurrence.idI32, mask.material),
                        bounds, filterInputSampling(coverageSource, bounds))))
                output
            }
            is MaskFilterNode.Table -> {
                val bounds = identityBounds(coverageSource)
                val evaluationKey = key(bounds.copyDesiredOutputDeviceI32())
                val output = target(bounds)
                val tableResource = maskTableResource()
                append(PlanPass.FilterPass(cursor.passOrdinalI32, listOf(coverageSource.resourceId), output.resourceId, evaluationKey,
                    FilterPassOperationV1.MaskTable(mask.table, tableResource, 256, 0L,
                        occurrence.maskOccurrenceI32, bounds, filterInputSampling(coverageSource, bounds))))
                output
            }
        }
        return FrozenMask(resources, passes, output)
    }

    /** A mask-only occurrence still gets one typed terminal filter target before parent blending. */
    internal fun freezeMaterializedSource(
        occurrence: PositiveOccurrence,
        materialSource: SourceBinding,
        coverageSource: SourceBinding,
        cursor: FreezeCursor,
    ): FrozenOccurrence {
        val bounds = identityBounds(materialSource)
        val id = planResourceId(PlanResourceRole.FilterTarget, cursor.filterTargetOrdinalI32)
        cursor.filterTargetOrdinalI32 = Math.addExact(cursor.filterTargetOrdinalI32, 1)
        val output = materialSource.withResource(id,
            SizeI32(bounds.copyDesiredOutputDeviceI32().width(), bounds.copyDesiredOutputDeviceI32().height()),
            bounds.copyTargetOriginDeviceI32(), bounds.copyProducedOutputDeviceI32(),
            bounds.copyDesiredOutputDeviceI32(), bounds.copyRequiredInputDeviceI32(), bounds.copyProducedOutputDeviceI32())
        val key = FilterEvaluationKeyV1.forMaskOccurrence(occurrence.maskOccurrenceI32, materialSource.resourceId,
            materialSource.mapping, bounds.copyDesiredOutputDeviceI32())
        // The styled coverage is an existing frozen producer.  Publish it as a true filter
        // input rather than asking the renderer to rediscover a producer-side association.
        val pass = PlanPass.FilterPass(cursor.passOrdinalI32,
            listOf(materialSource.resourceId, coverageSource.resourceId), output.resourceId, key,
            FilterPassOperationV1.MaterializedSource(bounds, filterInputSampling(materialSource, bounds),
                filterInputSampling(coverageSource, bounds)))
        cursor.passOrdinalI32 = Math.addExact(cursor.passOrdinalI32, 1)
        return FrozenOccurrence(listOf(ResourceSpec(id, PlanResourceRole.FilterTarget, output.copyExtentI32())),
            listOf(pass), output, key)
    }

    private fun identityBounds(source: SourceBinding): FilterBoundsPlanV1 {
        val domain = source.copyDeviceBoundsI32()
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), source.copyDesiredOutputDeviceI32() ?: domain,
            source.copyRequiredInputDeviceI32() ?: domain,
            source.copyProducedOutputDeviceI32() ?: source.copyKnownContentDeviceI32(), source.originDeviceI32)
    }

    private fun exactPositiveI32(value: Float, label: String): Int {
        if (!value.isFinite() || value <= 0f || value != value.toLong().toFloat()) throw ConstructionFailure(
            W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d $label is not a positive integral F32."))
        return try { Math.toIntExact(value.toLong()) } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6d $label overflows I32."))
        }
    }

    private fun matrixBounds(source: SourceBinding, width: Int, height: Int, offsetX: Double, offsetY: Double): FilterBoundsPlanV1 {
        val desired = source.copyDeviceBoundsI32()
        val required = RectF64(desired.left.toDouble(), desired.top.toDouble(), desired.right.toDouble(), desired.bottom.toDouble())
            .expandSamplingHaloF64OrNull(offsetX.coerceAtLeast(0.0), offsetY.coerceAtLeast(0.0),
                (width - 1.0 - offsetX).coerceAtLeast(0.0), (height - 1.0 - offsetY).coerceAtLeast(0.0))
            ?.roundOutToRectI32OrNull() ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6d matrix sampling halo cannot be represented in checked I32 texels."))
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), desired, required,
            source.copyProducedOutputDeviceI32() ?: source.copyKnownContentDeviceI32(), source.originDeviceI32)
    }

    /** Lighting affects transparent black, so its desired domain is never narrowed to source content. */
    private fun distantDiffuseBounds(source: SourceBinding, consumerDemand: RectI32?): FilterBoundsPlanV1 {
        val desired = consumerDemand ?: source.copyDesiredOutputDeviceI32() ?: source.copyDeviceBoundsI32()
        val required = RectF64(desired.left.toDouble(), desired.top.toDouble(), desired.right.toDouble(), desired.bottom.toDouble())
            .expandSamplingHaloF64OrNull(1.0, 1.0, 1.0, 1.0)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6d distant diffuse Sobel halo cannot be represented in checked I32 texels."))
        // The diffuse pass can light transparent-black output; its emitted known content is
        // therefore the terminal consumer, even when the Sobel input lies wholly in its halo.
        return FilterBoundsPlanV1(desired.copy(), desired, required, desired.copy(),
            Point2I32(desired.left, desired.top))
    }

    /** All origin transforms and edge decisions are published before the renderer sees a pass. */
    private fun distantDiffuseSobelSampling(input: SourceBinding, bounds: FilterBoundsPlanV1): W6dSobelSamplingV1 {
        val output = bounds.copyDesiredOutputDeviceI32()
        val child = input.copyDeviceBoundsI32()
        fun local(region: RectI32, label: String): RectI32 = try {
            RectI32(
                Math.subtractExact(region.left, output.left), Math.subtractExact(region.top, output.top),
                Math.subtractExact(region.right, output.left), Math.subtractExact(region.bottom, output.top),
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6d distant diffuse $label cannot be represented in output-local I32 texels."))
        }
        val offset = try {
            Point2I32(Math.subtractExact(output.left, input.originDeviceI32.x), Math.subtractExact(output.top, input.originDeviceI32.y))
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6d distant diffuse input offset cannot be represented in output-local I32 texels."))
        }
        return W6dSobelSamplingV1(offset, local(child, "child bounds"), local(bounds.copyRequiredInputDeviceI32(), "required input"),
            if (child.left == output.left) W6dSobelEdgeModeV1.CLAMP else W6dSobelEdgeModeV1.DECAL,
            if (child.top == output.top) W6dSobelEdgeModeV1.CLAMP else W6dSobelEdgeModeV1.DECAL,
            if (child.right == output.right) W6dSobelEdgeModeV1.CLAMP else W6dSobelEdgeModeV1.DECAL,
            if (child.bottom == output.bottom) W6dSobelEdgeModeV1.CLAMP else W6dSobelEdgeModeV1.DECAL)
    }

    /** The output domain grows by blur support; it is never intersected back to the source. */
    internal fun reverseInputDemand(
        occurrence: PositiveOccurrence?,
        desired: RectI32,
        mapping: LayerMappingF64? = null,
    ): RectI32? {
        if (occurrence == null) return desired.copy()
        fun expand(region: RectI32, x: Float, y: Float): RectI32 = RectF64(
            region.left.toDouble(), region.top.toDouble(), region.right.toDouble(), region.bottom.toDouble())
            .expandForBlurF64OrNull(x, y)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds,
                "W6b reverse blur demand cannot be represented in checked I32 texels.",
            ))
        fun spatialDemand(node: CapturedFilterNodeV1, output: RectI32): RectI32? =
            W6cSpatialBoundsPlanner.requiredInputBounds(node, output, mapping ?: throw ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                    "W6c reverse demand has no sealed local-to-device mapping."),
        ))
        lateinit var inputDemand: (CapturedFilterInputV1, RectI32) -> RectI32?
        fun unionInputDemands(inputs: Iterable<CapturedFilterInputV1>, output: RectI32): RectI32? {
            var demand: RectI32? = null
            for (input in inputs) {
                val required = inputDemand(input, output) ?: continue
                demand = demand?.let { union(it, required) } ?: required
            }
            return demand
        }
        fun nodeDemand(id: CapturedFilterNodeIdI32, output: RectI32): RectI32? = when (val node = occurrence.table.nodeAt(id)) {
            is CapturedFilterNodeV1.Crop -> spatialDemand(node, output)?.let { required -> inputDemand(node.input, required) }
            is CapturedFilterNodeV1.Offset -> spatialDemand(node, output)?.let { required -> inputDemand(node.input, required) }
            is CapturedFilterNodeV1.Tile -> spatialDemand(node, output)?.let { required -> inputDemand(node.input, required) }
            is CapturedFilterNodeV1.Dilate -> inputDemand(node.input,
                W6cMorphologyPlanner.requiredInputBounds(node.radiusX.toDouble(), node.radiusY.toDouble(), output,
                    mapping ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6c reverse demand has no sealed local-to-device mapping."))))
            is CapturedFilterNodeV1.Erode -> inputDemand(node.input,
                W6cMorphologyPlanner.requiredInputBounds(node.radiusX.toDouble(), node.radiusY.toDouble(), output,
                    mapping ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6c reverse demand has no sealed local-to-device mapping."))))
            is CapturedFilterNodeV1.Blur -> inputDemand(node.input, expand(output, node.sigmaX, node.sigmaY))
            is CapturedFilterNodeV1.DropShadow -> {
                val translated = RectF64(output.left.toDouble(), output.top.toDouble(),
                    output.right.toDouble(), output.bottom.toDouble()).translateF64OrNull(-node.dx.toDouble(), -node.dy.toDouble())
                    ?.roundOutToRectI32OrNull() ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds,
                    "W6b reverse shadow demand cannot be represented in checked I32 texels.",
                ))
                val shadow = expand(translated, node.sigmaX, node.sigmaY)
                inputDemand(node.input, if (node.mode == CapturedDropShadowModeV1.COMPOSITE) union(output, shadow) else shadow)
            }
            // These nodes do not alter their input's spatial demand.  Compose is contextual:
            // outer consumes the inner result, so its reverse demand must flow into inner.
            is CapturedFilterNodeV1.ColorFilter -> inputDemand(node.input, output)
            is CapturedFilterNodeV1.Compose -> inputDemand(node.outer, output)?.let { outerDemand ->
                inputDemand(node.inner, outerDemand)
            }
            // Keep the captured public order while forming a geometric union.  A Set here would
            // erase repeated inputs before their individual source demand is accounted for.
            is CapturedFilterNodeV1.Merge -> unionInputDemands(node, output)
            is CapturedFilterNodeV1.Blend -> unionInputDemands(listOf(node.background, node.foreground), output)
            is CapturedFilterNodeV1.MatrixConvolution -> inputDemand(node.input, output)
            is CapturedFilterNodeV1.DisplacementMap -> unionInputDemands(listOf(node.displacement, node.input), output)
            is CapturedFilterNodeV1.Magnifier -> inputDemand(node.input, output)
            is CapturedFilterNodeV1.DistantLitDiffuse -> inputDemand(node.input, sobelRequiredInput(output))
            else -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.UnsupportedFamily,
                "Reverse demand requires an admitted W6b filter."))
        }
        inputDemand = { input, region -> if (input is CapturedFilterInputV1.Node) nodeDemand(input.id, region) else region }
        val imageInput = occurrence.root?.let { nodeDemand(it.id, desired) } ?: desired
        return (occurrence.mask as? MaskFilterNode.Blur)?.let { expand(imageInput, it.sigma, it.sigma) } ?: imageInput
    }

    private fun sobelRequiredInput(output: RectI32): RectI32 = RectF64(
        output.left.toDouble(), output.top.toDouble(), output.right.toDouble(), output.bottom.toDouble(),
    ).expandSamplingHaloF64OrNull(1.0, 1.0, 1.0, 1.0)?.roundOutToRectI32OrNull()
        ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
            "W6d distant diffuse reverse Sobel demand cannot be represented in checked I32 texels."))

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

    /**
     * Freezes the MatrixTransform-equivalent source coordinate and both linear-sampling
     * footprints in target-local texels.  `roundOut` of the translated F64 domain includes the
     * outer transparent half-texel at fractional offsets; required input remains the exact
     * sampled source domain and DECAL supplies transparent taps beyond it.
     */
    private fun dropShadowLinearSampling(
        input: SourceBinding,
        outputBounds: FilterBoundsPlanV1,
        dxF64: Double,
        dyF64: Double,
    ): DropShadowLinearSamplingV1 {
        val inputDomain = input.copyDeviceBoundsI32()
        val outputDomain = outputBounds.copyDesiredOutputDeviceI32()
        val inputOrigin = input.originDeviceI32
        val outputOrigin = outputBounds.copyTargetOriginDeviceI32()
        val sourceOffset = try {
            Vector2F64(
                Math.subtractExact(outputOrigin.x, inputOrigin.x).toDouble() - dxF64 - .5,
                Math.subtractExact(outputOrigin.y, inputOrigin.y).toDouble() - dyF64 - .5,
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6b shadow linear-sampling origin overflows I32.",
            ))
        }
        return DropShadowLinearSamplingV1(
            sourceOffset,
            RectI32(0, 0, inputDomain.width(), inputDomain.height()),
            RectI32(0, 0, outputDomain.width(), outputDomain.height()),
        )
    }

    /** A COMPOSITE pass receives only plan-frozen target-local source coordinates. */
    private fun targetLocalSampleOffset(input: SourceBinding, outputBounds: FilterBoundsPlanV1): Point2I32 {
        val outputOrigin = outputBounds.copyTargetOriginDeviceI32()
        return try {
            Point2I32(
                Math.subtractExact(outputOrigin.x, input.originDeviceI32.x),
                Math.subtractExact(outputOrigin.y, input.originDeviceI32.y),
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6b shadow target-local sample offset overflows I32.",
            ))
        }
    }

    /** Converts every source-space rectangle while the graph still owns both device origins. */
    private fun filterInputSampling(input: SourceBinding, outputBounds: FilterBoundsPlanV1): FilterInputSamplingV1 {
        val inputOrigin = input.originDeviceI32
        val known = input.copyKnownContentDeviceI32() ?: input.copyDeviceBoundsI32()
        val knownTargetLocal = try {
            RectI32(
                Math.subtractExact(known.left, inputOrigin.x),
                Math.subtractExact(known.top, inputOrigin.y),
                Math.subtractExact(known.right, inputOrigin.x),
                Math.subtractExact(known.bottom, inputOrigin.y),
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds,
                "W6b input known-content cannot be represented in target-local I32 texels.",
            ))
        }
        return FilterInputSamplingV1(targetLocalSampleOffset(input, outputBounds), knownTargetLocal)
    }

    /** Seals F64 spatial sampling in resource coordinates; renderer receives neither device origins nor mapping. */
    private fun spatialSampling(
        input: SourceBinding,
        outputBounds: FilterBoundsPlanV1,
        clipOutputTargetLocalF64: RectF64,
        extraOffsetXF64: Double,
        extraOffsetYF64: Double,
    ): SpatialSamplingV1 {
        val inputDomain = input.copyDeviceBoundsI32()
        val sourceLocal = try {
            RectI32(
                Math.subtractExact(inputDomain.left, input.originDeviceI32.x),
                Math.subtractExact(inputDomain.top, input.originDeviceI32.y),
                Math.subtractExact(inputDomain.right, input.originDeviceI32.x),
                Math.subtractExact(inputDomain.bottom, input.originDeviceI32.y),
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6c source sampling rectangle cannot be represented in target-local I32 texels."))
        }
        val outputOrigin = outputBounds.copyTargetOriginDeviceI32()
        val offset = try {
            Vector2F64(
                Math.subtractExact(outputOrigin.x, input.originDeviceI32.x).toDouble() + extraOffsetXF64,
                Math.subtractExact(outputOrigin.y, input.originDeviceI32.y).toDouble() + extraOffsetYF64,
            )
        } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6c source sampling origin overflows I32."))
        }
        return SpatialSamplingV1(sourceLocal, clipOutputTargetLocalF64, offset)
    }

    private fun fullClip(bounds: FilterBoundsPlanV1): RectF64 {
        val desired = bounds.copyDesiredOutputDeviceI32()
        return RectF64(0.0, 0.0, desired.width().toDouble(), desired.height().toDouble())
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
        var invalidMaskTableLengthI32: Int? = null
        val bounded = visitScenes(scene) { nestedScene, command, _, _, _, _, _ -> when (command) {
            is SceneCommand.Draw -> filterPayload(command.node.paint, command.node.effects).let { payload ->
                payload.root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                mask = mask || payload.mask != null
                (payload.mask as? MaskFilterNode.Table)?.let { table ->
                    if (table.table.sizeI32 != 256 && invalidMaskTableLengthI32 == null)
                        invalidMaskTableLengthI32 = table.table.sizeI32
                }
            }
            is SceneCommand.BeginLayer -> filterPayload(command.descriptor.paint, command.descriptor.effects).let { payload ->
                payload.root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                mask = mask || payload.mask != null
                (payload.mask as? MaskFilterNode.Table)?.let { table ->
                    if (table.table.sizeI32 != 256 && invalidMaskTableLengthI32 == null)
                        invalidMaskTableLengthI32 = table.table.sizeI32
                }
                if (command.descriptor.backdrop !is EffectStack.Empty) {
                    backdrop = true
                    filterPayload(null, command.descriptor.backdrop).root?.let { roots += RootOccurrence(nestedScene.filterTable, it) }
                }
                filteredPrevious = filteredPrevious || command.descriptor.initWithPrevious && (payload.root != null || payload.mask != null)
            }
            else -> Unit
        } }
        return Ownership(roots, mask, backdrop, filteredPrevious, bounded, invalidMaskTableLengthI32)
    }

    /** Bounded iterative traversal refuses repeated ancestral Picture scenes. */
    private fun visitScenes(root: SceneSnapshot,
        visit: (SceneSnapshot, SceneCommand, Int, Int, Boolean, List<org.graphiks.kanvas.render.ir.DrawNode>, List<Int>) -> Unit): Boolean {
        var visitedCommandsI32 = 0
        fun visitOrdered(
            scene: SceneSnapshot,
            depthI32: Int,
            insertionCommandIndexI32: Int?,
            ancestors: Set<String>,
            outerPictures: List<org.graphiks.kanvas.render.ir.DrawNode>,
            picturePathI32: List<Int>,
        ): Boolean {
            if (depthI32 > root.graphLimits.maxDepth) return true
            val nextAncestors = ancestors + scene.canonicalId.value
            scene.toList().forEachIndexed { indexI32, command ->
                visitedCommandsI32 = try { Math.addExact(visitedCommandsI32, 1) } catch (_: ArithmeticException) { return true }
                if (visitedCommandsI32 > root.graphLimits.maxNodes) return true
                val insertion = insertionCommandIndexI32 ?: indexI32
                visit(scene, command, insertion, indexI32, insertionCommandIndexI32 != null, outerPictures, picturePathI32)
                val picture = (command as? SceneCommand.Draw)?.node?.geometry as? GeometryNode.Picture ?: return@forEachIndexed
                if (picture.scene.canonicalId.value in nextAncestors) return true
                if (visitOrdered(picture.scene, Math.addExact(depthI32, 1), insertion, nextAncestors,
                        outerPictures + command.node, picturePathI32 + indexI32)) return true
            }
            return false
        }
        return visitOrdered(root, 1, null, emptySet(), emptyList(), emptyList())
    }

    private class Ownership(
        private val roots: List<RootOccurrence>,
        private val hasMask: Boolean,
        val hasBackdrop: Boolean,
        val hasFilteredPrevious: Boolean,
        val traversalBounded: Boolean,
        val invalidMaskTableLengthI32: Int?,
    ) {
        // Traversal bounds diagnose an owned W6b filter occurrence; a filter-free scene must
        // retain its existing W6a command-limit admission rather than becoming W6b-owned only
        // because the generic scene walk reached maxNodes.
        val isOwned: Boolean get() = roots.isNotEmpty() || hasMask || hasBackdrop
        fun unsupportedImageFamilyOwnerOrNull(): String? {
            roots.forEach { root ->
            val pending = ArrayDeque<CapturedFilterNodeIdI32>()
            pending.addLast(root.root.id)
            val seen = BooleanArray(root.table.nodeCount)
            while (pending.isNotEmpty()) {
                val id = pending.removeLast()
                if (id.valueI32 !in seen.indices || seen[id.valueI32]) continue
                seen[id.valueI32] = true
                when (val node = root.table.nodeAt(id)) {
                    is CapturedFilterNodeV1.Crop -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.Offset -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.Tile -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.Blur -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.DropShadow -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.ColorFilter -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.Compose -> {
                        node.inner.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                        node.outer.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    }
                    is CapturedFilterNodeV1.Merge -> node.forEach { input ->
                        input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    }
                    is CapturedFilterNodeV1.Blend -> {
                        node.background.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                        node.foreground.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    }
                    is CapturedFilterNodeV1.Dilate -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.Erode -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.MatrixConvolution -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.DisplacementMap -> {
                        node.displacement.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                        node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    }
                    is CapturedFilterNodeV1.Magnifier -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.DistantLitDiffuse -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    else -> return if (isW6cVariant(node)) "W6c" else "W6d"
                }
            }
            }
            return null
        }

        fun firstMergeInputCountExceeding(sampledTextureLimit: Int?, bindGroupBindingLimit: Int?): Int? {
            roots.forEach { root ->
                val pending = ArrayDeque<CapturedFilterNodeIdI32>()
                pending.addLast(root.root.id)
                val seen = BooleanArray(root.table.nodeCount)
                while (pending.isNotEmpty()) {
                    val id = pending.removeLast()
                    if (id.valueI32 !in seen.indices || seen[id.valueI32]) continue
                    seen[id.valueI32] = true
                    when (val node = root.table.nodeAt(id)) {
                        is CapturedFilterNodeV1.Crop -> node.input.enqueueNodeOrUnsupported(pending)
                        is CapturedFilterNodeV1.Offset -> node.input.enqueueNodeOrUnsupported(pending)
                        is CapturedFilterNodeV1.Tile -> node.input.enqueueNodeOrUnsupported(pending)
                        is CapturedFilterNodeV1.Blur -> node.input.enqueueNodeOrUnsupported(pending)
                        is CapturedFilterNodeV1.DropShadow -> node.input.enqueueNodeOrUnsupported(pending)
                        is CapturedFilterNodeV1.ColorFilter -> node.input.enqueueNodeOrUnsupported(pending)
                        is CapturedFilterNodeV1.Compose -> {
                            node.inner.enqueueNodeOrUnsupported(pending)
                            node.outer.enqueueNodeOrUnsupported(pending)
                        }
                        is CapturedFilterNodeV1.Merge -> {
                            if (sampledTextureLimit == null || bindGroupBindingLimit == null ||
                                node.inputCount > sampledTextureLimit || node.inputCount > bindGroupBindingLimit
                            ) return node.inputCount
                            node.forEach { input -> input.enqueueNodeOrUnsupported(pending) }
                        }
                        is CapturedFilterNodeV1.Blend -> {
                            node.background.enqueueNodeOrUnsupported(pending)
                            node.foreground.enqueueNodeOrUnsupported(pending)
                        }
                        else -> Unit
                    }
                }
            }
            return null
        }
    }

    private data class RootOccurrence(val table: CapturedFilterTableV1, val root: CapturedFilterRootV1)

    /** W6c owns these roots and this slice admits Crop, Offset and Tile only. */
    private fun isW6cVariant(node: CapturedFilterNodeV1): Boolean = node is CapturedFilterNodeV1.Crop ||
        node is CapturedFilterNodeV1.Offset || node is CapturedFilterNodeV1.Tile ||
        node is CapturedFilterNodeV1.ColorFilter || node is CapturedFilterNodeV1.Compose ||
        node is CapturedFilterNodeV1.Merge || node is CapturedFilterNodeV1.Blend ||
        node is CapturedFilterNodeV1.Dilate || node is CapturedFilterNodeV1.Erode
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

    private fun CapturedFilterInputV1.enqueueNodeOrUnsupported(pending: ArrayDeque<CapturedFilterNodeIdI32>): Boolean? = when (this) {
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
