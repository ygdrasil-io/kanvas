# Graphite Algorithm References For GPU Renderer Tickets

These references are local, pinned evidence for agents working on
`:gpu-renderer` tickets. They point to the local Skia checkout at
`/Users/chaos/workspace/kanvas-forge/skia-main`, observed at commit
`defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`.

Use them as algorithm references only. Kanvas must not port Graphite or Ganesh,
must keep WebGPU as the backend, and must keep `SkRuntimeEffect` as a
compatibility facade backed by registered Kotlin/WGSL implementations.

## Core Recording, Draw Ordering, And Resources

<a id="gfx-recorder-snap"></a>
### GFX-RECORDER-SNAP - Recording snapshot and root task ordering

- Source: [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198)
- Algorithm to study: flush tracked devices, finalize draw/upload buffers, put
  root uploads before dependent render tasks, run `prepareResources`, then reset
  transient dictionaries, proxy read counts, atlases, and builder capacity.
- Kanvas boundary: use the lifecycle model for snapshot/readiness evidence, not
  Graphite task classes.

<a id="gfx-drawcontext-flush"></a>
### GFX-DRAWCONTEXT-FLUSH - Upload, compute, draw pass extraction

- Source: [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213)
- Algorithm to study: drain pending uploads, record compute path-atlas
  dispatches, derive pass bounds/MSAA/depth-stencil/destination-read strategy,
  then convert pending draws into an immutable `DrawPass`.
- Kanvas boundary: model the ordering and evidence, but express it through the
  Kanvas WebGPU command plan.

<a id="gfx-drawcontext-record"></a>
### GFX-DRAWCONTEXT-RECORD - Barrier selection before draw recording

- Source: [DrawContext.cpp:155](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:155)
- Algorithm to study: classify whether a draw needs a destination-read barrier
  or advanced-blend barrier before inserting it into the pending draw list.
- Kanvas boundary: map this to explicit WebGPU pass/copy/barrier diagnostics.

<a id="gfx-draw-order"></a>
### GFX-DRAW-ORDER - Painter order, depth, and disjoint stencil ordering

- Source: [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52)
- Algorithm to study: encode compressed painter order, depth ordering, and
  disjoint stencil indices so batching can reorder compatible work without
  violating visible draw order.
- Kanvas boundary: use this for sort-key evidence and route diagnostics, not as
  a license to reorder unsupported draw families.

<a id="gfx-drawlist-record"></a>
### GFX-DRAWLIST-RECORD - Per-RenderStep key and data capture

- Source: [DrawList.cpp:21](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:21)
- Algorithm to study: one high-level draw expands to one sort key per
  `RenderStep`; each key combines render step ID, paint ID, deduplicated
  uniform data, and texture binding data.
- Kanvas boundary: use the key shape as inspiration for route/pipeline keys,
  not the Graphite C++ data structures.

<a id="gfx-drawlist-sort"></a>
### GFX-DRAWLIST-SORT - State-minimizing sorted draw pass construction

- Source: [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90)
- Algorithm to study: sort compact keys, detect pipeline/uniform/texture/scissor
  state changes, flush the draw writer when state or barriers require it, and
  accumulate per-pipeline draw area telemetry.
- Kanvas boundary: preserve Kanvas painter-order and fallback policy even when
  adopting similar batching evidence.

<a id="gfx-draw-writer"></a>
### GFX-DRAW-WRITER - Coalesced draw emission

- Source: [DrawWriter.cpp:32](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawWriter.cpp:32)
- Algorithm to study: coalesce pending vertices/instances, bind only changed
  buffers, choose draw/drawIndexed/drawInstanced commands, and insert barriers
  at writer flush boundaries.
- Kanvas boundary: use this for command-count and buffer-binding evidence, not
  for importing Graphite writer ownership.

<a id="gfx-drawlist-layer"></a>
### GFX-DRAWLIST-LAYER - Layered batching with stencil and destination constraints

- Source: [DrawListLayer.cpp:48](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawListLayer.cpp:48)
- Algorithm to study: search layers backward for compatible batching,
  optionally forward-merge safe single-step draws, and keep stencil sequences
  atomic within a layer.
