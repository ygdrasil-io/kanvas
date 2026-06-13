# Coordinate, Transform, Bounds, And Precision Policy

Status: Draft
Date: 2026-06-13

## Purpose

Define the target coordinate-space, transform, bounds, pixel-grid, and
precision contract for the GPU-first renderer.

Many renderer specs own specialized plans: `GPUClipBoundsPlan`,
`GPUDestinationReadBounds`, `GPUFilterBoundsPlan`, `GPULayerBoundsPlan`,
`GPUPathBoundsPlan`, text atlas bounds, image sampling rectangles, and target
store/readback bounds. This spec owns the shared policy those plans consume:
coordinate-space identity, transform classification, inverse availability,
finite proofs, conservative bounds propagation, pixel/texel rounding,
precision domains, diagnostics, and validation gates.

This is a target-complete spec. It is not an implementation slice. The first
rect/rrect plus solid/linear-gradient slice may validate only identity,
translate, scale, axis-aligned affine, conservative bounds, and target
rounding, but the target model must already explain how perspective,
non-invertible, unbounded, filter-expanded, layer-local, glyph-local, and
texture-space behavior is promoted or refused.

The target is Graphite-inspired but Kanvas-owned:

- every geometry, sampling, layer, filter, clip, destination-read, and target
  operation states its source and destination coordinate spaces;
- transform and bounds facts are value objects, not mutable canvas state;
- conservative bounds are proof artifacts, not best-effort hints;
- integer copy/allocation/scissor bounds use explicit rounding policy;
- WGSL and CPU reference paths consume the same transform descriptors;
- unsupported transforms or unproven bounds refuse with stable diagnostics
  instead of silently widening, approximating, or CPU-rendering fallback
  textures.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module, naming, and Graphite evidence policy;
- `01-normalized-draw-commands.md` for captured transform, clip, layer,
  material, bounds, and ordering facts;
- `02-gpu-recording-task-graph.md` for draw analysis, culling, pass bounds,
  and task construction;
- `03-material-key-wgsl.md` for material transform facts that affect WGSL code
  shape;
- `04-pipeline-key-cache-resources.md` for cache, resource, and generation
  policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for transform/bounds evidence and
  refusal families;
- `10-gpu-execution-context-submission.md` for target dimensions, device pixel
  scale, surface generation, and readback facts;
- `11-wgsl-layout-binding-abi.md` for transform uniform/storage layout,
  vertex/instance coordinate packing, and reflection;
- `12-blend-color-target-state.md` for destination-read and target-state
  interactions;
- `13-performance-telemetry-cache-gates.md` for transform/bounds telemetry,
  caches, budgets, and performance gates;
- `15-draw-layer-planner-and-sort-policy.md` for sort-window overlap proofs;
- `17-payload-gathering-and-slots.md` for transform payloads, bounds payloads,
  and pass-local slots;
- `18-texture-image-ownership.md` for texture/view/sampler coordinate facts;
- `20-destination-read-strategy.md` for destination-read bounds and
  fragment-to-copy coordinate mapping;
- `21-text-glyph-pipeline.md` for glyph, atlas, SDF, bitmap, and SVG glyph
  coordinate spaces;
- `22-image-bitmap-codec-pipeline.md` for image source rectangles,
  orientation, pixel layout, mip, and upload bounds;
- `23-filter-effect-pipeline.md` for filter graph coordinates, local
  matrices, bounds propagation, crop/tile/sample plans, and precision;
- `24-clip-stencil-mask-pipeline.md` for clip stack coordinates, stencil/mask
  bounds, and clip reduction proofs;
- `25-path-stroke-geometry-pipeline.md` for path transform, tolerance,
  inflation, atlas, mask, stencil, and bounds plans;
- `26-draw-vertices-mesh-pipeline.md` for vertex attribute spaces, primitive
  bounds, index buffers, and mesh-like coordinates;
- `27-registered-runtime-effects-registry.md` for runtime-effect input/output
  coordinate contracts and sample radius;
