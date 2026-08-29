# W216 — uniformly scaled vertical dashed stroke

## Objet

Compléter la preuve de l’échelle uniforme positive `2×` pour le lane vertical de dash borné `[8, 4]`.

## Contrat vérifié

- Path source `(8,4) → (8,14)`, largeur `4`, dash `[8,4]`, phase `0`.
- CTM `scale(2,2)`, soit `(16,8) → (16,28)`, largeur device `8`, dash device `[16,8]`.
- Coordonnées device intégrales, cap `BUTT`, join `MITER`, `antiAlias = false`.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-uniform-scale-vertical`.
- Smoke natif exact : `128` pixels rouges dans le run device `y=8..23`, gap à `y=24`.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `VerticalDashedButtMiterV1`.
- Catalogue : `164` render cases, `181` cases au total.

## Limites

Les échelles anisotropes, négatives ou non intégrales, ainsi que rotations et skew, restent hors contrat.
