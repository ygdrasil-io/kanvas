# Image Bitmap Codec Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target image, bitmap, codec, and upload-preparation contract for the
GPU-first renderer.

This spec closes the gap between encoded image inputs and sampled GPU textures.
It defines codec registration, encoded source identity, still and animated
decode plans, color/profile/orientation handling, CPU pixel preparation,
upload artifacts, upload scheduling, image cache keys, diagnostics, and
validation gates.

This is a target-complete spec. It is not an implementation slice and it does
not reduce the renderer target to the first deliverable.

The target is Graphite-inspired but Kanvas-owned:

- encoded image and bitmap preparation are explicit plans, not implicit image
  object behavior;
- codec use is mediated through a dumpable Kanvas registry;
- decoded pixels enter the GPU renderer only through typed upload artifacts;
- animation frame selection and frame dependency are part of the plan;
- color management, alpha handling, orientation, bit depth, and HDR facts are
  explicit;
- unsupported or unvalidated decode, conversion, upload, or animation behavior
  refuses with stable diagnostics instead of falling back to CPU rendering.

## Source Specs

This spec depends on:

- `03-material-key-wgsl.md` for material/image source facts and the rule that
  pixels, handles, and upload artifact keys stay out of `MaterialKey`;
- `04-pipeline-key-cache-resources.md` for `GPUResourceProvider`,
  `CPUPreparedGPUArtifactRegistry`, cache domains, and generation policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for image and bitmap family support;
- `10-gpu-execution-context-submission.md` for upload-before-use and
  device-generation rules;
- `11-wgsl-layout-binding-abi.md` for sampled texture binding ABI;
- `12-blend-color-target-state.md` for `GPUColorPlan`, premul, alpha, and
  target color behavior;
- `13-performance-telemetry-cache-gates.md` for budgets, telemetry, and
  quarantine;
- `17-payload-gathering-and-slots.md` for sampled image payload bindings;
- `18-texture-image-ownership.md` for texture/view/sampler ownership,
  imported textures, surface leases, and sampled texture resource binding;
- `21-text-glyph-pipeline.md` for the boundary with text-owned bitmap glyph
  decode and glyph-scoped image plans;
- `24-clip-stencil-mask-pipeline.md` for clipping image draws after accepted
  image source/upload/texture planning.

## Graphite And Skia Evidence

Relevant Skia and Graphite concepts:

- `SkCodec` identifies encoded formats, reports dimensions and metadata,
  selects still-image or animation behavior, and returns structured decode
  result codes such as success, incomplete input, invalid input, invalid
  conversion, invalid scale, invalid parameters, rewind failure, unimplemented,
  internal error, and out-of-memory.
- `SkAndroidCodec` demonstrates a higher-level decode policy layer that chooses
  output color type, alpha type, color space, and sample size while preserving
  encoded format, ICC profile access, and gainmap access.
- `SkCodecAnimation` models animation frame duration, required prior frame,
  disposal method, blend mode, and fully received frame state.
- `SkEncodedOrigin`, `SkParseEncodedOrigin`, and `SkPixmapUtils::Orient` are
  evidence that encoded origin/orientation is a first-class pixel preparation
  step, not a draw-time accident.
- `SkCodec::queryYUVAInfo()` and `SkYUVAPixmaps` are evidence that some codecs
  expose planar YUV/YUVA data. Kanvas treats this as codec metadata and
  conversion input unless a future accepted multi-plane route exists.
- Skia gainmap support in JPEG, PNG, AVIF, and Android codec paths is evidence
  that HDR/gainmap metadata must be planned explicitly. Kanvas must either
  preserve it through an accepted `GPUColorPlan`, tone-map through a validated
  path, or refuse.
- Graphite image-provider and lazy proxy patterns are evidence for deferred
  resource preparation. Kanvas replaces provider callbacks with explicit
  registry, artifact, upload, and ownership plans.
- Graphite `MakeBitmapProxyView()` creates a `TextureProxy`, derives mip
  requirements, builds upload source metadata from bitmap pixels, maps source
  and destination color info, applies read swizzle policy, and either performs
  or records an upload task.
