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

W5g est clos sur son périmètre fonctionnel borné et publié en [Draft PR #2401](https://github.com/ygdrasil-io/kanvas/pull/2401), empilée sur W5f #2400. La source finale `c28615222` donne 921 cas publics : 919 PASS, 2 skips AA4 historiques et 0 failure/error public ; cinq compilations séparées terminent exit0. Gradle reste exit1 avec l'Executor258 natif exit133 de cause `UNKNOWN`, sans claim native green ni ISO global. L'unique Important de review — double accounting/lease du même owner image entre W5e ordinaire et V5 — est `ADDRESSED` par un inventaire frame-wide owner-aware et un test public causal ; la re-review Sol conclut `COMPLIANT`/`APPROVED`, C0/I0/M0. Les gaps `Picture.playback(Canvas)`/`SetClip` et composite/complex-clip restent inchangés et suivis.

Historique du checkpoint W5g/Task4 : le lot Noise `9a14bdf96` et sa correction bornée `b63f4ceb3` sont committés avec 62 chemins Task4 et 5 témoins vérifiés byte à byte. Le `final4` figé obtient 197/197 PASS publics (57 Noise Surface, 10 Noise Picture, 125 Blend Surface, 5 Blend Picture), sans failure/error/skip. Gradle reste exit1 avec l'Executor242 natif exit133 de cause `UNKNOWN` : ce résultat n'est pas requalifié en native green. La re-review Sol confirme les deux findings `ADDRESSED` — reader de clips schema 5 et identité typée commune FLOOR/fraction/adresses — avec 0 Critical/Important/Minor et `Ready to proceed: Yes`. Deux gaps Picture préexistants restent trackés hors Task4 : `Picture.playback(Canvas)` perd `SetClip`, et le composite refuse encore les clips complexes. À ce checkpoint, Task5/convergence restait la prochaine étape et W5g global était encore ouvert.

Checkpoint W5g/Task4 : l'epoch 6 ciblée obtient 13/22 PASS ; les six domaines Noise 2/8/255, les trois routes d'accounting, les trois mixes Noise/gradient/image et le budget public 32 KB passent. Les neuf échecs restants sont séparés en sept refus numériques de filtre, un mauvais clip AA de fixture sur les lanes mixtes et une attente de diagnostic Stroke située après la vraie frontière publique. R37 aligne uniquement cette attente sur `unsupported.material.composed.slice`; R38 rend uniquement le clip de la fixture explicitement hard-edge. Aucun owner production, promotion geometry/AA, test d'infrastructure ou changement de priorité n'est ajouté. Gradle exit1 / native exit133 reste de cause `UNKNOWN`.

R39 autorise uniquement deux faits corrélés dans la preuve Noise existante : le même `f` dans `f*f*(3-2*f)` et le même `a` dans `a+(b-a)*t`. Les opérations originales sont d'abord validées, puis leur enveloppe ordinaire est intersectée avec des extrema dirigés et une erreur explicite gamma/DAZ/FTZ dans un nouveau contexte d'identité immuable ; une intersection vide refuse. Aucun WGSL, opcode, clamp, epsilon, alpha supposé, domaine, tail d'octave ou tolérance ne change. Risque : sous-estimer l'amplification ou confondre l'identité d'un scalaire ; final4 et la review Sol restent obligatoires. Scope inchangé.

Epoch 7 ciblée : 8/9 PASS en 27 s ; compositions6/6, Stroke/recovery et mixed Rect/direct/stencil passent. Le seul refus est la mutation Picture Fractal8, dont le domaine 1x1 traverse plusieurs floors et conserve légitimement alpha=0 avant Matrix unpremultiply. R40 ramène uniquement ce child incident à Fractal2, en gardant matrice mutable, deux familles/seeds, stitch, capture, replay et roundtrip ; octave8/255 reste couvert indépendamment sur les deux géométries et en stitch. Aucun guard, preuve, production ou tolérance ne change. Native133 reste `UNKNOWN`.

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
- [Plan W5f — color filters et interpolation, huit tâches séquentielles](plans/2026-09-14-w5f-color-filters-implementation-plan.md) — Task1–8 closes sur leurs tranches fonctionnelles bornées. Task8 corrigée en 85d8c2d16 : les cinq Important sont traités, l'unique re-review Sol approuve conformité et qualité sans nouveau finding. Le lot final forcé06 compte exactement690 méthodes publiques fraîches :688PASS,0failure/error,2skipsAA4 connus ; les six contrôles de budget passent après l'historique W5a–e/couleur. Commande FAILED15m03s/Gradle1/native174exit133, pas de GREEN natif. Cinq compilations séparées incrémentales exit0 (targets UP-TO-DATE, pas clean) ; ROOT vérifie les1225 sources réelles index/commit et leur identité avec le code testé. Copies image originales prébudget, coûtsproof/quarantaine et321warningsKotlin/8JVM restent suivis. Les essais antérieurs échoués ne sont pas effacés ni déclarés antérieurs sans preuve. Admission générale filteredW4e complexclip/inverse, cache-hit/handles et close/rollback target-level ne sont pas prouvés par les pixels. Review globale Sol approuvée sur7a06459dd→f743a222a : conformité COMPLIANT/qualité APPROVED,0Critical/0Important/2Minor hérités déjà suivis,0nouveau finding actionnable. Aucun fixwave supplémentaire ; Ready to merge:NO. [PR W5f #2400](https://github.com/ygdrasil-io/kanvas/pull/2400) publiée en Draft, empilée sur W5e #2399 ; suivi et archivage récupérable des seules notes SDD W5f terminés ; domaines restreints explicites dans le status, pas de claim ISO global.

- [Plan W5g — Blend partagé, NoiseV1 et convergence](plans/2026-09-14-w5g-composed-procedural-materials-implementation-plan.md) — Tasks1–5 closes sur le périmètre borné : Blend ordonné avec gradients/images, NoiseV1 Perlin/Fractal jusqu'à 255 octaves demandées, compatibilité Picture8/9/10/11, budgets work/storage et récupération frame-wide. Final corrigé : 921 cas publics, 919 PASS, 2 skips AA4 historiques, 0 failure/error public ; cinq compiles séparées exit0. Review globale puis fix owner-aware : `ADDRESSED`, `COMPLIANT`/`APPROVED`, C0/I0/M0. [Draft PR #2401](https://github.com/ygdrasil-io/kanvas/pull/2401) sur W5f #2400 ; native133 `UNKNOWN`, pas de claim ISO/Ready-to-merge.

R36 conserve le graph/WGSL et précise la preuve des mêmes opérations Noise : phase corrélée sur FLOOR singleton, adresses floor+corner/période/permutation exactes vers le slab authentifié, fallback all256. Floor BigInteger négatif vers −∞ ; aucun epsilon, alpha supposé ou échantillonnage. Les pixels négatifs/transforms/stitch/seeds/Matrix/mutation, final4 et review Sol couvrent le risque d’over-admission. Scope inchangé39 Modify/5 Kotlin Create/21 assets/2 producteurs éphémères.

R35 raccorde le preflight W3 exact au même slab Noise authentifié : l’allocation R34 est vérifiée une fois, sans preparation supplémentaire, et les guards geometry/clip/blend/readback/Gradient restent inchangés. Risque : refus direct/composite, slab contrefait ou double charge. Scope Task4 : 39 Modify, 5 Kotlin Create, 21 assets, 2 producteurs historiques éphémères ; aucun test d’infrastructure/harness.

R34 raccorde le même slab Noise authentifié à cinq owners de lowering/custody W3/W5b/composite : un seul label physique, un seul peak/allocation, validation packet exacte et aucune charge par lane. Les comptes geometry et les gates AA/clip/capability restent inchangés. Le risque de refus direct-W3, sous-comptage ou duplication demeure couvert par les preuves publiques et la review Sol ; aucun test d'infrastructure ni changement de harness. Scope Task4 : 38 Modify, 5 Kotlin Create, 21 assets et 2 producteurs historiques éphémères.

### État et rapports finaux

Noise4 : dérivation255 corrigée validée MAIN (R28), phase F32 originale,
lattice/périodes128bits et accumulation255 conservée. Bilerp suit le schedule
Skia a+(b-a)*t ; certificat floor/fract inclut le cas subnormal/DAZ, sans
élargir les gardes globales. Coût : arithmetic entière/preuve plus complexes,
risque de mauvais carry/corrélation ou refus conservateur ; domaines extrêmes
non prouvés restent explicitement refusés, pas une clôture générale Noise.
Vrai RED public vérifié ROOT :12cas/12refusproduction, aucun échec oracle,
production inchangée et XML/SHA exacts. Même Astra peut maintenant implémenter.
R29 classe la capture publique temporaire des vrais writers8/9/10 et21assets
Noise ciblés ; aucun changement du harnais natif. Coût : mauvaise provenance
si le classpath réutilisait un writer actuel ; commits/inputs/bytes documentés,
rejets historiques conservés, replay public obligatoire avant compatibilité.
R30 autorise8signatures/1forwarding dans un test existant, aucune assertion
ajoutée ni exécution infrastructure. Coût : compilation ou snapshot perdu ;
transport exact du budget et vrais tests publics Noise/recovery obligatoires.
R31 autorise uniquement le rôle NoiseTableData distinct, ajouté sans changer
les rôles/guards existants. Coût : nouveau consommateur exhaustif ou ressource
mal authentifiée ; toute adaptation supplémentaire est classifiée avant edit,
inventaire complet/preuve/native et tests publics restent obligatoires.
R33 raccorde uniquement le même slab Noise authentifié au RenderGraph et à ses
inventaires de paths : une charge physique complète, mêmes guards/lifetimes,
aucune promotion AA4/H. Coût : ressource contrefaite/omise, double comptage ou
régression de géométrie ; inventaire, budgets/recovery publics et review Sol requis.
Les15fixtures réellement capturées8/9/10 ont une provenance vérifiée ROOT ;
NaN/infini sont refusés par les trois writers. R32 autorise les sixassets
manquants comme constructions volontairement invalides du vieux wire format,
jamais captures writer : quatre widths F32 seulement, headers/versions conservés.
Coût : mauvais offset/provenance pouvant masquer un rejet ; bytes/SHA vérifiés
et Picture.fromByteArray doit refuser publiquement. Positifs8/9/10/new11 restent
obligatoires ; aucune clôture statique de compatibilité.
Convergence5 et les gates
finales suivront séquentiellement ; aucune clôture W5g globale anticipée.

W5g continue entièrement : Task3 images/Blend sur Astra/high, puis Noise4,
convergence5, review globale Sol et une Draft empilée sur W5f#2400 inchangée.
Task3 acceptée : commitfix48b0bb4f4 vérifié, re-review ciblée même Sol
COMPLIANT/APPROVED, I1 ADDRESSED,0Critical/Important/0nouveau Minor.
La perte des alternatives alpha0/1 est identifiée dans les conditions de preuve.
R27 autorise leur conservation uniquement, sans changer arithmetic ni fixture.
Coût : contextes supplémentaires et risque de corrélation ; positif original,
final5 figé et re-review même Sol obligatoires avant acceptation/Noise.
Le positif original passe désormais (Epoch16), Rect/direct/stencil twice,
fixture inchangée ; commande FAILED/native223exit133 UNKNOWN. Final5 amendé17 :
529uniques529PASS0failure/error/skip, mêmes529cas,5XML réels et18SHA testés/
committés vérifiés ROOT. FAILED18m8s/native224exit133 UNKNOWN ; acceptance
Task3/Blend borné close après ce verdict ; Noise4 dérivation255 puis
implémentation, convergence5 et gates finales s'enchaînent sans pause utilisateur.
Tasks1–2 restent acceptées. Nearest/linear, frame mixte, domaines image et
rétention Picture passent sur les epochs ciblés ; SRC_IN/DIFFERENCE discriminent
les cinq ordres. R25 retire seulement le cross-product exhaustif ajouté ROOT :
SRC_OVER conserve un vrai positif et un contre-exemple borné, sans réduire le
contrat validé ni effacer les échecs facultatifs R22/R23.

Epoch12 :4PASS/0failure-error-skip pour les16topologies cubic et le budget
agrégé512/3MB corrigé. Commande FAILED32s/native219exit133 UNKNOWN.
R20 conserve le refus cubic à alpha variable/recovery ; DECAL positif est
intérieur opaque,16taps réels, pas une admission générale aux bords transparents.
R21 compte source +snapshot final +coverage effectivement lié avant préparation,
avec un diagnostic Binding V5 distinct de la base legacy.
Réservation pessimiste par capture immuable distincte, cache content-addressed
inchangé : des contenus égaux peuvent réserver plus que leur résidence minimale.
Historique Epoch14 : même contexte V5 invalide sans filter passe3routes twice,
avec restoring filter refuse NumericDomainUnbounded ; transparence encore OPEN.
2tests/1PASS/1FAIL, FAILED8s/native221exit133 UNKNOWN, preuve réelle vérifiée ROOT.
Historique final5 Epoch15 :529méthodes uniques/528PASS/1FAIL/0ERROR/0SKIP,
FAILED17m47/Gradle1/native222exit133 causeUNKNOWN. Le défaut projectif reste OPEN.
ROOT a vérifié le rapport intégral5190EOF,5XML réels/SHA et18SHA source identiques
avant/après ; commit provisoire18paths autorisé pour la review Sol Task3 (R26),
sans acceptation Blend ni lancement Noise avant résolution des assertions requises.
Coûts/limites/échecs conservés au plan/status ; Noise255/archives/storage causal
et gates finales obligatoires, aucun nativeGREEN, ISO, push/PR W5g ou clôture globale.

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
| W5 | Material graph, blends, gradients et images | W5a–W5g closes sur leurs périmètres fonctionnels bornés. W5f conserve 688PASS/2skipsAA4 et sa [Draft #2400](https://github.com/ygdrasil-io/kanvas/pull/2400). W5g livre Blend partagé, gradients/images, NoiseV1 et convergence : final921 cas/919PASS/2skipsAA4/0failure-error public, cinq compiles exit0, review finale `COMPLIANT`/`APPROVED` C0/I0/M0 après correction owner-aware ; [Draft #2401](https://github.com/ygdrasil-io/kanvas/pull/2401) empilée sur W5f. Les exits natifs133 restent `UNKNOWN`, sans score ISO ni Ready-to-merge. Conical B-cross-zero, AA4, clips Picture complexes, close/rollback target-level et teardown restent réservés ; W5h/H restent ouverts ([status](waves/W05-material-graph/status.md)) |
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
