# Pipeline Keys, Cache, And Resources

Status: Draft
Date: 2026-06-13

## Purpose

Define executable render and compute pipeline identity plus resource-provider
responsibilities for the new GPU renderer.

Pipeline keys are separate from `MaterialKey`. `MaterialKey` identifies
render-material logic only. `GPURenderPipelineKey` identifies the
GPU-executable combination of render material, render step, target state,
fixed state, and capabilities. `GPUComputePipelineKey` identifies the
GPU-executable combination of compute program, compute entry state,
resource-layout state, and capabilities.

The generic name `PipelineKey` may appear only when a rule applies to both
render and compute keys.

## `GPURenderPipelineKey`

`GPURenderPipelineKey` includes:

- `GPURenderStep` identity and version;
- `MaterialKey` identity;
- `GPUMaterialProgramID` plus dictionary version when a material has been
  interned;
- vertex layout;
- primitive topology;
- target color format;
- target alpha/color-space write assumptions;
- depth/stencil state;
- blend state;
- sample count or coverage mode;
- bind group layout identity;
- WGSL module identity;
- capability requirements;
- renderer version salt.

It does not include:

- per-draw uniform values;
- payload slot IDs;
- bind group instances;
- command ID;
- transient buffer offsets;
- texture contents;
- atlas coordinates;
- resource object addresses.

## `GPUComputePipelineKey`

`GPUComputePipelineKey` includes:

- `GPUComputeProgramKey` identity and version;
- `WGSLComputeModule` identity;
- compute entry point identity;
- workgroup sizing policy when it affects pipeline validity or code shape;
- bind group layout identity;
- storage buffer, storage texture, sampled texture, sampler, and uniform
  layout identity;
- capability requirements such as storage texture formats, workgroup limits,
  buffer binding limits, and required shader features;
- renderer version salt.

It does not include:

- per-dispatch uniform values;
- command ID;
- transient buffer offsets;
- input texture contents;
- output resource object addresses;
- dispatch dimensions, unless they are compiled into the module or otherwise
  affect pipeline validity;
- cache occupancy or scheduling state.

Filter plans that use compute pipelines reference `GPUComputeProgramKey`,
`WGSLComputeModule`, and `GPUComputePipelineKey` explicitly. They must not
reuse `MaterialKey` as a proxy for compute filter identity.

## Key Determinism

Pipeline key generation for render and compute keys must be:

- deterministic across equivalent commands;
- stable across process runs when inputs are equivalent;
- explicit about version changes;
- independent of Kotlin object identity;
- backed by test fixtures that assert canonical preimages.

Every promoted key family must expose a diagnostic preimage and a compact hash.

Render and compute key preimages must name their key kind explicitly so a
render and compute key with similar module/layout facts cannot collide in
diagnostics or cache entries.

Render preimages that use material WGSL must also name the
`GPUMaterialAssemblyPlan` hash or preimage label. `GPUMaterialProgramID` alone
is not enough because it is dictionary-local and version scoped.

## `GPUCapabilities`

`GPUCapabilities` describes the selected `GPU` facade implementation and
adapter limits relevant to route selection and pipeline creation.

It includes:

- texture formats and usages;
- shader feature support;
- limits affecting buffers, bindings, and workgroup sizes;
- depth/stencil support;
- multisample support;
- storage buffer support;
- storage texture and compute shader support;
- workgroup size and dispatch limit support;
- timestamp/query support when used for evidence;
- implementation identity such as browser/native/Dawn/pure Kotlin when
  available.

Capabilities may change between implementations of the same facade. Pipeline
keys must include only the capability facts that affect validity or behavior.

## `GPUResourceProvider`

`GPUResourceProvider` owns creation, lookup, and lifetime of GPU resources.
Execution context, command scopes, and submission behavior are defined in
`10-gpu-execution-context-submission.md`; this resource contract supplies the
objects those scopes consume. Texture and image ownership descriptors are
defined in `18-texture-image-ownership.md`. Encoded image decode, bitmap
preparation, codec registry, animation frame selection, color/orientation
handling, and uploaded image artifact keys are defined in
`22-image-bitmap-codec-pipeline.md`. Filter graph descriptors, node plans,
runtime-effect filter descriptors, and filter intermediate plans are defined
in `23-filter-effect-pipeline.md`. Clip stack descriptors, scissor/analytic
plans, stencil producer-consumer plans, coverage-mask plans, shader clip plans,
and clip diagnostics are defined in `24-clip-stencil-mask-pipeline.md`.

