package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ColorChannel
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.color.ColorARGB
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
