# FOR-466 - D50 GM Dashboard Lot 1 image evidence

Date: 2026-06-06
Linear: FOR-466
Source: FOR-465 finding `global/kanvas/findings/for-465-formalise-le-refus-drawminibitmaprect-d50-lot-1`
Manifest: `reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json`
Row evidence: `reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json`

## Resultat

FOR-464 a formalise un manifeste PM strict pour le lot 1 D50 dans l'ordre FOR-461. FOR-465 traite `skia-gm-drawminibitmaprect` en refus stable. FOR-466 traite uniquement `skia-gm-image` et le classe en refus stable `expected-unsupported` parce que les artefacts row-specific D50 requis ne sont pas encore disponibles.

Sept lignes restent `supported` uniquement parce qu'elles pointent vers des lignes dashboard existantes avec `status=pass`, `gpu.status=pass` et `fallbackReason=none`. Trois lignes restent `diagnostic-only` et exigent des preuves ligne par ligne avant promotion: reference, CPU, GPU ou refus stable, diff/stat, diagnostics de route et politique de seuil inchangee.

FOR-465 ajoute 0 ligne dashboard, 0 revendication de support et 0 revendication Skia-comparable. FOR-466 ajoute 0 ligne dashboard, 0 revendication de support et 0 revendication Skia-comparable. Le score de support ne monte pas: le changement ameliore la visibilite du refus, pas le rendu.

## Statuts Lot 1

| Statut | Nombre |
|---|---:|
| `supported` | 7 |
| `expected-unsupported` | 2 |
| `diagnostic-only` | 3 |

## Compteurs Avant / Apres

| Compteur | Avant inventaire FOR-461 | Apres porte dashboard courante | Delta |
|---|---:|---:|---:|
| Lignes selectionnees | 28 | 93 | 65 |
| Lignes supportees | 21 | 70 | 49 |
| Lignes expected-unsupported | 7 | 23 | 16 |
| Lignes diagnostic-only | 0 | 0 | 0 |
| Lignes Skia-comparable | 5 | 17 | 12 |

Ces compteurs avant/apres donnent le contexte dashboard existant. Les deltas ne sont pas des nouvelles revendications FOR-464, FOR-465 ou FOR-466.

## Lignes

| Inventory id | Statut strict | Ligne dashboard | Reference | Raison | Fallback |
|---|---|---|---|---|---|
| `skia-gm-drawbitmaprect` | `supported` | `m66-bitmap-rect-nearest-skia` | `skia-upstream` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-drawminibitmaprect` | `expected-unsupported` | `-` | `-` | `bitmap.drawminibitmaprect.row-specific-artifacts-required` | `bitmap.drawminibitmaprect.row-specific-artifacts-required` |
| `skia-gm-bitmappremul` | `supported` | `m53-bitmap-premul-alpha` | `skia-upstream` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-image` | `expected-unsupported` | `-` | `-` | `image.imagegm.row-specific-artifacts-required` | `image.imagegm.row-specific-artifacts-required` |
| `skia-gm-imagesource` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` | `-` |
| `skia-gm-localmatriximageshader` | `supported` | `m54-local-matrix-blend-composition` | `skia-upstream` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-gradientsdegenerate` | `supported` | `m53-degenerate-gradient-linear` | `test-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-offsetimagefilter` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` | `-` |
| `skia-gm-matriximagefilter` | `supported` | `m54-matrix-imagefilter-affine` | `test-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-imageblur` | `supported` | `m53-imageblur-bounded-prepass` | `test-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-simpleaaclip` | `supported` | `m54-simple-aa-clip` | `cpu-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-pathfill` | `diagnostic-only` | `-` | `-` | `diagnostic.missing-row-specific-evidence` | `-` |

## Provenance FOR-466

`skia-gm-image` vient de `ImageGM`: des snapshots 64x64 de surfaces raster N32 premul sont crees dans la scene statique, puis redessines. Aucun fichier image encode externe n'est decode dans cette preuve, et la fixture source ne suffit pas a promouvoir la ligne sans artefacts D50 reference/CPU/GPU/diff-stat.

## Non-Claims

- Aucun statut dashboard n'est change par FOR-465.
- Aucun statut dashboard n'est change par FOR-466.
- Aucune ligne dashboard n'est ajoutee par FOR-465.
- Aucune ligne dashboard n'est ajoutee par FOR-466.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-465.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-466.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-465.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-466.
- Aucun seuil global, calcul de score, politique de fallback, `PipelineKey`, WGSL de production, code renderer ou source upstream n'est modifie.
- `skia-gm-drawminibitmaprect` n'herite pas de la preuve `skia-gm-drawbitmaprect`; il reste un refus attendu row-specific.
- `skia-gm-image` n'herite pas des tests historiques ImageGM ni des preuves bitmap/image voisines; il reste un refus attendu row-specific.
- FOR-466 ne revendique aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed.
- Les trois lignes `diagnostic-only` ne sont pas du support cache; elles sont bloquees jusqu'a l'arrivee de preuves ligne par ligne.
- Les 7 correspondances `supported` sont des preuves existantes, pas une revendication de support visuel superieur a 50% ni une broad Skia GM parity.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py
rtk python3 scripts/validate_for466_skia_gm_image_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for466-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py
rtk git diff --check
```
