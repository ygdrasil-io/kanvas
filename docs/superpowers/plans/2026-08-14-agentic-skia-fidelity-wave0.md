# Agentic Skia Fidelity Wave 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore trustworthy headless rendering evidence and fix the FP-13 `modecolorfilters`, prepared-session lifecycle, and SVG error-classification regressions with end-to-end CPU/GPU/native proof.

**Architecture:** Wave 0 is split into three source-isolated workstreams. The core-primitive stream keeps the existing full-target destination-read shader ABI, aliases one full-target snapshot across non-overlapping ordered copy lifetimes, and teaches the native materializer to execute every ordered copy/render scope. The lifecycle stream makes device-generation invalidation explicit and preserves terminal errors; the evidence stream produces a versioned JUnit/dashboard delta without changing thresholds.

**Tech Stack:** Kotlin/JVM, Gradle 9.2, JUnit 5, WebGPU via wgpu4k/llvmpipe, Python 3 standard library for evidence reconciliation, Xvfb `DISPLAY=:99`.

**Spec:** `docs/superpowers/specs/2026-08-14-agentic-skia-fidelity-design.md`

## Global Constraints

- CPU remains the Skia-like reference path; WebGPU remains the GPU backend.
- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- `SkRuntimeEffect` remains a compatibility facade over registered Kotlin/WGSL descriptors.
- WGSL parser/IR/generator surprises stop Kanvas work and become minimized `wgsl4k` evidence.
- Unsupported behavior keeps stable diagnostics and is never silently treated as support.
- Do not change the 1 GiB frame-memory budget to hide the regression.
- Do not weaken similarity thresholds, JUnit assertions, or reference artifacts.
- Only the evidence task may commit/publish generated Skia score/dashboard outputs; focused fix agents may run the existing test tasks in disposable worktrees, but they must not stage or commit score/dashboard changes.
- Native Kadre execution is not required for this headless wave.
- The existing worktree modification to `integration-tests/skia/test-similarity-scores.properties` is outside this fresh worktree and must not be copied into it.

---

### Task 1: Add Wave 0 Evidence Reconciliation Tool

**Files:**
- Create: `scripts/gm/reconcile_skia_fidelity_wave0.py`
- Create: `scripts/gm/test_reconcile_skia_fidelity_wave0.py`
- Test inputs: use temporary XML/JSON/properties fixtures created inside the Python unittest; do not add generated PNGs or edit the FP-13 snapshot.

**Interfaces:**
- Consumes current `SkiaGmRunner.xml`, current Skia dashboard `data/gms.json`, current SVG JUnit XML, current score properties, and the frozen FP-13 runner XML as historical context.
- Produces a versioned JSON delta and a Markdown report. It never rewrites an input file.
- CLI shape:

```text
python3 scripts/gm/reconcile_skia_fidelity_wave0.py \
  --skia-runner PATH \
  --dashboard-json PATH \
  --svg-xml PATH \
  --scores PATH \
  --fp13-runner PATH \
  --source-commit SHA \
  --output-json PATH \
  --output-markdown PATH \
  [--check]
```

- Python functions:

```python
def parse_skia_runner(path: pathlib.Path) -> dict:
    return {}

def parse_dashboard(path: pathlib.Path) -> dict:
    return {}

def parse_svg_results(path: pathlib.Path) -> dict:
    return {}

def load_scores(path: pathlib.Path) -> dict[str, float]:
    return {}

def build_delta(inputs: dict, source_commit: str) -> dict:
    return {}

def render_markdown(delta: dict) -> str:
    return ""
```

- JSON top-level fields must be `schemaVersion`, `kind`, `generatedBy`, `sourceCommit`, `policy`, `inputs`, `current`, `crossLaneDelta`, `historicalFp13Delta`, `rows`, and `nonClaims`.

- [ ] **Step 1: Write parser tests with synthetic current and historical inputs**

Create `unittest.TestCase` methods that cover:

```python
def test_classifies_terminal_memory_failure_as_unexpected(self):
    xml = """<testsuite tests="1" failures="1" skipped="0">
      <testcase name="render GM" classname="SkiaGmRunner">
        <failure message="unsupported.frame_memory.aggregate_budget_exceeded"/>
      </testcase>
    </testsuite>"""
    result = parse_skia_runner(write_xml(xml))
    self.assertEqual(result["unexpectedFailures"], 1)
    self.assertEqual(result["rows"][0]["failureCode"],
                     "unsupported.frame_memory.aggregate_budget_exceeded")

def test_keeps_cpu_oracle_and_skia_rows_distinct(self):
    dashboard = {"gms": [{"name": "draw", "similarity": 99.0}]}
    result = build_delta({"dashboard": dashboard, "cpuOracleRows": ["draw"]}, "abc123")
    self.assertEqual(result["rows"]["skia"][0]["referenceKind"], "unknown")
    self.assertEqual(result["rows"]["cpuOracle"][0]["referenceKind"], "cpu-oracle")

def test_svg_terminal_error_is_not_a_skip(self):
    xml = """<testsuite tests="1" failures="1" skipped="0">
      <testcase name="texture-3" classname="SvgIntegrationTest">
        <failure message="failed.surface.prepared.session-close"/>
      </testcase>
    </testsuite>"""
    result = parse_svg_results(write_xml(xml))
    self.assertEqual(result["failures"], 1)
    self.assertEqual(result["skips"], 0)
    self.assertEqual(result["rows"][0]["classification"], "lifecycle-failure")

def test_historical_fp13_is_context_only(self):
    result = build_delta({"fp13": {"tests": 615, "failures": 498}}, "abc123")
    self.assertFalse(result["inputs"]["fp13"]["acceptanceBaseline"])
    self.assertEqual(result["policy"]["readinessDelta"], 0.0)
```

Each test body must create concrete temporary files and assertions; no test source may contain placeholder bodies. The assertions must cover `failed.surface.prepared.session-close`, `modecolorfilters`, missing reference, size mismatch, similarity failure, `TestAbortedException`, and the four known SVG expected-unsupported codes observed in the fresh baseline:

- `unsupported.core_primitive.geometry.invalid`
- `unsupported.material.linear_gradient_capability_missing`
- `unsupported.geometry.path_key_nondeterministic`
- `unsupported.core_primitive.stencil_edge_fan_budget`

- [ ] **Step 2: Run the parser tests and verify the initial failure**

Run:

```bash
python3 -m unittest discover -s scripts/gm -p 'test_reconcile_skia_fidelity_wave0.py' -v
```

Expected: FAIL because the new parser functions and CLI do not exist.

- [ ] **Step 3: Implement the read-only parsers and classification policy**

Use only `argparse`, `hashlib`, `json`, `pathlib`, `re`, `xml.etree.ElementTree`, and `configparser` or a small properties parser. For each testcase retain its name, class, outcome, failure/error message, failure code when present, and whether it is terminal, expected unsupported, missing-reference, size-mismatch, similarity-failure, or lifecycle failure.

Set these policy values in every generated JSON report:

```json
{
  "globalThresholdWeakened": false,
  "scoresDirectlyEdited": false,
  "readinessDelta": 0.0
}
```

`historicalFp13Delta` must mark the FP-13 XML as `acceptanceBaseline: false`. `rows.skia` and `rows.svg` must remain separate. The tool must exit non-zero under `--check` when an input is missing, a testcase has an unclassified failure/error, or a current `failed.surface.prepared.session-close` remains.

- [ ] **Step 4: Run parser tests and the CLI contract check**

Run:

```bash
python3 -m unittest discover -s scripts/gm -p 'test_reconcile_skia_fidelity_wave0.py' -v
python3 scripts/gm/reconcile_skia_fidelity_wave0.py --help
```

