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
- complete `WGSLModule` and `WGSLComputeModule` assembly contracts;
- binding layout descriptors;
- uniform and storage layout descriptors;
- Kotlin-side packing contracts;
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
- access mode;
- required feature and capability facts;
- minimum binding size when relevant;
- dynamic-offset policy;
- stable diagnostic label.

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

## Validation Requirements

Promoted ABI behavior requires:

- canonical dumps for binding layouts and packing plans;
- reflection comparison tests for every promoted module;
- negative tests for binding, alignment, size, and resource-kind mismatch;
- render and compute key preimages that include ABI hashes;
- Kotlin packer tests that assert offsets, sizes, and padding;
- PM evidence that includes module hash, layout hash, and reflection status.

## Non-Goals

- Do not rely on hand-maintained duplicate binding declarations.
- Do not validate fragments only and skip complete module reflection.
- Do not allow runtime WGSL strings to bypass ABI objects.
- Do not include per-draw values in pipeline keys.
- Do not hide ABI mismatches behind CPU fallback.
