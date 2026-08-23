# GPU Renderer Evidence Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `:gpu-renderer-scenes` with a small, production-backed `:integration-tests:gpu-evidence` correctness and promotion harness, then remove the legacy renderer and reports in one atomic cutover.

**Architecture:** Build the replacement beside a frozen legacy module. Scene programs construct typed product inputs; production recorders create `GPUTaskList` values; `GPUBackendSession.prepareSceneFrameSession` and `GPUFrameCoordinator` perform all GPU work. A typed expectation gate compares `ShouldRender`/`ShouldRefuse` with observed results and a versioned artifact verifier independently replays that decision from generated evidence. Correctness, promoted evidence, and performance remain separate gates inside the single `reports/gpu-renderer/evidence/` report namespace.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, JUnit 5, `kotlin.test`, `kotlinx.serialization.json`, `:gpu-renderer`, `:integration-tests:test-utils`, PNG codec runtime, WebGPU through the product runtime only.

**Spec:** `docs/superpowers/specs/2026-08-22-gpu-renderer-evidence-rebuild-design.md`

## Verification Authority

The checked-in production code at the evaluated commit is authoritative for implemented behavior. This plan and the design specification describe intent; neither is evidence that a route is supported. Executable tests and runtime artifacts tied to the same source commit verify the code. If an implementation step discovers that a named API, diagnostic, or route differs from the code, stop that step, record the mismatch, and update the implementation or obtain review for a plan correction. Never change an expectation or threshold merely to make an observed failure pass.

## Global Constraints

- Prefix every repository shell command with `rtk`.
- Use test-driven development: add one failing test, run it and observe the expected failure, implement the minimum production change, then rerun the focused test.
- Do not port Ganesh or Graphite, build a SkSL compiler, or compile scene-owned WGSL.
- Keep WebGPU behind `:gpu-renderer`; `:integration-tests:gpu-evidence` must not depend on wgpu4k or import `io.ygdrasil.webgpu`.
- The harness must not own shaders, bind-group layouts, pipeline creation, command encoders, queue submission, raw readback, or shader ABI packing.
- All rendering is headless/offscreen through `prepareSceneFrameSession`; native windowing and Kadre are out of scope.
- A product refusal is evidence only when its exact stable code is observed and runtime submission delta is zero.
- `Unavailable` never passes a correctness or performance promotion gate.
- Every generated, failed, promoted, and performance report goes below `reports/gpu-renderer/evidence/`; no new report is written directly under `reports/gpu-renderer/` or into a module `build/reports/` tree.
- Correctness uses `reports/gpu-renderer/evidence/correctness/{generated,promoted}/`; performance uses `reports/gpu-renderer/evidence/performance/{generated,promoted}/`.
- Preserve failure diagnostics and partial generated artifacts; never overwrite a promoted snapshot from a normal render task.
- Do not add font, codec, path, clip, or text substitutes to grow the initial catalog. Missing product routes stay absent or refuse explicitly.
- End every task below with its focused verification and a small commit. Do not combine Task 8 (cutover) with Task 9 (performance).

## Delivery Map

| Gate | Result | May proceed when |
|---|---|---|
| 1 | Shadow module and frozen legacy inventory | Boundary and freeze tests pass |
| 2 | No speculative product API | `gpu-renderer` stays unchanged until a production consumer requires a seam |
| 3 | Typed scene/result contracts and expectation gate | Full decision matrix passes without GPU |
| 4 | Comparator, evidence schema, writer, verifier | Artifact round-trip and tamper tests pass |
| 5 | Canonical product runner and 3-scene bootstrap | Eligible GPU produces three valid bundles |
| 6 | Curated four-case catalog | All code-backed render and refusal scenes execute through the product route |
| 7 | Headless verification, GPU promotion, and rebaseline policy | Promoted bundles verify from a clean checkout |
| 8 | Atomic legacy deletion | No executable legacy dependency remains; relevant build passes |
| 9 | Separate performance lane | Hardware eligibility and measurement semantics pass |

---

## Task 1: Establish the shadow module and freeze the legacy surface

**Files:**

- Modify: `settings.gradle.kts`
- Modify: `.gitignore`
- Create: `integration-tests/gpu-evidence/build.gradle.kts`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/GpuEvidenceMarker.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/boundary/GpuEvidenceArchitectureBoundaryTest.kt`
- Create: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/LegacySceneFreezeTest.kt`
- Create: `gpu-renderer-scenes/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/LegacyCutoverInventoryTest.kt`
- Create then check in during the shadow period: `gpu-renderer-scenes/legacy-cutover-inventory.json`

### Contract to establish

The new project exists while `:gpu-renderer-scenes` remains included. It has no dependency on the legacy project or the backend library. The legacy catalog remains exactly 88 uniquely identified entries, no new renderer/helper source is added, and every entry is assigned one cutover disposition: `covered`, `duplicate`, `historical`, `dependency-gated`, `unsupported`, or `future-candidate`.

- [ ] Add the failing architecture test first. It must inspect `integration-tests/gpu-evidence/build.gradle.kts`, `src/main`, and `src/test`, and reject these patterns:

```kotlin
private val forbidden = mapOf(
    "direct backend dependency" to Regex("wgpu4k|io\\.ygdrasil\\.webgpu"),
    "legacy module dependency" to Regex("project\\(\"?:gpu-renderer-scenes\"\\)"),
    "legacy source import" to Regex("org\\.graphiks\\.kanvas\\.gpu\\.renderer\\.scenes"),
    "direct target allocation" to Regex("\\.createOffscreenTarget\\("),
    "direct encoding" to Regex("\\.encode(?:OffscreenTexture)?\\("),
    "scene-owned WGSL" to Regex("@(vertex|fragment|compute)|@group\\("),
)

@Test
fun `gpu evidence has no second renderer boundary`() {
    val projectRoot = File(".").canonicalFile
    val text = buildList {
        add(projectRoot.resolve("build.gradle.kts").readText())
        projectRoot.resolve("src").walkTopDown()
            .filter {
                it.isFile &&
                    it.name != "GpuEvidenceArchitectureBoundaryTest.kt" &&
                    it.extension in setOf("kt", "kts", "wgsl")
            }
            .forEach { add(it.readText()) }
    }.joinToString("\n")
    forbidden.forEach { (label, pattern) ->
        assertFalse(pattern.containsMatchIn(text), "$label crossed the gpu-evidence boundary")
    }
}
```

