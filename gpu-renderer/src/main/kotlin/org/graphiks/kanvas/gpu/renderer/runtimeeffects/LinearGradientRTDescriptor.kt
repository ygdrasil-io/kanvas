package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectSourceColorContract
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTBindingPlanHash
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTDescriptorVersion
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTEffectId
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTReflectionHash
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTUniformBlockSizeBytes
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientRTUniformSchemaHash
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder

/** Registered descriptor for the linear_gradient_rt runtime effect. */
object LinearGradientRTDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID(LinearGradientRTEffectId)
    val descriptorVersion: GPURuntimeEffectDescriptorVersion =
        GPURuntimeEffectDescriptorVersion(LinearGradientRTDescriptorVersion)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = LinearGradientRTUniformSchemaHash,
        fields = listOf(
            "start:vec4<f32>@0:16",
            "end:vec4<f32>@16:16",
            "startColor:vec4<f32>@32:16",
            "endColor:vec4<f32>@48:16",
        ),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = LinearGradientRTUniformBlockSizeBytes.toLong(),
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = LinearGradientRTBindingPlanHash,
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = LinearGradientRTModuleHash,
        entryPoint = LinearGradientRTEntryPoint,
        reflectionHash = LinearGradientRTReflectionHash,
    )

    val routeContract: GPURuntimeEffectRouteContract = GPURuntimeEffectRouteContract(
        nativeSupported = true,
        cpuOracleOnly = false,
        acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
    )

    val liveEditPlan: GPURuntimeEffectLiveEditPlan = GPURuntimeEffectLiveEditPlan(
        enabled = false,
        descriptorVersion = descriptorVersion,
        validationPolicy = "static",
    )

    val childSlots: List<GPURuntimeEffectChildSlotPlan> = emptyList()

    /** Builds a GPURuntimeEffectDescriptor from the plan properties. */
    fun createDescriptor(): GPURuntimeEffectDescriptor = GPURuntimeEffectDescriptor(
        id = effectId,
        version = descriptorVersion,
        uniformSchema = uniformSchema,
        uniformBlockPlan = uniformBlockPlan,
        childSlots = childSlots,
        resources = resources,
        wgslPlan = wgslPlan,
        routeContract = routeContract,
        liveEditPlan = liveEditPlan,
        sourceColorContract = GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba,
    )
}

/** CPU reference for the same two-stop clamp interpolation as [LinearGradientRTWgsl]. */
object LinearGradientRTCPUOracle : GPURuntimeEffectCPUOracle {
    override fun evaluate(): GPURuntimeEffectOracleResult =
        GPURuntimeEffectOracleResult(
            effectId = LinearGradientRTDescriptor.effectId,
            evidenceHash = CanonicalIdentityEncoder("runtime-effect-cpu-oracle-v2")
                .text("effectId", LinearGradientRTDescriptor.effectId.value)
                .int("descriptorVersion", LinearGradientRTDescriptor.descriptorVersion.value)
                .digestIdentity(),
        )

    override fun evaluateMaterial(
        input: GPURuntimeEffectMaterialEvaluationInput,
    ): GPURuntimeEffectMaterialEvaluationResult {
        val uniformBytes = input.uniformBytes
        if (uniformBytes.size != LinearGradientRTUniformBlockSizeBytes) {
            return GPURuntimeEffectMaterialEvaluationResult.Unsupported(
                GPURuntimeEffectMaterialEvaluationRefusal.PAYLOAD_SIZE,
            )
        }
        if (!input.localPositionX.isFinite() || !input.localPositionY.isFinite()) {
            return GPURuntimeEffectMaterialEvaluationResult.Unsupported(
                GPURuntimeEffectMaterialEvaluationRefusal.NON_FINITE_INPUT,
            )
        }
        val values = ByteBuffer.wrap(uniformBytes).order(ByteOrder.LITTLE_ENDIAN)
            .let { buffer -> List(16) { buffer.float } }
        if (values.any { value -> !value.isFinite() }) {
            return GPURuntimeEffectMaterialEvaluationResult.Unsupported(
                GPURuntimeEffectMaterialEvaluationRefusal.NON_FINITE_INPUT,
            )
        }
        val dx = values[4] - values[0]
        val dy = values[5] - values[1]
        val lengthSquared = dx * dx + dy * dy
        val rawT = if (lengthSquared < 1.0e-12f) {
            -1.0e30f
        } else {
            ((input.localPositionX - values[0]) * dx + (input.localPositionY - values[1]) * dy) /
                lengthSquared
        }
        val t = rawT.coerceIn(0f, 1f)
        val output = List(4) { channel ->
            values[8 + channel] * (1f - t) + values[12 + channel] * t
        }
        return GPURuntimeEffectMaterialEvaluationResult.Color(
            r = output[0],
            g = output[1],
            b = output[2],
            a = output[3],
            evidenceHash = CanonicalIdentityEncoder("runtime-effect-material-evaluation-v2")
                .bytes(
                    "inputAndOutput",
                    ByteBuffer.allocate(8 + uniformBytes.size + output.size * Float.SIZE_BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putFloat(input.localPositionX)
                        .putFloat(input.localPositionY)
                        .put(uniformBytes)
                        .apply { output.forEach(::putFloat) }
                        .array(),
                )
                .digestIdentity(),
        )
    }
}
