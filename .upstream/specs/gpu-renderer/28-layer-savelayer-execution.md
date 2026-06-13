# Layer And SaveLayer Execution

Status: Draft
Date: 2026-06-13

## Purpose

Define the target execution contract for Canvas-style `saveLayer`, layer
restore, offscreen isolation, layer initialization, backdrop input, attached
filters, direct-to-parent elision, restore composite, and layer resource
lifetimes in the GPU-first renderer.

`08-layer-and-filter-plans.md` defines `GPULayerPlan` as the high-level
semantic layer contract. This spec defines how an accepted `GPULayerPlan`
becomes executable GPU work through `GPULayerExecutionPlan` and related layer
task/resource plans.

This is a target-complete spec. It is not an implementation slice. The first
rect/rrect plus solid/linear-gradient slice remains governed by
`14-first-slice-contract.md` and may validate layer refusal or trivial elision
diagnostics without claiming broad `saveLayer` support.

The target is Graphite-inspired but Kanvas-owned:

- layer semantics are explicit plans, not hidden Canvas stack state;
- offscreen layer targets are ordinary `GPUResourceProvider` resources;
- restore/composite is a planned GPU operation with blend, color,
  destination-read, clip, and filter facts;
- direct-to-parent elision is allowed only with a proof;
- unsupported layers refuse with stable diagnostics instead of silently
  dropping paint, filters, clips, backdrop reads, or layer alpha;
- CPU may provide oracle evidence, but it must not render a full unsupported
  layer into a product GPU texture.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module, naming, Graphite evidence policy, and
  no-hidden-fallback rules;
- `01-normalized-draw-commands.md` for captured layer command facts and child
  command provenance;
- `02-gpu-recording-task-graph.md` for `GPURecorder`, `GPUDrawAnalysis`,
  `GPUTaskList`, `GPUDrawPass`, and task ownership;
- `04-pipeline-key-cache-resources.md` for resource provider, cache, resource,
  device generation, and typed artifact policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for promotion gates and evidence;
- `08-layer-and-filter-plans.md` for high-level `GPULayerPlan` and
  `GPUFilterPlan` semantics;
- `09-draw-family-support-matrix.md` for layer target maturity and evidence
  rows;
- `10-gpu-execution-context-submission.md` for command scopes, target
  generations, submission, and readback evidence;
- `11-wgsl-layout-binding-abi.md` for layer composite bindings and packing;
- `12-blend-color-target-state.md` for `GPUBlendPlan`, `GPUColorPlan`, and
  `GPUTargetState`;
- `29-color-management-pipeline.md` for layer target value specs, F16 color
  behavior, color-space restoration, source/filter/composite conversions, and
  store diagnostics;
- `30-coordinate-transform-bounds-policy.md` for layer-local coordinate
  spaces, creation/restore transform facts, offscreen origin mapping,
  bounds-hint policy, conservative bounds proofs, and rounding;
- `13-performance-telemetry-cache-gates.md` for layer counters and performance
  gates;
- `15-draw-layer-planner-and-sort-policy.md` for low-level draw layer
  insertion, ordering, and sort windows;
- `18-texture-image-ownership.md` for `GPUTargetTextureDescriptor`,
  `GPUTextureOwnershipPlan`, target texture views, samplers, and surface
  leases;
- `20-destination-read-strategy.md` for destination/backdrop/source reads and
  target-copy/intermediate ordering;
- `23-filter-effect-pipeline.md` for filter DAG execution and intermediates;
- `24-clip-stencil-mask-pipeline.md` for layer creation/restore clip
  constraints;
- `27-registered-runtime-effects-registry.md` when registered runtime effects
  participate in layer filters, blenders, or future clip shader routes.

## Skia And Graphite Evidence

