# W5e — Images décodées dans le material graph

Date : 2026-09-12  
Statut : conception validée, en attente de review documentaire  
Branche : `codex/w5e-decoded-images`  
Base stackée : `codex/w5d-gradient-addressing`

## 1. Décision

W5e promeut les images déjà décodées dans le chemin plan-first du material
graph. Le `MaterialPlan` devient l'unique autorité pour les coordonnées image,
le sampling, les tile modes, la couleur, l'alpha et les refus.

L'implémentation suit une migration convergente : elle réutilise et refactore
la plomberie GPU existante pour les uploads, les ressources natives, le cache
et leur ownership, mais ne conserve aucune décision sémantique dans le chemin
`prepared image` historique.

La cible est :

```text
API publique
  -> DisplayOp / Scene IR v2
  -> MaterialNode.ImageSample
  -> MaterialPlan
  -> ImageSampleExecutionPlan scellé et handle-free
  -> matérialisation de ressources GPU
  -> WebGPU
```

Pour les lanes promues par W5e, aucun fallback vers le lowerer image historique
n'est autorisé après la sélection du nouveau chemin.

## 2. Objectifs

W5e doit rendre fonctionnels, via le chemin plan-first :

- les pixels décodés `RGBA_8888`, `BGRA_8888`, `SRGBA_8888` et `ALPHA_8` ;
- les samplings `Nearest`, `Linear` et `Cubic(B, C)` ;
- `CLAMP`, `REPEAT`, `MIRROR` et `DECAL`, indépendamment sur X et Y ;
- `DrawImage`, `DrawImageNine`, `DrawImageLattice` et `DrawAtlas` ;
- un shader image sur `Rect` et `Path fill` ;
- la sérialisation fidèle de ces informations dans `Picture` et
  `SceneArchiveCodec` ;
- une sélection et des refus déterministes avant la création de handles natifs.

W5e doit aussi éliminer le transport implicite du sampling via le shader du
`Paint` et retirer les branches sémantiques historiques remplacées.

## 3. Hors périmètre

Sont explicitement différés :

- la détection, le décodage ou l'encodage de PNG, JPEG, WebP, GIF, BMP ou de
  tout autre format externe ;
- `ExternalImageReference`, tant qu'un propriétaire et une completion native
  ne sont pas définis ;
- les formats de pixels autres que les quatre formats promus ;
- les mipmaps et le filtrage anisotrope ;
- les shaders image sur `RRect`, `Path stroke`, `Point(s)`, `Text` et
  `Vertices/Mesh`, qui restent des lanes W5h ;
- toute nouvelle géométrie définie en dehors de `:math:geometry` ;
- les tests d'infrastructure visant directement caches, artifacts, lowerers ou
  handles GPU.

Les polices et les codecs image externes ne constituent pas des dépendances de
sortie de W5e.

## 4. Autorité architecturale

### 4.1 Frontière sémantique

Le frontend capture une scène immuable. Le planner transforme cette scène en
un graphe matériel et scelle toutes les décisions observables avant le
backend. Le backend reçoit un contrat exécutable ; il ne choisit ni sampling,
ni tile mode, ni interprétation colorimétrique.

`ImageSampleExecutionPlan` contient au minimum :

- l'identité canonique de l'image et ses dimensions ;
- son format logique, son alpha type et son `ColorSpace` ;
- la projection des coordonnées locales vers les coordonnées image ;
- les deux tile modes ;
- le sampling et, pour Cubic, les paramètres `B` et `C` ;
- la géométrie ou les cellules décomposées ;
- le programme matériel sélectionné ;
- les besoins mémoire checked et les faits nécessaires au preflight.

Ce contrat est immutable, handle-free et sérialisable sous forme de dump
stable pour les diagnostics.

### 4.2 Réutilisation contrôlée de l'existant

La chaîne existante peut être refactorée et réutilisée pour :

- construire un upload aligné ;
- ajouter et initialiser à zéro le padding WebGPU ;
- planifier les buffers, textures, views et bind groups ;
- vérifier la génération et l'ownership du device ;
- mettre en cache les ressources natives ;
- libérer les ressources de manière déterministe.

Elle ne peut plus :

