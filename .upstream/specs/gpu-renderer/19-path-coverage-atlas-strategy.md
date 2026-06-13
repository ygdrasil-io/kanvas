# Path And Coverage Atlas Strategy

Status: Draft
Date: 2026-06-13

## Purpose

Define the target atlas strategy for path coverage, clip masks, and reusable
coverage masks in the GPU-first renderer.

This spec closes the atlas gap left by the resource, routing, texture, payload,
and geometry/coverage specs. It defines when Kanvas may create a path or
coverage atlas entry, which keys make that entry valid, how atlas residency is
budgeted and invalidated, how atlas mutations interact with pass planning, and
which diagnostics replace silent fallback.
Detailed captured clip descriptor, effective element, scissor, analytic,
stencil, mask-route, shader clip, budget, ordering, and clip diagnostic policy
is defined in `24-clip-stencil-mask-pipeline.md`. This spec owns the atlas and
coverage artifact mechanics used by those clip routes.
Detailed path, stroke, fill-rule, inverse-fill, flattening, tessellation,
stencil-cover, prepared geometry, geometry budget, and geometry diagnostic
policy is defined in `25-path-stroke-geometry-pipeline.md`. This spec owns the
atlas residency and coverage artifact mechanics used by those geometry routes.

The design is Graphite-inspired but Kanvas-owned:

- WebGPU-style `GPU` facade only;
- WGSL, not SkSL;
- uppercase `GPU`, `CPU`, and `WGSL` concept names;
- no Ganesh or Graphite port;
- no hidden CPU-rendered draw/layer/scene texture compatibility.

## Graphite Evidence

Graphite's useful atlas model is conceptual, not an implementation template.
The relevant source evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/`.

Useful Graphite concepts:

- `DrawAtlas` owns atlas pages, plots, generation counters, last-use tokens,
  eviction callbacks, dirty uploads, compaction, and `addRect()` outcomes.
- `DrawAtlas::ErrorCode` distinguishes success, try-again when all plots are
  still in use by unflushed draws, and hard error when the entry is invalid or
  too large.
- `PathAtlas` stores transient coverage masks and gives draws texture origin,
  mask size, and mask-to-device facts for sampling.
- `RasterPathAtlas` separates small cached path masks, general cached path
  masks, uncached masks, and Graphite's `ProxyCache` fallback.
- `ComputePathAtlas` uses GPU compute when capabilities and bounds allow it,
  rejects entries larger than the atlas or threshold policy, and writes to a
  storage-capable atlas texture.
- `ClipAtlasManager` separates path-keyed clips from save-record-keyed clips,
  uses different atlas dimensions/lifetime assumptions, and removes cached
  keys when plots are evicted.
- `AtlasProvider` owns atlas managers, pooled atlas textures, upload recording,
  compaction, invalidation, and resource release.
- `ClipStack` tries simple/scissor/analytic clip strategies before raster clip
  atlas use; atlas is not the first answer for every clip.

Kanvas preserves these invariants:

- atlas entries carry generation and residency facts;
- atlas reads prevent unsafe overwrite until their use token is complete;
- atlas uploads or compute writes happen before draws that sample them;
- atlas failure is classified as retryable pressure, hard capability/size
  failure, budget refusal, or unsupported route;
- atlas textures are owned by atlas/resource specs, not by material keys.

Kanvas intentionally does not copy:

- Graphite class names, source layout, virtual dispatch, or bit packing;
- SkSL paint machinery;
- Graphite's `ProxyCache` fallback as a generic compatibility route;
- a CPU raster atlas as the primary GPU-first architecture;
- any fallback that CPU-renders a complete unsupported draw or layer.

## Ownership Boundary

The `:gpu-renderer` module owns the path and coverage atlas contracts.

Owned by this spec:

- `GPUPathAtlasPlan`;
- `GPUCoverageAtlasPlan`;
- `GPUAtlasPolicy`;
- `GPUAtlasBudgetPolicy`;
- `GPUAtlasDescriptor`;
- `GPUAtlasPageDescriptor`;
- `GPUAtlasPlotDescriptor`;
- `GPUAtlasEntryDescriptor`;
- `GPUAtlasEntryRef`;
- `GPUAtlasGeneration`;
- `GPUAtlasUseToken`;
- `GPUAtlasMutationPlan`;
- `GPUAtlasUploadPlan`;
- `GPUAtlasDiagnostic`;
- `GPUPathAtlasKey`;
- `GPUCoverageAtlasKey`;
- `GPUCoverageMaskDescriptor`;
- `GPUCoverageAtlasBinding`;
- `GPUComputeCoverageAtlasPlan`.

`GPUResourceProvider` materializes atlas textures, views, samplers, storage
textures, uploads, and bind groups. `CPUPreparedGPUArtifactRegistry` owns typed
artifact lookup, creation, invalidation, budgets, and diagnostic accounting.
`GPUPayloadGatherer` consumes accepted atlas/resource plans and emits payload
binding records. It must not allocate, import, upload, evict, compact, or reset
atlas state.

Text/glyph atlas ownership is outside this spec. Text uses
`GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, and
`21-text-glyph-pipeline.md`. Image/bitmap uploads use
`UploadedTextureArtifact` and `18-texture-image-ownership.md`.

