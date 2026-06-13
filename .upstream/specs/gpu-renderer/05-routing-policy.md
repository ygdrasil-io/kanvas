# Routing Policy

Status: Draft
Date: 2026-06-13

## Purpose

Define route selection for normalized draw commands in the GPU-first renderer.

The policy is explicit: prefer GPU-native execution, allow CPU preparation only
when the GPU consumes the prepared artifact, use CPU reference for evidence,
and refuse unsupported routes with stable diagnostics.

## Route Kinds

### `GPUNative`

The draw is executed by GPU render or compute work without CPU rasterization of
the draw result.

Examples:

- analytic rect coverage;
- gradient material WGSL;
- image sampling through GPU texture bindings;
- GPU-native, imported, surface-leased, offscreen, or atlas-backed texture
  sampling when `GPUTextureOwnershipPlan` accepts ownership, usage, and
  generation;
- text outline, color glyph, bitmap glyph, SVG glyph, A8 atlas, or SDF atlas
  rendering when `21-text-glyph-pipeline.md` accepts the route and resources;
- scissor, geometric clip intersection, analytic clip coverage, stencil clip
  producer-consumer work, or registered clip shader GPU work when
  `24-clip-stencil-mask-pipeline.md` accepts the route;
- stencil/depth clip preparation on GPU;
- GPU tessellation or generated geometry buffers when owned by GPU-side logic.
- destination-read target snapshots, existing intermediates, and layer
  isolation when accepted by `20-destination-read-strategy.md`.

### `CPUPreparedGPU`

The CPU prepares a typed artifact that the GPU consumes.

`CPUPreparedGPU` is broad as a route kind, but every selected route must name a
registered artifact type from `CPUPreparedGPUArtifactRegistry`. The route is
not valid if it can only say "prepare on CPU" without identifying the artifact
key, lifetime, budget, and GPU consumer.

Examples:

- `CoverageMaskArtifact`;
- `PathAtlasArtifact`;
- `GlyphAtlasArtifact`;
- `SDFGlyphAtlasArtifact`;
- `GlyphUploadPlan`;
- `OutlineGlyphPlan`;
- `ColorGlyphPlan`;
- `BitmapGlyphPlan`;
- `SVGGlyphPlan`;
- `UploadedTextureArtifact`;
- `PrecomputedGeometryArtifact`;
- `FilterIntermediateArtifact` only when `23-filter-effect-pipeline.md`
  validates the intermediate lifecycle and route.

This route is Graphite-like in spirit for small-path or raster-atlas behavior,
but it is not a full CPU fallback. The final draw still goes through GPU
composition or GPU command submission.

Path and coverage atlas routes are governed by
`19-path-coverage-atlas-strategy.md`. A route may select
`CPUPreparedGPU(PathAtlasArtifact)` for reusable path coverage or
`CPUPreparedGPU(CoverageMaskArtifact)` for clip/operation-specific coverage
only when the artifact key, atlas policy, budget, generation, mutation plan,
and GPU consumer are all accepted.
Clip execution routes are governed by `24-clip-stencil-mask-pipeline.md`. A
route may select `CPUPreparedGPU(CoverageMaskArtifact)` only for a bounded clip
coverage mask consumed by GPU work. It must not CPU-render the clipped draw,
layer, filter, or scene result.

Text and glyph routes are governed by `21-text-glyph-pipeline.md`. A route may
select `CPUPreparedGPU(GlyphAtlasArtifact)`,
`CPUPreparedGPU(SDFGlyphAtlasArtifact)`, or another registered text artifact
only when artifact keys, text atlas/page generation, upload plans, instance
buffer plans, WGSL text render steps, and GPU consumers are all accepted.

Image and bitmap routes are governed by
`22-image-bitmap-codec-pipeline.md`. A route may select
`CPUPreparedGPU(UploadedTextureArtifact)` for encoded images, animated image
frames, or already-decoded CPU image pixels only when codec selection, decode
request, frame selection, color/profile conversion, orientation, pixel layout,
mip policy, upload artifact key, budget, and GPU consumer are all accepted.

Filter and effect routes are governed by `23-filter-effect-pipeline.md`. A
filter node may select `GPUNative` render, compute, or copy routes, or
`CPUPreparedGPU(FilterIntermediateArtifact)` only when graph identity, node
descriptor, bounds, crop, tile, sample policy, intermediate descriptor,
runtime-effect descriptor when present, budget, and GPU consumer are all
accepted.

GPU-native textures, render targets, swapchain/surface textures, and imported
GPU handles remain normal `GPUResourceProvider` resources. Their ownership,
view, sampler, usage, and generation policy is defined in
`18-texture-image-ownership.md`. A texture is an `UploadedTextureArtifact` only
when CPU work decodes, converts, repacks, color-converts, composes animation
frames, tiles, or mip-prepares pixels before upload.

### `CPUReferenceOnly`

The CPU computes oracle behavior for tests, diffs, reports, or local
debugging. This route is not used as a production fallback for unsupported GPU
features.

### `RefuseDiagnostic`

The renderer refuses the command with a stable reason because no supported GPU
or CPU-prepared GPU route exists.

Refusal is a valid product behavior when it is explicit, deterministic, and
covered by tests or PM evidence.

## Route Precedence

Route selection follows this order:

1. Validate normalized command invariants.
2. Try a supported `GPUNative` route.
3. Try a supported `CPUPreparedGPU` route by resolving a typed registered
   artifact and its GPU consumer.
4. Return `RefuseDiagnostic`.

`CPUReferenceOnly` is invoked by validation/evidence harnesses, not by product
route fallback.

## Artifact Routing Contract

A `CPUPreparedGPU` decision must produce:

- artifact type;
- stable artifact key preimage and compact hash;
- artifact lifetime class;
- invalidation facts;
- memory and upload budget decision;
- GPU consumer step or pass;
- resource-provider ownership facts;
- diagnostic reason for selection or refusal.

The route must refuse when:

- the artifact type is not registered;
- a required key fact is unavailable or nondeterministic;
- the artifact would exceed its stable budget policy;
- the artifact would be stale for the current device, atlas, target, or
  recording generation;
- the filter intermediate has not been validated by
  `23-filter-effect-pipeline.md`;
- CPU preparation would produce a complete rendered draw result instead of a
  typed GPU-consumed artifact.

## No CPU-Rendered Texture Compatibility

The renderer must not silently render an unsupported command fully on CPU and
composite it as though it were GPU support.

`CPUPreparedGPU` cannot be used as a compatibility escape hatch for broad
unsupported features. It is allowed only when the CPU output is one of the
registered artifacts and the final rendering operation remains a GPU route.

The current target forbids a product route that CPU-renders a complete
unsupported draw, layer, filter result, or fallback scene into a texture for
GPU composition. This remains forbidden even if the route is explicit,
diagnosed, or gated.

If a future target reopens this decision, it must do so by replacing this
policy in a new accepted target/spec. Implementation tickets under this pack
must not introduce such a route.

## Route Inputs

Route selection may use:

- command family;
- geometry complexity;
- transform facts;
- clip classification;
- clip descriptor, effective element, scissor, analytic, stencil, mask, shader,
  budget, and ordering facts for routes governed by
  `24-clip-stencil-mask-pipeline.md`;
- material classification;
- target format;
- destination-read requirements;
- destination-read strategy, bounds, target generation, and pass-split legality
  for routes that observe previous destination pixels;
- `GPUCapabilities`;
- resource availability;
- `CPUPreparedGPUArtifactRegistry` support;
- atlas budget;
- atlas entry dimensions, area, generation, policy, use-token state, and
  retry/split legality for path or coverage atlas routes;
- text representation, text route, text atlas/page generation, upload plan,
  instance buffer budget, and glyph artifact registration for text/glyph routes;
- artifact memory/upload budget;
- feature gates;
- conformance maturity.

Route selection must not use nondeterministic facts such as object identity,
current cache occupancy without a stable budget policy, implicit full-CPU
fallback availability, or hidden environment flags.

## Diagnostics

Every command must produce a route diagnostic in debug/conformance modes.

Diagnostic fields:

- command ID;
- command family;
- selected route or refusal;
- stable reason code;
- material key or material refusal;
- pipeline key or pipeline refusal;
- render step or render-step refusal;
- capability facts that affected selection;
- CPU-prepared artifact type, key hash, lifetime, invalidation facts, budget
  decision, and registry refusal when used;
- fallback/refusal source.

Stable reason-code examples:

- `unsupported.clip.complex_stack`
- `unsupported.clip.stack_too_deep`
- `unsupported.clip.analytic_unsupported`
- `unsupported.clip.stencil_ordering_illegal`
- `unsupported.clip.mask_budget_exceeded`
- `unsupported.clip.shader_unregistered`
- `unsupported.material.unregistered_runtime_effect`
- `unsupported.wgsl.validation_error`
- `unsupported.pipeline.capability_missing`
- `unsupported.atlas.capacity`
- `unsupported.atlas.entry_too_large`
- `unsupported.atlas.in_use_try_again_limit`
- `unsupported.atlas.generation_stale`
- `unsupported.artifact.unregistered_type`
- `unsupported.artifact.key_nondeterministic`
- `unsupported.artifact.budget_exceeded`
- `unsupported.artifact.stale_generation`
- `unsupported.texture.cpu_preparation_missing`
- `unsupported.texture.ownership_missing`
- `unsupported.texture.import_unvalidated`
- `unsupported.texture.active_attachment_sampled`
- `unsupported.image.codec.unregistered`
- `unsupported.image.codec.selection_nondeterministic`
- `unsupported.image.decode.invalid_input`
- `unsupported.image.animation.required_frame_missing`
- `unsupported.image.color.conversion_unvalidated`
- `unsupported.image.upload.budget_exceeded`
- `unsupported.destination_read.strategy_unaccepted`
- `unsupported.destination_read.active_attachment_sampled`
- `unsupported.destination_read.pass_split_illegal`
- `unsupported.text.artifact_unregistered`
- `unsupported.text.atlas_generation_stale`
- `unsupported.text.upload_plan_missing`
- `unsupported.text.instance_buffer_budget_exceeded`
- `unsupported.texture.device_generation_stale`
- `unsupported.filter.intermediate_unvalidated`
- `unsupported.filter.bounds_unbounded`
- `unsupported.filter.runtime_effect_unregistered`
- `unsupported.filter.CPU_rendered_texture_forbidden`
- `unsupported.geometry.perspective_path`
- `unsupported.resource.device_lost`

## Promotion Gates

A route may be promoted only when:

- command normalization tests pass;
- route diagnostics are deterministic;
- key preimages are stable;
- typed CPU-prepared artifact keys, lifetimes, invalidation rules, budgets, and
  diagnostics are stable when `CPUPreparedGPU` is used;
- WGSL validation passes when WGSL is used;
- CPU reference evidence or explicit refusal exists;
- GPU evidence exists for GPU claims;
- PM/report output identifies route counts and refusals.

## Non-Goals

- Do not infer support from missing diagnostics.
- Do not treat CPU reference as production rendering.
- Do not route broad unsupported features through one generic compatibility
  path.
- Do not treat untyped CPU preparation as a valid `CPUPreparedGPU` route.
- Do not treat CPU-rendered texture composition as `CPUPreparedGPU`.
- Do not weaken refusal diagnostics to make dashboards look greener.