Relevant local evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/`.

Useful source landmarks:

- `SkCanvas::saveLayer()` saves matrix and clip, allocates a drawing surface
  for later draws, and composites that surface when `restore()` is called.
- `SkCanvas::SaveLayerRec` records bounds, paint, side-car filters, backdrop
  filter, backdrop tile mode, optional color space, `kInitWithPrevious`, LCD
  preservation, and F16 color-type requests.
- Skia documents layer bounds as a size hint, not a clip. A separate clip must
  be applied if content is intended to be clipped.
- Restore paint may apply alpha, color filter, image filter, and blend mode
  while compositing the layer into the parent.
- Backdrop or `kInitWithPrevious` behavior observes prior parent contents and
  initializes the new layer from those contents before child draws run.
- Skia has `SaveLayerStrategy` and record optimizations that can suppress or
  fold some layers when the proof is narrow, for example simple opacity
  patterns.
- `SkDevice::snapSpecial()`, `drawSpecial()`, `drawDevice()`, and
  `SkSpecialImage` show the source/intermediate-image model used by filters and
  layer restore paths.
- Graphite tracks destination-read bounds through `DrawListBase`,
  `RenderPassTask`, `CommandBuffer`, `DstUsage`, and `DstReadStrategy`.
- Graphite's experimental `DrawListLayer` direction separates ordered layer
  insertion from final immutable draw-pass materialization.

Kanvas adopts these invariants:

- saveLayer is a semantic boundary before it is an optimization boundary;
- bounds hints, active clips, filter expansion, backdrop reads, and restore
  clips are separate facts;
- layer source rendering, optional filter execution, and restore composite are
  separate planned stages;
- prior destination/backdrop contents require explicit read plans;
- a separate texture or target is required whenever WebGPU cannot legally read
  and write the same attachment;
- eliding a layer requires a dumpable proof, not a heuristic;
- failure to allocate, copy, filter, or composite produces a stable refusal.

Kanvas intentionally does not copy:

- Skia device stack classes, `SkSpecialImage` ownership, or `SkCanvas`
  internals;
- Ganesh or Graphite backend abstractions;
- Graphite class names, C++ arenas, sort-key bit layouts, or backend-specific
  destination-read strategies;
- SkSL layer composite shaders;
- CPU-rendered full-layer compatibility textures.

## Ownership Boundary

The legacy adapter owns:

- recognizing save, restore, saveLayer, saveLayerAlpha, saveLayer-like
  auto-layer, and restore-to-count calls;
- capturing layer bounds hints, layer paint, side-car filters, backdrop
  filters, flags, color-space requests, save/restore stack provenance, active
  transform, and active clip into normalized layer facts;
- preserving ordering between child commands and restores;
- translating Skia-like API types into Kanvas-owned descriptors before calling
  `:gpu-renderer`.

The `:gpu-renderer` core owns:

- `GPULayerExecutionPlan`;
- `GPULayerScopeID`;
- `GPULayerSaveRecord`;
- `GPULayerRestorePlan`;
- `GPULayerBoundsPlan`;
- `GPULayerTargetPlan`;
- `GPULayerInitializationPlan`;
- `GPULayerBackdropPlan`;
- `GPULayerSourcePlan`;
- `GPULayerFilterChainPlan`;
- `GPULayerCompositePlan`;
- `GPULayerElisionPlan`;
- `GPULayerTaskPlan`;
- `GPULayerResourcePlan`;
- `GPULayerOrderingToken`;
- `GPULayerCachePlan`;
- `GPULayerBudgetPolicy`;
- `GPULayerDiagnostic`.

Owned by other specs:

- high-level semantic layer plan: `08-layer-and-filter-plans.md`;
- filter graph details: `23-filter-effect-pipeline.md`;
- blend, color, alpha, target format: `12-blend-color-target-state.md`;
- destination/backdrop/source reads: `20-destination-read-strategy.md`;
- clip route details: `24-clip-stencil-mask-pipeline.md`;
- texture/target ownership: `18-texture-image-ownership.md`;
- pass/layer insertion and sorting: `15-draw-layer-planner-and-sort-policy.md`;
- GPU command encoding and submission: `10-gpu-execution-context-submission.md`;
- WGSL ABI and composite shader binding layouts:
  `11-wgsl-layout-binding-abi.md`;
- runtime-effect descriptors used by filters or blenders:
  `27-registered-runtime-effects-registry.md`.

`GPULayerExecutionPlan` may reference the products owned by these specs. It
must not re-derive filter bounds, invent destination-read routes, choose clip
routes, allocate resources directly, or encode commands itself.

## Core Objects

| Object | Purpose |
|---|---|
| `GPULayerExecutionPlan` | Executable lowering product for one accepted or refused `GPULayerPlan`. |
| `GPULayerScopeID` | Stable recording-local identity for a layer scope and its parent relationship. |
| `GPULayerSaveRecord` | Captured saveLayer input facts after adapter normalization. |
| `GPULayerRestorePlan` | Restore-time state, paint, filter, blend, color, clip, and parent target facts. |
| `GPULayerBoundsPlan` | Conservative source, child, filter, backdrop, target, and composite bounds. |
| `GPULayerTargetPlan` | Offscreen target descriptor, format, usage flags, load/clear/store policy, and lifetime. |
| `GPULayerInitializationPlan` | Transparent, previous-content, backdrop, or refused initialization route. |
| `GPULayerBackdropPlan` | Parent-content input, backdrop filter, tile mode, bounds, and destination-read plan. |
| `GPULayerSourcePlan` | Child draw source isolation, source target, source bounds, and source-read facts. |
| `GPULayerFilterChainPlan` | Ordered layer-source filters and their `GPUFilterPlan`/intermediate products. |
| `GPULayerCompositePlan` | Final restore composite into parent with blend/color/destination-read/clip facts. |
| `GPULayerElisionPlan` | Proof that the layer can draw directly to the parent, or reason it cannot. |
| `GPULayerTaskPlan` | Ordered task consequence: allocate, initialize, draw children, filter, composite, release, or refuse. |
| `GPULayerResourcePlan` | Required target textures, views, samplers, buffers, copies, intermediates, and lifetimes. |
| `GPULayerOrderingToken` | Planner-local dependency token for child draws, init reads, filters, composite, and parent work. |
| `GPULayerCachePlan` | Cache and invalidation policy for reusable analysis, target descriptors, and filter/composite products. |
| `GPULayerBudgetPolicy` | Limits for layer target dimensions, bytes, count, nesting, pass splits, and copied backdrop bytes. |
| `GPULayerDiagnostic` | Structured accepted/refused diagnostic for the layer execution path. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Layer Semantics

`GPULayerSaveRecord` records:

- layer scope ID and parent scope ID;
- normalized source command provenance;
- save count or stack provenance from the adapter;
- bounds hint, when provided;
- layer paint descriptor;
- side-car filter descriptors;
- backdrop filter descriptor;
- backdrop tile mode;
- optional layer color-space request;
- saveLayer flags: preserve LCD text, init with previous, F16 target request,
  and any accepted Kanvas-specific flags;
- local-to-device transform at layer creation;
- clip stack descriptor and effective clip facts active at creation;
- target/surface descriptor and generation at creation;
- child command range or child command IDs;
- restore provenance and restore-time parent facts when known.

Rules:

- Bounds hint is not a clip. It can constrain allocation only when
  `GPULayerBoundsPlan` proves no required source, backdrop, filter, or
  composite pixel is removed.
- Active clip at layer creation constrains what child draws can contribute, but
  final restore clipping is a separate restore-time fact.
- Nested layers preserve parent/child ordering through `GPULayerScopeID` and
  `GPULayerOrderingToken`.
- Restore-to-count that pops multiple scopes produces one restore plan per
  popped layer in stack order.
- Empty child content may still require work when backdrop, init-with-previous,
  restore paint, destination read, or side effects are observed.
- Unsupported flags refuse with stable diagnostics. They must not be ignored.

## Bounds Planning

`GPULayerBoundsPlan` computes conservative bounds for:

- requested bounds hint;
- child draw source bounds after creation-time transform and clip;
- active creation clip;
- backdrop or previous-content input bounds;
- filter input and output bounds;
- restore paint and composite bounds;
- restore-time clip;
- parent target intersection;
- integer offscreen allocation bounds;
- destination-read copy bounds when needed.

Bounds rules:

- Floating bounds are converted to integer target bounds with conservative
  rounding.
- Filter expansion is applied before final restore clip unless
  `23-filter-effect-pipeline.md` proves a narrower crop is equivalent.
- Backdrop input bounds are driven by what the backdrop filter samples, not
  only by child draw bounds.
- Init-with-previous requires parent-content bounds even when the layer has no
  child draws.
- An unbounded layer may use a full-target route only when the layer budget
  policy and target usage flags accept it.
- A layer with invalid, NaN, negative, or unproven bounds refuses unless an
  accepted full-target route is explicitly available.
- Bounds reduction by clip is legal only when `GPUClipBoundsPlan` proves the
  reduction does not remove pixels observed by filters, backdrop reads, or the
  final composite.

## Isolation And Elision

`GPULayerExecutionPlan` chooses one execution class:

| Class | Meaning |
|---|---|
| `DirectToParent` | Child draws execute in the parent target; saveLayer/restore is semantically elided. |
| `OffscreenLayer` | Child draws execute into a separate GPU target, then composite into the parent. |
| `BackdropInitializedLayer` | The offscreen target is initialized from parent content and optional backdrop filtering before child draws. |
| `FilterIsolatedLayer` | The source is isolated, then passed through `GPUFilterPlan` before composite. |
| `CompositeOnlyLayer` | Source is already an accepted intermediate and only restore composite remains. |
| `CullEmptyLayer` | The whole layer has no observable output and can be culled with proof. |
| `RefuseDiagnostic` | No accepted route preserves semantics. |

`GPULayerElisionPlan` may select `DirectToParent` only when all are proven:

- no backdrop filter and no init-with-previous requirement;
- no layer-source filter, restore image filter, or side-car filter requires a
  source image;
- layer alpha is equivalent to applying alpha to each child draw, or no alpha
  effect exists;
- restore blend is equivalent to direct child blending in the parent;
- restore color filter and color-space conversion are absent or proven
  distributive over every child draw;
- creation clip and restore clip are equivalent to parent draw clips for every
  child;
- child ordering can stay inside the parent draw-layer scope without crossing
  a destination-read, atlas mutation, clip/stencil atomic group, upload,
  target, or nested-layer barrier;
- no child, filter, or future runtime effect observes the isolated source;
- diagnostics can dump the proof.

If any proof is missing, the planner must keep the layer isolated or refuse.
Elision is an optimization, never a fallback.

`CullEmptyLayer` is legal only when:

- source bounds are empty;
- backdrop/init-with-previous is absent;
- filters and restore paint have no observable output;
- destination reads do not observe the layer;
- restore blend and clip cannot affect parent output;
- diagnostics record the culling proof.

## Offscreen Target Planning

`GPULayerTargetPlan` records:

- target descriptor ID and version;
- layer scope ID;
- target bounds and origin;
- texture dimensions;
- sample count or coverage mode;
- color format;
- alpha type and premul convention;
- color-space/working-space facts;
- F16 request acceptance or refusal;
- usage flags: render attachment, texture binding, copy source, copy
  destination, storage binding when a compute filter accepts it, and readback
  only for evidence lanes;
- load operation: clear, load previous content, discard, or refused;
- clear color and clear alpha interpretation;
- store operation and discard policy;
- texture/view/sampler descriptor hashes when sampled;
- lifetime: layer-local, recording-local, frame-local, or refused;
- owner scope and target generation;
- budget class and byte estimate.

Rules:

- Offscreen layer targets are provider-owned GPU resources.
- A layer target is not a `CPUPreparedGPU` artifact.
- The active parent attachment cannot be sampled as the layer source or
  destination.
- If the layer target will be sampled by a filter or composite, texture-binding
  usage must be present before allocation.
- If the layer target will be copied, copy-source or copy-destination usage
  must be present as required.
- The target origin and layer-local coordinate mapping must be part of payload
  and diagnostic dumps.
- A layer target may be reused only when descriptor, generation, usage, format,
  dimensions, sample count, color/alpha facts, and lifetime policy match.

## Layer Initialization

`GPULayerInitializationPlan` chooses:

| Initialization | Meaning |
|---|---|
| `TransparentClear` | Clear the isolated target to transparent black before child draws. |
| `PreserveUninitialized` | Allowed only for a proven full overwrite before any read. |
| `CopyPreviousContent` | Copy parent content into the layer target before child draws. |
| `BackdropFilterInput` | Copy/sample parent content, run backdrop filter, and write the result into the layer target. |
| `SampleExistingIntermediate` | Use an already separate validated parent/backdrop intermediate. |
| `RefuseDiagnostic` | Initialization cannot be proven or materialized. |

Rules:

- Default saveLayer initialization is transparent black.
- `kInitWithPrevious` requires `CopyPreviousContent` or
  `SampleExistingIntermediate`.
- A non-null backdrop filter requires `BackdropFilterInput`; it implies prior
  parent content is observed.
- Previous-content and backdrop routes use `GPUDestinationReadPlan` with
  bounded read facts and copy/intermediate ordering.
- Backdrop tile mode is part of the plan and must be validated by the filter
  route or refused.
- Backdrop scale factors are refused until an accepted spec defines their
  coordinate and sampling contract.
- Previous-content copy bounds must be conservative and clipped only when the
  clip plan proves equivalence.
- Initialization failure refuses the layer. It must not silently clear when
  previous content or backdrop was requested.

## Layer Source And Filters

`GPULayerSourcePlan` records:

- child command IDs;
- child draw target: parent target for elided layers or layer target for
  isolated layers;
- source bounds after child draw planning;
- child clip, transform, atlas, image, text, runtime-effect, and
  destination-read dependencies;
- nested child layer dependencies;
- whether the source is readable as a sampled texture after rendering;
- source generation after child draws complete.

`GPULayerFilterChainPlan` records:

- ordered filter descriptors from side-car filters and restore paint;
- attached `GPUFilterPlan` values;
- filter input source: layer target, initialized backdrop, existing
  intermediate, or refused;
- filter output bounds and intermediate descriptors;
- filter destination/backdrop read needs;
- render/compute/copy route facts;
- color-space and alpha interpretation;
- filter budget decisions;
- stable diagnostics.

Rules:

- Filter DAG execution is owned by `23-filter-effect-pipeline.md`.
- A layer with source filters must keep the source readable as a separate
  texture or intermediate unless that spec proves direct evaluation is
  equivalent.
- Side-car filters and paint image filters must have deterministic order.
- Unsupported filters refuse the layer. They must not be dropped.
- Filter intermediates are provider-owned GPU resources unless
  `FilterIntermediateArtifact` is explicitly accepted by spec 23.
- CPU reference filter behavior is evidence only and is not a product fallback.

## Restore And Composite

`GPULayerRestorePlan` records:

- restore command provenance;
- parent layer/target scope;
- restore-time transform and clip;
- layer paint alpha;
- layer paint color filter;
- layer paint image filter or side-car filters already lowered into
  `GPULayerFilterChainPlan`;
- restore blend mode;
- optional color-space conversion;
- target color format and alpha/premul convention;
- destination-read requirement;
- composite bounds;
- parent target generation before composite;
- selected composite route or refusal.

`GPULayerCompositePlan` records:

- source: layer target, filter output, initialized backdrop result, existing
  intermediate, or refused;
- source texture/view/sampler binding facts;
- source-to-parent coordinate mapping;
- restore clip plan reference;
- `GPUBlendPlan`;
- `GPUColorPlan`;
- `GPUDestinationReadPlan` when restore blend or shader composite observes
  parent destination;
- `WGSLModule` or fixed-function blend path when needed;
- pipeline-key and ABI facts;
- payload/resource binding facts;
- target generation update;
- stable diagnostics.

Composite rules:

- Fixed-function blend is preferred when `GPUBlendPlan` proves it preserves
  restore semantics.
- Shader composite that needs destination color must use an accepted
  `GPUDestinationReadPlan`.
- Source texture and parent target must be separate resources while sampling
  and writing.
- Restore clip is applied to the final composite unless the layer source/filter
  route proves an equivalent earlier clip.
- Layer alpha is applied at the restore/composite stage unless
  `GPULayerElisionPlan` proves it can be pushed into children.
- Color filter and color-space conversion must be validated in
  `GPUColorPlan`, material/filter WGSL, or refused.
- `kPreserveLCDText` is dependency-gated by `21-text-glyph-pipeline.md`.
  Until the text route proves subpixel/LCD preservation through layer
  isolation and restore, this flag refuses or downgrades only when explicitly
  accepted by text diagnostics.
- F16 target requests require an accepted F16 format, color plan, filter plan,
  color-management plan, filter plan, and composite route.

## Ordering And Task Graph

`GPULayerTaskPlan` is consumed by `GPUTaskList`.

Task stages:

1. Validate bounds, color, clip, destination-read, and budget facts.
2. Choose elision, culling, offscreen, backdrop-initialized, filter-isolated,
   composite-only, or refusal route.
3. Allocate or reuse layer target resources when needed.
4. Initialize the layer target through clear, previous-content copy, backdrop
   filter, or existing intermediate.
5. Execute child draw tasks targeting the accepted layer target or parent
   target.
6. Execute source filters and produce filter intermediates when required.
7. Execute restore composite into the parent target.
8. Release or retain resources according to lifetime policy.
9. Emit deterministic diagnostics and telemetry.

Ordering rules:

- Initialization must complete before child draws that read or write the layer
  target.
- Child draws must complete before filters that consume the source.
- Filters must complete before restore composite.
- Restore composite must complete before later parent draws that observe the
  composite output.
- Destination-copy/backdrop tasks close sort windows unless a disjoint proof is
  recorded.
- Parent draws must not be reordered across a layer restore when the restore
  may affect their destination.
- Child draws must not move outside the layer scope unless
  `GPULayerElisionPlan` proves direct-to-parent equivalence.
- Nested layers create ordering tokens between parent child draws, nested
  source rendering, nested restore composite, and outer composite.
- Atlas mutations, uploads, clip/stencil producers, destination reads, and
  target generation changes remain barriers according to their owning specs.

`GPULayerOrderingToken` is planner-local. It may participate in sort windows,
barrier diagnostics, and task dependencies, but it must not enter durable
material keys.

## Interaction With Draw Layer Planner

`GPUDrawLayerPlanner` consumes `GPULayerExecutionPlan` and produces low-level
`GPUDrawLayer` scopes.

Rules:

- An isolated semantic layer usually creates at least one child draw-layer
  scope targeting the layer target and one composite invocation targeting the
  parent.
- Direct-to-parent elision removes the offscreen scope but keeps diagnostic
  provenance for the original semantic layer.
- A layer composite is a draw invocation with restore blend/color/clip facts.
- The planner may sort and batch child draws only within the layer scope.
- The planner may sort and batch composite invocations only inside legal parent
  windows after the layer source is complete.
- Destination-read, backdrop, filter, clip/stencil, atlas, upload, target, and
  nested-layer tokens stop movement unless their owning specs prove safety.
- A planner split, atlas retry, or destination-copy split is legal only when it
  preserves layer source, filter, and restore ordering.

## Resource Lifetimes And Cache

`GPULayerResourcePlan` records:

- layer target descriptors;
- backdrop copy descriptors;
- filter intermediate descriptors;
- composite source bindings;
- texture view and sampler descriptors;
- storage texture/buffer resources when compute filters accept them;
- clear/copy/render pass resource usages;
- generation dependencies;
- release timing;
- budget and cache facts.

Cache keys may include:

- layer execution plan version;
- layer scope ID only for recording-local caches;
- bounds plan hash;
- target descriptor hash;
- initialization route and backdrop descriptor hash;
- filter graph descriptor hash;
- restore blend/color/composite route hash;
- clip plan hash when it affects bounds or composite;
- destination-read plan hash when it affects copies or bindings;
- resource usage flags and target generation when they affect validity.

Cache keys must not include:

- concrete GPU handle addresses;
- transient command object identity;
- uniform values that do not affect layout or route;
- child texture contents;
- cache hit/miss state;
- CPU oracle pixels.

Layer target resource reuse is performance evidence only. It is not correctness
evidence unless generation, bounds, usage, and content provenance are proven.

## Budget Policy

`GPULayerBudgetPolicy` records:

- maximum isolated layer count per recording and frame;
- maximum nesting depth;
- maximum layer target dimensions;
- maximum layer target bytes;
- maximum aggregate live layer bytes;
- maximum backdrop copy bytes;
- maximum filter intermediate bytes caused by layers;
- maximum pass split count caused by layers;
- maximum full-target layer count;
- accepted F16 layer budget;
- stale resource rebuild/refusal policy;
- hard capability limits versus configurable policy limits.

Budget rules:

- Budget pressure may refuse a layer.
- The renderer must not substitute a lower-quality layer target silently.
- The renderer must not drop filters, backdrop, alpha, blend, clip, or color
  conversion to fit budget.
- A configurable budget refusal and a hard capability refusal must have
  different diagnostics.

## Diagnostics

Every accepted, elided, culled, or refused layer emits `GPULayerDiagnostic`.

Fields:

- layer scope ID and parent scope ID;
- save/restore provenance;
- execution class;
- bounds hint, source bounds, filter bounds, backdrop bounds, composite bounds,
  allocation bounds, and copied bounds;
- creation clip and restore clip summaries;
- target descriptor hash, usage flags, format, dimensions, sample count,
  color/alpha/premul facts, and generation;
- initialization route;
- backdrop plan and destination-read plan when present;
- filter chain and intermediate summary;
- blend plan, color plan, composite route, and destination-read plan when
  present;
- elision or culling proof when selected;
- ordering token and task dependency summary;
- resource lifetime and budget summary;
- cache hits/misses when relevant;
- selected route or stable refusal reason.

Stable reason-code examples:

- `unsupported.layer.bounds_invalid`
- `unsupported.layer.bounds_unbounded`
- `unsupported.layer.target_too_large`
- `unsupported.layer.target_format`
- `unsupported.layer.target_usage_missing`
- `unsupported.layer.f16_unavailable`
- `unsupported.layer.preserve_lcd_text`
- `unsupported.layer.init_previous_unaccepted`
- `unsupported.layer.backdrop_filter`
- `unsupported.layer.backdrop_tile_mode`
- `unsupported.layer.backdrop_scale`
- `unsupported.layer.previous_content_copy`
- `unsupported.layer.active_attachment_sampled`
- `unsupported.layer.filter_chain`
- `unsupported.layer.filter_intermediate`
- `unsupported.layer.restore_blend`
- `unsupported.layer.restore_color_filter`
- `unsupported.layer.color_space_conversion`
- `unsupported.layer.destination_read`
- `unsupported.layer.restore_clip`
- `unsupported.layer.elision_proof_missing`
- `unsupported.layer.ordering_unproven`
- `unsupported.layer.pass_split_illegal`
- `unsupported.layer.budget_exceeded`
- `unsupported.layer.nesting_depth`
- `unsupported.layer.cpu_fallback_forbidden`

Planner/reporting reason-code examples:

- `layer.elide.direct_to_parent`
- `layer.elide.rejected.alpha`
- `layer.elide.rejected.filter`
- `layer.elide.rejected.backdrop`
- `layer.elide.rejected.destination_read`
- `layer.cull.empty_unobservable`
- `layer.task.allocate_target`
- `layer.task.clear_transparent`
- `layer.task.copy_previous_content`
- `layer.task.apply_backdrop_filter`
- `layer.task.render_children`
- `layer.task.apply_source_filter`
- `layer.task.composite_parent`
- `layer.task.release_target`

## Telemetry

`GPUTelemetryLedger` records layer counters:

- saveLayer count;
- direct-to-parent elision count;
- offscreen layer count;
- backdrop-initialized layer count;
- filter-isolated layer count;
- composite-only layer count;
- culled layer count;
- refused layer count by reason;
- target allocation count and bytes;
- maximum live layer bytes;
- layer target reuse count;
- F16 request/accept/refusal count;
- preserve-LCD request/refusal count;
- init-with-previous count and copied bytes;
- backdrop filter count and copied/intermediate bytes;
- layer source filter count;
- restore composite count by blend plan;
- layer-induced destination-read count;
- layer-induced pass split count;
- layer nesting depth histogram;
- layer budget pressure count;
- layer elision proof success/failure count.

Performance reports must distinguish:

- semantic layer support;
- offscreen allocation support;
- filter support;
- restore composite support;
- elision optimization;
- realtime performance readiness.

## Validation Requirements

Promoted layer behavior requires:

- canonical dumps for `GPULayerExecutionPlan`, `GPULayerSaveRecord`,
  `GPULayerRestorePlan`, `GPULayerBoundsPlan`, `GPULayerTargetPlan`,
  `GPULayerInitializationPlan`, `GPULayerBackdropPlan`,
  `GPULayerSourcePlan`, `GPULayerFilterChainPlan`,
  `GPULayerCompositePlan`, `GPULayerElisionPlan`, `GPULayerTaskPlan`,
  `GPULayerResourcePlan`, `GPULayerOrderingToken`,
  `GPULayerBudgetPolicy`, and `GPULayerDiagnostic`;
- normalized saveLayer fixtures covering bounds hints, clips, nested layers,
  saveLayerAlpha, restore-to-count, flags, and paint;
- direct-to-parent elision positive and negative fixtures;
- offscreen target allocation, clear, child draw, sample, composite, and
  release fixtures;
- init-with-previous fixtures using `GPUDestinationReadPlan`;
- backdrop filter fixtures with bounds, tile mode, and refusal cases;
- layer source filter fixtures through `23-filter-effect-pipeline.md`;
- restore blend/color/color-filter/color-space fixtures through
  `12-blend-color-target-state.md`;
- active attachment sampling refusal tests;
- destination-read ordering and pass-split tests;
- clip-at-creation and clip-at-restore tests;
- nested layer ordering tests;
- budget pressure tests for dimensions, bytes, depth, and pass splits;
- resource generation/stale-resource refusal tests;
- WGSL validation and ABI reflection for any composite shader route;
- CPU oracle or reference comparison for promoted semantics;
- GPU evidence for every promoted offscreen/composite route;
- PM evidence exposing route counts, bytes, pass splits, refusals, elisions,
  and budgets.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice may validate layer
boundaries but must not claim broad `saveLayer` support.

It may validate:

- normalized saveLayer command capture into diagnostics;
- stable refusal for nontrivial `saveLayer`;
- direct-to-parent elision for a no-op layer only when proof is complete;
- refusal for init-with-previous, backdrop, source filters, restore filters,
  unsupported restore blend, F16, preserve LCD text, and destination-read layer
  cases;
- telemetry counters for refused or elided layers.

It must not claim support for:

- offscreen layer allocation and restore composite;
- layer alpha unless direct-to-parent equivalence is proven by fixtures;
- backdrop filters;
- `kInitWithPrevious`;
- F16 layers;
- preserve-LCD text through layers;
- source or restore image filters;
- shader destination-read restore composites;
- nested layer optimization beyond deterministic refusal or original-order
  diagnostics.

Those routes require later evidence against this spec.

## Non-Goals

- Do not replay a mutable Canvas stack inside `:gpu-renderer`.
- Do not treat layer bounds hints as clips.
- Do not silently ignore layer paint, filters, backdrop, flags, alpha, blend,
  clip, or color-space requests.
- Do not sample the active parent attachment while writing it.
- Do not hide layer execution inside `MaterialKey`.
- Do not use a CPU-rendered full-layer texture as product fallback.
- Do not copy Skia `SkDevice`, `SkSpecialImage`, Ganesh, or Graphite backend
  ownership models.
- Do not claim `saveLayer` support from direct-to-parent elision alone.
