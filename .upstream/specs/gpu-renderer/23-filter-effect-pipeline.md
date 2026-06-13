# Filter Effect Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target filter, image-filter DAG, color-filter, backdrop-filter, and
registered runtime-effect contract for the GPU-first renderer.

This spec expands the high-level `GPUFilterPlan` contract from
`08-layer-and-filter-plans.md` into a target-complete filter/effect pipeline.
It defines filter graph descriptors, node plans, source/backdrop inputs, crop
and tile behavior, bounds propagation, render and compute routes,
intermediate textures, destination-read integration, registered WGSL runtime
effects, budgets, diagnostics, and validation gates.

This is a target-complete spec. It is not an implementation slice and it does
not reduce the filter target to the first deliverable.

The target is Graphite-inspired but Kanvas-owned:

- filter DAGs are explicit plans, not hidden paint or layer behavior;
- every node has a route, resource plan, bounds plan, and diagnostics;
- intermediate textures are ordinary `GPUResourceProvider` resources unless a
  validated typed `CPUPreparedGPU` artifact owns their prepared contents;
- destination/backdrop reads use `GPUDestinationReadPlan`;
- color filters that fit material snippets may fold into `MaterialKey`, while
  image-filter DAG execution stays outside `MaterialKey`;
- runtime effects are accepted only through registered Kanvas descriptors with
  parser-validated WGSL implementations;
- unsupported filter nodes, unbounded bounds, invalid crops, or missing
  resources refuse with stable diagnostics instead of silently dropping the
  effect or rendering a CPU fallback texture.

## Source Specs

This spec depends on:

- `03-material-key-wgsl.md` for the boundary between material color filters,
  registered runtime-effect descriptors, and filter graph execution;
- `04-pipeline-key-cache-resources.md` for render/compute pipeline keys,
  `GPUResourceProvider`, `CPUPreparedGPUArtifactRegistry`, and filter
  intermediate artifacts;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `08-layer-and-filter-plans.md` for `GPULayerPlan`, the semantic
  `GPUFilterPlan`, offscreen requirements, and layer/filter interaction;
- `09-draw-family-support-matrix.md` for image filter, color filter, runtime
  effect, blend, and layer family support;
- `10-gpu-execution-context-submission.md` for render, compute, copy, upload,
  pass-split, and device-generation rules;
- `11-wgsl-layout-binding-abi.md` for filter node resource layouts and WGSL
  reflection;
- `12-blend-color-target-state.md` for `GPUBlendPlan`, `GPUColorPlan`, and
  target-state interactions;
- `13-performance-telemetry-cache-gates.md` for budgets, telemetry, warmup,
  and quarantine;
- `17-payload-gathering-and-slots.md` for per-node uniform/resource payloads;
- `18-texture-image-ownership.md` for filter intermediates, sampled textures,
  views, samplers, and imported/surface/target texture ownership;
- `20-destination-read-strategy.md` for backdrop and destination reads;
- `22-image-bitmap-codec-pipeline.md` for encoded image inputs used by image
  filter source nodes;
- `24-clip-stencil-mask-pipeline.md` for active clip descriptors, clip bounds,
  stencil/mask routes, and clip ordering when filters interact with clipped
  sources or outputs;
- `27-registered-runtime-effects-registry.md` for runtime-effect descriptor
  IDs, uniform schemas, child slots, WGSL plans, CPU oracles, compatibility
  lookup, live-edit metadata, and descriptor diagnostics;
- `28-layer-savelayer-execution.md` for saveLayer source targets,
  initialization/backdrop inputs, filter-chain placement, restore composite,
  and layer ordering tokens.

## Graphite And Skia Evidence

Relevant Skia and Graphite concepts:

- `SkImageFilter` defines a DAG with inputs, color-filter replacement checks,
  local matrix support, fast bounds, and recursive forward/reverse
  `filterBounds()` used for clipping and temporary allocation.
- `SkImageFilters` exposes a broad factory surface: arithmetic blend, blend,
  blur, color filter, compose, crop, displacement map, drop shadow, empty,
  image, magnifier, matrix convolution, matrix transform, merge, offset,
  picture, runtime shader, shader, tile, morphology, and lighting filters.
- `SkImageFilter` semantics imply an isolated source image: drawing happens as
  if into an offscreen, the filter produces a new image, and the filtered
  result is composited with the original blend mode.
