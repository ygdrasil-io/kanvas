# FP-07 Prepared Composite Route Design

Date: 2026-07-29
Status: Proposed, architecture approved in conversation
Roadmap item: `FP-07 — Prepared composite route`

## Purpose

FP-07 migrates pictures, layers, image filters, mask filters, backdrop reads,
and restore composites from the temporary legacy immediate family to the
common prepared Surface frame used by core primitives, images, text, and
vertices.

The product result is one transactional, handle-free composite plan lowered to
ordered WebGPU work with exact bounds, clip, color, blend, destination-read,
resource, preflight, ownership, and refusal semantics.

FP-07 supports the complete current public `ImageFilter` and `MaskFilter`
families. It does not defer valid public filter kinds to FP-10 merely because
their implementation needs multiple GPU passes.

## Source Of Truth

This design is subordinate to:

- `.upstream/target/high-performance-wgsl-pipeline-target.md`;
- `.upstream/target/skia-like-realtime-renderer-target.md`;
- `.upstream/specs/gpu-renderer/08-layer-and-filter-plans.md`;
- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`;
- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`;
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`;
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`;
- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`;
- `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`;
- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`;
- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md`;
- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md`;
- `.upstream/specs/gpu-renderer/30-coordinate-transform-bounds-policy.md`;
- `docs/superpowers/specs/2026-07-13-graphite-dawn-inspired-webgpu-frame-plan-design.md`;
- the accepted FP-04 prepared-image route;
- the accepted FP-05 prepared-text route;
- the accepted FP-06 prepared-vertices route before product activation.

Graphite, Skia image filtering, and Dawn are bounded implementation references
at Skia commit `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`. They are not
architecture to port.

## Confirmed Decisions

- Keep WebGPU as the only GPU backend.
- Use one composite engine and one typed filter DAG.
- Support every current public `ImageFilter` kind in FP-07.
- Support every current public `MaskFilter` kind in FP-07.
- Apply image filters to the isolated output of any prepared child draw family.
- Apply mask filters to coverage before paint coloration.
- Treat `saveLayer` as a semantic boundary before considering elision.
- Expand unpainted `DrawPicture` transactionally into its child operations.
- Lower painted or filtered `DrawPicture` through a synthetic isolated layer.
- Resolve backdrop and destination reads through the existing
  `GPUDestinationReadPlan` authority.
- Reuse the existing layer, filter, clip, material, blend, color, resource,
  task-graph, prepared-frame, and ownership authorities.
- Add a small typed filter normalization and fusion pass.
- Materialize intermediate textures only at true execution boundaries.
- Keep every refusal terminal after prepared-route admission.
- Keep the product gate closed until native pixels, ownership, mixed-frame,
  refusal, and no-fallback evidence pass.

## Non-Goals

FP-07 does not add:

- a generic image-filter backend interface;
- `SkSpecialImage`, a Canvas device stack, or a Graphite-style `Recorder`;
- Ganesh or another GPU backend;
- a dynamic SkSL compiler;
- arbitrary runtime shader source;
- CPU rendering of an unsupported full layer followed by texture upload;
- persistent cross-frame intermediate residency, which belongs to FP-09;
- general automatic graph optimization beyond proven local rewrites;
- final GM score and performance claims, which belong to FP-11;
- compatibility with malformed serialized pictures or cyclic filter graphs.

FP-10 remains available for bounded gaps discovered by validation or blocked
by an external dependency. It is not the planned home for otherwise valid
public filter kinds.

## Current State

The repository already contains most semantic authorities and evidence
foundations:

- `GPULayerPlan`, `GPULayerExecutionPlan`, target, initialization, backdrop,
  composite, resource, budget, and diagnostic contracts;
- `GPUFilterPlan`, typed filter node routes, bounds, crops, sampling,
  intermediates, budgets, and diagnostics;
- `GPUFilterDAGExecutor`;
- native blur, displacement, tiling, drop-shadow, lighting, morphology,
  convolution-related, and color-matrix helpers;