- [ ] Run the focused test before creating the module and observe project/task resolution failure:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*GpuEvidenceArchitectureBoundaryTest'
```

Expected: Gradle reports that `:integration-tests:gpu-evidence` does not exist.

- [ ] Add `include(":integration-tests:gpu-evidence")` immediately after the existing integration-test includes, without removing `include(":gpu-renderer-scenes")`.

- [ ] Add only generated evidence subtrees to `.gitignore`; promoted bundles remain trackable:

```gitignore
/reports/gpu-renderer/evidence/correctness/generated/
/reports/gpu-renderer/evidence/performance/generated/
```

- [ ] Create the minimal build file. Do not add `:kanvas`, wgpu4k, coroutines, or the legacy module unless a later product-backed scene proves a concrete need:

```kotlin
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":gpu-renderer"))
    implementation(project(":integration-tests:test-utils"))
    implementation(libs.kotlinxSerialization)
    runtimeOnly(project(":codec:png"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
```

- [ ] Add an empty marker object in the target package so `classes` proves source-set wiring:

```kotlin
package org.graphiks.kanvas.gpu.evidence

internal object GpuEvidenceMarker
```

- [ ] Add `LegacySceneFreezeTest`. Compute a sorted newline-separated list of current catalog IDs, compare its SHA-256 to a checked-in literal, and assert count `88`, uniqueness, and the current expectation split `87 ShouldRender / 1 ShouldRefuse`. Generate the digest once from the current code, review it, and place the exact hex value in the test; the test must not update the value.

- [ ] Add `LegacyCutoverInventoryTest` and generate `legacy-cutover-inventory.json` with one row per catalog ID. Use this reviewed mapping for legacy intents that the curated catalog directly replaces, then apply the closed rules below in order:

```kotlin
enum class LegacyDisposition { Covered, Duplicate, Historical, DependencyGated, Unsupported, FutureCandidate }

val coveredByReplacement = mapOf(
    "solid-card-stack" to "solid-card-stack",
    "linear-gradient-lanes" to "linear-gradient-band",
    "radial-swatch" to "radial-gradient-disc",
    "sweep-disk" to "sweep-gradient-wheel",
    "color-matrix-tint" to "color-matrix-identity",
    "color-matrix-filter" to "color-matrix-grayscale",
    "runtime-effect-color-tile" to "registered-simple-runtime-effect",
    "custom-runtime-effect-unregistered-refusal" to "custom-runtime-effect-unregistered-refusal",
    "blur-radius-ladder" to "separable-blur-rect",
    "cache-frame-budget-strip" to "aggregate-memory-budget-refusal",
)

fun disposition(scene: GPURendererScene<*>): LegacyDisposition = when {
    scene.sceneId.value in coveredByReplacement -> LegacyDisposition.Covered
    scene.expectation is SceneExpectation.ShouldRefuse -> LegacyDisposition.Unsupported
    scene.tags.any { it in setOf(SceneTag.Text, SceneTag.Image) } -> LegacyDisposition.DependencyGated
    listOf("board", "panel", "review", "deck", "bundle", "milestone")
        .any(scene.sceneId.value::contains) -> LegacyDisposition.Historical
    SceneTag.LegacyComparison in scene.tags -> LegacyDisposition.Duplicate
    else -> LegacyDisposition.FutureCandidate
}
```

The JSON row fields are exactly `sceneId`, `legacyExpectation`, `disposition`, and `replacementSceneId` (nullable). The test must fail on an unknown disposition, duplicate ID, missing catalog ID, extra JSON row, or a `covered` row without a replacement ID. The inventory is planning traceability, not rendering evidence.

- [ ] Run the shadow boundary and freeze checks:

```bash
rtk ./gradlew --no-daemon \
  :integration-tests:gpu-evidence:test \
  :gpu-renderer-scenes:test --tests '*LegacySceneFreezeTest' --tests '*LegacyCutoverInventoryTest'
```

Expected: all tests pass, catalog count remains 88, and no WebGPU adapter is requested.

- [ ] Commit only this gate:

```bash
rtk git add .gitignore settings.gradle.kts integration-tests/gpu-evidence gpu-renderer-scenes/legacy-cutover-inventory.json gpu-renderer-scenes/src/test
rtk git commit -m "test: freeze legacy GPU scene surface"
```

---

## Task 2: Defer product API changes until a production consumer needs them

**Files:** None in this foundation slice.

### Contract to establish

The evidence harness must not create a convenience API in `:gpu-renderer` solely for tests. It uses existing code-backed production inputs and routes. If a future evidence scene cannot be expressed without harness-owned ABI packing, omit that positive scene until a real Kanvas or renderer consumer needs and validates a product seam.

- [ ] Confirm this branch has no effective `:gpu-renderer` source or test diff against its base after the speculative registered-uniform payload API is removed.
- [ ] Keep the architecture boundary that forbids shader ABI packing inside `:integration-tests:gpu-evidence`.
- [ ] When a production consumer eventually requires a typed registered-uniform input, land it as a separate consumer-backed change with its own tests before the evidence catalog adopts it.

This deferral is intentional: the current foundation PR establishes evidence contracts, not new renderer behavior.

---

## Task 3: Define typed scene, observation, and expectation contracts

**Files:**

- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/EvidenceSceneContracts.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/SceneProgram.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/gate/EvidenceExpectationGate.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/EvidenceSceneContractsTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/gate/EvidenceExpectationGateTest.kt`

### Contract to establish

There is one closed outcome algebra. Descriptor validation prevents image expectations without an oracle/comparison policy and prevents refusal expectations with image policies. The expectation gate implements every row of the approved matrix and is independent of WebGPU.

- [ ] Write descriptor validation tests for blank/duplicate IDs, non-positive dimensions, invalid tolerance/similarity, a render scene without an image oracle, a refusal scene without `StableRefusal`, and a refusal code that is blank.

- [ ] Write the full expectation matrix as parameterized or explicit tests. At minimum cover render/pass, render/diff-fail, render/refused, render/unavailable, refusal/exact-code/zero-submission, refusal/wrong-code, refusal/nonzero-submission, refusal/rendered, and refusal/unavailable.

```kotlin
@Test
fun `exact refusal passes only before submission`() {
    val descriptor = refusalDescriptor("unsupported.example")

    assertIs<EvidenceVerdict.Pass>(
        gate.evaluate(descriptor, refused("unsupported.example", submissionDelta = 0L)),
    )
    assertIs<EvidenceVerdict.Fail>(
        gate.evaluate(descriptor, refused("unsupported.example", submissionDelta = 1L)),
    )
}
```

- [ ] Run both tests and observe unresolved contract symbols:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests '*EvidenceSceneContractsTest' --tests '*EvidenceExpectationGateTest'
```

- [ ] Implement the stable descriptor model:

```kotlin
@JvmInline value class EvidenceSceneId(val value: String)

sealed interface EvidenceExpectation {
    data object ShouldRender : EvidenceExpectation
    data class ShouldRefuse(val stableReasonCode: String) : EvidenceExpectation
}

sealed interface OraclePolicy {
    data class GeneratedCpu(val oracleId: String, val version: Int) : OraclePolicy
    data class CheckedInPng(
        val resourcePath: String,
        val sha256: String,
        val provenance: String,
    ) : OraclePolicy
    data object StableRefusal : OraclePolicy
}

data class ComparisonPolicy(
    val perChannelTolerance: Int,
    val minimumSimilarityPercent: Double,
    val version: Int,
    val rationale: String,
)

data class EvidenceSceneDescriptor(
    val id: EvidenceSceneId,
    val title: String,
    val purpose: String,
    val width: Int,
    val height: Int,
    val seed: Long,
    val tags: Set<String>,
    val expectation: EvidenceExpectation,
    val oracle: OraclePolicy,
    val comparison: ComparisonPolicy?,
    val requiredCapabilities: Set<String>,
)
```

Enforce lower-kebab-case IDs, nonblank human fields, positive dimensions/version, tolerance `0..255`, similarity `0.0..100.0`, and exact expectation/oracle compatibility in `init`.

- [ ] Implement runner-facing contracts. Scene programs receive observed product facts and a product target identity; they do not receive a backend session, output path, cache, writer, or adapter discovery API:

```kotlin
data class SceneRecordingContext(
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val frameOrdinal: Long,
    val readbackRequestId: GPUReadbackRequestID,
)

sealed interface ScenePreparation {
    data class Recorded(
        val routeId: String,
        val taskList: GPUTaskList,
        val diagnostics: List<String>,
    ) : ScenePreparation
    data class Refused(
        val stableReasonCode: String,
        val message: String,
        val diagnostics: List<String>,
    ) : ScenePreparation
}

fun interface SceneProgram {
    fun prepare(context: SceneRecordingContext): ScenePreparation
}
```

- [ ] Implement observed results and verdicts with defensively owned pixels:

```kotlin
sealed interface SceneObservation {
    val environment: EvidenceEnvironment

    class Rendered(
        rgba: ByteArray,
        val route: RouteEvidence,
        val diagnostics: List<String>,
        override val environment: EvidenceEnvironment,
        val comparison: ImageComparison,
    ) : SceneObservation {
        private val ownedRgba = rgba.copyOf()
        val rgba: ByteArray get() = ownedRgba.copyOf()
    }

    data class Refused(
        val stableReasonCode: String,
        val message: String,
        val submissionDelta: Long,
        val route: RouteEvidence,
        val diagnostics: List<String>,
        override val environment: EvidenceEnvironment,
    ) : SceneObservation

    data class Unavailable(
        val stableReasonCode: String,
        val message: String,
        override val environment: EvidenceEnvironment,
    ) : SceneObservation
}

sealed interface EvidenceVerdict {
    data class Pass(val reason: String) : EvidenceVerdict
    data class Fail(val reason: String) : EvidenceVerdict
    data class Unavailable(val reason: String) : EvidenceVerdict
}
```

Define the observation payloads with explicit field types; no `Map<String, Any>` escape hatch:

```kotlin
data class EvidenceAdapter(
    val summary: String?,
    val vendor: String?,
    val device: String?,
    val architecture: String?,
    val description: String?,
    val isFallbackAdapter: Boolean?,
)

data class EvidenceEnvironment(
    val sourceCommit: String,
    val osName: String,
    val osVersion: String,
    val osArchitecture: String,
    val javaVersion: String,
    val adapter: EvidenceAdapter?,
    val deviceGeneration: Long?,
    val capabilityImplementation: String?,
    val available: Boolean,
)

data class StructuralEventEvidence(
    val kind: String,
    val phase: String,
    val label: String?,
)

data class RouteEvidence(
    val routeId: String,
    val attemptId: String?,
    val furthestPhase: String?,
    val outcome: String,
    val encodedScopeKinds: List<String>,
    val structuralEvents: List<StructuralEventEvidence>,
    val structuralCounters: Map<String, Long>,
    val runtimeTelemetryDelta: GPUBackendRuntimeTelemetry,
)

data class ImageComparison(
    val passed: Boolean,
    val similarityPercent: Double,
    val differingPixels: Int,
    val maxChannelDifference: Int,
    val meanChannelDifference: Double,
    diffRgba: ByteArray,
    val policyVersion: Int,
) {
    private val ownedDiff = diffRgba.copyOf()
    val diffRgba: ByteArray get() = ownedDiff.copyOf()
}
```

- [ ] Implement `EvidenceExpectationGate.evaluate` as an exhaustive `when` over expectation and observation. A rendered result passes only when `comparison.passed` is true. Exact refusal additionally requires `submissionDelta == 0L`. `Unavailable` always yields `EvidenceVerdict.Unavailable`.

- [ ] Run contract, gate, and architecture tests:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests '*EvidenceSceneContractsTest' \
  --tests '*EvidenceExpectationGateTest' \
  --tests '*GpuEvidenceArchitectureBoundaryTest'
```

- [ ] Commit the closed contracts:

```bash
rtk git add integration-tests/gpu-evidence/src
rtk git commit -m "feat: define GPU evidence outcome contracts"
```

---

## Task 4: Build the comparator and versioned evidence writer/verifier

**Files:**

- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/compare/EvidenceComparator.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceArtifactModel.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleWriter.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleVerifier.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/compare/EvidenceComparatorTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleRoundTripTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleTamperTest.kt`

### Contract to establish

`gpu-evidence-v1` bundles are deterministic, complete, tied to one source commit, and independently verifiable. Render bundles always contain `manifest.json`, `gpu.png`, `cpu.png` or `skia.png`, `diff.png`, `stats.json`, `route.json`, `diagnostics.json`, `environment.json`, and `verdict.json`. Refusal bundles omit images and prove the exact reason plus zero submissions. Normal correctness generation writes only to `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/<scene-id>/`.

- [ ] Write comparator tests for identical RGBA, one channel inside tolerance, one outside tolerance, mismatched dimensions/byte count, fully transparent pixels, and deterministic diff bytes. Use `ComparisonUtils.compareRgba`; do not create a second SSIM implementation.

- [ ] Write a render bundle round-trip test and refusal bundle round-trip test in `@TempDir`. Assert exact file sets, JSON schema version, hashes, scene ID, source commit, oracle provenance, route, diagnostics, environment, and verdict.

- [ ] Write tamper tests that independently remove a file, change `sourceCommit`, change `sceneId`, alter a PNG byte, change refusal reason, set submission delta to one, and replace `schemaVersion`. Each must return a typed verification failure, not throw an unclassified exception.

- [ ] Run the tests and observe unresolved comparator/writer/verifier symbols:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests '*EvidenceComparatorTest' \
  --tests '*EvidenceBundleRoundTripTest' \
  --tests '*EvidenceBundleTamperTest'
```

- [ ] Implement the comparator as a narrow adapter over `ComparisonUtils`:

```kotlin
class EvidenceComparator {
    fun compare(
        actual: ByteArray,
        expected: ByteArray,
        width: Int,
        height: Int,
        policy: ComparisonPolicy,
    ): ImageComparison {
        require(actual.size == width * height * 4)
        require(expected.size == actual.size)
        val result = ComparisonUtils.compareRgba(
            actual = actual,
            reference = expected,
            width = width,
            height = height,
            tolerance = policy.perChannelTolerance,
            minSimilarity = policy.minimumSimilarityPercent,
        )
        return ImageComparison(
            passed = result.isPassing,
            similarityPercent = result.similarity,
            differingPixels = result.totalPixels - result.matchingPixels,
            maxChannelDifference = result.maxDiff.max(),
            meanChannelDifference = result.meanDiff.average(),
            diffRgba = result.diffRgba ?: ByteArray(actual.size),
            policyVersion = policy.version,
        )
    }
}
```

- [ ] Implement explicit artifact records. Required manifest fields are:

```kotlin
const val GPU_EVIDENCE_SCHEMA = "gpu-evidence-v1"

data class EvidenceManifest(
    val schemaVersion: String,
    val sceneId: String,
    val expectation: String,
    val observedOutcome: String,
    val sourceCommit: String,
    val generatedAtUtc: String,
    val oracleKind: String,
    val oracleId: String,
    val oracleVersion: Int,
    val files: Map<String, String>, // relative file name -> SHA-256
)
```

`stats.json` carries exact dimensions, color format `rgba8unorm`, interpretation `encoded-premul-srgb`, tolerance, similarity threshold, similarity, differing pixels, max/mean channel difference, and pass. `route.json` carries `routeId`, attempt ID, furthest phase, outcome, encoded scope kinds, structural events/counters, and runtime telemetry delta. `environment.json` carries source commit, OS name/version/architecture, Java version, adapter fields, device generation, capability identity, and availability. `verdict.json` carries expectation, observed outcome, verdict kind, and reason.

- [ ] Implement JSON with `kotlinx.serialization.json` builders and strict parsers. Reject unknown schema versions, missing required keys, wrong primitive types, absolute paths, `..` path traversal, and duplicate logical files. Do not use Markdown as an input or output of the verifier.

- [ ] Implement `EvidenceBundleWriter.writeGenerated` with pure Java NIO, an injected `java.time.Clock`, an explicit repository root, and this canonical destination only: `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/<scene-id>/`. Write through a sibling temporary directory, close every file, calculate SHA-256, verify the completed bundle, then atomically move when supported with a normal move fallback. Generated bundles are disposable and may be replaced; promoted bundles are never writable through this API.

  The filesystem threat model is a trusted developer or CI workspace. Reject traversal, existing symlink components, direct-child writes in `reports/gpu-renderer/` outside `evidence/`, module `build/reports/` destinations, and paths containing `/promoted/`. Do not use FFM, JNI, libc handles, or native-access JVM flags, and do not claim protection from a hostile process racing filesystem changes. On failure, make a best-effort write to `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/_failed/<scene-id>-<attempt-id>/`; the original generation failure remains authoritative and any retention failure is suppressed. Tests inject a fixed clock so identical inputs produce identical JSON and hashes.

- [ ] Implement `EvidenceBundleVerifier.verify(directory, expectedSourceCommit)` to recompute every hash and expectation verdict rather than trusting `verdict.json`. Return `Verified(sceneId, verdict)` or `Invalid(sceneId?, errors: List<String>)`. A serialized `Pass` with reconstructed `Fail` is invalid.

- [ ] Run the full host-independent module suite:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test
```

- [ ] Commit the artifact gate:

```bash
rtk git add integration-tests/gpu-evidence/src
rtk git commit -m "feat: verify GPU evidence bundles"
```

---

## Task 5: Connect the canonical prepared-session route and prove the bootstrap catalog

**Files:**

- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/EvidenceCase.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/BootstrapEvidenceCatalog.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/CpuOracle.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/ReferenceRaster.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/ProductScenePrograms.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GPUPreparedEvidenceExecutor.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCli.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/BootstrapEvidenceCatalogTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GPUPreparedEvidenceExecutorContractTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GPUPreparedEvidenceExecutorSmokeTest.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`

### Contract to establish

The bootstrap catalog has exactly two cases: a product solid rectangle render and unregistered custom runtime-effect refusal with code `unsupported.runtime_effect.custom_wgsl_not_registered`. Positive rendering uses product recorders and the canonical `GPUBackendSession.prepareSceneFrameSession` → `GPUPreparedSceneFrameSession.renderFrame` path. The refusal runs the product `GPUCustomRuntimeEffectExecutor` with an empty registry and proves zero runtime submissions. The registered `SimpleRT` row is omitted: no checked-in production consumer-backed typed input exists, evidence must not pack registered-uniform ABI bytes, and it must not add a `gpu-renderer` API solely for evidence. Adapter absence yields typed `Unavailable`, writes no correctness bundle, and exits nonzero.

- [ ] Write catalog tests asserting exact IDs, uniqueness, deterministic order, dimensions, expectations, oracle compatibility, and the exact runtime-effect refusal code.

```kotlin
@Test
fun `bootstrap catalog is the approved two case gate`() {
    assertEquals(
        listOf(
            "solid-card-stack",
            "custom-runtime-effect-unregistered-refusal",
        ),
        BootstrapEvidenceCatalog.cases.map { it.descriptor.id.value },
    )
}
```

- [ ] Write host-independent executor contract tests with an injected `EvidenceBackendPort`. Prove the call order is `open session`, `prepare program`, `prepare scene frame`, `render frame`, `wait completion`, `close prepared frame`; prove refusal returns before `prepare scene frame`; prove telemetry is sampled before/after; prove timeout, failed completion, missing readback, wrong readback ID, and absent capabilities return typed failure/unavailable results with diagnostics.

- [ ] Add an opt-in smoke test guarded by `GPU_EVIDENCE_SMOKE=1`. Unlike promotion, it may use a JUnit assumption when the variable is absent. When enabled, adapter absence fails the test instead of skipping it. Assert the solid result reaches `GPUFrameStructuralOutcome.Succeeded`, `GPUFrameStructuralPhase.Completed`, includes a readback of `width * height * 4`, and has positive `QueueSubmit` and runtime submission deltas.

- [ ] Run the host-independent tests and observe unresolved catalog/executor symbols:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests '*BootstrapEvidenceCatalogTest' \
  --tests '*GPUPreparedEvidenceExecutorContractTest'
```

- [ ] Implement the closed case/oracle pair:

```kotlin
data class EvidenceCase(
    val descriptor: EvidenceSceneDescriptor,
    val program: SceneProgram,
    val oracle: CpuOracle?,
) {
    init {
        require((descriptor.expectation is EvidenceExpectation.ShouldRender) == (oracle != null))
    }
}

fun interface CpuOracle {
    fun render(width: Int, height: Int): ByteArray
}
```

`ReferenceRaster` is validation-only and must never be imported by `:gpu-renderer` or uploaded as a texture fallback. It rasterizes pixel centers into encoded premultiplied RGBA8 and provides `clear`, `fillRect`, and `srcOver` helpers. It is an independent CPU oracle, not a second GPU/product route.

- [ ] Implement the two scene programs using only product APIs:

```kotlin
object ProductScenePrograms {
    fun solidRects(draws: List<GPUSolidRectFrameResolvedDraw>, budgetBytes: Long = 1L shl 30) =
        SceneProgram { context ->
            when (val recorded = GPUSolidRectFrameRecorder().record(
                GPUSolidRectFrameRecordingRequest(
                    frameId = GPUFrameID(context.frameOrdinal),
                    recordingId = GPURecordingID("gpu-evidence.${context.frameOrdinal}"),
                    capabilities = context.capabilities,
                    deviceGeneration = context.deviceGeneration,
                    target = context.target,
                    targetBounds = context.targetBounds,
                    draws = draws,
                    readbackRequestId = context.readbackRequestId,
                    configuredAggregateBudgetBytes = budgetBytes,
                ),
            )) {
                is GPUSolidRectFrameRecordingResult.Recorded ->
                    ScenePreparation.Recorded("product.solid-rect", recorded.taskList, emptyList())
                is GPUSolidRectFrameRecordingResult.Refused ->
                    ScenePreparation.Refused(
                        recorded.diagnostic.code.value,
                        recorded.diagnostic.message,
                        listOf("${recorded.diagnostic.code.value}: ${recorded.diagnostic.message}"),
                    )
            }
        }

    // Add a registered-uniform program only after a production consumer-backed input API exists.
    fun unregisteredRuntimeEffect(id: GPUCustomRuntimeEffectID): SceneProgram
}
```

Do not implement or locally pack a registered-uniform payload in the evidence harness. Add that positive scene only after a production consumer-backed input API exists. Implement `unregisteredRuntimeEffect` with `GPUCustomRuntimeEffectExecutor` and an empty `GPUCustomRuntimeEffectRegistry`; it must pass no WGSL string and return the executor's observed `reason` unchanged.

- [ ] Implement the production executor with dependency injection around runtime creation, but use these exact product operations in the real port:

```kotlin
val backend = GPUBackendRuntimeFactory.createOrNull()
    ?: return SceneObservation.Unavailable(
        "unavailable.gpu.backend",
        "GPU backend runtime could not create a session.",
        hostEnvironment(sourceCommit),
    )
val capabilities = backend.capabilities
    ?: return SceneObservation.Unavailable(
        "unavailable.gpu.capabilities",
        "GPU backend session did not expose capabilities.",
        environmentOf(backend, sourceCommit),
    )
val before = backend.runtimeTelemetry
val preparation = evidenceCase.program.prepare(recordingContext(backend, capabilities, descriptor))
```

`recordingContext` uses `GPUFrameTargetRef("target.scene")`, zero-origin `GPUPixelBounds` matching descriptor dimensions, a positive monotonically increasing `GPUFrameID`/frame ordinal, and a scene-specific `GPUReadbackRequestID("gpu-evidence.<scene-id>")`.

For `ScenePreparation.Refused`, sample telemetry again and record `submissionDelta = after.submissions - before.submissions`. For `Recorded`, open one `prepareSceneFrameSession(GPUOffscreenTargetRequest(width, height))`, call:

```kotlin
val handle = prepared.renderFrame(
    preparation.taskList,
    GPUSceneFrameOutputRequest.ReadbackRgba(readbackRequestId),
)
val completed = handle.completion.toCompletableFuture().get(30, TimeUnit.SECONDS)
```

Require `Succeeded`, `Completed`, a `GPUSceneFrameOutput.ReadbackRgba` with the same request ID, and exact byte size. Copy all observed structural events/counters, encoded scope kinds, diagnostics, and runtime telemetry delta into `RouteEvidence`. Close the prepared session in `finally`. The CLI owns `GPUBackendRuntimeFactory.dispose()` in its outermost `finally`; it creates one backend session for the requested catalog run and never calls `createOffscreenTarget`.

- [ ] After a successful readback, invoke the case's CPU oracle, compare it through `EvidenceComparator`, construct `SceneObservation.Rendered`, evaluate `EvidenceExpectationGate`, and pass the descriptor, observation, oracle pixels, and reconstructed verdict to `EvidenceBundleWriter`. For a refusal, skip oracle/comparator execution and pass the exact refusal observation through the same gate/writer sequence. Bundles are written only for promotable `Rendered` or exact pre-submission `Refused` observations; `Unavailable` and `ExecutionFailure` write no bundle and exit nonzero. The writer must never accept a caller-supplied verdict without recomputing it.

- [ ] Build the bootstrap cases with exact policies:

| Scene | Size | Product input | Oracle | Comparison |
|---|---:|---|---|---|
| `solid-card-stack` | 64×64 | three opaque `GPUSolidRectFrameResolvedDraw` values | `ReferenceRaster` rect/SrcOver v1 | tolerance 0, similarity 100.0%, policy v1 |
| `custom-runtime-effect-unregistered-refusal` | 16×16 | `GPUCustomRuntimeEffectID("gpu-evidence.unregistered")` against empty registry | stable refusal | exact code, zero submissions |

- [ ] Implement `GpuEvidenceCli` arguments as `--repository-root <absolute-dir> --source-commit <40-hex> [--scene <id>]`. Reject a missing/dirty placeholder commit, relative repository root, unknown scene, or duplicate flag. Derive the output internally as `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/`; callers cannot supply an arbitrary output path. The CLI writes generated bundles only for `Rendered` or exact pre-submission `Refused` observations; `Unavailable` and `ExecutionFailure` write no bundle and exit nonzero.

- [ ] Add `generateBootstrapGpuEvidence` as an opt-in `JavaExec` using the same native JVM flags as the old runner. Require `-PsourceCommit=<40-hex>`, pass the repository root, and write to `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/`; do not run Git from Gradle or Kotlin.

- [ ] Run host tests, then the eligible-GPU bootstrap:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:generateBootstrapGpuEvidence \
  -PsourceCommit=$(rtk git rev-parse HEAD)
```

Expected: two verified bundles: one render pass and one exact refusal with
submission delta zero. If the adapter is unavailable, the second command exits
non-zero and writes no evidence bundle.

- [ ] Commit the canonical runner and bootstrap gate; do not force-add ignored `reports/gpu-renderer/evidence/correctness/generated/` output:

```bash
rtk git add integration-tests/gpu-evidence
rtk git commit -m "feat: prove GPU evidence bootstrap routes"
```

---

## Task 6: Establish the curated code-backed GPU evidence catalog

**Files:**

- Rename: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/BootstrapEvidenceCatalog.kt` → `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt`
- Rename: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/BootstrapEvidenceCatalogTest.kt` → `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalogTest.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/ProductScenePrograms.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SeparableBlurCpuOracle.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/oracle/SeparableBlurCpuOracleTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/CatalogExpectationInvariantTest.kt`
- Modify: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/GpuEvidenceCli.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`

### Contract to establish

The catalog contains exactly four current product-backed cases. It does not claim registered uniforms, gradients, matrices, SimpleRT, path, clip, bitmap, text, color-glyph, stroke, or destination-read support merely because legacy fixtures existed. Those intents remain classified in the temporary inventory until the relevant product routes and dependencies exist.

- [ ] Replace the bootstrap catalog test with an exact ordered-ID test for these four rows:

| # | Scene ID | Size | Program/route | Oracle and fixed policy |
|---:|---|---:|---|---|
| 1 | `solid-card-stack` | 64×64 | `GPUSolidRectFrameRecorder` | CPU rect SrcOver v1; tolerance 0; 100.0% |
| 2 | `separable-blur-rect` | 64×64 | `GPUSeparableBlurRectFrameRecorder`, sigma 3 | independent seven-tap separable convolution v1; tolerance 2; 99.0% |
| 3 | `custom-runtime-effect-unregistered-refusal` | 16×16 | product custom-effect executor | exact `unsupported.runtime_effect.custom_wgsl_not_registered`, zero submissions |
| 4 | `aggregate-memory-budget-refusal` | 16×16 | `GPUSolidRectFrameRecorder`, budget 1 byte | exact `unsupported.frame_memory.aggregate_budget_exceeded`, zero submissions |

Every `ComparisonPolicy.rationale` states whether the policy is exact integer output or permits bounded GPU floating-point rounding. These are initial reviewed values. A failing eligible adapter opens a reviewed rebaseline record in Task 7; it does not edit the policy in the render task.

- [ ] Write blur-oracle unit tests at edges and centers, alpha-zero and alpha-one cases, and a 1D impulse blur whose seven hand-derived weights are symmetric and sum to one. Do not compare CPU code to itself.

- [ ] Write `CatalogExpectationInvariantTest` to run every catalog case through a fake product port with rendered/refused/unavailable outcomes and prove the descriptor/result gate cannot be bypassed by the CLI or writer. Assert each positive route has exactly one non-null oracle and each refusal has none.

- [ ] Run the new tests and observe failures for the eight missing cases/oracles:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests '*GpuEvidenceCatalogTest' \
  --tests '*SeparableBlurCpuOracleTest' \
  --tests '*CatalogExpectationInvariantTest'
```

- [ ] Implement `ProductScenePrograms.separableBlur` with `GPUSeparableBlurRectFrameRecorder`. Its request uses transparent clear, an opaque source rectangle, sigma `3f`, the context target/readback IDs, and the observed capabilities/device generation. Map a recorder refusal to `ScenePreparation.Refused` without rewriting its code.

- [ ] Implement `SeparableBlurCpuOracle` as a validation-only horizontal then vertical convolution over premultiplied floats. Derive the Gaussian weights independently for `SeparableBlurQualityTier.NORMAL.tapCount(3f) = 7`, sample transparent outside the target, and quantize only after the vertical pass. Assert the resulting buffer is never passed into a product recorder.

- [ ] Implement the budget refusal with a valid full-target registered solid draw and `configuredAggregateBudgetBytes = 1L`. Assert the refusal happens while recording and runtime submission delta stays zero.

- [ ] Rename the Gradle task to `generateGpuEvidence` and make it execute the current catalog. Keep `generateBootstrapGpuEvidence` as a temporary alias only until Task 8, where it is removed.

- [ ] Run the entire host suite and full eligible-GPU catalog:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit=$(rtk git rev-parse HEAD)
```

Expected on an eligible adapter: two complete render bundles and two exact refusal bundles. An unavailable adapter remains a nonzero attempt and never creates promotable evidence.

- [ ] Commit the curated code-backed catalog:

```bash
rtk git add integration-tests/gpu-evidence gpu-renderer-scenes/legacy-cutover-inventory.json
rtk git commit -m "feat: establish curated GPU evidence catalog"
```

---

## Task 7: Wire headless verification, explicit promotion, and GPU CI gates

**Files:**

- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCli.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCliTest.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`
- Modify: `build.gradle.kts`
- Create from reviewed eligible-GPU output: `reports/gpu-renderer/evidence/correctness/promoted/<scene-id>/` for every checked-in catalog row

### Contract to establish

Ordinary `check` runs only host-independent tests. `gpuEvidenceCorrectness` requires an eligible GPU, generates all cases below `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/`, and rejects `Fail` and `Unavailable`. `gpuEvidenceVerification` reads checked-in `correctness/promoted/` snapshots without creating a GPU runtime. `pipelinePmBundle` may depend only on the headless verifier. Promotion and rebaseline require an explicit source commit and review metadata.

- [ ] Write promotion tests in `@TempDir`. Reject unverified input, a source-commit mismatch, an unavailable/failing bundle, missing `--reason`, missing `--reviewer`, an existing destination without `--rebaseline`, and rebaseline without old/new comparison metrics. Assert normal generation APIs cannot target the promoted root.

- [ ] Run the test and observe unresolved promotion symbols:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest'
```

- [ ] Implement `VerifyEvidenceCli` with arguments `--root`, optional `--source-commit`, and optional `--allow-historical-commit`. It verifies every checked-in catalog ID, rejects extra scene directories, prints one line per scene, and exits nonzero on invalid, fail, or unavailable evidence. `--allow-historical-commit` requires all bundles to agree on one internally consistent manifest commit but does not require equality with the checkout; use it only for checked-in historical snapshots and PM packaging, never correctness promotion.

- [ ] Implement `PromoteEvidenceCli` to verify a generated root, copy through a sibling temporary directory, record `promotion.json`, and then atomically replace only verified scene directories. `promotion.json` fields are `schemaVersion = gpu-evidence-promotion-v1`, `sceneId`, `sourceCommit`, `promotedAtUtc`, `reviewer`, `reason`, `rebaseline`, and nullable prior/new comparison summaries. In `--all` mode, verify every current catalog source before changing any destination. The CLI accepts a repository root and derives the two canonical trees itself: `reports/gpu-renderer/evidence/correctness/generated/<source-commit>/` and `reports/gpu-renderer/evidence/correctness/promoted/`. It rejects arbitrary source/destination arguments and any canonical path escaping `reports/gpu-renderer/evidence/`.

- [ ] Add module tasks with explicit separation:

```kotlin
val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()
val sourceCommit = providers.gradleProperty("sourceCommit")

tasks.register<JavaExec>("generateGpuEvidence") {
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.runner.GpuEvidenceCliKt")
    doFirst { require(sourceCommit.isPresent) { "sourceCommit with 40 hexadecimal characters is required" } }
    args("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath)
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf("--source-commit", sourceCommit.get())
    })
    outputs.upToDateWhen { false }
    jvmArgs(buildList {
        add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        add("--enable-native-access=ALL-UNNAMED")
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
    })
}

tasks.register<JavaExec>("verifyGeneratedGpuEvidence") {
    dependsOn("generateGpuEvidence", "classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.VerifyEvidenceCliKt")
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf(
            "--root",
            rootProject.layout.projectDirectory
                .dir("reports/gpu-renderer/evidence/correctness/generated/${sourceCommit.get()}")
                .asFile.absolutePath,
            "--source-commit",
            sourceCommit.get(),
        )
    })
}