- `SkImageFilters::Crop` demonstrates that crop geometry and tile mode are
  filter graph behavior, not merely layer clip behavior.
- `SkImageFilters::RuntimeShader` accepts child image/filter inputs and a
  sample radius. Kanvas maps this only to registered descriptor-based WGSL
  runtime effects, not arbitrary SkSL or runtime shader source strings.
- Skia known runtime effects are used for effects such as arithmetic blending
  and high-contrast color filters. Kanvas adopts registered descriptor
  semantics, but not SkSL compilation.
- Graphite `Device::createImageFilteringBackend()` returns a Graphite image
  filtering backend, and `snapSpecial()` / `drawSpecial()` show that filter
  inputs and outputs are special images backed by GPU resources or views.
- Graphite/Skia bounds and special-image behavior show that filter execution
  needs source bounds, crop bounds, intermediate bounds, coordinate mapping,
  and texture ownership before command encoding.

Kanvas adopts these invariants. Kanvas does not copy Skia image-filter classes,
Skia flattening/deserialization, SkSL runtime shader compilation, Graphite
backend classes, `SkSpecialImage` ownership, or CPU image-filter fallback
providers.

## Ownership Boundary

`08-layer-and-filter-plans.md` owns the semantic relationship between layers,
saveLayer behavior, attached filters, offscreen isolation, and layer restore.
This spec owns the detailed filter/effect pipeline under that semantic plan.

Owned by this spec:

- filter graph descriptors and graph versions;
- filter node IDs, node descriptors, node plans, and dependency edges;
- source, backdrop, destination, image, shader, and intermediate input plans;
- forward and reverse bounds propagation;
- crop, tile, sample-radius, and coordinate-space plans;
- render-pass, compute-pass, and copy/intermediate node routes;
- filter intermediate descriptors, cache keys, and budget policy;
- registered runtime-effect filter descriptors and WGSL node plans;
- color-filter chain lowering when it is part of a filter DAG;
- node-level and graph-level diagnostics.

Owned by other specs:

- `GPULayerPlan` semantic layer isolation and final parent composite:
  `08-layer-and-filter-plans.md`;
- destination/backdrop read strategy, bounds, copy/intermediate resources, and
  bindings: `20-destination-read-strategy.md`;
- clip descriptor execution, final output clipping, clip masks, stencil clip
  routes, and clip ordering: `24-clip-stencil-mask-pipeline.md`;
- generic texture/view/sampler ownership and sampled bindings:
  `18-texture-image-ownership.md`;
- blend/color/target contracts: `12-blend-color-target-state.md`;
- WGSL material snippets and material runtime-effect descriptors:
  `16-material-dictionary-and-snippet-registry.md`;
- registered runtime-effect descriptor lifecycle, compatibility lookup,
  uniform schema, child slot rules, WGSL plan, CPU oracle, and live-edit
  metadata: `27-registered-runtime-effects-registry.md`;
- image decode and upload artifacts for image source nodes:
  `22-image-bitmap-codec-pipeline.md`.

