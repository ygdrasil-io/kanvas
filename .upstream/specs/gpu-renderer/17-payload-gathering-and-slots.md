# Payload Gathering And Slots

Status: Draft
Date: 2026-06-13

## Purpose

Define how the new GPU renderer gathers per-draw and per-render-step payloads
after material/layout identity has been accepted.

This spec closes the Graphite `PipelineDataGatherer` gap. `MaterialKey` and
`GPUMaterialDictionary` define material identity and WGSL ABI. `WGSLPackingPlan`
defines byte layout. `GPUPayloadGatherer` writes concrete values and resource
references into pass-local payload slots for GPU execution.

## Graphite Evidence

Relevant Graphite concepts:

- `PipelineDataGatherer` owns mutable uniform and texture collection while a
  paint and its render steps are lowered.
- `UniformManager` writes bytes with layout-specific alignment, padding,
  matrix order, half/full precision conversion, and debug expectation checks.
- `UniformDataBlock` wraps finished uniform bytes and is de-duplicated by
  byte contents in `UniformDataCache`.
- `TextureDataBlock` wraps ordered sampled texture and sampler pairs and is
  de-duplicated in `TextureDataCache`, while unique texture proxies are retained
  for the eventual draw pass.
- `markOffsetAndAlign()`, `endCombinedData()`, and `rewindForRenderStep()` let
  Graphite share paint payloads across multiple render steps while appending
  step payloads deterministically.
- `FloatStorageManager` aggregates gradient stop data into one storage buffer
  for a draw pass when gradients exceed inline uniform storage.
- `DrawList` converts gathered uniform and texture blocks into sort-key indices,
  then `DrawPass` materializes buffers, texture bindings, and pipelines.

Kanvas adopts the payload and slot separation, not Graphite's C++ classes,
SkSL types, proxy ownership model, raw pointer hashing, or backend abstractions.

## Ownership Boundary

`GPUPayloadGatherer` belongs to `:gpu-renderer` pass construction.

It owns:

- writing concrete uniform and storage payload values;
- collecting texture, sampler, storage-buffer, storage-texture, and artifact
  resource references;
- validating writes against `WGSLPackingPlan` and `WGSLBindingLayout`;
- de-duplicating payload blocks within one pass or recording scope;
- assigning pass-local payload slots;
- producing upload and binding requests for `GPUResourceProvider`;
- diagnostics for payload mismatch, upload failure, stale resources, and budget
  pressure.

It does not own:

- public paint interpretation;
- `MaterialKey` construction;
- `GPUMaterialDictionary` snippet registration;
- WGSL module assembly;
- render pipeline identity;
- broad CPU raster fallback.

