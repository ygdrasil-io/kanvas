package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterTernaryEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterTernarySourceHash

object ColorFilterTernaryDescriptor {
    val effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.color_filter_ternary")
    val descriptorVersion: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1)

    val uniformSchema: GPURuntimeEffectUniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:color_filter_ternary:v1",
        fields = listOf(
            "condition:f32@0:4",
            "colorTrue:vec4<f32>@16:16",
            "colorFalse:vec4<f32>@32:16",
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
        bindingPlanHash = "binding:color_filter_ternary:v1",
    )

    val wgslPlan: GPURuntimeEffectWGSLPlan = GPURuntimeEffectWGSLPlan(
        moduleHash = "module:color_filter_ternary:v1",
        entryPoint = ColorFilterTernaryEntryPoint,
        reflectionHash = "reflection:color_filter_ternary:v1",
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
