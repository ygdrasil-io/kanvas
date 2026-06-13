# MaterialKey And WGSL

Status: Draft
Date: 2026-06-13

## Purpose

Define the render paint/material identity and WGSL module rules for the new
GPU renderer.

Kanvas replaces Graphite's SkSL-centered paint machinery with
`MaterialKey`, `GPUMaterialDictionary`, `WGSLSnippet`, `WGSLFragment`, and
render `WGSLModule` contracts. WGSL is the shader implementation target for
both render and compute work, but compute programs do not flow through
`MaterialKey`.

## `MaterialKey`

`MaterialKey` identifies the render material behavior of a normalized draw
command. It is independent from target attachment state and render-step fixed
state.

`MaterialKey` is render-only. It is consumed by `GPURenderStep` and
`GPURenderPipelineKey` construction. Compute work uses
`GPUComputeProgramKey`, `WGSLComputeModule`, and `GPUComputePipelineKey`
instead.

It includes:

- source kind: solid color, gradient, image, registered runtime effect, or
  future supported source;
- image source descriptor class when the material samples an image;
- color filter chain identity when supported;
- blender identity when supported;
- color-space requirements;
- local coordinate requirements;
- uniform layout identity;
- texture and sampler binding layout identity;
- registered runtime-effect descriptor identity;
- WGSL fragment identity and version;
- material dictionary version;
- `GPUMaterialAssemblyPlan` identity when the dictionary has accepted the key;
- feature flags that affect generated WGSL behavior.

It does not include:

- target texture format;
- sample count;
- vertex topology;
- depth/stencil state;
- per-draw uniform values;
- payload slot IDs;
- bind group instances;
- texture object handles;
- texture resource refs;
- surface texture leases;
- uploaded texture artifact keys;
- imported texture handles;
- pixel contents;
- transient resource handles;
- command ID;
- compute entry point, workgroup size, or storage-resource topology;
- image/filter graph identity.

## Material Descriptor To Key

`NormalizedDrawCommand.material` is a descriptor. The recorder derives a
`MaterialKey` from it.

Derivation must be:

- deterministic;
- independent of object addresses;
- stable across equivalent descriptors;
- explicit about unsupported material features;
- dumpable for PM and conformance reports.

If descriptor lowering fails, route selection returns `RefuseDiagnostic` with a
stable reason. It must not silently substitute a CPU shader.

For image materials, descriptor lowering may include `GPUImageSourceDescriptor`
kind, sampling class, tile behavior, sample type, and binding layout facts when
they affect WGSL code or layout. Texture ownership, concrete handles, codec
selection, decode requests, animation frame selection, upload artifact keys,
imported handles, surface leases, and pixels are handled by
`18-texture-image-ownership.md` and
`22-image-bitmap-codec-pipeline.md`, not by `MaterialKey`.

## Material Dictionary Boundary

`MaterialKey` is consumed by `GPUMaterialDictionary` before WGSL module
assembly.

The dictionary:

- interns equivalent material keys as `GPUMaterialProgramID` values;
- expands material keys into `WGSLSnippetNode` trees;
- validates snippet child slots and requirement propagation;
- produces `GPUMaterialAssemblyPlan` records;
- contributes material uniforms, textures, samplers, and material-owned buffers
  to the WGSL ABI contract.

`GPUMaterialProgramID` may be used as a compact cache handle only together with
dictionary version and material preimage facts. It does not replace
`MaterialKey` as the durable identity.

The detailed dictionary and snippet registry policy is defined in
`16-material-dictionary-and-snippet-registry.md`.

## WGSL Fragment Model

`WGSLFragment` is a validated piece of shader logic with declared inputs,
outputs, uniforms, textures, samplers, and feature requirements.

Fragment categories:

- material source;
- color filter;
- blender or blend helper;
- coordinate transform helper;
- render-step geometry/coverage contribution;
- color-space helper;
- runtime-effect descriptor contribution.

Fragments are composed into a render `WGSLModule` only through deterministic
module assembly rules. Material fragments enter those rules through
`GPUMaterialAssemblyPlan`, not through ad hoc string concatenation.

## `WGSLModule`

`WGSLModule` is the concrete render shader-module source and reflection result
used by the GPU renderer.

It must record:

