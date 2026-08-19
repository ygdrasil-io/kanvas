package org.graphiks.kanvas.gpu.renderer.layers

import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

class GPUSaveLayerNativeExecutor {

    fun execute(
        materializationRequest: GPUSaveLayerMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUSaveLayerMaterializationResult {
        val materializer = ValidatingSaveLayerMaterializer()
        return materializer.materialize(materializationRequest, context)
    }
}
