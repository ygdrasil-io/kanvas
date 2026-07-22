# Task 10 native frame execution report

## Pinned dependency

- wgpu4k snapshot: `0.2.0-20260716.235022-2`
- wgpu4k revision: `9a35a17effbfbe33ddc436052664ef8651d3fc52`
- publication job: <https://github.com/wgpu4k/wgpu4k/actions/runs/29542588206/job/87767701438>
- publication result: success

## Slice 10C-5b evidence

`GPUWgpu4kSolidRectFrameSmokeTest` executes the production prepared-frame route with:

- one 4x4 `RGBA8Unorm` target;
- one canonical 64-byte solid-rectangle block, with color at byte offset 32;
- one premultiplied red 2x2 rectangle covering `[1,1)-[3,3)`;
- one encoder, one render pass, one draw, one `copyTextureToBuffer`, one finish, and one queue submit;
- `bytesPerRow=256`, `rowsPerImage=4`, a 1024-byte physical staging buffer, a 784-byte mapped WebGPU minimum, and a final 64-byte tightly packed RGBA result;
- queue completion before one public `mapAsync(Read)`, row depadding while mapped, guaranteed unmap, then output-owned release.

Focused command:

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSolidRectFrameSmokeTest'
```

Observed result: `BUILD SUCCESSFUL`; the native pixel assertion and exact structural counters pass.

## wgpu4k issue found

The published facade exposes `GPUCommandEncoder.label`, but setting it calls
`_wgpuCommandEncoderSetLabel`, which reaches `src/unimplemented.rs:47` and panics with
`not implemented`. Because the panic crosses a non-unwinding native boundary, the Gradle test JVM
aborts with exit code 134.

Kanvas does not call this optional diagnostic setter on the production frame path. The validated
label remains in Kanvas telemetry/token identity. No other native setter or execution failure is
masked by this exception.

## Slice 10D pilot evidence

The pilot validates one bounded destination-read sequence on the production prepared-frame route:

- render the background into the canonical target;
- copy source bounds `[1,1)-[3,3)` into snapshot origin `(0,0)` with public wgpu4k
  `copyTextureToTexture` on the active encoder;
- render the exact declared consumer with a destination-reading blend and an unfiltered
  `textureLoad` whose integer coordinates subtract the logical source origin;
- copy the final target to the output-owned staging buffer with `copyTextureToBuffer`;
- finish and submit the single command buffer once.

The logical snapshot is 2x2 while its scratch-pool backing is 16x16. The consumer uniform carries
the source logical origin and the inverse physical backing dimensions. The pixel oracle expects blue
outside the copied bounds and magenta inside them; sampling the backing as if the logical origin
were `(0,0)` therefore fails the test.

The materializer refuses ambiguous consumers, packet/semantic mismatches, blends that do not read
the destination, and source bounds outside the target before creating any native resource. The
prepared allocation evidence comes from the accepted scratch lease, not from labels or inferred
sizes. Rollback leaves no pending pool or native-payload ownership.

This follows the useful Graphite/Dawn shape without porting either backend: the copy is a recording
boundary outside a render pass, remains on the same encoder, uses an approximate scratch backing,
and is submitted with the rest of the frame. Texture-to-texture geometry is expressed only through
source/destination origins and extent; row layout remains exclusive to the final
texture-to-buffer readback.

Focused commands:

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kDestinationCopyFrameSmokeTest'
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kDestinationCopyFrameSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSolidRectFrameSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest' --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanIntegrityTest'
```

Observed result: both commands report `BUILD SUCCESSFUL`; native pixels, exact copy geometry,
single-submit counters, refusal-before-creation, ownership, plan integrity, and package boundaries
all pass. A separate focused batch also passes every `GPUFrameExecutorTest` and
`GPUConcreteResourceProviderTest`.

### Explicit non-claim

