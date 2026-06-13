# DrawVertices And Mesh Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target `DrawVertices` and mesh-like geometry pipeline for the
GPU-first renderer.

`DrawVertices` is not a path/stroke problem. The source already provides
positions, optional indices, optional per-vertex colors, optional texture or
local-coordinate data, and a primitive topology. This spec owns how those facts
become GPU vertex/index buffers, render-step variants, material inputs,
primitive-color blending, bounds, sort keys, cache entries, diagnostics, and
stable refusals.

This is a target-complete spec. It is not an implementation slice. The first
rect/rrect plus solid/linear-gradient slice remains governed by
`14-first-slice-contract.md`.

The target is Graphite-inspired but Kanvas-owned:

- Graphite's useful pattern is a `VerticesRenderStep` variant selected by
  topology, color presence, and texture-coordinate presence;
- Kanvas keeps immutable descriptors and explicit buffer plans instead of
  mutable `SkVertices` inside the renderer core;
- Kanvas uses the WebGPU-like `GPU` facade and WGSL, not SkSL;
- `GPUNative` is preferred for vertex/index buffer drawing;
- `CPUPreparedGPU` is allowed only for typed buffer packing or canonicalization
  artifacts consumed by GPU work;
- unsupported layouts, topologies, colors, indices, blends, or sizes return
  `RefuseDiagnostic`, not silent CPU fallback.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module, naming, and Graphite equivalence
  policy;
- `01-normalized-draw-commands.md` for the `DrawVertices` command envelope;
- `02-gpu-recording-task-graph.md` for `GPUDrawAnalysis`, `GPUTaskList`, and
  `GPURenderStep` responsibilities;
- `03-material-key-wgsl.md` and
  `16-material-dictionary-and-snippet-registry.md` for material assembly and
  primitive-color WGSL requirements;
- `04-pipeline-key-cache-resources.md` for `PrecomputedGeometryArtifact`,
  `GPUResourceProvider`, and cache/resource policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for draw-family target maturity;
- `11-wgsl-layout-binding-abi.md` for vertex attributes, storage buffers,
  bind groups, and Kotlin packing;
- `12-blend-color-target-state.md` for final target blend and color behavior;
- `29-color-management-pipeline.md` for vertex color value specs,
  primitive-color conversions, precision, premul policy, and diagnostics;
- `30-coordinate-transform-bounds-policy.md` for vertex attribute coordinate
  spaces, transform classification, primitive bounds, precision, and rounded
  target bounds;
- `13-performance-telemetry-cache-gates.md` for mesh counters and gates;
- `15-draw-layer-planner-and-sort-policy.md` for sort windows and draw
  ordering;
- `17-payload-gathering-and-slots.md` for per-draw transform, depth, and
  buffer payload facts;
- `18-texture-image-ownership.md` and `22-image-bitmap-codec-pipeline.md` for
  texture/image sources sampled by vertex texcoords;
- `20-destination-read-strategy.md` for destination-dependent primitive or
  final blend behavior;
- `24-clip-stencil-mask-pipeline.md` for clipping interaction;
- `25-path-stroke-geometry-pipeline.md` for shared geometry diagnostic
  principles and `PrecomputedGeometryArtifact` boundaries.

## Graphite And Skia Evidence

