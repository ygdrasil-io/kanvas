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
