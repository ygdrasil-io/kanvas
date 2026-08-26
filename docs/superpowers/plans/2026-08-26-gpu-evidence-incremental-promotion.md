# GPU Evidence Incremental Promotion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a versioned, incrementally promotable GPU correctness-evidence catalogue that keeps PNGs in Git while preventing unchanged scenes and duplicated run metadata from being rewritten.

**Architecture:** Keep the current v1 scene-bundle verifier for the migration window. Add a v2 root catalogue containing one shared environment record, one promotion record, and per-scene source-commit/index entries; v2 scene directories retain only scene evidence and integrity metadata. Generation, verification, and promotion share an explicit `EvidenceSelection`, and selected promotion stages the complete catalogue before an atomic swap.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, kotlinx.serialization JSON, JUnit 5/Kotlin Test, existing PNG/hash/comparison utilities, headless WebGPU evidence runtime.

**Spec:** `docs/superpowers/specs/2026-08-26-gpu-evidence-incremental-promotion-design.md`

## Global Constraints

- Keep promoted PNG evidence in Git for reviewability and auditability.
- Do not change GPU rendering, CPU oracle, comparison thresholds, route IDs, or refusal semantics.
- Keep the supported renderer runtime headless/offscreen and do not introduce native windowing.
- Do not move correctness evidence to an external artifact store or introduce Git LFS.
- Do not infer affected scenes from source-code dependency analysis; selection remains explicit and reviewable.
- Preserve complete-catalogue validation for CI and release checks.
- An incremental promotion must reject an execution-environment mismatch with `gpu.evidence.environment-mismatch.requires-rebaseline`.
- A failed staged verification or filesystem swap must leave the existing promoted root unchanged.
- v1 evidence remains readable during the migration window and is never silently interpreted as v2.
- Use `rtk` before every shell command and use `apply_patch` for source edits.

### Promotion command policy

Selected scene promotion is the daily workflow. For an existing complete
promoted catalogue, the full command is an explicit rebaseline and must pass
`-Pall=true`, `-PpromotionRebaseline=true`, and nonblank prior/new comparison
summaries (the CLI receives `--all --rebaseline` and both summaries):

```text
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence \
  -PsourceCommit="$COMMIT_SHA" -Pall=true \
  -PpromotionReviewer="$REVIEWER" -PpromotionReason="$PROMOTION_REASON" \
  -PpromotionRebaseline=true \
  -PpromotionPriorComparison="$PRIOR_COMPARISON" \
  -PpromotionNewComparison="$NEW_COMPARISON"
```

Do not use or document a bare full-catalogue promotion against an existing
destination; implementation rejects it without rebaseline comparison data.

---

### Task 1: Add the shared evidence-selection and v2 schema contracts

**Files:**
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceSelection.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceArtifactModel.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceSelectionTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceV2SchemaSerializationTest.kt`

**Interfaces:**
- Produces `sealed interface EvidenceSelection` with `data object All` and `data class Explicit(val sceneIds: List<String>)`.
- Produces `object EvidenceSelectionParser` with `fun from(sceneIds: List<String>, all: Boolean): EvidenceSelection` and `fun readSceneFile(path: Path): List<String>`.
- Produces `fun EvidenceSelection.resolve(cases: List<EvidenceCase>): List<EvidenceCase>`; it returns all cases for `All`, resolves known IDs for `Explicit`, sorts explicit IDs deterministically, and rejects unknown or duplicate IDs.
- Produces v2 constants `GPU_EVIDENCE_CATALOG_SCHEMA_V2 = "gpu-evidence-catalog-v2"`, `GPU_EVIDENCE_SCENE_SCHEMA_V2 = "gpu-evidence-scene-v2"`, and `GPU_EVIDENCE_PROMOTION_SCHEMA_V2 = "gpu-evidence-promotion-v2"`.
- Produces v2 data models for `EvidenceCatalogEntry`, `EvidenceCatalogV2`, `EvidenceEnvironmentV2`, and `EvidencePromotionV2` with canonical JSON serialization.

- [ ] **Step 1: Write failing selection tests.**

Add tests asserting that `EvidenceSelectionParser.from(emptyList(), all = true)` returns `EvidenceSelection.All`, a non-empty explicit list is sorted, duplicate IDs fail, an unknown ID fails when resolved against `GpuEvidenceCatalog.cases`, and an empty scene file fails.

```kotlin
@Test
fun `explicit selection resolves known ids in sorted order`() {
    val selection = EvidenceSelectionParser.from(
        listOf("solid-triangle-path", "solid-card-stack"),
        all = false,
    )

    assertEquals(
        listOf("solid-card-stack", "solid-triangle-path"),
        selection.resolve(GpuEvidenceCatalog.cases).map { it.descriptor.id.value },
    )
}
```

- [ ] **Step 2: Run the focused tests and verify they fail.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*EvidenceSelectionTest' --tests '*EvidenceV2SchemaSerializationTest'`