- `28-layer-savelayer-execution.md` for saveLayer creation/restore transforms,
  offscreen origins, source/filter/backdrop/composite bounds;
- `29-color-management-pipeline.md` for value-spec interactions when target
  store/readback bounds produce evidence.

## Skia And Graphite Evidence

Relevant local evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/`.

Useful source landmarks:

- Skia `SkMatrix` / `SkM44` model 2D affine and perspective transforms,
  classification, inversion, and rect mapping.
- Skia `SkRect` / `SkIRect` and `roundOut()`-style behavior show that floating
  geometry bounds and integer allocation/copy/scissor bounds are separate
  products.
- Graphite `geom/Transform` wraps local-to-device transforms and exposes a
  validity/type model used by draw recording.
- Graphite `DrawList` and `DrawListLayer` assert valid transforms and non-empty
  non-NaN draw bounds before storing draws, deduplicate transform objects, and
  accumulate pass/destination-read bounds.
- Graphite `DrawListBase` stores transform lists separately from draw params,
  proving transform identity can be shared without becoming a material key.
- Graphite `Renderer::bounds()` and cover-bounds render steps show that
  inverse fill, scissor, and transformed shape bounds affect render-step
  bounds differently.
- Graphite path and clip atlas code records transformed shape bounds, mask
  device bounds, key bounds, padding, and local-to-device transform as explicit
  atlas facts.
- Graphite `DrawContext` converts accumulated destination-read bounds to
  integer pixel bounds before creating copy tasks.
- Graphite image-filter APIs carry source subset, clip bounds, output subset,
  and offset facts for filtered images.
- Skia text blobs distinguish conservative and tight run bounds, showing that
  text bounds are a proof product with route-specific precision.

Kanvas adopts these invariants:

- invalid or non-finite transforms do not enter accepted GPU routes;
- local-to-target and target-to-local availability are explicit facts;
- bounds are accumulated conservatively and rounded only at the boundary that
  needs integer pixels;
- full-target widening is a route decision with budget and diagnostics, not a
  silent repair for unknown bounds;
- transform, bounds, and pixel-grid facts are dumpable and hashable;
- specialized plans can add route-specific inflation or crop behavior, but
  must cite the common coordinate and rounding policy.

Kanvas intentionally does not copy:

- Skia/Graphite C++ class ownership or packed bit layouts;
- Skia's mutable canvas matrix stack as a renderer-core contract;
- Ganesh/Graphite backend abstractions;
- accepting invalid bounds because a later CPU fallback can render them;
- hidden platform coordinate behavior from browser, Dawn, or native surfaces.

## Ownership Boundary

This spec owns:

- `GPUCoordinateSpace`;
- `GPUCoordinateSpaceID`;
- `GPUCoordinateSpaceRole`;
- `GPUTransformDescriptor`;
- `GPUTransformClass`;
- `GPUTransformPlan`;
- `GPUTransformChain`;
- `GPUInverseTransformPlan`;
- `GPUTransformPrecisionPlan`;
- `GPUPixelGridPlan`;
- `GPUBoundsDescriptor`;
- `GPUBoundsKind`;
- `GPUBoundsPlan`;
- `GPUBoundsProof`;
- `GPUBoundsExpansionPlan`;
- `GPURoundingPlan`;
- `GPUClipReductionProof`;
- `GPUCoordinatePayloadPlan`;
- `GPUTransformCachePlan`;
- `GPUTransformBudgetPolicy`;
- `GPUTransformDiagnostic`.

Owned by other specs:

- draw-command capture fields and command IDs:
  `01-normalized-draw-commands.md`;
- draw analysis, task creation, pass bounds, and culling:
  `02-gpu-recording-task-graph.md`;
- clip execution plans and clip mask/stencil resources:
  `24-clip-stencil-mask-pipeline.md`;
- destination-read strategy, copy resources, and binding plans:
  `20-destination-read-strategy.md`;
- filter graph node plans, crop/tile/sample semantics, and intermediates:
  `23-filter-effect-pipeline.md`;
- path/stroke/tessellation tolerance and geometry-specific inflation:
  `25-path-stroke-geometry-pipeline.md`;
- layer target allocation, saveLayer elision, and restore composites:
  `28-layer-savelayer-execution.md`;
- text/glyph atlas resources, subrun plans, and glyph artifacts:
  `21-text-glyph-pipeline.md`;
- image decode/upload/source sampling semantics:
  `22-image-bitmap-codec-pipeline.md`;
- WGSL binding layout and Kotlin packing:
  `11-wgsl-layout-binding-abi.md`;
- payload writing and resource binding:
  `17-payload-gathering-and-slots.md`.

Specialized `*BoundsPlan` and `*TransformPlan` objects remain in their owning
specs. They must reference or embed the common descriptors from this spec when
their behavior affects routing, culling, resource allocation, WGSL layout,
payload packing, cache keys, or diagnostics.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUCoordinateSpace` | Dumpable coordinate-space descriptor with role, units, origin, axis convention, owner scope, and generation. |
| `GPUCoordinateSpaceID` | Stable ID for a coordinate space within a recording, target, layer, texture, glyph, filter graph, or resource. |
| `GPUCoordinateSpaceRole` | Role: local, layer, parent layer, target, device pixel, fragment, texture texel, normalized texture, glyph, atlas, filter graph, clip mask, destination copy, or raw. |
| `GPUTransformDescriptor` | Numeric transform descriptor between two spaces, with matrix values, type, precision, invertibility, and provenance. |
| `GPUTransformClass` | Classification: identity, translate, scale, scale-translate, rect-stays-rect affine, general affine, perspective, singular, non-finite, or refused. |
| `GPUTransformPlan` | Accepted/refused transform usage plan for a draw, layer, filter, clip, image, text, path, vertex, or runtime-effect route. |
| `GPUTransformChain` | Ordered sequence of descriptors and spaces used to compose source-to-target behavior. |
| `GPUInverseTransformPlan` | Inverse availability, precision, domain restriction, and refusal facts. |
| `GPUTransformPrecisionPlan` | Numeric domain, tolerances, rounding, f32/f16/double CPU oracle relation, and WGSL parity requirements. |
| `GPUPixelGridPlan` | Device-pixel, texel, fragment, sample, half-pixel, origin, and device-scale rules. |
| `GPUBoundsDescriptor` | Rect or region descriptor with coordinate space, kind, finite proof, inclusivity, and version. |
| `GPUBoundsKind` | Bound role: source, local, stroke-inflated, transformed, clipped, sample, crop, destination-read, pass, allocation, scissor, atlas, mask, stencil, store, or readback. |
| `GPUBoundsPlan` | Accepted/refused common bounds plan with propagation, expansion, intersection, rounding, full-target policy, and diagnostics. |
| `GPUBoundsProof` | Evidence that bounds conservatively include all pixels/texels/samples a route may read or write. |
| `GPUBoundsExpansionPlan` | Route-specific expansion: AA outset, stroke inflation, blur radius, sample radius, tile mode, filter kernel, mip footprint, or padding. |
| `GPURoundingPlan` | Float-to-integer policy for copy, allocation, scissor, atlas, mask, readback, and evidence bounds. |
| `GPUClipReductionProof` | Proof that clip intersection may reduce work without removing pixels observed by filters, destination reads, layers, or inverse fills. |
| `GPUCoordinatePayloadPlan` | Transform/bounds facts that must be carried as uniforms, storage, vertex attributes, instance data, or push-like constants. |
| `GPUTransformCachePlan` | Cache key and invalidation policy for transform chains, inverse matrices, rounded bounds, and coordinate payload layouts. |
| `GPUTransformBudgetPolicy` | Bounds area, full-target expansion, transform count, precision, inverse, and payload budgets. |
| `GPUTransformDiagnostic` | Structured accepted/refused diagnostic product for coordinate, transform, bounds, precision, and rounding behavior. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Coordinate Space Model

