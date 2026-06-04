# FOR-370 Extension diagnostic source paint F16 M60

Linear: `FOR-370`

Decision: `M60_F16_SOURCE_PAINT_CAPTURE_EXTENSION_REFUSED_BY_AMBIGUOUS_COVERAGE`

Classification: `candidate-probe-refused-by-ambiguous-coverage`

FOR-370 modifie uniquement le producteur de preuve
`StrokeCapJoinSceneCaptureTest.kt`. Le mode
`-Dkanvas.sceneEvidence.write=true` peut maintenant emettre l'artefact
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370/m60-f16-source-paint-capture-extension-for370.json` avec les 10 coordonnees FOR-367/FOR-368/FOR-369,
le residuel baseline `856`, la bande de stroke, la couleur source paint
statique issue de `BoundedStrokeCapJoinGM`, le cap, le join et le
`strokeWidth`.

## Entrees verrouillees

- FOR-369 decision requise: `M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA`
- FOR-369 classification requise: `capture-path-still-missing-source-metadata`
- Politique candidate: `straight_srgb_quantized_alpha_src_over_white`
- Residuel baseline: `856`

## Resultat

- Source paint reliee aux samples: `True`
- Couverture AA effective connue: `False`
- candidatePolicyRgba produite: `False`
- Pret pour evaluation candidate: `False`

La classification reste `candidate-probe-refused-by-ambiguous-coverage`: la couleur source statique est
connue par bande, mais la couverture AA effective et l'alpha source effectif
ne sont pas exportes par `strokeResidualStats`. La candidate serait donc
non comparable sans inventer une couverture.

## Inspection du producteur

- Producteur: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt`
- Appel ecriture preuve: ligne `162`
- Writer FOR-370: ligne `721`
- Source bands: ligne `848`

## Table des echantillons

| # | x | y | bande | reference RGBA | current/gpu RGBA | residual | paint source RGBA | coverage | candidatePolicyRgba |
|---:|---:|---:|---|---|---|---:|---|---|---|
| 1 | 92 | 75 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 2 | 91 | 76 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 3 | 90 | 77 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 4 | 89 | 78 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 5 | 88 | 79 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 6 | 87 | 80 | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 7 | 21 | 81 | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | 56 | `[0, 102, 204, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 8 | 93 | 74 | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | 52 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 9 | 17 | 77 | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | 52 | `[0, 102, 204, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |
| 10 | 69 | 81 | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | 66 | `[0, 138, 76, 255]` | `effective-aa-coverage-not-exported-by-strokeResidualStats` | `refused-by-ambiguous-coverage` |

## Refus stable

`Static source paint is known for the stroke band, but the capture path does not export per-pixel AA coverage/effective source alpha; candidatePolicyRgba would require inventing coverage.`

## Non-objectifs respectes

- Aucun changement renderer/runtime.
- Aucun changement GPU/WGSL, geometrie, couverture, fallback, Kadre,
  F16 premul/blend ou `SkBitmap.getPixel`.
- Aucun score augmente, seuil modifie, promotion ou statut de support.
- Aucune branche renderer par scene, coordonnee, selected-cell, fixture-only
  path ou full-GM crop.
- Aucune reconstruction approximative de couverture AA.

## Artefacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370/m60-f16-source-paint-capture-extension-for370.json`
- Validateur: `scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- Rapport: `reports/wgsl-pipeline/2026-06-05-for-370-m60-f16-source-paint-capture-extension.md`

## Validation

- `rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for370-pycache python3 -m py_compile scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
