# FOR-434 M60 F16 stencil source payload trace

Date: 2026-06-06

## Classification

`paint-payload-mismatch`

FOR-434 est diagnostic-only : il ne corrige pas le rendu, ne change pas le
rendu par défaut, ne promeut pas FOR-431 et ne modifie pas le runtime WebGPU.
Le ticket reprend les six pixels FOR-433 et expose les composantes déjà
capturées par le shader-return du sous-passage AA stencil-cover.

## Preuves

- Linear: FOR-434.
- Drapeau de preuve: `kanvas.webgpu.m60F16StencilSourcePayloadTraceFor434.enabled`.
- Brouillon mémoire relu: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-le-payload-couleur-source-aa-stencil-cover`.
- Finding source: `global/kanvas/findings/for-433-web-gpu-stencil-subdraw-source-payload-mismatch-explains-m60-f16-regression`.
- Artefact source FOR-433: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-subdraw-source-color-for433/m60-f16-stencil-subdraw-source-color-for433.json`.
- Nouvel artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-source-payload-trace-for434/m60-f16-stencil-source-payload-trace-for434.json`.
- Pixels couverts: exactement `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`.

## Résultat

Pour les six pixels, la destination avant mélange reste `[181, 191, 230, 255]`,
la référence CPU `[133, 150, 214, 255]`, la sortie WebGPU opt-in FOR-431
`[111, 147, 129, 255]`, la couverture `10/16`, et le drawIndex effectif `3`.
Les deux candidats stencil `inside` et `outside` sont exposés et portent la
même source capturée.

La source prémultipliée nécessaire pour reconstruire la référence CPU reste
environ `[0.255516410, 0.307534635, 0.501019895, 0.625000000]`. La source
WebGPU envoyée au blend reste environ `[0.168627456, 0.294117659,
0.168627456, 0.623529434]`, ce qui conserve l'écart principal sur le bleu.

FOR-434 décompose ce payload :

- `sourceColorBeforeQuantization / (10/16)` donne le payload effectif avant
  couverture, donc la modulation par couverture explique bien la source avant
  quantification.
- Le champ historique `correctedColorBeforeCoverage` reste exposé, mais il ne
  représente pas à lui seul la sortie effective après correction bornée dans ce
  chemin.
- `sourceColorBeforeQuantization` est déjà trop faible en bleu avant
  quantification.
- La quantification entre `sourceColorBeforeQuantization` et
  `sourceColorSentToBlend` reste dans une tolérance d'un octet et n'explique
  pas l'écart.
- Le payload paint requis avant couverture serait environ
  `[0.408826256, 0.492055416, 0.801631832, 1.000000000]`, alors que le payload
  effectif inféré avant couverture est autour de `[0.266716362, 0.473101235,
  0.267230963, 1.000000000]`.

La cause probable est donc le payload paint/couleur alimentant
`correctedColorBeforeCoverage`, pas la sélection draw/stencil, pas la
modulation par couverture, pas la quantification, pas le seuil, pas le scoring
et pas FOR-431.

## Non-objectifs préservés

Aucun changement de rendu par défaut, support claim, seuil, scoring, fallback
policy, `PipelineKey`, WGSL de production ou `wgsl4k`. FOR-431 reste opt-in et
désactivé par défaut.

## Prochaine piste

Le prochain ticket doit inspecter l'entrée paint/stroke utilisée par le
sous-passage AA stencil-cover M60 F16 au drawIndex effectif `3`, avant
modulation par couverture. La correction ne doit pas modifier la couverture
`10/16` ni promouvoir FOR-431 sans une preuve séparée.

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilSourcePayloadTraceFor434.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for434_m60_f16_stencil_source_payload_trace.py
rtk python3 scripts/validate_for433_m60_f16_stencil_subdraw_source_color.py
rtk python3 scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for434-pycache python3 -m py_compile scripts/validate_for434_m60_f16_stencil_source_payload_trace.py
rtk git diff --check
```
