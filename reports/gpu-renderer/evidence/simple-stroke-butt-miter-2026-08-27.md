# Simple stroke — butt/miter (2026-08-27)

La seule fixture activée est `simple-stroke-butt-miter-v1`: un unique segment
ouvert `(4,16) -> (28,16)`, rouge opaque, largeur `4`, cap `butt`, join
`miter`, sans dash/path-effect et sans AA. Elle suit le chemin CorePrimitive
en `Stencil1x`, est rendue par WebGPU headless/offscreen, et son readback est
identique à l’oracle CPU: 4 096 canaux comparés, zéro différence.

Les diagnostics de route, l’oracle CPU, le readback GPU, le diff et les
statistiques sont les JSON voisins. Aucun budget ni seuil n’a été augmenté ou
abaissé.

Aucun GM stroke n’est promu. Les routes terminales stables actuelles sont
documentées dans `refusals.json`: `strokedline_caps` est bloqué par son gradient
à trois stops, `strokes_round` par son budget d’expansion, et `dashcircle` par
une capability pipeline manquante. Les GMs n’ont pas été modifiés.
