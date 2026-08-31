# Task 2 — Sol quality review

Base : `14e93a9ab`  
Head : `04f7accbb`

## Verdict

- `Quality: FAIL`
- `Gate qualité: NEEDS FIXES`

## Critical findings

1. **Chord replacement.** A multi-section span is reduced to endpoints in broad phase, DCEL, ray order and writer (`PathHybridTopologyF64F32.kt:208`, `PathArrangementF64F32.kt:557`, `PathOpsF32.kt:261`). Preserve each section as geometric carrier and emit the complete run.
2. **N-way overlaps with staggered extents are not atomized.** Pairwise contacts with different endpoints remain multiple witnesses and reject exact valid geometry (`PathIntersectionsF64.kt:1259-1364`, `PathHybridTopologyF64F32.kt:344`). Sweep exact endpoints into atomic intervals and aggregate active incidences.
3. **Projected claims are published without inter-witness validation** (`PathHybridTopologyF64F32.kt:303`, `PathHybridTopologyF64F32.kt:608`). Validate all claims transactionally before IDs/aliases/topology.

## Important findings

1. **ULP lookup off-by-one.** Index includes exactly 16 ULP while source equality is strict `<16` (`PathSourceTopologyF64.kt:229`, `PathSourceTopologyF64.kt:754`).
2. **Endpoint touch without witness accepted** (`PathHybridTopologyF64F32.kt:273`, `PathHybridTopologyF64F32.kt:427`).
3. **Representative selection omits per-incidence evaluation and same-witness canonical validation** (`PathHybridTopologyF64F32.kt:466`).
4. **Zero-area selected boundary is silently filtered**, permitting partial output (`PathArrangementF64F32.kt:863`).
5. **Budget over/double-charges and uses unchecked arithmetic; limits use raw rather than final canonical counts** (`PathHybridTopologyF64F32.kt:96`, `PathHybridTopologyF64F32.kt:398-495`, `PathIntersectionsF64.kt:94`).
6. **Aggregated edge chooses one carrier and cyclic canonicalization lacks full-sequence Booth**, leaving order dependence (`PathArrangementF64F32.kt:539`, `PathArrangementF64F32.kt:850`).
7. **Tests miss staggered overlaps, chord-discriminating probes, inter-witness claims and hand-derived budget boundary** (`PathOpsHybridTopologyF32Test.kt:71-109`, `PathOpsHybridTopologyF32Test.kt:407`).

## Minor finding

- The 1082/807-line files combine too many responsibilities. Split classification/claims, DCEL embedding/face propagation, and writing where the plan permits, or establish focused internal boundaries without source-shape tests.

