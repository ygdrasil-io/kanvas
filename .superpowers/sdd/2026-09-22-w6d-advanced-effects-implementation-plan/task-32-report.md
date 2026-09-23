# Task 32 report — amended numeric slice complete, native status UNKNOWN

Base: `51536252662a4cf2d5a5a5ac58964c07dc59987b`.

## RED causal

Ajouté un témoin public `remaining lighting families match independent point spot and specular oracle` dans `W6dLightingSurfacePixelTest`, avec oracle CPU indépendant (normales Sobel, point/spot/specular, cône et alpha). Avant les changements de production, le test échouait exactement avec :

`w6b.filter.unsupported_family: The captured image-filter family belongs to W6d.`

## Prototype préservé (non commit)

Le worktree contient un prototype non commité qui étend le `FilterPass` W6d existant : cinq IDs gelés, paramètres 3D mappés, direction/cosinus spot calculés dans le planner, admission/lowerer, et un fragment WGSL unique point/spot/specular. Aucun appel à `GPULightingFilter.execute`, aucune pass ou allocation parallèle.

Compilations observées :

- `rtk ./gradlew :gpu-plan:compileKotlin` : succès.
- `rtk ./gradlew -q :gpu-renderer:compileKotlin` : succès.

Le GPU atteint le nouveau fragment. Les erreurs de validation WGSL rencontrées et corrigées dans le prototype furent : `isNan` non reconnu, expression `if` non supportée, puis collision de symbole `base`.

## Blocage exact

Le test public des cinq familles échoue maintenant sur la seconde variante spot avec une divergence de pixel :

`channel 0 expected=175 actual=121`

Le XML correspondant est `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest.xml`. Les exécutions de test terminent également par exit natif `133`; conformément au brief, celui-ci est `UNKNOWN`, mais l’assertion XML est elle-même rouge et reste donc le blocage réel.

La suite complète requiert de caractériser séparément la sémantique spot (orientation de l’axe, coordonnées de texel et facteur de cône), puis de faire passer les cinq pixels avant d’ajouter les preuves exigées : `surfaceScale=-1`, exposants `0.5/129`, refus `kd/ks=-0.1`/non-fini/perspective, degenerate/pow, sentinelle+recovery, wrapper `ColorFilter` conservant la demande non bornée et replay Picture mémoire/wire avec changement de Z.

## État Git

Aucun commit Task 32. Les changements de prototype sont volontairement préservés sur instruction du contrôleur, afin de servir au découpage suivant.

## Reprise après diagnostic (périmètre réduit, plan `06cf9c2`)

Le diagnostic fourni a établi que `expected=175 actual=121` visait le troisième cas, `DISTANT_SPECULAR`, et non spot. La cause était l’oracle qui utilisait `location-surface` pour distant ; le filtre exécutait déjà correctement la direction distante. L’oracle corrige désormais cette direction et encode l’alpha specular en linéaire. Le témoin `(0,0)` est épinglé à `[121,121,121,49]`.

Un RED spot distinct a ensuite établi la formule erronée `pow(edgeRamp, falloff)`. Le planner restait gelé ; la correction WGSL et oracle applique désormais la règle Skia `pow(cosAngle, falloff) * edgeRamp`. Les cinq familles sont des méthodes publiques nommées.

GREEN : `rtk ./gradlew -q :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'` a produit XML `tests=13 failures=0 errors=0 skipped=0`. Le processus natif sort `133`, donc statut natif `UNKNOWN` malgré XML vert, conformément au brief.

## Témoin affine ajouté

La méthode publique `point diffuse maps location Z and surface scale through affine canvas matrix` utilise `Canvas.setMatrix(Matrix3x3F32(sx=2, sy=3, tx=5, ty=7))` et un `Render`+`Readback`. Le point de filtre `(1,0,1)` est mappé en device à `(7,7,2.5)`; le source layer commence en device `(5,7)`, donc son repère local scellé est `(2,0,2.5)`. `surfaceScale=1` devient `2.5`. L’oracle indépendant utilise l’alpha rasterisé après la même transformation et compare aussi sa sortie à l’oracle non mappé pour vérifier le discriminant.

Le premier essai attendait `140` au channel 0 et recevait `237`; l’inspection de l’espace du filtre a établi que l’oracle mélangeait device et layer coordinates. Avec le point correctement rebasé de `(7,7)` vers `(2,0)`, le test public passe sans changement de production. C’est une correction d’oracle, pas un défaut du planner.