Expected: compilation or test failure because the selection and v2 contract types do not exist.

- [ ] **Step 3: Implement the selection contract.**

Create `EvidenceSelection.kt` with strict parsing. `Explicit` must copy and sort its IDs; `EvidenceSelectionParser.from` must reject `all = true` together with scene IDs and must reject an empty non-all selection; `readSceneFile` must read nonblank trimmed lines, reject duplicate lines, and reject a file with no scene IDs.

The resolver must use the supplied case list as the only catalogue authority:

```kotlin
fun EvidenceSelection.resolve(cases: List<EvidenceCase>): List<EvidenceCase> = when (this) {
    EvidenceSelection.All -> cases
    is EvidenceSelection.Explicit -> {
        val byId = cases.associateBy { it.descriptor.id.value }
        sceneIds.map { id -> byId[id] ?: error("unknown evidence scene: $id") }
    }
}
```

- [ ] **Step 4: Implement deterministic v2 models and serialization.**

Add v2 models with these JSON keys:

```text
catalog.json: schemaVersion, environment, promotion, scenes
catalog scene entry: sceneId, sourceCommit, manifest, manifestSha256
environment.json: schemaVersion, osName, osVersion, osArchitecture, javaVersion,
                  deviceGeneration, capabilityImplementation, available, adapter
promotion.json: schemaVersion, promotedAtUtc, reviewer, reason, rebaseline,
                sceneIds, priorComparison, newComparison
v2 scene manifest: schemaVersion, sceneId, expectation, observedOutcome,
                   oracleKind, oracleId, oracleVersion, oracleProvenance,
                   oracleSha256, files
```

The v2 scene manifest must not serialize `sourceCommit`, `generatedAtUtc`, `environment.json`, or `promotion.json`; those are root-level or catalogue-entry data in v2. Use sorted maps and sorted scene entries before serialization.

- [ ] **Step 5: Run the focused tests and commit.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*EvidenceSelectionTest' --tests '*EvidenceV2SchemaSerializationTest'`

Expected: PASS.

Commit: `rtk git add integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceSelection.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceArtifactModel.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceSelectionTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceV2SchemaSerializationTest.kt && rtk git commit -m "test: define GPU evidence selection and v2 schemas"`

### Task 2: Write v2 scene bundles and generated-root metadata

**Files:**
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleWriter.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogWriter.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/EvidenceSceneContracts.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogWriterTest.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleRoundTripTest.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleSchemaSerializationTest.kt`

**Interfaces:**
- Adds `EvidenceBundleWriter.writeGeneratedV2(descriptor: EvidenceSceneDescriptor, observation: SceneObservation, expectedRgba: ByteArray?, attemptId: String, checkedInPngBytes: ByteArray?): Path`, returning the selected scene directory below `correctness/generated/$sourceCommit/$sceneId`.
- Produces `EvidenceCatalogWriter.writeGeneratedCatalog(root: Path, selection: EvidenceSelection, observations: Map<String, SceneObservation>, bundlePaths: Map<String, Path>): Path`.
- `EvidenceCatalogWriter` writes `catalog.json` and `environment.json` into the generated root and verifies that all observations use the same environment identity, ignoring only `EvidenceEnvironment.sourceCommit`.
- `GpuEvidenceCliRequest` becomes `data class GpuEvidenceCliRequest(val repositoryRoot: Path, val sourceCommit: String, val selection: EvidenceSelection)` and exposes `val sceneId: String?` as a compatibility accessor returning the single explicit ID or null.

- [ ] **Step 1: Add failing v2 writer tests.**

Extend round-trip tests with a rendered and refusal v2 bundle. Assert that rendered v2 scene files are exactly `manifest.json`, `gpu.png`, `cpu.png` or `skia.png`, `diff.png`, `stats.json`, `route.json`, `diagnostics.json`, and `verdict.json`; refusal v2 files omit all images and contain `manifest.json`, `stats.json`, `route.json`, `diagnostics.json`, and `verdict.json`. Assert that neither scene directory contains `environment.json` or `promotion.json`, and that v2 manifests omit `sourceCommit` and `generatedAtUtc`.

Add a catalogue test asserting that two observations produce one root `environment.json`, one deterministic `catalog.json`, sorted scene entries, and each `manifestSha256` matches the scene manifest bytes.

- [ ] **Step 2: Run the focused tests and verify they fail.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*EvidenceBundleRoundTripTest' --tests '*EvidenceBundleSchemaSerializationTest' --tests '*EvidenceCatalogWriterTest'`