This remains a closed solid-rectangle route, not the complete general destination-read route. The
consumer shader is now assembled from `GPUBlendFormulaLibrary` instead of duplicating a
`difference@v1` formula. Every canonical full-coverage formula parses through wgsl4k, and native
prepared-session evidence executes the 14 advanced modes selected for destination reads against an
independent CPU oracle with at most one RGBA level of tolerance. The planner splits a render task and
places every planned snapshot
operation immediately before its exact first consumer packet. The prepared-session dispatcher also
selects this route before the plain solid materializer, borrows the session's canonical native
texture/view without taking ownership, and submits the destination copy and final readback once.
Native session evidence proves exact pixels, one target creation/use, one submit, one readback copy,
zero active payloads after completion, and target close only when the session closes.

The prepared route also executes several conservative snapshot groups in one frame. Native evidence
uses an opaque blue direct `Src` packet followed by two bounded `Difference` consumers over the same
2x2 region. The planner produces `render -> copy -> render -> copy -> render -> readback`; the
materializer binds two distinct prepared snapshot allocations and the second consumer observes the
first consumer's result. The final center is white, with one target use, one encoder, one command
buffer, one submit, and one readback.

Snapshot backings are now owned by the serialized prepared-session cache. The cache key is the
ordered list of physical backing extents produced by prepared scratch leases; it never replaces
logical bounds or budget evidence. A stable one-snapshot topology creates one native texture/view
and reuses it across all 14 advanced-mode frames. The two-consumer topology creates exactly two
backings. A topology change closes the old set before creating the new one, and session close owns
the final native teardown.

The remaining 10D work is to broaden mixed direct packets beyond exact full-coverage `Src` and bind
the separately prepared scalar/LCD coverage resource topology. No global 10D completion or
performance claim is made here.

## Slice 10E persistent MSAA continuation evidence

The production prepared-frame route now executes one color-only, frame-local
4x MSAA attachment across several encoder scopes without recreating it. The E1
native pixel smoke starts from a real `GPUTaskList`, crosses
`GPUFramePlanner -> GPUFramePreflighter -> GPUFrameExecutor`, and records this
exact sequence on one command encoder:

1. clear and draw a red 2x2 quadrant into the MSAA attachment, then store it
   and resolve the 4x4 canonical single-sample target;
2. copy the resolved canonical target into a typed `CopyScratch` texture as a
   neutral non-writing pass break;
3. load the same MSAA view, draw a green 2x2 quadrant, store, and resolve;
4. perform a second neutral texture copy;
5. load the same MSAA view, draw a blue 2x2 lower region, store, and resolve;
6. copy the final canonical target to the output-owned readback buffer.

The exact structural counters are:

- 1 native encoder, 1 finish, 1 queue submit;
- 3 render passes, 3 draws, and 3 MSAA resolves;
- 2 generic texture-to-texture resource copies and 0 destination-read copies;
- 1 texture-to-buffer readback copy;
- 1 created 4x MSAA color attachment and 1 distinct MSAA color view reused by
  all three passes.

Each MSAA `GPUTask.Render` owns its exact typed continuation key. The frame
planner accepts no missing, target-mismatched, sample-mismatched, stale-device,
or target-local key divergence, then lowers that task-owned proof into each
render step. Destination grouping only recoups and checks the same key for its
consumer; it is never the authority that creates continuation proof.

The exact pixel oracle is red in `[0,0)-[2,2)`, green in `[2,0)-[4,2)`, blue
in `[1,2)-[3,4)`, and transparent elsewhere. This proves that later pass
loads preserve earlier samples while every producing pass keeps the canonical
single-sample target current. The 4x attachment contributes exactly 256 bytes
to `FrameLocalMsaaColor`. `peakFrameTransientBytes` is exactly 1,344 bytes
(256 MSAA + 64 reusable scratch + 1,024 readback staging),
`targetResidentBytes` is 64 bytes for the canonical target, and the aggregate
live accounted memory is therefore 1,408 bytes.

Continuation identity is typed by target, device generation, target
generation, sample plan, color format/interpretation, and exact attachment
references. Load, store, and resolve are independent facts: resolving the
canonical target no longer destroys the stored-attachment proof. Preflight
refuses unsafe shapes before native resource or encoder creation with stable
diagnostics:

- direct single-sample writes inside the active MSAA interval:
  `unsupported.msaa.continuation_canonical_write`;
- missing stored-attachment proof:
  `unsupported.msaa.continuation_attachment_not_stored`;
- a render load/store plan that does not store while its typed continuation
  requires `Store`: `unsupported.msaa.continuation_store_operation`;
- depth/stencil continuation in this color-only slice:
  `unsupported.msaa.continuation_depth_stencil_unavailable`;
- a real destination-reading consumer combined with MSAA and no proven exact
  single-sample geometry/clip lowering:
  `unsupported.blend.msaa_destination_read_exactness`.

The E1 copy breaks intentionally use `CopyResourceStep`; they validate
continuation across non-render scopes but do not claim destination-read
support. E2 gates both `CopyDestinationStep` and
`CopyAsDrawMaterializationStep` consumers with the same exact diagnostic.
Direct canonical writes, including compute writes declared either through the
compute target or its writable resource uses, invalidate continuation. The
native materializer also requires the canonical target's exact
`RenderAttachment + CopySource` usage set and `FrameLocal` lifetime before any
native handle creation, derives the store operation from the checked load/store
plan, and refuses a contradiction with the continuation request. Likewise,
inter-frame retained attachments remain an explicit non-claim and stable
refusal until separate ownership and pixel evidence exist.

Validation commands:

```text
rtk ./gradlew :gpu-renderer:compileKotlin :gpu-renderer:compileTestKotlin --console=plain
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSolidRectFrameSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kDestinationCopyFrameSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlannerDestinationContractTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanIntegrityTest' --console=plain
rtk ./gradlew :gpu-renderer:test --console=plain
```

Observed results:

- main and test Kotlin compilation: `BUILD SUCCESSFUL`;
- focused E1/E2/model/preflight/executor/ownership/planner matrix: 194 tests,
  0 failures, 0 errors;
- complete `gpu-renderer` module: 1,701 tests, 0 failures, 0 errors, 2 skipped.

No new wgpu4k API mismatch was found with snapshot
`0.2.0-20260716.235022-2`. Public `TextureDescriptor.sampleCount`, render-pass
`resolveTarget`, repeated `Load`/`Store`, and same-encoder texture copies all
executed successfully. The earlier optional command-encoder label setter issue
documented above is unchanged and remains outside this production path.

## Slice 10F-a reusable prepared scene session evidence

`GPUBackendSession.prepareSceneFrameSession()` now creates the production
`GPUPreparedSceneFrameSession` seam for repeated compatible offscreen frames.
The session retains the exact canonical native `GPUTexture` and
`GPUTextureView`, device generation, target generation, encoding backend, and
readback bridge. Every accepted frame still owns a fresh planner, preflighter,
coordinator, materializer, native payload, frame ID, attempt ID, and retention
ticket. No prepared-session frame calls the legacy
`createOffscreenTarget`/`target.encode`/`readRgba` wrappers.

The Task 10 output algebra is exactly:

```text
CurrentFrameCompletionOnly  // exact queue completion, no readback lane
ReadbackRgba                // final planned copy, completion, map, depad
```

The completion-only lane creates no staging buffer, texture-to-buffer copy,
mapping request, or empty completion anchor. The readback lane remains tied to
the exact submitted frame and returns immutable tightly packed premultiplied
RGBA bytes only after mapping cleanup.

The prepared-session state machine refuses a concurrent frame and every frame
after close locally, before coordinator entry. Compatibility validation also
refuses stale device generation, logical-target substitution, and bounds,
sample-count, or color-format mismatch before the retained native target is
borrowed. Close during an in-flight frame requests shutdown but does not
complete that frame artificially; the close action runs exactly once after its
real terminal result, including when that close action throws.

