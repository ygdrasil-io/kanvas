# W212 — phase-shifted vertical dashed stroke

## Objet

Vérifier la seconde phase admise (`4`) du lane natif de dash borné `[8, 4]` sur un segment vertical public (`(16,4) → (16,28)`). La vague étend la couverture d’évidence au vertical ; elle ne modifie pas la route de production.

## Contrat vérifié

- `Surface` public, `RGBA8`, cible `32×32`.
- Path ouvert vertical, coordonnées intégrales, largeur `4`.
- `antiAlias = false`, cap `BUTT`, join `MITER`, transformation identité.
- `PathEffect.Dash([8, 4], phase = 4)`.
- Route native WebGPU stencil-cover, avec les autres phases et formes hors contrat toujours refusées.

## Preuves

- Oracle CPU indépendant `surface-srgb-dashed-stroke-phase-four-vertical`.
- Smoke natif exact : `64` pixels rouges ; centres `y=4`, `12`, `16`, `24` rouges et `y=8`, `20` transparents.
- Inventaire : décision `native.path_stroke.stencil_cover`, preuve `VerticalDashedButtMiterV1`, phase `4` conservée.
- Catalogue : `160` render cases, `177` cases au total.

## Limites

La phase reste limitée aux valeurs explicitement prouvées (`0` et `4`) et aux segments horizontaux/verticaux bornés. Les dashs généraux, courbes, transformations non intégrales et autres caps/joins ne sont pas promus.
