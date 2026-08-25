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
299623222 fix(gpu): partition hard path clip scopes
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