Every accepted route must know the coordinate space for:

- source geometry or source pixels;
- public local coordinates;
- layer-local coordinates;
- parent layer coordinates;
- target logical coordinates;
- device-pixel coordinates after device scale;
- fragment coordinates used by WGSL;
- texture texel coordinates;
- normalized texture coordinates when a sampler uses them;
- atlas page coordinates;
- glyph source coordinates;
- filter graph coordinates;
- clip mask/stencil coordinates;
- destination-copy coordinates;
- readback coordinates.

`GPUCoordinateSpace` records:

- role and stable ID;
- owner scope: command, layer, filter graph, texture, atlas, target, frame, or
  recording;
- dimensions or unbounded status;
- origin convention;
- axis orientation;
- unit scale;
- device-pixel scale when applicable;
- generation token when the space is resource-backed;
- diagnostic label.

Rules:

- A transform cannot be applied if either endpoint coordinate space is unknown.
- Resource-backed spaces must include generation facts when stale resources can
  make coordinates invalid.
- Layer-local and offscreen target coordinates must record origin translation;
  a layer texture origin is not implicitly the parent target origin.
- Texture coordinates distinguish texel-space, normalized-space, and
  fragment-derived sample coordinates.
- Public API local space is not assumed to equal shader local space after
  image orientation, glyph atlas placement, filter crop, or layer isolation.

