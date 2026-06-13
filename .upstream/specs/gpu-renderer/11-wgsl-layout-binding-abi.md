# WGSL Layout And Binding ABI

Status: Draft
Date: 2026-06-13

## Purpose

Define the WGSL binding, layout, reflection, uniform packing, and Kotlin-side
ABI rules for render and compute modules in the new GPU renderer.

WGSL module validation is not enough by itself. A route is promoted only when
the generated WGSL bindings match the Kotlin-side descriptors, packers, resource
bindings, and pipeline-layout keys that will be used at command encoding time.

## Ownership Boundary

The `:gpu-renderer` core owns:

- renderer WGSL fragment metadata;
- material snippet ABI contributions from `GPUMaterialDictionary`;
- complete `WGSLModule` and `WGSLComputeModule` assembly contracts;
- binding layout descriptors;
- uniform and storage layout descriptors;
- Kotlin-side packing contracts;
- payload write contracts that consume those packing plans;
- reflection comparison rules;
- diagnostics for ABI mismatches.

The core does not own:

- arbitrary WGSL user extension loading;
- SkSL compilation or SkSL-to-WGSL translation;
- CPU interpretation of arbitrary WGSL;
- backend-specific shader language translation.

## ABI Objects

The renderer defines explicit ABI objects:

| Object | Purpose |
|---|---|
| `WGSLBindingLayout` | Canonical bind group, binding, visibility, resource kind, and access contract. |
| `WGSLUniformLayout` | Canonical scalar/vector/matrix/array/struct layout and alignment contract for uniform buffers. |
| `WGSLStorageLayout` | Canonical storage-buffer or storage-texture layout, access, and element contract. |
| `WGSLResourceBindingPlan` | Per-pass or per-dispatch binding plan connecting descriptors to actual GPU resources. |
| `WGSLPackingPlan` | Kotlin-side write contract for uniforms, storage payloads, dynamic offsets, and padding. |
| `GPUPayloadWritePlan` | Renderer-side value write recipe that must obey `WGSLPackingPlan`. |

These objects are part of key preimages when they affect pipeline validity or
module behavior. They must be dumpable and independent of Kotlin object
identity.

## Bind Group Policy

The initial renderer target uses stable bind group roles:

| Group | Role |
|---|---|
| `0` | Frame, target, transform, clip, and render-step intrinsic data. |
| `1` | Material uniforms, material textures, and material samplers. |
| `2` | CPU-prepared or GPU-native artifacts such as atlases, masks, and image textures when not material-owned. |
| `3` | Compute or filter node resources when a compute or filter plan requires a separate resource topology. |

Filter/effect routes from `23-filter-effect-pipeline.md` may use group `3`
for node-local sampled textures, storage textures, storage buffers, uniforms,
runtime-effect child bindings, and intermediate resources. The ABI includes
layout, access, sample/storage type, and reflection facts. It does not include
filter intermediate cache residency, node execution timing, destination-copy
generation, or concrete resource handles.

Clip routes from `24-clip-stencil-mask-pipeline.md` use group `0` for
render-step intrinsic analytic clip data when it is part of frame/target/step
state, and group `2` for coverage-mask resources such as
`GPUCoverageAtlasBinding`. Registered clip shader routes may use group `3`
when they require a separate shader-mask resource topology. The ABI includes
layout, access, sample/storage type, and reflection facts. It does not include
clip stack descriptor identity, mask residency, stencil ordering tokens, atlas
generations, or concrete resource handles.

A route may use fewer groups. A route may add a new group role only through an
accepted spec update because group roles affect pipeline-layout compatibility,
cache keys, and diagnostics.

Bindings inside a group must have deterministic ordering by role, declared
binding number, and resource kind. Generated WGSL and Kotlin descriptors must
share the same ordering source.

## Resource Kinds

`WGSLBindingLayout` supports:

- uniform buffer;
- read-only storage buffer;
- read-write storage buffer;
- sampled texture;
- multisampled texture;
- storage texture;
- sampler;
- comparison sampler when accepted by a route;
- external or imported texture only when a future accepted spec defines its
  facade behavior.

Each binding records:

- group and binding number;
- shader visibility;
- resource kind;
- sample type or storage format when applicable;
- texture view dimension, multisample state, and storage access when
  applicable;
- access mode;
- required feature and capability facts;
- minimum binding size when relevant;
- dynamic-offset policy;
- stable diagnostic label.

Sampled texture bindings use `GPUSampledTextureBinding` records from
`18-texture-image-ownership.md` during payload gathering. The ABI includes
sample type, view dimension, multisample/storage facts, and binding layout. It
For image/bitmap routes from `22-image-bitmap-codec-pipeline.md`, the ABI sees
only the accepted sampled texture/view/sampler contract after decode,
preparation, upload artifact, and texture ownership planning. It does not
include codec selection, frame selection, texture handles, imported handles,
surface leases, pixel contents, or uploaded artifact cache keys.
Path and coverage atlas bindings use `GPUCoverageAtlasBinding` from
`19-path-coverage-atlas-strategy.md`; the ABI includes the resource layout and
access facts, not atlas residency as material identity.
Clip coverage-mask bindings use the same accepted atlas or standalone mask
binding objects through `24-clip-stencil-mask-pipeline.md`; the ABI includes
resource layout and access facts, not clip stack identity or stencil ordering
as material identity.
Destination-read bindings use `GPUDestinationReadBinding` from
`20-destination-read-strategy.md`; the ABI includes sampled texture/sampler
layout and coordinate payload facts, not destination copy residency as material
identity.
Text and glyph bindings use `GPUTextBinding` from
`21-text-glyph-pipeline.md`; the ABI includes text atlas or bitmap glyph
texture/sampler layouts, text instance buffer layouts, `GPUTextSDFParams`
uniform layout, and text render-step access facts. Atlas coordinates, glyph
IDs, atlas generations, upload tokens, and entry refs remain payload/resource
facts, not material identity.

