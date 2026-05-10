# Graphite Backend

Graphite is Skia's modern GPU backend. It targets explicit, command-buffer
APIs (Vulkan, Metal, Dawn / WebGPU) and is structured around a
record-then-replay model: clients build `Recording` objects on a worker
thread via a `Recorder`, then hand them to a single `Context` on the GPU
thread for submission. Compared with [Ganesh](ganesh-backend.md), Graphite
removes the implicit GL state machine, makes pipeline creation cacheable
and asynchronous, and unifies CPU and GPU work behind a directed acyclic
graph of `Task` objects.

For the cross-backend GPU layer (atlases, tessellation, SkSL compilation),
see [GPU Overview](gpu-overview.md). Backend-specific dispatch lives in
[Vulkan](backend-vulkan.md), [Metal](backend-metal.md), and
[Dawn / WebGPU](backend-dawn.md). Programmable shading shares the same
language documented in [SkSL Shading Language](sksl-shading-language.md).

## Pipeline at a glance

```
   client thread(s)                       GPU thread
  ┌───────────────────┐                 ┌──────────────────┐
  │  Recorder         │                 │  Context         │
  │  - SkCanvas       │                 │  - QueueManager  │
  │  - DrawList       │                 │  - SharedContext │
  │  - DrawPass       │                 │  - GlobalCache   │
  │  - TaskList       │                 │                  │
  └─────┬─────────────┘                 └────────┬─────────┘
        │ snap()                                  ▲
        ▼                                         │ insertRecording
  ┌───────────────────┐    handed off    ┌────────┴─────────┐
  │  Recording        │  ──────────────► │  CommandBuffer   │ ─► GPU
  │  (root TaskList,  │                  │  (Vk / Mtl /     │
  │   resource refs,  │                  │   Dawn)          │
  │   lazy proxies)   │                  └──────────────────┘
  └───────────────────┘
```

A draw on a Graphite-backed `SkCanvas` is intercepted by `Device`, which
appends to a `DrawList`. When the canvas is flushed (or the recorder is
snapped) the draws are sorted, batched into a `DrawPass`, and wrapped in a
`RenderPassTask`. The recorder's `TaskList` collects render-pass, copy,
upload, compute, and clear tasks, and `Recorder::snap()` packages them
into a `Recording`. The `Context` then plays the recording back: each
task's `prepareResources()` instantiates lazy proxies and `addCommands()`
encodes commands into a backend `CommandBuffer`. Commands are flushed on
`Context::submit()`.

---

## Public surface — `include/gpu/graphite/`

| Header | Role |
|---|---|
| `Context.h` | Per-GPU object that owns the queue, shared cache, and submission. One per backend device. |
| `ContextOptions.h` | Caps overrides, label/debug knobs, allowed precompile budget. |
| `Recorder.h` | Worker-thread recording surface; spawns canvases/surfaces and snaps `Recording` objects. |
| `Recording.h` | Immutable, replayable bundle of tasks plus pinned resources. |
| `Surface.h`, `Image.h` | Factories for `SkSurface` / `SkImage` backed by Graphite proxies or imported textures. |
| `BackendTexture.h`, `TextureInfo.h` | Opaque handles for client-supplied or Skia-allocated textures. |
| `BackendSemaphore.h` | Cross-queue synchronization primitives. |
| `GraphiteTypes.h` | `BackendApi`, `InsertStatus`, `SyncToCpu`, `Volatile` enums shared across backends. |
| `ImageProvider.h` | Client hook for resolving lazy `SkImage`s on demand. |
| `PersistentPipelineStorage.h` | Two-method `load()` / `store()` blob hook used to persist compiled pipelines across runs. |
| `PrecompileContext.h`, `precompile/Precompile*.h` | Movable handle and `PaintOption` factories for offline pipeline compilation. |
| `YUVABackendTextures.h` | Multi-plane texture wrapper for YUV imports. |
| `dawn/`, `mtl/`, `vk/` | Backend factory headers (`MakeDawn(...)`, `MakeMetal(...)`, `MakeVulkan(...)`). |