Relevant local Graphite evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/`.

Useful source landmarks:

- `Device::drawVertices()` wraps `SkVertices` as `Geometry`, creates a
  primitive blender only when per-vertex colors exist, and routes through the
  normal `drawGeometry()` path.
- `Device::chooseRenderer()` selects `RendererProvider::vertices()` from
  vertex mode, color presence, and texcoord presence.
- `RendererProvider::vertices()` exposes eight variants: triangles or triangle
  strips, each with no extra attributes, colors, texcoords, or both. Graphite
  expects triangle fans to be converted before renderer selection.
- `render/VerticesRenderStep.*` defines position, optional normalized color,
  optional texcoord, and SSBO-index attributes; it transforms positions in the
  vertex shader and passes primitive color or local coordinates to the fragment
  stage.
- Graphite's render-step model keeps vertex layout, primitive topology, fixed
  state, vertex shader contribution, and primitive-color emission as executable
  facts.

Kanvas adopts these invariants:

- topology and attribute presence select a render-step/pipeline variant;
- vertex positions are transformed by GPU work when the route is native;
- per-vertex color is a primitive input to material evaluation, not final
  target blending by itself;
- texcoords override default local coordinates only when the material route
  accepts them;
- triangle fan conversion, interleaving, index expansion, or color repacking
  are typed `CPUPreparedGPU` preparation when needed;
- buffer layout, stride, attribute formats, and primitive topology are
  pipeline-key facts;
- concrete buffer handles, offsets, contents, and upload state are resource or
  payload facts.

Kanvas intentionally does not copy:

- Graphite class names, C++ ownership, `DrawWriter`, SSBO-index conventions, or
  render-step bit layout;
- Skia `SkVertices`, `SkBlender`, or `SkPaint` as core API;
- SkSL shader code;
- Graphite's exact color channel swizzles or Metal/Vulkan binding assumptions;
- any fallback that CPU-renders the vertices result into a texture.

## Scope Boundary

This spec covers `DrawVertices` and 2D renderer mesh-like draws.

Included:

- immutable vertex and mesh descriptors;
- triangles, triangle strips, and canonicalized triangle fans;
- optional indices;
- optional per-vertex colors;
- optional texcoords or local-coordinate attributes;
- primitive-color blending with paint/material output;
- material local-coordinate selection;
- vertex/index buffer packing and upload;
- render-step variants and pipeline keys;
- bounds, culling, sorting, clip, layer, and destination-read interaction;
- diagnostics, telemetry, and validation.

Excluded from this spec:

- a general 3D scene engine;
- cameras, lights, skeletal animation, skinning, morph targets, PBR materials,
  or depth-buffered 3D semantics;
- arbitrary user-defined vertex shader source;
- compute-generated mesh expansion unless a later spec accepts a
  descriptor-based route;
- CPU-rendered mesh textures;
- broad vector mesh formats or asset import pipelines.

## Ownership Boundary

The legacy adapter owns:

- converting Skia-like `drawVertices` inputs into immutable Kanvas descriptors;
- preserving `DrawVertices` source mode, indices, colors, texcoords, blender,
  paint, transform, and source provenance;
- canonicalizing or refusing mutable/nondeterministic source arrays;
- converting unsupported source modes such as triangle fan into an accepted
  descriptor or stable refusal;
- rejecting non-finite positions or unbounded input before the core when no
  safe descriptor can be built.

This spec owns:

- `GPUVerticesDescriptor`;
- `GPUVertexMode`;
- `GPUVertexAttributeDescriptor`;
- `GPUVertexLayoutPlan`;
- `GPUVertexPositionPlan`;
- `GPUVertexColorPlan`;
- `GPUVertexTexCoordPlan`;
- `GPUPrimitiveColorPlan`;
- `GPUPrimitiveBlendPlan`;
- `GPUIndexBufferPlan`;
- `GPUVertexBufferPlan`;
- `GPUVerticesRoute`;
- `GPUVerticesRenderStepPlan`;
- `GPUVerticesBoundsPlan`;
- `GPUMeshDescriptor`;
- `GPUMeshAttributeDescriptor`;
- `GPUMeshBufferPlan`;
- `GPUMeshRoute`;
- `GPUVerticesCachePlan`;
- `GPUVerticesBudgetPolicy`;
- `GPUVerticesDiagnostic`.

Owned by other specs:

- command envelope, captured transform, clip, layer, material, bounds, and
  ordering facts: `01-normalized-draw-commands.md`;
- material snippets, primitive-color input requirements, and WGSL assembly:
  `03-material-key-wgsl.md` and
  `16-material-dictionary-and-snippet-registry.md`;
- texture/image ownership for sampled material children:
  `18-texture-image-ownership.md` and `22-image-bitmap-codec-pipeline.md`;
- final target blend, color, premul, and color-space behavior:
  `12-blend-color-target-state.md`;
- destination reads: `20-destination-read-strategy.md`;
- clipping: `24-clip-stencil-mask-pipeline.md`;
- generic prepared geometry artifact rules:
  `04-pipeline-key-cache-resources.md` and
  `25-path-stroke-geometry-pipeline.md`.

`MaterialKey` may include the fact that primitive color is present, that
texcoords provide material-local coordinates, and that a registered primitive
blend mode is required. It must not include concrete vertex values, index
contents, buffer handles, upload generations, or draw offsets.

`GPURenderPipelineKey` may include vertex layout, attribute formats, topology,
index format, render-step identity, primitive-color ABI, texcoord ABI,
target/blend state, sample count, and WGSL layout facts. It must not include
per-draw vertex contents.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUVerticesDescriptor` | Immutable `DrawVertices` facts: mode, counts, attributes, indices, bounds, colors, texcoords, blender, and provenance. |
| `GPUVertexMode` | Accepted topology: `Triangles`, `TriangleStrip`, `TriangleFanCanonicalized`, or refused. |
| `GPUVertexAttributeDescriptor` | Source attribute presence, type, normalized/premul facts, stride, and canonical key facts. |
| `GPUVertexLayoutPlan` | Executable vertex layout: attributes, formats, strides, offsets, step mode, and WGSL locations. |
| `GPUVertexPositionPlan` | Position source, transform class, finite proof, precision, and local/device coordinate behavior. |
| `GPUVertexColorPlan` | Per-vertex color format, alpha/premul policy, color-space facts, and interpolation behavior. |
| `GPUVertexTexCoordPlan` | Texcoord/local-coordinate source, transform relation, interpolation behavior, and material coordinate policy. |
| `GPUPrimitiveColorPlan` | Primitive color input made available to material WGSL when vertex colors exist. |
| `GPUPrimitiveBlendPlan` | Descriptor-based blend between material/paint output and primitive color before final target blend. |
| `GPUIndexBufferPlan` | Index presence, source type, canonicalization, index format, count, bounds, and validation. |
| `GPUVertexBufferPlan` | Buffer layout, upload/packing, owner scope, byte count, usage flags, and GPU consumer. |
| `GPUVerticesRoute` | Selected route: native buffers, prepared buffers, compute-generated future route, or refusal. |
| `GPUVerticesRenderStepPlan` | Render-step variant and draw call facts for vertices/mesh draws. |
| `GPUVerticesBoundsPlan` | Source, transformed, clipped, and conservative bounds used for culling and ordering. |
| `GPUMeshDescriptor` | Future generalized 2D mesh descriptor with named attributes and bounded topology. |
| `GPUMeshAttributeDescriptor` | Future mesh attribute descriptor for position, color, texcoord, custom registered inputs, or refusal. |
| `GPUMeshBufferPlan` | Future mesh-owned buffer plan with explicit lifetime and attribute binding facts. |
| `GPUMeshRoute` | Future mesh route using the same route taxonomy and diagnostics as `DrawVertices`. |
| `GPUVerticesCachePlan` | Cache policy for descriptor analysis, packed buffers, and uploaded buffer resources. |
| `GPUVerticesBudgetPolicy` | Hard and policy limits for counts, bytes, attributes, index expansion, and upload cost. |
| `GPUVerticesDiagnostic` | Structured accepted/refused diagnostic product. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Descriptor Semantics

