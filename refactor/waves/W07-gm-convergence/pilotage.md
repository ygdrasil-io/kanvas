# Pilotage de la convergence Skia

Baseline : PR draft [#2411](https://github.com/ygdrasil-io/kanvas/pull/2411),
empilée sur [#2410](https://github.com/ygdrasil-io/kanvas/pull/2410).
Lot suivant : routage standalone rect/path, renderer `718445e6e`.

Objectif : rapprocher les pixels du corpus Skia éligible, avec une mesure par
identité de GM, une durée bornée et des régressions explicites. Les fonts,
codecs et `jpg-color-cube` conservent leurs exclusions documentées. Les limites
de rendu et timeouts éligibles restent au dénominateur.

## Boucle de décision

1. Mesurer le code actuel contre les références inchangées ; conserver commit,
   corpus, empreintes des références, configuration, résultat et durée par GM.
2. Distinguer setup, refus de rendu, timeout, comparaison impossible et pixels
   comparés. Un rendu disponible ne vaut pas une conformité visuelle.
3. Classer les causes par nombre de GMs affectées et sélectionner une cause
   transversale, puis réduire un cas réel en témoin public si nécessaire.
4. Corriger et rejouer les mêmes identités. Toute disparition, changement de
   scope/référence/tolérance ou régression est signalé séparément du gain.
5. Publier le lot en PR empilée avec le bilan avant/après. Astra sert au
   diagnostic difficile ou à l'arbitrage architectural ; les validations
   intermédiaires ne remplacent pas les pixels.

Les gates W6 relatives à la durée de vie et aux ressources restent suivies.
Leur fermeture et la proximité visuelle sont deux mesures distinctes.

## Mesure

`measureSkiaParity` utilise la capture existante `Surface`, y compris les
exclusions observées pendant le setup. Il écrit un journal JSONL hors des
références et du fichier historique de scores. Chaque cas est persisté avant
le suivant ; un watchdog termine seulement le processus de mesure à 30 s
par GM. Après un timeout à l'index N, reprendre avec `gm.parityFrom=N+1` dans
le même dossier. Le cas reste enregistré comme timeout.
L'identité, la configuration et l'empreinte de référence sont figées avant
le watchdog ; les 30 s bornent ensuite setup, rendu et comparaison, pas
l'initialisation du registre ni la lecture préalable de cette empreinte.

```sh
rtk proxy ./gradlew :integration-tests:skia:measureSkiaParity --offline \
  -Pgm.rendererCommit=<SHA complet du renderer> \
  -Pgm.parityOutput=<dossier neuf> -Pgm.parityTimeout=30
```

La comparaison publie les pixels exactement identiques, les pixels dont tous
les canaux diffèrent de 2 au plus, la MAE normalisée sans tolérance et le SSIM
de luminance. Les tolérances/seuils propres à chaque GM restent visibles,
mais leur réussite n'est pas un taux global de parité : certains seuils sont
très permissifs. Les fonds uniformes peuvent aussi gonfler une métrique
globale ; les cas prioritaires sont inspectés spatialement.

Le premier journal complet fixe les identités et scopes de la baseline.
Les mesures suivantes doivent comparer ces mêmes lignes et empreintes,
et conserver les changements de périmètre comme tels. Aucune modification
des références, des seuils ou des exclusions ne peut compter comme réparation.

## Baseline du 29 septembre 2026

Renderer : `d661f10c32777c5d43cb549d3b27fa86449b1471` (PR #2410),
macOS / aarch64, JDK 25.0.1, Apple M2 Max. Configuration `Surface` par défaut,
fond blanc opaque comme le runner existant, comparaison RGBA sRGB via
`ComparisonUtils`. Ce lot ne modifie pas le renderer, les GMs, les références,
les seuils, les exclusions ou les anciens scores.

Le [snapshot machine](baseline-d661f10c3.json) conserve les 631 identités,
les quatre sessions, les empreintes et les résultats par GM. Les diagnostics
sont le **premier refus observé**, pas une attribution exhaustive des causes.

| Résultat sur les 443 GMs éligibles | Nombre |
| --- | ---: |
| Rendu obtenu et comparaison possible | 105 |
| Rendu obtenu, référence absente ou déclarée non fiable | 11 |
| Rendu obtenu, dimensions différentes de la référence | 7 |
| Échec de rendu | 260 |
| Échec pendant le setup | 57 |
| Timeout à 30 s | 3 |

Les 188 exclusions restent visibles : 133 fonts, 54 codecs, 1 quarantaine
(`jpg-color-cube`). Les timeouts éligibles sont `lattice2`,
`ninepatch-stretch` et `vertices` ; ils ne deviennent pas des exclusions.

Le rejeu complet retrouve les mêmes empreintes RGBA pour les **123 rendus**
et les mêmes empreintes de références. Les trois timeouts ont leur provenance
complète dans le snapshot final. La somme des durées par cas est de **188,45 s**,
timeouts inclus, hors démarrage Gradle/JVM ; ce n'est pas un benchmark de
débit ni une garantie de déterminisme sur d'autres GPUs.

L'ancien inventaire à 124 rendus n'avait pas cette borne de 30 s :
`ninepatch-stretch` et `vertices`, auparavant rendus, expirent désormais ;
`recordopts` rend maintenant, mais sa comparaison est à 0 %. Ce delta de
disponibilité ne constitue donc pas à lui seul une régression ni un gain visuel.

Sur les 105 comparaisons disponibles, **20** atteignent 99 % des pixels à
±2 par canal et **25** atteignent 95 %. La médiane est **54,64 %**.
Ce ne sont ni un pourcentage de parité globale ni une preuve de fidélité des
ports. `referenceStatus=trusted` signifie uniquement « déclaré tel par la
GM », pas « port audité et conforme à la source Skia ».

Les sept désaccords de dimensions concernent les six variantes
`fast/strict_constraint_*` rendues et `scale-pixels`. Ils restent au bilan,
sans redimensionner artificiellement les références.

## Lot standalone rect/path — 29 septembre 2026

Renderer : `718445e6ef366dbc63ab213b0f4eb301c574af12`.
Le [plan exécuté](stroke-routing-plan.md) et le
[snapshot complet](strokes-718445e6e.json) décrivent ce lot. Les **631 identités**,
les **443 éligibles**, les empreintes de référence, les seuils et la borne de
30 s sont inchangés. Les quatre sessions terminent le registre sans doublon.
Aucun port GM, codec, font, budget ou référence n'a été modifié.

| Mesure à corpus inchangé | Baseline | Après ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 123 | 164 |
| Comparaisons possibles | 105 | 142 |
| Rendus sans référence fiable disponible | 11 | 14 |
| Dimensions incompatibles | 7 | 8 |
| Échecs de rendu | 260 | 226 |
| Échecs de setup | 57 | 50 |
| Timeouts | 3 | 3 |
| Cas à ≥99 % des pixels ±2/canal | 20 | 26 |
| Cas à ≥95 % des pixels ±2/canal | 25 | 36 |

Les **41 nouveaux rendus** comprennent 37 comparaisons, 3 références
absentes/non fiables et `sharedcorners` aux dimensions incompatibles.
Les **123 anciens rendus restent disponibles**, dont **118 empreintes RGBA
identiques**. Aucun ancien cas comparable ne devient non comparable.
La médiane des 142 comparaisons atteint **65,23 %**, mais la médiane des
**105 mêmes cas appariés reste 54,64 %** : la hausse globale provient d'une
population élargie, pas d'une amélioration uniforme des anciens pixels.
Durée cumulée des cas : **185,12 s**, hors démarrage Gradle/JVM, sans valeur
de benchmark. `lattice2`, `ninepatch-stretch` et `vertices` expirent encore
à 30 s ; ils restent dans les 443.

Les six nouveaux cas au-dessus de 99 % sont `clip_strokerect` (100 %),
`clippedcubic` (99,52 %), `crbug_1174186` (99,999 %), `crbug_1177833`
(99,85 %), `ctmpatheffect` (99,52 %) et `tinyanglearcs` (99,90 %).
Ces scores décrivent la comparaison RGBA sRGB du runner, pas une conformité
globale Skia. Deux nouveaux rendus sont à **0 %** : `child_sampling_rt` et
`matrixconvolution_color`. Leur inspection source/PNG confirme des scènes
non fidèles : le premier ne construit aucun runtime effect, dessine un fond
gris et transmet un paint FILL à deux paths linéaires sans aire via
`GmCanvas.drawLine`; le second dessine cinq cercles, alors que son PNG montre
une convolution de texte. Ils restent au dénominateur actuel ; aucun gain
de fidélité n'est revendiqué pour eux.

### Changements des anciens pixels et dette ouverte

| GM | Pixels ±2 avant → après | Observation |
| --- | --- | --- |
| `circle_sizes` | 94,39697 → 94,34204 % | Recul réel : SSIM 0,987834 → 0,974624 ; MAE normalisée 0,006447 → 0,008638. Les contours du nouveau rendu restent visiblement moins lisses que la référence. |
| `concavepaths` | 98,79767 → 98,79833 % | MAE et SSIM légèrement meilleurs ; pas une identité bit à bit. |
| `crbug_640176` | 99,6832 → 99,6832 % | Même taux ±2, mais MAE 0,000391 → 0,000403 et SSIM légèrement moins bon. |
| `p3_ovals` | 88,44861 → 88,44792 % | Léger recul de MAE/SSIM également. |
| `rect` | Non comparable | Empreinte modifiée, référence absente ; aucune conclusion de fidélité. |

Le lot apporte une couverture fonctionnelle plus large, **pas une absence
de régression visuelle**. La sélection du parcours AA et la qualité des
contours, en priorité `circle_sizes`, constituent la prochaine correction
renderer avant tout élargissement supplémentaire du domaine.

Le test public historique de phase négative des pointillés échoue encore
sur `w5b.geometry.incompatible-plan`. L'A/B avec le constructeur historique
reproduit le même échec : le snapshot recopie `PathStrokeDashF64`, dont
l'égalité reste une identité d'objet ; la comparaison de styles du seal W5b
échoue. Ce défaut de validation, distinct de la géométrie des pointillés,
est à corriger séparément avec le témoin public existant, sans desserrer le
seal et sans ajouter de test d'infrastructure.

### Vérification du lot

Le parcours standalone conserve la provenance RECT/PATH et utilise la
géométrie `math` existante. Les réparations natives concernent la cible
logique resolve-only, le mapping 4× des consommateurs de masque, la
continuité MSAA jusqu'au resolve final, les masques hard blancs et la
compatibilité de pipeline avec l'attachement D24 existant. Les compilateurs
de sources W5/W6 ne reçoivent pas l'extension d'admission des rectangles.

Les trois suites publiques W6/W7 complètes (40 + 9 + 6 tests),
`GPUPlanSurfacePixelTest.W4dGeneral*` et
`GPUPlanSurfacePixelTest.W4e public hard*` (14 tests) passent :
**69/69, Gradle exit 0**. Un sélecteur élargi antérieur a donné **68/69**,
avec le seul défaut pointillé décrit ci-dessus ; aucun succès global des
tests n'est revendiqué. Astra a aidé au diagnostic/raccord natif difficile ;
la relecture indépendante Sol n'a trouvé aucun défaut confirmé du diff et
a vérifié séparément la cohérence du bilan complet.

Le rejeu avec PNG des témoins `child_sampling_rt`, `circle_sizes`,
`clip_strokerect`, `clipdrawdraw`, `cliplargerect` et
`matrixconvolution_color` conserve leurs empreintes du relevé complet.
Les références et les scores historiques ne sont pas réécrits.
W7, les gates W6 et la décision de merge restent ouverts.

## Décisions de pilotage

1. **Vérifier les scènes avant d'optimiser leurs scores.** L'audit de témoins
   réels a déjà trouvé `matrixconvolution_bigger` réduit à trois rectangles,
   contre une référence avec convolutions et texte ; `tinybitmap` fournit
   des octets RGBA avec alpha nul au lieu du texel rouge prémultiplié de
   [Skia](https://github.com/google/skia/blob/main/gm/tinybitmap.cpp).
   `imagefiltersunpremul` passe `"unpremul"` comme `sourceId`, pas comme
   `AlphaType`, et remplace le filtre Image de
   [Skia](https://github.com/google/skia/blob/main/gm/imagefiltersunpremul.cpp)
   par un drawImage. Ces défauts de port sont suivis séparément : corriger
   une scène ne constitue pas un gain du renderer à corpus identique.
   La révision Skia ayant produit les PNG n'est pas établie ici ; les liens
   upstream servent au diagnostic, pas à inventer cette provenance.
   L'audit du lot strokes relève aussi un piège partagé dans `GmCanvas` :
   ses transformations affines sont souvent appliquées aux coordonnées des
   paths avant le draw, sans transmettre la CTM et donc sans transformer
   simultanément la largeur du stroke. Son `drawColor(color)` passe par un
   rectangle transformé, contrairement au `clear` de
   [cliplargerect upstream](https://github.com/google/skia/blob/main/gm/scaledrects.cpp).
   Le port `nonclosedpaths` remplace aussi ses deux styles par `STROKE`.
   Ces scènes ne sont pas des oracles suffisants pour une correction du
   renderer : le lot utilise des témoins `Surface` directs et conserve les
   ports inchangés pour mesurer un delta à corpus constant.
2. **Strokes/hairlines standalone et AA : lot mesuré ci-dessus.** Les refus
   initiaux `width_invalid` (15 cas), `rect_anti_alias` (11) et
   `scalar_aa_not_promoted` (19) n'étaient pas des gains additionnables.
   Le bilan réel est de 41 rendus supplémentaires avec des reculs localisés.
   Priorité suivante : expliquer et corriger les contours AA de
   `circle_sizes`, puis réparer l'égalité des styles dash W5b ; préserver les
   41 nouveaux rendus et leurs scores pendant ces corrections. Ne pas
   remplacer un hairline par une largeur locale ni désactiver l'AA.
3. **Puis composition des layers et paths généraux.** `unsupported_child`
   est le premier refus de 51 cas, `fan_budget_exceeded` de 28 cas.
   Décomposer les refus de layer par opération enfant avant d'élargir leur
   contrat. Garder les contrôles de ressources ; les budgets ne sont pas
   relevés aveuglément pour augmenter le compteur de rendus.
4. Chaque correction du renderer doit montrer un avant/après sur les mêmes
   identités, références et scènes. Chaque correction de GM a son bilan
   distinct. Rejouer ensuite les 443 cas éligibles, y compris les refus et
   timeouts, avant de conclure à une convergence. La médiane et les cas
   proches de Skia complètent le taux de rendus disponibles.

## Fiabilité des validations

Le crash `133` des suites ciblées venait de la fermeture GLFW/AppKit depuis
un thread de shutdown JVM. Le rapport natif du 28 septembre à 13:15:17
indique `Must only be used from the main thread` dans la fermeture de fenêtre,
sur `Java: Thread-5`. Les deux suites disposent désormais explicitement du
runtime GPU en `@AfterAll`, sur le thread de test lancé avec
`-XstartOnFirstThread`, comme le runner Skia existant.

Validation réelle : **49/49 tests publics**, dont 40 W6 et 9 W7,
`:kanvas:test` **exit 0**. Cela corrige la terminaison de ces deux suites,
pas la politique de shutdown de tous les consommateurs du runtime.
Pas de nouveau test d'infrastructure : l'outil est exécuté sur les GMs
réelles et le cleanup est vérifié par les tests publics existants.

```sh
rtk proxy ./gradlew :kanvas:test --offline --console=plain \
  --tests 'org.graphiks.kanvas.surface.W7AaPathLayerSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest'

rtk proxy node refactor/waves/W07-gm-convergence/summarize-parity.mjs \
  <dossier-des-journaux> <nouveau-snapshot.json>
```

L'agrégateur refuse un corpus incomplet, des identités dupliquées ou des
sessions mélangeant commits/configurations. Les timeouts restent des lignes
ordinaires. La relecture indépendante du lot a demandé de préserver leurs
empreintes ; correction appliquée avant le relevé final. W7 et les gates W6
encore ouverts ne sont pas déclarés terminés par ce checkpoint.
