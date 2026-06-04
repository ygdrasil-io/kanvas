# FOR-371 Audit acces couverture effective M60 F16

Linear: `FOR-371`

Decision: `M60_F16_EFFECTIVE_COVERAGE_ACCESS_REQUIRES_NEW_EXPORT_POINT`

Classification: `coverage-access-requires-new-export-point`

Cet audit ne change pas le rendu. Il relit FOR-370 et les artefacts M60
existants pour verifier si les 10 coordonnees disposent deja d'une source
fiable de couverture AA effective ou d'alpha source effectif.

## Conclusion

La route CPU expose un proprietaire probable de la donnee:
`cpu.coverage.stroke-cap-join-oracle` avec `PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,capJoinMatrix=butt-bevel+round-round+square-bevel)`.
Cette information suffit a cibler le prochain point d'export, mais les
artefacts actuels ne portent pas la valeur par pixel pour les 10 samples.

Resultat: `coverage-access-requires-new-export-point`. La prochaine etape doit ajouter un
export de preuve, limite au diagnostic, depuis le proprietaire de couverture
CPU ou son chemin `PathStrokeCoverage`.

## Lignes de preuve

- Producteur: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt`
- Route CPU nommee: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:226` et `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json:6`
- Plan CPU nomme: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:227` et `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json:7`
- Route GPU encore refusee: `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json:8` et racine `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json:13`
- Samples AA residuels: `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json:47`
- Lecture du producteur limitee aux pixels bitmap: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:438` et `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:439`
- Serialization residuelle limitee a `referenceRgba` / `gpuRgba`: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:702`
- FOR-370 ajoute `source paint`, mais garde `sourceCoverage` et `effectiveSourceAlpha` nuls: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370/m60-f16-source-paint-capture-extension-for370.json:53` et `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370/m60-f16-source-paint-capture-extension-for370.json:55`

## Samples audites

| # | x | y | bande | reference RGBA | current/gpu RGBA | source paint RGBA | sourceCoverage | effectiveSourceAlpha |
|---|---:|---:|---|---|---|---|---|---|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[0, 102, 204, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[0, 102, 204, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[0, 138, 76, 255]` | `requires-new-export-point-from-cpu-coverage-oracle` | `requires-new-export-point-from-effective-aa-coverage` |

Residuel conserve: `856`.

## Non-objectifs preserves

- Pas de `candidatePolicyRgba` produit.
- Pas de reconstruction de couverture depuis les deltas RGBA.
- Pas de changement renderer/runtime, GPU/WGSL, geometrie, couverture de production, fallback, Kadre, seuil, score ou promotion.
- Pas de modification de `SkBitmap.getPixel`.

## Validations

- `rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py`
- `rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for371-pycache python3 -m py_compile scripts/validate_for371_m60_f16_effective_coverage_access_audit.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
