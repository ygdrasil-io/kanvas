# Route image préparée pour `Surface` — conception FP-04

**Date :** 2026-07-25
**Révision :** 2026-07-26
**Statut :** conception approuvée, révisée après audit indépendant des Tasks 1 à 5

## Objectif

FP-04 migre les quatre opérations image publiques de `Surface` vers la route
de frame préparée commune :

- `DrawImage` ;
- `DrawImageNine` ;
- `DrawImageLattice` ;
- `DrawAtlas`.

La migration inclut la possession explicite des pixels, de la texture, de la
vue et du sampler, l'ordre upload-avant-échantillonnage, l'exécution WebGPU
native et les preuves pixels/alpha. À la fin de FP-04, aucune de ces opérations
ne produit `legacy.surface.prepared.family.images` et `Images` n'appartient plus
à l'allowlist du renderer immédiat.

## Décision

La route est livrée verticalement par tranches fermées. L'audit du
2026-07-26 conserve les Tasks 1 à 4, mais considère la Task 5 comme
implémentée et non encore acceptée. L'ordre devient :

1. contrat sémantique image sans handle ;
2. frame préparée hétérogène et ordonnée, capable de combiner payloads solid
   et image ;
3. plan de ressource et préflight ;
4. matérialiseur WebGPU/WGSL natif ;
5. consolidation obligatoire de l'ABI, du partage de ressources, du mapping
   draw→source, des générations, des diagnostics et des `PipelineKey` ;
6. preuve puis correction de la chaîne sRGB préparée, avant toute preuve pixel
   d'admission ;
7. matérialiseur de frame mixte globalement préflighté avec un seul
   propriétaire et sans snapshot CPU de destination ;
8. préparation complète de `DrawImage` ;
9. expansion transactionnelle de `DrawImageNine` puis
   `DrawImageLattice` ;
10. payload de quad texturé et migration affine complète de `DrawAtlas` ;
11. admission produit atomique des quatre opérations et retrait de la famille
   legacy `Images`.

La route immédiate existante sert de source de comportement et de contre-preuve
pour les défauts connus. Elle n'est ni appelée depuis la frame préparée, ni
enveloppée dans un nouveau nom, ni utilisée comme fallback.

## Hiérarchie des autorités et références

Les documents cités n'ont pas tous la même autorité ni la même fraîcheur.
FP-04 résout les ambiguïtés dans l'ordre suivant.

### Règles et cibles d'architecture actives

- `AGENTS.md` fixe les décisions du dépôt et interdit d'utiliser les anciens
  checklists, phases ou backlogs comme critères actifs ;
- `.upstream/target/high-performance-wgsl-pipeline-target.md` contraint
  l'architecture pipeline, shader et convergence CPU/GPU ;
- `.upstream/target/skia-like-realtime-renderer-target.md` et
  `.upstream/specs/skia-like-realtime/README.md` contraignent l'expansion du
  renderer temps réel.

Ces sources priment sur une affirmation contraire contenue dans une spec
`Draft`. En particulier, l'affirmation de
`.upstream/specs/gpu-renderer/29-color-management-pipeline.md` selon laquelle
la cible high-performance serait « older » n'est pas applicable : `AGENTS.md`
désigne cette cible comme contrainte actuelle.

### Acceptance active de FP-04

`reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` définit
le périmètre et les critères de fermeture de FP-04 : les quatre opérations
image, leur ownership texture/sampler, l'exécution native testée, les preuves
pixels/alpha, la disparition de `legacy.surface.prepared.family.images` et le
retrait de l'allowlist `Images`.

Cette acceptance n'active pas implicitement les matrices cibles complètes des
specs image, couleur ou paint.

### État d'implémentation vérifiable

Le source et les tests courants attestent seulement ce qui existe réellement ;
ils ne constituent pas une autorité d'architecture et ne réduisent pas la
cible. Leur comportement doit être soit préservé, soit migré par une décision
explicite accompagnée de preuves. Inversement, une spec `Draft` ne prouve pas
l'activation d'une capacité absente.

