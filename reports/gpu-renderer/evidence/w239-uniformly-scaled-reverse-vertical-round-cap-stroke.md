# W239 — uniformly scaled reverse vertical round-cap stroke

## Objet

Étendre la lane de round-cap scalée au parcours vertical inverse, avec une couverture indépendante de l'orientation du segment.

## Contrat vérifié

- source : `(16,24) -> (16,8)` ;
- transformation : `scale(2,2)` ;
- extrémités device : `(32,48) -> (32,16)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Preuves

- lowering explicite : `SingleSegmentRoundUniformScaleV1` ;
- route native : `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant, évalué dans l'espace device et invariant par orientation : `surface-srgb-round-cap-stroke-uniform-scale-vertical` ;
- sortie attendue et observée : 308 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- inventaire : géométrie `StrokeStencilEdgeFan` et lowering vérifiés ;
- contrat de catalogue : une scène et un rendu ajoutés, avec politique exacte.

## Limites explicites

La lane couvre l'échelle positive exactement `2`, les segments horizontaux ou verticaux sur grille entière dans les deux sens, sans clip ni anti-aliasing. Les échelles différentes, transformations non uniformes, diagonales, rotations, clips, autres caps/largeurs, courbes et dash restent refusés.
