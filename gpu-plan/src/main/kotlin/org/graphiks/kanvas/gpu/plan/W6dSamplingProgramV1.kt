package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ColorChannel
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.vector.Vector3F32

/** Versioned backend-neutral recipes. Constants are specialized at graph construction,
 * so these programs require only sampled textures: no uniform buffer or sampler allocation.
 * Bindings are group 0, consecutive texture slots, then one RGBA8 render attachment.
 */
public enum class W6dSamplingProgramIdV1(public val inputArityI32: Int) {
    MATRIX_CLAMP_RGBA8_V1(1), MATRIX_REPEAT_RGBA8_V1(1),
    MATRIX_MIRROR_RGBA8_V1(1), MATRIX_DECAL_RGBA8_V1(1),
    DISPLACEMENT_NEAREST_CLAMP_RGBA8_V1(2), MAGNIFIER_NEAREST_CLAMP_RGBA8_V1(1),
    DISTANT_DIFFUSE_RGBA8_V1(1), POINT_DIFFUSE_RGBA8_V1(1), SPOT_DIFFUSE_RGBA8_V1(1),
    DISTANT_SPECULAR_RGBA8_V1(1), POINT_SPECULAR_RGBA8_V1(1), SPOT_SPECULAR_RGBA8_V1(1),
    PICTURE_NEAREST_CLAMP_RGBA8_V1(1),
}

/**
 * Exact Picture texture coordinates after the F64 crop has been checked and rebased during
 * plan construction. Native materialization may bind this immutable I32/F32 payload only.
 */
