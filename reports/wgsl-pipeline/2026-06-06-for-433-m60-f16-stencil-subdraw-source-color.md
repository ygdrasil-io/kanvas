# FOR-433 M60 F16 stencil subdraw source color

Date: 2026-06-06

## Classification

`source-payload-mismatch`

FOR-433 est diagnostic-only : il ne corrige pas le rendu, ne change pas le
rendu par défaut, ne promeut pas FOR-431 et ne modifie pas le runtime WebGPU.
Le ticket part de la preuve FOR-432 et isole la couleur source du sous-passage
stencil sélectionné.

## Preuves

- Linear: FOR-433.
- Drapeau de preuve: `kanvas.webgpu.m60F16StencilSubdrawSourceColorFor433.enabled`.
- Brouillon mémoire: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-isoler-source-couleur-du-sous-passage-stencil-regresse`.
- Finding source: `global/kanvas/findings/for-432-web-gpu-width-quantized-color-reconstruction-matches-single-stencil-gated-subdraw`.
- Artefact source FOR-432: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-width-quantized-color-reconstruction-for432/m60-f16-width-quantized-color-reconstruction-for432.json`.
- Nouvel artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-subdraw-source-color-for433/m60-f16-stencil-subdraw-source-color-for433.json`.
- Pixels couverts: exactement `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`.

## Résultat

Pour les six pixels, FOR-432 donne la destination avant mélange
`[181, 191, 230, 255]`, la référence CPU `[133, 150, 214, 255]`, la sortie
FOR-431 opt-in `[111, 147, 129, 255]` et une couverture `10/16`.

FOR-433 inverse `SrcOver` (mélange source sur destination) en prémultiplié :

```text
source.rgb = reference_cpu.rgb - destination.rgb * (1 - 10/16)
source.a = 10/16
```

Cette source nécessaire reste prémultipliée et reconstruit la référence CPU.
La source WebGPU capturée par FOR-432 garde la bonne couverture, mais son
source payload (charge couleur source) ne correspond pas à la source nécessaire,
avec un écart dominant sur le canal bleu. La prochaine correction doit donc
viser la charge couleur envoyée au sous-passage AA stencil-cover M60 F16, pas
la couverture, le seuil, le scoring, la politique de fallback, `PipelineKey`,
le WGSL de production, `wgsl4k`, ni la promotion FOR-431.

## Non-objectifs préservés

Aucun changement de rendu par défaut, support claim, seuil, scoring, fallback
policy, `PipelineKey`, WGSL de production ou `wgsl4k`. FOR-431 reste opt-in et
désactivé par défaut.

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilSubdrawSourceColorFor433.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for433_m60_f16_stencil_subdraw_source_color.py
rtk python3 scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py
rtk python3 scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for433-pycache python3 -m py_compile scripts/validate_for433_m60_f16_stencil_subdraw_source_color.py
rtk git diff --check
```