tasks.register<JavaExec>("verifyPromotedGpuEvidence") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.VerifyEvidenceCliKt")
    args(
        "--root",
        rootProject.layout.projectDirectory
            .dir("reports/gpu-renderer/evidence/correctness/promoted")
            .asFile.absolutePath,
    )
    args("--allow-historical-commit")
}

tasks.register<JavaExec>("promoteGpuEvidence") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.evidence.artifacts.PromoteEvidenceCliKt")
    val reviewer = providers.gradleProperty("promotionReviewer")
    val reason = providers.gradleProperty("promotionReason")
    doFirst {
        require(sourceCommit.isPresent) { "sourceCommit is required" }
        require(reviewer.isPresent && reviewer.get().isNotBlank()) { "promotionReviewer is required" }
        require(reason.isPresent && reason.get().isNotBlank()) { "promotionReason is required" }
    }
    args("--repository-root", rootProject.layout.projectDirectory.asFile.absolutePath)
    args("--all")
    argumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
        listOf(
            "--source-commit", sourceCommit.get(),
            "--reviewer", reviewer.get(),
            "--reason", reason.get(),
        )
    })
}
```

- [ ] Add root aggregate tasks:

```kotlin
tasks.register("gpuEvidenceCorrectness") {
    group = "verification"
    dependsOn(":integration-tests:gpu-evidence:verifyGeneratedGpuEvidence")
}

