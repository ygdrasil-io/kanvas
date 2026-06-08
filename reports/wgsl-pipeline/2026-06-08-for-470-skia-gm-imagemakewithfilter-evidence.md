# FOR-470 - skia-gm-imagemakewithfilter row evidence

Date: 2026-06-08
Linear: FOR-470
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json`

## Resultat

`skia-gm-imagemakewithfilter` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `ImageMakeWithFilterGM` manquent encore.

La row reste visible dans le registry M89 sans ajouter de support image-filter. Les preuves M61/M89 bornees pour crop, compose, color-filter ou matrix-transform ne sont pas heritees comme preuve pour `ImageMakeWithFilterGM`.

## Provenance ImageMakeWithFilter

`ImageMakeWithFilterGM` est une scene issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageMakeWithFilterGM.kt`, portee de `gm/imagemakewithfilter.cpp`.

La similarite historique `ImageMakeWithFilterGM=84.35382962588474` reste un signal historique, pas une preuve dashboard D50 ligne par ligne. Cette row n'est donc pas classee comme feature manquante de production sur la seule base d'une tolerance non atteinte; le refus actif vient de l'absence de reference, CPU, GPU, diff/stat et route diagnostics propres a la row.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-imagemakewithfilter` |
| Statut strict | `expected-unsupported` |
| Fallback | `image-filter.imagemakewithfilter.row-specific-artifacts-required` |
| Route CPU | `cpu.image-filter.imagemakewithfilter.expected-unsupported` |
| Route GPU | `webgpu.image-filter.imagemakewithfilter.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `ImageMakeWithFilterGM`.
- Artefact CPU image-filter DAG/layer et diagnostics de route.
- Artefact GPU image-filter DAG/layer et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Cette preuve rend le refus row-specific visible sans ajouter de support, sans changer les seuils, sans changer le scoring, et sans modifier le code de production.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support image-filter large, DAG arbitraire, crop prepass, picture prepass, layer prepass ou heritage de preuve M61/M89 n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