The bulk of the implementation is in `src/gpu/graphite/`. Subdirectories
divide the work:

| Directory | Contents |
|---|---|
| `task/` | `Task` base class plus `RenderPassTask`, `CopyTask`, `UploadTask`, `ComputeTask`, `ClearBuffersTask`, `SynchronizeToCpuTask`, `DrawTask`, and the `TaskList` container. |
| `render/` | `RenderStep` subclasses — one per primitive technique (`AnalyticRRect`, `TessellateCurves`, `SDFText`, `BitmapText`, `CoverageMask`, `Vertices`, …). |
| `geom/` | Internal geometry: `Shape`, `Rect`, `Transform`, `EdgeAAQuad`, `IntersectionTree` for occlusion. |
| `compute/` | `ComputeStep`, `DispatchGroup`, plus the optional `VelloComputeSteps` integration. |
| `precompile/` | `PaintOption` graph and `PrecompileShader/Blender/ColorFilter/ImageFilter/MaskFilter/RuntimeEffect` builders. |
| `text/` | Graphite-side glyph atlases and SubRun bookkeeping (companion to `sktext::gpu`). |
| `surface/` | `Surface_Graphite`, `SpecialImage_Graphite`. |
| `sparse_strips/` | Experimental tile-based rasterizer support. |
| `dawn/`, `mtl/`, `vk/` | Backend implementations of `CommandBuffer`, `GraphicsPipeline`, `Sampler`, `Texture`, `Buffer`, `QueueManager`. |

---

## Context — the GPU-thread owner

`Context` (`src/gpu/graphite/Context.cpp`) is the singleton root for a
single GPU device. It is constructed indirectly through
`skgpu::graphite::ContextFactory::MakeVulkan` / `MakeMetal` / `MakeDawn`
and exposes:

- `makeRecorder(RecorderOptions)` — spawn a worker-thread recorder.
- `makePrecompileContext()` — movable helper for offline pipeline build.
- `insertRecording(InsertRecordingInfo)` — enqueue a `Recording`. The
  info struct also accepts a target `BackendTexture` (for late
  retargeting), wait/signal `BackendSemaphore`s, and a finished proc.
- `submit(SubmitInfo)` — flush the `QueueManager`, optionally with
  `SyncToCpu::kYes` to block until completion.
- `asyncRescaleAndReadPixels` / `asyncRescaleAndReadPixelsYUV420` —
  callback-based readback that respects the backend's signal model.
- `freeGpuResources()`, `performDeferredCleanup()`,
  `setCurrentTimestamp()`, `dumpMemoryStatistics()` — lifecycle hooks.
- `makeCPURecorder()` — short-circuits to the legacy raster pipeline so
  client code can share a single recorder API.

The `Context` holds a `SharedContext` (caps, code dictionary, renderer
provider) and a `GlobalCache` (`PipelineManager`, `ProxyCache`,
`StaticBufferManager`). Everything that survives across recorders lives
on the shared context; everything that is per-frame lives on the
recorder.

---

## Recorder — building `Recording`s off-thread

`Recorder` (`src/gpu/graphite/Recorder.h`, `Recorder.cpp`) is the
per-thread record buffer. It is also an `SkRecorder`, so client code that
has already been written against Ganesh's `SkSurface` API works
unchanged. Each recorder owns its own:

- `DrawBufferManager`, `UploadBufferManager`, `FloatStorageManager` —
  GPU-staged transient buffers.
- `TaskList` — the root task DAG built so far.
- `ResourceProvider` — per-recorder allocation arena (sub-allocations are
  recycled into a `ResourceCache` on flush).
- `AtlasProvider` (text + path masks) and a `RuntimeEffectDictionary`.
- `PaintParamsKeyBuilder` and `PipelineDataGatherer` for PaintParamsKey
  hashing.

`RecorderOptions` lets the embedder cap the per-recorder GPU budget
(default 256 MiB) and pin the order of `Recording` playback — useful when
internal text atlases must remain coherent. Recorders are owned by
`unique_ptr` and are destroyed before the `Context` they came from.

