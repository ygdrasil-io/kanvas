# Task 3 — re-review de conformité round 1

## Verdict

`Spec: FAIL`

## Critical

1. Le comptage `maxIntersections` exclut les groupes d'endpoints existants mais
   d'identités exactes distinctes. Un nouveau contact/alias projeté non no-op
   peut donc être accepté lorsque la source est déjà à la limite. Le contrôle
   arrive aussi après les allocations de staging.
2. Le remapping post-split associe un claim à une seule section finale par sa
   paire d'endpoints. Un staggered n-way où une autre relation ajoute un cut au
   milieu de ce claim ne retrouve plus de section portant les deux endpoints;
   les coincidences ne sont ni subdivisées ni propagées sur les sous-rails.
   L'identité/aliasing conserve en outre une dépendance au représentant F32.
3. Les endpoints collapsed sont unionnés et le carrier omis des contributions
   avant le DCEL/winding. Le gate ultérieur ne peut pas restaurer les secteurs
   perdus; l'absence de contour sélectionné peut encore devenir une fausse
   preuve d'absence de dépendance.
4. Les siblings full-collapsed sont classifiés isolément contre le même winding
   de face. Leur effet conjoint n'est jamais évalué; le no-face multi-contour
   rejette indistinctement et l'orientation exacte n'est pas démontrée par le
   simple `windingDeltaI32` source.

## Important

- L'aire exacte et la classification par contour ont une complexité quadratique
  potentielle mais seulement un débit linéaire.
- Aucun nouveau test public ne couvre groupe endpoint-only à la limite,
  staggered n-way subdivisé, siblings collapsed, no-face multiple/inverse ou
  seuils hybrides.

## Contrôles positifs

Le staging préserve la source, les validations ordinaires précèdent la
publication, l'adjacence cyclique traverse spans/seams et les labels ne servent
plus de tie-break géométrique. Full JVM+JS vert, 61 tâches; diff-check propre;
aucun changement font/codec/GM/exclusion.