Backend/child ownership is explicit. Parent close refuses new prepared
children, closes idle children before queue/device teardown, and defers teardown
while a child frame is in flight. Native texture and view close independently;
if one handle close fails, the successful handle is not closed again and the
failed handle stays quarantined for retry. Aborted setup uses a reverse-order,
retryable rollback ledger: successful releases are removed, failures remain
pending, are transferred to a parent-owned quarantine, and are retried before
device teardown. The regression oracle fails the view close once, proves the
texture closes exactly once, then proves the parent retry closes only the view
and empties both ledgers.

The native two-frame smoke proves reuse and frame-local work with these exact
observations:

- 2 distinct frame IDs and 2 distinct attempt IDs;
- 1 persistent native target creation and 2 uses of that exact texture/view;
- 2 frame-local coordinator creations and 2 native-payload registrations;
- 2 queue submits and 2 distinct retention tickets;
- 1 readback copy, from the second frame only;
- exact premultiplied RGBA pixels from the second frame;
- 0 active, output-owned, or quarantined native payloads after terminal output;
- 2 retention registrations, 2 completions, and 0 retention quarantines;
- 0 target closes before session close and exactly 1 after session close.

## Slice 10F-b scene-consumer migration evidence

`PreparedSceneFrameRecorder` is now the single typed scene-to-task selector for the
currently executable prepared families: solid rectangles, registered uniform
rectangles (gradients, color matrix and registered runtime effect), axis-aligned
stroke rectangles, separable blur rectangles, and COLRv0 color glyphs. Unknown
families return `unsupported.prepared-scene.family`; the selector never creates an
immediate target or encodes work.

`OffscreenFrameSampler` now uses one reusable `GPUPreparedSceneFrameSession` for
every selected family. Measured frames request completion only and the sole final
validation frame requests planned RGBA readback. `PerFamilyBenchmark` no longer
measures an immediate render-plus-readback fallback: families without a typed
prepared semantic route are reported as unsupported, so their numbers cannot be
mistaken for the target architecture.

The product offscreen renderer still retains the old immediate path for the
remaining path, image, A8 text, vertices, layer, and composite scenes. Removing it
now would regress committed pixel evidence. Those exact families are the Task 12
migration slices; the legacy path remains an explicit non-completion item and is
not counted as Task 10 performance evidence.

Validation:

```text
rtk ./gradlew :gpu-renderer-scenes:test --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PreparedSceneFrameRecorderTest'
rtk ./gradlew :gpu-renderer-scenes:test
```

Observed result: the selector tests and the complete 283-test scene module pass.

Validation commands:

```text
rtk ./gradlew :gpu-renderer:compileKotlin :gpu-renderer:compileTestKotlin --console=plain
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoordinatorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutorTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackCompletionTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSolidRectFrameSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kDestinationCopyFrameSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest' --tests 'org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaContinuationTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlannerDestinationContractTest' --tests 'org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanIntegrityTest' --tests 'org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProviderTest' --console=plain
rtk ./gradlew :gpu-renderer:test --console=plain
```

Observed results after the rollback-quarantine review fix:

- main and test Kotlin compilation: `BUILD SUCCESSFUL`;
- focused 10A–10F-a matrix: 329 tests, 0 skipped, 0 failures, 0 errors;
- complete `gpu-renderer` module: 1,715 tests, 2 skipped, 0 failures,
  0 errors.

The published wgpu4k snapshot `0.2.0-20260716.235022-2` executes both prepared
frames and the real parent/child teardown smoke successfully. No new facade or
native API mismatch was found. The optional command-encoder label setter issue
documented above is unchanged and is not invoked by this route.

### Explicit non-claim

10F-a establishes the reusable production seam and its native lifetime proof.
It does not claim that glyph, `gpu-renderer-scenes`, benchmark, Canvas, or
window consumers have already migrated; those remain the rest of Slice 10F and
Task 11 work.

## Slice 10F-b product-consumer and measurement evidence

The first two real consumers now use public, handle-free recording facades:

- `GPUColorGlyphFrameRecorder` records one bounded COLRv0 composite;
- `GPUSolidRectFrameRecorder` records one homogeneous ordered rectangle batch.

