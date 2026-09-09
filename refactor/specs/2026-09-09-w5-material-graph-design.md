# W5 — Material graph commun et exécution native

Date : 2026-09-09  
Branche de design : `codex/w5-material-graph`  
Base stackée : `codex/w4e-complex-clips` / PR #2393

## 1. Objectif

W5 rend le matériau indépendant de la famille géométrique. Une même scène
`MaterialNode` doit produire une autorité de programme, de bindings et de
ressources unique, consommable par les Rect, RRect, Path, points, images,
vertices et textes déjà admis par les routes préparées.

La vague couvre séquentiellement :

1. solid et opacity ;
2. blends fixed-function et destination-read ;
3. gradients linéaire, radial, sweep et conical ;
4. matrices locales, `CoordClamp` et tile modes ;
5. images déjà décodées RGBA/A8 et sampling ;
6. color filters et espaces de travail ;
7. graphes composés et matériaux procéduraux ;
8. runtime effects enregistrés.

Cet ordre W5a–W5h remplace l'ordre indicatif de l'umbrella W5. Le blend passe
avant les gradients afin de fermer d'abord les 45 échecs historiques
`DrawPoint`, puis de valider la séparation geometry/material/blend sur un cas
multi-draw avant d'ajouter les ABI de stops et d'images. Ce réordonnancement ne
change ni le périmètre ni les dépendances W4/W6.

W5 ne change pas la géométrie W4. Les combinaisons qui dépendent encore de la
topologie AA4 native conservent un refus de capability exact ; aucune
capability ne sera inventée pour élargir artificiellement la matrice.

## 2. État de départ

L'IR sémantique existe déjà dans `:render-ir`. `MaterialNode` représente les
sources solides, quatre gradients, images, blend children, runtime effects,
matrices locales, color filters, opacity, domaines d'interpolation, bruit et
`CoordClamp`. Les snapshots, identités canoniques et limites de graphe sont
déjà en place.

Le renderer possède également un `GPUPreparedMaterialProgramCompiler` et des
lowerers spécialisés. Ils ne forment toutefois pas encore l'autorité commune
du rendu :

- les compilers W4 reconnaissent directement `MaterialNode.Solid` ;
- text et vertices appellent le compilateur préparé par leurs propres routes ;
- gradients et images utilisent encore plusieurs descripteurs, clés et ABI ;
- des branches de `GPUMaterialMapper` remplacent un matériau non supporté par
  son child, une couleur transparente ou un autre fallback sémantique ;
- l'ABI gradient borne fonctionnellement les stops à 16 ;
- les programmes destination-read sont spécialisés par plusieurs lanes de
  géométrie ;
- le `RenderGraph` ne porte pas une autorité material complète et uniforme
  avant `Ready`.

W5 consolide ces éléments ; il ne crée pas une seconde hiérarchie de matériaux.

## 3. Approches considérées

### 3.1 Étendre chaque lane existante

Chaque route Rect/RRect/Path/text/vertices apprendrait progressivement les
gradients, images et blends. Cette approche livre vite un cas isolé, mais
multiplie les matrices de support, les clés pipeline et les divergences de
couleur. Elle est rejetée.

### 3.2 Remplacement big-bang du renderer material

Tous les chemins existants seraient remplacés en une seule branche. Cela
réduit théoriquement la période de coexistence, mais rend le diagnostic des
régressions et la revue presque impossibles. Cette approche est rejetée.

### 3.3 Compilateur commun, migration verticale séquentielle

Une autorité material plan-first est introduite, puis chaque famille est
activée par une tranche verticale publique. Les anciens chemins ne sont
retirés qu'après preuve du remplacement. C'est l'approche retenue.

## 4. Frontières de modules

### 4.1 `:render-ir`

`:render-ir` reste l'autorité backend-neutral :

- `MaterialNode`, ses children et leurs identités canoniques ;
- valeurs sémantiques immuables : couleurs, stops, tile modes, sampling,
  interpolation, matrices et ressources image ;
- `GraphLimits` communs, avec validation itérative et bornée ;
- compatibilité `Picture`/`SceneArchive` seulement lorsqu'un changement de
  schéma sémantique est réellement nécessaire. W5e est précisément un de ces
  cas : le sampling d'un draw image devient une donnée explicite de l'opération
  et du nœud IR `GeometryNode.ImagePatch` ou `GeometryNode.ImageNine`, au lieu
  d'être transporté indirectement par `Paint.shader` puis perdu par la capture.

Il ne contient ni WGSL, ni layout WebGPU, ni pipeline key native.

### 4.2 `:math`

Les nouvelles valeurs géométriques ou de transformation restent dans
`:math:geometry` et `:math:matrix`, avec suffixes I32/I64/F32/F64. En
particulier, la composition et l'inversion des matrices locales de sampling
sont préparées en F64 dans `:math:matrix`, puis projetées explicitement vers
les données F32 du backend.

W5 n'introduit aucun Rect, Point, Size ou Matrix privé dans `:kanvas` ou
`:gpu-renderer`.

### 4.3 `:color-management`

`:color-management` porte les conversions de gamut et transfer function. Le
renderer ne duplique pas les formules de conversion dans plusieurs
materializers. Dans le schéma IR actuel, `WithWorkingColorSpace` contient un
`ColorInterpolation`, pas un `ColorSpace` : W5f couvre donc les cinq domaines
d'interpolation existants, mais n'introduit pas de working gamut arbitraire.

### 4.4 `:gpu-plan`

`:gpu-plan` compile `MaterialNode` en deux produits immuables :

- `MaterialProgramPlan` : structure du DAG, ordre d'évaluation, opcodes,
  contrats de couleur, besoins destination-read et identité structurelle ;
- `MaterialBindingPlan` : blocs uniformes, buffers de stops, textures,
  samplers, children, usages, tailles, alignements et lifetimes.

Ces plans ne contiennent aucun handle ni source WGSL. Ils sont attachés aux
draws et aux passes du `RenderGraph` avant sa publication `Ready`.

W5a fait évoluer `PlanDraw` vers une autorité unique :

- `material: MaterialPlanRef` référence une entrée d'une table scellée de
  couples `MaterialProgramPlan`/`MaterialBindingPlan` ;
- `blend: BlendPlan` devient un plan scellé capable de représenter
  fixed-function, `NoOp` et destination-read ;
- le champ `color: ColorF32` n'est conservé que dans un adaptateur legacy
  versionné pour les witnesses W3/W4 non encore migrés ;
- un draw W5 ne peut jamais posséder simultanément `color` et
  `MaterialPlanRef`, ni deux autorités de blend.

Les witnesses W4 gardent leur version historique et leur résultat public. Une
nouvelle version de witness est créée dès qu'un draw adopte le contrat W5 ;
elle encode explicitement la version du material plan. La migration se fait par
famille de draw, sans nouvelle hiérarchie parallèle.

Le planner reçoit aussi un `RuntimeEffectSemanticCatalogSnapshot` immuable et
backend-neutral. Il peut ainsi valider identité, version, ABI sémantique,
children et budgets d'un effet avant `Ready` sans voir son WGSL.

### 4.5 `:gpu-renderer`

`:gpu-renderer` consomme exclusivement le plan scellé pour :

- sélectionner ou assembler un module WGSL enregistré ;
- valider parser, reflection et ABI ;
- matérialiser exactement les bindings déclarés ;
- construire la pipeline key structurelle ;
- uploader les valeurs dynamiques sans recompiler le matériau ;
- gérer séparément les caches device/session et les ressources frame-locales.

Le `GPUPreparedMaterialProgramCompiler` existant est réutilisé comme point de
départ, puis séparé entre compilation sémantique et exécutable backend. Il ne
doit plus recevoir un descripteur reconstruit différemment par chaque famille
de draw.

### 4.6 `:kanvas`

`:kanvas` capture `Paint`/`Shader` vers les champs immuables de `DrawNode`,
transmet les faits de target et publie les résultats. Il ne fusionne pas
silencieusement paint/material/effects, et ne choisit ni shader WGSL, ni ABI,
ni fallback de matériau.

## 5. Contrat du material graph

### 5.1 Séparation structure / valeurs

La clé de programme dépend uniquement de la structure qui modifie le code :

- famille de nœud et ordre des children ;
- mode de blend ;
- tile/sampling/interpolation lorsqu'ils changent la fonction exécutée ;
- contrats de couleur et topologie de bindings ;
- version des dictionnaires et runtime effects.

Elle ne dépend pas des couleurs, positions, matrices, alpha, pixels, IDs de
ressource ni du nombre concret de stops lorsque l'ABI reste identique. Ces
valeurs appartiennent aux bindings dynamiques.

Deux graphes structurellement identiques avec des valeurs différentes doivent
donc partager le programme sans partager leurs données.

### 5.2 Ordre et canonicalisation

L'ordre `dst`/`src`, l'ordre des children runtime et l'ordre d'évaluation sont
préservés. Aucune réécriture commutative n'est autorisée.

Les normalisations algébriques sont limitées aux équivalences exactes :

