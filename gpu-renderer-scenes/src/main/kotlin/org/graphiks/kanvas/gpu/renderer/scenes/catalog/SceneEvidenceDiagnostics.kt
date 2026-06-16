package org.graphiks.kanvas.gpu.renderer.scenes.catalog

private const val RUNTIME_EFFECT_REFUSAL_SCENE_ID = "runtime-effect-refusal-gate-board"

internal fun GPURendererScene<*>.runtimeEffectRefusalGateDiagnostics(): List<String> {
    if (sceneId.value != RUNTIME_EFFECT_REFUSAL_SCENE_ID) return emptyList()
    return listOf(
        "runtimeEffectRefusalMatrix=" +
            "arbitrary-source:RefuseRequired:unsupported.runtime_effect.dynamic_sksl_forbidden," +
            "child-slot:RefuseRequired:unsupported.runtime_effect.child_count," +
            "unsupported-placement:RefuseRequired:unsupported.runtime_effect.route_unaccepted",
        "pmRuntimeEffectRefusalRow=gpu-renderer.runtime-effect-refusals",
        "pmRuntimeEffectRefusalClassification=RefuseRequired",
        "dynamicSourceCompilation=false",
        "childRuntimeEffectSupport=false",
    )
}
