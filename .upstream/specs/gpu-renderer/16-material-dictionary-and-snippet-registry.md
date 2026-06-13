# Material Dictionary And WGSL Snippet Registry

Status: Draft
Date: 2026-06-13

## Purpose

Define the Graphite-inspired material dictionary for the new GPU renderer.

Graphite uses `ShaderCodeDictionary`, `ShaderSnippet`, `ShaderNode`, and
`PaintParamsKey` to turn compact paint identity into a decompressed shader tree,
stable code IDs, and generated shader modules. Kanvas adopts the separation and
the invariants, but replaces SkSL with WGSL and keeps the implementation inside
Kanvas-owned `GPU` renderer concepts.

This spec closes the material assembly gap between `MaterialKey` and
`WGSLModule`.

## Graphite Evidence

Relevant Graphite concepts:

- `ShaderSnippet` describes a shader module function ABI: name, static function,
  required dynamic inputs, uniforms, textures/samplers, child count, and
  generation hooks.
- `SnippetRequirementFlags` propagate needs such as local coordinates, prior
  stage color, destination color, primitive color, gradient buffers, sampler
  descriptor data, and optional vertex-lifted expressions.
- `ShaderNode` is the decompressed tree node for a paint key. It stores snippet
  ID, key index, children, embedded data, and aggregate requirement flags.
- `ShaderCodeDictionary` owns built-in snippets, known runtime-effect snippets,
  user runtime-effect snippets, stable snippet IDs, and unique IDs for
  equivalent paint keys.
- `PaintParamsKey` is compact key data. It is not the shader module itself; it
  can be expanded into a forest of nodes and then consumed by shader assembly.

Kanvas must keep these boundaries but must not copy Graphite's SkSL generation,
C++ arena ownership, bit layout, runtime-effect dictionary, or class/package
structure.

## Ownership Boundary

`GPUMaterialDictionary` is owned by `GPUSharedScope` and used by recorders,
pipeline-key construction, and WGSL module assembly.

It owns:

- built-in `WGSLSnippet` registration;
- registered runtime-effect descriptor snippets;
- `WGSLSnippetID` allocation;
- `MaterialKey` to `GPUMaterialProgramID` lookup;
- `MaterialKey` decompression into `WGSLSnippetNode` trees;
- requirement propagation across those trees;
- deterministic material assembly plans;
- dictionary versioning and diagnostics.

It does not own:

- public Skia-like paint interpretation;
- arbitrary WGSL string loading;
- SkSL parsing, SkSL IR, or SkSL translation;
- `GPURenderStep` geometry and coverage code;
- `GPUBlendPlan`, `GPUColorPlan`, or target attachment policy;
- GPU resource allocation or command encoding.

Compatibility adapters produce material descriptors. `MaterialKey` derives from
those descriptors. `GPUMaterialDictionary` validates, interns, and expands that
key for the GPU renderer.

## Core Objects

| Object | Purpose |
|---|---|
| `WGSLSnippetID` | Stable dictionary-local ID for a built-in or registered material snippet. |
| `WGSLSnippet` | Structured WGSL material function contract with ABI, children, requirements, and versions. |
| `WGSLSnippetNode` | Decompressed material tree node produced from a `MaterialKey`. |
| `GPUMaterialProgramID` | Compact dictionary-local ID for an equivalent `MaterialKey` under one dictionary version. |
| `GPUMaterialAssemblyPlan` | Deterministic plan for turning snippet nodes into WGSL fragment/module inputs. |

`GPUMaterialProgramID` is an optimization and cache handle. It is not a
portable serialization format by itself. Durable diagnostics and cache preimages
must include the dictionary version, `MaterialKey` hash, and enough structured
preimage data to reconstruct the material identity.

## Material Key Payload Boundary

`MaterialKey` is a typed canonical preimage, not Graphite's raw `int32` stream.

It may include behavior-affecting descriptor facts:

- snippet IDs and descriptor versions;
- child-slot structure;
- immutable sampler descriptor facts when they affect module or binding shape;
- gradient stop count and layout class;
- runtime-effect descriptor identity;
- color-space and premul behavior flags;
- feature flags that affect generated WGSL.

It must not include:

- per-draw uniform values;
- texture object handles;
- texture resource refs;
- imported texture handles;
- surface texture leases;
- uploaded texture artifact keys;
- pixel contents;
- transient atlas coordinates;
- cache residency;
- GPU resource handles;
- CPU-rendered fallback artifacts.

Uniform, texture, sampler, and buffer values are gathered later by the payload
path. The dictionary declares their layout and binding requirements; it does
not pack per-draw payload bytes. The dedicated payload-gathering policy is
defined in `17-payload-gathering-and-slots.md`. Texture and image ownership,
imports, surface leases, uploaded CPU pixels, and sampled binding descriptors
are defined in `18-texture-image-ownership.md`.

## `WGSLSnippet`

A `WGSLSnippet` records:

- snippet ID and human-readable label;
- snippet category: source, color filter, shader blender, coordinate helper,
  color-space helper, sampler helper, or registered runtime-effect
  contribution;
- snippet version;
- WGSL function or fragment identity;
- declared return kind;
- required dynamic inputs;
- uniform layout contribution;
- texture, sampler, storage-buffer, and gradient-buffer contributions;
- child count or accepted child-slot contract;
- child invocation rules;
- required capabilities and WGSL language features;
- liftability metadata when a computation may move to the vertex stage;
- diagnostic labels and unsupported reason codes.

Snippet metadata is the only source of truth for material WGSL assembly. The
renderer must not infer uniforms, textures, samplers, or dynamic inputs by
parsing ad hoc WGSL strings outside the `wgsl4k` validation and reflection
contract.

## Requirement Flags

Kanvas defines `WGSLSnippetRequirement` flags as stable, dumpable facts:

| Requirement | Meaning |
|---|---|
| `NeedsLocalCoords` | The node or one of its children requires local coordinates. |
| `NeedsPriorStageColor` | The node consumes the previous color in a filter or blend chain. |
| `NeedsDestinationColor` | The node needs destination color and must be accepted by destination-read policy. |
| `NeedsPrimitiveColor` | The node consumes render-step or per-primitive color. |
| `NeedsGradientBuffer` | The node consumes material-owned gradient stop data. |
| `NeedsSamplerDescriptorData` | The node carries sampler descriptor facts that affect module or binding layout. |
| `NeedsColorSpaceTransform` | The node requires explicit color-space conversion helpers. |
| `PassesLocalCoordsToChildren` | The node invokes children without changing local coordinates. |
| `CanLiftToVertex` | The node may move a pure expression to vertex output when budgets allow. |

Requirement propagation is deterministic:

- a parent starts with its own snippet requirements;
- child requirements propagate to the parent unless the child slot explicitly
  consumes the requirement through an argument;
- destination-color requirements are preserved until `GPUBlendPlan` or a later
  destination-read strategy accepts them;
- liftability is advisory and never required for correctness;
- any unsupported aggregate requirement refuses with a stable diagnostic.

The first slice may set `CanLiftToVertex` to false for every snippet. Later
slices may enable lifting only when `GPURenderStep` varying locations, ABI
reflection, and module hashes include the lifted shape.

## Material Root Set

`MaterialKey` remains the durable material identity. `GPUMaterialDictionary`
turns it into an explicit material root set.

Root roles are:

| Root | Policy |
|---|---|
| `sourceRoot` | Required. Produces the source color before fixed-function or shader blending. |
| `filterRoot` | Optional. Represents accepted color-filter chains when they are not folded into `sourceRoot`. |
| `shaderBlendRoot` | Optional. Used only for accepted shader-side blenders that cannot be represented by fixed GPU blend state. |
| `sourceCoverageRoot` | Not accepted by the initial target; future specs must define it before use. |
| `clipRoot` | Forbidden in `MaterialKey`; clip and coverage belong to draw/layer/render-step planning. |

This keeps Graphite's explicit-root lesson without inheriting Graphite's exact
paint-key roots. Fixed-function blending remains in `GPUBlendPlan` when possible
and does not require a material `shaderBlendRoot`.

Each root references a `WGSLSnippetNode`. Each node records:

- `WGSLSnippetID`;
- material key index or stable node ordinal;
- child nodes;
- embedded key data owned by the `MaterialKey` preimage;
- aggregate requirement flags;
- uniform layout contribution;
- texture, sampler, and storage binding contribution;
- diagnostic provenance.

Kanvas does not put clip or render-step coverage roots in the material tree.
Clip, coverage, stencil, and geometry behavior belong to `GPURenderStep`,
`GPUDrawLayerPlanner`, `GPULayerPlan`, and future coverage specs. A material
root set may contribute alpha or source coverage only when an accepted material
feature explicitly defines that behavior.

## Module Assembly

`GPUMaterialAssemblyPlan` is the bridge from a material root set to render
`WGSLModule` assembly.

It includes:

- `GPUMaterialProgramID`;
- dictionary version;
- material key hash and preimage label;
- explicit root-role dump;
- ordered `WGSLSnippetNode` dump;
- ordered `WGSLFragment` list and versions;
- function-name mangling policy;
- required dynamic inputs;
- aggregate snippet requirements;
- material binding contributions;
- uniform, storage, texture, and sampler layout contributions;
- required capabilities and WGSL features;
- parser/reflection evidence when already available;
- refusal diagnostics when assembly is unsupported.

WGSL helper names must be deterministic and independent of Kotlin object
identity. Node ordinals, snippet labels, and dictionary version are valid naming
inputs. Memory addresses, insertion-order accidents, and transient handles are
not valid naming inputs.

Complete module assembly still belongs to the WGSL module contract. The
material dictionary supplies the material side of that assembly; `GPURenderStep`,
`GPUBlendPlan`, `GPUColorPlan`, and `GPUTargetState` supply the remaining facts.

## Binding And ABI Integration

