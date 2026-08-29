# W229 — translated scissored reverse horizontal round-cap stroke

## Objet

Vérifier simultanément la translation intégrale, le clipping rectangulaire et le sens droite-vers-gauche sur la route native round-cap.

## Contrat vérifié

- source : `(26,16) -> (6,16)` ;
- transformation : translation `(3,2)`, extrémités device `(29,18) -> (9,18)` ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- scissor device : `[28,17,30,20)` ;
- anti-aliasing désactivé.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke-translated-scissor` ;
- sortie attendue : 6 pixels rouges dans l’intersection clip/cap ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseHorizontalV1` ;
- bounds de scissor propagé : `GPUPixelBounds(28,17,30,20)`.

## Limites explicites

Cette vague couvre uniquement la translation entière `(3,2)` et un clip device intégral sur le segment horizontal inversé de largeur 4. Les transformations fractionnaires, clips complexes, rotations non couvertes, autres caps, dash, courbes et anti-aliasing restent hors contrat.
