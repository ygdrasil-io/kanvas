# Inventaire courant des tests GPU evidence

> Document de travail temporaire. Il sert à préparer les lots de test sous
> `wip/` et doit être supprimé lorsque ces lots ont été exécutés et que leurs
> preuves ont été intégrées aux artefacts et au code vérifiés.

État du code fusionné dans `origin/master` au commit
`116ce6ec3547c1d367e4c51881597a351eb285c4`. La capture correctness promue
correspondante provient du SHA source
`226674870ee09e080beee62b8d2935704f4b3331`.

Ce document est un index de lecture : le code Kotlin est la source de vérité,
principalement `GpuEvidenceCatalog.kt` et les classes de test sous
`../integration-tests/gpu-evidence/src/test/kotlin`. Il ne remplace ni les
preuves générées, ni les artefacts promus.

Le module contient 18 cas de catalogue (16 rendus attendus, 2 refus attendus),
28 fichiers/classes de test et 216 annotations littérales `@Test`. L'exécution
rapporte aussi 216 tests / 1 skipped : il n'y a pas de delta à attribuer à la
mécanique de test.

## Cas de catalogue et route réellement exercée

| ID | Route réelle | Attente | Ce qui est testé |
| --- | --- | --- | --- |
| `solid-card-stack` | `KanvasSurfaceProgram` | rendu | Deux rectangles opaques superposés, composés en `SrcOver`. |
| `separable-blur-rect` | `KanvasSurfaceProgram` | rendu | Rectangle avec un masque de blur normal séparable. |
| `translucent-card-overlap` | `KanvasSurfaceProgram` | rendu | Composition linéaire prémultipliée de deux rectangles semi-transparentes. |
| `scissor-overlay` | `KanvasSurfaceProgram` | rendu | Deux séquences `save` / `clipRect` / `restore` qui bornent des rectangles. |
| `stroke-rect-outline` | `KanvasSurfaceProgram` | rendu | Contour de rectangle à largeur fixe, sans anti-aliasing. |
| `linear-gradient-lanes` | `KanvasSurfaceProgram` | rendu | Dégradé linéaire `CLAMP` à deux stops. |
| `radial-swatch` | `KanvasSurfaceProgram` | rendu | Dégradé radial `CLAMP` à deux stops. |
| `sweep-disk` | `KanvasSurfaceProgram` | rendu | Dégradé sweep `CLAMP` sur un tour complet de 360°. |
| `linear-gradient-three-stops` | `KanvasSurfaceProgram` | rendu | Dégradé linéaire `CLAMP` à trois stops, dont un stop médian. |
| `sweep-gradient-partial-angle` | `KanvasSurfaceProgram` | rendu | Dégradé sweep `CLAMP` sur une plage partielle de 45° à 315°. |
| `affine-solid-rect` | `KanvasSurfaceProgram` | rendu | Rectangle opaque sous transformation affine avec cisaillement. |
| `scissored-radial-gradient` | `KanvasSurfaceProgram` | rendu | Dégradé radial `CLAMP` limité par un clip rectangulaire non AA. |
| `repeat-gradient-refusal` | `KanvasSurfaceProgram` | rendu | Rectangle rempli borné, non filtré, à dégradé linéaire sRGB `REPEAT`, avec anti-aliasing désactivé. |
| `gradient-stroke-refusal` | `KanvasSurfaceProgram` | rendu | Stroke `drawRect` : largeur entière paire 4, non-AA, cap `Butt`, join `Miter`, miter par défaut 4, transform identité, dégradé linéaire sRGB `CLAMP` valide et nu, sans path effect/local matrix/filters/blender. |
| `scaled-solid-rrect` | `KanvasSurfaceProgram` | rendu | `drawRRect` solide, non-AA, sous l’unique scale axis-aligned borné `(2,1)`. |
| `solid-drrect-hole` | `KanvasSurfaceProgram` | rendu | `drawDRRect` solide, non-AA, identité, avec trou intérieur. |
| `custom-runtime-effect-unregistered-refusal` | `RoutedSceneProgram` interne | refus | Runtime effect custom non enregistré refusé avant toute soumission GPU. |
| `aggregate-memory-budget-refusal` | `RoutedSceneProgram` interne | refus | Frame dépassant le budget mémoire agrégé refusée pendant l’enregistrement. |