Material binding contributions flow into `WGSLBindingLayout`,
`WGSLUniformLayout`, and `WGSLPackingPlan`.

Rules:

- material uniforms, material textures, material samplers, and material-owned
  read-only buffers use bind group `1` unless a later accepted spec changes the
  group policy;
- material snippets declare texture/sampler ABI slots; they do not own
  texture allocation, import, upload, lease, or release policy;
- shared atlases, masks, and CPU-prepared artifacts remain outside material
  ownership and use their accepted artifact or atlas group;
- gradient stop data is material-owned for the first accepted linear-gradient
  route unless a later gradient-buffer spec moves it to a shared resource;
- per-draw values are not key facts, but their layout and packing plan are key
  facts;
- reflection mismatch between snippet metadata and complete module reflection
  refuses the route.

## Runtime Effects

Runtime effects are accepted only through registered Kanvas descriptors.

`GPUMaterialDictionary` may register a runtime-effect snippet only when the
descriptor provides:

- stable effect ID and descriptor version;
- WGSL fragment identity and version;
- uniform layout and packing plan;
- child shader or texture slot rules;
- CPU/Kotlin oracle behavior for evidence;
- parser/reflection evidence;
- unsupported reason codes for missing features.

Runtime-effect snippet de-duplication is based on descriptor identity, WGSL
fragment hash, uniform layout hash, child-slot ABI, and descriptor version. It
must not depend on object identity.
Runtime-effect texture child slots must use `GPUImageSourceDescriptor` and the
ownership rules from `18-texture-image-ownership.md`.

Arbitrary Skia/SkSL runtime shader input is refused with
`unsupported.material.runtime_effect_unregistered`. SkSL remains compatibility
vocabulary only.

The dictionary must not accept unknown runtime effects by shader hash alone.
Hash matching may be used only inside registered descriptor identity and
diagnostic evidence.

## Dictionary Versioning And Concurrency

`GPUMaterialDictionary` is thread-safe for read-heavy use by recorders and
pipeline construction.

Rules:

- built-in snippets are immutable after dictionary creation;
- runtime-effect descriptor registration is explicit and versioned;
- dictionary version changes when a snippet contract, descriptor contract,
  WGSL fragment, ABI contribution, or requirement propagation rule changes;
- IDs remain stable only within a dictionary version and device-generation
  compatible `GPUSharedScope`;
- entries are not removed while a recording or pipeline cache can reference
  them;
- caches must include dictionary version and device-generation facts where they
  affect validity.

If the implementation later supports dynamic descriptor registration, it must
publish a new dictionary generation rather than mutating existing key behavior
in place.

## Diagnostics

Material dictionary diagnostics must include:

- material descriptor summary;
- `MaterialKey` hash and preimage label;
- dictionary version;
- `GPUMaterialProgramID` when assigned;
- snippet tree dump;
- aggregate requirements;
- binding and packing contribution hashes;
- WGSL fragment list;
- module assembly status;
- refused requirement or snippet ID when unsupported.

Stable reason-code examples:

- `unsupported.material.snippet_unknown`
- `unsupported.material.snippet_child_count`
- `unsupported.material.requirement_destination_read`
- `unsupported.material.requirement_gradient_buffer`
- `unsupported.material.runtime_effect_unregistered`
- `unsupported.material.runtime_effect_child_slot`
- `unsupported.material.root_role_forbidden`
- `unsupported.material.dictionary_version_mismatch`
- `unsupported.material.snippet_reflection_mismatch`
- `unsupported.material.lift_varying_budget`

## First Slice Contract

The first rect/rrect slice requires these built-in snippets:

- solid color source;
- linear-gradient source;
- local-coordinate helper when needed by linear gradients;
- premul or color-space helper only when required by the accepted
  `GPUColorPlan`;
- fixed `SrcOver` metadata only if the accepted route needs a material-side
  helper; otherwise fixed-function blend remains in `GPUBlendPlan`.

The first slice does not register runtime effects, image shaders, color
filters, shader blenders requiring destination reads, radial gradients, sweep
gradients, or complex tile modes.

Required fixtures:

- equivalent solid-color descriptors produce the same `MaterialKey` and
  `GPUMaterialProgramID`;
- distinct linear-gradient layouts produce distinct material preimages;
- unsupported gradient tile mode refuses through the dictionary or material
  lowering with a stable reason;
- complete WGSL module reflection matches snippet-declared ABI for solid and
  linear-gradient routes;
- dictionary dumps are stable across equivalent inputs.

## Non-Goals

- Do not port Graphite's `ShaderCodeDictionary` implementation.
- Do not implement Graphite's `PaintParamsKey` binary layout.
- Do not compile SkSL or translate SkSL to WGSL.
- Do not accept arbitrary WGSL strings outside registered snippet metadata.
- Do not hide unsupported snippets behind CPU fallback.
- Do not put clip, geometry, or render-step coverage policy into
  `MaterialKey`.
- Do not use `GPUMaterialProgramID` as the only durable diagnostic or cache
  identity.
