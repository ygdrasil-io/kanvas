# Direct3D Backend

The Direct3D 12 backend is **Ganesh-only**: Skia's Graphite renderer
([Graphite Backend](graphite-backend.md)) does not target D3D12. All
Direct3D code therefore lives under `skia-main/src/gpu/ganesh/d3d/` with
public headers in `include/gpu/ganesh/d3d/`. There is no shared `gpu/d3d/`
tree because no second renderer needs to share with it. For the rendering
architecture this plugs into see [Ganesh Backend](ganesh-backend.md), and
[GPU Overview](gpu-overview.md) for how it relates to the other backends.

## Map

| Area | Location |
|------|----------|
| Public Direct3D headers | `skia-main/include/gpu/ganesh/d3d/` |
| `GrD3DBackendContext` | `include/gpu/ganesh/d3d/GrD3DBackendContext.h` |
| Direct-context entry point | `include/gpu/ganesh/d3d/GrD3DDirectContext.h` |
| Backend-surface accessors | `include/gpu/ganesh/d3d/GrD3DBackendSurface.h` |
| Cross-API semaphore wrapper | `include/gpu/ganesh/d3d/GrD3DBackendSemaphore.h` |
| D3D types (DXGI / D3D12 forwards) | `include/gpu/ganesh/d3d/GrD3DTypes.h` |
| Implementation | `skia-main/src/gpu/ganesh/d3d/` (`GrD3D*`) |
| `GrGpu` subclass | `src/gpu/ganesh/d3d/GrD3DGpu.{h,cpp}` |
| AMD D3D12 memory allocator wrapper | `src/gpu/ganesh/d3d/GrD3DAMDMemoryAllocator.{h,cpp}` |
| Capabilities / format probing | `src/gpu/ganesh/d3d/GrD3DCaps.{h,cpp}` |
| Command-list recording | `src/gpu/ganesh/d3d/GrD3DCommandList.{h,cpp}`, `GrD3DOpsRenderPass.{h,cpp}` |
| Descriptor heaps & tables | `src/gpu/ganesh/d3d/GrD3DDescriptorHeap.{h,cpp}`, `GrD3DDescriptorTableManager.{h,cpp}`, `GrD3DCpuDescriptorManager.{h,cpp}` |
| Root signatures | `src/gpu/ganesh/d3d/GrD3DRootSignature.{h,cpp}` |
| Pipeline state & cache | `GrD3DPipelineStateBuilder`, `GrD3DPipelineState`, `GrD3DPipelineStateDataManager`, `GrD3DResourceProvider` |

## What the client supplies

`include/gpu/ganesh/d3d/GrD3DBackendContext.h` is what the client builds
on Skia's behalf:

```cpp
struct GrD3DBackendContext {
    gr_cp<IDXGIAdapter1>         fAdapter;
    gr_cp<ID3D12Device>          fDevice;
    gr_cp<ID3D12CommandQueue>    fQueue;
    sk_sp<GrD3DMemoryAllocator>  fMemoryAllocator;
    GrProtected                  fProtectedContext = GrProtected::kNo;
};
```

`gr_cp<...>` is Skia's COM smart pointer for `IUnknown`-derived
interfaces. The client owns the adapter, device and command queue;
Skia just borrows them. The factory is
`GrDirectContexts::MakeDirect3D(backendContext, options)` from
`GrD3DDirectContext.h`. The header `GrD3DBackendContext.h` warns up
front that including `d3d12.h` (transitively via `GrD3DTypes.h`) will
pull in `windows.h`, which redefines a number of common identifiers
(`interface`, `small`, `near`, `far`, `CreateSemaphore`,
`MemoryBarrier`, etc.) — clients must be prepared for that.

## Memory allocation

D3D12 does not provide a built-in suballocator, so every D3D12 client
needs one. Skia's interface is `GrD3DMemoryAllocator`, declared in
`GrD3DTypes.h` and stored on the `GrD3DBackendContext`. The default
implementation Skia ships, `GrD3DAMDMemoryAllocator`, wraps **AMD's
D3D12MemoryAllocator (D3D12MA)** library — D3D12MA picks heaps, places
resources, and returns aligned `ID3D12Resource` handles. Clients that
already integrate D3D12MA (or another allocator) plug in their own
implementation by subclassing `GrD3DMemoryAllocator`. The structure
mirrors the Vulkan side ([Vulkan Backend](backend-vulkan.md)).

## `GrD3DGpu` — the work horse

`GrD3DGpu` (`src/gpu/ganesh/d3d/GrD3DGpu.{h,cpp}`) is the D3D12
implementation of `GrGpu`. It owns:

- The borrowed `ID3D12Device`, `ID3D12CommandQueue`, and adapter
- A `GrD3DResourceProvider` that caches pipeline-state objects,
  root signatures, command-allocator pools, and descriptor tables
- A `GrD3DCommandList` (wrapping `ID3D12GraphicsCommandList`) that is
  reset per submission
- Resource subclasses: `GrD3DBuffer`, `GrD3DTexture`,
  `GrD3DTextureRenderTarget`, `GrD3DAttachment`, `GrD3DSemaphore`
- A `GrD3DResourceState` per texture, tracking the
  `D3D12_RESOURCE_STATES` for the next barrier issue

`GrD3DOpsRenderPass` records draw commands into the active
`ID3D12GraphicsCommandList`. Render-target binding goes through RTV /
DSV CPU descriptor heaps managed by `GrD3DCpuDescriptorManager`;
shader-visible bindings go through GPU descriptor heaps managed by
`GrD3DDescriptorHeap` / `GrD3DDescriptorTableManager`. Indirect-draw
support uses cached `GrD3DCommandSignature` instances.

## Pipeline state and shaders

D3D12 requires a fully baked **Pipeline State Object (PSO)** plus a
**root signature** per draw configuration. `GrD3DRootSignature` builds
the root signature describing root constants, root descriptor tables and
samplers Skia uses. `GrD3DPipelineStateBuilder` then:

1. Compiles the SkSL source ([SkSL Shading Language](sksl-shading-language.md))
   to **HLSL** through Skia's SkSL → HLSL backend.
2. Hands the HLSL to the DirectX shader compiler (DXC / FXC depending on
   the build) to produce DXIL / DXBC bytecode.
3. Calls `ID3D12Device::CreateGraphicsPipelineState` with that bytecode,
   the root signature, and the input layout / blend / depth-stencil /
   raster state derived from the Ganesh draw configuration.

The resulting `GrD3DPipelineState` is cached in
`GrD3DResourceProvider`. `GrD3DPipelineStateDataManager` writes uniforms
into the upload heap `ID3D12Resource`s the PSO references through the
root signature.

## Descriptor heaps

D3D12 requires that all shader-visible descriptors live in two big
heaps (CBV/SRV/UAV and SAMPLER). Skia's `GrD3DDescriptorHeap` allocates
ranges out of those, and `GrD3DDescriptorTableManager` packs the
per-draw descriptors that get bound into the root signature each draw.
Non-shader-visible "CPU" descriptors (RTVs, DSVs) are managed
independently by `GrD3DCpuDescriptorManager`.

## Capabilities — `GrD3DCaps`

`GrD3DCaps` queries the `ID3D12Device` for feature levels, supported
DXGI formats, MSAA sample counts, root-signature version, sampler
feedback, mesh-shader support, etc. Like the corresponding GL / VK caps
files, this is where format swizzles and per-vendor workarounds are
encoded.

## Backend interop and semaphores

`GrD3DBackendSurface.h` lets a client wrap an existing
`ID3D12Resource` as a `GrBackendTexture` / `GrBackendRenderTarget`,
typically backed by a swapchain buffer the client retrieved from
`IDXGISwapChain3::GetBuffer`. The wrapped resource carries a
`D3D12_RESOURCE_STATES` so Skia knows what state it is in on entry and
what state the client expects on exit; the equivalent of Vulkan's
`MutableTextureState` ([Vulkan Backend](backend-vulkan.md)) but
expressed in D3D12 terms.

`GrD3DBackendSemaphore.h` plus `GrD3DSemaphore` wrap an `ID3D12Fence`
plus a 64-bit fence value. A client passes one as a wait semaphore for
Skia to wait on before issuing work, or as a signal semaphore Skia will
signal at the end of submission — useful for coordinating with other
queues, with the swapchain's `Present`, or with cross-API interop using
shared `D3D12_FENCE_FLAG_SHARED_CROSS_ADAPTER` fences.

## Where to look next

- [Ganesh Backend](ganesh-backend.md) — the renderer the D3D12 backend
  plugs into; **note D3D12 is Ganesh-only**, Graphite does not target it
- [GPU Overview](gpu-overview.md) — how Ganesh / Graphite and the
  individual backends relate
- [SkSL Shading Language](sksl-shading-language.md) — the SkSL → HLSL →
  DXIL pipeline that produces every shader the D3D12 backend uses
- [Surface & Output](surface-and-output.md) — wrapping a swapchain
  `ID3D12Resource` as an `SkSurface`
