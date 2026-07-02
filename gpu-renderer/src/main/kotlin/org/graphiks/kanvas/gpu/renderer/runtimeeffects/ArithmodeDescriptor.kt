package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.wgsl.ArithmodeEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ArithmodeSourceHash

object ArithmodeDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.arithmode")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:arithmode:v1",
        fields = listOf(
            "mode:i32@0:4",
            "src:vec4<f32>@16:16",
            "dst:vec4<f32>@32:16",
        ),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema,
        blockSizeBytes = 48L,
        dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:arithmode:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:arithmode:v1",
        entryPoint = ArithmodeEntryPoint,
        reflectionHash = "reflection:arithmode:v1",
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