`Recorder::snap()` finalizes the in-progress `DrawPass`es, freezes the
current `TaskList` into a `Recording`, transfers ownership of the
referenced resources, and records any `LazyProxyData` that the eventual
playback target must satisfy.

---

## Recording — the shippable bundle

`Recording` (`include/gpu/graphite/Recording.h`) is a movable, opaque
container that holds:

- `fRootTaskList` — the DAG of `Task` objects.
- `fExtraResourceRefs` — references to resources used by the tasks but
  not directly owned by them (e.g. shared upload buffers).
- `fNonVolatileLazyProxies` / `fVolatileLazyProxies` — proxies that are
  resolved at insertion time.
- `fTargetProxyData` — optional late-bound render target (for
  `replay()`).
- `fFinishedProcs` — callbacks fired when the GPU work completes.

Recordings are insertable on any `Context` whose `Recorder` produced
them. They can be replayed multiple times when the recorder is created
with `fRequireOrderedRecordings = true` and no per-replay state mutates.

---

## Tasks — the unit of GPU work

`Task` (`src/gpu/graphite/task/Task.h`) is the abstract base for every
operation Graphite issues. The interface is small:

- `Status prepareResources(ResourceProvider*, ScratchResourceManager*,
  sk_sp<const RuntimeEffectDictionary>)` — instantiate textures /
  buffers / pipelines, return `kSuccess`, `kDiscard` (drop on replay), or
  `kFail` (invalidate the recording).
- `Status addCommands(Context*, CommandBuffer*, ReplayTargetData)` —
  encode the actual backend commands.
- `visitPipelines` / `visitProxies` — traversal hooks used by the
  scheduler and the pipeline manager.

Concrete subclasses cover every kind of GPU work:

| Task | Purpose |
|---|---|
| `RenderPassTask` | Begin/end a backend render pass, walk a list of `DrawPass`es. |
| `DrawPass` (not a Task itself; carried by `RenderPassTask`) | A sorted set of draws sharing render-target attachments. |
| `CopyTask` | `copyBufferToTexture`, `copyTextureToTexture`, `copyTextureToBuffer`. |
| `UploadTask` | Stage CPU pixel data through `UploadBufferManager` into a texture. |
| `ComputeTask` | Run a `DispatchGroup` of `ComputeStep`s (e.g. Vello, gradient SDF). |
| `ClearBuffersTask` | Zero a slab of GPU buffer for indirect draws. |
| `SynchronizeToCpuTask` | Make GPU writes visible for CPU readback. |
| `DrawTask` | A nested `TaskList` with its own scope (used by
sub-recordings and offscreen layers). |

The `TaskList` is a flat array but conceptually a DAG: each task may
depend on resources written by earlier tasks, and the
`ScratchResourceManager` performs lifetime-based aliasing so that
short-lived render targets share underlying textures.

---

## Renderers and RenderSteps

Where Ganesh has `GrOp`, Graphite has `Renderer` (`Renderer.h`), a
non-virtual handle to an ordered list of `RenderStep`s. The
`SKGPU_RENDERSTEP_TYPES` X-macro in `Renderer.h` enumerates every
technique — a partial list:

- `AnalyticRRect`, `AnalyticBlur`, `CircularArc` — closed-form
  rasterization in the fragment shader.
- `PerEdgeAAQuad`, `CoverBounds(NonAAFill | RegularCover | InverseCover)`
  — quad-based fills and stencil covers.
- `TessellateCurves`, `TessellateStrokes`, `TessellateWedges`,
  `MiddleOutFan` — curve / stroke / fan tessellation built on the shared
  [GPU Tessellation](gpu-tessellation.md) primitives.
- `BitmapText(Mask | LCD | Color)`, `SDFText`, `SDFTextLCD`,
  `CoverageMask` — atlas-fed glyph and mask drawing.
- `Vertices(Tris | Tristrips × Color × TexCoords)` — `SkVertices`
  passthrough.