Chaque rendu passe par la route publique `Surface` et a un oracle CPU
indépendant, une comparaison de pixels, des compteurs de draw/pipeline et une
preuve de soumission. Les deux refus actuels vérifient un code stable et
l'absence de soumission, mais sont des `RoutedSceneProgram` internes. Le
catalogue ne possède donc pas de probe de refus public `Surface` : c'est un
gap de couverture pour une vague future explicitement approuvée, pas une
autorisation d'en ajouter un ici.

## Suites de tests du harness

| Classe | Ce qui est vérifié |
| --- | --- |
| `EvidenceBundleRoundTripTest` | Jeux de fichiers déterministes pour rendu/refus et vérification d’un bundle complet. |
| `EvidenceBundleSchemaSerializationTest` | Sérialisation exhaustive des champs du schéma v1. |
| `EvidenceBundleTamperTest` | Rejet de fichiers, hash, pixels, commit, scène, schéma ou raison de refus modifiés. |
| `EvidenceBundleVerifierStrictnessTest` | Règles strictes de route, pixels, télémétrie, environnement et JSON non ambigu. |
| `EvidenceBundleWriterContractTest` | Écriture atomique, nettoyage des échecs, sécurité des chemins/symlinks et préservation PNG. |
| `PromoteEvidenceCliTest` | Promotion transactionnelle, préflight, rebaseline et restauration après échec. |
| `VerifyEvidenceCliTest` | Vérification d’un root complet : catalogue exact, verdicts et environnement cohérent. |
| `GpuEvidenceArchitectureBoundaryTest` | Absence de second renderer ; seules les captures correctness/performance acceptent une scène ciblée. |
| `EvidenceSceneContractsTest` | Validité des IDs, dimensions, oracles, politiques de comparaison et refus stables. |
| `CatalogExpectationInvariantTest` | Invariants du catalogue : un oracle par rendu, aucun pour les refus, et pas de faux succès. |
| `GpuEvidenceCatalogTest` | IDs, routes publiques, opérations Canvas littérales, oracles et tolérances du catalogue. |
| `GpuEvidenceCatalogOracleTest` | Calcul indépendant des pixels des scènes : `SrcOver`, clips, strokes, gradients et affine. |
| `EvidenceComparatorTest` | Comparaison RGBA, tolérances, pixels transparents, dimensions et diff déterministe. |
| `EvidenceExpectationGateTest` | Conversion correcte des observations rendu/refus/indisponible en verdicts. |
| `SurfaceSrgbOracleMathTest` | Mathématiques sRGB, prémultiplication, arrondi RGBA8 et géométrie locale. |
| `SurfaceSrgbSrcOverCpuOracleTest` | Oracle CPU de composition translucide en espace linéaire prémultiplié. |
| `SurfaceSrgbSeparableMaskBlurCpuOracleTest` | Oracle CPU de blur : noyau, frontières decal et quantification par passe. |
| `SurfaceSrgbGradientCpuOracleTest` | Oracles linéaire/radial/sweep/repeat, stops, angles, géométries dégénérées et entrées invalides. |
| `GPUPreparedEvidenceExecutorContractTest` | Contrat du chemin prepared : télémetrie, readback, completion, fermeture et erreurs non promouvables. |
| `KanvasSurfaceEvidenceExecutorTest` | Contrat de la route publique `Surface` : une soumission, travail draw/pipeline positif et refus stricts. |
| `GpuEvidenceCliTest` | Cycle de vie CLI : open, execute, close, dispose, erreurs fatales et absence d’artefact après échec. |
| `GPUPreparedEvidenceExecutorSmokeTest` | Smoke test GPU réel du rectangle solide ; actif seulement avec `GPU_EVIDENCE_SMOKE=1`. |
| `PerformanceEligibilityTest` | Éligibilité de la mesure selon l’adapter, son caractère fallback et l’identité hardware. |
| `PerformanceRunnerTest` | Capture cold/warmup/measured, session `Surface` réutilisée, oracle CPU et compteurs de soumission. |
| `PerformanceStatisticsTest` | Configuration fixe (10 warmups, 90 mesures) et percentiles nearest-rank. |
| `PerformanceBundleTest` | Schéma, hashes, métriques, télémétrie et remplacement atomique des bundles de performance. |
| `PerformanceCliTest` | Parsing fermé de la CLI performance et rejet des scènes non rendables. |