It is responsible for:

- render pipelines;
- compute pipelines;
- shader modules;
- bind group layouts;
- bind groups;
- buffers;
- textures;
- texture views;
- samplers;
- imported texture materialization and refusal;
- surface texture lease materialization;
- materializing gathered uniform, storage, texture, sampler, and artifact
  payload bindings;
- coverage/path/glyph atlases when owned by this renderer;
- `CPUPreparedGPUArtifactRegistry` lookup and accounting;
- upload staging resources;
- cache eviction and diagnostics;
- device generation validation.

It must report stable diagnostics for unsupported usage, allocation failure,
validation failure, and device-generation mismatch.

GPU-native resources stay under normal `GPUResourceProvider` ownership. A
texture that already exists as a GPU-native texture, swapchain/surface texture,
render target, or imported GPU handle is not a `CPUPreparedGPU` artifact just
because it is bound by a draw. It becomes an `UploadedTextureArtifact` only
when CPU work decodes, converts, repacks, color-converts, tiles, or
mip-prepares pixels before upload for GPU consumption.

## Cache Layers

Expected cache layers:

- material module cache keyed by `MaterialKey`, `GPUMaterialAssemblyPlan`,
  dictionary version, and WGSL fragment versions;
- material dictionary cache keyed by `MaterialKey`, dictionary version, and
  `GPUMaterialProgramID`;
- render pipeline cache keyed by `GPURenderPipelineKey`;
- compute program/module cache keyed by `GPUComputeProgramKey` plus
  `WGSLComputeModule` identity;
- compute pipeline cache keyed by `GPUComputePipelineKey`;
- layout cache keyed by bind group and uniform layout identity;
- payload cache keyed by pass-local uniform/resource payload blocks;
- sampler cache keyed by sampler descriptor;
- texture/resource cache keyed by explicit resource descriptors;
- texture ownership/materialization cache keyed by `GPUTextureDescriptor`,
  `GPUTextureViewDescriptor`, `GPUSamplerDescriptor`, and accepted ownership
  plan facts;
- image/codec cache keyed by `GPUEncodedImageSource`,
  `GPUImageCodecDescriptor`, `GPUImageDecodeRequest`,
  `GPUAnimatedImagePlan`, `GPUImageColorDecodePlan`,
  `GPUImageOrientationPlan`, and `GPUImageUploadArtifactKey` facts from
  `22-image-bitmap-codec-pipeline.md`;
- atlas caches with explicit ownership and eviction rules;
- clip descriptor, effective element, scissor/analytic/stencil/mask, shader
  clip, budget, and diagnostic cache entries keyed by `GPUClipStackDescriptor`,
  `GPUClipPlan`, `GPUClipBoundsPlan`, `GPUClipStencilPlan`,
  `GPUClipMaskPlan`, and `GPUClipShaderPlan` facts from
  `24-clip-stencil-mask-pipeline.md`;
- text atlas/resource cache keyed by `GPUTextAtlasDescriptor`,
  `GPUTextAtlasPageDescriptor`, artifact generation, upload plan, and accepted
  text ownership facts from `21-text-glyph-pipeline.md`;
- text instance buffer cache or allocator keyed by `GPUTextInstanceLayout`,
  buffer usage, owner scope, and frame/recording generation;
- destination-read target snapshot and intermediate resources with explicit
  ownership, generation, pass-split, and budget rules from
  `20-destination-read-strategy.md`;
- filter graph, node, intermediate, runtime-effect, and bounds cache entries
  keyed by `GPUFilterGraphDescriptor`, `GPUFilterNodeDescriptor`,
  `GPUFilterBoundsPlan`, `GPUFilterIntermediatePlan`, and
  `GPUFilterRuntimeEffectPlan` facts from `23-filter-effect-pipeline.md`;
- `CPUPreparedGPUArtifactRegistry` keyed by typed artifact descriptors.