Expected: FAIL because the v2 writer and catalogue writer are not implemented.

- [ ] **Step 3: Implement v2 scene writing without changing v1 behavior.**

Refactor shared PNG, route, diagnostics, stats, verdict, hash, and atomic-directory logic inside `EvidenceBundleWriter`, keep the existing `writeGenerated(evidenceCase: EvidenceCase, observation: SceneObservation, expectedRgba: ByteArray?, attemptId: String, checkedInPngBytes: ByteArray?): Path` overload emitting the v1 layout for migration tests, and add the v2 method with the signature listed above. The v2 method writes the same scene-specific evidence without environment/promotion files and emits a v2 manifest.

The v2 writer must keep the existing `destination(sceneId: String): Path`, symlink, path-confinement, failure-retention, and atomic move protections. A v2 refusal must remain zero-submission and must not acquire any image oracle.

- [ ] **Step 4: Implement `EvidenceCatalogWriter`.**

Derive the root environment from the first selected observation and compare every other selected observation by `osName`, `osVersion`, `osArchitecture`, `javaVersion`, `deviceGeneration`, `capabilityImplementation`, `available`, and every adapter field. Store each selected scene’s source commit in its `EvidenceCatalogEntry`; do not put that commit into the root environment or v2 scene manifest. Write `catalog.json` using sorted scene IDs and canonical JSON bytes.

- [ ] **Step 5: Update the GPU runner to write only the requested selection.**

Parse `--scene` repeatedly, `--scenes-file`, and `--all` in `GpuEvidenceCliRequest`. Reject a missing selection instead of implicitly running the complete catalogue. Resolve the selection before opening the GPU runtime, execute only selected cases, collect each observation, call `writeGeneratedV2`, and call `writeGeneratedCatalog` after all selected cases pass. Keep cleanup and failure-graph handling unchanged.

- [ ] **Step 6: Run tests and commit.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*EvidenceBundleRoundTripTest' --tests '*EvidenceBundleSchemaSerializationTest' --tests '*EvidenceCatalogWriterTest' --tests '*GpuEvidenceCliTest'`

Expected: PASS, with existing v1 tests still passing and new v2 tests green.

Commit: `rtk git add integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleWriter.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogWriter.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCli.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/EvidenceSceneContracts.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleRoundTripTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleSchemaSerializationTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogWriterTest.kt && rtk git commit -m "feat: write versioned GPU evidence staging roots"`

### Task 3: Add dual-read catalogue verification

**Files:**
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogVerifier.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleVerifier.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCliTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogVerifierTest.kt`

**Interfaces:**
- Produces `EvidenceCatalogVerifier.verify(root: Path, selection: EvidenceSelection, cases: List<EvidenceCase>, expectedSourceCommit: String?): EvidenceCatalogVerification`.
- Produces `data class EvidenceCatalogVerification(val sceneIds: List<String>, val sourceCommits: Map<String, String>, val environment: EvidenceEnvironmentV2)`.
- `VerifyEvidenceCliRequest` gains `val selection: EvidenceSelection`; `--allow-historical-commit` remains valid only when no expected commit is supplied and remains a v1/historical escape hatch.

- [ ] **Step 1: Write failing v2 verification tests.**

Create a valid v2 generated root fixture with two known cases and assert that selected verification passes, complete verification rejects the strict subset, a root with an unknown scene ID fails, a wrong manifest hash fails, a root environment mismatch fails, and two known scenes with different catalogue-entry source commits pass complete v2 verification when their root environment is identical.

Add a regression test that the existing v1 `writeAll(COMMIT)` fixture still passes the v1 verifier path.

- [ ] **Step 2: Run focused tests and verify they fail.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*EvidenceCatalogVerifierTest' --tests '*VerifyEvidenceCliTest'`

