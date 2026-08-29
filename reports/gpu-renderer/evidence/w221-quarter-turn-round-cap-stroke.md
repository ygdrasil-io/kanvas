# W221 — quarter-turn round-cap stroke

## Objet

Promouvoir la variante `round cap` (embout rond) largeur 4 après une rotation exacte de 90 degrés.

## Contrat vérifié

- Segment source `(4,8) → (16,8)`.
- Matrice device exacte `translate(20,4) · rotate(90)`.
- Segment device obtenu `(12,8) → (12,20)`.
- Largeur 4, rayon 2, cap `ROUND`, join `MITER`, `antiAlias = false`.
- Coordonnées device intégrales et segment ouvert unique.

## Preuves

- Oracle CPU indépendant `surface-srgb-round-cap-stroke-vertical`.
- Smoke natif exact : `60` pixels rouges, avec pixels d’extrémité et pixel hors contour vérifiés.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `SingleSegmentRoundPixelExactR2QuarterTurnV1`.
- Payload et autorité de couverture valident explicitement ce nouveau proof ID.

## Limites

La route reste limitée à une rotation exactement positive de 90 degrés, une largeur/rayon prouvés,
un segment unique non anti-aliasé et des coordonnées device intégrales. Les rotations `-90°`/`270°`,
la rotation de 180°, les transformations affines générales, les coordonnées fractionnaires et les
chemins multi-segments restent refusés.
