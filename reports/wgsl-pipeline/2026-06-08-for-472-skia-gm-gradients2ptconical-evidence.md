# FOR-472 - skia-gm-gradients2ptconical row evidence

Date: 2026-06-08
Linear: FOR-472
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for472-skia-gm-gradients2ptconical-evidence.json`

## Resultat

`skia-gm-gradients2ptconical` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts M89 propres a `Gradients2ptConicalGM` manquent encore.

La row D50 vient d'une exclusion inventory remappee en visibilite dashboard `expected-unsupported`. Cette visibilite ne deplace pas les compteurs de support, de readiness ou de fidelity.

## Provenance two-point conical

Les rapports M56/M63 ont corrige une confusion precedente: le support borne de sweep-gradient ne prouve pas les gradients two-point conical. Le support linear, radial, sweep ou color/blend borne ne s'etend pas a `skia-gm-gradients2ptconical`.

Les ports Kotlin `ConicalGradientsGM` et les tests historiques `ConicalGradientsTest`, `ConicalGradients2ptTest` et `ConicalGradientsWebGpuTest` restent des signaux de provenance. Ils ne remplacent pas une preuve registry M89 avec reference, CPU, GPU, diff/stat et diagnostics de route propres a la row.

## Contrat de refus

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-gradients2ptconical` |
| Statut strict | `expected-unsupported` |
| Fallback | `gradient.2ptconical.row-specific-artifacts-required` |
| Route CPU | `cpu.gradient.2ptconical.expected-unsupported` |
| Route GPU | `webgpu.gradient.2ptconical.expected-unsupported` |
| Diff/stat | `not-computed` |

## Artefacts manquants requis pour une future promotion

- Reference Skia candidate-specific pour `Gradients2ptConicalGM`.
- Artefact CPU two-point conical et diagnostics de route.
- Artefact WebGPU two-point conical et diagnostics de route.
- Payload diff/stat.
- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.

## Impact score

Le score de support ne monte pas. Cette preuve rend le refus row-specific visible sans ajouter de support, sans changer les seuils, sans changer le scoring, et sans modifier le code de production.

## Non-claims

- 0 ligne dashboard ajoutee.
- 0 support ajoute.
- 0 revendication Skia-comparable ajoutee.
- Aucun support two-point conical gradient, conical-gradient large, gradient tile-mode parity, broader gradient envelope ou heritage de preuve sweep-gradient/conical historique n'est revendique.
- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.