Expected: unittest PASS and the help command lists all required input/output flags. The first real report run happens in Task 5 against fresh JUnit/dashboard inputs; the static M86 PM evidence file is never used as a dashboard substitute.

- [ ] **Step 5: Commit the evidence-tool task**

```bash
git add scripts/gm/reconcile_skia_fidelity_wave0.py scripts/gm/test_reconcile_skia_fidelity_wave0.py
git commit -m "test: add Skia fidelity wave reconciliation"
```

---

### Task 2: Deduplicate Core-Primitive Destination Snapshot Planning

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2540-2566,2767-2794,3534-3622,4372-4452`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt` before the helper section at line 2876
- Read-only contract reference: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlannerDestinationContractTest.kt:634-718`

**Interfaces:**
- Preserve `GPUCorePrimitiveDestinationSnapshotPlan.groupIndex` as the ordered grouping index.
- Preserve one `GPUDestinationSnapshotOperation.TextureCopy` and one consumer binding per destination-reading packet.
- Change only the physical `GPUFrameTextureRef`, preparation, and allocation identity for non-overlapping full-target copies. All logical bounds remain `request.targetBounds` because `GPUCorePrimitiveNativeShader.kt:209-221` samples device coordinates from an exact full-target copy.

- [ ] **Step 1: Add a red builder regression test**

Create a direct CorePrimitive frame with at least two destination-reading packets, a `GPUFrameTargetRef` of `512x1024`, and an aggregate budget that is larger than one 2 MiB RGBA8 snapshot but smaller than two. Build it through `GPUCorePrimitivePreparedFrameTaskListBuilder` and then `GPUFramePlanner.plan`.

The test must assert after the fix:

```kotlin
assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(result)
assertEquals(1, destinationSnapshotPreparations.size)
assertEquals(1, destinationSnapshotAllocations.size)
assertEquals(2_097_152L, destinationSnapshotAllocations.single().bytes)
assertEquals(1, snapshotRefs.distinct().size)
assertEquals(2, framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().size)
assertFalse(framePlan.atomicallyRefused, framePlan.diagnostics.joinToString())
```

Before the implementation, the test must fail with either the aggregate budget diagnostic or two physical snapshot preparations. Do not use an increased budget as the expected result.

- [ ] **Step 2: Run the focused builder test to confirm the red state**

Run:

```bash
./gradlew -F off :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilderTest" \
  --no-daemon --no-parallel --console=plain
```

Expected: the new test fails while the existing class tests retain their current baseline status.

- [ ] **Step 3: Give all non-overlapping plans one canonical full-target resource**

In `buildCorePrimitiveDestinationSnapshotPlans`, keep `groupIndex` and per-command labels in diagnostic dumps, but assign the same canonical `GPUFrameTextureRef` to the physical snapshot field for the ordered destination-read plans. Keep every `TextureCopy` operation distinct so the planner retains each copy’s source step, consumer, and lifetime.

Build preparations and memory allocations with `distinctBy { it.resource }` / `distinctBy { it.snapshot }` at the point they are appended. The single preparation must remain:

```kotlin
role = GPUFrameResourceRole.DestinationSnapshot
usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding)
lifetime = GPUFrameResourceLifetime.FrameLocal
descriptor.logicalBounds = request.targetBounds
descriptor.sampleCount = 1
byteSize = width * height * 4
```

Do not alter `configuredAggregateBudgetBytes`, `GPUFrameMemoryBudgetPlanner`, `GPUBlendPlanning`, or the scalar-coverage projection. Retain the existing `GPUFramePlanner` alias validation so an overlapping lifetime still returns `invalid.frame_plan.destination_snapshot_alias`.

- [ ] **Step 4: Run builder and destination-alias contract tests**

```bash
./gradlew -F off :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlannerDestinationContractTest" \
  --no-daemon --no-parallel --console=plain