## Transform Model

`GPUTransformDescriptor` records:

- source and destination `GPUCoordinateSpaceID`;
- numeric matrix values;
- matrix shape: 3x3 2D, 4x4 homogeneous, or reduced descriptor;
- transform class;
- determinant or invertibility proof when relevant;
- finite proof for all stored values;
- perspective row/column facts;
- rect-stays-rect proof when relevant;
- axis alignment and orientation preservation facts;
- scale, skew, rotation, and translation summaries;
- precision domain and tolerance class;
- descriptor version and provenance.

`GPUTransformPlan` records:

- descriptor ID and hash;
- use site: geometry, material, image sampling, text, path, filter, clip,
  layer, destination read, store/readback, or runtime effect;
- required forward transform;
- required inverse transform, if any;
- whether transform can be baked into vertices, carried as payload, folded into
  sampling coordinates, or requires WGSL helper code;
- selected route or refusal;
- diagnostics.

Rules:

- Non-finite transforms refuse before route promotion.
- Singular transforms are accepted only for routes that never require inverse
  mapping and can prove conservative output bounds.
- Perspective transforms are dependency-gated per route. A route must prove
  WGSL math, bounds, sample footprint, and precision before accepting
  perspective.
- Transform composition is explicit through `GPUTransformChain`; hidden
  mutation of current canvas state is not a renderer-core operation.
- A route may not downgrade perspective to affine or affine to scale-translate
  unless it proves equivalence within the declared tolerance.
- Matrix decomposition is advisory. Behavior is defined by the descriptor and
  classification, not by a lossy decomposition.

## Inverse Transform Policy

`GPUInverseTransformPlan` records:

- whether an inverse is required;
- inverse descriptor or refusal reason;
- precision domain used to compute it;
- determinant threshold or singularity policy;
- domain restriction when inverse is only valid for a bounded region;
- CPU oracle descriptor;
- WGSL helper route when inverse is evaluated in shader;
- tolerance class.

Required inverse use cases include:

- shader local-coordinate reconstruction from fragment coordinates;
- image/bitmap sampling;
- gradient and runtime-effect local coordinate evaluation;
- text SDF and atlas sampling when coordinates are reconstructed in shader;
- filter source sampling;
- destination-copy sampling from fragment coordinates;
- clip shader or analytic clip evaluation;
- path atlas placement when mask-local and device coordinates are related.

Rules:

- If an inverse is required and not proven, the route refuses.
- CPU-computed inverse constants and WGSL inverse evaluation must share the
  same descriptor and tolerance policy.
- Near-singular transforms refuse unless a route-specific policy records the
  accepted precision loss and evidence.
