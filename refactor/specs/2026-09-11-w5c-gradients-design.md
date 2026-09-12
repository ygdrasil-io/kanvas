# W5c — Gradients et stop buffer partagé

Date : 2026-09-11  
Branche : `codex/w5c-gradients`, stackée sur `codex/w5b-blends`  
Statut : frontière validée ; document de réalisation à relire avant le plan d'implémentation

## 1. Objet

W5c réalise la tranche « quatre gradients et stop buffer sans plafond 16 » du
design W5. Elle promeut exactement les familles `LinearGradient`,
`RadialGradient`, `SweepGradient` et `ConicalGradient` sur les lanes déjà
authentifiées Rect, RRect, Path fill et Path stroke.

Cette tranche ne redéfinit ni la géométrie W4, ni le blend final W5b. Elle
remplace uniquement la source material solid/opacity par une source gradient
évaluée au point local du fragment, puis remet cette source linear-premul dans
la même équation de couverture et de blend final que W5b.

Le contrat normatif reste la section 7 de
`refactor/specs/2026-09-09-w5-material-graph-design.md`. Le présent document
fige son découpage de réalisation ; il ne crée pas une seconde sémantique.

## 2. Frontière admise

| Axe | W5c | Report explicite |
| --- | --- | --- |
| Familles | Linear, Radial, Sweep, Conical | aucune des quatre |
| Géométries | Rect, RRect, Path fill, Path stroke | Point(s), Text, Vertices/Mesh jusqu'à W5h |
| Tile mode | `CLAMP` | `REPEAT`, `MIRROR`, `DECAL` en W5d |
| Interpolation | `SRGB` | `LINEAR`, `OKLAB`, `HSL`, `OKLCH` en W5f |
| Local matrix | identité implicite | `WithLocalMatrix` et `CoordClamp` en W5d |
| Wrappers déjà promus | `Opacity`, blend final W5b | autres nœuds material selon W5d–W5h |
| Entrées image/font/codec | aucune | hors W5c ; font et codec restent hors périmètre |

Une combinaison reportée reste un `GapNotMigrated` avant acquisition de
l'ownership W5c. Une combinaison admise par cette table est terminale après
admission : un échec de normalisation, capability, budget, allocation,
compilation, submit ou readback ne retourne jamais vers la route legacy.

## 3. Décisions structurantes

### 3.1 Une seule autorité material

W5c étend `MaterialProgramPlan`, `MaterialBindingPlan`,
`EffectiveMaterialPlanner` et `W5aMaterialSourceStage`. Les anciens
`LinearGradientMaterialLowering`, `RadialGradientMaterialLowering`,
`SweepGradientMaterialLowering`, `ConicalGradientMaterialLowering` et
`GradientWgslShaderProvider` ne deviennent pas une seconde autorité W5c.
Ils restent confinés à la route legacy tant que celle-ci reçoit encore des
combinaisons non promues.

Les quatre nouveaux programmes sont scellés par famille et version numérique :

- `LinearGradientClampSrgbV1` ;
- `RadialGradientClampSrgbV1` ;
- `SweepGradientClampSrgbV1` ;
- `ConicalGradientClampSrgbV1`.

Leur clé structurelle exclut les couleurs, les positions et le nombre concret
de stops. Ces valeurs appartiennent aux bindings et au stop slab frame-local.
`OpacityV1` continue d'envelopper la source sans dupliquer le programme enfant.

### 3.2 Deux niveaux de plan, un seul stop slab

Le planning se fait en deux temps :

1. `EffectiveMaterialPlanner` valide et normalise chaque gradient en une
   séquence immuable de `GradientStopV1`, calcule son
   `GradientDegeneracyV1` et produit les paramètres uniformes de famille ;
2. l'interning frame-wide de `MaterialPlanTable` concatène ou déduplique les
   séquences exactes, réécrit chaque binding vers un
   `GradientStopRangeV1(baseIndexU32, countU32)` et scelle un unique
   `GradientStopSlabPlanV1`.

