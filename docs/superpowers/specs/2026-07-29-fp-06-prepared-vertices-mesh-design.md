# FP-06 Prepared Vertices And Mesh Route Design

Date: 2026-07-29  
Status: Proposed, architecture approved in conversation  
Roadmap item: `FP-06 — Prepared vertices and mesh route`

## Purpose

FP-06 migrates `DisplayOp.DrawVertices` and `DisplayOp.DrawMesh` from the
temporary legacy immediate family to the common prepared Surface frame used by
core primitives, images, and text.

The product result is a native WebGPU vertex/index-buffer route with exact
material, clip, blend, upload, preflight, ownership, and refusal semantics.
It does not add a general mesh intermediate representation and does not prepare
for a hypothetical 3D renderer.

## Source Of Truth

This design is subordinate to:

- `.upstream/target/high-performance-wgsl-pipeline-target.md`;
- `.upstream/target/skia-like-realtime-renderer-target.md`;
- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`;
- `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`;
- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`;
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`;
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`;
- `.upstream/specs/gpu-renderer/27-registered-runtime-effects-registry.md`;
- `docs/superpowers/specs/2026-07-13-graphite-dawn-inspired-webgpu-frame-plan-design.md`;
- the accepted FP-04 prepared-image route and the FP-05 prepared-text route.

Graphite and Dawn are bounded implementation references at Skia commit
`defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`. They are not architecture to
port.

## Confirmed Decisions

- Keep WebGPU as the only GPU backend.
- Keep the route 2D-specific.
- Do not create a general `MeshIR`.
- Do not add a `Recorder`, `RendererProvider`, `RenderStep` hierarchy,
  `ResourceProvider` hierarchy, or multi-backend abstraction.
- Reuse existing Kanvas vertices, buffer, blend, material, clip, resource,
  task-graph, and prepared-frame authorities.
- Support `DrawVertices`.
- Support `DrawMesh` with the same bounded position/color/UV/index geometry.
- Support `MeshProgram` only through a registered Kanvas runtime-effect
  descriptor with Kotlin/CPU behavior and parser-validated WGSL.
- Do not compile arbitrary SkSL or accept arbitrary vertex shader source.
- Keep every refusal terminal after prepared-route admission.
- Keep the product gate closed until native pixel, ownership, mixed-frame, and
  no-fallback evidence passes.

## Non-Goals

FP-06 does not add:

- 3D positions, cameras, projection, depth testing, face culling, lighting,
  PBR, skinning, or skeletal animation;
- arbitrary named vertex attributes;
- multiple independent vertex streams;
- user-supplied vertex shader source;
- compute-generated meshes;
- indirect draw APIs;
- persistent inter-frame buffer residency, which belongs to FP-09;
- final GM and performance claims, which belong to FP-11.

## Current State

The repository already contains contract and evidence foundations:

- `GPUVerticesDescriptor` and topology contracts;
- vertex/index buffer plans;
- route-decision and batching planners;
- `GPUVertexBufferUploader`, `VerticesExecutor`, and `GPUMeshBatcher`;
- WGSL vertices snippets;
- native WebGPU buffer and draw support in the renderer;
- the common `GPUPreparedMaterialProgramCompiler`;
- the registered runtime-effect authorities;
- the heterogeneous prepared Surface task graph, preflight, materializer,
  ownership ledger, and one-submit route.

These foundations do not yet form a product path. `DrawVertices` and
`DrawMesh` remain classified under `LegacyDisplayOpFamily.Vertices`, and the
legacy renderer still converts several untextured cases to paths or uses a
special textured route.

FP-06 consolidates the existing authorities. It does not keep the legacy
path conversion or textured-vertices dispatch as a second implementation.

## Graphite And Dawn Alignment

FP-06 adopts the useful invariants visible in Graphite:

- topology plus color/UV presence selects the render-step variant;
- triangle fans are canonicalized before native renderer selection;
- positions are transformed in the vertex shader;
- UVs replace position-derived local coordinates when present;
- per-vertex color is a primitive material input, not final target blending;
- vertex/index contents are payload facts, not material-key facts;
- buffer upload precedes the exact draw that consumes it;
- Dawn ultimately binds vertex/index buffers and issues `Draw` or
  `DrawIndexed`.

Kanvas intentionally omits Graphite's general renderer-provider hierarchy,
draw-writer conventions, C++ arena ownership, SSBO index convention, SkSL
generation, and backend-polymorphic resource providers.