- lire le sampling depuis `Paint.shader` ;
- décider d'un tile mode ;
- choisir une conversion couleur ;
- classifier une opération comme supportée ou refusée ;
- rediriger une opération promue vers une autre route de rendu.

Les contrats physiques réutilisés sont rendus neutres lorsque leur API ou leur
nom maintient une dépendance sémantique au chemin `prepared image`.

## 5. Capture publique et Scene IR v2

### 5.1 Sampling explicite

`DisplayOp.DrawImage` reçoit :

```kotlin
sampling: SamplingOptions = SamplingOptions.NEAREST
```

Les overloads publics sans sampling conservent ce défaut. L'overload explicite
enregistre la valeur directement. Il est interdit de fabriquer
`paint.copy(shader = image.makeShader(sampling))` pour transporter ce choix.

Le shader éventuel du `Paint` est snapshoté séparément, car il participe à la
colorisation d'une image A8.

### 5.2 Géométries image

- `GeometryNode.ImagePatch` reçoit `sampling: ImageSampling` et son identité
  devient `geometry-image-patch-v2`.
- `GeometryNode.ImageNine` devient un nœud distinct avec `image`,
  `center: RectF32`, `destination: RectF32` et `sampling = Nearest`.
- `GeometryNode.ImageLattice` conserve son sampling explicite.
- `GeometryNode.Atlas` reste `Nearest` tant que l'API publique n'expose pas de
  sampling.

`ImageNine` est décomposé en neuf couples source/destination avant la création
des samples. Il ne peut jamais être interprété comme un `ImagePatch` étirant le
rectangle central sur toute la destination.

Les rectangles, points, matrices et autres objets géométriques restent dans
`:math:geometry` et suivent la nomenclature explicite `I32`/`I64` et
`F32`/`F64` selon leur domaine.

### 5.3 Compatibilité des codecs internes

`SceneDisplayOpAdapter`, `DisplayOpSceneAdapter`, `Picture`,
`SceneArchiveCodec` et leurs witnesses sont versionnés atomiquement.

- un ancien payload sans sampling est lu comme `Nearest` ;
- un nouveau payload conserve `Nearest`, `Linear` ou Cubic ;
- les bits de `B` et `C` sont préservés lors d'un round-trip ;
- l'identité canonique distingue toutes les variations sémantiques ;
- aucun changement ne concerne le décodage d'un fichier image externe.

## 6. Pixels, layout et identité

W5e consomme seulement `ImageResourceSnapshot.Pixels`.

| Format public | Format IR | Interprétation W5e |
| --- | --- | --- |
| `RGBA_8888` | `RGBA_8888` | ordre R, G, B, A |
| `BGRA_8888` | `BGRA_8888` | swizzle B/R explicite |
| `SRGBA_8888` | `SRGBA_8888` | transfert sRGB explicite |
| `ALPHA_8` | `ALPHA_8` | masque de couverture |

Les dimensions, `rowBytes`, la taille logique d'une ligne et la taille totale
sont calculées avec des opérations I64 checked. Sont refusés avant allocation :

- dimension nulle, négative ou non représentable ;
- `rowBytes` inférieur à la taille logique ;
- multiplication ou addition overflow ;
- payload trop court ;
- budget ou limite GPU dépassé.

Chaque ligne copie uniquement ses octets logiques. Le padding du producteur
n'entre ni dans le contenu uploadé, ni dans son hash. Le padding WebGPU imposé
par `copyBytesPerRowAlignment` est créé séparément, rempli de zéro et reste
hors du domaine du sampler.

Le stockage physique est normalisé en `RGBA8_UNORM` pour les images couleur et
`R8_UNORM` pour A8. Une texture sRGB automatique n'est pas utilisée : swizzle,
transfert et alpha restent des opérations explicites appliquées exactement une
fois.

L'identité d'upload repose sur le snapshot immuable, les dimensions, le layout
logique, le format et le contenu. Elle ne dépend jamais de l'identité objet de
`Image`. Le sampling et les tile modes ne font pas partie de la clé d'upload,
afin qu'une texture soit partageable entre plusieurs évaluations.

## 7. Couleur et alpha

La valeur produite par un sample couleur est un RGBA linéaire prémultiplié.

Pour `RGBA_8888`, `BGRA_8888` et `SRGBA_8888` :

