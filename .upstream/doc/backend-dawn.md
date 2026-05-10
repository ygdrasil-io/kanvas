# Dawn / WebGPU Backend

Dawn is Skia's path to **WebGPU**. The Dawn backend is **Graphite-only**:
Skia's older Ganesh renderer ([Ganesh Backend](ganesh-backend.md)) does
not target WebGPU, so all Dawn code lives under
`skia-main/src/gpu/graphite/dawn/` with public headers in
`include/gpu/graphite/dawn/`. See [Graphite Backend](graphite-backend.md)
for the rendering architecture this plugs into and
[GPU Overview](gpu-overview.md) for how the per-API backends compare.
The Dawn path is also the GPU path used by [CanvasKit](canvaskit.md)
when it is built against WebGPU rather than WebGL.

A few things make this backend distinct from the Vulkan / Metal / D3D
ones: WebGPU is an **async API by design** (no synchronous pipeline
creation, queue completion, or error reporting); there are two compile
targets — **Dawn native** (desktop / Android, via the `wgpu::` C++
wrapper around `webgpu.h`) and **Emscripten** (browser WebGPU via
WASM), with `#if defined(__EMSCRIPTEN__)` guards for the differences;
and shaders are emitted as **WGSL** via Skia's SkSL → WGSL backend
([SkSL Shading Language](sksl-shading-language.md)).

## Map

| Area | Location |
|------|----------|
| Public Dawn / WebGPU headers | `skia-main/include/gpu/graphite/dawn/` |
| `DawnBackendContext` | `include/gpu/graphite/dawn/DawnBackendContext.h` |
| `DawnTextureInfo` & `BackendTextures::MakeDawn` | `include/gpu/graphite/dawn/DawnGraphiteTypes.h` |
| Format / type helpers | `include/gpu/graphite/dawn/DawnTypes.h`, `DawnUtils.h` |
| Implementation | `skia-main/src/gpu/graphite/dawn/` (`Dawn*`) |
| Per-`Context` shared state | `DawnSharedContext.{h,cpp}` |
| Capabilities / format probing | `DawnCaps.{h,cpp}` |
| Queue / submission | `DawnQueueManager.{h,cpp}`, `DawnCommandBuffer.{h,cpp}` |
| Resource provider | `DawnResourceProvider.{h,cpp}` |
| Resource wrappers | `DawnBuffer`, `DawnTexture`, `DawnSampler`, `DawnBackendTexture` |
| Pipelines | `DawnGraphicsPipeline.{h,cpp}`, `DawnComputePipeline.{h,cpp}` |
| SkSL → WGSL helper | `DawnGraphiteUtils.{h,cpp}` |
| Async future helper | `DawnAsyncWait.{h,cpp}` |
| Error scope helper | `DawnErrorChecker.{h,cpp}` |

## What the client supplies

`include/gpu/graphite/dawn/DawnBackendContext.h` is what the client
hands to the Graphite `Context` factory:

```cpp
struct SK_API DawnBackendContext {
    wgpu::Instance fInstance;
    wgpu::Device   fDevice;
    wgpu::Queue    fQueue;
    DawnTickFunction* fTick = ...;  // see below
};

namespace ContextFactory {
SK_API std::unique_ptr<Context> MakeDawn(const DawnBackendContext&,
                                         const ContextOptions&);
}
```

`wgpu::Instance`, `wgpu::Device`, and `wgpu::Queue` are the standard
WebGPU C++ wrapper types from `<webgpu/webgpu_cpp.h>`. The client owns
all three; Skia just borrows them, the same model used by
[Vulkan](backend-vulkan.md) and [Metal](backend-metal.md). On native
Dawn the `wgpu::Instance` exists; on Emscripten it is implicit.

### Yielding vs non-yielding contexts — `DawnTickFunction`

WebGPU is fundamentally async: callbacks for buffer mapping, pipeline
compilation, and submission completion only fire once the implementation
is allowed to run. Skia therefore needs a hook to "tick" the WebGPU
implementation forward. That hook is `DawnTickFunction`:

```cpp
using DawnTickFunction = void(const wgpu::Instance& device);
```

Two policies are supported:

- **Yielding context (default on Dawn native)** — the tick function
  calls `wgpu::Instance::ProcessEvents()`, which Dawn native exposes but
  WebGPU-in-the-browser does not. Graphite installs
  `DawnNativeProcessEventsFunction` for you. The context can call
  `Context::submit(SyncToCpu::kYes)` and block in its destructor until
  GPU work completes.
- **Non-yielding context (browser / WebGPU)** — the client passes
  `fTick = nullptr`. `SyncToCpu::kYes` is then disallowed, and the
  client is responsible for keeping the `Context` alive until
  `Context::hasUnfinishedGpuWork()` returns false. This makes it
  possible to build Graphite/Dawn for WebGPU without Emscripten's
  `-s ASYNCIFY`, at the cost of restricting the API.

The header comment on `DawnBackendContext.h` walks through both cases
(including an Emscripten `EM_ASYNC_JS`-based tick function for clients
that *do* opt into ASYNCIFY).

## `DawnSharedContext` — the per-`Context` core

`DawnSharedContext` (`src/gpu/graphite/dawn/DawnSharedContext.{h,cpp}`)
is Graphite's central per-`Context` object for the Dawn backend. It
holds the borrowed `wgpu::Instance` / `wgpu::Device` / `wgpu::Queue`,
the `DawnCaps`, the optional `DawnTickFunction`, and pre-built objects
that every recording will need:

- A no-op fragment `wgpu::ShaderModule` used for depth-only / stencil-only
  passes