The expected architectural alignment after FP-06 is high for the
`DrawVertices` hot path, but no performance-equivalence claim is permitted
before FP-11 records measured samples. `DrawMesh` with a custom
`MeshProgram` goes beyond the referenced Graphite implementation, whose
Graphite `Device::drawMesh` override is empty at the bounded commit.

## Architecture

The prepared route is:

```text
DisplayOp.DrawVertices / DisplayOp.DrawMesh
    -> GPUPreparedVerticesLowerer
    -> PreparedVerticesFrameInventory
    -> GPUPreparedMaterialProgramCompiler
    -> GPUDrawSemanticPayload.Vertices
    -> GPUPreparedSurfaceFrameTaskListBuilder
    -> GPUPreparedSurfaceNativePreflight
    -> GPUWgpu4kPreparedSurfaceFramePayloadMaterializer
    -> setVertexBuffer / setIndexBuffer
    -> draw / drawIndexed
```

No native handle exists before materialization. The lowerer, inventory,
payload, resource plan, task graph, and preflight are immutable or operate on
immutable snapshots.

### Surface Lowering

`GPUPreparedVerticesLowerer` consumes one `DisplayOp.DrawVertices` or
`DisplayOp.DrawMesh` and produces a typed ready/refused result.

It snapshots:

- source operation kind;
- vertex mode;
- positions;
- optional colors;
- optional texture coordinates;
- optional indices;
- transform;
- clip;
- paint and paint alpha;
- final target blend;
- `DrawMesh.blendMode`;
- mesh bounds;
- mesh program identity, uniforms, and children;
- operation index and source provenance.

It does not:

- allocate native buffers;
- compile a pipeline;
- select a cache entry;
- create texture or sampler handles;
- silently repair malformed public input;
- fall back to a path or CPU-rendered texture.

The lowerer uses the existing paint/material mapper and registered
runtime-effect resolver. It must not introduce a second material or
runtime-effect registry.

### Immutable Geometry Artifact

`GPUPreparedVerticesUploadArtifact` is the exact handle-free vertex/index
payload retained by the frame.

Its identity covers:

- canonical topology;
- exact packed vertex bytes;
- exact packed index bytes when present;
- vertex layout and stride;
- attribute formats and offsets;
- vertex and index counts;
- index format;
- source content hash;
- canonicalization version.

Its identity excludes:

- command ID;
- transform;
- clip;
- paint/material;
- final blend;
- target dimensions;
- native handles;
- upload offsets;
- device generation.

Artifact bytes are immutable snapshots. Every mutable input list is copied
before hashing or validation. Equal exact artifacts may be deduplicated
within a frame. Hash equality alone never authorizes deduplication without
matching structural identity and byte counts.

### Topology

Accepted source modes:

| Source mode | Canonical native form |
|---|---|
| `TRIANGLES` | triangle list |
| `TRIANGLE_STRIP` | triangle strip when native constraints permit |
| `TRIANGLE_FAN` | deterministic triangle-list indices |

Triangle-fan canonicalization preserves source winding and index provenance.
When source indices exist, the fan expands through those indices rather than
the implicit vertex order.

Degenerate triangles remain deterministic. They may be culled only by a
documented proof that no coverage, destination read, or side effect is
observable.

### Attributes And Packing

FP-06 accepts one interleaved vertex stream with:

- required `position: float32x2`;
- optional color in one canonical premultiplied RGBA representation;
- optional `texCoord: float32x2`.

All enabled attributes have exactly `vertexCount` elements. Positions and UVs
must be finite. Colors must have finite normalized components after mapping.

The canonical interleaved order is:

```text
position, optional color, optional texCoord
```

Offsets, stride, shader locations, alignment, and WGSL input types are exact
pipeline-layout facts. Concrete vertex values are not pipeline-key facts.

The first product slice uses `uint16` indices when the maximum referenced
vertex fits. It uses `uint32` only when device capability, WGSL/pipeline
layout, byte budgets, and native smokes explicitly accept it.

### Coordinates And Transform

Source positions stay in local coordinates in the artifact. The draw transform
is uploaded separately and applied in the vertex shader. This matches the
Graphite invariant and avoids repacking identical geometry for transform-only
changes.

When UVs are absent, local material coordinates come from source positions.
When UVs are present, they are the material-local coordinates.