- Graphite `TextureProxyView` and `Image_Graphite` separate a sampled texture
  view from image color information and concrete resource ownership.
- Graphite `UploadTask` makes texture upload an ordered task with source data,
  destination texture, color conversion facts, and command-buffer dependency.

Kanvas adopts these invariants. Kanvas does not copy Skia codec APIs as the
public renderer contract, Graphite C++ proxy classes, pointer identity,
Ganesh/Graphite backends, SkSL machinery, Skia CPU image providers, or hidden
CPU fallback behavior.

## Ownership Boundary

This spec owns image decode and preparation:

- encoded source identity;
- codec registry and codec capability descriptors;
- still image decode plans;
- animated image frame plans;
- color/profile/orientation/alpha/bit-depth/HDR conversion plans;
- CPU pixel layout, repack, and mip preparation plans;
- upload artifact descriptors and artifact keys for decoded or prepared image
  pixels;
- image upload scheduling requirements;
- codec, decode, animation, color, upload, budget, and cache diagnostics.

`18-texture-image-ownership.md` owns generic texture resource ownership:

- `GPUImageSourceDescriptor`;
- `GPUTextureOwnershipPlan`;
- `GPUTextureDescriptor`;
- `GPUTextureViewDescriptor`;
- `GPUSamplerDescriptor`;
- `GPUTextureAllocationPlan`;
- `GPUSampledTextureBinding`;
- imported GPU textures, render-target textures, atlas textures, surface
  texture leases, and provider-owned resource refs.

The bridge is `UploadedTextureArtifact`. This spec defines how image decode and
preparation create the artifact. Spec 18 defines how an accepted artifact is
materialized as a provider-owned texture and sampled by a draw.

No component may bypass this boundary by treating raw decoded bytes, mutable
legacy image objects, platform codec handles, or external texture handles as
material identity.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUImagePipelinePlan` | Top-level plan for converting one logical image input into one still artifact, one animated image plan, or a stable refusal. |
| `GPUEncodedImageSource` | Dumpable identity for encoded bytes or stream-backed input: source kind, content hash, length when known, provenance, invalidation facts, and label. |
| `GPUCPUImageSource` | Dumpable identity for already-decoded CPU pixels provided by an adapter: dimensions, row stride, pixel format, alpha type, color tag/profile, orientation state, generation, and ownership policy. |
| `GPUImageCodecRegistry` | Registry that selects a `KanvasImageCodec` implementation by encoded format, capability, target platform, determinism policy, and version. |
| `KanvasImageCodec` | Codec interface implemented by pure Kotlin, platform, or external backends under the Kanvas registry and diagnostics contract. |
| `GPUImageCodecDescriptor` | Dumpable codec identity: codec ID, implementation kind, version, supported formats, supported profiles, color behavior, animation behavior, scaling behavior, and determinism facts. |
| `GPUImageDecodeRequest` | Requested decode facts: still/animation selection, frame selection, target dimensions, sample size, requested color/pixel output, premul policy, orientation policy, and budget class. |
| `GPUImageDecodePlan` | Accepted or refused decode route with selected codec, encoded metadata, output pixel plan, color plan, animation plan when applicable, and diagnostic reason. |
| `GPUImageDecodeResult` | Structured result from decoding or preparing CPU pixels: pixel buffer descriptor, frame metadata, conversion facts, warnings, and stable error if refused. |
| `GPUImageFrameInfo` | Per-frame animation metadata: index, duration, required prior frame, dirty rect, disposal method, blend mode, completeness, and dependency facts. |
| `GPUAnimatedImagePlan` | Target plan for animated images: loop count, frame list, frame selection policy, frame cache policy, upload scheduling, and refusal behavior. |
| `GPUImageFrameSelection` | Concrete selection of a frame by time, index, loop iteration, or still-image policy. |
| `GPUImageColorDecodePlan` | Source-to-output color/profile plan before upload: ICC/CICP/profile metadata, transfer, gamut, alpha type, premul policy, bit depth, HDR metadata, and gainmap handling. |
| `GPUImageOrientationPlan` | EXIF/origin/orientation handling and whether pixels are physically reoriented before upload. |
| `GPUImagePixelPlan` | CPU pixel memory contract: width, height, format, component type, row stride, alignment, premul convention, color tag, plane layout when accepted, and zero-copy eligibility. |
| `GPUImageMipmapPlan` | Mip requirements, source level policy, generation route, validation, and refusal behavior. |
| `GPUImageUploadPlan` | Upload-before-sample contract for one prepared image artifact: staging layout, row alignment, texture format, usage, mip levels, dependencies, and budget. |
| `GPUImageUploadArtifactKey` | Durable preimage for an `UploadedTextureArtifact` produced by this spec. |
| `GPUUploadedImageArtifactDescriptor` | Dumpable descriptor for the decoded/prepared pixel artifact consumed by spec 18. |
| `GPUImageCachePlan` | Decode, frame, pixel, artifact, and texture cache policy with keys, generations, invalidation, memory class, and eviction facts. |
| `GPUImageBudgetPolicy` | Per-image and per-recording CPU memory, upload memory, frame cache, decode time, and staging limits. |
| `GPUImageDiagnostic` | Structured diagnostic product for accepted or refused image/codec routes. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU` and `CPU` uppercase to match the
facade vocabulary used above `wgpu4k`.

