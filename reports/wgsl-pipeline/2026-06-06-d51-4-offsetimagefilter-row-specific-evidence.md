# D51-4 - skia-gm-offsetimagefilter row-specific evidence

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d51-4-convertir-skia-gm-offsetimagefilter-en-preuve-row-specific-precise`
Execution attachment: FOR-460, because dedicated Linear issue creation is blocked by the free issue limit.
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json`

## Resultat

`skia-gm-offsetimagefilter` reste `expected-unsupported`.

L'audit D51-4 confirme les preuves historiques row-specific deja disponibles:
la source Kotlin `skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt`
declare le GM `offsetimagefilter`, la reference
`skia-integration-tests/src/test/resources/original-888/offsetimagefilter.png`
existe en 600x100 au format PNG 16-bit/color RGBA, le test historique
`skia-integration-tests/src/test/kotlin/org/skia/tests/OffsetImageFilterTest.kt`
existe, et le score CPU historique est `OffsetImageFilterGM=84.515`.

Cette preuve ne suffit pas pour promouvoir la ligne. `OffsetImageFilterGM`
dessine 5 cellules horizontales sur fond noir. Les 4 premieres cellules
alternent image texte `e` et checkerboard, creent un
`SkImageFilters.Image(image, SkSamplingOptions.nearest())`, appliquent
`SkImageFilters.Offset(dx, dy, tileInput)`, puis enveloppent le resultat dans
`SkImageFilters.Crop(cropRectF, SkTileMode.kDecal, ...)`. Les offsets sont
`(0,0)`, `(5,10)`, `(10,20)` et `(15,30)`. Les crop rects ont des insets
croissants derives de `SkIRect.MakeXYWH(i * 12, i * 8, image.width - i * 8,
image.height - i * 12)`. La cinquieme cellule applique
`Offset(-5,-10,null)` avec crop 100x100 et draw scale 2x. Chaque cellule passe
par `drawClippedImage`, avec clip aux bounds de l'image, `drawImage` via
`paint.imageFilter`, puis rectangle rouge de debug sur l'intersection
clip/crop.

D51-4 ne genere pas d'artefact WebGPU row-specific dashboard, pas de diff/stat
D51, et pas de diagnostics de route avec `fallbackReason=none`.

## Classification finale

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-offsetimagefilter` |
| Statut | `expected-unsupported` |
| Raison D50 conservee | `image-filter.offset.row-specific-artifacts-required` |
| Raison D51 | `image-filter.offset.crop-prepass-scaled-clipped-webgpu-artifacts-required` |
| Reference row-specific | `available-historical-skia-integration-reference` |
| CPU row-specific | `available-historical-cpu-similarity-score` |
| WebGPU row-specific | `missing` |
| Diff/stat dashboard | `not-computed` |
| Preuve SimpleOffsetImageFilterGM heritee | `false` |

## Pourquoi la preuve historique ne suffit pas

La reference PNG et le score CPU historique identifient le comportement attendu,
mais ils ne forment pas le paquet dashboard requis pour un support: reference,
CPU, WebGPU, statistiques de difference, diagnostics de route et
`fallbackReason=none` produits pour la meme ligne candidate.

Les preuves image-filter voisines ne couvrent pas ce chemin:
`SimpleOffsetImageFilterGM` possede des artefacts crop/prepass separes, mais
`OffsetImageFilterGM` combine une grille de 5 cellules, des images sources
alternees, un offset filtre, un crop kDecal, un clip par cellule et une cellule
scalee 2x. Ce chemin reste distinct des preuves `simple-offsetimagefilter`,
des audits crop/prepass existants, des preuves image-filter DAG voisines et
des lignes image deja promues ou refusees.

## Artefacts requis avant support

- Artefact reference row-specific sous le paquet PM actif.
- Artefact CPU row-specific avec diagnostics de prepass/layer.
- Artefact WebGPU row-specific avec diagnostics de prepass/layer, crop et
  cellule scalee 2x.
- Payload diff/stat calcule sur ces artefacts.
- `fallbackReason=none` uniquement si la route WebGPU passe sans changer les
  seuils globaux.

## Non-claims

- Aucun rendu par defaut n'est modifie.
- Aucun manifeste D50, `results.json`, `scenes.json`, seuil global, scoring
  global, politique fallback globale, `PipelineKey`, WGSL de production,
  `wgsl4k`, source upstream ou `skia-integration-tests` n'est modifie.
- Aucun support image-filter DAG large, crop image-filter large,
  picture prepass, arbitrary layer prepass ou pipeline couleur global n'est
  revendique.
- Aucun support WebGPU pour `OffsetImageFilterGM` n'est revendique.
- Aucun heritage depuis `SimpleOffsetImageFilterGM` n'est revendique.
- Aucun gain de score support et aucun gain Skia-comparable ne sont
  revendiques.

## Validation attendue

```bash
rtk python3 scripts/validate_d51_offsetimagefilter_row_specific_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-offsetimagefilter-pycache python3 -m py_compile scripts/validate_d51_offsetimagefilter_row_specific_evidence.py
rtk git diff --check
```
