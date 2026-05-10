# Metal Backend

Apple's Metal API is supported by both Skia GPU renderers on macOS and
iOS / iPadOS / tvOS / visionOS. The Ganesh implementation lives under
`skia-main/src/gpu/ganesh/mtl/` with public headers in
`include/gpu/ganesh/mtl/`; the Graphite implementation under
`skia-main/src/gpu/graphite/mtl/` with public headers in
`include/gpu/graphite/mtl/`.

Most Metal source files have the `.mm` extension because they are
**Objective-C++**: Skia's C++ types interleave with the native
`MTL...` Objective-C protocols (`id<MTLDevice>`, `id<MTLCommandQueue>`,
`id<MTLRenderCommandEncoder>` …), and Apple's headers can only be
imported from an Objective-C compilation unit. Public headers stay
plain C++ and pass Metal objects across the boundary as `CFTypeRef` /
`sk_cfp<CFTypeRef>` so non-ObjC clients can include them.

For the renderers Metal plugs into, see
[Ganesh Backend](ganesh-backend.md) and
[Graphite Backend](graphite-backend.md); for SkSL → MSL emission
([SkSL Shading Language](sksl-shading-language.md)); for the
macOS / iOS font hosts (CoreText) and the rest of the platform
integration on Apple OSes, see [Platform Ports](platform-ports.md).

## Map

| Area | Location |
|------|----------|
| Ganesh public Metal headers | `skia-main/include/gpu/ganesh/mtl/` |
| `GrMtlBackendContext`, `GrMtlDirectContext`, types | `include/gpu/ganesh/mtl/GrMtl*.h`, `SkSurfaceMetal.h` |
| Ganesh Metal implementation (ObjC++) | `skia-main/src/gpu/ganesh/mtl/` (`GrMtl*.{h,mm}`) |
| Ganesh Metal `GrGpu` | `src/gpu/ganesh/mtl/GrMtlGpu.{h,mm}` |
| Pipeline / state caching | `GrMtlPipelineStateBuilder`, `GrMtlPipelineStateDataManager`, `GrMtlResourceProvider` |
| Render-pass encoding | `GrMtlOpsRenderPass`, `GrMtlCommandBuffer`, `GrMtlRenderCommandEncoder.h` |
| Pure-C++ / ObjC bridge | `GrMtlCppUtil.h`, `GrMtlTrampoline.{h,mm}` |
| Graphite public Metal headers | `skia-main/include/gpu/graphite/mtl/` |
| `MtlBackendContext` (Graphite) | `include/gpu/graphite/mtl/MtlBackendContext.h` |
| Graphite Metal implementation | `skia-main/src/gpu/graphite/mtl/` |

## What the client supplies

Both backends take only the device and the command queue from the
client; everything else is built internally.

### Ganesh — `GrMtlBackendContext`

`include/gpu/ganesh/mtl/GrMtlBackendContext.h` carries
`sk_cfp<CFTypeRef> fDevice` and `sk_cfp<CFTypeRef> fQueue`, which are
strong references to a `id<MTLDevice>` and `id<MTLCommandQueue>`. The
factory is `GrDirectContexts::MakeMetal(backendContext, options)` from
`include/gpu/ganesh/mtl/GrMtlDirectContext.h`. From there the rest of
the Ganesh API behaves identically to other backends — see
[Ganesh Backend](ganesh-backend.md).

### Graphite — `MtlBackendContext`

The Graphite struct is even simpler:

```cpp
struct MtlBackendContext {
    sk_cfp<CFTypeRef> fDevice;
    sk_cfp<CFTypeRef> fQueue;
};

namespace ContextFactory {
  std::unique_ptr<Context> MakeMetal(const MtlBackendContext&,
                                     const ContextOptions&);
}
```

Graphite then drives the queue from a `Recorder` per thread and submits
batched recordings — see [Graphite Backend](graphite-backend.md).

## Ganesh — `src/gpu/ganesh/mtl/`

`GrMtlGpu` owns:

- the `id<MTLDevice>` and `id<MTLCommandQueue>` borrowed from the client
- a `GrMtlCommandBuffer` wrapping `id<MTLCommandBuffer>`, recreated each
  flush
- a `GrMtlResourceProvider` caching `MTLRenderPipelineState`,
  `MTLDepthStencilState`, samplers and small uniform buffers
- per-resource subclasses: `GrMtlBuffer`, `GrMtlTexture`,
  `GrMtlTextureRenderTarget`, `GrMtlAttachment`, `GrMtlSampler`,
  `GrMtlSemaphore`

