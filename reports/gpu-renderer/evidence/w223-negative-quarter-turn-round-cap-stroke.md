# W223 — negative quarter-turn round-cap stroke

## Objet

Promouvoir la variante `round cap` (embout rond) largeur 4 après une rotation exacte de −90 degrés.

## Contrat vérifié

- Segment source `(4,8) → (16,8)`.
- Matrice device exacte `translate(12,24) · rotate(-90)`.
- Segment device obtenu `(20,20) → (20,8)`.
- Largeur 4, rayon 2, cap `ROUND`, join `MITER`, `antiAlias = false`.
- Coordonnées device intégrales et segment ouvert unique.

## Preuves

- Oracle CPU indépendant `surface-srgb-round-cap-stroke-vertical`.
- Smoke natif exact : `60` pixels rouges, avec pixels d’extrémité et pixel hors contour vérifiés.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `SingleSegmentRoundPixelExactR2NegativeQuarterTurnV1`.
- Payload et autorité de couverture valident explicitement ce nouveau proof ID.

## Limites

La route reste limitée à une rotation exactement négative de 90 degrés, une largeur/rayon prouvés,
un segment unique non anti-aliasé et des coordonnées device intégrales. Les rotations non exactes,
les translations fractionnaires, les autres transformations affines et les chemins multi-segments
restent soumis à leurs contrats dédiés ou refusés.
