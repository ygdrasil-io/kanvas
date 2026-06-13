# Path, Stroke, And Geometry Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target path, stroke, and geometry pipeline for the GPU-first
renderer.

This spec owns how `FillShape` and `StrokeShape` commands become accepted or
refused geometry routes. It covers path descriptors, fill rules, inverse fills,
stroke expansion, dash/path-effect policy, curve flattening, tessellation,
stencil-cover, prepared geometry buffers, path coverage masks, render-step
selection, geometry budgets, diagnostics, and validation gates.

This is a target-complete spec. It is not an implementation slice. The first
implementation slice can still stop at rect/rrect plus solid/linear materials
as defined in `14-first-slice-contract.md`.

The target is Graphite-inspired but Kanvas-owned:

- Graphite's useful pattern is the separation between `Geometry`, renderer
  choice, render steps, clip planning, atlas insertion, and paint key
  gathering;
- Kanvas keeps normalized immutable geometry descriptors instead of mutable
  Skia objects inside the core;
- Kanvas uses the WebGPU-like `GPU` facade and WGSL, not SkSL;
- `CPUPreparedGPU` is allowed only for typed geometry or coverage artifacts
  consumed by GPU work;
- unsupported geometry returns `RefuseDiagnostic` with stable reason codes,
  not silent full CPU rendering.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module, naming, and Graphite equivalence
  policy;
- `01-normalized-draw-commands.md` for `FillShape` and `StrokeShape`
  command envelopes;
- `02-gpu-recording-task-graph.md` for `GPUDrawAnalysis`, `GPUTaskList`, and
  `GPURenderStep` responsibilities;
- `04-pipeline-key-cache-resources.md` for `PrecomputedGeometryArtifact`,
  `PathAtlasArtifact`, `CoverageMaskArtifact`, resource provider, and cache
  policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for path fill/stroke target maturity;
- `11-wgsl-layout-binding-abi.md` for geometry uniform, storage, and vertex
  binding ABI;
- `13-performance-telemetry-cache-gates.md` for geometry counters and gates;
- `15-draw-layer-planner-and-sort-policy.md` for multi-step render-step
  ordering, stencil atomicity, and sort windows;
- `17-payload-gathering-and-slots.md` for geometry uniforms, buffer bindings,
  mask bindings, and pass-local payload slots;
- `19-path-coverage-atlas-strategy.md` for path atlas, coverage atlas,
  mutation, upload, retry, and atlas diagnostics;
- `20-destination-read-strategy.md` for destination-dependent geometry routes;
- `24-clip-stencil-mask-pipeline.md` for clipping interaction, stencil
  producers, coverage masks, and clip ordering.

The older `.upstream/specs/geometry-coverage/` pack remains migration and CPU
oracle context. This spec is the target GPU-renderer contract.

## Graphite And Skia Evidence

