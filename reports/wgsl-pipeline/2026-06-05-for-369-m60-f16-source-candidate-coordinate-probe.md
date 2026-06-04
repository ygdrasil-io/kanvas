# FOR-369 Probe source/candidate F16 M60

Linear: `FOR-369`

Decision: `M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA`

Classification: `capture-path-still-missing-source-metadata`

FOR-369 reste une preuve diagnostique. Il reprend exactement les 10
coordonnees FOR-367/FOR-368, conserve le residuel courant `856`, puis
inspecte le chemin de capture M60 qui produit les artefacts
`aa-residual-diagnostic.json`, `stats.json`, `route-cpu.json`,
`route-gpu.json` et `experimental-gpu-diagnostic.json`.

## Source memoire

- Brouillon: `global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-source-et-candidate-coordinate-probe-apres-for-368`
- Finding FOR-368: `global/kanvas/findings/for-368-confirme-que-les-metadonnees-candidate-m60-f16-restent-absentes-des-artefacts-commites`

## Entrees verrouillees

- FOR-368 decision requise: `M60_F16_CANDIDATE_METADATA_STILL_MISSING`
- FOR-368 classification requise: `candidate-metadata-still-missing`
- FOR-367 ligne: `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`
- Residuel FOR-367/FOR-368: `856`

## Resultat du probe

- Classification: `capture-path-still-missing-source-metadata`
- Pret pour evaluation candidate: `False`
- candidatePolicyRgba produites: `False`
- Valeurs candidate d'artefact produites: `False`
- Refus par couverture ambigue: `False`

Le blocage est dans le chemin de capture, pas dans une evaluation candidate:

`gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:422 and gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt:702 build and serialize residual samples from reference/current bitmap deltas only; the capture path does not carry candidatePolicyRgba, source/input/raw RGBA, source coverage/color, or explicit F16 premul/blend inputs.`

## Inspection du producteur

- Producteur: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt`
- Entree residual: ligne `105` - The M60 residual artifact is produced from strokeResidualStats with only the experimental GPU bitmap and the Skia reference bitmap.
- Construction sample: lignes `422`, `438`, `439`, `449` - ResidualSample is built from getPixel(reference) and getPixel(gpu) only; source/input/raw RGBA, candidatePolicyRgba, and raw F16 premul/blend inputs are not available to the sample object.
- Serialisation: lignes `702`, `707`, `708` - ResidualSample.toJson serializes x/y/maxChannelDelta/referenceRgba/gpuRgba only.
- Politique existante: ligne `39` - The existing M60 probe policy is a targetColorSpaceBlend diagnostic toggle, not the straight_srgb_quantized_alpha_src_over_white candidate policy.

## Metadonnees manquantes

- candidatePolicyRgba for straight_srgb_quantized_alpha_src_over_white
- source/input/raw RGBA at the same 10 coordinates
- source coverage/color sufficient to compute the candidate externally
- explicit F16 premul/blend assumptions for the diagnostic calculation

## Table des echantillons

| # | x | y | reference RGBA | current RGBA | residual | candidatePolicyRgba | source/raw | premul/blend |
|---:|---:|---:|---|---|---:|---|---|---|
| 1 | 92 | 75 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 2 | 91 | 76 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 3 | 90 | 77 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 4 | 89 | 78 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 5 | 88 | 79 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 6 | 87 | 80 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 7 | 21 | 81 | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | 56 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 8 | 93 | 74 | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | 52 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 9 | 17 | 77 | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | 52 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |
| 10 | 69 | 81 | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | 66 | `not-produced-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` | `not-exposed-by-current-m60-capture-path` |

## Non-objectifs respectes

- Aucun changement renderer.
- Aucun changement GPU/WGSL, geometrie, couverture, fallback, Kadre,
  runtime F16 premul/blend ou `SkBitmap.getPixel`.
- Aucun score augmente, seuil modifie, promotion ou statut de support.
- Aucune branche renderer par scene, coordonnee, selected-cell, fixture-only path
  ou full-GM crop.

## Artefacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-candidate-coordinate-probe-for369/m60-f16-source-candidate-coordinate-probe-for369.json`
- Validateur: `scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- Rapport: `reports/wgsl-pipeline/2026-06-05-for-369-m60-f16-source-candidate-coordinate-probe.md`

## Validation

- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for369-pycache python3 -m py_compile scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
