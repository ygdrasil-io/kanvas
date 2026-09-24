@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1
import org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32
import org.graphiks.kanvas.render.ir.ColorChannel
import org.graphiks.kanvas.render.ir.ImmutableUBytes
import org.graphiks.kanvas.render.ir.ImmutableFloats
import org.graphiks.kanvas.render.ir.MaskBlurStyle
import org.graphiks.kanvas.render.ir.RuntimeEffectAbi
import org.graphiks.kanvas.render.ir.RuntimeEffectDescriptor
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.vector.Vector2F64
import org.graphiks.math.vector.Vector3F32

/** Axis is explicit so a frozen separable operation cannot be reselected by the renderer. */
public enum class FilterAxisV1 { X, Y }

/** The complete W6b implementation vocabulary; later tasks add execution, not another pass kind. */
public enum class FilterImplementationKindV1 {
    /** W6c source-domain Crop. */
    CROP,
    /** W6c source translation. */
    OFFSET,
    /** W6c periodic source sampling constrained to a destination domain. */
    TILE,
    /** W6c image color filter using the already sealed W5f numeric graph. */
    COLOR_FILTER,
    /** W6c ordered source-over composition of every frozen Merge input. */
    MERGE_COMPOSITE,
    /** W6c ordered background/foreground composition using a frozen W5 BlendPlan. */
    BLEND_COMPOSITE,
    /** W6c frozen horizontal morphology extrema pass. */
    MORPHOLOGY_X,
    /** W6c frozen vertical morphology extrema pass. */
    MORPHOLOGY_Y,
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
    MATRIX_CONVOLUTION,
    DISPLACEMENT_MAP,
    MAGNIFIER,
    DISTANT_DIFFUSE,
    POINT_DIFFUSE,
    SPOT_DIFFUSE,
    DISTANT_SPECULAR,
    POINT_SPECULAR,
    SPOT_SPECULAR,
    PICTURE,
    RUNTIME_IMAGE_OPACITY,
}

/** The six W6d lighting variants remain one frozen FilterPass operation family. */
public enum class LightingFamilyV1 {
    DISTANT_DIFFUSE, POINT_DIFFUSE, SPOT_DIFFUSE,
    DISTANT_SPECULAR, POINT_SPECULAR, SPOT_SPECULAR,
}

/** Immutable mapped light geometry selected by planning; renderers receive no public filter node. */
public sealed interface LightingParametersV1 {
    public fun copy(): LightingParametersV1

    public class Distant(
        direction3F32: Vector3F32,
        public val lightColor: ColorARGB,
        public val surfaceDepthF32: Float,
        public val coefficientF32: Float,
        public val shininessF32: Float? = null,
    ) : LightingParametersV1 {
        private val directionSnapshot3F32 = Vector3F32(direction3F32.x, direction3F32.y, direction3F32.z)
        init {
            require(directionSnapshot3F32.x.isFinite() && directionSnapshot3F32.y.isFinite() && directionSnapshot3F32.z.isFinite() &&
                surfaceDepthF32.isFinite() && coefficientF32.isFinite() && coefficientF32 >= 0f &&
                (shininessF32 == null || shininessF32.isFinite()))
        }
        public fun copyDirection3F32(): Vector3F32 = Vector3F32(directionSnapshot3F32.x, directionSnapshot3F32.y, directionSnapshot3F32.z)
        override fun copy(): LightingParametersV1 = Distant(copyDirection3F32(), lightColor, surfaceDepthF32, coefficientF32, shininessF32)
    }

    public class Point(
        location3F32: Point3F32,
        public val lightColor: ColorARGB,
        public val surfaceScaleF32: Float,
        public val coefficientF32: Float,
        public val shininessF32: Float? = null,
    ) : LightingParametersV1 {
        private val locationSnapshot3F32 = Point3F32(location3F32.x, location3F32.y, location3F32.z)
        init {
            require(locationSnapshot3F32.x.isFinite() && locationSnapshot3F32.y.isFinite() && locationSnapshot3F32.z.isFinite() &&
                surfaceScaleF32.isFinite() && coefficientF32.isFinite() && coefficientF32 >= 0f &&
                (shininessF32 == null || shininessF32.isFinite()))
        }
        public fun copyLocation3F32(): Point3F32 = Point3F32(locationSnapshot3F32.x, locationSnapshot3F32.y, locationSnapshot3F32.z)
        override fun copy(): LightingParametersV1 = Point(copyLocation3F32(), lightColor, surfaceScaleF32, coefficientF32, shininessF32)
    }