- `MaskBlurPlan` and native mask-blur execution;
- saveLayer isolated-target and native materialization evidence;
- destination-read copy/intermediate plans;
- prepared Surface task graph, preflight, materializer, ownership ledger, and
  one-submit route;
- transactional `DrawPicture` expansion helpers;
- public picture serialization for `DrawPicture`, `BeginLayer`, and
  `EndLayer`.

These foundations do not yet form one product path. `DrawPicture`,
`BeginLayer`, and `EndLayer` remain classified under
`LegacyDisplayOpFamily.Composites`. Some filter families are executable only
through specialized legacy dispatches, and `MaskFilter.Shader` and
`MaskFilter.Table` are not normalized by the common mapper.

FP-07 consolidates these authorities. It does not retain specialized legacy
composite execution as a second product implementation.

## Graphite, Skia Filtering, And Dawn Alignment

Skia's image-filter semantics primarily live in the shared `skif` pipeline.
Graphite supplies GPU-backed devices, images, cached inputs, blur execution,
destination-read tracking, and draw work. Dawn ultimately encodes render
passes, compute passes, bind groups, texture copies, and draws.

FP-07 keeps the useful invariants:

- reverse bounds determine required filter inputs;
- forward bounds determine filter outputs;
- source, crop, clip, and restore bounds remain separate facts;
- a saved layer isolates child rendering when semantics require it;
- backdrop contents are captured before the layer modifies them;
- filter nodes form a DAG with explicit inputs and intermediates;
- lazy metadata operations can be combined without an offscreen surface;
- a new intermediate is created when operation ordering cannot be preserved;
- destination-reading blends never sample the active writable attachment;
- restore alpha, color filter, image filter, and blend are applied exactly
  once;
- command work becomes immutable before native resource materialization;
- Dawn uses ordinary WebGPU resource bindings and pass commands.

Kanvas intentionally omits:

- `skif::Backend`;
- `SkSpecialImage`;
- the `SkCanvas` device stack;
- Graphite `ResourceProvider`, `RendererProvider`, `RenderStep`, and
  backend-polymorphic factories;
- SkSL program compilation;
- C++ arena and proxy ownership conventions;
- Graphite sort-key layouts.

The expected architectural alignment after FP-07 is approximately 80–85% for
the complete Skia-to-Graphite-to-Dawn composite path. This is an architectural
estimate, not a benchmark. FP-09 owns inter-frame reuse and FP-11 owns measured
performance evidence.

## Architecture

The prepared route is:

```text
DisplayOp stream
    -> GPUPreparedCompositeCapture
       -> transactional picture expansion
       -> immutable layer-scope tree
    -> GPUPreparedCompositeLowerer
       -> child prepared draws from FP-04 / FP-05 / FP-06
       -> typed mask-filter plans
       -> typed image-filter DAGs
       -> layer and restore plans
    -> GPUPreparedFilterNormalizer
       -> proven metadata folding and node fusion
       -> explicit materialization boundaries
    -> PreparedCompositeFrameInventory
    -> GPUPreparedSurfaceFrameTaskListBuilder
    -> GPUPreparedSurfaceNativePreflight
    -> GPUWgpu4kPreparedSurfaceFramePayloadMaterializer
       -> texture allocation / copy
       -> render and compute passes
       -> restore composite
    -> one ordered submission
```

No native handle exists before materialization. Capture, graph normalization,
lowering, inventory, payloads, resources, task lists, and preflight operate on
immutable snapshots.

## Composite Capture

`GPUPreparedCompositeCapture` consumes a complete `DisplayOp` stream and emits
an immutable root scope containing ordered draw and child-scope entries.

Each captured scope records:

- stable scope ID and parent scope ID;
- source operation range and provenance;
- `SaveLayerRec` facts;
- creation transform and clip;
- ordered child operations or child scope IDs;
- matching restore position;
- restore-time parent target facts;
- nesting depth;
- whether it originated from `saveLayer` or a painted picture.

