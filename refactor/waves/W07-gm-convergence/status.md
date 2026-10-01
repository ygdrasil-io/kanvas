# W07 — diagnostic GM provisoire

PR draft empilée : [#2410](https://github.com/ygdrasil-io/kanvas/pull/2410),
sur la PR W6 [#2409](https://github.com/ygdrasil-io/kanvas/pull/2409).

## Qualification Picture / Porter-Duff — 1er octobre 2026

Renderer mesuré : `3398dc3db8c8741d49234c74643f464baae645f6`.
Le garde de plain layer conserve W4a pour les Rect `identity`/`scale-translate`
non-`SRC_OVER`/`PLUS`, et n'admet W7 que lorsque le fait existant
`GeneralAffine` le requiert. Le RED causal au même B analytique complet
`26808` refuse sans ce garde (`requires 28736`); le GREEN passe avec le garde.
Les témoins publics W7/Picture, quatre régressions historiques, la garde de
graphe, 14 cas Rect affine et ce témoin sont **186/186** (archive
`layer-preservation-covering-186-1`, wrapper/enfant 0/0, zéro
failure/error/skip/stderr). Le témoin Picture-layer `DST_OUT` observe C
`64/255`, établi depuis le pattern MSAA4 avant Surface. Le contrôle PLUS
translation racine demeure positif. La relecture scoped reste en attente du
contrôleur; aucune publication n'est autorisée par ce statut.

La suite globale `layer-preservation-full-final-1` expire comme bornée
(wrapper 124, enfant 143) après 751 END : 710 SUCCESS, 40 FAILURE, 1 SKIPPED.
Les 725 identités du run final précédent sont toutes présentes avec les mêmes
40 échecs; 26 identités W5e supplémentaires sont atteintes (25 SUCCESS, un
SKIPPED) et `cubicDrawImageMatchesMitchellNetravaliOracle` devient SUCCESS.
Les 718 identités Task1 restent présentes, avec 33 ajouts (32 SUCCESS, un
SKIPPED) et `rowPadding...` SKIPPED→SUCCESS. Ce n'est pas une suite globale
verte ni une clôture W7.

Le corpus figé au même SHA est 631 identités / 443 éligibles : 200 rendus,
178 comparaisons, 39 à ≥99 % et 52 à ≥95 % des pixels à ±2/canal. Les 18
invariants, tous les hashes et métriques sont identiques à
`picture-8829d18`; le snapshot remplacé est
[`picture-3398dc3.json`](picture-3398dc3.json). Face au hardstop,
`sk3d_simple` reste une admission native seulement (51.931111111111115 % à ±2,
SSIM 0.6385013748432061, max [136,255,119,0]) : son écart de
silhouette/couleur est majeur, et l'alpha opaque identique ne prouve aucune
équivalence. `PlusMergesAA` reste le gain historique. Le timeout `vertices`
index 607 reste 30 s. Références, seuils, exclusions et oracle n'ont pas été
modifiés.

Le checkpoint intermédiaire `7488067` reste archivé honnêtement dans les journaux
privés : son JSON n'est pas conservé dans le repo; il avait gagné
`PlusMergesAA` mais perdu `lattice2`. Le diagnostic a montré qu'un Rect AA SRC
forçait W6 avant le plan whole-frame W5e; `93ec530` rétablit la priorité W5e sur
un vrai candidat root sans layer/W6b. `lattice2` retrouve exactement le hash
`49d38b8f…02da0`; aucune admission image ou codec n'a été élargie.

## Série AA et composition différée — en cours, 1er octobre 2026

Branche `codex/w7-aa-blend-sources`, prévue en draft sur #2425.
[Design](aa-blend-sources-design.md), [plan séquentiel](aa-blend-sources-plan.md).
La correction W5 PLUS couvert est implémentée et approuvée par Sol après
deux vagues de corrections : `sat(C*S+D)` remplace le post-lerp à saturation,
avec loi sélectionnée et oracle V2 indépendant, sans tolérance élargie.
Les témoins natifs W7 corrigés passent **4/4**, processus 0 ; le contrôle
Point V2 couvre ses trois contextes et passe **1/1**, processus 0.

La couverture AA indépendante du matériau, le consommateur typé et l'émetteur
partagé racine/plain layer sont implémentés et relus pour PLUS/SRC_OVER.
Le lot Porter-Duff `dd498a4bc`, corrigé par `7953b2116`, apporte les **52 cellules**
des 13 modes × Path/Rect × root/layer. Son covering passe **110/110**, sorties enfant/wrapper
0/0, sans timeout, erreur, skip ni stderr XML ; les 31 identités originales
et les 59 du checkpoint Task2 sont conservées. La relecture Sol accepte le lot
après correction de l'observabilité de DST_OUT dans l'ordre inverse.
DST est sélectionné puis éliminé sans écriture ni version supplémentaire ;
CLEAR/SRC restent actifs avec une source transparente. Les Rect de layers
conservent leurs lanes analytiques. Les nouveaux modes Rect root emploient la
projection W4d existante (coût MSAA/resolve, C=128/255 et non C=.5 analytique).
SRC_ATOP possède un témoin coloré aligné, pas de nouveau témoin de bord partiel.
Le scope W7 exclut maintenant explicitement la perspective et les transformations
sans inverse fini. Un témoin public distingue translation, bord AA et scissor,
puis vérifie refus de perspective, sentinelle intacte et récupération ; les
routes historiques de W4d/W4a restent inchangées.
`aarectmodes` reste `render_failed` sur `w6a.layer.unsupported_child`, cause
non isolée ; aucun gain GM revendiqué pour ce lot. Picture, la validation
complète du corpus et le contrôle de limite de graphe sont terminés; W7, W6 et
W0 ne sont pas clos et la suite globale reste incomplète par timeout.

Les checkpoints ci-dessous retracent la mise en place du contrat partagé.
Après appui architectural et reprise native ciblés par Astra, le commit
`6f07a6448` raccorde le consommateur GPU. Son témoin root Path PLUS passe
**1/1**, processus 0, sans erreur/skip : pixels indépendants à C=0, C=1 et
C=128/255, Render/Readback réels et second rendu byte-identique. L'archive
finale `task2-rescue-final-green` ne contient plus de trace temporaire.

L'émetteur commun est extrait depuis `fa84fa81d`. La reprise native corrige
l'initialisation de la première passe stencil, la double déclaration d'un
snapshot enfant et la sélection SRC_OVER : conserver l'ancienne source quand
elle admet le draw, sinon employer la source typée pour Solid/Opacity.
Le correctif `2dbefcdb9` raccorde la sélection des frames Rect racine mêlant
AA PLUS et SRC_OVER hard, en conservant la géométrie analytique. La trace
antérieure montrait un paquet legacy non scellé, avant toute entrée W6 ; aucun
fallback WGSL ni guard n'a été ajouté pour masquer ce défaut de sélection.
Le contrôle `task2-rootrect-owner-native-2` compte **25 succès sur 25**, zéro
échec/erreur/skip et sorties enfant/wrapper **0/0**, sans timeout ni stderr XML.
Il comprend les huit cellules PLUS/SRC_OVER × Path/Rect × root/layer, quatre
cas concave/even-odd, le premier témoin PLUS, huit contrôles root historiques
et quatre témoins de la loi PLUS couverte. Les nouvelles cellules vérifient
tous leurs pixels sur deux rendus natifs.

La fixture SRC_OVER initialement proposée était non bornée avant Surface aux
bords partiels : elle ne constituait pas un échec moteur. Ses quatre contrôles
emploient maintenant des formes alignées avec intérieur/extérieur disjoints,
sans élargir l'oracle. Les témoins PLUS conservent leurs bords partiels ; cette
matrice n'apporte pas de nouvelle preuve SRC_OVER aux bords partiels.
L'audit a également confirmé une double allocation possible d'une même row
uniforme déclarée. Le correctif `0980fab24` fait emprunter à W5 le buffer
deferred détenu par W6, avec validation de frame, row, génération, capacité et
bytes immuables ; W6 reste seul responsable de sa libération. Le contrôle
`task2-shared-uniform-native` passe **22/22**, sorties **0/0**, sans timeout
ni stderr XML. Ces pixels vérifient la conservation du rendu, pas le nombre
d'allocations : ce dernier repose sur l'audit de propriété et reste à relire.

Le témoin de budget `9b822cee1` dérive **B = 27 804 octets** : root, resolve
et snapshot 3×196, AA4 784, readback 1 792, uniforme W6 16, buffers W4d
16 384+4 096+4 096 et matériaux D/S 16+32. Le Rect hard PLUS utilise le blend
fixe One/One ; seul le Path AA exige une snapshot. B passe sur deux rendus ;
B−1 refuse sans modifier la sentinelle, puis la même Surface récupère sur deux
rendus. `task2-shared-uniform-budget-3` passe 1/1 et le contrôle couvrant passe
**23/23**, sorties **0/0**, sans timeout ni stderr XML. Une trace a corroboré
l'inventaire avant qualification ; le budget n'a pas été ajusté par recherche.

Le checkpoint numérique `b4d48388a` passe **33/33** (`task2-numeric-covering-3`,
sorties 0/0, sans timeout ni stderr XML) : saturation PLUS, ordre forward/reverse
avec write intermédiaire, destination périmée, alpha nul, translation/scissor,
origine de layer et double couverture. L'opacité nulle a révélé un refus de
`TransparentV1/EmptyV1` ; la recette accepte désormais cette paire normalisée
déjà prise en charge par l'évaluateur. Le témoin historique bleu sur bleu
observe aussi le bord restauré (alpha 192 contre 128 si la layer est omise).
Le premier témoin de mapping combiné avait une attente géométrique erronée et
est explicitement invalidé ; les deux scènes corrigées passent séparément.
Les bords chronologiques colorés restent non bornés avant GPU : l'ordre et
la destination périmée sont observés dans leur overlap, la double couverture
sur le vrai bord d'une autre scène. Aucun oracle n'a été élargi.

Le rejeu `task2-final-aa-baseline-point` passe **25/25** : PathLayer9,
MaskBlur8, MixedRootAaRect7 et PointV2. Avec Root8 du covering final, les
**31 identités AA historiques sont toutes présentes et vertes**, sans doublon,
plus le positif layer ajouté. Les deux runs comptent **58 tests uniques**,
sorties 0/0, sans timeout ni stderr XML. Les avertissements Gradle historiques
restent distincts de ce résultat ciblé.

`PlusMergesAA` est désormais rendu : **7 dispatches, 0 refus**, contre
`w6a.layer.unsupported_child` auparavant. La mesure ciblée au même commit
(`task2-plusmergesaa-final`, sorties 0/0) conserve référence, port, domaine et
seuils. Pixels exacts/tolérance2 : **69,4824 %**, SSIM **0,985107**, delta
max RGBA **[73,37,0,0]**. Les images actual/reference/diff ont été inspectées :
écarts R/G sur les deux carrés, alpha identique. Le rendu est débloqué, mais
sa fidélité couleur n'est pas résolue par ce seul run.
Le seuil contractuel historique nul n'est pas une preuve de parité.

Le diagnostic couleur en lecture seule trouve le même intérieur à gauche et
à droite : référence décodée `(14,240,0,255)`, actual `(69,248,0,255)`.
Le calcul avec alpha240/255 en LINEAR suivi de l'encodage sRGB prédit exactement
l'actual ; le calcul en valeurs encodées prédit `(15,240,0,255)`. Les 20 000
pixels des deux carrés expliquent tout le mismatch au seuil2. Le port est fidèle
au `plus.cpp` Skia épinglé ; la configuration de surface ayant généré la PNG
n'est pas documentée. **Gap de contrat de domaine/référence à résoudre** :
pas de changement de domaine, de référence ni de seuil dans cette série.
Ce diagnostic distingue le décalage intérieur du travail restant sur la parité
à domaine comparable ; il ne valide pas globalement le renderer.

La revue indépendante Task2 a demandé deux corrections (ownership root
SRC_OVER trop large et premier consumer non observé par le test d'ordre).
Le correctif `a851621fc` partage le même fait de sélection entre ownership et
occurrence, rétablit le budget root historique de 27 576 octets, et observe
chaque premier consumer sur un pixel exclusif. La relecture Sol ciblée accepte
les deux corrections, sans nouvelle anomalie importante. Le covering50 et le
complément9 passent **59/59**, sorties0/0, sans timeout ni stderr XML ; les31
identités originales sont toutes présentes. La re-mesure du GM conserve
exactement le hash RGBA et les métriques du checkpoint précédent.

**Tasks2–3 validées**, sans qualifier la suite globale ni clôturer W7.
Picture et corpus complet suivent. La revue finale Astra de toute la série
reste requise avant sa draft stackée.

Les limites de validation restent explicites : W5g compte 125 identités,
69 assertions réussies, deux échecs reproduits sur la base antérieure et
54 non atteintes. Son run ciblé de 29 assertions réussies quitte ensuite
avec un executor 133 inexpliqué, donc n'est pas vert. La globale bornée
compte 677 succès, 40 échecs déjà observés et un cas interrompu ; elle est
incomplète. Les fonts, codecs/décodage externe et `jpg-color-cube` restent
exclus. W7 et les gates antérieures restent ouverts, sans merge ni claim ISO.

## Lot ports hardstop fidèles — 30 septembre 2026

Branche `codex/w7-hardstop-ports`, draft
[#2425](https://github.com/ygdrasil-io/kanvas/pull/2425) empilée sur #2424.
[Plan](hardstop-ports-plan.md), [snapshot631](hardstop-ports-34e3d4e98.json),
[bilan et arbitrages](pilotage.md#lot-ports-hardstop-fidèles--30-septembre-2026).
Grille 500×500 dans une image 512×512, ordre des doubles stops et hauteur
des bandes réparés ; paints non-AA conformes aux sources Skia épinglées.
Les deux domaines restent LINEAR, aucun changement moteur ou critère.

**hardstop_gradients : 16,11 % → 100 % des pixels ±2** ;
**hardstop_gradients_many : 10,66 % → 100 %**. Écarts maximaux RGB 2 et 1,
alpha exact ; égalité stricte 90,47 % et 96,382 %, pas 100 % exact.
Le code final `34e3d4e98` conserve 631 identités / 443 éligibles,
**198 rendus / 176 comparés ; 196 autres images byte-identiques**.
39 cas ≥99 %, 52 ≥95 %, médiane 73,264678 %. Références, scopes, seuils,
diagnostics et domaines inchangés ; timeout vertices 30 s conservé.

RED causal 2/2, GREEN séparés 1/1 chacun, final **9/9**, les sept contrôles
parent conservés. Sol et Astra sans Critical/Important ; un Minor natif
aligné à la tolérance déjà prévue puis relu. Warnings hérités différés.
Globale historique rouge/incomplète, non rejouée ; corpus complet terminé.
Suite : diagnostiquer les sources de layers, notamment AA avec blend PLUS,
sans supposer que les 38 refus génériques sont une seule cause.
W7 reste ouvert, publication draft uniquement, aucune parité globale.

## Lot alphagradients fidèle et diagnostic cohérent — 30 septembre 2026

Branche `codex/w7-alphagradients-port`, draft
[#2424](https://github.com/ygdrasil-io/kanvas/pull/2424) empilée sur #2423.
[Design](alphagradients-port-design.md), [plan](alphagradients-port-plan.md),
[bilan et arbitrages](pilotage.md#lot-alphagradients-fidèle-et-diagnostic-cohérent--30-septembre-2026).
Domaine déclaré par GM, LINEAR par défaut et encodé pour alphagradients,
config conservé au rendu et au replay diagnostique. Port des deux colonnes
fidèle, sans changement moteur ni relâchement des proofs/caps/tolérances.

**alphagradients33,882161%→100% des pixels ±2**, maximumRGB1/alpha0 ; fond
et contours exacts. [Corpus631 final](alphagradients-port-6259c38d8.json)
sur `6259c38d8` :198/443 rendus,176comparaisons,197autres images inchangées,
aucune perte/nouveau rendu, références/scopes/seuils préservés.37cas≥99%,
50≥95%, médiane72,010742%. Métadonnée630LINEAR/1encoded, vertices30s conservé.

Reviews Sol des deux tâches, revue finale Astra sans Critical/Important,
deux Minor corrigés ensemble puis relus. Validation finale7/7 Skia ;
hairline13/13 après son unique modification. Aucun test d'infrastructure.
Warnings hérités maintenus. Globale historique rouge/incomplète, pas rejouée ;
limites replay clip/layer et provenance des références explicites.
Prochain lot : écarts de port hardstop identifiés dans les sources épinglées.
W7 reste ouvert, aucune parité globale ni merge revendiqué.

## Lot Rect hairline entier et encodé — 30 septembre 2026

Branche `codex/w7-encoded-hairline`, draft
[#2423](https://github.com/ygdrasil-io/kanvas/pull/2423) empilée sur #2422.
[Design](encoded-hairline-design.md), [plan](encoded-hairline-plan.md).
La première tâche est validée au commit `aa29de2ca` : couverture hard-edge
du Rect entier de largeur zéro, commune et détenue par `math`, une seule
occurrence de source conservée. **48/48 tests natifs**, dont les43 contrôles
précédents, et **478/478 tests math**, processus/wrapper0. Sol a demandé le
cas supplémentaire de hauteur minimale : corrigé, test ciblé2/2, relecture
sans nouveau Critical/Important. Warnings Java/Gradle hérités conservés.

La propagation SRGB_ENCODED est implémentée et revue en Task2 : mélanges
racine, plain layer, Picture, snapshots, refus et budget B25840/B−1.
Sélection68/68 avant sa correction de review, puis56/56 au commit
`ebfd29bb1`, avec les43 contrôles initiaux présents. L'unique globale240s
reste rouge/incomplète :678 succès,40 échecs hérités,1 interrompu ;719
identités communes, aucun nouvel échec observé,25 anciens tests non atteints.

La correction finale `f80d94fb4` transmet le format du successeur W5b non vide,
ferme les entrées publiques encodées au contrat existant et couvre les
mélanges dans un même plain layer. **69/69 tests**, onze classes, sorties0,
tous les43 contrôles initiaux et50 du lot parent présents et inchangés.
La contre-relecture Sol confirme les deux Important d'Astra corrigés, sans
nouvelle casse Critical/Important. Minor différé : le helper omettrait son
contrôle de largeur pour une future ligne vide ; les grilles actuelles sont
complètes, y compris la colonne manquante du cas scale. Warnings conservés.

[Corpus631 final](encoded-hairline-f80d94fb4.json) : **198/443 rendus,
176 comparaisons,198 anciennes images strictement identiques**. Références,
seuils, scopes, résultats et diagnostics inchangés ; `vertices` timeout30s.
Aucun gain GM mesuré : le port fidèle d'`alphagradients` reste séparé et
n'active pas encore ces capacités. Publication draft uniquement, W7 ouvert.

## Lot domaine de composition Surface — 30 septembre 2026

Branche `codex/w7-surface-composition`, draft
[#2422](https://github.com/ygdrasil-io/kanvas/pull/2422) empilée sur
[#2421](https://github.com/ygdrasil-io/kanvas/pull/2421).
[Design](surface-composition-design.md), [plan](surface-composition-plan.md)
et [bilan](pilotage.md#lot-domaine-de-composition-surface--30-septembre-2026).

`SRGB_ENCODED` explicite fonctionne du draw au snapshot : solides,
LinearGradient sRGB/CLAMP dans les deux modes alpha, un plain layer,
images SOURCE_SPACE nearest1:1 et Picture. `LINEAR` reste le défaut.
AUTO résout le format natif ; RGBA/BGRA public ne change que l'ordre des
octets. Les configurations natives contradictoires refusent explicitement,
changement de compatibilité intentionnel. Pas de support ajouté AA/hairline,
filtres ou topologies riches de layers.

**50/50 tests ciblés, neuf classes XML, processus/wrapper0**, les48 identités
précédentes conservées. Les deux tâches sont approuvées par Sol ; la re-review
finale confirme les six corrections, sans nouvelle casse Critical/Important.
La validation utilise l'amendement approuvé
`CompositionEnvelope`, ensembles complets avant GPU mais **acceptation moins
précise** ; ni les bornes/preuves produit ni les seuils GM ne sont élargis.
L'omission d'un store n'est pas toujours détectable. Budget3×3/B888 contre887
vérifié ; les erreurs des premières dérivations restent documentées.

Globale unique240s : **703 PASS,40 échecs hérités,1 interrompu**,744 identités,
wrapper124/enfant143, XML non finalisés. Aucun nouvel échec sur724 identités
communes ;13 corrections Picture du lot parent confirmées. Ancien témoin
legacy SRC renommé et élargi, pas supprimé. Globale antérieure à la correction
finale, non relancée ; suite rouge/incomplète. Dettes
native133 isolées et warnings conservés.

[Corpus631 final](surface-composition-1d629b0be.json) : **198/443 rendus,176
comparaisons,198 anciennes images strictement identiques**. Aucun changement
d'identité/référence/scope/seuil/résultat/diagnostic ; `vertices` timeout30s
conservé. Aucun gain GM : les ports n'activent pas encore l'opt-in encodé.
Astra avait relevé quatre raccords à corriger : mixtures root avec DrawColor,
Surface encodée vide, identité W6 entre domaines et identités LINEAR issues
des capabilities natives, ainsi que deux diagnostics à actualiser. La vague
groupée `1d629b0be` les corrige, puis le corpus final reste invariant.
La re-review Sol est terminée : un Minor résiduel est différé. Le readback
clear-only encodé déclare encore RGBA8UnormSrgb dans sa métadonnée de layout,
malgré une cible native RGBA8Unorm correctement authentifiée. Sans défaut de
pixels/tag établi, mais à corriger avant une nouvelle utilisation interprétative
de ce champ. Publication draft uniquement. Les essais intermédiaires dans le mauvais
checkout sont exclus ; les modifications accidentelles y ont été annulées
sans toucher aux deux fichiers utilisateurs préexistants.
W7 reste ouvert ; hairline puis port fidèle
d'`alphagradients` constituent la suite distincte.

## Lot politique alpha LinearGradient — 30 septembre 2026

Renderer `09d9574b5`, branche `codex/w7-gradient-alpha-mode`, draft
[#2421](https://github.com/ygdrasil-io/kanvas/pull/2421) empilée sur #2420,
revue finale corrigée et validée. [Design](gradient-alpha-design.md),
[plan et commandes](gradient-alpha-plan.md),
[snapshot631](gradient-alpha-09d9574b5.json) et
[bilan](pilotage.md#lot-politique-alpha-du-gradient--30-septembre-2026).

`STRAIGHT` reste le défaut ; `PREMULTIPLIED` est maintenant une capacité
publique sRGB/CLAMP, conservée dans capture, V4 et Picture16/schema10.
Les anciennes versions Picture13/14/15 restent acceptées. Prérequis natif
SRC sans blending strictement limité au direct single-sample/coverage None ;
composition, AA, images et enveloppes restent inchangés.

**46/46 tests ciblés, XML complets, Gradle0.** La globale unique240s sur le
code précédent est rouge/incomplète (671 PASS,53 FAIL,1 interrompu) ; elle
a révélé13 régressions Picture corrigées et rejouées dans les tests finaux.
Elle n'a pas été relancée après ce correctif. Les deux re-reviews Sol de
tâche approuvent les corrections. La revue globale Astra relève ensuite un
Important sur la perte du mode dans un Rect stroke non-AA legacy, reproduit
sur Surface puis corrigé : mapper/planners/admission refusent avant publication.
Le run final comprend les39 cas précédents, ce nouveau refus et six contrôles
strokes. La re-review ciblée finale Sol approuve conformité et qualité,
zéro Critical/Important/Minor résiduel ; pas de seconde correction finale.

**198/443 rendus,176 comparaisons ; les198 images précédentes sont strictement
pixel-identiques.** Identités, références, scopes, seuils, résultats et diagnostics
inchangés :36 cas à≥99%,49 à≥95%,194 render failures,50 setup failures et
`vertices` toujours timeout30s. Aucun gain GM annoncé pour un mode encore
inutilisé par les ports. SRC partiel AA reste non validé ; anciens native133
et warnings conservés au bilan. Prochain contrat : composition de Surface,
puis port GM séparé. W7 reste ouvert, pas de merge readiness.

## Audit `alphagradients` — 29 septembre 2026

[Diagnostic causal](alphagradients-audit.md) sans modification de code ni du
corpus : interpolation premul/unpremul non exposée et domaine de composition
différent de celui observé dans la référence (sRGB encodé contre linéaire).
Les modèles retrouvent chacun les RGB de leurs 184 704 pixels intérieurs à un
octet près ; ce n'est pas un nouveau score GM. AA du port différent, diagonale conforme,
profil Rec.2020 reconnu. **15/15 tests publics natifs, Gradle 0**. Bilan inchangé,
W7 toujours ouvert. Ordre retenu après relecture Astra : politique alpha
explicite du LinearGradient sRGB clamp, puis domaine de composition de
Surface, puis port corrigé avec mesure distincte du changement de scène.

## Lot mélange racine Rect stroke AA — 29 septembre 2026

Renderer `ff628a94d`, branche `codex/w7-mixed-root-aa-rect`, draft
[#2420](https://github.com/ygdrasil-io/kanvas/pull/2420) empilée sur #2419.
[Design](mixed-root-aa-rect-design.md), [plan](mixed-root-aa-rect-plan.md),
[snapshot631](mixed-root-ff628a94d.json) et
[bilan détaillé](pilotage.md#lot-mélange-racine-rect-stroke-aa--29-septembre-2026).

**197→198 rendus /443 éligibles;175→176 comparaisons.** Les197 anciennes
images restent pixel-identiques. Seul `alphagradients` devient rendable,
à33,88% des pixels ±2/canal : gain fonctionnel, pas parité. Le port répète les
deux colonnes là où la référence les différencie; audit port/interpolation
nécessaire avant attribution complète des écarts. Aucun GM, référence, seuil,
exclusion ou score historique modifié. Toujours36 cas à≥99%,49 à≥95%,194 échecs
de rendu,50 de setup et1 timeout (`vertices`).

**47/47 tests ciblés, Gradle0**, après correction/re-review Sol. Budget exact
B29408 et B−1 transactionnel vérifiés, transparence/ordre/hairline/clip et
récupération sur Surface publique. Les erreurs initiales de dérivation du
budget et l'absence de préfixes négatifs pré-patch sont documentées, sans
réécriture de l'historique. Globale unique240s rouge/incomplète :665 succès,
42 échecs déjà présents,1 interrompu sur708 identités communes;16 autres cas
du parent non atteints. Wrapper124, enfant Gradle143, XML globaux non finalisés.
Un ancien run voisin isolé avait terminé native133 malgré ses assertions PASS.
Revue globale Sol `1cd04aa77..cd6f923fd` validée, aucun finding C/I/M;
W7 non clos, aucune autorisation de merge.

## Lot couverture AA filtrée — 29 septembre 2026

Draft [#2419](https://github.com/ygdrasil-io/kanvas/pull/2419) empilée sur #2418,
renderer `82893045c`, branche `codex/w7-aa-mask-coverage`.
[Design](aa-mask-design.md), [plan](aa-mask-plan.md) et
[snapshot complet](aa-mask-82893045c.json). La source blanche AA4/resolve1
reste indépendante de la peinture ; stencil producer/cover partagent une
seule passe native, puis NORMAL et le matériau W5 couvrent le halo complet.

Les **631 identités / 443 éligibles** et toutes les références, scopes et
tolérances sont inchangés : **193 → 197 rendus**, **171 → 175 comparaisons**.
Les **193 anciennes empreintes RGBA sont identiques**, sans perte de rendu.
Les gains sont `blur2rects` (96,18 % des pixels ±2/canal, 499 ms),
`blur2rectsnonninepatch` (94,88 %, 651 ms), `blur_matrix_rect` (91,62 %, 5 845 ms)
et `blurcircles` (86,24 %, 3 281 ms). Ce sont quatre rendus nouveaux, pas une
parité globale. `vertices` reste timeout30s ; 195 échecs de rendu, 50 de setup,
14 rendus non comparables et 8 désaccords de dimensions restent au bilan.

Revues Sol des deux tâches validées après corrections des oracles publics.
Final proche **75/75, exit 0** ; dernier correctif tests-only **8/8, exit 0**.
Budgets dérivés avant exécution : direct B=299104, stencil B=723296 ; B−1
refuse avant publication, sentinel intact et récupération stable sur deux rendus.
La globale unique est **rouge/incomplète** : 680 succès, 43 échecs déjà présents,
un test interrompu sur 724 identités communes. Le watchdog wrapper240s retourne
124 ; l'exit propre de Gradle n'est pas observé. `formatsAlphaAndColorSpaceMatchOracle`
est interrompu (passait sur parent), `cubicDrawImageMatchesMitchellNetravaliOracle`
n'est pas atteint. Aucune nouvelle assertion en échec observée, sans garantie
sur les tests non atteints. Warnings JVM/Gradle conservés ; un fallback Kotlin
daemon avait eu lieu en Task1, absent des validations finales.

Revue globale Sol de `a21bb6472..70f2ff164` : zéro Critical/Important, draft
recevable sans merge readiness. Trois Minor suivis : deux commentaires encore
limités au direct/DirectTriangle et les warnings/outillage historiques.
AA+blur en
layer/Picture, styles autres que NORMAL, strokes/blends/clips complexes et
parité visuelle fine restent hors du contrat de ce lot ; W7 reste ouvert.

## Lot source AA racine — 29 septembre 2026

Renderer `470f62e63`, branche `codex/w7-root-aa-source`, PR draft
[#2418](https://github.com/ygdrasil-io/kanvas/pull/2418), empilée sur
[#2417](https://github.com/ygdrasil-io/kanvas/pull/2417). Path AA solid SrcOver
racine admis dans une frame W6 ordinaire, par source MSAA4 isolée et composite
immédiat ; aucun changement d'ownership, de budget ou d'autorité native.

Le [snapshot](root-aa-470f62e63.json) conserve 631 identités/443 éligibles :
**193 rendus (+1), 171 comparaisons ; les 192 anciens rendus sont pixel-identiques**.
Seul `rasterallocator` devient rendable, à 27,25 % de pixels ±2/canal ; ce n'est
pas une preuve de fidélité au GM Skia. Toujours 36 cas à ≥99 %, 48 à ≥95 %.
`vertices` reste timeout à 30 s. Références, seuils et exclusions inchangés.

Validation ciblée : **16/16 W7 et 53/53 contrôles voisins**, Gradle 0 (sélections
recoupées). Suite globale rouge/incomplète : 681 PASS, mêmes 43 échecs sur 725 cas
communs, un interrompu et quatre anciens cas non atteints. Le runner échoue
aussi lors de l'arrêt. Voir le [bilan](pilotage.md#lot-source-aa-racine--29-septembre-2026).

Arbitrages : nouvelle route exclue des frames W6b ; allocation plein viewport
conservatrice ; **Picture AA positif différé**, même sans clip, avec refus
transactionnel testé plutôt que playback revendiqué. AA filtré et PLUS restent
ouverts ; `PlusMergesAA`/`blur2rects` refusent toujours. W7 n'est pas terminé.
Reviews Sol tâche, correctif et ensemble approuvées pour la draft, sans
Critical/Important ; Minor suivi pour un témoin de refus Picture au root
(le permanent actuel couvre le playback en layer).

## Lot adaptateur Rect+CTM — 29 septembre 2026

Code `d45904e0b`, branche `codex/w7-layer-source-routing`, empilée sur
[#2416](https://github.com/ygdrasil-io/kanvas/pull/2416), PR draft
[#2417](https://github.com/ygdrasil-io/kanvas/pull/2417).
`GmCanvas.drawRect` conserve le rectangle local et la CTM pour scale/translate,
reflets inclus. Le renderer savait déjà les traiter ; l'adaptateur les
transformait prématurément en Path. Ce lot ne crée aucune capacité GPU.

Le [snapshot complet](rect-adapter-d45904e0b.json) mesure **192/443 rendus
(+26), 170 comparaisons et 36 cas à ≥99 % de pixels ±2/canal (+10)**.
Les 166 anciens rendus restent disponibles : 162 sont pixel-identiques,
trois scores progressent et `perlinnoise_localmatrix` change de pixels à score
constant (62,5 %). Aucun ancien score ne baisse. `crbug_899512` rend à 91,64 %.
Fixtures GM, identités, références, seuils et exclusions restent identiques,
**pas la capture IR**. `vertices` reste timeout à 30 s ; 29 refus changent
seulement de diagnostic, sans être comptés comme gains.

Les **3/3 nouveaux tests publics passent**, avec RED causal préalable et
Gradle 0 ; les 24 contrôles W6b/W7 AA passent aussi. Le replay Picture avec
clip reste défectueux. Le shard W5f complémentaire a 44/44 assertions PASS,
mais termine Gradle 1 / native 133 (`UNKNOWN`), pas un succès du run. Deux
témoins vérifient le second rendu Surface,
seul le blur vérifie aussi Picture. Le test historique de clip tourné échoue.
La tentative générale reste rouge/incomplète à 240 s : 685 PASS, les mêmes
43 échecs que #2416 et un interrompu. Les réserves de validation et la mesure
appariée figurent dans le [bilan](pilotage.md#diagnostic-des-sources-de-layers--29-septembre-2026).
Reviews Sol de tâche et de branche approuvées pour cette publication draft ;
aucun Critical/Important, un Minor de nommage de second rendu Surface reste suivi.
Le verdict ne permet ni merge ni clôture W7.

Les 43 refus génériques de segment layer restants ne sont pas une cause unique.
La prochaine priorité est le contrat de source Path AA racine puis sa couverture
filtrée, avec `PlusMergesAA`/`blur2rects` comme diagnostics, sans relâcher les
gardes actuelles. La fidélité du port, les transformations générales et les
clips Picture restent des dettes distinctes. W7 demeure ouvert.

## Lot preuve Sweep AA — 29 septembre 2026

Renderer `49d8224d3`, PR draft [#2416](https://github.com/ygdrasil-io/kanvas/pull/2416),
branche `codex/w7-sweep-aa-proof`, empilée sur la PR draft
[#2415](https://github.com/ygdrasil-io/kanvas/pull/2415). La preuve conserve les
classes zéro/subnormal/normal perdues par le hull des sélections Sweep, puis
traite les régions normales signées d'`Atan2` avec l'enveloppe inchangée.
Les deux bras eager restent validés. Un défaut de label d'allocation des stops
RRect, révélé ensuite et diagnostiqué par Astra, est corrigé au producteur ;
aucun montant mémoire, lifetime, shader ou contrôle d'autorité n'est relâché.

**9/9 tests publics ciblés passent, Gradle 0.** La tentative générale à 240 s
atteint 730 cas : 686 réussites, 43 échecs, un interrompu. Six anciens échecs
passent, aucun nouvel échec d'assertion dans cette intersection ; 49 cas de
la tentative de base ne sont pas atteints et un ancien succès est interrompu.
La suite reste donc rouge et incomplète, sans conclusion de performance.

La review de tâche Sol approuve le code ; la sensibilité de `PATH_STROKE` est
confirmée par mutation causale postérieure, mais son RED avant implémentation
n'est pas attesté. Cette réserve TDD reste explicitement ouverte.
La review Sol de toute la branche `14d2be4f8..6ad868c91` autorise sa publication
draft (Critical 0 / Important 0 / Minor 0), pas le merge ni la clôture W7.

Le [snapshot complet](sweep-aa-49d8224d3.json) conserve **166/443 rendus**, 144
comparaisons et 26 cas à ≥99 % de pixels ±2/canal. **Les 166 empreintes RGBA,
les issues et diagnostics sont inchangés** : aucun gain de GM ou de fidélité
mesuré. `vertices` reste timeout à 30 s. Voir le [bilan](pilotage.md#lot-preuve-sweep-aa--29-septembre-2026).

La combinaison RRect × vingt wrappers n'est pas établie. Les défauts Radial,
Conical, AA géométrique et opacité × bords fractionnaires restent ouverts.
Prochaine priorité de diagnostic : le groupe `w6a.layer.unsupported_child`
(51 premiers refus), sans promettre autant de rendus gagnés. W7 reste ouvert.

## Lot image/opacité — 29 septembre 2026

Renderer `bef3af6fa`, PR draft [#2415](https://github.com/ygdrasil-io/kanvas/pull/2415)
(`codex/w7-image-opacity-authority`), empilée sur
la PR draft [#2414](https://github.com/ygdrasil-io/kanvas/pull/2414).
`OpacityV1` reconnaît précisément l'enfant `ImageMaterialProgramV3` comme
une chaîne V4 ; le graph numérique et la preuve existants redeviennent
cohérents, sans modifier les contrôles d'autorité du renderer.

Le [snapshot complet](image-opacity-bef3af6fa.json) mesure **166/443 rendus**
(+1), **144 comparaisons** et toujours **26 cas à ≥99 % de pixels ±2/canal**.
`lattice2` passe du refus à un rendu sans refus, à **54,0875 %** : la fidélité
reste imparfaite. Les **165 anciens rendus sont identiques pixel à pixel**.
Références, scènes, scopes, seuils et timeout `vertices` restent inchangés.

Le témoin public est RED avant correction puis GREEN ; **9/9 tests publics
ciblés passent avec Gradle 0** dans une exécution isolée. La suite générale
isolée reste inachevée à 240 s : **728 réussites, 50 échecs, un interrompu**,
exactement les 779 mêmes identités et résultats que la base. Les exécutions
préliminaires ayant brièvement chevauché sont écartées au profit de ces
rejeux sérialisés. Voir le [bilan](pilotage.md#lot-imageopacité--29-septembre-2026).
Revues Sol de tâche et de branche : aucun défaut bloquant ; publication draft
approuvée, couverture opacité × bords AA fractionnaires à compléter.
Prochain lot : preuve Sweep AA ;
W7 et les gates W6 restent ouverts, sans merge readiness.

## Lot preuve CPU — 29 septembre 2026

Renderer `b256b3d68`, PR draft [#2414](https://github.com/ygdrasil-io/kanvas/pull/2414)
empilée sur #2413 :
le cache de preuve utilise les dépendances conservatrices sans élargir
les domaines numériques ou budgets. Le [snapshot](proof-b256b3d68.json)
mesure **165/443 rendus** (+1), **143 comparaisons**, **26 cas à ≥99 %**
et **un timeout** au lieu de trois. Les 164 anciens rendus sont identiques
pixel à pixel. `ninepatch-stretch` rend en 26,168 s (78,15 %, marge faible) ;
`lattice2` atteint un refus d'autorité explicite ; `vertices` reste timeout.
Le périmètre, les références et les seuils sont inchangés.

**20/20 tests publics ciblés et 476/476 tests math passent.** Le test W5d
auparavant bloqué termine en 1,756 s mais conserve un refus numérique AA.
La tentative complète, bornée à 240 s, reste inachevée : **728 réussites,
50 échecs, un test interrompu** ; aucun ancien test vert observé ne devient
rouge. Le [bilan et ses limites](pilotage.md#lot-cache-de-preuve-cpu--29-septembre-2026)
et le [plan](proof-evaluation-plan.md) distinguent performance, admission et
fidélité. W7 reste ouvert, sans merge readiness.

La [validation complémentaire par dix lots bornés](pilotage.md#validation-complémentaire-par-lots-bornés)
atteint **411/412 réussites W6/W7 Surface**, **598/598 cas de géométrie W5h**
et **92/92 cas W5f image filter**. La sélection générale GPU/API/blend observe
**1 990 réussites et 1 254 échecs** : leur antériorité n'est pas établie
individuellement. Six classes W5 restent partielles ou non atteintes ; les
timeouts et sorties natives 133 restent séparés des assertions. Ces lots
ne constituent ni une suite complète ni un nouveau gain de similarité.

Le premier refus de `lattice2` est localisé : un wrapper image/opacité déclare
un programme V1 avec une autorité V4. La prochaine correction doit produire
une chaîne V4 authentique, sans desserrer le witness. Le diagnostic Sweep AA,
relu avec Astra, propose ensuite de préserver localement les classes F32
perdues par le hull des sélections. **Ces deux corrections restent à implémenter** ;
seuls les diagnostics et la validation sont ajoutés à ce checkpoint.

## Lot pointillés — 29 septembre 2026

Renderer `5f971f750`, PR draft [#2413](https://github.com/ygdrasil-io/kanvas/pull/2413)
empilée sur #2412 : l'égalité des
pointillés immuables est réparée dans `math`, sans desserrer les seals.
Le [snapshot final](dash-5f971f750.json) conserve **164/443 rendus**, tous
identiques pixel à pixel à #2412, **142 comparaisons** et **26 cas à ≥99 %**.
Les 631 identités, scopes, références, seuils et trois timeouts sont inchangés.

L'amélioration de précision AA a été **retirée après avis Astra** : malgré
le gain sur `circle_sizes`, elle faisait perdre `parsedpaths` et
`perspective_clip`. Le [plan et le diagnostic](aa-dash-repair-plan.md)
préservent l'expérience ; **le défaut AA reste ouvert**.

**77 tests publics ciblés et 476 tests math geometry passent** sur le code
final, Gradle 0. La tentative de suite Kanvas complète est inachevée : 39 échecs observés,
puis arrêt d'un calcul long dans la preuve CPU d'un test de gradient W5d.
Les [limites de validation](pilotage.md#validation-du-lot-et-limite-de-la-suite-complète)
sont explicites. W7 et les gates W6 ne sont pas clos ; aucune merge
readiness n'est revendiquée.

## Lot standalone rect/path — 29 septembre 2026

PR draft [#2412](https://github.com/ygdrasil-io/kanvas/pull/2412),
renderer `718445e6e`, empilée sur #2411 : **164/443** rendus (+41),
**142** comparaisons (+37), **26** cas à ≥99 % de pixels ±2/canal (+6).
Les 123 anciens rendus restent disponibles, 118 identiques bit à bit.
Les références, scènes, seuils, exclusions et trois timeouts restent inchangés.

Le [bilan détaillé](pilotage.md#lot-standalone-rectpath--29-septembre-2026)
documente les cinq anciens rendus modifiés, notamment le recul de
`circle_sizes`, et les deux nouveaux rendus à 0 % liés à des ports non fidèles.
La médiane appariée des 105 anciennes comparaisons reste 54,64 %.
**69 tests ciblés passent**, mais le test historique de pointillé à phase
négative reste en échec, reproduit avec le routage historique et diagnostiqué.
Le [plan](stroke-routing-plan.md) et le [snapshot](strokes-718445e6e.json)
conservent la preuve. W7 reste ouvert, sans revendication de parité globale
ni de merge readiness.

## Pilotage et mesure fraîche — 29 septembre 2026

PR draft empilée : [#2411](https://github.com/ygdrasil-io/kanvas/pull/2411)
sur #2410.

Le [pilotage](pilotage.md) remplace le comptage de rendus comme indicateur
unique : **123/443** GMs éligibles produisent une image, **105** peuvent être
comparées aux références actuelles ; **20** de ces comparaisons atteignent
99 % de pixels à ±2 par canal. Les défauts de port et de dimensions restent
visibles. Les scores historiques et les références ne sont pas modifiés.

Les suites publiques W6/W7 disposent explicitement du runtime GPU à leur
fin : **49/49 tests passent, Gradle exit 0**. Le crash `133` décrit ci-dessous
est historique pour ces deux suites. W7 et les gates W6 ne sont pas clos.

## Checkpoint AA W6/W7 — historique

Les phases `PathRenderPass` AA W6 sont maintenant reconnues par leur autorité
`W4dAaSource`, séparément des anciens seals de paire stencil W4c/W4d. Le
`PreparedGPUFrame` vérifie l'usage du D24S8, le `load/store` producteur/cover
et l'unique operand emprunté. Le validateur natif garde l'égalité stricte des
indices de vues stencil, contrôle leur identité entre les deux phases, ainsi
que le `resolve`, la cible couleur et leurs opérations de charge. L'autorité
de source AA contrôle désormais l'égalité exacte du `load/store` sémantique,
et non seulement sa nullité. La phase directe reste sans profondeur.

Sur cet hôte, les **9/9 assertions JUnit W7 AA** passent, dont les quatre
témoins stencil/alpha auparavant bloqués ; les deux contrôles publics W6
adjacents passent aussi (**2/2**). `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` passent. **La tâche Gradle `:kanvas:test` reste
rouge** : l'exécuteur natif quitte avec `133` après les assertions. Aucun
inventaire GM, score, PNG ni dashboard n'a été régénéré ; ces témoins locaux
ne ferment pas W7, les gates W6 2A1/2B ni la question de conformité ISO.

## Checkpoint natif sRGB 4×/resolve — historique

Une sonde autonome temporaire, sur la session wgpu4k de cet hôte (Apple M2 Max),
a exécuté deux fois un rendu `RGBA8UnormSrgb` 4× avec resolve sRGB 1×, lecture
du pixel rouge `[255, 0, 0, 255]` et aucune erreur de validation. Elle a réussi
avec et sans attachement `Depth24PlusStencil8` 4×. Cela prouve ce couple
format/resolve sur cette session, pas tous les adaptateurs ni la sémantique
stencil complète de W7.

Le worktree courant contient une détection une fois par session GPU : la table
annonce sRGB 4×/resolve seulement si les deux rendus natifs, la complétion de
queue, les scopes de validation et les pixels lus réussissent ; sinon elle
conserve `{1}` sans resolve. La relecture Astra a fait corriger le contrôle du
`Result` de queue et l'unicité du `popErrorScope`. L'ancien test de table `{1}`
constante a été retiré ; la preuve de comportement reste dans les tests publics
`Surface`, sans nouveau test d'infrastructure. La sonde et ce relevé sont
inclus dans la PR draft #2410 ; ils ne ferment pas les gates globaux.

L'activation a révélé et permis de corriger des seals W6/W7 jusque-là masqués
par le refus de capacité : opérations V/I du `Path` AA, classification de
`PathAaColorComposite`, comptage d'un draw stencil public une seule fois, et
absence de template de blend couleur pour le producteur stencil. Le témoin
`DirectTriangle` a maintenant un budget exact **B = 26 980 octets** : root
196, readback 1 792, layer 4×4 64, AA 4× 256, resolve 64, pools V/I/U
16 384/4 096/4 096, uniforme W4d 16 et uniforme de source solide 16. La
borne B admet et B−1 refuse avant écriture du sentinel ; la même `Surface`
reste réutilisable après refus.

Rejeu intermédiaire : W7 JUnit **9 tests, 5 passés, 4 échoués** ; les quatre
échecs stencil/alpha atteignent `invalid.preflight.prepared_frame` sur le seal
historique `Prepared path seal, unified pair, writable attachment use,
load/store, and native operand must agree exactly`. Les deux contrôles W6
adjacents passent (JUnit **2/0/0/0**). La tâche Gradle sort toujours 1, avec
l'exécuteur natif `133` après les assertions. Les pixels W7 directs, l'ordre
des enfants, la translation et B/B−1 étaient positifs sur cet hôte. Le
checkpoint plus récent ci-dessus traite ce gate de seal préparé et celui des
operands natifs ; **W7 n'est pas terminé ni merge-ready**.
Fonts, codecs, `jpg-color-cube`, renders/scores/dashboard GM et gates W6
2A1/2B restent hors de ce checkpoint.

## Checkpoint des operands W6/W7 après relecture Astra

Le commit `bd5eba11a6fc42c064c8279f2e3b788c77654509` rétablit la clé D24S8
des passes stencil W6 génériques `StencilGeometryProducerV3`/`StencilCover` et
reconnaît le `depthStencil` scellé des `PathRenderPass` AA W7 dans le seal des
operands. Il ne modifie ni le format couleur ni la table de capacités GPU.
La relecture Astra ciblée ne relève aucune nouvelle régression dans ce diff.

Le témoin public `path fill stroke and hairline retain stencil through a
translated layer` échouait avant correction à `Surface.render()` sur
`invalid.preflight.encoder_lowering` (clé D24S8 empruntée absente). Après
correction, ses pixels passent, ainsi que le contrôle W6 inverse-even-odd
et deux témoins W7 de refus de capacité : XML JUnit W6 `2/0/0/0`, W7
`2/0/0/0`. La tâche Gradle demeure en échec car l'exécuteur natif quitte
avec le code `133` après les assertions. Les témoins W7 atteignent encore
`w4d.general.texture-sample-support-unavailable` : ils ne prouvent ni pixels
AA positifs ni budget B/B−1 sur ce runtime. Une sonde native sRGB 4× + resolve
serait un travail diagnostique distinct ; aucune capacité n'est inférée du
seul matériel Mac et aucune conformité ISO n'est revendiquée.

## Portée et preuve

Ce relevé ouvre le diagnostic W7 sur le commit `b99e321c6bf6b7776fefd69a56fbe7439cc7a9cb`, empilé sur W6 `fc6e57209a69de43f57854886853714ccb1cad58`.
Le commit W7 adapte seulement trois fixtures de lighting à l'API 3D déjà publiée ;
`:integration-tests:skia:compileTestKotlin` passe et la revue Sol du diff ne relève
aucun problème. L'inventaire a été généré par
`generateSkiaGmInventory` avec une sortie non suivie dans
`/private/tmp/w7-provisional-source-inventory-b99e321c6b.json`.
Ni rendu de référence, ni score, ni dashboard n'ont été régénérés.

Le relevé reste **provisoire** : les gates W6 2A1/2B ne sont pas closes et
`jpg-color-cube` reste en quarantaine `quarantined-resource-limit`, filtrée
avant setup et rendu. Fonts et codecs sont exclus du périmètre. Les scores
enregistrés sont historiques ; l'audit `strict=true`, `orphanCount=0` vérifie
seulement leur cohérence de registre, pas la similarité des pixels actuels.

## Checkpoint Task 4 — budget AA et refus hors route

Le commit `b8b035b329163dba37f21c6de7a85d4cd851e383` ajoute les témoins publics
W7 du budget et des filtres, ainsi que le refus W6 explicite qui empêche qu'un
`Path` AA filtré dans une layer perde sa provenance en
`w6a.layer.unsupported_child`. Image et mask filter retournent maintenant
`w6a.layer.unsupported_spatial_filter` avant que W6b ne retire leur payload
pour une voie enfant W4d. Cela ne constitue ni une route image-filter
`ResolvedColor`, ni une implémentation de `ResolvedCoverage` ; ces deux travaux
restent différés.

L'estimation initiale `B = 27 756` du témoin 7×7 `DirectTriangle` traitait
à tort la layer et la source AA comme des surfaces 7×7, et omettait le second
uniforme de 16 octets. Le checkpoint natif ci-dessus corrige ces dimensions
aux bornes conservatrices 4×4 du triangle et établit **B = 26 980** sans
rabais d'aliasing ou de cache. `DirectTriangle` ne déclare pas de D24S8 ; le
budget W6 reste checked-I64 et charge toute ressource déclarée, staging et
ressources des autres lanes inclus, sans économie de lifetime.

Lors de ce checkpoint antérieur, `GPUBackendRuntimeNative` publiait sRGB 4×
comme `{1}`. Le test
public observe donc d'abord exactement
`w4d.general.texture-sample-support-unavailable` et ne peut pas atteindre
l'allocation native ni établir ici que B admet et B−1 refuse. L'oracle
capable-backend conserve ce contrôle B/B−1, son sentinel atomique et la
réutilisation de `Surface`, mais aucune réussite B/B−1 n'est revendiquée sur
cette machine. Les compiles `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin` et `:kanvas:compileTestKotlin` passent. Le
sélecteur filtre passe avec Gradle 0 ; le sélecteur B passe côté JUnit mais
Gradle termine 1 car le processus natif quitte 133 après l'assertion.
Les sept autres sélecteurs publics W7 (triangle, concave/even-odd/stencil,
ordre, alpha et translation) ont aussi chacun une assertion JUnit passée puis
ce même exit natif 133 : cette observation ne rend pas la suite Gradle verte.

Un inventaire frais a été produit uniquement dans
`/private/tmp/w7-task4-gm-inventory.json` par
`generateSkiaGmInventory -Pgm.inventoryOutput=...` (succès, 4 min 10 s), sans
PNG, référence, score ni dashboard. Il compte 631 GMs, 443 éligibles, 124
rendables et 262 échecs de rendu. La sonde antérieure de 44 chemins AA n'a pas
conservé ses identités, donc aucune comparaison par GM ne serait honnête. Le
relevé frais contient encore 52 `w6a.layer.unsupported_child`, sans permettre
d'identifier ces GMs aux 44 historiques, et observe aussi
`imagefilters_xfermodes` sur le nouveau refus
`w6a.layer.unsupported_spatial_filter`. Cette observation GM et le témoin
`Surface` n'autorisent aucune promesse de réparation globale. Les gates W6 2A1
(leases/programmes natif) et 2B restent ouverts ; aucune conformité ISO W7
n'est déclarée.

Après la revue Sol, le gate D24S8 est rendu dépendant de la stratégie dans la
seule source W6 `allowAaColorSource` : `DirectTriangle` n'exige plus une
capacité D24S8 qu'il ne déclare pas, tandis que `StencilCover` exige toujours
D24S8 4× et son opération stencil. Le W4d AA autonome conserve son préflight
et budget depth conservateurs. Le témoin B vérifie aussi désormais la
récupération de la même `Surface` après le refus sRGB 4×, avec un draw
hard-edge et ses pixels/scopes ; cela ne transforme pas cette preuve en B−1.

## Résultats observés

| Mesure | Inventaire W0–W2 suivi | W7 initial | W7 après correction lifetime |
| --- | ---: | ---: | ---: |
| GMs enregistrées | 631 | 631 | 631 |
| Éligibles | 450 | 443 | 443 |
| Exclues codec / font / quarantaine | 54 / 126 / 1 | 54 / 133 / 1 | 54 / 133 / 1 |
| `Surface.render()` tenté | 379 | 386 | 386 |
| Rendu disponible | 83 | 89 | 124 |
| Échec terminal de rendu | 296 | 297 | 262 |
| Setup échoué, tous scopes | 75 | 60 | 60 |
| Éligibles sans tentative | — | 57 | 57 |

Sept GMs sont passées d'`eligible` à `excluded-font` ; ce changement de
dénominateur doit rester visible dans toute comparaison. Par identité de GM,
46 GMs auparavant non rendues rendent désormais, mais 40 auparavant rendues
ne rendent plus. Ce n'est donc pas une progression monotone, malgré le gain
net de six rendus. Parmi les 40 pertes, 25 échouent sur l'invariant de durée
de vie des ressources.

## Premiers groupes de causes

Les nombres ci-dessous décrivent l'inventaire W7 **initial** et comptent les échecs terminaux des GMs éligibles, sauf
la dernière ligne, qui concerne le setup. Une GM n'est comptée qu'à son premier
diagnostic ; ce regroupement n'établit pas encore la cause racine.

| Diagnostic initial | GMs | Lecture provisoire |
| --- | ---: | --- |
| `w6a.layer.unsupported_child` | 54 | Admission des enfants layer/source/geometry à étudier comme axe transversal. |
| `Resource lifetime must be non-empty` | 31 | Invariant `PlanResource.of` ; 25 anciennes réussites perdues, priorité de diagnostic. |
| `geometry.path.fan_budget_exceeded` | 23 | Limite de topologie path, à distinguer d'une erreur de géométrie. |
| `scalar_aa_not_promoted` | 18 | Promotion de couverture AA non admise. |
| `runtime_effect.unregistered_semantics` | 12 | Contrat d'enregistrement des effets runtime à classifier. |
| `unsupported.pipeline.capability_missing` en setup | 14 | Capacité GPU et taille de ressource à distinguer d'un manque de route. |

Les 57 éligibles sans tentative sont des échecs de setup, dont des stubs
explicites et des contraintes de taille GPU. Aucune gate « 100 % exécutées »,
« 95 % conformes » ou « zéro refus non classifié » ne peut être revendiquée
sur ce relevé. Aucune mesure fraîche de similarité n'a été faite.

## Ordre de triage proposé

1. ~~Tracer les 31 lifetimes vides jusqu'au producteur du `PlanResource`.~~
   Corrigé et mesuré ci-dessous ; conserver un test pixel représentatif.
2. Décomposer les 54 refus `w6a.layer.unsupported_child` par type exact
   d'enfant et contrat W6 ; ne pas élargir l'admission à l'aveugle.
3. Distinguer les limites explicites de ressources et les stubs des manques
   sémantiques réutilisables (path, AA, materials, runtime effects).
4. Après fermeture des gates W6 et stabilisation du périmètre, produire une
   nouvelle baseline de rendus/scores et seulement alors mesurer la conformité
   pixel, la colorimétrie, les meshes et les combinaisons rares.

Ces étapes constituent un triage, pas encore un plan d'implémentation approuvé.

## Correction bornée de la durée de vie W5e

Le diagnostic a localisé les lifetimes vides dans `RenderGraph.issueW5e` :
le wrapper W5a composite ne porte aucune passe, alors que ses lanes en portent.
La durée de vie des images décodées utilise désormais la somme I32 vérifiée
des passes de ces lanes (et garde le nombre de passes direct hors composite).
Dans `FrameSourceLayoutV4.prepareImageFrame`, le pic natif W5a est calculé
explicitement à partir de sa géométrie, des stops et du bruit ; le budget
extérieur W5e conserve intégralement images, runtime storage et uniformes.
Les invariants de `PlanResource.of` et l'égalité de budget du lowerer W5a
restent stricts.

Un test de pixels public sur `bitmap_premul` a échoué avant le correctif sur
`Resource lifetime must be non-empty`, puis a rendu les pixels de référence
après celui-ci, avec cinq dispatches, zéro refus et aucun diagnostic.
L'inventaire frais, écrit hors dépôt dans
`/private/tmp/w7-post-lifetime-inventory.json`, mesure 124 rendus disponibles
contre 89 avant correction : 35 gains, aucune perte. Les 31 refus de lifetime
ont disparu ; quatre autres GMs auparavant refusées sur
`invalid.material.image.contract` rendent aussi. Ce relevé ne mesure toujours
pas la similarité des 35 nouveaux rendus et ne régénère ni scores, ni
références, ni dashboard. Les gates W6 demeurent ouvertes.

La vérification ciblée `:integration-tests:skia:test` du GM passe. Trois cas
publics image, noise/gradient/image et runtime passent également leurs
assertions, mais leur processus `:kanvas:test` quitte avec le code natif 133
après les tests. Le cas public `sharedImageKeepsClampMatrixOrderAndMixedFrameStorage`
échoue sur `failed.frame-coordinator.preflight` ; le même diagnostic et la même
sortie 133 ont été reproduits isolément sur le HEAD W7 avant cette correction.
Ces résultats ne constituent donc pas une suite Kanvas verte et ce problème
préexistant reste distinct du correctif de lifetime.