```

Expected: PASS except for the documented package-boundary baseline when the whole module is run; the alias contract still accepts non-overlap and refuses overlap.

- [ ] **Step 5: Commit the planner task**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt
git commit -m "fix: share core primitive destination snapshots"
```

---

### Task 3: Materialize Ordered Core-Primitive Copy Sequences

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:208-244,690-745,4997-5410,5420-5895,6242-6611`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt:128-150,1042-1224,4604-4810`
- Read-only shader contract: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveNativeShader.kt:209-221,359-385`
- Read-only scope model: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt:437-449`

**Interfaces:**
- Preserve `GPUPreparedNativeScopeOperand.Copy(sourceStepIndex, operationKind, source, destination, textureLayout)` one-to-one with each `GPUFrameStep.CopyDestinationStep`.
- Preserve `GPUPreparedNativeScopeOperand.Render` one-to-one with each render scope.
- Preserve `GPUPreparedNativeFramePayload.scopeOperands` in exact `encoderPlan.scopes` order.
- Use one native `CorePrimitiveDestinationSnapshotHandles` per distinct logical `GPUFrameTextureRef`; multiple non-overlapping copy steps may point at that same handle.

- [ ] **Step 1: Add a red multi-copy materializer test**

Extend the existing test fixture with a direct sequence containing at least three render steps and at least two `CopyDestinationStep` values, using the existing `RouteShape.MultipleDirects` fixture shape or a dedicated `destinationReadCommandIds` parameter. Set all copies to the same full-target logical bounds and the same `GPUFrameTextureRef`, with non-overlapping consumer lifetimes.

Add a test that calls `fixture.materializeCoreResult()` and asserts before the fix:

```kotlin
assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
assertEquals("unsupported.native-core-primitive.destination-copy-shape", result.code)
```

Add the post-fix assertions in the same test:

```kotlin
val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(result)
assertEquals(
    listOf(
        GPUEncoderOperationKind.Render,
        GPUEncoderOperationKind.CopyDestination,
        GPUEncoderOperationKind.Render,
        GPUEncoderOperationKind.CopyDestination,
        GPUEncoderOperationKind.Render,
    ),
    materialized.draft.payload.scopeOperands.map { it.operationKind },
)
assertEquals(1, fixture.native.events.count {
    it == "createTexture:Kanvas.frame.corePrimitive.destinationSnapshot"
})
assertTrue(materialized.draft.disposeBeforeRegistration())
```

Also retain negative tests for copy-after-consumer, mismatched source, duplicate consumers, and overlapping aliases. These must still refuse before any native event.

- [ ] **Step 2: Run the focused materializer test to confirm the red state**

```bash
./gradlew -F off :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest" \
  --no-daemon --no-parallel --console=plain
```

Expected: the new multi-copy test fails at the existing one-copy validation; the current one-copy and two-render tests remain green.

- [ ] **Step 3: Replace global one-copy validation with ordered per-copy validation**

Refactor `validateCorePrimitiveDestinationCopy` into a validation path that:

1. Collects every `CopyDestinationStep` in frame-step order.
2. Requires each copy to have exactly one consumer.
3. Verifies source target, target format, device generation, full-target logical bounds, copy row alignment, frame-local preparation, and `GPUEncoderOperationKind.CopyDestination` scope exactly as today.
4. Resolves the consumer packet to the render step that owns it.
5. Requires each copy to precede its consumer render step.
6. Returns one `CorePrimitiveDestinationCopyAuthority` per copy with its own copy scope.

Single-copy routes may continue to use a one-element selection of this result, preserving current diagnostics. Multi-copy routes must reject a render step with more than one destination snapshot binding requirement because the current core render-run materializer accepts one `dstRead` binding per run.

- [ ] **Step 4: Add a direct multi-render destination-copy sequence lane**

Route any direct CorePrimitive frame with multiple render steps and one or more `CopyDestinationStep` values to a generalized sequence implementation before the existing no-copy split lane. Keep the specialized two-render path and continued path-stencil path only where their additional producer/depth-stencil invariants are required; do not route path-stencil scopes through the direct sequence lane.