Capture is transactional:

- an unmatched `EndLayer` refuses the frame;
- an unclosed `BeginLayer` refuses the frame;
- a malformed nested picture refuses the enclosing picture expansion;
- recursion cycles refuse before child work is admitted;
- an over-budget picture or nesting depth refuses before native allocation;
- partial expansion is never submitted.

The capture stage does not map paints, compile filters, allocate resources, or
create native handles.

## Picture Lowering

### Unpainted Picture

An unpainted `DrawPicture` may inline its operations into the current scope
when:

- expansion is acyclic and within budget;
- its transform composes exactly with every child transform;
- its captured clip is intersected with child clips;
- its children remain in source order;
- nested layer boundaries remain intact.

Inlining changes no pixel semantics and needs no synthetic target.

### Painted Or Filtered Picture

A `DrawPicture` with a paint is an atomic composite group. It lowers to:

```text
synthetic BeginLayer(picture bounds, picture paint)
    -> expanded child operations
synthetic EndLayer
```

The picture paint, alpha, color filter, image filter, blend mode, mask filter,
and clip apply to the group result, not independently to every child.

The outer picture clip constrains the final group composite. It must not be
incorrectly applied as a replacement for child-local clips.

### Picture As Image-Filter Input

`ImageFilter.Picture` records a picture source node with its own source rect.
It uses the same transactional expansion and child prepared routes, but renders
into a filter-owned source target sized by the reverse-bounds plan.

## Layer Semantics

Every captured layer produces one `GPULayerPlan`.

The plan distinguishes:

- bounds hint;
- child contribution bounds;
- active creation clip;
- source filter input bounds;
- backdrop read bounds;
- offscreen target bounds;
- final composite bounds;
- restore clip.

The bounds hint is not a clip.

An isolated layer follows this ordered model:

```text
allocate target
    -> initialize transparent or from prior contents/backdrop
    -> render ordered children
    -> execute source image-filter DAG
    -> apply restore alpha/color/blend
    -> composite into parent under restore clip
    -> release frame-local resources
```

Nested scopes preserve parent/child ordering through exact task dependencies.
The parent cannot consume a child target before the child composite is
complete.

### Direct-To-Parent Elision

A layer may be elided only when an explicit proof establishes all of:

- no source image filter;
- no mask or coverage effect requiring isolation;
- alpha is one or distributable without changing overlap semantics;
- restore blend is equivalent per child;
- no backdrop or destination read changes;
- no init-with-previous behavior;
- clip behavior is equivalent;
- color conversion is equivalent;
- children do not observe a layer-local target;
- no later task consumes the isolated source.

Missing proof means isolate or refuse, never heuristic elision.

## Complete Image-Filter Coverage

FP-07 maps every current public `ImageFilter` kind:

| Public filter | Prepared execution |
|---|---|
| `Crop` | metadata crop/tile node, materialized only when ordering requires |
| `Blur` | separable native GPU passes with bounded sigma strategy |
| `DropShadow` | alpha extraction, blur, colorize, offset, merge/composite DAG |
| `ColorFilter` | fold into metadata/material when safe, otherwise native render node |
| `Compose` | deterministic inner-to-outer DAG edges |
| `Blend` | two-input node using fixed-function or destination-read shader plan |
| `Dilate` | native morphology passes |
| `Erode` | native morphology passes |
| `DistantLitDiffuse` | native lighting node |
| `PointLitDiffuse` | native lighting node |
| `SpotLitDiffuse` | native lighting node |
| `DistantLitSpecular` | native lighting node |
| `PointLitSpecular` | native lighting node |
| `SpotLitSpecular` | native lighting node |
| `Offset` | metadata translation until a materialization boundary |
| `Tile` | metadata sampling/crop where legal, otherwise native render node |
| `Merge` | deterministic multi-input composite node |
| `DisplacementMap` | native two-input displacement node |
| `Picture` | prepared picture source target |
| `Magnifier` | native sampled-image transform node |
| `MatrixConvolution` | native bounded convolution, separable only when proven |
| `RuntimeEffect` | registered Kotlin/WGSL descriptor route |

