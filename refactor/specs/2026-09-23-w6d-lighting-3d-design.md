# W6d — éclairage 3D sans API 2D transitoire

Date : 2026-09-23

Branche : `codex/w6d-advanced-effects`

Portée : amendement à `2026-09-16-w6-layers-effects-design.md`, section 10.4,
et à la tâche 3 du plan W6d. Les tâches W6d 1 et 2 sont déjà implémentées.

## Intention et décision

W6d doit exécuter les six familles d'éclairage Skia — distant, point, spot,
chacune en diffuse et specular — sur la route W6 plan-first, avec des pixels
vérifiables par des oracles indépendants. Le projet est en incubation : les
breaking changes source sont autorisés. Conserver les paramètres 2D actuels
obligerait à inventer leur coordonnée Z et empêcherait la convergence Skia.

Les six filtres publics utilisent donc **exclusivement** des paramètres 3D.
Les classes et leurs autres paramètres restent reconnaissables, mais aucune
surcharge 2D, valeur Z par défaut ou conversion implicite 2D→3D n'est ajoutée.
Les appels source existants en `Point2F32` ou `Vector2F32` doivent être migrés.
Cette décision prime sur toute suggestion de compatibilité 2D du plan W6d.

Approches écartées : conserver les constructeurs 2D en les refusant à
l'admission ajouterait une API non fonctionnelle ; leur attribuer Z=0 ou Z=1
produirait silencieusement des pixels possiblement faux. Le retrait source
net est plus lisible et plus sûr pendant l'incubation.

## Contrats et propriétaires

- `:math` possède les types existants `Vector3F32` et `Point3F32`. Aucun
  nouvel objet géométrique n'est défini dans `:kanvas`, `:render-ir`,
  `:gpu-plan` ou `:gpu-renderer`. Les calculs de mapping/bounds qui exigent
  une précision accrue utilisent la nomenclature F64 existante.
- `ImageFilter.DistantLit{Diffuse,Specular}.direction` devient
  `Vector3F32`. `PointLit{Diffuse,Specular}.location`, ainsi que
  `SpotLit{Diffuse,Specular}.location` et `.target`, deviennent `Point3F32`.
  Les autres champs et l'input optionnel gardent leur rôle.
- Les deux représentations `ImageFilterNode` et `CapturedFilterNodeV1`
  transportent les trois composantes, sans les aplatir en XY lors de la
  capture ou du replay. Leurs identités canoniques incluent X, Y et Z, avec
  une version d'identité nouvelle pour les six familles. Une différence de
  Z ne peut jamais partager un ID, une occurrence ou un cache de résultat.
- `:gpu-plan` transforme les paramètres dans l'espace du layer, valide les
  valeurs et fige famille, paramètres, programme, inputs, target, bounds,
  ressources et budget avant publication. `:gpu-renderer` ne choisit ni
  variante ni valeur Z : il matérialise et exécute le `FilterPass` gelé.

## Sémantique d'éclairage

La révision Skia de référence utilise des positions/directions 3D. W6d
n'admet ici qu'un mapping **affine, fini et inversible** de l'espace des
paramètres vers celui du layer, déjà gelé par W6 ; une perspective ou un
mapping non représentable est refusé avant publication avec un diagnostic
de capacité précis, sans approximation affine tardive. Pour sa partie
linéaire `A = [[sx,kx],[ky,sy]]` et sa translation `t`, une location ou
target `(x,y,z)` devient `(A·(x,y)+t, mapZ(z))` ; une direction devient
`(A·(x,y), mapZ(z))`, sans translation. La règle Skia exacte est
`mapZ(z) = ((A·(z,z)).x + (A·(z,z)).y) / 2`. Elle s'applique **aussi**
à `surfaceScale` avant de calculer hauteur et normales ; Z n'est pas
simplement multiplié par une moyenne des longueurs d'axes. Les résultats
doivent être finis et représentables dans les uniforms gelés.

La hauteur de surface est `alpha * mapZ(surfaceScale)` ; la normale est
calculée par Sobel sur l'alpha en texels de layer. Le plan demande un halo
d'entrée d'un pixel autour de `desiredOutput`. Pour chaque bord
gauche/haut/droit/bas séparément, si le bord réel du child output coïncide
avec celui de `desiredOutput`, l'échantillonnage hors child est `clamp` à ce
bord ; sinon le bord reste celui de `requiredInput` et les texels hors child
suivent `decal` (transparent). L'oracle et le programme gelé utilisent la
même sélection par bord, sans échantillonner l'attachment active.

Pour un spot, la direction est `target - location` **après** le mapping 3D ;
`cutoffAngle` est exprimé en degrés, transformé en cosinus pour le programme.
Diffuse, specular, falloff, couleur et prémultiplication suivent les six
branches de la référence épinglée, sans conversion colorimétrique cachée.