- multiplication de deux opacités finies ;
- suppression d'une matrice locale identité ;
- composition ordonnée de matrices locales adjacentes ;
- neutralisation d'un `Opacity(..., 1)` ;
- publication d'un matériau transparent pour `Opacity(..., 0)` seulement si
  le blend et les effets associés ne rendent pas la distinction observable.

La Scene IR d'origine reste inchangée ; seule la représentation compilée peut
être canonisée.

### 5.3 Autorité du matériau effectif

Le `EffectiveMaterialPlan` est construit une seule fois à partir d'un
`DrawNode`. Il est l'unique endroit où les champs actuellement séparés
`material`, `paint`, `effects`, `resource` et `operationBlendMode` acquièrent
leur ordre d'exécution. La capture IR reste inchangée sauf pour les évolutions
versionnées indispensables décrites en 9.2 et 12.

Pour une couleur `c`, `toLinearPremul(c)` signifie : conversion de ses RGB sRGB
non prémultipliés vers le target linéaire, puis multiplication RGB par alpha.
`scaleAlpha(c, a)` multiplie les quatre composantes d'une couleur déjà
prémultipliée par `a`. L'équation normative est :

```text
materialColor =
  paint.shader absent       -> toLinearPremul(paint.color)
  paint material/shader     -> scaleAlpha(eval(material), paint.color.alpha)
  RGBA image draw           -> scaleAlpha(sample(image), paint.color.alpha)
  A8 image draw sans shader -> sampleA8(image) * toLinearPremul(paint.color)
  A8 image + paint shader   -> sampleA8(image) *
                               scaleAlpha(eval(paint.shader), paint.color.alpha)

source1 = apply origin composition / operationBlendMode(materialColor)
source2 = apply paint.colorFilter(source1), exactement une fois
blended = drawBlend(destination, source2)
output  = destination + coverage * (blended - destination)
```

Un draw image sans `PaintNode` utilise un paint blanc opaque implicite. Pour un
matériau couleur ou une image RGBA, les composantes RGB de `paint.color` sont
ignorées : seul son alpha module la source. Pour A8, l'alpha échantillonné est
un masque et le paint complet — ou le shader du paint s'il existe — fournit la
couleur. Cette distinction est portée par `DrawOrigin` et `resource`, pas
inférée dans le WGSL. Les invariants de capture sont fermés :

- pour une origine non-image, `resource == null` et `DrawNode.material` vaut
  `paint.shader` s'il existe, sinon `Solid(paint.color)` ;
- pour une origine image directe, `resource != null` et `DrawNode.material`
  vaut toujours `ImageSample(resource, operationSampling)` ;
- le `PaintNode.shader` d'une image RGBA est conservé mais n'est pas évalué ;
  celui d'une image A8 est la source de couleur du masque ;
- un draw image sans paint possède le paint blanc opaque implicite, mais aucun
  faux shader de paint.

Toute autre combinaison est un refus de schéma. La présence de
`PaintNode.shader` reste donc le discriminateur autoritaire entre couleur du
paint et shader du paint, sans concurrencer l'`ImageSample` de l'opération.

`coverage = geometryCoverage * clipCoverage` est un scalaire fini borné dans
`[0,1]` et s'applique au résultat du blend, pas seulement à la source. Cette
forme préserve notamment `CLEAR`,
`SRC`, `SRC_IN`, `DST_IN`, `SRC_OUT`, `DST_ATOP` et `MODULATE` sous couverture
partielle. Une écriture fixed-function n'est permise que si
`GPUBlendPlanner` démontre l'équivalence algébrique avec cette équation. Le
chemin analytique `source * coverage` est déjà exact pour
`SRC_OVER`, `DST_OVER`, `DST_OUT`, `SRC_ATOP`, `XOR` et `SCREEN` ; les modes
non exprimables par cette forme utilisent destination-read, sauf optimisation
plus forte prouvée pour des alpha/constants particuliers. `DST` reste un
`NoOp`. Le CPU oracle évalue toujours l'équation générale.

`MaterialNode.WithColorFilter` reste un wrapper interne et s'exécute à son
emplacement dans le DAG. `PaintNode.colorFilter` est le filtre externe
autoritaire. La même valeur apparaît aujourd'hui dans `EffectStack`; le
compilateur valide que les deux snapshots concordent, consomme le filtre depuis
`PaintNode` et marque l'entrée `EffectStack` comme déjà consommée. Une absence
ou divergence est un refus de schéma, jamais une seconde application.

`operationBlendMode` ne remplace pas le blend du draw. Pour Atlas, il combine
le sprite source avec la couleur d'entrée destination avant le color filter ;
sans table de couleurs il est sans effet. Pour Mesh, il combine la source du
paint avec la couleur fragment du mesh selon le contrat de l'opération. Le
blend final avec le target reste toujours `DrawNode.blend` (SrcOver implicite
sans paint). `MaterialNode.Blend(dst, src)` est lui aussi interne à la source.
Un blender custom non résolu par le catalogue runtime est refusé avant `Ready`.

### 5.4 Limites, capabilities et budgets

Les limites de graphe restent `GraphLimits(maxDepth=64, maxNodes=4096)`. Avant
toute copie de collection ou de pixels, `SceneCaptureLimits` ajoute
`maxGradientStopsI32`, `maxImageBytesI64` et
`maxRuntimeUniformBytesI64`. Le contexte cumule les stops, les bytes des images
uniques et les bytes uniformes avec arithmétique I64 checked ; la metadata est
préflightée avant `toList`, `copyOf` ou snapshot pixel. Un dépassement refuse
la capture sans construire la copie. Les ressources du plan sont ensuite
bornées de la même manière : octets uniformes, octets de storage, textures,
samplers, bindings, passes, mémoire temporaire et évaluations de bruit.
`MaterialFrameLimits` porte notamment `maxNoiseOctaveEvaluationsI64`; ce budget
logiciel est injecté et snapshoté avec le plan, il n'est pas déguisé en limite
du device.

`PlanCapabilitySnapshot` W5 ajoute des faits authentifiés par le device :
`maxUniformBufferBindingSizeBytesI64`,
`maxStorageBufferBindingSizeBytesI64`,
`maxStorageBuffersPerShaderStageI32`, `maxUniformBuffersPerShaderStageI32`,
`maxSampledTexturesPerShaderStageI32`, `maxSamplersPerShaderStageI32`,
`maxBindingsPerBindGroupI32` et `maxBindGroupsI32`.
`PlanOperationCapability` ajoute `StorageBuffer` ; `PlanResourceUsage` ajoute
`StorageRead` ; `PlanResourceRole` ajoute `GradientStopData`,
`MaterialImageData`, `DestinationSnapshot` et `RuntimeUniformData`. Une limite
absente vaut capability non disponible, jamais une valeur par défaut inventée.

Une limite device ou de frame est vérifiée avant publication `Ready` et avant
toute allocation native. Sont comparés au minimum : taille du binding,
`baseIndex + count`, nombre de buffers/textures/samplers par stage, bindings par
bind group, nombre de bind groups, taille physique des buffers et budgets de
frame. Un overflow, une limite manquante ou un dépassement produit un diagnostic
typé et transactionnel, jamais une substitution de matériau.

### 5.5 Ownership des ressources et caches

Les uniforms, stop buffers, destination snapshots et staging buffers sont
frame-local. Ils vivent de leur premier producteur jusqu'à la completion de
leur dernier consumer ; un rollback les libère ou les met en quarantaine via
les pools existants. Les stops sont dédupliqués uniquement dans la frame W5 :
W5 n'introduit pas de cache inter-frame pour eux.

Les pipelines, bind-group layouts, samplers et textures d'images décodées sont
session/device. Leur clé commence toujours par `deviceGeneration` puis par
l'identité canonique et les faits physiques pertinents (format, dimensions,
usages, version ABI). `PlanResourceLifetime` ajoute `DeviceSessionCache` et le
plan porte une `PlanCacheResourceRequest` handle-free pour chaque ressource de
ce type. Le budget de frame compte de façon pessimiste la taille complète même si
le cache produira un hit ; une optimisation fondée sur un snapshot mutable du
cache est interdite dans W5.

Les caches ont des budgets explicites en entrées et en octets, une éviction LRU
uniquement lorsque le lease count vaut zéro et un lease retenu jusqu'à la
completion de la frame. L'admission d'une miss évince d'abord les entrées sans
lease et refuse transactionnellement si le budget reste insuffisant ; un hit
reçoit seulement un nouveau lease. Un device loss invalide en bloc la
génération ; aucune ressource d'une ancienne génération n'est réutilisée.

## 6. Couleur et alpha

Le contrat interne est RGBA linéaire prémultiplié. L'alpha reste linéaire et
n'est jamais soumis à la transfer function.

- `ColorARGB` sans metadata est de l'sRGB non prémultiplié ;
- une image `Pixels` est décodée depuis sa transfer function et son gamut
  `ColorSpace`, puis convertie vers le linéaire sRGB du target ;
- Display P3 et Linear sRGB ne sont donc supportés ici que comme espaces
  *source d'image* explicitement décrits, pas comme working gamut arbitraire ;
