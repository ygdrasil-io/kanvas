# Adaptive rational conic lowering

Cette vague remplace l'échantillonnage uniforme des `ConicTo` par une subdivision
adaptative de De Casteljau en coordonnées homogènes. Le poids rationnel est donc
pris en compte dans le critère de planéité, tout en conservant une profondeur et
un budget de sommets bornés.

## Preuves

- `PathTessellatorTest.rational conic uses homogeneous adaptive subdivision`
  vérifie que l'arc rationnel conserve sa courbure et reste dans une enveloppe
  de tessellation bornée.
- Le test existant vérifie l'extrémité exacte et le refus stable des poids non
  positifs.
- Le test ciblé `:gpu-renderer:test --tests '*PathTessellatorTest'` passe (33 tests).

Cette preuve est au niveau de la géométrie GPU. Aucun GM n'est promu dans cette
vague : les GMs `conicpaths` contiennent encore des strokes/hairlines refusés,
et cette PR ne contourne pas ces refus en prétendant supporter ces routes.
