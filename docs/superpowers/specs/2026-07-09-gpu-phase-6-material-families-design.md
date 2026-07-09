# Design: phase 6 familles Material

## Objectif

Continuer la phase 6 de `refactor/05-roadmap.md` apres les vagues `IMAGE`
et `PATH + CLIP` avec un groupe semantique **Material** :
`RenderFamily.GRADIENT`, `RenderFamily.RUNTIME_EFFECT` et
`RenderFamily.COLOR`.

Le but est de produire une evidence exploitable famille par famille, pas de
declarer le support complet des shaders, gradients, runtime effects ou color
management. La vague doit inventorier les rows du dashboard, distinguer les
supports deja instrumentes des refus stables, separer les `no-score` des vrais
fails, et documenter les deltas du dashboard.

Ces trois familles sont regroupees parce qu'elles decrivent comment un paint
fabrique la couleur du pixel avant composition. `COMPOSITE` reste hors scope :
ses rows melangent blend modes, compose shaders, color filters, image filters,
saveLayer, atlas, destination-read et vertices. Ces sujets demandent une vague
dediee.

## Sources et contraintes

Sources actives :

- `refactor/05-roadmap.md`, phase 6 ;
- `.upstream/target/skia-like-realtime-renderer-target.md` ;
- `.upstream/specs/skia-like-realtime/README.md` ;
- `.upstream/target/high-performance-wgsl-pipeline-target.md` ;
- `.upstream/specs/wgsl-pipeline/README.md` ;
- `docs/superpowers/specs/2026-07-08-gpu-phase-6-image-family-design.md` ;
- `docs/superpowers/specs/2026-07-09-gpu-phase-6-coverage-families-design.md` ;
- `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md` ;
- `reports/gpu-renderer/2026-07-09-gpu-phase-6-coverage-families.md` ;
- `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

Contexte observe avant cette spec :

- le dashboard courant liste 56 rows `GRADIENT`, 25 rows `RUNTIME_EFFECT` et
  20 rows `COLOR` ;
- le module `:integration-tests:skia-evidence` contient deja les generateurs
  Phase 6 `IMAGE` et `PATH + CLIP` ;
- le workspace est sur un linked worktree propre, branche
  `codex/phase-6-material-families`, creee depuis `origin/master`.

Contraintes :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire SkSL, son IR ou sa VM ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible shader ;
- garder `SkRuntimeEffect` comme facade de compatibilite seulement ;
- ne compter un runtime effect comme supporte que s'il a un descripteur Kanvas
  enregistre, un comportement Kotlin/CPU, et un module WGSL valide ;
- ne pas baisser les seuils globaux de similarite ;
- ne pas masquer les `no-score` ;
- ne pas promouvoir une row sans reference, generated render, diff/stat,
  diagnostics de route/material, fallback policy explicite et non-claim ;
- ne pas inclure `COMPOSITE`, `BLUR`, `IMAGE_FILTERS`, `MESH` ou `TEXT` dans
  cette vague ;
- ne pas annoncer de support large shader, color-space, color-filter, runtime
  effect arbitraire, compose shader ou blend chain.

## Choix retenu

Nous retenons l'approche **Material strict** :
`GRADIENT + RUNTIME_EFFECT + COLOR`.

La vague couvre toute la largeur de ces trois familles dans l'evidence, mais
elle ne promeut que les rows qui ont des preuves completes. Les rows qui
passent visuellement sans diagnostics material suffisants restent
`instrumented-existing`. Les rows hors scope deviennent `expected-unsupported`
avec reason code stable.

Cette approche est plus utile qu'une vague `GRADIENT` seule, parce que les
runtime effects et la couleur partagent la meme frontiere paint pipeline. Elle
est moins risquee qu'une vague Material large qui absorberait `COMPOSITE` et
melangerait composition, filters et destination-read avec le sujet paint.

## Architecture

L'orchestration d'evidence reste dans `:integration-tests:skia-evidence`.
Elle suit le modele des vagues `IMAGE` et `PATH + CLIP`.

Flux cible :

```text
Skia GM dashboard regenere
  -> rows GRADIENT / RUNTIME_EFFECT / COLOR
  -> material-family classifier
  -> family/subfamily/classification counters
  -> stable refusal taxonomy
  -> reports/gpu-renderer/phase-6-material-families/*
```

Responsabilites :

- `integration-tests/skia` reste proprietaire du dashboard GM, des references,
  des PNG generated/diff et des taches de regeneration.
- `integration-tests/skia-evidence` porte la lecture du dashboard, la
  classification, l'export JSON/CSV/Markdown et la tache Gradle
  `generateGpuPhase6MaterialFamiliesEvidence`.
- `render-pipeline` / paint pipeline restent proprietaires des contrats
  shader, color filter, blend, color-space et fallback quand une row expose
  une preuve de route.
- `gpu-renderer` reste proprietaire des diagnostics WebGPU, PipelineKey,
  resource/cache et fallback lorsque la row material touche le backend.
- Le rapport Phase 6 documente rows, refus, gaps et non-claims ; il ne modifie
  pas le renderer par defaut.

## Sous-familles

Sous-familles `GRADIENT` initiales :

```text
gradient-linear
gradient-radial
gradient-sweep
gradient-conical
gradient-hard-stops
gradient-many-stops-gated
gradient-tile-mode
gradient-local-matrix
gradient-perspective-gated
gradient-color-space-gated
```

Sous-familles `RUNTIME_EFFECT` initiales :

```text
runtime-effect-registered
runtime-effect-unregistered-gated
runtime-effect-color-filter
runtime-effect-child-shader-gated
runtime-effect-image-or-surface-gated
```

Sous-familles `COLOR` initiales :

```text
color-solid
color-alpha
color-filter-gated
color-space-gated
color-processor-gated
```

Ces sous-familles peuvent etre ajustees pendant l'execution si le dashboard
regenere revele des rows nouvelles, mais tout changement doit rester explicite
dans les tests et le rapport.

## Classification

Les categories suivent les vagues precedentes :

- `promoted-support` : support revendique avec reference, generated PNG,
  diff/stat, score au-dessus du seuil, diagnostics de route/material et
  fallback `none`.
- `instrumented-existing` : row qui rend ou compare utilement, mais sans preuve
  suffisante pour une promotion Phase 6.
- `expected-unsupported` : row hors scope avec refus stable.
- `no-score` : row sans score exploitable, reference manquante, render absent,
  size mismatch ou evidence incomplete.
- `unexpected-fail` : fail qui n'a pas de refus stable ou d'explication
  attendue.

Une row ne peut pas etre promue depuis son nom ou sa sous-famille seule. Elle
doit avoir des artifacts de comparaison et des diagnostics qui prouvent que la
route material revendiquee est bien celle qui a rendu l'image.

Reason codes representatifs :

```text
unsupported.material.perspective_shader
unsupported.material.color_space
unsupported.material.gradient_many_stops
unsupported.material.gradient_tile_mode
unsupported.material.color_filter
unsupported.material.color_processor
unsupported.runtime_effect.unregistered_descriptor
unsupported.runtime_effect.child_shader
unsupported.runtime_effect.image_or_surface_input
unsupported.runtime_effect.dynamic_sksl
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

- `reports/gpu-renderer/phase-6-material-families/evidence.json` ;
- `reports/gpu-renderer/phase-6-material-families/classification.csv` ;
- `reports/gpu-renderer/YYYY-MM-DD-gpu-phase-6-material-families.md`.

Le JSON doit exposer au minimum :

- compteurs globaux ;
- compteurs par famille ;
- deltas de famille depuis le dashboard de depart ;
- compteurs par sous-famille ;
- compteurs par classification ;
- rows `no-score` avec `noScoreCause` ;
- rows `unexpected-fail` ;
- refus stables avec reason code ;
- non-claims globaux.

## Validation

Validation minimale de la vague :

- `:integration-tests:skia:generateSkiaDashboard` regenere le dashboard ;
- `:integration-tests:skia-evidence:generateGpuPhase6MaterialFamiliesEvidence`
  lit le dashboard regenere et produit JSON/CSV/Markdown ;
- `:integration-tests:skia-evidence:test` couvre au moins :
  - filtrage strict `GRADIENT`, `RUNTIME_EFFECT`, `COLOR` ;
  - gradients linear, radial, sweep, conical, hard-stops et local-matrix ;
  - gates perspective, many-stops, tile-mode et color-space ;
  - runtime effect enregistre vs descripteur absent ;
  - runtime effect avec child shader ou input image/surface gated ;
  - couleur solide/alpha vs color filter/color processor gated ;
  - `no-score` separe de `unexpected-fail` ;
  - compteurs famille, classification et sous-famille ;
- `git diff --check` reste propre ;
- le rapport final liste les deltas `GRADIENT`, `RUNTIME_EFFECT` et `COLOR`
  separement.

La suite globale peut rester non verte si les echecs sont hors scope et deja
documentes, mais les tests du module d'evidence et les taches de generation
propres a cette vague doivent passer.

## Non-Claims

Cette vague ne revendique pas :

- le support complet des shaders ;
- un compilateur SkSL ou une execution dynamique de SkSL ;
- le support arbitraire de `SkRuntimeEffect` ;
- le support large des child shaders runtime-effect ;
- le support large des inputs image/surface dans les runtime effects ;
- le color management large ou wide-gamut parity ;
- le support complet des color filters ou color processors ;
- les compose shaders, blend modes, filter DAGs, saveLayer ou destination-read
  de `COMPOSITE` ;
- une promotion depuis le nom de GM seul ;
- une amelioration de score sans artifact reference/generated/diff et
  diagnostics de route.

## Plan de transition

Apres validation utilisateur de cette spec, le plan d'implementation devra :

1. ajouter `Phase6MaterialFamiliesEvidence` dans
   `:integration-tests:skia-evidence` ;
2. ajouter la tache Gradle
   `generateGpuPhase6MaterialFamiliesEvidence` ;
3. ajouter les tests de classification Material ;
4. generer les artifacts `evidence.json`, `classification.csv` et Markdown ;
5. lancer les tests evidence et `git diff --check` ;
6. documenter explicitement tout echec global hors scope.
