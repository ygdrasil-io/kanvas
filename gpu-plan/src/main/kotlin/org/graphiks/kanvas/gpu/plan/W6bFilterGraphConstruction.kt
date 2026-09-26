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
import org.graphiks.math.geometry.intersectF64OrNull
import org.graphiks.math.geometry.roundOutToRectI32OrNull
import org.graphiks.math.geometry.translateF64OrNull
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.mapRectBoundsF64OrNull
import org.graphiks.math.vector.Vector2F64

/** W6b's single captured-filter authority; it publishes planning facts but never native work. */
internal object W6bFilterGraphConstruction {
    internal class SourceBinding(
        val resourceId: PlanResourceId,
        extent: SizeI32,
        override val originDeviceI32: Point2I32,
        override val mapping: LayerMappingF64,
        knownContentDeviceI32: RectI32? = null,
        desiredOutputDeviceI32: RectI32? = null,
        requiredInputDeviceI32: RectI32? = null,
        producedOutputDeviceI32: RectI32? = null,
        /** Exact immutable SceneSnapshot revision for cache admission, if known. */
        val sourceRevisionIdentity: String? = null,
    ) : W6bSourceGeometryV1 {
        private val extentSnapshotI32 = extent.copy()
        private val knownContentSnapshotI32 = knownContentDeviceI32?.copy()
        private val desiredOutputSnapshotI32 = desiredOutputDeviceI32?.copy()
        private val requiredInputSnapshotI32 = requiredInputDeviceI32?.copy()
        private val producedOutputSnapshotI32 = producedOutputDeviceI32?.copy()

        override fun copyExtentI32(): SizeI32 = extentSnapshotI32.copy()
        override fun copyKnownContentDeviceI32(): RectI32? = knownContentSnapshotI32?.copy()
        override fun copyDesiredOutputDeviceI32(): RectI32? = desiredOutputSnapshotI32?.copy()
        override fun copyRequiredInputDeviceI32(): RectI32? = requiredInputSnapshotI32?.copy()
        override fun copyProducedOutputDeviceI32(): RectI32? = producedOutputSnapshotI32?.copy()
        override fun copyDeviceBoundsI32(): RectI32 = try {
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
        /** Unique source evaluation that owns nested captured Picture traversal. */
        val evaluationIdentityI32: Int,
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
        /** This is the save-time backdrop root, not the post-child layer root. */
        val isBackdropInitialization: Boolean,
        val maskOccurrenceI32: Int,
        picturePathI32: List<Int>,
        val source: FilterOccurrenceSourceV1,
    ) {
        internal val topology: W6bBoundFilterTopologyV1 by lazy { W6bBoundFilterTopologyV1(this) }
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

    /** The one frame schedule shared by W6a Picture sources and W6b filter operations. */
    internal class W6FramePassSinkV1(private val values: MutableList<PlanPass>) {
        fun nextOrdinalI32(): Int = values.size

        fun append(pass: PlanPass) {
            require(pass.ordinal == values.size) { "W6 frame pass ordinal is not contiguous." }
            values += pass
        }

        fun appendAll(passes: List<PlanPass>) {
            passes.forEach(::append)
        }
    }

    /** The W6a-owned aggregate construction result consumed by one W6b Picture leaf. */
    internal class FilterPictureSourceEmissionV1(
        val aggregateId: PictureStreamAggregateIdI32,
        val resourceId: PlanResourceId,
        val sourceGenerationI64: Long,
        val source: SourceBinding,
        val owner: PictureAggregateOwnerV1.FilterPicture,
        contentDeviceF64: RectF64,
    ) {
        private val contentSnapshotF64 = contentDeviceF64.copy()
        init { require(sourceGenerationI64 >= 0L && contentSnapshotF64.isFinite() && !contentSnapshotF64.isEmpty) }
        fun copyContentDeviceF64(): RectF64 = contentSnapshotF64.copy()
    }

    internal fun interface FilterPictureSourceEmitterV1 {
        fun emit(
            prepared: W6bPreparedPictureSourceV1,
            sourceContext: SourceBinding,
        ): FilterPictureSourceEmissionV1
    }

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
        val source: W6bRecipeSourceV1,
        val bounds: FilterBoundsPlanV1,
        val evaluationKey: W6bRecipeKeyV1?,
    ) {
        val symbol: W6bRecipeSymbolV1 get() = source.symbol
    }

    internal fun owns(scene: SceneSnapshot): Boolean = ownership(scene).isOwned

    /** W6d resource refusals retain their advanced-family owner across W6c wrappers. */
    internal fun ownsW6dAdvanced(scene: SceneSnapshot): Boolean = ownership(scene).hasW6dAdvanced

    internal fun hasReverseInputDemandTerminal(occurrence: PositiveOccurrence): Boolean =
        occurrence.topology.hasTerminalFamily { node -> when (node) {
            is CapturedFilterNodeV1.MatrixConvolution, is CapturedFilterNodeV1.DisplacementMap,
            is CapturedFilterNodeV1.Magnifier, is CapturedFilterNodeV1.DistantLitDiffuse,
            is CapturedFilterNodeV1.PointLitDiffuse, is CapturedFilterNodeV1.SpotLitDiffuse,
            is CapturedFilterNodeV1.DistantLitSpecular, is CapturedFilterNodeV1.PointLitSpecular,
            is CapturedFilterNodeV1.SpotLitSpecular -> true
            else -> false
        } }

    internal fun hasConsumerDemandTerminal(occurrence: PositiveOccurrence): Boolean =
        occurrence.topology.hasTerminalFamily { node -> when (node) {
            is CapturedFilterNodeV1.DistantLitDiffuse, is CapturedFilterNodeV1.PointLitDiffuse,
            is CapturedFilterNodeV1.SpotLitDiffuse, is CapturedFilterNodeV1.DistantLitSpecular,
            is CapturedFilterNodeV1.PointLitSpecular, is CapturedFilterNodeV1.SpotLitSpecular -> true
            else -> false
        } }

    internal fun hasContentOutputSamplingTerminal(occurrence: PositiveOccurrence): Boolean =
        occurrence.topology.hasTerminalFamily { node ->
            node is CapturedFilterNodeV1.DisplacementMap || node is CapturedFilterNodeV1.Magnifier
        }

    /** Unsupported captured-filter cases stop before source allocation. */
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
        var nextEvaluationIdentityI32 = 0
        visitScenes(scene) { nestedScene, command, insertionIndexI32, commandIndexI32, nested, outerPictures, picturePathI32 ->
            val evaluationIdentityI32 = nextEvaluationIdentityI32.also {
                nextEvaluationIdentityI32 = Math.addExact(it, 1)
            }
            fun append(
                root: CapturedFilterRootV1?,
                mask: MaskFilterNode?,
                layer: Boolean,
                picture: Boolean,
                backdropInitialization: Boolean = false,
            ) {
                if (root == null && mask == null) return
                result += PositiveOccurrence(
                    nextOccurrenceI32++, evaluationIdentityI32, nestedScene.filterTable, root, mask, insertionIndexI32,
                    nestedScene.canonicalId.value, commandIndexI32, layer, picture || nested, backdropInitialization,
                    nextMaskOccurrenceI32,
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
                    filterPayload(null, command.descriptor.backdrop).root?.let { backdrop ->
                        append(backdrop, null, true, false, backdropInitialization = true)
                    }
                }
                else -> Unit
            }
            evaluationIdentityI32
        }
        return immutableList(result)
    }

    /** Freezes one occurrence from its exact immutable source generation. */
    /** Freezes only the post-material captured image-filter chain. */
    internal fun freezeImageOccurrence(
        occurrence: PositiveOccurrence,
        sourceBinding: SourceBinding,
        cursor: FreezeCursor,
        sink: W6FramePassSinkV1,
        emitFilterPictureSource: FilterPictureSourceEmitterV1,
        evaluated: W6bEvaluatedFilterRecipeV1,
    ): FrozenOccurrence {
        val source = sourceBinding.withSourceRevision(
            "${occurrence.source.scene.canonicalId.value}:${occurrence.source.sourceCommandIndexI32}:" +
                occurrence.source.picturePathI32().joinToString(","))
        return lowerRecipe(evaluated, source, cursor, sink, emitFilterPictureSource)
    }

