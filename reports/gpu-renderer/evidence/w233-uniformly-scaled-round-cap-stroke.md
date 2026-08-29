# W233 — uniformly scaled round-cap stroke

## Objet

Promouvoir le premier cas de round-cap sous transformation : une échelle uniforme positive entière.

## Contrat vérifié

- source : `(8,16) -> (24,16)` ;
- transformation : `scale(2,2)` ;
- extrémités device : `(16,32) -> (48,32)` ;
- largeur locale : `4` pixels, rayon device `4` ;
- cap : `ROUND` ;
- anti-aliasing désactivé.

## Implémentation

- lowering explicite `SingleSegmentRoundUniformScaleV1` ;
- route native `native.path_stroke.stencil_cover` ;
- tessellation des caps densifiée pour la lane mise à l'échelle ;
- la politique reste bornée à l'échelle uniforme positive exactement `2` et aux segments horizontaux intégraux.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke-uniform-scale` ;
- sortie attendue et observée : 308 pixels rouges ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- inventaire : lowering et géométrie `StrokeStencilEdgeFan` confirmés.

## Limites explicites

Les échelles non entières ou non uniformes, rotations combinées, translations affines, clips, caps autres que `ROUND`, largeurs autres que `4`, courbes, dash et anti-aliasing restent hors contrat.