    public class Spot(
        location3F32: Point3F32,
        target3F32: Point3F32,
        direction3F32: Vector3F32,
        public val specularExponentF32: Float,
        public val cutoffCosineF32: Float,
        public val lightColor: ColorARGB,
        public val surfaceScaleF32: Float,
        public val coefficientF32: Float,
        public val shininessF32: Float? = null,
    ) : LightingParametersV1 {
        private val locationSnapshot3F32 = Point3F32(location3F32.x, location3F32.y, location3F32.z)
        private val targetSnapshot3F32 = Point3F32(target3F32.x, target3F32.y, target3F32.z)
        private val directionSnapshot3F32 = Vector3F32(direction3F32.x, direction3F32.y, direction3F32.z)
        init {
            require(listOf(locationSnapshot3F32.x, locationSnapshot3F32.y, locationSnapshot3F32.z,
                targetSnapshot3F32.x, targetSnapshot3F32.y, targetSnapshot3F32.z,
                directionSnapshot3F32.x, directionSnapshot3F32.y, directionSnapshot3F32.z).all(Float::isFinite) &&
                listOf(specularExponentF32, cutoffCosineF32, surfaceScaleF32, coefficientF32).all(Float::isFinite) &&
                cutoffCosineF32 in -1f..1f && coefficientF32 >= 0f &&
                (shininessF32 == null || shininessF32.isFinite()))
        }
        public fun copyLocation3F32(): Point3F32 = Point3F32(locationSnapshot3F32.x, locationSnapshot3F32.y, locationSnapshot3F32.z)
        public fun copyTarget3F32(): Point3F32 = Point3F32(targetSnapshot3F32.x, targetSnapshot3F32.y, targetSnapshot3F32.z)
        public fun copyDirection3F32(): Vector3F32 = Vector3F32(directionSnapshot3F32.x, directionSnapshot3F32.y, directionSnapshot3F32.z)
        override fun copy(): LightingParametersV1 = Spot(copyLocation3F32(), copyTarget3F32(), copyDirection3F32(), specularExponentF32,
            cutoffCosineF32, lightColor, surfaceScaleF32, coefficientF32, shininessF32)
    }
}

/** Per-edge alpha-Sobel sampling is frozen with the lighting pass, never inferred from an attachment. */
public enum class W6dSobelEdgeModeV1 { CLAMP, DECAL }