- `WithWorkingColorSpace` sélectionne l'un des cinq domaines d'interpolation
  existants `SRGB`, `LINEAR`, `OKLAB`, `HSL`, `OKLCH` ; il ne transporte aucun
  gamut et ne doit jamais être interprété comme un `ColorSpace` ;
- les valeurs non prémultipliées sont prémultipliées après conversion vers le
  contrat linéaire ; les valeurs PREMUL sont d'abord remises en straight alpha
  de façon sûre avant la conversion, puis prémultipliées à nouveau ;
- chaque color filter déclare son contrat d'entrée/sortie ; Matrix, Blend,
  Table, Lighting, HSLA, Lerp, HighContrast, Luma et Overdraw opèrent dans le
  domaine défini par leur sémantique publique, avec conversion explicite à la
  frontière, jamais par hypothèse backend ;
- le target sRGB 1× W4 reste le seul target physique W5 ; un target HDR ou une
  combinaison non définie reste un refus typé.

Le rendu encode vers sRGB une seule fois à l'écriture finale. Les blends
fixed-function et shader utilisent le même contrat prémultiplié.

Un futur working gamut exige un nouveau snapshot `ColorSpace` versionné dans
`:render-ir` ; il est explicitement reporté et ne peut pas être simulé par
`ColorInterpolation`.

## 7. Gradients

Les quatre familles Linear/Radial/Sweep/Conical utilisent une même séquence de
stops immuable et un même contrat d'interpolation.

### 7.1 Normalisation des stops

La normalisation suit la sémantique Skia observable et est partagée par l'oracle
CPU et le planner :

| Entrée | Résultat canonique |
| --- | --- |
| 0 stop | refus `unsupported.material.gradient.empty_stops` |
| 1 stop | Linear/Radial/Sweep deviennent `Solid` ; Conical duplique la couleur aux deux extrémités et conserve le masque de validité du cône |
| position ou couleur non finie | refus avant `Ready` |
| position hors `[0,1]` | clamp dans `[0,1]` |
| position décroissante | `p[i] = clamp(input[i], p[i-1], 1)` |
| premier stop `p > 0` | insertion `(0, firstColor)` implicite |
| dernier stop `p < 1` | insertion `(1, lastColor)` implicite |
| plus de deux stops au même `p` | conservation du premier et du dernier ; les intermédiaires sont inobservables |
| doublon à 0/1 en mode non-CLAMP | suppression respectivement du côté extérieur gauche/droit, comme Skia |

Les deux couleurs restantes d'un doublon forment un hard stop
(discontinuité) : pour `t == p`, la couleur du stop le plus à droite est
choisie ; juste avant `p`, l'interpolation se termine sur la couleur la plus à
gauche, et juste après elle repart de la plus à droite. Pour `CLAMP`, `t < 0`
retourne la couleur extrême gauche et `t > 1` l'extrême droite, sans réduire ces
valeurs à un endpoint qui possède un hard stop. `REPEAT` applique
`t - floor(t)`, `MIRROR` applique la période triangulaire 2, et `DECAL` retourne
transparent strictement hors `[0,1]`.

Après tile, le shader trouve par recherche binaire le plus grand index `k` tel
que `p[k] <= t` (`upper_bound - 1`). Il retourne directement la dernière
couleur seulement si `k == count - 1` ; pour tout autre `k`, y compris zéro,
il interpole vers `k+1` avec
`u = (t - p[k]) / (p[k+1] - p[k])`. La normalisation garantit un dénominateur
strictement positif pour cet intervalle. Le choix `upper_bound` rend les hard
stops déterministes.

### 7.2 Paramètre géométrique et dégénérescences

Les quatre familles calculent le paramètre brut `t` en F32 depuis le point
local `P`, puis appliquent le tile mode de 7.1. WGSL ne possède pas de F64 : le
CPU oracle exécute donc chaque opération en F32, avec le même arbre
d'expressions et sans contraction implicite ; un `fma` n'est permis que s'il
est explicite dans les deux implémentations. Les deux utilisent le même seuil
F32 `epsilon = 2^-15` et les mêmes masques. Le preflight peut employer F64 pour
valider les paramètres et préparer des constantes, mais il projette celles-ci
en F32 avant le plan. Les valeurs d'entrée non finies et les rayons négatifs
sont refusés à la capture.

- Linear : avec `d = end - start`,
  `t = dot(P - start, d) / dot(d, d)`. Si `length(d) <= epsilon`, la règle
  dégénérée commune ci-dessous s'applique.
- Radial : `t = length(P - center) / radius`. Si `radius <= epsilon`, la règle
  dégénérée commune s'applique.
- Sweep : dans les coordonnées device où Y croît vers le bas,
  `a = floorMod(atan2(P.y-center.y, P.x-center.x) / (2*pi), 1)` ; zéro est
  l'axe +X et le sens positif est horaire à l'écran. Puis
  `t = (360*a - startAngle) / (endAngle - startAngle)`. `startAngle >
  endAngle` est refusé. Si l'écart est au plus `epsilon`, la règle commune
  s'applique, sauf en `CLAMP` avec `endAngle > epsilon` : la première couleur
  remplit `[0,endAngle)`, et l'angle exact du hard stop ainsi que la suite
  reçoivent la dernière couleur. Si
  `startAngle <= 0 && endAngle >= 360`, le tile effectif est `CLAMP`.
- Conical : poser `d = end-start`, `q = P-start`, `dr = endRadius-startRadius`
  et résoudre `A*t^2 + B*t + C = 0`, avec
  `A = dot(d,d)-dr^2`, `B = -2*(dot(q,d)+startRadius*dr)` et
  `C = dot(q,q)-startRadius^2`. Si
  `abs(A) <= epsilon*max(1,dot(d,d),dr^2)`, résoudre l'équation linéaire ;
  sinon exiger un discriminant non négatif. Parmi les racines
  finies telles que `startRadius + t*dr > 0`, choisir la plus grande. Sans
  racine valide, le fragment est transparent avant tile, quel que soit le tile
  mode. Centres confondus et rayons distincts suivent directement
  `t = (length(P-start)-startRadius)/dr`. Centres et rayons égaux appliquent la
  règle commune, sauf `CLAMP` avec rayon `> epsilon` : première couleur à
  l'intérieur du disque, dernière couleur à l'extérieur via un hard stop sur
  le cercle.

La règle dégénérée commune est : `DECAL` transparent ; `CLAMP` dernière
couleur ; `REPEAT` et `MIRROR` couleur moyenne exacte du gradient. Cette moyenne
est l'intégrale de ses segments linéaires normalisés : pour chaque intervalle,
`0.5 * (c[i] + c[i+1]) * (p[i+1]-p[i])`, plus les intervalles implicites de
couleur constante avant le premier stop et après le dernier. Conformément à
l'autorité Skia épinglée, `c[i]` est ici la couleur straight dans son espace
source déclaré — sRGB pour les `ColorARGB` actuels — avant application de
`ColorInterpolation`. Le solid moyen est ensuite converti une seule fois vers
linear-premul. Les cas Sweep/Conical `CLAMP` décrits ci-dessus prennent
priorité sur cette règle.

Les comparaisons de dégénérescence des paramètres sont préparées en F64 au
preflight puis figées dans le plan. Le discriminant, la sélection de racine et
`t` par fragment sont évalués en F32 avec l'ordre commun défini ci-dessus ; un
résultat non fini rend le fragment transparent. Ces règles font partie du
contrat sémantique et ne peuvent pas varier selon la lane géométrique.

### 7.3 Interpolation couleur

Les stops sont convertis en straight alpha dans le domaine demandé, les quatre
composantes sont interpolées linéairement, puis le RGB résultant est reconverti
en linear sRGB et prémultiplié par l'alpha interpolé :

| `ColorInterpolation` | triplet interpolé |
| --- | --- |
| `SRGB` | RGB sRGB encodé |
| `LINEAR` | RGB linear sRGB |
| `OKLAB` | L, a, b OKLab |
| `HSL` | H, S, L sRGB |
| `OKLCH` | L, C, H OKLCH |

HSL et OKLCH utilisent le chemin de hue le plus court. Avec
`d = ((h1 - h0 + 540) mod 360) - 180`, remplacer `d = -180` par `+180` pour
fixer le tie de 180° dans le sens croissant, puis
`h = (h0 + u*d) mod 360`. Si une seule extrémité est
achromatique, elle hérite du hue de l'autre ; si les deux le sont, le hue vaut
0. Aucun gamut clamp intermédiaire n'est appliqué ; seul l'encodage final RGBA8
borne les canaux. Le schéma actuel n'expose ni interpolation premul ni autre
hue method : W5 ne les invente pas.

### 7.4 ABI `GradientStopBufferV1`

Le plafond sémantique de 16 stops est supprimé. Tous les gradients d'une frame
partagent un read-only storage buffer frame-local, binding offset zéro, et
référencent une tranche par `baseIndexU32`/`countU32` dans un header uniforme :

