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
Material-facing source composition, including `GPUShaderBlendSourcePlan`, is
defined in `31-material-source-paint-pipeline.md`. Final target blending and
destination-read requirements remain owned by this spec and
`20-destination-read-strategy.md`.

`GPURenderPipelineKey` owns executable render state, including target format,
blend state, depth/stencil state, sample count, and compatible WGSL module
identity.

`GPUBlendPlan` and `GPUColorPlan` bridge material, layer, and target facts so
the renderer can decide whether a draw can execute natively, needs an isolated
layer or texture read, or must refuse.

Detailed color-management descriptors, value specs, ICC/CICP/profile metadata,
transfer/gamut transforms, working-space selection, gradient interpolation,
runtime color uniforms, HDR/gainmap policy, and final store behavior are
defined in `29-color-management-pipeline.md`. `GPUColorPlan` consumes those
facts for a specific draw, layer, filter, blend, or target write.
Detailed layer/saveLayer execution, including layer target planning,
initialization, source filtering, restore composite, direct-to-parent elision,
and layer ordering, is defined in `28-layer-savelayer-execution.md`.
The detailed destination-read contract is defined in
`20-destination-read-strategy.md`. `GPUBlendPlan` declares the requirement;
`GPUDestinationReadPlan` defines bounds, strategy, copy/intermediate resources,
barriers, payload bindings, budgets, diagnostics, and validation gates.
Common destination-read coordinate mapping, bounds proof, and rounding policy
is defined in `30-coordinate-transform-bounds-policy.md`.
Clip coverage, stencil, mask, and shader-mask constraints are defined in
`24-clip-stencil-mask-pipeline.md`; they may constrain render-step selection,
coverage multiplication, target state, or destination-read requirements but do
not become material identity.

## `GPUBlendPlan`

`GPUBlendPlan` is the explicit blend decision for one draw, layer composite, or
filter composite.

It records:

- blend mode identity;
- source opacity and alpha classification;
- destination-read requirement;
- `GPUDestinationReadPlan` reference when shader blend, coverage blend, layer
  composite, or filter behavior needs previous destination pixels;
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
| `ShaderBlendWithDstRead` | The blend requires destination color as an input and therefore requires an accepted `GPUDestinationReadPlan`. |
| `LayerCompositeBlend` | The blend is applied while compositing an isolated `GPULayerPlan`. |
| `UnsupportedBlend` | The renderer must refuse with a stable reason. |

`ShaderBlendWithDstRead` is not promoted until the required target-copy,
existing-intermediate, layer-isolation, ordering, payload, and validation rules
from `20-destination-read-strategy.md` are accepted for that route.

## `GPUColorPlan`

`GPUColorPlan` defines color representation for a draw, layer, filter
intermediate, or target write.

It records:

- `GPUColorManagementPlan` and `GPUColorValueSpec` references when color
  behavior is profile, precision, transfer, gamut, interpolation, HDR, gainmap,
  runtime-uniform, or store dependent;
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
Detailed conversion and refusal rules are owned by
`29-color-management-pipeline.md`.
Filter DAG color behavior, including whether a color filter folds into
`MaterialKey` or executes as a `GPUFilterNodePlan`, is governed by
`23-filter-effect-pipeline.md`.
Clip coverage multiplication and mask sampling behavior is governed by
`24-clip-stencil-mask-pipeline.md`; `GPUColorPlan` records the accepted alpha
and coverage convention for the final target write.
For image and bitmap sources, encoded/source profile handling, ICC/CICP
metadata, bit depth, HDR metadata, orientation-adjacent color facts, and
premul/unpremul conversion before upload are planned by
`22-image-bitmap-codec-pipeline.md`. `GPUColorPlan` records how the prepared
sampled image interacts with the material, layer, and target color contract.

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
attachment and no accepted target-copy, existing-intermediate, or
layer-isolation route exists. It must not rely on undefined read/write
attachment behavior.
Target-copy and intermediate texture routes must follow
`20-destination-read-strategy.md` and `18-texture-image-ownership.md`: the
sampled resource must have a `GPUTargetTextureDescriptor`, valid usage flags,
compatible generation, and a separate texture/view from the active attachment
being written.

## Layer And Filter Interaction

