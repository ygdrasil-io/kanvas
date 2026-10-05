# W7 — matrice de ColorFilter dans le domaine de destination

## Objectif et décision

Ajouter la capacité GPU native manquante pour évaluer `ImageFilter.ColorFilter(ColorFilter.Matrix, null)` dans le domaine de composition de destination d’un `saveLayer` non imbriqué. Le comportement doit rester cohérent de l’entrée du layer au retour sur la surface, dans `LINEAR` comme dans `SRGB_ENCODED`, et doit être démontré par des pixels natifs calculés contre une oracle analytique indépendante.

Le domaine de destination (`CompositionDomain`) est l’unique contrat ajouté à ce lot. Il détermine l’arithmétique de la matrice, le format des textures du filtre et du layer, la restauration et la lecture des pixels. Il n’y a pas de nouveau paramètre public de domaine de calcul du filtre. `SkColorFilter.h` établit que les filtres publics Skia utilisent par défaut le domaine de destination; un éventuel override de working color space reste une extension distincte. `SkEffectPriv.h` contient la déclaration de `SkStageRec`; il ne porte pas cette loi par défaut. Les reçus primaires du contrôleur établissent aussi l’absence de transfert sRGB implicite dans la matrice. La review indépendante a consommé ces reçus, sans pouvoir relire les sources primaires elle-même.

Le domaine par défaut reste `LINEAR`; les identités et résultats historiques `LINEAR` sont conservés. En `SRGB_ENCODED`, source, filtre, stockage intermédiaire, restore et readback suivent tous le domaine encodé; la cible physique est `RGBA8Unorm` correspondant au format logique `RGBA8_UNORM_ENCODED_SRGB_PREMUL`, sans conversion sRGB cachée. Le format linéaire reste `RGBA8UnormSrgb` / `RGBA8_UNORM_SRGB_LINEAR_PREMUL`.

## Contrat API et identité numérique

Étendre le compilateur avec cette signature, en préservant le comportement et le résultat des appels existants :

```kotlin
ColorFilterPlanCompilerV1.compile(
    filter: ColorFilterNode,
    compositionDomain: CompositionDomain = CompositionDomain.LINEAR,
): ColorFilterCompileResultV1
```

Le chemin `SRGB_ENCODED` compile uniquement un `ColorFilterNode.Matrix` feuille. Il refuse avant tout appel natif les filtres composés, `Lerp`, les presets et les autres familles. En `LINEAR`, l’ensemble des familles actuellement valides et leurs comportements ne changent pas. `ColorFilterExecutionPlanV1.matrix(filter, compositionDomain = LINEAR)` construit un graphe typé lié au domaine. Son contrat expose ce domaine et l’inclut dans l’identité d’exécution; les identités de coefficients capturés peuvent rester indépendantes du domaine.

Ajouter `ColorOperationGraphV1.Scalar.InputEncodedPremul(channelI32: Int)` aux côtés de `InputLinearPremul`. Le graphe Matrix choisit explicitement l’entrée selon son domaine. Chaque visiteur, émetteur, évaluateur et substitution canonique scalaire reconnaît cette variante. La conversion entrée prémultipliée → droite, l’application des 20 coefficients row-major, le clamp puis la prémultiplication gardent leur ordre numérique actuel. Aucun transfert de courbe n’est ajouté à la matrice. Les valeurs de coefficients, budgets d’uniformes et limites restent inchangés.

Les clés de plan, graphe, exécution et cache liées à la cible doivent distinguer `LINEAR` d’`SRGB_ENCODED`; deux plans Matrix aux coefficients identiques mais aux domaines différents ne peuvent pas entrer en collision. Les identités de matrice indépendantes de la cible restent réutilisables là où elles ne représentent que les coefficients.

## Admission publique

N’admettre en domaine encodé que cette famille de layer :

- un `saveLayer` public unique, non imbriqué, initialisé transparentement;
- un `ImageFilter.ColorFilter` contenant une feuille `ColorFilter.Matrix` avec entrée source courante/null;
- restauration `SrcOver` avec alpha capturée;
- rectangles pleins à bords nets, `DrawColor`/`Clear`, et transforms entières et clips durs déjà admis par le chemin encodé.

Conserver les refus avant rendu pour l’imbrication, `initWithPrevious`, backdrop, les autres `ImageFilter`, les autres `ColorFilter` et compositions, les effets directs de peinture, les shaders/images, le blending non admis et les couches AA. Le reste de W6b en `LINEAR` ne change pas. Les frames et layers encodés simples déjà admis restent inchangés. Ne pas transformer l’ouverture étroite de cette famille en admission générale de W6b.

## Propagation des formats et recettes

W6a choisit déjà un format logique selon `CompositionDomain`; le chemin W6b Matrix doit recevoir ce format gelé depuis le layer parent à travers la construction, l’évaluation et le lowering de la recette. La ressource de source, chaque cible intermédiaire/filtrée, le parent et la cible de restauration doivent avoir le même domaine, format logique et nombre d’échantillons (un). Étendre les variantes validées des formats dans les recettes `W6FilterColorFilter` et `W6FilterCompositeLayerPlainRecipeV1`; ne pas supprimer ni affaiblir leurs contrôles de cohérence.