Cache hits and misses must be observable in conformance or PM evidence before
performance claims are made.
The telemetry, budget, warmup, and performance-gate reporting contract is
defined in `13-performance-telemetry-cache-gates.md`.
Path and coverage atlas cache keys, residency facts, budgets, use tokens,
retry/split actions, and mutation diagnostics are defined in
`19-path-coverage-atlas-strategy.md`.

## Resource Lifetimes

Resources must be tied to:

- device generation;
- execution context generation;
- target generation when applicable;
- surface texture lease generation when applicable;
- encoded image source generation and codec registry generation when an
  uploaded image artifact is involved;
- recording lifetime when one-shot;
- frame scope when frame-local;
- cache lifetime when reusable;
- atlas generation for CPU-prepared GPU artifacts.

No command may hold a stale resource silently. Stale resources must trigger
rebuild, discard, or refusal with diagnostics.

## `CPUPreparedGPUArtifactRegistry`

`CPUPreparedGPU` is intentionally broad as a route kind, but it is never an
untyped compatibility bucket. The resource contract is a registry of typed
artifacts. Route selection may choose `CPUPreparedGPU` only by naming one or
more registered artifact types that the GPU consumes during command encoding.

The registry owns:

- accepted artifact type IDs and descriptor versions;
- stable artifact key construction and diagnostic preimages;
- lookup, creation, upload, and reuse policy;
- device-generation and atlas-generation validation;
- lifetime class: frame-local, recording-local, cache-resident, or
  atlas-resident;
- invalidation rules for every content-affecting input;
- per-artifact and aggregate memory budgets;
- cache hit, miss, eviction, upload, refusal, and stale-entry counters;
- stable diagnostics for unsupported type, unsupported descriptor, budget
  pressure, invalidation, stale GPU handle, upload failure, and device loss.

Artifacts are resources. They may reference GPU handles owned by
`GPUResourceProvider`, but the artifact key describes the CPU preparation
result, not the transient WebGPU object address. A registry entry must not be
used as a correctness dependency when lookup fails; it must rebuild within
budget or return a deterministic refusal.

## CPUPreparedGPU Artifact Types

Accepted artifact families for this kernel are:

| Artifact | CPU preparation | GPU consumption |
|---|---|---|
| `CoverageMaskArtifact` | Rasterize or pack coverage/mask data for a bounded geometry/clip strategy. | Sample mask texture or coverage buffer in a GPU draw, including clip masks governed by `24-clip-stencil-mask-pipeline.md`. |
| `PathAtlasArtifact` | Prepare reusable path coverage, mask, or path-specific atlas entry. | Sample or reference atlas entry during a path/coverage draw. |
| `GlyphAtlasArtifact` | Rasterize and pack A8 glyph masks owned by pure Kotlin text infrastructure. | Sample A8 glyph atlas during text/glyph coverage draws as defined in `21-text-glyph-pipeline.md`. |
| `SDFGlyphAtlasArtifact` | Generate and pack SDF glyph masks owned by pure Kotlin text infrastructure. | Sample SDF glyph atlas and reconstruct coverage in WGSL as defined in `21-text-glyph-pipeline.md`. |
| `GlyphUploadPlan` | Describe text-stack-owned atlas or glyph artifact bytes that must reach GPU resources. | Feed `GPUTextUploadPlan` and upload-before-sample ordering. |
| `OutlineGlyphPlan` | Prepare immutable glyph outline facts owned by text infrastructure. | Route through GPU path/coverage strategy or refuse. |
| `ColorGlyphPlan` | Prepare glyph-scoped COLR/CPAL paint graph facts. | Route through accepted GPU color glyph composite primitives or refuse. |
| `BitmapGlyphPlan` | Prepare decoded PNG bitmap glyph facts. | Route through glyph-scoped texture ownership and sampling or refuse. |
| `SVGGlyphPlan` | Prepare bounded pure Kotlin SVG-in-OpenType glyph vector facts. | Route through accepted vector/material primitives or refuse. |
| `UploadedTextureArtifact` | Decode, convert, repack, color-convert, tile, compose animated frames, or mip-prepare CPU pixels before upload as defined in `22-image-bitmap-codec-pipeline.md`. | Bind uploaded texture and sampler for GPU image sampling through spec 18 ownership. |
| `PrecomputedGeometryArtifact` | Flatten, stroke, tessellate, or pack vertex/index/edge data on CPU. | Bind GPU buffers for a render step such as fan, stencil-cover, or edge coverage. |
| `FilterIntermediateArtifact` | Materialize a validated bounded filter intermediate according to `23-filter-effect-pipeline.md`. | Bind intermediate texture/view for a later GPU filter or layer-composite pass. |

