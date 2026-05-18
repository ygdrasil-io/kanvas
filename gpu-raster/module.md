# Module gpu-raster

WebGPU-backed implementation of the `kanvas-skia` device abstraction. Hosts `SkWebGpuDevice` (sibling of `SkBitmapDevice` from `:cpu-raster`) and its WebGPU plumbing (context, headless target, shader resources). See `MIGRATION_PLAN_GPU_WEBGPU.md` for the full phase plan ; the module was extracted from `:kanvas-skia` in G1.

The module depends on `:kanvas-skia` (for `SkDevice` / `SkBitmap` / `SkPaint` / etc.) and `:cpu-raster` (for shader / gradient state types). Raster consumers of `:kanvas-skia` don't pay the `wgpu4k-toolkit` native binary cost (~50 MB Metal/Vulkan/DX) until they explicitly opt in by depending on this module.

# Package org.skia.gpu.webgpu

Top-level GPU surface : `SkWebGpuDevice`, `WebGpuContext`, `HeadlessTarget`, plus the shader resources and pass infrastructure (geometry, gradient, sweep / radial / conical / bitmap shader pipelines, present pass with the embedded sRGB → Rec.2020 colorspace transform).
