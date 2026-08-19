# Destination Read Strategy

Status: Draft
Date: 2026-06-13

## Purpose

Define the target strategy for draws, layer composites, filters, and shader
blend routes that need the previous destination color.

The GPU renderer must make destination reads explicit. WebGPU does not provide
a portable framebuffer-fetch feature and does not allow sampling the active
color attachment while writing it. Kanvas therefore supports destination reads
only through planned GPU routes: fixed-function blend where no shader read is
needed, a copied destination texture, an isolated layer/intermediate, or a
stable refusal.

This spec is a target contract, not an implementation slice. It defines the
full architecture surface required by future blend modes, shader blends,
backdrop/filter work, saveLayer interactions, and advanced compositing.

Common coordinate-space, reverse-bounds, pixel-grid, copy rounding, and
precision policy is defined in `30-coordinate-transform-bounds-policy.md`.
This spec owns destination-read strategy and resources; it consumes common
`GPUBoundsPlan`, `GPUBoundsProof`, and `GPURoundingPlan` facts.

## Graphite Evidence

Graphite's useful model is conceptual:

- `DstUsage` distinguishes draws whose prior destination pixels can affect the
  output from draws that require the destination color in shader code.
- `DstReadStrategy` includes no read, texture copy, texture sample,
  input-attachment read, and framebuffer fetch.
- `DrawList` and `DrawListLayer` collect `dstReadBounds` for draws that require
  destination reads.
- `Device::needsFlushBeforeDraw()` flushes before a draw that needs texture
  copy so the destination surface can be copied before the draw records.
- `DrawContext` creates a destination copy texture for the rounded
  destination-read bounds when the selected strategy is texture copy.
- `RenderPassTask` carries the destination copy and read bounds into command
  encoding.
- `CommandBuffer` binds the destination copy texture and sampler separately
  from normal paint textures when the pipeline uses texture-copy reads.
- Hardware blending can clear the destination-read strategy back to no read
  when the blend equation is expressible by fixed-function GPU state.

Kanvas adopts these invariants:

- destination dependency is a planning fact, not hidden shader behavior;
- read bounds are tracked and rounded conservatively;
- a copy/intermediate texture must be separate from the active attachment;
- pass splitting and copy-before-sample ordering are explicit;
- fixed-function blending avoids shader destination reads where possible;
- failure to create or use the read source becomes `RefuseDiagnostic`.

Kanvas intentionally does not copy:

- Graphite classes, C++ ownership, or pipeline bit layouts;
- SkSL destination read code generation;
- input attachment or framebuffer-fetch routes as accepted WebGPU behavior;
- log-only draw drops when destination copy creation fails;
- CPU-rendered texture compatibility for unsupported blend or filter modes.

## Ownership Boundary

Owned by this spec:

- `GPUDestinationReadPlan`;
- `GPUDestinationReadStrategyPlanner`;
- `GPUDestinationReadStrategy`;
- `GPUDestinationReadClass`;
- `GPUDestinationReadBounds`;
- `GPUDestinationReadAction`;
- `GPUDestinationReadBudgetPolicy`;
- `GPUDestinationCopyPlan`;
- `GPUDestinationCopyTextureDescriptor`;
- `GPUDestinationReadBinding`;
- `GPUDestinationReadToken`;
- `GPUDestinationReadDiagnostic`.