`EvidenceBundleVerifierTestCompatibility` et
`EvidenceBundleWriterFixtureCompatibility` sont des adaptateurs de fixtures
utilisés par les tests ; ils ne déclarent pas de test directement.

## État des preuves promues et mesures locales

Les artefacts checked-in sous
`reports/gpu-renderer/evidence/correctness/promoted/` reflètent 16 cas : 14
rendus et 2 refus, capturés depuis
`226674870ee09e080beee62b8d2935704f4b3331`. Ils contiennent 122 hashes de
manifest, puis le code associé a été fusionné au SHA master
`116ce6ec3547c1d367e4c51881597a351eb285c4`. Les IDs
`repeat-gradient-refusal` et `gradient-stroke-refusal` restent historiques,
mais décrivent désormais des rendus.

Séparément, une baseline performance locale non promue a été auditée au SHA
`a1143cee2425e3a818dabe076ac468c551fbae75` sur Apple M2 Max natif
non-fallback : 14 rendus, 1 cold + 10 warmups + 90 mesures par scène, 1260
échantillons et p95 entre 4,30 et 8,40 ms. Elle est reporting-only et
reproductible, sans statut de release-blocking evidence.

## Matrice de couverture requise pour un backend Skia-like

Cette matrice décrit les tests à ajouter ou à promouvoir ; elle ne déclare pas
des fonctionnalités comme supportées. Pour chaque ligne, le comportement du
code Kotlin décide s'il s'agit d'un rendu natif ou d'un refus. Une route non
implémentée doit d'abord avoir un test de refus déterministe, avec son code de
diagnostic et zéro soumission GPU ; elle ne devient un test de rendu qu'avec
l'implémentation correspondante.

Une ligne rendable est une preuve complète seulement si elle passe par l'API
publique `Surface` et produit : l'entrée reproductible, l'oracle CPU ou une
référence Skia identifiée, le readback GPU, le diff et ses statistiques, les
compteurs de route (draws, pipelines, fallbacks), ainsi que l'environnement
de capture. Une ligne de fidélité Skia ne peut pas utiliser l'oracle CPU
Kanvas comme référence Skia : celui-ci reste un contrôle de cohérence interne.

### Règles communes à chaque famille

| Vérification | Exigence |
| --- | --- |
| Route publique | Construire la scène par `Kanvas Surface`, et vérifier la route préparée réellement prise ; aucun succès CPU de repli ne vaut rendu GPU. |
| Cas générés | Couvrir au minimum un cas nominal, une limite de domaine et une entrée invalide ou hors budget. Les générateurs doivent avoir une seed et sérialiser l'entrée qui échoue. |
| Comparaison | Comparer dimensions, pixels RGBA, maximum/mean des écarts, nombre de pixels en défaut et image diff. Les seuils sont propres à la famille ; aucun assouplissement global. |
| Couleur | Exercer alpha 0/1/intermédiaire, destination opaque et non opaque, valeurs transparentes colorées, prémultiplication, arrondi RGBA8 et espace de couleur exposé par l'API. |
| Refus | Vérifier le code, le message diagnostic stable quand il est contractualisé, l'absence de submission/readback et l'absence d'artefact promouvable. |
| Reproductibilité | Fixer taille, seed, inputs, version du code, adapter et flags ; rejouer le même cas après sérialisation du bundle. |
| Régression | Toute correction de pixel ou de route ajoute le cas réduit qui l'a révélée, avec sa classification (coverage, blend/premul, colorspace, sampling, filter bounds, glyph, routage ou référence). |

### Priorité P0 — préserver les routes déjà rendables

Ces contrôles préservent les 16 rendus actuels et les 2 refus contractuels
avant d'élargir une promesse de support. Ils doivent être des captures hardware
promouvables, pas seulement des tests unitaires d'oracle.

