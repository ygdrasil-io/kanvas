# W214 — translated vertical dashed stroke

## Objet

Vérifier que la translation intégrale déjà autorisée par le renderer conserve la phase et les gaps d’un dash vertical `[8, 4]`.

## Contrat vérifié

- `Surface` public `RGBA8`, cible `32×32`.
- Segment source `(16,4) → (16,28)`, largeur `4`, path ouvert.
- Translation CTM `(3,2)`, donc segment device `(19,6) → (19,30)`.
- `antiAlias = false`, cap `BUTT`, join `MITER`, phase `0`.
- Route native WebGPU stencil-cover, sans généralisation aux transformations non intégrales.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-translated-vertical`.
- Smoke natif exact : `64` pixels rouges ; centre `(18,6)` et `(18,18)` rouge, `(18,14)` et `(18,26)` transparent.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `VerticalDashedButtMiterV1`.
- Catalogue : `162` render cases, `179` cases au total.

## Limites

Seules les translations intégrales du lane borné sont couvertes. Les rotations, échelles non unitaires, matrices affines générales et chemins courbes restent hors contrat.