## Relationship To Geometry/Coverage

The existing `geometry-coverage/` pack remains migration and evidence context.
This spec refines its `CoverageAtlas` placeholder for the GPU renderer target.
The target path/stroke geometry semantics are owned by
`25-path-stroke-geometry-pipeline.md`; this atlas spec is selected only after a
geometry route has determined that reusable or standalone coverage storage is
the accepted representation.

The default selector stance remains fail-closed:

- analytic rect/rrect coverage should use `GPUNative` coverage, not an atlas;
- simple clip rect/scissor/stencil strategies should avoid atlas when possible;
- path or clip atlas use requires an accepted `GPUPathAtlasPlan` or
  `GPUCoverageAtlasPlan`;
- clip route selection, clip stencil-vs-mask choice, clip shader refusal, and
  clip-specific ordering diagnostics are governed by
  `24-clip-stencil-mask-pipeline.md`;
- persistent atlas residency is enabled only after key, budget, eviction,
  synchronization, profiling, and visual evidence are accepted;
- if no accepted atlas or non-atlas route exists, route selection returns
  `RefuseDiagnostic`.

The prior WebGPU AA edge budget remains in force until a replacement benchmark
or ADR changes it. An atlas plan can be selected when it is the accepted
strategy for overflow, repeated masks, or complex clip state; it must not hide
an edge-budget overflow without a route diagnostic.

## Strategy Taxonomy

### `GPUPathAtlasPlan`

`GPUPathAtlasPlan` describes reusable or transient path coverage entries for
path fill and stroke.

Allowed route kinds:

- `CPUPreparedGPU(PathAtlasArtifact)` when CPU work produces a bounded path
  coverage mask or atlas entry and the GPU samples it;
- `CPUPreparedGPU(CoverageMaskArtifact)` when the path result is operation
  specific, clipped, one-shot, or not reusable as a path atlas entry;
- `GPUNative` when a GPU stencil-cover, tessellation, or compute coverage route
  is accepted;
- `RefuseDiagnostic` when no supported route exists.

`PathAtlasArtifact` is for path-shaped content whose preparation result can be
reused under its key. It is not a full rendered draw result. The final material
shading, blend, target writes, and submission remain GPU work.

### `GPUCoverageAtlasPlan`

`GPUCoverageAtlasPlan` describes bounded coverage masks for clip stacks,
operation-specific masks, path coverage with clip baked in, filter masks when
`23-filter-effect-pipeline.md` accepts them, and other non-glyph alpha
coverage.

Allowed route kinds:

- `CPUPreparedGPU(CoverageMaskArtifact)` for frame-local, recording-local, or
  atlas-resident coverage masks;
- `GPUNative` for accepted stencil/depth, analytic, or compute coverage;
- `RefuseDiagnostic` when the coverage cannot be represented safely.

`CoverageMaskArtifact` is less reusable than `PathAtlasArtifact`. Its key often
includes clip-stack facts, save-record facts, device bounds, combine operation,
initial alpha, and transform facts.

### `GPUComputeCoverageAtlasPlan`

`GPUComputeCoverageAtlasPlan` is the future GPU-native compute path for writing
coverage into a storage-capable atlas texture or storage buffer before a render
pass samples it.

It requires:

- compute command support;
- storage texture or storage buffer support for the chosen format;
- explicit write-before-sample barriers;
- accepted bounds, area, coordinate, and complexity thresholds;
- deterministic WGSL compute module validation and reflection;
- texture ownership through `GPUTextureOwnershipPlan`;
- route diagnostics for unavailable compute, storage, or synchronization.

It is not required by the first implementation slice.

### `GPUAtlasPolicy`

`GPUAtlasPolicy` selects one of these residency modes:

| Mode | Meaning |
|---|---|
| `NoAtlas` | The command must use analytic, stencil, geometry, standalone mask, or refuse. |
| `FrameLocalMask` | Create a bounded mask for one frame or pass, with no persistent atlas reuse. |
| `RecordingLocalAtlas` | Atlas entries are valid only for the recording or task list that created them. |
| `PersistentAtlas` | Entries can survive across recordings under stable keys, generations, and budgets. |
| `GPUComputeAtlas` | GPU compute writes coverage into a sampled atlas or buffer under explicit barriers. |

`PersistentAtlas` is a target capability, not the default support claim. It
requires profiling evidence showing that repeated coverage generation or upload
cost dominates enough to justify long-lived residency.

## Core Objects

### `GPUAtlasDescriptor`

Describes an atlas texture family without exposing a raw handle:

- atlas purpose: path, clip, coverage, filter mask, or text/glyph owned by
  `21-text-glyph-pipeline.md`;
- format and sample type;
- dimensions and page size;
- plot/tile layout when used;
- padding policy;
- origin and coordinate convention;
- usage flags: texture binding, copy destination, storage binding, render
  attachment when accepted;
- owner scope: `GPURecorderScope`, `GPUFrameScope`, `GPUAtlasScope`, or
  `GPUSharedScope` when a later spec accepts sharing;
- device and capability requirements;
- budget policy ID and version.

### `GPUAtlasPageDescriptor` And `GPUAtlasPlotDescriptor`

`GPUAtlasPageDescriptor` describes one logical atlas page:

- page index within the atlas family;
- texture descriptor hash;
- dimensions and format;
- owner scope;
- page generation;
- active, inactive, or pending-release state;
- resident byte accounting;
- last compacted generation.

`GPUAtlasPlotDescriptor` describes a suballocation region when an atlas uses
plot-style allocation:

- plot index within a page;
- plot rectangle;
- plot generation;
- dirty rectangle summary for uploads;
- resident entry count;
- last use token;
- eviction eligibility.

Pages and plots are diagnostic/resource facts. They must not leak into
`MaterialKey` or become stable public API beyond the renderer contracts.

### `GPUAtlasEntryDescriptor`

Describes one logical entry before materialization:

- entry kind: path mask, clip mask, operation coverage, filter mask, or
  standalone bounded mask;
- requested bounds in device space;
- source bounds in local or clip space when needed;
- mask origin and mask size;
- coverage format and coverage quality;
- padding pixels;
- sampling policy: nearest, linear, or route-specific;
- combine/invert semantics;
- expected upload or compute-write byte size;
- maximum supported dimensions and area;
- key hash and diagnostic label.

### `GPUAtlasEntryRef`

`GPUAtlasEntryRef` is the non-durable reference used by payload and resource
binding code after an entry is accepted.

