# D52-1 - DrawMiniBitmapRect promotion readiness

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d52-1-preparer-paquet-promotion-drawminibitmaprect`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json`
Previous evidence: `reports/wgsl-pipeline/2026-06-06-d51-1-drawminibitmaprect-row-specific-evidence.md`

## Resultat

`skia-gm-drawminibitmaprect` reste `expected-unsupported`.

D52-1 ne promeut pas la ligne. Le paquet confirme que la promotion reste
bloquee tant que le paquet row-specific reference/CPU/WebGPU/diff n'existe pas
pour `DrawMiniBitmapRectGM` en 1024x1024, avec diagnostics de route et
`fallbackReason=none`.

La raison D51-1 reste la raison active:
`bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required`.

## Classement des preuves

| Preuve | Statut | Decision |
|---|---|---|
| Reference historique | `available-historical-skia-integration-reference` | Les PNG `drawminibitmaprect.png` et `drawminibitmaprect_aa.png` existent et restent utiles comme entree d'audit, mais ne constituent pas le paquet PM actif de promotion. |
| CPU historique | `available-historical-disabled-stress-test` | Les scores historiques CPU existent, mais les tests `DrawMiniBitmapRectGM` et `DrawMiniBitmapRectAaGM` restent des tests de stress desactives par defaut. |
| WebGPU row-specific | `missing` | Aucun artefact WebGPU row-specific pour la GM 1024x1024 n'est materialise sous le paquet PM actif. |
| Diff/stat row-specific | `missing` | Aucun payload diff/stat ne peut etre revendique sans reference, CPU, WebGPU et route produits ensemble. |
| Diagnostics de route de promotion | `missing` | Les diagnostics disponibles sont des diagnostics de refus, pas des diagnostics de route supportee. |

## Pourquoi `m66-bitmap-rect-nearest-skia` ne peut pas etre herite

`m66-bitmap-rect-nearest-skia` couvre `skia-gm-drawbitmaprect`, pas
`skia-gm-drawminibitmaprect`.

Cette preuve M66 est un smoke 64x64 en `strict-nearest`, avec `fallbackReason=none`.
`DrawMiniBitmapRectGM` est une GM 1024x1024 issue d'un atlas 2048x2048, avec
rectangles source croissants, rotations deterministes, `SkSamplingOptions.Default`
et `SrcRectConstraint.kFast`. Cette surface de risque n'est pas couverte par le
smoke M66.

Conclusion: `m66-bitmap-rect-nearest-skia` ne peut pas etre herite comme support
pour `skia-gm-drawminibitmaprect`.

## Decision de readiness

La ligne est une bonne candidate de prochaine tranche bitmap, mais elle n'est
pas promotable maintenant.

Le statut PM reste:

| Champ | Valeur |
|---|---|
| Inventory id | `skia-gm-drawminibitmaprect` |
| Statut | `expected-unsupported` |
| Raison active | `bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required` |
| Support WebGPU revendique | `false` |
| Gain de score | `false` |
| Changement de seuil | `false` |
| Changement dashboard | `false` |
| Changement rendu par defaut | `false` |

## nextImplementationSlice

Prochaine tranche minimale:

1. Produire une reference row-specific pour `DrawMiniBitmapRectGM` en 1024x1024
   dans le paquet PM actif.
2. Produire un artefact CPU row-specific pour la meme GM, avec diagnostics de
   route CPU explicites.
3. Produire un artefact WebGPU row-specific pour la meme GM, avec diagnostics de
   route WebGPU explicites.
4. Calculer le payload diff/stat entre reference, CPU et WebGPU.
5. Ne poser `fallbackReason=none` que si la route WebGPU passe sans changer de
   seuil global.
6. Conserver un refus stable si un artefact manque ou si la route WebGPU ne
   passe pas.

Cette tranche ne doit pas reutiliser `m66-bitmap-rect-nearest-skia` comme preuve
de support.

## Non-claims

- Aucun gain de score n'est revendique.
- Aucun support WebGPU pour `DrawMiniBitmapRectGM` n'est revendique.
- Aucun support Skia-comparable supplementaire n'est revendique.
- Aucun seuil global, scoring, dashboard D50/D51, politique fallback globale,
  `PipelineKey`, WGSL de production, source upstream, `wgsl4k` ou rendu par
  defaut n'est modifie.
- Aucun support large image, codec, mipmap, tile-mode, YUV, EXIF, animation,
  bicubic ou image color-managed n'est revendique.

## Validation attendue

```bash
rtk python3 scripts/validate_d52_drawminibitmaprect_promotion_readiness.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-drawminibitmaprect-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_promotion_readiness.py
rtk git diff --check
```
