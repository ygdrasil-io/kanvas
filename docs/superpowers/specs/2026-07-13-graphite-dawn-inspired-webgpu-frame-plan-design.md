# Graphite/Dawn-inspired WebGPU frame plan design

**Date:** 2026-07-13  
**Status:** Approved for written specification review  
**Scope:** Replace immediate per-operation GPU composition with a measured, linear WebGPU frame plan that prioritizes fixed-function blending, uses bounded GPU-only destination copies when required, renders through one canonical scene texture, and submits one command buffer per scene render.

## Goal

Bring Kanvas closer to Graphite/Dawn performance without porting Graphite or rebuilding its multi-backend task graph.

Kanvas has one GPU backend, WebGPU through wgpu4k. The design therefore adopts the performance invariants visible in Graphite while keeping a linear 2D architecture:

- classify blend and coverage before backend execution;
- keep representable blends in the current render pass;
- read the destination only when the exact formula requires it;
- copy only the destination bounds that a shader will read;
- keep all destination access on the GPU;
- reuse transient textures;
- record the scene into one command encoder and submit once;
- generate diagnostics and evidence from the plan that the runtime actually executes.

The target flow is:

```text
DisplayList / DisplayOp sequence
  -> GeometryPlan + CoveragePlan + normalized paint facts
  -> CoveragePlan WebGPU lowering
  -> normalized GPU draw commands
  -> BlendCoveragePlanner
  -> GPUFramePlanner
       -> direct render-pass groups
       -> bounded destination-copy groups
       -> layer/filter target transitions
       -> present or readback step
  -> resource preflight
  -> one WebGPU command encoder
  -> one command buffer
  -> one queue submission
```

`CoveragePlan` remains the geometry-side semantic source of truth defined by
`.upstream/specs/geometry-coverage/`. The new blend-and-frame planners consume
its WebGPU lowering result; they do not inspect raw path/clip state again and
do not introduce a competing geometry-coverage model.

## Context and current problem

Commit `2d2764415` made blend and geometry-coverage composition correct across the mapped drawing APIs, but it also made `coverageCompositionRequired` route many antialiased operations through `destinationReadComposer`. That path can create a separate source surface, copy the destination, and execute a fullscreen three-texture formula pass even for common `SrcOver` draws.

The native runtime compounds the cost:

- `copyOffscreenTexture()` creates, finishes, and submits its own command encoder;
- `encodeOffscreenTexture()` creates and submits another encoder;
- `snapshotTargetToOffscreenTexture()` calls `readRgba()`, waits for a CPU mapping, then uploads the bytes into a texture;
- offscreen and primary-target routes use different snapshot behavior;
- the existing `GPUDestinationReadExecutor` reports declarative statistics but does not execute the native destination-read strategy;
- the existing destination-read, pass-batching, command-stream, and command-encoder contracts are not yet one live frame-level source of truth.

The visible result is a performance cliff for draw-heavy GMs. In the current measured snapshot, `hairmodes` takes roughly 10.2 seconds, `aaxfermodes` roughly 2.4 seconds, and `xfermodes` roughly 1.35 seconds. The dominant issue is not merely the cost of blend arithmetic. It is the multiplication of intermediate passes, destination copies, command encoders, and queue submissions.

## Graphite/Dawn reference

The design is informed by Skia checkout commit `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a` under `/Users/chaos/workspace/kanvas-forge/skia-main`.

The relevant reference points are:

