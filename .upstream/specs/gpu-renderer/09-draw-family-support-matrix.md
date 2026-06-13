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
| Path fill | `TargetPrepared` plus future native | `CPUPreparedGPU` initially, `GPUNative` when proven | Render pass sampling coverage, prepared geometry, tessellation, or stencil-cover route | `MaterialKey` plus `GPUGeometryPlan` | `PathAtlasArtifact`, `CoverageMaskArtifact`, `PrecomputedGeometryArtifact`, or future GPU tessellation/stencil/compute coverage governed by `25-path-stroke-geometry-pipeline.md` and `19-path-coverage-atlas-strategy.md`. |
| Path stroke | `TargetPrepared` plus future native | `CPUPreparedGPU` initially, `GPUNative` when proven | Render pass with prepared geometry/mask, stroke expansion, tessellation, or future stencil-cover | `MaterialKey` plus `GPUGeometryPlan` | `PrecomputedGeometryArtifact`, `PathAtlasArtifact`, `CoverageMaskArtifact`; geometry routes governed by `25-path-stroke-geometry-pipeline.md`, atlas routes by `19-path-coverage-atlas-strategy.md`. |
| Clip rect | `TargetNative` | `GPUNative` | `GPUClipPlan`, scissor, geometric intersection, analytic coverage, or stencil/mask when required | `GPUClipStackDescriptor`, `GPUClipBoundsPlan`, `GPUClipScissorPlan`, optional `GPULayerPlan` context | Captured clip facts from `NormalizedDrawCommand`; no CPU artifact for scissor/geometric/analytic routes. |
| Clip rrect/path | `TargetPrepared` plus native routes | `GPUNative`, `CPUPreparedGPU`, or refusal by strategy | `GPUClipPlan`, `GPUClipAnalyticPlan`, `GPUClipStencilPlan`, `GPUClipMaskPlan`, stencil/depth, coverage mask, or path atlas | `GPUClipStackDescriptor`, `GPUClipElementPlan`, `GPUClipBoundsPlan`, `GPUClipOrderingToken`, optional `GPULayerPlan` context | `CoverageMaskArtifact` or `PathAtlasArtifact` governed by `24-clip-stencil-mask-pipeline.md` and `19-path-coverage-atlas-strategy.md`; stable refusal for unsupported stack interactions. |
| Image rect | `TargetNative` plus prepared upload | `GPUNative` or `CPUPreparedGPU` upload | Texture sampling render pass | `MaterialKey` image source with `GPUImageSourceDescriptor` and, for encoded/CPU pixels, `GPUImagePipelinePlan` | GPU-native texture resource, or `UploadedTextureArtifact` from `22-image-bitmap-codec-pipeline.md` when CPU prepares pixels. |
| Bitmap/image decode | `TargetPrepared` with codec dependency gates | `CPUPreparedGPU` upload when accepted | Decode/prepare, upload, then texture sampling | `GPUImageDecodePlan`, `GPUImageColorDecodePlan`, `GPUImageOrientationPlan`, `GPUImageUploadPlan`, and `MaterialKey` image source | `UploadedTextureArtifact`; codec/color/animation policy governed by `22-image-bitmap-codec-pipeline.md`. |
| Animated image frame | `TargetPrepared` with codec dependency gates | `CPUPreparedGPU` per selected frame | Frame select/compose, upload, then texture sampling | `GPUAnimatedImagePlan`, `GPUImageFrameSelection`, `GPUImageFrameInfo`, `GPUImageUploadPlan`, and `MaterialKey` image source | Per-frame or composed-frame `UploadedTextureArtifact`; loop, disposal, blend, dirty rect, required-frame, cache, and upload scheduling governed by `22-image-bitmap-codec-pipeline.md`. |
| Text/glyph run | `DependencyGated` until pure Kotlin text artifacts and GPU evidence are promoted | `GPUNative` or `CPUPreparedGPU` by representation | Text render steps, atlas sampling, path/coverage route, texture sampling, or glyph composite route | `GPUTextRunPlan`, `GPUTextSubRunPlan`, `GPUTextBinding`, `MaterialKey` text/glyph material when needed | `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, `GlyphUploadPlan`, `OutlineGlyphPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, `SVGGlyphPlan`; routes governed by `21-text-glyph-pipeline.md`. |
| Vertices | `TargetNative` with prepared packing | `GPUNative` preferred, `CPUPreparedGPU` for typed buffer preparation | Render pass with vertex/index buffers, topology-specific render step, optional prepared buffer artifact | `MaterialKey` plus `GPUVerticesDescriptor`, `GPUPrimitiveColorPlan`, and `GPUPrimitiveBlendPlan` | GPU vertex/index buffers; possible `PrecomputedGeometryArtifact` for CPU-packed vertices governed by `26-draw-vertices-mesh-pipeline.md`. |
| Layer/saveLayer | `TargetNative` with refusals | `GPUNative` render/composite, sometimes `RefuseDiagnostic` | `GPULayerPlan`, `GPULayerExecutionPlan`, `GPULayerTargetPlan`, `GPULayerInitializationPlan`, `GPULayerCompositePlan`, `GPULayerTaskPlan`, and `GPUDestinationReadPlan` when parent destination or backdrop is observed | `GPULayerPlan` with optional `GPUFilterPlan`, `GPUBlendPlan`, and `GPUColorPlan` | Offscreen GPU resources governed by `28-layer-savelayer-execution.md`; bounds hints are not clips; no CPU-rendered full-layer fallback. |
| Image filter DAG | `TargetNative` plus dependency-gated nodes | `GPUNative`, `CPUPreparedGPU`, or refusal by node | `GPUFilterPlan`, `GPUFilterNodePlan`, render/compute/copy nodes, intermediates, `GPUDestinationReadPlan` for backdrop/destination reads | `GPUFilterGraphDescriptor`, `GPUFilterBoundsPlan`, `GPUFilterIntermediatePlan`, `GPUFilterRuntimeEffectPlan` when needed | Intermediate GPU textures; `FilterIntermediateArtifact` only when validated by `23-filter-effect-pipeline.md`. |
| Runtime effect | `DependencyGated` until registry descriptors and GPU evidence are promoted | `GPUNative` only for registered descriptors; `CPUReferenceOnly` for oracle evidence | Render pass, compute pass, material snippet, filter node, primitive blender, or future clip shader only where descriptor permits | `GPURuntimeEffectRegistry`, `GPURuntimeEffectDescriptor`, `GPURuntimeEffectRouteContract`, `MaterialKey`, `GPUFilterRuntimeEffectPlan`, or compute program | Descriptor ID/version, uniform schema, child slots, WGSL plan, CPU oracle, registry snapshot, and diagnostics governed by `27-registered-runtime-effects-registry.md`; no arbitrary SkSL/source string support. |
| Color management | `DependencyGated` beyond SDR sRGB lane | `GPUNative`, `CPUPreparedGPU` for typed pixel preparation, `CPUReferenceOnly` for oracle evidence, or `RefuseDiagnostic` | Color transform helpers, profile conversion, gradient interpolation, image/profile preparation, layer/store conversion, or target refusal | `GPUColorManagementPlan`, `GPUColorValueSpec`, `GPUColorConversionPlan`, `GPUColorTransformPlan`, `GPUColorStorePlan`, and `GPUColorDiagnostic` | ICC/CICP/profile, transfer/gamut, premul/unpremul, precision, F16, HDR, gainmap, runtime color uniforms, and store behavior governed by `29-color-management-pipeline.md`; no silent reinterpretation. |
| Coordinate / transform / bounds | `TargetRequired` for accepted GPU routes | `GPUNative`, `CPUReferenceOnly` for oracle evidence, or `RefuseDiagnostic` | Coordinate-space descriptors, transform chains, inverse plans, pixel-grid plans, conservative bounds proofs, rounding plans, clip reduction proofs, and payload facts | `GPUCoordinateSpace`, `GPUTransformPlan`, `GPUBoundsPlan`, `GPUBoundsProof`, `GPURoundingPlan`, `GPUCoordinatePayloadPlan`, and `GPUTransformDiagnostic` | Common transform/bounds behavior governed by `30-coordinate-transform-bounds-policy.md`; unknown bounds, unsupported perspective, singular inverses, and unsafe rounding refuse unless a specialized route proves acceptance. |
| Color filter | `TargetNative` | `GPUNative` | WGSL material fragment or filter render node | `MaterialKey` when folded, `GPUFilterColorPlan` inside filter DAGs | No CPU artifact; refusal for unsupported chains. |
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

