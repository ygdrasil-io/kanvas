# Clip path cubique borné — WebGPU (2026-08-27)

La fixture `bounded-cubic-clip-v1` dessine un `FillRect` opaque 64×64 sous un
unique clip fermé composé d’un cubic `(8,8) → (8,44) → (56,44) → (56,8)`.
Elle couvre les quatre combinaisons bornées : `Intersect`/`Difference` et
`Winding`/`EvenOdd`, sans AA. Le lowering conserve l’information qu’un cubic
était présent avant la flattening bornée afin que les clips inverse cubic restent
refusés explicitement.

Le stencil-cover WebGPU existant est la preuve native : le producteur emploie
`IncrementWrap`/`DecrementWrap` pour `Winding` et `Invert`/`Invert` pour
`EvenOdd`; le consommateur teste `NotEqual` pour `Intersect` et `Equal` pour
`Difference`. L’oracle CPU indépendant ne sonde que les pixels éloignés des
arêtes : le point `(32,24)` est dans le lobe cubique et `(32,4)` est hors de la
forme. Les 32 canaux RGBA de ces huit échantillons (2 par variante) sont
byte-exact entre l’oracle et le readback WebGPU.

La voie reste headless/offscreen. Aucun GM n’a été modifié, aucune limite
d’arêtes ni seuil de similarité/performance n’a été relâché. `clipcubic` et
`clippedcubic` ne sont pas promus par cette fixture : ils restent des GMs hors
de ce contrat monopath non-AA, avec leurs refus existants conservés.

Les refus restent explicites : `unsupported.clip.inverse_cubic`,
`unsupported_transform:Perspective` et `unsupported.clip.vertex_budget`.
Les détails CPU, GPU, diff, stats, route et refus sont dans les JSON voisins.
