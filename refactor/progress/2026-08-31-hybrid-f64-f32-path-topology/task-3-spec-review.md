# Task 3 — review de conformité

## Verdict

`Spec: FAIL`

## Critical

### Les cuts projetés strictement intérieurs ne sont pas implémentés

La phase se déclare encore `lookup-only`. Les identités ne sont produites que
pour les paramètres de section `0/1`; `validateProjectedClaimNoOpF64F32`
rejette toute identité absente et toute borne locale intérieure. `limitsI32`
n'est pas utilisé pour cette transaction et Task 3 ne débite aucun nouveau
slot `maxIntersections`.

Une coïncidence locale valide dont une borne tombe dans une section est donc
rejetée au lieu de matérialiser coupe, identité et provenance. Le plafond de
trois hypothèses publiques ne dispense pas cette voie transférée explicitement
par le ruling Task 2.

### Une dépendance collapsed partielle peut être ignorée

`classifySelectedCollapsedContinuationsF64F32` ignore une incidence lorsque son
propre `sourceSpanIdI64` n'apparaît pas dans les demi-arêtes sélectionnées. Un
span entièrement collapsed au milieu d'un contour par ailleurs sélectionné a
précisément cette forme : aucune half-edge propre, mais deux voisins de contour
potentiellement sélectionnés.

Le garde full-contour ne couvre que les contours dont tous les carriers sont
collapsed. Les rays ne cherchent par ailleurs que les sections voisines du
même span, pas la continuation du contour au-delà de ses frontières. Cette
voie peut omettre un span sans preuve au lieu de rejeter atomiquement la
dépendance partielle.

## Important

- Les tests n-way et contacts disjoints ajoutés caractérisent des contacts
  source exacts déjà verts sur Task 2; ils ne traversent pas la branche de
  proposal projetée. Aucun test public ne couvre conflit inter-witness,
  nouvelle borne intérieure ou seuil under/equal/above `2^-45`.
- `sourceContourDoubleAreaWithinCollapseToleranceF64` exécute les produits et
  sommes d'expansions sans budget reçu ni preflight local distinct du nombre
  de termes d'aire.

## Éléments conformes

- Pour les proposals endpoint-only actuellement atteignables, tous les groupes
  sont validés avant publication des IDs/coincidences/aliases.
- Les conflits d'intérieurs inter-witness et identités exactes aux endpoints
  partagés sont contrôlés.
- `PathBoundaryDisposition` est un vrai enum; un sibling full-collapsed non
  prouvé n'est jamais silencieusement `DROP`.
- Aucun changement font, codec, GM, render, score ou exclusion.

Vérification fraîche : tests focalisés JVM `20/20`, JS vert, diff-check et
worktree propres. Les suites vertes n'atteignent pas les deux blockers.