`passes` owns the canonical blend mode, `GPUBlendPlan`, and semantic
`GPUBlendDestinationReadRequirement`. This spec consumes that closed semantic
requirement; it never reinterprets a blend mode, coverage formula,
fixed-function state, shader formula identity, or opacity specialization.
`GPULayerPlan`, `GPUFilterPlan`, and `GPUClipPlan` declare
destination/backdrop/source-read and clip-bound semantics for layers, filters,
and clipped draws. Detailed filter node, bounds, sample-radius, and
intermediate behavior is defined in `23-filter-effect-pipeline.md`. Detailed
clip descriptor, bounds, stencil, mask, and ordering behavior is defined in
`24-clip-stencil-mask-pipeline.md`.
Detailed layer/saveLayer execution, including previous-content initialization,
backdrop input, source filters, restore composite, elision, and layer ordering
tokens, is defined in `28-layer-savelayer-execution.md`.
Common coordinate, transform, and bounds proof policy is defined in
`30-coordinate-transform-bounds-policy.md`.
`GPUDrawAnalysis` records semantic requirements, bounds, and dependency facts.
`GPUDrawLayerPlanner` preserves ordering and split barriers. `GPUTaskList`
remains the handle-free dependency authority. After `GPUFramePlan` finalizes
that order, `GPUFramePreflighter` invokes `GPUDestinationReadStrategyPlanner`
and `GPUResourceProvider` to choose and materialize copies, intermediates,
texture ownership, and payload bindings.

`GPUPayloadGatherer` may consume an accepted `GPUDestinationReadBinding`; it
must not allocate the copy texture, copy the target, split passes, or invent a
destination-read route.

## WebGPU Constraint

The target assumes the portable `GPU` facade has no framebuffer fetch and no
subpass input attachment route.

Accepted product routes must not:

- sample the active color attachment while it is bound for writing;
- depend on undefined read/write aliasing;
- rely on backend-specific framebuffer fetch;
- rely on Vulkan input attachments, Metal framebuffer-only reads, or Dawn-only
  extensions unless a future accepted spec replaces this policy;
- read destination pixels through CPU readback for product rendering.

If a future facade implementation exposes a portable destination-read feature,
it must enter through a new strategy mode and validation gate. It cannot silently
replace copy/intermediate semantics.

## Core Objects

### Semantic Input

For blend-driven reads, `GPUDestinationReadStrategyPlanner` consumes
`GPUBlendDestinationReadRequirement.None`,
`GPUBlendDestinationReadRequirement.DestinationTextureRequired`, or
`GPUBlendDestinationReadRequirement.Refused` from `passes`. Layer, filter,
backdrop, and registered runtime-effect planners provide equivalent typed
semantic inputs with their provenance. No input is inferred from shader source
strings, and this planner cannot turn a semantic refusal into a materialized
route.

### `GPUDestinationReadBounds`

Records conservative read bounds:

- command or layer provenance;
- local, device, and target coordinate facts when relevant;
- unclipped draw bounds;
- clipped read bounds;
- integer copy bounds after rounding and target intersection;
- target dimensions and target generation;
- whether bounds are finite, empty, or unbounded;
- expansion caused by filter radius, blur, sampling kernel, or backdrop input;
- clipping intersection only when `GPUClipBoundsPlan` proves that reducing the
  read bounds preserves all pixels observed by the draw, layer, or filter;
- diagnostic reason when bounds are invalid.

Bounds must be conservative. If the renderer cannot prove finite bounds for a
destination read, it must choose an accepted full-target route within budget or
refuse.

### `GPUDestinationReadStrategy`

Selects the target strategy:

| Strategy | Meaning |
|---|---|
| `NoDestinationRead` | No previous destination pixels are needed. |
| `FixedFunctionAttachmentBlend` | Destination contribution is handled by attachment blend state. |
| `TargetCopySnapshot` | Copy bounded destination pixels into a sampled texture before the draw. |
| `SampleExistingIntermediate` | Sample an already separate layer/filter/intermediate texture whose generation and bounds match the read requirement. |
| `LayerCompositeIsolation` | Render or preserve source/backdrop through a separate layer/intermediate target before compositing. |
| `RefuseDiagnostic` | No accepted route can preserve semantics. |

`FramebufferFetch`, `InputAttachment`, and direct `TextureSampleActiveTarget`
are intentionally not accepted strategy values for this WebGPU target.

### `GPUDestinationReadClass`

`GPUDestinationReadClass` is the planner-local ordering class derived from the
strategy and bounds:

- `None`;
- `FixedFunctionOnly`;
- `CopySnapshotProducer`;
- `CopySnapshotConsumer`;
- `ExistingIntermediateConsumer`;
- `LayerIsolationProducer`;
- `LayerIsolationComposite`;
- `Refused`.

