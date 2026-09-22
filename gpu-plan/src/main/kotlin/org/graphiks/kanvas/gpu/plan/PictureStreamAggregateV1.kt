package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F32

/** A frame-local identity; canonical Picture identities are deliberately not execution keys. */
@JvmInline
public value class PictureStreamAggregateIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) { "Picture stream aggregate IDs must be non-negative." } }
}

/** A monotonically assigned entry identity within one [PictureStreamAggregateV1]. */
@JvmInline
public value class PictureStreamEntryIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) { "Picture stream entry IDs must be non-negative." } }
}

/** A unique frame-wide execution identity, distinct from a scene-local command index. */
@JvmInline
public value class FramePlannedCommandIdI32(public val valueI32: Int) {
    init { require(valueI32 >= 0) { "Planned command IDs must be non-negative." } }
}

/** Locates one command in one *occurrence* of a captured Picture stream. */
public class PictureSourceLocatorV1 internal constructor(
    public val pictureOccurrenceIdI32: Int,
    public val sourceCommandIndexI32: Int,
) {
    init {
        require(pictureOccurrenceIdI32 >= 0 && sourceCommandIndexI32 >= 0) {
            "Picture source locators require non-negative occurrence and command IDs."
        }
    }

    override fun equals(other: Any?): Boolean = other is PictureSourceLocatorV1 &&
        pictureOccurrenceIdI32 == other.pictureOccurrenceIdI32 && sourceCommandIndexI32 == other.sourceCommandIndexI32
    override fun hashCode(): Int = 31 * pictureOccurrenceIdI32 + sourceCommandIndexI32
    override fun toString(): String = "PictureSourceLocatorV1($pictureOccurrenceIdI32,$sourceCommandIndexI32)"
}

/** The mode is selected by gpu-plan and is never an executor optimization decision. */
public enum class PictureStreamExecutionModeV1 {
    INLINE_CURRENT_TARGET,
    ISOLATED_SOURCE,
}

/** The four W6 regions stay separate even when a conservative plan gives two equal values. */
public class PictureStreamRegionsV1 internal constructor(
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    requiredInputDeviceI32: RectI32,
    producedOutputDeviceI32: RectI32?,
) {
    private val knownContent = knownContentDeviceI32?.copy()
    private val desiredOutput = desiredOutputDeviceI32.copy()
    private val requiredInput = requiredInputDeviceI32.copy()
    private val producedOutput = producedOutputDeviceI32?.copy()

    init {
        require(!desiredOutput.isEmpty && !requiredInput.isEmpty)
        require(knownContent?.isEmpty != true && producedOutput?.isEmpty != true)
    }

    public fun copyKnownContentDeviceI32(): RectI32? = knownContent?.copy()
    public fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutput.copy()
    public fun copyRequiredInputDeviceI32(): RectI32 = requiredInput.copy()
    public fun copyProducedOutputDeviceI32(): RectI32? = producedOutput?.copy()
}

/**
 * The renderer-facing W5 bridge for a premultiplied graph texture.  It contains no SceneSnapshot
 * or public Picture/Paint/filter object, so Tasks 3–6 can materialize this sealed source without
 * becoming a second planner.
 */
public class GraphTextureSourceOperandV1 internal constructor(
    public val aggregateId: PictureStreamAggregateIdI32,
    public val sealedSourceId: PlanResourceId,
    public val sealedSourceGenerationI64: Long,
    targetOriginDeviceI32: Point2I32,
    sampleBoundsTargetI32: RectI32,
    public val mapping: LayerMappingF64,
    /** Applied by the one terminal parent composite, after parent mask/image-filter work. */
    public val deferredCompositeClip: ClipStackNode,
    public val material: MaterialPlanRef,
    public val uniformResource: PlanResourceId,
    public val alphaF32: Float,
    public val colorFilter: ColorFilterExecutionPlanV1?,
    public val finalBlend: BlendPlan,
) {
    private val origin = Point2I32(targetOriginDeviceI32.x, targetOriginDeviceI32.y)
    private val sampleBounds = sampleBoundsTargetI32.copy()

    init {
        require(sealedSourceGenerationI64 >= 0L && alphaF32.isFinite() && alphaF32 in 0f..1f)
        require(!sampleBounds.isEmpty)
        require(uniformResource.value.startsWith("${PlanResourceRole.SourceUniformData.name}:"))
    }

    public fun copyTargetOriginDeviceI32(): Point2I32 = Point2I32(origin.x, origin.y)
    public fun copySampleBoundsTargetI32(): RectI32 = sampleBounds.copy()
}

/** Internal pre-publication request; FrameSourceLayoutV4 resolves it to [GraphTextureSourceOperandV1]. */
public class GraphTextureSourceRequestV1 internal constructor(
    val aggregateId: PictureStreamAggregateIdI32,
    val sealedSourceId: PlanResourceId,
    val sealedSourceGenerationI64: Long,
    targetOriginDeviceI32: Point2I32,
    sampleBoundsTargetI32: RectI32,
    val mapping: LayerMappingF64,
    val deferredCompositeClip: ClipStackNode,
    val alphaF32: Float,
    val colorFilter: ColorFilterExecutionPlanV1?,
    val finalBlend: BlendPlan,
) {
    private val origin = Point2I32(targetOriginDeviceI32.x, targetOriginDeviceI32.y)
    private val sampleBounds = sampleBoundsTargetI32.copy()

    init {
        require(sealedSourceGenerationI64 >= 0L && alphaF32.isFinite() && alphaF32 in 0f..1f && !sampleBounds.isEmpty)
    }

    fun copyTargetOriginDeviceI32(): Point2I32 = Point2I32(origin.x, origin.y)
    fun copySampleBoundsTargetI32(): RectI32 = sampleBounds.copy()
}

/** Explicitly consumed capture state; later entries already carry the resulting immutable state. */
public sealed interface PictureStreamConsumedStateV1 {
    public class Transform internal constructor(matrix: Matrix3x3F32) : PictureStreamConsumedStateV1 {
        private val value = matrix.copy()
        public fun copyMatrixF32(): Matrix3x3F32 = value.copy()
    }