It records:

- atlas descriptor hash;
- atlas generation;
- page index when pages are used;
- plot index when plots are used;
- plot generation when plots are used;
- entry rectangle inside the atlas;
- content rectangle excluding padding;
- texture origin;
- mask size;
- inverse atlas size;
- owner scope and lifetime;
- last accepted use token;
- stale, evicted, or rebuilt state.

`GPUAtlasEntryRef` must be dumpable but is not a durable key. It must not enter
`MaterialKey`; it may enter payload, resource binding, sort, and diagnostic
products only where generation and barriers make it legal.

### `GPUAtlasGeneration` And `GPUAtlasUseToken`

`GPUAtlasGeneration` is the monotonic validation fact for an atlas family,
page, or plot. Any reset, incompatible compaction, eviction, or resource
rebuild that can make old entry refs invalid must advance the relevant
generation.

`GPUAtlasUseToken` records that a submitted or pending draw samples an atlas
entry. An entry may be overwritten only after all use tokens that can sample it
are complete or after a pass/task split proves the old read cannot occur.

Generation and use-token values are not material identity. They are execution
and residency facts used for validation, ordering, eviction, and diagnostics.

### `GPUAtlasMutationPlan`

`GPUAtlasMutationPlan` records atlas state changes needed before a draw samples
an entry:

- lookup hit with no mutation;
- CPU-raster or CPU-pack entry creation;
- upload from `CoverageMaskArtifact` or `PathAtlasArtifact`;
- GPU compute write into an atlas entry;
- page activation;
- safe plot eviction;
- compaction;
- reset of a frame-local atlas;
- split-pass retry;
- refusal.

`GPUAtlasUploadPlan` is the upload subset: source artifact, destination entry,
row layout, byte count, staging scope, upload-before-use dependency, and
budget decision.

### `GPUCoverageMaskDescriptor`

`GPUCoverageMaskDescriptor` describes a bounded coverage mask whether it is
atlas-resident or standalone:

- source kind: clip stack, path, operation-specific mask, or filter mask
  accepted by `23-filter-effect-pipeline.md`;
- content key hash;
- mask bounds, mask origin, and mask size;
- coverage format and AA quality;
- combine/invert semantics;
- lifetime class: frame-local, recording-local, atlas-resident, or
  cache-resident when accepted;
- expected GPU consumer;
- budget class and upload/compute requirements.

It is the bridge between `CoverageMaskArtifact` and atlas residency. A
standalone descriptor can be accepted without persistent atlas residency, but it
still must remain a typed coverage artifact consumed by GPU work.

## Atlas Keys And Invalidation

Atlas entries have two identities:

- a content key that proves the prepared coverage bytes or GPU-written coverage
  are correct;
- residency facts that prove where that content currently lives and whether a
  draw may sample it.

Both must be visible in dumps. The content key must be stable across equivalent
inputs. Residency facts may change across runs without changing material or
pipeline identity.

### `GPUPathAtlasKey`

The path content key includes every fact that changes path coverage:

- artifact type and descriptor version;
- immutable path identity or canonical path data hash;
- fill rule and inverse-fill behavior;
- stroke style: width, cap, join, miter, dash, path effect, hairline policy,
  and stroke expansion algorithm version;
- transform facts when mask generation is device-space or otherwise transform
  dependent;
- accepted transform class and full matrix facts when required by the route;
- tolerance, flatness, edge budget, curve budget, and coordinate thresholds;
- local, device, mask, and clipped bounds when they affect content;
- coverage quality and AA mode;
- padding policy;
- atlas policy ID and version;
- CPU raster algorithm version or GPU compute algorithm version;
- relevant `GPUCapabilities` facts when they change format, limits, or
  storage behavior.

If the source path is mutable and no canonical immutable key is available, the
route must refuse with `unsupported.artifact.key_nondeterministic` or a more
specific path key reason.

### `GPUCoverageAtlasKey`

