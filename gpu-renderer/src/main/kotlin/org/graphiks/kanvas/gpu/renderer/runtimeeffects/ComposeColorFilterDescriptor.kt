package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.wgsl.ComposeColorFilterEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ComposeColorFilterSourceHash

object ComposeColorFilterDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.compose_cf")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:compose_cf:v1",
        fields = emptyList(),
        packingPolicy = "std140",
    )

    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
        schema = uniformSchema, blockSizeBytes = 0L, dynamicOffsets = false,
    )

    val resources: GPURuntimeEffectResourcePlan = GPURuntimeEffectResourcePlan(
        resourceLabels = listOf("group1.binding0.uniformBuffer"),
        bindingPlanHash = "binding:compose_cf:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:compose_cf:v1",
        entryPoint = ComposeColorFilterEntryPoint,
        reflectionHash = "reflection:compose_cf:v1",
    )

    val routeContract: GPURuntimeEffectRouteContract = GPURuntimeEffectRouteContract(
        nativeSupported = true, cpuOracleOnly = false,
        acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
    )

    val liveEditPlan: GPURuntimeEffectLiveEditPlan = GPURuntimeEffectLiveEditPlan(
        enabled = false, descriptorVersion = descriptorVersion, validationPolicy = "static",
    )

    val childSlots: List<GPURuntimeEffectChildSlotPlan> = listOf(
        GPURuntimeEffectChildSlotPlan("inner", setOf("color-filter"), required = true),
        GPURuntimeEffectChildSlotPlan("outer", setOf("color-filter"), required = true),
    )

    fun createDescriptor(): GPURuntimeEffectDescriptor = GPURuntimeEffectDescriptor(
        id = effectId, version = descriptorVersion,
        uniformSchema = uniformSchema, uniformBlockPlan = uniformBlockPlan,
        childSlots = childSlots, resources = resources, wgslPlan = wgslPlan,
        routeContract = routeContract, liveEditPlan = liveEditPlan,
    )
}