A `RenderStep` declares its vertex / instance / uniform layout, its
required depth/stencil settings, and the SkSL fragment for its primitive
geometry. Paint shading is composed on top via the paint key system
(`PaintParamsKey`, `KeyContext`, `KeyHelpers`) so the same render step
can be reused with arbitrary shaders/blenders/color filters.

`RendererProvider` (held by `SharedContext`) caches the singleton
`Renderer` for each technique; `BuiltInCodeSnippetID` enumerates the
shared SkSL fragments stored in `ShaderCodeDictionary`.

---

## Pipelines — caching and precompile

A `GraphicsPipeline` (`GraphicsPipeline.h`) is the backend-compiled
artifact derived from a `GraphicsPipelineDesc` (RenderStep ID + paint key
+ render-pass key). The `PipelineManager` deduplicates desc hashes,
hands out `GraphicsPipelineHandle`s, and drives async compilation
through `PipelineCreationTask`s on a backend-supplied executor.

`PersistentPipelineStorage` (one of the smallest public headers, two
methods: `load()` / `store()`) lets the embedder save the binary blob of
compiled pipelines to disk so they need not be rebuilt on next launch.
Backends that natively expose binary pipelines (Metal, Vulkan with
`VK_EXT_pipeline_binary`) feed straight into this hook.

For applications that want zero shader-compile jank in steady state, the
`precompile/` subsystem builds pipelines ahead of time. A
`PaintOption` (`precompile/PaintOption.h`) enumerates the combinatorial
shader/blender/colorFilter possibilities a draw might use; passing a set
of `PaintOptions` plus a `RenderPassDesc` to `Precompile()` produces the
matching `GraphicsPipeline`s and warms the cache. `PrecompileContext` is
the movable cousin of `Context` that lets the precompile work run on a
dedicated worker thread.

---

## Compute, atlases, and clip

Graphite supports compute pipelines through `ComputeStep` /
`ComputePipeline` and groups them into a `DispatchGroup` that
`ComputeTask` schedules. The optional Vello (linebender/vello) renderer
is wired up via `VelloComputeSteps.cpp` and `ComputePathAtlas.cpp`,
which rasterize complex paths on the GPU into a coverage atlas.

`AtlasProvider` (`AtlasProvider.cpp`) owns:

- `DrawAtlas` — generic glyph/path atlas (multi-page, recycle-on-flush).
- `RasterPathAtlas` — CPU-rasterized fallback for paths that defeat
  analytic and tessellated render steps.
- `ComputePathAtlas` — Vello-based GPU rasterizer (when enabled).
- `ClipAtlasManager` — masks for complex clips that exceed the analytic
  clip stack.

`ClipStack` (`ClipStack.cpp`) tracks per-`Device` clipping. Simple
intersect rules collapse into the analytic path (depth/stencil + paint
key); harder cases promote into a `CoverageMaskShape` rendered through
the clip atlas. `BoundsManager` and `IntersectionTree` (in `geom/`) keep
overdraw-aware ordering so that opaque draws can be reordered ahead of
transparent ones.

---

## Cross-references

- The shared GPU layer (atlases, glyph caching, SkSL compiler driver,
  `GrBackendApi` enums) lives in `src/gpu/` and is documented in
  [GPU Overview](gpu-overview.md).
- For the legacy backend that Graphite supersedes — including its op
  list, `GrCaps`, and direct GL state tracking — see
  [Ganesh Backend](ganesh-backend.md).
- For the curve-tessellation primitives the
  `Tessellate{Curves,Strokes,Wedges}RenderStep`s consume, see
  [GPU Tessellation](gpu-tessellation.md).
- Backend dispatch is in [Vulkan](backend-vulkan.md),
  [Metal](backend-metal.md), and [Dawn / WebGPU](backend-dawn.md).
- Shader composition uses
  [SkSL Shading Language](sksl-shading-language.md) plus the paint key
  system documented alongside [Runtime Effects](runtime-effects.md).