- Kanvas boundary: use the constraints to design route-local batching tests, not
  as a requirement to implement Graphite's layer list.

<a id="gfx-drawpass-prepare"></a>
### GFX-DRAWPASS-PREPARE - Pipeline handles and sampled texture validation

- Source: [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40)
- Algorithm to study: turn pipeline descriptions into handles, start pipeline
  creation, resolve handles, reject missing pipelines, and reject non-lazy,
  uninstantiated sampled texture proxies before command submission.
- Kanvas boundary: equivalent WebGPU validation should be surfaced as route
  diagnostics and evidence artifacts.

<a id="gfx-renderpass-task"></a>
### GFX-RENDERPASS-TASK - Render pass resource preparation and replay

- Source: [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128)
- Algorithm to study: instantiate targets, prepare draw passes, recycle scratch
  resources, allocate MSAA/depth-stencil attachments, and replay the render pass
  with destination-copy metadata.
- Kanvas boundary: map to Kanvas pass execution evidence and avoid hiding
  resource creation failures.

<a id="gfx-tasklist"></a>
### GFX-TASKLIST - Ordered prepare/addCommands task traversal

- Source: [TaskList.cpp:19](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/TaskList.cpp:19)
- Algorithm to study: walk tasks in order, honor success/discard/fail statuses,
  scope scratch resources during preparation, and replay only prepared tasks.
- Kanvas boundary: useful for `Recording` readiness and migration gates, not as
  an imported task runtime.

<a id="gfx-pipeline-manager"></a>
### GFX-PIPELINE-MANAGER - Pipeline cache/task race handling

- Source: [PipelineManager.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PipelineManager.cpp:38)
- Algorithm to study: derive a unique pipeline key, reuse in-flight creation
  tasks, check the global cache, create a task on miss, then resolve the task
  when preparation starts.
- Kanvas boundary: use this for pipeline cache telemetry and race-safe creation
  semantics, not for adopting Skia cache ownership.

<a id="gfx-resource-keyed-cache"></a>
### GFX-RESOURCE-KEYED-CACHE - Texture, sampler, and buffer keys

- Source: [ResourceProvider.cpp:113](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceProvider.cpp:113)
- Algorithm to study: build keys from dimensions/texture info or sampler/buffer
  descriptors, distinguish budgeted/shareable/scratch resources, and insert
  created resources into a cache after validation.
- Kanvas boundary: keep Kanvas resource ownership explicit and report cache
  misses/evictions with Kanvas vocabulary.

<a id="gfx-resource-cache-mru"></a>
### GFX-RESOURCE-CACHE-MRU - Resource cache lookup, reuse, and purge

- Source: [ResourceCache.cpp:163](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceCache.cpp:163)
- Algorithm to study: find keyed resources, take refs safely, distinguish
  shareable/scratch/non-shareable usage, maintain MRU order, and purge returned
  or stale resources.
- Kanvas boundary: use for telemetry and release-gate thresholds, not for
  obscuring resource lifetime in route code.

<a id="gfx-renderstep-model"></a>
### GFX-RENDERSTEP-MODEL - Technique decomposition contract

- Source: [Renderer.h:83](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.h:83)
- Algorithm to study: decompose one high-level renderer into ordered
  `RenderStep`s that can be batched across draws if stencil and painter-order
  dependencies remain valid.
- Kanvas boundary: use the decomposition concept for route design; do not reuse
  SkSL hooks or Graphite `RenderStep` APIs.

## Geometry, Coverage, Clips, And Paths

<a id="gfx-renderer-strategy"></a>
### GFX-RENDERER-STRATEGY - Path renderer capability selection

- Source: [RendererProvider.cpp:80](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/RendererProvider.cpp:80)
- Algorithm to study: choose a path strategy from capabilities, preferring
  compute when available, then tessellation/small-atlas, then raster atlas, and
  initialize reusable render steps for shapes, text, vertices, and coverage.
- Kanvas boundary: translate this into explicit Kanvas route decisions and
  dependency gates.

<a id="gfx-drawgeometry-routing"></a>
### GFX-DRAWGEOMETRY-ROUTING - Draw-family routing and order updates

