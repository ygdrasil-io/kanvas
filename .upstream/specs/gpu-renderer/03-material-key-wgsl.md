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

Canonical key, payload, resource, material-lowering, and snippet ABI boundaries
are defined in `33-key-boundaries-material-lowering.md`. This file defines the
material-specific key and WGSL module rules under that broader boundary.

Material-facing paint/source planning is defined in
`31-material-source-paint-pipeline.md`. `MaterialKey` consumes accepted
`GPUPaintPipelinePlan` and `GPUMaterialSourcePlan` facts; it does not own
legacy paint interpretation or source-family validation itself.

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
- local coordinate requirements from
  `30-coordinate-transform-bounds-policy.md` when they affect WGSL code shape,
  helper selection, or ABI layout;
- uniform layout identity;
- texture and sampler binding layout identity;
- registered runtime-effect descriptor identity;
- WGSL fragment identity and version;
- material dictionary version;
- `GPUMaterialAssemblyPlan` identity when the dictionary has accepted the key;
- feature flags that affect generated WGSL behavior.

Resource facts included in `MaterialKey` are limited to topology, layout,
usage, and behavior-affecting source facts. Concrete resource identity,
resource residency, handles, leases, cache entries, and pixel contents are
never material identity.

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
- concrete transform matrix values, rounded bounds, coordinate-space
  generations, and pixel-grid facts unless they affect WGSL code shape,
  layout, or pipeline validity;
- compute entry point, workgroup size, or storage-resource topology;
- clip stack descriptor identity, scissor bounds, stencil ordering tokens, mask
  coordinates, atlas entry refs, or clip budget state;
- image/filter graph identity.

Clip execution is governed by `24-clip-stencil-mask-pipeline.md`. A clip route
may affect render-step selection, depth/stencil state, WGSL ABI, or
`GPURenderPipelineKey` validity, but the captured clip stack and per-draw clip
values are not render-material identity.
Common coordinate-space, transform, bounds, pixel-grid, rounding, and
precision behavior is governed by
`30-coordinate-transform-bounds-policy.md`. `MaterialKey` may include only the
coordinate facts that change material WGSL behavior or layout.

## Material Descriptor To Key

`NormalizedDrawCommand.material` is first lowered to `GPUPaintPipelinePlan` and
`GPUMaterialSourcePlan` as defined in
`31-material-source-paint-pipeline.md`. The recorder derives `MaterialKey` from
accepted plan identity.

Derivation must be:

- deterministic;
- independent of object addresses;
- stable across equivalent descriptors;
- explicit about unsupported material-source features;
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
- primitive-color input and registered primitive blender for
  `DrawVertices`, governed by `26-draw-vertices-mesh-pipeline.md`;
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

Color-management facts that affect material WGSL code shape, helper selection,
gradient interpolation, runtime color uniform handling, or profile-dependent
sampling are defined in `29-color-management-pipeline.md`. `MaterialKey` may
include the color behavior identity required to assemble WGSL, but it must not
include payload values, decoded pixel contents, concrete profile object
identity, or platform conversion state.

`GPUFilterPlan` is separate from `MaterialKey`. A filter plan may consume
render pipelines, compute pipelines, intermediate textures, buffers, samplers,
and typed `CPUPreparedGPU` artifacts when routing policy allows them. Filter
graph identity, intermediate ownership, and pass scheduling must not be encoded
as material identity. Detailed filter graph, node route, bounds, crop/tile,
runtime-effect, and intermediate rules are defined in
`23-filter-effect-pipeline.md`.

`GPULayerPlan` and broad layer semantics are defined in
`08-layer-and-filter-plans.md`. Detailed `saveLayer` execution, offscreen
targets, initialization/backdrop, restore composite, and layer elision are
defined in `28-layer-savelayer-execution.md`. `GPUDrawLayer` remains the
low-level pass/layer planning structure; it does not replace the higher-level
layer semantic or execution plans.
Color-filter chains may fold into `MaterialKey` only when
`23-filter-effect-pipeline.md` proves that material placement is equivalent to
the filter DAG behavior.

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

Runtime effects are supported only through registered Kanvas descriptors owned
by `27-registered-runtime-effects-registry.md`.

A supported descriptor must define:

- stable effect ID;
- descriptor version and registry generation;
- material-key contribution;
- uniform layout;
- child shader or texture binding rules;
- Kotlin/CPU behavior for oracle use;
- WGSL fragment implementation;
- parser/reflection evidence;
- stable unsupported reasons for missing features.

Runtime effects that execute inside filter DAGs must also satisfy
`GPUFilterRuntimeEffectPlan` from `23-filter-effect-pipeline.md`; arbitrary
Skia `SkRuntimeEffect`, SkSL source, or runtime shader builder input is still
refused.

Arbitrary Skia/SkSL runtime shader input is refused with a stable diagnostic.
SkSL is compatibility vocabulary, not the implementation language.

Registered runtime effects contribute typed material root or child nodes through
`GPUMaterialDictionary`. They are not accepted by matching arbitrary shader
source hashes.
Uniform values are payload facts from `17-payload-gathering-and-slots.md`; they
must not enter `MaterialKey` or pipeline keys. Live-editable parameters are
governed by `GPURuntimeEffectLiveEditPlan`.

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