- An inverse used only for bounds proof must still be dumpable and tested.

## Bounds Model

`GPUBoundsDescriptor` records:

- coordinate space;
- bounds kind;
- floating rect, integer rect, or accepted region representation;
- empty, finite, infinite, NaN, or unbounded classification;
- inclusivity/exclusivity convention for integer bounds;
- source descriptor or operation provenance;
- version and diagnostic label.

`GPUBoundsPlan` records:

- source bounds;
- transform chain applied to bounds;
- expansions and intersections;
- clip reduction proof;
- target/resource intersection;
- integer rounding plan when needed;
- full-target widening decision;
- selected route or refusal.

Common bounds kinds:

- source/local geometry bounds;
- stroke-inflated local bounds;
- transformed device/target bounds;
- active clip bounds;
- layer source/filter/backdrop/composite bounds;
- filter input/output/sample/crop bounds;
- destination-read bounds;
- path atlas/mask/stencil bounds;
- text glyph/subrun/atlas bounds;
- image source/subset/destination bounds;
- vertex/mesh primitive bounds;
- pass bounds;
- allocation/copy/scissor/readback bounds.

Rules:

- Bounds must be conservative for all pixels, texels, or samples read or
  written by the accepted route.
- Empty bounds may cull only when side effects, destination reads, layers,
  filters, and ordering tokens prove no visible or resource effect remains.
- Unknown bounds do not silently become full-target work. Full-target widening
  requires route acceptance, budget acceptance, target usage facts, and
  telemetry.
- Bounds cannot be tightened by active clip when filters, backdrop reads,
  destination reads, inverse fills, or sample radii can observe pixels outside
  the clipped output.
- A bounds plan that contains NaN or non-finite values refuses unless the
  source operation is explicitly defined to cull before reaching GPU routes.
- Specialized specs may add stricter rules but cannot loosen these common
  safety rules.

## Bounds Propagation

Bounds propagation uses named operations:

| Operation | Meaning |
|---|---|
| `MapForward` | Transform source bounds to destination space conservatively. |
| `MapReverse` | Compute needed source bounds for a requested output region. |
| `Inflate` | Expand by stroke width, AA radius, filter kernel, sample radius, mip footprint, or padding. |
| `Intersect` | Intersect with target, clip, crop, subset, atlas page, or resource bounds. |
| `Union` | Join independent draw/node/layer/pass bounds. |
| `RoundOut` | Convert float bounds to integer bounds that include all touched pixels/texels. |
| `RoundIn` | Convert only when a route proves losing border pixels is equivalent; otherwise forbidden. |
| `Snap` | Align to pixel grid by an explicit snapping plan. |
| `WidenToTarget` | Accept full-target route with budget and diagnostic evidence. |
| `Refuse` | Reject unprovable or unsupported bounds. |

Forward propagation is used for culling, pass bounds, scissor selection,
allocation, and draw ordering. Reverse propagation is used for filters,
destination reads, crop/tile behavior, image sampling, and runtime-effect
sample-radius contracts.

`GPUBoundsProof` records:

- operation chain;
- all source descriptors;
- route-specific expansions;
- integer rounding;
- target/resource intersections;
- tolerance and precision class;
- evidence fixture when promoted.

## Pixel Grid And Rounding Policy

`GPUPixelGridPlan` records:

- target logical size;
- device-pixel scale;
- pixel-center convention;
- fragment coordinate convention;
- texture texel-center convention;
- normalized texture coordinate mapping;
- sample count and sample position policy when relevant;
- origin orientation;
- integer bounds inclusivity;
- replay/target translation when present.

`GPURoundingPlan` records:

- operation: allocation, copy, scissor, atlas entry, mask, stencil, pass,
  readback, evidence, or payload bounds;
- source floating bounds;
- target integer space;
- `floor`, `ceil`, `roundOut`, `roundNearest`, `snapThenRoundOut`, or refusal;
- AA or sample-padding applied before rounding;
- target/resource intersection applied after rounding;
- empty-after-rounding behavior.

