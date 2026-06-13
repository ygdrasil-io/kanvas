# Draw Family Support Matrix

Status: Draft
Date: 2026-06-13

## Purpose

Define the target support and refusal matrix for the GPU renderer draw
families. This matrix describes the full technical target. It is not an
implementation order, release promise, or shortcut around the evidence gates in
`07-validation-conformance.md`.

Every family below must resolve to one of:

- `TargetNative`: intended to become a `GPUNative` route.
- `TargetPrepared`: intended to use typed `CPUPreparedGPU` artifacts consumed
  by the GPU.
- `ReferenceOnly`: CPU oracle or test evidence only, not product rendering.
- `RefuseRequired`: explicit stable refusal required.
- `DependencyGated`: support depends on another accepted spec or external
  delivery such as font, codec, or filter infrastructure.
- `FutureResearch`: target direction is recognized but not yet accepted.

`TargetNative` and `TargetPrepared` still require validation before support can
be claimed.

## Matrix Columns

Each family records:

- target maturity;
- target route;
- GPU primitive or plan;
- material or semantic plan;
- geometry/coverage model;
- allowed `CPUPreparedGPU` artifacts;
- required spec contracts;
- required validation evidence;
- stable refusal examples;
- implementation-slice notes.

Implementation-slice notes are sequencing hints only. They must not narrow the
target contracts.

## Target Matrix

| Family | Maturity | Target route | GPU primitive or plan | Material / plan | Coverage / artifact model |
|---|---|---|---|---|---|
| Rect fill | `TargetNative` | `GPUNative` | Render pass, rect render step, sortable draw layer | `MaterialKey` | Analytic rect coverage; no CPU artifact. |
| Rounded-rect fill | `TargetNative` | `GPUNative` | Render pass, rrect render step | `MaterialKey` | Analytic or segmented rrect coverage; no CPU artifact unless later evidence requires a typed mask route. |
| Rect/rrect stroke | `TargetNative` | `GPUNative` preferred | Render pass, stroke render step | `MaterialKey` | Analytic stroke coverage for bounded joins/caps; refusals for unsupported stroke style. |
| Path fill | `TargetPrepared` plus future native | `CPUPreparedGPU` initially, `GPUNative` when proven | Render pass sampling coverage or stencil/cover route | `MaterialKey` | `PathAtlasArtifact`, `CoverageMaskArtifact`, or future GPU stencil/compute coverage governed by `19-path-coverage-atlas-strategy.md`. |
| Path stroke | `TargetPrepared` plus future native | `CPUPreparedGPU` initially, `GPUNative` when proven | Render pass with prepared geometry/mask or future stencil/cover | `MaterialKey` | `PrecomputedGeometryArtifact`, `PathAtlasArtifact`, `CoverageMaskArtifact`; atlas routes governed by `19-path-coverage-atlas-strategy.md`. |
| Clip rect | `TargetNative` | `GPUNative` | Scissor, depth/stencil, or analytic clip facts | none or `GPULayerPlan` context | Captured clip facts in `NormalizedDrawCommand`; no CPU artifact. |
| Clip rrect/path | `TargetPrepared` | `CPUPreparedGPU` or `GPUNative` by strategy | Stencil/depth, coverage mask, or path atlas | none or `GPULayerPlan` context | `CoverageMaskArtifact` or `PathAtlasArtifact` governed by `19-path-coverage-atlas-strategy.md`; stable refusal for unsupported stack interactions. |
| Image rect | `TargetNative` plus prepared upload | `GPUNative` or `CPUPreparedGPU` upload | Texture sampling render pass | `MaterialKey` image source | GPU-native texture resource, or `UploadedTextureArtifact` when CPU prepares pixels. |
| Bitmap/image decode | `DependencyGated` | `CPUPreparedGPU` upload when accepted | Upload then texture sampling | `MaterialKey` image source | `UploadedTextureArtifact`; codec/color conversion policy must be accepted separately. |
| Text/glyph run | `DependencyGated` | `CPUPreparedGPU` initially | Glyph atlas sampling render pass | `MaterialKey` text/glyph material when needed | `GlyphAtlasArtifact`; font/shaping/glyph ownership remains dependency-gated. |
| Vertices | `FutureResearch` | `GPUNative` expected | Render pass with vertex/index buffers | `MaterialKey` or per-vertex color material | GPU buffers; possible `PrecomputedGeometryArtifact` for CPU-packed vertices. |
| Layer/saveLayer | `TargetNative` with refusals | `GPUNative` render/composite, sometimes `RefuseDiagnostic` | `GPULayerPlan`, offscreen target, parent composite, `GPUDestinationReadPlan` when parent destination is observed | `GPULayerPlan` with optional `GPUFilterPlan` | Offscreen GPU resources; no untyped CPU fallback. |
| Image filter DAG | `DependencyGated` | `GPUNative`, `CPUPreparedGPU`, or refusal by node | `GPUFilterPlan`, render/compute passes, intermediates, `GPUDestinationReadPlan` for backdrop/destination reads | `GPUFilterPlan` | Intermediate GPU textures; `FilterIntermediateArtifact` only when validated. |
| Runtime effect | `DependencyGated` | `GPUNative` only for registered descriptors | Render pass or compute where descriptor permits | Registered descriptor contributes to `MaterialKey` or compute program | No arbitrary SkSL; refusal for unregistered effects. |
| Color filter | `TargetNative` | `GPUNative` | WGSL material fragment | `MaterialKey` | No CPU artifact; refusal for unsupported chains. |
| Blend mode | `TargetNative` for selected modes | `GPUNative` or refusal | Fixed blend state or shader blend path with `GPUDestinationReadPlan` when needed | `MaterialKey`, `GPUBlendPlan`, `GPUColorPlan`, and `GPURenderPipelineKey` | Destination-read strategy from `20-destination-read-strategy.md`; refusal for unsupported dst-dependent modes. |
| Clear/discard | `TargetNative` | `GPUNative` | Pass load/clear/discard ops | none | Target-state operation, not material rendering. |