public class W6dSobelSamplingV1 internal constructor(
    outputToInputOffsetTargetLocalI32: Point2I32,
    childOutputTargetLocalI32: RectI32,
    requiredInputTargetLocalI32: RectI32,
    public val leftMode: W6dSobelEdgeModeV1,
    public val topMode: W6dSobelEdgeModeV1,
    public val rightMode: W6dSobelEdgeModeV1,
    public val bottomMode: W6dSobelEdgeModeV1,
) {
    private val offsetSnapshot = Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    private val childSnapshot = childOutputTargetLocalI32.copy()
    private val requiredSnapshot = requiredInputTargetLocalI32.copy()

    init {
        require(!childSnapshot.isEmpty && !requiredSnapshot.isEmpty)
    }

    public fun copyOutputToInputOffsetTargetLocalI32(): Point2I32 = Point2I32(offsetSnapshot.x, offsetSnapshot.y)
    public fun copyChildOutputTargetLocalI32(): RectI32 = childSnapshot.copy()
    public fun copyRequiredInputTargetLocalI32(): RectI32 = requiredSnapshot.copy()
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

/** A target-local spatial sampler sealed by planning, retaining fractional F64 clips. */
public class SpatialSamplingV1 internal constructor(
    sourceInputTargetLocalI32: RectI32,
    clipOutputTargetLocalF64: RectF64,
    outputToInputOffsetTargetLocalF64: Vector2F64,
) {
    private val sourceSnapshot = sourceInputTargetLocalI32.copy()
    private val clipSnapshot = clipOutputTargetLocalF64.copy()
    private val offsetSnapshot = Vector2F64(outputToInputOffsetTargetLocalF64.x, outputToInputOffsetTargetLocalF64.y)

    init {
        require(!sourceSnapshot.isEmpty && !clipSnapshot.isEmpty && clipSnapshot.isFinite() &&
            offsetSnapshot.x.isFinite() && offsetSnapshot.y.isFinite())
    }

    public fun copySourceInputTargetLocalI32(): RectI32 = sourceSnapshot.copy()
    public fun copyClipOutputTargetLocalF64(): RectF64 = clipSnapshot.copy()
    public fun copyOutputToInputOffsetTargetLocalF64(): Vector2F64 =
        Vector2F64(offsetSnapshot.x, offsetSnapshot.y)
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

/**
 * Immutable ownership proof for a W6d Picture evaluation.  The table canonical ID identifies
 * content only, so it is deliberately paired with the frame-local positive occurrence rather
 * than being used to deduplicate two equal captured tables.
 */
public class PictureFilterEvaluationProvenanceV1 private constructor(
    public val capturedFilterTableCanonicalId: String,
    public val filterOccurrenceIdI32: Int,
) {
    init {
        require(capturedFilterTableCanonicalId.isNotBlank() && filterOccurrenceIdI32 >= 0) {
            "Picture evaluation provenance requires a captured table and non-negative occurrence."
        }
    }

    internal fun matches(owner: PictureAggregateOwnerV1.FilterPicture): Boolean =
        capturedFilterTableCanonicalId == owner.capturedFilterTableCanonicalId &&
            filterOccurrenceIdI32 == owner.filterOccurrenceIdI32

    public fun copy(): PictureFilterEvaluationProvenanceV1 = PictureFilterEvaluationProvenanceV1(
        capturedFilterTableCanonicalId,
        filterOccurrenceIdI32,
    )

    public companion object {
        internal fun of(
            capturedFilterTableCanonicalId: String,
            filterOccurrenceIdI32: Int,
        ): PictureFilterEvaluationProvenanceV1 = PictureFilterEvaluationProvenanceV1(
            capturedFilterTableCanonicalId,
            filterOccurrenceIdI32,
        )
    }
}

/** Exact contextual identity for one captured node evaluation; equality-by-value is never a reuse proof. */
public class FilterEvaluationKeyV1 private constructor(
    public val capturedNodeId: CapturedFilterNodeIdI32?,
    public val maskOccurrenceI32: Int?,
    public val boundSourceId: PlanResourceId,
    /**
     * Immutable source revision captured with this occurrence.  Null deliberately means that
     * this evaluation is not cacheable across frames: a renderer must never guess a source
     * generation from a physical resource id.
    */
    public val sourceRevisionIdentity: String?,
    pictureProvenance: PictureFilterEvaluationProvenanceV1?,
    public val mapping: LayerMappingF64,
    desiredOutputDeviceI32: RectI32,
) {
    private val desiredOutputSnapshotI32 = desiredOutputDeviceI32.copy()
    private val pictureProvenanceSnapshot = pictureProvenance?.copy()

    init {
        require(!desiredOutputSnapshotI32.isEmpty) { "Filter evaluation output must be non-empty." }
        require((capturedNodeId == null) != (maskOccurrenceI32 == null)) {
            "A filter evaluation is either one captured node or one mask occurrence."
        }
        require(maskOccurrenceI32 == null || maskOccurrenceI32 >= 0)
        require(pictureProvenanceSnapshot == null || capturedNodeId != null) {
            "Picture evaluation provenance requires a captured node evaluation."
        }
    }

    public fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutputSnapshotI32.copy()
    public fun copyPictureProvenanceOrNull(): PictureFilterEvaluationProvenanceV1? = pictureProvenanceSnapshot?.copy()

    public companion object {
        public fun of(
            capturedNodeId: CapturedFilterNodeIdI32,
            boundSourceId: PlanResourceId,
            mapping: LayerMappingF64,
            desiredOutputDeviceI32: RectI32,
            sourceRevisionIdentity: String? = null,
            pictureProvenance: PictureFilterEvaluationProvenanceV1? = null,
        ): FilterEvaluationKeyV1 = FilterEvaluationKeyV1(
            capturedNodeId,
            null,
            boundSourceId,
            sourceRevisionIdentity,
            pictureProvenance,
            mapping,
            desiredOutputDeviceI32,
        )

        public fun forMaskOccurrence(
            maskOccurrenceI32: Int,
            boundSourceId: PlanResourceId,
            mapping: LayerMappingF64,
            desiredOutputDeviceI32: RectI32,
            sourceRevisionIdentity: String? = null,
        ): FilterEvaluationKeyV1 = FilterEvaluationKeyV1(
            null,
            maskOccurrenceI32,
            boundSourceId,
            sourceRevisionIdentity,
            null,
            mapping,
            desiredOutputDeviceI32,
        )
    }
}

/**
 * The sole executable input of a filter-owned Picture leaf.  It snapshots the source aggregate
 * resource and generation after Seal; the captured scene remains planner provenance only.
 */
public class SealedPictureFilterSourceV1 internal constructor(
    public val aggregateId: PictureStreamAggregateIdI32,
    public val resourceId: PlanResourceId,
    public val sourceGenerationI64: Long,
    sampling: FilterInputSamplingV1,
    owner: PictureAggregateOwnerV1.FilterPicture,
) {
    private val samplingSnapshot = sampling
    private val ownerSnapshot = PictureAggregateOwnerV1.FilterPicture(
        owner.capturedNodeId,
        owner.capturedFilterTableCanonicalId,
        owner.filterOccurrenceIdI32,
        owner.occurrenceSourceSceneCanonicalId,
        owner.sourceSceneCanonicalId,
        owner.sourceCommandIndexI32,
        owner.sourcePathI32(),
    )

    init {
        require(sourceGenerationI64 >= 0L)
    }

    public fun copySampling(): FilterInputSamplingV1 = samplingSnapshot
    public fun copyOwner(): PictureAggregateOwnerV1.FilterPicture = PictureAggregateOwnerV1.FilterPicture(
        ownerSnapshot.capturedNodeId,
        ownerSnapshot.capturedFilterTableCanonicalId,
        ownerSnapshot.filterOccurrenceIdI32,
        ownerSnapshot.occurrenceSourceSceneCanonicalId,
        ownerSnapshot.sourceSceneCanonicalId,
        ownerSnapshot.sourceCommandIndexI32,
        ownerSnapshot.sourcePathI32(),
    )
    public fun authenticates(evaluationKey: FilterEvaluationKeyV1): Boolean = ownerSnapshot.authenticates(evaluationKey)
}

/** Frozen, renderer-readable operation payload.  No arm carries a public filter object. */
public sealed interface FilterPassOperationV1 {
    public val kind: FilterImplementationKindV1
    public val bounds: FilterBoundsPlanV1

    public class MatrixConvolution(
        kernelSizeI32: SizeI32,
        kernel: ImmutableFloats,
        public val gainF32: Float,
        public val biasF32: Float,
        kernelOffsetF64: Vector2F64,
        public val tileMode: TileMode,
        public val convolveAlpha: Boolean,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MATRIX_CONVOLUTION,
    ) : FilterPassOperationV1 {
        private val kernelSizeSnapshotI32 = kernelSizeI32.copy()
        private val kernelSnapshot = ImmutableFloats.copyOf(kernel.copyToFloatArray())
        private val kernelOffsetSnapshotF64 = Vector2F64(kernelOffsetF64.x, kernelOffsetF64.y)
        init {
            require(kind == FilterImplementationKindV1.MATRIX_CONVOLUTION && gainF32.isFinite() && biasF32.isFinite() &&
                kernelOffsetSnapshotF64.x.isFinite() && kernelOffsetSnapshotF64.y.isFinite())
            require(kernelSizeSnapshotI32.width > 0 && kernelSizeSnapshotI32.height > 0 &&
                kernelSnapshot.sizeI32 == Math.multiplyExact(kernelSizeSnapshotI32.width, kernelSizeSnapshotI32.height) &&
                kernelSnapshot.copyToFloatArray().all(Float::isFinite))
        }
        public fun copyKernelSizeI32(): SizeI32 = kernelSizeSnapshotI32.copy()
        public fun copyKernel(): ImmutableFloats = ImmutableFloats.copyOf(kernelSnapshot.copyToFloatArray())
        public fun copyKernelOffsetF64(): Vector2F64 = Vector2F64(kernelOffsetSnapshotF64.x, kernelOffsetSnapshotF64.y)
    }

    public class DisplacementMap(
        public val xChannel: ColorChannel,
        public val yChannel: ColorChannel,
        public val scaleF32: Float,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.DISPLACEMENT_MAP,
    ) : FilterPassOperationV1 {
        init { require(kind == FilterImplementationKindV1.DISPLACEMENT_MAP && scaleF32.isFinite()) }
    }

    public class Magnifier(
        sourceF64: RectF64,
        public val zoomF32: Float,
        public val insetF32: Float,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MAGNIFIER,
    ) : FilterPassOperationV1 {
        private val sourceSnapshotF64 = sourceF64.copy()
        init { require(kind == FilterImplementationKindV1.MAGNIFIER && sourceSnapshotF64.isFinite() && !sourceSnapshotF64.isEmpty &&
            zoomF32.isFinite() && zoomF32 > 0f && insetF32.isFinite() && insetF32 >= 0f) }
        public fun copySourceF64(): RectF64 = sourceSnapshotF64.copy()
    }

    public class Lighting(
        public val family: LightingFamilyV1,
        parameters: LightingParametersV1,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1,
        sobelSampling: W6dSobelSamplingV1? = null,
    ) : FilterPassOperationV1 {
        private val parametersSnapshot = parameters.copy()
        private val sobelSamplingSnapshot = sobelSampling
        init {
            require(kind == when (family) {
                LightingFamilyV1.DISTANT_DIFFUSE -> FilterImplementationKindV1.DISTANT_DIFFUSE
                LightingFamilyV1.POINT_DIFFUSE -> FilterImplementationKindV1.POINT_DIFFUSE
                LightingFamilyV1.SPOT_DIFFUSE -> FilterImplementationKindV1.SPOT_DIFFUSE
                LightingFamilyV1.DISTANT_SPECULAR -> FilterImplementationKindV1.DISTANT_SPECULAR
                LightingFamilyV1.POINT_SPECULAR -> FilterImplementationKindV1.POINT_SPECULAR
                LightingFamilyV1.SPOT_SPECULAR -> FilterImplementationKindV1.SPOT_SPECULAR
            })
            require(when (family) {
                LightingFamilyV1.DISTANT_DIFFUSE,
                LightingFamilyV1.DISTANT_SPECULAR,
                -> parametersSnapshot is LightingParametersV1.Distant
                LightingFamilyV1.POINT_DIFFUSE,
                LightingFamilyV1.POINT_SPECULAR,
                -> parametersSnapshot is LightingParametersV1.Point
                LightingFamilyV1.SPOT_DIFFUSE,
                LightingFamilyV1.SPOT_SPECULAR,
                -> parametersSnapshot is LightingParametersV1.Spot
            })
            require(sobelSamplingSnapshot != null) {
                "Every W6d lighting family carries its frozen Sobel sampling recipe."
            }
        }
        public fun copyParameters(): LightingParametersV1 = parametersSnapshot.copy()
        public fun copySobelSamplingOrNull(): W6dSobelSamplingV1? = sobelSamplingSnapshot
    }

    public class Picture(
        sealedSource: SealedPictureFilterSourceV1,
        cullRectF64: RectF64,
        override val bounds: FilterBoundsPlanV1,
        sampling: W6dPictureSamplingV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.PICTURE,
    ) : FilterPassOperationV1 {
        private val sealedSourceSnapshot = SealedPictureFilterSourceV1(
            sealedSource.aggregateId,
            sealedSource.resourceId,
            sealedSource.sourceGenerationI64,
            sealedSource.copySampling(),
            sealedSource.copyOwner(),
        )
        private val cullRectSnapshotF64 = cullRectF64.copy()
        private val samplingSnapshot = sampling.copy()
        init { require(kind == FilterImplementationKindV1.PICTURE && cullRectSnapshotF64.isFinite() && !cullRectSnapshotF64.isEmpty &&
            samplingSnapshot.copyOutputToInputOffsetTargetLocalI32() == sealedSourceSnapshot.copySampling()
                .copyOutputToInputOffsetTargetLocalI32()) }
        public fun copySealedSource(): SealedPictureFilterSourceV1 = SealedPictureFilterSourceV1(
            sealedSourceSnapshot.aggregateId,
            sealedSourceSnapshot.resourceId,
            sealedSourceSnapshot.sourceGenerationI64,
            sealedSourceSnapshot.copySampling(),
            sealedSourceSnapshot.copyOwner(),
        )
        public fun copyCullRectF64(): RectF64 = cullRectSnapshotF64.copy()
        public fun copyPictureSampling(): W6dPictureSamplingV1 = samplingSnapshot.copy()
    }

    public class RuntimeImageOpacity(
        public val effect: RuntimeEffectDescriptor,
        public val alphaF32: Float,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.RUNTIME_IMAGE_OPACITY,
    ) : FilterPassOperationV1 {
        init { require(kind == FilterImplementationKindV1.RUNTIME_IMAGE_OPACITY && effect.id.value == "kanvas.runtime.image-opacity" &&
            effect.semanticVersionI32 == 1 && effect.abi == RuntimeEffectAbi.IMAGE_FILTER && alphaF32.isFinite() && alphaF32 in 0f..1f) }
    }

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

    public class Crop(
        cropInputTargetLocalI32: RectI32,
        public val tileMode: TileMode,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: SpatialSamplingV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.CROP,
    ) : FilterPassOperationV1 {
        private val cropSnapshotInputTargetLocalI32 = cropInputTargetLocalI32.copy()

        init {
            require(kind == FilterImplementationKindV1.CROP)
            require(!cropSnapshotInputTargetLocalI32.isEmpty)
        }

        public fun copyCropInputTargetLocalI32(): RectI32 = cropSnapshotInputTargetLocalI32.copy()
    }

    public class Offset(
        offsetF64: Vector2F64,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: SpatialSamplingV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.OFFSET,
    ) : FilterPassOperationV1 {
        private val offsetSnapshotF64 = Vector2F64(offsetF64.x, offsetF64.y)
        init { require(kind == FilterImplementationKindV1.OFFSET && offsetSnapshotF64.x.isFinite() && offsetSnapshotF64.y.isFinite()) }
        public fun copyOffsetF64(): Vector2F64 = Vector2F64(offsetSnapshotF64.x, offsetSnapshotF64.y)
    }

    public class Tile(
        sourceInputTargetLocalI32: RectI32,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: SpatialSamplingV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.TILE,
    ) : FilterPassOperationV1 {
        private val sourceSnapshotInputTargetLocalI32 = sourceInputTargetLocalI32.copy()
        init { require(kind == FilterImplementationKindV1.TILE && !sourceSnapshotInputTargetLocalI32.isEmpty) }
        public fun copySourceInputTargetLocalI32(): RectI32 = sourceSnapshotInputTargetLocalI32.copy()
    }

    /**
     * The W5f execution and its sole FrameSourceLayoutV4 uniform row are frozen together.  This
     * is deliberately a texture consumer, not a second color-filter evaluator.
     */
    public class ColorFilter(
        public val execution: ColorFilterExecutionPlanV1,
        public val uniformResource: PlanResourceId?,
        /** Exact W5f byte-window start in [uniformResource], published by FrameSourceLayoutV4. */
        public val uniformOffsetBytesI64: Long?,
        public val uniformCapacityBytesI64: Long?,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: FilterInputSamplingV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.COLOR_FILTER,
    ) : FilterPassOperationV1 {
        init {
            require(kind == FilterImplementationKindV1.COLOR_FILTER)
            require((uniformResource == null) == (uniformOffsetBytesI64 == null) &&
                (uniformResource == null) == (uniformCapacityBytesI64 == null))
            uniformCapacityBytesI64?.let { capacity ->
                require(uniformResource!!.value.startsWith("${PlanResourceRole.SourceUniformData.name}:"))
                requireW6cColorUniformWindow(requireNotNull(uniformOffsetBytesI64), capacity,
                    execution.dynamicByteCountI64)
            }
        }

        internal fun withUniformBinding(binding: W6cColorUniformBindingV1): ColorFilter =
            ColorFilter(execution, binding.resourceId, binding.offsetBytesI64, binding.capacityBytesI64, bounds, sampling)
    }

    /**
     * Merge preserves every captured input occurrence, including duplicates.  The sampling rows
     * are positional: index N describes [PlanPass.FilterPass.inputs]' index N and are never a
     * canonical-key lookup.
     */
    public class Merge(
        inputSamplings: List<FilterInputSamplingV1>,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.MERGE_COMPOSITE,
    ) : FilterPassOperationV1 {
        private val samplingSnapshot = immutableList(inputSamplings)

        init {
            require(kind == FilterImplementationKindV1.MERGE_COMPOSITE)
            require(samplingSnapshot.isNotEmpty())
        }

        public fun inputSamplings(): List<FilterInputSamplingV1> = samplingSnapshot
    }

    /**
     * Background and foreground keep their public positions.  [blend] is issued by W5 before
     * publication; renderer materialization receives no public BlendMode or replanning input.
     */
    public class Blend(
        public val blend: BlendPlan,
        backgroundSampling: FilterInputSamplingV1,
        foregroundSampling: FilterInputSamplingV1,
        override val bounds: FilterBoundsPlanV1,
        override val kind: FilterImplementationKindV1 = FilterImplementationKindV1.BLEND_COMPOSITE,
    ) : FilterPassOperationV1 {
        private val backgroundSamplingSnapshot = backgroundSampling
        private val foregroundSamplingSnapshot = foregroundSampling

        init { require(kind == FilterImplementationKindV1.BLEND_COMPOSITE) }

        public fun backgroundSampling(): FilterInputSamplingV1 = backgroundSamplingSnapshot
        public fun foregroundSampling(): FilterInputSamplingV1 = foregroundSamplingSnapshot
    }

    /**
     * One axis of a frozen two-pass morphology evaluation.  Both the mapped F64 support and
     * its checked I32 tap count are selected in :gpu-plan; native lowering only consumes them.
     */
    public class Morphology(
        public val morphologyKind: Kind,
        public val radiusXF64: Double,
        public val radiusYF64: Double,
        public val radiusXTexelsI32: Int,
        public val radiusYTexelsI32: Int,
        public val axis: FilterAxisV1,
        override val bounds: FilterBoundsPlanV1,
        public val sampling: FilterInputSamplingV1,
        override val kind: FilterImplementationKindV1,
    ) : FilterPassOperationV1 {
        public enum class Kind { DILATE, ERODE }

        init {
            require(radiusXF64.isFinite() && radiusYF64.isFinite() && radiusXF64 >= 0.0 && radiusYF64 >= 0.0)
            require(radiusXTexelsI32 >= 0 && radiusYTexelsI32 >= 0)
            require((kind == FilterImplementationKindV1.MORPHOLOGY_X) == (axis == FilterAxisV1.X) &&
                (kind == FilterImplementationKindV1.MORPHOLOGY_Y) == (axis == FilterAxisV1.Y)) {
                "Morphology kind and axis must agree."
            }
        }
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