Un binding gradient final ne conserve donc pas une seconde copie logique des
stops. Il contient ses paramètres de famille, son sceau de dégénérescence et sa
plage dans le slab. `GradientStopSlabPlanV1` possède le snapshot immuable des
stops normalisés. Son contenu ordonné, ses plages, sa version ABI et sa
provenance entrent dans le seal frame-local, sans entrer dans la clé
structurelle des programmes. La déduplication est limitée à la frame et compare
les bits F32 canoniques complets ; aucun cache inter-frame n'est introduit.

La séquence de programmes reste topologique comme en W5a : une source gradient
précède ses wrappers `Opacity`. Les références restent valides après
concaténation de lanes et l'interner réécrit explicitement les plages du slab.

### 3.3 Un point local authentifié, pas une nouvelle géométrie

`GPUW5aSourceStageNativeV2` appelle actuellement
`kanvas_material_source(vec2<f32>(0.0))`. W5c remplace cette constante par deux
niveaux explicitement reliés :

1. `MaterialCoordinatePlanV1`, témoin backend-neutral immuable, est calculé
   avant le seal depuis le CTM du draw au moyen de `:math:matrix`. Il contient
   l'inverse projetée en F32 ou l'identité authentifiée, avec des champs
   numériques suffixés, et est attaché à chaque sealed draw Rect/RRect/Path
   fill/Path stroke ;
2. `MaterialCoordinateSlotV1`, porté par
   `GPUW5aGeometryPipelineTemplate`, décrit uniquement comment le WGSL consomme
   ce témoin pour produire le point local du fragment.

Le contenu de `MaterialCoordinatePlanV1`, sa version et son identité numérique
entrent dans le seal canonique du draw et dans son budget uniforme. Si son
transport modifie group 0, l'ABI géométrique est versionnée explicitement ; le
renderer ne relit jamais le CTM depuis `SceneSnapshot`, un `Paint` ou une
structure legacy après `Ready`.

Chaque lane Rect/RRect/Path fill/Path stroke transporte donc le témoin dérivé
des faits W4 avant de perdre le `DrawScope`. Le compositeur W5 ne reconstruit
pas la géométrie et n'invente pas une matrice. Il consomme seulement le slot et
le témoin authentifiés par la lane.

En W5c, `P` est la coordonnée locale après inverse CTM. L'inverse de local
matrix n'est pas appliqué, puisque les matrices material sont reportées à W5d.
Les règles existantes de coverage, clip, stencil, AA et blend final restent
byte-for-byte hors de la source material.

Le compositeur échoue fermé si une lane promue ne publie pas de coordinate
slot, si le slot n'est pas fini/représentable en F32, ou si la reflection ne
retrouve pas exactement les bindings géométriques scellés.

## 4. Capture bornée et immutabilité

### 4.1 Limite choisie

`SceneCaptureLimits` reçoit :

```kotlin
maxGradientStopsI32: Int = 65_536
```

`65_536` est une décision d'implémentation W5c, pas une constante normative de
Skia. Elle borne la mémoire avant copie et reste révisable par une future
décision mesurée. Elle supprime le plafond sémantique historique à 16 : les
séquences de 17 à 65 536 stops suivent exactement la même ABI et le même
programme que les séquences plus petites.

La limite compte cumulativement les stops d'entrée référencés par une capture.
Les endpoints implicites éventuellement ajoutés par la normalisation sont
comptés séparément par arithmétique I64 checked dans le budget du plan. Le
device et `frameLocalBudgetBytes` peuvent imposer une borne physique plus
basse ; ils produisent alors un refus typé avant `Ready`.

### 4.2 Préflight avant la première copie contrôlée par Kanvas

Le problème actuel est que `GeometrySnapshotContext` appelle `toList()` avant
que `PaintSceneAdapter` voie la metadata. W5c déplace le garde-fou au boundary
de recording :

- `GeometrySnapshotContext` lit `stops.size`, additionne le count en I64
  checked et compare la limite avant chaque `toList()` ;
