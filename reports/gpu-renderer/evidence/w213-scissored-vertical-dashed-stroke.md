# W213 — scissored vertical dashed stroke

## Objet

Vérifier que le lane vertical de dash borné `[8, 4]` conserve ses gaps lorsqu’il est limité par un scissor rectangulaire intégral.

## Contrat vérifié

- `Surface` public `RGBA8`, cible `32×32`.
- Segment vertical `(16,4) → (16,28)`, largeur `4`, path ouvert.
- Cap `BUTT`, join `MITER`, `antiAlias = false`, phase `0`, transformation identité.
- Scissor device intégral `[14,8]–[19,20]`.
- Route native WebGPU stencil-cover et coverage scissor préparée.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-vertical-scissor`.
- Smoke natif : `32` pixels rouges ; pixels aux centres `(15,8)`, `(15,16)`, `(15,19)` rouges et `(15,12)`, `(15,20)` transparents.
- Inventaire : `native.path_stroke.stencil_cover`, preuve `VerticalDashedButtMiterV1`.
- Catalogue : `161` render cases, `178` cases au total.

## Limites

La preuve couvre uniquement le scissor rect intégral borné. Les clips complexes, les coordonnées fractionnaires, les dashs généraux et les transformations non intégrales restent hors contrat.
