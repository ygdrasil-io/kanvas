# Blend, Color, And Target State

Status: Draft
Date: 2026-06-13

## Purpose

Define blend, color, alpha, premultiplication, and target-state planning for
the new GPU renderer.

The renderer must not choose between fixed-function blending, shader blending,
offscreen isolation, or refusal with ad hoc logic. Blend and color behavior are
explicit planning outputs that feed material keys, render pipeline keys, layer
plans, diagnostics, and evidence.

## Ownership Boundary

`MaterialKey` identifies render material behavior. It may include source,
color-filter, and blender identity when those affect shader code, but it does
not own target attachment state.

`GPURenderPipelineKey` owns executable render state, including target format,
blend state, depth/stencil state, sample count, and compatible WGSL module
identity.

`GPUBlendPlan` and `GPUColorPlan` bridge material, layer, and target facts so
the renderer can decide whether a draw can execute natively, needs an isolated
layer or texture read, or must refuse.

## `GPUBlendPlan`

`GPUBlendPlan` is the explicit blend decision for one draw, layer composite, or
filter composite.

It records:

- blend mode identity;
- source opacity and alpha classification;
- destination-read requirement;
- fixed-function blend eligibility;
- shader blend eligibility;
- offscreen isolation requirement;
- target format and alpha compatibility;
- ordering and barrier requirements;
- route outcome or refusal reason.

Plan kinds:

| Kind | Meaning |
|---|---|
| `FixedFunctionBlend` | The blend can use GPU attachment blend state. |
| `ShaderBlendNoDstRead` | The blend math is implemented in WGSL without sampling destination. |
| `ShaderBlendWithDstRead` | The blend requires destination color as an input and therefore requires an accepted texture-read or isolated-layer strategy. |
| `LayerCompositeBlend` | The blend is applied while compositing an isolated `GPULayerPlan`. |
| `UnsupportedBlend` | The renderer must refuse with a stable reason. |

`ShaderBlendWithDstRead` is not promoted until the required target-read,
intermediate, ordering, and validation rules are accepted for that route.

## `GPUColorPlan`

`GPUColorPlan` defines color representation for a draw, layer, filter
intermediate, or target write.

It records:

- source color-space tag;
- working-space tag;
- target color-space tag;
- transfer-function expectations;
- alpha type: opaque, premultiplied, unpremultiplied, or unknown;
- whether premul conversion is required;
- channel encoding and numeric representation;
- color-filter chain placement;
- coverage multiplication convention;
- target write convention.

The plan must preserve existing Kanvas reference behavior unless an accepted
target update changes the color contract. Any lossy conversion must be
diagnosed before promotion.

## Target State

`GPUTargetState` is the render-target state contribution used by pass planning
and `GPURenderPipelineKey`.

It includes:

- color attachment format;
- depth/stencil attachment format when used;
- sample count or coverage mode;
- load, clear, discard, and store operations;
- blend attachment state;
- write mask;
- resolve target facts;
- target color-space and alpha assumptions;
- usage flags needed for later sampling, copying, or presentation.

Target state belongs in pipeline and pass identity when it affects GPU
validity or behavior. Clear color values are pass values unless they alter
pipeline validity.

## Destination Reads

Destination reads are legal only through explicit routes:

- accepted fixed-function blend state that does not expose destination as a
  sampled input;
- accepted isolated layer composite where the source layer and destination are
  separate resources;
- accepted filter or shader route that reads a previous target copy or
  intermediate texture with validated ordering.

The renderer must refuse when a draw requires sampling the active color
attachment and no accepted intermediate route exists. It must not rely on
undefined read/write attachment behavior.

## Layer And Filter Interaction

`GPULayerPlan` may require a `GPUBlendPlan` and `GPUColorPlan` for:

- layer source rendering;
- filter intermediate production;
- filter node execution;
- final composite into parent;
- direct-to-parent elision proof.

Layer elision is legal only when the blend and color plans prove equivalence.
If layer alpha, color-space conversion, destination reads, or blend behavior
would change, the planner must keep the isolated layer or refuse.

## Material Interaction

Material descriptors may contribute:

- source color;
- gradient color stops;
- image sampling color facts;
- color-filter chain identity;
- blender identity when the blend is shader-owned;
- runtime-effect color behavior when registered.

`MaterialKey` must include the material-owned pieces that affect WGSL code or
layout. `GPUBlendPlan`, `GPUColorPlan`, and `GPUTargetState` carry target and
composite facts outside `MaterialKey`.

## Diagnostics

Blend and color diagnostics must include:

- blend mode;
- plan kind;
- source opacity;
- destination-read requirement;
- fixed-function state when used;
- shader blend module when used;
- layer isolation requirement;
- source, working, and target color-space tags;
- alpha and premul convention;
- target format and write mask;
- selected route or refusal reason.

Stable reason-code examples:

- `unsupported.blend.mode_unimplemented`
- `unsupported.blend.dst_read_requires_intermediate`
- `unsupported.blend.fixed_function_unavailable`
- `unsupported.blend.shader_route_unvalidated`
- `unsupported.color.space_conversion_unimplemented`
- `unsupported.color.alpha_type_unknown`
- `unsupported.color.premul_conversion_unvalidated`
- `unsupported.target.format_blend_incompatible`
- `unsupported.target.write_mask_unavailable`

## Validation Requirements

Promoted blend/color behavior requires:

- canonical `GPUBlendPlan` and `GPUColorPlan` dumps;
- key determinism for target-state and blend-state contributions;
- CPU reference or explicit refusal evidence for each promoted blend mode;
- GPU evidence for fixed-function and shader blend routes;
- negative tests for unsupported destination-read modes;
- layer-elision tests that prove equivalence or refusal;
- PM-visible counts by blend plan and color refusal reason.

## Non-Goals

- Do not infer blend support from WebGPU fixed-function state alone.
- Do not sample an active color attachment without an accepted route.
- Do not encode target attachment state in `MaterialKey`.
- Do not silently drop color-space or premul conversions.
- Do not use CPU-rendered textures to implement unsupported blend modes.
