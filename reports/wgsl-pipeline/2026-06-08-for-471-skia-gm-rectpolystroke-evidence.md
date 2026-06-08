# FOR-471 - skia-gm-rectpolystroke row evidence

Date: 2026-06-08
Linear: FOR-471
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for471-skia-gm-rectpolystroke-evidence.json`

## Resultat

`skia-gm-rectpolystroke` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts M89 propres a `RectPolyStrokeGM` manquent encore.

La row reste visible dans le registry M89 sans ajouter de support Path AA ou stroke. Les tests historiques WebGPU/cross-backend et les preuves stroke/coverage voisines ne sont pas herites comme preuve row-specific M89.

## Provenance RectPolyStroke

`RectPolyStrokeGM` est une scene issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/RectPolyStrokeGM.kt`, portee de `gm/rect_poly_stroke.cpp`.

La scene couvre 48 cellules: 3 joins, 4 rectangles incluant des cas degeneres, 2 rotations, 2 chemins de dessin (`drawRect` et `drawPath`), plus des overlays hairline verts. Les tests `RectPolyStrokeWebGpuTest` et `RectPolyStrokeCrossBackendTest` restent des signaux historiques/adapter-gated; ils ne remplacent pas une preuve registry M89 avec reference, CPU, GPU, diff/stat et diagnostics de route propres a la row.

La similarite historique `RectPolyStrokeGM=97.4320415879017` reste un signal historique. Cette row n'est pas classee comme feature manquante de production sur la seule base d'une tolerance non atteinte; le refus actif vient de l'absence d'artefacts row-specific M89.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-rectpolystroke` |
| Statut strict | `expected-unsupported` |
| Fallback | `coverage.rectpolystroke.row-specific-artifacts-required` |
| Route CPU | `cpu.path.rectpolystroke.expected-unsupported` |
| Route GPU | `webgpu.path.rectpolystroke.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `RectPolyStrokeGM`.
- Artefact CPU Path AA/stroke et diagnostics de route.
- Artefact WebGPU coverage borne et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Cette preuve rend le refus row-specific visible sans ajouter de support, sans changer les seuils, sans changer le scoring, sans augmenter l'edge budget, et sans modifier le code de production.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support large Path AA, stroke large, cap/join/dash, path stroker parity, edge-budget promotion ou heritage de preuve WebGPU/cross-backend n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