Références primaires :
[SkLightingImageFilter.cpp](https://skia.googlesource.com/skia/+/263308ea4386/src/effects/imagefilters/SkLightingImageFilter.cpp)
et [SkKnownRuntimeEffects.cpp](https://skia.googlesource.com/skia/+/93912d50850d/src/core/SkKnownRuntimeEffects.cpp).

Le filtre affecte le noir transparent : ses `producedOutputBounds` sont
non bornées avant intersection avec `desiredOutput`, le clip, un Crop
explicite, le target et les budgets. Les seules bounds du contenu source
ne peuvent pas limiter la sortie. `requiredInput` inclut le halo Sobel.

L'admission des six familles suit ce domaine, vérifié avant publication :

| Paramètre | Domaine |
| --- | --- |
| X/Y/Z des locations, targets et directions ; `surfaceScale` | F32 finis ; `surfaceScale` peut être négatif |
| `kd` ou `ks` | F32 fini et ≥ 0 |
| `shininess` specular ; `specularExponent` spot | F32 finis, sans ancienne limite `[1,128]` |
| `cutoffAngle` spot | F32 fini en degrés ; cosinus calculé fini dans `[-1,1]` |
| Paramètres après mapping, halos, ressources et uniforms | finis, représentables et dans les budgets W6 |

Les restrictions legacy de `GPULighting.kt` sur `surfaceScale < 0` et les
exposants hors `[1,128]` ne deviennent pas une seconde admission W6d.
Une géométrie impossible, une capacité absente ou un budget insuffisant
refusent avant publication, avec préservation du readback et récupération
sur la même `Surface`.

Skia ne promet pas de résultat portable pour toutes les expressions GPU
dégénérées. W6d fixe ici une convention **Kanvas**, limitée à ces entrées :
une direction distante nulle, un spot avec `target == location`, une lumière
ponctuelle située exactement sur le point de surface, ou un demi-vecteur
specular nul donnent une contribution lumineuse nulle. Un `pow` de base
négative, ou de base zéro avec exposant négatif, donne également une
contribution nulle ; `pow(0,0)` vaut 1. Après cette convention, le diffuse
émet un noir opaque et le specular un noir transparent lorsque la
contribution est nulle. Tout autre intermédiaire non fini donne aussi une
contribution nulle ; aucun NaN ne passe au readback. Cette convention
est une divergence bornée, **pas** une revendication ISO sur ces cas.

## Picture et compatibilité binaire

Le prochain writer émet `Picture` version 15 / schéma IR 9. Dans les deux
formes sérialisées existantes — table de nœuds capturés et arbre récursif —
les tags 9 à 14 encodent désormais les trois composantes de chaque direction,
location et target. Le lecteur branche sur le schéma avant de décoder ces
tags : il ne peut pas lire deux floats anciens comme un triplet nouveau.

Les versions antérieures restent décodables lorsqu'elles ne contiennent pas
d'éclairage 2D. Un ancien tag d'éclairage 2D, y compris dans le lecteur
historique de `Picture`, échoue proprement et précisément
(`unsupported-2d-lighting` côté codec IR, échec de décodage côté façade
publique), sans ajout de Z arbitraire ni publication partielle. Les autres
filtres et le partage explicite du DAG gardent leurs règles de lecture.

## Preuves et limites

La tâche 3 W6d sera amendée avant reprise de l'implémentation. Ses tests
publics couvriront les six familles avec un oracle CPU indépendant, deux
lumières ayant les mêmes XY mais des Z différents, alpha plat et variations
de hauteur, une échelle non uniforme avec origine décalée et `surfaceScale`
mappé, les deux règles de bord Sobel (child bordant `desiredOutput` et child
strictement intérieur), un éclairage visible sur noir transparent, spot
cutoff, cas dégénérés ci-dessus, sorties prémultipliées, acceptation de
`surfaceScale` négatif et d'exposants hors `[1,128]`, refus de `kd/ks`
négatifs et des valeurs non finies, sentinel et récupération. La
préservation `Picture` vérifiera aller-retour mémoire/wire version 15,
rejet d'un ancien éclairage 2D et lecture d'une ancienne scène sans
éclairage. Aucun test privé,
reflection, fake device, mock backend ou test statique d'infrastructure ne
sera ajouté.

Fonts, codecs externes, GMs, dashboard, renders, scores, suite Skia globale
et retrait des routes legacy W8 restent hors périmètre. Un exit natif 133/134
reste `UNKNOWN` même si les assertions XML sont satisfaites ; ce document
ne revendique ni ISO ni convergence globale.