- `GPUShapeDescriptor`, `GPUPathDescriptor`, `GPUStrokeDescriptor`,
  `GPUGeometryPlan`, `GPUGeometryRoute`, `GPUPathBoundsPlan`,
  `GPUStrokeExpansionPlan`, `GPUPreparedGeometryPlan`,
  `GPUGeometryRenderStepPlan`, and `GPUGeometryDiagnostic` dumps as defined in
  `25-path-stroke-geometry-pipeline.md`;
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

- captured `GPUClipStackDescriptor` and `GPUClipPlan` facts;
- `GPUClipElementPlan`, `GPUClipBoundsPlan`, `GPUClipScissorPlan`,
  `GPUClipAnalyticPlan`, `GPUClipStencilPlan`, `GPUClipMaskPlan`,
  `GPUClipOrderingToken`, and `GPUClipDiagnostic` dumps when touched;
- stack interaction diagnostics;
- proof that ordering, stencil, depth, mask, or shader-mask state is preserved;
- coverage-mask atlas key, generation, retry, upload, and budget diagnostics
  when `CoverageMaskArtifact` or `PathAtlasArtifact` is used;
- culling refusal when clips make coverage ambiguous;
- stable refusals for complex difference/intersect stacks until supported.

### Image And Bitmap

Evidence must include:

- `GPUImagePipelinePlan`, `GPUImageCodecRegistry`,
  `GPUImageCodecDescriptor`, `GPUImageDecodeRequest`,
  `GPUImageDecodePlan`, `GPUImageDecodeResult`,
  `GPUImageColorDecodePlan`, `GPUImageOrientationPlan`,
  `GPUImagePixelPlan`, `GPUImageMipmapPlan`, `GPUImageUploadPlan`,
  `GPUImageUploadArtifactKey`, and `GPUImageDiagnostic` dumps when encoded or
  CPU pixel image sources are used;
- `GPUAnimatedImagePlan`, `GPUImageFrameInfo`, and
  `GPUImageFrameSelection` dumps when animated inputs are used;
- texture provenance: GPU-native resource vs `UploadedTextureArtifact`;
- `GPUImageSourceDescriptor`, `GPUTextureOwnershipPlan`,
  `GPUTextureViewDescriptor`, and `GPUSamplerDescriptor` dumps;
- codec ID, version, implementation kind, capability, conformance tier, and
  nondeterminism policy;
- sampler, tile mode, mip policy, and color conversion facts;
- ICC/CICP/profile, EXIF/origin, alpha, premul/unpremul, bit depth, HDR,
  orientation, and tone-map/refusal facts where relevant;
- animation loop count, frame duration, dirty rect, disposal, blend, required
  prior frame, first-frame-still policy, frame cache, and upload scheduling
  facts where relevant;
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

- dependency gate to `.upstream/specs/pure-kotlin-text/` for font, shaping,
  paragraph, glyph artifact, color glyph, and handoff contracts;
- `GPUTextRunPlan`, `GPUTextSubRunPlan`, `GPUTextRoute`,
  `GPUTextRenderStep`, `GPUTextAtlasPlan`, `GPUTextBinding`, and
  `GPUTextDiagnostic` dumps;
- glyph atlas artifact keys, SDF artifact keys, upload plans, atlas page
  generations, entry refs, instance buffer plans, and upload-before-sample
  ordering;