It is used by sort, merge, culling, and pass partitioning. It is not a durable
key and must not be used outside the planner/pass product that created it.

### `GPUDestinationReadPlan`

The preflight materialization product for one draw, layer composite, or filter
node after final frame order is known:

- requirement;
- strategy;
- read bounds;
- source target descriptor and generation;
- destination copy or intermediate descriptor when used;
- texture ownership plan for the read source;
- payload binding requirement;
- pass split or barrier action;
- blend/color/target-state interaction;
- layer/filter interaction;
- memory, copy, and binding budget decisions;
- route outcome or refusal reason.

Analysis and recording carry only the semantic requirement, conservative
bounds, target/resource roles, and dependency tokens. After `GPUFramePlan`
finalization, `GPUDestinationReadStrategyPlanner` creates this plan together
with the matching `GPUResourceMaterializationDecision`; payload bindings and
pass command streams then consume the accepted result. It is not a material
key and cannot change blend semantics.

### `GPUDestinationReadAction`

Records the concrete preflight/encoder consequence selected after final
`GPUFramePlan` order for destination-read intent whose semantics, conservative
bounds, target/resource roles, and dependencies are already fixed:

| Action | Meaning |
|---|---|
| `KeepInPass` | No split or copy is required. |
| `UseFixedFunctionBlend` | Use attachment blend state and no sampled destination. |
| `SplitPassAndCopyTarget` | Close pending writes, copy target bounds, then sample the copy. |
| `UseExistingIntermediate` | Bind a separate validated intermediate texture. |
| `CreateIsolatedLayer` | Materialize and render the isolated target already declared by the semantic layer plan. |
| `CompositeIsolatedLayer` | Encode the composite required by the already-declared child/parent target roles and dependency. |
| `Refuse` | Return a stable refusal diagnostic. |

`GPUTaskList` and `GPUDrawLayerPlanner` carry only semantic destination-read
intent, conservative bounds, target/resource roles, and dependency tokens.
Neither consumes this action nor accepts any late materialization product.
After final `GPUFramePlan` order, `GPUFramePreflighter` produces and consumes
`GPUDestinationReadAction` while building the prepared
`GPUCommandEncoderPlan`; `GPUFrameExecutor` exclusively records the resulting
commands. Layer action variants instantiate roles already declared by semantic
layer plans; they do not introduce isolation, change blend/filter/layer
semantics, or reroute the command stream after final ordering. Actions remain
dumpable so pass splits and copies are visible in evidence.

### `GPUDestinationCopyTextureDescriptor`

Describes a texture created only to read prior destination pixels:

- source target ID and generation;
- copied bounds;
- copy dimensions;
- texture format and color-space/premul interpretation;
- usage flags: copy destination and texture binding are required; copy source
  is required only if the copy itself will be read or recopied later;
- sample count: destination copies are single-sample sampled textures unless a
  future spec accepts multisample read semantics;
- lifetime: pass-local, recording-local, frame-local, or layer-local;
- owner scope;
- budget class;
- diagnostic label.

This descriptor is not an image source descriptor and not an uploaded texture
artifact. It describes a provider-owned GPU copy of previous target contents.

### `GPUDestinationCopyPlan`

Records copy work:

- source target texture descriptor or surface lease;
- source bounds and target generation;
- destination copy texture descriptor;
- copy command scope;
- pass split required before copy;
- copy-before-sample dependency;
- color/alpha interpretation;
- copy byte estimate;
- budget decision;
- failure and refusal behavior.

After final `GPUFramePlan` order, `GPUFramePreflighter` asks
`GPUResourceProvider` to materialize the copy resources and includes the copy
in the prepared `GPUCommandEncoderPlan`. `GPUFrameExecutor` exclusively records
that planned copy into the frame's one encoder. `GPUTaskList` remains
handle-free dependency authority, and the draw that samples the copied
destination must depend on the copy plan.

### `GPUDestinationReadBudgetPolicy`

Declares accepted policy limits for destination snapshots and isolated
intermediates:

- maximum destination copy bytes per frame and per recording;
- maximum copy count per pass, frame, and recording;
- maximum sampled area per draw;
- maximum live snapshot/intermediate bytes;
- maximum pass split count caused by destination reads;
- full-target copy policy;
- stale generation rebuild/refusal policy;
- hard capability limits versus configurable policy limits.

Diagnostics must distinguish a hard capability refusal from a configurable
budget refusal.

Preflight applies one aggregate scratch budget across canonical scene bytes,
persistent/retained MSAA color and depth-stencil, layer/filter targets,
destination snapshots, readback staging, and other scratch. It records logical
and backing dimensions, aligned requested bytes, live/reusable/evictable bytes,
device limits, `peakFrameTransientBytes`, and `targetResidentBytes`. Reuse is
legal only after real queue completion. No historical fixed per-copy ceiling
defines feature support.

### `GPUDestinationReadBinding`

Payload/resource binding record used by WGSL shader blend or filter code:

- destination read plan ID;
- destination copy texture descriptor hash;
- `GPUTextureOwnershipPlan` ID;
- `GPUTextureViewDescriptor` hash;
- `GPUSamplerDescriptor` hash;
- binding layout hash;
- read bounds in target coordinates;
- inverse copy dimensions;
- coordinate transform from fragment position or layer-local coords to copy
  texture coords;
- target generation;
- payload/resource binding slot when materialized.

The binding stays out of `MaterialKey`. `GPURenderPipelineKey` may include
only ABI/layout/access facts required for executable validity.

### `GPUDestinationReadToken`

Records the ordering relationship between:

- writes that produce the target contents observed by the read;
- the copy or isolated intermediate that captures those contents;
- draws that sample the copy or intermediate;
- later writes to the target.

The token is scoped to a `GPURecording`, `GPUTaskList`, or frame target
generation. It is not a durable identity and must not enter material keys.

## Strategy Semantics

### `NoDestinationRead`

Use when previous destination pixels do not affect output. It allows normal
sort windows, batching, and direct rendering as long as other barriers allow
it.

### `FixedFunctionAttachmentBlend`

Use when `GPUBlendPlan` proves the blend can be expressed by fixed-function
attachment state for the target format and color plan.

Rules:

- no destination texture binding is created;
- no copy or isolated layer is created solely for blend;
- pipeline key includes fixed-function blend state;
- sort/batch may group compatible fixed-function blend draws inside legal
  order windows;
- unsupported fixed-function state falls through to another accepted route or
  refusal.

### `TargetCopySnapshot`

Use when WGSL needs destination color and a bounded copy can preserve semantics.

Rules:

- pending writes to the source target must complete before the copy;
- the source target must have copy-source usage or a valid surface/target copy
  lease;
- the copied texture must be separate from the active attachment;
- the draw pass that samples the copy starts after the copy dependency;
- read bounds must be finite, rounded conservatively, and intersected with the
  target;
- shader sampling uses validated coordinate mapping from fragment/layer coords
  to copy texture coords;
- nearest sampling is the default for pixel-exact destination color unless a
  filter/backdrop spec accepts another sampling policy;
- copy failure refuses the affected draw/layer/filter.

`TargetCopySnapshot` is the primary portable WebGPU route for shader
destination reads. Its materialization is either `NativeTextureCopy`, when the
source has copy-source usage, or `CopyAsDrawMaterialization`, when a real
non-copyable source is nevertheless texturable. Copy-as-draw remains part of
the same strategy and must execute on the frame's one encoder. A
non-copyable, non-texturable source refuses; CPU readback/upload is never a
materialization.

The canonical `GPUSceneTarget` is Kanvas-owned with copy-source usage, so its
ordinary route is `NativeTextureCopy`. A destination snapshot closes the
active render-pass segment, records the bounded copy, and resumes a later pass
on the same encoder. Persistent MSAA continuation keeps the authoritative
attachment alive across the break; a fresh transient MSAA attachment is not a
valid continuation.

### Bounded Snapshot Grouping

