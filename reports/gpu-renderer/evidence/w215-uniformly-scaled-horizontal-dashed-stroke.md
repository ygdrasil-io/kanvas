# W215 — uniformly scaled horizontal dashed stroke

## Objet

Promouvoir une première transformation de dash au-delà de l’identité et de la translation : une échelle uniforme positive `2×` sur un segment horizontal borné.

## Contrat vérifié

- Path source `(4,8) → (14,8)`, largeur `4`, dash `[8,4]`, phase `0`.
- CTM `scale(2,2)`, soit un segment device `(8,16) → (28,16)`, largeur device `8`, dash device `[16,8]`.
- Coordonnées device intégrales, cap `BUTT`, join `MITER`, `antiAlias = false`.
- Seules les échelles uniformes positives (avec leur variante translate-affine bornée) sont admises ; rotations et affines générales restent refusées.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-uniform-scale`.
- Smoke natif exact : `128` pixels rouges ; run à partir de `x=8`, run suivant jusqu’à `x=23`, gap à `x=24`.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `HorizontalDashedButtMiterV1`.
- Contrats gpu-renderer, payload et catalogue validés.

## Limites

La promotion ne couvre pas les échelles anisotropes, négatives, fractionnaires donnant des coordonnées non intégrales, ni les rotations ou skew. Le pattern est conservé dans l’espace du path puis mesuré en espace device par la géométrie native.
