# Design: phase 6 familles Effect + Composition

## Objectif

Continuer la phase 6 de `refactor/05-roadmap.md` apres les vagues `IMAGE`,
`PATH + CLIP`, et `GRADIENT + RUNTIME_EFFECT + COLOR` avec un groupe
semantique **Effect + Composition** :
`RenderFamily.COMPOSITE` et `RenderFamily.BLUR`.

Le but est de produire une evidence exploitable famille par famille, pas de
declarer le support large des blend modes, `saveLayer`, image filters, blur
graphs, backdrop filters ou destination reads. La vague doit inventorier les
rows du dashboard, distinguer les supports deja instrumentes des refus stables,
separer les `no-score` des vrais fails, documenter les deltas du dashboard, et
garder visibles les limites qui demandent de vrais travaux renderer.

Ces deux familles sont regroupees parce qu'elles touchent toutes les deux au
traitement apres production de couleur et couverture :

- `COMPOSITE` decide comment le resultat courant est combine avec la
  destination, les couches, les filters et les blend modes ;
- `BLUR` est un effet spatial qui demande souvent une image intermediaire,
  des bounds explicites, une politique de sigma, et parfois un filter graph.

`TEXT`, `MESH`, `IMAGE`, `PATH`, `CLIP`, `GRADIENT`, `RUNTIME_EFFECT` et
`COLOR` restent hors scope sauf quand une row `COMPOSITE` ou `BLUR` les exerce
comme dependance. Dans ce cas la classification doit refuser la dependance avec
un reason code stable au lieu de l'absorber silencieusement.

## Sources et contraintes

Sources actives :

- `refactor/05-roadmap.md`, phase 6 ;
- `.upstream/target/skia-like-realtime-renderer-target.md` ;
- `.upstream/target/high-performance-wgsl-pipeline-target.md` ;
- `.upstream/specs/skia-like-realtime/README.md` ;
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md` ;
- `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md` ;
- `.upstream/specs/wgsl-pipeline/README.md` ;
- `docs/superpowers/specs/2026-07-08-gpu-phase-6-image-family-design.md` ;
- `docs/superpowers/specs/2026-07-09-gpu-phase-6-coverage-families-design.md` ;
- `docs/superpowers/specs/2026-07-09-gpu-phase-6-material-families-design.md` ;
- `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md` ;
- `reports/gpu-renderer/2026-07-09-gpu-phase-6-coverage-families.md` ;
- `reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md` ;
- `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

Contexte observe avant cette spec :

- le dashboard courant liste 113 rows `COMPOSITE` et 45 rows `BLUR` ;
- les vagues Phase 6 precedentes ont deja installe le modele
  `:integration-tests:skia-evidence` avec lecture dashboard, classification,
  export JSON/CSV/Markdown et non-claims ;
- `COMPOSITE` est la plus grosse famille restante non traitee ;
- `BLUR` contient des rows simples de mask blur, mais aussi des rows qui
  exercent image filters, matrix convolution, text filters, backdrop blur ou
  grandes textures ;
- cette branche demarre depuis l'etat courant apres merge des vagues image,
  coverage et material.

Contraintes :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire SkSL, son IR ou sa VM ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible shader ;
- ne pas baisser les seuils globaux de similarite ;
- ne pas masquer les `no-score` ;
- ne pas promouvoir une row sans reference, generated render, diff/stat,
  diagnostics de route/effect/composition, fallback policy explicite et
  non-claim ;
- ne pas annoncer de support large `saveLayer`, destination-read, backdrop
  filter, image-filter DAG, matrix convolution, advanced blend chain, ou blur
  arbitraire ;
- ne pas compter un rendu visuellement passant comme support si la route
  reelle est une approximation, une omission ou une dependance hors scope ;
- garder les corrections renderer lourdes dans des PRs separees si elles ne
  sont pas strictement necessaires a l'evidence.

## Choix retenu

Nous retenons l'approche **Effect + Composition evidence wave** :
`COMPOSITE + BLUR` en inventaire large, avec compteurs separes par famille et
sous-famille.

Cette approche est preferable a une vague `COMPOSITE` seule parce que beaucoup
de rows `BLUR` partagent les memes decisions d'architecture : bounds,
intermediate textures, filter ownership, layer composition et refus stables.
Elle est moins risquee qu'une vague "filters" plus large qui absorberait des
rows `IMAGE`, `TEXT`, `COLOR` ou `RUNTIME_EFFECT` deja traitees ou dependantes
d'autres sous-systemes.