The accepted FP-06 transform set follows the exact prepared Surface transform
authority. Affine transforms are required initially. Perspective is accepted
only if that shared authority and native shader ABI already prove equivalent
behavior; otherwise it returns a stable refusal rather than applying an
affine approximation.

Clip and scissor facts are captured per command and delegated to the existing
prepared clip authority.

### Per-Vertex Color And Primitive Blend

When colors are absent, the prepared material output is the draw source.

When colors are present:

1. vertex color is converted to the declared canonical color/value domain;
2. it is interpolated across the primitive;
3. it becomes the primitive-color input;
4. the canonical primitive blend combines it with the paint/material result;
5. paint alpha is applied exactly once;
6. final target blend runs through the existing `GPUBlendPlan`.

`DrawVertices` uses the existing public semantics for primitive blending.
For `DrawMesh`, the final target blend is exactly
`drawMesh.blendMode ?: paint.blendMode`, matching the existing Surface blend
authority and the public no-program normalization. The lowerer snapshots that
resolved value and must not infer another blend from nullity later.

Primitive-color presence and primitive-blend identity are material/pipeline
facts. Concrete color values remain payload facts.

### Materials

The fragment material always comes from
`GPUPreparedMaterialProgramCompiler`.

For `DrawVertices`, the paint maps through the existing prepared material
authority.

For `DrawMesh` without a program, `Canvas.drawMesh` already lowers to the
vertices semantics. FP-06 preserves that public behavior and avoids two
equivalent internal paths.

For `DrawMesh` with a `MeshProgram`:

- the `RuntimeEffect` must resolve to a registered Kanvas descriptor;
- the descriptor must have Kotlin/CPU behavior;
- the descriptor must provide parser-valid WGSL;
- the effect is a fragment-material program; FP-06 always uses the canonical
  vertices vertex stage and does not accept a custom vertex entry point;
- the effect receives the canonical local coordinate and optional interpolated
  primitive-color inputs declared by the registered descriptor;
- the declared entry point, uniforms, children, resources, and ABI must match
  exactly;
- every uniform byte and child binding participates in the prepared program
  identity;
- shader, color-filter, and blender children use existing prepared child
  authorities;
- paint alpha and primitive blend are each applied once at their defined
  stage.

The following are terminal refusals:

- unregistered effect;
- registered effect without Kotlin/CPU behavior;
- registered effect without WGSL;
- parser-invalid WGSL;
- entry-point mismatch;
- missing, extra, or wrongly typed child;
- uniform layout or byte-count mismatch;
- unsupported sampled resource;
- unsupported declared value domain.

`GPUPreparedVerticesShaderAssembler` combines the topology/layout-specific
vertex stage with the already prepared fragment material. It is not allowed
to recompile, reinterpret, or fork material semantics.

### Prepared Frame Inventory

`PreparedVerticesFrameInventory` owns frame-local:

- exact command-to-artifact mapping;
- geometry artifact deduplication;
- material program references;
- upload ranges;
- canonical topology and layout identities;
- draw or indexed-draw parameters;
- bounds and culling facts;
- operation ordering.

Deduplication is allowed for exact geometry artifacts and exact prepared
material programs. Draw commands remain distinct and preserve command IDs,
transforms, clips, blend, target facts, and paint order.

The inventory is all-or-nothing. A refused command produces a typed frame
refusal before semantic payload construction. No partial inventory is exposed.

### Semantic Payload

`GPUDrawSemanticPayload.Vertices` retains:

- one immutable geometry artifact;
- canonical layout/topology plan;
- prepared material program;
- primitive-color and primitive-blend facts;
- transform bytes or exact transform payload reference;
- target and scissor bounds;
- clip plan identity;
- final blend plan identity;
- capability snapshot hash;
- frame provenance;
- canonical payload hash.

The canonical hash covers all facts that can change pixels or binding
compatibility. Native handles, transient offsets, and cache-hit state are
excluded.

### Task Graph And Batching

The heterogeneous Surface task graph adds typed vertex/index upload tasks and
vertices draw tasks.

Required dependencies:

```text
vertex upload -> exact consuming draw
index upload  -> exact consuming indexed draw
sampled material resource upload -> exact consuming draw
draw -> later ordered draw/composite that depends on it
```

Compatible adjacent draws may share:

- render pipeline;
- shader module;
- bind-group layout;
- sampler or immutable sampled resource;
- one packed vertex/index buffer allocation;
- one render-pass scope.

They may not be reordered across:

- clip or stencil barriers;
- destination reads;
- layer boundaries;
- filter/composite boundaries;
- upload dependencies;
- incompatible blend/target state;
- explicit command-order barriers.

Batching is an optimization over already correct commands. A failure to batch
must not change pixels. Buffer coalescing preserves exact aligned subranges and
draw offsets.

### Preflight

The common native preflight validates the complete heterogeneous frame before
the first target borrow, buffer creation, encoder creation, or queue write.

For every vertices payload it verifies:

- canonical hash integrity;
- supported topology;
- vertex/index counts;
- attribute counts and finiteness;
- stride, offsets, formats, shader locations, and alignment;
- index range and index format;
- transform and bounds;
- clip and blend plan identities;
- prepared material key and ABI;
- mesh-program registry generation;
- sampled-resource bindings and generations;
- per-draw and per-frame budgets;
- upload-before-use edges;
- draw offsets and packed buffer ranges;
- target format/sample-count compatibility.

Any refusal is stable, terminal, allocation-free, submit-free, and visible in
route diagnostics.

### Native Materialization And Ownership

After successful preflight, the materializer:

1. borrows the exact target;
2. creates or borrows frame-owned vertex/index buffers;
3. writes each exact aligned upload range once;
4. materializes sampled material resources;
5. obtains invariant pipeline/cache entries;
6. binds vertex/index buffers and material resources;
7. applies viewport/scissor and draw state;
8. calls `draw` or `drawIndexed`;
9. submits through the existing single prepared-frame submission;
10. transfers completion ownership through the common retained-close-owner
    protocol.

Vertex/index operands are frame-owned in FP-06. Persistent residency belongs
to FP-09. Pipeline/layout cache entries remain session-owned.

Every created native object has one owner and closes exactly once on success,
refusal after acquisition, exception, completion, runtime shutdown, or device
generation replacement.

## Budgets

FP-06 uses explicit hard limits for:

- vertices per draw;
- indices per draw;
- vertex bytes per draw;
- index bytes per draw;
- total vertex/index upload bytes per frame;
- draw count;
- unique artifacts;
- expanded triangle-fan indices;
- sampled resources per material;
- runtime-effect uniform bytes;
- child binding count;
- packed-buffer alignment padding.

Limits come from a named policy plus effective device capabilities. A larger
device limit does not silently enlarge a product policy. If product policy
allows adaptive limits, the accepted effective value and source are recorded
in diagnostics and the capability snapshot.

Budget failure occurs before native allocation and uses stable typed codes.

## Refusal Authority

FP-06 centralizes, rather than duplicates, canonical refusal codes.

Required families include:

- `unsupported.vertices.topology`;
- `unsupported.vertices.position_count`;
- `unsupported.vertices.attribute_count`;
- `unsupported.vertices.non_finite`;
- `unsupported.vertices.index_out_of_range`;
- `unsupported.vertices.index_format`;
- `unsupported.vertices.attribute_layout`;
- `unsupported.vertices.transform`;
- `unsupported.vertices.color_conversion_unvalidated`;
- `unsupported.vertices.primitive_blender_unregistered`;
- `unsupported.vertices.material`;
- `unsupported.vertices.budget`;
- `unsupported.mesh.bounds`;
- `unsupported.mesh.program_unregistered`;
- `unsupported.mesh.program_cpu_not_available`;
- `unsupported.mesh.program_wgsl_not_available`;
- `unsupported.mesh.program_wgsl_validation`;
- `unsupported.mesh.program_abi`;
- `unsupported.mesh.program_child`;
- `unsupported.mesh.program_resource`;
- `unsupported.mesh.budget`.

Where an existing canonical authority already names the same boundary, FP-06
must reuse that exact code. The implementation plan must not introduce
parallel string constants.

## Validation Strategy

### Pure Tests

Fixtures cover:

- triangles, strips, and fans;
- indexed and non-indexed geometry;
- colors only, UVs only, colors plus UVs, and neither;
- shared geometry with different transforms/materials;
- duplicate exact artifacts;
- partial-alpha vertex colors;
- affine transform and clip combinations;
- valid registered mesh programs with uniforms and each supported child type.