## Codec Registry

Kanvas uses a hybrid codec registry:

- pure Kotlin codecs are preferred when they satisfy format coverage,
  determinism, performance, and maintenance requirements;
- platform codecs or external libraries are allowed only behind
  `KanvasImageCodec`;
- every codec implementation has a stable descriptor and version;
- codec selection is a recorded planning decision;
- codec output that cannot be made deterministic enough for conformance is
  allowed only as non-normative product behavior and must be labeled as such;
- fixture promotion requires a codec descriptor, source fixture hash, decoded
  output or accepted tolerance, diagnostics, and drift policy.

`KanvasImageCodec` must expose at least:

- codec descriptor;
- capability query by encoded format, profile, color type, bit depth,
  animation, scaling, orientation, and alpha behavior;
- metadata scan without full decode when possible;
- still decode;
- animation metadata decode;
- frame decode with required-frame dependency facts;
- cancellation or bounded failure policy when supported;
- structured result mapping to `GPUImageDiagnostic`.

The registry refuses when:

- no codec is registered for the format;
- multiple codecs are possible and selection is not deterministic;
- the selected codec version is not dumpable;
- platform/external behavior is not permitted by the active conformance tier;
- metadata scan or decode would require unbounded memory or unbounded time.

## Target Encoded Formats

The target image format surface is Skia-like large:

| Format | Target status | Required target behavior |
|---|---|---|
| PNG | `TargetRequired` | Still decode, alpha, color profile, bit depth, interlace policy, orientation if present through container metadata, and deterministic refusal for unsupported chunks. |
| JPEG | `TargetRequired` | Still decode, EXIF orientation, ICC profile, grayscale/RGB/CMYK policy, subsampling policy, and deterministic refusal for unsupported color transforms. |
| WebP | `TargetRequired` | Still and animated decode, alpha, ICC when present, frame metadata, disposal/blend behavior, and profile capability diagnostics. |
| GIF | `TargetRequired` | Animated and still decode, palette handling, transparency, frame duration normalization, disposal, blend, loop count, and required-frame dependencies. |
| BMP | `TargetRequired` | Still decode, bit-depth variants accepted by capability, palette handling, alpha policy, top-down/bottom-up orientation, and stable refusal for unsupported compression. |
| ICO | `TargetRequired` | Multi-image selection, embedded PNG/BMP handling, size/depth selection, alpha/mask handling, and deterministic selection diagnostics. |
| WBMP | `TargetRequired` | Monochrome decode, target pixel conversion, and stable refusal for invalid dimensions or malformed data. |
| HEIF | `DependencyGated` | Still decode when a registered codec supports required profile, color metadata, bit depth, alpha, orientation, and conversion policy. |
| AVIF | `DependencyGated` | Still and animated decode when a registered codec supports profile, bit depth, alpha, HDR metadata, CICP/ICC, frame timing, and conversion policy. |