- Source: [Device.cpp:1512](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1512)
- Algorithm to study: compute clipped bounds, pick a renderer or atlas, derive
  paint keys before recording, handle atlas insertion, update clip/depth order,
  and split stroke/fill/inner-fill draws when needed.
- Kanvas boundary: use the route skeleton to define acceptance evidence, not to
  inherit Graphite draw behavior wholesale.

<a id="gfx-shape-routing-heuristics"></a>
### GFX-SHAPE-ROUTING-HEURISTICS - Simple, analytic, atlas, and tessellation routing

- Source: [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900)
- Algorithm to study: route subruns, vertices, coverage masks, edge-AA quads,
  simple rect/rrects, circular arcs, compute/raster atlas paths, or tessellated
  paths based on transform, style, bounds, and capabilities.
- Kanvas boundary: encode comparable refusal reasons when Kanvas lacks an
  accepted route.

<a id="gfx-msaa-path-heuristics"></a>
### GFX-MSAA-PATH-HEURISTICS - Stroke, convex, wedge, and curve path choices

- Source: [Device.cpp:2040](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2040)
- Algorithm to study: choose tessellated strokes for stroke/hairline, convex
  wedges for non-inverted convex fills, and switch between stencil wedges and
  curve+triangle tessellation using verb-count/area heuristics.
- Kanvas boundary: use as a decision-tree reference for future path tickets and
  current refusal diagnostics.

<a id="gfx-clip-simplify"></a>
### GFX-CLIP-SIMPLIFY - Shape-aware clip simplification

- Source: [ClipStack.cpp:348](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ClipStack.cpp:348)
- Algorithm to study: combine bounds, oriented-bbox intersection, containment
  checks, rrect/path identity checks, and clip-op truth tables to simplify or
  preserve complex clip interactions.
- Kanvas boundary: expose deterministic clip lowering and refusal diagnostics
  before claiming support.

<a id="gfx-scissor-snap"></a>
### GFX-SCISSOR-SNAP - Scissor coarsening to reduce state changes

- Source: [ClipStack.cpp:308](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ClipStack.cpp:308)
- Algorithm to study: snap scissor rectangles to coarse pixel boundaries so
  slightly larger rasterization can avoid excessive state churn.
- Kanvas boundary: use only if Kanvas validation keeps parity bounds explicit.

<a id="gfx-renderstep-scissor"></a>
### GFX-RENDERSTEP-SCISSOR - Per-step scissor state minimization

- Source: [Renderer.cpp:49](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Renderer.cpp:49)
- Algorithm to study: avoid scissor changes when current clipped bounds already
  match, canonicalize no-op scissor to device bounds, and treat inverse-fill
  render steps specially.
- Kanvas boundary: use for clip/scissor evidence and do not weaken clip parity.

<a id="gfx-simple-shape-bounds"></a>
### GFX-SIMPLE-SHAPE-BOUNDS - Pixel-aligned and inner-fill bounds

- Source: [Device.cpp:248](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:248)
- Algorithm to study: identify pixel-aligned rects, simple rect/rrect/line
  shapes, circular stroked rrect constraints, and inner-fill bounds that justify
  a second non-AA fill for overdraw reduction.
- Kanvas boundary: map these cases into explicit route support or refusal rows.

<a id="gfx-analytic-rrect-step"></a>
### GFX-ANALYTIC-RRECT-STEP - Analytic rrect coverage and instance encoding

- Source: [AnalyticRRectRenderStep.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/AnalyticRRectRenderStep.cpp:40)
- Algorithm to study: encode filled/stroked/hairline rects, rrects, lines, and
  per-edge quads in one instance format; compute rrect coverage using level-set
  distances and analytic Jacobians.
- Kanvas boundary: use as an algorithm reference for WGSL coverage math, not as
  a source-code port.

<a id="gfx-per-edge-aa-quad"></a>
### GFX-PER-EDGE-AA-QUAD - Edge-flagged quad coverage

- Source: [PerEdgeAAQuadRenderStep.cpp:34](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/PerEdgeAAQuadRenderStep.cpp:34)
- Algorithm to study: encode per-edge AA flags, vertex distances, and winding
  correction so rect/image quads can avoid seams and preserve coverage.
