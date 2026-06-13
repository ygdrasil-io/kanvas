# Draw Layer Planner And Sort Policy

Status: Draft
Date: 2026-06-13

## Purpose

Define the Graphite-inspired draw-layer insertion, sort, cull, and merge policy
for the new GPU renderer.

This spec closes the first known Graphite gap in the target: the existing
recording/task graph names `GPUDrawLayerPlanner`, `GPUDrawLayer`, `SortKey`,
and `GPUDrawPass`, but it does not yet define the target insertion algorithm.
Kanvas should be deeply inspired by Graphite's `DrawList` and experimental
`DrawListLayer` behavior while keeping Kanvas-owned names, diagnostics, and
data shapes.

## Graphite Evidence

Relevant Graphite concepts:

- `DrawList` records draw commands, expands them into render-step sort keys,
  sorts by paint/stencil/render-step/pipeline/uniform/texture axes, culls, and
  merges compatible GPU work during `snapDrawPass`.
- `DrawListLayer` records into ordered layers, searches backward for compatible
  insertion points, records later render steps forward from the first inserted
  step, preserves stencil atomicity, handles destination-read constraints, and
  allows restricted forward merge.
- `DrawListBase` owns transform deduplication, uniform data cache, texture data
  cache, pipeline cache, destination-read bounds, pass bounds, load op, clear
  color, MSAA requirement, and depth/stencil flags.

Kanvas adopts the ideas, not the implementation. It must not copy Graphite bit
layouts, source package structure, C++ arena model, or backend abstractions.

## Ownership Boundary

`GPUDrawLayerPlanner` owns:

- expanding accepted analysis records into draw invocations;
- assigning invocations to `GPUDrawLayer` scopes;
- creating insertion tokens for stencil, clip, and multi-step relationships;
- defining legal sorting windows;
- defining batching and merge candidates;
- preserving destination-read, barrier, stencil, clip, layer, and paint-order
  constraints;
- emitting diagnostics for every reorder, cull, merge, or refusal.

`GPUDrawLayerPlanner` does not own:

- route selection;
- material-key derivation;
- WGSL module assembly;
- GPU resource allocation;
- command encoding;
- final WebGPU submission.

Those responsibilities remain with `GPUDrawAnalysis`, material/WGSL specs,
`GPUResourceProvider`, `GPUTaskList`, and execution/submission specs.

## `GPUDrawInvocation`

`GPUDrawInvocation` is the low-level unit produced from an accepted analysis
record and one `GPURenderStep`.

One normalized command may produce zero, one, or many invocations. A command
with a stencil producer and shading consumer produces multiple related
invocations. A command culled or refused during analysis produces no executable
invocation but remains visible in diagnostics.

Each invocation records:

- command ID and analysis record ID;
- render-step index and render-step identity;
- invocation role: shading, depth-only, stencil-producer, stencil-consumer,
  clip-producer, clear, discard, or composite;
- geometry and coverage summary;
- layer scope request;
- primitive clip and scissor summary;
- destination-read class;
- barrier class;
- pipeline-key group when known;
- material key or invalid material for depth-only work;
- binding-layout group;
- uniform data slot identity;
- texture/sampler/artifact binding slot identity;
- texture ownership or target/surface generation when it affects legal
  movement;
- conservative bounds;
- original paint order;
- diagnostic provenance.

Per-draw uniform values may be referenced by slot identity, but they are not
part of durable pipeline identity.
Payload slot semantics are defined in
`17-payload-gathering-and-slots.md`.

## `GPUDrawInsertion`

`GPUDrawInsertion` is a deterministic token that names where an invocation was
placed.

It includes:

- draw-layer ID;
- binding-list ID;
- invocation-list position or stable ordinal;
- layer order band;
- dependency class;
- command ID and render-step index;
- reason code for the insertion decision.

Insertion tokens are used for:

- multi-step renderers whose later steps must follow the first inserted step;
- stencil and clip producer/consumer relationships;
- conservative stop points during backward search;
- diagnostics that explain why an invocation could or could not move.

An insertion token is valid only inside one `GPUDrawLayerPlanner` output. It is
not a cross-recording stable handle.

## Layer Model

`GPUDrawLayer` is the ordered scope for invocation insertion. It is lower-level
than `GPULayerPlan`; it represents a pass/layer batching and ordering scope,
not Canvas saveLayer semantics.

A draw layer records:

- stable layer ID;
- parent semantic layer or target scope;
- layer order band;
- conservative bounds;
- destination-read bounds;
- depth/stencil requirements;
- MSAA or coverage requirements;
- load/clear/discard/store intent inherited from target state;
- ordered binding lists;
- culling and merge diagnostics.