`GPUVerticesDescriptor` records:

- descriptor version;
- vertex mode;
- vertex count;
- index count and index presence;
- position attribute descriptor;
- optional color attribute descriptor;
- optional texcoord attribute descriptor;
- primitive blender descriptor when colors are present;
- material-local coordinate policy;
- source bounds and finite proof;
- transform facts;
- source volatility and mutability facts;
- source provenance for diagnostics;
- stable descriptor key or refusal reason.

The descriptor must not contain:

- `SkVertices`, `SkBlender`, `SkPaint`, or mutable source arrays;
- raw pointers, object addresses, or nondeterministic identity;
- raw `GPU` buffer handles;
- decoded image pixels or texture handles;
- already-packed transient upload buffers as durable identity.

If source vertices are mutable or cannot be copied/canonicalized within budget,
normalization must either create a stable immutable handle with content hash and
lifetime rules or refuse.

## Topology And Indices

Accepted topology target:

| Source mode | Target behavior |
|---|---|
| `Triangles` | Native draw or indexed draw when counts and indices are valid. |
| `TriangleStrip` | Native strip draw when the render step and facade path accept it, otherwise prepared triangle expansion. |
| `TriangleFan` | Canonicalize to triangles through `CPUPreparedGPU(PrecomputedGeometryArtifact)` or refuse until accepted. |
| Other/future topology | Refuse until the descriptor, render step, and validation are specified. |

