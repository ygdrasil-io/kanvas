# FOR-440 - M60 F16 edge predicate WebGPU contre couverture CPU verte

Date: 2026-06-06

## Resultat

La trace opt-in audite la decision geometrique du sous-passage WebGPU
`StencilCoverAaPolygonDraw` pour `drawIndex 3` contre le masque CPU vert isole
par FOR-438/FOR-439.

Classification dominante obtenue apres generation:
`webgpu-edge-predicate-overincludes-cpu-excluded-samples`.

## Preuve

- Drapeau opt-in:
  `kanvas.webgpu.m60F16EdgePredicateVsCpuGreenCoverageFor440.enabled`.
- Artefact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440.json`.
- Source FOR-439:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439.json`.
- Finding memoire source:
  `global/kanvas/findings/for-439-web-gpu-cover-polygon-overcovers-cpu-green-excluded-m60-f16-pixels`.

## Interpretation

Le diagnostic reste borne aux six pixels `(92,75)`, `(91,76)`, `(90,77)`,
`(89,78)`, `(88,79)`, `(87,80)`.

Pour chaque pixel, l'artefact expose la decision geometrique issue du predicat
WebGPU `aa_stencil_cover.wgsl`: `supersampled_path_cov`, `winding_at` et
`sample_covered(fillType)`. Le masque CPU vert reste `0x0000`, tandis que le
compte WebGPU indique dix sous-echantillons couverts et correspond a
`coverageOrAaAlpha = 0.625`. Le masque exact par sous-echantillon n'est pas
expose par ce passage runtime; la grille 4x4 garde donc les coordonnees et le
masque CPU, avec decision WebGPU exacte au niveau du compte de couverture.

Le ticket ne corrige pas le rendu. Il ne modifie pas les shaders de production,
les seuils, le scoring, `PipelineKey`, les fallbacks, FOR-431, ni wgsl4k.

## Validations

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16EdgePredicateVsCpuGreenCoverageFor440.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py
rtk python3 scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py
rtk python3 scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for440-pycache python3 -m py_compile scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py
rtk git diff --check
```
