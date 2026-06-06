# FOR-469 - D50 GM Dashboard Lot 1 pathfill evidence

Date: 2026-06-06
Linear: FOR-469
Source: FOR-468 finding `global/kanvas/findings/for-468-formalise-le-refus-skia-gm-offsetimagefilter-d50-lot-1`
Manifest: `reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json`
Row evidence: `reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json`

## Resultat

FOR-464 a formalise un manifeste PM strict pour le lot 1 D50 dans l'ordre FOR-461. FOR-465 traite `skia-gm-drawminibitmaprect` en refus stable. FOR-466 traite `skia-gm-image` en refus stable. FOR-467 traite `skia-gm-imagesource` en refus stable. FOR-468 traite `skia-gm-offsetimagefilter` en refus stable. FOR-469 traite uniquement `skia-gm-pathfill` et le classe en refus stable `expected-unsupported` parce que les artefacts row-specific D50 requis ne sont pas encore disponibles.

Sept lignes restent `supported` uniquement parce qu'elles pointent vers des lignes dashboard existantes avec `status=pass`, `gpu.status=pass` et `fallbackReason=none`. Aucune ligne ne reste `diagnostic-only`: les cinq candidats non prouves sont visibles en `expected-unsupported` jusqu'a disposer de reference, CPU, GPU, diff/stat, diagnostics de route et politique de seuil inchangee.

D50 n'ajoute aucune ligne dashboard active pour ces refus. FOR-465, FOR-466, FOR-467, FOR-468 et FOR-469 ajoutent chacun 0 revendication de support et 0 revendication Skia-comparable. Le score de support ne monte pas: le changement ameliore la lecture du refus, pas le rendu.

## Statuts Lot 1

| Statut | Nombre |
|---|---:|
| `supported` | 7 |
| `expected-unsupported` | 5 |
| `diagnostic-only` | 0 |

## Compteurs Avant / Apres

| Compteur | Avant inventaire FOR-461 | Apres porte dashboard courante | Delta |
|---|---:|---:|---:|
| Lignes selectionnees | 28 | 93 | 65 |
| Lignes supportees | 21 | 70 | 49 |
| Lignes expected-unsupported | 7 | 23 | 16 |
| Lignes diagnostic-only | 0 | 0 | 0 |
| Lignes Skia-comparable | 5 | 17 | 12 |

Ces compteurs avant/apres donnent le contexte dashboard existant. Les cinq refus sont des decisions strictes `expected-unsupported` dans le manifeste D50, pas des lignes dashboard supplementaires ni des nouvelles revendications de support FOR-464, FOR-465, FOR-466, FOR-467, FOR-468 ou FOR-469.

## Lignes