- strike, transform, SDF, subpixel, palette, color-font, bitmap, SVG, and emoji
  facts when relevant;
- WGSL validation and binding ABI evidence for promoted text render steps;
- stable refusal for unsupported shaping, font fallback, emoji, color font,
  SDF, bitmap, SVG, LCD, atlas, upload, or GPU route behavior.

### Layers And Filters

Evidence must include:

- `GPULayerPlan` dumps;
- `GPULayerExecutionPlan`, `GPULayerSaveRecord`, `GPULayerRestorePlan`,
  `GPULayerBoundsPlan`, `GPULayerTargetPlan`,
  `GPULayerInitializationPlan`, `GPULayerBackdropPlan`,
  `GPULayerSourcePlan`, `GPULayerFilterChainPlan`,
  `GPULayerCompositePlan`, `GPULayerElisionPlan`,
  `GPULayerTaskPlan`, `GPULayerResourcePlan`, `GPULayerOrderingToken`,
  `GPULayerBudgetPolicy`, and `GPULayerDiagnostic` dumps when saveLayer
  execution is used;
- `GPUFilterPlan` dumps;
- `GPUFilterGraphDescriptor`, `GPUFilterNodeDescriptor`,
  `GPUFilterNodePlan`, `GPUFilterBoundsPlan`, `GPUFilterCropPlan`,
  `GPUFilterTilePlan`, `GPUFilterSamplingPlan`,
  `GPUFilterIntermediatePlan`, `GPUFilterRuntimeEffectPlan`,
  `GPUFilterCachePlan`, `GPUFilterBudgetPolicy`, and
  `GPUFilterDiagnostic` dumps when filter graphs are used;
- intermediate resource keys;
- render/compute pipeline keys for filter nodes;
- forward/reverse bounds, crop, tile, local matrix, sample radius, and kernel
  expansion evidence;
- direct-to-parent and offscreen decisions;
- layer target descriptor, usage flags, initialization route, source/filter
  target generation, restore composite route, ordering token, pass split, and
  resource lifetime evidence;
- destination-read plan, strategy, bounds, and target/intermediate resource
  diagnostics when parent destination or backdrop is observed;
- registered runtime-effect descriptor, WGSL validation, uniform packing,
  child binding, and CPU oracle evidence when runtime filter effects are used;
- material-folded color-filter equivalence evidence when a DAG color filter is
  folded into `MaterialKey`;
- culling and layer-elision negative tests;
- refusal for unsupported filter DAG nodes or layer composite semantics.

### Color Management

Evidence must include:

- `GPUColorManagementPlan`, `GPUColorSpaceDescriptor`,
  `GPUColorProfileDescriptor`, `GPUICCProfileDescriptor`,
  `GPUCICPDescriptor`, `GPUTransferFunctionDescriptor`,
  `GPUGamutDescriptor`, `GPUColorValueSpec`,
  `GPUColorConversionPlan`, `GPUColorTransformPlan`,
  `GPUWorkingColorSpacePlan`, `GPUGradientColorPlan`,
  `GPUImageColorManagementPlan`, `GPUColorUniformPlan`,
  `GPUHDRColorPlan`, `GPUGainmapPlan`, `GPUColorStorePlan`,
  `GPUColorCachePlan`, `GPUColorBudgetPolicy`, and
  `GPUColorDiagnostic` dumps;
- source and destination value specs for paint, gradient, image, vertex, text,
  runtime-effect, filter, layer, blend, destination-read, and store boundaries
  when touched;
- CPU/WGSL transform descriptor parity and `wgsl4k` validation for promoted
  shader transforms;
- stable refusals for unsupported ICC/CICP profiles, custom transfers, HDR,
  gainmaps, F16 targets, untagged policies, and platform-only conversions.

### Coordinate / Transform / Bounds

Evidence must include:

