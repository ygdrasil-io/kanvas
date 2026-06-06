# FOR-464 - D50 GM Dashboard Lot 1 Strict Manifest

Date: 2026-06-06
Linear: FOR-464
Source: FOR-462 finding `global/kanvas/findings/for-462-verrouille-le-lot-1-d50-sans-faux-support`
Manifest: `reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json`

## Resultat

FOR-464 formalise un manifeste PM strict pour le lot 1 D50 dans l'ordre FOR-461. Sept lignes sont `supported` uniquement parce qu'elles pointent vers des lignes dashboard existantes avec `status=pass`, `gpu.status=pass` et `fallbackReason=none`.

Cinq lignes restent `diagnostic-only` et exigent des preuves ligne par ligne avant promotion: reference, CPU, GPU ou refus stable, diff/stat, diagnostics de route et politique de seuil inchangee.

FOR-464 ajoute 0 ligne dashboard, 0 revendication de support et 0 revendication Skia-comparable. Il ne declare pas de support visuel superieur a 50%.

## Statuts Lot 1

| Statut | Nombre |
|---|---:|
| `supported` | 7 |
| `expected-unsupported` | 0 |
| `diagnostic-only` | 5 |

## Compteurs Avant / Apres

| Compteur | Avant inventaire FOR-461 | Apres porte dashboard courante | Delta |
|---|---:|---:|---:|
| Lignes selectionnees | 28 | 93 | 65 |
| Lignes supportees | 21 | 70 | 49 |
| Lignes expected-unsupported | 7 | 23 | 16 |
| Lignes diagnostic-only | 0 | 0 | 0 |
| Lignes Skia-comparable | 5 | 17 | 12 |

Ces compteurs avant/apres donnent le contexte dashboard existant. Les deltas ne sont pas des nouvelles revendications FOR-464.

## Lignes

| Inventory id | Statut strict | Ligne dashboard | Reference | Raison |
|---|---|---|---|---|
| `skia-gm-drawbitmaprect` | `supported` | `m66-bitmap-rect-nearest-skia` | `skia-upstream` | `already-materialized-dashboard-evidence` |
| `skia-gm-drawminibitmaprect` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` |
| `skia-gm-bitmappremul` | `supported` | `m53-bitmap-premul-alpha` | `skia-upstream` | `already-materialized-dashboard-evidence` |
| `skia-gm-image` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` |
| `skia-gm-imagesource` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` |
| `skia-gm-localmatriximageshader` | `supported` | `m54-local-matrix-blend-composition` | `skia-upstream` | `already-materialized-dashboard-evidence` |
| `skia-gm-gradientsdegenerate` | `supported` | `m53-degenerate-gradient-linear` | `test-oracle` | `already-materialized-dashboard-evidence` |
| `skia-gm-offsetimagefilter` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` |
| `skia-gm-matriximagefilter` | `supported` | `m54-matrix-imagefilter-affine` | `test-oracle` | `already-materialized-dashboard-evidence` |
| `skia-gm-imageblur` | `supported` | `m53-imageblur-bounded-prepass` | `test-oracle` | `already-materialized-dashboard-evidence` |
| `skia-gm-simpleaaclip` | `supported` | `m54-simple-aa-clip` | `cpu-oracle` | `already-materialized-dashboard-evidence` |
| `skia-gm-pathfill` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` |

## Non-Claims

- Aucun statut dashboard n'est change par FOR-464.
- Aucune ligne dashboard n'est ajoutee par FOR-464.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-464.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-464.
- Aucun seuil global, calcul de score, politique de fallback, `PipelineKey`, WGSL de production, code renderer ou source upstream n'est modifie.
- Les cinq lignes `diagnostic-only` ne sont pas du support cache; elles sont bloquees jusqu'a l'arrivee de preuves ligne par ligne.
- Les 7 correspondances `supported` sont des preuves existantes, pas une revendication de support visuel superieur a 50% ni une broad Skia GM parity.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for464-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk git diff --check
```