Both facades, their immutable inputs, task-list builders, readback layout
planner, and exact packet authorities live in `recording`. Native wgpu4k
materializers, reusable native caches, encoding, completion, and teardown stay
in `execution`. A package-boundary test rejects moving either public recorder
back into execution. The SolidRect materializer additionally refuses any
packet whose declared blend plan is not the exact premultiplied fixed-function
`SrcOver` state used by its native pipeline. Target-size multiplication is
checked before resource declarations and fails closed on overflow.

The `colr-v0-color-glyph` product scene no longer uses the old synthetic A/B
fixture. It loads Skia's real `/fonts/skia/colr.ttf`, resolves base glyph 2 into
COLRv0 layer glyphs 7 and 8 with their CPAL red/black colors, rasterizes their
real contours into a 46x38 A8 atlas, and records the 784-byte uniform ABI with
per-layer device bounds. One prepared frame produces:

- 1 encoder, 1 command buffer, 1 submit, and 1 final readback;
- 4,096/4,096 pixels equal to an independent CPU source-over reference;
- identical SHA-256 for render and reference:
  `13b6d8808563476e9eb316684d860e7571b1fd1c226c91aad940d6d63a13eee4`.

The `solid-card-stack` product scene lowers its clear plus three fills into four
ordered SolidRect packets. One prepared frame produces 1 encoder, 1 command
buffer, 1 submit, 1 final readback, and one invariant pipeline-cache creation.
The 60-frame sample keeps all measured frames on the completion-only lane and
performs a single validation readback after measurement. Exact observations:

- 61 native frames total: 60 measured completion-only frames plus 1 validation
  readback frame;
- 61 encoders, 61 command buffers, 61 submits;
- 0 measured readbacks and 1 final validation readback;
- 1 SolidRect invariant-cache creation and 60 reuses;
- 3 warmup samples and 57 stable samples;
- stable wall-clock submit-to-completion times: min 2.6515 ms, median 3.0856
  ms, mean 3.2243 ms, max 4.5655 ms on the recorded Apple M2 Max run.

These samples are route evidence, not a cross-machine performance gate. The
metric excludes PNG encoding and GPU-to-CPU readback from the measured loop.
The scene sampler and per-family benchmark mains explicitly dispose the backend
before process exit; this removes the former native exit-133 teardown race.

Validation commands:

```text
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=colr-v0-color-glyph -PsceneOutput=reports/gpu-renderer-scenes/offscreen --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=solid-card-stack -PsceneOutput=reports/gpu-renderer-scenes/offscreen --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:sampleGpuRendererSceneFrames -PsceneId=solid-card-stack -Pframes=60 -PsceneOutput=reports/gpu-renderer-scenes/frame-samples --no-daemon --console=plain
```

Observed results:

- `gpu-renderer`: 1,759 tests, 0 skipped, 0 failures, 0 errors;
- `gpu-renderer-scenes`: 268 tests, 0 skipped, 0 failures, 0 errors;
- both offscreen render tasks: `BUILD SUCCESSFUL`;
- 60-frame prepared sample task: `BUILD SUCCESSFUL`.

The published wgpu4k snapshot `0.2.0-20260716.235022-2` remains compatible with
the unchanged public `GPUQueue.onSubmittedWorkDone(): Result<Unit>` API and all
native evidence above. Kanvas adds no private completion workaround. The
optional native command-encoder label setter remains a separately documented
wgpu4k issue and is not called by the production path.

### Explicit non-claim

10F-b proves the reusable target/session path for homogeneous SolidRect and
bounded COLRv0 consumers plus completion-only measurement. Gradients, images,
paths, strokes, filters, runtime effects, mixed frames, saveLayer,
destination-read, general Canvas integration, and window presentation have not
yet migrated to this prepared dispatcher and remain later slices.

## Slice 10F-c closed-program uniform rectangle batches

The prepared dispatcher now has one generic, handle-free
`RegisteredUniformRect` recording contract. A frame carries only a closed
program identity, exact immutable uniform bytes, target/scissor bounds, paint
order, and canonical hashes. It never carries WGSL source or native handles.
The native side validates the exact packet authority, creates one render pass,
and uses a session cache keyed by program, target format, and sample count.

