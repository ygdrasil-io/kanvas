# Vulkan Backend

Vulkan is the only graphics API that has a **first-class implementation
in both Skia GPU renderers**: Ganesh ([Ganesh Backend](ganesh-backend.md))
under `src/gpu/ganesh/vk/`, and Graphite
([Graphite Backend](graphite-backend.md)) under `src/gpu/graphite/vk/`.
Code that has to be shared between them — the function-pointer
interface, memory allocation, mutable image-state tracking, and the
preferred-features helper — lives in `src/gpu/vk/` with public headers
under `include/gpu/vk/`. See [GPU Overview](gpu-overview.md) for how the
two renderers compare, and [SkSL Shading Language](sksl-shading-language.md)
for the SkSL → SPIR-V path that both Vulkan backends drive.

## Map

| Area | Location |
|------|----------|
| Shared public headers | `skia-main/include/gpu/vk/` |
| `VulkanBackendContext` | `include/gpu/vk/VulkanBackendContext.h` |
| Memory-allocator interface | `include/gpu/vk/VulkanMemoryAllocator.h` |
| Extension and types | `include/gpu/vk/VulkanExtensions.h`, `VulkanTypes.h` |
| Mutable-state (layout / queue family) | `include/gpu/vk/VulkanMutableTextureState.h` |
| Shared implementation | `skia-main/src/gpu/vk/` |
| Function-pointer interface | `src/gpu/vk/VulkanInterface.{h,cpp}` |
| AMD VMA wrapper | `src/gpu/vk/vulkanmemoryallocator/` |
| Ganesh Vulkan | `skia-main/src/gpu/ganesh/vk/` (`GrVk*`) |
| Ganesh Vulkan public headers | `include/gpu/ganesh/vk/` |
| Graphite Vulkan | `skia-main/src/gpu/graphite/vk/` |
| Graphite Vulkan public headers | `include/gpu/graphite/vk/` |

## `VulkanBackendContext` — what the client supplies

Both renderers consume the same struct
(`include/gpu/vk/VulkanBackendContext.h`):

```cpp
struct VulkanBackendContext {
    VkInstance       fInstance;
    VkPhysicalDevice fPhysicalDevice;
    VkDevice         fDevice;
    VkQueue          fQueue;
    uint32_t         fGraphicsQueueIndex;
    uint32_t         fMaxAPIVersion;          // Skia requires Vulkan 1.1+
    const VulkanExtensions* fVkExtensions;
    const VkPhysicalDeviceFeatures*  fDeviceFeatures;
    const VkPhysicalDeviceFeatures2* fDeviceFeatures2;
    sk_sp<VulkanMemoryAllocator> fMemoryAllocator;
    VulkanGetProc    fGetProc;
    Protected        fProtectedContext;
    VulkanDeviceLostContext fDeviceLostContext;
    VulkanDeviceLostProc    fDeviceLostProc;
};
```

The client owns the instance, physical device, logical device, queue,
and the lifetime of all of them. Skia only borrows them. `fGetProc` is
the entry-point loader, typically `vkGetDeviceProcAddr` (with a fallback
to `vkGetInstanceProcAddr` for instance-level functions). `fVkExtensions`
declares which device extensions the client enabled, so Ganesh / Graphite
can avoid calling functions the driver did not load.

`VulkanPreferredFeatures` (`include/gpu/vk/VulkanPreferredFeatures.h`,
`src/gpu/vk/VulkanPreferredFeatures.cpp`) is a helper a client can use
during `VkDevice` creation: it inspects what the physical device
supports and returns the optional features Skia would benefit from
turning on (YCbCr conversion, shader-draw-parameters, sampler-Ycbcr,
dynamic rendering on Graphite, etc.).

## Shared infrastructure — `src/gpu/vk/`

`VulkanInterface` is a function-pointer table populated through
`fGetProc`. It is the Vulkan analogue of `GrGLInterface` (see
[OpenGL Backend](backend-opengl.md)) and its content is determined by
the `VulkanExtensions` set the client passed in. `VulkanUtilsPriv`
provides format-conversion helpers, image-aspect-mask logic,
ycbcr-conversion utilities and a `CallVulkanFunction` debug shim used
by both backends.

### Memory allocation

Both backends allocate `VkDeviceMemory` through the
`VulkanMemoryAllocator` interface
(`include/gpu/vk/VulkanMemoryAllocator.h`). It is a pure-virtual
strategy object that the client is required to supply on the
`VulkanBackendContext`. Skia ships a default implementation in
`src/gpu/vk/vulkanmemoryallocator/` that wraps AMD's
**VulkanMemoryAllocator (VMA)** library — VMA handles sub-allocation,
heap selection, defragmentation, and pool management; Skia just asks for
typed buffer / image allocations and returns them when finished. Clients
that already use VMA (or a different allocator) plug in their own
implementation instead.

### Mutable image state