```wgsl
struct GradientStopV1 {              // align 16, stride 32 bytes
    positionAndReserved : vec4<f32>, // x = position, y/z/w = 0
    straightColor       : vec4<f32>, // domaine de ColorInterpolation
}
struct GradientStopHeaderV1 {         // align 16, size 16 bytes
    baseIndexU32 : u32,
    countU32     : u32,
    reserved0U32 : u32,
    reserved1U32 : u32,
}
```

Le byte order hôte est converti explicitement en little-endian lors de
l'upload ; les deux champs réservés valent zéro en V1. `baseIndex * 32`,
`count * 32`, leur somme et la taille totale du slab
sont calculés en I64 checked. Le binding complet doit rester sous
`maxStorageBufferBindingSizeBytesI64` et `maxBufferSizeBytes`, utiliser un seul
storage buffer de fragment et respecter le budget de bindings. Le planner
publie un `PlanResource(Buffer, GradientStopData, StorageRead, FrameLocal)` et
la durée de vie couvre tous les passes consommateurs. Une capability absente ou
une taille dépassée refuse la frame avant `Ready`.

Une optimisation inline pour les petits gradients est reportée : W5 utilise
une ABI unique pour 2, 16 et >16 stops afin d'éviter deux comportements. Aucun
LUT texture approximatif n'est utilisé pour masquer une limite de stops.

## 8. Matrices locales, coordonnées et tile modes

Kanvas utilise des vecteurs colonne : `p' = M * p`, avec translation dans la
dernière colonne. Pour un point device `pD`, un CTM géométrique `C` et des
wrappers locaux ordonnés du plus extérieur au plus intérieur
`Louter ... Linner`, le transform shader→device et la coordonnée de sampling
sont normativement :

```text
MshaderToDevice = C * Louter * ... * Linner
pShader         = inverse(MshaderToDevice) * pD
                = inverse(Linner) * ... * inverse(Louter) * inverse(C) * pD
```

Le CTM continue de gouverner la géométrie ; cette inverse n'est appliquée qu'à
la coordonnée envoyée au matériau. Un shader ayant une rotation `R`, puis
enveloppé par une translation `T`, produit `C*T*R` et s'échantillonne avec
`R^-1*T^-1*C^-1`. Il ne doit pas être réécrit en `C*R*T`, cas
non-commutatif couvert par un test public.

Les matrices sources restent `Matrix3x3F32`, mais la composition et l'inversion
sont exécutées par `Matrix3x3F64` dans `:math:matrix`. Une matrice est refusée si
elle contient une valeur non finie, si l'inversion F64 échoue, ou si une
composante de l'inverse projetée en F32 devient non finie. La projection utilise
l'arrondi IEEE-754 round-to-nearest ties-to-even. Pour une homographie, la
division par `w` est faite après multiplication ; `w == 0` ou un résultat non
fini rend l'échantillon transparent, sans contaminer les autres fragments.

Les quatre tile modes sont pris en charge par une fonction commune :

- `CLAMP` ;
- `REPEAT` ;
- `MIRROR` ;
- `DECAL`, avec transparent hors domaine.

`CoordClamp` est appliqué après les wrappers qui l'entourent et avant
l'évaluation de son child, donc exactement dans l'espace local de ce child. Une
matrice singulière, non finie ou non représentable après projection F64→F32
produit un refus exact.

## 9. Images déjà décodées

W5e consomme uniquement `ImageResourceSnapshot.Pixels`. Un
`ExternalImageReference` est refusé par
`unsupported.material.image.external_resource` tant qu'un propriétaire et une
completion native ne sont pas définis. W5 ne détecte, ne décode et n'encode
aucun PNG/JPEG/WebP/GIF/BMP.

### 9.1 Frontière publique et layout

Le mapping de capture est explicite et homonyme :

| `kanvas.image.ColorType` public | `render-ir.ImagePixelFormat` | W5e |
| --- | --- | --- |
| `RGBA_8888` | `RGBA_8888` | oui, ordre R G B A |
| `BGRA_8888` | `BGRA_8888` | oui, swizzle B↔R à l'évaluation |
| `SRGBA_8888` | `SRGBA_8888` | oui, transfer sRGB explicite |
| `ALPHA_8` | `ALPHA_8` | oui, canal mask uniquement |
| tout autre `ColorType` | valeur `ImagePixelFormat` homonyme | refus W5e typé |

Les dimensions, `rowBytes`, `rowBytes * height` et `width * bytesPerPixel` sont
calculés en I64 checked. Chaque ligne lit exactement les octets logiques ; le
padding source est ignoré. Le staging WebGPU ajoute son propre padding
`copyBytesPerRowAlignment`, initialisé à zéro et jamais accessible au sampler.
L'upload normalise le stockage physique en RGBA8_UNORM ou R8_UNORM et n'utilise
pas une texture sRGB automatique : décodage et swizzle restent explicites et ne
peuvent donc pas être appliqués deux fois.

Pour RGBA/BGRA, `OPAQUE` force `a=1` et ignore le byte alpha ; `UNPREMUL` convertit
le RGB source straight vers linear sRGB puis prémultiplie ; `PREMUL`
unpremultiplie d'abord dans l'espace source (`a=0` donne RGB zéro), convertit,
puis prémultiplie à nouveau. `SRGBA_8888` impose la transfer sRGB ; W5e accepte
seulement une metadata `ColorSpace` sRGB cohérente, les autres combinaisons
étant refusées au lieu d'arbitrer entre deux autorités. Display P3 et Linear
sRGB restent représentables via `RGBA_8888` plus leur `ColorSpace` explicite.

Pour `ALPHA_8`, le byte donne `mask = byte/255`. L'alpha type `OPAQUE` force le
mask à 1 ; `PREMUL` et `UNPREMUL` sont équivalents car aucun RGB n'est stocké.
Le résultat est `mask * paintColor` ou `mask * paintShader`, selon l'équation de
la section 5.3 ; le `ColorSpace` du snapshot ne transforme pas un canal alpha.

### 9.2 Sampling et frontières

Le sampling de l'opération est une autorité distincte du paint. W5e fait donc
évoluer les contrats suivants de façon atomique :

- `DisplayOp.DrawImage` ajoute
  `sampling: SamplingOptions = SamplingOptions.NEAREST` ; les overloads publics
  sans sampling conservent ce défaut et l'overload explicite enregistre la
  valeur directement, sans fabriquer `paint.copy(shader=image.makeShader())` ;
- `GeometryNode.ImagePatch` ajoute `sampling: ImageSampling`, son identité
  devient `geometry-image-patch-v2`, et `DisplayOpSceneAdapter` construit
  `MaterialNode.ImageSample` avec cette valeur exacte ;
- `GeometryNode.ImageNine` devient un nœud IR distinct de `ImagePatch`, avec
  `image`, `center: RectF32`, `destination: RectF32` et
  `sampling=Nearest`; `DisplayOpSceneAdapter` l'émet uniquement pour
  `DrawOrigin.IMAGE_NINE` et la lane préparée conserve sa décomposition
  existante en neuf couples source/destination. Aucun nouveau type rectangle
  n'est créé dans `:render-ir` : les objets géométriques restent ceux de
  `:math:geometry` avec la nomenclature F32 ;
- `ImageLattice` conserve son sampling déjà explicite et `Atlas` reste
  `Nearest` tant que son API n'expose pas ce paramètre ;
- `SceneDisplayOpAdapter`, le codec `Picture`, `SceneArchiveCodec` et les
  witnesses W4/W5 sont versionnés ensemble. Les anciens payloads décodent
  `Nearest` ; un round-trip nouveau doit conserver `Nearest`, `Linear` et les
  deux coefficients cubic bit pour bit.

Le shader éventuel du paint n'est plus utilisé comme transport de sampling. Il
reste snapshoté séparément pour la sémantique couleur A8 de 5.3 ; le supprimer
ou l'écraser lors d'un draw image est un échec de gate W5e.

Pour `ImagePatch(src,dst)` issu de `DrawOrigin.IMAGE`, le point local est
d'abord ramené dans le rectangle destination : `u=(P.x-dst.left)/dst.width`,
`v=(P.y-dst.top)/dst.height`, puis la coordonnée image vaut
`s=(src.left+u*src.width, src.top+v*src.height)`. Une largeur ou hauteur source
ou destination nulle/non finie est refusée ; un axe négatif conserve le flip
explicite. Le domaine de tile est l'image entière, après cette projection du
source rect. Cette projection générique est interdite à `ImageNine` : chaque
cellule de sa décomposition porte son propre couple source/destination avant
la création du `MaterialNode.ImageSample`. Le planner refuse toute combinaison
`ImagePatch/IMAGE_NINE` ou `ImageNine/IMAGE`, ce qui empêche d'étirer le centre
sur toute la destination.

Les coordonnées image utilisent des centres de pixels `i + 0.5`. Pour une
coordonnée `s`, poser `u = s - 0.5` : nearest choisit `floor(s)`, linear les
deux indices `floor(u)`/`floor(u)+1` par axe, et cubic les quatre indices
`floor(u)-1 ... floor(u)+2` par axe, soit 16 taps.

