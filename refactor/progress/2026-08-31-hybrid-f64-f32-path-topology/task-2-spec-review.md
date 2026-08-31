# Task 2 — Sol spec review

Base : `14e93a9ab`  
Head : `04f7accbb`

## Verdict

- `Spec: FAIL`
- `Gate spec: NEEDS FIXES`

## Critical findings

1. **Multi-section spans are replaced by chords.** Broad phase, ray order, areas and writer use only span endpoints and ignore `flattenedSectionsF64` (`PathHybridTopologyF64F32.kt:198`, `PathArrangementF64F32.kt:557`, `PathOpsF32.kt:261`). Preserve/classify every section, use local first/last directions and serialize every ordered section point without granting new topology identity.

2. **Overlap registry is still pairwise/rematerialized.** Contacts are stored pairwise and grouped by start/end coordinates; endpoint identity lookup builds a ±16 ULP window (`PathIntersectionsF64.kt:1333-1388`, `PathSourceTopologyF64.kt:219-237`, `PathSourceTopologyF64.kt:422`). Build canonical components directly with unique incidence multisets and exact `(edge ID, parameter bits)` identities, no coordinate/ULP recovery.

3. **Unsupported projected authority remains.** Endpoint/endpoint F32 contacts without witness are accepted; any `OverlapF64` on the same spans can authorize a distant projected relation (`PathHybridTopologyF64F32.kt:267-275`, `PathHybridTopologyF64F32.kt:425`, `PathHybridTopologyF64F32.kt:552`). Require witness coverage of exact parameters/endpoints; otherwise reject before DCEL.

4. **Projected coincidences/collapsed incidences are not authoritative.** Claims cover whole spans, are not cut/count-checked atomically, aliases are recovered by F32 coordinates, and collapsed incidences are silently skipped (`PathHybridTopologyF64F32.kt:624-637`, `PathArrangementF64F32.kt:215-227`). Produce exact bound parameters/identities, validate claims transactionally, count groups, and consume coincidences/collapsed incidences explicitly.

## Important findings

1. **Representative candidates are not evaluated per incidence.** All cuts reuse one canonical point and the alternate same-witness selection is missing (`PathIntersectionsF64.kt:330`, `PathHybridTopologyF64F32.kt:431`).
2. **`maxHalfEdges` is checked on raw spans, not canonical final counts** (`PathHybridTopologyF64F32.kt:91`).
3. **Behavioral tests miss chord/cross-operand mutations.** Tangent probes allow diamonds; helpers reconstruct internal inputs; n-way uses only operand FIRST (`PathBehaviorTestSupportF32.kt:101`, `PathOpsHybridTopologyF32Test.kt:327`, `PathOpsF32.kt:213`).
4. **Claims/budget remain quadratic and backend-local.** All pairs are preflighted, broad phase also charges dynamically, `Long` arithmetic is unchecked, and JVM/JS find independent boundaries (`PathHybridTopologyF64F32.kt:338-398`, `PathIntersectionsF64.kt:557`).

## Minor finding

- Rename `candidateIndex` to `candidateIndexI32` (`PathArrangementF64F32.kt:113`).

