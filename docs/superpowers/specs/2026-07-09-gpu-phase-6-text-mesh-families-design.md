# Design: phase 6 familles Text + Mesh

## Objectif

Continuer la phase 6 de `refactor/05-roadmap.md` apres les vagues `IMAGE`,
`PATH + CLIP`, `GRADIENT + RUNTIME_EFFECT + COLOR`, et
`COMPOSITE + BLUR` avec le dernier groupe d'inventaire large :
`RenderFamily.TEXT` et `RenderFamily.MESH`.

Le but est de fermer la couverture famille Phase 6 avec une evidence lisible,
pas de declarer un support large du texte, du shaping, des glyph caches, des
emoji/color fonts, des meshes arbitraires, des vertices textures, ou des
custom mesh effects. La vague doit inventorier les rows du dashboard,
distinguer les supports deja instrumentes des refus stables, separer les
`no-score` des vrais fails, documenter les deltas depuis le dashboard de
depart, et produire des follow-ups par root cause.

Ces deux familles sont regroupees parce qu'elles sont les principales familles
restantes non couvertes par une evidence Phase 6 dediee :

- `TEXT` exerce la frontiere font/glyph/shaping/atlas/paint ;
- `MESH` exerce la frontiere geometry buffers/vertices/custom mesh/material
  inputs.

`IMAGE`, `PATH`, `CLIP`, `GRADIENT`, `RUNTIME_EFFECT`, `COLOR`, `COMPOSITE`
et `BLUR` restent hors scope sauf quand une row `TEXT` ou `MESH` les exerce
comme dependance. Dans ce cas la classification doit refuser la dependance avec
un reason code stable au lieu de l'absorber silencieusement.

## Sources et contraintes

Sources actives :

- `refactor/05-roadmap.md`, phase 6 ;
- `.upstream/target/skia-like-realtime-renderer-target.md` ;
- `.upstream/specs/skia-like-realtime/README.md` ;
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md` ;
- `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md` ;
- `.upstream/target/high-performance-wgsl-pipeline-target.md` ;
- `.upstream/specs/wgsl-pipeline/README.md` ;
- `docs/superpowers/specs/2026-07-08-gpu-phase-6-image-family-design.md` ;
- `docs/superpowers/specs/2026-07-09-gpu-phase-6-coverage-families-design.md` ;
- `docs/superpowers/specs/2026-07-09-gpu-phase-6-material-families-design.md` ;
- `docs/superpowers/specs/2026-07-09-gpu-phase-6-effect-composition-families-design.md` ;
- `reports/gpu-renderer/phase-6-image-family/evidence.json` ;
- `reports/gpu-renderer/phase-6-coverage-families/evidence.json` ;
- `reports/gpu-renderer/phase-6-material-families/evidence.json` ;
- `reports/gpu-renderer/phase-6-effect-composition-families/evidence.json` ;
- `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

Contexte observe avant cette spec :

- le dashboard courant liste 77 rows `TEXT` et 16 rows `MESH` ;
- les familles deja traitees couvrent les autres familles dashboard visibles :
  `IMAGE`, `PATH`, `CLIP`, `GRADIENT`, `RUNTIME_EFFECT`, `COLOR`,
  `COMPOSITE` et `BLUR` ;
- `:integration-tests:skia-evidence` contient deja les patterns de lecture
  dashboard, classification, writer JSON/CSV/Markdown, preservation de section
  `Validation`, stable row ids, non-claims et follow-up candidates ;
- la branche demarre depuis `origin/master` apres merge de la vague
  `COMPOSITE + BLUR`.

Contraintes :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire SkSL, son IR ou sa VM ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible shader ;
- ne pas baisser les seuils globaux de similarite ;
- ne pas masquer les `no-score` ;
- ne pas promouvoir une row sans reference, generated render, diff/stat,
  diagnostics de route text/mesh, fallback policy explicite et non-claim ;
- ne pas annoncer de support large shaping, font fallback, color emoji,
  color font, glyph atlas, custom mesh, vertices texture, perspective mesh ou
  mesh effects ;
- garder les corrections renderer lourdes dans des PRs separees si elles ne
  sont pas strictement necessaires a l'evidence.