Snapshot sharing is target-scoped and may occur only after final task order is
known. A group key includes target and device generation, format/color
interpretation, sample/MSAA continuation state, and source-intermediate
identity. It closes on target/layer/filter/generation transitions,
incompatible sample state, composite refusal, an intersecting intervening
write, source change, or budget/cost rejection.

Bounds are expanded conservatively, rounded by floor/ceil, intersected with
clip and target bounds, and kept separate from pooled backing size. Sharing
must prove non-intersection with writes and pass a deterministic checked-in
cost calibration. Until that calibration exists, the policy is one bounded
snapshot per destination-reading draw. No distant union may grow into an
effectively full-target copy merely to save a pass break.

### `SampleExistingIntermediate`

Use when a separate texture already exists with the exact source generation,
read bounds, color interpretation, and lifetime required by the plan.

Examples:

- a filter graph input produced by an earlier validated node;
- a layer source texture already isolated by `GPULayerPlan`;
- a target snapshot created by a preceding plan in the same task graph.

Rules:

- the intermediate must be separate from the active attachment;
- its generation and bounds must match the read requirement;
- its color/alpha interpretation must match `GPUColorPlan`;
- lifetime must cover every consumer draw;
- stale or ambiguous intermediates refuse.

This route avoids redundant copies, but cache or resource reuse is not
correctness evidence.

### `LayerCompositeIsolation`

Use when preserving semantics requires an offscreen layer or intermediate
instead of a per-draw target copy.

Examples:

- saveLayer composite whose restore blend needs stable parent destination;
- saveLayer initialization with previous parent contents;
- saveLayer backdrop filter input;
- backdrop filter requiring a parent-content snapshot;
- filter graph requiring source and destination textures as separate inputs;
- command groups where one copy per draw would be incorrect or impossible.

Rules:

- `GPULayerPlan` owns semantic isolation requirements;
- `GPULayerExecutionPlan`, `GPULayerInitializationPlan`,
  `GPULayerBackdropPlan`, and `GPULayerCompositePlan` own the executable layer
  stages that consume this strategy;
- `GPUFilterPlan` and `23-filter-effect-pipeline.md` own filter DAG reads and
  writes;
- target texture descriptors follow `18-texture-image-ownership.md`;
- direct-to-parent elision is illegal unless the destination-read plan proves
  equivalence;
- isolated resources are ordinary GPU resources, not CPU-prepared fallback
  images;
- layer/intermediate budget pressure can refuse.

### `RefuseDiagnostic`

Return refusal when the renderer cannot preserve destination-read semantics.

Refusal is required when:

- read bounds are invalid, unbounded, or exceed accepted full-target policy;
- the target lacks required usage flags;
- the current surface lease cannot be copied or sampled safely;
- the route would sample the active attachment;
- pass splitting would break ordering, layer isolation, stencil/clip atomicity,
  atlas mutation, or target load/store semantics;
- copy/intermediate resource allocation exceeds budget;
- shader blend WGSL or binding ABI is unvalidated;
- color-space, alpha, premul, or format conversion is unaccepted;
- the route would require CPU readback or CPU-rendered texture compatibility.

## Planner, Barriers, And Sorting

Destination reads are hard ordering facts.

Rules:

- `GPUDrawAnalysis` records requirement, bounds, and selected plan;
- a destination-read draw cannot move before a prior intersecting draw whose
  output it observes;
- a draw that writes to an observed region cannot move before the copy or
  intermediate capture;
- copy and isolated-layer tasks close sort windows unless proven disjoint;
- pass split for destination copy is explicit and diagnostic;
- destination-read tokens prevent unsafe reordering across atlas mutations,
  uploads, stencil/clip atomic groups, layer boundaries, and target changes;
- culling cannot remove draws observed by a destination read;
- batching may occur only after the plan proves compatible read source,
  binding layout, bounds, generation, and blend/color state.

If a planner cannot prove safety, it keeps original order or refuses. It must
not silently drop the read or use stale target contents.

## Texture Ownership And Payload

Destination copy textures and isolated layer targets are provider-owned GPU
resources.

Rules:

