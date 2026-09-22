@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeId
import org.graphiks.kanvas.render.ir.ImmutableUBytes
import org.graphiks.kanvas.render.ir.MaskBlurStyle
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.vector.Vector2F64

/** Axis is explicit so a frozen separable operation cannot be reselected by the renderer. */
public enum class FilterAxisV1 { X, Y }

/** The complete W6b implementation vocabulary; later tasks add execution, not another pass kind. */
public enum class FilterImplementationKindV1 {
    IMAGE_BLUR_X,
    IMAGE_BLUR_Y,
    MASK_COVERAGE_BLUR_X,
    MASK_COVERAGE_BLUR_Y,
    MASK_BLUR_STYLE,
    MASK_SHADER,
    MASK_TABLE,
    DROP_SHADOW_COLORIZE,
    DROP_SHADOW_COMPOSITE,
}

/** Immutable device-space spatial facts and the origin used to localize every filter target. */
public class FilterBoundsPlanV1 internal constructor(
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    requiredInputDeviceI32: RectI32,
    producedOutputDeviceI32: RectI32?,
    targetOriginDeviceI32: Point2I32,
) {
    private val knownContentSnapshotI32 = knownContentDeviceI32?.copy()
    private val desiredOutputSnapshotI32 = desiredOutputDeviceI32.copy()
    private val requiredInputSnapshotI32 = requiredInputDeviceI32.copy()
    private val producedOutputSnapshotI32 = producedOutputDeviceI32?.copy()
    private val targetOriginSnapshotDeviceI32 = Point2I32(targetOriginDeviceI32.x, targetOriginDeviceI32.y)

    init {
        require(!desiredOutputSnapshotI32.isEmpty && !requiredInputSnapshotI32.isEmpty) {
            "Filter bounds require non-empty desired output and required input."
        }
        require(knownContentSnapshotI32?.isEmpty != true && producedOutputSnapshotI32?.isEmpty != true) {
            "Filter bounds cannot retain empty optional regions."
        }
    }

    public fun copyKnownContentDeviceI32(): RectI32? = knownContentSnapshotI32?.copy()
    public fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutputSnapshotI32.copy()
    public fun copyRequiredInputDeviceI32(): RectI32 = requiredInputSnapshotI32.copy()
    public fun copyProducedOutputDeviceI32(): RectI32? = producedOutputSnapshotI32?.copy()
    public fun copyTargetOriginDeviceI32(): Point2I32 =
        Point2I32(targetOriginSnapshotDeviceI32.x, targetOriginSnapshotDeviceI32.y)
}

/** Exact contextual identity for one captured node evaluation; equality-by-value is never a reuse proof. */
public class FilterEvaluationKeyV1 private constructor(
    public val capturedNodeId: CapturedFilterNodeId?,
    public val maskOccurrenceI32: Int?,
    public val boundSourceId: PlanResourceId,
    public val mapping: LayerMappingF64,
    desiredOutputDeviceI32: RectI32,
) {
    private val desiredOutputSnapshotI32 = desiredOutputDeviceI32.copy()

    init {
        require(!desiredOutputSnapshotI32.isEmpty) { "Filter evaluation output must be non-empty." }
        require((capturedNodeId == null) != (maskOccurrenceI32 == null)) {
            "A filter evaluation is either one captured node or one mask occurrence."
        }
        require(maskOccurrenceI32 == null || maskOccurrenceI32 >= 0)
    }

    public fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutputSnapshotI32.copy()

    public companion object {
        public fun of(
            capturedNodeId: CapturedFilterNodeId,
            boundSourceId: PlanResourceId,
            mapping: LayerMappingF64,
            desiredOutputDeviceI32: RectI32,
        ): FilterEvaluationKeyV1 = FilterEvaluationKeyV1(
            capturedNodeId,
            null,
            boundSourceId,
            mapping,
            desiredOutputDeviceI32,
        )

        public fun forMaskOccurrence(
            maskOccurrenceI32: Int,
            boundSourceId: PlanResourceId,
            mapping: LayerMappingF64,
            desiredOutputDeviceI32: RectI32,
        ): FilterEvaluationKeyV1 = FilterEvaluationKeyV1(
            null,
            maskOccurrenceI32,
            boundSourceId,
            mapping,
            desiredOutputDeviceI32,
        )
    }
}

/** Frozen, renderer-readable operation payload.  No arm carries a public filter object. */
public sealed interface FilterPassOperationV1 {
    public val kind: FilterImplementationKindV1
    public val bounds: FilterBoundsPlanV1

    public data class SeparableBlur(
        override val kind: FilterImplementationKindV1,
        public val sigmaF32: Float,
        public val axis: FilterAxisV1,
        public val tileMode: TileMode,
        override val bounds: FilterBoundsPlanV1,
    ) : FilterPassOperationV1 {
        init {
            require(sigmaF32.isFinite() && sigmaF32 >= 0f) { "Blur sigma must be finite and non-negative." }
            require(kind in setOf(
                FilterImplementationKindV1.IMAGE_BLUR_X,
                FilterImplementationKindV1.IMAGE_BLUR_Y,
                FilterImplementationKindV1.MASK_COVERAGE_BLUR_X,
                FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y,
            )) { "Separable blur requires a blur implementation kind." }
            require((kind.name.endsWith("_X")) == (axis == FilterAxisV1.X)) {
                "Separable blur kind and axis must agree."
            }
        }
    }

    public data class MaskBlurStyle(
        public val style: MaskBlurStyle,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_BLUR_STYLE,
    ) : FilterPassOperationV1 {
        init { require(kind == FilterImplementationKindV1.MASK_BLUR_STYLE) }
    }

    public data class MaskShader(
        public val material: MaterialPlanRef,
        public val uniformOffsetI64: Long,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_SHADER,
    ) : FilterPassOperationV1 {
        init { require(uniformOffsetI64 >= 0L && kind == FilterImplementationKindV1.MASK_SHADER) }
    }

    public class MaskTable(
        table: ImmutableUBytes,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_TABLE,
    ) : FilterPassOperationV1 {
        private val tableSnapshot = ImmutableUBytes.copyOf(table.copyToUByteArray())
        init { require(kind == FilterImplementationKindV1.MASK_TABLE) }
        public fun copyTable(): ImmutableUBytes = ImmutableUBytes.copyOf(tableSnapshot.copyToUByteArray())
    }

    public class DropShadowColorize(
        public val color: ColorARGB,
        offsetF64: Vector2F64,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COLORIZE,
    ) : FilterPassOperationV1 {
        private val offsetSnapshotF64 = Vector2F64(offsetF64.x, offsetF64.y)
        init {
            require(offsetSnapshotF64.x.isFinite() && offsetSnapshotF64.y.isFinite() &&
                kind == FilterImplementationKindV1.DROP_SHADOW_COLORIZE)
        }
        public fun copyOffsetF64(): Vector2F64 = Vector2F64(offsetSnapshotF64.x, offsetSnapshotF64.y)
    }

    public data class DropShadowComposite(
        public val mode: CapturedDropShadowModeV1,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COMPOSITE,
    ) : FilterPassOperationV1 {
        init { require(kind == FilterImplementationKindV1.DROP_SHADOW_COMPOSITE) }
    }
}
