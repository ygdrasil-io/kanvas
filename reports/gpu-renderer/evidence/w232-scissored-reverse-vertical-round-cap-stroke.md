# W232 — scissored reverse vertical round-cap stroke

## Objet

Vérifier le clipping intégral sur un segment vertical round-cap parcouru de bas en haut.

## Contrat vérifié

- source : `(16,26) -> (16,6)` ;
- transformation : identité ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- scissor device : `[16,5,18,8)` autour du cap supérieur ;
- anti-aliasing désactivé.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke-vertical-scissor` ;
- sortie attendue : 6 pixels rouges dans l'intersection clip/cap ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseVerticalV1` ;
- bounds de scissor propagé : `GPUPixelBounds(16,5,18,8)`.

## Limites explicites

Cette vague couvre uniquement le segment vertical inverse sur grille entière et un clip rectangulaire intégral autour du cap supérieur. Les translations, clips fractionnaires ou complexes, autres transformations, caps, largeurs, dash, courbes et anti-aliasing restent hors contrat.
