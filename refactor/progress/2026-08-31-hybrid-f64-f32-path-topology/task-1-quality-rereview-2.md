# Task 1 — Re-review Sol qualité, fix round 2

Fix base : `41c3e15ad`  
Head : `73fa30440`

## Verdicts

- **Locations source/seam** — **ADDRESSED** — transition de segment forcée à `[0,1]`, seam `-1` préservé (`PathOpsF32.kt:234-241`, `PathFlatteningF64.kt:53-56`).
- **Fusion des spans** — **NOT ADDRESSED** — un endpoint partagé collinéaire passe dans le kernel, devient `isIntersection=true`, puis bloque la fusion (`PathIntersectionsF64.kt:266-274`, `PathIntersectionsF64.kt:343-369`, `PathSourceTopologyF64.kt:71-80`).
- **Contacts exacts complets** — **NOT ADDRESSED** — aucun `OverlapF64` construit (`PathSourceTopologyF64.kt:96-127`).
- **Compactor accessible** — **NOT ADDRESSED** — heuristique globale et suppression effective persistent (`PathOpsF32.kt:283-308`, `PathOpsF32.kt:497-509`).
- **Travail candidat borné** — **NOT ADDRESSED** — double parcours, intersections et scans sans débit/index (`PathOpsF32.kt:672-688`, `PathSourceTopologyF64.kt:117-125`).
- **IDs indépendants des labels** — **NOT ADDRESSED** — spans et witnesses restent triés/numérotés depuis operand, contour, segment et raw edge IDs (`PathSourceTopologyF64.kt:59-86`, `PathSourceTopologyF64.kt:110-125`, `PathSourceTopologyF64.kt:206-209`).
- **Pipeline et tests autoritaires** — **NOT ADDRESSED** — side channel supprimé, mais witnesses jamais consommés; les tests hybrides appellent directement la projection et ne détecteraient pas leur neutralisation (`PathOpsF32.kt:167-170`, `PathOpsHybridTopologyF32Test.kt:67-71`).
- **Preuve RED historique** — **NOT ADDRESSED** au sens strict. Ruling maintenu : artefact perdu, ne pas fabriquer; Minor non bloquant si la limitation reste explicite.

## Nouvelle rupture

- **Important — espaces d'identités mélangés dans l'adaptateur.** Les endpoints gardent les identités basées sur les anciennes input edges, tandis que les joints internes reçoivent des identités synthétiques basées sur les nouveaux IDs de sections (`PathSourceTopologyF64.kt:149-181`). Des tuples identiques peuvent alors désigner des points différents et produire `path-arrangement-inconsistent`. Employer un namespace disjoint ou remapper toutes les identités de façon cohérente.

## Vérifications ciblées

- `PathOpsF32Test.metamorphic tangent ovals preserve INTERSECT at translation` : PASS, mais ne couvre pas l'aliasing d'identités.
- `git diff --check 41c3e15ad..73fa30440` : PASS.

## Verdict

- `Quality fix round 2: FAIL`