Relevant local Graphite evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/`.

Useful source landmarks:

- `Device::drawGeometry()` performs transform validation, clip visitation,
  renderer or path-atlas selection, optional flush before atlas mutation,
  paint key gathering, render-step recording, stroke/fill splitting, and
  ordering updates.
- `geom/Geometry.h` is a compact container for shapes, vertices, subruns,
  per-edge AA quads, coverage mask shapes, and analytic blur masks.
- `geom/Shape.h` is the shape descriptor side used by renderer selection.
- `Renderer` and `RenderStep` separate a high-level geometry strategy from
  fixed shader/state/data emission steps.
- `RendererProvider` exposes multiple geometry strategies such as analytic
  rrects, middle-out fans, tessellated wedges, tessellated curves,
  tessellated strokes, coverage masks, and cover-bounds behavior.
- `PathAtlas`, `RasterPathAtlas`, and `ComputePathAtlas` demonstrate the
  split between shape coverage generation, atlas residency, and later mask
  sampling.
- Graphite can split stroke-and-fill into separate stroke and fill recording
  work, and can add inner fill work for coverage-heavy fills.
- Graphite treats clip producer/consumer work, atlas mutation, destination
  reads, and render-step ordering as planner-visible constraints.

Kanvas adopts these invariants:

- geometry route selection is explicit and diagnostic-rich;
- a draw can expand into multiple render-step invocations;
- path atlas insertion is a resource mutation before later sampling;
- stroke and fill can become separate planned geometry work;
- path/stroke bounds must include AA, stroke inflation, and route-specific
  outsets before sorting, culling, and destination-read decisions;
- prepared geometry and masks are typed artifacts, not hidden CPU fallback
  pixels.

Kanvas intentionally does not copy:

- Graphite class names, class layout, C++ ownership, virtual dispatch, or
  backend plugin model;
- Skia's mutable `SkPath`, `SkStrokeRec`, `SkVertices`, or `SkPaint` objects
  as renderer-core API;
- SkSL shader machinery;
- Graphite's exact renderer heuristics, thresholds, or bit packing;
- any fallback that CPU-renders a complete unsupported draw, layer, or scene
  into a texture.

## Ownership Boundary

The legacy adapter owns:

- converting Skia-like API shapes into immutable Kanvas descriptors;
- resolving mutable `SkPath`/`SkRRect`/stroke state before the command enters
  the core;
- rejecting non-finite coordinates, nondeterministic path identities, or
  unsupported path-effect sources before route selection when they cannot be
  described stably;
- preserving source provenance for diagnostics.

This spec owns:

- `GPUShapeDescriptor`;
- `GPUPathDescriptor`;
- `GPUPathVerbSlice`;
- `GPUPathFillRule`;
- `GPUStrokeDescriptor`;
- `GPUStrokeStyle`;
- `GPUPathEffectDescriptor`;
- `GPUGeometryPlan`;
- `GPUGeometryRoute`;
- `GPUPathRoute`;
- `GPUStrokeRoute`;
- `GPUPathBoundsPlan`;
- `GPUPathTransformPlan`;
- `GPUPathTolerancePlan`;
- `GPUPathFlatteningPlan`;
- `GPUPathTessellationPlan`;
- `GPUStrokeExpansionPlan`;
- `GPUStencilCoverPlan`;
- `GPUPreparedGeometryPlan`;
- `GPUGeometryBufferPlan`;
- `GPUGeometryRenderStepPlan`;
- `GPUGeometryCachePlan`;
- `GPUGeometryBudgetPolicy`;
- `GPUGeometryDiagnostic`.

Owned by other specs:

- command envelopes and captured draw state: `01-normalized-draw-commands.md`;
- path/coverage atlas residency, keys, generations, mutation, upload, and
  retry: `19-path-coverage-atlas-strategy.md`;
- clip state, stencil clip producers, coverage-mask clip routes, and clip
  atomicity: `24-clip-stencil-mask-pipeline.md`;
- material identity and WGSL snippets: `03-material-key-wgsl.md` and
  `16-material-dictionary-and-snippet-registry.md`;
- texture/image ownership: `18-texture-image-ownership.md`;
- destination reads: `20-destination-read-strategy.md`;
- text outline glyph handoff: `21-text-glyph-pipeline.md`.

`MaterialKey` must not include path object identity, stroke width, join/cap,
dash arrays, geometry bounds, atlas entry refs, prepared buffer handles, or
route budgets. `GPURenderPipelineKey` may include geometry render-step identity,
vertex/storage layout, primitive topology, depth/stencil state, sample count,
and WGSL ABI facts when they affect executable validity.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUShapeDescriptor` | Normalized shape value for rect, rrect, oval, line, path, or future mesh-like geometry. |
| `GPUPathDescriptor` | Immutable path facts: verbs, points, conic weights, fill rule, inverse flag, bounds, volatility, and canonical key. |
| `GPUPathVerbSlice` | Deterministic verb/point slice used by dumps, artifact keys, and route validation. |
| `GPUPathFillRule` | Accepted fill semantics: `NonZero`, `EvenOdd`, plus explicit inverse-fill flag. |
| `GPUStrokeDescriptor` | Stroke request attached to `StrokeShape` or stroke-and-fill lowering. |
| `GPUStrokeStyle` | Width, hairline policy, cap, join, miter, stroke alignment when accepted, and AA policy. |
| `GPUPathEffectDescriptor` | Dash or future path-effect facts; unsupported effects refuse with stable diagnostics. |
| `GPUGeometryPlan` | Per-command accepted/refused geometry plan before material and resource binding. |
| `GPUGeometryRoute` | Selected route: analytic, tessellation, stencil-cover, path atlas, coverage mask, prepared geometry, or refusal. |
| `GPUPathRoute` | Path-specific route details for fill/inverse/fill-rule behavior. |
| `GPUStrokeRoute` | Stroke-specific route details for expansion, dash, cap/join, and hairline behavior. |
| `GPUPathBoundsPlan` | Conservative local, device, clipped, inflated, AA-outset, and render-step bounds. |
| `GPUPathTransformPlan` | Transform class, finite/invertible proof, perspective policy, local-to-device and device-to-local facts. |
| `GPUPathTolerancePlan` | Flatness, curve tolerance, conic tolerance, precision domain, and algorithm version. |
| `GPUPathFlatteningPlan` | CPU or GPU accepted flattening facts, segment counts, and edge budgets. |
| `GPUPathTessellationPlan` | GPU-native tessellation route facts: topology, patch kind, buffers, WGSL, and limits. |
| `GPUStrokeExpansionPlan` | Stroke-to-fill, stroke-to-strip, analytic stroke, or refusal route. |
| `GPUStencilCoverPlan` | Multi-step stencil producer plus cover consumer plan for filled paths. |
| `GPUPreparedGeometryPlan` | `CPUPreparedGPU(PrecomputedGeometryArtifact)` plan for flattened/tessellated/packed buffers. |
| `GPUGeometryBufferPlan` | Vertex, index, edge, patch, indirect, or storage-buffer layout consumed by render steps. |
| `GPUGeometryRenderStepPlan` | Ordered render-step invocations emitted by one geometry route. |
| `GPUGeometryCachePlan` | Cache policy for immutable descriptors, prepared geometry, tessellation products, and route analysis. |
| `GPUGeometryBudgetPolicy` | Hard and policy limits for verbs, edges, curves, generated vertices, stroke complexity, bytes, and pass steps. |
| `GPUGeometryDiagnostic` | Structured accepted/refused geometry diagnostic product. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Shape And Path Descriptors