- Kanvas boundary: useful for rect/image quad tickets only after Kanvas parity
  fixtures define acceptable error bounds.

<a id="gfx-gradient-stops"></a>
### GFX-GRADIENT-STOPS - Gradient stop packing and fallback storage

- Source: [KeyHelpers.cpp:166](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:166)
- Algorithm to study: pack 4/8-stop gradients into uniforms, move larger stop
  tables into storage buffers or textures, and carry tile mode/color-space
  interpolation metadata through the paint key.
- Kanvas boundary: keep unsupported stop/color-space combinations as stable
  refusals until WGSL evidence exists.

<a id="gfx-tessellate-wedges"></a>
### GFX-TESSELLATE-WEDGES - Wedge path fill patches

- Source: [TessellateWedgesRenderStep.cpp:82](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateWedgesRenderStep.cpp:82)
- Algorithm to study: pick a fan point per contour, then write line, quad,
  conic, and cubic patches for fixed-count wedge tessellation.
- Kanvas boundary: reference for future path-fill implementation or current
  route refusal diagnostics.

<a id="gfx-tessellate-curves"></a>
### GFX-TESSELLATE-CURVES - Curve patch stencil tessellation

- Source: [TessellateCurvesRenderStep.cpp:80](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateCurvesRenderStep.cpp:80)
- Algorithm to study: use fixed-count curve patches with Wang's-formula scale
  estimates, stencil depth settings, and per-curve patch emission.
- Kanvas boundary: gate by explicit curve complexity limits and WebGPU evidence.

<a id="gfx-tessellate-strokes"></a>
### GFX-TESSELLATE-STROKES - Stroke patch tessellation

- Source: [TessellateStrokesRenderStep.cpp:91](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateStrokesRenderStep.cpp:91)
- Algorithm to study: tessellate stroked paths with cap/join handling, cusp
  handling for quads/conics/cubics, transform scale, and stroke-width uniforms.
- Kanvas boundary: use for stroke acceptance criteria and stable refusals for
  unsupported joins/caps/perspective.

<a id="gfx-path-atlas-contract"></a>
### GFX-PATH-ATLAS-CONTRACT - Transient coverage mask atlas contract

- Source: [PathAtlas.h:29](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PathAtlas.h:29)
- Algorithm to study: transient coverage masks, one-pixel padding, atlas-space
  transforms, and returned `CoverageMaskShape` metadata for sampling.
- Kanvas boundary: a CPU-prepared atlas route remains typed evidence, not a
  hidden CPU-raster fallback.

<a id="gfx-path-atlas-pack"></a>
### GFX-PATH-ATLAS-PACK - Path mask keying, packing, and eviction

- Source: [PathAtlas.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PathAtlas.cpp:38)
- Algorithm to study: round mask bounds, derive clipped-mask origin, generate a
  path-mask key, reuse cached atlas locators, allocate padded atlas rectangles,
  update flush tokens, and evict cache entries by plot.
- Kanvas boundary: include cache hit/miss and atlas overflow evidence before
  route promotion.

<a id="gfx-compute-path-atlas"></a>
### GFX-COMPUTE-PATH-ATLAS - Compute coverage dispatch scheduling

- Source: [ComputePathAtlas.h:31](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ComputePathAtlas.h:31)
- Algorithm to study: track shapes as compute-pass input, record dispatch
  groups, reset scheduled draws after dispatch recording, and lazily allocate a
  shared atlas texture.
- Kanvas boundary: dependency-gate this until an accepted WebGPU compute route
  exists.

<a id="gfx-coverage-mask-step"></a>
### GFX-COVERAGE-MASK-STEP - Atlas mask sampling and inverse fill handling

- Source: [CoverageMaskRenderStep.cpp:39](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/CoverageMaskRenderStep.cpp:39)
- Algorithm to study: sample a coverage atlas with clamped UVs, normalize atlas
  coordinates, handle inverse masks, choose nearest versus linear filtering, and
  extract stable translation into instance data.
- Kanvas boundary: use for coverage-mask sampling routes and refusal policy.

<a id="gfx-raster-mask-helper"></a>
### GFX-RASTER-MASK-HELPER - CPU coverage mask preparation boundary

