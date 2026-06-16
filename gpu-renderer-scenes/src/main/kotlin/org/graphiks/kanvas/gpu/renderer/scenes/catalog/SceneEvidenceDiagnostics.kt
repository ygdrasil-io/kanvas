package org.graphiks.kanvas.gpu.renderer.scenes.catalog

private const val RUNTIME_EFFECT_REFUSAL_SCENE_ID = "runtime-effect-refusal-gate-board"
private const val TEXT_RESOURCE_BINDING_SCENE_ID = "text-resource-binding-gate-board"

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

internal fun GPURendererScene<*>.textResourceBindingGateDiagnostics(): List<String> {
    if (sceneId.value != TEXT_RESOURCE_BINDING_SCENE_ID) return emptyList()
    return listOf(
        "textResourceBindingRefusalMatrix=" +
            "upload-plan:RefuseRequired:unsupported.text.upload_plan_missing," +
            "binding-layout:RefuseRequired:unsupported.text.binding_layout_unavailable," +
            "stale-generation:RefuseRequired:unsupported.text.artifact_generation_stale," +
            "artifact-registration:RefuseRequired:unsupported.text.artifact_unregistered," +
            "upload-budget:RefuseRequired:unsupported.text.upload_budget_exceeded," +
            "cpu-rendered-texture:RefuseRequired:unsupported.text.cpu_rendered_texture_forbidden",
        "pmTextResourceBindingRow=gpu-renderer.text-resource-binding",
        "pmTextResourceBindingClassification=TargetPrepared",
        "textUploadPlanPromoted=false",
        "glyphAtlasRoutePromoted=false",
    )
}
