# W7 — autorité de scissor des producers W4e

## Intention et portée

Corriger le rendu des clips dont la géométrie dépasse leur attachment, sans
changer les scènes GM ni déplacer l'oracle de parité. Base : draft #2427,
`246da8583b8342c5828bb020b0e704e419082e7e`. L'utilisateur délègue les décisions
W7 et conserve Subagent-Driven Development. Le lot sera une draft empilée sur
#2427 ; ni merge ni clôture W7 ne sont autorisés par sa seule réussite.

Font, codecs/décodage externe et `jpg-color-cube` restent exclus. Aucun
assouplissement de proof, CompositionEnvelope, epsilon, cap, budget, ancien
oracle, référence, seuil, corpus631/443 ou domaine LINEAR. Aucun fallback CPU,
routage par nom de GM ou test d'infrastructure. Géométrie dans math, I/F32/64.

## Cause établie

`ClipStackPreparationF64` produit une géométrie Path complète et un scissor
distinct déjà borné au domaine cible. Les deux constructions
`W4eClipPlanCompiler → PlanPass.ClipMaskProducer` perdent ce dernier. Les
recettes DirectTriangle/StencilEdge et le materializer racine reprennent
alors le scissor non borné du Path. L'abort observé précédemment envoyait
`SetScissor(5,6,11,9)` sur une cible8×8. Le refus typé Picture restauré dans
#2427 évite ce fixture historique, mais ne répare pas la cause physique.

Le rebind W6 ajoute un cas distinct : un producer visible dans le domaine
initial peut devenir vide dans un layer plus petit. Supprimer ce pass après
sélection laisserait ses folds, ressources et dépendances incohérents.
Les gardes natives exigent un scissor positif et un draw typé ; elles restent.

## Choix architectural

Trois options ont été examinées : clamp tardif au backend (perd l'autorité
planifiée), reconstruction complète du préfixe à chaque rebind (rebranche
resources/consumers), ou transport explicite avec réalisation constante.
Le troisième choix conserve le graphe existant et répare la frontière exacte.
L'avis Astra confirme ce choix et le traitement du vide décrit ci-dessous.

### 1. Une autorité immutable, distincte de la géométrie

`PlanPass.ClipMaskProducer` reçoit un `scissorI32: RectI32` obligatoire et en
expose une copie défensive `copyScissorI32()`. Ses coordonnées sont celles de
l'attachment courant. Les deux constructeurs W4e copient exactement le
scissor de `ClipPreparedEntryF32`. Aucune valeur par défaut ne le reconstruit
depuis le Path. La géométrie complète, le fill rule et la provenance restent.

Au rebind, intersecter en coordonnées device avec le domaine cible puis
soustraire l'origine une seule fois. Une query math dédiée
`LayerMappingF64.mapDeviceScissorToLayerI32OrNull(RectI32, RectI32): RectI32?`
distingue `RectI32.Empty` d'un échec arithmétique `null`. Ne pas changer le
contrat non vide de `mapDeviceDomainToLayerI32OrNull`, utilisé par initialize
et fold. Ne pas reconstruire les bornes avec une transformation de Path.

### 2. Vide ordinaire : ConstantZero explicite

Ajouter la réalisation fermée `W4eClipMaskProducerRealizationV1` : `Raster`
ou `ConstantZero`. Un scissor de couverture ordinaire vide sélectionne
ConstantZero avant la classification Rect/RRect/Path. Le scissor vide est
une preuve de zéro, jamais un `SetScissor` natif. Aucun faux Rect/Path vide.

Conserver `selectRealization` et son algèbre de `ClipGeometryF32.Empty`
actuels. Pour les géométries non vides mais hors cible, utiliser le même
ConstantZero initialement et au rebind, en conservant producer, fold, ordre,
attachments, clear/store, resolve et reset D24S8. Cela évite d'élargir
`sealClipOnly` aux stacks nouvellement simplifiés. Les folds existants donnent
exactement INTERSECT0→0 et DIFFERENCE0→identité, même entre d'autres clips.

