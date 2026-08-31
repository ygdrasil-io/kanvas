# Task 1 — Re-review Sol conformité, fix round 2

Fix base : `41c3e15ad`  
Head : `73fa30440`

## Verdicts

- **Topologie source autoritaire pour l'adaptateur** — **ADDRESSED** — `PathOpsF32` passe par `splitPathSourceTopologyF64`; l'adaptateur parcourt chaque span et chaque section, sans chord (`PathOpsF32.kt:167-170`, `PathSourceTopologyF64.kt:136-175`).
- **Compactor sans autorité / Drop explicite** — **NOT ADDRESSED** — le fallback nullable est devenu l'erreur publique, mais le compactor reste appelé et supprime des sommets (`PathOpsF32.kt:283-309`, `PathOpsF32.kt:460-509`).
- **Witnesses exacts complets** — **NOT ADDRESSED** — seuls les `PointF64` sont matérialisés; aucun `OverlapF64` n'est construit (`PathSourceTopologyF64.kt:36-45`, `PathSourceTopologyF64.kt:96-127`).
- **Travail candidat budgété** — **NOT ADDRESSED** — double parcours et intersections robustes sans budget; scans des spans sans débit (`PathOpsF32.kt:672-687`, `PathSourceTopologyF64.kt:117-125`).
- **Couverture des nouveaux invariants** — **NOT ADDRESSED** — aucun test du fix ne rend observable la neutralisation de la topologie/witnesses.
- **Side channel legacy** — **ADDRESSED** — `legacySplitEdgesF64` a disparu et l'adaptateur dérive chaque section depuis les spans (`PathSourceTopologyF64.kt:132-181`).

## Nouvelle rupture

- Aucune rupture de conformité supplémentaire établie.

## Verdict

- `Spec fix round 2: FAIL`