`GPUShapeDescriptor` records:

- descriptor version;
- shape kind: rect, rrect, oval, line, path, or refused;
- canonical local bounds;
- conservative device bounds when known at capture time;
- AA mode and edge flag facts;
- source provenance;
- immutable shape key or stable refusal reason.

`GPUPathDescriptor` records:

- path descriptor version;
- canonical path data hash;
- verb count, point count, conic weight count, contour count, and close flags;
- fill rule and inverse-fill flag;
- path bounds and finite proof;
- convexity/concavity classification when known;
- volatile/mutable-source facts;
- degenerate contour policy;
- canonical winding facts when available;
- source object provenance for diagnostics only.

The descriptor must not contain:

- `SkPath`, `SkRRect`, `SkVertices`, `SkStrokeRec`, or mutable legacy objects;
- raw pointers, object addresses, or noncanonical identity;
- raw `GPU` resources;
- atlas coordinates or prepared-buffer handles.

If a path is mutable, nondeterministic, or too expensive to canonicalize within
budget, normalization must either capture an immutable handle with strict
lifetime and content hash rules or refuse with a stable diagnostic.

## Fill Semantics

Accepted fill facts:

- fill rules: `NonZero` and `EvenOdd`;
- inverse fill represented as an explicit semantic flag;
- empty path handling: cull, full-target inverse fill when accepted, or refuse;
- degenerate segment handling: deterministic drop, retain, or refuse by route;
- contour closure and implicit close behavior;
- anti-aliasing requested, disabled, or route-forced;
- local-to-device transform class and precision domain.

Rules:

- Inverse fill is not a material property. It changes geometry/coverage and
  must be present in the geometry or atlas key.
- A route must not approximate `EvenOdd` as `NonZero`, drop inverse fill, or
  convert a path to its bounds unless an accepted analytic proof says the
  output is equivalent.
- Perspective paths refuse unless a promoted route explicitly supports the
  transform without breaking local coordinates, coverage, or stroke semantics.
- Full-target inverse fills require target bounds, clip bounds, and budget
  proof. Otherwise they refuse.

## Stroke Semantics