| Domaine | Cas supplémentaires nécessaires | Vérifications déterminantes |
| --- | --- | --- |
| Catalogue courant | Vérifier exactement 16 rendus et 2 refus internes. | Les IDs `repeat-gradient-refusal` et `gradient-stroke-refusal` sont des rendus `Surface`; `scaled-solid-rrect` et `solid-drrect-hole` apportent une preuve `Surface` bornée. Il n'existe pas de probe de refus `Surface` dans le catalogue courant. |
| Rectangles solides et `SrcOver` | Bords négatifs/hors surface, rectangles vides, coordonnées fractionnaires, ordre de trois draws et alpha 0/1/partiel. | Règle top-left/coverage, clipping de surface, prémultiplication et ordre de composition. |
| Transformations et pile d'état | `save`/`restore` imbriqués, `restoreToCount`, translation, scale, rotate, skew, `concat`, `setMatrix`, `resetMatrix`. | Matrice courante restaurée exactement ; aucun état, clip ou alpha ne fuit vers le draw suivant. |
| Clip rectangulaire | Intersection, clip vide, clip hors surface, clip après transformation et plusieurs `save`/`restore`. | Bounds exactes, zéro draw visible pour un clip vide, route scissor ou refus explicite documenté. |
| Stroke rect | Largeurs minimale/maximale autorisées, intérieur/extérieur de surface, jointures aux quatre coins et transformation. | Contour sans remplissage parasite, budget respecté. Le matériau solide reste admis; le gradient nouvellement rendu est borné au linéaire sRGB `CLAMP` nu. AA, path effect, autres shaders/tile modes/local matrix, filters, blender, largeur invalide/impaire et alternatives cap/join/miter sont refusés. |
| Gradients actuels | Stops transparents, stops coïncidents, positions non uniformes, géométrie dégénérée, local matrix, `CLAMP` et le `REPEAT` linéaire borné rendu. | Paramétrisation identique CPU/GPU pour les routes rendables. `MIRROR`, `DECAL`, radial/sweep `REPEAT`, ainsi que le linéaire `REPEAT` sur RRect/Path ou `drawRect` mask-filtered sont refusés. |
| Blur masque | Sigma 0, bornes autorisées, bord de surface, rect minuscule, translation et dépassement de budget. | Kernel, tile mode, quantification et bounds d'intermédiaire ; refus avant allocation/soumission au-delà des limites. |
| Refus actuels | Runtime effect non enregistré et budget mémoire agrégé. | Code inchangé, aucune submission, aucun bundle de réussite ou compteur de pipeline/draw positif ; les deux refus sont internes, donc ne couvrent pas la route publique `Surface`. |

### Priorité P1 — couvrir toute surface Canvas exposée

Chaque entrée publique ci-dessous doit avoir une ligne de preuve, rendue si la
route est implémentée, sinon refusée explicitement. Cela évite qu'une API
semblant disponible produise silencieusement un fallback ou une image vide.