Chaque index de chaque tap passe par le tile mode *avant* le fetch : CLAMP borne
à `[0,n-1]`, REPEAT utilise `floorMod(i,n)`, MIRROR utilise une période `2n`, et
DECAL fournit transparent si l'index est hors domaine. Les poids ne sont pas
renormalisés en bordure ; les taps DECAL transparents conservent donc leur
poids.

`ImageSampling.Cubic(B,C)` exige `B` et `C` finis dans `[0,1]` et utilise le
noyau Mitchell–Netravali séparable suivant pour `x = abs(distance)` :

```text
K(x) = ((12-9B-6C)x^3 + (-18+12B+6C)x^2 + (6-2B)) / 6,  0 <= x < 1
K(x) = ((-B-6C)x^3 + (6B+30C)x^2 + (-12B-48C)x
        + (8B+24C)) / 6,                                  1 <= x < 2
K(x) = 0,                                                  x >= 2
```

La déduplication du cache se fait par snapshot immuable et faits physiques,
jamais par identité objet. Mutation du tableau public après capture, row
padding, swizzle, alpha zéro, chaque tile mode et les taps cubic de bord sont
des gates publiques W5e.

## 10. Blends

`GPUBlendPlanner` reste l'autorité de classification :

- fixed-function lorsque WebGPU exprime exactement le mode et la couverture ;
- `NoOp` pour `DST` lorsque la géométrie et les effets n'imposent aucun travail ;
- destination texture + formule GPU pour les autres modes ;
- refus exact lorsque MSAA ou une autre topologie empêche la lecture destination.

### 10.1 `BlendMathV1`

Toutes les lanes et les color filters partagent les mêmes formules. Pour les
couleurs straight `Cs/Cd`, alpha `as/ad` et prémultipliées `S/D` :

| Mode | résultat prémultiplié |
| --- | --- |
| `CLEAR` | `0` |
| `SRC`, `DST` | respectivement `S`, `D` |
| `SRC_OVER` | `S + D*(1-as)` |
| `DST_OVER` | `D + S*(1-ad)` |
| `SRC_IN`, `DST_IN` | respectivement `S*ad`, `D*as` |
| `SRC_OUT`, `DST_OUT` | respectivement `S*(1-ad)`, `D*(1-as)` |
| `SRC_ATOP` | `S*ad + D*(1-as)` |
| `DST_ATOP` | `D*as + S*(1-ad)` |
| `XOR` | `S*(1-ad) + D*(1-as)` |
| `PLUS` | `sat(S+D)` |
| `MODULATE` | multiplication composante par composante de `S` et `D`, alpha inclus |

Pour les modes avancés, `ao=as+ad-as*ad`,
`Co=(1-as)*D.rgb + (1-ad)*S.rgb + as*ad*B(Cs,Cd)` et le résultat vaut
`(Co,ao)`. Les fonctions séparables s'appliquent canal par canal :

| Mode | `B(Cs,Cd)` |
| --- | --- |
| `MULTIPLY` | `Cs*Cd` |
| `SCREEN` | `Cs+Cd-Cs*Cd` |
| `OVERLAY` | `2*Cs*Cd` si `Cd<=0.5`, sinon `1-2*(1-Cs)*(1-Cd)` |
| `DARKEN`, `LIGHTEN` | respectivement `min(Cs,Cd)`, `max(Cs,Cd)` |
| `COLOR_DODGE` | `1` si `Cs==1`, sinon `min(1,Cd/(1-Cs))` |
| `COLOR_BURN` | `0` si `Cs==0`, sinon `1-min(1,(1-Cd)/Cs)` |
| `HARD_LIGHT` | `2*Cs*Cd` si `Cs<=0.5`, sinon `1-2*(1-Cs)*(1-Cd)` |
| `SOFT_LIGHT` | `Cd-(1-2*Cs)*Cd*(1-Cd)` si `Cs<=0.5`, sinon `Cd+(2*Cs-1)*(softD(Cd)-Cd)` |
| `DIFFERENCE` | `abs(Cd-Cs)` |
| `EXCLUSION` | `Cd+Cs-2*Cd*Cs` |

`softD(x)=((16*x-12)*x+4)*x` pour `x<=0.25`, sinon `sqrt(x)`. Pour les quatre
modes non séparables, utiliser les helpers W3C bornés
`Lum(c)=0.3r+0.59g+0.11b`, `Sat(c)=max(c)-min(c)`, `ClipColor`, `SetLum` et
`SetSat` : `HUE=SetLum(SetSat(Cs,Sat(Cd)),Lum(Cd))`,
`SATURATION=SetLum(SetSat(Cd,Sat(Cs)),Lum(Cd))`,
`COLOR=SetLum(Cs,Lum(Cd))`,
`LUMINOSITY=SetLum(Cd,Lum(Cs))`. `SetLum(c,l)` ajoute `l-Lum(c)` puis
`ClipColor`; `SetSat(c,s)` translate le minimum à zéro, scale le maximum à `s`
en conservant l'ordre des canaux, ou retourne zéro si max=min. `ClipColor`
pose `l=Lum(c), n=min(c), x=max(c)` puis, si `n<0`,
`c=l+(c-l)*l/(l-n)` et, si `x>1`,
`c=l+(c-l)*(1-l)/(x-l)`.

Toutes les divisions traitent leur branche de dénominateur nul avant calcul.
Le CPU oracle arrondit en F32 après chaque opération primitive, suit le même
arbre d'expressions et les mêmes branches que le WGSL, sans contraction
implicite ; un `fma` n'est permis que s'il est explicite des deux côtés.
L'encodage RGBA8 final constitue l'arrondi de comparaison publique. Les modes
sans transcendantale sont byte-exacts ; `SOFT_LIGHT`, qui utilise `sqrt`,
autorise au plus un code RGBA8 uniquement lorsque l'analyse d'intervalle F32
autour du résultat CPU prouve que l'écart backend peut franchir la frontière
de quantification correspondante. Cette tolérance analytique est enregistrée
par canal et par pixel ; elle n'est ni un score de similarité ni une tolérance
globale.

### 10.2 Versions destination-read

Chaque target possède une `DestinationVersionI64`, initialement 0 et incrémentée
après toute écriture. Un draw destination-read doit consommer la version exacte
produite par la dernière écriture qui le précède en paint order. Son groupe est
clé `(targetId, deviceGeneration, destinationVersion)` ; aucune copie n'est
réutilisable après un draw qui écrit ce target, y compris lorsque ce draw est
lui-même le consumer du snapshot précédent.

Le graphe scellé exprime `Render(version n) -> Copy(version n) ->
RenderConsumer(version n+1)`. Le target déclare `RenderAttachment|CopySource`,
le snapshot `CopyDestination|Sampled`, et la lifetime du snapshot va du pass
Copy à la completion de son unique intervalle sans écriture. Deux consumers ne
partagent une copie que s'il n'existe strictement aucune écriture du target
entre eux.

La région copiée est l'intersection du target et des bounds conservateurs du
draw, élargis uniquement pour AA et sampling. Les color filters W5 sont non
spatiaux et n'élargissent jamais les bounds. Si ces bounds ne sont
pas prouvables, la copie porte le target complet ; elle n'est jamais réduite par
heuristique. Dimensions, row pitch aligné et octets temporaires entrent dans le
preflight. Ce modèle est indépendant du type de géométrie et préserve les seals
V/I/U de W4.

W5b cible explicitement les 45 failures historiques
`GPUAllApiBlendSurfaceTest :: DrawPoint` : trois commandes successives, quinze
modes et trois contextes. Elles ne seront retirées du ledger qu'après preuve
publique de pixels exacts sur la séquence complète. Aucun diagnostic externe à
W5 ne peut remplacer cette gate.

`MaterialNode.Blend(dst, src)` est distinct du blend final du draw. Le premier
évalue deux child materials pour produire la source ; le second compose cette
source avec le target. Les deux étapes ont des plans et diagnostics séparés.

## 11. Color filters, graphes composés et procédural

### 11.1 Sémantique normative des color filters

Les color filters non spatiaux sont des étapes material. Leur entrée et leur
sortie sont RGBA linéaire prémultiplié. On note `U(c)` l'unpremultiply sûr
(`a == 0` donne RGB zéro), `P(c)` la prémultiplication, `sat(x)` le clamp
composante par composante dans `[0,1]`, et `M(v)` la multiplication row-major
d'une matrice 4×5 par `(r,g,b,a,1)`. Sauf mention contraire, la formule travaille
sur `u = U(input)`, clamp son résultat straight, puis le prémultiplie.

