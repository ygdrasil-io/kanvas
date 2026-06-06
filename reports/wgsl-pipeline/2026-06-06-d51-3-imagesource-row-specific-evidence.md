# D51-3 - skia-gm-imagesource row-specific evidence

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d51-3-convertir-skia-gm-imagesource-en-preuve-row-specific-precise`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d51-imagesource-row-specific-evidence.json`

## Resultat

`skia-gm-imagesource` reste `expected-unsupported`.

L'audit D51-3 confirme les preuves historiques row-specific deja disponibles:
la source Kotlin `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageSourceGM.kt`
declare le GM `imagesource`, la reference
`skia-integration-tests/src/test/resources/original-888/imagesource.png` existe
en 500x150 au format PNG 16-bit/color RGBA, le test historique
`skia-integration-tests/src/test/kotlin/org/skia/tests/ImageSourceTest.kt`
existe, et le score CPU historique est
`ImageSourceGM=98.53466666666667`.

Cette preuve ne suffit pas pour promouvoir la ligne. `ImageSourceGM` fabrique
une image source N32 premul 100x100 avec la lettre `e`, puis dessine quatre
panneaux via `SkImageFilters.Image`: full image nearest, subset->subset cubic,
subset->dst cubic, et bounds->dst cubic. Chaque panneau applique le filtre via
`paint.imageFilter`, `clipRect(0,0,100,100)` et `drawPaint`, puis translate de
100 px. D51-3 ne genere pas d'artefact WebGPU row-specific dashboard, pas de
diff/stat D51, et pas de diagnostics de route avec `fallbackReason=none`.

## Classification finale

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-imagesource` |
| Statut | `expected-unsupported` |
| Raison D50 conservee | `image.imagesource.row-specific-artifacts-required` |
| Raison D51 | `image.imagesource.image-filter-cubic-panels-webgpu-artifacts-required` |
| Reference row-specific | `available-historical-skia-integration-reference` |
| CPU row-specific | `available-historical-cpu-similarity-score` |
| WebGPU row-specific | `missing` |
| Diff/stat dashboard | `not-computed` |
| Preuve image voisine heritee | `false` |

## Pourquoi la preuve historique ne suffit pas

Le score historique CPU et la reference `imagesource.png` identifient le
comportement attendu, mais ils ne forment pas le paquet dashboard requis pour
un support: reference, CPU, WebGPU, statistiques de difference, diagnostics de
route et `fallbackReason=none` produits pour la meme ligne candidate.

Les preuves image voisines ne couvrent pas ce chemin: `ImageSourceGM` passe par
un filtre `SkImageFilters.Image` attache au paint, sous clip, avec panneaux
cubic et translations deterministes. Cela reste distinct de `ImageGM`,
`BitmapImageGM`, `SimpleSnapImageGM`, `MakeRasterImageGM`,
`DrawBitmapRectGM`, et des preuves image-filter crop/prepass.

## Artefacts requis avant support

- Artefact reference row-specific sous le paquet PM actif.
- Artefact CPU row-specific et diagnostics de route.
- Artefact WebGPU row-specific et diagnostics de route.
- Payload diff/stat calcule sur ces artefacts.
- `fallbackReason=none` uniquement si la route WebGPU passe sans changer les
  seuils globaux.

## Non-claims

- Aucun rendu par defaut n'est modifie.
- Aucun manifeste D50, `results.json`, `scenes.json`, seuil global, scoring
  global, politique fallback globale, `PipelineKey`, WGSL de production,
  `wgsl4k` ou source upstream n'est modifie.
- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image
  dynamique, image color-managed ou image arbitraire n'est revendique.
- Aucun support WebGPU pour `ImageSourceGM` n'est revendique.
- Aucun gain de score support et aucun gain Skia-comparable ne sont
  revendiques.

## Validation attendue

```bash
rtk python3 scripts/validate_d51_imagesource_row_specific_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-imagesource-row-specific-evidence.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-imagesource-pycache python3 -m py_compile scripts/validate_d51_imagesource_row_specific_evidence.py
rtk git diff --check
```
