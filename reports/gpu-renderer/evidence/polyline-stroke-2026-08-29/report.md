# Bounded multi-segment stroke

Cette vague ouvre une seule route supplémentaire : une polyline ouverte de 3 à
8 points, avec largeur finie, cap `butt`, join `miter`, sans anti-aliasing,
dash ni `path effect`. Les transformations admises sont l'identité, la
translation et le scale uniforme positif.

## Preuves

- `GPUFramePathApiInventoryTest.bounded open polyline butt miter stroke reaches native stencil cover`
  traverse le planner, la collecte sémantique et la validation du payload.
- Le payload porte la preuve typée `MultiSegmentButtMiterV1`; les preuves
  single-segment restent limitées à deux sommets.
- Les variantes hors périmètre restent refusées par la politique existante.

Cette vague ne produit pas encore de capture pixel indépendante CPU/GPU :
`cpu.json` l'indique comme refus explicite et aucune promotion GM n'est
revendiquée.
