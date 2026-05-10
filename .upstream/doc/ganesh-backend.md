# Ganesh Backend

**Ganesh** is Skia's original (and still default) GPU backend, sitting
between the high-level canvas API and one of the per-API ports
([OpenGL / GLES](backend-opengl.md), [Vulkan](backend-vulkan.md),
[Metal](backend-metal.md), [Direct3D](backend-direct3d.md), the
WebGPU-via-Dawn frontend in [Dawn](backend-dawn.md), and the in-process
**Mock** backend used by tests). The newer [Graphite Backend](graphite-backend.md)
re-implements the same job with a record/replay model, but Ganesh
remains the production GPU backend on Android, Chrome, Flutter, and
most CanvasKit deployments.

This document covers the architecture: contexts, the device, render
tasks, the op pipeline, the clip stack, the GLSL program builder, and
the path-rendering hierarchy. The shared `skgpu::` primitives that
Ganesh sits on top of (atlases, blend formulas, swizzles, resource
keys, rectanizers, …) are described in [GPU Overview](gpu-overview.md).
SkSL compilation upstream of the GLSL emitter is [SkSL Shading
Language](sksl-shading-language.md).

All Ganesh code lives under `src/gpu/ganesh/` (private) with public
headers under `include/gpu/ganesh/`. Sources in this document are
relative to those roots unless otherwise noted.

## End-to-end pipeline

```
   SkCanvas::draw()
        │
        ▼
   skgpu::ganesh::Device  →  SurfaceDrawContext
        │
        │  addDrawOp(GrPaint, GrFragmentProcessor tree, GrAppliedClip, …)
        ▼
   GrOp ─chain/merge→ OpsTask  ⊂  GrRenderTask  (DAG node)
                                 │
                                 │  GrDrawingManager: cluster + topo-sort
                                 ▼
                        onPrepare:
                          - alloc vertex/uniform buffers
                          - GrGLSLProgramBuilder → SkSLToBackend
                            (GLSL / SPIR-V / MSL / HLSL / WGSL)
                                 │
                                 ▼
                        onExecute:
                          GrOpFlushState → GrOpsRenderPass → GrGpu
                                                              │
                                                              ▼
                                                  GL / VK / MTL / D3D / Mock
```

A `GrDirectContext` owns the GPU. From it, `SkSurface` factories
(`include/gpu/ganesh/SkSurfaceGanesh.h`) produce surfaces that hand
out an `SkCanvas` whose backing `SkDevice` is `skgpu::ganesh::Device`.
Every draw call funnels into that device and onto a
`SurfaceDrawContext`, which records `GrOp`s into an `OpsTask`. Tasks
form a DAG managed by `GrDrawingManager`. At flush time the manager
sorts tasks by their texture/render-target dependencies, asks each op
to `onPrepare` (build vertex/uniform buffers and link a GLSL program
through `GrGLSLProgramBuilder`), then asks each op to `onExecute`
through a `GrOpsRenderPass` produced by `GrGpu`.

Per-API ports live in sibling directories (`src/gpu/ganesh/gl/`,
`vk/`, `mtl/`, `d3d/`, `mock/`); see each [backend-*](backend-opengl.md)
document.

---

## Contexts — `GrRecordingContext`, `GrDirectContext`, `GrContextThreadSafeProxy`

The context hierarchy is three layers deep:

- **`GrContext_Base`** (private) holds shared bookkeeping (back-end
  enum, context ID, `GrCaps`).
- **`GrImageContext`** (private) layers in image and proxy creation.
- **`GrRecordingContext`** is the public *threadable* context. It can
  build `SkImage`s, `SkSurface` characterizations, and `SkDeferredDisplayList`s
  but **cannot flush**. It owns the proxy provider, the audit trail,
  the recording-side arena, and the text-blob redraw coordinator.
- **`GrDirectContext`** (`include/gpu/ganesh/GrDirectContext.h`)
  derives from `GrRecordingContext` and adds GPU ownership: a `GrGpu`,
  the `GrResourceCache`, the `GrAtlasManager`, the strike cache, and
  the only methods that actually submit work — `flush(...)`,
  `submit(...)`, `wait(semaphores...)`, `performDeferredCleanup(...)`,
  plus the VRAM accounting calls (`getResourceCacheLimit`,
  `setResourceCacheLimit`).

`GrDirectContext` factories are spelt as `GrDirectContexts::MakeGL`,
`MakeMetal`, `MakeVulkan`, `MakeDirect3D`, `MakeDawn`, and (for tests)
`GrDirectContext::MakeMock`. Once created the context is single-threaded
with respect to GPU calls — the `GrContextThreadSafeProxy` it hands out
is what other threads use to participate in `SkDeferredDisplayList`
recording or surface characterization without owning the GPU.

`GrDirectContext::abandonContext()` puts the context into a permanent
"ignore all further calls" state; used after a `VK_ERROR_DEVICE_LOST`,
a tab-discard in Chrome, or an Android EGL surface loss.

