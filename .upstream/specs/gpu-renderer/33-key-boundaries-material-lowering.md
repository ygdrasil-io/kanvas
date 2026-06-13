# Key Boundaries And Material Lowering

Status: Draft
Date: 2026-06-13

## Purpose

Define the canonical boundary between keys, payload, resource descriptors,
resource identity, material lowering context, material root sets, and WGSL
snippet ABI.

This consolidates the Graphite-inspired split that Kanvas keeps: compact
material identity, explicit pipeline identity, late payload gathering, and
resource materialization are separate contracts.

## Graphite Equivalence

Graphite concepts map to Kanvas concepts by responsibility, not by package or
class inheritance:

| Graphite concept | Kanvas target equivalent |
|---|---|
| `PaintParamsKey` | `MaterialKey` typed preimage. |
| `ShaderCodeDictionary` | `GPUMaterialDictionary`. |
| `ShaderSnippet` | `WGSLSnippet`. |
| `ShaderNode` | `WGSLSnippetNode`. |
| `KeyContext` | `GPUMaterialLoweringContext`. |
| `PipelineDataGatherer` | `GPUPayloadGatherer` plus payload plans. |
| `GraphicsPipelineDesc` / render pipeline desc | `GPURenderPipelineKey`. |
| Compute pipeline desc | `GPUComputePipelineKey`. |

Kanvas intentionally does not inherit Graphite's SkSL generation, raw key
stream layout, arena ownership, backend abstraction, or source package tree.

## Canonical Boundary Table

| Contract | Includes | Excludes |
|---|---|---|
| `MaterialKey` | Material-source kind, accepted snippet tree identity, child-slot shape, color-filter or shader-blender identity when material-owned, runtime-effect descriptor identity, color behavior identity that affects material WGSL, uniform/resource layout class, immutable sampler layout facts, material dictionary version salt. | Per-draw values, payload slots, concrete texture handles, imported handles, surface leases, uploaded artifact keys, pixel contents, atlas coordinates, clip stack identity, target format, blend state, command ID, cache residency. |
| `GPUMaterialProgramID` | Dictionary-local interned ID for an equivalent `MaterialKey` under one dictionary version. | Portable identity without dictionary version and key preimage, payload values, resource handles. |
| `GPUMaterialAssemblyPlan` | Material root roles, snippet nodes, aggregate requirements, material ABI contributions, module assembly inputs, dictionary version, material key hash. | Final target attachment state, concrete resources, per-draw values, command encoding. |
| `WGSLModule` / `WGSLComputeModule` hash | Complete source, entry points, helper versions, reflection-visible layouts, feature requirements, module assembly salt. | Per-draw values, bind group instances, texture contents, resource object addresses. |
| `WGSLBindingLayout` | Group, binding, visibility, resource kind, sample/storage type, access, minimum size, dynamic-offset policy, layout role. | Concrete resources, residency, pixel contents, current cache hit state. |
| `GPURenderPipelineKey` | Render step identity, material identity, module identity, vertex layout, target format class, blend/depth/stencil/sample state, bind group layout identity, capability facts affecting validity. | Per-draw values, buffer offsets, bind group instances, texture object identity, atlas coordinates, resource residency, command ID. |
| `GPUComputeProgramKey` | Algorithm identity, compute module identity, workgroup policy when code/layout-affecting, resource topology, capability facts, version salt. | Dispatch dimensions unless compiled into code, input/output resource handles, per-dispatch values. |
| `GPUComputePipelineKey` | Compute program identity, entry point, bind group layout, storage/sample/uniform layout identity, capability facts. | Input texture contents, output resource addresses, dispatch-local values, cache state. |
| `GPUPayloadFingerprint` | Pass-local payload block structure, value classes when used for deduplication, packing plan identity, upload range identity. | Durable material or pipeline identity, concrete texture content, support claim by itself. |
| `CPUPreparedGPUArtifactKey` | Typed artifact class, stable source facts, preparation parameters, generation requirements, invalidation facts, budget class. | Full CPU-rendered draw/layer/filter/scene output, GPU resource handle, current atlas coordinates unless explicitly part of the artifact key. |
| `GPUTextureDescriptor` / `GPUResourceDescriptor` | Resource topology, usage, format, dimensions, sample count, ownership/lifetime class, view/sampler descriptor facts. | Concrete backend handle identity, current residency, cache hit/miss, pixel contents unless descriptor explicitly represents immutable source bytes outside GPU identity. |
| `GPUTextureResourceRef` / resource handle | Concrete resource identity scoped to owner, device generation, and lifetime. | Durable key identity, material identity, portable serialization. |
| Residency facts | Whether a resource is available, stale, evicted, promised, leased, pending upload, or pending read. | Material identity and pipeline identity except when a capability or layout fact changes validity. |
| `GPUUseToken` / generation token | Ordering and lifetime facts for safe reuse, mutation, pending reads, and invalidation. | Shader code identity, material identity, payload values. |
| `SortKey` | Legal ordering axes: layer, pass, dependency class, render step, material/pipeline grouping when known, barrier generation. | Cache hit state, object identity, any axis that crosses an explicit dependency. |