| Filtre | Formule, domaine et alpha |
| --- | --- |
| `Matrix` | `P(sat(M(u)))`; exactement 20 coefficients F32 finis, translation déjà exprimée en unités `[0,1]` |
| `HSLAMatrix` | convertir `u.rgb` par `rgbToHslV1`, appliquer `M(h,s,l,a)`, convertir le triplet résultant par `hslToRgbV1`, puis `P(sat(...))`; exactement 20 coefficients finis |
| `Table` | `P(table(round(sat(channel)*255))/255)` indépendamment sur R,G,B,A ; exactement 256 entrées, même table pour les quatre canaux |
| `Lighting(mul,add)` | `P(sat(u.rgb * toLinear(mul.rgb) + toLinear(add.rgb)), u.a)` ; les alpha de `mul` et `add` sont ignorés et l'alpha d'entrée est inchangé |
| `Blend(color,mode)` | `BlendMathV1(dst=input, src=toLinearPremul(color), mode)` ; sortie prémultipliée, avec les mêmes 29 formules que le blend de draw |
| `Compose(outer,inner)` | `outer(inner(input))`, sans fusion commutative ni double conversion |
| `Lerp(t,dst,src)` | `(1-t)*dst(input) + t*src(input)` en prémultiplié ; `t` doit être fini dans `[0,1]` |
| `SRGBToLinear` | appliquer l'EOTF sRGB à chaque RGB straight, alpha inchangé, puis prémultiplier |
| `LinearToSRGB` | appliquer l'OETF sRGB à chaque RGB straight, alpha inchangé, puis prémultiplier |
| `HighContrast` | preset public figé `grayscale=false`, `invert=NONE`, `contrast=0.5` de `HighContrastV1` ci-dessous |
| `Luma` | `P(0,0,0, u.a * dot(u.rgb,(0.2126,0.7152,0.0722)))` |
| `Overdraw` | `index=min(round(sat(u.a)*255),5)`, puis couleur straight de la palette V1 ci-dessous, indépendamment du RGB d'entrée |

L'EOTF sRGB vaut `x/12.92` pour `x <= 0.04045`, sinon
`((x+0.055)/1.055)^2.4`. L'OETF inverse vaut `12.92*x` pour
`x <= 0.0031308`, sinon `1.055*x^(1/2.4)-0.055`. Les entrées de ces deux
filtres sont des valeurs numériques straight ; le nom du filtre exprime la
transformation demandée, il ne retague pas le target et n'autorise aucune
seconde conversion implicite.

`rgbToHslV1` emploie `max`, `min`, `delta=max-min`, `l=(max+min)/2`,
`s=0,h=0` si `delta=0`, sinon
`s=delta/(1-abs(2*l-1))` et le hue en tours vaut, modulo 1 :
`((g-b)/delta)/6` si R est maximal,
`(((b-r)/delta)+2)/6` si G est maximal, et
`(((r-g)/delta)+4)/6` sinon. `hslToRgbV1` utilise
`c=(1-abs(2*l-1))*s`, `x=c*(1-abs((6*h mod 2)-1))`, le secteur
`floor(6*h) mod 6`, puis ajoute `m=l-c/2`. Après la matrice HSLA, H est ramené
modulo 1 ; S et L entrent sans clamp intermédiaire dans la conversion, puis
RGB et A sont clampés avant prémultiplication.

`HighContrastV1(rgb, grayscale, invert, contrast)` opère en straight linear :
grayscale remplace RGB par le luma Rec.709, `BRIGHTNESS` fait `1-rgb`,
`LIGHTNESS` convertit en HSL et remplace `L` par `1-L`, puis
`scale=(1+contrast)/(1-contrast)` et
`rgb=sat(0.5 + scale*(rgb-0.5))`; alpha inchangé. La forme configurable Skia
n'existe pas encore dans l'API Kanvas : le preset ci-dessus ferme sans ambiguïté
l'objet historique, et l'ajout d'un `HighContrastConfig` public reste un gap
d'API distinct, sans modifier ce preset.

La palette `OverdrawV1` en ARGB est exactement
`[0x80FF0000, 0x8000FF00, 0x800000FF, 0x80FFFF00, 0x8000FFFF,
0x80FF00FF]`. Elle est convertie en linear-premul comme tout `ColorARGB`.

Une taille invalide, un coefficient non fini, un `t` hors contrat ou une
conversion non finie refuse avant `Ready`. Les données restent dynamiques et
hors de la program key. Pour chaque ligne du tableau, les gates W5f couvrent
alpha zéro/non trivial, valeurs nécessitant un clamp et une composition
non commutative ; elles comparent l'oracle CPU et les pixels publics.

Les runtime color filters ne deviennent exécutables qu'avec l'autorité
enregistrée de W5h. Les image filters, mask filters, blur, crop, backdrop et
autres effets spatiaux restent W6.

`MaterialNode.Blend`, `WithColorFilter`, `WithWorkingColorSpace`, `Opacity`,
`WithLocalMatrix` et `CoordClamp` composent un DAG partagé.

### 11.2 Perlin et Fractal

`PerlinNoise` désigne la turbulence Skia (`abs(noise)`) et `FractalNoise` la
forme signée ramenée dans `[0,1]`. Les deux utilisent `NoiseV1`, commun au CPU
et au WGSL : table de 256 entrées, quatre champs de gradients 2D et générateur
Park–Miller `seed = (16807*seed) mod (2^31-1)` implémenté sans overflow par les
quotient/reste `127773` et `2836`. Le seed I32 est normalisé dans
`[1,2^31-2]` : zéro/négatif devient `-(seed mod (2^31-2))+1`, et une valeur
supérieure est clampée.

L'initialisation est byte-exacte : `perm[i]=i`; pour chaque canal 0..3 puis
chaque `i` 0..255, tirer deux valeurs `random()%512`, soustraire 256, diviser
par 256 et normaliser le vecteur. Ensuite, pour `i` de 255 à 1, échanger
`perm[i]` avec `perm[random()%256]`. Les quatre tables de gradients sont
réordonnées par la permutation finale. Chaque composante normalisée est stockée
comme `round((v+1)*32767.5)` U16, avec arrondi au plus proche et tie vers le
haut puisque l'opérande est positif. CPU et GPU consomment exactement les
mêmes 256 bytes de permutation et 4×256×2 U16 little-endian au lieu de
régénérer chacun leur table.

Pour chaque canal et octave, à la coordonnée locale `P` :

```text
q0 = (P + 0.5) * baseFrequency
i  = floor(q0); f = fract(q0); smooth = f*f*(3-2*f)
b00 = (perm[i.x & 255]       + i.y)     & 255
b10 = (perm[(i.x+1) & 255]   + i.y)     & 255
b01 = (perm[i.x & 255]       + i.y + 1) & 255
b11 = (perm[(i.x+1) & 255]   + i.y + 1) & 255
n  = bilerp(dot(g00,f), dot(g10,f-(1,0)),
            dot(g01,f-(0,1)), dot(g11,f-(1,1)), smooth)
sum += (PerlinNoise ? abs(n) : n) * amplitude
q0 *= 2; amplitude *= 0.5
```

`FractalNoise` termine par `sum*0.5+0.5`; `PerlinNoise` conserve la somme
absolue. Après toutes les octaves, RGBA est clampé, puis RGB est multiplié par
l'alpha clampé. Zéro octave donne donc transparent pour `PerlinNoise` et
`(0.25,0.25,0.25,0.5)` prémultiplié pour `FractalNoise`.

`baseX/baseY` doivent être finis et >= 0 ; `numOctavesI32` est borné à
`0..255`. W5g remplace le `tileSize: SizeF32?` ambigu par `SizeI32?` dans
`:math:geometry`. L'overload F32 historique n'est accepté que si largeur et
hauteur sont finies, positives ou nulles, intégrales et représentables en I32 ;
il est ensuite converti et déprécié. Un tile absent ou dont un axe vaut zéro
désactive le stitching. Pour un tile ayant deux axes positifs, chaque fréquence
est ajustée vers `low=floor(tile*frequency)/tile` ou
`high=ceil(tile*frequency)/tile` : choisir `low` si
`frequency/low < high/frequency`, sinon `high`, ce qui fixe le cas `low=0` et
le tie vers `high`. Le nombre de périodes entier
`round(tile*adjustedFrequency)` est doublé à chaque octave et les quatre coins
lattice wrapent sur cette période avant lookup. `MaterialNode`, `Picture` et
`SceneArchiveCodec` changent
de version avec ce type ; les payloads historiques sont validés puis convertis
explicitement. Les paramètres invalides refusent à la capture.

Les tables NoiseV1 sont frame-locales et partageables entre nœuds de même seed;
leurs octets, le nombre d'octaves et les itérations shader sont préflightés. Un
budget `maxNoiseOctaveEvaluationsI64` borne
`pixelsConservateurs * numOctaves * 4` avant `Ready`. Les paramètres ne créent
pas de variantes de shader et aucun bruit pseudo-aléatoire backend ne peut se
substituer à NoiseV1.

Aucun nœud inconnu ou non exécutable n'est remplacé par un child, un solid ou
transparent. Le résultat est un refus stable avant ownership natif.

## 12. Runtime effects enregistrés

W5 n'introduit pas de frontend SkSL arbitraire. L'autorité est séparée en trois
couches, reliées par la clé `(RuntimeEffectId, semanticVersionI32,
abiHash)` :

1. `RuntimeEffectSemanticCatalogSnapshot`, injecté dans `:gpu-plan`, contient
   uniquement des entrées backend-neutral `RuntimeEffectSemanticEntryV1` ;
2. l'oracle public résout `cpuEvaluatorId/version` vers un évaluateur pur ;
3. `:gpu-renderer` résout la même clé vers un module WGSL built-in enregistré
   et son manifest de reflection.