L'audit a établi que le mapping courant `RGBA8Unorm +
EncodedPremulSrgb` ne suffit pas comme preuve de fidélité pour les couleurs
translucides. La route ne doit donc plus préserver ce mapping par principe.
Elle doit le comparer à un oracle indépendant et au store sRGB natif, puis
retenir la représentation qui reproduit les octets de référence. La direction
attendue est une texture source sRGB, des calculs shader en
`linear-premultiplied RGBA` (RGBA linéaire prémultiplié) et un attachment
`RGBA8UnormSrgb`, mais la décision finale reste subordonnée au test natif
minimal et à la réflexion des capacités WebGPU.

### Specs `Draft` utilisées comme références techniques bornées

Les specs gpu-renderer ci-dessous sont des références de vocabulaire et
d'invariants, pas un backlog, une acceptance ou une preuve d'activation :

- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md` : ownership du
  provider, artefact uploadé, séparation clé/payload, lifetime et ordre
  upload-avant-échantillonnage ;
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md` : pixels CPU
  déjà décodés, snapshot, alpha/orientation, identité d'artefact, bridge
  d'ownership et vocabulaire de refus ;
- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md` : provenance
  SDR connue, prémultiplication, alpha et refus couleur bornés ;
- `.upstream/specs/gpu-renderer/30-coordinate-transform-bounds-policy.md` :
  espaces source/texture/atlas, classification des transforms affines, bounds
  finis et conservateurs, et refus d'une route affine-only ;
- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md` :
  séparation clé/payload, ordre d'évaluation du paint, subset/domaine,
  coordonnées, sampling, alpha-only, binding texture/view/sampler et dépendance
  d'ownership.

Sont explicitement exclus de l'autorité FP-04 :

- les politiques de séquencement `First Slice` des specs 18, 29, 30 et 31,
  qui ne sont ni le séquencement ni l'acceptance de FP-04 ; leurs invariants
  techniques compatibles restent applicables lorsqu'ils sont sélectionnés
  ci-dessus ;
- la matrice codec cible complète de la spec 22 ;
- l'activation générale des pipelines couleur, image-shader et paint décrits
  par les specs 29 et 31 ;
- toute adoption sans preuve de la section `Initial SDR Implementation — sRGB
  Output Format` de la spec 29. FP-04 réutilise son vocabulaire, mais tranche
  son mapping par un test natif minimal et un oracle indépendant ;
- toute lecture de `GPUImageShaderPlan` qui ferait du pipeline
  material/image-shader complet un préalable à `Surface.DrawImage` ; FP-04
  réutilise seulement ses invariants sélectionnés de source, coordonnées,
  sampling, alpha-only, binding et ownership.

En cas de divergence, les règles et cibles actives contraignent la conception,
l'acceptance FP-04 en fixe le périmètre, le code et les tests attestent l'état
réel, et les specs `Draft` ne fournissent que les invariants bornés listés
ci-dessus.

## Consolidation obligatoire après audit des Tasks 1 à 5

La Task 5 ne peut pas servir de fondation à la frame mixte tant que les six
écarts suivants ne sont pas corrigés.

### ABI image canonique

Une seule valeur réfléchie et versionnée définit le groupe 0 :

- binding 0 : uniform buffer dynamique de 112 octets ;
- binding 1 : texture 2D filtrable ;
- binding 2 : sampler filtrant ;
- identité et hash de layout dérivés de cette topologie.

Le builder, le preflight, le shader, la session cache et le matérialiseur
consomment ce même contrat. Aucun composant ne peut substituer une autre
identité pour faire accepter un plan incohérent.

### Mapping draw→source et partage

Le `sourceId` est une provenance, pas une clé d'unicité de draw. Chaque
commande image conserve l'opération source exacte qui l'a produite. Plusieurs
draws de la même image sont valides et peuvent partager l'artefact, la
texture/view, le sampler et le bind group lorsque leurs clés canoniques sont
identiques. Leur géométrie, tint, clip et allocation uniforme restent propres
à chaque draw.

### Autorité de ressource unique

La Task 5 réutilise le provider et les clés de la Task 4 :

- texture/view par `UploadArtifactKey` ;
- sampler par `SamplerDescriptorKey` ;
- bind group par `BindingKey` ;
- allocation dynamique uniforme par commande.

Une boucle locale qui recrée un bind group par requête n'est pas conforme.

### Génération native

Une session cache est scellée sur un `GPUDevice` et une génération exacts. Une
requête d'une autre génération est refusée ; le propriétaire runtime ferme le
cache et en construit un nouveau avec le nouveau device. Un cache ne change
jamais sa génération tout en conservant le même device.

### Diagnostics canoniques

Chaque refus transporte directement le code stable de la table FP-04. Les
couches Surface, recording, preflight et native peuvent enrichir `facts` et
`message`, mais ne préfixent ni ne renomment le code. Un même fait produit le
même code à toutes les frontières.

### Spécialisation mesurée

Chaque axe de `GPUPreparedImagePipelineKey` est classé
`LayoutAffecting`, `CodeAffecting`, `PipelineStateAffecting` ou `UniformOnly`.
Seules les trois premières classes restent dans la clé. Un axe uniform-only
ne peut être spécialisé qu'avec des mesures de nombre de shaders/pipelines,
cache hits/misses, créations après warmup, octets uniformes et nombre de
draws.

## Frontières d'architecture

Les invariants sont :

- WebGPU reste le backend GPU produit ;
- le CPU fournit une référence et peut préparer des pixels sources, mais ne
  rasterise pas un draw, un layer ou une scène non supportée dans une texture
  de compatibilité ;
- le plan de frame reste sans handle natif ;
- les handles sont créés tardivement par le provider de ressources ;
- le snapshot immuable des pixels source reste autorisé, mais aucun snapshot
  CPU de la destination ni réupload de continuation ne fait partie de la route
  principale FP-04 ;
- l'identité de ressource, les pixels et les handles ne font jamais partie
  d'une `PipelineKey` ;
- toute capacité absente produit un diagnostic stable avant allocation native,
  sans continuation immediate/CPU cachée ;
- aucun port Ganesh, Graphite ou compilateur SkSL n'est introduit.

## État initial

`GPUPreparedSurfaceFrameGate` classe actuellement les quatre opérations comme
`legacy.surface.prepared.family.images`. Si cette gate était simplement
ouverte, `GPUOpMapper`, le builder sémantique et le dispatcher natif ne
pourraient construire que les payloads `CorePrimitive`.

Le dispatcher actuel sélectionne aussi une unique classe sémantique pour toute
la frame et refuse `mixed-semantic-shape`. Or une frame normale peut alterner
rectangles et images, et une seule lattice peut produire à la fois des cellules
solid `FIXED_COLOR` et des cellules image. FP-04 doit donc généraliser la frame
préparée à une suite hétérogène ordonnée ; ajouter seulement une branche image
au dispatcher ne suffirait pas.

Le code existant offre néanmoins des briques réutilisables :

- `DrawImage` peut déjà être normalisé en `DrawImageRect` ;
- image-nine possède une décomposition 3×3 ;
- lattice possède la décomposition fixed/scalable, les cellules transparentes
  et les cellules `FIXED_COLOR` ;
- l'ancien renderer sait télécharger des pixels RGBA et coloriser les images
  alpha-only ;
- les contrats bas niveau d'upload, de texture et de sampler existent, mais ne
  sont pas reliés au builder de frame `Surface`.

Le chemin atlas existant n'est pas une référence de fidélité affine :
`computeAtlasDst` transforme les quatre sommets puis les réduit à un rectangle
englobant. Une rotation ou un skew perd donc le mapping géométrie/UV attendu.

## Contrat source image préparée

Un nouveau contrat fermé représente une source échantillonnable sans référence
native. Les noms définitifs suivront les conventions du paquet, mais le contrat
doit contenir au minimum :

- dimensions finies et non vides ;
- format logique accepté : RGBA8 prémultiplié, BGRA8 prémultiplié ou A8
  alpha-only ;
- espace couleur source exactement `ColorSpace.SRGB` et orientation déjà
  résolue à l'identité ;
- row-bytes validé et taille de contenu bornée ;
- snapshot immuable des octets nécessaires à l'upload ;
- hash de contenu, génération et provenance ;
- origine, swizzle, orientation, espace couleur SDR et politique alpha
  explicites ;
- sous-rectangle source et domaine d'échantillonnage ;
- classe d'échantillonnage `Nearest` ou `Linear` ;
- transform, clip, paint, blend et ordre de draw capturés ;
- clé d'artefact d'upload distincte de toute clé de pipeline.

Le snapshot doit isoler la frame de toute mutation ultérieure de l'objet
`Image`. Puisque `Image.pixels` est un `ByteArray` mutable et qu'`Image`
n'expose pas aujourd'hui de génération, l'ordre est obligatoire :

1. copie défensive des octets ;
2. validation des dimensions, du row-bytes et de la longueur de la copie ;
3. conversion physique acceptée ;
4. hash de la copie convertie ;
5. construction de l'identité d'artefact.

Deux commandes qui désignent exactement le même contenu converti et la même
génération logique peuvent partager un artefact d'upload. Un changement de
contenu, format, dimensions, row-bytes ou génération force une identité
différente.

Les octets décodés sRGB sont d'abord snapshottés en RGBA8 prémultiplié au
boundary CPU. L'artefact physique d'échantillonnage peut appliquer une
conversion explicitement identifiée et hashée. Son contrat est déterminé par
une gate couleur dédiée :

1. une texture `RGBA8Unorm` avec interprétation manuelle courante ;
2. une texture source sRGB recevant directement les octets prémultipliés ;
3. une texture source sRGB recevant des octets straight sRGB obtenus par
   unpremultiply borné au boundary, puis re-premultiply en linéaire dans le
   shader ;
4. un attachment `RGBA8UnormSrgb` avec encodage matériel au store.

Le test compare ces chemins à un oracle indépendant pour des couleurs opaques
et translucides. La troisième représentation est la candidate attendue :
hardware sRGB decode produit une couleur straight linéaire et le shader la
prémultiplie par l'alpha avant tint/blend. La couverture A8 reste une texture
linéaire `RGBA8Unorm` et n'est jamais sRGB-décodée. La route n'accepte cette
candidate que si elle reproduit l'oracle sans correction CPU de destination.
Le résultat devient l'autorité de
`mapPreparedGpuColorConfig()`, des clés de target, de la réflexion pipeline et
des attentes readback. Si WebGPU ou wgpu4k ne peut pas exprimer le contrat
retenu, la route reste fermée avec un diagnostic stable et un ticket wgpu4k
minimal ; aucun workaround caché n'est admis.

La normalisation canonique initiale est :

- RGBA8 est copié sans réordonnancement ;
- BGRA8 est converti en RGBA8 au boundary de préparation ;
- A8 est développé en RGBA8 en répliquant la couverture dans les quatre
  canaux, tandis que le payload conserve `alphaOnly=true` pour la colorisation
  et interdit une vue sRGB qui appliquerait une fonction de transfert à la
  couverture.

Pour une source couleur translucide, l'artefact natif dérivé convertit ensuite
chaque canal premul encodé vers straight encodé avec
`round(channel * 255 / alpha)`, borné à `0..255`, et conserve l'alpha. Pour
alpha zéro, les canaux couleur deviennent zéro. La clé d'upload inclut le type
de conversion et le hash des octets physiques. Cette conversion est une
préparation de pixels source, pas un rendu CPU de compatibilité.

Les sources encodées peuvent alimenter ce contrat seulement après un décodage
accepté par le propriétaire codec. FP-04 ne choisit pas un codec et ne masque
pas une dépendance de décodage absente.

## Sémantiques de draw

La somme fermée des payloads préparés gagne deux formes image :

### Rectangle image échantillonné

Le payload porte le rectangle destination local, le domaine UV, le transform
affine, le binding logique de la source, l'échantillonnage, le paint/blend et
les faits de clip. Il sert à `DrawImage` et aux cellules image de nine/lattice.
Avant l'exécution, ses quatre coins sont transformés sans réduction à un
rectangle englobant. Le chemin rect peut rester une optimisation exacte pour
identité/translation/échelle alignée ; rotation, réflexion et skew utilisent le
même quad texturé que l'atlas.

### Quad image échantillonné

Le payload porte quatre positions de destination indépendantes, quatre
coordonnées UV et deux triangles à winding déterministe. Il sert à
`DrawAtlas` et à toute image rect/nine/lattice dont le transform affine ne
préserve pas un rectangle aligné.

Pour chaque sprite atlas, les quatre coins de `texRect` sont transformés par
`op.transform * op.transforms[index]`. Toute matrice affine finie est acceptée :
translation, échelle, rotation, réflexion et skew. Les positions transformées
ne sont jamais remplacées par leur rectangle englobant. Les matrices avec
perspective ne sont pas affines et produisent un refus typé pour les quatre
opérations tant qu'une route perspective indépendante n'est pas acceptée.

Les tableaux atlas ont un contrat strict :

- `transforms.size == texRects.size` ;
- `colors`, lorsqu'il est présent, possède la même taille ;
- chaque rectangle source est fini et non vide ;
- aucun sprite invalide n'est silencieusement omis ;
- la validation de tout le lot précède l'enregistrement du premier sprite.

Les couleurs par sprite, le paint optionnel, `blendMode`, le clip et l'ordre
original sont conservés. Tous les sprites d'une opération partagent la même
source image et le même sampler logique.

La composition atlas est définie en prémultiplié :

```text
sample = sampleRgba(texel)
       | colorizeA8(texel.a, paint.rgb)
