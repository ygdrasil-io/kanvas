# FOR-466 - skia-gm-image row evidence

Date: 2026-06-06
Linear: FOR-466
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json`

## Resultat

`skia-gm-image` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `ImageGM` manquent encore.

Les resultats historiques `ImageGM` et les lignes bitmap/image voisines ne sont pas herites comme support D50. Ils ne fournissent pas, pour ce ticket, la combinaison reference, CPU, GPU, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Provenance decode / fixture

`ImageGM` est une scene statique issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt`, portee de `gm/image.cpp`. Elle fabrique des snapshots 64x64 de surfaces raster N32 premul dans le GM, puis les redessine. Aucun fichier image encode externe n'est decode par cette preuve, et FOR-466 ne revendique donc aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed.

La fixture source est identifiable, mais les artefacts D50 propres a la ligne restent manquants: reference Skia candidate-specific, rendu CPU, rendu GPU, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-image` |
| Statut strict | `expected-unsupported` |
| Fallback | `image.imagegm.row-specific-artifacts-required` |
| Route CPU | `cpu.image.imagegm.expected-unsupported` |
| Route GPU | `webgpu.image.imagegm.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `ImageGM`.
- Artefact CPU et diagnostics de route.
- Artefact GPU et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 2 `expected-unsupported`, 3 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