The coverage content key includes every fact that changes mask coverage:

- artifact type and descriptor version;
- coverage source kind: clip stack, save-record clip, path coverage,
  operation-specific mask, or future filter mask;
- element list identity and canonical ordering when a clip stack is used;
- clip operations: intersect, difference, union when accepted, inverse, and
  initial alpha;
- per-element shape keys, transforms, AA modes, and bounds;
- stack/save-record identity when it is the stable source of the coverage;
- device mask bounds and mask origin;
- combine mode between analytic coverage and mask coverage;
- coverage quality, padding, and sampling policy;
- atlas policy ID and version;
- CPU raster/pack algorithm version or GPU compute algorithm version;
- relevant capability facts.

Path and clip semantics must not be merged accidentally. For path rendering,
inverse fill can be handled by the path coverage sampling plan when accepted.
For clip masks, inverse/difference semantics are normally flattened into the
mask content or refused if flattening is not accepted.

### Invalidation

An atlas content entry is invalid when any content key fact changes.

A resident atlas entry is invalid, stale, or unusable when:

- device generation changes;
- atlas generation changes;
- page or plot generation changes;
- eviction removes the entry;
- compaction moves or invalidates the entry without a recorded rebuild;
- upload or compute write fails;
- required usage flags are unavailable;
- route spec version no longer accepts the descriptor;
- the current command submission cannot prove read-after-write or
  write-after-read ordering.

A stale entry must rebuild within budget, safely evict and recreate, split and
retry when legal, or refuse. It must not silently sample old coverage.

## Budget, Retry, And Refusal

`GPUAtlasBudgetPolicy` separates hard capability limits from configurable
policy limits.

Hard limits include:

- maximum texture dimensions;
- maximum storage texture dimensions when compute writes are used;
- required texture usage flags;
- maximum binding/resource limits;
- supported format and sample type;
- supported command classes and synchronization.

Policy limits include:

- maximum resident atlas bytes;
- maximum page count;
- maximum entry dimensions;
- maximum entry area;
- maximum plot count;
- maximum upload bytes per frame or recording;
- maximum compute write bytes or dispatch count;
- maximum retry/split count;
- eviction policy;
- compaction cadence;
- stale-entry policy.

User or embedding code may choose a larger accepted policy before recording by
configuring the renderer or execution context. A command may not mutate atlas
budgets nondeterministically during route selection. A diagnostic must say
whether the refusal is caused by a hard capability limit or a policy budget
that can be increased.

Atlas add outcomes map to explicit actions:

| Action | Meaning |
|---|---|
| `UseResidentEntry` | Key hit and generation/use token are valid. |
| `CreateEntry` | Allocate or place a new entry within budget. |
| `ActivatePage` | Add or reuse a page within page and memory budgets. |
| `EvictAndCreate` | Evict entries whose use tokens have completed, invalidate their refs, then create. |
| `CreateStandaloneCoverageMask` | Use bounded non-atlas `CoverageMaskArtifact` when the route accepts it. |
| `SplitPassAndRetry` | Close the current pass/task scope, advance use tokens, then retry once or within policy. |
| `ResetFrameLocalAtlas` | Clear a frame-local atlas before any later read can observe stale contents. |
| `Refuse` | Return `RefuseDiagnostic` with a stable reason. |

`SplitPassAndRetry` is the Kanvas equivalent of Graphite's try-again behavior.
It is legal only when pass splitting preserves draw order, layer semantics,
target state, destination reads, and resource lifetime. If retry still cannot
place the entry, the route must refuse or choose another already accepted route;
it must not drop the draw silently.

## Artifact And Texture Ownership

Atlas-resident path and coverage content is represented by typed artifacts:

| Artifact | Typical atlas use |
|---|---|
| `PathAtlasArtifact` | Reusable path fill/stroke coverage entry. |
| `CoverageMaskArtifact` | Clip stack, clipped path, operation-specific, or standalone coverage mask. |
| `PrecomputedGeometryArtifact` | Geometry buffer route when atlas is refused or not selected. |