| Famille d'API | Cas à couvrir | Critères de qualité |
| --- | --- | --- |
| `drawColor`, `clear`, `drawPoint`, `drawPoints` | Modes de blend, clear opaque/transparent, points isolés/lot, points hors surface et taille/AA si exposés. | Clear remet exactement le contenu attendu ; ordre de draw et coverage des points ; un batch est égal à la somme des points unitaires. |
| `drawRect`, `drawRRect`, `drawDRRect` | Rayons uniformes et quatre rayons distincts, rayons dégénérés, rrect imbriqué, alignement pixel et sous-pixel. | Géométrie sans trous ni dépassements ; rendu AA si supporté, sinon refus stable plutôt qu'approximation silencieuse. |
| `drawPath` | Segments ligne/quadratique/cubique, contours multiples, fill types, fermeture implicite, convexité, auto-intersection et budgets de verbes/edges. | Coverage et winding cohérents ; diagnostic précis pour complexité, fill type, AA ou transform non pris en charge. |
| Strokes généraux | Caps butt/round/square, joins miter/round/bevel, miter limit, dash intervals/phases, hairline et strokes sous transformation. | Largeur et joins identiques à l'oracle ; les combinaisons non supportées ne doivent pas être rasterisées comme un remplissage incorrect. |
| Clips avancés | `clipRect`, `clipRRect`, `clipPath` convexe ou complexe, clips emboîtés, AA, difference/inverse si l'API les expose. | Politique explicite scissor/stencil/mask ; profondeur, surface de masque et clip impossible observables dans le diagnostic. |
| Images | `drawImage` et `drawImageRect` avec nearest/bilinear, source crop, destination inversée, alpha, translation/scale/rotation et image partiellement hors surface. | Sampling, origine, clamp/tile mode, couleur et bounds ; codec, mipmap ou format indisponible ont un refus dépendant de la dépendance. |
| Grilles et sprites | `drawImageNine`, `drawImageLattice`, `drawAtlas` avec cellules étirées, transparentes, tronquées et transformées. | Découpage sans seams, ordre des sprites, couleur par sprite, budget d'instances et refus sans soumission si la route n'est pas prête. |
| Sommets et mesh | `drawVertices` et `drawMesh` : triangles, indices, couleurs par sommet, texture, matrice locale, données malformées et limites de taille. | Ordre d'index, interpolation prémultipliée, validation des buffers et diagnostic de layout/WGSL plutôt qu'une substitution CPU. |
| Enregistrement | `drawPicture` avec replay simple, état local au picture, picture vide et picture récursif/interdit. | Replay dans l'ordre, isolation de l'état et refus clair des contenus non abaissables. |
| Snapshot et annotation | `flushAndSnapshot` avec bounds valides, vides et hors surface ; `drawAnnotation` avant/après draws et replay de picture. | Snapshot ne réordonne ni ne double les draws ; annotation ne modifie aucun pixel, mais reste sérialisée et visible au diagnostic. |
| Texte | `drawText`, `drawString` et `measureText` : latin simple, baseline, advance, scale/rotation, clipping, alpha, glyph manquant et cache atlas. | À activer uniquement avec les fontes réellement livrées ; sinon refus dépendant de la dépendance. Les écritures complexes restent un refus stable jusqu'à la disponibilité du shaping réel. |
| État de couche | `saveLayer` : alpha, bounds, clip, couche vide, couches imbriquées, restore avec blend/filter. | Allocation, composite final, durée de vie des intermédiaires et dépassements de budget observables ; aucune fuite entre couches. |
| Queries d'état | `matrix`, `saveCount`, `localClipBounds`, `isClipEmpty`, `isClipRect`, `quickReject` rect/path avant et après chaque mutation d'état. | Les queries reflètent l'état enregistré sans modifier la display list ; leurs réponses restent cohérentes avec les pixels et le route diagnostic. |

### Priorité P1 — paint, couleur, image filters et runtime effects

| Domaine | Cas à ajouter | Vérifications déterminantes |
| --- | --- | --- |
| Blend modes | Tous les Porter-Duff exposés, puis chaque mode avancé réellement supporté ; source/destination opaque et partiellement transparentes. | Sélection de la route fixed-function ou layer, équations prémultipliées, destination non opaque et refus des modes absents. |
| Color filters | `ColorMatrix`, blend color filter, composition de filtres et matrices identité/alpha-only/valeurs hors gamme. | Ordre paint/filter/blend, clamp et conversion couleur ; matrice malformée ou combinaison absente refusée explicitement. |
| Espaces de couleur | sRGB et tout espace réellement exposé, conversions linéaires, premul/unpremul, transparence colorée et quantification. | Les résultats CPU/GPU restent cohérents sans double conversion ni halo de bord. |
| Image filters | Offset, blur, crop, color matrix, blend à deux enfants, affine et DAG de 2 à 4 nœuds. | Dump du plan, ordre des passes, nombre/bytes/bounds des intermédiaires, ownership et refus déterministe sur transform, tile mode, DAG ou budget hors périmètre. |
| Runtime effects enregistrés | Chaque descriptor enregistré : uniforme scalaire/vecteur/matrice/tableau, child shader/image, layout multiple et valeurs limites. | Parse/validation WGSL, reflection, offsets et tailles du packer, comportement CPU associé et même image GPU. |
| Runtime effects inconnus | Descriptor absent, uniforme manquant/en trop, type faux, enfant invalide, WGSL/reflection non compatible. | Rejet avant pipeline/submission ; aucun chemin de compilation dynamique SkSL n'est autorisé. |

### Priorité P2 — intégrité du pipeline WebGPU

Ces tests sont headless/offscreen et vérifient le backend, pas une fenêtre
native.