- Source: [RasterPathUtils.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/RasterPathUtils.h:24)
- Algorithm to study: allocate A8 mask storage, draw shape or clip coverage into
  a pixmap, and generate path/clip mask keys for atlas reuse.
- Kanvas boundary: only valid as `CPUPreparedGPU` when the artifact is typed,
  bounded, uploaded, and diagnosed.

## Paint, Images, Runtime Effects, Text, And Vertices

<a id="gfx-paint-key-tree"></a>
### GFX-PAINT-KEY-TREE - Paint key tree, data blocks, and validation

- Source: [PaintParamsKey.cpp:88](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParamsKey.cpp:88)
- Algorithm to study: decode snippet IDs into a shader node tree, carry embedded
  sampler data blocks, lift expressions when possible, print/dump keys, and
  validate serializable keys against known runtime-effect IDs.
- Kanvas boundary: Kanvas keys should be deterministic and inspectable even
  though WGSL is the implementation target.

<a id="gfx-image-sampler-key"></a>
### GFX-IMAGE-SAMPLER-KEY - Image shader tiling and sampler key data

- Source: [KeyHelpers.cpp:530](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:530)
- Algorithm to study: decide whether hardware can handle tiling, select image
  shader variants for HW/cubic/clamp/manual tiling, add texture/sampler bindings,
  and embed immutable sampler data in the paint key.
- Kanvas boundary: route docs must separate decoded pixel ownership, sampler
  support, and refusal behavior.

<a id="gfx-sampler-desc"></a>
### GFX-SAMPLER-DESC - Compact sampler/tile/mipmap descriptor

- Source: [ResourceTypes.h:238](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceTypes.h:238)
- Algorithm to study: bit-pack tile modes, filter mode, mipmap mode, immutable
  sampler info, and external format bits into a hashable sampler descriptor.
- Kanvas boundary: use the descriptor concept for WebGPU sampler keys and
  diagnostics, not backend-specific immutable sampler semantics.

<a id="gfx-dawn-sampler"></a>
### GFX-DAWN-SAMPLER - WebGPU/Dawn sampler translation

- Source: [DawnSampler.cpp:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnSampler.cpp:52)
- Algorithm to study: translate tile modes to Dawn address modes, filter/mipmap
  options to sampler filters, disable mipmaps via LOD clamp when needed, and
  attach immutable YCbCr metadata when present.
- Kanvas boundary: useful for WebGPU sampler mapping and refusal diagnostics;
  unsupported `decal` or external formats must stay explicit.

<a id="gfx-texture-upload-root"></a>
### GFX-TEXTURE-UPLOAD-ROOT - Bitmap proxy upload scheduling

- Source: [TextureUtils.cpp:251](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:251)
- Algorithm to study: create a texture proxy view, compute mip levels and
  swizzles, build an upload source, attempt host upload, otherwise record a root
  upload task before rendering tasks consume the texture.
- Kanvas boundary: require explicit upload artifact ownership and readback or
  route evidence.

<a id="gfx-mipmap-generation"></a>
### GFX-MIPMAP-GENERATION - Scratch-surface mipmap generation

- Source: [TextureUtils.cpp:553](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:553)
- Algorithm to study: generate mipmaps by ping-ponging between scratch surfaces,
  copy each generated level into the target, and degrade sampling policy when
  required mipmaps are absent.
- Kanvas boundary: unsupported mipmap requirements must remain visible refusals
  until WebGPU evidence exists.

<a id="gfx-upload-task"></a>
### GFX-UPLOAD-TASK - Upload preparation and replay clipping

- Source: [UploadTask.cpp:309](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/UploadTask.cpp:309)
- Algorithm to study: instantiate upload targets during resource preparation,
  copy buffer-to-texture commands, crop replay-target uploads, and discard
  conditional uploads after successful submission.
- Kanvas boundary: upload tasks must remain observable in WebGPU evidence and
  failure diagnostics.

<a id="gfx-dst-read-copy"></a>
### GFX-DST-READ-COPY - Destination-read copy task insertion

