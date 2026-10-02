# W7 — véritables Rect hard dans une frame root Path AA encoded

3 octobre 2026. Base publiée : draft #2436, 53bf9c55b8950c36eb14a40eb44626cb02d6020c.
Branche isolée : codex/w7-root-aa-rect-admission. Carte blanche W7 et exécution
Subagent-Driven Development déjà déléguées : décisions et coûts consignés,
sans nouveaux cycles d'approbation intermédiaire. Luna implémente ; Sol relit ;
Astra intervient sur la stratégie ou les frontières qui le nécessitent.

## Intention

Connecter la capacité publique root Path AA SRGB_ENCODED à une vraie scène
contenant son fond Rect hard, sans falsifier les entrées du GM. Puis mesurer
la scène TeenyStrokesGm réelle dans les deux domaines et séparer le gain
d'intensité verticale de l'écart de couverture diagonal. Ce lot ne change
ni le domaine déclaré d'un GM, ni le sampling, ni la géométrie math.

Succès local : un Rect FILL entier, solid, hard, plus au moins un Path AA
admis restent sur un même état MSAA4 jusqu'au resolve final ; ordre et alpha
sont corrects et les exclusions restent transactionnelles. Les mêmes pixels
indépendamment attendus échouent avant source et passent ensuite. La comparaison
du vrai GM fournit ses métriques et rampes dans chaque domaine, sans qualifier
la parité Skia, la globale, le merge ou la clôture de W7.

## Cause statique et alternatives

Le runner dessine un DrawRect blanc, puis les dix Paths de TeenyStrokesGm.
Son compositionConfig impose le domaine déclaré du GM (LINEAR) même si un
config encodé lui est passé. En cas de frame réellement encodée, l'admission
whole-scene refuse d'abord le Rect, puis le prédicat public W4d exige tous les
Draw de provenance Path. La factory publique standaloneRectPathFrames existe
déjà ; elle n'est pas une factory réservée exclusivement à LINEAR.

Choix : étendre contextuellement cette famille publique fermée. Remplacer le
fond par un Path artificiel cacherait la frontière ; emprunter la source W6
privée changerait l'autorité ; réviser l'AA analytique maintenant mélangerait
admission, domaine et sampling. Ces trois voies ne sont pas retenues ici.

## Contrat de la frame publique

- SRGB_ENCODED, root, au moins un Draw PATH/Geometry.Path ANTIALIASED.
- Paths : contrat qualifié #2436 inchangé (segments linéaires non inverses,
  FILL ou STROKE positif fini BUTT/MITER, source solid, SrcOver, sans effets,
  CTM axis-aligned fini inversible et clip hard integer Rect/absent).
- Rect siblings : origine RECT et Geometry.Rect conservées, FILL hard non-AA,
  bornes entières finies non vides dans I32, solid direct sans shader/effets,
  SrcOver seulement, identité ou translation entière finie dans I32,
  clip hard integer Rect/absent. Même contrat quel que soit leur ordre.
- Aucune admission implicite de Rect AA, STROKE/hairline Rect, bounds fractionnaires,
  Rect scale/skew/perspective, gradient/image/layer, DrawColor/Clear/State,
  shader/effet ou blend différent. Les anciennes frames hors famille gardent
  leur politique ; une frame hard-only n'entre pas dans cette nouvelle famille.
- Refus avant acquisition/publication ; sentinelle readPixels intacte et
  récupération explicite du même Surface après discardRecordedOperations.

## Architecture

CompositionAdmissionV1 porte une décision publique commune pour cette famille,
réutilisée par le prédicat W4d : pas deux listes indépendantes susceptibles de
diverger. Les validations de provenance, Paint, clip et CTM source sont gardées.
La projection Rect existante dans le compilateur public construit uniquement
sa géométrie de préparation et ne remplace jamais le DrawNode source.

La factory standalone publique possède déjà cette projection et les sols AA
normalisés ; le constructeur historique Path-only ne doit pas prétendre
posséder une Rect qu'il ne peut classifier. Le prédicat public expose donc
la famille complète avec la possession Rect explicite de cette factory.
Les lanes privées W4e/W6, leur AA_FORMAT LINEAR et leurs seals sont inchangés.

Le format logique est dérivé du target authentifié : encoded UNORM ou LINEAR
sRGB. Ressources, prepared facts, physical keys, preflight et continuation
restent cohérents avec les preuves #2436 ; ne supprimer aucune vérification.
La source est appliquée une fois par sample ; pas de scalar coverage ni resolve
par draw. Caps, budgets, floors et calculs I64 restent inchangés. Toute nouvelle
géométrie/arithmétique produit appartient à math, nomenclature I/F32/64.

## Témoins et évolution du contrat

Surface 12x12, vrai Rect blanc [0,12)x[0,12), Path vertical (4,2)->(4,10),
width5 : à y5, x1 et x6 sont les masques indépendants 1010 et0101 ; intérieur
x3 noir, extérieurs x0/x7 blancs. Noir sur blanc : encoded127..128,
LINEAR187..188, mêmes masques et alpha255, suivant l'oracle MSAA figé #2436.
Ordre Rect après Path : tout blanc. Rect partiel entre répétitions : mêmes
masques corrélés, ne pas composer un alpha scalaire résolu. Conserver aussi
l'ancien cas exact Path puis Rect noir entier : résultat tout noir.

