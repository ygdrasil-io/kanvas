package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.wgsl.GChannelSplatEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.GChannelSplatSourceHash

object GChannelSplatDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.g_channel_splat")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:g_channel_splat:v1",
        fields = emptyList(),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema, blockSizeBytes = 0L, dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:g_channel_splat:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:g_channel_splat:v1",
        entryPoint = GChannelSplatEntryPoint,
        reflectionHash = "reflection:g_channel_splat:v1",
    )

    val routeContract: GPURuntimeEffectRouteContract = GPURuntimeEffectRouteContract(
        nativeSupported = true, cpuOracleOnly = false,
        acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
    )

    val liveEditPlan: GPURuntimeEffectLiveEditPlan = GPURuntimeEffectLiveEditPlan(
        enabled = false, descriptorVersion = descriptorVersion, validationPolicy = "static",
    )

    val childSlots: List<GPURuntimeEffectChildSlotPlan> = emptyList()

    fun createDescriptor(): GPURuntimeEffectDescriptor = GPURuntimeEffectDescriptor(
        id = effectId, version = descriptorVersion,
        uniformSchema = uniformSchema, uniformBlockPlan = uniformBlockPlan,
        childSlots = childSlots, resources = resources, wgslPlan = wgslPlan,
        routeContract = routeContract, liveEditPlan = liveEditPlan,
    )
}
