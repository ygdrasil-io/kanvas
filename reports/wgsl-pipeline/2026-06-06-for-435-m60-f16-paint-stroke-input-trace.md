# FOR-435 M60 F16 paint/stroke input trace

Date: 2026-06-06

## Classification

`host-paint-input-mismatch`

FOR-435 est diagnostic-only : il ne corrige pas le rendu, ne change pas le
rendu par défaut, ne promeut pas FOR-431 et ne modifie pas le runtime WebGPU.
Le ticket reprend les six pixels FOR-434 et inspecte l'entrée paint/stroke qui
alimente le sous-passage AA stencil-cover au drawIndex effectif `3`.

## Preuves

- Linear: FOR-435.
- Drapeau de preuve: `kanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled`.
- Brouillon mémoire relu: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-inspecter-entree-paint-stroke-aa-stencil-cover-draw-index-3`.
- Finding source: `global/kanvas/findings/for-434-web-gpu-stencil-source-payload-trace-identifies-paint-payload-mismatch`.
- Artefact source FOR-434: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-source-payload-trace-for434/m60-f16-stencil-source-payload-trace-for434.json`.
- Nouvel artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-paint-stroke-input-trace-for435/m60-f16-paint-stroke-input-trace-for435.json`.
- Pixels couverts: exactement `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`.

## Résultat

La trace conserve le drawIndex effectif `3`, les deux candidats stencil
`inside` et `outside`, la destination avant mélange `[181, 191, 230, 255]`, la
référence CPU `[133, 150, 214, 255]`, la sortie WebGPU opt-in FOR-431
`[111, 147, 129, 255]` et la couverture `10/16`.

L'entrée paint/stroke observée correspond au paint vert de la fixture :
`0xFF008A4C`, soit `[0, 138, 76, 255]`, stroke width `10`, cap `round`, join
`round`. Après conversion vers l'espace cible, le payload effectif avant
couverture reste proche de `[0.2667, 0.4731, 0.2672, 1.0]`.

Le payload paint nécessaire pour reconstruire la référence CPU serait plutôt
autour de `[0.4088, 0.4921, 0.8016, 1.0]`. L'écart dominant existe donc dès
l'entrée paint/stroke utilisée par ce sous-passage : le bleu attendu par la
référence CPU est beaucoup plus élevé que celui du paint vert réellement
sélectionné.

FOR-435 ne pointe pas vers la couverture, la quantification, la conversion
cible ni le seuil de promotion. La conversion cible et le payload shader avant
couverture restent alignés entre eux ; ils sont simplement alimentés par une
entrée paint/stroke qui ne correspond pas au payload requis par la référence
CPU sur ces six pixels.

## Non-objectifs préservés

Aucun changement de rendu par défaut, support claim, seuil, scoring, fallback
policy, `PipelineKey`, WGSL de production ou `wgsl4k`. FOR-431 reste opt-in et
désactivé par défaut.

## Prochaine piste

Le prochain ticket doit inspecter le lien entre le draw hôte et le paint choisi
pour le sous-passage AA stencil-cover au drawIndex effectif `3`. La correction
ne doit pas modifier la couverture `10/16`, la quantification, la conversion
cible, les seuils ou la promotion FOR-431 sans preuve séparée.

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for435_m60_f16_paint_stroke_input_trace.py
rtk python3 scripts/validate_for434_m60_f16_stencil_source_payload_trace.py
rtk python3 scripts/validate_for433_m60_f16_stencil_subdraw_source_color.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for435-pycache python3 -m py_compile scripts/validate_for435_m60_f16_paint_stroke_input_trace.py
rtk git diff --check
```