The first supported program set is deliberately bounded:

- solid color;
- two-stop linear, radial, and sweep gradients;
- the registered `SimpleRT` runtime effect.

All shaders emit premultiplied color into the exact fixed-function `SrcOver`
pipeline. The scene adapter creates an independent CPU premultiplied
source-over reference. Native evidence on the current Apple M2 Max run is:

- linear gradient: 1 encoder, 1 command buffer, 1 submit, 1 validation
  readback, 63,998/64,000 exact pixels, and every pixel within one UNORM8 LSB;
- radial gradient: 1 encoder, 1 command buffer, 1 submit, 1 validation
  readback, 64,000/64,000 exact pixels;
- sweep gradient: 1 encoder, 1 command buffer, 1 submit, 1 validation readback,
  64,000/64,000 exact pixels;
- registered `SimpleRT`: 1 encoder, 1 command buffer, 1 submit, 1 validation
  readback, 64,000/64,000 exact pixels, with no source in the frame plan.

The per-family benchmark moves FillRect and all three gradient families to the
completion-only path. Warmup and measured frames perform no readback; one final
readback validates the last state outside the measured interval. A regression
test opens successive prepared targets in one backend session and proves that
their exact target generations are bound during preflight instead of being
hard-coded by the handle-free recorder. This prevents the previously observed
`stale.preflight.resource_generation` refusal while preserving exact device,
target, capability-seal, and prepared-resource validation.

Validation commands:

```text
rtk ./gradlew :gpu-renderer-scenes:test --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PerFamilyBenchmarkTest.prepared families remain valid across successive native target generations'
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:runPerFamilyBenchmark -PwarmupFrames=1 -PmeasuredFrames=2 -PperformanceOutput=/tmp/kanvas-family-benchmark-2
```

Observed results:

- successive-target native regression: `BUILD SUCCESSFUL`;
- complete `gpu-renderer` and `gpu-renderer-scenes` test suites:
  `BUILD SUCCESSFUL`;
- small native family benchmark: all 8 families sampled; FillRect and all three
  gradient families used prepared submit-to-completion measurement with zero
  measured readbacks.

The published wgpu4k snapshot `0.2.0-20260716.235022-2` runs this slice through
the unchanged public `GPUQueue.onSubmittedWorkDone(): Result<Unit>` API. Kanvas
adds no completion workaround. The separately observed optional encoder-label
setter failure remains outside this route.

### Explicit non-claim

10F-c does not claim that blur, color matrix, stroke, bitmap, text, general
paths, mixed saveLayer/destination-read frames, Canvas integration, or window
presentation use the generic prepared dispatcher. Blur and color matrix need
their corrected premultiplied program evidence; the current legacy stroke ABI
must be diagnosed before promotion. Bitmap/text require sampled-resource
bindings, and paths require their geometry/coverage route rather than this
uniform-only contract.

## Slice 10F-d row-major ColorMatrix promotion

The ColorMatrix investigation compared the legacy Kanvas ABI with Skia's
`SkMatrixColorFilter` and Graphite's `MatrixColorFilterBlock`. The root cause
was not the 96-byte allocation: Kanvas already stored one input color, four
matrix rows, and one translation vector. The fragment program multiplied those
rows as columns and returned straight-alpha output into a premultiplied
`SrcOver` attachment.

The corrected contract now:

- interprets the four vectors as row-major output rows, matching Skia's 4x5
  matrix convention;
- adds the fifth vector as RGBA translation;
- clamps the filtered unpremultiplied color to `[0, 1]`;
- premultiplies once before fixed-function `SrcOver`;
- keeps the exact 96-byte ABI and closed `color-matrix-v1` program identity;
- retains no WGSL source or native handles in the frame plan.

The `color-matrix-filter` and `color-matrix-tint` scenes now use the prepared
registered-uniform route. The independent CPU reference applies identity,
Skia-luma grayscale, or a bounded channel rotation by scene fixture kind, then
premultiplies and composites independently of the packed GPU bytes.

