# Task 4 — PNG/JPEG canonical raster contracts

## Résultat

- Les codecs et encodeurs PNG/JPEG utilisent désormais `ImageInfo`, `Bitmap`
  et `Pixmap` Kanvas. Les tests PNG/JPEG existants utilisent les mêmes types.
- Les destinations de décodage ne permettent que `RGBA_8888` et
  `RGBA_F16_NORM`, avec validation de la géométrie, du format, de l’alpha et
  de l’identité de `ImageColorSpace`.
- Les parcours F16 utilisent les accès `getPremulRgbaF16` et
  `setPremulRgbaF16`. Les copies depuis `Pixmap` passent par ses coordonnées,
  et respectent donc son `rowBytes`.
- `PixmapUtils` est déplacé vers `org.graphiks.kanvas.codec`, où ses APIs sont
  `orient(Bitmap, Bitmap, EncodedOrigin)` et `swapWidthHeight(ImageInfo)`.
  Les chemins JPEG conservent le profil ICC et l’orientation EXIF.

## Correctif de revue

- Le chemin JPEG hierarchy classe désormais une géométrie différente comme
  `kInvalidScale`, et un alpha ou un `ImageColorSpace` non identique comme
  `kInvalidConversion`.
- `JpegDocument.decode` hierarchy refuse le retagging d’espace couleur ; les
  sorties `RGBA_F16_NORM` utilisent `AlphaType.PREMUL`.
- Les overloads `Pixmap` PNG/JPEG copient les composants F16 prémultipliés
  directement. `Pixmap.getPremulRgbaF16` lit avec le `rowBytes` déclaré et
  protège son usage aux formats F16.

## Vérifications

- `rtk ./gradlew :codec:png:test :codec:jpeg:test :codec:common:test` — PASS.
- `rtk git diff --check` — PASS.

## Commit

- `refactor(codec): migrate png and jpeg raster contracts`
- `fix(codec): tighten png jpeg raster contracts`

## Hors périmètre

- WebP et les autres codecs (T5+), ainsi que les vecteurs de test, ne sont pas
  modifiés.
- `codec/animated` reste hors périmètre de T4 ; son appel Skia historique à
  l’ancien utilitaire doit être migré avec ce module, plutôt que par une façade
  de compatibilité.
- `integration-tests/skia/test-similarity-scores.properties` était déjà
  modifié et reste strictement non indexé.