- `src/gpu/graphite/ContextUtils.cpp`: `CanUseHardwareBlending()` keeps common blends in fixed-function hardware when the formula and coverage permit it.
- `src/gpu/graphite/PaintParams.cpp`: `DstUsage` distinguishes painter-order dependence from an actual shader destination read.
- `src/gpu/graphite/ResourceTypes.h`: `DstReadStrategy` represents the backend method used only when a destination read is required.
- `src/gpu/graphite/Caps.cpp`: Graphite prefers framebuffer fetch when supported and otherwise uses a texture copy.
- `src/gpu/graphite/dawn/DawnCaps.cpp`: Dawn advertises framebuffer fetch only when the WebGPU feature is really present.
- `src/gpu/graphite/DrawList.cpp`: draws are recorded and batched before command encoding, and destination-read bounds are accumulated.
- `src/gpu/graphite/DrawContext.cpp`: the texture-copy route creates one bounded `DstCopy` for the pending pass.
- `src/gpu/graphite/Image_Graphite.cpp`: a non-copyable but texturable source falls back to a GPU copy-as-draw; a source that is neither copyable nor texturable fails instead of reading through the CPU.
- `src/gpu/graphite/ShaderInfo.cpp`: the destination sample and coverage-aware blend are fused into the fragment shader.
- `src/gpu/graphite/dawn/DawnCommandBuffer.cpp`: texture copies and render passes are recorded on a command encoder rather than immediately submitted by blend code.

Kanvas does not reproduce Graphite's general DrawList sorting, multi-backend capability hierarchy, SkSL generation, render-task DAG, subpasses, or queue manager. Those mechanisms solve a broader problem than Kanvas has.

## Decisions

### One canonical GPU scene texture

Every scene renders into an internal single-sample WebGPU texture with these usages:

```text
RenderAttachment | TextureBinding | CopySrc | CopyDst
```

This texture is the canonical current destination for both offscreen GMs and live/window rendering.

When a coverage strategy requires MSAA, the render pass uses a transient
multisample attachment and resolves into this single-sample canonical texture.
A destination copy is recorded only after the producing pass has ended and its
resolve is available. The multisample attachment itself is never sampled or
copied as the destination snapshot.

- A GM appends a copy-to-staging readback step to the same frame encoder, submits once, and maps only after submission completion.
- A window appends a final present pass that samples the canonical scene texture and writes to the acquired surface texture.
- The surface texture is never used as a destination-read source.
- `snapshotTargetToOffscreenTexture()` and its CPU `readRgba()`/upload loop are removed.
- Primary and offscreen rendering share the same blend, clip, layer, filter, and destination-read implementation.

The canonical target is recreated on resize, format change, sample-count change, or device-generation change. Old target resources remain retained through their final queue submission.

### No executable framebuffer-fetch branch

The current wgpu4k/WebGPU surface used by Kanvas does not expose framebuffer fetch. The live strategy model therefore does not include an inactive `FramebufferFetch` case.

Kanvas keeps:

- the Graphite/Dawn reference explaining why framebuffer fetch can be faster;
- the stable refusal `unsupported.destination_read.framebuffer_fetch_unavailable` for an explicit request;
- a negative test proving that the unavailable feature is not claimed.

If wgpu4k later exposes a real, testable framebuffer-fetch feature, it can be added as a new typed plan with native pixel and performance evidence. No placeholder execution branch is added now.

### No CPU fallback

Destination access uses this ordered GPU-only policy:

```text
fixed-function blend
  -> existing validated intermediate
  -> bounded native texture copy
  -> bounded copy-as-draw when the source is texturable but not CopySrc
  -> stable refusal
```

No destination-read route may use `readRgba()`, `mapAsync`, an upload of CPU-produced snapshot bytes, or a hidden CPU renderer as an input to later GPU rendering.

### Exact blend-and-coverage planning

The independent booleans `requiresDestinationRead` and `coverageCompositionRequired` are replaced as routing authorities by one exhaustive `BlendCoveragePlan`.

The planner classifies every current `GPUBlendMode` and coverage combination into one of:

```text
DirectFixedFunction
ShaderWithDestination
NoOp
Refuse
```

The plan also records how final coverage is obtained:

```text
NoCoverage
InlineGeometryCoverage
ScissorOnly
SampledClipCoverage
SampledGeometryAndClipCoverage
StencilCoverCoverage
```

These values are WebGPU consumption forms produced by the existing
`CoveragePlan` lowering boundary. They are not replacements for
`CoveragePlan` or `CoverageModel`. Unsupported geometry or coverage retains
the established typed coverage diagnostic and never reaches blend planning as
an apparently valid zero/full-coverage draw.

