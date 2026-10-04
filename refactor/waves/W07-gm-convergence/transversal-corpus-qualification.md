# W7 — recensement transversal gelé au HEAD2485cfb

Mesure du 4 octobre 2026, après les lots de fidélité source, admission
readback et contrats Surface promus. Aucun code produit n'est modifié par ce
recensement. Le [snapshot complet](transversal-corpus-2485cfb.json) contient
les 631 fiches exactes du registre ; ce n'est pas 631 rendus GPU réussis.

## Résultat et dénominateurs

Comparaison avec [le snapshot retenu](ordinary-aa-retained-corpus.json),
renderer `7360c5f94e3fcaa2f68d4375d8cdd7ad12295460`.
Le nouveau renderer est `2485cfb47177ec3f9bfd82a2014c6f5233b7448c`,
parent publié [draft #2446](https://github.com/ygdrasil-io/kanvas/pull/2446).

| Mesure | HEAD7360c5 | HEAD2485cfb |
| --- | ---: | ---: |
| Registre recensé | 631 | 631 |
| Éligibles | 443 | 443 |
| Fiches avec `attempted=true` enregistré | 392 | 392 |
| Entrées effectives en rendu, timeout inclus | 393 | 393 |
| Rendus | 220 | 220 |
| Comparés | 197 | 197 |
| Cas avec ≥95 % de pixels conformes à ±2 | 63 | 65 |
| Cas avec ≥99 % de pixels conformes à ±2 | 47 | 49 |
| Médiane des pixels conformes à ±2, parmi les comparés | 76,24387741088867 % | 77,91666666666667 % |
| `declaredContractPass` historique | 195 | 195 |

Dans les deux snapshots, `vertices`607 entre en rendu puis atteint le
watchdog avant la publication du champ `attempted`. Ses `stage=render` et
`outcome=timeout` attestent cette entrée : 392 fiches `attempted=true` plus
ce timeout sans champ, soit 393 tentatives effectives. Les 50
`setup_failed` n'entrent pas en rendu. Le JSON et son résumé restent
inchangés ; cette distinction explique le compteur enregistré, sans
reclasser le timeout ni améliorer un résultat.

Les issues éligibles restent exactement : 197 `compared`, 172
`render_failed`, 50 `setup_failed`, 15 `rendered_uncompared`, 8
`reference_dimension_mismatch`, 1 `timeout`. Aucun gain d'admission n'est
mesuré. Les 133 fonts, 54 codecs externes et l'unique `jpg-color-cube`
quarantiné restent hors périmètre selon la politique existante ; aucune
nouvelle exclusion. Les cas sans référence ou de dimensions incompatibles
restent dans les 443, pas retirés du dénominateur.

343 éligibles ont toujours `minSimilarity=0.0`. Un PASS de ce contrat
historique n'est donc pas une preuve de parité. Le comptage « ≥95 % de
pixels » par cas n'est pas la gate W7 « ≥95 % des cas éligibles conformes ».
Ni cette gate ni l'exécution réussie de tous les éligibles ne sont atteintes.
La somme `elapsedMs` vaut 170787 ms contre 160486 ms ; avec warmup, caches
et compilation séparés, ces valeurs ne constituent pas un benchmark.

## Deltas réellement observés

Trois cas seulement changent leurs métriques pixels et hash RGBA, hors temps.

| Indice / GM | Pixels ±2 avant → après | Exact avant → après |
| --- | --- | --- |
| 0 / `3x3bitmaprect` | 5,696614583333333 % → 100 % | 4,069010416666666 % → 98,37239583333334 % |
| 77 / `child_sampling_rt` | 0 % → 84,32769775390625 % | 0 % → 81,96563720703125 % |
| 592 / `tinybitmap` | 0 % → 100 % | 0 % → 0 % |

Leurs hashes RGBA nouveaux sont respectivement
`efba8d3988b77835e5427a015d809f5a83840fd330d1d79ab772d6dde1c6667c`,
`41ee7f7039e5d02b5210a397b8e636bc53054290fce94046f4c7ffffda35158d` et
`d8d95c3bf9d98a4479ebf85604be243b92c4f306799b966ed371634b23a4fa0c`.
Ces résultats confirment les qualifications ciblées précédentes, sans
qualifier à eux seuls la fidélité de tous les ports ou l'origine historique
des PNG. `tinybitmap` est le seul domaine qui change entre les snapshots
(`LINEAR` → `SRGB_ENCODED`) ; son exact reste nul, le maxRGBA devient
`[0,1,1,0]`. `child_sampling_rt` reste sous 95 %.

Il n'y a pas seulement trois lignes modifiées : les compteurs d'opérations
de 77/592 changent aussi, et huit cas ont uniquement un delta `dispatched`,
sans changement de pixels : 176 `custommesh` 1→17, 177 `custommesh_cs` 1→9,
178 `custommesh_cs_uniforms` 1→19, 179 `custommesh_uniforms` 1→2, 381
`mesh_updates` 1→4, 410 `patch_alpha` 1→13, 414 `patch_primitive` 1→13 et
528 `skbug_13047` 1→2. Ce delta est cohérent avec les contrats Surface
promus qualifiés séparément ; ce n'est pas un gain visuel.

Identités, scopes, seuils, tolérances, hashes des références et statuts de
tous les cas sont inchangés. Les autres métriques pixels sont identiques
sur ce corpus gelé dans cet environnement. Cela ne constitue pas une
affirmation générale sans régression sur toutes les suites ou tous les GPU.

## Ce que les refus disent de l'architecture

Les premiers préfixes de diagnostic sont des frontières observées, pas
des causes indépendantes ni des gains promis après un patch.

| Premier préfixe | Cas éligibles non rendus |
| --- | ---: |
| `w6a.layer.unsupported_child` | 32 |
| `w4d.general.path-resource-limit` | 19 |
| `unsupported.material.runtime_effect.unregistered_semantics` | 14 |
| `unsupported.pipeline.capability_missing` | 14 |
| `unsupported.stroke.rect_anti_alias` | 10 |
| `unsupported.material.gradient.numeric-domain-unbounded` | 9 |
| `w4e.clip.capability-unavailable` | 8 |
| `geometry.path.fan_budget_exceeded` | 8 |

Les 32 premiers refus layer sont hétérogènes : 17 `PATH/ANTIALIASED`, 10
`RECT/ANTIALIASED`, 3 `RECT/HARD_EDGE`, 2 sans origine dans le message ;
18 portent `ownsW6b=true`. Les raisons enfants mentionnent notamment
géométrie, matériaux, clip non intégral, draw complètement clipsé et
capacités de filtres. Ouvrir une lane supplémentaire ne prouve pas leur
résolution. Les budgets/caps ne sont pas relevés pour transformer ces
constats en PASS.

Autre frontière causale visible dans le code actuel : tous les `DrawRect`
stroked passent par `GPUPreparedStrokeRectLowerer`, qui refuse
inconditionnellement `paint.antiAlias`. `GPUOpMapper` retourne alors avant
le planner de composition ; le builder attend les quatre bandes Core de
cette route legacy, sauf ownership par la route commune. La prochaine
investigation compare cette route avec la géométrie path AA commune et
ses contrats d'identité/capture. Elle ne promet pas dix rendus supplémentaires,
ne désactive pas l'AA et ne supprime pas les guards.

Pas de migration globale du domaine encoded à ce stade : les limites de
`CompositionAdmissionV1` sur courbes/layers/matériaux demandent des preuves
appariées avant un changement de default.

### Priorité révisée après relecture indépendante Sol

La stratégie initiale de simple normalisation Rect→Path est insuffisante :
le Core source-free exclut les Rect stroked, sa géométrie stroke exige un
segment unique, et son inventaire de couverture actuel n'accepte pas AA4.
L'autorité math/AA4 de `W4dGeneralPathPlanCompiler` est distincte ; une
scène standalone Rect/Path qui passe ne démontre pas la fermeture d'une
frame Core mixte. Aucun guard n'est supprimé et aucun fix n'est livré ici.

Classement retenu : B admission enfant commune, puis A géométrie/couverture
Rect stroke commune, puis C consommation encoded générale. Première gate
de B : même PATH AA STROKE solide SrcOver, sans filtre propre, au root puis
dans un saveLayer plain ; contrôle root avec sibling filtré déjà qualifié.
Oracle indépendant full/empty/half sur tout le buffer, vraie completion,
replay même Surface et surfaces fraîches. Le diagnostic actuel doit prouver
une restriction de scope, pas une absence de consommateur AA4 ou un écart
de composition au restore. Alpha, ordre, clip/coordonnées, budgets,
négatifs filtre/AA clip/blend et refus public layer encoded restent à
qualifier avant toute extension. Pas de nouvelle lane ou flag par GM.

Cette gate est planifiée, pas exécutée. Si elle échoue, ne pas élargir le
guard : revenir au contrat commun geometry/coverage. Les 32 préfixes ne
constituent toujours pas une promesse de gains. Analyse Sol en lecture
seule, indépendante des décisions de main, sans build/GPU/mutation Git ;
rapport local conservé dans
`/private/tmp/kanvas-w7-transversal-strategy.mN3jDF/strategy-after-census-report.md`.
Aucune analyse Astra n'a pu être lancée à cause de la limite de sous-agents.

## Procédure native et audit

Cinq tranches sérialisées, mêmes HEAD/config/registre/références, tâche
existante `:integration-tests:skia:measureSkiaParity`, `--no-daemon`,
`images=false`, watchdog GM existant de 30 s, borne wrapper 240 s. Aucune
génération d'image, de score ou de dashboard. Pas de JUnit/XML/events pour
cette tâche JavaExec : les issues sont des mesures, pas des tests PASS.

| Tranche | PID enfant | Wrapper / Gradle exit | SHA256 JSONL |
| --- | ---: | ---: | --- |
| [0,200) | 79564 | 0 | `c9e889e66f5fe560115b2fb9ea76b1a572f40cde4e0d5d24f17eb56770429614` |
| [200,400) | 83046 | 0 | `d31bd55ce0f25518603f074105f38ed97a42aa8ebbac931b54c770f9b18d9011` |
| [400,607) | 87940 | 0 | `3fd6c67f9bcfdad8bdf2ed5442b68140c420c1a3fbd5560502f181025e55d83e` |
| [607,608) | 88658 | 1 | `58f186f8bca38e65bcafee49c3f90fe1066f85c4aaf72ce20d920417fa9a1b3d` |
| [608,631) | 89268 | 0 | `f9e28931da9b9728261fbbd0744ff84fd3e7ba55bda49379f3afa156e1d9938a` |

`vertices`607 conserve son timeout render à 30 s, sortie native124,
wrapper/Gradle1 et absence de footer complete. Il est isolé pour laisser le
recensement continuer, pas exclu. Le `unknown:1` du résumé de diagnostics
désigne ce timeout sans diagnostic, pas un nouveau refus non classé.
`ninepatch-stretch`396 a pris 26505 ms, proche de la borne existante, qui
n'est pas augmentée. Aucun timeout extérieur ni signal enfant observé.

Chaque archive native contient `process.log`, `exit.json` et le JSONL.
Main a lu intégralement stdout/stderr fusionnés, exits, inventaires et tous
les records, vérifié l'absence des processus du PGID possédé, puis exécuté
un postseal distinct avant le run suivant. Pas d'écriture code/docs/Git
pendant les runs. Les warnings Java native-access, LWJGL Unsafe et Gradle
sont conservés ; sortie sans warnings non revendiquée.

Archives locales conservées :
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/transversal-corpus-2485cfb-20261004-FROM-TO`.
Les copies exactes des cinq journaux, inventaires et reçus contrôleur sont
dans `/private/tmp/kanvas-w7-transversal-strategy.mN3jDF/`. Leur présence et
leurs hashes peuvent être revérifiés ; la chronologie intégrale de lecture
et de sérialisation est une attestation du contrôleur, pas une propriété
prouvée par les seuls hashes.

Le script existant `refactor/waves/W07-gm-convergence/summarize-parity.mjs` produit le snapshot
(format `skia-parity-snapshot-v1`) avec ses guards inchangés : 631 indices
uniques consécutifs, cinq runs du même HEAD/registre, aucun cas omis.
SHA256 du fichier durable, 740730 bytes :
`ecef91f79e25940f9fabe2e2c624734a9fa2bcaa861391f91f0a8c7b4dcd74f8`.

## Invariants pré/post et limites

Les 69 chemins critiques scellés avant/après chaque run restent identiques,
y compris le diagnostic inverse untracked protégé
`96cd8349c5b08032fe7374566e4b220edd931e881ee1e3e3cbbfcdaf92bfd747`.
814 PNG générées et 559 scores existants sont inchangés ; manifest repo-rel
des PNG `e589c21c3c3eebd3787434064fe5a122f6dc2005cf4d6786b529b1c8fd2a2c6d`,
fichier scores `ba0bd77609386acd8b77443d853d424d36edea08719b8e868b71a65fbc8dbd54`.
Les 631 statuts de référence du registre sont contrôlés : 616 PNG/hashs
présents conformes (613 `trusted`, 3 `untrustable`) et 15 absences attendues,
sans dérive. Les 15 éligibles `rendered_uncompared` comprennent 12 références
manquantes et les 3 `untrustable` ; les 3 autres absences concernent des cas
déjà exclus. L'inventaire physique distinct contient 1004 PNG,
`1fe843ea38ab34eaa9b49fa59eb872b2cee671e849e8645f5408e2bca510a7c9`.
Wrapper/init restent gelés. Les chemins et bytes complets figurent dans les
reçus privés ; aucune nouvelle référence n'est créée ou remplacée.

Mac OS X/aarch64/Java25.0.1 : mesure native de cet environnement seulement.
Pas de nouveaux PNG à inspecter dans ce recensement `images=false`, pas de
qualification visuelle entière de tous les GM. Les ports incomplets, la
couverture AA, Picture, les origines historiques des références, les dettes
de suites et l'ownership stencil restent ouverts.

La nouvelle branche `codex/w7-transversal-corpus-census` empile uniquement
le snapshot et ce suivi sur #2446. La review indépendante Sol a relevé
I1 (tentatives enregistrées/effectives) et M1 (statuts/PNG présentes),
corrigés uniquement dans les documents ; relecture ciblée en attente. Aucune
review Astra n'est revendiquée (dispatch refusé `agent thread limit reached`).
W7 ACTIVE : pas de merge, de global GREEN, de parité complète ni de clôture.