- `GPUCoordinateSpace`, `GPUTransformDescriptor`, `GPUTransformPlan`,
  `GPUTransformChain`, `GPUInverseTransformPlan`,
  `GPUTransformPrecisionPlan`, `GPUPixelGridPlan`,
  `GPUBoundsDescriptor`, `GPUBoundsPlan`, `GPUBoundsProof`,
  `GPUBoundsExpansionPlan`, `GPURoundingPlan`, `GPUClipReductionProof`,
  `GPUCoordinatePayloadPlan`, `GPUTransformCachePlan`,
  `GPUTransformBudgetPolicy`, and `GPUTransformDiagnostic` dumps;
- identity, translate, scale, rect-stays-rect affine, general affine,
  perspective refusal/promotion, singular, non-finite, and near-singular
  fixtures;
- conservative forward/reverse bounds, expansion, clip reduction, full-target
  widening, and integer rounding evidence for every promoted route family;
- CPU/WGSL parity evidence when transform math runs in WGSL;
- stable refusals for unproven bounds, unsafe layer-hint clipping,
  unsupported perspective, integer overflow, and missing inverse transforms.

### Runtime Effects

Evidence must include:

- `GPURuntimeEffectRegistry`, `GPURuntimeEffectRegistrySnapshot`,
  `GPURuntimeEffectDescriptor`, `GPURuntimeEffectLookupPlan`,
  `GPURuntimeEffectUniformSchema`, `GPURuntimeEffectChildSlotPlan`,
  `GPURuntimeEffectWGSLPlan`, `GPURuntimeEffectCPUOracle`,
  `GPURuntimeEffectRouteContract`, and `GPURuntimeEffectDiagnostic` dumps as
  defined in `27-registered-runtime-effects-registry.md`;
- registered descriptor ID, descriptor version, and registry generation;
- uniform and child binding reflection;
- Kotlin/CPU oracle behavior;
- WGSL validation and complete module reflection for promoted GPU routes;
- route-specific evidence for material, filter, blender, primitive, compute, or
  future clip placement;
- live-edit parameter evidence when live editing is claimed;
- stable refusal for arbitrary Skia/SkSL input, unknown compatibility keys,
  unregistered descriptors, missing WGSL, missing CPU oracle, and kind
  mismatches.

### Vertices And Mesh-Like Draws

Evidence must include:

- `GPUVerticesDescriptor`, `GPUVertexLayoutPlan`, `GPUVertexPositionPlan`,
  `GPUVertexColorPlan`, `GPUVertexTexCoordPlan`,
  `GPUPrimitiveColorPlan`, `GPUPrimitiveBlendPlan`,
  `GPUIndexBufferPlan`, `GPUVertexBufferPlan`, `GPUVerticesRoute`,
  `GPUVerticesRenderStepPlan`, `GPUVerticesBoundsPlan`,
  `GPUVerticesBudgetPolicy`, and `GPUVerticesDiagnostic` dumps as defined in
  `26-draw-vertices-mesh-pipeline.md`;
- topology diagnostics for triangles, triangle strips, and triangle fan
  canonicalization or refusal;
- attribute diagnostics for position-only, color, texcoord, and
  color+texcoord variants;
- index validation diagnostics for out-of-range, format, and overflow cases;
- primitive-color and primitive-blender diagnostics;
- vertex/index buffer upload and resource-owner diagnostics;
- artifact key preimages when `CPUPreparedGPU(PrecomputedGeometryArtifact)` is
  used for packing, conversion, or canonicalization;
- WGSL layout, vertex attribute, varying, and reflection evidence for promoted
  render-step variants;
- CPU oracle comparison or stable refusal;
- GPU evidence before support claims.

Unsupported topology, invalid indices, unvalidated color conversion,
unsupported primitive blenders, unsupported texcoord/material coordinate
semantics, excessive buffer budgets, and missing WGSL ABI evidence must refuse
with stable reasons.

## Stable Refusal Taxonomy

Families must reuse route-policy reason codes where possible and add specific
codes only when they identify an actionable unsupported condition.

