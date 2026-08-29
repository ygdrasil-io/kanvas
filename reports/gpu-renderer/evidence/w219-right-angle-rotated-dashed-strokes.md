# W219 — right-angle rotated dashed strokes

## Objet

Étendre le dash borné aux rotations exactes déjà reconnues par la pile de géométrie : quart de tour `90°` et demi-tour `180°`.

## Contrats vérifiés

- Quart de tour : path source horizontal `(4,8) → (16,8)`, CTM `translate(20,4) · rotate(90)`, path device vertical `(12,8) → (12,20)`, largeur `4`.
- Demi-tour : path source vertical `(8,4) → (8,28)`, CTM `translate(32,32) · rotate(180)`, path device inverse `(24,28) → (24,4)`, largeur `4`.
- Dash `[8,4]`, phase `0`, cap `BUTT`, join `MITER`, `antiAlias = false`, coordonnées device intégrales.

## Preuves

- Oracle CPU indépendant pour chaque transformation.
- Smoke natif exact : `32` pixels rouges pour le quart de tour et `64` pour le demi-tour, avec gaps vérifiés.
- Inventaire : décision `native.path_stroke.stencil_cover` et preuve verticale `VerticalDashedButtMiterV1`.
- L’analyse GPU et le builder Kanvas mappent désormais les deux coefficients de skew pour authentifier les extrémités transformées.

## Limites

Seules les rotations cardinales exactes `90°` et `180°`, sans skew additionnel et sur coordonnées device intégrales, sont promues. Les angles arbitraires, l’anisotropie, le skew, la perspective et les coordonnées fractionnaires restent refusés.