- The two `wgpu::BindGroupLayout`s Graphite always binds — one for
  uniform buffers, one for the single texture+sampler pair — so every
  pipeline can be built against a stable layout
- The `DawnThreadSafeResourceProvider` shared across recorders

`createGraphicsPipeline` is the override that turns a Graphite
`GraphicsPipelineDesc` into a `DawnGraphicsPipeline`, going through
SkSL → WGSL → `wgpu::ShaderModule` → `wgpu::RenderPipeline`.

## Pipelines and shaders — SkSL → WGSL

Graphite emits its shaders in SkSL ([SkSL](sksl-shading-language.md))
and uses `SkSLToWGSL` (declared in `DawnGraphiteUtils.h`) to translate
them through the SkSL WGSL code generator
(`src/sksl/codegen/SkSLWGSLCodeGenerator.h`). The pipeline path is:

1. `DawnGraphicsPipeline::Make` receives a `GraphicsPipelineDesc` and a
   `RenderPassDesc` from Graphite's pipeline cache.
2. SkSL is generated for the vertex and fragment stages and translated
   to WGSL via `skgpu::SkSLToWGSL`.
3. `DawnCompileWGSLShaderModule` calls
   `wgpu::Device::CreateShaderModule`, asynchronously checking for
   compilation errors via the helper in `DawnErrorChecker`.
4. A `wgpu::RenderPipelineDescriptor` is built (vertex layout, blend,
   depth-stencil, primitive state, MSAA settings) and handed to
   `wgpu::Device::CreateRenderPipeline` (or its async variant).

`DawnComputePipeline` is the same flow for compute passes (compute
shaders also translated to WGSL).

## Resources, queue, and submission

`DawnQueueManager` and `DawnCommandBuffer` together form the Graphite
`QueueManager` / `CommandBuffer` implementation. A submission packages
recorded `wgpu::CommandBuffer`s and calls
`wgpu::Queue::Submit`. Completion is observed asynchronously via
`wgpu::Queue::OnSubmittedWorkDone`, with the result delivered through a
`DawnAsyncWait` future — the same primitive used to await async
buffer-map and pipeline-compilation callbacks. Yielding contexts call
`DawnSharedContext::tick()` from `deviceTick` to drive these callbacks
forward.

`DawnResourceProvider` caches the per-recorder objects: bind-group
layouts, sampler objects, intermediate render targets, the WGSL
shader-module cache, and the pipeline cache keyed on
`GraphicsPipelineDesc + RenderPassDesc`. `DawnBuffer`, `DawnTexture`,
and `DawnSampler` wrap `wgpu::Buffer`, `wgpu::Texture`, and
`wgpu::Sampler` and implement Graphite's resource-management contract,
including async buffer mapping for readback / upload.

## Capabilities — `DawnCaps`

`DawnCaps` queries the `wgpu::Adapter` / `wgpu::Device` for supported
features, limits (max texture size, max bind groups, max storage buffer
binding size, etc.), MSAA support per format, and which Dawn-only
extensions are available (e.g. `wgpu::FeatureName::TimestampQuery`,
shader-f16, dual-source blending, the YCbCr-VK descriptor used on
Android when Dawn is layered over Vulkan). Graphite consults these caps
when building pipelines and choosing texture formats. The
`DawnFormatFlag` bitmask in `DawnGraphiteUtils.h` mirrors the columns of
the WebGPU spec's "Texture Format Capabilities" table — Filter, Render,
Blend, MSAA, Resolve, Storage R/W/RW — so the rest of Graphite can ask
"does this format support `TextureUsage::RenderAttachment`?" without
re-deriving it from raw `wgpu::TextureFormat`.

## Backend interop

Clients wrap an existing `wgpu::Texture` (or `wgpu::TextureView`) as a
Graphite `BackendTexture` via the factories in
`DawnGraphiteTypes.h`:

```cpp
namespace BackendTextures {
SK_API BackendTexture MakeDawn(WGPUTexture);
SK_API BackendTexture MakeDawn(SkISize planeDimensions,
                               const DawnTextureInfo&, WGPUTexture);
SK_API BackendTexture MakeDawn(SkISize dimensions,
                               const DawnTextureInfo& info,
                               WGPUTextureView textureView);
}
```

The `WGPUTexture` flavour is preferred because Skia can do efficient
buffer ↔ texture transfers; the `WGPUTextureView` flavour exists
specifically for `wgpu::SwapChain`, which only exposes views, and forces
an intermediate copy on `readPixels` / `writePixels`. Skia does **not**
take ownership: the client must keep the `WGPUTexture` /
`WGPUTextureView` alive for as long as any wrapping `SkImage` or
`SkSurface` is in use.

`DawnTextureInfo` carries the `wgpu::TextureFormat`, view format, usage,
aspect, and array slice that Graphite needs to bind the resource.
On Android (Dawn-on-Vulkan), an optional `wgpu::YCbCrVkDescriptor` lets
external multi-planar Vulkan images flow through unchanged — the only
place external-format / YCbCr conversion shows up in the Dawn backend.

## Where to look next

- [Graphite Backend](graphite-backend.md) — the renderer the Dawn
  backend plugs into; **WebGPU is Graphite-only**, Ganesh does not
  target it
- [GPU Overview](gpu-overview.md) — how Ganesh / Graphite and the
  individual backends relate
- [SkSL Shading Language](sksl-shading-language.md) — the SkSL → WGSL
  pipeline that produces every shader the Dawn backend uses
- [CanvasKit](canvaskit.md) — the WASM packaging that uses Dawn as its
  WebGPU GPU path in the browser