`PathAtlasArtifact` and `CoverageMaskArtifact` may reference an
`GPUAtlasEntryRef` when resident. They may also be frame-local or
recording-local standalone artifacts when a non-atlas mask route is accepted.

Atlas textures use `GPUTextureOwnershipPlan` with provenance `AtlasTexture`.
Texture ownership rules from `18-texture-image-ownership.md` apply:

- atlas textures are provider-owned resources;
- atlas textures are not user image textures;
- atlas coordinates are payload and artifact facts, not material identity;
- stale atlas generation rebuilds, evicts, or refuses deterministically;
- active attachment sampling rules still apply;
- imported texture ownership does not apply to provider-owned atlas textures.

Graphite's `ProxyCache` fallback maps only to a typed Kanvas artifact route if
that route is explicitly accepted. There is no generic proxy texture fallback.

## Payload And WGSL Binding

Atlas bindings use the artifact/resource binding group defined by
`11-wgsl-layout-binding-abi.md`, not the material-owned group unless a future
accepted spec changes the ABI.

`GPUCoverageAtlasBinding` records:

- `GPUAtlasEntryRef`;
- `GPUTextureOwnershipPlan` ID;
- `GPUTextureDescriptor`, `GPUTextureViewDescriptor`, and
  `GPUSamplerDescriptor` hashes;
- binding layout hash;
- required usage flags;
- atlas generation and plot/page generation;
- texture origin;
- mask size;
- content rectangle;
- inverse atlas size;
- mask-to-device or device-to-mask transform when required;
- sampling mode;
- combine/invert semantics;
- payload slot and resource binding slot when materialized.

For path coverage, payload may include texture origin, mask size,
mask-to-device transform, inverse-fill handling, and sampling mode.

For clip coverage, payload may include mask bounds, texel offset, inverse atlas
size, clip combine mode, and a policy that starts with nearest sampling unless
a later spec accepts linear clip mask sampling.

These payload values may affect sorting and batching only as pass-local
compatibility facts. They must not enter `MaterialKey` or durable pipeline
keys. `GPURenderPipelineKey` may include only layout/usage/state facts that
affect executable validity.

## Planner, Barriers, And Synchronization

Atlas mutation is an explicit dependency in `GPUDrawAnalysis`, `GPUTaskList`,
and `GPUDrawLayerPlanner`.

Rules:

- atlas lookup and route choice are visible in analysis diagnostics;
- atlas upload or compute write tasks must execute before draws that sample the
  entry;
- a draw that samples an entry records a `GPUAtlasUseToken`;
- eviction may overwrite an entry only after all relevant use tokens have
  completed;
- sort windows cannot move draws across intersecting atlas mutation barriers;
- pass splitting for retry must be recorded as a planner/task-list decision;
- destination-read, layer isolation, stencil/clip atomic groups, and target
  load/store semantics can forbid `SplitPassAndRetry`;
- compute atlas writes need write-before-sample synchronization and storage
  usage validation.

Atlas mutation is a barrier class just like upload, target change, and
destination-read work. A cache hit removes the mutation but does not remove
generation validation.

## Routing Policy

Route selection for path and coverage atlas work follows:

1. Validate normalized command and geometry/coverage facts, including the
   `GPUGeometryPlan` products required by
   `25-path-stroke-geometry-pipeline.md` for path fill or stroke commands.
2. Try a non-atlas `GPUNative` route when analytic, stencil, tessellation,
   or other GPU-native coverage is accepted.
3. Try `GPUNative(GPUComputeCoverageAtlasPlan)` when compute/storage/capability
   and thresholds accept it.
4. Try `CPUPreparedGPU(PathAtlasArtifact)` for reusable path coverage.
5. Try `CPUPreparedGPU(CoverageMaskArtifact)` for clip, clipped path,
   operation-specific, frame-local, or standalone coverage.