Each binding list records a compatible group:

- `GPURenderStep` identity;
- `GPURenderPipelineKey` or pre-resource pipeline group;
- `WGSLBindingLayout` group;
- uniform slot identity;
- texture/sampler/artifact slot identity;
- scissor or primitive clip group when it affects dynamic state;
- destination-read and barrier class.

Invocations inside one binding list may be emitted with invariant pipeline and
resource bindings when the binding list proves compatibility.

## Insertion Algorithm

The target algorithm is conservative and deterministic:

1. `GPUDrawAnalysis` produces ordered analysis records.
2. Each accepted record expands into `GPUDrawInvocation` values, one per
   render step.
3. The first executable invocation for a command performs a bounded backward
   search over existing draw layers.
4. Depth-only, stencil-producer, and clip-producer invocations record backward
   and capture the latest insertion token that their consumers must respect.
5. Shading consumer invocations with producer dependencies may start from the
   producer insertion token and search forward only when the dependency proof
   says this is safe.
6. Later render steps for the same command record forward from the first step's
   insertion so multi-step renderers preserve local step order.
7. Destination-read invocations cannot move before any incompatible intersecting
   shading work that they observe.
8. Barriers, uploads, atlas mutation, target changes, and layer boundaries stop
   movement unless the analysis record proves they are disjoint and safe.
9. If no compatible layer exists, the planner creates a new draw layer at the
   tail of the scope.
10. Every search stop, new-layer creation, and successful insertion records a
    stable reason code.

Path and coverage atlas mutations, including `SplitPassAndRetry`, are defined
in `19-path-coverage-atlas-strategy.md`. A split retry is legal only when the
planner can preserve order, layer semantics, destination reads, target state,
and resource lifetimes.

The search limit is a policy input. It must be deterministic and visible in
diagnostics. A bounded search may give up batching opportunities, but it must
not change rendering semantics.

## Overlap Classes

Layer insertion uses conservative overlap classes:

| Class | Meaning |
|---|---|
| `Disjoint` | Bounds prove the candidate and layer/list cannot affect each other. |
| `CompatibleOverlap` | Bounds overlap, but ordering and state allow insertion into the candidate group. |
| `IncompatibleOverlap` | Bounds or state overlap and would change output if crossed. |
| `UnknownOverlap` | Proof is incomplete; treat as incompatible for movement. |

`UnknownOverlap` must not be optimized as disjoint. It may still execute in
original order.

## Stencil And Clip Atomicity

Stencil and complex clip sequences are atomic within a draw layer unless a
later accepted spec proves a finer-grained split.

Rules:

- producer and consumer invocations for one stencil or clip sequence must stay
  in the same draw layer;
- later steps for the sequence record forward from the first insertion;
- another sequence may bypass an incompatible layer only when analysis proves
  its producer and consumer remain atomic;
- sort and merge must not interleave unrelated work into the middle of an
  atomic stencil or clip sequence;
- diagnostics must name the atomic group ID and any movement boundary.

The first rect/rrect slice can avoid stencil atomicity by refusing complex clip
and stencil-producing routes. The target policy still exists for later path and
clip work.

## Destination Reads

Destination-read work is conservative:

- a destination-read invocation records its read bounds;
- it cannot move before a prior intersecting shading invocation whose output it
  observes;
- it cannot merge across an incompatible destination-read boundary;
- it cannot sample the active attachment unless `12-blend-color-target-state.md`
  and `10-gpu-execution-context-submission.md` accept the required intermediate
  or read strategy;
- failed destination-read movement must refuse or keep original order, never
  silently drop the read.

This keeps the draw-layer planner compatible with future texture-copy or
intermediate destination-read strategies without accepting them prematurely.

## Forward Merge

Forward merge is an optional optimization. It is not required for the first
implementation slice.

When implemented, it is allowed only when all of these are true:

- the moved invocation is a single-step shading invocation;
- it is not depth-only, stencil-producing, clip-producing, or multi-step;
- moving it does not cross an incompatible destination-read dependency;
- moving it does not cross an intersecting barrier, upload, atlas mutation, or
  target boundary;
- bounds prove it is disjoint from skipped later work;
- the candidate layer is the tail layer for the current scope, unless a later
  spec defines a safe middle insertion ordering scheme;
- diagnostics record the source insertion, target insertion, proof, and reason.

Middle-layer forward merge is not accepted in this target. It is too easy to
break order-band reasoning for clip/stencil stop tokens.

## Sort Policy

