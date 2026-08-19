# GPU Recording And Task Graph

Status: Draft
Date: 2026-06-13

## Purpose

Define the Graphite-inspired recording and task concepts for the new GPU
renderer. The design is inline on the Kanvas `GPU` facade and does not preserve
Graphite's backend plugin architecture.

This document defines the full technical target for recording, draw analysis,
pass construction, and layer planning. It is not an implementation-slice plan;
future tickets must choose smaller vertical slices after these contracts are
accepted.

## Responsibilities

`GPURecorder` owns command intake and recording construction.

`GPUDrawAnalysis` owns explicit per-draw route, ordering, culling, layer,
material, render-step, texture/image ownership, and resource facts.
Common coordinate-space, transform, bounds, pixel-grid, rounding, and precision
facts are governed by `30-coordinate-transform-bounds-policy.md`; draw analysis
consumes those facts instead of reinterpreting matrices ad hoc.

`GPUOcclusionTracker` owns conservative occlusion state and culling decisions.

`GPUDrawLayer` owns an immutable logical layer or composite scope.

`GPUDrawLayerPlanner` owns layer graph construction and pass partitioning.

`GPURecording` owns the immutable result of recording.

`GPUTaskList` owns ordered GPU work and resource dependencies.

`GPUDrawPass` owns immutable draw-pass data close to GPU submission.
The handle-free packet boundary from `GPUDrawPass` into final frame planning
and preflight command-stream materialization is defined in
`37-draw-packet-command-stream.md`.

`GPUTaskList` is the dependency authority. `GPUFramePlan` is its immutable,
deterministic linear execution schedule and never a parallel task model.
`GPUFrameCoordinator` is the sole product entry across planner finalization,
`GPUFramePreflighter`, and `GPUFrameExecutor`. The coordinator performs no
route decision and preserves planning or preflight refusals as terminal frame
outcomes; neither a scene nor a surface entry may bypass it.

`GPURenderStep` owns the geometry/coverage technique used by a draw inside a
pass.

`GPUPayloadGatherer` owns payload writing and pass-local uniform/resource
slot assignment during pass construction.

`GPUPathAtlasPlan` and `GPUCoverageAtlasPlan` own path/coverage atlas
selection facts, entry keys, mutation requirements, and diagnostics as defined
in `19-path-coverage-atlas-strategy.md`.

`GPUClipPlan` owns captured clip descriptor execution facts, effective element
selection, bounds, scissor, analytic clip, stencil producer-consumer plans,
coverage-mask plans, shader clip plans, budgets, ordering tokens, and
diagnostics as defined in `24-clip-stencil-mask-pipeline.md`.
Common clip bounds and clip-reduction proof policy comes from
`30-coordinate-transform-bounds-policy.md`.

`GPUDestinationReadPlan` owns destination-read requirements, bounds, target
snapshot/intermediate strategy, pass-split actions, and diagnostics as defined
in `20-destination-read-strategy.md`.
Common reverse bounds, copy rounding, and fragment-to-copy coordinate policy
comes from `30-coordinate-transform-bounds-policy.md`.

`GPULayerExecutionPlan` owns executable saveLayer lowering, offscreen targets,
initialization/backdrop, source filtering, restore composite, layer elision,
ordering tokens, layer resources, budgets, and diagnostics as defined in
`28-layer-savelayer-execution.md`.

`GPUTextRunPlan` and `GPUTextSubRunPlan` own text/glyph route selection,
subrun splitting, text atlas resource needs, upload dependencies, instance
buffer facts, and text diagnostics as defined in
`21-text-glyph-pipeline.md`.

Analysis and recording are handle-free. Immutable `GPUDrawAnalysis` records
capture pre-allocation decisions and `GPUTaskList` captures dependencies.
Resource, pipeline, atlas, upload, lazy/promise resource, destination-copy,
pass-command-stream, and concrete-handle outcomes are confirmed or refused
only by `GPUFramePreflighter` after the final `GPUFramePlan` order is known, as
defined in `34-analysis-materialization-recording.md`.

## `GPURecorder`

`GPURecorder` accepts normalized draw commands and a target configuration. It
does not accept `SkCanvas` operations.

It is responsible for:

- validating command invariants;
- constructing `GPUDrawAnalysis`;
- selecting candidate routes for analysis records;
- deriving or requesting `MaterialKey` values for analysis records;
- selecting `GPURenderStep` candidates for analysis records;
- invoking `GPUOcclusionTracker` for conservative culling facts;
- invoking `GPUDrawLayerPlanner` for layer and pass plans;
- assigning recording-local command IDs;
- creating tasks;
- collecting deterministic diagnostics.

It must not:

- mutate legacy Canvas state;
- compile arbitrary SkSL;
- submit work directly to a surface;
- silently render unsupported work through CPU fallback.

## `GPUDrawAnalysis`

`GPUDrawAnalysis` is an immutable analysis product for one recorder snap. It is
explicit: route selection, culling, layer assignment, and ordering facts must
not be hidden inside `GPUDrawPass` construction.

It is a pre-materialization product. It may name candidate routes, resource
declarations, render-step candidates, and refusal reasons, but it must not
claim that concrete resources, pipelines, atlases, uploads, destination copies,
or lazy resources have been materialized.

It contains one analysis record per normalized command that reaches the core,
including commands that are later culled, discarded, or refused.

Each analysis record contains:

- recording-local command ID and adapter provenance;
- normalized command family and conservative bounds;
- transform, clip, layer, material, and ordering summaries;
- selected route or stable refusal reason;
- `MaterialKey` or material refusal;
- candidate `GPURenderStep` identities or render-step refusal;
- geometry and coverage strategy facts;
- clip plan facts, effective element decisions, scissor/analytic/stencil/mask
  requirements, and `GPUClipOrderingToken` dependencies when a route is
  clipped;
- path/coverage atlas plan facts, atlas entry requirements, and
  `GPUAtlasMutationPlan` dependencies when a route may sample atlas coverage;
- text run/subrun plan facts, text atlas entry requirements, text upload plans,
  and `GPUTextOrderingToken` dependencies when a route draws glyph artifacts;
- opacity and blend classification;
- semantic destination-read requirements, conservative bounds, and dependency
  roles when a route observes previous destination pixels; the concrete
  `GPUDestinationReadPlan` does not exist until preflight;
- clip, stencil, upload, atlas, and target-load dependencies;
- `GPUTextureOwnershipPlan` references for image or texture sources;
- layer assignment request;
- occlusion classification;
- resource declarations known before allocation;
- deterministic diagnostic fields.

`GPUDrawAnalysis` may contain Graphite-equivalent notes for review, but its API
is Kanvas-owned. It must not expose Graphite `DrawList`, `DrawPass`, or
`Renderer` classes, Graphite package names, or Graphite bit layouts as core
contracts.

Analysis may classify a command as unsupported before pass construction.
Unsupported analysis records remain visible in diagnostics and PM evidence
instead of disappearing from the recording.

## `GPUOcclusionTracker`

`GPUOcclusionTracker` is the dedicated occlusion-culling capability for the new
renderer.

It consumes ordered `GPUDrawAnalysis` records and layer facts, then emits
conservative culling facts back into the analysis or layer plan. Occlusion is a
first-class target capability, not an incidental optimization in the pass
builder.

It may cull only when correctness is proven from explicit facts:

- draw or layer bounds are finite and conservative;
- clip and transform classifications preserve the culling proof;
- the covering draw is opaque in the relevant target encoding;
- blend mode and destination-read facts do not require the hidden draw;
- layer isolation and composite semantics do not need the hidden contents;
- barriers, clears, discards, and target changes are respected.

It must not:

- depend on object identity or nondeterministic traversal order;
- use exact floating-point equality as the only proof for coverage;
- cull across destination-read, shader-read, image-filter, or layer-composite
  dependencies unless a later accepted spec defines that proof;
- hide culling from diagnostics.

Occlusion diagnostics must name the culled command or layer, the covering scope,
the proof category, and the stable reason code. When proof is incomplete, the
tracker must leave the command visible.

## `SortKey`

`SortKey` is a deterministic, Graphite-like sort key for draw invocations
inside the Kanvas renderer.

It is used after `GPUDrawAnalysis` has made dependencies explicit. A `SortKey`
may improve batching and pipeline locality only within the legal reordering
window described by the analysis record and layer plan.

A `SortKey` includes behavior-affecting ordering axes such as:

- layer and target scope;
- original paint-order band or barrier generation;
- explicit dependency class;
- render-step identity;
- `MaterialKey` or material group;
- render or compute pipeline-key group when known before resource preparation;
- clip/stencil preparation group;
- destination-read and blend classification;
- atlas or upload generation when it affects legality.
- texture ownership or target generation when it affects legal ordering.