Une géométrie inverse entièrement extérieure conserve **Raster**, son
scissor de couverture égal au domaine préparé et le clear de l'attachment
à1 ; l'intérieur offscreen ne produit aucun fragment. INTERSECT1 conserve
le masque, DIFFERENCE1 le met à zéro. Aucun ConstantOne n'est nécessaire.
Ne jamais déduire zéro de couverture inverse à partir du seul intérieur vide.

### 3. Recettes et commandes fermées

Les recettes raster analytic, triangle et stencil snapshotent le scissor
du pass et le valident dans l'extent réel. Le cover stencil partage le même
scissor que son edge ; l'inverse garde clear1 puis overwrite0 sur NonZero.
Le chemin préparé racine doit aussi consommer cette autorité sans recette W6.
Rect/RRect utilisent effectivement ce scissor : le floor/ceil existant inclut
les samples ±0.25 du shader, sans nouvel epsilon/padding AA.

ConstantZero possède sa propre recette fermée
`W4eClipMaskProducerConstantRecipeV1`, famille native
`W4eClipMaskProducerConstant`. Elle fige owner/pass/packet/bundle, couverture0,
target/resolve/depth et extents, domaine fullscreen positif, samples1/4,
clear/store/reset depth-stencil, shader constant, replace blend, ABI sans
bind group et Draw(3). Pas de V/I ni uniform consommé. Les slabs déjà admis
peuvent rester conservativement déclarés, mais les usages exécutables et
les calculs de budget doivent être exacts. Ne pas réutiliser aveuglément le
pipeline clear1x existant pour AA4 : le pipeline doit correspondre à ses
attachments et au sample count scellé.

La recette/catalogue owner-pass-packet-bundle appartient à la route W6.
Comme les producers raster existants, la route root consomme sa propre
autorité préparée authentifiée issue du graphe, sans exiger une recette W6
qu'elle ne possède pas. Le binding de frame détermine cette distinction :
une recette absente sur une route W6 reste un refus, jamais un fallback root.

Réalisations et scissor appartiennent au digest du graphe, au catalogue
canonique natif et aux comparaisons pass → prepared → frozen. ConstantZero
n'entre dans aucune recette raster concurrente. Corriger la comparaison W6
qui assimile actuellement bounds du Path et scissor de recette ; garder les
comparaisons indépendantes de géométrie. Les guards de payload natif restent inchangés.

### 4. Prérequis AA4 : reconnaître le resolve racine authentifié

La matrice a confirmé un refus hérité distinct : le SceneTarget W4e AA4 est
produit par resolve depuis la cible multisample, sans être une cible directe
de Render. Le contrôle de session ne reconnaît actuellement cette preuve que
pour W4d. L'avis Astra recommande un prérequis fermé dans ce lot : enrichir
l'autorité de frame W4e existante avec les endpoints exacts root/resolve/readback,
issus du graphe validé et liés à ses références physiques. Les autorités
clip-only et préfixes W6 n'obtiennent pas cette exception.

La session admet cette cible resolve-only uniquement si l'autorité commune
authentifie tous les renders ordonnés, frame/seal, dernier resolve couleur et
readback/staging. Seul le premier prédicat d'appartenance aux targets directs
évolue ; toutes les déclarations, descriptions, identités, budgets et contrôles
natifs suivants restent. Aucun suffix matching, fallback ou skip. Les cas
ConstantZero4x ne sont pas qualifiés tant que ce prérequis empêche leur exécution.

## Qualification publique

Nouvelle classe `W7ClipProducerScissorSurfacePixelTest`. Attendus déterminés
avant GPU, sans helper géométrique produit. Bleu opaque sur rouge opaque ;
vert pour des dessins suivants indépendants. Chaque positif exige Render,
Readback, dispatch, absence de refus et seconde image byte-identique.

