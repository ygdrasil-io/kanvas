# W240 — uniformly scaled translated reverse vertical round-cap stroke

## Objet

Fermer la couverture des orientations de la lane de round-cap scalée avec la variante verticale inverse et translatée.

## Contrat vérifié

- source : `(16,24) -> (16,8)` ;
- transformation : translation `(4,6)` puis `scale(2,2)` ;
- extrémités device : `(36,54) -> (36,22)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Preuves

- lowering explicite : `SingleSegmentRoundUniformScaleV1` ;
- route native : `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant, évalué dans l'espace device et invariant par orientation : `surface-srgb-round-cap-stroke-uniform-scale-translate-vertical` ;
- sortie attendue et observée : 308 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- inventaire : géométrie `StrokeStencilEdgeFan` et lowering vérifiés ;
- contrat de catalogue : une scène et un rendu ajoutés, avec politique exacte.

## Limites explicites

La lane couvre l'échelle positive exactement `2`, une translation entière, les segments horizontaux ou verticaux sur grille entière dans les deux sens, sans clip ni anti-aliasing. Les échelles différentes, transformations non uniformes, diagonales, rotations, clips, autres caps/largeurs, courbes et dash restent refusés.