public class W6dPictureSamplingV1 private constructor(
    outputToInputOffsetTargetLocalI32: Point2I32,
    sourceCropInputTargetLocalF32: RectF32?,
) {
    private val offsetSnapshot = Point2I32(
        outputToInputOffsetTargetLocalI32.x,
        outputToInputOffsetTargetLocalI32.y,
    )
    private val cropSnapshot = sourceCropInputTargetLocalF32?.copy()

    init {
        require(cropSnapshot == null || cropSnapshot.isFinite() && !cropSnapshot.isEmpty)
    }

    public fun copyOutputToInputOffsetTargetLocalI32(): Point2I32 = Point2I32(offsetSnapshot.x, offsetSnapshot.y)
    public fun copySourceCropInputTargetLocalF32(): RectF32? = cropSnapshot?.copy()
    public fun copy(): W6dPictureSamplingV1 = W6dPictureSamplingV1(offsetSnapshot, cropSnapshot)
    public fun matches(other: W6dPictureSamplingV1): Boolean =
        offsetSnapshot == other.offsetSnapshot && cropSnapshot == other.cropSnapshot

    public companion object {
        /** Null means a finite F64 crop cannot be represented by the native F32 contract. */
        public fun ofOrNull(
            sourceSampling: FilterInputSamplingV1,
            sourceRectF64: RectF64?,
            bounds: FilterBoundsPlanV1,
        ): W6dPictureSamplingV1? {
            val offset = sourceSampling.copyOutputToInputOffsetTargetLocalI32()
            val crop = sourceRectF64?.let { source ->
                val origin = bounds.copyTargetOriginDeviceI32()
                val left = source.left - origin.x.toDouble() + offset.x.toDouble()
                val top = source.top - origin.y.toDouble() + offset.y.toDouble()
                val right = source.right - origin.x.toDouble() + offset.x.toDouble()
                val bottom = source.bottom - origin.y.toDouble() + offset.y.toDouble()
                if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
                val result = RectF32(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                if (!result.isFinite() || result.isEmpty) return null
                result
            }
            return W6dPictureSamplingV1(offset, crop)
        }
    }
}

public sealed class W6dSamplingProgramV1(public val programId: W6dSamplingProgramIdV1) {
    public class Tap internal constructor(
        public val offsetXF64: Double,
        public val offsetYF64: Double,
        public val weightF32: Float,
    )

    public class Convolution internal constructor(
        programId: W6dSamplingProgramIdV1,
        taps: List<Tap>,
        public val gainF32: Float,
        public val normalizedBiasF32: Float,
        public val convolveAlpha: Boolean,
    ) : W6dSamplingProgramV1(programId) {
        private val storedTaps = immutableList(taps)
        public fun taps(): List<Tap> = storedTaps
    }

    public class Displacement internal constructor(
        public val xComponentI32: Int,
        public val yComponentI32: Int,
        public val scaleF32: Float,
    ) : W6dSamplingProgramV1(W6dSamplingProgramIdV1.DISPLACEMENT_NEAREST_CLAMP_RGBA8_V1)

    public class Magnifier internal constructor(
        public val centerXF64: Double,
        public val centerYF64: Double,
        public val innerLeftF64: Double,
        public val innerTopF64: Double,
        public val innerRightF64: Double,
        public val innerBottomF64: Double,
        public val zoomF32: Float,
    ) : W6dSamplingProgramV1(W6dSamplingProgramIdV1.MAGNIFIER_NEAREST_CLAMP_RGBA8_V1)

    /** One fixed nearest/decal Picture program with preflighted I32/F32 coordinates. */
    public class Picture(sampling: W6dPictureSamplingV1) :
        W6dSamplingProgramV1(W6dSamplingProgramIdV1.PICTURE_NEAREST_CLAMP_RGBA8_V1) {
        private val samplingSnapshot = sampling.copy()
        public fun copySampling(): W6dPictureSamplingV1 = samplingSnapshot.copy()
    }

    /** Immutable lighting recipe, including mapped 3D facts and the four Sobel edge decisions. */
    public class DistantDiffuse internal constructor(
        direction3F32: Vector3F32,
        public val lightColor: ColorARGB,
        public val mappedSurfaceDepthF32: Float,
        public val kdF32: Float,
        public val sobelSampling: W6dSobelSamplingV1,
    ) : W6dSamplingProgramV1(W6dSamplingProgramIdV1.DISTANT_DIFFUSE_RGBA8_V1) {
        private val directionSnapshot3F32 = Vector3F32(direction3F32.x, direction3F32.y, direction3F32.z)
        init {
            require(directionSnapshot3F32.x.isFinite() && directionSnapshot3F32.y.isFinite() && directionSnapshot3F32.z.isFinite() &&
                mappedSurfaceDepthF32.isFinite() && kdF32.isFinite() && kdF32 >= 0f)
        }
        public fun copyDirection3F32(): Vector3F32 = Vector3F32(directionSnapshot3F32.x, directionSnapshot3F32.y, directionSnapshot3F32.z)
    }

    /** All later lighting variants are sealed as one backend-neutral recipe before validation. */
    public class Lighting internal constructor(
        public val family: LightingFamilyV1,
        parameters: LightingParametersV1,
        public val sobelSampling: W6dSobelSamplingV1,
    ) : W6dSamplingProgramV1(when (family) {
        LightingFamilyV1.POINT_DIFFUSE -> W6dSamplingProgramIdV1.POINT_DIFFUSE_RGBA8_V1
        LightingFamilyV1.SPOT_DIFFUSE -> W6dSamplingProgramIdV1.SPOT_DIFFUSE_RGBA8_V1
        LightingFamilyV1.DISTANT_SPECULAR -> W6dSamplingProgramIdV1.DISTANT_SPECULAR_RGBA8_V1
        LightingFamilyV1.POINT_SPECULAR -> W6dSamplingProgramIdV1.POINT_SPECULAR_RGBA8_V1
        LightingFamilyV1.SPOT_SPECULAR -> W6dSamplingProgramIdV1.SPOT_SPECULAR_RGBA8_V1
        LightingFamilyV1.DISTANT_DIFFUSE -> error("Distant diffuse has its stable recipe.")
    }) {
        private val parametersSnapshot = parameters.copy()
        init {
            require(when (family) {
                LightingFamilyV1.POINT_DIFFUSE, LightingFamilyV1.POINT_SPECULAR -> parametersSnapshot is LightingParametersV1.Point
                LightingFamilyV1.SPOT_DIFFUSE, LightingFamilyV1.SPOT_SPECULAR -> parametersSnapshot is LightingParametersV1.Spot
                LightingFamilyV1.DISTANT_SPECULAR -> parametersSnapshot is LightingParametersV1.Distant
                LightingFamilyV1.DISTANT_DIFFUSE -> false
            })
        }
        public fun copyParameters(): LightingParametersV1 = parametersSnapshot.copy()
    }
}

/** The exact resources of this FilterPass, not a second graph or allocation authority. */
public class W6dFrozenProgramBindingV1 internal constructor(
    public val program: W6dSamplingProgramV1,
    inputs: List<PlanResourceId>,
    public val output: PlanResourceId,
) {
    private val storedInputs = immutableList(inputs)
    init { require(storedInputs.size == program.programId.inputArityI32 && output !in storedInputs) }
    /** Slot 0 is the source, or displacement; slot 1 is the displaced source. */
    public fun inputs(): List<PlanResourceId> = storedInputs
}

/** Called only while constructing the W6 physical pass, before graph validation/freeze. */
internal fun selectW6dSamplingProgram(
    operation: FilterPassOperationV1,
    inputs: List<PlanResourceId>,
    output: PlanResourceId,
): W6dFrozenProgramBindingV1? {
    val program = when (operation) {
        is FilterPassOperationV1.MatrixConvolution -> {
            val size = operation.copyKernelSizeI32()
            val offset = operation.copyKernelOffsetF64()
            val kernel = operation.copyKernel().copyToFloatArray()
            val id = when (operation.tileMode) {
                TileMode.CLAMP -> W6dSamplingProgramIdV1.MATRIX_CLAMP_RGBA8_V1
                TileMode.REPEAT -> W6dSamplingProgramIdV1.MATRIX_REPEAT_RGBA8_V1
                TileMode.MIRROR -> W6dSamplingProgramIdV1.MATRIX_MIRROR_RGBA8_V1
                TileMode.DECAL -> W6dSamplingProgramIdV1.MATRIX_DECAL_RGBA8_V1
            }
            W6dSamplingProgramV1.Convolution(id, kernel.indices.map { index ->
                W6dSamplingProgramV1.Tap(index % size.width - offset.x, index / size.width - offset.y, kernel[index])
            }, operation.gainF32, operation.biasF32 / 255f, operation.convolveAlpha)
        }
        is FilterPassOperationV1.DisplacementMap -> W6dSamplingProgramV1.Displacement(
            operation.xChannel.componentIndex(), operation.yChannel.componentIndex(), operation.scaleF32,
        )
        is FilterPassOperationV1.Magnifier -> {
            val source = operation.copySourceF64()
            W6dSamplingProgramV1.Magnifier(
                (source.left + source.right) / 2.0, (source.top + source.bottom) / 2.0,
                source.left + operation.insetF32, source.top + operation.insetF32,
                source.right - operation.insetF32, source.bottom - operation.insetF32, operation.zoomF32,
            )
        }
        is FilterPassOperationV1.Picture -> W6dSamplingProgramV1.Picture(operation.copyPictureSampling())
        is FilterPassOperationV1.Lighting -> {
            val sampling = requireNotNull(operation.copySobelSamplingOrNull())
            if (operation.family == LightingFamilyV1.DISTANT_DIFFUSE) {
                val parameters = operation.copyParameters() as LightingParametersV1.Distant
                W6dSamplingProgramV1.DistantDiffuse(
                    parameters.copyDirection3F32(), parameters.lightColor, parameters.surfaceDepthF32,
                    parameters.coefficientF32, sampling,
                )
            } else W6dSamplingProgramV1.Lighting(operation.family, operation.copyParameters(), sampling)
        }
        else -> return null
    }
    return W6dFrozenProgramBindingV1(program, inputs, output)
}

private fun ColorChannel.componentIndex(): Int = when (this) {
    ColorChannel.RED -> 0
    ColorChannel.GREEN -> 1
    ColorChannel.BLUE -> 2
    ColorChannel.ALPHA -> 3
}
