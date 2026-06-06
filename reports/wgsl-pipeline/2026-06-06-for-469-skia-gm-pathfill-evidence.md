# FOR-469 - skia-gm-pathfill row evidence

Date: 2026-06-06
Linear: FOR-469
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json`

## Resultat

`skia-gm-pathfill` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `PathFillGM` manquent encore.

Les preuves Path AA, stroke, cap/join/dash, convex path, edge-budget et scenes historiques non bornees ne sont pas heritees comme support D50. Elles ne fournissent pas, pour ce ticket, la combinaison reference, CPU Path AA fill, WebGPU coverage borne, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Provenance path fill

`PathFillGM` est une scene issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/PathFillGM.kt`, portee de `gm/pathfill.cpp`. Cette preuve n'ajoute aucun support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique.

La fixture source est identifiable, mais les artefacts D50 propres a la ligne restent manquants: reference Skia candidate-specific, rendu CPU Path AA fill, rendu WebGPU coverage borne, diff/stat et diagnostics de route avec `fallbackReason=none`.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-pathfill` |
| Statut strict | `expected-unsupported` |
| Fallback | `path-aa.fill.row-specific-artifacts-required` |
| Route CPU | `cpu.path-aa.fill.expected-unsupported` |
| Route GPU | `webgpu.path-aa.fill.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `PathFillGM`.
- Artefact CPU Path AA fill et diagnostics de route.
- Artefact WebGPU coverage borne et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 5 `expected-unsupported`, 0 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
