# Clip, Stencil, And Mask Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target clip, stencil, and coverage-mask pipeline for the GPU-first
renderer.

This spec expands the `clip` facts carried by `NormalizedDrawCommand` into a
target-complete execution plan. It defines captured clip descriptors, element
normalization, per-draw effective clip selection, clip bounds, scissor,
analytic clips, depth/stencil producer-consumer routes, coverage-mask routes,
clip shader policy, ordering tokens, budgets, diagnostics, and validation
gates.

This is a target-complete spec. It is not an implementation slice and it does
not reduce clipping to the first deliverable.

The target is Graphite-inspired but Kanvas-owned:

- the core receives captured clip state, not a mutable Canvas clip stack;
- clip execution is planned explicitly before pass construction;
- simple clips prefer scissor, geometric intersection, or analytic coverage;
- complex clips may use depth/stencil or typed coverage masks;
- `CPUPreparedGPU` is allowed only for typed `CoverageMaskArtifact` or
  `PathAtlasArtifact` coverage consumed by GPU work;
- clip masks use `19-path-coverage-atlas-strategy.md` and texture ownership
  rules instead of ad hoc scratch textures;
- unsupported stacks, unbounded masks, unregistered clip shaders, or illegal
  ordering produce stable refusals instead of silent CPU fallback.

## Source Specs

This spec depends on:

- `01-normalized-draw-commands.md` for captured clip facts at the command
  boundary;
- `02-gpu-recording-task-graph.md` for `GPUDrawAnalysis`, draw-layer planning,
  task lists, and render-pass construction;
- `04-pipeline-key-cache-resources.md` for depth/stencil pipeline keys,
  resources, and typed `CPUPreparedGPU` artifacts;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `08-layer-and-filter-plans.md` for clip facts active at layer creation and
  layer isolation decisions;
- `28-layer-savelayer-execution.md` for layer creation/restore clip
  interaction, layer bounds, restore composite clipping, and layer ordering
  tokens;
- `09-draw-family-support-matrix.md` for clip family target maturity;
- `10-gpu-execution-context-submission.md` for render/copy/upload ordering and
  device-generation rules;
- `11-wgsl-layout-binding-abi.md` for analytic clip and mask binding ABI;
- `13-performance-telemetry-cache-gates.md` for clip counters, budgets, and
  performance gates;
- `15-draw-layer-planner-and-sort-policy.md` for clip atomicity and sort
  windows;
- `17-payload-gathering-and-slots.md` for clip uniforms, mask bindings, and
  pass-local payload slots;
- `18-texture-image-ownership.md` for mask texture ownership and active target
  sampling rules;
- `19-path-coverage-atlas-strategy.md` for `GPUCoverageAtlasPlan`,
  `GPUCoverageMaskDescriptor`, `GPUCoverageAtlasBinding`,
  `CoverageMaskArtifact`, `PathAtlasArtifact`, atlas keys, mutation, and
  retry/split policy;
- `20-destination-read-strategy.md` for destination reads whose bounds are
  constrained or expanded by clips;
- `23-filter-effect-pipeline.md` for filter source/output bounds when filters
  interact with active clips;
- `25-path-stroke-geometry-pipeline.md` for stable path/shape descriptors,
  fill-rule and inverse-fill facts, geometry bounds, stencil-cover geometry,
  and path/stroke diagnostics when clip paths share geometry descriptor rules;
- `30-coordinate-transform-bounds-policy.md` for clip coordinate spaces,
  transform classification, clip bounds proofs, scissor rounding, mask/stencil
  bounds, and `GPUClipReductionProof`.

## Graphite And Skia Evidence

Relevant Skia and Graphite concepts:

- `SkClipOp` exposes intersect and difference operations for clip mutation.
- Core Skia `SkClipStack` keeps save records, elements, bounds, and operations
  as stack state before device-specific application.
- Graphite `ClipStack` classifies the current state as empty, wide-open,
  device rect, device rrect, or complex.
- Graphite clip elements carry shape, local-to-device transform, operation,
  outer bounds, inner bounds, invalidation state, and deferred usage facts.
- `ClipStack::visitClipStackForDraw()` computes the draw's scissor, clipped
  bounds, fill-style invariant bounds, analytic clip, and remaining effective
  clip elements for one draw.
- `ClipStack::updateClipStateForDraw()` tracks which effective elements need
  later depth-only clip draws and returns ordering/insertion facts.