## Choix retenu

Nous retenons l'approche **Text + Mesh evidence closure** :
`TEXT + MESH` en inventaire large, avec compteurs separes par famille et
sous-famille.

Cette approche est preferable a une vague `TEXT` seule parce qu'elle ferme
l'inventaire famille Phase 6 en une seule tranche sans melanger les families
deja traitees. Elle est moins risquee qu'une vague "visual fixes" parce qu'elle
ne modifie pas le renderer par defaut et ne promet pas de support large.

La vague peut signaler des candidats de fixes futurs, mais elle reste une vague
d'evidence. Les rows qui passent visuellement sans diagnostics suffisants
restent `instrumented-existing`. Les rows hors scope ou dependency-gated
deviennent `expected-unsupported` ou `no-score` avec reason code stable.

## Architecture

L'orchestration d'evidence reste dans `:integration-tests:skia-evidence`.
Elle suit le modele des vagues precedentes.

Flux cible :

```text
Skia GM dashboard regenere
  -> rows TEXT / MESH
  -> text-mesh-family classifier
  -> family/subfamily/classification counters
  -> stable refusal taxonomy
  -> reports/gpu-renderer/phase-6-text-mesh-families/*
```

Responsabilites :

- `integration-tests/skia` reste proprietaire du dashboard GM, des references,
  des PNG generated/diff et des taches de regeneration.
- `integration-tests/skia-evidence` porte la lecture du dashboard, la
  classification, l'export JSON/CSV/Markdown et la tache Gradle
  `generateGpuPhase6TextMeshFamiliesEvidence`.
- `font/*` et `kanvas` restent proprietaires des contrats texte, glyph,
  font manager, glyph masks, color fonts et fallback.
- `render-pipeline` et `gpu-renderer` restent proprietaires des diagnostics
  WebGPU, mesh buffers, vertex layouts, PipelineKey, resources et fallbacks.
- Le rapport Phase 6 documente rows, refus, gaps et non-claims ; il ne cree
  pas de support par classification seule.

La vague ne doit pas inventer un moteur de shaping, un glyph atlas large, ou
un pipeline custom mesh general. Si une row depend d'une de ces capacites sans
preuve complete, elle doit rester `expected-unsupported` ou `no-score`.

## Sous-familles

Sous-familles `TEXT` initiales :

```text
text-basic-latin
text-large-or-cache
text-rsxform-gated
text-perspective-or-transform-gated
text-font-manager-gated
text-font-fallback-gated
text-color-font-gated
text-emoji-gated
text-color-palette-gated
text-blob-gated
text-annotation-gated
text-shader-or-gradient-gated
text-filter-or-blur-gated
text-clip-interaction-gated
```

Sous-familles `MESH` initiales :

```text
mesh-basic-vertices
mesh-custom-basic
mesh-custom-uniforms-gated
mesh-color-space-gated
mesh-effect-dependency-gated
mesh-image-dependency-gated
mesh-paint-color-dependency-gated
mesh-paint-image-dependency-gated
mesh-perspective-gated
mesh-update-or-dynamic-gated
mesh-zero-init-gated
mesh-picture-dependency-gated
```

Ces sous-familles peuvent etre ajustees pendant l'execution si le dashboard
regenere revele des rows nouvelles, mais tout changement doit rester explicite
dans les tests et le rapport.

## Classification

Les categories suivent les vagues precedentes :

- `promoted-support` : support revendique avec reference, generated PNG,
  diff/stat, score au-dessus du seuil, diagnostics de route text/mesh et
  fallback `none`.
- `instrumented-existing` : row qui rend ou compare utilement, mais sans
  preuve suffisante pour une promotion Phase 6.
- `expected-unsupported` : row hors scope avec refus stable.
- `no-score` : row sans score exploitable, reference manquante, render absent,
  size mismatch ou evidence incomplete.
- `unexpected-fail` : fail qui n'a pas de refus stable ou d'explication
  attendue.

Une row ne peut pas etre promue depuis son nom, sa famille, ou son score seul.
Elle doit prouver la route effectivement utilisee. Par exemple, un text GM qui
compare bien mais qui ne prouve pas le font selection, glyph mask, atlas ou
fallback reste `instrumented-existing`. Un mesh GM qui compare bien mais depend
d'une image, d'un effect ou d'un custom uniform non modelise reste gated.

