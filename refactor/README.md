# Refactor du renderer Skia

Ce dossier centralise les documents humains de pilotage de la remédiation
architecturale du renderer. Les artefacts techniques générés — captures PNG,
diffs, métriques, manifests et résultats JSON — restent dans leurs répertoires
de preuve existants.

## Objectif

Atteindre une compatibilité Skia quasi isopixel hors `font` et `codec`, avec :

- 100 % des GMs éligibles exécutées ;
- au moins 95 % des GMs éligibles conformes à leur politique pixel ;
- zéro refus terminal non classifié ;
- zéro fallback CPU silencieux ;
- une liste fermée et documentée des écarts Skia acceptés.

## Documents autoritaires

### Spécifications

- [Spec architecturale](specs/2026-08-29-skia-renderer-remediation-design.md)
- [Moteur topologique robuste des paths dans `:math`](specs/2026-08-30-math-path-topology-engine-design.md)
- [Arrangement hybride F64/F32 pour les opérations de paths](specs/2026-08-31-hybrid-f64-f32-path-topology-design.md)
- [Admission conservative de la topologie hybride](specs/2026-09-01-conservative-hybrid-topology-admission-design.md)
- [Stack W4 geometry/coverage et tranche W4a ScalarAA Rect](specs/2026-09-03-w4-geometry-coverage-stack-design.md)
- [Tranche W4b — RRect analytique normalisée](specs/2026-09-04-w4b-analytic-rrect-design.md)
- [Tranche W4c — fills de paths par tessellation/stencil](specs/2026-09-05-w4c-path-fills-design.md)
- [Clôture W4d–W4e — strokes, transforms/AA et clips complexes](specs/2026-09-06-w4-remaining-geometry-coverage-design.md)
- [Material graph W5 — Solid/Opacity](specs/2026-09-09-w5-material-graph-design.md)

### Plans

