# Design: phase 6 familles PATH + CLIP

## Objectif

Continuer la phase 6 de `refactor/05-roadmap.md` apres la vague `IMAGE` en
traitant le groupe semantique **Coverage** : `RenderFamily.PATH` et
`RenderFamily.CLIP`.

Le but principal est de produire une evidence exploitable famille par famille,
pas de declarer le support large des paths ou des clips. La vague doit
cartographier les rows, distinguer les supports deja instrumentes des refus
stables, separer les `no-score` des vrais fails, et documenter les deltas du
dashboard.

`PATH` et `CLIP` sont regroupes parce qu'ils partagent le meme coeur renderer :
la conversion de geometrie en couverture pixels. Les deux familles touchent aux
memes decisions de budgets, AA, verb streams, strokes, convexite, nested clips,
clip stack depth, inverse clips, perspective, et fallback diagnostics.

## Sources et contraintes

Sources actives :

- `refactor/05-roadmap.md`, phase 6 ;
- `.upstream/target/skia-like-realtime-renderer-target.md` ;
- `.upstream/specs/skia-like-realtime/README.md` ;
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md` ;
- `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md` ;
- `.upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md` ;
- `.upstream/specs/geometry-coverage/README.md` ;
- `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md` ;
- `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

Contexte observe avant cette spec :

- le dashboard local genere le 2026-07-08 liste 517 rows, dont 58 `PATH` et
  32 `CLIP` ;
- le `master` courant contient plus de GMs que ce dashboard local, avec un
  comptage source approximatif de 241 declarations `PATH` et 40 declarations
  `CLIP` ;
- la premiere tache d'execution devra donc regenerer le dashboard avant de
  figer les compteurs d'evidence.

Contraintes :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire SkSL, son IR ou sa VM ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible shader ;
- ne pas baisser les seuils globaux de similarite ;
- ne pas masquer les `no-score` ;
- ne pas promouvoir une row sans reference, generated render, diff/stat,
  diagnostics de route, et fallback policy explicite ;
- ne pas annoncer de support large Path AA, clip stack arbitraire, path boolean
  parity, perspective clipping, ou global coverage budget increase ;
- garder les corrections renderer lourdes dans des PRs separees si elles ne
  sont pas strictement necessaires a l'evidence.

## Choix retenu

Nous retenons l'approche **coverage-family evidence wave**.

La vague couvre `PATH` et `CLIP` dans une meme infrastructure d'evidence, mais
elle continue de produire des compteurs separes par famille et par sous-famille.
Elle generalise les lecons de la vague `IMAGE` :

- inventaire depuis le dashboard et le registre GM ;
- classification row-level ;
- refus stables avec reason codes ;
- rapport JSON/CSV/Markdown ;
- non-claims explicites ;
- dashboard regenere comme source de verite visuelle.

Cette approche est preferable a une vague `PATH` seule parce que beaucoup de
rows `CLIP` echouent ou reussissent pour les memes raisons que les rows `PATH`.
Elle est aussi moins risquee qu'un groupe `BLUR/filter` ou `TEXT`, qui demande
plus rapidement des changements renderer et resource ownership lourds.

## Architecture

L'orchestration d'evidence doit rester dans `:integration-tests:skia-evidence`.
Le module peut etre generalise depuis le modele `Phase6ImageFamilyEvidence`,
mais le design ne demande pas de refactor massif avant d'avoir stabilise le
second groupe.

Flux cible :

```text
SkiaGmRegistry PATH + CLIP rows
  -> generateSkiaDashboard
  -> coverage-family classifier
  -> family/subfamily counters
  -> stable refusal taxonomy
  -> reports/gpu-renderer/phase-6-coverage-families/*
```

Responsabilites :

- `integration-tests/skia` reste proprietaire du dashboard, des references, des
  PNG generated/diff et des taches de regeneration.
- `integration-tests/skia-evidence` porte la lecture du dashboard, la
  classification, l'export JSON/CSV/Markdown et les tests de policy.
- `gpu-renderer/geometry` et `gpu-renderer/coverage` restent proprietaires des
  routes et diagnostics de coverage si la vague lit ou complete leurs facts.