Formats outside this table, including RAW camera formats and JPEG XL, are not
part of the accepted target until a future spec extends this table. They must
refuse with format-specific diagnostics instead of silently using a platform
decoder.

`TargetRequired` does not mean support is already implemented. It means the
target spec expects support before the image pipeline can be called complete.

## Source Identity

`GPUEncodedImageSource` records:

- source kind: immutable byte array, bounded stream snapshot, memory-mapped
  source, package asset, file-backed source provided by an adapter, network
  cache snapshot provided by an adapter, or refused;
- content hash and hash algorithm;
- byte length when known;
- source mutation or invalidation generation;
- provenance label;
- security/trust classification when the embedding product needs it;
- maximum scan and decode budget class;
- whether metadata-only scan is allowed;
- whether the source can be retained for animation frame decode;
- diagnostic label.

The GPU renderer core does not perform arbitrary file IO or network IO.
Adapters may provide source bytes or bounded source snapshots. If an adapter
cannot provide a stable source identity, normalization refuses before routing.

## Decode Requests

`GPUImageDecodeRequest` records:

- still-image selection, animation selection, or first-frame-as-still policy;
- requested output dimensions or sample size;
- requested subset when supported by the selected codec;
- requested color output and target color behavior;
- requested alpha type and premul policy;
- orientation policy: apply before upload, preserve as sampling transform, or
  refuse when unsupported;
- mip requirement;
- frame index, timestamp, or loop iteration for animated images;
- maximum decoded-pixel memory budget;
- maximum frame dependency depth;
- target GPU texture format preference;
- conformance tier.

No implicit first-frame behavior is allowed. A caller must request still-image
selection or animation behavior explicitly. If an animated input reaches an API
that expects a still image, the accepted policy is recorded as
`FirstFrameStill`, `AnimationUnsupportedRefuse`, or a product-specific adapter
policy with diagnostics.

## Still Image Decode

For a still image, `GPUImageDecodePlan` must record:

- selected codec descriptor and codec registry generation;
- encoded format and profile facts;
- metadata dimensions before orientation;
- output dimensions after sample size, subset, and orientation;
- color/profile conversion plan;
- alpha/premul conversion plan;
- pixel format and row-stride plan;
- mip generation plan;
- upload artifact key preimage;
- budget class and accepted memory cost;
- diagnostics for accepted warnings or refused behavior.

Still decode output becomes `GPUImageDecodeResult`. A successful decode result
is not itself a sampled texture. It must be converted into an
`UploadedTextureArtifact` and then materialized through the spec 18 texture
ownership path before any draw samples it.

## Animated Images

Animated formats are first-class target inputs.

`GPUAnimatedImagePlan` records:

- source identity and selected codec descriptor;
- encoded format and animation profile;
- loop count, including infinite loop representation;
- canvas dimensions and frame bounds;
- normalized frame duration policy;
- frame count when known;
- whether frames can be decoded independently or require previous frames;
- frame cache policy;
- upload scheduling policy;
- memory and upload budget policy;
- stable refusal behavior.

`GPUImageFrameInfo` records:

- frame index;
- presentation duration;
- dirty rect;
- required prior frame index when the frame depends on previous composition;
- disposal method: `Keep`, `RestoreBackground`, or `RestorePrevious`;
- blend mode: `SrcOver` or `Src`;
- completeness flag;
- output color and alpha facts;
- decode warnings or refusal reason.

`GPUImageFrameSelection` records:

- selection mode: `ByIndex`, `ByTimestamp`, `ByLoopAndTimestamp`,
  `FirstFrameStill`, or `Refuse`;
- normalized timestamp and loop iteration;
- selected frame index;
- required dependency chain;
- whether a cached composed frame is required;
- upload artifact key for the selected composed frame.

The renderer must not silently collapse animation to the first frame. It may do
so only when the API explicitly requested still-image behavior and the plan
records that policy.