Reason codes representatifs :

```text
unsupported.text.shaping
unsupported.text.font_manager
unsupported.text.font_fallback
unsupported.text.glyph_cache
unsupported.text.color_font
unsupported.text.emoji
unsupported.text.palette
unsupported.text.rsxform
unsupported.text.perspective
unsupported.text.shader_or_gradient
unsupported.text.filter_or_blur
unsupported.text.clip_interaction
unsupported.mesh.custom_uniforms
unsupported.mesh.color_space
unsupported.mesh.effect_dependency
unsupported.mesh.image_dependency
unsupported.mesh.paint_color_dependency
unsupported.mesh.paint_image_dependency
unsupported.mesh.perspective
unsupported.mesh.dynamic_updates
unsupported.mesh.zero_init
unsupported.mesh.picture_dependency
```

Les reason codes finaux doivent etre dedoublonnes avec les diagnostics deja
existants dans `font`, `render-pipeline` et `gpu-renderer` avant d'etre
commits.

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

- `reports/gpu-renderer/phase-6-text-mesh-families/evidence.json` ;
- `reports/gpu-renderer/phase-6-text-mesh-families/classification.csv` ;
- `reports/gpu-renderer/YYYY-MM-DD-gpu-phase-6-text-mesh-families.md`.

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
- follow-up candidates par root cause.

Le Markdown doit inclure :

- resume lisible pour review ;
- deltas `TEXT` et `MESH` ;
- taxonomy des reason codes ;
- follow-up candidates ;
- table de rows avec `rowId`, row, family, subfamily, classification,
  similarity, fallback et no-score cause ;
- section `Validation` preservee si le fichier existe deja.

## Non-claims

Le rapport doit dire explicitement :

- aucune promesse de support large `TEXT` ou `MESH` n'est faite par la
  classification seule ;
- shaping, font fallback, glyph cache, color fonts, emoji, palettes,
  transformed text, text filters et clip/text interactions restent hors scope
  sans diagnostics complets ;
- custom mesh, dynamic mesh updates, perspective mesh, picture mesh, image
  dependencies, paint-image dependencies, mesh effects et arbitrary vertices
  restent hors scope sans diagnostics complets ;
- les familles deja traitees ne sont pas reabsorbees dans cette vague.

## Tests

Les tests doivent couvrir :

- filtrage strict `TEXT + MESH` ;
- sous-familles `TEXT` principales ;
- sous-familles `MESH` principales ;
- stable row ids pour noms dupliques ;
- no-score separe d'un fail ;
- passing gated rows restent `expected-unsupported` quand la dependance est
  hors scope ;
- JSON contient diff stats, no-score cause et follow-up candidates ;
- Markdown expose `promotedRows`, `unexpectedFails`, `noScore` ;
- writer preserve la section `Validation` ;
- non-claims ne contiennent pas de claim de support complet.

## Taches attendues

1. Ajouter des tests RED `Phase6TextMeshFamilyEvidenceTest`.
2. Ajouter le classifier/model/writer `Phase6TextMeshFamilyEvidence`.
3. Ajouter la CLI et la tache Gradle
   `generateGpuPhase6TextMeshFamiliesEvidence`.
4. Regenerer les rapports JSON/CSV/Markdown sans committer les PNG generated.
5. Lancer une review independante avant PR.

## Criteres d'acceptation

- Le module `:integration-tests:skia-evidence:test` passe.
- La generation `:integration-tests:skia-evidence:generateGpuPhase6TextMeshFamiliesEvidence`
  ecrit les trois rapports attendus.
- Les compteurs de famille de depart sont visibles : `TEXT=77`, `MESH=16`.
- `promotedRows` reste `0` tant qu'aucune route text/mesh complete n'est
  prouvee.
- `unexpectedFails` est visible et doit etre `0` ou justifie par follow-up
  avant PR.
- Le diff ne contient pas de PNG generated ni d'artefact `.superpowers/sdd`.
- Le rapport ne claim pas de support large texte, glyph, font, emoji, mesh,
  custom mesh ou vertices.