- Le rapport Phase 6 documente les rows, raisons de refus, gaps et non-claims ;
  il ne deplace pas le support sans preuve visuelle et route-level.

## Sous-familles

Sous-familles `PATH` initiales :

```text
path-fill-simple
path-fill-convex
path-fill-concave
path-stroke-basic
path-stroke-caps-joins
path-dash-gated
path-hairline-gated
path-ops-gated
path-large-budget-gated
path-perspective-gated
path-shader-material-gated
```

Sous-familles `CLIP` initiales :

```text
clip-rect
clip-rrect
clip-convex
clip-nested-bounded
clip-complex-gated
clip-inverse-gated
clip-perspective-gated
clip-large-budget-gated
clip-path-aa-gated
```

Ces sous-familles peuvent etre ajustees pendant l'execution si le dashboard
regenere revele des rows nouvelles depuis `#2010`, mais tout changement doit
rester explicite dans les tests et le rapport.

## Classification

Les categories suivent la vague `IMAGE` :

- `promoted-support` : support revendique avec reference, generated PNG,
  diff/stat, score au-dessus du seuil, diagnostics de route et fallback `none`.
- `instrumented-existing` : row qui rend ou compare utilement, mais sans preuve
  suffisante pour une promotion Phase 6.
- `expected-unsupported` : row hors scope avec refus stable.
- `no-score` : row sans score exploitable, reference manquante, size mismatch,
  render absent ou evidence incomplete.
- `unexpected-fail` : fail qui n'a pas de refus stable ou d'explication
  attendue.

Reason codes representatifs :

```text
unsupported.coverage.verb_budget_exceeded
unsupported.coverage.edge_budget_exceeded
unsupported.coverage.clip_depth_exceeded
unsupported.coverage.inverse_clip
unsupported.coverage.perspective_clip
unsupported.coverage.path_ops
unsupported.coverage.dash_pattern
unsupported.coverage.hairline_stroke
unsupported.coverage.stroke_join_or_cap
unsupported.coverage.complex_clip
unsupported.material.shader_on_path
```

Les reason codes finaux doivent etre dedoublonnes avec les diagnostics deja
existants dans `gpu-renderer` avant d'etre commits.

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

- `reports/gpu-renderer/phase-6-coverage-families/evidence.json` ;
- `reports/gpu-renderer/phase-6-coverage-families/classification.csv` ;
- `reports/gpu-renderer/YYYY-MM-DD-gpu-phase-6-coverage-families.md`.

## Validation

Validation minimale de la vague :

- `:integration-tests:skia:generateSkiaDashboard` regenere le dashboard ;
- le rapport coverage lit le dashboard regenere ;
- les tests de classification couvrent au moins :
  - path fill simple ;
  - stroke caps/joins ;
  - dash gated ;
  - path ops gated ;
  - rect/rrect clip ;
  - nested bounded clip ;
  - inverse/complex/perspective clip ;
  - no-score vs unexpected-fail ;
- `git diff --check` reste propre ;
- le rapport final liste les deltas `PATH` et `CLIP` separement.

La suite globale peut rester non verte si les echecs sont hors scope et deja
documentes, mais les tests du module d'evidence et les taches de generation
propres a cette vague doivent passer.

## Non-Claims

Cette vague ne revendique pas :

- broad Path AA ;
- broad clip stack ;
- path boolean parity ;
- perspective clip support ;
- broad stroke-outline parity ;
- global edge/verb budget increase ;
- shader/material parity sur tous les paths ;
- performance gate release-blocking pour PATH/CLIP.

## Ordre d'execution propose

1. Regenerer le dashboard depuis `origin/master` courant.
2. Generaliser ou etendre `:integration-tests:skia-evidence` pour `PATH + CLIP`.
3. Ajouter les tests de classification coverage.
4. Generer JSON/CSV/Markdown.
5. Relire les reason codes contre les diagnostics renderer existants.
6. Documenter les compteurs, deltas, no-scores, expected-unsupported et
   unexpected-fails.
7. Faire une review dediee avant toute correction renderer.