Rules:

- Allocation, copy, scissor, atlas, mask, stencil, and readback bounds default
  to conservative `RoundOut`.
- `RoundIn` is forbidden unless a specialized spec proves equivalence.
- Pixel snapping changes geometry and must be recorded in
  `GPUTransformPlan`; it is not an invisible optimization.
- Half-pixel offsets are explicit payload or vertex facts; they are not
  inferred by a shader snippet.
- Device-pixel scale changes invalidate target-space integer bounds and any
  cached payloads that depend on them.

## Precision Policy

`GPUTransformPrecisionPlan` records:

- CPU calculation domain: Float, Double, fixed-point, or descriptor-specific;
- WGSL calculation domain: `f32`, accepted `f16`, integer, or mixed;
- input coordinate range;
- maximum accepted bounds area;
- determinant/near-singular threshold;
- tolerance class for CPU/GPU comparison;
- rounding mode;
- overflow and underflow policy;
- fixture ID.

Rules:

- WGSL transform math is `f32` unless a route explicitly accepts another
  domain.
- `f16` transform math is refused until precision fixtures promote it for a
  narrow route.
- CPU double precision may be used for oracle or descriptor construction, but
  promoted GPU behavior must record the WGSL precision that actually runs.
- Very large coordinate ranges must refuse or normalize through a recorded
  route. They must not rely on undefined GPU overflow behavior.
- Perspective divide must guard or refuse zero/near-zero `w`.
- Integer overflow in copy/allocation/scissor bounds refuses.

## Integration Points

### Normalization And Draw Analysis

`NormalizedDrawCommand` captures transform and bounds descriptors. It does not
store mutable canvas matrix state. `GPUDrawAnalysis` consumes these descriptors
to produce culling, ordering, layer, clip, destination-read, and route facts.

Invalid transforms or non-finite bounds may be refused during normalization
when no later route can define them. Otherwise they enter as refused diagnostic
commands, not accepted draws.

### Material, WGSL, And Payloads

Material keys include transform facts only when transform class changes WGSL
code shape, layout, or required helper variants. Concrete matrix values and
rounded bounds are payload facts unless they affect pipeline validity.

`GPUCoordinatePayloadPlan` may contribute:

- local-to-target matrices;
- target-to-local matrices;
- source-to-texture or source-to-atlas matrices;
- destination-copy coordinate transforms;
- normalized texture scale/bias;
- layer origin and target offset;
- rounded bounds uniforms;
- sample-radius or kernel footprint facts.

Payloads use `WGSLPackingPlan`, `WGSLBindingLayout`, and
`GPUPayloadGatherer`. ABI validation must prove that CPU packing and WGSL
reflection agree.

### Clips, Destination Reads, Filters, And Layers

Clip reduction, destination-read copy bounds, filter expansion, and layer
allocation all consume `GPUBoundsProof`.

Rules:

- Clip bounds can reduce work only with `GPUClipReductionProof`.
- Destination reads use reverse-mapped read bounds and conservative integer
  copy bounds.
- Filter bounds must expand for sample radius before output clip reduction.
- saveLayer bounds hints are allocation hints, not clips.
- Layer restore uses creation-time and restore-time coordinate facts
  separately.

### Images, Text, Paths, And Vertices

Image, text, path, and vertex routes add domain-specific bounds and transform
constraints:

- image routes record source rect, destination rect, orientation transform,
  texel-to-sample mapping, mip footprint, and tile behavior;
- text routes record glyph source, atlas page, subrun, mask, SDF, and target
  coordinate spaces;
- path routes record local bounds, stroke inflation, inverse-fill target
  bounds, flattening tolerance, mask bounds, and stencil cover bounds;
- vertex routes record attribute coordinate spaces, primitive bounds,
  index-buffer validity, and primitive blending bounds.

Those specialized plans remain authoritative for their domain-specific
details, but their coordinate and bounds proofs must be compatible with this
spec.

## Cache, Versioning, And Budgets