`GPUIndexBufferPlan` records:

- index source presence;
- source index type and count;
- target index format: `uint16` or `uint32` when accepted;
- min/max referenced vertex;
- out-of-range proof;
- degenerate triangle handling;
- primitive restart policy, defaulting to unsupported unless accepted;
- expansion policy when a source mode cannot be drawn directly;
- byte size, alignment, and upload requirements.

Rules:

- Out-of-range indices refuse.
- Index overflow refuses unless a supported wider index format is available.
- Triangle fan conversion is content-changing preparation and must be visible
  in artifact keys and diagnostics.
- Degenerate triangles may be kept, dropped, or refused by route, but the
  policy must be deterministic and recorded.
- Wireframe, line, point, adjacency, patches, or primitive restart are not
  accepted topology in this target without a future spec update.

## Vertex Attributes

### Positions

`GPUVertexPositionPlan` records:

- source format, initially f32 2D positions;
- finite proof and coordinate thresholds;
- local-to-device transform facts;
- device bounds and conservative transformed bounds;
- precision domain and WGSL type;
- whether positions are uploaded as provided, repacked, or generated.

Positions are transformed by GPU work for `GPUNative` routes. CPU
transformation is allowed only as a typed preparation step when the route
explicitly accepts it and material local-coordinate semantics are preserved.

### Colors

`GPUVertexColorPlan` records:

- color presence;
- source format;
- alpha type and premul/unpremul policy;
- color-space role;
- channel order and normalization;
- interpolation behavior;
- default color when the render step requires a color slot but source colors
  are absent;
- primitive-color WGSL ABI.

Per-vertex colors are not final framebuffer blend state. They feed
`GPUPrimitiveColorPlan`, then the selected `GPUPrimitiveBlendPlan` combines
them with material or paint output before final `GPUBlendPlan` writes the
target.

If color conversion, premul behavior, or primitive blending is unvalidated, the
route refuses instead of silently treating colors as raw bytes.

### Texcoords And Local Coordinates

`GPUVertexTexCoordPlan` records:

- texcoord presence;
- source format, initially f32 2D coordinates;
- interpolation behavior;
- relation to material local coordinates;
- default local-coordinate policy when texcoords are absent;
- transform facts when texcoords are pre-transformed or source-local;
- WGSL varying ABI.

Rules:

- When texcoords exist and the material accepts vertex-local coordinates, they
  become the material coordinate input.
- When texcoords are absent, positions are the default material-local
  coordinate input unless a material route requires explicit texcoords.
- Materials that require local-coordinate derivatives, perspective correction,
  or unsupported coordinate transforms must either accept the vertex route
  explicitly or refuse.
- Texcoords are geometry payload facts. They do not identify texture resources.

## Primitive Color And Material Interaction

`GPUPrimitiveBlendPlan` is distinct from final target blending.

It records:

- primitive color presence;
- registered primitive blender ID or accepted blend mode;
- material output input;
- primitive color input;
- alpha and premul domains;
- color-space role;
- WGSL snippet requirement;
- destination-read requirement if the primitive blend itself needs destination
  data;
- refusal reason for unregistered or unsupported primitive blenders.

Rules:

- If no vertex colors exist, no primitive blender is active.
- If vertex colors exist and no explicit primitive blender is provided, the
  default target is `SrcOver`-style primitive blending when registered and
  validated.