- `OPAQUE` force `a = 1` et ignore l'octet alpha ;
- `UNPREMUL` convertit le RGB straight source vers l'espace linéaire, puis le
  prémultiplie par `a` ;
- `PREMUL` déprémultiplie d'abord dans l'espace source, convertit le RGB, puis
  le prémultiplie à nouveau ;
- `a = 0` produit RGB zéro sans division non finie.

`SRGBA_8888` impose une metadata `ColorSpace` sRGB cohérente. Les combinaisons
contradictoires sont refusées. Display P3 et Linear sRGB restent exprimables
avec `RGBA_8888` et leur `ColorSpace` explicite.

Pour `ALPHA_8` :

- `mask = byte / 255` ;
- `OPAQUE` force `mask = 1` ;
- `PREMUL` et `UNPREMUL` sont équivalents ;
- le `ColorSpace` ne transforme jamais le canal alpha.

La composition source respecte les équations du material graph :

- draw d'une image RGBA : `sample(image) * paint.alpha` ;
- draw d'une image A8 sans shader : `mask * linearPremul(paint.color)` ;
- draw d'une image A8 avec shader :
  `mask * scaleAlpha(eval(paint.shader), paint.alpha)`.

Une image RGBA ignore le RGB du `Paint`. Le color filter, le blend de
l'opération, le blend du draw et la coverage sont appliqués ensuite, une seule
fois, dans l'ordre défini par le material graph global.

## 8. Coordonnées et décomposition

Pour un `ImagePatch(src, dst)` et un point local `P` :

```text
u = (P.x - dst.left) / dst.width
v = (P.y - dst.top)  / dst.height
s.x = src.left + u * src.width
s.y = src.top  + v * src.height
```

Une largeur ou hauteur source/destination nulle ou non finie est refusée. Une
dimension négative conserve le flip. Le tile domain est l'image entière après
projection du source rect.

`ImageNine` et `ImageLattice` calculent chaque cellule avant de produire leur
couple source/destination. `Atlas` conserve chaque transform et chaque source
rect ; il ne fusionne pas les entrées en une bounding box.

Le transform et le local matrix sont composés une seule fois par le planner.
Le backend ne reconstruit pas une convention de coordonnées concurrente.

Les opérations image directes utilisent `CLAMP` sur les deux axes. Un shader
image conserve les deux tile modes déclarés par son API. Le tile domain reste
l'image entière : un source rect change la projection, pas le domaine
d'adressage.

## 9. Sampling et tile modes

Les coordonnées image utilisent des centres de pixels `i + 0.5`.

Pour une coordonnée scalaire `s`, avec `u = s - 0.5` :

- `Nearest` sélectionne `floor(s)` ;
- `Linear` sélectionne `floor(u)` et `floor(u) + 1` sur chaque axe ;
- `Cubic` sélectionne `floor(u) - 1` à `floor(u) + 2` sur chaque axe.

Cubic utilise le filtre bicubic Mitchell–Netravali paramétré par `B` et `C`.
Les deux paramètres doivent être finis et appartenir à `[0, 1]`. Ils sont
capturés et identifiés bit pour bit.

Chaque index de chaque tap est adressé avant le fetch :

- `CLAMP` borne l'index dans `[0, n - 1]` ;
- `REPEAT` utilise `floorMod(i, n)` ;
- `MIRROR` utilise une période `2n` ;
- `DECAL` produit transparent si l'index est hors du domaine.

Les taps DECAL transparents participent au poids normal du filtre. Les poids
restants ne sont pas renormalisés.

L'évaluation est explicite dans le shader : un fetch pour Nearest, quatre pour
Linear et seize pour Cubic. Elle ne délègue pas les frontières à un sampler
matériel, ce qui garantit les mêmes règles sur les backends supportés.

## 10. Ressources et exécution

Le planner scelle le budget et les ressources nécessaires sans créer de handle
natif. Les allocations sont matérialisées seulement après succès du preflight.

Le cache distingue :

- l'upload, indexé par contenu et description physique ;
- la texture/view, indexée aussi par la génération du device ;
- le programme et le pipeline, indexés par l'identité du material program et
  les faits structurels du target ;
- les uniforms ou paramètres d'évaluation, qui ne polluent pas les clés de
  pipeline lorsqu'ils ne changent pas sa topologie.