It must not include:

- per-draw uniform values;
- command object addresses;
- transient buffer offsets;
- cache hit/miss state;
- any axis that would allow sorting across an explicit barrier.

The nearest Graphite equivalent is Graphite's `SortKey` ordering concept, but
Kanvas does not inherit Graphite's bit packing, source package, or class
ownership. The Kanvas key must expose a diagnostic preimage before any batching
or culling claim is promoted.

## `GPUDrawLayer` And `GPUDrawLayerPlanner`

`GPUDrawLayer` is an immutable logical layer or composite scope produced from
captured normalized command state. It is not a replay of `saveLayer` and
`restore` calls.

`GPUDrawLayer` is the low-level pass/layer planning structure used by the GPU
renderer. It consumes the higher-level `GPULayerPlan` and `GPUFilterPlan`
contracts defined in `08-layer-and-filter-plans.md`; it does not replace those
semantic plans.

A layer contains:

- stable layer ID and parent scope;
- target attachment facts;
- conservative bounds;
- command range or command IDs assigned to the layer;
- opacity, blend, alpha, and color-space composite facts;
- destination-read and prior-target-content requirements;
- intermediate texture or direct-to-parent classification;
- load, clear, discard, and store intent;
- dependency edges to clip, stencil, upload, and child-layer work;
- culling facts from `GPUOcclusionTracker`;
- diagnostics and stable refusal reasons.

`GPUDrawLayerPlanner` consumes `GPUDrawAnalysis`, `GPULayerPlan`,
`GPULayerExecutionPlan`, `GPUFilterPlan`, target facts, and
`GPUCapabilities`. It produces a
deterministic low-level draw-layer plan and a pass partitioning proposal.
The detailed invocation expansion, backward/forward insertion, sort-window,
and merge policy is defined in `15-draw-layer-planner-and-sort-policy.md`.
Detailed saveLayer execution classes, layer targets, initialization,
restore/composite, elision, and ordering tokens are defined in
`28-layer-savelayer-execution.md`.

It is responsible for:

- expanding accepted analysis records into `GPUDrawInvocation` values;
- assigning commands to `GPUDrawLayer` scopes;
- deciding whether a layer may draw directly into its parent or requires an
  intermediate target;
- consuming `GPULayerExecutionPlan` classes such as direct-to-parent,
  offscreen, backdrop-initialized, filter-isolated, composite-only, cull, or
  refusal;
- preserving layer isolation, alpha, blend, and destination-read semantics;
- creating dependency edges between parent and child layers;
- applying occlusion facts at layer granularity when proven safe;
- exposing stable refusal diagnostics when layer semantics cannot be executed;
- producing sort windows that `GPUDrawPass` may use.
- producing `GPUDrawInsertion` diagnostics for reordered, merged, or original
  order invocations.

It must not allocate GPU resources, create WebGPU pipelines, or encode commands.
Those responsibilities stay with `GPUResourceProvider`, `GPUTaskList`, and the
pass tasks.

## `GPURecording`

`GPURecording` is the immutable product of a recorder snap.

It contains:

- target facts;
- `GPUDrawAnalysis`;
- layer plan;
- task list;
- material and pipeline key references;
- payload write plans and payload slot references;
- required resource declarations;
- route diagnostics;
- feature and capability assumptions.

A `GPURecording` may be replayed only when its resource and capability
assumptions are still valid. If replay safety cannot be proven, the recording
must be treated as one-shot.

## `GPUTaskList`

`GPUTaskList` is the ordered dependency authority for handle-free tasks. A task
declares render, compute, copy, upload, output, or refusal intent plus resource
roles and dependency tokens. It does not allocate a resource, acquire a
surface, create a pass command stream, hold a concrete handle, or encode
against the `GPU` facade.

`GPUFramePlanner` validates dependencies and projects the list into one
immutable `GPUFramePlan`. It preserves task IDs, dependency seals, recording
insertion order, route decisions, and refusals. A cycle, incompatible replay
key, or unisolatable dependency is an atomic planning failure. Only after this
order is final may `GPUFramePreflighter` materialize resources and the
one-to-one command-encoder plan.