Expected: FAIL because v2 catalogue verification and selection-aware requests are not implemented.

- [ ] **Step 3: Extract shared scene checks into a v2-aware verifier path.**

Keep `EvidenceBundleVerifier.verify(directory: Path, expected: EvidenceVerificationExpectation): EvidenceBundleVerification` as the existing v1 scene verifier. Add a v2 scene branch that validates the code-derived descriptor, oracle identity, expected route, PNG dimensions, stats, route telemetry, diagnostics, verdict, safe file names, and every manifest hash, but reads `environment.json` and `sourceCommit` from the root catalogue instead of the scene directory.

- [ ] **Step 4: Implement root catalogue validation.**

`EvidenceCatalogVerifier.verify(root: Path, selection: EvidenceSelection, cases: List<EvidenceCase>, expectedSourceCommit: String?): EvidenceCatalogVerification` must reject symlinks and path traversal, require `catalog.json` and `environment.json`, require `promotion.json` only for promoted roots, enforce exact code-derived IDs for `EvidenceSelection.All`, enforce exact selected IDs for explicit generated verification, and reject unlisted scene directories. It must validate every `sourceCommit` against `[0-9a-f]{40}`, compare the generated environment to all selected observations, and use the entry-specific source commit when verifying each scene. The `catalog.json` `promotion` field is `null` in generated roots and the literal path `promotion.json` in promoted roots.

Use the stable environment mismatch text `gpu.evidence.environment-mismatch.requires-rebaseline` for incremental callers.

- [ ] **Step 5: Update `VerifyEvidenceCliRunner`.**

Parse selection flags, detect v1 versus v2 from the root layout/schema, dispatch to the matching verifier without schema coercion, and preserve current stdout/stderr verdict reporting. Selected v2 verification must not require unrelated scene directories; `--all` must require the complete catalogue.

- [ ] **Step 6: Run tests and commit.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*EvidenceCatalogVerifierTest' --tests '*VerifyEvidenceCliTest' --tests '*EvidenceBundleTamperTest' --tests '*EvidenceBundleVerifierStrictnessTest'`

Expected: PASS.

Commit: `rtk git add integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogVerifier.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleVerifier.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCli.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCliTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogVerifierTest.kt && rtk git commit -m "feat: verify GPU evidence catalogues incrementally"`

### Task 4: Make Gradle tasks selection-aware and keep full gates explicit

**Files:**
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/boundary/GpuEvidenceArchitectureBoundaryTest.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCliTest.kt`

**Interfaces:**
- Adds Gradle properties `-Pscene=solid-card-stack` and `-PscenesFile=scenes.txt` for explicit selection; existing `-Pscene` remains supported.
- Adds a shared Gradle helper `selectionArguments(): List<String>` that emits either repeated `--scene`, `--scenes-file`, or `--all`.
- Full gate tasks with no selection emit `--all`; selected invocations emit only their explicit selection.

- [ ] **Step 1: Write failing Gradle-boundary tests.**

Update `GpuEvidenceArchitectureBoundaryTest` to assert that correctness generation, generated verification, and promotion all use the same selection arguments; that no-selection full gates forward `--all`; that `scenesFile` is forwarded only as a selection input; and that no performance task receives correctness-only promotion flags.

Add runner tests for repeated `--scene`, `--scenes-file`, `--all`, missing selection, duplicate selection, and unknown selection.

- [ ] **Step 2: Run focused tests and verify they fail.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*GpuEvidenceArchitectureBoundaryTest' --tests '*GpuEvidenceCliTest'`

Expected: FAIL because the Gradle tasks and CLI parsers still support only one optional scene or catalogue-wide behavior.