- source fragments and versions;
- vertex and fragment entry points;
- required features such as `f16` if used;
- bind group layouts;
- uniform layout and alignment facts;
- texture and sampler bindings;
- reflection output;
- parser diagnostics;
- module hash.

The module hash must include all code and layout facts that affect execution.
It must not include per-draw values.

## Compute WGSL Program Model

`WGSLComputeModule` is the concrete compute shader-module source and
reflection result used by GPU compute tasks.

Compute WGSL is used for GPU-native preparation, filter kernels, reductions,
prefix-style work, or other non-render-pass work that the renderer explicitly
supports. It is not a material fragment and is not assembled from
`MaterialKey`.

`WGSLComputeModule` must record:

- source fragments or generator inputs and versions;
- compute entry point;
- required features such as `f16` if used;
- declared workgroup sizing policy;
- bind group layouts;
- storage buffer, storage texture, sampled texture, sampler, and uniform
  bindings;
- reflection output;
- parser diagnostics;
- module hash.

The module hash must include all code and layout facts that affect execution.
It must not include per-dispatch values, transient resource handles, or command
IDs.

`GPUComputeProgramKey` identifies the compute program contract before pipeline
creation. It includes the algorithm identity, compute module identity,
resource-layout identity, workgroup sizing policy, required capabilities, and
renderer version salt. It does not include input texture contents,
per-dispatch uniform values, output resource handles, or cache residency.

## Filter Planning Boundary

`GPUFilterPlan` is separate from `MaterialKey`. A filter plan may consume
render pipelines, compute pipelines, intermediate textures, buffers, samplers,
and typed `CPUPreparedGPU` artifacts when routing policy allows them. Filter
graph identity, intermediate ownership, and pass scheduling must not be encoded
as material identity.

`GPULayerPlan`, `saveLayer` lowering, and broad layer semantics are defined in
`08-layer-and-filter-plans.md`. `GPUDrawLayer` remains the low-level pass/layer
planning structure; it does not replace the higher-level layer semantic plan.

## `wgsl4k` Validation

All generated or assembled WGSL used by promoted GPU routes must be validated
through `wgsl4k` where the dependency can represent the required language
features.

If `wgsl4k` behavior is ambiguous or surprising:

- capture the minimized WGSL input;
- record expected and actual parser/reflection behavior;
- refuse the route or keep it unpromoted;
- open a `wgsl4k` ticket instead of adding a hidden workaround.

The WGSL specification's shader-creation and pipeline-creation error model
must be reflected in diagnostics. A module with validation errors is not a
supported GPU route.

## Runtime Effects

Runtime effects are supported only through registered Kanvas descriptors.

A supported descriptor must define:

- stable effect ID;
- material-key contribution;
- uniform layout;
- child shader or texture binding rules;
- Kotlin/CPU behavior for oracle use;
- WGSL fragment implementation;
- parser/reflection evidence;
- stable unsupported reasons for missing features.

Arbitrary Skia/SkSL runtime shader input is refused with a stable diagnostic.
SkSL is compatibility vocabulary, not the implementation language.

Registered runtime effects contribute typed material root or child nodes through
`GPUMaterialDictionary`. They are not accepted by matching arbitrary shader
source hashes.

## Diagnostics

Material diagnostics must include:

- material descriptor summary;
- `MaterialKey` hash/preimage;
- WGSL fragment list;
- material dictionary version and snippet tree summary;
- `GPUMaterialAssemblyPlan` hash/preimage label;
- payload write plan label when material values are gathered;
- module hash;
- parser/reflection result;
- unsupported feature code when refused;
- route consuming the material.

Compute diagnostics are separate from material diagnostics. They must include
the `GPUComputeProgramKey` preimage, `WGSLComputeModule` hash,
parser/reflection result, required capabilities, and the route or filter plan
consuming the compute program.

## Non-Goals

- Do not compile SkSL.
- Do not implement Graphite's `PaintParamsKey` machinery.
- Do not use WGSL string concatenation without structured fragment metadata.
- Do not bypass `GPUMaterialDictionary` for material WGSL assembly.
- Do not pack per-draw material values during material-key construction.
- Do not hide parser failures behind CPU fallback.
- Do not include backend target state in `MaterialKey`.
- Do not encode compute program identity in `MaterialKey`.
- Do not encode `GPUFilterPlan` identity in `MaterialKey`.