- Source: [DrawContext.cpp:270](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:270)
- Algorithm to study: if a pass needs a destination texture copy, copy the
  touched pixel bounds before constructing the render pass task and pass the
  copy into command encoding.
- Kanvas boundary: use as a model for explicit intermediate/copy strategies and
  refusal when unsupported.

<a id="gfx-dst-usage"></a>
### GFX-DST-USAGE - Paint destination usage classification

- Source: [PaintParams.cpp:51](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:51)
- Algorithm to study: classify whether paint depends on destination pixels,
  needs destination-read, uses advanced blend, or only needs destination for the
  renderer's coverage path.
- Kanvas boundary: keep destination-read, blend, and coverage-only needs as
  separate route decisions.

<a id="gfx-image-copy"></a>
### GFX-IMAGE-COPY - Texture copy versus copy-as-draw fallback

- Source: [Image_Graphite.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Image_Graphite.cpp:90)
- Algorithm to study: reject non-copyable/non-texturable sources, choose
  copy-as-draw if direct copy is unsupported, allocate a destination proxy,
  record a copy task, and generate mipmaps when requested.
- Kanvas boundary: document when Kanvas refuses instead of silently replacing
  copy semantics.

<a id="gfx-special-image-layer"></a>
### GFX-SPECIAL-IMAGE-LAYER - Special image wrapping for filter/layer pipelines

- Source: [SpecialImage_Graphite.cpp:20](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/SpecialImage_Graphite.cpp:20)
- Algorithm to study: wrap Graphite-backed images with subset metadata and
  convert non-Graphite images through the recorder image provider before filter
  use.
- Kanvas boundary: use as a reference for saveLayer/filter intermediate
  ownership, not as an accepted filter DAG route.

<a id="gfx-special-draw"></a>
### GFX-SPECIAL-DRAW - Drawing snapped special images and coverage masks

- Source: [Device.cpp:2180](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2180)
- Algorithm to study: draw a special image as an image shader-backed quad,
  manually notify coverage-mask images in use, wrap coverage masks as geometry,
  and snap a device to a special image with copy or image view semantics.
- Kanvas boundary: split saveLayer, filter node, and coverage-mask acceptance
  instead of treating them as one feature.

<a id="gfx-filter-backend"></a>
### GFX-FILTER-BACKEND - Graphite filter backend adapter

- Source: [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720)
- Algorithm to study: provide scratch-device, special-image, cached-bitmap
  proxy, and blur-device hooks used by image filters.
- Kanvas boundary: use for filter-route ownership and refusal criteria, not for
  importing Skia's filter DAG.

<a id="gfx-filter-resolve"></a>
### GFX-FILTER-RESOLVE - Resolve versus deferred shader decisions

- Source: [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334)
- Algorithm to study: decide when a filter result must resolve to texture versus
  remain deferred as shader logic, merging sampling, tiling, color filters, and
  decal behavior.
- Kanvas boundary: use as comparison vocabulary while Kanvas keeps a much
  narrower accepted filter set.

<a id="gfx-blend-keying"></a>
### GFX-BLEND-KEYING - Blend-mode key reduction

- Source: [KeyHelpers.cpp:2593](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:2593)
- Algorithm to study: reduce coefficient blends to constants, group HSL
  advanced blends into a shared snippet, and leave other advanced modes as fixed
  blend snippets.
- Kanvas boundary: maintain a Kanvas allowlist and deterministic refusals for
  modes without accepted WebGPU behavior.

<a id="gfx-runtime-effect-key"></a>
### GFX-RUNTIME-EFFECT-KEY - Runtime-effect snippet registration and uniforms

- Source: [KeyHelpers.cpp:1387](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:1387)
- Algorithm to study: register or find a runtime-effect snippet, stash
  user-defined effects in a transient dictionary, gather transformed uniforms,
  and fall back to no-op behavior when registration fails.
- Kanvas boundary: Kanvas supports registered descriptors only; arbitrary SkSL
  source remains a refusal path.

<a id="gfx-runtime-effect-preamble"></a>
### GFX-RUNTIME-EFFECT-PREAMBLE - Runtime-effect shader callback lowering

