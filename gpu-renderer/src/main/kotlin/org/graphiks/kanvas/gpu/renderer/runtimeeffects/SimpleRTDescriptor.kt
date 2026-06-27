package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash

/** Registered descriptor for the simple_rt runtime effect (solid color). */
object SimpleRTDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.simple_rt")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:simple_rt:v1",
        fields = listOf("gColor:vec4<f32>@0:16"),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = 16L,
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:simple_rt:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:simple_rt:v1",
        entryPoint = SimpleRTEntryPoint,
        reflectionHash = "reflection:simple_rt:v1",
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
    )
}

/**
 * CPU oracle for the simple_rt runtime effect.
 * Produces validation-only evidence; not a product fallback path.
 */
object SimpleRTCPUOracle : GPURuntimeEffectCPUOracle {
    override fun evaluate(): GPURuntimeEffectOracleResult =
        GPURuntimeEffectOracleResult(
            effectId = SimpleRTDescriptor.effectId,
            evidenceHash = runtimeEffectOracleEvidenceHash(SimpleRTDescriptor.effectId, SimpleRTDescriptor.descriptorVersion),
        )
}

private fun runtimeEffectOracleEvidenceHash(
    id: GPURuntimeEffectID,
    version: GPURuntimeEffectDescriptorVersion,
): String {
    val input = "runtime-effect-cpu-oracle-v1:${id.value}:${version.value}"
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return "sha256:" + hash.joinToString("") { "%02x".format(it) }
}
