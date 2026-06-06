# D50 lot 1 - Rapport PM de cloture

Date: 2026-06-06
Epic: FOR-460
Branche: `codex/d50-9-pm-lot1-closeout`
Brouillon memoire: `global/kanvas/tickets/drafts/brouillon-ticket-d50-9-produire-le-rapport-pm-de-cloture-lot-1`
Synthese JSON: `reports/wgsl-pipeline/scenes/generated/d50-lot1-pm-closeout.json`

## Resultat en une phrase

Le lot 1 D50 est maintenant clair et verifiable: les 12 lignes candidates sont classees, avec 7 lignes deja supportees par des preuves dashboard existantes, 5 refus stables, et 0 ligne encore ambigue en `diagnostic-only`.

## Ce que cela veut dire

Les tickets FOR-465 a FOR-469 ne montent pas le score de support. Ils servent a fermer l'ambiguite: une ligne sans reference Skia dediee, sans artefact CPU, sans artefact WebGPU, sans diff/stat et sans diagnostic de route propre ne doit pas etre presentee comme supportee.

Le gain PM est donc de la fiabilite de lecture. Les cinq lignes incertaines deviennent des refus visibles, avec une raison stable, au lieu de rester dans une zone grise qui pourrait etre lue comme un support cache.

## Avancement decoupe

| Indicateur | Valeur | Lecture simple |
|---|---:|---|
| Lignes du lot 1 | 12/12 | Le lot strict est completement classe. |
| Support prouve | 7/12, soit 58.3% | Ces lignes pointent vers des preuves dashboard deja existantes. |
| Refus stable | 5/12, soit 41.7% | Ces lignes sont visibles comme non supportees pour l'instant. |
| Ambiguite restante | 0/12, soit 0.0% | Aucune ligne du lot 1 ne reste en `diagnostic-only`. |
| Hausse de support FOR-465..FOR-469 | 0 | Ces tickets ne sont pas des corrections de rendu. |

## Dashboard global

| Compteur | Valeur |
|---|---:|
| Total | 93 |
| `pass` | 70 |
| `expected-unsupported` | 23 |
| `tracked-gap` | 0 |
| `fail` | 0 |

Ces compteurs ne sont pas modifies par D50-9. Le rapport ne change pas le dashboard actif.

## Tickets, PRs et commits

| Ticket | PR | Commit merge | Role |
|---|---|---|---|
| FOR-461 | https://github.com/ygdrasil-io/kanvas/pull/1553 | `e5a9be67ee72592c5c0611ea865b712256fa0535` | Inventaire candidat D50 GM dashboard. |
| FOR-462 | https://github.com/ygdrasil-io/kanvas/pull/1554 | `48094aa1a90489f4d6b245ea95aed93157903777` | Integration du lot 1 dans la porte dashboard. |
| FOR-464 | https://github.com/ygdrasil-io/kanvas/pull/1555 | `c34246afc367588c4c621d6b5d86baf205d2055e` | Manifeste strict du lot 1. |
| FOR-465 | https://github.com/ygdrasil-io/kanvas/pull/1556 | `9079020cef4e93f28d07c633aa8182860e375ee9` | Refus stable `skia-gm-drawminibitmaprect`. |
| FOR-466 | https://github.com/ygdrasil-io/kanvas/pull/1557 | `dcf7deba8e7b5004a90d0ed62578fe620a83e0ea` | Refus stable `skia-gm-image`. |
| FOR-467 | https://github.com/ygdrasil-io/kanvas/pull/1558 | `9f754d8c50d1fd570cb4ab03db856960282464cf` | Refus stable `skia-gm-imagesource`. |
| FOR-468 | https://github.com/ygdrasil-io/kanvas/pull/1559 | `6d2f49cb7f8c77a710940cee48b80c14f126857f` | Refus stable `skia-gm-offsetimagefilter`. |
| FOR-469 | https://github.com/ygdrasil-io/kanvas/pull/1560 | `fd3a54e89b319768311a03ffdbcce8e714a214c6` | Refus stable `skia-gm-pathfill`. |

## Les cinq refus stables

| Ligne | Raison stable |
|---|---|
| `skia-gm-drawminibitmaprect` | `bitmap.drawminibitmaprect.row-specific-artifacts-required` |
| `skia-gm-image` | `image.imagegm.row-specific-artifacts-required` |
| `skia-gm-imagesource` | `image.imagesource.row-specific-artifacts-required` |
| `skia-gm-offsetimagefilter` | `image-filter.offset.row-specific-artifacts-required` |
| `skia-gm-pathfill` | `path-aa.fill.row-specific-artifacts-required` |

Ces refus ne sont pas des echecs nouveaux. Ils disent simplement: "la ligne n'a pas encore les preuves dediees necessaires pour etre consideree comme supportee".

## Ce qui n'est pas revendique

- Aucun nouveau rendu n'est ajoute par D50-9.
- Aucune nouvelle ligne dashboard active n'est ajoutee.
- Aucune nouvelle compatibilite Skia-comparable n'est revendiquee.
- Aucune hausse de support visuel superieure a 50% n'est revendiquee par FOR-465 a FOR-469.
- Aucun seuil global, calcul de score, politique de fallback globale, `PipelineKey`, code renderer, WGSL de production ou source upstream n'est modifie.
- Aucun support large codec, YUV, animation, EXIF, mipmap, tile-mode, image color-managed, image-source dynamique, image-filter DAG, picture-prepass, Path AA, stroke, cap/join/dash, convex path ou edge-budget n'est revendique.

## Suite recommandee

Le prochain travail ne doit pas reouvrir le lot 1 comme s'il restait ambigu. Il faut choisir une des cinq lignes refusees et produire les preuves manquantes ligne par ligne: reference Skia dediee, artefact CPU, artefact WebGPU, diff/stat, diagnostics de route et `fallbackReason=none`. Sans ces preuves, la ligne doit rester `expected-unsupported`.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py
rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py
rtk python3 scripts/validate_for466_skia_gm_image_evidence.py
rtk python3 scripts/validate_for467_skia_gm_imagesource_evidence.py
rtk python3 scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py
rtk python3 scripts/validate_for469_skia_gm_pathfill_evidence.py
rtk python3 scripts/validate_d50_lot1_pm_closeout.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-lot1-pm-closeout.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d50-pm-closeout-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py scripts/validate_for467_skia_gm_imagesource_evidence.py scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py scripts/validate_for469_skia_gm_pathfill_evidence.py scripts/validate_d50_lot1_pm_closeout.py
rtk git diff --check
```