    private fun lowerRecipe(
        recipe: W6bEvaluatedFilterRecipeV1,
        source: SourceBinding,
        cursor: FreezeCursor,
        sink: W6FramePassSinkV1?,
        emitFilterPictureSource: FilterPictureSourceEmitterV1?,
    ): FrozenOccurrence {
        val sealedSource = recipe.sources.getValue(W6bRecipeSymbolV1(0))
        require(source.copyDeviceBoundsI32() == sealedSource.copyDeviceBoundsI32() &&
            source.mapping.copyLocalToDeviceF64() == sealedSource.mapping.copyLocalToDeviceF64()) {
            "Physical filter source disagrees with its evaluated sampling domain."
        }
        val resources = mutableListOf<ResourceSpec>()
        val passes = mutableListOf<PlanPass>()
        val bindings = linkedMapOf(W6bRecipeSymbolV1(0) to source)
        val tableIds = mutableMapOf<W6bRecipeSymbolV1, PlanResourceId>()
        val pictures = mutableMapOf<W6bRecipeSymbolV1, FilterPictureSourceEmissionV1>()
        fun physical(symbol: W6bRecipeSymbolV1): SourceBinding = bindings.getValue(symbol)
        val keys = java.util.IdentityHashMap<W6bRecipeKeyV1, FilterEvaluationKeyV1>()
        fun lowerKey(key: W6bRecipeKeyV1): FilterEvaluationKeyV1 = keys.getOrPut(key) {
            key.lower(physical(key.boundSource.symbol))
        }
        fun append(pass: PlanPass) {
            require(pass.ordinal == cursor.passOrdinalI32)
            cursor.passOrdinalI32 = Math.addExact(cursor.passOrdinalI32, 1)
            sink?.append(pass)
            passes += pass
        }
        for (instruction in recipe.instructions) when (instruction) {
            is W6bRecipeInstructionV1.Resource -> {
                val ordinal = when (instruction.role) {
                    PlanResourceRole.FilterTarget -> cursor.filterTargetOrdinalI32.also {
                        cursor.filterTargetOrdinalI32 = Math.addExact(it, 1) }
                    PlanResourceRole.FilterTransparentBlack -> cursor.transparentBlackOrdinalI32.also {
                        cursor.transparentBlackOrdinalI32 = Math.addExact(it, 1) }
                    PlanResourceRole.CoverageOriginal -> cursor.coverageOriginalOrdinalI32.also {
                        cursor.coverageOriginalOrdinalI32 = Math.addExact(it, 1) }
                    else -> error("Recipe has an invalid resource role.")
                }
                val id = planResourceId(instruction.role, ordinal)
                val geometry = recipe.sources.getValue(instruction.symbol)
                resources += ResourceSpec(id, instruction.role, instruction.copyExtentI32())
                bindings[instruction.symbol] = source.withResource(id, geometry.copyExtentI32(),
                    geometry.originDeviceI32, geometry.copyKnownContentDeviceI32(), geometry.copyDesiredOutputDeviceI32(),
                    geometry.copyRequiredInputDeviceI32(), geometry.copyProducedOutputDeviceI32())
            }
            is W6bRecipeInstructionV1.MaskTableResource -> {
                val ordinal = cursor.maskTableOrdinalI32.also { cursor.maskTableOrdinalI32 = Math.addExact(it, 1) }
                val id = planResourceId(PlanResourceRole.MaskTableData, ordinal)
                resources += ResourceSpec.maskTable(id)
                tableIds[instruction.symbol] = id
            }
            is W6bRecipeInstructionV1.RetainCoverage -> append(PlanPass.FilterCoverageRetainPass(
                cursor.passOrdinalI32, physical(instruction.source).resourceId,
                physical(instruction.output).resourceId, instruction.sampling))
            is W6bRecipeInstructionV1.Clear -> append(PlanPass.FilterSourceClear(cursor.passOrdinalI32,
                physical(instruction.output).resourceId, physical(instruction.reference).resourceId))
            is W6bRecipeInstructionV1.PictureSource -> {
                val emitted = requireNotNull(emitFilterPictureSource).emit(instruction.prepared, physical(instruction.context))
                require(emitted.source.copyDeviceBoundsI32() == instruction.output.copyDeviceBoundsI32()) {
                    "Picture physical source disagrees with its evaluated recipe domain."
                }
                pictures[instruction.output.symbol] = emitted
                bindings[instruction.output.symbol] = emitted.source
                cursor.passOrdinalI32 = requireNotNull(sink).nextOrdinalI32()
            }
            is W6bRecipeInstructionV1.Pass -> {
                val key = lowerKey(instruction.key)
                val operation = when (val selected = instruction.operation) {
                    is W6bRecipeOperationV1.Fixed -> selected.operation
                    is W6bRecipeOperationV1.MaskStyle -> FilterPassOperationV1.MaskBlurStyle(selected.style,
                        selected.original?.let { physical(it).resourceId }, physical(selected.blurred).resourceId,
                        selected.bounds, selected.blurredSampling, selected.originalSampling)
                    is W6bRecipeOperationV1.MaskTable -> FilterPassOperationV1.MaskTable(selected.table,
                        tableIds.getValue(selected.resource), 256, 0L, selected.ownerMaskOccurrenceI32,
                        selected.bounds, selected.sampling)
                    is W6bRecipeOperationV1.ShadowComposite -> FilterPassOperationV1.DropShadowComposite(
                        selected.mode, physical(selected.original).resourceId, selected.bounds,
                        selected.shadowOffset, selected.originalOffset)
                    is W6bRecipeOperationV1.Picture -> {
                        val emitted = pictures.getValue(selected.source)
                        val sealed = SealedPictureFilterSourceV1(emitted.aggregateId, emitted.resourceId,
                            emitted.sourceGenerationI64, selected.inputSampling, emitted.owner)
                        require(sealed.copyOwner().authenticates(key)) {
                            "W6d Picture source owner does not authenticate its captured-node evaluation."
                        }
                        FilterPassOperationV1.Picture(sealed, selected.prepared.copyContentDeviceF64(),
                            selected.bounds, selected.sampling)
                    }
                }
                append(PlanPass.FilterPass(cursor.passOrdinalI32, instruction.inputs.map { physical(it).resourceId },
                    physical(instruction.output).resourceId, key, operation))
            }
        }
        return FrozenOccurrence(resources, passes, physical(recipe.output.symbol),
            lowerKey(recipe.terminalKey))
    }

