# FOR-458 - M60 F16 production cover state vs shader emission

Date: 2026-06-06

## Resultat

Classification observee:

`production-cover-shader-capture-before-fixed-function-stencil-reject`

FOR-458 ajoute une instrumentation opt-in inactive par defaut:

`kanvas.webgpu.m60F16ProductionCoverStateVsShaderEmissionFor458.enabled`

L'artefact compare les six pixels M60 F16 entre:

- stencil diagnostique FOR-453;
- replay production-bound FOR-457;
- emission shader inside FOR-454/FOR-455;
- etat attendu du cover inside de production: `GPUCompareFunction.NotEqual`, reference `0`, read mask `255`, write mask `255`, stencil ops `Keep`, render pass cover split en `Load`/`Discard`, scissor et draw index.

## Preuves

Artefact:

`reports/wgsl-pipeline/scenes/artifacts/m60-f16-production-cover-state-vs-shader-emission-for458/m60-f16-production-cover-state-vs-shader-emission-for458.json`

Compteurs observes:

- `for453DiagnosticStencilZeroTargetCount = 6`
- `productionBoundFor457MatchingZeroTargetCount = 6`
- `productionCoverInsideExpectedRejectStateCount = 6`
- `productionCoverRenderPassAttachmentMismatchCount = 0`
- `productionCoverReplayOrderOrScissorMismatchCount = 0`
- `insideShaderEmissionOnDiagnosticZeroStencilCount = 6`
- `isolatedColorTargetEmissionCount = 6`
- `postCoverAvailableTargetCount = 6`
- `for442DecisionSourceUsedCount = 0`

Le resultat indique que l'emission shader inside non nulle reste observee alors que l'etat Kotlin du cover inside correspond a l'etat de rejet attendu pour un stencil `0`. FOR-458 ne prouve donc pas que le passage de production accepte ces pixels dans l'attachement depth/stencil par defaut; il classe la preuve comme une capture shader associee a un etat fixed-function qui devrait rejeter ces pixels.

## Exclusions

Ce ticket n'est pas un correctif de rendu.

Le rendu par defaut, les seuils, le scoring, la politique de fallback, `PipelineKey`, le WGSL de production, `wgsl4k`, FOR-442 et la promotion FOR-447 restent inchanges. FOR-442 reste exclu comme source de decision fiable et FOR-447 n'est pas promu.

## Suite unique

Ouvrir un ticket cible pour verifier si le diagnostic storage shader-return enregistre une source candidate avant l'acceptation finale fixed-function, avec une sonde d'acceptation color-attachment-only qui ne change pas le rendu par defaut.

## Validations attendues

- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ProductionCoverStateVsShaderEmissionFor458.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for458_m60_f16_production_cover_state_vs_shader_emission.py`
- `rtk python3 scripts/validate_for457_m60_f16_production_bound_cover_stencil_diagnostic.py`
- `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for458-pycache python3 -m py_compile scripts/validate_for458_m60_f16_production_cover_state_vs_shader_emission.py scripts/validate_for457_m60_f16_production_bound_cover_stencil_diagnostic.py`
- `rtk git diff --check`
