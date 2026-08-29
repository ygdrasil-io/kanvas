# W217 — uniform scale plus translation dashed stroke

## Objet

Vérifier la variante affine bornée qui combine une échelle uniforme positive `2×` et une translation intégrale `(2,4)`.

## Contrat vérifié

- Path source `(4,4) → (14,4)`, largeur `4`, dash `[8,4]`, phase `0`.
- CTM `translate(2,4) · scale(2,2)`, soit `(10,12) → (30,12)` en device.
- Largeur device `8`, intervalles device `[16,8]`, coordonnées device intégrales.
- Cap `BUTT`, join `MITER`, `antiAlias = false`.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-uniform-scale-translate`.
- Smoke natif exact : `128` pixels rouges, run device de `x=10` à `25`, gap à `x=26`.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `HorizontalDashedButtMiterV1`.
- Contrats gpu-renderer, payload et catalogue validés.

## Limites

Seules les matrices sans skew, à échelle uniforme positive et coordonnées device intégrales sont promues. L’anisotropie, le skew, la perspective et les coordonnées fractionnaires restent refusés.