`GPUStrokeDescriptor` records:

- stroke operation: stroke-only or stroke-and-fill after adapter lowering;
- stroke width and finite proof;
- hairline policy and device-pixel width facts;
- cap: butt, round, square;
- join: miter, round, bevel;
- miter limit and miter fallback behavior;
- stroke alignment when a future API exposes it; center is the only default
  target assumption;
- dash descriptor when present;
- path effect descriptor when present;
- source transform class and non-uniform-scale facts;
- AA and precision policy.

Stroke rules:

- Zero, negative, NaN, or infinite stroke widths refuse unless they map to an
  accepted hairline/empty policy.
- Hairline strokes are device-space coverage decisions. They must not be
  hidden as arbitrary local-space width changes.
- Non-uniform scale must record whether stroke width is transformed,
  normalized, expanded in device space, or refused.
- Miter joins must record the miter limit and bevel fallback when the limit is
  exceeded.
- Round joins and round caps require either accepted analytic arc handling,
  accepted tessellation, accepted CPU-prepared geometry, or stable refusal.
- Stroke-and-fill can expand into two planned geometry operations. Ordering,
  coverage, and blend behavior must stay equivalent to the source command.
- Dash support is descriptor-driven. Complex dashes, phase overflow, zero
  intervals, unsupported path effects, or dash expansion budget overflow
  produce stable refusals.

## Strategy Taxonomy

### `AnalyticShape`

`AnalyticShape` covers accepted rect/rrect/line/oval-like shapes whose coverage
can be generated directly by a render step or WGSL fragment without path
flattening or atlas sampling.

It records:

- accepted shape class;
- transform class;
- edge flags and AA policy;
- radius/width/cap/join facts when relevant;
- render-step ID;
- required geometry payload fields.

This is the preferred route for rect/rrect and other simple shapes when it is
exact enough.

### `PreparedGeometry`

`PreparedGeometry` uses
`CPUPreparedGPU(PrecomputedGeometryArtifact)` when CPU work flattens, strokes,
tessellates, or packs geometry into buffers consumed by GPU render steps.

It is allowed for:

- convex fan or triangle-list preparation;
- stroke expansion to strips/triangles;
- dash expansion to bounded path or segment geometry;
- edge buffer packing for an accepted WGSL edge coverage step;
- stencil-cover input buffers when GPU-native tessellation is not accepted.

It is not allowed to shade pixels or produce a complete rendered draw texture.
Material shading, blend, target state, and submission remain GPU work.

### `Tessellation`

`Tessellation` is a future `GPUNative` route where WGSL render or compute work
produces path coverage from path/curve data without CPU-prepared coverage
bytes.

It requires:

- accepted curve and patch representation;
- complete WGSL validation and reflection;
- deterministic buffer layout and packing;
- accepted limits for verbs, curves, segments, and generated primitives;
- write/read synchronization when compute generates intermediate buffers;
- GPU evidence before support claims.

### `StencilCover`

`GPUStencilCoverPlan` records a multi-step route:

- stencil producer render-step plan;
- cover or shading consumer render-step plan;
- stencil state, clear/load/store/discard policy;
- scissor and bounds for producer and consumer;
- fill rule and inverse behavior;
- depth/stencil attachment requirements;
- `GPUClipAtomicGroup` or geometry atomic group when clip and geometry both
  use stencil;
- sort-window restrictions.

Stencil-cover is `GPUNative` only when the selected target, sample count,
stencil state, clip state, and ordering all support it.

### `PathAtlasCoverage`

`PathAtlasCoverage` uses `GPUPathAtlasPlan` and usually
`CPUPreparedGPU(PathAtlasArtifact)` or a future
`GPUComputeCoverageAtlasPlan`.

It is appropriate when:

- a path or stroke coverage result is reusable under a stable key;
- atlas dimensions, budgets, generation, and use-token rules accept it;
- sampling the coverage mask preserves fill/stroke semantics;
- material shading still happens in a GPU render pass.

Atlas residency, mutation, retry, upload, and binding are owned by
`19-path-coverage-atlas-strategy.md`.

### `CoverageMask`

`CoverageMask` uses `CPUPreparedGPU(CoverageMaskArtifact)` for one-shot,
clipped, operation-specific, or non-reusable coverage.

