# W236 — uniformly scaled and translated vertical round-cap stroke

## Objet

Étendre la lane de round-cap scalée au segment vertical après translation entière, sans élargir le contrat aux transformations générales.

## Contrat vérifié

- source : `(16,8) -> (16,24)` ;
- transformation : translation `(4,6)` puis `scale(2,2)` ;
- extrémités device : `(36,22) -> (36,54)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Preuves

- lowering explicite : `SingleSegmentRoundUniformScaleV1` ;
- route native : `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant : `surface-srgb-round-cap-stroke-uniform-scale-translate-vertical` ;
- sortie attendue et observée : 308 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- inventaire : géométrie `StrokeStencilEdgeFan` et lowering vérifiés ;
- contrat de catalogue : une scène et un rendu ajoutés, avec politique exacte.

## Limites explicites

La lane couvre l'échelle positive exactement `2`, une translation entière, les segments horizontaux ou verticaux sur grille entière, sans clip ni anti-aliasing. Les échelles différentes, transformations non uniformes, diagonales, rotations, clips, autres caps/largeurs, courbes et dash restent refusés.
