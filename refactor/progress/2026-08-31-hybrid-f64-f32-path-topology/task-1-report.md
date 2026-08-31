# Task 1 report — Preserve source spans and close unsafe-compaction regressions

## Implementation

- Renamed flattened source fields to `sourceSegmentIndexI32` and `parameterF64`; implicit fill-close edges retain seam segment `-1` and the original endpoint.
- Extended input/split edges with source segment and source parameter intervals. Split cuts derive the source parameter by interpolation, not coordinate reevaluation.
- Added `PathSourceTopologyF64`, source spans/sections/locations, exact contact-witness model, and a marked transitional legacy adapter.
- Replaced late synthetic-F64 witness compaction with conservative rejection for projected witness runs. Existing original-`PathF32` provenance retains the temporary legacy branch until the hybrid DCEL writer lands in Task 3.
- Replaced the nullable permitted-collapse path in uncanonical projection with explicit `ProjectedContourResultF32.Drop` propagation.

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathMeasureF32.kt` (mechanical consumer rename)
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFlatteningF64Test.kt` (mechanical consumer rename)

## TDD evidence

RED, before production edits:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
3 tests completed, 3 failed
single source witness cannot erase either significant region: AssertionFailedError
distinct witnesses cannot consume one another: AssertionFailedError
under threshold collapse never leaks a generic Kotlin error: IllegalStateException
```

```text
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
3 tests completed, 3 failed
the first two failed membership assertions; the third failed in Kotlin Preconditions
```

Reason: the old late compaction erased significant witness-supported regions and dereferenced a permitted dropped contour.

GREEN:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
BUILD SUCCESSFUL — 3 tests passed
```

## Verification

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --rerun-tasks
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL — 290 tests completed

rtk git diff --check
exit 0
```

The existing Gradle restricted-native-access, deprecation, repository-preference, and Node `DEP0169` messages remain baseline toolchain noise; no new test failures or compiler warnings were introduced.

## Self-review and concerns

Reviewed source provenance, deterministic ordering, public-only regression assertions, the shared work-budget call sites, and the absence of GM/font/codec changes. The legacy compaction is explicitly transitional and only retained for original `PathF32` provenance so existing path-operation semantics remain stable; Task 3 must remove that branch when the hybrid DCEL writer consumes the topology directly.

## Fix round 1

The production arrangement now calls `splitPathSourceTopologyF64` and reaches the legacy arrangement only through its transitional adapter. Input-edge construction retains distinct coincident source locations, including the `t=0.0` segment location and the implicit seam. Exact-cut flags prevent span merging through an exact event while allowing contiguous flattening subdivisions to share a span. `PathInputEdgeF64` now uses the required F32/F64/I32 property names with mandatory provenance fields; all callers were adapted.

The first focused run after routing the production flow through topology was RED: `PathOpsF32Test` reported `Key 0 is missing in the map.` from `inputEdgesF64`, proving that discarded coincident locations left a detached vertex. The corrected construction initializes the vertex incidence map while retaining both locations.

GREEN evidence:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsF32Test*' --rerun-tasks
BUILD SUCCESSFUL — all 73 PathOpsF32Test tests passed

rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --rerun-tasks
BUILD SUCCESSFUL
```

The historical JS RED artifact was not retained verbatim. The JVM result above preserves the exact observable message for the round-1 correction; the earlier report's JS wording is therefore only a summary, not a quoted log.

## Fix round 2

The temporary arrangement adapter now materializes every legacy split edge solely from the ordered source spans and their flattened sections. It derives only internal-section identities from the canonical span/section order; source endpoint identities remain attached to the span locations. The parallel raw-split side channel was removed. Input closing edges now select the destination segment as authority and force a seam or segment transition to `[0.0, 1.0]`, retaining both coincident locations.