- Arbitrary Skia/SkSL blender source is not accepted.
- Primitive blending happens before final target `GPUBlendPlan`.
- If primitive blending requires a destination read, route selection must use
  `20-destination-read-strategy.md`; unsupported destination reads refuse.

`MaterialKey` may represent the primitive-color requirement and registered
primitive blender, but not concrete color values.

## Strategy Taxonomy

### `GPUNativeBuffers`

`GPUNativeBuffers` is the preferred route. It binds accepted vertex and optional
index buffers and issues a render pass draw call.

It requires:

- valid `GPUVerticesDescriptor`;
- accepted topology and index format;
- accepted vertex layout and WGSL ABI;
- accepted material and primitive-color plan;
- accepted clip, target, blend, and color plans;
- resource provider support for vertex and index buffers;
- upload-before-draw ordering.

### `CPUPreparedGPU(PrecomputedGeometryArtifact)`

`CPUPreparedGPU` is allowed when CPU work produces a typed prepared geometry
artifact consumed by GPU work.

Allowed preparation:

- interleaving separate position/color/texcoord arrays;
- packing colors into the accepted normalized format;
- expanding triangle fans into triangles;
- expanding triangle strips into triangles when strip draw is not accepted;
- widening or narrowing indices when validated;
- removing or preserving degenerates by deterministic policy;
- computing immutable bounds;
- creating canonical buffer payloads for cache reuse.

Not allowed:

- shading the mesh on CPU;
- blending the mesh on CPU;
- rasterizing the mesh into a texture;
- hiding unsupported material, clip, or blend behavior behind prepared pixels.

### `GPUComputeMeshPreparation`

`GPUComputeMeshPreparation` is a future route for descriptor-based GPU
generation or conversion of mesh buffers before rendering.

It requires:

- registered compute program descriptor;
- storage-buffer usage and synchronization support;
- complete WGSL compute validation and reflection;
- explicit generated buffer bounds and budgets;
- write-before-draw ordering;
- GPU evidence before support claims.

It is not required by the first implementation slices.

### `RefuseDiagnostic`

`RefuseDiagnostic` is returned when no route can preserve semantics within
capability and budget limits. The command remains visible in support matrices
and PM evidence.

## Route Selection

`GPUVerticesRoute` is produced during `GPUDrawAnalysis` before pass
construction.

Route selection follows:

1. Validate command, descriptor, transform, material, primitive blender, clip,
   layer, target, and capability facts.
2. Cull empty or fully degenerate input only when no layer/filter/destination
   read can observe it.
3. Try `GPUNativeBuffers` for accepted topology, layout, attributes, indices,
   material, primitive blend, clip, and target state.
4. Try `CPUPreparedGPU(PrecomputedGeometryArtifact)` for accepted packing,
   canonicalization, fan/strip expansion, color repacking, or index
   conversion.
5. Try `GPUComputeMeshPreparation` only when a future registered descriptor is
   accepted.
6. Return `RefuseDiagnostic`.

The route must refuse when:

- source identity is nondeterministic;
- positions are non-finite or exceed coordinate thresholds;
- topology is unsupported and no accepted canonicalization exists;
- indices are out of range or exceed accepted format limits;
- vertex, index, attribute, or byte budgets are exceeded;
- color format, alpha handling, or color-space behavior is unvalidated;
- texcoord/local-coordinate behavior is unsupported by the selected material;
- primitive blender is unregistered or requires an unsupported route;
- vertex layout or WGSL ABI validation is missing;
- required vertex/index/storage/copy/synchronization capabilities are
  unavailable;
- clip, layer, destination-read, or target boundaries make required
  preparation or ordering illegal;
- CPU preparation would produce shaded pixels or a rendered texture.

## Bounds, Culling, And Ordering

`GPUVerticesBoundsPlan` records:

- source local bounds from positions;
- transformed device bounds;
- clipped bounds from `GPUClipPlan`;
- conservative bounds after topology canonicalization;
- empty, degenerate, finite, unbounded, or invalid classification;
- culling proof;
- sort and overlap facts.

Rules:

- Bounds are conservative.
- Bounds must be recomputed or invalidated when positions, indices, topology,
  transform, or canonicalization policy changes.
