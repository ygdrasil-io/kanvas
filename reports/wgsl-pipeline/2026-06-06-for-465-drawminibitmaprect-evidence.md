# FOR-465 - skia-gm-drawminibitmaprect row evidence

Date: 2026-06-06
Linear: FOR-465
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json`

## Resultat

`skia-gm-drawminibitmaprect` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts propres a `DrawMiniBitmapRectGM` manquent encore.

La preuve `skia-gm-drawbitmaprect` / `m66-bitmap-rect-nearest-skia` n'est pas heritee. Elle couvre `DrawBitmapRectGM`, pas les petites dimensions de source et les risques d'arrondi nommes par l'inventaire pour `DrawMiniBitmapRectGM`.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-drawminibitmaprect` |
| Statut strict | `expected-unsupported` |
| Fallback | `bitmap.drawminibitmaprect.row-specific-artifacts-required` |
| Route CPU | `cpu.image-rect.drawminibitmaprect.expected-unsupported` |
| Route GPU | `webgpu.image-rect.drawminibitmaprect.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific.
- Artefact CPU et diagnostics de route.
- Artefact GPU et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 1 `expected-unsupported`, 4 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