Native Apple M2 Max evidence for `color-matrix-filter`:

- 1 encoder, 1 command buffer, 1 submit, and 1 validation readback;
- 2 invariant-cache creations and 2 reuses within the frame;
- 64,000/64,000 pixels exactly equal to the independent CPU reference;
- maximum channel delta 0.

The per-family benchmark now contains nine families. ColorMatrix uses prepared
submit-to-completion measurement with zero measured readbacks and one final
validation readback. A small 1-warmup + 2-measured run sampled all 9/9
families; ColorMatrix observed 3.7718 ms mean / 265.1245 FPS. This short sample
is execution evidence only, not a stable cross-machine performance claim.

Validation commands:

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.wgsl.ColorMatrixWgslTest'
rtk ./gradlew :gpu-renderer-scenes:test --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PreparedRegisteredUniformRectSceneFrameTest.color matrix scene lowers row-major programs into the generic prepared batch' --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.color matrix uses one prepared submit and matches the independent row-major reference'
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=color-matrix-filter -PsceneOutput=/tmp/kanvas-color-matrix-prepared --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:runPerFamilyBenchmark -PwarmupFrames=1 -PmeasuredFrames=2 -PperformanceOutput=/tmp/kanvas-family-benchmark-color-matrix --no-daemon --console=plain
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test --no-daemon --console=plain
```

The complete renderer and renderer-scenes suites finished with
`BUILD SUCCESSFUL`: 53 actionable tasks, 3 executed and 50 up-to-date. The
native ColorMatrix test was executed on the available adapter rather than
returning through its explicit adapter-unavailable guard.

### Explicit non-claim

10F-d promotes only the bounded solid-input ColorMatrix scene program. It does
not claim arbitrary image-filter DAGs, sampled-image ColorMatrix, HSLA-domain
matrices, unclamped matrices, blur, or stroke. Blur requires an intermediate
texture and separable filter passes; stroke belongs to geometry/coverage and
must not inherit the legacy overlapping center/dimension uniform fields.

## Slice 10F-e bounded separable Gaussian blur

The old `BlurWgsl` benchmark was an analytic radial alpha falloff around one
point. It did not blur the source image and therefore could not be promoted as
Skia-like blur evidence. The first prepared blur slice now uses a real bounded
Gaussian route for `gaussian-blur-photo`:

```text
solid source -> horizontal Gaussian -> vertical Gaussian + SrcOver -> scene target
```

The handle-free frame plan carries one immutable `SeparableBlurRect` semantic
payload, three ordered render tasks, two explicit `FilterTarget` resources, and
an optional final readback. The target is limited to 2048x2048, sigma is limited
to `[0.5, 12]`, and the fixed shader loop accepts at most 25 uniform-driven taps.
The lower sigma bound is explicitly normalized to the supported three-tap
minimum. No WGSL source or backend handle enters the frame plan.

The public-wgpu4k materializer records all three render passes into one command
encoder and one command buffer, then performs one queue submission. A session
cache owns the static source/horizontal/vertical pipelines, sampler, and the two
serialized intermediate textures. Repeated completion-only frames update only
their two small uniform buffers and frame-local bind groups; the measured loop
performs no readback.

Native Apple M2 Max evidence for `gaussian-blur-photo`:

- 1 encoder, 1 command buffer, 1 submit, and 1 validation readback;
- 2 intermediate rgba8unorm textures, 512,000 bytes total;
- 64,000/64,000 pixels exactly equal to an independent quantized two-pass CPU
  reference, maximum channel delta 0;
- a 1-warmup + 2-measured benchmark reused both invariants and intermediates
  three times after one creation;
- the short sample observed 4.0170 ms mean / 248.9433 FPS and passes the local
  frame gate; it is execution evidence, not a cross-machine performance claim.

Validation commands:

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.consumer.GPUSeparableBlurRectFrameRecorderTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFramePayloadMaterializerDispatcherTest' --console=plain
rtk ./gradlew :gpu-renderer-scenes:test --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PreparedSeparableBlurRectSceneFrameTest' --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.gaussian blur photo uses three prepared passes in one submit' --console=plain
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=gaussian-blur-photo -PsceneOutput=/tmp/kanvas-separable-blur-prepared --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:runPerFamilyBenchmark -PwarmupFrames=1 -PmeasuredFrames=2 -PperformanceOutput=/tmp/kanvas-family-benchmark-separable-blur --no-daemon --console=plain
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test --no-daemon --console=plain
```