Valid finite input within device capabilities and configured budgets is an
implementation target, not a planned `unsupported.filter.kind` refusal.

## Complete Mask-Filter Coverage

Mask filters operate on coverage before shader coloration, paint alpha, color
filter, and final target blend.

The common model is:

```text
geometry or glyph coverage
    -> optional mask-filter plan
    -> filtered A8 or canonical coverage
    -> paint material coloration
    -> final blend
```

Supported public kinds:

| Public mask filter | Prepared execution |
|---|---|
| `MaskFilter.Blur` | canonical blur plan preserving NORMAL, SOLID, OUTER, and INNER style |
| `MaskFilter.Shader` | prepared shader material evaluated as coverage and combined by the canonical mask rule |
| `MaskFilter.Table` | immutable 256-entry coverage lookup resource |

The same rules apply to shapes, images when their paint semantics require a
coverage mask, text, emoji, vertices, and meshes. A draw family may reuse a
family-specific coverage source, but must not implement a second mask-filter
authority.

`MaskFilter.Table` snapshots its bytes before identity calculation. A table
whose size is not exactly 256 refuses with a stable validation diagnostic.

## Filter DAG Compilation

`GPUPreparedFilterGraphCompiler` maps one public filter tree to one immutable,
acyclic, topologically ordered `GPUFilterPlan`.

It records:

- exact filter kind and version;
- stable graph-local node IDs;
- ordered input roles;
- exact parameter preimages;
- source, prior-node, picture, shader, backdrop, or empty input;
- forward and reverse bounds plans;
- coordinate-space and pixel-grid facts;
- crop and tile behavior;
- color and alpha treatment;
- planned route;
- required intermediate descriptor;
- task ordering tokens;
- refusal diagnostics.

Object identity and default `hashCode()` are not graph identity. Arrays, maps,
tables, kernels, uniforms, and child collections are snapshotted and encoded
in deterministic order.

One refused node refuses the complete enclosing graph, layer, and frame route.
The executor never silently removes an unsupported node.

## Filter Normalization And Fusion

`GPUPreparedFilterNormalizer` is a small typed optimizer. It is the
mono-backend equivalent of the useful lazy behavior in Skia `FilterResult`,
without introducing a generic lazy-image abstraction.

It may perform only proven rewrites:

- remove identity nodes;
- compose adjacent translations;
- intersect compatible crops;
- accumulate compatible tile and sampling metadata;
- combine adjacent color matrices or color filters when exact ordering is
  preserved;
- fold restore alpha or a color filter into the final composite material when
  it remains single-application;
- reuse an unchanged source view for pixel-aligned subsets;
- coalesce adjacent render nodes with identical target and legal load/store
  behavior;
- use a separable kernel only when kernel analysis proves equivalence.

It must materialize a new intermediate at:

- blur, morphology, lighting, convolution, magnifier, or displacement work
  that requires a distinct output;
- multi-input blend or merge boundaries;
- backdrop or destination-read boundaries;
- an operation whose semantic order cannot be represented by accumulated
  metadata;
- a color-space conversion boundary;
- a sampling or tile rule that cannot be preserved lazily;
- any read-after-write hazard;
- an explicit evidence boundary required by the validation plan.

Every rewrite produces a dumpable proof containing the original node IDs,
rewritten node IDs, equivalence rule, input/output bounds, and removed
intermediate count.

No approximate rewrite is accepted merely because the pixel difference is
expected to be small. Approximate optimizations require a separately approved
tolerance and evidence row.

## Bounds And Coordinates

Bounds planning has two passes:

1. reverse propagation from desired output to required inputs;
2. forward propagation from available inputs to possible output.

Each node records:

- parameter-space facts;
- source-space facts;
- layer-space facts;
- device-space facts;
- integer allocation bounds;
- sampling radius;
- crop and tile expansion;
- finite and overflow proofs;
- origin translation for its intermediate.

Rounding follows the common coordinate policy. The planner uses conservative
outward rounding for required pixels and never truncates a kernel halo.

Clip application order is:

1. preserve all source/filter inputs required to compute the result;
2. evaluate the filter in its planned bounds;
3. apply final draw or restore clip to the output composite.

An early clip may reduce work only with a proof that it removes no sampled
input.

## Color, Alpha, And Premultiplication

All filter inputs and outputs carry exact color-value facts:

- format;
- color space;
- transfer function;
- alpha type;
- premultiplication state;
- working-space conversion;
- store conversion.

Unless a public filter defines another contract, filtering occurs in the
planned linear working space with explicit decode and encode boundaries.

Alpha, tint, color filters, restore paint, and final blend are each applied
exactly once. Normalization may move an operation only when its proof preserves
ordering around premultiplication and color conversion.

Lighting, convolution, displacement, and runtime-effect nodes declare whether
they observe or modify alpha. Undefined alpha treatment is a refusal.

## Backdrop And Destination Reads

Backdrop filters and destination-reading composites use the existing
`GPUDestinationReadPlan`.

The plan owns:

- exact read bounds;
- source target and generation;
- copy, intermediate, or isolated-layer strategy;
- texture descriptor and usages;
- producer/copy/consumer ordering;
- binding identity;
- byte budget;
- diagnostics.

WebGPU read/write aliasing is forbidden. No node may sample the texture view
currently bound as its writable attachment.

The sequence is:

```text
finish prior parent writes
    -> copy or expose accepted destination intermediate
    -> run backdrop/filter consumer
    -> render layer children
    -> restore composite
```

Destination content is never read back to CPU for product continuation.

## Registered Runtime Image Filters

`ImageFilter.RuntimeEffect` is accepted only through the common registered
runtime-effect authority.

The descriptor must provide:

- stable effect ID and descriptor version;
- Kotlin/CPU reference behavior;
- parser-validated WGSL;
- exact entry point;
- exact uniform schema and ABI hash;
- declared child image-filter slots;
- optional shader child slot;
- bounded sample radius;
- color and alpha contract;
- resource binding plan.

Cases:

- unregistered effect: stable descriptor refusal;
- registered without WGSL: stable WGSL-unavailable refusal;
- parser or reflection mismatch: stable parser/ABI refusal;
- missing or extra child: stable child-schema refusal;
- valid descriptor: native filter node.

No placeholder shader and no arbitrary SkSL source are allowed.

## Resource And Task Planning

`PreparedCompositeFrameInventory` deduplicates exact immutable descriptors and
calculates frame totals before materialization.

It inventories:

- layer targets;
- picture source targets;
- filter intermediates;
- coverage masks;
- destination copies;
- sampled views and samplers;
- storage views;
- uniform payloads;
- render and compute pipelines;
- child prepared draw resources;
- estimated bytes and pass counts.

The task graph uses explicit operations:

```text
AllocateTarget
ClearTarget
CopyDestination
RenderChild
RunFilterRenderNode
RunFilterComputeNode
CompositeLayer
ReleaseIntermediate
```

Dependencies name exact producer and consumer resources. A target cannot be
released before its final consumer. A target cannot be sampled before its
producer pass is complete.

Compatible filter passes may share one command encoder and submission. They
remain separate render or compute passes when WebGPU attachment or usage
transitions require it.

## Native Materialization And Ownership

The wgpu4k materializer receives only accepted plans.

It:

- creates textures with the exact union of planned usages;
- creates one view per planned binding role when required;
- writes immutable uniforms and table/kernel payloads;
- resolves or creates exact pipelines;
- creates bind groups from preflighted layouts;
- records copies, render passes, compute passes, and draws in task order;
- closes passes and command encoders exactly once;
- submits once for the prepared frame unless a documented device constraint
  requires a split;
