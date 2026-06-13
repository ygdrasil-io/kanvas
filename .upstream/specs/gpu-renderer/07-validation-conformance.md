# Validation And Conformance

Status: Draft
Date: 2026-06-13

## Purpose

Define the evidence required before the new GPU renderer can claim support,
promote routes, or retire legacy behavior.

The validation model keeps Kanvas' existing discipline: support claims need
CPU/GPU evidence or explicit refusal, deterministic diagnostics, and PM-visible
artifacts.

## Validation Layers

### Command Normalization Tests

Tests must assert:

- captured transform facts;
- captured clip facts;
- captured layer facts;
- material descriptor normalization;
- conservative bounds;
- ordering hints;
- stable unsupported normalization reasons;
- absence of direct `Sk*` types at the core boundary.

### Key Determinism Tests

Tests must assert canonical preimages and hashes for:

- `MaterialKey`;
- `GPUMaterialProgramID`;
- `GPUMaterialAssemblyPlan`;
- `GPURenderPipelineKey`;
- `GPUComputePipelineKey`;
- WGSL module identity;
- payload gather plan identity;
- CPU-prepared artifact keys;
- texture/image descriptors and ownership plans;
- image pipeline, codec descriptor, decode request, animation plan, color
  decode plan, orientation plan, upload plan, and upload artifact key
  preimages;
- clip stack, save-record, element, bounds, scissor, analytic, stencil, mask,
  shader, cache, budget, ordering token, and diagnostic preimages;
- filter graph, node descriptor, bounds, crop, tile, sampling, intermediate,
  runtime-effect, cache, budget, ordering token, and diagnostic preimages;
- path/coverage atlas plans, content keys, entry refs, and mutation plans;
- destination-read plans, strategies, bounds, copy plans, bindings, and tokens;
- route diagnostics.

Equivalent inputs must produce equivalent keys. Different behavior-affecting
inputs must produce different keys.

### WGSL Validation Tests

Promoted WGSL routes must prove:

- assembled WGSL source is deterministic;
- material WGSL routes pass through `GPUMaterialDictionary` and
  `GPUMaterialAssemblyPlan`;
- `wgsl4k` validation and reflection succeed;
- binding layouts match Kotlin-side packing;
- parser diagnostics are captured when validation fails;
- generated modules do not depend on SkSL.

### Material Dictionary Tests

Tests must assert:

- equivalent material descriptors produce equivalent `MaterialKey` and
  `GPUMaterialProgramID` values within the same dictionary version;
- unsupported snippets and child-slot shapes refuse with stable diagnostics;
- snippet requirement propagation is deterministic and dumpable;
- material root roles are explicit and forbidden roles refuse;
- material assembly plans include dictionary version, snippet tree, ABI
  contributions, and WGSL fragment versions;
- material module assembly does not bypass registered `WGSLSnippet` metadata.

### WGSL ABI Tests

Tests must assert:

- bind group role and binding-number determinism;
- uniform, storage, texture, and sampler layout preimages;
- Kotlin packing offsets, sizes, alignment, and padding;
- reflection mismatch refusals;
- render and compute key preimages include ABI hashes.

### Payload Gathering Tests

Tests must assert:

- `GPUPayloadGatherPlan`, `GPUPayloadWritePlan`, `GPUPayloadBindingPlan`, and
  `GPUPayloadUploadPlan` dumps are deterministic;
- uniform payload bytes match `WGSLPackingPlan` offsets, sizes, padding, and
  numeric conversion;
- equal payload values de-duplicate to the same pass-local slot or scoped
  `GPUPayloadFingerprint`;
- distinct payload values do not change durable material or pipeline keys;
- resource binding order matches `WGSLResourceBindingPlan`;
- stale, missing, or incompatible resources refuse with stable diagnostics.

### Texture And Image Ownership Tests

Tests must assert:

- `GPUImageSourceDescriptor`, `GPUTextureDescriptor`,
  `GPUTextureViewDescriptor`, `GPUSamplerDescriptor`, and
  `GPUTextureOwnershipPlan` dumps are deterministic;