| Cas | Fixture et témoins indépendants |
| --- | --- |
| Triangle négatif |8×8, (-4,-4),(12,-4),(-4,12) : (1,1) bleu, (6,6) rouge. RED prioritaire ; le scissor négatif est rejeté côté Kotlin avant soumission. |
| Dépassement positif |(5,6),(16,6),(5,15) : (5,6) bleu, (4,6) rouge ; après GREEN négatif seulement. |
| Even-odd stencil |Outer[-2,-2,10,10], hole[2,2,6,6] : (1,1) bleu, (3,3) rouge ; inverse complément exact. |
| Winding concave |L(-2,-2),(10,-2),(10,2),(2,2),(2,10),(-2,10) : (1,6) bleu, (6,6) rouge ; inverse complément. |
| Hors cible initial |Triangle(10,10),(14,10),(10,14), ou rect Path[10,10,14,14] : normal/intersect rouge, normal/difference bleu, inverse/intersect bleu, inverse/difference rouge. |
| Hors cible tardif |Root16², clipRect parent hard[4,3,12,11], saveLayer avec ce hint, puis triangle enfant device(0,0),(2,0),(0,2) et draw full16 : mêmes quatre résultats dans le layer, extérieur rouge. Le hint seul ne clippe pas les enfants. |
| Origine non nulle |Triangle négatif translaté(4,3) dans ce layer : (5,4) bleu, (10,9) rouge, (3,4) rouge. Vraie Picture avec clip affine seulement en compagnon. |
| Ordre |Masque non trivial avant/après le clip extérieur : identité conserve le masque, zéro reste zéro. |
| Siblings |Hole non recouvert + deuxième polygone stencil + marqueur vert hors des deux anciens scissors, root et layer. |
| Analytic AA |Rect clip[-.25,0,.5,8] AA sur transparent, paint noir opaque : alpha128 à(0,3),0 à(1,3) ; hard alpha0. RRect/Path AA4 : fixtures entières binaires, pas d'oracle fractionnel matériel. |

Paramétrer les mêmes géométries utiles en root et layer, hard/AA4 pour les
contrats déjà admis ; pas de nouvelle promesse AA générale. Vérifier aussi
sentinelle/refus budget1 et récupération propre sur le même runtime. Les
refus historiques Picture perspective/singular/legacy restent inchangés.

Le contrôleur possède seul les processus natifs : RED négatif ciblé puis
GREEN de la matrice, couverture existante, un global240 final compté honnêtement,
et corpus neuf631/443 strictement séquentiel au SHA produit. Les refus AA4
préexistants doivent être attribués, jamais changés en skip pour passer le lot.

La mesure a distingué une autre frontière, propre aux clips complexes AA4 dans
les layers W6 : la construction de source différée refuse encore W5A_AA avec
Operations. Ce lot ne supprime pas ce guard et ne revendique pas leur rendu.
Les trois scènes nouvelles restent des témoins de refus typé avec sentinelle
fraîche et récupération native ; leurs futurs oracles pixels restent au plan.
Le prochain lot W7 doit joindre source clip-free, préfixe W4e figé et consumer
différé sous autorité commune. ConstantZero4x root doit être positif ici ;
ConstantZero4x W6 demeure explicitement non qualifié.

## Succès, limites et self-review

Succès : les scènes publiques valides rendent sans scissor hors attachment,
vides/inverses/ordre/rebase et AA restent exacts, aucun ancien rendu perdu ni
nouveau failure atteint non expliqué. Aucun gain GM n'est promis : ce lot
répare d'abord un défaut réel de rendu et de sécurité de soumission.
La globale héritée40failures/incomplète, les autres capacités GM, couleur
Sk3d, optimisation d'admission et warnings JDK restent distincts.

Self-review : calcul géométrique dans math, décisions de réalisation dans
planner, consommation immuable dans renderer ; aucun calcul de support GPU.
Un seul nouveau cas constant suffit ; pas de ConstantOne ni refonte de W6.
Les cas vide initial/tardif, inverse, AA, origine et stencil ont tous des
témoins publics distincts. Le clamp tardif et la suppression tardive de pass
sont explicitement exclus.