sprite = colors == null
       ? sample
       : blend(mode = DrawAtlas.blendMode, src = colors[index], dst = sample)
source = applyPaintAlphaExactlyOnce(sprite, paint)
output = blendToDestination(
    mode = paint?.blendMode ?: SrcOver,
    src = source,
    dst = framebuffer
)
```

Un color filter, image filter ou autre composant de paint non encore admis par
la route préparée produit un refus typé ; il ne modifie pas silencieusement
cette formule. Chaque blend mode accepté doit utiliser l'autorité blend
commune. Un mode absent est refusé, jamais remplacé par `SrcOver`.

## Expansion image-nine et lattice

L'expansion d'une opération logique est transactionnelle : la source, les
dimensions, les cellules, l'échantillonnage, les ressources et les limites sont
validés avant toute mutation du builder.

`DrawImageNine` réutilise la décomposition 3×3 existante. Chaque cellule image
hérite du paint, du blend, du transform et du clip de l'opération. L'API
actuelle ne porte pas de sampling explicite ; son défaut normalisé FP-04 est
`Linear`. Toutes les cellules partagent un artefact d'upload, une vue et ce
sampler.

`DrawImageLattice` conserve :

- les bandes fixed/scalable ;
- l'omission des cellules transparentes ;
- les cellules `FIXED_COLOR` sous forme de payloads solid préparés, sans
  binding image ;
- la multiplication correcte de l'alpha fixed-color par l'alpha du paint ;
- le blend du caller ;
- l'override d'échantillonnage de la lattice pour les cellules image.

Une erreur dans une cellule refuse l'opération complète ; aucun préfixe de
cellules ne doit atteindre la frame.

## Plan de ressources et durée de vie

Le builder produit un plan logique explicite :

```text
snapshot pixels
  -> artefact d'upload
  -> texture
  -> texture view
  -> sampler
  -> binding image
  -> un ou plusieurs draws consommateurs
