# Clip path cubique borné — WebGPU (2026-08-27)

La fixture `bounded-cubic-clip-v1` dessine un `FillRect` opaque 32×32 sous un
unique clip fermé de deux anneaux cubiques de même orientation. Elle couvre les
quatre combinaisons bornées : `Intersect`/`Difference` et
`Winding`/`EvenOdd`, sans AA. Les deux contours distinguent réellement les
fill rules : `Winding` conserve le centre (winding = 2) alors que `EvenOdd`
le transforme en trou. Le lowering conserve l’information qu’un cubic était
présent avant la flattening bornée afin que les clips inverse cubic restent
refusés explicitement.

Le stencil-cover WebGPU existant est la preuve native : le producteur emploie
`IncrementWrap`/`DecrementWrap` pour `Winding` et `Invert`/`Invert` pour
`EvenOdd`; le consommateur teste `NotEqual` pour `Intersect` et `Equal` pour
`Difference`. L’oracle CPU indépendant produit le buffer RGBA complet à partir
de `Path.contains` (linéarisation CPU à 16 pas, distincte du flattening GPU
adaptatif). Les 4 096 canaux de chaque variante, soit 16 384 canaux, sont
byte-exact (`tolerance = 0`) contre les readbacks WebGPU. Les pixels adjacents
aux arêtes cubiques extérieure et intérieure sont aussi nommés dans le test.

La voie reste headless/offscreen. La fixture a exactement 26 vertices après
flattening adaptatif, sous `RenderConfig.maxPathVertices = 256`. W140 a
également promu le GM `clippedcubic` : 10 opérations sont dispatchées sans
refus sur la route cubic bornée. `clipcubic` reste refusé (17 opérations,
`unsupported.stroke.width_invalid`) car son hairline stroke sort du contrat
de stroke actuel. Les PNG, statistiques et diagnostics de cette promotion
sont dans `reports/gpu-renderer/evidence/clipped-cubic-gm-2026-08-29/`.

Les refus restent explicites : `unsupported.clip.inverse_cubic`,
`unsupported_transform:Perspective` et `unsupported.clip.vertex_budget`.
Les détails CPU, GPU, diff, stats, route et refus sont dans les JSON voisins.