- [ ] **Step 3: Update Gradle selection forwarding.**

Replace `optionalSceneArgument()` with `selectionArguments()`. Read `scene`, `scenesFile`, and `all` properties; reject conflicting properties; emit `--all` when no selector is supplied to a full-gate task; emit the exact selected flags for developer invocations. Wire the helper into `generateGpuEvidence`, `verifyGeneratedGpuEvidence`, and `promoteGpuEvidence` while leaving `gpuEvidencePerformance` behavior unchanged except for its existing single-scene filter.

- [ ] **Step 4: Update task descriptions and aliases.**

Remove the obsolete temporary-alias wording from `generateBootstrapGpuEvidence`, describe selected versus full generation in the three correctness task descriptions, and keep `gpuEvidenceCorrectness` and `gpuEvidenceVerification` catalogue-wide.

- [ ] **Step 5: Run tests and commit.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*GpuEvidenceArchitectureBoundaryTest' --tests '*GpuEvidenceCliTest'`

Expected: PASS.

Commit: `rtk git add integration-tests/gpu-evidence/build.gradle.kts integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCli.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCli.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/boundary/GpuEvidenceArchitectureBoundaryTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCliTest.kt && rtk git commit -m "build: make GPU evidence selection explicit"`

### Task 5: Implement atomic incremental promotion

**Files:**
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCliTest.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleWriterContractTest.kt`

**Interfaces:**
- `PromoteEvidenceCliRequest` gains `val selection: EvidenceSelection`; `--all` remains required for an initial catalogue and remains the explicit full-rebaseline mode.
- Adds `PromoteEvidenceCliRunner.promoteSelected(request: PromoteEvidenceCliRequest)` as the internal operation used by `run`.
- Root v2 promotion writes `catalog.json`, `environment.json`, and one root `promotion.json`; scene directories do not receive promotion metadata.

- [ ] **Step 1: Write failing incremental-promotion tests.**

Add tests that create a valid v2 promoted root, generate a replacement v2 bundle for one known scene, promote only that scene, and assert:

```kotlin
assertEquals(beforeUnselectedBytes, afterUnselectedBytes)
val changedEntry = catalogEntry(promotedRoot(repository), "solid-card-stack")
assertEquals("0123456789abcdef0123456789abcdef01234567", changedEntry["sourceCommit"]!!.jsonPrimitive.content)
assertEquals(setOf("solid-card-stack"), rootPromotion.sceneIds.toSet())
```

The test-local `catalogEntry(root: Path, sceneId: String): JsonObject` helper must parse `root.resolve("catalog.json")`, select the object whose `sceneId` equals the argument, and return that object; `rootPromotion` must be parsed from `root.resolve("promotion.json")`.

Also test that selected promotion rejects an absent destination, an absent selected generated bundle, an unknown generated scene, a different environment, a changed unselected scene in the staged source, and a failed staged verification without mutating the existing root. Test that `--all --rebaseline` accepts comparison summaries and a changed environment.

- [ ] **Step 2: Run focused promotion tests and verify they fail.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest' --tests '*EvidenceBundleWriterContractTest'`

Expected: FAIL because promotion still requires v1 `--all`, writes per-scene metadata, and replaces the catalogue wholesale.

- [ ] **Step 3: Parse selection and validate generated input.**

Update `PromoteEvidenceCliRequest.parse` to accept repeated `--scene`, `--scenes-file`, and `--all`, then resolve the selection against `GpuEvidenceCatalog.cases`. For explicit selection, require the generated root to contain exactly the selected v2 scene directories and reject extra generated directories before staging. For `All`, require the generated root to contain the complete catalogue.

- [ ] **Step 4: Stage an incremental v2 catalogue.**

Replace the current per-scene promotion loop with these operations:

```text
verify selected generated v2 input
read and validate the existing promoted v2 catalogue
compare generated root environment with promoted root environment
copy the existing promoted root to a temporary sibling
copy only selected generated scene directories into the staging root
update sorted catalog.json entries for selected scenes
write one root promotion.json containing selected sceneIds
verify the complete staging root
atomically swap staging and promoted roots
```

Copy existing unselected scenes byte-for-byte; never pass them through `EvidenceBundleWriter`. Preserve the existing backup/restore behavior and cleanup diagnostics.