Les deux pipelines de materialization prennent explicitement le format GPU de la recette pour le pass ColorFilter et la restauration; ils ne déduisent pas ce format d’un helper par défaut. La création des ressources du cache et `colorSpaceIdentity` choisissent le format/domaine exacts. Conserver les durées de vie, epochs, réservations, quotas, comptabilité et règles de réutilisation actuels. Les validateurs natifs continuent de vérifier les formats et usages avant allocation et soumission.

Les axes d’implémentation sont `CompositionAdmissionV1`; `ColorFilterPlanCompilerV1`, `ColorFilterExecutionPlanV1`, `ColorOperationGraphV1` et ses visiteurs; la propagation W6a↔W6b, ressources et recettes natives; les deux chemins de materialization; `PlanPhysicalLayoutV1` et `GPUW6cSpatialFilterSessionCache`. Les constats I1–I4 et M1 du rapport de review sont des exigences de conception ou qualifications à établir; ils ne sont pas des bugs natifs déjà reproduits.

## Contrat de pixels natifs

Créer `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7ColorFilterDestinationDomainSurfacePixelTest.kt`. Chaque expected buffer est construit analytiquement, avant création de la `Surface`, sans graphe produit, plan, renderer ou readback GPU dans l’oracle. Lire et comparer les buffers complets de 32×32. Utiliser la politique existante d’écart pixel ±2; alpha doit être exact quand le cas est déterministe. Ne pas modifier comparateur, tolérance globale ou infrastructure de tests.

Les cas positifs couvrent les deux domaines :

1. Matrice identité sur RGBA `[128,64,192,255]`.
2. Matrice luma rouge opaque utilisant les coefficients portés `.2126/.7152/.0722` : résultat RGB 127 en `LINEAR`, 54 en `SRGB_ENCODED`, alpha 255. Ce contrôle de non-régression LINEAR ne répète pas l’enquête causale GM111.
3. Une entrée non extrême avec alpha source partiel et matrice non triviale.
4. Alpha de restauration partiel sur fond transparent et sur fond opaque connu; ordre non commutatif `draw-before → layer filtré → restore-alpha → draw-after`, vérifié sur buffer complet.
5. Matrice modifiant alpha, clamps de sortie et source d’alpha zéro.
6. Matrice dont les coefficients RGB sont nuls et dont les offsets RGBA valent `(.25,.5,.75,1)`, appliquée à du noir transparent. Sur la surface entière, attendu `LINEAR` `[137,188,225,255]` et encodé `[64,128,191,255]`.

Pour le sixième cas, dessiner uniquement un rectangle plein `4..8` sur `Surface(32,32)` et donner `4..8` comme bounds indicatifs de `saveLayer`. Ne pas installer de clip logique. L’inspection statique confirme que le hint n’est pas un clip. Comme une restauration qui affecte le noir transparent peut fixer les domaines effectif et désiré à la surface parente entière, ces bounds ne garantissent pas que la texture d’entrée du layer soit plus petite que la texture de sortie. Ce fixture public peut demander une sortie pleine surface tout en ne dessinant du contenu qu’en `4..8`; il n’établit pas à lui seul un échantillon natif hors de l’étendue de texture d’entrée. Ses pixels de biais qualifient la matrice sur des texels transparents **dans** la texture si la preuve native en confirme l’emplacement. Pour rechercher un vrai échantillon hors étendue, Task 1 doit aussi évaluer un témoin distinct déjà dans le scope public LINEAR : `drawRect(Rect(4,4,8,8), Paint(imageFilter = ImageFilter.ColorFilter(Matrix(bias), null)))` sur `Surface(32,32)`, avec le même shader `colorFilterRender` que le filtre de layer. La voie directe `drawRect`/`ImageFilter.ColorFilter` est déjà représentée par `W6cMultiInputSurfaceTest`; elle ne demande pas d’admettre le direct-filter en `SRGB_ENCODED`. Le plan et la preuve native doivent confirmer que la texture source est compacte, que la sortie demandée est pleine surface et qu’un sample tombe réellement hors source. Qualifier d’abord le noir transparent in-texture dans la voie layer. Si aucun témoin du scope ne prouve l’échantillon hors étendue, documenter exactement cette frontière non exercée et limiter les affirmations aux domaines/comportements réellement démontrés; ne pas interpréter un échec comme un shader return-zero causal sans preuve.

Chaque succès conserve et vérifie les opérations publiques capturées, le domaine et les formats planifiés, les recettes filtrée/restauration, les scopes natifs des deux passes, la soumission et sa completion, zéro refus et un readback complet. Inclure playback/replay des commandes publiques conservées, rendu répété sur la même `Surface`, puis sur une nouvelle `Surface`. Tester aussi l’ordre et la reprise après les refus étrangers, dans la même surface lorsque la récupération est attendue.