`StencilCoverCoverage` represents the established WebGPU stencil-cover
execution selected by `CoveragePlan.PathCoverage`; it can add stencil-write
and cover work to the frame plan without converting the path to an unrelated
alpha mask. MSAA sample coverage and stencil tests apply coverage through
attachment state. The scalar formula below describes analytic or sampled
coverage; the planner must preserve the equivalent sample/stencil semantics
for their dedicated strategies.

`DirectFixedFunction` is accepted only when the fragment output plus WebGPU attachment blend state exactly represents the CPU reference formula for the selected coverage class. A translucent draw may depend on prior destination contents without requiring the shader to sample a copied destination. Modes whose coverage formula would require unsupported dual-source blending use `ShaderWithDestination` instead.

`NoOp` is explicit for operations such as an effective `Dst` draw. `Refuse` contains a stable reason code and never silently changes the requested blend mode.

For destination-read shaders with analytic or sampled coverage, the final
formula is:

```text
F = geometryCoverage * clipCoverage
result = destination + F * (Blend(source, destination) - destination)
```

Geometry and clip coverage are multiplied in the final draw shader when both are present. The separate `COMBINE_COVERAGE_WGSL` pass is removed from the common path. A separate coverage texture remains permitted when geometry or an existing clip implementation inherently produces one, but it is sampled by the final draw rather than forcing an additional fullscreen combine pass.

### A linear GPU frame plan

`GPUFramePlanner` produces an immutable ordered `GPUFramePlan`. It owns no WebGPU handles.

Representative steps are:

```text
RenderPassStep(target, load/store, draws)
ComputePassStep(target/resources, dispatches)
CopyDestinationStep(source, snapshot, bounds)
CopyAsDrawStep(source, snapshot, bounds)
TargetTransitionStep(parent/layer/filter)
ReadbackStep(bounds, staging)
PresentStep(surface)
RefusedDrawStep(command, diagnostic)
```

The plan preserves painter's order. It is not a general dependency DAG.

Direct consecutive draws remain in one render pass whenever target, attachment state, sample count, and resource hazards permit pipeline and binding changes inside that pass. A destination-copy step ends the active render pass, records a texture copy, then starts a later render pass on the same command encoder.

### Safe sharing of destination snapshots

Destination-read draws may share one snapshot when later draws do not need pixels modified since that snapshot.

The planner processes a candidate group in painter's order and tracks:

```text
snapshotBounds = union of destination-read bounds in the group
writtenSinceSnapshot = union of conservative write bounds already scheduled in the group
```

A destination-read draw may join the group only when its read bounds do not intersect `writtenSinceSnapshot`. Every scheduled draw then contributes its conservative write bounds. The first destination-read draw that intersects prior writes starts a new group and therefore receives a fresh snapshot.

This permits disjoint advanced blends to share one copy while ensuring that an overlapping later draw observes the earlier result.

Snapshot bounds are always:

```text
draw read bounds intersect clip bounds intersect target bounds
```

The snapshot texture stores only this pixel-aligned region. Shader uniforms carry the snapshot origin and dimensions so device coordinates map to the bounded texture correctly.

### One encoder and one submission

`GPUFrameExecutor` materializes a validated plan into one WebGPU command encoder. A frame may contain several render passes, copies, layer transitions, and a final readback or present step, but it produces one command buffer and one `queue.submit()`.

The existing `GPUQueueManager` receives one logical submission containing all leases referenced by the frame. Resource release remains completion-driven.

An output readback waits for that one submission and maps its staging buffer afterwards. This synchronization is an output operation only; it is never used to continue rendering.

## Component ownership

### `GPUOpMapper`

`GPUOpMapper` remains in `:kanvas`. It maps every `DisplayOp` to backend-neutral normalized facts:

- `GeometryPlan`, `CoveragePlan`, and conservative device bounds;
- paint/material and alpha domain;
- requested blend mode;
- clip/scissor or clip-mask reference;
- target/layer identity;
- explicit filter or source-intermediate requirements.

