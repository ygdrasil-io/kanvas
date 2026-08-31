# Task 1 — Final Sol quality re-review, fix round 5

Fix base : `73fa30440`  
Head : `4e336bcfa`

## Findings ouverts

- **Contacts exacts incomplets.** L'overlap sur-réclame les spans aux bornes et perd les relations n-way/ties (`PathSourceTopologyF64.kt:399-484`).
- **Drop partiel.** Un collapse local peut conduire à `Drop` selon l'aire totale alors qu'au moins trois sommets représentables subsistent (`PathOpsF32.kt:345-378`).
- **Ledger de claims incomplet.** Aucune identité d'endpoint, conflits court-circuités par witness ID égal (`PathOpsF32.kt:117-168`, `PathOpsF32.kt:519-554`).
- **Budget non borné/déterministe.** Ledger `O(C²)`, rescans par overlap, coût des comparateurs propre au backend, tri final/writer non débités (`PathOpsF32.kt:129-151`, `PathSourceTopologyF64.kt:186-190`, `PathSourceTopologyF64.kt:382-430`, `PathIntersectionsF64.kt:1269-1277`).
- **IDs/ties instables.** Spans/witnesses égaux réutilisent le même ID sans agréger les données; le premier élément dépend de l'ordre stable des labels (`PathSourceTopologyF64.kt:269-279`, `PathSourceTopologyF64.kt:432-484`).
- **Tests hors pipeline.** Les fixtures claims contournent l'arrangement; les oracles d'immutabilité, seuil et budget ne ferment pas les mutations réalistes (`PathOpsHybridTopologyF32Test.kt:79-129`, `PathOpsHybridTopologyF32Test.kt:175-245`, `PathOpsF32Test.kt:967-997`).
- **Tests tangents affaiblis.** Le rejet n'est pas appuyé par un oracle numérique indépendant établissant le passage PointF64 → overlap F32 (`PathOpsF32Test.kt:95-149`, `PathOpsF32Test.kt:1123-1142`).

## Nouvelles ruptures

- **Important — Point witness distant.** La preuve ne compare pas le point F32 avec la projection du witness (`PathOpsF32.kt:499-529`, `PathOpsF32.kt:565-587`).
- **Important — paires adjacentes exclues avant classification.** Un backtrack/overlap F32 adjacent peut être émis sans preuve (`PathOpsF32.kt:478-484`, `PathOpsF32.kt:767-775`).
- **Important — Drop d'un contour seulement partiellement effondré** (`PathOpsF32.kt:345-378`).
- **Important — export overlap/ties incorrect** (`PathSourceTopologyF64.kt:399-484`).
- **Important — complexité et frontière de budget non déterministes** (`PathOpsF32.kt:129-151`, `PathSourceTopologyF64.kt:382-430`).

## Vérifications ciblées

- HEAD : `4e336bcfa`.
- `rtk git diff --check 73fa30440..4e336bcfa` : PASS.
- Aucun broad test relancé; les blockers sont établis statiquement.

## Verdict

- `Quality fix round 5: FAIL`

