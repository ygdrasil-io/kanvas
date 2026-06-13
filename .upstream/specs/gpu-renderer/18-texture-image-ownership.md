# Texture And Image Ownership

Status: Draft
Date: 2026-06-13

## Purpose

Define the texture and image ownership contract for the GPU-first renderer.

This spec closes the Graphite `TextureProxy` / `TextureProxyView` gap for
Kanvas. It defines how logical image sources, texture descriptors, texture
views, samplers, surface leases, uploaded CPU pixels, imported textures, and
GPU-resident resources move from normalized commands into payload bindings and
resource preparation.

Encoded image decode, bitmap preparation, codec registry selection, animated
frame composition, color/profile conversion, orientation, mip preparation, and
uploaded image artifact key construction are defined in
`22-image-bitmap-codec-pipeline.md`. This spec consumes the resulting
`UploadedTextureArtifact` and owns the generic texture/view/sampler resource
plan.
Detailed color-management descriptors and sampled image value specs are
defined in `29-color-management-pipeline.md`.
Texture texel-space, normalized-space, view-origin, sampling coordinate, and
payload transform rules are defined in
`30-coordinate-transform-bounds-policy.md`.

The target is Graphite-inspired but Kanvas-owned:

- texture/image facts are explicit descriptors and plans;
- actual `GPU` facade handles stay behind `GPUResourceProvider`;
- sampled bindings enter draws through `GPUPayloadGatherer` slots;
- CPU pixels reach the GPU only through `UploadedTextureArtifact`;
- missing ownership, usage, generation, or binding facts refuse with stable
  diagnostics instead of falling back to CPU rendering.

## Graphite Evidence

Relevant Graphite concepts:

- `TextureProxy` is a logical texture object that may or may not already have a
  concrete `Texture`. It records dimensions, `TextureInfo`, label, budget,
  lazy/volatile state, and an optional concrete texture.
- `TextureProxyView` is a lightweight view of a proxy with swizzle and origin.
  It does not own the concrete GPU resource independently.
- `Texture` is the concrete resource owned by Graphite resource providers. It
  has dimensions, texture info, mutable backend state, release callbacks, and
  upload behavior.
- `Image_Graphite` wraps a `TextureProxyView` plus color information. It can
  wrap a device target when texturable, or copy a source view into a new
  texturable proxy when required.
- `MakeBitmapProxyView()` creates a texture proxy and an upload source from CPU
  bitmap pixels. This is an explicit upload path, not a CPU-rendered fallback.
- `TextureDataBlock` / `TextureDataCache` collect sampled texture and sampler
  bindings for a draw pass. Graphite can use proxy pointer identity locally,
  while retaining references so the draw pass can prepare and track resources.
- `DrawPass::prepareResources()` validates that sampled texture proxies are
  instantiated or lazy before command encoding.

Kanvas adopts these separations. Kanvas does not copy Graphite's C++ proxy
classes, raw pointer identity, lazy callbacks, backend texture abstraction,
SkSL machinery, or CPU image provider fallback.

## Ownership Boundary

`GPUResourceProvider` is the only renderer component that materializes texture,
texture-view, sampler, import, upload, target-lease, and bind-group resources
into concrete `GPU` facade objects.

`GPUTextureOwnershipPlan` is the planning product that proves a texture or
image source can be used by a route. It is created before payload gathering and
consumed by pass/resource preparation.

`GPUPayloadGatherer` collects accepted sampled texture bindings, but it does
not create, import, upload, evict, or release textures.

`GPUMaterialDictionary` declares texture and sampler ABI requirements through
snippet metadata. It does not own texture lifetime, uploads, imports, or
surface leases.