tasks.register("gpuEvidenceVerification") {
    group = "verification"
    dependsOn(
        ":integration-tests:gpu-evidence:test",
        ":integration-tests:gpu-evidence:verifyPromotedGpuEvidence",
    )
}
```

- [ ] Add only `gpuEvidenceVerification` to `pipelinePmBundle.dependsOn`. Do not add `generateGpuEvidence`, native runtime dependencies, or unpublished native-windowing artifacts. Keep the PM package static/headless.

- [ ] Run the GPU gate on an eligible adapter and review every current image triple and refusal record:

```bash
rtk ./gradlew --no-daemon gpuEvidenceCorrectness -PsourceCommit=$(rtk git rev-parse HEAD)
```

Expected: every current render verdict and refusal verdict passes, with no unavailable row; source commit is identical across bundles.

- [ ] Promote the reviewed current catalog in one invocation. `PromoteEvidenceCli --all` first verifies every current source bundle and only then atomically replaces each exact destination scene. Use one reviewer/reason string for this initial baseline, then verify the promoted root:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence \
  -PsourceCommit=$(rtk git rev-parse HEAD) \
  -PpromotionReviewer="$GPU_EVIDENCE_REVIEWER" \
  -PpromotionReason=initial-product-backed-gpu-evidence
rtk ./gradlew --no-daemon gpuEvidenceVerification
```

