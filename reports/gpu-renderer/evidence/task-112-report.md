# W136 — RadialGradient avec matrice locale bornée

## Périmètre

Cette vague étend la route native `CorePrimitive` aux `RadialGradient` clamp à
deux stops portant une matrice locale limitée à l'identité ou à une échelle
uniforme positive avec translation. Le cas prouvé est un stroke mono-segment
diagonal `SQUARE + MITER`, sans AA, sous un clip triangle Winding.

## Changements vérifiés

- le mapper Kanvas conserve les matrices radiales bornées et refuse les
  rotations, skew, perspectives et échelles non uniformes ;
- l'analyse et le semantic builder admettent cette matrice uniquement sur le
  lane radial de stroke avec clip stencil 1x et CTM identité ;
- le packet builder natif utilise la même frontière bornée ;
- un test négatif conserve le refus stable avec
  `unsupported.material.mapping.local_matrix`.

## Preuve native

- route : `native.path_stroke.stencil_cover` ;
- clip Winding : producteur `IncrementWrap` / `DecrementWrap`, consumer
  `NotEqual` ;
- préparation : `Recorded` ;
- exécution : `Succeeded` ;
- un submit et une readback copy ;
- oracle CPU indépendant : centre des pixels, géométrie square de 2 px,
  inclusion barycentrique du triangle, puis distance radiale après la
  translation locale ; interpolation linear-light, encodage sRGB et tolérance
  d'un LSB.

## Frontière contractuelle

Les matrices locales `rotation(90)` et `scaling(2, 1)` restent refusées avant
la préparation native. Les transformations du draw (`CTM`) non identité ne
sont pas ajoutées à cette vague : le mapping device du centre et du rayon
reste à prouver séparément.

## Limites

Pas de perspective, skew, échelle non uniforme, rotation, autres tile modes,
plus de deux stops, caps round, chemins multi-segments, AA multi-sample ou
mapping radial sous CTM transformée.