- `MaterialKey` excludes raw handles, imported handles, surface leases,
  `GPUTextureResourceRef`, `UploadedTextureArtifact` keys, and pixel contents;
- sampled texture binding order matches `WGSLResourceBindingPlan`;
- CPU pixel sources use `UploadedTextureArtifact` or refuse;
- GPU-native, imported, surface, offscreen, render-target, and atlas textures
  are not treated as `CPUPreparedGPU` unless a typed CPU-prepared artifact owns
  the prepared contents;
- imported textures refuse when owner, usage, lifetime, generation, or release
  policy is not dumpable;
- surface texture leases are frame/target-generation scoped;
- sampling the active color attachment refuses unless a validated intermediate
  route exists;
- stale device, target, atlas, upload, or surface generations rebuild, discard,
  or refuse deterministically.

### Image Bitmap Codec Pipeline Tests

Tests must assert:

- `GPUImagePipelinePlan`, `GPUEncodedImageSource`, `GPUCPUImageSource`,
  `GPUImageCodecDescriptor`, `GPUImageDecodeRequest`,
  `GPUImageDecodePlan`, `GPUImageDecodeResult`, `GPUAnimatedImagePlan`,
  `GPUImageFrameInfo`, `GPUImageFrameSelection`,
  `GPUImageColorDecodePlan`, `GPUImageOrientationPlan`,
  `GPUImagePixelPlan`, `GPUImageMipmapPlan`, `GPUImageUploadPlan`,
  `GPUImageUploadArtifactKey`, `GPUUploadedImageArtifactDescriptor`,
  `GPUImageCachePlan`, `GPUImageBudgetPolicy`, and `GPUImageDiagnostic`
  dumps are deterministic;
- codec registry selection is deterministic and records codec ID, version,
  implementation kind, capabilities, conformance tier, and nondeterminism
  policy;
- PNG, JPEG, WebP, GIF, BMP, ICO, WBMP, HEIF, and AVIF target fixtures either
  decode through accepted codec capability or refuse with stable diagnostics;
- malformed, truncated, invalid-conversion, invalid-scale, invalid-parameter,
  unimplemented, and out-of-memory decode cases map to stable refusal codes;
- animated WebP, GIF, and AVIF fixtures cover frame duration, loop count,
  dirty rect, required prior frame, disposal, blend, first-frame-still policy,
  and frame-cache invalidation;
- ICC/CICP/profile, bit-depth, alpha, premul/unpremul, EXIF/origin,
  orientation, HDR metadata, and tone-map/refusal behavior are explicit and
  covered before support claims;
- uploaded image artifact keys exclude object identity, raw platform handles,
  wall-clock facts, and nondeterministic codec output;
- image decode, frame composition, mip generation, staging upload, and texture
  materialization are ordered before any draw samples the image;
- `MaterialKey` excludes codec versions, upload artifact keys, decoded pixel
  hashes, frame cache generations, and concrete resource handles unless a fact
  changes WGSL code or layout;
- no route CPU-renders a complete unsupported draw, layer, filter, text run, or
  scene into an image texture.

### Text And Glyph Pipeline Tests

Tests must assert:

- `GPUTextRunPlan`, `GPUTextSubRunPlan`, `GPUTextRepresentation`,
  `GPUTextRoute`, `GPUTextRenderStep`, `GPUTextAtlasPlan`,
  `GPUTextAtlasDescriptor`, `GPUTextAtlasPageDescriptor`,
  `GPUTextAtlasEntryRef`, `GPUTextUploadPlan`, `GPUTextResourcePlan`,
  `GPUTextInstanceLayout`, `GPUTextInstanceBufferPlan`, `GPUTextBinding`,
  `GPUTextSDFParams`, `GPUColorGlyphCompositePlan`, `GPUTextBatchKey`,
  `GPUTextOrderingToken`, `GPUTextBudgetPolicy`, and `GPUTextDiagnostic`
  dumps are deterministic;
- `DrawTextRun` payloads contain pure Kotlin text value objects and no `Sk*`
  mutable API types;
- `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, `GlyphUploadPlan`,
  `OutlineGlyphPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, and `SVGGlyphPlan`
  are registered typed artifacts before routes using them are promoted;