Before the promotion command, require the task-specific `GPU_EVIDENCE_REVIEWER` environment variable to contain the actual reviewer identity. An empty value is a promotion error.

- [ ] Prove unavailable hardware cannot promote by running `VerifyEvidenceCli` against the checked-in unavailable fixture from `PromoteEvidenceCliTest` and observing a nonzero exit.

- [ ] Commit tasks and reviewed artifacts together so the promoted source commit recorded in manifests remains auditable. If generating artifacts required a prior code commit, record that exact prior commit; do not rewrite it to the promotion commit.

```bash
rtk git add build.gradle.kts integration-tests/gpu-evidence reports/gpu-renderer/evidence/correctness/promoted
rtk git commit -m "build: gate product-backed GPU evidence"
```

---

## Task 8: Perform the atomic legacy cutover

**Files:**

- Delete: `gpu-renderer-scenes/`
- Delete: `reports/gpu-renderer-scenes/`
- Delete: every tracked child of `reports/gpu-renderer/` except `reports/gpu-renderer/evidence/`
- Delete: `integration-tests/skia-evidence/`
- Modify: `settings.gradle.kts`
- Modify: `gpu-renderer/build.gradle.kts`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/GPURendererPackageBoundaryTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GPUAxisAlignedStrokeRectLowererTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUFrameGatePolicyTest.kt`
- Delete: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/validation/FirstRoutePMEvidenceExport.kt`
- Delete: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/ReadinessDashboardPMEvidenceExport.kt`
- Delete: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/ReadinessDashboardContracts.kt`
- Delete: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidence.kt`
- Delete: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPURendererReadinessDashboardTest.kt`
- Delete: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPhase0BaselineReportTest.kt`
- Delete: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidenceTest.kt`
- Delete: `scripts/validate_gpu_renderer_r6_executed_pm_evidence_bundle.py`
- Delete: `scripts/validate_gpu_renderer_r6_pm_evidence_bundle.py`
- Delete: `scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py`
- Delete: `scripts/test_validate_gpu_renderer_r6_executed_pm_evidence_bundle.py`
- Delete: `scripts/test_validate_gpu_renderer_r6_pm_evidence_bundle.py`
- Delete: `scripts/test_validate_gpu_renderer_r6_promotion_readiness_boundary.py`
- Delete: `scripts/validate_gpu_renderer_m9_readiness_pm_evidence_bundle.py`
- Delete: `scripts/test_validate_gpu_renderer_m9_readiness_pm_evidence_bundle.py`
- Modify if executable references remain: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextRefusalMatrixTest.kt`
- Modify if executable references remain: `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusalTest.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`
- Modify: `build.gradle.kts`

### Contract to establish

This is one reviewable removal commit. Git history retains the deleted code, reports, freeze tests, and cutover inventory. No archive tree is created. `reports/gpu-renderer/evidence/` becomes the sole active GPU renderer report namespace; all dated Markdown, phase-family subtrees, R6/M9 PM bundles, and other legacy material currently placed directly under `reports/gpu-renderer/` are removed. Executable code/build configuration has no dependency on the old module or deleted reports.

- [ ] Before deleting, resolve the exact active references and save the command output in the review notes:

```bash
rtk rg -n 'gpu-renderer-scenes|reports/gpu-renderer-scenes|GpuRendererScene' \
  --glob '*.kt' --glob '*.kts' --glob '*.py' --glob '*.sh' --glob '*.json'
