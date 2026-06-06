# FOR-439 - M60 F16 géométrie WebGPU stencil-cover contre masque CPU vert

Date: 2026-06-06

## Résultat

La trace opt-in classe l'origine WebGPU de la sur-couverture du `drawIndex 3`
contre le masque CPU vert isolé par FOR-438.

Classification dominante obtenue après génération:
`webgpu-cover-polygon-overcovers-edge`.

## Preuve

- Drapeau opt-in:
  `kanvas.webgpu.m60F16StencilCoverGeometryVsCpuGreenMaskFor439.enabled`.
- Artefact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439.json`.
- Source FOR-438:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438.json`.
- Finding mémoire source:
  `global/kanvas/findings/for-438-web-gpu-stencil-cover-overcovers-green-draw-while-cpu-green-coverage-is-zero`.

## Interprétation

Le diagnostic reste borné aux six pixels `(92,75)`, `(91,76)`, `(90,77)`,
`(89,78)`, `(88,79)`, `(87,80)`.

Pour chaque pixel, l'artefact relie le masque CPU vert vide au sous-passage
WebGPU stencil-cover `inside` du `drawIndex 3`, avec scissor, métadonnées de
géométrie, prédicat shader et grille CPU 4x4. Quand le masque WebGPU 4x4
détaillé n'est pas exposé par la trace runtime, le compte WebGPU `10/16` est
dérivé de `coverageOrAaAlpha`.

Le ticket ne corrige pas le rendu. Une correction devra ensuite auditer le
polygone de couverture ou le prédicat d'arête WebGPU du `drawIndex 3` contre
la règle de couverture CPU.

## Validations

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilCoverGeometryVsCpuGreenMaskFor439.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py
rtk python3 scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py
rtk python3 scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for439-pycache python3 -m py_compile scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py
rtk git diff --check
```