`GPULayerPlan` may require a `GPUBlendPlan` and `GPUColorPlan` for:

- layer source rendering;
- filter intermediate production;
- filter node execution;
- final composite into parent;
- direct-to-parent elision proof.

`GPULayerCompositePlan` from `28-layer-savelayer-execution.md` consumes these
plans for restore/composite. It records whether the layer can use
fixed-function blend, a shader composite with a destination-read plan, an
existing intermediate, or refusal.

Layer elision is legal only when the blend and color plans prove equivalence.
If layer alpha, color-space conversion, destination reads, or blend behavior
would change, the planner must keep the isolated layer or refuse.

## Material Interaction

Material descriptors may contribute:

- source color;
- gradient color stops;
- image sampling color facts, with decode/profile/orientation facts governed
  by `22-image-bitmap-codec-pipeline.md`;
- filter DAG color facts, with node placement and material-fold equivalence
  governed by `23-filter-effect-pipeline.md`;
- text/glyph fill, coverage, SDF, bitmap, color glyph, and SVG glyph material
  facts when routed through `21-text-glyph-pipeline.md`;
- color-filter chain identity;
- blender identity when the blend is shader-owned;
- runtime-effect color behavior when registered.

`MaterialKey` must include the material-owned pieces that affect WGSL code or
layout. `GPUBlendPlan`, `GPUColorPlan`, and `GPUTargetState` carry target and
composite facts outside `MaterialKey`.
Text atlas coordinates, glyph IDs, text atlas generations, `GPUTextBinding`
values, and upload tokens are not material facts. Color glyph composites that
need prior destination pixels must carry `GPUDestinationReadPlan`.

## Diagnostics

Blend and color diagnostics must include:

- blend mode;
- plan kind;
- source opacity;
- destination-read requirement;
- destination-read plan ID, strategy, bounds, and refusal reason when present;
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
- `GPUDestinationReadPlan` fixture references for shader-destination-read
  refusal and any promoted target-copy/intermediate route;
- layer-elision tests that prove equivalence or refusal;
- PM-visible counts by blend plan and color refusal reason.

## Non-Goals

- Do not infer blend support from WebGPU fixed-function state alone.
- Do not sample an active color attachment without an accepted route.
- Do not encode target attachment state in `MaterialKey`.
- Do not silently drop color-space or premul conversions.
- Do not use CPU-rendered textures to implement unsupported blend modes.

## Multisample Render Target

MSAA (multisample anti-aliasing) is a `TargetNative` route when the adapter
and target format support it. The renderer declares multisample state as part of
`GPUTargetState` and `GPURenderPipelineKey`.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUMultisamplePlan` | Accepted multisample state: sample count (1, 4, 8), sample mask, alpha-to-coverage enable, and resolve target. |
| `GPUMultisampleResolvePlan` | Resolve operation: multisample target resolved to single-sample target via WGPU resolve command, custom WGSL resolve, or compute resolve. |
| `GPUMultisampleTargetDescriptor` | Multisample texture descriptor: sample count, format compatibility, usage flags (RENDER_ATTACHMENT + texture binding for custom resolve). |
| `GPUMultisampleDiagnostic` | Refusal for unsupported sample count, incompatible target format, missing resolve target, or adapter sample-count limit exceeded. |

### Target State Integration

`GPUTargetState` is extended with:
- `sampleCount: Int` (default 1 for non-MSAA).
- `multisamplePlan: GPUMultisamplePlan?` (null when sampleCount=1).

`GPURenderPipelineKey` includes `sampleCount` as a `PipelineStateAffecting` axis.

### Route Selection

```
Rect fill with MSAA
  -> GPUTargetState with sampleCount = 4
  -> GPUMultisamplePlan with resolve target as single-sample surface
  -> GPURenderPipelineKey includes sampleCount
  -> GPU executes render pass with multisample attachment
  -> Resolve to single-sample surface after render pass
```

### Acceptance Gates

- At least one filled rect rendered at 4x MSAA and resolved, compared to CPU supersampled reference (PSNR threshold).
- 8x MSAA accepted or refused based on adapter query.
- Alpha-to-coverage tested with partially transparent geometry.
- Stable refusal for MSAA on non-renderable formats (depth-only, stencil-only).
- MSAA resolve does not corrupt color values at polygon edges.