The sequence implementation must:

- validate that all render steps target the same prepared scene target;
- reject `CopyResourceStep` and more than one readback step;
- require copy steps to occur between the producer render and their consumer render;
- acquire one `GPUCorePrimitiveRenderRunPlan` per render step;
- create one native snapshot texture/view/sampler per distinct snapshot resource;
- pass the matching snapshot binding to the render run whose packet consumes it;
- emit a `GPUPreparedNativeScopeOperand.Copy` for every copy step, using that step’s `sourceStepIndex`, `logicalBounds`, and exact source/destination texture handles;
- emit one optional readback operand after the final render;
- assemble all lease lifecycles into one `GPUPreparedNativeCompositeFrameLeaseLifecycle`;
- transfer/rollback/quarantine all resources transactionally on success or failure.

The copy layout remains full-target for this wave:

```kotlin
sourceOriginX = targetBounds.left
sourceOriginY = targetBounds.top
destinationOriginX = 0
destinationOriginY = 0
width = targetBounds.width
height = targetBounds.height
```

Do not change `GPUCorePrimitiveNativeShader` or its uniform32/uniform80 ABI in this task. A footprint-bounded snapshot would require an explicit shader origin uniform and is a separate design.

- [ ] **Step 5: Run focused materializer, preflight, and smoke tests**

```bash
./gradlew -F off :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kDestinationCopyFrameSmokeTest" \
  --no-daemon --no-parallel --console=plain
```

Expected: multiple ordered copies materialize with one created snapshot texture when the logical resource is shared; copy order, consumer order, and pixel oracle remain correct; malformed and overlapping shapes refuse before native side effects.

- [ ] **Step 6: Reproduce the GM regression through the real native route**

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test \
  --tests "org.graphiks.kanvas.skia.SkiaGmRunner" \
  -Dkanvas.gm.name=modecolorfilters \
  -Dkanvas.gm.includeBlocking=true \
  --no-daemon --no-parallel --console=plain
```

Expected: both registered classes for logical GM `modecolorfilters` complete without `unsupported.frame_memory.aggregate_budget_exceeded` and without `unsupported.native-core-primitive.destination-copy-shape`. Record the JUnit XML and generated render artifacts for Task 5.

- [ ] **Step 7: Commit the native materialization task**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt
git commit -m "fix: materialize ordered core destination copies"
```

---

### Task 4: Stabilize Prepared Session Generation And SVG Terminal Errors

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt:323-335,417-464,1146-1167`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecutorTest.kt:517-608,1194-1457`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLifetimeStressTest.kt:147-169`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt:70-122`
- Modify: `integration-tests/svg/src/test/kotlin/org/graphiks/kanvas/svg/SvgIntegrationTest.kt:24-74`
- Read-only factory lifecycle reference: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt:917-993`

**Interfaces:**
- Preserve same-generation size/format transitions: the executor still closes an old session and reports `targetCloses = 1`.
- On a device-generation transition, treat the cached session as externally invalidated by the factory disposal; clear the cache without calling `close()` on the old child again and report the new frame as a fresh target creation with `targetCloses = 0`.
- A thrown `backendFactory.open()` is a terminal execution failure with `failed.surface.prepared.backend-open` and `failureClass`; only a returned `null` backend remains `unavailable.surface.prepared.backend`.
- SVG expected skips are an explicit four-code allowlist, not every `IllegalStateException`.

- [ ] **Step 1: Update the generation-transition tests before implementation**

Change the existing `GPUPreparedSurfaceFrameExecutorTest` expectations:

- Rename `device generation transition closes the stale session before creating the new one` to assert that a generation transition invalidates the stale session without a second close.
- Keep same-generation size and format transition assertions unchanged.
- Change `close transition after dispose completes a subsequent frame on a new generation` to assert `reopened.evidence.targetCloses == 0` and the disposed session’s `closeCalls == 1`, not `2`.

Add an executor test where `GPUPreparedSurfaceBackendPortFactory.open()` throws `IllegalStateException("open failed")`; assert `TerminalFailure`, code `failed.surface.prepared.backend-open`, and `facts["failureClass"] == IllegalStateException::class.java.name`.

The existing tests must be red after these expectation changes until production behavior is updated.

- [ ] **Step 2: Implement explicit generation invalidation and backend-open diagnostics**

At the cached-session transition, distinguish:

```kotlin
val generationChanged = cachedKey?.deviceGeneration != null &&
    cachedKey?.deviceGeneration != key.deviceGeneration