- source target and copy texture use `GPUTargetTextureDescriptor` or
  `GPUTextureOwnershipPlan` according to `18-texture-image-ownership.md`;
- surface/swapchain sources require a valid `GPUSurfaceTextureLease` with copy
  or sample usage;
- active attachment sampling refusal from `18-texture-image-ownership.md`
  remains mandatory;
- destination copy texture bindings use `GPUDestinationReadBinding`;
- `GPUPayloadGatherer` records binding payloads but does not create copies or
  intermediates;
- destination read bindings use the artifact/resource group from
  `11-wgsl-layout-binding-abi.md` unless a later ABI spec accepts another
  group.

Destination copy textures are not `UploadedTextureArtifact`,
`CoverageMaskArtifact`, or `FilterIntermediateArtifact` by default. They are
ordinary GPU resources created from previous target contents.
`23-filter-effect-pipeline.md` may use a `FilterIntermediateArtifact` only when
CPU preparation creates a typed artifact accepted by that spec; it must not be
used for product CPU fallback.

## Color And Blend Interaction

`GPUBlendPlan` is the front door for blend-driven destination reads.

Rules:

- fixed-function blend is preferred when target format, color plan, alpha
  convention, and capabilities accept it;
- shader blend routes that need destination color must carry
  `GPUDestinationReadPlan`;
- shader blend routes that do not need destination color remain
  `ShaderBlendNoDstRead`;
- color-space and premul conversion facts must be in `GPUColorPlan`;
- destination copy format must preserve the color values observed by blend or
  declare and validate conversion;
- hardware advanced blend with noncoherent barriers is a separate capability
  gate; WebGPU cannot be assumed to provide it.

Unsupported blend modes refuse with stable reasons rather than falling back to
CPU-rendered texture composition.

## Routing Policy

Destination-read route selection follows:

1. `NoDestinationRead` when requirement is none.
2. `FixedFunctionAttachmentBlend` when `GPUBlendPlan` proves native attachment blend.
3. `LayerCompositeIsolation` when layer/filter semantics require stable
   offscreen or backdrop inputs.
4. `SampleExistingIntermediate` when a validated separate texture already
   preserves the needed destination contents.
5. `TargetCopySnapshot` when WGSL needs destination color and bounded GPU
   `NativeTextureCopy` or `CopyAsDrawMaterialization` is accepted.
6. `RefuseDiagnostic`.

Analysis and `GPUTaskList` preserve the semantic need, bounds, and ordering;
the selected concrete plan is created only in preflight after `GPUFramePlan`
order is final. It is not a payload-only or shader-only decision.

`CPUReferenceOnly` may compute oracle output for tests. It is never a product
destination-read route.

## Budgets And Telemetry

Destination-read budgets are separate from texture upload and atlas budgets.

Budget inputs:

- maximum destination copy dimensions;
- maximum copy area;
- maximum copy bytes per draw, layer, frame, or recording;
- maximum number of destination copies per pass/frame;
- maximum isolated layer/intermediate bytes;
- full-target copy policy;
- pass split count policy;
- binding slot pressure;
- capability lane for copy and texture sampling.

Telemetry counters:

- destination-read requirement count by kind;
- plan count by strategy;
- read bounds area and copied bytes;
- destination copy texture count and bytes;
- isolated layer destination count and bytes;
- pass split count;
- copy-before-sample dependency count;
- fixed-function avoidance count;
- active-attachment sampling refusal count;
- stale target generation refusal count;
- budget pressure/refusal count;
- color/format conversion refusal count.

Cache or resource reuse is performance evidence only. A reused destination copy
must still prove target generation and bounds validity.

## Diagnostics

Every accepted or refused destination-read route emits
`GPUDestinationReadDiagnostic`.

Fields:

- command, layer, filter, or composite ID;
- requirement kind;
- selected strategy;
- read bounds and copied bounds;
- target descriptor hash and target generation;
- source surface lease facts when present;
- copy/intermediate descriptor hash when used;
- texture/view/sampler descriptor hashes when sampled;
- binding layout hash and payload/resource slot when materialized;
- pass split and barrier actions;
- destination-read token;
- blend plan and color plan IDs;
- layer/filter plan IDs when used;
- copy byte estimate and budget decision;
- capability facts that affected selection;
- route outcome or stable refusal reason.