`SortKey` is a diagnostic preimage and compact key for legal sorting inside a
sort window. It is not Graphite's bit layout.

Required axes:

- semantic layer or target scope;
- draw-layer order band;
- original paint-order band or barrier generation;
- dependency class;
- stencil or clip atomic group;
- render-step order for multi-step commands;
- `GPURenderStep` identity;
- `GPURenderPipelineKey` or pre-resource pipeline group;
- `MaterialKey` or invalid material for depth-only invocations;
- `GPUMaterialAssemblyPlan` identity when material WGSL affects grouping;
- `WGSLBindingLayout` group;
- uniform slot identity;
- texture/sampler/artifact binding slot identity;
- scissor or primitive clip group when it affects dynamic state;
- destination-read class;
- atlas/upload generation when it affects legality.

Forbidden axes:

- Kotlin object addresses;
- raw GPU handle addresses;
- imported texture handles or surface lease IDs;
- cache hit/miss state;
- material dictionary residency state;
- per-draw uniform values;
- transient buffer offsets;
- texture pixel contents;
- any axis that hides crossing a barrier.

Sort keys are valid only inside the planner or pass materialization product
that created them.
Texture/image resources participate through `GPUResourceBindingSlot`,
`GPUSampledTextureBinding`, upload/atlas/target generation, and explicit
barriers. Raw handles never participate directly.

## Merge And Batching

The planner may merge invocations into one binding list when:

- their render step is compatible;
- their pipeline group is compatible;
- their binding layout is compatible;
- uniforms, textures, samplers, and artifacts can be rebound or proven
  invariant according to the ABI spec;
- scissor and primitive clip changes are legal dynamic state;
- destination-read and barrier classes allow adjacency;
- conservative bounds do not require a stricter ordering boundary.

`GPUDrawPass` may then emit fewer command groups, but it must preserve the
diagnostic mapping from command ID to invocation, binding list, sort key, and
emitted pass command.

## First Slice Policy

For the first rect/rrect slice:

- use a single root draw-layer scope;
- create `GPUDrawInvocation` for each accepted rect/rrect render step;
- emit sort-key preimages even if no aggressive sorting is performed;
- allow batching only for adjacent compatible invocations;
- require one conservative culling proof fixture;
- keep forward merge disabled or diagnostic-only;
- refuse complex clip, stencil, destination-read shader blend, and multi-step
  path renderers.

This lets the first implementation validate the target data model without
requiring the full Graphite-like optimizer on day one.

## Diagnostics

Planner diagnostics must include:

- command ID and render-step index;
- invocation role;
- layer ID and binding-list ID;
- insertion direction: backward, forward, new layer, or original order;
- overlap class;
- stop reason;
- sort-key preimage and compact hash;
- merge decision;
- culling decision when present;
- destination-read bounds when present;
- barrier and upload generation when relevant.

Stable reason-code examples:

- `planner.insert.backward.compatible`
- `planner.insert.forward.producer_dependency`
- `planner.insert.new_layer.no_candidate`
- `planner.stop.incompatible_overlap`
- `planner.stop.destination_read`
- `planner.stop.barrier`
- `planner.stop.atlas_mutation`
- `planner.stop.layer_boundary`
- `planner.split.atlas_try_again`
- `planner.split.rejected.atlas_retry_illegal`
- `planner.merge.forward_tail_only`
- `planner.merge.rejected.multi_step`
- `planner.merge.rejected.barrier_intersection`
- `planner.sort.window_closed`
- `planner.sort.key_preimage`

## Validation Requirements

Promoted planner behavior requires:

- canonical dumps for invocations, insertion tokens, layers, binding lists, and
  sort keys;
- adjacent batching fixture;
- non-batching fixture for incompatible pipeline or binding layout;
- destination-read stop fixture;
- barrier stop fixture;
- atlas mutation stop fixture and split-pass retry positive/negative fixtures
  before path or coverage atlas routes are promoted;
- stencil or clip atomicity fixture before those routes are promoted;
- disabled or diagnostic-only forward merge fixture for the first slice;
- forward-merge positive and negative fixtures before enabling forward merge;
- culling fixture proving a safe opaque cover case and negative culling cases;
- pass materialization fixture that maps original command IDs to emitted pass
  commands.

## Non-Goals

- Do not copy Graphite's C++ data structures or bit layouts.
- Do not require forward merge for the first implementation slice.
- Do not hide planner choices inside `GPUDrawPass` construction.
- Do not sort across barriers, destination reads, stencil atomic groups, layer
  boundaries, or unknown overlaps.
- Do not treat batching as correctness evidence.