The gatherer consumes accepted analysis, material assembly, render-step, ABI,
and resource plans. It must not make unsupported material or route decisions
silently. If a value cannot be packed or a resource cannot be bound, the route
refuses or the pass is not promoted with a stable diagnostic.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUPayloadGatherer` | Deterministic collector for per-draw and per-step payloads. |
| `GPUPayloadGatherPlan` | Accepted gather contract for one draw invocation family. |
| `GPUPayloadWritePlan` | Ordered write recipe derived from `GPUMaterialAssemblyPlan`, `GPURenderStep`, and `WGSLPackingPlan`. |
| `GPUMaterialPayload` | Material-side payload values after descriptor normalization and before byte packing. |
| `GPUUniformPayloadBlock` | Canonical byte block for uniform or read-only storage payload values. |
| `GPUPayloadSlotID` | Generic pass-local slot identifier used by diagnostics and planner references. |
| `GPUUniformPayloadSlot` | Pass-local index for a de-duplicated uniform payload block. |
| `GPUResourceBindingBlock` | Ordered resource binding set for textures, samplers, storage buffers, storage textures, and artifacts. |
| `GPUResourceBindingSlot` | Pass-local index for a de-duplicated resource binding block. |
| `GPUPayloadBindingPlan` | Binding request plan consumed by `GPUResourceProvider`. |
| `GPUPayloadUploadPlan` | Upload and staging request plan for gathered payload bytes. |
| `GPUPayloadFingerprint` | Scoped fingerprint for pass/recording/frame-local payload de-duplication. |
| `GPUGradientPayloadStore` | Pass-local aggregate storage for gradient stop payloads when inline uniforms are not used. |
| `GPUDrawPayloadRef` | Immutable reference from a draw invocation to its payload slots. |

Slot IDs and payload fingerprints are local to the pass, recording, or frame
scope that created them. They are valid sorting or batching axes only inside
that product. They are not durable cache keys, serialization handles, or
cross-recording resource identities.

`GPUPayloadGatherPlan`, `GPUPayloadBindingPlan`, and `GPUPayloadUploadPlan`
are plans. They may be cached or dumped as structured metadata. They do not
carry correctness by cache residency.

## Gather And Write Plans

`GPUPayloadGatherPlan` is the bridge between accepted draw/material facts and
the write, binding, and upload plans needed for execution.

It includes:

- normalized command family;
- material assembly plan identity;
- render-step identity and version;
- payload write plan identity;
- payload binding plan identity;
- payload upload plan identity;
- scope for de-duplication: pass, recording, or frame;
- unsupported reason when a required payload class cannot be gathered.

`GPUPayloadWritePlan` is the bridge between layout metadata and concrete
values.

It includes:

- material assembly plan identity;
- render-step identity and version;
- packing plan hash;
- binding layout hash;
- field write order;
- source value path for every field;
- numeric representation and conversion policy;
- default or refused behavior for missing optional values;
- resource binding order;
- required upload or staging behavior;
- diagnostics labels.

The gather and write plans are deterministic and versioned. They may be key or
cache inputs when they change executable layout or generated WGSL behavior. The
values they gather or write are not pipeline key inputs.

The plan must be generated from structured metadata. It must not infer field
order by scanning WGSL strings or by Kotlin reflection over implementation
classes.

## Uniform Payloads

`GPUUniformPayloadBlock` contains bytes written according to
`WGSLPackingPlan`.

Rules:

- every write names the target layout field;
- field order follows the packing plan;
- alignment, size, stride, padding, and dynamic-offset rules come from
  `WGSLUniformLayout` or `WGSLStorageLayout`;
- padding bytes are zeroed before hashing or comparison;
- matrix order is explicit;
- color values record premul and color-space interpretation before packing;
- f16 conversion is capability-gated and represented in the layout;
- missing, NaN, infinite, or out-of-range values refuse when the plan requires
  finite data.

`GPUUniformPayloadSlot` is assigned by de-duplicating payload byte blocks within
the relevant pass or recording scope. A slot can reduce uploads and bind-group
changes, but a cache hit is not correctness evidence.

Uniform values must not enter `MaterialKey`, `GPURenderPipelineKey`, or
`GPUComputePipelineKey`. Their layout, packing plan, and write-plan versions may
enter those keys when they affect validity.

## Resource Binding Payloads

`GPUResourceBindingBlock` is an ordered binding payload that matches
`WGSLResourceBindingPlan`.

It records:

- binding layout hash;
- resource binding count;
- sampled texture descriptors and resource references;
- sampler descriptors;
- read-only storage buffer references;
- read-write storage buffer references when accepted;
- storage texture references when accepted;
- artifact references from `CPUPreparedGPUArtifactRegistry`;
- dynamic offset values when used;
- device-generation facts required to validate handles;
- diagnostic labels for every binding.

Resource binding blocks use stable resource descriptors and recording-local
resource IDs, not raw GPU object addresses, as diagnostic facts. Actual `GPU`
handles are supplied by `GPUResourceProvider` during resource preparation or
command encoding.

If a resource is stale, uninitialized, evicted, incompatible with the current
device generation, or missing required usage flags, the pass must rebuild,
rebind from an accepted artifact path, or refuse. It must not substitute a
CPU-rendered compatibility texture.

## Paint And Render-Step Boundaries

`GPUPayloadGatherer` keeps the Graphite-inspired paint/render-step split.

Target flow:

1. Reset gatherer for one accepted normalized command.
2. Write material payloads from the accepted material descriptor using
   `GPUMaterialAssemblyPlan`.
3. Mark the material payload boundary.
4. For each `GPURenderStep`, append render-step payloads using that step's
   write plan.
5. Finalize combined payloads for shading steps.
6. Finalize render-step-only payloads for non-shading steps.
7. Insert payload blocks into pass-local caches and assign slots.
8. Rewind to the material boundary before gathering the next render step for
   the same command.

This flow lets a multi-step renderer share material payloads without copying
Graphite's mutable implementation. A Kanvas implementation may use immutable
builders internally, but the observable contract must preserve the same
payload-boundary semantics.

## Gradient Payloads

Gradients have two payload classes:

- inline uniform payloads for small, accepted stop counts;
- `GPUGradientPayloadStore` entries for larger or shared stop data when an
  accepted route supports a material-owned buffer.

`GPUGradientPayloadStore` records:

- gradient descriptor identity;
- stop count and normalized stop order;
- packed colors and offsets;
- interpolation mode;
- color-space facts;
- storage-buffer layout;
- pass-local offset;
- upload bytes;
- cache and diagnostic counters.

For the first slice, linear gradients may promote only the inline uniform route
or an explicitly implemented `GPUGradientPayloadStore` route. Unsupported stop
counts, tile modes, interpolation modes, or storage-buffer requirements refuse
with stable diagnostics.

## Texture And Sampler Payloads

Texture and sampler payloads are layout-bound resource values.

Texture/image ownership, view descriptors, sampler descriptors, uploaded CPU
pixels, imported textures, and surface leases are defined in
`18-texture-image-ownership.md`. `GPUPayloadGatherer` consumes accepted
`GPUTextureOwnershipPlan` products and writes `GPUSampledTextureBinding`
records into `GPUResourceBindingBlock`.
For encoded image or already-decoded CPU pixel sources, codec selection,
decode, animation frame selection, color/profile conversion, orientation,
pixel preparation, and upload artifact key construction are defined in
`22-image-bitmap-codec-pipeline.md`; the gatherer only consumes the accepted
texture binding that results from those plans.

Rules:

- sampler descriptors are key/layout facts only when they affect shader or
  pipeline validity; otherwise they are resource binding payload facts;
- sampled texture bindings record `GPUImageSourceDescriptor`,
  `GPUTextureDescriptor`, `GPUTextureViewDescriptor`, `GPUSamplerDescriptor`,
  ownership plan, usage flags, and generation facts;
- sampled texture object identity is never part of `MaterialKey`;
- image shader routes require accepted texture ownership and lifetime policy;
- uploaded CPU image pixels must use `UploadedTextureArtifact` produced by
  `22-image-bitmap-codec-pipeline.md`;
- atlas, mask, glyph, and coverage textures must use their accepted artifact or
  atlas specs, with path/coverage atlas bindings following
  `19-path-coverage-atlas-strategy.md`;
- text/glyph atlas textures, bitmap glyph textures, SDF params, and text
  instance buffers must use accepted `GPUTextBinding` records from
  `21-text-glyph-pipeline.md`;
- destination-copy snapshots and existing destination intermediates must use
  accepted `GPUDestinationReadBinding` records from
  `20-destination-read-strategy.md`;
- layer source, backdrop, filter-output, restore composite, and layer target
  bindings must use accepted `GPULayerResourcePlan` and
  `GPULayerCompositePlan` records from
  `28-layer-savelayer-execution.md`;
- analytic clip uniforms, clip coverage masks, stencil payload values when
  used, and registered clip shader resources must use accepted plans from
  `24-clip-stencil-mask-pipeline.md`;
- filter intermediates, runtime-effect child bindings, filter node uniforms,
  sampled inputs, storage resources, and ordering tokens must use accepted
  plans from `23-filter-effect-pipeline.md`;
- runtime-effect uniform values, live-edit updates, child slot payloads, and
  descriptor resource bindings must use accepted descriptor contracts from
  `27-registered-runtime-effects-registry.md`;
- color transform uniforms, LUT bindings, gradient converted stop payloads,
  runtime color uniform transforms, image color conversion facts, and store
  conversion payloads must use accepted plans from
  `29-color-management-pipeline.md`;
- coordinate transform matrices, inverse matrices, scale/bias values,
  pixel-grid facts, rounded bounds, destination-copy coordinate mappings, and
  layer/atlas/texture origin payloads must use accepted
  `GPUCoordinatePayloadPlan` facts from
  `30-coordinate-transform-bounds-policy.md`;
- raw resource handles are not sort or cache key facts.
- import, upload, lease, allocation, eviction, and release are performed by
  `GPUResourceProvider`, not by the gatherer.

When a path or coverage atlas entry is accepted, the gatherer consumes
`GPUCoverageAtlasBinding` facts: atlas entry ref, texture origin, mask size,
inverse atlas size, mask transform, sampling mode, atlas generation, and
resource binding layout. These facts are pass-local payload/resource facts and
must stay out of `MaterialKey`.
When a clip route is accepted, the gatherer consumes only the facts produced by
`GPUClipPlan`: analytic clip payloads, `GPUClipMaskPlan` bindings,
`GPUClipShaderPlan` uniforms/resources, stencil payload values when represented
as payload, and `GPUClipOrderingToken` references. It does not normalize clip
stacks, choose clip routes, allocate mask textures, produce stencil work, or
mutate atlas state.
When a text/glyph route is accepted, the gatherer consumes `GPUTextBinding`
facts: text subrun ID, render step, atlas texture ownership, view/sampler
descriptors, binding layout, SDF params, instance buffer ref, atlas/page
generation, material/color plan IDs, and resource slot. It does not create text
atlases, upload glyph data, split subruns, or choose text routes.
When a destination-read route is accepted, the gatherer consumes
`GPUDestinationReadBinding` facts: target generation, copied/read bounds,
texture/view/sampler descriptors, coordinate mapping, binding layout, and
resource slot. It does not create the target snapshot or decide pass splits.
When a layer execution route is accepted, the gatherer consumes only binding
facts produced by `GPULayerResourcePlan`, `GPULayerInitializationPlan`,
`GPULayerSourcePlan`, `GPULayerFilterChainPlan`, and
`GPULayerCompositePlan`: layer source texture/view/sampler descriptors,
coordinate mapping, composite uniforms, destination-read bindings, target
generation, binding layout, and resource slots. It does not allocate layer
targets, initialize previous contents, execute backdrop filters, choose
elision, split passes, or release resources.
When an image/bitmap route is accepted, the gatherer consumes only the sampled
texture binding facts produced after `GPUImageUploadPlan` and
`GPUTextureOwnershipPlan` acceptance. It does not decode images, compose
animation frames, generate mips, or upload image pixels.
When a filter/effect route is accepted, the gatherer consumes only the
resource and uniform binding facts produced by `GPUFilterNodePlan`,
`GPUFilterIntermediatePlan`, `GPUFilterRuntimeEffectPlan`, and
`GPUFilterOrderingToken`. It does not normalize filter graphs, allocate
intermediates, execute copies, choose render/compute routes, or fold color
filters into material keys.
When a registered runtime-effect route is accepted, the gatherer consumes only
uniform values, child payload bindings, resource bindings, and live-edit values
described by `GPURuntimeEffectDescriptor`, `GPURuntimeEffectUniformBlockPlan`,
`GPURuntimeEffectChildSlotPlan`, `GPURuntimeEffectResourcePlan`, and
`GPURuntimeEffectLiveEditPlan`. It does not register descriptors, compile
source text, choose runtime-effect routes, or mutate registry snapshots.
When a color-management route is accepted, the gatherer consumes only payload
facts produced by `GPUColorTransformPlan`, `GPUGradientColorPlan`,
`GPUColorUniformPlan`, `GPUHDRColorPlan`, `GPUGainmapPlan`, and
`GPUColorStorePlan`: transform uniforms, LUT resource bindings, converted stop
blocks, color-uniform values, store conversion data, and diagnostic IDs. It
does not parse profiles, choose working spaces, generate transforms, tone-map
HDR, or reinterpret untagged bytes.
When a coordinate/transform/bounds route is accepted, the gatherer consumes
only payload facts produced by `GPUCoordinatePayloadPlan`: matrices, inverse
matrices, scale/bias, rounded bounds, pixel-grid data, coordinate-space IDs
when needed by diagnostics, and WGSL helper payloads. It does not classify
transforms, compute bounds, choose full-target widening, snap geometry, or
relax precision.

The first rect/rrect solid and linear-gradient slice does not require sampled
texture payloads except when a later accepted gradient-store route explicitly
uses a texture-backed payload.

## Upload And Binding Materialization

Payload gathering produces upload and binding requests. `GPUResourceProvider`
materializes them.

`GPUPayloadBindingPlan` records bind group role, binding order, resource class,
and dynamic-offset policy. `GPUPayloadUploadPlan` records upload byte ranges,
staging scope, upload-before-use dependencies, and budget accounting.

Materialization rules:

- uploads are bounded by declared buffer and staging budgets;
- upload staging uses `GPURecorderScope` or `GPUFrameScope` according to the
  accepted payload lifetime;
- uniform and storage buffer alignment must match `GPUCapabilities`;
- bind groups are created from `WGSLResourceBindingPlan`;
- dynamic offsets are emitted only when the ABI spec and facade capabilities
  accept them;
- failed allocation, mapping, write, bind-group creation, or device validation
  produces a stable diagnostic;
- a pass with failed required payload materialization is not a supported GPU
  route.

Where a facade implementation supports mapped staging or persistent upload
buffers differently, the strategy is an implementation detail as long as the
same payload plan, layout, diagnostics, and submission semantics are preserved.

## Sorting And Batching Use

Payload slots may participate in sorting and batching only as pass-local
compatibility facts.

Allowed planner/pass uses:

- group adjacent invocations that share pipeline, binding layout, uniform slot,
  and resource binding slot;
- avoid re-uploading or rebinding identical uniform payloads;
- avoid re-emitting texture/sampler bindings when the resource binding slot is
  unchanged;
- expose payload slot changes in diagnostics.

Forbidden uses:

- using payload values as durable pipeline identity;
- sorting across barriers, destination reads, layer boundaries, or unknown
  overlaps just because payload slots match;
- using cache hit/miss state as an ordering axis;
- using raw GPU handles as sort keys.

## Diagnostics

Payload diagnostics must include:

- command ID and render-step index;
- material key and material assembly plan label;
- payload gather plan hash;
- payload write plan hash;
- payload binding plan hash;
- payload upload plan hash;
- packing plan hash;
- binding layout hash;
- uniform payload byte size and slot;
- resource binding slot and binding count;
- atlas entry ref, atlas generation, and atlas binding hash when path or
  coverage atlas payloads are used;
- destination-read binding hash, target generation, and read bounds when
  destination snapshots or intermediates are sampled;
- upload bytes and target buffer class;
- dynamic offset summary when used;
- gradient payload store offset when used;
- stale resource or device-generation facts;
- refusal reason when packing, upload, or binding fails.

Stable reason-code examples:

- `unsupported.payload.write_plan_missing`
- `unsupported.payload.uniform_layout_mismatch`
- `unsupported.payload.uniform_value_non_finite`
- `unsupported.payload.uniform_size_exceeded`
- `unsupported.payload.resource_binding_mismatch`
- `unsupported.payload.resource_stale_generation`
- `unsupported.payload.texture_unavailable`
- `unsupported.payload.atlas_binding_unavailable`
- `unsupported.payload.atlas_generation_stale`
- `unsupported.payload.destination_read_binding_unavailable`
- `unsupported.payload.destination_read_generation_stale`
- `unsupported.payload.upload_budget_exceeded`
- `unsupported.payload.dynamic_offset_unavailable`
- `unsupported.payload.gradient_store_unavailable`
- `unsupported.payload.gradient_stop_count`

## Validation Requirements

Promoted payload behavior requires:

- canonical dumps for payload write plans;
- uniform payload byte fixtures with padding and alignment assertions;
- payload de-duplication tests for equal and unequal values;
- resource binding order tests;
- stale resource and missing usage-flag refusal tests;
- gradient inline and refused-gradient fixtures for the first slice;
- pass-local slot stability tests across equivalent inputs;
- payload fingerprint scope tests for pass, recording, or frame de-duplication;
- negative tests proving payload values do not change pipeline keys;
- telemetry counters for slots, bytes, uploads, cache hits, misses, and
  materialization failures.

## First Slice Contract

The first rect/rrect slice requires:

- solid color uniform payload writing;
- linear-gradient endpoint, stop, tile-mode, and interpolation payload writing
  for the accepted subset;
- render-step intrinsic payload writing for rect and rounded-rect geometry;
- payload slots referenced by `GPUDrawInvocation`;
- uniform payload deduplication for at least one equivalent solid-color case;
- deterministic refusal for unsupported gradient payload shape;
- payload binding and upload plan dumps for promoted fixtures;
- no texture, image, runtime-effect, glyph, or path atlas payload route unless
  a later accepted spec promotes it.

## Non-Goals

- Do not put per-draw values in `MaterialKey` or pipeline keys.
- Do not infer payload layout from ad hoc WGSL strings.
- Do not use raw GPU handles, Kotlin object addresses, or cache residency as
  durable identity.
- Do not treat upload/cache success as correctness proof.
- Do not hide packing, upload, or binding failures behind CPU fallback.
- Do not represent full CPU-rendered draw output as a payload artifact.
