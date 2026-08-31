# Task 1 — Re-review Sol qualité, fix round 1

Fix base : `d1f29b829`  
Head : `41c3e15ad`

## Verdicts des findings

- **Locations source/seam** — **NOT ADDRESSED** — la fermeture implicite n'ajoute qu'une location seam à `t=1.0`, et la fermeture explicite aucun début à `t=0.0` (`PathFlatteningF64.kt:53-56`, `PathFlatteningF64.kt:168-173`). `inputEdgesF64` prend le segment de l'extrémité et le paramètre de début du point précédent, conservant un intervalle `[1,1]` (`PathOpsF32.kt:229-241`).
- **Fusion des spans** — **NOT ADDRESSED** — tout cut du registre est marqué intersection (`PathIntersectionsF64.kt:266-274`) et le raccourci des subdivisions adjacentes ne reconnaît que les endpoints non collinéaires (`PathIntersectionsF64.kt:343-369`). Une subdivision collinéaire devient donc une barrière et un witness.
- **Contacts exacts complets** — **NOT ADDRESSED** — regroupement n-way des points corrigé, mais aucun `OverlapF64` n'est construit (`PathSourceTopologyF64.kt:111-127`).
- **Compactor accessible** — **NOT ADDRESSED** — heuristique globale, suppression réelle de sommets, et `?: contour` masque le collapse sans décision explicite (`PathOpsF32.kt:278-304`, `PathOpsF32.kt:466-504`).
- **Travail candidat borné** — **NOT ADDRESSED** — rejet pairwise sans budget et scan complet des spans par identité sans débit/index (`PathOpsF32.kt:667-680`, `PathSourceTopologyF64.kt:118-125`).
- **IDs indépendants des labels** — **NOT ADDRESSED** — ordre dépendant de `operand`, `contourIndexI32`, `sourceSegmentIndexI32`, IDs d'arêtes/map keys et compteur séquentiel (`PathSourceTopologyF64.kt:60-86`, `PathSourceTopologyF64.kt:157-160`).
- **Pipeline et tests autoritaires** — **NOT ADDRESSED** — l'adaptateur jette spans/witnesses au profit de `legacySplitEdgesF64`; les régressions hybrides appellent la projection directement et resteraient vertes si la topologie était neutralisée (`PathSourceTopologyF64.kt:131-134`, `PathOpsHybridTopologyF32Test.kt:67-71`).
- **Preuve RED historique** — **NOT ADDRESSED** au sens strict, car l'artefact JS exact n'existe pas (`task-1-report.md:33-37`, `task-1-report.md:83`). Ruling du contrôleur : ne pas fabriquer cette preuve ; conserver la qualification honnête et exiger des preuves exactes pour les nouveaux cycles.

## Nouvelle rupture dans le fix

- Aucune.

## Verdict

- `Quality fix round 1: FAIL`