Refusal matrices cover every malformed count, non-finite value, invalid index,
layout, unsupported capability, exceeded budget, registry, WGSL, ABI, child,
resource, generation, and ownership boundary.

Each pure refusal test asserts:

- exact code and facts;
- no native seam invocation;
- no partial inventory;
- immutable input snapshots;
- no legacy continuation.

### Independent CPU Oracle

The vertices pixel oracle is independent from the GPU packer and WGSL source.
It uses barycentric interpolation for triangle lists and the canonical
expansion rules for strips and fans.

It validates:

- coverage;
- color interpolation;
- UV interpolation and texel sampling;
- primitive blend;
- paint alpha exactly once;
- final target blend;
- clip bounds;
- transform order.

Color comparison uses exact bytes when the contract permits and
`maxChannelDelta <= 1` only at an explicitly documented UNORM quantization
boundary.

### Native Tests

Native WebGPU smokes cover:

- one unindexed triangle;
- one indexed triangle;
- triangle strip;
- canonicalized triangle fan;
- vertex colors with partial alpha;
- image sampling through UVs;
- prepared gradient/material through position-derived local coordinates;
- registered `MeshProgram` uniforms;
- registered `MeshProgram` shader, color-filter, and blender children;
- clip and transform;
- fixed-function and destination-read blend classes that the common blend
  authority accepts;
- repeated exact geometry with distinct draw state;
- mixed Core/Image/Text/Vertices ordering;
- one prepared encoder/submit;
- completion-only and readback ownership.

Negative native-seam tests prove zero target borrow, zero buffer creation,
zero encoder, zero queue write, zero submit, and zero fallback for every
preflight refusal class.

### Performance Evidence

FP-06 records diagnostic counters for:

- source draw count;
- unique artifact count;
- vertex/index bytes;
- fan expansion;
- buffer allocation count;
- upload count and bytes;
- packed subrange count;
- pipeline/layout cache creation and reuse;
- compatible batch count;
- draw and indexed-draw count;
- encoder/submit/readback count.

These counters verify the intended shape of work. They are not a claim of
Graphite-level performance. FP-11 owns measured p50/p95 comparisons.

## Product Activation

The product gate stays closed during Tasks 1 through the final native evidence
task.

Activation is atomic:

1. all pure, module, mixed-frame, and native tests pass;
2. every supported combination has CPU/GPU evidence;
3. every unsupported public combination has a stable terminal refusal;
4. independent spec and technical reviews have no legitimate Critical or
   Important finding;
5. `DisplayOp.DrawVertices` and `DisplayOp.DrawMesh` route directly through
   the prepared builder;
6. the `Vertices` legacy family and
   `legacy.surface.prepared.family.vertices` are removed;
7. production searches prove no migrated vertices/mesh draw reaches
   `GPULegacyImmediatePathAdapter`, path conversion, CPU destination snapshot,
   or the special legacy textured-vertices route.

Rollback is the atomic product gate before release. There is no per-command
fallback after admission.

## Relationship To Later FPs

- FP-07 may place vertices and meshes inside pictures, layers, filters, and
  composites without adding a second geometry path.
- FP-08 removes the final superseded immediate and CPU continuation code after
  all families migrate.
- FP-09 may retain compatible vertex/index allocations or pools across frames;
  FP-06 remains frame-owned.
- FP-10 fixes bounded native gaps found by validation; it does not own deferred
  broad mesh-program support because registered `MeshProgram` support is part
  of FP-06.
- FP-11 regenerates GM evidence and measures the final prepared candidate.

## Acceptance Criteria

FP-06 is complete only when:

- `DrawVertices` and `DrawMesh` no longer produce
  `legacy.surface.prepared.family.vertices`;
- triangle, strip, and canonicalized fan routes are native;
- supported vertex/index, color, UV, transform, clip, material, primitive
  blend, final blend, and bounds semantics pass CPU/GPU evidence;
- registered `MeshProgram` uniforms and supported children pass exact ABI and
  pixel evidence;
- unsupported mesh programs fail with stable typed refusals;
- upload-before-draw, all-or-nothing preflight, budgets, generation, and
  close-once ownership are proven;
- mixed prepared frames preserve order and use the common submission;
- no hidden CPU, path, immediate, Graphite, Ganesh, or dynamic SkSL fallback
  exists;
- independent review is clean;
- `Vertices` is removed from the legacy allowlist.