Gates finaux séquentiels : `:gpu-plan:compileKotlin` exit 0; `:gpu-renderer:compileKotlin` exit 0; `:kanvas:compileTestKotlin` exit 0. Pour W6d, la sortie Gradle liste les 15 méthodes en PASS, dont le témoin affine, mais le worker natif termine avec exit 133 avant l’écriture du XML de classe. Le XML agrégé Gradle donne `tests=1 failures=1 errors=0 skipped=0` pour l’échec de processus, pas pour une méthode JUnit; statut natif `UNKNOWN`. Pour W6a restore, le XML de classe donne `tests=9 failures=0 errors=0 skipped=0`; le XML agrégé note séparément `tests=1 failures=1 errors=0 skipped=0` pour le worker exit 133; statut natif `UNKNOWN`.

Préoccupations restantes : Task 33 reste propriétaire des refus/recovery, du wrapper ColorFilter et du replay Picture. La revue Sol Task32 reste un gate distinct avant Task33/Task4.

## Fix round 1/5 — revue Sol

Les trois constats numériques ont été traités sans ajouter de graphe, target, ID ou binding :

- Le cône WGSL n’évalue désormais `pow` que pour `spotCosine >= 0` et dans le cutoff ; un résultat `NaN` ou hors plage F32 donne une contribution nulle. L’oracle indépendant applique la même convention. Le témoin public nommé avec `cutoff=180°`, `exponent=0.5` épingle `[0,0,0,255]`; sur ce backend le résultat était déjà noir avant la garde, donc il prouve la convention de sortie mais ne distingue pas causalement l’implémentation de la garde.
- Le témoin spot normal `falloff=1`, `cutoff=90°` est maintenant épinglé explicitement à `[180,180,180,255]`.
- La normalisation `target-location` est déplacée dans `:math` : soustraction, échelle et longueur en F64, puis narrowing F32 fini contrôlé avant le recipe gelé. Le planner ne possède plus de helper géométrique local. RED : la variante F32 de la soustraction rendait les extrêmes finis `±1.8e38` non représentables ; le test math public échoue alors, et passe avec la voie F64.

Vérifications séquentielles : `:gpu-plan:compileKotlin` exit 0 ; `:gpu-renderer:compileKotlin` exit 0 ; `:kanvas:compileTestKotlin` exit 0 ; `:math:matrix:jvmTest` ciblé 1/0/0/0. Le XML W6d de `W6dLightingSurfacePixelTest` est 16/0/0/0 et le XML W6a restore est 9/0/0/0. Les deux lancements `:kanvas:test` terminent néanmoins par exit natif 133 après les assertions ; statut natif `UNKNOWN`, sans échec JUnit dans ces XML.

## Fix round 2/5 — re-revue en attente

Le nouveau témoin public `spot diffuse multiplies exponentiated cosine by a nontrivial edge ramp` utilise `exponent=2`, `cutoff=36°` et épingle le pixel `(0,0)` à `[116,116,116,255]` avant création de `Surface`, avec le même `Render`+`Readback` et la tolérance familiale de 2. La rampe est strictement entre 0 et 1 pour ce pixel.

RED causal contrôlé : une mutation temporaire (non commitée) du fragment WGSL remplaçant `pow(cosAngle, exponent) * edgeRamp` par `pow(edgeRamp, exponent)` fait échouer ce témoin avec `channel 0 expected=116 actual=98`. La commande `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest.spot diffuse multiplies exponentiated cosine by a nontrivial edge ramp'` a donc Gradle exit 1 / natif 133 (UNKNOWN) et XML JUnit 1/1/0/0. La formule a été restaurée par `apply_patch`, sans commit de la mutation.

Custody XML : après le sélecteur W6a `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest'` (neuf méthodes console PASS, Gradle exit 1 uniquement pour natif 133), le sélecteur W6d a été lancé en dernier : `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest'`. Son XML consulté immédiatement est `kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.W6dLightingSurfacePixelTest.xml` avec `tests=17 failures=0 errors=0 skipped=0`, incluant les méthodes spot `uses Skia falloff before cone edge ramp`, `multiplies exponentiated cosine by a nontrivial edge ramp`, `treats negative fractional-power bases as zero contribution` et `spot specular matches independent oracle`. Le Gradle sort 1 car le worker natif sort 133 après les assertions : statut natif `UNKNOWN`, pas de revendication full GREEN.
