# D51 - Cloture PM des refus row-specific precis

Date: 2026-06-06
Rattachement: FOR-460
Brouillon memoire: `global/kanvas/tickets/drafts/brouillon-ticket-d51-6-produire-le-closeout-pm-des-refus-row-specific-precis`
Synthese JSON: `reports/wgsl-pipeline/scenes/generated/d51-row-specific-refusal-precision-closeout.json`

## Resultat en une phrase

D51 a raffine 5 refus D50 sur 5, soit 100% du perimetre D51, sans promouvoir
de nouvelle ligne de rendu et sans modifier le tableau de bord actif.

## Lecture simple

D50 avait ferme l'ambiguite: les cinq lignes sans paquet de preuve complet
etaient visibles comme `expected-unsupported`.

D51 ajoute une precision utile pour le pilotage produit. Chaque refus dit
maintenant plus clairement pourquoi la ligne ne peut pas encore etre presentee
comme supportee. Cette precision facilite le tri des suites, mais elle ne
change pas le score.

D51 ameliore la lecture PM et la precision des refus, pas le score.
Cette cloture clarifie le perimetre, mais elle ne change pas le score.

## Avancement decoupe

| Indicateur | Valeur | Justification simple |
|---|---:|---|
| Refus D50 raffines en D51 | 5/5, soit 100% | Les cinq lignes du perimetre D51 ont maintenant une raison row-specific plus precise. |
| Lignes promues | 0/5, soit 0% | Aucun paquet reference + CPU + WebGPU + diff/stat + route `fallbackReason=none` n'a ete ajoute. |
| Nouveau support de rendu | 0 | D51 est une cloture de preuve et de lecture PM, pas une correction renderer. |
| Changement du tableau de bord actif | 0 | `results.json`, `scenes.json` et le manifeste D50 restent hors perimetre. |
| Changement de seuil, scoring ou fallback global | 0 | Aucun seuil global, calcul de score ou politique globale n'est change. |

## Les cinq lignes D51

| Ticket | Ligne | Raison D51 precise | PR |
|---|---|---|---|
| D51-1 | `skia-gm-drawminibitmaprect` | `bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required` | https://github.com/ygdrasil-io/kanvas/pull/1562 |
| D51-2 | `skia-gm-image` | `image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required` | https://github.com/ygdrasil-io/kanvas/pull/1563 |
| D51-3 | `skia-gm-imagesource` | `image.imagesource.image-filter-cubic-panels-webgpu-artifacts-required` | https://github.com/ygdrasil-io/kanvas/pull/1564 |
| D51-4 | `skia-gm-offsetimagefilter` | `image-filter.offset.crop-prepass-scaled-clipped-webgpu-artifacts-required` | https://github.com/ygdrasil-io/kanvas/pull/1565 |
| D51-5 | `skia-gm-pathfill` | `path-aa.fill.multi-shape-conic-cubic-transform-webgpu-artifacts-required` | https://github.com/ygdrasil-io/kanvas/pull/1566 |

## Pourquoi le score ne monte pas

Une raison de refus plus precise n'est pas une preuve de support. Pour monter
le score, une ligne doit fournir le paquet complet attendu: reference
row-specific, artefact CPU, artefact WebGPU, diff/stat, diagnostics de route et
`fallbackReason=none`.

D51 ne produit pas ces artefacts. D51 rend les limites plus lisibles et evite
les faux positifs: le produit sait quelles familles restent bloquees et quelle
preuve manque pour chaque ligne.

## Ce qui ne change pas

- 0 changement du tableau de bord actif.
- 0 changement de `results.json`.
- 0 changement de `scenes.json`.
- 0 changement du manifeste D50.
- 0 changement de seuil global.
- 0 changement de scoring.
- 0 changement de fallback globale.
- 0 changement de `PipelineKey`.
- 0 changement renderer.
- 0 changement WGSL de production.
- 0 changement upstream.
- 0 changement `skia-integration-tests`.
- 0 revendication de nouveau support de rendu.
- 0 revendication de nouveau score Skia-comparable.

## Suite necessaire pour une vraie promotion

Pour transformer une de ces lignes en support reel, la prochaine etape doit
produire une preuve de rendu row-specific complete:

Le paquet minimal commence par une reference row-specific comparable.

1. Reference row-specific comparable.
2. Artefact CPU pour la meme ligne.
3. Artefact WebGPU pour la meme ligne.
4. Payload diff/stat entre reference, CPU et WebGPU.
5. Diagnostics de route explicites.
6. `fallbackReason=none`.

Sans ces six elements, la ligne doit rester `expected-unsupported`.

## Validation attendue

```bash
rtk python3 scripts/validate_d51_row_specific_refusal_precision_closeout.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-row-specific-refusal-precision-closeout.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-closeout-pycache python3 -m py_compile scripts/validate_d51_row_specific_refusal_precision_closeout.py
rtk git diff --check
```