Stable reason-code examples:

- `unsupported.destination_read.required`
- `unsupported.destination_read.strategy_unaccepted`
- `unsupported.destination_read.bounds_invalid`
- `unsupported.destination_read.bounds_unbounded`
- `unsupported.destination_read.copy_unavailable`
- `unsupported.destination_read.copy_usage_missing`
- `unsupported.destination_read.texture_binding_missing`
- `unsupported.destination_read.intermediate_unvalidated`
- `unsupported.destination_read.active_attachment_sampled`
- `unsupported.destination_read.surface_lease_stale`
- `unsupported.destination_read.generation_stale`
- `unsupported.destination_read.target_generation_stale`
- `unsupported.destination_read.ordering_unproven`
- `unsupported.destination_read.budget_exceeded`
- `unsupported.destination_read.copy_budget_exceeded`
- `unsupported.destination_read.layer_budget_exceeded`
- `unsupported.destination_read.pass_split_illegal`
- `unsupported.destination_read.shader_route_unvalidated`
- `unsupported.destination_read.binding_unavailable`
- `unsupported.destination_read.color_plan_unvalidated`
- `unsupported.destination_read.color_conversion_unvalidated`
- `unsupported.destination_read.framebuffer_fetch_unavailable`
- `unsupported.destination_read.cpu_readback_forbidden`

## Validation Requirements

Promoted destination-read behavior requires:

- canonical dumps for `GPUDestinationReadPlan`,
  consumed `GPUBlendDestinationReadRequirement`, `GPUDestinationReadStrategy`,
  `GPUDestinationReadBounds`, `GPUDestinationReadAction`,
  `GPUDestinationReadBudgetPolicy`, `GPUDestinationCopyPlan`,
  `GPUDestinationCopyTextureDescriptor`, `GPUDestinationReadBinding`,
  `GPUDestinationReadToken`, and `GPUDestinationReadDiagnostic`;
- fixed-function positive and negative blend fixtures;
- shader-destination-read refusal fixture before copy route promotion;
- bounded destination-copy positive fixture before shader blend promotion;
- active attachment sampling refusal test;
- stale target/surface generation refusal test;
- pass split and copy-before-sample ordering tests;
- planner tests proving destination-read barriers stop sort and merge;
- layer elision negative tests when destination reads are observed;
- payload tests proving destination read bindings stay out of `MaterialKey`;
- texture ownership tests for copy source, copy destination, texture binding,
  and active attachment separation;
- color/premul/format conversion tests when conversion is claimed;
- GPU evidence for copied destination sampling or explicit refusal;
- CPU oracle or reference comparison for promoted blend/filter semantics;
- PM evidence exposing route counts, bytes, pass splits, refusals, and budgets.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice does not promote
shader destination reads, destination copy textures, backdrop filters, or
destination-dependent blend modes.

It may validate:

- `NoDestinationRead` plans for accepted fixed simple draws;
- `FixedFunctionAttachmentBlend` only for the accepted blend subset;
- active attachment sampling refusal through texture ownership fixtures;
- destination-read-required blend refusal with stable diagnostics.

The first slice must not create destination copy textures, sample existing
destination intermediates, or isolate destination layers as supported rendering
routes.

## Non-Goals

- Do not add framebuffer fetch to the WebGPU target.
- Do not rely on backend-specific input attachments.
- Do not sample the active color attachment.
- Do not encode destination copy textures in `MaterialKey`.
- Do not hide pass splits or copy tasks inside payload gathering.
- Do not use CPU readback to implement product destination reads.
- Do not CPU-render unsupported blends, filters, layers, or scenes into a
  texture for GPU composition.
- Do not claim advanced blend support from fixed-function blend availability
  without per-mode validation.
- Do not treat destination-copy cache reuse as correctness evidence.