The table above is normative. Domain specs may add more concrete fields, but
they must not move an excluded fact into a key without updating this file.

## Resource Topology Versus Resource Identity

Pipeline and material keys may include resource topology when it affects
shader code, binding layout, or pipeline validity.

Examples of allowed topology facts:

- sampled texture count and view dimension;
- sample type or storage texture format class;
- sampler layout class;
- uniform/storage buffer struct layout;
- whether a material requires an atlas binding;
- whether a destination-read binding is present;
- whether a runtime-effect child contributes a resource binding.

Examples of forbidden identity facts:

- `GPUTexture` object address;
- imported native handle value;
- surface lease token;
- atlas entry coordinate for one draw;
- uploaded image artifact cache entry;
- decoded pixel contents;
- bind group instance;
- current cache residency.

When a spec says a key represents "resources", it means resource topology,
layout, usage, and capability facts. It does not mean concrete resource
identity.

## `GPUMaterialLoweringContext`

`GPUMaterialLoweringContext` is the Kanvas equivalent of Graphite's key
context. It is passed to material-source lowering and material dictionary
expansion when deriving `MaterialKey`, root sets, requirements, and ABI
contributions.

It contains:

- `GPUCapabilities` relevant to material features;
- `GPUMaterialDictionary` version and available built-in snippets;
- `GPURuntimeEffectRegistry` snapshot and compatibility lookup view;
- target color facts needed by `GPUColorPlan` and
  `GPUColorManagementPlan`;
- working color-space and premul/alpha domain facts;
- `GPUCoordinateSpace`, local-to-device, device-to-local, and inverse
  availability summaries from `30-coordinate-transform-bounds-policy.md`;
- render-step primitive input availability such as primitive color or local
  coordinates;
- destination-read availability summary from `GPUDestinationReadPlan`;
- accepted source, color-filter, shader-blender, and local-matrix plan facts;
- material budget policy, including gradient stop storage and snippet
  expansion limits;
- diagnostic sink and stable provenance labels.

It must not contain:

- concrete texture handles;
- imported native handles;
- surface leases;
- atlas coordinates;
- pass-local payload slot IDs;
- per-draw uniform values;
- cache hit/miss state;
- mutable Canvas state;
- arbitrary WGSL source strings.

The context is read-only during lowering. Lowering returns structured plans and
diagnostics; it does not allocate GPU resources or encode commands.

## Material Root Set

`MaterialKey` remains the durable material identity. Dictionary expansion
produces a `GPUMaterialRootSet` before WGSL assembly.

Root roles are:

| Root | Policy |
|---|---|
| `sourceRoot` | Required. Produces the source color in the material working color domain. |
| `filterRoot` | Optional. Represents accepted color-filter chains when they are material-folded. |
| `shaderBlendRoot` | Optional. Represents accepted shader-side blenders that cannot be expressed as fixed GPU blend state. |
| `runtimeEffectRoot` | Optional role label for registered runtime effects when the descriptor participates as source, filter, or blender. It is not a generic arbitrary shader hook. |
| `clipRoot` | Forbidden. Clip, coverage, stencil, and mask behavior belong to clip, layer, and render-step planning. |
| `coverageRoot` | Forbidden until a future accepted spec explicitly defines source-coverage material behavior. |

Each root references a `WGSLSnippetNode` and records aggregate requirement
flags, child-slot shape, ABI contributions, and diagnostic provenance.

`GPUBlendPlan` owns fixed-function blend state. A `shaderBlendRoot` is used
only when an accepted material or runtime-effect route requires shader-side
blend logic and `GPUDestinationReadPlan` has accepted the destination-read
requirements.

## WGSL Snippet Input And Output ABI

Every `WGSLSnippet` must declare a structured ABI. The renderer must not rely
on hidden globals or ad hoc string conventions.

Snippet input facts:

| Input fact | Meaning |
|---|---|
| `localCoords` | Local material coordinates after accepted local matrix and transform handling. |
| `deviceCoords` | Device or target coordinates when required by a render step or destination read. |
| `normalizedPrimitiveCoords` | Primitive-relative coordinates when a render step exposes them. |
| `priorStageColor` | Color from the previous material/filter stage. Required only when declared. |
| `destinationColor` | Destination/backdrop color from an accepted destination-read strategy. |
| `primitiveColor` | Per-vertex or render-step primitive color when declared by `DrawVertices` or similar routes. |
| `samplePosition` | Sample/fragment position facts when the render step exposes them. |
| `colorValueSpec` | Color/premul/working-space metadata needed for correct conversion and store. |

Snippet output facts:

| Output fact | Meaning |
|---|---|
| `sourceColor` | Source color in the declared working color domain. |
| `filteredColor` | Color-filter result when the snippet is a filter root or filter stage. |
| `shaderBlendColor` | Shader-side blend result when destination color was accepted. |
| `requirementFlags` | Aggregate needs that must be accepted by the module, blend, destination-read, and render-step plans. |

Child invocation rules:

- child slots are declared by stable ordinal and role;
- a parent must declare whether it passes local coordinates unchanged,
  transformed, or not at all;
- a parent must declare whether it passes `priorStageColor` or consumes it;
- destination color cannot be read by a child unless the root requirement was
  accepted by `GPUDestinationReadPlan`;
- recursive or cyclic child graphs are forbidden;
- missing child descriptors refuse with canonical runtime-effect or material
  diagnostics.

The ABI is validated through `11-wgsl-layout-binding-abi.md` and
`16-material-dictionary-and-snippet-registry.md`. Complete module validation
through `wgsl4k` is required before support claims.

## Runtime-Effect Usage Set

`GPURuntimeEffectRegistry` is the durable descriptor source of truth.

Each `GPURecording` also records a `GPURuntimeEffectUsageSet`:

- descriptor IDs and versions used by the recording;
- requested placement: material source, color filter, shader blender, filter
  node, primitive blender, compute, or future clip shader;
- uniform schema hashes;
- child slot hashes;
- resource binding layout hashes;
- WGSL plan hashes;
- CPU oracle version used for evidence when applicable;
- compatibility lookup facts.

The usage set is not the registry. It is recording evidence and cache input for
validation, diagnostics, warmup, and PM reports.

## Validation Requirements

Promoted routes must provide fixtures that prove:

- equivalent descriptors produce equivalent `MaterialKey` preimages;
- payload value changes do not change durable material or pipeline keys unless
  they affect layout or code shape;
- concrete texture/resource handles never appear in `MaterialKey`,
  `GPURenderPipelineKey`, or `GPUComputePipelineKey`;
- `GPUMaterialLoweringContext` contains only allowed planning facts;
- root set dumps are deterministic;
- WGSL snippet ABI declarations match module reflection and Kotlin packing
  plans;
- registered runtime-effect usage sets are recorded separately from registry
  snapshots.

