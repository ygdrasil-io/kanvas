# MaterialKey And WGSL

Status: Draft
Date: 2026-06-13

## Purpose

Define the paint/material identity and WGSL module rules for the new GPU
renderer.

Kanvas replaces Graphite's SkSL-centered paint machinery with
`MaterialKey`, `WGSLFragment`, and `WGSLModule` contracts. WGSL is the shader
implementation target.

## `MaterialKey`

`MaterialKey` identifies the material behavior of a normalized draw command.
It is independent from target attachment state and render-step fixed state.

It includes:

- source kind: solid color, gradient, image, registered runtime effect, or
  future supported source;
- color filter chain identity when supported;
- blender identity when supported;
- color-space requirements;
- local coordinate requirements;
- uniform layout identity;
- texture and sampler binding layout identity;
- registered runtime-effect descriptor identity;
- WGSL fragment identity and version;
- feature flags that affect generated WGSL behavior.

It does not include:

- target texture format;
- sample count;
- vertex topology;
- depth/stencil state;
- per-draw uniform values;
- transient resource handles;
- command ID.

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

Fragments are composed into a `WGSLModule` only through deterministic module
assembly rules.

## `WGSLModule`

`WGSLModule` is the concrete shader-module source and reflection result used by
the GPU renderer.

It must record:

- source fragments and versions;
- entry points;
- required features such as `f16` if used;
- bind group layouts;
- uniform layout and alignment facts;
- texture and sampler bindings;
- reflection output;
- parser diagnostics;
- module hash.

The module hash must include all code and layout facts that affect execution.
It must not include per-draw values.

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

## Diagnostics

Material diagnostics must include:

- material descriptor summary;
- `MaterialKey` hash/preimage;
- WGSL fragment list;
- module hash;
- parser/reflection result;
- unsupported feature code when refused;
- route consuming the material.

## Non-Goals

- Do not compile SkSL.
- Do not implement Graphite's `PaintParamsKey` machinery.
- Do not use WGSL string concatenation without structured fragment metadata.
- Do not hide parser failures behind CPU fallback.
- Do not include backend target state in `MaterialKey`.