- Degenerate-only input may cull only when final effects cannot be observed by
  layer, filter, destination-read, or restore semantics.
- `UnknownOverlap` remains incompatible with movement across destination reads,
  clip producers, stencil producers, buffer uploads, or layer boundaries.
- Vertex draws that use destination-dependent primitive or final blending must
  preserve paint order unless a later proof allows movement.

## Render-Step And Pipeline Planning

`GPUVerticesRenderStepPlan` records:

- render-step ID and version;
- topology: triangles, triangle strip, or canonicalized triangles;
- draw call kind: direct, indexed, prepared direct, prepared indexed, or future
  indirect when accepted;
- vertex layout hash;
- index format and index-buffer presence;
- primitive-color ABI;
- texcoord/local-coordinate ABI;
- transform/depth payload requirement;
- material coordinate source;
- WGSL module and reflection facts;
- target, depth/stencil, sample count, and color-write state;
- resource requirements;
- upload-before-draw dependencies;
- diagnostic label.

Rules:

- Topology, vertex layout, index format, primitive-color ABI, texcoord ABI, and
  render-step identity contribute to `GPURenderPipelineKey`.
- Vertex contents, concrete buffer handles, draw offsets, and upload
  generations are payload/resource facts.
- A route can batch only when layout, pipeline key, compatible material,
  compatible clip/target state, and ordering allow it.
- Future indirect draws require their own descriptor and validation before
  entering the target.

## Buffer Ownership And Upload

`GPUVertexBufferPlan` and `GPUIndexBufferPlan` record:

- source descriptor hash;
- packed buffer layout;
- byte count and alignment;
- usage flags: vertex, index, copy destination, storage when accepted;
- owner scope: command-local, frame-local, recording-local, or cache-resident
  when accepted;
- upload staging scope;
- upload-before-draw dependency;
- device generation and invalidation facts;
- concrete resource ref only after `GPUResourceProvider` materialization.

`PrecomputedGeometryArtifact` keys for vertices include:

- descriptor version;
- topology and canonicalization policy;
- vertex count and index count;
- position, color, and texcoord content hashes;
- primitive blender descriptor when it affects packed payload needs;
- transform only when preparation depends on transform;
- color packing and premul policy;
- index expansion or conversion policy;
- budget policy ID and version;
- relevant capability facts.

Resource rules:

- Raw source arrays are not long-lived GPU resources.
- Uploaded vertex/index buffers are provider-owned resources.
- Cache hits are performance evidence only. A miss must upload, rebuild within
  budget, choose another accepted route, or refuse.
- Device loss or generation change invalidates concrete buffers and requires
  rebuild, reupload, or refusal.

## Mesh-Like Future Target

`GPUMeshDescriptor` generalizes `GPUVerticesDescriptor` for future Kanvas-owned
2D mesh commands.

It may add:

- named attribute sets;
- multiple vertex streams;
- optional instance attributes;
- registered custom attributes consumed by registered material descriptors;
- static or dynamic mesh ownership classes;
- future indirect draw descriptors.

It must still obey:

- descriptor-based registration;
- WGSL reflection-backed ABI;
- explicit topology and bounds;
- explicit buffer ownership and generation;
- no arbitrary user shader source;
- no hidden CPU-rendered fallback;
- no broad 3D engine semantics.

`DrawVertices` support must not wait for the full mesh target. The mesh objects
exist so the first vertices design does not paint itself into a naming corner.

## Interaction With Clip, Layers, Images, And Destination Reads

Rules:

- Clip selection is owned by `24-clip-stencil-mask-pipeline.md`; vertices route
  selection consumes the resulting `GPUClipPlan`.
- Simple scissor/analytic clips can combine with native vertex draws when
  `GPUClipPlan` accepts them.
- Complex clips may force stencil, coverage mask, layer isolation, or refusal.
- Material image sampling uses texture/image ownership specs; texcoords only
  provide coordinates.
- Layer allocation and restore/composite semantics are owned by
  `08-layer-and-filter-plans.md`; vertices must preserve layer ordering and
  bounds.
