# W7 — port fidèle de sk3d_simple

## Intention

Corriger la scène comparée avant d'attribuer ses différences au renderer.
L'utilisateur délègue les arbitrages W7 et demande des PR draft empilées,
avec implémentation par subagent et relecture indépendante. Base : draft
[#2426](https://github.com/ygdrasil-io/kanvas/pull/2426), `b31185372`.
Ce lot ne clôt ni W7 ni les gates globales.

Le corpus au produit `3398dc3` rend ce GM à 51,931111 % à ±2 et SSIM 0,638501.
Le port emploie une rotation 2D, alpha 0,5 et AA implicite. La
[source Skia figée](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/3d.cpp)
emploie une caméra perspective, une rotation autour de Y, alpha 136/255 et
`SkPaint` sans AA. Ce sont trois défauts concrets du port, pas une preuve que
leur correction résoudra toute la différence de couleur avec la référence.

## Correction retenue

Réutiliser `math` : `Matrix4x4F32.rotate`, `lookAt`, `perspective`, `scale`,
`invert` et `asM33`. Pour ce dessin dans le plan z=0, `asM33` conserve
l'homographie (transformation projective plane) ; aucune nouvelle API 3D.

Paramètres Skia : near 0,05, far 4, angle π/4, eye `(0,0,1/tan(angle/2)-1)`,
centre `(0,0,0)`, up `(0,1,0)`, rotation Y π/6, viewport scale `(150,150,1)`.
Ordre exact : `viewport * perspective * camera * model * inverse(viewport)`,
puis translation `(150,150)` avant Rect `(-100,-100,100,100)`.
Appliquer cet ordre séparément au draw rouge direct et au draw bleu dans une
vraie `PictureRecorder` de bounds `(0,0,300,300)`, puis `drawPicture`.
Rouge `0xFFFF0000`, bleu `0x880000FF`, `antiAlias=false` explicite pour les deux.

Conserver les interfaces et chemins existants : le `GmCanvas` direct projette
déjà ses Rect généraux en Path ; le Canvas du recorder conserve Rect+CTM.
Ne pas remplacer la Picture par un dessin direct ou un Path préprojeté pour
masquer un refus. Un refus natif confirmé déclenche un diagnostic de son
contrat avant tout changement de renderer. Une approximation affine ou une
retouche de référence n'est pas une solution.

## Preuves et limites

Trois témoins publics natifs, construits indépendamment avant Surface :
silhouette perspective, couleur intérieure distinguant alpha 136 de 128 et
omission Picture, puis bord hard distinguant AA. Render/Readback réels,
zéro diagnostic/refus/skip et répétition identique. Pas de tests
d'infrastructure, mocks, forwarding ou inspection de source.

Pour contrôler la géométrie sans utiliser les matrices produit, poser
`q=cot(π/8), c=cos(π/6), s=sin(π/6), u=(x+150)/150, v=(y+150)/150`.
Les coordonnées device indépendantes sont `(150*q*c*u/(q+s*u),
150*q*v/(q+s*u))`. Les coins valent environ `(40,504985;46,771127)`,
`(160,949968;37,169803)`, `(160,949968;185,849015)`,
`(40,504985;233,855637)`. Les témoins ne déduisent rien des pixels mesurés.
Sous le domaine LINEAR existant, l'intérieur rouge puis bleu a RGB sRGB
arrondi `(182,0,193)`, A=255 ; le test utilise ±1 sur R/B, G/A exacts.
Cela ne revendique pas que la référence Skia a été générée dans ce domaine.

Références, seuils, domaine LINEAR, exclusions et timeout 30 s/GM restent
inchangés. Mesurer le même corpus 631/443 et distinguer changement de scène,
capacité de rendu et fidélité. Fonts, codecs/décodage externe et jpg-color-cube
restent exclus. Aucune relaxation de proof, epsilon, cap ou oracle existant.
Toute nouvelle géométrie appartiendrait à math avec nomenclature I/F32/64 ;
aucune n'est prévue. Pas de fallback CPU, de routage par nom de GM ni de merge.

## Prérequis renderer révélé par le port fidèle

Les trois tests échouent avec la scène corrigée sur
`unsupported.surface.prepared.mixed-composite-topology` (archive
`/private/tmp/kanvas-w7-sk3d.WUms9p/green-1`, trois échecs, sorties 1/1).
Le gate Surface ne nomme W6 pour une Picture racine que via une famille AA.
Une fois cet owner atteint, le Rect projectif hard n'a pas de source : W4a
ne possède que les transformations axis-aligned et W4d n'autorise pas la
projection Rect dans son contrat source par défaut. Ce second manque est
un diagnostic statique, pas un nouveau refus natif déjà mesuré.

La correction retenue est un prérequis renderer distinct (Task 2), puis la
reprise de Task 1. Deux alternatives sont écartées : élargir indistinctement
le contrat AA/root-only ouvrirait des combinaisons non prouvées ; préprojeter
le Rect enregistré changerait la scène et masquerait la capacité manquante.
L'avis Astra ciblé recommande de réutiliser les sources hard W4d et
l'assemblage d'occurrences W6, sans nouveau compositor ni backend.

### Contrat fermé de Picture hard

Nommer W6 pour un vrai DrawPicture contenant un Rect/Path FILL hard,
SRC_OVER, paint solid sans shader, effet, filtre, pathEffect ou blender,
y compris dans une Picture imbriquée. Cette reconnaissance ne promet pas
que tous les siblings sont supportés : le planner valide le frame entier.
Ne pas filtrer CTM/cible invalides avant ownership pour poursuivre legacy.
Garder la traversal bornée et les identités Picture ; dépasser son budget
d'inspection doit conduire à l'owner terminal, pas à un résultat flat réussi.
Conserver intacte la reconnaissance AA et la préférence des frames W5e
directs sans Picture. Aligner la nomination hard dans la priorité capturée
de CapabilityCompilerChain ; pas de promotion générale de tous les Pictures.

Ajouter une factory interne `w6HardRectFillSource(catalog)` avec un mode de
projection fermé `PictureHardFill`, distinct des flags root-only, hairline
encoded et AA. Cette autorité nouvelle est limitée à RECT + GeometryNode.Rect,
FILL HARD_EDGE, solid SRC_OVER sans effets, Rect fini non vide, transformation
GeneralAffine ou Perspective admissible et clip hard déjà représentable.
Propager le mode dans les copies immutable du compiler. Ne pas réorganiser
tous ses modes existants. La projection locale PathBuilder du Rect sert
uniquement à la préparation math : DrawOrigin.RECT, sourceDraw, locator,
identités de scène et de commande restent originaux.

Dans preparePictureDrawLane, sélectionner cette factory sur le carrier
rebasé, uniquement lorsque ce nouveau contrat s'applique ; préserver W3/W4a
pour identity/scale-translate, ainsi que les sources Path existantes.
Pas d'extension du selector direct de W6, ni des sources AA projectives.
OccurrenceSourceInputV1 reste l'autorité du mapping F64 cible-local ; aucun
second CTM dans l'assemblage. Réutiliser constructSources, les ressources
hard et appendPlannedDraw : ni opaque-white ni consumer AA différé.
La Picture sans paint reste inline sur la destination courante ; le SRC_OVER
bleu voit le rouge précédent, sans isolation ajoutée. Tous les caps, budgets,
proofs, scissor, stencil/reset et FinalBlendPlanner restent effectifs.

Cette promotion hard Picture conserve le refus public des clips capturés
sous perspective : `unsupported_transform:Perspective`. La famille plain
hard est reconnue sur le draw capturé, avant normalisation sourceOnly ou
suppression des effets. À l'admission de l'occurrence, contrôler les clips
de source effectivement consommés (parents pertinents inclus, seul cull
authentifié retiré), puis leur carrier composé : une perspective enregistrée
ne disparaît pas du contrat parce qu'une transformation externe l'annule.
Ne pas confondre les clips différés de composition avec les clips de source,
ni le CTM projectif du draw avec celui du clip. Propager ce terminal avant
la chaîne W4/W5, sans enveloppe `w6a.layer.unsupported_child`, et conserver
les autres familles sous leurs propres contrats.

Le global au produit `ff3e3bb4a` a révélé cette admission manquante : W4e
acceptait le clip et le natif abortait sur un scissor hors cible. Le math
produit bien un scissor borné, mais `ClipMaskProducer` ne le transporte pas
jusqu'aux recettes stencil/direct. Ce défaut physique distinct reste une
dette W7 à corriger avec une autorité cible-locale scellée et des témoins
inverse/empty/stencil ; un clamp tardif ne constitue pas sa réparation.
Le présent correctif préserve la frontière publique et n'annonce pas une
réparation universelle W4e.

### Validation du prérequis

Les oracles sont fixés avant GPU. Homographie indépendante sur Surface16×16 :
`H(x,y)=((x+4)/(1+x/16),(y+3)/(1+x/16))`, Rect `[0,8]²`, fond rouge opaque,
bleu alpha136 et LINEAR : intérieur `(5,5)` et `(4,5)` dans la bande
`(182±1,0,193±1,255)` ; extérieurs `(3,5)` et `(9,5)` rouge exact.
Triangle `(0,0),(8,0),(0,8)` : `(5,4)` dedans, `(7,5)` dehors.
Even-odd outer `[0,8]²`, hole `[2,6]²` : `(5,3)` couvert, `(6,5)` trou.
L'inverse analytique aux centres pixel est
`x=(X−4)/(1−X/16), y=Y*(1+x/16)−3` ; ne pas appeler le math produit pour
construire les attentes. Un draw distinct après le trou contrôle le stencil.

Contrôler aussi l'affine `A(x,y)=(x+y/2+4,y+3)` sur `[0,4]²` (dedans5,4,
dehors4,6), deux replays H translatés de0 et8 sur fonds rouge/vert, une
Picture imbriquée avec translation externe(8,4), et un layer à origine non
nulle avec scissor hard et siblings avant/après. Leurs témoins positifs et
contre-exemples de transform omis/doublé sont dérivés avant Surface.

