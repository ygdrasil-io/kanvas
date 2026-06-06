# FOR-468 - skia-gm-offsetimagefilter row evidence

Date: 2026-06-06
Linear: FOR-468
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json`

## Resultat

`skia-gm-offsetimagefilter` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `OffsetImageFilterGM` manquent encore.

Les rapports historiques `OffsetImageFilterGM`, la scene voisine `SimpleOffsetImageFilterGM`, les artefacts crop/prepass et les preuves image-filter voisines ne sont pas herites comme support D50. Ils ne fournissent pas, pour ce ticket, la combinaison reference, CPU prepass/layer, WebGPU prepass/layer, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Provenance offset image-filter

`OffsetImageFilterGM` est une scene issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt`, portee de `gm/offsetimagefilter.cpp`. La similarite historique `OffsetImageFilterGM=84.515` et les audits `simple-offsetimagefilter` restent des signaux historiques; ils ne sont pas une preuve dashboard D50 ligne par ligne.

Cette preuve n'ajoute aucun support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire, pipeline couleur global ou heritage de support image-filter voisin.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-offsetimagefilter` |
| Statut strict | `expected-unsupported` |
| Fallback | `image-filter.offset.row-specific-artifacts-required` |
| Route CPU | `cpu.image-filter.offset.expected-unsupported` |
| Route GPU | `webgpu.image-filter.offset.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `OffsetImageFilterGM`.
- Artefact CPU prepass/layer et diagnostics de route.
- Artefact GPU prepass/layer et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 4 `expected-unsupported`, 1 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire ou pipeline couleur global n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