When a frame requires previous composition, the plan must either:

- decode and compose the required prior frames into a prepared CPU artifact;
- reuse a validated frame cache entry with matching source, codec, color, and
  frame-generation facts;
- refuse with `unsupported.image.animation.required_frame_missing` or a more
  specific diagnostic.

## Color, Alpha, Orientation, And HDR

`GPUImageColorDecodePlan` separates these spaces:

- encoded/source color metadata;
- codec output pixel color space;
- CPU canonical preparation space;
- upload texture format and storage color space;
- shader sampling color space;
- target/output `GPUColorPlan`.

The plan records:

- ICC profile bytes or stable profile hash when available;
- CICP or container color metadata when available;
- gainmap metadata and gainmap companion source identity when available;
- transfer function, gamut, matrix coefficients, range, and white point when
  known;
- source bit depth: 1, 2, 4, 8, 10, 12, 16, or float when accepted;
- output component type;
- alpha type: opaque, unpremul, premul, binary mask, or unknown;
- premul/unpremul conversion route;
- HDR metadata and gainmap metadata when the format carries them;
- gamut mapping and transfer conversion route;
- whether conversion is codec-owned, Kanvas-owned CPU conversion,
  shader-owned, or refused.

If conversion is not validated for the selected codec, profile, bit depth,
alpha type, and target format, the route refuses with
`unsupported.image.color.conversion_unvalidated`.

`GPUImageOrientationPlan` records:

- encoded origin/orientation metadata;
- whether orientation has already been applied by the codec;
- whether Kanvas reorients CPU pixels before upload;
- whether the sampling transform carries orientation;
- output dimensions after orientation;
- refusal reason when the orientation cannot be represented safely.

The target allows either CPU reorientation or sampling-transform orientation,
but the plan must be explicit. It must not double-apply or drop orientation.

HDR-capable and gainmap-capable inputs may route to `RGBA16Float` or another
accepted facade format only when the target, blending, sampling, and validation
evidence support that path. Otherwise they must tone-map through an accepted
`GPUColorPlan` or refuse.

## Pixel Layout And Upload Artifacts

`GPUImagePixelPlan` records:

- width and height after decode, subset, sample size, and orientation;
- pixel format, component type, and channel order;
- row stride and alignment;
- premul convention;
- color tag/profile hash;
- plane layout when accepted;
- byte size and budget class;
- ownership of the pixel buffer;
- zero-copy eligibility when the selected codec and upload path permit it.

The target upload formats are limited to formats exposed by the selected
`GPU` facade and accepted by `GPUTextureDescriptor`. Typical target formats
include `R8Unorm`, `RG8Unorm`, `RGBA8Unorm`, `BGRA8Unorm`, and `RGBA16Float`
when capabilities and color policy allow them.

Planar YUV/YCbCr source formats may be decoded by codecs, but WebGPU does not
provide a portable arbitrary YCbCr sampler contract for this renderer target.
The accepted target route is conversion to an interleaved sampled texture
format, or a future explicit multi-plane shader route. Until that route is
specified and validated, unsupported planar cases refuse.

`GPUImageUploadArtifactKey` must include:

- descriptor version;
- source content hash and invalidation generation;
- codec descriptor ID and version;
- encoded format and profile facts that affect output;
- decode request preimage;
- frame selection preimage for animated images;
- color/profile conversion preimage;
- orientation plan preimage;
- pixel layout and output texture format;
- mipmap plan;
- conformance tier;
- budget class;
- generator version.

The key must not include object addresses, mutable image IDs, platform handle
identity, wall-clock time, thread IDs, or non-deterministic codec output bytes.

`GPUUploadedImageArtifactDescriptor` records:

- artifact key;
- prepared pixel dimensions;
- upload texture descriptor requirements;
- pixel buffer descriptor or provider-owned staging reference;
- row-stride validation result;
- mip levels and byte ranges;
- color/profile facts after conversion;
- alpha and premul convention;
- source frame info when animated;
- upload-before-sample dependency;
- memory and staging budget classification;
- diagnostics.