It is appropriate when:

- clipping is baked into the mask content;
- inverse/difference semantics are flattened into the coverage artifact;
- persistent atlas reuse is not accepted or not useful;
- upload and sample ordering can be proven.

It follows the same no-full-CPU-render rule as `PathAtlasCoverage`.

### `RefuseDiagnostic`

`RefuseDiagnostic` is the required result when no accepted geometry route can
preserve semantics within capability and budget limits.

Refusal is product behavior. The command remains visible in diagnostics and
support matrices.

## Route Selection

`GPUGeometryPlan` is produced during `GPUDrawAnalysis` before render-pass
construction.

Route selection follows:

1. Validate command, descriptor, transform, stroke, clip, and target facts.
2. Cull empty geometry only when no layer/filter/destination-read semantics
   can observe the result.
3. Try `AnalyticShape` for accepted rect/rrect/line and bounded simple stroke
   cases.
4. Try `GPUNative(Tessellation)` when path/stroke topology, WGSL modules,
   buffers, capabilities, and budgets are promoted.
5. Try `GPUNative(StencilCover)` when fill rule, target, stencil, clip, and
   ordering accept it.
6. Try `GPUNative(GPUComputeCoverageAtlasPlan)` when compute coverage is
   accepted.
7. Try `CPUPreparedGPU(PrecomputedGeometryArtifact)` for accepted geometry
   buffer preparation.
8. Try `CPUPreparedGPU(PathAtlasArtifact)` for reusable path/stroke coverage.
9. Try `CPUPreparedGPU(CoverageMaskArtifact)` for one-shot or clipped
   coverage.
10. Return `RefuseDiagnostic`.

This order describes the target preference: `GPUNative` routes win when proven.
An implementation milestone may initially promote `CPUPreparedGPU` routes for
path fill/stroke because the support matrix marks them as prepared-first, but
the diagnostic must still expose why a `GPUNative` route was unavailable.

The route must refuse when:

- path or stroke identity is nondeterministic;
- bounds, coordinates, transform, or inverse fill are unbounded beyond policy;
- fill rule, stroke style, dash, path effect, cap, join, or miter semantics are
  unsupported by all accepted routes;
- edge, verb, curve, segment, vertex, byte, or render-step budgets are
  exceeded and the budget cannot be configured larger before recording;
- required depth/stencil, storage, vertex, index, copy, upload, sampled
  texture, or synchronization capability is unavailable;
- clip, layer, filter, destination-read, or target state makes the required
  pass split or atomic group illegal;
- WGSL validation/reflection or Kotlin packing evidence is missing for a
  promoted native route;
- CPU preparation would shade the draw or produce a complete rendered texture.

## Bounds, Inflation, And Culling

`GPUPathBoundsPlan` records:

- source local bounds;
- stroke-inflated local bounds when relevant;
- device-space bounds;
- clipped bounds from `GPUClipPlan`;
- AA-outset bounds;
- mask bounds and origin when a mask route is used;
- stencil producer bounds and consumer bounds;
- render-step-specific outsets;
- full-target requirement for inverse fills;
- finite, empty, unbounded, or invalid classification.

Rules:

- Bounds are conservative and may overestimate work, but they must not exclude
  pixels that a route can touch.
- Stroke inflation happens before coverage, clip, and destination-read
  decisions unless an accepted route proves a different order is equivalent.
- AA outsets affect sorting, culling, atlas dimensions, and readback evidence.
- A geometry cull is not legal when a layer, filter, destination read, or
  restore can observe the draw's would-be effect.
- Unknown path bounds are not allowed to become full-target work unless the
  full-target budget accepts it.

## Flattening, Tessellation, And Stroke Expansion

`GPUPathTolerancePlan` defines route-specific tolerance:

- curve flatness in local or device space;
- conic subdivision policy;
- precision domain: f32, f16 when explicitly accepted, or CPU double during
  preparation;
- transform dependency;
- algorithm version for deterministic artifact keys;
- visual tolerance gate used by validation.

`GPUPathFlatteningPlan` records:

- accepted input: lines, quads, conics, cubics, arcs after normalization, or
  refused verb;
