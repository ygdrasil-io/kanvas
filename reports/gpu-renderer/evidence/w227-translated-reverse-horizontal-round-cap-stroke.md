# W227 — translated reverse horizontal round-cap stroke

## Objet

Vérifier que la route native W226 conserve son résultat lorsqu’un segment horizontal parcouru de droite à gauche est déplacé par une translation entière.

## Contrat vérifié

- source : `(26,16) -> (6,16)` ;
- transformation : translation `(3,2)`, donc extrémités device `(29,18) -> (9,18)` ;
- largeur : `4` pixels, rayon `2` ;
- cap : `ROUND` ;
- join : `MITER` ;
- chemin ouvert, segment unique, coordonnées device entières et anti-aliasing désactivé.

## Preuves

- oracle CPU indépendant : `surface-srgb-round-cap-stroke` ;
- sortie attendue : 92 pixels rouges, identique à la forme non translatée dans le nouveau repère ;
- smoke test natif : aucun refus, preuves de draw, pipeline, submit et readback ;
- route : `native.path_stroke.stencil_cover` ;
- preuve de lowering : `SingleSegmentRoundPixelExactR2ReverseHorizontalV1`.

## Limites explicites

Cette vague ne généralise pas la route : elle vérifie uniquement les translations intégrales qui préservent les coordonnées device entières. Les translations fractionnaires, rotations non couvertes, autres largeurs, caps, dash, courbes et anti-aliasing restent hors de ce contrat.
