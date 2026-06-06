# FOR-438 - M60 F16 couverture CPU contre WebGPU du draw vert

Date: 2026-06-06

## Résultat

Classification obtenue après génération de l'artefact:
`webgpu-stencil-cover-overcovers-green-draw`.

La trace compare, pour les six pixels FOR-437, trois décisions CPU et une
décision WebGPU:

- le rendu CPU avant `drawIndex 3`, via `BoundedStrokeCapJoinPrefixFor437GM`;
- le rendu CPU après le draw vert `round/round`, via
  `BoundedStrokeCapJoinThroughGreenFor438GM`;
- le masque de couverture CPU du draw vert seul, via
  `BoundedStrokeCapJoinGreenCoverageFor438GM`;
- l'échantillon WebGPU `drawIndex 3` du sous-passage stencil-cover `inside`.

## Preuve

- Drapeau opt-in:
  `kanvas.webgpu.m60F16CpuVsWebGpuGreenDrawCoverageFor438.enabled`.
- Artefact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438.json`.
- Source FOR-437:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-reference-source-expectation-for437/m60-f16-cpu-reference-source-expectation-for437.json`.
- Brouillon mémoire:
  `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-isoler-divergence-draw-coverage-cpu-conserve-bleu-web-gpu-applique-vert`.

## Interprétation

Si l'artefact valide la classification
`webgpu-stencil-cover-overcovers-green-draw`, la divergence ne vient pas du
payload paint vert: le CPU ne couvre pas ces six pixels avec le draw vert,
alors que WebGPU applique une contribution stencil-cover `10/16` du
`drawIndex 3`.

La prochaine correction devra donc auditer la géométrie, le scissor ou la
couverture stencil-cover WebGPU pour `drawIndex 3`, avant tout changement de
payload, seuil, score, fallback, `PipelineKey`, WGSL de production ou FOR-431.

## Validations

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16CpuVsWebGpuGreenDrawCoverageFor438.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py
rtk python3 scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py
rtk python3 scripts/validate_for436_m60_f16_host_draw_paint_binding.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for438-pycache python3 -m py_compile scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py
rtk git diff --check
```
