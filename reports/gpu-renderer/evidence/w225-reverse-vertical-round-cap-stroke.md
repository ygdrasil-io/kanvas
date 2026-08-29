# W225 — reverse vertical round-cap stroke

## Objet

Admettre un segment vertical `round cap` (embout rond) largeur 4 lorsque le chemin source est parcouru dans le sens inverse.

## Contrat vérifié

- Segment source `(16,26) → (16,6)`.
- Matrice device identité.
- Largeur 4, rayon 2, cap `ROUND`, join `MITER`, `antiAlias = false`.
- Segment ouvert unique sur grille intégrale.

## Preuves

- Oracle CPU indépendant `surface-srgb-round-cap-stroke-vertical` (union géométrique, donc indépendant du sens).
- Smoke natif exact : `92` pixels rouges, identiques à la couverture du segment parcouru dans l’autre sens.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `SingleSegmentRoundPixelExactR2ReverseVerticalV1`.
- Payload et autorité de couverture valident explicitement ce nouveau proof ID.

## Limites

Cette vague ne généralise pas les chemins multi-segments ni les transformations affines. Les variantes
de largeur/rayon non prouvées, les coordonnées fractionnaires et les transformations non exactes restent refusées.