- Graphite attempts simple simplification before expensive routes:
  containment/culling, geometric intersection with compatible draw geometry,
  scissor tightening, analytic rect/rrect clip, then depth/stencil or atlas
  behavior.
- Graphite snaps scissors conservatively to reduce state churn while still
  preserving coverage correctness.
- Complex clip elements can be rendered as depth-only clip producer draws and
  then consumed by later shading draws.
- `ClipAtlasManager` manages rasterized clip masks and keeps path-keyed masks
  separate from transient save-record-keyed masks because their lifetimes are
  different.
- `CoverageMaskRenderStep` samples mask textures with explicit mask bounds,
  texture origin, mask-to-device transform, invert semantics, and atlas
  resource bindings.
- Graphite `clipShader()` is not a complete solved route; comments note that
  shader clips need a distinct strategy such as depth/sample locations or an
  alpha mask image.

Kanvas adopts these invariants:

- clip state is captured once and then applied per draw by explicit plans;
- clip bounds are conservative and visible in diagnostics;
- simple clips should avoid masks and atlases when possible;
- complex clip producers and consumers are ordering-sensitive;
- mask residency, generation, and upload/write-before-sample facts are
  explicit;
- shader clips require an accepted registered route, not arbitrary shader
  execution.

Kanvas intentionally does not copy:

- Graphite class names, source layout, C++ stack ownership, or insertion
  structures;
- Graphite's depth/stencil packing choices or scissor snapping constants;
- Skia mutable stack classes as core API;
- SkSL clip shader machinery;
- Graphite's mask cache implementation or proxy fallback behavior;
- CPU-rendered full-draw, layer, filter, or scene fallback textures.

## Ownership Boundary

The legacy adapter owns:

- interpreting stateful `clipRect`, `clipRRect`, `clipPath`, `clipShader`,
  `save`, and `restore` calls;
- resolving save/restore nesting into stable clip descriptors;
- converting legacy shapes and shaders into dumpable Kanvas descriptors or
  stable refusals;
- rejecting invalid transforms, non-finite bounds, mutable identity, or
  unsupported clip source objects before they enter the core.

This spec owns:

- `GPUClipStackDescriptor`;
- `GPUClipSaveRecordDescriptor`;
- `GPUClipElementID`;
- `GPUClipElementDescriptor`;
- `GPUClipState`;
- `GPUClipOperation`;
- `GPUClipPlan`;
- `GPUClipElementPlan`;
- `GPUClipBoundsPlan`;
- `GPUClipScissorPlan`;
- `GPUClipAnalyticPlan`;
- `GPUClipStencilPlan`;
- `GPUClipMaskPlan`;
- `GPUClipShaderPlan`;
- `GPUClipRoute`;
- `GPUClipAtomicGroup`;
- `GPUClipOrderingToken`;
- `GPUClipCachePlan`;
- `GPUClipBudgetPolicy`;
- `GPUClipDiagnostic`.

Owned by other specs:

- normalized command envelope and captured command facts:
  `01-normalized-draw-commands.md`;
- path/coverage atlas keys, entry refs, use tokens, mutation plans, and mask
  artifacts: `19-path-coverage-atlas-strategy.md`;
- path/shape descriptor rules and path geometry diagnostics used by clip path
  elements: `25-path-stroke-geometry-pipeline.md`;
- texture/view/sampler ownership for mask textures:
  `18-texture-image-ownership.md`;
- WGSL binding, packing, and reflection: `11-wgsl-layout-binding-abi.md`;
- draw insertion and sort-window legality:
  `15-draw-layer-planner-and-sort-policy.md`;
- destination-copy/intermediate strategy:
  `20-destination-read-strategy.md`;
- layer and filter semantics: `08-layer-and-filter-plans.md` and
  `23-filter-effect-pipeline.md`.