Vérifier une alternance `LINEAR → SRGB_ENCODED → LINEAR`: les identités de plan/cache ne collisionnent pas, le retour à LINEAR réutilise ou reconstruit seulement selon les règles existantes, et aucun format du domaine encodé ne fuit vers les deux côtés LINEAR. Réutiliser les sentinelles existantes de budget; ne pas calculer un nouveau budget à partir d’un plan natif et ne pas augmenter le budget par défaut. Les refus d’imbrication, d’images/shaders, blend, init/backdrop et filtres étrangers doivent être suivis d’une récupération valide.

## Qualification et étape de compatibilité distincte

La première livraison W7 est la qualification de la capacité native de layer Matrix dans les deux domaines, GM111 restant intact. Elle inclut contrôles LINEAR, témoin encoded, qualification du noir transparent in-texture, tentative séparée du falsifier direct LINEAR hors étendue, refus étrangers, récupération et tests voisins ciblés. Le runtime reste exécuté par le contrôleur seulement, une invocation runtime à la fois, avec protocole existant: outer limit 240 secondes, aucun daemon, journal FULL, vérification du groupe PID propriétaire, puis seal séparé avant toute écriture. L’implémenteur d’origine prend en charge tout correctif.

Si un témoin échoue, identifier d’abord la première frontière réellement divergente et établir sa causalité. Ne pas attribuer d’emblée le résultat au shader qui retourne zéro hors texture : le témoin `saveLayer` aux bounds `4..8` peut conserver une texture source couvrant toute la surface, et donc n’exercer que des texels transparents in-texture. Seulement si un témoin demande effectivement un sample hors de l’étendue réelle et établit que le return-zero cause l’écart, la correction appropriée est d’évaluer la matrice sur l’input transparent au lieu du retour zéro. Ne pas changer le shader sur suspicion statique seule et ne pas altérer l’oracle indépendant. Si aucun témoin public du scope n’établit cette frontière, en rapporter la limite et ne pas étendre les affirmations au-delà des domaines/comportements prouvés.

La compatibilité de GM111 constitue une seconde étape, uniquement après qualification et review de la capacité. Elle peut déclarer explicitement `compositionDomain=SRGB_ENCODED` pour ce GM, sans changer coefficients, opérations, fond, tolérance, seuil, référence ou valeurs par défaut globales. Exécuter d’abord les checkpoints 111–112, puis le recensement existant complet de 631 entrées. Conserver le dénominateur 443, les GM vertices index607, le timeout et les configurations existantes; ne lancer ni fonts, codecs ou jpg. Enregistrer l’avant/après du seul contrat du GM et garder la baseline historique. La migration reconnaît une politique de domaine pour approcher la référence; elle ne constitue pas un fix du renderer à entrée inchangée, un score « zero-delta » ou un gain avant mesure. Le producteur/backend/révision/configuration du PNG reste inconnu.

## Hors périmètre et invariants

Ne pas changer le défaut global, les coefficients de la matrice, les opérations, le background, la tolérance, le seuil de similarité ou la référence. Pas de nouvelle exclusion, CPU renderer/fallback/mock, skip GPU, relâchement de budgets/caps, infrastructure de test ou modification de décodage. Pas de fonts/external decoding/jpg-color-cube. Préserver à l’identique les fixtures protégés `96cd`, `b6206`, `490760` et `9a2058`. Les archives et espaces de travail SDD restent conservés. Aucune publication publique n’est autorisée par ce design; le rejet security antérieur exige encore une approbation spécifique avant toute publication.

## Bases probantes et limites

Le reçu contrôleur du 4 octobre 2026 établit l’oracle canonique existant `[54,54,54,255] × 1024` (hash `dc471d945ac4e533c368b7eaaa5d30c3e756f74d2c8af20b74a62766953207ba`) et l’identité du buffer natif déjà qualifié `[127,127,127,255] × 1024` (hash `65198d661c71eac87796e2f7d84f63eb69c7432e8a09b7485b4baaa7902f1f64`). Il ne s’agit pas d’un nouveau rendu natif. L’oracle est la sortie du chemin actuel `ComparisonUtils.loadPngAsSrgbRgba`, non une interprétation ICC universelle. La présence d’un ICC dans le PNG ne révèle ni le domaine de composition ni la configuration de son producteur.

`SkColorFilter.h` est la référence primaire pour le domaine de working color space par défaut des filtres. `SkEffectPriv.h` est le lieu de déclaration de `SkStageRec`, pas la source de cette loi. Le GM upstream utilise `.213/.715/.072`, tandis que le port utilise `.2126/.7152/.0722`; cette différence de coefficients demeure distincte de l’écart 127/54, et reste inchangée dans ce lot. Les reçus précis et les références de sources sont consignés dans le diagnostic compagnon.

Ce document ne prétend pas que la capacité est implémentée, testée, approuvée après implémentation, fusionnée, publiée ou globalement verte. Il fixe la direction et les critères de qualification pour les étapes à venir.