RED during this round: before initializing unreferenced coincident locations, the production-routing mutation failed `PathOpsF32Test` with the exact JVM error `java.util.NoSuchElementException: Key 0 is missing in the map.` from `inputEdgesF64`. GREEN followed after preserving the locations while initializing their incidence map.

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --rerun-tasks
BUILD SUCCESSFUL
```

Self-review: the adapter no longer transports raw split edges. Remaining work is limited to the follow-on hybrid DCEL: the existing legacy projection compactor is still isolated behind its prior compatibility branch and exact overlap witness materialization requires the registry export scheduled for the next topology step.

## Fix round 5

### Capture initial et RED TDD

La commande JVM ciblée imposée, exécutée avant toute modification de ce round, n'a pas reproduit les sept échecs annoncés par le handoff : elle est sortie avec `BUILD SUCCESSFUL in 8s` et zéro test en échec.

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --tests '*PathOpsF32Test*' --tests '*PathArrangementF64Test*' --rerun-tasks
BUILD SUCCESSFUL in 8s
```

Le RED réellement observé pour le budget du nouveau pont, avant son débit explicite, est :

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsF32Test.source topology bridge charges the candidate budget before writing output*' --rerun-tasks
1 test completed, 1 failed
AssertionFailedError at PathOpsF32Test.kt:983
```

Le test attendait `path-candidate-limit` avec `maxCandidateProbes = 519`; l'absence d'exception prouvait que le travail du pont n'était pas encore entièrement débité avant l'écriture de sortie.

### Transition fermée

- Le registre exact unique exporte les composants de points canoniques n-way et les overlaps avec leurs deux intervalles paramétriques et leurs listes de spans traversés. La topologie ne relance aucun kernel pairwise source et ne retrouve aucun witness par coordonnées projetées.
- Les spans et sections sont la seule autorité du pont temporaire : index de provenance construit une fois, registre → span/section → demi-arête de frontière legacy → projection. Le même `PathCandidateWorkBudgetI32` débite chaque indexation, tri et lecture avant le travail correspondant.
- Les espaces d'identité des endpoints legacy et des joints internes de section sont disjoints; les IDs `I64` de spans/witnesses sont attribués après ordre sémantique, sans dépendre des labels ou raw input IDs.
- La projection décide explicitement `Keep`, `Drop` ou `Reject`; elle n'a ni compactor autoritaire, ni chord, ni résultat partiel, ni branche permissive pour trace absente. Son ledger temporaire valide atomiquement les claims `(witness, span, intervalle)` et rejette des intérieurs chevauchants avant le builder.
- L'ancienne assertion interne « pas de sommet collinéaire » a été retirée. Son remplacement est la régression publique de membership `collinear subdivision crossing remains observable through the public operation`.

Les trois fixtures transitoires construisent maintenant la trace de production complète sans assertion de structure. Elles couvrent : (1) un `PointF64` qui ne peut jamais certifier un `OverlapF32`, (2) des claims exacts disjoints qui conservent les trois régions sous permutations/relabelings et sans mutation de l'entrée, et (3) des claims aux intérieurs chevauchants qui rejettent atomiquement.

Ruling appliqué : la preuve locale de claims disjoints autorise `Keep`; un chevauchement de claims, ou un `PointF64` promu en `OverlapF32`, produit `Reject` atomique. Les cinq cas tangents publics effectivement promus de `PointF64` vers `OverlapF32` sont donc testés comme rejets conservateurs de transition Task 1, avec immutabilité des entrées. Dette de suivi Task 2 : restaurer leur succès seulement après une `PathProjectedCoincidenceF32` locale, bornée et prouvée; aucun alias F32 ni second DCEL hybride n'a été ajouté ici.

### GREEN et vérifications finales

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --tests '*PathOpsF32Test*' --tests '*PathArrangementF64Test*' --rerun-tasks
BUILD SUCCESSFUL in 7s

rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL in 14s
```

Fichiers de code et tests revus/modifiés : `PathSourceTopologyF64.kt`, `PathIntersectionsF64.kt`, `PathArrangementF64.kt`, `PathOpsF32.kt`, `PathArrangementF64Test.kt`, `PathOpsF32Test.kt` et `PathOpsHybridTopologyF32Test.kt`.

Self-review : le diff a été inspecté fichier par fichier; les anciens helpers de compaction/reconstruction ne sont plus présents. Les `checkNotNull` restants sont les invariants AVL/AABB préexistants de `PathIntersectionsF64`, pas un fallback de projection. Les seuls avertissements des vérifications sont les avertissements Gradle/JDK/Node déjà signalés plus haut. Aucun résultat ni couverture n'est revendiqué au-delà des commandes et tests listés ci-dessus.