- generated segment count;
- edge count and edge budget;
- degenerate segment behavior;
- winding/fill-rule preservation facts;
- CPU or GPU owner of flattening.

`GPUStrokeExpansionPlan` records:

- expansion owner: analytic, CPU-prepared, GPU tessellation, or refused;
- cap/join representation;
- miter fallback;
- round arc subdivision or analytic arc route;
- dash expansion result and interval validation;
- generated vertex/edge/patch counts;
- transform and hairline policy;
- artifact key inputs when CPU-prepared.

Determinism requirements:

- CPU-prepared geometry must produce stable buffers for identical descriptor
  keys, budgets, tolerances, and capabilities.
- GPU-native tessellation must produce deterministic WGSL modules and stable
  layout/packing evidence.
- A tolerance or algorithm-version change invalidates prepared artifacts and
  route evidence.

## Render-Step Planning

`GPUGeometryRenderStepPlan` records every render-step invocation emitted by a
geometry route:

- step ID and version;
- invocation role: shading, coverage, stencil producer, cover consumer,
  inner-fill, stroke, mask sample, or debug/evidence-only;
- primitive topology or draw method;
- vertex/index/storage buffer layout;
- required WGSL modules and ABI facts;
- fixed state contribution: depth/stencil, cull mode, blend compatibility,
  sample count, color writes;
- payload requirements;
- resource requirements;
- draw bounds;
- ordering dependencies;
- diagnostic label.

Rules:

- One source command may produce multiple render-step invocations.
- Stroke-and-fill routes must keep stroke and fill ordering explicit.
- Stencil-cover and clip-stencil routes must share atomic-group rules with
  `24-clip-stencil-mask-pipeline.md`.
- Render-step identity and state can enter `GPURenderPipelineKey`.
- Geometry values and resource coordinates remain payload/resource facts unless
  they affect executable validity.

## Prepared Geometry Artifacts

`GPUPreparedGeometryPlan` records:

- `PrecomputedGeometryArtifact` key;
- source path/shape descriptor hash;
- fill, inverse, stroke, dash, path-effect, transform, tolerance, and budget
  facts that affect buffer contents;
- prepared buffer layout;
- vertex, index, edge, or patch counts;
- byte count and alignment;
- owner scope: command-local, frame-local, recording-local, or cache-resident
  when accepted;
- invalidation facts;
- upload or buffer allocation plan;
- GPU consumer render-step plan.

`PrecomputedGeometryArtifact` may contain:

- CPU-flattened edge lists;
- CPU-generated triangle lists;
- CPU-expanded stroke strips;
- CPU-expanded dash segment geometry;
- CPU-packed patch or curve records for WGSL processing;
- CPU-packed stencil-cover inputs.

It must not contain:

- shaded pixels;
- blended pixels;
- a rendered draw texture;
- layer or scene content;
- raw `GPU` handles as durable identity.

## Interaction With Clip, Layers, And Destination Reads

Geometry planning consumes `GPUClipPlan` facts but does not own clip stack
semantics.

Rules:

- Simple clips may tighten geometry bounds or enable geometric intersection
  only when spec 24 proves equivalence.
- Complex clips may force stencil, coverage mask, atlas, or refusal routes.
- If both path fill and clip use stencil, their producer/consumer ordering must
  be represented by a shared atomic-group policy.
- Path atlas mutation, prepared geometry upload, clip mask upload, destination
  copy, and layer target allocation are separate barriers.
- A route that requires pass splitting must prove the split is legal with
  layer, filter, destination-read, target load/store, and atlas use-token
  rules.
- Destination-dependent blends cannot reorder around geometry producers unless
  `20-destination-read-strategy.md` accepts the ordering.

## Cache And Budget Policy

`GPUGeometryCachePlan` may cache:

- normalized shape descriptors;
- path descriptor hashes;
- route analysis for equivalent path/stroke/transform/clip/capability facts;
- prepared geometry artifacts;
- tessellation buffer layouts;
- stencil-cover plan identities;
- atlas eligibility decisions.

Cache hits are performance evidence only. A miss must recompute, rebuild
within budget, choose another accepted route, or refuse.

`GPUGeometryBudgetPolicy` separates hard capability limits from configurable
policy limits.

