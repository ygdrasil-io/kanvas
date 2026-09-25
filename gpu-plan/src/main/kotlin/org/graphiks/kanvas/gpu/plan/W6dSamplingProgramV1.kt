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
    RUNTIME_IMAGE_OPACITY_RGBA8_V1(1),
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
        displacementSampling: FilterInputSamplingV1,
        sourceSampling: FilterInputSamplingV1,
    ) : W6dSamplingProgramV1(W6dSamplingProgramIdV1.DISPLACEMENT_NEAREST_CLAMP_RGBA8_V1) {
        private val displacementSamplingSnapshot = displacementSampling.copy()
        private val sourceSamplingSnapshot = sourceSampling.copy()
        public fun copyDisplacementSampling(): FilterInputSamplingV1 = displacementSamplingSnapshot.copy()
        public fun copySourceSampling(): FilterInputSamplingV1 = sourceSamplingSnapshot.copy()
    }

    public class Magnifier internal constructor(
        public val centerXF64: Double,
        public val centerYF64: Double,
        public val innerLeftF64: Double,
        public val innerTopF64: Double,
        public val innerRightF64: Double,
        public val innerBottomF64: Double,
        public val zoomF32: Float,
        inputSampling: FilterInputSamplingV1,
    ) : W6dSamplingProgramV1(W6dSamplingProgramIdV1.MAGNIFIER_NEAREST_CLAMP_RGBA8_V1)
    {
        private val inputSamplingSnapshot = inputSampling.copy()
        public fun copyInputSampling(): FilterInputSamplingV1 = inputSamplingSnapshot.copy()
    }

    /** One fixed nearest/decal Picture program with preflighted I32/F32 coordinates. */
    public class Picture(sampling: W6dPictureSamplingV1) :
        W6dSamplingProgramV1(W6dSamplingProgramIdV1.PICTURE_NEAREST_CLAMP_RGBA8_V1) {
        private val samplingSnapshot = sampling.copy()
        public fun copySampling(): W6dPictureSamplingV1 = samplingSnapshot.copy()
    }

    /** Registered IMAGE_FILTER program with its alpha field already selected from the ABI. */
    public class RuntimeImageOpacity(
        public val alphaF32: Float,
        public val alphaUniformOffsetBytesI32: Int,
    ) : W6dSamplingProgramV1(W6dSamplingProgramIdV1.RUNTIME_IMAGE_OPACITY_RGBA8_V1) {
        init {
            require(alphaF32.isFinite() && alphaF32 in 0f..1f && alphaUniformOffsetBytesI32 == 0)
        }
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

/** Native program preparation needs both module and render-pipeline authority. */
public enum class W6dProgramUsageV1 { ShaderModule, RenderPipeline }

/** A W6d program lease is retained through completion rather than one FilterPass execution. */
public enum class W6dProgramLifetimeV1 { ThroughFrameCompletion }

/**
 * Exact immutable binding identity for one selected W6d recipe.  The owner is intentionally
 * separate: identical recipes in distinct FilterPasses retain independent pessimistic leases.
 */
public class W6dFrozenProgramIdentityV1 internal constructor(
    public val programId: W6dSamplingProgramIdV1,
    inputs: List<PlanResourceId>,
    public val output: PlanResourceId,
) {
    private val storedInputs = immutableList(inputs)
    init { require(storedInputs.size == programId.inputArityI32 && output !in storedInputs) }
    public fun inputs(): List<PlanResourceId> = storedInputs
    public fun matches(program: W6dSamplingProgramV1, inputs: List<PlanResourceId>, output: PlanResourceId): Boolean =
        programId == program.programId && storedInputs == inputs && this.output == output
}

/**
 * Pre-publication descriptor for one W6d module/pipeline request.  Its RGBA8/sample facts are
 * planner facts, never reconstructed by the renderer.
 */
public class W6dProgramDescriptorV1 internal constructor(
    public val identity: W6dFrozenProgramIdentityV1,
    public val targetFormat: PlanLogicalColorFormat,
    public val targetSampleCountI32: Int,
) {
    init {
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        require(targetSampleCountI32 == 1)
    }
}

/**
 * Pessimistic, graph-owned accounting lease for an opaque native W6d program.
 *
 * [reservedBytesI64] is a stable logical admission unit, not a claimed byte size of a browser
 * or driver shader module/pipeline.  [descriptorBytesI64] and [recipePayloadBytesI64] form an
 * exact checked-I64 logical encoding, while the floor conservatively bounds its small forms.
 * Native allocation remains opaque, so no driver-byte-exact claim is made here.
 */
public class W6dProgramLeaseV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val generationI64: Long,
    public val descriptor: W6dProgramDescriptorV1,
    usages: Set<W6dProgramUsageV1>,
    public val lifetime: W6dProgramLifetimeV1,
    public val firstPassIndexI32: Int,
    public val lastPassIndexExclusiveI32: Int,
    public val descriptorBytesI64: Long,
    public val recipePayloadBytesI64: Long,
    public val reservedBytesI64: Long,
) {
    private val storedUsages = immutableSet(usages)
    init {
        require(ownerPassId.value.isNotBlank() && generationI64 >= 0L)
        require(storedUsages == setOf(W6dProgramUsageV1.ShaderModule, W6dProgramUsageV1.RenderPipeline))
        require(lifetime == W6dProgramLifetimeV1.ThroughFrameCompletion)
        require(firstPassIndexI32 == 0 && lastPassIndexExclusiveI32 > firstPassIndexI32)
        require(descriptorBytesI64 > 0L && recipePayloadBytesI64 > 0L &&
            reservedBytesI64 >= Math.addExact(descriptorBytesI64, recipePayloadBytesI64) &&
            reservedBytesI64 >= LOGICAL_LEASE_FLOOR_BYTES_I64)
    }

    public fun usages(): Set<W6dProgramUsageV1> = storedUsages

    /** Renderer-side consumption verifies the exact already-budgeted lease before materialization. */
    public fun matches(binding: W6dFrozenProgramBindingV1, deviceGenerationI64: Long, passCountI32: Int): Boolean =
        ownerPassId == binding.ownerPassId && generationI64 == deviceGenerationI64 &&
            descriptor.identity.matches(binding.program, binding.inputs(), binding.output) &&
            lifetime == W6dProgramLifetimeV1.ThroughFrameCompletion && firstPassIndexI32 == 0 &&
            lastPassIndexExclusiveI32 == passCountI32 &&
            storedUsages == setOf(W6dProgramUsageV1.ShaderModule, W6dProgramUsageV1.RenderPipeline) &&
            reservedBytesI64 >= Math.addExact(descriptorBytesI64, recipePayloadBytesI64) &&
            reservedBytesI64 >= LOGICAL_LEASE_FLOOR_BYTES_I64

    public companion object {
        /** Stable pessimistic lease unit; it is not an opaque driver allocation measurement. */
        public const val LOGICAL_LEASE_FLOOR_BYTES_I64: Long = 4096L
    }
}

