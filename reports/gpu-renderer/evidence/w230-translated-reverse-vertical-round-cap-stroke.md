# W230 — translated reverse vertical round-cap stroke

## Objet

Compléter la preuve de direction inverse pour les segments verticaux lorsque la géométrie est déplacée par une translation entière.

## Contrat vérifié

- source : `(16,26) -> (16,6)` ;
- transformation : translation `(2,3)`, extrémités device `(18,29) -> (18,9)` ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- join : `MITER` ;
- chemin ouvert, coordonnées device entières et anti-aliasing désactivé.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke-vertical` ;
- sortie attendue : 92 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseVerticalV1`.

## Limites explicites

Cette promotion couvre uniquement les segments verticaux inversés, de largeur 4, sur coordonnées entières, avec translation intégrale. Les translations fractionnaires, autres caps, largeurs, dash, courbes, rotations supplémentaires et anti-aliasing restent hors contrat.
