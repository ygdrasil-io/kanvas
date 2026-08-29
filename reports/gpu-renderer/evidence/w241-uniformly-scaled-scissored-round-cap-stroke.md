# W241 — uniformly scaled scissored round-cap stroke

## Objet

Prouver l'utilisation de la lane de round-cap scalée lorsqu'un clip rectangulaire entier est appliqué avant le dessin.

## Contrat vérifié

- source : `(8,16) -> (24,16)` ;
- transformation : `scale(2,2)` ;
- segment device : `(16,32) -> (48,32)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- scissor device : `[12,30,20,38)` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Preuves

- lowering explicite : `SingleSegmentRoundUniformScaleV1` ;
- route native : `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant : `surface-srgb-round-cap-stroke-uniform-scale-scissor` ;
- sortie attendue et observée : 45 pixels rouges dans le scissor ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- inventaire : géométrie `StrokeStencilEdgeFan`, lowering et bornes du scissor vérifiés ;
- contrat de catalogue : une scène et un rendu ajoutés, avec comparaison exacte.

## Limites explicites

La preuve couvre un scissor rectangulaire entier, une échelle positive exactement `2`, un segment horizontal de largeur `4`, sans anti-aliasing. Les clips de chemin, clips inverses, transformations non uniformes, diagonales, rotations et autres paramètres restent hors de cette route.