    internal fun evaluateRecipe(
        occurrence: PositiveOccurrence,
        sourceFacts: W6bFilterSourceFactsV1,
        demands: W6bFilterDemandsV1,
        runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
    ): W6bEvaluatedFilterRecipeV1 {
        val domain = sourceFacts.copySourceDomainDeviceI32()
        val occurrenceSource = W6bRecipeSourceV1(W6bRecipeSymbolV1(0), SizeI32(domain.width(), domain.height()),
            Point2I32(domain.left, domain.top), sourceFacts.mapping, sourceFacts.copyKnownContentDeviceI32(),
            sourceFacts.copyDesiredOutputDeviceI32(), demands.copyRequiredSourceI32(), sourceFacts.copyKnownContentDeviceI32())
        val instructions = mutableListOf<W6bRecipeInstructionV1>()
        val sources = linkedMapOf(occurrenceSource.symbol to occurrenceSource)
        var nextSymbolI32 = 1
        fun resource(role: PlanResourceRole, extent: SizeI32): W6bRecipeSymbolV1 =
            W6bRecipeSymbolV1(nextSymbolI32++).also { instructions += W6bRecipeInstructionV1.Resource(it, role, extent) }
        fun appendPass(inputs: List<W6bRecipeSymbolV1>, output: W6bRecipeSymbolV1,
            key: W6bRecipeKeyV1, operation: FilterPassOperationV1) {
            instructions += W6bRecipeInstructionV1.Pass(inputs, output, key, W6bRecipeOperationV1.Fixed(operation))
        }
        fun appendSymbolicPass(inputs: List<W6bRecipeSymbolV1>, output: W6bRecipeSymbolV1,
            key: W6bRecipeKeyV1, operation: W6bRecipeOperationV1) {
            instructions += W6bRecipeInstructionV1.Pass(inputs, output, key, operation)
        }
        fun bindOutput(id: W6bRecipeSymbolV1, bounds: FilterBoundsPlanV1): W6bRecipeSourceV1 {
            val desired = bounds.copyDesiredOutputDeviceI32()
            return occurrenceSource.withSymbol(
                id, SizeI32(desired.width(), desired.height()), bounds.copyTargetOriginDeviceI32(),
                bounds.copyProducedOutputDeviceI32(), bounds.copyDesiredOutputDeviceI32(),
                bounds.copyRequiredInputDeviceI32(), bounds.copyProducedOutputDeviceI32(),
            ).also { sources[id] = it }
        }
        fun allocateTarget(bounds: FilterBoundsPlanV1): W6bRecipeSourceV1 = bindOutput(
            resource(PlanResourceRole.FilterTarget, bounds.copyDesiredOutputDeviceI32().let { SizeI32(it.width(), it.height()) }), bounds,
        )
        fun transparentBlack(source: W6bRecipeSourceV1): W6bRecipeSourceV1 {
            val id = resource(PlanResourceRole.FilterTransparentBlack, source.copyExtentI32())
            instructions += W6bRecipeInstructionV1.Clear(id, source.symbol)
            return source.withSymbol(id, knownContentDeviceI32 = null, producedOutputDeviceI32 = null).also { sources[id] = it }
        }
        fun keyFor(
            nodeId: CapturedFilterNodeIdI32?,
            maskOccurrenceI32: Int?,
            boundSource: W6bRecipeSourceV1,
            desired: RectI32,
            pictureProvenance: PictureFilterEvaluationProvenanceV1? = null,
        ): W6bRecipeKeyV1 {
            require(pictureProvenance == null || nodeId != null) {
                "Only a captured-node evaluation can carry Picture provenance."
            }
            return W6bRecipeKeyV1(nodeId, maskOccurrenceI32, boundSource, desired, pictureProvenance)
        }
        fun appendBlur(
            source: W6bRecipeSourceV1,
            sigmaXF32: Float,
            sigmaYF32: Float,
            tileMode: TileMode,
            horizontalKind: FilterImplementationKindV1,
            verticalKind: FilterImplementationKindV1,
            key: W6bRecipeKeyV1,
            demand: RectI32?,
        ): ContextualFilterResult {
            val horizontalDemand = demand?.let { expandBlurRegion(it, 0f, sigmaYF32) }
            val horizontalBounds = demandedProduction(blurBounds(source, sigmaXF32, 0f), horizontalDemand)
            val horizontal = allocateTarget(horizontalBounds)
            appendPass( listOf(source.symbol), horizontal.symbol, key,
                FilterPassOperationV1.SeparableBlur(horizontalKind, sigmaXF32, FilterAxisV1.X, tileMode,
                    horizontalBounds, filterInputSampling(source, horizontalBounds)))
            val verticalBounds = demandedProduction(blurBounds(horizontal, 0f, sigmaYF32), demand)
            val vertical = allocateTarget(verticalBounds)
            appendPass( listOf(horizontal.symbol), vertical.symbol, key,
                FilterPassOperationV1.SeparableBlur(verticalKind, sigmaYF32, FilterAxisV1.Y, tileMode,
                    verticalBounds, filterInputSampling(horizontal, verticalBounds)))
            return ContextualFilterResult(vertical, verticalBounds, key)
        }
        fun appendMorphology(
            source: W6bRecipeSourceV1,
            morphologyKind: FilterPassOperationV1.Morphology.Kind,
            radiusXF64: Double,
            radiusYF64: Double,
            key: W6bRecipeKeyV1,
            demand: RectI32?,
        ): ContextualFilterResult {
            val radii = W6cMorphologyPlanner.deviceRadii(radiusXF64, radiusYF64, source.mapping)
            fun appendAxis(input: W6bRecipeSourceV1, axis: FilterAxisV1, phaseDemand: RectI32?): ContextualFilterResult {
                val radius = if (axis == FilterAxisV1.X) radii.radiusXF64 else radii.radiusYF64
                val bounds = demandedProduction(W6cMorphologyPlanner.bounds(input, morphologyKind, axis, radius), phaseDemand)
                val output = allocateTarget(bounds)
                val kind = if (axis == FilterAxisV1.X) FilterImplementationKindV1.MORPHOLOGY_X else FilterImplementationKindV1.MORPHOLOGY_Y
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.Morphology(morphologyKind, radii.radiusXF64, radii.radiusYF64,
                        radii.radiusXTexelsI32, radii.radiusYTexelsI32, axis, bounds,
                        filterInputSampling(input, bounds), kind))
                return ContextualFilterResult(output, bounds, key)
            }
            val horizontalDemand = demand?.let { RectF64(it.left.toDouble(), it.top.toDouble(),
                it.right.toDouble(), it.bottom.toDouble()).expandSamplingHaloF64OrNull(0.0, radii.radiusYF64,
                0.0, radii.radiusYF64)?.roundOutToRectI32OrNull() ?: throw ConstructionFailure(
                W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "Morphology phase demand overflows I32.")) }
            val horizontal = appendAxis(source, FilterAxisV1.X, horizontalDemand)
            return appendAxis(horizontal.source, FilterAxisV1.Y, demand)
        }
        fun appendLighting(
            id: CapturedFilterNodeIdI32,
            input: W6bRecipeSourceV1,
            sourceForKey: W6bRecipeSourceV1,
            family: LightingFamilyV1,
            parameters: LightingParametersV1,
            demand: RectI32?,
        ): ContextualFilterResult {
            // Every lighting family can synthesize opaque/visible output from transparent black.
            // Its output therefore belongs to the consumer, while its source stays the Sobel domain.
            val bounds = demandedProduction(distantDiffuseBounds(input, sourceForKey.copyDesiredOutputDeviceI32()), demand)
            val key = keyFor(id, null, sourceForKey, bounds.copyDesiredOutputDeviceI32())
            val output = allocateTarget(bounds)
            appendPass( listOf(input.symbol), output.symbol, key,
                FilterPassOperationV1.Lighting(family, parameters, bounds, when (family) {
                    LightingFamilyV1.DISTANT_DIFFUSE -> FilterImplementationKindV1.DISTANT_DIFFUSE
                    LightingFamilyV1.POINT_DIFFUSE -> FilterImplementationKindV1.POINT_DIFFUSE
                    LightingFamilyV1.SPOT_DIFFUSE -> FilterImplementationKindV1.SPOT_DIFFUSE
                    LightingFamilyV1.DISTANT_SPECULAR -> FilterImplementationKindV1.DISTANT_SPECULAR
                    LightingFamilyV1.POINT_SPECULAR -> FilterImplementationKindV1.POINT_SPECULAR
                    LightingFamilyV1.SPOT_SPECULAR -> FilterImplementationKindV1.SPOT_SPECULAR
                }, distantDiffuseSobelSampling(input, bounds)))
            return ContextualFilterResult(output, bounds, key)
        }
        val boundResults = java.util.IdentityHashMap<W6bFilterReferenceV1, ContextualFilterResult>()
        fun resolve(reference: W6bFilterReferenceV1): ContextualFilterResult = boundResults.getOrPut(reference) {
            when (reference) {
                W6bFilterReferenceV1.Source -> ContextualFilterResult(occurrenceSource, identityBounds(occurrenceSource), null)
                is W6bFilterReferenceV1.Transparent -> transparentBlack(requireNotNull(boundResults[reference.context]) {
                    "Transparent input context must precede its consumer."
                }.source).let { ContextualFilterResult(it, identityBounds(it), null) }
                is W6bFilterReferenceV1.Result -> error("Bound operation must precede its consumer.")
            }
        }
        boundResults[W6bFilterReferenceV1.Source] = ContextualFilterResult(occurrenceSource, identityBounds(occurrenceSource), null)
        val operationResults = java.util.IdentityHashMap<W6bBoundFilterOperationV1, ContextualFilterResult>()
        val operationFacts = mutableListOf<W6bEvaluatedOperationFactsV1>()
        fun resolved(reference: W6bFilterReferenceV1): ContextualFilterResult = when (reference) {
            is W6bFilterReferenceV1.Result -> operationResults.getValue(reference.operation).also { boundResults[reference] = it }
            else -> resolve(reference)
        }
        for (bound in occurrence.topology.operations) {
            val id = bound.id
            val operationDemand = demands.copyOutputI32(bound)
            fun demanded(bounds: FilterBoundsPlanV1): FilterBoundsPlanV1 = demandedProduction(bounds, operationDemand)
            val currentSource = resolved(bound.boundSource).source.withSymbol(
                resolved(bound.boundSource).symbol,
                desiredOutputDeviceI32 = operationDemand ?: resolved(bound.boundSource).source.copyDeviceBoundsI32(),
            )
            val inputs = bound.inputs.iterator()
            fun nextInput(): ContextualFilterResult = resolved(inputs.next())
            val result = when (val node = bound.node) {

            is CapturedFilterNodeV1.Crop -> {
                val input = nextInput().source.let { it.withSymbol(it.symbol, desiredOutputDeviceI32 = operationDemand) }
                val planned = W6cSpatialBoundsPlanner.crop(input, node, id == occurrence.root?.id)
                val bounds = demanded(planned.bounds)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.Crop(planned.cropInputTargetLocalI32, node.tileMode, bounds,
                        spatialSampling(input, bounds, planned.clipOutputTargetLocalF64, 0.0, 0.0)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Offset -> {
                val input = nextInput().source
                val planned = W6cSpatialBoundsPlanner.offset(input, node)
                val bounds = demanded(planned.bounds)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.Offset(Vector2F64(planned.offsetDeviceF64X, planned.offsetDeviceF64Y), bounds,
                        spatialSampling(input, bounds, fullClip(bounds), -planned.offsetDeviceF64X, -planned.offsetDeviceF64Y)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Tile -> {
                val input = nextInput().source.let { it.withSymbol(it.symbol, desiredOutputDeviceI32 = operationDemand) }
                val planned = W6cSpatialBoundsPlanner.tile(input, node, id == occurrence.root?.id)
                val bounds = demanded(planned.bounds)
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.Tile(planned.sourceInputTargetLocalI32, bounds,
                        spatialSampling(input, bounds, planned.clipOutputTargetLocalF64, 0.0, 0.0)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Blur -> {
                val input = nextInput().source
                val full = blurBounds(input, node.sigmaX, node.sigmaY)
                val key = keyFor(id, null, currentSource, full.copyDesiredOutputDeviceI32())
                appendBlur(input, node.sigmaX, node.sigmaY, node.tileMode,
                    FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, key, operationDemand)
            }
            is CapturedFilterNodeV1.DropShadow -> {
                val input = nextInput().source
                val blurredBounds = blurBounds(input, node.sigmaX, node.sigmaY)
                val predictedBlur = W6bRecipeSourceV1(occurrenceSource.symbol,
                    SizeI32(blurredBounds.copyDesiredOutputDeviceI32().width(), blurredBounds.copyDesiredOutputDeviceI32().height()),
                    blurredBounds.copyTargetOriginDeviceI32(), occurrenceSource.mapping, blurredBounds.copyProducedOutputDeviceI32())
                val predictedShadowBounds = translatedBounds(predictedBlur, node.dx.toDouble(), node.dy.toDouble())
                val predictedShadow = predictedBlur.withSymbol(predictedBlur.symbol,
                    SizeI32(predictedShadowBounds.copyDesiredOutputDeviceI32().width(), predictedShadowBounds.copyDesiredOutputDeviceI32().height()),
                    predictedShadowBounds.copyTargetOriginDeviceI32(), predictedShadowBounds.copyProducedOutputDeviceI32())
                val key = keyFor(id, null, currentSource, dropShadowCompositeBounds(input, predictedShadow, node.mode).copyDesiredOutputDeviceI32())
                // DropShadow does not expose a public tile mode.  Skia defines its internal
                // Blur through the overload whose default is transparent DECAL sampling.
                val blurred = appendBlur(input, node.sigmaX, node.sigmaY, TileMode.DECAL,
                    FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, key,
                    operationDemand?.let { translatedRegion(it, -node.dx.toDouble(), -node.dy.toDouble()) })
                val colorBounds = demanded(translatedBounds(blurred.source, node.dx.toDouble(), node.dy.toDouble()))
                val colorized = allocateTarget(colorBounds)
                appendPass( listOf(blurred.symbol), colorized.symbol, key,
                    FilterPassOperationV1.DropShadowColorize(node.color, Vector2F64(node.dx.toDouble(), node.dy.toDouble()), colorBounds,
                        dropShadowLinearSampling(blurred.source, colorBounds, node.dx.toDouble(), node.dy.toDouble())))
                if (node.mode == CapturedDropShadowModeV1.SHADOW_ONLY) {
                    // The colored target is the terminal: there is no identity composite, target,
                    // slot, or lifetime to charge in SHADOW_ONLY.
                    ContextualFilterResult(colorized, colorBounds, key)
                } else {
                    val compositeBounds = demanded(dropShadowCompositeBounds(input, colorized, node.mode))
                    val composite = allocateTarget(compositeBounds)
                    appendSymbolicPass( listOf(colorized.symbol, input.symbol), composite.symbol, key,
                        W6bRecipeOperationV1.ShadowComposite(node.mode, input.symbol, compositeBounds,
                            targetLocalSampleOffset(colorized, compositeBounds), targetLocalSampleOffset(input, compositeBounds)))
                    ContextualFilterResult(composite, compositeBounds, key)
                }
            }
            is CapturedFilterNodeV1.ColorFilter -> {
                val input = nextInput().source
                val execution = when (val compiled = ColorFilterPlanCompilerV1.compile(node.filter)) {
                    is ColorFilterCompileResultV1.Ready -> compiled.execution
                    is ColorFilterCompileResultV1.Refused -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                        compiled.diagnosticCode, "W6c ColorFilter could not reuse the W5f numeric graph."))
                }
                val bounds = demanded(if (execution.affectsTransparentBlack) {
                    val desired = operationDemand ?: input.copyDeviceBoundsI32()
                    FilterBoundsPlanV1(input.copyKnownContentDeviceI32(), desired, input.copyDeviceBoundsI32(),
                        desired, Point2I32(desired.left, desired.top))
                } else identityBounds(input))
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.ColorFilter(execution, null, null, null, bounds, filterInputSampling(input, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Merge -> {
                // List traversal is deliberately positional: repeated and value-equal nodes are
                // evaluated as separate occurrences unless their complete evaluation facts match
                // at a future explicit cache boundary.
                val inputs = bound.inputs.map(::resolved)
                if (inputs.isEmpty()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.UnsupportedFamily, "W6c Merge requires at least one captured input."))
                val sources = inputs.map(ContextualFilterResult::source)
                val bounds = demanded(W6cMultiInputPlanner.bounds(sources))
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( inputs.map(ContextualFilterResult::symbol), output.symbol, key,
                    FilterPassOperationV1.Merge(sources.map { input -> filterInputSampling(input, bounds) }, bounds))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Blend -> {
                // The public order is background then foreground.  FinalBlendPlanner freezes the
                // exact W5 numeric/blend authority before any native materialization occurs.
                val background = nextInput()
                val foreground = nextInput()
                val bounds = demanded(W6cMultiInputPlanner.bounds(listOf(background.source, foreground.source)))
                val blend = requireNotNull(FinalBlendPlanner.plan(
                    org.graphiks.kanvas.render.ir.BlendNode.Mode(node.mode),
                    CoveragePlan.FullOrScissor,
                    SamplePlan.SingleSample,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL.blendTargetClampV1(),
                )) { "W6c Blend cannot freeze the selected W5 BlendPlan." }
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass(
                    listOf(background.symbol, foreground.symbol), output.symbol, key,
                    FilterPassOperationV1.Blend(blend, filterInputSampling(background.source, bounds),
                        filterInputSampling(foreground.source, bounds), bounds))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Dilate -> {
                val input = nextInput().source
                val key = keyFor(id, null, currentSource, input.copyDeviceBoundsI32())
                appendMorphology(input, FilterPassOperationV1.Morphology.Kind.DILATE,
                    node.radiusX.toDouble(), node.radiusY.toDouble(), key, operationDemand)
            }
            is CapturedFilterNodeV1.Erode -> {
                val input = nextInput().source
                val key = keyFor(id, null, currentSource, input.copyDeviceBoundsI32())
                appendMorphology(input, FilterPassOperationV1.Morphology.Kind.ERODE,
                    node.radiusX.toDouble(), node.radiusY.toDouble(), key, operationDemand)
            }
            is CapturedFilterNodeV1.DistantLitDiffuse -> {
                val input = nextInput().source
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
                appendLighting(id, input, currentSource, LightingFamilyV1.DISTANT_DIFFUSE,
                    LightingParametersV1.Distant(mappedDirection, node.lightColor, mappedSurfaceDepth, node.kd), operationDemand)
            }
            is CapturedFilterNodeV1.PointLitDiffuse, is CapturedFilterNodeV1.PointLitSpecular -> {
                val location = if (node is CapturedFilterNodeV1.PointLitDiffuse) node.location else (node as CapturedFilterNodeV1.PointLitSpecular).location
                val surfaceScale = if (node is CapturedFilterNodeV1.PointLitDiffuse) node.surfaceScale else (node as CapturedFilterNodeV1.PointLitSpecular).surfaceScale
                val coefficient = if (node is CapturedFilterNodeV1.PointLitDiffuse) node.kd else (node as CapturedFilterNodeV1.PointLitSpecular).ks
                val shininess = (node as? CapturedFilterNodeV1.PointLitSpecular)?.shininess
                if (!surfaceScale.isFinite() || !coefficient.isFinite() || coefficient < 0f || shininess?.isFinite() == false) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d point lighting requires finite geometry, scale, coefficient >= 0 and exponent."))
                val input = nextInput().source
                val mappedLocation = input.mapping.mapLightingPointToLayerF32OrNull(location) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d point lighting requires a finite affine layer mapping."))
                val mappedDepth = input.mapping.mapLightingZToLayerF32OrNull(surfaceScale) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d point lighting surface depth cannot be represented."))
                appendLighting(id, input, currentSource, if (shininess == null) LightingFamilyV1.POINT_DIFFUSE else LightingFamilyV1.POINT_SPECULAR,
                    LightingParametersV1.Point(mappedLocation, if (node is CapturedFilterNodeV1.PointLitDiffuse) node.lightColor else (node as CapturedFilterNodeV1.PointLitSpecular).lightColor,
                        mappedDepth, coefficient, shininess), operationDemand)
            }
            is CapturedFilterNodeV1.SpotLitDiffuse, is CapturedFilterNodeV1.SpotLitSpecular -> {
                val diffuse = node as? CapturedFilterNodeV1.SpotLitDiffuse
                val specular = node as? CapturedFilterNodeV1.SpotLitSpecular
                val location = diffuse?.location ?: requireNotNull(specular).location
                val target = diffuse?.target ?: requireNotNull(specular).target
                val scale = diffuse?.surfaceScale ?: requireNotNull(specular).surfaceScale
                val coefficient = diffuse?.kd ?: requireNotNull(specular).ks
                val exponent = diffuse?.specularExponent ?: requireNotNull(specular).specularExponent
                val cutoff = diffuse?.cutoffAngle ?: requireNotNull(specular).cutoffAngle
                val shininess = specular?.shininess
                if (!listOf(scale, coefficient, exponent, cutoff).all(Float::isFinite) || coefficient < 0f || shininess?.isFinite() == false) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d spot lighting requires finite geometry, scale, coefficients and exponents."))
                val input = nextInput().source
                val mappedLocation = input.mapping.mapLightingPointToLayerF32OrNull(location) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d spot location requires a finite affine layer mapping."))
                val mappedTarget = input.mapping.mapLightingPointToLayerF32OrNull(target) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d spot target requires a finite affine layer mapping."))
                val mappedDepth = input.mapping.mapLightingZToLayerF32OrNull(scale) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d spot surface depth cannot be represented."))
                val cutoffCosine = kotlin.math.cos(Math.toRadians(cutoff.toDouble())).toFloat()
                if (!cutoffCosine.isFinite()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                    "W6d spot cutoff cosine is not finite."))
                appendLighting(id, input, currentSource, if (specular == null) LightingFamilyV1.SPOT_DIFFUSE else LightingFamilyV1.SPOT_SPECULAR,
                    LightingParametersV1.Spot(mappedLocation, mappedTarget,
                        input.mapping.normalizedLightingDirectionF32OrNull(mappedLocation, mappedTarget) ?: throw ConstructionFailure(
                            W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping,
                                "W6d spot direction cannot be represented by the sealed layer mapping."),
                        ), exponent,
                        cutoffCosine, diffuse?.lightColor ?: requireNotNull(specular).lightColor, mappedDepth, coefficient, shininess), operationDemand)
            }
            is CapturedFilterNodeV1.DistantLitSpecular -> {
                val input = nextInput().source
                if (!node.direction.x.isFinite() || !node.direction.y.isFinite() || !node.direction.z.isFinite() ||
                    !node.surfaceScale.isFinite() || !node.ks.isFinite() || node.ks < 0f || !node.shininess.isFinite()) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d distant specular requires finite parameters and ks >= 0."))
                val direction = input.mapping.mapLightingVectorToLayerF32OrNull(node.direction) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d distant specular requires a finite affine layer mapping."))
                val depth = input.mapping.mapLightingZToLayerF32OrNull(node.surfaceScale) ?: throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedLightingMapping, "W6d distant specular surface depth cannot be represented."))
                appendLighting(id, input, currentSource, LightingFamilyV1.DISTANT_SPECULAR,
                    LightingParametersV1.Distant(direction, node.lightColor, depth, node.ks, node.shininess), operationDemand)
            }
            is CapturedFilterNodeV1.MatrixConvolution -> {
                val input = nextInput().source
                val width = exactPositiveI32(node.kernelSize.width, "matrix kernel width")
                val height = exactPositiveI32(node.kernelSize.height, "matrix kernel height")
                if (node.kernel.sizeI32 != Math.multiplyExact(width, height)) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d matrix kernel count is inconsistent."))
                val offsetX = node.kernelOffset.x.toDouble()
                val offsetY = node.kernelOffset.y.toDouble()
                if (!offsetX.isFinite() || !offsetY.isFinite()) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d matrix kernel offset is non-finite."))
                val bounds = demanded(matrixBounds(input, width, height, offsetX, offsetY))
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.MatrixConvolution(SizeI32(width, height), node.kernel, node.gain, node.bias,
                        Vector2F64(offsetX, offsetY), node.tileMode, node.convolveAlpha, bounds))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.DisplacementMap -> {
                val displacement = nextInput().source
                val input = nextInput().source
                if (!node.scale.isFinite()) throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d displacement scale is non-finite."))
                val bounds = demanded(samplingBounds(input))
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(displacement.symbol, input.symbol), output.symbol, key,
                    FilterPassOperationV1.DisplacementMap(node.xChannelSelector, node.yChannelSelector, node.scale, bounds,
                        filterInputSampling(displacement, bounds), filterInputSampling(input, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Magnifier -> {
                val input = nextInput().source
                val source = node.copySource()
                if (!node.zoom.isFinite() || node.zoom <= 0f || !node.inset.isFinite() || node.inset < 0f ||
                    !source.left.isFinite() || !source.top.isFinite() || !source.right.isFinite() || !source.bottom.isFinite() || source.isEmpty) {
                    throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6d magnifier has invalid lens geometry."))
                }
                val bounds = demanded(samplingBounds(input))
                val mappedSource = input.mapping.mapLocalRectToDeviceF64OrNull(RectF64(
                    source.left.toDouble(), source.top.toDouble(), source.right.toDouble(), source.bottom.toDouble(),
                )) ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                    W6bFilterDiagnostics.InvalidBounds, "W6d magnifier source cannot be mapped to finite device coordinates."))
                val outputOrigin = bounds.copyTargetOriginDeviceI32()
                val sourceTargetLocal = RectF64(
                    mappedSource.left - outputOrigin.x.toDouble(), mappedSource.top - outputOrigin.y.toDouble(),
                    mappedSource.right - outputOrigin.x.toDouble(), mappedSource.bottom - outputOrigin.y.toDouble(),
                )
                if (!sourceTargetLocal.isFinite() || sourceTargetLocal.isEmpty) throw ConstructionFailure(
                    W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                        "W6d magnifier source cannot be represented in frozen output-local coordinates."),
                )
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                appendPass( listOf(input.symbol), output.symbol, key,
                    FilterPassOperationV1.Magnifier(sourceTargetLocal, node.zoom, node.inset, bounds,
                        filterInputSampling(input, bounds)))
                ContextualFilterResult(output, bounds, key)
            }
            is CapturedFilterNodeV1.Picture -> {
                val prepared = requireNotNull(sourceFacts.preparePicture) {
                    "Picture recipe requires its W6a semantic draft resolver."
                }(bound, currentSource)
                val provenance = PictureFilterEvaluationProvenanceV1.of(occurrence.table.canonicalId.value, occurrence.idI32)
                if (prepared == null) {
                    val transparent = transparentBlack(currentSource)
                    val bounds = demanded(identityBounds(transparent))
                    val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32(), provenance)
                    val output = allocateTarget(bounds)
                    appendPass(listOf(transparent.symbol), output.symbol, key,
                        FilterPassOperationV1.Offset(Vector2F64(0.0, 0.0), bounds,
                            spatialSampling(transparent, bounds, fullClip(bounds), 0.0, 0.0)))
                    ContextualFilterResult(output, bounds, key)
                } else {
                    val geometry = prepared.source
                    val picture = W6bRecipeSourceV1(W6bRecipeSymbolV1(nextSymbolI32++), geometry.copyExtentI32(),
                        geometry.originDeviceI32, geometry.mapping, geometry.copyKnownContentDeviceI32(),
                        geometry.copyDesiredOutputDeviceI32(), geometry.copyRequiredInputDeviceI32(),
                        geometry.copyProducedOutputDeviceI32())
                    sources[picture.symbol] = picture
                    instructions += W6bRecipeInstructionV1.PictureSource(picture, currentSource.symbol, prepared)
                    val cropped = picture.copyKnownContentDeviceI32()?.let { known ->
                        prepared.copyContentDeviceF64().roundOutToRectI32OrNull()?.let { intersectI32(known, it) }
                    }
                    val bounds = demanded(identityBounds(picture.withSymbol(picture.symbol,
                        knownContentDeviceI32 = cropped, producedOutputDeviceI32 = cropped)))
                    val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32(), provenance)
                    val output = allocateTarget(bounds)
                    val inputSampling = filterInputSampling(picture, bounds)
                    val sampling = W6dPictureSamplingV1.ofOrNull(inputSampling, prepared.copyContentDeviceF64(), bounds)
                        ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                            "W6d Picture crop cannot be represented by frozen native F32 sampling coordinates."))
                    appendSymbolicPass(listOf(picture.symbol), output.symbol, key,
                        W6bRecipeOperationV1.Picture(picture.symbol, bounds, sampling, inputSampling, prepared))
                    ContextualFilterResult(output, bounds, key)
                }
            }
            is CapturedFilterNodeV1.RuntimeEffect -> {
                val entry = runtimeCatalog.find(
                    node.descriptor.id,
                    node.descriptor.semanticVersionI32,
                    node.descriptor.abiHash,
                ) ?: throw ConstructionFailure(W6dPlanDiagnostics.refusal(
                    W6dPlanDiagnostics.RuntimeEffectNotRegistered,
                    "Runtime image filter ${node.descriptor.id.value}@${node.descriptor.semanticVersionI32} is not registered.",
                ))
                if (entry.semanticKind != RuntimeEffectSemanticKindV1.IMAGE_OPACITY ||
                    entry.descriptor != node.descriptor ||
                    node.descriptor.abi != org.graphiks.kanvas.render.ir.RuntimeEffectAbi.IMAGE_FILTER
                ) throw ConstructionFailure(W6dPlanDiagnostics.refusal(
                    W6dPlanDiagnostics.RuntimeEffectAbiUnsupported,
                    "Runtime image filter ${node.descriptor.id.value} does not have the IMAGE_FILTER image-opacity ABI.",
                ))
                val alpha = node.uniforms()["alpha"] as? org.graphiks.kanvas.render.ir.RuntimeUniformValue.F1
                if (alpha == null || !alpha.value.isFinite() || alpha.value !in 0f..1f) {
                    throw ConstructionFailure(W6dPlanDiagnostics.refusal(
                        W6dPlanDiagnostics.RuntimeEffectInvalidBinding,
                        "Runtime image opacity requires finite alpha in [0, 1].",
                    ))
                }
                val source = nextInput().source
                val bounds = demanded(identityBounds(if (alpha.value == 0f) source.withSymbol(source.symbol,
                    knownContentDeviceI32 = null, producedOutputDeviceI32 = null) else source))
                val key = keyFor(id, null, currentSource, bounds.copyDesiredOutputDeviceI32())
                val output = allocateTarget(bounds)
                val uniform = requireNotNull(entry.descriptor.uniformBlock.slots.singleOrNull { it.name == "alpha" })
                appendPass( listOf(source.symbol), output.symbol, key,
                    FilterPassOperationV1.RuntimeImageOpacity(
                        entry.descriptor,
                        alpha.value,
                        uniform.offsetBytesI32,
                        bounds,
                        filterInputSampling(source, bounds),
                    ))
                ContextualFilterResult(output, bounds, key)
            }
            else -> throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.UnsupportedFamily, "The captured image-filter family belongs to W6c or W6d.",
            ))
            }
            operationResults[bound] = result
            val known = bound.inputs.mapNotNull { resolved(it).source.copyKnownContentDeviceI32() }.reduceOrNull { a, b ->
                RectI32(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            }
            operationFacts += W6bEvaluatedOperationFactsV1(id, known, operationDemand,
                demands.copyInputI32(bound), result.source.copyProducedOutputDeviceI32(), demands.copyInputDemandsI32(bound))
        }
        val terminal = resolved(occurrence.topology.terminal)
        return W6bEvaluatedFilterRecipeV1(demands.copyRequiredSourceI32(), terminal.source,
            requireNotNull(terminal.evaluationKey), instructions, sources, operationFacts)
    }

    /** Freezes raw-coverage mask work before W5 material/color evaluation. */
    internal fun freezeMaskOccurrence(
        occurrence: PositiveOccurrence,
        coverageSource: SourceBinding,
        cursor: FreezeCursor,
        evaluated: W6bEvaluatedFilterRecipeV1,
    ): FrozenMask {
        val recipe = evaluated
        val frozen = lowerRecipe(recipe, coverageSource, cursor, null, null)
        return FrozenMask(frozen.resourceSpecs(), frozen.passes(), frozen.output)
    }

    internal fun evaluateMaskRecipe(
        occurrence: PositiveOccurrence,
        sourceFacts: W6bFilterSourceFactsV1,
    ): W6bEvaluatedFilterRecipeV1 =
        prepareMaskRecipe(occurrence, sourceFacts).evaluate(sourceFacts.copyKnownContentDeviceI32())

    /** Select mask geometry once, then seal initialized regions after its W4 coverage is prepared. */
    internal fun prepareMaskRecipe(
        occurrence: PositiveOccurrence,
        sourceFacts: W6bFilterSourceFactsV1,
    ): W6bPreparedMaskRecipeV1 {
        val mask = requireNotNull(occurrence.mask) { "W6b mask recipe requires a captured mask." }
        val domain = sourceFacts.copySourceDomainDeviceI32()
        val coverage = W6bRecipeSourceV1(W6bRecipeSymbolV1(0), SizeI32(domain.width(), domain.height()),
            Point2I32(domain.left, domain.top), sourceFacts.mapping, null,
            sourceFacts.copyDesiredOutputDeviceI32(), domain, null)
        val sourceGeometry = linkedMapOf(coverage.symbol to coverage)
        val content = linkedMapOf<W6bRecipeSymbolV1, (RectI32?) -> RectI32?>(coverage.symbol to { it?.copy() })
        val builders = mutableListOf<(Map<W6bRecipeSymbolV1, W6bRecipeSourceV1>) -> W6bRecipeInstructionV1>()
        var nextSymbolI32 = 1
        fun fixed(instruction: W6bRecipeInstructionV1) { builders += { instruction } }
        fun resource(role: PlanResourceRole, extent: SizeI32): W6bRecipeSymbolV1 =
            W6bRecipeSymbolV1(nextSymbolI32++).also { fixed(W6bRecipeInstructionV1.Resource(it, role, extent)) }
        fun target(bounds: FilterBoundsPlanV1, produced: (RectI32?) -> RectI32?): W6bRecipeSourceV1 {
            val desired = bounds.copyDesiredOutputDeviceI32()
            val extent = SizeI32(desired.width(), desired.height())
            val symbol = resource(PlanResourceRole.FilterTarget, extent)
            content[symbol] = produced
            return coverage.withSymbol(symbol, extent, bounds.copyTargetOriginDeviceI32(), null,
                desired, bounds.copyRequiredInputDeviceI32(), null).also { sourceGeometry[symbol] = it }
        }
        fun key(desired: RectI32) = W6bRecipeKeyV1(null, occurrence.maskOccurrenceI32, coverage, desired, null)
        fun sealedBounds(geometry: FilterBoundsPlanV1, input: W6bRecipeSourceV1,
            output: W6bRecipeSourceV1): FilterBoundsPlanV1 = FilterBoundsPlanV1(
            input.copyKnownContentDeviceI32(), geometry.copyDesiredOutputDeviceI32(),
            geometry.copyRequiredInputDeviceI32(), output.copyProducedOutputDeviceI32(),
            geometry.copyTargetOriginDeviceI32())
        fun blur(source: W6bRecipeSourceV1, sigma: Float, evaluationKey: W6bRecipeKeyV1): W6bRecipeSourceV1 {
            fun axis(input: W6bRecipeSourceV1, axis: FilterAxisV1): W6bRecipeSourceV1 {
                val x = if (axis == FilterAxisV1.X) sigma else 0f
                val y = if (axis == FilterAxisV1.Y) sigma else 0f
                val geometry = blurBounds(input, x, y)
                val incoming = content.getValue(input.symbol)
                val output = target(geometry) { known ->
                    incoming(known)?.let { expandBlurRegion(it, x, y) }
                }
                val sampling = filterInputSampling(input, geometry)
                builders += { sources ->
                    val bounds = sealedBounds(geometry, sources.getValue(input.symbol), sources.getValue(output.symbol))
                    W6bRecipeInstructionV1.Pass(listOf(input.symbol), output.symbol, evaluationKey,
                        W6bRecipeOperationV1.Fixed(FilterPassOperationV1.SeparableBlur(
                            if (axis == FilterAxisV1.X) FilterImplementationKindV1.MASK_COVERAGE_BLUR_X
                            else FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y,
                            sigma, axis, TileMode.CLAMP, bounds, sampling)))
                }
                return output
            }
            return axis(axis(source, FilterAxisV1.X), FilterAxisV1.Y)
        }
        val output = when (mask) {
            is MaskFilterNode.Blur -> {
                val original = if (mask.style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL) null else {
                    val symbol = resource(PlanResourceRole.CoverageOriginal, coverage.copyExtentI32())
                    content[symbol] = content.getValue(coverage.symbol)
                    fixed(W6bRecipeInstructionV1.RetainCoverage(coverage.symbol, symbol,
                        filterInputSampling(coverage, identityBounds(coverage))))
                    coverage.withSymbol(symbol).also { sourceGeometry[symbol] = it }
                }
                val evaluationKey = key(blurBounds(coverage, mask.sigma, mask.sigma).copyDesiredOutputDeviceI32())
                val blurred = blur(coverage, mask.sigma, evaluationKey)
                val geometry = identityBounds(blurred)
                val blurredContent = content.getValue(blurred.symbol)
                val styled = target(geometry) { known ->
                    val produced = blurredContent(known)
                    if (mask.style == org.graphiks.kanvas.render.ir.MaskBlurStyle.INNER)
                        known?.let { raw -> produced?.let { intersectI32(raw, it) } }
                    else produced
                }
                val blurredSampling = filterInputSampling(blurred, geometry)
                val originalSampling = original?.let { filterInputSampling(it, geometry) }
                val inputs = listOfNotNull(blurred.symbol, original?.symbol)
                builders += { sources -> W6bRecipeInstructionV1.Pass(inputs, styled.symbol, evaluationKey,
                    W6bRecipeOperationV1.MaskStyle(mask.style, original?.symbol, blurred.symbol,
                        sealedBounds(geometry, sources.getValue(blurred.symbol), sources.getValue(styled.symbol)),
                        blurredSampling, originalSampling)) }
                styled
            }
            is MaskFilterNode.Shader -> {
                val geometry = identityBounds(coverage)
                val evaluationKey = key(geometry.copyDesiredOutputDeviceI32())
                val output = target(geometry, content.getValue(coverage.symbol))
                val sampling = filterInputSampling(coverage, geometry)
                builders += { sources -> W6bRecipeInstructionV1.Pass(listOf(coverage.symbol), output.symbol, evaluationKey,
                    W6bRecipeOperationV1.Fixed(FilterPassOperationV1.MaskShader(
                        FilterPassOperationV1.MaskShaderMaterialBindingV1.CapturedOccurrence(occurrence.idI32, mask.material),
                        sealedBounds(geometry, sources.getValue(coverage.symbol), sources.getValue(output.symbol)), sampling))) }
                output
            }
            is MaskFilterNode.Table -> {
                val geometry = identityBounds(coverage)
                val evaluationKey = key(geometry.copyDesiredOutputDeviceI32())
                val output = target(geometry, content.getValue(coverage.symbol))
                val table = W6bRecipeSymbolV1(nextSymbolI32++)
                fixed(W6bRecipeInstructionV1.MaskTableResource(table))
                val sampling = filterInputSampling(coverage, geometry)
                builders += { sources -> W6bRecipeInstructionV1.Pass(listOf(coverage.symbol), output.symbol, evaluationKey,
                    W6bRecipeOperationV1.MaskTable(mask.table, table, occurrence.maskOccurrenceI32,
                        sealedBounds(geometry, sources.getValue(coverage.symbol), sources.getValue(output.symbol)), sampling)) }
                output
            }
        }
        val frozenGeometry = sourceGeometry.toMap()
        val frozenContent = content.toMap()
        val frozenBuilders = builders.toList()
        return W6bPreparedMaskRecipeV1(output) { known ->
            val sources = frozenGeometry.mapValues { (symbol, geometry) ->
                val initialized = frozenContent.getValue(symbol)(known)
                geometry.withSymbol(symbol, knownContentDeviceI32 = initialized, producedOutputDeviceI32 = initialized)
            }
            val instructions = frozenBuilders.map { it(sources) }
            W6bEvaluatedFilterRecipeV1(domain, sources.getValue(output.symbol),
                instructions.filterIsInstance<W6bRecipeInstructionV1.Pass>().last().key, instructions, sources)
        }
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

    private fun identityBounds(source: W6bSourceGeometryV1): FilterBoundsPlanV1 {
        val domain = source.copyDeviceBoundsI32()
        val desired = domain
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), desired,
            source.copyRequiredInputDeviceI32() ?: domain,
            source.copyProducedOutputDeviceI32() ?: source.copyKnownContentDeviceI32(),
            source.originDeviceI32)
    }

    /** Sampling filters preserve their content-sized output while reverse demand grows only the input. */
    private fun samplingBounds(source: W6bSourceGeometryV1): FilterBoundsPlanV1 {
        val domain = source.copyDeviceBoundsI32()
        val desired = source.copyKnownContentDeviceI32() ?: source.copyDesiredOutputDeviceI32() ?: domain
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), desired,
            source.copyRequiredInputDeviceI32() ?: domain,
            source.copyProducedOutputDeviceI32() ?: source.copyKnownContentDeviceI32(),
            Point2I32(desired.left, desired.top))
    }

    internal fun exactPositiveI32(value: Float, label: String): Int {
        if (!value.isFinite() || value <= 0f || value != value.toLong().toFloat()) throw ConstructionFailure(
            W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds, "W6d $label is not a positive integral F32."))
        return try { Math.toIntExact(value.toLong()) } catch (_: ArithmeticException) {
            throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6d $label overflows I32."))
        }
    }

    private fun matrixBounds(source: W6bSourceGeometryV1, width: Int, height: Int, offsetX: Double, offsetY: Double): FilterBoundsPlanV1 {
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
    private fun distantDiffuseBounds(source: W6bSourceGeometryV1, consumerDemand: RectI32?): FilterBoundsPlanV1 {
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
    private fun distantDiffuseSobelSampling(input: W6bSourceGeometryV1, bounds: FilterBoundsPlanV1): W6dSobelSamplingV1 {
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

    /** Compatibility entry point; contextual recipe demand is the only inverse authority. */
    internal fun reverseInputDemand(
        occurrence: PositiveOccurrence?,
        desired: RectI32,
        mapping: LayerMappingF64? = null,
    ): RectI32? = if (occurrence == null) desired.copy() else
        W6bFilterDemandsV1(occurrence.topology, occurrence.mask, desired, mapping).copyRequiredSourceI32()

    internal fun sobelRequiredInput(output: RectI32): RectI32 = RectF64(
        output.left.toDouble(), output.top.toDouble(), output.right.toDouble(), output.bottom.toDouble(),
    ).expandSamplingHaloF64OrNull(1.0, 1.0, 1.0, 1.0)?.roundOutToRectI32OrNull()
        ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
            "W6d distant diffuse reverse Sobel demand cannot be represented in checked I32 texels."))

    private fun expandBlurRegion(input: RectI32, sigmaXF32: Float, sigmaYF32: Float): RectI32 =
        RectF64(input.left.toDouble(), input.top.toDouble(), input.right.toDouble(), input.bottom.toDouble())
            .expandForBlurF64OrNull(sigmaXF32, sigmaYF32)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6b blur expansion cannot be represented in checked I32 texels."))

    private fun intersectI32(first: RectI32, second: RectI32): RectI32? =
        RectI32(maxOf(first.left, second.left), maxOf(first.top, second.top),
            minOf(first.right, second.right), minOf(first.bottom, second.bottom)).takeUnless(RectI32::isEmpty)

    /** Keep the frozen sampling target distinct from the consumer's demanded production. */
    private fun demandedProduction(bounds: FilterBoundsPlanV1, demand: RectI32?): FilterBoundsPlanV1 =
        FilterBoundsPlanV1(bounds.copyKnownContentDeviceI32(), bounds.copyDesiredOutputDeviceI32(),
            bounds.copyRequiredInputDeviceI32(), demand?.let { requested ->
                bounds.copyProducedOutputDeviceI32()?.let { intersectI32(it, requested) }
            }, bounds.copyTargetOriginDeviceI32())

    private fun translatedRegion(input: RectI32, dxF64: Double, dyF64: Double): RectI32 =
        RectF64(input.left.toDouble(), input.top.toDouble(), input.right.toDouble(), input.bottom.toDouble())
            .translateF64OrNull(dxF64, dyF64)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(W6bFilterDiagnostics.InvalidBounds,
                "W6b translated demand cannot be represented in checked I32 texels."))

    private fun blurBounds(source: W6bSourceGeometryV1, sigmaXF32: Float, sigmaYF32: Float): FilterBoundsPlanV1 {
        val input = source.copyDeviceBoundsI32()
        val desired = RectF64(input.left.toDouble(), input.top.toDouble(), input.right.toDouble(), input.bottom.toDouble())
            .expandForBlurF64OrNull(sigmaXF32, sigmaYF32)?.roundOutToRectI32OrNull()
            ?: throw ConstructionFailure(W6bFilterDiagnostics.refusal(
                W6bFilterDiagnostics.InvalidBounds, "W6b blur expansion cannot be represented in checked I32 texels."))
        return FilterBoundsPlanV1(source.copyKnownContentDeviceI32(), desired, input,
            source.copyKnownContentDeviceI32()?.let { expandBlurRegion(it, sigmaXF32, sigmaYF32) },
            Point2I32(desired.left, desired.top))
    }

    private fun translatedBounds(source: W6bSourceGeometryV1, dxF64: Double, dyF64: Double): FilterBoundsPlanV1 {
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
        input: W6bSourceGeometryV1,
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
    private fun targetLocalSampleOffset(input: W6bSourceGeometryV1, outputBounds: FilterBoundsPlanV1): Point2I32 {
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
    private fun filterInputSampling(input: W6bSourceGeometryV1, outputBounds: FilterBoundsPlanV1): FilterInputSamplingV1 {
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
        input: W6bSourceGeometryV1,
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

    private fun dropShadowCompositeBounds(input: W6bSourceGeometryV1, shadow: W6bSourceGeometryV1, mode: CapturedDropShadowModeV1): FilterBoundsPlanV1 {
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
        val bounded = visitScenes(scene) { nestedScene, command, _, _, _, _, _ ->
            when (command) {
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
            }
            0
        }
        return Ownership(roots, mask, backdrop, filteredPrevious, bounded, invalidMaskTableLengthI32)
    }

    /**
     * Visits both geometry Pictures and the scene carried by an admitted captured Picture node.
     *
     * A captured Picture is an execution occurrence, not a canonical-scene cache key: two equal
     * scene values may be live on separate branches.  The active stack therefore uses object
     * identity and is unwound in `finally`; canonical IDs remain only provenance on the emitted
     * occurrence.  The source evaluation identity and captured node ID join the source path so
     * a nested filter binds the exact owner that froze it, even when one filter object is reused.
     */
    private fun visitScenes(root: SceneSnapshot,
        visit: (SceneSnapshot, SceneCommand, Int, Int, Boolean, List<org.graphiks.kanvas.render.ir.DrawNode>, List<Int>) -> Int): Boolean {
        var visitedCommandsI32 = 0
        val activeScenes = mutableListOf<SceneSnapshot>()

        fun active(scene: SceneSnapshot): Boolean = activeScenes.any { it === scene }

        fun inputs(node: CapturedFilterNodeV1): List<CapturedFilterInputV1> = when (node) {
            is CapturedFilterNodeV1.Crop -> listOf(node.input)
            is CapturedFilterNodeV1.Blur -> listOf(node.input)
            is CapturedFilterNodeV1.DropShadow -> listOf(node.input)
            is CapturedFilterNodeV1.ColorFilter -> listOf(node.input)
            is CapturedFilterNodeV1.Compose -> listOf(node.outer, node.inner)
            is CapturedFilterNodeV1.Blend -> listOf(node.background, node.foreground)
            is CapturedFilterNodeV1.Dilate -> listOf(node.input)
            is CapturedFilterNodeV1.Erode -> listOf(node.input)
            is CapturedFilterNodeV1.DistantLitDiffuse -> listOf(node.input)
            is CapturedFilterNodeV1.PointLitDiffuse -> listOf(node.input)
            is CapturedFilterNodeV1.SpotLitDiffuse -> listOf(node.input)
            is CapturedFilterNodeV1.DistantLitSpecular -> listOf(node.input)
            is CapturedFilterNodeV1.PointLitSpecular -> listOf(node.input)
            is CapturedFilterNodeV1.SpotLitSpecular -> listOf(node.input)
            is CapturedFilterNodeV1.Offset -> listOf(node.input)
            is CapturedFilterNodeV1.Tile -> listOf(node.input)
            is CapturedFilterNodeV1.Merge -> node.toList()
            is CapturedFilterNodeV1.DisplacementMap -> listOf(node.displacement, node.input)
            is CapturedFilterNodeV1.Picture -> emptyList()
            is CapturedFilterNodeV1.Magnifier -> listOf(node.input)
            is CapturedFilterNodeV1.MatrixConvolution -> listOf(node.input)
            is CapturedFilterNodeV1.RuntimeEffect -> node.map { it.input }
        }

        fun roots(scene: SceneSnapshot, command: SceneCommand): List<CapturedFilterRootV1> = when (command) {
            is SceneCommand.Draw -> listOfNotNull(filterPayload(command.node.paint, command.node.effects).root)
            is SceneCommand.BeginLayer -> filterPayload(command.descriptor.paint, command.descriptor.effects).let { payload ->
                buildList {
                    payload.root?.let(::add)
                    filterPayload(null, command.descriptor.backdrop).root?.let(::add)
                }
            }
            else -> emptyList()
        }

        fun visitOrdered(
            scene: SceneSnapshot,
            depthI32: Int,
            insertionCommandIndexI32: Int?,
            outerPictures: List<org.graphiks.kanvas.render.ir.DrawNode>,
            picturePathI32: List<Int>,
        ): Boolean {
            if (depthI32 > root.graphLimits.maxDepth || active(scene)) return true
            activeScenes += scene
            try {
                scene.toList().forEachIndexed { indexI32, command ->
                    visitedCommandsI32 = try { Math.addExact(visitedCommandsI32, 1) } catch (_: ArithmeticException) { return true }
                    if (visitedCommandsI32 > root.graphLimits.maxNodes) return true
                    val insertion = insertionCommandIndexI32 ?: indexI32
                    val evaluationIdentityI32 = visit(scene, command, insertion, indexI32,
                        insertionCommandIndexI32 != null, outerPictures, picturePathI32)
                    val geometryPicture = (command as? SceneCommand.Draw)?.node?.geometry as? GeometryNode.Picture
                    if (geometryPicture != null && visitOrdered(geometryPicture.scene, Math.addExact(depthI32, 1), insertion,
                            outerPictures + command.node, picturePathI32 + indexI32)) return true

                    val nestedOuter = (command as? SceneCommand.Draw)?.let { outerPictures + it.node } ?: outerPictures
                    roots(scene, command).forEach { capturedRoot ->
                        val seen = BooleanArray(scene.filterTable.nodeCount)
                        fun visitNode(id: CapturedFilterNodeIdI32): Boolean {
                            if (id.valueI32 !in seen.indices || seen[id.valueI32]) return false
                            seen[id.valueI32] = true
                            val node = scene.filterTable.nodeAt(id)
                            if (node is CapturedFilterNodeV1.Picture &&
                                visitOrdered(node.scene, Math.addExact(depthI32, 1), insertion,
                                    nestedOuter, picturePathI32 + evaluationIdentityI32 + id.valueI32)) return true
                            return inputs(node).filterIsInstance<CapturedFilterInputV1.Node>().any { child -> visitNode(child.id) }
                        }
                        if (visitNode(capturedRoot.id)) return true
                    }
                }
                return false
            } finally {
                check(activeScenes.removeAt(activeScenes.lastIndex) === scene)
            }
        }
        return visitOrdered(root, 1, null, emptyList(), emptyList())
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
        val hasW6dAdvanced: Boolean get() = roots.any { root ->
            val pending = ArrayDeque<CapturedFilterNodeIdI32>()
            pending.addLast(root.root.id)
            val seen = BooleanArray(root.table.nodeCount)
            while (pending.isNotEmpty()) {
                val id = pending.removeLast()
                if (id.valueI32 !in seen.indices || seen[id.valueI32]) continue
                seen[id.valueI32] = true
                when (val node = root.table.nodeAt(id)) {
                    is CapturedFilterNodeV1.MatrixConvolution,
                    is CapturedFilterNodeV1.DisplacementMap,
                    is CapturedFilterNodeV1.Magnifier,
                    is CapturedFilterNodeV1.DistantLitDiffuse,
                    is CapturedFilterNodeV1.PointLitDiffuse,
                    is CapturedFilterNodeV1.SpotLitDiffuse,
                    is CapturedFilterNodeV1.DistantLitSpecular,
                    is CapturedFilterNodeV1.PointLitSpecular,
                    is CapturedFilterNodeV1.SpotLitSpecular,
                    is CapturedFilterNodeV1.Picture,
                    is CapturedFilterNodeV1.RuntimeEffect,
                    -> return true
                    else -> appendNodeInputs(node, pending)
                }
            }
            false
        }

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
                    is CapturedFilterNodeV1.Picture -> Unit
                    is CapturedFilterNodeV1.Magnifier -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.DistantLitDiffuse -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.PointLitDiffuse -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.SpotLitDiffuse -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.DistantLitSpecular -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.PointLitSpecular -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.SpotLitSpecular -> node.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    is CapturedFilterNodeV1.RuntimeEffect -> node.forEach { child ->
                        child.input.enqueueNodeOrUnsupported(pending)?.let { return "W6d" }
                    }
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

    private fun appendNodeInputs(node: CapturedFilterNodeV1, pending: ArrayDeque<CapturedFilterNodeIdI32>) {
        val inputs: List<CapturedFilterInputV1> = when (node) {
            is CapturedFilterNodeV1.Crop -> listOf(node.input)
            is CapturedFilterNodeV1.Blur -> listOf(node.input)
            is CapturedFilterNodeV1.DropShadow -> listOf(node.input)
            is CapturedFilterNodeV1.ColorFilter -> listOf(node.input)
            is CapturedFilterNodeV1.Compose -> listOf(node.outer, node.inner)
            is CapturedFilterNodeV1.Blend -> listOf(node.background, node.foreground)
            is CapturedFilterNodeV1.Dilate -> listOf(node.input)
            is CapturedFilterNodeV1.Erode -> listOf(node.input)
            is CapturedFilterNodeV1.DistantLitDiffuse -> listOf(node.input)
            is CapturedFilterNodeV1.PointLitDiffuse -> listOf(node.input)
            is CapturedFilterNodeV1.SpotLitDiffuse -> listOf(node.input)
            is CapturedFilterNodeV1.DistantLitSpecular -> listOf(node.input)
            is CapturedFilterNodeV1.PointLitSpecular -> listOf(node.input)
            is CapturedFilterNodeV1.SpotLitSpecular -> listOf(node.input)
            is CapturedFilterNodeV1.Offset -> listOf(node.input)
            is CapturedFilterNodeV1.Tile -> listOf(node.input)
            is CapturedFilterNodeV1.Merge -> node.toList()
            is CapturedFilterNodeV1.DisplacementMap -> listOf(node.displacement, node.input)
            is CapturedFilterNodeV1.Picture -> emptyList()
            is CapturedFilterNodeV1.Magnifier -> listOf(node.input)
            is CapturedFilterNodeV1.MatrixConvolution -> listOf(node.input)
            is CapturedFilterNodeV1.RuntimeEffect -> node.map { it.input }
        }
        inputs.filterIsInstance<CapturedFilterInputV1.Node>().forEach { pending.addLast(it.id) }
    }
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
