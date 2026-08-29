# W226 — reverse horizontal round-cap stroke

## Objet

Prouver la route native d’un segment horizontal à extrémités rondes parcouru de droite à gauche, avec une largeur de 4 pixels et sans anti-aliasing.

## Contrat vérifié

- source : `(26,16) -> (6,16)` ;
- transformation : identité ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- join : `MITER` ;
- chemin ouvert, segment unique et coordonnées entières.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke` ;
- sortie attendue : union exacte de deux disques de rayon 2 et du corps horizontal ;
- smoke test natif : 92 pixels rouges, aucun refus ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseHorizontalV1`.

## Limites explicites

Cette promotion couvre uniquement les segments horizontaux inversés, de largeur 4, sur coordonnées entières, avec transformation identité ou translation intégrale. Les autres caps, largeurs, courbes, dash, perspective et anti-aliasing restent soumis à leurs routes ou refus dédiés.
