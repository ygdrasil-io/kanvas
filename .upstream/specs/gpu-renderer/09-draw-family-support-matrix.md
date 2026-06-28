# Draw Family Support Matrix

Status: Draft
Date: 2026-06-13

## Purpose

Define the target support and refusal matrix for the GPU renderer draw
families. This matrix describes the full technical target. It is not an
implementation order, release promise, or shortcut around the evidence gates in
`07-validation-conformance.md`.

Every family below must resolve to one of:

- `TargetRequired`: required target concept for accepted GPU routes, even when
  the first slice promotes only a subset.
- `TargetNative`: intended to become a `GPUNative` route.
- `TargetPrepared`: intended to use typed `CPUPreparedGPU` artifacts consumed
  by the GPU.
- `ReferenceOnly`: CPU oracle or test evidence only, not product rendering.
- `RefuseRequired`: explicit stable refusal required.
- `DependencyGated`: support depends on another accepted spec or external
  delivery such as font, codec, or filter infrastructure.
- `PolicyGated`: support depends on an explicit product or architecture policy
  decision, not only missing implementation.
- `FutureResearch`: target direction is recognized but not yet accepted.

`TargetNative` and `TargetPrepared` still require validation before support can
be claimed.

The canonical maturity vocabulary is defined in
`32-target-authority-taxonomy-diagnostics.md`.

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
| Material source / paint pipeline | `TargetRequired` | `GPUNative`, `CPUPreparedGPU` only through typed non-shaded artifacts, or `RefuseDiagnostic` | `GPUPaintPipelinePlan`, `GPUMaterialSourcePlan`, source snippets, payload handoff | `GPUPaintDescriptor`, `GPUMaterialSourceDescriptor`, `GPUSolidColorPlan`, `GPUGradientPlan`, `GPUImageShaderPlan`, `GPULocalMatrixShaderPlan`, `GPUShaderBlendSourcePlan` | No coverage artifact; material source plans feed accepted geometry/text/vertex/image/layer routes. |
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
| GPU compute tessellation | `TargetNative` | `GPUNative` when capabilities accept, else `CPUPreparedGPU` | Compute pass, indirect dispatch, output buffer | `GPUComputeTessellationPlan`, `GPUComputeTessellationStage`, and `WGSLComputeModule` | `GPUComputeTessellationArtifact` or refusal. |
| Advanced stroke (complex dash, path effects) | `TargetNative` | `GPUNative` or `CPUPreparedGPU` | Render pass with expanded geometry or mask | `GPUPathEffectChainPlan`, `GPUStrokeStyleCompositionPlan`, `GPUComplexDashPlan` | `PrecomputedGeometryArtifact` or `PathAtlasArtifact`. |
| Subpixel LCD text | `TargetNative` | `GPUNative` when adapter reports pixel geometry | Text render step, per-component alpha modulation | `GPUSubpixelLCDPlan`, `GPUSubpixelCoverageMask`, `GPUSubpixelLCDRenderStep` | `GlyphAtlasArtifact` with per-component coverage or refusal. |
| Color font / emoji | `DependencyGated` | `GPUNative`, `CPUPreparedGPU`, or refusal per format | Direct render, atlas composite, or bitmap decode then sample | `GPUColorGlyphLayerPlan`, `GPUColorGlyphCompositePlan`, `GPUCBDTCBLCGlyphPlan`, `GPUSVGOpenTypeGlyphPlan` | Color glyph artifact from pure-kotlin-text or refusal. |
| Variable font | `DependencyGated` | Resolved at text-stack level; GPU sees static glyphs | Same as text/glyph run | `GPUVariableFontInstancePlan` | Resolved glyph artifacts; no GPU-side axis computation. |
| Complex shaping (Arabic, Devanagari, CJK) | `DependencyGated` | Resolved at text-stack level | Same as text/glyph run | `GPUShapingIntegrationContract`, `GPUBiDiRunPlan` | Shaping facts as metadata only; GPU does not shape. |
| Font fallback chain | `TargetNative` | Resolved at text-stack level | Subrun splitting by fallback font identity | `GPUFallbackGlyphPlan`, `GPUFallbackBatchPolicy` | Fallback glyph artifacts; exhausted chain refuses. |
| HDR transfer functions (PQ, HLG) | `TargetNative` | `GPUNative` or `CPUPreparedGPU` for pixel preparation | Color transform render/compute nodes, EOTF application in WGSL | `GPUHDRTransferFunctionPlan`, `GPUHDREOTFPlan`, `GPUHDRToneMapPlan` | HDR target format required; tone-map to SDR when unavailable. |
| Wide-gamut (P3, AdobeRGB, Rec.2020) | `TargetNative` | `GPUNative` | Color transform render nodes, intermediate format upgrade to rgba16float | `GPUWideGamutWorkingSpacePlan`, `GPUWideGamutConversionPlan` | Wide-gamut intermediate textures; refusal for incompatible target. |
| Gain map / Ultra HDR | `TargetNative` | `GPUNative` or `CPUPreparedGPU` decode | Gain map apply WGSL fragment, adaptive display rendering | `GPUGainmapDecodePlan`, `GPUGainmapApplyPlan`, `GPUGainmapDisplayAdaptationPlan` | Decode base + gain map; adaptive output by display headroom. |
| ICC profile parsing | `TargetNative` | `GPUNative` when profile is matrix/TRC, refuse for LUT | Color transform from parsed profile tags | `GPUICCProfileParsePlan`, `GPUICCProfileTransformPlan`, `GPUICCProfileCachePlan` | Parsed transform validated against reference; LUT profiles refused. |
| HEIF / AVIF codec | `DependencyGated` | `CPUPreparedGPU` upload when codec accepted | Decode/prepare, upload, then texture sampling | `GPUHEIFCodecDescriptor`, `GPUAVIFCodecDescriptor`, `GPUISOBMFFParsePlan` | `UploadedTextureArtifact`; refused if codec unregistered. |
| Hardware codec | `DependencyGated` | `CPUPreparedGPU` upload with documented nondeterminism policy | Decode via platform API, upload | `GPUHardwareCodecDescriptor`, `GPUHardwareCodecNondeterminismPolicy`, `GPUHardwareCodecFallbackPlan` | `UploadedTextureArtifact`; fallback to software codec on failure. |
| YUV multi-plan texture | `TargetNative` | `GPUNative` when WGSL YUV converter is validated | Multi-plane texture upload, WGSL YUV-to-RGB conversion | `GPUYUVMultiPlanDescriptor`, `GPUYUVPlaneUploadPlan`, `GPUYUVToRGBCoverterPlan` | GPU YUV-to-RGB conversion; CPU-side conversion refused. |
| Mipmap auto-generation | `TargetNative` | `GPUNative` via blit or compute | Mip generation blit/compute passes, cached mipmaps | `GPUImageMipmapGenerationPlan`, `GPUImageMipmapBlitPlan`, `GPUImageMipmapComputePlan`, `GPUImageMipmapCachePlan` | Refusal when mip count exceeds adapter limit. |
| Blur multi-pass | `TargetNative` | `GPUNative` | Two-pass separable blur (horizontal + vertical), intermediate texture | `GPUSeparableBlurPlan`, `GPUBlurPassPlan`, `GPUBlurKernelCachePlan`, `GPUBlurQualityLevel` | `GPUBlurIntermediateArtifact`. |
| Morphology (dilate/erode) | `TargetNative` | `GPUNative` | Single-pass or separable two-pass min/max gather | `GPUMorphologyPlan`, `GPUMorphologyPassPlan`, `GPUMorphologySamplingPlan` | GPU rendering; refusal for radius exceeding sample budget. |
| Lighting filters | `TargetNative` | `GPUNative` | WGSL Phong/Blinn-Phong lighting with normal map | `GPULightingPlan`, `GPULightingNormalMapPlan`, `GPULightingWGSL` | GPU rendering; spot lights refused initially. |
| Displacement map | `TargetNative` | `GPUNative` | WGSL gather with offset coordinates | `GPUDisplacementMapPlan`, `GPUDisplacementSamplingPlan` | GPU rendering; refusal for unregistered sampler. |
| Drop shadow | `TargetNative` | `GPUNative` | Mask extraction, blur, offset composite | `GPUDropShadowPlan`, `GPUDropShadowMaskPlan`, `GPUDropShadowBlurPlan`, `GPUDropShadowCompositePlan` | Reuses `GPUSeparableBlurPlan`; refusal when blur unavailable. |
| Filter tile-based evaluation | `TargetNative` | `GPUNative` | Tiled filter sub-renders with overlap, final composite | `GPUFilterTilePlan`, `GPUFilterTileRenderPlan`, `GPUFilterTileBudgetPolicy` | Per-tile intermediates; refused when tile smaller than kernel. |
| MSAA resolve | `TargetNative` | `GPUNative` when adapter accepts sample count | Multisample render pass, resolve to single-sample target | `GPUMultisamplePlan`, `GPUMultisampleResolvePlan`, `GPUMultisampleTargetDescriptor` | GPU resolve; refusal for unsupported sample count or format. |
| Live effect parameter editing | `TargetNative` | `GPUNative` | Uniform-only update, no pipeline recompilation | `GPURuntimeEffectLiveParameterSchema`, `GPURuntimeEffectLiveParameterBinding`, `GPURuntimeEffectLiveState` | Uniform payload update only; refusal for unregistered parameters. |
| Blender / clip-shader / compute effects | `TargetNative` | `GPUNative` per effect kind | Material, blender, clip-shader, compute, or filter route | `GPURuntimeEffectKind.Blender`, `.ClipShader`, `.Compute` | Kind-specific WGSL and route placement; mismatch refused. |
| Dynamic shader graph | `TargetNative` | `GPUNative` | Assembled WGSL module from effect DAG | `GPURuntimeEffectShaderGraph`, `GPURuntimeEffectShaderGraphAssemblyPlan` | DAG validated for cycles and budget before assembly. |
| Perspective transform | `TargetNative` | `GPUNative` for rect/rrect + solid, refused for path/text | Homogeneous divide in vertex shader, conservative bounds proof | `GPUPerspectiveTransformPlan`, `GPUPerspectiveBoundsProof`, `GPUPerspectiveRouteAcceptance` | Accepted for first-slice geometry; refused for paths. |
| Deferred display list | `TargetNative` | `GPUNative` replay | Record once, replay with new CTM/clip/target | `GPUDeferredDisplayList`, `GPUDeferredDisplayListCompatibilityKey`, `GPUDeferredDisplayListReplayPlan` | Cached recordings; refusal for incompatible replay. |
| Subpass merging | `TargetNative` | `GPUNative` when adapter supports input attachments | Merged render pass with input attachments | `GPUSubpassMergePlan` | Refusal for incompatible format, barrier, or adapter limit. |
| Instanced draw batching | `TargetNative` | `GPUNative` | Instanced draw commands with per-instance uniform/vertex data | `GPUInstancedPacketGroup`, `GPUInstancedUniformStrategy`, `GPUInstancedVertexStrategy` | Refusal for incompatible packets or barrier interference. |
| Tile-deferred rendering | `TargetNative` | `GPUNative` for large targets | Tile-based render passes, tile binning, tile composite | `GPUTileGridPlan`, `GPUTileBin`, `GPUTilePass`, `GPUTileCompositePass` | Single-pass fallback for small targets; tile budget enforced. |
| Multi-threaded recording | `TargetNative` | `GPUNative` when split is safe | Per-thread recording fragments, merge into single recording | `GPURecordingFragment`, `GPURecordingFragmentMerger`, `GPUThreadBoundArena` | Single-threaded remains default; unsafe splits refused. |
| Hi-Z occlusion culling | `TargetNative` | `GPUNative` for opaque draws with Z-prepass or previous-frame depth | Depth pyramid compute pass, per-draw occlusion test | `GPUHiZPyramid`, `GPUHiZOcclusionTest` | ZERO false-positive tolerance; translucent draws excluded. |