`GPUTransformCachePlan` may cache:

- transform descriptor hashes;
- composed transform chains;
- inverse transform descriptors;
- rect-stays-rect and axis-alignment proofs;
- transformed bounds;
- rounded integer bounds;
- pixel-grid descriptors;
- coordinate payload layouts;
- route-specific bounds proofs.

Cache keys include:

- coordinate-space IDs and generations;
- transform descriptor versions;
- transform class;
- matrix values or reduced transform values;
- source bounds descriptor hash;
- expansion plan hash;
- rounding plan hash;
- target/device-pixel scale;
- precision plan hash;
- capability facts that affect WGSL or resource validity.

Cache keys must not include:

- mutable canvas state identity;
- Kotlin object addresses;
- live `GPU` handles;
- transient command IDs unless the cached object is command-local;
- cache residency state.

`GPUTransformBudgetPolicy` records:

- maximum accepted coordinate magnitude;
- maximum transformed bounds area;
- maximum full-target widened routes per recording/frame;
- maximum transform chain length;
- maximum inverse calculations per route;
- maximum transform payload bytes;
- maximum bounds expansion area for filters, AA, strokes, and sample radius;
- maximum integer allocation/copy/readback area;
- diagnostic retention limits.

Budget pressure refuses or splits only when a specialized spec defines a split
that preserves semantics. The renderer must not shrink bounds, drop sample
radius, ignore perspective, or replace a transform with an easier class to fit
budget.

## Diagnostics

Every accepted or refused coordinate route emits `GPUTransformDiagnostic`.

Fields:

- transform/bounds plan ID;
- source and destination coordinate-space IDs;
- transform descriptor hash and class;
- inverse requirement and result;
- precision plan;
- pixel-grid plan;
- source bounds, expanded bounds, clipped bounds, rounded bounds, and target
  intersection;
- full-target widening decision and budget state;
- integration point: draw, clip, destination read, filter, layer, path, text,
  image, vertices, runtime effect, store, or readback;
- WGSL helper/module ID when transform math runs in shader;
- CPU oracle fixture and tolerance when validated;
- stable reason code.

Stable reason-code examples:

- `unsupported.transform.space_unknown`
- `unsupported.transform.non_finite`
- `unsupported.transform.singular`
- `unsupported.transform.inverse_required`
- `unsupported.transform.near_singular`
- `unsupported.transform.perspective`
- `unsupported.transform.class_downgrade`
- `unsupported.transform.precision`
- `unsupported.transform.chain_too_long`
- `unsupported.transform.WGSL_validation`
- `unsupported.bounds.unknown`
- `unsupported.bounds.non_finite`
- `unsupported.bounds.nan`
- `unsupported.bounds.unbounded`
- `unsupported.bounds.not_conservative`
- `unsupported.bounds.rounding`
- `unsupported.bounds.integer_overflow`
- `unsupported.bounds.full_target_budget`
- `unsupported.bounds.clip_reduction_unproven`
- `unsupported.bounds.sample_radius_unproven`
- `unsupported.bounds.filter_expansion_unproven`
- `unsupported.bounds.layer_hint_as_clip`
- `unsupported.pixel_grid.device_scale_changed`
- `unsupported.pixel_grid.sample_position`
- `unsupported.coordinate.payload_layout`
- `unsupported.coordinate.cache_key_nondeterministic`

Older domain-specific reason codes may remain in specialized reports, but
common transform/bounds refusals should use the `unsupported.transform.*`,
`unsupported.bounds.*`, `unsupported.pixel_grid.*`, or
`unsupported.coordinate.*` families where this spec owns the refusal.

## Telemetry

`GPUTelemetryLedger` records coordinate counters:

- coordinate-space descriptor count by role;
- transform descriptor count by class;
- transform chain length histogram;
- inverse-required count and refusal count;
- perspective request and promotion/refusal count;
- near-singular refusal count;
- bounds descriptor count by kind;
- bounds expansion area by reason;
- full-target widening count and area;
- rounded integer bounds area by operation;
- clip reduction proof count;
- destination-read reverse bounds count;
- filter reverse/forward bounds count;
- layer allocation bounds count;
- transform cache hit/miss count;
- rounded-bounds cache hit/miss count;
- transform payload bytes;
- coordinate refusal count by reason.