`MaterialKey` must not include clip stack identity, scissor rectangles, mask
coordinates, atlas entry refs, stencil ordering tokens, save-record IDs, or
clip budget state. `GPURenderPipelineKey` may include clip-related layout,
depth/stencil state, render-step identity, sample count, and shader ABI facts
only when they affect executable validity.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUClipStackDescriptor` | Dumpable captured clip state for one command or layer scope. |
| `GPUClipSaveRecordDescriptor` | Stable save-record facts: record ID, generation, parent relation, state, bounds, and lifetime class. |
| `GPUClipElementID` | Stable descriptor-local element identity independent of object addresses. |
| `GPUClipElementDescriptor` | One clip element: shape kind, operation, transform, AA mode, bounds, source order, and provenance. |
| `GPUClipState` | Normalized state classification: `Empty`, `WideOpen`, `DeviceRect`, `DeviceRRect`, `AnalyticSimple`, `Complex`, or `Refused`. |
| `GPUClipOperation` | Accepted operation: `Intersect` or `Difference`; unsupported operations refuse during normalization. |
| `GPUClipPlan` | Per-draw or per-layer accepted/refused clipping plan. |
| `GPUClipElementPlan` | Effective per-element decision for one draw: ignore, cull, geometric intersect, scissor, analytic, stencil, mask, shader, or refuse. |
| `GPUClipBoundsPlan` | Conservative draw bounds, clip bounds, inner/outer bounds, snapped integer bounds, mask bounds, and finite proof. |
| `GPUClipScissorPlan` | Integer target-space scissor with rounding, target intersection, dynamic-state policy, and diagnostic reason. |
| `GPUClipAnalyticPlan` | Analytic rect/rrect coverage facts carried by render-step or WGSL payload. |
| `GPUClipStencilPlan` | Depth/stencil producer-consumer plan, stencil/depth state, atomic group, clears, and ordering. |
| `GPUClipMaskPlan` | Coverage-mask route using standalone or atlas-resident mask resources. |
| `GPUClipShaderPlan` | Registered clip shader route or stable refusal. |
| `GPUClipRoute` | Selected route: `NoClip`, `EmptyCull`, `ScissorOnly`, `GeometricIntersection`, `AnalyticCoverage`, `StencilCoverage`, `CoverageMask`, `ShaderMask`, `CPUPreparedGPU`, `CPUReferenceOnly`, or `RefuseDiagnostic`. |
| `GPUClipAtomicGroup` | Planner-local group that keeps clip producers and consumers ordered together. |
| `GPUClipOrderingToken` | Token connecting clip producer work, mask mutation/upload, and clipped consumers. |
| `GPUClipCachePlan` | Cache policy for descriptors, effective-element analysis, mask artifacts, and stencil plans. |
| `GPUClipBudgetPolicy` | Limits for stack depth, effective elements, mask bytes, stencil groups, pass splits, clip shader cost, and retries. |
| `GPUClipDiagnostic` | Structured diagnostic product for accepted or refused clip routes. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Captured Clip Descriptor

`GPUClipStackDescriptor` records:

- descriptor version;
- command or layer scope provenance;
- target dimensions and device-pixel scale when captured;
- stack state classification;
- conservative stack bounds;
- save-record descriptors in deterministic order;
- active element descriptors in deterministic oldest-to-newest order;
- current save-record ID and generation;
- operation mode for the aggregate stack;
- whether shader clips are present;
- whether any element uses inverse fill or difference semantics;
- stable refusal reason when capture cannot produce a deterministic
  descriptor.

`GPUClipSaveRecordDescriptor` records:

- save-record ID;
- parent save-record ID when relevant;
- generation or descriptor hash;
- active element range;
- outer and inner conservative bounds;
- aggregate operation;
- state classification;
- lifetime: command-local, layer-local, recording-local, or refused;
- shader clip descriptor ID when present.

`GPUClipElementDescriptor` records:

- element ID and source order;
- element kind: rect, rrect, path, shader, or refused;
- operation: intersect or difference;
- anti-aliasing mode and pixel-snapping facts;
- local-to-device transform facts;
- local, device, outer, inner, and conservative bounds;
- fill rule and inverse-fill behavior;
- immutable shape key or canonical shape data hash;
- mutability and lifetime facts;
- diagnostic provenance.

The descriptor must not contain:

- `SkClipStack`, `SkPath`, `SkRRect`, `SkShader`, or mutable legacy objects;
- raw pointers, object addresses, or mutable generation IDs without canonical
  descriptor facts;
- raw `GPU` handles;
- cached mask texture handles or atlas coordinates.

If a legacy clip source is mutable or cannot be converted into a stable key,
normalization refuses with a stable diagnostic before route selection.

## State And Operation Semantics

`GPUClipState` values:

| State | Meaning |
|---|---|
| `Empty` | No pixels can pass; affected commands are culled with diagnostics. |
| `WideOpen` | The clip equals the target bounds; no clipping work is needed. |
| `DeviceRect` | One device-space rect intersect clip can be represented as scissor. |
| `DeviceRRect` | One device-space rrect intersect clip may use analytic coverage. |
| `AnalyticSimple` | Bounded rect/rrect combinations can use geometric or analytic handling. |
| `Complex` | One or more elements require stencil, mask, shader-mask, or refusal. |
| `Refused` | The descriptor exists only as a stable refusal. |

Accepted operations:

- `Intersect`: the element restricts visible coverage to the element shape.
- `Difference`: the element removes coverage from the current clip.

`Union`, `Xor`, non-dumpable platform clip state, and arbitrary shader-derived
operations are not accepted clip stack operations in this target. If a future
API introduces them, it must add explicit descriptor, route, and validation
rules before product support.

Difference semantics must be explicit in every plan. A route must not silently
approximate difference clips as intersect clips, clamp them to scissor, or drop
them. Difference may be flattened into a coverage mask only when the mask key,
contents, bounds, and inverse/combine behavior are validated by spec 19.

## Per-Draw Clip Planning

`GPUClipPlan` is produced for each command or layer scope after
`NormalizedDrawCommand` enters `GPUDrawAnalysis`.

It records:

- clip stack descriptor hash and version;
- command ID, draw family, transform, geometry, and style summary;
- selected route;
- effective element list;
- element plans;
- draw bounds before clipping;
- clipped draw bounds;
- fill-style invariant clipped bounds when needed by atlas or stencil routes;
- scissor plan;
- analytic plan;
- stencil plan;
- mask plan;
- shader plan;
- layer/filter/destination-read interaction facts;
- ordering tokens and atomic groups;
- resource declarations;
- budget decisions;
- stable diagnostic fields.

Per-draw planning is allowed to simplify:

- empty clip to command cull;
- wide-open clip to no clip;
- clip that fully contains the draw to no clip plus optional scissor;
- draw that fully covers a simple clip to a flood-fill or clip-shaped geometry
  route when the render step accepts it;
- compatible intersect rect/rrect elements into draw geometry;
- integral device rect clips into scissor;
- one accepted device-space rect/rrect into analytic coverage.

Every simplification must record the proof category and the facts used. Missing
proof keeps the route conservative or refuses; it must not approximate.

## Bounds And Culling

`GPUClipBoundsPlan` records:

- unclipped draw bounds in local, device, and target coordinates when relevant;
- conservative stack bounds;
- effective clipped draw bounds;
- fill-style invariant clipped bounds;
- snapped integer scissor bounds;
- mask bounds and mask origin when a mask route is used;
- stencil producer bounds and consumer bounds when stencil is used;
- filter or destination-read expansions that affect required clip bounds;
- finite, empty, unbounded, or invalid classification;
- target intersection and device-pixel-scale facts;
- culling proof or refusal reason.

Rules:

- Bounds are conservative. They may overestimate work but must not exclude
  pixels that can affect output.
- Empty clip culling is a route result and remains visible in diagnostics.
- Culling is forbidden when a later layer, filter, shader, destination read,
  or restore operation observes the would-be culled pixels unless an accepted
  spec proves the observation unchanged.
- Perspective, non-finite, NaN, or coordinate-overflow bounds refuse unless an
  accepted route names a safe full-target strategy within budget.
- Full-target masks are allowed only when `GPUClipBudgetPolicy` accepts the
  byte and pass cost; otherwise the route refuses.

## Route Matrix

This matrix describes the complete target surface. It is not an implementation
order.

| Clip family | Preferred route | Required behavior |
|---|---|---|
| Empty clip | `EmptyCull` | Cull affected command/layer and emit diagnostic. |
| Wide-open clip | `NoClip` | No scissor, stencil, or mask work; target bounds remain known. |
| Integral device rect intersect | `ScissorOnly` | Use integer target scissor; no material or mask change. |
| Non-integral rect intersect | `ScissorOnly` plus analytic or `AnalyticCoverage` | Round conservatively; preserve AA edges through accepted analytic coverage or refuse. |
| Device rrect intersect | `AnalyticCoverage` | Use accepted rect/rrect analytic coverage with explicit radius/edge facts. |
| Compatible rect/rrect intersect with draw geometry | `GeometricIntersection` | Modify planned geometry/edge flags only when exact enough and render step accepts it. |
| Complex intersect path | `StencilCoverage`, `CoverageMask`, or refusal | Use depth/stencil producer-consumer, coverage mask artifact, or stable refusal. |
| Difference rect/rrect/path | `CoverageMask`, `StencilCoverage`, or refusal | Preserve subtract/invert semantics explicitly. |
| Nested complex stack | `StencilCoverage`, `CoverageMask`, or refusal | Preserve element ordering, combine semantics, bounds, and atomicity. |
| Clip shader | `ShaderMask` or refusal | Only registered descriptor-based routes are accepted; no arbitrary SkSL or source string. |
| Unbounded or non-dumpable clip | `RefuseDiagnostic` | Refuse before pass construction. |

### `ScissorOnly`

`GPUClipScissorPlan` records:

- target-space integer rectangle;
- rounding mode and conservative outsets;
- target intersection;
- device-pixel-scale facts;
- whether dynamic scissor state or pass grouping is required;
- proof that scissor-only clipping preserves AA semantics or that no AA edge is
  involved;
- stable refusal reason when scissor is invalid.

Scissor does not express curved AA coverage or difference. It may be combined
with analytic, stencil, or mask routes to reduce rasterization bounds.

### `GeometricIntersection`

`GeometricIntersection` is accepted only when:

- the clip element is intersect, not difference;
- the draw geometry and clip geometry can be represented by the same accepted
  render step after intersection;
- transform relations are finite and precision-bounded;
- AA edge flags, stroke inflation, hairline behavior, and fill rules remain
  equivalent;
- diagnostics record the original and intersected geometry facts.

If the route cannot prove equivalence, it must fall back to another accepted
clip route or refuse. It must not mutate legacy geometry objects.

### `AnalyticCoverage`

`GPUClipAnalyticPlan` records:

- accepted shape class: rect, simple rrect, tab rrect, or other explicitly
  promoted analytic clip;
- local/device coordinate mapping;
- radius, edge flags, inversion, and AA mode;
- uniform or payload fields;
- render-step or WGSL snippet requirement;
- interaction with coverage and blend;
- pipeline/layout contribution when the analytic clip changes WGSL or fixed
  state;
- proof that at most the accepted analytic clip set is active for the draw.

Analytic clip payload values are pass-local facts. Shape values do not enter
`MaterialKey`.

### `StencilCoverage`

`GPUClipStencilPlan` records:

- producer geometry or mask source;
- producer render step and depth/stencil state;
- consumer render steps and depth/stencil state;
- `GPUClipAtomicGroup`;
- stencil/depth clear, load, store, and discard policy;
- stencil reference/value policy when used;
- scissor for producer and consumers;
- target sample count and depth/stencil attachment requirements;
- ordering token linking producers and consumers;
- pass split or layer boundary restrictions;
- budget and capability decisions.

Rules:

- producer and consumer work for one clip sequence is atomic unless a later
  accepted spec proves a safe split;
- sort/merge cannot interleave unrelated work inside an atomic clip sequence;
- stencil producer work must execute before every consumer that depends on it;
- stale or uncleared stencil state refuses instead of being reused silently;
- depth/stencil capability absence returns `RefuseDiagnostic` or a mask route
  when that route is accepted;
- stencil routes cannot rely on active attachment sampling or CPU readback.

### `CoverageMask`

`GPUClipMaskPlan` records:

- coverage source kind: clip stack, save-record clip, operation-specific clip,
  or layer-local clip;
- `GPUCoverageMaskDescriptor`;
- `GPUCoverageAtlasPlan` or standalone mask plan;
- accepted artifact type: `CoverageMaskArtifact` or `PathAtlasArtifact` when
  reusable path coverage is proven;
- mask bounds, origin, padding, coverage quality, and sampling policy;
- combine and invert semantics;
- texture/view/sampler ownership through `GPUTextureOwnershipPlan`;
- `GPUCoverageAtlasBinding` when sampled;
- upload or compute-write mutation plan;
- ordering token and use token;
- budget and retry/split decisions;
- stable diagnostics.

Rules:

- CPU preparation may rasterize or pack only the coverage mask artifact; it
  must not render the clipped draw, layer, filter, or scene contents.
- Mask bytes, atlas residency, texture origin, generation, and use tokens are
  resource/payload facts, not material identity.
- Upload-before-sample or compute-write-before-sample ordering is mandatory.
- Linear filtering of clip masks is not assumed. The default target is nearest
  sampling unless a route proves a different sampling policy.
- Difference and inverse behavior must be represented in the mask content or
  explicit combine/invert payload.
- Atlas retry/split uses spec 19 and is legal only when it preserves layer,
  destination-read, filter, target, and clip atomicity.

### `ShaderMask`

`GPUClipShaderPlan` records:

- registered clip shader descriptor ID;
- descriptor version and capability requirements;
- accepted route: analytic WGSL coverage, render-to-alpha-mask, or compute
  alpha-mask when promoted;
- child resources and uniforms;
- WGSL module and ABI validation facts;
- bounds, sampling, and finite proof;
- mask/intermediate resource declarations when used;
- CPU oracle descriptor when validation needs reference behavior;
- refusal reason when the shader is unregistered or unsupported.

Arbitrary Skia/SkSL clip shaders are refused. Registered clip shaders follow
the same philosophy as runtime effects: descriptor-based Kotlin/CPU behavior
for reference, parser-validated WGSL for GPU, and no dynamic SkSL compiler.

Until a registered clip shader route is accepted, `clipShader` normalization
must produce `RefuseDiagnostic` for product GPU rendering.

## Clip And Layers

Layer planning consumes clip facts in two places:

- clip active at layer creation limits the layer source, offscreen target, and
  child draw bounds;
- clip active at restore/composite limits the parent composite result.

`GPULayerPlan` owns the semantic layer relationship. `GPUClipPlan` owns the
execution technique for clipping layer source draws and layer composites.

Rules:

- layer offscreen allocation may use clipped bounds only when filter, blend,
  alpha, and destination-read semantics prove no required pixels are removed;
- a layer with attached filter must preserve filter source bounds and filter
  expansion before applying final clip rules from spec 23;
- culling a layer because of empty clip is legal only when no destination-read
  or filter observes its contents;
- direct-to-parent layer elision must include clip equivalence proof;
- clip masks used by a layer are resources with explicit lifetime and
  generation, not hidden layer textures.

## Clip, Filters, And Destination Reads

Filters and destination reads can expand the pixels required by a clipped
command.

Rules:

- `GPUFilterBoundsPlan` owns filter expansion, crop, tile, and reverse bounds.
  `GPUClipBoundsPlan` may intersect final output bounds with active clip only
  after required filter source/input bounds are preserved.
- Backdrop and destination reads use `GPUDestinationReadPlan`; clip bounds may
  reduce read bounds only when conservative expansion and target intersection
  prove equivalence.
- A clip route must not cull source pixels that a later filter node samples.
- Destination-read copies and clip mask uploads/writes are independent
  barriers; planner sort windows must respect both.
- Active attachment sampling remains forbidden. Clip masks and destination
  snapshots must be separate sampled resources.

## Clip And Draw Planning

`GPUDrawAnalysis` records `GPUClipPlan` for every accepted, culled, or refused
command. The plan contributes to:

- route selection;
- render-step selection;
- conservative culling;
- `SortKey` preimage;
- `GPUDrawInvocation` role and atomic group;
- resource declarations;
- payload requirements;
- pass split and barrier requirements;
- diagnostics.

`GPUDrawLayerPlanner` consumes:

- `GPUClipRoute`;
- `GPUClipAtomicGroup`;
- `GPUClipOrderingToken`;
- scissor grouping facts;
- stencil producer/consumer facts;
- mask mutation/upload facts;
- atlas generation and retry/split facts.

It may sort and batch clipped draws only inside windows that preserve:

- original paint order where needed;
- clip producer before consumer;
- stencil clear/load/store correctness;
- mask upload/write before sample;
- atlas use-token and eviction safety;
- destination-read ordering;
- layer/filter isolation.

`UnknownOverlap` with a clip producer, clip mask mutation, or destination read
is treated as incompatible for movement.

## WGSL, ABI, And Payload

Analytic clip, mask sampling, and shader-mask routes may contribute WGSL and
payload facts.

Rules:

- analytic clip uniforms use `WGSLPackingPlan` and group roles from
  `11-wgsl-layout-binding-abi.md`;
- coverage mask textures use `GPUCoverageAtlasBinding` or an accepted sampled
  texture binding through spec 19 and spec 18;
- mask coordinates, scissor rectangles, atlas entry refs, generation tokens,
  and stencil refs are payload/resource facts, not `MaterialKey` facts;
- pipeline keys may include clip ABI/layout/depth-stencil state when it changes
  executable validity;
- complete modules must be validated and reflected through `wgsl4k` before a
  WGSL clip route is promoted;
- payload gathering consumes accepted `GPUClipPlan` products and must not
  choose a new clip route.

`GPUPayloadGatherer` writes:

- analytic clip parameters;
- mask-to-device or device-to-mask transforms;
- mask bounds and inverse atlas size;
- invert/combine flags;
- clip shader uniforms and child bindings when registered;
- stencil reference values only when encoded as payload rather than fixed
  dynamic state.

## Cache And Budget Policy

`GPUClipCachePlan` may cache:

- normalized descriptor hashes;
- effective-element analysis for equivalent draw/clip/transform/style facts;
- analytic plan identities;
- stencil plan identities;
- coverage mask artifact keys;
- atlas residency lookup products;
- clip shader descriptor lowering products.

Cache hits are performance evidence only. A miss must recompute, choose another
accepted route, or refuse without changing output.

`GPUClipBudgetPolicy` records:

- maximum active element count;
- maximum effective element count per draw;
- maximum clip stack descriptor byte size;
- maximum clip mask dimensions and area;
- maximum clip mask bytes per command, layer, recording, and frame;
- maximum stencil atomic groups per pass;
- maximum clip-induced pass splits;
- maximum atlas retry/split count;
- maximum clip shader uniforms/resources and dispatch/render cost;
- full-target mask policy;
- configurable policy limits versus hard capability limits.

Embedding code may increase accepted policy budgets before recording. A command
cannot raise its own budget during route selection. Diagnostics must state
whether refusal came from hard capability limits or policy limits that can be
configured larger.

## Routing Policy

Clip route selection follows:

1. Validate `GPUClipStackDescriptor`.
2. Return `EmptyCull` when state is empty.
3. Return `NoClip` when state is wide open and the draw/layer needs no
   scissor.
4. Try `ScissorOnly` for integral device rect intersect clips.
5. Try `GeometricIntersection` when draw and clip geometry can be combined
   exactly enough.
6. Try `AnalyticCoverage` for accepted rect/rrect analytic clips.
7. Try `StencilCoverage` when depth/stencil capabilities, target state, and
   ordering accept producer-consumer clipping.
8. Try `CoverageMask` through `GPUCoverageAtlasPlan` or standalone
   `CoverageMaskArtifact`.
9. Try `ShaderMask` only for registered clip shader descriptors.
10. Return `RefuseDiagnostic`.

`CPUReferenceOnly` may provide oracle behavior for validation. It is not a
product route.

The route must refuse when:

- descriptor capture is nondeterministic;
- an element lacks a stable shape or shader key;
- operations cannot be represented by the selected route;
- bounds are unbounded or invalid and full-target strategy is not accepted;
- active element count or mask bytes exceed budget;
- depth/stencil, storage, copy, upload, or sampled texture capability is
  unavailable for the selected route;
- stencil producer/consumer ordering cannot be preserved;
- mask upload/write-before-sample ordering cannot be proven;
- atlas entry is stale, evicted, in use without legal retry, or over budget;
- destination-read, layer, filter, or target boundaries make split/retry
  illegal;
- clip shader descriptor is unregistered;
- CPU preparation would render full command/layer pixels instead of typed
  coverage.

## Diagnostics

Every accepted, culled, or refused clip route emits `GPUClipDiagnostic`.

Fields:

- command ID, layer ID, or resource owner;
- clip stack descriptor hash and version;
- state classification;
- selected route or refusal;
- active save-record ID and generation;
- element count and effective element count;
- element plan summary;
- operation summary, including difference/inverse flags;
- draw bounds, clip bounds, scissor bounds, mask bounds, and target bounds;
- analytic plan hash when used;
- stencil plan hash, atomic group, and ordering token when used;
- mask descriptor hash, artifact key hash, atlas generation, entry ref, and
  mutation action when used;
- shader descriptor ID and WGSL validation facts when used;
- resource descriptors and usage flags;
- budget policy ID, budget used, budget remaining, and hard/policy flag;
- simplification proof category;
- culling/elision decision;
- pass split, retry, or barrier action;
- stable reason code.

Stable reason-code examples:

- `unsupported.clip.descriptor_invalid`
- `unsupported.clip.stack_unbounded`
- `unsupported.clip.stack_too_deep`
- `unsupported.clip.operation`
- `unsupported.clip.element_key_nondeterministic`
- `unsupported.clip.path_mutable`
- `unsupported.clip.shape_unsupported`
- `unsupported.clip.scissor_invalid`
- `unsupported.clip.analytic_unsupported`
- `unsupported.clip.geometric_intersection_unproven`
- `unsupported.clip.stencil_unavailable`
- `unsupported.clip.stencil_ordering_illegal`
- `unsupported.clip.stencil_state_stale`
- `unsupported.clip.mask_bounds_invalid`
- `unsupported.clip.mask_key_nondeterministic`
- `unsupported.clip.mask_budget_exceeded`
- `unsupported.clip.mask_upload_unavailable`
- `unsupported.clip.shader_unregistered`
- `unsupported.clip.shader_bounds_unbounded`
- `unsupported.clip.destination_read_split_illegal`
- `unsupported.clip.layer_interaction_unproven`
- `unsupported.clip.filter_bounds_unproven`
- `unsupported.clip.cpu_rendered_texture_forbidden`

## Telemetry

`GPUTelemetryLedger` records clip counters when clipping is touched:

- clip descriptor count;
- empty, wide-open, scissor, geometric, analytic, stencil, mask, shader, and
  refused route counts;
- active element count histogram;
- effective element count histogram;
- scissor state change count;
- analytic clip payload count;
- stencil producer count and consumer count;
- stencil atomic group count;
- mask artifact count and bytes;
- atlas lookup/hit/miss/stale/retry counts for clip masks;
- clip-induced pass split count;
- clip shader descriptor count;
- clip budget pressure count;
- hard capability refusal count;
- policy budget refusal count.

Performance reports must distinguish:

- correctness support for clip routes;
- mask or stencil cost;
- cache efficiency;
- clip-induced batching loss;
- pass split cost;
- unsupported/refused clip complexity.

## Validation Requirements

Promoted clip behavior requires:

- canonical dumps for `GPUClipStackDescriptor`,
  `GPUClipSaveRecordDescriptor`, `GPUClipElementDescriptor`, `GPUClipPlan`,
  `GPUClipElementPlan`, `GPUClipBoundsPlan`, `GPUClipScissorPlan`,
  `GPUClipAnalyticPlan`, `GPUClipStencilPlan`, `GPUClipMaskPlan`,
  `GPUClipShaderPlan`, `GPUClipOrderingToken`, `GPUClipCachePlan`,
  `GPUClipBudgetPolicy`, and `GPUClipDiagnostic`;
- descriptor key tests for equivalent and different clip stacks;
- element key tests for rect, rrect, path, transform, AA mode, intersect,
  difference, inverse, and mutable-shape refusal;
- scissor tests for target intersection, integer rounding, empty clips, and
  non-AA rect clips;
- geometric intersection positive and negative tests;
- analytic clip WGSL/ABI/payload tests before analytic routes are promoted;
- stencil producer-consumer ordering tests before stencil routes are promoted;
- stencil stale/clear/load/store negative tests;
- coverage-mask key, upload/write-before-sample, atlas generation, retry,
  eviction, and budget tests before mask routes are promoted;
- clip shader registered descriptor, WGSL validation, uniform packing, bounds,
  and unregistered refusal tests before shader-mask routes are promoted;
- planner tests proving sort/merge cannot cross clip atomic groups, mask
  mutations, destination reads, layer boundaries, or unknown overlaps;
- layer/filter/destination-read interaction tests for clipped offscreen
  bounds, filter expansion, backdrop reads, and direct-to-parent elision;
- texture ownership tests proving mask textures are atlas/provider resources,
  not user images or active attachments;
- CPU oracle or explicit refusal evidence for promoted clip families;
- GPU evidence for every claimed GPU clip route;
- PM evidence that shows route counts, bounds, budgets, pass splits, and
  refusal counts.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient implementation slice may
accept only:

- `WideOpen`;
- `Empty`;
- simple integral `DeviceRect` scissor;
- stable refusals for complex clips, difference clips, stencil clips, masks,
  clip shaders, and unbounded stacks.

It may include descriptor, diagnostic, culling, and scissor tests needed to
prove these routes. It must not claim broad clip-stack support, path clips,
coverage masks, stencil clips, or clip shader support until the relevant
routes satisfy this spec.

## Non-Goals

- Do not port Graphite `ClipStack`, `ClipAtlasManager`, or
  `CoverageMaskRenderStep`.
- Do not expose a mutable Canvas clip stack inside `:gpu-renderer`.
- Do not rely on SkSL for clipping.
- Do not treat clip stack identity as material identity.
- Do not approximate difference clips by silently dropping them.
- Do not use scissor as a substitute for curved AA coverage.
- Do not sample an active render attachment as a clip mask.
- Do not let clip mask cache hits count as correctness evidence.
- Do not hide clip errors behind draw drops or log-only behavior.
- Do not CPU-render complete clipped draws, layers, filters, or scenes into a
  texture for GPU composition.