La vague doit rester une vague d'evidence. Elle peut signaler des candidats de
fixes futurs, mais elle ne modifie pas le renderer par defaut et ne deplace pas
une row en support revendique sans preuve complete.

## Architecture

L'orchestration d'evidence reste dans `:integration-tests:skia-evidence`.
Elle suit le modele des vagues `IMAGE`, `PATH + CLIP` et Material.

Flux cible :

```text
Skia GM dashboard regenere
  -> rows COMPOSITE / BLUR
  -> effect-composition-family classifier
  -> family/subfamily/classification counters
  -> stable refusal taxonomy
  -> reports/gpu-renderer/phase-6-effect-composition-families/*
```

Responsabilites :

- `integration-tests/skia` reste proprietaire du dashboard GM, des references,
  des PNG generated/diff et des taches de regeneration.
- `integration-tests/skia-evidence` porte la lecture du dashboard, la
  classification, l'export JSON/CSV/Markdown et la tache Gradle
  `generateGpuPhase6EffectCompositionFamiliesEvidence`.
- `render-pipeline` reste proprietaire du contrat semantique pour blend,
  color filter, alpha modulation, load-dst/store et fallback plans.
- `gpu-renderer` reste proprietaire des diagnostics WebGPU, layer plans,
  intermediate texture ownership, PipelineKey, resource/cache counters et
  refus GPU.
- Le rapport Phase 6 documente rows, refus, gaps et non-claims ; il ne cree
  pas de support par classification seule.

La vague ne doit pas inventer un nouveau moteur de filter graph. Si une row
necessite un `ImageFilterDAG`, un `saveLayer` isole, un readback destination ou
une chaine d'intermediaires non modelisee, elle doit rester
`expected-unsupported` ou `no-score` avec raison explicite.

## Sous-familles

Sous-familles `COMPOSITE` initiales :

```text
composite-src-over-basic
composite-porter-duff
composite-advanced-blend-gated
composite-xfermode-gated
composite-save-layer-gated
composite-backdrop-gated
composite-destination-read-gated
composite-image-filter-gated
composite-color-filter-gated
composite-overdraw-diagnostic
composite-atlas-or-vertices-gated
composite-layer-bounds-gated
```

Sous-familles `BLUR` initiales :

```text
blur-mask-basic
blur-rect-rrect-circle
blur-image-basic
blur-small-sigma
blur-large-sigma-gated
blur-transform-or-perspective-gated
blur-clip-interaction-gated
blur-image-filter-gated
blur-filter-graph-gated
blur-matrix-convolution-gated
blur-backdrop-gated
blur-text-dependent-gated
blur-resource-budget-gated
```

Ces sous-familles peuvent etre ajustees pendant l'execution si le dashboard
regenere revele des rows nouvelles, mais tout changement doit rester explicite
dans les tests et le rapport.

## Classification

Les categories suivent les vagues precedentes :

- `promoted-support` : support revendique avec reference, generated PNG,
  diff/stat, score au-dessus du seuil, diagnostics de route/effect et fallback
  `none`.
- `instrumented-existing` : row qui rend ou compare utilement, mais sans
  preuve suffisante pour une promotion Phase 6.
- `expected-unsupported` : row hors scope avec refus stable.
- `no-score` : row sans score exploitable, reference manquante, render absent,
  size mismatch ou evidence incomplete.
- `unexpected-fail` : fail qui n'a pas de refus stable ou d'explication
  attendue.

Une row ne peut pas etre promue depuis son nom, sa famille ou son score seul.
Elle doit prouver la route effectivement utilisee. Par exemple, un blur qui
compare bien mais qui ignore une source image, un crop, un layer input ou un
destination read reste `instrumented-existing` ou `expected-unsupported` selon
la politique de refus disponible.

Reason codes representatifs :

```text
unsupported.composition.advanced_blend
unsupported.composition.xfermode
unsupported.composition.save_layer
unsupported.composition.backdrop_filter
unsupported.composition.destination_read
unsupported.composition.image_filter_dag
unsupported.composition.layer_bounds
unsupported.composition.atlas_or_vertices
unsupported.blur.large_sigma
unsupported.blur.image_filter_graph
unsupported.blur.matrix_convolution
unsupported.blur.transform_or_perspective
unsupported.blur.clip_interaction
unsupported.blur.backdrop
unsupported.blur.text_dependency
unsupported.blur.resource_budget
```