## Uniform And Storage Packing

`WGSLPackingPlan` must be derived from structured layout metadata and validated
against `wgsl4k` reflection when the dependency can represent the required
layout.

Uniform and storage packing must record:

- field name and stable ID;
- scalar/vector/matrix/array/struct kind;
- alignment;
- size;
- stride;
- offset;
- padding bytes;
- numeric representation;
- color and premul interpretation when packed values carry color data;
- dynamic-offset alignment when used.

Per-draw uniform values are not key inputs, but their layout is. A command may
write values only through a packing plan that matches the module reflection and
pipeline key preimage.

Concrete value gathering and pass-local payload slots are defined in
`17-payload-gathering-and-slots.md`.

## Reflection Contract

For every promoted WGSL route, the renderer must compare:

- declared `WGSLBindingLayout`;
- declared `WGSLUniformLayout`;
- declared `WGSLStorageLayout`;
- `wgsl4k` parser and reflection output;
- generated pipeline-layout descriptor;
- Kotlin packing plan.

The route refuses when reflection and declarations disagree on behavior-
affecting facts such as binding type, visibility, access, format, alignment,
size, or dynamic-offset requirements.

If `wgsl4k` cannot represent a needed WGSL feature, the route remains
unpromoted or capability-gated. The minimized WGSL input and expected behavior
must be captured for a `wgsl4k` issue rather than hidden behind a local
workaround.

## Render Module ABI

Render `WGSLModule` ABI includes:

- vertex inputs and locations;
- instance inputs when used;
- render-step intrinsic bindings;
- material bindings;
- artifact or atlas bindings;
- fragment outputs;
- target color format and alpha/color-space assumptions;
- blend/color plan dependencies;
- bind group layout identity.

`GPURenderPipelineKey` must include every ABI fact that affects pipeline
validity. It must not include per-draw uniform values, buffer offsets, texture
contents, or transient GPU handle identity.

## Material Dictionary Integration

`GPUMaterialDictionary` contributes material-side ABI facts through
`WGSLSnippet` metadata and `GPUMaterialAssemblyPlan`.

Rules:

- snippet-declared uniforms, textures, samplers, and material-owned buffers must
  become canonical `WGSLBindingLayout`, `WGSLUniformLayout`, and
  `WGSLPackingPlan` entries;
- complete module reflection must match both render-step declarations and
  material snippet declarations;
- material-owned resources use bind group `1` unless an accepted spec changes
  bind group policy;
- shared atlases, masks, and CPU-prepared artifacts stay outside the material
  dictionary and use their accepted resource group. Path and coverage atlas
  bindings follow `19-path-coverage-atlas-strategy.md`;
- destination-read snapshots and existing intermediates stay outside the
  material dictionary and use their accepted destination-read binding group from
  `20-destination-read-strategy.md`;
- a mismatch between snippet ABI and complete module reflection refuses the
  route with a stable diagnostic.

## Payload Gathering Integration

`GPUPayloadGatherer` consumes this ABI contract.

Rules:

- every payload write must reference a field in `WGSLPackingPlan`;
- every resource binding must reference an entry in `WGSLResourceBindingPlan`;
- payload slots must not change pipeline-key identity;
- payload gather/write/binding/upload plans must be validated by fixtures
  against reflected layouts;
- packing or binding mismatch refuses the route instead of falling back to CPU.

## Compute Module ABI

`WGSLComputeModule` ABI includes:

- compute entry point;
- workgroup size policy;
- storage buffer bindings;
- storage texture bindings;
- sampled texture and sampler bindings;
- uniform bindings;
- indirect dispatch requirements when accepted;
- resource access and synchronization requirements;
- bind group layout identity.

`GPUComputePipelineKey` must include every ABI fact that affects pipeline
validity. Dispatch dimensions are values, not key facts, unless they are
compiled into module shape or affect pipeline validity.

## Diagnostics

ABI diagnostics must include:

- module kind: render or compute;
- module hash;
- binding layout preimage and hash;
- uniform or storage layout preimage and hash;
- reflection summary;
- packing plan summary;
- payload write plan summary when values were gathered;
- mismatch field when refused;
- consuming material, render step, filter node, or compute program.

Stable reason-code examples:

- `unsupported.wgsl.binding_group_role`
- `unsupported.wgsl.binding_reflection_mismatch`
- `unsupported.wgsl.uniform_alignment_mismatch`
- `unsupported.wgsl.storage_layout_mismatch`
- `unsupported.wgsl.dynamic_offset_unavailable`
- `unsupported.wgsl.resource_kind_unavailable`
- `unsupported.wgsl.feature_unrepresented_by_wgsl4k`
- `unsupported.wgsl.packing_plan_missing`
- `unsupported.wgsl.payload_write_plan_mismatch`

## Validation Requirements

Promoted ABI behavior requires:

- canonical dumps for binding layouts and packing plans;
- reflection comparison tests for every promoted module;
- negative tests for binding, alignment, size, and resource-kind mismatch;
- render and compute key preimages that include ABI hashes;
- Kotlin packer tests that assert offsets, sizes, and padding;
- payload gathering tests that write values through the packing plan;
- PM evidence that includes module hash, layout hash, and reflection status.

## Non-Goals

- Do not rely on hand-maintained duplicate binding declarations.
- Do not validate fragments only and skip complete module reflection.
- Do not allow runtime WGSL strings to bypass ABI objects.
- Do not include per-draw values in pipeline keys.
- Do not hide ABI mismatches behind CPU fallback.
