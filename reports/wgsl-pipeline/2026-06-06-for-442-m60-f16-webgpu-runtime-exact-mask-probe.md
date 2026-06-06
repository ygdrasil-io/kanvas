# FOR-442 - M60 F16 sonde runtime du masque WebGPU exact

Date: 2026-06-06

## Resultat

Classification: `webgpu-runtime-exact-mask-probe-unavailable`.

La sonde runtime opt-in ajoute un chemin diagnostique borne par
`kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled`. Elle selectionne une
variante shader-return WebGPU avec un stockage diagnostique a 7 `vec4` et tente
d'exporter `M60F16AaStencilCoverShaderReturnDiagnosticSample.wgslSubsampleMask4x4`
depuis `m60_f16_subsample_mask_4x4(pixel)`.

Le rendu par defaut n'est pas modifie. Le WGSL de production
`gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl` n'est pas modifie.

## Preuve

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-runtime-exact-mask-probe-for442/m60-f16-webgpu-runtime-exact-mask-probe-for442.json`

Pour les six pixels `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`,
`(87,80)`, le masque CPU vert reste `0x0000`.

La sonde runtime expose seulement deux masques partiels:

| Pixel | Masque runtime | Compte | Couverture precedente |
|---|---:|---:|---:|
| `(92,75)` | `0x005C` | `4/16` | `10/16` |
| `(89,78)` | `0x0058` | `3/16` | `10/16` |

Les quatre autres pixels ne produisent pas d'echantillon runtime
`drawIndex 3` / `StencilCoverAaPolygonDraw` / `inside` avec
`wgslSubsampleMask4x4`. Le point bloquant est donc que la sonde shader-return ne
couvre pas les six pixels requis sans instrumentation plus bas niveau.

## Non-objectifs preserves

- Aucun changement de rendu par defaut.
- Aucun changement de seuil, scoring, fallback policy, `PipelineKey`, FOR-431,
  `wgsl4k` ou correction du predicat stencil-cover.
- Aucune promotion de support de rendu.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py
rtk python3 scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py
rtk python3 scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for442-pycache python3 -m py_compile scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py
rtk git diff --check
```

## Suite

La suite utile est une sonde plus bas niveau, non corrective, capable
d'echantillonner les six coordonnees independamment de la couverture effective
du fragment shader principal.
