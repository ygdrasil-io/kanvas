# W220 — vertical round-cap stroke

## Objet

Promouvoir la variante verticale de la preuve pixel-exacte des round caps (embouts ronds) de rayon `2`.

## Contrat vérifié

- Segment source et device `(16,6) → (16,26)`.
- Largeur `4`, rayon `2`, cap `ROUND`, join `MITER`, `antiAlias = false`.
- Grille device intégrale et segment ouvert unique, assez long pour séparer les deux caps.

## Preuves

- Oracle CPU indépendant `surface-srgb-round-cap-stroke-vertical`.
- Smoke natif exact : `92` pixels rouges, avec pixels d’extrémité et pixel hors disque vérifiés.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `SingleSegmentRoundPixelExactR2VerticalV1`.
- Payload et autorité de couverture acceptent explicitement ce nouveau proof ID.

## Limites

Les caps ronds restent limités à une géométrie mono-segment, largeur/rayon prouvés et coordonnées intégrales. Les diagonales, tailles différentes, chemins multi-segments, transformations générales et clips complexes restent refusés.
