@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32
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
    /** The W6c Task 1 full-domain 1x1 Crop vertical slice. */
    CROP,
    IMAGE_BLUR_X,
    IMAGE_BLUR_Y,
    MASK_COVERAGE_BLUR_X,
    MASK_COVERAGE_BLUR_Y,
    MASK_BLUR_STYLE,
    MASK_SHADER,
    MASK_TABLE,
    DROP_SHADOW_COLORIZE,
    DROP_SHADOW_COMPOSITE,
    /** Typed W5 shaded source hand-off for a mask-only occurrence. */
    W5_MATERIALIZED_SOURCE,
}

/**
 * One sealed output-local to input-local texel transform.  It is calculated while the W6b
 * graph still owns device geometry; native lowering may only consume this target-local fact.
 */
public class FilterInputSamplingV1 internal constructor(
    outputToInputOffsetTargetLocalI32: Point2I32,
    knownContentInputTargetLocalI32: RectI32,
) {
    private val offsetSnapshotTargetLocalI32 = Point2I32(
        outputToInputOffsetTargetLocalI32.x,
        outputToInputOffsetTargetLocalI32.y,
    )
    private val knownSnapshotInputTargetLocalI32 = knownContentInputTargetLocalI32.copy()

    init {
        require(!knownSnapshotInputTargetLocalI32.isEmpty) {
            "W6b target-local input sampling requires non-empty known content."
        }
    }

    public fun copyOutputToInputOffsetTargetLocalI32(): Point2I32 = Point2I32(
        offsetSnapshotTargetLocalI32.x,
        offsetSnapshotTargetLocalI32.y,
    )
    public fun copyKnownContentInputTargetLocalI32(): RectI32 = knownSnapshotInputTargetLocalI32.copy()
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

/**
 * Sealed local coordinates for DropShadow's internal MatrixTransform-equivalent sampling.
 * The renderer receives no device origins: a fragment-local position plus this F64 vector is
 * the input texel-index coordinate used by the required linear DECAL sample.
 */
public class DropShadowLinearSamplingV1 internal constructor(
    sourceCoordinateOffsetTargetLocalF64: Vector2F64,
    sourceFootprintTargetLocalI32: RectI32,
    outputFootprintTargetLocalI32: RectI32,
) {
    private val sourceCoordinateOffsetSnapshotF64 = Vector2F64(
        sourceCoordinateOffsetTargetLocalF64.x,
        sourceCoordinateOffsetTargetLocalF64.y,
    )
    private val sourceFootprintSnapshotTargetLocalI32 = sourceFootprintTargetLocalI32.copy()
    private val outputFootprintSnapshotTargetLocalI32 = outputFootprintTargetLocalI32.copy()

    init {
        require(sourceCoordinateOffsetSnapshotF64.x.isFinite() && sourceCoordinateOffsetSnapshotF64.y.isFinite())
        require(!sourceFootprintSnapshotTargetLocalI32.isEmpty && !outputFootprintSnapshotTargetLocalI32.isEmpty)
        require(sourceFootprintSnapshotTargetLocalI32.left == 0 && sourceFootprintSnapshotTargetLocalI32.top == 0)
        require(outputFootprintSnapshotTargetLocalI32.left == 0 && outputFootprintSnapshotTargetLocalI32.top == 0)
    }

    public fun copySourceCoordinateOffsetTargetLocalF64(): Vector2F64 = Vector2F64(
        sourceCoordinateOffsetSnapshotF64.x,
        sourceCoordinateOffsetSnapshotF64.y,
    )

    public fun copySourceFootprintTargetLocalI32(): RectI32 = sourceFootprintSnapshotTargetLocalI32.copy()
    public fun copyOutputFootprintTargetLocalI32(): RectI32 = outputFootprintSnapshotTargetLocalI32.copy()
}

/** Exact contextual identity for one captured node evaluation; equality-by-value is never a reuse proof. */
public class FilterEvaluationKeyV1 private constructor(
    public val capturedNodeId: CapturedFilterNodeIdI32?,
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
            capturedNodeId: CapturedFilterNodeIdI32,
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
        /** Sealed before publication; no renderer origin reconstruction is permitted. */
        public val sampling: FilterInputSamplingV1? = null,
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

    /**
     * The initial W6c Crop arm carries only the sealed 1x1 full-domain witness.  Later W6c
     * tasks extend its bounds semantics without asking native lowering to recover public crop
     * geometry or source origins.
     */
    public class Crop(
        cropInputTargetLocalI32: RectI32,
        public val tileMode: TileMode,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: FilterInputSamplingV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.CROP,
    ) : FilterPassOperationV1 {
        private val cropSnapshotInputTargetLocalI32 = cropInputTargetLocalI32.copy()

        init {
            require(kind == FilterImplementationKindV1.CROP)
            require(tileMode == TileMode.CLAMP)
            require(cropSnapshotInputTargetLocalI32 == RectI32(0, 0, 1, 1))
        }

        public fun copyCropInputTargetLocalI32(): RectI32 = cropSnapshotInputTargetLocalI32.copy()
    }

    public data class MaskBlurStyle(
        public val style: org.graphiks.kanvas.render.ir.MaskBlurStyle,
        /** Immutable raw/original coverage retained for SOLID, OUTER and INNER style combination. */
        public val originalCoverageSource: PlanResourceId?,
        /** The preceding separable blurred coverage result. */
        public val blurredCoverageSource: PlanResourceId,
        override val bounds: FilterBoundsPlanV1,
        public val blurredSampling: FilterInputSamplingV1? = null,
        public val originalSampling: FilterInputSamplingV1? = null,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_BLUR_STYLE,
    ) : FilterPassOperationV1 {
        init {
            require(kind == FilterImplementationKindV1.MASK_BLUR_STYLE)
            require(blurredCoverageSource != originalCoverageSource)
            require(if (style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL) originalCoverageSource == null else originalCoverageSource != null) {
                "Mask blur styles that combine coverage require the original immutable coverage source."
            }
        }
    }

    /**
     * A real W5 material reference is deliberately disjoint from a captured/deferred identity.
     * The accompanying uniform resource is issued by the same FrameSourceLayoutV4 publication;
     * it is never an inferred offset or a synthetic zero-sized placeholder.
     */
    public sealed interface MaskShaderMaterialBindingV1 {
        public data class Planned(
            /** Retains the immutable occurrence which owns this W5 row. */
            public val occurrenceIdI32: Int,
            public val material: MaterialPlanRef,
            public val uniformResource: PlanResourceId,
            /** Exact byte window in the W5-issued uniform resource; never a renderer inference. */
            public val uniformOffsetBytesI64: Long,
            public val uniformCapacityBytesI64: Long,
            /** Existing W5 coordinate authority, projected at graph publication without exposing V1 internals. */
            public val materialAuthority: PlanDrawMaterialAuthority,
            /** The occurrence-local F64 mapping already sealed by the filter evaluation. */
            public val evaluationMappingF64: LayerMappingF64,
            /** W5's device-position bridge, sealed at publication; native must not recover it from filter bounds. */
            public val materialDeviceOriginI32: Point2I32,
        ) : MaskShaderMaterialBindingV1 {
            init {
                require(occurrenceIdI32 >= 0)
                require(uniformResource.value.startsWith("${PlanResourceRole.SourceUniformData.name}:"))
                require(uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 > 0L &&
                    uniformOffsetBytesI64 < uniformCapacityBytesI64)
                require(materialAuthority.materialPlanRef() == material)
                require(materialDeviceOriginI32 == evaluationMappingF64.copyLayerOriginDeviceI32())
            }
        }
        /** Actual captured material and occurrence identity, never a canonical-string substitute. */
        public data class CapturedOccurrence(
            public val occurrenceIdI32: Int,
            public val material: org.graphiks.kanvas.render.ir.MaterialNode,
        ) : MaskShaderMaterialBindingV1 {
            init { require(occurrenceIdI32 >= 0) }
        }
    }

    public data class MaskShader(
        public val materialBinding: MaskShaderMaterialBindingV1,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: FilterInputSamplingV1? = null,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_SHADER,
    ) : FilterPassOperationV1 {
        init { require(kind == FilterImplementationKindV1.MASK_SHADER) }
    }

    public class MaskTable(
        table: ImmutableUBytes,
        /** Immutable storage row allocated by the W6b graph, never by native materialization. */
        public val tableResourceId: PlanResourceId,
        /** Explicitly seals the public 256-entry Table contract before graph publication. */
        public val entryCountI32: Int,
        /** One immutable captured generation per occurrence-local table resource. */
        public val generationI64: Long,
        /** Prevents identical content from being treated as a cross-occurrence resource authority. */
        public val ownerMaskOccurrenceI32: Int,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: FilterInputSamplingV1? = null,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MASK_TABLE,
    ) : FilterPassOperationV1 {
        private val tableSnapshot = ImmutableUBytes.copyOf(table.copyToUByteArray())
        init {
            require(kind == FilterImplementationKindV1.MASK_TABLE)
            require(tableResourceId.value.startsWith("${PlanResourceRole.MaskTableData.name}:"))
            require(entryCountI32 == 256 && tableSnapshot.sizeI32 == entryCountI32)
            require(generationI64 >= 0L && ownerMaskOccurrenceI32 >= 0)
        }
        public fun copyTable(): ImmutableUBytes = ImmutableUBytes.copyOf(tableSnapshot.copyToUByteArray())
    }

    /** Freezes the already selected W5 color/material result without inventing a native filter. */
    public data class MaterializedSource(
        override val bounds: FilterBoundsPlanV1,
        public val sourceSampling: FilterInputSamplingV1? = null,
        public val coverageSampling: FilterInputSamplingV1? = null,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.W5_MATERIALIZED_SOURCE,
    ) : FilterPassOperationV1 {
        init { require(kind == FilterImplementationKindV1.W5_MATERIALIZED_SOURCE) }
    }

    public class DropShadowColorize(
        public val color: ColorARGB,
        offsetF64: Vector2F64,
        override val bounds: FilterBoundsPlanV1,
        /** Required for an admitted shadow; retained nullable only for old malformed-graph tests. */
        public val linearSampling: DropShadowLinearSamplingV1? = null,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COLORIZE,
    ) : FilterPassOperationV1 {
        private val offsetSnapshotF64 = Vector2F64(offsetF64.x, offsetF64.y)
        init {
            require(offsetSnapshotF64.x.isFinite() && offsetSnapshotF64.y.isFinite() &&
                kind == FilterImplementationKindV1.DROP_SHADOW_COLORIZE)
        }
        public fun copyOffsetF64(): Vector2F64 = Vector2F64(offsetSnapshotF64.x, offsetSnapshotF64.y)
    }

    public class DropShadowComposite(
        public val mode: CapturedDropShadowModeV1,
        /** Exact captured original input; SHADOW_ONLY deliberately has none. */
        public val originalInput: PlanResourceId?,
        override val bounds: FilterBoundsPlanV1,
        /** Frozen output-local to shadow-local texel offset; native lowering must never derive it. */
        shadowSampleOffsetTargetLocalI32: Point2I32? = null,
        /** Frozen output-local to original-local texel offset for COMPOSITE. */
        originalSampleOffsetTargetLocalI32: Point2I32? = null,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DROP_SHADOW_COMPOSITE,
    ) : FilterPassOperationV1 {
        private val shadowSampleOffsetSnapshotTargetLocalI32 = shadowSampleOffsetTargetLocalI32?.let { Point2I32(it.x, it.y) }
        private val originalSampleOffsetSnapshotTargetLocalI32 = originalSampleOffsetTargetLocalI32?.let { Point2I32(it.x, it.y) }
        init {
            require(kind == FilterImplementationKindV1.DROP_SHADOW_COMPOSITE)
            require((mode == CapturedDropShadowModeV1.SHADOW_ONLY) == (originalInput == null)) {
                "Drop-shadow original input must match its captured mode."
            }
        }
        public fun copyShadowSampleOffsetTargetLocalI32(): Point2I32? =
            shadowSampleOffsetSnapshotTargetLocalI32?.let { Point2I32(it.x, it.y) }
        public fun copyOriginalSampleOffsetTargetLocalI32(): Point2I32? =
            originalSampleOffsetSnapshotTargetLocalI32?.let { Point2I32(it.x, it.y) }
    }
}
