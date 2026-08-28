package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsMatrixEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsMatrixSourceHash

object IntrinsicsMatrixDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.intrinsics_matrix")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:intrinsics_matrix:v1",
        fields = listOf(
            "testCase:i32@0:4",
            "input:mat4x4<f32>@16:64",
            "vec:vec4<f32>@80:16",
        ),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = 96L,
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:intrinsics_matrix:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:intrinsics_matrix:v1",
        entryPoint = IntrinsicsMatrixEntryPoint,
        reflectionHash = "reflection:intrinsics_matrix:v1",
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
        wgslSource = org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsMatrixWgsl,
        cpuOracle = IntrinsicsMatrixCPUOracle,
    )
}

object IntrinsicsMatrixCPUOracle : GPURuntimeEffectCPUOracle {
    override fun evaluate() = GPURuntimeEffectOracleResult(
        IntrinsicsMatrixDescriptor.effectId,
        runtimeEffectOracleEvidenceHash(IntrinsicsMatrixDescriptor.effectId, IntrinsicsMatrixDescriptor.descriptorVersion),
    )

    override fun evaluateMaterial(input: GPURuntimeEffectMaterialEvaluationInput): GPURuntimeEffectMaterialEvaluationResult {
        if (input.uniformBytes.size != 96) return GPURuntimeEffectMaterialEvaluationResult.Unsupported(GPURuntimeEffectMaterialEvaluationRefusal.PAYLOAD_SIZE)
        val b = ByteBuffer.wrap(input.uniformBytes).order(ByteOrder.LITTLE_ENDIAN)
        val testCase = b.int; repeat(3) { b.int }
        val m = FloatArray(16) { b.float }; val vector = FloatArray(4) { b.float }
        if ((m + vector.toList()).any { !it.isFinite() }) return GPURuntimeEffectMaterialEvaluationResult.Unsupported(GPURuntimeEffectMaterialEvaluationRefusal.NON_FINITE_INPUT)
        val out = when (testCase) {
            2 -> FloatArray(4) { row -> (0..3).sumOf { col -> (m[col * 4 + row] * vector[col]).toDouble() }.toFloat() }
            else -> floatArrayOf(0f, 0f, 0f, 1f)
        }
        return GPURuntimeEffectMaterialEvaluationResult.Color(out[0], out[1], out[2], out[3], runtimeEffectOracleEvidenceHash(IntrinsicsMatrixDescriptor.effectId, IntrinsicsMatrixDescriptor.descriptorVersion))
    }
}
