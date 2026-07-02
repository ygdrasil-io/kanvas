package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsExponentialEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsExponentialSourceHash

object IntrinsicsExponentialDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.intrinsics_exponential")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:intrinsics_exponential:v1",
        fields = listOf(
            "testCase:i32@0:4",
            "x:f32@4:4",
            "y:f32@8:4",
        ),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = 16L,
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:intrinsics_exponential:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:intrinsics_exponential:v1",
        entryPoint = IntrinsicsExponentialEntryPoint,
        reflectionHash = "reflection:intrinsics_exponential:v1",
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
    )
}
