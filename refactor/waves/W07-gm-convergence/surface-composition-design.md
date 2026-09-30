# W7 — domaine de composition de Surface

30 septembre 2026. Base `b1ba6d0f3622d8cab8f41605ddf6e3f404ddf8be`,
PR [#2421](https://github.com/ygdrasil-io/kanvas/pull/2421).
Branche de travail : `codex/w7-surface-composition`.

## Intention et décision

La délégation de pilotage porte sur la convergence Skia, sans multiplier les
validations intermédiaires. Le lot alpha précédent est livré ; l'audit
[alphagradients](alphagradients-audit.md) distingue encore le domaine de
composition. Ce lot doit fournir des pixels encodés cohérents, pas seulement
un paramètre public : direct, layer intermédiaire et snapshot semi-transparent.
Les formats externes et les fonts restent exclus.

Trois options examinées avec Astra : migration de toutes les routes W3–W6
(trop large), métadonnées de sortie seules (pas de capacité fonctionnelle),
tranche verticale des routes existantes (retenue). W3/source-deferred porte
les Rect ; W6a porte le plain layer ; ni renderer parallèle ni raccourci GM.
La review est statique : aucune preuve native ne lui est attribuée.

Deux tâches séquentielles : contrat complet solide/layer/snapshot, puis
LinearGradient utilisant ce même contrat. Chaque tâche a des pixels publics
et une review distincte ; le lot est publié après les deux.

## Contrat public et propriété des données

- `render-ir/CompositionDomain` expose `LINEAR` et `SRGB_ENCODED`.
- `RenderConfig.compositionDomain: CompositionDomain = LINEAR`, dernier
  paramètre, fixe le domaine pour toute la Surface. Pas de changement implicite
  selon le matériau, la présence d'un layer ou le format public.
- `GPUColorFormat.AUTO` devient le défaut. AUTO résout vers
  `RGBA8_UNORM_SRGB` pour LINEAR et `RGBA8_UNORM` pour SRGB_ENCODED.
  Les deux formats explicites sont acceptés seulement avec leur domaine
  correspondant. `BGRA8_UNORM` natif et `RGBA16_FLOAT` sont refusés dans ce
  contrat ; on ne crée pas une seconde famille de cibles natives BGRA.
- `PixelFormat.RGBA8/BGRA8` ne choisit que l'ordre des octets publics :
  même cible native RGBA par domaine, swizzle à la sortie. L'arête historique
  config BGRA/public RGBA et le choix de domaine dépendant de la route sont
  corrigés intentionnellement, pas présentés comme inchangés.
- `RenderTargetDescriptor.compositionDomain`, dernier paramètre LINEAR par
  défaut, porte le domaine backend-neutral. `SceneSnapshot` et `Picture`
  restent rejouables sur plusieurs domaines ; le domaine n'est pas une
  propriété intrinsèque des commandes. Pas de nouvelle version d'archive.
- `RenderResult.premultiplication` et `GpuFrameOutput.premultiplication`
  décrivent les bytes réellement produits, avec défaut historique
  `TRANSFER_ENCODED_LINEAR_PREMUL`. Le résultat du renderer renseigne toujours
  explicitement la valeur. Égalité/hash/copy publics conservent ce champ.

LINEAR garde les pixels, identités et preuves historiques là où le contrat
était cohérent. Pour SRGB_ENCODED, les identités cible/programme/preuve/graph
et clés natives incluent le domaine, même pour une source noire ou opaque
numériquement identique. Les stops physiques ne changent pas de convention.

## Tranche SRGB_ENCODED admise

Admission de la frame entière avant construction de ressources/soumission,
sur scène immutable ; jamais de continuation legacy pour ce domaine.

- Rect FILL non-AA, CTM identité ou translation entière, limites finies
  pixel-aligned ; clip absent ou rectangle hard entier. `drawColor` est un
  remplissage de la cible dans ce même domaine, CTM identité seulement.
- SrcOver seulement ; couleurs publiques normalisées, opacité de paint.
- Solide, puis LinearGradient sRGB/CLAMP avec modes alpha STRAIGHT et
  PREMULTIPLIED existants, hard stops et axe dégénéré selon leur contrat.
  Pas de changement du domaine d'interpolation ni de la préparation des stops.
- Zéro ou un plain `saveLayer/restore`, non imbriqué, bornes absentes ou
  rectangulaires entières, plusieurs draws enfants admis, restore SrcOver
  avec opacité dans [0,1]. Pas de shader/filtre/backdrop au restore.
- Image RGBA_8888/BGRA_8888, SRGB, PREMUL, SOURCE_SPACE, pixels présents,
  via `DrawOrigin.IMAGE` et `GeometryNode.ImagePatch` seulement : rectangles
  source/destination entiers de même étendue, nearest 1:1, CTM admise,
  sans filtre/conversion. ImageShader, nine, lattice et atlas sont exclus.
  Cette règle est sémantique :
  une image ordinaire identique à un snapshot est également admise, sans
  fausse provenance « snapshot seulement ».
- Les commandes non visuelles et save/restore simples conservent leurs
  contraintes existantes ; elles n'élargissent pas la géométrie admise.

Sont refusés notamment AA/coverage fractionnaire, stroke/hairline, autres
géométries, autres blends, autres interpolations/tile modes, sources composées
arbitraires, runtime effects, filtres/couleurs/backdrop, layers imbriqués ou
plusieurs occurrences de layer, transformations non admises, anciens
snapshots transfer-encoded-linear-premul et autres conversions d'images.
Un mélange contenant une opération exclue refuse intégralement : pas de rendu
partiel ni de changement silencieux vers LINEAR. Les limites existantes ne
sont ni relevées ni contournées.

Diagnostics stables sous `unsupported.surface.composition` avec suffixes
`target-format`, `geometry`, `source`, `blend`, `layer`, `image`,
`recording-snapshot`. Les erreurs de validité et ressources existantes restent
distinctes. Précédence : cible, topologie layer, puis ordre des commandes ;
géométrie avant blend avant source/image pour un draw.

## Pipeline et preuve

Ajouter `PlanLogicalColorFormat.RGBA8_UNORM_ENCODED_SRGB_PREMUL`, résolu en
texture native RGBA8Unorm. La variante historique reste RGBA8UnormSrgb.
Les couples format/domaine/interprétation/store sont validés ensemble.

Propager la cible dans candidat, sources différées, occurrences W6 et
construction de graph. `CapabilityCompilerChain.constructSourceLanes` ne doit
pas recréer une cible LINEAR par défaut pour un enfant encodé.
Paramétrer seulement W3, les sources communes admises et la recette de plain
layer ; garder fermées les recettes AA/filtrées et leurs invariants linéaires.
Le chemin distinct `W3SolidRectPlanCompiler.recognizeDrawColor` doit également
produire la couleur prémultipliée dans le domaine cible ; il ne passe pas par
le compilateur commun des sources. La projection image source-deferred de
`W5eImagePlanCompiler` conserve cible et admission `ImagePatch`, sans élargir
le gate Rect FILL aux autres formes d'image.

Pour les sources admises : produire directement `alpha * C_srgb` au lieu de
`alpha * EOTF(C_srgb)`. Le graphe d'opérations utilisé par la preuve est aussi
celui émis en WGSL. Le nouveau domaine ne doit pas devenir un post-traitement
`unpremultiply/OETF/premultiply` du résultat linéaire. Les paramètres alpha
du gradient restent orthogonaux. Pour une image SOURCE_SPACE/PREMUL admise,
charger le premul encodé directement, sans EOTF ni nouvelle prémultiplication.
Conserver swizzle, gardes de coordonnées/texel et `ZERO_ALPHA_GUARD` : alpha
nul produit RGBA zéro même si les octets RGB de l'image source sont non nuls.
Seules unpremultiply/EOTF/repremultiply disparaissent de la variante encodée.

SrcOver et restore opèrent sur les valeurs du domaine de leur cible. Les
intermédiaires encodés sont UNORM8, sans transfert implicite au sampling/store.
L'opacité du restore multiplie exactement une fois RGB premul et alpha.
Chaque store UNORM8 reste une étape numérique ; direct et layer ne sont pas
promis identiques au bit près lorsqu'un store supplémentaire arrondit.

`ColorSourceProofV1.compositionDomain` est authentifié, et toute jonction
source/blend/target vérifie l'égalité de domaine. Les valeurs identiques ne
permettent pas d'échanger les owners/certificats. Pas de seconde formule WGSL,
epsilon, plafond nouveau, borne primitive élargie ni désactivation de seal.
L'amendement de validation ci-dessous concerne seulement l'acceptation des
tests d'intégration W7 ; il ne change pas les preuves du moteur.

## Snapshots et replay

LINEAR conserve `OETF(alpha * C_linear)` et le tag
`TRANSFER_ENCODED_LINEAR_PREMUL`. SRGB_ENCODED produit `alpha * C_srgb`,
donc `SOURCE_SPACE` + SRGB + PREMUL, convention déjà existante.
`toImage`, full/subset snapshot, copie d'Image et Picture conservent cette
représentation ainsi que l'ordre des canaux.

Un snapshot encodé peut être rejoué sur LINEAR via la conversion SOURCE_SPACE
existante ; un témoin coloré le vérifie. Un snapshot historique LINEAR vers
SRGB_ENCODED refuse dans ce lot. Les conversions générales sont différées.
En recording-only, `snapshotScene` reste possible ; le snapshot image encodé
refuse explicitement tant que cette demande ne peut pas être représentée
sans pixels. Aucun rendu natif caché dans ce mode.

## Validation publique et critères d'arrêt

Tests Surface/Picture uniquement, oracle indépendant avec les primitives
d'enveloppe existantes, bornes calculées avant rendu. Cas minimum :

Amendement approuvé le 30 septembre 2026 par la carte blanche W7, après
présentation de sa moindre précision : les compositions multi-stores utilisent
un type de test distinct `CompositionEnvelope`, avec ensembles complets par
canal et trace de chaque store. Il n'est pas un `DrawResult.Bounded` historique
(au plus deux codes adjacents). Les assertions vérifient l'appartenance aux
ensembles calculés avant GPU, sans midpoint ni tolérance empirique. Les bornes
des primitives, gates historiques, preuves produit et seuils GM restent intacts.
Les tests W7 directs peuvent utiliser ce même type, sans supprimer leurs
exigences de discrimination.

Avant GPU, les témoins choisis doivent séparer le bon résultat de ceux obtenus
avec mauvais domaine, opacité de restore omise/doublée ou inversion R/B
(au moins un canal disjoint par alternative). Si l'omission d'un store
intermédiaire n'est pas séparée, déclarer cette limite : répétition et trace
ne prouvent pas sa détection. Cette validation fonctionnelle n'établit pas
une parité Skia à un code près. Pour les snapshots, valider d'abord le
producteur avec son enveloppe ; copie/subset/layout peuvent ensuite vérifier
exactement les bytes du producteur. L'oracle d'un replay peut prendre ces bytes
validés comme entrée fixe avant le replay, jamais son résultat comme attendu.

1. Noir alpha128/255 sur blanc : 127 environ en encodé, 187 en linéaire ;
   répétition et alternance des domaines, Render/Readback natifs.
2. Direct contre layer contenant plusieurs sources semi-transparentes ;
   restore opacité128/255 ; quantification de chaque store dans l'oracle.
3. Snapshot rouge alpha128 : environ (128,0,0,128) encodé contre
   (188,0,0,128) historique ; aussi couleur non saturée (R128,G64,B32,A128).
4. Full/subset, RGBA/BGRA, copie, Picture mémoire/archive et replay nearest
   1:1 dans chaque domaine admis ; contrôle encodé→LINEAR, refus inverse.
5. Gradients : blanc opaque→noir transparent et rouge A128→bleu A64,
   STRAIGHT/PREMULTIPLIED, t=.5 ; même source dans root et layer.
6. Alternance de cibles sur mêmes couleurs/images/stops sans collision cache.
7. Refus après draw admissible, sentinel intact, discard puis récupération,
   deux rendus ; couvrir les exclusions, recording-only et formats contradictoires.
8. Budget B/B−1 calculé depuis les ressources avant exécution, pas recherché
   empiriquement ; aucune hausse des limites existantes.
9. `drawColor` coloré semi-transparent direct et dans un plain layer ; CTM
   identité, refus de sa CTM translatée même entière.
10. Image SOURCE_SPACE/PREMUL ordinaire avec RGB non nul et alpha zéro,
    pour vérifier la garde que ne teste pas un snapshot transparent normalisé.
11. Témoin coloré AUTO contre format explicite compatible dans chaque domaine,
    RGBA/BGRA. Couvrir aussi une continuation legacy LINEAR publique si un
    témoin éligible existe ; sinon déclarer cette limite et vérifier statiquement
    la résolution unique et le swizzle. Noir/blanc seuls ne détectent pas une
    inversion de canaux.

Baseline fraîche : 19/19 tests alpha/layer/snapshot, 3 classes XML, Gradle0,
archives `/private/tmp/kanvas-w7-composition.25GaUn/baseline`. La globale
héritée reste rouge/incomplète. Après chaque tâche : tests ciblés/review ;
après le lot : une globale bornée240s, corpus631/443 constant et review finale.
Pas de test d'infrastructure ajouté. Si une enveloppe ne certifie pas un
témoin, diagnostiquer avant de changer le témoin ; ne pas ajuster la tolérance
aux pixels observés. Aucune réussite si le processus finit native133.

## Limites et trajectoire

Fonts, codecs/décodage externe, `jpg-color-cube`, GM/adaptateurs, PNG de
référence, seuils, exclusions et scores historiques ne changent pas.
Le corpus par défaut n'utilise pas encore le domaine encodé : aucun gain de
similarité attendu de ce seul opt-in. W7 reste ouvert, PR draft sans merge.

Le contour Rect hairline de `alphagradients` reste un prérequis explicite du
port final : extension géométrique réutilisant le contrat ensuite, puis port
avec mode alpha par colonne et AA explicite. Pas de promesse de parité du GM
avant ces étapes. Si le présent lot exige AA, filtres, nested layers ou une
migration générale, suspendre cette extension et faire relire le couplage ;
ne pas généraliser les recettes sous couvert du cas plain.
