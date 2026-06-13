# First Implementation Slice Contract

Status: Draft
Date: 2026-06-13

## Purpose

Define the first implementation slice after the GPU renderer specs are accepted.

The slice is intentionally narrow: rect and rounded-rect geometry with solid
color and linear-gradient materials, isolated inside `:gpu-renderer` first. It
is a vertical proof of the architecture contracts, not a reduction of the
target scope.

## Slice Scope

The first slice includes:

- `FillShape` commands for rectangles;
- `FillShape` commands for rounded rectangles;
- solid color material descriptors;
- linear-gradient material descriptors;
- captured transform, clip, layer, material, bounds, and ordering facts;
- `GPUNative` route selection;
- `RefuseDiagnostic` for unsupported variants;
- complete WGSL module assembly and validation;
- deterministic key and diagnostic dumps;
- isolated `:gpu-renderer` tests before `gpu-raster` integration.

The slice may include clear/discard tasks only when needed to test pass target
state. Clear/discard support remains governed by the draw-family matrix.

## Explicit Non-Scope

The first slice does not implement:

- path fill or stroke;
- glyphs or text, which remain governed by the target contract in
  `21-text-glyph-pipeline.md`;
- image decoding or bitmap upload;
- sampled image textures;
- runtime effects;
- image filters;
- complex saveLayer behavior;
- arbitrary blend modes;
- destination-read shader blending;
- destination copy snapshots, existing-intermediate destination reads, and
  destination-isolated layer composites;
- complex clip stacks;
- persistent path, glyph, or coverage atlases;
- default `gpu-raster` route activation.

Unsupported inputs must refuse deterministically. They must not use broad CPU
fallback, CPU-rendered texture compatibility, or hidden legacy rendering.

Path and coverage atlas routes remain governed by
`19-path-coverage-atlas-strategy.md` and are not promoted by this slice.
Destination-read routes remain governed by `20-destination-read-strategy.md`;
the first slice promotes only `NoDestinationRead` and the accepted
fixed-function blend subset.

## Required Contracts

The slice must exercise these contracts:

- `NormalizedDrawCommand`;
- `GPUDrawAnalysis`;
- `GPUOcclusionTracker` for conservative non-culling and at least one safe
  culling proof;
- `GPUDrawLayerPlanner` for the root target scope;
- `GPUDrawInvocation` and `GPUDrawInsertion` dumps for accepted draw steps;
- `GPURecording`;
- `GPUTaskList`;
- `GPUDrawPass`;
- `GPURenderStep`;
- `MaterialKey`;
- `GPUMaterialDictionary`;
- `WGSLSnippet`;
- `WGSLSnippetNode`;
- `GPUMaterialProgramID`;
- `WGSLFragment` and `WGSLModule`;
- `WGSLBindingLayout` and `WGSLPackingPlan`;
- `GPUPayloadGatherer`;
- `GPUPayloadGatherPlan`;
- `GPUMaterialPayload`;
- `GPUPayloadWritePlan`;
- `GPUPayloadBindingPlan`;
- `GPUPayloadUploadPlan`;
- `GPUUniformPayloadBlock`;
- `GPUUniformPayloadSlot`;
- `GPUDrawPayloadRef`;
- `GPURenderPipelineKey`;
- `GPUResourceProvider`;
- `GPUTargetTextureDescriptor` for the render attachment descriptor;
- `GPUExecutionContext` or a deterministic test double;
- `GPUBlendPlan`, `GPUColorPlan`, and `GPUTargetState`;
- route diagnostics;
- `GPUTelemetryLedger` for counters.

The implementation may use minimal internal representations, but the dumps and
tests must name these concepts so later slices do not have to reinterpret the
first slice's behavior.

## Accepted Geometry

Rect geometry is accepted when:

- bounds are finite;
- transform is identity, translate, scale, or affine without perspective;
- clip is wide-open, empty, or device rect;
- coverage can be represented by the accepted rect render step;
- target format and blend/color plan are supported.

Rounded-rect geometry is accepted when:

- radii are finite and normalized;
- degenerate radii have deterministic rect or refusal behavior;
- transform is accepted by the rrect render step;
- the render step can produce stable analytic or segmented coverage;
- unsupported radius, transform, or coverage cases refuse.