```

When `generationChanged` is true, clear `cachedSession`, `cachedKey`, and `cachedTarget` without invoking the old child’s `close()`. When only size, format, or interpretation changes within one generation, retain the current close-and-report behavior. Do not catch a same-generation close failure.

Change the `backendFactory.open()` catch to create a terminal diagnostic with the thrown failure class. Preserve the existing null-backend refusal and all existing preparation, counters, completion, and backend-close diagnostic codes.

- [ ] **Step 3: Narrow SVG expected-unsupported conversion**

In `SvgIntegrationTest`, replace the broad catch policy with an explicit code extractor from `IllegalStateException.message` and this immutable set:

```kotlin
private val expectedUnsupportedCodes = setOf(
    "unsupported.core_primitive.geometry.invalid",
    "unsupported.material.linear_gradient_capability_missing",
    "unsupported.geometry.path_key_nondeterministic",
    "unsupported.core_primitive.stencil_edge_fan_budget",
)
```

Only an exception whose message code is in this set becomes `TestAbortedException`. Rethrow `failed.surface.prepared.session-close`, `failed.surface.prepared.backend-open`, `unsupported.frame_memory.aggregate_budget_exceeded`, and every other unlisted terminal code.

Add a unit-level test helper assertion in `SvgIntegrationTest` or a focused companion test:

```kotlin
assertTrue(isExpectedUnsupportedSvgCode("unsupported.geometry.path_key_nondeterministic"))
assertFalse(isExpectedUnsupportedSvgCode("failed.surface.prepared.session-close"))
assertFalse(isExpectedUnsupportedSvgCode("unsupported.frame_memory.aggregate_budget_exceeded"))
```

- [ ] **Step 4: Run lifecycle and SVG tests**

```bash
./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameExecutorTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceLifetimeStressTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest" \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test \
  --no-daemon --no-parallel --console=plain
```

Expected: focused lifecycle tests pass; SVG’s four known unsupported codes remain skipped, `texture-3` remains a similarity failure or passes based on actual pixels, and no lifecycle terminal is converted to a skip.

- [ ] **Step 5: Repeat the native lifetime probes without masking errors**

```bash
for i in $(seq 1 3); do
  DISPLAY=:99 ./gradlew -F off :kanvas:test \
    --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceLifetimeStressTest" \
    --no-daemon --no-parallel --rerun-tasks --console=plain || exit 1
done

DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" \
  --no-daemon --no-parallel --rerun-tasks --console=plain
```

Expected: zero `failed.surface.prepared.session-close` in the XML from the three stress runs and the ordered blend matrix. If native teardown still fails, retain the terminal diagnostic and stop this task instead of adding a retry or catch.

- [ ] **Step 6: Commit the lifecycle task**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecutorTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceLifetimeStressTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt integration-tests/svg/src/test/kotlin/org/graphiks/kanvas/svg/SvgIntegrationTest.kt
git commit -m "fix: preserve prepared session lifecycle diagnostics"
```

---

### Task 5: Generate Current Wave 0 Evidence And Regression Report