## Device and SurfaceDrawContext

`skgpu::ganesh::Device` (`Device.{h,cpp}`) is the `SkDevice` subclass
that turns canvas calls into render-target drawing. It owns a
`SurfaceDrawContext` (analogous to a "render-target view") through
which it issues every draw. The split exists because some callers
(e.g. image filters) need a fill-only surface — for those there is
`SurfaceFillContext`; both inherit from `SurfaceContext` which knows
how to read pixels back, copy from another surface, and clear.

`SurfaceDrawContext` is the choke-point for converting
(`SkPaint`, geometry, matrix, clip) into one or more `GrOp`s. It picks
the right `GrPathRenderer` for a path, splits an oversized image into
tiles via [`TiledTextureUtils`](gpu-overview.md#smaller-helpers),
constructs the `GrFragmentProcessor` tree from the paint's shader /
color filter / image filter (using `GrFragmentProcessors::Make...`),
applies the clip stack, and finally hands a populated `GrOp` to the
current `OpsTask`.

## Render tasks and OpsTask

`GrRenderTask` (`GrRenderTask.{h,cpp}`) is the DAG node managed by
`GrDrawingManager`. Each task targets one `GrSurfaceProxy` and tracks
its set of read/written proxies so the manager can topologically sort
tasks at flush time and elide texture barriers where possible.
Concrete subclasses include:

- **`OpsTask`** — the workhorse: an ordered list of `GrOp` chains all
  drawing into the same render target (one per render pass).
- **`GrCopyRenderTask`** — surface-to-surface copy, with optional
  format conversion.
- **`GrBufferTransferRenderTask`** / **`GrBufferUpdateRenderTask`** —
  CPU↔GPU buffer transfers and inline updates.
- **`AtlasRenderTask`** (in `ops/`) — renders into a dynamic atlas page
  shared by many ops.
- **`GrDDLTask`** — the replay node produced when an `SkDeferredDisplayList`
  is dropped onto a surface.

`OpsTask::onPrepare` walks every chain, lets each op build its vertex
buffer (via `GrBufferAllocPool`), assembles the `GrPipeline` /
`GrProgramInfo` describing color/coverage processors, and links the
GLSL program. `onExecute` opens a `GrOpsRenderPass` (load/store the
target, set viewport/scissor/blend) and asks each op to emit its
draw calls.

## The op pipeline

`GrOp` (`ops/GrOp.h`) is the base of every deferred GPU operation.
The header comment summarises the contract:

> Ganesh does not generate geometry inline with draw calls. Instead,
> it captures the arguments to the draw and then generates the
> geometry when flushing. This gives `GrOp` subclasses complete
> freedom to decide how/when to combine in order to produce fewer
> draw calls and minimize state changes.

Every op carries a class ID (`DEFINE_OP_CLASS_ID`), device-space
bounds (which the manager uses for clip elision), and three virtuals
the framework drives:

- **`combineIfPossible(GrOp*, ...)`** — try to merge this op with
  another in front of us in the chain, or chain head-to-tail. Merging
  unions data; chaining keeps separate data but a single program
  binding.
- **`onPrepare(GrOpFlushState*)`** — allocate vertex/index/uniform
  buffers, build the `GrProgramInfo`, request a GLSL program.
- **`onExecute(GrOpFlushState*, const SkRect& chainBounds)`** — issue
  the draw call(s) on the current `GrOpsRenderPass`.

Most concrete ops derive from `GrMeshDrawOp` (`ops/GrMeshDrawOp.h`),
which provides shared bookkeeping for meshes and the
`GrSimpleMeshDrawOpHelper(WithStencil)` mixins. Notable op classes:

- `FillRectOp`, `FillRRectOp` — solid fills with per-edge AA.
- `PathTessellateOp`, `PathInnerTriangulateOp`, `PathStencilCoverOp` —
  path fill via the [shared GPU tessellator](gpu-tessellation.md).
- `AtlasTextOp` — glyph runs sourced from the strike-cache atlas.
- `DashOp`, `DrawAtlasOp`, `LatticeOp`, `DrawMeshOp`, `DrawableOp`,
  `ClearOp`, `DrawAtlasPathOp` — specialised paths from `SkCanvas`
  primitives.

Color and coverage are themselves modular. A `GrPaint` carries a
`GrProcessorSet`, which is a tree of `GrFragmentProcessor`s for color
plus another tree for coverage, and a single `GrXferProcessor` (chosen
via `BlendFormula` from [GPU Overview](gpu-overview.md)). The
`GrGeometryProcessor` paired with the op generates the vertex shader
and the per-vertex attributes. Together (`GP, FPs, XP`) they form the
shader program; a `GrPipeline` packages them with render-state
(blend mode, write mask, scissor).

## Clip stack

`ClipStack` (`ClipStack.{h,cpp}`) is the GPU implementation of the
canvas clip. It tracks rectangle / RRect / path / shader clip
elements with their `SkClipOp`. At each draw, `apply(...)` returns a
`GrAppliedClip` describing what the op must do: scissor, window
rectangles, an AA coverage `GrFragmentProcessor` ("analytic clip"), or
a stencil pass plus stencil-test render state. Software-rendered masks
fall through `GrSWMaskHelper` to a CPU-rasterised alpha image uploaded
into a coverage atlas.

## GLSL program builder

Ganesh emits GLSL (and via [`SkSLToBackend`](gpu-overview.md#sksl--backend--srcgpusksltobackendh)
also SPIR-V, MSL, HLSL, and WGSL) through `GrGLSLProgramBuilder`
(`glsl/GrGLSLProgramBuilder.{h,cpp}`). The builder walks the
`GrProgramInfo` for a draw and:

1. Creates a `GrGLSLVertexBuilder` and `GrGLSLFragmentShaderBuilder`,
   plus a `GrGLSLUniformHandler` and `GrGLSLVaryingHandler` that
   assign mangled, stage-prefixed names.
2. Asks the `GrGeometryProcessor` to emit its vertex code and any
   varyings to the fragment stage.
3. Walks the color and coverage `GrFragmentProcessor` trees, calling
   each one's `ProgramImpl::emitCode` to splice in its SkSL snippet.
   `emitTextureSamplersForFPs` allocates per-FP samplers; the swizzle
   for each sampler is provided by the per-API uniform handler.
4. Asks the `GrXferProcessor` to write the final blend / dual-source
   output (using `GrGLSLBlend` for SkSL blend funcs and the
   `BlendFormula` lookup).
5. Hands the finished `(vsh, fsh)` strings to the per-API
   `GrGLSLProgramBuilder` subclass (e.g. `GrGLProgramBuilder` in
   `src/gpu/ganesh/gl/builders/`), which compiles them and returns a
   `GrProgram` cached against a `GrProgramDesc` key.

`GrGLSLProgramDataManager` holds the link between a uniform handle and
its current backend binding so per-frame `set...()` calls don't have to
re-look-up locations.

## Atlases, paths, and other subsystems

- **Glyph atlas** — `GrAtlasManager` (driven from
  `sktext::gpu::SubRunContainer` in `src/text/gpu/`) keeps three pages
  per `MaskFormat` (A8, A565, ARGB; see [GPU Overview](gpu-overview.md)).
  `AtlasTextOp` and `AtlasInstancedHelper` issue instanced draws over
  the resulting plots; eviction is keyed on flush `Token`s.
- **Path atlases** — `AtlasPathRenderer` rasterises arbitrary paths
  into a coverage atlas via `AtlasRenderTask`, then re-uses them
  across draws. `SmallPathRenderer` (when `SK_ENABLE_OPTIMIZE_SIZE`
  is off) does the same for small repeated paths via
  `SmallPathAtlasMgr`.
- **Path renderers** — `GrPathRenderer` is the abstract chooser; the
  concrete classes in `ops/` cover convex AA, hairlines, dashed lines,
  GPU tessellation, and the CPU fallback `DefaultPathRenderer` that
  uploads a `SkRasterPipeline` mask through `GrSWMaskHelper`. The
  tessellator itself is shared with Graphite — see
  [GPU Tessellation](gpu-tessellation.md).
- **Image and gradient FPs** — `GrFragmentProcessors::Make...` in
  `GrFragmentProcessors.{h,cpp}` lifts each `SkShader` /
  `SkColorFilter` / `SkImageFilter` subclass into a corresponding
  `GrFragmentProcessor`. Built-ins live in
  `src/gpu/ganesh/effects/` (e.g. `GrTextureEffect`,
  `GrYUVtoRGBEffect`, `GrGaussianConvolutionFragmentProcessor`,
  `GrBicubicEffect`).
- **Deferred display lists** — `GrDDLContext`,
  `GrDeferredDisplayList(Recorder)`, and `GrDDLTask` let a thread
  record draws against a surface characterization on one thread, then
  replay them onto a real surface later. Used heavily by Chrome's
  raster scheduler.

## Per-API ports

The ports under `src/gpu/ganesh/gl/`, `vk/`, `mtl/`, `d3d/`,
`mock/` provide concrete `GrGpu`, `GrCaps`, `GrGLSLProgramBuilder`,
`GrOpsRenderPass`, `GrGpuBuffer`, `GrTexture`/`GrAttachment` /
`GrRenderTarget` subclasses for each API. Each is documented in its
own file: [OpenGL](backend-opengl.md), [Vulkan](backend-vulkan.md),
[Metal](backend-metal.md), [Direct3D](backend-direct3d.md),
[Dawn / WebGPU](backend-dawn.md). They all share the cross-cutting
infrastructure described in [GPU Overview](gpu-overview.md), feed SkSL
through the same `SkSLToBackend` shim ([SkSL](sksl-shading-language.md)),
and use the same path tessellator ([GPU Tessellation](gpu-tessellation.md)).
