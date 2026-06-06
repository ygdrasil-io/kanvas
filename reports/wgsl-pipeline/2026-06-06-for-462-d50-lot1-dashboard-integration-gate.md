# FOR-462 - D50 lot 1 dashboard integration gate

## Resultat

Classification : `lot1-row-specific-refusals-no-new-support-claims`

FOR-462 verifie le premier lot D50 sans ajouter de faux support. Le tableau de bord genere est deja vert avec 0 `tracked-gap` et 0 `fail`, mais seuls 7 des 12 candidats du lot 1 ont actuellement une ligne materialisee avec preuves existantes. FOR-465 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-drawminibitmaprect`; FOR-466 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-image`; FOR-467 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-imagesource`. Aucun de ces refus n'est compte comme support.

Aucune ligne dashboard n'est ajoutee par FOR-462 : le ticket documente l'etat reel et bloque les deux candidats qui n'ont pas encore leurs preuves ligne par ligne.

## Compteurs

| Compteur | Valeur |
|---|---:|
| Candidats lot 1 | 12 |
| Candidats materialises | 7 |
| Candidats expected-unsupported row-specific | 3 |
| Candidats sans preuve suffisante | 2 |
| Lignes ajoutees par FOR-462 | 0 |
| Claims support ajoutes par FOR-462 | 0 |
| Claims Skia-comparable ajoutes par FOR-462 | 0 |
| Dashboard total | 93 |
| Dashboard pass | 70 |
| Dashboard expected-unsupported | 23 |
| Dashboard tracked-gap | 0 |
| Dashboard fail | 0 |

## Candidats deja materialises

| Inventory id | Row | Statut | Reference | Derivation | Fallback |
|---|---|---|---|---|---|
| `skia-gm-drawbitmaprect` | `m66-bitmap-rect-nearest-skia` | `pass` | `skia-upstream` | `pipelineM66GmPromotionWave` | `none` |
| `skia-gm-bitmappremul` | `m53-bitmap-premul-alpha` | `pass` | `skia-upstream` | `pipelineM53InventoryPromotionPack` | `none` |
| `skia-gm-localmatriximageshader` | `m54-local-matrix-blend-composition` | `pass` | `skia-upstream` | `pipelineM54HardFeatureDepthPack` | `none` |
| `skia-gm-gradientsdegenerate` | `m53-degenerate-gradient-linear` | `pass` | `test-oracle` | `pipelineM53InventoryPromotionPack` | `none` |
| `skia-gm-matriximagefilter` | `m54-matrix-imagefilter-affine` | `pass` | `test-oracle` | `pipelineM54HardFeatureDepthPack` | `none` |
| `skia-gm-imageblur` | `m53-imageblur-bounded-prepass` | `pass` | `test-oracle` | `pipelineM53InventoryPromotionPack` | `none` |
| `skia-gm-simpleaaclip` | `m54-simple-aa-clip` | `pass` | `cpu-oracle` | `pipelineM54HardFeatureDepthPack` | `none` |

## Refus row-specific

| Inventory id | Statut | CPU route | GPU route | Fallback |
|---|---|---|---|---|
| `skia-gm-drawminibitmaprect` | `expected-unsupported` | `cpu.image-rect.drawminibitmaprect.expected-unsupported` | `webgpu.image-rect.drawminibitmaprect.expected-unsupported` | `bitmap.drawminibitmaprect.row-specific-artifacts-required` |
| `skia-gm-image` | `expected-unsupported` | `cpu.image.imagegm.expected-unsupported` | `webgpu.image.imagegm.expected-unsupported` | `image.imagegm.row-specific-artifacts-required` |
| `skia-gm-imagesource` | `expected-unsupported` | `cpu.image-source.imagesource.expected-unsupported` | `webgpu.image-source.imagesource.expected-unsupported` | `image.imagesource.row-specific-artifacts-required` |

## Candidats bloques

| Inventory id | Raison |
|---|---|
| `skia-gm-offsetimagefilter` | preuve ligne par ligne manquante ; pas de promotion dashboard sans reference, CPU, GPU ou refus stable, diff/stat et diagnostics de route |
| `skia-gm-pathfill` | preuve ligne par ligne manquante ; pas de promotion dashboard sans reference, CPU, GPU ou refus stable, diff/stat et diagnostics de route |

## Non-claims

- FOR-462 ne change pas les statuts dashboard actifs.
- FOR-462 n'ajoute aucun claim de support.
- FOR-462 n'ajoute aucun claim de fidelite Skia-comparable.
- FOR-462 ne modifie pas les seuils, le scoring, la politique de fallback, `PipelineKey`, le code de production ou les sources upstream.
- FOR-462 ne revendique pas broad Skia GM parity.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for462-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk git diff --check
```

## Suite

Open row-specific evidence tickets for the two remaining diagnostic-only lot 1 candidates before any dashboard support promotion.