It does not select a destination-read strategy and does not hold WebGPU resources.
It also does not reinterpret a `CoveragePlan` from raw path or clip facts after
the geometry/coverage lowering boundary has made its decision.

### `BlendCoveragePlanner`

`BlendCoveragePlanner` lives in `:gpu-renderer` and is the only authority for blend-plus-coverage routing. Its plan carries the exact attachment blend state or shader formula identity required by pipeline-key construction.

Pipeline keys contain code, binding topology, attachment state, sample count, format class, and coverage topology. They do not contain concrete texture identity, snapshot origin, bounds, or other uniform values.

### Existing `GPUPassBatcher`

The existing `GPUPassBatcher` is retained and generalized. It remains responsible for compatible draw grouping inside render-pass segments. It no longer represents the whole frame and no longer treats every destination-read diagnostic as an opaque fallback to an unrelated immediate path.

`GPUFramePlanner` orchestrates frame-level target transitions, copies, layers, filters, readback, and presentation. It delegates compatible direct and destination-read draw segments to `GPUPassBatcher` for pass-local grouping.

The initial live integration may emit complete pipeline and binding setup for every draw. State-change elision and instanced primitive batching are follow-up optimizations only when measurements show they are needed.

### Existing command contracts

`GPUPassCommandStream` remains the pass-local lowering format. It already models begin/end render pass, draws, copies, intermediate binding, and layer preparation. `GPUFramePlan` orders the pass-local streams and non-render steps for the whole scene.

`GPUCommandEncoderPlan` is evolved from a single render-scope evidence object into the one-to-one preflight description of the frame's real command encoder. It records the ordered render, copy, readback, and present scopes. There must not be a second evidence-only encoder plan that diverges from native execution.

### Existing destination-read contracts

Useful validation and stable diagnostics from `GPUDestinationReadStrategyPlanner` and `ValidatingDestinationReadMaterializer` move behind the live `BlendCoveragePlan` and `GPUFramePlan` preparation path.

The following duplication is removed:

- `GPUDestinationReadExecutor` statistics that claim copy execution without native execution;
- a separate strategy decision after blend-and-coverage planning;
- a materialization result that is not the resource set consumed by `GPUFrameExecutor`;
- evidence dumps produced from a plan different from the submitted frame.

Compatibility dump formatting may be adapted during migration, but every accepted line must be generated from the live plan and materialized resources.

### `GPUSceneTarget`

`GPUSceneTarget` owns the canonical scene texture, view, dimensions, format, generation, and usage facts. Layer and filter targets use the same target descriptor model but have bounded lifetimes in the frame plan.

### `GPUScratchTexturePool`

The pool keys reusable textures by:

- format;
- usage mask;
- sample count;
- size class;
- device generation.

Requested sizes are rounded to deterministic size classes. A lease exposes the logical bounded region separately from the backing dimensions. The frame planner's lifetime intervals allow a returned texture to serve a later non-overlapping step.

The existing fixed 16 MiB per-copy acceptance ceiling no longer defines rendering support. The runtime enforces device limits and a configurable peak scratch-memory safety budget based on actually live allocations. Free compatible textures are reused, then evicted if needed, before a budget refusal is produced.

### `GPUFrameExecutor`

The executor only executes a fully prepared `GPUFramePlan`. It may not reclassify blends, widen copy bounds, choose a CPU fallback, or submit intermediate command buffers.

It is responsible for:

- creating one command encoder;
- opening and closing render/compute passes in planned order;
- recording bounded texture copies;
- executing copy-as-draw when planned;
- applying planned pipeline/binding/scissor state;
- appending readback or present work;
- finishing one command buffer;
- submitting once;
- retaining all referenced resources in the matching queue submission.

## Data flow

One scene render executes these phases:

1. Normalize all supported `DisplayOp` values.
2. Build one `BlendCoveragePlan` per draw.
3. Emit a local refusal for semantically unsupported commands.
4. Construct the linear `GPUFramePlan`, including destination-copy grouping and target transitions.
5. Preflight pipelines, bind groups, buffers, target textures, scratch leases, generations, WebGPU limits, and the peak scratch budget.
6. If preflight succeeds, create one command encoder and execute every frame step.
7. Finish and submit one command buffer.
8. For a window, present the acquired surface texture after submission.
9. For a GM/readback, wait for the same submission and map the already-recorded staging copy.
10. Release resources only when the queue completion policy permits it.

