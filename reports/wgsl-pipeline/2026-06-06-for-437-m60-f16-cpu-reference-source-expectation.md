# FOR-437 - M60 F16 attente source CPU/référence

Date: 2026-06-06

## Résultat

Classification: `cpu-reference-source-derived-from-different-draw`.

Les six pixels résiduels M60 F16 `(92,75)`, `(91,76)`, `(90,77)`,
`(89,78)`, `(88,79)`, `(87,80)` sont déjà présents dans la référence CPU
quand la scène est rendue seulement jusqu'au premier draw bleu, avant le
draw `3` vert `round/round`.

FOR-436 confirmait que WebGPU lie bien `drawIndex = 3` au paint vert
`0xFF008A4C`, stroke `10`, cap `round`, join `round`. FOR-437 montre que,
côté CPU/référence, ces six pixels correspondent au préfixe bleu
`drawIndex = 1` (`0xFF0066CC`) avant contribution du draw vert.

## Preuve

- Drapeau opt-in: `kanvas.webgpu.m60F16CpuReferenceSourceExpectationFor437.enabled`.
- Artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-reference-source-expectation-for437/m60-f16-cpu-reference-source-expectation-for437.json`.
- Artefact source FOR-436: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-host-draw-paint-binding-for436/m60-f16-host-draw-paint-binding-for436.json`.
- Brouillon mémoire: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-attente-source-cpu-reference-pour-pixels-draw-index-3`.

## Interprétation

Le problème n'est pas encore une preuve de payload paint à corriger. La
prochaine étape doit isoler pourquoi le CPU conserve ces pixels comme résultat
du draw bleu précédent alors que WebGPU applique une contribution stencil-cover
verte `10/16` au même emplacement.

Non-objectifs conservés: aucun changement de rendu par défaut, seuil, score,
fallback policy, `PipelineKey`, WGSL de production, `wgsl4k`, ou promotion
FOR-431.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16CpuReferenceSourceExpectationFor437.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py
rtk python3 scripts/validate_for436_m60_f16_host_draw_paint_binding.py
rtk python3 scripts/validate_for435_m60_f16_paint_stroke_input_trace.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for437-pycache python3 -m py_compile scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py
rtk git diff --check
```
