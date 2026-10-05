package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.plan.ColorSourceProofV1
import org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest

/** Resource declarations only. Color semantics use the existing sealed color-operation emitter. */
internal object W5hRuntimeEffectEmitterV1 {
    fun resourceDeclarations(proof: ColorSourceProofV1): String = proof.runtimeResources.joinToString("\n") { reference ->
        require(proof.authenticatesRuntimeResource(reference))
        val prefix="@group(${reference.groupI32}) @binding(${reference.bindingI32})"
        when(reference.cacheRequest) {
            is PlanCacheResourceRequest.Storage -> {
                val bytes=requireNotNull(reference.resource.buffer).minBindingSizeBytesI64
                "struct W5hStorageBinding${reference.bindingI32} { words: array<u32, ${bytes/4L}>, }\n"+
                    "$prefix var<storage, read> w5hStorage${reference.bindingI32}: W5hStorageBinding${reference.bindingI32};"
            }
            // Sampled textures share the composed texture declaration and decoded image cache.
            is PlanCacheResourceRequest.Texture -> ""
            is PlanCacheResourceRequest.Sampler -> "$prefix var w5hSampler${reference.bindingI32}: sampler;"
        }
    }
}