| Sujet | Cas à couvrir | Vérifications déterminantes |
| --- | --- | --- |
| WGSL généré et enregistré | Parse, reflection et impression déterministe pour chaque module produit ; WGSL invalide et bindings/entry points incohérents. | Aucune hypothèse cachée sur le parser ; structure de bindings, types et diagnostics reproductibles. |
| Uniforms et ressources | Layout réfléchi contre packer Kotlin, alignement/padding, valeurs limites, textures/samplers absents ou incompatibles. | Octets, offsets, taille finale et validation GPU identiques ; erreur avant draw si incompatibles. |
| `PipelineKey` et caches | Même layout avec valeurs d'uniforme distinctes, changement de shader/layout/blend/clip, séquences de scènes et éviction bornée. | Les uniforms seuls ne créent pas de pipeline ; les dimensions de code/layout/state créent la bonne variante sans collision ni croissance non bornée. |
| Cycle de vie device | Resize, perte/récupération de device, fermeture du backend, soumission annulée et réouverture d'une session. | Invalidation des caches, ressources libérées, diagnostic stable et absence de handle obsolète. |
| Déterminisme | Même scène/seed sur exécutions répétées, sérialisation de display list et ordre de passes. | Même résultat et même route sur le même adapter ; les variations inter-adapter restent capturées avec leurs métadonnées. |
| Limites et sécurité | Textures trop grandes, buffers/instances excessifs, depth de clip/couche/filtre, NaN/Inf et rectangles/path invalides. | Limite vérifiée avant allocation quand possible, absence de fuite et refus actionnable. |

### Référence Skia, GM et promotion des preuves

Une couverture unitaire ou un oracle CPU ne suffit pas à prétendre à une
fidélité Skia. Pour chaque famille rendable ci-dessus, sélectionner des GMs à
fort signal (un cas simple, un cas de bord et un cas de composition), conserver
la provenance de la référence et associer les artefacts CPU/GPU/diff/statistiques
au même identifiant de scénario. Les scènes à référence Kanvas CPU sont
étiquetées « cohérence interne » et ne comptent pas comme GMs de fidélité.

Le seuil de promotion dépend de la famille (par exemple coverage, gradient,
sampling, filtre ou texte). Une régression doit conserver son diff, sa cause
classifiée et son cas réduit ; modifier un seuil global ne peut pas masquer une
régression d'une autre famille. Les refus sont promus avec le même niveau de
traçabilité, mais sans PNG de réussite ni métrique de rendement présentée comme
valide.

### Mesures de performance séparées de la correction

La correction reste bloquante avant toute mesure. Les scénarios de performance
headless nécessaires sont : batch de rects solides, rect/rrect AA, gradients
linéaire/radial/sweep, images nearest/bilinear, blend avec alpha partiel et
destination non opaque, `saveLayer`, blur/filter, texte quand la dépendance est
livrée, vertices/atlas quand la route est rendable, et frames chaudes de shaders
générés.

Chaque résultat conserve les 10 warmups et 90 mesures actuels, les échantillons
bruts, p50/p95, adapter, fallback adapter, dimensions et commits. Il collecte
aussi créations et hits de pipelines, draws, bytes d'uniforms, allocations et
bytes d'intermédiaires, uploads, readbacks et fallbacks inattendus. Une mesure
sur adapter fallback ou sans preuve de correction reste informative, jamais un
gate de release.

## Ordre concret d'ajout

1. Vérifier le catalogue courant à 16 rendus / 2 refus et conserver les
   artefacts promus alignés. Aucun ajout de catalogue n'est autorisé sans une
   petite vague explicitement approuvée, ses IDs précis et ses preuves
   d'acceptation.
2. Ajouter les probes `Surface` P1 pour chaque entrée publique sans preuve :
   elles attendent le rendu natif existant ou le refus explicite correspondant.
3. Pour chaque refus révélant une route manquante, ajouter d'abord le cas
   réduit et son diagnostic ; lors de l'implémentation, le convertir en oracle
   CPU + capture GPU + artefact de référence.
4. Ajouter les contrôles WGSL/layout/cache/device et les scénarios de
   performance seulement aux routes rendables, puis promouvoir les GMs avec une
   référence Skia traçable.

## Commandes utiles

```bash
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha>
./gradlew :integration-tests:gpu-evidence:gpuEvidencePerformance -PsourceCommit=<sha>
```