The resulting artifact is registered as `UploadedTextureArtifact` for spec 18.

## Mipmaps, Scaling, And Subsets

`GPUImageMipmapPlan` records:

- whether mips are required by material sampling, sampler descriptor, or cache
  policy;
- number of levels;
- source for each level: codec-scaled decode, CPU resample, GPU generation, or
  refused;
- filter used for CPU resample when accepted;
- color-space handling during resample;
- budget cost;
- validation evidence requirement.

If a material requires mip sampling and the pipeline cannot provide complete
validated mip levels, the route refuses with `unsupported.image.mip_required`.

Subsets must be explicit. A codec may decode a subset only when its descriptor
claims that capability and the output is covered by fixtures. Otherwise Kanvas
may decode the full image then crop/repack as CPU preparation, or refuse when
budgets are exceeded.

## Texture Ownership Bridge

The image pipeline produces an `UploadedTextureArtifact`. Spec 18 then builds a
`GPUTextureOwnershipPlan` with `UploadedTextureArtifact` provenance.

The bridge records:

- artifact key and descriptor hash;
- target `GPUTextureDescriptor`;
- target `GPUTextureViewDescriptor`;
- target `GPUSamplerDescriptor` when sampled;
- upload plan;
- owner scope: shared cache, recording-local, frame-local, or one-shot;
- device generation and queue dependency;
- texture cache generation after upload;
- binding role and payload slot expectation.

`GPUPayloadGatherer` may gather a sampled image only after a texture ownership
plan accepts the artifact. Payload gathering does not decode images, compose
animation frames, allocate textures, or upload pixels.

`MaterialKey` may include stable image source kind, sampling behavior, and WGSL
layout facts that affect shader code. It must not include upload artifact keys,
decoded pixel hashes, concrete resource refs, codec versions, or frame cache
generations unless those facts change shader behavior.

## Routing

Image routes are:

| Input | Route | Required plan |
|---|---|---|
| Provider-owned GPU texture | `GPUNative` | Spec 18 `GPUTextureOwnershipPlan`; this spec is not involved except for image diagnostics if the high-level source was image-like. |
| Imported GPU texture | `GPUNative` when accepted | Spec 18 import contract; no decode path. |
| Render target, layer, filter, or surface texture | `GPUNative` when accepted | Spec 18 target or lease contract; no decode path. |
| Encoded still image | `CPUPreparedGPU` | `GPUImageDecodePlan` plus `UploadedTextureArtifact`, then spec 18 ownership. |
| Encoded animated image | `CPUPreparedGPU` per selected frame | `GPUAnimatedImagePlan`, frame selection, composed-frame artifact, then spec 18 ownership. |
| Already-decoded CPU pixels | `CPUPreparedGPU` | `GPUCPUImageSource`, color/orientation/pixel/mip preparation as required, then artifact and spec 18 ownership. |
| Unsupported image source | none | `RefuseDiagnostic`. |

`CPUPreparedGPU` is not a CPU-rendered compatibility route. It may decode,
convert, reorient, repack, resample, compose animation frames, or generate mips
as explicit preparation for GPU sampling. It must not render an unsupported
draw, layer, filter, text run, or scene into a texture for composite.

`CPUReferenceOnly` is allowed only for evidence, oracle generation, and tests.
It is not a product route.

## Complete Target Support Matrix

This matrix describes the complete target surface owned or constrained by this
spec. It is not an implementation order.