rtk rg --pcre2 -n 'reports/gpu-renderer/(?!evidence/)' \
  --glob '*.kt' --glob '*.kts' --glob '*.py' --glob '*.sh' --glob '*.json'
rtk ./gradlew --no-daemon gpuEvidenceCorrectness -PsourceCommit=$(rtk git rev-parse HEAD)
rtk ./gradlew --no-daemon gpuEvidenceVerification
```

Expected before deletion: the searches produce a closed inventory of the legacy module, old Phase 6 exporters, R6/M9 PM bundle scripts/tasks, named boundary/fixture tests, and temporary compatibility aliases. Review this inventory before deletion. Both replacement gates pass.

- [ ] Remove `include(":gpu-renderer-scenes")` and `include(":integration-tests:skia-evidence")` from `settings.gradle.kts` while retaining `include(":integration-tests:gpu-evidence")`. The removed Skia-evidence project contains only the retired Phase 6 catch-all report generators.

- [ ] Update `GPURendererPackageBoundaryTest` so it scans `../integration-tests/gpu-evidence/src` and enforces the new forbidden patterns from Task 1. Remove assertions whose only purpose was allowing legacy prepared/direct dual routes.

- [ ] Replace fixture provenance/path literals in `GPUAxisAlignedStrokeRectLowererTest` and `GPUFrameGatePolicyTest` with self-contained test provenance such as `gpu-renderer-test-fixture`; do not point them at a new Markdown report. Update the Kanvas/font tests only if the pre-delete search shows executable file reads or assertions tied to the legacy path.

- [ ] Remove the temporary `generateBootstrapGpuEvidence` alias and all Gradle task references owned solely by the old module. Retire `gpuRendererR6FirstRoutePmEvidenceBundle`, `gpuRendererM9ReadinessPmEvidenceBundle`, all `generateGpuPhase6*` report tasks, their root delegates/injectors, and the corresponding exporters/validators/tests listed above. Before deleting `ReadinessDashboardContracts.kt`, verify again with `rtk rg -n 'GPURendererReadinessDashboard' gpu-renderer/src/main` that only the listed exporter remains. Keep `generateGpuEvidence`, `verifyGeneratedGpuEvidence`, `verifyPromotedGpuEvidence`, and the separate root aggregate tasks. Make `pipelinePmBundle` depend only on the new headless `gpuEvidenceVerification` for GPU renderer evidence.

- [ ] Delete the legacy scene/report tree and the old `reports/gpu-renderer/` catch-all contents after confirming their canonical paths are inside the repository. The Git exclude pathspec preserves every tracked file below `evidence/`:

```bash
rtk git rm -r -- gpu-renderer-scenes integration-tests/skia-evidence reports/gpu-renderer-scenes
rtk git rm -r -- 'reports/gpu-renderer/*' ':(exclude)reports/gpu-renderer/evidence/**'
```

Do not delete `reports/gpu-renderer/evidence/` or any `reports/upstream-rebaseline/` material. Inspect the staged deletion and confirm no path under `reports/gpu-renderer/evidence/` is staged before proceeding.

- [ ] Prove executable references are gone. Historical Markdown hits are allowed only outside build/verifier inputs:

```bash
rtk rg -n 'gpu-renderer-scenes|reports/gpu-renderer-scenes|GpuRendererScene' \
  --glob '*.kt' --glob '*.kts' --glob '*.py' --glob '*.sh' --glob '*.json'