- `SnapshotDisplayListBuffer` possède ce budget et garantit qu'un append en
  dépassement ne modifie pas la liste enregistrée ;
- les buffers internes de `Surface` et `PictureRecorder` sont reconnus comme
  propriétaires du snapshot, afin que `Canvas` ne fasse pas une première copie
  redondante avant eux ;
- une vue interne scellée permet à `Surface` et au router GPU de capturer sans
  recopier les stops ; `snapshotOps()` conserve sa copie défensive publique ;
- `PaintSceneAdapter` recompte les séquences immuables avant la copie vers le
  Render IR, de sorte qu'une limite de capture plus stricte produit
  `SceneCaptureResult.Invalid` sans nouvelle copie de stops.

`Surface` et `PictureRecorder` reçoivent la même limite par un paramètre final
avec valeur par défaut, sans casser les appels existants. Le render router
consomme le snapshot de limite de la `Surface` au lieu de recréer silencieusement
`SceneCaptureLimits.DEFAULT`.

Comme `DisplayListBuffer.append` retourne `Unit`, le refus de recording possède
un canal public immédiat et non ambigu :

```kotlin
class SceneRecordingLimitException(
    val diagnostic: RenderDiagnostic,
    val limitI32: Int,
    val requestedI64: Long,
) : IllegalArgumentException
```

L'exception est levée avant la copie et avant toute mutation du buffer, avec le
code stable `scene-recording-gradient-stops-exceeded`. La réservation cumulative
tentée est annulée avec l'opération. Les opérations déjà valides restent
intactes ; après interception de ce diagnostic public, la même surface peut
enregistrer puis rendre une opération valide. Aucun draw n'est omis
silencieusement, aucun état de refus latched n'exige un reset implicite et aucun
compteur privé ou hook d'infrastructure n'est ajouté pour le tester.

### 4.3 Normalisation

La normalisation suit exactement la section 7.1 du design W5 :

- 0 stop : `unsupported.material.gradient.empty_stops` ;
- 1 stop : Solid pour Linear/Radial/Sweep ; duplication pour Conical afin de
  conserver son masque de validité ;
- rejet des positions/couleurs non finies et des rayons négatifs ;
- clamp dans `[0,1]`, monotonisation, endpoints implicites ;
- pour plus de deux stops de même position, conservation du premier et du
  dernier ;
- hard stop : la couleur droite gagne exactement à l'égalité.

La recherche est `upper_bound - 1`, commune aux quatre familles. Aucun chemin
inline pour les petits gradients et aucun LUT approximatif ne sont admis.

## 5. Contrat numérique gradient

`GradientDegeneracyV1` est une valeur backend-neutral calculée une fois dans
`:gpu-plan`. Elle sérialise les scalaires, booléens et branch tags de la section
7.2 avec opérations F32 immédiatement arrondies roundTiesToEven et epsilon
exact `2^-15`. WGSL consomme ce sceau ; il ne recalcule pas les branches
uniformes.

Pour W5c/`CLAMP` :

- Linear utilise la projection sur l'axe et retourne la dernière couleur dans
  le cas dégénéré commun ;
- Radial utilise la distance au centre et la même règle dégénérée ;
- Sweep respecte l'orientation écran, le span ordonné et le cas spécial du
  leading segment `CLAMP` ;
- Conical résout les branches `FULLY_DEGENERATE`, `CONCENTRIC`,
  `LINEAR_EQUATION` et `QUADRATIC`, choisit la plus grande racine finie dont le
  rayon est positif et retourne transparent sans racine valide.

Les stops contiennent une couleur straight sRGB. Le shader interpole les quatre
composantes en sRGB, convertit RGB vers linear sRGB, puis prémultiplie une seule
fois. `Opacity` s'applique ensuite dans le DAG et le blend final W5b reçoit une
source linear-premul.

Les valeurs par fragment suivent `WgslFloatEnvelopeV1`. Les fixtures pixel ne
sont acceptées que si l'oracle indépendant produit un code exact ou deux codes
adjacents explicitement justifiés.