## Error handling

### Local semantic refusal

A command known to be unsupported during planning is omitted while the remainder of the scene stays renderable. The refusal is attached to that command and remains visible in diagnostics and dashboard evidence.

Representative stable codes are:

```text
unsupported.gpu.blend_coverage_formula
unsupported.gpu.destination_copy_unavailable
unsupported.gpu.destination_texture_not_sampleable
unsupported.destination_read.framebuffer_fetch_unavailable
unsupported.destination_read.cpu_readback_forbidden
```

No refusal may coerce the blend to `SrcOver` or hide the command from evidence.

### Atomic frame failure

Failures that invalidate a previously accepted execution plan abort the entire frame before submission:

- pipeline or bind-group creation failure;
- target or resource generation mismatch;
- allocation failure after permitted pool eviction;
- WebGPU dimension or usage violation;
- encoder failure;
- resource lifetime proof failure.

For a live window, the last successfully presented frame remains visible. For a GM, the render fails with the terminal diagnostic. A partially encoded command buffer is never submitted.

### Scratch-memory budget

Diagnostics for a budget failure report:

- requested logical and backing dimensions;
- requested bytes;
- current live scratch bytes;
- reusable and evicted bytes;
- configured peak budget;
- device texture limits.

The budget controls peak resource lifetime, not feature semantics through an arbitrary full-surface estimate.

## Testing

### Exhaustive blend-and-coverage unit tests

Every current `GPUBlendMode` is tested across:

- coverage `0`, `0.25`, `0.5`, and `1`;
- opaque and translucent sources;
- transparent, translucent, and opaque destinations;
- no clip, scissor, and sampled clip coverage;
- AA and non-AA geometry coverage classes.

Tests assert the selected plan, attachment state or shader formula, destination-read requirement, stable refusal, and CPU reference result. The CPU oracle uses premultiplied colors and the canonical formula `D + F * (Blend(S,D) - D)`.

### Drawing-API routing tests

DrawColor/clear, rect, rrect, DRRect, path, point/line, vertices, mesh, image,
atlas, nine-patch, lattice, text, picture, layer, and filter families prove that
they provide the correct normalized facts to the same planner. Formula
exhaustiveness is tested once in the planner rather than duplicated for every
API. Each geometry family also proves that the consumed coverage input is the
lowering result of its `CoveragePlan`, including stable `coverage.*` refusals.

### Frame-planner tests

Deterministic sequences cover:

- consecutive direct draws in one pass with no copy;
- disjoint destination-read draws sharing one bounded snapshot;
- overlapping destination-read draws requiring fresh snapshots;
- direct writes between destination reads;
- clip and target intersection;
- snapshot-origin sampling math;
- layer/filter target transitions;
- resource lifetime intervals and scratch reuse;
- local refusal without painter-order corruption.

### Native executor tests

Instrumented execution asserts:

- one native command encoder;
- one command buffer;
- one `queue.submit()` per scene render;
- planned pass/copy/pass order;
- bounded copy extents;
- no destination readback snapshot;
- no submission on atomic failure;
- resource retention through submission completion.

### Pixel tests

Adapter-backed and native surface tests compare all RGBA channels against the CPU reference at interiors, exteriors, and AA/clip edges. They cover all blend families and representative drawing APIs. Existing `aaxfermodes`, `hairmodes`, and `xfermodes` remain the primary visual regression GMs.

### Graphite/Dawn performance reference

The performance report records on the same host:

- Graphite+Dawn for the same upstream `hairmodes`, `aaxfermodes`, and
  `xfermodes` GM implementations and dimensions;
- Kanvas before the change;
- Kanvas after the change.

Runs use the same dimensions, at least 60 warmup frames or a documented
three-sigma stabilization rule, multiple measured iterations, and median
reporting. The report includes provenance for the Skia commit, Kanvas commit,
adapter, device, driver, JDK, and command configuration.