Examples:

- `unsupported.geometry.path_edge_budget`
- `unsupported.geometry.path_key_nondeterministic`
- `unsupported.geometry.path_fill_rule`
- `unsupported.geometry.tessellation_unavailable`
- `unsupported.geometry.stencil_cover_unavailable`
- `unsupported.geometry.prepared_buffer_budget_exceeded`
- `unsupported.atlas.entry_too_large`
- `unsupported.atlas.in_use_try_again_limit`
- `unsupported.atlas.generation_stale`
- `unsupported.stroke.width_invalid`
- `unsupported.stroke.join`
- `unsupported.stroke.cap`
- `unsupported.stroke.dash_complex`
- `unsupported.vertices.topology`
- `unsupported.vertices.index_out_of_range`
- `unsupported.vertices.attribute_layout`
- `unsupported.vertices.color_conversion_unvalidated`
- `unsupported.vertices.primitive_blender_unregistered`
- `unsupported.vertices.buffer_budget_exceeded`
- `unsupported.clip.stack_difference_path`
- `unsupported.clip.stack_too_deep`
- `unsupported.clip.operation`
- `unsupported.clip.analytic_unsupported`
- `unsupported.clip.stencil_ordering_illegal`
- `unsupported.clip.mask_budget_exceeded`
- `unsupported.clip.shader_unregistered`
- `unsupported.image.codec_missing`
- `unsupported.image.codec.unregistered`
- `unsupported.image.codec.selection_nondeterministic`
- `unsupported.image.decode.invalid_input`
- `unsupported.image.animation.required_frame_missing`
- `unsupported.color.image_profile_conversion`
- `unsupported.color.YUV_conversion`
- `unsupported.color.profile_parse`
- `unsupported.color.transfer_function`
- `unsupported.color.gainmap`
- `unsupported.color.WGSL_validation`
- `unsupported.image.orientation`
- `unsupported.image.upload.budget_exceeded`
- `unsupported.image.tile_mode`
- `unsupported.texture.ownership_missing`
- `unsupported.texture.import_unvalidated`
- `unsupported.texture.active_attachment_sampled`
- `unsupported.text.shaping_dependency`
- `unsupported.text.color_font_dependency`
- `unsupported.text.artifact_unregistered`
- `unsupported.text.atlas_generation_stale`
- `unsupported.text.upload_plan_missing`
- `unsupported.text.SDF_route_unavailable`
- `unsupported.layer.destination_read`
- `unsupported.layer.init_previous_unaccepted`
- `unsupported.layer.backdrop_filter`
- `unsupported.layer.restore_blend`
- `unsupported.layer.elision_proof_missing`
- `unsupported.layer.CPU_fallback_forbidden`
- `unsupported.destination_read.strategy_unaccepted`
- `unsupported.destination_read.active_attachment_sampled`
- `unsupported.destination_read.copy_budget_exceeded`
- `unsupported.filter.node_unimplemented`
- `unsupported.filter.bounds_unbounded`
- `unsupported.filter.tile_mode`
- `unsupported.filter.runtime_effect_unregistered`
- `unsupported.filter.intermediate_budget_exceeded`
- `unsupported.filter.CPU_rendered_texture_forbidden`
- `unsupported.runtime_effect.compatibility_key_unknown`
- `unsupported.runtime_effect.kind_mismatch`
- `unsupported.runtime_effect.WGSL_missing`
- `unsupported.runtime_effect.WGSL_validation`
- `unsupported.runtime_effect.CPU_oracle_missing`
- `unsupported.runtime_effect.dynamic_SkSL_forbidden`
- `unsupported.runtime_effect.unregistered_descriptor`
- `unsupported.blend.dst_dependent_mode`

## Non-Goals

- Do not claim support from appearing in this matrix.
- Do not use this matrix as implementation order.
- Do not collapse dependency-gated families into generic CPU fallback.
- Do not add a family without route, evidence, and refusal policy.
- Do not treat `FutureResearch` as accepted support.
