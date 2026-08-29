# W243 — round-cap stroke sous clip winding

## Objectif

Prouver qu’un cas borné de `round-cap stroke` (contour à bouts arrondis) sous
un clip de chemin `WINDING` (règle d’enroulement) passe maintenant par la route
native de stencil-cover de Kanvas.

## Contrat vérifié

- surface publique Kanvas, cible RGBA8 32×32 ;
- clip triangle `(3.25,3.25)`, `(28.75,3.25)`, `(3.25,28.75)` ;
- segment horizontal `(6,16) -> (26,16)` ;
- largeur 4, cap `ROUND`, join `MITER` ;
- couleur rouge opaque, SrcOver, anti-aliasing désactivé ;
- clip `INTERSECT`, remplissage `WINDING`.

## Preuves attendues

- lowering direct explicite des triangles du contour et des deux demi-disques ;
- route native `native.path_stroke.stencil_cover` ;
- oracle CPU indépendant `surface-srgb-clip-path-round-cap-stroke` ;
- comparaison pixel-center exacte ;
- diagnostic de route et preuve native de draw, pipeline, submit et readback.

## Portée et limites

La preuve publiée ici couvre cette topologie de segment unique, opaque, non-AA,
avec cap rond et join miter, sous un clip winding triangle simple. Les gradients,
dash effects, autres fill rules, clips inverses et anti-aliasing restent refusés.
Les autres variantes géométriques ne sont considérées valides que lorsqu’elles
réutilisent un `loweringProof` déjà établi par une preuve dédiée.