rtk rg --pcre2 -n 'reports/gpu-renderer/(?!evidence/)' \
  --glob '*.kt' --glob '*.kts' --glob '*.py' --glob '*.sh' --glob '*.json'
rtk ls reports/gpu-renderer
```

Expected: both searches produce no output, and the directory listing contains only `evidence/`.

- [ ] Run cutover verification:

```bash
rtk ./gradlew --no-daemon \
  :integration-tests:gpu-evidence:test \
  :gpu-renderer:test --tests '*GPURendererPackageBoundaryTest' \
  :kanvas:test \
  gpuEvidenceVerification \
  pipelinePmBundle
```

Expected: no legacy project resolution, all headless checks pass, and `pipelinePmBundle` does not initialize a GPU adapter or native windowing dependency.

- [ ] Inspect the staged change before committing:

```bash
rtk git status --short
rtk git diff --stat --cached
rtk git diff --check --cached
```

- [ ] Commit the atomic cutover:

```bash
rtk git add settings.gradle.kts build.gradle.kts gpu-renderer scripts kanvas/src/test font/gpu-api/src/test integration-tests/gpu-evidence reports/gpu-renderer/evidence
rtk git commit -m "refactor: replace legacy GPU renderer scenes"
```

---

## Task 9: Add the independent hardware-eligible performance lane

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContractsTest.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/performance/PerformanceContracts.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/performance/PerformanceEligibility.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/performance/GpuEvidencePerformanceRunner.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/performance/PerformanceBundleWriter.kt`
- Create: `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/performance/GpuEvidencePerformanceCli.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/performance/PerformanceEligibilityTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/performance/PerformanceStatisticsTest.kt`
- Create: `integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/performance/PerformanceBundleTest.kt`
- Modify: `integration-tests/gpu-evidence/build.gradle.kts`
- Modify: `build.gradle.kts`

