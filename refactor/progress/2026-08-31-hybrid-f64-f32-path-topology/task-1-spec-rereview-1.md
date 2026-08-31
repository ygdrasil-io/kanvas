# Task 1 — Re-review Sol conformité, fix round 1

Fix base : `d1f29b829`  
Head : `41c3e15ad`

## Verdicts des findings

- **Topologie source non intégrée au flux cible** — **NOT ADDRESSED** — le pipeline construit nominalement la topologie, mais l'adaptateur renvoie les raw split edges stockés en parallèle ; ni spans ni witnesses ne gouvernent l'arrangement (`PathOpsF32.kt:167-170`, `PathSourceTopologyF64.kt:48-51`, `PathSourceTopologyF64.kt:128-134`).
- **Une subdivision de flattening reste une autorité de span** — **ADDRESSED** dans la lecture de conformité — la fusion n'utilise plus `sourceId` et suit opérande, contour, segment et paramètre lorsque le cut suivant n'est pas exact (`PathSourceTopologyF64.kt:72-83`, `PathIntersectionsF64.kt:307-308`). La review qualité conserve toutefois un défaut plus précis sur la classification des endpoints collinéaires ; il reste bloquant.
- **Provenance du seam implicite supprimée** — **ADDRESSED** dans la lecture de conformité (`PathFlatteningF64.kt:51-56`, `PathOpsF32.kt:189-209`, `PathOpsF32.kt:229-240`). La review qualité conserve toutefois le défaut d'intervalle de fermeture `[1,1]`; il reste bloquant.
- **Compaction encore autoritaire et chemin nullable** — **NOT ADDRESSED** — bypass heuristique, compaction effective et retour silencieux du contour original sans `Drop` (`PathOpsF32.kt:278-304`, `PathOpsF32.kt:492-504`).
- **Witnesses pairwise, pas composantes exactes** — **NOT ADDRESSED** — les points n-way sont groupés par identité, mais aucun `OverlapF64` n'est produit depuis les composantes exactes (`PathSourceTopologyF64.kt:97-127`).
- **Contrôle quadratique hors budget** — **NOT ADDRESSED** — deux intersections robustes par paire sans budget (`PathOpsF32.kt:667-680`).
- **Interface exacte `PathInputEdgeF64`** — **ADDRESSED** (`PathIntersectionsF64.kt:15-27`).
- **Couverture `PathIntersectionsF64Test.kt`** — **NOT ADDRESSED** — adaptation mécanique seulement ; pas de couverture observable des nouveaux spans/witnesses (`PathIntersectionsF64Test.kt:582-615`, `PathIntersectionsF64Test.kt:1390-1403`).
- **Preuve RED JS** — **ADDRESSED** par qualification honnête de l'absence d'artefact historique (`task-1-report.md:83`).

## Nouvelle rupture dans le fix

- **Important — side channel legacy interdit.** `PathSourceTopologyF64` ajoute `legacySplitEdgesF64` puis l'adaptateur restitue cette liste au lieu de mapper `sourceSpansF64` (`PathSourceTopologyF64.kt:48-52`, `PathSourceTopologyF64.kt:128-134`).

## Verdict

- `Spec fix round 1: FAIL`

