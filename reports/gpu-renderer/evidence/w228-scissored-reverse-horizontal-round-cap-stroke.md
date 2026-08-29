# W228 — scissored reverse horizontal round-cap stroke

## Objet

Vérifier la composition entre le clipping rectangulaire et la route native d’un segment horizontal round-cap parcouru de droite à gauche.

## Contrat vérifié

- source : `(26,16) -> (6,16)` ;
- transformation : identité ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- join : `MITER` ;
- scissor device intégral : `[24,15,27,18)` ;
- anti-aliasing désactivé.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke-scissor` ;
- sortie attendue : 9 pixels rouges dans l’intersection cap/corps avec le scissor ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseHorizontalV1` ;
- bounds de scissor propagé : `GPUPixelBounds(24,15,27,18)`.

## Limites explicites

Cette vague couvre uniquement un clip rectangulaire intégral sans transformation, sur le contrat horizontal inversé de largeur 4. Les clips fractionnaires, clips complexes, autres directions, largeurs, caps, dash, courbes et anti-aliasing restent hors de ce contrat.