    public class Clip internal constructor(public val clip: ClipStackNode) : PictureStreamConsumedStateV1
}

/** A source-order partition of one captured Picture occurrence. */
public sealed interface PictureStreamEntryV1 {
    public val id: PictureStreamEntryIdI32
    public val locator: PictureSourceLocatorV1?
    public val plannedCommandId: FramePlannedCommandIdI32?
    public val terminalPassId: PlanPassId?

    public class Draw internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        override val terminalPassId: PlanPassId?,
    ) : PictureStreamEntryV1

    public class Picture internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        public val childAggregateId: PictureStreamAggregateIdI32,
        override val terminalPassId: PlanPassId?,
    ) : PictureStreamEntryV1

    /** Begin/end are one closed interval rather than two independently replayed commands. */
    public class Layer internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        public val endCommandIndexI32: Int,
        /** The existing W6a child scope selected for this closed layer interval. */
        public val layerScopeId: LayerScopeIdI32,
        public val layerTargetId: PlanResourceId,
        children: List<PictureStreamEntryV1>,
        override val terminalPassId: PlanPassId?,
    ) : PictureStreamEntryV1 {
        private val childValues = immutableList(children)
        init { require(endCommandIndexI32 > locator.sourceCommandIndexI32) }
        /** Ordered entries captured inside this layer; Task 3 never re-enumerates a SceneSnapshot. */
        public fun children(): List<PictureStreamEntryV1> = childValues
    }

    public class Clear internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        public val color: ColorF32,
        override val terminalPassId: PlanPassId?,
    ) : PictureStreamEntryV1

    public class DrawColor internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        public val color: ColorARGB,
        public val mode: BlendMode,
        transform: Matrix3x3F32,
        public val clip: ClipStackNode,
        override val terminalPassId: PlanPassId?,
    ) : PictureStreamEntryV1 {
        private val valueTransform = transform.copy()
        public fun copyTransformF32(): Matrix3x3F32 = valueTransform.copy()
    }

    /** SetTransform and SetClip have already been captured into following draw state. */
    public class ConsumedState internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        public val state: PictureStreamConsumedStateV1,
    ) : PictureStreamEntryV1 {
        override val plannedCommandId: FramePlannedCommandIdI32? = null
        override val terminalPassId: PlanPassId? = null
    }

    public class AnnotationNoOp internal constructor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        /** Provenance only; annotations never create raster work. */
        public val annotationCanonicalId: String,
    ) : PictureStreamEntryV1 {
        override val plannedCommandId: FramePlannedCommandIdI32? = null
        override val terminalPassId: PlanPassId? = null
    }
}

/**
 * Frozen structural and execution facts for one drawPicture occurrence.  The source scene is
 * retained only by the planner's private provenance; public readers receive neither it nor a
 * replay recipe.
 */
public class PictureStreamAggregateV1 internal constructor(
    public val id: PictureStreamAggregateIdI32,
    public val executionMode: PictureStreamExecutionModeV1,
    public val sourceSceneCanonicalId: String,
    public val sourceCommandCountI32: Int,
    public val sourcePictureOccurrenceIdI32: Int,
    /** Frame identity of this drawPicture command; nested entries may reference the same ID. */
    public val sourcePlannedCommandId: FramePlannedCommandIdI32,
    outerPicturePathI32: List<Int>,
    public val outerEvaluationMappingF64: LayerMappingF64,
    public val recordedInnerClip: ClipStackNode,
    public val deferredCompositeClip: ClipStackNode,
    cullContentBoundDeviceI32: RectI32?,
    demandRegionDeviceI32: RectI32,
    public val parentTargetId: PlanResourceId,
    public val aggregateTargetId: PlanResourceId?,
    public val sealedSourceId: PlanResourceId?,
    public val sealedSourceGenerationI64: Long?,
    public val bounds: PictureStreamRegionsV1,
    entries: List<PictureStreamEntryV1>,
    public val beginPassId: PlanPassId?,
    public val sealPassId: PlanPassId?,
    public val terminalPassId: PlanPassId?,
) {
    private val outerPath = immutableList(outerPicturePathI32)
    private val cullContent = cullContentBoundDeviceI32?.copy()
    private val demand = demandRegionDeviceI32.copy()
    private val values = immutableList(entries)

    init {
        require(sourceSceneCanonicalId.isNotBlank() && sourceCommandCountI32 >= 0 &&
            sourcePictureOccurrenceIdI32 >= 0 && !demand.isEmpty)
        require(outerPath.all { it >= 0 })
        require(values.map { it.id }.distinct().size == values.size)
        require(values.zipWithNext().all { (first, second) -> first.id.valueI32 < second.id.valueI32 })
        when (executionMode) {
            PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET ->
                require(aggregateTargetId == null && sealedSourceId == null && sealedSourceGenerationI64 == null &&
                    beginPassId == null && sealPassId == null)
            PictureStreamExecutionModeV1.ISOLATED_SOURCE ->
                require(aggregateTargetId != null && sealedSourceId == aggregateTargetId &&
                    sealedSourceGenerationI64 != null && beginPassId != null && sealPassId != null)
        }
    }

    public fun outerPicturePathI32(): List<Int> = outerPath
    public fun copyCullContentBoundDeviceI32(): RectI32? = cullContent?.copy()
    public fun copyDemandRegionDeviceI32(): RectI32 = demand.copy()
    public fun entries(): List<PictureStreamEntryV1> = values
}

/** Stable structural diagnostic construction. Existing capture/bounds/budget codes stay intact. */
internal fun pictureStreamInvalid(
    aggregateId: PictureStreamAggregateIdI32,
    locator: PictureSourceLocatorV1? = null,
    invariant: String,
    passId: PlanPassId? = null,
    resourceId: PlanResourceId? = null,
): W6bFilterGraphConstruction.ConstructionFailure {
    val suffix = buildString {
        append("aggregateIdI32=").append(aggregateId.valueI32)
        locator?.let { append(", pictureOccurrenceIdI32=").append(it.pictureOccurrenceIdI32)
            .append(", sourceCommandIndexI32=").append(it.sourceCommandIndexI32) }
        passId?.let { append(", passId=").append(it.value) }
        resourceId?.let { append(", resourceId=").append(it.value) }
        append(": ").append(invariant)
    }
    return W6bFilterGraphConstruction.ConstructionFailure(W6bFilterDiagnostics.refusal(
        W6bFilterDiagnostics.PictureStreamInvalid,
        "Invalid W6b Picture stream ($suffix)",
    ))
}