`FilterIntermediateArtifact` is allowed only for filter shapes whose spec has
validated the intermediate ownership, coordinate space, usage flags, clear
policy, and release behavior. Unvalidated filter graphs must refuse with a
stable diagnostic instead of materializing an implicit CPU or GPU scratch path.

Ordinary uniforms, bind groups, GPU-native textures, render targets, and
already-GPU-resident resources remain normal `GPUResourceProvider` resources.
They do not become `CPUPreparedGPU` artifacts unless CPU preparation creates a
typed artifact listed above.

## Artifact Keys And Invalidation

`CPUPreparedGPU` artifacts are resources too. Their key must include all facts
that affect their contents:

- artifact type and descriptor version;
- source geometry or image identity;
- transform facts when rasterization depends on transform;
- clip facts when preparation is clipped;
- style and stroke facts;
- coverage quality;
- color-space or alpha handling when pixels are prepared;
- atlas content key, atlas generation, coordinates, and entry reference facts
  when an artifact is atlas-resident.

Atlas-resident path and coverage artifacts must distinguish a stable content
key from mutable residency facts. `GPUPathAtlasKey` and `GPUCoverageAtlasKey`
identify prepared coverage contents. `GPUAtlasEntryRef`, atlas generation,
page/plot generation, coordinates, and use tokens validate current residency
and may change without becoming material identity.

Additional key facts are required when they affect contents or validity:

- font, strike, glyph, subpixel, and text-raster settings for glyph artifacts;
- text artifact route, text atlas descriptor, SDF spread, glyph artifact
  generation, and glyph-scoped color/bitmap/SVG plan version when text artifacts
  are used;
- path fill rule, stroke expansion, tolerance, edge budget, and atlas policy;
- image decode source, codec descriptor/configuration, still or animated frame
  selection, color/profile conversion, orientation, pixel format, row stride,
  tile mode, mip policy, and upload format for uploaded textures as defined in
  `22-image-bitmap-codec-pipeline.md`;
- filter graph node identity, input bounds, crop, sample mode, intermediate
  format, runtime-effect descriptor, bounds plan, and validated filter-spec
  version from `23-filter-effect-pipeline.md` for filter intermediates;
- clip stack descriptor, effective element list, clip operations, shape keys,
  transforms, AA modes, mask bounds, combine/invert semantics, and validated
  clip-spec version from `24-clip-stencil-mask-pipeline.md` for clip coverage
  artifacts;
- device capability facts when they change prepared format, layout, usage, or
  limits.

CPU-prepared artifacts must be invalidated when any content-affecting fact
changes. They must also be invalidated, rebuilt, or refused when:

- the owning device generation changes;
- the atlas generation changes;
- a budget eviction removes required residency;
- an upload or staging buffer fails;
- a resource handle is stale for the current command submission;
- the active spec version no longer accepts the descriptor.

Every promoted artifact family must expose diagnostics for stable key preimage,
compact key hash, lifetime class, resident bytes, upload bytes, budget used and
remaining, invalidation reason, and refusal reason when preparation is not
allowed.

## Non-Goals

- Do not build a cache that assumes only one future `GPU` implementation.
- Do not put per-draw or per-dispatch values in pipeline keys.
- Do not make cache success a correctness dependency.
- Do not hide resource allocation failures by falling back to CPU rendering.
- Do not add a generic untyped `CPUPreparedGPU` cache entry.
- Do not represent full CPU-rendered draw results as `CPUPreparedGPU`
  artifacts.
- Do not claim persistent pipeline storage until measured and specified.
- Do not route compute or filter identity through `MaterialKey`.