`MaterialKey` may include color-filter or runtime-effect facts only when they
affect material WGSL code or layout for a draw. It must not include filter DAG
intermediate identities, node execution order, destination-read bounds,
intermediate texture handles, cache residency, or full graph output pixels.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUFilterPlan` | Semantic and executable filter graph plan attached to a draw, layer, backdrop, or filter-specific task. |
| `GPUFilterGraphDescriptor` | Dumpable graph identity: graph kind, version, source role, node list, edges, coordinate spaces, and adapter provenance. |
| `GPUFilterNodeID` | Stable graph-local node identity independent of object addresses. |
| `GPUFilterNodeDescriptor` | Dumpable descriptor for one node: node kind, parameter preimage, child input roles, crop/tile facts, local matrix, and version. |
| `GPUFilterNodePlan` | Accepted or refused execution plan for one node. |
| `GPUFilterNodeRoute` | Route class for a node: `GPUNativeRender`, `GPUNativeCompute`, `GPUNativeCopy`, `CPUPreparedGPU`, `FoldIntoMaterial`, `CPUReferenceOnly`, or `RefuseDiagnostic`. |
| `GPUFilterInputPlan` | Input selection for one node input: source image, prior node output, backdrop/destination read, image source, shader source, empty transparent input, or refusal. |
| `GPUFilterSourcePlan` | Plan for the implicit source image produced by the draw/layer being filtered. |
| `GPUFilterBackdropPlan` | Plan for backdrop/previous destination content when a node observes parent target contents. |
| `GPUFilterBoundsPlan` | Forward and reverse bounds, kernel expansion, crop intersection, finite-bounds proof, and refusal reason. |
| `GPUFilterCropPlan` | Crop rect and outside-crop behavior, including tile mode and output bounds. |
| `GPUFilterTilePlan` | Tile mode semantics for sampling outside input bounds: decal, clamp, repeat, mirror when accepted, or refusal. |
| `GPUFilterSamplingPlan` | Sampling mode, filter quality, kernel support, sample radius, mip policy, and coordinate mapping. |
| `GPUFilterIntermediatePlan` | Descriptor for an intermediate texture/resource produced by a node and consumed by later nodes. |
| `GPUFilterRenderNodePlan` | Render-pass route for a node, including render step, pipeline key, target state, blend/color plan, and bindings. |
| `GPUFilterComputeNodePlan` | Compute-pass route for a node, including compute program key, workgroup plan, storage/sampled resources, and dispatch bounds. |
| `GPUFilterKernelPlan` | Convolution, blur, morphology, sampling kernel, separability, pass count, and precision facts. |
| `GPUFilterRuntimeEffectPlan` | Registered runtime-effect node descriptor, child binding plan, uniform layout, sample radius, and WGSL validation facts. |
| `GPUFilterColorPlan` | Filter-local color-filter chain plan and its interaction with `GPUColorPlan`. |
| `GPUFilterOrderingToken` | Token preventing unsafe reordering across input production, destination copy, intermediate write, and node consumption. |
| `GPUFilterCachePlan` | Graph, node, intermediate, pipeline, payload, and artifact cache policy. |
| `GPUFilterBudgetPolicy` | Node count, intermediate bytes, copied bytes, kernel radius, compute dispatch, pass count, and artifact budget limits. |
| `GPUFilterDiagnostic` | Structured diagnostic product for accepted or refused filter routes. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Filter Graph Model

`GPUFilterGraphDescriptor` records:

- graph ID and descriptor version;
- source role: draw source, layer source, backdrop filter, explicit image
  source, shader source, or refused;
- node descriptors in deterministic topological order;
- graph edges and input roles;
- graph-local coordinate spaces;
- local matrix facts;
- graph-level crop and clip facts;
- graph-level color and alpha facts;
- adapter provenance and compatibility notes;
- stable refusal reason when normalization cannot produce a graph.

Filter graph normalization must reject:

- cycles;
- ambiguous implicit source inputs;
- unsupported deserialized or platform-owned filter objects;
- object-address-based identity;
- unknown node kinds without a registered descriptor;
- unbounded dynamic child count beyond policy limits;
- graph-local transforms that cannot be dumped.

Active clips constrain filter outputs only after `GPUFilterBoundsPlan` has
preserved required source, backdrop, crop, tile, and sample-radius inputs.
`GPUClipPlan` owns the final scissor/stencil/mask execution route, while this
spec owns filter graph expansion and intermediate bounds.

`GPUFilterPlan` records graph-level execution:

- graph descriptor hash and version;
- source and destination target facts;
- node plans;
- intermediate resource requirements;
- destination/backdrop read plans;
- layer isolation requirement;
- pass split and ordering requirements;
- render/compute pipeline keys;
- payload and binding plans;
- cache and budget decisions;
- accepted warnings or refusal diagnostics.

## Inputs And Source Semantics

`GPUFilterInputPlan` supports:

| Input | Meaning |
|---|---|
| `ImplicitSourceImage` | The filtered draw or isolated layer source. |
| `PriorNodeOutput` | Texture/intermediate produced by a previous node. |
| `BackdropDestination` | Previous parent target contents, routed through `GPUDestinationReadPlan`. |
| `ExplicitImageSource` | Image filter leaf backed by `GPUImagePipelinePlan` or spec 18 texture ownership. |
| `ShaderSource` | Registered shader/material source that fills filter output. |
| `EmptyTransparentBlack` | Stable empty input behavior. |
| `RefuseDiagnostic` | No accepted input route. |

The implicit source image is not a magic bitmap. It is produced by an accepted
draw/layer isolation plan, with finite bounds, target descriptor, color/alpha
facts, usage flags, and generation.

Backdrop and destination inputs must use `20-destination-read-strategy.md`.
The filter pipeline must not read the active color attachment directly and must
not use CPU readback for product filter behavior.

Explicit image source leaves use `22-image-bitmap-codec-pipeline.md` for
encoded/CPU pixels and `18-texture-image-ownership.md` for GPU-resident or
imported textures.

## Bounds, Crop, Tile, And Coordinates

`GPUFilterBoundsPlan` records both directions:

- forward bounds: source/local bounds to output/target bounds;
- reverse bounds: needed input bounds for a requested output/clip region;
- conservative kernel expansion;
- displacement or transform expansion;
- crop application;
- tile-mode behavior outside input bounds;
- finite-bounds proof;
- rounded integer texture bounds;
- target intersection;
- refusal reason when bounds are unbounded, NaN, infinite, or too expensive.

Bounds must be conservative. If the planner cannot prove finite bounds, it may
choose a full-target route only when budgets accept the cost. Otherwise it must
refuse.

`GPUFilterCropPlan` records:

- crop rect in graph-local coordinates;
- transformed crop in layer/target coordinates;
- whether crop affects input, output, or both;
- outside-crop tile behavior;
- whether transparent black outside crop is required;
- clip interaction;
- invalid crop refusal.

`GPUFilterTilePlan` records tile modes:

- `Decal`: transparent black outside bounds;
- `Clamp`: clamp to edge;
- `Repeat`: repeat input coordinates when accepted;
- `Mirror`: mirror input coordinates only when the node and sampling route
  prove support;
- `Refuse`: unsupported tile behavior.

Nodes must not silently replace mirror/repeat with clamp or decal.

`GPUFilterSamplingPlan` records:

- source-to-output coordinate mapping;
- local matrix;
- filter quality and sampler descriptor;
- sample radius or kernel support;
- mip requirement;
- normalized vs texel coordinate convention;
- edge behavior;
- precision class.

Runtime-effect sample radius is a contract. If a registered runtime-effect
descriptor declares that child inputs may be sampled outside the current pixel,
the bounds plan must expand by that radius or refuse.

## Complete Target Node Matrix

This matrix describes the complete target surface owned or constrained by this
spec. It is not an implementation order.

| Node family | Target route | Required behavior |
|---|---|---|
| `Empty` | `GPUNativeRender` or elision | Produce transparent black with stable bounds and diagnostics. |
| `Image` | `GPUNativeRender` or `CPUPreparedGPU` by source | Use image source plans from spec 22/spec 18, source/destination rects, sampling, and crop behavior. |
| `Shader` | `GPUNativeRender` | Fill output from registered material/shader snippets with explicit local coordinates and crop. |
| `ColorFilter` | `FoldIntoMaterial` or `GPUNativeRender` | Fold into material when equivalent; otherwise execute as a filter node with `GPUColorPlan` evidence. |
| `Compose` | Graph topology | Evaluate inner before outer with preserved bounds and intermediate ownership. |
| `Crop` | Graph topology or render node | Apply crop and outside-crop tile mode exactly. |
| `Offset` | `GPUNativeCopy` or `GPUNativeRender` | Shift coordinates/bounds without changing pixels; allocate intermediate only when required by consumers. |
| `MatrixTransform` | `GPUNativeRender` | Resample input through accepted transform, sampling, and finite bounds. |
| `Blur` | `GPUNativeRender` or `GPUNativeCompute` | Validate sigma, separable kernel/pass plan, tile mode, expanded bounds, intermediate count, and precision. |
| `DropShadow` / `DropShadowOnly` | DAG expansion | Offset, colorize, blur, and merge/composite through explicit node plans and bounds. |
| `Blend` | `GPUNativeRender` or refusal | Use `GPUBlendPlan`; destination/backdrop inputs use `GPUDestinationReadPlan` when needed. |
| `Arithmetic` | Registered WGSL effect or fixed blend simplification | Use fixed blend when equivalent, otherwise registered arithmetic descriptor with premul policy. |
| `Merge` | `GPUNativeRender` DAG composite | Draw inputs in deterministic order with src-over semantics and intermediate validation. |
| `DisplacementMap` | `GPUNativeRender` or `GPUNativeCompute` | Validate channel selectors, scale, color/displacement input bounds, sample expansion, and texture bindings. |
| `MatrixConvolution` | `GPUNativeCompute` preferred, `GPUNativeRender` when proven | Validate kernel size, gain, bias, offset, convolve-alpha, tile mode, precision, and budget. |
| `Morphology` (`Dilate` / `Erode`) | `GPUNativeCompute` preferred, `GPUNativeRender` when proven | Validate radii, separability, channel behavior, tile/crop, and budget. |
| `Magnifier` | `GPUNativeRender` | Validate lens bounds, zoom, inset, sampling, finite input expansion, and crop. |
| `Tile` | `GPUNativeRender` | Validate source/destination rects, repeat behavior, coordinate mapping, and budget. |
| Lighting diffuse/specular | `DependencyGated` | Validate alpha-height semantics, light params, surface scale, precision, crop, and WGSL route before promotion. |
| `Picture` | `DependencyGated` | Requires a bounded nested recording/spec before support; otherwise stable refusal. |
| `RuntimeShader` | Registered `GPUFilterRuntimeEffectPlan` only | No arbitrary SkSL. Accept only registered descriptors with WGSL, child binding, sample radius, and CPU oracle behavior. |
| Unsupported or unknown node | `RefuseDiagnostic` | Stable refusal with node kind, descriptor hash, and graph provenance. |

## Node Routes

`GPUFilterNodeRoute` values:

- `FoldIntoMaterial`: the node is equivalent to a material color/filter snippet
  for the affected draw and does not require an intermediate image.
- `GPUNativeRender`: the node executes as a render pass into an intermediate
  or final filter target.
- `GPUNativeCompute`: the node executes as a compute pass with explicit
  storage/sampled resources.
- `GPUNativeCopy`: the node can be expressed as a copy or coordinate-preserving
  resource operation.
- `CPUPreparedGPU`: the CPU prepares a typed `FilterIntermediateArtifact`
  consumed by the GPU. This is accepted only for bounded, validated filter
  shapes whose artifact descriptor is registered.
- `CPUReferenceOnly`: CPU oracle behavior for tests and diffs.
- `RefuseDiagnostic`: no accepted route.

`CPUPreparedGPU` must not mean a full CPU-rendered filtered layer or scene. It
may only produce a typed artifact whose descriptor, bounds, inputs, color
facts, and GPU consumer are validated by this spec.

## Render And Compute Nodes

`GPUFilterRenderNodePlan` records:

- node descriptor hash;
- output intermediate descriptor;
- render step or full-screen/rect pass identity;
- `GPURenderPipelineKey`;
- `GPUMaterialAssemblyPlan` or fixed filter WGSL fragment identity;
- input sampled texture bindings;
- uniforms and payload slots;
- `GPUBlendPlan`, `GPUColorPlan`, and `GPUTargetState`;
- clear/load/store policy;
- ordering token and diagnostics.

`GPUFilterComputeNodePlan` records:

- node descriptor hash;
- output storage texture or buffer descriptor;
- `GPUComputeProgramKey`;
- `WGSLComputeModule` identity;
- `GPUComputePipelineKey`;
- workgroup size and dispatch bounds;
- storage/sampled texture bindings;
- uniform/storage layouts;
- read/write aliasing proof;
- barriers and ordering token;
- diagnostics.

Compute routes require storage texture/buffer capabilities. If the selected
`GPU` facade implementation cannot expose the required storage or barrier
facts, the node refuses.

## Intermediate Resources

`GPUFilterIntermediatePlan` records:

- producing node ID;
- consuming node IDs;
- bounds and coordinate mapping;
- texture descriptor and usage flags;
- view and sampler descriptors when sampled;
- storage usage when compute writes it;
- color format, alpha type, premul convention, and color-space facts;
- clear/load/store policy;
- lifetime class: node-local, graph-local, layer-local, frame-local, or
  cache-resident;
- generation and invalidation facts;
- budget class;
- diagnostic label.

Ordinary GPU filter intermediates are `GPUResourceProvider` resources, not
`CPUPreparedGPU` artifacts. `FilterIntermediateArtifact` is used only when CPU
preparation creates a validated bounded artifact. The artifact key must include
the graph/node descriptor, input hashes, bounds, crop/tile/sample facts,
color/alpha facts, descriptor version, and generator version.

Sampling an intermediate that is currently bound for writing is forbidden.
The planner must create a separate resource, split the pass, use ping-pong
intermediates, or refuse.

## Destination And Backdrop Reads

Backdrop filters and destination-dependent filter nodes use
`GPUDestinationReadPlan`.

The filter plan records:

- why previous target contents are observed;
- conservative backdrop read bounds;
- selected destination-read strategy;
- destination copy or existing intermediate descriptor;
- payload binding requirements;
- pass split and copy-before-sample ordering;
- budget decision;
- refusal reason.

Filter nodes must not read from the active attachment, framebuffer fetch,
input attachments, or CPU readback in product routes.

## Runtime Effects

Runtime filter effects are descriptor-based.

`GPUFilterRuntimeEffectPlan` records:

- registered descriptor ID, version, and registry snapshot generation;
- descriptor kind: color filter, shader filter, image filter, blender, or
  compute filter;
- WGSL fragment or compute module identity;
- Kotlin/CPU oracle descriptor;
- child input count and names;
- child sample radius or maximum sample radius;
- uniform layout and packing plan;
- texture/sampler/storage binding plan;
- `wgsl4k` validation and reflection facts;
- allowed color/alpha behavior;
- stable refusal reason.

Arbitrary Skia `SkRuntimeEffect`, SkSL source, or runtime shader builder input
is refused. A compatibility adapter may translate a known Skia effect into a
registered Kanvas descriptor only when the descriptor version, CPU behavior,
WGSL implementation, child binding contract, and validation fixtures are
accepted by `27-registered-runtime-effects-registry.md`.

## Color Filters

Color filters have two target placements:

- material placement: fold into `MaterialKey` and `GPUMaterialDictionary` when
  the filter affects only the current draw source color and does not require an
  intermediate image;
- filter-node placement: execute as a `GPUFilterNodePlan` when the color
  filter is inside an image-filter DAG, applies after an intermediate, or
  requires graph-level crop/bounds semantics.

`GPUFilterColorPlan` records:

- color filter descriptor and version;
- chain order;
- color-space and working-space facts;
- premul/unpremul requirements;
- whether the chain is folded, executed as a render node, or refused;
- diagnostics.

Color-filter support cannot be claimed by accepting a material snippet alone.
If the same behavior is used inside a filter DAG, the DAG route, bounds,
intermediate, and validation evidence must also exist.

## Cache And Budget Policy

`GPUFilterCachePlan` covers:

- graph descriptor cache;
- node plan cache;
- render and compute pipeline caches;
- WGSL module cache;
- payload cache;
- intermediate texture/resource cache;
- destination copy/intermediate reuse when allowed by spec 20;
- `FilterIntermediateArtifact` registry entries;
- runtime-effect descriptor cache.

Cache entries record:

- key preimage;
- graph and node descriptor versions;
- input source generation;
- bounds/crop/tile/sample facts;
- color/alpha facts;
- device generation when GPU-specific;
- memory cost;
- last use generation;
- eviction class;
- normative vs product-only status.

`GPUFilterBudgetPolicy` records:

- maximum graph node count;
- maximum graph depth;
- maximum intermediate count;
- maximum live intermediate bytes;
- maximum copied destination bytes;
- maximum filter output area;
- maximum blur sigma or kernel radius before refusal or alternative route;
- maximum convolution kernel size;
- maximum morphology radius;
- maximum compute dispatch size;
- maximum render/compute pass count;
- maximum runtime-effect child count and sample radius;
- retry, split, and quarantine policy.

Budget exhaustion refuses with stable diagnostics. The renderer must not drop
nodes, lower quality, shrink bounds, skip color conversion, or substitute a CPU
filtered texture without an explicit accepted route.

## Diagnostics

Diagnostics are structured and stable. They include:

- graph descriptor hash and label;
- node ID, kind, descriptor hash, and topological index;
- source/input roles;
- selected node route;
- bounds, crop, tile, and sample facts;
- intermediate descriptor hashes;
- destination-read plan IDs when present;
- pipeline/module/key hashes when present;
- cache and budget facts;
- accepted warnings;
- refusal reason.

Required refusal codes include:

- `unsupported.filter.graph_unregistered`
- `unsupported.filter.graph_cycle`
- `unsupported.filter.graph_node_limit`
- `unsupported.filter.node_unimplemented`
- `unsupported.filter.node_descriptor_invalid`
- `unsupported.filter.input_missing`
- `unsupported.filter.source_unavailable`
- `unsupported.filter.bounds_unbounded`
- `unsupported.filter.bounds_invalid`
- `unsupported.filter.crop_invalid`
- `unsupported.filter.tile_mode`
- `unsupported.filter.sample_radius_unbounded`
- `unsupported.filter.blur_sigma`
- `unsupported.filter.kernel_size`
- `unsupported.filter.morphology_radius`
- `unsupported.filter.displacement_scale`
- `unsupported.filter.lighting_unvalidated`
- `unsupported.filter.picture_unbounded`
- `unsupported.filter.runtime_effect_unregistered`
- `unsupported.filter.runtime_effect_WGSL_validation`
- `unsupported.filter.intermediate_unvalidated`
- `unsupported.filter.intermediate_budget_exceeded`
- `unsupported.filter.storage_texture_capability_missing`
- `unsupported.filter.read_write_aliasing`
- `unsupported.filter.destination_read_unaccepted`
- `unsupported.filter.CPU_rendered_texture_forbidden`

Accepted routes also produce diagnostics. A successful graph must be dumpable
enough to explain which nodes executed, which routes were selected, which
intermediates were allocated or reused, which destination reads occurred, and
which WGSL modules and bindings were used.

## Validation Gates

Filter pipeline promotion requires:

- canonical dumps for every core object in this spec;
- graph normalization tests for acyclic order, input roles, and stable node
  IDs;
- forward and reverse bounds fixtures;
- crop, tile, sample-radius, and local-matrix fixtures;
- filter source isolation tests with `GPULayerPlan`;
- destination/backdrop read fixtures with `GPUDestinationReadPlan`;
- render-node WGSL validation and binding reflection tests;
- compute-node WGSL validation, storage resource, dispatch, and barrier tests;
- intermediate resource ownership tests with spec 18;
- `FilterIntermediateArtifact` key and refusal tests when `CPUPreparedGPU` is
  accepted;
- material-folding tests proving folded color filters match DAG behavior only
  when semantics are equivalent;
- runtime-effect descriptor tests with CPU oracle, WGSL validation, uniform
  packing, child binding, and sample radius evidence;
- per-node fixture coverage for every promoted node family;
- negative tests for unbounded bounds, unsupported tile modes, active
  attachment sampling, read/write aliasing, and budget exhaustion;
- GPU evidence for promoted product routes;
- CPU reference evidence only as oracle behavior;
- PM-visible route counts, pass counts, intermediate bytes, copied bytes,
  filter refusals, and skipped/quarantined lanes.

Support cannot be claimed from a node appearing in the target matrix. A node is
supported only when route, bounds, resources, WGSL, diagnostics, and evidence
are all promoted.

## Relationship To Layer Plans

`08-layer-and-filter-plans.md` remains the parent semantic layer/filter spec.
This spec supplies the detailed filter pipeline used by attached layer filters,
draw filters, backdrop filters, and future filter-specific tasks.

Layer direct-to-parent elision is legal only when this spec proves the filter
is equivalent without source isolation. Otherwise the layer must isolate,
execute the filter graph, composite through the accepted layer/target plan, or
refuse.

## Relationship To Image And Text

Image filter `Image` leaves use `22-image-bitmap-codec-pipeline.md` for
encoded and CPU pixel sources, and `18-texture-image-ownership.md` for
GPU-resident/imported sources.

Text/glyph filters are not a shortcut around `21-text-glyph-pipeline.md`.
Filtering a text result requires a bounded source image or glyph/vector route
accepted by the text spec and this filter spec. The renderer must not render a
complete unsupported text run into a CPU texture just to apply a filter.

## Non-Goals

- Do not port Skia image-filter classes, flattenables, or deserialization into
  renderer core.
- Do not dynamically compile SkSL or arbitrary runtime shader strings.
- Do not hide filter execution inside `MaterialKey`.
- Do not silently drop unsupported filter nodes.
- Do not replace unsupported tile modes, crops, or bounds with approximate
  behavior without a recorded route.
- Do not sample the active color attachment.
- Do not use CPU readback or CPU-rendered full-layer textures as product
  filter fallback.
- Do not claim support for picture filters, lighting filters, runtime effects,
  compute filters, or large kernels without explicit route evidence.
