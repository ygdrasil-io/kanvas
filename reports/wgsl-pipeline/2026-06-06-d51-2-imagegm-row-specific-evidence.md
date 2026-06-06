# D51-2 - skia-gm-image row-specific evidence

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d51-2-convertir-skia-gm-image-en-preuve-row-specific-precise`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json`

## Resultat

`skia-gm-image` reste `expected-unsupported`.

L'audit D51-2 trouve bien une preuve historique row-specific cote tests Skia integres: la source Kotlin `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt` declare le GM `image-surface`, la reference `skia-integration-tests/src/test/resources/original-888/image-surface.png` existe en 960x1200 au format PNG 16-bit/color RGBA, le test historique `ImageGMTest.kt` existe, et le score CPU historique est `ImageGM=98.16961805555555`.

Cette preuve ne suffit pas pour promouvoir la ligne. `ImageGM` fabrique des snapshots de surfaces raster N32 premul 64x64, puis les redessine via `drawImage`, `surf.draw` et `drawImageRect`; la colonne GPU upstream est intentionnellement non materialisee dans le port Kanvas. D51-2 ne genere pas d'artefact WebGPU row-specific dashboard, pas de diff/stat D51, et pas de diagnostics de route avec `fallbackReason=none`.

## Classification finale

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-image` |
| Statut | `expected-unsupported` |
| Raison D50 conservee | `image.imagegm.row-specific-artifacts-required` |
| Raison D51 | `image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required` |
| Reference row-specific | `available-historical-skia-integration-reference` |
| CPU row-specific | `available-historical-cpu-similarity-score` |
| WebGPU row-specific | `missing` |
| Diff/stat dashboard | `not-computed` |
| Colonne GPU upstream materialisee cote Kanvas | `false` |

## Pourquoi la preuve historique ne suffit pas

`ImageGM` est utile pour identifier le comportement attendu: deux surfaces raster 64x64 sont remplies, snapshottees, modifiees, puis redessinees en sept variantes de lignes: image originale, image modifiee, surface courante, full-crop, over-crop, upper-left et no-crop.

Mais la promotion dashboard exige un paquet coherent produit pour la ligne candidate: reference, CPU, WebGPU, statistiques de difference, diagnostics de route et `fallbackReason=none`. Le score historique CPU ne prouve pas la route WebGPU, et la colonne GPU upstream laissee vide dans Kanvas interdit de compter cette ligne comme support GPU.

## Artefacts requis avant support

- Artefact reference row-specific sous le paquet PM actif.
- Artefact CPU row-specific et diagnostics de route.
- Artefact WebGPU row-specific et diagnostics de route.
- Payload diff/stat calcule sur ces artefacts.
- `fallbackReason=none` uniquement si la route WebGPU passe sans changer les seuils globaux.

## Non-claims

- Aucun rendu par defaut n'est modifie.
- Aucun manifeste D50, `results.json`, `scenes.json`, seuil global, scoring global, politique fallback globale, `PipelineKey`, WGSL de production, `wgsl4k` ou source upstream n'est modifie.
- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, image color-managed ou image arbitraire n'est revendique.
- Aucun support WebGPU pour `ImageGM` n'est revendique.
- Aucun gain de score support et aucun gain Skia-comparable ne sont revendiques.

## Validation attendue

```bash
rtk python3 scripts/validate_d51_imagegm_row_specific_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-imagegm-pycache python3 -m py_compile scripts/validate_d51_imagegm_row_specific_evidence.py
rtk git diff --check
```