Perspective transforms, complex clips, inverse fills, path-backed rrects, and
unsupported stroke styles are outside this slice.

## Accepted Materials

Solid color is accepted when:

- color values are finite;
- alpha and premul conventions are known;
- color-space behavior is covered by `GPUColorPlan`;
- `GPUMaterialDictionary` resolves the solid source snippet;
- `GPUPayloadGatherer` writes the solid color payload through a validated
  payload write plan;
- uniform packing is validated against WGSL reflection.

Linear gradient is accepted when:

- endpoints are finite;
- stop order is normalized deterministically;
- tile mode is supported or refused;
- color interpolation behavior is explicit;
- `GPUMaterialDictionary` resolves the linear-gradient source snippet and
  local-coordinate requirements;
- gradient payload shape is accepted by the first-slice inline or
  `GPUGradientPayloadStore` route;
- uniform layout and WGSL fragment composition validate.

Unsupported color filters, blend helpers, runtime-effect children, gradient
types, or tile modes refuse with stable reasons.

## WGSL Requirements

The slice must generate or assemble complete WGSL modules for:

- rect solid;
- rect linear gradient;
- rrect solid;
- rrect linear gradient, unless the accepted rrect render step shares the same
  module contract with a different render-step key.

Each promoted module requires:

- deterministic source;
- deterministic `GPUMaterialAssemblyPlan`;
- complete module validation through `wgsl4k`;
- reflection dump;
- binding layout dump;
- packing plan dump;
- payload gather, write, binding, upload, and slot dumps;
- module hash;
- render pipeline key preimage.

Fragment-only validation is not sufficient.

## Diagnostics And Dumps

Every slice fixture must be able to dump:

- normalized command;
- analysis record;
- layer plan and draw layer assignment;
- route decision;
- material key;
- blend/color plan;
- WGSL module hash and reflection status;
- binding and packing plan hashes;
- render pipeline key;
- pass command;
- resource plan;
- telemetry counters.

Dumps must be deterministic across equivalent inputs.

## Test Fixture Set

Minimum isolated fixtures:

- solid rect identity transform;
- solid rect translated;
- solid rect clipped by device rect;
- adjacent compatible solid rect batching under the conservative planner;
- linear-gradient rect;
- rounded-rect solid;
- rounded-rect linear gradient;
- equivalent solid material descriptors producing the same
  `GPUMaterialProgramID`;
- equivalent solid payload values de-duplicating to the same
  `GPUUniformPayloadSlot`;
- empty clip producing discard or cull diagnostic;
- opaque covering rect culling a previous covered rect;
- unsupported perspective rect refusal;
- unsupported complex clip refusal;
- unsupported gradient tile mode refusal;
- unsupported destination-read shader blend refusal;
- WGSL validation failure fixture using an intentionally invalid test module;
- stale device-generation resource refusal using a test double;
- illegal active-attachment sampling refusal using target texture descriptors.
- unsupported path or complex clip atlas-route refusal when such commands enter
  the isolated module fixture set.

These fixtures are contract fixtures. They do not claim broad Skia GM coverage.

## Promotion Gate

The slice may be promoted from isolated tests to `gpu-raster` shadow
integration only when:

- all isolated contract fixtures pass;
- complete WGSL modules validate;
- key preimages and dumps are stable;
- route diagnostics are deterministic;
- readback, checksum, or explicit skipped GPU evidence exists for the GPU lane;
- telemetry counters are emitted;
- default legacy rendering remains unchanged;
- rollback to the legacy route remains possible.

Default route activation requires a later integration ticket and evidence
against legacy output. This slice alone does not switch production rendering.

## Non-Goals

- Do not treat the slice as the full renderer scope.
- Do not skip analysis, pass, ABI, blend/color, or telemetry contracts just
  because the geometry is simple.
- Do not activate the route by default inside `gpu-raster`.
- Do not claim support for paths, text, images, filters, or runtime effects.
- Do not create uploaded texture artifacts, import external textures, or sample
  image textures in this slice.
- Do not create `PathAtlasArtifact`, `CoverageMaskArtifact`, or path/coverage
  atlas textures as supported routes in this slice.
- Do not create destination copy textures, sample existing destination
  intermediates, or isolate layers as supported destination-read routes in this
  slice.
- Do not rely on CPU-rendered texture compatibility for unsupported variants.