/** Planner-only source facts. They are converted to immutable [PictureStreamAggregateV1] values at publication. */
internal class PictureStreamAggregateDraftV1(
    val id: PictureStreamAggregateIdI32,
    val executionMode: PictureStreamExecutionModeV1,
    val sourceScene: SceneSnapshot,
    val sourcePictureOccurrenceIdI32: Int,
    val sourcePlannedCommandId: FramePlannedCommandIdI32,
    outerPicturePathI32: List<Int>,
    val source: FilterOccurrenceSourceV1,
    val draw: DrawNode,
    val filterOccurrence: W6bFilterGraphConstruction.PositiveOccurrence?,
    entries: List<PictureStreamEntryDraftV1>,
) {
    private val outerPath = immutableList(outerPicturePathI32)
    private val values = immutableList(entries)
    fun outerPicturePathI32(): List<Int> = outerPath
    fun entries(): List<PictureStreamEntryDraftV1> = values
}

internal sealed interface PictureStreamEntryDraftV1 {
    val id: PictureStreamEntryIdI32
    val locator: PictureSourceLocatorV1
    val plannedCommandId: FramePlannedCommandIdI32?

    class Draw(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        val source: FilterOccurrenceSourceV1,
        val filterOccurrence: W6bFilterGraphConstruction.PositiveOccurrence?,
    ) : PictureStreamEntryDraftV1

    class Picture(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        val child: PictureStreamAggregateDraftV1,
    ) : PictureStreamEntryDraftV1

    class Layer(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        val descriptorSource: FilterOccurrenceSourceV1,
        val endCommandIndexI32: Int,
        children: List<PictureStreamEntryDraftV1>,
        val filterOccurrence: W6bFilterGraphConstruction.PositiveOccurrence?,
    ) : PictureStreamEntryDraftV1
    {
        private val childValues = immutableList(children)
        fun children(): List<PictureStreamEntryDraftV1> = childValues
    }

    class Clear(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        val color: ColorF32,
    ) : PictureStreamEntryDraftV1

    class DrawColor(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        override val plannedCommandId: FramePlannedCommandIdI32,
        val color: ColorARGB,
        val mode: BlendMode,
        transform: Matrix3x3F32,
        val clip: ClipStackNode,
    ) : PictureStreamEntryDraftV1 {
        private val valueTransform = transform.copy()
        fun copyTransformF32(): Matrix3x3F32 = valueTransform.copy()
    }

    class ConsumedState(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        val state: PictureStreamConsumedStateV1,
    ) : PictureStreamEntryDraftV1 {
        override val plannedCommandId: FramePlannedCommandIdI32? = null
    }

    class AnnotationNoOp(
        override val id: PictureStreamEntryIdI32,
        override val locator: PictureSourceLocatorV1,
        val annotationCanonicalId: String,
    ) : PictureStreamEntryDraftV1 {
        override val plannedCommandId: FramePlannedCommandIdI32? = null
    }
}

/**
 * Ordered recursive discovery. It purposely uses indexed recursion rather than a LIFO stack so
 * sibling Pictures preserve their recorded order. The draft retains no renderer replay choice.
 */