- releases frame-local resources exactly once.

It does not:

- reinterpret public filter objects;
- recompute bounds;
- choose another route;
- add a missing usage flag;
- substitute a CPU texture;
- recover from a refused plan.

Device generation is part of native cache identity. No native handle survives
device recreation.

If wgpu4k exposes behavior that contradicts its public API, the implementation
must preserve minimized evidence and report the issue upstream instead of
adding a hidden Kanvas-specific ownership or lifecycle workaround. The same
rule applies to surprising wgsl4k parser, reflection, or generator behavior.

## Preflight And Atomicity

`GPUPreparedSurfaceNativePreflight` validates the complete composite frame
before the first native allocation or write.

It validates:

- balanced scope tree and picture expansion;
- child prepared-draw acceptance;
- filter graph topology and exact node descriptors;
- normalization proofs;
- finite bounds and allocation dimensions;
- target formats and usage unions;
- sample counts;
- render and compute pipeline keys;
- WGSL parser/reflection results;
- uniform sizes, alignments, and bindings;
- runtime-effect descriptors and children;
- destination-read bindings and generations;
- read/write alias absence;
- resource ownership and release schedules;
- per-node, per-layer, and per-frame budgets;
- task dependency acyclicity;
- command operands and materialization preimages.

Failure is atomic: zero native allocation, zero queue write, zero command
encoding, zero submit, and zero fallback for that prepared frame.

## Budgets

Budgets extend the existing configurable render limits. They are capability
and safety limits, not a list of arbitrarily unsupported filters.

Required limits include:

- picture recursion depth and expanded operation count;
- layer nesting depth and count;
- filter graph node and edge count;
- maximum kernel dimensions and sample radius;
- target and intermediate dimensions;
- per-resource bytes;
- total simultaneous intermediate bytes;
- destination-copy bytes;
- coverage-mask bytes;
- render-pass and compute-pass count;
- dispatch dimensions;
- uniform and immutable lookup-table bytes.

If the public configuration raises a limit and the device supports it, the
planner may accept the larger workload. Hard device limits remain authoritative.

Budget refusal identifies the exact node or layer, requested amount, configured
limit, device limit when applicable, and estimated live-byte peak.

## Stable Refusals

Planned refusal families cover invalid or unavailable execution, not ordinary
valid public filter kinds:

- `unsupported.composite.picture.cycle`;
- `unsupported.composite.picture.budget`;
- `unsupported.composite.layer.unbalanced`;
- `unsupported.composite.layer.bounds`;
- `unsupported.composite.layer.budget`;
- `unsupported.composite.layer.destination_read`;
- `unsupported.filter.graph.cycle`;
- `unsupported.filter.graph.budget`;
- `unsupported.filter.parameter.non_finite`;
- `unsupported.filter.bounds.overflow`;
- `unsupported.filter.intermediate.budget`;
- `unsupported.filter.kernel.invalid`;
- `unsupported.filter.runtime_effect.descriptor`;
- `unsupported.filter.runtime_effect.wgsl_not_available`;
- `unsupported.filter.runtime_effect.abi`;
- `unsupported.filter.runtime_effect.child`;
- `unsupported.mask-filter.table.size`;
- `unsupported.mask-filter.shader.material`;
- `unsupported.composite.native.alias`;
- `unsupported.composite.native.capability`;
- `unsupported.composite.preflight`;

Diagnostics include scope, operation, node, source provenance, bounds,
resources, budget facts, and the terminal stage.

No catch-all `unsupported.filter.kind` is acceptable for a current public
filter after FP-07 is complete.

## Product Cutover

The product gate remains closed during implementation.

Activation order:

1. capture and lower pictures/layers without changing routing;
2. complete public image-filter and mask-filter planning;
3. complete normalization/fusion;
4. complete task/resource/preflight integration;
5. complete native materialization;
6. pass pure, native, mixed-frame, ownership, refusal, and no-fallback tests;
7. enable the prepared composite route behind the product gate;
8. remove `Composites` from the legacy allowlist;
9. remove specialized legacy composite/filter product dispatches that have no
   remaining consumers;