**Files:**
- Create: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-gm-runner.xml`
- Create: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-dashboard-gms.json`
- Create: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/svg-integration.xml`
- Create: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-delta.json`
- Create: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-reconciliation.md`
- Do not modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-13-close-bounded-native-rendering-gaps-evidence.md`
- Do not modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`

**Interfaces:**
- Inputs are immutable copies of fresh current-run XML/JSON plus hashes of the checked-in score store and the frozen FP-13 XML.
- The JSON is produced by Task 1 and must have `schemaVersion: 1` and `kind: "skia-fidelity-wave-0-delta"`.
- The Markdown report links exact commands, environment facts, input hashes, current counts, historical counts, and non-claims.

- [ ] **Step 1: Run the focused end-to-end regressions and preserve their outputs**

Run and retain the JUnit outputs:

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test \
  --tests "org.graphiks.kanvas.skia.SkiaGmRunner" \
  -Dkanvas.gm.name=modecolorfilters \
  -Dkanvas.gm.includeBlocking=true \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test \
  --no-daemon --no-parallel --console=plain
```

Copy the resulting XML files into the dated input directory. Copy the current dashboard JSON only after:

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:generateSkiaDashboard \
  --no-daemon --no-parallel --console=plain
```

Use the actual generated path `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.

Copy exactly these three fresh inputs:

```bash
mkdir -p reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs
cp integration-tests/skia/build/test-results/test/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml \
  reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-gm-runner.xml
cp integration-tests/svg/build/test-results/test/TEST-org.graphiks.kanvas.svg.SvgIntegrationTest.xml \
  reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/svg-integration.xml
cp integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json \
  reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-dashboard-gms.json
```

- [ ] **Step 2: Run the full relevant headless suites as separate evidence commands**

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-daemon --no-parallel --console=plain
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-daemon --no-parallel --console=plain
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test --no-daemon --no-parallel --console=plain
DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test --no-daemon --no-parallel --console=plain
```

Record process exit status and JUnit counts separately. The known package-boundary failure, known Kanvas UNORM 1-LSB failure, expected Skia refusal inventory, and SVG `texture-3` similarity failure must remain individually named.

- [ ] **Step 3: Generate the JSON delta and Markdown report**

Run:

```bash
python3 scripts/gm/reconcile_skia_fidelity_wave0.py \
  --skia-runner reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-gm-runner.xml \
  --dashboard-json reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-dashboard-gms.json \
  --svg-xml reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/svg-integration.xml \
  --scores integration-tests/skia/test-similarity-scores.properties \
  --fp13-runner reports/upstream-rebaseline/graphite-dawn-frame-plan/fp13-m86-wave/junit-xml-2026-08-13/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml \
  --source-commit "$(git rev-parse HEAD)" \
  --output-json reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-delta.json \
  --output-markdown reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-reconciliation.md \
  --check
```

The report must state:

- whether both `modecolorfilters` cases render or have a new explicit refusal;
- whether the old aggregate-budget and one-copy materializer codes disappeared;
- whether `failed.surface.prepared.session-close` appears in any current XML;
- whether SVG `texture-3` is a pixel failure or a lifecycle terminal;
- current Skia runner totals versus dashboard non-blocking totals;
- current SVG pass/fail/skip totals;
- the 2026-08-13 FP-13 snapshot as historical context only;
- no readiness movement from this stabilization wave;
- CPU-oracle rows are not counted as Skia fidelity.

- [ ] **Step 4: Review generated artifacts and score-file ownership**

Run:

```bash
git status --short
git diff --check
git diff -- integration-tests/skia/test-similarity-scores.properties
git diff --name-only -- integration-tests/skia/src/test/resources/generated-renders
```

The score file may contain runner-produced values/timestamp changes, but no manual edits are allowed. If a score changed for an unrelated GM, stop and re-run the focused/full evidence in a disposable worktree before accepting the report.

- [ ] **Step 5: Commit the dated evidence**

```bash
git add reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-delta.json reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-reconciliation.md integration-tests/skia/test-similarity-scores.properties
git commit -m "docs: reconcile Skia fidelity wave 0 evidence"
```

---

### Task 6: Wave Gate Review And Integration

**Files:**
- Read: all commits from Tasks 1-5
- Read: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-delta.json`
- Read: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-reconciliation.md`
- Create: `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-review.md`

**Interfaces:**
- The reviewer consumes source diffs, focused test logs, current JUnit XML, the dashboard JSON, and the wave delta.
- The review result is `approved` only when all Wave 0 gates pass; otherwise it is `blocked` with exact failure codes and no threshold changes.

- [ ] **Step 1: Inspect all intended changes and verify ownership boundaries**

Run:

```bash
git status --short
  git diff --stat f2e68b895..HEAD
  git diff f2e68b895..HEAD --check