Graphite is a comparative performance reference, not a runtime dependency or acceptance oracle for Kanvas architecture.

## Performance gates

Deterministic structural gates are release-relevant even when wall-clock measurements vary:

- one submission and one command buffer per scene render;
- zero CPU destination snapshots;
- zero destination copies caused only by AA in `hairmodes`;
- no full-target copy when bounded read bounds are smaller;
- snapshot sharing only across dependency-safe draws;
- no similarity regression in the selected GMs;
- diagnostics count direct draws, pass breaks, destination groups, copies, copied pixels/bytes, scratch allocations/reuses/evictions, encoders, command buffers, submissions, and readback waits.

Initial measured targets are:

- at least 2x faster than the current measured snapshot for `hairmodes`, `aaxfermodes`, and `xfermodes`;
- no greater than 10% regression for direct-draw GMs outside those targets;
- publish the Kanvas/Graphite+Dawn ratio;
- for direct-draw-dominated scenes, target Kanvas at no more than 2x Graphite+Dawn time on the same host.

If structural gates pass but the ratio target does not, profiling must identify the next dominant cost before adding complexity. Candidate follow-ups are state-change elision, buffer upload consolidation, and specialized instanced batches. They are not automatically part of this implementation.

## Migration and removal

The implementation remains one chantier but proceeds through reviewable slices:

1. Add exhaustive `BlendCoveragePlanner` tests and typed plans while preserving current pixels.
2. Add `GPUFramePlan` and extend existing pass/encoder contracts to describe a mixed render/copy frame.
3. Implement `GPUSceneTarget`, scratch pooling, resource preflight, and one native frame executor.
4. Route offscreen rendering through the frame executor and remove immediate per-operation submissions.
5. Route live/window rendering through the canonical scene target and remove the CPU snapshot API.
6. Integrate clip, layers, filters, pictures, text, images, vertices, and meshes through the common planner.
7. Remove superseded destination composer/executor paths and evidence-only duplicate decisions.
8. Run exhaustive unit/native/pixel validation, regenerate renders and scores, and produce the Graphite/Kanvas performance report.

Temporary adapters are allowed only between consecutive slices and must be removed before the chantier is considered complete. The final state has one live planning path and one native execution path.

## Non-goals

- No port of Ganesh or Graphite.
- No multi-backend capability hierarchy.
- No SkSL compiler, IR, or VM work.
- No placeholder framebuffer-fetch implementation.
- No CPU destination-read fallback.
- No general render-task DAG.
- No global painter-order reordering beyond proven pass-local grouping.
- No mandatory general instancing system unless post-change measurements identify draw-call overhead as the remaining dominant cost.
- No support claim based on diagnostics without pixel and performance evidence.

## Acceptance criteria

- All current drawing APIs use the same exhaustive blend-and-coverage planner.
- Common AA `SrcOver` draws do not allocate source/destination intermediates or leave the current render pass solely because of coverage.
- Every true destination read uses a bounded GPU snapshot, an existing validated intermediate, or a stable refusal.
- Disjoint destination-read draws share snapshots only when dependency analysis proves it safe.
- Overlapping destination-read draws observe prior results correctly.
- Offscreen and window rendering use the same canonical scene target path.
- `snapshotTargetToOffscreenTexture()` and the destination-snapshot CPU readback/upload route are removed.
- A scene render records one command encoder, produces one command buffer, and calls `queue.submit()` once.
- Output readback, when requested, is encoded in that command buffer and mapped only after submission.
- Scratch resources are reused and retained correctly; their peak memory and eviction behavior are observable.
- A local unsupported draw remains visible as a stable diagnostic; an execution failure submits no partial frame.
- `aaxfermodes`, `hairmodes`, and `xfermodes` retain or improve visual similarity and meet the initial measured performance target, or the remaining measured bottleneck is explicitly reported without a false completion claim.
- The final report cites the exact Graphite/Dawn source revision and records before/after/Graphite measurements with reproducible commands.
