# W7 — diagnostic borné de l'autorité couleur

## Résultat — 2 octobre 2026

Les différences de couleurs dominantes des PNG bruts ne démontrent pas un
défaut de paint/renderer. Le loader existant convertit les références ICC en
sRGB avant comparaison. Les plateaux décodés conservent les primaires attendues
à ±2. Les désaccords résiduels observés portent sur les intensités des pixels
des traits ; ils sont compatibles avec des différences de couverture, de
placement ou de composition, dont la cause exacte reste à isoler.

Base publiée : draft[#2435](https://github.com/ygdrasil-io/kanvas/pull/2435),
head6f059f0dcf364cc351f984573f1fa9b7610ba337, source/tests/artefacts inchangés
depuis2e044cd8e. Investigation read-only : aucun nouveau test d'infrastructure,
changement produit, référence, codec externe, seuil, score ou scope.
Probe temporaire conservée dans /private/tmp/kanvas-w7-color-authority.K4ULsX,
pas une implémentation à conserver dans les modules.

## Contrat réellement consommé

ReferenceManager.loadReference appelle ComparisonUtils.loadPngAsSrgbRgba,
qui ouvre le codec existant, conserve son espace source et convertit Matrix/TRC
vers sRGB. Le PngCodec existant expose le profil ICC résolu dans getInfo ;
les noms ICC connus peuvent être substitués par ComparisonUtils, le descriptif
Google/Skia/CAB9EFCEBFA516F3185A3C8B8072A056 de ces références suit le profil
réel. Lecture seule de cette chaîne, aucun décodage implémenté ou modifié.

Les deux PNG références ont ce même ICC ; leurs triplets bruts
vert(145,245,68), rouge(202,59,18), bleu(43,13,242) ne sont pas les
triplets sRGB du rendu. Une évaluation indépendante LittleCMS de ces seules
constantes donne respectivement(1,255,0),(255,1,0),(0,0,255), sans convertir
ou sauvegarder une image de référence. Cela réfute l'interprétation des
couleurs brutes comme des paint literals erronés.

## Probe du chemin Kanvas effectivement exécutée

Une petite main Java temporaire appelle la méthode publique existante
ComparisonUtils.loadPngAsSrgbRgba sur les quatre fichiers réels, puis compte
les couleurs et les correspondances. Pas de renderer CPU/GPU, mock, test
JUnit ni fallback ; aucune écriture dans le repository.
Invocation originale20676 terminale0, puis audit séparé0 des comptes :
la similitude±2 reproduit exactement celle du corpus qualifié.

| GM | Pixels correspondant à±2 | Score probe = corpus |
| --- | --- | --- |
| ctmpatheffect |478822 /480000,1178désaccords|99.75458333333333%|
| teenyStrokes |318473 /320000,1527désaccords|99.5228125%|

Dans ctmpatheffect,4612pixels référence décodés(2,255,0,255) correspondent
aux pixels verts purs actuels ;103des4717pixels verts purs actuels
diffèrent encore à±2, avec notamment référence(0,224,31,255).
Dans teenyStrokes, les plateaux de référence décodés sont noir(0,0,0),
rouge(255,0,0), vert(2,255,0), bleu(0,0,255), alpha255.
Les dominantes partielles diffèrent : référence gris223/32, actuel188/225.
727des4157pixels actuels à primaire pure diffèrent encore ; ces chiffres
ne distinguent pas seuls géométrie, couverture et loi de composition.

Références SHA256 inchangés :
ctmpatheffect ca1c66a9aef5d092db77ce919b788fddeb60c4094e24f33186f6b722218d8aa1 ;
teenyStrokes 78cbf8bfe9b44e74f282311f517f86daa20ff0512f5770b6bc79819544a47597.
Aucun nouveau corpus natif ni gain revendiqué ; scores/references restent ceux
de la qualification CTM. Alpha opaque ne permet pas d'exclure une erreur
géométrique : le fond blanc doit rester considéré.

## Sources primaires et limites

[Skia strokes.cpp au commit fixé](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/gm/strokes.cpp)
contient TeenyStrokesGM, pas le teenystrokes.cpp indiqué par l'annotation du port.
Couleurs, échelles, widths et séquence de ses lignes concordent avec le port
dans les entrées examinées ; absence d'erreur de couleur primaire démontrée,
pas approbation générale de tous les arrondis/backend.

[Skia patheffects.cpp au même commit](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/gm/patheffects.cpp)
utilise blue/green opaques et AA=true. Son effet mappe la normale de rayon
dans le CTM, gonfle sa longueur de0.5pixel et la remappe ; le port emploie
cette même construction pour ses deux CTM. Aucune source renderer fautive
ni approximation acceptable déduite de cette lecture seule.

La visualisation initiale du lot CTM doit donc être qualifiée : des couleurs
de stockage/profil différentes ne prouvaient pas un défaut de couleur pleine
après normalisation. Les écarts de contenu/couverture subsistent, les métriques
et les pixels du corpus ne changent pas. La source exacte des références et
leur espace de blending Skia ne sont pas prouvés par le seul ICC.

## Prochaine étape bornée

### Rampes localisées, sans rendu nouveau

Une seconde main Java temporaire, ReferenceRampProbe.java, charge les mêmes
PNG par le loader existant et imprime des coordonnées explicites. Original
exec terminé 0 (chunk544fd3), stdout de 13 lignes JSON entièrement lu, stderr
vide. Audit séparé : 4 rampes/37 pixels ctmpatheffect et 9 rampes/92 pixels
teenyStrokes, 129 couples au total. Le premier audit attendait 81 pixels teeny
par erreur de comptage du contrôleur ; le compte réel lu était 92, puis l'audit
corrigé termine 0. Aucun pixel ni probe relancé pour obtenir cette correction.

À y60 dans teenyStrokes, le trait noir vertical centré x70 a ses transitions
aux mêmes colonnes x67/x72 : actuel (188,188,188), référence (128,128,128).
x68..71 sont noirs des deux côtés ; x66/x73 blancs. Même écart 188/128 dans
les canaux non saturés des traits rouge/vert/bleu à leurs colonnes correspondantes.
Ce cas distingue les intensités aux mêmes coordonnées, pas un trait absent.
Sur la diagonale noire à y60, x106 donne 225/223, x107 donne 0/32, x108..112
sont noirs, puis la rampe se répète symétriquement. Les deux phénomènes ne
doivent pas être réunis en une cause unique non démontrée.

Dans ctmpatheffect à y150, x138 donne actuel (188,188,255) versus référence
(115,139,231), x139 (0,225,137) versus (0,215,39), puis l'intérieur vert concorde
à ±2. Sous CTM anisotrope, les bandes mesurées ont davantage de niveaux et
d'intensités résiduelles. Ces 129 échantillons ne localisent pas tous les écarts.

Lecture du contrat existant : RenderConfig/SkiaGm restent LINEAR par défaut.
SRGB_ENCODED et sa cible UNORM existent déjà, mais CompositionAdmissionV1
refuse AA/Path/stroke fini et W4dGeneralPathPlanCompiler ferme explicitement
l'AA encodé. Les observations motivent un témoin public indépendant des domaines
et de la couverture ; elles ne prouvent pas un défaut du domaine LINEAR.
Le support AA encodé est une capacité absente à concevoir séparément, pas une
constante à remplacer ni une raison de retirer ses gardes.

### Direction de l'expérience suivante

À partir des rampes sRGB archivées ci-dessus, départager placement/coverage AA
et composition/transfert avec un petit témoin Surface à valeurs indépendantes.
Arrêter à la première frontière causale ; aucune correction de couleurs,
normalisation du comparateur, référence/seuil ni décodage externe autorisée
par cette seule observation. Math reste l'autorité numérique I/F32/64.
AA=false de crbug, RRect/I2, inverse/filter, pathops SSIM et globale restent ouverts.

## Relecture et disposition

Revue Sol indépendante du diagnostic : aucun Critical/Important ; la suggestion
Minor sur la distinction intensité mesurée/couverture inférée est appliquée.
Les exécutions, l'identité des jars, l'évaluation LittleCMS et les sources Skia
restent des reçus du contrôleur, non reproduits par cette revue. Aucune autorité
nouvelle sur le renderer, une loi AA/gamma, la provenance complète des références
ou les gates globales. Le diagnostic peut guider la prochaine expérience,
pas justifier seul un changement produit.