Hard limits include:

- maximum buffer size and binding count;
- maximum vertex/index count accepted by the facade path;
- available depth/stencil formats and sample counts;
- required texture, copy, storage, and synchronization features;
- WGSL feature and layout support;
- finite coordinate and f32 precision limits.

Policy limits include:

- maximum path verb count;
- maximum contour count;
- maximum curve count;
- maximum edge count;
- maximum generated segment count;
- maximum stroke join/cap subdivision count;
- maximum dash interval count and generated dash segment count;
- maximum prepared geometry bytes per command, recording, and frame;
- maximum render-step invocations per command;
- maximum stencil-cover pass splits;
- maximum full-target inverse-fill bytes or area;
- maximum geometry preparation time or reporting threshold.

Embedding code may increase accepted policy budgets before recording. A command
cannot raise its own budget during route selection. Diagnostics must state
whether refusal came from hard capability limits or policy limits that can be
configured larger.

## Diagnostics

Every accepted, culled, or refused geometry route emits
`GPUGeometryDiagnostic`.

Fields:

- command ID and draw family;
- source shape descriptor hash and version;
- path descriptor hash when present;
- stroke descriptor hash when present;
- selected route or refusal;
- fill rule and inverse-fill flags;
- stroke width, cap, join, miter, hairline, dash, and path-effect summary;
- transform classification and finite/invertible facts;
- bounds: local, stroke-inflated, device, clipped, AA-outset, mask, stencil,
  and target;
- tolerance, flattening, tessellation, and stroke expansion summaries;
- generated vertex/index/edge/patch counts;
- artifact type and artifact key hash when prepared;
- atlas plan, atlas key, or coverage mask descriptor hash when used;
- render-step plan IDs and invocation roles;
- pipeline-key contribution summary;
- resource descriptors and usage flags;
- budget policy ID, budget used, budget remaining, and hard/policy flag;
- clip/layer/destination-read interaction facts;
- culling, pass split, barrier, or atomic-group decision;
- WGSL validation/reflection facts for promoted native routes;
- stable reason code.

Stable reason-code examples:

- `unsupported.geometry.descriptor_invalid`
- `unsupported.geometry.path_key_nondeterministic`
- `unsupported.geometry.path_mutable`
- `unsupported.geometry.path_empty_inverse_unbounded`
- `unsupported.geometry.path_nonfinite`
- `unsupported.geometry.path_coordinate_too_large`
- `unsupported.geometry.path_verb_unsupported`
- `unsupported.geometry.path_verb_budget_exceeded`
- `unsupported.geometry.path_edge_budget_exceeded`
- `unsupported.geometry.path_curve_budget_exceeded`
- `unsupported.geometry.path_fill_rule`
- `unsupported.geometry.inverse_fill_full_target_budget`
- `unsupported.geometry.perspective_path`
- `unsupported.geometry.tessellation_unavailable`
- `unsupported.geometry.tessellation_budget_exceeded`
- `unsupported.geometry.stencil_cover_unavailable`
- `unsupported.geometry.stencil_cover_ordering_illegal`
- `unsupported.geometry.prepared_buffer_budget_exceeded`
- `unsupported.geometry.prepared_upload_unavailable`
- `unsupported.stroke.width_invalid`
- `unsupported.stroke.hairline_policy`
- `unsupported.stroke.cap`
- `unsupported.stroke.join`
- `unsupported.stroke.miter_limit`
- `unsupported.stroke.round_geometry_unavailable`
- `unsupported.stroke.nonuniform_transform`
- `unsupported.stroke.dash_invalid`
- `unsupported.stroke.dash_complex`
- `unsupported.stroke.path_effect_unregistered`
- `unsupported.stroke.expansion_budget_exceeded`
- `unsupported.geometry.CPU_rendered_texture_forbidden`

Migration diagnostics from `.upstream/specs/geometry-coverage/` may remain in
older lanes, but new GPU renderer diagnostics should prefer these
`unsupported.*` codes inside `:gpu-renderer`.

## Telemetry

`GPUTelemetryLedger` records geometry counters when path/stroke geometry is
touched:

- geometry route counts by family and route;
- path descriptor count and path key refusal count;
- stroke descriptor count and stroke refusal count;
- analytic, tessellation, stencil-cover, prepared geometry, path atlas,
  coverage mask, and refused counts;
- verb, contour, curve, edge, vertex, index, and render-step histograms;
- prepared geometry artifact hit, miss, create, upload, byte, and eviction
  counts;
- atlas eligibility, atlas route, and atlas refusal counts delegated to spec
  19;
- stencil-cover producer and consumer counts;
- pass split and atomic-group counts;
- budget pressure counts;
- hard capability refusal counts;
- WGSL geometry module validation failure counts.

Performance reports must distinguish:

- correctness support for a geometry route;
- CPU preparation cost;
- GPU tessellation or stencil cost;
- atlas/cache efficiency;
- memory pressure;
- refused unsupported geometry.

## Validation Requirements

Promoted path/stroke behavior requires:

- canonical dumps for `GPUShapeDescriptor`, `GPUPathDescriptor`,
  `GPUPathVerbSlice`, `GPUStrokeDescriptor`, `GPUPathEffectDescriptor`,
  `GPUGeometryPlan`, `GPUPathBoundsPlan`, `GPUPathTransformPlan`,
  `GPUPathTolerancePlan`, `GPUPathFlatteningPlan`,
  `GPUPathTessellationPlan`, `GPUStrokeExpansionPlan`,
  `GPUStencilCoverPlan`, `GPUPreparedGeometryPlan`,
  `GPUGeometryBufferPlan`, `GPUGeometryRenderStepPlan`,
  `GPUGeometryBudgetPolicy`, and `GPUGeometryDiagnostic`;
- descriptor determinism tests for equivalent immutable paths and strokes;
- negative tests for mutable/nondeterministic path keys;
- fill rule tests for nonzero, even-odd, inverse, empty, degenerate, convex,
  concave, and multi-contour paths;
- stroke tests for width, hairline, cap, join, miter, round geometry, dash,
  path-effect refusal, and non-uniform transform behavior;
- flattening/tolerance tests that prove deterministic segment and edge counts;
- budget tests for verbs, edges, curves, vertices, bytes, render steps, and
  full-target inverse fills;
- prepared artifact key and invalidation tests;
- render-step expansion tests for single-step, multi-step, stroke-and-fill,
  stencil-cover, and inner-fill style routes when promoted;
- payload tests proving geometry values stay out of `MaterialKey`;
- pipeline-key tests proving only executable layout/state facts enter
  `GPURenderPipelineKey`;
- clip interaction tests with scissor, analytic clip, stencil clip, coverage
  mask clip, and refusal cases when those clip routes are promoted;
- atlas interaction tests when `PathAtlasArtifact` or `CoverageMaskArtifact`
  is used;
- WGSL validation and reflection evidence for every promoted native route;
- CPU oracle or explicit refusal evidence for every promoted path/stroke row;
- GPU evidence before product support claims;
- PM evidence showing route, artifact, bounds, budgets, generated counts,
  diffs, and refusals.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice may use this spec only to
define refusal and boundary behavior.

It may validate:

- rect/rrect analytic geometry descriptors;
- refusal for arbitrary paths and unsupported strokes;
- path/stroke diagnostics and reason codes;
- material-key exclusion of geometry facts;
- render-step and pipeline-key separation for rect/rrect geometry.

It must not claim support for:

- arbitrary path fill;
- arbitrary path stroke;
- dash;
- path effects;
- `PathAtlasArtifact`;
- `CoverageMaskArtifact`;
- `PrecomputedGeometryArtifact`;
- stencil-cover path rendering;
- GPU-native path tessellation.

Those routes require later evidence against this spec.

## Non-Goals

- Do not port Graphite `Geometry`, `Renderer`, `RenderStep`,
  `RendererProvider`, `PathAtlas`, or tessellation code.
- Do not expose Skia mutable shape objects inside `:gpu-renderer`.
- Do not introduce SkSL for path or stroke rendering.
- Do not hide unsupported paths behind CPU-rendered textures.
- Do not claim path/stroke support from descriptor acceptance alone.
- Do not treat cache hits, atlas hits, or CPU oracle results as GPU product
  support without GPU evidence.