- A8, SDF, outline, color glyph, bitmap glyph, and SVG glyph routes have
  positive GPU evidence before support claims;
- unregistered, stale, evicted, upload-missing, upload-failed, and
  budget-exceeded text artifacts refuse with stable diagnostics;
- text atlas uploads and instance buffer uploads execute before draws that
  sample or consume them;
- text atlas coordinates, glyph IDs, atlas generation, entry refs, and upload
  tokens stay out of `MaterialKey`;
- text bindings use `GPUTextBinding` and match WGSL reflection for text render
  steps;
- planner sort/merge cannot cross text upload, atlas generation, layer, clip,
  or destination-read barriers;
- no route CPU-renders a complete unsupported text run into a texture.

### Destination Read Tests

Tests must assert:

- `GPUDestinationReadPlan`, `GPUDestinationReadRequirement`,
  `GPUDestinationReadStrategy`, `GPUDestinationReadBounds`,
  `GPUDestinationReadAction`, `GPUDestinationReadBudgetPolicy`,
  `GPUDestinationCopyPlan`, `GPUDestinationCopyTextureDescriptor`,
  `GPUDestinationReadBinding`, `GPUDestinationReadToken`, and
  `GPUDestinationReadDiagnostic` dumps are deterministic;
- fixed-function blend routes do not create destination texture bindings;
- shader destination-read routes refuse until target-copy, intermediate,
  ordering, payload, and validation rules are accepted;
- active color attachment sampling refuses;
- stale target or surface generation refuses;
- destination-copy usage flags and copy-before-sample ordering are validated;
- planner sort/merge cannot cross destination-read barriers;
- layer elision and culling refuse when destination contents are observed;
- destination-read bindings stay out of `MaterialKey`.

### Clip, Stencil, And Mask Pipeline Tests

Tests must assert:

- `GPUClipStackDescriptor`, `GPUClipSaveRecordDescriptor`,
  `GPUClipElementDescriptor`, `GPUClipPlan`, `GPUClipElementPlan`,
  `GPUClipBoundsPlan`, `GPUClipScissorPlan`, `GPUClipAnalyticPlan`,
  `GPUClipStencilPlan`, `GPUClipMaskPlan`, `GPUClipShaderPlan`,
  `GPUClipOrderingToken`, `GPUClipCachePlan`, `GPUClipBudgetPolicy`, and
  `GPUClipDiagnostic` dumps are deterministic;
- clip descriptor keys include element order, operation, shape key, transform,
  AA mode, save-record generation, bounds, difference/inverse semantics, and
  shader descriptor facts when present;
- scissor routes prove integer target intersection and refuse invalid or
  non-equivalent AA cases;
- geometric and analytic clip routes prove equivalence before support claims;
- stencil producer-consumer routes preserve clear/load/store, depth/stencil
  state, atomic groups, and ordering tokens;
- coverage-mask clip routes validate `GPUCoverageMaskDescriptor`,
  `CoverageMaskArtifact` or `PathAtlasArtifact`, atlas generation, upload or
  compute-write before sample, and mask sampling payloads;
- registered clip shader routes validate descriptor identity, CPU oracle,
  complete WGSL module reflection, uniform packing, child bindings, bounds, and
  refusal for unregistered shaders;
- planner sort/merge cannot cross clip atomic groups, clip mask mutations,
  clip shader mask production, destination-read barriers, layer boundaries, or
  unknown overlaps;
- layer/filter/destination-read interaction tests preserve required source,
  filter expansion, backdrop read, and restore/composite pixels;
- no route CPU-renders a complete clipped draw, layer, filter graph, text run,
  or scene into a texture.

### Filter Effect Pipeline Tests

Tests must assert:

- `GPUFilterPlan`, `GPUFilterGraphDescriptor`, `GPUFilterNodeID`,
  `GPUFilterNodeDescriptor`, `GPUFilterNodePlan`, `GPUFilterNodeRoute`,
  `GPUFilterInputPlan`, `GPUFilterSourcePlan`, `GPUFilterBackdropPlan`,
  `GPUFilterBoundsPlan`, `GPUFilterCropPlan`, `GPUFilterTilePlan`,
  `GPUFilterSamplingPlan`, `GPUFilterIntermediatePlan`,
  `GPUFilterRenderNodePlan`, `GPUFilterComputeNodePlan`,
  `GPUFilterKernelPlan`, `GPUFilterRuntimeEffectPlan`,
  `GPUFilterColorPlan`, `GPUFilterOrderingToken`, `GPUFilterCachePlan`,
  `GPUFilterBudgetPolicy`, and `GPUFilterDiagnostic` dumps are deterministic;
- graph normalization rejects cycles, ambiguous implicit sources, unknown node
  kinds, object-address identity, and unsupported deserialized filter objects;
- forward and reverse bounds, crop, tile, sample-radius, local-matrix, kernel
  expansion, and finite-bounds facts are covered before support claims;
- render and compute filter nodes validate complete WGSL modules through
  `wgsl4k` and match binding reflection;
- filter intermediates use accepted texture ownership, usage flags, lifetimes,
  generations, and upload/copy/compute ordering;
- backdrop/destination filter inputs use accepted `GPUDestinationReadPlan`
  routes and refuse active-attachment sampling;
- runtime filter effects use registered descriptors with CPU oracle behavior,
  WGSL implementation, child bindings, uniform packing, and sample-radius
  evidence;
- material-folded color filters prove equivalence with DAG behavior before the
  fold is claimed as support;
- `FilterIntermediateArtifact` is registered and keyed before any
  `CPUPreparedGPU` filter route is promoted;
- no route CPU-renders a complete unsupported draw, layer, filter graph, text
  run, or scene into a texture.

### Path And Coverage Atlas Tests

Tests must assert:

- `GPUPathAtlasPlan`, `GPUCoverageAtlasPlan`, `GPUAtlasPolicy`,
  `GPUAtlasBudgetPolicy`, `GPUAtlasPageDescriptor`,
  `GPUAtlasPlotDescriptor`, `GPUAtlasEntryDescriptor`, `GPUAtlasEntryRef`,
  `GPUAtlasUseToken`, `GPUAtlasMutationPlan`, `GPUAtlasUploadPlan`,
  `GPUCoverageMaskDescriptor`, and `GPUAtlasDiagnostic` dumps are
  deterministic;
- `GPUPathAtlasKey` and `GPUCoverageAtlasKey` include all content-affecting
  path, stroke, transform, clip, tolerance, coverage, and policy facts;
- atlas residency facts such as coordinates, page/plot generation, atlas
  generation, and use tokens stay out of `MaterialKey`;
- stale, evicted, in-use, upload-failed, and budget-exceeded entries rebuild,
  split/retry, or refuse deterministically;
- atlas upload or compute write tasks execute before draws that sample them;
- planner sort/merge cannot cross atlas mutation barriers;
- no path/coverage atlas route CPU-renders a complete unsupported draw or
  layer into a texture.

### Blend And Color Tests

Tests must assert:

- `GPUBlendPlan` dumps for promoted blend modes;
- `GPUColorPlan` dumps for promoted color and alpha behavior;
- `GPUTargetState` key contribution;
- refusal for unsupported destination-read or color-conversion paths;
- layer elision only when blend and color plans prove equivalence.

### Execution And Submission Tests

Tests must assert:

- execution context and target dumps;
- device-generation validation;
- render, compute, copy, upload, and readback scope legality when used;
- stale resource refusal or rebuild behavior;
- skipped readback or timing lanes are reported explicitly.

### Route Policy Tests

Tests must cover:

- `GPUNative` success;
- `CPUPreparedGPU` success where allowed;
- `RefuseDiagnostic` for unsupported features;
- absence of silent CPU fallback;
- absence of CPU-rendered texture compatibility routes;
- stable route reason codes;
- capability-gated differences.

### GPU Evidence

GPU support claims require adapter-backed or accepted equivalent GPU evidence.

Evidence must include:

- command count;
- selected route count;
- pipeline key count;
- material key count;
- material dictionary version and material program count;
- material assembly plan count;
- payload slot counts, payload fingerprints, and upload bytes;
- texture provenance, ownership plan counts, sampled binding counts, and upload
  artifact counts when textures/images are used;