/** The exact resources of this FilterPass, not a second graph or allocation authority. */
public class W6dFrozenProgramBindingV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val program: W6dSamplingProgramV1,
    inputs: List<PlanResourceId>,
    public val output: PlanResourceId,
) {
    private val storedInputs = immutableList(inputs)
    public val identity = W6dFrozenProgramIdentityV1(program.programId, storedInputs, output)
    init { require(identity.matches(program, storedInputs, output)) }
    /** Slot 0 is the source, or displacement; slot 1 is the displaced source. */
    public fun inputs(): List<PlanResourceId> = storedInputs
}

/**
 * The sole W6d logical-program lease issuer.  It runs before graph publication and charges a
 * lease per owner even if a native cache later reuses a module or pipeline.
 */
internal object W6dProgramLeasePlannerV1 {
    fun freeze(
        resources: List<PlanResource>,
        passes: List<PlanPass>,
        capabilities: PlanCapabilitySnapshot,
    ): List<W6dProgramLeaseV1> {
        if (passes.isEmpty()) return emptyList()
        val resourcesById = resources.associateBy(PlanResource::id)
        return passes.mapNotNull { candidate ->
            val pass = candidate as? PlanPass.FilterPass ?: return@mapNotNull null
            val binding = pass.frozenSamplingProgram ?: return@mapNotNull null
            require(binding.ownerPassId == pass.id && binding.identity.matches(binding.program, pass.inputs(), pass.output))
            val output = requireNotNull(resourcesById[pass.output]) { "W6d program output has no graph resource." }
            require(output.kind == PlanResourceKind.Texture2D &&
                output.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) &&
                output.sampleCountI32 == 1) { "W6d programs require a frozen RGBA8 single-sample FilterTarget." }
            val descriptorBytesI64 = checkedLogicalDescriptorBytesI64(pass.id, binding)
            val recipePayloadBytesI64 = binding.program.checkedLogicalRecipePayloadBytesI64()
            val encodedPayloadBytesI64 = Math.addExact(descriptorBytesI64, recipePayloadBytesI64)
            W6dProgramLeaseV1(
                ownerPassId = pass.id,
                generationI64 = capabilities.deviceGeneration,
                descriptor = W6dProgramDescriptorV1(binding.identity,
                    PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, 1),
                usages = setOf(W6dProgramUsageV1.ShaderModule, W6dProgramUsageV1.RenderPipeline),
                lifetime = W6dProgramLifetimeV1.ThroughFrameCompletion,
                firstPassIndexI32 = 0,
                lastPassIndexExclusiveI32 = passes.size,
                descriptorBytesI64 = descriptorBytesI64,
                recipePayloadBytesI64 = recipePayloadBytesI64,
                reservedBytesI64 = maxOf(W6dProgramLeaseV1.LOGICAL_LEASE_FLOOR_BYTES_I64, encodedPayloadBytesI64),
            )
        }
    }
}