## Required Evidence By Family
### Material Source And Paint Pipeline

Evidence must include:

- `GPUPaintDescriptor`, `GPUPaintPipelinePlan`,
  `GPUMaterialSourceDescriptor`, and `GPUMaterialSourcePlan` dumps;
- source-kind diagnostics for solid, gradient, image shader, local matrix,
  shader blend, folded color-filter, and registered runtime-effect sources
  when touched;
- `MaterialKey` preimages that show only behavior/layout identity, not payload
  values or resource handles;
- `GPUMaterialDictionary` root/snippet dumps and requirement propagation;
- `GPUMaterialSourcePayloadPlan`, payload write plan, and related
  `GPUPayloadGatherer` evidence;
- linked color, coordinate, texture/image, runtime-effect, blend, and
  destination-read diagnostics when the source requests those contracts;
- stable refusal for unsupported source kind, stage order, tile/sampling,
  gradient stop storage, image ownership, local matrix, runtime effect, or
  payload budget.

No material-source route may CPU-render a complete draw or layer into a
texture for compatibility.

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
- `unsupported.paint_pipeline.stage_order`
- `unsupported.paint_pipeline.filter_fold_unproven`
- `unsupported.paint_pipeline.shader_blender_unaccepted`
- `unsupported.paint_pipeline.cpu_rendered_texture_forbidden`
- `unsupported.material_source.unknown`
- `unsupported.material_source.child_count`
- `unsupported.material_source.payload_budget`
- `unsupported.gradient.stop_store`
- `unsupported.image_shader.texture_ownership`
- `unsupported.local_matrix.non_invertible`
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
- `unsupported.color.yuv_conversion`
- `unsupported.color.profile_parse`
- `unsupported.color.transfer_function`
- `unsupported.color.gainmap`
- `unsupported.color.wgsl_validation`
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
- `unsupported.text.sdf_route_unavailable`
- `unsupported.layer.destination_read`
- `unsupported.layer.init_previous_unaccepted`
- `unsupported.layer.backdrop_filter`
- `unsupported.layer.restore_blend`
- `unsupported.layer.elision_proof_missing`
- `unsupported.layer.cpu_fallback_forbidden`
- `unsupported.destination_read.strategy_unaccepted`
- `unsupported.destination_read.active_attachment_sampled`
- `unsupported.destination_read.copy_budget_exceeded`
- `unsupported.filter.node_unimplemented`
- `unsupported.filter.bounds_unbounded`
- `unsupported.filter.tile_mode`
- `unsupported.filter.runtime_effect_unregistered`
- `unsupported.filter.intermediate_budget_exceeded`
- `unsupported.filter.cpu_rendered_texture_forbidden`
- `unsupported.runtime_effect.compatibility_key_unknown`
- `unsupported.runtime_effect.kind_mismatch`
- `unsupported.runtime_effect.wgsl_missing`
- `unsupported.runtime_effect.wgsl_validation`
- `unsupported.runtime_effect.cpu_oracle_missing`
- `unsupported.runtime_effect.dynamic_sksl_forbidden`
- `unsupported.runtime_effect.unregistered_descriptor`
- `unsupported.blend.dst_dependent_mode`
- `unsupported.tessellation.compute_unavailable`
- `unsupported.tessellation.wgsl_validation`
- `unsupported.tessellation.vertex_budget_exceeded`
- `unsupported.stroke.path_effect_chain_depth`
- `unsupported.stroke.dash_pattern_length`
- `unsupported.text.subpixel_pixel_geometry`
- `unsupported.text.subpixel_target_format`
- `unsupported.text.color_font.format_unavailable`
- `unsupported.text.color_font.layer_count`
- `unsupported.text.shaping_script_unavailable`
- `unsupported.text.fallback_exhausted`
- `unsupported.color.hdr_transfer_function`
- `unsupported.color.hdr_target_format`
- `unsupported.color.wide_gamut_working_space`
- `unsupported.color.gainmap_metadata_missing`
- `unsupported.color.gainmap_apply_wgsl_unvalidated`
- `unsupported.color.icc_profile_version`
- `unsupported.color.icc_lut_profile`
- `unsupported.image.heif_codec_unregistered`
- `unsupported.image.avif_codec_unregistered`
- `unsupported.image.hardware_codec_unapproved`
- `unsupported.image.hardware_codec_nondeterministic`
- `unsupported.image.yuv_color_space`
- `unsupported.image.yuv_converter_wgsl_unvalidated`
- `unsupported.image.mipmap_budget_exceeded`
- `unsupported.filter.blur_sigma_range`
- `unsupported.filter.blur_intermediate_budget`
- `unsupported.filter.morphology_radius_budget`
- `unsupported.filter.lighting_normal_source_missing`
- `unsupported.filter.displacement_missing_texture`
- `unsupported.filter.drop_shadow_blur_unavailable`
- `unsupported.filter.tile_smaller_than_kernel`
- `unsupported.target.multisample_count`
- `unsupported.target.multisample_resolve_format`
- `unsupported.runtime_effect.live_parameter_unregistered`
- `unsupported.runtime_effect.live_parameter_type_mismatch`
- `unsupported.runtime_effect.kind_not_registered`
- `unsupported.runtime_effect.shader_graph_cycle`
- `unsupported.runtime_effect.shader_graph_depth_exceeded`
- `unsupported.transform.perspective_route_rejected`
- `unsupported.transform.perspective_degenerate`
- `unsupported.recording.deferred_incompatible_replay`
- `unsupported.recording.subpass_merge_incompatible`
- `unsupported.stream.instanced_incompatible_packets`
- `unsupported.tile.budget_exceeded`
- `unsupported.tile.cross_tile_destination_read`
- `unsupported.tile.cross_tile_clip_atomic_group`
- `unsupported.recording.fragment_split_unsafe`
- `unsupported.recording.fragment_merge_cycle`
- `unsupported.occlusion.depth_format_unsupported`
- `unsupported.occlusion.depth_not_readable`

## Non-Goals

- Do not claim support from appearing in this matrix.
- Do not use this matrix as implementation order.
- Do not collapse dependency-gated families into generic CPU fallback.
- Do not add a family without route, evidence, and refusal policy.
- Do not treat `FutureResearch` as accepted support.