- image/codec registry generation, decode request count, animated frame
  selection count, image color conversion count, image upload artifact count,
  codec refusal count, and upload-before-sample evidence when image/bitmap
  routes are used;
- atlas policy counts, resident entry counts, upload/compute bytes, generation
  facts, retry/split counts, eviction counts, and atlas refusal counts when
  path or coverage atlases are used;
- clip descriptor counts, route counts, effective element counts, scissor
  counts, analytic/stencil/mask/shader counts, mask bytes, clip-induced pass
  splits, budget pressure, and clip refusal counts when clipping is touched;
- destination-read strategy counts, copied bytes, pass splits, binding counts,
  target generation facts, and destination-read refusal counts when previous
  destination contents are observed;
- WGSL module validation result;
- output artifact, checksum, diff, or readback where applicable;
- capability facts;
- refusal counts.

Skipped GPU lanes must be reported as skipped or risk states, not as support.

### CPU Reference Evidence

CPU reference behavior is used for:

- oracle generation;
- diffs;
- support/refusal comparison;
- migration confidence.

CPU reference does not imply product fallback support.

## Existing Commands

Until this pack has dedicated tasks, the existing validation commands remain
the minimum evidence layer:

- `rtk ./gradlew --no-daemon pipelineConformance`
- `rtk ./gradlew --no-daemon pipelineConformanceReport`

New GPU renderer tickets may add narrower tasks, but they must not bypass the
existing conformance discipline without an accepted replacement.

## PM Evidence

PM/report artifacts must show:

- route taxonomy;
- counts by route kind;
- stable unsupported reasons;
- representative support artifacts;
- GPU capability facts;
- execution context and device-generation facts;
- WGSL ABI validation status;
- blend/color/target-state plan counts;
- path/coverage atlas route, generation, retry, budget, and eviction state
  when atlas routes are touched;
- clip route, effective element, scissor, analytic/stencil/mask/shader, mask
  bytes, pass split, budget, and refusal state when clipping is touched;
- destination-read strategy, bounds, copy/intermediate bytes, pass splits,
  budgets, and refusal state when destination reads are touched;
- cache and pipeline counters when performance is claimed;
- telemetry and performance-gate state when realtime readiness is claimed;
- known limitations.

Reports must distinguish:

- supported GPU rendering;
- CPU-prepared GPU rendering;
- CPU reference-only evidence;
- explicit refusal;
- skipped or unavailable adapter evidence.

## Promotion Criteria

A route can be promoted only when:

- normalized command contract tests pass;
- key determinism tests pass;
- WGSL validation passes when WGSL is used;
- GPU evidence exists for GPU support claims;
- CPU reference or explicit refusal evidence exists;
- route diagnostics are stable;
- WGSL ABI, blend/color, target-state, and execution assumptions are validated
  for the route;
- PM/report artifacts expose support and refusal state;
- rollback to the legacy path is documented for migrated slices.

## Cleanup Acceptance

Cleanup-only changes are accepted only when they prove:

- no default route change;
- no pixel change;
- diagnostics are compatible or intentionally versioned;
- new shadow output is deterministic;
- tests cover the touched boundary.

## Failure Policy

Failures must be classified as:

- normalization failure;
- material-key failure;
- material-dictionary failure;
- payload-gathering failure;
- WGSL validation failure;
- pipeline-key failure;
- resource-preparation failure;
- texture/image ownership failure;
- command-encoding failure;
- GPU execution/readback failure;
- WGSL ABI mismatch;
- blend/color plan refusal;
- clip plan refusal;
- execution context or device-generation failure;
- performance gate failure;
- CPU oracle mismatch;
- explicit unsupported feature.

Each class needs a stable reason code before it can appear in promoted
reports.

## Non-Goals

- Do not claim support from unit tests alone.
- Do not hide skipped adapter evidence.
- Do not replace explicit refusals with dashboard omissions.
- Do not treat CPU reference success as GPU product support.
- Do not accept CPU-rendered texture composition as product support.
- Do not retire legacy paths without route-level evidence.
