# FOR-372 Export couverture effective M60 F16

Linear: `FOR-372`

Decision: `M60_F16_EFFECTIVE_COVERAGE_EXPORT_READY_FOR_CANDIDATE_PROBE`

Classification: `coverage-export-ready-for-candidate-probe`

FOR-372 ajoute un export de preuve diagnostique uniquement. Il conserve les
10 coordonnees M60 et le residuel `856`, puis lit la
couverture depuis le canal alpha d'un GM de masque CPU transparent.

## Provenance

- Route proprietaire: `cpu.coverage.stroke-cap-join-oracle`
- Plan: `PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,capJoinMatrix=butt-bevel+round-round+square-bevel)`
- Masque: `m60_bounded_stroke_cap_join_coverage_mask_for372`
- Lecture: `alpha-channel-from-transparent-cpu-diagnostic-mask`
- Appel writer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:163`
- GM masque transparent: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:560` et `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:562`
- Lecture alpha du masque: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:869`

La couverture n'est pas reconstruite depuis `referenceRgba`, `currentRgba`,
`gpuRgba` ou les deltas RGBA. `candidatePolicyRgba` reste non produit et non
applique au renderer.

## Samples exportes

| # | x | y | bande | reference RGBA | current/gpu RGBA | source paint RGBA | coverage byte | coverage | alpha byte | effective alpha |
|---|---:|---:|---|---|---|---|---:|---:|---:|---:|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | `[0, 138, 76, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | `[0, 102, 204, 255]` | 64 | 0.250980 | 64 | 0.250980 |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | `[0, 138, 76, 255]` | 96 | 0.376471 | 96 | 0.376471 |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | `[0, 102, 204, 255]` | 160 | 0.627451 | 160 | 0.627451 |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | `[0, 138, 76, 255]` | 64 | 0.250980 | 64 | 0.250980 |

## Non-objectifs preserves

- Pas de changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Pas de fallback, Kadre, F16 premul/blend, score, seuil ou promotion modifie.
- Pas de `candidatePolicyRgba` applique au renderer.
- Pas de reconstruction de couverture depuis les deltas RGBA.

## Validations

- `rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py`
- `rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py`
- `rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for372-pycache python3 -m py_compile scripts/validate_for372_m60_f16_effective_coverage_export.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