Une entrée sémantique contient le kind shader/color-filter/blender, l'ordre des
uniforms avec type scalaire, count, byte size, offset, alignment et stride, les
child slots avec nom/type/nullability, les ressources logiques autorisées avec
slot/type, les limites de graphe et de bindings, ainsi que
l'identité/version obligatoire de l'évaluateur CPU. Elle ne contient ni WGSL,
ni handle WebGPU. Son snapshot et sa version de dictionnaire sont figés avant
la compilation de la frame.

W5h fait évoluer `pipeline.RuntimeEffect` et `RuntimeEffectDescriptor` avec
`semanticVersionI32` et `abiHash`. La version zéro est réservée aux effets issus
de `compile(wgsl)` et n'est jamais catalogable. Une entrée enregistrée exige
une version strictement positive et un `abiHash` SHA-256 lowercase de 64
caractères. Ces deux champs entrent dans `runtime-effect-descriptor-v3`, dans
les identités des nœuds runtime et dans une nouvelle version de
`SceneArchiveCodec`/`Picture`; les archives antérieures décodent version zéro
et restent donc explicitement non exécutables par W5.

`RuntimeUniformBlockV1` décrit l'ABI logique locale d'un effet, sans group ni
binding physique. Les champs suivent l'ordre du descriptor, sans tri par nom,
avec `offset=alignUp(cursor,alignment)` relatif au début du block et une taille
finale alignée à 16 bytes :

| Type public | alignement | taille | représentation |
| --- | ---: | ---: | --- |
| `FLOAT`, `INT1` | 4 | 4 | `f32`, `i32` little-endian |
| `FLOAT2` | 8 | 8 | `vec2<f32>` |
| `FLOAT3` | 16 | 12 | `vec3<f32>` |
| `FLOAT4` | 16 | 16 | `vec4<f32>` |
| `MAT3X3` | 16 | 48 | 3 colonnes `vec3<f32>`, stride 16 |
| `MAT4X4` | 16 | 64 | 4 colonnes `vec4<f32>`, stride 16 |

`RuntimeUniformSlotV2` porte explicitement `offsetBytesI32`,
`sizeBytesI32`, `arrayCountI32` et `arrayStrideBytesI32`. W5 fixe
`arrayCountI32=1` et `arrayStrideBytesI32=0` pour les types scalaires ci-dessus;
toute déclaration d'array est refusée jusqu'à la définition d'une ABI V2.
Le champ historique `binding` par uniform est supprimé à cette frontière :
leur offset local est l'autorité.

Chaque ressource logique est décrite par un `RuntimeLogicalResourceSlotV1`
comprenant au minimum `logicalSlotI32`, `kind`, `addressSpace`, `access`,
`minBindingSizeBytesI64`, `textureViewDimension`, `textureSampleType`,
`multisampled` et `samplerType`, mais aucun group/binding physique. Un stop
buffer est `STORAGE/read`. Une image W5 est toujours
`TEXTURE/2D/float-filterable`; nearest, linear et cubic exécutent les taps
explicites de 9.2 par `textureLoad`, donc le slot porte `samplerType=NONE` et
aucun sampler caché ne peut modifier l'arrondi. Un effet futur déclarant un
sampler devra exposer un slot séparé `SAMPLER/filtering`, `non-filtering` ou
`comparison`, mais W5 refuse `comparison`. Un
child runtime n'est pas un handle : son sous-graphe est inline et ses slots de
ressource restent locaux à son entrée sémantique. Les textures storage,
dimensions autres que 2D, samplers comparison, arrays de bindings et address
spaces autres que `uniform`/`storage-read` sont refusés en W5.

`abiHash` est le SHA-256 de la sérialisation UTF-8 canonique de : kind,
semantic version, ordre/type/nullability des children, chaque champ uniforme
avec type/offset/size/alignment/count/stride, taille du block, chaque ressource
avec tous ses champs logiques ci-dessus, entrypoint logique et contrats
d'entrée/sortie couleur. Le group/binding physique et les offsets de composition
n'en font pas partie ; les valeurs d'uniform, pixels, stops et IDs de ressources
en sont également exclues. Le planner le recalcule depuis l'entrée sémantique
et le renderer depuis le manifest logique du module enregistré. Ils doivent
tous deux être byte-exactement égaux au hash du descriptor.

La composition physique appartient à
`MaterialBindingPlan.ComposedBindingLayoutV1`, jamais au descriptor runtime.
Pour le DAG material complet, le parcours préfixe et l'ordre déclaré des
children définissent une liste stable. Le bind group material est toujours 1 :

- `@group(1) @binding(0)` contient l'unique block uniforme composé ; pour
  chaque nœud material ayant un block logique,
  `baseOffsetBytesI32=alignUp(cursor,16)` et l'offset physique d'un champ vaut
  `baseOffsetBytesI32 + offsetBytesI32` local. Le block logique est
  `RuntimeUniformBlockV1` pour un effet runtime et le schéma versionné du
  `MaterialProgramPlan` pour un nœud built-in ;
- les stops, images et autres ressources sont affectés à partir de binding 1
  dans l'ordre du parcours ; chaque entrée physique conserve
  `ownerNodeIndexI32`, `logicalSlotI32`, `groupI32`, `bindingI32` et tous les
  faits de type du slot logique. Les slots des effets runtime viennent du
  descriptor ; les nœuds built-in utilisent le schéma versionné de leur plan ;
- `ownerNodeIndexI32` est attribué à la première visite préfixe ; une référence
  ultérieure au même nœud partagé réutilise cet index et la même plage de
  bindings. Toute divergence entre partage déclaré et parcours est un refus de
  planification.

`composedBindingLayoutHash` est le SHA-256 de la sérialisation canonique de la
taille totale du block, de chaque mapping
`ownerNodeIndexI32/localOffsetBytesI32 -> physicalOffsetBytesI32` et de chaque
mapping
`ownerNodeIndexI32/logicalSlotI32 -> groupI32/bindingI32/faits physiques`. Il
entre dans la clé du programme assemblé, pas dans `abiHash`.

Le manifest renderer contient le même `abiHash`, le fragment WGSL enregistré,
ses slots logiques attendus et les capabilities physiques. Le fragment
enregistré n'a aucune annotation `@group/@binding`; l'assembleur déclare les
ressources physiques du module final depuis `ComposedBindingLayoutV1`. Avant
toute création de pipeline, le renderer valide d'abord le manifest logique
contre `abiHash`, puis la reflection du WGSL assemblé — offsets/strides,
group/binding, address spaces, texture sample/dimension et sampler type —
contre `composedBindingLayoutHash`. Une clé absente d'une des trois couches,
une version différente, un child incompatible, un hash différent ou un budget
dépassé produit un refus terminal avant ownership natif.

L'API actuelle `RuntimeEffect.compile(wgsl)` peut continuer à construire un
snapshot/descripteur version zéro pour compatibilité, mais son
auto-enregistrement de WGSL ne confère aucune capability W5. Sans triplet exact
dans le catalogue sémantique, avec évaluateur CPU et module renderer,
l'exécution répond
`unsupported.material.runtime_effect.unregistered_semantics` et ne tente jamais
d'exécuter le WGSL appelant.

W5h retient une surface bornée : la librairie publie un lookup
`RuntimeEffect.registered(id, semanticVersionI32)` pour sélectionner un effet
built-in du catalogue et livre au moins un effet passthrough avec uniform et
child afin de fermer l'ABI end-to-end. L'enregistrement applicatif d'un nouveau
triplet sémantique/CPU/WGSL est reporté ; cette décision évite que
`compile(wgsl)` redevienne une autorité implicite.

## 13. Data flow

```text
Paint / Shader public
        │ snapshot immuable
        ▼
DrawNode {material, paint, effects, resource, final blend} (:render-ir)
        │ normalisation EffectiveMaterial + catalogue + capabilities
        ▼
EffectiveMaterialPlan + MaterialProgramPlan + MaterialBindingPlan (:gpu-plan)
        │ attachés aux draws/passes/resources avant Ready
        ▼
RenderGraph scellé
        │ lowering backend + validation WGSL/reflection/ABI
        ▼
PreparedMaterialExecutable (:gpu-renderer)
        │ location/upload/bind des seules ressources planifiées
        ▼
passes géométriques W4 + blend final + readback public
```

Une fois la frame promue à W5, tout échec est terminal pour cette frame. Aucun
fallback legacy ou CPU n'est possible après ownership.

## 14. Gestion des erreurs et récupération

Les refus sont classifiés par frontière : capture, graph validation, material
program, binding, color conversion, capability, budget, preflight et native.
Le diagnostic conserve l'index du draw, l'identité material et la ressource
concernée sans exposer d'objet backend.

Les plans et snapshots restent publiés seulement après validation complète.
Un refus de budget ne consomme aucune allocation. Une panne native libère ou
met en quarantaine les ressources acquises selon les contrats de pool existants.