6. Try `CPUPreparedGPU(PrecomputedGeometryArtifact)` when geometry-buffer
   preparation is the accepted route for the shape.
7. Return `RefuseDiagnostic`.

This precedence can be specialized per draw family, but a specialization must
still produce the same diagnostic fields and must not introduce full CPU
rendered texture compatibility.

The route must refuse when:

- no registered artifact or native route supports the strategy;
- required key facts are unavailable or nondeterministic;
- entry dimensions or area exceed hard limits;
- coordinates or transforms exceed accepted thresholds;
- edge, curve, stroke, dash, or clip complexity exceeds accepted limits;
- storage texture, compute, copy/upload, or synchronization is unavailable;
- budget policy rejects entry creation or upload and no accepted fallback
  strategy exists;
- the atlas entry would be stale for the current device, atlas, page, plot,
  target, or recording generation;
- retry/split is illegal or exceeds policy;
- CPU preparation would produce a complete rendered draw or layer rather than a
  typed coverage artifact.

## Budget And Telemetry

Atlas telemetry is part of `GPUTelemetryLedger`.

Required counters:

- atlas descriptor count by purpose and policy;
- resident page count and resident bytes;
- entry count by artifact type;
- lookup count, hit count, miss count, and stale count;
- entry create count;
- page activation count;
- eviction count;
- compaction/reset count;
- upload byte count and upload refusal count;
- compute write count and storage refusal count;
- split-pass retry count;
- retry success and retry refusal count;
- standalone mask fallback count when accepted;
- budget pressure count;
- hard capability refusal count;
- stale-generation rebuild/refusal count.

Performance reports must distinguish:

- correctness support for a path/coverage route;
- atlas cache efficiency;
- upload cost;
- compute atlas cost;
- resident memory pressure;
- retry/pass-split cost;
- refused unsupported coverage.

Cache hits are performance evidence only. A cache miss must rebuild, choose an
accepted alternate route, or refuse without changing the intended output.

## Diagnostics

Every accepted or refused atlas route emits `GPUAtlasDiagnostic`.

Fields:

- command ID and draw family;
- selected route or refusal;
- atlas strategy and policy;
- artifact type;
- content key preimage hash;
- residency facts: atlas generation, page, plot, plot generation, coordinates;
- atlas descriptor hash;
- entry descriptor hash;
- bounds, mask origin, mask size, padding, and coverage quality;
- transform key and clip key when used;
- texture/view/sampler descriptor hashes;
- resource owner scope and lifetime class;
- upload or compute write bytes;
- budget policy ID, budget used, budget remaining, and budget-related flag;
- hard capability facts that affected selection;
- mutation action;
- use token or retry/split decision;
- eviction/rebuild/refusal reason;
- stable reason code.

Stable reason-code examples:

- `unsupported.atlas.policy_unavailable`
- `unsupported.atlas.entry_too_large`
- `unsupported.atlas.entry_area_exceeded`
- `unsupported.atlas.capacity`
- `unsupported.atlas.budget_exceeded`
- `unsupported.atlas.upload_budget_exceeded`
- `unsupported.atlas.compute_unavailable`
- `unsupported.atlas.storage_texture_unavailable`
- `unsupported.atlas.sync_unavailable`
- `unsupported.atlas.in_use_try_again_limit`
- `unsupported.atlas.evicted_before_use`
- `unsupported.atlas.generation_stale`
- `unsupported.atlas.key_nondeterministic`
- `unsupported.atlas.upload_failed`
- `unsupported.path.coordinate_too_large`
- `unsupported.path.edge_budget`
- `unsupported.path.stroke_unsupported`
- `unsupported.path.volatile_uncacheable`
- `unsupported.clip.stack_unbounded`
- `unsupported.clip.mask_key_nondeterministic`
- `unsupported.coverage.mask_bounds_invalid`
- `unsupported.coverage.alpha_mask_unavailable`