## 6. ABI `GradientStopBufferV1`

La représentation est celle du design W5 :

```wgsl
struct GradientStopV1 {
    positionAndReserved : vec4<f32>, // position, 0, 0, 0
    straightColor       : vec4<f32>, // straight sRGB en W5c
} // align 16, stride 32

struct GradientStopHeaderV1 {
    baseIndexU32 : u32,
    countU32     : u32,
    reserved0U32 : u32,
    reserved1U32 : u32,
} // align 16, size 16
```

Tous les gradients d'une frame partagent un seul storage buffer read-only côté
fragment, binding offset zéro. `PlanResourceRole` ajoute `GradientStopData`,
usages `{StorageRead, CopyDestination}`, lifetime `FrameLocal`. L'upload est une
opération planifiée `CopyUpload` et le buffer natif porte exactement
`Storage | CopyDst` ; W5c ne choisit pas une voie `mappedAtCreation` implicite.
Le groupe material conserve son uniform buffer de draw et ajoute le storage
binding ; les groupes destination-read W5b restent inchangés.

`baseIndex * 32`, `count * 32`, `baseIndex + count`, la taille du slab et le
budget combiné sont calculés en I64 checked puis validés comme U32 lorsque
l'ABI l'exige. La sérialisation hôte est little-endian explicite et tous les
champs réservés valent zéro.

Le lowering renderer publie un manifest de bindings au lieu de supposer qu'un
source stage possède toujours exactement un unique uniform binding. La
reflection doit prouver :

- les bindings group 0 de la géométrie sont inchangés ;
- group 1 contient exactement l'uniform material et, pour un gradient, le
  storage buffer partagé ;
- les groupes W5b destination/coverage conservent leurs indices et types ;
- aucune ressource supplémentaire n'est cachée.

## 7. Capabilities, budgets et ownership

Le renderer ajoute d'abord une feature backend-observed `StorageBuffer` dans
son snapshot de capabilities et `GpuPlanCapabilityAdapter` la mappe vers
`PlanOperationCapability.StorageBuffer`. L'absence de cette feature refuse la
frame avant toute allocation, même si des limites numériques sont présentes.

Le snapshot de capabilities GPU doit en outre transporter sans valeur inventée :

- `maxStorageBufferBindingSizeBytesI64` ;
- `maxStorageBuffersPerShaderStageI32` ;
- `maxBindingsPerBindGroupI32` ;
- `maxBindGroupsI32` ;
- `maxBufferSizeBytesI64` ou son fait physique équivalent déjà exposé.

`GPULimits` et `GpuPlanCapabilityAdapter` doivent relayer la feature et ces
valeurs depuis le device. Une valeur absente signifie capability indisponible.
Le planner vérifie `StorageBuffer`, `CopyUpload`, le binding complet, le nombre
de storage buffers du fragment, les bindings du groupe, la taille physique, le
budget frame-local et les plages U32 avant de publier `Ready`.

Le materializer alloue et upload le slab une seule fois par frame. Tous les
bind groups gradient empruntent ce même buffer ; son owner vit jusqu'à la
completion du dernier consumer. Une panne ferme ou met en quarantaine les
handles selon les contrats de pool existants. Il n'existe ni cache inter-frame,
ni allocation par stop, ni buffer par draw.

## 8. Admission et intégration aux lanes W4

Le candidate gate reconnaît seulement les combinaisons de la section 2. Le
planner commun est invoqué avant toute lane-specific lowering. Les quatre lanes
reçoivent un `MaterialPlanRef`, le `MaterialCoordinateSlotV1` et les ressources
du plan commun ; aucune lane ne mappe directement un `Shader` public.

L'ordre d'une frame mixte reste celui du display list. Le slab peut être
ordonné par première occurrence canonique, mais ce choix ne peut pas réordonner
les draws. Les programs sont internés par structure ; les bindings, plages de
stops, opacités et modes de blend restent dynamiques.

Les chemins suivants restent inchangés : W4 coverage/clip/AA/stencil,
destination snapshot W5b, quantification attachment sRGB, readback et format
public RGBA/BGRA.

