# W234 — uniformly scaled and translated round-cap stroke

## Objet

Étendre la lane de round-cap scalée à une translation affine intégrale, sans ouvrir les transformations générales.

## Contrat vérifié

- source : `(8,16) -> (24,16)` ;
- transformation : `translate(4,6)` puis `scale(2,2)` ;
- extrémités device : `(20,38) -> (52,38)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Preuves

- lowering explicite : `SingleSegmentRoundUniformScaleV1` ;
- route native : `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant : `surface-srgb-round-cap-stroke-uniform-scale-translate` ;
- sortie attendue et observée : 308 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- propagation de la matrice affine vérifiée dans l'inventaire.

## Limites explicites

La lane reste limitée à l'échelle `2`, à une translation intégrale, à un segment horizontal, une largeur `4`, un cap `ROUND`, sans clip et sans anti-aliasing. Les échelles différentes, transformations non uniformes, rotations, clips, autres caps/largeurs, courbes et dash restent refusés.