`MaterialKey` identifies render-material behavior. It may include image source
kind and WGSL layout facts. It must not include concrete resource handles,
pixels, artifact cache keys, imported handles, surface leases, or resource
generation IDs.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUTextureDescriptor` | Dumpable logical texture descriptor: dimensions, format, usage requirements, sample count, mip policy, protection flag when supported, and label. |
| `GPUTextureViewDescriptor` | Dumpable view descriptor: texture descriptor ID, view dimension, format override when accepted, aspect, mip range, array layer range, origin, swizzle policy, subset, and sample type. |
| `GPUSamplerDescriptor` | Dumpable sampler descriptor: filter, mipmap, address modes, compare mode when accepted, LOD clamp, anisotropy when accepted, and capability requirements. |
| `GPUImageSourceDescriptor` | Material-side logical image source descriptor before resource materialization. |
| `GPUTextureOwnershipPlan` | Accepted route plan for provenance, owner, lifetime, generation, usage, view, sampler, and refusal behavior. |
| `GPUTextureAllocationPlan` | Explicit allocation/import/upload/lease plan consumed by `GPUResourceProvider`; no hidden lazy callback. |
| `GPUTextureResourceRef` | Recording-local reference to a concrete provider-owned texture resource. It is not a durable key or public facade handle. |
| `GPUImportedTextureDescriptor` | Dumpable contract for externally owned GPU texture import. |
| `GPUTargetTextureDescriptor` | Dumpable descriptor for render targets, layer intermediates, and copy/readback-eligible target textures. |
| `GPUSurfaceTextureLease` | Frame/target-generation lease for a current surface or swapchain texture. |
| `GPUSampledTextureBinding` | Payload binding record for one sampled texture view plus sampler. |
| `GPUTextureDiagnostic` | Structured diagnostic product for accepted or refused texture/image routes. |

These objects use Kanvas package responsibility names under
`org.graphiks.kanvas.gpu.renderer`. `GPUTextureDescriptor`,
`GPUTextureViewDescriptor`, `GPUSamplerDescriptor`,
`GPUTextureResourceRef`, `GPUTextureAllocationPlan`,
`GPUImportedTextureDescriptor`, and `GPUTargetTextureDescriptor` belong with
resource contracts. `GPUImageSourceDescriptor` belongs with material
descriptors. `GPUSampledTextureBinding` belongs with payload contracts.
`GPUSurfaceTextureLease` belongs with execution contracts.

## `GPUImageSourceDescriptor`

`GPUImageSourceDescriptor` is the material-facing image source fact carried by
normalized image materials and image draw commands.

It records:

- source kind: GPU-resident texture, CPU pixel source, imported GPU texture,
  surface/target texture, atlas-backed image, filter intermediate, or refused;
- logical dimensions and subset;
- color type, alpha type, premul convention, and color-space tag;
- tile mode and sampling policy requested by the material;
- mip requirement;
- local-coordinate and transform facts needed by sampling WGSL;
- source provenance label for diagnostics;
- stable refusal reason when the source cannot enter the core.

It must not contain:

- `SkImage`, `SkBitmap`, `SkPixmap`, `SkSurface`, or mutable legacy objects;
- raw `GPU` facade texture objects;
- imported native/Dawn/browser handles;
- pixel byte arrays unless they are wrapped by a typed CPU source descriptor
  that can become `UploadedTextureArtifact`;
- cache pointers, object addresses, or mutable image unique IDs.

When an adapter cannot convert an image-like legacy object into a stable
`GPUImageSourceDescriptor`, it refuses during normalization.

## Texture Provenance

Every accepted `GPUTextureOwnershipPlan` names one provenance:

| Provenance | Route class | Policy |
|---|---|---|
| `GPUResidentTexture` | `GPUNative` | Texture is already owned by the current `GPUResourceProvider` generation. |
| `UploadedTextureArtifact` | `CPUPreparedGPU` | CPU pixels were decoded, converted, repacked, color-converted, tiled, or mip-prepared before upload. |
| `ImportedGPUTexture` | `GPUNative` when accepted | External owner provides a GPU texture under a dumpable import and release contract. |
| `SurfaceTextureLease` | `GPUNative` when accepted | Current surface/swapchain texture leased for the frame/target generation. |
| `RenderTargetTexture` | `GPUNative` | Offscreen target, layer intermediate, or filter intermediate created as an ordinary GPU resource. |
| `AtlasTexture` | `CPUPreparedGPU` or `GPUNative` by owning spec | Path, glyph, mask, or coverage atlas texture with atlas generation policy. |
| `RefuseDiagnostic` | none | Ownership, usage, generation, or capabilities are insufficient. |

GPU-native, imported, surface, swapchain, offscreen, render-target, filter
intermediate, and atlas textures remain ordinary `GPUResourceProvider`
resources unless a typed CPU-prepared artifact creates their contents.

## Descriptor Rules

`GPUTextureDescriptor` includes all facts that affect resource compatibility:

- width, height, depth or array layers when accepted;
- format and sample type;
- usage flags: render attachment, texture binding, storage binding, copy
  source, copy destination, upload destination, readback source, presentation;
- sample count;
- mip level count and mip policy;
- storage access when storage textures are accepted;
- protected/content flags only when the selected facade exposes them;
- device-generation and capability requirements;
- diagnostic label.

`GPUTextureViewDescriptor` includes all facts that affect shader binding or
view compatibility:

- view dimension;
- sample type;
- multisample flag;
- storage format/access when applicable;
- view format override when supported;
- aspect;
- mip range;
- array layer range;
- origin and swizzle policy;
- subset and normalized coordinate convention when the image source requires
  one.

WebGPU does not expose arbitrary texture swizzle as a portable view feature.
If swizzle is required, the accepted route must either express it in WGSL,
prove the texture format already matches the shader convention, or refuse with
`unsupported.texture.swizzle_unimplemented`.

`GPUSamplerDescriptor` is a resource descriptor, not a texture owner. It may
contribute to `MaterialKey`, `WGSLBindingLayout`, or pipeline keys only when it
changes WGSL code, binding layout, sample type compatibility, or pipeline
validity. Ordinary sampler values are payload/resource binding facts.

## Ownership Plans

`GPUTextureOwnershipPlan` records:

- image source descriptor hash and label;
- texture provenance;
- owner scope: `GPUSharedScope`, `GPURecorderScope`, `GPUFrameScope`,
  `GPUAtlasScope`, external importer, or surface target;
- lifetime class: shared cache, recording-local, frame-local, atlas-resident,
  imported external, surface lease, or one-shot;
- texture descriptor and view descriptor;
- sampler descriptor when sampled;
- required usage flags;
- generation facts: device, queue when relevant, target, surface lease, atlas,
  upload artifact, and resource generation;
- allocation, import, upload, or lease plan;
- payload binding role and expected bind group;
- budget and eviction policy when cache-resident or artifact-backed;
- rebuild, discard, or refusal policy for stale resources;
- diagnostic reason for selection or refusal.

The plan is a structured product. It may be dumped, cached, or compared by
descriptor facts. It must not contain raw facade handles or callbacks that can
materialize resources outside `GPUResourceProvider`.

## Allocation And Deferred Materialization

Kanvas uses explicit `GPUTextureAllocationPlan` values instead of Graphite's
public lazy proxy callbacks.

Accepted allocation plan kinds:

- `ExistingGPUResource`: reuse a provider-owned texture resource ref after
  generation and usage validation.
- `CreateTexture`: allocate a new provider-owned texture from a descriptor.
- `UploadFromArtifact`: resolve or create an `UploadedTextureArtifact`, upload
  it, then bind the resulting texture.
- `ImportExternalTexture`: import an external GPU texture under an accepted
  import contract.
- `LeaseSurfaceTexture`: acquire the current surface texture lease for the
  frame/target generation.
- `ReuseAtlasTexture`: bind an atlas texture through its atlas generation and
  artifact owner.
- `Refuse`: no materialization is allowed.

Deferred materialization is allowed only as a provider-owned plan. A recording
may carry `GPUTextureAllocationPlan` metadata, but command encoding must see a
valid provider-owned resource ref or a deterministic refusal.

Fully-lazy, dimensionless public texture proxies are not accepted as a Kanvas
core concept. When dimensions are not known, the route refuses until a future
accepted spec defines a bounded dynamic-size resource contract.

## Uploaded CPU Pixels

`UploadedTextureArtifact` is the only accepted path from CPU pixels to a
sampled GPU image texture in this target.

For encoded images, animated images, and already-decoded CPU image pixels, the
artifact descriptor and key are produced by
`22-image-bitmap-codec-pipeline.md`. This spec validates that the artifact can
be materialized as a texture with the requested usage, view, sampler, owner
scope, lifetime, and device generation.

The upload descriptor must include:

- artifact type and descriptor version;
- source identity and invalidation facts;
- decoded or prepared dimensions;
- pixel format and row stride;
- color type, alpha type, premul convention, and color-space handling;
- tile or subset preparation when performed on CPU;
- mip generation source and mip level count;
- upload texture format and usage flags;
- staging scope and upload-before-use dependency;
- memory/upload budget class;
- device-generation validation.

An uploaded texture artifact is not a CPU-rendered compatibility result. It may
contain decoded or prepared source pixels. It must not contain a full draw,
layer, filter DAG, or unsupported scene rendered by the CPU for GPU composite.

If CPU pixels are present but the source cannot be described by a stable
`UploadedTextureArtifact` key, route selection returns
`unsupported.texture.upload_artifact_missing` or a more specific refusal.

## Imported Textures

Imported GPU textures are accepted only when the import contract is dumpable
and validated for the selected `GPU` facade implementation.

`GPUImportedTextureDescriptor` records:

- external owner identity class, not object address;
- dimensions, format, sample count, mip policy, and usage flags;
- current device-generation compatibility;
- synchronization and visibility assumptions;
- lifetime and release policy;
- whether Kanvas may sample, copy, render to, or store into the texture;
- readback limitations when used for evidence;
- stable refusal behavior for missing facts.

When a facade implementation cannot expose enough import facts, the route must
refuse. Kanvas must not infer validity from a raw handle existing.

## Surface And Target Textures

`GPUSurfaceTarget` remains a safe-to-dump target descriptor. It is not a raw
surface texture handle.

When command encoding needs the current surface/swapchain texture, the
execution layer creates a `GPUSurfaceTextureLease`.

The lease records:

- target ID and target generation;
- frame generation;
- dimensions and format;
- usage flags exposed by the surface texture;
- presentation state;
- whether the lease can be sampled, copied, rendered to, or read back;
- release/present ownership;
- stale or invalid lease reason.

Surface leases are frame-local. They cannot be stored in material keys,
pipeline keys, reusable recordings, artifact keys, or shared caches.

Sampling a texture that is active as the current render attachment is refused
unless an accepted intermediate/copy route has created a separate sampled
resource with validated ordering. Destination-read copy/intermediate routes
are defined in `20-destination-read-strategy.md`.

## Render Targets And Intermediates

Layer, filter, destination-read, and offscreen targets use
`GPUTargetTextureDescriptor` plus ordinary `GPUResourceProvider` ownership.
Layer target role, initialization, source/filter/composite usage, lifetime, and
budget requirements are defined by `GPULayerTargetPlan` and
`GPULayerResourcePlan` in `28-layer-savelayer-execution.md`.
Destination-copy targets are created only through `GPUDestinationCopyPlan` and
`GPUDestinationCopyTextureDescriptor` from
`20-destination-read-strategy.md`.

An intermediate target descriptor records:

- target role: layer source, filter intermediate, destination-copy,
  resolve target, readback source, or scratch;
- size and coordinate mapping;
- format, alpha, premul, and color-space facts;
- usage flags;
- clear/load/store policy;
- lifetime class and budget;
- generation and invalidation facts;
- whether the target may later be sampled, copied, read back, or presented.

`FilterIntermediateArtifact` is used only when CPU preparation creates a typed
artifact accepted by `23-filter-effect-pipeline.md`. Ordinary GPU filter
intermediates are not `CPUPreparedGPU` artifacts.

## Atlas Textures

Atlas textures for paths, glyphs, clip masks, other masks, and coverage are
owned by their atlas or artifact specs.

Texture/image ownership rules still apply:

- atlas generation is part of resource validation;
- atlas coordinates are payload/artifact facts, not material durable identity;
- stale atlas entries rebuild, evict, or refuse deterministically;
- atlas textures are not user image textures unless an accepted spec creates
  such a route explicitly.

The detailed path/coverage atlas strategy is defined in
`19-path-coverage-atlas-strategy.md`. That spec owns `GPUPathAtlasPlan`,
`GPUCoverageAtlasPlan`, atlas entry refs, use tokens, mutation plans,
retry/split behavior, and atlas-specific diagnostics.

The detailed clip/stencil/mask strategy is defined in
`24-clip-stencil-mask-pipeline.md`. That spec owns `GPUClipMaskPlan`,
`GPUClipShaderPlan`, clip coverage-mask resource needs, ordering tokens, and
clip-specific diagnostics while this spec owns generic texture/view/sampler
validation.

The detailed text/glyph atlas and bitmap glyph texture strategy is defined in
`21-text-glyph-pipeline.md`. That spec owns `GPUTextAtlasPlan`,
`GPUTextAtlasDescriptor`, `GPUTextAtlasPageDescriptor`,
`GPUTextAtlasEntryRef`, `GPUTextUploadPlan`, and `GPUTextBinding` for A8/SDF
text atlas sampling, bitmap glyph sampling, and text upload-before-sample
ordering.

## Key Boundaries

`MaterialKey` may include:

- image source kind;
- immutable image sampling class when it changes WGSL code or layout;
- tile mode class when it changes WGSL behavior;
- sample type and binding layout identity;
- color-space or premul behavior flags when material code depends on them;
- registered runtime-effect child image slot shape.

`MaterialKey` must not include:

- `GPUTextureResourceRef`;
- raw `GPU` texture, texture view, sampler, or imported handle;
- `UploadedTextureArtifact` key or artifact cache residency;
- pixel contents;
- surface lease ID;
- resource generation;
- atlas coordinates;
- atlas entry refs and atlas use tokens;
- clip stack descriptors, clip ordering tokens, clip mask coordinates, and
  stencil state;
- text atlas entry refs, glyph IDs, atlas generations, and text upload tokens;
- payload slot IDs.

`GPURenderPipelineKey` and `GPUComputePipelineKey` include texture/view/sampler
layout facts only when those facts affect pipeline validity, WGSL module
layout, or fixed state. They do not include actual resource handles or texture
contents.

`UploadedTextureArtifact` keys include CPU-prepared content facts because those
facts define the artifact contents. Artifact keys are not material keys.

## Payload Binding

`GPUSampledTextureBinding` is the texture-specific record inside
`GPUResourceBindingBlock`.

It records:

- binding layout hash;
- image source descriptor label;
- ownership plan ID;
- texture descriptor hash;
- view descriptor hash;
- sampler descriptor hash;
- resource ref or pending allocation plan reference;
- required usage flags;
- device/target/atlas/upload generation facts needed for validation;
- diagnostic label.

`GPUPayloadGatherer` may collect these records and assign
`GPUResourceBindingSlot` values. It may not allocate, import, upload, lease, or
release the texture.

If the gatherer observes a missing or refused ownership plan, the draw remains
refused or unpromoted with a stable diagnostic. It must not substitute an
uploaded CPU fallback texture.
When the sampled resource is a destination copy or existing destination
intermediate, `GPUDestinationReadBinding` from
`20-destination-read-strategy.md` records the destination-read-specific bounds,
target generation, and binding facts.

## Routing Policy

Route selection for image and texture sources follows:

1. Validate `GPUImageSourceDescriptor`.
2. Try `GPUNative` when the source is GPU-resident, imported with an accepted
   contract, surface/target-backed with a valid lease, or atlas-backed by an
   accepted owner.
3. Try `CPUPreparedGPU(UploadedTextureArtifact)` only when CPU pixels require
   decode, conversion, repack, color conversion, tiling, or mip preparation
   before upload.
4. Return `RefuseDiagnostic`.

`CPUReferenceOnly` may render or sample images for oracle evidence. It is not a
product fallback and cannot produce a texture that product GPU rendering
silently composites.

## Diagnostics

Every accepted or refused texture/image route must produce
`GPUTextureDiagnostic`.

Fields:

- command ID or resource owner;
- image source descriptor hash and label;
- texture provenance;
- selected route or refusal;
- ownership plan ID;
- texture descriptor and view descriptor hashes;
- sampler descriptor hash when sampled;
- owner scope and lifetime class;
- usage flags requested and available;
- device, target, surface, atlas, upload, and resource generations;
- allocation/import/upload/lease plan kind;
- artifact key hash when `UploadedTextureArtifact` is used;
- memory/upload budget decision when relevant;
- payload binding slot when materialized;
- stable reason code.

Stable reason-code examples:

- `unsupported.texture.descriptor_invalid`
- `unsupported.texture.ownership_missing`
- `unsupported.texture.usage_missing`
- `unsupported.texture.sample_type`
- `unsupported.texture.view_dimension`
- `unsupported.texture.swizzle_unimplemented`
- `unsupported.texture.mipmap_unavailable`
- `unsupported.texture.upload_artifact_missing`
- `unsupported.texture.cpu_pixels_require_uploaded_artifact`
- `unsupported.texture.import_unvalidated`
- `unsupported.texture.import_lifetime`
- `unsupported.texture.surface_lease_stale`
- `unsupported.texture.active_attachment_sampled`
- `unsupported.texture.device_generation_stale`
- `unsupported.texture.target_generation_stale`
- `unsupported.texture.atlas_generation_stale`
- `unsupported.texture.budget_exceeded`
- `unsupported.texture.allocation_failed`
- `unsupported.texture.bind_group_materialization_failed`

## Validation Requirements

Promoted texture/image behavior requires:

- canonical dumps for `GPUImageSourceDescriptor`, `GPUTextureDescriptor`,
  `GPUTextureViewDescriptor`, `GPUSamplerDescriptor`, and
  `GPUTextureOwnershipPlan`;
- material-key tests proving texture handles, pixel contents,
  `UploadedTextureArtifact` keys, imported handles, and surface leases are
  excluded;
- pipeline-key tests proving only layout/usage facts that affect validity enter
  executable keys;
- payload tests proving `GPUSampledTextureBinding` order matches
  `WGSLResourceBindingPlan`;
- resource-provider tests for create, reuse, upload, import refusal, lease,
  stale generation, and missing usage flags;
- active-attachment sampling refusal tests;
- uploaded-artifact key and invalidation tests when CPU pixels are accepted;
- imported-texture refusal tests when owner, usage, lifetime, or generation
  facts are not dumpable;
- surface-lease generation tests;
- PM evidence that distinguishes GPU-native images, uploaded CPU pixels,
  imported texture refusals, surface lease state, and texture upload bytes.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice does not activate image
shader routes or sampled image textures.

It may still validate:

- `GPUTargetTextureDescriptor` for the render attachment;
- usage-flag refusal for illegal sampled active attachment;
- stale device-generation refusal through a deterministic test double;
- diagnostic fields required for future sampled texture routes.

The first slice must not create `UploadedTextureArtifact` entries, import
external textures, or route image/bitmap commands as supported.

## Non-Goals

- Do not port Graphite `TextureProxy`, `TextureProxyView`, or resource cache
  implementation.
- Do not introduce a backend abstraction above the `GPU` facade.
- Do not expose raw facade texture handles in material or pipeline keys.
- Do not hide upload/import/lease work inside `GPUPayloadGatherer`.
- Do not treat GPU-native, imported, surface, offscreen, or render-target
  textures as `CPUPreparedGPU` artifacts.
- Do not accept CPU-rendered draw, layer, filter, or scene textures as
  compatibility fallback.
- Do not rely on cache residency as correctness evidence.