git log --oneline -6
```

Confirm no fix agent edited generated renders, no task modified the FP-13 historical report, and no task changed `GPUBlendPlanning`, the frame budget constant, or global similarity thresholds.

- [ ] **Step 2: Re-run the final targeted probes**

```bash
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test \
  --tests "org.graphiks.kanvas.skia.SkiaGmRunner" \
  -Dkanvas.gm.name=modecolorfilters \
  -Dkanvas.gm.includeBlocking=true \
  --no-daemon --no-parallel --rerun-tasks --console=plain

DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test \
  --tests "org.graphiks.kanvas.svg.SvgIntegrationTest" \
  --no-daemon --no-parallel --rerun-tasks --console=plain
```

Use the exact dated-input invocation from Task 5 again against these final XML files. A current `failed.surface.prepared.session-close`, aggregate-budget refusal, or one-copy shape refusal blocks approval.

- [ ] **Step 3: Write the review outcome**

The review file must include:

```markdown
status: approved
wave: 0
modecolorfilters: rendered-through-native-route
session-close: absent-from-targeted-final-xml
svg-terminal-masking: fixed
thresholds-weakened: false
known-baselines: listed-individually
```

If any value is false or unknown, write `status: blocked`, name the exact JUnit/test diagnostic, and do not claim Wave 0 completion.

- [ ] **Step 4: Commit the review**

```bash
git add reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-review.md
git commit -m "docs: review Skia fidelity wave 0 gate"
```

## Parallel Dispatch Order

1. Dispatch Task 1 to one `general` agent and Task 2 to one `general` agent in parallel after assigning their exclusive files.
2. Dispatch Task 4 to one `general` agent in parallel with Tasks 1 and 2; it owns only the lifecycle/SVG files listed in Task 4.
3. Dispatch Task 3 only after Task 2’s planner commit is reviewed, because the native materializer consumes the planner’s shared-resource shape.
4. Run Task 5 serially after Tasks 1-4 are integrated; only this task owns current generated evidence and the score file.
5. Run Task 6 serially as an independent review. Do not dispatch two agents that edit `GPUCorePrimitivePreparedFrameTaskListBuilder.kt`, `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`, or the score/dashboard artifacts concurrently.

## Final Verification Command Set

```bash
python3 -m unittest discover -s scripts/gm -p 'test_reconcile_skia_fidelity_wave0.py' -v
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-daemon --no-parallel --console=plain
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-daemon --no-parallel --console=plain
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test --no-daemon --no-parallel --console=plain
DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test --no-daemon --no-parallel --console=plain
python3 scripts/gm/reconcile_skia_fidelity_wave0.py \
  --skia-runner reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-gm-runner.xml \
  --dashboard-json reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/skia-dashboard-gms.json \
  --svg-xml reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-inputs/svg-integration.xml \
  --scores integration-tests/skia/test-similarity-scores.properties \
  --fp13-runner reports/upstream-rebaseline/graphite-dawn-frame-plan/fp13-m86-wave/junit-xml-2026-08-13/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml \
  --source-commit "$(git rev-parse HEAD)" \
  --output-json reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-delta.json \
  --output-markdown reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-reconciliation.md \
  --check
```

The final report command uses only the dated input paths created by Task 5.