Horizon `w=1−x/4`, CTM NaN, RGBA16_FLOAT, budget frame1 byte et sibling AA
projectif restent des refus publics, sans modification de sentinelle,
suivis d'un rendu valide sur le même backend. Les assertions de diagnostic
doivent correspondre au premier contrat terminal réellement applicable.
Les premières prédictions W4d/W6 étaient trop tardives pour ces fixtures :
le CTM externe horizon refuse déjà le cull Picture (`w6b.filter.invalid_bounds`),
NaN refuse en capture (`non-finite-value`), et budget1 refuse dès la source
rouge W3 (`w3.budget.frame_local_exceeded`). Ces corrections sont fondées sur
les checks préexistants, sans déplacer le CTM ni changer le renderer pour
obtenir un autre code. Le témoin horizon qualifie donc le cull d'aggregate,
et budget1 le frame global ; ils ne prouvent pas un seuil spécifique W4d.
La configuration invalide reste immuable : recovery sur une nouvelle Surface
valide, dans le même backend sans dispose entre échec et récupération.
Tous les positifs exigent Render/Readback, zéro refus/diagnostic, dispatch
positif et deuxième rendu byte-identique. Contrôler les Picture Rect
identity/scale-translate sous budget analytique calculé statiquement avant
GPU et conserver sans relever le témoin historique B=26808 des layers AA.
Pour ce nouveau contrôle hard, garder la géométrie pixel-aligned de W3 :
Rect `[1,4]²`, identity puis translation(1,0)×scale(2,1), sur Surface16×16.
W4a exige AA et n'est pas le propriétaire du hard fractionnaire. La Picture
sans paint est inline, sans cible enfant : B=1024 (cible) +4096 (16 lignes
readback alignées256) +16 (cursor W6) +16+16 (deux sources Solid) =5168 bytes.
W3 n'émet pas de pools V/I/U ; une diversion vers W4d en ajouterait.
Fixer intérieur et extérieur des deux transformations avant RED ; cette
borne est dérivée des ressources, pas transposée de B26808 ni mesurée au GPU.
Relancer les témoins AA/Picture/affine/encoded hairline existants ; aucun
test d'infrastructure nouveau. La mesure du corpus reste dans Task 1,
après revue du prérequis, au SHA produit final.