Vulkan images carry per-subresource layout and queue-family ownership
that Skia does not see when a client wraps a foreign image. The
`VulkanMutableTextureState`
(`include/gpu/vk/VulkanMutableTextureState.h`,
`src/gpu/vk/VulkanMutableTextureState.cpp`) is a small handle that lets
the client and Skia agree on the *current* `VkImageLayout` and queue
family for a wrapped texture, before and after Skia work, so the
necessary `vkCmdPipelineBarrier` is issued. Both Ganesh and Graphite use
the same type.

## Ganesh — `src/gpu/ganesh/vk/`

The Ganesh side instantiates a `GrVkGpu` (`GrVkGpu.{h,cpp}`) that owns
command pools (`GrVkCommandPool`), command buffers (`GrVkCommandBuffer`,
both primary and secondary), descriptor-pool / set managers
(`GrVkDescriptorPool`, `GrVkDescriptorSet`, `GrVkDescriptorSetManager`),
a render-pass cache (`GrVkRenderPass`) and a framebuffer cache
(`GrVkFramebuffer`). `GrVkResourceProvider` recycles those pieces
across draws.

Pipelines are built lazily by `GrVkPipelineStateBuilder` from the
[SkSL](sksl-shading-language.md) program, transpiled to SPIR-V, and
cached in `GrVkPipelineStateCache`. `GrVkOpsRenderPass` records draw
commands inside one of the cached `VkRenderPass` instances. MSAA load
ops on tilers are emulated by `GrVkMSAALoadManager` when the driver
lacks a fast MSAA-load path.

Public Ganesh headers in `include/gpu/ganesh/vk/`:
`GrVkBackendContext.h`, `GrVkBackendSurface.h`, `GrVkBackendSemaphore.h`,
`GrVkDirectContext.h`, `GrVkTypes.h`. `GrDirectContexts::MakeVulkan(ctx)`
is the entry point.

`AHardwareBufferVk.cpp` adapts Android `AHardwareBuffer` to a `VkImage`
through `VK_ANDROID_external_memory_android_hardware_buffer` — see
[Android Integration](android-integration.md).

## Graphite — `src/gpu/graphite/vk/`

Graphite's Vulkan port is structurally similar but uses the modern
Graphite primitives. `VulkanSharedContext` holds the device + caps +
allocator; `VulkanQueueManager` schedules `CommandBuffer`s into the
`VkQueue`; `VulkanResourceProvider` caches `VulkanBuffer`,
`VulkanTexture`, `VulkanSampler`, `VulkanImageView`,
`VulkanRenderPass`, `VulkanFramebuffer`, and
`VulkanGraphicsPipeline`. `VulkanCommandBuffer` records the
GPU work and `VulkanSpirvTransforms` post-processes SPIR-V emitted by
the SkSL compiler (e.g. fold immutable-sampler bindings).

The public surface in `include/gpu/graphite/vk/` is small —
`VulkanGraphiteContext.h`, `VulkanGraphiteUtils.h`,
`VulkanGraphiteTypes.h`, plus a `precompile/` subtree
(see [Graphite Backend](graphite-backend.md) on pipeline
pre-compilation).

YCbCr conversion samplers (used for HDR video and external camera
formats) are handled by `VulkanYcbcrConversion` in concert with
`VulkanSampler`.

## External memory and semaphores

`GrVkBackendSemaphore.cpp` (Ganesh) and
`VulkanBackendSemaphore.cpp` (Graphite) wrap a client-supplied
`VkSemaphore` so it can be passed to `flushAndSubmit` /
`submit` either as a wait semaphore (Skia waits before issuing work) or
a signal semaphore (Skia signals when its submission completes). On
platforms where the semaphore must be importable from / exportable to
another API, the client creates the `VkSemaphore` with the right
`VK_KHR_external_semaphore_*` extensions before handing it to Skia.

The same pattern applies to images: a `VkImage` allocated by another
subsystem (camera, video decoder, the compositor's `VkSwapchainKHR`) is
wrapped via `Make...BackendTexture` / `Make...BackendRenderTarget`,
shared with Skia for rendering, then handed back — the
`VulkanMutableTextureState` records the post-Skia layout / queue family
so the next consumer can issue the right barrier.

## Where to look next

- [Ganesh Backend](ganesh-backend.md) — the renderer the Ganesh Vulkan
  port plugs into
- [Graphite Backend](graphite-backend.md) — the renderer the Graphite
  Vulkan port plugs into
- [GPU Overview](gpu-overview.md) — Ganesh vs Graphite at a glance
- [SkSL Shading Language](sksl-shading-language.md) — source for the
  SPIR-V shaders both Vulkan backends emit
- [Surface & Output](surface-and-output.md) — wrapping a `VkImage` /
  swapchain image as an `SkSurface`
- [Android Integration](android-integration.md) —
  `AHardwareBuffer` ↔ `VkImage` interop