La même image peut donc être utilisée avec plusieurs sampling/tile modes sans
upload dupliqué. Deux images distinctes au contenu canonique identique peuvent
partager le même upload.

La validation d'ownership, de génération, de target et de lifetime précède la
matérialisation. Une erreur native postérieure est terminale et produit un
diagnostic ; elle ne déclenche pas de fallback.

Tous les leases, bindings et objets temporaires acquis par une tentative sont
libérés à sa fin, y compris en cas de refus ou d'exception. Une ressource mise
en cache peut survivre uniquement sous l'ownership et la politique de lifetime
du resource provider. Aucun handle natif n'est retenu dans le Scene IR ou le
MaterialPlan.

## 11. Refus typés

Les familles minimales de refus sont :

- `unsupported.material.image.external_resource` ;
- format pixel non promu ;
- incohérence format / `ColorSpace` ;
- alpha type non interprétable ;
- layout, stride ou payload invalide ;
- dimensions, taille ou budget overflow ;
- paramètres cubic non finis ou hors domaine ;
- couple origin / géométrie IR incohérent ;
- limite de texture, binding ou feature GPU insuffisante ;
- génération ou ownership natif incompatible.

Chaque refus contient des faits stables suffisants pour identifier l'étape et
la propriété fautive, sans exposer un handle ou une identité mémoire.

## 12. Validation publique

Les gates entrent par les API publiques et vérifient pixels, statistiques de
rendu ou diagnostics. Elles couvrent :

- les quatre formats promus ;
- `OPAQUE`, `PREMUL`, `UNPREMUL` et le cas alpha zéro ;
- mutation de la source après capture ;
- padding source et padding WebGPU ;
- Nearest, Linear et plusieurs Cubic ;
- les quatre tile modes sur X et Y, notamment aux frontières ;
- transforms, flips, source rects et local matrices ;
- `DrawImage`, `ImageNine`, `ImageLattice` et `Atlas` ;
- shader image sur `Rect` et `Path fill` ;
- sémantique couleur RGBA et colorisation A8 ;
- lecture des anciens payloads et round-trip des nouveaux ;
- chaque famille de refus observable.

Les assertions sont exactes lorsque l'oracle est discret. Une tolérance
documentée est utilisée uniquement lorsque la conversion ou l'interpolation
flottante l'exige.

Les GMs image ciblés complètent la validation publique. Les formats externes,
`jpg-color-cube` et les autres exclusions déjà actées ne conditionnent pas la
sortie de W5e.

Aucun nouveau test d'infrastructure ne cible directement un cache, un artifact,
un lowerer, un resource provider ou un handle GPU.

## 13. Livraison stackée

W5e est livré dans une PR unique basée sur `codex/w5d-gradient-addressing`, avec
les slices séquentielles suivantes :

1. contrats publics, Scene IR v2 et codecs internes ;
2. snapshot, formats, layout, couleur et alpha ;
3. plan scellé, coordonnées, sampling et tile modes ;
4. `DrawImage`, `ImageNine`, `ImageLattice` et `Atlas` ;
5. shader image sur `Rect` et `Path fill` ;
6. suppression des autorités legacy, gates publiques et documentation.

L'exécution emploie Subagent-Driven Development. Les agents d'implémentation
sont choisis selon la tâche ; Sol est réservé aux reviews indépendantes. Une
slice est intégrée seulement après conformité à la spec, review de qualité et
vérification publique proportionnée à son risque.

## 14. Critères de sortie

W5e est terminé lorsque :

- toutes les lanes déclarées W5e passent exclusivement par le material graph ;
- aucun sampling n'est transporté dans `Paint.shader` ;
- le backend ne décide plus des règles image observables ;
- les formats, alpha types, sampling et tile modes promus passent les gates ;
- les codecs internes préservent la nouvelle sémantique ;
- les ressources sont budgétées avant matérialisation et libérées sans fuite ;
- aucun fallback legacy n'est possible après sélection ;
- les suppressions et renommages nécessaires à une autorité unique sont faits ;
- la documentation W5 et la matrice des lanes différées sont à jour.

Les limitations restantes doivent être soit explicitement affectées à W5h,
soit documentées comme gap d'intégration Skia non bloquant avec un diagnostic
stable.
