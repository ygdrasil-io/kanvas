# Bounded multi-segment stroke (2026-08-29)

Cette vague ajoute une route native bornée pour une polyline ouverte de 3 à 8
points : largeur finie de 0,5 à 64 px, cap `butt`, join `miter`, sans AA, dash
ou path effect, avec identité, translation ou scale uniforme positive.

La géométrie existante de `GPUStroke` est conservée et consommée comme
`StrokeStencilEdgeFan`; le proof typé `MultiSegmentButtMiterV1` empêche de
confondre cette route avec les preuves single-segment ou hairline.

La preuve de cette vague est contractuelle : le test public traverse le planner,
la collecte sémantique et la validation du payload natif. L’oracle CPU et la
capture pixel GPU ne sont pas produits ici et restent explicitement refusés dans
`cpu.json`; aucune promotion GM n’est revendiquée.
