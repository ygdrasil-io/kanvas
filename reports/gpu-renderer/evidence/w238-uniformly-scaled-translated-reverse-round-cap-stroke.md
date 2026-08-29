# W238 — uniformly scaled translated reverse round-cap stroke

## Objet

Compléter la lane de round-cap scalée pour le parcours inverse avec une translation entière, en gardant la couverture strictement bornée.

## Contrat vérifié

- source : `(24,16) -> (8,16)` ;
- transformation : translation `(4,6)` puis `scale(2,2)` ;
- extrémités device : `(52,38) -> (20,38)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Preuves

- lowering explicite : `SingleSegmentRoundUniformScaleV1` ;
- route native : `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant, évalué dans l'espace device et invariant par orientation : `surface-srgb-round-cap-stroke-uniform-scale-translate` ;
- sortie attendue et observée : 308 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- inventaire : géométrie `StrokeStencilEdgeFan` et lowering vérifiés ;
- contrat de catalogue : une scène et un rendu ajoutés, avec politique exacte.

## Limites explicites

La lane couvre l'échelle positive exactement `2`, une translation entière, les segments horizontaux ou verticaux sur grille entière dans les deux sens, sans clip ni anti-aliasing. Les échelles différentes, transformations non uniformes, diagonales, rotations, clips, autres caps/largeurs, courbes et dash restent refusés.