## Required Evidence By Family

### Rect And Rounded Rect

Evidence must include:

- normalized command dumps;
- `GPUDrawAnalysis` records;
- `SortKey` preimages;
- `MaterialKey` preimages for solid and linear-gradient materials;
- `GPURenderPipelineKey` preimages;
- complete WGSL module validation through `wgsl4k`;
- culling and non-culling tests;
- route diagnostics.

This is the accepted first implementation vertical slice after specs are
complete.

### Path Fill And Stroke

Evidence must include:

- path identity and bounds diagnostics;
- stroke style diagnostics for width, cap, join, miter, dash, and transform;
- artifact key preimages when `CPUPreparedGPU` is used;
- `GPUPathAtlasKey`, `GPUCoverageAtlasKey`, atlas entry, generation,
  retry/split, upload, eviction, or geometry budget diagnostics as defined in
  `19-path-coverage-atlas-strategy.md`;
- CPU oracle comparison or stable refusal;
- GPU evidence before support claims.

Unsupported perspective paths, complex dashes, pathological edge budgets, and
unsupported fill/stroke combinations must refuse with stable reasons.

### Clip Families

Evidence must include:

- captured clip facts;
- stack interaction diagnostics;
- proof that ordering, stencil, depth, or mask state is preserved;
- coverage-mask atlas key, generation, retry, upload, and budget diagnostics
  when `CoverageMaskArtifact` or `PathAtlasArtifact` is used;
- culling refusal when clips make coverage ambiguous;
- stable refusals for complex difference/intersect stacks until supported.

### Image And Bitmap

Evidence must include:

- texture provenance: GPU-native resource vs `UploadedTextureArtifact`;
- `GPUImageSourceDescriptor`, `GPUTextureOwnershipPlan`,
  `GPUTextureViewDescriptor`, and `GPUSamplerDescriptor` dumps;
- sampler, tile mode, mip policy, and color conversion facts;
- usage flags, resource owner scope, and device/target/surface/upload
  generation facts;
- upload format and row-stride diagnostics when CPU prepares pixels;
- imported texture refusal evidence when owner, usage, lifetime, or release
  policy is not accepted;
- active attachment sampling refusal evidence;
- CPU/GPU sample evidence or explicit refusal.

Decoded or transformed CPU pixels must not be hidden as normal GPU resources.

### Text And Glyphs

Evidence must include:

- dependency gate to font/shaping/glyph specs;
- glyph atlas artifact keys;
- strike, subpixel, transform, and color-font facts when relevant;
- stable refusal for unsupported shaping, font fallback, emoji, or color font
  behavior.

### Layers And Filters

Evidence must include:

- `GPULayerPlan` dumps;
- `GPUFilterPlan` dumps;
- intermediate resource keys;
- render/compute pipeline keys for filter nodes;
- direct-to-parent and offscreen decisions;
- destination-read plan, strategy, bounds, and target/intermediate resource
  diagnostics when parent destination or backdrop is observed;
- culling and layer-elision negative tests;
- refusal for unsupported filter DAG nodes or layer composite semantics.

### Runtime Effects

Evidence must include:

- registered descriptor ID;
- uniform and child binding reflection;
- Kotlin/CPU oracle behavior;
- WGSL validation;
- stable refusal for arbitrary Skia/SkSL input and unregistered descriptors.

## Stable Refusal Taxonomy

Families must reuse route-policy reason codes where possible and add specific
codes only when they identify an actionable unsupported condition.

Examples:

- `unsupported.geometry.path_edge_budget`
- `unsupported.atlas.entry_too_large`
- `unsupported.atlas.in_use_try_again_limit`
- `unsupported.atlas.generation_stale`
- `unsupported.stroke.dash_complex`
- `unsupported.clip.stack_difference_path`
- `unsupported.image.codec_missing`
- `unsupported.image.tile_mode`
- `unsupported.texture.ownership_missing`
- `unsupported.texture.import_unvalidated`
- `unsupported.texture.active_attachment_sampled`
- `unsupported.text.shaping_dependency`
- `unsupported.text.color_font_dependency`
- `unsupported.layer.destination_read`
- `unsupported.destination_read.strategy_unaccepted`
- `unsupported.destination_read.active_attachment_sampled`
- `unsupported.destination_read.copy_budget_exceeded`
- `unsupported.filter.node_unimplemented`
- `unsupported.runtime_effect.unregistered_descriptor`
- `unsupported.blend.dst_dependent_mode`

## Non-Goals

- Do not claim support from appearing in this matrix.
- Do not use this matrix as implementation order.
- Do not collapse dependency-gated families into generic CPU fallback.
- Do not add a family without route, evidence, and refusal policy.
- Do not treat `FutureResearch` as accepted support.