/** Called only while constructing the W6 physical pass, before graph validation/freeze. */
internal fun selectW6dSamplingProgram(
    ownerPassId: PlanPassId,
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
            operation.copyDisplacementSampling(), operation.copySourceSampling(),
        )
        is FilterPassOperationV1.Magnifier -> {
            val source = operation.copySourceF64()
            W6dSamplingProgramV1.Magnifier(
                (source.left + source.right) / 2.0, (source.top + source.bottom) / 2.0,
                source.left + operation.insetF32, source.top + operation.insetF32,
                source.right - operation.insetF32, source.bottom - operation.insetF32, operation.zoomF32,
                operation.copyInputSampling(),
            )
        }
        is FilterPassOperationV1.Picture -> W6dSamplingProgramV1.Picture(operation.copyPictureSampling())
        is FilterPassOperationV1.RuntimeImageOpacity -> W6dSamplingProgramV1.RuntimeImageOpacity(
            operation.alphaF32,
            operation.alphaUniformOffsetBytesI32,
        )
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
    return W6dFrozenProgramBindingV1(ownerPassId, program, inputs, output)
}

/**
 * Canonical logical payload sizing for the immutable plan record.  A convolution retains one
 * F64/F64/F32 tuple per tap; all remaining shipped W6d recipes have bounded scalar payloads and
 * therefore remain below the lease floor.  Every arithmetic operation is checked-I64.
 */
private fun W6dSamplingProgramV1.checkedLogicalRecipePayloadBytesI64(): Long = when (this) {
    is W6dSamplingProgramV1.Convolution -> Math.addExact(64L,
        Math.multiplyExact(taps().size.toLong(), 24L))
    is W6dSamplingProgramV1.Displacement -> 32L
    is W6dSamplingProgramV1.Magnifier -> 64L
    is W6dSamplingProgramV1.Picture -> 64L
    is W6dSamplingProgramV1.RuntimeImageOpacity -> 32L
    is W6dSamplingProgramV1.DistantDiffuse -> 128L
    is W6dSamplingProgramV1.Lighting -> 256L
}

/**
 * Canonical byte count for the immutable logical lease descriptor.  It records every identity
 * string as UTF-8 plus fixed-width generation, descriptor, usage and lifetime fields; this is
 * an admission encoding, not a serialization of opaque driver objects.
 */
private fun checkedLogicalDescriptorBytesI64(
    ownerPassId: PlanPassId,
    binding: W6dFrozenProgramBindingV1,
): Long = buildList {
    add(48L) // generation, RGBA8/sample, two usages, lifetime and half-open pass range
    add(ownerPassId.value.encodeToByteArray().size.toLong())
    add(binding.program.programId.name.encodeToByteArray().size.toLong())
    binding.inputs().forEach { input -> add(input.value.encodeToByteArray().size.toLong()) }
    add(binding.output.value.encodeToByteArray().size.toLong())
}.fold(0L) { total, bytes -> Math.addExact(total, bytes) }

private fun ColorChannel.componentIndex(): Int = when (this) {
    ColorChannel.RED -> 0
    ColorChannel.GREEN -> 1
    ColorChannel.BLUE -> 2
    ColorChannel.ALPHA -> 3
}