| Case | Target route | Required behavior |
|---|---|---|
| `DrawImageRect` with provider-owned GPU texture | `GPUNative` | Use spec 18 texture ownership, view, sampler, usage, and lifetime validation; no codec path. |
| `DrawImageRect` with encoded still image | `CPUPreparedGPU` | Decode through `GPUImageDecodePlan`, prepare an `UploadedTextureArtifact`, materialize texture ownership through spec 18, then sample. |
| `DrawImageRect` with already-decoded CPU bitmap/pixels | `CPUPreparedGPU` | Validate `GPUCPUImageSource`, color/orientation/pixel/mip preparation, artifact key, upload plan, and spec 18 ownership. |
| Image shader or child image input | `GPUNative` or `CPUPreparedGPU` by source | Material key records only shader-relevant source/sampling/layout facts; source resource and upload facts stay in image/texture plans. |
| Local matrix, subset, and tile sampling | `GPUNative` after ownership acceptance | Sampling WGSL and payload facts carry coordinate mapping; CPU tiling is allowed only when explicitly requested and keyed. |
| Mipmapped image sampling | `GPUNative` or `CPUPreparedGPU` | Provide complete validated mips through codec scale, CPU resample, GPU generation, or refuse. |
| Animated image as animation | `CPUPreparedGPU` per selected frame | Use `GPUAnimatedImagePlan`, frame selection, required-frame composition/cache, and per-frame upload artifact. |
| Animated image as still | `CPUPreparedGPU` only with explicit policy | Use `FirstFrameStill` or another recorded still-selection policy; never silently collapse animation. |
| Planar YUV/YUVA encoded source | `CPUPreparedGPU` conversion or future route | Convert to accepted interleaved upload texture format, or refuse until a multi-plane WGSL route is specified. |
| HDR or gainmap source | `CPUPreparedGPU` with accepted color plan or refusal | Preserve through accepted HDR target, tone-map through validated `GPUColorPlan`, or refuse. |
| Imported/provider GPU texture | `GPUNative` | No decode. Spec 18 validates import/provider ownership, usage, lifetime, and release facts. |
| Compressed GPU texture upload | `FutureResearch` | Refuse unless a future spec accepts compressed texture formats, upload layout, sampling ABI, and codec/container mapping. |
| Progressive or unbounded stream decode | `RefuseDiagnostic` | Core accepts only bounded source snapshots with stable identity and budget. |

## Cache And Budget Policy

`GPUImageCachePlan` covers:

- metadata scan cache;
- decoded still pixel cache;
- animated frame metadata cache;
- composed frame cache;
- upload artifact cache;
- provider-owned texture cache after spec 18 materialization.

Each cache entry records:

- key preimage;
- source generation;
- codec registry generation;
- color/profile conversion version;
- device generation when GPU-specific;
- memory cost;
- last use frame or logical generation;
- eviction class;
- whether it is conformance-normative or product-only.

`GPUImageBudgetPolicy` records:

- maximum encoded source retention bytes;
- maximum metadata scan bytes;
- maximum decoded pixel bytes per image;
- maximum composed animated frame bytes;
- maximum queued upload bytes;
- maximum frame dependency depth;
- maximum decode time class where measurable;
- retry and quarantine policy after repeated failures.

Budget exhaustion refuses with stable diagnostics. The renderer must not exceed
budgets by silently downgrading to CPU rendering, dropping frames without an
animation policy, ignoring color conversion, or uploading lower-quality pixels
without a recorded request.

## Diagnostics

Diagnostics are structured and stable. They include:

- source label and source hash when available;
- codec descriptor and version when selected;
- encoded format and profile facts;
- decode request preimage;
- selected route;
- accepted warnings;
- refusal reason;
- budget facts;
- whether a fallback was considered and why it was rejected.

Required refusal codes include:

- `unsupported.image.codec.format`
- `unsupported.image.codec.unregistered`
- `unsupported.image.codec.selection_nondeterministic`
- `unsupported.image.codec.version_nondeterministic`
- `unsupported.image.codec.external_not_allowed`
- `unsupported.image.decode.invalid_input`
- `unsupported.image.decode.incomplete_input`
- `unsupported.image.decode.invalid_conversion`
- `unsupported.image.decode.invalid_scale`
- `unsupported.image.decode.invalid_parameters`
- `unsupported.image.decode.rewind_required`
- `unsupported.image.decode.out_of_memory`
- `unsupported.image.decode.unimplemented`
- `unsupported.image.animation.not_requested`
- `unsupported.image.animation.frame_unavailable`
- `unsupported.image.animation.required_frame_missing`
- `unsupported.image.animation.disposal`
- `unsupported.image.animation.blend`
- `unsupported.image.color.profile`
- `unsupported.image.color.conversion_unvalidated`
- `unsupported.image.color.HDR_unvalidated`
- `unsupported.image.color.gainmap_unvalidated`
- `unsupported.image.orientation`
- `unsupported.image.pixel.format`
- `unsupported.image.pixel.row_stride`
- `unsupported.image.pixel.planar_route`
- `unsupported.image.mip_required`
- `unsupported.image.upload.artifact_key_nondeterministic`
- `unsupported.image.upload.row_stride`
- `unsupported.image.upload.texture_format`
- `unsupported.image.upload.budget_exceeded`
- `unsupported.image.CPU_rendered_texture_forbidden`