Conformément à la contrainte de tests, W5 ne crée ni API de contrôle de panne,
ni proxy interne, ni reflection ou compteur d'appels. La récupération est
prouvée par un refus public atteignable suivi d'un rendu exact sur la même
surface. Les sites natifs non injectables restent des gaps d'intégration
documentés.

## 15. Stratégie de tests

Le développement suit RED → GREEN → refactor pour chaque comportement.

Les preuves obligatoires sont publiques et mutation-sensitive :

- `Surface`, `Canvas`, `Picture` et `render()` ;
- pixels byte-exacts ou tolérance uniquement lorsqu'elle provient d'un calcul
  analytique explicite et non d'un seuil de similarité ;
- petit oracle CPU indépendant pour gradient, sampling, color et blend ;
- combinaisons ordonnées de matériaux, géométries et blends que les routes
  legacy ne savent pas satisfaire intégralement, avec pixels et diagnostics
  publics comme seules observations ;
- mutation des stops, matrices, pixels, filters, uniforms, paint alpha et
  children après capture ;
- frames mixtes Rect/RRect/Path/points/text/vertices quand leur route préparée
  est déjà admise ;
- refus puis récupération publique sur la même surface ;
- validation JVM/JS des nouvelles fonctions `:math`.

Sont interdits : tests de source shape, accès private/internal comme preuve,
reflection, call counts, assertion d'identité du cache, injection artificielle
de capability, scopes/counters/structural steps de `RenderResult` et tests
d'infrastructure du code. L'unicité architecturale de la route material est
vérifiée par inspection statique humaine lors des reviews, jamais par un test
qui lit la structure ou les compteurs du renderer.

Les tests `font`, codecs, GM Skia, dashboard, renders/baselines,
`:integration-tests:skia` et `jpg-color-cube` restent hors des gates W5. La
convergence GM est réservée à W7.

## 16. Découpage des PR stackées

Chaque tranche part de la précédente et possède sa propre PR :

| Tranche | Contenu | Gate de sortie |
| --- | --- | --- |
| W5a | plans communs, solid, opacity, suppression des fallbacks silencieux concernés | pixels solid/opacity identiques sur les géométries W4 admises |
| W5b | fixed-function commun, destination-read multi-draw et snapshot grouping | fermeture prouvée des 45 `DrawPoint` historiques |
| W5c | quatre gradients et stop buffer sans plafond 16 | 1/2/16/>16 stops, doublons sur Rect, RRect, Path fill et Path stroke |
| W5d | matrices locales, `CoordClamp`, quatre tile modes | transform classes et domaines exacts |
| W5e | images RGBA/A8 déjà décodées, nearest/linear/cubic | sampling, alpha, color space et mutation pixels |
| W5f | color filters et domaines d'interpolation existants | chaîne colorimétrique exacte et graphes filter composés |
| W5g | blend children, DAG partagé, Perlin/Fractal | ordre child, partage, limites et récupération |
| W5h | runtime effects enregistrés, nettoyage et gates globales | ABI/children exacts, aucune route material parallèle restante |

### 16.1 Matrice minimale de promotion

`T` signifie gate publique obligatoire à la sortie de la tranche, `H` promotion
obligatoire au plus tard dans W5h, et `—` combinaison que la sémantique de
l'origin public n'applique pas. Chaque cellule promue couvre au minimum alpha
non trivial, mutation post-capture et un blend final non trivial ; elle ne
représente pas un test d'infrastructure.

| Famille / tranche | Rect | RRect | Path fill | Path stroke | Point(s) | Text | Vertices/Mesh | Image origin |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Solid + Opacity / W5a | T | T | T | T | T | T | T | A8:H, RGBA alpha:H |
| Blend final / W5b | T | T | T | T | T:15 modes×3 contextes | T | T | H |
| 4 gradients / W5c | T | T | T | T | H | H | H | A8:H, RGBA:— |
| LocalMatrix + tile + CoordClamp / W5d | T | T | T | T | H | H | H | A8:H, RGBA:— |
| ImageSample / W5e | T | H | T | H | H | H | H | T |
| Color filters / W5f | T | H | T | H | H | H | H | T |
| Blend children + noise / W5g | T | H | T | H | H | H | H | A8:H, RGBA:— |
| Runtime effect catalogué / W5h | T | H | T | H | H | H | H | A8:H, RGBA:— |

Les 15 modes de W5b désignent exactement les modes actuellement inscrits dans
le ledger `GPUAllApiBlendSurfaceTest :: DrawPoint`; les trois contextes et les
trois commandes successives sont conservés. Pour les autres cellules W5b, un
mode fixed-function, `DST/NoOp` et un mode destination-read sont requis. Toute
cellule `H` encore non promue empêche la fermeture W5, sauf capability physique
déjà explicitement hors périmètre (AA4 reste notamment un refus W4 tracé).

Les cellules Text réutilisent uniquement des glyphs/runs déjà résolus par une
route préparée admise et ne modifient ni ne valident la génération de font. Les cellules
Image construisent leurs bytes décodés en mémoire et n'invoquent aucun codec.

Le document de suivi sera `refactor/waves/W05-material-graph/status.md`. Les
rapports temporaires d'agents restent ignorés ; aucun document de suivi n'est
ajouté hors de `refactor/`.

## 17. Critères de fermeture W5

W5 est fermée lorsque :

1. chaque famille `MaterialNode` de ce document est soit rendue par la route
   commune, soit refusée uniquement sur une capability physique explicitement
   hors W5 ;
2. aucune route migrée ne reconstruit son propre descripteur/programme material ;
3. program structure et valeurs dynamiques sont séparées ;
4. stops, uniforms, images et children respectent les budgets de capture avant
   leur copie, puis les budgets device/frame avant toute allocation native ;
5. geometry, coverage, material et blend restent des axes indépendants ;
6. les 45 failures `DrawPoint` sont fermées, sans échappatoire documentaire ;
7. les gates ciblées sont vertes et la suite globale ne contient aucun nouveau
   nom de failure/error ;
8. fonts, codecs et GMs n'ont pas été utilisés pour gonfler le résultat ;
9. toutes les cellules `T` et `H` de la matrice applicable sont promues ;
10. les caches device/session respectent génération, leases, budgets et
    éviction ; les chemins de device loss non injectables restent un gap
    d'intégration tracé, pas une preuve fabriquée ;
11. une revue finale Sol de la stack complète ne contient aucun finding
   Critical ou Important.

## 18. Limites explicitement reportées

- topologie et resolve AA4 natifs de W4 ;
- targets HDR et formats physiques autres que les targets sRGB 1× déjà admis ;
- working gamut arbitraire dans `WithWorkingColorSpace` tant que l'IR ne porte
  pas un vrai snapshot `ColorSpace` ;
- décodage/encodage d'images ;
- images externes sans snapshot de pixels et cache inter-frame des stop buffers ;
- surface publique configurable de `HighContrast` ; W5 fige seulement le
  preset historique sans paramètre ;
- font et glyph generation ;
- image filters, mask filters, layers, backdrop et effets spatiaux W6 ;
- frontend SkSL arbitraire ;
- convergence, score et rebaseline GM W7 ;
- retrait final du renderer legacy W8.

## 19. Références sémantiques

Les règles Skia reproduites par les sections 5.3, 7, 8 et 11 sont ancrées sur le
commit upstream `70977ebbdbc111776199920c8c25243ba5dc71db` :

- [`SkShader.h`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/include/core/SkShader.h), pour la modulation d'un shader par l'alpha du paint ;
- [`SkGradient.h`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/include/effects/SkGradient.h) et [`SkGradientBaseShader.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/gradients/SkGradientBaseShader.cpp), pour stops, interpolation et hard stops ;
- [`SkLinearGradient.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/gradients/SkLinearGradient.cpp), [`SkRadialGradient.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/gradients/SkRadialGradient.cpp), [`SkSweepGradient.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/gradients/SkSweepGradient.cpp) et [`SkConicalGradient.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/gradients/SkConicalGradient.cpp), pour les paramètres et dégénérescences ;
- [`SkShaderBase.h`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkShaderBase.h), [`SkShaderBase.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkShaderBase.cpp) et [`SkShader.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkShader.cpp), pour l'ordre CTM/local matrices ;
- [`SkImageShader.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkImageShader.cpp), pour les contraintes de sampling image.
- [`SkMatrixColorFilter.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/effects/colorfilters/SkMatrixColorFilter.cpp) et [`SkHighContrastFilter.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/effects/SkHighContrastFilter.cpp), pour les domaines straight/premul et la formule high-contrast ;
- [`SkPerlinNoiseShaderImpl.cpp`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkPerlinNoiseShaderImpl.cpp) et [`SkPerlinNoiseShaderImpl.h`](https://skia.googlesource.com/skia/+/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkPerlinNoiseShaderImpl.h), pour le PRNG, les gradients et le stitching NoiseV1.
- [`WebGPU Shading Language`](https://www.w3.org/TR/WGSL/), pour les types
  scalaires disponibles, les contraintes de layout et les bindings physiques
  du module assemblé.

Ces références fixent la sémantique attendue ; elles n'introduisent aucun test
GM, baseline ou dépendance au code Skia dans les gates W5.