- Source: [ShaderCodeDictionary.cpp:638](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ShaderCodeDictionary.cpp:638)
- Algorithm to study: resolve known or transient runtime effects, convert their
  program through pipeline callbacks, and inject child sampling/color transform
  callbacks into the generated preamble.
- Kanvas boundary: useful for descriptor vocabulary only; Kanvas must not build
  a SkSL compiler.

<a id="gfx-text-atlas-config"></a>
### GFX-TEXT-ATLAS-CONFIG - Glyph atlas sizing and multitexture policy

- Source: [TextAtlasManager.cpp:47](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:47)
- Algorithm to study: derive atlas/plot dimensions from max texture size and
  glyph cache byte budget, choose multitexturing based on caps, and keep A8
  atlas dimensions larger than ARGB/LCD dimensions.
- Kanvas boundary: use for text resource planning and PM evidence, not as proof
  that text rendering is supported.

<a id="gfx-text-atlas-glyph-upload"></a>
### GFX-TEXT-ATLAS-GLYPH-UPLOAD - Glyph normalization and atlas upload

- Source: [TextAtlasManager.cpp:237](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:237)
- Algorithm to study: resolve mask format, normalize glyph pixels with padding,
  add glyphs to a `DrawAtlas`, inset source coordinates, and record pending
  atlas uploads.
- Kanvas boundary: requires pure-Kotlin text payload provenance and typed upload
  plans before promotion.

<a id="gfx-draw-atlas-plots"></a>
### GFX-DRAW-ATLAS-PLOTS - Plot allocation, LRU, and retry

- Source: [DrawAtlas.cpp:149](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawAtlas.cpp:149)
- Algorithm to study: place rectangles into atlas plots, track active pages,
  evict by usage where possible, and return retry when a flush is needed before
  insertion can succeed.
- Kanvas boundary: use for glyph/path atlas overflow diagnostics and PM
  evidence.

<a id="gfx-subrun-data"></a>
### GFX-SUBRUN-DATA - Typed glyph subrun handoff

- Source: [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24)
- Algorithm to study: carry a subspan of an atlas subrun, mask bounds,
  mask-to-device matrix, glyph range, SDF/LCD metadata, recorder ownership, and
  renderer data as geometry.
- Kanvas boundary: mirrors the required pure-Kotlin text handoff contract.

<a id="gfx-bitmap-text-step"></a>
### GFX-BITMAP-TEXT-STEP - Bitmap text atlas sampling render step

- Source: [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59)
- Algorithm to study: choose A8/LCD/color variants, append per-glyph instance
  data, bind up to four atlas textures, and produce coverage or primitive color
  from indexed atlas samples.
- Kanvas boundary: adapt the data-flow pattern to WGSL and WebGPU bindings only
  after text artifact contracts land.

<a id="gfx-sdf-text-step"></a>
### GFX-SDF-TEXT-STEP - SDF text atlas sampling and gamma parameters

- Source: [SDFTextRenderStep.cpp:95](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/SDFTextRenderStep.cpp:95)
- Algorithm to study: sample an indexed SDF atlas, carry luminance/gamma/pixel
  geometry parameters, bind atlas proxies with linear clamp sampling, and emit
  coverage.
- Kanvas boundary: keep SDF/color glyph support dependency-gated until text
  artifacts and shader evidence exist.

<a id="gfx-vertices-step"></a>
### GFX-VERTICES-STEP - Vertices variant and append-data expansion

- Source: [VerticesRenderStep.cpp:71](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/VerticesRenderStep.cpp:71)
- Algorithm to study: specialize by primitive type, colors, and texcoords;
  append expanded triangle vertices; carry transform/depth as uniforms; emit
  primitive color when vertex colors exist.
- Kanvas boundary: use to design `DrawVertices` descriptors, payload validation,
  and batching evidence without adopting Graphite vertex writers.

<a id="gfx-paintparams-to-key"></a>
### GFX-PAINTPARAMS-TO-KEY - Paint lowering to key and `DstUsage`

- Source: [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222)
- Algorithm to study: lower paint color, image shader, primitive color, color
  filters, and final blend into key blocks while producing destination-usage
  metadata.
- Kanvas boundary: use to structure color/blend/vertices evidence while keeping
  WGSL descriptor support explicit.