Existing geometry/coverage reasons such as `coverage.edge-count-exceeded`,
`coverage.atlas-policy-unavailable`, `coverage.alpha-mask-unsupported`, and
`coverage.arbitrary-aa-clip-unsupported` may remain in migration contexts, but
GPU renderer route diagnostics should prefer the renderer-specific
`unsupported.*` codes above when the failure occurs inside this module.

## Validation Requirements

Promoted atlas behavior requires:

- canonical dumps for `GPUPathAtlasPlan`, `GPUCoverageAtlasPlan`,
  `GPUAtlasPolicy`, `GPUAtlasBudgetPolicy`, `GPUAtlasDescriptor`,
  `GPUAtlasPageDescriptor`, `GPUAtlasPlotDescriptor`,
  `GPUAtlasEntryDescriptor`, `GPUAtlasEntryRef`, `GPUAtlasGeneration`,
  `GPUAtlasUseToken`, `GPUAtlasMutationPlan`, `GPUAtlasUploadPlan`,
  `GPUCoverageMaskDescriptor`, and `GPUAtlasDiagnostic`;
- key determinism tests for `GPUPathAtlasKey` and `GPUCoverageAtlasKey`;
- negative tests for nondeterministic mutable path keys;
- path content-key tests for fill, inverse fill, stroke, dash refusal,
  transform, tolerance, and edge budget;
- clip coverage-key tests for element list, intersect/difference, initial
  alpha, inverse, save-record identity, and device bounds;
- atlas residency tests for generation, plot/page generation, stale ref,
  eviction, rebuild, and refuse paths;
- upload-before-sample tests;
- compute-write-before-sample tests before compute atlas promotion;
- `SplitPassAndRetry` positive and negative tests;
- budget pressure tests for hard capability limits and configurable policy
  limits;
- planner tests proving atlas mutations stop sorting and merging;
- payload tests proving atlas coordinates and entry refs stay out of
  `MaterialKey` and durable pipeline keys;
- texture ownership tests proving atlas textures are `AtlasTexture` resources,
  not user images or uploaded texture fallbacks;
- GPU evidence for promoted GPU sampling or compute atlas routes;
- CPU oracle or explicit refusal evidence for path/clip coverage correctness;
- PM evidence that shows atlas routes, bytes, generations, retries, evictions,
  and refusals.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient implementation slice does not
activate path, glyph, or persistent coverage atlas routes.

It may validate atlas-adjacent refusal behavior:

- path fill/stroke commands refuse with stable path/atlas diagnostics when
  routed through the new module;
- complex clip commands refuse with stable clip/atlas diagnostics;
- texture ownership tests may include stale atlas-generation refusal through a
  deterministic test double;
- payload and material-key tests may assert that atlas coordinates and entry
  refs are excluded.

The first slice must not create `PathAtlasArtifact`, `CoverageMaskArtifact`, or
atlas textures as supported rendering routes unless a later accepted ticket
explicitly expands the slice.

## Non-Goals

- Do not port Graphite `DrawAtlas`, `PathAtlas`, `RasterPathAtlas`,
  `ComputePathAtlas`, `ClipAtlasManager`, or `AtlasProvider`.
- Do not mirror Graphite UV/page bit packing.
- Do not rebuild SkSL or depend on SkSL for atlas sampling.
- Do not make atlas residency part of `MaterialKey`.
- Do not use raw GPU handles or Kotlin object identity as atlas keys.
- Do not treat atlas cache hits as correctness evidence.
- Do not sample stale atlas entries.
- Do not hide atlas errors behind `nullptr`, log-only drops, or silent draw
  discard.
- Do not use Graphite's generic `ProxyCache` behavior as a Kanvas fallback.
- Do not CPU-render complete unsupported draws, layers, filters, or scenes into
  a texture for GPU composition.
- Do not merge path, clip, glyph, image, and filter atlas lifetimes into one
  cache policy.
- Do not require persistent atlas support before profiling and validation
  justify it.
