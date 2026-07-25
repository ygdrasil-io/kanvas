# Route image préparée pour `Surface` — conception FP-04

**Date :** 2026-07-25
**Statut :** approuvé pour planification

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

La route est livrée verticalement par tranches fermées :

1. contrat sémantique image sans handle ;
2. plan de ressource et préflight ;
3. matérialiseur WebGPU/WGSL natif ;
4. admission de `DrawImage` ;
5. expansion transactionnelle de `DrawImageNine` puis
   `DrawImageLattice` ;
6. payload de quad texturé et migration affine complète de `DrawAtlas` ;
7. retrait de la famille legacy `Images`.

La route immédiate existante sert de source de comportement et de contre-preuve
pour les défauts connus. Elle n'est ni appelée depuis la frame préparée, ni
enveloppée dans un nouveau nom, ni utilisée comme fallback.

## Autorités et frontières d'architecture

La conception respecte :

- `.upstream/target/high-performance-wgsl-pipeline-target.md` ;
- `.upstream/target/skia-like-realtime-renderer-target.md` ;
- `.upstream/specs/skia-like-realtime/README.md` ;
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md` ;
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md` ;
- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md` ;
- `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`.

Les invariants sont :

- WebGPU reste le backend GPU produit ;
- le CPU fournit une référence et peut préparer des pixels sources, mais ne
  rasterise pas un draw, un layer ou une scène non supportée dans une texture
  de compatibilité ;
- le plan de frame reste sans handle natif ;
- les handles sont créés tardivement par le provider de ressources ;
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
- format logique accepté : RGBA8 prémultiplié ou A8 alpha-only ;
- row-bytes validé et taille de contenu bornée ;
- snapshot immuable des octets nécessaires à l'upload ;
- hash de contenu, génération et provenance ;
- origine, swizzle et politique alpha explicites ;
- sous-rectangle source et domaine d'échantillonnage ;
- classe d'échantillonnage `Nearest` ou `Linear` ;
- transform, clip, paint, blend et ordre de draw capturés ;
- clé d'artefact d'upload distincte de toute clé de pipeline.

Le snapshot doit isoler la frame de toute mutation ultérieure de l'objet
`Image`. Deux commandes qui désignent exactement le même contenu et la même
génération peuvent partager un artefact d'upload. Un changement de contenu,
format, dimensions, row-bytes ou génération force une identité différente.

Les sources encodées peuvent alimenter ce contrat seulement après un décodage
accepté par le propriétaire codec. FP-04 ne choisit pas un codec et ne masque
pas une dépendance de décodage absente.

## Sémantiques de draw

La somme fermée des payloads préparés gagne deux formes image :

### Rectangle image échantillonné

Le payload porte quatre positions de destination rectangulaire, le domaine UV,
le binding logique de la source, l'échantillonnage, le paint/blend et les faits
de clip. Il sert à `DrawImage` et aux cellules image de nine/lattice.

### Quad image échantillonné

Le payload porte quatre positions de destination indépendantes, quatre
coordonnées UV et deux triangles à winding déterministe. Il sert à
`DrawAtlas`.

Pour chaque sprite atlas, les quatre coins de `texRect` sont transformés par
`op.transform * op.transforms[index]`. Toute matrice affine finie est acceptée :
translation, échelle, rotation, réflexion et skew. Les positions transformées
ne sont jamais remplacées par leur rectangle englobant. Les matrices avec
perspective ne sont pas affines et produisent un refus typé tant qu'une route
perspective indépendante n'est pas acceptée.

Les tableaux atlas ont un contrat strict :

- `transforms.size == texRects.size` ;
- `colors`, lorsqu'il est présent, possède la même taille ;
- chaque rectangle source est fini et non vide ;
- aucun sprite invalide n'est silencieusement omis ;
- la validation de tout le lot précède l'enregistrement du premier sprite.

Les couleurs par sprite, le paint optionnel, `blendMode`, le clip et l'ordre
original sont conservés. Tous les sprites d'une opération partagent la même
source image et le même sampler logique.

## Expansion image-nine et lattice

L'expansion d'une opération logique est transactionnelle : la source, les
dimensions, les cellules, l'échantillonnage, les ressources et les limites sont
validés avant toute mutation du builder.

`DrawImageNine` réutilise la décomposition 3×3 existante. Chaque cellule image
hérite du paint, du blend, du transform, du clip et du sampler de l'opération.
Toutes les cellules partagent un artefact d'upload et une vue.

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

Dans FP-04, la durée garantie est celle de la soumission de frame. La route
n'invente pas de cache inter-frame. FP-09 pourra réutiliser la texture et le
sampler seulement avec une génération, un budget, une invalidation et une
télémétrie explicites.

Une même frame partage :

- la texture/view pour une même identité d'artefact ;
- le sampler pour un même descripteur accepté ;
- un seul upload par artefact avant sa première consommation.

Les ressources de cellules nine/lattice et de sprites atlas ne sont jamais
réuploadées par draw.

## Matérialisation WebGPU et WGSL

Le dispatcher de payloads obtient une route image fermée. Elle :

1. valide le contrat et les capacités avant création native ;
2. matérialise l'upload, la texture, la vue et le sampler via le provider ;
3. crée un bind group conforme à l'ABI réfléchie ;
4. sélectionne le pipeline rect ou quad ;
5. encode les draws dans l'ordre du plan ;
6. conserve les ressources jusqu'à la complétion ;
7. publie les diagnostics et compteurs d'ownership.

Le WGSL est parser-validé par wgsl4k et possède une ABI réfléchie stable. Les
samplers nearest et linear sont réellement distincts dans la matérialisation
native ; un label Kotlin non consommé n'est pas une preuve.

Le shader traite :

- RGBA8 prémultiplié ;
- A8 alpha-only colorisé par le paint/tint ;
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

L'admission progresse seulement après preuve de la tranche correspondante.
Pendant le développement, la gate peut rester fermée pour les tranches non
achevées. Une fois une opération admise, ses cas non supportés ne reviennent
jamais à `GPULegacyImmediatePathAdapter` : ils produisent une issue préparée
stable.

Les refus minimaux couvrent :

- pixels absents ou snapshot impossible ;
- dimensions, row-bytes, format ou contenu invalides ;
- budget d'upload ou limite texture dépassés ;
- génération de device/target incompatible ;
- usage, owner, lifetime ou binding incomplet ;
- sampling cubic, anisotrope ou mipmap non accepté ;
- matrice perspective atlas ;
- longueurs atlas incohérentes ;
- géométrie nine/lattice invalide ;
- capacité native ou validation WGSL absente.

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

- snapshot immuable et identité générationnelle ;
- hash et dump déterministes ;
- absence de pixels, handles et identité de ressource dans `PipelineKey` ;
- upload placé avant tous ses consommateurs ;
- partage texture/view/sampler ;
- absence d'upload pour les cellules lattice fixed-color ;
- rétention et libération sur succès, refus et exception ;
- refus avant allocation native.

### Route préparée

- mapper → payload → task list → préflight → dispatcher natif ;
- sélection nearest/linear observable ;
- bind group texture/sampler conforme à l'ABI ;
- aucune invocation legacy après admission ;
- diagnostics distincts pour refus before-entry et échec terminal.

### Pixels

- RGBA8 nearest et linear ;
- A8 tint, paint alpha et blend ;
- image-nine normale et dégénérée acceptée/refusée selon contrat ;
- lattice sampled/fixed-color/transparent ;
- atlas identité, translation, échelle, rotation, réflexion et skew ;
- atlas colors + blend, clip et ordre ;
- comparaison CPU/référence/GPU avec diff et statistiques explicites.

Les preuves pixels doivent également enregistrer la route préparée pour éviter
qu'une sortie correcte du renderer legacy soit prise pour une réussite FP-04.

### Validation agrégée

Après les tests ciblés, exécuter les suites image, clip, blend, Surface,
gpu-renderer et scènes pertinentes. Les validations Gradle utilisent la
toolchain JDK 25 et `--dependency-verification=off`, conformément au périmètre
de branche. Le crash natif de recréation de device/session déjà affecté à
FP-09 reste une classe d'échec distincte.

## Travail ultérieur explicitement attribué

FP-04 ne réduit pas la cible image finale. Les extensions sont réparties ainsi :

- FP-09 : session réutilisable et cache image/sampler inter-frame borné ;
- FP-10 : mipmaps, cubic/anisotrope et activation des modes image-shader
  Repeat/Mirror/Decal non consommés par FP-04 ;
- FP-11 : preuves visuelles et performance de la candidate finale ;
- pipeline codec/image : décodage et animation selon les dépendances réelles ;
- M35/M36 : activation HDR/YUV après preuves produit ;
- future spécification de façade : textures importées et synchronisation
  externe.

Les codecs/animations, HDR/YUV et textures importées ne possèdent pas
actuellement d'échéance dans la TODO FP-04…FP-11. Cette absence doit rester
visible ; elle ne doit pas être transformée en promesse implicite.

## Alternatives rejetées

### Ouvrir uniquement la gate

Rejeté : le builder préparé est CorePrimitive-only et ne possède ni snapshot
pixels, ni ressource, ni sampler, ni matérialiseur image.

### Envelopper le renderer immédiat

Rejeté : cela conserverait le cache de pixels et les handles dans l'invocation
legacy, sans ownership préparé ni upload-before-sample vérifiable.

### Limiter atlas à translation/échelle

Rejeté après décision utilisateur : FP-04 inclut un vrai quad texturé pour toute
matrice affine finie. Le rectangle englobant legacy n'est pas une
implémentation acceptable de rotation/skew.

### Généraliser immédiatement tout le pipeline image

Rejeté : codec, animation, HDR/YUV, textures importées, mipmaps et sampling
avancé ont des propriétaires et dépendances distincts. FP-04 établit la route
produit nécessaire sans produire de faux support transversal.