## 9. Diagnostics et récupération

Les diagnostics W5c sont stables et séparés par domaine :

- recording/capture/valeur : diagnostic public immédiat du plafond avant copie,
  stops vides, valeur non finie, rayon négatif, sweep mal ordonné, plafond de
  capture IR ;
- capability : storage buffer ou limite authentifiée absente ;
- ressource/budget : overflow, taille de binding, frame-local budget ;
- schema/ABI : plage invalide, reserved non nul, coordinate slot absent,
  reflection différente ;
- native : allocation, mapping, compilation, submit ou readback.

Tout refus intervient avant la publication d'un token `Ready`, ou devient
terminal après ownership. Le test de récupération utilise uniquement une
surface publique : refus atteignable, puis rendu exact valide sur la même
surface. Aucun failpoint, proxy, reflection test, call count ou inspection de
scope interne n'est autorisé.

## 10. Gate publique W5c

La gate couvre au minimum :

- les quatre familles sur Rect, RRect, Path fill et Path stroke ;
- 1, 2, 16 et plus de 16 stops ;
- positions hors domaine, endpoints implicites, positions décroissantes,
  doublons et hard stops aux endpoints et à l'intérieur ;
- les dégénérescences Linear/Radial/Sweep/Conical et le masque de validité
  conical ;
- CTM non trivial prouvant que le point local, et non `(0,0)` ou le point
  device brut, alimente le gradient ;
- `Opacity` non triviale et au moins un blend final destination-read W5b ;
- frames mixtes, ordre de draws et mutations post-capture des stops ;
- dépassements déterministes de recording/capture et de budget logiciel, puis
  récupération publique ;
- lorsque l'adapter authentique courant expose réellement une capability ou une
  limite storage insuffisante, refus public conditionnel et typé ; cette preuve
  est un skip authentique sinon, jamais une capability injectée ;
- pixels exacts ou enveloppe analytique à deux codes adjacents.

Les preuves utilisent `Surface`, `Canvas`, `Picture`, `render()`, pixels,
exceptions portant un diagnostic public et diagnostics publics. La correction
du mapping capability est également revue statiquement, mais cette inspection
n'est pas un test automatisé. Sont exclus : tests d'infrastructure du code, source
shape, accès private/internal, reflection comme assertion de test, compteurs,
call counts, scopes, packets, bytes uniformes, bind groups, font, codecs, GM,
dashboard, renders/baselines, `:integration-tests:skia` et `jpg-color-cube`.

## 11. Découpage de réalisation

Le plan d'implémentation détaillera les tâches suivantes en RED → GREEN →
refactor :

1. limite de capture et snapshot avant copie ;
2. normalisation, `GradientDegeneracyV1` et modèles de plan ;
3. stop slab frame-wide, ressources, capabilities et budgets ;
4. coordinate slot commun aux quatre lanes W4 ;
5. source WGSL Linear/Radial/Sweep/Conical et ABI native partagée ;
6. candidate gate, ownership terminal et intégration Rect/RRect/Path ;
7. matrice de tests publics et oracle indépendant ;
8. revue indépendante, correction, vérification finale, documentation et PR
   stackée.

Chaque tâche d'implémentation est confiée à un agent Astra adapté. Les reviews
indépendantes utilisent Sol uniquement, conformément à la décision de la
branche. Aucune phase ne demande une décision utilisateur supplémentaire tant
qu'elle reste dans cette frontière ; seule une contradiction normative ou une
capability physique réellement absente constitue un blocker.

Tous les nouveaux champs numériques Kotlin de `GradientStopV1`,
`GradientStopRangeV1`, `GradientStopSlabPlanV1`, `GradientDegeneracyV1`,
`MaterialCoordinatePlanV1`, des limites, des budgets et des diagnostics portent
un suffixe exact `F32`, `F64`, `I32`, `I64` ou `U32`. Les noms WGSL déjà figés
par l'ABI de la section 6 restent inchangés.
