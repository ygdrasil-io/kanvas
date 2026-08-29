package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.sin

import org.graphiks.kanvas.gpu.renderer.wgsl.SpiralRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SpiralRTSourceHash

/** Registered descriptor for the spiral_rt runtime effect. */
object SpiralRTDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.spiral_rt")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:spiral_rt:v1",
        fields = listOf(
            "center:vec4<f32>@0:16",
            "color1:vec4<f32>@16:16",
            "color2:vec4<f32>@32:16",
            "params:vec4<f32>@48:16",
        ),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = 64L,
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:spiral_rt:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:spiral_rt:v1",
        entryPoint = SpiralRTEntryPoint,
        reflectionHash = "reflection:spiral_rt:v1",
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
        kind = GPURuntimeEffectKind.Material,
        wgslSource = org.graphiks.kanvas.gpu.renderer.wgsl.SpiralRTWgsl,
        cpuOracle = SpiralRTCPUOracle,
    )
}

object SpiralRTCPUOracle : GPURuntimeEffectCPUOracle {
    override fun evaluate() = GPURuntimeEffectOracleResult(
        SpiralRTDescriptor.effectId,
        runtimeEffectOracleEvidenceHash(SpiralRTDescriptor.effectId, SpiralRTDescriptor.descriptorVersion),
    )

    override fun evaluateMaterial(input: GPURuntimeEffectMaterialEvaluationInput): GPURuntimeEffectMaterialEvaluationResult {
        if (input.uniformBytes.size != 64 || !input.localPositionX.isFinite() || !input.localPositionY.isFinite())
            return GPURuntimeEffectMaterialEvaluationResult.Unsupported(GPURuntimeEffectMaterialEvaluationRefusal.PAYLOAD_SIZE)
        val v = ByteBuffer.wrap(input.uniformBytes).order(ByteOrder.LITTLE_ENDIAN).let { b -> List(16) { b.float } }
        if (v.any { !it.isFinite() }) return GPURuntimeEffectMaterialEvaluationResult.Unsupported(GPURuntimeEffectMaterialEvaluationRefusal.NON_FINITE_INPUT)
        val dx = input.localPositionX - v[0]; val dy = input.localPositionY - v[1]
        val spiral = sin(atan2(dy, dx) + sqrt(dx * dx + dy * dy) * v[12]) * .5f + .5f
        return GPURuntimeEffectMaterialEvaluationResult.Color(
            v[4] * (1 - spiral) + v[8] * spiral, v[5] * (1 - spiral) + v[9] * spiral,
            v[6] * (1 - spiral) + v[10] * spiral, v[7] * (1 - spiral) + v[11] * spiral,
            runtimeEffectOracleEvidenceHash(SpiralRTDescriptor.effectId, SpiralRTDescriptor.descriptorVersion),
        )
    }
}
