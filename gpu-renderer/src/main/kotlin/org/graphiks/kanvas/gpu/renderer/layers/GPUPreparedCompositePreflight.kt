package org.graphiks.kanvas.gpu.renderer.layers

data class GPUPreflightCapabilities(
    val maxTextureSize: Int,
    val maxColorAttachments: Int,
)

object GPUPreparedCompositePreflight {

    fun preflight(
        plan: GPUPreparedCompositePlan,
        capabilities: GPUPreflightCapabilities,
    ): GPUPreparedCompositeLowering {
        if (plan.layers.size > capabilities.maxColorAttachments) {
            return GPUPreparedCompositeLowering.Refused(
                code = GPUPreparedCompositeRefusalCodes.PREFLIGHT,
                operationIndex = null,
                facts = mapOf(
                    "layerCount" to plan.layers.size.toString(),
                    "maxColorAttachments" to capabilities.maxColorAttachments.toString(),
                    "reason" to "Layer count exceeds device color attachment limit",
                ),
            )
        }

        for ((index, layer) in plan.layers.withIndex()) {
            val bounds = layer.bounds
            val maxDim = maxOf(bounds.width, bounds.height)
            if (maxDim > capabilities.maxTextureSize) {
                return GPUPreparedCompositeLowering.Refused(
                    code = GPUPreparedCompositeRefusalCodes.PREFLIGHT,
                    operationIndex = null,
                    facts = mapOf(
                        "layerIndex" to index.toString(),
                        "targetDimension" to maxDim.toString(),
                        "maxTextureSize" to capabilities.maxTextureSize.toString(),
                        "reason" to "Layer target exceeds max texture size",
                    ),
                )
            }
        }

        return GPUPreparedCompositeLowering.Ready(plan)
    }
}