Atlas mutations from `19-path-coverage-atlas-strategy.md` are resource
preparation work. A task that samples a path or coverage atlas must depend on
the upload, compute write, page activation, eviction, or split-pass retry plan
that made the entry valid.
Clip stencil producers, clip mask uploads or compute writes, clip shader mask
resources, and clip ordering tokens from `24-clip-stencil-mask-pipeline.md`
are resource preparation and ordering requirements. A task that draws through a clip
must depend on its accepted `GPUClipPlan`, `GPUClipStencilPlan` or
`GPUClipMaskPlan` when present, and any `GPUCoverageAtlasPlan` or
`GPUCoverageAtlasBinding` it references.
Destination-read target copies and isolated intermediates from
`20-destination-read-strategy.md` are resource preparation and ordering requirements. A
task that samples a copied destination or existing intermediate must depend on
the corresponding copy/intermediate validation plan.
Layer target allocation, transparent clear, previous-content copy, backdrop
filter initialization, child layer rendering, source filter execution,
restore composite, and layer target release from
`28-layer-savelayer-execution.md` are resource preparation and ordering requirements.
A task that samples or composites a layer source must depend on its accepted
`GPULayerTaskPlan`, `GPULayerResourcePlan`, and `GPULayerOrderingToken`.
Filter graph intermediates, render/compute node resources, runtime-effect
bindings, and ordering tokens from `23-filter-effect-pipeline.md` are resource
preparation and ordering work. A task that consumes a filter node output must
depend on its accepted `GPUFilterNodePlan`, `GPUFilterIntermediatePlan`,
`GPUFilterOrderingToken`, and any destination-read or image-source plan it
references.

Text atlas uploads, atlas generation validation, text instance buffer uploads,
and text artifact resource bindings from `21-text-glyph-pipeline.md` are
resource preparation and ordering requirements. A task that samples a text atlas or
bitmap glyph texture must depend on its accepted `GPUTextUploadPlan`,
`GPUTextAtlasEntryRef`, `GPUTextBinding`, and `GPUTextOrderingToken`.
Image decode artifacts, animated frame selection/composition, mip generation,
and image upload plans from `22-image-bitmap-codec-pipeline.md` are resource
preparation and ordering work. A task that samples an uploaded image texture
must depend on its accepted `GPUImageUploadPlan`,
`GPUImageUploadArtifactKey`, `UploadedTextureArtifact`, and
`GPUTextureOwnershipPlan`.

Task outcomes:

- `Success`: task remains valid and may be replayable under its assumptions.
- `Discard`: task was consumed or optimized away.
- `Fail`: recording is invalid and must report a stable diagnostic.

## `GPUDrawPass`

`GPUDrawPass` is immutable after creation. It consumes `GPUDrawAnalysis` and
the `GPUDrawLayerPlanner` output, then records immutable, handle-free packet
intent. `GPUPassCommandStream` is produced later in preflight after the final
frame order is known.
It must not rediscover route, material, layer, culling, or dependency facts
from raw normalized commands.

It contains:

- target attachment facts;
- layer scope and pass partition identity;
- load/store operations;
- clear/discard intent;
- immutable pass commands expanded into render-step invocations;
- `SortKey` preimages for commands that may be sorted;
- pipeline keys;
- dynamic uniform and resource binding references;
- pass-level barriers and diagnostics.

`GPUDrawPass` is allowed to sort, cull, and merge draw-step invocations when
analysis and layer ordering facts prove correctness. If the pass changes
ordering, it must preserve the diagnostic trail from original command ID to
analysis record, layer, sort key, and materialized pass command.

Pass planning must preserve refused and discarded analysis outcomes in
diagnostics. A command that cannot become a pass command must report whether it
was culled, discarded as redundant, refused, or blocked by resource planning.

## `GPURenderStep`

`GPURenderStep` describes the fixed geometry and coverage contribution for a
draw invocation.

It owns:

- vertex/instance input requirements;
- primitive topology;
- depth/stencil requirements;
- coverage behavior;
- WGSL geometry/coverage fragment contribution;
- fixed state contribution to `GPURenderPipelineKey`;
- supported geometry/material compatibility checks.

A command may produce multiple render-step invocations. For example, a filled
stroke or a coverage technique with an inner fill may need more than one step.

## Ordering And Barriers

The task graph must preserve:

- paint-order dependencies for blending;
- clip or stencil preparation dependencies;
- destination-read dependencies;
- layer isolation and composite dependencies;
- occlusion proof boundaries;
- clip stencil producer, clip mask mutation, and clip shader mask dependencies;
- atlas mutation and upload dependencies;
- text atlas upload, instance buffer upload, and atlas generation dependencies;
- target load/store correctness.

Optimization is allowed only after those dependencies are explicit in
`GPUDrawAnalysis`, `GPUDrawLayer`, and `SortKey` diagnostics.

## Preflight Resource Materialization

After `GPUFramePlan` finalization, `GPUFramePreflighter` uses
`GPUResourceProvider` to produce `GPUResourceMaterializationDecision` values,
pass command streams, concrete handles hidden inside the prepared resource set,
and either rollback or one `PreparedGPUFrame`. It must produce deterministic
diagnostics for:

- unsupported capabilities;
- analysis or layer plan refusal;
- pipeline creation failure;
- WGSL validation or reflection failure;
- resource allocation failure;
- intermediate layer target allocation failure;
- atlas capacity failure;
- texture format mismatch;
- device loss or invalid generation.

## Non-Goals

- Do not model all Graphite task classes.
- Do not add a backend abstraction beyond the existing `GPU` facade.
- Do not expose `GPUDrawPass` as a public Skia-like API.
- Do not assume recordings are reusable across devices or capability changes.
- Do not implement broad render-graph scheduling in this kernel.
- Do not hide draw analysis, layer planning, or occlusion decisions as private
  pass-builder side effects.
- Do not treat this target spec as an implementation order or vertical slice.

## Deferred Display List Contract

A deferred display list (`GPUDeferredDisplayList`) allows a `NormalizedDrawCommand`
sequence to be recorded once and replayed across multiple frames or targets.
This is a Kanvas-internal replay contract, not a product API.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUDeferredDisplayList` | Immutable recorded command sequence + analysis decisions + layer plans. Can be composed with a new CTM, clip, and target for replay. |
| `GPUDeferredDisplayListCompatibilityKey` | Keyed by recording ID, command sequence hash, and replay-compatible fields (excludes target, surface lease, and mutable resource generations). |
| `GPUDeferredDisplayListReplayPlan` | Per-replay plan: apply composed CTM, intersection clip, target substitution, re-execute analysis/layer planning (lightweight), produce new task list. |
| `GPUDeferredDisplayListCachePlan` | Cache plan for frequently replayed display lists. Keyed by compatibility key + replay CTM class + replay clip class. |
| `GPUDeferredDisplayListDiagnostic` | Refusal for incompatible replay (format change, capability change, device generation mismatch) or cache budget exceeded. |

### Target Acceptance

```
Recording Session
  -> GPURecorder records N commands
  -> snapshots as GPUDeferredDisplayList
  -> later frame: replay with new CTM/clip
  -> GPUDeferredDisplayListReplayPlan produces new GPUTaskList
  -> GPUFrameCoordinator
```

The replay path avoids full material-source re-evaluation when the recording is
compatible. Only analysis decisions dependent on CTM/clip/target are recalculated.

### Non-Goals

- Do not expose `GPUDeferredDisplayList` as a public Skia-like API.
- Do not allow cross-device replay until device-loss recovery is proven.
- Do not claim performance improvement without measured evidence.

## Subpass Merging Policy

When a render pass produces intermediate textures consumed by later passes,
subpass merging combines them into a single render pass with input attachments
where the adapter supports it.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUSubpassMergePlan` | Accepted merge: producer pass (color attachment), consumer pass (input attachment), compatible formats, and no intervening barriers that prevent merging. |
| `GPUSubpassMergeDiagnostic` | Refusal for incompatible format, intervening texture barrier, vendor limitation, or merged pass exceeding max attachment count. |

### Merge Conditions

Two passes can be merged into subpasses when:
1. The producer's color attachment is the consumer's input attachment.
2. Both passes share the same render pass scope (same surface target or offscreen).
3. No copy, upload, barrier, or readback is required between them.
4. Adapter supports `inputAttachment` reads (`maxColorAttachments` permits the layout).
5. Both passes use the same sample count.

### Acceptance Gates

- At least one producer-consumer pair (blur horizontal -> blur vertical) merged into subpasses with GPU evidence.
- Non-mergeable pair produces stable refusal with explicit reason.
- Subpass merge does not regress pixel output compared to sequential passes.