internal class PictureStreamAggregateDiscoveryV1(
    private val positiveOccurrences: List<W6bFilterGraphConstruction.PositiveOccurrence>,
) {
    private var nextAggregateI32: Int = 0
    private var nextPictureOccurrenceI32: Int = 0
    private var nextFrameCommandI32: Int = 0

    fun root(
        scene: SceneSnapshot,
        commandIndexI32: Int,
        draw: DrawNode,
    ): PictureStreamAggregateDraftV1 = build(
        scene,
        commandIndexI32,
        draw,
        FramePlannedCommandIdI32(nextFrameCommandI32.also { nextFrameCommandI32 = Math.addExact(it, 1) }),
        emptyList(),
        emptyList(),
    )

    private fun build(
        scene: SceneSnapshot,
        commandIndexI32: Int,
        draw: DrawNode,
        sourcePlannedCommandId: FramePlannedCommandIdI32,
        outerPictures: List<DrawNode>,
        outerPath: List<Int>,
    ): PictureStreamAggregateDraftV1 {
        val picture = draw.geometry as? GeometryNode.Picture
            ?: throw IllegalArgumentException("Picture aggregate requires GeometryNode.Picture.")
        val aggregateId = PictureStreamAggregateIdI32(nextAggregateI32.also { nextAggregateI32 = Math.addExact(it, 1) })
        val pictureOccurrence = nextPictureOccurrenceI32.also { nextPictureOccurrenceI32 = Math.addExact(it, 1) }
        val source = FilterOccurrenceSourceV1(scene, commandIndexI32, draw, outerPictures, picturePathI32 = outerPath)
        val filter = matchingOccurrence(scene, commandIndexI32, outerPath)
        var nextEntryI32 = 0
        fun entryId(): PictureStreamEntryIdI32 = PictureStreamEntryIdI32(nextEntryI32.also {
            nextEntryI32 = Math.addExact(it, 1)
        })
        fun locator(indexI32: Int): PictureSourceLocatorV1 = PictureSourceLocatorV1(pictureOccurrence, indexI32)
        fun planned(): FramePlannedCommandIdI32 = FramePlannedCommandIdI32(nextFrameCommandI32.also {
            nextFrameCommandI32 = Math.addExact(it, 1)
        })
        val commands = picture.scene.toList()
        /**
         * Layer intervals remain scopes rather than opaque replay ranges.  The same FIFO/source
         * recursion owns their children, so a nested layer cannot hide a filtered or unfiltered
         * sibling from the frozen stream.
         */
        fun entriesFor(startInclusiveI32: Int, endExclusiveI32: Int): List<PictureStreamEntryDraftV1> {
            val values = mutableListOf<PictureStreamEntryDraftV1>()
            var indexI32 = startInclusiveI32
            while (indexI32 < endExclusiveI32) {
                when (val command = commands[indexI32]) {
                    is SceneCommand.Draw -> {
                        val childPicture = command.node.geometry as? GeometryNode.Picture
                        if (childPicture == null) {
                            val childSource = FilterOccurrenceSourceV1(picture.scene, indexI32, command.node,
                                outerPictures + draw, picturePathI32 = outerPath + commandIndexI32)
                            values += PictureStreamEntryDraftV1.Draw(entryId(), locator(indexI32), planned(), childSource,
                                matchingOccurrence(picture.scene, indexI32, outerPath + commandIndexI32))
                        } else {
                            val childPlanned = planned()
                            val child = build(picture.scene, indexI32, command.node, childPlanned, outerPictures + draw,
                                outerPath + commandIndexI32)
                            values += PictureStreamEntryDraftV1.Picture(entryId(), locator(indexI32), childPlanned, child)
                        }
                        indexI32 = Math.addExact(indexI32, 1)
                    }
                    is SceneCommand.Clear -> {
                        values += PictureStreamEntryDraftV1.Clear(entryId(), locator(indexI32), planned(), command.color)
                        indexI32 = Math.addExact(indexI32, 1)
                    }
                    is SceneCommand.DrawColor -> {
                        values += PictureStreamEntryDraftV1.DrawColor(
                            entryId(), locator(indexI32), planned(), command.color, command.mode, command.transform, command.clip,
                        )
                        indexI32 = Math.addExact(indexI32, 1)
                    }
                    is SceneCommand.SetTransform -> {
                        values += PictureStreamEntryDraftV1.ConsumedState(
                            entryId(), locator(indexI32), PictureStreamConsumedStateV1.Transform(command.matrix),
                        )
                        indexI32 = Math.addExact(indexI32, 1)
                    }
                    is SceneCommand.SetClip -> {
                        values += PictureStreamEntryDraftV1.ConsumedState(
                            entryId(), locator(indexI32), PictureStreamConsumedStateV1.Clip(command.clip),
                        )
                        indexI32 = Math.addExact(indexI32, 1)
                    }
                    is SceneCommand.Annotation -> {
                        values += PictureStreamEntryDraftV1.AnnotationNoOp(entryId(), locator(indexI32), command.canonicalId.value)
                        indexI32 = Math.addExact(indexI32, 1)
                    }
                    is SceneCommand.BeginLayer -> {
                        val end = matchingLayerEnd(commands, indexI32, aggregateId, locator(indexI32))
                        if (end >= endExclusiveI32) throw pictureStreamInvalid(aggregateId, locator(indexI32),
                            "Layer interval escapes its owning Picture scope.")
                        val layerSource = FilterOccurrenceSourceV1(picture.scene, indexI32, null, outerPictures + draw,
                            command.descriptor, outerPath + commandIndexI32)
                        values += PictureStreamEntryDraftV1.Layer(
                            entryId(), locator(indexI32), planned(), layerSource, end,
                            entriesFor(Math.addExact(indexI32, 1), end),
                            matchingOccurrence(picture.scene, indexI32, outerPath + commandIndexI32),
                        )
                        indexI32 = Math.addExact(end, 1)
                    }
                    SceneCommand.EndLayer -> throw pictureStreamInvalid(aggregateId, locator(indexI32), "EndLayer has no matching BeginLayer.")
                    is SceneCommand.State, is SceneCommand.Readback -> throw W6bFilterGraphConstruction.ConstructionFailure(
                        W6bFilterDiagnostics.refusal(W6aPlanDiagnostics.UnsupportedChild,
                            "This layer-frame command has no admitted source lane (pictureOccurrenceIdI32=${pictureOccurrence}, " +
                                "sourceCommandIndexI32=$indexI32)."),
                    )
                }
            }
            return immutableList(values)
        }
        val entries = entriesFor(0, commands.size)
        return PictureStreamAggregateDraftV1(
            aggregateId,
            if (draw.paint == null) PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET else PictureStreamExecutionModeV1.ISOLATED_SOURCE,
            picture.scene,
            pictureOccurrence,
            sourcePlannedCommandId,
            outerPath,
            source,
            draw,
            filter,
            entries,
        )
    }

    private fun matchingOccurrence(
        scene: SceneSnapshot,
        commandIndexI32: Int,
        path: List<Int>,
    ): W6bFilterGraphConstruction.PositiveOccurrence? = positiveOccurrences.singleOrNull { occurrence ->
        occurrence.sourceSceneCanonicalId == scene.canonicalId.value &&
            occurrence.sourceCommandIndexI32 == commandIndexI32 &&
            occurrence.outerPicturePathI32() == path
    }

    private fun matchingLayerEnd(
        commands: List<SceneCommand>,
        begin: Int,
        aggregateId: PictureStreamAggregateIdI32,
        locator: PictureSourceLocatorV1,
    ): Int {
        var depth = 0
        for (indexI32 in begin until commands.size) when (commands[indexI32]) {
            is SceneCommand.BeginLayer -> depth = Math.addExact(depth, 1)
            SceneCommand.EndLayer -> {
                depth = Math.subtractExact(depth, 1)
                if (depth == 0) return indexI32
            }
            else -> Unit
        }
        throw pictureStreamInvalid(aggregateId, locator, "BeginLayer has no matching EndLayer.")
    }
}