| Inventory id | Statut strict | Ligne dashboard | Reference | Raison | Fallback |
|---|---|---|---|---|---|
| `skia-gm-drawbitmaprect` | `supported` | `m66-bitmap-rect-nearest-skia` | `skia-upstream` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-drawminibitmaprect` | `expected-unsupported` | `-` | `-` | `bitmap.drawminibitmaprect.row-specific-artifacts-required` | `bitmap.drawminibitmaprect.row-specific-artifacts-required` |
| `skia-gm-bitmappremul` | `supported` | `m53-bitmap-premul-alpha` | `skia-upstream` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-image` | `expected-unsupported` | `-` | `-` | `image.imagegm.row-specific-artifacts-required` | `image.imagegm.row-specific-artifacts-required` |
| `skia-gm-imagesource` | `expected-unsupported` | `-` | `-` | `image.imagesource.row-specific-artifacts-required` | `image.imagesource.row-specific-artifacts-required` |
| `skia-gm-localmatriximageshader` | `supported` | `m54-local-matrix-blend-composition` | `skia-upstream` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-gradientsdegenerate` | `supported` | `m53-degenerate-gradient-linear` | `test-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-offsetimagefilter` | `expected-unsupported` | `-` | `-` | `image-filter.offset.row-specific-artifacts-required` | `image-filter.offset.row-specific-artifacts-required` |
| `skia-gm-matriximagefilter` | `supported` | `m54-matrix-imagefilter-affine` | `test-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-imageblur` | `supported` | `m53-imageblur-bounded-prepass` | `test-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-simpleaaclip` | `supported` | `m54-simple-aa-clip` | `cpu-oracle` | `already-materialized-dashboard-evidence` | `none` |
| `skia-gm-pathfill` | `expected-unsupported` | `-` | `-` | `path-aa.fill.row-specific-artifacts-required` | `path-aa.fill.row-specific-artifacts-required` |

## Provenance FOR-468

`skia-gm-offsetimagefilter` vient de `OffsetImageFilterGM`, porte depuis `gm/offsetimagefilter.cpp`. Les rapports historiques `OffsetImageFilterGM`, la scene voisine `SimpleOffsetImageFilterGM` et les artefacts crop/prepass associes ne sont pas des artefacts D50 row-specific pour cette ligne. Sans reference Skia candidate-specific, artefact CPU prepass/layer, artefact WebGPU prepass/layer, diff/stat et diagnostics de route avec `fallbackReason=none`, FOR-468 formalise un refus stable au lieu de promouvoir la ligne.

## Provenance FOR-469

`skia-gm-pathfill` vient de `PathFillGM`, porte depuis `gm/pathfill.cpp`. Les signaux historiques Path AA, stroke, cap/join/dash, convex path, edge-budget et scenes voisines ne sont pas des artefacts D50 row-specific pour cette ligne. Sans reference Skia candidate-specific, artefact CPU Path AA fill, artefact WebGPU coverage borne, diff/stat et diagnostics de route avec `fallbackReason=none`, FOR-469 formalise un refus stable au lieu de promouvoir la ligne.

## Non-Claims

- Aucun statut dashboard n'est change par FOR-465.
- Aucun statut dashboard n'est change par FOR-466.
- Aucun statut dashboard n'est change par FOR-467.
- Aucun statut dashboard n'est change par FOR-468.
- Aucun statut dashboard n'est change par FOR-469.
- Aucune ligne dashboard n'est ajoutee par FOR-465.
- FOR-465 ajoute 0 ligne dashboard active.
- Aucune ligne dashboard n'est ajoutee par FOR-466.
- FOR-466 ajoute 0 ligne dashboard active.
- Aucune ligne dashboard n'est ajoutee par FOR-467.
- FOR-467 ajoute 0 ligne dashboard active.
- Aucune ligne dashboard n'est ajoutee par FOR-468.
- FOR-468 ajoute 0 ligne dashboard active.
- Aucune ligne dashboard n'est ajoutee par FOR-469.
- FOR-469 ajoute 0 ligne dashboard active.
- D50 ajoute 0 ligne dashboard active: les cinq refus restent dans le manifeste strict, sans augmenter `status.pass`.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-465.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-466.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-467.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-468.
- Aucune nouvelle ligne de support n'est ajoutee par FOR-469.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-465.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-466.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-467.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-468.
- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-469.
- Aucun seuil global, calcul de score, politique de fallback, `PipelineKey`, WGSL de production, code renderer ou source upstream n'est modifie.
- `skia-gm-drawminibitmaprect` n'herite pas de la preuve `skia-gm-drawbitmaprect`; il reste un refus attendu row-specific.
- `skia-gm-image` n'herite pas des tests historiques ImageGM ni des preuves bitmap/image voisines; il reste un refus attendu row-specific.
- `skia-gm-imagesource` n'herite pas de la fixture historique `imagesource.png`, du rapport `ImageSourceGM`, ni des preuves image voisines; il reste un refus attendu row-specific.
- `skia-gm-offsetimagefilter` n'herite pas des rapports historiques `OffsetImageFilterGM`, de `SimpleOffsetImageFilterGM`, des preuves crop/prepass, ni des preuves image-filter voisines; il reste un refus attendu row-specific.
- `skia-gm-pathfill` n'herite pas des preuves Path AA, stroke, cap/join/dash, convex path, edge-budget ou scenes historiques non bornees; il reste un refus attendu row-specific.
- FOR-466 ne revendique aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed.
- FOR-467 ne revendique aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed.
- FOR-468 ne revendique aucun support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire ou pipeline couleur global.
- FOR-469 ne revendique aucun support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique.
- Aucune ligne `diagnostic-only` ne reste dans le lot 1 strict; les refus visibles ne sont pas du support cache.
- Les 7 correspondances `supported` sont des preuves existantes, pas une revendication de support visuel superieur a 50% ni une broad Skia GM parity.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py
rtk python3 scripts/validate_for466_skia_gm_image_evidence.py
rtk python3 scripts/validate_for467_skia_gm_imagesource_evidence.py
rtk python3 scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py
rtk python3 scripts/validate_for469_skia_gm_pathfill_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for469-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py scripts/validate_for467_skia_gm_imagesource_evidence.py scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py scripts/validate_for469_skia_gm_pathfill_evidence.py
rtk git diff --check
```