```

Le plan enregistre :

- format, taille, usage flags et limites de texture ;
- layout de copie et row pitch ;
- identité d'artefact et génération de device/target ;
- owner scope et use token ;
- slot texture et slot sampler ;
- dépendance upload-avant-premier-draw ;
- rétention jusqu'à la complétion de la soumission ;
- politique de libération déterministe.

Les clés de ressource sont séparées :

- `UploadArtifactKey` identifie les octets physiques, le format, la taille et
  la génération de la texture/view ;
- `SamplerDescriptorKey` identifie uniquement filter/address/LOD/capacités du
  sampler et permet son partage entre images compatibles ;
- `BindingKey` identifie le layout, les slots et les ressources logiques liées
  pour une consommation donnée.

Le provider actuel qui agrège texture, vue, sampler, owner et binding dans une
seule clé doit être adapté ; le partage de sampler ne peut pas rester une
assertion documentaire.

Dans FP-04, la durée garantie est celle de la soumission de frame. La route
n'invente pas de cache inter-frame. Toute promotion future de la réutilisation
texture/sampler relève des règles FP-09 et exige génération, budget,
invalidation et télémétrie explicites.

Une même frame partage :

- la texture/view pour une même identité d'artefact ;
- le sampler pour un même descripteur accepté ;
- le bind group pour une même identité de binding ;
- un seul upload par artefact avant sa première consommation.

Les ressources de cellules nine/lattice et de sprites atlas ne sont jamais
réuploadées par draw.

## Frame hétérogène et ordre d'exécution

Le builder produit une seule task list fermée dont chaque draw conserve son
index d'ordre et sa forme sémantique. Le préflight valide l'ensemble de la
frame et matérialise ses ressources globales avant l'encodage du premier draw.
Le dispatcher choisit ensuite le matérialiseur par tâche, sans regrouper ni
réordonner les draws par type.

Une transition de pipeline solid ↔ image rect ↔ image quad est permise dans le
même render pass lorsque target, clip, blend et attachment le permettent.
Lorsqu'une frontière exige un autre pass, la task list porte explicitement la
dépendance et conserve l'ordre observable. Les ressources image restent
partageables entre les tâches.

Le refus historique `mixed-semantic-shape` est retiré uniquement pour les
combinaisons dont tous les payloads sont acceptés. Une combinaison contenant
une forme inconnue ou incompatible refuse toute la frame avant allocation et
avant encodage partiel.

## Matérialisation WebGPU et WGSL

Le dispatcher de payloads obtient une route image fermée. Elle :

1. valide le contrat et les capacités avant création native ;
2. matérialise l'upload, la texture, la vue et le sampler via le provider ;
3. crée un bind group conforme à l'ABI réfléchie ;
4. sélectionne le pipeline par la clé structurelle minimale ; le WGSL courant
   partage le même pipeline rect/quad car géométrie et UV sont des uniformes ;
5. encode les draws dans l'ordre du plan ;
6. conserve les ressources jusqu'à la complétion ;
7. publie les diagnostics et compteurs d'ownership.

Le WGSL est parser-validé par wgsl4k et possède une ABI réfléchie stable. Cette
réflexion est l'unique autorité du layout groupe 0 et de l'ABI uniforme de
112 octets. Les samplers nearest et linear sont réellement distincts dans la
matérialisation native ; un label Kotlin non consommé n'est pas une preuve.

Le shader traite :

- RGBA8 prémultiplié ;
- BGRA8 converti au boundary en RGBA8 ;
- A8 développé physiquement en RGBA8 puis colorisé par le paint/tint grâce au
  fait logique `alphaOnly` ;
- alpha du paint ;
- couleur par sprite atlas ;
- UV rectangulaires ou quadrilatéraux ;
- clamp au domaine source nécessaire aux opérations image rect/nine/lattice/
  atlas ;
- blend selon l'autorité déjà préparée.

Le quad atlas peut être exécuté comme deux triangles ou comme un draw indexé
équivalent. FP-04 n'exige pas l'instancing ou le batching multi-atlas ; il exige
la géométrie affine et les UV corrects.

## Admission, refus et retrait legacy

Le produit route aujourd'hui la frame entière : une seule opération legacy
envoie aussi les opérations autrement préparables vers le renderer immédiat.
Pour éviter cette régression, FP-04 ne fait aucune admission produit partielle.
Les tranches sont testées sous la gate via leurs builders, task lists,
préflighters et matérialiseurs dédiés. La gate des images reste fermée jusqu'à
ce que les quatre opérations et les frames mixtes soient prêtes.

La bascule produit est atomique : dans le même changement, les quatre
opérations deviennent prepared-or-refused et `Images` quitte l'allowlist. Dès
cette bascule, aucun cas image non supporté ne revient à
`GPULegacyImmediatePathAdapter`.

Les refus minimaux couvrent :

- pixels absents ou snapshot impossible ;
- dimensions, row-bytes, format ou contenu invalides ;
- color space autre que SDR sRGB, profil ICC/CICP non résolu ou orientation
  non appliquée ;
- source YUV/YUVA, HDR, gainmap, codec/animation non préparée ou texture
  importée sans contrat accepté ;
- budget d'upload ou limite texture dépassés ;
- génération de device/target incompatible ;
- usage, owner, lifetime ou binding incomplet ;
- sampling cubic, anisotrope ou mipmap non accepté ;
- matrice perspective pour image, nine, lattice ou atlas ;
- longueurs atlas incohérentes ;
- géométrie nine/lattice invalide ;
- capacité native ou validation WGSL absente.

FP-04 réutilise les codes autoritatifs lorsqu'ils existent, notamment :

- `unsupported.image.pixel.format`,
  `unsupported.image.pixel.row_stride` et
  `unsupported.image.upload.budget_exceeded` ;
- `unsupported.color.gamut_transform`,
  `unsupported.color.image_profile_conversion` et
  `unsupported.image.orientation` ;
- `unsupported.color.yuv_conversion`,
  `unsupported.color.hdr_transfer` et `unsupported.color.gainmap` ;
- `unsupported.image.codec.unregistered` et
  `unsupported.image.animation.not_requested` ;
- `unsupported.texture.import_unvalidated` ;
- `unsupported.image.mip_required`,
  `unsupported.image.sampling_cubic`,
  `unsupported.image.sampling_anisotropic` et
  `unsupported.image.perspective_sampling`.

Les nouvelles erreurs propres à l'expansion logique, comme une longueur atlas
incohérente, reçoivent un code `unsupported.image.*` stable testé par valeur
exacte.

Le retrait final est atomique :

- les quatre opérations ne peuvent plus produire
  `legacy.surface.prepared.family.images` ;
- `LegacyDisplayOpFamily.Images` disparaît de l'allowlist ;
- les tests gate/router/product-entry attendent prepared ou refused, jamais
  legacy ;
- les familles Text, Vertices et Composites restent inchangées.

## Validation

La preuve FP-04 combine tests de contrats, route et pixels.

### Contrats et plan

- ordre copie défensive → validation → conversion → hash ;
- snapshot immuable et identité générationnelle ;
- RGBA8, BGRA8→RGBA8 et A8→RGBA8 avec tag alpha-only ;
- conversion couleur premul-encoded → straight-encoded explicitement hashée,
  re-premultiplication WGSL après sRGB decode, et couverture A8 non décodée ;
- SDR sRGB accepté et profils/orientations/HDR/YUV refusés explicitement ;
- hash et dump déterministes ;
- absence de pixels, handles et identité de ressource dans `PipelineKey` ;
- classification explicite de chaque axe de `PipelineKey` et absence d'axe
  uniform-only non mesuré ;
- clés distinctes upload/sampler/binding ;
- upload placé avant tous ses consommateurs ;
- partage texture/view/sampler/bind-group ;
- plusieurs draws de la même source avec artefact partagé et uniformes
  distincts ;
- une identité ABI112 unique du builder jusqu'au bind group natif ;
- refus d'une génération différente sans mutation du cache existant ;
- même code de diagnostic stable à chaque frontière ;
- absence d'upload pour les cellules lattice fixed-color ;
- rétention et libération sur succès, refus et exception ;
- refus avant allocation native.

### Route préparée

- mapper → payload → task list → préflight → dispatcher natif ;
- frames mixtes `solid + image` et alternance rect/quad sans réordonnancement ;
- lattice mixed-payload fixed-color + sampled-image ;
- sélection nearest/linear observable ;
- bind group texture/sampler conforme à l'ABI ;
- aucun snapshot CPU de destination ni réupload de continuation dans la route
  image principale ;
- aucune invocation legacy après admission ;
- diagnostics distincts pour refus before-entry et échec terminal.

### Pixels

- RGBA8 nearest et linear ;
- opaque et translucide comparés entre oracle, source sRGB, shader linéaire et
  store sRGB natif ;
- A8 tint, paint alpha et blend ;
- image-nine normale et dégénérée acceptée/refusée selon contrat ;
- lattice sampled/fixed-color/transparent ;
- transforms affines image/nine/lattice sans rectangle englobant ;
- atlas identité, translation, échelle, rotation, réflexion et skew ;
- atlas colors × texel, paint alpha, destination blend, clip et ordre ;
- comparaison CPU/référence/GPU avec diff et statistiques explicites.

Les preuves pixels doivent également enregistrer la route préparée pour éviter
qu'une sortie correcte du renderer legacy soit prise pour une réussite FP-04.

La consolidation des Tasks 1 à 5 est acceptée seulement après une review
indépendante ne laissant aucun problème bloquant ou important non attribué.
Les 289 tests ciblés établis lors de l'audit sont rerun avec les nouveaux tests
ABI, répétition de source, partage, génération, diagnostics, pipeline key et
couleur ; leur ancien résultat ne vaut pas preuve après modification.

### Validation agrégée

Après les tests ciblés, exécuter les suites image, clip, blend, Surface,
gpu-renderer et scènes pertinentes. Les validations Gradle utilisent la
toolchain JDK 25 et `--dependency-verification=off`, conformément au périmètre
de branche. Le crash natif de recréation de device/session déjà affecté à
FP-09 reste une classe d'échec distincte.

## Suivi ultérieur et non-attributions

FP-04 ne réduit pas la cible image finale. Les autorités actives permettent
seulement les attributions conditionnelles suivantes :

- FP-09 régit session, génération, réutilisation et compteurs de cache ; un
  cache image/sampler inter-frame ne peut être promu que dans ce cadre avec
  budget, invalidation et télémétrie ;
- FP-10 régit toute expansion acceptée des gaps natifs retenus ; mipmaps,
  cubic/anisotrope et activation des modes image-shader Repeat/Mirror/Decal
  sont des candidats, pas des promesses déjà ajoutées à son acceptance ;
- FP-11 régit les preuves visuelles et performance de la candidate finale.

Codecs/animations, HDR/YUV et textures importées ne sont attribués à aucun item
de la TODO FP-04…FP-11. Les specs `Draft` image, couleur, ownership et ABI
restent seulement des références techniques bornées selon la hiérarchie
ci-dessus ; elles ne planifient pas ces capacités. Les refus restent stables
jusqu'à ce qu'une future décision active les attribue. Les anciens noms de
milestone ou tickets ne doivent pas servir de date, de backlog actif ou de
promesse implicite.

## Alternatives rejetées

### Ouvrir uniquement la gate

Rejeté : le builder préparé est CorePrimitive-only et ne possède ni snapshot
pixels, ni ressource, ni sampler, ni matérialiseur image.

### Envelopper le renderer immédiat

Rejeté : cela conserverait le cache de pixels et les handles dans l'invocation
legacy, sans ownership préparé ni upload-before-sample vérifiable.

### Limiter atlas à translation/échelle

Rejeté par cette conception : FP-04 inclut un vrai quad texturé pour toute
matrice affine finie. Le rectangle englobant legacy n'est pas une
implémentation acceptable de rotation/skew.

### Généraliser immédiatement tout le pipeline image

Rejeté : codec, animation, HDR/YUV, textures importées, mipmaps et sampling
avancé ont des propriétaires et dépendances distincts. FP-04 établit la route
produit nécessaire sans produire de faux support transversal.

### Continuer directement avec la Task 6

Rejeté après audit : cela figerait deux identités de binding contradictoires,
le refus des draws répétés, la recréation des bind groups et une mutation de
génération invalide dans la future autorité de frame mixte.

### Préserver le mapping couleur courant sans preuve

Rejeté : le résultat translucide préparé déjà observé diverge de l'oracle
legacy. La correction sRGB doit précéder les preuves pixels et l'admission,
sans snapshot CPU de destination.

### Recommencer les Tasks 1 à 5

Rejeté : les contrats alpha, artefacts immuables, sémantiques handle-free,
task lists et préflight existants sont réutilisables. Une consolidation
ciblée apporte la preuve manquante sans jeter le travail valide.
