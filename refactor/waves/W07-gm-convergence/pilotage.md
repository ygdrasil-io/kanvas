# Pilotage de la convergence Skia

Baseline : PR draft [#2411](https://github.com/ygdrasil-io/kanvas/pull/2411),
empilée sur [#2410](https://github.com/ygdrasil-io/kanvas/pull/2410).
Lot standalone : PR draft [#2412](https://github.com/ygdrasil-io/kanvas/pull/2412),
empilée sur #2411, routage standalone rect/path, renderer `718445e6e`.
Lot précédent : pointillés réparés, expérience AA retirée après mesure,
renderer `5f971f750`, PR draft [#2413](https://github.com/ygdrasil-io/kanvas/pull/2413)
empilée sur #2412.
Lot cache de preuve CPU : renderer `b256b3d68`, PR draft
[#2414](https://github.com/ygdrasil-io/kanvas/pull/2414), empilée sur #2413.
Lot image/opacité : renderer `bef3af6fa`, PR draft
[#2415](https://github.com/ygdrasil-io/kanvas/pull/2415), empilée sur #2414.
Lot preuve Sweep AA : renderer `49d8224d3`, PR draft
[#2416](https://github.com/ygdrasil-io/kanvas/pull/2416), branche
`codex/w7-sweep-aa-proof`, empilée sur #2415.
Lot adaptateur Rect+CTM : code `d45904e0b`, branche
`codex/w7-layer-source-routing`, PR draft
[#2417](https://github.com/ygdrasil-io/kanvas/pull/2417), empilée sur #2416.
Lot précédent : source AA racine, renderer `470f62e63`, branche
`codex/w7-root-aa-source`, PR draft
[#2418](https://github.com/ygdrasil-io/kanvas/pull/2418), empilée sur #2417.
Lot précédent : couverture AA filtrée, renderer `82893045c`, branche
`codex/w7-aa-mask-coverage`, draft
[#2419](https://github.com/ygdrasil-io/kanvas/pull/2419) empilée sur #2418.
Lot précédent : Rect stroke AA dans un mélange racine, renderer `ff628a94d`,
branche `codex/w7-mixed-root-aa-rect`, draft
[#2420](https://github.com/ygdrasil-io/kanvas/pull/2420) empilée sur #2419.
Lot courant : politique alpha du LinearGradient, renderer `09d9574b5`,
branche `codex/w7-gradient-alpha-mode`, future draft empilée sur #2420.

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

## Lot politique alpha du gradient — 30 septembre 2026

Le [design](gradient-alpha-design.md) et le [plan](gradient-alpha-plan.md)
livrent `GradientAlphaMode.STRAIGHT/PREMULTIPLIED` pour LinearGradient.
STRAIGHT reste le défaut et conserve ses identités historiques. Le nouveau
mode est admis seulement dans l'espace effectif SRGB avec tile CLAMP ;
les feuilles composées sont contrôlées avant réduction, y compris à un stop.
Le transport paint/IR/Picture/V4 conserve le mode sans doubler les slabs de
stops. La recipe F32 appartient au graphe partagé preuve/WGSL, sans formule
indépendante de l'émetteur. Picture16/schema10 écrit les wire IDs0/1 ;
la façade conserve explicitement les versions13/14/15.

Prérequis natif borné : blending désactivé (`blend=null`) seulement pour
PremulSrc, single-sample et programme direct authentifié, dont la route
garantit coverage None. AA, masque, clip analytique et destination-read ne
reçoivent pas cette optimisation. Le template, son identité et le descripteur
représentent fidèlement l'absence de blending ; les contrôles d'autorité
restent en place. Ni GM/adaptateur, référence, seuil, score historique,
exclusion, plafond de budget, enveloppe numérique ni composition de Surface
n'est changé.

### Preuves publiques et corrections de review

Le RED causal `red-src` contient deux vrais écarts de pixels : R92 contre
187–188 pour blanc opaque→noir transparent, R80 contre108–109 pour
rouge alpha128→bleu alpha64. Les contrôles STRAIGHT passent. Les essais
`red2` (oracle non borné avant GPU) et `red3` (destination incohérente) ne sont
pas des RED valides. La fixture Picture15 provient du writer historique,
capturée avant modification ; ses583 octets et sa base64 ont été comparés
au XML de capture. L'assertion volontaire d'export quitte1, pas un succès.
Le replay réel direct/décodé, les mutations après capture, les alphas zéro
et1/255, stops2/16/17, hard stops, axe dégénéré CLAMP t=1, wrappers et refus
transactionnels sont couverts par la Surface publique.

Le budget mixte est dérivé **avant essai** :
`B=4+256+16384+4096+4096+2×(112+48)+64=25220` octets.
Les deux programmes V4 partagent un slab de64 octets. B passe, B−1 refuse
avec `resource.material.gradient.stop-budget`, sentinel intact ; discard et
deux rendus sains vérifient la récupération. Aucun seuil n'a été recherché
par essais successifs.

La première review Sol trouve quatre Important et un Minor : garde bypassée
en capture composée, identités STRAIGHT modifiées, Picture13 perdu, témoin
mixed-AA non discriminant et liste de blend vide acceptée pour destination.
Le commit `032cd9466` corrige ces cinq points. La re-review Sol de
`1ce59fdcc..032cd9466` approuve conformité et qualité : cinq constats clos,
zéro Critical/Important/Minor, aucune nouvelle casse trouvée. Le mixed-AA compare désormais
un vrai pixel intermédiaire SrcOver blanc opaque→gris204 transparent à t=.5 :
STRAIGHT RGB168–169 contre PREMULTIPLIED187–188, alpha127–128. La première
valeur bornée/disjointe est figée après un tableau CPU, **avant** rendu GPU ;
aucune attente n'est copiée des pixels observés. Le stroke AA sibling et ses
trois contrôles restent présents.

Validation finale ciblée `fix1-final-tests-b` : **26/26**, XML complets,
wrapper/Gradle0, sans terminal native133. Répartition : alpha13, mixed-rootAA7,
fractional Rect1, trois contrôles W5f et deux fixtures Picture13/schema7.
Les méthodes schema7 conservent leurs noms pour l'appariement historique ;
seules leurs attentes obsolètes du writer14/schema8 deviennent16/schema10.
Cela ne constitue pas un gain de rendu GM.

### Réserves conservées

Step0 a été validée après le premier GREEN alpha, contrairement au séquencement
prescrit ; les contrôles ont ensuite été rejoués sur la portée native corrigée.
Aucun RED natif One/Zero n'est revendiqué. Un contrôle exploratoire SRC+AA
refuse au préflight (`Every material packet must have its sealed geometry
host template`) : retiré sans ouvrir l'admission. **Les pixels SRC à couverture
partielle AA ne sont donc pas validés** par ce lot.

Le run voisin W5c compte22/27 assertions PASS, puis executor133/Gradle1.
Les cinq identités déjà en échec sur le parent sont
`conicalGradientMasksFragmentsWithoutValidRoot`,
`conicalGradientCoversFourLanesAndSelectsLargestValidRoot`,
`mixedGradientFramePreservesOrderRangesOpacityAndBlend`,
`radialGradientCoversFourGeometryLanes` et
`conicalGradientCoversAllDegeneracyBranches`.
Les anciens runs W5f (sélections1/2/3) ont leurs assertions PASS mais terminent
aussi native133 : ils ne sont pas verts et leur cause native commune n'est
pas établie. Le dernier run combiné exit0 n'efface pas ces réserves.
Warnings JVM native-access/Unsafe et dépréciations Gradle restent présents.
Archives : `/private/tmp/kanvas-w7-alpha-mode.vpi7cG/`.

### Arbitrages

1. Design décidé dans la délégation de pilotage utilisateur, avec review :
   coût en cas d'erreur, reprise réversible du design/code sur draft.
2. Livraison end-to-end atomique, pas un flag public sans comportement :
   coût, review plus large et éventuel découpage si couplage réel.
3. Recipe F32 propre, partition exacte des alphas et calcul depuis leur minimum :
   coût, reprise de preuve/intégration ; pas d'identité bit-à-bit Skia promise.
4. Témoins SRC source seule et prérequis natif borné : coût, risque de régression
   SRC/template à contrôler ; témoin numérique sur destination blanche différé.
   Aucune enveloppe ni authentification n'est assouplie.

### Globale bornée et régressions Picture corrigées

La seule globale `global-240` sur `032cd9466` atteint725 identités :
**671 PASS,53 FAIL,1 interrompu**, sans doublon ; wrapper124/enfant143 après
TERM à240s, XML non finalisés. Elle ne prouve pas une suite verte ni complète.
Les708 identités du parent sont toutes appariées :13 anciens succès deviennent
des échecs Picture, trois anciens échecs passent (deux fixtures schema7 et le
refus de version inconnue), et `generalCoordinateUniformBudgetRefusesPreciselyAndRecovers`
passe après l'interruption parente. Les17 cas supplémentaires comptent15 succès,
un échec et un interrompu (`cubicDrawImageMatchesMitchellNetravaliOracle`).
Leur échec `excludedLinearPaintLanesPreservePreparedRefusals` était déjà présent
dans le run AA-mask antérieur, non atteint dans le relevé parent immédiat.

Les13 régressions Picture mêlent attentes de header devenues obsolètes et vrais
retours `null` au décodage public. La cause est `ArchiveReader.clip()` :
le writer schema10 émet toujours `clipTransformV2`, mais le reader ne reconnaît
que schemas2..9. Le commit `fadbd80e3` ajoute10 à cette branche et met à jour
les seules attentes du writer courant16/10 dans les13 cas. Aucun format de
clip, fixture historique, nom de méthode ou assertion sémantique n'est modifié.

Le témoin public isolé `fix2-red-picture-enums` confirme le défaut avant patch
(un échec requireNotNull, Gradle1). Le run `fix2-final-controls-regressions`
valide ensuite **39/39 tests sur12 classes, XML complets, Gradle0 en26s**, sans
native133 : les13 régressions et les26 contrôles précédents. Le contrôleur
a vérifié exit/log/XML indépendamment du rapport. La globale ci-dessus reste
le résultat **avant ce dernier correctif** ; aucune seconde globale ni garantie
sur le reste de la suite n'en est déduite. Ces39 tests ne sont pas un gain GM.
La re-review Sol de `032cd9466..fadbd80e3` approuve conformité et qualité,
les deux constats sont clos sans nouveau Critical/Important/Minor.

### Corpus constant après correction

Le [snapshot631](gradient-alpha-09d9574b5.json) étiquette exactement
`09d9574b5c10be782109efecc73efd53d4174759`, après le correctif legacy décrit
ci-dessous. Les631 identités,443 éligibles,
empreintes de références PNG, scopes, seuils, dimensions et paramètres
restent identiques à [la base](mixed-root-ff628a94d.json). Les trois tranches
`[0,607)`, `[607,608)`, `[608,631)` sont sérialisées, exits Gradle0/1/0 ;
le1 est le processus de mesure124 après timeout30s de `vertices`, conservé
au dénominateur. La première tranche termine en1m57, sans valeur de benchmark.

| Indicateur | Parent #2420 | Alpha explicite |
| --- | ---: | ---: |
| Rendus / comparaisons | 198 / 176 | 198 / 176 |
| Cas à≥99% / ≥95% des pixels ±2 | 36 / 49 | 36 / 49 |
| Échecs rendu / setup | 194 / 50 | 194 / 50 |
| Non comparés / dimensions incompatibles | 14 / 8 | 14 / 8 |
| Timeouts | 1 | 1 |
| Médiane des176 comparaisons | 71,7349% | 71,7349% |

**Les198 anciennes empreintes RGBA sont identiques, aucun rendu perdu ou
nouveau, aucun changement d'issue ni de diagnostic.** Aucun GM n'active
encore le nouveau mode ; cette livraison apporte un contrat public testé,
pas un gain de parité mesuré. Les journaux sont dans
`/private/tmp/kanvas-w7-alpha-mode.vpi7cG/corpus-final`. Le checkpoint préalable
`fadbd80e3` donnait déjà les mêmes198 empreintes dans `corpus` ; son snapshot
intermédiaire redondant est retiré du checkout, récupérable dans le commit
documentaire `08a3fd8dd`. L'agrégateur contrôle
complétude/unicité et métadonnées ; la comparaison appariée vérifie aussi
les références, scopes, scores et empreintes, pas seulement les compteurs.

La revue finale architecturale est confiée une fois à Astra, les reviews de
tâche restant Sol. Sur `c80e5b56d..08a3fd8dd`, elle confirme les preuves
39/39 et corpus invariant, mais trouve un Important : les voies legacy du
mapper et des normalisations V1/V2 ignorent encore `alphaMode`. Un Rect stroke
non-AA à CTM identité peut atteindre le descriptor historique et rendre
STRAIGHT à la place de PREMULTIPLIED. Aucune reproduction native n'est attribuée
à cette review statique. Le contrôleur déclenche une seule correction finale.

Le RED public `legacyStrokeRouteRefusesPremultipliedBeforePublicationAndRecovers`
reproduit ensuite le défaut : `readPixels` retourne true au lieu du refus,
un échec XML/Gradle1. Le commit `09d9574b5` ferme les frontières legacy du
mapper, des normalisations V1/V2 (wrappers inclus) et de l'admission stroke,
avec `unsupported.material.gradient.alpha-mode`, avant réduction un-stop.
Aucune route legacy n'est promue en V4, STRAIGHT garde son contrat.

La sélection finale `finalfix-targeted46-w6e` passe **46/46,13 classes XML,
Gradle0 en18s**, zéro échec/skip/doublon. Les39 identités antérieures sont toutes
présentes, avec le nouveau refus/sentinel/récupération et six contrôles
`W7StrokeRoutingSurfacePixelTest`. Le contrôleur vérifie aussi les identités,
pas seulement le compte : la première sélection45 avait omis W6e. Son run
isolé de complément passe une assertion puis termine native133/Gradle1 ;
il reste **non vert**, même après le succès du run combiné46. Le shutdown
isolé de W6e n'est pas corrigé par ce lot.

Le corpus final sur `09d9574b5` est invariant comme détaillé ci-dessus.
La re-review finale Sol de `08a3fd8dd..09d9574b5` clôt le finding :
conformité et qualité PASS, zéro Critical/Important/Minor résiduel. Le cycle
reste une seule correction finale suivie d'une seule re-review ciblée.
SRC partiel AA, dettes globales/native133, composition,
port GM et backends GPU non exécutés restent des limites explicites.
La suite prévue est le contrat de composition de Surface, puis le port GM
dans un lot distinct. W7 reste ouvert ; aucune autorisation de merge.

## Audit alpha et domaine de composition — 29 septembre 2026

L'[audit causal de `alphagradients`](alphagradients-audit.md) distingue deux
contrats manquants : choix premul/unpremul du gradient et domaine de
composition de Surface. Sur les 184 704 pixels intérieurs sondés, la référence
suit straight gauche / premul droite et SrcOver sRGB encodé ; Kanvas suit
straight des deux côtés et SrcOver linéaire. Chaque modèle retrouve les RGB
de son image à un octet près. Les différences d'AA du port restent distinctes.
La diagonale est conforme, contrairement à une première hypothèse retirée.

Le profil Rec.2020 de la référence est reconnu par le comparateur ; cette
expérience ne modifie pas le codec. **15/15 contrôles natifs frais, Gradle 0**,
mais ils valident le contrat actuel et non sa parité Skia. Aucun renderer,
GM, référence, seuil ou score modifié : 198 rendus / 176 comparaisons demeurent
le dernier bilan, pas un résultat amélioré par l'audit. Astra reproduit la
sonde et recommande cet ordre borné : politique alpha du LinearGradient sRGB
clamp d'abord, contrat de composition ensuite, correction du port séparée.
Les critères d'arrêt figurent dans l'audit ; aucune extension implicite aux
images/filtres ou autres familles de gradients. Ni retouche locale du gradient
ni conversion terminale seule ne suffiront à résoudre tous les écarts.

## Lot mélange racine Rect stroke AA — 29 septembre 2026

Le [design](mixed-root-aa-rect-design.md), issu d'un diagnostic Terra et d'un
avis stratégique Astra, puis le [plan](mixed-root-aa-rect-plan.md) bornent le
nouveau domaine aux Rect solid STROKE AA / SrcOver / MITER et aux siblings
Rect FILL solides ou LinearGradient. Pas de layer artificielle : W6 sélectionne
la frame complète, W4d garde RECT/STROKE et l'outline math, produit une source
MSAA4/resolve1 puis compose immédiatement dans l'ordre. Les anciennes voies
standalone/layers/filtres gardent leurs contrats. Les images/Picture, autres
blends, clips complexes et transforms non axis-aligned restent hors extension.

Le [snapshot](mixed-root-ff628a94d.json), renderer exact
`ff628a94da2a9c38aa05c004dff354f61ceaafbf`, conserve les631 identités,443
éligibles, références, scènes, dimensions, seuils, scopes et exclusions.

| Mesure à corpus inchangé | Parent #2419 | Ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 197 | 198 |
| Comparaisons possibles | 175 | 176 |
| Échecs de rendu | 195 | 194 |
| Échecs de setup | 50 | 50 |
| Rendus sans référence exploitable | 14 | 14 |
| Dimensions incompatibles | 8 | 8 |
| Timeouts à30s | 1 | 1 |
| Cas à≥99% des pixels ±2/canal | 36 | 36 |
| Cas à≥95% des pixels ±2/canal | 49 | 49 |

**Un seul nouveau rendu : `alphagradients`,33,8822% de pixels ±2/canal**,553ms
dans le corpus. SSIM luminance0,658254, erreur absolue normalisée0,0840202.
Les197 anciennes empreintes RGBA sont identiques, sans perte, ni autre
changement d'outcome/diagnostic. Les13 autres premiers refus Rect stroke AA
restent des refus, pas des gains promis. La médiane appariée des175 anciens
cas reste71,8965%; celle des176 devient71,7349% par changement de population.
Les trois sessions [0,607),[607,608),[608,631) terminent Gradle0/1/0 :
`vertices` reste timeout au rendu, child124. `ninepatch-stretch` rend en26,758s
avec faible marge sous30s. Somme des durées155,241s, hors démarrage Gradle/JVM,
observation et non benchmark.

Le rejeu PNG de `alphagradients` conserve exactement l'empreinte du corpus.
Inspection du rendu et de la référence : colonnes produites identiques contre
colonnes différenciées dans la référence, contours gris contre noirs et écarts
de couleur visibles. `AlphaGradientsGm.draw` appelle la même construction
`drawGrad` pour les deux colonnes, seul leur placement change. Cela identifie
une divergence du port; cela n'attribue pas causalement chaque écart de pixel
au port ni au renderer. Le seuil historique0 donne `declaredContractPass=true`
malgré33,88% : il ne devient pas une preuve de parité. Prochaine priorité de
fidélité : auditer le port et son contrat d'interpolation/alpha par rapport à
Skia, puis isoler les écarts renderer avec des témoins publics. Aucun GM,
adaptateur, PNG de référence, seuil ni score historique n'est changé ici.

### Validation et limites du lot

La revue de tâche Sol a demandé des témoins séparés : transparent visible
(fond inchangé) et opaque hors écran (refus terminal W4d lors de la sélection
du mélange W6, sentinel intact, récupération). Correction tests-only
`ff628a94d`, re-review validée. **Final proche47/47 sur6classes, XML complets,
Gradle0**, natif Render/Readback et deux rendus identiques. Un run isolé
antérieur W6 budget avait10 assertions PASS puis executor133/Gradle1; ce fait
reste distinct du succès combiné final.

Budget physique final B=29408,B−1 refusé sans publication puis récupération.
Le premier pré-calcul53952 était erroné (deux tripletsV/I/U au lieu d'un,
uniforme gradientV1 mal compté). La dérivation statique corrigée, revue par
Sol, précède le succès B/B−1 final mais pas le premier essai. Le RED négatif
ne relevait pas les préfixes; les préfixes finaux ne prouvent pas leur stabilité
historique. Ces deux écarts au plan sont assumés, pas effacés rétroactivement.

Une seule globale bornée240s :708 END persistés,665 PASS,42 FAIL déjà présents,
1 SKIPPED à l'interruption de
`W5dGradientAddressingSurfacePixelTest.generalCoordinateUniformBudgetRefusesPreciselyAndRecovers`.
Les708 identités sont communes au parent, sans nouvelle assertion en échec
observée;16 autres cas du relevé parent ne sont pas atteints. Le wrapper
retourne124 et l'enfant Gradle143 après TERM de son groupe, sans daemon partagé.
Les XML globaux n'ont pas été finalisés : preuve issue des events et du log,
pas708 XML. Warnings JVM `System::load`/`Unsafe` et dépréciations Gradle restent
signalés; conversions redondantes des nouveaux tests retirées en re-review.
La globale est donc rouge/incomplète. W7 et les gates W6 restent ouverts,
aucune merge readiness. Revue globale Sol `1cd04aa77..cd6f923fd` :
Critical0/Important0/Minor0, publication draft recevable. Non jugés par cette
revue : fidélité Skia globale, extension aux images/Picture, périmètres
exclus et stabilité des tests globaux non atteints. Ces sujets restent ouverts;
aucune preuve correspondante n'est revendiquée.

## Lot couverture AA filtrée — 29 septembre 2026

Le [design](aa-mask-design.md) relu par Astra et le [plan](aa-mask-plan.md)
livrent une couverture blanche distincte de la source AA couleur. Les chemins
direct et stencil producer/cover produisent leur resolve dans une seule passe
native MSAA4. Le blur NORMAL précède l'application unique du matériau W5,
halo compris. Les contributions hors viewport restent disponibles jusqu'au
clip terminal. Le contrat demeure root/Path fill solide/SrcOver/NORMAL.

Le [snapshot](aa-mask-82893045c.json) porte le renderer exact
`82893045c29c0f75cf3b7a95d882b0c3c0e0562a`. Trois sessions couvrent les
631 mêmes identités, 443 éligibles, sans changement de référence, seuil,
scope ou exclusion ; fonts, codecs et `jpg-color-cube` restent hors périmètre.

| Mesure à corpus inchangé | Parent #2418 | Après ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 193 | 197 |
| Comparaisons possibles | 171 | 175 |
| Échecs de rendu | 199 | 195 |
| Échecs de setup | 50 | 50 |
| Rendus sans référence exploitable | 14 | 14 |
| Dimensions incompatibles | 8 | 8 |
| Timeouts à 30 s | 1 | 1 |
| Cas à ≥99 % des pixels ±2/canal | 36 | 36 |
| Cas à ≥95 % des pixels ±2/canal | 48 | 49 |

| Nouveau rendu | Pixels ±2/canal | Durée du cas |
| --- | ---: | ---: |
| `blur2rects` | 96,1751 % | 499 ms |
| `blur2rectsnonninepatch` | 94,8811 % | 651 ms |
| `blur_matrix_rect` | 91,6193 % | 5 845 ms |
| `blurcircles` | 86,2413 % | 3 281 ms |

Aucun ancien rendu perdu : les **193 anciennes empreintes RGBA sont identiques**.
Les quatre gains sont les seuls changements d'outcome/diagnostic. La médiane
des 171 mêmes comparaisons reste **68,9011 %** ; la médiane des 175 cas passe
à 71,8965 % par élargissement de population, pas par amélioration des anciens
pixels. La somme des durées des cas est 161,847 s, timeout inclus, hors
démarrage Gradle/JVM ; ce n'est pas un benchmark. `vertices` reste un timeout
de rendu (child124/Gradle1), les deux autres sessions terminent Gradle0.

Les preuves publiques couvrent AA causal à sigma0,1, alpha appliqué une fois,
translation, trou/halos, source hors écran/clip, deux occurrences et récupération.
Les budgets indépendants direct299104 et stencil723296 incluent MSAA4,
resolve1, depth-stencil4, cibles W6b/W5, V/I/U et readback aligné : B passe,
B−1 refuse sans publication puis la Surface récupère sur deux rendus identiques.
Les revues de tâche Sol sont validées après renforcement des tests.

Validation finale proche : **75/75 exit0**, puis **8/8 exit0** après correctif
tests-only de répétition. La seule globale bornée donne 724 identités communes,
680 succès, les 43 mêmes échecs et un test interrompu. Le wrapper Ruby240s
envoie TERM à son enfant Gradle et retourne124 ; l'exit Gradle indépendant
n'est pas observé. `formatsAlphaAndColorSpaceMatchOracle` (passait sur parent)
est marqué skipped à l'interruption ; `cubicDrawImageMatchesMitchellNetravaliOracle`
n'est pas atteint. Aucune nouvelle assertion en échec observée n'équivaut pas
à une suite verte. Warnings JVM/Gradle et le fallback Kotlin daemon historique
de Task1 restent signalés ; les runs finaux n'ont pas ce fallback.

AA filtré en layer/Picture, autres styles/blends/strokes/clips complexes,
réduction des écarts de pixels et fermeture des gates W6 restent des suites
possibles, pas des propriétés livrées ici. La draft et sa revue globale ne
doivent pas être présentées comme une clôture W7 ou une autorisation de merge.
Revue globale Sol de `a21bb6472..70f2ff164` : **Critical 0 / Important 0 / Minor 3**,
publication draft recevable. Restent deux commentaires trop restrictifs
(classe de test « direct », sélection `DirectTriangle`) et les warnings/outillage.
La revue n'a pas jugé les images spatialement, les autres GPUs/OS, toutes les
combinaisons EVEN_ODD/transformations, les causes des échecs historiques ni les
tests globaux non atteints. Aucune preuve correspondante n'est revendiquée.

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
`codex/w7-image-opacity-authority` ([Draft #2415](https://github.com/ygdrasil-io/kanvas/pull/2415)
empilée sur #2414), corrige la cause localisée
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
Les revues Sol de tâche et de branche ne trouvent pas de défaut bloquant ;
la publication en draft est approuvée. Elles relèvent une amélioration
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

## Lot preuve Sweep AA — 29 septembre 2026

Renderer `49d8224d3158002d2e52d73bc4d90976d161c19a`. L'analyse locale des
classes F32 conserve zéro (signé inclus), subnormal non nul et normal en
complément des intervalles conservateurs. Elle reconnaît les prédicats exacts
sur `abs(v)` et zéro/`MIN_NORMAL`, avec la même identité scalaire et le contexte
d'évaluation. Elle ne déduit pas « normal » d'un simple `v != 0`. Les deux bras
de `EagerSelect` restent validés, la réassociation et les preuves scellées restent
inchangées, et le cache ne mélange pas les environnements.

Pour deux opérandes certifiés normaux, `Atan2` découpe les bornes originales en
au plus deux signes chacun, soit quatre rectangles. Chaque région conserve
l'enveloppe **4096 ULP**, puis les résultats sont réunis. La limite de magnitude
de x et le domaine normal fini restent ceux de la
[spécification WGSL épinglée](https://www.w3.org/TR/2026/CRD-WGSL-20260831/#accuracy-of-concrete-expressions).
Ni shader, ni géométrie, ni budget, ni tolérance ne sont modifiés.

Le témoin public initial RRect AA non uniforme, sur `Surface(17,1)`, échoue
d'abord sur `composed.numeric-domain-unbounded`. La nouvelle preuve expose
ensuite une incohérence distincte du label de l'allocation `GradientStopData` :
le lowerer RRect déclarait un label générique alors que le validateur exigeait
le label composé déjà utilisé par Rect. Astra a confirmé la cause par lecture
du stack et du producteur/consommateur. Le correctif reprend ce seul helper et
son fallback dans le lowerer RRect ; octets, extent, lifetime, identité du slab,
witness et comparaison exacte sont conservés. `NoiseTableData` reste hors lot.

### Tests publics et limites

La sélection finale initiale comprend **9/9 réussites, Gradle 0** : huit tests
`W7ProofContextSurfacePixelTest` et le contrôle image/opacité. Le témoin RRect
initial conserve ses huit pixels bleus puis neuf rouges, Render/Readback et
replay. Le `PATH_STROKE` non uniforme utilise la route hard-edge déjà admise ;
sa première variante AA refusée n'a pas servi à élargir les capacités.
Une fixture distincte vérifie quadrants, axes et coupure angulaire avec vingt
paires CoordClamp/LocalMatrix et pixels littéraux. **La combinaison RRect ×
vingt wrappers n'est pas revendiquée** ; le test historique de budget
`sweepFullCoveragePreservesRequestedTileBudgetIdentity` reste rouge.

Les points-clamps publics zéro, `Float.MIN_VALUE` et `Float.MIN_NORMAL`
atteignent réellement Render/Readback/replay. Les petites échelles LocalMatrix
conservent séparément leurs refus précis et la récupération ; ces refus en amont
ne sont pas présentés comme preuve positive des classes F32. Aucun test
d'infrastructure ni changement de l'oracle n'est ajouté.

La review de tâche Sol approuve le code (aucun point Critical/Important), mais
relève une réserve de méthode : aucun RED `PATH_STROKE` antérieur au correctif
n'est attesté. Un contrôle causal postérieur rétablit temporairement l'ancienne
restriction `Atan2` : le test échoue alors sur `numeric-domain-unbounded`.
Le code est ensuite restauré exactement au commit mesuré. La seconde lecture
Sol valide cette preuve causale, sans la confondre avec une chronologie TDD
initiale : cette dernière reste non démontrée. La sélection initiale 9/9 et le
corpus sérialisé ci-dessous restent les preuves d'acceptation principales.
Les replays supplémentaires après mutation ne renforcent pas ce gate : une
tentative venait du cache, puis la terminaison d'un rerun n'a pas été attestée
avant le suivant. Bien que le dernier ait fini Gradle 0 avec neuf succès,
l'isolation de cette reprise n'est pas certifiée ; aucun gain de durée ou de
robustesse n'en est déduit. Aucun worker ne subsistait à la restitution finale.

La tentative générale est unique et strictement sérialisée : session `20040`,
Gradle 1 à 240 s, **730 cas = 686 réussites + 43 échecs + un interrompu**.
Les 730 identités sont appariées à la base. Six anciens échecs deviennent verts :

- W5c `sweepGradientUsesClockwiseScreenAnglesOnFourLanes` ;
- W5c `sweepGradientHandlesSpanBoundariesAndDegeneracy` ;
- W5d `nonClampDropsOnlyTheOuterEndpointDuplicate` ;
- W5d `sweepFullCoverageForcesClamp` ;
- W5d `linearTileModesCoverSignedBoundariesOnEveryLane` ;
- W5d `sweepEndpointsAndFullCoverageMatchOracle`.

Aucun nouvel échec d'assertion n'apparaît dans cette intersection, mais 49 cas
atteints par la base ne le sont pas ici. L'ancien succès
`W5eImageConvergenceSurfaceTest.unownedDirectImagesKeepExplicitLinearSampling`
est interrompu : la suite reste rouge/incomplète et ce relevé ne certifie ni
sa totalité ni une amélioration de durée. Les 43 échecs sont conservés dans
les XML/JSONL directs, et le runner ajoute un échec synthétique distinct.
Archives : `/private/tmp/kanvas-w7-sweep-aa.82Ytul/green-final-focused` et
`full-suite-240`. Les avertissements JVM/Gradle et de compilation restent visibles.

### Corpus et priorité suivante

Le [snapshot complet](sweep-aa-49d8224d3.json) garde les 631 identités, 443
éligibles, 133 exclusions font, 54 codec et la quarantaine `jpg-color-cube`.
Les trois tranches sérialisées `[0,607)`, `[607,608)`, `[608,631)` terminent
respectivement Gradle 0, 1 (timeout `vertices` persisté par le worker124), 0.
Elles utilisent le commit exact, une limite de 30 s inchangée et des archives
dédiées dans `/private/tmp/kanvas-w7-sweep-aa.82Ytul/corpus`.

Comparé à `image-opacity-bef3af6fa.json`, **aucune issue, aucun diagnostic,
aucune empreinte RGBA, aucune référence, aucun seuil ni scope ne change**.
Les 166 rendus, 144 comparaisons, 26 cas à ≥99 % et 36 à ≥95 % de pixels ±2
restent identiques ; la médiane reste 65,23469075520833 %. `vertices` reste
timeout et `ninepatch-stretch` prend 23,760 s sur ce relevé.
Ce lot répare donc des comportements publics et six tests historiques, mais
**ne produit aucun gain de GM ni de fidélité mesuré**.

La prochaine priorité de diagnostic est le groupe `w6a.layer.unsupported_child`
(51 premiers refus du corpus). Il faut isoler un cas représentatif, distinguer
les restrictions du planner des capacités réellement disponibles, puis choisir
une correction transversale mesurable ; ces 51 refus ne promettent pas 51 gains.
Radial/Conical, RRect × wrappers, AA géométrique, opacité × bords fractionnaires,
les gates W6/W0 et la suite générale restent explicitement ouverts. Publication
draft uniquement, sans clôture W7 ni autorisation de merge.

La review indépendante Sol de branche `14d2be4f8..6ad868c91` autorise la
publication draft : aucun point Critical/Important/Minor. Elle confirme le
snapshot inchangé et conserve toutes les limites ci-dessus, sans autoriser
merge ou clôture W7. PR [#2416](https://github.com/ygdrasil-io/kanvas/pull/2416)
empilée sur #2415 ; le suivi de revue/lien ne change ni code ni mesures.

## Diagnostic des sources de layers — 29 septembre 2026

Base `5c89431a1`, après la PR #2416. Le groupe de 51 premiers refus
`w6a.layer.unsupported_child` comprend 24 GMs COMPOSITE, 21 BLUR, cinq IMAGE
et un CLIP. Ce regroupement ne désigne pas une cause unique : W6a remplace
par ce message générique les `NotCandidate` de sa chaîne de sources enfants.

Le triage statique des témoins distingue :

- `PlusMergesAA` : le premier path AA est dessiné hors `saveLayer`, alors que
  la source `AaResolvedColor` exige un scope W6 explicite. Les enfants `PLUS`
  rencontreraient ensuite une limite distincte, le contrat AA ne permettant
  actuellement que `SrcOver`. Une simple admission ne crée pas ces contrats.
- `blur2rects` : paths AA avec mask blur, hors layer explicite. Il manque la
  source AA racine et son contrat de couverture filtrée. Le refus explicite
  des paths AA filtrés dans une layer reste un comportement testé et conservé.
- `crbug_899512` : rectangle AA réfléchi avec mask blur et color filter `Blend`.
  Une source Rect existante paraît pouvoir le traiter, mais la cause exacte
  du refus doit être établie. La restriction Matrix de W4a n'est pas une
  explication suffisante : W3 est essayé avant W4a et normalise déjà `Blend`.

Une nouvelle exécution sur la base confirme `crbug_899512` refusé (149 ms),
et les neuf tests `W7AaPathLayerSurfacePixelTest` passent (Gradle 0, sans
branche de refus de capacité dans les logs). Ces tests ne prouvent pas les
contrats manquants ci-dessus. Le témoin Surface réduit et la trace temporaire
de sélection ci-dessous ont ensuite établi la cause avant correction.
Archives : `/private/tmp/kanvas-w7-layer-source.mCvMdN/baseline-crbug` et
`baseline-aa-layer`.

### Cause établie et correctif retenu

Le témoin `Surface` reproduisant directement le rectangle, sa CTM, son mask
blur et son color filter passe déjà sur la base, ainsi que son replay Picture
et son aller-retour sérialisé. Ce résultat est un contrôle positif, **pas un
RED**. La trace temporaire du GM montre en revanche `PATH/ANTIALIASED` avant
et après le retrait du mask filter. W3/W4a refusent donc la géométrie Path et
W4c refuse l'AA ; aucun défaut de leur normalisation des color filters n'est
établi. Le producteur fautif est `GmCanvas.drawRect`, qui prétransforme les
coins et remplace tout rectangle à CTM non identité par un path device-space.

L'[appel Skia de référence](https://github.com/google/skia/blob/3f4c5038da37/gm/crbug_899512.cpp)
conserve au contraire `concat(matrix)` puis `drawRect`. Sur avis ciblé Astra,
le correctif retenu préserve ce couple Rect+CTM pour scale/translate, reflets
inclus, à l'intérieur de l'adaptateur : clip existant posé d'abord, puis
`save/concat/drawRect/restore` avec `finally`. Rect et Paint restent intacts.
Il ne change ni une whitelist du renderer ni le contrat des paths AA filtrés.

La preuve retenue passe par **GmCanvas puis Surface**, avec trois
témoins pixels : le cas blur exact, un gradient local réfléchi avec clip et
restauration de l'état, et un stroke mis à l'échelle. Le renderer direct déjà
vert ne peut pas remplacer leur RED avant correction.

Cette décision change la capture effectuée par l'adaptateur partagé : une
hausse du nombre de GMs rendus est un **gain d'adaptateur**, pas une nouvelle
capacité GPU. La comparaison porte sur chaque identité, les anciens rendus et
les scores, sans affirmer que l'IR capturée est restée identique. Les fixtures GM,
références, seuils et exclusions ne sont pas modifiés.

Portée réservée : rotation/skew/perspective, autres primitives prétransformées,
`setMatrix/resetMatrix`, clip différé et forwarding de `saveLayer`. La fidélité
complète du port `crbug_899512` n'est pas certifiée : AA par défaut et
`respectCTM` du blur sont des écarts distincts à auditer. Le reflet unitaire du
témoin ne prouve pas la fidélité d'un blur sous échelle non unitaire.

### Mesure complète du code `d45904e0b`

Le [snapshot](rect-adapter-d45904e0b.json) contient les **631 mêmes identités**,
dont 443 éligibles, 133 fonts, 54 codecs et la même quarantaine. Registre,
dimensions, empreintes PNG de référence, scopes, seuils et tolérances sont
identiques à [#2416](sweep-aa-49d8224d3.json). La capture Rect+CTM, elle, change.

| Indicateur | Base #2416 | Adaptateur corrigé |
| --- | ---: | ---: |
| Rendus / 443 éligibles | 166 | 192 (+26) |
| Comparaisons possibles | 144 | 170 (+26) |
| Cas à ≥99 % de pixels ±2/canal | 26 | 36 (+10) |
| Cas à ≥95 % de pixels ±2/canal | 36 | 48 (+12) |
| Médiane des comparaisons courantes | 65,23469 % | 70,23720 % |
| Médiane appariée des 144 anciennes comparaisons | 65,23469 % | 65,49051 % |
| Rendus anciens perdus | — | 0 |
| Empreintes RGBA anciennes identiques | — | 162 / 166 |

Les 26 gains sont `analytic_gradients`, `anisomips`,
`backdrop_imagefilter_croprect`, `clamped_gradients`, `color4blendcf`,
`colorcomposefilter_alpha`, `colorcomposefilter_wacky`, `colorfilterimagefilter`,
`composeshader_alpha`, `composeshader_bitmap`, `composeshader_bitmap_lm`,
`crbug_899512`, `drawimagerect_filter`, `fillrect_gradient`,
`gradient_dirty_laundry`, `gradient_matrix`, `gradients_interesting`,
`hardstop_gradients_many`, `imagefiltersgraph`, `linear_gradient_rt`,
`linear_gradient_tiny`, `localmatriximageshader`, `luminosity_overflow`,
`paint_alpha_normals_rt`, `perlinnoise` et `sweep_tiling`.
`crbug_899512` atteint **91,63905 %** ; un rendu nouveau n'est pas forcément
fidèle (`paint_alpha_normals_rt` reste à 0,22430 %, par exemple).

Quatre anciens rendus changent effectivement de pixels :

| GM | Score avant | Score après |
| --- | ---: | ---: |
| `crbug_938592` | 93,4 % | 99,8 % |
| `scaled_tilemode_gradient` | 61,02320 % | 99,44489 % |
| `thinstrokedrects` | 89,14583 % | 91,66667 % |
| `perlinnoise_localmatrix` | 62,5 % | 62,5 % |

Aucun score ancien ne baisse, mais l'égalité du score Noise **ne prouve pas
l'égalité des pixels ni l'absence de différences locales**. Le snapshot conserve
les deux empreintes dans les checkpoints respectifs. Vingt-neuf cas toujours
en échec changent seulement de premier diagnostic : aucun gain de rendu ne
leur est attribué. Les refus génériques de segment layer passent de 51 à 43,
sans prétendre que les huit sorties de ce groupe deviennent toutes des succès.

Exécutions sérielles sur le même code et timeout 30 s : `[0,607)` Gradle 0
(2 min 2 s), `[607,608)` Gradle 1 / processus 124 (`vertices`, timeout rendu),
`[608,631)` Gradle 0 (5 s). Les 50 setup failures, 200 render failures,
huit dimensions incompatibles et quatorze références non comparables restent
comptés. `ninepatch-stretch` rend en 22,991 s, avec toujours peu de marge ;
ces durées isolées ne constituent pas un benchmark. Journaux :
`/private/tmp/kanvas-w7-layer-source.mCvMdN/corpus`.

### Limites de replay révélées par les contrôles

Les trois nouveaux témoins passent réellement au rouge avant le correctif :
refus `unsupported_child` pour le blur, pixel (8,0) bleu au lieu de rouge pour
le shader, pixel (2,8) transparent au lieu de rouge pour le stroke. Les oracles
directs passent ensuite sans modification de leurs valeurs attendues.

Deux essais complémentaires de `Picture.playback` avec clip divergent : un
pixel extérieur au clip devient coloré. Le code existant ignore `SetClip` et
rejoue `DrawRect` sans réappliquer son clip capturé. La dette de CTM parent est
distincte ; elle ne suffit pas à expliquer ces deux observations. Ce lot ne
modifie pas `Picture`. Les contrôles shader/stroke vérifient donc le second
`Surface.render()` et les mêmes pixels littéraux ; seul le cas blur sans clip
revendique aussi le replay Picture. Les archives des deux échecs sont conservées
(`stage-b-green-adapter`, `stage-b-green-replay-diagnostic`) : aucune couverture
générale de replay Picture avec clip n'est revendiquée.

Le contrôle existant `GmCanvasTest.rotated clip rect is captured as a device path`
échoue également. Il appelle directement Surface/Canvas, sans passer par la
méthode de l'adaptateur modifiée ; l'échec reste rapporté, sans modifier ce test
d'infrastructure ni étendre la correction aux clips tournés.

### Validation du correctif d'adaptateur

Le commit `d45904e0b` ne touche que `GmCanvas.kt` (huit lignes) et les trois
tests pixels publics. Le run final `stage-b-final-adapter` donne **3/3 PASS**,
sans skip, Gradle 0. Les 24 contrôles W6b/W7 AA passent avec Gradle 0. Une
sélection incluant W5f a émis 68 PASS, mais sans sortie terminale exploitable :
elle reste incomplète et n'est pas une validation réussie du run entier.
Après demande de review, le seul shard W5f manquant a été relancé séparément
(`review-r1-w5f`) : **44/44 assertions PASS**, sans skip, puis le worker natif
quitte avec `133` et Gradle avec 1 après 3 min 23 s. La cause native reste
`UNKNOWN` ; ce run a maintenant une issue connue, mais n'est pas vert. Aucun
worker ne reste actif après sa terminaison. Il ne justifie aucune nouvelle
relance générale ni effacement de l'exécution incomplète précédente.
Les contrôles historiques de l'adaptateur donnent 9/10, Gradle 1, avec le
seul échec de clip tourné détaillé ci-dessus.

L'unique tentative complète `:kanvas:test` atteint 240,104 s puis sort 1
(`Could not stop all services`). Les événements/XML recensent **729 tests :
685 PASS, les mêmes 43 échecs que la base #2416 et un interrompu**.
`W5eImageConvergenceSurfaceTest.unownedSyntheticImageSamplersKeepHistoricalCompatibility`
passe sur la tentative parente mais est interrompu ici ; un autre cas de la
base n'est pas atteint. Il n'y a aucun nouvel échec d'assertion dans les 729
identités communes, mais aucune conclusion verte ou complète n'est possible.
Les warnings JVM native-access, LWJGL Unsafe et Gradle deprecation restent
présents ; le rapport conserve aussi les essais de compilation/trace invalides,
qui ne valent pas RED comportemental. Archives dans
`/private/tmp/kanvas-w7-layer-source.mCvMdN/`, comparaison avec
`/private/tmp/kanvas-w7-sweep-aa.82Ytul/full-suite-240`.

La review de tâche Sol approuve conformité et qualité après correction du
rapport : sélection incomplète explicitée et 43 échecs listés par méthode.
Le nom interne « replay » de deux résultats désigne le second rendu Surface,
pas une preuve Picture ; cette remarque mineure reste ouverte, sans modifier
les assertions. Les warnings restent visibles. La review Sol de toute la branche
`5c89431a1..728868af4` approuve la publication draft (Critical 0 / Important 0 /
Minor 1 de nommage). Elle vérifie indépendamment les 631 identités, les 26 gains,
les quatre anciens rendus modifiés et les mêmes 43 échecs. Les contrats AA racine,
AA filtré/PLUS, transforms générales et Picture/clip restent hors correction,
explicitement ouverts ; aucune clôture W7/merge n'est proposée.

## Lot source AA racine — 29 septembre 2026

Le [design](root-aa-design.md) et le [plan](root-aa-plan.md), relus par Astra,
réemploient la source MSAA4/resolve1× pour les Paths AA solid-fill SrcOver
racine d'une frame déjà possédée par W6. L'ownership ne change pas, la racine
reste 1× et chaque source isolée est composée immédiatement dans l'ordre.
Le mapping root ne réapplique pas la CTM ; le binding natif reçoit une origine
zéro explicite, au lieu d'interpréter l'ordinal multisample comme une layer.
Les chemins layer existants, seals, budgets et contrôles d'autorité restent
inchangés. Aucun adaptateur, fixture, référence, seuil ou exclusion n'a changé.

### Mesure à corpus constant

Le [snapshot](root-aa-470f62e63.json) porte le commit complet
`470f62e638618274997aec9dfd5cd4f172688001` et les **631 mêmes identités**,
dont **443 éligibles**. Les 133 fonts, 54 codecs et `jpg-color-cube` restent
exclus. Les trois tranches terminent avec des exits Gradle 0/1/0 ; le 1 est
le timeout conservé de `vertices` à 30 s (processus 124).

| Mesure | #2417 | Ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 192 | 193 |
| Comparaisons possibles | 170 | 171 |
| Échecs de rendu / setup | 200 / 50 | 199 / 50 |
| Non comparés / dimensions incompatibles | 14 / 8 | 14 / 8 |
| Timeouts | 1 | 1 |
| Cas à ≥99 % / ≥95 % des pixels ±2/canal | 36 / 48 | 36 / 48 |

Le seul gain est **`rasterallocator`, à 27,2533 %**. Les **192 anciens rendus
sont pixel-identiques** ; aucune perte, aucun autre changement d'issue ou de
diagnostic. Les identités, scopes, références et seuils sont invariants.
La médiane des 170 mêmes cas reste **70,2372 %** ; celle des 171 comparaisons
devient **68,9011 %**, par ajout d'un cas moins fidèle, pas par régression
des anciens pixels. Le port `RasterAllocatorGm` se décrit lui-même comme
une approximation simplifiée ; ce rendu gagné ne valide pas sa fidélité au GM
Skia. Aucun rapprochement ISO n'est revendiqué pour ce nouveau cas.
Somme des durées par cas : **149,813 s**, sans valeur de benchmark.
`ninepatch-stretch` termine à 25,092 s, toujours proche de la borne de 30 s.

### Validation et limites

Cinq positifs directs refusent avant patch puis passent : ordre dans les
deux sens, alpha 128 composé une fois à 188 sur noir, translation/clip hard et
origine layer, deux sources stencil et budget exact **B=27 772 / B−1**.
La layer vide de la fixture B est réellement allouée (196 octets inclus).
Le run final W7 donne **16/16 PASS**, et les contrôles voisins **53/53 PASS**,
sans skip, Gradle 0. Ce sont deux sélections qui se recoupent, pas 69 tests
distincts. Les assertions positives exigent Render/Readback.

L'unique tentative globale, arrêtée par la borne de quatre minutes, termine
Gradle 1 en 4m01 : **681 PASS, 43 échecs, un interrompu** parmi 725 identités.
Ce sont les mêmes 43 échecs que la base dans l'intersection. Le cas auparavant
passant `W5eDecodedImageSurfacePixelTest.cubicDrawImageMatchesMitchellNetravaliOracle`
est interrompu ; quatre cas parents ne sont pas atteints. Le runner XML
signale aussi `failed to execute tests` / `Could not stop all services`.
La suite reste rouge/incomplète. Warnings JVM native-access, LWJGL Unsafe et
Gradle deprecation conservés ; le problème natif 133 W5f antérieur n'est ni
réexécuté ni clos par ce lot.

Les archives résident dans `/private/tmp/kanvas-w7-root-aa.8WK1ZR/`.
`green` mélange plusieurs tentatives (trois échecs alpha) ; `final` est
séparé, sans doublon. Une première mesure contrôleur avait un SHA étiqueté
incorrectement : arrêtée, conservée mais exclue. Seul `corpus-verified`
alimente le snapshot et son SHA est celui réellement exécuté.

La review Sol a demandé un oracle bleu indépendant pour le contrôle hard root,
en plus du vert de la layer et de Render/Readback. Le commit test-only
`3bfab918c` l'ajoute et précise le nom du refus Picture en layer. Son run
`review-r1` repasse **16/16**, Gradle 0 ; aucun fichier de production n'a changé
depuis le commit mesuré. Le détail d'une des trois tentatives GREEN échouées
n'est plus récupérable après réutilisation du dossier : cette perte de détail
reste signalée, sans cause inventée.
La re-review Sol approuve conformité et qualité après ces corrections, sans
nouveau défaut. La review indépendante de l'ensemble `54781716e..e900a0983`
approuve la publication draft #2418 : Critical 0 / Important 0 / Minor 1.
Le Minor reste suivi pour le chantier Picture : le refus permanent est testé
en layer, pas au root, où il n'a été observé que dans une tentative GREEN.
Le support positif Picture/AA filtré/PLUS/W6b/clips complexes et la fidélité
du port simplifié restent explicitement ouverts. Cette validation ne vaut
ni merge readiness ni clôture W7.

### Arbitrages et suite

- Nouvelle admission limitée à `!ownsW6b` : les frames mêlant filtres et root
  AA restent refusées ; la restriction ne touche pas l'AA des layers existantes.
- Source plein viewport isolée conservée : simplicité de preuve et ordre
  préservés, au coût d'une allocation mémoire conservatrice par occurrence.
- **Picture AA positif différé**, contrairement au premier plan : sa chaîne
  de compilation et son émetteur SingleSample demandent un raccord séparé.
  Le test vérifie son refus/sentinel/récupération, pas un playback réussi,
  même sans clip. Cette part du contrat initial n'est pas accomplie.

AA filtré, `PLUS`, les frames W6b et Picture AA restent des extensions
distinctes. `PlusMergesAA` et `blur2rects` refusent toujours ; les 42 refus
génériques de segments restants ne sont pas 42 gains potentiels démontrés.
Le prochain travail doit traiter la couverture AA filtrée avec un témoin
public et un consommateur réel, sans contourner le garde W6b ; le raccord
Picture reste identifié séparément. Ni merge ni clôture W7 à ce stade.

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