- Destination-dependent primitive blending or final blending uses
  `20-destination-read-strategy.md` and must not assume framebuffer fetch.
- Buffer uploads, destination copies, clip mask uploads, and layer target
  changes are separate barriers.

## Cache And Budget Policy

`GPUVerticesCachePlan` may cache:

- immutable descriptor hashes;
- route analysis for equivalent descriptor/material/clip/capability facts;
- packed `PrecomputedGeometryArtifact` buffers;
- uploaded buffer resources;
- vertex layout and WGSL ABI lowering products;
- primitive-blend lowering products.

`GPUVerticesBudgetPolicy` separates hard capability limits from configurable
policy limits.

Hard limits include:

- maximum vertex buffer size;
- maximum index buffer size;
- supported index formats;
- maximum vertex attribute count;
- supported vertex formats and strides;
- maximum vertex buffer slots;
- supported topology and draw call kinds;
- required copy, vertex, index, and storage usages;
- WGSL feature and layout support.

Policy limits include:

- maximum vertex count per command;
- maximum index count per command;
- maximum source descriptor bytes;
- maximum packed bytes per command, recording, and frame;
- maximum triangle fan or strip expansion ratio;
- maximum upload bytes per frame;
- maximum cache-resident mesh bytes;
- maximum primitive-blender complexity;
- maximum mesh preparation time or reporting threshold.

Embedding code may increase accepted policy budgets before recording. A command
cannot raise its own budget during route selection. Diagnostics must state
whether refusal came from hard capability limits or configurable policy limits.

## Diagnostics

Every accepted, culled, or refused vertices route emits
`GPUVerticesDiagnostic`.

Fields:

- command ID and draw family;
- vertices descriptor hash and version;
- selected route or refusal;
- topology and canonicalization policy;
- vertex count and index count;
- attribute presence: position, color, texcoord, future custom attributes;
- vertex layout hash, formats, strides, offsets, and WGSL locations;
- index format, min/max index, and out-of-range proof;
- color alpha/premul/color-space facts;
- texcoord/local-coordinate policy;
- primitive blender descriptor and route;
- material primitive-color requirement;
- transform classification and finite proof;
- bounds: source, transformed, clipped, and conservative;
- artifact type and artifact key hash when prepared;
- buffer plan hashes, byte counts, owner scopes, usage flags, and upload
  facts;
- render-step plan ID and pipeline-key contribution summary;
- clip/layer/destination-read interaction facts;
- budget policy ID, budget used, budget remaining, and hard/policy flag;
- culling, barrier, batch, or sort-window decision;
- WGSL validation/reflection facts for promoted routes;
- stable reason code.

Stable reason-code examples:

- `unsupported.vertices.descriptor_invalid`
- `unsupported.vertices.key_nondeterministic`
- `unsupported.vertices.positions_nonfinite`
- `unsupported.vertices.coordinate_too_large`
- `unsupported.vertices.topology`
- `unsupported.vertices.triangle_fan_unprepared`
- `unsupported.vertices.vertex_count_budget`
- `unsupported.vertices.index_count_budget`
- `unsupported.vertices.index_out_of_range`
- `unsupported.vertices.index_format`
- `unsupported.vertices.degenerate_policy`
- `unsupported.vertices.attribute_layout`
- `unsupported.vertices.attribute_format`
- `unsupported.vertices.color_format`
- `unsupported.vertices.color_conversion_unvalidated`
- `unsupported.vertices.texcoord_required`
- `unsupported.vertices.local_coords_unproven`
- `unsupported.vertices.primitive_blender_unregistered`
- `unsupported.vertices.primitive_blend_destination_read`
- `unsupported.vertices.buffer_budget_exceeded`
- `unsupported.vertices.upload_unavailable`
- `unsupported.vertices.WGSL_ABI_unvalidated`
- `unsupported.vertices.CPU_rendered_texture_forbidden`

## Telemetry

`GPUTelemetryLedger` records vertices counters when `DrawVertices` or mesh-like
routes are touched:

