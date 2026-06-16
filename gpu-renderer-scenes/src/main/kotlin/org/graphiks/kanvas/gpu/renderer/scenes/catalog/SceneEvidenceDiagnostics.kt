package org.graphiks.kanvas.gpu.renderer.scenes.catalog

private const val RUNTIME_EFFECT_REFUSAL_SCENE_ID = "runtime-effect-refusal-gate-board"
private const val A8_GLYPH_ATLAS_SCENE_ID = "a8-glyph-atlas-gate-board"
private const val TEXT_RESOURCE_BINDING_SCENE_ID = "text-resource-binding-gate-board"
private const val PM_READINESS_FREEZE_SCENE_ID = "pm-readiness-freeze-board"
private const val LEGACY_RETIREMENT_BLOCKER_SCENE_ID = "legacy-retirement-blocker-board"

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

internal fun GPURendererScene<*>.a8GlyphAtlasGateDiagnostics(): List<String> {
    if (sceneId.value != A8_GLYPH_ATLAS_SCENE_ID) return emptyList()
    return listOf(
        "a8GlyphAtlasRefusalMatrix=" +
            "atlas-descriptor:TargetPrepared:unsupported.text.atlas_descriptor_unaccepted," +
            "atlas-page:RefuseRequired:unsupported.text.atlas_page_unavailable," +
            "atlas-entry:RefuseRequired:unsupported.text.atlas_entry_missing," +
            "atlas-generation:RefuseRequired:unsupported.text.atlas_generation_stale," +
            "a8-route:TargetPrepared:unsupported.text.a8_atlas_route_unavailable," +
            "instance-buffer:RefuseRequired:unsupported.text.instance_buffer_budget_exceeded",
        "pmA8GlyphAtlasRow=gpu-renderer.text.a8-atlas",
        "pmA8GlyphAtlasClassification=TargetPrepared",
        "a8GlyphAtlasRoutePromoted=false",
        "uploadBeforeSampleOrderingProven=false",
        "cpuRenderedTextTextureFallback=false",
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

internal fun GPURendererScene<*>.pmReadinessFreezeDiagnostics(): List<String> {
    if (sceneId.value != PM_READINESS_FREEZE_SCENE_ID) return emptyList()
    return listOf(
        "pmReadinessRow=gpu-renderer.readiness",
        "pmReadinessClassification=PolicyGated",
        "readinessDelta=0.0",
        "releaseBlocking=false",
        "productRouteActivated=false",
        "performanceReadinessPromoted=false",
        "missingGate=KGPU-M9-002",
        "reportingOnlyGatesVisible=true",
        "pipelinePmBundleUpdated=false",
        "nonClaims=no-product-activation,no-release-blocking-gate,no-readiness-delta,no-performance-readiness-from-correctness,no-dashboard-row-promotes-readiness,no-derived-cache-as-observed",
    )
}

internal fun GPURendererScene<*>.legacyRetirementBlockerDiagnostics(): List<String> {
    if (sceneId.value != LEGACY_RETIREMENT_BLOCKER_SCENE_ID) return emptyList()
    return listOf(
        "legacyRetirementRow=gpu-renderer.legacy-retirement",
        "legacyRetirementClassification=PolicyGated",
        "legacyRouteRetired=false",
        "legacyDefaultActive=true",
        "productRouteActivated=false",
        "acceptedReplacementLinked=false",
        "activationDecisionLinked=false",
        "rollbackEvidenceLinked=false",
        "pmEvidenceLinked=false",
        "oldPathUsageEvidenceLinked=false",
        "archivedEvidencePreserved=true",
        "genericMigrationRetirement=false",
        "missingGate=KGPU-M10-002",
        "replacementGate=route-specific-accepted-replacement-required",
    )
}
