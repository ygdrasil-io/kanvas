# D51-5 - skia-gm-pathfill row-specific evidence

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d51-5-convertir-skia-gm-pathfill-en-preuve-row-specific-precise`
Execution attachment: FOR-460, because dedicated Linear issue creation is blocked by the free issue limit.
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d51-pathfill-row-specific-evidence.json`

## Resultat

`skia-gm-pathfill` reste `expected-unsupported`.

L'audit D51-5 confirme les preuves historiques row-specific deja disponibles:
la source Kotlin `skia-integration-tests/src/main/kotlin/org/skia/tests/PathFillGM.kt`
declare le GM `pathfill`, la reference
`skia-integration-tests/src/test/resources/original-888/pathfill.png`
existe en 640x480 au format PNG 16-bit/color RGBA, le test historique
`skia-integration-tests/src/test/kotlin/org/skia/tests/PathFillTest.kt`
existe, et le score CPU historique est `PathFillGM=97.89453125`.

Cette preuve ne suffit pas pour promouvoir la ligne. `PathFillGM` rend une
scene 640x480 avec `SkPaint().apply { isAntiAlias = true }`: une pile verticale
de 10 chemins remplis, puis 3 pictogrammes dessines avec transformations. Les
10 chemins couvrent un frame issu de `FillPathWithPaint` sur un rrect stroke,
triangle, rect, oval, sawtooth 32 dents, star 5, star 13, line/degenerate line,
house polygon avec cavity et sawtooth 3 dents. Les pictogrammes `info`,
`accessibility` et `visualizer` ajoutent des courbes cubiques et coniques avec
des `scale`/`translate` successifs.

D51-5 ne genere pas d'artefact WebGPU row-specific dashboard, pas de diff/stat
D51, et pas de diagnostics de route avec `fallbackReason=none`.

## Classification finale

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-pathfill` |
| Statut | `expected-unsupported` |
| Raison D50 conservee | `path-aa.fill.row-specific-artifacts-required` |
| Raison D51 | `path-aa.fill.multi-shape-conic-cubic-transform-webgpu-artifacts-required` |
| Reference row-specific | `available-historical-skia-integration-reference` |
| CPU row-specific | `available-historical-cpu-similarity-score` |
| Cross-backend historique | `available-floor-0-non-promotable` |
| WebGPU row-specific | `missing` |
| Diff/stat dashboard | `not-computed` |
| Preuve path historique heritee | `false` |

## Pourquoi la preuve historique ne suffit pas

La reference PNG et le score CPU historique identifient un comportement attendu,
mais ils ne forment pas le paquet dashboard requis pour un support: reference,
CPU, WebGPU, statistiques de difference, diagnostics de route et
`fallbackReason=none` produits pour la meme ligne candidate.

Le test cross-backend `PathFillCrossBackendTest` existe, mais il utilise
`rasterFloor = 0.0` et `gpuFloor = 0.0`. Il sert donc de smoke/audit historique,
pas de preuve de promotion pour `PathFillGM`.

Les preuves Path AA voisines ne couvrent pas ce chemin: cette scene combine
remplissage Path AA multi-formes, chemins issus d'un stroke, formes degeneres,
coniques, cubiques, pictogrammes et transformations d'echelle. D51-5 ne herite
pas des scenes path historiques, des preuves convex path, stroke, cap/join/dash
ou edge-budget.

## Artefacts requis avant support

- Artefact reference row-specific sous le paquet PM actif.
- Artefact CPU Path AA fill row-specific avec diagnostics de route.
- Artefact WebGPU row-specific couvrant multi-formes, coniques, cubiques et
  transformations.
- Payload diff/stat calcule sur ces artefacts.
- `fallbackReason=none` uniquement si la route WebGPU passe sans changer les
  seuils globaux.

## Non-claims

- Aucun rendu par defaut n'est modifie.
- Aucun manifeste D50, `results.json`, `scenes.json`, seuil global, scoring
  global, politique fallback globale, `PipelineKey`, WGSL de production,
  `wgsl4k`, source upstream ou `skia-integration-tests` n'est modifie.
- Aucun support Path AA large, stroke, cap/join/dash, convex path,
  edge-budget, WebGPU `PathFillGM`, pictogram coverage, conic/cubic coverage
  large ou heritage depuis des scenes path historiques n'est revendique.
- Aucun support WebGPU pour `PathFillGM` n'est revendique.
- Aucun gain de score support et aucun gain Skia-comparable ne sont
  revendiques.

## Validation attendue

```bash
rtk python3 scripts/validate_d51_pathfill_row_specific_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-pathfill-row-specific-evidence.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-pathfill-pycache python3 -m py_compile scripts/validate_d51_pathfill_row_specific_evidence.py
rtk git diff --check
```