- [ ] **Step 5: Enforce the environment and rebaseline policy.**

For selected promotion, compare root environment records byte-for-byte after canonical serialization. On mismatch, fail with `gpu.evidence.environment-mismatch.requires-rebaseline`. For `--all --rebaseline`, require nonblank prior/new comparison summaries, replace the root environment, and set `rebaseline: true` in the single root promotion record.

- [ ] **Step 6: Run promotion tests and commit.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest' --tests '*EvidenceBundleWriterContractTest' --tests '*EvidenceCatalogVerifierTest'`

Expected: PASS, including the existing atomic-swap failure and failed-rollback tests.

Commit: `rtk git add integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCliTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleWriterContractTest.kt && rtk git commit -m "feat: promote selected GPU evidence scenes atomically"`

### Task 6: Add the mechanical v1-to-v2 migration command

**Files:**
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/MigratePromotedEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/MigratePromotedEvidenceCliTest.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCliTest.kt`

**Interfaces:**
- Adds Gradle task `migratePromotedGpuEvidenceV1ToV2`.
- Adds `MigratePromotedEvidenceCliRunner.run(args: Array<String>): Int` with `--repository-root`, `--reviewer`, and `--reason` arguments.
- Migration reads only `correctness/promoted`, writes a temporary v2 root beside it, and atomically swaps only after complete v1 and v2 verification.

- [ ] **Step 1: Write failing migration tests.**

Create a complete v1 promoted fixture with `EvidenceBundleWriter` and assert that migration creates root `catalog.json`, `environment.json`, and `promotion.json`, removes scene-level `environment.json` and `promotion.json`, preserves every scene ID and support/refusal verdict, and preserves the SHA-256 bytes of every `cpu.png`, `skia.png`, `gpu.png`, and `diff.png` file.

Add tests for missing promoted roots, v1 verification failure, mixed v1 environments, malformed promotion metadata, and an injected swap failure that leaves the original v1 root intact.

- [ ] **Step 2: Run focused migration tests and verify they fail.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest'`

Expected: FAIL because the migration CLI and task do not exist.

- [ ] **Step 3: Implement v1 validation and extraction.**

Verify the existing promoted root with the v1 historical verifier, read every scene manifest/environment/promotion, require one coherent environment identity and valid scene IDs, and retain each manifest’s source commit as the v2 catalogue-entry source commit. Reject the migration before creating a destination when any scene is invalid.

- [ ] **Step 4: Implement v2 conversion without rendering.**

Copy all scene-specific evidence bytes, remove v1 run-level JSON files, rewrite only v2 scene manifests, create one root environment record, and create one root promotion record with `rebaseline: true`, the supplied reviewer/reason, and all migrated scene IDs sorted. Do not decode/re-encode PNGs; compare their hashes before writing the staged root.

- [ ] **Step 5: Add the Gradle task and run the migration tests.**

Wire the task to the new main class, make it depend on `classes`, and declare the promoted-root inputs plus the v2 temporary output behavior. Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest' --tests '*VerifyEvidenceCliTest'`

Expected: PASS.

- [ ] **Step 6: Commit the migration tooling.**

Commit: `rtk git add integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/MigratePromotedEvidenceCli.kt integration-tests/gpu-evidence/build.gradle.kts integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/MigratePromotedEvidenceCliTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCliTest.kt && rtk git commit -m "feat: add GPU evidence v1 to v2 migration"`

### Task 7: Migrate checked-in evidence and update documentation/gates

**Files:**
- Modify: `reports/gpu-renderer/evidence/correctness/promoted/` through the migration task only; do not hand-edit PNGs.
- Modify: `wip/00-evidence-and-catalog.md`
- Modify: `wip/index.md`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/boundary/GpuEvidenceArchitectureBoundaryTest.kt`
- Modify: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCliTest.kt`

**Interfaces:**
- Checked-in promoted evidence becomes a complete v2 catalogue.
- `verifyPromotedGpuEvidence` validates v2 root metadata and heterogeneous per-scene source commits.
- Documentation exposes selected commands for daily development and `--all` only for complete gates/rebaselines.

