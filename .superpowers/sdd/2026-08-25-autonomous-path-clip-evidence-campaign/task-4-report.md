# Task 4 report — Wave 3 hard `clipPath` scopes

Status: `DONE_WITH_CAPTURE_PENDING`

## TDD / RED

Added `native path clip accepts a direct solid background prefix before its consumers` to
`GPUCorePrimitivePreparedFrameTaskListBuilderTest`. The test uses the prepared-frame model for
the public shape of `drawColor` followed by one hard path clip consumer. Before the production
change it failed with:

```text
unsupported.recording.core_primitive_clip_stencil_mixed_geometry:
The bounded clip-stencil arena cannot share slabs with foreign geometry.
```

The existing regression `native path clip refuses foreign geometry in its bounded shared arena`
remains RED-safe for a foreign packet after the clip scope.

## Production correction

`GPUCorePrimitivePreparedFrameTaskListBuilder.kt` now admits only the Wave 3 subset:

- a direct, un-clipped, solid `FillRect` prefix;
- one native non-AA path stencil producer;
- one or two solid non-AA `FillRect` stencil consumers in source order.

The task list emits the direct prefix before the D24S8 producer, then the bounded consumer scope.
The existing attachment continuity, frame-local resource ownership, dependency/use-token
ordering, and producer/consumer packet authorities remain intact. Mixed geometry after the clip,
AA scopes, path/RRect/DRRect consumers, and other non-admitted forms remain fail-closed under the
existing mixed-geometry diagnostic.

## Verification

All commands were run with `rtk`:

- `./gradlew :gpu-renderer:test --tests ...GPUCorePrimitivePreparedFrameTaskListBuilderTest`
  — PASS (all focused builder tests, including the new RED→GREEN test).
- `./gradlew :gpu-renderer:test --tests ...GPUWgpu4kClipStencilPreparedFrameSmokeTest`
  — PASS (native producer/two-consumer smoke).
- `./gradlew :kanvas:test --tests ...GPUFramePathApiInventoryTest`
  — PASS (all inventory tests).
- `git diff --check` — PASS.

No native evidence capture, catalog source, oracle, promotion, push, PR, or merge was performed
in this task; those remain capture/promotion work for the parent Wave 3 flow.

## Commit

Production correction and focused prepared-frame tests:

```text
edb7b0cd7 fix(gpu): partition hard path clip scopes
```

## Concerns / capture gate

The public `KanvasSurfaceProgram` scenes and independent CPU oracle still need to be added and
captured on this clean commit. Capture must confirm the required producer-before-consumer order,
single D24S8 resource, one submission, exact 64×64 pixel counts, and zero fallback before any
promotion.

## Sol review round 1 / TDD follow-up

Sol identified three admission gaps. RED regressions were added first for two background
`FillRect` prefixes, destination-read/layer consumers, transformed clip provenance, and an AA
scope mixed with an un-clipped background. The existing foreign-geometry, difference, nesting,
RRect/DRRect, and filter/layer refusal coverage was retained.

The follow-up correction now:

- carries capture-time `transformClass` through `ClipStack.PathOp`, clip coverage transport,
  content keys, and `StencilCoverage`; non-identity native hard path clips refuse with
  `unsupported.recording.core_primitive_clip_stencil_transform`;
- rejects `ShaderBlendWithDstRead` and `LayerCompositeBlend` consumers with
  `unsupported.recording.core_primitive_clip_stencil_consumer`;
- requires exactly one direct no-clip solid `FillRect` prefix and still allows only one or two
  direct opaque consumers;
- preserves picture-replay transform provenance and rejects the public translated path scene with
  `unsupported.clip.path_transform`.

Follow-up verification:

- focused builder suite: PASS, 70 tests;
- `GPUClipCoverageSurfaceTest`: PASS, including public transformed hard clip refusal;
- `GPUClipCoverageContractsTest`: PASS;
- `git diff --check`: PASS.

## Sol review round 2 / TDD follow-up

Two additional RED regressions were added before the production edits:

- `native path clip refuses destination read and layer background prefixes` first exposed that
  the direct background prefix was not independently guarded against destination reads and
  layer composition (the pre-fix result was the generic
  `unsupported.recording.core_primitive_clip_stencil_mixed_geometry` refusal);
- `roundtrip preserves transformed path clip provenance` first observed that picture replay
  silently restored an affine path clip as `identity` (the pre-fix assertion was
  `expected <affine>, actual <identity>`).

The correction now rejects `ShaderBlendWithDstRead` and `LayerCompositeBlend` on the one allowed
direct prefix with `unsupported.recording.core_primitive_clip_stencil_prefix`. Picture format 7
serializes `ClipStackOp.PathOp.transformClass`; readers of formats 1–6 retain the historical
identity default, while format 7 preserves non-identity provenance so transformed hard clips
remain fail-closed after serialization and replay.

Round 2 verification:

- `:gpu-renderer:test --tests ...GPUCorePrimitivePreparedFrameTaskListBuilderTest` — PASS (70
  tests, including the new prefix guards);
- `:kanvas:test --tests org.graphiks.kanvas.picture.PictureTest` — PASS;
- focused public/native clip smoke and API inventory suites — PASS;
- `git diff --check` — PASS.

## Catalog/oracle follow-up / TDD

The independent oracle RED test was added before its production source. The initial focused
compile failed because `SurfaceSrgbClipPathCpuOracle` did not exist. The GREEN implementation
uses only literal polygon contours, an explicit winding-number test with point-on-segment
membership, and ordered opaque half-open rectangles; it does not import Kanvas `Path`, GPU clip
plans, tessellation, or WGSL. Its regressions lock the required counts and edge/notch samples:

- triangle orange: `1128` pixels;
- concave blue: `1920` pixels, with the notch clear;
- two bands: blue `852`, orange `276`, with the `x=32` half-open band boundary checked.

The catalog now contains exactly the three public `KanvasSurfaceProgram` scenes required by the
brief. Each records `drawColor`, `save`, one identity hard winding `clipPath` intersect, one or
two opaque non-AA rectangle consumers in order, and `restore`. Contract tests verify the literal
clip operation, route `kanvas.surface.render`, no-AA policy, oracle identity, exact comparison
policy, and the `36 = 34 rendered + 2 refused` inventory.

Catalog verification:

- `SurfaceSrgbClipPathCpuOracleTest` — PASS (4 tests);
- catalog, oracle, invariant, and architecture-boundary focused suites — PASS;
- `git diff --check` — PASS.

The catalog source is capture-pending only; no evidence was generated, promoted, or modified in
the evidence report directories.

Final catalog verification after the source commit:

- `:integration-tests:gpu-evidence:test --rerun-tasks --no-build-cache --console=plain` — PASS
  (245 tests, 1 skipped).
- `git diff --check` — PASS.
- Worktree is clean at the catalog commit; native capture remains intentionally pending.

## Capture gate — blocked

At source commit `816d0879a06a958f27e8a24f1e1716ae7cb5f40b`, the first required native public
capture was attempted with:

```text
:integration-tests:gpu-evidence:generateGpuEvidence
  -PsourceCommit=816d0879a06a958f27e8a24f1e1716ae7cb5f40b
  -Pscene=clip-path-triangle-solid
```

The public `kanvas.surface.render` route failed before submission during preflight with the exact
diagnostic:

```text
invalid.preflight.core_primitive_clip_producer_authority
```

No scene bundle was accepted, and the remaining two captures, full 36-scene generation, and
`verifyGenerated` gate were intentionally not run. No production change was made after the
catalog commit; capture is blocked pending investigation of the producer-authority mismatch.

## Native public capture root-cause follow-up — blocked

The public Surface RED regression was added first in
`GPUClipCoverageSurfaceTest.public drawColor hard path clip renders through one stencil scope`.
Before the correction it reproduced the exact producer-authority refusal. The first causal
mismatch was target-format provenance: public Surface uses `RGBA8UnormSrgb`, while the late
producer-authority check reconstructed the default `RGBA8Unorm` target state and structural
color format. The preflight correction now derives both from the prepared target descriptor.