### Contract to establish

Performance uses `gpu-evidence-performance-v1`, its own `reports/gpu-renderer/evidence/performance/` subtree, runner, and verdict. It never changes correctness results. Software/fallback adapters remain diagnostic-only. Observed and derived counters are labeled distinctly; unavailable backend/driver facts remain explicitly unavailable rather than inferred.

- [ ] Write product contract tests for structured adapter fields and backward compatibility of `GPUBackendAdapterSummary("summary")`.

- [ ] Extend the product value with nullable defaults and populate only facts exposed by `GPUAdapterInfo`:

```kotlin
data class GPUBackendAdapterSummary(
    val summary: String,
    val vendor: String? = null,
    val device: String? = null,
    val architecture: String? = null,
    val description: String? = null,
    val isFallbackAdapter: Boolean? = null,
    val backend: String? = null,
    val driver: String? = null,
)
```

In `GPUBackendRuntimeNative.kt`, construct this value from `GPUAdapterInfo.vendor`, `device`, `architecture`, `description`, and `isFallbackAdapter`. Leave `backend` and `driver` null unless the runtime exposes them directly. Do not parse them from free-form description text.

- [ ] Write eligibility tests for explicit fallback, summaries containing `llvmpipe`, `swiftshader`, or `software rasterizer` case-insensitively, a hardware adapter, missing adapter facts, and unavailable backend/driver fields. Only the hardware case is eligible.

- [ ] Write deterministic percentile tests over odd/even samples and a performance bundle round-trip/tamper suite. Define p50/p95 as nearest-rank over sorted nanosecond samples; require 10 cold/warmup frames and 90 measured frames by default.

- [ ] Run focused tests and observe missing contracts:

```bash
rtk ./gradlew --no-daemon \
  :gpu-renderer:test --tests '*GPUBackendRuntimeContractsTest' \
  :integration-tests:gpu-evidence:test --tests '*Performance*Test'
```

- [ ] Implement closed performance records:

```kotlin
data class PerformanceConfig(
    val warmupFrames: Int = 10,
    val measuredFrames: Int = 90,
    val gateVersion: Int = 1,
)

enum class MetricSource { Observed, Derived, Unavailable }

data class FrameTimingSummary(
    val sampleCount: Int,
    val p50Nanos: Long,
    val p95Nanos: Long,
    val source: MetricSource,
)

sealed interface PerformanceVerdict {
    data class EligibleMeasurement(val reason: String) : PerformanceVerdict
    data class DiagnosticOnly(val reason: String) : PerformanceVerdict
    data class Unavailable(val reason: String) : PerformanceVerdict
    data class Failed(val reason: String) : PerformanceVerdict
}
```

- [ ] Implement eligibility before measurement. A run is `DiagnosticOnly` when `isFallbackAdapter == true` or the normalized summary contains one of the three software tokens. Missing adapter identity is `Unavailable`. A hardware adapter with nullable backend/driver remains eligible, while those two fields are serialized with `source = Unavailable` and an explicit reason.

- [ ] Implement `GpuEvidencePerformanceRunner` over the same canonical product program/session path as correctness. Run one cold frame, 10 warmup frames, then 90 measured frames using `System.nanoTime` around `renderFrame` through terminal completion. Read back only on the cold validation frame; measured frames use `CurrentFrameCompletionOnly` so readback cost is not mislabeled as frame-render cost. Record separate readback timing and counters.

- [ ] Sample `GPUBackendRuntimeTelemetry` before and after each phase. Serialize submissions, command buffers, render passes, buffer/texture creation, queue writes, uniform slabs, bind groups, and cache telemetry as `Observed`. Any ratio or delta computed by the harness is `Derived`. Missing values are `Unavailable`; no counter from prose or prior reports is accepted.

- [ ] Write generated bundles only under `reports/gpu-renderer/evidence/performance/generated/<source-commit>/<scene-id>/`; reviewed performance promotions go only under `reports/gpu-renderer/evidence/performance/promoted/<scene-id>/`. Required files are `manifest.json`, `environment.json`, `eligibility.json`, `timings.json`, `telemetry.json`, `diagnostics.json`, and `verdict.json`. `manifest.json` includes `sourceCommit`, scene ID, cold/warmup/measured counts, gate version, and hashes. Reuse the canonical-path and atomic-write protections from correctness.

- [ ] Add `gpuEvidencePerformance` in the module and root. Require `-PsourceCommit`, keep it out of `check`, `gpuEvidenceCorrectness`, `gpuEvidenceVerification`, and `pipelinePmBundle`:

```bash
rtk ./gradlew --no-daemon gpuEvidencePerformance \
  -PsourceCommit=$(rtk git rev-parse HEAD) \
  -PwarmupFrames=10 \
  -PmeasuredFrames=90
```

Expected on hardware: `EligibleMeasurement` with 90 timing samples and observed submission counters. Expected on llvmpipe/SwiftShader: task completes as a diagnostic capture but the promotion verifier rejects it as hardware performance evidence.

- [ ] Run complete separation verification:

```bash
rtk ./gradlew --no-daemon \
  :gpu-renderer:test \
  :integration-tests:gpu-evidence:test \
  gpuEvidenceVerification
```

Confirm no correctness bundle or promoted correctness verdict changed during the performance run.

- [ ] Commit performance separately:

```bash
rtk git add gpu-renderer/src integration-tests/gpu-evidence build.gradle.kts
rtk git commit -m "feat: measure GPU evidence on eligible hardware"
```

---

## Final Verification

- [ ] Run the host-independent full gate from a clean worktree:

```bash
rtk ./gradlew --no-daemon \
  :gpu-renderer:test \
  :integration-tests:gpu-evidence:test \
  gpuEvidenceVerification \
  pipelinePmBundle
```

- [ ] Run correctness on an eligible GPU and verify every current catalog row:

```bash
rtk ./gradlew --no-daemon gpuEvidenceCorrectness \
  -PsourceCommit=$(rtk git rev-parse HEAD)
```

- [ ] Run performance separately on an eligible hardware adapter:

```bash
rtk ./gradlew --no-daemon gpuEvidencePerformance \
  -PsourceCommit=$(rtk git rev-parse HEAD) \
  -PwarmupFrames=10 \
  -PmeasuredFrames=90
```

- [ ] Prove the removed implementation and catch-all report namespace are not active:

```bash
rtk rg -n 'gpu-renderer-scenes|reports/gpu-renderer-scenes|RectOnlyOffscreenRenderer|UniformPacker' \
  --glob '*.kt' --glob '*.kts' --glob '*.py' --glob '*.sh' --glob '*.json'
rtk rg --pcre2 -n 'reports/gpu-renderer/(?!evidence/)' \
  --glob '*.kt' --glob '*.kts' --glob '*.py' --glob '*.sh' --glob '*.json'
rtk ls reports/gpu-renderer
```

Expected: both searches produce no output and the directory listing contains only `evidence/`. Deleted dated Markdown remains available only from Git history and is not used as verification input.

- [ ] Confirm every positive scene has GPU/oracle/diff/stats/route/diagnostics/environment/verdict evidence, both refusal scenes have exact codes and zero submissions, no unavailable row was promoted, and performance artifacts have a different schema/root/verdict.

- [ ] Run repository hygiene checks:

```bash
rtk git diff --check
rtk git status --short
```

Expected: no whitespace errors and no unreviewed generated `build/` output staged.