The complete renderer and renderer-scenes suites finished with
`BUILD SUCCESSFUL`: 53 actionable tasks, 3 executed and 50 up-to-date.

### Explicit non-claim

10F-e does not claim the four-object `blur-radius-ladder`, arbitrary image
inputs, local reduced-resolution intermediates, blur styles, crop/tile modes,
multi-node image-filter DAGs, or general Canvas integration. Those extensions
must keep explicit bounds, budgets, ownership, CPU/GPU evidence, and stable
refusal diagnostics. Stroke remains a separate Geometry/Coverage route.

## Slice 10F-f axis-aligned Stroke through Geometry/Coverage

The legacy `StrokeWgsl` route did not represent a rectangular stroke. Its
packer ignored the supplied half-width and half-height, hard-coded a 4-pixel
width, and stored center coordinates in fields that the shader also interpreted
as edge distance and half-height. The result was a small filled analytic shape,
not a centered rectangular outline.

The promoted `stroke-rect-outline` route now keeps stroke interpretation in the
Geometry/Coverage layer:

```text
bounded rect path + stroke facts
  -> analytic annular-rect lowering
  -> four disjoint coverage bands
  -> existing prepared SolidRect batch
```

The first slice requires an axis-aligned integral path, an even integral stroke
width, identity or translate transform, exact Miter joins, outer bounds inside
the target, and a non-empty inner rectangle. Unsupported subpixel, overflow,
join, miter, transform, and degenerate cases receive stable geometry refusal
codes. No raw stroke center/width ABI or `StrokeWgsl` source reaches the promoted
native route.

Native Apple M2 Max evidence for `stroke-rect-outline`:

- four disjoint bands plus the clear packet render in one prepared pass;
- 1 encoder, 1 command buffer, 1 submit, and 1 final validation readback;
- 64,000/64,000 pixels exactly equal to an independent CPU analytic-band
  reference, maximum channel delta 0;
- a 1-warmup + 2-measured benchmark created the SolidRect invariant cache once
  and reused it three times;
- the short sample observed 3.3505 ms mean / 298.4647 FPS and passed the local
  frame gate; it is route evidence, not a cross-machine performance claim;
- the per-family benchmark now samples 10/10 families.

Validation commands:

```text
rtk ./gradlew :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLowererTest' --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:test --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PreparedStrokeRectSceneFrameTest' --tests 'org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest.stroke rect outline uses geometry lowering and one prepared submit' --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=stroke-rect-outline -PsceneOutput=/tmp/kanvas-prepared-stroke-rect --no-daemon --console=plain
rtk ./gradlew :gpu-renderer-scenes:runPerFamilyBenchmark -PwarmupFrames=1 -PmeasuredFrames=2 -PperformanceOutput=/tmp/kanvas-family-benchmark-stroke --no-daemon --console=plain
rtk ./gradlew :gpu-renderer:test :gpu-renderer-scenes:test --no-daemon --console=plain
```

The complete renderer and renderer-scenes suites finished with
`BUILD SUCCESSFUL`: 53 actionable tasks, 3 executed and 50 up-to-date.

### Explicit non-claim

10F-f promotes only the integral axis-aligned `stroke-rect-outline` fixture.
It does not claim hairlines, odd or subpixel widths, fractional-edge AA, round
or bevel joins, cap variants, dashes, path effects, rotated/perspective paths,
general path stroking, the combined `stroke-and-filter-card`, or Canvas route
retirement. Those cases require their own Geometry/Coverage expansion and
reference-backed promotion evidence.