/** Production publication validation for the frozen Picture aggregate contract. */
internal fun validatePictureStreamAggregates(
    frame: LayerFramePlanV1,
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
) {
    val aggregates = frame.pictureStreamAggregates()
    if (aggregates.isEmpty()) return
    val byAggregate = aggregates.associateBy { it.id }
    val passById = passes.associateBy { it.id }
    val passIndex = passes.mapIndexed { indexI32, pass -> pass.id to indexI32 }.toMap()
    val rowById = resources.associateBy { it.id }
    val scopeById = frame.scopes().associateBy { it.id }
    val plannedIds = mutableSetOf<FramePlannedCommandIdI32>()
    val sealedTargets = mutableSetOf<PlanResourceId>()
    val aggregateTargets = mutableSetOf<PlanResourceId>()
    val entryIdsByAggregate = mutableMapOf<PictureStreamAggregateIdI32, MutableSet<PictureStreamEntryIdI32>>()
    val terminalIdsByAggregate = mutableMapOf<PictureStreamAggregateIdI32, MutableSet<PlanPassId>>()

    fun fail(
        aggregate: PictureStreamAggregateV1,
        entry: PictureStreamEntryV1? = null,
        invariant: String,
        passId: PlanPassId? = null,
        resourceId: PlanResourceId? = null,
    ): Nothing = throw pictureStreamInvalid(aggregate.id, entry?.locator, invariant, passId, resourceId)

    if (byAggregate.size != aggregates.size) {
        val first = aggregates.first()
        fail(first, invariant = "Aggregate ID is duplicated.")
    }
    if (scopeById.size != frame.scopes().size) fail(aggregates.first(), invariant = "Layer scope ID is duplicated.")
    if (aggregates.map { it.sourcePlannedCommandId }.distinct().size != aggregates.size) {
        fail(aggregates.first(), invariant = "Picture aggregate source planned ID aliases another occurrence.")
    }
    val treePlannedIds = mutableSetOf<FramePlannedCommandIdI32>()

    fun subtreeTerminals(entries: List<PictureStreamEntryV1>): List<PlanPassId> = buildList {
        entries.forEach { entry ->
            entry.terminalPassId?.let(::add)
            if (entry is PictureStreamEntryV1.Layer) addAll(subtreeTerminals(entry.children()))
        }
    }

    /**
     * Layer intervals are a second ordered stream, not an opaque Begin/End placeholder.  Keep
     * this recursive because a nested Picture layer has occurrence-local command indices while
     * its target is a real W6a scope target.
     */
    fun validateEntryTree(
        aggregate: PictureStreamAggregateV1,
        entries: List<PictureStreamEntryV1>,
        firstI32: Int,
        lastExclusiveI32: Int,
        target: PlanResourceId,
    ) {
        if (firstI32 < 0 || lastExclusiveI32 < firstI32 || lastExclusiveI32 > aggregate.sourceCommandCountI32) {
            fail(aggregate, invariant = "Picture entry interval is outside the captured source stream.")
        }
        if (!entries.zipWithNext().all { (first, second) -> first.id.valueI32 < second.id.valueI32 }) {
            fail(aggregate, invariant = "Entry IDs are not monotone.")
        }
        val covered = BooleanArray(lastExclusiveI32 - firstI32)
        fun cover(entry: PictureStreamEntryV1, startI32: Int, endExclusiveI32: Int) {
            if (startI32 < firstI32 || endExclusiveI32 > lastExclusiveI32 || startI32 >= endExclusiveI32) {
                fail(aggregate, entry, "Source index is outside the captured Picture stream.")
            }
            for (indexI32 in startI32 until endExclusiveI32) {
                val localIndexI32 = indexI32 - firstI32
                if (covered[localIndexI32]) fail(aggregate, entry, "Picture source-index partition overlaps.")
                covered[localIndexI32] = true
            }
        }
        val immediateTerminals = mutableListOf<Int>()
        entries.forEach { entry ->
            val locator = entry.locator ?: fail(aggregate, entry, "Picture entry lost its source locator.")
            if (locator.pictureOccurrenceIdI32 != aggregate.sourcePictureOccurrenceIdI32) {
                fail(aggregate, entry, "Entry locator belongs to another Picture occurrence.")
            }
            if (!entryIdsByAggregate.getOrPut(aggregate.id, ::mutableSetOf).add(entry.id)) {
                fail(aggregate, entry, "Entry ID is duplicated.")
            }
            val consumed = entry is PictureStreamEntryV1.ConsumedState || entry is PictureStreamEntryV1.AnnotationNoOp
            if (consumed && (entry.plannedCommandId != null || entry.terminalPassId != null)) {
                fail(aggregate, entry, "Consumed Picture state owns visual execution identity or terminal work.")
            }
            if (!consumed && entry.plannedCommandId == null) {
                fail(aggregate, entry, "Visual Picture entry lost its planned command identity.")
            }
            entry.plannedCommandId?.let { planned ->
                if (!treePlannedIds.add(planned)) {
                    fail(aggregate, entry, "Frame planned command ID aliases another occurrence.")
                }
            }
            val endExclusive = when (entry) {
                is PictureStreamEntryV1.Layer -> Math.addExact(entry.endCommandIndexI32, 1)
                else -> Math.addExact(locator.sourceCommandIndexI32, 1)
            }
            cover(entry, locator.sourceCommandIndexI32, endExclusive)
            fun requireTerminal(): Pair<PlanPassId, Int> {
                val terminal = entry.terminalPassId
                    ?: fail(aggregate, entry, "Visual Picture entry has no effective terminal pass.")
                val indexI32 = passIndex[terminal]
                    ?: fail(aggregate, entry, "Entry terminal pass is absent.", terminal)
                if (!terminalIdsByAggregate.getOrPut(aggregate.id, ::mutableSetOf).add(terminal)) {
                    fail(aggregate, entry, "Two Picture entries own the same terminal pass.", terminal)
                }
                return terminal to indexI32
            }
            when (entry) {
                is PictureStreamEntryV1.Draw -> {
                    val (terminal, indexI32) = requireTerminal()
                    val exact = when (val pass = passById.getValue(terminal)) {
                        is PlanPass.PictureSourcePass -> pass.output == target && pass.pictureSourceLocator == locator &&
                            pass.plannedCommandId == entry.plannedCommandId
                        is PlanPass.FilterComposite -> (pass.operation as? FilterCompositeOperationV1.Picture)?.let { operation ->
                            pass.destination == target && operation.sourceSceneCanonicalId == aggregate.sourceSceneCanonicalId &&
                                operation.sourceCommandIndexI32 == locator.sourceCommandIndexI32
                        } == true
                        else -> false
                    }
                    if (!exact) fail(aggregate, entry, "Draw terminal does not write its immediate Picture target.", terminal, target)
                    immediateTerminals += indexI32
                }
                is PictureStreamEntryV1.Clear,
                is PictureStreamEntryV1.DrawColor,
                -> {
                    val (terminal, indexI32) = requireTerminal()
                    val pass = passById.getValue(terminal) as? PlanPass.PictureSourcePass
                    if (pass == null || pass.output != target || pass.pictureSourceLocator != locator ||
                        pass.plannedCommandId != entry.plannedCommandId) {
                        fail(aggregate, entry, "Visual Picture command does not write its immediate target.", terminal, target)
                    }
                    immediateTerminals += indexI32
                }
                is PictureStreamEntryV1.Picture -> {
                    val child = byAggregate[entry.childAggregateId]
                        ?: fail(aggregate, entry, "Picture entry references an absent child aggregate.")
                    if (entry.plannedCommandId != child.sourcePlannedCommandId || entry.terminalPassId != child.terminalPassId) {
                        fail(aggregate, entry, "Picture entry does not reference its child's actual planned source and terminal.",
                            entry.terminalPassId)
                    }
                    if (child.parentTargetId != target) {
                        fail(aggregate, entry, "Picture child writes outside its immediate aggregate parent target.")
                    }
                    entry.terminalPassId?.let { terminal ->
                        val indexI32 = passIndex[terminal]
                            ?: fail(aggregate, entry, "Picture child terminal pass is absent.", terminal)
                        if (!terminalIdsByAggregate.getOrPut(aggregate.id, ::mutableSetOf).add(terminal)) {
                            fail(aggregate, entry, "Two Picture entries own the same terminal pass.", terminal)
                        }
                        immediateTerminals += indexI32
                    }
                }
                is PictureStreamEntryV1.Layer -> {
                    val (terminal, indexI32) = requireTerminal()
                    val scope = scopeById[entry.layerScopeId]
                        ?: fail(aggregate, entry, "Layer entry references an absent W6a scope.")
                    val scopeInitializes = frame.executionSteps().filterIsInstance<LayerExecutionStepV1.Initialize>()
                        .filter { it.scopeId == scope.id }
                    val scopeRestores = frame.executionSteps().filterIsInstance<LayerExecutionStepV1.Restore>()
                        .filter { it.scopeId == scope.id }
                    if (scopeInitializes.size != 1 || scopeRestores.size != 1 || scopeRestores.single().passId != terminal) {
                        fail(aggregate, entry, "Layer entry does not own one complete W6a initialize/restore scope.", terminal)
                    }
                    val exact = scope.targetResource == entry.layerTargetId && scope.parentTargetResource == target &&
                        scope.beginCommandIndexI32 == locator.sourceCommandIndexI32 &&
                        scope.endCommandIndexI32 == entry.endCommandIndexI32 && when (val pass = passById.getValue(terminal)) {
                        is PlanPass.LayerComposite -> pass.scopeId == scope.id && pass.source == entry.layerTargetId &&
                            pass.destination == target && pass.restore === scope.restore
                        is PlanPass.FilterComposite -> (pass.operation as? FilterCompositeOperationV1.Layer)?.let { operation ->
                            pass.replacedLayerSource == entry.layerTargetId && pass.destination == target &&
                                operation.restore === scope.restore
                        } == true
                        else -> false
                    }
                    if (!exact) fail(aggregate, entry, "Layer terminal is not its real W6a restore into the immediate target.",
                        terminal, target)
                    validateEntryTree(aggregate, entry.children(), Math.addExact(locator.sourceCommandIndexI32, 1),
                        entry.endCommandIndexI32, entry.layerTargetId)
                    if (subtreeTerminals(entry.children()).any { passIndex.getValue(it) >= indexI32 }) {
                        fail(aggregate, entry, "Layer terminal precedes work in its closed child interval.", terminal)
                    }
                    immediateTerminals += indexI32
                }
                is PictureStreamEntryV1.ConsumedState,
                is PictureStreamEntryV1.AnnotationNoOp,
                -> Unit
            }
        }
        if (covered.any { !it }) fail(aggregate, invariant = "Picture source-index partition is not exhaustive.")
        if (!immediateTerminals.zipWithNext().all { (first, second) -> first < second }) {
            fail(aggregate, invariant = "Picture entry terminals do not preserve source order.")
        }
    }
    fun pictureEntries(entries: List<PictureStreamEntryV1>): List<PictureStreamEntryV1.Picture> = buildList {
        entries.forEach { entry ->
            if (entry is PictureStreamEntryV1.Picture) add(entry)
            if (entry is PictureStreamEntryV1.Layer) addAll(pictureEntries(entry.children()))
        }
    }
    val visitState = mutableMapOf<PictureStreamAggregateIdI32, Int>()
    fun visitAggregate(aggregate: PictureStreamAggregateV1) {
        when (visitState[aggregate.id]) {
            1 -> fail(aggregate, invariant = "Picture aggregate graph contains a cycle.")
            2 -> return
            else -> Unit
        }
        visitState[aggregate.id] = 1
        pictureEntries(aggregate.entries()).forEach { entry ->
            val child = byAggregate[entry.childAggregateId] ?: return@forEach
            if (visitState[child.id] == 1) {
                fail(aggregate, entry, "Picture aggregate graph contains a cycle.")
            }
            visitAggregate(child)
        }
        visitState[aggregate.id] = 2
    }
    aggregates.forEach(::visitAggregate)
    aggregates.forEach { aggregate ->
        val entries = aggregate.entries()
        validateEntryTree(aggregate, entries, 0, aggregate.sourceCommandCountI32,
            aggregate.aggregateTargetId ?: aggregate.parentTargetId)
        if (entries.map { it.id }.distinct().size != entries.size) fail(aggregate, invariant = "Entry ID is duplicated.")
        if (!entries.zipWithNext().all { (first, second) -> first.id.valueI32 < second.id.valueI32 }) {
            fail(aggregate, invariant = "Entry IDs are not monotone.")
        }
        val directTerminalOwners = mutableSetOf<PlanPassId>()
        entries.filterNot { it is PictureStreamEntryV1.Picture }.forEach { entry ->
            val isVisual = entry is PictureStreamEntryV1.Draw || entry is PictureStreamEntryV1.Layer ||
                entry is PictureStreamEntryV1.Clear || entry is PictureStreamEntryV1.DrawColor
            if (isVisual && entry.terminalPassId == null) {
                fail(aggregate, entry, "Visual Picture entry has no effective terminal pass.")
            }
            entry.terminalPassId?.let { terminal ->
                if (!directTerminalOwners.add(terminal)) {
                    fail(aggregate, entry, "Two direct Picture entries own the same terminal pass.", terminal)
                }
            }
        }
        val covered = BooleanArray(aggregate.sourceCommandCountI32)
        fun cover(entry: PictureStreamEntryV1, firstI32: Int, lastExclusiveI32: Int) {
            if (firstI32 < 0 || lastExclusiveI32 > covered.size || firstI32 >= lastExclusiveI32) {
                fail(aggregate, entry, "Source index is outside the captured Picture stream.")
            }
            for (indexI32 in firstI32 until lastExclusiveI32) {
                if (covered[indexI32]) fail(aggregate, entry, "Picture source-index partition overlaps.")
                covered[indexI32] = true
            }
        }
        entries.forEach { entry ->
            val locator = entry.locator ?: fail(aggregate, entry, "Picture entry lost its source locator.")
            if (locator.pictureOccurrenceIdI32 != aggregate.sourcePictureOccurrenceIdI32) {
                fail(aggregate, entry, "Entry locator belongs to another Picture occurrence.")
            }
            when (entry) {
                is PictureStreamEntryV1.Layer -> cover(entry, locator.sourceCommandIndexI32, entry.endCommandIndexI32 + 1)
                else -> cover(entry, locator.sourceCommandIndexI32, locator.sourceCommandIndexI32 + 1)
            }
            entry.plannedCommandId?.let { planned ->
                if (!plannedIds.add(planned)) fail(aggregate, entry, "Frame planned command ID aliases another occurrence.")
            }
            when (entry) {
                is PictureStreamEntryV1.Picture -> {
                    val child = byAggregate[entry.childAggregateId]
                        ?: fail(aggregate, entry, "Picture entry references an absent child aggregate.")
                    if (entry.terminalPassId != child.terminalPassId) {
                        fail(aggregate, entry, "Picture entry does not reference its child's actual terminal.", entry.terminalPassId)
                    }
                    val expectedTarget = aggregate.aggregateTargetId ?: aggregate.parentTargetId
                    if (child.parentTargetId != expectedTarget) {
                        fail(aggregate, entry, "Picture child writes outside its immediate aggregate parent target.")
                    }
                }
                else -> Unit
            }
            entry.terminalPassId?.let { terminal ->
                if (terminal !in passById) fail(aggregate, entry, "Entry terminal pass is absent.", terminal)
            }
        }
        if (covered.any { !it }) fail(aggregate, invariant = "Picture source-index partition is not exhaustive.")
        val orderedVisualTerminals = entries.mapNotNull { it.terminalPassId }
        if (!orderedVisualTerminals.zipWithNext().all { (first, second) ->
                passIndex.getValue(first) < passIndex.getValue(second)
            }) {
            fail(aggregate, invariant = "Picture entry terminals do not preserve source order.")
        }
        when (aggregate.executionMode) {
            PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET -> {
                if (aggregate.aggregateTargetId != null || aggregate.sealedSourceId != null ||
                    aggregate.sealedSourceGenerationI64 != null || aggregate.beginPassId != null || aggregate.sealPassId != null) {
                    fail(aggregate, invariant = "Inline Picture owns an isolated target or seal.")
                }
                val expectedTerminal = entries.lastOrNull { it.terminalPassId != null }?.terminalPassId
                if (aggregate.terminalPassId != expectedTerminal) {
                    fail(aggregate, invariant = "Inline Picture terminal is not its final ordered entry terminal.",
                        passId = aggregate.terminalPassId)
                }
            }
            PictureStreamExecutionModeV1.ISOLATED_SOURCE -> {
                val target = aggregate.aggregateTargetId ?: fail(aggregate, invariant = "Isolated Picture has no target.")
                val source = aggregate.sealedSourceId ?: fail(aggregate, invariant = "Isolated Picture has no sealed source.")
                val generation = aggregate.sealedSourceGenerationI64
                    ?: fail(aggregate, invariant = "Isolated Picture has no sealed generation.")
                val begin = aggregate.beginPassId ?: fail(aggregate, invariant = "Isolated Picture has no begin pass.")
                val seal = aggregate.sealPassId ?: fail(aggregate, invariant = "Isolated Picture has no seal pass.")
                val beginPasses = passes.filterIsInstance<PlanPass.PictureAggregateBeginPass>().filter { it.aggregateId == aggregate.id }
                val sealPasses = passes.filterIsInstance<PlanPass.PictureAggregateSealPass>().filter { it.aggregateId == aggregate.id }
                if (beginPasses.size != 1 || sealPasses.size != 1 || beginPasses.single().id != begin || sealPasses.single().id != seal) {
                    fail(aggregate, invariant = "Picture aggregate does not have exactly one matching begin and seal.")
                }
                val beginPass = beginPasses.single()
                val sealPass = sealPasses.single()
                if (target != source || beginPass.aggregateId != aggregate.id || beginPass.target != target ||
                    beginPass.parentTarget != aggregate.parentTargetId || sealPass.aggregateId != aggregate.id ||
                    sealPass.aggregateTarget != target || sealPass.sealedSource != source ||
                    sealPass.sourceGenerationI64 != generation) {
                    fail(aggregate, invariant = "Begin/seal target, owner, or generation disagrees with aggregate facts.",
                        passId = seal, resourceId = source)
                }
                val targetRow = rowById[source] ?: fail(aggregate, invariant = "Sealed Picture source resource is absent.", resourceId = source)
                if (targetRow.role != PlanResourceRole.PictureAggregateSource ||
                    !aggregateTargets.add(target) || !sealedTargets.add(source)) {
                    fail(aggregate, invariant = "Picture aggregate source aliases another aggregate or has the wrong role.", resourceId = source)
                }
                val beginIndex = passIndex.getValue(begin)
                val sealIndex = passIndex.getValue(seal)
                if (beginIndex >= sealIndex) fail(aggregate, invariant = "Picture aggregate seals before it begins.", passId = seal)
                fun writesTarget(pass: PlanPass): Boolean = when (pass) {
                    is PlanPass.RenderPass -> pass.target == target
                    is PlanPass.StencilGeometryProducerV3 -> pass.target == target
                    is PlanPass.StencilCover -> pass.target == target
                    is PlanPass.TextureCopy -> pass.destination == target
                    is PlanPass.PictureSourcePass -> pass.output == target
                    is PlanPass.PictureComposite -> pass.destination == target
                    is PlanPass.FilterComposite -> pass.destination == target
                    is PlanPass.LayerComposite -> pass.destination == target
                    else -> false
                }
                passes.forEachIndexed { indexI32, pass ->
                    if (writesTarget(pass) && (indexI32 <= beginIndex || indexI32 >= sealIndex)) {
                        fail(aggregate, invariant = "Picture aggregate writes outside its unsealed interval.", passId = pass.id,
                            resourceId = target)
                    }
                }
                terminalIdsByAggregate[aggregate.id].orEmpty().forEach { terminal ->
                    if (passIndex.getValue(terminal) !in (beginIndex + 1) until sealIndex) {
                        fail(aggregate, invariant = "Picture child terminal is outside begin/seal interval.", passId = terminal)
                    }
                }
                val graphTextureConsumers = passes.filterIsInstance<PlanPass.PictureSourcePass>().filter { pass ->
                    pass.aggregateId == aggregate.id && (pass.graphTextureRequest != null || pass.graphTextureOperand != null)
                }
                if (graphTextureConsumers.size != 1) {
                    fail(aggregate, invariant = "Isolated Picture must have exactly one graph-texture consumer.")
                }
                val consumer = graphTextureConsumers.single()
                val operand = consumer.graphTextureOperand ?: fail(aggregate,
                    invariant = "Published isolated Picture graph-texture consumer lacks its W5 operand.", passId = consumer.id)
                if (consumer.pictureSourceLocator?.pictureOccurrenceIdI32 != aggregate.sourcePictureOccurrenceIdI32 ||
                    consumer.plannedCommandId != aggregate.sourcePlannedCommandId ||
                    operand.aggregateId != aggregate.id || operand.sealedSourceId != source ||
                    operand.sealedSourceGenerationI64 != generation || passIndex.getValue(consumer.id) <= sealIndex) {
                    fail(aggregate, invariant = "Graph-texture consumer reads before seal or from the wrong source generation.",
                        passId = consumer.id, resourceId = source)
                }
                val sourceExtent = requireNotNull(targetRow.copyExtent())
                if (operand.copyTargetOriginDeviceI32() != aggregate.outerEvaluationMappingF64.copyLayerOriginDeviceI32() ||
                    operand.copySampleBoundsTargetI32() != RectI32(0, 0, sourceExtent.width, sourceExtent.height) ||
                    operand.mapping.copyLocalToDeviceF64() != aggregate.outerEvaluationMappingF64.copyLocalToDeviceF64() ||
                    operand.mapping.copyLayerOriginDeviceI32() != aggregate.outerEvaluationMappingF64.copyLayerOriginDeviceI32() ||
                    operand.deferredCompositeClip.canonicalId != aggregate.deferredCompositeClip.canonicalId) {
                    fail(aggregate, invariant = "Graph-texture operand origin, bounds, mapping, or deferred clip diverges from the aggregate.",
                        passId = consumer.id, resourceId = source)
                }
                val terminal = aggregate.terminalPassId ?: fail(aggregate, invariant = "Isolated Picture has no final composite terminal.")
                val terminalPass = passById[terminal]
                val reachesParent = when (terminalPass) {
                    is PlanPass.PictureComposite -> terminalPass.destination == aggregate.parentTargetId &&
                        terminalPass.source == consumer.output &&
                        terminalPass.occurrence.scene.canonicalId.value == consumer.sourceSceneCanonicalId &&
                        terminalPass.occurrence.sourceCommandIndexI32 == consumer.sourceCommandIndexI32
                    is PlanPass.FilterComposite -> {
                        val operation = terminalPass.operation as? FilterCompositeOperationV1.Picture
                        val producer = passes.getOrNull(passIndex.getValue(terminal) - 1) as? PlanPass.FilterPass
                        terminalPass.destination == aggregate.parentTargetId && operation != null &&
                            operation.sourceSceneCanonicalId == consumer.sourceSceneCanonicalId &&
                            operation.sourceCommandIndexI32 == consumer.sourceCommandIndexI32 &&
                            producer?.output == terminalPass.source &&
                            producer.evaluationKey.boundSourceId == consumer.output
                    }
                    else -> false
                }
                if (!reachesParent || passIndex.getValue(terminal) <= sealIndex) {
                    fail(aggregate, invariant = "Isolated Picture terminal does not uniquely composite to its immediate parent.", passId = terminal)
                }
                val lastReader = maxOf(passIndex.getValue(terminal), passIndex.getValue(consumer.id))
                if (targetRow.firstPassIndex > beginIndex || targetRow.lastPassIndexExclusive <= lastReader) {
                    fail(aggregate, invariant = "Sealed Picture source lifetime does not contain begin through its last reader.", resourceId = source)
                }
            }
        }
    }
    if (dependencies != passes.zipWithNext { first, second -> PlanPassDependency(first.id, second.id) }) {
        val first = aggregates.first()
        fail(first, invariant = "Picture aggregate graph dependencies are not the sealed ordered queue.")
    }
}
