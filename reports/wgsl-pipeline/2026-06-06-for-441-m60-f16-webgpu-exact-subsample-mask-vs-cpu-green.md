# FOR-441 - M60 F16 masque WebGPU exact contre masque CPU vert

Date: 2026-06-06

## Resultat

La sonde opt-in tente d'exporter le masque 4x4 exact du predicat WebGPU
`StencilCoverAaPolygonDraw` pour les six pixels FOR-440:
`(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`.

Classification obtenue apres generation:
`webgpu-exact-mask-unavailable`.

## Preuve

- Drapeau opt-in:
  `kanvas.webgpu.m60F16ExactSubsampleMaskVsCpuGreenFor441.enabled`.
- Artefact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441/m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441.json`.
- Source FOR-440:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440.json`.
- Finding memoire source:
  `global/kanvas/findings/for-440-web-gpu-edge-predicate-overincludes-cpu-excluded-m60-f16-samples`.

## Interpretation

Pour les six pixels, le masque CPU vert seul reste `0x0000`, soit `0/16`
sous-echantillons couverts. Le chemin WebGPU selectionne toujours
`drawIndex 3`, le sous-passage `inside` de `StencilCoverAaPolygonDraw`, avec
`coverageOrAaAlpha = 0.625`, soit un compte derive de `10/16`.

Le masque WebGPU exact n'est pas expose par le snapshot runtime principal:
`M60F16AaStencilCoverShaderReturnDiagnosticSample.wgslSubsampleMask4x4`.
L'artefact garde donc la grille 4x4 et les coordonnees, mais marque les cellules
WebGPU exactes comme indisponibles au lieu de fabriquer un masque synthetique.

Le ticket ne corrige pas le rendu. Il ne modifie pas `SkWebGpuDevice.kt`, les
shaders WGSL de production, les seuils, le scoring, `PipelineKey`, les
fallbacks, FOR-431, ni wgsl4k.

## Validations

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ExactSubsampleMaskVsCpuGreenFor441.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py
rtk python3 scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py
rtk python3 scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for441-pycache python3 -m py_compile scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py
rtk git diff --check
```