- [Plan d'implémentation W0–W2](plans/2026-08-29-w00-w02-foundation-implementation-plan.md)
- [Plan du moteur topologique robuste](plans/2026-08-30-math-path-topology-engine-implementation-plan.md)
- [Plan de topologie hybride F64/F32](plans/2026-08-31-hybrid-f64-f32-path-topology-implementation-plan.md)
- [Plan d'admission conservative](plans/2026-09-01-conservative-hybrid-topology-admission-implementation-plan.md)
- [Plan W4a — rectangles fractionnaires ScalarAA](plans/2026-09-03-w4a-scalar-aa-rect-implementation-plan.md)
- [Plan W4b — RRect analytique normalisée](plans/2026-09-04-w4b-analytic-rrect-implementation-plan.md)
- [Plan W4c — fills de paths hard-edge](plans/2026-09-06-w4c-path-fills-implementation-plan.md)
- [Plan W4d.1 — strokes et hairlines hard-edge](plans/2026-09-06-w4d-strokes-hairlines-implementation-plan.md)
- [Plan W4d.2 — transforms généraux et path AA](plans/2026-09-06-w4d-general-transform-aa-implementation-plan.md)
- [Plan W4e — clips complexes et inverse paths](plans/2026-09-06-w4e-complex-clips-implementation-plan.md)
- [Plan W5a — Solid/Opacity](plans/2026-09-09-w5a-solid-opacity-implementation-plan.md)

### État et rapports finaux

- [Baseline de vérité W00](waves/W00-truth-baseline/status.md) — gate stricte
  non atteinte en raison de la quarantaine temporaire `jpg-color-cube`.
- [État W01 — géométrie immuable et format `Picture` v8](waves/W01-immutable-geometry/status.md)
  — preuve d'immuabilité, compatibilité de lecture v7 et writer v8 stable.
- [État W02 — Scene IR et frontières de modules](waves/W02-scene-ir/status.md)
  — capture backend-neutral publiée ; rendu public encore legacy et gates strictes non atteintes.
- [État W03 — `gpu-plan` et première tranche compositionnelle](waves/W03-gpu-plan/status.md)
  — preuve pixel exacte publiée ; baseline globale W0–W2 préservée.
- [État W04 — geometry/coverage](waves/W04-geometry-coverage/status.md)
  — W4a ScalarAA Rect, W4b RRect analytique, W4c fills de paths hard-edge et
  W4d.1 strokes/hairlines hard-edge sont atteints. W4d.2 ajoute les transforms
  généraux et l'architecture path AA4/resolve, avec rendu public exact pour la
  lane hard générale. W4e apporte des preuves positives hard-mask 1×,
  inverse/D24S8 et oracle/matrice `Surface`; AA4 positif reste honnêtement
  skipped faute de topologie native complète. Task 9-fix1 clôt les 18 deltas
  d'intégration W4e et Task 9-fix2 publie les usages couleur 1× strictement
  observés, sans les confondre avec les 51 failures historiques. Le correctif
  final post-revue étend l'admission aux consumers Rect/RRect/Path et à
  l'inverse `STROKE_AND_FILL`, rétablit les budgets d'entrées/copies et scelle
  les buffers V/I/U W4e dans le graphe. `final-fix2` conserve aussi le scissor
  `DeviceRect` W4d, canonise les paires RRect non positives et réutilise/évince
  transactionnellement les slots physiques. `final-fix3` remplace les preuves
  internes écartées par quatre scénarios publics hard 1× : sibling `DeviceRect`,
  inverse sous CTM non identité avec scissor device-space fixe, cinq frames W4e
  distinctes sur le même `Surface`, et mutation du `Path` appelant après capture.
  La revue statique, et non les pixels black-box, établit que `.from` précède la
  publication du graphe; les pixels établissent seulement le comportement et
  l'isolation observable, sans conclure à une identité d'objet, à une réutilisation
  précise du pool ou à un nombre d'allocations. `final-fix4` exige en outre,
  avant chaque oracle pixel de ces quatre scénarios, les scopes publics
  `Render`/`Readback` du `RenderResult`, pour empêcher qu'un fallback legacy
  satisfasse seulement les pixels. Les deux skips AA4 restent explicites.
- [État consolidé de la topologie hybride](progress/2026-08-31-hybrid-f64-f32-path-topology/progress.md)
- [Rapport d'implémentation de l'admission conservative](progress/2026-09-01-conservative-hybrid-topology-admission/implementation-report.md)
- [Revue de spécification de l'admission conservative](progress/2026-09-01-conservative-hybrid-topology-admission/spec-review.md)
- [Revue qualité de l'admission conservative](progress/2026-09-01-conservative-hybrid-topology-admission/quality-review.md)

## État des vagues

| Vague | Sujet | État |
| --- | --- | --- |
| W0 | Vérité de référence | Baseline publiée ; gate stricte non atteinte |
| W1 | Géométrie immuable dans `:math` | Périmètre fonctionnel implémenté et prouvé ciblé pour les frontières d'enregistrement/Picture : snapshots profonds immuables d'images/effets, copie itérative résistante aux cycles avec limites reportées à `SceneCaptureLimits`, writer `Picture` v8 stable et enregistrement détaché/transactionnel des `RuntimeEffect`. Gate stricte **NON ATTEINTE / bloquée** par la validation globale fraîche de 51 échecs sur 3 585 tests, qui confirme la baseline globale ; topologie source, topologie hybride F64/F32 et admission conservative restent documentées séparément |
| W2 | `Scene IR` et frontières de modules | Capture backend-neutral et frontières de modules implémentées ; gate stricte **NON ATTEINTE** (431/443 captures, 12 dettes), rendu public encore legacy |
| W3 | `gpu-plan` et premier `RenderGraph` | Capability rectangles solides/clip simple/`SrcOver` branchée et prouvée par pixels exacts ; baseline globale conservée (51 échecs connus, 0 erreur) |
| W4 | Geometry/coverage | W4a ScalarAA Rect, W4b RRect analytique, W4c fills hard-edge et W4d.1 strokes/hairlines hard-edge sont atteints. W4d.2 ajoute les transforms F64 `Identity`/`AxisAlignedAffine`/`GeneralAffine`/`Perspective`, le graph AA4/resolve scellé et la lane hard générale prouvée byte-exact à travers `Surface`. W4e fournit hard mask 1×, inverse/D24S8 et oracle/matrice `Surface`; Task 9-fix1 clôt les 18 deltas frais, Task 9-fix2 élimine le fallback d'usages couleur implicite, et le correctif final post-revue couvre les consumers Rect/RRect/Path, les entrées/copies bornées et les buffers V/I/U scellés. `final-fix3` remplace les anciennes assertions internes par des pixels `Surface` publics : sibling `DeviceRect`, inverse à CTM non identité, cinq frames W4e distinctes et mutation après capture. La pré-publication `.from` reste un constat de revue statique, pas une conclusion des tests pixels; ceux-ci ne mesurent ni identité d'objet ni détail du pool. Le baseline global reste exactement 51 failures, 0 error et 2 skips. Restent ouverts : le probe/capacité AA4, `TopologyLimit` conservative F64→F32 et la baseline DrawPoint. Les tests font exception de `font`/`codec`, GM/dashboard/baseline et `jpg-color-cube` ([status](waves/W04-geometry-coverage/status.md)) |
| W5 | Material graph | Correctif global W5a poursuivi à `7dbaf8c` : DAG fragment avec raw bindings/ABI V2, registre material scellé après admission réelle Core/A8/Vertices/Mesh sans programme, diagnostics géométriques préservés. Ordre/capture, refus gradient puis récupération sur la même Surface et bornes 512/513 sur le même runtime/backend vérifiés. Oracle attachment encode/decode officiel; fixtures multi-draw choisies uniquement lorsque l'enveloppe stricte reste à un ou deux codes adjacents, sans borne universelle revendiquée. 120 tests publics : 117 passés, 3 skips AA4 authentiques, aucune failure/error. Les deux re-reviews globales Task 8 restent en attente; pas de clôture implicite. MeshProgram et le gap legacy Rect+hard-RRect `uniform slab` restent hors promotion; W5b ensuite ([status](waves/W05-material-graph/status.md)) |
| W6 | Layers et effets | Non démarrée |
| W7 | Convergence GM | Non démarrée |
| W8 | Retrait legacy et runtime | Non démarrée |

## Organisation

```text
refactor/
├── README.md
├── specs/       # designs et décisions architecturales approuvées
├── plans/       # plans d'implémentation exécutables
├── progress/    # états consolidés et rapports finaux
└── waves/       # états, décisions et écarts par vague active
```