- vertices route counts by route;
- topology counts;
- attribute variant counts: position-only, color, texcoord, color+texcoord;
- native direct and indexed draw counts;
- prepared direct and indexed draw counts;
- triangle fan conversion counts;
- strip expansion counts;
- vertex and index count histograms;
- uploaded vertex/index bytes;
- prepared artifact hit, miss, create, upload, and eviction counts;
- primitive blender counts and refusals;
- layout/pipeline cache hit and miss counts;
- budget pressure counts;
- hard capability refusal counts;
- WGSL ABI validation failure counts.

Performance reports must distinguish:

- correctness support for a vertices route;
- CPU packing/canonicalization cost;
- upload cost;
- pipeline/layout cache efficiency;
- primitive blender cost;
- memory pressure;
- refused unsupported input.

## Validation Requirements

Promoted `DrawVertices` behavior requires:

- canonical dumps for `GPUVerticesDescriptor`, `GPUVertexAttributeDescriptor`,
  `GPUVertexLayoutPlan`, `GPUVertexPositionPlan`, `GPUVertexColorPlan`,
  `GPUVertexTexCoordPlan`, `GPUPrimitiveColorPlan`,
  `GPUPrimitiveBlendPlan`, `GPUIndexBufferPlan`, `GPUVertexBufferPlan`,
  `GPUVerticesRoute`, `GPUVerticesRenderStepPlan`,
  `GPUVerticesBoundsPlan`, `GPUVerticesBudgetPolicy`, and
  `GPUVerticesDiagnostic`;
- descriptor determinism tests for equivalent immutable vertices;
- negative tests for mutable or nondeterministic source arrays;
- topology tests for triangles, triangle strips, triangle fan canonicalization,
  and unsupported topology refusal;
- index validation tests for no indices, valid indices, out-of-range indices,
  `uint16`, `uint32` when accepted, and overflow refusal;
- attribute layout tests for position-only, color, texcoord, and
  color+texcoord variants;
- color tests for premul, alpha, channel order, interpolation, conversion
  refusal, and primitive-color material input;
- primitive blender tests for default behavior, registered blend modes,
  unsupported blender refusal, and destination-read refusal;
- texcoord/local-coordinate tests for texcoords present, absent, material
  requirements, and unsupported coordinate behavior;
- buffer upload and upload-before-draw tests;
- prepared artifact key and invalidation tests;
- pipeline-key tests proving layout/topology/ABI enter keys while values do
  not;
- payload tests proving concrete buffer offsets, handles, and upload
  generations stay out of durable material keys;
- clip interaction tests for scissor, analytic, complex clip refusal, and mask
  routes when promoted;
- WGSL validation and reflection evidence for every promoted render-step
  variant;
- CPU oracle or explicit refusal evidence;
- GPU evidence before product support claims;
- PM evidence showing route, topology, layout, buffers, primitive blend,
  budgets, diffs, and refusals.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice may validate refusal and
boundary behavior only.

It may validate:

- `DrawVertices` command normalization refuses with stable diagnostics;
- vertex facts stay out of `MaterialKey` when refused;
- route diagnostics distinguish unsupported topology, layout, primitive
  blender, and budget reasons;
- `GPURenderPipelineKey` examples reserve space for vertex layout axes without
  promoting vertices rendering.

It must not claim support for:

- `DrawVertices`;
- per-vertex colors;
- primitive blenders;
- texcoord-driven material coordinates;
- vertex/index buffer uploads as product rendering;
- `PrecomputedGeometryArtifact` for vertices;
- `GPUMeshDescriptor`.

Those routes require later evidence against this spec.

## Non-Goals

- Do not port Graphite `VerticesRenderStep`, `DrawWriter`,
  `RendererProvider`, or `SkVerticesPriv`.
- Do not expose `SkVertices`, `SkBlender`, or mutable source arrays inside
  `:gpu-renderer`.
- Do not introduce SkSL or arbitrary user shader source.
- Do not turn the renderer into a 3D engine.
- Do not hide unsupported vertices behind CPU-rendered textures.
- Do not claim vertices support from descriptor acceptance alone.
- Do not treat CPU oracle, upload success, or cache hits as GPU product
  support without GPU evidence.