Pipeline objects are built lazily by `GrMtlPipelineStateBuilder`, which
runs the SkSL → MSL transpiler ([SkSL](sksl-shading-language.md)) and
hands the resulting source to `MTLLibrary` for compilation. The
resulting `MTLRenderPipelineState` is keyed and cached so subsequent
draws with the same pipeline configuration reuse it.

Draws are recorded by `GrMtlOpsRenderPass` into a
`MTLRenderCommandEncoder`. `GrMtlAttachment` plus `GrMtlFramebuffer`
manage the render target, depth / stencil, and MSAA resolve. The
`SkSurfaces::WrapMTKView` / `WrapCAMetalLayer` family in
`include/gpu/ganesh/mtl/SkSurfaceMetal.h` wraps the system-supplied
`CAMetalDrawable` as an `SkSurface` for one frame.

`GrMtlCppUtil.h` and `GrMtlTrampoline.{h,mm}` are the pure-C++ bridge
through which non-ObjC translation units can request a Metal context
without including Apple's headers.

## Graphite — `src/gpu/graphite/mtl/`

The Graphite Metal port maps Graphite's renderer abstractions onto Metal:

- `MtlSharedContext` — owns the device + caps + capabilities probing
- `MtlQueueManager` — pushes recorded `MtlCommandBuffer`s into the
  `id<MTLCommandQueue>`
- `MtlResourceProvider` — caches `MtlBuffer`, `MtlTexture`,
  `MtlSampler`, `MtlGraphicsPipeline`, `MtlComputePipeline`
- `MtlCommandBuffer` — wraps `id<MTLCommandBuffer>` and dispatches
  render / blit / compute encoders
- `MtlRenderCommandEncoder.h`, `MtlBlitCommandEncoder.h`,
  `MtlComputeCommandEncoder.h` — small adapters

Graphics pipelines come from `MtlGraphicsPipeline`, built from SkSL
emitted as MSL by the SkSL backend; compute pipelines from
`MtlComputePipeline` (Graphite uses compute for some atlas / gradient /
tessellation work).

Public Graphite headers in `include/gpu/graphite/mtl/`:
`MtlBackendContext.h`, `MtlGraphiteTypes.h` (and its `_cpp.h` /
`Utils.h` companions), `MtlGraphiteUtils.h`.

## Capabilities and shader compilation

Both `GrMtlCaps` (Ganesh) and `MtlCaps` (Graphite) probe the
`id<MTLDevice>` for its `MTLGPUFamily`, MSAA limits, supported pixel
formats, argument-buffer tier, raster-order-group support, etc., and
expose those as the `Caps` Skia consults at draw time. The
[SkSL](sksl-shading-language.md) compiler emits MSL targeting the
versions supported by the OS Skia is built for; the resulting source is
fed to `[MTLDevice newLibraryWithSource:options:error:]` and the
returned function objects are wrapped as Metal pipeline-state stages.

## Backend interop and semaphores

A client wraps an existing `id<MTLTexture>` as a `BackendTexture` /
`BackendRenderTarget` via `GrBackendTextures::MakeMtl(...)` /
`MtlBackendTexture` and promotes it to an `SkSurface` via
[Surface & Output](surface-and-output.md). On macOS, an `IOSurface` can
travel between processes by being wrapped on each side as an
`MTLTexture` — Skia just sees the `id<MTLTexture>`.

`GrMtlBackendSemaphore` (Ganesh) and `MtlBackendSemaphore.mm` (Graphite)
wrap an `id<MTLSharedEvent>` so a client can synchronise Skia work
against other Metal queues, against Vulkan via MoltenVK, or against
display delivery. The semaphore is signalled / waited on submission,
in the same pattern the Vulkan backend uses (see
[Vulkan Backend](backend-vulkan.md)).

## Where to look next

- [Ganesh Backend](ganesh-backend.md) and [Graphite Backend](graphite-backend.md)
  — the two renderers Metal plugs into
- [GPU Overview](gpu-overview.md) — Ganesh vs Graphite at a glance
- [SkSL Shading Language](sksl-shading-language.md) — the SkSL → MSL
  pipeline that drives Metal pipeline-state creation
- [Platform Ports](platform-ports.md) — CoreText font host, CoreGraphics
  helpers, and other macOS / iOS-specific code
- [Surface & Output](surface-and-output.md) — wrapping a
  `CAMetalDrawable` / `MTKView` / `id<MTLTexture>` as an `SkSurface`