Performance reports must distinguish:

- accepted GPU transform math;
- CPU reference/oracle transform math;
- descriptor construction;
- resource allocation/copy bounds;
- culling and ordering bounds;
- non-normative diagnostic-only lanes.

## Validation Requirements

Promoted coordinate behavior requires:

- canonical dumps for `GPUCoordinateSpace`, `GPUTransformDescriptor`,
  `GPUTransformPlan`, `GPUTransformChain`, `GPUInverseTransformPlan`,
  `GPUTransformPrecisionPlan`, `GPUPixelGridPlan`, `GPUBoundsDescriptor`,
  `GPUBoundsPlan`, `GPUBoundsProof`, `GPUBoundsExpansionPlan`,
  `GPURoundingPlan`, `GPUClipReductionProof`,
  `GPUCoordinatePayloadPlan`, `GPUTransformCachePlan`,
  `GPUTransformBudgetPolicy`, and `GPUTransformDiagnostic`;
- deterministic descriptor hashing tests;
- identity, translate, scale, scale-translate, rect-stays-rect affine, and
  general affine transform fixtures;
- perspective refusal or promotion fixtures per route;
- non-finite, NaN, singular, and near-singular refusal fixtures;
- inverse-required and inverse-not-required fixtures;
- conservative forward and reverse bounds fixtures;
- stroke/AA/filter/sample-radius expansion fixtures when those routes are
  promoted;
- clip reduction proof fixtures;
- saveLayer bounds-hint-not-clip fixtures;
- destination-read integer copy bounds fixtures;
- texture texel/normalized coordinate fixtures;
- device-pixel scale and target resize invalidation fixtures;
- pixel snapping and half-pixel payload fixtures when accepted;
- CPU/GPU parity tests for WGSL transform helpers;
- budget pressure tests for full-target widening, large coordinates, and large
  allocation/copy/readback bounds;
- stable refusal tests for unsupported perspective, unbounded filters,
  unproven sample radius, invalid layer bounds, and integer overflow;
- PM evidence showing route, coordinate spaces, transform class, bounds chain,
  rounded integer bounds, CPU/GPU/diff artifacts, budget state, and stable
  refusals.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice may validate:

- local, target, device-pixel, fragment, and texture coordinate spaces;
- identity, translate, scale, and rect-stays-rect affine transforms;
- finite local-to-target transform descriptors;
- no inverse required for solid color;
- simple inverse for gradient local coordinate reconstruction when validated;
- conservative rect/rrect local and target bounds;
- target intersection and `RoundOut` scissor/allocation/readback bounds;
- device-pixel scale invalidation;
- stable refusal for perspective, non-finite, singular, unbounded, and
  unproven bounds.

It must not claim support for:

- arbitrary perspective transforms;
- near-singular inverse shader math;
- filter reverse bounds;
- destination-read sample-radius bounds;
- complex path/stroke inflation beyond promoted geometry fixtures;
- text/glyph atlas coordinate plans;
- image orientation/mip/tile sampling beyond promoted image fixtures;
- full-target widening as a general repair for unknown bounds.

Those routes require later evidence against this spec.

## Non-Goals

- Do not port Skia's matrix, rect, or Graphite transform classes.
- Do not keep mutable canvas state in renderer-core objects.
- Do not infer coordinate spaces from resource handles.
- Do not silently widen unknown bounds to full target.
- Do not use bounds hints as clips.
- Do not drop filter expansion, sample radius, stroke inflation, AA outset, or
  inverse-fill coverage to fit budget.
- Do not replace perspective, singular, or non-finite transforms with simpler
  transforms.
- Do not hide transform, bounds, rounding, or precision failures behind CPU
  fallback rendering.