Corrélation C figée : fond blanc entier, Path noir A ci-dessus, Rect blanc
opaque [4,12)x[0,12), même Path A. À y5, x1/6 restent demi-couverts (encoded
127..128, LINEAR187..188), x3 noir, x0/7 blancs. À x1 le Rect n'efface pas
l'historique ; à x6 il réinitialise les quatre samples. Montrer avant le GPU
que le modèle erroné resolve-per-draw est disjoint à x1. Rejouer exactement C
par Picture réel memory/archive, aux mêmes attentes indépendantes.

Alpha D figé : fond transparent, Rect entier RGBA(64,128,192,128), Path A noir.
À y5, x0/7 sont Rect seul alpha128, x1/6 combinent masque2/4 noir opaque
avec le Rect translucide (alpha autour192 selon l'oracle parent), x3 noir
opaque. Figer les ensembles RGBA de l'oracle avant le GPU et prouver la
distinction avec un alpha source appliqué deux fois. Alterner LINEAR puis
ENCODED puis LINEAR en RGBA/BGRA ; un repeat stable par config. Aucun attendu
à alpha255 opaque seul ne remplace cette preuve de Rect translucide.

CTM/clip B figé : clip hard device [0,7)x[0,12) avant save/translation(1,1),
Rect blanc [0,12)x[0,12), Path A. À y6, x2 demi-couvert, x4 noir, x1 blanc,
x7 transparent ; les deux domaines gardent les mêmes masques device.
Tests uniquement pixels Surface natifs : pas de mock, source-text ou forwarding.

Le cas historique hard-draw-rect devient intentionnellement admis ; le remplacer
explicitement dans l'exclusion matrix par un Rect hard fractionnaire. La nouvelle
preuve positive conserve exactement le cas entier autrefois refusé, y compris
son ordre après Path et sa couleur noire. Tous les autres oracles/témoins
restent figés, sans fitting, notamment rect-gradient conserve
unsupported.surface.composition.geometry. L'intégralité finie seule ne
prouve pas I32 ; la restriction I32 est locale à la nouvelle famille, sans
modifier les helpers historiques Rect/image/layer.
Budget 8x8 dérivé statiquement : target256 + V/I/U floors24576 + MSAA
color1024 + depth1024 + hard mask Rect256 =27136 B au pic ; readback2048
est une autre phase. Quad/indices/uniforms tiennent dans ces floors ; pas
d'allocation de matériau/resolve supplémentaire. Qualifier B27136 et B-1
27135, pas le B26880 Path-only. Ne jamais rechercher le budget par essais GPU.

## Mesure de la vraie scène

Le diagnostic natif utilise Surface et GmCanvas publics, le vrai Rect blanc
du runner, puis appelle les hooks et draw du vrai TeenyStrokesGm inchangé.
Le config explicitement sélectionné sur cette Surface permet de mesurer les
deux domaines sans wrapper GM, changement de nom, clone de géométrie ou route
particulière du renderer. Ce n'est pas la preuve qu'un runner LINEAR consomme
encoded : sa propriété reste inchangée. Le test exerce les pixels des deux
scènes ; il ne teste pas le forwarding de config.

Attentes verticales indépendantes à (67,60)/(72,60), noir plein (70,60), blanc
(66,60)/(73,60). Les diagonales (106,60)/(107,60) sont mesurées séparément,
pas contraintes à des valeurs choisies après GPU. Comparer via le loader sRGB
existant à la référence SHA25678cbf8bfe9b44e74f282311f517f86daa20ff0512f5770b6bc79819544a47597.
Exporter métriques, hashes et rampes privées ; aucune modification de codec,
référence, seuil, score historique, registry631 ou scope443. Les plateaux ICC
bruts ne constituent pas l'oracle de couleur. Une migration GM ou couverture
analytique ultérieure exige son propre diagnostic causal et contrat.
Le contrôle LINEAR est comparé au rendu qualifié parent : tout écart doit être
expliqué avant d'attribuer une différence au seul domaine.

## Relecture de stratégie

Astra approuve la direction statique ; un Important demandait de figer C et D
pour éviter d'effacer la corrélation avec un Rect opaque ou de masquer l'alpha
avec un fond blanc. Ces entrées/ordres/points sont maintenant explicites.
La stabilité de rect-gradient et la preuve I32 sont également précisées.
La revue n'a exécuté aucun runtime et ne ferme aucune preuve native.

## Livraison et vérification

Deux gates : témoins/mesures avant source, puis admission publique + même
témoins GREEN et qualification proportionnée. Contrôleur seul lance runtime,
Gradle, evidence, docs, Git et publication. Une invocation à la fois ; terminal
original puis audit séparé complet avant la suivante ou tout edit source.
Reporter chaque échec global/hérité sans suppression ni claim GREEN global.
Fonts, external codecs et jpg-color-cube restent exclus. Aucune suppression,
merge, faux Picture, GPU skip ou CPU renderer/fallback. PR draft empilée sur
#2436 après reviews et qualification ; garder tous les artefacts de custody.
