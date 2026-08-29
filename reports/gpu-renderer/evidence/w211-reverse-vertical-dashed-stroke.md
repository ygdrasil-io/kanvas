# W211 — reverse vertical dashed stroke

## Objet

Étendre la preuve du lane natif de dash borné `[8, 4]` à un segment vertical parcouru de bas en haut (`(16,28) → (16,4)`). Cette vague ne crée pas de nouvelle route : elle vérifie que la direction source inverse conserve la phase du dash sur le contrat vertical déjà promu.

## Contrat vérifié

- `Surface` public, format `RGBA8`, cible `32×32`.
- Path non fermé, segment vertical intégral, largeur `4`.
- `antiAlias = false`, `BUTT` cap, `MITER` join.
- `PathEffect.Dash([8, 4], phase = 0)`.
- Transformation identité.
- Route native WebGPU stencil-cover existante, avec refus stable hors de ce périmètre.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-reverse-vertical`.
- Pixel smoke natif : `64` pixels rouges, runs aux centres `y=27` et `y=15`, gaps aux centres `y=19` et `y=7`.
- Diagnostics : décision `Prepared`, draw/pipeline/submit/readback présents, `opsRefused = 0`.
- Catalogue : `159` render cases, `176` cases au total.

## Limites

Cette preuve ne généralise pas les dashs aux courbes, aux transformations non intégrales, aux phases arbitraires, aux caps/joins différents ou aux autres path effects ; ces variantes restent explicitement hors du contrat promu.
