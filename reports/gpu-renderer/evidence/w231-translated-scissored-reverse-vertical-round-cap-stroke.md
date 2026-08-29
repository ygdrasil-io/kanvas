# W231 — translated scissored reverse vertical round-cap stroke

## Objet

Vérifier la combinaison complète translation + clipping sur un segment vertical round-cap parcouru de bas en haut.

## Contrat vérifié

- source : `(16,26) -> (16,6)` ;
- transformation : translation `(2,3)`, extrémités device `(18,29) -> (18,9)` ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- scissor device : `[17,28,20,30)` ;
- anti-aliasing désactivé.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke-vertical-scissor` ;
- sortie attendue : 6 pixels rouges dans l’intersection clip/cap ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseVerticalV1` ;
- bounds de scissor propagé : `GPUPixelBounds(17,28,20,30)`.

## Limites explicites

Cette vague couvre uniquement la translation entière `(2,3)` et un clip device intégral autour de l’extrémité basse. Les clips fractionnaires ou complexes, autres transformations, caps, largeurs, dash, courbes et anti-aliasing restent hors contrat.
