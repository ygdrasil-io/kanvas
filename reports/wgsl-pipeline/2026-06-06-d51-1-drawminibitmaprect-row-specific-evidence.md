# D51-1 - skia-gm-drawminibitmaprect row-specific evidence

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d51-1-convertir-skia-gm-drawminibitmaprect-en-preuve-row-specific`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json`

## Resultat

`skia-gm-drawminibitmaprect` reste `expected-unsupported`.

L'audit D51-1 trouve bien une preuve historique row-specific cote tests Skia integres: les references PNG `drawminibitmaprect.png` et `drawminibitmaprect_aa.png` existent, et les scores historiques CPU sont 93.95856857299805% pour `DrawMiniBitmapRectGM` et 92.79909133911133% pour `DrawMiniBitmapRectAaGM`.

Cette preuve ne suffit pas pour promouvoir la ligne. Les tests sont des GMs de stress desactives par defaut, et aucune preuve WebGPU/dashboard row-specific n'existe sous `reports/wgsl-pipeline` pour `DrawMiniBitmapRectGM`. La ligne ne peut donc pas heriter de `m66-bitmap-rect-nearest-skia`, qui couvre seulement un smoke 64x64 strict-nearest issu de `DrawBitmapRectGM`.

## Classification finale

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-drawminibitmaprect` |
| Statut | `expected-unsupported` |
| Raison D51 | `bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required` |
| Reference row-specific | `available-historical-skia-integration-reference` |
| CPU row-specific | `available-historical-disabled-stress-test` |
| WebGPU row-specific | `missing` |
| Diff/stat dashboard | `not-computed` |
| Preuve M66 heritee | `false` |

## Pourquoi M66 ne suffit pas

`m66-bitmap-rect-nearest-skia` utilise `bitmap-rect-nearest`, une scene 64x64 avec route CPU/GPU `strict-nearest` et similarite 100%.

`DrawMiniBitmapRectGM` dessine une GM 1024x1024 depuis un atlas radial 2048x2048, avec rects source de taille croissante, rotations aleatoires deterministes, destination 64x64, `SkSamplingOptions.Default` et `SrcRectConstraint.kFast`. Cette combinaison expose des risques d'arrondi, de filtrage et de sampling hors rect source qui ne sont pas couverts par le smoke M66.

## Artefacts requis avant support

- Artefact reference row-specific sous le paquet PM actif.
- Artefact CPU row-specific et diagnostics de route.
- Artefact WebGPU row-specific et diagnostics de route.
- Payload diff/stat calcule sur ces artefacts.
- `fallbackReason=none` uniquement si la route WebGPU passe sans changer les seuils globaux.

## Non-claims

- Aucun rendu par defaut n'est modifie.
- Aucun manifeste D50, seuil global, scoring global, politique fallback globale, `PipelineKey`, WGSL de production, `wgsl4k` ou source upstream n'est modifie.
- Aucun support large codec, mipmap, tile-mode, image color-managed, YUV, animation, EXIF, bicubic ou image arbitraire n'est revendique.
- Aucun support WebGPU pour `DrawMiniBitmapRectGM` n'est revendique.

## Validation attendue

```bash
rtk python3 scripts/validate_d51_drawminibitmaprect_row_specific_evidence.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-drawminibitmaprect-pycache python3 -m py_compile scripts/validate_d51_drawminibitmaprect_row_specific_evidence.py
rtk git diff --check
```
