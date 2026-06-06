# FOR-467 - skia-gm-imagesource row evidence

Date: 2026-06-06
Linear: FOR-467
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json`

## Resultat

`skia-gm-imagesource` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `ImageSourceGM` manquent encore.

La fixture historique `imagesource.png`, les rapports de similarite `ImageSourceGM` et les preuves image voisines ne sont pas herites comme support D50. Ils ne fournissent pas, pour ce ticket, la combinaison reference, CPU, GPU, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Provenance image-source

`ImageSourceGM` est une scene statique issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageSourceGM.kt`, portee de `gm/imagesource.cpp`. Cette preuve n'ajoute aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed.

La fixture source est identifiable, mais les artefacts D50 propres a la ligne restent manquants: reference Skia candidate-specific, rendu CPU image-source, rendu WebGPU image-source, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-imagesource` |
| Statut strict | `expected-unsupported` |
| Fallback | `image.imagesource.row-specific-artifacts-required` |
| Route CPU | `cpu.image-source.imagesource.expected-unsupported` |
| Route GPU | `webgpu.image-source.imagesource.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `ImageSourceGM`.
- Artefact CPU image-source et diagnostics de route.
- Artefact GPU image-source et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 3 `expected-unsupported`, 2 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
