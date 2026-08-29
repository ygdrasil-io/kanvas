# W224 — scissored vertical round-cap stroke

## Objet

Vérifier la composition du round-cap vertical pixel-exact avec un scissor device intégral.

## Contrat vérifié

- Segment `(16,6) → (16,26)`, largeur 4, rayon 2.
- Cap `ROUND`, join `MITER`, `antiAlias = false`.
- Scissor device `(14,5) → (18,22)`.
- Segment ouvert unique sur grille intégrale.

## Preuves

- Oracle CPU indépendant `surface-srgb-round-cap-stroke-vertical-scissor`.
- Smoke natif exact : `68` pixels rouges, avec pixels du cap conservés, pixels du corps conservés et pixel hors scissor vérifié.
- Route native `native.path_stroke.stencil_cover`, sans refus d’opération.

## Limites

Le scissor doit rester intégral et non anti-aliasé. Les clips path complexes, les coordonnées
fractionnaires et les variantes de stroke hors des contrats round-cap existants restent refusés.