10. run FP-11 GM and performance evidence before broad performance claims.

The cutover is one-way for an admitted prepared frame. There is no per-node or
per-layer fallback into the legacy renderer.

## Validation

### Pure Contract Tests

Cover:

- balanced and malformed scope capture;
- picture transform and clip composition;
- painted picture synthetic-layer semantics;
- every public image-filter mapping;
- every public mask-filter mapping;
- exact graph identity and array/map snapshots;
- forward and reverse bounds;
- crop, tile, sampling, and coordinate behavior;
- normalization and fusion proofs;
- materialization boundaries;
- layer elision proof and refusal;
- destination-read planning;
- budgets and stable diagnostics;
- runtime-effect descriptor, WGSL, ABI, and child validation;
- no duplicate authority use.

### CPU Oracles

Deterministic fixtures cover:

- transparent edges and crop halos;
- nearest and linear sampling;
- blur styles;
- morphology;
- color matrices and alpha;
- drop shadows;
- every lighting family;
- tile modes;
- merge and blend modes;
- displacement channels;
- magnifier;
- convolution with and without alpha;
- mask shader and table;
- runtime registered filters;
- sRGB/linear and premultiplication order.

Exact pixels are required when the contract is exact. Tolerances must be
node-specific, justified, and no broader than the public semantic contract.

### Native WebGPU Evidence

Native tests prove:

- isolated saveLayer child content is sampled by restore composite;
- nested layers preserve order;
- backdrop reads prior parent contents;
- image filters work on shapes, images, text, emoji, vertices, and meshes;
- all public filter kinds execute natively for accepted bounded inputs;
- mask filters apply before coloration;
- destination-reading blends use legal non-aliasing bindings;
- intermediates carry exact usages and ownership;
- pass and encoder close-once behavior;
- one ordered submission;
- device generation invalidates native caches;
- refused frames allocate and submit nothing.

### Mixed-Frame Evidence

One prepared frame must combine:

- root core primitives;
- prepared images;
- prepared text and emoji;
- prepared vertices or mesh;
- an unpainted picture;
- a painted picture;
- nested saveLayer;
- image filter DAG;
- mask filter;
- backdrop or destination-reading restore blend;
- draws before and after the composite scopes.

The evidence must prove ordering, pixels, ownership, route diagnostics, and no
legacy invocation.

## Completion Criteria

FP-07 is complete only when:

- `DrawPicture`, `BeginLayer`, and `EndLayer` use the prepared route;
- every current public `ImageFilter` kind has native accepted evidence for
  valid bounded input;
- every current public `MaskFilter` kind has native accepted evidence;
- child content from every prepared draw family can feed a layer/filter;
- normalization removes avoidable intermediates with dumpable proofs;
- backdrop and destination reads remain GPU-owned;
- no active attachment is sampled while writable;
- preflight failure produces zero native side effects;
- `legacy.surface.prepared.family.composites` is absent from accepted frames;
- `LegacyDisplayOpFamily.Composites` is removed from the allowlist;
- superseded composite/filter product dispatches have no consumers;
- tests and `git diff --check` pass;
- independent review has no unresolved Critical or Important finding.

## Relationship To Later Frame Plans

- FP-08 deletes the immediate and CPU continuation paths that FP-07 makes
  unnecessary.
- FP-09 reuses prepared targets, pipelines, bind groups, and compatible
  intermediate pools across frames.
- FP-10 handles only bounded gaps found by evidence or genuine dependency
  blockers.
- FP-11 regenerates visual evidence and measures whether pass fusion, target
  reuse, and the prepared path approach the performance target.

FP-07 must not pre-implement FP-09 cross-frame residency, but its immutable
keys and ownership contracts must make later reuse possible without changing
public filter semantics.