That exposed the next public-only boundary: the approved direct opaque background prefix is an
`Empty` clip scope, but native operand planning/materialization previously assumed every render
scope in the prepared clip frame was a producer or consumer. The narrow correction now carries
one prefix through the exact bridge/key, command, slab-size, scope-order, and semantic-operation
contracts. The first RED at this boundary was
`Native payload operand keys must exactly describe each typed native operand`; the diagnostic
showed prefix declared `[target,pipeline,bind-group,vertex,index]` while its scope keys stopped
at `[target,pipeline,bind-group]`. Prefix geometry is now explicitly included in the public
scope key/bridge partition, and the native payload is accepted.

Focused native retry status: the public route reaches submission with zero fatal diagnostics,
and the prefix pixels are retained, but the clip consumer produces no covered pixels (`1128`
expected, `0` observed). The same public hard triangle without the prefix produces exactly `1128`
covered pixels, proving the remaining defect is the prefix-plus-shared-clip-slab native
materialization boundary rather than the independent stencil producer/consumer route. This is
not being hidden in the CPU oracle or bypassed via fallback. Capture remains BLOCKED pending a
minimal production correction for that last native boundary.

### Final attachment/lifetime trace (last normal attempt)

The failing and control routes were compared at the native pass boundary. The prefix route
uses the same borrowed scene-target view as the producer and consumers, stores the prefix
color pass, then enters the producer with the existing D24S8 authority and stencil clear;
the consumer remains stencil-read-only with retained-load and store. The no-prefix control
uses the identical producer/consumer attachment and stencil state and renders `1128` pixels.
The prefix route reaches the same native submission and preserves all `4096` background
pixels, but the producer/consumer sequence yields no covered pixels. Temporary alternatives
for shared slab index/base offsets and prefix stencil attachment initialization did not change
the zero-covered result (the latter correctly failed the exact operand-key contract and was
reverted). No oracle, fallback, shader, or ABI path was changed.

Focused verification after the final attempt:

- `:gpu-renderer:compileKotlin` — PASS;
- `git diff --check` — PASS;
- public Surface RED/GREEN regression — still FAIL: `expected 1128, got 0` covered pixels;
- no capture, generation, promotion, or commit was performed.

Status: **BLOCKED**. The remaining defect is isolated to native execution continuity when a
color-only direct prefix precedes the shared D24S8 clip producer; further progress requires a
new targeted native attachment/command trace or an architecture decision about prefix slab
ownership.

## Native continuity resolution

The final native trace and the local Skia Graphite/Dawn reference converged on one model: the
opaque background, stencil producer, and ordered consumers must share one physical D24S8
`RenderPass`; Dawn does not retain stencil across pass boundaries. The prepared frame therefore
keeps its logical render scopes separate but seals the bounded prefix chain as one native
render-pass segment. The prefix clears color and stencil, the producer writes stencil, and the
consumers retain it read-only.

The correction also packs the prefix geometry and uniform before the clip geometry, shifts
producer/consumer indexed slices accordingly, and addresses consumer uniform slots after that
leading prefix slot. A null clip scissor remains the route-seal authority; it is canonicalized to
the semantic full target only when validating or encoding the actual WebGPU scissor.

Final RED→GREEN checks:

- `GPUClipCoverageSurfaceTest.public drawColor hard path clip renders through one stencil scope`
  — PASS: zero fatal diagnostics and exactly `1128` non-background pixels;
- `GPUWgpu4kClipStencilPreparedFrameSmokeTest.public prepared clip with opaque background
  encodes one D24 pass` — PASS: background, producer, consumers, and exactly one native render
  pass are asserted;
- `GPUCorePrimitiveClipStencilNativeRouteTest` — PASS;
- `GPUCorePrimitivePreparedFrameTaskListBuilderTest` — PASS.

The full `:gpu-renderer:test` run has one known independent failure in
`GPURendererPackageBoundaryTest`; the identical test also fails on `origin/master` with the same
pre-existing package-cycle report. No evidence capture, generation, promotion, commit, or push
has been performed since the correction.

Status: **READY_FOR_CAPTURE**.