Accepted routes also produce diagnostics. A successful image route must be
dumpable enough to explain which codec, color plan, frame, artifact, upload
plan, texture descriptor, and sampler were used.

## Validation Gates

Image pipeline promotion requires:

- codec registry dump tests;
- metadata scan fixtures for each target format;
- malformed input and truncated input refusal fixtures;
- dimensions, subset, sample size, and budget tests;
- alpha and premul conversion fixtures;
- ICC/CICP/profile conversion fixtures;
- EXIF/origin/orientation fixtures;
- bit depth fixtures for 1/2/4/8/10/12/16-bit paths as applicable;
- HDR metadata and tone-map/refusal fixtures for HEIF/AVIF where applicable;
- gainmap metadata, companion image, tone-map, and refusal fixtures where the
  selected format and codec expose gainmap data;
- PNG, JPEG, WebP, GIF, BMP, ICO, WBMP, HEIF, and AVIF target coverage
  evidence according to their target status;
- animated GIF/WebP/AVIF fixtures for frame duration, loop count, disposal,
  blend, dirty rect, required prior frame, first-frame-still policy, and frame
  cache invalidation;
- upload artifact key determinism tests;
- row-stride and staging alignment tests;
- mip generation tests;
- `GPUTextureOwnershipPlan` bridge tests with spec 18;
- `GPUSampledTextureBinding` payload tests with spec 17;
- `MaterialKey` exclusion tests for pixel data, artifact keys, codec versions,
  concrete handles, and frame generations;
- GPU sample evidence for accepted uploaded artifacts;
- CPU oracle evidence only as reference, not as product route;
- telemetry and budget evidence through spec 13;
- quarantine tests for nondeterministic external codec behavior;
- stable refusal snapshots for every required refusal code.

Conformance must identify whether a fixture is Kanvas-normative, product-only,
or external-codec observational evidence. A support claim may not depend on an
external codec whose version, output, or color behavior cannot be dumped and
reproduced within the accepted tolerance.

## Relationship To Text And Glyphs

`21-text-glyph-pipeline.md` owns text-specific glyph representation,
including bitmap glyph routes and text atlas artifacts.

This spec owns general image and bitmap inputs. Text may choose to reuse the
codec registry for embedded glyph images only if a future text spec update
defines that handoff. Until then, glyph PNG, CBDT/CBLC, sbix, COLRv1, and SVG
font behavior remains text-owned and must not be counted as general image
codec support.

## Non-Goals

- Do not port Skia codecs or Graphite image classes as public Kanvas APIs.
- Do not expose platform decoder handles or mutable legacy image objects in
  renderer core plans.
- Do not add a hidden CPU renderer fallback for unsupported image draws,
  filters, layers, scenes, or text.
- Do not treat arbitrary platform codec success as conformance support.
- Do not dynamically compile SkSL for image color conversion or sampling.
- Do not assume browser-only WebGPU behavior; the target is the generic `GPU`
  facade used with `wgpu4k`.
- Do not rely on backend-specific YCbCr sampler features without a future
  accepted multi-plane route spec.
- Do not perform unbounded file, network, stream, or progressive decode inside
  the GPU renderer core.
- Do not claim image/bitmap support without decode, artifact, upload, texture
  ownership, sampling, diagnostics, and validation evidence.
