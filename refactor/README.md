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
- [W5e — autorité commune des images décodées](specs/2026-09-12-w5e-decoded-images-design.md)

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
- [Plan W5b — final blends communs](plans/2026-09-10-w5b-final-blends-implementation-plan.md)
- [Plan W5e — images décodées, neuf tâches séquentielles](plans/2026-09-12-w5e-decoded-images-implementation-plan.md)
- [Plan W5f — color filters et interpolation, huit tâches séquentielles](plans/2026-09-14-w5f-color-filters-implementation-plan.md) — Task1–8 closes sur leurs tranches fonctionnelles bornées. Task8 corrigée en 85d8c2d16 : les cinq Important sont traités, l'unique re-review Sol approuve conformité et qualité sans nouveau finding. Le lot final forcé06 compte exactement690 méthodes publiques fraîches :688PASS,0failure/error,2skipsAA4 connus ; les six contrôles de budget passent après l'historique W5a–e/couleur. Commande FAILED15m03s/Gradle1/native174exit133, pas de GREEN natif. Cinq compilations séparées incrémentales exit0 (targets UP-TO-DATE, pas clean) ; ROOT vérifie les1225 sources réelles index/commit et leur identité avec le code testé. Copies image originales prébudget, coûtsproof/quarantaine et321warningsKotlin/8JVM restent suivis. Les essais antérieurs échoués ne sont pas effacés ni déclarés antérieurs sans preuve. Admission générale filteredW4e complexclip/inverse, cache-hit/handles et close/rollback target-level ne sont pas prouvés par les pixels. Review globale et PR empilée restent ouvertes ; domaines restreints explicites dans le status, pas de claim ISO global.

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
- [État W05 — Solid/Opacity, final blends, gradients et images décodées](waves/W05-material-graph/status.md)
  — W5e implémente DrawImage/Nine/Lattice/Atlas et ImageShader Rect/Path fill sous
  une autorité MaterialV3, avec sampling/stride/provenance de snapshot préservés
  par capture/Picture10, et cache device borné. Les neuf tâches et leurs reviews
  sont closes. La revue globale a produit deux Important et deux Minor, tous
  corrigés dans une vague Astra puis approuvés par l'unique re-review Sol, sans
  nouvelle régression identifiée. Sampling/stride legacy, copies non uniformes
  RGBA/BGRA et garde schema4 du tag Nine sont fermés.
  La régression forcée après correctifs du 13 septembre 2026 compte 262 méthodes,
  260 passées, deux skips AA4, aucune failure/error d'assertion;
  Gradle exit1/worker142 exit133, `BUILD FAILED`6m11s, compilation indépendante
  séparée exit0. Contrôle indépendant final28/28 assertions passées,
  Gradle1/worker143exit133. La PR stackée demandée reste en brouillon, basée sur
  W5d #2398, et non prête à fusionner. Les cinq commandes GM décodés ont été
  tentées: quatre failures de rendu (producteurs/AA/legacy), une initialization
  failure (`alpha_image` absent du registre), aucun score produit ni preuve ISO.
  Font/codec exclus. Les réserves numériques Atlas, clip Picture générique,
  close/rollback target-level et teardown natif restent documentées, ainsi que la
  convention de payload exact et les copies prévalidation du legacy non admis.
  L'ajout actualSceneTarget ferme la collision equal-extent antérieure.
  L'historique suivant décrit W5d avant cette tranche W5e.
  — W5d ferme Linear/Radial/Sweep/Conical SRGB et les quatre tile modes avec
  `WithLocalMatrix`, `CoordClamp`, coordonnées ordonnées et moyenne dégénérée,
  sur Rect/RRect analytique/Path fill/stroke. Task 7 prouve frame mixte, ordre,
  capture immuable et budgets des allocations uniques par l'autorité Raw commune.
  Le correctif Task 8 préserve les valeurs invalides de toutes les feuilles admises,
  scelle requested/effective Sweep full-coverage et transporte MaterialV2 sur General
  hard-edge/AA4 sans nouvel owner. Les pixels General direct/stencil et destination-read
  sont prouvés; le budget public distingue les demandes Sweep et garde le refus Raw
  précis sur General, avec contrôles sans wrappers et récupération.
  La sélection conjointe forcée du 12 septembre 2026 W5d/W5c/W5b/W5a compte 166 méthodes :
  164 passées, 0 failure/error XML, deux skips AA4 authentiques
  `w4d.general.texture-sample-support-unavailable` (W5d 40/41, W5c 27/27,
  W5b 50/50, W5a 47/48). Gradle test exit 1 après les assertions, worker 95 exit 133,
  `BUILD FAILED`; compilation séparée exit 0. Les RED publics et les essais refusés
  restent distingués dans le rapport de correction. L'auto-review ne remplace pas
  la re-review globale indépendante.
  Le gap target-ID equal-extent et l'intervalle Conical B-cross-zero conservateur
  étaient explicites à cette clôture; la collision equal-extent est corrigée en W5e.
  AA4 positif n'est pas exercé. `ClampF32` non émis est retiré avec son arm de rejet.
  W5f/W5g/W5h et les autres familles H restent ouverts.
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
| W4 | Geometry/coverage | W4a ScalarAA Rect, W4b RRect analytique, W4c fills hard-edge et W4d.1 strokes/hairlines hard-edge sont atteints. W4d.2 ajoute les transforms F64 `Identity`/`AxisAlignedAffine`/`GeneralAffine`/`Perspective`, le graph AA4/resolve scellé et la lane hard générale prouvée byte-exact à travers `Surface`. W4e fournit hard mask 1×, inverse/D24S8 et oracle/matrice `Surface`; Task 9-fix1 clôt les 18 deltas frais, Task 9-fix2 élimine le fallback d'usages couleur implicite, et le correctif final post-revue couvre les consumers Rect/RRect/Path, les entrées/copies bornées et les buffers V/I/U scellés. `final-fix3` conserve ses preuves publiques de mutation/ordre; la pré-publication `.from` reste un constat statique, sans conclusion pixel sur l'identité du pool. La baseline globale historique reste 51 failures, 0 error et 2 skips, sans nouveau run global W5b. Les 45 DrawPoint sont désormais fermés par le gate public W5b; restent AA4 et `TopologyLimit` conservative F64→F32. Font/codec, GM/dashboard/baseline et `jpg-color-cube` exclus ([status](waves/W04-geometry-coverage/status.md)) |
| W5 | Material graph, blends, gradients et images | W5a–W5e closes sur leurs périmètres fonctionnels. Les neuf tâches W5e sous MaterialV3, la review globale, sa vague de quatre corrections et l'unique re-review Sol sont closes. Régression forcée13 septembre après fix:262 méthodes,260 passées,2skipsAA4,0failure/error d'assertion; Gradle1/worker142exit133, compilation indépendante0. Final indépendant28/28 assertions passées, Gradle1/worker143exit133. PR W5e brouillon basée sur W5d #2398, non prête à fusionner. W5f : huit tâches fonctionnelles bornées closes et reviews Sol approuvées ; final forcé06 690méthodes/688PASS/2skipsAA4/0failure/error, commande FAILED/native174exit133, cinq compiles séparées incrémentales0 et custody réelle1225sources vérifiée. Review globale et PR W5f encore ouvertes. GM ciblés W5e:4failures de rendu et1défaut de registre, pas de score ISO; gaps producteurs/legacy et domaines Atlas conservateurs explicites. Equal-extent corrigé; Conical B-cross-zero/AA4/clip Picture/close/rollback target-level/teardown restent réservés. W5g/W5h/H ouverts ([status](waves/W05-material-graph/status.md)) |
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