- [ ] **Step 1: Run the full v1 verifier before conversion.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence`

Expected: PASS against the current v1 promoted root; stop if it fails and preserve the failure report instead of converting invalid evidence.

- [ ] **Step 2: Run the one-time migration.**

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:migratePromotedGpuEvidenceV1ToV2 \
  -PpromotionReviewer=renderer-maintainer \
  -PpromotionReason="migrate checked-in GPU evidence to v2"
```

Expected: one checked-in v2 root with unchanged pixel hashes. The migration is a deliberate metadata-only repository change and must be reviewed separately from implementation changes.

- [ ] **Step 3: Verify the migrated root and pixel hashes.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence`.

Then compare the pre-migration hash inventory with the migrated inventory using:

```bash
rtk proxy find reports/gpu-renderer/evidence/correctness/promoted -type f \( -name 'cpu.png' -o -name 'skia.png' -o -name 'gpu.png' -o -name 'diff.png' \) -exec shasum -a 256 {} \;
```

Expected: all image hashes match the saved pre-migration inventory, every current catalogue ID is present, and no scene directory contains `environment.json` or `promotion.json`.

- [ ] **Step 4: Update active WIP commands and boundary assertions.**

Replace claims that promotion always imposes `--all` with the selected-promotion contract. Document `-Pscene=solid-card-stack`, `-PscenesFile=scenes.txt`, and explicit full-gate `--all` behavior. Keep the warning that hardware capture and correctness promotion are separate human-authorized actions. Update architecture tests to assert root v2 files and no per-scene run metadata.

- [ ] **Step 5: Run all module tests and commit the migration/documentation.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test :integration-tests:gpu-evidence:verifyPromotedGpuEvidence`

Expected: PASS with no GPU runtime needed for promoted-root verification.

Commit: `rtk git add reports/gpu-renderer/evidence/correctness/promoted wip/00-evidence-and-catalog.md wip/index.md integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/boundary/GpuEvidenceArchitectureBoundaryTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCliTest.kt && rtk git commit -m "chore: migrate checked-in GPU evidence to v2"`

### Task 8: Add the no-unrelated-changes regression gate and complete verification

**Files:**
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/IncrementalPromotionRegressionTest.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`
- Modify: `build.gradle.kts`

**Interfaces:**
- Adds an internal promotion diff guard that accepts only selected scene paths plus root `catalog.json`, root `promotion.json`, and root `environment.json` during explicit rebaseline.
- Keeps `gpuEvidenceCorrectness` as a full generated-catalogue gate and `gpuEvidenceVerification` as a full promoted-catalogue gate.

- [ ] **Step 1: Write the regression test.**

Create a v2 promoted fixture, snapshot every regular file under an unselected scene, promote a selected replacement scene, and assert the unselected snapshot is byte-for-byte identical. Assert that injecting a modification into an unselected staged scene causes promotion to fail before swap.

- [ ] **Step 2: Run the regression test and verify it fails.**

Run: `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*IncrementalPromotionRegressionTest'`

Expected: FAIL until the promotion diff guard is wired.

- [ ] **Step 3: Implement the diff guard.**

Compare the existing promoted root and staged root after staged verification. For a selected promotion, allow changed paths matching the selected scene directories plus `catalog.json` and `promotion.json`; allow `environment.json` only for `All` with `rebaseline = true`. Fail with a path list if any unselected scene file changes.

- [ ] **Step 4: Run the complete verification suite.**

Run:

```text
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
rtk ./gradlew --no-daemon gpuEvidenceCorrectness
rtk ./gradlew --no-daemon gpuEvidenceVerification
```

Expected: all commands PASS; `gpuEvidenceCorrectness` may require an eligible WebGPU adapter, while promoted verification remains headless and does not create a GPU runtime.

- [ ] **Step 5: Review the final diff and commit.**

Run: `rtk git diff --check`, `rtk git status --short`, and `rtk git diff --stat HEAD~1`.

Expected: only selected scene evidence and root v2 metadata change in incremental promotions; no generated ignored directory is staged.

Commit: `rtk git add integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/IncrementalPromotionRegressionTest.kt integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt integration-tests/gpu-evidence/build.gradle.kts build.gradle.kts && rtk git commit -m "test: guard incremental GPU evidence diffs"`
