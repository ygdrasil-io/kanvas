# FOR-368 Capture des métadonnées candidate F16 M60

Linear: `FOR-368`

Décision: `M60_F16_CANDIDATE_METADATA_STILL_MISSING`

Classification: `candidate-metadata-still-missing`

FOR-368 est un ticket de preuve uniquement. Il reprend les 10 coordonnées
FOR-367 pour `m60-bounded-stroke-cap-join`, conserve le résiduel courant
`856` et enregistre pourquoi les métadonnées candidate F16 restent
indisponibles dans les artefacts commités.

## Source mémoire

- `global/kanvas/ticket-drafts/draft-for-next-m60-f16-candidate-metadata-capture-after-for-367`

## Entrées obligatoires

- FOR-365 décision requise: `F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS`
- FOR-366 décision requise: `F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY`
- FOR-367 décision requise: `M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED`
- FOR-367 classification requise: `still-missing-comparable-metadata`
- Ligne: `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`
- Résiduel courant: `856`

## Résultat

- Classification: `candidate-metadata-still-missing`
- Prêt pour évaluation candidate: `False`
- Échantillons préservés: `10`
- Résiduel recomputé: `856`

La ligne reste `candidate-metadata-still-missing`: aucun artefact commité
ne contient les samples `candidatePolicyRgba`, les valeurs source/input/raw
RGBA, ni les entrées F16 premul/blend nécessaires pour évaluer une future
candidate. Ces valeurs ne sont pas inventées.

## Refus stables

- candidatePolicyRgba: `missing-from-committed-m60-artifacts` - No committed M60 artifact records candidatePolicyRgba samples for policy straight_srgb_quantized_alpha_src_over_white at this coordinate; FOR-368 is evidence-only and cannot render or invent a candidate sample.
- source/raw RGBA: `missing-from-committed-m60-artifacts` - The committed M60 artifacts expose reference/current RGBA samples only; they do not expose source/input/raw RGBA or raw F16 premul/unpremul components for this coordinate.
- hypothèses premul/blend: `not-capturable-from-committed-m60-artifacts` - FOR-367 records targetColorSpaceBlend diagnostics, not explicit source F16 premul/blend inputs for a selected candidate row.

## Table des échantillons

| # | x | y | reference RGBA | current RGBA | residual | candidatePolicyRgba | source/raw RGBA | premul/blend |
|---:|---:|---:|---|---|---:|---|---|---|
| 1 | 92 | 75 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 2 | 91 | 76 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 3 | 90 | 77 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 4 | 89 | 78 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 5 | 88 | 79 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 6 | 87 | 80 | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 105 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 7 | 21 | 81 | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | 56 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 8 | 93 | 74 | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | 52 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 9 | 17 | 77 | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | 52 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |
| 10 | 69 | 81 | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | 66 | `missing-from-committed-m60-artifacts` | `missing-from-committed-m60-artifacts` | `not-capturable-from-committed-m60-artifacts` |

## Non-objectifs respectés

- Aucun changement de rendu.
- Aucun score augmenté.
- Aucun seuil modifié.
- Aucune implémentation candidate autorisée.
- Aucun changement GPU/WGSL, géométrie, couverture, fallback, Kadre,
  F16 premul, blend ou `SkBitmap.getPixel`.

## Artefacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-metadata-capture-for368/m60-f16-candidate-metadata-capture-for368.json`
- Validateur: `scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- Rapport: `reports/wgsl-pipeline/2026-06-05-for-368-m60-f16-candidate-metadata-capture.md`

## Validation

- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py`
- `rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py`
- `rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py`
- `rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py`
- `rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py`
- `rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for368-pycache python3 -m py_compile scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
