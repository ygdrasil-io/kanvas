# Simple stroke — butt/miter (2026-08-27)

La seule fixture activée est `simple-stroke-butt-miter-v1`: un unique segment
ouvert `(4,16) -> (28,16)`, rouge opaque, largeur `4`, cap `butt`, join
`miter`, sans dash/path-effect et sans AA. Elle suit le chemin CorePrimitive
en `Stencil1x`, est rendue par WebGPU headless/offscreen, et son readback est
identique à l’oracle CPU: 4 096 canaux comparés, zéro différence.

Les diagnostics de route, l’oracle CPU, le readback GPU, le diff et les
statistiques sont les JSON voisins. Aucun budget ni seuil n’a été augmenté ou
abaissé.

Les compteurs de submission/readback sont une preuve descriptive, pas un gate
de performance; aucun seuil de similarité ou de performance n’a été ajouté,
abaissé ou relâché.

Aucun GM stroke n’est promu. Les routes terminales stables actuelles sont
documentées dans `refusals.json`: `strokedline_caps` est bloqué par son gradient
à trois stops, `strokes_round` par son budget d’expansion, et `dashcircle` par
une capability pipeline manquante. Les GMs n’ont pas été modifiés.

Le test public `Surface` couvre aussi un `PathEffect.Dash(floatArrayOf())`:
l’identité `Dash` est conservée même sans intervalle et le lowering le refuse.
La limite de miter exacte est finie et `>= 1`; `0`, `NaN` et `Infinity` sont
refusés avant la préparation native.
