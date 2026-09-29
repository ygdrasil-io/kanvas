# Pilotage de la convergence Skia

Baseline : PR draft [#2411](https://github.com/ygdrasil-io/kanvas/pull/2411),
empilée sur [#2410](https://github.com/ygdrasil-io/kanvas/pull/2410).
Lot standalone : PR draft [#2412](https://github.com/ygdrasil-io/kanvas/pull/2412),
empilée sur #2411, routage standalone rect/path, renderer `718445e6e`.
Lot précédent : pointillés réparés, expérience AA retirée après mesure,
renderer `5f971f750`, PR draft [#2413](https://github.com/ygdrasil-io/kanvas/pull/2413)
empilée sur #2412.
Lot courant : cache de preuve CPU, renderer `b256b3d68`, PR draft
[#2414](https://github.com/ygdrasil-io/kanvas/pull/2414), empilée sur #2413.

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

## Lot pointillés et expérience AA retirée — 29 septembre 2026

Le correctif `adcf16eba` donne à `PathStrokeDashF64` une égalité structurelle
exacte et un `hashCode` cohérent, phase et intervalles inclus. Les copies
défensives restent inchangées ; le seal W5b n'est pas desserré. Le test public
historique de phase négative et une matrice littérale de trois pointillés
passent après avoir échoué avant la correction. Les 476 tests math geometry
passent également.

L'expérience AA `9d3355ec9` appliquait une précision géométrique de 1/16 de
pixel aux seuls remplissages racine AA, sans modifier le MSAA4. Les témoins
d'aire alpha passaient (rayon 1 : 2,008 → 2,996 ; rayon 8 : 195,043 → 199,012,
y compris après scale 2). `circle_sizes` remontait à SSIM **0,987049** contre
0,974624 dans #2412, encore sous les 0,987834 historiques.

Mais le corpus complet inchangé perdait **deux rendus**, `parsedpaths` et
`perspective_clip`, sur `Geometry(value=VertexLimit)` : 164 → 162. Le gate
statique à 255 arêtes winding est le premier suspect, pas un sous-motif
capturé : le diagnostic public agrège plusieurs limites. Après l'avis
ciblé d'Astra, l'expérience et ses trois nouveaux tests ont été retirés en
`5f971f750`. Leur code et leurs résultats restent récupérables dans
`9d3355ec9` et le [plan](aa-dash-repair-plan.md).

**Aucun gain AA n'est livré par ce lot.** Le retour à `.25` conserve donc la
régression de `circle_sizes` constatée dans #2412. Une marge GPU inventée,
une hausse de budget ou un retour silencieux à une approximation grossière
n'ont pas été utilisés pour sauver le compteur. La prochaine reprise doit
capturer le refus interne exact, puis traiter la borne stencil avec des
témoins winding 1 à plus de 255 arêtes et winding réel 256, inversions et
annulations incluses. Des tests pixels ne remplacent pas la preuve de
classification GPU manquante.

Le [snapshot final](dash-5f971f750.json), renderer
`5f971f750f4699adb1a7fb1cbe531e9383367641`, couvre les **631 mêmes identités**
et **443 éligibles** : **164 rendus, 142 comparaisons, 26 à ≥99 % et 36 à
≥95 % de pixels ±2/canal**. Les 164 empreintes RGBA et tous les résultats
terminaux sont identiques à #2412, sans gain ni perte de GM. Les trois
timeouts (`lattice2`, `ninepatch-stretch`, `vertices`) sont conservés à 30 s.
Les références, seuils, exclusions et scènes sont inchangés.

Par rapport à la baseline `d661f10c3`, les 41 gains de #2412 sont donc
préservés, avec les mêmes cinq anciennes images modifiées et la même médiane
appariée de 54,64 %. La médiane des 142 comparaisons reste 65,23 %. Durée
cumulée des cas : 189,97 s, hors démarrage Gradle/JVM, sans valeur de benchmark.
Le gain du lot est la sémantique publique des pointillés, pas une hausse
de la similarité du corpus.

### Validation du lot et limite de la suite complète

Sur le code final, **77/77 tests publics W4/W6/W7** et **476/476 tests math
geometry** passent, zéro skipped, Gradle exit 0. Les 77 incluent les 16
tests W7 stroke/routing, AA layer et dash, les 40 W6 layer W4/W5 et les 21
sélecteurs W4d/W4e hard, dont le témoin historique de phase négative.

```sh
rtk proxy ./gradlew :math:geometry:jvmTest --rerun :kanvas:test --offline --console=plain \
  --tests 'org.graphiks.kanvas.surface.W7StrokeRoutingSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W7AaPathLayerSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W7DashStrokeSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4d*' \
  --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4e public hard*'
```

La sélection élargie de l'expérience donnait 118/119 : l'échec
`W6aLayerBoundsSurfacePixelTest.emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers`
est reproduit avec les deux politiques `.25` et `.0625`, puis laissé intact.

Une tentative de `:kanvas:test` complet sur `5f971f750` est **inachevée** :
646 tests passent, 39 échouent et le test en cours est marqué skipped après
arrêt explicite du processus. Deux lectures de pile localisent le calcul
CPU dans `ColorRoundedGraphProofV1.prove`, appelé par
`W5dGradientAddressingSurfacePixelTest.sweepFullCoveragePreservesRequestedTileBudgetIdentity`.
Le worker avait consommé plus de 208 s de CPU après 263 s d'exécution ;
l'arrêt aboutit à l'exit natif 133 et Gradle 1. Ce n'est ni une absence de
GPU ni une suite verte. Aucun fichier de score/référence n'a changé.

Les 39 échecs observés se répartissent comme suit ; leur antériorité n'a
pas été établie individuellement dans ce lot :

| Suite | Échecs |
| --- | ---: |
| ImageTest / PictureTest | 1 / 1 |
| W5ePictureImageSamplingTest / W5hRuntimeEffectPictureTest | 5 / 1 |
| W6aLayerPictureTest / W6bFilterPictureTest | 1 / 2 |
| DisplayOpSceneAdapterTest / SceneRoundTripTest | 1 / 4 |
| GPUPlanSurfacePixelTest / SceneRecordingScopeTest | 4 / 1 |
| SurfaceSceneSnapshotTest / W5aMaterialSurfacePixelTest | 4 / 1 |
| W5bBlendSurfacePixelTest / W5cGradientSurfacePixelTest | 2 / 7 |
| W5dGradientAddressingSurfacePixelTest | 4 |

Les XML de cette tentative sont conservés localement dans
`/private/tmp/kanvas-w7-full-suite-5f971f750-20260929` ; ils contiennent aussi
un échec synthétique du runner Gradle, distinct des 39 tests échoués.
La pile est conservée dans `/private/tmp/kanvas-w7-w5d-stall-5f971f750.txt`.
La PR reste draft, sans promesse de merge readiness.
La relecture finale indépendante Sol ne relève aucun nouveau défaut du diff
livré et confirme les chiffres des snapshots et XML ; elle valide la
publication draft, pas le merge. L'origine des 39 échecs globaux, la
réparation AA et les domaines font/codec/ports restent ouverts ou hors scope.

## Lot cache de preuve CPU — 29 septembre 2026

Le [plan](proof-evaluation-plan.md) borne la correction à la mémoïsation
des évaluations scalaires. Une clé immutable conserve les identités des
scalars, les bits exacts des bornes et le scope Noise (région/octave).
Elle projette seulement la **clé de cache** sur les dépendances transitives
conservatrices ; les conditions réellement évaluées restent complètes,
y compris la présence des Add matérialisés. Les régions opaques
Image/Noise/GradientStop et leurs parents gardent le contexte complet.
Ni calcul numérique, ni enveloppe, budget ou règle d'admission n'est élargi.
Les facts de la première évaluation restent présents, sans rejouer leurs
doublons sur les hits ; la multiplicité textuelle des identités opaques
n'est pas un contrat conservé.

La première tentative, à contexte complet, dépassait encore 60 s.
L'analyse Astra a identifié les conditions affines externes sans incidence
sur les coordonnées antérieures ; la projection conservatrice traite
cette redondance. La revue Sol du code et sa relecture des preuves sont
approuvées. Les témoins permanents restent publics, sans test d'infrastructure.

### Résultat mesuré à corpus constant

Le [snapshot](proof-b256b3d68.json) conserve les 631 identités, 443 éligibles,
133 exclusions font, 54 codec et une quarantaine. Scènes, références,
empreintes, dimensions, seuils et limite de 30 s sont inchangés.

| Mesure | `5f971f750` | `b256b3d68` |
| --- | ---: | ---: |
| Rendus disponibles | 164 | 165 |
| Comparaisons | 142 | 143 |
| Non comparés / dimensions incompatibles | 14 / 8 | 14 / 8 |
| Échecs de rendu / setup | 226 / 50 | 227 / 50 |
| Timeouts | 3 | 1 |
| Cas à ≥99 % de pixels ±2/canal | 26 | 26 |
| Cas à ≥95 % | 36 | 36 |
| Médiane des comparaisons courantes | 65,23469 % | 65,410625 % |

Les **164 anciens rendus gardent leur empreinte RGBA exacte** : aucune perte,
aucun pixel modifié. La médiane appariée des 142 anciennes comparaisons
reste **65,23469 %** ; la hausse de médiane globale vient de l'ajout d'un cas,
pas d'une amélioration de leurs pixels.

- `ninepatch-stretch` passe de timeout à rendu comparé en **26,168 s**,
  avec **78,14951 %** de pixels ±2/canal. Il reste proche de la limite :
  ce relevé unique ne prouve pas une marge robuste sur d'autres hôtes.
- `lattice2` passe de timeout à refus en **0,527 s** :
  `w5b.geometry.incompatible-plan: Invalid W5a source authority`.
  C'est un diagnostic désormais accessible, pas un rendu gagné.
- `vertices` reste timeout à 30 s. Son résultat est persisté, puis le
  corpus reprend à l'index 608 ; aucune ligne n'est supprimée.

Pour les deux anciens timeouts terminés, `scopeReason` et `scopeOwner`,
absents de la ligne timeout, sont désormais explicitement `null` : quatre
enrichissements de champs, sans changement de scope. Les journaux locaux
sont dans `/private/tmp/kanvas-w7-proof-parity.suk2dP` ; leur agrégation
vérifie les 631 indices uniques et le même renderer/configuration.

### Tests publics et limites

Les deux nouveaux tests W7 passent en **0,163 s** dans la sélection
adjacente, qui compte **20/20 réussites**, zéro skipped, Gradle 0.
Le Sweep hard vérifie quatre TileModes et vingt paires matrix/clamp ;
le témoin bicolore vérifie les deux ordres non commutatifs, avec pixels
littéraux, Render/Readback et répétition sur la même Surface.
Ce dernier est un contrôle de non-régression, déjà vert avant projection,
pas un RED inventé. Le Sweep fournit le RED causal (timeout de 60,047 s).

Le test historique W5d auparavant bloqué termine en **1,756 s**, mais
**échoue** sur `unsupported.material.composed.numeric-domain-unbounded` ;
ses assertions restent intactes. Une sonde AA RRect sans wrapper,
strictement identique sur base et correctif, reproduit ce refus dans les
deux versions. Un premier contrôle avait accidentellement utilisé Rect
au lieu de RRect : sa conclusion a été rétractée, et aucun correctif
numérique n'a été fondé sur cette comparaison non équivalente.

Les **476 tests math geometry passent**. La tentative Kanvas complète,
bornée globalement à **240 s**, reste **inachevée et rouge** :
**779 tests observés = 728 réussites + 50 échecs + 1 interrompu**.
Le runner ajoute un échec synthétique de shutdown, distinct de ces 50.
L'arrêt atteint `W5eImageShaderSurfacePixelTest.cubicTileBoundariesMatchOracle`,
alors que la suite progressait ; ce n'est pas la preuve d'un nouveau stall.
Gradle termine 1 après 4 min 1 s ; le worker est ensuite absent.

Les 686 identités du relevé précédent sont toutes retrouvées :
646 restent vertes, les 39 échecs restent rouges, et le Sweep interrompu
atteint maintenant son refus. Aucun passage vert→rouge n'est observé dans
cette intersection. Parmi les tests atteints en plus, dix autres échecs
sont observés ; leur antériorité n'est pas établie individuellement.
La répartition reprend le tableau précédent, avec **14 échecs W5d** au lieu
de quatre, et **un W5eImageShader** supplémentaire. Ces derniers concernent
les refus numériques/lanes, les budgets/diagnostics et l'attente historique
de refus du hairline image shader ; ils restent ouverts, sans changement
d'oracle. Les XML sont archivés dans
`/private/tmp/kanvas-w7-proof-full.TzcJf8/{kanvas-test,math-geometry}`.

```sh
rtk proxy ./gradlew :kanvas:test :math:geometry:jvmTest --rerun --offline --console=plain \
  --init-script /private/tmp/kanvas-w7-proof-full-timeout.gradle --continue
```

Le lot justifie une publication draft, pas une clôture de W7 ni une merge
readiness. La suite doit encore être complétée par sélections bornées ;
les refus AA de gradient, les erreurs de budget/autorité, le timeout
`vertices` et la réparation géométrique AA restent des travaux distincts.
La relecture finale indépendante Sol confirme les comptes des XML, les
631 identités et les 164 anciennes empreintes RGBA ; elle ne relève aucun
défaut confirmé du lot et valide sa publication draft, pas le merge.

### Validation complémentaire par lots bornés

Dix sélections supplémentaires de `:kanvas:test` ont été exécutées en série,
sans modification persistante du renderer, des tests ou des oracles. Chaque
tâche est bornée à 240 s ; cette limite d'exécution ne modifie aucun budget
du renderer. Les résultats ci-dessous sont **par exécution, non additionnables
en un total de tests uniques** : des reprises recouvrent des cas déjà observés.

| Lot | Réussites | Échecs d'assertion | Cas interrompus | Terminaison |
| --- | ---: | ---: | ---: | --- |
| 01 — W5e méthodes manquantes | 14 | 3 | 0 | worker natif 133 après assertions |
| 02 — W5e frontière cubic | 1 | 0 | 0 | worker natif 133 après assertions |
| 03 — W5f Surface | 83 | 16 | 1 | limite 240 s |
| 04 — W5f image filter | 92 | 0 | 0 | worker natif 133 après assertions |
| 05 — W5g composed | 48 | 2 | 1 | limite 240 s |
| 06 — contrats GPU, API/blend, text/types existants | 1 990 | 1 254 | 0 | Gradle 1, assertions |
| 07 — W6/W7 Surface | 411 | 1 | 0 | Gradle 1, assertion |
| 08 — W5g convergence/noise, W5h convergence partiel | 89 | 4 | 1 | limite 240 s |
| 09 — W5h géométrie, image origin partiel | 671 | 0 | 1 | limite 240 s |
| 10 — reprise W5f matrix/table | 16 | 0 | 0 | worker natif 133 après assertions |

Les interruptions et erreurs synthétiques du runner ne sont pas des échecs
d'assertion. Trois conteneurs paramétrés marqués `skipped` sont aussi exclus
de la colonne « cas interrompus ». Les 57 réussites W5gNoise du lot 08 sont
attestées par le listener JSONL, son XML étant vide après l'arrêt. Le lot 09
comprend **598/598 cas de géométrie W5h réussis** ; ses six noms d'affichage
dupliqués dans ImageOrigin désignent des invocations distinctes et ne doivent
pas être fusionnés. Un `SUITE_END` lors d'un timeout ne certifie pas que toutes
les méthodes de la classe ont été exécutées.

La sélection W6/W7 atteint 38 classes et **411/412 réussites**. Son seul échec
est `emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers` :
le test attend `IllegalStateException`, mais le rendu termine avec `true`.
L'attente n'a pas été changée. La sélection large atteint 86 classes ;
`GPUAllApiBlendSurfaceTest` concentre **1 071 des 1 254 échecs**, sans que cela
prouve une cause unique. Cette dette nouvellement mesurée n'a pas de baseline
individuelle complète : elle n'est pas présentée comme autant de régressions
du cache. L'exécution de tests existants text/types n'élargit pas le périmètre
de réparation, qui exclut toujours fonts et codecs.

La validation globale reste **incomplète** : W5fGradientInterpolation,
W5gComposedMaterial, W5hConvergence et W5hImageOrigin ont encore des méthodes
ou variantes non exécutées ; W5hRuntimeEffect et W5hTextVertices ne sont pas
atteints dans ces lots. Le rejeu du seul template matrix/table du lot 10 ne
ferme pas la classe GradientInterpolation. Le dry-run ne fournit pas un
dénominateur fiable pour les invocations paramétrées et les factories.

Archives XML, résultats binaires et événements :
`/private/tmp/kanvas-w7-remaining.IzRmgJ/batches/`. Les événements des lots
06–10 sont du JSONL malgré le suffixe `.tsv` ; ceux du lot 05 sont altérés par
un échappement du listener et ne servent pas à certifier la couverture.
Les XML restent exploitables. Aucun test d'infrastructure n'a été ajouté,
aucun seuil ni exclusion n'a été changé et aucun nouveau score GM n'est
revendiqué. Ces résultats justifient un triage ciblé, pas une suite verte.
La relecture indépendante Sol recoupe ces comptes dans les XML et le listener,
y compris les conteneurs synthétiques et les 57 cas JSONL-only. Elle ne relève
pas de problème important dans ce bilan et valide sa publication draft,
sans valider le merge.

La prochaine correction de comportement vise d'abord l'autorité image/opacité
de `lattice2`, dont la cause est localisée ci-dessous ; le raffinement de preuve
Sweep vient ensuite. Les portions de validation manquantes restent explicites,
sans devenir un prétexte pour modifier leurs oracles ou multiplier les relances
globales interrompues.

### Diagnostic numérique AA et prochain correctif borné

Le refus Sweep est reproduit par une `Surface(17, 1)` avec un RRect AA
débordant et un Sweep de 0 à 360 degrés, sans wrapper, sur la base comme
sur le correctif de cache. La preuve reçoit le rectangle raster conservateur,
pas seulement les fragments de couverture non nulle. Le graph flush les
deltas subnormaux vers zéro, puis remplace les axes nuls par un avant
`Atan2`. Les axes sont donc normaux, mais le hull de `EagerSelect` perd cette
disjonction et invente zéro entre les valeurs atteignables. Le rejet provient
de la précondition de normalité d'`Atan2`, pas du GPU ou du cache.

L'avis ciblé Astra recommande une analyse auxiliaire locale des classes F32
possibles : zéro (signé inclus), subnormal non nul, normal. Le fallback reste
l'intervalle conservateur. Seules les sélections et les comparaisons de
`abs(v)` à zéro ou `MIN_NORMAL`, portant sur la même identité scalaire,
raffineraient ces classes. Il ne faut jamais déduire « normal » de `v != 0`
seul : une comparaison peut subir le FTZ (flush-to-zero).

La réparation proposée conserve la validation des **deux bras eager**,
les contextes, la réassociation des Add, le cache et tous les contrats
numériques. Pour deux axes certifiés normaux, `Atan2` découperait leurs
bornes en deux signes au plus, soit quatre rectangles, appliquerait à chacun
la même enveloppe 4096 ULP, puis réunirait les résultats. Aucune modification
du shader, de la tolérance, du raster ou des budgets n'est prévue. Ce design
n'est pas encore implémenté et aucun gain de GM ne lui est attribué.

Les témoins doivent rester publics : Sweep non uniforme en RRect AA et
PATH_STROKE, quadrants/axes/coupure angulaire, mêmes pixels au second rendu,
oracle existant inchangé, témoin rouge et vingt wrappers, refus précis puis
récupération. Les limites zéro/subnormal/normal doivent être exercées via
des transformations publiques admises ; aucun test d'infrastructure ajouté.

Radial et Conical restent distincts : leur `Sqrt` guardé par égalité à zéro
ne prouve pas l'absence de subnormaux. L'absence de subnormal dans une trace
ne suffirait pas non plus à le démontrer. Le premier nœud fautif du Conical
reste à capturer ; ses dénominateurs corrélés constituent une autre hypothèse.
La réparation de ces familles n'est pas incluse dans le correctif Sweep.

### Refus d'autorité de lattice2 localisé

Une instrumentation temporaire, retirée après le rejeu du seul index 343,
localise le refus dans `W5aMaterialPlanVersionWitnessV2.issue`. La commande 4
porte `MaterialV4(ref=5, coordinates=V3)` ; son stage original et rebasé,
leur identité canonique et les contrôles proof/structural/layout sont valides,
avec 864 octets d'uniformes. Le programme racine déclare pourtant
`versionI32=1`, donc le witness rejette l'autorité V4.

Le producteur est `FrameSourceLayoutV4.prepareAndFinish` : il ajoute
`MaterialProgramPlan.OpacityV1` autour de l'image V3. `OpacityV1` propage
aujourd'hui la version 4 seulement pour un enfant V4 ; un enfant image V3
retombe donc à V1 malgré la preuve de source V4 scellée sur le wrapper.
La correction devra construire un programme d'opacité V4 authentique pour
cette chaîne image V3, avec graph et bindings cohérents, puis vérifier les
pixels et l'opacité appliquée une seule fois. Il ne s'agit pas d'admettre
arbitrairement V1 côté renderer ni de supprimer le contrôle du witness.
La relecture Sol confirme cette causalité. Aucun correctif de comportement
n'est livré ici ; localiser le premier refus ne prouve pas qu'il soit unique.

Le relevé instrumenté, distinct de la mesure de corpus, est conservé dans
`/private/tmp/kanvas-w7-lattice-probe.tjMJ5N` : `[343,344)` exécute un GM,
reproduit le refus, et termine avec Gradle 0. Le premier intervalle
`[343,343)` était vide et n'est pas compté. Après retrait exact des logs,
`:gpu-renderer:compileKotlin :gpu-renderer:jar` termine avec Gradle 0 ;
aucun diff de source ou de test ne reste. Les contrôles restent stricts.

## Lot image/opacité — 29 septembre 2026

Le renderer `bef3af6faabcbbd205fe711e54ba38de1c4e4ac1`, sur
`codex/w7-image-opacity-authority` empilée sur #2414, corrige la cause localisée
ci-dessus. `OpacityV1` propage désormais V4 lorsqu'il enveloppe précisément
`ImageMaterialProgramV3`, comme il le faisait déjà pour un enfant V4. Le graph
numérique V4 existant est donc sélectionné ; l'opacité V1 non-image, les bindings,
le scellement de preuve et le witness strict du renderer sont conservés.
Aucun shader, budget ou contrat numérique n'est assoupli.

Le nouveau `W7ImageOpacitySurfacePixelTest` reproduit le refus d'autorité
avant modification, puis passe après correction. Il utilise une image issue
d'un snapshot public et un frame mêlant image rect, lattice opaque et lattice
avec alpha. Les variantes AA/hard-edge, `SRC_OVER`/`SRC_ATOP`, cellules
default/fixed/transparent et rendu répété vérifient les pixels au moyen de
l'oracle image existant, inchangé. Aucun test d'infrastructure n'est ajouté.
La revue Sol ne trouve pas de défaut bloquant ; elle relève une amélioration
de couverture non bloquante : ces nouveaux bords sont entiers. Le témoin
existant `latticeAdjacentCellsKeepFullInteriorCoverage` couvre séparément
les bords fractionnaires, pas leur combinaison avec cette opacité.

### Mesure à corpus constant

Le [snapshot](image-opacity-bef3af6fa.json), comparé à `proof-b256b3d68.json`,
conserve les 631 identités, les 443 éligibles, les 133 exclusions font,
54 codec et la quarantaine `jpg-color-cube`. Aucun port GM, PNG de référence,
seuil ni score historique n'est changé.

| Mesure | Base #2414 | Image/opacité |
| --- | ---: | ---: |
| Rendus disponibles | 165 | 166 |
| Comparaisons | 143 | 144 |
| Échecs de rendu / setup | 227 / 50 | 226 / 50 |
| Cas à ≥99 % / ≥95 % de pixels ±2/canal | 26 / 36 | 26 / 36 |
| Timeouts à 30 s | 1 | 1 |

`lattice2` est le seul changement d'issue : huit opérations dispatchées,
zéro refus, **54,0875 %** de pixels à ±2/canal, 1,063 s sur ce relevé.
Son seuil déclaré de 50 % est franchi, sans être relevé ni abaissé ; cela
ne signifie pas une parité visuelle. **Les 165 anciens rendus conservent
exactement leur empreinte RGBA**. La médiane appariée des 143 anciennes
comparaisons reste 65,410625 % ; la médiane globale devient 65,23469075520833 %
par ajout du nouveau cas, sans dégradation des anciens pixels.
`ninepatch-stretch` reste rendu en 23,261 s ; `vertices` reste timeout.

Les trois tranches `[0,607)`, `[607,608)` et `[608,631)` sont exécutées en série
sur le commit, avec 30 s par GM. Gradle termine respectivement 0, 1 (worker124
après persistance du timeout `vertices`), puis 0. Journaux immuables :
`/private/tmp/kanvas-w7-image-opacity.YmtXjI/corpus`. La sonde antérieure
`lattice2-dirty` utilise un code non committé et n'entre pas dans ce snapshot.

### Validation et réserves

La sélection finale isolée donne **9/9 tests publics réussis, Gradle 0** :
nouveau témoin, deux contrôles W7 de preuve, deux W5a d'opacité, un W5f de
filtre couleur et trois W5f de filtres image. La suite Kanvas générale est
également rejouée isolément avec une borne de 240 s : **779 cas = 728 réussites
+ 50 échecs + 1 interrompu**, Gradle 1. Les 779 identités et résultats sont
identiques à la tentative de la base, sans nouveau passage vert→rouge dans
cette intersection. `cubicTileBoundariesMatchOracle` est interrompu ; la suite
globale reste incomplète et rouge. L'échec synthétique du runner est distinct.

Les XML contiennent 742 cas ; les 37 manquants, dont sept échecs, sont conservés
dans les événements JUnit JSONL écrits directement pendant ce run. Le listener
et le total console concordent sur 779. Archives finales :
`/private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated-focused` et `isolated-full`.
Les avertissements JVM/Gradle et la dette de suite restent visibles.

Une annonce prématurée de fin du sous-agent avait fait chevaucher sa tentative
générale avec le début d'un contrôle ciblé. Son archive initiale était aussi
une copie obsolète de W5e ; l'attribution du code133 à la suite générale était
erronée. Ces deux runs sont écartés de la validation finale. Après confirmation
de leur terminaison, les contrôles et la suite générale ont été rejoués en
série, aux bornes inchangées, avec sorties d'archives dédiées. Les 27 assertions
W5e suivies de133 restent une observation préliminaire, pas une suite verte.

Le lot résout le refus d'autorité de `lattice2`, pas son écart de fidélité.
La prochaine correction est la preuve Sweep AA décrite plus haut ; Radial,
Conical, les défauts géométriques AA et les autres gates restent distincts.
La publication demeure draft, sans clôture W7 ni autorisation de merge.

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
   L'égalité des styles dash W5b est réparée dans le lot suivant. La précision
   AA seule améliore `circle_sizes` mais fait perdre deux nouveaux rendus :
   l'expérience est retirée après revue Astra. Priorité suivante : capturer
   le refus géométrique exact et choisir une correction bornée de la limite
   stencil ; préserver les 41 nouveaux rendus. Ne pas
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