Les reason codes finaux doivent etre dedoublonnes avec les diagnostics deja
existants dans `render-pipeline` et `gpu-renderer` avant d'etre commits.

## Data Flow

Le rapport lit `data/gms.json` du dashboard regenere. Il doit conserver les
champs dashboard sans supposer un schema trop ferme :

- `name` ;
- `family` ;
- `similarity` ;
- `minSimilarity` ;
- `isPassing` ;
- `renderFailed` ;
- `noReference` ;
- `sizeMismatch` ;
- dimensions et stats de diff quand presentes.

Le rapport produit :

- `reports/gpu-renderer/phase-6-effect-composition-families/evidence.json` ;
- `reports/gpu-renderer/phase-6-effect-composition-families/classification.csv` ;
- `reports/gpu-renderer/YYYY-MM-DD-gpu-phase-6-effect-composition-families.md`.

Le JSON doit exposer au minimum :

- compteurs globaux ;
- compteurs par famille ;
- deltas de famille depuis le dashboard de depart ;
- compteurs par sous-famille ;
- compteurs par classification ;
- rows `no-score` avec `noScoreCause` ;
- rows `unexpected-fail` ;
- refus stables avec reason code ;
- non-claims globaux ;
- candidats de follow-up classes par root cause.

## Validation

Validation minimale de la vague :

- `:integration-tests:skia:generateSkiaDashboard` regenere le dashboard ;
- le rapport effect/composition lit le dashboard regenere ;
- les tests de classification couvrent au moins :
  - `COMPOSITE` SrcOver/basic ;
  - Porter-Duff / xfermode ;
  - advanced blend gated ;
  - `saveLayer` gated ;
  - backdrop / destination-read gated ;
  - image-filter composition gated ;
  - overdraw diagnostic ;
  - `BLUR` mask/basic ;
  - small sigma vs large sigma ;
  - image blur ;
  - blur with clip or transform ;
  - matrix convolution gated ;
  - text-dependent blur gated ;
  - no-score vs unexpected-fail ;
- `:integration-tests:skia-evidence:test` passe ;
- la tache Gradle de generation effect/composition passe ;
- `git diff --check` reste propre ;
- le rapport final liste les deltas `COMPOSITE` et `BLUR` separement.

La suite globale peut rester non verte si les echecs sont hors scope et deja
documentes, mais les tests du module d'evidence et les taches de generation
propres a cette vague doivent passer.

## Non-Claims

La vague ne revendique pas :

- support complet de `COMPOSITE` ;
- support complet de `BLUR` ;
- support large `saveLayer` ;
- support destination-read ou backdrop filter ;
- support arbitraire image-filter DAG ;
- support matrix convolution ;
- support de tous les blend modes Skia ;
- support des advanced blends sans allowlist explicite ;
- support de blur text/glyph independant de la vague `TEXT` ;
- equivalence Skia sans reference et route diagnostics.

## Risques et mitigations

Risque : `COMPOSITE` absorbe trop de familles transverses.

Mitigation : ne classer que les rows dont `family == "COMPOSITE"` et refuser
les dependances hors scope avec reason code stable.

Risque : `BLUR` devient implicitement une vague image-filter DAG.

Mitigation : distinguer les blurs simples des graphes de filters et garder les
graphes non modelises en `expected-unsupported`.

Risque : des rows passent avec un rendu incomplet parce qu'une operation
critique est omise.

Mitigation : `instrumented-existing` reste le statut par defaut quand la route
effect/composition n'est pas prouvee ; `promoted-support` exige diagnostics.

Risque : les reason codes divergent des diagnostics renderer existants.

Mitigation : la premiere tache d'implementation doit auditer les diagnostics
existants et dedoublonner les nouveaux codes avant de figer les tests.

## Definition of Done

La phase 6 Effect + Composition est terminee quand :

- la spec est committee ;
- le plan d'implementation est committe ;
- le module `:integration-tests:skia-evidence` contient le classifier/writer
  dedie ;
- les artifacts JSON/CSV/Markdown sont generes ;
- les `no-score` et `unexpected-fail` sont separes ;
- `COMPOSITE` et `BLUR` ont des compteurs separes ;
- les refus `saveLayer`, destination-read, image-filter DAG, advanced blend,
  matrix convolution, large sigma et text-dependent blur sont visibles ;
- les non-claims empechent toute lecture comme support large ;
- les tests de la vague et `git diff --check` passent ;
- une review independante ne trouve pas de probleme bloquant.
